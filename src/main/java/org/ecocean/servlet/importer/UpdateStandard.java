package org.ecocean.servlet.importer;

import org.joda.time.LocalDateTime;

import java.io.*;
import java.io.File;
import java.util.*;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.ecocean.*;
import org.ecocean.genetics.*;
import org.ecocean.media.*;
import org.ecocean.servlet.*;
import org.ecocean.tag.SatelliteTag;
import org.joda.time.DateTime;
import org.json.JSONObject;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

public class UpdateStandard extends HttpServlet {
    // variables shared by any single import instance
    Shepherd myShepherd;
    String context;
    Map<String, Integer> colIndexMap = new HashMap<String, Integer>();
    Set<String> unusedColumns;
    Set<String> missingColumns; // columns we look for but don't find
    List<String> missingPhotos = new ArrayList<String>();
    List<String> foundPhotos = new ArrayList<String>();
    int numFolderRows = 0;
    boolean committing = false;
    PrintWriter out;
    // verbose variable can be switched on/off throughout the import for debugging
    boolean verbose = false;
    String photoDirectory;
    String dataSource;
    int numAnnots = 0; // for loggin'

    // these prefixes are added to any individualID, occurrenceID, or sightingID imported
    String individualPrefix = "";
    String occurrencePrefix = "";
    String sightingPlatformPrefix = "";
    String defaultSubmitterID = null; // leave null to not set a default
    String defaultCountry = null;
    int numEncsFound = 0;
    int numEncsMissing = 0;
    int numMediaAssetCols = -1;
    int numNameCols = 2; 
    int rownum = -1; // so we can print this to logs from wherever in the file
    Map<String, MarkedIndividual> individualCache = new HashMap<String, MarkedIndividual>();
    HttpServletRequest request;

    private AssetStore astore;

    // just for lazy loading a var used on each row
    Integer numMediaAssets;

    public void init(ServletConfig config)
    throws ServletException {
        super.init(config);
    }

