package org.ecocean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Collections;
import org.apache.http.HttpHost;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opensearch.client.Request;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Real-OpenSearch coverage for per-length vector fields: an annotation index created before the
 * 1536 field existed (only the 2152 `vector`) is upgraded in place on first write, and 2152 and
 * 1536 vectors then index, kNN-search and pass the match-readiness query side by side.
 *
 * The image defaults to the fleet's pinned OpenSearch (devops/deploy/docker-compose.yml); run
 * against another release with -Dwildbook.test.opensearchImage=opensearchproject/opensearch:2.19.1
 */
@Testcontainers class AnnotationVectorFieldsIntegrationTest {

    private static final String INDEX = "annotation";

    @Container static GenericContainer<?> opensearch = new GenericContainer<>(DockerImageName.parse(
        System.getProperty("wildbook.test.opensearchImage", "opensearchproject/opensearch:3.1.0")))
            .withExposedPorts(9200, 9300)
            .withEnv("discovery.type", "single-node")
            .withEnv("plugins.security.disabled", "true")
            .withEnv("OPENSEARCH_JAVA_OPTS", "-Xms512m -Xmx512m")
            .withEnv("DISABLE_INSTALL_DEMO_CONFIG", "true")
            .waitingFor(new HttpWaitStrategy()
            .forPort(9200)
            .forPath("/_cluster/health")
            .forStatusCode(200)
            .withStartupTimeout(java.time.Duration.ofMinutes(2)));

    private OpenSearch os;

    @BeforeAll static void connect() {
        OpenSearch.initializeClient(new HttpHost(opensearch.getHost(),
            opensearch.getMappedPort(9200), "http"));
    }

    @BeforeEach void freshLegacyIndex() throws Exception {
        OpenSearch.INDEX_EXISTS_CACHE.clear();
        os = new OpenSearch();
        if (os.existsIndex(INDEX)) os.deleteIndex(INDEX); // also clears the upgraded marker
        createLegacyIndex();
    }

    @AfterEach void cleanup() throws Exception {
        if (os.existsIndex(INDEX)) os.deleteIndex(INDEX);
    }

    // an annotation index as an older WAR created it: the embeddings mapping has only the 2152
    // `vector` field. Created directly, not via createIndex(), which would mark it up to date.
    private void createLegacyIndex() throws Exception {
        JSONObject mapping = new Annotation().opensearchMapping();
        JSONObject embProps = mapping.getJSONObject("embeddings").getJSONObject("properties");
        for (String field : Embedding.VECTOR_FIELDS.values()) {
            if (!Embedding.LEGACY_VECTOR_FIELD.equals(field)) embProps.remove(field);
        }
        JSONObject body = new JSONObject()
            .put("settings", new JSONObject().put("index", new JSONObject().put("knn", true))
                .put("analysis", new JSONObject("{\"normalizer\": {\"wildbook_keyword_normalizer\": " +
                "{\"type\": \"custom\", \"char_filter\": [], \"filter\": [\"lowercase\", " +
                "\"asciifolding\"]}}}")))
            .put("mappings", new JSONObject().put("properties", mapping));
        rest("PUT", "/" + INDEX, body);
        OpenSearch.INDEX_EXISTS_CACHE.remove(INDEX);
    }

    private String rest(String method, String endpoint, JSONObject body) throws IOException {
        Request req = new Request(method, endpoint);
        if (body != null) req.setJsonEntity(body.toString());
        return os.getRestResponse(req);
    }

    private static JSONArray vec(int dim, double seed) {
        JSONArray arr = new JSONArray();
        for (int i = 0; i < dim; i++) arr.put(Math.sin(seed * (i + 1)));
        return arr;
    }

    private static Annotation annot(String id) {
        Annotation ann = new Annotation();
        ann.setId(id);
        return ann;
    }

