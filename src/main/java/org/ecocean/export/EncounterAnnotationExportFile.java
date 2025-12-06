package org.ecocean.export;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.ecocean.*;
import org.ecocean.media.MediaAsset;
import org.ecocean.security.HiddenEncReporter;
import org.ecocean.servlet.export.ExportColumn;
import org.ecocean.servlet.export.MultiValueExportColumn;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.social.SocialUnit;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import javax.servlet.http.HttpServletRequest;

public class EncounterAnnotationExportFile {
    // TODO: Refactor this into a SearchApi-based searching component and an export component

    private static final int BYTES_DOWNLOAD = 1024;
    private static final String REFERENCE_KEYWORD = "Reference";
    private static final int ROW_LIMIT = 100000;

    private final String name;
    private final HttpServletRequest request;
    private final Shepherd myShepherd;

    private int numMediaAssetCols = 0;
    private int numNameCols = 0;
    private int numSubmitters = 0;
    private int numSocialUnits = 1;
    private int numKeywords;

    private List<String> measurementColTitles = new ArrayList<String>();

    public EncounterAnnotationExportFile(HttpServletRequest request, Shepherd myShepherd) {
        this.request = request;
        this.myShepherd = myShepherd;

        DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy-MM-dd");
        DateTime timeNow = new DateTime();
        String formattedDate = fmt.print(timeNow);

        // set up the files
        this.name = "AnnotnExp_" + formattedDate + ".xlsx";
    }

    // this would be a static method of above subclass if java allowed that
    public static ExportColumn newEasyColumn(String classDotFieldNameHeader,
        List<ExportColumn> columns)
    throws ClassNotFoundException, NoSuchMethodException {
        return ExportColumn.newEasyColumn(classDotFieldNameHeader, columns);
    }

    public String getName() { return name; }

