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

import java.util.HashMap;
import java.util.Iterator;
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

    //a convenience method which accesses the data.attributes JSONObject
    // even if it doesnt exist, this will return an empty one for convenience of calling .optFOO(key) easily on it
    public JSONObject getAttributes() {
        if ((getData() == null) || (getData().optJSONObject("attributes") == null)) return new JSONObject();
        return getData().getJSONObject("attributes");
    }


    //for now(?) this just searches down into exif structure, returns HashMap of key:values whose keys match regex
    //  deeper values with same keys will overwrite earlier
    //  note also that keys will be squashed to lowercase (hence squashing regex)
    public HashMap<String,String> findRecurse(String regex) {
        if ((getData() == null) || (getData().optJSONObject("exif") == null)) return null;
        return _find(getData().getJSONObject("exif"), regex.toLowerCase());
    }

    private static HashMap<String,String> _find(JSONObject jobj, String regex) {
        HashMap<String,String> found = new HashMap<String,String>();
        Iterator<String> it = jobj.keys();
        while (it.hasNext()) {
            String k = it.next();
            JSONObject sub = jobj.optJSONObject(k);
            if (sub != null) {  //recurse down...
                HashMap<String,String> fsub = _find(sub, regex);
                if (fsub != null) found.putAll(fsub);
                continue;
            }
            //here on out, we assume we have key:value pairs
            if (!k.toLowerCase().matches(regex)) continue;
            found.put(k, jobj.optString(k, null));
        }
        return found;
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
