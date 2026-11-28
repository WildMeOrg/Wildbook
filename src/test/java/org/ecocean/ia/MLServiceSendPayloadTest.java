package org.ecocean.ia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mockStatic;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import com.sun.net.httpserver.HttpServer;

import org.ecocean.RestClient;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class MLServiceSendPayloadTest {
    private static final String ENDPOINT = "http://localhost/predict/";

    // Exercise the actual legacy catch ladder without loading IA configuration,
    // opening an HTTP connection, or widening production visibility for tests.
    private static JSONObject sendPayload(JSONObject payload) throws Exception {
        return sendPayload(ENDPOINT, payload);
    }

    private static JSONObject sendPayload(String endpoint, JSONObject payload) throws Exception {
        Method send = MLService.class.getDeclaredMethod("sendPayload", String.class,
            JSONObject.class);
        send.setAccessible(true);
        return (JSONObject)send.invoke(new MLService(null), endpoint, payload);
    }

    private static IAException failure(String message) throws Exception {
        try (MockedStatic<RestClient> rest = mockStatic(RestClient.class, invocation -> {
            if (invocation.getMethod().getName().equals("postJSON")) {
                throw new IOException(message);
            }
            // In particular, run the real shared parser rather than Mockito's
            // default zero return for unstubbed static methods.
            return invocation.callRealMethod();
        })) {
            InvocationTargetException thrown = assertThrows(InvocationTargetException.class,
                () -> sendPayload(new JSONObject()));
            return assertInstanceOf(IAException.class, thrown.getCause());
        }
    }

    private static void assertRetry(IAException failure, boolean increment) {
        assertEquals(true, failure.shouldRequeue(), failure.getMessage());
        assertEquals(increment, failure.shouldIncrement(), failure.getMessage());
    }

    @Test void badGatewayRetriesWithoutIncrementForBothSpellings() throws Exception {
        for (String separator : new String[] { " = ", " : " }) {
            IAException ex = failure("HTTP error code" + separator + "502");
            assertRetry(ex, false);
            assertEquals("502 error", ex.getMessage());
        }
    }

    @Test void actualHttp502RetriesWithoutIncrement() throws Exception {
        // Covers RestClient's real transport message, not a hand-written exception.
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/predict/", exchange -> {
            try {
                exchange.getRequestBody().readAllBytes();
                byte[] body = "Bad Gateway".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(502, body.length);
                exchange.getResponseBody().write(body);
            } finally {
                exchange.close();
            }
        });
        server.start();
        try {
            String endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/predict/";
            InvocationTargetException thrown = assertThrows(InvocationTargetException.class,
                () -> sendPayload(endpoint, new JSONObject()));
            IAException failure = assertInstanceOf(IAException.class, thrown.getCause());
            assertRetry(failure, false);
            assertEquals("502 error", failure.getMessage());
        } finally {
            server.stop(0);
        }
    }

    @Test void serverErrorRetriesWithIncrementForBothSpellings() throws Exception {
        for (String separator : new String[] { " = ", " : " }) {
            IAException ex = failure("HTTP error code" + separator + "500");
            assertRetry(ex, true);
            assertEquals("500 error", ex.getMessage());
        }
    }

    @Test void clientErrorsRetainLegacyRetryWithIncrement() throws Exception {
        for (int code : new int[] { 400, 408, 429 }) {
            assertRetry(failure("HTTP error code = " + code), true);
        }
    }

    @Test void readTimeoutRetriesWithoutIncrement() throws Exception {
        assertRetry(failure("Read timed out"), false);
    }

    @Test void connectionRefusedRetriesWithIncrement() throws Exception {
        assertRetry(failure("Connection refused"), true);
    }

    @Test void unmatchedAndOtherStatusesRetainDefaultRetryWithIncrement() throws Exception {
        for (String message : new String[] { null, "unknown failure", "HTTP error code = 503",
            "HTTP error code = 608" }) {
            assertRetry(failure(message), true);
        }
    }

    @Test void successfulResponsePassesThrough() throws Exception {
        JSONObject response = new JSONObject().put("success", true);
        try (MockedStatic<RestClient> rest = mockStatic(RestClient.class, invocation -> {
            if (invocation.getMethod().getName().equals("postJSON")) return response;
            return invocation.callRealMethod();
        })) {
            assertSame(response, sendPayload(new JSONObject()));
        }
    }
}
