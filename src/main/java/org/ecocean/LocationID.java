package org.ecocean;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

public class LocationID {
  
  private static JSONObject json;
  
  public static JSONObject getLocationIDStructure() {
    if(json==null)loadJSONData();
    return json;
  }
  
  public static void reloadJSON() {
    json=null;
    loadJSONData();
  }
  
  private static void loadJSONData() {
    InputStream is = LocationID.class.getResourceAsStream("/bundles/locationID.json");
    if (is == null) {
        System.out.println("Cannot find file locationID.json");
    }
    JSONTokener tokener = new JSONTokener(is);
    json = new JSONObject(tokener);
  }
  
  private static JSONObject recurseToFindID(String id,JSONObject jsonobj) {
    
    //if this is the right object, return it
    try {
      if(jsonobj.getString("id")!=null && jsonobj.getString("id").equals(id)) {return jsonobj;}
    }
    catch(JSONException e) {}
    
    //otherwise iterate through its locationID array
    try {
      if(jsonobj.getJSONArray("locationID")!=null) {
  
        JSONArray locs=jsonobj.getJSONArray("locationID");
        //System.out.println("Iterating locationID array for: "+jsonobj.getString("name"));
        int numLocs=locs.length();
        for(int i=0;i<numLocs;i++) {
          JSONObject loc=locs.getJSONObject(i);
          JSONObject j=recurseToFindID(id,loc);
          if(j!=null) return j;
        }
      }
  }
  catch(JSONException e) {}
    return null;
  }
  
  public static String getNameforLocationID(String locationID) {
    JSONObject j=recurseToFindID(locationID,json);
    if(j!=null) {
      try{
        return j.getString("name");
      }
      catch(JSONException e) {}
    }
    return null;
  }
  
  public static List<String> getNamesForParentAndChildren(String locationID) {
    ArrayList<String> al=new ArrayList<String>();
    JSONObject j=recurseToFindID(locationID,json);
    if(j!=null) {
      try{
        
        recurseToFindIDStrings(j,al);
        
      }
      catch(JSONException e) {}
    }
    return al;
  }
  
  private static void recurseToFindIDStrings(JSONObject jsonobj,ArrayList<String> al) {
    
    //if this is the right object, return it
    try {
      al.add(jsonobj.getString("id"));
    }
    catch(JSONException e) {}
    
    //otherwise iterate through its locationID array
    try {
      if(jsonobj.getJSONArray("locationID")!=null) {
  
        JSONArray locs=jsonobj.getJSONArray("locationID");
        //System.out.println("Iterating locationID array for: "+jsonobj.getString("name"));
        int numLocs=locs.length();
        for(int i=0;i<numLocs;i++) {
          JSONObject loc=locs.getJSONObject(i);
          recurseToFindIDStrings(loc,al);

        }
      }
  }
  catch(JSONException e) {}
  }
  

  
  

}
