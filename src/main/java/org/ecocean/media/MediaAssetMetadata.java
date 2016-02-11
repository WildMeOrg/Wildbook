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

/*
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
*/

import org.json.JSONObject;
import org.json.JSONException;


/**
 * MediaAssetMetadata is *machine-read* (i.e. not human input or confirmed) data *derived from* the raw file itself.
 * Most likely it is Exif(-ish) data, but may be some other analytical stuff.  There should be a one-to-one mapping from MediaAsset to MediaAssetMetadata
 */
public class MediaAssetMetadata implements java.io.Serializable {
    static final long serialVersionUID = 8844123150443974780L;
    protected JSONObject data;

    public MediaAssetMetadata() {
        data = new JSONObject();
    }
    public MediaAssetMetadata(JSONObject d) {
        data = d;
    }


    public JSONObject getData() {
        return data;
    }

    public void setData(JSONObject d) {
        data = d;
    }

    public String getDataAsString() {
        if (data == null) return null;
        return data.toString();
    }

    public void setDataAsString(String d) {
        if (d == null) return;
        try {
            data = new JSONObject(d);
        } catch (JSONException je) {
            System.out.println(this + " -- error parsing data json string (" + d + "): " + je.toString());
            data = null;
        }
    }


/*
    public String toString() {
        return new ToStringBuilder(this)
                .append("id", id)
                .append("rev", revision)
                .append("type", type.toString())
                .toString();
    }
*/

}
