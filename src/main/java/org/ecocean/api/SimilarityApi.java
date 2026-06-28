package org.ecocean.api;

import io.prometheus.client.Counter;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.ecocean.Annotation;
import org.ecocean.Embedding;
import org.ecocean.Encounter;
import org.ecocean.OpenSearch;
import org.ecocean.User;
import org.ecocean.Util;
import org.ecocean.security.WildbookTokenAuthenticationFilter;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * POST /api/v3/similarity — read-only, token-authenticated k-NN re-identification: given an annotation
 * the caller can see, return the most visually similar annotations (server-side ANN), WITHOUT the
 * caller downloading any vectors. Surfaces Wildbook's existing OpenSearch kNN as a bounded, ACL-scoped
 * token endpoint.
 *
 * Safety: candidates are filtered INSIDE the native knn.filter (ACL + same viewpoint + method/version
 * + optional scope + self/encounter exclusion) so the ANN candidate set itself is constrained (an
 * outer bool.filter would post-filter ANN results). Vectors are never returned. The query annotation's
 * visibility is gated like media/resolve (not-visible and not-found are the same 404 — no existence
 * oracle). It runs on a request-scoped PIT (no shared PIT_CACHE contention) and behind the SIMILARITY
 * concurrency pool so it cannot starve interactive Wildbook use. Each served query increments a
 * dedicated re-ID metric (this is user-run identification compute on our servers).
 */
public class SimilarityApi extends ApiBase {

    static final int DEFAULT_K = 50;
    static final int MAX_K = 100;

    // Distinct from the pipeline wildbook_identification_tasks* gauges: token-API re-ID is a separate,
    // synchronous, agent-driven modality. Registered defensively (re-deploy / multi-context safe).
    private static final Counter REID_QUERIES;
    static {
        Counter c = null;
        try {
            c = Counter.build().name("wildbook_token_reid_queries_total")
                .help("Token-API similarity (k-NN re-identification) queries served, by context")
                .labelNames("context").register();
        } catch (RuntimeException alreadyRegistered) {
            c = null;
        }
        REID_QUERIES = c;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        handle(request, response);
    }

    void doPostForTest(HttpServletRequest request, HttpServletResponse response) throws IOException {
        handle(request, response);
    }

