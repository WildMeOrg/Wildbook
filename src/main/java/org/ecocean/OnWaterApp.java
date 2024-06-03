package org.ecocean;

import org.json.JSONObject;
import org.json.JSONArray;
import javax.servlet.http.HttpServletRequest;
import org.ecocean.servlet.ServletUtilities;
import java.util.Properties;
import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Arrays;
import java.util.Random;
import org.joda.time.DateTime;
import org.ecocean.datacollection.*;
import org.ecocean.movement.*;
import org.ecocean.media.Feature;
import org.ecocean.media.MediaAsset;
import org.ecocean.media.MediaAssetFactory;
import org.ecocean.media.AssetStore;
import org.ecocean.media.LocalAssetStore;
import java.net.URL;

import java.io.IOException;
import java.net.MalformedURLException;
import java.security.NoSuchAlgorithmException;
import java.security.InvalidKeyException;
import java.io.UnsupportedEncodingException;
import org.apache.shiro.crypto.hash.Sha256Hash;

public class OnWaterApp {

    private static final String SYSTEMVALUE_KEY_LASTSYNC = "OnWaterApp_lastSync";
    private static final String OBS_WEATHER_NAME = "weather";
    private static final String PROP_USERID = "onWaterUserId";
    private static Double WATER_DISTANCE_RADIUS = 300.0D;
    public static String apiUsername = null;
    public static String apiPassword = null;
    public static String apiUrlPrefix = null;
    public static Properties props = null;  //will be set by init()
    public static LocalAssetStore assetStore;

    public static void init(HttpServletRequest request) {
        init(ServletUtilities.getContext(request));
    }

    //should be called once -- sets up credentials for REST calls
    public static void init(String context) {
        if (props == null) props = ShepherdProperties.getProperties("on-water-app.properties", "", context);
        if (props == null) throw new RuntimeException("no on-water-app.properties");
        apiUsername = props.getProperty("apiUsername");
        apiPassword = props.getProperty("apiPassword");
        apiUrlPrefix = props.getProperty("apiUrlPrefix");
    }

    public static boolean hasBeenInitialized() {
        return !(apiUrlPrefix == null);
    }
    public static void checkInit() throws IOException {
        if (!hasBeenInitialized()) throw new IOException("Looks like OnWaterApp.init(context) has not been called yet.");
    }




/*
              "trip" : {
                 "startTime" : "2024-03-12T16:05:22.686Z",
                 "endTime" : "2024-03-13T05:31:29.587Z",
                 "id" : "b5494275-f48a-4cc3-a20d-1d2acc0295a9",
                 "distance" : "0.6"
              },
*/
    public static Survey toSurvey(JSONObject jin, Encounter enc, Shepherd myShepherd) {
        if (jin == null) return null;
        DateTime startDate = toDateTime(jin.optString("startTime", null));
        DateTime endDate = toDateTime(jin.optString("endTime", null));
        String id = jin.optString("id", Util.generateUUID());
        Survey survey = new Survey(startDate);
        survey.setID(id);

        double dist = jin.optDouble("distance", -1.0);
        if (dist > 0.0) survey.setEffort(new Measurement(null, "distance", dist, "miles", null));

        if (endDate != null) survey.setEndTimeMilli(endDate.getMillis());

        //survey.setProjectType()
        //survey.addComments()
        //survey.setProjectName()
        //survey.setOrganization()

        Occurrence occ = new Occurrence();
        occ.setDWCDateLastModified();
        occ.setOccurrenceID(Util.generateUUID());
        //occ.setDateTimeCreated()
        //occ.setDecimalLatitude()
        //occ.setDecimalLongitude()
        occ.setSource("OnWaterApp trip id " + id);
        occ.addEncounter(enc);
        ArrayList<Occurrence> occs = new ArrayList<Occurrence>();
        occs.add(occ);

        SurveyTrack st = new SurveyTrack();
        st.setOccurrences(occs);
        // st.path ?
        survey.addSurveyTrack(st);

        return survey;
    }

