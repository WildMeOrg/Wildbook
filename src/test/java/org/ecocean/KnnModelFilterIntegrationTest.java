package org.ecocean;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.apache.http.HttpHost;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.opensearch.client.Request;
import org.opensearch.client.ResponseException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Real-OpenSearch coverage for filtering by model INSIDE the knn clause: when several models'
 * vectors share one field, all k neighbours must come from the query's model. `current` runs the
 * default engine of the image under test (default the fleet's pinned 3.1.0; run also with
 * -Dwildbook.test.opensearchImage=opensearchproject/opensearch:2.19.1). `legacy` is pinned to
 * 2.17.1, whose default engine for these methodless fields (NMSLIB) cannot filter inside knn, to
 * cover the post-filter fallback.
 */
@Testcontainers class KnnModelFilterIntegrationTest {

    private static final String INDEX = "annotation";
    private static final int DIM = 1536;
    private static final String FIELD = "embeddings." + Embedding.vectorFieldName(DIM);

    @Container static GenericContainer<?> current = container(System.getProperty(
        "wildbook.test.opensearchImage", "opensearchproject/opensearch:3.1.0"));
    @Container static GenericContainer<?> legacy = container(
        "opensearchproject/opensearch:2.17.1");

    private static GenericContainer<?> container(String image) {
        return new GenericContainer<>(DockerImageName.parse(image))
                .withExposedPorts(9200)
                .withEnv("discovery.type", "single-node")
                .withEnv("plugins.security.disabled", "true")
                .withEnv("OPENSEARCH_JAVA_OPTS", "-Xms512m -Xmx512m")
                .withEnv("DISABLE_INSTALL_DEMO_CONFIG", "true")
                .waitingFor(new HttpWaitStrategy()
                .forPort(9200)
                .forPath("/_cluster/health")
                .forStatusCode(200)
                .withStartupTimeout(java.time.Duration.ofMinutes(2)));
    }

    private OpenSearch os;
    private float[] query;
    private final Map<String, float[]> modelB = new HashMap<String, float[]>();

    // re-point the shared client at a container (also clears the per-field engine record)
    private void connect(GenericContainer<?> c) throws Exception {
        OpenSearch.initializeClient(new HttpHost(c.getHost(), c.getMappedPort(9200), "http"));
        OpenSearch.INDEX_EXISTS_CACHE.clear();
        os = new OpenSearch();
        if (os.existsIndex(INDEX)) os.deleteIndex(INDEX);
    }

    private String rest(String method, String endpoint, String body) throws IOException {
        Request req = new Request(method, endpoint);
        if (body != null) req.setJsonEntity(body);
        return os.getRestResponse(req);
    }

    private static float[] noisy(float[] base, double noise, Random rnd) {
        float[] v = new float[base.length];
        for (int i = 0; i < v.length; i++) v[i] = (float)(base[i] + noise * rnd.nextGaussian());
        return v;
    }

    private static JSONArray json(float[] v) {
        JSONArray a = new JSONArray();
        for (float f : v) a.put(f);
        return a;
    }

    private static double cosine(float[] a, float[] b) {
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        return dot / Math.sqrt(na * nb);
    }

    private static JSONObject emb(String method, float[] v) {
        return new JSONObject().put("method", method).put("methodVersion", "1")
                   .put(Embedding.vectorFieldName(DIM), json(v));
    }

