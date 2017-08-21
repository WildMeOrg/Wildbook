package org.ecocean;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Properties;
import java.util.Vector;
import java.util.Arrays;
import java.util.Set;
import java.util.HashSet;
import org.joda.time.DateTime;
import java.text.SimpleDateFormat;

import org.ecocean.media.AssetStoreType;
import org.ecocean.media.MediaAsset;
import org.ecocean.security.Collaboration;
import org.ecocean.media.MediaAsset;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.builder.ToStringBuilder;

import org.datanucleus.api.rest.orgjson.JSONObject;
import org.datanucleus.api.rest.orgjson.JSONArray;
import org.datanucleus.api.rest.orgjson.JSONException;


/**
 * Whereas an Encounter is meant to represent one MarkedIndividual at one point in time and space, an Occurrence
 * is meant to represent several Encounters that occur in a natural grouping (e.g., a pod of dolphins). Ultimately
 * the goal of the Encounter class is to represent associations among MarkedIndividuals that are commonly
 * sighted together.
 *
 * @author Jason Holmberg
 *
 */
public class Occurrence implements java.io.Serializable{



  /**
   *
   */
  private static final long serialVersionUID = -7545783883959073726L;
  private ArrayList<Encounter> encounters;
  private List<MediaAsset> assets;
  private String occurrenceID;
  private Integer individualCount;
  private String groupBehavior;
  //additional comments added by researchers
  private String comments = "None";
  private String modified;
  //private String locationID;
  private String dateTimeCreated;
  
  //social media registration fields for AI-created occurrences
  private String socialMediaSourceID;
  private String socialMediaQueryCommentID;
  private String socialMediaQueryCommentReplies;

  // this is helpful for sorting but isn't (for now) intended to be UI-facing
  // rather it's set from Encounters
  private Long millis;


	/* Rosemary meta-data for IBEIS */
/*
	private String sun = "";
	private String wind = "";
	private String rain = "";
	private String cloudCover = "";
	private String direction;
	private String localName;
	private String grassLength;
	private String grassColor;
	private String grassSpecies;
	private String bushType;
	private String bit;
	private String otherSpecies;
*/
	private Double distance;
	private Double decimalLatitude;
	private Double decimalLongitude;

/////Lewa-specifics
  private DateTime dateTime;

  /*
	private String habitat;
  private String groupType;
  private String groupActivity;
  private Integer groupSize;
	private Integer numTerMales;
	private Integer numBachMales;
	private Integer numNonLactFemales;
	private Integer numLactFemales;
  private Integer numJuveniles;
	private Double bearing;
*/
  // new fields added for Dan's lab
  
  /*
  private String imageSet;
  private String soil;
  private String rain;
  private String activity;
  private String habitatOpenness;
  private String grassGreenness;
  private String grassHeight;
  private String weather;
  private String wind;
*/
  
  
  //empty constructor used by the JDO enhancer
  public Occurrence(){}

  /**
   * Class constructor.
   *
   *
   * @param occurrenceID A unique identifier for this occurrence that will become its primary key in the database.
   * @param enc The first encounter to add to this occurrence.
   */
  public Occurrence(String occurrenceID, Encounter enc){
    this.occurrenceID=occurrenceID;
    encounters=new ArrayList<Encounter>();
    encounters.add(enc);
    assets = new ArrayList<MediaAsset>();
    setDWCDateLastModified();
    setDateTimeCreated();
    //if((enc.getLocationID()!=null)&&(!enc.getLocationID().equals("None"))){this.locationID=enc.getLocationID();}
  }

  public Occurrence(List<MediaAsset> assets, Shepherd myShepherd){
    this.occurrenceID = Util.generateUUID();

    this.encounters = new ArrayList<Encounter>();
    this.assets = assets;
    for (MediaAsset ma : assets) {
      ma.setOccurrence(this);
      myShepherd.getPM().makePersistent(ma);
    }
    setDWCDateLastModified();
    setDateTimeCreated();
  }


  public boolean addEncounter(Encounter enc){
    if(encounters==null){encounters=new ArrayList<Encounter>();}

    //prevent duplicate addition
    boolean isNew=true;
    for(int i=0;i<encounters.size();i++) {
      Encounter tempEnc=(Encounter)encounters.get(i);
      if(tempEnc.getEncounterNumber().equals(enc.getEncounterNumber())) {
        isNew=false;
      }
    }

    if(isNew){encounters.add(enc);}

    //if((locationID!=null) && (enc.getLocationID()!=null)&&(!enc.getLocationID().equals("None"))){this.locationID=enc.getLocationID();}
    return isNew;

  }

