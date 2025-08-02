package org.ecocean.media;

import java.io.IOException;
import java.util.HashMap;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.ecocean.Annotation;
import org.ecocean.Util;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * A Feature is a unique set of (arbitrary) data that contributes to helping with detection and identification of images (or other data, e.g. audio).
 * It is metadata that can be generated either manually or automated (or both).
 */
public class Feature implements java.io.Serializable {
    static final long serialVersionUID = 8844223450443974780L;
    protected String id = null;

    // NOTE: a null for feature type we call the "unity" case -- meaning it serves only as a direct map from Annotation to MediaAsset.
    // this can be thought of as "no" feature, or the "entire asset" feature, etc. (ostensibly parameters would be ignored/useless, but that may
    // change in the future)
    protected FeatureType type;

    protected JSONObject parameters;

    // this link back to the objs with .features that include us
    protected Annotation annotation;
    protected MediaAsset asset;

    protected long revision;

    // effectively creates a "unity" feature
    public Feature() {
        this(Util.generateUUID(), null, null);
    }
/*
    public Feature(final String typeAsString, final JSONObject params) {
        this(new FeatureType(typeAsString), params);
    }
 */
    public Feature(final FeatureType type, final JSONObject params) {
        this(Util.generateUUID(), type, params);
    }

    public Feature(final String featureTypeId, final JSONObject params) {
        this(Util.generateUUID(), FeatureType.load(featureTypeId), params);
    }

    public Feature(final String id, final FeatureType type, final JSONObject params) {
        if (id == null) throw new IllegalArgumentException("id is null");
        this.id = id;
        this.type = type;
        this.parameters = params;
        this.setRevision();
    }

    public String getId() {
        return id;
    }

    public void setId(String i) {
        id = i;
    }

    public FeatureType getType() {
        return type;
    }

    public void setType(FeatureType t) {
        type = t;
    }

    public boolean isType(String tid) { // pass in string version of FeatureType.id (e.g. "org.ecocean.fubar")
        if ((type == null) && (tid == null)) return true; // who would really do this?
        if (type == null) return false;
        return type.getId().equals(tid);
    }

    public boolean isUnity() {
        return (type == null);
    }

    public Annotation getAnnotation() {
        return annotation;
    }

    public MediaAsset getMediaAsset() {
        return asset;
    }

    public JSONObject getParameters() {
// System.out.println("getParameters() called -> " + parameters);
        return parameters;
    }

    public void setParameters(JSONObject p) {
// System.out.println("setParameters(" + p + ") called");
        parameters = p;
    }

    public String getParametersAsString() {
// System.out.println("getParametersAsString() called -> " + parameters);
        if (parameters == null) return null;
        return parameters.toString();
    }

    public void setParametersAsString(String p) {
// System.out.println("setParametersAsString(" + p + ") called");
        if (p == null) return;
        try {
            parameters = new JSONObject(p);
        } catch (JSONException je) {
            System.out.println(this + " -- error parsing parameters json string (" + p + "): " +
                je.toString());
            parameters = null;
        }
    }

    public long getRevision() {
        return revision;
    }

    public long setRevision() {
        this.revision = System.currentTimeMillis();
        return this.revision;
    }

    public String toString() {
        return new ToStringBuilder(this)
                   .append("id", id)
                   .append("rev", revision)
                   .append("type", type)
                   .append("asset", asset)
                   .toString();
    }

    /*
        this is how we derive a MediaAsset from the source MediaAsset this Feature references.
        for images, this might do some kind of clipping or rotation etc.  perhaps in the future this may need to return N MediaAssets?
        ideally we would break this out into neater classes, perhaps per MediaAsset type (image, video, sound)
     */
    public MediaAsset createMediaAsset()
    throws IOException {
        MediaAsset ma = this.getMediaAsset();

        if (ma == null) return null;
        if (this.isUnity()) return null; // we shouldnt make a new MA that is identical, right?
        HashMap<String, Object> hmap = new HashMap<String, Object>();
        hmap.put("feature", this);
        return ma.updateChild("feature", hmap);
    }

    public org.datanucleus.api.rest.orgjson.JSONObject sanitizeJson(HttpServletRequest request,
        boolean fullAccess)
    throws org.datanucleus.api.rest.orgjson.JSONException {
        org.datanucleus.api.rest.orgjson.JSONObject jobj =
            new org.datanucleus.api.rest.orgjson.JSONObject();
        jobj.put("id", id);
        if (this.getType() != null) jobj.put("type", this.getType().getId());
        if (this.getParameters() != null)
            jobj.put("parameters", Util.toggleJSONObject(getParameters()));
        if (this.getMediaAsset() != null)
            jobj.put("mediaAsset",
                this.getMediaAsset().sanitizeJson(request,
                new org.datanucleus.api.rest.orgjson.JSONObject(), fullAccess));                                                                                              //
                                                                                                                                                                              // "should
                                                                                                                                                                              // never"
                                                                                                                                                                              // be
                                                                                                                                                                              // null
                                                                                                                                                                              // anyway
        return jobj;
    }

    // default behavior is limited access
    public org.datanucleus.api.rest.orgjson.JSONObject sanitizeJson(HttpServletRequest request)
    throws org.datanucleus.api.rest.orgjson.JSONException {
        return this.sanitizeJson(request, false);
    }

    // our standard will be 0 ("same") when things are not-comparable for any reason.  :/
    public int comparePositional(Feature other) {
        if (other == null) return 0;
        // it might be kinda silly to use "left edge" of unity (i.e. 0) but i am going for it!
        // right now only support bounding box.  have fun in the future!
        int x1 = -1;
        int y1 = -1;
        int x2 = -1;
        int y2 = -1;
        if (this.isUnity()) {
            x1 = 0;
            y1 = 0;
        } else if (this.isType("org.ecocean.boundingBox") && (this.getParameters() != null)) {
            x1 = this.getParameters().optInt("x", 0);
            y1 = this.getParameters().optInt("y", 0);
        } else {
            return 0;
        }
        if (other.isUnity()) {
            x2 = 0;
            y2 = 0;
        } else if (other.isType("org.ecocean.boundingBox") && (other.getParameters() != null)) {
            x2 = other.getParameters().optInt("x", 0);
            y2 = other.getParameters().optInt("y", 0);
        } else {
            return 0;
        }
        if (x1 == x2) return (y1 - y2);
        return (x1 - x2);
    }

    public boolean equals(Feature other) {
        if (other == null) return false;
        if (id == null) return false; // snh
        return id.equals(other.getId());
    }
}
