package com.nightshade.util;

import net.minecraft.client.Minecraft;

import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Restarts Minecraft with exactly the same launch command (account, folders and memory stay the same), so new or
 * updated mods load without going back through the launcher. A small helper process does the relaunch after the
 * game has closed. Returns false (and changes nothing) when the launch command cannot be worked out.
 */
public final class GameRestart {
    private GameRestart() {
    }

    /** The command that started this game, as separate arguments, or empty when it cannot be read. */
    public static Optional<List<String>> currentCommand() {
        ProcessHandle.Info info = ProcessHandle.current().info();
        Optional<String> exe = info.command();
        Optional<String[]> arguments = info.arguments();
        if (exe.isPresent() && arguments.isPresent() && arguments.get().length > 0) {
            List<String> out = new ArrayList<>();
            out.add(exe.get());
            out.addAll(List.of(arguments.get()));
            return Optional.of(out);
        }
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            // Windows does not hand the arguments to Java; ask the system for the full command line instead.
            try {
                Process p = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command",
                        "[Console]::OutputEncoding=[Text.Encoding]::UTF8; (Get-CimInstance Win32_Process -Filter \"ProcessId="
                                + ProcessHandle.current().pid() + "\").CommandLine").redirectErrorStream(true).start();
                String line = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                p.waitFor();
                List<String> parsed = parseWindowsCommandLine(line);
                if (parsed.size() > 1) {
                    return Optional.of(parsed);
                }
            } catch (Exception ignored) {
                // fall through
            }
        }
        return Optional.empty();
    }

    /** Splits a Windows command line the way Windows programs do (CommandLineToArgvW rules for quotes and backslashes). */
    public static List<String> parseWindowsCommandLine(String line) {
        List<String> args = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        boolean has = false;
        int i = 0;
        while (i < line.length()) {
            char c = line.charAt(i);
            if (c == '\\') {
                int slashes = 0;
                while (i < line.length() && line.charAt(i) == '\\') {
                    slashes++;
                    i++;
                }
                if (i < line.length() && line.charAt(i) == '"') {
                    current.append("\\".repeat(slashes / 2));
                    if (slashes % 2 == 1) {
                        current.append('"');
                        i++;
                    }
                } else {
                    current.append("\\".repeat(slashes));
                }
                has = true;
                continue;
            }
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i += 2;
                    continue;
                }
                inQuotes = !inQuotes;
                has = true;
                i++;
                continue;
            }
            if (Character.isWhitespace(c) && !inQuotes) {
                if (has) {
                    args.add(current.toString());
                    current.setLength(0);
                    has = false;
                }
                i++;
                continue;
            }
            current.append(c);
            has = true;
            i++;
        }
        if (has) {
            args.add(current.toString());
        }
        return args;
    }

    /** Where Nightshade's classes are (its jar, or a classes folder in development). */
    private static Path ownLocation() throws Exception {
        return Path.of(GameRestart.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    }

    /** Closes the game and starts it again. Returns false when the restart could not be prepared. */
    public static boolean restart() {
        try {
            Optional<List<String>> command = currentCommand();
            if (command.isEmpty()) {
                return false;
            }
            Path args = Files.createTempFile("nightshade-restart", ".txt");
            Files.write(args, command.get(), StandardCharsets.UTF_8);
            String java = Path.of(System.getProperty("java.home"), "bin",
                    System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win") ? "javaw.exe" : "java").toString();
            new ProcessBuilder(java, "-cp", ownLocation().toString(), Relauncher.class.getName(),
                    Long.toString(ProcessHandle.current().pid()), args.toString(), System.getProperty("user.dir"))
                    .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            Log.info("Restarting Minecraft with the same launch command ({} arguments)", command.get().size() - 1);
            Minecraft.getInstance().stop();
            return true;
        } catch (Exception e) {
            Log.warn("Could not restart Minecraft: {}", e.getMessage());
            return false;
        }
    }

    /** The JVM's own arguments (for diagnostics). */
    public static List<String> jvmArguments() {
        return ManagementFactory.getRuntimeMXBean().getInputArguments();
    }
}
