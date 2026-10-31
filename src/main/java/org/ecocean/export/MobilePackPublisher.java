package org.ecocean.export;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import org.ecocean.CommonConfiguration;
import org.json.JSONObject;

/** Thin service client: metadata uses publisher auth; ZIP bytes use a scoped Blob SAS. */
public final class MobilePackPublisher {
    private final String base;
    private final String token;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NEVER).build();

    public MobilePackPublisher(String context) {
        base = CommonConfiguration.getProperty("mobilePackServiceUrl", context);
        MobilePackConfiguration.https(base);
        token = CommonConfiguration.getApiAccessProperty("mobilePackPublisherToken", context);
        if (token == null || token.isBlank()) throw new IllegalArgumentException("Publisher token is not configured");
    }

    private JSONObject post(String path, JSONObject body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(base.replaceAll("/+$", "") + path))
            .timeout(Duration.ofSeconds(60)).header("Authorization", "Bearer " + token)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300)
            throw new IOException("Pack registry returned HTTP " + response.statusCode());
        return new JSONObject(response.body());
    }

    public JSONObject publish(JSONObject descriptor, Path archive) throws Exception {
        JSONObject reservation = post("/api/v1/publications", descriptor);
        if (!reservation.optBoolean("alreadyUploaded", false)) {
            URI upload = URI.create(reservation.getString("uploadUrl"));
            if (!"https".equals(upload.getScheme()) || upload.getUserInfo() != null || upload.getFragment() != null)
                throw new IOException("Invalid artifact upload URL");
            HttpRequest request = HttpRequest.newBuilder(upload).timeout(Duration.ofMinutes(20))
                .header("x-ms-blob-type", "BlockBlob").header("Content-Type", "application/zip")
                .header("x-ms-blob-cache-control", "public, max-age=31536000, immutable")
                .header("If-None-Match", "*").PUT(HttpRequest.BodyPublishers.ofFile(archive)).build();
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() < 200 || response.statusCode() >= 300)
                throw new IOException("Artifact upload returned HTTP " + response.statusCode());
        }
        return post("/api/v1/publications/" + descriptor.getString("packId") + "/" +
            descriptor.getString("version") + "/complete", new JSONObject());
    }
}
