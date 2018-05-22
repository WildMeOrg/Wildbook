package org.ecocean;

import org.json.JSONObject;
import org.json.JSONArray;
import javax.servlet.http.HttpServletRequest;
import org.ecocean.servlet.ServletUtilities;
import java.util.Properties;
import java.util.List;
import java.util.ArrayList;
import org.joda.time.DateTime;
import org.ecocean.datacollection.*;
import org.ecocean.movement.*;
import org.ecocean.media.Feature;
import java.net.URL;

import java.io.IOException;
import java.net.MalformedURLException;
import java.security.NoSuchAlgorithmException;
import java.security.InvalidKeyException;
import java.io.UnsupportedEncodingException;

public class SpotterConserveIO {

    public static int PROJECT_ID_CI = 2;
    public static int PROJECT_ID_WA = 7;
    public static String apiUsername = null;
    public static String apiPassword = null;
    public static String apiUrlPrefix = null;

    public static void init(HttpServletRequest request) {
        init(ServletUtilities.getContext(request));
    }

    //should be called once -- sets up credentials for REST calls
    public static void init(String context) {
        Properties props = ShepherdProperties.getProperties("spotter-conserve-io.properties", "", context);
        if (props == null) throw new RuntimeException("no spotter-conserve-io.properties");
        apiUsername = props.getProperty("apiUsername");
        apiPassword = props.getProperty("apiPassword");
        apiUrlPrefix = props.getProperty("apiUrlPrefix");
    }

    public static boolean hasBeenInitialized() {
        return !(apiUrlPrefix == null);
    }
    public static void checkInit() throws IOException {
        if (!hasBeenInitialized()) throw new IOException("Looks like SpotterConserveIO.init(context) has not been called yet.");
    }


    //public SpotterConserveIO() {}

    /*
        we currently support two trip "types": Channel Island and WhaleAlert.  the prefixes ci and wa denote these flavors.
    */

    //////TODO this is an "Object" now cuz i dont have SurveyTrack here yet!  (get from colin)
    public static SurveyTrack ciToSurveyTrack(JSONObject jin) {
        if (jin.optJSONArray("sightings") != null) {
            SurveyTrack st = new SurveyTrack();
            List<Occurrence> occs = new ArrayList<Occurrence>();

            /* the way this apparently works is the "sightings" array is actually *two* sets of data:
               (1) a list of json objs in one format, then (2) a second list (same length) in another.
               thus, this JSONArray is 2N in length.  so we pass in the i and i+N json objs and hope for the best
            */
            JSONArray jocc = jin.getJSONArray("sightings");
            if (jocc.length() % 2 == 1) throw new RuntimeException("sightings JSONArray is odd length=" + jocc.length());
            int halfSize = (int) jocc.length() / 2;
            for (int i = 0 ; i < halfSize ; i++) {
                Occurrence occ = ciToOccurrence(jocc.optJSONObject(i), jocc.optJSONObject(i + halfSize));
                if (occ != null) occs.add(occ);
            }
            st.setOccurrences(occs);
        }

        if (jin.optJSONArray("weather") != null) {
            // maybe we make our own "weather datacollectionevent" !
            List<Observation> wths = new ArrayList<Observation>();
            JSONArray jw = jin.getJSONArray("weather");
            for (int i = 0 ; i < jw.length() ; i++) {
                //Observation wth = ciToWeather(jw.optJSONObject(i));
                //if (wth != null) wths.add(wth);
            }
            //.setWeather(wths);
        }
        return null;
    }


