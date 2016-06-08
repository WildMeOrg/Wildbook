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
