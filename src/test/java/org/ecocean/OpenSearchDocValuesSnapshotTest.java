package org.ecocean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.opensearch.client.Request;

class OpenSearchDocValuesSnapshotTest {
    private static final List<String> INDICES = Arrays.asList("encounter", "annotation", "individual");

    private static JSONObject shards(int failed) {
        return new JSONObject().put("total", 3).put("successful", 3 - failed).put("failed", failed);
    }

    private static JSONObject hit(String id) {
        return new JSONObject().put("_index", "annotation").put("_id", id)
            .put("sort", new JSONArray().put(id));
    }

    @Test void everyPageUsesTheSameUncachedPitAndItIsClosed() throws Exception {
        OpenSearch os = spy(new OpenSearch());
        List<JSONObject> searches = new ArrayList<JSONObject>();
        List<String> deletes = new ArrayList<String>();
        doAnswer(inv -> {
            Request request = inv.getArgument(0);
            if ("DELETE".equals(request.getMethod())) {
                deletes.add(EntityUtils.toString(request.getEntity()));
                return "{}";
            }
            if (request.getEndpoint().endsWith("point_in_time")) {
                assertEquals("false", request.getParameters().get("allow_partial_pit_creation"));
                return new JSONObject().put("pit_id", "audit-pit").put("_shards", shards(0)).toString();
            }
            JSONObject body = new JSONObject(EntityUtils.toString(request.getEntity()));
            searches.add(body);
            JSONArray hits = new JSONArray();
            if (searches.size() == 1) hits.put(hit("a1")).put(hit("a2"));
            return new JSONObject().put("pit_id", "audit-pit-next").put("_shards", shards(0))
                .put("hits", new JSONObject().put("hits", hits)).toString();
        }).when(os).getRestResponse(any(Request.class));
        List<String> ids = new ArrayList<String>();
        try (OpenSearch.DocValuesSnapshot view = os.openDocValuesSnapshot(INDICES, "5m")) {
            view.scan("annotation", Arrays.asList("viewUsers"), 2, hits -> {
                for (int i = 0; i < hits.length(); i++) ids.add(hits.getJSONObject(i).getString("_id"));
            });
        }
        assertEquals(Arrays.asList("a1", "a2"), ids);
        assertEquals(2, searches.size());
        assertFalse(searches.get(0).getBoolean("_source"));
        assertEquals("audit-pit", searches.get(0).getJSONObject("pit").getString("id"));
        assertEquals("audit-pit-next", searches.get(1).getJSONObject("pit").getString("id"));
        assertEquals("a2", searches.get(1).getJSONArray("search_after").getString(0));
        assertTrue(deletes.get(0).contains("audit-pit-next"));
    }

    @Test void failedOrMalformedPageClosesThePitWithoutReopeningIt() throws Exception {
        for (String failure : Arrays.asList("expired", "partial", "missing-sort")) {
            OpenSearch os = spy(new OpenSearch());
            int[] openedAndClosed = { 0, 0 };
            doAnswer(inv -> {
                Request request = inv.getArgument(0);
                if ("DELETE".equals(request.getMethod())) { openedAndClosed[1]++; return "{}"; }
                if (request.getEndpoint().endsWith("point_in_time")) {
                    openedAndClosed[0]++;
                    return new JSONObject().put("pit_id", "audit-pit").put("_shards", shards(0)).toString();
                }
                if ("expired".equals(failure)) throw new IOException("PIT expired");
                JSONObject hit = hit("a1");
                if ("missing-sort".equals(failure)) hit.remove("sort");
                return new JSONObject().put("_shards", shards("partial".equals(failure) ? 1 : 0))
                    .put("hits", new JSONObject().put("hits", new JSONArray().put(hit))).toString();
            }).when(os).getRestResponse(any(Request.class));
            try (OpenSearch.DocValuesSnapshot view = os.openDocValuesSnapshot(INDICES, "5m")) {
                assertThrows(IOException.class, () -> view.scan("annotation", Arrays.asList("viewUsers"),
                    2, hits -> { throw new AssertionError("unusable page reached the audit"); }));
            }
            assertEquals(1, openedAndClosed[0], failure);
            assertEquals(1, openedAndClosed[1], failure);
        }
    }

    @Test void partiallyCreatedPitIsRejectedAndClosed() throws Exception {
        OpenSearch os = spy(new OpenSearch());
        int[] closed = { 0 };
        doAnswer(inv -> {
            Request request = inv.getArgument(0);
            if ("DELETE".equals(request.getMethod())) { closed[0]++; return "{}"; }
            return new JSONObject().put("pit_id", "partial-pit").put("_shards", shards(1)).toString();
        }).when(os).getRestResponse(any(Request.class));
        assertThrows(IOException.class, () -> os.openDocValuesSnapshot(INDICES, "5m"));
        assertEquals(1, closed[0]);
    }
}
