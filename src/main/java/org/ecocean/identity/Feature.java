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

package org.ecocean.identity;

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
//import javax.jdo.Query;


/**
 * A Feature is a unique set of (arbitrary) data that contributes to helping with detection and identification of images (or other data, e.g. audio).
 * It is metadata that can be generated either manually or automated (or both).
 */
public class Feature implements java.io.Serializable {
    static final long serialVersionUID = 8844223450443974780L;
    protected String id = null;

    protected FeatureType type;
    protected JSONObject parameters;

    protected long revision;

/*
    public Feature(final String typeAsString, final JSONObject params) {
        this(new FeatureType(typeAsString), params);
    }
*/
    public Feature(final FeatureType type, final JSONObject params) {
        this(Util.generateUUID(), type, params);
    }


    public Feature(final String id, final FeatureType type, final JSONObject params) {
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
        if (type == null) return false;  //"should never happen"
        return type.getId().equals(tid);
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
                .append("type", type.toString())
                .toString();
    }

}
