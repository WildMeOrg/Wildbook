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

import org.ecocean.Util;

import java.util.Date;
import org.joda.time.DateTime;
import java.text.SimpleDateFormat;


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
    //  key is "tree-like" as it recurses: "level1:level2:key" => "value" (to help prevent squashing)
    //   .. however, squashing may occur cuz of lowercase-ing
    //  note also that keys will be squashed to lowercase (hence squashing regex)
    public HashMap<String,String> findRecurse(String regex) {
        if ((getData() == null) || (getData().optJSONObject("exif") == null)) return null;
        return _find(getData().getJSONObject("exif"), null, regex.toLowerCase());
    }

    private static HashMap<String,String> _find(JSONObject jobj, String keyPrefix, String regex) {
        if (jobj == null) return null;
        HashMap<String,String> found = new HashMap<String,String>();
        Iterator<String> it = jobj.keys();
        while (it.hasNext()) {
            String k = it.next();
            String fullK = ((keyPrefix == null) ? k : keyPrefix + ":" + k);
            JSONObject sub = jobj.optJSONObject(k);
            if (sub != null) {  //recurse down...
                HashMap<String,String> fsub = _find(sub, fullK, regex);
                if (fsub != null) found.putAll(fsub);
                continue;
            }
            //here on out, we assume we have key:value pairs
            if (!k.toLowerCase().matches(regex)) continue;
            found.put(fullK, jobj.optString(k, null));
        }
        return found;
    }

/* sigh. ok, date/time created. sounds easy?  nope. here is an example of exif data *from a single image* ....

Iptc: {    /// thanks for not following "yyyy:MM:dd HH:mm:ss" iptc.  :(
	Digital Date Created: "20140811",
	Time Created: "134915",
	Date Created: "Mon Aug 11 00:00:00 PDT 2014",   ///who needs time, right?  and wtf is "134915" ... swatch time???
	Digital Time Created: "134915"
}
Exif SubIFD: {
	Sub-Sec Time Original: "70",
	Sub-Sec Time Digitized: "70",
	Date/Time Original: "2014:08:11 13:49:15",
	Date/Time Digitized: "2014:08:11 13:49:15",
}
Exif IFD0: {
	Date/Time: "2014:10:20 16:06:40",   ///  WTF!?   ... and just our luck the recursing down found this first.  SIGH!!!!!!
}

oh, and incidentally GPS block often has time in it too.  :( :( :(   @@
*/

    public DateTime getDateTime() {
        if ((getData() == null) || (getData().optJSONObject("exif") == null)) return null;
        HashMap<String,String> matches = _find(getData().getJSONObject("exif"), null, ".*date.*");
//System.out.println("MediaAssetMetadata.getDateTime() ----> " + matches);
        //we attempt to find "the most prevalant" one.  ugh.  give me a break.
        HashMap<DateTime,Integer> count = new HashMap<DateTime,Integer>();
        SimpleDateFormat dateParser = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss");  //note: exif doesnt carry tz :(
        // TODO maybe?  we could also try just yyyy:MM:dd which sometimes exists.  sigh.  (etc x infinity)
        for (String key : matches.keySet()) {
            String val = matches.get(key);
            DateTime dt = null;
            try {
                dt = new DateTime(dateParser.parse(val));
            } catch (java.text.ParseException ex) { }
System.out.println("* MediaAssetMetadata.getDateTime(): " + key + "[" + val + "] -> " + dt);
            if (dt == null) continue;
            if (count.get(dt) == null) {
                count.put(dt, 1);
            } else {
                count.put(dt, count.get(dt) + 1);
            }
            //now we do a little quasi-weighting based on key (e.g. "original" is best, "digitized" is kinda good; otherwise sketchy
            if (key.toLowerCase().indexOf("original") > -1) count.put(dt, count.get(dt) + 2);
            if (key.toLowerCase().indexOf("digitized") > -1) count.put(dt, count.get(dt) + 1);
        }
System.out.println("* MediaAssetMetadata.getDateTime(): summary => " + count);
        if (count.size() < 1) return null;  //no such luck!
        DateTime bestest = null;
        int bestestCount = 0;
        for (DateTime dt : count.keySet()) {
            if (count.get(dt) > bestestCount) {
                bestest = dt;
                bestestCount = count.get(dt);
            }
        }
        return bestest;
    }


/*
GPS: {
GPS Satellites: "10",
GPS Latitude: "41.0° 31.0' 35.735999999990895"",
GPS Map Datum: "WGS 84",
GPS Date Stamp: "2014:08:11",
GPS Version ID: "2.300",
GPS Latitude Ref: "N",
GPS Altitude Ref: "Sea level",
GPS Longitude: "-69.0° 22.0' 45.62999999998169"",
GPS Time-Stamp: "17:49:26 UTC",
GPS Longitude Ref: "W",
GPS Altitude: "10 metres"
*/

    public Double getLatitude() {
        Double[] gps = getGPSArray();
        if ((gps == null) || (gps.length < 1)) return null;
        return gps[0];
    }
    public Double getLongitude() {
        Double[] gps = getGPSArray();
        if ((gps == null) || (gps.length < 2)) return null;
        return gps[1];
    }
    public Double getAltitude() {
        Double[] gps = getGPSArray();
        if ((gps == null) || (gps.length < 3)) return null;
        return gps[2];
    }
    private Double[] getGPSArray() {
        if ((getData() == null) || (getData().optJSONObject("exif") == null)) return null;
        Double[] gps = new Double[3];
        String[] keys = new String[]{"GPS Latitude", "GPS Longitude", "GPS Altitude"};
        JSONObject exif = getData().getJSONObject("exif");

        for (int i = 0 ; i < keys.length ; i++) {
            Double dbl = null;
            if (exif.optString(keys[i], null) != null) {
                if (i < 2) {  //digital degrees
                    dbl = Util.latlonDMStoDD(exif.getString(keys[i]));
                } else {
                    dbl = _parseAltitude(exif.getString(keys[i]));
                }
                if (dbl != null) {
                    gps[i] = dbl;
                    continue;
                }
            }
            if (exif.optJSONObject("GPS") == null) continue;  // try exif.GPS.foo
            if (exif.getJSONObject("GPS").optString(keys[i], null) != null) {
                if (i < 2) {  //digital degrees
                    gps[i] = Util.latlonDMStoDD(exif.getJSONObject("GPS").getString(keys[i]));
                } else {
                    gps[i] = _parseAltitude(exif.getJSONObject("GPS").getString(keys[i]));
                }
            }
        }
//System.out.println("gps array: " + gps[0] + ", " + gps[1] + ", " + gps[2]);
        return gps;
    }

    //we are going to standardize on meters cuz eff imperialism
    private Double _parseAltitude(String alt) {
//System.out.println("alt -> " + alt);
        if (alt == null) return null;
        String[] parts = alt.split(" +");
        if (parts.length < 1) return null;
        Double dbl = null;
        try {
            dbl = Double.valueOf(parts[0]);
        } catch (NumberFormatException nfe) {
            return null;
        }
        if (dbl == null) return null;  //would this ever happen???
        if ((parts.length == 1) || "".equals(parts[1]) || "m".equals(parts[1].toLowerCase().substring(0,1))) return dbl;  //already in metres (we are hoping, not miles?)
        if ("f".equals(parts[1].toLowerCase().substring(0,1))) return (dbl / 0.3048);  //goodbye, feet
        System.out.println("WARNING: MediaAssetMetadata._parseAltitude() unable to make sense of units, i think: " + alt);
        return dbl;  //meh, whaddya going to do?
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
