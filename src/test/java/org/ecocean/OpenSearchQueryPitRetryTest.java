package org.ecocean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import org.apache.http.HttpHost;
import org.apache.http.ProtocolVersion;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicRequestLine;
import org.apache.http.message.BasicStatusLine;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.opensearch.client.Request;
import org.opensearch.client.Response;
import org.opensearch.client.ResponseException;

/**
 * Pins queryPit()'s retry contract: stale/expired PIT errors are retried with a fresh
 * PIT (bounded), everything else propagates after a single attempt, and the cached PIT
 * is always discarded on failure so a bad PIT cannot poison subsequent searches.
 */
class OpenSearchQueryPitRetryTest {
    private static final String STALE_BODY =
        "{\"error\":{\"root_cause\":[{\"type\":\"search_context_missing_exception\","
        + "\"reason\":\"No search context found for id [123]\"}]},\"status\":404}";
    private static final String WINDOW_BODY =
        "{\"error\":{\"root_cause\":[{\"type\":\"illegal_argument_exception\",\"reason\":"
        + "\"Result window is too large, from + size must be less than or equal to:"
        + " [12004] but was [147080]\"}]},\"status\":400}";

    private static ResponseException respEx(int status, String body) throws IOException {
        ProtocolVersion http = new ProtocolVersion("HTTP", 1, 1);
        Response r = mock(Response.class);

        when(r.getRequestLine()).thenReturn(new BasicRequestLine("POST", "/_search", http));
        when(r.getHost()).thenReturn(new HttpHost("opensearch", 9200, "http"));
        when(r.getStatusLine()).thenReturn(new BasicStatusLine(http, status, ""));
        when(r.getEntity()).thenReturn(new StringEntity(body, ContentType.APPLICATION_JSON));
        return new ResponseException(r);
    }

    private OpenSearch spyOs() throws IOException {
        OpenSearch os = spy(new OpenSearch());

        doReturn("pit-1").when(os).createPit(anyString());
        doNothing().when(os).deletePit(anyString());
        return os;
    }

    @Test void deterministicRejection_singleAttempt_pitDiscarded() throws Exception {
        OpenSearch os = spyOs();

        doThrow(respEx(400, WINDOW_BODY)).when(os).getRestResponse(any(Request.class));
        assertThrows(ResponseException.class,
            () -> os.queryPit("encounter", new JSONObject(), 147060, 20, null, null));
        verify(os, times(1)).getRestResponse(any(Request.class));
        // the cached PIT is discarded even on non-retryable failures - a possibly-bad
        // cached PIT must never poison every subsequent search on the index
        verify(os, times(1)).deletePit("encounter");
    }

    @Test void stalePit_retriedOnceWithFreshPit() throws Exception {
        OpenSearch os = spyOs();

        doThrow(respEx(404, STALE_BODY)).doReturn("{\"hits\":{\"hits\":[]}}")
            .when(os).getRestResponse(any(Request.class));
        JSONObject res = os.queryPit("encounter", new JSONObject(), 0, 10, null, null);
        assertNotNull(res.optJSONObject("hits"));
        verify(os, times(2)).getRestResponse(any(Request.class));
        verify(os, times(2)).createPit("encounter");
    }

    @Test void persistentStalePit_givesUpAfterBoundedAttempts() throws Exception {
        OpenSearch os = spyOs();

        doThrow(respEx(404, STALE_BODY)).when(os).getRestResponse(any(Request.class));
        IOException ex = assertThrows(IOException.class,
            () -> os.queryPit("encounter", new JSONObject(), 0, 10, null, null));
        assertTrue(ex.getMessage().contains("failed to POST query"));
        verify(os, times(6)).getRestResponse(any(Request.class));
    }

    @Test void bare404WithoutStaleContextBody_isNotRetried() throws Exception {
        OpenSearch os = spyOs();

        // e.g. a routing/proxy 404 - deterministic, must not burn 6 PITs
        doThrow(respEx(404, "{\"error\":\"no handler found for uri\"}"))
            .when(os).getRestResponse(any(Request.class));
        assertThrows(ResponseException.class,
            () -> os.queryPit("encounter", new JSONObject(), 0, 10, null, null));
        verify(os, times(1)).getRestResponse(any(Request.class));
    }

    @Test void retryBudgetIsPerCall_notPerInstance() throws Exception {
        OpenSearch os = spyOs();

        // exhaust one call's budget...
        doThrow(respEx(404, STALE_BODY)).when(os).getRestResponse(any(Request.class));
        assertThrows(IOException.class,
            () -> os.queryPit("encounter", new JSONObject(), 0, 10, null, null));
        // ...then a fresh call on the SAME instance must get a full budget again
        doThrow(respEx(404, STALE_BODY)).doReturn("{\"hits\":{\"hits\":[]}}")
            .when(os).getRestResponse(any(Request.class));
        JSONObject res = os.queryPit("encounter", new JSONObject(), 0, 10, null, null);
        assertNotNull(res.optJSONObject("hits"));
    }
}
