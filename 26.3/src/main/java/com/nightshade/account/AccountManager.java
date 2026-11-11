package com.nightshade.account;

import com.nightshade.net.Http;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.SessionService;
import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.services.MinecraftServicesDiscoveryService;
import com.mojang.authlib.services.ProfileResult;
import com.nightshade.mixin.accessor.MinecraftClientSessionAccessor;
import com.nightshade.util.Log;
import com.nightshade.util.NotificationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.PlayerSkin;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Saved accounts and live account switching.
 * <p>
 * Switching replaces every identity-related field of {@link Minecraft} (session, user API service, chat
 * signing keys, game profile and user properties) so multiplayer, chat signing and skins follow the new account.
 * Accounts are stored in {@code config/nightshade/accounts.json}; Microsoft accounts store only a refresh token,
 * never a password. The launcher's own account is kept in memory and never written to disk.
 */
public final class AccountManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Minecraft mc = Minecraft.getInstance();
    private final Path file;
    private final NotificationManager notifications;
    private final MicrosoftAuth auth = new MicrosoftAuth();
    private final List<Account> accounts = new ArrayList<>();
    private final Map<UUID, PlayerSkin> skins = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> skinRequests = new ConcurrentHashMap<>();
    private Account launcherAccount;
    private User launcherSession;
    private Account active;
    @Nullable
    private String savedActiveKey;
    private volatile boolean switching;

    public AccountManager(Path configRoot, NotificationManager notifications) {
        this.file = configRoot.resolve("accounts.json");
        this.notifications = notifications;
    }

    /** Initialises lazily so the launcher session is captured only once Minecraft has created it. */
    public void ensureInit() {
        if (launcherAccount == null) {
            init();
        }
    }

    private void init() {
        launcherSession = mc.getUser();
        UUID uuid = launcherSession.getProfileId() != null ? launcherSession.getProfileId() : Account.offlineUuid(launcherSession.getName());
        launcherAccount = new Account(Account.Type.LAUNCHER, launcherSession.getName(), uuid, null);
        launcherAccount.setLauncherToken(launcherSession.getAccessToken());
        accounts.add(launcherAccount);
        active = launcherAccount;
        load();
    }

    /** Restores the previously selected account once the client has started. */
    public void restoreActive() {
        ensureInit();
        if (savedActiveKey == null || savedActiveKey.equals(launcherAccount.key())) {
            return;
        }
        for (Account account : accounts) {
            if (account.key().equals(savedActiveKey)) {
                switchTo(account, success -> {
                    if (!success) {
                        notifications.warn("Accounts", "Could not restore " + account.name() + ", using launcher account");
                    }
                });
                return;
            }
        }
    }

    // ------------------------------------------------------------------ queries

    public List<Account> accounts() {
        ensureInit();
        return Collections.unmodifiableList(accounts);
    }

    public Account active() {
        ensureInit();
        return active;
    }

    public boolean isSwitching() {
        return switching;
    }

    public MicrosoftAuth auth() {
        return auth;
    }

    /** Whether the active account can join online-mode servers and Realms. */
    public boolean isPremium() {
        ensureInit();
        return active.type() == Account.Type.MICROSOFT
                || (active.type() == Account.Type.LAUNCHER && hasRealToken(launcherSession));
    }

    /** Skin for the head icon; offline accounts use the default skin derived from their UUID. */
    public PlayerSkin skin(Account account) {
        if (account.type() == Account.Type.OFFLINE) {
            return DefaultPlayerSkin.get(account.uuid());
        }
        PlayerSkin cached = skins.get(account.uuid());
        if (cached != null) {
            return cached;
        }
        if (skinRequests.putIfAbsent(account.uuid(), Boolean.TRUE) == null) {
            CompletableFuture.supplyAsync(() -> sessionService().fetchProfile(account.uuid(), false), Util.nonCriticalIoPool())
                    .thenCompose(result -> result == null
                            ? CompletableFuture.completedFuture(DefaultPlayerSkin.get(account.uuid()))
                            : mc.getSkinManager().get(result.profile())
                                    .thenApply(optional -> optional.orElseGet(() -> DefaultPlayerSkin.get(account.uuid()))))
                    .whenComplete((textures, error) -> {
                        if (textures != null) {
                            skins.put(account.uuid(), textures);
                        }
                    });
        }
        return DefaultPlayerSkin.get(account.uuid());
    }

    /** Drops the cached skin so the head icon is fetched again (after a skin change). */
    public void forgetSkin(Account account) {
        skins.remove(account.uuid());
        skinRequests.remove(account.uuid());
    }

    public Account launcherAccount() {
        ensureInit();
        return launcherAccount;
    }

    // ------------------------------------------------------------------ mutations

    /** Adds an offline account. Returns an error message, or null on success. */
    @Nullable
    public String addOffline(String name) {
        ensureInit();
        String trimmed = name == null ? "" : name.trim();
        if (!Account.isValidOfflineName(trimmed)) {
            return "Use 3-16 letters, numbers or underscores";
        }
        for (Account account : accounts) {
            if (account.type() == Account.Type.OFFLINE && account.name().equalsIgnoreCase(trimmed)) {
                return "That offline account already exists";
            }
        }
        accounts.add(Account.offline(trimmed));
        save();
        return null;
    }

    /** Adds (or updates) a Microsoft account from a completed sign-in. */
    public Account addMicrosoft(MicrosoftAuth.Result result) {
        ensureInit();
        for (Account account : accounts) {
            if (account.type() == Account.Type.MICROSOFT && account.uuid().equals(result.uuid())) {
                account.updateFromLogin(result);
                save();
                return account;
            }
        }
        Account account = new Account(Account.Type.MICROSOFT, result.name(), result.uuid(), result.refreshToken());
        account.updateFromLogin(result);
        accounts.add(account);
        save();
        return account;
    }

    public boolean remove(Account account) {
        if (account.type() == Account.Type.LAUNCHER) {
            return false;
        }
        if (account == active) {
            apply(launcherAccount);
        }
        accounts.remove(account);
        TokenVault.forget(account.storedToken());
        save();
        return true;
    }

    /**
     * Switches to an account. Offline and launcher accounts switch immediately; Microsoft accounts refresh their
     * token first (asynchronously). The callback runs on the render thread.
     */
    public void switchTo(Account account, Consumer<Boolean> callback) {
        if (switching) {
            callback.accept(false);
            return;
        }
        if (mc.level != null) {
            notifications.warn("Accounts", "Leave the world before switching accounts");
            callback.accept(false);
            return;
        }
        if (account.type() != Account.Type.MICROSOFT || account.hasValidAccessToken()) {
            apply(account);
            callback.accept(true);
            return;
        }
        switching = true;
        CompletableFuture.supplyAsync(() -> {
            try {
                return auth.refresh(account.refreshToken(), message -> {
                });
            } catch (Exception e) {
                throw new RuntimeException(Http.describe(e), e);
            }
        }, Util.nonCriticalIoPool()).whenComplete((result, error) -> mc.execute(() -> {
            switching = false;
            if (error != null) {
                Throwable cause = error.getCause() != null ? error.getCause() : error;
                notifications.error("Accounts", cause.getMessage());
                callback.accept(false);
                return;
            }
            account.updateFromLogin(result);
            save();
            apply(account);
            callback.accept(true);
        }));
    }

    /** Installs the account's identity into the running client. Must be called on the render thread. */
    private void apply(Account account) {
        MinecraftClientSessionAccessor accessor = (MinecraftClientSessionAccessor) mc;
        User session;
        UserApiService api;
        ProfileKeyPairManager keys;
        CompletableFuture<ProfileResult> profile;
        CompletableFuture<UserApiService.UserProperties> properties;
        switch (account.type()) {
            case LAUNCHER -> {
                session = launcherSession;
                api = createApi(launcherSession);
                keys = api == UserApiService.OFFLINE ? ProfileKeyPairManager.EMPTY_KEY_MANAGER : ProfileKeyPairManager.create(api, session, mc.gameDirectory.toPath());
                profile = fetchProfileAsync(session);
                properties = fetchPropertiesAsync(api);
            }
            case MICROSOFT -> {
                session = new User(account.name(), account.uuid(), account.accessToken(),
                        Optional.ofNullable(account.xuid()).filter(s -> !s.isEmpty()), Optional.empty());
                api = createApi(session);
                keys = ProfileKeyPairManager.create(api, session, mc.gameDirectory.toPath());
                profile = fetchProfileAsync(session);
                properties = fetchPropertiesAsync(api);
            }
            default -> {
                session = new User(account.name(), account.uuid(), "0", Optional.empty(), Optional.empty());
                api = UserApiService.OFFLINE;
                keys = ProfileKeyPairManager.EMPTY_KEY_MANAGER;
                profile = CompletableFuture.completedFuture(new ProfileResult(new GameProfile(account.uuid(), account.name())));
                properties = CompletableFuture.completedFuture(UserApiService.OFFLINE_PROPERTIES);
            }
        }
        accessor.nightshade$setSession(session);
        accessor.nightshade$setUserApiService(api);
        accessor.nightshade$setProfileKeys(keys);
        accessor.nightshade$setGameProfileFuture(profile);
        accessor.nightshade$setUserPropertiesFuture(properties);
        active = account;
        savedActiveKey = account.key();
        save();
        Log.info("Switched to {} account {}", account.type().label(), account.name());
        notifications.success("Accounts", "Playing as " + account.name() + " (" + account.type().label() + ")");
    }

    private UserApiService createApi(User session) {
        if (!hasRealToken(session)) {
            return UserApiService.OFFLINE;
        }
        try {
            return authenticationService().createUserApiService(session.getAccessToken());
        } catch (Exception e) {
            Log.warn("Could not create user API service: {}", e.toString());
            return UserApiService.OFFLINE;
        }
    }

    /** Minecraft 1.21.11 dropped Session.AccountType; a real (premium) session carries a long JWT access token. */
    private static boolean hasRealToken(User session) {
        String token = session.getAccessToken();
        return token != null && token.length() > 20 && !token.equals("0");
    }

    private SessionService sessionService() {
        return mc.services().sessionService();
    }

    private MinecraftServicesDiscoveryService authService;

    private MinecraftServicesDiscoveryService authenticationService() {
        if (authService == null) {
            authService = MinecraftServicesDiscoveryService.create(mc.getProxy());
        }
        return authService;
    }

    private CompletableFuture<ProfileResult> fetchProfileAsync(User session) {
        UUID uuid = session.getProfileId();
        if (uuid == null) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                return sessionService().fetchProfile(uuid, true);
            } catch (RuntimeException e) {
                return null;
            }
        }, Util.nonCriticalIoPool());
    }

    private static CompletableFuture<UserApiService.UserProperties> fetchPropertiesAsync(UserApiService api) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return api.fetchProperties();
            } catch (Exception e) {
                return UserApiService.OFFLINE_PROPERTIES;
            }
        }, Util.nonCriticalIoPool());
    }

    // ------------------------------------------------------------------ persistence

    private void load() {
        if (!Files.exists(file)) {
            return;
        }
        try {
            JsonElement root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            JsonObject object = root.getAsJsonObject();
            if (object.has("active")) {
                savedActiveKey = object.get("active").getAsString();
            }
            if (object.has("accounts")) {
                for (JsonElement element : object.getAsJsonArray("accounts")) {
                    if (element.isJsonObject()) {
                        Account account = Account.fromJson(element.getAsJsonObject());
                        if (account != null) {
                            accounts.add(account);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Path backup = file.resolveSibling("accounts.corrupt-" + System.currentTimeMillis() + ".json");
            try {
                Files.move(file, backup);
            } catch (IOException ignored) {
                // keep the original if it cannot be moved
            }
            Log.warn("accounts.json was unreadable and has been moved to {}", backup.getFileName());
        }
    }

    public void save() {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        if (savedActiveKey != null) {
            root.addProperty("active", savedActiveKey);
        }
        JsonArray array = new JsonArray();
        for (Account account : accounts) {
            if (account.type() != Account.Type.LAUNCHER) {
                array.add(account.toJson());
            }
        }
        root.add("accounts", array);
        try {
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling("accounts.json.tmp");
            Files.writeString(temp, GSON.toJson(root), StandardCharsets.UTF_8);
            try {
                Files.setPosixFilePermissions(temp, PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException | IOException ignored) {
                // Windows: rely on the user profile directory permissions
            }
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            Log.error("Failed to save accounts", e);
        }
    }
}
