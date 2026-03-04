package org.ecocean;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.UUID;

import java.text.SimpleDateFormat;
import java.util.Date;

import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.shepherd.core.ShepherdProperties;
import org.ecocean.tag.MetalTag;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.regex.Pattern;
import net.jpountz.xxhash.StreamingXXHash32;
import net.jpountz.xxhash.XXHash32;
import net.jpountz.xxhash.XXHashFactory;
import org.apache.commons.lang3.StringUtils;
import org.apache.tika.Tika;
import org.joda.time.DateTime;

import com.drew.imaging.jpeg.JpegMetadataReader;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import java.util.Iterator;

import org.apache.commons.io.IOUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.jdo.Query;
import javax.servlet.http.HttpServletRequest;

import com.reijns.I3S.Point2D;

public class Util {
    // Measurement static values
    private static final String MEASUREMENT = "measurement";
    private static final String BIOLOGICALMEASUREMENT = "biologicalMeasurementType";
    private static final String UNITS = MEASUREMENT + "Units";
    private static final String BIOLOGICALMEASUREMENTUNITS = BIOLOGICALMEASUREMENT.replaceAll(
        "Type", "Units");
    private static final String METAL_TAG_LOCATION = "metalTagLocation";
    private static final String SATELLITE_TAG_NAME = "satelliteTagName";
    private static final String VESSEL = "vessel";
    private static final String GENUS_SPECIES = "genusSpecies";

    public static final double INVALID_LAT_LON = 999.0; // sometimes you need a bad coordinate!

    // GPS coordinate caching for Encounter Search and Individual Search
    private static ArrayList<Point2D> coords;

    public static List<MeasurementDesc> findMeasurementDescs(String langCode, String context) {
        List<MeasurementDesc> list = new ArrayList<MeasurementDesc>();
        List<String> types = CommonConfiguration.getIndexedPropertyValues(MEASUREMENT, context);

        if (types.size() > 0) {
            List<String> units = CommonConfiguration.getIndexedPropertyValues(UNITS, context);
            for (int i = 0; i < types.size() && i < units.size(); i++) {
                String type = types.get(i);
                String unit = units.get(i);
                String typeLabel = findLabel(type, langCode, context);
                String unitsLabel = findLabel(unit, langCode, context);
                list.add(new MeasurementDesc(type, typeLabel, unit, unitsLabel));
            }
        }
        return list;
    }

    public static ArrayList<String> findVesselNames(String langCode, String context) {
        ArrayList<String> list = new ArrayList<String>();
        List<String> types = CommonConfiguration.getIndexedPropertyValues(VESSEL, context);

        if (types.size() > 0) {
            for (int i = 0; i < types.size(); i++) {
                String type = types.get(i);
                list.add(type);
            }
        }
        return list;
    }

    public static ArrayList<String> findSpeciesNames(String langCode, String context) {
        ArrayList<String> nameArr = new ArrayList<>();
        List<String> nameList = CommonConfiguration.getIndexedPropertyValues(GENUS_SPECIES,
            context);

        if (nameList.size() > 0) {
            for (String name : nameList) {
                nameArr.add(name);
            }
        }
        return nameArr;
    }

    public static List<MeasurementDesc> findBiologicalMeasurementDescs(String langCode,
        String context) {
        List<MeasurementDesc> list = new ArrayList<MeasurementDesc>();
        List<String> types = CommonConfiguration.getIndexedPropertyValues(BIOLOGICALMEASUREMENT,
            context);

        if (types.size() > 0) {
            List<String> units = CommonConfiguration.getIndexedPropertyValues(
                BIOLOGICALMEASUREMENTUNITS, context);
            for (int i = 0; i < types.size() && i < units.size(); i++) {
                String type = types.get(i);
                String unit = units.get(i);
                String typeLabel = findLabel(type, langCode, context);
                String unitsLabel = findLabel(unit, langCode, context);
                list.add(new MeasurementDesc(type, typeLabel, unit, unitsLabel));
            }
        }
        return list;
    }

    // a hook to UUID generator
    public static String generateUUID() {
        return UUID.randomUUID().toString();
    }

    public static String prettyUUID(String uuid) {
        if (!isUUID(uuid)) return uuid;
        return (uuid.substring(0, 8) + "...");
    }

    public static boolean isUUID(String s) {
        if (s == null) return false;
        boolean ok = true;
        try {
            UUID u = UUID.fromString(s);
        } catch (java.lang.IllegalArgumentException e) {
            ok = false;
        }
        return ok;
    }

    /**
     * Returns a map of sampling protocols, where the key is the name of protocol, and the value is the user-friendly, localized label.
     * @param langCode
     * @return
     */
    public static List<OptionDesc> findSamplingProtocols(String langCode, String context) {
        List<String> values = CommonConfiguration.getIndexedPropertyValues("samplingProtocol",
            context);
        List<OptionDesc> list = new ArrayList<OptionDesc>();
        int valuesSize = values.size();

        for (int i = 0; i < valuesSize; i++) {
            String key = "samplingProtocol" + i;
            String label = findLabel(key, langCode, context);
            list.add(new OptionDesc(key, label));
        }
        return list;
    }