    // nA model-A vectors very near the query, nB model-B vectors farther away (the last `dual` of
    // the B docs also carry an A vector), 3 model-C docs; merged to one segment so a filter that
    // leaves more than k eligible vectors runs the ANN path, not the small-set exact path
    private void load(String mappingJson, int nA, int nB, int dual) throws Exception {
        if (mappingJson == null) {
            os.ensureIndex(INDEX, new Annotation().opensearchMapping());
        } else {
            rest("PUT", "/" + INDEX, mappingJson);
        }
        Random rnd = new Random(42);
        query = new float[DIM];
        for (int i = 0; i < DIM; i++) query[i] = (float)rnd.nextGaussian();
        StringBuilder bulk = new StringBuilder();
        int n = 0;
        List<JSONObject> docs = new ArrayList<JSONObject>();
        for (int i = 0; i < nA; i++) {
            docs.add(new JSONObject().put("_id", "a" + i).put("embeddings",
                new JSONArray().put(emb("A", noisy(query, 0.2, rnd)))));
        }
        for (int i = 0; i < nB; i++) {
            float[] b = noisy(query, 0.6, rnd);
            modelB.put("b" + i, b);
            JSONArray embs = new JSONArray().put(emb("B", b));
            if (i >= nB - dual) embs.put(emb("A", noisy(query, 0.2, rnd)));
            docs.add(new JSONObject().put("_id", "b" + i).put("embeddings", embs));
        }
        for (int i = 0; i < 3; i++) {
            docs.add(new JSONObject().put("_id", "c" + i).put("embeddings",
                new JSONArray().put(emb("C", noisy(query, 0.6, rnd)))));
        }
        for (JSONObject d : docs) {
            bulk.append(new JSONObject().put("index", new JSONObject().put("_index", INDEX)
                .put("_id", d.remove("_id")))).append("\n");
            bulk.append(d.put("matchAgainst", true)).append("\n");
            if (++n % 100 == 0 || n == docs.size()) {
                JSONObject res = new JSONObject(rest("POST", "/_bulk", bulk.toString()));
                assertFalse(res.getBoolean("errors"), "bulk errors");
                bulk.setLength(0);
            }
        }
        rest("POST", "/" + INDEX + "/_forcemerge?max_num_segments=1", null);
        rest("POST", "/" + INDEX + "/_refresh", null);
    }

    private JSONObject matchQuery(String method) {
        Annotation ann = new Annotation();
        new Embedding(ann, method, "1", json(query));
        JSONObject matchingSet = new JSONObject().put("query", new JSONObject().put("bool",
            new JSONObject().put("filter", new JSONArray())));
        JSONObject q = ann.getMatchQuery(method, "1", matchingSet);
        q.put("_source", false);
        return q;
    }

    private static Map<String, Double> hits(JSONObject res) {
        Map<String, Double> out = new HashMap<String, Double>();
        JSONArray hits = res.getJSONObject("hits").getJSONArray("hits");
        for (int i = 0; i < hits.length(); i++) {
            out.put(hits.getJSONObject(i).getString("_id"), hits.getJSONObject(i).getDouble("_score"));
        }
        return out;
    }

    // the exact top-k model-B ids by cosine to the query
    private Set<String> exactTopB(int k) {
        List<String> ids = new ArrayList<String>(modelB.keySet());
        ids.sort((x, y) -> Double.compare(cosine(query, modelB.get(y)), cosine(query, modelB.get(x))));
        return new HashSet<String>(ids.subList(0, Math.min(k, ids.size())));
    }

    private void assertAllKFromModelB(Map<String, Double> h) {
        int k = Annotation.KNN_K_DISTANCE_VALUE;
        assertEquals(k, h.size(), "all k slots go to model B: " + h.keySet());
        for (String id : h.keySet()) assertTrue(id.startsWith("b"), "only model-B docs: " + id);
        // dual-model docs (b196..b199) and every other hit are scored by their B vector
        for (Map.Entry<String, Double> e : h.entrySet()) {
            assertEquals((1 + cosine(query, modelB.get(e.getKey()))) / 2, e.getValue(), 1e-4,
                "score of " + e.getKey());
        }
        Set<String> exact = exactTopB(k);
        int found = 0;
        for (String id : h.keySet()) if (exact.contains(id)) found++;
        assertTrue(found >= 0.9 * k, "recall@" + k + " vs exact = " + found);
    }

