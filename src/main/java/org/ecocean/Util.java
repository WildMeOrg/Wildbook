package org.ecocean;

//import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.UUID;
import org.json.JSONObject;
import org.json.JSONException;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.text.DateFormat;
import java.util.TimeZone;

import org.joda.time.DateTime;
import org.joda.time.LocalDateTime;


//EXIF-related imports
import java.io.File;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import com.drew.imaging.jpeg.JpegMetadataReader;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;
import java.util.Iterator;
import java.util.Map;
import org.apache.commons.io.IOUtils;

//import javax.jdo.JDOException;
//import javax.jdo.JDOHelper;
import javax.jdo.Query;
//import javax.jdo.PersistenceManagerFactory;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;


import org.ecocean.tag.MetalTag;
import org.ecocean.*;


//use Point2D to represent cached GPS coordinates
import com.reijns.I3S.Point2D;

public class Util {

  //Measurement static values
  private static final String MEASUREMENT = "measurement";
  private static final String BIOLOGICALMEASUREMENT = "biologicalMeasurementType";
  private static final String UNITS = MEASUREMENT + "Units";
  private static final String BIOLOGICALMEASUREMENTUNITS = BIOLOGICALMEASUREMENT.replaceAll("Type", "Units");
  private static final String METAL_TAG_LOCATION = "metalTagLocation";
  private static final String SATELLITE_TAG_NAME = "satelliteTagName";
  private static final String VESSEL = "vessel";

  //GPS coordinate caching for Encounter Search and Individual Search
  private static ArrayList<Point2D> coords;

  public static List<MeasurementDesc> findMeasurementDescs(String langCode,String context) {
    List<MeasurementDesc> list = new ArrayList<MeasurementDesc>();
    List<String> types = CommonConfiguration.getIndexedPropertyValues(MEASUREMENT,context);
    if (types.size() > 0) {
      List<String> units = CommonConfiguration.getIndexedPropertyValues(UNITS,context);
      for (int i = 0; i < types.size() && i < units.size(); i++) {
        String type = types.get(i);
        String unit = units.get(i);
        String typeLabel = findLabel(type, langCode,context);
        String unitsLabel = findLabel(unit, langCode,context);
        list.add(new MeasurementDesc(type, typeLabel, unit, unitsLabel));
      }
    }
    return list;
  }
  
  public static ArrayList<String> findVesselNames(String langCode,String context) {
    ArrayList<String> list = new ArrayList<String>();
    List<String> types = CommonConfiguration.getIndexedPropertyValues(VESSEL,context);
    if (types.size() > 0) {
      for (int i = 0; i < types.size(); i++) {
        String type = types.get(i);
        list.add(type);
      }
    }
    return list;
  }

