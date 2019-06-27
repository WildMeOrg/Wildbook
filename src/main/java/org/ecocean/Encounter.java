/*
 * The Shepherd Project - A Mark-Recapture Framework
 * Copyright (C) 2011 Jason Holmberg
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package org.ecocean;

import org.apache.commons.codec.digest.DigestUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Calendar;
import java.util.StringTokenizer;
import java.text.SimpleDateFormat;
import java.util.TreeMap;
import java.util.UUID;
import java.util.Vector;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.SortedMap;
import java.util.GregorianCalendar;
import java.lang.Math;
import java.io.*;
import java.lang.reflect.Field;

import javax.jdo.Query;

import org.apache.commons.lang3.builder.ToStringBuilder;

import javax.servlet.http.HttpServletRequest;

import org.ecocean.genetics.*;
import org.ecocean.tag.AcousticTag;
import org.ecocean.tag.DigitalArchiveTag;
import org.ecocean.tag.MetalTag;
import org.ecocean.tag.SatelliteTag;
import org.ecocean.Util;
//import org.ecocean.servlet.ServletUtilities;
import org.ecocean.identity.IBEISIA;
import org.ecocean.ia.IA;
import org.ecocean.media.*;
import org.ecocean.PointLocation;
import org.ecocean.Survey;

import javax.servlet.http.HttpServletRequest;




import org.joda.time.DateTime;
import org.joda.time.LocalDateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.ecocean.security.Collaboration;
import org.ecocean.servlet.ServletUtilities;

import javax.servlet.http.HttpServletRequest;


//note these are different.  so be explicit if you need the org.json.JSONObject flavor
//import org.json.JSONObject;
import org.datanucleus.api.rest.orgjson.JSONObject;
import org.datanucleus.api.rest.orgjson.JSONArray;
import org.datanucleus.api.rest.orgjson.JSONException;


/**
 * An <code>encounter</code> object stores the complete data for a single sighting/capture report.
 * <code>Encounters</code> are added to MarkedIndividual objects as multiple encounters are associated with
 * known individuals.
 * <p/>
 *
 * @author Jason Holmberg
 * @version 2.0
 */
public class Encounter implements java.io.Serializable {
  static final long serialVersionUID = -146404246317385604L;

    public static final String STATE_MATCHING_ONLY = "matching_only";
    //at least one frame/image (e.g. from YouTube detection) must have this confidence or encounter will be ignored
    public static final double ENCOUNTER_AUTO_SOURCE_CONFIDENCE_CUTOFF = 0.7;
    public static final String STATE_AUTO_SOURCED = "auto_sourced";

  /**
   * The following attributes are described in the Darwin Core quick reference at:
   * http://rs.tdwg.org/dwc/terms/#dcterms:type
   * <p/>
   * Wherever possible, this class will be extended with Darwin Core attributes for greater adoption of the standard.
   */
  private String sex = null;
  private String locationID = null;
  private Double maximumDepthInMeters;
  private Double maximumElevationInMeters;
  private String catalogNumber = "";
  //private String individualID;
    private MarkedIndividual individual;
  private int day = 0;
  private int month = -1;
  private int year = 0;
  private Double decimalLatitude;
  private Double decimalLongitude;
  
  private Double endDecimalLatitude;
  private Double endDecimalLongitude;
  
  private String verbatimLocality;
  private String occurrenceRemarks = "";
  private String modified;
  private String occurrenceID;
  private String recordedBy;
  private String otherCatalogNumbers;
  private String behavior;
  private String eventID;
  private String measurementUnit;
  private String verbatimEventDate;
  private String dynamicProperties;
  public String identificationRemarks = "";
  public String genus = "";
  public String specificEpithet;
  public String lifeStage;
  public String country;
  public String zebraClass ="";  //via lewa: lactating female, territorial male, etc etc

  // fields from Dan's sample csv
  private String imageSet;
  private String soil;

  private String reproductiveStage;
  private Double bodyCondition;
  private Double parasiteLoad;
  private Double immunoglobin;
  private Boolean sampleTakenForDiet;
  private Boolean injured;

  private ArrayList<Observation> observations = new ArrayList<Observation>();

  public String getSoil() {return soil;}
  public void setSoil(String soil) {this.soil = soil;}

  public String getReproductiveStage() {return reproductiveStage;}
  public void setReproductiveStage(String reproductiveStage) {this.reproductiveStage = reproductiveStage;}

  public Double getBodyCondition() {return bodyCondition;}
  public void setBodyCondition(Double bodyCondition) {this.bodyCondition = bodyCondition;}

  public Double getParasiteLoad() {return parasiteLoad;}
  public void setParasiteLoad(Double parasiteLoad) {this.parasiteLoad = parasiteLoad;}

  public Double getImmunoglobin() {return immunoglobin;}
  public void setImmunoglobin(Double immunoglobin) {this.immunoglobin = immunoglobin;}

  public Boolean getSampleTakenForDiet() {return sampleTakenForDiet;}
  public void setSampleTakenForDiet(Boolean sampleTakenForDiet) {this.sampleTakenForDiet = sampleTakenForDiet;}

  public Boolean getInjured() {return injured;}
  public void setInjured(Boolean injured) {this.injured = injured;}





  // for searchability
  private String imageNames;
  
  
  private List<User> submitters;
  private List<User> photographers;
  private List<User> informOthers;


    private static HashMap<String,ArrayList<Encounter>> _matchEncounterCache = new HashMap<String,ArrayList<Encounter>>();


  /*
    * The following fields are specific to this mark-recapture project and do not have an easy to map Darwin Core equivalent.
    */

  //An URL to a thumbnail image representing the encounter.
  //This is
  private String dwcImageURL;

  //Defines whether the sighting represents a living or deceased individual.
  //Currently supported values are: "alive" and "dead".
  private String livingStatus;

    //observed age (if any) via IBEIS zebra projects
    private Double age;

  //Date the encounter was added to the library.
  private String dwcDateAdded;
  private Long dwcDateAddedLong;

  // If Encounter spanned more than one day, date of release
  private Date releaseDate;

  private Long releaseDateLong;

  //Size of the individual in meters
  private Double size;

  //Additional comments added by library users
  private String researcherComments = "None";

  //username of the logged in researcher assigned to the encounter
  //this STring is matched to an org.ecocean.User object to obtain more information
  private String submitterID;

  //name, email, phone, address of the encounter reporter
  private String submitterEmail, submitterPhone, submitterAddress;
  private String hashedSubmitterEmail;
  private String hashedPhotographerEmail;
  private String hashedInformOthers;
  private String informothers;
  //name, email, phone, address of the encounter photographer
  private String photographerName, photographerEmail, photographerPhone, photographerAddress;
  //a Vector of Strings defining the relative path to each photo. The path is relative to the servlet base directory
  public Vector additionalImageNames = new Vector();
  //a Vector of Strings of email addresses to notify when this encounter is modified
  private Vector interestedResearchers = new Vector();
  //time metrics of the report
  private int hour = 0;
  private String minutes = "00";

  private String state="";

  //the globally unique identifier (GUID) for this Encounter
  private String guid;

  
  
  private Long endDateInMilliseconds;
  private Long dateInMilliseconds;
  //describes how the shark was measured
  private String size_guess = "none provided";
  //String reported GPS values for lat and long of the encounter
  private String gpsLongitude = "", gpsLatitude = "";
  private String gpsEndLongitude = "", gpsEndLatitude = "";
  //whether this encounter has been rejected and should be hidden from public display
  //unidentifiable encounters generally contain some data worth saving but not enough for accurate photo-identification
  //private boolean unidentifiable = false;
  //whether this encounter has a left-side spot image extracted
  //public boolean hasSpotImage = false;
  //whether this encounter has a right-side spot image extracted
  //public boolean hasRightSpotImage = false;
  //Indicates whether this record can be exposed via TapirLink
  private boolean okExposeViaTapirLink = false;
  //whether this encounter has been approved for public display
  //private boolean approved = true;
  //integers of the latitude and longitude degrees
  //private int lat=-1000, longitude=-1000;
  //name of the stored file from which the left-side spots were extracted
  public String spotImageFileName = "";
  //name of the stored file from which the right-side spots were extracted
  public String rightSpotImageFileName = "";
  //string descriptor of the most obvious scar (if any) as reported by the original submitter
  //we also use keywords to be more specific
  public String distinguishingScar = "None";
  //describes how this encounter was matched to an existing shark - by eye, by pattern recognition algorithm etc.

  //DEPRECATING OLD DATA CONSTRUCT
  //private int numSpotsLeft = 0;
  //private int numSpotsRight = 0;


  //SPOTS
  //an array of the extracted left-side superSpots
  //private superSpot[] spots;
  private ArrayList<SuperSpot> spots;

  //an array of the extracted right-side superSpots
  //private superSpot[] rightSpots;
  private ArrayList<SuperSpot> rightSpots;

  //an array of the three extracted left-side superSpots used for the affine transform of the I3S algorithm
  //private superSpot[] leftReferenceSpots;
  private ArrayList<SuperSpot> leftReferenceSpots;

  //an array of the three extracted right-side superSpots used for the affine transform of the I3S algorithm
  //private superSpot[] rightReferenceSpots;
  private ArrayList<SuperSpot> rightReferenceSpots;

  //an open ended string that allows a type of patterning to be identified.
  //as an example, see the use of color codes at splashcatalog.org, allowing pre-defined fluke patterning types
  //to be used to help narrow the search for a marked individual
  private String patterningCode;

  //submitting organization and project further detail the scope of who submitted this project
  private String submitterOrganization;
  private String submitterProject;
  private List<String> submitterResearchers;

  //hold submittedData
  //private List<DataCollectionEvent> collectedData;
  private List<TissueSample> tissueSamples;
  private List<SinglePhotoVideo> images;
  //private ArrayList<MediaAsset> media;
  private ArrayList<Annotation> annotations;
  private List<Measurement> measurements;
  private List<MetalTag> metalTags;
  private AcousticTag acousticTag;
  private SatelliteTag satelliteTag;
  private DigitalArchiveTag digitalArchiveTag;

  private Boolean mmaCompatible = false;
  
  // Variables used in the Survey, SurveyTrack, Path, Location model
  
  private String correspondingSurveyTrackID = null;
  private String correspondingSurveyID = null;
  
  
  // This is the eventual replacement for the old decimal lat lon and other location data.
  private PointLocation pointLocation;
  
  // This is the number used to cross reference with dates to find occurances. (Read Lab)
  private String sightNo = "";
  

  // This is what researchers eyeball is the individual's ID in the field
  // it could be a name that only has meaning in the context of that day's work
  // (not necessarily an individual name from the WB database)
  private String fieldID;

  // This is a standard 1-5 color scale used by cetacean researchers
  private Integer flukeType;

  // added by request for ASWN, this is the role an individual served in its occurrence
  // (from a standard list like Escort Male)
  private String groupRole;

  // identifies the import/dataset this came from for data provenance
  private String dataSource;

  //start constructors

  /**
   * empty constructor required by the JDO Enhancer
   */
  public Encounter() {
  }

  public Encounter(boolean skipSetup) {
    if (skipSetup) return;
    this.catalogNumber = Util.generateUUID();
    this.setDWCDateAdded();
    this.setDWCDateLastModified();
    this.resetDateInMilliseconds();
    this.annotations = new ArrayList<Annotation>();
  }

  /**
   * Use this constructor to add the minimum level of information for a new encounter
   * The Vector <code>additionalImages</code> must be a Vector of Blob objects
   *
   * NOTE: technically this is DEPRECATED cuz, SinglePhotoVideos? really?
   */
  public Encounter(int day, int month, int year, int hour, String minutes, String size_guess, String location) {
    if (images != null) System.out.println("WARNING: danger! deprecated SinglePhotoVideo-based Encounter constructor used!");
    this.verbatimLocality = location;
    //this.recordedBy = submitterName;
    //this.submitterEmail = submitterEmail;

    //now we need to set the hashed form of the email addresses
    //this.hashedSubmitterEmail = Encounter.getHashOfEmailString(submitterEmail);

    this.images = images;
    this.day = day;
    this.month = month;
    this.year = year;
    this.hour = hour;
    this.minutes = minutes;
    this.size_guess = size_guess;

    this.setDWCDateAdded();
    this.setDWCDateLastModified();
    this.resetDateInMilliseconds();
  }

    public Encounter(Annotation ann) {
      this(new ArrayList<Annotation>(Arrays.asList(ann)));
    }


    public Encounter(ArrayList<Annotation> anns) {
        this.catalogNumber = Util.generateUUID();
        this.annotations = anns;
        if (!this.annotationsAreEmpty()) {
          this.setDateFromAssets();
          this.setSpeciesFromAnnotations();
          this.setLatLonFromAssets();
        }
        this.setDWCDateAdded();
        this.setDWCDateLastModified();
        this.resetDateInMilliseconds();
    }
    private boolean annotationsAreEmpty() {
      return( this.annotations == null       ||
              this.annotations.size() == 0 || 
             (this.annotations.size() == 1 && (this.annotations.get(0)==null)) );
    }

    // space saver since we're about to use this hundreds of times
    private boolean shouldReplace(String str1, String str2) {return Util.shouldReplace(str1, str2);}
    // also returns true when str1 is a superstring of str2.
    private boolean shouldReplaceSuperStr(String str1, String str2) {
      return (shouldReplace(str1,str2)|| (Util.stringExists(str1) && str1.contains(str2)) );
    }

