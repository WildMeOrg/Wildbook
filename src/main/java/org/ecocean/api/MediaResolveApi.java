package org.ecocean.api;

import java.util.ArrayList;

import org.ecocean.Annotation;
import org.ecocean.media.MediaAsset;
import org.ecocean.media.LocalAssetStore;
import org.ecocean.shepherd.core.Shepherd;

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

    private static long clamp(long v, long lo, long hi) {
        return (v < lo) ? lo : (v > hi ? hi : v);
    }

    /**
     * Select the safe derivative to serve for an annotation's region: a child of the source asset
     * labeled _master (preferred) or _mid. Both the source and the chosen derivative must be backed
     * by a LocalAssetStore — an ALLOWLIST, not a denylist: this rejects URLAssetStore (external/public
     * originals) AND YouTubeAssetStore (webURL is a watch page, not cropable image bytes). Also skips
     * any child carrying _original. Returns null if none qualifies (caller omits).
     * Deliberately does NOT use MediaAsset.safeURL/bestSafeAsset, which can return originals for
     * URLAssetStore and does not fall back from a missing _master to _mid.
     */
    static MediaAsset selectSafeDerivative(Annotation ann, Shepherd myShepherd) {
        if (ann == null) return null;
        MediaAsset src = ann.getMediaAsset();
        if (src == null) return null;
        if (!(src.getStore() instanceof LocalAssetStore)) return null;
        for (String label : new String[] {"_master", "_mid"}) {
            ArrayList<MediaAsset> kids = src.findChildrenByLabel(myShepherd, label);
            if (kids == null) continue;
            for (MediaAsset kid : kids) {
                if (kid == null) continue;
                if (!(kid.getStore() instanceof LocalAssetStore)) continue;
                if (kid.hasLabel("_original")) continue;
                return kid;
            }
        }
        return null;
    }
}
