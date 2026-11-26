package com.nightshade.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nightshade.NightshadeClient;
import com.nightshade.net.Http;
import com.nightshade.util.Log;
import net.minecraft.SharedConstants;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Checks Modrinth (modrinth.com/mod/nightshadeclient) once per launch for a newer Nightshade build for this
 * Minecraft version. Only reads the public version list; nothing about the player is sent. The main menu shows
 * the result with a button that opens the version page.
 */
public final class UpdateChecker {
    public static final String PROJECT = "nightshadeclient";
    public static final String PAGE = "https://modrinth.com/mod/" + PROJECT;

    /** A newer version, or null. */
    public record Update(String version, String url, String type) {
    }

    private static volatile Update available;
    private static volatile String status = "not checked";
    private static volatile boolean started;

    private UpdateChecker() {
    }

    /** Starts the background check once; safe to call every time the main menu opens. */
    public static void checkOnce(boolean includeBeta) {
        if (started) {
            return;
        }
        started = true;
        Thread thread = new Thread(() -> check(includeBeta), "Nightshade update check");
        thread.setDaemon(true);
        thread.start();
    }

    public static Update available() {
        return available;
    }

    public static String status() {
        return status;
    }

    static void check(boolean includeBeta) {
        String mc = SharedConstants.getCurrentVersion().name();
        try {
            HttpClient http = Http.client(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();
            String query = "?loaders=" + enc("[\"fabric\"]") + "&game_versions=" + enc("[\"" + mc + "\"]");
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.modrinth.com/v2/project/" + PROJECT + "/version" + query))
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "Nightshade-Client/" + NightshadeClient.VERSION + " (update check; " + PAGE + ")")
                    .GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) {
                status = "project not public yet";
                return;
            }
            if (response.statusCode() != 200) {
                status = "Modrinth returned HTTP " + response.statusCode();
                return;
            }
            available = newest(JsonParser.parseString(response.body()), NightshadeClient.VERSION, includeBeta);
            status = available == null ? "up to date" : "update " + available.version();
        } catch (Exception e) {
            status = "offline (" + e.getClass().getSimpleName() + ")";
            Log.info("Update check skipped: {}", e.toString());
        }
    }

    /** The newest listed version that is newer than {@code current}, or null. Package-private for tests. */
    static Update newest(JsonElement json, String current, boolean includeBeta) {
        if (json == null || !json.isJsonArray()) {
            return null;
        }
        Update best = null;
        JsonArray versions = json.getAsJsonArray();
        for (JsonElement element : versions) {
            JsonObject v = element.getAsJsonObject();
            String type = v.has("version_type") ? v.get("version_type").getAsString() : "release";
            if ("alpha".equals(type) || ("beta".equals(type) && !includeBeta)) {
                continue;
            }
            String number = v.get("version_number").getAsString();
            if (compare(number, current) <= 0 || (best != null && compare(number, best.version()) <= 0)) {
                continue;
            }
            best = new Update(number, PAGE + "/version/" + v.get("id").getAsString(), type);
        }
        return best;
    }

    /** Compares the numeric parts of two version strings ("1.16.0", "v1.17", "1.16.0-26.3"): -1, 0 or 1. */
    public static int compare(String a, String b) {
        int[] x = numbers(a);
        int[] y = numbers(b);
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? x[i] : 0;
            int q = i < y.length ? y[i] : 0;
            if (p != q) {
                return p < q ? -1 : 1;
            }
        }
        return 0;
    }

    private static int[] numbers(String version) {
        // Only the client version: stop at the first separator after the numbers ("1.16.0-26.3" -> 1.16.0).
        String core = version.replaceFirst("^[^0-9]*", "").split("[^0-9.]", 2)[0];
        String[] parts = core.split("\\.");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                out[i] = parts[i].isEmpty() ? 0 : Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                out[i] = 0;
            }
        }
        return out;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
