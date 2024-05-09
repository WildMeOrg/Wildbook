package org.ecocean;

import java.io.IOException;
import java.util.*;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import org.ecocean.genetics.*;
import org.ecocean.social.Membership;
import org.ecocean.social.Relationship;
import org.ecocean.social.SocialUnit;
import org.ecocean.security.Collaboration;
import org.ecocean.media.MediaAsset;
import org.ecocean.scheduled.ScheduledIndividualMerge;
import org.ecocean.servlet.ServletUtilities;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.StringUtils;
import javax.jdo.Query;

import java.text.DecimalFormat;

import org.datanucleus.api.rest.orgjson.JSONObject;
import org.datanucleus.api.rest.orgjson.JSONArray;
import org.datanucleus.api.rest.orgjson.JSONException;
import java.util.regex.*;
import org.ecocean.LocationID;
import java.math.BigInteger;

/**
 * A <code>MarkedIndividual</code> object stores the complete <code>encounter</code> data for a single marked individual in a mark-recapture study.
 * <code>Encounters</code> are added to MarkedIndividual objects as multiple encounters are associated with
 * known marked individuals.
 * <p/>
 *
 * @author Jason Holmberg
 * @version 2.0
 * @see Encounter, Shepherd
 */
public class MarkedIndividual implements java.io.Serializable {

    private String individualID = "";

    private MultiValue names;
    private static HashMap<Integer,String> NAMES_CACHE = new HashMap<Integer,String>();  //this is for searching
    private static HashMap<Integer,String> NAMES_KEY_CACHE = new HashMap<Integer,String>();

    private String alternateid;  //TODO this will go away soon
    private String legacyIndividualID;  //TODO this "could" go away "eventually"

  //additional comments added by researchers
  private String comments = "None";

  //sex of the MarkedIndividual
  public final String DEFAULT_SEX = "unknown";
  private String sex = DEFAULT_SEX;

  private String genus = "";
  private String specificEpithet;

  //unused String that allows groups of MarkedIndividuals by optional parameters
  private String seriesCode = "None";

  // the default is what was previously returned by .getNickName(), not the actual val
  public final String DEFAULT_NICKNAME = "Unassigned";
  private String nickName = "", nickNamer = "";

  //Vector of approved encounter objects added to this MarkedIndividual
  private Vector<Encounter> encounters = new Vector<Encounter>();
  //Vector of unapproved encounter objects added to this MarkedIndividual
  //private Vector unidentifiableEncounters = new Vector();

  //Vector of String filenames of additional files added to the MarkedIndividual
  private Vector dataFiles = new Vector();

  //number of encounters of this MarkedIndividual
  private int numberEncounters;

  //number of unapproved encounters (log) of this MarkedIndividual
  //private int numUnidentifiableEncounters;

  //number of locations for this MarkedIndividual
  private int numberLocations;

	//first sighting
	private String dateFirstIdentified;

	//points to thumbnail (usually of most recent encounter) - TODO someday will be superceded by MediaAsset magic[tm]
	private String thumbnailUrl;

  //a Vector of Strings of email addresses to notify when this MarkedIndividual is modified
  private Vector interestedResearchers = new Vector();

  private String dateTimeCreated;

  private String dateTimeLatestSighting;

  //FOR FAST QUERY PURPOSES ONLY - DO NOT MANUALLY SET
  private String localHaplotypeReflection;

  private String dynamicProperties;

  private String patterningCode;

  private int maxYearsBetweenResightings;

  private long timeOfBirth=0;

  private long timeOfDeath=0;


    public static final String NAMES_KEY_NICKNAME = "Nickname";
    public static final String NAMES_KEY_ALTERNATEID = "Alternate ID";
    public static final String NAMES_KEY_LEGACYINDIVIDUALID = "_legacyIndividualID_";

  public MarkedIndividual(String name, Encounter enc) {
    this();
    //this.individualID = individualID;
    this.addName(name);
    encounters.add(enc);
    //dataFiles = new Vector();
    numberEncounters = 1;
    if(enc.getSex()!=null){
      this.sex = enc.getSex();
    }
    //numUnidentifiableEncounters = 0;
    setTaxonomyFromEncounters();
    setSexFromEncounters();
    refreshDependentProperties();
    maxYearsBetweenResightings=0;
  }

  /**
   * empty constructor used by JDO Enhancer - DO NOT USE
   */
  public MarkedIndividual() {
        this.individualID = Util.generateUUID();
  }

    public MarkedIndividual(Encounter enc) {
        this();
        addEncounter(enc);
        setTaxonomyFromEncounters();
        setSexFromEncounters();
        refreshDependentProperties();
    }

  /**Adds a new encounter to this MarkedIndividual.
   *@param  newEncounter  the new <code>encounter</code> to add
   *@return true for successful addition, false for unsuccessful - Note: this change must still be committed for it to be stored in the database
   *@see  Shepherd#commitDBTransaction()
   */

    public String getId() {
        return individualID;
    }

    //this is "something to show" (by default)... it falls back to the id,
    //  which is a uuid, but chops that to the first 8 char.  sorry-not-sorry?
    //  note that if keyHint is null, default is used
    public String getDisplayName() {
        //System.out.println("[INFO]: Called MarkedIndividual.getDisplayName()");
        return getDisplayName(null);
    }

    public String getDisplayName(Object keyHint) {
      //System.out.println("[INFO]: Called MarkedIndividual.getDisplayName(keyHint)");
      return getDisplayName(keyHint, null, null);
    }

    public String getDisplayName(HttpServletRequest request) {
      //System.out.println("[INFO]: Called MarkedIndividual.getDisplayName(request)");
      return getDisplayName(null, request, null);
    }

    public String getDisplayName(HttpServletRequest request, Shepherd myShepherd) {
      //System.out.println("[INFO]: Called MarkedIndividual.getDisplayName(request,myShepherd)");
      return getDisplayName(null, request, myShepherd);
    }

    public String getDisplayName(Object keyHint, HttpServletRequest request, Shepherd myShepherd) {
        if (names == null) return null;

        //if you have a specific preferred context and have a request/shepherd, we look for that first
        if (request!=null&&request.getUserPrincipal()!=null) {
          String context = ServletUtilities.getContext(request);
          // hopefully the call was able to provide an existing shepherd, but we have to make one if not
          Shepherd nameShepherd = null;
          boolean newShepherd = false;
          try {
            if (myShepherd==null) {
              nameShepherd = new Shepherd(context);
              nameShepherd.setAction("MarkedIndividual.getDisplayName()");
              newShepherd = true;
            } else {
              nameShepherd = myShepherd;
            }
            nameShepherd.beginDBTransaction();
            User user = AccessControl.getUser(request, nameShepherd);
            if (user!=null) {
              String projectContextId = user.getProjectIdForPreferredContext();
              if (Util.stringExists(projectContextId)) {
                Project project = nameShepherd.getProject(projectContextId);
                if (project!=null) {
                  return getDisplayName(project.getProjectIdPrefix());
                }
              }
            }
          } catch (Exception e) {
            if (nameShepherd!=null) {
              nameShepherd.rollbackAndClose();
            }
          } finally {
            if (newShepherd) nameShepherd.rollbackAndClose();
          }
        }

        List<String> nameVals = getNamesList(keyHint);
        // you have provided a specific key, will get a key specific return
        if (!Util.isEmpty(nameVals)) return nameVals.get(0);


        // fallback case: try using the default keyhint
        if(getNames()!=null) {
          nameVals = getNames().getValuesDefault();
        }
        if (!Util.isEmpty(nameVals)) return nameVals.get(0);
        // second fallback: try using another nameKey
        List<String> keys = names.getSortedKeys();
        if (!Util.isEmpty(keys) && !keys.get(0).equals(keyHint)) { // need second check to disable infinite recursion
          return getDisplayName(keys.get(0));
        }
        return displayIndividualID();
    }

    public String getDefaultName() {
      return getName(MultiValue.DEFAULT_KEY_VALUE);
    }

    public String displayIndividualID() {
      if (Util.isUUID(individualID)) return individualID.substring(0,8);
      else return individualID;
    }

    public static String getDisplayNameForEncounter(Shepherd myShepherd, String encId) {
      Encounter enc = myShepherd.getEncounter(encId);
      if (enc==null) return null;
      MarkedIndividual ind = enc.getIndividual();
      if (ind==null) return null;
      return ind.getDisplayName();
    }
    public static String getDisplayNameForID(Shepherd myShepherd, String indId) {
      MarkedIndividual ind = myShepherd.getMarkedIndividualQuiet(indId);
      if (ind==null) return null;
      return ind.getDisplayName();
    }

    //MultiValue has some subtleties to it!
    public void setNames(MultiValue mv) {
        names = mv;
        refreshNamesCache();
    }
    public MultiValue getNames() {
        return names;
    }
    public List<String> getNameKeys() {
      if (names==null) return new ArrayList<String>();
      List<String> keys = names.getKeyList();
      return names.getKeyList();
    }
    public List<String> getNamesList(Object keyHint) {
        if (names == null) return null;
        return names.getValuesAsList(keyHint);
    }

    public List<String> getNamesList() {
        if (names == null) return null;
        return names.getValuesDefault();
    }

    public int numNames() {
      if (names==null) return 0;
      return names.size();
    }

    // mostly for data cleaning purposes
    // ignores legacyIndividualID bc we might wanna keep that
    public void unsetNames() {
      String legacy = getName(NAMES_KEY_LEGACYINDIVIDUALID);
      this.names = new MultiValue();
      if (Util.stringExists(legacy)) addName(NAMES_KEY_LEGACYINDIVIDUALID, legacy);

    }

    //this adds to the default
    public void addName(String name) {
        if (names == null) names = new MultiValue();
        names.addValuesDefault(name);
        refreshNamesCache();
    }
    public void addName(Object keyHint, String name) {
        if (names == null) names = new MultiValue();
        names.addValues(keyHint, name);
        refreshNamesCache();
    }

    // adds a name and inserts a comment describing who, when, and (optionally) why that was done
    public void addNameAndComment(Object keyHint, String name, User user, String message) {
      String timeStamp = new SimpleDateFormat("yyyy.MM.dd.HH.mm").format(new Date());
      String nameStr = "("+keyHint.toString()+": "+name+")";
      String commentPrefix = "On "+timeStamp+" "+user.getDisplayName()+" added name "+nameStr;
      // empty message means we just have the timestamp, name, and user
      String fullComment = (message!=null) ? (commentPrefix+": "+message) : commentPrefix;
      fullComment+="<br>"; // formatting
      this.addName(keyHint, name);
      this.addComments(fullComment);
    }
    public void addNameAndComment(Object keyHint, String name, User user) {
      addNameAndComment(keyHint, name, user, null);
    }



    public void addNameByKey(String key, String value) {
        if (names == null) names = new MultiValue();
        names.addValuesByKey(key, value);
        refreshNamesCache();
    }

    public boolean hasName(String value) {
      return (names!=null && names.hasValue(value));
    }
    public boolean hasNameSubstring(String value) {
      return (names!=null && names.hasValueSubstring(value));
    }
    public boolean hasNameKey(String query){
      boolean returnVal = false;
      if (this.getNameKeys()!=null && this.getNameKeys().contains(query)) {
        returnVal = true;
      }
      return returnVal;
    }

    public String getFirstMatchingName(String query){
      // System.out.println("entered getFirstMatchingName. Query is: " + query);
      String returnVal = "";
      if (NAMES_CACHE == null) return returnVal;
      if (query == null) return returnVal;
      int tracker = 0;
      for (Integer nid : NAMES_CACHE.keySet()) {
          if (NAMES_CACHE.get(nid).matches(query.toLowerCase()) && tracker<1){
            returnVal = NAMES_CACHE.get(nid);
            return returnVal;
            // tracker ++;
            // System.out.println("tracker is: " + tracker);
          }
      }
      return returnVal;
  }

///////////////// TODO other setters!!!!  e.g. addNameByKey(s)

