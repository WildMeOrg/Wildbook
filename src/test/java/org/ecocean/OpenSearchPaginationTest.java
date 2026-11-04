package org.ecocean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.opensearch.client.Request;

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

    // ===== isStaleSearchContextError: queryPit may retry ONLY stale/expired PIT errors,
    // matched on the typed OpenSearch error - not status codes or broad substrings =====

    @Test void staleContext_missingSearchContextIsRetryable() {
        assertTrue(OpenSearch.isStaleSearchContextError(
            "{\"error\":{\"caused_by\":{\"type\":\"search_context_missing_exception\"}}}"));
        assertTrue(OpenSearch.isStaleSearchContextError(
            "No search context found for id [42]"));
    }

    @Test void staleContext_bareMessageIsNotRetryable() {
        // a routing/proxy 404 or any other error without the typed cause must propagate
        assertFalse(OpenSearch.isStaleSearchContextError("no handler found for uri"));
        assertFalse(OpenSearch.isStaleSearchContextError("gone"));
    }

    @Test void staleContext_resultWindowRejectionIsNotRetryable() {
        // the exact class of error observed in production 2026-07-10 (issue #1680):
        // deterministic 400 that can never succeed on retry
        assertFalse(OpenSearch.isStaleSearchContextError(
            "Result window is too large, from + size must be less than or equal to:"
            + " [12004] but was [147080]"));
    }

    @Test void staleContext_nullMessageIsNotRetryable() {
        assertFalse(OpenSearch.isStaleSearchContextError(null));
    }

    // ===== max_result_window bump (background sync): grow-only, strict read, no preserve_existing =====

    private static String settingsJson(String indexName, Object window) {
        JSONObject index = new JSONObject();
        if (window != null) index.put("max_result_window", window);
        return new JSONObject().put(indexName, new JSONObject().put("settings",
            new JSONObject().put("index", index))).toString();
    }

    /** spy whose GET _settings answers with getBody (or throws getFailure) and which records every PUT */
    private static OpenSearch stubbed(String getBody, IOException getFailure, List<Request> puts)
    throws Exception {
        OpenSearch os = spy(new OpenSearch());
        doAnswer(inv -> {
            Request request = inv.getArgument(0);
            if ("PUT".equals(request.getMethod())) {
                puts.add(request);
                return "{\"acknowledged\":true}";
            }
            if (getFailure != null) throw getFailure;
            return getBody;
        }).when(os).getRestResponse(any(Request.class));
        return os;
    }

    private static int windowIn(Request put) throws Exception {
        return new JSONObject(EntityUtils.toString(put.getEntity())).getInt("index.max_result_window");
    }

    @Test void targetMaxResultWindow_isOnePointTwoTimesTheCount() {
        assertEquals(14624, OpenSearch.targetMaxResultWindow(12187)); // the stale value seen in the field
        assertEquals(653682, OpenSearch.targetMaxResultWindow(544735));
        assertEquals(12001, OpenSearch.targetMaxResultWindow(10001));
    }

    @Test void targetMaxResultWindow_leavesIndexesAtOrBelowTheDefaultAlone() {
        assertEquals(-1, OpenSearch.targetMaxResultWindow(10000));
        assertEquals(-1, OpenSearch.targetMaxResultWindow(0));
        assertEquals(-1, OpenSearch.targetMaxResultWindow(-5));
    }

    @Test void targetMaxResultWindow_clampsToIntegerRange() {
        assertEquals(Integer.MAX_VALUE, OpenSearch.targetMaxResultWindow(Integer.MAX_VALUE));
        assertEquals(Integer.MAX_VALUE, OpenSearch.targetMaxResultWindow(Long.MAX_VALUE));
    }

    @Test void ensureMaxResultWindow_raisesStoredWindowWithoutPreserveExisting() throws Exception {
        List<Request> puts = new ArrayList<Request>();
        OpenSearch os = stubbed(settingsJson("encounter", "14875"), null, puts);
        os.ensureMaxResultWindow("encounter", 544735);
        assertEquals(1, puts.size());
        assertEquals("encounter/_settings", puts.get(0).getEndpoint());
        assertTrue(puts.get(0).getParameters().isEmpty(),
            "preserve_existing=true made every update after the first a no-op");
        assertEquals(653682, windowIn(puts.get(0)));
    }

    @Test void ensureMaxResultWindow_neverLowersALargerStoredWindow() throws Exception {
        List<Request> puts = new ArrayList<Request>();
        OpenSearch os = stubbed(settingsJson("encounter", "660000"), null, puts);
        os.ensureMaxResultWindow("encounter", 544735);
        assertTrue(puts.isEmpty(), "a hand-raised window must survive the sync");
    }

    @Test void ensureMaxResultWindow_skipsWhenSettingsReadFails() throws Exception {
        List<Request> puts = new ArrayList<Request>();
        OpenSearch os = stubbed(null, new IOException("connection refused"), puts);
        os.ensureMaxResultWindow("encounter", 544735);
        assertTrue(puts.isEmpty(), "a failed read must not be mistaken for the default window");
    }

    @Test void ensureMaxResultWindow_skipsWhenSettingsResponseIsMalformed() throws Exception {
        List<Request> puts = new ArrayList<Request>();
        OpenSearch os = stubbed("{}", null, puts);
        os.ensureMaxResultWindow("encounter", 544735);
        assertTrue(puts.isEmpty());
    }

    @Test void ensureMaxResultWindow_skipsWhenStoredValueIsUnparseable() throws Exception {
        List<Request> puts = new ArrayList<Request>();
        OpenSearch os = stubbed(settingsJson("encounter", "lots"), null, puts);
        os.ensureMaxResultWindow("encounter", 544735);
        assertTrue(puts.isEmpty());
    }

    @Test void ensureMaxResultWindow_treatsAnAbsentSettingAsTheDefault() throws Exception {
        List<Request> puts = new ArrayList<Request>();
        OpenSearch os = stubbed(settingsJson("encounter", null), null, puts);
        os.ensureMaxResultWindow("encounter", 12187);
        assertEquals(1, puts.size());
        assertEquals(14624, windowIn(puts.get(0)));
    }

    @Test void ensureMaxResultWindow_doesNotTouchOpenSearchForSmallIndexes() throws Exception {
        List<Request> all = new ArrayList<Request>();
        OpenSearch os = spy(new OpenSearch());
        doAnswer(inv -> {
            all.add(inv.getArgument(0));
            return settingsJson("encounter", "10000");
        }).when(os).getRestResponse(any(Request.class));
        os.ensureMaxResultWindow("encounter", 10000);
        assertTrue(all.isEmpty(), "no GET and no PUT below the default window");
    }

    @Test void ensureMaxResultWindow_decidesFromTheLiveSettingNotTheCache() throws Exception {
        List<Request> puts = new ArrayList<Request>();
        String[] stored = { "14875" };
        OpenSearch os = spy(new OpenSearch());
        doAnswer(inv -> {
            Request request = inv.getArgument(0);
            if ("PUT".equals(request.getMethod())) {
                puts.add(request);
                return "{\"acknowledged\":true}";
            }
            return settingsJson("individual", stored[0]);
        }).when(os).getRestResponse(any(Request.class));
        assertEquals(14875, os.getMaxResultWindow("individual")); // primes the 60 s cache
        stored[0] = "660000"; // an operator raised it by hand since
        os.ensureMaxResultWindow("individual", 544735);
        assertTrue(puts.isEmpty(), "a stale cached value must not justify lowering the live setting");
    }

    @Test void ensureMaxResultWindow_refreshesTheCachedWindowRightAway() throws Exception {
        String[] stored = { "14875" };
        OpenSearch os = spy(new OpenSearch());
        doAnswer(inv -> {
            Request request = inv.getArgument(0);
            if ("PUT".equals(request.getMethod())) {
                assertEquals("annotation/_settings", request.getEndpoint());
                assertTrue(request.getParameters().isEmpty());
                stored[0] = Integer.toString(windowIn(request));
                return "{\"acknowledged\":true}";
            }
            return settingsJson("annotation", stored[0]);
        }).when(os).getRestResponse(any(Request.class));
        assertEquals(14875, os.getMaxResultWindow("annotation")); // primes the 60 s cache
        os.ensureMaxResultWindow("annotation", 786983);
        assertEquals(944380, os.getMaxResultWindow("annotation"),
            "the API gate must see the new window without waiting for the cache to expire");
    }
}
