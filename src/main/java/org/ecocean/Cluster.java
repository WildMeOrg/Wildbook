package org.ecocean;

import org.ecocean.media.*;

import org.joda.time.DateTime;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Properties;
import java.util.Vector;
import java.util.Arrays;
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

  public static String[] makeNOccurrences(int n, List<MediaAsset> assets, Shepherd myShepherd) {
    ArrayList<String> occurrenceIDs = new ArrayList<String>();
    double hop_size = assets.size() / (double) n;
    for (int i=0; i<n; i++) {
      int start_index = (int) Math.round(i*hop_size);
      int end_index = (int) Math.round((i+1)*hop_size);
      List<MediaAsset> subList = assets.subList(start_index, end_index);
      Occurrence occ = new Occurrence(subList);
      if (myShepherd!= null) myShepherd.storeNewOccurrence(occ);
      occurrenceIDs.add(occ.getOccurrenceID());
    }
    return (String[]) occurrenceIDs.toArray();
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