    //this should be run once, as it will set (default key) values based on old field values
    //   see, e.g.  appadmin/migrateMarkedIndividualNames.jsp
    public MultiValue setNamesFromLegacy() {
        if (names == null) names = new MultiValue();

        // save the old individualID
        if (Util.shouldReplace(individualID, legacyIndividualID)) {
          setLegacyIndividualID(individualID);
        }

        if (Util.stringExists(getLegacyIndividualID())) {
          names.addValuesDefault(getLegacyIndividualID());
        }
        // use old individualID as default name moving forward

        // add nickname and alternateID to names list (labelled), but not default list
        if (Util.stringExists(nickName)) {
            names.addValuesByKey(NAMES_KEY_NICKNAME, nickName);
        }
        //note: alternateids seems to sometimes (looking at you flukebook) contain "keys" of their own, e.g. "IFAW:fluffy"
        //   in some perfect world this would be used as own keys.  :(
        if (Util.stringExists(alternateid)) {
            String[] part = alternateid.split("\\s*[;,]\\s*");
            for (int i = 0 ; i < part.length ; i++) {
                names.addValuesByKey(NAMES_KEY_ALTERNATEID, part[i]);
            }
        }
        return names;
    }


    //NOTE:  this is a little wonky in that it incorporates SQL.  deal with it.
    //you can pass null for keyHint to get default only
    public static List<String> allNamesValues(Shepherd myShepherd, Object keyHint) {
        Set<String> keys = MultiValue.generateKeys(keyHint);
        List<String> rtn = new ArrayList<String>();
        if (keys.size() < 1) return rtn;
        List<String> keysList = new ArrayList<String>(keys);  //we want a list to use .replaceAll
        keysList.replaceAll(s -> s.replaceAll("'", "''"));
        keysList.replaceAll(s -> s.replaceAll("_", "\\_"));
        keysList.replaceAll(s -> s.replaceAll(":", "_"));  //$*#(@*(! JDO PARAMETERS!!!
        String sql = "SELECT DISTINCT(\"ID_OID\") AS \"ID\" FROM \"MULTIVALUE_VALUES\" JOIN \"MARKEDINDIVIDUAL\" ON (\"NAMES_ID_OID\" = \"ID_OID\") WHERE \"KEY\" LIKE '" + StringUtils.join(keysList, "' OR \"KEY\" LIKE '") + "'";
System.out.println("MarkedIndividual.allNamesValues() sql->[" + sql + "]");
        Query q = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
        q.setClass(MultiValue.class);
        List<MultiValue> mvs = (List<MultiValue>)q.execute();
        for (MultiValue mv : mvs) {
            List<String> vals = mv.getValuesByKeys(keys);
            vals.removeAll(rtn);  //weed out duplicates
            rtn.addAll(vals);
        }
        q.closeAll();
        return rtn;
    }

  public void addIncrementalProjectId(Project project) {
    if (project!=null) {
      if (!hasNameKey(project.getProjectIdPrefix())) {
        int nextIncrement = project.getNextIndividualIdIncrement();
        try {
          addNameByKey(project.getProjectIdPrefix(), project.getNextIncrementalIndividualId());
          project.getNextIncrementalIndividualIdAndAdvance();
        } catch (Exception e) {
          if (nextIncrement<project.getNextIndividualIdIncrement()) {
            project.adjustIncrementalIndividualId(-1);
          }
          e.printStackTrace();
        }
      } else {
        System.out.println("[ERROR]: Project Id not added to Individual "+getId()+". Individual already has an Id from project "+project.getProjectIdPrefix()+".");
      }
    } else {
      System.out.println("[WARN]: Passed a null project to MarkedIndividual.addIncrementalProjectId() on "+getDisplayName()+".");
    }
  }

  public boolean addEncounter(Encounter newEncounter) {
      //get and therefore set the haplotype if necessary
      getHaplotype();

      boolean isNew=true;
      for(int i=0;i<encounters.size();i++) {
        Encounter tempEnc=(Encounter)encounters.get(i);
        if(tempEnc.getEncounterNumber().equals(newEncounter.getEncounterNumber())) {
          isNew=false;
        }
      }

      //prevent duplicate addition of encounters
      if(isNew){
        encounters.add(newEncounter);
        numberEncounters++;
        refreshDependentProperties();
      }
      setTaxonomyFromEncounters();  //will only set if has no value
      setSexFromEncounters();       //likewise
      return isNew;

 }

   public boolean addEncounterNoCommit(Encounter newEncounter) {
      //get and therefore set the haplotype if necessary
      getHaplotype();

      boolean isNew=true;
      for(int i=0;i<encounters.size();i++) {
        Encounter tempEnc=(Encounter)encounters.get(i);
        if(tempEnc.getEncounterNumber().equals(newEncounter.getEncounterNumber())) {
          isNew=false;
        }
      }

      //prevent duplicate addition of encounters
      if(isNew){
        encounters.add(newEncounter);
        numberEncounters++;
        refreshDependentProperties();
      }
      //setTaxonomyFromEncounters();  //will only set if has no value
      //setSexFromEncounters();       //likewise
      return isNew;

  }


   /**Removes an encounter from this MarkedIndividual.
   *@param  getRidOfMe  the <code>encounter</code> to remove from this MarkedIndividual
   *@return true for successful removal, false for unsuccessful - Note: this change must still be committed for it to be stored in the database
   *@see  Shepherd#commitDBTransaction()
   */
  public boolean removeEncounter(Encounter getRidOfMe) {

      numberEncounters--;
      boolean changed=false;
      for(int i=0;i<encounters.size();i++) {
        Encounter tempEnc=(Encounter)encounters.get(i);
        if(tempEnc.getEncounterNumber().equals(getRidOfMe.getEncounterNumber())) {

          encounters.remove(i);
          i--;
          changed=true;
          }
      }
      refreshDependentProperties();

      //reset haplotype
      localHaplotypeReflection=null;
      getHaplotype();

      return changed;
  }


  /**
   * Returns the total number of submitted encounters for this MarkedIndividual
   *
   * @return the total number of encounters recorded for this MarkedIndividual
   */
  public int totalEncounters() {
    return encounters.size();
  }


	public int refreshNumberEncounters() {
		this.numberEncounters = 0;
		if(encounters!=null)this.numberEncounters = encounters.size();
		return this.numberEncounters;
	}


	public String getDateFirstIdentified() {
		return this.dateFirstIdentified;
	}

	public String refreshDateFirstIdentified() {
		Encounter[] sorted = this.getDateSortedEncounters();
		if (sorted.length < 1) return null;
		Encounter first = sorted[sorted.length - 1];
		if (first.getYear() < 1) return null;
		String d = new Integer(first.getYear()).toString();
		if (first.getMonth() > 0) d = new Integer(first.getMonth()).toString() + "/" + d;
		this.dateFirstIdentified = d;
		return d;
	}

	 public String refreshDateLastestSighting() {
	    Encounter[] sorted = this.getDateSortedEncounters();
	    if (sorted.length < 1) return null;
	    Encounter last = sorted[0];
	    if (last.getYear() < 1) return null;
	    this.dateTimeLatestSighting=last.getDate();
	    return last.getDate();
	  }



  public String getThumbnailUrl() {
    return thumbnailUrl;
  }

	public String refreshThumbnailUrl(Shepherd myShepherd, HttpServletRequest req) throws org.datanucleus.api.rest.orgjson.JSONException {
    org.datanucleus.api.rest.orgjson.JSONObject thumbJson = getExemplarThumbnail(myShepherd,req);
    String thumbUrl = thumbJson.optString("url", null);
    if (Util.stringExists(thumbUrl)) this.thumbnailUrl = thumbUrl;
    return thumbUrl;
	}

/*
  public int totalLogEncounters() {
    if (unidentifiableEncounters == null) {
      //unidentifiableEncounters = new Vector();
    }
    return unidentifiableEncounters.size();
  }
*/

  public Vector returnEncountersWithGPSData(HttpServletRequest request){
    return returnEncountersWithGPSData(false,false,"context0", request);
  }
  public Vector returnEncountersWithGPSData(boolean useLocales, boolean reverseOrder,String context, HttpServletRequest request) {
    //if(unidentifiableEncounters==null) {unidentifiableEncounters=new Vector();}
    Vector haveData=new Vector();
    Encounter[] myEncs=getDateSortedEncounters(reverseOrder);

    for(int c=0;c<myEncs.length;c++) {
      String catalogNumber="";
      try {
          Encounter temp=myEncs[c];
          if(temp!=null)catalogNumber=temp.getCatalogNumber();
          if((temp.getDWCDecimalLatitude()!=null)&&(temp.getDWCDecimalLongitude()!=null)) {
            if(ServletUtilities.isUserAuthorizedForEncounter(temp, request))haveData.add(temp);
          }
          else if(useLocales && (temp.getLocationID()!=null) && (LocationID.getLatitude(temp.getLocationID(), LocationID.getLocationIDStructure())!=null) && LocationID.getLongitude(temp.getLocationID(), LocationID.getLocationIDStructure())!=null){
            if(ServletUtilities.isUserAuthorizedForEncounter(temp, request))haveData.add(temp);
          }
        }
        catch(Exception e) {
          System.out.println("Hit exception in MarkedIndividual.returnEncountersWithGPSData for Encounter: "+catalogNumber);
          e.printStackTrace();
        }
    }

    return haveData;

  }

  public boolean isDeceased() {
    //if (unidentifiableEncounters == null) {
    //  unidentifiableEncounters = new Vector();
    //}
    for (int c = 0; c < encounters.size(); c++) {
      Encounter temp = (Encounter) encounters.get(c);
      if ((temp.getLivingStatus() != null) && temp.getLivingStatus().equals("dead")) {
        return true;
      }
    }
    /*
    for (int d = 0; d < numUnidentifiableEncounters; d++) {
      Encounter temp = (Encounter) unidentifiableEncounters.get(d);
      if (temp.getLivingStatus().equals("dead")) {
        return true;
      }
    }
    */
    return false;
  }

  public boolean wasSightedInYear(int year) {
    for (int c = 0; c < encounters.size(); c++) {
      Encounter temp = (Encounter) encounters.get(c);
      if (temp.getYear() == year) {
        return true;
      }
    }
    return false;
  }

  public boolean wasSightedInYear(int year, String locCode) {
    for (int c = 0; c < encounters.size(); c++) {
      Encounter temp = (Encounter) encounters.get(c);
      if ((temp.getYear() == year) && (temp.getLocationCode().startsWith(locCode))) {
        return true;
      }
    }
    return false;
  }

  public boolean wasSightedInYearLeftTagsOnly(int year, String locCode) {
    for (int c = 0; c < encounters.size(); c++) {
      Encounter temp = (Encounter) encounters.get(c);
      if ((temp.getYear() == year) && (temp.getLocationCode().startsWith(locCode)) && (temp.getNumSpots() > 0)) {
        return true;
      }
    }
    return false;
  }

  /**
   *
   *
   * @deprecated
   */
  public double averageLengthInYear(int year) {
    int numLengths = 0;
    double total = 0;
    double avg = 0;
    for (int c = 0; c < encounters.size(); c++) {
      Encounter temp = (Encounter) encounters.get(c);
      if ((temp.getYear() == year) && ((temp.getSizeAsDouble()!=null)&&(temp.getSize() > 0))) {
        total += temp.getSize();
        numLengths++;
      }
    }
    if (numLengths > 0) {
      avg = total / numLengths;
    }
    return avg;
  }


  /**
   *
   *
   * @deprecated
   */
  public double averageMeasuredLengthInYear(int year, boolean allowGuideGuess) {
    int numLengths = 0;
    double total = 0;
    double avg = 0;
    for (int c = 0; c < encounters.size(); c++) {
      Encounter temp = (Encounter) encounters.get(c);
      if (temp.getYear() == year) {
        if ((temp.getSizeAsDouble()!=null)&&(temp.getSize() > 0)) {
          if ((temp.getSizeGuess().equals("directly measured")) || ((allowGuideGuess) && (temp.getSizeGuess().equals("guide/researcher's guess")))) {

            total += temp.getSize();
            numLengths++;
          }
        }
      }
    }
    if (numLengths > 0) {
      avg = total / numLengths;
    }
    return avg;
  }

  //use the index identifier, not the full name of the keyword
  public boolean isDescribedByPhotoKeyword(Keyword word) {
    for (int c = 0; c < encounters.size(); c++) {
      Encounter temp = (Encounter) encounters.get(c);
      if(temp.hasKeyword(word)){return true;}
    }
    return false;
  }

  /*
  public boolean hasApprovedEncounters() {
    for (int c = 0; c < encounters.size(); c++) {
      Encounter temp = (Encounter) encounters.get(c);
      if (temp.getState()!=null) {
        return true;
      }
    }
    return false;
  }
  */

  public boolean wasSightedInMonth(int year, int month) {
    for (int c = 0; c < encounters.size(); c++) {
      Encounter temp = (Encounter) encounters.get(c);
      if ((temp.getYear() == year) && (temp.getMonth() == month)) {
        return true;
      }
    }
    return false;
  }