    public static String getLocalizedSamplingProtocol(String samplingProtocol, String langCode,
        String context) {
        if (samplingProtocol != null) {
            List<OptionDesc> samplingProtocols = findSamplingProtocols(langCode, context);
            for (OptionDesc optionDesc : samplingProtocols) {
                if (optionDesc.name.equals(samplingProtocol)) {
                    return optionDesc.display;
                }
            }
        }
        return null;
    }

    public static List<MetalTagDesc> findMetalTagDescs(String langCode, String context) {
        List<String> metalTagLocations = CommonConfiguration.getIndexedPropertyValues(
            METAL_TAG_LOCATION, context);
        List<MetalTagDesc> list = new ArrayList<MetalTagDesc>();

        for (String location : metalTagLocations) {
            String locationLabel = findLabel(location, langCode, context);
            list.add(new MetalTagDesc(location, locationLabel));
        }
        return list;
    }

    /**
     * Find the MetalTag instance belonging to an Encounter that is described by the MetalTagDesc.
     * @param metalTagDesc
     * @param langCode
     * @param encounter
     * @return
     */
    public static MetalTag findMetalTag(MetalTagDesc metalTagDesc, Encounter encounter) {
        List<MetalTag> metalTags = encounter.getMetalTags();

        if (metalTags != null) {
            for (MetalTag metalTag : metalTags) {
                if (metalTag.getLocation().equals(metalTagDesc.getLocation())) {
                    return metalTag;
                }
            }
        }
        return null;
    }

    public static List<String> findSatelliteTagNames(String context) {
        return CommonConfiguration.getIndexedPropertyValues(SATELLITE_TAG_NAME, context);
    }

    public static String findLabel(String key, String langCode, String context) {
        // System.out.println("Trying to find key: "+key+" with langCode "+langCode);

        Properties myProps = ShepherdProperties.getProperties(
            "commonConfigurationLabels.properties", langCode, context);

        if (myProps == null) return null;
        return myProps.getProperty(key + ".label");
    }

    public static String quote(String arg) {
        StringBuilder sb = new StringBuilder(arg.length() + 2);

        sb.append('"');
        sb.append(arg);
        sb.append('"');
        return sb.toString();
    }

    public static class MeasurementDesc {
        private String type;
        private String label;
        private String units;
        private String unitsLabel;

        private MeasurementDesc(String type, String label, String units, String unitsLabel) {
            this.type = type;
            this.label = label;
            this.units = units;
            this.unitsLabel = unitsLabel;
        }

        public String getType() {
            return type;
        }

        public String getLabel() {
            return label;
        }

        public String getUnits() {
            return units;
        }

        public String getUnitsLabel() {
            return unitsLabel;
        }
    }

    public static class OptionDesc {
        private String name;
        private String display;
        private OptionDesc(String name, String display) {
            this.name = name;
            this.display = display;
        }

        public String getName() {
            return name;
        }

        public String getDisplay() {
            return display;
        }
    }

    public static class MetalTagDesc {
        private String location;
        private String locationLabel;

        private MetalTagDesc(String location, String locationLabel) {
            this.location = location;
            this.locationLabel = locationLabel;
        }

        public String getLocation() {
            return location;
        }

        public String getLocationLabel() {
            return locationLabel;
        }
    }

    public synchronized static ArrayList<Point2D> getCachedGPSCoordinates(boolean refresh,
        String context) {
        Shepherd myShepherd = new Shepherd(context);

        myShepherd.setAction("Util.class.getCached GPSCoordinates");
        myShepherd.beginDBTransaction();
        try {
            if ((coords == null) || (refresh)) {
                // execute the JDOQL

                Query query = myShepherd.getPM().newQuery(
                    "SELECT FROM org.ecocean.Encounter WHERE decimalLatitude != null && decimalLongitude != null");
                Collection<Encounter> c = (Collection<Encounter>)(query.execute());
                ArrayList<Encounter> encs = new ArrayList<Encounter>(c);
                int encsSize = encs.size();

                // populate coords
                coords = new ArrayList<Point2D>(encsSize);
                for (int i = 0; i < encsSize; i++) {
                    Encounter e = encs.get(i);
                    // updating this to handle empty (null) values
                    // note: forcing this to an int was legacy code.  seems "bad" (loss of accuracy); but doing anyway!  2017-03-16  -jon   FIXME?
                    if ((e.getDecimalLatitudeAsDouble() == null) ||
                        (e.getDecimalLongitudeAsDouble() == null)) continue;
                    int lat = (int)Math.round(e.getDecimalLatitudeAsDouble());
                    int longie = (int)Math.round(e.getDecimalLongitudeAsDouble());
                    // int lat=(int)e.getDecimalLatitudeAsDouble();
                    // int longie=(int)e.getDecimalLongitudeAsDouble();
                    Point2D myPoint = new Point2D(lat, longie);
                    if (!coords.contains(myPoint)) {
                        coords.add(myPoint);
                    }
                }
                query.closeAll();
            }
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
            return coords;
        } catch (Exception jdo) {
            jdo.printStackTrace();
            System.out.println(
                "I hit an error trying to populate the cached GPS coordinates in Util.java.");
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
            return new ArrayList<Point2D>();
        }
    }

