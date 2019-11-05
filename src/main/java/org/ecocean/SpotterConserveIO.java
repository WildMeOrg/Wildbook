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
import org.ecocean.media.URLAssetStore;
import java.net.URL;

import java.io.IOException;
import java.net.MalformedURLException;
import java.security.NoSuchAlgorithmException;
import java.security.InvalidKeyException;
import java.io.UnsupportedEncodingException;
import org.apache.shiro.crypto.hash.Sha256Hash;

// general note: "CI" refers to Channel Island (project) and "WA" to WhaleAlert

public class SpotterConserveIO {

    private static final int PROJECT_ID_CI = 2;
    private static final int PROJECT_ID_WA = 7;
    private static final int PROJECT_ID_CW = 4;
    private static final String SYSTEMVALUE_KEY_LASTSYNC_CI = "SpotterConserveIO_lastSync_CI";
    private static final String SYSTEMVALUE_KEY_LASTSYNC_WA = "SpotterConserveIO_lastSync_WA";
    private static final String SYSTEMVALUE_KEY_LASTSYNC_CW = "SpotterConserveIO_lastSync_CW";
    private static final String OBS_WEATHER_NAME = "weather";
    private static final String PROP_ORGID_CI_VOLUNTEER = "channelIslandsVolunteerOrgId";
    private static final String PROP_ORGID_CI = "channelIslandsOrgId";
    private static final String PROP_USERID_CI = "channelIslandsUserId";
    private static final String PROP_ORGID_NEWUSER = "newUserOrgId";
    private static final String LOCATIONID_CI = "Santa Barbara Channel";
    private static final String LOCATION_COUNTRY_USA = "United States of America";
    public static final String ENCOUNTER_STATE_REVIEW = "spotter_review";
    private static Double WATER_DISTANCE_RADIUS = 300.0D;
    public static String apiUsername = null;
    public static String apiPassword = null;
    public static String apiUrlPrefix = null;
    public static Properties props = null;  //will be set by init()

    public static void init(HttpServletRequest request) {
        init(ServletUtilities.getContext(request));
    }

    //should be called once -- sets up credentials for REST calls
    public static void init(String context) {
        if (props == null) props = ShepherdProperties.getProperties("spotter-conserve-io.properties", "", context);
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



/******************************************
    Channel Island flavor
******************************************/


    //this is the "starting point" for JSON from the API
    public static Survey ciToSurvey(JSONObject jin, Shepherd myShepherd) {
        if (jin == null) return null;
        DateTime startDate = toDateTime(jin.optString("start_date", null));
        DateTime endDate = toDateTime(jin.optString("end_date", null));
        DateTime createDate = toDateTime(jin.optString("create_date", null));
        Survey survey = new Survey(startDate);

        //if (startDate != null) survey.setStartTimeMilli(startDate.getMillis());
        if (endDate != null) survey.setEndTimeMilli(endDate.getMillis());
        if (createDate != null) survey.addComments("<p>Created on source: <b>" + createDate.toString() + "</b></p>");

        survey.setProjectType("Channel Island Spotter conserve.IO");
        survey.addComments("<p>Observer Names: <b>" + jin.optString("Observer Names", "<i>none provided</i>") + "</b>, Channel Island trip ID: <b>" + jin.optInt("_tripId", 0) + "</b></p>");
        survey.setProjectName("Channel Island");
        survey.setOrganization("conserve.io");

        //there will be only one SurveyTrack pulled from this data, fwiw
        SurveyTrack st = ciToSurveyTrack(jin, myShepherd);
        String integ = checkIntegrity(st, myShepherd);
        survey.addSurveyTrack(st);
        if (integ != null) survey.addComments("<p>Note: SurveyTrack failed integrity check</p>");

///TODO do we .setEffort based on survey track lengths or what???

        if (jin.optJSONArray("CINMS Weather") != null) {
            ArrayList<Observation> wths = new ArrayList<Observation>();
            JSONArray jw = jin.getJSONArray("CINMS Weather");
            for (int i = 0 ; i < jw.length() ; i++) {
                Observation wth = ciToWeather(jw.optJSONObject(i), survey);
                if (wth != null) wths.add(wth);
            }
            survey.addObservationArrayList(wths);
        }
        return survey;
    }

    public static Observation ciToWeather(JSONObject wj, Survey surv) {
        if (wj == null) return null;
        Observation obs = new Observation(OBS_WEATHER_NAME, wj.toString(), surv, surv.getID());
        DateTime dt = toDateTime(wj.optString("create_date", null));
        obs.setDateAddedMilli((dt == null) ? null : dt.getMillis());
        obs.setDateLastModifiedMilli();
        return obs;
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
        occ.setSource("SpotterConserveIO:ci:" + tripId);
/*
        String taxString = jin.optString("CINMS Species", null);
        if (taxString != null) occ.addSpecies(taxString, myShepherd);
*/
        Taxonomy tax = ciToTaxonomy(jin.optString("CINMS Species", null), myShepherd);
System.out.println("ciToTaxonomy => " + tax);
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
                Instant bhv = ciToBehavior(jb.optJSONObject(i));
                if (bhv != null) bhvs.add(bhv);
            }
            occ.setBehaviors(bhvs);
        }