  public static List<MeasurementDesc> findBiologicalMeasurementDescs(String langCode, String context) {
    List<MeasurementDesc> list = new ArrayList<MeasurementDesc>();
    List<String> types = CommonConfiguration.getIndexedPropertyValues(BIOLOGICALMEASUREMENT,context);
    if (types.size() > 0) {
      List<String> units = CommonConfiguration.getIndexedPropertyValues(BIOLOGICALMEASUREMENTUNITS,context);
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

	//a hook to UUID generator
	public static String generateUUID() {
		return UUID.randomUUID().toString();
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
   * Returns a map of sampling protocols, where the key is the name of protocol, and the
   * value is the user-friendly, localized label.
   * @param langCode
   * @return
   */
  public static List<OptionDesc> findSamplingProtocols(String langCode,String context) {
    List<String> values = CommonConfiguration.getIndexedPropertyValues("samplingProtocol",context);
    List<OptionDesc> list = new ArrayList<OptionDesc>();

    /*
    for (String key : values) {
      String label = findLabel(key, langCode,context);
      list.add(new OptionDesc(key, label));
    }
    */
    int valuesSize=values.size();
    for(int i=0;i<valuesSize;i++){
      String key="samplingProtocol"+i;
      String label = findLabel(key, langCode,context);
      list.add(new OptionDesc(key, label));

    }
    return list;
  }

  public static String getLocalizedSamplingProtocol(String samplingProtocol, String langCode, String context) {
    if (samplingProtocol != null) {
      List<OptionDesc> samplingProtocols = findSamplingProtocols(langCode,context);
      for (OptionDesc optionDesc : samplingProtocols) {
        if (optionDesc.name.equals(samplingProtocol)) {
          return optionDesc.display;
        }
      }
    }
    return null;
  }

  public static List<MetalTagDesc> findMetalTagDescs(String langCode,String context) {
    List<String> metalTagLocations = CommonConfiguration.getIndexedPropertyValues(METAL_TAG_LOCATION,context);
    List<MetalTagDesc> list = new ArrayList<MetalTagDesc>();
    for (String location : metalTagLocations) {
      String locationLabel = findLabel(location, langCode,context);
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
    return CommonConfiguration.getIndexedPropertyValues(SATELLITE_TAG_NAME,context);
  }

  private static String findLabel(String key, String langCode, String context) {

    //System.out.println("Trying to find key: "+key+" with langCode "+langCode);

    /*
    Locale locale = Locale.US;
    if (langCode != null) {
      locale = new Locale(langCode);
    }
    try {
      ResourceBundle bundle = ResourceBundle.getBundle("bundles.commonConfigurationLabels", locale);
      return bundle.getString(key + ".label");
    }
    catch (MissingResourceException ex) {
      System.out.println("Error finding bundle or key for key: " + key);
    }
    return key;*/

    Properties myProps = ShepherdProperties.getProperties("commonConfigurationLabels.properties", langCode, context);
    return myProps.getProperty(key+".label");


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

  public synchronized static ArrayList<Point2D> getCachedGPSCoordinates(boolean refresh,String context) {
    Shepherd myShepherd=new Shepherd(context);
    myShepherd.setAction("Util.class.getCached GPSCoordinates");
    myShepherd.beginDBTransaction();
    try {
      if ((coords == null)||(refresh)) {

        //execute the JDOQL


        Query query=myShepherd.getPM().newQuery("SELECT FROM org.ecocean.Encounter WHERE decimalLatitude != null && decimalLongitude != null");
        Collection<Encounter> c = (Collection<Encounter>) (query.execute());
        ArrayList<Encounter> encs=new ArrayList<Encounter>(c);
        int encsSize=encs.size();

        //populate coords
        coords=new ArrayList<Point2D>(encsSize);
        for(int i=0;i<encsSize;i++){
          Encounter e=encs.get(i);
            // updating this to handle empty (null) values
            //  note: forcing this to an int was legacy code.  seems "bad" (loss of accuracy); but doing anyway!  2017-03-16  -jon   FIXME?
            if ((e.getDecimalLatitudeAsDouble() == null) || (e.getDecimalLongitudeAsDouble() == null)) continue;
            int lat = (int)Math.round(e.getDecimalLatitudeAsDouble());
            int longie = (int)Math.round(e.getDecimalLongitudeAsDouble());
          //int lat=(int)e.getDecimalLatitudeAsDouble();
          //int longie=(int)e.getDecimalLongitudeAsDouble();
          Point2D myPoint=new Point2D(lat,longie);
          if(!coords.contains(myPoint)){
            coords.add(myPoint);
          }
        }

        query.closeAll();

      }
      myShepherd.rollbackDBTransaction();
      myShepherd.closeDBTransaction();
      return coords;
    }
    catch (Exception jdo) {
      jdo.printStackTrace();
      System.out.println("I hit an error trying to populate the cached GPS coordinates in Util.java.");
      myShepherd.rollbackDBTransaction();
      myShepherd.closeDBTransaction();
      return new ArrayList<Point2D>();
    }
  }

  public static String getEXIFDataFromJPEGAsHTML(File exifImage){
    StringBuffer results=new StringBuffer("<p>File not found on file system. No EXIF data available.</p><p>Looking in: "+exifImage.getAbsolutePath()+"</p>");
    if(exifImage.exists()){
      results=new StringBuffer();
      InputStream inp=null;

      try{
          inp = new FileInputStream(exifImage);
          Metadata metadata = JpegMetadataReader.readMetadata(exifImage);
          // iterate through metadata directories
          Iterator directories = metadata.getDirectories().iterator();
          while (directories.hasNext()) {
              Directory directory = (Directory) directories.next();
              // iterate through tags and print to System.out
              Iterator tags = directory.getTags().iterator();
              while (tags.hasNext()) {
                Tag tag = (Tag) tags.next();
                results.append(tag.toString()+"<br/>");
              }
          }
          inp.close();
        } //end try
        catch(Exception e){
          results=null;
          results=new StringBuffer("<p>Cannot read metadata for this file.</p>");
        }
      finally {
        IOUtils.closeQuietly(inp);
      }


    } //end if
    return results.toString();

  }


    //got sick of having to concat these strings with a space in the middle.
    // TODO: someday make a Taxonomy class for storing/processing this stuff right! (or find the wheel someone already invented!!)
    public static String taxonomyString(String genus, String species) {
        if ((genus != null) && (species != null)) return genus + " " + species;
        if (genus != null) return genus;
        if (species != null) return species;
        return null;
    }


    //a generic version of our uuid-dir-structure-creating algorithm -- adjust as needed!?
    // TODO check for incoming slashes and similar weirdness
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
        if ((m[0] == 1) && (m[1] == 0) && (m[2] == 0) && (m[3] == 1) && (m[4] == 0) && (m[5] == 0)) return false;
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

    //this basically just swallows exceptions in parsing and returns a null if failure
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

    public static org.datanucleus.api.rest.orgjson.JSONObject stringToDatanucleusJSONObject(String s) {
      org.datanucleus.api.rest.orgjson.JSONObject j = null;
      if (s == null) return j;
      try {
          j = new org.datanucleus.api.rest.orgjson.JSONObject(s);
      } catch (org.datanucleus.api.rest.orgjson.JSONException je) {
          System.out.println("error parsing json string (" + s + "): " + je.toString());
      }
      return j;
    }

    public static org.datanucleus.api.rest.orgjson.JSONArray concatJsonArrayInPlace(org.datanucleus.api.rest.orgjson.JSONArray toBeReturned, org.datanucleus.api.rest.orgjson.JSONArray toBeAdded) throws org.datanucleus.api.rest.orgjson.JSONException {
      for (int i=0; i<toBeAdded.length(); i++) {
        toBeReturned.put(toBeAdded.get(i));
      }
      return toBeReturned;
    }

    /**
     * Useful for some UI stuff -db
     * From stackOverflow http://stackoverflow.com/a/7085652
     **/
    public static org.datanucleus.api.rest.orgjson.JSONObject requestParamsToJSON(HttpServletRequest req) throws org.datanucleus.api.rest.orgjson.JSONException {
      org.datanucleus.api.rest.orgjson.JSONObject jsonObj = new org.datanucleus.api.rest.orgjson.JSONObject();
      Map<String,String[]> params = req.getParameterMap();
      for (Map.Entry<String,String[]> entry : params.entrySet()) {
        String v[] = entry.getValue();
        Object o = (v.length == 1) ? v[0] : v;
        jsonObj.put(entry.getKey(), o);
      }
      return jsonObj;
    }

    // transforms a string such as "90.1334" or "46″ N 79°" into a decimal value
    // TODO: parse second type of input string
    public static Double getDecimalCoordFromString(String latOrLong) {

      try {
        return Double.valueOf(latOrLong);
      }
      catch (NumberFormatException nfe) {
        System.out.println("ERROR: could not parse decimal coordinate from string "+latOrLong);
        nfe.printStackTrace();
      }
      return null;

    }

/////GPS Longitude: "-69.0° 22.0' 45.62999999998169"",
    public static Double latlonDMStoDD(String dms) {
        String[] d = dms.split(" +");
        if (d.length < 1) return null;
//System.out.println("latlonDMStoDD(" + dms + ") --> " + d[0] + "/" + d[1] + "/" + d[2]);
        Double dd = null;
        try {
            dd = Double.valueOf(d[0].substring(0, d[0].length() - 1));
            Double m = 0.0;
            Double s = 0.0;
            if (d.length > 1) m = Double.valueOf(d[1].substring(0, d[1].length() - 1));
            if (d.length > 2) s = Double.valueOf(d[2].substring(0, d[2].length() - 1));
            dd = Math.signum(dd) * (Math.abs(dd) + ((m * 60) + s) / (60*60));
//System.out.println("  --> " + dd + " deg, " + m + " min, " + s + " sec => " + dd);
            return dd;

        } catch (NumberFormatException nfe) {
            return null;
        }
    }

    //whatever a string of this should look like.  apparently we want a string version on encounter for example. (?)
    public static String decimalLatLonToString(Double ll) {
        if (ll == null) return null;
        return ll.toString();
    }


    //   h/t  https://www.mkyong.com/regular-expressions/how-to-validate-email-address-with-regular-expression/
    public static String validEmailRegexPattern() {
        //return "^[_A-Za-z0-9-\\+]+(\\.[_A-Za-z0-9-]+)*@[A-Za-z0-9-]+(\\.[A-Za-z0-9]+)*(\\.[A-Za-z]{2,})$";  //THIS FAILED on sito.org+foo@gmail.com !!
        return "^[_A-Za-z0-9-\\+\\.]+(\\.[_A-Za-z0-9-]+)*@[A-Za-z0-9-]+(\\.[A-Za-z0-9]+)*(\\.[A-Za-z]{2,})$";
    }

    public static boolean isValidEmailAddress(String email) {
        if (email == null) return false;
        java.util.regex.Pattern patt = java.util.regex.Pattern.compile(validEmailRegexPattern());
        java.util.regex.Matcher matcher = patt.matcher(email);
        return matcher.matches();
    }

    // e.g. you have collectionSize = 13 items you want displayed in sections with 3 per section.
    public static int getNumSections(int collectionSize, int itemsPerSection) {
      return (collectionSize - 1)/itemsPerSection + 1;
    }

    public static String prettyPrintDateTime(DateTime dt) {
      System.out.println("prettyPrintDateTime:");
      System.out.println("  dt.hourOfDay = "+dt.hourOfDay().get());
      boolean isOnlyDate = dateTimeIsOnlyDate(dt);
      String currentToString = dt.toString();
      if (isOnlyDate) {
        currentToString = currentToString.split("T")[0];
      }
      return (currentToString);
    }

    public static boolean dateTimeIsOnlyDate(DateTime dt) {
      try {
        return (dt.millisOfDay().get()==0);
      } catch (Exception e) {
        return false;
      }
    }

    public static String capitolizeFirstLetterOnly(String str) {
      String lower = str.toLowerCase();
      if (lower.length()<=1) return (lower.toUpperCase());
      return (lower.substring(0,1).toUpperCase() + lower.substring(1));
    }

    public static String capitolizeFirstLetter(String str) {
      if (str.length()<=1) return (str.toUpperCase());
      return (str.substring(0,1).toUpperCase() + str.substring(1));
    }


    public static boolean requestHasVal(HttpServletRequest request, String paramName) {
      return ((request.getParameter(paramName)!=null) && (!request.getParameter(paramName).equals("")));
    }

    public static String addToJDOFilter(String constraint, String filter, String origFilter) {
      if (filter.equals(origFilter)) return (filter + constraint);
      else return (filter + " && " + constraint);
    }

    public static String jdoStringContainsConstraint(String fieldName, String containsThis) {
      return "("+fieldName+".indexOf('"+containsThis+"') != -1)";
    }

    public static String jdoStringContainsConstraint(String fieldName, String containsThis, boolean toLowerCase) {
      if (!toLowerCase) return jdoStringContainsConstraint(fieldName, containsThis);
      return "("+fieldName+".toLowerCase().indexOf('"+containsThis.toLowerCase()+"') != -1)";
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
      return (str!=null && !str.equals(""));
    }


    public static boolean hasProperty(String key, Properties props) {
      return (props.getProperty(key) != null);
    }

    // given "animalType"
    public static List<String> getIndexedPropertyValues(String key, Properties props) {
      List<String> values = new ArrayList<String>();
      for (int i=0; hasProperty((key+i), props); i++) {
        values.add(props.getProperty(key+i));
      }
      return values;
    }

    public static void writeToFile(String data, String path) throws FileNotFoundException {
      PrintWriter out = new PrintWriter(path);
      out.println(data);
      out.close();
    }

    public static String readFromFile(String path) throws FileNotFoundException, IOException {
      FileInputStream inputStream = new FileInputStream(path);
      String readData = IOUtils.toString(inputStream);
      return readData;
    }

    public static String convertEpochTimeToHumanReadable (long epochTime){
      Date date = new Date(epochTime);
          DateFormat format = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
          format.setTimeZone(TimeZone.getTimeZone("Etc/GMT"));
          String formatted = format.format(date);
          formatted = format.format(date);
          return formatted.toString();
    }
    
    public static String basicSanitize(String input) {
      String sanitized = null;
      if (input!=null) {
        sanitized = input;
        sanitized = input.replace(":", "");
        sanitized = input.replace(";", "");
        sanitized = sanitized.replace("\"", "");
        sanitized = sanitized.replace("'", "");
        sanitized = sanitized.replace("(", "");
        sanitized = sanitized.replace(")", "");
        sanitized = sanitized.replace("*", "");
        sanitized = sanitized.replace("%", "");        
      }
      return sanitized;
    }
}


