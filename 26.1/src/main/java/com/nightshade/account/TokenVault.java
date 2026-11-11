package com.nightshade.account;

import com.nightshade.util.Log;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * Protects saved Microsoft refresh tokens with the operating system's credential storage:
 * <ul>
 *     <li>Windows: DPAPI (CryptProtectData, bound to your Windows user) via the JNA library bundled with Minecraft;</li>
 *     <li>macOS: the login Keychain via {@code /usr/bin/security};</li>
 *     <li>Linux: the desktop keyring (Secret Service) via {@code secret-tool}, when installed.</li>
 * </ul>
 * When none is available the token is stored as before (plain text in a file only your user can read). The stored
 * string records which method was used, so files stay readable when moving between methods.
 */
public final class TokenVault {
    private static final String SERVICE = "Nightshade Client";

    private TokenVault() {
    }

    public enum Method {
        DPAPI("Windows DPAPI"), KEYCHAIN("macOS Keychain"), SECRET_SERVICE("Linux keyring"), PLAIN("file (owner-only)");

        private final String label;

        Method(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** Returns the string to store for {@code token}. {@code key} identifies the account in keychains. */
    public static String protect(String key, String token) {
        try {
            switch (Util.getPlatform()) {
                case WINDOWS -> {
                    byte[] encrypted = com.sun.jna.platform.win32.Crypt32Util.cryptProtectData(token.getBytes(StandardCharsets.UTF_8));
                    return "dpapi:" + Base64.getEncoder().encodeToString(encrypted);
                }
                case OSX -> {
                    if (run(null, "/usr/bin/security", "add-generic-password", "-U", "-a", key, "-s", SERVICE, "-w", token) != null) {
                        return "keychain:" + key;
                    }
                }
                case LINUX -> {
                    if (run(token, "secret-tool", "store", "--label=" + SERVICE + " " + key, "service", SERVICE, "account", key) != null) {
                        return "secret-service:" + key;
                    }
                }
                default -> {
                }
            }
        } catch (Throwable t) {
            Log.warn("OS credential storage unavailable ({}); storing token in the owner-only file", t.toString());
        }
        return "plain:" + token;
    }

    /** Recovers a token from its stored form, or null if it cannot be read (e.g. keychain entry removed). */
    @Nullable
    public static String reveal(String stored) {
        if (stored == null) {
            return null;
        }
        try {
            if (stored.startsWith("dpapi:")) {
                byte[] decrypted = com.sun.jna.platform.win32.Crypt32Util.cryptUnprotectData(Base64.getDecoder().decode(stored.substring(6)));
                return new String(decrypted, StandardCharsets.UTF_8);
            }
            if (stored.startsWith("keychain:")) {
                return run(null, "/usr/bin/security", "find-generic-password", "-a", stored.substring(9), "-s", SERVICE, "-w");
            }
            if (stored.startsWith("secret-service:")) {
                return run(null, "secret-tool", "lookup", "service", SERVICE, "account", stored.substring(15));
            }
            if (stored.startsWith("plain:")) {
                return stored.substring(6);
            }
            return stored; // files written before protection existed
        } catch (Throwable t) {
            Log.warn("Could not read protected token: {}", t.toString());
            return null;
        }
    }

    /** Removes a token from the OS store when its account is deleted. */
    public static void forget(@Nullable String stored) {
        if (stored == null) {
            return;
        }
        try {
            if (stored.startsWith("keychain:")) {
                run(null, "/usr/bin/security", "delete-generic-password", "-a", stored.substring(9), "-s", SERVICE);
            } else if (stored.startsWith("secret-service:")) {
                run(null, "secret-tool", "clear", "service", SERVICE, "account", stored.substring(15));
            }
        } catch (Throwable ignored) {
            // nothing stored or tool missing
        }
    }

    public static Method methodOf(@Nullable String stored) {
        if (stored == null || stored.startsWith("plain:")) {
            return Method.PLAIN;
        }
        if (stored.startsWith("dpapi:")) {
            return Method.DPAPI;
        }
        if (stored.startsWith("keychain:")) {
            return Method.KEYCHAIN;
        }
        if (stored.startsWith("secret-service:")) {
            return Method.SECRET_SERVICE;
        }
        return Method.PLAIN;
    }

    /** Runs a command; returns trimmed stdout on exit code 0, else null. {@code stdin} is written if non-null. */
    @Nullable
    private static String run(@Nullable String stdin, String... command) throws IOException, InterruptedException {
        Process process;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(false).start();
        } catch (IOException missing) {
            return null; // tool not installed
        }
        if (stdin != null) {
            try (OutputStream out = process.getOutputStream()) {
                out.write(stdin.getBytes(StandardCharsets.UTF_8));
            }
        } else {
            process.getOutputStream().close();
        }
        if (!process.waitFor(10, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            return null;
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        return process.exitValue() == 0 ? output : null;
    }
}
