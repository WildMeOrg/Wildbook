package org.ecocean;

import org.apache.commons.codec.digest.DigestUtils;

import java.io.*;
import java.lang.Math;
import java.net.URI;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

import javax.jdo.Query;

import org.apache.commons.lang3.builder.ToStringBuilder;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import javax.servlet.http.HttpServletRequest;

import org.ecocean.api.ApiException;
import org.ecocean.api.bulk.BulkImportUtil;
import org.ecocean.api.bulk.BulkValidatorException;
import org.ecocean.api.patch.EncounterPatchValidator;
import org.ecocean.genetics.*;
import org.ecocean.ia.IA;
import org.ecocean.ia.Task;
import org.ecocean.identity.IBEISIA;
import org.ecocean.media.*;
import org.ecocean.security.Collaboration;
import org.ecocean.servlet.importer.ImportTask;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.shepherd.core.ShepherdProperties;
import org.ecocean.social.Membership;
import org.ecocean.social.SocialUnit;
import org.ecocean.tag.AcousticTag;
import org.ecocean.tag.DigitalArchiveTag;
import org.ecocean.tag.MetalTag;
import org.ecocean.tag.SatelliteTag;
import org.ecocean.Util.MeasurementDesc;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.LocalDateTime;

import org.datanucleus.api.rest.orgjson.JSONArray;
import org.datanucleus.api.rest.orgjson.JSONException;
import org.datanucleus.api.rest.orgjson.JSONObject;

/**
 * An <code>encounter</code> object stores the complete data for a single sighting/capture report.
 * <code>Encounters</code> are added to MarkedIndividual objects as multiple encounters are associated with known individuals.
 * <p/>
 *
 * @author Jason Holmberg
 * @version 2.0
 */
public class Encounter extends Base implements java.io.Serializable {
    static final long serialVersionUID = -146404246317385604L;

    public static final String STATE_MATCHING_ONLY = "matching_only";

    @Override public String opensearchIndexName() { return "encounter"; }

    public static final double ENCOUNTER_AUTO_SOURCE_CONFIDENCE_CUTOFF = 0.7;
    public static final String STATE_AUTO_SOURCED = "auto_sourced";

    private String sex = null;
    private String locationID = null;
    private Double maximumDepthInMeters;
    private Double maximumElevationInMeters;
    private String catalogNumber = "";
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
    public String zebraClass = ""; // via lewa: lactating female, territorial male, etc etc

    private String imageSet;
    private String soil;

    private String reproductiveStage;
    private Double bodyCondition;
    private Double parasiteLoad;
    private Double immunoglobin;
    private Boolean sampleTakenForDiet;
    private Boolean injured;
    private boolean opensearchProcessPermissions = false;

    private ArrayList<Observation> observations = new ArrayList<Observation>();

    public String getSoil() { return soil; }
    public void setSoil(String soil) { this.soil = soil; }

    public String getReproductiveStage() { return reproductiveStage; }
    public void setReproductiveStage(String reproductiveStage) {
        this.reproductiveStage = reproductiveStage;
    }

    public Double getBodyCondition() { return bodyCondition; }
    public void setBodyCondition(Double bodyCondition) { this.bodyCondition = bodyCondition; }

    public Double getParasiteLoad() { return parasiteLoad; }
    public void setParasiteLoad(Double parasiteLoad) { this.parasiteLoad = parasiteLoad; }

    public Double getImmunoglobin() { return immunoglobin; }
    public void setImmunoglobin(Double immunoglobin) { this.immunoglobin = immunoglobin; }

    public Boolean getSampleTakenForDiet() { return sampleTakenForDiet; }
    public void setSampleTakenForDiet(Boolean sampleTakenForDiet) {
        this.sampleTakenForDiet = sampleTakenForDiet;
    }

    public Boolean getInjured() { return injured; }
    public void setInjured(Boolean injured) { this.injured = injured; }

    // for searchability
    private String imageNames;

    private List<User> submitters;
    private List<User> photographers;
    private List<User> informOthers;

    private static HashMap<String, ArrayList<Encounter> > _matchEncounterCache = new HashMap<String,
        ArrayList<Encounter> >();

    // An URL to a thumbnail image representing the encounter.
    private String dwcImageURL;

    // Defines whether the sighting represents a living or deceased individual.
    // Currently supported values are: "alive" and "dead".
    private String livingStatus;

    // observed age (if any) via IBEIS zebra projects
    private Double age;

    // Date the encounter was added to the library.
    private String dwcDateAdded;
    private Long dwcDateAddedLong;

    // If Encounter spanned more than one day, date of release
    private Date releaseDate;

    private Long releaseDateLong;

    // Size of the individual in meters
    private Double size;

    // Additional comments added by library users
    private String researcherComments = "None";

    // username of the logged in researcher assigned to the encounter
    // this String is matched to an org.ecocean.User object to obtain more information
    private String submitterID;

    // name, email, phone, address of the encounter reporter
    private String submitterEmail, submitterPhone, submitterAddress;
    private String hashedSubmitterEmail;
    private String hashedPhotographerEmail;
    private String hashedInformOthers;
    private String informothers;

    // name, email, phone, address of the encounter photographer
    private String photographerName, photographerEmail, photographerPhone, photographerAddress;

    // a Vector of Strings defining the relative path to each photo. The path is relative to the servlet base directory
    public Vector additionalImageNames = new Vector();

    // a Vector of Strings of email addresses to notify when this encounter is modified
    private Vector interestedResearchers = new Vector();

    // time metrics of the report
    private int hour = 0;
    private String minutes = "00";

    private String state = "";

    // the globally unique identifier (GUID) for this Encounter
    private String guid;

    private Long endDateInMilliseconds;
    private Long dateInMilliseconds;

    // describes how the shark was measured
    private String size_guess = "none provided";

    // String reported GPS values for lat and long of the encounter
    private String gpsLongitude = "", gpsLatitude = "";
    private String gpsEndLongitude = "", gpsEndLatitude = "";

    // Indicates whether this record can be exposed via TapirLink
    private boolean okExposeViaTapirLink = false;

    public String spotImageFileName = "";

    // name of the stored file from which the right-side spots were extracted
    public String rightSpotImageFileName = "";

    // string descriptor of the most obvious scar (if any) as reported by the original submitter
    // we also use keywords to be more specific
    public String distinguishingScar = "None";
    // describes how this encounter was matched to an existing shark - by eye, by pattern recognition algorithm etc.

    // SPOTS
    // an array of the extracted left-side superSpots
    // private superSpot[] spots;
    private ArrayList<SuperSpot> spots;

    // an array of the extracted right-side superSpots
    // private superSpot[] rightSpots;
    private ArrayList<SuperSpot> rightSpots;

    // an array of the three extracted left-side superSpots used for the affine transform of the I3S algorithm
    // private superSpot[] leftReferenceSpots;
    private ArrayList<SuperSpot> leftReferenceSpots;

    // an array of the three extracted right-side superSpots used for the affine transform of the I3S algorithm
    // private superSpot[] rightReferenceSpots;
    private ArrayList<SuperSpot> rightReferenceSpots;

    // an open ended string that allows a type of patterning to be identified.
    // as an example, see the use of color codes at splashcatalog.org, allowing pre-defined fluke patterning types
    // to be used to help narrow the search for a marked individual
    private String patterningCode;

    // submitting organization and project further detail the scope of who submitted this project
    private String submitterOrganization;
    private String submitterProject;
    private List<String> submitterResearchers;

    // hold submittedData
    // private List<DataCollectionEvent> collectedData;
    private List<TissueSample> tissueSamples;
    private List<SinglePhotoVideo> images;
    // private ArrayList<MediaAsset> media;
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

    // start constructors

    /**
     * empty constructor required by the JDO Enhancer
     */
    public Encounter() {}

    public Encounter(boolean skipSetup) {
        if (skipSetup) return;
        this.catalogNumber = Util.generateUUID();
        this.setDWCDateAdded();
        this.setDWCDateLastModified();
        this.resetDateInMilliseconds();
        this.annotations = new ArrayList<Annotation>();
    }

    /**
     * Use this constructor to add the minimum level of information for a new encounter The Vector <code>additionalImages</code> must be a Vector of
     * Blob objects
     *
     * TODO: evaluate and remove if this is DEPRECATED cuz, SinglePhotoVideos? really?
     */
    public Encounter(int day, int month, int year, int hour, String minutes, String size_guess,
        String location) {
        if (images != null)
            System.out.println(
                "WARNING: danger! deprecated SinglePhotoVideo-based Encounter constructor used!");
        this.verbatimLocality = location;

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
        return (this.annotations == null || this.annotations.size() == 0 ||
                   (this.annotations.size() == 1 && (this.annotations.get(0) == null)));
    }

    // space saver since we're about to use this hundreds of times
    private boolean shouldReplace(String str1, String str2) {
        return Util.shouldReplace(str1, str2);
    }

    // also returns true when str1 is a superstring of str2.
    private boolean shouldReplaceSuperStr(String str1, String str2) {
        return (shouldReplace(str1, str2) || (Util.stringExists(str1) && str1.contains(str2)));
    }

    public void mergeAndDelete(Encounter enc2, Shepherd myShepherd) {
        mergeDataFrom(enc2);
        MarkedIndividual ind = myShepherd.getMarkedIndividual(enc2);
        if (ind != null) {
            ind.removeEncounter(enc2);
            ind.addEncounter(this); // duplicate-safe
        }
        Occurrence occ = myShepherd.getOccurrence(enc2);
        if (occ != null) {
            occ.removeEncounter(enc2);
            occ.addEncounter(this); // duplicate-safe
        }
        // Remove it from an ImportTask if needed
        ImportTask task = myShepherd.getImportTaskForEncounter(enc2.getCatalogNumber());
        if (task != null) {
            task.removeEncounter(enc2);
            myShepherd.updateDBTransaction();
        }
        // Remove from Project if needed
        List<Project> projects = myShepherd.getProjectsForEncounter(enc2);
        if (projects != null && !projects.isEmpty()) {
            for (Project project : projects) {
                project.removeEncounter(enc2);
                myShepherd.updateDBTransaction();
            }
        }
        // remove tissue samples because of bogus foreign key constraint that prevents deletion
        int numTissueSamples = 0;
        if (enc2.getTissueSamples() != null) numTissueSamples = enc2.getTissueSamples().size();
        for (int i = 0; i < numTissueSamples; i++) {
            enc2.removeTissueSample(0);
        }
        this.addComments("<p>Merged in encounter " + enc2.getCatalogNumber() + ".");

        myShepherd.throwAwayEncounter(enc2);
    }

    // copies otherEnc's data into thisEnc, not overwriting anything
    public void mergeDataFrom(Encounter enc2) {
        if (enc2.getIndividual() != null) setIndividual(enc2.getIndividual());
        // simple string fields
        if (shouldReplace(enc2.getSex(), getSex())) setSex(enc2.getSex());
        if (shouldReplace(enc2.getLocationID(), getLocationID()))
            setLocationID(enc2.getLocationID());
        if (shouldReplace(enc2.getVerbatimLocality(), getVerbatimLocality()))
            setVerbatimLocality(enc2.getVerbatimLocality());
        if (shouldReplace(enc2.getOccurrenceID(), getOccurrenceID()))
            setOccurrenceID(enc2.getOccurrenceID());
        if (shouldReplace(enc2.getRecordedBy(), getRecordedBy()))
            setRecordedBy(enc2.getRecordedBy());
        if (shouldReplace(enc2.getEventID(), getEventID())) setEventID(enc2.getEventID());
        if (shouldReplace(enc2.getGenus(), getGenus())) setGenus(enc2.getGenus());
        if (shouldReplace(enc2.getSpecificEpithet(), getSpecificEpithet()))
            setSpecificEpithet(enc2.getSpecificEpithet());
        if (shouldReplace(enc2.getLifeStage(), getLifeStage())) setLifeStage(enc2.getLifeStage());
        if (shouldReplace(enc2.getCountry(), getCountry())) setCountry(enc2.getCountry());
        if (shouldReplace(enc2.getZebraClass(), getZebraClass()))
            setZebraClass(enc2.getZebraClass());
        if (shouldReplace(enc2.getSoil(), getSoil())) setSoil(enc2.getSoil());
        if (shouldReplace(enc2.getReproductiveStage(), getReproductiveStage()))
            setReproductiveStage(enc2.getReproductiveStage());
        if (shouldReplace(enc2.getLivingStatus(), getLivingStatus()))
            setLivingStatus(enc2.getLivingStatus());
        if (shouldReplace(enc2.getSubmitterEmail(), getSubmitterEmail()))
            setSubmitterEmail(enc2.getSubmitterEmail());
        if (shouldReplace(enc2.getSubmitterPhone(), getSubmitterPhone()))
            setSubmitterPhone(enc2.getSubmitterPhone());
        if (shouldReplace(enc2.getSubmitterAddress(), getSubmitterAddress()))
            setSubmitterAddress(enc2.getSubmitterAddress());
        if (shouldReplace(enc2.getState(), getState())) setState(enc2.getState());
        if (shouldReplace(enc2.getGPSLongitude(), getGPSLongitude()))
            setGPSLongitude(enc2.getGPSLongitude());
        if (shouldReplace(enc2.getGPSLatitude(), getGPSLatitude()))
            setGPSLatitude(enc2.getGPSLatitude());
        if (shouldReplace(enc2.getPatterningCode(), getPatterningCode()))
            setPatterningCode(enc2.getPatterningCode());
        if (shouldReplace(enc2.getSubmitterOrganization(), getSubmitterOrganization()))
            setSubmitterOrganization(enc2.getSubmitterOrganization());
        if (shouldReplace(enc2.getSubmitterProject(), getSubmitterProject()))
            setSubmitterProject(enc2.getSubmitterProject());
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
            setOccurrenceRemarks(getOccurrenceRemarks() + " " + enc2.getOccurrenceRemarks());
        }
        // now combine list fields making sure not to add duplicate entries
        setAnnotations(Util.combineArrayListsInPlace(getAnnotations(), enc2.getAnnotations()));
        setObservationArrayList(Util.combineArrayListsInPlace(getObservationArrayList(),
            enc2.getObservationArrayList()));
        setSubmitterResearchers(Util.combineListsInPlace(getSubmitterResearchers(),
            enc2.getSubmitterResearchers()));
        // custom no-duplicate logic bc the same sampleID may have been added on both encounters, but this would create unique tissuesample objects
        Set<String> sampleIDs = getTissueSampleIDs();
        for (TissueSample samp : enc2.getTissueSamples()) {
            if (!sampleIDs.contains(samp.getSampleID())) addTissueSample(samp);
        }
        setMeasurements(Util.combineListsInPlace(getMeasurements(), enc2.getMeasurements()));
        setMetalTags(Util.combineListsInPlace(getMetalTags(), enc2.getMetalTags()));

        // spot lists
        setSpots(Util.combineArrayListsInPlace(getSpots(), enc2.getSpots()));
        setRightSpots(Util.combineArrayListsInPlace(getRightSpots(), enc2.getRightSpots()));
        setLeftReferenceSpots(Util.combineArrayListsInPlace(getLeftReferenceSpots(),
            enc2.getLeftReferenceSpots()));
        setRightReferenceSpots(Util.combineArrayListsInPlace(getRightReferenceSpots(),
            enc2.getRightReferenceSpots()));
        // tags
        if (enc2.getAcousticTag() != null && getAcousticTag() == null)
            setAcousticTag(enc2.getAcousticTag());
        if (enc2.getSatelliteTag() != null && getSatelliteTag() == null)
            setSatelliteTag(enc2.getSatelliteTag());
        if (enc2.getDTag() != null && getDTag() == null) setDTag(enc2.getDTag());
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
        if (imageNames == null) imageNames = name;
        else if (name != null) imageNames += (", " + name);
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
                } catch (Exception e) {
                    System.out.println("exception parsing image name from feature " + feat);
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
        return spots;
    }

    public ArrayList<SuperSpot> getRightSpots() {
        return rightSpots;
    }

    /**
     * Returns an array of all of the superSpots for this encounter.
     *
     * @return the array of superSpots, taken from the croppedImage, that make up the digital fingerprint for this encounter
     */
