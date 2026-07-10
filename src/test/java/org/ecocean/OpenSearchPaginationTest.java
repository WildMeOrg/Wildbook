package org.ecocean;

import static org.junit.jupiter.api.Assertions.*;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

class OpenSearchPaginationTest {
    // ===== parseMaxResultWindow: reads the live index setting, defaults to 10000 =====

    @Test void parseMaxResultWindow_readsNumericStringSetting() {
        // OpenSearch _settings returns values as strings
        JSONObject settings = new JSONObject("{\"max_result_window\":\"12004\"}");

        assertEquals(12004, OpenSearch.parseMaxResultWindow(settings));
    }

    @Test void parseMaxResultWindow_readsIntegerSetting() {
        JSONObject settings = new JSONObject().put("max_result_window", 20000);

        assertEquals(20000, OpenSearch.parseMaxResultWindow(settings));
    }

    @Test void parseMaxResultWindow_defaultsWhenAbsent() {
        assertEquals(OpenSearch.DEFAULT_MAX_RESULT_WINDOW,
            OpenSearch.parseMaxResultWindow(new JSONObject()));
    }

    @Test void parseMaxResultWindow_defaultsWhenNull() {
        assertEquals(OpenSearch.DEFAULT_MAX_RESULT_WINDOW, OpenSearch.parseMaxResultWindow(null));
    }

    @Test void parseMaxResultWindow_defaultsOnUnparseableValue() {
        JSONObject settings = new JSONObject("{\"max_result_window\":\"not-a-number\"}");

        assertEquals(OpenSearch.DEFAULT_MAX_RESULT_WINDOW,
            OpenSearch.parseMaxResultWindow(settings));
    }

    @Test void parseMaxResultWindow_defaultsOnNonPositiveValue() {
        // a broken/misconfigured setting must never lock search out entirely
        assertEquals(OpenSearch.DEFAULT_MAX_RESULT_WINDOW,
            OpenSearch.parseMaxResultWindow(new JSONObject("{\"max_result_window\":\"0\"}")));
        assertEquals(OpenSearch.DEFAULT_MAX_RESULT_WINDOW,
            OpenSearch.parseMaxResultWindow(new JSONObject("{\"max_result_window\":\"-1\"}")));
    }

    // ===== isStaleSearchContextError: queryPit may retry ONLY stale/expired PIT errors =====

    @Test void staleContext_missingSearchContextIsRetryable() {
        assertTrue(OpenSearch.isStaleSearchContextError(404,
            "{\"error\":{\"caused_by\":{\"type\":\"search_context_missing_exception\"}}}"));
        assertTrue(OpenSearch.isStaleSearchContextError(400,
            "search_context_missing_exception: No search context found for id"));
    }

    @Test void staleContext_404IsRetryable() {
        assertTrue(OpenSearch.isStaleSearchContextError(404, "gone"));
    }

    @Test void staleContext_resultWindowRejectionIsNotRetryable() {
        // the exact class of error observed in production 2026-07-10 (issue #1680):
        // deterministic 400 that can never succeed on retry
        assertFalse(OpenSearch.isStaleSearchContextError(400,
            "Result window is too large, from + size must be less than or equal to:"
            + " [12004] but was [147080]"));
    }

    @Test void staleContext_nullMessageIsNotRetryable() {
        assertFalse(OpenSearch.isStaleSearchContextError(400, null));
    }
}
