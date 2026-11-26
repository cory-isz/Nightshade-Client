package com.nightshade.util;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * A tiny separate program (no Minecraft classes): waits until the old game process has fully exited, then starts it
 * again with the same command line. Run as {@code java -cp <nightshade jar> com.nightshade.util.Relauncher <pid> <args file> <work dir>}.
 */
public final class Relauncher {
    private Relauncher() {
    }

    public static void main(String[] args) throws Exception {
        long pid = Long.parseLong(args[0]);
        List<String> command = Files.readAllLines(Path.of(args[1]), StandardCharsets.UTF_8);
        ProcessHandle.of(pid).ifPresent(p -> {
            try {
                p.onExit().get();
            } catch (Exception ignored) {
                // gone already
            }
        });
        Thread.sleep(1500); // let the old process release its files (logs, options) on Windows
        new ProcessBuilder(command).directory(Path.of(args[2]).toFile()).inheritIO().start();
        Files.deleteIfExists(Path.of(args[1]));
    }
}
