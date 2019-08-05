package org.ecocean;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

public class LocationID {
  
  //the JSON representation of /bundles/locationID.json
  private static JSONObject json;
  
  /*
   * Return the JSON representation of /bundles/locationID.json 
   */
  public static JSONObject getLocationIDStructure() {
    if(json==null)loadJSONData();
    return json;
  }
  
  /*
   * Force a reload of /bundles/locationID.json
   */
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
  
  /*
   * Return the "name" attribute from JSON for a given "id" in /bundles/locationID.json
   */
  public static String getNameForLocationID(String locationID) {
    JSONObject j=recurseToFindID(locationID,getLocationIDStructure());
    if(j!=null) {
      try{
        return j.getString("name");
      }
      catch(JSONException e) {}
    }
    return null;
  }
  
  
  /*
   * Return a List of Strings of the "id" attributes of the parent locationID and the IDs of all of its children
   */
  public static List<String> getIDForParentAndChildren(String locationID) {
    ArrayList<String> al=new ArrayList<String>();
    return getIDForParentAndChildren(locationID,al);
  }
  
  /*
   * Return a List of Strings of the "id" attributes of the parent locationID and the IDs of all of its children
   */
  public static List<String> getIDForParentAndChildren(String locationID,ArrayList<String> al) {
    JSONObject j=recurseToFindID(locationID,getLocationIDStructure());
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
      if(!al.contains(jsonobj.getString("id")))al.add(jsonobj.getString("id"));
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
  
  /*
  * Return an HTML selector of hierarchical locationIDs with indenting
  */
  public static String getHTMLSelector(boolean multiselect, String selectedID) {
    
    String multiselector="";
    if(multiselect)multiselector=" multiple=\"multiple\"";
    
    StringBuffer selector=new StringBuffer("<select name=\"code\" id=\"selectCode\" class=\"form-control\" "+multiselector+">\n\r<option value=\"\"></option>\n\r");

     createSelectorOptions(getLocationIDStructure(),selector,0,selectedID);
    
    selector.append("</select>\n\r");
    return selector.toString();

  }
  
  private static void createSelectorOptions(JSONObject jsonobj,StringBuffer selector,int nestingLevel, String selectedID) {
    
    int localNestingLevel=nestingLevel;
    String selected="";
    String spacing="";
    for(int i=0;i<localNestingLevel;i++) {spacing+="&nbsp;&nbsp;";}
    //see if we can add this item to the list
    try {
      if(selectedID!=null && jsonobj.getString("id").equals(selectedID))selected=" selected=\"selected\"";
      selector.append("<option value=\""+jsonobj.getString("id")+"\" "+selected+">"+spacing+jsonobj.getString("name")+"</option>\n\r");
      localNestingLevel++;
    }
    catch(JSONException e) {}

    
    //iterate locationID array
    try {
        JSONArray locs=jsonobj.getJSONArray("locationID");
        int numLocs=locs.length();
        for(int i=0;i<numLocs;i++) {
          
          JSONObject loc=locs.getJSONObject(i);
          createSelectorOptions(loc,selector,localNestingLevel,selectedID);
        }
    }
    catch(JSONException e) {}
  }
  

}
