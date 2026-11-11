package com.nightshade.account;

import com.nightshade.net.Http;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Microsoft account sign-in for Minecraft Java Edition using the OAuth device-code flow:
 * Microsoft (login.live.com) -> Xbox Live -> XSTS -> Minecraft services -> profile.
 * The user signs in in their own browser; Nightshade never sees the password.
 */
public final class MicrosoftAuth {
    /** Microsoft app id, from the build's edition settings; empty in editions without Microsoft sign-in. */
    private static final String CLIENT_ID = com.nightshade.Edition.microsoftClientId();
    private static final String SCOPE = "service::user.auth.xboxlive.com::MBI_SSL";
    private static final String DEVICE_CODE_URL = "https://login.live.com/oauth20_connect.srf";
    private static final String TOKEN_URL = "https://login.live.com/oauth20_token.srf";
    private static final String XBL_URL = "https://user.auth.xboxlive.com/user/authenticate";
    private static final String XSTS_URL = "https://xsts.auth.xboxlive.com/xsts/authorize";
    private static final String MC_LOGIN_URL = "https://api.minecraftservices.com/authentication/login_with_xbox";
    private static final String MC_PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile";

    private final HttpClient http = Http.client(Duration.ofSeconds(15)).build();

    public record DeviceCode(String userCode, String deviceCode, String verificationUri, int intervalSeconds, long expiresAtMillis) {
    }

    public record Result(String name, UUID uuid, String accessToken, long expiresAtMillis, String refreshToken, String xuid) {
    }

    public static final class AuthException extends Exception {
        public AuthException(String message) {
            super(message);
        }
    }

    /** Step 1: request a code the user enters at microsoft.com/link. */
    public DeviceCode requestDeviceCode() throws IOException, InterruptedException, AuthException {
        if (CLIENT_ID.isEmpty()) {
            throw new IOException("Microsoft sign-in is not part of this edition");
        }
        JsonObject json = postForm(DEVICE_CODE_URL, "client_id=" + CLIENT_ID + "&scope=" + enc(SCOPE) + "&response_type=device_code");
        if (!json.has("user_code")) {
            throw new AuthException("Microsoft did not return a device code: " + json);
        }
        return new DeviceCode(json.get("user_code").getAsString(), json.get("device_code").getAsString(),
                json.get("verification_uri").getAsString(), json.has("interval") ? json.get("interval").getAsInt() : 5,
                System.currentTimeMillis() + json.get("expires_in").getAsLong() * 1000L);
    }

    /** Step 2: poll until the user approves, then complete the Xbox/Minecraft chain. */
    public Result pollAndLogin(DeviceCode code, AtomicBoolean cancelled, Consumer<String> status)
            throws IOException, InterruptedException, AuthException {
        int interval = Math.max(1, code.intervalSeconds());
        while (!cancelled.get()) {
            if (System.currentTimeMillis() > code.expiresAtMillis()) {
                throw new AuthException("The code expired. Start the sign-in again.");
            }
            Thread.sleep(interval * 1000L);
            if (cancelled.get()) {
                break;
            }
            JsonObject json = postForm(TOKEN_URL, "grant_type=" + enc("urn:ietf:params:oauth:grant-type:device_code")
                    + "&client_id=" + CLIENT_ID + "&device_code=" + enc(code.deviceCode()));
            if (json.has("access_token")) {
                status.accept("Signing in to Xbox Live...");
                return completeLogin(json.get("access_token").getAsString(), json.get("refresh_token").getAsString(), status);
            }
            String error = json.has("error") ? json.get("error").getAsString() : "unknown_error";
            switch (error) {
                case "authorization_pending" -> {
                }
                case "slow_down" -> interval += 5;
                case "authorization_declined" -> throw new AuthException("Sign-in was declined.");
                case "expired_token" -> throw new AuthException("The code expired. Start the sign-in again.");
                default -> throw new AuthException("Microsoft sign-in failed: " + error);
            }
        }
        throw new AuthException("Cancelled");
    }

    /** Uses a saved refresh token to obtain fresh tokens without user interaction. */
    public Result refresh(String refreshToken, Consumer<String> status) throws IOException, InterruptedException, AuthException {
        status.accept("Refreshing Microsoft session...");
        if (CLIENT_ID.isEmpty()) {
            throw new IOException("Microsoft sign-in is not part of this edition");
        }
        JsonObject json = postForm(TOKEN_URL, "grant_type=refresh_token&client_id=" + CLIENT_ID
                + "&scope=" + enc(SCOPE) + "&refresh_token=" + enc(refreshToken));
        if (!json.has("access_token")) {
            throw new AuthException("Saved sign-in expired. Remove the account and sign in again.");
        }
        return completeLogin(json.get("access_token").getAsString(),
                json.has("refresh_token") ? json.get("refresh_token").getAsString() : refreshToken, status);
    }