    private void handle(HttpServletRequest request, HttpServletResponse response) throws IOException {
        boolean tokenAuth = Boolean.TRUE.equals(
            request.getAttribute(WildbookTokenAuthenticationFilter.TOKEN_AUTH_ATTR));
        if (!tokenAuth) { writeError(response, 401, "token auth required"); return; }
        String context = (String) request.getAttribute(
            WildbookTokenAuthenticationFilter.TOKEN_CONTEXT_ATTR);
        if (!Util.stringExists(context)) { writeError(response, 401, "unauthorized"); return; }

        // Parse + validate BEFORE acquiring a permit or touching the DB/OpenSearch.
        String annotationId, method, methodVersion, taxonomy, locationId;
        int k;
        boolean excludeSameEncounter;
        try {
            JSONObject body = ServletUtilities.jsonFromHttpServletRequest(request);
            if (body == null) { writeError(response, 400, "missing request body"); return; }
            annotationId = body.optString("annotationId", null);
            if (!Util.stringExists(annotationId)) {
                writeError(response, 400, "annotationId is required"); return;
            }
            k = body.has("k") ? body.optInt("k", -1) : DEFAULT_K;
            if ((k < 1) || (k > MAX_K)) {
                writeError(response, 400, "k must be between 1 and " + MAX_K); return;
            }
            method = body.optString("method", "miewid");
            if (!Util.stringExists(method)) method = "miewid";
            methodVersion = Util.stringExists(body.optString("methodVersion", null))
                ? body.optString("methodVersion") : null;
            taxonomy = Util.stringExists(body.optString("taxonomy", null))
                ? body.optString("taxonomy") : null;
            locationId = Util.stringExists(body.optString("locationId", null))
                ? body.optString("locationId") : null;
            excludeSameEncounter = body.optBoolean("excludeSameEncounter", false);
        } catch (Exception ex) {
            writeError(response, 400, "malformed request body"); return;
        }

        // Concurrency guard: bound simultaneous kNN so token re-ID cannot starve interactive use.
        TokenApiConcurrency.Permit permit =
            TokenApiConcurrency.tryAcquire(TokenApiConcurrency.Kind.SIMILARITY);
        if (permit == null) { writeBusy(response); return; }

        Shepherd myShepherd = null;
        try {
            myShepherd = new Shepherd(context);
            myShepherd.setAction("api.SimilarityApi.POST");
            myShepherd.beginDBTransaction();
            User currentUser = myShepherd.getUser(request);
            if ((currentUser == null) || (currentUser.getId() == null)) {
                writeError(response, 401, "unauthorized"); return;
            }
            boolean isAdmin = currentUser.isAdmin(myShepherd);

            // No-oracle visibility gate for the QUERY annotation: a non-admin must be able to see it.
            // not-visible and not-found both -> identical 404 (no existence oracle).
            if (!isAdmin) {
                Set<String> one = new LinkedHashSet<>();
                one.add(annotationId);
                Set<String> visible = visibleAnnotationIds(one, currentUser.getId());
                if (!visible.contains(annotationId)) { writeError(response, 404, "not found"); return; }
            }
            Annotation ann = myShepherd.getAnnotation(annotationId);
            if (ann == null) { writeError(response, 404, "not found"); return; }

            String viewpoint = ann.getViewpoint();
            if (!Util.stringExists(viewpoint)) {
                writeError(response, 400, "query annotation has no viewpoint"); return;
            }
            Embedding emb = resolveSingleEmbedding(ann, method, methodVersion);
            if (emb == null) {
                writeError(response, 400, "no unique embedding for that method/version"); return;
            }
            String mv = emb.getMethodVersion();
            String queryEncounterId = null;
            Encounter qEnc = ann.findEncounter(myShepherd);
            if (qEnc != null) queryEncounterId = qEnc.getId();

            JSONObject body = buildKnnBody(emb, k, method, mv, viewpoint, taxonomy, locationId,
                annotationId, (excludeSameEncounter ? queryEncounterId : null),
                (isAdmin ? null : currentUser.getId()));
            // Defense in depth: the ACL is already inside knn.filter (the candidate set is ACL-scoped);
            // for a non-admin also wrap the whole query so any hit is ACL-gated even if the engine ever
            // post-filtered. (No-op for admin.)
            if (!isAdmin) body = OpenSearch.applyAclFilter(body, currentUser.getId(), "annotation");

            OpenSearch os = new OpenSearch();
            JSONObject queryRes = os.queryPitPrivate("annotation", body, 0, k, null, null);
            JSONArray neighbors = new JSONArray();
            JSONObject outerHits = queryRes.optJSONObject("hits");
            JSONArray hits = (outerHits != null) ? outerHits.optJSONArray("hits") : null;
            if (hits != null) {
                for (int i = 0; i < hits.length(); i++) {
                    JSONObject h = hits.optJSONObject(i);
                    if (h == null) continue;
                    JSONObject doc = h.optJSONObject("_source");
                    if (doc == null) continue;
                    doc.remove("embeddings"); // never return vectors (defense in depth on top of _source excludes)
                    JSONObject clean = OpenSearch.sanitizeDoc(doc, "annotation", myShepherd, currentUser,
                        tokenAuth);
                    clean.put("score", h.optDouble("_score", 0.0));
                    neighbors.put(clean);
                }
            }

            JSONObject q = new JSONObject();
            q.put("annotationId", annotationId);
            q.put("viewpoint", viewpoint);
            q.put("method", method);
            q.put("methodVersion", mv);
            JSONObject out = new JSONObject();
            out.put("query", q);
            out.put("neighbors", neighbors);
            if (REID_QUERIES != null) REID_QUERIES.labels(context).inc();
            response.setStatus(200);
            writeBody(response, out.toString());
        } catch (IOException ioEx) {
            throw ioEx; // broken pipe etc — don't double-write
        } catch (Exception ex) {
            writeError(response, 500, "similarity failed");
            ex.printStackTrace();
        } finally {
            permit.close();
            if (myShepherd != null) myShepherd.rollbackAndClose();
        }
    }

