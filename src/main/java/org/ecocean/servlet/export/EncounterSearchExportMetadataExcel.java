package org.ecocean.servlet.export;
import javax.servlet.*;
import javax.servlet.http.*;

import java.io.*;
import java.util.*;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.ecocean.*;
import org.ecocean.media.*;
import org.ecocean.security.*;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.shepherd.core.Shepherd;

import java.lang.reflect.Method;

public class EncounterSearchExportMetadataExcel extends HttpServlet {
    // The goal of this class is to make it so we can define a column in one place
    // and each row of the export be generated automatically from the list of
    // ExportColumns

    private static final int BYTES_DOWNLOAD = 1024;

    private int numMediaAssetCols = 0;
    private int numKeywords = 0;
    private List<String> labeledKeywords = new ArrayList<String>();
    private int numMeasurements = 0;
    private int numNameCols = 0;
    private List<String> measurementColTitles = new ArrayList<String>();
    int rowLimit = 100000;

    private void setMediaAssetCounts(Vector rEncounters) {
        System.out.println("EncounterSearchExportMetadataExcel: setting environment vars for " +
            Math.max(rEncounters.size(), rowLimit) + " encs.");
        int maxNumMedia = 0;
        int maxNumKeywords = 0;
        int maxNumLabeledKeywords = 0;
        int maxNumNames = 0;
        int maxNumMeasurements = 0;
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
            if (enc.getMeasurements().size() > maxNumMeasurements)
                maxNumMeasurements = enc.getMeasurements().size();
            ArrayList<MediaAsset> mas = enc.getMedia();
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
                int numNames = enc.getIndividual().numNames();
                // System.out.println("Individual "+enc.getIndividual()+" isnull = "+(enc.getIndividual()==null)+" and has # names: "+numNames);
                if (numNames > maxNumNames) maxNumNames = numNames;
            }
        }
        numMediaAssetCols = maxNumMedia;
        numKeywords = maxNumKeywords;
        numNameCols = maxNumNames;
        numMeasurements = maxNumMeasurements;
        System.out.println(
            "EncounterSearchExportMetadataExcel: environment vars numMediaAssetCols = " +
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

        // set up the files
        String filename = "encounterSearchResults_export_" + request.getRemoteUser() + ".csv";

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            myShepherd.beginDBTransaction();

            try (OutputStreamWriter sw = new OutputStreamWriter(outputStream);
            CSVPrinter sheet = new CSVPrinter(sw, CSVFormat.EXCEL)) {
                EncounterQueryResult queryResult = EncounterQueryProcessor.processQuery(myShepherd,
                    request, "year descending, month descending, day descending");
                rEncounters = queryResult.getResult();
                int numMatchingEncounters = rEncounters.size();

                // Security: categorize hidden encounters with the initializer
                HiddenEncReporter hiddenData = new HiddenEncReporter(rEncounters, request,
                    myShepherd);

                // so we know how many MA columns we need
                setMediaAssetCounts(rEncounters);

                // so we know how many name columns we need

                // business logic start here
                List<ExportColumn> columns = new ArrayList<ExportColumn>();
                // adds Encounter.catalogNumber to columns
                ExportColumn.newEasyColumn("Encounter.catalogNumber", columns);
                // newEasyColumn("Encounter.individualID", columns);
                // newEasyColumn("Encounter.alternateID", columns);
                MultiValueExportColumn.addNameColumns(numNameCols, columns);
                ExportColumn.newEasyColumn("Occurrence.occurrenceID", columns);
                ExportColumn.newEasyColumn("Occurrence.sightingPlatform", columns);
                ExportColumn.newEasyColumn("Occurrence.fieldSurveyCode", columns);
                ExportColumn.newEasyColumn("Encounter.decimalLatitude", columns);
                ExportColumn.newEasyColumn("Encounter.decimalLongitude", columns);
                ExportColumn.newEasyColumn("Encounter.locationID", columns);
                ExportColumn.newEasyColumn("Encounter.verbatimLocality", columns);
                ExportColumn.newEasyColumn("Encounter.country", columns);

                Method encDepthGetter = Encounter.class.getMethod("getDepthAsDouble", null); // depth is special bc the getDepth getter can fail with a
                // NPE
                ExportColumn depthIsSpecial = new ExportColumn(Encounter.class, "Encounter.depth",
                    encDepthGetter, columns);

                ExportColumn.newEasyColumn("Encounter.dateInMilliseconds", columns);
                ExportColumn.newEasyColumn("Encounter.year", columns);
                ExportColumn.newEasyColumn("Encounter.month", columns);
                ExportColumn.newEasyColumn("Encounter.day", columns);
                ExportColumn.newEasyColumn("Encounter.hour", columns);
                ExportColumn.newEasyColumn("Encounter.minutes", columns);
                ExportColumn.newEasyColumn("Encounter.submitterOrganization", columns);
                ExportColumn.newEasyColumn("Encounter.submitterID", columns);
                ExportColumn.newEasyColumn("Encounter.recordedBy", columns);
                ExportColumn.newEasyColumn("Occurrence.groupComposition", columns);
                ExportColumn.newEasyColumn("Occurrence.groupBehavior", columns);
                ExportColumn.newEasyColumn("Occurrence.minGroupSizeEstimate", columns);
                ExportColumn.newEasyColumn("Occurrence.bestGroupSizeEstimate", columns);
                ExportColumn.newEasyColumn("Occurrence.maxGroupSizeEstimate", columns);
                ExportColumn.newEasyColumn("Occurrence.numAdults", columns);
                ExportColumn.newEasyColumn("Occurrence.numJuveniles", columns);
                ExportColumn.newEasyColumn("Occurrence.numCalves", columns);
                ExportColumn.newEasyColumn("Occurrence.initialCue", columns);
                ExportColumn.newEasyColumn("Occurrence.seaState", columns);
                ExportColumn.newEasyColumn("Occurrence.seaSurfaceTemp", columns);
                ExportColumn.newEasyColumn("Occurrence.swellHeight", columns);
                ExportColumn.newEasyColumn("Occurrence.visibilityIndex", columns);
                ExportColumn.newEasyColumn("Occurrence.effortCode", columns);
                ExportColumn.newEasyColumn("Occurrence.observer", columns);
                ExportColumn.newEasyColumn("Occurrence.transectName", columns);
                ExportColumn.newEasyColumn("Occurrence.transectBearing", columns);
                ExportColumn.newEasyColumn("Occurrence.distance", columns);
                ExportColumn.newEasyColumn("Occurrence.bearing", columns);
                ExportColumn.newEasyColumn("Occurrence.comments", columns);
                ExportColumn.newEasyColumn("Occurrence.humanActivityNearby", columns);
                ExportColumn.newEasyColumn("Encounter.patterningCode", columns);
                ExportColumn.newEasyColumn("Encounter.flukeType", columns);
                ExportColumn.newEasyColumn("Encounter.behavior", columns);
                ExportColumn.newEasyColumn("Encounter.groupRole", columns);
                ExportColumn.newEasyColumn("Encounter.sex", columns);
                ExportColumn.newEasyColumn("Encounter.lifeStage", columns);
                ExportColumn.newEasyColumn("Encounter.genus", columns);
                ExportColumn.newEasyColumn("Encounter.specificEpithet", columns);
                ExportColumn.newEasyColumn("Encounter.otherCatalogNumbers", columns);
                ExportColumn.newEasyColumn("Encounter.occurrenceRemarks", columns);

                Method maGetFilename = MediaAsset.class.getMethod("getUserFilename", null);
                Method maLocalPath = MediaAsset.class.getMethod("localPath", null);
                // This will include labels in a labeledKeyword value
                Method keywordGetName = Keyword.class.getMethod("getDisplayName");
                Method labeledKeywordGetValue = LabeledKeyword.class.getMethod("getValue");
                for (int maNum = 0; maNum < numMediaAssetCols; maNum++) { // numMediaAssetCols set by setter above
                    String mediaAssetColName = "Encounter.mediaAsset" + maNum;
                    String fullPathName = "Encounter.mediaAsset" + maNum + ".filePath";
                    ExportColumn maFilenameK = new ExportColumn(MediaAsset.class, mediaAssetColName,
                        maGetFilename, columns);
                    maFilenameK.setMaNum(maNum); // important for later!
                    ExportColumn maPathK = new ExportColumn(MediaAsset.class, fullPathName,
                        maLocalPath, columns);
                    maPathK.setMaNum(maNum);
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
                        ExportColumn keywordCol = new ExportColumn(LabeledKeyword.class,
                            keywordColName, labeledKeywordGetValue, columns);
                        keywordCol.setMaNum(maNum);
                        keywordCol.setLabeledKwName(label);
                    }
                }
                // add measurements to export

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
                        int currentIndexInMeasureVals = measureVals.indexOf(
                            currentMeasurementTitle);
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

                Method getMeasurementValue = Measurement.class.getMethod("toExcelFormat");
                if (sortedMeasurementColTitles.size() > 0) {
                    for (String currentSortedColTitle : sortedMeasurementColTitles) {
                        String measurementColName = "Encounter.measurement." +
                            currentSortedColTitle;
                        ExportColumn measurementCol = new ExportColumn(Measurement.class,
                            measurementColName, getMeasurementValue, columns);
                        String modifiedColumnName = measurementColName.replace(
                            "Encounter.measurement.", "");
                        int matchingMeasurementColNum = sortedMeasurementColTitles.indexOf(
                            modifiedColumnName);
                        measurementCol.setMeasurementNum(matchingMeasurementColNum);
                    }
                }
                // End measurements export
                for (ExportColumn exportCol : columns) {
                    exportCol.writeHeaderLabel(sheet);
                }
                sheet.printRecord();
                // Excel export =========================================================
                int row = 0;
                int numColumns = columns.size();
                for (int i = 0; i < numMatchingEncounters && i < rowLimit; i++) {
                    Encounter enc = (Encounter)rEncounters.get(i);
                    // Security: skip this row if user doesn't have permission to view this encounter
                    if (hiddenData.contains(enc)) continue;
                    row++;

                    // Initialize row array - each column writes to its own index
                    String[] rowData = new String[numColumns];
                    Arrays.fill(rowData, "");

                    // get attached objects
                    Occurrence occ = myShepherd.getOccurrence(enc);
                    MarkedIndividual ind = myShepherd.getMarkedIndividual(enc);
                    MultiValue names = (ind != null) ? ind.getNames() : null;
                    List<String> sortedNameKeys = (names != null) ? names.getSortedKeys() : null;
                    List<MediaAsset> mas = enc.getMedia();
                    // use exportColumns, passing in the appropriate object for each column
                    // (can't use switch statement bc Class is not a java primitive type)
                    for (ExportColumn exportCol : columns) {
                        if (exportCol.isFor(Encounter.class))
                            exportCol.writeLabel(enc, row, rowData);
                        else if (exportCol.isFor(Occurrence.class))
                            exportCol.writeLabel(occ, row, rowData);
                        else if (exportCol.isFor(MarkedIndividual.class))
                            exportCol.writeLabel(ind, row, rowData);
                        else if (exportCol.isFor(MultiValue.class)) {
                            MultiValueExportColumn multiValCol = (MultiValueExportColumn)exportCol;
                            multiValCol.writeLabel(sortedNameKeys, names, row, rowData);
                        } else if (exportCol.isFor(MediaAsset.class)) {
                            int num = exportCol.getMaNum();
                            MediaAsset ma = null;
                            if (num < mas.size()) {
                                ma = mas.get(num);
                            }
                            exportCol.writeLabel(ma, row, rowData);
                        }
                        // add labeled keywords
                        else if (exportCol.isFor(LabeledKeyword.class)) {
                            int maNum = exportCol.getMaNum();
                            LabeledKeyword lkw = null;
                            if (maNum < mas.size()) {
                                MediaAsset ma = mas.get(maNum);
                                if (ma != null) {
                                    String lkwValue = ma.getLabeledKeywordValue(
                                        exportCol.getlabeledKwName());
                                    lkw = myShepherd.getLabeledKeyword(exportCol.getlabeledKwName(),
                                        lkwValue);
                                }
                            }
                            exportCol.writeLabel(lkw, row, rowData);
                        }
                        // end add labeled keywords
                        else if (exportCol.isFor(Keyword.class)) {
                            int maNum = exportCol.getMaNum();
                            Keyword kw = null;
                            if (maNum < mas.size()) {
                                MediaAsset ma = mas.get(maNum);
                                int kwNum = exportCol.getKwNum();
                                if (ma != null && kwNum < ma.numKeywordsStrict() && kwNum != -1) {
                                    kw = ma.getKeywordStrict(kwNum);
                                }
                            }
                            exportCol.writeLabel(kw, row, rowData);
                        } else if (exportCol.isFor(Measurement.class)) {
                            int measurementNumber = exportCol.getMeasurementNum();
                            Measurement currentMeasurement = null;
                            if (measurementNumber >= 0 && enc.getMeasurements() != null &&
                                enc.getMeasurements().size() > 0 &&
                                measurementNumber < enc.getMeasurements().size()) {
                                String whichMeasurementNameDoesThisNumberCorrespondToInTheSortedList
                                    = sortedMeasurementColTitles.get(measurementNumber);
                                currentMeasurement = enc.getMeasurement(
                                    whichMeasurementNameDoesThisNumberCorrespondToInTheSortedList.
                                        replace("Encounter.measurement.", ""));
                            }
                            exportCol.writeLabel(currentMeasurement, row, rowData);
                        } else
                            System.out.println(
                                "EncounterSearchExportMetadataExcel: no object found for class " +
                                exportCol.getDeclaringClass());
                    }
                    // Write the complete row at once
                    sheet.printRecord((Object[])rowData);
                } // end for loop iterating encounters

                // end Excel export and business logic ===============================================
                System.out.println("Done with EncounterSearchExportMetadataExcel. We hid " +
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
            response.setContentType("text/csv");
            response.setHeader("Content-Disposition", "attachment;filename=" + filename);
            byte[] bytes = outputStream.toByteArray();
            OutputStream os = response.getOutputStream();
            os.write(bytes, 0, bytes.length);
            os.flush();
            os.close();
        }
    }
}