  public ArrayList<Encounter> getEncounters(){
    return encounters;
  }

  public boolean addAsset(MediaAsset ma){
    if(assets==null){assets=new ArrayList<MediaAsset>();}

    //prevent duplicate addition
    boolean isNew=true;
    for(int i=0;i<assets.size();i++) {
      MediaAsset tempAss=(MediaAsset)assets.get(i);
      if(tempAss.getId() == ma.getId()) {
        isNew=false;
      }
    }

    if(isNew){assets.add(ma);}

    //if((locationID!=null) && (enc.getLocationID()!=null)&&(!enc.getLocationID().equals("None"))){this.locationID=enc.getLocationID();}
    return isNew;

  }

  public void setAssets(List<MediaAsset> assets) {
    this.assets = assets;
  }

  public List<MediaAsset> getAssets(){
    return assets;
  }

  public void removeEncounter(Encounter enc){
    if(encounters!=null){
      encounters.remove(enc);
    }
  }

  public int getNumberEncounters(){
    if(encounters==null) {return 0;}
    else{return encounters.size();}
  }

  public int getNumberIndividualIDs(){
    return getIndividualIDs().size();
  }

  public Set<String> getIndividualIDs(){
    Set<String> indivIds = new HashSet<String>();
    if (encounters == null) return indivIds;
    for (Encounter enc : encounters) {
      String id = enc.getIndividualID();
      if (id!=null && !indivIds.contains(id)) indivIds.add(id);
    }
    return indivIds;
  }


  public void setLatLonFromEncs() {
    for (Encounter enc: getEncounters()) {
      String lat = enc.getDecimalLatitude();
      String lon = enc.getDecimalLongitude();
      if (lat!=null && lon!=null && !lat.equals("-1.0") && !lon.equals("-1.0")) {
        try {
          setDecimalLatitude(Double.valueOf(lat));
          setDecimalLongitude(Double.valueOf(lon));
          return;
        } catch (Exception e) {}
      }
    }
  }

  public String getLatLonString() {
    String latStr = (decimalLatitude!=null) ? decimalLatitude.toString() : "";
    String lonStr = (decimalLongitude!=null) ? decimalLongitude.toString() : "";
    return (latStr+", "+lonStr);
  }


  public void setEncounters(ArrayList<Encounter> encounters){this.encounters=encounters;}

  public ArrayList<String> getMarkedIndividualNamesForThisOccurrence(){
    ArrayList<String> names=new ArrayList<String>();
    try{
      int size=getNumberEncounters();

      for(int i=0;i<size;i++){
        Encounter enc=encounters.get(i);
        if((enc.getIndividualID()!=null)&&(!enc.getIndividualID().equals("Unassigned"))&&(!names.contains(enc.getIndividualID()))){names.add(enc.getIndividualID());}
      }
    }
    catch(Exception e){e.printStackTrace();}
    return names;
  }

    public void setOccurrenceID(String id) {
        occurrenceID = id;
    }

  public String getOccurrenceID(){return occurrenceID;}


  public Integer getIndividualCount(){return individualCount;}
  public void setIndividualCount(Integer count){
      if(count!=null){individualCount = count;}
      else{individualCount = null;}
   }
   public void setIndividualCount() {
     setIndividualCount(getNumberIndividualIDs());
   }

  public String getGroupBehavior(){return groupBehavior;}
  public void setGroupBehavior(String behavior){
    if((behavior!=null)&&(!behavior.trim().equals(""))){
      this.groupBehavior=behavior;
    }
    else{
      this.groupBehavior=null;
    }
  }

  public ArrayList<SinglePhotoVideo> getAllRelatedMedia(){
    int numEncounters=encounters.size();
    ArrayList<SinglePhotoVideo> returnList=new ArrayList<SinglePhotoVideo>();
    for(int i=0;i<numEncounters;i++){
     Encounter enc=encounters.get(i);
     if(enc.getSinglePhotoVideo()!=null){
       returnList.addAll(enc.getSinglePhotoVideo());
     }
    }
    return returnList;
  }