    public void mergeAndDelete(Encounter enc2, Shepherd myShepherd) {
      mergeDataFrom(enc2);
      MarkedIndividual ind = myShepherd.getMarkedIndividual(enc2);
      if (ind!=null) {
        ind.removeEncounter(enc2);
        ind.addEncounter(this); // duplicate-safe
      }
      Occurrence occ = myShepherd.getOccurrence(enc2);
      if (occ!=null) {
        occ.removeEncounter(enc2);        
        occ.addEncounter(this); // duplicate-safe
      }
      // remove tissue samples because of bogus foreign key constraint that prevents deletion
      int numTissueSamples = 0;
      if (enc2.getTissueSamples()!=null) numTissueSamples = enc2.getTissueSamples().size();
      for (int i=0; i<numTissueSamples; i++) {
        enc2.removeTissueSample(0);
      }
      myShepherd.throwAwayEncounter(enc2);
    }
    // copies otherEnc's data into thisEnc, not overwriting anything
    public void mergeDataFrom(Encounter enc2) {

      if (enc2.getIndividual()!=null) setIndividual(enc2.getIndividual());

      // simple string fields
      if (shouldReplace(enc2.getSex(), getSex())) setSex(enc2.getSex());
      if (shouldReplace(enc2.getLocationID(), getLocationID())) setLocationID(enc2.getLocationID());
      if (shouldReplace(enc2.getVerbatimLocality(), getVerbatimLocality())) setVerbatimLocality(enc2.getVerbatimLocality());
      if (shouldReplace(enc2.getOccurrenceID(), getOccurrenceID())) setOccurrenceID(enc2.getOccurrenceID());
      if (shouldReplace(enc2.getRecordedBy(), getRecordedBy())) setRecordedBy(enc2.getRecordedBy());
      if (shouldReplace(enc2.getEventID(), getEventID())) setEventID(enc2.getEventID());
      if (shouldReplace(enc2.getGenus(), getGenus())) setGenus(enc2.getGenus());
      if (shouldReplace(enc2.getSpecificEpithet(), getSpecificEpithet())) setSpecificEpithet(enc2.getSpecificEpithet());
      if (shouldReplace(enc2.getLifeStage(), getLifeStage())) setLifeStage(enc2.getLifeStage());
      if (shouldReplace(enc2.getCountry(), getCountry())) setCountry(enc2.getCountry());
      if (shouldReplace(enc2.getZebraClass(), getZebraClass())) setZebraClass(enc2.getZebraClass());
      if (shouldReplace(enc2.getSoil(), getSoil())) setSoil(enc2.getSoil());
      if (shouldReplace(enc2.getReproductiveStage(), getReproductiveStage())) setReproductiveStage(enc2.getReproductiveStage());
      if (shouldReplace(enc2.getLivingStatus(), getLivingStatus())) setLivingStatus(enc2.getLivingStatus());
      if (shouldReplace(enc2.getSubmitterEmail(), getSubmitterEmail())) setSubmitterEmail(enc2.getSubmitterEmail());
      if (shouldReplace(enc2.getSubmitterPhone(), getSubmitterPhone())) setSubmitterPhone(enc2.getSubmitterPhone());
      if (shouldReplace(enc2.getSubmitterAddress(), getSubmitterAddress())) setSubmitterAddress(enc2.getSubmitterAddress());
      if (shouldReplace(enc2.getState(), getState())) setState(enc2.getState());
      if (shouldReplace(enc2.getGPSLongitude(), getGPSLongitude())) setGPSLongitude(enc2.getGPSLongitude());
      if (shouldReplace(enc2.getGPSLatitude(), getGPSLatitude())) setGPSLatitude(enc2.getGPSLatitude());
      if (shouldReplace(enc2.getPatterningCode(), getPatterningCode())) setPatterningCode(enc2.getPatterningCode());
      if (shouldReplace(enc2.getSubmitterOrganization(), getSubmitterOrganization())) setSubmitterOrganization(enc2.getSubmitterOrganization());
      if (shouldReplace(enc2.getSubmitterProject(), getSubmitterProject())) setSubmitterProject(enc2.getSubmitterProject());
      if (shouldReplace(enc2.getFieldID(), getFieldID())) setFieldID(enc2.getFieldID());
      if (shouldReplace(enc2.getGroupRole(), getGroupRole())) setGroupRole(enc2.getGroupRole());

      // now string fields that might need to be combined rather than replaced
      if (shouldReplaceSuperStr(enc2.getDynamicProperties(), getDynamicProperties())) {
        setDynamicProperties(enc2.getDynamicProperties());
      } else if (Util.stringExists(enc2.getDynamicProperties())) { // shouldn't replace, should combine
        addDynamicProperties(enc2.getDynamicProperties());
      }
      if (shouldReplaceSuperStr(enc2.getOccurrenceRemarks(), getOccurrenceRemarks())) {
        setOccurrenceRemarks(enc2.getOccurrenceRemarks());
      } else if (Util.stringExists(enc2.getOccurrenceRemarks())) { // shouldn't replace, should combine
        setOccurrenceRemarks(getOccurrenceRemarks()+" "+enc2.getOccurrenceRemarks());
      }

      // now combine list fields making sure not to add duplicate entries
      setAnnotations(Util.combineArrayListsInPlace(getAnnotations(), enc2.getAnnotations()));
      setObservationArrayList(Util.combineArrayListsInPlace(getObservationArrayList(), enc2.getObservationArrayList()));
      setSubmitterResearchers(Util.combineListsInPlace(getSubmitterResearchers(), enc2.getSubmitterResearchers()));
      // custom no-duplicate logic bc the same sampleID may have been added on both encounters, but this would create unique tissuesample objects
      Set<String> sampleIDs = getTissueSampleIDs();
      for (TissueSample samp: enc2.getTissueSamples()) {
        if (!sampleIDs.contains(samp.getSampleID())) addTissueSample(samp);
      }
      setMeasurements(Util.combineListsInPlace(getMeasurements(), enc2.getMeasurements()));
      setMetalTags(Util.combineListsInPlace(getMetalTags(), enc2.getMetalTags()));

      // spot lists
      setSpots(Util.combineArrayListsInPlace(getSpots(), enc2.getSpots()));
      setRightSpots(Util.combineArrayListsInPlace(getRightSpots(), enc2.getRightSpots()));
      setLeftReferenceSpots(Util.combineArrayListsInPlace(getLeftReferenceSpots(), enc2.getLeftReferenceSpots()));
      setRightReferenceSpots(Util.combineArrayListsInPlace(getRightReferenceSpots(), enc2.getRightReferenceSpots()));

      // tags
      if (enc2.getAcousticTag() !=null && getAcousticTag() ==null) setAcousticTag(enc2.getAcousticTag());
      if (enc2.getSatelliteTag()!=null && getSatelliteTag()==null) setSatelliteTag(enc2.getSatelliteTag());
      if (enc2.getDTag()        !=null && getDTag()        ==null) setDTag(enc2.getDTag());

      // skip time stuff bc if the time is different we probably don't want to combine the encounters anyway.

    }


    public String getZebraClass() {
        return zebraClass;
    }
    public void setZebraClass(String c) {
        zebraClass = c;
    }

    public String getImageNames() {
        return imageNames;
    }
    public void addImageName(String name) {
      if  (imageNames==null) imageNames = name;
      else if (name != null) imageNames += (", "+name);
    }
    public String addAllImageNamesFromAnnots(boolean overwrite) {
      if (overwrite) imageNames = null;
      return addAllImageNamesFromAnnots();
    }
    public String addAllImageNamesFromAnnots() {
      for (Annotation ann : getAnnotations()) {
        for (Feature feat : ann.getFeatures()) {
          try {
            MediaAsset ma = feat.getMediaAsset();
            addImageName(ma.getFilename());
          }
          catch (Exception e) {
            System.out.println("exception parsing image name from feature "+feat);
          }
        }
      }
      return imageNames;
    }



  /**
   * Returns an array of all of the superSpots for this encounter.
   *
   * @return the array of superSpots, taken from the croppedImage, that make up the digital fingerprint for this encounter
   */
  public ArrayList<SuperSpot> getSpots() {
    //return HACKgetSpots();
    return spots;
  }

  public ArrayList<SuperSpot> getRightSpots() {
    //return HACKgetRightSpots();
    return rightSpots;
  }

  /**
   * Returns an array of all of the superSpots for this encounter.
   *
   * @return the array of superSpots, taken from the croppedImage, that make up the digital fingerprint for this encounter
   */
/*   these have gone away!  dont be setting spots on Encounter any more .... NOT SO FAST... we regress for whaleshark.org... */
  public void setSpots(ArrayList<SuperSpot> newSpots) {
    spots = newSpots;
  }

  public void setRightSpots(ArrayList<SuperSpot> newSpots) {
    rightSpots = newSpots;
  }


  /**
   * Removes any spot data
   */
  public void removeSpots() {
    spots = null;
  }

  public void removeRightSpots() {
    rightSpots = null;
  }

  public Integer getFlukeType() {return this.flukeType;}
  public void setFlukeType(Integer flukeType) {this.flukeType=flukeType;}
  // this averages all the fluketypes
  public void setFlukeTypeFromKeywords() {
    int totalFlukeType=0;
    int numFlukes=0;
    for (Annotation ann: getAnnotations()) {
      Integer thisFlukeType = getFlukeTypeFromAnnotation(ann);
      if (thisFlukeType!=null) {
        totalFlukeType+=thisFlukeType;
        numFlukes++;
      }
    }
    if (numFlukes==0) return;
    setFlukeType(totalFlukeType/numFlukes);
  }

  // assuming the list is of erroneously-duplicated encounters, returns the one we want to keep
  public static Encounter chooseFromDupes(List<Encounter> encs) {
    int maxAnns=-1;
    int encWithMax=0;
    for (int i=0;i<encs.size();i++) {
      Encounter enc = encs.get(i);
      if (enc.numAnnotations()>maxAnns) {
        maxAnns = enc.numAnnotations();
        encWithMax = i;
      }
    }
    return encs.get(encWithMax);
  }



  public static Integer getFlukeTypeFromAnnotation(Annotation ann) {
    return getFlukeTypeFromAnnotation(ann, 5);
  }

  // int maxScore is used because some people store flukeType on a 5 point (most standard), some on a 9 point scale
  public static Integer getFlukeTypeFromAnnotation(Annotation ann, int maxScore) {
    MediaAsset ma = ann.getMediaAsset();
    if (ma==null || !ma.hasKeywords()) return null;
    String flukeTypeKwPrefix = "fluke"+maxScore+":";
    for (Keyword kw: ma.getKeywords()) {
      String kwName = kw.getReadableName();
      if (kwName.contains(flukeTypeKwPrefix)) {
        String justScore = kwName.split(flukeTypeKwPrefix)[1];
        try {
          Integer score = Integer.parseInt(justScore);
          if (score!=null) return score;
        } catch (NumberFormatException nfe) {
          System.out.println("NFE on getFlukeTypeFromAnnotation! For ann "+ann+" and kwPrefix "+flukeTypeKwPrefix);
        }
      }
    }
    return null;
  }


    //yes, there "should" be only one of each of these, but we be thorough!
    public void removeLeftSpotMediaAssets(Shepherd myShepherd) {
    	ArrayList<MediaAsset> spotMAs = this.findAllMediaByLabel(myShepherd, "_spot");
        for (MediaAsset ma : spotMAs) {
            System.out.println("INFO: removeLeftSpotMediaAsset() detaching " + ma + " from parent id=" + ma.getParentId());
            ma.setParentId(null);
        }
    }
    public void removeRightSpotMediaAssets(Shepherd myShepherd) {
    	ArrayList<MediaAsset> spotMAs = this.findAllMediaByLabel(myShepherd, "_spotRight");
        for (MediaAsset ma : spotMAs) {
            System.out.println("INFO: removeRightSpotMediaAsset() detaching " + ma + " from parent id=" + ma.getParentId());
            ma.setParentId(null);
        }
    }

  public void nukeAllSpots() {
    leftReferenceSpots = null;
    rightReferenceSpots = null;
    spots = null;
    rightSpots = null;
  }


  /**
   * Returns the number of spots in the cropped image stored for this encounter.
   *
   * @return the number of superSpots that make up the digital fingerprint for this encounter
   */


  public int getNumSpots() {
    return (spots == null) ? 0 : spots.size();
/*
    ArrayList<SuperSpot> fakeSpots = HACKgetSpots();
    if(fakeSpots!=null){return fakeSpots.size();}
    else{return 0;}
*/
  }

  public int getNumRightSpots() {
    return (rightSpots == null) ? 0 : rightSpots.size();
/*
    ArrayList<SuperSpot> fakeRightSpots = HACKgetRightSpots();
    if(fakeRightSpots!=null){return fakeRightSpots.size();}
    else{return 0;}
*/
  }

  public boolean hasLeftSpotImage() {
    return (this.getNumSpots() > 0);
  }

  public boolean hasRightSpotImage() {
    return (this.getNumRightSpots() > 0);
  }


  /**
   * Sets the recorded length of the shark for this encounter.
   */
  public void setSize(Double mysize) {
	  if(mysize!=null){size = mysize;}
	  else{size=null;}

  }

  /**
   * Returns the recorded length of the shark for this encounter.
   *
   * @return the length of the shark
   */
  public double getSize() {
    return size.doubleValue();
  }

  public Double getSizeAsDouble() {
    return size;
  }
  

  /**
   * Sets the units of the recorded size and depth of the shark for this encounter.
   * Acceptable entries are either "Feet" or "Meters"
   */
  public void setMeasureUnits(String measure) {
    measurementUnit = measure;
  }

  /**
   * Returns the units of the recorded size and depth of the shark for this encounter.
   *
   * @return the units of measure used by the recorded of this encounter, either "feet" or "meters"
   */
  public String getMeasureUnits() {
    return measurementUnit;
  }

  public String getMeasurementUnit() {
    return measurementUnit;
  }

  /**
   * Returns the recorded location of this encounter.
   *
   * @return the location of this encounter
   */
  public String getLocation() {
    return verbatimLocality;
  }

  public void setLocation(String location) {
    this.verbatimLocality = location;
  }

  /**
   * Sets the recorded sex of the shark in this encounter.
   * Acceptable values are "Male" or "Female"
   */
  public void setSex(String thesex) {
    if(thesex!=null){sex = thesex;}
    else{sex=null;}
  }

  /**
   * Returns the recorded sex of the shark in this encounter.
   *
   * @return the sex of the shark, either "male" or "female"
   */
  public String getSex() {
    return sex;
  }

  /**
   * Returns any submitted comments about scarring on the shark.
   *
   * @return any comments regarding observed scarring on the shark's body
   */

	public boolean getMmaCompatible() {
                if (mmaCompatible == null) return false;
		return mmaCompatible;
	}
	public void setMmaCompatible(boolean b) {
		mmaCompatible = b;
	}

  public String getComments() {
    return occurrenceRemarks;
  }

  /**
   * Sets the initially submitted comments about markings and additional details on the shark.
   */
  public void setComments(String newComments) {
    occurrenceRemarks = newComments;
  }

  /**
   * Returns any comments added by researchers
   *
   * @return any comments added by authroized researchers
   */

  public String getRComments() {
    return researcherComments;
  }

  /**
   * Adds additional comments about the encounter
   *
   * @param newComments any additional comments to be added to the encounter
   */
  public void addComments(String newComments) {
    if ((researcherComments != null) && (!(researcherComments.equals("None")))) {
      researcherComments += newComments;
    } else {
      researcherComments = newComments;
    }
  }

  /**
   * Returns the name of the person who submitted this encounter data.
   *
   * @return the name of the person who submitted this encounter to the database
   */
  public String getSubmitterName() {
    return recordedBy;
  }

  public void setSubmitterName(String newname) {
    if(newname==null) {
      recordedBy=null;
    }
    else {
      recordedBy = newname;
    }
  }

  /**
   * Returns the e-mail address of the person who submitted this encounter data
   *
   * @return the e-mail address of the person who submitted this encounter data
   */
  public String getSubmitterEmail() {
    return submitterEmail;
  }

  public void setSubmitterEmail(String newemail) {
    if(newemail==null) {
      submitterEmail = null;
      this.hashedSubmitterEmail = null;
    }
    else {
      submitterEmail = newemail;
      this.hashedSubmitterEmail = Encounter.getHashOfEmailString(newemail);
    }
  }

  /**
   * Returns the phone number of the person who submitted this encounter data.
   *
   * @return the phone number of the person who submitted this encounter data
   */
  public String getSubmitterPhone() {
    return submitterPhone;
  }

  /**
   * Sets the phone number of the person who submitted this encounter data.
   */
  public void setSubmitterPhone(String newphone) {
    if(newphone==null) {
      submitterPhone=null;
    }
    else{
      submitterPhone = newphone;
    }
  }

  /**
   * Returns the mailing address of the person who submitted this encounter data.
   *
   * @return the mailing address of the person who submitted this encounter data
   */
  public String getSubmitterAddress() {
    return submitterAddress;
  }

  /**
   * Sets the mailing address of the person who submitted this encounter data.
   */
  public void setSubmitterAddress(String address) {
    if(address==null) {
      submitterAddress=null;
    }
    else {
      submitterAddress = address;
    }
  }

  /**
   * Returns the name of the person who took the primaryImage this encounter.
   *
   * @return the name of the photographer who took the primary image for this encounter
   */
  public String getPhotographerName() {
    return photographerName;
  }

  /**
   * Sets the name of the person who took the primaryImage this encounter.
   */
  public void setPhotographerName(String name) {
    if(name==null) {
      photographerName=null;
    }
    else {
      photographerName = name;
    }
  }

  /**
   * Returns the e-mail address of the person who took the primaryImage this encounter.
   *
   * @return  @return the e-mail address of the photographer who took the primary image for this encounter
   */
  public String getPhotographerEmail() {
    return photographerEmail;
  }

  /**
   * Sets the e-mail address of the person who took the primaryImage this encounter.
   */
  public void setPhotographerEmail(String email) {
    if(email==null) {
      photographerEmail = null;
      this.hashedPhotographerEmail = null;
    }
    else {
      photographerEmail = email;
      this.hashedPhotographerEmail = Encounter.getHashOfEmailString(email);
    }
  }

  /**
   * Returns the phone number of the person who took the primaryImage this encounter.
   *
   * @return the phone number of the photographer who took the primary image for this encounter
   */
  public String getPhotographerPhone() {
    return photographerPhone;
  }

