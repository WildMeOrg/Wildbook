package org.ecocean.servlet.export;
import javax.servlet.*;
import javax.servlet.http.*;

import java.io.*;
import java.util.*;
import org.ecocean.*;
import org.ecocean.media.*;
import org.ecocean.genetics.*;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.security.*;
import javax.jdo.*;
import java.lang.StringBuffer;
import jxl.write.*;
import jxl.Workbook;

import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;

public class EncounterSearchExportMetadataExcel extends HttpServlet {


  // The goal of this class is to make it so we can define a column in one place
  // and each row of the export be generated automatically from the list of
  // ExportColumns

  // this would be a static method of above subclass if java allowed that
  public static ExportColumn newEasyColumn(String classDotFieldNameHeader, List<ExportColumn> columns) throws ClassNotFoundException, NoSuchMethodException {
    return ExportColumn.newEasyColumn(classDotFieldNameHeader, columns);
  }

  private static final int BYTES_DOWNLOAD = 1024;

  private int numMediaAssetCols = 0;
  private int numKeywords = 0;
  private int numNameCols = 0;

  int rowLimit = 100000;

  private void setMediaAssetCounts(Vector rEncounters) {
    System.out.println("EncounterSearchExportMetadataExcel: setting environment vars for "+Math.max(rEncounters.size(), rowLimit)+" encs.");
    int maxNumMedia = 0;
    int maxNumKeywords = 0;
    int maxNumNames = 0;
    Set<String> individualIDsChecked = new HashSet<String>();
    for (int i=0;i<rEncounters.size() && i< rowLimit;i++) {
      Encounter enc=(Encounter)rEncounters.get(i);
      ArrayList<MediaAsset> mas = enc.getMedia();
      int numMedia = mas.size();
      if (numMedia > maxNumMedia) maxNumMedia = numMedia;
      for (MediaAsset ma : mas) {
        int numKw = ma.numKeywords();
        if (numKw > maxNumKeywords) maxNumKeywords = numKw;
      }
      MarkedIndividual id = enc.getIndividual();
      if (id!=null && !individualIDsChecked.contains(id.getIndividualID())) {
        individualIDsChecked.add(id.getIndividualID());
        int numNames = enc.getIndividual().numNames();
        //System.out.println("Individual "+enc.getIndividual()+" isnull = "+(enc.getIndividual()==null)+" and has # names: "+numNames);
        if (numNames>maxNumNames) maxNumNames = numNames;
      }
    }
    numMediaAssetCols = maxNumMedia;
    numKeywords = maxNumKeywords;
    numNameCols = maxNumNames;
    System.out.println("EncounterSearchExportMetadataExcel: environment vars numMediaAssetCols = "+numMediaAssetCols+"; maxNumKeywords = "+maxNumKeywords+" and maxNumNames = "+numNameCols);

  }


  public void init(ServletConfig config) throws ServletException {
      super.init(config);
  }

  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException,IOException {
      doPost(request, response);
  }

  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