  public boolean wasSightedInPeriod(int m_startYear, int m_startMonth, int m_endYear, int m_endMonth) {
    int endYear = m_endYear;
    int endMonth = m_endMonth;

    int startYear = m_startYear;
    int startMonth = m_startMonth;


    GregorianCalendar gcMin=new GregorianCalendar(startYear, startMonth, 1);
    GregorianCalendar gcMax=new GregorianCalendar(endYear, endMonth, 31);



    for (int c = 0; c < encounters.size(); c++) {
      Encounter temp = (Encounter) encounters.get(c);

        if((temp.getDateInMilliseconds()!=null)&&(temp.getDateInMilliseconds()>=gcMin.getTimeInMillis())&&(temp.getDateInMilliseconds()<=gcMax.getTimeInMillis())){
          return true;
        }
    }
    return false;
  }

  public boolean wasSightedInPeriod(int m_startYear, int m_startMonth, int m_startDay, int m_endYear, int m_endMonth, int m_endDay, String locCode) {
    int endYear = m_endYear;
    int endMonth = m_endMonth;
    int endDay = m_endDay;
    int startYear = m_startYear;
    int startMonth = m_startMonth;
    int startDay = m_startDay;

    GregorianCalendar gcMin=new GregorianCalendar(startYear, startMonth, startDay);
    GregorianCalendar gcMax=new GregorianCalendar(endYear, endMonth, endDay);



    for (int c = 0; c < encounters.size(); c++) {
      Encounter temp = (Encounter) encounters.get(c);

      if ((temp.getLocationID()!=null)&&(!temp.getLocationID().trim().equals(""))&&(temp.getLocationID().trim().equals(locCode))) {

        if((temp.getDateInMilliseconds()!=null)&&(temp.getDateInMilliseconds()>=gcMin.getTimeInMillis())&&(temp.getDateInMilliseconds()<=gcMax.getTimeInMillis())){
          return true;
        }
      }
    }
    return false;
  }

  public boolean wasSightedInPeriod(int m_startYear, int m_startMonth, int m_startDay, int m_endYear, int m_endMonth, int m_endDay) {
    int endYear = m_endYear;
    int endMonth = m_endMonth;
    int endDay = m_endDay;
    int startYear = m_startYear;
    int startMonth = m_startMonth;
    int startDay = m_startDay;
    GregorianCalendar gcMin=new GregorianCalendar(startYear, startMonth, startDay);
    GregorianCalendar gcMax=new GregorianCalendar(endYear, endMonth, endDay);
    for (int c = 0; c < encounters.size(); c++) {
      Encounter temp = (Encounter) encounters.get(c);
      if((temp.getDateInMilliseconds()!=null)&&(temp.getDateInMilliseconds()>=gcMin.getTimeInMillis())&&(temp.getDateInMilliseconds()<=gcMax.getTimeInMillis())){
          return true;
      }
    }
    return false;
  }

  public boolean wasSightedInPeriodLeftOnly(int m_startYear, int m_startMonth, int m_endYear, int m_endMonth) {
    int endYear = m_endYear;
    int endMonth = m_endMonth;

    int startYear = m_startYear;
    int startMonth = m_startMonth;

    GregorianCalendar gcMin=new GregorianCalendar(startYear, startMonth, 1);
    GregorianCalendar gcMax=new GregorianCalendar(endYear, endMonth, 31);



    for (int c = 0; c < encounters.size(); c++) {
      Encounter temp = (Encounter) encounters.get(c);

        if((temp.getDateInMilliseconds()!=null)&&(temp.getDateInMilliseconds()>=gcMin.getTimeInMillis())&&(temp.getDateInMilliseconds()<=gcMax.getTimeInMillis())&&(temp.getNumSpots()>0)){
          return true;
        }
    }
    return false;
  }


  /**
   * Returns the user-input name of the MarkedIndividual, which is also used as an Index in the FastObjects database
   *
   * @return the name of the MarkedIndividual as a String
   */
    public String getName() {
        System.out.println("INFO: MarkedIndividual.getName() now is alias for .getDisplayName()");
        return getDisplayName();
    }

    public String getName(Object keyHint) {
      if (names==null) return null;
      return names.getValue(keyHint);
    }

  public String getIndividualID() {
      return individualID;
  }

/*
    now part of migration of all names to .names MultiValue property
    we still want "a (singular) nickname" and notably a nickNamer
    so we set nickname to a special keyed value, but leave nicknameR as its own property

    TODO someday(?) we might want to move the nicknamer to a key, so we could have multiple nicknamers/nicknames
*/
    public String getNickName() {
        if (names == null) return null;
        List<String> many = names.getValuesByKey(NAMES_KEY_NICKNAME);
        if ((many == null) || (many.size() < 1)) return null;  //"should" only have 0 or 1 nickname
        return many.get(0);
    }

    public void setNickName(String newName) {
        this.addNameByKey(NAMES_KEY_NICKNAME, newName);
    }

  public String getNickNamer() {
    if (nickNamer != null) {
      return nickNamer;
    } else {
      return "Unknown";
    }
  }

  public void setNickNamer(String newNamer) {
    nickNamer = newNamer;
  }

    public void setName(String newName) {
        this.addName(newName);
    }

    public void setLegacyIndividualID(String id) {
      legacyIndividualID = id;
    }

    public String getLegacyIndividualID() {
      return legacyIndividualID;
    }


    public void setIndividualID(String id) {
        individualID = id;
    }

    public void setAlternateID(String alt) {
        System.out.println("WARNING: indiv.setAlternateID() is depricated, please consider modifying .names according to a hint/context");
        this.addNameByKey(NAMES_KEY_ALTERNATEID, alt);
    }

  /**
   * Returns the specified encounter, where the encounter numbers range from 0 to n-1, where n is the total number of encounters stored
   * for this MarkedIndividual.
   *
   * @return the encounter at position i in the stored Vector of encounters
   * @param  i  the specified encounter number, where i=0...(n-1)
   */
  public Encounter getEncounter(int i) {
    return (Encounter) encounters.get(i);
  }

  /*
  public Encounter getLogEncounter(int i) {
    return (Encounter) unidentifiableEncounters.get(i);
  }
  */
  public int numEncounters() {
    if (encounters==null) return 0;
    return encounters.size();
  }
  /**
   * Returns the complete Vector of stored encounters for this MarkedIndividual.
   *
   * @return a Vector of encounters
   * @see java.util.Vector
   */
  public Vector<Encounter> getEncounters() {
    return encounters;
  }
  public int getNumEncounters() {
    if (encounters==null) return 0;
    return encounters.size();
  }
  public List<Encounter> getEncounterList() {
    List<Encounter> encs = new ArrayList<Encounter>();
    for (Object obj: encounters) {
      encs.add((Encounter) obj);
    }
    return encs;
  }

