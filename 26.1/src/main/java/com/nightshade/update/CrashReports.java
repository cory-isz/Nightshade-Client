package com.nightshade.update;

import com.nightshade.Edition;
import com.nightshade.NightshadeClient;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.SharedConstants;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Finds a crash report Minecraft wrote since the last time it was shown, and builds a short, shareable summary
 * (versions, mods, the error and the top of the stack trace). Nothing is uploaded: the main menu offers to copy it to
 * the clipboard or open the crash-reports folder.
 */
public final class CrashReports {
    private static final String SEEN_FILE = "last_crash_seen.txt";

    private CrashReports() {
    }

    private static Path folder() {
        return FabricLoader.getInstance().getGameDir().resolve("crash-reports");
    }

    private static Path seenFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("nightshade").resolve(SEEN_FILE);
    }

    /** The newest crash report not dismissed yet, if any. */
    public static Optional<Path> unseen() {
        Path dir = folder();
        if (!Files.isDirectory(dir)) {
            return Optional.empty();
        }
        long seen = 0L;
        try {
            if (Files.isRegularFile(seenFile())) {
                seen = Long.parseLong(Files.readString(seenFile()).trim());
            }
        } catch (IOException | NumberFormatException ignored) {
            // treat as never seen
        }
        long since = seen;
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(f -> f.getFileName().toString().endsWith(".txt"))
                    .filter(f -> modified(f) > since)
                    .max(Comparator.comparingLong(CrashReports::modified));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private static long modified(Path f) {
        try {
            return Files.getLastModifiedTime(f).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }

    /** Hides the current crash notice (until the next crash). */
    public static void dismiss(Path report) {
        try {
            Files.createDirectories(seenFile().getParent());
            Files.writeString(seenFile(), Long.toString(modified(report)));
        } catch (IOException ignored) {
            // shown again next launch
        }
    }

    public static Path folderPath() {
        return folder();
    }

    /** A compact report to paste in Discord or an issue. */
    public static String summary(Path report) {
        StringBuilder out = new StringBuilder();
        out.append("**Nightshade crash report**\n```\n");
        out.append("Nightshade ").append(NightshadeClient.VERSION).append(" (").append(Edition.name()).append(")\n");
        out.append("Minecraft ").append(SharedConstants.getCurrentVersion().name()).append(", Fabric Loader ")
                .append(FabricLoader.getInstance().getModContainer("fabricloader").map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("?"))
                .append(", Java ").append(System.getProperty("java.version")).append(", ").append(System.getProperty("os.name")).append('\n');
        List<String> mods = FabricLoader.getInstance().getAllMods().stream()
                .filter(m -> m.getContainingMod().isEmpty())
                .map(ModContainer::getMetadata)
                .filter(m -> !m.getId().startsWith("fabric-") && !m.getId().equals("java") && !m.getId().equals("minecraft"))
                .map(m -> m.getId() + " " + m.getVersion().getFriendlyString())
                .sorted().toList();
        out.append("Mods (").append(mods.size()).append("): ").append(String.join(", ", mods)).append("\n\n");
        try {
            List<String> lines = Files.readAllLines(report, StandardCharsets.UTF_8);
            int taken = 0;
            boolean inStack = false;
            for (String line : lines) {
                if (line.startsWith("Description:") || line.startsWith("Time:")) {
                    out.append(line).append('\n');
                    continue;
                }
                if (!inStack && (line.startsWith("java.") || line.contains("Exception") || line.contains("Error:"))) {
                    inStack = true;
                }
                if (inStack) {
                    if (line.isBlank() || taken >= 25) {
                        break;
                    }
                    out.append(line).append('\n');
                    taken++;
                }
            }
        } catch (IOException e) {
            out.append("(could not read ").append(report.getFileName()).append(")\n");
        }
        out.append("```\nFull file: crash-reports/").append(report.getFileName());
        return out.toString();
    }
}