    /** Exactly one embedding matching (method, methodVersion-if-given); else null (ambiguous/none). */
    private Embedding resolveSingleEmbedding(Annotation ann, String method, String methodVersion) {
        if (ann.getEmbeddings() == null) return null;
        Embedding found = null;
        for (Embedding e : ann.getEmbeddings()) {
            if (e == null) continue;
            if ((method != null) && !method.equals(e.getMethod())) continue;
            if ((methodVersion != null) && !methodVersion.equals(e.getMethodVersion())) continue;
            if (found != null) return null; // more than one match -> ambiguous
            found = e;
        }
        return found;
    }

    /** Build the nested-kNN body with ALL constraints inside the native knn.filter (filtered ANN). */
    private JSONObject buildKnnBody(Embedding emb, int k, String method, String methodVersion,
        String viewpoint, String taxonomy, String locationId, String selfId, String excludeEncounterId,
        String aclUserId) {
        JSONArray filt = new JSONArray();
        filt.put(term("embeddings.method", method));
        filt.put(term("embeddings.methodVersion", methodVersion));
        filt.put(term("viewpoint", viewpoint));
        if (taxonomy != null) filt.put(term("encounterTaxonomy", taxonomy));
        if (locationId != null) filt.put(term("encounterLocationId", locationId));
        if (aclUserId != null) filt.put(OpenSearch.aclShouldClause(aclUserId, "annotation"));
        JSONArray mustNot = new JSONArray();
        mustNot.put(term("id", selfId)); // never return the query annotation itself
        if (excludeEncounterId != null) mustNot.put(term("encounterId", excludeEncounterId));
        JSONObject filterBool = new JSONObject().put("filter", filt).put("must_not", mustNot);

        JSONObject knnInner = new JSONObject()
            .put("vector", new JSONArray(emb.vectorToFloatArray()))
            .put("k", k)
            .put("filter", new JSONObject().put("bool", filterBool));
        JSONObject nested = new JSONObject().put("nested", new JSONObject()
            .put("path", "embeddings")
            .put("query", new JSONObject().put("knn",
                new JSONObject().put("embeddings.vector", knnInner))));
        JSONObject body = new JSONObject().put("query", nested);
        // keep payload small AND never ship vectors: exclude embeddings from _source
        body.put("_source", new JSONObject().put("excludes", new JSONArray().put("embeddings")));
        return body;
    }

    private static JSONObject term(String field, String value) {
        return new JSONObject().put("term", new JSONObject().put(field, value));
    }

    /** Which of these annotation ids the user may see, via a request-scoped (private) PIT. */
    private Set<String> visibleAnnotationIds(Set<String> ids, String userId) throws IOException {
        JSONObject query = OpenSearch.buildIdEligibilityQuery(ids);
        query = OpenSearch.applyAclFilter(query, userId, "annotation");
        JSONObject res = new OpenSearch().queryPitPrivate("annotation", query, 0, ids.size(), null, null);
        Set<String> visible = new LinkedHashSet<>();
        JSONObject outer = (res != null) ? res.optJSONObject("hits") : null;
        JSONArray hits = (outer != null) ? outer.optJSONArray("hits") : null;
        if (hits != null) {
            for (int i = 0; i < hits.length(); i++) {
                JSONObject h = hits.optJSONObject(i);
                String hid = (h != null) ? h.optString("_id", null) : null;
                if (Util.stringExists(hid)) visible.add(hid);
            }
        }
        return visible;
    }

    private void writeBusy(HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setHeader("Retry-After", Integer.toString(TokenApiConcurrency.RETRY_AFTER_SECONDS));
        writeBody(response, new JSONObject()
            .put("error", "token API busy, retry after " + TokenApiConcurrency.RETRY_AFTER_SECONDS
                + " seconds").toString());
    }

    private void writeError(HttpServletResponse response, int status, String msg) throws IOException {
        response.setStatus(status);
        writeBody(response, new JSONObject().put("error", msg).toString());
    }

    private void writeBody(HttpServletResponse response, String s) throws IOException {
        response.setContentType("application/json; charset=UTF-8");
        java.io.PrintWriter w = response.getWriter();
        w.write(s);
        w.close();
    }
}
