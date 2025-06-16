package org.ecocean.servlet.export;
import javax.servlet.*;
import javax.servlet.http.*;

import java.io.*;
import java.util.*;

import org.ecocean.*;
import org.ecocean.genetics.Locus;
import org.ecocean.genetics.MicrosatelliteMarkersAnalysis;
import org.ecocean.genetics.MitochondrialDNAAnalysis;
import org.ecocean.genetics.SexAnalysis;
import org.ecocean.genetics.TissueSample;
import org.ecocean.identity.IBEISIA;
import org.ecocean.importutils.TabularFeedback;
import org.ecocean.media.*;
import org.ecocean.resumableupload.UploadServlet;
import org.ecocean.security.*;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.servlet.importer.ImportTask;
import org.ecocean.social.Membership;
import org.ecocean.social.SocialUnit;
import org.ecocean.tag.SatelliteTag;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.json.JSONObject;

import java.lang.reflect.Method;
import java.text.SimpleDateFormat;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class EncounterAnnotationExportExcelFile extends HttpServlet {
    // The goal of this class is to make it so we can define a column in one place
    // and each row of the export be generated automatically from the list of
    // ExportColumns

    // this would be a static method of above subclass if java allowed that
    public static ExportColumn newEasyColumn(String classDotFieldNameHeader,
        List<ExportColumn> columns)
    throws ClassNotFoundException, NoSuchMethodException {
        return ExportColumn.newEasyColumn(classDotFieldNameHeader, columns);
    }

    private static final int BYTES_DOWNLOAD = 1024;
    private static final String REFERENCE_KEYWORD = "Reference";

    private int numMediaAssetCols = 0;
    private int numKeywords = 0;
    private List<String> labeledKeywords = new ArrayList<String>();
    private int numMeasurements = 0;
    private int numNameCols = 0;
    private List<String> measurementColTitles = new ArrayList<String>();
    private int numSubmitters = 0;
    private int numSocialUnits = 1;
    int rowLimit = 100000;

    private void setMediaAssetCounts(Vector rEncounters, Shepherd myShepherd) {
        System.out.println("EncounterAnnotationExportExcelFile: setting environment vars for " +
            Math.max(rEncounters.size(), rowLimit) + " encs.");
        int maxNumMedia = 0;
        int maxNumKeywords = 0;
        int maxNumLabeledKeywords = 0;
        int maxNumNames = 0;
        int maxNumMeasurements = 0;
        int maxSocialUnits = 1;
        int maxSubmitters = 0;
        Set<String> individualIDsChecked = new HashSet<String>();
        for (int i = 0; i < rEncounters.size() && i < rowLimit; i++) {
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
                // System.out.println("Individual "+enc.getIndividual()+" isnull = "+(enc.getIndividual()==null)+" and has # names: "+numNames);
                if (numNames > maxNumNames) maxNumNames = numNames;
                List<SocialUnit> mySocialUnits = myShepherd.getAllSocialUnitsForMarkedIndividual(
                    id);
                if (mySocialUnits.size() > maxSocialUnits) maxSocialUnits = mySocialUnits.size();
            }
        }
        numMediaAssetCols = maxNumMedia;
        numKeywords = maxNumKeywords;
        numNameCols = maxNumNames;
        numMeasurements = maxNumMeasurements;
        numSubmitters = maxSubmitters;
        numSocialUnits = maxSocialUnits;
        System.out.println(
            "EncounterAnnotationExportExcelFile: environment vars numMediaAssetCols = " +
            numMediaAssetCols + "; maxNumKeywords = " + maxNumKeywords + " and maxNumNames = " +
            numNameCols);
    }

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
        String context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);
        Vector rEncounters = new Vector();
        DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy-MM-dd");
        DateTime timeNow = new DateTime();
        String formattedDate = fmt.print(timeNow);

        // set up the files
        String filename = "AnnotnExp_" + formattedDate + ".xlsx";
        // setup data dir
        String rootWebappPath = getServletContext().getRealPath("/");
        File webappsDir = new File(rootWebappPath).getParentFile();
        File shepherdDataDir = new File(webappsDir,
            CommonConfiguration.getDataDirectoryName(context));

        if (!shepherdDataDir.exists()) { shepherdDataDir.mkdirs(); }
        File encountersDir = new File(shepherdDataDir.getAbsolutePath() + "/encounters");
        if (!encountersDir.exists()) { encountersDir.mkdirs(); }
        File excelFile = new File(encountersDir.getAbsolutePath() + "/" + filename);
        myShepherd.beginDBTransaction();

        try {
            EncounterQueryResult queryResult = EncounterQueryProcessor.processQuery(myShepherd,
                request, "year descending, month descending, day descending");
            rEncounters = queryResult.getResult();
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
            Method submitterOrganization = Encounter.class.getMethod("getSubmitterOrganization",
                null);
            Method photographerEmail = User.class.getMethod("getEmailAddress", null);
            Method FullName = User.class.getMethod("getFullName", null);
            Method submitterAffiliation = User.class.getMethod("getAffiliation", null);
            Method submitterProject = User.class.getMethod("getUserProject", null);
            Method SocialUnitName = SocialUnit.class.getMethod("getSocialUnitName", null);
            Method keywordGetName = Keyword.class.getMethod("getDisplayName");
            Method labeledKeywordGetValue = LabeledKeyword.class.getMethod("getValue");
            Method markedIndividualNickName = MarkedIndividual.class.getMethod("getNickName");
            Method markedIndividualSexName = MarkedIndividual.class.getMethod("getSex");
            Method markedIndividualLifeStageName = MarkedIndividual.class.getMethod(
                "getLastLifeStage");
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
                ExportColumn aanViewpointK = new ExportColumn(Annotation.class, Viewpoint,
                    annViewpoint, columns);
                aanViewpointK.setMaNum(maNum);
            }
            ExportColumn markedIndividualSex = new ExportColumn(MarkedIndividual.class,
                "Encounter.sex", markedIndividualSexName, columns);
            ExportColumn markedIndividuallifeStage = new ExportColumn(MarkedIndividual.class,
                "Encounter.lifeStage", markedIndividualLifeStageName, columns);

            MultiValueExportColumn.addNameColumns(numNameCols, columns);
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
            newEasyColumn("Encounter.occurrenceRemarks", columns);
            ExportColumn OccurrenceComments = new ExportColumn(Occurrence.class,
                "Occurrence.comments", occurrenceComments, columns);
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

            ExportColumn keywordCol = new ExportColumn(Keyword.class, "Reference keyword",
                keywordGetName, columns);
            keywordCol.setKwNum(0);
            keywordCol.setMaNum(0);

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
                List<String> measureVals =
                    (List<String>)CommonConfiguration.getIndexedPropertyValues("measurement",
                    context);
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
                            sortedMeasurementColTitles.add(measureVals.get(
                                currentIndex.intValue()));
                    }
                }
            }
            // end sorting
            for (ExportColumn exportCol : columns) {
                exportCol.writeHeaderLabel(sheet);
            }
            // Excel export =========================================================
            int row = 0;
            for (int i = 0; i < numMatchingEncounters && i < rowLimit; i++) {
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
                        LabeledKeyword lkw = myShepherd.getLabeledKeyword(
                            exportCol.getlabeledKwName(), lkwValue);
                        if (lkw == null) continue;
                        exportCol.writeLabel(lkw, row, sheet);
                    }
                    // end add labeled keywords
                    else if (exportCol.isFor(Keyword.class)) {
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
                    } else if (exportCol.isFor(Measurement.class)) {
                        int measurementNumber = exportCol.getMeasurementNum();
                        if (measurementNumber < 0) continue;
                        if (enc.getMeasurements() != null && enc.getMeasurements().size() > 0 &&
                            measurementNumber < enc.getMeasurements().size()) {
                            String whichMeasurementNameDoesThisNumberCorrespondToInTheSortedList =
                                sortedMeasurementColTitles.get(measurementNumber);
                            Measurement currentMeasurement = enc.getMeasurement(
                                whichMeasurementNameDoesThisNumberCorrespondToInTheSortedList.
                                    replace("Encounter.measurement.", ""));
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

            FileOutputStream fileOut = new FileOutputStream(excelFile);
            wb.write(fileOut); // Write the workbook to the FileOutputStream
            fileOut.close(); // Close the FileOutputStream
            wb.close(); // Close the workbook

            // end Excel export and business logic ===============================================
            System.out.println("Done with EncounterAnnotationExportExcelFile. We hid " +
                hiddenData.size() + " encounters.");
        } catch (Exception e) {
            e.printStackTrace();
            response.setContentType("text/html");
            PrintWriter out = response.getWriter();
            out.println(ServletUtilities.getHeader(request));
            out.println("<html><body><p><strong>Error encountered</strong></p>");
            out.println(
                "<p>Please let the webmaster know you encountered an error at: EncounterSearchExportExcelFile servlet</p></body></html>");
            out.println(ServletUtilities.getFooter(context));
            out.close();
        }
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();

        // now write out the file
        response.setContentType("application/msexcel");
        response.setHeader("Content-Disposition", "attachment;filename=" + filename);
        InputStream is = new FileInputStream(excelFile);
        int read = 0;
        byte[] bytes = new byte[BYTES_DOWNLOAD];
        OutputStream os = response.getOutputStream();
        while ((read = is.read(bytes)) != -1) {
            os.write(bytes, 0, read);
        }
        os.flush();
        os.close();
    }


   
}
