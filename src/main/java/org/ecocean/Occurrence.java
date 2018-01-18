package org.ecocean;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Properties;
import java.util.Vector;
import java.util.Arrays;
import java.util.Set;
import java.util.HashSet;
import java.text.SimpleDateFormat;
import org.ecocean.media.MediaAsset;
import org.ecocean.security.Collaboration;
import org.ecocean.media.MediaAsset;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.builder.ToStringBuilder;

import org.datanucleus.api.rest.orgjson.JSONObject;
import org.datanucleus.api.rest.orgjson.JSONArray;
import org.datanucleus.api.rest.orgjson.JSONException;

import org.joda.time.DateTime;

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
  private String groupBehavior;  // categorical
  //additional comments added by researchers
  private String comments = "None";
  private String modified;
  //private String locationID;
  private String dateTimeCreated;

  // ASWN fields
  private String fieldStudySite;
  private String fieldSurveyCode;  // i.e. project-specific sighting no. (redundant w/ id, or is that UUID?o)
  private String sightingPlatform; // e.g. vessel name
  private String groupComposition; // categorical
  private String humanActivityNearby; // drop-down, e.g. artisanal fishing, commercial shipping, tourism
  private String initialCue; // blow, splash, birds, dorsal fin etc
  private String seaState; //beaufort categories
  private Double seaSurfaceTemp;
  private Double swellHeight;
  private Double visibilityIndex; // 1-5 with 5 indicating horizon visible

  private Double effortCode; // 1-5;

  private Double decimalLatitude;
  private Double decimalLongitude;
  private String transectName;
  private Double transectBearing;
  private Double distance;
  private Double bearing;

  private Integer minGroupSizeEstimate;
  private Integer maxGroupSizeEstimate;
  private Double bestGroupSizeEstimate;
  private Integer numAdults;
  private Integer numJuveniles;
  private Integer numCalves;
  private String observer;



  // do we have these?



  private Long dateTimeLong; // this is for searching


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

  public Occurrence(String occurrenceID){
    System.out.println("    NEW OCCURRENCE with only an id: "+occurrenceID);
    this.occurrenceID=occurrenceID;
    encounters=new ArrayList<Encounter>();
    assets = new ArrayList<MediaAsset>();
    setDWCDateLastModified();
    setDateTimeCreated();
    System.out.println("Created new occurrence "+this.occurrenceID);

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
        return false;
      }
    }

    if(isNew){encounters.add(enc);}

    //if((locationID!=null) && (enc.getLocationID()!=null)&&(!enc.getLocationID().equals("None"))){this.locationID=enc.getLocationID();}
    return isNew;

  }

  // like addEncounter but adds backwards link to this enc
  public void addEncounterAndUpdateIt(Encounter enc){
    addEncounter(enc);
    enc.setOccurrenceID(this.getOccurrenceID());
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

  public void setEncounters(ArrayList<Encounter> encounters){this.encounters=encounters;}

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
        } catch (Exception e) {
          System.out.println("Occurrence.setLatLonFromEncs could not parse values ("+lat+", "+lon+")");
        }
      }
    }
  }

  public String getLatLonString() {
    String latStr = (decimalLatitude!=null) ? decimalLatitude.toString() : "";
    String lonStr = (decimalLongitude!=null) ? decimalLongitude.toString() : "";
    return (latStr+", "+lonStr);
  }

  public ArrayList<String> getMarkedIndividualNamesForThisOccurrence(){
    ArrayList<String> names=new ArrayList<String>();
    try{
      int size=getNumberEncounters();

      for(int i=0;i<size;i++){
        Encounter enc=encounters.get(i);
        if((enc.getIndividualID()!=null)&&(!names.contains(enc.getIndividualID()))){names.add(enc.getIndividualID());}
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
                .append("fieldStudySite",fieldStudySite)
                .append("fieldSurveyCode",fieldSurveyCode)
                .append("sightingPlatform",sightingPlatform)
                .append("decimalLatitude",decimalLatitude)
                .append("decimalLongitude",decimalLongitude)
                .append("individualCount",individualCount)
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

    //this is called when a batch of encounters (which should be on this occurrence) were made from detection
    // *as a group* ... see also Encounter.detectedAnnotation() for the one-at-a-time equivalent
    public void fromDetection(Shepherd myShepherd, HttpServletRequest request) {
        System.out.println(">>>>>> detection created " + this);
    }

    public org.datanucleus.api.rest.orgjson.JSONObject getExemplarImage(HttpServletRequest req) throws JSONException {

      ArrayList<org.datanucleus.api.rest.orgjson.JSONObject> al=getExemplarImages(req);
      if(al.size()>0){return al.get(0);}
      return new JSONObject();


    }

    // ASWN field getters/setters
    public String getFieldStudySite() {
      return fieldStudySite;
    }
    public void setFieldStudySite(String fieldStudySite) {
      this.fieldStudySite = fieldStudySite;
    }
    public String getFieldSurveyCode() {
      return fieldSurveyCode;
    }
    public void setFieldSurveyCode(String fieldSurveyCode) {
      this.fieldSurveyCode = fieldSurveyCode;
    }
    public String getSightingPlatform() {
      return sightingPlatform;
    }
    public void setSightingPlatform(String sightingPlatform) {
      this.sightingPlatform = sightingPlatform;
    }
    public String getGroupComposition() {
      return groupComposition;
    }
    public void setGroupComposition(String groupComposition) {
      this.groupComposition = groupComposition;
    }
    public String getHumanActivityNearby() {
      return humanActivityNearby;
    }
    public void setHumanActivityNearby(String humanActivityNearby) {
      this.humanActivityNearby = humanActivityNearby;
    }
    public String getInitialCue() {
      return initialCue;
    }
    public void setInitialCue(String initialCue) {
      this.initialCue = initialCue;
    }
    public String getSeaState() {
      return seaState;
    }
    public void setSeaState(String seaState) {
      this.seaState = seaState;
    }

    public Double getSeaSurfaceTemp() {
      return seaSurfaceTemp;
    }
    public void setSeaSurfaceTemp(Double seaSurfaceTemp) {
      this.seaSurfaceTemp = seaSurfaceTemp;
    }
    public Double getSwellHeight() {
      return swellHeight;
    }
    public void setSwellHeight(Double swellHeight) {
      this.swellHeight = swellHeight;
    }
    public Double getVisibilityIndex() {
      return visibilityIndex;
    }
    public void setVisibilityIndex(Double visibilityIndex) {
      this.visibilityIndex = visibilityIndex;
    }
    public Double getEffortCode() {
      return effortCode;
    }
    public void setEffortCode(Double effortCode) {
      this.effortCode = effortCode;
    }
    public Double getDecimalLatitude() {
      return decimalLatitude;
    }
    public void setDecimalLatitude(Double decimalLatitude) {
      this.decimalLatitude = decimalLatitude;
    }
    public Double getDecimalLongitude() {
      return decimalLongitude;
    }
    public void setDecimalLongitude(Double decimalLongitude) {
      this.decimalLongitude = decimalLongitude;
    }

    public String getTransectName() {
      return transectName;
    }
    public void setTransectName(String transectName) {
      this.transectName = transectName;
    }

    public Double getTransectBearing() {
      return transectBearing;
    }
    public void setTransectBearing(Double transectBearing) {
      this.transectBearing = transectBearing;
    }
    public Double getDistance() {
      return distance;
    }
    public void setDistance(Double distance) {
      this.distance = distance;
    }
    public Double getBearing() {
      return bearing;
    }
    public void setBearing(Double bearing) {
      this.bearing = bearing;
    }

    public Integer getMinGroupSizeEstimate() {
      return minGroupSizeEstimate;
    }
    public void setMinGroupSizeEstimate(Integer minGroupSizeEstimate) {
      this.minGroupSizeEstimate = minGroupSizeEstimate;
    }
    public Integer getMaxGroupSizeEstimate() {
      return maxGroupSizeEstimate;
    }
    public void setMaxGroupSizeEstimate(Integer maxGroupSizeEstimate) {
      this.maxGroupSizeEstimate = maxGroupSizeEstimate;
    }
    public Double getBestGroupSizeEstimate() {
      return bestGroupSizeEstimate;
    }
    public void setBestGroupSizeEstimate(Double bestGroupSizeEstimate) {
      this.bestGroupSizeEstimate = bestGroupSizeEstimate;
    }
    public Integer getNumAdults() {
      return numAdults;
    }
    public void setNumAdults(Integer numAdults) {
      this.numAdults = numAdults;
    }
    public Integer getNumJuveniles() {
      return numJuveniles;
    }
    public void setNumJuveniles(Integer numJuveniles) {
      this.numJuveniles = numJuveniles;
    }
    public Integer getNumCalves() {
      return numCalves;
    }
    public void setNumCalves(Integer numCalves) {
      this.numCalves = numCalves;
    }
    public String getObserver() {
      return observer;
    }
    public void setObserver(String observer) {
      this.observer = observer;
    }



    public Long getDateTimeLong() {
      return dateTimeLong;
    }
    public void setDateTimeLong(Long dateTimeLong) {
      this.dateTimeLong = dateTimeLong;
    }
    public DateTime getDateTime() {
      if (dateTimeLong == null) return null;
      return new DateTime(dateTimeLong);
    }
    public void setDateTime(DateTime dt) {
      if (dt == null) dateTimeLong = null;
      else dateTimeLong = dt.getMillis();
    }




}
