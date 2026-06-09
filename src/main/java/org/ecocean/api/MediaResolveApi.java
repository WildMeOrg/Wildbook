package org.ecocean.api;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
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
import org.ecocean.media.MediaAssetFactory;
import org.ecocean.media.LocalAssetStore;
import org.ecocean.security.WildbookTokenAuthenticationFilter;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONArray;
import org.json.JSONObject;

public class MediaResolveApi extends ApiBase {

    /** Max annotation IDs accepted per request. */
    static final int MAX_IDS = 100;

    /**
     * Scale an axis-aligned bbox from the source asset's pixel space into the returned
     * derivative's pixel space, then clamp to the derivative bounds.
     * Returns null if inputs are invalid or the scaled region is empty (<1px) — caller omits the entry.
     *
     * @param src  [x, y, width, height] in source-asset pixels
     */
    static int[] scaleBbox(int[] src, double srcW, double srcH, double dstW, double dstH) {
        if ((src == null) || (src.length < 4)) return null;
        if ((srcW <= 0) || (srcH <= 0) || (dstW <= 0) || (dstH <= 0)) return null;
        double sx = dstW / srcW;
        double sy = dstH / srcH;
        int maxW = (int) Math.floor(dstW);
        int maxH = (int) Math.floor(dstH);
        // Scale BOTH corners, clamp each corner to the derivative bounds, THEN derive w/h.
        // (Clamping only the origin and keeping the scaled w/h would mis-size a negative-origin box.)
        // src[i] * sx is int*double -> promotes through double, no overflow; the (long) casts below
        // matter because src[0]+src[2] (an int sum) could overflow before the multiply.
        long x1 = clamp(Math.round(src[0] * sx), 0, maxW);
        long y1 = clamp(Math.round(src[1] * sy), 0, maxH);
        long x2 = clamp(Math.round(((long) src[0] + src[2]) * sx), 0, maxW);
        long y2 = clamp(Math.round(((long) src[1] + src[3]) * sy), 0, maxH);
        int w = (int) (x2 - x1);
        int h = (int) (y2 - y1);
        if ((w < 1) || (h < 1)) return null;
        return new int[] {(int) x1, (int) y1, w, h};
    }

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
     * required piece is missing: annotation, source asset, dimensions, safe derivative, bbox, or url.
     * encounterId/individualId use findEncounter's first parent (documented multi-parent behavior;
     * multi-parent annotations are admin-only in the index so a non-admin never reaches here for them).
     */
    private JSONObject resolveOne(String annotationId, Shepherd myShepherd) {
        Annotation ann = myShepherd.getAnnotation(annotationId);
        if (ann == null) return null;
        MediaAsset src = ann.getMediaAsset();
        if (src == null) return null;
        double srcW = src.getWidth();
        double srcH = src.getHeight();
        if ((srcW <= 0) || (srcH <= 0)) return null;
        MediaAsset deriv = selectSafeDerivative(ann, myShepherd);
        if (deriv == null) return null;
        double dstW = deriv.getWidth();
        double dstH = deriv.getHeight();
        if ((dstW <= 0) || (dstH <= 0)) return null;
        int[] scaled = scaleBbox(ann.getBbox(), srcW, srcH, dstW, dstH);
        if (scaled == null) return null;
        URL url;
        try {
            url = deriv.webURL();
        } catch (RuntimeException ex) {
            // corrupt asset params (e.g. LocalAssetStore.pathFromParameters throws IllegalArgumentException)
            // -> omit this one annotation (fail-closed); never 500 the whole batch.
            return null;
        }
        if (url == null) return null;

        JSONObject e = new JSONObject();
        e.put("id", ann.getId());
        e.put("imageUrl", url.toString());
        e.put("imageWidth", (int) dstW);
        e.put("imageHeight", (int) dstH);
        JSONArray bb = new JSONArray();
        for (int v : scaled) bb.put(v);
        e.put("bbox", bb);
        e.put("theta", ann.getTheta());
        String vp = ann.getViewpoint();
        e.put("viewpoint", (vp != null) ? vp : JSONObject.NULL);
        Encounter enc = ann.findEncounter(myShepherd);
        e.put("encounterId",
            (enc != null && Util.stringExists(enc.getId())) ? enc.getId() : JSONObject.NULL);
        // Derive the individual from the already-loaded encounter. findIndividualId() would re-run
        // findEncounter (a second DB query per annotation); mirror its guard here instead.
        // Util.stringExists already rejects null/blank/"none"/"unknown", so no extra "None" check.
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

    private static long clamp(long v, long lo, long hi) {
        return (v < lo) ? lo : (v > hi ? hi : v);
    }

    /**
     * Select the safe derivative whose pixels are a UNIFORM SCALE of the annotation's bbox frame, so
     * the bbox (defined in {@code ann.getMediaAsset()} space) maps to it by a single dimension ratio.
     *
     * Two paths, in order:
     *  1. A _master/_mid child of the annotation's OWN asset — always a uniform scale of that frame.
     *  2. If the own asset has no such child, the derivatives hang off the _original ANCESTOR and the
     *     annotation's asset is a sibling of them. Walk to the _original parent (mirroring
     *     MediaAsset.bestSafeAsset) and use its derivative ONLY when it is a uniform scale of the
     *     source frame (matching aspect ratio) — i.e. the source is a full-frame view, not a
     *     crop/transform. Otherwise the bbox would be scaled against a different coordinate plane, so
     *     we omit (fail-closed).
     *
     * Chosen assets must be LocalAssetStore-backed (allowlist: rejects URLAssetStore external
     * originals AND YouTubeAssetStore watch pages) and never carry _original. Deliberately does NOT
     * use MediaAsset.safeURL/bestSafeAsset (returns originals for URLAssetStore; no master->mid fallback).
     */
    static MediaAsset selectSafeDerivative(Annotation ann, Shepherd myShepherd) {
        if (ann == null) return null;
        MediaAsset src = ann.getMediaAsset();
        if (src == null) return null;
        if (!(src.getStore() instanceof LocalAssetStore)) return null;
        // Path 1: a derivative of the annotation's own asset is a uniform scale of its frame -> safe.
        MediaAsset own = findSafeDerivativeChild(src, myShepherd);
        if (own != null) return own;
        // Path 2: derivatives live on the _original ancestor; src is a sibling. Walk to the parent.
        Integer parentId = src.getParentId();
        if (parentId == null) return null;
        MediaAsset parent;
        try {
            parent = MediaAssetFactory.load(parentId, myShepherd);
        } catch (RuntimeException ex) {
            return null; // parent lookup unavailable -> omit this id (fail-soft, never 500 the batch)
        }
        if ((parent == null) || !parent.hasLabel("_original")) return null;
        MediaAsset cand = findSafeDerivativeChild(parent, myShepherd);
        if (cand == null) return null;
        // Only safe if the parent's derivative is a uniform scale of the source frame (full-frame
        // sibling). A crop/transform source has a different aspect ratio -> omit (fail-closed).
        if (!isUniformScale(src, cand)) return null;
        return cand;
    }

    /** First _master (else _mid) child of {@code base} that is LocalAssetStore-backed and not _original. */
    private static MediaAsset findSafeDerivativeChild(MediaAsset base, Shepherd myShepherd) {
        if (base == null) return null;
        for (String label : new String[] {"_master", "_mid"}) {
            ArrayList<MediaAsset> kids = base.findChildrenByLabel(myShepherd, label);
            // findChildrenByLabel returns an EMPTY list (not null) when children exist but none
            // match this label — treat that the same as "no match" and try the next label.
            if ((kids == null) || kids.isEmpty()) continue;
            for (MediaAsset kid : kids) {
                if (kid == null) continue;
                if (!(kid.getStore() instanceof LocalAssetStore)) continue;
                if (kid.hasLabel("_original")) continue;
                return kid;
            }
        }
        return null;
    }

    /**
     * True iff {@code deriv} is (within ~1% rounding tolerance) a uniform scale of {@code src}'s pixel
     * frame — the precondition for mapping an axis-aligned bbox by a single dimension ratio. Guards
     * against cropped/transformed sources whose aspect ratio differs from a full-frame derivative.
     */
    private static boolean isUniformScale(MediaAsset src, MediaAsset deriv) {
        double sw = src.getWidth();
        double sh = src.getHeight();
        double dw = deriv.getWidth();
        double dh = deriv.getHeight();
        if ((sw <= 0) || (sh <= 0) || (dw <= 0) || (dh <= 0)) return false;
        double sx = dw / sw;
        double sy = dh / sh;
        return Math.abs(sx - sy) <= 0.01 * Math.max(sx, sy);
    }
}
