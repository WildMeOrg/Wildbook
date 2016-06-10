package org.ecocean;

import org.ecocean.media.*;

import org.joda.time.DateTime;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Properties;
import java.util.Vector;
import java.util.Arrays;
import java.io.File;
import java.io.IOException;
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

  public static List<Occurrence> defaultCluster(List<MediaAsset> assets, Shepherd myShepherd) {
    return makeNOccurrences(10, assets, myShepherd);
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
///////TODO pre-process vals for lat/lon/time
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
                                case "habitat":
                                    //occ.setHabitat(getValueString(ael));
                                    break;
                                case "bearing":
                                    //getValueDouble(ael)
                                    break;
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

}
/*
private class TimePlace {
  public final DateTime datetime;
  public final double latitude;
  public final double longitude;

  public TimePlace(DateTime datetime, Double lat, Double lon) {
    this.datetime = datetime;
    this.latitude = lat;
    this.longitude = lon;
  }

  public TimePlace(MediaAsset ma) {
    datetime = ma.getDateTime();
    latitude = ma.getLatitude();
    longitude = ma.getLongitude();
  }
}
*/