    public static Occurrence ciToOccurrence(JSONObject jin, JSONObject jin2) {
        Occurrence occ = new Occurrence();
        occ.setOccurrenceID(Util.generateUUID());
        occ.addComments(jin.optString("Comments", null));
        occ.setDateTimeCreated(jin.optString("create_date", null));
        occ.setBearing(findDouble(jin, "device_bearing"));
        occ.setDecimalLatitude(resolveLatLon(jin, "device_latitude", "latitude"));
        occ.setDecimalLongitude(resolveLatLon(jin, "device_longitude", "longitude"));

        //it actually appears the jin2 array contains WhaleAlert type sightings data, fwiw; but we only care about these 2:
        occ.addComments("<p class=\"import-source\">conserve.io source: <a href=\"" + jin2.optString("url") + "\"><b>" + jin2.optString("id") + "</b></a></p>");

        if (jin.optJSONArray("CINMS Behavior") != null) {
            List<Instant> bhvs = new ArrayList<Instant>();
            JSONArray jb = jin.getJSONArray("CINMS Behavior");
            for (int i = 0 ; i < jb.length() ; i++) {
                Instant bhv = ciToBehavior(jb.optJSONObject(i));
                if (bhv != null) bhvs.add(bhv);
            }
            occ.setBehaviors(bhvs);
        }

        if (jin.optJSONArray("CINMS Photo Log") != null) {
            ArrayList<Encounter> encs = new ArrayList<Encounter>();
            JSONArray je = jin.getJSONArray("CINMS Photo Log");
            for (int i = 0 ; i < je.length() ; i++) {
                Encounter enc = ciToEncounter(je.optJSONObject(i), jin, occ.getOccurrenceID());
                if (enc != null) encs.add(enc);
            }
            occ.setEncounters(encs);
        }
        return occ;
    }


/*
    {
        create_date: "2017-06-03 18:41:00+00:00",
        Card Number: 1,
        PID Code: "SBE",
        Image Number Start: 1583,      \__  use these to fill out some (new!) kinda placeholder Features (via Annotations)
        Image Number End: 1588,        /
        Animals Identified: 1
}
*/
    public static Encounter ciToEncounter(JSONObject jin, JSONObject occJson, String occId) {  //occJson we need for species (if not more)
        Encounter enc = new Encounter();
        enc.setCatalogNumber(Util.generateUUID());
        //enc.setGroupSize(findInteger(jin, "Animals Identified"));
        enc.setDynamicProperty("CINMS PID Code", jin.optString("PID Code", null));
        enc.setDynamicProperty("CINMS Card Number", jin.optString("Card Number", null));
        enc.setOccurrenceID(occId);

        String dc = jin.optString("create_date", null);
        if (dc != null) {
            enc.setDWCDateAdded(dc);
            DateTime dt = toDateTime(dc);
            if (dt != null) enc.setDWCDateAdded(dt.getMillis());  //sets the millis version on enc.  SIGH!!!!!!!!!!!
        }
        String tax[] = ciSpeciesSplit(occJson.optString("CINMS Species", null));
        if ((tax != null) && (tax.length > 1)) {
            enc.setGenus(tax[0]);
            enc.setSpecificEpithet(tax[1]);
        }

        //since we dont have proper images, but only references to them, we create annotations with special "placeholder" features
        int imageStart = jin.optInt("Image Number Start", -1);
        int imageEnd = jin.optInt("Image Number End", -1);
        if ((imageStart < 0) || (imageEnd < 0) || (imageEnd < imageStart)) {
            System.out.println("WARNING: " + enc + " had no valid image range [" + imageStart + " - " + imageEnd + "]");
        } else {
            ArrayList<Annotation> anns = new ArrayList<Annotation>();
            for (int i = imageStart ; i <= imageEnd ; i++) {
                JSONObject params = new JSONObject();
                params.put("PID Code", jin.optString("PID Code", null));
                params.put("Card Number", jin.optString("Card Number", null));
                params.put("Image Number", i);
                params.put("Image Start", imageStart);
                params.put("Image End", imageEnd);
                params.put("description", "Image number " + i + " (in " + imageStart + "-" + imageEnd + "); Card Number " + jin.optString("Card Number", "Unknown") + ", PID Code " + jin.optString("PID Code", "Unknown"));
                Feature ft = new Feature("org.ecocean.MediaAssetPlaceholder", params);
                Annotation ann = new Annotation(ciSpecies(occJson.optString("CINMS Species", null)), ft);
System.out.println(enc + ": just made " + ann);
                anns.add(ann);
            }
            enc.setAnnotations(anns);
        }

System.out.println("MADE " + enc);
        return enc;
    }

    public static Instant ciToBehavior(JSONObject jin) {
        String name = jin.optString("CINMS Behavior", null);
        DateTime dt = toDateTime(jin.optString("create_date", null));
        if ((name == null) || (dt == null)) return null;
        return new Instant(name, dt, null);
    }

    //someday, SpeciesTaxonomy!  sigh  TODO
    private static String ciSpecies(String species) {  //e.g. "Blue Whale" --> "Balaenoptera musculus" ... also: may be null
        return species;  //meh. for now.
    }
    private static String[] ciSpeciesSplit(String species) { //e.g. "Foo Bar" --> ["Foo", "Bar"]
        if (species == null) return null;
        return species.split(" +");
    }

    /*
       note: seems gpx has a trk made up of trkseg, which are made of trkpts...
       i suppose we really should have trkseg -> path, then have surveytrack made of multiple paths...
       TODO discuss with colin et al

       also: really should we be able to pass in entire "track" structure (find .gpx, save schema, etc)?  probably!
       something like: trackToSurveyTrackPaths(surveyTrack)

       NOTE!  trkpt can be JSONArray or JSONObject single pt!!  not sure if same is true of trkseg!!! TODO

        track: {
            gpx: {
                @xsi:schemaLocation: "http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd",
                @creator: "TrailBehind",
                @xmlns:xsi: "http://www.w3.org/2001/XMLSchema-instance",
                @xmlns: "http://www.topografix.com/GPX/1/1",
                @version: "1.1",
                trk: {
                    trkseg: [
                        {
                            trkpt: [{....}, ...., {....}]  // ARRAY
                        },
                        {
                            trkpt: {                       // SINGLE POINT
                                @lat: "34.321975",
                                @lon: "-119.691129",
                                time: "2017-06-01T20:57:49Z",
                                ele: "-3.248924"
                .......

    */