/*  TODO: evaluate if this is deprecate and can be removed
    these have gone away!  dont be setting spots on Encounter any more .... NOT SO FAST... we regress for whaleshark.org... */
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

    public Integer getFlukeType() { return this.flukeType; }
    public void setFlukeType(Integer flukeType) { this.flukeType = flukeType; }
    // this averages all the fluketypes
    public void setFlukeTypeFromKeywords() {
        int totalFlukeType = 0;
        int numFlukes = 0;

        for (Annotation ann : getAnnotations()) {
            Integer thisFlukeType = getFlukeTypeFromAnnotation(ann);
            if (thisFlukeType != null) {
                totalFlukeType += thisFlukeType;
                numFlukes++;
            }
        }
        if (numFlukes == 0) return;
        setFlukeType(totalFlukeType / numFlukes);
    }

    // assuming the list is of erroneously-duplicated encounters, returns the one we want to keep
    public static Encounter chooseFromDupes(List<Encounter> encs) {
        int maxAnns = -1;
        int encWithMax = 0;

        for (int i = 0; i < encs.size(); i++) {
            Encounter enc = encs.get(i);
            if (enc.numAnnotations() > maxAnns) {
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

        if (ma == null || !ma.hasKeywords()) return null;
        String flukeTypeKwPrefix = "fluke" + maxScore + ":";
        for (Keyword kw : ma.getKeywords()) {
            String kwName = kw.getReadableName();
            if (kwName.contains(flukeTypeKwPrefix)) {
                String justScore = kwName.split(flukeTypeKwPrefix)[1];
                try {
                    Integer score = Integer.parseInt(justScore);
                    if (score != null) return score;
                } catch (NumberFormatException nfe) {
                    System.out.println("NFE on getFlukeTypeFromAnnotation! For ann " + ann +
                        " and kwPrefix " + flukeTypeKwPrefix);
                }
            }
        }
        return null;
    }

    // yes, there "should" be only one of each of these, but we be thorough!
    public void removeLeftSpotMediaAssets(Shepherd myShepherd) {
        ArrayList<MediaAsset> spotMAs = this.findAllMediaByLabel(myShepherd, "_spot");

        for (MediaAsset ma : spotMAs) {
            System.out.println("INFO: removeLeftSpotMediaAsset() detaching " + ma +
                " from parent id=" + ma.getParentId());
            ma.setParentId(null);
        }
    }

    public void removeRightSpotMediaAssets(Shepherd myShepherd) {
        ArrayList<MediaAsset> spotMAs = this.findAllMediaByLabel(myShepherd, "_spotRight");

        for (MediaAsset ma : spotMAs) {
            System.out.println("INFO: removeRightSpotMediaAsset() detaching " + ma +
                " from parent id=" + ma.getParentId());
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
    }

    public int getNumRightSpots() {
        return (rightSpots == null) ? 0 : rightSpots.size();
    }

    public boolean hasLeftSpotImage() {
        return (this.getNumSpots() > 0);
    }

    public boolean hasRightSpotImage() {
        return (this.getNumRightSpots() > 0);
    }

    // Sets the recorded length of the shark for this encounter.
    public void setSize(Double mysize) {
        if (mysize != null) { size = mysize; } else { size = null; }
    }

    // @return the length of the shark
    public double getSize() {
        return size.doubleValue();
    }

    public Double getSizeAsDouble() {
        return size;
    }

    // Sets the units of the recorded size and depth of the shark for this encounter. Acceptable entries are either "Feet" or "Meters"
    public void setMeasureUnits(String measure) {
        measurementUnit = measure;
    }

    // @return the units of measure used by the recorded of this encounter, either "feet" or "meters"
    public String getMeasureUnits() {
        return measurementUnit;
    }

    public String getMeasurementUnit() {
        return measurementUnit;
    }

    // @return the location of this encounter
    public String getLocation() {
        return verbatimLocality;
    }

    public void setLocation(String location) {
        this.verbatimLocality = location;
    }

    // Sets the recorded sex of the shark in this encounter. Acceptable values are "Male" or "Female"
    public void setSex(String thesex) {
        if (thesex != null) { sex = thesex; } else { sex = null; }
    }

    // @return the sex of the shark, either "male" or "female"
    public String getSex() {
        return sex;
    }

    // @return any comments regarding observed scarring on the shark's body
    public boolean getMmaCompatible() {
        if (mmaCompatible == null) return false;
        return mmaCompatible;
    }

    public void setMmaCompatible(boolean b) {
        mmaCompatible = b;
    }

    // @return Occurrence Remarks String
    @Override public String getComments() {
        return occurrenceRemarks;
    }

    // FIXME why is setComments() affecting occurrenceRemarks, and yet
    // addComments() affecting researchComments  !!!!!????
    // @param newComments Occurrence remarks to set
    @Override public void setComments(String newComments) {
        occurrenceRemarks = newComments;
    }

    // @return any comments added by authroized researchers
    public String getRComments() {
        return researcherComments;
    }

    // @param newComments any additional comments to be added to the encounter
    @Override public void addComments(String newComments) {
        if ((researcherComments != null) && (!(researcherComments.equals("None")))) {
            researcherComments += newComments;
        } else {
            researcherComments = newComments;
        }
    }

    // @return the name of the person who submitted this encounter to the database
    public String getSubmitterName() {
        return recordedBy;
    }

    public void setSubmitterName(String newname) {
        if (newname == null) {
            recordedBy = null;
        } else {
            recordedBy = newname;
        }
    }

    // @return the e-mail address of the person who submitted this encounter data
    public String getSubmitterEmail() {
        return submitterEmail;
    }

    public void setSubmitterEmail(String newemail) {
        if (newemail == null) {
            submitterEmail = null;
            this.hashedSubmitterEmail = null;
        } else {
            submitterEmail = newemail;
            this.hashedSubmitterEmail = Encounter.getHashOfEmailString(newemail);
        }
    }

    // @return the phone number of the person who submitted this encounter data
    public String getSubmitterPhone() {
        return submitterPhone;
    }

    // Sets the phone number of the person who submitted this encounter data.
    public void setSubmitterPhone(String newphone) {
        if (newphone == null) {
            submitterPhone = null;
        } else {
            submitterPhone = newphone;
        }
    }

    // @return the mailing address of the person who submitted this encounter data
    public String getSubmitterAddress() {
        return submitterAddress;
    }

    // Sets the mailing address of the person who submitted this encounter data.
    public void setSubmitterAddress(String address) {
        if (address == null) {
            submitterAddress = null;
        } else {
            submitterAddress = address;
        }
    }

    // @return the name of the photographer who took the primary image for this encounter
    public String getPhotographerName() {
        return photographerName;
    }

    // @return the name of the photographer who took the primary image for this encounter
    public void setPhotographerName(String name) {
        if (name == null) {
            photographerName = null;
        } else {
            photographerName = name;
        }
    }

    // @return the e-mail address of the photographer who took the primary image for this encounter
    public String getPhotographerEmail() {
        return photographerEmail;
    }

    // Sets the e-mail address of the person who took the primaryImage this encounter.
    public void setPhotographerEmail(String email) {
        if (email == null) {
            photographerEmail = null;
            this.hashedPhotographerEmail = null;
        } else {
            photographerEmail = email;
            this.hashedPhotographerEmail = Encounter.getHashOfEmailString(email);
        }
    }

    // @return the phone number of the photographer who took the primary image for this encounter
    public String getPhotographerPhone() {
        return photographerPhone;
    }

    // this is a cruddy "solution" to .submitterName and .submitters existing simultaneously
    public Set<String> getAllSubmitterIds(Shepherd myShepherd) {
        Set<String> all = new HashSet<String>();
        User owner = this.getSubmitterUser(myShepherd);

        if (owner == null) {
            all.add(this.submitterID);
            all.add(this.submitterEmail);
        } else {
            all.add(owner.getUsername());
            all.add(owner.getFullName());
            all.add(owner.getId());
            all.add(owner.getEmailAddress());
        }
        if (this.submitters != null)
            for (User user : this.submitters) {
                all.add(user.getUsername());
                all.add(user.getFullName());
                all.add(user.getId());
                all.add(user.getEmailAddress());
            }
        all.remove(null);
        all.remove("");
        return all;
    }

    // similar to above
    public Set<String> getAllPhotographerIds() {
        Set<String> all = new HashSet<String>();

        all.add(this.photographerName);
        if (this.photographers != null)
            for (User user : this.photographers) {
                all.add(user.getUsername());
                all.add(user.getFullName());
                all.add(user.getId());
                all.add(user.getEmailAddress());
            }
        all.remove(null);
        all.remove("");
        return all;
    }

    public Set<String> getAllInformOtherIds() {
        Set<String> all = new HashSet<String>();

        if (this.informOthers != null)
            for (User user : this.informOthers) {
                all.add(user.getUsername());
                all.add(user.getFullName());
                all.add(user.getId());
                all.add(user.getEmailAddress());
            }
        all.remove(null);
        all.remove("");
        return all;
    }

    public String getWebUrl(HttpServletRequest req) {
        return getWebUrl(this.getCatalogNumber(), req);
    }

    public static String getWebUrl(String encId, HttpServletRequest req) {
        return getWebUrl(encId, CommonConfiguration.getServerURL(req));
    }

    public static String getWebUrl(String encId, String serverUrl) {
        return (serverUrl + "/encounters/encounter.jsp?number=" + encId);
    }

    // Sets the phone number of the person who took the primaryImage this encounter.
    public void setPhotographerPhone(String phone) {
        if (phone == null) {
            photographerPhone = null;
        } else {
            photographerPhone = phone;
        }
    }

    // @return the mailing address of the photographer who took the primary image for this encounter
    public String getPhotographerAddress() {
        return photographerAddress;
    }

    // Sets the mailing address of the person who took the primaryImage this encounter.
    public void setPhotographerAddress(String address) {
        if (address == null) {
            photographerAddress = null;
        } else {
            photographerAddress = address;
        }
    }

    // Sets the recorded depth of this encounter.
    public void setDepth(Double myDepth) {
        if (myDepth != null) { maximumDepthInMeters = myDepth; } else {
            maximumDepthInMeters = null;
        }
    }

    // @return the recorded depth for this encounter
    public double getDepth() {
        return maximumDepthInMeters.doubleValue();
    }

    public Double getDepthAsDouble() {
        return maximumDepthInMeters;
    }

    // @return a vector of image name Strings
    public Vector getAdditionalImageNames() {
        Vector imageNamesOnly = new Vector();

        if ((images != null) && (images.size() > 0)) {
            int imagesSize = images.size();
            for (int i = 0; i < imagesSize; i++) {
                SinglePhotoVideo dce = (SinglePhotoVideo)images.get(i);
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

    // @return a unique integer String used to identify this encounter in the database
    public String getEncounterNumber() {
        return catalogNumber;
    }

    public String generateEncounterNumber() {
        return Util.generateUUID();
    }

    public String dir(String baseDir) {
        return baseDir + File.separator + "encounters" + File.separator + this.subdir();
    }

    // like above, but class method so you pass the encID
    public static String dir(String baseDir, String id) {
        return baseDir + File.separator + "encounters" + File.separator + subdir(id);
    }

    // like above, but can pass a File in for base
    public static String dir(File baseDir, String id) {
        return baseDir.getAbsolutePath() + File.separator + "encounters" + File.separator +
                   subdir(id);
    }

    // subdir() is kind of a utility function, which can be called as enc.subdir() or Encounter.subdir(IDSTRING) as needed
    public String subdir() {
        return subdir(this.getEncounterNumber());
    }

    public static String subdir(String id) {
        String d = id; // old-world

        if (Util.isUUID(id)) { // new-world
            d = id.charAt(0) + File.separator + id.charAt(1) + File.separator + id;
        }
        return d;
    }

    // @return a Date object
    public String getDate() {
        String date = "";
        String time = "";

        if (year <= 0) {
            return "Unknown";
        } else if (month == -1) {
            return Integer.toString(year);
        }
        if (hour != -1) {
            String localMinutes = minutes;
            if (!Util.stringExists(localMinutes)) {
                localMinutes = "00";
            } else if (localMinutes.length() == 1) {
                localMinutes = "0" + localMinutes;
            }
            time = String.format("%02d:%s", hour, localMinutes);
        }
        if (day > 0) {
            date = String.format("%04d-%02d-%02d %s", year, month, day, time);
        } else if (month > 0) {
            date = String.format("%04d-%02d %s", year, month, time);
        } else {
            date = String.format("%04d %s", year, time);
        }
        return date.trim();
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

    public org.json.JSONObject getDateValuesJson() {
        org.json.JSONObject dv = new org.json.JSONObject();
        // in theory we *always* should have at least a year, but our data probably says otherwise
        // sadly we just bail empty if we dont have a year
        if (getYear() < 1900) return dv;
        dv.put("year", getYear());
        // from here on out we only add things if the previous one existed; hence the short-circuit return
        // e.g. we do not add a day if there was no month
        if ((getMonth() < 1) || (getMonth() > 12)) return dv;
        dv.put("month", getMonth());
        // sorry not checking actual days-per-month here
        if ((getDay() < 1) || (getDay() > 31)) return dv;
        dv.put("day", getDay());
        if ((getHour() < 0) || (getHour() > 23)) return dv;
        dv.put("hour", getHour());
        // sigh, deal with string-based minutes...
        Integer min = getMinutesInteger();
        if ((min != null) && (min >= 0) && (min < 60)) {
            dv.put("minutes", min);
        } else {
            // choosing to do this because we *must* have hour value here, so dumb to leave null?
            dv.put("minutes", 0);
        }
        return dv;
    }

    // @return a String with text about how the size of this animal was estimated/measured
    public String getSizeGuess() {
        return size_guess;
    }

    public void setDay(int day) {
        this.day = day;
        resetDateInMilliseconds();
    }

    public void setHour(int hour) {
        this.hour = hour;
        resetDateInMilliseconds();
    }

    public void setMinutes(String minutes) {
        this.minutes = minutes;
        resetDateInMilliseconds();
    }

    public String getMinutes() {
        return minutes;
    }

    public Integer getMinutesInteger() {
        Integer min = null;

        try { min = Integer.parseInt(minutes); } catch (Exception e) {}
        return min;
    }

    public int getHour() {
        return hour;
    }

    public void setMonth(int month) {
        this.month = month;
        resetDateInMilliseconds();
    }

    public void setYear(int year) {
        this.year = year;
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

        if (thisTime == null) return false;
        return (start.getMillis() <= thisTime && end.getMillis() >= thisTime);
    }

    // @return the String holding specific location data used for searching
    public String getLocationCode() {
        return locationID;
    }

    // TODO: Verify and remove if this is deprecated: A legacy method replaced by setLocationID(...).
    public void setLocationCode(String newLoc) {
        setLocationID(newLoc);
    }

    // @return the String holding specific location data used for searching
    public String getDistinguishingScar() {
        return distinguishingScar;
    }

    // Sets the String holding scarring information for the encounter
    public void setDistinguishingScar(String scar) {
        distinguishingScar = scar;
    }

    // Sets the String documenting how the size of this animal was approximated.
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
     * Sets the unique encounter identifier to be usd with this encounter. Once this is set, it cannot be changed without possible impact to the
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

    public boolean hasMarkedIndividual(MarkedIndividual match) {
        if (match == null) return false;
        if (individual == null) return false;
        return (match.getId().equals(individual.getId()));
    }

    public void assignToMarkedIndividual(MarkedIndividual indiv) {
        setIndividual(indiv);
    }

    public void setIndividual(MarkedIndividual indiv) {
        if (indiv == null) { this.individual = null; } else { this.individual = indiv; }
        this.refreshAnnotationLiteIndividual();
    }

    public MarkedIndividual getIndividual() {
        return individual;
    }

    public String getDisplayName() {
        return (individual == null) ? null : individual.getDisplayName();
    }

    public String getIndividualID() {
        if (individual == null) return null;
        return individual.getId();
    }

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
            tempEnc = (Encounter)this.clone();
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

    // -------------
    // for the right side spot image

    public String getRightSpotImageFileName() {
        return rightSpotImageFileName;
    }

    public void setRightSpotImageFileName(String name) {
        rightSpotImageFileName = name;
    }

    // ----------------

    // TODO: evaluate and remove if this has been deprecated: really only intended to convert legacy SinglePhotoVideo to MediaAsset/Annotation world
    public ArrayList<Annotation> generateAnnotations(String baseDir, Shepherd myShepherd) {
        if ((annotations != null) && (annotations.size() > 0)) return annotations;
        if ((images == null) || (images.size() < 1)) return null; // probably pointless, so...
        if (annotations == null) annotations = new ArrayList<Annotation>();
        boolean thumbDone = false;
        ArrayList<MediaAsset> haveMedia = new ArrayList<MediaAsset>(); // so we dont add duplicates!
        for (SinglePhotoVideo spv : images) {
            MediaAsset ma = spv.toMediaAsset(myShepherd);
            if (ma == null) {
                System.out.println(
                    "WARNING: Encounter.generateAnnotations() could not create MediaAsset from SinglePhotoVideo "
                    + spv.getDataCollectionEventID() + "; skipping");
                continue;
            }
            if (haveMedia.contains(ma)) {
                System.out.println(
                    "WARNING: Encounter.generateAnnotations() found a duplicate MediaAsset in the SinglePhotoVideo images; skipping -- "
                    + ma);
                continue;
            }
            // note: we need at least minimal metadata (w,h) in order to make annotation, so if this fails, we are no-go
            try {
                ma.updateMetadata();
            } catch (IOException ioe) {
                System.out.println(
                    "WARNING: Encounter.generateAnnotations() failed to updateMetadata() on original MediaAsset "
                    + ma + " (skipping): " + ioe.toString());
                continue;
            }
            ma.addLabel("_original");
            haveMedia.add(ma);

            annotations.add(new Annotation(getTaxonomyString(), ma));
            File idir = new File(spv.getFullFileSystemPath()).getParentFile();

            addMediaIfNeeded(myShepherd, new File(idir, spv.getDataCollectionEventID() + ".jpg"),
                "spv/" + spv.getDataCollectionEventID() + "/" + spv.getDataCollectionEventID() +
                ".jpg", ma, "_watermark");
            addMediaIfNeeded(myShepherd,
                new File(idir, spv.getDataCollectionEventID() + "-mid.jpg"),
                "spv/" + spv.getDataCollectionEventID() + "/" + spv.getDataCollectionEventID() +
                "-mid.jpg", ma, "_mid");
            // note: we "assume" thumb was created from 0th spv, cuz we simply dont know but want it living somewhere
            if (!thumbDone)
                addMediaIfNeeded(myShepherd, new File(idir, "/thumb.jpg"),
                    "spv/" + spv.getDataCollectionEventID() + "/thumb.jpg", ma, "_thumb");
            thumbDone = true;
        }
        // we need to have the spot image as a child under *some* MediaAsset from above, but unfortunately we do not know its lineage.  so we just
        // pick one.  :/
        MediaAsset sma = spotImageAsMediaAsset(((annotations.size() <
            1) ? null : annotations.get(0).getMediaAsset()), baseDir, myShepherd);
        return annotations;
    }

    // utility method for created MediaAssets
    // note: also will check for existence of mpath and fail silently if doesnt exist
    private MediaAsset addMediaIfNeeded(Shepherd myShepherd, File mpath, String key,
        MediaAsset parentMA, String label) {
        if ((mpath == null) || !mpath.exists()) return null;
        AssetStore astore = AssetStore.getDefault(myShepherd);
        org.json.JSONObject sp = astore.createParameters(mpath);
        if (key != null) sp.put("key", key); // will use default from createParameters() (if there was one even)
        MediaAsset ma = astore.find(sp, myShepherd);
        if (ma != null) {
            ma.addLabel(label);
            if (parentMA != null) ma.setParentId(parentMA.getIdInt());
            return ma;
        }
        System.out.println("creating new MediaAsset for key=" + key);
        try {
            ma = astore.copyIn(mpath, sp);
        } catch (IOException ioe) {
            System.out.println("Could not create MediaAsset for key=" + key + ": " +
                ioe.toString());
            return null;
        }
        if (parentMA != null) {
            ma.setParentId(parentMA.getIdInt());
            ma.updateMinimalMetadata(); // for children (ostensibly derived?) MediaAssets, really only need minimal metadata or so i claim
        } else {
            try {
                ma.updateMetadata(); // root images get the whole deal (guess this sh/could key off label=_original ?)
            } catch (IOException ioe) {
                // we dont care (well sorta) ... since IOException usually means we couldnt open file or some nonsense that we cant recover from
            }
        }
        ma.addLabel(label);
        MediaAssetFactory.save(ma, myShepherd);
        return ma;
    }

    // this makes assumption (for flukes) that both right and left image files are identical
    public MediaAsset spotImageAsMediaAsset(MediaAsset parent, String baseDir,
        Shepherd myShepherd) {
        if ((spotImageFileName == null) || spotImageFileName.equals("")) return null;
        File fullPath = new File(this.dir(baseDir) + "/" + spotImageFileName);
// System.out.println("**** * ***** looking for spot file " + fullPath.toString());
        if (!fullPath.exists()) return null; // note: this only technically matters if we are *creating* the MediaAsset
        if (parent == null) {
            System.out.println("seems like we do not have a parent MediaAsset on enc " +
                this.getCatalogNumber() + ", so cannot add spot MediaAsset for " +
                fullPath.toString());
            return null;
        }
        AssetStore astore = AssetStore.getDefault(myShepherd);
        if (astore == null) {
            System.out.println("No AssetStore in Encounter.spotImageAsMediaAsset()");
            return null;
        }
        System.out.println("trying spotImageAsMediaAsset with file=" + fullPath.toString());
        org.json.JSONObject sp = astore.createParameters(fullPath);
        sp.put("key", this.subdir() + "/spotImage-" + spotImageFileName);
                                                                          // others?
        MediaAsset ma = astore.find(sp, myShepherd);
        if (ma == null) {
            System.out.println("did not find MediaAsset for params=" + sp + "; creating one?");
            try {
                ma = astore.copyIn(fullPath, sp);
                ma.addDerivationMethod("historicSpotImageConversion", true);
                ma.updateMinimalMetadata();
// System.out.println("params? " + ma.getParameters());
                ma.addLabel("_spot");
                ma.addLabel("_annotation");
                MediaAssetFactory.save(ma, myShepherd);
// System.out.println("params? " + ma.getParameters());
            } catch (java.io.IOException ex) {
                System.out.println("spotImageAsMediaAsset threw IOException " + ex.toString());
            }
        }
        ma.setParentId(parent.getIdInt());
        return ma;
    }

    public void setSubmitterID(String username) {
        if (username != null) { submitterID = username; } else { submitterID = null; }
    }

    // old method. use getAssignedUser() instead
    public String getSubmitterID() {
        return getAssignedUsername();
    }

    public String getAssignedUsername() {
        return submitterID;
    }

    public User getSubmitterUser(Shepherd myShepherd) {
        return myShepherd.getUser(submitterID);
    }

    public Vector getInterestedResearchers() {
        return interestedResearchers;
    }

    public void addInterestedResearcher(String email) {
        interestedResearchers.add(email);
    }

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

            Rray[0] = new com.reijns.I3S.Point2D(refsLeft.get(0).getTheSpot().getCentroidX(),
                refsLeft.get(0).getTheSpot().getCentroidY());
            Rray[1] = new com.reijns.I3S.Point2D(refsLeft.get(1).getTheSpot().getCentroidX(),
                refsLeft.get(1).getTheSpot().getCentroidY());
            Rray[2] = new com.reijns.I3S.Point2D(refsLeft.get(2).getTheSpot().getCentroidX(),
                refsLeft.get(2).getTheSpot().getCentroidY());
            System.out.println("\tI found three left reference points!");
        } else {
            com.reijns.I3S.Point2D topLeft = new com.reijns.I3S.Point2D(getLeftmostSpot(),
                getHighestSpot());
            com.reijns.I3S.Point2D bottomLeft = new com.reijns.I3S.Point2D(getLeftmostSpot(),
                getLowestSpot());
            com.reijns.I3S.Point2D bottomRight = new com.reijns.I3S.Point2D(getRightmostSpot(),
                getLowestSpot());
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
            Rray[0] = new com.reijns.I3S.Point2D(refsRight.get(0).getTheSpot().getCentroidX(),
                refsRight.get(0).getTheSpot().getCentroidY());
            Rray[1] = new com.reijns.I3S.Point2D(refsRight.get(1).getTheSpot().getCentroidX(),
                refsRight.get(1).getTheSpot().getCentroidY());
            Rray[2] = new com.reijns.I3S.Point2D(refsRight.get(2).getTheSpot().getCentroidX(),
                refsRight.get(2).getTheSpot().getCentroidY());
        } else {
            com.reijns.I3S.Point2D topRight = new com.reijns.I3S.Point2D(getRightmostRightSpot(),
                getHighestRightSpot());
            com.reijns.I3S.Point2D bottomRight = new com.reijns.I3S.Point2D(getRightmostRightSpot(),
                getLowestRightSpot());
            com.reijns.I3S.Point2D bottomLeft = new com.reijns.I3S.Point2D(getLeftmostRightSpot(),
                getLowestRightSpot());

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
        // return HACKgetAnyReferenceSpots();
        return leftReferenceSpots;
    }

    public ArrayList<SuperSpot> getRightReferenceSpots() {
        // return HACKgetAnyReferenceSpots();
        return rightReferenceSpots;
    }

/*  gone! no more setting spots on encounters!  ... whoa there, yes there is for whaleshark.org */
    public void setLeftReferenceSpots(ArrayList<SuperSpot> leftReferenceSpots) {
        this.leftReferenceSpots = leftReferenceSpots;
    }

    public void setRightReferenceSpots(ArrayList<SuperSpot> rightReferenceSpots) {
        this.rightReferenceSpots = rightReferenceSpots;
    }

    // @return the variance for population
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
        // return (s / n);
        return (s / (n - 1));
    }

    // @return the standard deviation for population
    public double standard_deviation(double[] population) {
        return Math.sqrt(variance(population));
    }

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

    public Long getDWCDateAddedLong() {
        return dwcDateAddedLong;
    }

    public Long getDwcDateAddedLong() {
        return dwcDateAddedLong;
    }

    public void setDWCDateAdded(String m_dateAdded) {
        dwcDateAdded = m_dateAdded;
    }

    public void setDWCDateAdded() {
        Date myDate = new Date();

        dwcDateAddedLong = new Long(myDate.getTime());
        dwcDateAdded = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(myDate);
    }

    public void setDWCDateAdded(Long m_dateAdded) {
        dwcDateAddedLong = m_dateAdded;
    }

    // TODO: evaluate and remove if deprecated
    public Date getReleaseDateDONOTUSE() {
        return releaseDate;
    }

    public Date getReleaseDate() {
        if ((releaseDateLong != null) && (releaseDateLong > 0)) {
            Date mDate = new Date(releaseDateLong);
            return mDate;
        }
        return null;
    }

    public Long getReleaseDateLong() { return releaseDateLong; }

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

    // TODO: Get all this lat lon over to Locations

    public void setDWCDecimalLatitude(double lat) {
        if (lat == -9999.0) {
            decimalLatitude = null;
        } else {
            decimalLatitude = (new Double(lat));
        }
    }

    public void setDWCDecimalLatitude(Double lat) {
        if ((lat != null) && (lat <= 90) && (lat >= -90)) {
            this.decimalLatitude = lat;
        } else { this.decimalLatitude = null; }
    }

    public String getDWCDecimalLatitude() {
        if (decimalLatitude != null) { return Double.toString(decimalLatitude); }
        return null;
    }

    public void setDWCDecimalLongitude(double longit) {
        if ((longit >= -180) && (longit <= 180)) {
            this.decimalLongitude = longit;
        }
    }

    public String getDWCDecimalLongitude() {
        if (decimalLongitude != null) {
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

    public String getLocationID() {
        return locationID;
    }

    public String getLocationName() {
        if (locationID == null) return null;
        return LocationID.getNameForLocationID(locationID, null);
    }

    public void setLocationID(String newLocationID) {
        if (newLocationID != null) {
            this.locationID = newLocationID.trim();
        } else {
            this.locationID = null;
        }
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

    // @return Catalog Number String
    @Override public String getId() {
        return catalogNumber;
    }

    // @param newNumber The Catalog Number to set.
    @Override public void setId(String newNumber) {
        this.catalogNumber = newNumber;
    }

    // TODO: remove if actually deprecated and unused
    // ##DEPRECATED #509 - Base class getId() method
    public String getCatalogNumber() {
        return catalogNumber;
    }

    // TODO: remove if actually deprecated and unused
    // ##DEPRECATED #509 - Base class setId() method
    public void setCatalogNumber(String newNumber) {
        this.catalogNumber = newNumber;
    }

    // TODO: remove if actually deprecated and unused
    // ##DEPRECATED #509 - Base class getId() method
    public String getID() {
        return catalogNumber;
    }

    // TODO: remove if actually deprecated and unused
    // ##DEPRECATED #509 - Base class setId() method
    public void setID(String newNumber) {
        this.catalogNumber = newNumber;
    }

    public String getVerbatimLocality() {
        return verbatimLocality;
    }

    public void setVerbatimLocality(String vlcl) {
        this.verbatimLocality = vlcl;
    }

    public Double getDecimalLatitudeAsDouble() {
        return (decimalLatitude == null) ? null : decimalLatitude.doubleValue();
    }

    public void setDecimalLatitude(Double lat) {
        this.decimalLatitude = lat;
        gpsLatitude = Util.decimalLatLonToString(lat);
    }

    public Double getDecimalLongitudeAsDouble() {
        return (decimalLongitude == null) ? null : decimalLongitude.doubleValue();
    }

    public void setDecimalLongitude(Double lon) {
        this.decimalLongitude = lon;
        gpsLongitude = Util.decimalLatLonToString(lon);
    }

    public Double getEndDecimalLatitudeAsDouble() {
        return (endDecimalLatitude == null) ? null : endDecimalLatitude.doubleValue();
    }

    public void setEndDecimalLatitude(Double lat) {
        this.endDecimalLatitude = lat;
        gpsEndLatitude = Util.decimalLatLonToString(lat);
    }

    public Double getEndDecimalLongitudeAsDouble() {
        return (endDecimalLongitude == null) ? null : endDecimalLongitude.doubleValue();
    }

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
        if (vet != null) { this.verbatimEventDate = vet; } else { this.verbatimEventDate = null; }
    }

    public String getDynamicProperties() {
        return dynamicProperties;
    }

    public org.json.JSONObject getDynamicPropertiesJSONObject() {
        org.json.JSONObject dp = new org.json.JSONObject();
        if (dynamicProperties == null) return dp;
        StringTokenizer st = new StringTokenizer(dynamicProperties, ";");
        while (st.hasMoreTokens()) {
            String token = st.nextToken();
            int equalPlace = token.indexOf("=");
            if (equalPlace > 0)
                dp.put(token.substring(0, equalPlace), token.substring(equalPlace + 1));
        }
        return dp;
    }

    public void setDynamicProperties(String allDynamicProperties) {
        this.dynamicProperties = allDynamicProperties;
    }

    public void addDynamicProperties(String allDynamicProperties) {
        this.dynamicProperties += allDynamicProperties;
    }

    public void setDynamicProperty(String name, String value) {
        name = name.replaceAll(";", "_").trim().replaceAll("%20", " ");
        value = value.replaceAll(";", "_").trim();
        if (dynamicProperties == null) { dynamicProperties = name + "=" + value + ";"; } else {
            // let's create a TreeMap of the properties
            TreeMap<String, String> tm = new TreeMap<String, String>();
            StringTokenizer st = new StringTokenizer(dynamicProperties, ";");
            while (st.hasMoreTokens()) {
                String token = st.nextToken();
                int equalPlace = token.indexOf("=");
                try {
                    tm.put(token.substring(0, equalPlace), token.substring(equalPlace + 1));
                } catch (java.lang.StringIndexOutOfBoundsException soe) {
                    // this is a badly formatted pair that should be ignored
                }
            }
            if (tm.containsKey(name)) {
                tm.remove(name);
                tm.put(name, value);

                // now let's recreate the dynamicProperties String
                String newProps = tm.toString();
                int stringSize = newProps.length();
                dynamicProperties = newProps.substring(1, (stringSize - 1)).replaceAll(", ",
                    ";") + ";";
            } else {
                dynamicProperties = dynamicProperties + name + "=" + value + ";";
            }
        }
    }

    public String getDynamicPropertyValue(String name) {
        if (dynamicProperties != null) {
            name = name.replaceAll("%20", " ");
            // let's create a TreeMap of the properties
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
        return (this.getDynamicPropertyValue(name) != null);
    }

    public void removeDynamicProperty(String name) {
        name = name.replaceAll(";", "_").trim().replaceAll("%20", " ");
        if (dynamicProperties != null) {
            // let's create a TreeMap of the properties
            TreeMap<String, String> tm = new TreeMap<String, String>();
            StringTokenizer st = new StringTokenizer(dynamicProperties, ";");
            while (st.hasMoreTokens()) {
                String token = st.nextToken();
                int equalPlace = token.indexOf("=");
                tm.put(token.substring(0, (equalPlace)), token.substring(equalPlace + 1));
            }
            if (tm.containsKey(name)) {
                tm.remove(name);

                // now let's recreate the dynamicProperties String
                String newProps = tm.toString();
                int stringSize = newProps.length();
                dynamicProperties = newProps.substring(1, (stringSize - 1)).replaceAll(", ",
                    ";") + ";";
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
        if (newGenus != null) { genus = newGenus; } else { genus = null; }
        this.refreshAnnotationLiteTaxonomy();
    }

    // we need these methods because our side-effected setGenus will silently break an import (!!!!!) in an edge case I cannot identify
    public void setGenusOnly(String genus) {
        this.genus = genus;
        this.refreshAnnotationLiteTaxonomy();
    }

    public void setSpeciesOnly(String species) {
        setSpecificEpithet(species);
        this.refreshAnnotationLiteTaxonomy();
    }

    public String getSpecificEpithet() {
        return specificEpithet;
    }

    public void setSpecificEpithet(String newEpithet) {
        if (newEpithet != null) { specificEpithet = newEpithet.replaceAll("_", " "); } else {
            specificEpithet = null;
        }
        this.refreshAnnotationLiteTaxonomy();
    }

    public String getTaxonomyString() {
        return Util.taxonomyString(getGenus(), getSpecificEpithet());
    }

    // hacky (as generates new Taxonomy -- with random uuid) but still should work for tax1.equals(tax2);
    // TODO: FIXME this should be superceded by the getter for Taxonomy property in the future....
    public Taxonomy getTaxonomy(Shepherd myShepherd) {
        String sciname = this.getTaxonomyString();

        if (sciname == null) return null;
        return myShepherd.getOrCreateTaxonomy(sciname, false); // false means don't commit the taxonomy
    }

    // right now this updates .genus and .specificEpithet ... but in some glorious future we will just store Taxonomy!
    // note that "null" cases will leave *current values untouched* (does not reset them)
    public void setTaxonomy(Taxonomy tax) {
        if (tax == null) return;
        String[] gs = tax.getGenusSpecificEpithet();
        if ((gs == null) || (gs.length < 1)) return;
        if (gs.length == 1) {
            this.genus = gs[0];
            this.specificEpithet = null;
        } else {
            this.genus = gs[0];
            this.specificEpithet = gs[1].replaceAll("_", " ");
        }
        this.refreshAnnotationLiteTaxonomy();
    }

    public void setTaxonomyFromString(String s) { // basically scientific name (will get split on space)
        String[] gs = Util.stringToGenusSpecificEpithet(s);

        if ((gs == null) || (gs.length < 1)) return;
        if (gs.length == 1) {
            this.genus = gs[0];
            this.specificEpithet = null;
        } else {
            this.genus = gs[0];
            this.specificEpithet = gs[1].replaceAll("_", " ");
        }
        this.refreshAnnotationLiteTaxonomy();
    }

    public void setTaxonomyFromIAClass(String iaClass, Shepherd myShepherd) {
        setTaxonomy(IBEISIA.iaClassToTaxonomy(iaClass, myShepherd));
    }

    public String getPatterningCode() { return patterningCode; }
    public void setPatterningCode(String newCode) { this.patterningCode = newCode; }

    // crawls thru assets and sets date
    public void setDateFromAssets() {
        // FIXME if you dare.  i can *promise you* there are some timezone problems here.  ymmv.
        if ((annotations == null) || (annotations.size() < 1)) return;
        DateTime dt = null;
        for (Annotation ann : annotations) {
            MediaAsset ma = ann.getMediaAsset();
            if (ma == null) continue;
            dt = ma.getDateTime();
            if (dt != null) break; // we just take the first one
        }
        if (dt != null) setDateInMilliseconds(dt.getMillis());
    }

    // this can(should?) fail in a lot of cases, since we may not know
    public void setSpeciesFromAnnotations() {
        if ((annotations == null) || (annotations.size() < 1)) return;
        String[] sp = null;
        for (Annotation ann : annotations) {
            sp = IBEISIA.convertSpecies(annotations.get(0).getIAClass());
            if (sp != null) break; // use first one we get
        }
        // note: now we require (exactly) two parts ... please fix this, Taxonomy class!
        if ((sp == null) || (sp.length != 2)) return;
        this.setGenus(sp[0]);
        this.setSpecificEpithet(sp[1]);
    }

    // find the first one(s) we can
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

    // sets date to the closes we have to "not set" :)
    public void zeroOutDate() {
        year = 0;
        month = 0;
        day = 0;
        hour = -1;
        minutes = "00";
        resetDateInMilliseconds(); // should set that to null as well
    }

    public void resetDateInMilliseconds() {
        dateInMilliseconds = computeDateInMilliseconds();
    }

    public Long computeDateInMilliseconds() {
        if (year > 0) {
            int localMonth = 0;
            if (month > 0) { localMonth = month - 1; }
            int localDay = 1;
            if (day > 0) { localDay = day; }
            int localHour = 0;
            if (hour > -1) { localHour = hour; }
            int myMinutes = 0;
            try { myMinutes = Integer.parseInt(minutes); } catch (Exception e) {}
            TimeZone tz = TimeZone.getTimeZone("UTC");
            Calendar calendar = Calendar.getInstance(tz);
            calendar.set(year, localMonth, localDay, localHour, myMinutes);
            return new Long(calendar.getTimeInMillis());
        }
        return null;
    }

    public java.lang.Long getDateInMilliseconds() { return dateInMilliseconds; }

    public Long getDateInMillisecondsFallback() {
        if (dateInMilliseconds != null) return dateInMilliseconds;
        return computeDateInMilliseconds();
    }

    // this will set all date stuff based on ms since epoch
    public void setDateInMilliseconds(long ms) {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

        cal.setTimeInMillis(ms);
        this.year = cal.get(Calendar.YEAR);
        this.month = cal.get(Calendar.MONTH) + 1;
        this.day = cal.get(Calendar.DAY_OF_MONTH);
        this.hour = cal.get(Calendar.HOUR);
        this.minutes = Integer.toString(cal.get(Calendar.MINUTE));
        if (this.minutes.length() == 1) this.minutes = "0" + this.minutes;
        this.dateInMilliseconds = ms;
    }

    // also supports YYYY and YYYY-MM
    public void setDateFromISO8601String(String iso8601) {
        if (!validISO8601String(iso8601)) return;
        if (iso8601.length() == 4) { // assume year
            try {
                this.year = Integer.parseInt(iso8601);
            } catch (Exception ex) {}
            resetDateInMilliseconds();
            return;
        }
        // this should already be validated so we can trust it (flw)
        if (iso8601.length() == 7) {
            try {
                this.year = Integer.parseInt(iso8601.substring(0, 4));
                this.month = Integer.parseInt(iso8601.substring(5, 7));
            } catch (Exception ex) {}
            resetDateInMilliseconds();
            return;
        }
        try {
            String adjusted = Util.getISO8601Date(iso8601);
            DateTime dt = new DateTime(adjusted);
            this.setDateInMilliseconds(dt.getMillis());
        } catch (Exception ex) {
            System.out.println("setDateFromISO8601String(" + iso8601 + ") failed: " + ex);
        }
        resetDateInMilliseconds();
    }

    // also supports YYYY and YYYY-MM
    public static boolean validISO8601String(String iso8601) {
        if (iso8601 == null) return false;
        if (iso8601.length() == 4) {
            Integer yr = null;
            try {
                yr = Integer.parseInt(iso8601);
            } catch (Exception ex) {}
            return (yr != null);
        }
        if (iso8601.length() == 7) {
            Integer yr = null;
            Integer mo = null;
            try {
                yr = Integer.parseInt(iso8601.substring(0, 4));
                mo = Integer.parseInt(iso8601.substring(5, 7));
            } catch (Exception ex) {}
            if ((yr == null) || (mo == null)) return false;
            if ((mo < 1) || (mo > 12)) return false;
            return true;
        }
        long test = Util.getVersionFromModified(iso8601);
        return (test > 0);
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

    public String getDecimalLatitude() {
        if (decimalLatitude != null) { return Double.toString(decimalLatitude); }
        return null;
    }

    public String getDecimalLongitude() {
        if (decimalLongitude != null) { return Double.toString(decimalLongitude); }
        return null;
    }

    public String getEndDecimalLongitude() {
        if (endDecimalLongitude != null) { return Double.toString(endDecimalLongitude); }
        return null;
    }

    public String getEndDecimalLatitude() {
        if (endDecimalLatitude != null) { return Double.toString(endDecimalLatitude); }
        return null;
    }

    public String getSubmitterProject() {
        return submitterProject;
    }

    public void setSubmitterProject(String newProject) {
        if (newProject != null) { submitterProject = newProject; } else { submitterProject = null; }
    }

    public String getSubmitterOrganization() {
        return submitterOrganization;
    }

    public void setSubmitterOrganization(String newOrg) {
        if (newOrg != null) { submitterOrganization = newOrg; } else {
            submitterOrganization = null;
        }
    }

    public List<String> getSubmitterResearchers() {
        return submitterResearchers;
    }

    public void addSubmitterResearcher(String researcher) {
        if (submitterResearchers == null) submitterResearchers = new ArrayList<String>();
        submitterResearchers.add(researcher);
    }

    public void setSubmitterResearchers(Collection<String> researchers) {
        if (researchers != null) this.submitterResearchers = new ArrayList<String>(researchers);
    }

    public List<Project> getProjects(Shepherd myShepherd) {
        return myShepherd.getProjectsForEncounter(this);
    }

    public void addTissueSample(TissueSample dce) {
        if (tissueSamples == null) { tissueSamples = new ArrayList<TissueSample>(); }
        if (!tissueSamples.contains(dce)) { tissueSamples.add(dce); }
        dce.setCorrespondingEncounterNumber(getCatalogNumber());
    }

    public void setTissueSamples(List<TissueSample> samps) {
        this.tissueSamples = samps;
    }

    public void removeTissueSample(int num) { tissueSamples.remove(num); }
    public List<TissueSample> getTissueSamples() { return tissueSamples; }
    public Set<String> getTissueSampleIDs() {
        Set<String> ids = new HashSet<String>();

        if (tissueSamples != null)
            for (TissueSample ts : tissueSamples) {
                ids.add(ts.getSampleID());
            }
        return ids;
    }

    public void removeTissueSample(TissueSample num) { tissueSamples.remove(num); }

    public void addSinglePhotoVideo(SinglePhotoVideo dce) {
        if (images == null) { images = new ArrayList<SinglePhotoVideo>(); }
        if (!images.contains(dce)) { images.add(dce); }
    }

    public void removeSinglePhotoVideo(int num) { images.remove(num); }
    public List<SinglePhotoVideo> getSinglePhotoVideo() { return images; }
    public void removeSinglePhotoVideo(SinglePhotoVideo num) { images.remove(num); }

    public void setMeasurements(List<Measurement> measurements) {
        this.measurements = measurements;
    }

    public void setMeasurement(Measurement measurement, Shepherd myShepherd) {
        // if measurements are null, set the empty list
        if (measurements == null) { measurements = new ArrayList<Measurement>(); }
        // now start checking for existence of a previous measurement
        // if we have it but the new value is null, remove the measurement
        if ((this.hasMeasurement(measurement.getType())) && (measurement.getValue() == null)) {
            Measurement m = this.getMeasurement(measurement.getType());
            measurements.remove(m);
            myShepherd.getPM().deletePersistent(m);
            myShepherd.commitDBTransaction();
            myShepherd.beginDBTransaction();
        }
        // just add the measurement it if we did not have it before
        else if (!this.hasMeasurement(measurement.getType())) {
            measurements.add(measurement);
            myShepherd.commitDBTransaction();
            myShepherd.beginDBTransaction();
        }
        // if we had it before then just update the value
        else if ((this.hasMeasurement(measurement.getType())) && (measurement != null)) {
            Measurement m = this.getMeasurement(measurement.getType());
            m.setValue(measurement.getValue());
            m.setSamplingProtocol(measurement.getSamplingProtocol());
            myShepherd.commitDBTransaction();
            myShepherd.beginDBTransaction();
        }
    }

    public Measurement removeMeasurementByType(String type) {
        // findMeasurementOfType() seems to return even ones with value=null
        // (versus getMeasurement())... so this seems the better choice?
        Measurement m = findMeasurementOfType(type);

        if (m != null) measurements.remove(m);
        return m;
    }

    // will find the Measurement (by type) and modify it *or* make a new one
    public Measurement getOrCreateMeasurement(org.json.JSONObject jdata) {
        if (jdata == null) return null;
        String type = jdata.optString("type", null);
        if (type == null) return null;
        Measurement m = findMeasurementOfType(type);
        if (m == null) {
            m = new Measurement(this.getId(), type, jdata.optDouble("value", 0.0D),
                jdata.optString("units", null), jdata.optString("samplingProtocol", null));
        } else {
            m.setValue(jdata.optDouble("value", 0.0D));
            m.setUnits(jdata.optString("units", null));
            m.setSamplingProtocol(jdata.optString("samplingProtocol", null));
        }
        return m;
    }

    // like above but way less persisty
    public void setMeasurement(Measurement measurement) {
        if (measurement == null) return;
        if (measurements == null) measurements = new ArrayList<Measurement>();
        Measurement hasType = this.getMeasurement(measurement.getType());
        if (hasType == null) {
            measurements.add(measurement);
        } else if (measurement.getValue() == null) {
            // i guess this means we remove it
            measurements.remove(hasType);
        } else {
            // update existing
            hasType.setValue(measurement.getValue());
            hasType.setSamplingProtocol(measurement.getSamplingProtocol());
        }
    }

    // like above but less..... checky (trust the caller!)
    public void addMeasurement(Measurement meas) {
        if (meas == null) return;
        if (measurements == null) measurements = new ArrayList<Measurement>();
        measurements.add(meas);
    }

    public void removeMeasurement(int num) { measurements.remove(num); }
    public List<Measurement> getMeasurements() { return measurements; }
    public void removeMeasurement(Measurement num) { measurements.remove(num); }
    // FIXME this seems suspiciously just like getMeasurement(type)
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

    // this does NOT validate values
    public MetalTag addOrUpdateMetalTag(String location, String number) {
        if ((location == null) || (number == null)) return null;
        MetalTag tag = findMetalTagForLocation(location);
        if (tag == null) {
            tag = new MetalTag(number, location);
            addMetalTag(tag);
        } else {
            tag.setTagNumber(number);
        }
        return tag;
    }

    public void removeMetalTag(MetalTag metalTag) {
        metalTags.remove(metalTag);
    }

    // this will clear out ALL tags with this location, but i think we
    // are supposed to only have [at most] one anyway!
    public void removeMetalTag(String location) {
        if ((location == null) || (metalTags == null)) return;
        ListIterator<MetalTag> it = metalTags.listIterator();
        while (it.hasNext()) {
            MetalTag next = it.next();
            if (location.equals(next.getLocation())) it.remove();
        }
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

    public String getLifeStage() { return lifeStage; }
    public void setLifeStage(String newStage) {
        if (newStage != null) { lifeStage = newStage; } else { lifeStage = null; }
    }

    // A convenience method that returns the first haplotype found in the TissueSamples for this Encounter.
    public String getHaplotype() {
        if (tissueSamples != null) {
            int numTissueSamples = tissueSamples.size();
            if (numTissueSamples > 0) {
                for (int j = 0; j < numTissueSamples; j++) {
                    TissueSample thisSample = tissueSamples.get(j);
                    int numAnalyses = thisSample.getNumAnalyses();
                    if (numAnalyses > 0) {
                        List<GeneticAnalysis> gAnalyses = thisSample.getGeneticAnalyses();
                        for (int g = 0; g < numAnalyses; g++) {
                            GeneticAnalysis ga = gAnalyses.get(g);
                            if (ga.getAnalysisType().equals("MitochondrialDNA")) {
                                MitochondrialDNAAnalysis mito = (MitochondrialDNAAnalysis)ga;
                                if (mito.getHaplotype() != null) { return mito.getHaplotype(); }
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    // A convenience method that returns the first genetic sex found in the TissueSamples for this Encounter.
    public String getGeneticSex() {
        if (tissueSamples != null) {
            int numTissueSamples = tissueSamples.size();
            if (numTissueSamples > 0) {
                for (int j = 0; j < numTissueSamples; j++) {
                    TissueSample thisSample = tissueSamples.get(j);
                    int numAnalyses = thisSample.getNumAnalyses();
                    if (numAnalyses > 0) {
                        List<GeneticAnalysis> gAnalyses = thisSample.getGeneticAnalyses();
                        for (int g = 0; g < numAnalyses; g++) {
                            GeneticAnalysis ga = gAnalyses.get(g);
                            if (ga.getAnalysisType().equals("SexAnalysis")) {
                                SexAnalysis mito = (SexAnalysis)ga;
                                if (mito.getSex() != null) { return mito.getSex(); }
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    public List<MicrosatelliteMarkersAnalysis> getMicrosatelliteMarkers() {
        List<MicrosatelliteMarkersAnalysis> markers =
            new ArrayList<MicrosatelliteMarkersAnalysis>();

        if (tissueSamples == null) return markers;
        for (TissueSample tsamp : tissueSamples) {
            for (GeneticAnalysis gan : tsamp.getGeneticAnalyses()) {
                if (!"MicrosatelliteMarkers".equals(gan.getAnalysisType())) continue;
                markers.add((MicrosatelliteMarkersAnalysis)gan);
            }
        }
        return markers;
    }

    public List<SinglePhotoVideo> getImages() { return images; }

    public boolean hasAnnotation(Annotation ann) {
        return (annotations != null && annotations.contains(ann));
    }

    public boolean hasAnnotations() {
        return (annotations != null && annotations.size() > 0);
    }

    public int numAnnotations() {
        if (annotations == null) return 0;
        return annotations.size();
    }

    public int numNonTrivialAnnotations() {
        if (annotations == null) return 0;
        int ct = 0;
        for (Annotation ann : annotations) {
            if (!ann.isTrivial()) ct++;
        }
        return ct;
    }

    public ArrayList<Annotation> getAnnotations() {
        return annotations;
    }

    public Annotation getAnnotation(String annId) {
        if ((annId == null) || (annotations == null)) return null;
        for (Annotation ann : annotations) {
            if (annId.equals(ann.getId())) return ann;
        }
        return null;
    }

    public Set<String> getAnnotationViewpoints() {
        Set<String> vps = new HashSet<String>();

        if (!hasAnnotations()) return vps;
        for (Annotation ann : annotations) {
            if (ann.getViewpoint() != null) vps.add(ann.getViewpoint());
        }
        return vps;
    }

    public Set<String> getAnnotationIAClasses() {
        Set<String> classes = new HashSet<String>();

        if (!hasAnnotations()) return classes;
        for (Annotation ann : annotations) {
            if (ann.getIAClass() != null) classes.add(ann.getIAClass());
        }
        // TODO: we should find out how/where bunk iaClass values are getting set
        // and stop the via isValidIAClass() or similar
        // also should be considered for any data integrity/repair tools
        classes.remove("____");
        return classes;
    }

    // all an enc's annotations on a given asset (might be multiple if parts are involved)
    public List<Annotation> getAnnotations(MediaAsset ma) {
        List<Annotation> anns = new ArrayList<Annotation>();

        for (Annotation ann : getAnnotations()) {
            if (ann.getMediaAsset() == ma) anns.add(ann);
        }
        return anns;
    }

    public void setAnnotations(ArrayList<Annotation> anns) {
        annotations = anns;
    }

    public void addAnnotations(List<Annotation> anns) {
        if (annotations == null) annotations = new ArrayList<Annotation>();
        for (Annotation ann : anns) {
            if (!annotations.contains(ann)) annotations.add(ann);
        }
    }

    public void addAnnotation(Annotation ann) {
        if (annotations == null) annotations = new ArrayList<Annotation>();
        if (!annotations.contains(ann)) annotations.add(ann);
    }

    public void useAnnotationsForMatching(boolean use) {
        if (getAnnotations() != null && getAnnotations().size() >= 1) {
            for (Annotation ann : getAnnotations()) {
                ann.setMatchAgainst(use);
            }
        }
    }

    public Annotation getAnnotationWithKeyword(String word) {
        System.out.println("getAnnotationWithKeyword called for " + word);
        System.out.println("getAnnotationWithKeyword called, annotations = " + annotations);
        if (annotations == null) return null;
        for (Annotation ann : annotations) {
            if (ann == null) continue;
            MediaAsset ma = ann.getMediaAsset();
            if (ma != null && ma.hasKeyword(word)) return ann;
        }
        return null;
    }

    // pretty much only useful for frames pulled from video (after detection, to be made into encounters)
    public static List<Encounter> collateFrameAnnotations(List<Annotation> anns,
        Shepherd myShepherd) {
        if ((anns == null) || (anns.size() < 1)) return null;
        // Determine skipped frames before another encounter should be made.
        int minGapSize = 4;
        try {
            String gapFromProperties = IA.getProperty(myShepherd.getContext(),
                "newEncounterFrameGap");
            if (gapFromProperties != null) {
                minGapSize = Integer.parseInt(gapFromProperties);
            }
        } catch (NumberFormatException nfe) {}
        SortedMap<Integer, List<Annotation> > ordered = new TreeMap<Integer, List<Annotation> >();
        MediaAsset parentRoot = null;
        for (Annotation ann : anns) {
            System.out.println("========================== >>>>>> " + ann);
            if (ann.getFeatures().get(0).isUnity()) continue; // makes big assumption there is one-and-only-one feature btw (detection should have
                                                              // bbox)
            MediaAsset ma = ann.getMediaAsset();
            if (parentRoot == null) parentRoot = ma.getParentRoot(myShepherd);
            System.out.println("   -->>> ma = " + ma);
            if (!ma.hasLabel("_frame") || (ma.getParentId() == null)) continue; // nope thx
            int offset = ma.getParameters().optInt("extractOffset", -1);
            System.out.println("   -->>> offset = " + offset);
            if (offset < 0) continue;
            if (ordered.get(offset) == null) ordered.put(offset, new ArrayList<Annotation>());
            ordered.get(offset).add(ann);
        }
        if (ordered.size() < 1) return null; // none used!

        // now construct Encounters based upon spacing of frame-clusters
        List<Encounter> newEncs = new ArrayList<Encounter>();
        int prevOffset = -1;
        int groupsMade = 1;
        ArrayList<Annotation> tmpAnns = new ArrayList<Annotation>();
        for (Integer i : ordered.keySet()) {
            if ((prevOffset > -1) && ((i - prevOffset) >= minGapSize)) {
                Encounter newEnc = __encForCollate(tmpAnns, parentRoot);
                if (newEnc != null) { // null means none of the frames met minimum detection confidence
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
        // deal with dangling tmpAnns content
        if (tmpAnns.size() > 0) {
            Encounter newEnc = __encForCollate(tmpAnns, parentRoot);
            if (newEnc != null) {
                newEnc.setDynamicProperty("frameSplitNumber", Integer.toString(groupsMade + 1));
                newEncs.add(newEnc);
                System.out.println(" (final)cluster [" + groupsMade + "] -> " + newEnc);
                groupsMade++;
            }
        }
        return newEncs;
    }

    // this is really only for above method
    private static Encounter __encForCollate(ArrayList<Annotation> tmpAnns, MediaAsset parentRoot) {
        if ((tmpAnns == null) || (tmpAnns.size() < 1)) return null;
        // make sure we even can use these annots first
        double bestConfidence = 0.0;
        for (Annotation ann : tmpAnns) {
            if ((ann.getFeatures() == null) || (ann.getFeatures().size() < 1) ||
                (ann.getFeatures().get(0).getParameters() == null)) continue;
            double conf = ann.getFeatures().get(0).getParameters().optDouble("detectionConfidence",
                -1.0);
            if (conf > bestConfidence) bestConfidence = conf;
        }
        if (bestConfidence < ENCOUNTER_AUTO_SOURCE_CONFIDENCE_CUTOFF) {
            System.out.println("[INFO] bestConfidence=" + bestConfidence +
                " below threshold; rejecting 1 enc from " + parentRoot);
            return null;
        }
        Encounter newEnc = new Encounter(tmpAnns);
        newEnc.setState(STATE_AUTO_SOURCED);
        newEnc.zeroOutDate(); // do *not* want it using the video source date
        newEnc.setDynamicProperty("bestDetectionConfidence", Double.toString(bestConfidence));
        if (parentRoot == null) {
            newEnc.setSubmitterName("Unknown video source");
            newEnc.addComments("<i>unable to determine video source - possibly YouTube error?</i>");
        } else {
            newEnc.addComments("<p>YouTube ID: <b>" + parentRoot.getParameters().optString("id") +
                "</b></p>");
            String consolidatedRemarks =
                "<p>Auto-sourced from YouTube Parent Video: <a href=\"https://www.youtube.com/watch?v="
                + parentRoot.getParameters().optString("id") + "\">" +
                parentRoot.getParameters().optString("id") + "</a></p>";
            // set the video ID as the EventID for distinct access later
            newEnc.setEventID("youtube:" + parentRoot.getParameters().optString("id"));
            if ((parentRoot.getMetadata() != null) &&
                (parentRoot.getMetadata().getData() != null)) {
                if (parentRoot.getMetadata().getData().optJSONObject("basic") != null) {
                    newEnc.setSubmitterName(parentRoot.getMetadata().getData().getJSONObject(
                        "basic").optString("author_name", "[unknown]") + " (by way of YouTube)");
                    consolidatedRemarks += "<p>From YouTube video: <i>" +
                        parentRoot.getMetadata().getData().getJSONObject("basic").optString("title",
                        "[unknown]") + "</i></p>";
                    newEnc.addComments(consolidatedRemarks);

                    // add a dynamic property to make a quick link to the video
                }
                if (parentRoot.getMetadata().getData().optJSONObject("detailed") != null) {
                    String desc = "<p>" +
                        parentRoot.getMetadata().getData().getJSONObject("detailed").optString(
                        "description", "[no description]") + "</p>";
                    if (parentRoot.getMetadata().getData().getJSONObject("detailed").optJSONArray(
                        "tags") != null) {
                        desc += "<p><b>tags:</b> " +
                            parentRoot.getMetadata().getData().getJSONObject(
                            "detailed").getJSONArray("tags").toString() + "</p>";
                    }
                    consolidatedRemarks += desc;
                }
            }
            newEnc.setOccurrenceRemarks(consolidatedRemarks);
        }
        return newEnc;
    }

    // convenience method
    public ArrayList<MediaAsset> getMedia() {
        ArrayList<MediaAsset> m = new ArrayList<MediaAsset>();

        if ((annotations == null) || (annotations.size() < 1)) return m;
        for (Annotation ann : annotations) {
            if (ann == null) continue; // really weird that this happens sometimes
            MediaAsset ma = ann.getMediaAsset();
            if (ma != null) m.add(ma);
        }
        return m;
    }

    public MediaAsset getMediaAssetByFilename(String filename) {
        if (!Util.stringExists(filename)) return null;
        for (MediaAsset ma : getMedia()) {
            if (Util.stringsEqualish(filename, ma.getFilename())) return ma;
        }
        return null;
    }

    // only checks top-level MediaAssets, not children or resized images
    public boolean hasTopLevelMediaAsset(int id) {
        return (indexOfMediaAsset(id) >= 0);
    }

    // finds the index of the MA we're looking for
    public int indexOfMediaAsset(int id) {
        if (annotations == null) return -1;
        for (int i = 0; i < annotations.size(); i++) {
            MediaAsset ma = annotations.get(i).getMediaAsset();
            if (ma == null) continue;
            if (ma.getIdInt() == id) return i;
        }
        return -1;
    }

    // creates a new annotation and attaches the asset
    public void addMediaAsset(MediaAsset ma) {
        Annotation ann = new Annotation(getTaxonomyString(), ma);

        if (annotations == null) annotations = new ArrayList<Annotation>();
        annotations.add(ann);
    }

    public void removeAnnotation(Annotation ann) {
        if (annotations == null) return;
        annotations.remove(ann);
    }

    public void removeAnnotation(int index) {
        annotations.remove(index);
    }

    // this removes an Annotation from Encounter (and from its MediaAsset!!) and replaces it with a new one
    // please note: the oldAnn gets killed off (not orphaned)
    public void replaceAnnotation(Annotation oldAnn, Annotation newAnn) {
        oldAnn.detachFromMediaAsset();
        // note: newAnn should already attached to a MediaAsset
        removeAnnotation(oldAnn);
        addAnnotation(newAnn);
    }

    public void removeMediaAsset(MediaAsset ma) {
        removeAnnotation(indexOfMediaAsset(ma.getIdInt()));
    }

    // this is a kinda hacky way to find media ... really used by encounter.jsp now but likely should go away?
    public ArrayList<MediaAsset> findAllMediaByFeatureId(Shepherd myShepherd, String[] featureIds) {
        ArrayList<MediaAsset> mas = new ArrayList<MediaAsset>();

        for (MediaAsset ma : getMedia()) {
            if (ma.hasFeatures(featureIds)) mas.add(ma);
            ArrayList<MediaAsset> kids = ma.findChildren(myShepherd); // note: does not recurse, but... meh?
            if ((kids == null) || (kids.size() < 1)) continue;
            for (MediaAsset kma : kids) {
                if (kma.hasFeatures(featureIds)) mas.add(kma);
            }
        }
        return mas;
    }

    // down-n-dirty with no myShepherd passed!  :/
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

    public boolean hasKeyword(Keyword word) {
        int imagesSize = images.size();

        for (int i = 0; i < imagesSize; i++) {
            SinglePhotoVideo image = images.get(i);
            if (image.getKeywords().contains(word)) { return true; }
        }
        return false;
    }

    public Set<Keyword> getMediaAssetKeywords() {
        Set<Keyword> mak = new HashSet<Keyword>();

        for (MediaAsset ma : this.getMedia()) {
            ArrayList<Keyword> kws = ma.getKeywords();
            if (kws != null) mak.addAll(kws);
        }
        return mak;
    }

    public String getState() { return state; }

    public void setState(String newState) { this.state = newState; }

    // DO NOT USE - LEGACY MIGRATION ONLY
    /*
       public boolean getApproved(){return approved;}
       public boolean getUnidentifiable(){return unidentifiable;}
     */
    public Vector getOldAdditionalImageNames() { return additionalImageNames; }

    public Double getLatitudeAsDouble() { return decimalLatitude; }
    public Double getLongitudeAsDouble() { return decimalLongitude; }

    public boolean hasMeasurements() {
        if ((measurements != null) && (measurements.size() > 0)) {
            int numMeasurements = measurements.size();
            for (int i = 0; i < numMeasurements; i++) {
                Measurement m = measurements.get(i);
                if (m.getValue() != null) { return true; }
            }
        }
        return false;
    }

    public boolean hasMeasurement(String type) {
        if ((measurements != null) && (measurements.size() > 0)) {
            int numMeasurements = measurements.size();
            for (int i = 0; i < numMeasurements; i++) {
                Measurement m = measurements.get(i);
                if ((m.getValue() != null) && (m.getType().equals(type))) { return true; }
            }
        }
        return false;
    }

    public boolean hasBiologicalMeasurement(String type) {
        if ((tissueSamples != null) && (tissueSamples.size() > 0)) {
            int numTissueSamples = tissueSamples.size();
            for (int i = 0; i < numTissueSamples; i++) {
                TissueSample ts = tissueSamples.get(i);
                if (ts.getBiologicalMeasurement(type) != null) {
                    BiologicalMeasurement bm = ts.getBiologicalMeasurement(type);
                    if (bm.getValue() != null) { return true; }
                }
            }
        }
        return false;
    }

    // Returns the first measurement of the specified type
    public Measurement getMeasurement(String type) {
        if ((measurements != null) && (measurements.size() > 0)) {
            int numMeasurements = measurements.size();
            for (int i = 0; i < numMeasurements; i++) {
                Measurement m = measurements.get(i);
                if ((m.getValue() != null) && (m.getType().equals(type))) { return m; }
            }
        }
        return null;
    }

    public Map<String, BiologicalMeasurement> getBiologicalMeasurementsByType() {
        Map<String, BiologicalMeasurement> meas = new HashMap<String, BiologicalMeasurement>();

        for (MeasurementDesc mdesc : Util.findBiologicalMeasurementDescs("en", "context0")) {
            BiologicalMeasurement bm = getBiologicalMeasurement(mdesc.getType());
            if (bm != null) meas.put(mdesc.getType(), bm);
        }
        return meas;
    }

    public BiologicalMeasurement getBiologicalMeasurement(String type) {
        if (tissueSamples != null) {
            int numTissueSamples = tissueSamples.size();
            for (int y = 0; y < numTissueSamples; y++) {
                TissueSample ts = tissueSamples.get(y);
                if ((ts.getGeneticAnalyses() != null) && (ts.getGeneticAnalyses().size() > 0)) {
                    int numMeasurements = ts.getGeneticAnalyses().size();
                    for (int i = 0; i < numMeasurements; i++) {
                        GeneticAnalysis m = ts.getGeneticAnalyses().get(i);
                        if (m.getAnalysisType().equals("BiologicalMeasurement")) {
                            BiologicalMeasurement f = (BiologicalMeasurement)m;
                            if ((f.getMeasurementType().equals(type)) && (f.getValue() != null)) {
                                return f;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    public String getCountry() { return country; }

    public void setCountry(String newCountry) {
        if (newCountry != null) { country = newCountry; } else { country = null; }
    }

    public void setOccurrenceID(String vet) {
        if (vet != null) { this.occurrenceID = vet; } else { this.occurrenceID = null; }
    }

    public String getOccurrenceID() { return occurrenceID; }

    public Occurrence getOccurrence(Shepherd myShepherd) {
        return myShepherd.getOccurrence(this);
    }

    public DateTime getOccurrenceDateTime(Shepherd myShepherd) {
        Occurrence occ = this.getOccurrence(myShepherd);

        if (occ == null) return null;
        return occ.getDateTime();
    }

    public boolean hasSinglePhotoVideoByFileName(String filename) {
        int numImages = images.size();

        for (int i = 0; i < numImages; i++) {
            SinglePhotoVideo single = images.get(i);
            if (single.getFilename().trim().toLowerCase().equals(filename.trim().toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    // convenience function to Collaboration permissions
    public boolean canUserAccess(HttpServletRequest request) {
        return Collaboration.canUserAccessEncounter(this, request);
    }

    public boolean canUserAccess(User user, String context) {
        if (user == null) return false;
        // see comment below on canUserEdit(); substituting isUserOwner() now
        // if (canUserEdit(user)) return true;
        if (isUserOwner(user)) return true;
        String username = user.getUsername();
        if (username == null) return false;
        return Collaboration.canUserAccessEncounter(this, context, username);
    }

    /*
       this really is ugly cuz what is "view" vs "access", but i do NOT want to futz with canUserAccess() and
       cause unintended consequences. so, for now, canUserView() is pretty much exclusively for search
     */
    public boolean canUserView(User user, Shepherd myShepherd) {
        return (user != null) && (user.isAdmin(myShepherd) || this.canUserAccess(user,
                myShepherd.getContext()));
    }

    // as part of 10.9, canUserEdit() was modified. it was not
/*
    public boolean canUserEdit(User user) {
        return isUserOwner(user);
    }
 */

    // rewrite for 10.9 tries to actually do the right thing and (maybe) make sense
    @Override public boolean canUserEdit(User user, Shepherd myShepherd) {
        if (user == null) return false;
        if (isUserOwner(user)) return true;
        if (user.isAdmin(myShepherd)) return true;
        if (Collaboration.canEditEncounter(this, user, myShepherd.getContext())) return true;
        // TODO there seems to be some legacy stuff about roles based on location. is this real?
        return false;
    }

    public boolean isUserOwner(User user) { // the definition of this might change?
        if (user == null) return false;
        if ((submitters != null) && submitters.contains(user)) return true;
        if ((submitterID != null) && submitterID.equals(user.getUsername())) return true;
        return false;
    }

    // new logic means we only need users who are in collab with submitting user
    // and if public, we dont need to do this at all
    public List<String> userIdsWithViewAccess(Shepherd myShepherd) {
        List<String> ids = new ArrayList<String>();

        if (this.isPubliclyReadable()) return ids;
        List<Collaboration> collabs = Collaboration.collaborationsForUser(myShepherd,
            this.getSubmitterID());
        for (Collaboration collab : collabs) {
            User user = myShepherd.getUser(collab.getOtherUsername(this.getSubmitterID()));
            if (user != null) ids.add(user.getId());
        }
        return ids;
    }

/*
    public List<String> userIdsWithEditAccess(Shepherd myShepherd) {
        List<String> ids = new ArrayList<String>();

        for (User user : myShepherd.getUsersWithUsername()) {
            if ((user.getId() != null) && this.canUserEdit(user)) ids.add(user.getId());
        }
        return ids;
    }
 */
    public JSONObject sanitizeJson(HttpServletRequest request, JSONObject jobj)
    throws JSONException {
        boolean fullAccess = this.canUserAccess(request);
        String useProjectContext = "false";

        if (request.getParameter("useProjectContext") != null) {
            useProjectContext = request.getParameter("useProjectContext");
        }
        if (fullAccess) {
            if (this.individual != null) {
                jobj.put("individualID", this.individual.getIndividualID());
                if ("true".equals(useProjectContext)) {
                    jobj.put("displayName", this.individual.getDisplayName(request));
                } else {
                    jobj.put("displayName", this.individual.getDisplayName());
                }
            }
            return jobj;
        }
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

    public JSONObject decorateJsonNoAnnots(HttpServletRequest request, JSONObject jobj)
    throws JSONException {
        jobj.put("location", this.getLocation());
        // these are for convenience, like .hasImages above (for use in table building e.g.)
        if ((this.getTissueSamples() != null) && (this.getTissueSamples().size() > 0))
            jobj.put("hasTissueSamples", true);
        if (this.hasMeasurements()) jobj.put("hasMeasurements", true);
        return jobj;
    }

    public JSONObject decorateJson(HttpServletRequest request, JSONObject jobj)
    throws JSONException {
        jobj = decorateJsonNoAnnots(request, jobj);

        jobj.put("_imagesNote",
            ".images have been deprecated!  long live MediaAssets!  (see: .annotations)");

        boolean fullAccess = this.canUserAccess(request);
        if ((this.getAnnotations() != null) && (this.getAnnotations().size() > 0)) {
            jobj.put("hasAnnotations", true);
            JSONArray jarr = new JSONArray();
            for (Annotation ann : this.getAnnotations()) {
                jarr.put(ann.sanitizeJson(request, fullAccess));
            }
            jobj.put("annotations", jarr);
        }
        return jobj;
    }

    public JSONObject uiJson(HttpServletRequest request)
    throws JSONException {
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
        return request.getScheme() + "://" + CommonConfiguration.getURLLocation(request) +
                   "/encounters/encounter.jsp?number=" + this.getCatalogNumber();
    }

    /**
     * returns an array of the MediaAsset sanitized JSON, because whenever UI queries our DB (regardless of class query), all they want in return are
     * MediaAssets TODO: decorate with metadata
     **/
    public org.datanucleus.api.rest.orgjson.JSONArray sanitizeMedia(HttpServletRequest request)
    throws org.datanucleus.api.rest.orgjson.JSONException {
        org.datanucleus.api.rest.orgjson.JSONArray jarr =
            new org.datanucleus.api.rest.orgjson.JSONArray();
        boolean fullAccess = this.canUserAccess(request);
        if ((this.getAnnotations() != null) && (this.getAnnotations().size() > 0)) {
            for (Annotation ann : this.getAnnotations()) {
                jarr.put(ann.sanitizeMedia(request, fullAccess));
            }
        }
        return jarr;
    }

    // this simple version makes some assumptions: you already have list of collabs, and it is not visible
    public String collaborationLockHtml(List<Collaboration> collabs) {
        Collaboration c = Collaboration.findCollaborationWithUser(this.getAssignedUsername(),
            collabs);
        String collabClass = "pending";

        if ((c == null) || (c.getState() == null)) {
            collabClass = "new";
        } else if (c.getState().equals(Collaboration.STATE_REJECTED)) {
            collabClass = "blocked";
        }
        return "<div class=\"row-lock " + collabClass +
                   " collaboration-button\" data-collabowner=\"" + this.getAssignedUsername() +
                   "\" data-collabownername=\"" + this.getAssignedUsername() + "\">&nbsp;</div>";
    }

    // pass in a Vector of Encounters, get out a list that the user can NOT see
    public static Vector blocked(Vector encs, HttpServletRequest request) {
        Vector blk = new Vector();

        for (int i = 0; i < encs.size(); i++) {
            Encounter e = (Encounter)encs.get(i);
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
    // see also: future, MediaAssets
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

    // this probably needs a better name and should allow for something more like an ordered list; that said,
    // knowing we can always try to get THE ONE is probably useful too
    public MediaAsset getPrimaryMediaAsset() {
        ArrayList<MediaAsset> mas = getMedia();

        if (mas.size() < 1) return null;
        // here we could walk thru and find keywords, for example
        return mas.get(0);
    }

    public boolean restAccess(HttpServletRequest request, org.json.JSONObject jsonobj)
    throws Exception {
        ApiAccess access = new ApiAccess();

        System.out.println("hello i am in restAccess() on Encounter");

        String fail = access.checkRequest(this, request, jsonobj);
        System.out.println("fail -----> " + fail);
        if (fail != null) throw new Exception(fail);
        return true;
    }

///////// this is bunk now: see fix for findAllByMediaAsset() if you need this
    public static Encounter findByMediaAsset(MediaAsset ma, Shepherd myShepherd) {
        String queryString =
            "SELECT FROM org.ecocean.Encounter WHERE annotations.contains(ann) && ann.mediaAsset.id =="
            + ma.getId();
        Encounter returnEnc = null;
        Query query = myShepherd.getPM().newQuery(queryString);
        List results = (List)query.execute();

        if ((results != null) && (results.size() >= 1)) {
            returnEnc = (Encounter)results.get(0);
        }
        query.closeAll();
        return returnEnc;
    }

    public static List<Encounter> findAllByMediaAsset(MediaAsset ma, Shepherd myShepherd) {
        List<Encounter> returnEncs = new ArrayList<Encounter>();

        try {
            String queryString =
                "SELECT FROM org.ecocean.Encounter WHERE annotations.contains(ann) && ann.features.contains(feat) && mediaAsset.features.contains(feat) && mediaAsset.id =="
                + ma.getId() +
                " VARIABLES org.ecocean.media.MediaAsset mediaAsset; org.ecocean.Annotation ann; org.ecocean.media.Feature feat";
            Query query = myShepherd.getPM().newQuery(queryString);
            Collection results = (Collection)query.execute();
            returnEncs = new ArrayList<Encounter>(results);
            query.closeAll();
        } catch (Exception e) {}
        return returnEncs;
    }

    public String getPrefixForLocationID() { // convenience function
        return LocationID.getPrefixForLocationID(this.getLocationID(), null);
    }

    public int getPrefixDigitPaddingForLocationID() { // convenience function
        return LocationID.getPrefixDigitPaddingForLocationID(this.getLocationID(), null);
    }

    public static Encounter findByAnnotation(Annotation annot, Shepherd myShepherd) {
        String queryString =
            "SELECT FROM org.ecocean.Encounter WHERE annotations.contains(ann) && ann.id =='" +
            annot.getId() + "'";
        Encounter returnEnc = null;
        Query query = myShepherd.getPM().newQuery(queryString);
        List results = (List)query.execute();

        if ((results != null) && (results.size() >= 1)) {
            if (results.size() > 1)
                System.out.println("WARNING: Encounter.findByAnnotation() found " + results.size() +
                    " Encounters that contain Annotation " + annot.getId());
            returnEnc = (Encounter)results.get(0);
        }
        query.closeAll();
        return returnEnc;
    }

    public static Encounter findByAnnotationId(String annid, Shepherd myShepherd) {
        Annotation ann = ((Annotation)(myShepherd.getPM().getObjectById(
            myShepherd.getPM().newObjectIdInstance(Annotation.class, annid), true)));

        if (ann == null) return null;
        return findByAnnotation(ann, myShepherd);
    }

    public static ArrayList<Encounter> getEncountersForMatching(String taxonomyString,
        Shepherd myShepherd) {
        if (_matchEncounterCache.get(taxonomyString) != null)
            return _matchEncounterCache.get(taxonomyString);
        ArrayList<Encounter> encs = new ArrayList<Encounter>();
        String queryString = "SELECT FROM org.ecocean.media.MediaAsset WHERE !features.isEmpty()";
        Query query = myShepherd.getPM().newQuery(queryString);
        List results = (List)query.execute();
        for (int i = 0; i < results.size(); i++) {
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
    this section are intentionally hacky backwards-compatible ways to get spots on an encounter in the new world of Features/Annotations/MediaAssets
       ... do not use these, of course... and SOON we must weed out all the encounter-based-spot calls from everywhere and clean all this mess up!
 */
    public ArrayList<SuperSpot> HACKgetSpots() {
        return HACKgetAnySpots("spotsLeft");
    }

    public ArrayList<SuperSpot> HACKgetRightSpots() {
        return HACKgetAnySpots("spotsRight");
    }

    public ArrayList<SuperSpot> HACKgetAnySpots(String which) {
        ArrayList<MediaAsset> mas = findAllMediaByFeatureId(
            new String[] { "org.ecocean.flukeEdge.edgeSpots", "org.ecocean.dorsalEdge.edgeSpots" });

        if ((mas == null) || (mas.size() < 1)) return new ArrayList<SuperSpot>();
        for (Feature f : mas.get(0).getFeatures()) {
            if (f.isType("org.ecocean.flukeEdge.edgeSpots") ||
                f.isType("org.ecocean.dorsalEdge.edgeSpots")) {
                if (f.getParameters() != null)
                    return SuperSpot.listFromJSONArray(f.getParameters().optJSONArray(which));
            }
        }
        return new ArrayList<SuperSpot>();
    }

    // err, i think ref spots are the same right or left.... at least for flukes/dorsals.  :/  good luck with mantas and whalesharks!
    public ArrayList<SuperSpot> HACKgetAnyReferenceSpots() {
        ArrayList<MediaAsset> mas = findAllMediaByFeatureId(
            new String[] { "org.ecocean.flukeEdge.referenceSpots",
                           "org.ecocean.referenceEdge.edgeSpots" });

        if ((mas == null) || (mas.size() < 1)) return new ArrayList<SuperSpot>();
        for (Feature f : mas.get(0).getFeatures()) {
            if (f.isType("org.ecocean.flukeEdge.referenceSpots") ||
                f.isType("org.ecocean.dorsalEdge.referenceSpots")) {
                if (f.getParameters() != null)
                    return SuperSpot.listFromJSONArray(f.getParameters().optJSONArray("spots"));
            }
        }
        return new ArrayList<SuperSpot>();
    }

    // note this sets some things (e.g. species) which might (should!) need to be adjusted after, e.g. with setSpeciesFromAnnotations()
    public Encounter cloneWithoutAnnotations(Shepherd myShepherd) {
        Encounter enc = new Encounter(this.day, this.month, this.year, this.hour, this.minutes,
            this.size_guess, this.verbatimLocality);

        enc.setCatalogNumber(Util.generateUUID());
        System.out.println("NOTE: cloneWithoutAnnotations(" + this.catalogNumber + ") -> " +
            enc.getCatalogNumber());
        enc.setGenus(this.getGenus());
        enc.setSpecificEpithet(this.getSpecificEpithet());
        enc.setDecimalLatitude(this.getDecimalLatitudeAsDouble());
        enc.setDecimalLongitude(this.getDecimalLongitudeAsDouble());
        // just going to go ahead and go nuts here and copy most "logical"(?) things.  reset on clone if needed
        enc.setSubmitterID(this.getSubmitterID());
        enc.setSubmitters(this.submitters);
        enc.setPhotographers(this.photographers);
        enc.setSex(this.getSex());
        enc.setLocationID(this.getLocationID());
        enc.setVerbatimLocality(this.getVerbatimLocality());
        if (this.getCountry() != null && !"".equals(this.getCountry())) {
            enc.setCountry(this.getCountry());
        }
        Occurrence occ = myShepherd.getOccurrence(this);
        if (occ != null) {
            occ.addEncounter(enc);
            enc.setOccurrenceID(occ.getOccurrenceID());
        }
        // WB-1949: clone into same projects too
        ArrayList<Project> projects = myShepherd.getAllProjectsForEncounter(this);
        if (projects != null) {
            for (Project proj : projects) {
                proj.addEncounter(enc);
            }
        }
        enc.setRecordedBy(this.getRecordedBy());
        enc.setState(this.getState()); // not too sure about this one?

        enc.setAlternateID(this.getAlternateID());
        enc.setOccurrenceRemarks(this.getOccurrenceRemarks());
        enc.addComments("NOTE: cloneWithoutAnnotations(" + this.catalogNumber + ") -> " +
            enc.getCatalogNumber());

        ImportTask itask = getImportTask(myShepherd);
        if (itask != null) itask.addEncounter(enc);
        return enc;
    }

    // for convenience
    public ImportTask getImportTask(Shepherd myShepherd) {
        return myShepherd.getImportTaskForEncounter(this);
    }

    // this is a special state only used now for match.jsp but basically means the data should be mostly hidden and soon deleted, roughly speaking???
    // TODO: figure out what this really means
    public void setMatchingOnly() {
        this.setState(STATE_MATCHING_ONLY);
    }

    // ann is the Annotation that was created after IA detection.  mostly this is just to notify... someone
    // note: this is for singly-made encounters; see also Occurrence.fromDetection()
    public void detectedAnnotation(Shepherd myShepherd, Annotation ann) {
        System.out.println(">>>>> detectedAnnotation() on " + this);
    }

    /*
       note: these are baby steps into proper ownership of Encounters.  a similar (but cleaner) attempt is done in MediaAssets... however, really this
          probably should be upon some (mythical) BASE CLASS!!!! ... for now, this Encounter variation kinda fudges with existing "ownership" stuff,
          namely, the submitterID - which maps (in theory!) to a User username. TODO: much much much  ... incl call via constructor maybe ??  etc.
     */
    // NOTE: not going to currently persist the AccessControl object yet, but create on the fly...  clever? stupid?
    public AccessControl getAccessControl() {
        if ((submitterID == null) || submitterID.equals("")) return new AccessControl(); // not sure if we really have some "" but lets be safe
        return new AccessControl(submitterID);
    }

    public void setAccessControl(HttpServletRequest request) { // really just setting submitterID duh
        this.submitterID = AccessControl.simpleUserString(request); // null if anon
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

    public boolean hasMediaFromAssetStoreType(AssetStoreType aType) {
        System.out.println("Entering Encounter.hasMediaFromAssetStoreType");
        if (getMediaAssetsOfType(aType).size() > 0) { return true; }
        return false;
    }

    public ArrayList<MediaAsset> getMediaAssetsOfType(AssetStoreType aType) {
        System.out.println("Entering Encounter.getMediaAssetsOfType");
        ArrayList<MediaAsset> results = new ArrayList<MediaAsset>();
        try {
            ArrayList<MediaAsset> assets = getMedia();
            int numAssets = assets.size();
            for (int i = 0; i < numAssets; i++) {
                MediaAsset ma = assets.get(i);
                if (ma.getStore().getType() == aType) { results.add(ma); }
            }
        } catch (Exception e) { e.printStackTrace(); }
        System.out.println("Exiting Encounter.getMediaAssetsOfType with this num results: " +
            results.size());
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
            observations = arr;
        } else {
            observations.addAll(arr);
        }
    }

    public void addObservation(Observation obs) {
        boolean found = false;

        // System.out.println("Adding Observation in Base Class... : "+obs.toString());
        if (observations != null && observations.size() > 0) {
            for (Observation ob : observations) {
                if (ob.getName() != null) {
                    if (ob.getName().toLowerCase().trim().equals(
                        obs.getName().toLowerCase().trim())) {
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
                        System.out.println("Match! Trying to delete Observation " + name +
                            " at index " + counter);
                        observations.remove(counter);
                        break;
                    }
                }
                counter++;
            }
        }
    }

    public List<User> getSubmitters() {
        return submitters;
    }

    public List<User> getInformOthers() {
        return informOthers;
    }

    public List<String> getSubmitterEmails() {
        ArrayList<String> listy = new ArrayList<String>();
        ArrayList<User> subs = new ArrayList<User>();

        if (getSubmitters() != null) subs.addAll(getSubmitters());
        int numUsers = subs.size();
        for (int k = 0; k < numUsers; k++) {
            User use = subs.get(k);
            if ((use.getEmailAddress() != null) && (!use.getEmailAddress().trim().equals(""))) {
                listy.add(use.getEmailAddress());
            }
        }
        return listy;
    }

    public List<String> getHashedSubmitterEmails() {
        ArrayList<String> listy = new ArrayList<String>();
        ArrayList<User> subs = new ArrayList<User>();

        if (getSubmitters() != null) subs.addAll(getSubmitters());
        int numUsers = subs.size();
        for (int k = 0; k < numUsers; k++) {
            User use = subs.get(k);
            if ((use.getHashedEmailAddress() != null) &&
                (!use.getHashedEmailAddress().trim().equals(""))) {
                listy.add(use.getHashedEmailAddress());
            }
        }
        return listy;
    }

    public List<User> getPhotographers() {
        return photographers;
    }

    public List<String> getPhotographerEmails() {
        ArrayList<String> listy = new ArrayList<String>();
        ArrayList<User> subs = new ArrayList<User>();

        if (getPhotographers() != null) subs.addAll(getPhotographers());
        int numUsers = subs.size();
        for (int k = 0; k < numUsers; k++) {
            User use = subs.get(k);
            if ((use.getEmailAddress() != null) && (!use.getEmailAddress().trim().equals(""))) {
                listy.add(use.getEmailAddress());
            }
        }
        return listy;
    }

    public List<String> getInformOthersEmails() {
        ArrayList<String> listy = new ArrayList<String>();
        ArrayList<User> subs = new ArrayList<User>();

        if (getInformOthers() != null) subs.addAll(getInformOthers());
        int numUsers = subs.size();
        for (int k = 0; k < numUsers; k++) {
            User use = subs.get(k);
            if ((use.getEmailAddress() != null) && (!use.getEmailAddress().trim().equals(""))) {
                listy.add(use.getEmailAddress());
            }
        }
        return listy;
    }

    public List<String> getHashedPhotographerEmails() {
        ArrayList<String> listy = new ArrayList<String>();
        ArrayList<User> subs = new ArrayList<User>();

        if (getPhotographers() != null) subs.addAll(getPhotographers());
        int numUsers = subs.size();
        for (int k = 0; k < numUsers; k++) {
            User use = subs.get(k);
            if ((use.getHashedEmailAddress() != null) &&
                (!use.getHashedEmailAddress().trim().equals(""))) {
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

    public void removeSubmitter(User user) {
        if ((user == null) || (submitters == null)) return;
        submitters.remove(user);
    }

    public void addPhotographer(User user) {
        if (user == null) return;
        if (photographers == null) photographers = new ArrayList<User>();
        if (!photographers.contains(user)) photographers.add(user);
    }

    public void setSubmitters(List<User> submitters) {
        if (submitters == null) { this.submitters = null; } else {
            this.submitters = submitters;
        }
    }

    public void setPhotographers(List<User> photographers) {
        if (photographers == null) { this.photographers = null; } else {
            this.photographers = photographers;
        }
    }

    public void removePhotographer(User user) {
        if ((user == null) || (photographers == null)) return;
        photographers.remove(user);
    }

    public void addInformOther(User user) {
        if (user == null) return;
        if (informOthers == null) informOthers = new ArrayList<User>();
        if (!informOthers.contains(user)) informOthers.add(user);
    }

    public void setInformOthers(List<User> users) {
        if (users == null) { this.informOthers = null; } else {
            this.informOthers = users;
        }
    }

    public void removeInformOther(User user) {
        if ((user == null) || (informOthers == null)) return;
        informOthers.remove(user);
    }

    public static List<String> getIndividualIDs(Collection<Encounter> encs) {
        Set<String> idSet = new HashSet<String>();

        for (Encounter enc : encs) {
            if (enc.hasMarkedIndividual()) idSet.add(enc.getIndividualID());
        }
        return Util.asSortedList(idSet);
    }

    public void refreshAnnotationLiteTaxonomy() {
        if (!this.hasAnnotations()) return;
        String tax = this.getTaxonomyString();
        for (Annotation ann : this.annotations) {
            ann.refreshLiteTaxonomy(tax);
        }
    }

    public void refreshAnnotationLiteIndividual() {
        if (!this.hasAnnotations()) return;
        String indivId = "____";
        if (this.individual != null) indivId = this.individual.getIndividualID();
        for (Annotation ann : this.annotations) {
            ann.refreshLiteIndividual(indivId);
        }
    }

    public int hashCode() { // we need this along with equals() for collections methods (contains etc) to work!!
        if (this.getCatalogNumber() == null) return Util.generateUUID().hashCode(); // random(ish) so we dont get two identical for null values
        return this.getCatalogNumber().hashCode();
    }

    // sadly, this mess needs to carry on the tradition set up in User.isUsernameAnonymous()
    // thanks to the logic in Collaboration.canUserAccessOwnedObject()
    public boolean isPubliclyReadable() {
        if (!Collaboration.securityEnabled("context0")) return true;
        return User.isUsernameAnonymous(this.submitterID);
    }

    public boolean getOpensearchProcessPermissions() {
        return opensearchProcessPermissions;
    }

    public void setOpensearchProcessPermissions(boolean value) {
        opensearchProcessPermissions = value;
    }

    // wrapper for below, that checks if we really need to be run
    public static void opensearchIndexPermissionsBackground(Shepherd myShepherd) {
        boolean runIt = false;
        Long lastRun = OpenSearch.getPermissionsTimestamp(myShepherd);
        long now = System.currentTimeMillis();

        if ((lastRun == null) ||
            ((now - lastRun) > OpenSearch.BACKGROUND_PERMISSIONS_MAX_FORCE_MINUTES * 60000)) {
            System.out.println(
                "opensearchIndexPermissionsBackground: forced run due to max time since previous");
            runIt = true;
        }
        boolean needed = OpenSearch.getPermissionsNeeded(myShepherd);
        if (needed && !runIt) {
            System.out.println("opensearchIndexPermissionsBackground: running due to needed=true");
            runIt = true;
        }
        if (!runIt) {
            System.out.println("opensearchIndexPermissionsBackground: running not required; done");
            return;
        }
        // i think we should set these first... tho they may not get persisted til after?
        OpenSearch.setPermissionsTimestamp(myShepherd);
        OpenSearch.setPermissionsNeeded(myShepherd, false);
        opensearchIndexPermissions();
        System.out.println("opensearchIndexPermissionsBackground: running completed");
    }

/*  note: there are a great deal of users with *no username* that seem to appear in enc.submitters array.
    however, very few (2 out of 5600+) encounters with such .submitters have a blank submitterID value
    therefore: submitterID will be assumed to be a required value on users which need to be

    this seems further validated by the facts that:
    - canUserAccess(user) returns false if no username on user
    - a user wihtout a username cant be logged in (and thus cant search)

    "admin" users are just ignored entirely, as they will be exempt from the viewUsers criteria during searching.

    other than "ownership" (via submitterID), a user can view if they have view or edit collab with
    another user. so we frontload *approved* collabs for every user here too.

    in terms of "public" encounters, it seems that (based on Collaboration.canUserAccessEncounter()),
    encounters with submitterID in (NULL, "public", "", "N/A" [ugh]) is readable by anyone; so we will
    skip these from processing as they should be flagged with the boolean isPubliclyReadable in indexing
 */
    public static void opensearchIndexPermissions() {
        Util.mark("perm start");
        long startT = System.currentTimeMillis();
        System.out.println("opensearchIndexPermissions(): begin...");
        // no security => everything publiclyReadable - saves us work, no?
        if (!Collaboration.securityEnabled("context0")) return;
        OpenSearch os = new OpenSearch();
        Map<String, Set<String> > collab = new HashMap<String, Set<String> >();
        Map<String, String> usernameToId = new HashMap<String, String>();
        Shepherd myShepherd = new Shepherd("context0");
        myShepherd.setAction("Encounter.opensearchIndexPermissions");
        myShepherd.beginDBTransaction();
        // it seems as though user.uuid is *required* so we can trust that
        try {
            for (User user : myShepherd.getUsersWithUsername()) {
                usernameToId.put(user.getUsername(), user.getId());
                List<Collaboration> collabsFor = Collaboration.collaborationsForUser(myShepherd,
                    user.getUsername());
                if (Util.collectionIsEmptyOrNull(collabsFor)) continue;
                for (Collaboration col : collabsFor) {
                    if (!col.isApproved() && !col.isEditApproved()) continue;
                    if (!collab.containsKey(user.getId()))
                        collab.put(user.getId(), new HashSet<String>());
                    collab.get(user.getId()).add(col.getOtherUsername(user.getUsername()));
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        Util.mark("perm: user build done", startT);
        System.out.println("opensearchIndexPermissions(): " + usernameToId.size() +
            " total users; " + collab.size() + " have active collab");
        // now iterated over (non-public) encounters
        int encCount = 0;
        org.json.JSONObject updateData = new org.json.JSONObject();
        // we do not need full Encounter objects here to update index docs, so lets do this via sql/fields - much faster
        String sql =
            "SELECT \"CATALOGNUMBER\", \"SUBMITTERID\" FROM \"ENCOUNTER\" WHERE \"SUBMITTERID\" IS NOT NULL AND \"SUBMITTERID\" != '' AND \"SUBMITTERID\" != 'N/A' AND \"SUBMITTERID\" != 'public'";
        Query q = null;
        try {
            q = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
            List results = (List)q.execute();
            Util.mark("perm: start encs, size=" + results.size(), startT);
            Iterator it = results.iterator();
            while (it.hasNext()) {
                Object[] row = (Object[])it.next();
                String id = (String)row[0];
                String submitterId = (String)row[1];
                org.json.JSONArray viewUsers = new org.json.JSONArray();
                String uid = usernameToId.get(submitterId);
                if (uid == null) {
                    // see issue 939 for example :(
                    System.out.println("opensearchIndexPermissions(): WARNING invalid username " +
                        submitterId + " on enc " + id);
                    continue;
                }
                encCount++;
                if (encCount % 1000 == 0) Util.mark("enc[" + encCount + "]", startT);
                // viewUsers.put(uid);  // we no longer do this as we use submitterUserId from regular indexing in query filter

                // this first part asks the question: who is the owner of the Encounter collaborating with?
                // Let those people see the encounter
                // This ignores the one-way visibility of admins and orgAdmins
                // the question is backwards: it asks: who can the owning user see?
                // better to ask: who can see this Encounter by collaborating with its owner?
                /*
                   if (collab.containsKey(uid)) {
                    for (String colUsername : collab.get(uid)) {
                        String colId = usernameToId.get(colUsername);
                        if (colId == null) {
                            System.out.println(
                                "opensearchIndexPermissions(): WARNING invalid username " +
                                colUsername + " in collaboration with userId=" + uid);
                            continue;
                        }
                        viewUsers.put(colId);
                    }
                   }*/

                // better: ask the question, who else can see this encounter via collaboration?
                // get the entry set for all collaborations
                Set<String> uids = collab.keySet();
                // iterate over the key set
                Iterator<String> uidsIter = uids.iterator();
                while (uidsIter.hasNext()) {
                    // get the uid for the user of this entry
                    String localUid = uidsIter.next();
                    // get the list of usernames in this entry
                    Set<String> localCollabs = collab.get(localUid);
                    // evaluate if the submitterId (a username) of this encounter is in this list
                    if (localCollabs.contains(submitterId)) {
                        // if the submitterId is in the list, put the uid of the user in viewUsers for OpenSearch
                        viewUsers.put(localUid);
                    }
                }
                if (viewUsers.length() > 0) {
                    updateData.put("viewUsers", viewUsers);
                    try {
                        os.indexUpdate("encounter", id, updateData);
                    } catch (Exception ex) {
                        // keeping this quiet cuz it can get noise while index builds
                        // System.out.println("opensearchIndexPermissions(): WARNING failed to update viewUsers on enc " + enc.getId() + "; likely has not been indexed yet: " + ex);
                    }
                }
            }
        } catch (Exception ex) {
            System.out.println("opensearchIndexPermissions(): failed during encounter loop: " + ex);
            ex.printStackTrace();
        } finally {
            if (q != null) q.closeAll();
        }
        Util.mark("perm: done encs", startT);
        myShepherd.rollbackAndClose();
        System.out.println("opensearchIndexPermissions(): ...end [" + encCount + " encs; " +
            Math.round((System.currentTimeMillis() - startT) / 1000) + "sec]");
    }

    public static org.json.JSONObject opensearchQuery(final org.json.JSONObject query, int numFrom,
        int pageSize, String sort, String sortOrder)
    throws IOException {
        return Base.opensearchQuery("encounter", query, numFrom, pageSize, sort, sortOrder);
    }

    public void opensearchDocumentSerializer(JsonGenerator jgen)
    throws IOException, JsonProcessingException {
        Shepherd myShepherd = new Shepherd("context0");

        myShepherd.setAction("Encounter.opensearchDocumentSerializer");
        myShepherd.beginDBTransaction();
        try {
            opensearchDocumentSerializer(jgen, myShepherd);
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            myShepherd.rollbackAndClose();
        }
    }

    public void opensearchDocumentSerializer(JsonGenerator jgen, Shepherd myShepherd)
    throws IOException, JsonProcessingException {
        super.opensearchDocumentSerializer(jgen, myShepherd);

        jgen.writeStringField("locationId", this.getLocationID());
        jgen.writeStringField("locationName", this.getLocationName());
        Long dim = this.getDateInMillisecondsFallback();
        if (dim != null) jgen.writeNumberField("dateMillis", dim);
        String date = Util.getISO8601Date(this.getDate());
        if (date != null) jgen.writeStringField("date", date);
        date = Util.getISO8601Date(this.getDWCDateAdded());
        if (date != null) jgen.writeStringField("dateSubmitted", date);
        jgen.writeStringField("verbatimEventDate", this.getVerbatimEventDate());
        jgen.writeStringField("sex", this.getSex());
        jgen.writeStringField("taxonomy", this.getTaxonomyString());
        jgen.writeStringField("lifeStage", this.getLifeStage());
        jgen.writeStringField("livingStatus", this.getLivingStatus());
        jgen.writeStringField("verbatimLocality", this.getVerbatimLocality());
        jgen.writeStringField("country", this.getCountry());
        jgen.writeStringField("behavior", this.getBehavior());
        jgen.writeStringField("patterningCode", this.getPatterningCode());
        jgen.writeStringField("state", this.getState());
        jgen.writeStringField("occurrenceRemarks", this.getOccurrenceRemarks());
        jgen.writeStringField("otherCatalogNumbers", this.getOtherCatalogNumbers());
        jgen.writeBooleanField("publiclyReadable", this.isPubliclyReadable());
        jgen.writeStringField("distinguishingScar", this.getDistinguishingScar());
        String featuredAssetId = null;
        List<MediaAsset> mas = this.getMedia();
        jgen.writeNumberField("numberAnnotations", this.numNonTrivialAnnotations());
        jgen.writeNumberField("numberMediaAssets", mas.size());
        jgen.writeArrayFieldStart("mediaAssets");
        for (MediaAsset ma : mas) {
            jgen.writeStartObject();
            jgen.writeNumberField("id", ma.getIdInt());
            jgen.writeStringField("uuid", ma.getUUID());
            jgen.writeNumberField("width", ma.getWidth());
            jgen.writeNumberField("height", ma.getHeight());
            jgen.writeStringField("mimeTypeMajor", ma.getMimeTypeMajor());
            jgen.writeStringField("mimeTypeMinor", ma.getMimeTypeMinor());
            try {
                // historic data might throw IllegalArgumentException: Path not under given root
                URL url = ma.safeURL(myShepherd, null, "master");
                if (url != null) jgen.writeStringField("url", url.toString());
            } catch (Exception ex) {}
            // we (likely) dont need annotations to search on, but they are needed for
            // many of the usages (export, gallery, etc) of search *results*
            jgen.writeArrayFieldStart("annotations");
            if (ma.hasAnnotations())
                for (Annotation ann : ma.getAnnotations()) {
                    jgen.writeStartObject();
                    jgen.writeStringField("id", ann.getId());
                    jgen.writeStringField("iaClass", ann.getIAClass());
                    jgen.writeStringField("viewpoint", ann.getViewpoint());
                    jgen.writeBooleanField("isTrivial", ann.isTrivial());
                    jgen.writeNumberField("theta", ann.getTheta());
                    jgen.writeArrayFieldStart("boundingBox");
                    Feature ft = ann.getFeature(); // attempt force loading features for getBbox()
                    int[] bbox = ann.getBbox();
                    if (bbox != null)
                        for (int i : bbox) {
                            jgen.writeNumber(i);
                        }
                    jgen.writeEndArray();
                    Encounter annEnc = ann.findEncounter(myShepherd);
                    if (annEnc != null) jgen.writeStringField("encounterId", annEnc.getId());
                    jgen.writeEndObject();
                }
            jgen.writeEndArray();
            jgen.writeEndObject();
            if (featuredAssetId == null) featuredAssetId = ma.getUUID();
        }
        jgen.writeEndArray();
        if (featuredAssetId != null) jgen.writeStringField("featuredAssetUuid", featuredAssetId);
        if (this.submitterID == null) {
            jgen.writeNullField("assignedUsername");
        } else {
            jgen.writeStringField("assignedUsername", this.submitterID);
            User submitter = this.getSubmitterUser(myShepherd);
            if (submitter != null) jgen.writeStringField("submitterUserId", submitter.getId());
        }
        jgen.writeArrayFieldStart("submitters");
        for (String id : this.getAllSubmitterIds(myShepherd)) {
            jgen.writeString(id);
        }
        jgen.writeEndArray();
        jgen.writeArrayFieldStart("photographers");
        for (String id : this.getAllPhotographerIds()) {
            jgen.writeString(id);
        }
        jgen.writeEndArray();
        jgen.writeArrayFieldStart("informOthers");
        for (String id : this.getAllInformOtherIds()) {
            jgen.writeString(id);
        }
        jgen.writeEndArray();

        List<String> kws = new ArrayList();
        Map<String, String> lkws = new HashMap<String, String>();
        for (Keyword kw : this.getMediaAssetKeywords()) {
            if (kw instanceof LabeledKeyword) {
                LabeledKeyword lkw = (LabeledKeyword)kw;
                lkws.put(lkw.getLabel(), lkw.getValue());
            } else {
                String name = kw.getDisplayName();
                if (!kws.contains(name)) kws.add(name);
            }
        }
        jgen.writeArrayFieldStart("mediaAssetKeywords");
        for (String kw : kws) {
            jgen.writeString(kw);
        }
        jgen.writeEndArray();
        jgen.writeObjectFieldStart("mediaAssetLabeledKeywords");
        for (String kwLabel : lkws.keySet()) {
            jgen.writeStringField(kwLabel, lkws.get(kwLabel));
        }
        jgen.writeEndObject();

        List<Project> projs = this.getProjects(myShepherd);
        jgen.writeArrayFieldStart("projects");
        if (projs != null)
            for (Project proj : projs) {
                jgen.writeString(proj.getId());
            }
        jgen.writeEndArray();

        ImportTask itask = this.getImportTask(myShepherd);
        if (itask != null) {
            jgen.writeStringField("importTaskId", itask.getId());
            if (itask.getCreator() != null)
                jgen.writeStringField("importTaskCreatorId", itask.getCreator().getId());
            String sourceName = itask.getSourceName();
            if (sourceName != null)
                jgen.writeStringField("importTaskSourceName", sourceName);
        }
        jgen.writeArrayFieldStart("annotationViewpoints");
        for (String vp : this.getAnnotationViewpoints()) {
            jgen.writeString(vp);
        }
        jgen.writeEndArray();

        jgen.writeArrayFieldStart("annotationIAClasses");
        for (String cls : this.getAnnotationIAClasses()) {
            jgen.writeString(cls);
        }
        jgen.writeEndArray();

        jgen.writeArrayFieldStart("measurements");
        if (this.measurements != null)
            for (Measurement meas : this.measurements) {
                if (meas.getValue() == null) continue; // no value means we should skip
                jgen.writeStartObject();
                jgen.writeNumberField("value", meas.getValue());
                if (meas.getType() != null) jgen.writeStringField("type", meas.getType());
                if (meas.getUnits() != null) jgen.writeStringField("units", meas.getUnits());
                if (meas.getSamplingProtocol() != null)
                    jgen.writeStringField("samplingProtocol", meas.getSamplingProtocol());
                jgen.writeEndObject();
            }
        jgen.writeEndArray();

        jgen.writeArrayFieldStart("metalTags");
        if (this.getMetalTags() != null)
            for (MetalTag tag : this.getMetalTags()) {
                jgen.writeStartObject();
                jgen.writeStringField("number", tag.getTagNumber());
                jgen.writeStringField("location", tag.getLocation());
                jgen.writeEndObject();
            }
        jgen.writeEndArray();
        if (this.getAcousticTag() != null) {
            jgen.writeObjectFieldStart("acousticTag");
            jgen.writeStringField("idNumber", this.getAcousticTag().getIdNumber());
            jgen.writeStringField("serialNumber", this.getAcousticTag().getSerialNumber());
            jgen.writeEndObject();
        }
        if (this.getSatelliteTag() != null) {
            jgen.writeObjectFieldStart("satelliteTag");
            jgen.writeStringField("name", this.getSatelliteTag().getName());
            jgen.writeStringField("serialNumber", this.getSatelliteTag().getSerialNumber());
            jgen.writeStringField("argosPttNumber", this.getSatelliteTag().getArgosPttNumber());
            jgen.writeEndObject();
        }
        if (this.getDTag() != null) {
            jgen.writeObjectFieldStart("digitalArchiveTag");
            jgen.writeStringField("dTagID", this.getDTag().getDTagID());
            jgen.writeStringField("serialNumber", this.getDTag().getSerialNumber());
            jgen.writeEndObject();
        }
        org.json.JSONObject dpj = this.getDynamicPropertiesJSONObject();
        jgen.writeObjectFieldStart("dynamicProperties");
        for (String key : (Set<String>)dpj.keySet()) {
            jgen.writeStringField(key, dpj.optString(key, null));
        }
        jgen.writeEndObject();

        Double dlat = this.getDecimalLatitudeAsDouble();
        Double dlon = this.getDecimalLongitudeAsDouble();
        if ((dlat == null) || !Util.isValidDecimalLatitude(dlat) || (dlon == null) ||
            !Util.isValidDecimalLongitude(dlon)) {
            jgen.writeNullField("locationGeoPoint");
        } else {
            jgen.writeObjectFieldStart("locationGeoPoint");
            jgen.writeNumberField("lat", dlat);
            jgen.writeNumberField("lon", dlon);
            jgen.writeEndObject();
        }
        MarkedIndividual indiv = this.getIndividual();
        if (indiv == null) {
            jgen.writeNullField("individualId");
        } else {
            jgen.writeStringField("individualId", indiv.getId());
            jgen.writeStringField("individualSex", indiv.getSex());
            jgen.writeStringField("individualTaxonomy", indiv.getTaxonomyString());
            jgen.writeNumberField("individualNumberEncounters", indiv.getNumEncounters());
            jgen.writeStringField("individualDisplayName", indiv.getDisplayName());
            jgen.writeArrayFieldStart("individualNames");
            Set<String> names = indiv.getAllNamesList();
            if (names != null)
                for (String name : names) {
                    jgen.writeString(name);
                }
            jgen.writeEndArray();
            jgen.writeStringField("individualNickName", indiv.getNickName());
            if (indiv.getTimeOfBirth() > 0) {
                String birthTime = Util.getISO8601Date(new DateTime(
                    indiv.getTimeOfBirth()).toString());
                jgen.writeStringField("individualTimeOfBirth", birthTime);
            }
            if (indiv.getTimeOfDeath() > 0) {
                String deathTime = Util.getISO8601Date(new DateTime(
                    indiv.getTimeOfDeath()).toString());
                jgen.writeStringField("individualTimeOfDeath", deathTime);
            }
            Encounter[] encs = indiv.getDateSortedEncounters(true);
            if ((encs != null) && (encs.length > 0)) {
                String encDate = Util.getISO8601Date(encs[0].getDate());
                if (encDate != null) jgen.writeStringField("individualFirstEncounterDate", encDate);
                encDate = Util.getISO8601Date(encs[encs.length - 1].getDate());
                if (encDate != null) jgen.writeStringField("individualLastEncounterDate", encDate);
            }
            jgen.writeArrayFieldStart("individualSocialUnits");
            for (SocialUnit su : myShepherd.getAllSocialUnitsForMarkedIndividual(indiv)) {
                Membership mem = su.getMembershipForMarkedIndividual(indiv);
                if (mem != null) jgen.writeString(su.getSocialUnitName());
            }
            jgen.writeEndArray();

            jgen.writeArrayFieldStart("individualRelationshipRoles");
            for (String relRole : myShepherd.getAllRoleNamesForMarkedIndividual(indiv.getId())) {
                jgen.writeString(relRole);
            }
            jgen.writeEndArray();
        }
        DateTime occdt = getOccurrenceDateTime(myShepherd);
        if (occdt != null) jgen.writeStringField("occurrenceDate", occdt.toString());
        jgen.writeStringField("geneticSex", this.getGeneticSex());
        jgen.writeStringField("haplotype", this.getHaplotype());

        jgen.writeArrayFieldStart("microsatelliteMarkers");
        for (MicrosatelliteMarkersAnalysis msm : getMicrosatelliteMarkers()) {
            jgen.writeStartObject();
            jgen.writeStringField("analysisId", msm.getAnalysisID());
            jgen.writeObjectFieldStart("loci");
            if (msm.getLoci() != null)
                for (Locus locus : msm.getLoci()) {
                    if (locus.getName() == null) continue; // snh
                    jgen.writeObjectFieldStart(locus.getName());
                    for (int i = 0; i < 4; i++) {
                        Integer allele = locus.getAllele(i);
                        if (allele != null) jgen.writeNumberField("allele" + i, allele);
                    }
                    jgen.writeEndObject();
                }
            jgen.writeEndObject();
            jgen.writeEndObject();
        }
        jgen.writeEndArray();

        Occurrence occ = this.getOccurrence(myShepherd);
        if (occ == null) {
            jgen.writeNullField("occurrenceId");
        } else {
            jgen.writeStringField("occurrenceId", occ.getId());
            jgen.writeStringField("occurrenceGroupBehavior", occ.getGroupBehavior());
            jgen.writeStringField("occurrenceGroupComposition", occ.getGroupComposition());
            jgen.writeStringField("occurrenceComments", occ.getComments());
            if (occ.getVisibilityIndex() != null)
                jgen.writeNumberField("occurrenceVisibilityIndex", occ.getVisibilityIndex());
            if (occ.getIndividualCount() != null)
                jgen.writeNumberField("occurrenceIndividualCount", occ.getIndividualCount());
            if (occ.getMinGroupSizeEstimate() != null)
                jgen.writeNumberField("occurrenceMinGroupSizeEstimate",
                    occ.getMinGroupSizeEstimate());
            if (occ.getMaxGroupSizeEstimate() != null)
                jgen.writeNumberField("occurrenceMaxGroupSizeEstimate",
                    occ.getMaxGroupSizeEstimate());
            if (occ.getBestGroupSizeEstimate() != null)
                jgen.writeNumberField("occurrenceBestGroupSizeEstimate",
                    occ.getBestGroupSizeEstimate());
            if (occ.getBearing() != null)
                jgen.writeNumberField("occurrenceBearing", occ.getBearing());
            if (occ.getDistance() != null)
                jgen.writeNumberField("occurrenceDistance", occ.getDistance());
            Double odlat = occ.getDecimalLatitude();
            Double odlon = occ.getDecimalLongitude();
            if ((odlat == null) || !Util.isValidDecimalLatitude(odlat) || (odlon == null) ||
                !Util.isValidDecimalLongitude(odlon)) {
                jgen.writeNullField("occurrenceLocationGeoPoint");
            } else {
                jgen.writeObjectFieldStart("occurrenceLocationGeoPoint");
                jgen.writeNumberField("lat", odlat);
                jgen.writeNumberField("lon", odlon);
                jgen.writeEndObject();
            }
        }
        jgen.writeArrayFieldStart("organizations");
        User owner = this.getSubmitterUser(myShepherd);
        if ((owner != null) && (owner.getOrganizations() != null))
            for (Organization org : owner.getOrganizations()) {
                jgen.writeString(org.getId());
            }
        jgen.writeEndArray();

        jgen.writeArrayFieldStart("tissueSampleIds");
        for (String id : this.getTissueSampleIDs()) {
            jgen.writeString(id);
        }
        jgen.writeEndArray();

        Map<String, BiologicalMeasurement> bmeas = this.getBiologicalMeasurementsByType();
        jgen.writeObjectFieldStart("biologicalMeasurements");
        for (String type : (Set<String>)bmeas.keySet()) {
            jgen.writeNumberField(type, bmeas.get(type).getValue());
        }
        jgen.writeEndObject();
        // this gets set on specific single-encounter-only actions, when extra expense is okay
        // otherwise this will be computed by permissions backgrounding
        if (this.getOpensearchProcessPermissions()) {
            System.out.println("opensearchProcessPermissions=true for " + this.getId() +
                "; indexing permissions");
            jgen.writeFieldName("viewUsers");
            jgen.writeStartArray();
            for (String id : this.userIdsWithViewAccess(myShepherd)) {
                System.out.println("opensearch whhhh: " + id);
                jgen.writeString(id);
            }
            jgen.writeEndArray();
        }
    }

    public void opensearchIndexDeep()
    throws IOException {
        this.opensearchIndex();

        final String encId = this.getId();
        ExecutorService executor = Executors.newFixedThreadPool(4);
        Runnable rn = new Runnable() {
            public void run() {
                Shepherd bgShepherd = new Shepherd("context0");
                bgShepherd.setAction("Encounter.opensearchIndexDeep_" + encId);
                bgShepherd.beginDBTransaction();
                try {
                    Encounter enc = bgShepherd.getEncounter(encId);
                    if ((enc == null) || (!enc.hasMarkedIndividual() && !enc.hasAnnotations())) {
                        // bgShepherd.rollbackAndClose();
                        executor.shutdown();
                        return;
                    }
                    MarkedIndividual indiv = enc.getIndividual();
                    if (indiv != null) {
                        System.out.println("opensearchIndexDeep() background indexing indiv " +
                            indiv.getId() + " via enc " + encId);
                        try {
                            indiv.opensearchIndex();
                        } catch (Exception ex) {
                            System.out.println("opensearchIndexDeep() background indexing " +
                                indiv.getId() + " FAILED: " + ex.toString());
                            ex.printStackTrace();
                        }
                    }
                    Occurrence occ = enc.getOccurrence(bgShepherd);
                    if (occ != null) {
                        System.out.println("opensearchIndexDeep() background indexing occ " +
                            occ.getId() + " via enc " + encId);
                        try {
                            occ.opensearchIndex();
                        } catch (Exception ex) {
                            System.out.println("opensearchIndexDeep() background indexing " +
                                occ.getId() + " FAILED: " + ex.toString());
                            ex.printStackTrace();
                        }
                    }
                    if (enc.hasAnnotations()) {
                        for (Annotation ann : enc.getAnnotations()) {
                            System.out.println("opensearchIndexDeep() background indexing annot " +
                                ann.getId() + " via enc " + encId);
                            try {
                                ann.opensearchIndex();
                            } catch (Exception ex) {
                                System.out.println("opensearchIndexDeep() background indexing " +
                                    ann.getId() + " FAILED: " + ex.toString());
                                ex.printStackTrace();
                            }
                        }
                    }
                } catch (Exception e) {
                    System.out.println("opensearchIndexDeep() backgrounding Encounter " + encId +
                        " hit an exception.");
                    e.printStackTrace();
                } finally {
                    bgShepherd.rollbackAndClose();
                }
                System.out.println("opensearchIndexDeep() backgrounding Encounter " + encId +
                    " finished.");
                executor.shutdown();
            }
        };
        System.out.println("opensearchIndexDeep() begin backgrounding for " + this);
        executor.execute(rn);
        System.out.println("opensearchIndexDeep() [foreground] finished for Encounter " + encId);
    }

    // given a doc from opensearch, can user access it?
    public static boolean opensearchAccess(org.json.JSONObject doc, User user,
        Shepherd myShepherd) {
        if ((doc == null) || (user == null)) return false;
        if (doc.optBoolean("publiclyReadable", false)) return true;
        if (doc.optString("submitterUserId", "__FAIL__").equals(user.getId())) return true;
        if (user.isAdmin(myShepherd)) return true;
        org.json.JSONArray viewUsers = doc.optJSONArray("viewUsers");
        if (viewUsers == null) return false;
        for (int i = 0; i < viewUsers.length(); i++) {
            if (viewUsers.optString(i, "__FAIL__").equals(user.getId())) return true;
        }
        return false;
    }

    @Override public Base getById(Shepherd myShepherd, String id) {
        return myShepherd.getEncounter(id);
    }

    @Override public String getAllVersionsSql() {
        return
                "SELECT \"CATALOGNUMBER\", CAST(COALESCE(EXTRACT(EPOCH FROM CAST(\"MODIFIED\" AS TIMESTAMP))*1000,-1) AS BIGINT) AS version FROM \"ENCOUNTER\" ORDER BY version";
    }

    @Override public long getVersion() {
        return Util.getVersionFromModified(modified);
    }

    public org.json.JSONObject opensearchMapping() {
        org.json.JSONObject map = super.opensearchMapping();
        org.json.JSONObject keywordType = new org.json.JSONObject("{\"type\": \"keyword\"}");
        org.json.JSONObject keywordNormalType = new org.json.JSONObject(
            "{\"type\": \"keyword\", \"normalizer\": \"wildbook_keyword_normalizer\"}");
        map.put("date", new org.json.JSONObject("{\"type\": \"date\"}"));
        map.put("dateSubmitted", new org.json.JSONObject("{\"type\": \"date\"}"));
        map.put("verbatimEventDate", new org.json.JSONObject("{\"type\": \"text\"}"));
        map.put("individualTimeOfBirth", new org.json.JSONObject("{\"type\": \"date\"}"));
        map.put("individualTimeOfDeath", new org.json.JSONObject("{\"type\": \"date\"}"));
        map.put("locationGeoPoint", new org.json.JSONObject("{\"type\": \"geo_point\"}"));
        map.put("occurrenceLocationGeoPoint", new org.json.JSONObject("{\"type\": \"geo_point\"}"));

        // if we want to sort on it (and it is texty), it needs to be keyword
        // (ints, dates, etc are all sortable)
        // note: "id" is done in Base.java
        map.put("taxonomy", keywordType);
        map.put("occurrenceId", keywordType);
        map.put("state", keywordType);
        map.put("submitterUserId", keywordType);
        map.put("individualTaxonomy", keywordType);
        map.put("individualId", keywordType);
        map.put("importTaskId", keywordType);
        map.put("importTaskCreatorId", keywordType);
        map.put("importTaskSourceName", keywordType);

        // all case-insensitive keyword-ish types
        map.put("locationId", keywordNormalType);
        map.put("locationName", keywordNormalType);
        map.put("country", keywordNormalType);
        map.put("assignedUsername", keywordNormalType);
        map.put("projects", keywordNormalType);
        map.put("behavior", keywordNormalType);
        map.put("patterningCode", keywordNormalType);
        map.put("annotationViewpoints", keywordNormalType);
        map.put("mediaAssetKeywords", keywordNormalType);
        map.put("annotationIAClasses", keywordNormalType);
        map.put("haplotype", keywordNormalType);
        map.put("individualSocialUnits", keywordNormalType);
        map.put("individualRelationshipRoles", keywordNormalType);
        map.put("individualDisplayName", keywordNormalType);
        map.put("organizations", keywordNormalType);
        map.put("otherCatalogNumbers", keywordNormalType);
        map.put("lifeStage", keywordNormalType);
        map.put("submitters", keywordNormalType);
        map.put("photographers", keywordNormalType);
        map.put("informOthers", keywordNormalType);

        // https://stackoverflow.com/questions/68760699/matching-documents-where-multiple-fields-match-in-an-array-of-objects
        map.put("measurements", new org.json.JSONObject("{\"type\": \"nested\"}"));
        map.put("metalTags", new org.json.JSONObject("{\"type\": \"nested\"}"));
        return map;
    }

    // for encounters, we allow null user to see *some* things
    public org.json.JSONObject jsonForApiGet(Shepherd myShepherd, User user)
    throws IOException {
        org.json.JSONObject rtn = new org.json.JSONObject();
        boolean isPublic = isPubliclyReadable();
        // anon-user can only see public, so we 401 here
        if ((user == null) && !isPublic) {
            rtn.put("statusCode", 401);
            rtn.put("success", false);
            return rtn;
        }
        rtn = opensearchDocumentAsJSONObject(myShepherd);
        rtn.put("success", true);
        rtn.put("statusCode", 200);
        rtn.put("access", "read");
        rtn.put("isPublic", isPublic);

        boolean hideUserEmail = true;
        User submitter = getSubmitterUser(myShepherd);
        if (submitter != null)
            rtn.put("submitterInfo",
                submitter.infoJSONObject(myShepherd, user != null, hideUserEmail));
        // check to see if logged-in-user is outright blocked
        if (user != null) {
            boolean blocked = true;
            if (canUserEdit(user, myShepherd)) {
                rtn.put("access", "write");
                blocked = false;
                // we can allow email being shown when access=write
                hideUserEmail = false;
                if (submitter != null)
                    rtn.put("submitterInfo",
                        submitter.infoJSONObject(myShepherd, true, hideUserEmail));
            } else if (canUserView(user, myShepherd)) {
                blocked = false;
            }
            if (blocked) {
                // we keep this very minimal
                org.json.JSONObject blockedRtn = new org.json.JSONObject();
                blockedRtn.put("success", true);
                blockedRtn.put("statusCode", 200);
                blockedRtn.put("access", "none");
                blockedRtn.put("id", rtn.get("id"));
                // need this to know who to collab with
                Collaboration col = Collaboration.collaborationBetweenUsers(user, submitter,
                    myShepherd.getContext());
                if (col != null) {
                    blockedRtn.put("collaborationState", col.getState());
                    blockedRtn.put("collaborationCreated", col.getDateStringCreated());
                }
                blockedRtn.put("assignedUsername", rtn.optString("assignedUsername", null));
                blockedRtn.put("submitterUserId", rtn.optString("submitterUserId", null));
                blockedRtn.put("submitterInfo", rtn.optJSONObject("submitterInfo"));
                return blockedRtn;
            }
        }
        // additional fields we want
        rtn.put("dateValues", getDateValuesJson());
        rtn.put("researcherComments", getRComments());
        rtn.put("groupRole", getGroupRole());
        rtn.put("identificationRemarks", getIdentificationRemarks());

        // the user-listy things
        rtn.put("submitters",
            userListJSONArray(myShepherd, this.submitters, user != null, hideUserEmail));
        rtn.put("photographers",
            userListJSONArray(myShepherd, this.photographers, user != null, hideUserEmail));
        rtn.put("informOthers",
            userListJSONArray(myShepherd, this.informOthers, user != null, hideUserEmail));
        // sanitize for non-logged-in users (hence, on public data)
        if (user == null) {
            rtn.put("access", "read");
            String[] blocked = {
                "importTaskSourceName", "locationId", "locationName", "country", "verbatimLocality",
                    "microsatelliteMarkers", "locationGeoPoint", "occurrenceLocationGeoPoint",
                    "mediaAssetKeywords", "mediaAssetLabeledKeywords", "biologicalMeasurements",
                    "importTaskCreatorId", "annotationIAClasses", "annotationViewpoints",
                    "researcherComments", "informOthers", "photographers", "submitters",
                    "assignedUsername"
            };
            for (String field : blocked) {
                rtn.remove(field);
            }
            // we redo mediaAssets, with no annots and mid size
            org.json.JSONArray mas = new org.json.JSONArray();
            for (MediaAsset ma : getMedia()) {
                org.json.JSONObject maj = new org.json.JSONObject();
                maj.put("uuid", ma.getUUID());
                maj.put("mimeTypeMajor", ma.getMimeTypeMajor());
                maj.put("mimeTypeMinor", ma.getMimeTypeMinor());
                maj.put("rotationInfo", ma.getRotationInfo());
                try {
                    // historic data might throw IllegalArgumentException: Path not under given root
                    java.net.URL url = ma.safeURL(myShepherd, null, "mid");
                    if (url != null) maj.put("url", url.toString());
                } catch (Exception ex) {}
                mas.put(maj);
            }
            rtn.put("mediaAssets", mas);
        } else {
            // for real users we add some extras to assets and annotations
            if (rtn.optJSONArray("mediaAssets") != null) {
                for (int i = 0; i < rtn.getJSONArray("mediaAssets").length(); i++) {
                    if (rtn.getJSONArray("mediaAssets").optJSONObject(i) != null) {
                        MediaAsset ma = MediaAssetFactory.loadByUuid(rtn.getJSONArray(
                            "mediaAssets").getJSONObject(i).optString("uuid"), myShepherd);
                        if (ma != null) {
                            rtn.getJSONArray("mediaAssets").getJSONObject(i).put("keywords",
                                ma.getKeywordsJSONArray());
                            rtn.getJSONArray("mediaAssets").getJSONObject(i).put("detectionStatus",
                                ma.getDetectionStatus());
                            rtn.getJSONArray("mediaAssets").getJSONObject(i).put("userFilename",
                                ma.getUserFilename());
                            rtn.getJSONArray("mediaAssets").getJSONObject(i).put("rotationInfo",
                                ma.getRotationInfo());
                        }
                        // now we cram some stuff into each annotation as well
                        if (rtn.getJSONArray("mediaAssets").getJSONObject(i).optJSONArray(
                            "annotations") != null) {
                            for (int j = 0;
                                j <
                                rtn.getJSONArray("mediaAssets").getJSONObject(i).getJSONArray(
                                "annotations").length();
                                j++) {
                                if (rtn.getJSONArray("mediaAssets").getJSONObject(i).getJSONArray(
                                    "annotations").optJSONObject(j) == null) continue;
                                // this parsing of nested json really makes me miss perl
                                Annotation ann = myShepherd.getAnnotation(rtn.getJSONArray(
                                    "mediaAssets").getJSONObject(i).getJSONArray(
                                    "annotations").getJSONObject(j).optString("id", null));
                                if (ann == null) continue;
                                rtn.getJSONArray("mediaAssets").getJSONObject(i).getJSONArray(
                                    "annotations").getJSONObject(j).put("identificationStatus",
                                    ann.getIdentificationStatus());
                                // annTasks are in chron order so most recent will be at end
                                List<Task> annTasks = ann.getRootIATasks(myShepherd);
                                int ntasks = Util.collectionSize(annTasks);
                                if (ntasks > 0) {
                                    rtn.getJSONArray("mediaAssets").getJSONObject(i).getJSONArray(
                                        "annotations").getJSONObject(j).put("iaTaskId",
                                        annTasks.get(ntasks - 1).getId());
                                    rtn.getJSONArray("mediaAssets").getJSONObject(i).getJSONArray(
                                        "annotations").getJSONObject(j).put("iaTaskParameters",
                                        annTasks.get(ntasks - 1).getParameters());
                                }
                            }
                        }
                    }
                }
            }
        }
        return rtn;
    }

    // internal utility function
    private org.json.JSONArray userListJSONArray(Shepherd myShepherd, List<User> users,
        boolean includeSensitive, boolean hideEmail) {
        org.json.JSONArray arr = new org.json.JSONArray();
        if (Util.collectionIsEmptyOrNull(users)) return arr;
        for (User user : users) {
            arr.put(user.infoJSONObject(myShepherd, includeSensitive, hideEmail));
        }
        return arr;
    }

    public static Base createFromApi(org.json.JSONObject payload, List<File> files,
        Shepherd myShepherd)
    throws ApiException {
        if (payload == null) throw new ApiException("empty payload");
        User user = (User)payload.opt("_currentUser");

        // these need validation (will throw ApiException if fail)
        String locationID = (String)validateFieldValue("locationId", payload);
        String dateTime = (String)validateFieldValue("dateTime", payload);
        String txStr = (String)validateFieldValue("taxonomy", payload);
        String submitterEmail = (String)validateFieldValue("submitterEmail", payload);
        String photographerEmail = (String)validateFieldValue("photographerEmail", payload);
        Double decimalLatitude = (Double)validateFieldValue("decimalLatitude", payload);
        Double decimalLongitude = (Double)validateFieldValue("decimalLongitude", payload);
        if (((decimalLatitude == null) && (decimalLongitude != null)) ||
            ((decimalLatitude != null) && (decimalLongitude == null))) {
            org.json.JSONObject error = new org.json.JSONObject();
            error.put("code", ApiException.ERROR_RETURN_CODE_INVALID);
            // i guess we pick one, since both are wrong
            error.put("fieldName", "decimalLatitude");
            error.put("value", decimalLatitude);
            throw new ApiException("cannot send just one of decimalLatitude and decimalLongitude",
                    error);
        }
        String additionalEmailsValue = payload.optString("additionalEmails", null);
        String[] additionalEmails = null;
        if (!Util.stringIsEmptyOrNull(additionalEmailsValue))
            additionalEmails = additionalEmailsValue.split("[,\\s]+");
        if (additionalEmails != null) {
            org.json.JSONObject error = new org.json.JSONObject();
            error.put("fieldName", "additionalEmails");
            for (String email : additionalEmails) {
                if (!Util.isValidEmailAddress(email)) {
                    error.put("code", ApiException.ERROR_RETURN_CODE_INVALID);
                    error.put("value", email);
                    throw new ApiException("invalid email address", error);
                }
            }
        }
        Encounter enc = new Encounter(false);
        if (Util.isUUID(payload.optString("_id"))) enc.setId(payload.getString("_id"));
        enc.setLocationID(locationID);
        enc.setDecimalLatitude(decimalLatitude);
        enc.setDecimalLongitude(decimalLongitude);
        enc.setDateFromISO8601String(dateTime);
        enc.setTaxonomyFromString(txStr);
        if (CommonConfiguration.getProperty("encounterState0", myShepherd.getContext()) != null) {
            enc.setState(CommonConfiguration.getProperty("encounterState0",
                myShepherd.getContext()));
        }
        enc.setComments(payload.optString("comments", null));
        if (user == null) {
            enc.setSubmitterID("public"); // this seems to be what EncounterForm servlet does so...
        } else {
            enc.setSubmitterID(user.getUsername());
            enc.addSubmitter(user);
        }
        if (!Util.stringIsEmptyOrNull(submitterEmail)) {
            User submitterUser = myShepherd.getOrCreateUserByEmailAddress(submitterEmail,
                payload.optString("submitterName", null));
            // set this after the owner-submitter being set
            enc.addSubmitter(submitterUser);
        }
        if (!Util.stringIsEmptyOrNull(photographerEmail)) {
            User photographerUser = myShepherd.getOrCreateUserByEmailAddress(photographerEmail,
                payload.optString("photographerName", null));
            enc.addPhotographer(photographerUser);
        }
        if (additionalEmails != null) {
            for (String email : additionalEmails) {
                User addlUser = myShepherd.getOrCreateUserByEmailAddress(email, null);
                enc.addInformOther(addlUser);
            }
        }
        // this will get/make an Occurrence no matter what
        Occurrence occ = myShepherd.getOrCreateOccurrence(payload.optString("occurrenceId", null));
        occ.addEncounterAndUpdateIt(enc);
        myShepherd.getPM().makePersistent(occ);
        return enc;
    }

    // user should already have been validated -- via obj.canUserEdit() -- in api/BaseObject, so this
    // does not need to be tested here. however, more detailed checks may require user (e.g. can user
    // also alter another object, such as Occurrence)
    public org.json.JSONObject processPatch(org.json.JSONArray patchArr, User user,
        Shepherd myShepherd)
    throws ApiException {
        if (patchArr == null)
            throw new ApiException("null patch array", ApiException.ERROR_RETURN_CODE_REQUIRED);
        this.setSkipAutoIndexing(true);
        org.json.JSONArray resArr = new org.json.JSONArray();
        Set<Occurrence> occNeedPruning = new HashSet<Occurrence>();
        Set<MarkedIndividual> indivNeedPruning = new HashSet<MarkedIndividual>();
        for (int i = 0; i < patchArr.length(); i++) {
            System.out.println("applied patch at [i=" + i + "]: " + patchArr.optJSONObject(i));
            org.json.JSONObject patchRes = EncounterPatchValidator.applyPatch(this,
                patchArr.optJSONObject(i), user, myShepherd);
            for (int j = 0; j < patchRes.getJSONArray("_mayNeedPruning").length(); j++) {
                Object p = patchRes.getJSONArray("_mayNeedPruning").get(j);
                if (p instanceof Occurrence) {
                    occNeedPruning.add((Occurrence)p);
                } else if (p instanceof MarkedIndividual) {
                    indivNeedPruning.add((MarkedIndividual)p);
                }
            }
            patchRes.remove("_mayNeedPruning");
            System.out.println("patch returned at [i=" + i + "]: " + patchRes);
            resArr.put(patchRes);
        }
        org.json.JSONObject rtn = new org.json.JSONObject();
        rtn.put("patchResults", resArr);
        // after applying each patch, make sure nothing is wrong
        EncounterPatchValidator.finalValidation(this, myShepherd);
        // now we need to look at modified objects which may be empty (and thus need pruning)
        for (Occurrence occ : occNeedPruning) {
            if (!occ.pruneIfNeeded(myShepherd)) {
                occ.setSkipAutoIndexing(false);
            }
        }
        for (MarkedIndividual indiv : indivNeedPruning) {
            if (!indiv.pruneIfNeeded(myShepherd)) {
                indiv.setSkipAutoIndexing(false);
            }
        }
        // no exceptions means success
        rtn.put("success", true);
        rtn.put("statusCode", 200);
        this.setDWCDateLastModified();
        this._log(resArr);
        this.setSkipAutoIndexing(false);
        return rtn;
    }

    // see note on painful redundancy with bulk import and createFromApi on
    // Base.applyPatchOp()
    public Object applyPatchOp(String fieldName, Object value, String op)
    throws ApiException {
        System.out.println("[DEBUG] applyPatchOp(): " + op + " " + fieldName + "=>" + value +
            " on: " + this);
        if (op == null)
            throw new ApiException("op is required", ApiException.ERROR_RETURN_CODE_REQUIRED);
        // TODO future enhancement: op=remove path=annotations/ANNOT_ID should perform
        // functionality of servlet/EncounterRemoveAnnotation.java
        User user = null; // needed for some options below
        switch (fieldName) {
        case "decimalLatitude":
            setDecimalLatitude((Double)value);
            break;
        case "decimalLongitude":
            setDecimalLongitude((Double)value);
            break;
        case "alternateId":
            // note: is same as otherCatalogNumbers
            setAlternateID((String)value);
            break;
        case "behavior":
            setBehavior((String)value);
            break;
        case "country":
            setCountry((String)value);
            break;
        case "dateInMilliseconds":
            if (value != null) setDateInMilliseconds((Long)value);
            break;
        case "year":
            if (value == null) {
                setYear(0);
            } else {
                setYear((Integer)value);
            }
            break;
        case "month":
            if (value == null) {
                setMonth(0);
            } else {
                setMonth((Integer)value);
            }
            break;
        case "day":
            if (value == null) {
                setDay(0);
            } else {
                setDay((Integer)value);
            }
            break;
        case "hour":
            if (value == null) {
                setHour(-1); // grr
            } else {
                setHour((Integer)value);
            }
            break;
        case "minutes":
            if (value == null) {
                setMinutes(null);
            } else {
                setMinutes(value.toString());
            }
            break;
        case "depth":
            setDepth((Double)value);
            break;
        case "elevation":
            setMaximumElevationInMeters((Double)value);
            break;
        case "genus":
            setGenus((String)value);
            break;
        case "specificEpithet":
            setSpecificEpithet((String)value);
            break;
        case "lifeStage":
            setLifeStage((String)value);
            break;
        case "livingStatus":
            setLivingStatus((String)value);
            break;
        case "locationId":
            setLocationID((String)value);
            break;
        case "sex":
            setSex((String)value);
            break;
        case "state":
            setState((String)value);
            break;
        case "submitterID":
            setSubmitterID((String)value);
            break;
        case "submitterName":
            setSubmitterName((String)value);
            break;
        case "submitterOrganization":
            setSubmitterOrganization((String)value);
            break;
        case "distinguishingScar":
            setDistinguishingScar((String)value);
            break;
        case "groupRole":
            setGroupRole((String)value);
            break;
        case "identificationRemarks":
            setIdentificationRemarks((String)value);
            break;
        case "occurrenceRemarks":
        case "sightingRemarks":
            setOccurrenceRemarks((String)value);
            break;
        case "otherCatalogNumbers":
            setOtherCatalogNumbers((String)value);
            break;
        case "patterningCode":
            setPatterningCode((String)value);
            break;
        case "satelliteTag":
            setSatelliteTag((SatelliteTag)value);
            break;
        case "acousticTag":
            setAcousticTag((AcousticTag)value);
            break;
        case "metalTags":
            // we only need location to remove
            if ("remove".equals(op) && (value != null)) {
                removeMetalTag(value.toString());
            } else if (("add".equals(op) || "replace".equals(op)) &&
                (value instanceof org.json.JSONObject)) {
                // add or replace will update based on location if exists
                org.json.JSONObject jval = (org.json.JSONObject)value;
                addOrUpdateMetalTag(jval.optString("location", null),
                    jval.optString("number", null));
            }
            break;
        case "measurements":
            if ("remove".equals(op) && (value != null)) {
                removeMeasurementByType(value.toString());
            } else if (value instanceof Measurement) {
                // for op=add or op=replace, if it has the measurement (i.e. by type), it means
                // it should be changed in place already so dont do anything
                Measurement meas = (Measurement)value;
                if (findMeasurementOfType(meas.getType()) == null) addMeasurement(meas);
            }
            break;
        case "annotations":
            // for now we can only patch op=remove on path=annotations
            // adding annots is done through legacy servlet
            if ("remove".equals(op) && (value instanceof Annotation)) {
                // enc.removeAnnotation and deletePersistent (from db/shepherd) done in EncounterPatchValidator
                // so we only need to clean up some loose ends here
                Annotation goneAnnot = (Annotation)value;
                goneAnnot.setSkipAutoIndexing(true);
                goneAnnot.opensearchUnindexQuiet();
            }
            break;
        // these we really only want to append to (i think??)
        // so this should only happen when op=add/replace and
        // value is non-null
        case "researcherComments":
            if (value != null) addComments((String)value);
            break;
        case "verbatimLocality":
            setVerbatimLocality((String)value);
            break;
        case "verbatimEventDate":
            setVerbatimEventDate((String)value);
            break;
        // we should get value as a MarkedIndividual here (or null)
        case "individualId":
            if ("remove".equals(op) || (value == null)) {
                MarkedIndividual current = removeIndividual();
                if (current != null) {
                    System.out.println("enc.applyPatchOp() removed " + this + " from " + current);
                }
            } else {
                // value should be an individual (new or existing)
                MarkedIndividual indiv = (MarkedIndividual)value;
                MarkedIndividual current = getIndividual();
                if (indiv.equals(current)) {
                    System.out.println(
                        "enc.applyPatchOp() ignoring adding existing individual to " + this);
                    break;
                }
                current = removeIndividual();
                if (current != null) {
                    current.setSkipAutoIndexing(false);
                    setIndividual(null);
                    System.out.println("enc.applyPatchOp() removed (prior to re-setting) " + this +
                        " from " + current);
                }
                indiv.addEncounter(this);
                setIndividual(indiv);
                // this will only set indiv taxonomy if NOT set
                // so for new indiv and existing with no taxonomy
                if (indiv.getTaxonomyString() == null)
                    indiv.setTaxonomyString(this.getTaxonomyString());
                // indiv.version will be updated by above calls
                System.out.println("enc.applyPatchOp() added " + this + " to " + indiv);
            }
            break;
        // value should be an Occurrence here (already validated and permission-checked)
        case "occurrenceId":
            // if EncounterPatchValidator let thru null value, we do nothing
            if (value == null) break;
            Occurrence occ = (Occurrence)value;
            if ("remove".equals(op)) {
                // in remove case, we are given occ to remove from
                occ.removeEncounter(this);
                this.occurrenceID = null;
            } else {
                // otherwise it is occ we add to
                occ.addEncounterAndUpdateIt(this);
            }
            break;
        case "assets":
            if ("add".equals(op) && (value != null)) {
                MediaAsset ma = (MediaAsset)value;
                ma.setDetectionStatus("_new");
                addMediaAsset(ma);
            }
            break;
        // value should be a user here
        case "informOthers":
            user = (User)value;
            if (op.equals("remove")) {
                removeInformOther(user);
            } else {
                addInformOther(user);
            }
            break;
        case "submitters":
            user = (User)value;
            if (op.equals("remove")) {
                removeSubmitter(user);
            } else {
                addSubmitter(user);
            }
            break;
        case "photographers":
            user = (User)value;
            if (op.equals("remove")) {
                removePhotographer(user);
            } else {
                addPhotographer(user);
            }
            break;
        default:
            throw new ApiException("unknown fieldName: " + fieldName,
                    ApiException.ERROR_RETURN_CODE_INVALID);
        }
        return value;
    }

    public org.json.JSONObject afterPatch(Shepherd myShepherd) {
        org.json.JSONObject res = new org.json.JSONObject();
        List<Base> needsIndexing = new ArrayList<Base>();
        org.json.JSONArray newAssetsArr = new org.json.JSONArray();
        // we update children here (not backgrounded) since we need them available
        // once the api returns the results
        for (MediaAsset ma : this.getMedia()) {
            if ("_new".equals(ma.getDetectionStatus())) {
                // _post_new is meant to be temporary, as it
                // will get overwritten once put into IA pipeline
                ma.setDetectionStatus("_post_new");
                ma.updateStandardChildren(myShepherd);
                needsIndexing.add(ma);
                org.json.JSONObject maj = new org.json.JSONObject();
                maj.put("id", ma.getIdInt());
                try {
                    URL url = ma.safeURL(myShepherd, null, "master");
                    if (url != null) maj.put("url", url.toString());
                } catch (Exception ex) {}
                newAssetsArr.put(maj);
            }
        }
        if (newAssetsArr.length() > 0) res.put("newMediaAssets", newAssetsArr);
        BulkImportUtil.bulkOpensearchIndex(needsIndexing);
        return res;
    }

    // this is allowed to have its own new thread since encounter is persisted
    public org.json.JSONObject afterPatchTransaction(String context) {
        List<Integer> ids = new ArrayList<Integer>();

        // we use _post_new state to determine what needs to go to IA
        // see: afterPatch() above
        for (MediaAsset ma : this.getMedia()) {
            if ("_post_new".equals(ma.getDetectionStatus())) {
                ids.add(ma.getIdInt());
            }
        }
        if (ids.size() < 1) return null;
        Task task = this.sendToIA(ids, context);
        if (task == null) return null;
        org.json.JSONObject rtn = new org.json.JSONObject();
        rtn.put("taskId", task.getId());
        return rtn;
    }

    public MarkedIndividual removeIndividual() {
        MarkedIndividual current = getIndividual();

        if (current == null) return null;
        // skip auto indexing cuz race conditions; must manually index later
        current.setSkipAutoIndexing(true);
        current.removeEncounter(this);
        setIndividual(null);
        return current;
    }

    public static Object validateFieldValue(String fieldName, org.json.JSONObject data)
    throws ApiException {
        if (data == null) throw new ApiException("empty payload");
        org.json.JSONObject error = new org.json.JSONObject();
        error.put("fieldName", fieldName);
        String exMessage = "invalid value for " + fieldName;
        Object returnValue = null;
        double UNSET_LATLON = 9999.99;
        switch (fieldName) {
        case "locationId":
            returnValue = data.optString(fieldName, null);
            if (returnValue == null) {
                error.put("code", ApiException.ERROR_RETURN_CODE_REQUIRED);
                throw new ApiException(exMessage, error);
            }
            if (!LocationID.isValidLocationID((String)returnValue)) {
                error.put("code", ApiException.ERROR_RETURN_CODE_INVALID);
                error.put("value", returnValue);
                throw new ApiException(exMessage, error);
            }
            break;

        case "dateTime":
            returnValue = data.optString(fieldName, null);
            if (returnValue == null) {
                error.put("code", ApiException.ERROR_RETURN_CODE_REQUIRED);
                throw new ApiException(exMessage, error);
            }
            if (!validISO8601String((String)returnValue)) {
                error.put("code", ApiException.ERROR_RETURN_CODE_INVALID);
                error.put("value", returnValue);
                throw new ApiException(exMessage, error);
            }
            break;

        case "taxonomy":
            returnValue = data.optString(fieldName, null);
            if (returnValue != null) { // null is allowed, but will not pass validity
                // this is throwaway read-only shepherd
                Shepherd myShepherd = new Shepherd("context0");
                myShepherd.setAction("Encounter.validateFieldValue");
                boolean validTaxonomy = false;
                myShepherd.beginDBTransaction();
                try {
                    validTaxonomy = myShepherd.isValidTaxonomyName((String)returnValue);
                } catch (Exception e) { e.printStackTrace(); } finally {
                    myShepherd.rollbackAndClose();
                }
                if (!validTaxonomy) {
                    error.put("code", ApiException.ERROR_RETURN_CODE_INVALID);
                    error.put("value", returnValue);
                    throw new ApiException(exMessage, error);
                }
            }
            break;

        case "photographerEmail":
        case "submitterEmail":
            returnValue = data.optString(fieldName, null);
            if ((returnValue != null) && !Util.isValidEmailAddress((String)returnValue)) {
                error.put("code", ApiException.ERROR_RETURN_CODE_INVALID);
                error.put("value", returnValue);
                throw new ApiException(exMessage, error);
            }
            break;

        case "decimalLatitude":
            returnValue = data.optDouble(fieldName, UNSET_LATLON);
            if ((double)returnValue == UNSET_LATLON) {
                returnValue = null;
            } else if (!Util.isValidDecimalLatitude((double)returnValue)) {
                error.put("code", ApiException.ERROR_RETURN_CODE_INVALID);
                error.put("value", returnValue);
                throw new ApiException(exMessage, error);
            }
            break;

        case "decimalLongitude":
            returnValue = data.optDouble(fieldName, UNSET_LATLON);
            if ((double)returnValue == UNSET_LATLON) {
                returnValue = null;
            } else if (!Util.isValidDecimalLongitude((double)returnValue)) {
                error.put("code", ApiException.ERROR_RETURN_CODE_INVALID);
                error.put("value", returnValue);
                throw new ApiException(exMessage, error);
            }
            break;

        default:
            System.out.println("Encounter.validateFieldValue(): WARNING unsupported fieldName=" +
                fieldName);
        }
        // must be okay!
        return returnValue;
    }

    // basically ripped from servlet/EncounterForm
    public Task sendToIA(Shepherd myShepherd) {
        Task task = null;

        try {
            IAJsonProperties iaConfig = IAJsonProperties.iaConfig();
            if (iaConfig.hasIA(this, myShepherd)) {
                for (MediaAsset ma : this.getMedia()) {
                    ma.setDetectionStatus(IBEISIA.STATUS_INITIATED);
                }
                Task parentTask = null; // this is *not* persisted, but only used so intakeMediaAssets will inherit its params
                if (this.getLocationID() != null) {
                    parentTask = new Task();
                    org.json.JSONObject tp = new org.json.JSONObject();
                    org.json.JSONObject mf = new org.json.JSONObject();
                    mf.put("locationId", this.getLocationID());
                    tp.put("matchingSetFilter", mf);
                    parentTask.setParameters(tp);
                }
                task = IA.intakeMediaAssets(myShepherd, this.getMedia(), parentTask);
                myShepherd.storeNewTask(task);
                System.out.println("sendToIA() success on " + this + " => " + task);
            } else {
                System.out.println("sendToIA() skipped; no config for " + this);
            }
        } catch (Exception ex) {
            System.out.println("sendToIA() failed on " + this + ": " + ex);
            ex.printStackTrace();
        }
        return task;
    }

    // this is based on servlet/MediaAssetCreate and differs only slightly from
    // above - mainly in that it can handle multiple assets and creates own shepherd
    // (note: all assets assumed to be using *this encounter*
    public Task sendToIA(List<Integer> ids, String context) {
        Task task = null;
        Shepherd myShepherd = new Shepherd(context);

        myShepherd.setAction("Encounter.sendToIA()");
        myShepherd.beginDBTransaction();
        try {
            IAJsonProperties iaConfig = IAJsonProperties.iaConfig();
            boolean hasConfig = iaConfig.hasIA(this, myShepherd);
            List<MediaAsset> allMAs = new ArrayList<MediaAsset>();
            for (Integer id : ids) {
                if (id < 0) continue;
                MediaAsset ma = MediaAssetFactory.load(id, myShepherd);
                if (ma != null) {
                    ma.setDetectionStatus(hasConfig ? "pending" : "complete");
                    allMAs.add(ma);
                }
            }
            if (!hasConfig) {
                System.out.println("sendToIA() skipped; no config for " + this);
            } else if (allMAs.size() > 0) {
                Task parentTask = null; // not persisted
                if (this.getLocationID() != null) {
                    parentTask = new Task();
                    org.json.JSONObject tp = new org.json.JSONObject();
                    org.json.JSONObject mf = new org.json.JSONObject();
                    mf.put("locationId", this.getLocationID());
                    tp.put("matchingSetFilter", mf);
                    parentTask.setParameters(tp);
                }
                Taxonomy taxy = this.getTaxonomy(myShepherd);
                if (taxy != null) {
                    task = IA.intakeMediaAssetsOneSpecies(myShepherd, allMAs, taxy, parentTask);
                } else {
                    task = IA.intakeMediaAssets(myShepherd, allMAs);
                }
                myShepherd.storeNewTask(task);
                System.out.println("sendToIA() created " + task + " for " + this);
            }
            // persist will catch change on asset detectionStatus regardless
            myShepherd.commitDBTransaction();
        } catch (Exception ex) {
            ex.printStackTrace();
            myShepherd.rollbackDBTransaction();
        } finally {
            myShepherd.closeDBTransaction();
        }
        return task;
    }

    public Set<String> getNotificationEmailAddresses() {
        Set<String> addrs = new HashSet<String>();

        addrs.addAll(Util.getUserEmailAddresses(this.getSubmitters()));
        addrs.addAll(Util.getUserEmailAddresses(this.getPhotographers()));
        addrs.addAll(Util.getUserEmailAddresses(this.getInformOthers()));
        return addrs;
    }

    // FIXME passing the langCode is dumb imho, but this is "standard practice"
    // better would be that each recipient user's language preference would be used for their email
    public void sendCreationEmails(Shepherd myShepherd, String langCode) {
        String context = myShepherd.getContext();

        if (!CommonConfiguration.sendEmailNotifications(context)) return;
        myShepherd.beginDBTransaction();
        try {
            URI uri = CommonConfiguration.getServerURI(myShepherd);
            if (uri == null) throw new IOException("could not find server uri");
            ThreadPoolExecutor es = MailThreadExecutorService.getExecutorService();
            Properties submitProps = ShepherdProperties.getProperties("submit.properties", langCode,
                context);
            Map<String, String> tagMap = NotificationMailer.createBasicTagMap(this);
            tagMap.put(NotificationMailer.WILDBOOK_COMMUNITY_URL,
                CommonConfiguration.getWildbookCommunityURL(context));
            List<String> mailTo = NotificationMailer.splitEmails(
                CommonConfiguration.getNewSubmissionEmail(context));
            String mailSubj = submitProps.getProperty("newEncounter") + this.getCatalogNumber();
            for (String emailTo : mailTo) {
                NotificationMailer mailer = new NotificationMailer(context, emailTo, langCode,
                    "newSubmission-summary", tagMap);
                mailer.setUrlScheme(uri.getScheme());
                es.execute(mailer);
            }
            // this will be empty if no locationID
            Set<String> locEmails = myShepherd.getAllUserEmailAddressesForLocationIDAsSet(
                this.getLocationID(), context);
            for (String emailTo : locEmails) {
                NotificationMailer mailer = new NotificationMailer(context, langCode, emailTo,
                    "newSubmission-summary", tagMap);
                mailer.setUrlScheme(uri.getScheme());
                es.execute(mailer);
            }
            // Add encounter dont-track tag for remaining notifications (still needs email-hash assigned).
            tagMap.put(NotificationMailer.EMAIL_NOTRACK, "number=" + this.getCatalogNumber());
            // this is a mashup of: submitters, photographers, informOthers....
            for (String emailTo : this.getNotificationEmailAddresses()) {
                tagMap.put(NotificationMailer.EMAIL_HASH_TAG, getHashOfEmailString(emailTo));
                NotificationMailer mailer = new NotificationMailer(context, langCode, emailTo,
                    "newSubmission", tagMap);
                mailer.setUrlScheme(uri.getScheme());
                es.execute(mailer);
            }
            es.shutdown();
        } catch (Exception ex) {
            System.out.println("sendCreationEmails() on " + this + " failed: " + ex);
            ex.printStackTrace();
        } finally {
            myShepherd.rollbackDBTransaction();
        }
    }

/*
    in anticipation of 10.10.0, this is a sketchy of what logging might look like
    for a change in an Encounter via PATCH.

    FIXME this should be fully replaced (or wrapped around) the more basic log functionality
    that is yet to be written. this can serve as an idea of some things which might be
    desirable to support in general logging. take with a grain of salt.
 */
    public void _log(org.json.JSONArray arr) {
        if ((arr == null) || (arr.length() == 0)) return;
        String actionId = Util.generateUUID();
        for (int i = 0; i < arr.length(); i++) {
            if (arr.optJSONObject(i) == null) continue;
            org.json.JSONObject p = arr.getJSONObject(i).optJSONObject("_patch");
            if (p == null) continue;
            String op = p.optString("op", "UNKNOWN_OP");
            String path = p.optString("path", "UNKNOWN_PATH");
            Object value = null;
            if (p.has("value") && !p.isNull("value")) value = p.get("value");
            String logMessage = "modified by PATCH op=" + op + ", path " + path + " with value: " +
                ((value == null) ? "NULL" : value.toString());
            _log2(null, null, null, "Encounter", this.getId(), actionId, logMessage);
            // legacy "audit log" (aka some random comment)
            // TODO really need to add the user/who part here
            this.addComments("<p class=\"patch\" data-action-id=\"" + actionId + "\" data-op=\"" +
                op + "\" data-path=\"" + path + "\"><i>" + Util.prettyTimeStamp() +
                "</i> modified <b>" + path + "</b> with operation <b>" + op + "</b>" + ((value ==
                null) ? "" : " and value <b>" + value.toString() + "</b>") + "</p>");
        }
    }

    // one of many dilemmas: we need a request object to get user + ip address, but dont have
    // it at this point. so we likely need to pass it all around somehow. maybe on a shepherd or
    // some other more common object? FIXME TODO
    public void _log2(
    // "standard" levels (null should be some reasonable default???)
    String logLevel,
    // reference to who did it, could be multiple users etc. probably should be id _and_ username?
    String user,
    // pretty obvious?
    String ip,
    // class of object acted upon
    String objectClass,
    // primary key
    String objectId,
    // for groupin multiple logs into one "action" (e.g. a PATCH on object)
    String actionId,
    // message
    String message) {
        System.out.println(String.join("\t", "[TMPLOG]",
            new Long(System.currentTimeMillis()).toString(), user, ip, objectClass, objectId,
            actionId, message));
    }
}
