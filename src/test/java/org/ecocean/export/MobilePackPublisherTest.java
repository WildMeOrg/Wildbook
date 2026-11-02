package org.ecocean.export;

import static org.junit.jupiter.api.Assertions.*;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MobilePackPublisherTest {
    @TempDir Path directory;

    private static JSONObject descriptor() {
        return new JSONObject().put("packId", "pk-one").put("version", "1");
    }

    private Exception publishAgainst(int status) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/publications", exchange -> {
            byte[] body = "{\"error\":\"nope\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            // The public constructor validates the configured HTTPS base; the package-private one takes
            // the base as given so an in-process http server can stand in for the registry.
            MobilePackPublisher publisher = new MobilePackPublisher(
                "http://127.0.0.1:" + server.getAddress().getPort(), "secret");
            Path archive = Files.write(directory.resolve("pack.zip"), new byte[] {1, 2, 3});
            return assertThrows(Exception.class, () -> publisher.publish(descriptor(), archive));
        } finally {
            server.stop(0);
        }
    }

    @Test void mapsRegistryStatusesToActionableMessages() throws Exception {
        Exception conflict = publishAgainst(409);
        assertTrue(conflict instanceof IllegalArgumentException, conflict.toString());
        assertTrue(conflict.getMessage().startsWith("The pack registry reports a newer release"), conflict.getMessage());

        Exception forbidden = publishAgainst(403);
        assertTrue(forbidden instanceof IllegalArgumentException, forbidden.toString());
        assertTrue(forbidden.getMessage().startsWith("The pack registry rejected the publisher token"), forbidden.getMessage());

        Exception invalid = publishAgainst(400);
        assertTrue(invalid instanceof IllegalArgumentException, invalid.toString());
        assertTrue(invalid.getMessage().startsWith("The pack registry rejected the release metadata"), invalid.getMessage());

        Exception failure = publishAgainst(500);
        assertTrue(failure instanceof IOException, failure.toString());
        assertEquals("Pack registry returned HTTP 500", failure.getMessage());
    }

    @Test void sha256HelpersUseCommonsCodec() throws Exception {
        Path file = Files.write(directory.resolve("bytes"), "abc".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", MobilePackArtifacts.sha256(file));
        assertEquals("wb-" + org.apache.commons.codec.digest.DigestUtils.sha256Hex("https://one.example\nA-001"),
            MobilePackJobs.scopedIdentity("https://one.example", "A-001"));
    }
}