    String context = ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);

    Vector rEncounters = new Vector();

    //set up the files
    String filename = "encounterSearchResults_export_" + request.getRemoteUser() + ".xls";
    //setup data dir
    String rootWebappPath = getServletContext().getRealPath("/");
    File webappsDir = new File(rootWebappPath).getParentFile();
    File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));
    if(!shepherdDataDir.exists()){shepherdDataDir.mkdirs();}
    File encountersDir=new File(shepherdDataDir.getAbsolutePath()+"/encounters");
    if(!encountersDir.exists()){encountersDir.mkdirs();}

    File excelFile = new File(encountersDir.getAbsolutePath()+"/"+ filename);
    myShepherd.beginDBTransaction();

    try {

      EncounterQueryResult queryResult = EncounterQueryProcessor.processQuery(myShepherd, request, "year descending, month descending, day descending");
      rEncounters = queryResult.getResult();
      int numMatchingEncounters = rEncounters.size();

      // Security: categorize hidden encounters with the initializer
      HiddenEncReporter hiddenData = new HiddenEncReporter(rEncounters, request, myShepherd);

      // so we know how many MA columns we need
      setMediaAssetCounts(rEncounters);

      // so we know how many name columns we need


      // business logic start here
      WritableWorkbook excelWorkbook = Workbook.createWorkbook(excelFile);
      WritableSheet sheet = excelWorkbook.createSheet("Search Results", 0);
      
      List<ExportColumn> columns = new ArrayList<ExportColumn>();
      newEasyColumn("Encounter.catalogNumber", columns); // adds Encounter.catalogNumber to columns
      //newEasyColumn("Encounter.individualID", columns);
      //newEasyColumn("Encounter.alternateID", columns);
      MultiValueExportColumn.addNameColumns(numNameCols, columns);
      newEasyColumn("Occurrence.occurrenceID", columns);
      newEasyColumn("Occurrence.sightingPlatform", columns);
      newEasyColumn("Encounter.decimalLatitude", columns);
      newEasyColumn("Encounter.decimalLongitude", columns);
      newEasyColumn("Encounter.locationID", columns);
      newEasyColumn("Encounter.verbatimLocality", columns);
      newEasyColumn("Encounter.country", columns);

      Method encDepthGetter = Encounter.class.getMethod("getDepthAsDouble", null); // depth is special bc the getDepth getter can fail with a NPE
      ExportColumn depthIsSpecial = new ExportColumn(Encounter.class, "Encounter.depth", encDepthGetter, columns);
      
      newEasyColumn("Encounter.dateInMilliseconds", columns);
      newEasyColumn("Encounter.year", columns);
      newEasyColumn("Encounter.month", columns);
      newEasyColumn("Encounter.day", columns);
      newEasyColumn("Encounter.hour", columns);
      newEasyColumn("Encounter.minutes", columns);
      newEasyColumn("Encounter.submitterOrganization", columns);
      newEasyColumn("Encounter.submitterID", columns);
      newEasyColumn("Encounter.recordedBy", columns);
      newEasyColumn("Occurrence.groupComposition", columns);
      newEasyColumn("Occurrence.groupBehavior", columns);
      newEasyColumn("Occurrence.minGroupSizeEstimate", columns);
      newEasyColumn("Occurrence.bestGroupSizeEstimate", columns);
      newEasyColumn("Occurrence.maxGroupSizeEstimate", columns);
      newEasyColumn("Occurrence.numAdults", columns);
      newEasyColumn("Occurrence.numJuveniles", columns);
      newEasyColumn("Occurrence.numCalves", columns);
      newEasyColumn("Occurrence.initialCue", columns);
      newEasyColumn("Occurrence.seaState", columns);
      newEasyColumn("Occurrence.seaSurfaceTemp", columns);
      newEasyColumn("Occurrence.swellHeight", columns);
      newEasyColumn("Occurrence.visibilityIndex", columns);
      newEasyColumn("Occurrence.effortCode", columns);
      newEasyColumn("Occurrence.observer", columns);
      newEasyColumn("Occurrence.transectName", columns);
      newEasyColumn("Occurrence.transectBearing", columns);
      newEasyColumn("Occurrence.distance", columns);
      newEasyColumn("Occurrence.bearing", columns);
      newEasyColumn("Occurrence.comments", columns);
      newEasyColumn("Occurrence.humanActivityNearby", columns);
      newEasyColumn("Encounter.patterningCode", columns);
      newEasyColumn("Encounter.flukeType", columns);
      newEasyColumn("Encounter.behavior", columns);
      newEasyColumn("Encounter.groupRole", columns);
      newEasyColumn("Encounter.sex", columns);
      newEasyColumn("Encounter.lifeStage", columns);
      newEasyColumn("Encounter.genus", columns);
      newEasyColumn("Encounter.specificEpithet", columns);
      newEasyColumn("Encounter.otherCatalogNumbers", columns);
      newEasyColumn("Encounter.occurrenceRemarks", columns);


      // int numMediaAssets = 3;
      // for (int i=0; i<=numMediaAssets; i++) {
      Method maGetFilename = MediaAsset.class.getMethod("getFilename", null);
      Method maLocalPath   = MediaAsset.class.getMethod("localPath", null);
      // This will include labels in a labeledKeyword value
      Method keywordGetName   = Keyword.class.getMethod("getDisplayName");
      for (int maNum = 0; maNum < numMediaAssetCols; maNum++) { // numMediaAssetCols set by setter above
        String mediaAssetColName = "Encounter.mediaAsset"+maNum;
        String fullPathName = "Encounter.mediaAsset"+maNum+".filePath";
        ExportColumn maFilenameK = new ExportColumn(MediaAsset.class, mediaAssetColName, maGetFilename, columns);
        maFilenameK.setMaNum(maNum); // important for later!
        ExportColumn maPathK = new ExportColumn(MediaAsset.class, fullPathName, maLocalPath, columns);
        maPathK.setMaNum(maNum);
        ExportColumn relPos = new ExportColumn(Annotation.class, "Encounter.positionInMediaAsset" + maNum, Annotation.class.getMethod("relativePosition", null), columns);
        relPos.setMaNum(maNum);

        for (int kwNum = 0; kwNum < numKeywords; kwNum++) {
          String keywordColName = "Encounter.mediaAsset"+maNum+".keyword"+kwNum;
          ExportColumn keywordCol = new ExportColumn(Keyword.class, keywordColName, keywordGetName, columns);
          keywordCol.setMaNum(maNum);
          keywordCol.setKwNum(kwNum);
        }

      }

      for (ExportColumn exportCol: columns) {
        exportCol.writeHeaderLabel(sheet);
      }

      // Excel export =========================================================
      int row = 0;
      for (int i=0;i<numMatchingEncounters && i<rowLimit;i++) {

        Encounter enc=(Encounter)rEncounters.get(i);
        // Security: skip this row if user doesn't have permission to view this encounter
        if (hiddenData.contains(enc)) continue;
        row++;

        // get attached objects
        Occurrence occ = myShepherd.getOccurrence(enc);
        MarkedIndividual ind = myShepherd.getMarkedIndividual(enc);
        MultiValue names = (ind!=null) ? ind.getNames() : null;
        List<String> sortedNameKeys = (names!=null) ? names.getSortedKeys() : null;
        List<Annotation> anns = enc.getAnnotations();
        List<MediaAsset> mas = new ArrayList<MediaAsset>();
        for (Annotation ann : anns) {
            mas.add(ann.getMediaAsset());
        }

        // use exportColumns, passing in the appropriate object for each column
        // (can't use switch statement bc Class is not a java primitive type)
        for (ExportColumn exportCol: columns) {
          if (exportCol.isFor(Encounter.class)) exportCol.writeLabel(enc, row, sheet);
          else if (exportCol.isFor(Occurrence.class)) exportCol.writeLabel(occ, row, sheet);
          else if (exportCol.isFor(MarkedIndividual.class)) exportCol.writeLabel(ind, row, sheet);
          else if (exportCol.isFor(MultiValue.class)) {
            MultiValueExportColumn multiValCol = (MultiValueExportColumn) exportCol;
            multiValCol.writeLabel(sortedNameKeys, names, row, sheet);
          }
          else if (exportCol.isFor(MediaAsset.class)) {
            int num = exportCol.getMaNum();
            if (num >= mas.size()) continue;
            MediaAsset ma = mas.get(num);
            if (ma == null) continue; // on to next column
            exportCol.writeLabel(ma, row, sheet);
          }
          else if (exportCol.isFor(Annotation.class)) {
            int num = exportCol.getMaNum();
            if (num >= anns.size()) continue;
            Annotation ann = anns.get(num);
            if (ann == null) continue;
            exportCol.writeLabel(ann, row, sheet);
          }
          else if (exportCol.isFor(Keyword.class)) {
            int maNum = exportCol.getMaNum();
            if (maNum >= mas.size()) continue;
            MediaAsset ma = mas.get(maNum);
            if (ma == null) continue; // on to next column
            int kwNum = exportCol.getKwNum();
            if (kwNum >= ma.numKeywords()) continue;
            Keyword kw = ma.getKeyword(kwNum);
            if (kw == null) continue;
            exportCol.writeLabel(kw, row, sheet);
          }
          else System.out.println("EncounterSearchExportMetadataExcel: no object found for class "+exportCol.getDeclaringClass());
        }

     	} //end for loop iterating encounters

      // Security: log the hidden data report in excel so the user can request collaborations with owners of hidden data
      hiddenData.writeHiddenDataReport(excelWorkbook);

      excelWorkbook.write();
      excelWorkbook.close();
      // end Excel export and business logic ===============================================
      System.out.println("Done with EncounterSearchExportMetadataExcel. We hid "+hiddenData.size()+" encounters.");
    }
    catch (Exception e) {
      e.printStackTrace();
      response.setContentType("text/html");
      PrintWriter out = response.getWriter();
      out.println(ServletUtilities.getHeader(request));
      out.println("<html><body><p><strong>Error encountered</strong></p>");
      out.println("<p>Please let the webmaster know you encountered an error at: EncounterSearchExportExcelFile servlet</p></body></html>");
      out.println(ServletUtilities.getFooter(context));
      out.close();
    }

    myShepherd.rollbackDBTransaction();
    myShepherd.closeDBTransaction();

    // now write out the file
    response.setContentType("application/msexcel");
    response.setHeader("Content-Disposition","attachment;filename="+filename);
    InputStream is=new FileInputStream(excelFile);
    int read=0;
    byte[] bytes = new byte[BYTES_DOWNLOAD];
    OutputStream os = response.getOutputStream();
    while((read = is.read(bytes))!= -1){
      os.write(bytes, 0, read);
    }
    os.flush();
    os.close();
  }

}