  public String getWebUrl(HttpServletRequest req) {
    return getWebUrl(this.getCatalogNumber(), req);
  }
  public static String getWebUrl(String encId, HttpServletRequest req) {
    return getWebUrl(encId, CommonConfiguration.getServerURL(req));
  }
  public static String getWebUrl(String encId, String serverUrl) {
    return (serverUrl+"/encounters/encounter.jsp?number="+encId);
  }
  public String getHyperlink(HttpServletRequest req, int labelLength) {
    String label="";
    if (labelLength==1) label = "Enc ";
    if (labelLength> 1) label = "Encounter ";
    return "<a href=\""+getWebUrl(req)+"\">"+label+getCatalogNumber()+ "</a>";
  }
  public String getHyperlink(HttpServletRequest req) {
    return getHyperlink(req, 1);
  }

  /**
   * Sets the phone number of the person who took the primaryImage this encounter.
   */
  public void setPhotographerPhone(String phone) {
    if(phone==null) {
      photographerPhone=null;
    }
    else {
      photographerPhone = phone;
    }
  }

  /**
   * Returns the mailing address of the person who took the primaryImage this encounter.
   *
   * @return the mailing address of the photographer who took the primary image for this encounter
   */
  public String getPhotographerAddress() {
    return photographerAddress;
  }

  /**
   * Sets the mailing address of the person who took the primaryImage this encounter.
   */
  public void setPhotographerAddress(String address) {
    if(address==null) {
      photographerAddress=null;
    }
    else {
      photographerAddress = address;
    }
  }

  /**
   * Sets the recorded depth of this encounter.
   */
  public void setDepth(Double myDepth) {
	  if(myDepth!=null){maximumDepthInMeters = myDepth;}
	  else{maximumDepthInMeters = null;}
  }

  /**
   * Returns the recorded depth of this encounter.
   *
   * @return the recorded depth for this encounter
   */
  public double getDepth() {
    return maximumDepthInMeters.doubleValue();
  }

  public Double getDepthAsDouble(){
	  return maximumDepthInMeters;
  }


  //public Vector getAdditionalImages() {return additionalImages;}

  /**
   * Returns the file names of all images taken for this encounter.
   *
   * @return a vector of image name Strings
   */
  public Vector getAdditionalImageNames() {
    Vector imageNamesOnly=new Vector();

    //List<SinglePhotoVideo> images=getCollectedDataOfClass(SinglePhotoVideo.class);
    if((images!=null)&&(images.size()>0)){
      int imagesSize=images.size();
      for(int i=0;i<imagesSize;i++){
        SinglePhotoVideo dce=(SinglePhotoVideo)images.get(i);
        imageNamesOnly.add(dce.getFilename());
      }
    }
    return imageNamesOnly;
  }


  public String getFieldID() {
    return this.fieldID;
  }
  public void setFieldID(String fieldID) {
    this.fieldID = fieldID;
  }

  public String getGroupRole() {
    return this.groupRole;
  }
  public void setGroupRole(String role) {
    this.groupRole = role;
  }

  public String getDataSource() {
    return dataSource;
  }
  public void setDataSource(String dataSource) {
    this.dataSource = dataSource;
  }
  
  public String getImageOriginalName() {
    MediaAsset ma = getPrimaryMediaAsset();
    if (ma == null) return null;
    return ma.getFilename();

  }

  /**
   * Adds another image to the collection of images for this encounter.
   * These images should be the additional or non-side shots.
   *

  public void addAdditionalImageName(SinglePhotoVideo file) {
    images.add(file);

  }
*/
/*
  public void approve() {
    approved = true;
    okExposeViaTapirLink = true;
  }
*/
  /**
  public void resetAdditionalImageName(int position, String fileName) {
    additionalImageNames.set(position, fileName);
    //additionalImageNames.add(fileName);
  }
*/


  /**
   * Removes the specified additional image from this encounter.
   *
   * @param  imageFile  the image to be removed from the additional images stored for this encounter
   */
  /*
  public void removeAdditionalImageName(String imageFile) {

    for (int i = 0; i < collectedData.size(); i++) {


      String thisName = images.get(i).getFilename();
      if ((thisName.equals(imageFile)) || (thisName.indexOf("#") != -1)) {
        images.remove(i);
        i--;
      }

    }


  }
  */

  /*
  public void removeDataCollectionEvent(DataCollectionEvent dce) {
   collectedData.remove(dce);
  }
*/
  /**
   * Returns the unique encounter identifier number for this encounter.
   *
   * @return a unique integer String used to identify this encounter in the database
   */
  public String getEncounterNumber() {
    return catalogNumber;
  }


	public String generateEncounterNumber() {
		return Util.generateUUID();
	}


	public String dir(String baseDir) {
		return baseDir + File.separator + "encounters" + File.separator + this.subdir();
	}


	//like above, but class method so you pass the encID
	public static String dir(String baseDir, String id) {
		return baseDir + File.separator + "encounters" + File.separator + subdir(id);
	}


	//like above, but can pass a File in for base
	public static String dir(File baseDir, String id) {
		return baseDir.getAbsolutePath() + File.separator + "encounters" + File.separator + subdir(id);
	}


	//subdir() is kind of a utility function, which can be called as enc.subdir() or Encounter.subdir(IDSTRING) as needed
	public String subdir() {
		return subdir(this.getEncounterNumber());
	}

	public static String subdir(String id) {
		String d = id;  //old-world
		if (Util.isUUID(id)) {  //new-world
			d = id.charAt(0) + File.separator + id.charAt(1) + File.separator + id;
		}
		return d;
	}


  /**
   * Returns the date of this encounter.
   *
   * @return a Date object
   * @see java.util.Date
   */
  public String getDate() {
    String date = "";
    String time = "";
    if (year <= 0) {
      return "Unknown";
    } else if (month == -1) {
      return Integer.toString(year);
    }

    if (hour != -1) {
      String localMinutes=minutes;
      if(localMinutes.length()==1){localMinutes="0"+localMinutes;}
      time = String.format("%02d:%s", hour, localMinutes);
    }

    if (day > 0) {
      date = String.format("%04d-%02d-%02d %s", year, month, day, time);
    }
    else if(month>0) {
      date = String.format("%04d-%02d %s", year, month, time);
    }
    else {
      date = String.format("%04d %s", year, time);
    }

    return date;
  }

  public String getShortDate() {
    String date = "";
    if (year <= 0) {
      return "Unknown";
    } else if (month == -1) {
      return Integer.toString(year);
    }
    if (day > 0) {
      date = String.format("%02d/%02d/%04d", day, month, year);
    } else {
      date = String.format("%02d/%04d", month, year);
    }

    return date;
  }

  /**
   * Returns the String discussing how the size of this animal was approximated.
   *
   * @return a String with text about how the size of this animal was estimated/measured
   */
  public String getSizeGuess() {
    return size_guess;
  }

  public void setDay(int day) {
    this.day=day;
    resetDateInMilliseconds();
  }

  public void setHour(int hour) {
    this.hour=hour;
    resetDateInMilliseconds();
  }

  public void setMinutes(String minutes) {
    this.minutes=minutes;
    resetDateInMilliseconds();
  }

  public String getMinutes() {
    return minutes;
  }

  public int getHour() {
    return hour;
  }

  public void setMonth(int month) {
    this.month=month;
    resetDateInMilliseconds();
  }
  public void setYear(int year) {
    this.year=year;
    resetDateInMilliseconds();
  }
  // this does not reset year/month/etc
  public void setDateInMillisOnly(long ms) {
      this.dateInMilliseconds = ms;
  }


  public int getDay() {
    return day;
  }

  public int getMonth() {
    return month;
  }

  public int getYear() {
    return year;
  }

  public boolean wasInPeriod(DateTime start, DateTime end) {
    Long thisTime = getDateInMilliseconds();
    if (thisTime==null) return false;
    return (start.getMillis()<=thisTime && end.getMillis()>thisTime);
  }


  /**
   * Returns the String holding specific location data used for searching
   *
   * @return the String holding specific location data used for searching
   */
  public String getLocationCode() {
    return locationID;
  }

  /**
   * A legacy method replaced by setLocationID(...).
   *
   *
   */
  public void setLocationCode(String newLoc) {
    setLocationID(newLoc);
  }

  /**
   * Returns the String holding specific location data used for searching
   *
   * @return the String holding specific location data used for searching
   */
  public String getDistinguishingScar() {
    return distinguishingScar;
  }

  /**
   * Sets the String holding scarring information for the encounter
   */
  public void setDistinguishingScar(String scar) {
    distinguishingScar = scar;
  }

  /**
   * Sets the String documenting how the size of this animal was approximated.
   */
  public void setSizeGuess(String newGuess) {
    size_guess = newGuess;
  }

  public String getMatchedBy() {
    if ((identificationRemarks == null) || (identificationRemarks.equals(""))) {
      return "Unknown";
    }
    return identificationRemarks;
  }

  public void setMatchedBy(String matchType) {
    identificationRemarks = matchType;
  }

  public void setIdentificationRemarks(String matchType) {
    identificationRemarks = matchType;
  }


  /**
   * Sets the unique encounter identifier to be usd with this encounter.
   * Once this is set, it cannot be changed without possible impact to the
   * database structure.
   *
   * @param num the unique integer to be used to uniquely identify this encoun ter in the database
   */
  public void setEncounterNumber(String num) {
    catalogNumber = num;
  }

    public boolean hasMarkedIndividual() {
        return (individual != null);
    }

    public void assignToMarkedIndividual(MarkedIndividual indiv) {
        setIndividual(indiv);
    }

    public void setIndividual(MarkedIndividual indiv) {
        if(indiv==null) {this.individual=null;}
        else{this.individual = indiv;}
    }

    public MarkedIndividual getIndividual() {
        return individual;
    }

    public String getDisplayName() {
      return (individual==null) ? null : individual.getDisplayName();
    }

    public String getIndividualID() {
        if (individual == null) return null;
        return individual.getId();
  }

  /*
  public boolean wasRejected() {

    return unidentifiable;
  }

  public void reject() {
    unidentifiable = true;
    //okExposeViaTapirLink=false;
  }

  public void reaccept() {
    unidentifiable = false;
    //okExposeViaTapirLink=true;
  }
*/
  public String getGPSLongitude() {
    if (gpsLongitude == null) {
      return "";
    } else {
      return gpsLongitude;
    }
  }

  public void setGPSLongitude(String newLong) {

    gpsLongitude = newLong;
  }

  public String getGPSLatitude() {
    if (gpsLatitude == null) {
      return "";
    } else {
      return gpsLatitude;
    }
  }

  public void setGPSLatitude(String newLat) {
    gpsLatitude = newLat;
  }


  public Encounter getClone() {
    Encounter tempEnc = new Encounter();
    try {
      tempEnc = (Encounter) this.clone();
    } catch (java.lang.CloneNotSupportedException e) {
      e.printStackTrace();
    }

    return tempEnc;
  }

  public String getSpotImageFileName() {
    return spotImageFileName;
  }

  public void setSpotImageFileName(String name) {
    spotImageFileName = name;
  }

  //-------------
  //for the right side spot image

  public String getRightSpotImageFileName() {
    return rightSpotImageFileName;
  }

  public void setRightSpotImageFileName(String name) {
    rightSpotImageFileName = name;
  }

  //----------------


    //really only intended to convert legacy SinglePhotoVideo to MediaAsset/Annotation world
    public ArrayList<Annotation> generateAnnotations(String baseDir, Shepherd myShepherd) {
        if ((annotations != null) && (annotations.size() > 0)) return annotations;
        if ((images == null) || (images.size() < 1)) return null;  //probably pointless, so...
        if (annotations == null) annotations = new ArrayList<Annotation>();
        boolean thumbDone = false;
        ArrayList<MediaAsset> haveMedia = new ArrayList<MediaAsset>();  //so we dont add duplicates!
        for (SinglePhotoVideo spv : images) {
            MediaAsset ma = spv.toMediaAsset(myShepherd);
            if (ma == null) {
                System.out.println("WARNING: Encounter.generateAnnotations() could not create MediaAsset from SinglePhotoVideo " + spv.getDataCollectionEventID() + "; skipping");
                continue;
            }
            if (haveMedia.contains(ma)) {
                System.out.println("WARNING: Encounter.generateAnnotations() found a duplicate MediaAsset in the SinglePhotoVideo images; skipping -- " + ma);
                continue;
            }

            //note: we need at least minimal metadata (w,h) in order to make annotation, so if this fails, we are no-go
            try {
                ma.updateMetadata();
            } catch (IOException ioe) {
                System.out.println("WARNING: Encounter.generateAnnotations() failed to updateMetadata() on original MediaAsset " + ma + " (skipping): " + ioe.toString());
                continue;
            }

            ma.addLabel("_original");
            haveMedia.add(ma);

            annotations.add(new Annotation(getTaxonomyString(), ma));
            //if (!media.contains(ma)) media.add(ma);
            //File idir = new File(this.dir(baseDir));
            File idir = new File(spv.getFullFileSystemPath()).getParentFile();
            //now we iterate through flavors that could be derived
            //TODO is it bad to assume ".jpg" ? i forget!
            addMediaIfNeeded(myShepherd, new File(idir, spv.getDataCollectionEventID() + ".jpg"), "spv/" + spv.getDataCollectionEventID() + "/" + spv.getDataCollectionEventID() + ".jpg", ma, "_watermark");
            addMediaIfNeeded(myShepherd, new File(idir, spv.getDataCollectionEventID() + "-mid.jpg"), "spv/" + spv.getDataCollectionEventID() + "/" + spv.getDataCollectionEventID() + "-mid.jpg", ma, "_mid");

            // note: we "assume" thumb was created from 0th spv, cuz we simply dont know but want it living somewhere
            if (!thumbDone) addMediaIfNeeded(myShepherd, new File(idir, "/thumb.jpg"), "spv/" + spv.getDataCollectionEventID() + "/thumb.jpg", ma, "_thumb");
            thumbDone = true;
        }

        //we need to have the spot image as a child under *some* MediaAsset from above, but unfortunately we do not know its lineage.  so we just pick one.  :/
        MediaAsset sma = spotImageAsMediaAsset(((annotations.size() < 1) ? null : annotations.get(0).getMediaAsset()), baseDir, myShepherd);
        return annotations;
    }


    //utility method for created MediaAssets
    // note: also will check for existence of mpath and fail silently if doesnt exist
    private MediaAsset addMediaIfNeeded(Shepherd myShepherd, File mpath, String key, MediaAsset parentMA, String label) {
        if ((mpath == null) || !mpath.exists()) return null;
        AssetStore astore = AssetStore.getDefault(myShepherd);
        org.json.JSONObject sp = astore.createParameters(mpath);
        if (key != null) sp.put("key", key);  //will use default from createParameters() (if there was one even)
        MediaAsset ma = astore.find(sp, myShepherd);
        if (ma != null) {
            ma.addLabel(label);
            if (parentMA != null) ma.setParentId(parentMA.getId());
            return ma;
        }
System.out.println("creating new MediaAsset for key=" + key);
        try {
            ma = astore.copyIn(mpath, sp);
        } catch (IOException ioe) {
            System.out.println("Could not create MediaAsset for key=" + key + ": " + ioe.toString());
            return null;
        }
        if (parentMA != null) {
            ma.setParentId(parentMA.getId());
            ma.updateMinimalMetadata();  //for children (ostensibly derived?) MediaAssets, really only need minimal metadata or so i claim
        } else {
            try {
                ma.updateMetadata();  //root images get the whole deal (guess this sh/could key off label=_original ?)
            } catch (IOException ioe) {
                //we dont care (well sorta) ... since IOException usually means we couldnt open file or some nonsense that we cant recover from
            }
        }
        ma.addLabel(label);
        MediaAssetFactory.save(ma, myShepherd);
        return ma;
    }