    public void writeToStream(OutputStream fileOut)
    throws NoSuchMethodException, ClassNotFoundException, InvocationTargetException,
        IllegalAccessException, IOException {
        String context = ServletUtilities.getContext(request);
        EncounterQueryResult queryResult = EncounterQueryProcessor.processQuery(myShepherd, request,
            "year descending, month descending, day descending");
        Vector rEncounters = queryResult.getResult();
        int numMatchingEncounters = rEncounters.size();

        // Security: categorize hidden encounters with the initializer
        HiddenEncReporter hiddenData = new HiddenEncReporter(rEncounters, request, myShepherd);

        // so we know how many MA columns we need
        setMediaAssetCounts(rEncounters, myShepherd);

        // so we know how many name columns we need

        // business logic start here
        Workbook wb = new XSSFWorkbook(); // Create a new workbook
        Sheet sheet = wb.createSheet("Search Results");
        Sheet hiddenSheet = wb.createSheet("Hidden Data Report");
        Method maGetFilename = MediaAsset.class.getMethod("getFilename", null);
        Method maLocalPath = MediaAsset.class.getMethod("localPath", null);
        Method maImgUrl = MediaAsset.class.getMethod("webURL", null);
        Method annBbox = Annotation.class.getMethod("getBboxAsString", null);
        Method annViewpoint = Annotation.class.getMethod("getViewpoint", null);
        Method annMatchAgainst = Annotation.class.getMethod("getMatchAgainst", null);
        Method submitterID = Encounter.class.getMethod("getSubmitterID", null);
        Method submitterOrganization = Encounter.class.getMethod("getSubmitterOrganization", null);
        Method photographerEmail = User.class.getMethod("getEmailAddress", null);
        Method FullName = User.class.getMethod("getFullName", null);
        Method submitterAffiliation = User.class.getMethod("getAffiliation", null);
        Method submitterProject = User.class.getMethod("getUserProject", null);
        Method SocialUnitName = SocialUnit.class.getMethod("getSocialUnitName", null);
        Method keywordGetName = Keyword.class.getMethod("getDisplayName");
        Method labeledKeywordGetValue = LabeledKeyword.class.getMethod("getValue");
        Method markedIndividualNickName = MarkedIndividual.class.getMethod("getNickName");
        Method markedIndividualSexName = MarkedIndividual.class.getMethod("getSex");
        Method markedIndividualLifeStageName = MarkedIndividual.class.getMethod("getLastLifeStage");
        Method occurrenceComments = Occurrence.class.getMethod("getCommentsExport");
        List<ExportColumn> columns = new ArrayList<ExportColumn>();

        newEasyColumn("Occurrence.occurrenceID", columns);

        // added new Column for Encounter weburl
        // method is null as we current approach does not support parameters
        // used Encounter.getWebUrl(request, enc.getCatalogNumber()
        // to fill this column value manually
        String sourceEncounterUrlColName = "Encounter.sourceUrl";
        ExportColumn sourceEncounterUrlCol = new ExportColumn(Encounter.class,
            sourceEncounterUrlColName, null, columns);
        for (int maNum = 0; maNum < numMediaAssetCols; maNum++) { // numMediaAssetCols set by setter above
            String mediaAssetColName = "Encounter.mediaAsset" + maNum;
            ExportColumn maFilenameK = new ExportColumn(MediaAsset.class, mediaAssetColName,
                maGetFilename, columns);
            maFilenameK.setMaNum(maNum);
        }
        for (int maNum = 0; maNum < numMediaAssetCols; maNum++) {
            String Viewpoint = "Annotation" + maNum + ".ViewPoint";
            ExportColumn aanViewpointK = new ExportColumn(Annotation.class, Viewpoint, annViewpoint,
                columns);
            aanViewpointK.setMaNum(maNum);
        }
        // individual names and summaries
        MultiValueExportColumn.addNameColumns(numNameCols, columns);
        ExportColumn markedIndividualSex = new ExportColumn(MarkedIndividual.class,
            "IndividualSummary.sex", markedIndividualSexName, columns);
        ExportColumn markedIndividuallifeStage = new ExportColumn(MarkedIndividual.class,
            "IndividualSummary.lifeStage", markedIndividualLifeStageName, columns);

        // encounter information
        newEasyColumn("Encounter.genus", columns);
        newEasyColumn("Encounter.specificEpithet", columns);
        newEasyColumn("Encounter.verbatimLocality", columns);
        newEasyColumn("Encounter.decimalLatitude", columns);
        newEasyColumn("Encounter.decimalLongitude", columns);
        newEasyColumn("Encounter.country", columns);
        newEasyColumn("Encounter.locationID", columns);
        newEasyColumn("Encounter.year", columns);
        newEasyColumn("Encounter.month", columns);
        newEasyColumn("Encounter.day", columns);
        newEasyColumn("Encounter.hour", columns);
        newEasyColumn("Encounter.minutes", columns);
        newEasyColumn("Encounter.sex", columns);
        newEasyColumn("Encounter.lifeStage", columns);
        newEasyColumn("Encounter.occurrenceRemarks", columns);
        ExportColumn OccurrenceComments = new ExportColumn(Occurrence.class, "Occurrence.comments",
            occurrenceComments, columns);
        for (int subNum = 0; subNum < numSocialUnits; subNum++) {
            String socialUnitsName = "SocialUnit.socialUnitName" + subNum;
            ExportColumn maSocialK = new ExportColumn(SocialUnit.class, socialUnitsName,
                SocialUnitName, columns);
            maSocialK.setMaNum(subNum);
        }
        for (int subNum = 0; subNum < numSubmitters; subNum++) {
            String submitterIDName = "Encounter.submitter" + subNum + ".submitterID";
            ExportColumn submitterIDK = new ExportColumn(Encounter.class, submitterIDName,
                submitterID, columns);
            submitterIDK.setMaNum(subNum);

            String submitterFullName = "Encounter.submitter" + subNum + ".fullName";
            ExportColumn submitterFullNameK = new ExportColumn(User.class, submitterFullName,
                FullName, columns);
            submitterFullNameK.setMaNum(subNum);

            String submitterOrganizationName = "Encounter.submitter" + subNum +
                ".submitterOrganization";
            ExportColumn submitterOrganizationNameK = new ExportColumn(Encounter.class,
                submitterOrganizationName, submitterOrganization, columns);
            submitterOrganizationNameK.setMaNum(subNum);

            String submitterAffiliationName = "Encounter.submitter" + subNum + ".Affiliation";
            ExportColumn maPathK = new ExportColumn(User.class, submitterAffiliationName,
                submitterAffiliation, columns);
            maPathK.setMaNum(subNum);
        }
        ExportColumn photographerEmailCol = new ExportColumn(User.class,
            "Encounter.photographer0.Email", photographerEmail, columns);
        photographerEmailCol.setMaNum(0);

        ExportColumn photographerNameCol = new ExportColumn(User.class,
            "Encounter.photographer0.fullName", FullName, columns);
        photographerNameCol.setMaNum(0);

        newEasyColumn("Encounter.state", columns);

        ExportColumn refKeywordCol = new ExportColumn(Keyword.class, "Reference keyword",
            keywordGetName, columns);
        refKeywordCol.setKwNum(0);
        refKeywordCol.setMaNum(0);

        // ExportColumn maPathK = new ExportColumn(User.class, submitterAffiliationName, submitterAffiliation, columns);

        // newEasyColumn("Encounter.catalogNumber", columns);
        newEasyColumn("Encounter.alternateID", columns);
        for (int maNum = 0; maNum < numMediaAssetCols; maNum++) { // numMediaAssetCols set by setter above
            String imageUrl = "Encounter.mediaAsset" + maNum + ".imageUrl";
            String bBox = "Annotation" + maNum + ".bbox";
            String MatchAgainst = "Annotation" + maNum + ".MatchAgainst";
            ExportColumn maimageUrlK = new ExportColumn(MediaAsset.class, imageUrl, maImgUrl,
                columns);
            maimageUrlK.setMaNum(maNum);
            for (int kwNum = 0; kwNum < numKeywords; kwNum++) {
                String keywordColName = "Encounter.mediaAsset" + maNum + ".keyword" + kwNum;
                ExportColumn keywordCol = new ExportColumn(Keyword.class, keywordColName,
                    keywordGetName, columns);
                keywordCol.setMaNum(maNum);
                keywordCol.setKwNum(kwNum);
            }
            List<String> labels = myShepherd.getAllKeywordLabels();
            for (String label : labels) {
                String keywordColName = "Encounter.mediaAsset" + maNum + "." + label;
                ExportColumn keywordCol = new ExportColumn(LabeledKeyword.class, keywordColName,
                    labeledKeywordGetValue, columns);
                keywordCol.setMaNum(maNum);
                keywordCol.setLabeledKwName(label);
            }
            ExportColumn aanBboxK = new ExportColumn(Annotation.class, bBox, annBbox, columns);
            aanBboxK.setMaNum(maNum);

            ExportColumn annMatchAgainstK = new ExportColumn(Annotation.class, MatchAgainst,
                annMatchAgainst, columns);
            annMatchAgainstK.setMaNum(maNum);
        }
        // sort measurementColTitles (a subset of all possible measureVals that's currently not in the same order as they appear in measureVals)
        // by order in which they will appear in enc.getMeasurements, which is dictated by commonConfig array (i.e., measureVals). If this doesn't
        // happen, some encounters with a measurements list beginning with a rare measurement will end up misplaced in the colunns
        List<String> sortedMeasurementColTitles = new ArrayList<String>();
        if (measurementColTitles.size() > 0) {
            List<String> measureVals = (List<String>)CommonConfiguration.getIndexedPropertyValues(
                "measurement", context);
            List<Integer> measurementColTitlesRanked = new ArrayList<Integer>();
            for (String currentMeasurementTitle : measurementColTitles) {
                int currentIndexInMeasureVals = measureVals.indexOf(currentMeasurementTitle);
                measurementColTitlesRanked.add(currentIndexInMeasureVals); // an array of indeces, a copy of which will be sorted
            }
            List<Integer> measurementColTitlesRankedSorted = measurementColTitlesRanked;
            if (measurementColTitlesRankedSorted != null &&
                measurementColTitlesRankedSorted.size() > 0) {
                Collections.sort(measurementColTitlesRankedSorted);
                for (Integer currentIndex : measurementColTitlesRankedSorted) {
                    if (currentIndex != null && currentIndex.intValue() != -1)
                        sortedMeasurementColTitles.add(measureVals.get(currentIndex.intValue()));
                }
            }
        }
        // end sorting
        for (ExportColumn exportCol : columns) {
            exportCol.writeHeaderLabel(sheet);
        }
        // Excel export =========================================================
        int row = 0;
        for (int i = 0; i < numMatchingEncounters && i < ROW_LIMIT; i++) {
            // get the Encounter and check if user
            // has permission otherwise hide the encounter

            Encounter enc = (Encounter)rEncounters.get(i);
            if (hiddenData.contains(enc)) continue;
            row++;

            // get attached objects
            Occurrence occ = myShepherd.getOccurrence(enc);
            MarkedIndividual ind = myShepherd.getMarkedIndividual(enc);
            MultiValue names = (ind != null) ? ind.getNames() : null;
            List<String> sortedNameKeys = (names != null) ? names.getSortedKeys() : null;
            ArrayList<MediaAsset> masDuplicates = enc.getMedia();
            ArrayList<MediaAsset> mas = new ArrayList<>(new HashSet<MediaAsset>(masDuplicates)); // remove Media Asset duplicates
            List<User> submitters = enc.getSubmitters();
            List<SocialUnit> socialUnits = (ind !=
                null) ? myShepherd.getAllSocialUnitsForMarkedIndividual(ind) : null;
            List<User> photographers = enc.getPhotographers();
            // use exportColumns, passing in the appropriate object for each column
            // (can't use switch statement bc Class is not a java primitive type)
            for (ExportColumn exportCol : columns) {
                if (exportCol.isFor(Encounter.class)) {
                    // added new column which holds the encounter url
                    if (exportCol.header.contains("Encounter.sourceUrl")) {
                        String EncUrl = Encounter.getWebUrl(enc.getCatalogNumber(), request);
                        exportCol.writeLabel(EncUrl, row, sheet);
                    } else {
                        exportCol.writeLabel(enc, row, sheet);
                    }
                } else if (exportCol.isFor(Occurrence.class))
                    exportCol.writeLabel(occ, row, sheet);
                else if (exportCol.isFor(MarkedIndividual.class))
                    exportCol.writeLabel(ind, row, sheet);
                else if (exportCol.isFor(MultiValue.class)) {
                    MultiValueExportColumn multiValCol = (MultiValueExportColumn)exportCol;
                    multiValCol.writeLabel(sortedNameKeys, names, row, sheet);
                } else if (exportCol.isFor(MediaAsset.class)) {
                    int num = exportCol.getMaNum();
                    if (num >= mas.size()) continue;
                    MediaAsset ma = mas.get(num);
                    if (ma == null) continue; // on to next column
                    exportCol.writeLabel(ma, row, sheet);
                } else if (exportCol.isFor(Annotation.class)) {
                    int num = exportCol.getMaNum();
                    if (num >= mas.size()) continue;
                    MediaAsset ma = mas.get(num);
                    if (ma == null) continue;
                    List<Annotation> anns = enc.getAnnotations(ma);
                    for (int annNum = 0; annNum < anns.size(); annNum++) {
                        Annotation ann = anns.get(annNum);
                        if (ann.getMatchAgainst()) {
                            exportCol.writeLabel(ann, row, sheet);
                        }
                    }
                } else if (exportCol.isFor(User.class)) {
                    int num = exportCol.getMaNum();
                    User user = null;
                    if (exportCol.header.contains("photographer")) {
                        if (num >= photographers.size()) continue;
                        user = photographers.get(num);
                    } else {
                        if (num >= submitters.size()) continue;
                        user = submitters.get(num);
                    }
                    exportCol.writeLabel(user, row, sheet);
                } else if (exportCol.isFor(SocialUnit.class)) {
                    int num = exportCol.getMaNum();
                    if (socialUnits == null || num >= socialUnits.size()) continue;
                    SocialUnit social = socialUnits.get(num);
                    exportCol.writeLabel(social, row, sheet);
                }
                // add labeled keywords
                else if (exportCol.isFor(LabeledKeyword.class)) {
                    int maNum = exportCol.getMaNum();
                    if (maNum >= mas.size()) continue;
                    MediaAsset ma = mas.get(maNum);
                    if (ma == null) continue; // on to next column
                    // String kwNum = exportCol.getLabeledKwName();
                    // if (kwNum >= ma.numKeywords()) continue;
                    String lkwValue = ma.getLabeledKeywordValue(exportCol.getlabeledKwName());
                    if (lkwValue == null) continue;
                    LabeledKeyword lkw = myShepherd.getLabeledKeyword(exportCol.getlabeledKwName(),
                        lkwValue);
                    if (lkw == null) continue;
                    exportCol.writeLabel(lkw, row, sheet);
                }
                // end add labeled keywords
                else if (exportCol.isFor(Keyword.class)) {
                    if (Objects.equals(exportCol.header, "Reference keyword")) {
                        boolean keywordFound = false;
                        for (MediaAsset ma : mas) {
                            for (Keyword kw : ma.getKeywordsStrict()) {
                                if (kw != null && kw.getReadableName().equals(REFERENCE_KEYWORD)) {
                                    exportCol.writeLabel(kw, row, sheet);
                                    keywordFound = true;
                                    break;
                                }
                            }
                            if (keywordFound) break;
                        }
                    } else {
                        int maNum = exportCol.getMaNum();
                        if (maNum >= mas.size()) continue;
                        MediaAsset ma = mas.get(maNum);
                        if (ma == null) continue; // on to next column
                        int kwNum = exportCol.getKwNum();
                        if (kwNum >= ma.numKeywordsStrict() || kwNum == -1) continue;
                        Keyword kw = ma.getKeywordStrict(kwNum);
                        if (kw == null) continue;
                        exportCol.writeLabel(kw, row, sheet);
                    }
                } else if (exportCol.isFor(Measurement.class)) {
                    int measurementNumber = exportCol.getMeasurementNum();
                    if (measurementNumber < 0) continue;
                    if (enc.getMeasurements() != null && enc.getMeasurements().size() > 0 &&
                        measurementNumber < enc.getMeasurements().size()) {
                        String whichMeasurementNameDoesThisNumberCorrespondToInTheSortedList =
                            sortedMeasurementColTitles.get(measurementNumber);
                        Measurement currentMeasurement = enc.getMeasurement(
                            whichMeasurementNameDoesThisNumberCorrespondToInTheSortedList.replace(
                            "Encounter.measurement.", ""));
                        if (currentMeasurement == null) continue;
                        exportCol.writeLabel(currentMeasurement, row, sheet);
                    }
                } else
                    System.out.println(
                        "EncounterAnnotationExportExcelFile: no object found for class " +
                        exportCol.getDeclaringClass());
            }
        } // end for loop iterating encounters

        // Security: log the hidden data report in excel so the user can request collaborations with owners of hidden data
        hiddenData.writeHiddenDataReport(hiddenSheet);

        wb.write(fileOut); // Write the workbook to the OutputStream
        // Note: Don't close fileOut - let the caller manage stream lifecycle
        wb.close(); // Close the workbook

        // end Excel export and business logic ===============================================
        System.out.println("Done with EncounterAnnotationExportExcelFile. We hid " +
            hiddenData.size() + " encounters.");
    }

