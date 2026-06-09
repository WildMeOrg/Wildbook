package org.ecocean.api;

import java.io.IOException;
import java.net.URL;
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
import org.ecocean.media.MediaAsset;
import org.ecocean.media.LocalAssetStore;
import org.ecocean.security.WildbookTokenAuthenticationFilter;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONArray;
import org.json.JSONObject;

public class MediaResolveApi extends ApiBase {

    /** Max annotation IDs accepted per request. */
    static final int MAX_IDS = 100;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        handle(request, response);
    }

    // package-visible entry point for unit tests
    void doPostForTest(HttpServletRequest request, HttpServletResponse response)
    throws IOException {
        handle(request, response);
    }

    private void handle(HttpServletRequest request, HttpServletResponse response)
    throws IOException {
        boolean tokenAuth = Boolean.TRUE.equals(
            request.getAttribute(WildbookTokenAuthenticationFilter.TOKEN_AUTH_ATTR));
        if (!tokenAuth) {
            writeError(response, 401, "token auth required");
            return;
        }
        // Verified context ONLY — never fall back to ServletUtilities.getContext() (no session on
        // this endpoint, so a caller must not be able to steer context via ?context=/cookie/host).
        String context = (String) request.getAttribute(
            WildbookTokenAuthenticationFilter.TOKEN_CONTEXT_ATTR);
        if (!Util.stringExists(context)) {
            writeError(response, 401, "unauthorized");
            return;
        }
        // Parse + validate the body BEFORE constructing a Shepherd or touching the DB/OpenSearch.
        // Malformed JSON throws here -> 400 (not the 500 a later broad catch would give).
        Set<String> ids;
        try {
            JSONObject body = ServletUtilities.jsonFromHttpServletRequest(request);
            JSONArray idArr = (body != null) ? body.optJSONArray("annotationIds") : null;
            if ((idArr == null) || (idArr.length() == 0) || (idArr.length() > MAX_IDS)) {
                writeError(response, 400, "annotationIds must be a non-empty array of <= " + MAX_IDS);
                return;
            }
            ids = new LinkedHashSet<>();
            for (int i = 0; i < idArr.length(); i++) {
                String s = idArr.optString(i, null);
                if (Util.stringExists(s)) ids.add(s);
            }
            if (ids.isEmpty()) {
                writeError(response, 400, "annotationIds must contain at least one valid id");
                return;
            }
        } catch (Exception ex) {
            writeError(response, 400, "malformed request body");
            return;
        }

        Shepherd myShepherd = null;
        try {
            myShepherd = new Shepherd(context);
            myShepherd.setAction("api.MediaResolveApi.POST");
            myShepherd.beginDBTransaction();
            User currentUser = myShepherd.getUser(request);
            if ((currentUser == null) || (currentUser.getId() == null)) {
                writeError(response, 401, "unauthorized");
                return;
            }
            boolean isAdmin = currentUser.isAdmin(myShepherd);
            Set<String> visible = isAdmin ? ids : gatedVisibleIds(ids, currentUser.getId());
            JSONArray results = new JSONArray();
            for (String id : visible) {
                JSONObject entry = resolveOne(id, myShepherd);
                if (entry != null) results.put(entry);
            }
            response.setStatus(200);
            writeBody(response, results.toString());
        } catch (IOException ioEx) {
            throw ioEx; // broken pipe / client gone — let the container handle; don't double-write
        } catch (Exception ex) {
            writeError(response, 500, "resolve failed");
            ex.printStackTrace();
        } finally {
            if (myShepherd != null) myShepherd.rollbackAndClose();
        }
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

    /**
     * Visibility gate (non-admin): reuse Spec A's exact annotation ACL filter over an ids query.
     * Returns the subset of requested ids the token may see. Query size is set to the id count so
     * none are dropped by the default page size.
     */
    // package-visible for unit testing
    Set<String> gatedVisibleIds(Set<String> ids, String userId) throws IOException {
        JSONObject query = OpenSearch.buildIdEligibilityQuery(ids);
        query = OpenSearch.applyAclFilter(query, userId, "annotation");
        OpenSearch os = new OpenSearch();
        // deletePit-then-queryPit mirrors SearchApi (SearchApi.java:159): forces a FRESH point-in-time
        // so the ACL gate reads current state (createPit reuses a cached PIT otherwise, which could be
        // stale and miss a just-revoked viewUser). PIT_CACHE being process-static is a pre-existing,
        // systemic property shared by all OpenSearch callers — not addressed here.
        os.deletePit("annotation");
        JSONObject res = os.queryPit("annotation", query, 0, ids.size(), null, null);
        Set<String> visible = new LinkedHashSet<>();
        JSONObject outer = (res != null) ? res.optJSONObject("hits") : null;
        JSONArray hits = (outer != null) ? outer.optJSONArray("hits") : null;
        if (hits != null) {
            for (int i = 0; i < hits.length(); i++) {
                JSONObject h = hits.optJSONObject(i);
                if (h == null) continue;
                String hid = h.optString("_id", null);
                if (Util.stringExists(hid)) visible.add(hid);
            }
        }
        return visible;
    }

    /**
     * Resolve one visible annotation into a response entry, or null to omit (fail-closed) when any
     * required piece is missing. The bbox is returned in the ANNOTATION ASSET's coordinate space
     * (whose dimensions are reliably stored, unlike derivative children's) — NOT scaled into the
     * served derivative. The consumer fetches imageUrl, reads its real pixel size, and scales bbox by
     * realDim/reportedDim before cropping (usually a no-op since _master == source size).
     * encounterId/individualId use the first parent (findEncounter); multi-parent annotations are
     * admin-only in the index, so a non-admin never reaches here for them.
     */
    private JSONObject resolveOne(String annotationId, Shepherd myShepherd) {
        Annotation ann = myShepherd.getAnnotation(annotationId);
        if (ann == null) return null;
        MediaAsset src = ann.getMediaAsset();
        if (src == null) return null;
        // Only LocalAssetStore assets yield servable image bytes (rejects URLAssetStore externals and
        // YouTubeAssetStore watch pages).
        if (!(src.getStore() instanceof LocalAssetStore)) return null;
        double w = src.getWidth();
        double h = src.getHeight();
        if ((w <= 0) || (h <= 0)) return null;
        int[] bbox = clampBbox(ann.getBbox(), w, h);
        if (bbox == null) return null;
        URL url = safeServableUrl(src, myShepherd);
        if (url == null) return null;

        JSONObject e = new JSONObject();
        e.put("id", ann.getId());
        e.put("imageUrl", url.toString());
        e.put("imageWidth", (int) w);
        e.put("imageHeight", (int) h);
        JSONArray bb = new JSONArray();
        for (int v : bbox) bb.put(v);
        e.put("bbox", bb);
        e.put("theta", ann.getTheta());
        String vp = ann.getViewpoint();
        e.put("viewpoint", (vp != null) ? vp : JSONObject.NULL);
        Encounter enc = ann.findEncounter(myShepherd);
        e.put("encounterId",
            (enc != null && Util.stringExists(enc.getId())) ? enc.getId() : JSONObject.NULL);
        String indId = (enc != null && enc.hasMarkedIndividual()) ? enc.getIndividualID() : null;
        e.put("individualId", Util.stringExists(indId) ? indId : JSONObject.NULL);
        JSONArray mvs = new JSONArray();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        if (ann.getEmbeddings() != null) {
            for (Embedding emb : ann.getEmbeddings()) {
                String mv = (emb != null) ? emb.getMethodVersion() : null;
                if (Util.stringExists(mv) && seen.add(mv)) mvs.put(mv);
            }
        }
        e.put("methodVersion", mvs);
        return e;
    }

    /**
     * Servable URL in the SAME coordinate frame as the bbox (the annotation asset's frame), so the
     * consumer's scale-only transform is valid (a scale cannot recover a crop offset).
     *
     * A child asset (has a parent) is a derivative — safe to serve directly, and it IS the bbox frame.
     * A ROOT asset (no parent) is the raw upload and must NOT be served directly even if it lacks the
     * `_original` label (some import paths create unlabeled raw roots) — instead serve a `_master`/
     * `_mid` child of it (an aspect-preserving scale of the same frame; the consumer scales). The
     * `_original` label is also honored as a raw-root signal. LocalAssetStore/non-`_original` guards;
     * fail-soft (any error or null URL -> omit, never 500 the batch).
     */
    private static URL safeServableUrl(MediaAsset src, Shepherd myShepherd) {
        try {
            boolean rawRoot = src.hasLabel("_original") || (src.getParentId() == null);
            if (!rawRoot) {
                return src.webURL(); // a child derivative; this IS the bbox frame. null -> omit
            }
            for (String type : new String[] {"master", "mid"}) {
                MediaAsset a = src.bestSafeAsset(myShepherd, null, type);
                if (a == null) continue;
                if (!(a.getStore() instanceof LocalAssetStore)) continue;
                if (a.hasLabel("_original")) continue;
                if (a.getParentId() == null) continue; // must be a child derivative, never a self-returned root
                URL u = a.webURL();
                if (u != null) return u;
            }
            return null;
        } catch (RuntimeException ex) {
            return null; // fail-soft
        }
    }

    /** Clamp bbox [x,y,w,h] (source pixel space) to [0,W]x[0,H] by clamping both corners; null if
        invalid or empty (incl. negative/inverted). */
    private static int[] clampBbox(int[] b, double W, double H) {
        if ((b == null) || (b.length < 4)) return null;
        long maxW = (long) Math.floor(W);
        long maxH = (long) Math.floor(H);
        long x1 = clampLong((long) b[0], 0, maxW);
        long y1 = clampLong((long) b[1], 0, maxH);
        long x2 = clampLong((long) b[0] + b[2], 0, maxW);
        long y2 = clampLong((long) b[1] + b[3], 0, maxH);
        int w = (int) (x2 - x1);
        int h = (int) (y2 - y1);
        if ((w < 1) || (h < 1)) return null;
        return new int[] {(int) x1, (int) y1, w, h};
    }

    private static long clampLong(long v, long lo, long hi) {
        return (v < lo) ? lo : (v > hi ? hi : v);
    }
}