    public void doGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        doPost(request, response);
    }

    public void doPost(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        this.request = request; // so we can access this elsewhere without passing it around
        String importId = Util.generateUUID();
        if (request.getCharacterEncoding() == null) {
            request.setCharacterEncoding("utf-8");
        }
        response.setContentType("text/html; charset=UTF-8");
        context = ServletUtilities.getContext(request);
        myShepherd = new Shepherd(context);
        out = response.getWriter();
        astore = getAssetStore(myShepherd);
        if (astore != null) {
            System.out.println("astore is OK!");
        } else {
            System.out.println("astore is null...BOO!!");
            out.println("<p>I could not find a default AssetStore. Please create one.</p>");
            return;
        }
        numEncsFound = 0;
        numEncsMissing = 0;

        // this might better be set via a different configuration variable of its own
        // String filename = Util.safePath(request.getParameter("filename"));
        // if (filename == null) filename = "upload.xlsx";  //meh?

        // Thus MUST be full path, such as: /import/NEAQ/converted/importMe.xlsx
        String filename = request.getParameter("filename");
        File dataFile = new File(filename);
        if (filename == null) {
            System.out.println("Filename request parameter was not set in the URL.");
            out.println(
                "<p>I could not find a filename parameter in the URL. Please specify the full path on the server file system to the Excel import file as the ?filename= parameter on the URL.</p><p>Please note: the importer assumes that all image files exist in the same folder as the Excel file or are relatively referenced in the Excel file within a subdirectory.</p><p>Example value: ?filename=/import/MyNewData/importMe.xlsx</p>");
            return;
        }
        if (!dataFile.exists()) {
            out.println(
                "<p>I found a filename parameter in the URL, but I couldn't find the file itself at the path your specified: "
                + filename + "</p>");
            return;
        }
        String uploadDir = dataFile.getParentFile().getAbsolutePath();

        // String subdir = Util.safePath(request.getParameter("subdir"));
        // if (subdir != null) uploadDir += subdir;
        photoDirectory = uploadDir + "/";

        boolean dataFound = dataFile.exists();

        missingColumns = new HashSet<String>();
        missingPhotos = new ArrayList<String>();
        foundPhotos = new ArrayList<String>();
        numFolderRows = 0;
        numAnnots = 0;

        committing = Util.requestParameterSet(request.getParameter("commit"));

        out.println("<h2>File Overview: </h2>");
        out.println("<ul>");
        out.println("<li>Directory: " + uploadDir + "</li>");
        out.println("<li>Filename: " + filename + "</li>");
        out.println("<li>File found = " + dataFound + "</li>");

        Workbook wb = null;
        try {
            wb = WorkbookFactory.create(dataFile);
        } catch (org.apache.poi.openxml4j.exceptions.InvalidFormatException invalidFormat) {
            out.println("<err>InvalidFormatException on input file " + filename +
                ". Only excel files supported.</err>");
            return;
        }
        Sheet sheet = wb.getSheetAt(0);
        int numSheets = wb.getNumberOfSheets();
        out.println("<li>Num Sheets = " + numSheets + "</li>");
        int physicalNumberOfRows = sheet.getPhysicalNumberOfRows();
        out.println("<li>Num Rows = " + physicalNumberOfRows + "</li>");
        int rows = sheet.getPhysicalNumberOfRows(); // No of rows
        Row firstRow = sheet.getRow(0);
        // below line is important for on-screen logging
        initColIndexVariables(firstRow);
        int cols = firstRow.getPhysicalNumberOfCells(); // No of columns
        int lastColNum = firstRow.getLastCellNum();

        out.println("<li>Num Cols = " + cols + "</li>");
        out.println("<li>Last col num = " + lastColNum + "</li>");
        out.println("<li><em>committing = " + committing + "</em></li>");
        out.println("</ul>");
        out.println("<h2>Column headings:</h2><ul>");
        out.println("<li>number columns = " + colIndexMap.size() + "</li>");
        out.println("<li>num Media Asset cols = " + numMediaAssets + "</li>");
        for (String heading : Util.asSortedList(colIndexMap.keySet()))
            out.println("<li>" + colIndexMap.get(heading) + ": " + heading + "</li>");
        out.println("</ul>");

        int printPeriod = 50;
        LocalDateTime ldt = new LocalDateTime();
        String importComment = "<p style=\"import-comment\">import <i>" + importId + "</i> at " +
            ldt.toString() + "</p>";
        System.out.println("===== importId " + importId + " (committing=" + committing + ")");
        if (committing) myShepherd.beginDBTransaction();
        out.println("<h2>Beginning row loop:</h2>");
        out.println("<ul>");
        // one encounter per-row. We keep these running.
        Occurrence occ = null;
        int offset = 0;
        int numRows = 5000000;
        for (rownum = 1 + offset; rownum < rows && rownum < (numRows + offset); rownum++) {
            MarkedIndividual mark = null;
            verbose = ((rownum % printPeriod) == 0);
            try {
                if (committing) myShepherd.beginDBTransaction();
                Row row = sheet.getRow(rownum);
                if (isRowEmpty(row)) continue;
                System.out.println("STANDARD IMPORT: processing row " + rownum);

                // here's the central logic
                // ArrayList<Annotation> annotations = loadAnnotations(row);
                // numAnnots+=annotations.size();
                Encounter enc = loadEncounter(row);
                if (enc == null) continue;
                enc.addComments(importComment);
                occ = loadOccurrence(row, occ, enc);
                occ.addComments(importComment);
                mark = loadIndividual(row, enc);
                if (mark != null) individualCache.put(mark.getName(), mark);
                if (committing) {

                    myShepherd.commitDBTransaction();
                    myShepherd.beginDBTransaction();
                }
                if (verbose) {
                    out.println("<li>Parsed row (" + rownum + ")<ul>" + "<li> Enc " +
                        getEncounterDisplayString(enc) + " <ul>");
                    for (MediaAsset ma : enc.getMedia()) {
                        out.println("<li>" + ma.toString() + ", filename " + ma.getFilename() +
                            "<ul>");
                        for (Keyword kw : ma.getKeywords()) {
                            out.println("<li>" + kw.toString() + "</li>");
                        }
                        out.println("</ul></li>");
                    }
                    out.println("</ul></li>" + "<li> individual " + mark + "</li>" +
                        "<li> occurrence " + occ + "</li>" + "<li> dateInMillis " +
                        enc.getDateInMilliseconds() + "</li>" + "<li> sex " + enc.getSex() +
                        "</li>" + "<li> lifeStage " + enc.getLifeStage() + "</li>" + "</ul></li>");
                }
            } catch (Exception e) {
                out.println("Encountered an error while importing the file.");
                e.printStackTrace(out);
                myShepherd.rollbackDBTransaction();
            }
        }
        out.println("</ul>");

        out.println("<h2><em>UNUSED</em> Column headings (" + unusedColumns.size() + "):</h2><ul>");
        for (String heading : Util.asSortedList(unusedColumns)) {
            out.println("<li>" + heading + "</li>");
        }
        out.println("</ul>");

        List<String> usedColumns = new ArrayList<String>();
        for (String colName : colIndexMap.keySet()) {
            if (!unusedColumns.contains(colName)) usedColumns.add(colName);
        }
        out.println("<h2><em>USED</em> Column headings (" + usedColumns.size() + "):</h2><ul>");
        for (String heading : Util.asSortedList(usedColumns)) {
            out.println("<li>" + heading + "</li>");
        }
        out.println("</ul>");

        out.println("<h2><em>Missing photos</em>(" + missingPhotos.size() + "):</h2><ul>");
        for (String photo : Util.asSortedList(missingPhotos)) {
            out.println("<li>" + photo + "</li>");
        }
        out.println("</ul>");

        out.println("<h2><em>Found photos</em>(" + foundPhotos.size() + "):</h2><ul>");
        for (String photo : Util.asSortedList(foundPhotos)) {
            out.println("<li>" + photo + "</li>");
        }
        out.println("</ul>");

        out.println("<ul>");
        out.println("<li>Num encs found = " + numEncsFound + "</li>");
        out.println("<li>Num encs missing = " + numEncsMissing + "</li>");
        out.println("</ul>");

        out.println("<h2><strong> " + numFolderRows + " </strong> Folder Rows</h2>");
        out.println("<h2><strong> " + numAnnots + " </strong> annots</h2>");

        out.println("<h2>Import completed successfully</h2>");
        if (committing) out.println("<p>Import reference ID <b>" + importId + "</b></p>");
        // fs.close();
    }

    public Taxonomy loadTaxonomy0(Row row) {
        String sciName = getString(row, "Taxonomy.scientificName");

        if (sciName == null) return null;
        Taxonomy taxy = myShepherd.getOrCreateTaxonomy(sciName);
        String commonName = getString(row, "Taxonomy.commonName");
        if (commonName != null) taxy.addCommonName(commonName);
        return taxy;
    }

    public Taxonomy loadTaxonomy1(Row row) {
        String sciName = getString(row, "Occurrence.taxonomy1");

        if (sciName == null) return null;
        return myShepherd.getOrCreateTaxonomy(sciName);
    }

    public static boolean validCoord(Double latOrLon) {
        return (latOrLon != null && latOrLon != 0.0);
    }

    public Occurrence loadOccurrence(Row row, Occurrence oldOcc, Encounter enc) {
        Occurrence occ = getCurrentOccurrence(oldOcc, row);
        // would love to have a more concise way to write following couplets, c'est la vie
        Double seaSurfaceTemp = getDouble(row, "Occurrence.seaSurfaceTemperature");

        if (seaSurfaceTemp == null) seaSurfaceTemp = getDouble(row, "Occurrence.seaSurfaceTemp");
        if (seaSurfaceTemp != null) occ.setSeaSurfaceTemp(seaSurfaceTemp);
        Integer individualCount = getInteger(row, "Occurrence.individualCount");
        if (individualCount != null) occ.setIndividualCount(individualCount);
        // covers a typo on some decimalLatitude headers ("decimalLatitiude" note the extra i in Latitiude)
        Double decimalLatitiude = getDouble(row, "Encounter.decimalLatitiude");
        if (validCoord(decimalLatitiude)) occ.setDecimalLatitude(decimalLatitiude);
        Double decimalLatitude = getDouble(row, "Encounter.decimalLatitude");
        if (validCoord(decimalLatitude)) occ.setDecimalLatitude(decimalLatitude);
        Double decimalLongitude = getDouble(row, "Encounter.decimalLongitude");
        if (validCoord(decimalLongitude)) occ.setDecimalLongitude(decimalLongitude);
        String fieldStudySite = getString(row, "Occurrence.fieldStudySite");
        // fieldStudySite defaults to locationID
        if (fieldStudySite == null) fieldStudySite = getString(row, "Encounter.locationID");
        if (fieldStudySite != null) occ.setFieldStudySite(fieldStudySite);
        String groupComposition = getString(row, "Occurrence.groupComposition");
        if (groupComposition != null) occ.setGroupComposition(groupComposition);
        String groupBehavior = getString(row, "Occurrence.groupBehavior");
        // if no groupBehavior we want the behavior from an Encounter to copy over for occurrence searches
        // this makes sense semantically since many people view the world occurrence-first
        if (groupBehavior == null) groupBehavior = getString(row, "Encounter.behavior");
        if (groupBehavior != null) occ.setGroupBehavior(groupBehavior);
        String fieldSurveyCode = getStringOrInt(row, "Survey.id");
        if (fieldSurveyCode == null) fieldSurveyCode = getString(row, "Occurrence.fieldSurveyCode");
        if (fieldSurveyCode != null) occ.setFieldSurveyCode(fieldSurveyCode);
        String sightingPlatform = getString(row, "Survey.vessel");
        if (sightingPlatform == null) sightingPlatform = getString(row, "Platform Designation");
        if (sightingPlatform != null)
            occ.setSightingPlatform(sightingPlatformPrefix + sightingPlatform);
        String surveyComments = getString(row, "Survey.comments");
        if (surveyComments != null) occ.addComments(surveyComments);
        String comments = getString(row, "Occurrence.comments");
        if (comments != null) occ.addComments(comments);
        Integer numAdults = getInteger(row, "Occurrence.numAdults");
        if (numAdults != null) occ.setNumAdults(numAdults);
        Integer minGroupSize = getInteger(row, "Occurrence.minGroupSizeEstimate");
        if (minGroupSize != null) occ.setMinGroupSizeEstimate(minGroupSize);
        Integer maxGroupSize = getInteger(row, "Occurrence.maxGroupSizeEstimate");
        if (maxGroupSize != null) occ.setMaxGroupSizeEstimate(maxGroupSize);
        Double bestGroupSize = getDouble(row, "Occurrence.bestGroupSizeEstimate");
        if (bestGroupSize != null) occ.setBestGroupSizeEstimate(bestGroupSize);
        Integer numCalves = getInteger(row, "Occurrence.numCalves");
        if (numCalves != null) occ.setNumCalves(numCalves);
        Integer numJuveniles = getInteger(row, "Occurrence.numJuveniles");
        if (numJuveniles != null) occ.setNumJuveniles(numJuveniles);
        Double bearing = getDouble(row, "Occurrence.bearing");
        if (bearing != null) occ.setBearing(bearing);
        Double distance = getDouble(row, "Occurrence.distance");
        if (distance != null) occ.setDistance(distance);
        Double swellHeight = getDouble(row, "Occurrence.swellHeight");
        if (swellHeight != null) occ.setSwellHeight(swellHeight);
        String seaState = getString(row, "Occurrence.seaState");
        if (seaState == null) {
            Integer intSeaState = getInteger(row, "Occurrence.seaState");
            if (intSeaState != null) seaState = intSeaState.toString();
        }
        if (seaState != null) occ.setSeaState(seaState);
        Double visibilityIndex = getDouble(row, "Occurrence.visibilityIndex");
        if (visibilityIndex == null) {
            Integer visIndexInt = getIntFromMap(row, "Occurrence.visibilityIndex");
            if (visIndexInt != null) visibilityIndex = visIndexInt.doubleValue();
        }
        if (visibilityIndex != null) occ.setVisibilityIndex(visibilityIndex);
        Double transectBearing = getDouble(row, "Occurrence.transectBearing");
        if (transectBearing != null) occ.setTransectBearing(transectBearing);
        String transectName = getString(row, "Occurrence.transectName");
        if (transectName != null) occ.setTransectName(transectName);
        String initialCue = getString(row, "Occurrence.initialCue");
        String humanActivity = getString(row, "Occurrence.humanActivityNearby");
        if (humanActivity != null) occ.setHumanActivityNearby(humanActivity);
        Double effortCode = getDouble(row, "Occurrence.effortCode");
        if (effortCode != null) occ.setEffortCode(effortCode);
        Taxonomy taxy = loadTaxonomy0(row);
        if (taxy != null) occ.addTaxonomy(taxy);
        Taxonomy taxy1 = loadTaxonomy1(row);
        if (taxy1 != null) occ.addTaxonomy(taxy1);
        String surveyTrackVessel = getString(row, "SurveyTrack.vesselID");
        if (surveyTrackVessel != null) occ.setSightingPlatform(surveyTrackVessel);
        String observer = getString(row, "Occurrence.observer");
        if (observer != null) occ.setObserver(observer);
        Long millis = getLong(row, "Encounter.dateInMilliseconds");
        if (millis == null) millis = getLong(row, "Occurrence.dateInMilliseconds");
        if (millis == null) millis = getLong(row, "Occurrence.millis");
        if (millis != null) occ.setDateTimeLong(millis);
        String occurrenceRemarks = getString(row, "Encounter.occurrenceRemarks");
        if (occurrenceRemarks != null) occ.addComments(occurrenceRemarks);
        if (enc != null) {
            occ.addEncounter(enc);
            // overwrite=false on following fromEncs methods
            occ.setLatLonFromEncs(false);
            occ.setSubmitterIDFromEncs(false);
        }
        return occ;
    }

    public Encounter loadEncounter(Row row) {
        // try to load encounter by indID and occID, make a new one if it doesn't exist.
        String individualID = getIndividualID(row);
        String occurrenceID = getOccurrenceID(row);

        // since we need access to the encounter ID
        String encID = getString(row, "Encounter.catalogNumber");

        if (!Util.stringExists(encID)) {
            return null;
        }
        Encounter enc = myShepherd.getEncounter(encID);
        if (enc == null) {
            numEncsMissing++;
            return enc;
        }
        numEncsFound++;

        // annotations
        loadAnnotations(enc, row);
        for (int i = 0; i < 3; i++) {
            loadEncounterName(row, enc, i);
        }
        if (occurrenceID != null) enc.setOccurrenceID(occurrenceID);
        // Data source
        if (dataSource != null) enc.setDataSource(dataSource);
        // Time
        Integer year = getInteger(row, "Encounter.year");
        if (year == null) year = getInteger(row, "Occurrence.year");
        if (year != null) enc.setYear(year);
        Integer month = getInteger(row, "Encounter.month");
        if (month == null) month = getInteger(row, "Occurrence.month");
        if (month != null) enc.setMonth(month);
        Integer day = getInteger(row, "Encounter.day");
        if (day == null) day = getInteger(row, "Occurrence.day");
        if (day != null) enc.setDay(day);
        Integer hour = getInteger(row, "Encounter.hour");
        if (hour == null) hour = getInteger(row, "Occurrence.hour");
        if (hour != null) enc.setHour(hour);
        String minutes = getIntAsString(row, "Encounter.minutes");
        if (minutes == null) minutes = getIntAsString(row, "Occurrence.minutes");
        if (minutes != null) enc.setMinutes(minutes);
        Double depth = getDouble(row, "Encounter.depth");
        if (depth != null) enc.setDepth(depth);
        // setting milliseconds last means that (if provided) the exif/millis data will always take precedence
        // if we set it before, enc.setMinutes & others would reset millis
        Long millis = getLong(row, "Encounter.dateInMilliseconds");
        if (millis == null) millis = getLong(row, "Occurrence.dateInMilliseconds");
        if (millis == null) millis = getLong(row, "Occurrence.millis");
        boolean hasTimeCategories = (year != null || month != null || day != null || hour != null ||
            minutes != null);
        if (millis != null) {
            if (hasTimeCategories) enc.setDateInMillisOnly(millis); // does not overwrite day/month/etc
            else enc.setDateInMilliseconds(millis);
        }
        // Location
        Double latitude = getDouble(row, "Encounter.latitude");
        if (latitude == null) latitude = getDouble(row, "Encounter.decimalLatitude");
        if (latitude == null) latitude = getDouble(row, "Occurrence.decimalLatitude");
        if (latitude == null) latitude = getDouble(row, "Encounter.decimalLatitiude");
        if (validCoord(latitude)) enc.setDecimalLatitude(latitude);
        Double longitude = getDouble(row, "Encounter.longitude");
        if (longitude == null) longitude = getDouble(row, "Encounter.decimalLongitude");
        if (longitude == null) longitude = getDouble(row, "Occurrence.decimalLongitude");
        if (validCoord(longitude)) enc.setDecimalLongitude(longitude);
        String locationID = getString(row, "Encounter.locationID");
        if (locationID != null) enc.setLocationID(locationID);
        String country = getString(row, "Encounter.country");
        if (country != null) enc.setCountry(country);
        // String fields
        String otherCatalogNumbers = getStringOrInt(row, "Encounter.otherCatalogNumbers");
        if (otherCatalogNumbers != null) enc.setOtherCatalogNumbers(otherCatalogNumbers);
        String sex = getString(row, "Encounter.sex");
        if (sex != null) enc.setSex(sex);
        String genus = getString(row, "Encounter.genus");
        if (genus != null) enc.setGenus(genus);
        String specificEpithet = getString(row, "Encounter.specificEpithet");
        if (specificEpithet != null) enc.setSpecificEpithet(specificEpithet);
        String submitterOrganization = getString(row, "Encounter.submitterOrganization");
        if (submitterOrganization != null) enc.setSubmitterOrganization(submitterOrganization);
        String submitterName = getString(row, "Encounter.submitterName");
        if (submitterName != null) enc.setSubmitterName(submitterName);
        Integer patterningCode = getInteger(row, "Encounter.patterningCode");
        if (patterningCode != null) enc.setFlukeType(patterningCode);
        String occurrenceRemarks = getString(row, "Encounter.occurrenceRemarks");
        if (occurrenceRemarks != null) enc.setOccurrenceRemarks(occurrenceRemarks);
        String recordedBy = getString(row, "Encounter.recordedBy");
        if (recordedBy != null) enc.setRecordedBy(recordedBy);
        String submitterID = getString(row, "Encounter.submitterID");
        // don't commit this line
        if (submitterID == null) submitterID = defaultSubmitterID;
        if (submitterID != null) enc.setSubmitterID(submitterID);
        String behavior = getString(row, "Encounter.behavior");
        if (behavior != null) enc.setBehavior(behavior);
        String lifeStage = getString(row, "Encounter.lifeStage");
        if (lifeStage != null) enc.setLifeStage(lifeStage);
        String groupRole = getString(row, "Encounter.groupRole");
        if (groupRole != null) enc.setGroupRole(groupRole);
        String researcherComments = getString(row, "Encounter.researcherComments");
        if (researcherComments != null) enc.addComments(researcherComments);
        String verbatimLocality = getString(row, "Encounter.verbatimLocality");
        if (verbatimLocality != null) enc.setVerbatimLocality(verbatimLocality);
        String nickname = getString(row, "MarkedIndividual.nickname");
        if (nickname == null) nickname = getString(row, "MarkedIndividual.nickName");
        // if (nickname!=null) enc.setAlternateID(nickname);

        String alternateID = getString(row, "Encounter.alternateID");
        if (alternateID != null) enc.setAlternateID(alternateID);
        /*
         * Start measurements import
         */
        List<String> measureVals = (List<String>)CommonConfiguration.getIndexedPropertyValues(
            "measurement", context);
        List<String> measureUnits = (List<String>)CommonConfiguration.getIndexedPropertyValues(
            "measurementUnits", context);
        int numMeasureVals = measureVals.size();
        for (int bg = 0; bg < numMeasureVals; bg++) {
            String colName = "Encounter.measurement" + bg;
            Double val = getDouble(row, colName);
            if (val != null) {
                Measurement valMeas = new Measurement(encID, measureVals.get(bg), val,
                    measureUnits.get(bg), "");
                if (committing) enc.setMeasurement(valMeas, myShepherd);
                if (unusedColumns != null) unusedColumns.remove(colName);
            }
        }
        /*
         * End measurements import
         */

        /*
         * Start Submitter imports
         */
        boolean hasSubmitters = true;
        int startIter = 0;
        while (hasSubmitters) {
            String colEmail = "Encounter.submitter" + startIter + ".emailAddress";
            String val = getString(row, colEmail);
            if (val != null) {
                if (myShepherd.getUserByEmailAddress(val.trim()) != null) {
                    User thisPerson = myShepherd.getUserByEmailAddress(val.trim());
                    if ((enc.getSubmitters() == null) ||
                        !enc.getSubmitters().contains(thisPerson)) {
                        if (committing) enc.addSubmitter(thisPerson);
                        if (unusedColumns != null) unusedColumns.remove(colEmail);
                    }
                } else {
                    // create a new User
                    User thisPerson = new User(val.trim(), Util.generateUUID());
                    if (committing) enc.addSubmitter(thisPerson);
                    if (unusedColumns != null) unusedColumns.remove(colEmail);
                    String colFullName = "Encounter.submitter" + startIter + ".fullName";
                    String val2 = getString(row, colFullName);
                    if (val2 != null) thisPerson.setFullName(val2.trim());
                    if (unusedColumns != null) unusedColumns.remove(colFullName);
                    String colAffiliation = "Encounter.submitter" + startIter + ".affiliation";
                    String val3 = getString(row, colAffiliation);
                    if (val3 != null) thisPerson.setAffiliation(val3.trim());
                    if (unusedColumns != null) unusedColumns.remove(colAffiliation);
                }
                startIter++;
            } else {
                hasSubmitters = false;
            }
        }
        /*
         * End Submitter imports
         */

        /*
         * Start Photographer imports
         */
        boolean hasPhotographers = true;
        startIter = 0;
        while (hasPhotographers) {
            String colEmail = "Encounter.photographer" + startIter + ".emailAddress";
            String val = getString(row, colEmail);
            if (val != null) {
                if (myShepherd.getUserByEmailAddress(val.trim()) != null) {
                    User thisPerson = myShepherd.getUserByEmailAddress(val.trim());
                    if ((enc.getPhotographers() == null) ||
                        !enc.getPhotographers().contains(thisPerson)) {
                        if (committing) enc.addPhotographer(thisPerson);
                        if (unusedColumns != null) unusedColumns.remove(colEmail);
                    }
                } else {
                    // create a new User
                    User thisPerson = new User(val.trim(), Util.generateUUID());
                    if (committing) enc.addPhotographer(thisPerson);
                    if (unusedColumns != null) unusedColumns.remove(colEmail);
                    String colFullName = "Encounter.photographer" + startIter + ".fullName";
                    String val2 = getString(row, colFullName);
                    if (val2 != null) thisPerson.setFullName(val2.trim());
                    if (unusedColumns != null) unusedColumns.remove(colFullName);
                    String colAffiliation = "Encounter.photographer" + startIter + ".affiliation";
                    String val3 = getString(row, colAffiliation);
                    if (val3 != null) thisPerson.setAffiliation(val3.trim());
                    if (unusedColumns != null) unusedColumns.remove(colAffiliation);
                }
                startIter++;
            } else {
                hasPhotographers = false;
            }
        }
        /*
         * End Photographer imports
         */

        // SAMPLES
        TissueSample sample = null;
        String tissueSampleID = getStringOrInt(row, "TissueSample.sampleID");
        // we need to make sure we have a sampleID whenever we have a microsat marker
        if (tissueSampleID == null)
            tissueSampleID = getStringOrInt(row, "MicrosatelliteMarkersAnalysis.analysisID");
        // same for sex analysis
        if (tissueSampleID == null)
            tissueSampleID = getStringOrInt(row, "SexAnalysis.processingLabTaskID");
        if (tissueSampleID != null) {
            sample = myShepherd.getTissueSample(tissueSampleID, encID);
            if (sample == null) sample = new TissueSample(enc.getCatalogNumber(), tissueSampleID);
        }
        String markerAnalysisID = getStringOrInt(row, "MicrosatelliteMarkersAnalysis.analysisID");
        // we need to add uniqueness to the parsed string bc it's a primary key
        // but adding full encID is too long of a string.
        if (markerAnalysisID != null)
            markerAnalysisID = markerAnalysisID + "-enc-" + encID.substring(0,
                Math.min(8, encID.length()));
        if (markerAnalysisID != null && !myShepherd.isGeneticAnalysis(markerAnalysisID)) {
            markerAnalysisID = markerAnalysisID.replaceAll("_", "-");
            MicrosatelliteMarkersAnalysis microMark = myShepherd.getMicrosatelliteMarkersAnalysis(
                markerAnalysisID);
            if (microMark == null) {
                microMark = new MicrosatelliteMarkersAnalysis(markerAnalysisID, tissueSampleID,
                    encID);
                if (sample != null) sample.addGeneticAnalysis(microMark);
            } // if microMark was grabbed from Shepherd correctly there is no further data to store.
        }
        String sexAnalID = getStringOrInt(row, "SexAnalysis.processingLabTaskID");
        String sexAnalSex = getString(row, "SexAnalysis.sex");
        if (sexAnalID != null) {
            // we need to add uniqueness to the parsed string bc it's a primary key
            // but adding full encID is too long of a string.
            sexAnalID = sexAnalID + "-enc-" + encID.substring(0, Math.min(8, encID.length()));
            sexAnalID = sexAnalID.replaceAll("_", "-");
        }
        if (sexAnalID != null && sexAnalSex != null && !myShepherd.isGeneticAnalysis(sexAnalID)) {
            SexAnalysis sexAnal = myShepherd.getSexAnalysis(sexAnalID);
            if (sexAnal == null) {
                sexAnal = new SexAnalysis(sexAnalID, sexAnalSex, encID, tissueSampleID);
                if (sample != null) sample.addGeneticAnalysis(sexAnal);
            } else sexAnal.setSex(sexAnalSex);
        }
        if (sample != null) enc.addTissueSample(sample);
        // END SAMPLES

        String satelliteTag = getString(row, "SatelliteTag.serialNumber");
        if (satelliteTag != null) {
            SatelliteTag tag = new SatelliteTag("", satelliteTag, ""); // note the empty fields. sat tags are weird.
            enc.setSatelliteTag(tag);
        }
        String caudalType = getIntAsString(row, "Type caudale Mn");
        if (caudalType != null) {
            enc.setDynamicProperty("caudal type", caudalType);
        }
        enc.setState("approved");
        return enc;
    }

    public Set<String> getColumnFieldsForClass(String className) {
        Set<String> fieldNames = new HashSet<String>();

        try {
            for (String columnHeader : colIndexMap.keySet()) {
                if (columnHeader.contains(className + ".")) {
                    fieldNames.add(columnHeader.split(className + ".")[1]); // for Encounter.date returns date
                }
            }
        } catch (Exception e) {}
        return fieldNames;
    }

    public ArrayList<Annotation> loadAnnotations(Encounter enc, Row row) {
        // if (isFolderRow(row)) return loadAnnotationsFolderRow(row);
        ArrayList<Annotation> annots = new ArrayList<Annotation>();

        for (int i = 0; i < getNumMediaAssets(); i++) {
            MediaAsset ma = getMediaAsset(enc, row, i);
            if (ma == null) continue;
        }
        return annots;
    }

    // for when the provided image filename is actually a folder of images
    private ArrayList<Annotation> loadAnnotationsFolderRow(Row row) {
        ArrayList<Annotation> annots = new ArrayList<Annotation>();
        String localPath = getString(row, "Encounter.mediaAsset0");

        if (localPath == null) return annots;
        localPath = localPath.substring(0, localPath.length() - 1); // removes trailing asterisk
        localPath = fixGlobiceFullPath(localPath) + "/";
// localPath = localPath.replace(" ","\\ ");
        String fullPath = photoDirectory + localPath;
        // Globice fix!
        // now fix spaces
        File photoDir = new File(fullPath);
        if (!photoDir.exists() || !photoDir.isDirectory() || photoDir.listFiles() == null) {
            boolean itExists = photoDir.exists();
            boolean isDirectory = (itExists) && photoDir.isDirectory();
            boolean hasFiles = isDirectory && photoDir.listFiles() != null;
            System.out.println(
                "StandardImport ERROR: loadAnnotationsFolderRow called on non-directory (or empty?) path "
                + fullPath);
            System.out.println("\t\titExists: " + itExists);
            System.out.println("\t\tisDirectory: " + isDirectory);
            System.out.println("\t\thasFiles: " + hasFiles);
            missingPhotos.add(localPath);
            return annots;
        }
        // if there are keywords we apply to all photos in encounter
        String keyword0 = getString(row, "Encounter.keyword00");
        Keyword key0 = (keyword0 == null) ? null : myShepherd.getOrCreateKeyword(keyword0);
        String keyword1 = getString(row, "Encounter.keyword01");
        Keyword key1 = (keyword1 == null) ? null : myShepherd.getOrCreateKeyword(keyword1);
        String species = getSpeciesString(row);
        for (File f : photoDir.listFiles()) {
            MediaAsset ma = null;
            try {
                JSONObject assetParams = astore.createParameters(f);
                System.out.println("\t\thave assetParams");
                assetParams.put("_localDirect", f.toString());
                System.out.println("\t\tabout to create mediaAsset");
                ma = astore.copyIn(f, assetParams);
            } catch (Exception e) {
                System.out.println("IOException creating MediaAsset for file " + f.getPath());
                missingPhotos.add(f.getPath());
                continue; // skips the rest of loop for this file
            }
            if (ma == null) continue;
            if (key0 != null) ma.addKeyword(key0);
            if (key1 != null) ma.addKeyword(key1);
            Annotation ann = new Annotation(species, ma);
            ann.setIsExemplar(true);
            annots.add(ann);
        }
        if (annots.size() > 0) foundPhotos.add(fullPath);
        return annots;
    }

    // capitolizes the final directory in path
    private String fixGlobiceFullPath(String path) {
        String fixed = capitolizeLastFilepart(path);

        fixed = removeExtraGlobiceString(fixed);
        return fixed;
    }

    private String removeExtraGlobiceString(String path) {
        // we somehow got an extra instance of the word "globice" in the path string, right before a 1
        return (path.replace("Globice1", "1"));
    }

    private String capitolizeLastFilepart(String path) {
        String[] parts = path.split("/");
        String lastPart = parts[parts.length - 1];
        String firstPart = path.substring(0, path.indexOf(lastPart));

        return firstPart + lastPart.toUpperCase();
    }

    // most rows have a single image, but some have an image folder
    private boolean isFolderRow(Row row) {
        String path = getString(row, "Encounter.mediaAsset0");

        if (path == null) return false;
        boolean ans = path.endsWith("*");
        if (ans) numFolderRows++;
        return ans;
    }

    public String getSpeciesString(Row row) {
        String genus = getString(row, "Encounter.genus");
        String species = getString(row, "Encounter.species");
        String total = genus + " " + species;

        if (total == null || total.equals(" ")) total = "unknown";
        return total;
    }

    public String getIndividualID(Row row) {
        String indID = getStringOrInt(row, "Encounter.individualID");

        if (indID == null) indID = getStringOrInt(row, "MarkedIndividual.individualID");
        if (!Util.stringExists(indID)) return indID;
        return individualPrefix + indID;
    }

    public MediaAsset getMediaAsset(Encounter enc, Row row, int i) {
        String filename = getString(row, "Encounter.mediaAsset" + i);

        if (filename == null) return null;
        MediaAsset ma = enc.getMediaAssetByFilename(filename);
        if (ma == null) missingPhotos.add(filename);
        else foundPhotos.add(filename);
        if (filename.contains("picasa") && ma != null) {
            System.out.println("Found picasa asset " + ma + " on enc " + enc);
        }
        ArrayList<Keyword> kws = getKeywordsForAsset(row, i);
        if (kws != null && kws.size() > 0) {
            System.out.println("on row " + rownum + " returning " + kws.size() + " kws: " + kws);
            ma.setKeywords(kws);
        }
        System.out.println("getMediaAsset got asset from filename " + filename + ": " + ma);
        return ma;
    }

    private ArrayList<Keyword> getKeywordsForAsset(Row row, int n) {
        System.out.println("calling getKeywordsForAsset");
        // get labeledKeywords first
        ArrayList<Keyword> ans = getLabeledKeywords(row, n);
        int maxAssets = getNumAssets(row);
        int maxKeywords = 4;
        int stopAtKeyword = (maxAssets == (n + 1)) ? maxKeywords : n; //
        // we have up to 4 keywords per row.
        String kwColPrefix = "Encounter.mediaAsset" + n + ".keyword";
        for (int i = 0; i <= maxKeywords; i++) {
            String kwColName = kwColPrefix + i;
            String kwName = getString(row, kwColName);
            if (kwName == null) {
                kwColName = "Encounter.keyword0" + i;
                kwName = getString(row, kwColName);
            }
            if (kwName == null) continue;
            Keyword kw = myShepherd.getOrCreateKeyword(kwName);
            if (kw != null) ans.add(kw);
        }
        // idk how these are getting into the list but we need to remove labeled kws with null labels
        ArrayList<Keyword> cleanedAns = new ArrayList<Keyword>();
        for (Keyword kw : ans) {
            if (kw instanceof LabeledKeyword) {
                LabeledKeyword lkw = (LabeledKeyword)kw;
                if (Util.stringExists(lkw.getLabel())) cleanedAns.add(lkw);
            } else cleanedAns.add(kw);
        }
        return cleanedAns;
    }

    private Keyword getLabeledKeyword(Row row, int n, String label) {
        String colName = "Encounter." + label + n; // Encounter.flukeType5

        if (!colIndexMap.containsKey(colName)) return null;
        String value = getString(row, colName);
        if (value == null) return null;
        System.out.println("getLabeledKeyword success! row " + rownum + ", n=" + n + ", label " +
            label);
        return myShepherd.getOrCreateLabeledKeyword(label, value, committing);
    }

    private ArrayList<Keyword> getLabeledKeywords(Row row, int n) {
        ArrayList<Keyword> keys = new ArrayList<Keyword>();

        for (String label : LabeledKeyword.LABELS) {
            if (label == null) continue; // why is this happening?
            Keyword kw = getLabeledKeyword(row, n, label);
            if (kw != null) keys.add(kw);
        }
        if (verbose)
            System.out.println("getLabeledKeywords returning " + keys.size() + " keywords on row " +
                rownum + ", n=" + n);
        return keys;
    }

    private ArrayList<Keyword> getKeywordForAsset(Row row, int n) {
        ArrayList<Keyword> ans = new ArrayList<Keyword>();
        String kwColName = "Encounter.keyword" + n;
        String kwName = getString(row, kwColName);

        if (kwName == null) {
            kwColName = "Encounter.keyword0" + n;
            kwName = getString(row, kwColName);
        }
        if (kwName == null) return ans;
        Keyword kw = myShepherd.getOrCreateKeyword(kwName);
        if (kw != null) ans.add(kw);
        return ans;
    }

    private int getNumAssets(Row row) {
        int n = 0;

        while (getString(row, "Encounter.mediaAsset" + n) != null) { n++; }
        return n;
    }

    // Checks common human errors in inputing filenames
    // and returns the most similar filename that actually exists on the server
    // returns null if it cannot find a good string
    private String resolveHumanEnteredFilename(String fullPath) {
        if (Util.fileExists(fullPath)) return fullPath;
        String candidatePath = uppercaseJpg(fullPath);
        if (Util.fileExists(candidatePath)) return candidatePath;
        String candidatePath2 = uppercaseBeforeJpg(candidatePath);
        if (Util.fileExists(candidatePath2)) return candidatePath2;
        candidatePath = lowercaseJpg(fullPath);
        if (Util.fileExists(candidatePath)) return candidatePath;
        candidatePath = fixSpaceBeforeJpg(candidatePath);
        if (Util.fileExists(candidatePath)) return candidatePath;
        candidatePath = fixSpaceBeforeDotJpg(candidatePath);
        if (Util.fileExists(candidatePath)) return candidatePath;
        candidatePath = removeSpaceDashSpaceBeforeDot(candidatePath);
        if (Util.fileExists(candidatePath)) return candidatePath;
        return null;
    }

    private String uppercaseBeforeJpg(String filename) {
        // uppercases the section between final slash and .jpg
        if (filename == null) return null;
        int indexOfDotJpg = filename.indexOf(".jpg");
        if (indexOfDotJpg == -1) indexOfDotJpg = filename.indexOf(".JPG");
        int indexOfLastSlash = filename.lastIndexOf("/");
        if (indexOfDotJpg == -1 || indexOfLastSlash == -1) return filename;
        String beforePart = filename.substring(0, indexOfLastSlash + 1);
        String capitolizedPart = filename.substring(indexOfLastSlash + 1,
            indexOfDotJpg).toUpperCase();
        String afterPart = filename.substring(indexOfDotJpg);

        return (beforePart + capitolizedPart + afterPart);
    }

    private String lowercaseJpg(String filename) {
        if (filename == null) return null;
        return (filename.replace(".JPG", ".jpg"));
    }

    private String uppercaseJpg(String filename) {
        if (filename == null) return null;
        return (filename.replace(".jpg", ".JPG"));
    }

    private String fixSpaceBeforeJpg(String filename) {
        if (filename == null) return null;
        return (filename.replace(" jpg", ".jpg"));
    }

    private String fixSpaceBeforeDotJpg(String filename) {
        if (filename == null) return null;
        return (filename.replace(" .jpg", ".jpg"));
    }

    private String removeTailingSpace(String filename) {
        if (filename == null) return null;
        return (filename.replace(" .jpg", ".jpg"));
    }

    private String removeSpaceDashSpaceBeforeDot(String filename) {
        if (filename == null) return null;
        return (filename.replace(" - .", "."));
    }

    private int getNumMediaAssets() {
        return 12;
    }

    private void setNumMediaAssets() {
        int numAssets = 0;

        for (String col : colIndexMap.keySet()) {
            if (col == null) continue;
            if (col.indexOf("mediaAsset") > -1) numAssets++;
        }
        numMediaAssets = numAssets;
    }

    // just apply nickname / alternate ID if applicable
    public void loadEncounterName(Row row, Encounter enc, int i) {
        String nameLabel = getString(row, "Name" + i + ".label");
        String nameValue = getString(row, "Name" + i + ".value");

        if (nameLabel == null || nameValue == null) return;
        // check for & standardize common labels
        if (Util.stringsEqualish(MarkedIndividual.NAMES_KEY_NICKNAME, nameLabel)) {
            enc.setAlternateID(nameValue);
        } else if (Util.stringsEqualish(MarkedIndividual.NAMES_KEY_ALTERNATEID, nameLabel)) {
            enc.setAlternateID(nameValue);
        }
    }

    public void loadIndividualName(Row row, MarkedIndividual ind, int i) {
        String nameLabel = getString(row, "Name" + i + ".label");
        String nameValue = getString(row, "Name" + i + ".value");

        if (nameLabel == null || nameValue == null) return;
        // check for & standardize common labels
        System.out.println("Adding name with vars " + nameLabel + ": " + nameValue);
        if (Util.stringsEqualish("default", nameLabel)) {
            System.out.println("Default key being used for val " + nameValue);
            ind.addName(nameValue);
        } else if (Util.stringsEqualish(MarkedIndividual.NAMES_KEY_NICKNAME, nameLabel)) {
            System.out.println("Nickname key being used for val " + nameValue);
            ind.addName(MarkedIndividual.NAMES_KEY_NICKNAME, nameValue);
        } else if (Util.stringsEqualish(MarkedIndividual.NAMES_KEY_ALTERNATEID, nameLabel)) {
            System.out.println("altID key being used for val " + nameValue);
            ind.addName(MarkedIndividual.NAMES_KEY_ALTERNATEID, nameValue);
        } else {
            System.out.println("Provided key being used " + nameLabel + ": " + nameValue);
            ind.addName(nameLabel, nameValue);
        }
    }

    public void loadIndividualNames(Row row, MarkedIndividual mark) {
        mark.unsetNames();
        for (int i = 0; i < numNameCols; i++) {
            loadIndividualName(row, mark, i);
        }
    }

    public MarkedIndividual loadIndividual(Row row, Encounter enc) {
        boolean newIndividual = false;
        String individualID = getIndividualID(row);
        // if (individualID==null) return null;
        MarkedIndividual mark = enc.getIndividual();

        if (mark == null) return null;
        if (mark == null) {
            System.out.println(
                "StandardImport WARNING: weird behavior. Just made an individual but it's still null.");
            return mark;
        }
        loadIndividualNames(row, mark);
        if (!newIndividual) {
            mark.addEncounterNoCommit(enc);
            System.out.println("loadIndividual notnew individual: " + mark.getDisplayName());
        }
        return mark;
    }

    // check if oldOcc is the same occurrence as the occurrence on this row
    // if so, return oldOcc. If not, return parseOccurrence(row)
    public Occurrence getCurrentOccurrence(Occurrence oldOcc, Row row) {
        String occID = getOccurrenceID(row);

        if (oldOcc != null && oldOcc.getOccurrenceID() != null &&
            oldOcc.getOccurrenceID().equals(occID)) return oldOcc;
        Occurrence occ = myShepherd.getOrCreateOccurrence(occID);

        return occ;
        // if (isOccurrenceOnRow(oldOcc, row)) return oldOcc;
        // return parseOccurrence(row);
    }

    private void initColIndexVariables(Row firstRow) {
        colIndexMap = makeColIndexMap(firstRow);
        unusedColumns = new HashSet<String>();
        // have to manually copy-in like this because keySet returns a POINTER (!!!)
        for (String colName : colIndexMap.keySet()) {
            unusedColumns.add(colName);
        }
        numMediaAssets = null;
        setNumMediaAssets();
    }

    // Returns a map from each column header to the integer col number
    public static Map<String, Integer> makeColIndexMap(Row firstRow) {
        Map<String, Integer> colMap = new HashMap<String, Integer>();

        for (int i = 0; i < firstRow.getLastCellNum(); i++) {
            String colName = getString(firstRow, i);
            colMap.put(colName, i);
        }
        return colMap;
    }

    public String getOccurrenceID(Row row) {
        // some custom data-fixing just for our aswn data blend
        String occID = getStringOrInt(row, "Occurrence.occurrenceID");

        if (!Util.stringExists(occID)) occID = getStringOrInt(row, "Encounter.occurrenceID");
        if (!Util.stringExists(occID)) return occID;
        occID = occID.replace("LiveVesselSighting", "");
        return (occurrencePrefix + occID);
    }

    public boolean isOccurrenceOnRow(Occurrence occ, Row row) {
        return (occ != null && !occ.getOccurrenceID().equals(getOccurrenceID(row)));
    }

    private static final Map<String, Integer> qualityMap = new HashMap<String, Integer>() {
        { // whoah, DOUBLE brackets! Java, you so crazy!
            put("No Data", null);
            put("Bad", 2);
            put("Fair", 3);
            put("Good", 4);
            put("Excellent", 5);
        }
    };

    public Integer getIntFromMap(Row row, String colName) {
        String key = getString(row, colName);

        if (key == null || !qualityMap.containsKey(key)) return null;
        return qualityMap.get(key);
    }

    // following 'get' functions swallow errors
    public static Integer getInteger(Row row, int i) {
        try {
            double val = row.getCell(i).getNumericCellValue();
            return new Integer((int)val);
        } catch (Exception e) {
            // case for when we have a weird String-Double, which looks like a double in the excel sheet, yet is cast as a String, AND has a leading
            // apostrophe in its stored value that prevents us from parsing it as a number.
            try {
                String str = getString(row, i);
                if (str == null) return null;
                try {
                    Integer ans = Integer.parseInt(str);
                    return ans;
                } catch (Exception badParse) {
                    str = str.substring(1);
                    Integer ans2 = Integer.parseInt(str);
                    return ans2;
                }
            } catch (Exception ex) {}
        }
        return null;
    }

    public static Long getLong(Row row, int i) {
        try {
            double val = row.getCell(i).getNumericCellValue();
            return new Long((long)val);
        } catch (Exception e) {
            try {
                String str = getString(row, i);
                if (str == null) return null;
                try {
                    Long ans = Long.parseLong(str);
                    return ans;
                } catch (Exception badParse) {
                    str = str.substring(1);
                    Long ans2 = Long.parseLong(str);
                    return ans2;
                }
            } catch (Exception ex) {}
        }
        return null;
    }

    public static Double getDouble(Row row, int i) {
        try {
            double val = row.getCell(i).getNumericCellValue();
            return new Double(val);
        } catch (Exception e) {
            // case for when we have a weird String-Double, which looks like a double in the excel sheet, yet is cast as a String, AND has a leading
            // apostrophe in its stored value that prevents us from parsing it as a number.
            try {
                String str = getString(row, i);
                if (str == null) return null;
                try {
                    Double ans = Double.parseDouble(str);
                    return ans;
                } catch (Exception badParse) {
                    str = str.substring(1);
                    Double ans2 = Double.parseDouble(str);
                    return ans2;
                }
            } catch (Exception ex) {}
        }
        return null;
    }

    public static String getString(Row row, int i) {
        try {
            String str = row.getCell(i).getStringCellValue();
            if (str.equals("")) str = null;
            return str;
        } catch (Exception e) {}
        return null;
    }

    public static Boolean getBooleanFromString(Row row, int i) {
        try {
            String boolStr = getString(row, i).trim().toLowerCase();
            if (boolStr == null || boolStr.equals("")) return null;
            else if (boolStr.equals("yes")) return new Boolean(true);
            else if (boolStr.equals("no")) return new Boolean(false);
        } catch (Exception e) {}
        return null;
    }

    public static Date getDate(Row row, int i) {
        try {
            Date date = row.getCell(i).getDateCellValue();
            return date;
        } catch (Exception e) {}
        return null;
    }

    public static DateTime getDateTime(Row row, int i) {
        Date date = getDate(row, i);

        if (date == null) return null;
        return new DateTime(date);
    }

    // Below methods are *not* static and work from column names rather than column numbers
    // IMPORTANT: ONLY WORKS IF colIndexMap HAS BEEN INITIALIZED
    public String getString(Row row, String colName) {
        if (!colIndexMap.containsKey(colName)) {
            if (verbose) missingColumns.add(colName);
            return null;
        }
        String ans = getString(row, colIndexMap.get(colName));
        if (ans != null && unusedColumns != null) unusedColumns.remove(colName);
        return ans;
    }

    public String getIntAsString(Row row, String colName) {
        Integer i = getInteger(row, colName);

        if (i == null) return null;
        return i.toString();
    }

    public String getStringOrInt(Row row, String colName) {
        if (!colIndexMap.containsKey(colName)) {
            if (verbose) missingColumns.add(colName);
            return null;
        }
        String ans = getStringOrInt(row, colIndexMap.get(colName));
        if (ans != null && unusedColumns != null) unusedColumns.remove(colName);
        return ans;
    }

    public static String getStringOrInt(Row row, int i) {
        String ans = getString(row, i);

        if (ans == null) {
            Integer inty = getInteger(row, i);
            if (inty != null) ans = inty.toString();
        }
        return ans;
    }

    public Organization getOrganization(Row row) {
        String orgID = getString(row, "Encounter.submitterOrganization");

        if (orgID == null) return null;
        Organization org = myShepherd.getOrCreateOrganizationByName(orgID, committing);
        return org;
    }

    public Integer getInteger(Row row, String colName) {
        if (!colIndexMap.containsKey(colName)) {
            if (verbose) missingColumns.add(colName);
            return null;
        }
        Integer ans = getInteger(row, colIndexMap.get(colName));
        if (ans != null && unusedColumns != null) unusedColumns.remove(colName);
        return ans;
    }

    public Long getLong(Row row, String colName) {
        if (!colIndexMap.containsKey(colName)) {
            if (verbose) missingColumns.add(colName);
            return null;
        }
        Long ans = getLong(row, colIndexMap.get(colName));
        if (ans != null && unusedColumns != null) unusedColumns.remove(colName);
        return ans;
    }

    public Double getDouble(Row row, String colName) {
        if (!colIndexMap.containsKey(colName)) {
            if (verbose) missingColumns.add(colName);
            return null;
        }
        Double ans = getDouble(row, colIndexMap.get(colName));
        if (ans != null && unusedColumns != null) unusedColumns.remove(colName);
        return ans;
    }

    public Date getDate(Row row, String colName) {
        if (!colIndexMap.containsKey(colName)) {
            if (verbose) missingColumns.add(colName);
            return null;
        }
        Date ans = getDate(row, colIndexMap.get(colName));
        if (ans != null && unusedColumns != null) unusedColumns.remove(colName);
        return ans;
    }

    public DateTime getDateTime(Row row, String colName) {
        if (!colIndexMap.containsKey(colName)) {
            if (verbose) missingColumns.add(colName);
            return null;
        }
        DateTime ans = getDateTime(row, colIndexMap.get(colName));
        if (ans != null && unusedColumns != null) unusedColumns.remove(colName);
        return ans;
    }

    // Apache POI, shame on you for making me write this. Shame! Shame! Shame! SHAME!
    // (as if I actually wrote this. thanks stackoverflow!)
    public static boolean isRowEmpty(Row row) {
        if (row == null) return true;
        for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell != null && cell.getCellType() != Cell.CELL_TYPE_BLANK)
                return false;
        }
        return true;
    }

    // This would be cool to put in Encounter or something.
    // tho I'm not immediately sure how we'd get the url context, or determine if we want to include /encounters/ or not
    public static String getEncounterURL(Encounter enc) {
        if (enc == null || enc.getCatalogNumber() == null) return null;
        return "/encounters/encounter.jsp?number=" + enc.getCatalogNumber();
    }

    // gives us a nice link if we're
    public String getEncounterDisplayString(Encounter enc) {
        if (enc == null) return null;
        if (committing) {
            return "<a href=\"" + getEncounterURL(enc) + "\" >" + enc.toString() + "</a>";
        }
        return enc.toString();
    }

    private AssetStore getAssetStore(Shepherd myShepherd) {
        return AssetStore.getDefault(myShepherd);
    }
}
