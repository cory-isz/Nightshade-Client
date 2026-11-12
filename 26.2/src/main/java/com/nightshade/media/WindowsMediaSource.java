package com.nightshade.media;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Windows: reads the system media controls (the panel next to the volume flyout) through Windows PowerShell and its
 * WinRT bridge - no extra software. One small PowerShell process stays open and prints the current track as a line
 * of JSON about once a second; play/pause/skip are passed to it through a command file.
 */
final class WindowsMediaSource implements MediaSource {
    private static final String SCRIPT = """
            param($cmdFile, $artDir)
            $ErrorActionPreference = 'Stop'
            [Console]::OutputEncoding = [Text.Encoding]::UTF8
            Add-Type -AssemblyName System.Runtime.WindowsRuntime
            $asTask = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]
            function Await($op, [Type]$t) { $task = $asTask.MakeGenericMethod($t).Invoke($null, @($op)); $task.Wait(-1) | Out-Null; $task.Result }
            function Say($text) { [Console]::Out.WriteLine($text); [Console]::Out.Flush() }
            [void][Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime]
            [void][Windows.Storage.Streams.IRandomAccessStreamWithContentType, Windows.Storage.Streams, ContentType = WindowsRuntime]
            $mgr = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
            $lastKey = ''
            $artPath = ''
            while ($true) {
              try {
                $session = $null
                foreach ($s in $mgr.GetSessions()) { if ($s.SourceAppUserModelId -like '*Spotify*') { $session = $s } }
                $spotify = $null -ne $session
                if ($null -eq $session) { $session = $mgr.GetCurrentSession() }
                if (Test-Path $cmdFile) {
                  $cmd = (Get-Content $cmdFile -Raw).Trim()
                  Remove-Item $cmdFile -Force
                  if ($null -ne $session) {
                    switch ($cmd) {
                      'next' { [void](Await ($session.TrySkipNextAsync()) ([bool])) }
                      'previous' { [void](Await ($session.TrySkipPreviousAsync()) ([bool])) }
                      default { [void](Await ($session.TryTogglePlayPauseAsync()) ([bool])) }
                    }
                  }
                }
                if ($null -eq $session) { Say '{}' } else {
                  $p = Await ($session.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
                  $tl = $session.GetTimelineProperties()
                  $playing = $session.GetPlaybackInfo().PlaybackStatus.ToString() -eq 'Playing'
                  $key = "$($p.Title)|$($p.Artist)|$($p.AlbumTitle)"
                  if ($key -ne $lastKey) {
                    $lastKey = $key
                    $artPath = ''
                    if ($null -ne $p.Thumbnail) {
                      $stream = Await ($p.Thumbnail.OpenReadAsync()) ([Windows.Storage.Streams.IRandomAccessStreamWithContentType])
                      $file = Join-Path $artDir ('art-' + [Math]::Abs($key.GetHashCode()) + '.img')
                      $net = [System.IO.WindowsRuntimeStreamExtensions]::AsStreamForRead($stream)
                      $fs = [System.IO.File]::Create($file)
                      $net.CopyTo($fs)
                      $fs.Close()
                      $net.Close()
                      $artPath = $file
                    }
                  }
                  $pos = $tl.Position.TotalMilliseconds
                  if ($playing) { $pos += ([DateTimeOffset]::Now - $tl.LastUpdatedTime).TotalMilliseconds }
                  $o = [ordered]@{ title = $p.Title; artist = $p.Artist; album = $p.AlbumTitle; playing = $playing; position = [long]$pos;
                                   duration = [long]$tl.EndTime.TotalMilliseconds; art = $artPath; player = $session.SourceAppUserModelId; spotify = $spotify }
                  Say ($o | ConvertTo-Json -Compress)
                }
              } catch {
                Say ('{"error":' + (ConvertTo-Json ([string]$_.Exception.Message)) + '}')
              }
              Start-Sleep -Milliseconds 700
            }
            """;

    private final Path dir;
    private final Path commandFile;
    private Process process;
    private volatile String lastLine;
    private volatile long lastLineAt;

    WindowsMediaSource() throws IOException {
        dir = Files.createTempDirectory("nightshade-media");
        commandFile = dir.resolve("command.txt");
        Path script = dir.resolve("now-playing.ps1");
        // A BOM so Windows PowerShell reads the script as UTF-8.
        Files.writeString(script, "﻿" + SCRIPT, StandardCharsets.UTF_8);
        process = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                "-WindowStyle", "Hidden", "-File", script.toString(), commandFile.toString(), dir.toString())
                .redirectErrorStream(true).start();
        Thread reader = new Thread(() -> {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = in.readLine()) != null) {
                    if (line.startsWith("{")) {
                        lastLine = line;
                        lastLineAt = System.currentTimeMillis();
                    }
                }
            } catch (IOException ignored) {
                // process ended
            }
        }, "Nightshade media reader");
        reader.setDaemon(true);
        reader.start();
        Runtime.getRuntime().addShutdownHook(new Thread(this::close));
    }

    @Override
    public MediaInfo poll(boolean spotifyOnly) throws Exception {
        if (process == null || !process.isAlive()) {
            throw new IOException("The Windows media reader stopped");
        }
        String line = lastLine;
        if (line == null || System.currentTimeMillis() - lastLineAt > 10_000) {
            return null;
        }
        JsonObject o = JsonParser.parseString(line).getAsJsonObject();
        if (o.has("error")) {
            throw new IOException(o.get("error").getAsString());
        }
        if (!o.has("title") || o.get("title").getAsString().isBlank()) {
            return null;
        }
        if (spotifyOnly && !(o.has("spotify") && o.get("spotify").getAsBoolean())) {
            return null;
        }
        String art = o.has("art") ? o.get("art").getAsString() : "";
        return new MediaInfo(o.get("title").getAsString(), str(o, "artist"), str(o, "album"), o.has("playing") && o.get("playing").getAsBoolean(),
                o.has("position") ? o.get("position").getAsLong() : 0, o.has("duration") ? o.get("duration").getAsLong() : 0, lastLineAt,
                art.isBlank() ? null : art, str(o, "player"));
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
    }

    @Override
    public void control(String command) throws Exception {
        Files.writeString(commandFile, command, StandardCharsets.UTF_8);
    }

    @Override
    public synchronized void close() {
        if (process != null) {
            process.destroy();
            process = null;
        }
    }
}
