package org.ecocean;

import org.ecocean.media.*;

import org.joda.time.DateTime;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Properties;
import java.util.Vector;
import java.util.Arrays;
import java.io.*;
import org.ecocean.security.Collaboration;
import org.ecocean.media.MediaAsset;
import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.Comparator;


import org.datanucleus.api.rest.orgjson.JSONObject;
import org.datanucleus.api.rest.orgjson.JSONException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.xml.sax.InputSource;
import java.io.StringReader;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 *
 *  Clusters events with datetime + location info into groups, e.g.
 *  "I have a bunch of MediaAssets and need to build occurrences"
 *
 *  This class could basically be a set of static utility functions off of Occurrence
 *  Keeping separate for cleanliness sake right now.
 *
 **/

public class Cluster {

  public static List<Occurrence> makeNOccurrences(int n, List<MediaAsset> assets, Shepherd myShepherd) {
    ArrayList<Occurrence> occurrences = new ArrayList<Occurrence>();
    double hop_size = assets.size() / (double) n;
    for (int i=0; i<n; i++) {
      int start_index = (int) Math.round(i*hop_size);
      int end_index = (int) Math.round((i+1)*hop_size);
      if (end_index-start_index < 1) continue;
      List<MediaAsset> subList = assets.subList(start_index, end_index);
      Occurrence occ = new Occurrence(subList, myShepherd);
      if (myShepherd!= null) myShepherd.storeNewOccurrence(occ);
      occurrences.add(occ);
    }
    return occurrences;
  }

  public static List<Occurrence> defaultCluster(List<MediaAsset> assets, Shepherd myShepherd) throws IOException {
    return makeNOccurrences(100, assets, myShepherd);
    //return runJonsClusterer(assets, myShepherd);
  }


    public static List<Occurrence> fromSmartXml(File smartXmlFile, List<MediaAsset> assets) throws IOException {
        Document xdoc = null;
        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            xdoc = dBuilder.parse(smartXmlFile);
            xdoc.getDocumentElement().normalize();
        } catch (Exception ex) {
            throw new IOException("could not open/parse SMART xml file: " + ex.toString());
        }


        List<MediaAsset> sortedAssets = sortAssets(assets);
//for (MediaAsset ma : sortedAssets) { System.out.println(ma.getFilename()); }
        List<Occurrence> occs = new ArrayList<Occurrence>();
        //we need to build the list of photoOffsets as we go thru xml, then dish out assets at the very end accordingly
        List<Integer> photoOffsets = new ArrayList<Integer>();