    public static SurveyTrack ciToSurveyTrack(JSONObject jin, Shepherd myShepherd) {
        SurveyTrack st = new SurveyTrack();

        if (jin.optJSONArray("sightings") != null) {
            ArrayList<Occurrence> occs = new ArrayList<Occurrence>();

            /* the way this apparently works is the "sightings" array is actually *two* sets of data:
               (1) a list of json objs in one format, then (2) a second list (same length) in another.
               thus, this JSONArray is 2N in length.  so we pass in the i and i+N json objs and hope for the best
            */
            JSONArray jocc = jin.getJSONArray("sightings");
            if (jocc.length() % 2 == 1) throw new RuntimeException("sightings JSONArray is odd length=" + jocc.length());
            int halfSize = (int) jocc.length() / 2;
            for (int i = 0 ; i < halfSize ; i++) {
                Occurrence occ = ciToOccurrence(jocc.optJSONObject(i), jocc.optJSONObject(i + halfSize), jin, myShepherd);
                if (occ != null) occs.add(occ);
            }
            st.setOccurrences(occs);
        }

        Path path = trackToPath(jin.optJSONObject("track"));
        if (path != null) st.setPath(path);

        return st;
    }


    public static Occurrence ciToOccurrence(JSONObject jin, JSONObject jin2, JSONObject allJson, Shepherd myShepherd) {
        int tripId = allJson.optInt("_tripId", 0);
        Occurrence occ = new Occurrence();
        occ.setDWCDateLastModified();
        occ.setOccurrenceID(Util.generateUUID());
        occ.addComments(jin.optString("Comments", null));
        occ.setDateTimeCreated(jin.optString("create_date", null));
        occ.setBearing(findDouble(jin, "device_bearing"));
        occ.setDecimalLatitude(resolveLatLon(jin, "device_latitude", "latitude"));
        occ.setDecimalLongitude(resolveLatLon(jin, "device_longitude", "longitude"));
        int numCalves = jin.optInt("Calves Sighted", 0);
        int numTotal = jin.optInt("Total Sighted (Including Calves)", 0);
        int numAdults = numTotal - numCalves;
        occ.setNumCalves(numCalves);
        occ.setNumAdults(numAdults);
        occ.setBestGroupSizeEstimate(new Double(numTotal));
        occ.setSightingPlatform(allJson.optString("CINMS Vessel", null));
        occ.setSource("OnWaterApp:ci:" + tripId);
/*
        String taxString = jin.optString("CINMS Species", null);
        if (taxString != null) occ.addSpecies(taxString, myShepherd);
*/
        Taxonomy tax = toTaxonomy(jin.optString("species", null), myShepherd);
        if (tax != null) occ.addTaxonomy(tax);

/* also notable?
Other Vessels On Scene: 0,
Other Species: "",
Certainty: "Certain",
Distance Category: "B"
*/

        //it actually appears the jin2 array contains WhaleAlert type sightings data, fwiw; but we only care about these 2:
        occ.addComments("<p class=\"import-source\">conserve.io source: <a href=\"" + jin2.optString("url") + "\"><b>" + jin2.optString("id") + "</b></a></p>");

        if (jin.optJSONArray("CINMS Behavior") != null) {
            List<Instant> bhvs = new ArrayList<Instant>();
            JSONArray jb = jin.getJSONArray("CINMS Behavior");
            for (int i = 0 ; i < jb.length() ; i++) {
                //Instant bhv = ciToBehavior(jb.optJSONObject(i));
                //if (bhv != null) bhvs.add(bhv);
            }
            occ.setBehaviors(bhvs);
        }

        if (jin.optJSONArray("CINMS Photo Log") != null) {
            ArrayList<Encounter> encs = new ArrayList<Encounter>();
            JSONArray je = jin.getJSONArray("CINMS Photo Log");
            for (int i = 0 ; i < je.length() ; i++) {
                //Encounter enc = ciToEncounter(je.optJSONObject(i), jin, occ.getOccurrenceID(), allJson, myShepherd);
                //if (enc != null) encs.add(enc);
            }
            occ.setEncounters(encs);
        }
        //occ.setSubmitter(ciToUser(allJson, myShepherd));
        //List<User> vols = ciGetVolunteerUsers(allJson, myShepherd);
        //occ.setInformOthers(vols);
        return occ;
    }

    public static Taxonomy toTaxonomy(String taxString, Shepherd myShepherd) {
        if (taxString == null) return null;
        return findTaxonomy(myShepherd, taxString);  // may be null
/*
        if (tax != null) return tax;
        //we make a new one, but assume it is non-specific (cuz what do we know?)
        tax = new Taxonomy(taxString);
        tax.setNonSpecific(true);
        myShepherd.storeNewTaxonomy(tax);
        return tax;
*/
    }

    public static Encounter toEncounter(JSONObject jin, Shepherd myShepherd) throws IOException {
        return toEncounter(jin, myShepherd, false);
    }

