package org.ecocean.api;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;

import org.ecocean.OpenSearch;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.User;
import org.ecocean.Util;
import org.json.JSONArray;
import org.json.JSONObject;

public class SearchApi extends ApiBase {
    /** Indices a token caller may search; everything else is 403. The agent-skill drift-guard test pins to this. */
    static final java.util.Set<String> TOKEN_ALLOWED_INDICES =
        java.util.Set.of("encounter", "annotation", "individual");

    public void doGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        doPost(request, response);
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        boolean tokenAuth = Boolean.TRUE.equals(
            request.getAttribute(org.ecocean.security.WildbookTokenAuthenticationFilter.TOKEN_AUTH_ATTR));
        String tokenContext = (String) request.getAttribute(
            org.ecocean.security.WildbookTokenAuthenticationFilter.TOKEN_CONTEXT_ATTR);
        // On the token path use the filter-VERIFIED context, not request-derived context (which a
        // caller can steer via ?context=/cookie/host). Session path resolves context as before.
        String context = (tokenAuth && (tokenContext != null))
            ? tokenContext : ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);

        myShepherd.setAction("api.SearchApi.POST");
        myShepherd.beginDBTransaction();

        try {
        User currentUser = myShepherd.getUser(request);
        JSONObject res = new JSONObject();
        String authzHeader = request.getHeader("Authorization");
        // case-insensitive scheme match (matches the filter); avoids fall-through on "bearer ..."
        boolean bearerPresent = (authzHeader != null)
            && authzHeader.regionMatches(true, 0, "Bearer ", 0, 7);
        if ((currentUser == null) || (currentUser.getId() == null)) {
            response.setStatus(401);
            res.put("error", 401);
        } else if (bearerPresent && !tokenAuth) {
            // a Bearer reached SearchApi without the token filter marking it -> filter misconfig
            response.setStatus(401);
            res.put("error", "token auth misconfiguration");
        } else {
            boolean isAdmin = currentUser.isAdmin(myShepherd);
            String arg = request.getPathInfo();
            if ((arg == null) || arg.equals("/")) {
                // for multisearch later maybe?
                response.setStatus(404);
                res.put("error", "unsupported");
            } else {
                String indexName = arg.substring(1);
                String searchQueryId = null;
                JSONObject query = null;
                if (Util.isUUID(indexName)) {
                    searchQueryId = indexName;
                    query = OpenSearch.queryLoad(searchQueryId, myShepherd);
                }
                // effective index: a stored query's real index comes from the loaded doc, not the {uuid} URL.
                // null-safe: null when a stored query failed to load (handled by the missing-stored-query branch).
                String effectiveIndex = (searchQueryId != null)
                    ? (query != null ? query.optString("indexName", null) : null)
                    : indexName;
                // --- token method allowlist FIRST (no existence/ownership leak via 403/404) ---
                if (tokenAuth && (searchQueryId != null)
                    && !"GET".equals(request.getMethod())) {
                    // stored-query replay is GET-only on the token path
                    response.setStatus(405);
                    res.put("error", "method not allowed");
                } else if (tokenAuth && (searchQueryId == null)
                    && !"POST".equals(request.getMethod())) {
                    // direct index search is POST-only on the token path
                    response.setStatus(405);
                    res.put("error", "method not allowed");
                // --- missing stored query (failed load): before index validity ---
                } else if ((searchQueryId != null) && (query == null)) {
                    response.setStatus(404);
                    res.put("error", "invalid searchQueryId " + searchQueryId);
                // --- token stored-query OWNER check: before index validity so a non-owner can't probe it ---
                } else if (tokenAuth && (searchQueryId != null) && (query != null)
                    && !isAdmin
                    && !currentUser.getId().equals(query.optString("creator", null))) {
                    response.setStatus(403);
                    res.put("error", "not the owner of this stored query");
                // --- effective-index validity (subsumes the old direct unknown-index check) ---
                } else if (!OpenSearch.isValidIndexName(effectiveIndex)) {
                    response.setStatus(404);
                    res.put("error", "unknown index");
                // --- session-path annotation gate stays admin-only; token path uses the ACL filter instead ---
                } else if (!tokenAuth && "annotation".equals(effectiveIndex) && !isAdmin) {
                    // per discussion with jh: api exposure of annotations admin-only on the session path
                    response.setStatus(403);
                    res.put("error", 403);
                // --- token index allowlist: encounter, annotation, individual (others 403) ---
                } else if (tokenAuth && !TOKEN_ALLOWED_INDICES.contains(effectiveIndex)) {
                    response.setStatus(403);
                    res.put("error", "token search is limited to encounter, annotation, individual");
                } else if ((query == null) && !"POST".equals(request.getMethod())) {
                    response.setStatus(405);
                    res.put("error", "method not allowed");
                } else {
                    String fromStr = request.getParameter("from");
                    String sizeStr = request.getParameter("size");
                    String sort = request.getParameter("sort");
                    String sortOrder = request.getParameter("sortOrder");
                    // for now, we delete pit by default. TODO: let frontend decide when to keep it
                    // by passing in the previous pit (e.g. for pagination)
                    // boolean deletePit = Util.requestParameterSet(request.getParameter("deletePit"));
                    boolean deletePit = true;
                    int numFrom = 0;
                    int pageSize = 10;
                    try { numFrom = Integer.parseInt(fromStr); } catch (Exception ex) {}
                    try { pageSize = Integer.parseInt(sizeStr); } catch (Exception ex) {}
                    boolean newQueryToStore = false;
                    String aggError = null;
                    if (query == null) { // must be body (a new query, stored after it passes all gates)
                        query = ServletUtilities.jsonFromHttpServletRequest(request);
                        // Validate aggregations (token path) up front so an invalid aggregation body
                        // is rejected (400). Deny-by-default: only a bounded terms agg on an
                        // allow-listed field is permitted.
                        if (tokenAuth) aggError = OpenSearch.aggregationError(query, indexName);
                        // Persist only AFTER all token gates pass (below), so a rejected body is never
                        // stored, and before applyAclFilter mutates the query.
                        newQueryToStore = (aggError == null);
                    } else { // stored query, so:
                        indexName = query.optString("indexName", null);
                        query = OpenSearch.queryScrubStored(query);
                    }
                    if (aggError != null) {
                        response.setStatus(400);
                        res.put("error", aggError);
                    } else {
                    query = OpenSearch.querySanitize(query, currentUser, myShepherd);
                    // Non-admin token individual search may only QUERY/SORT identity fields.
                    // Fail-closed: if we cannot prove the query touches only allowlisted fields,
                    // reject (400) BEFORE execution so it can't probe hidden cross-encounter
                    // aggregates (numberEncounters, users, encounterIds, ...) via range/sort/aggs.
                    // Checked on the scrubbed query, so it covers BOTH a direct body and a stored
                    // individual query replay.
                    // The URL `sort` param is applied by queryPit() (not present in the body),
                    // so validate it here too: it may only sort on an allowlisted identity field.
                    boolean badSort = (sort != null) && !sort.trim().isEmpty()
                        && !OpenSearch.INDIVIDUAL_TOKEN_KEEP_SET.contains(sort.trim());
                    if (tokenAuth && !isAdmin && "individual".equals(indexName)
                        && (!OpenSearch.queryReferencesOnlyAllowedFields(query,
                            OpenSearch.INDIVIDUAL_TOKEN_KEEP_SET) || badSort)) {
                        response.setStatus(400);
                        res.put("error", "individual token search may only query/sort identity fields");
                    } else {
                    // all token validation gates passed -> persist the clean new query now (before
                    // applyAclFilter embeds ACL fields into it); rejected bodies were never stored
                    boolean storeFailed = false;
                    boolean sessionRecoveryFailed = false;
                    if (newQueryToStore) {
                        // commits + re-begins myShepherd's (read-only so far) transaction
                        searchQueryId = OpenSearch.queryStore(query, indexName, currentUser,
                            myShepherd);
                        storeFailed = (searchQueryId == null);
                        // commit confirmed but the request transaction could not be re-established:
                        // the id IS durable, but sanitizeDoc needs live DB reads -- fail the request
                        // without claiming the store failed, and hand back the (valid) id
                        sessionRecoveryFailed = !storeFailed &&
                            !myShepherd.isDBTransactionActive();
                    }
                    if (storeFailed) {
                        // a search whose response promises a share id must not 200 without one
                        response.setStatus(503);
                        res.put("success", false);
                        res.put("error", "failed to store search query");
                    } else if (sessionRecoveryFailed) {
                        response.setStatus(503);
                        res.put("success", false);
                        res.put("error",
                            "search query stored but request could not continue; retry");
                        res.put("searchQueryId", searchQueryId);
                        response.setHeader("X-Wildbook-Search-Query-Id", searchQueryId);
                    } else {
                    if (tokenAuth && !isAdmin) {
                        // Java is the hard boundary: scope totals + pagination + hits before execution
                        query = OpenSearch.applyAclFilter(query, currentUser.getId(), indexName);
                    }
                    // do not log the full (possibly ACL-scoped) query body / user identifiers
                    System.out.println("SearchApi search indexName=" + indexName
                        + " tokenAuth=" + tokenAuth);

                    OpenSearch os = new OpenSearch();
                    try {
                        if (deletePit) os.deletePit(indexName);
                        JSONObject queryRes = os.queryPit(indexName, query, numFrom, pageSize, sort,
                            sortOrder);
                        JSONObject outerHits = queryRes.optJSONObject("hits");
                        if (outerHits == null) throw new IOException("could not find (outer) hits");
                        JSONArray hits = outerHits.optJSONArray("hits");
                        if (hits == null) throw new IOException("could not find hits");
                        int totalHits = -2;
                        if (outerHits.optJSONObject("total") != null)
                            totalHits = outerHits.getJSONObject("total").optInt("value", -1);
                        // String scrollId = outerHits.optString("_scroll_id", null);
                        JSONArray hitsArr = new JSONArray();
                        for (int i = 0; i < hits.length(); i++) {
                            JSONObject h = hits.optJSONObject(i);
                            if (h == null) throw new IOException("failed to parse hits[" + i + "]");
                            JSONObject doc = h.optJSONObject("_source");
                            if (doc == null)
                                throw new IOException("failed to parse doc in hits[" + i + "]");
                            hitsArr.put(OpenSearch.sanitizeDoc(doc, indexName, myShepherd,
                                currentUser, tokenAuth));
                        }
                        response.setHeader("X-Wildbook-Total-Hits", Integer.toString(totalHits));
                        response.setHeader("X-Wildbook-Search-Query-Id", searchQueryId);
                        // response.setHeader("X-Wildbook-Scroll-Id", scrollId);
                        response.setStatus(200);
                        res.put("success", true);
                        res.put("searchQueryId", searchQueryId);
                        res.put("hits", hitsArr);
                        // Surface a (validated, ACL-scoped) aggregation result on the token path.
                        // Validation gated execution above, so only an allow-listed terms agg reaches
                        // here; for a non-admin the agg ran over the applyAclFilter-wrapped query.
                        if (tokenAuth) {
                            JSONObject aggsOut = queryRes.optJSONObject("aggregations");
                            if (aggsOut != null) res.put("aggregations", aggsOut);
                        }
                        // On the token path `query` has been mutated by applyAclFilter to embed the
                        // ACL filter (internal ACL field names + the caller's UUID). Don't echo it back.
                        if (!tokenAuth) res.put("query", query);
                    } catch (IOException ex) {
                        response.setStatus(500);
                        res.put("success", false);
                        res.put("error", "query failed");
                        ex.printStackTrace();
                    }
                    } // end storeFailed/sessionRecoveryFailed else
                    } // end individual-token field-gate else
                    } // end aggError else
                }
            }
        }
        response.setHeader("Content-Type", "application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(res.toString());
        response.getWriter().close();
        } finally {
            myShepherd.rollbackAndClose();
        }
    }
}