        NodeList dlist = xdoc.getDocumentElement().getElementsByTagName("days");
        if (dlist.getLength() < 1) return occs;
        for (int i = 0 ; i < dlist.getLength() ; i++) {
            Node dnode = dlist.item(i);
            if (dnode.getNodeType() != Node.ELEMENT_NODE) continue;
            Element del = (Element)dnode;
            String dateAttribute = del.getAttribute("date");  //note: also have startTime, endTime, restMinutes
System.out.println("dateAttribute -> " + dateAttribute);
            NodeList wlist = del.getElementsByTagName("waypoints");
            if (wlist.getLength() < 1) continue;
            for (int w = 0 ; w < wlist.getLength() ; w++) {
                Node wnode = wlist.item(w);
                if (wnode.getNodeType() != Node.ELEMENT_NODE) continue;
                Element wel = (Element)wnode;   // <waypoints id="4" x="37.4676312262241" y="0.278523922074831" time="11:00:40">
///////TODO verify that this produces correct output

                String x = wel.getAttribute("x");
                Double decimalLongitude = strToDoubleNoExceptions(x);

                String y = wel.getAttribute("y");
                Double decimalLatitude = strToDoubleNoExceptions(y);

                String timeStr = wel.getAttribute("time");
                DateTime dateTime = null;
                try {
                  dateTime = DateTime.parse(timeStr);
                } catch (Exception e) {
                }


                NodeList olist = wel.getElementsByTagName("observations");   //these will map to separate Occurrences, so kind of "inherit" the above from waypoint. :/
                if (olist.getLength() < 1) continue;
                for (int o = 0 ; o < olist.getLength() ; o++) {
                    Node onode = olist.item(o);
                    if (onode.getNodeType() != Node.ELEMENT_NODE) continue;
                    Element oel = (Element)onode;
                    if (!"animals.liveanimals.".equals(oel.getAttribute("categoryKey"))) {
                        System.out.println(" - skipping observation with non-animal categoryKey=" + oel.getAttribute("categoryKey") + " in waypoint id=" + wel.getAttribute("id"));
                        continue;
                    }
                    System.out.println(" - valid observation found at waypoint id=" + wel.getAttribute("id"));

/* here is an example of stuff we have in an animal observation.  not sure if this is exhaustive??
    a whole bunch of these were implemented for lewa in "previous ibeis branch" on the occurrence, such as:
        occ.setNumBachMales(int);   see Occurrence.java for those... which will need to be ported... :/   TODO

                <observations categoryKey="animals.liveanimals.">
                    <attributes attributeKey="species">
                        <itemKey>chordata_rl.mammalia_rl.perissodactyla_rl.equidae_rl.equus_rl.equusgrevyi_rl7950.</itemKey>
                    </attributes>
                    <attributes attributeKey="habitat">
                        <itemKey>openwoodland</itemKey>
                    </attributes>
                    <attributes attributeKey="groupsize">
                        <dValue>6.0</dValue>
                    </attributes>
                    <attributes attributeKey="noofbm">
                        <dValue>6.0</dValue>
                    </attributes>
                    <attributes attributeKey="nooftm">
                        <dValue>1.0</dValue>
                    </attributes>
                    <attributes attributeKey="distancem">
                        <dValue>51.0</dValue>
                    </attributes>
                    <attributes attributeKey="noofnlf">
                        <dValue>7.0</dValue>
                    </attributes>
                    <attributes attributeKey="nooflf">
                        <dValue>2.0</dValue>
                    </attributes>
                    <attributes attributeKey="numberof612monthsfemales">
                        <dValue>2.0</dValue>
                    </attributes>
                    <attributes attributeKey="bearing">
                        <dValue>30.0</dValue>
                    </attributes>
                    <attributes attributeKey="photonumber">
                        <sValue>1</sValue>
                    </attributes>
                </observations>

*/
                    Occurrence occ = new Occurrence();
                    occ.setOccurrenceID(Util.generateUUID());
                    occ.setDecimalLatitude(decimalLatitude);
                    occ.setDecimalLongitude(decimalLongitude);
                    occ.setDateTime(dateTime);

                    Integer photoOffset = null;
                    NodeList alist = oel.getElementsByTagName("attributes");
                    //TODO this is where we would build out the Occurrence
                    if (alist.getLength() > 0) {
                        for (int a = 0 ; a < alist.getLength() ; a++) {
                            Node anode = alist.item(a);
                            if (anode.getNodeType() != Node.ELEMENT_NODE) continue;
                            Element ael = (Element)anode;
                            String akey = ael.getAttribute("attributeKey");
                            if (akey == null) continue;  //seems bad?
                            System.out.println("   . attributeKey=" + akey);
                            switch (akey) {
                                case "photonumber":
                                    Integer pnum = getValueInteger(ael);
                                    if (pnum != null) {
                                        photoOffset = new Integer(pnum - 1);  //xml is 1-indexed
                                        if (photoOffset >= assets.size()) throw new RuntimeException("photonumber " + pnum + " > assets.size() " + assets.size());
                                    }
                                    break;
                                /* legacy zebra stuff    
                                case "habitat":
                                    occ.setHabitat(getValueString(ael));
                                    break;
                                case "bearing":
                                    occ.setBearing(getValueDouble(ael));
                                    break;
                                case "groupsize":
                                    occ.setGroupSize(getValueDoubleAsInt(ael));
                                    break;
                                case "noofbm":
                                    occ.setNumBachMales(getValueDoubleAsInt(ael));
                                    break;
                                case "nooftm":
                                    occ.setNumTerMales(getValueDoubleAsInt(ael));
                                    break;
                                case "distancem":
                                    occ.setDistance(getValueDouble(ael));
                                    break;
                                case "noofnlf":
                                    occ.setNumNonLactFemales(getValueDoubleAsInt(ael));
                                    break;
                                case "nooflf":
                                    occ.setNumLactFemales(getValueDoubleAsInt(ael));
                                    break;
                                case "numberof612monthsfemales":
                                    //
                                    break;
                                    */
                                    
                            }

                            //TODO ... do something with the value!
                        }
                    }

                    System.out.println(" - created Occurrence " + occ);
                    occs.add(occ);
                    photoOffsets.add(photoOffset);
                }
            }
        }

