package org.ecocean.servlet.importer;

import java.io.*;
import java.io.File;
import java.util.*;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.ecocean.*;
import org.ecocean.media.*;
import org.ecocean.servlet.*;
import org.joda.time.DateTime;
import org.json.JSONObject;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

public class FixStandardImport extends HttpServlet {
    // variables shared by any single import instance
    Shepherd myShepherd;
    Map<String, Integer> colIndexMap = new HashMap<String, Integer>();
    Set<String> unusedColumns;
    Set<String> missingColumns; // columns we look for but don't find
    Set<String> missingEncs;
    List<String> missingPhotos = new ArrayList<String>();
    List<String> foundPhotos = new ArrayList<String>();
    int numFolderRows = 0;
    int numFixedYears = 0;
    int numFixedLats = 0;
    int numFixedEncs = 0;
    boolean committing = true;
    PrintWriter out;
    // verbose variable can be switched on/off throughout the import for debugging
    boolean verbose = false;
    String photoDirectory;

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
        numFixedYears = 0;
        numFixedEncs = 0;

        missingEncs = new HashSet<String>();
        String context = "context0";
        context = ServletUtilities.getContext(request);
        myShepherd = new Shepherd(context);
        out = response.getWriter();
        astore = getAssetStore(myShepherd);

        photoDirectory = "/data/guererro-raw/WGRP-Flukes-2017/";
        // photoDirectory = Util.ensureTrailingSlash(photoDirectory);

        String filename = "/data/guererro-raw/WGRP-Flukes-2017/wgrpExported.xlsx";
        // String filename = "/data/oman_import/ASWN_firstExport.xlsx";
        if (request.getParameter("filename") != null) filename = request.getParameter("filename");
        File dataFile = new File(filename);
        boolean dataFound = dataFile.exists();

        missingPhotos = new ArrayList<String>();
        foundPhotos = new ArrayList<String>();
        numFolderRows = 0;

        committing = (request.getParameter("commit") != null &&
            !request.getParameter("commit").toLowerCase().equals("false"));                                                // false by default

        out.println("<h2>File Overview: </h2>");
        out.println("<ul>");
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
        for (String heading : colIndexMap.keySet())
            out.println("<li>" + colIndexMap.get(heading) + ": " + heading + "</li>");
        out.println("</ul>");

        int printPeriod = 10;
        if (committing) myShepherd.beginDBTransaction();
        out.println("<h2>Beginning row loop:</h2>");
        out.println("<ul>");
        // one encounter per-row. We keep these running.
        Occurrence occ = null;
        int maxRows = 50000;
        int offset = 0;
        for (int i = 1 + offset; i < rows && i < (maxRows + offset); i++) {
            MarkedIndividual mark = null;
            verbose = ((i % printPeriod) == 0);
            try {
                if (committing) myShepherd.beginDBTransaction();
                Row row = sheet.getRow(i);
                if (isRowEmpty(row)) continue;
                // here's the central logic
                // ArrayList<Annotation> annotations = loadAnnotations(row);
                Encounter enc = fixEncounter(row);
                if (enc != null) numFixedEncs++;
                occ = fixOccurrence(row, occ, enc);
                // mark = loadIndividual(row, enc);
                if (committing) {
                    myShepherd.commitDBTransaction();
                }
                if (verbose) {
                    out.println("<li>Fixed row (" + i + ") with Enc " +
                        getEncounterDisplayString(enc) + "<ul>" + "<li> year " + enc.getYear() +
                        "</li>" + "<li> longitude " + enc.getDecimalLongitude() + "</li>" +
                        "</ul></li>");
                }
            } catch (Exception e) {
                out.println("Encountered an error while importing the file.");
                e.printStackTrace(out);
                myShepherd.rollbackDBTransaction();
            }
        }
        out.println("</ul>");

        out.println("<h2><em>Num Fixed Years: <strong>" + numFixedYears + "</strong></em></h2>");