    //this makes assumption (for flukes) that both right and left image files are identical
    //  TODO handle that they are different
    //  TODO also maybe should reuse addMediaIfNeeded() for some of this where redundant
    public MediaAsset spotImageAsMediaAsset(MediaAsset parent, String baseDir, Shepherd myShepherd) {
        if ((spotImageFileName == null) || spotImageFileName.equals("")) return null;
        File fullPath = new File(this.dir(baseDir) + "/" + spotImageFileName);
//System.out.println("**** * ***** looking for spot file " + fullPath.toString());
        if (!fullPath.exists()) return null;  //note: this only technically matters if we are *creating* the MediaAsset
        if (parent == null) {
            System.out.println("seems like we do not have a parent MediaAsset on enc " + this.getCatalogNumber() + ", so cannot add spot MediaAsset for " + fullPath.toString());
            return null;
        }
        AssetStore astore = AssetStore.getDefault(myShepherd);
        if (astore == null) {
            System.out.println("No AssetStore in Encounter.spotImageAsMediaAsset()");
            return null;
        }
System.out.println("trying spotImageAsMediaAsset with file=" + fullPath.toString());
        org.json.JSONObject sp = astore.createParameters(fullPath);
        sp.put("key", this.subdir() + "/spotImage-" + spotImageFileName);  //note: this really only applies to S3 AssetStores, but shouldnt hurt others?
        MediaAsset ma = astore.find(sp, myShepherd);
        if (ma == null) {
System.out.println("did not find MediaAsset for params=" + sp + "; creating one?");
            try {
                ma = astore.copyIn(fullPath, sp);
                ma.addDerivationMethod("historicSpotImageConversion", true);
                ma.updateMinimalMetadata();
//System.out.println("params? " + ma.getParameters());
                ma.addLabel("_spot");
                ma.addLabel("_annotation");
                MediaAssetFactory.save(ma, myShepherd);
//System.out.println("params? " + ma.getParameters());
            } catch (java.io.IOException ex) {
                System.out.println("spotImageAsMediaAsset threw IOException " + ex.toString());
            }
        }
        ma.setParentId(parent.getId());
        return ma;
    }


  public void setSubmitterID(String username) {
    if(username!=null){submitterID = username;}
    else{submitterID=null;}
  }



  //old method. use getAssignedUser() instead
  public String getSubmitterID() {
    return getAssignedUsername();
  }

  public String getAssignedUsername() {
    return submitterID;
  }

  
  public Vector getInterestedResearchers() {
    return interestedResearchers;
  }

  public void addInterestedResearcher(String email) {
    interestedResearchers.add(email);
  }
  

 /*
  public boolean isApproved() {
    return approved;
  }
  */

  /*
  public void removeInterestedResearcher(String email) {
    for (int i = 0; i < interestedResearchers.size(); i++) {
      String rName = (String) interestedResearchers.get(i);
      if (rName.equals(email)) {
        interestedResearchers.remove(i);
      }
    }
  }
*/

  public double getRightmostSpot() {
    double rightest = 0;
    ArrayList<SuperSpot> spots = getSpots();
    for (int iter = 0; iter < spots.size(); iter++) {
      if (spots.get(iter).getTheSpot().getCentroidX() > rightest) {
        rightest = spots.get(iter).getTheSpot().getCentroidX();
      }
    }
    return rightest;
  }

  public double getLeftmostSpot() {
    double leftest = getRightmostSpot();
    ArrayList<SuperSpot> spots = getSpots();
    for (int iter = 0; iter < spots.size(); iter++) {
      if (spots.get(iter).getTheSpot().getCentroidX() < leftest) {
        leftest = spots.get(iter).getTheSpot().getCentroidX();
      }
    }
    return leftest;
  }

  public double getHighestSpot() {
    double highest = getLowestSpot();
    ArrayList<SuperSpot> spots = getSpots();
    for (int iter = 0; iter < spots.size(); iter++) {
      if (spots.get(iter).getTheSpot().getCentroidY() < highest) {
        highest = spots.get(iter).getTheSpot().getCentroidY();
      }
    }
    return highest;
  }

  public double getLowestSpot() {
    double lowest = 0;
    ArrayList<SuperSpot> spots = getSpots();
    for (int iter = 0; iter < spots.size(); iter++) {
      if (spots.get(iter).getTheSpot().getCentroidY() > lowest) {
        lowest = spots.get(iter).getTheSpot().getCentroidY();
      }
    }
    return lowest;
  }

  public com.reijns.I3S.Point2D[] getThreeLeftFiducialPoints() {
    com.reijns.I3S.Point2D[] Rray = new com.reijns.I3S.Point2D[3];
    if (getLeftReferenceSpots() != null) {

      ArrayList<SuperSpot> refsLeft = getLeftReferenceSpots();

      Rray[0] = new com.reijns.I3S.Point2D(refsLeft.get(0).getTheSpot().getCentroidX(), refsLeft.get(0).getTheSpot().getCentroidY());
      Rray[1] = new com.reijns.I3S.Point2D(refsLeft.get(1).getTheSpot().getCentroidX(), refsLeft.get(1).getTheSpot().getCentroidY());
      Rray[2] = new com.reijns.I3S.Point2D(refsLeft.get(2).getTheSpot().getCentroidX(), refsLeft.get(2).getTheSpot().getCentroidY());
      System.out.println("	I found three left reference points!");

    } else {
      com.reijns.I3S.Point2D topLeft = new com.reijns.I3S.Point2D(getLeftmostSpot(), getHighestSpot());
      com.reijns.I3S.Point2D bottomLeft = new com.reijns.I3S.Point2D(getLeftmostSpot(), getLowestSpot());
      com.reijns.I3S.Point2D bottomRight = new com.reijns.I3S.Point2D(getRightmostSpot(), getLowestSpot());
      Rray[0] = topLeft;
      Rray[1] = bottomLeft;
      Rray[2] = bottomRight;
    }

    return Rray;
  }

  public com.reijns.I3S.Point2D[] getThreeRightFiducialPoints() {
    com.reijns.I3S.Point2D[] Rray = new com.reijns.I3S.Point2D[3];
    if (getRightReferenceSpots() != null) {
      ArrayList<SuperSpot> refsRight = getRightReferenceSpots();
      Rray[0] = new com.reijns.I3S.Point2D(refsRight.get(0).getTheSpot().getCentroidX(), refsRight.get(0).getTheSpot().getCentroidY());
      Rray[1] = new com.reijns.I3S.Point2D(refsRight.get(1).getTheSpot().getCentroidX(), refsRight.get(1).getTheSpot().getCentroidY());
      Rray[2] = new com.reijns.I3S.Point2D(refsRight.get(2).getTheSpot().getCentroidX(), refsRight.get(2).getTheSpot().getCentroidY());

    } else {

      com.reijns.I3S.Point2D topRight = new com.reijns.I3S.Point2D(getRightmostRightSpot(), getHighestRightSpot());
      com.reijns.I3S.Point2D bottomRight = new com.reijns.I3S.Point2D(getRightmostRightSpot(), getLowestRightSpot());
      com.reijns.I3S.Point2D bottomLeft = new com.reijns.I3S.Point2D(getLeftmostRightSpot(), getLowestRightSpot());

      Rray[0] = topRight;
      Rray[1] = bottomRight;
      Rray[2] = bottomLeft;
    }
    return Rray;
  }

  public double getRightmostRightSpot() {
    double rightest = 0;
    ArrayList<SuperSpot> rightSpots = getRightSpots();
    for (int iter = 0; iter < rightSpots.size(); iter++) {
      if (rightSpots.get(iter).getTheSpot().getCentroidX() > rightest) {
        rightest = rightSpots.get(iter).getTheSpot().getCentroidX();
      }
    }
    return rightest;
  }


  public double getLeftmostRightSpot() {
    double leftest = getRightmostRightSpot();
    ArrayList<SuperSpot> rightSpots = getRightSpots();
    for (int iter = 0; iter < rightSpots.size(); iter++) {
      if (rightSpots.get(iter).getTheSpot().getCentroidX() < leftest) {
        leftest = rightSpots.get(iter).getTheSpot().getCentroidX();
      }
    }
    return leftest;
  }

  public double getHighestRightSpot() {
    double highest = getLowestRightSpot();
    ArrayList<SuperSpot> rightSpots = getRightSpots();
    for (int iter = 0; iter < rightSpots.size(); iter++) {
      if (rightSpots.get(iter).getTheSpot().getCentroidY() < highest) {
        highest = rightSpots.get(iter).getTheSpot().getCentroidY();
      }
    }
    return highest;
  }

  public double getLowestRightSpot() {
    double lowest = 0;
    ArrayList<SuperSpot> rightSpots = getRightSpots();
    for (int iter = 0; iter < rightSpots.size(); iter++) {
      if (rightSpots.get(iter).getTheSpot().getCentroidY() > lowest) {
        lowest = rightSpots.get(iter).getTheSpot().getCentroidY();
      }
    }
    return lowest;
  }


  public ArrayList<SuperSpot> getLeftReferenceSpots() {
    //return HACKgetAnyReferenceSpots();
    return leftReferenceSpots;
  }

  public ArrayList<SuperSpot> getRightReferenceSpots() {
    //return HACKgetAnyReferenceSpots();
    return rightReferenceSpots;
  }

/*  gone! no more setting spots on encounters!  ... whoa there, yes there is for whaleshark.org */
  public void setLeftReferenceSpots(ArrayList<SuperSpot> leftReferenceSpots) {
    this.leftReferenceSpots = leftReferenceSpots;
  }

  public void setRightReferenceSpots(ArrayList<SuperSpot> rightReferenceSpots) {
    this.rightReferenceSpots = rightReferenceSpots;
  }



  /**
   * @param population array values to get the variance for
   * @return the variance
   */
  public double variance(double[] population) {
    long n = 0;
    double mean = 0;
    double s = 0.0;

    for (double x : population) {
      n++;
      double delta = x - mean;
      mean += delta / n;
      s += delta * (x - mean);
    }
    // if you want to calculate std deviation
    // of a sample change this to (s/(n-1))
    //return (s / n);
    return (s / (n - 1));
  }

  /**
   * @param population array values to get the standard deviation for
   * @return the standard deviation
   */
  public double standard_deviation(double[] population) {
    return Math.sqrt(variance(population));
  }


/*  GONE!  no more spots on encounters
  public void setNumLeftSpots(int numspots) {
    numSpotsLeft = numspots;
  }

  public void setNumRightSpots(int numspots) {
    numSpotsRight = numspots;
  }
*/


  public void setDWCGlobalUniqueIdentifier(String guid) {
    this.guid = guid;
  }

  public String getDWCGlobalUniqueIdentifier() {
    return guid;
  }

  public void setDWCImageURL(String link) {
    dwcImageURL = link;
  }
  // lmao, we have this capitalization of the getter for reflexivity purposes
  public String getModified() {
    return modified;
  }
  public String getDWCDateLastModified() {
    return modified;
  }

  public void setDWCDateLastModified(String lastModified) {
    modified = lastModified;
  }
    public void setDWCDateLastModified() {
        modified = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    }

  public String getDWCDateAdded() {
    return dwcDateAdded;
  }

  // lmao, we have this capitalization of the getter for reflexivity purposes
  public String getDwcDateAdded() {
    return dwcDateAdded;
  }


  public Long getDWCDateAddedLong(){
    return dwcDateAddedLong;
  }

  public void setDWCDateAdded(String m_dateAdded) {
    dwcDateAdded = m_dateAdded;
  }
    public void setDWCDateAdded() {
        Date myDate=new Date();
        dwcDateAddedLong=new Long(myDate.getTime());
        dwcDateAdded = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(myDate);
    }


 public void setDWCDateAdded(Long m_dateAdded) {
    dwcDateAddedLong = m_dateAdded;
    //org.joda.time.DateTime dt=new org.joda.time.DateTime(dwcDateAddedLong.longValue());
    //DateTimeFormatter parser1 = ISODateTimeFormat.dateOptionalTimeParser();
    //setDWCDateAdded(dt.toString(parser1));
    //System.out.println("     Encounter.detDWCDateAded(Long): "+dt.toString(parser1)+" which is also "+m_dateAdded.longValue());
  }
  //public void setDateAdded(long date){dateAdded=date;}
  //public long getDateAdded(){return dateAdded;}

  public Date getReleaseDateDONOTUSE() {
    return releaseDate;
  }

   public Date getReleaseDate() {
    if((releaseDateLong!=null)&&(releaseDateLong>0)){
      Date mDate=new Date(releaseDateLong);
      return mDate;
    }
    return null;
  }

   public Long getReleaseDateLong(){return releaseDateLong;}

  public void setReleaseDate(Long releaseDate) {
    this.releaseDateLong = releaseDate;
  }
  
  // Survey ect associations...
  
  public void setSurveyTrackID(String id) {
    if (id != null && !id.equals("")) {
      this.correspondingSurveyTrackID = id;
    }
  }

  public String getSurveyTrackID() {
    if (correspondingSurveyTrackID != null) {
      return correspondingSurveyTrackID;
    }
    return null;
  }
  
  public void setPointLocation(PointLocation loc) {
    if (loc.getID() != null) {
      this.pointLocation = loc;
    }
  }
  
  public PointLocation getPointLocation() {
    if (pointLocation != null) {
      return pointLocation;
    }
    return null;
  }
  
  public String getSurveyID() {
    if (correspondingSurveyID != null && !correspondingSurveyID.equals("")) {
      return correspondingSurveyID;
    }  
    return null;
  }
  
  public void setSurveyID(String id) {
    if (id != null && !id.equals("")) {
      this.correspondingSurveyID = id;
    }
  }
  
  
  public void setSurvey() {
    
  }
  
  // TODO Get all this lat lon over to Locations

  public void setDWCDecimalLatitude(double lat) {
    if (lat == -9999.0) {
      decimalLatitude = null;
    } else {
      decimalLatitude = (new Double(lat));
    }
  }

  public void setDWCDecimalLatitude(Double lat){
    if((lat!=null)&&(lat<=90)&&(lat>=-90)){
      this.decimalLatitude=lat;
    }
    else{this.decimalLatitude=null;}
  }
  public String getDWCDecimalLatitude(){
   if(decimalLatitude!=null){return Double.toString(decimalLatitude);}
     return null;
   }
  public void setDWCDecimalLongitude(double longit){
    if((longit>=-180)&&(longit<=180)){
      this.decimalLongitude=longit;
    }
  }

  public String getDWCDecimalLongitude(){
    if(decimalLongitude!=null){
      return Double.toString(decimalLongitude);
    }
    return null;
  }

  public boolean getOKExposeViaTapirLink() {
    return okExposeViaTapirLink;
  }

  public void setOKExposeViaTapirLink(boolean ok) {
    okExposeViaTapirLink = ok;
  }

  public void setAlternateID(String newID) {
    this.otherCatalogNumbers = newID;
  }

  public String getAlternateID() {
    if (otherCatalogNumbers == null) {
      return null;
    }
    return otherCatalogNumbers;
  }

  public String getOLDInformOthersFORLEGACYCONVERSION() {
    if (informothers == null) {
      return "";
    }
    return informothers;
  }

  /*
  public void setInformOthers(String others) {
    this.informothers = others;
    this.hashedInformOthers = Encounter.getHashOfEmailString(others);
  }
  */

  public String getLocationID() {
    return locationID;
  }

  public void setLocationID(String newLocationID) {
    this.locationID = newLocationID.trim();
  }

  public Double getMaximumDepthInMeters() {
    return maximumDepthInMeters;
  }

  public void setMaximumDepthInMeters(Double newDepth) {
    this.maximumDepthInMeters = newDepth;
  }

  public Double getMaximumElevationInMeters() {
    return maximumElevationInMeters;
  }

  public void setMaximumElevationInMeters(Double newElev) {
    this.maximumElevationInMeters = newElev;
  }


  public String getCatalogNumber() {
    return catalogNumber;
  }

  public void setCatalogNumber(String newNumber) {
    this.catalogNumber = newNumber;
  }

  public String getID() {
    return catalogNumber;
  }

  public void setID(String newNumber) {
    this.catalogNumber = newNumber;
  }