    @Test void currentEngine_allNeighboursFromTheQueryModel() throws Exception {
        connect(current);
        load(null, 400, 200, 4);
        JSONObject q = matchQuery("B");
        assertTrue(q.toString().contains("\"filter\":{\"bool\""), "pre-filtered shape: " + q);

        Map<String, Double> h = hits(Annotation.searchMatchQuery(os, q, 100));
        assertAllKFromModelB(h);
        assertTrue(OpenSearch.KNN_FILTER_UNSUPPORTED.isEmpty(), "no fallback on this engine");

        // the post-filter shape on the same data loses the B slots to model A's nearer vectors
        Map<String, Double> post = hits(os.querySearch(INDEX, Annotation.withKnnPostFilter(q), 0, 100));
        assertTrue(post.size() < Annotation.KNN_K_DISTANCE_VALUE,
            "post-filter returned " + post.size());

        // fewer eligible than k: all of them; none eligible: nothing
        assertEquals(new HashSet<String>(java.util.Arrays.asList("c0", "c1", "c2")),
            hits(Annotation.searchMatchQuery(os, matchQuery("C"), 100)).keySet());
        assertEquals(0, hits(Annotation.searchMatchQuery(os, matchQuery("Z"), 100)).size());
    }

    @Test void luceneEngine_allNeighboursFromTheQueryModel() throws Exception {
        connect(current);
        JSONObject mapping = new Annotation().opensearchMapping();
        mapping.getJSONObject("embeddings").getJSONObject("properties").getJSONObject(
            Embedding.vectorFieldName(DIM)).put("method", new JSONObject().put("name", "hnsw")
            .put("engine", "lucene"));
        load(new JSONObject().put("settings", new JSONObject().put("index", new JSONObject()
            .put("knn", true).put("analysis", new JSONObject("{\"normalizer\": " +
            "{\"wildbook_keyword_normalizer\": {\"type\": \"custom\", \"char_filter\": [], " +
            "\"filter\": [\"lowercase\", \"asciifolding\"]}}}")))).put("mappings",
            new JSONObject().put("properties", mapping)).toString(), 400, 200, 4);
        assertAllKFromModelB(hits(Annotation.searchMatchQuery(os, matchQuery("B"), 100)));
    }

    @Test void unrelatedBadRequest_isThrown_notCached() throws Exception {
        connect(current);
        load(null, 20, 20, 0);
        JSONObject q = matchQuery("B");
        // corrupt the knn filter so OpenSearch rejects the query for a different reason
        q.getJSONObject("query").getJSONObject("bool").getJSONArray("must").getJSONObject(0)
            .getJSONObject("nested").getJSONObject("query").getJSONObject("bool")
            .getJSONArray("must").getJSONObject(0).getJSONObject("knn").getJSONObject(FIELD)
            .put("filter", new JSONObject().put("bool", new JSONObject().put("filter",
            new JSONArray().put(new JSONObject().put("no_such_query", new JSONObject())))));
        ResponseException ex = assertThrows(ResponseException.class,
            () -> Annotation.searchMatchQuery(os, q, 100));
        assertEquals(400, ex.getResponse().getStatusLine().getStatusCode());
        assertTrue(OpenSearch.KNN_FILTER_UNSUPPORTED.isEmpty());
    }

    @Test void nmslibEngine_fallsBackToPostFilter_perField() throws Exception {
        connect(legacy);
        load(null, 40, 10, 0);
        JSONObject q = matchQuery("B");
        Map<String, Double> expected = hits(os.querySearch(INDEX, Annotation.withKnnPostFilter(q),
            0, 100));

        // first search: the engine rejects the knn filter, the field is recorded, retried post-filtered
        assertEquals(expected, hits(Annotation.searchMatchQuery(os, q, 100)));
        assertEquals(java.util.Collections.singleton(FIELD), OpenSearch.KNN_FILTER_UNSUPPORTED);
        // later queries on that field are built post-filtered from the start
        JSONObject next = matchQuery("B");
        assertNull(Annotation.withKnnPostFilter(next), "no knn filter once recorded: " + next);
        assertEquals(expected, hits(Annotation.searchMatchQuery(os, next, 100)));

        // a recreated index gets the current default engine, so the record is dropped
        os.deleteIndex(INDEX);
        assertTrue(OpenSearch.KNN_FILTER_UNSUPPORTED.isEmpty());
        OpenSearch.KNN_FILTER_UNSUPPORTED.add(FIELD);
        connect(current); // and it is per cluster
        assertTrue(OpenSearch.KNN_FILTER_UNSUPPORTED.isEmpty());
    }
}
