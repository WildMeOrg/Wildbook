package org.ecocean;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Properties;
import java.util.Vector;
import java.util.Arrays;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Calendar;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.lang.reflect.Method;
import java.time.ZonedDateTime;
import org.ecocean.media.MediaAsset;
import org.ecocean.security.Collaboration;
import org.ecocean.media.MediaAsset;
import org.ecocean.api.ApiValueException;
import org.ecocean.api.ApiDeleteCascadeException;
//import org.ecocean.external.ExternalSubmission;
import org.ecocean.SystemLog;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.datanucleus.api.rest.orgjson.JSONObject;
import org.datanucleus.api.rest.orgjson.JSONArray;
import org.datanucleus.api.rest.orgjson.JSONException;
import org.ecocean.datacollection.Instant;

import org.joda.time.DateTime;

import org.ecocean.media.AssetStoreType;

/**
 * Whereas an Encounter is meant to represent one MarkedIndividual at one point in time and space, an Occurrence
 * is meant to represent several Encounters that occur in a natural grouping (e.g., a pod of dolphins). Ultimately
 * the goal of the Encounter class is to represent associations among MarkedIndividuals that are commonly
 * sighted together.
 *
 * @author Jason Holmberg
 *
 */
public class Occurrence extends org.ecocean.api.ApiCustomFields implements java.io.Serializable {

  /**
   *
   */
  private static final long serialVersionUID = -7545783883959073726L;
  //private String occurrenceID;
  private ArrayList<Encounter> encounters;

    /*
        this block begins reorganization of fields from Sighting Design meeting/document.
    */

    private List<Taxonomy> taxonomies;   //use for primary species
    private String groupBehavior;  // aka overall behavior
    //we are dropping support of .behaviors -- in favor of .groupBehavior ... TODO migration must copy these over
    private List<Instant> behaviors;
    private String context;  // type of sighting
    private String alternateId;  // type of sighting

    //date/time related
    // this can be manually set, otherwise can be derived from Encounters
    private ComplexDateTime startTime;
    private ComplexDateTime endTime;

    // END Sighting Design list (remainder are pre-existing and misc)

  private List<MediaAsset> assets;
  private ArrayList<Observation> observations = new ArrayList<Observation>();
  // Old ID. Getters and setters now use ID from base class.
  //private String ID;
  private Integer individualCount;
  //additional comments added by researchers
  private String comments = "None";
  private String modified;
    private String locationId;
    private String verbatimLocality;
    private String verbatimEventDate;
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
 
  // Variables used in the Survey, SurveyTrack, Path, Location model
  
  private String correspondingSurveyTrackID;
  private String correspondingSurveyID;
  //social media registration fields for AI-created occurrences
  private String socialMediaSourceID;
  private String socialMediaQueryCommentID;
  private String socialMediaQueryCommentReplies;

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

  private String submitterID;   //not sure if this should atrophy, now that we have .submitters ???  TODO what does Encounter do?
  private List<User> submitters;
  private List<User> informOthers;

    private String source;  //this is for SpotterConserveIO mostly but...
    //private List<ExternalSubmission> submissions;   //note: these may go away in favor of:
    //private List<SubmissionContentReference> submissionContentReferences;

  // do we have these?

