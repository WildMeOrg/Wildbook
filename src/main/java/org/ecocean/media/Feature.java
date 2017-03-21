/*
 * This file is a part of Wildbook.
 * Copyright (C) 2015 WildMe
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Foobar is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Wildbook.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.ecocean.media;

import org.ecocean.CommonConfiguration;
import org.ecocean.ImageAttributes;
import org.ecocean.Keyword;
import org.ecocean.Annotation;
import org.ecocean.Shepherd;
import org.ecocean.Encounter;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Files;
//import java.time.LocalDateTime;
import org.joda.time.DateTime;
import java.util.Date;
import org.json.JSONObject;
import org.json.JSONException;
import java.util.Set;
import java.util.HashMap;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.ecocean.Util;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
//import java.io.FileInputStream;
import javax.jdo.Query;


/**
 * A Feature is a unique set of (arbitrary) data that contributes to helping with detection and identification of images (or other data, e.g. audio).
 * It is metadata that can be generated either manually or automated (or both).
 */
public class Feature implements java.io.Serializable {
    static final long serialVersionUID = 8844223450443974780L;
    protected String id = null;

    //NOTE: a null for feature type we call the "unity" case -- meaning it serves only as a direct map from Annotation to MediaAsset.
    //  this can be thought of as "no" feature, or the "entire asset" feature, etc. (ostensibly parameters would be ignored/useless, but that may change in the future)
    protected FeatureType type;

    protected JSONObject parameters;

    //this link back to the objs with .features that include us
    protected Annotation annotation;
    protected MediaAsset asset;

    protected long revision;

    //effectively creates a "unity" feature
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
    public boolean isType(String tid) {  //pass in string version of FeatureType.id (e.g. "org.ecocean.fubar")
        if ((type == null) && (tid == null)) return true;  //who would really do this?
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
//System.out.println("getParameters() called -> " + parameters);
        return parameters;
    }

    public void setParameters(JSONObject p) {
//System.out.println("setParameters(" + p + ") called");
        parameters = p;
    }

    public String getParametersAsString() {
//System.out.println("getParametersAsString() called -> " + parameters);
        if (parameters == null) return null;
        return parameters.toString();
    }

    public void setParametersAsString(String p) {
//System.out.println("setParametersAsString(" + p + ") called");
        if (p == null) return;
        try {
            parameters = new JSONObject(p);
        } catch (JSONException je) {
            System.out.println(this + " -- error parsing parameters json string (" + p + "): " + je.toString());
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


    public MediaAsset createMediaAsset() throws IOException {
        MediaAsset ma = this.getMediaAsset();
        if (ma == null) return null;
        if (this.isUnity()) return null;  //we shouldnt make a new MA that is identical, right?
        HashMap<String,Object> hmap = new HashMap<String,Object>();
        hmap.put("feature", this);
        return ma.updateChild("feature", hmap);
    }


    public org.datanucleus.api.rest.orgjson.JSONObject sanitizeJson(HttpServletRequest request,
                                                                    boolean fullAccess) throws org.datanucleus.api.rest.orgjson.JSONException {
        org.datanucleus.api.rest.orgjson.JSONObject jobj = new org.datanucleus.api.rest.orgjson.JSONObject();
        jobj.put("id", id);
        if (this.getType() != null) jobj.put("type", this.getType().getId());
        if (this.getParameters() != null) jobj.put("parameters", Util.toggleJSONObject(getParameters()));
        if (this.getMediaAsset() != null) jobj.put("mediaAsset", this.getMediaAsset().sanitizeJson(request, new org.datanucleus.api.rest.orgjson.JSONObject(), fullAccess));  //"should never" be null anyway
        return jobj;
    }

    //default behavior is limited access
    public org.datanucleus.api.rest.orgjson.JSONObject sanitizeJson(HttpServletRequest request) throws org.datanucleus.api.rest.orgjson.JSONException {
        return this.sanitizeJson(request, false);
    }

}