  //you can choose the order of the EncounterDateComparator
  public Encounter[] getDateSortedEncounters(boolean reverse) {
  Vector final_encs = new Vector();
  for (int c = 0; c < encounters.size(); c++) {
    Encounter temp = (Encounter) encounters.get(c);
    final_encs.add(temp);
  }

  int finalNum = final_encs.size();
  Encounter[] encs2 = new Encounter[finalNum];
  for (int q = 0; q < finalNum; q++) {
    encs2[q] = (Encounter) final_encs.get(q);
  }
  EncounterDateComparator dc = new EncounterDateComparator(reverse);
  Arrays.sort(encs2, dc);
  return encs2;
}

  /**
   * Returns any additional, general comments recorded for this Occurrence as a whole.
   *
   * @return a String of comments
   */
  public String getComments() {
    if (comments != null) {

      return comments;
    } else {
      return "None";
    }
  }

  /**
   * Adds any general comments recorded for this Occurrence as a whole.
   *
   * @return a String of comments
   */
  public void addComments(String newComments) {
    if ((comments != null) && (!(comments.equals("None")))) {
      comments += newComments;
    } else {
      comments = newComments;
    }
  }

	public void setDecimalLatitude(Double d) {
		this.decimalLatitude = d;
	}

	public Double getDecimalLatitude() {
		return this.decimalLatitude;
	}

	public void setDecimalLongitude(Double d) {
		this.decimalLongitude = d;
	}

	public Double getDecimalLongitude() {
		return this.decimalLongitude;
	}


  public void setMillis(Long millis) {this.millis = millis;}
  public Long getMillis() {return this.millis;}

  public void setMillisFromEncounters() {
    this.millis = getMillisFromEncounters();
  }

  public Long getMillisFromEncounters() {
    for (Encounter enc: encounters) {
      if (enc.getDateInMilliseconds()!=null) {
        return enc.getDateInMilliseconds();
      }
    }
    return null;
  }


  public void setMillisFromEncounterAvg() {
    this.millis = getMillisFromEncounterAvg();
  }

  public Long getMillisFromEncounterAvg() {
    Long total = 0L;
    int numAveraged = 0;
    for (Encounter enc: encounters) {
      if (enc.getDateInMilliseconds()!=null) {
        total += enc.getDateInMilliseconds();
        numAveraged++;
      }
    }
    if (numAveraged == 0) return null;
    return (total / numAveraged);
  }
  public Long getMillisRobust() {
    if (this.millis!=null) return this.millis;
    if (getMillisFromEncounterAvg()!=null) return getMillisFromEncounterAvg();
    if (getMillisFromEncounters()!=null) return getMillisFromEncounters();
    return null;
  }



/*
	public void setWind(String w) {
		this.wind = w;
	}

	public String getWind() {
		return this.wind;
	}

	public void setSun(String s) {
		this.sun = s;
	}

	public String getSun() {
		return this.sun;
	}
	public String getRain() {
		return this.rain;
	}
	public void setRain(String r) {
		this.rain = r;
	}

	public String getCloudCover() {
		return this.cloudCover;
	}
	public void setCloudCover(String c) {
		this.cloudCover = c;
	}

	public String getDirection() {
		return this.direction;
	}
	public void setDirection(String d) {
		this.direction = d;
	}

	public String getLocalName() {
		return this.localName;
	}
	public void setLocalName(String n) {
		this.localName = n;
	}

	public String getGrassLength() {
		return this.grassLength;
	}
	public void setGrassLength(String l) {
		this.grassLength = l;
	}

	public String getGrassColor() {
		return this.grassColor;
	}
	public void setGrassColor(String c) {
		this.grassColor = c;
	}

	public String getGrassSpecies() {
		return this.grassSpecies;
	}
	public void setGrassSpecies(String g) {
		this.grassSpecies = g;
	}

	public String getBushType() {
		return this.bushType;
	}
	public void setBushType(String t) {
		this.bushType = t;
	}

	public String getBit() {
		return this.bit;
	}
	public void setBit(String b) {
		this.bit = b;
	}

	public String getOtherSpecies() {
		return this.otherSpecies;
	}
	public void setOtherSpecies(String s) {
		this.otherSpecies = s;
	}
*/

  public DateTime getDateTime() {
    return this.dateTime;
  }