  public String getVerbatimLocality() {
    return verbatimLocality;
  }

  public void setVerbatimLocality(String vlcl) {
    this.verbatimLocality = vlcl;
  }

/* i cant for the life of me figure out why/how gps stuff is stored on encounters, cuz we have
some strings and decimal (double, er Double?) values -- so i am doing my best to standardize on
the decimal one (Double) .. half tempted to break out a class for this: lat/lon/alt/bearing etc */
  public Double getDecimalLatitudeAsDouble(){return (decimalLatitude == null) ? null : decimalLatitude.doubleValue();}

  public void setDecimalLatitude(Double lat){
      this.decimalLatitude = lat;
      gpsLatitude = Util.decimalLatLonToString(lat);
   }

  public Double getDecimalLongitudeAsDouble(){return (decimalLongitude == null) ? null : decimalLongitude.doubleValue();}

  public void setDecimalLongitude(Double lon) {
      this.decimalLongitude = lon;
      gpsLongitude = Util.decimalLatLonToString(lon);
  }
  
  public Double getEndDecimalLatitudeAsDouble(){return (endDecimalLatitude == null) ? null : endDecimalLatitude.doubleValue();}

  public void setEndDecimalLatitude(Double lat){
      this.endDecimalLatitude = lat;
      gpsEndLatitude = Util.decimalLatLonToString(lat);
   }

  public Double getEndDecimalLongitudeAsDouble(){return (endDecimalLongitude == null) ? null : endDecimalLongitude.doubleValue();}

  public void setEndDecimalLongitude(Double lon) {
      this.endDecimalLongitude = lon;
      gpsEndLongitude = Util.decimalLatLonToString(lon);
  } 

  public String getOccurrenceRemarks() {
    return occurrenceRemarks;
  }

  public void setOccurrenceRemarks(String remarks) {
    this.occurrenceRemarks = remarks;
  }

  public String getRecordedBy() {
    return recordedBy;
  }

  public void setRecordedBy(String submitterName) {
    this.recordedBy = submitterName;
  }

  public String getOtherCatalogNumbers() {
    return otherCatalogNumbers;
  }

  public void setOtherCatalogNumbers(String otherNums) {
    this.otherCatalogNumbers = otherNums;
  }

  public String getLivingStatus() {
    return livingStatus;
  }

  public void setLivingStatus(String status) {
    this.livingStatus = status;
  }

    public void setAge(Double a) {
        age = a;
    }
    public Double getAge() {
        return age;
    }

  public String getBehavior() {
    return behavior;
  }

  public void setBehavior(String beh) {
    this.behavior = beh;
  }

  public String getEventID() {
    return eventID;
  }

  public void setEventID(String id) {
    this.eventID = id;
  }

  public String getVerbatimEventDate() {
    return verbatimEventDate;
  }


  public void setVerbatimEventDate(String vet) {
      if(vet!=null){this.verbatimEventDate = vet;}
  	  else{this.verbatimEventDate=null;}
  }

  public String getDynamicProperties() {
    return dynamicProperties;
  }
  public void setDynamicProperties(String allDynamicProperties) {
    this.dynamicProperties = allDynamicProperties;
  }
  public void addDynamicProperties(String allDynamicProperties) {
    this.dynamicProperties+=allDynamicProperties;
  }
  public void setDynamicProperty(String name, String value){
    name=name.replaceAll(";", "_").trim().replaceAll("%20", " ");
    value=value.replaceAll(";", "_").trim();

    if(dynamicProperties==null){dynamicProperties=name+"="+value+";";}
    else{

      //let's create a TreeMap of the properties
      TreeMap<String,String> tm=new TreeMap<String,String>();
      StringTokenizer st=new StringTokenizer(dynamicProperties, ";");
      while(st.hasMoreTokens()){
        String token = st.nextToken();
        int equalPlace=token.indexOf("=");
        try{
          tm.put(token.substring(0,equalPlace), token.substring(equalPlace+1));
       }
       catch(java.lang.StringIndexOutOfBoundsException soe){
       //this is a badly formatted pair that should be ignored
     }
      }
      if(tm.containsKey(name)){
        tm.remove(name);
        tm.put(name, value);

        //now let's recreate the dynamicProperties String
        String newProps=tm.toString();
        int stringSize=newProps.length();
        dynamicProperties=newProps.substring(1,(stringSize-1)).replaceAll(", ", ";")+";";
      }
      else{
        dynamicProperties=dynamicProperties+name+"="+value+";";
      }
    }
  }

  public String getDynamicPropertyValue(String name) {
    if (dynamicProperties != null) {
      name = name.replaceAll("%20", " ");
      //let's create a TreeMap of the properties
      TreeMap<String, String> tm = new TreeMap<String, String>();
      StringTokenizer st = new StringTokenizer(dynamicProperties, ";");
      while (st.hasMoreTokens()) {
        String token = st.nextToken();
        int equalPlace = token.indexOf("=");
        tm.put(token.substring(0, equalPlace), token.substring(equalPlace + 1));
      }
      if (tm.containsKey(name)) {
        return tm.get(name);
      }
    }
    return null;
  }
  public boolean hasDynamicProperty(String name) {
    return ( this.getDynamicPropertyValue(name) != null );
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


  public String getIdentificationRemarks() {
    return identificationRemarks;
  }

  public String getHashedSubmitterEmail() {
    return hashedSubmitterEmail;
  }

  public String getHashedPhotographerEmail() {
    return hashedPhotographerEmail;
  }

  public String getHashedInformOthers() {
    return hashedInformOthers;
  }

  public static String getHashOfEmailString(String hashMe) {
    if (hashMe == null) return null;
    String returnString = "";
    StringTokenizer tokenizer = new StringTokenizer(hashMe, ",");
    while (tokenizer.hasMoreTokens()) {
      String emailAddress = tokenizer.nextToken().trim().toLowerCase();
      if (!emailAddress.equals("")) {
        String md5 = DigestUtils.md5Hex(emailAddress);
        if (returnString.equals("")) {
          returnString += md5;
        } else {
          returnString += "," + md5;
        }
      }
    }
    return returnString;
  }

  public String getGenus() {
    return genus;
  }

  public void setGenus(String newGenus) {
    if(newGenus!=null){genus = newGenus;}
	  else{genus=null;}
  }
  // we need these methods because our side-effected setGenus will silently break an import (!!!!!) in an edge case I cannot identify
  public void setGenusOnly(String genus) {
    this.genus = genus;
  }
  public void setSpeciesOnly(String species) {
    this.specificEpithet = species;
  }

  public String getSpecificEpithet() {
    return specificEpithet;
  }

  public void setSpecificEpithet(String newEpithet) {
    if(newEpithet!=null){specificEpithet = newEpithet;}
	  else{specificEpithet=null;}
  }

  public String getTaxonomyString() {
      return Util.taxonomyString(getGenus(), getSpecificEpithet());
  }

    //hacky (as generates new Taxonomy -- with random uuid) but still should work for tax1.equals(tax2);
    // TODO FIXME this should be superceded by the getter for Taxonomy property in the future....
    public Taxonomy getTaxonomy(Shepherd myShepherd) {
        String sciname = this.getTaxonomyString();
        if (sciname == null) return null;
        return myShepherd.getOrCreateTaxonomy(sciname, false); // false means don't commit the taxonomy
    }

    //right now this updates .genus and .specificEpithet ... but in some glorious future we will just store Taxonomy!
    //  note that "null" cases will leave *current values untouched* (does not reset them)
    public void setTaxonomy(Taxonomy tax) {
        if (tax == null) return;
        String[] gs = tax.getGenusSpecificEpithet();
        if ((gs == null) || (gs.length < 1)) return;
        if (gs.length == 1) {
            this.genus = gs[0];
            this.specificEpithet = null;
        } else {
            this.genus = gs[0];
            this.specificEpithet = gs[1];
        }
    }
    public void setTaxonomyFromString(String s) {  //basically scientific name (will get split on space)
        String[] gs = Util.stringToGenusSpecificEpithet(s);
        if ((gs == null) || (gs.length < 1)) return;
        if (gs.length == 1) {
            this.genus = gs[0];
            this.specificEpithet = null;
        } else {
            this.genus = gs[0];
            this.specificEpithet = gs[1];
        }
    }
    public void setTaxonomyFromIAClass(String iaClass, Shepherd myShepherd) {
        setTaxonomy(IBEISIA.iaClassToTaxonomy(iaClass, myShepherd));
    }

  public String getPatterningCode(){ return patterningCode;}
  public void setPatterningCode(String newCode){this.patterningCode=newCode;}


    //crawls thru assets and sets date.. in an ideal world would do some kinda avg or whatever if more than one  TODO?
    public void setDateFromAssets() {
        //FIXME if you dare.  i can *promise you* there are some timezone problems here.  ymmv.
        if ((annotations == null) || (annotations.size() < 1)) return;
        DateTime dt = null;
        for (Annotation ann : annotations) {
            MediaAsset ma = ann.getMediaAsset();
            if (ma == null) continue;
            dt = ma.getDateTime();
            if (dt != null) break;  //we just take the first one
        }
        if (dt != null) setDateInMilliseconds(dt.getMillis());
    }

    //this can(should?) fail in a lot of cases, since we may not know
    public void setSpeciesFromAnnotations() {
        if ((annotations == null) || (annotations.size() < 1)) return;
        String[] sp = null;
        for (Annotation ann : annotations) {
            sp = IBEISIA.convertSpecies(annotations.get(0).getIAClass());
            if (sp != null) break;  //use first one we get
        }
        //note: now we require (exactly) two parts ... please fix this, Taxonomy class!
        if ((sp == null) || (sp.length != 2)) return;
        this.setGenus(sp[0]);
        this.setSpecificEpithet(sp[1]);
    }

    //find the first one(s) we can
    public void setLatLonFromAssets() {
        if ((annotations == null) || (annotations.size() < 1)) return;
        Double lat = null;
        Double lon = null;
        for (Annotation ann : annotations) {
            MediaAsset ma = ann.getMediaAsset();
            if (ma == null) continue;
            if (lat == null) lat = ma.getLatitude();
            if (lon == null) lon = ma.getLongitude();
            if ((lat != null) && (lon != null)) break;
        }
        if (lat != null) this.setDecimalLatitude(lat);
        if (lon != null) this.setDecimalLongitude(lon);
    }

    //sets date to the closes we have to "not set" :)
    public void zeroOutDate() {
        year = 0;
        month = 0;
        day = 0;
        hour = -1;
        minutes = "00";
        resetDateInMilliseconds();  //should set that to null as well
    }

  public void resetDateInMilliseconds(){
    if(year>0){
      int localMonth=0;
      if(month>0){localMonth=month-1;}
      int localDay=1;
      if(day>0){localDay=day;}
      int localHour=0;
      if(hour>-1){localHour=hour;}
      int myMinutes=0;
      try{myMinutes = Integer.parseInt(minutes);}catch(Exception e){}
      GregorianCalendar gc=new GregorianCalendar(year, localMonth, localDay,localHour,myMinutes);

      dateInMilliseconds = new Long(gc.getTimeInMillis());
    }
    else{dateInMilliseconds=null;}
  }

  public java.lang.Long getDateInMilliseconds(){return dateInMilliseconds;}

    // this will set all date stuff based on ms since epoch
    public void setDateInMilliseconds(long ms) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(ms);
        this.year = cal.get(Calendar.YEAR);
        this.month = cal.get(Calendar.MONTH) + 1;
        this.day = cal.get(Calendar.DAY_OF_MONTH);
        this.hour = cal.get(Calendar.HOUR);
        this.minutes = Integer.toString(cal.get(Calendar.MINUTE));
        if (this.minutes.length() == 1) this.minutes = "0" + this.minutes;
        this.dateInMilliseconds = ms;
    }
    
    
  public Long getEndDateInMilliseconds() {
    return endDateInMilliseconds;
  }  
  
  public void setEndDateInMilliseconds(long ms) {
    this.endDateInMilliseconds = ms;
  }
  
  private String milliToMonthDayYear(Long millis) {
    DateTime dt = new DateTime(millis);
    DateTimeFormatter dtf = DateTimeFormat.forPattern("yyyy-MM-dd hh:mm a");
    return dtf.print(dt); 
  }
  
  public String getStartDateTime() {
    return milliToMonthDayYear(dateInMilliseconds);
  }
  
  public String getEndDateTime() {
    return milliToMonthDayYear(endDateInMilliseconds);
  }


  public String getDecimalLatitude(){
    if(decimalLatitude!=null){return Double.toString(decimalLatitude);}
    return null;
  }
  //public void setDecimalLatitude(String lat){this.decimalLatitude=Double.parseDouble(lat);}

  public String getDecimalLongitude(){
    if(decimalLongitude!=null){return Double.toString(decimalLongitude);}
    return null;
  }
  
  public String getEndDecimalLongitude(){
    if(endDecimalLongitude!=null){return Double.toString(endDecimalLongitude);}
    return null;
  }

  public String getEndDecimalLatitude(){
    if(endDecimalLatitude!=null){return Double.toString(endDecimalLatitude);}
    return null;
  }



  public String getSubmitterProject() {
      return submitterProject;
  }
  public void setSubmitterProject(String newProject) {
      if(newProject!=null){submitterProject = newProject;}
  	else{submitterProject=null;}
  }

    public String getSubmitterOrganization() {
        return submitterOrganization;
    }
    public void setSubmitterOrganization(String newOrg) {
        if(newOrg!=null){submitterOrganization = newOrg;}
    	else{submitterOrganization=null;}
    }

	public List<String> getSubmitterResearchers() {
		return submitterResearchers;
	}
	public void addSubmitterResearcher(String researcher) {
		if (submitterResearchers==null) submitterResearchers = new ArrayList<String>();
		submitterResearchers.add(researcher);
	}
	public void setSubmitterResearchers(Collection<String> researchers) {
		if (researchers!=null) this.submitterResearchers = new ArrayList<String>(researchers);
	}

   // public List<DataCollectionEvent> getCollectedData(){return collectedData;}

    /*
    public ArrayList<DataCollectionEvent> getCollectedDataOfType(String type){
      ArrayList<DataCollectionEvent> filteredList=new ArrayList<DataCollectionEvent>();
      int cdSize=collectedData.size();
      System.out.println("cdSize="+cdSize);
      for(int i=0;i<cdSize;i++){
        System.out.println("i="+i);
        DataCollectionEvent tempDCE=collectedData.get(i);
        if(tempDCE.getType().equals(type)){filteredList.add(tempDCE);}
      }
      return filteredList;
    }
    */
    /*
    public <T extends DataCollectionEvent> List<T> getCollectedDataOfClass(Class<T> clazz) {
      List<DataCollectionEvent> collectedData = getCollectedData();
      List<T> result = new ArrayList<T>();
      for (DataCollectionEvent dataCollectionEvent : collectedData) {
        if (dataCollectionEvent.getClass().isAssignableFrom(clazz)) {
          result.add((T) dataCollectionEvent);
        }
      }
      return result;
    }

    public <T extends DataCollectionEvent> List<T> getCollectedDataOfClassAndType(Class<T> clazz, String type) {
      List<T> collectedDataOfClass = getCollectedDataOfClass(clazz);
      List<T> result = new ArrayList<T>();
      for (T t : collectedDataOfClass) {
        if (type.equals(t.getType())) {
          result.add(t);
        }
      }
      return result;
    }

    public void addCollectedDataPoint(DataCollectionEvent dce){
      if(collectedData==null){collectedData=new ArrayList<DataCollectionEvent>();}
      if(!collectedData.contains(dce)){collectedData.add(dce);}
    }
    public void removeCollectedDataPoint(int num){collectedData.remove(num);}
    */

