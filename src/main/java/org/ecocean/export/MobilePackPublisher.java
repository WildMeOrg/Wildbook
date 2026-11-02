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
        this(validated(CommonConfiguration.getProperty("mobilePackServiceUrl", context)),
            CommonConfiguration.getApiAccessProperty("mobilePackPublisherToken", context));
    }

    /** Package-private: takes the base as given so tests can point at an in-process server. */
    MobilePackPublisher(String base, String token) {
        if (token == null || token.isBlank()) throw new IllegalArgumentException("Publisher token is not configured");
        this.base = base;
        this.token = token;
    }

    private static String validated(String base) {
        MobilePackConfiguration.https(base);
        return base;
    }

    private JSONObject post(String path, JSONObject body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(base.replaceAll("/+$", "") + path))
            .timeout(Duration.ofSeconds(60)).header("Authorization", "Bearer " + token)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        int status = response.statusCode();
        if (status >= 200 && status < 300) return new JSONObject(response.body());
        // Registry outcomes an administrator can act on are surfaced verbatim in the job status.
        if (status == 409)
            throw new IllegalArgumentException(
                "The pack registry reports a newer release for this pack; review the current publication before exporting again");
        if (status == 403)
            throw new IllegalArgumentException(
                "The pack registry rejected the publisher token or instance URL; check mobilePackPublisherToken and mobilePackInstanceUrl");
        if (status == 400)
            throw new IllegalArgumentException(
                "The pack registry rejected the release metadata; check the IA.json mobile profile");
        throw new IOException("Pack registry returned HTTP " + status);
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