    private Result completeLogin(String msAccessToken, String refreshToken, Consumer<String> status)
            throws IOException, InterruptedException, AuthException {
        // Xbox Live user token
        JsonObject xblBody = new JsonObject();
        JsonObject xblProps = new JsonObject();
        xblProps.addProperty("AuthMethod", "RPS");
        xblProps.addProperty("SiteName", "user.auth.xboxlive.com");
        xblProps.addProperty("RpsTicket", msAccessToken);
        xblBody.add("Properties", xblProps);
        xblBody.addProperty("RelyingParty", "http://auth.xboxlive.com");
        xblBody.addProperty("TokenType", "JWT");
        HttpResponse<String> xblResponse = postJson(XBL_URL, xblBody, null);
        if (xblResponse.statusCode() != 200) {
            throw new AuthException("Xbox Live authentication failed (HTTP " + xblResponse.statusCode() + ")");
        }
        JsonObject xbl = JsonParser.parseString(xblResponse.body()).getAsJsonObject();
        String xblToken = xbl.get("Token").getAsString();

        // XSTS token for Minecraft services
        status.accept("Authorising with Xbox services...");
        JsonObject xstsBody = new JsonObject();
        JsonObject xstsProps = new JsonObject();
        xstsProps.addProperty("SandboxId", "RETAIL");
        JsonArray userTokens = new JsonArray();
        userTokens.add(xblToken);
        xstsProps.add("UserTokens", userTokens);
        xstsBody.add("Properties", xstsProps);
        xstsBody.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
        xstsBody.addProperty("TokenType", "JWT");
        HttpResponse<String> xstsResponse = postJson(XSTS_URL, xstsBody, null);
        if (xstsResponse.statusCode() == 401) {
            throw new AuthException(xstsError(xstsResponse.body()));
        }
        if (xstsResponse.statusCode() != 200) {
            throw new AuthException("XSTS authorisation failed (HTTP " + xstsResponse.statusCode() + ")");
        }
        JsonObject xsts = JsonParser.parseString(xstsResponse.body()).getAsJsonObject();
        String xstsToken = xsts.get("Token").getAsString();
        JsonObject claims = xsts.getAsJsonObject("DisplayClaims").getAsJsonArray("xui").get(0).getAsJsonObject();
        String userHash = claims.get("uhs").getAsString();
        String xuid = claims.has("xid") ? claims.get("xid").getAsString() : "";

        // Minecraft access token
        status.accept("Signing in to Minecraft...");
        JsonObject mcBody = new JsonObject();
        mcBody.addProperty("identityToken", "XBL3.0 x=" + userHash + ";" + xstsToken);
        HttpResponse<String> mcResponse = postJson(MC_LOGIN_URL, mcBody, null);
        if (mcResponse.statusCode() != 200) {
            throw new AuthException("Minecraft login failed (HTTP " + mcResponse.statusCode() + ")");
        }
        JsonObject mc = JsonParser.parseString(mcResponse.body()).getAsJsonObject();
        String mcToken = mc.get("access_token").getAsString();
        long expiresAt = System.currentTimeMillis() + mc.get("expires_in").getAsLong() * 1000L;

        // Profile (fails with 404 if the account does not own Java Edition)
        status.accept("Loading profile...");
        HttpRequest profileRequest = HttpRequest.newBuilder(URI.create(MC_PROFILE_URL))
                .timeout(Duration.ofSeconds(20)).header("Authorization", "Bearer " + mcToken).GET().build();
        HttpResponse<String> profileResponse = http.send(profileRequest, HttpResponse.BodyHandlers.ofString());
        if (profileResponse.statusCode() == 404) {
            throw new AuthException("This Microsoft account does not own Minecraft: Java Edition.");
        }
        if (profileResponse.statusCode() != 200) {
            throw new AuthException("Could not load Minecraft profile (HTTP " + profileResponse.statusCode() + ")");
        }
        JsonObject profile = JsonParser.parseString(profileResponse.body()).getAsJsonObject();
        String name = profile.get("name").getAsString();
        UUID uuid = parseUndashedUuid(profile.get("id").getAsString());
        return new Result(name, uuid, mcToken, expiresAt, refreshToken, xuid);
    }

    private static String xstsError(String body) {
        try {
            long code = JsonParser.parseString(body).getAsJsonObject().get("XErr").getAsLong();
            if (code == 2148916233L) {
                return "This Microsoft account has no Xbox profile. Sign in once at xbox.com, then try again.";
            }
            if (code == 2148916235L) {
                return "Xbox Live is not available in your country.";
            }
            if (code == 2148916236L || code == 2148916237L) {
                return "This account needs adult verification on xbox.com.";
            }
            if (code == 2148916238L) {
                return "Child account: an adult must add it to a Microsoft family.";
            }
            return "Xbox authorisation refused (XErr " + code + ")";
        } catch (RuntimeException e) {
            return "Xbox authorisation refused";
        }
    }

    static UUID parseUndashedUuid(String id) {
        String dashed = id.length() == 32
                ? id.replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5")
                : id;
        return UUID.fromString(dashed);
    }

    private JsonObject postForm(String url, String body) throws IOException, InterruptedException, AuthException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        try {
            return JsonParser.parseString(response.body()).getAsJsonObject();
        } catch (RuntimeException e) {
            throw new AuthException("Unexpected response from Microsoft (HTTP " + response.statusCode() + ")");
        }
    }

    private HttpResponse<String> postJson(String url, JsonObject body, String bearer) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json").header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        if (bearer != null) {
            builder.header("Authorization", "Bearer " + bearer);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