    public void addTissueSample(TissueSample dce){
      if(tissueSamples==null){tissueSamples=new ArrayList<TissueSample>();}
      if(!tissueSamples.contains(dce)){tissueSamples.add(dce);}
      dce.setCorrespondingEncounterNumber(getCatalogNumber());
    }
    public void setTissueSamples(List<TissueSample> samps) {
      this.tissueSamples = samps;
    }
    public void removeTissueSample(int num){tissueSamples.remove(num);}
    public List<TissueSample> getTissueSamples(){return tissueSamples;}
    public Set<String> getTissueSampleIDs(){
      Set<String> ids = new HashSet<String>();
      for (TissueSample ts: tissueSamples) {
        ids.add(ts.getSampleID());
      }
      return ids;
    }

    public void removeTissueSample(TissueSample num){tissueSamples.remove(num);}

    public void addSinglePhotoVideo(SinglePhotoVideo dce){
      if(images==null){images=new ArrayList<SinglePhotoVideo>();}
      if(!images.contains(dce)){images.add(dce);}
    }
    public void removeSinglePhotoVideo(int num){images.remove(num);}
    public List<SinglePhotoVideo> getSinglePhotoVideo(){return images;}
    public void removeSinglePhotoVideo(SinglePhotoVideo num){images.remove(num);}


    public void setMeasurements(List<Measurement> measurements) {
      this.measurements = measurements;
    }
    public void setMeasurement(Measurement measurement, Shepherd myShepherd){

      //if measurements are null, set the empty list
      if(measurements==null){measurements=new ArrayList<Measurement>();}

      //now start checking for existence of a previous measurement

      //if we have it but the new value is null, remove the measurement
      if((this.hasMeasurement(measurement.getType()))&&(measurement.getValue()==null)){
        Measurement m=this.getMeasurement(measurement.getType());
        measurements.remove(m);
        myShepherd.getPM().deletePersistent(m);
        myShepherd.commitDBTransaction();
        myShepherd.beginDBTransaction();
      }

      //just add the measurement it if we did not have it before
      else if(!this.hasMeasurement(measurement.getType())){
        measurements.add(measurement);
        myShepherd.commitDBTransaction();
        myShepherd.beginDBTransaction();
      }

      //if we had it before then just update the value
      else if((this.hasMeasurement(measurement.getType()))&&(measurement!=null)){
        Measurement m=this.getMeasurement(measurement.getType());
        m.setValue(measurement.getValue());
        m.setSamplingProtocol(measurement.getSamplingProtocol());
        myShepherd.commitDBTransaction();
        myShepherd.beginDBTransaction();
      }

    }
    public void removeMeasurement(int num){measurements.remove(num);}
    public List<Measurement> getMeasurements(){return measurements;}
    public void removeMeasurement(Measurement num){measurements.remove(num);}
    public Measurement findMeasurementOfType(String type) {
      List<Measurement> measurements = getMeasurements();
      if (measurements != null) {
        for (Measurement measurement : measurements) {
          if (type.equals(measurement.getType())) {
            return measurement;
          }
        }
      }
      return null;
    }

    public void addMetalTag(MetalTag metalTag) {
      if (metalTags == null) {
        metalTags = new ArrayList<MetalTag>();
      }
      metalTags.add(metalTag);
    }

    public void removeMetalTag(MetalTag metalTag) {
      metalTags.remove(metalTag);
    }
    public void setMetalTags(List<MetalTag> metalTags) {
      this.metalTags = metalTags;
    }
    public List<MetalTag> getMetalTags() {
      return metalTags;
    }

    public MetalTag findMetalTagForLocation(String location) {
      List<MetalTag> metalTags = getMetalTags();
      if (metalTags != null) {
        for (MetalTag metalTag : metalTags) {
          if (location.equals(metalTag.getLocation())) {
            return metalTag;
          }
        }
      }
      return null;
    }

    public AcousticTag getAcousticTag() {
      return acousticTag;
    }

    public void setAcousticTag(AcousticTag acousticTag) {
      this.acousticTag = acousticTag;
    }

    public SatelliteTag getSatelliteTag() {
      return satelliteTag;
    }

    public void setSatelliteTag(SatelliteTag satelliteTag) {
      this.satelliteTag = satelliteTag;
    }
    
    public DigitalArchiveTag getDTag() {
      return digitalArchiveTag;
    }

    public void setDTag(DigitalArchiveTag dt) {
      this.digitalArchiveTag = dt;
    }

    public String getLifeStage(){return lifeStage;}
    public void setLifeStage(String newStage) {
      if(newStage!=null){lifeStage = newStage;}
      else{lifeStage=null;}
    }


    /**
     * A convenience method that returns the first haplotype found in the TissueSamples for this Encounter.
     *
     *@return a String if found or null if no haplotype is found
     */
    public String getHaplotype(){
      //List<TissueSample> tissueSamples=getCollectedDataOfClass(TissueSample.class);
      int numTissueSamples=tissueSamples.size();
      if(numTissueSamples>0){
        for(int j=0;j<numTissueSamples;j++){
          TissueSample thisSample=tissueSamples.get(j);
          int numAnalyses=thisSample.getNumAnalyses();
          if(numAnalyses>0){
            List<GeneticAnalysis> gAnalyses = thisSample.getGeneticAnalyses();
            for(int g=0;g<numAnalyses;g++){
              GeneticAnalysis ga = gAnalyses.get(g);
              if(ga.getAnalysisType().equals("MitochondrialDNA")){
                MitochondrialDNAAnalysis mito=(MitochondrialDNAAnalysis)ga;
                if(mito.getHaplotype()!=null){return mito.getHaplotype();}
              }
            }
          }
        }
      }
      return null;
    }

    /**
     * A convenience method that returns the first genetic sex found in the TissueSamples for this Encounter.
     *
     *@return a String if found or null if no genetic sex is found
     */
    public String getGeneticSex(){
      if(tissueSamples!=null){
      int numTissueSamples=tissueSamples.size();
      if(numTissueSamples>0){
        for(int j=0;j<numTissueSamples;j++){
          TissueSample thisSample=tissueSamples.get(j);
          int numAnalyses=thisSample.getNumAnalyses();
          if(numAnalyses>0){
            List<GeneticAnalysis> gAnalyses = thisSample.getGeneticAnalyses();
            for(int g=0;g<numAnalyses;g++){
              GeneticAnalysis ga = gAnalyses.get(g);
              if(ga.getAnalysisType().equals("SexAnalysis")){
                SexAnalysis mito=(SexAnalysis)ga;
                if(mito.getSex()!=null){return mito.getSex();}
              }
            }
          }
        }
      }
      }
      return null;
    }

    public List<SinglePhotoVideo> getImages(){return images;}

    public boolean hasAnnotation(Annotation ann) {
      return (annotations!=null && annotations.contains(ann));
    }
    public boolean hasAnnotations() {
        return (annotations!=null && annotations.size()>0);
    }
    public int numAnnotations() {
      if (annotations==null) return 0;
      return annotations.size();
    }
    public ArrayList<Annotation> getAnnotations() {
        return annotations;
    }
    public void setAnnotations(ArrayList<Annotation> anns) {
        annotations = anns;
    }
    public void addAnnotations(List<Annotation> anns) {
      if (annotations == null) annotations = new ArrayList<Annotation>();
      for (Annotation ann: anns) {
        annotations.add(ann);
      }
    }
    public void addAnnotation(Annotation ann) {
        if (annotations == null) annotations = new ArrayList<Annotation>();
        annotations.add(ann);
    }

    public void useAnnotationsForMatching(boolean use) {
      if (getAnnotations()!=null&&getAnnotations().size()>=1) {
        for (Annotation ann : getAnnotations()) {
          ann.setMatchAgainst(use);
        }
      }
    }

/*  officially deprecating this (until needed?) ... work now being done with replaceAnnotation() basically   -jon
    public void addAnnotationReplacingUnityFeature(Annotation ann) {
        int unityAnnotIndex = -1;
        if (annotations == null) annotations = new ArrayList<Annotation>();
        System.out.println("n annotations = "+annotations.size());

        for (int i=0; i<annotations.size(); i++) {
          if (annotations.get(i).isTrivial()) {
            System.out.println("annotation "+i+" is unity!");
            unityAnnotIndex = i;
            break;
          }
        }
        System.out.println("unityAnnotIndex = "+unityAnnotIndex);
        if (unityAnnotIndex > -1) { // there is a unity annot; replace it
          annotations.set(unityAnnotIndex, ann);
        } else {
          annotations.add(ann);
        }
    }
*/

    public Annotation getAnnotationWithKeyword(String word) {
        System.out.println("getAnnotationWithKeyword called for "+word);
        System.out.println("getAnnotationWithKeyword called, annotations = "+annotations);
        if (annotations == null) return null;
        for (Annotation ann : annotations) {
          if (ann==null) continue;
          MediaAsset ma = ann.getMediaAsset();
          if (ma!=null && ma.hasKeyword(word)) return ann;
        }
        return null;
    }


    //pretty much only useful for frames pulled from video (after detection, to be made into encounters)
    public static List<Encounter> collateFrameAnnotations(List<Annotation> anns, Shepherd myShepherd) {
        if ((anns == null) || (anns.size() < 1)) return null;
          
        //Determine skipped frames before another encounter should be made. 
      int minGapSize = 4;  
      try {
        String gapFromProperties = IA.getProperty(myShepherd.getContext(), "newEncounterFrameGap");
        if (gapFromProperties!=null) {
          minGapSize = Integer.parseInt(gapFromProperties);
        }
      } catch (NumberFormatException nfe) {}

        SortedMap<Integer,List<Annotation>> ordered = new TreeMap<Integer,List<Annotation>>();
        MediaAsset parentRoot = null;
        for (Annotation ann : anns) {
System.out.println("========================== >>>>>> " + ann);
            if (ann.getFeatures().get(0).isUnity()) continue; //makes big assumption there is one-and-only-one feature btw (detection should have bbox)
            MediaAsset ma = ann.getMediaAsset();
            if (parentRoot == null) parentRoot = ma.getParentRoot(myShepherd);
System.out.println("   -->>> ma = " + ma);
            if (!ma.hasLabel("_frame") || (ma.getParentId() == null)) continue;  //nope thx
            int offset = ma.getParameters().optInt("extractOffset", -1);
System.out.println("   -->>> offset = " + offset);
            if (offset < 0) continue;
            if (ordered.get(offset) == null) ordered.put(offset, new ArrayList<Annotation>());
            ordered.get(offset).add(ann);
        }
        if (ordered.size() < 1) return null;  //none used!

        //now construct Encounters based upon spacing of frame-clusters
        List<Encounter> newEncs = new ArrayList<Encounter>();
        int prevOffset = -1;
        int groupsMade = 1;
        ArrayList<Annotation> tmpAnns = new ArrayList<Annotation>();
        for (Integer i : ordered.keySet()) {
            if ((prevOffset > -1) && ((i - prevOffset) >= minGapSize)) {
                Encounter newEnc = __encForCollate(tmpAnns, parentRoot);
                if (newEnc != null) {  //null means none of the frames met minimum detection confidence
                    newEnc.setDynamicProperty("frameSplitNumber", Integer.toString(groupsMade + 1));
                    newEncs.add(newEnc);
System.out.println(" cluster [" + (groupsMade) + "] -> " + newEnc);
                    groupsMade++;
                    tmpAnns = new ArrayList<Annotation>();
                }
            }
            prevOffset = i;
            tmpAnns.addAll(ordered.get(i));
        }
        //deal with dangling tmpAnns content
        if (tmpAnns.size() > 0) {
            Encounter newEnc = __encForCollate(tmpAnns, parentRoot);
            if (newEnc != null) {
                newEnc.setDynamicProperty("frameSplitNumber", Integer.toString(groupsMade + 1));
                //newEnc.setDynamicProperty("frameSplitSourceEncounter", this.getCatalogNumber());
                newEncs.add(newEnc);
System.out.println(" (final)cluster [" + groupsMade + "] -> " + newEnc);
                groupsMade++;
            }
        }
        return newEncs;
    }

    //this is really only for above method
    private static Encounter __encForCollate(ArrayList<Annotation> tmpAnns, MediaAsset parentRoot) {
        if ((tmpAnns == null) || (tmpAnns.size() < 1)) return null;

        //make sure we even can use these annots first
        double bestConfidence = 0.0;
        for (Annotation ann : tmpAnns) {
            if ((ann.getFeatures() == null) || (ann.getFeatures().size() < 1) || (ann.getFeatures().get(0).getParameters() == null)) continue;
            double conf = ann.getFeatures().get(0).getParameters().optDouble("detectionConfidence", -1.0);
            if (conf > bestConfidence) bestConfidence = conf;
        }
        if (bestConfidence < ENCOUNTER_AUTO_SOURCE_CONFIDENCE_CUTOFF) {
            System.out.println("[INFO] bestConfidence=" + bestConfidence + " below threshold; rejecting 1 enc from " + parentRoot);
            return null;
        }

        Encounter newEnc = new Encounter(tmpAnns);
        newEnc.setState(STATE_AUTO_SOURCED);
        newEnc.zeroOutDate();  //do *not* want it using the video source date
        newEnc.setDynamicProperty("bestDetectionConfidence", Double.toString(bestConfidence));
        if (parentRoot == null) {
            newEnc.setSubmitterName("Unknown video source");
            newEnc.addComments("<i>unable to determine video source - possibly YouTube error?</i>");
        } else {
            newEnc.addComments("<p>YouTube ID: <b>" + parentRoot.getParameters().optString("id") + "</b></p>");
            String consolidatedRemarks="<p>Auto-sourced from YouTube Parent Video: <a href=\"https://www.youtube.com/watch?v="+parentRoot.getParameters().optString("id")+"\">"+parentRoot.getParameters().optString("id")+"</a></p>";
            //set the video ID as the EventID for distinct access later
            newEnc.setEventID("youtube:"+parentRoot.getParameters().optString("id"));
            if ((parentRoot.getMetadata() != null) && (parentRoot.getMetadata().getData() != null)) {
                
                if (parentRoot.getMetadata().getData().optJSONObject("basic") != null) {
                    newEnc.setSubmitterName(parentRoot.getMetadata().getData().getJSONObject("basic").optString("author_name", "[unknown]") + " (by way of YouTube)");
                    consolidatedRemarks+="<p>From YouTube video: <i>" + parentRoot.getMetadata().getData().getJSONObject("basic").optString("title", "[unknown]") + "</i></p>";
                    newEnc.addComments(consolidatedRemarks);
                    
                    //add a dynamic property to make a quick link to the video
                }
                if (parentRoot.getMetadata().getData().optJSONObject("detailed") != null) {
                    String desc = "<p>" + parentRoot.getMetadata().getData().getJSONObject("detailed").optString("description", "[no description]") + "</p>";
                    if (parentRoot.getMetadata().getData().getJSONObject("detailed").optJSONArray("tags") != null) {
                        desc += "<p><b>tags:</b> " + parentRoot.getMetadata().getData().getJSONObject("detailed").getJSONArray("tags").toString() + "</p>";
                    }
                    consolidatedRemarks+=desc;
                    
                }
            }
            newEnc.setOccurrenceRemarks(consolidatedRemarks);
        }
        return newEnc;
    }


    //convenience method
    public ArrayList<MediaAsset> getMedia() {
        ArrayList<MediaAsset> m = new ArrayList<MediaAsset>();
        if ((annotations == null) || (annotations.size() < 1)) return m;
        for (Annotation ann : annotations) {
            if (ann==null) continue; // really weird that this happens sometimes
            MediaAsset ma = ann.getMediaAsset();
            if (ma != null) m.add(ma);
        }
        return m;
    }

    public MediaAsset getMediaAssetByFilename(String filename) {
      if (!Util.stringExists(filename)) return null;
      for (MediaAsset ma: getMedia()) {
        if (Util.stringsEqualish(filename, ma.getFilename())) return ma;
      }
      return null;
    }


    // only checks top-level MediaAssets, not children or resized images
    public boolean hasTopLevelMediaAsset(int id) {
      return (indexOfMediaAsset(id)>=0);
    }

