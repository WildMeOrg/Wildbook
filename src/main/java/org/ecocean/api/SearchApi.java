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
    public void doGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        doPost(request, response);
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        String context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);

        myShepherd.setAction("api.SearchApi.POST");
        myShepherd.beginDBTransaction();

        User currentUser = myShepherd.getUser(request);
        JSONObject res = new JSONObject();
        boolean tokenAuth = Boolean.TRUE.equals(
            request.getAttribute(org.ecocean.security.WildbookTokenAuthenticationFilter.TOKEN_AUTH_ATTR));
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
                    query = OpenSearch.queryLoad(searchQueryId);
                }
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
                // --- existing validity checks ---
                } else if ((searchQueryId != null) && (query == null)) {
                    response.setStatus(404);
                    res.put("error", "invalid searchQueryId " + searchQueryId);
                } else if ((searchQueryId == null) && !OpenSearch.isValidIndexName(indexName)) {
                    response.setStatus(404);
                    res.put("error", "unknown index");
                } else if ("annotation".equals(indexName) && !isAdmin) {
                    // per discussion with jh today, api exposure of annotations admin-only currently
                    response.setStatus(403);
                    res.put("error", 403);
                // --- token encounter-only index gate + stored-query owner check ---
                } else if (tokenAuth && !"encounter".equals(
                    (searchQueryId != null) ? query.optString("indexName", null) : indexName)) {
                    // covers stored queries whose real index is read from the stored doc, not the URL
                    response.setStatus(403);
                    res.put("error", "token search is limited to the encounter index");
                } else if (tokenAuth && (searchQueryId != null) && (query != null)
                    && !isAdmin
                    && !currentUser.getId().equals(query.optString("creator", null))) {
                    // replaying someone else's stored query is not allowed (admin bypasses)
                    response.setStatus(403);
                    res.put("error", "not the owner of this stored query");
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
                    if (query == null) { // must be body (new query, needs storing)
                        query = ServletUtilities.jsonFromHttpServletRequest(request);
                        // we store this *before* we sanitize
                        searchQueryId = OpenSearch.queryStore(query, indexName, currentUser);
                    } else { // stored query, so:
                        indexName = query.optString("indexName", null);
                        query = OpenSearch.queryScrubStored(query);
                    }
                    query = OpenSearch.querySanitize(query, currentUser, myShepherd);
                    if (tokenAuth && !isAdmin) {
                        // Java is the hard boundary: scope totals + pagination + hits before execution
                        query = OpenSearch.applyEncounterAclFilter(query, currentUser.getId());
                    }
                    System.out.println("SearchApi (sanitized) indexName=" + indexName + "; query=" +
                        query);

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
                                currentUser));
                        }
                        response.setHeader("X-Wildbook-Total-Hits", Integer.toString(totalHits));
                        response.setHeader("X-Wildbook-Search-Query-Id", searchQueryId);
                        // response.setHeader("X-Wildbook-Scroll-Id", scrollId);
                        response.setStatus(200);
                        res.put("success", true);
                        res.put("searchQueryId", searchQueryId);
                        res.put("hits", hitsArr);
                        res.put("query", query);
                    } catch (IOException ex) {
                        response.setStatus(500);
                        res.put("success", false);
                        res.put("error", "query failed");
                        ex.printStackTrace();
                    }
                }
            }
        }
        response.setHeader("Content-Type", "application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(res.toString());
        response.getWriter().close();
        myShepherd.rollbackAndClose();
    }
}