  // this is helpful for sorting but isn't (for now) intended to be UI-facing
  // rather it's set from Encounters
  private Long millis;
  private Long dateTimeLong; // this is for searching
    private Long version;


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
    this.setId(occurrenceID);
    encounters=new ArrayList<Encounter>();
    encounters.add(enc);
    assets = new ArrayList<MediaAsset>();
    setDWCDateLastModified();
    setVersion();
    setDateTimeCreated();
    //if(encounters!=null){
    //  updateNumberOfEncounters();
    //}
    //if((enc.getLocationID()!=null)&&(!enc.getLocationID().equals("None"))){this.locationID=enc.getLocationID();}
  }

  public Occurrence(List<MediaAsset> assets, Shepherd myShepherd){
    this.setId(Util.generateUUID());
    this.encounters = new ArrayList<Encounter>();
    this.assets = assets;
    for (MediaAsset ma : assets) {
      ma.setOccurrence(this);
      myShepherd.getPM().makePersistent(ma);
    }
    setDWCDateLastModified();
    setVersion();
    setDateTimeCreated();
  }
  public Occurrence(String occurrenceID){
    this.setId(occurrenceID);
    encounters=new ArrayList<Encounter>();
    assets = new ArrayList<MediaAsset>();
    setDWCDateLastModified();
    setDateTimeCreated();
    setVersion();
    System.out.println("Created new occurrence with only ID" + occurrenceID);
  }


    public void setStartTime(ComplexDateTime dt) {
        startTime = dt;
        _validateStartEndTimes();
        setVersion();
    }
    public void setStartTimeNoCheck(ComplexDateTime dt) {
        setVersion();
        startTime = dt;
    }
    public ComplexDateTime getStartTime() {
        return startTime;
    }
    public void setEndTime(ComplexDateTime dt) {
        endTime = dt;
        _validateStartEndTimes();
        setVersion();
    }
    public void setEndTimeNoCheck(ComplexDateTime dt) {
        setVersion();
        endTime = dt;
    }
    public ComplexDateTime getEndTime() {
        return endTime;
    }

    public boolean setTimesFromEncounters() {
        return setTimesFromEncounters(false);
    }
    //override means set even if already has values
    public boolean setTimesFromEncounters(boolean override) {
        if (!override && ((this.startTime != null) || (this.endTime != null))) {
            SystemLog.debug("setTimesFromEncounters() not setting due to existing value(s) and no override");
            return false;
        }
        if (Util.collectionIsEmptyOrNull(this.encounters)) return false;
        ComplexDateTime st = null;
        ComplexDateTime et = null;
        for (Encounter enc : this.encounters) {
            ComplexDateTime enct = enc.getTime();
            if (enct == null) continue;
            if ((st == null) || (st.gmtLong() > enct.gmtLong())) st = enct;
            if ((et == null) || (et.gmtLong() < enct.gmtLong())) et = enct;
        }
        if ((st == null) && (et == null)) return false;
        this.setStartTime(st);
        this.setEndTime(et);
        return true;
    }

    private void _validateStartEndTimes() {
        if (this.startTime == null) throw new ApiValueException("startTime must have a value", "startTime");
        if (this.endTime == null) return;
        if (this.startTime.gmtMinus(this.endTime) > 0L) throw new ApiValueException("startTime > endTime", "startTime");
    }

  public boolean addEncounter(Encounter enc){
    if(encounters==null){encounters=new ArrayList<Encounter>();}

    //prevent duplicate addition
    if (this.encounters.contains(enc)) return false;
    boolean isNew=true;
    setVersion();
    if(isNew){
      encounters.add(enc);
      //updateNumberOfEncounters();
    }
    //if((locationID!=null) && (enc.getLocationID()!=null)&&(!enc.getLocationID().equals("None"))){this.locationID=enc.getLocationID();}
    return isNew;

  }
  
  //private void updateNumberOfEncounters() {
  //  if (individualCount!=null) {
  //    individualCount = encounters.size();      
  //  }
  //}

  // like addEncounter but adds backwards link to this enc
  public void addEncounterAndUpdateIt(Encounter enc){
    if (enc == null) return;
    addEncounter(enc);
    enc.setOccurrenceID(this.getOccurrenceID());
    setVersion();
  }

    public int getNumEncounters() {
        return Util.collectionSize(this.encounters);
    }

  public ArrayList<Encounter> getEncounters(){
    return encounters;
  }
  public List<String> getEncounterIDs(){
    List<String> res = new ArrayList<String>();
    for (Encounter enc: encounters) {res.add(enc.getCatalogNumber());}
    return res;
  }
  public List<String> getEncounterWebUrls(HttpServletRequest request){
    List<String> res = new ArrayList<String>();
    for (Encounter enc: encounters) {res.add(enc.getWebUrl(request));}
    return res;
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
  public void setSubmitterIDFromEncs(boolean overwrite) {
    if (!overwrite && Util.stringExists(getSubmitterID())) return;
    setSubmitterIDFromEncs();
  }
  public void setSubmitterIDFromEncs(){
    if (Util.collectionIsEmptyOrNull(encounters)) return;
    for (Encounter enc: encounters) {
      if (Util.stringExists(enc.getSubmitterID())) {
        setSubmitterID(enc.getSubmitterID());
        return;
      }
    }
  }
  public void setSubmitterID(String submitterID) {
    this.submitterID = submitterID;
  }
  public String getSubmitterID() {
    return submitterID;
  }

    public List<User> getSubmitters() {
        return submitters;
    }
    public void setSubmitters(List<User> u) {
        submitters = u;
        setVersion();
    }
    public void setSubmitter(User u) {  //overwrites existing
        if (u == null) return;
        submitters = new ArrayList<User>();
        submitters.add(u);
        setVersion();
    }
    public void addSubmitter(User u) {
        if (u == null) return;
        if (submitters == null) submitters = new ArrayList<User>();
        if (!submitters.contains(u)) submitters.add(u);
        setVersion();
    }
    public void setSubmittersFromEncounters() {  //note: this overrides any previously set
        if (Util.collectionIsEmptyOrNull(encounters)) return;
        submitters = new ArrayList<User>();
        for (Encounter enc : encounters) {
            if (enc.getSubmitters() == null) continue;
            for (User u : enc.getSubmitters()) {
                if (!submitters.contains(u)) submitters.add(u);
            }
        }
        setVersion();
    }

    public void addInformOther(User user) {
        if (user == null) return;
        if (informOthers == null) informOthers = new ArrayList<User>();
        if (!informOthers.contains(user)) informOthers.add(user);
        setVersion();
    }
    public List<User> getInformOthers() {
        return informOthers;
    }
    public void setInformOthers(List<User> users) {
        this.informOthers=users;
        setVersion();
    }
    
    public String getSource() {
        return source;
    }
    public void setSource(String s) {
        source = s;
        setVersion();
    }

/*
    public List<ExternalSubmission> getSubmissions() {
        return submissions;
    }
    public void setSubmissions(List<ExternalSubmission> subs) {
        submissions = subs;
        setVersion();
    }
    public List<SubmissionContentReference> getSubmissionContentReferences() {
        return submissionContentReferences;
    }
    public void setSubmissionContentReferences(List<SubmissionContentReference> scrs) {
        submissionContentReferences = scrs;
        setVersion();
    }
    public void addSubmissionContentReference(SubmissionContentReference scr) {
        if (submissionContentReferences == null) submissionContentReferences = new ArrayList<SubmissionContentReference>();
        submissionContentReferences.add(scr);
    }
*/

  public void setAssets(List<MediaAsset> assets) {
    this.assets = assets;
        setVersion();
  }

  public List<MediaAsset> getAssets(){
    return assets;
  }

  public void removeEncounter(Encounter enc){
    if(encounters!=null&&encounters.contains(enc)){
      encounters.remove(enc);
      //updateNumberOfEncounters();
    }
        setVersion();
  }

  public int getNumberEncounters(){
    if(encounters==null) {return 0;}
    else{return encounters.size();}
  }

    public void setEncounters(ArrayList<Encounter> encounters){
        this.encounters=encounters;
        setVersion();
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



  public void setLatLonFromEncs(boolean overwrite) {
    if (Util.collectionIsEmptyOrNull(encounters)) return;
    if (!overwrite && hasLatLon()) return;
    setLatLonFromEncs();
  }

    public void setLatLonFromEncs() {
        Double[] ll = getLatLonFromEncs();
        if (ll == null) return;
        setDecimalLatitude(ll[0]);
        setDecimalLongitude(ll[1]);
        setVersion();
    }

    //this just picks first enc with values -- we could get fancier
    public Double[] getLatLonFromEncs() {
        if (Util.collectionIsEmptyOrNull(encounters)) return null;
        for (Encounter enc: getEncounters()) {
            Double lat = enc.getDecimalLatitudeAsDouble();
            Double lon = enc.getDecimalLongitudeAsDouble();
            if (!Util.isValidDecimalLatitude(lat) || !Util.isValidDecimalLongitude(lon)) continue;
            Double[] ll = new Double[2];
            ll[0] = lat;
            ll[1] = lon;
            return ll;
        }
        return null;
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

  public static String getWebUrl(String occId, HttpServletRequest req) {
    return (CommonConfiguration.getServerURL(req)+"/occurrence.jsp?number="+occId);
  }
  public String getWebUrl(HttpServletRequest req) {
    return getWebUrl(getOccurrenceID(), req);
  }
  
  public String getOccurrenceID(){
    return this.getId();
  }

  public void setOccurrenceID(String id){
    this.setId(id);
    setVersion();
  }
  
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
    setVersion();
  }

    //just a hackaround so we can let api call this "behavior"
    public void setBehavior(String b) {
        groupBehavior = b;
    }

    public List<Instant> getBehaviors() {
        return behaviors;
    }
    public void setBehaviors(List<Instant> bhvs) {
        SystemLog.warn("Occurrence.behaviors has been deprecated! on {}", this);
        behaviors = bhvs;
        setVersion();
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

    public void setContext(String c) {
        context = c;
    }
    public String getContext() {
        return context;
    }

    public void setAlternateId(String c) {
        alternateId = c;
    }
    public String getAlternateId() {
        return alternateId;
    }

    public void setLocationId(String loc) {
        locationId = loc;
    }
    public String getLocationId() {
        return locationId;
    }

    public boolean setLocationIdFromEncounters() {
        return setLocationIdFromEncounters(false);
    }
    //override means set even if already has a value
    public boolean setLocationIdFromEncounters(boolean override) {
        if (!override && (this.locationId != null)) {
            SystemLog.debug("setLocationIdFromEncounters() not setting due to existing value and no override");
            return false;
        }
        if (Util.collectionIsEmptyOrNull(this.encounters)) return false;
        String common = null;
        for (Encounter enc : this.encounters) {
            String loc = enc.getLocationId();
            if (loc == null) continue;
            if (common == null) {
                common = loc;
            } else if (!loc.equals(common)) {
                SystemLog.debug("setLocationIdFromEncounters() non-consistent locationIds found: " + loc + ", " + common + "; giving up");
                return false;
            }
        }
        if (common == null) return false;
        this.setLocationId(common);
        return true;
    }

    public void setVerbatimLocality(String vl) {
        verbatimLocality = vl;
    }
    public String getVerbatimLocality() {
        return verbatimLocality;
    }

    public void setVerbatimEventDate(String vd) {
        verbatimEventDate = vd;
    }
    public String getVerbatimEventDate() {
        return verbatimEventDate;
    }

    //when we *must* have some kind of location
    public void _validateLocations() {
        if (!this.hasLatLon() && (this.getLocationId() == null) && (this.getVerbatimLocality() == null)) {
            throw new ApiValueException("must have at least one location-related value", "locationId");
        }
    }

    //these are for falling back on values derived from encounters if need be
    public String getLocationIdSomehow() {
        if (locationId != null) return locationId;
        if (Util.collectionIsEmptyOrNull(encounters)) return null;
        for (Encounter enc : encounters) {
            if (enc.getLocationId() != null) return enc.getLocationId();
        }
        return null;
    }
    public String getVerbatimLocalitySomehow() {
        if (verbatimLocality != null) return verbatimLocality;
        if (Util.collectionIsEmptyOrNull(encounters)) return null;
        for (Encounter enc : encounters) {
            if (enc.getVerbatimLocality() != null) return enc.getVerbatimLocality();
        }
        return null;
    }
    public ComplexDateTime getStartTimeSomehow() {
        if (startTime != null) return startTime;
        if (Util.collectionIsEmptyOrNull(encounters)) return null;;
        ComplexDateTime lowest = null;
        for (Encounter enc : encounters) {
            ComplexDateTime et = enc.getTime();
            if (et == null) continue;
            if ((lowest == null) || (lowest.gmtLong() > et.gmtLong())) lowest = et;
        }
        return lowest;
    }
    public ComplexDateTime getEndTimeSomehow() {
        if (endTime != null) return endTime;
        if (Util.collectionIsEmptyOrNull(encounters)) return null;
        ComplexDateTime highest = null;
        for (Encounter enc : encounters) {
            ComplexDateTime et = enc.getTime();
            if (et == null) continue;
            if ((highest == null) || (highest.gmtLong() < et.gmtLong())) highest = et;
        }
        return highest;
    }

  /**
   * Returns any additional, general comments recorded for this Occurrence as a whole.
   *
   * @return a String of comments
   */
  public String getComments() {
        return comments;
  }

  /**
   * Adds any general comments recorded for this Occurrence as a whole.
   *
   * @return a String of comments
   */
  public void addComments(String newComments) {
        if (comments == null) comments = "";
        comments += newComments;
  }

    public void setComments(String com) {
        comments = com;
    }


  public void setMillis(Long millis) {this.millis = millis;}
  public Long getMillis() {return this.millis;}

  public void setMillisFromEncounters() {
    this.millis = getMillisFromEncounters();
  }

    public boolean setStartEndFromEncounters() {
        return setStartEndFromEncounters(false);
    }
    public boolean setStartEndFromEncounters(boolean force) {
        if (!force && ((startTime != null) || (endTime != null))) return false;
        if (Util.collectionIsEmptyOrNull(encounters)) return false;
        boolean modified = false;
        long start = System.currentTimeMillis() * 2l;
        long end = 0l;
        for (Encounter enc : encounters) {
            ComplexDateTime encDT = null; //enc.getDateTime();   FIXME need to upgrade Encounter to ComplexDateTime
            if (encDT == null) continue;
            Long egmt = encDT.gmtLong();
            if (egmt < start) {
                modified = true;
                startTime = encDT;
                start = egmt;
            }
            if (egmt > end) {
                modified = true;
                endTime = encDT;
                end = egmt;
            }
        }
        return modified;
    }

    public Long getVersion() {
        return version;
    }
    public void setVersion() {
        version = System.currentTimeMillis();
        setDWCDateLastModified();  //this is a little overloaded to mean "modified"
    }
    public void setVersion(Long v) {
        version = v;
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
    Long total = 1L;
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

  // Convention: getters/setters for Taxonomy objects use noun "Taxonomy".
  // while convenience string-only methods use noun "Species"
  public String getSpecies() { return getSpecies(0);}
  public String getSpecies(int i) {
    Taxonomy taxy = getTaxonomy(i);
    if (taxy==null) return null;
    return taxy.getScientificName();
  }
  // convenience method for e.g. web display
  public List<String> getAllSpecies() {
    List<String> result = new ArrayList<String>();
    for (Taxonomy tax: taxonomies) {
      String sciName = tax.getScientificName();
      if (sciName!=null && !result.contains(sciName)) result.add(sciName);
    }
    return result;
  }
  public void addSpecies(String scientificName, Shepherd readOnlyShepherd) {
    Taxonomy taxy = readOnlyShepherd.getOrCreateTaxonomy(scientificName, false); // commit=false as standard with setters
    addTaxonomy(taxy);
    setVersion();
  }
  // warning: overwrites list (use addSpecies for multi-species)
  public void setSpecies(String scientificName, Shepherd readOnlyShepherd) {
    Taxonomy taxy = readOnlyShepherd.getOrCreateTaxonomy(scientificName, false);
    setTaxonomy(taxy);
    setVersion();
  }
  public boolean hasSpecies(String scientificName) {
    for (Taxonomy taxy: taxonomies) {
      if (scientificName.equals(taxy.getScientificName())) return true;
    }
    return false;
  }

  public List<Taxonomy> getTaxonomies() {
    return this.taxonomies;
  }
  public void setTaxonomies(List<Taxonomy> taxonomies) {
    this.taxonomies = taxonomies;
    setVersion();
  }
  public void setTaxonomiesFromEncounters(Shepherd myShepherd) {
    setTaxonomiesFromEncounters(myShepherd, true); // if we don't commit we risk creating multiple taxonomies with the same scientificName
  }
  public void setTaxonomiesFromEncounters(Shepherd myShepherd, boolean commit) {
    boolean shepherdWasCommitting = myShepherd.isDBTransactionActive();
    for (Encounter enc: encounters) {
      String taxString = enc.getTaxonomyString();
      // we need the manual hasSpecies check below to prevent duplicates with the same scientificName when commit=false
      if (!Util.stringExists(taxString) || (commit==false && hasSpecies(taxString))) continue;
      Taxonomy taxy = myShepherd.getOrCreateTaxonomy(taxString, commit);
      addTaxonomy(taxy);
    }
    if (shepherdWasCommitting) myShepherd.beginDBTransaction();
    setVersion();
  }

  public Taxonomy getTaxonomy() { return getTaxonomy(0);}
  public Taxonomy getTaxonomy(int i) {
    if (taxonomies==null || taxonomies.size()<=i) return null;
    return taxonomies.get(i);
  }
  public void addTaxonomy(Taxonomy taxy) {
    ensureTaxonomiesExist();
    if (!this.taxonomies.contains(taxy)) this.taxonomies.add(taxy);
  }
  // warning: overwrites list (use addTaxonomy for multi-species)
  public void setTaxonomy(Taxonomy taxy) {
    List<Taxonomy> taxis = new ArrayList<Taxonomy>();
    taxis.add(taxy);
    setTaxonomies(taxis);
    setVersion();
  }
    public void removeTaxonomy(Taxonomy tx) {
        if ((tx == null) || (this.taxonomies == null)) return;
        this.taxonomies.remove(tx);
    }

  private void ensureTaxonomiesExist() {
    if (this.taxonomies==null) this.taxonomies = new ArrayList<Taxonomy>();
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
/*  deprecated cuz now we store this explicitly on Occurrence
  public String getLocationID(){
    int size=encounters.size();
    for(int i=0;i<size;i++){
      Encounter enc=encounters.get(i);
      if(enc.getLocationID()!=null){return enc.getLocationID();}
    }
    return null;
  }
*/
  
  public void setCorrespondingSurveyTrackID(String id) {
    if (id != null && !id.equals("")) {
      correspondingSurveyTrackID = id;
    }
  }

  public String getCorrespondingSurveyTrackID() {
    if (correspondingSurveyTrackID != null) {
      return correspondingSurveyTrackID;
    }
    return null;
  }
  
  public void setCorrespondingSurveyID(String id) {
    if (id != null && !id.equals("")) {
      correspondingSurveyID = id;
    }
  }
  
  public String getCorrespondingSurveyID() {
    if (correspondingSurveyID != null) {
      return correspondingSurveyID;
    }
    return null;
  }
  
  public Survey getSurvey(Shepherd myShepherd) {
    Survey sv = null;
    if (correspondingSurveyID!=null) {
      try {
        sv = myShepherd.getSurvey(correspondingSurveyID);
        return sv;
      } catch (Exception e) {
        e.printStackTrace();
      }
    } else {
      try {
        for (Encounter enc : encounters) {
          if (correspondingSurveyID!=null) {
            if (enc.getOccurrenceID().length()>1) {
              correspondingSurveyID = enc.getOccurrenceID();
              sv = myShepherd.getSurvey(enc.getOccurrenceID());
              return sv;
            }
          }
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
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
      encounterInfo.put(enc.getOccurrenceID(), new JSONObject("{url: "+enc.getUrl(request)+"}"));
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
    jobj.put("ID", this.ID);
    jobj.put("encounters", this.encounters);
    if ((this.getEncounters() != null) && (this.getEncounters().size() > 0)) {
        JSONArray jarr = new JSONArray();
  ///  *if* we want full-blown:  public JSONObject Encounter.sanitizeJson(HttpServletRequest request, JSONObject jobj) throws JSONException {
        //but for *now* (see note way above) this is all we need for gallery/image display js:
        for (Encounter enc : this.getEncounters()) {
            JSONObject je = new JSONObject();
            je.put("id", enc.getID());
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
                .append("id", this.getId())
                .append("fieldStudySite",fieldStudySite)
                .append("fieldSurveyCode",fieldSurveyCode)
                .append("sightingPlatform",sightingPlatform)
                .append("decimalLatitude",decimalLatitude)
                .append("decimalLongitude",decimalLongitude)
                .append("individualCount",individualCount)
                .append("numEncounters", (encounters == null) ? 0 : encounters.size())
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

    public MediaAsset getRepresentativeMediaAsset() {
        if (getNumberEncounters() < 0) return null;
        MediaAsset rep = null;
        for (Encounter enc : this.getEncounters()) {
            if (enc.getMedia() == null) continue;
            for (MediaAsset ma : enc.getMedia()) {
                if (ma.hasKeyword("ProfilePhoto") || (rep == null)) rep = ma;
            }
        }
        return rep;
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
        if ((decimalLatitude != null) && !Util.isValidDecimalLatitude(decimalLatitude)) throw new ApiValueException("invalid latitude value", "decimalLatitude");
      this.decimalLatitude = decimalLatitude;
    }
    public Double getDecimalLongitude() {
      return decimalLongitude;
    }
    public boolean hasLatLon() {
      return (decimalLongitude!=null && decimalLatitude!=null);
    }
    public boolean hasLatOrLon() {
      return (decimalLongitude!=null || decimalLatitude!=null);
    }
    public void setDecimalLongitude(Double decimalLongitude) {
        if ((decimalLongitude != null) && !Util.isValidDecimalLongitude(decimalLongitude)) throw new ApiValueException("invalid longitude value", "decimalLongitude");
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
        if ((distance != null) && (distance < 0.0d)) throw new ApiValueException("invalid distance", "distance");
      this.distance = distance;
    }
    public Double getBearing() {
      return bearing;
    }
    public void setBearing(Double bearing) {
        if ((bearing != null) && ((bearing < 0.0d) || (bearing > 360.0d))) throw new ApiValueException("bearing out of range", "bearing");
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
    //this tries to be a way to get number even when individualCount is not set...
    public Integer getGroupSizeCalculated() {
        if (individualCount != null) return individualCount;
        if ((numCalves == null) && (numJuveniles == null) && (numAdults == null)) return getNumberEncounters();  //meh?
        int s = 0;
        if (numCalves != null) s += numCalves;
        if (numJuveniles != null) s += numJuveniles;
        if (numAdults != null) s += numAdults;
        /// not sure if we want to do something like:  if (getNumberEncounters() > s) return getNumberEncounters() ???
        return s;
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
    public void setDateFromEncounters() {
      for (Encounter enc: encounters) {
        Long millis = enc.getDateInMilliseconds();
        if (millis!=null) {
          setDateTimeLong(millis);
          return;
        }
      }
    }
    public DateTime getDateTime() {
      if (dateTimeLong == null) return null;
      return new DateTime(dateTimeLong);
    }
    public void setDateTime(DateTime dt) {
      if (dt == null) dateTimeLong = null;
      else dateTimeLong = dt.getMillis();
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
    public ArrayList<Observation> getObservationArrayList() {
      return observations;
    }
    public void addObservationArrayList(ArrayList<Observation> arr) {
      if (observations.isEmpty()) {
        observations=arr;      
      } else {
       observations.addAll(arr); 
      }
    }
    public void addObservation(Observation obs) {
      boolean found = false;
      if (observations != null && observations.size() > 0) {
        for (Observation ob : observations) {
          if (ob.getName() != null) {
            if (ob.getName().toLowerCase().trim().equals(obs.getName().toLowerCase().trim())) {
               found = true;
               this.removeObservation(obs.getName());
               observations.add(obs);
               break;
            }
          }
        } 
        if (!found) {
          observations.add(obs);        
        }
      } else {
        observations.add(obs);
      }
    }
    public Observation getObservationByName(String obName) {
      if (observations != null && observations.size() > 0) {
        for (Observation ob : observations) {
          if (ob.getName() != null) {
            if (ob.getName().toLowerCase().trim().equals(obName.toLowerCase().trim())) {
              return ob;            
            }
          }
        }
      }
      return null;
    }
    public Observation getObservationByID(String obId) {
      if (observations != null && observations.size() > 0) {
        for (Observation ob : observations) {
          if (ob.getID() != null && ob.getID().equals(obId)) {
            return ob;
          }
        }
      }
      return null;
    }
    public void removeObservation(String name) {
      int counter = 0;
      if (observations != null && observations.size() > 0) {
        System.out.println("Looking for the Observation to delete...");
        for (Observation ob : observations) {
          if (ob.getName() != null) {
            if (ob.getName().toLowerCase().trim().equals(name.toLowerCase().trim())) {
               System.out.println("Match! Trying to delete Observation "+name+" at index "+counter);
               observations.remove(counter);
               break;
            }
          }
          counter++;
        }
      }  
    } 
    
    public JSONObject sanitizeJson(HttpServletRequest request, JSONObject jobj) throws JSONException {


      jobj.put("_sanitized", true);

      return jobj;
  }
    
    public JSONObject decorateJson(HttpServletRequest request, JSONObject jobj) throws JSONException {

      if ((this.getEncounters() != null) && (this.getEncounters().size() > 0)) {
          JSONArray jarr = new JSONArray();
          boolean fullAccess = this.canUserAccess(request);
          for (Encounter enc : this.getEncounters()) {
              jarr.put(     enc.decorateJsonNoAnnots(request, enc.sanitizeJson(request, new JSONObject())     )  );
          }
          jobj.put("encounters", jarr);
      }

      return jobj;
  }


    /*
        primary (only?) use is to create a NEW OCCURRENCE from an api POST.  however, it may reference sub-objects which may or MAY NOT
        yet exists (e.g. linked Encounters); as such, it is not *purely* a creative process.

        note also that this is passed a shepherd (for sub-objects), but *does not* persist the new object.
    */
    public static Occurrence fromApiJSONObject(Shepherd myShepherd, org.json.JSONObject jsonIn) throws IOException {
        if (jsonIn == null) throw new IOException("passed null json");
        if (jsonIn.optString("id", null) != null) throw new IOException("passing id value not allowed");  //i think this will be our standard
        Occurrence occ = new Occurrence();
        occ.setId(Util.generateUUID());

/*
        occ.setStartTime(ComplexDateTime.gentlyFromIso8601(jsonIn.optString("startTime", null)));
        occ.setEndTime(ComplexDateTime.gentlyFromIso8601(jsonIn.optString("endTime", null)));
*/

        try {
            if (jsonIn.has("customFields")) occ.trySettingCustomFields(myShepherd, jsonIn.optJSONObject("customFields"), false);
        } catch (Exception ex) {
            throw new ApiValueException(ex.toString(), "customFields");
        }

        occ.setFromJSONObject("decimalLatitude", Double.class, jsonIn);
        occ.setFromJSONObject("decimalLongitude", Double.class, jsonIn);
        occ.setFromJSONObject("comments", String.class, jsonIn);
        occ.setFromJSONObject("locationId", String.class, jsonIn);  //TODO validate value?
        occ.setFromJSONObject("verbatimLocality", String.class, jsonIn);
        occ.setFromJSONObject("verbatimEventDate", String.class, jsonIn);
        occ._validateLocations();

        org.json.JSONArray jencs = jsonIn.optJSONArray("encounters");
        // we *may* want to allow this superpower for the edm, but for now lets be strict
        if ((jencs == null) || (jencs.length() < 1)) throw new ApiValueException("cannot have zero encounters", "encounters");
        if (jencs != null) {
            for (int i = 0 ; i < jencs.length() ; i++) {
                org.json.JSONObject jenc = jencs.optJSONObject(i);
                if (jenc == null) throw new IOException("invalid JSONObject at offset=" + i);
                String id = jenc.optString("id", null);  //if we have one, assume lookup; otherwise, try to create new
                Encounter enc = null;
                if (id == null) {
                    enc = Encounter.fromApiJSONObject(myShepherd, jenc);
                } else {
                    enc = myShepherd.getEncounter(id);
                    if (enc == null) throw new IOException("failed to load Encounter with id=" + id);
                }
                occ.addEncounter(enc);
            }
        }

        org.json.JSONArray jtxs = jsonIn.optJSONArray("taxonomies");
        if (jtxs != null) {
            Set<Taxonomy> siteTxs = Taxonomy.siteTaxonomies(myShepherd);
            for (int i = 0 ; i < jtxs.length() ; i++) {
                try {
                    //occ.addTaxonomy(resolveTaxonomyJSONObject(myShepherd, jtxs.optJSONObject(i), siteTxs));  //throws IllegalArgumentException if bad
                    occ.addTaxonomy(resolveTaxonomyString(myShepherd, jtxs.optString(i, null), siteTxs));  //throws IllegalArgumentException if bad
                } catch (IllegalArgumentException ex) {
                    throw new ApiValueException(ex.toString(), "taxonomies");
                }
            }
        }

/*
        org.json.JSONArray jscrs = jsonIn.optJSONArray("submissionContentReferences");
        if (jscrs != null) {
            for (int i = 0 ; i < jscrs.length() ; i++) {
                org.json.JSONObject jscr = jscrs.optJSONObject(i);
                if (jscr == null) throw new IOException("invalid JSONObject at offset=" + i);
                occ.addSubmissionContentReference(new SubmissionContentReference(jscr));
            }
        }
*/

/*  we currently DO NOT *set* values that can be derived from encounters -- these will be *reported* via GET however.....
    based on discussion 2020-10-29 w/tanya

        //these will fill out UNSET values based on encounter(s) if possible
        occ.setLatLonFromEncs(false);
        occ.setStartEndFromEncounters();  //will only set if setStartTime() and setEndTime() didnt happen
        //TODO do we also set taxonomies?  unknown at this point
        // what about locationId?  verbatimLocality?
*/

        //auto-set
        occ.setDWCDateLastModified();
        occ.setDateTimeCreated();
        occ.setVersion();
        return occ;
    }

    public org.json.JSONObject asApiJSONObject() {
        return asApiJSONObject(null);
    }

    public org.json.JSONObject asApiJSONObject(org.json.JSONObject arg) {
        String detLvl = getDetailLevel(arg);
        boolean isMaxDetail = DETAIL_LEVEL_MAX.equals(detLvl);
        org.json.JSONObject obj = new org.json.JSONObject();
        obj.put("id", this.getId());
        obj.put("version", this.getVersion());
        obj.put("comments", this.getComments());
        obj.put("createdEDM", this.getDateTimeCreated());
/*
        //ComplexDateTime st = getStartTimeSomehow();
        //ComplexDateTime et = getEndTimeSomehow();
        ComplexDateTime st = this.getStartTime();
        ComplexDateTime et = this.getEndTime();
        if (st != null) obj.put("startTime", st.toIso8601());
        if (et != null) obj.put("endTime", et.toIso8601());
*/

        if (!Util.collectionIsEmptyOrNull(this.encounters)) {
            org.json.JSONObject encCounts = new org.json.JSONObject();
            encCounts.put("sex", new org.json.JSONObject());
            //encCounts.put("behavior", new org.json.JSONObject());
            //encCounts.put("lifeStage", new org.json.JSONObject());
            encCounts.put("individuals", 0);
            org.json.JSONArray jarr = new org.json.JSONArray();
            for (Encounter enc : this.encounters) {
                if (detLvl.equals(DETAIL_LEVEL_MIN)) {
                    org.json.JSONObject j = new org.json.JSONObject();
                    j.put("id", enc.getId());
                    j.put("version", enc.getVersion());
                    jarr.put(j);
                } else {
                    // We need max data from encounters too when we get max for individual or sighting
                    org.json.JSONObject encArg = new org.json.JSONObject();
                    encArg.put("org.ecocean.Encounter", "max");
                    jarr.put(enc.asApiJSONObject(encArg));
                }
                if (isMaxDetail) {
                    String v = enc.getSex();
                    if (v != null) encCounts.getJSONObject("sex").put(v, encCounts.getJSONObject("sex").optInt(v, 0) + 1); 
                    //v = enc.getLifeStage();
                    //if (v != null) encCounts.getJSONObject("lifeStage").put(v, encCounts.getJSONObject("lifeStage").optInt(v, 0) + 1); 
                    //v = enc.getBehavior();
                    //if (v != null) encCounts.getJSONObject("behavior").put(v, encCounts.getJSONObject("behavior").optInt(v, 0) + 1); 
                    if (enc.hasMarkedIndividual()) encCounts.put("individuals", encCounts.getInt("individuals") + 1);
                }
            }
            obj.put("encounters", jarr);
            if (isMaxDetail) obj.put("encounterCounts", encCounts);
        }

        if (detLvl.equals(DETAIL_LEVEL_MIN)) return obj;  //our work is done here

/*
        if (!Util.collectionIsEmptyOrNull(this.submissionContentReferences)) {
            org.json.JSONArray jarr = new org.json.JSONArray();
            for (SubmissionContentReference scr : this.submissionContentReferences) {
                jarr.put(scr.getParameters());
            }
            obj.put("submissionContentReferences", jarr);
        }
*/

        obj.put("customFields", this.getCustomFieldJSONObject());

        if (hasLatOrLon()) {
            obj.put("decimalLatitude", getDecimalLatitude());
            obj.put("decimalLongitude", getDecimalLongitude());
        } else {
            Double[] ll = getLatLonFromEncs();
            if (ll != null) {
                obj.put("decimalLatitude", ll[0]);
                obj.put("decimalLongitude", ll[1]);
            }
        }

        obj.put("locationId", getLocationIdSomehow());
        obj.put("verbatimLocality", getVerbatimLocalitySomehow());
        obj.put("verbatimEventDate", getVerbatimEventDate());

        if (!Util.collectionIsEmptyOrNull(taxonomies)) {
            org.json.JSONArray txs = new org.json.JSONArray();
            for (Taxonomy tx : taxonomies) {
                //txs.put(tx.asApiJSONObject());
                txs.put(tx.getId());
            }
            obj.put("taxonomies", txs);
        }

        return obj;
    }

    // this overrides base, just so we can post-validate
    public org.json.JSONArray apiPatch(Shepherd myShepherd, org.json.JSONObject jsonIn) throws IOException {
        org.json.JSONArray rtn = super.apiPatch(myShepherd, jsonIn);
        if (!this.isJDODeleted()) {
            //this._validateStartEndTimes();
            this._validateLocations();
        }
        return rtn;
    }

    public org.json.JSONObject apiPatchAdd(Shepherd myShepherd, org.json.JSONObject jsonIn) throws IOException {
        String opName = jsonIn.optString("op", null);  //valuable cuz apiPatchAdd is recycled by apiPatchReplace below
        if (jsonIn == null) throw new IOException("apiPatch op=" + opName + " has null json");
        String path = jsonIn.optString("path", null);
        if (path == null) throw new IOException("apiPatch op=" + opName + " has null path");
        if (path.startsWith("/")) path = path.substring(1);
        Object valueObj = jsonIn.opt("value");
        boolean hasValue = jsonIn.has("value");

        // unsure if we should let add/replace set null, so we are only going to allow this
        // if (!hasValue || (valueObj == null)) throw new IOException("apiPatch op=" + opName + " has empty value - use op=remove");
        // okay, flopping on this via discussion w/ben on 2021-04-15 -- now will allow setting null as a value until we dont like it
        if (!hasValue) throw new IOException("apiPatch op=" + opName + " has no value - use op=remove");

        if ((valueObj != null) && valueObj.equals(null)) valueObj = null;  //convert org.json.JSONObject.NULL to java null

        org.json.JSONObject rtn = new org.json.JSONObject();
        SystemLog.debug("apiPatch op={} on {}, with path={}, valueObj={}, jsonIn={}", opName, this, path, valueObj, jsonIn);
        try {  //catch this whole block where we try to modify things!
            switch (path) {
                case "decimalLatitude":
                    this.setDecimalLatitude( (valueObj == null) ? null : tryDouble(valueObj) );
                    break;
                case "decimalLongitude":
                    this.setDecimalLongitude( (valueObj == null) ? null : tryDouble(valueObj) );
                    break;
                case "comments":
                    this.addComments((String)valueObj);
                    break;
                case "verbatimLocality":
                    this.setVerbatimLocality((String)valueObj);
                    break;
                case "verbatimEventDate":
                    this.setVerbatimEventDate((String)valueObj);
                    break;
                case "locationId":
                    this.setLocationId((String)valueObj);
                    break;
                case "taxonomies":
/*
                    // we allow for both { "id": "uuid" } and simply "uuid" as values here
                    org.json.JSONObject tj = jsonIn.optJSONObject("value");
                    if (tj == null) {
                        String tid = jsonIn.optString("value", null);
                        if (tid == null) throw new ApiValueException("must pass json object with .id or string id", "taxonomies");
                        tj = new org.json.JSONObject();
                        tj.put("id", tid);
                    }
                    try {
                        this.addTaxonomy(resolveTaxonomyJSONObject(myShepherd, tj));
                    } catch (IllegalArgumentException ex) {
                        throw new ApiValueException(ex.getMessage(), "taxonomies");
                    }
*/
                    try {
                        this.addTaxonomy(resolveTaxonomyString(myShepherd, jsonIn.optString("value", null)));
                    } catch (IllegalArgumentException ex) {
                        throw new ApiValueException(ex.getMessage(), "taxonomies");
                    }
                    break;
                case "encounters":
                    // really the only thing we can ADD is a _new_ encounter (raw data) so existing ones arent allowed
                    org.json.JSONObject ej = jsonIn.optJSONObject("value");
                    String id = ej.optString("id", null);
                    if (id != null) throw new ApiValueException("cannot add encounter by id; must use op=move instead", "encounters");
                    Encounter enc = Encounter.fromApiJSONObject(myShepherd, ej);
                    this.addEncounter(enc);
                    break;
                case "customFields":
                    this.trySettingCustomFields(myShepherd, jsonIn.optJSONObject("value"), false);
                    break;
                default:
                    throw new Exception("apiPatch op=" + opName + " unknown path " + path);
            }
        } catch (ApiValueException ex) {
            throw ex;
        } catch (ApiDeleteCascadeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IOException("apiPatch op=" + opName + " unable to modify " + this + " due to " + ex.toString());
        }
        rtn.put("op", opName);
        rtn.put("path", path);
        rtn.put("value", valueObj);
        return rtn;
    }

    //for non-array targets, this is the *same as* an add (i.e. it (re)sets the value)), so
    //  you will note many cases just pass to apiPatchAdd()
    public org.json.JSONObject apiPatchReplace(Shepherd myShepherd, org.json.JSONObject jsonIn) throws IOException {
        if (jsonIn == null) throw new IOException("apiPatchReplace has null json");
        String path = jsonIn.optString("path", null);
        if (path == null) throw new IOException("apiPatchReplace has null path");
        if (path.startsWith("/")) path = path.substring(1);
        Object valueObj = jsonIn.opt("value");
        boolean hasValue = jsonIn.has("value");

        if ((valueObj != null) && valueObj.equals(null)) valueObj = null;  //convert org.json.JSONObject.NULL to java null

        org.json.JSONObject rtn = new org.json.JSONObject();
        SystemLog.debug("apiPatchReplace on {}, with path={}, valueObj={}, jsonIn={}", this, path, valueObj, jsonIn);
        try {  //catch this whole block where we try to modify things!
            switch (path) {
                //these cases are all equivalent to add
                case "decimalLatitude":
                case "decimalLongitude":
                case "locationId":
                case "verbatimLocality":
                case "verbatimEventDate":
                    rtn.put("_chainedAdd", true);
                    this.apiPatchAdd(myShepherd, jsonIn);
                    break;
                case "comments":
                    this.setComments((String)valueObj);
                    break;
                // currently not going to support op=replace for encounter; instead should use move + remove
                case "customFields":
                    this.trySettingCustomFields(myShepherd, jsonIn.optJSONObject("value"), true);
                    break;
                default:
                    throw new Exception("apiPatchReplace unknown path " + path);
            }
        } catch (ApiValueException ex) {
            throw ex;
        } catch (ApiDeleteCascadeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IOException("apiPatchReplace unable to modify " + this + " due to " + ex.toString());
        }
        rtn.put("op", "replace");
        rtn.put("path", path);
        rtn.put("value", valueObj);
        return rtn;
    }

    public org.json.JSONObject apiPatchRemove(Shepherd myShepherd, org.json.JSONObject jsonIn) throws IOException {
        if (jsonIn == null) throw new IOException("apiPatchRemove has null json");
        String path = jsonIn.optString("path", null);
        if (path == null) throw new IOException("apiPatchRemove has null path");
        if (path.startsWith("/")) path = path.substring(1);

        org.json.JSONObject rtn = new org.json.JSONObject();
        SystemLog.debug("apiPatchRemove on {}, with path={}, jsonIn={}", this, path, jsonIn);
        try {  //catch this whole block where we try to modify things!
            switch (path) {
                case "decimalLatitude":
                    this.setDecimalLatitude(null);
                    break;
                case "decimalLongitude":
                    this.setDecimalLongitude(null);
                    break;
                case "locationId":
                    this.setLocationId(null);
                    break;
                case "verbatimLocality":
                    this.setVerbatimLocality(null);
                    break;
                case "verbatimEventDate":
                    this.setVerbatimEventDate(null);
                    break;
                case "comments":
                    this.setComments(null);
                    break;
                case "customFields":
                    this.removeCustomField(myShepherd, jsonIn.optString("value", null));
                    break;
                case "taxonomies":
/*
                    // we allow for both { "id": "uuid" } and simply "uuid" as values here
                    org.json.JSONObject tj = jsonIn.optJSONObject("value");
                    if (tj == null) {
                        String tid = jsonIn.optString("value", null);
                        if (tid == null) throw new ApiValueException("must pass json object with .id or string id", "taxonomies");
                        tj = new org.json.JSONObject();
                        tj.put("id", tid);
                    }
                    try {
                        this.removeTaxonomy(resolveTaxonomyJSONObject(myShepherd, tj));
                    } catch (IllegalArgumentException ex) {
                        throw new ApiValueException(ex.getMessage(), "taxonomies");
                    }
                    rtn.put("value", tj.getString("id"));
*/
                    try {
                        this.removeTaxonomy(resolveTaxonomyString(myShepherd, jsonIn.optString("value", null)));
                        rtn.put("value", jsonIn.optString("value", null));
                    } catch (IllegalArgumentException ex) {
                        throw new ApiValueException(ex.getMessage(), "taxonomies");
                    }
                    break;
                case "encounters":
                    String id = jsonIn.optString("value", null);
                    if (id == null) throw new ApiValueException("must pass value=id with encounter id", "encounters");
                    if (this.getNumEncounters() < 1) throw new ApiValueException("no encounters; which is bad", "encounters");  //snh
                    Encounter found = null;
                    for (Encounter enc : this.encounters) {
                        if (enc.getId().equals(id)) {
                            found = enc;
                            break;
                        }
                    }
                    if (found == null) throw new ApiValueException("invalid encounter id=" + id, "encounters");
                    rtn.put("value", id);
                    // we delete the encounter, but accommodate possible cascading implications
                    String occId = this.getId();
                    MarkedIndividual indiv = found.getIndividual();
                    String indivId = (indiv == null) ? null : indiv.getId();
                    found.delete(myShepherd,
                        jsonIn.optBoolean(KEY_DELETE_CASCADE_SIGHTING, false),
                        jsonIn.optBoolean(KEY_DELETE_CASCADE_INDIVIDUAL, false)
                    );
                    if (this.isJDODeleted()) {
                        SystemLog.warn("looks like enc id={} delete cascaded to delete occurrence id={}", id, occId);
                    } else {
                        this.removeEncounter(found);
                    }
                    if ((indiv != null) && indiv.isJDODeleted()) {
                        SystemLog.warn("looks like enc id={} delete cascaded to delete individual id={}", id, indivId);
                        rtn.put("deletedIndividual", indivId);
                    }
                    break;
                default:
                    throw new Exception("apiPatchRemove unsupported path " + path);
            }
        } catch (ApiValueException ex) {
            throw ex;
        } catch (ApiDeleteCascadeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IOException("apiPatchRemove unable to modify " + this + " due to " + ex.toString());
        }
        rtn.put("op", "remove");
        rtn.put("path", path);
        return rtn;
    }

    /*
        rule is, we die, we take our encounters with us!   so this can have a cascade effect on individual... caution!
    */
    public void delete(Shepherd myShepherd, boolean cascadeOccurrence, boolean cascadeMarkedIndividual) throws IOException {
        if (Util.collectionIsEmptyOrNull(this.encounters)) {  //this should not happen in new world!
            SystemLog.warn("no encounters found during deletion of {}", this);
            myShepherd.getPM().deletePersistent(this);
        } else {
            for (Encounter enc : this.encounters) {
                SystemLog.debug("deletion of Occurrence {} triggering deletion of Encounter {}", this.getId(), enc);
                // cascadeOccurrence=true here because we (implicitly!) want last encounter removed to cascade and delete *us* !!
                enc.delete(myShepherd, true, cascadeMarkedIndividual);
            }
        }
    }

}
