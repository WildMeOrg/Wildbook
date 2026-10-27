package org.ecocean;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RestClientErrorCodeTest {
    @Test void parsesBothRestClientMessageSpellings() {
        assertEquals(502, RestClient.parseErrorCode("HTTP error code = 502"));
        assertEquals(500, RestClient.parseErrorCode("HTTP error code : 500"));
    }

    @Test void acceptsWhitespaceAndExceptionContext() {
        assertEquals(503, RestClient.parseErrorCode(
            "java.io.IOException: HTTP error code\t=\t503 from POST"));
        assertEquals(400, RestClient.parseErrorCode("HTTP error code:400"));
    }

    @Test void acceptsNonstandardThreeDigitCodes() {
        assertEquals(608, RestClient.parseErrorCode("HTTP error code = 608"));
    }

    @Test void returnsMinusOneWithoutAMatchingCode() {
        for (String message : new String[] { null, "", "Connection refused", "HTTP 502",
            "HTTP error code = nope", "HTTP error code = 50" }) {
            assertEquals(-1, RestClient.parseErrorCode(message), "message: " + message);
        }
    }
}