    public static Path trkToPath(JSONObject trk) {
        if (trk == null) return null;
        JSONArray segs = trk.optJSONArray("trkseg");  //see note above about single-element trkseg TODO
        if (segs == null) return null;
        ArrayList<PointLocation> pts = new ArrayList<PointLocation>();
        for (int i = 0 ; i < segs.length() ; i++) {
            JSONObject seg = segs.optJSONObject(i);
System.out.println(i + " seg = " + seg);
            if (seg == null) continue;
            JSONObject single = seg.optJSONObject("trkpt");
            JSONArray segPts = seg.optJSONArray("trkpt");
            if (single != null) {  //only one point in this segment
System.out.println("SINGLE " + single);
                PointLocation pl = trkptToPointLocation(single);
                if (pl != null) pts.add(pl);
            } else if (segPts != null) {
System.out.println("SEGPTS " + segPts);
                for (int j = 0 ; j < segPts.length() ; j++) {
                    PointLocation pl = trkptToPointLocation(segPts.optJSONObject(j));
                    if (pl != null) pts.add(pl);
                }
            }
        }
        return new Path(pts);
    }

    public static Path trackToPath(JSONObject track) {  //for now (see comments above) this is just convenience method
        if ((track == null) || (track.optJSONObject("gpx") == null) || (track.getJSONObject("gpx").optJSONObject("trk") == null)) return null;
        return trkToPath(track.getJSONObject("gpx").optJSONObject("trk"));
    }


    public static PointLocation trkptToPointLocation(JSONObject trkpt) {
        if (trkpt == null) return null;
        Long dt = null;  //long, so good luck with timezones?  :/
        String tt = trkpt.optString("time", null);
        try {
            if (tt != null) dt = new DateTime(tt).getMillis();  //tt should already be in right ISO format
        } catch (Exception ex) {
            System.out.println("WARNING: trkptToPointLocation() could not convert '" + tt + "' to DateTime");
        }
        Double lat = trkpt.optDouble("@lat", Util.INVALID_LAT_LON);
        Double lon = trkpt.optDouble("@lon", Util.INVALID_LAT_LON);
        if (!Util.isValidDecimalLatitude(lat)) lat = null;
        if (!Util.isValidDecimalLongitude(lon)) lon = null;
        Measurement elevMeas = null;
        double elev = trkpt.optDouble("ele", Double.MAX_VALUE);
        if (elev < Double.MAX_VALUE) elevMeas = new Measurement(null, "elevation", elev, "m", null);  //FIXME Measurement() seems wrong here
System.out.println(lat + "," + lon + " [" + dt + "]:" + elev);
System.out.println("elevMeas -> " + elevMeas);
        PointLocation ploc = new PointLocation(lat, lon, dt, elevMeas);
System.out.println(ploc);
        return ploc;
        //return new PointLocation(lat, lon, dt, elevMeas);
    }

    private static Double resolveLatLon(JSONObject jin, String devKey, String userKey) {
        Double devVal = findDouble(jin, devKey);
        Double userVal = findDouble(jin, userKey);
        //how we decide is a bit sketchy for now.  seems like even when the "same" the values vary by precision. :(
        //    so..... i will just favor the non-human for now!  haha
        if (devVal != null) return devVal;
        return userVal;
    }


    private static Double findDouble(JSONObject jin, String key) {
        if ((jin == null) || (jin.optDouble(key, 99999.0) == 99999.0)) return null;
        return jin.getDouble(key);
    }

    private static Integer findInteger(JSONObject jin, String key) {
        if ((jin == null) || (jin.optInt(key, 99999) == 99999)) return null;
        return jin.getInt(key);
    }
    
    //2017-06-03 21:31:16+00:00 assumed input.... really just changes space to T
    public static DateTime toDateTime(String dt) {
        if (dt == null) return null;
        return new DateTime(dt.replaceAll(" ", "T"));
    }

    //note: since is seconds (int), NOT millis (long) !!!
    public static JSONObject ciGetTripListSince(int since) throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException, InvalidKeyException {
        return apiGet("/project/" + PROJECT_ID_CI + "/trip_data/" + since + "/0");
    }
    public static JSONObject waGetTripListSince(int since) throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException, InvalidKeyException {
        return apiGet("/project/" + PROJECT_ID_WA + "/trip_data/" + since + "/0");
    }

    //CI & WA trips use same call
    public static JSONObject getTrip(int tripId) throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException, InvalidKeyException {
        return apiGet("/trip/" + tripId + "/data");
    }

    //init() must be called once before this
    //  note, apiUrl is a relative url (String), like "/trip/NNNN/data"
    public static JSONObject apiGet(String apiUrl) throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException, InvalidKeyException {
        checkInit();
        URL getUrl = new URL(apiUrlPrefix + apiUrl);
        String res = RestClient.get(getUrl, apiUsername, apiPassword);
        if (res == null) return null;
        return new JSONObject(res);
    }

}