  public void setDateTime(DateTime dt) {
    this.dateTime = dt;
  }

	public Double getDistance() {
		return this.distance;
	}
	public void setDistance(Double d) {
		this.distance = d;
	}

	
	/*
	public String getHabitat() {
		return this.habitat;
	}
	public void setHabitat(String h) {
		this.habitat = h;
	}

	public Integer getGroupSize() {
		return this.groupSize;
	}
	public void setGroupSize(Integer s) {
		this.groupSize = s;
	}

  public String getGroupActivity() {
		return this.groupActivity;
	}
	public void setGroupActivity(String s) {
		this.groupActivity = s;
	}
  public String getGroupType() {
		return this.groupType;
	}
	public void setGroupType(String s) {
		this.groupType = s;
	}

	public Integer getNumTerMales() {
		return this.numTerMales;
	}
	public void setNumTerMales(Integer s) {
		this.numTerMales = s;
	}

	public Integer getNumBachMales() {
		return this.numBachMales;
	}
	public void setNumBachMales(Integer s) {
		this.numBachMales = s;
	}

	public Integer getNumNonLactFemales() {
		return this.numNonLactFemales;
	}
	public void setNumNonLactFemales(Integer s) {
		this.numNonLactFemales = s;
	}

	public Integer getNumJuveniles() {
		return this.numJuveniles;
	}
	public void setNumJuveniles(Integer s) {
		this.numJuveniles = s;
	}

  public Integer getNumLactFemales() {
		return this.numLactFemales;
	}
	public void setNumLactFemales(Integer s) {
		this.numLactFemales = s;
	}

	public Double getBearing() {
		return this.bearing;
	}
	public void setBearing(Double b) {
		this.bearing = b;
	}

  public String getImageSet() { return this.imageSet; }
	public void setImageSet(String h) { this.imageSet = h; }

  public String getSoil() { return this.soil; }
	public void setSoil(String h) { this.soil = h; }

  public String getRain() { return this.rain; }
	public void setRain(String h) { this.rain = h; }

  public String getActivity() { return this.activity; }
	public void setActivity(String h) { this.activity = h; }

  public String getHabitatOpenness() { return this.habitatOpenness; }
	public void setHabitatOpenness(String h) { this.habitatOpenness = h; }

  public String getGrassGreenness() { return this.grassGreenness; }
	public void setGrassGreenness(String h) { this.grassGreenness = h; }

  public String getGrassHeight() { return this.grassHeight; }
	public void setGrassHeight(String h) { this.grassHeight = h; }

  public String getWeather() { return this.weather; }
	public void setWeather(String h) { this.weather = h; }

  public String getWind() { return this.wind; }
	public void setWind(String h) { this.wind = h; }
*/




  public Vector returnEncountersWithGPSData(boolean useLocales, boolean reverseOrder,String context) {
    //if(unidentifiableEncounters==null) {unidentifiableEncounters=new Vector();}
    Vector haveData=new Vector();
    Encounter[] myEncs=getDateSortedEncounters(reverseOrder);

    Properties localesProps = new Properties();
    if(useLocales){
      try {
        localesProps=ShepherdProperties.getProperties("locationIDGPS.properties", "",context);
      }
      catch (Exception ioe) {
        ioe.printStackTrace();
      }
    }

    for(int c=0;c<myEncs.length;c++) {
      Encounter temp=myEncs[c];
      if((temp.getDWCDecimalLatitude()!=null)&&(temp.getDWCDecimalLongitude()!=null)) {
        haveData.add(temp);
      }
      else if(useLocales && (temp.getLocationID()!=null) && (localesProps.getProperty(temp.getLocationID())!=null)){
        haveData.add(temp);
      }

      }

    return haveData;

  }


  public String getDWCDateLastModified() {
    return modified;
  }

  public void setDWCDateLastModified(String lastModified) {
    modified = lastModified;
  }