    public static Encounter toEncounter(JSONObject jin, Shepherd myShepherd, boolean createDuplicate) throws IOException {
        Encounter enc = new Encounter();
        String id = jin.optString("id", null);
        if (id == null) {
            id = Util.generateUUID();
            enc.setDynamicProperty("OnWaterApp photo id", id);
        } else {
            enc.setDynamicProperty("OnWaterApp photo id", id);
            if (myShepherd.isEncounter(id)) {
                if (createDuplicate) {
                    System.out.println("OnWaterApp.toEncounter(): attempt to create duplicate Encounter ID " + id + "; generating random");
                    id = Util.generateUUID();
                } else {
                    System.out.println("OnWaterApp.toEncounter(): will not create duplicate Encounter ID " + id);
                    throw new IOException("Encounter with id=" + id + " already exists; will not created duplicate");
                }
            }
        }
        enc.setCatalogNumber(id);

        String dt = jin.optString("dateTime", null);
        if (dt != null) {
            enc.setDWCDateAdded(dt);
            DateTime dtObj = toDateTime(dt);
            if (dtObj != null) {
                enc.setDWCDateAdded(dtObj.getMillis());
                enc.setDateInMilliseconds(dtObj.getMillis());
            }
        }
        Taxonomy tax = toTaxonomy(jin.optString("species", null), myShepherd);
        enc.setTaxonomy(tax);

        MediaAsset ma = createMediaAssetFromUrl(jin.optString("uri", null), myShepherd);
        if (ma == null) {
            System.out.println("FAILED MediaAsset");
        } else {
            Annotation ann = new Annotation(enc.getTaxonomyString(), ma);
            enc.addAnnotation(ann);
        }

        enc.setDecimalLatitude(resolveLatLon(jin, "latitude", "__FAIL__"));
        enc.setDecimalLongitude(resolveLatLon(jin, "longitude", "__FAIL__"));

        enc.setFlowAmount(findDouble(jin, "flowAmount"));
        enc.setFlowUnit(jin.optString("flowUnit", null));

        enc.setTemperature(findDouble(jin, "temperature"));
        enc.setTemperatureUnit(jin.optString("temperatureUnit", null));

        enc.setGearType(jin.optString("gearType", null));

        enc.setSize(findDouble(jin, "size"));

        enc.setWaterClarity(jin.optString("waterClarity", null));
        enc.setWaterClarityUri(jin.optString("waterClarityUri", null));

        enc.setMeasuredLength(findDouble(jin, "measuredLength"));

        enc.setEstimatedLengthBody(findDouble(jin, "estimatedLengthBody"));
        enc.setEstimatedLengthEye(findDouble(jin, "estimatedLengthEye"));

        enc.setAppUserId(jin.optString("user", null));

        Survey survey = toSurvey(jin.optJSONObject("trip"), enc, myShepherd);

        String notes = jin.optString("notes", null);
        if (notes != null) enc.addComments(notes);

System.out.println("MADE " + enc);
        return enc;
    }



    public static MediaAsset createMediaAssetFromUrl(String urlStr, Shepherd myShepherd) {
        LocalAssetStore store = (LocalAssetStore)AssetStore.getDefault(myShepherd);
        if (urlStr == null) return null;
        URL url = null;
        try {
            url = new URL(urlStr);
        } catch (java.net.MalformedURLException ex) {
            ex.printStackTrace();
            return null;
        }
        MediaAsset ma = store.create(url);
        if (ma == null) return null;
        MediaAssetFactory.save(ma, myShepherd);
        ma.updateStandardChildren(myShepherd);
        return ma;
    }

    public static Taxonomy findTaxonomy(Shepherd myShepherd, String tstring) {
        if (tstring == null) return null;
        List<Taxonomy> found = Taxonomy.findMatch(myShepherd, "(?i)" + tstring);  //exact match (but case-insensitive)
        if (found.size() > 0) return found.get(0);
        // ok lets try commonConfiguration
        List<String> configuredSpecies = CommonConfiguration.getIndexedPropertyValues("genusSpecies", myShepherd.getContext());
        List<String> configuredCommonNames = CommonConfiguration.getIndexedPropertyValues("commonName", myShepherd.getContext());
        for (String sp : configuredSpecies) {
            if (sp.toLowerCase().equals(tstring.toLowerCase())) return new Taxonomy(sp);
        }
        for (int i = 0 ; i < configuredCommonNames.size() ; i++) {
            String cn = configuredCommonNames.get(i);
            if (cn.toLowerCase().equals(tstring.toLowerCase()) && (i < configuredSpecies.size())) return new Taxonomy(configuredSpecies.get(i));
        }
        return null;
    }