        out.println("<h2>Import completed successfully</h2>");
        // fs.close();
    }

    public Occurrence fixOccurrence(Row row, Occurrence oldOcc, Encounter enc) {
        return null;
    }

    public Encounter fixEncounter(Row row) {
        // since we need access to the encounter ID
        String encID = getString(row, "Encounter.catalogNumber");

        if (encID == null) {
            missingEncs.add(encID);
            return null;
        }
        Encounter enc = myShepherd.getEncounter(encID);
        if (enc == null) {
            missingEncs.add(encID);
            return null;
        }
        // Time
        Integer year = getInteger(row, "Encounter.year");
        if (year == null) {
            year = getIntegerFromString(row, "Encounter.year");
            if (year != null) numFixedYears++;
        }
        if (year != null) enc.setYear(year);
        Integer month = getInteger(row, "Encounter.month");
        if (month == null) month = getIntegerFromString(row, "Encounter.month");
        if (month != null) enc.setMonth(month);
        Integer day = getInteger(row, "Encounter.day");
        if (day == null) month = getIntegerFromString(row, "Encounter.day");
        if (day != null) enc.setDay(day);
        Integer hour = getInteger(row, "Encounter.hour");
        if (hour == null) month = getIntegerFromString(row, "Encounter.hour");
        if (hour != null) enc.setHour(hour);
        Long millis = getLong(row, "Encounter.dateInMilliseconds");
        if (millis == null) millis = getLongFromString(row, "Encounter.dateInMilliseconds");
        // Location
        Double latitude = getDouble(row, "Encounter.latitude");
        if (latitude == null) latitude = getDoubleFromString(row, "Encounter.latitude");
        if (latitude == null) latitude = getDouble(row, "Encounter.decimalLatitude");
        if (latitude == null) latitude = getDoubleFromString(row, "Encounter.decimalLatitude");
        if (latitude != null) enc.setDecimalLatitude(latitude);
        Double longitude = getDouble(row, "Encounter.longitude");
        if (longitude == null) longitude = getDoubleFromString(row, "Encounter.longitude");
        if (longitude == null) longitude = getDouble(row, "Encounter.decimalLongitude");
        if (longitude == null) longitude = getDoubleFromString(row, "Encounter.decimalLongitude");
        if (longitude != null) enc.setDecimalLongitude(longitude);
        Integer patterningCode = getInteger(row, "Encounter.patterningCode");
        if (patterningCode == null)
            patterningCode = getIntegerFromString(row, "Encounter.patterningCode");
        if (patterningCode != null) enc.setFlukeType(patterningCode);
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

    public ArrayList<Annotation> loadAnnotations(Row row) {
        if (isFolderRow(row)) return loadAnnotationsFolderRow(row);
        ArrayList<Annotation> annots = new ArrayList<Annotation>();
        for (int i = 0; i < getNumMediaAssets(); i++) {
            MediaAsset ma = getMediaAsset(row, i);
            if (ma == null) continue;
            String species = getSpeciesString(row);
            Annotation ann = new Annotation(species, ma);
            ann.setIsExemplar(true);
            annots.add(ann);
            if (ma != null && ma.localPath() != null) foundPhotos.add(ma.localPath().toString());
        }
        if (annots.size() > 0) {
            String localPath = getString(row, "Encounter.mediaAsset0");
            String fullPath = photoDirectory + localPath;
            foundPhotos.add(fullPath);
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
        String indID = getString(row, "MarkedIndividual.individualID");

        if (indID == null) indID = getString(row, "Encounter.individualID");
        return indID;
    }

    public MediaAsset getMediaAsset(Row row, int i) {
        String localPath = getString(row, "Encounter.mediaAsset" + i);

        if (localPath == null) return null;
        localPath = Util.windowsFileStringToLinux(localPath);
        String fullPath = photoDirectory + localPath;
        boolean fileExists = Util.fileExists(fullPath);
        if (!fileExists) {
            fullPath = lowercaseJpg(fullPath);
            fileExists = Util.fileExists(fullPath);
        }
        if (!fileExists) {
            fullPath = removeTailingSpace(fullPath);
            fileExists = Util.fileExists(fullPath);
        }
        if (!fileExists) {
            missingPhotos.add(fullPath);
            return null;
        }
        File f = new File(fullPath);

        // create MediaAsset and return it
        JSONObject assetParams = astore.createParameters(f);
        assetParams.put("_localDirect", f.toString());
        MediaAsset ma = null;
        try {
            ma = astore.copyIn(f, assetParams);
        } catch (java.io.IOException ioEx) {
            System.out.println("IOException creating MediaAsset for file " + fullPath);
            missingPhotos.add(fullPath);
        }
        // keywording
        Keyword keyword = null;
        String keywordI = getString(row, "Encounter.keyword" + i);
        if (keywordI != null) keyword = myShepherd.getOrCreateKeyword(keywordI);
        String keywordOIKey = "Encounter.keyword0" + i;
        System.out.println("GETMEDIAASSET got keywordOI key " + keywordOIKey);
        String keywordOI = getString(row, keywordOIKey);
        if (keywordOI != null) keyword = myShepherd.getOrCreateKeyword(keywordOI);
        if (keyword != null) ma.addKeyword(keyword);
        return ma;
    }

    private String lowercaseJpg(String filename) {
        if (filename == null) return null;
        return (filename.replace(".JPG", ".jpg"));
    }

    private String removeTailingSpace(String filename) {
        if (filename == null) return null;
        return (filename.replace(" .jpg", ".jpg"));
    }

    private int getNumMediaAssets() {
        if (numMediaAssets == null) setNumMediaAssets();
        return numMediaAssets.intValue();
    }

    private void setNumMediaAssets() {
        int numAssets = 0;

        for (String col : colIndexMap.keySet()) {
            if (col.indexOf("mediaAsset") > -1) numAssets++;
        }
        numMediaAssets = numAssets;
    }

    public MarkedIndividual loadIndividual(Row row, Encounter enc) {
        boolean newIndividual = false;
        String individualID = getIndividualID(row);

        if (individualID == null) return null;
        MarkedIndividual mark = myShepherd.getMarkedIndividualQuiet(individualID);
        if (mark == null) { // new individual
            mark = new MarkedIndividual(individualID, enc);
            newIndividual = true;
        }
        if (mark == null) return mark;
        if (!newIndividual) mark.addEncounterNoCommit(enc);
        String nickname = getString(row, "MarkedIndividual.nickname");
        if (nickname == null) nickname = getString(row, "MarkedIndividual.nickName");
        if (nickname != null) mark.setNickName(nickname);
        String alternateID = getString(row, "MarkedIndividual.alternateID");
        if (alternateID != null) mark.setAlternateID(alternateID);
        String genus = getString(row, "Encounter.genus");
        if (Util.shouldReplace(genus, mark.getGenus())) mark.setGenus(genus);
        String species = getString(row, "Encounter.specificEpithet");
        if (Util.shouldReplace(species, mark.getSpecificEpithet()))
            mark.setSpecificEpithet(species);
        return mark;
    }

    // check if oldOcc is the same occurrence as the occurrence on this row
    // if so, return oldOcc. If not, return parseOccurrence(row)
    public Occurrence getCurrentOccurrence(Occurrence oldOcc, Row row) {
        String occID = getOccurrenceID(row);

        System.out.println("\t\tgetCurrentOccurrence got occID: " + occID);
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
        return (getString(row, "Occurrence.occurrenceID"));
    }

    public boolean isOccurrenceOnRow(Occurrence occ, Row row) {
        return (occ != null && !occ.getOccurrenceID().equals(getOccurrenceID(row)));
    }

//// following 'get' functions swallow errors
    public static Integer getInteger(Row row, int i) {
        try {
            double val = row.getCell(i).getNumericCellValue();
            return new Integer((int)val);
        } catch (Exception e) {}
        return null;
    }

    public static Long getLong(Row row, int i) {
        try {
            double val = row.getCell(i).getNumericCellValue();
            return new Long((long)val);
        } catch (Exception e) {}
        return null;
    }

    public static Double getDouble(Row row, int i) {
        try {
            double val = row.getCell(i).getNumericCellValue();
            return new Double(val);
        } catch (Exception e) {}
        return null;
    }

    public static String getString(Row row, int i) {
        try {
            String str = row.getCell(i).getStringCellValue();
            if (str.equals("")) return null;
            return str;
        } catch (Exception e) {}
        return null;
    }

    public static String getStringOrIntString(Row row, int i) {
        try {
            String str = row.getCell(i).getStringCellValue();
            if (str.equals("")) return null;
            return str;
        } catch (Exception e) {
            try {
                return getInteger(row, i).toString();
            } catch (Exception e2) {}
        }
        return null;
    }

    public static Double getDoubleFromString(Row row, int i) {
        try {
            String intStr = getString(row, i).trim().toLowerCase();
            System.out.println("getDoubleFromString got str " + intStr);
            if (!Util.stringExists(intStr)) return null;
            return Double.valueOf(intStr);
        } catch (Exception e) {
            try {
                String intStr = getString(row, i).trim().toLowerCase().substring(1);
                System.out.println("getDoubleFromString still trying. got str " + intStr);
                if (!Util.stringExists(intStr)) return null;
                return Double.valueOf(intStr);
            } catch (Exception ex) {}
        }
        return null;
    }

    public static Integer getIntegerFromString(Row row, int i) {
        try {
            String intStr = getString(row, i).trim().toLowerCase();
            if (!Util.stringExists(intStr)) return null;
            return Integer.valueOf(intStr);
        } catch (Exception e) {}
        return null;
    }

    public static Long getLongFromString(Row row, int i) {
        try {
            String intStr = getString(row, i).trim().toLowerCase();
            if (!Util.stringExists(intStr)) return null;
            return Long.valueOf(intStr);
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
        if (unusedColumns != null) unusedColumns.remove(colName);
        if (!colIndexMap.containsKey(colName)) {
            if (verbose)
                System.out.println("StandardImport ERROR: getString failed for column name " +
                    colName + ": column not found");
            return null;
        }
        return getString(row, colIndexMap.get(colName));
    }

    public String getIntAsString(Row row, String colName) {
        Integer i = getInteger(row, colName);

        if (i == null) return null;
        return i.toString();
    }

    public Integer getInteger(Row row, String colName) {
        if (unusedColumns != null) unusedColumns.remove(colName);
        if (!colIndexMap.containsKey(colName)) {
            if (verbose)
                System.out.println("StandardImport ERROR: getString failed for column name " +
                    colName + ": column not found");
            return null;
        }
        return getInteger(row, colIndexMap.get(colName));
    }

    public Integer getIntegerFromString(Row row, String colName) {
        if (unusedColumns != null) unusedColumns.remove(colName);
        if (!colIndexMap.containsKey(colName)) {
            if (verbose)
                System.out.println("StandardImport ERROR: getString failed for column name " +
                    colName + ": column not found");
            return null;
        }
        return getIntegerFromString(row, colIndexMap.get(colName));
    }

    public Long getLong(Row row, String colName) {
        if (unusedColumns != null) unusedColumns.remove(colName);
        if (!colIndexMap.containsKey(colName)) {
            if (verbose)
                System.out.println("StandardImport ERROR: getString failed for column name " +
                    colName + ": column not found");
            return null;
        }
        return getLong(row, colIndexMap.get(colName));
    }

    public Long getLongFromString(Row row, String colName) {
        if (unusedColumns != null) unusedColumns.remove(colName);
        if (!colIndexMap.containsKey(colName)) {
            if (verbose)
                System.out.println("StandardImport ERROR: getString failed for column name " +
                    colName + ": column not found");
            return null;
        }
        return getLongFromString(row, colIndexMap.get(colName));
    }

    public Double getDouble(Row row, String colName) {
        if (unusedColumns != null) unusedColumns.remove(colName);
        if (!colIndexMap.containsKey(colName)) {
            if (verbose)
                System.out.println("StandardImport ERROR: getString failed for column name " +
                    colName + ": column not found");
            return null;
        }
        return getDouble(row, colIndexMap.get(colName));
    }

    public Double getDoubleFromString(Row row, String colName) {
        if (unusedColumns != null) unusedColumns.remove(colName);
        if (!colIndexMap.containsKey(colName)) {
            if (verbose)
                System.out.println("StandardImport ERROR: getString failed for column name " +
                    colName + ": column not found");
            return null;
        }
        return getDoubleFromString(row, colIndexMap.get(colName));
    }

    public Date getDate(Row row, String colName) {
        if (unusedColumns != null) unusedColumns.remove(colName);
        if (!colIndexMap.containsKey(colName)) {
            if (verbose)
                System.out.println("StandardImport ERROR: getString failed for column name " +
                    colName + ": column not found");
            return null;
        }
        return getDate(row, colIndexMap.get(colName));
    }

    public DateTime getDateTime(Row row, String colName) {
        if (unusedColumns != null) unusedColumns.remove(colName);
        if (!colIndexMap.containsKey(colName)) {
            if (verbose)
                System.out.println("StandardImport ERROR: getString failed for column name " +
                    colName + ": column not found");
            return null;
        }
        return getDateTime(row, colIndexMap.get(colName));
    }

    // Apache POI, shame on you for making me write this. Shame! Shame! Shame! SHAME!
    // (as if I actually wrote this. thanks stackoverflow!)
    public static boolean isRowEmpty(Row row) {
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
            return "<a href=\"" + getEncounterURL(enc) + "\" >" + enc.getCatalogNumber() + "</a>";
        }
        return enc.getCatalogNumber();
    }

    private AssetStore getAssetStore(Shepherd myShepherd) {
        return AssetStore.getDefault(myShepherd);
    }
}