        //now we handle dolling out the MediaAssets
        for (int i = 0 ; i < occs.size() ; i++) {
            if (photoOffsets.get(i) == null) {
                System.out.println(" # Occurrence[" + i + "] did not have a photoOffset -- not assigning MediaAssets");
            } else {
                int toIndex = assets.size();  //subList second arg is *exclusive*
                //we find the next available photoOffset (if any)
                for (int j = i + 1 ; j < photoOffsets.size() ; j++) {
                    Integer jStart = photoOffsets.get(j);
                    if (jStart != null) {
                        toIndex = jStart;
                        j = photoOffsets.size() + 1;
                    }
                }
                occs.get(i).setAssets(assets.subList(photoOffsets.get(i), toIndex));
//System.out.println(" - Occurrence[" + i + "] assets -> [" + photoOffsets.get(i) + "," + toIndex + "] " + occs.get(i).getAssets());
System.out.println(" - Occurrence[" + i + "] assets -> [" + photoOffsets.get(i) + "," + toIndex + "]");
            }
            //persist the occ!
        }

/*
			val.put("time", el.getAttribute("time"));
			NodeList anlist = el.getElementsByTagName("attributes");
				if (an.getNodeType() != Node.ELEMENT_NODE) continue;
				Element ael = (Element) an;
				String aval = "";
				NodeList vl = ael.getElementsByTagName("dValue");  //numeric
				if (vl.getLength() < 1) vl = ael.getElementsByTagName("itemKey");  //string
				if (vl.getLength() > 0) aval = vl.item(0).getTextContent();
System.out.println(ael.getAttribute("attributeKey") + " -> " + aval);
*/