    public static String getEXIFDataFromJPEGAsHTML(File exifImage) {
        StringBuffer results = new StringBuffer(
            "<p>File not found on file system. No EXIF data available.</p><p>Looking in: " +
            exifImage.getAbsolutePath() + "</p>");

        if (exifImage.exists()) {
            results = new StringBuffer();
            InputStream inp = null;

            try {
                inp = new FileInputStream(exifImage);
                Metadata metadata = JpegMetadataReader.readMetadata(exifImage);
                // iterate through metadata directories
                Iterator directories = metadata.getDirectories().iterator();
                while (directories.hasNext()) {
                    Directory directory = (Directory)directories.next();
                    // iterate through tags and print to System.out
                    Iterator tags = directory.getTags().iterator();
                    while (tags.hasNext()) {
                        Tag tag = (Tag)tags.next();
                        results.append(tag.toString() + "<br/>");
                    }
                }
                inp.close();
            } // end try
            catch (Exception e) {
                results = null;
                results = new StringBuffer("<p>Cannot read metadata for this file.</p>");
            } finally {
                IOUtils.closeQuietly(inp);
            }
        } // end if
        return results.toString();
    }

    // got sick of having to concat these strings with a space in the middle.
    // TODO: Make a Taxonomy class for storing/processing this stuff right
    public static String taxonomyString(String genus, String species) {
        if (stringExists(genus) && stringExists(species)) return genus + " " + species;
        if (stringExists(genus)) return genus;
        if (stringExists(species)) return species;
        return null;
    }

    // will always be null, String[1] or String[2]
    public static String[] stringToGenusSpecificEpithet(String s) {
        if (s == null) return null;
        String[] gs = null;
        int i = s.indexOf(" ");
        if (i < 0) {
            gs = new String[1];
            gs[0] = s;
        } else {
            gs = new String[2];
            gs[0] = s.substring(0, i);
            gs[1] = s.substring(i + 1);
        }
        return gs;
    }

    // a generic version of our uuid-dir-structure-creating algorithm -- adjust as needed!?
    // TODO: check for incoming slashes and similar weirdness
    public static String hashDirectories(String in, String separator) {
        if ((in == null) || (in.length() < 4)) return in;
        return in.charAt(0) + separator + in.charAt(1) + separator + in;
    }

    public static String hashDirectories(String in) {
        return hashDirectories(in, File.separator);
    }

    public static boolean isIdentityMatrix(float[] m) {
        if (m == null) return false;
        if (m.length != 6) return false;
        if ((m[0] == 1) && (m[1] == 0) && (m[2] == 0) && (m[3] == 1) && (m[4] == 0) && (m[5] == 0))
            return false;
        return true;
    }

    public static org.datanucleus.api.rest.orgjson.JSONObject toggleJSONObject(JSONObject jin) {
        if (jin == null) return null;
        return stringToDatanucleusJSONObject(jin.toString());
    }

    public static JSONObject toggleJSONObject(org.datanucleus.api.rest.orgjson.JSONObject jin) {
        if (jin == null) return null;
        return stringToJSONObject(jin.toString());
    }

    public static org.datanucleus.api.rest.orgjson.JSONArray toggleJSONArray(
        org.json.JSONArray jin) {
        if (jin == null) return null;
        return stringToDatanucleusJSONArray(jin.toString());
    }

    public static org.json.JSONArray toggleJSONArray(
        org.datanucleus.api.rest.orgjson.JSONArray jin) {
        if (jin == null) return null;
        return stringToJSONArray(jin.toString());
    }

    public static Object jsonNull(Object obj) {
        return (obj == null) ? org.json.JSONObject.NULL : obj;
    }

    // this basically just swallows exceptions in parsing and returns a null if failure
    public static JSONObject stringToJSONObject(String s) {
        JSONObject j = null;

        if (s == null) return j;
        try {
            j = new JSONObject(s);
        } catch (JSONException je) {
            System.out.println("error parsing json string (" + s + "): " + je.toString());
        }
        return j;
    }

    // note, this will (silently) *skip* non-string elements!  you have been warned.
    public static List<String> jsonArrayToStringList(JSONArray arr) {
        if (arr == null) return null;
        List<String> rtn = new ArrayList<String>();
        for (int i = 0; i < arr.length(); i++) {
            String val = arr.optString(i, null);
            if (val != null) rtn.add(val);
        }
        return rtn;
    }

    public static boolean jsonArrayContains(JSONArray arr, String str) {
        if ((str == null) || (arr == null)) return false; // might be a matter of philosophical debate
        return jsonArrayToStringList(arr).contains(str);
    }

    public static org.datanucleus.api.rest.orgjson.JSONObject stringToDatanucleusJSONObject(
        String s) {
        org.datanucleus.api.rest.orgjson.JSONObject j = null;
        if (s == null) return j;
        try {
            j = new org.datanucleus.api.rest.orgjson.JSONObject(s);
        } catch (org.datanucleus.api.rest.orgjson.JSONException je) {
            System.out.println("error parsing json string (" + s + "): " + je.toString());
        }
        return j;
    }

    // this basically just swallows exceptions in parsing and returns a null if failure
    public static JSONArray stringToJSONArray(String s) {
        JSONArray j = null;

        if (s == null) return j;
        try {
            j = new JSONArray(s);
        } catch (JSONException je) {
            System.out.println("error parsing json string (" + s + "): " + je.toString());
        }
        return j;
    }

