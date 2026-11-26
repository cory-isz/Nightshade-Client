package com.nightshade.cosmetic;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

/**
 * Owner tools unlock only when config/nightshade/owner.key holds the secret whose SHA-256 matches the lock below.
 * The jar contains just this hash, so it names nobody and the secret cannot be read back out of it.
 */
public final class OwnerKey {
    private static final String LOCK = "ef32f926b132fec7ca74bb642602b9b180d6bee15ff53ac9b01ebec0b599d374";
    private static Boolean cached;
    private static long checkedAt;

    private OwnerKey() {
    }

    public static boolean present() {
        long now = System.currentTimeMillis();
        if (cached != null && now - checkedAt < 5000) {
            return cached;
        }
        checkedAt = now;
        cached = check(FabricLoader.getInstance().getConfigDir().resolve("nightshade").resolve("owner.key"));
        return cached;
    }

    static boolean check(Path file) {
        try {
            if (!Files.isRegularFile(file) || Files.size(file) > 4096) {
                return false;
            }
            String secret = Files.readString(file, StandardCharsets.UTF_8).trim();
            byte[] d = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) {
                sb.append(String.format("%02x", b));
            }
            return MessageDigest.isEqual(sb.toString().getBytes(StandardCharsets.US_ASCII), LOCK.getBytes(StandardCharsets.US_ASCII));
        } catch (Exception e) {
            return false;
        }
    }

    /** The owner key itself (for the cosmetics service's owner tools), or "" without a valid key file. */
    public static String secret() {
        try {
            java.nio.file.Path f = FabricLoader.getInstance().getConfigDir().resolve("nightshade").resolve("owner.key");
            return present() ? Files.readString(f, StandardCharsets.UTF_8).trim() : "";
        } catch (Exception e) {
            return "";
        }
    }

    public static void forgetCache() {
        cached = null;
    }
}