    void setMediaAssetCounts(Vector rEncounters, Shepherd myShepherd) {
        System.out.println("EncounterAnnotationExportExcelFile: setting environment vars for " +
            Math.max(rEncounters.size(), ROW_LIMIT) + " encs.");
        int maxNumMedia = 0;
        int maxNumKeywords = 0;
        int maxNumLabeledKeywords = 0;
        int maxNumNames = 0;
        int maxNumMeasurements = 0;
        int maxSocialUnits = 1;
        int maxSubmitters = 0;
        Set<String> individualIDsChecked = new HashSet<String>();
        for (int i = 0; i < rEncounters.size() && i < ROW_LIMIT; i++) {
            Encounter enc = (Encounter)rEncounters.get(i);
            if (enc.getMeasurements().size() > 0) {
                for (Measurement currentMeasurement : enc.getMeasurements()) { // populate a list of measurementColName with measurements in current
                    // encounter set only
                    String currentMeasurementType = currentMeasurement.getType();
                    if (currentMeasurementType != null &&
                        !measurementColTitles.contains(currentMeasurementType)) {
                        measurementColTitles.add(currentMeasurementType);
                    }
                }
            }
            if (enc.getSubmitters().size() > maxSubmitters)
                maxSubmitters = enc.getSubmitters().size();
            if (enc.getMeasurements().size() > maxNumMeasurements)
                maxNumMeasurements = enc.getMeasurements().size();
            ArrayList<MediaAsset> masDuplicates = enc.getMedia();
            ArrayList<MediaAsset> mas = new ArrayList<>(new HashSet<MediaAsset>(masDuplicates)); // remove Media Asset duplicates
            int numMedia = mas.size();
            if (numMedia > maxNumMedia) maxNumMedia = numMedia;
            for (MediaAsset ma : mas) {
                int numKw = ma.numKeywordsStrict();
                int numLabeledKw = 0;
                if (ma.getLabeledKeywords() != null) {
                    List<LabeledKeyword> lkws = ma.getLabeledKeywords();
                    numLabeledKw = lkws.size();
                }
                if (numKw > maxNumKeywords) maxNumKeywords = numKw;
                if (numLabeledKw > maxNumLabeledKeywords) maxNumLabeledKeywords = numLabeledKw;
            }
            MarkedIndividual id = enc.getIndividual();
            if (id != null && !individualIDsChecked.contains(id.getIndividualID())) {
                individualIDsChecked.add(id.getIndividualID());
                int numNames = id.getNameKeys().size();
                if (numNames > maxNumNames) maxNumNames = numNames;
                List<SocialUnit> mySocialUnits = myShepherd.getAllSocialUnitsForMarkedIndividual(
                    id);
                if (mySocialUnits.size() > maxSocialUnits) maxSocialUnits = mySocialUnits.size();
            }
        }
        numMediaAssetCols = maxNumMedia;
        numNameCols = maxNumNames;
        numSubmitters = maxSubmitters;
        numSocialUnits = maxSocialUnits;
        numKeywords = maxNumKeywords;
        System.out.println(
            "EncounterAnnotationExportExcelFile: environment vars numMediaAssetCols = " +
            numMediaAssetCols + "; maxNumKeywords = " + maxNumKeywords + " and maxNumNames = " +
            numNameCols);
    }
}