    public static org.datanucleus.api.rest.orgjson.JSONArray stringToDatanucleusJSONArray(
        String s) {
        org.datanucleus.api.rest.orgjson.JSONArray j = null;
        if (s == null) return j;
        try {
            j = new org.datanucleus.api.rest.orgjson.JSONArray(s);
        } catch (org.datanucleus.api.rest.orgjson.JSONException je) {
            System.out.println("error parsing json string (" + s + "): " + je.toString());
        }
        return j;
    }

    // NEW

    public static org.datanucleus.api.rest.orgjson.JSONArray concatJsonArrayInPlace(
        org.datanucleus.api.rest.orgjson.JSONArray toBeReturned,
        org.datanucleus.api.rest.orgjson.JSONArray toBeAdded)
    throws org.datanucleus.api.rest.orgjson.JSONException {
        for (int i = 0; i < toBeAdded.length(); i++) {
            toBeReturned.put(toBeAdded.get(i));
        }
        return toBeReturned;
    }

    /**
     * Useful for some UI stuff -db From stackOverflow http://stackoverflow.com/a/7085652
     **/
    public static org.datanucleus.api.rest.orgjson.JSONObject requestParamsToJSON(
        HttpServletRequest req)
    throws org.datanucleus.api.rest.orgjson.JSONException {
        org.datanucleus.api.rest.orgjson.JSONObject jsonObj =
            new org.datanucleus.api.rest.orgjson.JSONObject();
        Map<String, String[]> params = req.getParameterMap();
        for (Map.Entry<String, String[]> entry : params.entrySet()) {
            String v[] = entry.getValue();
            Object o = (v.length == 1) ? v[0] : v;
            jsonObj.put(entry.getKey(), o);
        }
        return jsonObj;
    }

    // the non-datanucleus-y version of above (which i also needed! -jon)
    public static JSONObject requestParametersToJSONObject(HttpServletRequest request) {
        if (request == null) return null;
        JSONObject j = new JSONObject();
        Enumeration<String> allParams = request.getParameterNames();
        while (allParams.hasMoreElements()) {
            String key = allParams.nextElement();
            String[] vals = request.getParameterValues(key);
            if (vals == null) continue;
            j.put(key, new JSONArray(Arrays.asList(vals)));
        }
        return j;
    }

    public static Map<String, Integer> mapAdd(Map<String, Integer> m1, Map<String, Integer> m2) {
        Map<String, Integer> sum = new HashMap<String, Integer>();

        if ((m1 == null) && (m2 == null)) return sum;
        if (m1 == null) return m2;
        if (m2 == null) return m1;
        Set<String> allKeys = new HashSet<String>();
        allKeys.addAll(m1.keySet());
        allKeys.addAll(m2.keySet());
        for (String key : allKeys) {
            sum.put(key, m1.getOrDefault(key, 0) + m2.getOrDefault(key, 0));
        }
        return sum;
    }

    // transforms a string such as "90.1334" or "46″ N 79°" into a decimal value
    // TODO: parse second type of input string
    public static Double getDecimalCoordFromString(String latOrLong) {
        try {
            return Double.valueOf(latOrLong);
        } catch (NumberFormatException nfe) {
            System.out.println("ERROR: could not parse decimal coordinate from string " +
                latOrLong);
            nfe.printStackTrace();
        }
        return null;
    }

    // GPS Longitude: "-69.0° 22.0' 45.62999999998169"",
    public static Double latlonDMStoDD(String dms) {
        String[] d = dms.split(" +");

        if (d.length < 1) return null;
        // System.out.println("latlonDMStoDD(" + dms + ") --> " + d[0] + "/" + d[1] + "/" + d[2]);
        Double dd = null;
        try {
            dd = Double.valueOf(d[0].substring(0, d[0].length() - 1));
            Double m = 0.0;
            Double s = 0.0;
            if (d.length > 1) m = Double.valueOf(d[1].substring(0, d[1].length() - 1));
            if (d.length > 2) s = Double.valueOf(d[2].substring(0, d[2].length() - 1));
            dd = Math.signum(dd) * (Math.abs(dd) + ((m * 60) + s) / (60 * 60));
            // System.out.println("  --> " + dd + " deg, " + m + " min, " + s + " sec => " + dd);
            return dd;
        } catch (NumberFormatException nfe) {
            return null;
        }
    }

    // whatever a string of this should look like.  apparently we want a string version on encounter for example. (?)
    public static String decimalLatLonToString(Double ll) {
        if (ll == null) return null;
        return ll.toString();
    }

    // h/t https://stackoverflow.com/a/39540339
    public static double latDegreesToMeters(double degLat) {
        return degLat * 111320.0d;
    }

    public static double lonDegreesToMeters(double degLon, double degLat) {
        return degLon * (40075000.0d * Math.cos(degLat) / 360d);
    }

    public static double latMetersToDegrees(double m) {
        return m / 111320.0d;
    }

    public static double lonMetersToDegrees(double m, double degLat) {
        return m / (40075000.0d * Math.cos(degLat) / 360d);
    }

    // one should consider this "approximate", btw    :)  :|
    public static double[] getComputedLatLon(Double decimalLatitude, Double decimalLongitude,
        Double bearing, Double distance) {
        if ((bearing == null) || (decimalLatitude == null) || (decimalLongitude == null) ||
            (distance == null)) return null;
        double[] cll = new double[2];
        double dx = distance * Math.sin(Math.toRadians(bearing));
        double dy = distance * Math.cos(Math.toRadians(bearing));
        cll[0] = latMetersToDegrees(latDegreesToMeters(decimalLatitude) + dy);
        cll[1] = lonMetersToDegrees(lonDegreesToMeters(decimalLongitude, decimalLatitude) + dx,
            decimalLatitude);
        return cll;
    }

