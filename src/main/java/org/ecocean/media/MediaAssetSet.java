package org.ecocean.media;

import java.util.ArrayList;
import javax.servlet.http.HttpServletRequest;
import org.ecocean.Util;

/**
 * MediaAsset describes a photo or video that can be displayed or used for processing and analysis.
 */
public class MediaAssetSet implements java.io.Serializable {
    static final long serialVersionUID = 8844392150447974780L;

    protected String id;
    protected ArrayList<MediaAsset> assets = null;
    protected String status = null;
    protected long timestamp;

    public MediaAssetSet() {
        this(null);
    }

    public MediaAssetSet(final ArrayList<MediaAsset> set) {
        this.id = Util.generateUUID();
        this.timestamp = System.currentTimeMillis();
        this.assets = set;
    }

    public String getId() {
        return id;
    }

    public ArrayList<MediaAsset> getMediaAssets() {
        return assets;
    }

    public void setMediaAssets(ArrayList<MediaAsset> set) {
        assets = set;
    }

    public ArrayList<MediaAsset> addMediaAsset(MediaAsset ma) {
        if (assets == null) assets = new ArrayList<MediaAsset>();
        assets.add(ma);
        return assets;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String s) {
        status = s;
    }

    public org.datanucleus.api.rest.orgjson.JSONObject sanitizeJson(HttpServletRequest request,
        org.datanucleus.api.rest.orgjson.JSONObject jobj)
    throws org.datanucleus.api.rest.orgjson.JSONException {
        // TODO security check, duh
        jobj.put("id", id);
        jobj.put("timestamp", timestamp);
        if ((getMediaAssets() != null) && (getMediaAssets().size() > 0)) {
            org.datanucleus.api.rest.orgjson.JSONArray assets =
                new org.datanucleus.api.rest.orgjson.JSONArray();
            for (MediaAsset ma : getMediaAssets()) {
                assets.put(ma.sanitizeJson(request,
                    new org.datanucleus.api.rest.orgjson.JSONObject(), true));
            }
            jobj.put("assets", assets);
        }
        return jobj;
    }
}