    //you can choose the order of the EncounterDateComparator
    public Encounter[] getDateSortedEncounters(boolean reverse) {
    Vector final_encs = new Vector();
    if(encounters!=null){
      for (int c = 0; c < encounters.size(); c++) {
        Encounter temp = (Encounter) encounters.get(c);
        final_encs.add(temp);
      }
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
  public Encounter[] getDateSortedEncounters(int limit) {
    return (getDateSortedEncounters(false, limit));
  }

  // for the scenario where you don't have permission to view this, but you'd like to
  public String getAnEncounterOwner() {
    for (Encounter enc: encounters) {
      if (Util.stringExists(enc.getAssignedUsername())) return enc.getAssignedUsername();
    }
    return null;
  }

  public Encounter[] getDateSortedEncounters(boolean reverse, int limit) {
    Encounter[] allEncs = getDateSortedEncounters(reverse);
    return (Arrays.copyOfRange(allEncs, 0, Math.min(limit,allEncs.length)));
  }

  public static String getWebUrl(String individualID, HttpServletRequest req) {
    return (CommonConfiguration.getServerURL(req)+"/individuals.jsp?number="+individualID);
  }
  public String getWebUrl(HttpServletRequest req) {
    return getWebUrl(this.getIndividualID(), req);
  }

  // public String getHyperlink(HttpServletRequest req) {
  //   return "<a href=\""+getWebUrl(req)+"\"> Individual "+getDisplayName(req)+ "</a>";
  // }



  //sorted with the most recent first
  public Encounter[] getDateSortedEncounters() {return getDateSortedEncounters(false);}


  //preserved for legacy purposes
 /** public Encounter[] getDateSortedEncounters(boolean includeLogEncounters) {
    return getDateSortedEncounters();
  }
  */

  /*
  public Vector getUnidentifiableEncounters() {
    if (unidentifiableEncounters == null) {
      unidentifiableEncounters = new Vector();
    }
    return unidentifiableEncounters;
  }
  */

  /**
   * Returns any additional, general comments recorded for this MarkedIndividual as a whole.
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
   * Adds any general comments recorded for this MarkedIndividual as a whole.
   *
   * @return a String of comments
   */
  public void addComments(String newComments) {
    System.out.println("addComments called. oldComments="+comments+" and new comments = "+newComments);
    if (Util.stringExists(comments)) {
      comments += newComments;
    } else {
      comments = newComments;
    }
  }
  public void setComments(String comments) {
    this.comments = comments;
  }

  /**
   * Returns the complete Vector of stored satellite tag data files for this MarkedIndividual.
   *
   * @return a Vector of Files
   * @see java.util.Vector
   */
  public Vector getDataFiles() {
    return dataFiles;
  }

  /**
   * Returns the sex of this MarkedIndividual.
   *
   * @return a String
   */
  public String getSex() {
    return sex;
  }

  /**
   * Sets the sex of this MarkedIndividual.
   */
  public void setSex(String newSex) {
    if(newSex!=null){sex = newSex;}
    else{sex=null;}

  }

  public boolean hasSex() {
    String thisSex = getSex();
    return (thisSex!=null && !thisSex.equals(DEFAULT_SEX));
  }

    public String getGenus() {
        return genus;
    }

    public void setGenus(String newGenus) {
        genus = newGenus;
    }

    public String getSpecificEpithet() {
        return specificEpithet;
    }

    public void setSpecificEpithet(String newEpithet) {
        specificEpithet = newEpithet;
    }

    public void setTaxonomyString(String tax) {
      if (tax == null) return;
      String[] parts = tax.split(" ");
      if (parts.length < 2) {
        setSpecificEpithet(tax);
      } else {
        setGenus(parts[0]);
        setSpecificEpithet(parts[1]);
      }
    }

    public String getTaxonomyString() {
        return Util.taxonomyString(getGenus(), getSpecificEpithet());
    }

    ///this is really only for when dont have a value set; i.e. it should not be run after set on the instance;
    /// therefore we dont allow that unless you pass boolean true to force it
    ///  TODO we only pick first one - perhaps smarter would be to check all encounters and pick dominant one?
    public String setTaxonomyFromEncounters(boolean force) {
        if (!force && ((genus != null) || (specificEpithet != null))) return getTaxonomyString();
        if ((encounters == null) || (encounters.size() < 1)) return getTaxonomyString();
        for (Encounter enc : encounters) {
            if ((enc.getGenus() != null) && (enc.getSpecificEpithet() != null)) {
                genus = enc.getGenus();
                specificEpithet = enc.getSpecificEpithet();
                return getTaxonomyString();
            }
        }
        return getTaxonomyString();
    }
    public String setTaxonomyFromEncounters() {
        return setTaxonomyFromEncounters(false);
    }

    //similar to above
    public String setSexFromEncounters(boolean force) {
        if (!force && (sex != null) && !sex.equals("unknown")) return getSex();
        if ((encounters == null) || (encounters.size() < 1)) return getSex();
        for (Encounter enc : encounters) {
            if (enc.getSex() != null && !enc.getSex().equals("unknown")) {
                sex = enc.getSex();
                return getSex();
            }
        }
        return getSex();
    }
    public String setSexFromEncounters() {
        return setSexFromEncounters(false);
    }

  public double getLastEstimatedSize() {
    double lastSize = 0;
    for (int c = 0; c < encounters.size(); c++) {
      Encounter temp = (Encounter) encounters.get(c);
      if ((temp.getSizeAsDouble()!=null)&&(temp.getSize() > lastSize)) {
        lastSize = temp.getSize();
      }
    }
    return lastSize;
  }

    public String getLastLifeStage() {
        Encounter[] encs = this.getDateSortedEncounters();
        if ((encs == null) || (encs.length < 1)) return null;
        for (int i = 0 ; i < encs.length ; i++) {
            if (encs[i].getLifeStage() != null) return encs[i].getLifeStage();
        }
        return null;
    }

  public boolean wasSightedInLocationCode(String locationCode) {

        for (int c = 0; c < encounters.size(); c++) {
          try{
            Encounter temp = (Encounter) encounters.get(c);

            if ((temp.getLocationID()!=null)&&(!temp.getLocationID().trim().equals(""))&&(temp.getLocationID().trim().equals(locationCode))) {
              return true;
            }
          }
          catch(NullPointerException npe){return false;}
        }

        return false;
    }



  public ArrayList<String> participatesInTheseVerbatimEventDates() {
    ArrayList<String> vbed = new ArrayList<String>();
    for (int c = 0; c < encounters.size(); c++) {
      Encounter temp = (Encounter) encounters.get(c);
      if ((temp.getVerbatimEventDate() != null) && (!vbed.contains(temp.getVerbatimEventDate()))) {
        vbed.add(temp.getVerbatimEventDate());
      }
    }
    return vbed;
  }

    public ArrayList<String> participatesInTheseLocationIDs() {
      ArrayList<String> vbed = new ArrayList<String>();
      for (int c = 0; c < encounters.size(); c++) {
        Encounter temp = (Encounter) encounters.get(c);
        if ((temp.getLocationID() != null) && (!vbed.contains(temp.getLocationID()))) {
          vbed.add(temp.getLocationID());
        }
      }
      return vbed;
  }


	public int getNumberLocations() {
		return this.numberLocations;
	}

	public int refreshNumberLocations() {
		this.numberLocations = this.participatesInTheseLocationIDs().size();
		return this.numberLocations;
	}

  public boolean wasSightedInVerbatimEventDate(String ved) {
    for (int c = 0; c < encounters.size(); c++) {
      Encounter temp = (Encounter) encounters.get(c);
      if ((temp.getVerbatimEventDate() != null) && (temp.getVerbatimEventDate().equals(ved))) {
        return true;
      }
    }
    return false;
  }

  public boolean wasSightedByUser(String user) {
    for (int c = 0; c < encounters.size(); c++) {
      Encounter temp = (Encounter) encounters.get(c);
      if ((temp.getSubmitterID() != null) && (temp.getSubmitterID().equals(user))) {
        return true;
      }
    }
    return false;
  }

  public int getMaxNumYearsBetweenSightings(){
    return maxYearsBetweenResightings;
  }

  public int getEarliestSightingYear() {
    int lowestYear = 5000;
    for (int c = 0; c < encounters.size(); c++) {
      Encounter temp = (Encounter) encounters.get(c);
      if ((temp.getYear() < lowestYear)&&(temp.getYear()>0)){
        lowestYear = temp.getYear();
      }
    }
    return lowestYear;
  }

  public long getEarliestSightingTime() {
    long lowestTime = GregorianCalendar.getInstance().getTimeInMillis();
    for (int c = 0; c < encounters.size(); c++) {
      Encounter temp = (Encounter) encounters.get(c);
      if ((temp.getDateInMilliseconds()!=null)&&(temp.getDateInMilliseconds() < lowestTime)&&(temp.getYear()>0)) lowestTime = temp.getDateInMilliseconds();
    }
    return lowestTime;
  }

  public String getSeriesCode() {
    return seriesCode;
  }

  public Vector getInterestedResearchers() {
    return interestedResearchers;
  }

  public void addInterestedResearcher(String email) {
    if(interestedResearchers==null){interestedResearchers=new Vector();}
      interestedResearchers.add(email);

  }

  public void removeInterestedResearcher(String email) {
    if(interestedResearchers!=null){
      for (int i = 0; i < interestedResearchers.size(); i++) {
        String rName = (String) interestedResearchers.get(i);
        if (rName.equals(email)) {
          interestedResearchers.remove(i);
        }
      }
    }
  }

  public void setSeriesCode(String newCode) {
    seriesCode = newCode;
  }

  /**
   * Adds a satellite tag data file for this MarkedIndividual.
   *
   * @param  dataFile  the satellite tag data file to be added
   */
  public void addDataFile(String dataFile) {
    if(dataFiles==null){dataFiles = new Vector();}
    dataFiles.add(dataFile);
  }

  /**
   * Removes a satellite tag data file for this MarkedIndividual.
   *
   * @param  dataFile  The satellite data file, as a String, to be removed.
   */
  public void removeDataFile(String dataFile) {
    if(dataFiles!=null)
    {
      dataFiles.remove(dataFile);
    }
  }

  public int getNumberTrainableEncounters() {
    int count = 0;
    for (int iter = 0; iter < encounters.size(); iter++) {
      Encounter enc = (Encounter) encounters.get(iter);
      if ((enc.getSpots()!=null)&&(enc.getSpots().size() > 0)) {
        count++;
      }
    }
    return count;
  }


  public int getNumberRightTrainableEncounters() {
    int count = 0;
    for (int iter = 0; iter < encounters.size(); iter++) {
      Encounter enc = (Encounter) encounters.get(iter);
      if (enc.getRightSpots().size() > 0) {
        count++;
      }
    }
    return count;
  }

  public Vector getTrainableEncounters() {
    int count = 0;
    Vector results = new Vector();
    for (int iter = 0; iter < encounters.size(); iter++) {
      Encounter enc = (Encounter) encounters.get(iter);
      if (enc.getSpots().size() > 0) {
        results.add(enc);
      }
    }
    return results;
  }

  public Vector getRightTrainableEncounters() {
    int count = 0;
    Vector results = new Vector();
    for (int iter = 0; iter < encounters.size(); iter++) {
      Encounter enc = (Encounter) encounters.get(iter);
      if (enc.getRightSpots().size() > 0) {
        results.add(enc);
      }
    }
    return results;
  }

  /*public int getFirstTrainingEncounter() {
     for(int iter=0;iter<encounters.size(); iter++) {
       encounter enc=(encounter)encounters.get(iter);
       if (enc.getSpots()!=null) {return iter;}
       }
     return 0;
   }*/

  /*public int getSecondTrainingEncounter() {
     for(int iter=(getFirstTrainingEncounter()+1);iter<encounters.size(); iter++) {
       encounter enc=(encounter)encounters.get(iter);
       if (enc.getSpots()!=null) {return iter;}
       }
     return 0;
   }*/


  //months 1-12, days, 1-31
  /**
   *
   *
   * @deprecated
   */
  public double avgLengthInPeriod(int m_startYear, int m_startMonth, int m_endYear, int m_endMonth) {

    double avgLength = 0;
    int numMeasurements = 0;

    int endYear = m_endYear;
    int endMonth = m_endMonth;
    int startYear = m_startYear;
    int startMonth = m_startMonth;

    //test that start and end dates are not reversed
    if (endYear < startYear) {
      endYear = m_startYear;
      endMonth = m_startMonth;
      startYear = m_endYear;
      startMonth = m_endMonth;
    } else if ((endYear == startYear) && (endMonth < startMonth)) {
      endYear = m_startYear;
      endMonth = m_startMonth;
      startYear = m_endYear;
      startMonth = m_endMonth;
    }

    for (int c = 0; c < encounters.size(); c++) {
      Encounter temp = (Encounter) encounters.get(c);
      if ((temp.getYear() > startYear) && (temp.getYear() < endYear)) {
        if ((temp.getSizeAsDouble()!=null)&&(temp.getSize() > 0)) {
          avgLength += temp.getSize();
          numMeasurements++;
        }
      } else if ((temp.getYear() == startYear) && (temp.getYear() < endYear) && (temp.getMonth() >= startMonth)) {
        if ((temp.getSizeAsDouble()!=null)&&(temp.getSize() > 0)) {
          avgLength += temp.getSize();
          numMeasurements++;
        }
      } else if ((temp.getYear() > startYear) && (temp.getYear() == endYear) && (temp.getMonth() <= endMonth)) {
        if ((temp.getSizeAsDouble()!=null)&&(temp.getSize() > 0)) {
          avgLength += temp.getSize();
          numMeasurements++;
        }
      } else if ((temp.getYear() >= startYear) && (temp.getYear() <= endYear) && (temp.getMonth() >= startMonth) && (temp.getMonth() <= endMonth)) {
        if ((temp.getSizeAsDouble()!=null)&&(temp.getSize() > 0)) {
          avgLength += temp.getSize();
          numMeasurements++;
        }
      }


    }
    if (numMeasurements > 0) {
      return (avgLength / numMeasurements);
    } else {
      return 0.0;
    }
  }

  public Double getAverageMeasurementInPeriod(int m_startYear, int m_startMonth, int m_endYear, int m_endMonth, String measurementType) {

    double avgMeasurement = 0;
    int numMeasurements = 0;
    int endYear = m_endYear;
    int endMonth = m_endMonth;
    int startYear = m_startYear;
    int startMonth = m_startMonth;

    //test that start and end dates are not reversed
    if (endYear < startYear) {
      endYear = m_startYear;
      endMonth = m_startMonth;
      startYear = m_endYear;
      startMonth = m_endMonth;
    } else if ((endYear == startYear) && (endMonth < startMonth)) {
      endYear = m_startYear;
      endMonth = m_startMonth;
      startYear = m_endYear;
      startMonth = m_endMonth;
    }

    for (int c = 0; c < encounters.size(); c++) {
      Encounter temp = (Encounter) encounters.get(c);
      if(temp.hasMeasurement(measurementType)){
        List<Measurement> measures=temp.getMeasurements();
        if ((temp.getYear() > startYear) && (temp.getYear() < endYear)) {
          if (temp.getMeasurement(measurementType)!=null) {
            avgMeasurement += temp.getMeasurement(measurementType).getValue();
            numMeasurements++;
          }
        }
        else if ((temp.getYear() == startYear) && (temp.getYear() < endYear) && (temp.getMonth() >= startMonth)) {
          if (temp.getMeasurement(measurementType)!=null){
            avgMeasurement += temp.getMeasurement(measurementType).getValue();
            numMeasurements++;
          }
        }
        else if ((temp.getYear() > startYear) && (temp.getYear() == endYear) && (temp.getMonth() <= endMonth)) {
          if (temp.getMeasurement(measurementType)!=null) {
            avgMeasurement += temp.getMeasurement(measurementType).getValue();
            numMeasurements++;
          }
        }
        else if ((temp.getYear() >= startYear) && (temp.getYear() <= endYear) && (temp.getMonth() >= startMonth) && (temp.getMonth() <= endMonth)) {
          if (temp.getMeasurement(measurementType)!=null) {
            avgMeasurement += temp.getMeasurement(measurementType).getValue();
            numMeasurements++;
          }
        }
      }
    }
    if (numMeasurements > 0) {
      return (new Double(avgMeasurement / numMeasurements));
    }
    else {
      return null;
    }
  }

  public Double getAverageBiologicalMeasurementInPeriod(int m_startYear, int m_startMonth, int m_endYear, int m_endMonth, String measurementType) {

    double avgMeasurement = 0;
    int numMeasurements = 0;
    int endYear = m_endYear;
    int endMonth = m_endMonth;
    int startYear = m_startYear;
    int startMonth = m_startMonth;

    //test that start and end dates are not reversed
    if (endYear < startYear) {
      endYear = m_startYear;
      endMonth = m_startMonth;
      startYear = m_endYear;
      startMonth = m_endMonth;
    } else if ((endYear == startYear) && (endMonth < startMonth)) {
      endYear = m_startYear;
      endMonth = m_startMonth;
      startYear = m_endYear;
      startMonth = m_endMonth;
    }

    for (int c = 0; c < encounters.size(); c++) {
      Encounter enc = (Encounter) encounters.get(c);
      if((enc.getTissueSamples()!=null)&&(enc.getTissueSamples().size()>0)){
        List<TissueSample> samples=enc.getTissueSamples();
        int numTissueSamples=samples.size();
        for(int h=0;h<numTissueSamples;h++){
          TissueSample temp=samples.get(h);

          if(temp.hasMeasurement(measurementType)){
            List<BiologicalMeasurement> measures=temp.getBiologicalMeasurements();
            if ((enc.getYear() > startYear) && (enc.getYear() < endYear)) {
              if (temp.getBiologicalMeasurement(measurementType)!=null) {
                avgMeasurement += temp.getBiologicalMeasurement(measurementType).getValue();
                numMeasurements++;
              }
            }
            else if ((enc.getYear() == startYear) && (enc.getYear() < endYear) && (enc.getMonth() >= startMonth)) {
              if (temp.getBiologicalMeasurement(measurementType)!=null){
                avgMeasurement += temp.getBiologicalMeasurement(measurementType).getValue();
                numMeasurements++;
              }
            }
            else if ((enc.getYear() > startYear) && (enc.getYear() == endYear) && (enc.getMonth() <= endMonth)) {
              if (temp.getBiologicalMeasurement(measurementType)!=null) {
                avgMeasurement += temp.getBiologicalMeasurement(measurementType).getValue();
                numMeasurements++;
              }
            }
            else if ((enc.getYear() >= startYear) && (enc.getYear() <= endYear) && (enc.getMonth() >= startMonth) && (enc.getMonth() <= endMonth)) {
              if (temp.getBiologicalMeasurement(measurementType)!=null) {
                avgMeasurement += temp.getBiologicalMeasurement(measurementType).getValue();
                numMeasurements++;
              }
            }
          }
        }
      }
    }
    if (numMeasurements > 0) {
      return (new Double(avgMeasurement / numMeasurements));
    }
    else {
      return null;
    }
  }

  public String getDateTimeCreated() {
    if (dateTimeCreated != null) {
      return dateTimeCreated;
    }
    return "";
  }

  public String getDateLatestSighting() {
    if (dateTimeLatestSighting != null) {
      return dateTimeLatestSighting;
    }
    return "";
  }

  public void setDateTimeCreated(String time) {
    dateTimeCreated = time;
  }

  public void setDateTimeLatestSighting(String time) {
    dateTimeLatestSighting = time;
  }


  public String getDynamicProperties() {
    return dynamicProperties;
  }

  public void setDynamicProperties(String dprop) {
    this.dynamicProperties = dprop;
  }

  public void setDynamicProperty(String name, String value) {
    name = name.replaceAll(";", "_").trim().replaceAll("%20", " ");
    value = value.replaceAll(";", "_").trim();

    if (dynamicProperties == null) {
      dynamicProperties = name + "=" + value + ";";
    } else {

      //let's create a TreeMap of the properties
      TreeMap<String, String> tm = new TreeMap<String, String>();
      StringTokenizer st = new StringTokenizer(dynamicProperties, ";");
      while (st.hasMoreTokens()) {
        String token = st.nextToken();
        int equalPlace = token.indexOf("=");
        tm.put(token.substring(0, equalPlace), token.substring(equalPlace + 1));
      }
      if (tm.containsKey(name)) {
        tm.remove(name);
        tm.put(name, value);

        //now let's recreate the dynamicProperties String
        String newProps = tm.toString();
        int stringSize = newProps.length();
        dynamicProperties = newProps.substring(1, (stringSize - 1)).replaceAll(", ", ";") + ";";
      } else {
        dynamicProperties = dynamicProperties + name + "=" + value + ";";
      }
    }
  }

  public String getDynamicPropertyValue(String name){
    if(dynamicProperties!=null){
      name=name.replaceAll("%20", " ");
      //let's create a TreeMap of the properties
      TreeMap<String,String> tm=new TreeMap<String,String>();
      StringTokenizer st=new StringTokenizer(dynamicProperties, ";");
      while(st.hasMoreTokens()){
        String token = st.nextToken();
        int equalPlace=token.indexOf("=");
        try{
          tm.put(token.substring(0,equalPlace), token.substring(equalPlace+1));
        }
        catch(IndexOutOfBoundsException ioob){}
      }
      if(tm.containsKey(name)){return tm.get(name);}
    }
    return null;
  }

  public void removeDynamicProperty(String name) {
    name = name.replaceAll(";", "_").trim().replaceAll("%20", " ");
    if (dynamicProperties != null) {

      //let's create a TreeMap of the properties
      TreeMap<String, String> tm = new TreeMap<String, String>();
      StringTokenizer st = new StringTokenizer(dynamicProperties, ";");
      while (st.hasMoreTokens()) {
        String token = st.nextToken();
        int equalPlace = token.indexOf("=");
        tm.put(token.substring(0, (equalPlace)), token.substring(equalPlace + 1));
      }
      if (tm.containsKey(name)) {
        tm.remove(name);

        //now let's recreate the dynamicProperties String
        String newProps = tm.toString();
        int stringSize = newProps.length();
        dynamicProperties = newProps.substring(1, (stringSize - 1)).replaceAll(", ", ";") + ";";
      }
    }
  }

  public ArrayList<Keyword> getAllAppliedKeywordNames(Shepherd myShepherd) {
    ArrayList<Keyword> al = new ArrayList<Keyword>();
    int numEncounters = encounters.size();
    for (int i = 0; i < numEncounters; i++) {
      Encounter enc = (Encounter) encounters.get(i);
      Iterator<Keyword> it = myShepherd.getAllKeywords();
      while (it.hasNext()) {
        Keyword word = it.next();
        if (enc.hasKeyword(word) && (!al.contains(word))) {
          al.add(word);
        }
      }
    }
    return al;
  }

  public ArrayList<TissueSample> getAllTissueSamples() {
    ArrayList<TissueSample> al = new ArrayList<TissueSample>();
    if(encounters!=null){
    int numEncounters = encounters.size();
    for (int i = 0; i < numEncounters; i++) {
      Encounter enc = (Encounter) encounters.get(i);
      if(enc.getTissueSamples()!=null){
        List<TissueSample> list = enc.getTissueSamples();
        if(list.size()>0){
          al.addAll(list);
        }
      }
    }
    return al;
    }
    return null;
  }

  public ArrayList<SinglePhotoVideo> getAllSinglePhotoVideo() {
    ArrayList<SinglePhotoVideo> al = new ArrayList<SinglePhotoVideo>();
    int numEncounters = encounters.size();
    for (int i = 0; i < numEncounters; i++) {
      Encounter enc = (Encounter) encounters.get(i);
      if(enc.getSinglePhotoVideo()!=null){
        List<SinglePhotoVideo> list = enc.getSinglePhotoVideo();
        if(list.size()>0){
          al.addAll(list);
        }
      }
    }
    return al;
  }

  public ArrayList<String> getAllValuesForDynamicProperty(String propertyName) {
    ArrayList<String> listPropertyValues = new ArrayList<String>();

    //first, check if the individual has the property applied
    if (getDynamicPropertyValue(propertyName) != null) {
      listPropertyValues.add(getDynamicPropertyValue(propertyName));
    }

    //next check the encounters
    int numEncounters = encounters.size();
    for (int i = 0; i < numEncounters; i++) {
      Encounter enc = (Encounter) encounters.get(i);
      if (enc.getDynamicPropertyValue(propertyName) != null) {
        listPropertyValues.add(enc.getDynamicPropertyValue(propertyName));
      }
    }
    return listPropertyValues;
  }

  /**
  Returns the patterning type evident on this MarkedIndividual instance.

  */
  public String getPatterningCode(){

    int numEncs=encounters.size();
    for(int i=0;i<numEncs;i++){
      Encounter enc=(Encounter)encounters.get(i);
      if(enc.getPatterningCode()!=null){return enc.getPatterningCode();}
    }
    return null;
  }

  /**
  Sets the patterning type evident on this MarkedIndividual instance.

  */
  public void setPatterningCode(String newCode){this.patterningCode=newCode;}

  public void resetMaxNumYearsBetweenSightings(){
    int maxYears=0;
    int lowestYear=3000;
    int highestYear=0;
    if(encounters!=null){
      for(int c=0;c<encounters.size();c++) {
        Encounter temp=(Encounter)encounters.get(c);
        if((temp.getYear()<lowestYear)&&(temp.getYear()>0)) lowestYear=temp.getYear();
        if(temp.getYear()>highestYear) highestYear=temp.getYear();
        maxYears=highestYear-lowestYear;
        if(maxYears<0){maxYears=0;}
       }
    }
    maxYearsBetweenResightings=maxYears;
  }



  public String sidesSightedInPeriod(int m_startYear, int m_startMonth, int m_startDay, int m_endYear, int m_endMonth, int m_endDay, String locCode) {
    int endYear = m_endYear;
    int endMonth = m_endMonth;
    int endDay = m_endDay;
    int startYear = m_startYear;
    int startMonth = m_startMonth;
    int startDay = m_startDay;

    GregorianCalendar gcMin=new GregorianCalendar(startYear, startMonth-1, startDay);
    GregorianCalendar gcMax=new GregorianCalendar(endYear, endMonth-1, endDay);

    boolean left=false;
    boolean right=false;
    boolean leftRightTogether=false;


    for (int c = 0; c < encounters.size(); c++) {
      Encounter temp = (Encounter) encounters.get(c);

      if (temp.getLocationCode().startsWith(locCode)) {

        if((temp.getDateInMilliseconds()!=null)&&(temp.getDateInMilliseconds()>=gcMin.getTimeInMillis())&&(temp.getDateInMilliseconds()<=gcMax.getTimeInMillis())){
          if(temp.getNumRightSpots()>0){right=true;}
          if(temp.getNumSpots()>0){left=true;}
          if((temp.getNumRightSpots()>0)&&(temp.getNumSpots()>0)){leftRightTogether=true;}
        }
      }
    }
    if(leftRightTogether){return "3";}
    else if(left&&right){return "4";}
    else if(left){return "1";}
    else if(right){return "2";}
    else{
      return "0";
    }
  }

    public String getGenusSpecies(){
        return getTaxonomyString();
    }

    //if not set on this, then drill down into encounters until we find one
    public String getGenusSpeciesDeep() {
        if (getTaxonomyString() != null) return getTaxonomyString();
        if (Util.collectionIsEmptyOrNull(encounters)) return null;
        for (Encounter enc : encounters) {
            String s = Util.taxonomyString(enc.getGenus(), enc.getSpecificEpithet());
            if (s != null) return s;  //return first one we hit
        }
        return null;
    }


/**
Returns the first haplotype found in the Encounter objects for this MarkedIndividual.
@return a String if found or null if no haplotype is found
*/
public String getHaplotype(){

    return localHaplotypeReflection;

}



public String getGeneticSex(){
  for (int c = 0; c < encounters.size(); c++) {
    Encounter temp = (Encounter) encounters.get(c);
    if(temp.getGeneticSex()!=null){return temp.getGeneticSex();}
  }
return null;

}

public boolean hasLocusAndAllele(String locus, Integer alleleValue){
  ArrayList<TissueSample> samples=getAllTissueSamples();
  int numSamples=samples.size();
  for(int i=0;i<numSamples;i++){
      TissueSample sample=samples.get(i);
      if(sample.getGeneticAnalyses()!=null){
        List<GeneticAnalysis> analyses=sample.getGeneticAnalyses();
        int numAnalyses=analyses.size();
        for(int e=0;e<numAnalyses;e++){
          GeneticAnalysis ga=analyses.get(e);
          if(ga.getAnalysisType().equals("MicrosatelliteMarkers")){
            MicrosatelliteMarkersAnalysis msa=(MicrosatelliteMarkersAnalysis)ga;
            if(msa.getLocus(locus)!=null){
               Locus l=msa.getLocus(locus);
               if(l.hasAllele(alleleValue)){return true;}
            }
          }
        }
      }
  }
  return false;
}

public ArrayList<Integer> getAlleleValuesForLocus(String locus){
  ArrayList<Integer> matchingValues=new ArrayList<Integer>();
  ArrayList<TissueSample> samples=getAllTissueSamples();
  int numSamples=samples.size();
  for(int i=0;i<numSamples;i++){
      TissueSample sample=samples.get(i);
      if(sample.getGeneticAnalyses()!=null){
        List<GeneticAnalysis> analyses=sample.getGeneticAnalyses();
        int numAnalyses=analyses.size();
        for(int e=0;e<numAnalyses;e++){
          GeneticAnalysis ga=analyses.get(e);
          if(ga.getAnalysisType().equals("MicrosatelliteMarkers")){
            MicrosatelliteMarkersAnalysis msa=(MicrosatelliteMarkersAnalysis)ga;
            if(msa.getLocus(locus)!=null){
               Locus l=msa.getLocus(locus);
               if((l.getAllele0()!=null)){matchingValues.add(l.getAllele0());}
               if((l.getAllele1()!=null)){matchingValues.add(l.getAllele1());}
               if((l.getAllele2()!=null)){matchingValues.add(l.getAllele2());}
               if((l.getAllele3()!=null)){matchingValues.add(l.getAllele3());}
            }
          }
        }
      }
  }
  return matchingValues;
}

public boolean hasLocus(String locus){
  ArrayList<TissueSample> samples=getAllTissueSamples();
  int numSamples=samples.size();
  for(int i=0;i<numSamples;i++){
      TissueSample sample=samples.get(i);
      if(sample.getGeneticAnalyses()!=null){
        List<GeneticAnalysis> analyses=sample.getGeneticAnalyses();
        int numAnalyses=analyses.size();
        for(int e=0;e<numAnalyses;e++){
          GeneticAnalysis ga=analyses.get(e);
          if(ga.getAnalysisType().equals("MicrosatelliteMarkers")){
            MicrosatelliteMarkersAnalysis msa=(MicrosatelliteMarkersAnalysis)ga;
            if(msa.getLocus(locus)!=null){
               return true;
            }
          }
        }
      }
  }
  return false;
}





public boolean hasMsMarkers(){
  ArrayList<TissueSample> samples=getAllTissueSamples();
  if(samples!=null){
  int numSamples=samples.size();
  for(int i=0;i<numSamples;i++){
      TissueSample sample=samples.get(i);
      if(sample.getGeneticAnalyses()!=null){
        List<GeneticAnalysis> analyses=sample.getGeneticAnalyses();
        int numAnalyses=analyses.size();
        for(int e=0;e<numAnalyses;e++){
          GeneticAnalysis ga=analyses.get(e);
          if(ga.getAnalysisType().equals("MicrosatelliteMarkers")){
            return true;
          }
        }
      }
  }
  }
  return false;
}

public boolean hasGeneticSex(){
  ArrayList<TissueSample> samples=getAllTissueSamples();
  int numSamples=samples.size();
  for(int i=0;i<numSamples;i++){
      TissueSample sample=samples.get(i);
      if(sample.getGeneticAnalyses()!=null){
        List<GeneticAnalysis> analyses=sample.getGeneticAnalyses();
        int numAnalyses=analyses.size();
        for(int e=0;e<numAnalyses;e++){
          GeneticAnalysis ga=analyses.get(e);
          if(ga.getAnalysisType().equals("SexAnalysis")){
            return true;
          }
        }
      }
  }
  return false;
}


/**
*Obtains the email addresses of all submitters, photographs, and others to notify.
*@return ArrayList of all emails to inform
*/
public List<String> getAllEmailsToUpdate(){
	ArrayList<String> notifyUs=new ArrayList<String>();

	int numEncounters=encounters.size();
	//int numUnidetifiableEncounters=unidentifiableEncounters.size();

	//process encounters
	for(int i=0;i<numEncounters;i++){
		Encounter enc=(Encounter)encounters.get(i);


		/*
		if((enc.getSubmitterEmail()!=null)&&(!enc.getSubmitterEmail().trim().equals(""))){
			String submitter = enc.getSubmitterEmail();
			if (submitter.indexOf(",") != -1) {
			   StringTokenizer str = new StringTokenizer(submitter, ",");
			   while (str.hasMoreTokens()) {
        	         String token = str.nextToken().trim();
					 if((!token.equals(""))&&(!notifyUs.contains(token))){notifyUs.add(token);}
	   			}
			}
			else{if(!notifyUs.contains(submitter)){notifyUs.add(submitter);}}
		}
		if((enc.getPhotographerEmail()!=null)&&(!enc.getPhotographerEmail().trim().equals(""))){
					String photog = enc.getPhotographerEmail();
					if (photog.indexOf(",") != -1) {
					   StringTokenizer str = new StringTokenizer(photog, ",");
					   while (str.hasMoreTokens()) {
		        	         String token = str.nextToken().trim();
							 if((!token.equals(""))&&(!notifyUs.contains(token))){notifyUs.add(token);}
			   			}
					}
					else{if(!notifyUs.contains(photog)){notifyUs.add(photog);}}
		}
		*/

		List<User> allUsers=new ArrayList<User>();
		if(enc.getSubmitters()!=null)allUsers.addAll(enc.getSubmitters());
		if(enc.getPhotographers()!=null)allUsers.addAll(enc.getPhotographers());
		if(enc.getInformOthers()!=null)allUsers.addAll(enc.getInformOthers());
		int numUsers=allUsers.size();
		for(int k=0;k<numUsers;k++){
		  User use=allUsers.get(k);
		  if((use.getEmailAddress()!=null)&&(!use.getEmailAddress().trim().equals(""))){
		    notifyUs.add(use.getEmailAddress());
		  }
		}

		/*
		if((enc.getInformOthers()!=null)&&(!enc.getInformOthers().trim().equals(""))){
							String photog = enc.getInformOthers();
							if (photog.indexOf(",") != -1) {
							   StringTokenizer str = new StringTokenizer(photog, ",");
							   while (str.hasMoreTokens()) {
				        	         String token = str.nextToken().trim();
									 if((!token.equals(""))&&(!notifyUs.contains(token))){notifyUs.add(token);}
					   			}
							}
							else{if(!notifyUs.contains(photog)){notifyUs.add(photog);}}
		}
		*/

	}

	/*
		//process log encounters
		for(int i=0;i<numUnidentifiableEncounters;i++){
			Encounter enc=(Encounter)unidentifiableEncounters.get(i);
			if((enc.getSubmitterEmail()!=null)&&(!enc.getSubmitterEmail().trim().equals(""))){
				String submitter = enc.getSubmitterEmail();
				if (submitter.indexOf(",") != -1) {
				   StringTokenizer str = new StringTokenizer(submitter, ",");
				   while (str.hasMoreTokens()) {
	        	         String token = str.nextToken().trim();
						 if((!token.equals(""))&&(!notifyUs.contains(token))){notifyUs.add(token);}
		   			}
				}
				else{if(!notifyUs.contains(submitter)){notifyUs.add(submitter);}}
			}
			if((enc.getPhotographerEmail()!=null)&&(!enc.getPhotographerEmail().trim().equals(""))){
						String photog = enc.getPhotographerEmail();
						if (photog.indexOf(",") != -1) {
						   StringTokenizer str = new StringTokenizer(photog, ",");
						   while (str.hasMoreTokens()) {
			        	         String token = str.nextToken().trim();
								 if((!token.equals(""))&&(!notifyUs.contains(token))){notifyUs.add(token);}
				   			}
						}
						else{if(!notifyUs.contains(photog)){notifyUs.add(photog);}}
			}
			if((enc.getInformOthers()!=null)&&(!enc.getInformOthers().trim().equals(""))){
								String photog = enc.getInformOthers();
								if (photog.indexOf(",") != -1) {
								   StringTokenizer str = new StringTokenizer(photog, ",");
								   while (str.hasMoreTokens()) {
					        	         String token = str.nextToken().trim();
										 if((!token.equals(""))&&(!notifyUs.contains(token))){notifyUs.add(token);}
						   			}
								}
								else{if(!notifyUs.contains(photog)){notifyUs.add(photog);}}
			}

	}
		*/

	return notifyUs;

}

//public void removeLogEncounter(Encounter enc){if(unidentifiableEncounters.contains(enc)){unidentifiableEncounters.remove(enc);}}

public float distFrom(float lat1, float lng1, float lat2, float lng2) {
  double earthRadius = 3958.75;
  double dLat = Math.toRadians(lat2-lat1);
  double dLng = Math.toRadians(lng2-lng1);
  double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
             Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
             Math.sin(dLng/2) * Math.sin(dLng/2);
  double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
  double dist = earthRadius * c;

  int meterConversion = 1609;

  return new Float(dist * meterConversion).floatValue();
}

public Float getMaxDistanceBetweenTwoSightings(){
  int numEncs=encounters.size();
  Float maxDistance=new Float(0);
  if(numEncs>1){
  for(int y=0;y<numEncs;y++){
    Encounter thisEnc=(Encounter)encounters.get(y);
    if((thisEnc.getLatitudeAsDouble()!=null)&&(thisEnc.getLongitudeAsDouble()!=null)){
    for(int z=(y+1);z<numEncs;z++){
      Encounter nextEnc=(Encounter)encounters.get(z);
      if((nextEnc.getLatitudeAsDouble()!=null)&&(nextEnc.getLongitudeAsDouble()!=null)){
        try{
          Float tempMaxDistance=distFrom(new Float(thisEnc.getLatitudeAsDouble()), new Float(thisEnc.getLongitudeAsDouble()), new Float(nextEnc.getLatitudeAsDouble()), new Float(nextEnc.getLongitudeAsDouble()));
          if(tempMaxDistance>maxDistance){maxDistance=tempMaxDistance;}
        }
        catch(Exception e){e.printStackTrace();System.out.println("Hit an NPE when calculating distance between: "+thisEnc.getCatalogNumber()+" and "+nextEnc.getCatalogNumber());}
      }
    }
  }
  }
  }
  return maxDistance;
}

public long getMaxTimeBetweenTwoSightings(){
  int numEncs=encounters.size();
  long maxTime=0;
  if(numEncs>1){
  for(int y=0;y<numEncs;y++){
    Encounter thisEnc=(Encounter)encounters.get(y);
    for(int z=(y+1);z<numEncs;z++){
      Encounter nextEnc=(Encounter)encounters.get(z);
      if((thisEnc.getDateInMilliseconds()!=null)&&(nextEnc.getDateInMilliseconds()!=null)){
        long tempMaxTime=Math.abs(thisEnc.getDateInMilliseconds().longValue()-nextEnc.getDateInMilliseconds().longValue());
        if(tempMaxTime>maxTime){maxTime=tempMaxTime;}
      }
    }
  }
  }
  return maxTime;
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

/**
 * DO NOT SET DIRECTLY!!
 *
 * @param myDepth
 */
public void doNotSetLocalHaplotypeReflection(String myHaplo) {
  if(myHaplo!=null){localHaplotypeReflection = myHaplo;}
  else{localHaplotypeReflection = null;}
}

public long getTimeOfBirth(){return timeOfBirth;}
public long getTimeofDeath(){return timeOfDeath;}

public void setTimeOfBirth(long newTime){timeOfBirth=newTime;}
public void setTimeOfDeath(long newTime){timeOfDeath=newTime;}

public List<Relationship> getAllRelationships(Shepherd myShepherd){
  return myShepherd.getAllRelationshipsForMarkedIndividual(individualID);
}

public String getFomattedMSMarkersString(String[] loci){
  StringBuffer sb=new StringBuffer();
  int numLoci=loci.length;
  for(int i=0;i<numLoci;i++){
    ArrayList<Integer> alleles=getAlleleValuesForLocus(loci[i]);
    if((alleles.size()>0)&&(alleles.get(0)!=null)){sb.append(alleles.get(0)+" ");}
    else{sb.append("--- ");}
    if((alleles.size()>=2)&&(alleles.get(1)!=null)){sb.append(alleles.get(1)+" ");}
    else{sb.append("--- ");}
  }
  return sb.toString();
}

public Float getMinDistanceBetweenTwoMarkedIndividuals(MarkedIndividual otherIndy){

  DecimalFormat df = new DecimalFormat("#.#");
  Float minDistance=new Float(1000000);
  if((encounters!=null)&&(encounters.size()>0)&&(otherIndy.getEncounters()!=null)&&(otherIndy.getEncounters().size()>0)){
  int numEncs=encounters.size();
  int numOtherEncs=otherIndy.getEncounters().size();

  if(numEncs>0){
  for(int y=0;y<numEncs;y++){
    Encounter thisEnc=(Encounter)encounters.get(y);
    if((thisEnc.getLatitudeAsDouble()!=null)&&(thisEnc.getLongitudeAsDouble()!=null)){
    for(int z=0;z<numOtherEncs;z++){
      Encounter nextEnc=otherIndy.getEncounter(z);
      if((nextEnc.getLatitudeAsDouble()!=null)&&(nextEnc.getLongitudeAsDouble()!=null)){
        try{
          Float tempMinDistance=distFrom(new Float(thisEnc.getLatitudeAsDouble()), new Float(thisEnc.getLongitudeAsDouble()), new Float(nextEnc.getLatitudeAsDouble()), new Float(nextEnc.getLongitudeAsDouble()));
          if(tempMinDistance<minDistance){minDistance=tempMinDistance;}
        }
        catch(Exception e){e.printStackTrace();System.out.println("Hit an NPE when calculating distance between: "+thisEnc.getCatalogNumber()+" and "+nextEnc.getCatalogNumber());}
      }
    }
  }
  }
  }
  }
  if(minDistance>999999)minDistance=new Float(-1);
  return minDistance;
}


	//convenience function to Collaboration permissions
	public boolean canUserAccess(HttpServletRequest request) {
		return Collaboration.canUserAccessMarkedIndividual(this, request);
	}


	public JSONObject sanitizeJson(HttpServletRequest request, JSONObject jobj) throws JSONException {
	          jobj.put("displayName", this.getDisplayName(request));
	          if (this.canUserAccess(request)) return jobj;
            jobj.remove("numberLocations");
            jobj.remove("sex");
            jobj.remove("numberEncounters");
            jobj.remove("timeOfDeath");
            jobj.remove("timeOfBirth");
            jobj.remove("maxYearsBetweenResightings");
            //jobj.remove("numUnidentifiableEncounters");
            jobj.remove("nickName");
            jobj.remove("nickNamer");
            jobj.put("_sanitized", true);
            return jobj;
        }



  public JSONObject decorateJson(HttpServletRequest request, JSONObject jobj) throws JSONException {
    jobj.remove("nickName");
    jobj.put("nickName", this.getNickName());
    //System.out.println("Put displayName in sanitizeJSON: "+jobj.get("displayName"));
    return jobj;
  }


//Returns a somewhat rest-like JSON object containing the metadata
 public JSONObject uiJson(HttpServletRequest request) throws JSONException {
   return uiJson(request, true);
 }
  // Returns a somewhat rest-like JSON object containing the metadata
  public JSONObject uiJson(HttpServletRequest request, boolean includeEncounters) throws JSONException {
    JSONObject jobj = new JSONObject();
    jobj.put("individualID", this.getIndividualID());
    jobj.put("displayName", this.getDisplayName(request));
    jobj.put("id", this.getId());
    jobj.put("url", this.getUrl(request));
    jobj.put("sex", this.getSex());
    jobj.put("nickname", this.getNickName());
    jobj.put("numberEncounters", this.getNumEncounters());
    jobj.put("numberLocations", this.getNumberLocations());
    jobj.put("maxYearsBetweenResightings", getMaxNumYearsBetweenSightings());
    // note this does not re-compute thumbnail url (so we can get thumbnails on searchResults in a reasonable time)
    jobj.put("thumbnailUrl", this.thumbnailUrl);
    jobj.put("livingStatus", this.isDeceased() ? "dead" : "alive");

    Vector<String> encIDs = new Vector<String>();
    Encounter firstEncounter = null;
    Encounter lastEncounter = null;
    for (Encounter enc : this.getDateSortedEncounters()) {
        encIDs.add(enc.getCatalogNumber());
        if (firstEncounter == null) firstEncounter = enc;
        lastEncounter = enc;
    }
    if (includeEncounters) jobj.put("encounterIDs", encIDs.toArray());
    if (firstEncounter != null) jobj.put("firstSightingDate", firstEncounter.getDate());
    if (lastEncounter != null) jobj.put("mostRecentSightingDate", lastEncounter.getDate());

    return sanitizeJson(request,decorateJson(request, jobj));
  }

  public String getUrl(HttpServletRequest request) {
    return request.getScheme()+"://" + CommonConfiguration.getURLLocation(request)+"/individuals.jsp?number="+this.getIndividualID();
  }


  /**
  * returns an array of the MediaAsset sanitized JSON, because whenever UI queries our DB (regardless of class query),
  * all they want in return are MediaAssets
  * TODO: decorate with metadata
  **/
  public org.datanucleus.api.rest.orgjson.JSONArray sanitizeMedia(HttpServletRequest request) throws org.datanucleus.api.rest.orgjson.JSONException {
    org.datanucleus.api.rest.orgjson.JSONArray resultArray = new org.datanucleus.api.rest.orgjson.JSONArray();
    for(int i=0;i<encounters.size();i++) {
      Encounter tempEnc=(Encounter)encounters.get(i);
      Util.concatJsonArrayInPlace(resultArray, tempEnc.sanitizeMedia(request));
    }
    return resultArray;
  }


  public ArrayList<org.datanucleus.api.rest.orgjson.JSONObject> getExemplarImages(Shepherd myShepherd, HttpServletRequest req) throws JSONException {
    return getExemplarImages(myShepherd, req, 5);
  }

  public ArrayList<org.datanucleus.api.rest.orgjson.JSONObject> getExemplarImages(Shepherd myShepherd,HttpServletRequest req, int numResults) throws JSONException {
    return getExemplarImages(myShepherd, req, numResults, "_mid");
  }

  public ArrayList<org.datanucleus.api.rest.orgjson.JSONObject> getExemplarImages(Shepherd myShepherd,HttpServletRequest req, int numResults, String imageSize) throws JSONException {
    ArrayList<org.datanucleus.api.rest.orgjson.JSONObject> al=new ArrayList<org.datanucleus.api.rest.orgjson.JSONObject>();

    List<MediaAsset> assets=new ArrayList<MediaAsset>();
    
    String[] kwReadableNames = {"ProfilePhoto"};
    List<MediaAsset> profilePhotos =  myShepherd.getKeywordPhotosForIndividual(this, kwReadableNames, 5);
    if(profilePhotos==null || profilePhotos.size()<numResults) {
      //add what we have
      if(profilePhotos!=null)assets.addAll(profilePhotos);
      //but we need a few more examples
      String[] noKeywordNames = new String[0];
      List<MediaAsset> otherPhotos = myShepherd.getKeywordPhotosForIndividual(this, noKeywordNames, (numResults-assets.size()));
      if(otherPhotos!=null)assets.addAll(otherPhotos);
    }
    else{assets=profilePhotos;}
    
    for(MediaAsset ma:assets) {
        if (ma != null) {
          
          JSONObject j = ma.sanitizeJson(req, new JSONObject());

          //we get a url which is potentially more detailed than we might normally be allowed (e.g. anonymous user)
          // we have a throw-away shepherd here which is fine since we only care about the url ultimately
          URL midURL = null;
          ArrayList<MediaAsset> kids = ma.findChildrenByLabel(myShepherd, imageSize);
          if ((kids != null) && (kids.size() > 0)) midURL = kids.get(0).webURL();
          if (midURL != null) j.put("url", midURL.toString()); //this overwrites url that was set in ma.sanitizeJson()

          if ((j!=null)&&(ma.getMimeTypeMajor()!=null)&&(ma.getMimeTypeMajor().equals("image"))) {
            //put ProfilePhotos at the beginning
            if(ma.hasKeyword("ProfilePhoto")){al.add(0, j);}
            //do nothing and don't include it if it has NoProfilePhoto keyword
            //due to this dropout, you may get less than request
            //but there may be less than requested anyway for any individual
            else if(ma.hasKeyword("NoProfilePhoto")){}
            //otherwise, just add it to the bottom of the stack
            else{
              al.add(j);
            }
          }
        }
        if(al.size()>=numResults){return al;}
    }
    return al;
  }

  public ArrayList<org.datanucleus.api.rest.orgjson.JSONObject> getExemplarImagesWithKeywords(HttpServletRequest req, List<String> kwNames) throws JSONException {
    ArrayList<org.datanucleus.api.rest.orgjson.JSONObject> al=new ArrayList<org.datanucleus.api.rest.orgjson.JSONObject>();

    List<String> kwNamesLeft = new ArrayList<String>(kwNames); // a copy of kwNames
    //boolean haveProfilePhoto=false;
    for (Encounter enc : this.getDateSortedEncounters()) {
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

            //we get a url which is potentially more detailed than we might normally be allowed (e.g. anonymous user)
            // we have a throw-away shepherd here which is fine since we only care about the url ultimately
            URL midURL = null;
            String context = ServletUtilities.getContext(req);
            Shepherd myShepherd = new Shepherd(context);
            myShepherd.setAction("MarkedIndividual.getExemplarImages");
            myShepherd.beginDBTransaction();
            ArrayList<MediaAsset> kids = ma.findChildrenByLabel(myShepherd, "_mid");
            if ((kids != null) && (kids.size() > 0)) midURL = kids.get(0).webURL();
            if (midURL != null) j.put("url", midURL.toString()); //this overwrites url that was set in ma.sanitizeJson()
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();

            if ((j!=null)&&(ma.getMimeTypeMajor()!=null)&&(ma.getMimeTypeMajor().equals("image"))) {

              //here is the keyword filtering logic
              String removeThisName=null; // awkward to avoid concurrent modification by removing kwName in the loop
              for (String kwName: kwNamesLeft) {
                if (ma.hasKeyword(kwName)) {
                  // position variable & logic ensures the keywords are returned in the same order
                  int position = kwNames.indexOf(kwName);
                  if (position>al.size()) position = al.size();
                  al.add(position, j); // we can ensure the first listed keyword is returned first
                  removeThisName=kwName;
                  break; // breaks the loop on this ma only
                }
              }
              if (removeThisName!=null) kwNamesLeft.remove(removeThisName);
            }
          }
        }
    //}
    }
    return al;

  }

  public ArrayList<org.datanucleus.api.rest.orgjson.JSONObject> getBestKeywordPhotos(HttpServletRequest req, List<String> kwNames, Shepherd myShepherd) throws JSONException {
    return getBestKeywordPhotos(req, kwNames, false, myShepherd);
  }
  public ArrayList<org.datanucleus.api.rest.orgjson.JSONObject> getBestKeywordPhotos(HttpServletRequest req, List<String> kwNames, boolean tryNoKeywords, Shepherd myShepherd) throws JSONException {
    ArrayList<org.datanucleus.api.rest.orgjson.JSONObject> al=new ArrayList<org.datanucleus.api.rest.orgjson.JSONObject>();

    List<MediaAsset> assets = new ArrayList<MediaAsset>();
    for (String kwName: kwNames) {
      MediaAsset ma = myShepherd.getBestKeywordPhoto(this, kwName);
      if (ma!=null) assets.add(ma);
    }

    if (tryNoKeywords) {
      int numMissingPhotos = 3-assets.size();
      List<MediaAsset> leftovers = myShepherd.getPhotosForIndividual(this, numMissingPhotos);
      for (MediaAsset assy: leftovers) {
        if (!assets.contains(assy)) assets.add(assy);
      }
    }

    for (MediaAsset ma: assets) {
      JSONObject j = ma.sanitizeJson(req, new JSONObject());
      ArrayList<MediaAsset> kids = ma.findChildrenByLabel(myShepherd, "_mid");
      URL midURL = null;
      if ((kids != null) && (kids.size() > 0)) midURL = kids.get(0).webURL();
      if (midURL != null) j.put("url", midURL.toString()); //this overwrites url that was set in ma.sanitizeJson()
      if ((j!=null)&&(ma.getMimeTypeMajor()!=null)&&(ma.getMimeTypeMajor().equals("image"))) {
        al.add(j);
      }
    }

    //myShepherd.rollbackDBTransaction();
    //myShepherd.closeDBTransaction();
    return al;
  }


  public org.datanucleus.api.rest.orgjson.JSONObject getExemplarImage(Shepherd myShepherd, HttpServletRequest req) throws JSONException {

    ArrayList<org.datanucleus.api.rest.orgjson.JSONObject> al=getExemplarImages(myShepherd, req, 0);
    if(al!=null && al.size()>0){return al.get(0);}
    return new JSONObject();


  }
  public org.datanucleus.api.rest.orgjson.JSONObject getExemplarThumbnail(Shepherd myShepherd, HttpServletRequest req) throws org.datanucleus.api.rest.orgjson.JSONException {

    ArrayList<org.datanucleus.api.rest.orgjson.JSONObject> al=getExemplarImages(myShepherd, req, 0, "_thumb");
    if(al!=null && al.size()>0){return al.get(0);}
    return new JSONObject();


  }





  // WARNING! THIS IS ONLY CORRECT IF ITS LOGIC CORRESPONDS TO getExemplarImage
  public String getExemplarPhotographer() {
    for (Encounter enc : this.getDateSortedEncounters()) {
      if((enc.getDynamicPropertyValue("PublicView")==null)||(enc.getDynamicPropertyValue("PublicView").equals("Yes"))){
        if((enc.getDynamicPropertyValue("ShowPhotographer")==null)||(enc.getDynamicPropertyValue("ShowPhotographer").equals("Yes"))){return enc.getPhotographerName();}
        else{return "";}

      }
    }
    return "";
  }


	//this simple version makes some assumptions: you already have list of collabs, and it is not visible
	public String collaborationLockHtml(HttpServletRequest request) {
		String context = "context0";
		context = ServletUtilities.getContext(request);
		Shepherd myShepherd = new Shepherd(context);
		myShepherd.setAction("MarkedIndividual.class");
		myShepherd.beginDBTransaction();

		List<Collaboration> collabs = Collaboration.collaborationsForCurrentUser(request);
  	ArrayList<String> uids = this.getAllAssignedUsers();
  	ArrayList<String> open = new ArrayList<String>();
		String collabClass = "pending";
		String data = "";

		for (String u : uids) {
			Collaboration c = Collaboration.findCollaborationWithUser(u, collabs);
			if ((c == null) || (c.getState() == null)) {
				User user = myShepherd.getUser(u);
				String fullName = u;
				if (user.getFullName()!=null) fullName = user.getFullName();
				open.add(u);
				data += "," + u + ":" + fullName.replace(",", " ").replace(":", " ").replace("\"", " ");
			}
		}
		if (open.size() > 0) {
			collabClass = "new";
			data = data.substring(1);
		}
		myShepherd.rollbackDBTransaction();
		myShepherd.closeDBTransaction();
		return "<div class=\"row-lock " + collabClass + " collaboration-button\" data-multiuser=\"" + data + "\">&nbsp;</div>";
	}



	public void refreshDependentProperties() {
		this.refreshNumberEncounters();
		this.refreshNumberLocations();
		this.resetMaxNumYearsBetweenSightings();
		this.refreshDateFirstIdentified();
		this.refreshDateLastestSighting();
	}

    // to find an *exact match* on a name, you can use:   regex = "(^|.*;)NAME(;.*|$)";
    // NOTE: this is case-insentitive, and as such it squashes the regex as well, sorry!
    public static List<MarkedIndividual> findByNames(Shepherd myShepherd, String regex, String genus, String specificEpithet) {

        int idLimit = 2000;  //this is cuz we get a stack overflow if we have too many.  :(  so kinda have to fail when we have too many
        System.out.println("findByNames regex: "+regex);
        List<MarkedIndividual> rtn = new ArrayList<MarkedIndividual>();
        if (NAMES_CACHE == null) return rtn;  //snh
        if (regex == null) return rtn;
        List<String> nameIds = findNameIds(regex);
        if (nameIds.size() > idLimit) {
            System.out.println("WARNING: MarkedIndividual.findByNames() found too many names; failing (" + nameIds.size() + " > " + idLimit + ")");
            return rtn;
        }
        //System.out.println("findByNames nameIds: "+nameIds.toString());
        if (nameIds.size() < 1) return rtn;
        //System.out.println("findByNames: "+genus+" "+specificEpithet);
        String taxonomyStringFilter="";
        if((genus!=null)&&(specificEpithet!=null)) {
          genus = genus.trim();
          specificEpithet = specificEpithet.trim();
          taxonomyStringFilter=" && enc.genus == '"+genus+"' && enc.specificEpithet == '"+specificEpithet+"' VARIABLES org.ecocean.Encounter enc";
        }
        String jdoql = "SELECT FROM org.ecocean.MarkedIndividual WHERE encounters.contains(enc) && (names.id == " + String.join(" || names.id == ", nameIds)+")"+taxonomyStringFilter;
        System.out.println("findByNames jdoql: "+jdoql);
        Query query = myShepherd.getPM().newQuery(jdoql);
        Collection c = (Collection) (query.execute());
        for (Object m : c) {
            MarkedIndividual ind = (MarkedIndividual)m;
            rtn.add(ind);
        }
        query.closeAll();
        return rtn;
    }

    public static List<MarkedIndividual> findByNames(Shepherd myShepherd, String regex) {
      return findByNames(myShepherd, regex, null, null);
    }

    public static MarkedIndividual withName(Shepherd myShepherd, String name) {
      return withName(myShepherd, name, null, null);
    }


    // exact case-insensitive version of above func that returns 1 or 0 individuals
    public static MarkedIndividual withName(Shepherd myShepherd, String name, String genus, String specificEpithet) {
      String regex = "(^|.*;)"+name+"(;.*|$)";
      System.out.println("withName: "+genus+" "+specificEpithet);
      List<MarkedIndividual> inds = findByNames(myShepherd, regex, genus, specificEpithet);
      if (inds==null || inds.size()==0) return null;
      if (inds.size()>1) System.out.println("WARNING! MarkedIndividual.withName called for name "+name+". THERE ARE "+inds.size()+" INDIVIDUALS WITH THIS NAME AND WE'RE RETURNING ONLY ONE.");
      return inds.get(0);
    }

    //used above, but also used in IndividualQueryProcessor, for example
    public static List<String> findNameIds(String regex) {
        List<String> nameIds = new ArrayList<String>();
        if (NAMES_CACHE == null) return nameIds;
        if (regex == null) return nameIds;
        for (Integer nid : NAMES_CACHE.keySet()) {
            if (NAMES_CACHE.get(nid).matches(regex.toLowerCase())) nameIds.add(Integer.toString(nid));
        }
        return nameIds;
    }

    //returns next integer-based value that follows pattern PREnnn (where 'nnn' is one-or-more digits!)
    // cache-key is lowercase, but we return respecting case of original prefix
    // in a perfect world, this would use a sequence in db to prevent race-conditions.   :/
    public static String nextNameByPrefix(String prefix) {
        return nextNameByPrefix(prefix, 0);  //0 means guess at length of zeroes
    }
    public static String nextNameByPrefix(String prefix, int zeroPadding) {
        if (NAMES_CACHE == null) return null;  //snh
        if (NAMES_CACHE.size() < 1) return null;  //on the off chance has not been init'ed  (snh?)
        if (prefix == null) return null;
        Pattern pat = Pattern.compile("(^|.*;)" + prefix.toLowerCase() + "(\\d+)(;.*|$)");
        BigInteger val = new BigInteger("0");  //will have +1 at end; see comment elsewhere about 0 vs 1 and heathens
        for (String c : NAMES_CACHE.values()) {
            Matcher mat = pat.matcher(c);
            if (!mat.find()) continue;
            if (zeroPadding < 1) zeroPadding = mat.group(2).length();
            BigInteger num = new BigInteger(mat.group(2));
            if(num.compareTo(val)>0) val = num;
        }
        if (zeroPadding < 1) zeroPadding = 4;  //if we had no guess (e.g. new?) lets be optimistic!
        return String.format("%s%0" + zeroPadding + "d", prefix, val.add(new BigInteger("1")));
      }

    public static List<String> findNames(String regex) {
        List<String> names = new ArrayList<String>();
        if (NAMES_CACHE == null) return names;  //snh
        if (regex == null) return names;
        for (Integer nid : NAMES_CACHE.keySet()) {
            if (NAMES_CACHE.get(nid).matches(regex.toLowerCase())) names.add(NAMES_CACHE.get(nid));
        }
        return names;
    }

    //only does once (when needed)
    public static boolean initNamesCache(final Shepherd myShepherd) {
        if ((NAMES_CACHE != null) && (NAMES_CACHE.size() > 0) && (NAMES_KEY_CACHE != null) && (NAMES_KEY_CACHE.size() > 0)) return false;
        updateNamesCache(myShepherd);
        return true;
    }
    public static Map<Integer,String> updateNamesCache(final Shepherd myShepherd) {
        NAMES_CACHE = new HashMap<Integer,String>();
        NAMES_KEY_CACHE = new HashMap<Integer,String>();
        Query query = myShepherd.getPM().newQuery("SELECT FROM org.ecocean.MarkedIndividual");
        Collection c = (Collection) (query.execute());
        int numIndividuals = c.size();
        int currentIndy=0;
        for (Object m : c) {
            currentIndy++;
            myShepherd.setAction("MarkedIndividual.initNamesCache_"+currentIndy+"_"+numIndividuals);
            MarkedIndividual ind = (MarkedIndividual) m;
            if (ind.names == null) continue;
            NAMES_CACHE.put(ind.names.getId(), ind.getId() + ";" + String.join(";", ind.names.getAllValues()).toLowerCase());
            NAMES_KEY_CACHE.put(ind.names.getId(), ind.getId() + ";" + String.join(";", ind.getNameKeys()).toLowerCase());
        }
        query.closeAll();
        return NAMES_CACHE;
    }

    //updates cache based upon this instance (assumes names has changed)
    public void refreshNamesCache() {
        if (names == null) return;
        if (NAMES_CACHE == null) return;  //snh
        NAMES_CACHE.put(names.getId(), this.getId() + ";" + String.join(";", names.getAllValues()).toLowerCase());
    }


    //multiple ways to remove from cache
    public static void removeFromNamesCache(MarkedIndividual indiv) {
        if ((indiv == null) || (indiv.names == null) || (NAMES_CACHE == null)) return;
        removeFromNamesCache(indiv.names.getId());
    }
    public static void removeFromNamesCache(int namesId) {
        if (NAMES_CACHE == null) return;
        NAMES_CACHE.remove(namesId);
    }
    public void removeFromNamesCache() {
        if (this.names == null) return;
        removeFromNamesCache(this.names.getId());
    }

    // Need request to record which user did it
    public void mergeIndividual(MarkedIndividual other, String username, Shepherd myShepherd) {
      for (Encounter enc: other.getEncounters()) {
        other.removeEncounter(enc);
        enc.setIndividual(this);
      }
      this.names.merge(other.getNames());
      this.setComments(getMergedComments(other, username));

      //WB-951: merge relationships
      ArrayList<Relationship> rels=myShepherd.getAllRelationshipsForMarkedIndividual(other.getIndividualID());
      if(rels!=null && rels.size()>0) {
        for(Relationship rel:rels) {
          if(rel.getMarkedIndividualName1().equals(other.getIndividualID())) {
            rel.setIndividual1(this);
            rel.setMarkedIndividualName1(this.getIndividualID());
            myShepherd.updateDBTransaction();
          }
          else if(rel.getMarkedIndividualName2().equals(other.getIndividualID())) {
            rel.setIndividual2(this);
            rel.setMarkedIndividualName2(this.getIndividualID());
            myShepherd.updateDBTransaction();
          }
        }
      }

      //WB-951: merge social units
      List<SocialUnit> units=myShepherd.getAllSocialUnitsForMarkedIndividual(other);
      if(units!=null && units.size()>0) {
        for(SocialUnit unit:units) {
          Membership member=unit.getMembershipForMarkedIndividual(other);
          member.removeMarkedIndividual();
          member.setMarkedIndividual(this);
          myShepherd.updateDBTransaction();
        }
      }

      //check for a ScheduledIndividualMerge that may have other
      String filter="select from org.ecocean.scheduled.ScheduledIndividualMerge where primaryIndividual.individualID =='"+other.getIndividualID()+"' || secondaryIndividual.individualID == '"+other.getIndividualID()+"'";
      Query q=myShepherd.getPM().newQuery(filter);
      Collection c=(Collection)q.execute();
      ArrayList<ScheduledIndividualMerge> merges=new ArrayList<ScheduledIndividualMerge>(c);
      //throw out any scheduled merge related to this individual as it is now being merged.
      for(ScheduledIndividualMerge merge:merges) {
        myShepherd.getPM().deletePersistent(merge);
        myShepherd.updateDBTransaction();
      }


      refreshDependentProperties();
    }

    public String getMergedComments(MarkedIndividual other, HttpServletRequest request, Shepherd myShepherd) {
      User user = myShepherd.getUser(request);
      String username = user.getDisplayName();
      return getMergedComments(other, username);
    }

    public String getMergedComments(MarkedIndividual other, String username) {
      String mergedComments = Util.stringExists(getComments()) ? getComments() : "";

      mergedComments += "<p>This individual merged with individual "+other.getIndividualID()+" (\""+other.getDisplayName()+"\")";
      mergedComments += ", which had encounters: [<ul>";
      for (Encounter enc: other.getEncounters()) {
        mergedComments += "<li>"+enc.getCatalogNumber()+"</li>";
      }
      mergedComments += "</ul>]";

      mergedComments += "Merged on "+Util.prettyTimeStamp();

      if (username!=null) mergedComments += " by "+ username;
      else mergedComments += " No user was logged in.";

      if (Util.stringExists(other.getComments())) {
        mergedComments += "</p><p>Merged comments:";
        mergedComments += other.getComments();
      }

      mergedComments += "</p>";
      return mergedComments;
    }

    public void mergeAndThrowawayIndividual(MarkedIndividual other, String username, Shepherd myShepherd) {
      mergeIndividual(other, username, myShepherd);
      myShepherd.throwAwayMarkedIndividual(other);
    }

    public String toString() {
        return new ToStringBuilder(this)
                .append("individualID", individualID)
                .append("species", getGenusSpecies())
                .append("names", getNames())
                .append("sex", getSex())
                .append("numEncounters", numberEncounters)
                .append("numLocations", numberLocations)
                .toString();
    }

}
