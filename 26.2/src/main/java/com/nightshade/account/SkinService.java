package com.nightshade.account;

import com.nightshade.net.Http;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

/** Uploads or resets the skin of a premium account through Minecraft's official services API. */
public final class SkinService {
    private static final String SKINS = "https://api.minecraftservices.com/minecraft/profile/skins";
    private final HttpClient http = Http.client(Duration.ofSeconds(15)).build();

    /** Uploads PNG bytes as the account's skin. {@code slim} selects the thin-arm (Alex) model. */
    public void upload(String accessToken, byte[] png, boolean slim) throws IOException, InterruptedException {
        String boundary = "----Nightshade" + UUID.randomUUID().toString().replace("-", "");
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        String head = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"variant\"\r\n\r\n" + (slim ? "slim" : "classic") + "\r\n"
                + "--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"skin.png\"\r\nContent-Type: image/png\r\n\r\n";
        body.write(head.getBytes(StandardCharsets.UTF_8));
        body.write(png);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(URI.create(SKINS)).timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build();
        check(http.send(request, HttpResponse.BodyHandlers.ofString()));
    }

    /** Resets the account to the default skin. */
    public void reset(String accessToken) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(SKINS + "/active")).timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + accessToken).DELETE().build();
        check(http.send(request, HttpResponse.BodyHandlers.ofString()));
    }

    private static void check(HttpResponse<String> response) throws IOException {
        int code = response.statusCode();
        if (code == 401) {
            throw new IOException("Session expired - switch to the account again and retry");
        }
        if (code == 429) {
            throw new IOException("Too many skin changes - wait a minute and retry");
        }
        if (code < 200 || code >= 300) {
            throw new IOException("Minecraft services returned HTTP " + code);
        }
    }
}