    // what OpenSearch.index() does for an annotation: ensureIndex first, then write the doc.
    // Only the fields the match + readiness queries read are written.
    private void indexDoc(OpenSearch client, Annotation ann) throws Exception {
        client.ensureIndex(INDEX, ann.opensearchMapping());
        StringWriter sw = new StringWriter();
        JsonGenerator jg = new JsonFactory().createGenerator(sw);
        jg.writeStartObject();
        jg.writeBooleanField("matchAgainst", true);
        jg.writeStringField("acmId", ann.getId());
        ann.writeEmbeddings(jg);
        jg.writeEndObject();
        jg.close();
        rest("PUT", "/" + INDEX + "/_doc/" + ann.getId() + "?refresh=true",
            new JSONObject(sw.toString()));
    }

    private JSONObject embeddingsMapping() throws IOException {
        return os.fieldMappings(INDEX).get("embeddings").getJSONObject("properties");
    }

    private JSONArray knnHits(Annotation query, String method, String version) throws IOException {
        JSONObject matchingSet = new JSONObject().put("query", new JSONObject().put("bool",
            new JSONObject().put("filter", new JSONArray())));
        JSONObject q = query.getMatchQuery(method, version, matchingSet);
        assertNotNull(q, "match query");
        q.put("_source", false);
        return new JSONObject(rest("POST", "/" + INDEX + "/_search", q)).getJSONObject("hits")
            .getJSONArray("hits");
    }

    private static boolean containsId(JSONArray hits, String id) {
        for (int i = 0; i < hits.length(); i++) {
            if (id.equals(hits.getJSONObject(i).getString("_id"))) return true;
        }
        return false;
    }

    private int readyCount(String id, String method, String version) throws IOException {
        return os.queryCount(INDEX, OpenSearch.buildAnnotationMatchableQuery(
            Collections.singleton(id), method, version));
    }

    @Test void legacyIndex_upgradedInPlace_mixedLengthsSearchable() throws Exception {
        // a 2152 doc written to the legacy index before upgrade-aware code ran
        Annotation old2152 = annot("ann-old-2152");
        new Embedding(old2152, "miewid", "msv4.1", vec(2152, 0.1));
        JSONObject legacyDoc = new JSONObject().put("matchAgainst", true).put("acmId",
            "ann-old-2152").put("embeddings", new JSONArray().put(new JSONObject()
            .put("method", "miewid").put("methodVersion", "msv4.1").put("vector",
            vec(2152, 0.1))));
        rest("PUT", "/" + INDEX + "/_doc/ann-old-2152?refresh=true", legacyDoc);
        JSONObject legacyVector = embeddingsMapping().getJSONObject("vector");
        assertFalse(embeddingsMapping().has("vector_1536"), "precondition: legacy mapping");

        // a stale "exists" cache entry must not stop the upgrade
        OpenSearch.INDEX_EXISTS_CACHE.put(INDEX, true);
        Annotation a1536 = annot("ann-1536");
        new Embedding(a1536, "miewid-dinov3", "msv5", vec(1536, 0.2));
        Annotation both = annot("ann-both");
        new Embedding(both, "miewid", "msv4.1", vec(2152, 0.3));
        new Embedding(both, "miewid-dinov3", "msv5", vec(1536, 0.4));
        indexDoc(os, a1536);
        indexDoc(os, both);
        os.ensureIndex(INDEX, new Annotation().opensearchMapping()); // idempotent

        JSONObject emb = embeddingsMapping();
        assertEquals(1536, emb.getJSONObject("vector_1536").getInt("dimension"));
        assertEquals(legacyVector.toString(), emb.getJSONObject("vector").toString(),
            "legacy 2152 field untouched");

        JSONArray hits1536 = knnHits(a1536, "miewid-dinov3", "msv5");
        assertTrue(containsId(hits1536, "ann-1536"), hits1536.toString());
        assertTrue(containsId(hits1536, "ann-both"), hits1536.toString());
        assertFalse(containsId(hits1536, "ann-old-2152"), "2152-only doc has no 1536 vector");

        JSONArray hits2152 = knnHits(old2152, "miewid", "msv4.1");
        assertTrue(containsId(hits2152, "ann-old-2152"), hits2152.toString());
        assertTrue(containsId(hits2152, "ann-both"), hits2152.toString());
        assertFalse(containsId(hits2152, "ann-1536"), "1536-only doc has no 2152 vector");

        assertEquals(1, readyCount("ann-1536", "miewid-dinov3", "msv5"));
        assertEquals(1, readyCount("ann-both", "miewid-dinov3", "msv5"));
        assertEquals(1, readyCount("ann-old-2152", "miewid", "msv4.1"));
        assertEquals(1, readyCount("ann-old-2152", null, null));
        assertEquals(0, readyCount("ann-1536", "miewid", "msv4.1"));
    }