    // finds the index of the MA we're looking for
    public int indexOfMediaAsset(int id) {
      if (annotations == null) return -1;
      for (int i=0; i < annotations.size(); i++) {
        MediaAsset ma = annotations.get(i).getMediaAsset();
        if (ma == null) continue;
        if (ma.getId() == id) return i;
      }
      return -1;
    }

    // creates a new annotation and attaches the asset
    public void addMediaAsset(MediaAsset ma) {
      Annotation ann = new Annotation(getTaxonomyString(), ma);
      annotations.add(ann);
    }

    public void removeAnnotation(Annotation ann) {
        if (annotations == null) return;
        annotations.remove(ann);
    }

    public void removeAnnotation(int index) {
      annotations.remove(index);
    }

    //this removes an Annotation from Encounter (and from its MediaAsset!!) and replaces it with a new one
    // please note: the oldAnn gets killed off (not orphaned)
    public void replaceAnnotation(Annotation oldAnn, Annotation newAnn) {
        oldAnn.detachFromMediaAsset();
        //note: newAnn should already attached to a MediaAsset
        removeAnnotation(oldAnn);
        addAnnotation(newAnn);
    }


    public void removeMediaAsset(MediaAsset ma) {
      removeAnnotation(indexOfMediaAsset(ma.getId()));
    }

    //this is a kinda hacky way to find media ... really used by encounter.jsp now but likely should go away?
    public ArrayList<MediaAsset> findAllMediaByFeatureId(Shepherd myShepherd, String[] featureIds) {
        ArrayList<MediaAsset> mas = new ArrayList<MediaAsset>();
        for (MediaAsset ma : getMedia()) {
            if (ma.hasFeatures(featureIds)) mas.add(ma);
            ArrayList<MediaAsset> kids = ma.findChildren(myShepherd); //note: does not recurse, but... meh?
            if ((kids == null) || (kids.size() < 1)) continue;
            for (MediaAsset kma : kids) {
                if (kma.hasFeatures(featureIds)) mas.add(kma);
            }
        }
        return mas;
    }

    //down-n-dirty with no myShepherd passed!  :/
    public ArrayList<MediaAsset> findAllMediaByFeatureId(String[] featureIds) {
        Shepherd myShepherd = new Shepherd("context0");
        myShepherd.setAction("Encounter.class.findAllMediaByFeatureID");  
        myShepherd.beginDBTransaction();
        ArrayList<MediaAsset> all = findAllMediaByFeatureId(myShepherd, featureIds);
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
        return all;
    }

    public ArrayList<MediaAsset> findAllMediaByLabel(Shepherd myShepherd, String label) {
        return MediaAsset.findAllByLabel(getMedia(), myShepherd, label);
    }

/*
    public MediaAsset findOneMediaByLabel(Shepherd myShepherd, String label) {
        return MediaAsset.findOneByLabel(media, myShepherd, label);
    }
*/

    public boolean hasKeyword(Keyword word){
     int imagesSize=images.size();
     for(int i=0;i<imagesSize;i++){
       SinglePhotoVideo image=images.get(i);
       if(image.getKeywords().contains(word)){return true;}
     }
     return false;
    }

    public String getState(){return state;}

    public void setState(String newState){this.state=newState;}

    //DO NOT USE - LEGACY MIGRATION ONLY
   /*
    public boolean getApproved(){return approved;}
    public boolean getUnidentifiable(){return unidentifiable;}
    */


    public Vector getOldAdditionalImageNames(){return additionalImageNames;}

    public Double getLatitudeAsDouble(){return decimalLatitude;}
    public Double getLongitudeAsDouble(){return decimalLongitude;}

    public boolean hasMeasurements(){
      if((measurements!=null)&&(measurements.size()>0)){
        int numMeasurements=measurements.size();
        for(int i=0;i<numMeasurements;i++){
          Measurement m=measurements.get(i);
          if(m.getValue()!=null){return true;}
        }
      }
      return false;
    }

    public boolean hasMeasurement(String type){
      if((measurements!=null)&&(measurements.size()>0)){
        int numMeasurements=measurements.size();
        for(int i=0;i<numMeasurements;i++){
          Measurement m=measurements.get(i);
          if((m.getValue()!=null)&&(m.getType().equals(type))){return true;}
        }
      }
      return false;
    }

    public boolean hasBiologicalMeasurement(String type){
      if((tissueSamples!=null)&&(tissueSamples.size()>0)){
        int numTissueSamples=tissueSamples.size();
        for(int i=0;i<numTissueSamples;i++){
          TissueSample ts=tissueSamples.get(i);
          if(ts.getBiologicalMeasurement(type)!=null){
            BiologicalMeasurement bm=ts.getBiologicalMeasurement(type);
            if(bm.getValue()!=null){return true;}
          }
        }
      }
      return false;
    }



    /**
     * Returns the first measurement of the specified type
     * @param type
     * @return
     */
    public Measurement getMeasurement(String type){
      if((measurements!=null)&&(measurements.size()>0)){
        int numMeasurements=measurements.size();
        for(int i=0;i<numMeasurements;i++){
          Measurement m=measurements.get(i);
          if((m.getValue()!=null)&&(m.getType().equals(type))){return m;}
        }
      }
      return null;
    }

    public BiologicalMeasurement getBiologicalMeasurement(String type){

      if(tissueSamples!=null){int numTissueSamples=tissueSamples.size();
      for(int y=0;y<numTissueSamples;y++){
        TissueSample ts=tissueSamples.get(y);
        if((ts.getGeneticAnalyses()!=null)&&(ts.getGeneticAnalyses().size()>0)){
          int numMeasurements=ts.getGeneticAnalyses().size();
          for(int i=0;i<numMeasurements;i++){
            GeneticAnalysis m=ts.getGeneticAnalyses().get(i);
            if(m.getAnalysisType().equals("BiologicalMeasurement")){
              BiologicalMeasurement f=(BiologicalMeasurement)m;
              if((f.getMeasurementType().equals(type))&&(f.getValue()!=null)){return f;}
            }
          }
        }
      }
      }

      return null;
    }

    public String getCountry(){return country;}

    public void setCountry(String newCountry) {
      if(newCountry!=null){country = newCountry;}
      else{country=null;}
    }

    public void setOccurrenceID(String vet) {
      if(vet!=null){this.occurrenceID = vet;}
      else{this.occurrenceID=null;}
  }

    public String getOccurrenceID(){return occurrenceID;}

    public boolean hasSinglePhotoVideoByFileName(String filename){
        int numImages=images.size();
        for(int i=0;i<numImages;i++){
          SinglePhotoVideo single=images.get(i);
          if(single.getFilename().trim().toLowerCase().equals(filename.trim().toLowerCase())){return true;}
        }
        return false;
    }


	//convenience function to Collaboration permissions
	public boolean canUserAccess(HttpServletRequest request) {
		return Collaboration.canUserAccessEncounter(this, request);
	}
        public boolean canUserEdit(User user) {
            return isUserOwner(user);
        }
        public boolean isUserOwner(User user) {  //the definition of this might change?
            if ((user == null) || (submitters == null)) return false;
            return submitters.contains(user);
        }

	public JSONObject sanitizeJson(HttpServletRequest request, JSONObject jobj) throws JSONException {
            jobj.put("location", this.getLocation());
            if(individual!=null){jobj.put("individualID", this.getIndividualID());}
            boolean fullAccess = this.canUserAccess(request);

            //these are for convenience, like .hasImages above (for use in table building e.g.)
            if ((this.getTissueSamples() != null) && (this.getTissueSamples().size() > 0)) jobj.put("hasTissueSamples", true);
            if (this.hasMeasurements()) jobj.put("hasMeasurements", true);
/*
            String context="context0";
            context = ServletUtilities.getContext(request);
            Shepherd myShepherd = new Shepherd(context);
            if ((myShepherd.getAllTissueSamplesForEncounter(this.getCatalogNumber())!=null) && (myShepherd.getAllTissueSamplesForEncounter(this.getCatalogNumber()).size()>0)) jobj.put("hasTissueSamples", true);
            if ((myShepherd.getMeasurementsForEncounter(this.getCatalogNumber())!=null) && (myShepherd.getMeasurementsForEncounter(this.getCatalogNumber()).size()>0)) jobj.put("hasMeasurements", true);
*/

            jobj.put("_imagesNote", ".images have been deprecated!  long live MediaAssets!  (see: .annotations)");
            //jobj.remove("images");  //TODO uncomment after debugging
/*
            if ((this.getImages() != null) && (this.getImages().size() > 0)) {
                jobj.put("hasImages", true);
                JSONArray jarr = new JSONArray();
                for (SinglePhotoVideo spv : this.getImages()) {
                    jarr.put(spv.sanitizeJson(request, fullAccess));
                }
                jobj.put("images", jarr);
            }
*/
            if ((this.getAnnotations() != null) && (this.getAnnotations().size() > 0)) {
                jobj.put("hasAnnotations", true);
                JSONArray jarr = new JSONArray();
                for (Annotation ann : this.getAnnotations()) {
                    jarr.put(ann.sanitizeJson(request, fullAccess));
                }
                jobj.put("annotations", jarr);
            }

            if (this.individual!=null) jobj.put("displayName",getDisplayName());

            if (fullAccess) return jobj;

            jobj.remove("gpsLatitude");
            jobj.remove("location");
            jobj.remove("gpsLongitude");
            jobj.remove("verbatimLocality");
            jobj.remove("locationID");
            jobj.remove("gpsLongitude");
            jobj.remove("genus");
            jobj.remove("specificEpithet");
            jobj.put("_sanitized", true);

            return jobj;
        }

        // this doesn't add any fields, and only removes fields that shouldn't be there
        public JSONObject sanitizeJsonNoAnnots(HttpServletRequest request, JSONObject jobj) throws JSONException {

            boolean fullAccess = this.canUserAccess(request);
            if (fullAccess) return jobj;

            jobj.remove("gpsLatitude");
            jobj.remove("location");
            jobj.remove("gpsLongitude");
            jobj.remove("verbatimLocality");
            jobj.remove("locationID");
            jobj.remove("gpsLongitude");
            jobj.remove("genus");
            jobj.remove("specificEpithet");
            jobj.put("_sanitized", true);

            return jobj;
        }


        public JSONObject uiJson(HttpServletRequest request) throws JSONException {
          JSONObject jobj = new JSONObject();
          jobj.put("individualID", this.getIndividualID());
          jobj.put("url", this.getUrl(request));
          jobj.put("year", this.getYear());
          jobj.put("month", this.getMonth());
          jobj.put("day", this.getDay());
          jobj.put("gpsLatitude", this.getGPSLatitude());
          jobj.put("gpsLongitude", this.getGPSLongitude());
          jobj.put("location", this.getLocation());
          jobj.put("locationID", this.getLocationID());

          jobj = sanitizeJson(request, jobj);
          // we don't want annotations, which are added by sanitizeJson
          jobj.remove("annotations");
          return jobj;
        }

        public String getUrl(HttpServletRequest request) {
          return request.getScheme()+"://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" + this.getCatalogNumber();
        }

        /**
        * returns an array of the MediaAsset sanitized JSON, because whenever UI queries our DB (regardless of class query),
        * all they want in return are MediaAssets
        * TODO: decorate with metadata
        **/

        public org.datanucleus.api.rest.orgjson.JSONArray sanitizeMedia(HttpServletRequest request) throws org.datanucleus.api.rest.orgjson.JSONException {

          org.datanucleus.api.rest.orgjson.JSONArray jarr = new org.datanucleus.api.rest.orgjson.JSONArray();
          boolean fullAccess = this.canUserAccess(request);

          if ((this.getAnnotations() != null) && (this.getAnnotations().size() > 0)) {
              for (Annotation ann : this.getAnnotations()) {
                  jarr.put(ann.sanitizeMedia(request, fullAccess));
              }
          }
          return jarr;

        }



	//this simple version makes some assumptions: you already have list of collabs, and it is not visible
	public String collaborationLockHtml(List<Collaboration> collabs) {
		Collaboration c = Collaboration.findCollaborationWithUser(this.getAssignedUsername(), collabs);
		String collabClass = "pending";
		if ((c == null) || (c.getState() == null)) {
			collabClass = "new";
		} else if (c.getState().equals(Collaboration.STATE_REJECTED)) {
			collabClass = "blocked";
		}
		return "<div class=\"row-lock " + collabClass + " collaboration-button\" data-collabowner=\"" + this.getAssignedUsername() + "\" data-collabownername=\"" + this.getAssignedUsername() + "\">&nbsp;</div>";
	}


	//pass in a Vector of Encounters, get out a list that the user can NOT see
	public static Vector blocked(Vector encs, HttpServletRequest request) {
		Vector blk = new Vector();
		for (int i = 0; i < encs.size() ; i++) {
			Encounter e = (Encounter) encs.get(i);
			if (!e.canUserAccess(request)) blk.add(e);
		}
		return blk;
	}

/*
in short, this rebuilds (or builds for the first time) ALL *derived* images (etc?) for this encounter.
it is a baby step into the future of MediaAssets that hopefully will provide a smooth(er) transition to that.
right now its primary purpose is to create derived formats upon encounter creation; but that is obviously subject to change.
it should be considered an asyncronous action that happens in the background magickally
*/
/////other possiblity: only pass basedir??? do we need context if we do that?

                public boolean refreshAssetFormats(Shepherd myShepherd) {
                    ArrayList<MediaAsset> mas = this.getMedia();
                    if ((mas == null) || (mas.size() < 1)) return true;
                    for (MediaAsset ma : mas) {
                        ma.updateStandardChildren(myShepherd);
                    }
                    return true;
                }
/*
NOTE on "thumb.jpg" ... we only get one of these per encounter; and we do not have stored (i dont think?) which SPV it came from!
this is a problem, as we cant make a thumb in refreshAssetFormats(req, spv) since we dont know if that is the "right" spv.
thus, we have to treat it as a special case.
*/
/*
		public boolean refreshAssetFormats(String context, String baseDir) {
			boolean ok = true;
			//List<SinglePhotoVideo> allSPV = this.getImages();
			boolean thumb = true;
			for (SinglePhotoVideo spv : this.getImages()) {
				ok &= this.refreshAssetFormats(context, baseDir, spv, thumb);
				thumb = false;
			}
			return ok;
		}

		//as above, but for specific SinglePhotoVideo
		public boolean refreshAssetFormats(String context, String baseDir, SinglePhotoVideo spv, boolean doThumb) {
			if (spv == null) return false;
			String encDir = this.dir(baseDir);

			boolean ok = true;
			if (doThumb) ok &= spv.scaleTo(context, 100, 75, encDir + File.separator + "thumb.jpg");
			//TODO some day this will be a structure/definition that lives in a config file or on MediaAsset, etc.  for now, ya get hard-coded

			//this will first try watermark version, then regular
			ok &= (spv.scaleToWatermark(context, 250, 200, encDir + File.separator + spv.getDataCollectionEventID() + ".jpg", "") || spv.scaleTo(context, 250, 200, encDir + File.separator + spv.getDataCollectionEventID() + ".jpg"));

			ok &= spv.scaleTo(context, 1024, 768, encDir + File.separator + spv.getDataCollectionEventID() + "-mid.jpg");  //for use in VM tool etc. (bandwidth friendly?)
			return ok;
		}


*/
	//see also: future, MediaAssets
	public String getThumbnailUrl(String context) {
                MediaAsset ma = getPrimaryMediaAsset();
                if (ma == null) return null;
                String url = null;
                Shepherd myShepherd = new Shepherd(context);
                myShepherd.setAction("Encounter.class.getThumbnailUrl");
                myShepherd.beginDBTransaction();
                ArrayList<MediaAsset> kids = ma.findChildrenByLabel(myShepherd, "_thumb");
                if ((kids == null) || (kids.size() <= 0)) {
                  myShepherd.rollbackDBTransaction();
                  myShepherd.closeDBTransaction();
                  return null;
                }
                ma = kids.get(0);
                if (ma.webURL() == null) {
                  myShepherd.rollbackDBTransaction();
                  myShepherd.closeDBTransaction();
                  return null;
                }
                url = ma.webURL().toString();
                myShepherd.rollbackDBTransaction();
                myShepherd.closeDBTransaction();
                return url;
	}