        return occs;
    }

    private static Double strToDoubleNoExceptions(String str) {
      Double out = null;
      try {
        out = Double.parseDouble(str);
      } catch (NullPointerException npe) {
      } catch (NumberFormatException nfe) {
      }
      return out;
    }

    private static Integer getValueInteger(Element el) {
        String val = getValue(el, "sValue");
        if (val == null) return null;
        try {
            return Integer.parseInt(val);
        } catch (Exception ex) {
            return null;
        }
    }
    private static Double getValueDouble(Element el) {
        String val = getValue(el, "dValue");
        if (val == null) return null;
        try {
            return Double.parseDouble(val);
        } catch (Exception ex) {
            return null;
        }
    }

    private static Integer getValueDoubleAsInt(Element el) {
        Double doubleVal = getValueDouble(el);
        if (doubleVal == null) return null;
        return ((Integer) doubleVal.intValue());
    }

    private static String getValueString(Element el) {
        return getValue(el, "itemKey");
    }
    private static String getValue(Element el, String name) {
        if (el == null) return null;
        NodeList list = el.getElementsByTagName(name);
        if ((list == null) || (list.getLength() < 1)) return null;
        return list.item(0).getTextContent();
    }

    private static List<MediaAsset> sortAssets(final List<MediaAsset> assets) {
        Collections.sort(assets, new Comparator<MediaAsset>() {
            public int compare(MediaAsset m1, MediaAsset m2) {
                if ((m1 == null) || (m2 == null)) return 0;
                String n1 = m1.getFilename();
                String n2 = m2.getFilename();
                if ((n1 == null) || (n2 == null)) return 0;
                return n1.compareTo(n2);
            }
        });
        return assets;
    }

  public static String buildCommand(List<MediaAsset> validAssets) {

    if (validAssets==null) {
      return null;
    }
    List<TimePlace> inputData = new ArrayList<TimePlace>();
    for (MediaAsset ma : validAssets) {
      inputData.add(new TimePlace(ma));
    }

    String command =  buildConsoleCommand(inputData);
    return command;

  }

  private static List<TimePlace> extractTimePlace(List<MediaAsset> assets) {
    List<TimePlace> inputData = new ArrayList<TimePlace>();
    for (MediaAsset ma : assets) {
      inputData.add(new TimePlace(ma));
    }
    return inputData;
  }

  public static List<List<MediaAsset>> groupAssetsByValidity(List<MediaAsset> assets) {

    if (assets==null) {
      return new ArrayList<List<MediaAsset>>();
    }


    ArrayList<MediaAsset> valid = new ArrayList<MediaAsset>();
    ArrayList<MediaAsset> invalid = new ArrayList<MediaAsset>();

    for (MediaAsset ma : assets) {
      if (ma.getDateTime()==null) { // lat or lon can be set to -1 (for null values) with no issue
        invalid.add(ma);
      } else {
        valid.add(ma);
      }
    }
    List<List<MediaAsset>> ret = new ArrayList<List<MediaAsset>>();
    ret.add(valid);
    ret.add(invalid);
    return ret;

  }


  public static String buildConsoleCommand(List<TimePlace> data) {
    if (data.size()==0) return null;

    StringBuilder lats = new StringBuilder("--lat ");
    StringBuilder lons = new StringBuilder("--lon ");
    StringBuilder secs = new StringBuilder("--sec ");

    for (TimePlace tp : data) {
      lats.append(tp.lat.toString()).append(" ");
      lons.append(tp.lon.toString()).append(" ");
      secs.append(String.valueOf(tp.getDateTimeInSeconds())).append(" ");
    }

    return ("python3 /var/lib/tomcat7/webapps/wildbook/config/occurrence_blackbox.py "+lats.toString()+lons.toString()+secs.toString());

  }


  public static List<List<MediaAsset>> groupAssetsByJonsOutput(List<MediaAsset> assets, int[] occIndices) throws IOException {
    if (occIndices.length!=assets.size()) throw new IOException("Unequal input sizes.");

    List<List<MediaAsset>> out = new ArrayList<List<MediaAsset>>();

    for (int i=0; i<occIndices.length; i++) {
      int currentNum = occIndices[i];
      while (out.size()<currentNum) {
        out.add(new ArrayList<MediaAsset>());
      }
      // -1 because Jon starts with 1, not 0
      out.get(currentNum-1).add(assets.get(i));
    }

    return out;

  }

  public static List<Occurrence> putAssetsInOccs(List<List<MediaAsset>> assetGroups, Shepherd myShepherd) {

    List<Occurrence> occs = new ArrayList<Occurrence>();
    for (List<MediaAsset> occGroup : assetGroups) {
      Occurrence occ = new Occurrence(occGroup, myShepherd);
      myShepherd.storeNewOccurrence(occ);
    }
    return occs;

  }

  public static List<Occurrence> runJonsClusterer(List<MediaAsset> assets, Shepherd myShepherd) throws IOException {
    List<List<MediaAsset>> groupedAssets = groupAssetsByValidity(assets);
    List<MediaAsset> validAssets = groupedAssets.get(0);
    List<MediaAsset> invalidAssets = groupedAssets.get(1);

    String command = buildCommand(validAssets);
    String output = runPythonCommand(command);
    int[] occNums = parseJonsOutput(output);

    List<List<MediaAsset>> occurrenceGroups = groupAssetsByJonsOutput(validAssets, occNums);

    List<Occurrence> occurrences = putAssetsInOccs(occurrenceGroups, myShepherd);

    // final occurrence contains all invalid (ie no timeplace) MediaAssets
    if (invalidAssets.size()>0) {
      Occurrence leftovers = new Occurrence(invalidAssets, myShepherd);
      occurrences.add(leftovers);
      myShepherd.storeNewOccurrence(leftovers);
    }

    return occurrences;

  }

  public static String runJonsScript(List<MediaAsset> assets, Shepherd myShepherd) throws IOException {
    StringWriter out = new StringWriter();


    if (assets==null) return "you've given me trash assets, idiot";

    List<List<MediaAsset>> groupedAssets = groupAssetsByValidity(assets);
    List<MediaAsset> validAssets = groupedAssets.get(0);
    List<MediaAsset> invalidAssets = groupedAssets.get(1);


    String command = buildCommand(validAssets);
    String output = runPythonCommand(command);
    int[] occNums = parseJonsOutput(output);


    List<List<MediaAsset>> occurrenceGroups = groupAssetsByJonsOutput(validAssets, occNums);

    /*
    for (List<MediaAsset> occGroup : occurrenceGroups) {
      Occurrence occ = new Occurrence(occGroup);
      myShepherd.storeNewOccurrence(occ);
    }*/

    for (int i=0; i<occurrenceGroups.size(); i++) {
      List<MediaAsset> occGroup = occurrenceGroups.get(i);
      Occurrence occ = new Occurrence(occGroup, myShepherd);
      myShepherd.storeNewOccurrence(occ);
      out.write(newline);
      out.write("Group " + String.valueOf(i+1));
      out.write(newline);
      out.write("OccurrenceID: "+occ.getOccurrenceID());
      for (MediaAsset ma : occurrenceGroups.get(i)) {
        out.write(newline);
        out.write(String.valueOf(ma.getId()));
      }
      out.write(newline);
    }

    String s = null;

    out.write(runPythonCommand(command));

    return out.toString();
  }


  private static String newline = "<br/>";

  public static int[] parseJonsOutput(String output) {
    if (output.indexOf('[')<0) {
      System.out.println("parseJonsOutput failed on output="+output);
      return (new int[0]);
    }
    String inner = output.substring(output.indexOf('[')+1, output.indexOf(']'));
    String[] intStrings = inner.split(",");

    int[] out = new int[intStrings.length];
    for (int i=0;i<intStrings.length;i++) {
      out[i] = (int) Integer.parseInt(intStrings[i].trim());
    }
    return out;
  }

  public static String runPythonCommand(String command) {
    StringWriter out = new StringWriter();
    String s = null;
    try {
      Process p = Runtime.getRuntime().exec(command);
      BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));
      BufferedReader stdError = new BufferedReader(new InputStreamReader(p.getErrorStream()));

      // first copy any errors from the attempted command
      while ((s = stdError.readLine()) != null) {
        out.write(s);
        out.write(newline);
      }

      // then the output
      while ((s = stdInput.readLine()) != null) {
        out.write(s);
        out.write(newline);
      }

      //Process p = Runtime.getRuntime().exec

    }
    catch (Exception e) {
      out.write("THERE WAS AN EXCEPTION");
      e.printStackTrace();
    }
    return out.toString();

  }
}

// this class is private to this file
class TimePlace {
  public final DateTime datetime;
  public final Double lat;
  public final Double lon;

  public TimePlace(DateTime datetime, Double lat, Double lon) {
    this.datetime = datetime;
    this.lat = makeNullDefault(lat);
    this.lon = makeNullDefault(lon);
  }

  public TimePlace(MediaAsset ma) {
    datetime = ma.getDateTime();
    lat = makeNullDefault(ma.getLatitude());
    lon = makeNullDefault(ma.getLongitude());
  }

  private Double defaultLatLon = -1.0;
  private Double makeNullDefault(Double d) {
    if (d==null) {
      return defaultLatLon;
    }
    return d;
  }

  public long getDateTimeInSeconds() {
    return datetime.getMillis() / 1000;
  }

  public boolean hasAllFields() {
    return (lat!=null && lon!=null && datetime!=null);
  }
}
