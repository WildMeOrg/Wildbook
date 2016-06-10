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


import org.datanucleus.api.rest.orgjson.JSONObject;
import org.datanucleus.api.rest.orgjson.JSONException;

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



  public static String buildCommand(List<MediaAsset> assets) {

    if (assets==null) {
      return null;
    }

    List<TimePlace> inputData = new ArrayList<TimePlace>();
    List<MediaAsset> validAssets = new ArrayList<MediaAsset>();
    List<MediaAsset> invalidAssets = new ArrayList<MediaAsset>();

    for (MediaAsset ma : assets) {
      TimePlace datum = new TimePlace(ma);
      if (datum.hasAllFields()) {
        inputData.add(datum);
        validAssets.add(ma);
      } else {
        invalidAssets.add(ma);
      }
    }

    // TODO: deal with invalid assets
    // TODO: handle case with NO valid assets

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
      if (ma.getLatitude()==null || ma.getLongitude()==null || ma.getDateTime()==null) {
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

  public static List<Occurrence> runJonsClusterer(List<MediaAsset> assets, Shepherd myShepherd) {
    List<List<MediaAsset>> groupedAssets = groupAssetsByValidity(assets);
    List<MediaAsset> validAssets = groupedAssets.get(0);
    List<MediaAsset> invalidAssets = groupedAssets.get(1);

    String command = buildCommand(validAssets);
    String output = runPythonCommand(command);
    int[] occNums = parseJonsOutput(output);

    List<List<MediaAsset>> occurrenceGroups = groupAssetsByJonsOutput(validAssets, occNums);

    List<Occurrence> occurrences = putAssetsInOccs(occurrenceGroups);

    // final occurrence contains all invalid (ie no timeplace) MediaAssets
    if (invalidAssets.size()>0) {
      Occurrence leftovers = new Occurrence(invalidAssets, myShepherd);
      occurrences.put(leftovers);
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


class TimePlace {
  public final DateTime datetime;
  public final Double lat;
  public final Double lon;

  public TimePlace(DateTime datetime, Double lat, Double lon) {
    this.datetime = datetime;
    this.lat = lat;
    this.lon = lon;
  }

  public TimePlace(MediaAsset ma) {
    datetime = ma.getDateTime();
    lat = ma.getLatitude();
    lon = ma.getLongitude();
  }

  public long getDateTimeInSeconds() {
    return datetime.getMillis() / 1000;
  }

  public boolean hasAllFields() {
    return (lat!=null && lon!=null && datetime!=null);
  }
}







/*
public class JavaRunCommand {
 
    public static void main(String args[]) {
 
        String s = null;
 
        try {
             
        // run the Unix "ps -ef" command
            // using the Runtime exec method:
            Process p = Runtime.getRuntime().exec("ps -ef");
             
            BufferedReader stdInput = new BufferedReader(new
                 InputStreamReader(p.getInputStream()));
 
            BufferedReader stdError = new BufferedReader(new
                 InputStreamReader(p.getErrorStream()));
 
            // read the output from the command
            System.out.println("Here is the standard output of the command:\n");
            while ((s = stdInput.readLine()) != null) {
                System.out.println(s);
            }
             
            // read any errors from the attempted command
            System.out.println("Here is the standard error of the command (if any):\n");
            while ((s = stdError.readLine()) != null) {
                System.out.println(s);
            }
             
            System.exit(0);
        }
        catch (IOException e) {
            System.out.println("exception happened - here's what I know: ");
            e.printStackTrace();
            System.exit(-1);
        }
    }
}
*/