    public void setDWCDateLastModified() {
        modified = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date());
    }

  /**
   * This method simply iterates through the encounters for the occurrence and returns the first Encounter.locationID that it finds or returns null.
   *
   * @return
   */
  public String getLocationID(){
    int size=encounters.size();
    for(int i=0;i<size;i++){
      Encounter enc=encounters.get(i);
      if(enc.getLocationID()!=null){return enc.getLocationID();}
    }
    return null;
  }

  //public void setLocationID(String newLocID){this.locationID=newLocID;}

  public String getDateTimeCreated() {
    if (dateTimeCreated != null) {
      return dateTimeCreated;
    }
    return "";
  }

  public void setDateTimeCreated(String time) {
    dateTimeCreated = time;
  }

    public void setDateTimeCreated() {
        dateTimeCreated = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date());
    }

  public ArrayList<String> getCorrespondingHaplotypePairsForMarkedIndividuals(Shepherd myShepherd){
    ArrayList<String> pairs = new ArrayList<String>();

    ArrayList<String> names=getMarkedIndividualNamesForThisOccurrence();
    int numNames=names.size();
    for(int i=0;i<(numNames-1);i++){
      for(int j=1;j<numNames;j++){
        String name1=names.get(i);
        MarkedIndividual indie1=myShepherd.getMarkedIndividual(name1);
        String name2=names.get(i);
        MarkedIndividual indie2=myShepherd.getMarkedIndividual(name2);
        if((indie1.getHaplotype()!=null)&&(indie2.getHaplotype()!=null)){

          //we have a haplotype pair,
          String haplo1=indie1.getHaplotype();
          String haplo2=indie2.getHaplotype();

          if(haplo1.compareTo(haplo2)>0){pairs.add((haplo1+":"+haplo2));}
          else{pairs.add((haplo2+":"+haplo1));}
        }


      }
    }

    return pairs;
  }


  public ArrayList<String> getAllAssignedUsers(){
    ArrayList<String> allIDs = new ArrayList<String>();

     //add an alt IDs for the individual's encounters
     int numEncs=encounters.size();
     for(int c=0;c<numEncs;c++) {
       Encounter temp=(Encounter)encounters.get(c);
       if((temp.getAssignedUsername()!=null)&&(!allIDs.contains(temp.getAssignedUsername()))) {allIDs.add(temp.getAssignedUsername());}
     }

     return allIDs;
   }

	//convenience function to Collaboration permissions
	public boolean canUserAccess(HttpServletRequest request) {
		return Collaboration.canUserAccessOccurrence(this, request);
	}

  public JSONObject uiJson(HttpServletRequest request) throws JSONException {
    JSONObject jobj = new JSONObject();
    jobj.put("individualCount", this.getNumberEncounters());

    JSONObject encounterInfo = new JSONObject();
    for (Encounter enc : this.encounters) {
      encounterInfo.put(enc.getCatalogNumber(), new JSONObject("{url: "+enc.getUrl(request)+"}"));
    }
    jobj.put("encounters", encounterInfo);
    jobj.put("assets", this.assets);

    jobj.put("groupBehavior", this.getGroupBehavior());
    return jobj;

  }