        if (jin.optJSONArray("CINMS Photo Log") != null) {
            ArrayList<Encounter> encs = new ArrayList<Encounter>();
            JSONArray je = jin.getJSONArray("CINMS Photo Log");
            for (int i = 0 ; i < je.length() ; i++) {
                Encounter enc = ciToEncounter(je.optJSONObject(i), jin, occ.getOccurrenceID(), allJson, myShepherd);
                if (enc != null) encs.add(enc);
            }
            occ.setEncounters(encs);
        }
        occ.setSubmitter(ciToUser(allJson, myShepherd));
        List<User> vols = ciGetVolunteerUsers(allJson, myShepherd);
        occ.setInformOthers(vols);
        return occ;
    }

    public static Taxonomy ciToTaxonomy(String taxString, Shepherd myShepherd) {
        if (taxString == null) return null;
        Taxonomy tax = findTaxonomy(myShepherd, taxString);
        if (tax != null) return tax;
        //we make a new one, but assume it is non-specific (cuz what do we know?)
        tax = new Taxonomy(taxString);
        tax.setNonSpecific(true);
        myShepherd.storeNewTaxonomy(tax);
        return tax;
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
    public static Encounter ciToEncounter(JSONObject jin, JSONObject occJson, String occId, JSONObject allJson, Shepherd myShepherd) {  //occJson we need for species (if not more)
        Encounter enc = new Encounter();
        enc.setCatalogNumber(Util.generateUUID());
        //enc.setGroupSize(findInteger(jin, "Animals Identified"));
        enc.setDynamicProperty("CINMS PID Code", jin.optString("PID Code", null));
        enc.setDynamicProperty("CINMS Card Number", jin.optString("Card Number", null));
        enc.setOccurrenceID(occId);
        //lat/lon come from occurrence
        enc.setDecimalLatitude(resolveLatLon(occJson, "device_latitude", "latitude"));
        enc.setDecimalLongitude(resolveLatLon(occJson, "device_longitude", "longitude"));
        enc.setLocationID(LOCATIONID_CI);
        enc.setCountry(LOCATION_COUNTRY_USA);
        User sub = ciToUser(allJson, myShepherd);
        if ((sub != null) && (sub.getUsername() != null)) enc.setSubmitterID(sub.getUsername());
        enc.addSubmitter(sub);
        List<User> vols = ciGetVolunteerUsers(allJson, myShepherd);
        enc.setInformOthers(vols);

        String dc = jin.optString("create_date", null);
        if (dc == null) dc = occJson.optString("create_date", null); //use the Occurrence date instead
        if (dc != null) {
            enc.setDWCDateAdded(dc);
            DateTime dt = toDateTime(dc);
            if (dt != null) {
                enc.setDWCDateAdded(dt.getMillis());
                enc.setDateInMilliseconds(dt.getMillis());
            }
        }
        Taxonomy tax = ciToTaxonomy(occJson.optString("CINMS Species", null), myShepherd);
        enc.setTaxonomy(tax);

        //since we dont have proper images, but only references to them, we create annotations with special "placeholder" features
        int imageStart = jin.optInt("Image Number Start", -1);
        int imageEnd = jin.optInt("Image Number End", -1);
        int sanityMaxNumberImages = 100;
        if ((imageStart < 0) || (imageEnd < 0) || (imageEnd < imageStart)) {
            enc.addComments("<p class=\"error\"><b>NOTE:</b> invalid range for image start/end; ignored</p><p class=\"json\">" + jin.toString(4) + "</p>");
            System.out.println("WARNING: " + enc + " had no valid image range [" + imageStart + " - " + imageEnd + "]");
        } else if ((imageEnd - imageStart) > sanityMaxNumberImages) {
            enc.addComments("<p class=\"error\"><b>NOTE:</b> too many images detected (" + (imageEnd - imageStart) + " > " + sanityMaxNumberImages + "); ignored</p><p class=\"json\">" + jin.toString(4) + "</p>");
            System.out.println("WARNING: " + enc + " number images > sanity check (" + sanityMaxNumberImages + ") [" + imageEnd + " - " + imageStart + "]");
        } else {
            String annot_species = "unknown_species";
            if (tax != null) annot_species = enc.getTaxonomyString();
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
                Annotation ann = new Annotation(annot_species, ft);
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

/*
    //someday, Taxonomy!  sigh  TODO
    private static String ciSpecies(String species) {  //e.g. "Blue Whale" --> "Balaenoptera musculus" ... also: may be null
        return species;  //meh. for now.
    }
    private static String[] ciSpeciesSplit(String species) { //e.g. "Foo Bar" --> ["Foo", "Bar"]
        if (species == null) return null;
        return species.split(" +");
    }
*/

/*
    unfortunately, we get a lot of noise for the "Observer Names" field, which need to be broken up
    into names to search on User.fullName ... examples we need to split on:
ci_data/ci_21339.json:  "Observer Names": "Jess Morten, Sean Hastings, Brad pilot", 
ci_data/ci_21340.json:  "Observer Names": "Emilee Hurlbert", 
ci_data/ci_21346.json:  "Observer Names": "Maria Ornelas\n",   #trailing noise! ugh
ci_data/ci_21367.json:  "Observer Names": "Carolyn McCleskey,Sue Miller",  #comma-no-space, oof
ci_data/ci_21371.json:  "Observer Names": "Rosie Romo\nDave Morse (PID)\n",   #wtf, gimme a break!
ci_data/ci_21384.json:  "Observer Names": "Sophie Busch", 
ci_data/ci_21390.json:  "Observer Names": "Sophie Busch and Marian Jean",    #you serious here???
ci_data/ci_21408.json:  "Observer Names": "Larry Driscoll\nAnn Camou",      #etc.

*/
    public static List<User> ciGetVolunteerUsers(JSONObject jin, Shepherd myShepherd) {
        if (jin == null) return null;
        String namesIn = jin.optString("Observer Names", "").replaceAll("\\n$", "").replaceAll(" \\([^\\)]+\\)", "");
        if (namesIn.equals("")) return null;
        namesIn = namesIn.replaceAll("\\n", ",").replaceAll("\\s+and\\s+", ",");
System.out.println("vols namesIn=[" + namesIn + "]");
        return ciGetVolunteerUsers(myShepherd, Arrays.asList(namesIn.split("\\s*,\\s*")));
    }

    public static User ciToUser(JSONObject jin, Shepherd myShepherd) {
        return ciGetSubmitterUser(myShepherd);
    }



/******************************************
    CaribWhale flavor
  "Jake's trips are under project #4 which is Carib Whale and should be very similar to the CINMS format"  -virgil
        thus, some of this piggybacks on ci* calls.

also, seems like project id=26 (shane's) is close enough to this that i am going to go ahead and piggyback on this
there are of course (sigh) some minor differences, so mind the hacking.

******************************************/
    //this is the "starting point" for JSON from the API
    public static Survey cwToSurvey(JSONObject jin, Shepherd myShepherd) {
        if (jin == null) return null;
        DateTime startDate = toDateTime(jin.optString("start_date", null));
        DateTime endDate = toDateTime(jin.optString("end_date", null));
        DateTime createDate = toDateTime(jin.optString("create_date", null));
        Survey survey = new Survey(startDate);

        //if (startDate != null) survey.setStartTimeMilli(startDate.getMillis());
        if (endDate != null) survey.setEndTimeMilli(endDate.getMillis());
        if (createDate != null) survey.addComments("<p>Created on source: <b>" + createDate.toString() + "</b></p>");

        survey.setProjectType("CaribWhale Spotter conserve.IO");
        String comments = "<p>Data Collector: <b>" + jin.optString("Data Collector", "<i>none provided</i>") + "</b>; ";
        comments += "Assistants: <b>" + jin.optString("Assistants", "<i>none provided</i>") + "</b>; ";
        comments += "Operator: <b>" + jin.optString("Operator", "<i>none provided</i>") + "</b>; ";
        comments += "Departure port: <b>" + jin.optString("Departure Port", "<i>none provided</i>") + "</b>; ";
        comments += "trip ID: <b>" + jin.optInt("_tripId", 0) + "</b></p>";
        survey.addComments(comments);
        survey.setProjectName("CaribWhale");
        survey.setOrganization("CaribWhale");

        //there will be only one SurveyTrack pulled from this data, fwiw
        SurveyTrack st = cwToSurveyTrack(jin, myShepherd);
        String integ = checkIntegrity(st, myShepherd);
        survey.addSurveyTrack(st);
        if (integ != null) survey.addComments("<p>Note: SurveyTrack failed integrity check</p>");

///TODO do we .setEffort based on survey track lengths or what???

        //HACK ... can be either one of these
        JSONArray weatherArr = jin.optJSONArray("Demo Weather");
        if (weatherArr == null) weatherArr = jin.optJSONArray("CaribWhale Weather");
        if (weatherArr != null) {
            ArrayList<Observation> wths = new ArrayList<Observation>();
            for (int i = 0 ; i < weatherArr.length() ; i++) {
                Observation wth = ciToWeather(weatherArr.optJSONObject(i), survey);
                if (wth != null) wths.add(wth);
            }
            survey.addObservationArrayList(wths);
        }
        return survey;
    }

/*
    public static Observation ciToWeather(JSONObject wj, Survey surv) {
        if (wj == null) return null;
        Observation obs = new Observation(OBS_WEATHER_NAME, wj.toString(), surv, surv.getID());
        DateTime dt = toDateTime(wj.optString("create_date", null));
        obs.setDateAddedMilli((dt == null) ? null : dt.getMillis());
        obs.setDateLastModifiedMilli();
        return obs;
    }
*/

    public static SurveyTrack cwToSurveyTrack(JSONObject jin, Shepherd myShepherd) {
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
                Occurrence occ = cwToOccurrence(jocc.optJSONObject(i), jocc.optJSONObject(i + halfSize), jin, myShepherd);
                if (occ != null) occs.add(occ);
            }
            st.setOccurrences(occs);
        }

        Path path = trackToPath(jin.optJSONObject("track"));
        if (path != null) st.setPath(path);

        st.setVesselID(jin.optString("Ship Name", null));
        return st;
    }


    public static Occurrence cwToOccurrence(JSONObject jin, JSONObject jin2, JSONObject allJson, Shepherd myShepherd) {
        int tripId = allJson.optInt("_tripId", 0);
        Occurrence occ = new Occurrence();
        occ.setDWCDateLastModified();
        occ.setOccurrenceID(Util.generateUUID());
        occ.addComments(jin.optString("Comments", null));
        occ.setDateTimeCreated(jin.optString("create_date", null));
        occ.setBearing(findDouble(jin, "device_bearing"));
        occ.setDistance(findDouble(jin, "Distance"));
        occ.setDecimalLatitude(resolveLatLon(jin, "device_latitude", "latitude"));
        occ.setDecimalLongitude(resolveLatLon(jin, "device_longitude", "longitude"));
        int numCalves = jin.optInt("Calves Sighted", 0);
        int numTotal = jin.optInt("Number Sighted", 0);
        int numAdults = numTotal - numCalves;
        occ.setNumCalves(numCalves);
        occ.setNumAdults(numAdults);
        occ.setBestGroupSizeEstimate(new Double(numTotal));
        occ.setSightingPlatform(allJson.optString("Ship Name", null));
        occ.setSource("SpotterConserveIO:cw:" + tripId);
        String bool = "<p>Photos taken? <b>" + (jin.optBoolean("Photos Taken?", false) ? "yes" : "no") + "</b>; ";
        bool += "Calf present? <b>" + (jin.optBoolean("Calf Present?", false) ? "yes" : "no") + "</b>; ";
        bool += "Birds present? <b>" + (jin.optBoolean("Birds Present?", false) ? "yes" : "no") + "</b>; ";
        bool += "</p>";
        occ.addComments(bool);
/*
        String taxString = jin.optString("CINMS Species", null);
        if (taxString != null) occ.addSpecies(taxString, myShepherd);
*/
        Taxonomy tax = ciToTaxonomy(jin.optString("CINMS Species", null), myShepherd);
System.out.println("(cw)ciToTaxonomy => " + tax);
        if (tax != null) occ.addTaxonomy(tax);

        //it actually appears the jin2 array contains WhaleAlert type sightings data, fwiw; but we only care about these 2:
        occ.addComments("<p class=\"import-source\">conserve.io source: <a href=\"" + jin2.optString("url") + "\"><b>" + jin2.optString("id") + "</b></a></p>");

        //HACK it can be either of these   :(
        JSONArray behavArr = jin.optJSONArray("Behavior");
        if (behavArr == null) behavArr = jin.optJSONArray("CaribWhale Behavior");
        if (behavArr != null) {
            List<Instant> bhvs = new ArrayList<Instant>();
            for (int i = 0 ; i < behavArr.length() ; i++) {
                Instant bhv = cwToBehavior(behavArr.optJSONObject(i));
                if (bhv != null) bhvs.add(bhv);
            }
            occ.setBehaviors(bhvs);
        }

/*   this does not seem to exist in CaribWhale ... :/
        if (jin.optJSONArray("CINMS Photo Log") != null) {
            ArrayList<Encounter> encs = new ArrayList<Encounter>();
            JSONArray je = jin.getJSONArray("CINMS Photo Log");
            for (int i = 0 ; i < je.length() ; i++) {
                Encounter enc = ciToEncounter(je.optJSONObject(i), jin, occ.getOccurrenceID(), allJson, myShepherd);
                if (enc != null) encs.add(enc);
            }
            occ.setEncounters(encs);
        }
*/

/* NOTE!   jin2 does seem to have a "photos" array.  not sure if it is the same as use in Whale Alert, e.g.:
                Encounter enc = waToEncounter(je.optString(i, null), jin, occ, myShepherd);
*/
        occ.setSubmitter(cwToUser(allJson, myShepherd));
        //List<User> vols = ciGetVolunteerUsers(allJson, myShepherd);
        //occ.setInformOthers(vols);
        return occ;
    }

    public static Instant cwToBehavior(JSONObject jin) {
        //HACK can be either of these....
        String name = jin.optString("Behavior", null);
        if (name == null) name = jin.optString("CaribWhale Behavior", null);
        DateTime dt = toDateTime(jin.optString("create_date", null));
        if ((name == null) || (dt == null)) return null;
        return new Instant(name, dt, null);
    }

    //FIXME  these are hardcoded for now cuz i have no idea how to map these -- no email addresses.  :/
    public static User cwToUser(JSONObject jin, Shepherd myShepherd) {
        if (jin.optString("creator").equals("Jlevenson1")) return myShepherd.getUserByUUID("dc23b977-dfaa-4cda-b074-78df4f388dd8");
        if (jin.optString("creator").equals("ShaneGero")) return myShepherd.getUserByUUID("6811f404-aaa5-4b13-9d2c-111e3738955b");
        //return ciGetSubmitterUser(myShepherd);
        return null;
    }



/******************************************
    Whale Alert flavor
******************************************/

    //this is the "starting point" for JSON from the API
    // Whale Alert has no Survey/SurveyTrack info, so we go straight to Occurrence
    //   n.b. chose Occurrence over Encounter since there is the possibility of reporting > 1 animal in the data from api
    public static List<Occurrence> waToOccurrences(JSONObject jin, Shepherd myShepherd) {
        if (jin.optJSONArray("sightings") == null) return null;   // not a lot to do!  :( 
        List<Occurrence> occs = new ArrayList<Occurrence>();

        // similar to CI, sightings list uses (i) and (i + N/2) format list as well
        JSONArray jocc = jin.getJSONArray("sightings");
        if (jocc.length() % 2 == 1) throw new RuntimeException("sightings JSONArray is odd length=" + jocc.length());
        int halfSize = (int) jocc.length() / 2;
        for (int i = 0 ; i < halfSize ; i++) {
            Occurrence occ = waToOccurrence(jocc.optJSONObject(i), jocc.optJSONObject(i + halfSize), jin.optInt("_tripId", 0), myShepherd);
            if (occ != null) occs.add(occ);
        }
        checkIntegrity(occs, false, myShepherd);
        return occs;
    }

    public static Occurrence waToOccurrence(JSONObject jin, JSONObject jin2, int tripId, Shepherd myShepherd) {
        Occurrence occ = new Occurrence();
        occ.setDWCDateLastModified();
        occ.setOccurrenceID(Util.generateUUID());
        String comments = jin.optString("Comments", null);
        if (comments == null) {
            comments = "<p>Whale Alert trip ID: <b>" + tripId + "</b></p>";
        } else {
            comments = "<p>" + comments + "</p><p>Whale Alert trip ID: <b>" + tripId + "</b></p>";
        }
        occ.addComments(comments);
        occ.setDateTimeCreated(jin.optString("create_date", null));
        occ.setBearing(findDouble(jin, "device_bearing"));
        occ.setDecimalLatitude(resolveLatLon(jin, "device_latitude", "Latitude"));
        occ.setDecimalLongitude(resolveLatLon(jin, "device_longitude", "Longitude"));
        occ.setIndividualCount(jin.optInt("Number Sighted", 0));
        //occ.setBestGroupSizeEstimate(jin.optDouble("Number Sighted", 0.0));
        occ.setSource("SpotterConserveIO:wa:" + tripId);

        //  also notable???     Whale Alert Other Species: ""
        Taxonomy tax = waToTaxonomy(jin.optString("Whale Alert Species", null), myShepherd);
System.out.println("wa.tax => " + tax);
        if (tax != null) occ.addTaxonomy(tax);

        //it actually appears the jin2 array contains WhaleAlert type sightings data, fwiw; but we only care about these 2:
        occ.addComments("<p class=\"import-source\">conserve.io source: <a href=\"" + jin2.optString("url") + "\"><b>" + jin2.optString("id") + "</b></a></p>");

        if (jin2.optJSONArray("photos") != null) {
            ArrayList<Encounter> encs = new ArrayList<Encounter>();
            JSONArray je = jin2.getJSONArray("photos");
            for (int i = 0 ; i < je.length() ; i++) {
                //basically we get one Encounter per photo here and let the Occurrence group it together
                Encounter enc = waToEncounter(je.optString(i, null), jin, occ, myShepherd);
                if (enc != null) encs.add(enc);
            }
            occ.setEncounters(encs);
        }
        occ.setSubmitter(waToUser(jin, myShepherd));
        myShepherd.getPM().makePersistent(occ);
        return occ;
    }

    public static Taxonomy waToTaxonomy(String taxString, Shepherd myShepherd) {
        if (taxString == null) return null;
        Taxonomy tax = findTaxonomy(myShepherd, taxString);
        if (tax != null) return tax;
        //we make a new one, but assume it is non-specific (cuz what do we know?)
        tax = new Taxonomy(taxString);
        tax.setNonSpecific(true);
        myShepherd.storeNewTaxonomy(tax);
        return tax;
    }

    public static Encounter waToEncounter(String photoUrl, JSONObject occJson, Occurrence occ, Shepherd myShepherd) {
        URLAssetStore urlStore = URLAssetStore.find(myShepherd);
        if (urlStore == null) throw new RuntimeException("Could not find a URLAssetStore to store images");
        Encounter enc = new Encounter();
        User sub = waToUser(occJson, myShepherd);
        if ((sub != null) && (sub.getUsername() != null)) enc.setSubmitterID(sub.getUsername());
        enc.addSubmitter(sub);
        enc.setCatalogNumber(Util.generateUUID());
        //enc.setGroupSize(???)
        enc.setOccurrenceID(occ.getID());

        //we have a start_date and end_date in *very top level* (not occJson!) but it seems (!??) to always be the
        //  same timestamp throughout as create_date as well!!  so we are going to use this value for:
        //  enc.DWCDateAdded as well as enc *date of encounter*.  :/   TODO figure out whats up here!

        String dc = occJson.optString("create_date", null);
        if (dc != null) {
            enc.setDWCDateAdded(dc);
            DateTime dt = toDateTime(dc);
            if (dt != null) {
                enc.setDWCDateAdded(dt.getMillis());  //sets the millis version on enc.  SIGH!!!!!!!!!!!
                enc.setDateInMilliseconds(dt.getMillis());  //sets the real date (.month, etc)
            }
        }

        //we also just grab the Occurrence lat/lon too
        enc.setDecimalLatitude(occ.getDecimalLatitude());
        enc.setDecimalLongitude(occ.getDecimalLongitude());

        Taxonomy tax = waToTaxonomy(occJson.optString("Whale Alert Species", null), myShepherd);
System.out.println("wa.tax => " + tax);
        enc.setTaxonomy(tax);
        String annotSpecies = "annot_species";
        if (tax != null) {
            annotSpecies = enc.getTaxonomyString();
            occ.addTaxonomy(tax);
        }

        if (photoUrl != null) {
            JSONObject params = new JSONObject();
            params.put("url", photoUrl);
            MediaAsset ma = urlStore.create(params);
            ma.addLabel("_original");
            ma.addDerivationMethod("SpotterConserveIO.waToEncounter", System.currentTimeMillis());
            ///////ma.setAccessControl(request);  // sub User ??
            try {
                ma.updateMetadata();
            } catch (IOException iox) {
                System.out.println("WARNING: SpotterConserveIO.waToEncounter() on updateMetadata() of " + photoUrl + " threw " + iox.toString());
            }
            MediaAssetFactory.save(ma, myShepherd);
            ma.updateStandardChildren(myShepherd);
            Annotation ann = new Annotation(annotSpecies, ma);
            myShepherd.getPM().makePersistent(ann);
            enc.addAnnotation(ann);
        }

        myShepherd.getPM().makePersistent(enc);
System.out.println("MADE " + enc);
        return enc;
    }

    public static User waToUser(JSONObject jin, Shepherd myShepherd) {
System.out.println("waToUser -> " + jin);
        return findOrMakeUser(
            jin.optString("Whale Alert Submitter Email", null),
            jin.optString("Whale Alert Submitter Phone", null),
            myShepherd
        );
    }

    public static User findOrMakeUser(String email, String phone, Shepherd myShepherd) {
        User user = null;
        if (email != null) user = myShepherd.getUserByEmailAddress(email);  //"real" user by email
        if (user != null) return user;

        //now we look for hash-based usernames (first email, then phone)
        String unameEmail = hashFromEmail(email);
        if (unameEmail != null) user = myShepherd.getUser(unameEmail);
        if (user != null) return user;
        String unamePhone = hashFromPhone(phone);
        if (unamePhone != null) user = myShepherd.getUser(unamePhone);
        if (user != null) return user;

        //no existing user, lets see if we can make one
        if ((unameEmail == null) && (unamePhone == null)) return null;  //cant make one.  :(
        String uname = unameEmail;
        if (uname == null) uname = unamePhone;
        String salt = ServletUtilities.getSalt().toHex();
        String hashedPassword = ServletUtilities.hashAndSaltPassword(Util.generateUUID(), salt);
        user = new User(uname, hashedPassword, salt);
        System.out.println("findOrMakeUser() ==> " + user);
        if (user == null) return null;

        user.setFullName("Conserve.IO User " + uname.substring(0,8));
        user.setNotes("Created by SpotterConserveIO.findOrMakeUser() " + new DateTime());
        String orgId = props.getProperty(PROP_ORGID_NEWUSER);
        Organization org = Organization.load(orgId, myShepherd);
        if (org != null) user.addOrganization(org);
        myShepherd.getPM().makePersistent(user);
        return user;
    }


/******************************************
    Ocean Alert flavor
******************************************/

    //this is the "starting point" for JSON from the API
    // Whale Alert has no Survey/SurveyTrack info, so we go straight to Occurrence
    //   n.b. chose Occurrence over Encounter since there is the possibility of reporting > 1 animal in the data from api
    public static List<Occurrence> oaToOccurrences(JSONObject jin, Shepherd myShepherd) {
        if (jin.optJSONArray("sightings") == null) return null;   // not a lot to do!  :( 
        List<Occurrence> occs = new ArrayList<Occurrence>();

        // similar to CI, sightings list uses (i) and (i + N/2) format list as well
        JSONArray jocc = jin.getJSONArray("sightings");
        if (jocc.length() % 2 == 1) throw new RuntimeException("sightings JSONArray is odd length=" + jocc.length());
        int halfSize = (int) jocc.length() / 2;
        for (int i = 0 ; i < halfSize ; i++) {
            Occurrence occ = oaToOccurrence(jocc.optJSONObject(i), jocc.optJSONObject(i + halfSize), jin.optInt("_tripId", 0), myShepherd);
            if (occ != null) occs.add(occ);
        }
        checkIntegrity(occs, false, myShepherd);
        return occs;
    }

    public static Occurrence oaToOccurrence(JSONObject jin, JSONObject jin2, int tripId, Shepherd myShepherd) {
        Occurrence occ = new Occurrence();
        occ.setDWCDateLastModified();
        occ.setOccurrenceID(Util.generateUUID());
        String comments = jin.optString("Comments", null);
        if (comments == null) {
            comments = "<p>Ocean Alert trip ID: <b>" + tripId + "</b></p>";
        } else {
            comments = "<p>" + comments + "</p><p>Ocean Alert trip ID: <b>" + tripId + "</b></p>";
        }
        occ.addComments(comments);
        occ.setDateTimeCreated(jin.optString("create_date", null));
        occ.setBearing(findDouble(jin, "device_bearing"));
        occ.setDecimalLatitude(resolveLatLon(jin, "device_latitude", "Latitude"));
        occ.setDecimalLongitude(resolveLatLon(jin, "device_longitude", "Longitude"));
        occ.setIndividualCount(jin.optInt("Number Sighted", 0));
        //occ.setBestGroupSizeEstimate(jin.optDouble("Number Sighted", 0.0));
        occ.addTaxonomy(oaToTaxonomy(jin, myShepherd));
        occ.setSource("SpotterConserveIO:oa:" + tripId);

        //  also notable???     Whale Alert Other Species: ""
        //      Animal Status: "Test",

        //it actually appears the jin2 array contains WhaleAlert type sightings data, fwiw; but we only care about these 2:
        occ.addComments("<p class=\"import-source\">conserve.io source: <a href=\"" + jin2.optString("url") + "\"><b>" + jin2.optString("id") + "</b></a></p>");

        if (jin2.optJSONArray("photos") != null) {
            ArrayList<Encounter> encs = new ArrayList<Encounter>();
            JSONArray je = jin2.getJSONArray("photos");
            for (int i = 0 ; i < je.length() ; i++) {
                //basically we get one Encounter per photo here and let the Occurrence group it together
                Encounter enc = oaToEncounter(je.optString(i, null), jin, occ, myShepherd);
                if (enc != null) encs.add(enc);
            }
            occ.setEncounters(encs);
        }
        occ.setSubmitter(oaToUser(jin, myShepherd, occ));
        myShepherd.getPM().makePersistent(occ);
        return occ;
    }


    public static Encounter oaToEncounter(String photoUrl, JSONObject occJson, Occurrence occ, Shepherd myShepherd) {
        URLAssetStore urlStore = URLAssetStore.find(myShepherd);
        if (urlStore == null) throw new RuntimeException("Could not find a URLAssetStore to store images");
        Encounter enc = new Encounter();
        enc.setCatalogNumber(Util.generateUUID());
        //enc.setGroupSize(???)
        enc.setOccurrenceID(occ.getID());

        User sub = oaToUser(occJson, myShepherd, occ);
        if ((sub != null) && (sub.getUsername() != null)) enc.setSubmitterID(sub.getUsername());
        enc.addSubmitter(sub);

        //we have a start_date and end_date in *very top level* (not occJson!) but it seems (!??) to always be the
        //  same timestamp throughout as create_date as well!!  so we are going to use this value for:
        //  enc.DWCDateAdded as well as enc *date of encounter*.  :/   TODO figure out whats up here!

        String dc = occJson.optString("create_date", null);
        if (dc != null) {
            enc.setDWCDateAdded(dc);
            DateTime dt = toDateTime(dc);
            if (dt != null) {
                enc.setDWCDateAdded(dt.getMillis());  //sets the millis version on enc.  SIGH!!!!!!!!!!!
                enc.setDateInMilliseconds(dt.getMillis());  //sets the real date (.month, etc)
            }
        }

        //we also just grab the Occurrence lat/lon too
        enc.setDecimalLatitude(occ.getDecimalLatitude());
        enc.setDecimalLongitude(occ.getDecimalLongitude());
        Taxonomy tax = oaToTaxonomy(occJson, myShepherd);
        enc.setTaxonomy(tax);
        String annotSpecies = enc.getTaxonomyString();
        if (annotSpecies == null) annotSpecies = "unknown";  //only if no taxonomy above

        if (photoUrl != null) {
            JSONObject params = new JSONObject();
            params.put("url", photoUrl);
            MediaAsset ma = urlStore.create(params);
            ma.addLabel("_original");
            ma.addDerivationMethod("SpotterConserveIO.oaToEncounter", System.currentTimeMillis());
            ///////ma.setAccessControl(request);  // sub User ??
            try {
                ma.updateMetadata();
            } catch (IOException iox) {
                System.out.println("WARNING: SpotterConserveIO.oaToEncounter() on updateMetadata() of " + photoUrl + " threw " + iox.toString());
            }
            MediaAssetFactory.save(ma, myShepherd);
            ma.updateStandardChildren(myShepherd);
            Annotation ann = new Annotation(annotSpecies, ma);
            myShepherd.getPM().makePersistent(ann);
            enc.addAnnotation(ann);
        }

        myShepherd.getPM().makePersistent(enc);
System.out.println("MADE " + enc);
        return enc;
    }

    //Ocean Alert seems to still use key 'Whale Alert...'
    public static User oaToUser(JSONObject jin, Shepherd myShepherd, Occurrence occ) {
        return findOrMakeUser(
            jin.optString("Whale Alert Submitter Email", null),
            jin.optString("Whale Alert Submitter Phone", null),
            myShepherd
        );
    }

/* saved for prosperity?
    public static User PREVIOUS____oaToUser(JSONObject jin, Shepherd myShepherd, Occurrence occ) {
        String subEmail = jin.optString("Whale Alert Submitter Email", null);
        String subName = jin.optString("Whale Alert Submitter Name", null);
        /////String subPhone = jin.optString("Whale Alert Submitter Phone", null);  //ignore!

        User user = null;
        if (Util.stringExists(subEmail)) {
            String emhash = User.generateEmailHash(subEmail);
            //we get by *email hash* here (and not email) in case the email has been reset (e.g. GDPR)
            user = myShepherd.getUserByHashedEmailAddress(emhash);
            if (user == null) {
                user = new User(subEmail, Util.generateUUID());
                if (Util.stringExists(subName)) user.setFullName(subName);
                user.setNotes("<p>Created via Ocean Alert, Occurrence " + occ.getID() + ".</p>");
            }

        } else {  //no subEmail (so anonymous submission)
            user = new User(Util.generateUUID());
            if (Util.stringExists(subName)) user.setFullName(subName);
            user.setNotes("<p>Created via Ocean Alert, Occurrence " + occ.getID() + ".</p>");
        }
System.out.println("INFO: oaToUser(" + subEmail + ", " + subName + " --> " + user);
        return user;
    }
*/


/*
ITIS Species Scientific Name: "Mysticeti",
ITIS Species Common Name: "baleen whales",
ITIS Species TSN: "552298",
*/
    public static Taxonomy oaToTaxonomy(JSONObject jin, Shepherd myShepherd) {
        int tsn = jin.optInt("ITIS Species TSN", 0);
        String sciName = jin.optString("ITIS Species Scientific Name");
        String comName = jin.optString("ITIS Species Common Name");
        Taxonomy tax = null;
        if (tsn > 0) {  //easiest match
            tax = myShepherd.getTaxonomy(tsn);
            if (tax != null) return tax;
        }
        tax = findTaxonomy(myShepherd, sciName);
        if (tax != null) return tax;
        tax = findTaxonomy(myShepherd, comName);
        if (tax != null) return tax;
        if (sciName == null) return null;  //need it to fetch one or create one.  :(  so sorry
/*  this should be redundant now
        tax = myShepherd.getTaxonomy(sciName);
        if (tax != null) return tax;
*/
        tax = new Taxonomy(sciName);
        if (comName != null) tax.addCommonName(comName);
        if (tsn > 0) tax.setItisTsn(tsn);
        return tax;
    }


///// more flavorless utility


    public static Taxonomy findTaxonomy(Shepherd myShepherd, String tstring) {
        if (tstring == null) return null;
        List<Taxonomy> found = Taxonomy.findMatch(myShepherd, "(?i)" + tstring);  //exact match (but case-insensitive)
        if (found.size() > 0) return found.get(0);
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

    public static JSONObject ciGetTripList(String context) throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException, InvalidKeyException {
        int since = ciGetLastSync(context);
        JSONObject rtn = ciGetTripListSince(since);
        int last = ciSetLastSync(context);
        rtn.put("_wb_since", since);
        rtn.put("_wb_set_last", last);
        rtn.put("_wb_timestamp", System.currentTimeMillis());
        return rtn;
    }
    public static JSONObject waGetTripList(String context) throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException, InvalidKeyException {
        int since = waGetLastSync(context);
        JSONObject rtn = waGetTripListSince(since);
        int last = waSetLastSync(context);
        rtn.put("_wb_since", since);
        rtn.put("_wb_set_last", last);
        rtn.put("_wb_timestamp", System.currentTimeMillis());
        return rtn;
    }

    //note: since is seconds (int), NOT millis (long) !!!
    public static JSONObject ciGetTripListSince(int since) throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException, InvalidKeyException {
System.out.println(">>> ciGetTripListSince grabbing since " + new DateTime(new Long(since) * 1000));
        return apiGet("/project/" + PROJECT_ID_CI + "/trip_data/" + since + "/0");
    }
    public static JSONObject waGetTripListSince(int since) throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException, InvalidKeyException {
System.out.println(">>> waGetTripListSince grabbing since " + new DateTime(new Long(since) * 1000));
        return apiGet("/project/" + PROJECT_ID_WA + "/trip_data/" + since + "/0");
    }


    //this assumes the user is Whale Alert (for now?)
    public static boolean testLogin(String username, String password) {
        try {
            checkInit();
            long since = (System.currentTimeMillis() - (7*24*60*60*1000)) / 1000l;
            URL getUrl = new URL(apiUrlPrefix + "/project/" + PROJECT_ID_WA + "/trip_data/" + since + "/0");
            String res = RestClient.get(getUrl, username, password);
        } catch (Exception ex) {
            System.out.println("SpotterConserveIO.testLogin() failed with " + ex.toString());
            ex.printStackTrace();
            return false;
        }
        return true;
    }

    //TODO needs some better way to tell some of these... sigh
    public static String tripFlavor(JSONObject tripData) {
        if (tripData == null) return null;
        if ((tripData.optString("Ship Name", null) != null) || (tripData.optString("Data Collector", null) != null)) return "cw";
        if ((tripData.optJSONObject("track") != null) || (tripData.optJSONArray("CINMS Weather") != null) || (tripData.optString("CINMS Vessel", null) != null)) return "ci";
        JSONArray sarr = tripData.optJSONArray("sightings");
        //ITIS Species TSN seems to only exist in Ocean Alert, not Whale Alert
        if ((sarr != null) && (sarr.length() > 0) && (sarr.optJSONObject(0) != null) && (sarr.getJSONObject(0).optString("ITIS Species TSN", null) != null)) return "oa";
        return "wa";
    }

    //CI & WA trips use same call
    public static JSONObject getTrip(int tripId) throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException, InvalidKeyException {
        JSONObject data = apiGet("/trip/" + tripId + "/data");
        if (data == null) return null;
        data.put("_tripId", tripId);
        data.put("_tripFlavor", tripFlavor(data));
        return data;
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


    //note: these are in seconds (not milli) cuz that is what spotter.io uses

    public static int ciGetLastSync(String context) {
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.beginDBTransaction();
        Integer last = SystemValue.getInteger(myShepherd, SYSTEMVALUE_KEY_LASTSYNC_CI);
        myShepherd.rollbackDBTransaction();
        if (last != null) return last;
        //if we dont have a value, we kind of grab one in the past so we arent getting everything forever!
        Long sec = (long)(System.currentTimeMillis() / 1000) - (7 * 24 * 60 * 60);
        return sec.intValue();
    }
    public static int ciSetLastSync(String context) {  //the "now" flavor
        Long sec = (long)(System.currentTimeMillis() / 1000);
        return ciSetLastSync(context, sec.intValue());
    }
    public static int ciSetLastSync(String context, int time) {
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.beginDBTransaction();
        SystemValue.set(myShepherd, SYSTEMVALUE_KEY_LASTSYNC_CI, time);
        myShepherd.commitDBTransaction();
        return time;
    }

    public static int waGetLastSync(String context) {
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.beginDBTransaction();
        Integer last = SystemValue.getInteger(myShepherd, SYSTEMVALUE_KEY_LASTSYNC_WA);
        myShepherd.rollbackDBTransaction();
        if (last != null) return last;
        //if we dont have a value, we kind of grab one in the past so we arent getting everything forever!
        Long sec = (long)(System.currentTimeMillis() / 1000) - (7 * 24 * 60 * 60);
        return sec.intValue();
    }
    public static int waSetLastSync(String context) {  //the "now" flavor
        Long sec = (long)(System.currentTimeMillis() / 1000);
        return waSetLastSync(context, sec.intValue());
    }
    public static int waSetLastSync(String context, int time) {
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.beginDBTransaction();
        SystemValue.set(myShepherd, SYSTEMVALUE_KEY_LASTSYNC_WA, time);
        myShepherd.commitDBTransaction();
        return time;
    }


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
                u.setNotes("auto-created by SpotterConserveIO data import");
                myShepherd.getPM().makePersistent(u);
                vorg.addMember(u);
            }
            vols.add(u);
        }
        myShepherd.getPM().makePersistent(vorg);
        return vols;
    }

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
        enc.setState(ENCOUNTER_STATE_REVIEW);
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