    // see postgis/README.md for full details on these!  (including setup)
    public static JSONArray overlappingWaterGeometries(Shepherd myShepherd, Double lat, Double lon,
        Double radius) {
        if (!Util.isValidDecimalLatitude(lat) || !Util.isValidDecimalLongitude(lon)) return null;
        if ((radius == null) || (radius < 0)) radius = 200.0D; // this seems "close enough"... might be in meters?
        String sql =
            "SELECT ST_AsGeoJSON(ST_Transform(geom, 4326)) FROM overlappingWaterGeometries(" +
            lat.toString() + ", " + lon.toString() + ", " + radius.toString() + ")";
        Query q = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
        JSONArray rtn = new JSONArray();
        List results = (List)q.execute();
        Iterator it = results.iterator();
        while (it.hasNext()) {
            String js = (String)it.next();
            JSONObject geom = Util.stringToJSONObject(js);
            if (geom != null) rtn.put(geom);
        }
        q.closeAll();
        return rtn;
    }

    public static boolean nearWater(Shepherd myShepherd, Double lat, Double lon, Double radius) {
        if (!Util.isValidDecimalLatitude(lat) || !Util.isValidDecimalLongitude(lon)) return false;
        if ((radius == null) || (radius < 0)) radius = 200.0D;
        String sql = "SELECT nearWater(" + lat.toString() + ", " + lon.toString() + ", " +
            radius.toString() + ")";
        Query q = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
        List results = (List)q.execute();
        Iterator it = results.iterator();
        if (!it.hasNext()) return false;
        Boolean rtn = (Boolean)it.next();
        q.closeAll();
        return rtn;
    }

    // e.g. you have collectionSize = 13 items you want displayed in sections with 3 per section.
    public static int getNumSections(int collectionSize, int itemsPerSection) {
        return (collectionSize - 1) / itemsPerSection + 1;
    }

    public static String prettyPrintDateTime(DateTime dt) {
        boolean isOnlyDate = dateTimeIsOnlyDate(dt);
        String currentToString = dt.toString();

        if (isOnlyDate) {
            currentToString = currentToString.split("T")[0];
        }
        return (currentToString);
    }

    public static String prettyTimeStamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");