/*  this was messing up the co-occur js (d3?), so lets kill for now?
  public org.datanucleus.api.rest.orgjson.JSONObject sanitizeJson(HttpServletRequest request,
                org.datanucleus.api.rest.orgjson.JSONObject jobj) throws org.datanucleus.api.rest.orgjson.JSONException {
            return sanitizeJson(request, jobj, true);
        }

  public org.datanucleus.api.rest.orgjson.JSONObject sanitizeJson(HttpServletRequest request, org.datanucleus.api.rest.orgjson.JSONObject jobj, boolean fullAccess) throws org.datanucleus.api.rest.orgjson.JSONException {
    jobj.put("occurrenceID", this.occurrenceID);
    jobj.put("encounters", this.encounters);
    if ((this.getEncounters() != null) && (this.getEncounters().size() > 0)) {
        JSONArray jarr = new JSONArray();
	///  *if* we want full-blown:  public JSONObject Encounter.sanitizeJson(HttpServletRequest request, JSONObject jobj) throws JSONException {
        //but for *now* (see note way above) this is all we need for gallery/image display js:
        for (Encounter enc : this.getEncounters()) {
            JSONObject je = new JSONObject();
            je.put("id", enc.getCatalogNumber());
            if (enc.hasMarkedIndividual()) je.put("individualID", enc.getIndividualID());
            if ((enc.getAnnotations() != null) && (enc.getAnnotations().size() > 0)) {
                JSONArray ja = new JSONArray();
                for (Annotation ann : enc.getAnnotations()) {
                    ja.put(ann.getId());
                }
                je.put("annotations", ja);
            }
            jarr.put(je);
        }
        jobj.put("encounters", jarr);
    }
    int[] assetIds = new int[this.assets.size()];
    for (int i=0; i<this.assets.size(); i++) {
      if (this.assets.get(i)!=null) assetIds[i] = this.assets.get(i).getId();
    }
    jobj.put("assets", assetIds);
    return jobj;

  }
*/

    public String toString() {
        return new ToStringBuilder(this)
                .append("id", occurrenceID)
                .toString();
    }
    
    public ArrayList<org.datanucleus.api.rest.orgjson.JSONObject> getExemplarImages(HttpServletRequest req) throws JSONException {
      ArrayList<org.datanucleus.api.rest.orgjson.JSONObject> al=new ArrayList<org.datanucleus.api.rest.orgjson.JSONObject>();
      //boolean haveProfilePhoto=false;
      for (Encounter enc : this.getDateSortedEncounters(false)) {
        //if((enc.getDynamicPropertyValue("PublicView")==null)||(enc.getDynamicPropertyValue("PublicView").equals("Yes"))){
          ArrayList<Annotation> anns = enc.getAnnotations();
          if ((anns == null) || (anns.size() < 1)) {
            continue;
          }
          for (Annotation ann: anns) {
            //if (!ann.isTrivial()) continue;
            MediaAsset ma = ann.getMediaAsset();
            if (ma != null) {
              //JSONObject j = new JSONObject();
              JSONObject j = ma.sanitizeJson(req, new JSONObject());
              
              
              
              if (j!=null) {
                
                
                //ok, we have a viable candidate
                
                //put ProfilePhotos at the beginning
                if(ma.hasKeyword("ProfilePhoto")){al.add(0, j);}
                //otherwise, just add it to the bottom of the stack
                else{
                  al.add(j);
                }
                
              }
              
              
            }
          }
      //}
      }
      return al;

    }
    


    public org.datanucleus.api.rest.orgjson.JSONObject getExemplarImage(HttpServletRequest req) throws JSONException {
      
      ArrayList<org.datanucleus.api.rest.orgjson.JSONObject> al=getExemplarImages(req);
      if(al.size()>0){return al.get(0);}
      return new JSONObject();
      

    }
    
    //social media registration fields for AI-created occurrences
    public String getSocialMediaSourceID(){return socialMediaSourceID;};
    public void setSocialMediaSourceID(String id){socialMediaSourceID=id;};
    
    
    public String getSocialMediaQueryCommentID(){return socialMediaQueryCommentID;};
    public void setSocialMediaQueryCommentID(String id){socialMediaQueryCommentID=id;};
    //each night we look for one occurrence that has commentid but not commentresponseid.
    
    public String getSocialMediaQueryCommentReplies(){return socialMediaQueryCommentReplies;};
    public void setSocialMediaQueryCommentReplies(String replies){socialMediaQueryCommentReplies=replies;};

    public boolean hasMediaFromAssetStoreType(AssetStoreType aType){
      if(getMediaAssetsOfType(aType).size()>0){return true;}
      return false;
    }
    
    public ArrayList<MediaAsset> getMediaAssetsOfType(AssetStoreType aType){
      ArrayList<MediaAsset> results=new ArrayList<MediaAsset>();     
      try{
        int numEncs=encounters.size();
        for(int k=0;k<numEncs;k++){
          
          ArrayList<MediaAsset> assets=encounters.get(k).getMedia();
          int numAssets=assets.size();
          for(int i=0;i<numAssets;i++){
            MediaAsset ma=assets.get(i);
            if(ma.getStore().getType()==aType){results.add(ma);}
          }
        }
      }
      catch(Exception e){e.printStackTrace();}
      return results;
    }
    
    public boolean hasMediaAssetFromRootStoreType(Shepherd myShepherd, AssetStoreType aType){
      try{
        int numEncs=encounters.size();
        for(int k=0;k<numEncs;k++){
          
          ArrayList<MediaAsset> assets=encounters.get(k).getMedia();
          int numAssets=assets.size();
          for(int i=0;i<numAssets;i++){
            MediaAsset ma=assets.get(i);
            if(ma.getStore().getType()==aType){return true;}
            if(ma.getParentRoot(myShepherd).getStore().getType()==aType){return true;}
          }
        }
      }
      catch(Exception e){e.printStackTrace();}
      return false;
    }

}
