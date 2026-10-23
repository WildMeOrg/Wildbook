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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Real-OpenSearch coverage for filtering by model INSIDE the knn clause: when several models'
 * vectors share one field, all k neighbours must come from the query's model. The image defaults
 * to the fleet's pinned 3.1.0; run also with
 * -Dwildbook.test.opensearchImage=opensearchproject/opensearch:2.19.1 (the oldest release whose
 * default engine for these methodless fields, faiss, filters inside knn).
 */
@Testcontainers class KnnModelFilterIntegrationTest {

    private static final String INDEX = "annotation";
    private static final int DIM = 1536;
    private static final String FIELD = "embeddings." + Embedding.vectorFieldName(DIM);

    @Container static GenericContainer<?> current = container(System.getProperty(
        "wildbook.test.opensearchImage", "opensearchproject/opensearch:3.1.0"));

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

    // point the shared client at the container, with no annotation index
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
            // B vectors spread over a range of distances (identical noise levels would make the
            // exact top-k an arbitrary pick among near-ties); the dual-model docs' B vectors are
            // near enough to be certain B top-k hits, while their (even nearer) A vectors must not
            // be what scores them
            float[] b = noisy(query, (i >= nB - dual) ? 0.3 : 0.4 + 1.2 * i / nB, rnd);
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
        // never take the small-filtered-set exact path, so filtered searches exercise the graph
        rest("PUT", "/" + INDEX + "/_settings",
            "{\"index.knn.advanced.filtered_exact_search_threshold\": 0}");
    }

    private long knnStat(String name) throws IOException {
        JSONObject nodes = new JSONObject(rest("GET", "/_plugins/_knn/stats", null))
            .getJSONObject("nodes");
        return nodes.getJSONObject(nodes.keys().next()).getLong(name);
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

    // the pre-change shape of a getMatchQuery() query: the knn clause's filter moved beside it,
    // where it only post-filters the k neighbours already chosen
    private static JSONObject postFiltered(JSONObject matchQuery) {
        JSONObject copy = new JSONObject(matchQuery.toString());
        JSONObject nestedBool = copy.getJSONObject("query").getJSONObject("bool")
            .getJSONArray("must").getJSONObject(0).getJSONObject("nested").getJSONObject("query")
            .getJSONObject("bool");
        JSONObject knnField = nestedBool.getJSONArray("must").getJSONObject(0)
            .getJSONObject("knn").getJSONObject(FIELD);
        nestedBool.put("filter", knnField.getJSONObject("filter").getJSONObject("bool")
            .getJSONArray("filter"));
        knnField.remove("filter");
        return copy;
    }

    private Map<String, Double> search(JSONObject q) throws IOException {
        return hits(os.querySearch(INDEX, q, 0, 100));
    }

    private static Map<String, Double> hits(JSONObject res) {
        Map<String, Double> out = new HashMap<String, Double>();
        JSONArray hits = res.getJSONObject("hits").getJSONArray("hits");
        for (int i = 0; i < hits.length(); i++) {
            out.put(hits.getJSONObject(i).getString("_id"), hits.getJSONObject(i).getDouble("_score"));
        }
        return out;
    }

    private static final Set<String> DUAL = new HashSet<String>(java.util.Arrays.asList(
        "b196", "b197", "b198", "b199"));

    // the exact top-k model-B ids by cosine to the query, among `ids`
    private Set<String> exactTopB(Set<String> ids, int k) {
        List<String> sorted = new ArrayList<String>(ids);
        sorted.sort((x, y) -> Double.compare(cosine(query, modelB.get(y)), cosine(query, modelB.get(x))));
        return new HashSet<String>(sorted.subList(0, Math.min(k, sorted.size())));
    }

    // `exactRecall` is checked on the exact path and on lucene. On faiss's filtered GRAPH path
    // (2.19.1, 3.1.0) recall against exact is an engine property, not Wildbook's: when every
    // filtered-out vector is nearer the query than every eligible one (this fixture), default
    // ef_search reaches ~0.46 at 1536-d (0.98 at ef_search 500) and parents whose filtered-out
    // sibling vector is the nearer one (the dual docs) can be dropped. The post-filter shape
    // returns no model-B docs at all on the same data.
    private void assertAllKFromModelB(Map<String, Double> h, boolean exactRecall) {
        int k = Annotation.KNN_K_DISTANCE_VALUE;
        assertEquals(k, h.size(), "all k slots go to model B: " + h.keySet());
        for (String id : h.keySet()) assertTrue(id.startsWith("b"), "only model-B docs: " + id);
        // every hit, dual-model docs included, is scored by its B vector
        for (Map.Entry<String, Double> e : h.entrySet()) {
            assertEquals((1 + cosine(query, modelB.get(e.getKey()))) / 2, e.getValue(), 1e-4,
                "score of " + e.getKey());
        }
        if (!exactRecall) return;
        for (String dual : DUAL) {
            assertTrue(h.containsKey(dual), "dual-model doc " + dual + " is a B hit: " + h.keySet());
        }
        Set<String> exact = exactTopB(modelB.keySet(), k);
        int found = 0;
        for (String id : h.keySet()) if (exact.contains(id)) found++;
        assertTrue(found >= 0.9 * k, "recall@" + k + " vs exact = " + found);
    }

    @Test void currentEngine_allNeighboursFromTheQueryModel() throws Exception {
        connect(current);
        load(null, 400, 200, 4);
        JSONObject q = matchQuery("B");
        assertTrue(q.toString().contains("\"filter\":{\"bool\""), "pre-filtered shape: " + q);

        long graphQueries = knnStat("graph_query_requests");
        long filteredQueries = knnStat("knn_query_with_filter_requests");
        Map<String, Double> h = search(q);
        assertAllKFromModelB(h, false);
        assertTrue(knnStat("knn_query_with_filter_requests") > filteredQueries,
            "ran as a filtered knn query");
        assertTrue(knnStat("graph_query_requests") > graphQueries,
            "searched the ANN graph, not the exact path");
        // this small eligible set takes the exact path by default, which keeps the dual docs
        rest("PUT", "/" + INDEX + "/_settings",
            "{\"index.knn.advanced.filtered_exact_search_threshold\": null}");
        assertAllKFromModelB(search(q), true);

        // the post-filter shape on the same data loses the B slots to model A's nearer vectors
        Map<String, Double> post = search(postFiltered(q));
        assertTrue(post.size() < Annotation.KNN_K_DISTANCE_VALUE,
            "post-filter returned " + post.size());

        // fewer eligible than k: all of them; none eligible: nothing
        assertEquals(new HashSet<String>(java.util.Arrays.asList("c0", "c1", "c2")),
            search(matchQuery("C")).keySet());
        assertEquals(0, search(matchQuery("Z")).size());
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
        long filteredQueries = knnStat("knn_query_with_filter_requests");
        assertAllKFromModelB(search(matchQuery("B")), true);
        assertTrue(knnStat("knn_query_with_filter_requests") > filteredQueries,
            "ran as a filtered knn query");
    }
}
