package com.nightshade.account;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** A saved account. Offline accounts have no credentials; Microsoft accounts keep a refresh token. */
public final class Account {
    public enum Type {
        /** The account the launcher started the game with (never written to disk). */
        LAUNCHER("Launcher", 0xFF7C6CFF),
        /** A Microsoft account signed in through Nightshade (premium). */
        MICROSOFT("Microsoft", 0xFF3BA4F0),
        /** Offline / "cracked" username. Works only on servers in offline mode and in singleplayer. */
        OFFLINE("Offline", 0xFF8A8FA3);

        private final String label;
        private final int color;

        Type(String label, int color) {
            this.label = label;
            this.color = color;
        }

        public String label() {
            return label;
        }

        public int color() {
            return color;
        }
    }

    private final Type type;
    private String name;
    private UUID uuid;
    @Nullable
    private String refreshToken;
    /** How the refresh token is stored on disk (see TokenVault); recomputed when the token changes. */
    @Nullable
    private transient String storedToken;
    /** Minecraft access token (in memory only). */
    @Nullable
    private transient String accessToken;
    private transient long accessTokenExpiry;
    @Nullable
    private transient String xuid;

    public Account(Type type, String name, UUID uuid, @Nullable String refreshToken) {
        this.type = type;
        this.name = name;
        this.uuid = uuid;
        this.refreshToken = refreshToken;
    }

    public static Account offline(String name) {
        return new Account(Type.OFFLINE, name, offlineUuid(name), null);
    }

    /** The UUID vanilla offline-mode servers assign to a username. */
    public static UUID offlineUuid(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    /** Minecraft usernames: 3-16 characters, letters, digits and underscore. */
    public static boolean isValidOfflineName(String name) {
        return name != null && name.matches("[A-Za-z0-9_]{3,16}");
    }

    public Type type() {
        return type;
    }

    public String name() {
        return name;
    }

    public UUID uuid() {
        return uuid;
    }

    @Nullable
    public String refreshToken() {
        return refreshToken;
    }

    @Nullable
    public String accessToken() {
        return accessToken;
    }

    @Nullable
    public String xuid() {
        return xuid;
    }

    public boolean hasValidAccessToken() {
        return accessToken != null && System.currentTimeMillis() < accessTokenExpiry - 60_000L;
    }

    void updateFromLogin(MicrosoftAuth.Result result) {
        this.name = result.name();
        this.uuid = result.uuid();
        if (!java.util.Objects.equals(this.refreshToken, result.refreshToken())) {
            this.storedToken = null; // re-protect on next save
        }
        this.refreshToken = result.refreshToken();
        this.accessToken = result.accessToken();
        this.accessTokenExpiry = result.expiresAtMillis();
        this.xuid = result.xuid();
    }

    void setLauncherToken(String token) {
        this.accessToken = token;
        this.accessTokenExpiry = Long.MAX_VALUE;
    }

    /** How this account's token is protected on disk (shown in the account screen). */
    public TokenVault.Method tokenProtection() {
        return TokenVault.methodOf(storedToken);
    }

    @Nullable
    String storedToken() {
        return storedToken;
    }

    /** Stable identifier used to remember the active account. */
    public String key() {
        return type.name() + ":" + uuid;
    }

    JsonObject toJson() {
        JsonObject object = new JsonObject();
        object.addProperty("type", type.name());
        object.addProperty("name", name);
        object.addProperty("uuid", uuid.toString());
        if (refreshToken != null) {
            if (storedToken == null) {
                storedToken = TokenVault.protect(key(), refreshToken);
            }
            object.addProperty("refreshToken", storedToken);
        }
        return object;
    }

    @Nullable
    static Account fromJson(JsonObject object) {
        try {
            Type type = Type.valueOf(object.get("type").getAsString());
            if (type == Type.LAUNCHER) {
                return null;
            }
            String name = object.get("name").getAsString();
            UUID uuid = UUID.fromString(object.get("uuid").getAsString());
            String stored = object.has("refreshToken") ? object.get("refreshToken").getAsString() : null;
            String refresh = TokenVault.reveal(stored);
            if (type == Type.OFFLINE && !isValidOfflineName(name)) {
                return null;
            }
            if (type == Type.MICROSOFT && refresh == null) {
                return null;
            }
            Account account = new Account(type, name, uuid, refresh);
            // Re-protect tokens saved by older versions (no method prefix) on the next save.
            account.storedToken = stored != null && stored.contains(":") && TokenVault.reveal(stored) != null
                    && !TokenVault.methodOf(stored).equals(TokenVault.Method.PLAIN) ? stored : null;
            return account;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
