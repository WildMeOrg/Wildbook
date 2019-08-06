package org.ecocean;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import org.ecocean.grid.EncounterLite;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

public class LocationID {
  
  //the JSON representation of /bundles/locationID.json
  private static ConcurrentHashMap<String,JSONObject> jsonMaps=new ConcurrentHashMap<String,JSONObject>();
  
  
  public static ConcurrentHashMap<String,JSONObject> getJSONMaps(){return jsonMaps;}
  
  /*
   * Return the JSON representation of /bundles/locationID.json 
   */
  public static JSONObject getLocationIDStructure() {
    if(jsonMaps.get("default")==null)loadJSONData(null);
    return jsonMaps.get("default");
  }
  
  /*
   * Return the JSON representation of /bundles/locationID.json 
   */
  public static JSONObject getLocationIDStructure(String qualifier) {
    if(qualifier==null)return getLocationIDStructure();
    if(jsonMaps.get(qualifier)==null)loadJSONData(qualifier);
    return jsonMaps.get(qualifier);
  }
  
  /*
   * Force a reload of /bundles/locationID.json
   */
  public static void reloadJSON(String filename) {
    jsonMaps=new ConcurrentHashMap<String,JSONObject>();
    loadJSONData(filename);
  }
  
  private static void loadJSONData(String qualifier) {
    InputStream is = null;
    String filename="locationID.json";
    if(qualifier!=null) {
      filename="locationID_"+qualifier+".json";
    } 
    
    String shepherdDataDir="wildbook_data_dir";
    Properties contextsProps=ShepherdProperties.getContextsProperties();
    if(contextsProps.getProperty("context0"+"DataDir")!=null){
      shepherdDataDir=contextsProps.getProperty("context0"+"DataDir");
    }
      
    //first look for the file in the override director
    File configFile = new File("webapps/"+shepherdDataDir+"/WEB-INF/classes/bundles/"+filename);

      if(configFile.exists()) {
        try {
          is=new FileInputStream(configFile);
        }
        catch(Exception e) {e.printStackTrace();}
      }
    
      //if we couldn't find it in the override dir, locad it from the web app root
    if (is == null) {
      is=LocationID.class.getResourceAsStream("/bundles/"+filename);
    }
    JSONTokener tokener = new JSONTokener(is);
    String key="default";
    if(qualifier!=null)key=qualifier;
    jsonMaps.put(key,new JSONObject(tokener));
    //System.out.println("jsonMaps: "+jsonMaps.toString());
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
  public static String getNameForLocationID(String locationID, String qualifier) {
    JSONObject j=recurseToFindID(locationID,getLocationIDStructure(qualifier));
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
  public static List<String> getIDForParentAndChildren(String locationID, String qualifier) {
    ArrayList<String> al=new ArrayList<String>();
    return getIDForParentAndChildren(locationID,al,qualifier);
  }
  
  /*
   * Return a List of Strings of the "id" attributes of the parent locationID and the IDs of all of its children in the order traversed
   */
  public static List<String> getIDForParentAndChildren(String locationID,ArrayList<String> al,String qualifier) {
    JSONObject j=recurseToFindID(locationID,getLocationIDStructure(qualifier));
    if(j!=null) {
      try{
        
        recurseToFindIDStrings(j,al);
        
      }
      catch(JSONException e) {}
    }
    return al;
  }
  
  /*
   * Starting with a childID, get the IDs of its root parent all the way down to the child ID
   * @childLocationID - dig for a child with this @id
   * @qualifier to use in the digging (e.g., to define user or org value, such as use the 'indocet' qualifier)
   * @return a List of Strings of the lineage of the child ID, starting with its highest parent down to the ID itself.
   */
  public static List<String> getIDForChildAndParents(String childLocationIDToFind,String qualifier){
    ArrayList<String> al=new ArrayList<String>();
    JSONObject jsonobj=getLocationIDStructure(qualifier);
    findPath(jsonobj, childLocationIDToFind, al);
    return al;
  }
  
  private static void findPath(JSONObject jsonobj, String childLocationIDToFind, ArrayList<String> al) {
    try {
      if(jsonobj.getString("id").equals( childLocationIDToFind)) {return;}
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
          JSONObject id=recurseToFindID(childLocationIDToFind,loc);
          if(id!=null) {
            al.add(loc.getString("id"));
            if(loc.getString("id").equals(childLocationIDToFind))return;
            findPath(loc, childLocationIDToFind,al);
          }
         
        }
      }
    }
    catch(JSONException e) {}
  }
  
  private static String getIDIfContainsChildID(JSONObject jsonobj,String childID, String qualifier) {
    List<String> list=getIDForParentAndChildren(childID,new ArrayList<String>(),qualifier);
    try {
      if(list!=null && list.contains(childID)) {return jsonobj.getString("id");}
    }
    catch(JSONException jsone) {}
    return null;
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
  public static String getHTMLSelector(boolean multiselect, String selectedID,String qualifier, String htmlID, String htmlName, String htmlClass) {
    
    String multiselector="";
    if(multiselect)multiselector=" multiple=\"multiple\"";
    
    StringBuffer selector=new StringBuffer("<select style=\"resize:both;\" name=\""+htmlName+"\" id=\""+htmlID+"\" class=\""+htmlClass+"\" "+multiselector+">\n\r<option value=\"\"></option>\n\r");

     createSelectorOptions(getLocationIDStructure(qualifier),selector,0,selectedID);
    
    selector.append("</select>\n\r");
    return selector.toString();

  }
  
  private static void createSelectorOptions(JSONObject jsonobj,StringBuffer selector,int nestingLevel, String selectedID) {
    
    int localNestingLevel=nestingLevel;
    String selected="";
    String spacing="";
    for(int i=0;i<localNestingLevel;i++) {spacing+="&nbsp;&nbsp;&nbsp;";}
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
  

    //this will take in a list of locationIDs and expand them to include any children they may have
    // it returns a list that does not have duplicates, so the input list can contain relatives and all should be fine
    // please note it also will leave untouched any IDs which *are not in LocationID.json*, so they will not be filtered out
    //  (this is intentional behavior so that an ID need not be in LocationID.json to be considered valid here)
    public static List<String> expandIDs(List<String> ids) {
        return expandIDs(ids, null);
    }
    public static List<String> expandIDs(List<String> ids, String qualifier) {
        List<String> rtn = new ArrayList<String>();
        if (Util.collectionIsEmptyOrNull(ids)) return rtn;
        for (String id : ids) {
            List<String> tree = getIDForParentAndChildren(id, qualifier);
            if (tree.size() < 1) {
                if (!rtn.contains(id)) rtn.add(id);
            } else {
                for (String t : tree) {
                    if (!rtn.contains(t)) rtn.add(t);
                }
            }
        }
        return rtn;
    }

}