    /*
       note: seems gpx has a trk made up of trkseg, which are made of trkpts...
       i suppose we really should have trkseg -> path, then have surveytrack made of multiple paths...
       however, *for now* we just combine all leaf points into one Path
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

        NOTE2!  also trkseg can be a JSONArray or a (singleton) JSONObject!!  grrffff...
        NOTE3:  JP asks "what if it is empty?" ... ARGH. haha

    */

    public static Path trkToPath(JSONObject trk) {
        if (trk == null) return null;
        JSONArray segs = trk.optJSONArray("trkseg");
        //we may have single-element here! (see note above), so lets force into a JSONArray
        JSONObject segSingle = trk.optJSONObject("trkseg");
        if ((segs == null) && (segSingle != null)) {
            segs = new JSONArray();
            segs.put(segSingle);
        }
        if (segs == null) return null;
        ArrayList<PointLocation> pts = new ArrayList<PointLocation>();
        for (int i = 0 ; i < segs.length() ; i++) {
            JSONObject seg = segs.optJSONObject(i);
//System.out.println(i + " seg = " + seg);
            if (seg == null) continue;
            JSONObject single = seg.optJSONObject("trkpt");
            JSONArray segPts = seg.optJSONArray("trkpt");
            if (single != null) {  //only one point in this segment
//System.out.println("SINGLE " + single);
                PointLocation pl = trkptToPointLocation(single);
                if (pl != null) pts.add(pl);
            } else if (segPts != null) {
//System.out.println("SEGPTS " + segPts);
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
//System.out.println(lat + "," + lon + " [" + dt + "]:" + elev);
//System.out.println("elevMeas -> " + elevMeas);
        PointLocation ploc = new PointLocation(lat, lon, dt, elevMeas);
//System.out.println(ploc);
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


//// API-related calls (both flavors)

    public static JSONArray getList(String context) throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException, InvalidKeyException {
        int since = getLastSync(context);
        JSONArray rtn = getListSince(since);
        int last = setLastSync(context);
        return rtn;
    }

    //note: since is seconds (int), NOT millis (long) !!!
    public static JSONArray getListSince(int since) throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException, InvalidKeyException {
System.out.println(">>> getListSince grabbing since " + new DateTime(new Long(since) * 1000));
        return apiGetArray("/trout-spotter/photos");
    }

/*
    //CI & WA trips use same call
    public static JSONObject getTrip(int tripId) throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException, InvalidKeyException {
        JSONObject data = apiGet("/trip/" + tripId + "/data");
        if (data == null) return null;
        data.put("_tripId", tripId);
        data.put("_tripFlavor", tripFlavor(data));
        return data;
    }
*/

    //init() must be called once before this
    //  note, apiUrl is a relative url (String), like "/foo/bar"
    public static JSONObject apiGet(String apiUrl) throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException, InvalidKeyException {
        checkInit();
        URL getUrl = new URL(apiUrlPrefix + apiUrl);
        String res = RestClient.get(getUrl, apiUsername, apiPassword);
        if (res == null) return null;
        return new JSONObject(res);
    }
    public static JSONArray apiGetArray(String apiUrl) throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException, InvalidKeyException {
        checkInit();
        URL getUrl = new URL(apiUrlPrefix + apiUrl);
        String res = RestClient.get(getUrl, apiUsername, apiPassword);
        if (res == null) return null;
        return new JSONArray(res);
    }


    //note: these are in seconds (not milli) cuz that is what spotter.io uses

    public static int getLastSync(String context) {
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.beginDBTransaction();
        Integer last = SystemValue.getInteger(myShepherd, SYSTEMVALUE_KEY_LASTSYNC);
        myShepherd.rollbackDBTransaction();
        if (last != null) return last;
        //if we dont have a value, we kind of grab one in the past so we arent getting everything forever!
        Long sec = (long)(System.currentTimeMillis() / 1000) - (7 * 24 * 60 * 60);
        return sec.intValue();
    }
    public static int setLastSync(String context) {  //the "now" flavor
        Long sec = (long)(System.currentTimeMillis() / 1000);
        return setLastSync(context, sec.intValue());
    }
    public static int setLastSync(String context, int time) {
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.beginDBTransaction();
        SystemValue.set(myShepherd, SYSTEMVALUE_KEY_LASTSYNC, time);
        myShepherd.commitDBTransaction();
        return time;
    }


/*
    public static User ciGetSubmitterUser(Shepherd myShepherd) {
        String uid = props.getProperty(PROP_USERID_CI);
        if (uid == null) return null;
        return myShepherd.getUserByUUID(uid);
    }

    // fullName is a (cleaned!) list of user fullnames
    //  users *will be created* if they cannot be found within the main or volunteer organizations
    public static List<User> ciGetVolunteerUsers(Shepherd myShepherd, List<String> fullNames) {
        if (fullNames == null) return null;
        String orgId = props.getProperty(PROP_ORGID_CI);
        String vorgId = props.getProperty(PROP_ORGID_CI_VOLUNTEER);
        Organization org = Organization.load(orgId, myShepherd);
        Organization vorg = Organization.load(vorgId, myShepherd);
        if ((org == null) || (vorg == null)) throw new RuntimeException("You must have valid " + PROP_ORGID_CI + " and " + PROP_ORGID_CI_VOLUNTEER + " set in SpotterConserverIO properties file");
        List<User> vols = new ArrayList<User>();
        for (String fn : fullNames) {
            if (!Util.stringExists(fn)) continue;
            User u = null;
            if ((org != null) && (org.getMembers() != null)) {
                for (User mem : org.getMembers()) {
                    if (fn.equals(mem.getFullName())) u = mem;
                }
            }
            if ((u == null) && (vorg != null) && (vorg.getMembers() != null)) {
                for (User mem : vorg.getMembers()) {
                    if (fn.equals(mem.getFullName())) u = mem;
                }
            }
            if (u == null) {
                u = new User(Util.generateUUID());
                u.setFullName(fn);
                u.setNotes("auto-created by OnWaterApp data import");
                myShepherd.getPM().makePersistent(u);
                vorg.addMember(u);
            }
            vols.add(u);
        }
        myShepherd.getPM().makePersistent(vorg);
        return vols;
    }
*/

    //these do a series of sanity checks and sets .state based on if data "passes"
    //  returning null means all is ok, otherwise it returns a string with the reason

    public static String checkIntegrity(SurveyTrack st, Shepherd myShepherd) {  //this will just recurse thru occs
        if (st == null) return null;  //i guess???
        String reason = "";

        boolean overrideOccurrences = !reason.equals("");  //means fail everything below due to our badness
        String c = checkIntegrity(st.getOccurrences(), overrideOccurrences, myShepherd);
        if (Util.stringExists(c)) reason += c;
        c = checkIntegrity(st.getPath(), myShepherd);
        if (c != null) reason += c;
        if (reason.equals("")) return null;
        System.out.println("WARNING: checkIntegrity() on " + st + " failed due to: " + reason);
        return reason;
    }

    public static String checkIntegrity(Path path, Shepherd myShepherd) {
        if (path == null) return null;
        if (Util.collectionIsEmptyOrNull(path.getPointLocations())) return "empty .path on SurveyTrack; ";
        String reason = "";
        Random rnd = new Random();
        //here we test a random subset of points for validity.  then decide if the path is good????   #magick
        int invalidPts = 0;
        for (PointLocation pt : path.getPointLocations()) {
            if (rnd.nextDouble() > 0.05D) continue;  //we skip 95% of points -- cuz there are usually many!
            String c = checkIntegrity(pt.getLatitude(), pt.getLongitude(), myShepherd);
            if (c != null) invalidPts++;
            //NOTE: also could check .getDateTimeMilli();
        }
        if (invalidPts > 5) reason += "found " + invalidPts + " invalid lat/lon points in SurveyTrack path; ";
        if (reason.equals("")) return null;
        return reason;
    }

    public static String checkIntegrity(List<Occurrence> occs, boolean override, Shepherd myShepherd) {
        if (Util.collectionIsEmptyOrNull(occs)) return null;
        String reason = "";
        for (Occurrence occ : occs) {
            String c = checkIntegrity(occ, override, myShepherd);
            if (Util.stringExists(c)) reason += "occ " + occ.getOccurrenceID() + " failed integrity: [" + c + "]";
        }
        if (!override && reason.equals("")) return null;
        return reason; //nothing else to really do to the set of occs here (no comments etc)
    }

    public static String checkIntegrity(Occurrence occ, boolean override, Shepherd myShepherd) {
        if (occ == null) return null;
        String reason = "";
        Long ms = occ.getDateTimeLong();
        if (occ.getDateTimeCreated() != null) {
            DateTime dt = toDateTime(occ.getDateTimeCreated());
            ms = dt.getMillis();
        }
        String c = checkIntegrity(ms, myShepherd);
        if (c != null) reason += c;
        //the HashSet makes it unique
        if (!Util.collectionIsEmptyOrNull(occ.getTaxonomies())) for (Taxonomy tx : new HashSet<Taxonomy>(occ.getTaxonomies())) {
            c = checkIntegrity(tx, myShepherd);
            if (c != null) reason += c;
        }
        c = checkIntegrity(occ.getDecimalLatitude(), occ.getDecimalLongitude(), myShepherd);
        if (c != null) reason += c;

        boolean overrideEncounter = override || !reason.equals("");  //means fail the encounter due to our badness
        if (occ.getEncounters() != null) for (Encounter enc : occ.getEncounters()) {
            c = checkIntegrity(enc, overrideEncounter, myShepherd);
            if (Util.stringExists(c)) reason += "enc[" + c + "]; ";  //null *or* "" can mean enc passed in the override case!
        }

        if (!override && reason.equals("")) return null;
        occ.addComments("<p class=\"spotter-review\">pending approval: <i>" + reason + (override ? "overridden (likely bad integrity in sibling Occurrences or SurveyTrack); " : "") +  "</i></p>");
        System.out.println("WARNING: checkIntegrity() on " + occ + " failed due to: " + reason + " override=" + override);
        return reason;
    }

    public static String checkIntegrity(Encounter enc, boolean override, Shepherd myShepherd) {
        if (enc == null) return null;
        String reason = "";
        String c = checkIntegrity(enc.getDateInMilliseconds(), myShepherd);
        if (c != null) reason += c;
        c = checkIntegrity(enc.getDecimalLatitudeAsDouble(), enc.getDecimalLongitudeAsDouble(), myShepherd);
        if (c != null) reason += c;
        if (enc.getTaxonomyString() != null) {
            c = checkIntegrity(myShepherd.getTaxonomy(enc.getTaxonomyString()), myShepherd);
            if (c != null) reason += c;
        }

        if (!override && reason.equals("")) {
            enc.setState("approved");
            return null;
        }
        enc.addComments("<p class=\"spotter-review\">pending approval: <i>" + reason + (override ? "overridden (likely bad integrity in Occurrence); " : "") +  "</i></p>");
        //enc.setState(ENCOUNTER_STATE_REVIEW);
        System.out.println("WARNING: checkIntegrity() on " + enc + " failed due to: " + reason + " override=" + override);
        return reason;
    }

    public static String checkIntegrity(Long ms, Shepherd myShepherd) {
        String reason = "";
        if (ms == null) {
            reason += "no date/time set; ";
        } else if (ms > (System.currentTimeMillis() + 3L * 24L * 60L * 60L * 1000L)) {
            reason += "date/time too far in future; ";
        } else if (ms < (System.currentTimeMillis() - 3L * 24L * 60L * 60L * 1000L)) {
            reason += "date/time too old; ";
        }
        if (reason.equals("")) return null;
        return reason;
    }

    public static String checkIntegrity(Double lat, Double lon, Shepherd myShepherd) {
        String reason = "";
        if (!Util.isValidDecimalLatitude(lat)) reason += "invalid latitude; ";
        if (!Util.isValidDecimalLongitude(lon)) reason += "invalid longitude; ";
        if (Util.isValidDecimalLatitude(lat) && Util.isValidDecimalLongitude(lon) && !Util.nearWater(myShepherd, lat, lon, WATER_DISTANCE_RADIUS)) reason += "lat/lon is not near water; ";
        if (reason.equals("")) return null;
        return reason;
    }

    public static String checkIntegrity(Taxonomy tx, Shepherd myShepherd) {
        if (tx == null) return "invalid (null) Taxonomy";
        if (tx.getNonSpecific()) return "Taxonomy is non-specific: " + tx.getScientificName();
        return null;  //specific = good
    }


    public static String hashFromPhone(String phone) {
        if (phone == null) return null;
        phone = phone.replaceAll("\\D", "");
        if (phone.equals("")) return null;
        return phone.substring(0,1) + (new Sha256Hash(phone).toHex());
    }
    public static String hashFromEmail(String email) {
        if (email == null) return null;
        email = email.replaceAll("\\s", "");
        if (email.equals("")) return null;
        return email.substring(0,1) + (new Sha256Hash(email).toHex());
    }
}