        return sdf.format(new Date());
    }

    public static boolean dateTimeIsOnlyDate(DateTime dt) {
        try {
            return (dt.millisOfDay().get() == 0);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean dateIsInFuture(Integer year, Integer month, Integer day) {
        if (year == null) return false;
        Calendar cal = Calendar.getInstance();
        int nowY = cal.get(Calendar.YEAR);
        int nowM = cal.get(Calendar.MONTH) + 1; // frikken zero-based months!
        int nowD = cal.get(Calendar.DAY_OF_MONTH);
        if (year > nowY) return true;
        if (month == null) return false; // only have year
        if ((year == nowY) && (month > nowM)) return true;
        if (day == null) return false; // only have year/month
        if ((year == nowY) && (month == nowM) && (day > nowD)) return true;
        return false;
    }

    public static String capitolizeFirstLetter(String str) {
        if (str == null) return str;
        if (str.length() <= 1) return (str.toUpperCase());
        return (str.substring(0, 1).toUpperCase() + str.substring(1));
    }

    public static String capitolizeFirstLetterOnly(String str) {
        if (str == null) return str;
        String lower = str.toLowerCase();
        return capitolizeFirstLetter(lower);
    }

    public static String getRequestParamIfExists(HttpServletRequest request, String paramName) {
        String returnVal = "";

        if (Util.stringExists(request.getParameter(paramName))) {
            returnVal = request.getParameter(paramName);
        }
        return returnVal;
    }

    public static boolean requestHasVal(HttpServletRequest request, String paramName) {
        return ((request.getParameter(paramName) != null) &&
                   (!request.getParameter(paramName).equals("")));
    }

    // h/t  https://www.mkyong.com/regular-expressions/how-to-validate-email-address-with-regular-expression/
    public static String validEmailRegexPattern() {
        return
                "^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$";
        // from https://stackoverflow.com/questions/201323/how-can-i-validate-an-email-address-using-a-regular-expression (JP found on 30 Aug 2021)
    }

    public static boolean isValidEmailAddress(String email) {
        if (email == null) return false;
        java.util.regex.Pattern patt = java.util.regex.Pattern.compile(validEmailRegexPattern());
        java.util.regex.Matcher matcher = patt.matcher(email);
        return matcher.matches();
    }

    public static String addToJDOFilter(String constraint, String filter, String origFilter) {
        if (filter.equals(origFilter)) return (filter + constraint);
        else return (filter + " && " + constraint);
    }

    public static String jdoStringContainsConstraint(String fieldName, String containsThis) {
        return "(" + fieldName + ".indexOf('" + containsThis + "') != -1)";
    }

    public static String jdoStringContainsConstraint(String fieldName, String containsThis,
        boolean toLowerCase) {
        if (!toLowerCase) return jdoStringContainsConstraint(fieldName, containsThis);
        return "(" + fieldName + ".toLowerCase().indexOf('" + containsThis.toLowerCase() +
                   "') != -1)";
    }

    public static String undoUrlEncoding(String str) {
        return str.replaceAll("%20", " ").trim();
    }

    public static <T> String toString(Enumeration<T> things) {
        StringBuilder result = new StringBuilder("[");

        while (things.hasMoreElements()) {
            T thing = things.nextElement();
            result.append(thing.toString());
            if (things.hasMoreElements()) result.append(", ");
        }
        result.append("]");
        return result.toString();
    }

    public static String toString(Object obj) {
        if (obj == null) return null;
        return obj.toString();
    }

    public static boolean stringExists(String str) {
        return (str != null && !str.trim().equals("") && !str.toLowerCase().equals("none") &&
                   !str.toLowerCase().equals("unknown"));
    }

    public static boolean stringIsEmptyOrNull(String str) {
        return ((str == null) || str.equals(""));
    }

    public static boolean stringsEqualish(String s1, String s2) {
        if (!stringExists(s1)) {
            if (!stringExists(s2)) return true;
            return false;
        }
        if (stringExists(s2)) {
            return s1.toLowerCase().trim().equals(s2.toLowerCase().trim());
        }
        return false;
    }

    public static boolean isEmpty(Collection c) {
        return (c == null || c.size() == 0);
    }

    // these two utility functions handle the case where the argument (Collection, and subclasses like Lists) is null!
    public static boolean collectionIsEmptyOrNull(Collection c) {
        return (collectionSize(c) == 0);
    }

    public static int collectionSize(Collection c) {
        if (c == null) return 0;
        return c.size();
    }

    public static boolean hasProperty(String key, Properties props) {
        return (props.getProperty(key) != null);
    }

    public static List<String> getIndexedPropertyValues(String key, Properties props) {
        List<String> values = new ArrayList<String>();

        for (int i = 0; hasProperty((key + i), props); i++) {
            values.add(props.getProperty(key + i));
        }
        return values;
    }

    // convenience method for comparing string values
    public static boolean shouldReplace(String val1, String val2) {
        return (stringExists(val1) && !stringExists(val2));
    }

    // only if one of the Strings should replace the other, return that string
    public static String betterValue(String val1, String val2) {
        if (val1 != null && val2 != null && val1.trim().equals(val2.trim())) {
            // return shorter string (less whitespace)
            if (val1.length() < val2.length()) return val1;
            else return val2;
        }
        if (!stringExists(val2)) return val1;
        if (!stringExists(val1)) return val2;
        return null;
    }

    public static boolean doubleExists(Double val) {
        return (val != null && val != 0.0);
    }

    public static boolean shouldReplace(Double val1, Double val2) {
        return (doubleExists(val1) && !doubleExists(val2));
    }

    public static boolean intExists(int val) {
        // this feels so wrong... so wrong!!!
        return (val != 0 && val != -1);
    }

    public static boolean integerExists(Integer val) {
        return (val != null && intExists(val));
    }

    public static void writeToFile(String data, String absolutePath)
    throws FileNotFoundException {
        File file = new File(absolutePath);

        try {
            FileOutputStream fos = new FileOutputStream(file);
            OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
            writer.write(data);
            writer.flush();
            writer.close();
            fos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String readFromFile(String path)
    throws FileNotFoundException, IOException {
        FileInputStream inputStream = new FileInputStream(path);
        String readData = IOUtils.toString(inputStream);

        inputStream.close();
        return readData;
    }

    public static <T> List<T> combineListsInPlace(List<T> list, List<T> otherList) {
        if (list == null) {
            list = otherList;
            return list;
        }
        for (T item : otherList) {
            if (!list.contains(item)) list.add(item);
        }
        return list;
    }

    // kinda annoying, just for cases where we've (erroneously IMO) typed things as specifically ArrayLists
    public static <T> ArrayList<T> combineArrayListsInPlace(ArrayList<T> list,
        ArrayList<T> otherList) {
        if (list == null) {
            list = otherList;
            return list;
        }
        for (T item : otherList) {
            if (!list.contains(item)) list.add(item);
        }
        return list;
    }

    public static boolean isValidDecimalLatitude(Double lat) {
        if (lat == null) return false;
        return ((lat >= -90.0) && (lat <= 90.0));
    }

    public static boolean isValidDecimalLongitude(Double lon) {
        if (lon == null) return false;
        return ((lon >= -180.0) && (lon <= 180.0));
    }

    public static int count(Iterator it) {
        int num = 0;

        while (it.hasNext()) {
            Object elem = it.next();
            num++;
        }
        return num;
    }

    // replaces wrong-slashes with right-slashes
    public static String windowsFileStringToLinux(String windowsFileString) {
        return windowsFileString.replaceAll("\\\\", "/");
    }

    public static boolean fileExists(String filepath) {
        File f = new File(filepath);

        return (f.exists() && !f.isDirectory());
    }

    // handles fuzzy case where url?key=value wants to test that 'key' is "set" (namely exists *and* is not explicitely "false")
    public static boolean requestParameterSet(String value) {
        if (value == null) return false;
        value = value.toLowerCase();
        return !(value.equals("false") || value.equals("f") || value.equals("0"));
    }

    // a slightly(!) more generic(!?) version of above
    public static boolean booleanNotFalse(String value) {
        return requestParameterSet(value);
    }

    public static String basicSanitize(String input) {
        return sanitizeUserInput(input);
    }

    public static String sanitizeUserInput(String input) {
        if (input == null) return null;
        final String[] forbidden = {
            "'", "<%", "<s", "<i", "alert(", "prompt(", "confirm(", "\"", "</", "&#38;", "&#39;",
                "&#40;", "&#41;", "&#60;", "&#62;", "&#34;", "var ", ">var", "var+", "href=", ".*",
                "src=", "%20", "\">", "()", ");", ")&", "$(", "${", "new+", "%3C", "%3E", "%27",
                "%22", "><", "=\"", "document.get", "document.add", "document.cookie", "document[",
                "javascript", ":edit", "&quot", "\\u", "String.from",
        };
        for (String forbid : forbidden) {
            if (StringUtils.containsIgnoreCase(input, forbid)) {
                input = input.replaceAll("(?i)" + Pattern.quote(forbid), "");
            }
        }
        return input;
    }

    public static String joinStrings(List<String> strings) {
        return joinStrings(strings, ", ");
    }

    public static String joinStrings(List<String> strings, String seperator) {
        if (strings.size() == 0) return "";
        if (strings.size() == 1) return strings.get(0);
        StringBuffer strBuff = new StringBuffer();
        strBuff.append(strings.get(0));
        for (int i = 1; i < strings.size(); i++) {
            strBuff.append(seperator + strings.get(i));
        }
        return strBuff.toString();
    }

    // this can be used to scrub user-provided paths to disallow "questionable" entries that might be
    // trying to access parts of the filesystem they should not have access to.
    // currently scrubs: parent directories in path (../) and hidden "dot" files (.config etc)
    // present behavior will return null if unsafe substrings are found
    // NOTE: a leading '/' is stripped off to be safe.  this puts the onus on the user of this method
    // to prepend a '/' when using at a subpath, e.g. "/my/absolute/" + safePath(userProvidedString)
    // but hopefully will prevent absolute paths accidentally getting used.
    public static String safePath(String path) {
        if (path == null) return null;
        if (new File(path).getName().startsWith(".")) {
            System.out.println("WARNING: safePath(" + path + ") detected hidden dot file; failing");
            return null;
        }
        if (path.indexOf("..") > -1) {
            System.out.println("WARNING: safePath(" + path + ") detected '..' in path; failing");
            return null;
        }
        if (path.indexOf("/") == 0) return path.substring(1);
        return path;
    }

    public static <KeyType, ValType> void addToMultimap(Map<KeyType, Set<ValType> > multimap,
        KeyType key, ValType val) {
        if (!multimap.containsKey(key)) multimap.put(key, new HashSet<ValType>());
        multimap.get(key).add(val);
    }

    // this is a fast hash
    // https://lz4.github.io/lz4-java/1.5.1/docs/net/jpountz/xxhash/package-summary.html
    public static final int XXHASH_SEED = 0x2170beef; // just needs to be consistent
    public static int xxhash(byte[] barr) {
        XXHashFactory factory = XXHashFactory.fastestInstance();
        XXHash32 hash32 = factory.hash32();

        return hash32.hash(barr, 0, barr.length, XXHASH_SEED);
    }

    public static int xxhash(String s)
    throws IOException {
        if (s == null) throw new IOException("xxhash() passed null string");
        return xxhash(s.getBytes("UTF-8"));
    }

    public static int xxhash(File f)
    throws IOException {
        if (f == null) throw new IOException("xxhash() passed null file");
        XXHashFactory factory = XXHashFactory.fastestInstance();
        StreamingXXHash32 hash32 = factory.newStreamingHash32(XXHASH_SEED);
        InputStream inputStream = new FileInputStream(f);
        byte[] buf = new byte[8192];
        int read;
        while ((read = inputStream.read(buf)) != -1) {
            hash32.update(buf, 0, read);
        }
        return hash32.getValue();
    }

    public static boolean areFilesIdentical(File f1, File f2)
    throws IOException {
        if ((f1 == null) || (f2 == null)) return false;
        if (f1.length() != f2.length()) return false; // easy!
        int h1 = xxhash(f1);
        int h2 = xxhash(f2);
        return (h1 == h2);
    }

    // value is hex number... first part is length, second part (8char) xxhash; max = 24char
    public static String fileContentHash(File f)
    throws IOException {
        if (f == null) throw new IOException("fileContentHash() passed null file");
        return Long.toHexString(f.length()) + Integer.toHexString(xxhash(f));
    }

    public static boolean containsFilename(Set<File> files, String filename) {
        if (files == null) return false;
        for (File f : files) {
            if (f.getName().equals(filename)) return true;
        }
        return false;
    }

    public static boolean containsFilename(List<File> files, String filename) {
        return containsFilename(new HashSet<File>(files), filename);
    }

    // this is a good "quick first pass" at checking an uploaded file for use as MediaAsset
    // but does not 100% guarantee creation of a MediaAsset will succeed
    // it can process 1000 files in < 1 second
    public static boolean fastFileValidation(File file) {
        if (file == null) return false;
        if (file.length() < 1000) return false;
        try {
            // probeContentType() sux cuz it uses extension first before actually probing :(
            // String type = Files.probeContentType(file.toPath());
            // so we just commit to another library, as Tika seems legit at using content of file
            Tika tika = new Tika();
            String type = tika.detect(file);
            if (type == null) return false; // seems to return null for file not exists
            if (type.startsWith("video/")) return true;
            String[] accepted = { "jpeg", "png", "bmp", "gif" };
            for (String subType : accepted) {
                if (type.equals("image/" + subType)) return true;
            }
            System.out.println("fastFileValidation() failed on " + file + ": " + type);
            return false;
        } catch (Exception ex) {
            System.out.println("fastFileValidation() failed on " + file + ": " + ex);
            // ex.printStackTrace(); // seems overkill
        }
        return false;
    }

    // h/t StackOverflow user erickson https://stackoverflow.com/questions/740299/how-do-i-sort-a-set-to-a-list-in-java
    public static <T extends Comparable<? super T> > List<T> asSortedList(Collection<T> c) {
        List<T> list = new ArrayList<T>(c);

        java.util.Collections.sort(list);
        return list;
    }

    // these are for debugging/timing purposes
    public static void mark(String msg) {
        mark(msg, -1);
    }

    public static void mark(String msg, long startTime) {
        long t = System.currentTimeMillis();
        DateTime now = new DateTime();
        String diff = "0";

        if (startTime > 0l) diff = Long.toString(t - startTime);
        System.out.println(now.toString() + " MARK [" + msg + "," + t + "," + diff + "]");
    }

    public static String scrubURL(URL u) {
        if (u == null) return (String)null;
        return u.toString().replace("#", "%23");
    }

    public static JSONObject copy(JSONObject original) {
        if (original == null) return null;
        return new JSONObject(original, JSONObject.getNames(original));
    }

    // changes original in-place by folding source into original
    // overwrite means source will squash like-keyed values in original
    public static void merge(JSONObject original, JSONObject source, boolean overwrite) {
        if ((original == null) || (source == null)) return;
        for (String key : source.keySet()) {
            if (original.has(key) && !overwrite) continue;
            original.put(key, source.get(key));
        }
    }

    public static void merge(JSONObject original, JSONObject source) {
        merge(original, source, true);
    }

    /**
     * Generates and returns version long value using 'modified', returns 0 for now if the 'modified' property
     * does not have any value or can't be converted to Long.
     *
     * @param modified String value
     * @return Version long value generated using modified string
     */
    public static long getVersionFromModified(final String modified) {
        if (!stringExists(modified)) return 0;
        try {
            String iso8601 = roundISO8601toMillis(getISO8601Date(modified));
            if (iso8601 == null) return 0;
            // switching from DateTimeFormatter to DateTime here because the math seems to line up with how psql does it -jon
            return new DateTime(iso8601).getMillis();
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    public static String roundISO8601toMillis(final String input) {
        if ((input == null) || (input.length() < 24)) return input;
        try {
            float seconds = Float.parseFloat(input.substring(17)) + 0.0005f;
            return input.substring(0, 17) + String.valueOf(seconds).substring(0, 6);
        } catch (Exception ex) {}
        return input;
    }

    // note: this respect user.receiveEmails - you have been warned
    public static Set<String> getUserEmailAddresses(List<User> users) {
        Set<String> addrs = new HashSet<String>();

        if (users == null) return addrs;
        for (User user : users) {
            if (user.getReceiveEmails() && !stringIsEmptyOrNull(user.getEmailAddress()))
                addrs.add(user.getEmailAddress());
        }
        return addrs;
    }

    public static String getISO8601Date(final String date) {
        if (date == null) return null;
        String iso8601 = date.replace(" ", "T");
        if (iso8601.length() == 10) iso8601 += "T00:00:00";
        // TODO: better testing of date's string format (or transition to date format support?)
        if (iso8601.length() < 16) return null;
        if (iso8601.length() == 16) iso8601 += 'Z';
        return iso8601;
    }

    // from issue #1227, there are a couple ways to derive a list of valid countries (e.g. for validating
    // bulk import data), including some based on CommonConfiguration. for now we are using a canned list
    // but might be adjusted later to allow customization
    public static List<String> getCountries() {
        List<String> cnames = new ArrayList<String>();

        for (String code : Locale.getISOCountries()) {
            Locale obj = new Locale("", code);
            cnames.add(obj.getDisplayCountry());
        }
        Collections.sort(cnames);
        return cnames;
    }

    // TODO could be read from config in future, if desired
    public static List<String> getIdentificationRemarksValues() {
        return Arrays.asList("Unmatched first encounter", "Visual inspection", "Pattern match");
    }

    // this is meant to be a temporary replacement for an old library which we are dropping.
    // it is only used in a couple ancient jsps related to spotmapping
    // for details: https://github.com/WildMeOrg/Wildbook/issues/1346#issuecomment-3712415060
    // TODO this can be dropped when those jsps are no longer needed
    public static java.awt.Dimension hackSanselanGetImageSize(File file, String filename)
    throws IOException {
        java.awt.Dimension dim = new java.awt.Dimension();
        JSONObject attr = org.ecocean.media.AssetStore.extractMetadataAttributes(file);
        // optInt will truncate values that are floats
        dim.setSize(attr.optInt("width"), attr.optInt("height"));
        return dim;
    }

}