        //this probably needs a better name and should allow for something more like an ordered list; that said,
        //  knowing we can always try to get THE ONE is probably useful too
        public MediaAsset getPrimaryMediaAsset() {
            ArrayList<MediaAsset> mas = getMedia();
            if (mas.size() < 1) return null;
            //here we could walk thru and find keywords, for example
            return mas.get(0);
        }

	public boolean restAccess(HttpServletRequest request, org.json.JSONObject jsonobj) throws Exception {
		ApiAccess access = new ApiAccess();
System.out.println("hello i am in restAccess() on Encounter");

		String fail = access.checkRequest(this, request, jsonobj);
System.out.println("fail -----> " + fail);
		if (fail != null) throw new Exception(fail);

		//HashMap<String, String> perm = access.permissions(this, request);
//System.out.println(perm);

/*
System.out.println("!!!----------------------------------------");
System.out.println(request.getMethod());
throw new Exception();
*/
		return true;
	}

///////// these are bunk now - dont use Features  TODO fix these - perhaps by crawlng thru ma.getAnnotations() ?
        public static Encounter findByMediaAsset(MediaAsset ma, Shepherd myShepherd) {
            String queryString = "SELECT FROM org.ecocean.Encounter WHERE annotations.contains(ann) && ann.mediaAsset.id ==" + ma.getId();
            Encounter returnEnc=null;
            Query query = myShepherd.getPM().newQuery(queryString);
            List results = (List)query.execute();
            if ((results!=null)&&(results.size() >=1)){
              returnEnc=(Encounter)results.get(0);
            }
            query.closeAll();
            return returnEnc;
        }

        public static List<Encounter> findAllByMediaAsset(MediaAsset ma, Shepherd myShepherd) {
            List<Encounter> returnEncs = new ArrayList<Encounter>();
            try {
                String queryString = "SELECT FROM org.ecocean.Encounter WHERE annotations.contains(ann) && ann.mediaAsset.id ==" + ma.getId();
                //String queryString = "SELECT FROM org.ecocean.Encounter WHERE annotations.contains(ann) && ann.features.contains(mAsset) && mAsset.id ==" + ma.getId();
                Query query = myShepherd.getPM().newQuery(queryString);
                Collection results = (Collection) query.execute();
                returnEncs = new ArrayList<Encounter>(results);
                query.closeAll();
            }
            catch (Exception e) {

            }
            return returnEncs;
        }


        public static Encounter findByAnnotation(Annotation annot, Shepherd myShepherd) {
            String queryString = "SELECT FROM org.ecocean.Encounter WHERE annotations.contains(ann) && ann.id =='" + annot.getId() + "'";
            Encounter returnEnc=null;
            Query query = myShepherd.getPM().newQuery(queryString);
            List results = (List)query.execute();
            if ((results!=null) && (results.size() >= 1)) {
                if (results.size() > 1) System.out.println("WARNING: Encounter.findByAnnotation() found " + results.size() + " Encounters that contain Annotation " + annot.getId());
                returnEnc = (Encounter)results.get(0);
            }
            query.closeAll();
            return returnEnc;
        }

        public static Encounter findByAnnotationId(String annid, Shepherd myShepherd) {
            Annotation ann = ((Annotation) (myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(Annotation.class, annid), true)));
            if (ann == null) return null;
            return findByAnnotation(ann, myShepherd);
        }


/*  not really sure we need this now/yet

	public void refreshDependentProperties() {
		this.resetDateInMilliseconds();
//TODO could possibly do integrity check, re: individuals/occurrences linking?
	}

*/

    public static ArrayList<Encounter> getEncountersForMatching(String taxonomyString, Shepherd myShepherd) {
        if (_matchEncounterCache.get(taxonomyString) != null) return _matchEncounterCache.get(taxonomyString);
        ArrayList<Encounter> encs = new ArrayList<Encounter>();
        String queryString = "SELECT FROM org.ecocean.media.MediaAsset WHERE !features.isEmpty()";
        Query query = myShepherd.getPM().newQuery(queryString);
        List results = (List)query.execute();
        for (int i = 0 ; i < results.size() ; i++) {
            MediaAsset ma = (MediaAsset)results.get(i);
            MediaAsset top = ma.getParentRoot(myShepherd);
            if (top == null) continue;
            Encounter enc = Encounter.findByMediaAsset(top, myShepherd);
            if (enc == null) System.out.println("could not find enc for ma " + ma);
            if (enc == null) continue;
            if (!enc.getTaxonomyString().equals(taxonomyString)) continue;
            if (!encs.contains(enc)) encs.add(enc);
        }
        query.closeAll();
        _matchEncounterCache.put(taxonomyString, encs);
        return encs;
    }


/*
    this section are intentionally hacky backwards-compatible ways to get spots on an encounter in the new world of Features/Annotations/MediaAssets ... do not use
    these, of course... and SOON we must weed out all the encounter-based-spot calls from everywhere and clean all this mess up!
*/

    public ArrayList<SuperSpot> HACKgetSpots() {
        return HACKgetAnySpots("spotsLeft");
    }
    public ArrayList<SuperSpot> HACKgetRightSpots() {
        return HACKgetAnySpots("spotsRight");
    }
    public ArrayList<SuperSpot> HACKgetAnySpots(String which) {
/*
        RuntimeException ex = new RuntimeException(" ===== DEPRECATED ENCOUNTER SPOT BEHAVIOR! PLEASE FIX =====");
        System.out.println(ex.toString());
        ex.printStackTrace();
*/
        ArrayList<MediaAsset> mas = findAllMediaByFeatureId(new String[]{"org.ecocean.flukeEdge.edgeSpots", "org.ecocean.dorsalEdge.edgeSpots"});
        if ((mas == null) || (mas.size() < 1)) return new ArrayList<SuperSpot>();
        for (Feature f : mas.get(0).getFeatures()) {
            if (f.isType("org.ecocean.flukeEdge.edgeSpots") || f.isType("org.ecocean.dorsalEdge.edgeSpots")) {
                if (f.getParameters() != null) return SuperSpot.listFromJSONArray(f.getParameters().optJSONArray(which));
            }
        }
        return new ArrayList<SuperSpot>();
    }

    //err, i think ref spots are the same right or left.... at least for flukes/dorsals.  :/  good luck with mantas and whalesharks!
    public ArrayList<SuperSpot> HACKgetAnyReferenceSpots() {
/*
        RuntimeException ex = new RuntimeException(" ===== DEPRECATED ENCOUNTER SPOT BEHAVIOR! PLEASE FIX =====");
        System.out.println(ex.toString());
        ex.printStackTrace();
*/
        ArrayList<MediaAsset> mas = findAllMediaByFeatureId(new String[]{"org.ecocean.flukeEdge.referenceSpots", "org.ecocean.referenceEdge.edgeSpots"});
        if ((mas == null) || (mas.size() < 1)) return new ArrayList<SuperSpot>();
        for (Feature f : mas.get(0).getFeatures()) {
            if (f.isType("org.ecocean.flukeEdge.referenceSpots") || f.isType("org.ecocean.dorsalEdge.referenceSpots")) {
                if (f.getParameters() != null) return SuperSpot.listFromJSONArray(f.getParameters().optJSONArray("spots"));
            }
        }
        return new ArrayList<SuperSpot>();
    }


    //note this sets some things (e.g. species) which might (should!) need to be adjusted after, e.g. with setSpeciesFromAnnotations()
    public Encounter cloneWithoutAnnotations() {
        Encounter enc = new Encounter(this.day, this.month, this.year, this.hour, this.minutes, this.size_guess, this.verbatimLocality);
        enc.setCatalogNumber(Util.generateUUID());
        System.out.println("NOTE: cloneWithoutAnnotations(" + this.catalogNumber + ") -> " + enc.getCatalogNumber());
        enc.setGenus(this.getGenus());
        enc.setSpecificEpithet(this.getSpecificEpithet());
        enc.setDecimalLatitude(this.getDecimalLatitudeAsDouble());
        enc.setDecimalLongitude(this.getDecimalLongitudeAsDouble());
        //just going to go ahead and go nuts here and copy most "logical"(?) things.  reset on clone if needed
        enc.setSubmitterID(this.getSubmitterID());
        enc.setSubmitters(this.submitters);
        enc.setPhotographers(this.photographers);
        enc.setSex(this.getSex());
        enc.setLocationID(this.getLocationID());
        enc.setVerbatimLocality(this.getVerbatimLocality());
        enc.setOccurrenceID(this.getOccurrenceID());
        enc.setRecordedBy(this.getRecordedBy());
        enc.setState(this.getState());  //not too sure about this one?
        return enc;
    }

    //this is a special state only used now for match.jsp but basically means the data should be mostly hidden and soon deleted, roughly speaking???
    //  TODO figure out what this really means
    public void setMatchingOnly() {
        this.setState(STATE_MATCHING_ONLY);
    }

    //ann is the Annotation that was created after IA detection.  mostly this is just to notify... someone
    //  note: this is for singly-made encounters; see also Occurrence.fromDetection()
    public void detectedAnnotation(Shepherd myShepherd, Annotation ann) {
System.out.println(">>>>> detectedAnnotation() on " + this);
    }

    /*
       note: these are baby steps into proper ownership of Encounters.  a similar (but cleaner) attempt is done in MediaAssets... however, really
       this probably should be upon some (mythical) BASE CLASS!!!! ... for now, this Encounter variation kinda fudges with existing "ownership" stuff,
       namely, the submitterID - which maps (in theory!) to a User username.
       TODO much much much  ... incl call via constructor maybe ??  etc.
    */
    // NOTE: not going to currently persist the AccessControl object yet, but create on the fly...  clever? stupid?
    public AccessControl getAccessControl() {
        if ((submitterID == null) || submitterID.equals("")) return new AccessControl();  //not sure if we really have some "" but lets be safe
        return new AccessControl(submitterID);
    }
    public void setAccessControl(HttpServletRequest request) {   //really just setting submitterID duh
        this.submitterID = AccessControl.simpleUserString(request);  //null if anon
    }


    public String toString() {
        return new ToStringBuilder(this)
                .append("catalogNumber", catalogNumber)
                .append("individualID", (hasMarkedIndividual() ? individual.getId() : null))
                .append("species", getTaxonomyString())
                .append("sex", getSex())
                .append("shortDate", getShortDate())
                .append("numAnnotations", ((annotations == null) ? 0 : annotations.size()))
                .toString();
    }
    
    public boolean hasMediaFromAssetStoreType(AssetStoreType aType){
      System.out.println("Entering Encounter.hasMediaFromAssetStoreType");
      if(getMediaAssetsOfType(aType).size()>0){return true;}
      return false;
    }
    
    public ArrayList<MediaAsset> getMediaAssetsOfType(AssetStoreType aType){
      System.out.println("Entering Encounter.getMediaAssetsOfType");
      ArrayList<MediaAsset> results=new ArrayList<MediaAsset>();     
      try{
        ArrayList<MediaAsset> assets=getMedia();
        int numAssets=assets.size();
        for(int i=0;i<numAssets;i++){
          MediaAsset ma=assets.get(i);
          if(ma.getStore().getType()==aType){results.add(ma);}
        }
      }
      catch(Exception e){e.printStackTrace();}
      System.out.println("Exiting Encounter.getMediaAssetsOfType with this num results: "+results.size());
      return results;
    }
    public void setObservationArrayList(ArrayList<Observation> obs) {
      this.observations = obs;
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
      //System.out.println("Adding Observation in Base Class... : "+obs.toString());
      if (observations != null && observations.size() > 0) {
        for (Observation ob : observations) {
          if (ob.getName() != null) {
            if (ob.getName().toLowerCase().trim().equals(obs.getName().toLowerCase().trim())) {
               found = true;
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

    
    public List<User> getSubmitters(){
      return submitters;
    }
    
    public List<User> getInformOthers(){
      return informOthers;
    }
    
    public List<String> getSubmitterEmails(){
      ArrayList<String> listy=new ArrayList<String>();
      ArrayList<User> subs=new ArrayList<User>();
      if(getSubmitters()!=null)subs.addAll(getSubmitters());
      int numUsers=subs.size();
      for(int k=0;k<numUsers;k++){
        User use=subs.get(k);
        if((use.getEmailAddress()!=null)&&(!use.getEmailAddress().trim().equals(""))){
          listy.add(use.getEmailAddress());
        }
      }
      return listy;
    }
    
    public List<String> getHashedSubmitterEmails(){
      ArrayList<String> listy=new ArrayList<String>();
      ArrayList<User> subs=new ArrayList<User>();
      if(getSubmitters()!=null)subs.addAll(getSubmitters());
      int numUsers=subs.size();
      for(int k=0;k<numUsers;k++){
        User use=subs.get(k);
        if((use.getHashedEmailAddress()!=null)&&(!use.getHashedEmailAddress().trim().equals(""))){
          listy.add(use.getHashedEmailAddress());
        }
      }
      return listy;
    }
    
    public List<User> getPhotographers(){
      return photographers;
    }
    
    public List<String> getPhotographerEmails(){
      ArrayList<String> listy=new ArrayList<String>();
      ArrayList<User> subs=new ArrayList<User>();
      if(getPhotographers()!=null)subs.addAll(getPhotographers());
      int numUsers=subs.size();
      for(int k=0;k<numUsers;k++){
        User use=subs.get(k);
        if((use.getEmailAddress()!=null)&&(!use.getEmailAddress().trim().equals(""))){
          listy.add(use.getEmailAddress());
        }
      }
      return listy;
    }
    
    public List<String> getInformOthersEmails(){
      ArrayList<String> listy=new ArrayList<String>();
      ArrayList<User> subs=new ArrayList<User>();
      if(getInformOthers()!=null)subs.addAll(getInformOthers());
      int numUsers=subs.size();
      for(int k=0;k<numUsers;k++){
        User use=subs.get(k);
        if((use.getEmailAddress()!=null)&&(!use.getEmailAddress().trim().equals(""))){
          listy.add(use.getEmailAddress());
        }
      }
      return listy;
    }
    
    public List<String> getHashedPhotographerEmails(){
      ArrayList<String> listy=new ArrayList<String>();
      ArrayList<User> subs=new ArrayList<User>();
      if(getPhotographers()!=null)subs.addAll(getPhotographers());
      int numUsers=subs.size();
      for(int k=0;k<numUsers;k++){
        User use=subs.get(k);
        if((use.getHashedEmailAddress()!=null)&&(!use.getHashedEmailAddress().trim().equals(""))){
          listy.add(use.getHashedEmailAddress());
        }
      }
      return listy;
    }
    
    public void addSubmitter(User user) {
        if (user == null) return;
        if (submitters == null) submitters = new ArrayList<User>();
        if (!submitters.contains(user)) submitters.add(user);
    }
    public void addPhotographer(User user) {
      if (user == null) return;
      if (photographers == null) photographers = new ArrayList<User>();
      if (!photographers.contains(user)) photographers.add(user);
    }

    public void setSubmitters(List<User> submitters) {
      if(submitters==null){this.submitters=null;}
      else{
        this.submitters=submitters;
      }
    }
    public void setPhotographers(List<User> photographers) {
      if(photographers==null){this.photographers=null;}
      else{
        this.photographers=photographers;
      }
    }
    
   public void addInformOther(User user) {
      if (user == null) return;
      if (informOthers == null) informOthers = new ArrayList<User>();
      if (!informOthers.contains(user)) informOthers.add(user);
  }

  public void setInformOthers(List<User> users) {
    if(informOthers==null){this.informOthers=null;}
    else{
      this.informOthers=users;
    } 
  }
    
  public static List<String> getIndividualIDs(Collection<Encounter> encs) {
    Set<String> idSet = new HashSet<String>();
    for (Encounter enc: encs) {
      if (enc.hasMarkedIndividual()) idSet.add(enc.getIndividualID());
    }
    return Util.asSortedList(idSet);
  }

}
