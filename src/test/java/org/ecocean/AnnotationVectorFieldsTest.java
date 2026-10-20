package org.ecocean;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import java.io.StringWriter;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

// Embeddings of different lengths (MiewID 2152 vs MiewID-DINOv3 1536) live in separate nested
// knn_vector fields, because a knn_vector field has exactly one dimension.
class AnnotationVectorFieldsTest {

    private static JSONArray vec(int dim) {
        JSONArray arr = new JSONArray();
        for (int i = 0; i < dim; i++) arr.put(0.01d * ((i % 7) + 1));
        return arr;
    }

    private static JSONObject matchingSet() {
        return new JSONObject().put("query", new JSONObject().put("bool",
            new JSONObject().put("filter", new JSONArray())));
    }

    private static JSONObject knnClause(JSONObject matchQuery) {
        return matchQuery.getJSONObject("query").getJSONObject("bool").getJSONArray("must")
            .getJSONObject(0).getJSONObject("nested").getJSONObject("query")
            .getJSONObject("bool").getJSONArray("must").getJSONObject(0).getJSONObject("knn");
    }

    private static JSONArray serializeEmbeddings(Annotation ann) throws Exception {
        StringWriter sw = new StringWriter();
        JsonGenerator jg = new JsonFactory().createGenerator(sw);
        jg.writeStartObject();
        ann.writeEmbeddings(jg);
        jg.writeEndObject();
        jg.close();
        return new JSONObject(sw.toString()).getJSONArray("embeddings");
    }

    @Test void vectorFieldName_perSupportedLength() {
        assertEquals("vector", Embedding.vectorFieldName(2152),
            "2152 keeps the original field so existing indexes need no reindex");
        assertEquals("vector_1536", Embedding.vectorFieldName(1536));
        assertNull(Embedding.vectorFieldName(512));
        assertNull(Embedding.vectorFieldName(0));
        assertTrue(Embedding.isSupportedLength(1536));
        assertFalse(Embedding.isSupportedLength(8));
    }

    @Test void mapping_hasOneCosineKnnFieldPerLength() {
        JSONObject embProps = new Annotation().opensearchMapping().getJSONObject("embeddings")
            .getJSONObject("properties");
        for (int dim : new int[] { 2152, 1536 }) {
            JSONObject field = embProps.getJSONObject(Embedding.vectorFieldName(dim));
            assertEquals("knn_vector", field.getString("type"));
            assertEquals(dim, field.getInt("dimension"));
            assertEquals("cosinesimil", field.getString("space_type"));
        }
    }

    @Test void serializer_writesEachVectorUnderItsLengthsField() throws Exception {
        Annotation ann = new Annotation();
        new Embedding(ann, "miewid", "msv4.1", vec(2152));
        new Embedding(ann, "miewid-dinov3", "msv5", vec(1536));
        JSONArray embs = serializeEmbeddings(ann);
        assertEquals(2, embs.length());
        for (int i = 0; i < embs.length(); i++) {
            JSONObject e = embs.getJSONObject(i);
            if ("miewid".equals(e.getString("method"))) {
                assertEquals(2152, e.getJSONArray("vector").length());
                assertFalse(e.has("vector_1536"));
            } else {
                assertEquals(1536, e.getJSONArray("vector_1536").length());
                assertFalse(e.has("vector"));
            }
        }
    }

    @Test void serializer_unsupportedLength_omitsVectorButKeepsMetadata() throws Exception {
        // writing it to any field would make OpenSearch reject the whole annotation doc
        Annotation ann = new Annotation();
        new Embedding(ann, "pie", "v2", vec(512));
        JSONObject e = serializeEmbeddings(ann).getJSONObject(0);
        assertEquals("pie", e.getString("method"));
        for (String field : Embedding.VECTOR_FIELDS.values()) assertFalse(e.has(field));
    }

    @Test void matchQuery_searchesTheQueryVectorsField() {
        Annotation ann = new Annotation();
        new Embedding(ann, "miewid", "msv4.1", vec(2152));
        new Embedding(ann, "miewid-dinov3", "msv5", vec(1536));

        JSONObject knn1536 = knnClause(ann.getMatchQuery("miewid-dinov3", "msv5", matchingSet()));
        assertEquals(1, knn1536.length());
        assertEquals(1536, knn1536.getJSONObject("embeddings.vector_1536").getJSONArray(
            "vector").length());

        JSONObject knn2152 = knnClause(ann.getMatchQuery("miewid", "msv4.1", matchingSet()));
        assertEquals(1, knn2152.length());
        assertEquals(2152, knn2152.getJSONObject("embeddings.vector").getJSONArray(
            "vector").length());
    }

    @Test void matchQuery_unsupportedLength_isNull() {
        // same contract as "no embedding to match"; findMatchProspects reports it as a task error
        Annotation ann = new Annotation();
        new Embedding(ann, "pie", "v2", vec(512));
        assertNull(ann.getMatchQuery("pie", "v2", matchingSet()));
    }
}
