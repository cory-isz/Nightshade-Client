package com.nightshade.mods;

import com.nightshade.net.Http;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nightshade.NightshadeClient;
import com.nightshade.util.Log;
import net.minecraft.SharedConstants;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Modrinth API v2 client (https://docs.modrinth.com/api/): search Fabric mods for the running Minecraft version,
 * install them with their required dependencies (files verified against Modrinth's SHA-512), and check installed
 * jars for updates by file hash. All calls are blocking; callers run them off the render thread.
 */
public final class ModrinthClient {
    private static final String API = "https://api.modrinth.com/v2";
    private final HttpClient http = Http.client(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
    private final String gameVersion = SharedConstants.getCurrentVersion().name();

    public record SearchHit(String projectId, String slug, String title, String description, String author,
                            long downloads, @Nullable String iconUrl) {
    }

    public record RemoteFile(String url, String filename, String sha512, String sha1) {
    }

    public record Version(String id, String projectId, String versionNumber, String versionType, RemoteFile file,
                          List<String> requiredProjects) {
    }

    /** An installed jar matched to a Modrinth version. */
    public record InstalledMatch(Path jar, String sha1, Version current) {
    }

    private String userAgent() {
        return "Nightshade-Client/" + NightshadeClient.VERSION + " (Minecraft " + gameVersion + ")";
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * Sends a read-only API request, retrying up to twice on timeouts, dropped connections, rate limiting (429)
     * and server errors (5xx) with a short back-off. Every API call made here is a query, so retrying is safe.
     */
    private HttpResponse<String> sendWithRetry(HttpRequest request) throws IOException, InterruptedException {
        IOException last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            if (attempt > 0) {
                Thread.sleep(attempt * 1500L);
            }
            try {
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                int code = response.statusCode();
                if ((code == 429 || code >= 500) && attempt < 2) {
                    last = new IOException("Modrinth returned HTTP " + code);
                    continue;
                }
                return response;
            } catch (java.net.http.HttpTimeoutException | java.net.ConnectException e) {
                last = e;
            } catch (IOException e) {
                if (e instanceof javax.net.ssl.SSLHandshakeException) {
                    throw e; // a certificate problem will not fix itself on retry
                }
                last = e;
            }
        }
        throw last;
    }

    private JsonElement get(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(API + path)).timeout(Duration.ofSeconds(25))
                .header("User-Agent", userAgent()).GET().build();
        HttpResponse<String> response = sendWithRetry(request);
        if (response.statusCode() == 404) {
            return null;
        }
        if (response.statusCode() != 200) {
            throw new IOException("Modrinth returned HTTP " + response.statusCode());
        }
        return JsonParser.parseString(response.body());
    }

    private JsonElement post(String path, JsonObject body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(API + path)).timeout(Duration.ofSeconds(25))
                .header("User-Agent", userAgent()).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
        HttpResponse<String> response = sendWithRetry(request);
        if (response.statusCode() != 200) {
            throw new IOException("Modrinth returned HTTP " + response.statusCode());
        }
        return JsonParser.parseString(response.body());
    }

    // ------------------------------------------------------------------ search & versions

    public List<SearchHit> search(String query, int offset) throws IOException, InterruptedException {
        return search(query, offset, "[[\"versions:" + gameVersion + "\"],[\"categories:fabric\"],[\"project_type:mod\"]]");
    }

    /** Resource packs that list this Minecraft version. */
    public List<SearchHit> searchResourcePacks(String query, int offset) throws IOException, InterruptedException {
        return search(query, offset, "[[\"versions:" + gameVersion + "\"],[\"project_type:resourcepack\"]]");
    }

    /** Sort orders Modrinth's search supports, as shown in the browser. */
    public static final String[] SORTS = {"Popular", "Relevance", "Most followed", "Newest", "Recently updated"};
    private static final String[] SORT_INDEX = {"downloads", "relevance", "follows", "newest", "updated"};

    /** Fabric mods for this Minecraft version in one Modrinth category (null = any), in the given sort order. */
    public List<SearchHit> search(String query, int offset, @Nullable String category, int sort) throws IOException, InterruptedException {
        String facets = "[[\"versions:" + gameVersion + "\"],[\"categories:fabric\"],[\"project_type:mod\"]"
                + (category == null ? "" : ",[\"categories:" + category + "\"]") + "]";
        return search(query, offset, facets, SORT_INDEX[Math.floorMod(sort, SORT_INDEX.length)]);
    }

    private List<SearchHit> search(String query, int offset, String facets) throws IOException, InterruptedException {
        return search(query, offset, facets, query.isBlank() ? "downloads" : "relevance");
    }

    /**
     * Several projects by id or slug in one request, keeping the given order and only those with a Fabric version for
     * this Minecraft version (used for the Recommended list). Author is left empty: the bulk endpoint does not have it.
     */
    public List<SearchHit> projects(List<String> idsOrSlugs) throws IOException, InterruptedException {
        JsonArray ids = new JsonArray();
        idsOrSlugs.forEach(ids::add);
        JsonElement root = get("/projects?ids=" + enc(ids.toString()));
        List<SearchHit> out = new ArrayList<>();
        if (root == null || !root.isJsonArray()) {
            return out;
        }
        Map<String, SearchHit> bySlug = new HashMap<>();
        for (JsonElement element : root.getAsJsonArray()) {
            JsonObject o = element.getAsJsonObject();
            if (!contains(o, "game_versions", gameVersion) || !contains(o, "loaders", "fabric")) {
                continue;
            }
            SearchHit hit = new SearchHit(str(o, "id"), str(o, "slug"), str(o, "title"), str(o, "description"), "",
                    o.has("downloads") ? o.get("downloads").getAsLong() : 0L,
                    o.has("icon_url") && !o.get("icon_url").isJsonNull() ? o.get("icon_url").getAsString() : null);
            bySlug.put(hit.slug(), hit);
            bySlug.put(hit.projectId(), hit);
        }
        for (String key : idsOrSlugs) {
            SearchHit hit = bySlug.get(key);
            if (hit != null && !out.contains(hit)) {
                out.add(hit);
            }
        }
        return out;
    }

    private static boolean contains(JsonObject o, String key, String value) {
        if (!o.has(key) || !o.get(key).isJsonArray()) {
            return false;
        }
        for (JsonElement e : o.getAsJsonArray(key)) {
            if (value.equals(e.getAsString())) {
                return true;
            }
        }
        return false;
    }

    /** The full Modrinth page of a project, for the details panel. */
    public record ProjectDetails(String body, List<String> categories, long followers, String license, String updated,
                                 String clientSide, String serverSide, List<String> dependencyNames) {
    }

    /** Loads a project's page and the names of the required dependencies its newest compatible version would install. */
    public ProjectDetails details(String projectId) throws IOException, InterruptedException {
        JsonElement root = get("/project/" + enc(projectId));
        if (root == null || !root.isJsonObject()) {
            throw new IOException("Project not found");
        }
        JsonObject o = root.getAsJsonObject();
        List<String> categories = new ArrayList<>();
        if (o.has("categories") && o.get("categories").isJsonArray()) {
            for (JsonElement e : o.getAsJsonArray("categories")) {
                categories.add(e.getAsString());
            }
        }
        String license = o.has("license") && o.get("license").isJsonObject() ? str(o.getAsJsonObject("license"), "id") : "";
        String updated = str(o, "updated");
        List<String> dependencyNames = new ArrayList<>();
        Version version = latestVersion(projectId);
        if (version != null && !version.requiredProjects().isEmpty()) {
            net.fabricmc.loader.api.FabricLoader loader = net.fabricmc.loader.api.FabricLoader.getInstance();
            for (SearchHit dep : projectsAnyVersion(version.requiredProjects())) {
                if (!loader.isModLoaded(dep.slug()) && !loader.isModLoaded(dep.slug().replace('-', '_'))) {
                    dependencyNames.add(dep.title()); // only what would really be downloaded
                }
            }
        }
        return new ProjectDetails(str(o, "body"), categories, o.has("followers") ? o.get("followers").getAsLong() : 0L,
                license, updated.length() >= 10 ? updated.substring(0, 10) : updated, str(o, "client_side"), str(o, "server_side"), dependencyNames);
    }

    /** Like {@link #projects} but without the version filter (dependency names). */
    private List<SearchHit> projectsAnyVersion(List<String> ids) throws IOException, InterruptedException {
        JsonArray array = new JsonArray();
        ids.forEach(array::add);
        JsonElement root = get("/projects?ids=" + enc(array.toString()));
        List<SearchHit> out = new ArrayList<>();
        if (root != null && root.isJsonArray()) {
            for (JsonElement element : root.getAsJsonArray()) {
                JsonObject o = element.getAsJsonObject();
                out.add(new SearchHit(str(o, "id"), str(o, "slug"), str(o, "title"), "", "", 0L, null));
            }
        }
        return out;
    }

    private List<SearchHit> search(String query, int offset, String facets, String index) throws IOException, InterruptedException {
        JsonElement root = get("/search?limit=25&offset=" + offset + "&index=" + index
                + "&query=" + enc(query) + "&facets=" + enc(facets));
        List<SearchHit> hits = new ArrayList<>();
        if (root == null) {
            return hits;
        }
        for (JsonElement element : root.getAsJsonObject().getAsJsonArray("hits")) {
            JsonObject o = element.getAsJsonObject();
            hits.add(new SearchHit(str(o, "project_id"), str(o, "slug"), str(o, "title"), str(o, "description"),
                    str(o, "author"), o.has("downloads") ? o.get("downloads").getAsLong() : 0L,
                    o.has("icon_url") && !o.get("icon_url").isJsonNull() ? o.get("icon_url").getAsString() : null));
        }
        return hits;
    }

    /** Newest compatible version (releases preferred over betas), or null when none exists for this version. */
    @Nullable
    public Version latestVersion(String projectId) throws IOException, InterruptedException {
        JsonElement root = get("/project/" + enc(projectId) + "/version?loaders=" + enc("[\"fabric\"]")
                + "&game_versions=" + enc("[\"" + gameVersion + "\"]"));
        if (root == null || !root.isJsonArray() || root.getAsJsonArray().isEmpty()) {
            return null;
        }
        Version fallback = null;
        for (JsonElement element : root.getAsJsonArray()) {
            Version version = parseVersion(element.getAsJsonObject());
            if (version == null) {
                continue;
            }
            if (version.versionType().equals("release")) {
                return version;
            }
            if (fallback == null) {
                fallback = version;
            }
        }
        return fallback;
    }

    /** Newest resource-pack version for this Minecraft version (releases preferred), or null. */
    @Nullable
    public Version latestResourcePackVersion(String projectId) throws IOException, InterruptedException {
        JsonElement root = get("/project/" + enc(projectId) + "/version?game_versions=" + enc("[\"" + gameVersion + "\"]"));
        if (root == null || !root.isJsonArray()) {
            return null;
        }
        Version fallback = null;
        for (JsonElement element : root.getAsJsonArray()) {
            Version version = parseVersion(element.getAsJsonObject());
            if (version == null || !version.file().filename().toLowerCase(Locale.ROOT).endsWith(".zip")) {
                continue;
            }
            if (version.versionType().equals("release")) {
                return version;
            }
            if (fallback == null) {
                fallback = version;
            }
        }
        return fallback;
    }

    /**
     * Downloads a resource pack into {@code packsDir}. Returns the file name, or null if the exact same file was
     * already there.
     */
    @Nullable
    public String installResourcePack(String projectId, Path packsDir, Consumer<String> status) throws IOException, InterruptedException {
        status.accept("Finding a version...");
        Version version = latestResourcePackVersion(projectId);
        if (version == null) {
            throw new IOException("No version for Minecraft " + gameVersion);
        }
        String name = safeFileName(version.file().filename());
        Path existing = packsDir.resolve(name);
        if (Files.exists(existing) && hash(existing, "SHA-512").equalsIgnoreCase(version.file().sha512())) {
            return null;
        }
        status.accept("Downloading " + name + "...");
        download(version.file(), packsDir, ".zip");
        return name;
    }

    @Nullable
    private static Version parseVersion(JsonObject o) {
        JsonArray files = o.getAsJsonArray("files");
        JsonObject chosen = null;
        for (JsonElement f : files) {
            JsonObject file = f.getAsJsonObject();
            if (chosen == null || (file.has("primary") && file.get("primary").getAsBoolean())) {
                chosen = file;
            }
        }
        if (chosen == null) {
            return null;
        }
        JsonObject hashes = chosen.getAsJsonObject("hashes");
        RemoteFile file = new RemoteFile(str(chosen, "url"), str(chosen, "filename"), str(hashes, "sha512"), str(hashes, "sha1"));
        List<String> required = new ArrayList<>();
        if (o.has("dependencies")) {
            for (JsonElement d : o.getAsJsonArray("dependencies")) {
                JsonObject dep = d.getAsJsonObject();
                if ("required".equals(str(dep, "dependency_type")) && dep.has("project_id") && !dep.get("project_id").isJsonNull()) {
                    required.add(dep.get("project_id").getAsString());
                }
            }
        }
        return new Version(str(o, "id"), str(o, "project_id"), str(o, "version_number"), str(o, "version_type"), file, required);
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
    }

    // ------------------------------------------------------------------ installed jars

    /** SHA-1 of every jar (enabled or disabled) in the mods folder, mapped to its path. */
    public static Map<String, Path> hashModsFolder(Path modsDir) {
        Map<String, Path> result = new HashMap<>();
        if (!Files.isDirectory(modsDir)) {
            return result;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(modsDir)) {
            for (Path path : stream) {
                String name = path.getFileName().toString();
                if (name.endsWith(".jar") || name.endsWith(".jar.disabled")) {
                    result.put(hash(path, "SHA-1"), path);
                }
            }
        } catch (IOException e) {
            Log.warn("Could not hash mods folder: {}", e.toString());
        }
        return result;
    }

    /** Matches installed jars to Modrinth versions (jars not on Modrinth are absent from the result). */
    public List<InstalledMatch> identify(Map<String, Path> hashes) throws IOException, InterruptedException {
        List<InstalledMatch> matches = new ArrayList<>();
        if (hashes.isEmpty()) {
            return matches;
        }
        JsonObject body = new JsonObject();
        JsonArray array = new JsonArray();
        hashes.keySet().forEach(array::add);
        body.add("hashes", array);
        body.addProperty("algorithm", "sha1");
        JsonObject root = post("/version_files", body).getAsJsonObject();
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            Version version = parseVersion(entry.getValue().getAsJsonObject());
            if (version != null) {
                matches.add(new InstalledMatch(hashes.get(entry.getKey()), entry.getKey(), version));
            }
        }
        return matches;
    }

    /** For each installed jar hash, the newest compatible version when it differs from the installed one. */
    public Map<String, Version> findUpdates(Map<String, Path> hashes) throws IOException, InterruptedException {
        Map<String, Version> updates = new HashMap<>();
        if (hashes.isEmpty()) {
            return updates;
        }
        JsonObject body = new JsonObject();
        JsonArray array = new JsonArray();
        hashes.keySet().forEach(array::add);
        body.add("hashes", array);
        body.addProperty("algorithm", "sha1");
        JsonArray loaders = new JsonArray();
        loaders.add("fabric");
        body.add("loaders", loaders);
        JsonArray versions = new JsonArray();
        versions.add(gameVersion);
        body.add("game_versions", versions);
        JsonObject root = post("/version_files/update", body).getAsJsonObject();
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            Version latest = parseVersion(entry.getValue().getAsJsonObject());
            if (latest != null && !latest.file().sha1().equalsIgnoreCase(entry.getKey())) {
                updates.put(entry.getKey(), latest);
            }
        }
        return updates;
    }

    // ------------------------------------------------------------------ install

    /**
     * Installs a project's newest compatible version plus any required dependencies that are not installed yet.
     * Returns the file names written. Progress messages go to {@code status}.
     */
    public List<String> install(String projectId, Path modsDir, Consumer<String> status) throws IOException, InterruptedException {
        Map<String, Path> hashes = hashModsFolder(modsDir);
        Set<String> installedProjects = new HashSet<>();
        for (InstalledMatch match : identify(hashes)) {
            installedProjects.add(match.current().projectId());
        }
        List<String> written = new ArrayList<>();
        installRecursive(projectId, modsDir, installedProjects, written, status, 0);
        return written;
    }

    private void installRecursive(String projectId, Path modsDir, Set<String> installed, List<String> written,
                                  Consumer<String> status, int depth) throws IOException, InterruptedException {
        if (installed.contains(projectId) || depth > 6) {
            return;
        }
        if (depth > 0 && isLoadedUnderSlug(projectId)) {
            installed.add(projectId);
            return; // e.g. Fabric API already installed from another source
        }
        Version version = latestVersion(projectId);
        if (version == null) {
            if (depth == 0) {
                throw new IOException("No version for Fabric " + gameVersion);
            }
            Log.warn("Required dependency {} has no Fabric {} version", projectId, gameVersion);
            return;
        }
        installed.add(projectId);
        installed.add(version.projectId());
        for (String dependency : version.requiredProjects()) {
            installRecursive(dependency, modsDir, installed, written, status, depth + 1);
        }
        status.accept("Downloading " + version.file().filename());
        download(version.file(), modsDir);
        written.add(version.file().filename());
    }

    /** True when a loaded mod's id matches the project's slug (Modrinth slugs usually equal Fabric mod ids). */
    private boolean isLoadedUnderSlug(String projectId) throws IOException, InterruptedException {
        JsonElement project = get("/project/" + enc(projectId));
        if (project == null) {
            return false;
        }
        String slug = str(project.getAsJsonObject(), "slug");
        net.fabricmc.loader.api.FabricLoader loader = net.fabricmc.loader.api.FabricLoader.getInstance();
        return !slug.isEmpty() && (loader.isModLoaded(slug) || loader.isModLoaded(slug.replace('-', '_')));
    }

    /** Downloads a file into the mods folder, verifying the SHA-512 Modrinth published for it. */
    public Path download(RemoteFile file, Path modsDir) throws IOException, InterruptedException {
        return download(file, modsDir, ".jar");
    }

    /** Downloads into {@code modsDir}; only files ending in {@code extension} are accepted. SHA-512 verified. */
    public Path download(RemoteFile file, Path modsDir, String extension) throws IOException, InterruptedException {
        String name = safeFileName(file.filename());
        if (!name.toLowerCase(Locale.ROOT).endsWith(extension)) {
            throw new IOException("Refusing to download " + name + " (expected a " + extension + " file)");
        }
        Files.createDirectories(modsDir);
        Path target = modsDir.resolve(name);
        if (Files.exists(target) && hash(target, "SHA-512").equalsIgnoreCase(file.sha512())) {
            return target; // already present
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(file.url())).timeout(Duration.ofMinutes(2))
                .header("User-Agent", userAgent()).GET().build();
        HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            throw new IOException("Download failed (HTTP " + response.statusCode() + ")");
        }
        Path temp = modsDir.resolve(name + ".part");
        try (InputStream in = response.body()) {
            Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
        }
        String actual = hash(temp, "SHA-512");
        if (!actual.equalsIgnoreCase(file.sha512())) {
            Files.deleteIfExists(temp);
            throw new IOException("Checksum mismatch for " + name + " - download discarded");
        }
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    /**
     * A file name that is safe on every file system and system encoding: no path separators, no formatting codes
     * (resource packs often put a coloured "\u00A7" code in their file name) and nothing outside printable ASCII.
     */
    public static String safeFileName(String raw) {
        String noCodes = raw.replaceAll("\u00A7.", "");
        StringBuilder out = new StringBuilder(noCodes.length());
        for (char c : noCodes.toCharArray()) {
            boolean ok = c >= 0x20 && c < 0x7F && c != '/' && c != '\\' && c != ':' && c != '*' && c != '?'
                    && c != '"' && c != '<' && c != '>' && c != '|';
            out.append(ok ? c : '_');
        }
        String name = out.toString().trim();
        return name.isEmpty() || name.startsWith(".") ? "download" + name : name;
    }

    public static String hash(Path file, String algorithm) {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException e) {
            return "";
        }
    }
}