    @Test void readiness_rejectsMetadataWithoutIndexedVector() throws Exception {
        // an unsupported length is written without its vector; its metadata must not read as ready
        Annotation ann = annot("ann-512");
        new Embedding(ann, "pie", "v2", vec(512, 0.5));
        indexDoc(os, ann);
        assertEquals(1, os.queryCount(INDEX, new JSONObject().put("query", new JSONObject().put(
            "ids", new JSONObject().put("values", new JSONArray().put("ann-512"))))),
            "doc itself is indexed");
        assertEquals(0, readyCount("ann-512", "pie", "v2"));
        assertEquals(0, readyCount("ann-512", null, null));
    }

    @Test void failedUpgrade_propagates_thenRetrySucceeds() throws Exception {
        OpenSearch failing = spy(os);
        doThrow(new IOException("simulated mapping failure")).when(failing).getRestResponse(
            argThat(r -> (r != null) && r.getEndpoint().endsWith("/_mapping")));
        Annotation ann = annot("ann-retry");
        new Embedding(ann, "miewid-dinov3", "msv5", vec(1536, 0.6));

        assertThrows(IOException.class, () -> indexDoc(failing, ann));
        assertFalse(embeddingsMapping().has("vector_1536"));
        assertEquals(0, os.queryCount(INDEX, new JSONObject().put("query", new JSONObject().put(
            "ids", new JSONObject().put("values", new JSONArray().put("ann-retry"))))),
            "no doc written when the upgrade failed");

        // the failure was not cached as upgraded: the next write upgrades and indexes
        indexDoc(os, ann);
        assertTrue(embeddingsMapping().has("vector_1536"));
        assertEquals(1, readyCount("ann-retry", "miewid-dinov3", "msv5"));
        assertTrue(containsId(knnHits(ann, "miewid-dinov3", "msv5"), "ann-retry"));
    }

    @Test void deleteAndRecreate_hasEveryVectorField() throws Exception {
        indexDoc(os, annot("ann-x")); // upgrades and marks the legacy index
        os.deleteIndex(INDEX);
        os.ensureIndex(INDEX, new Annotation().opensearchMapping());
        JSONObject emb = embeddingsMapping();
        for (int dim : Embedding.VECTOR_FIELDS.keySet()) {
            assertEquals(dim, emb.getJSONObject(Embedding.vectorFieldName(dim)).getInt(
                "dimension"));
        }
    }

    @Test void otherIndexes_untouched() {
        assertNull(OpenSearch.vectorFieldsUpgradeMapping(new JSONObject().put("id",
            new JSONObject().put("type", "keyword"))));
        assertNull(OpenSearch.vectorFieldsUpgradeMapping(null));
        JSONObject upgrade = OpenSearch.vectorFieldsUpgradeMapping(
            new Annotation().opensearchMapping());
        JSONObject props = upgrade.getJSONObject("embeddings").getJSONObject("properties");
        assertEquals("nested", upgrade.getJSONObject("embeddings").getString("type"));
        assertFalse(props.has("vector"), "legacy field is never replayed");
        assertFalse(props.has("method"));
        assertTrue(props.has("vector_1536"));
    }
}
