package org.ecocean.servlet.export;
import javax.servlet.*;
import javax.servlet.http.*;

import java.io.*;
import java.util.*;

import org.ecocean.*;
import org.ecocean.genetics.*;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.security.HiddenOccReporter;

import javax.jdo.*;

import java.lang.StringBuffer;

import jxl.write.*;
import jxl.Workbook;


public class OccurrenceSearchExportMetadataExcel extends HttpServlet {

  private static final int BYTES_DOWNLOAD = 1024;

  private static String cleanToString(Object obj) {
    if (obj==null) return "";
    return obj.toString();
  }

  private WritableSheet sheet; // main data sheet on the excel file that will be modified
  private Map<String,Integer> colNames; // column headers (will be used to write cells by col name, not number)
  private int currentRow; // having this as a class-wide var is great bc we don't have to pass around

  private void loadColHeaders(String[] headers) throws jxl.write.WriteException {
    for (int i=0; i<headers.length; i++) {
      colNames.put(headers[i],i);
      sheet.addCell(new Label(i, 0, headers[i]));
    }
  }

  private int getColNum(String colName) throws jxl.write.WriteException {
    if(colNames.containsKey(colName)) return colNames.get(colName);
    int newColNum = colNames.size();
    colNames.put(colName, newColNum);
    sheet.addCell(new Label(newColNum, 0, colName));
    return newColNum;
  }

  // write cell finds the right cell based on the col name, the running colHeaderList, and currentRow
  private void writeCell(String colName, Object value) throws jxl.write.WriteException {
    if (value==null) return;
    int col = getColNum(colName);
    sheet.addCell(new Label(col, currentRow, value.toString()));
  }

  public void init(ServletConfig config) throws ServletException {
      super.init(config);
      colNames = new HashMap<String,Integer>();
      sheet = null;
      currentRow = 1; // start under the header row
    }


  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException,IOException {
      doPost(request, response);
  }



  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException{

    //set the response

    String context="context0";
    context=ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);



    Vector rOccurrences = new Vector();
    int numResults = 0;


    //set up the files
    String filename = "sightingSearchResults_export_" + request.getRemoteUser() + ".xls";

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

      //set up the output stream
      FileOutputStream fos = new FileOutputStream(excelFile);
      OutputStreamWriter outp = new OutputStreamWriter(fos);

      try{


        OccurrenceQueryResult queryResult = OccurrenceQueryProcessor.processQuery(myShepherd, request, "");
        rOccurrences = queryResult.getResult();


        int numMatchingOccurrences=rOccurrences.size();

       //business logic start here
        HiddenOccReporter hiddenData = new HiddenOccReporter(rOccurrences, request,myShepherd);

        //load the optional locales
        Properties props = new Properties();
        try {
          props=ShepherdProperties.getProperties("locationIDGPS.properties", "",context);

        } catch (Exception e) {
          System.out.println("     Could not load locales.properties OccurrenceSearchExportExcelFile.");
          e.printStackTrace();
        }

      //let's set up some cell formats
        WritableCellFormat floatFormat = new WritableCellFormat(NumberFormats.FLOAT);
        WritableCellFormat integerFormat = new WritableCellFormat(NumberFormats.INTEGER);

      //let's write out headers for the OBIS export file
        WritableWorkbook workbookOBIS = Workbook.createWorkbook(excelFile);
        sheet = workbookOBIS.createSheet("Search Results", 0);

        String[] colHeaders = new String[]{
          "occurrenceID",
          "dateTime",
          "decimalLatitude",
          "decimalLongitude",
          "taxonomies",
          "individualCount",
          "groupBehavior",
          "comments",
          "modified",
          "dateTimeCreated",
          "fieldStudySite",
          "fieldSurveyCode",
          "sightingPlatform",
          "groupComposition",
          "humanActivityNearby",
          "initialCue",
          "seaState",
          "seaSurfaceTemp",
          "swellHeight",
          "visibilityIndex",
          "effortCode",
          "transectName",
          "transectBearing",
          "distance",
          "bearing",
          "minGroupSizeEstimate",
          "maxGroupSizeEstimate",
          "bestGroupSizeEstimate",
          "numAdults",
          "numJuveniles",
          "numCalves",
          "observer",
          "submitterID",
          "encounterIDs"
        };

        // if we don't call loadColHeaders, any fully-blank column is totally absent from the data.
        // this is undesirable because "there is no data in this column" is important to show the user
        loadColHeaders(colHeaders);

        // Excel export =========================================================

        int printPeriod = 10;
         for(int i=0;i<numMatchingOccurrences;i++){
            boolean verbose = (i%printPeriod == 0);
            Occurrence occ=(Occurrence)rOccurrences.get(i);
            if (hiddenData.contains(occ)) {
              if (verbose) System.out.println("OSEME: Hiding row "+i);
              continue;
            }


            numResults++;

            List<Label> rowLabels = new ArrayList<Label>();

            writeCell("occurrenceID", occ.getOccurrenceID());
            writeCell("dateTime", occ.getDateTime());
            writeCell("decimalLatitude", occ.getDecimalLatitude());
            writeCell("decimalLongitude", occ.getDecimalLongitude());
            writeCell("taxonomies", occ.getAllSpecies()); // the getAllSpecies List<String> toStrings nicely
            writeCell("individualCount", occ.getIndividualCount());
            writeCell("groupBehavior", occ.getGroupBehavior());
            writeCell("commments", occ.getComments());
            writeCell("modified", occ.getDWCDateLastModified());
            writeCell("dateTimeCreated", occ.getDateTimeCreated());
            writeCell("fieldStudySite", occ.getFieldStudySite());
            writeCell("fieldSurveyCode", occ.getFieldSurveyCode());
            writeCell("sightingPlatform", occ.getSightingPlatform());
            writeCell("groupComposition", occ.getGroupComposition());
            writeCell("humanActivityNearby", occ.getHumanActivityNearby());
            writeCell("initialCue", occ.getInitialCue());
            writeCell("seaState", occ.getSeaState());
            writeCell("seaSurfaceTemp", occ.getSeaSurfaceTemp());
            writeCell("swellHeight", occ.getSwellHeight());
            writeCell("visibilityIndex", occ.getVisibilityIndex());
            writeCell("effortCode", occ.getEffortCode());
            writeCell("transectName", occ.getTransectName());
            writeCell("transectBearing", occ.getTransectBearing());
            writeCell("distance", occ.getDistance());
            writeCell("bearing", occ.getBearing());
            writeCell("minGroupSizeEstimate", occ.getMinGroupSizeEstimate());
            writeCell("maxGroupSizeEstimate", occ.getMaxGroupSizeEstimate());
            writeCell("bestGroupSizeEstimate", occ.getBestGroupSizeEstimate());
            writeCell("numAdults", occ.getNumAdults());
            writeCell("numJuveniles", occ.getNumJuveniles());
            writeCell("numCalves", occ.getNumCalves());
            writeCell("observer", occ.getObserver());
            writeCell("submitterID", occ.getSubmitterID());
            writeCell("encounterIDs", occ.getEncounterIDs());
            writeCell("web link", occ.getWebUrl(request));
            writeCell("encounter web links", occ.getEncounterWebUrls(request));            

            if (verbose) System.out.println("OccurrenceSearchExportMetadataExcel: done row "+currentRow);
            currentRow++;
         } //end for loop iterating encounters

         hiddenData.writeHiddenDataReport(workbookOBIS);

         workbookOBIS.write();
         workbookOBIS.close();

      // end Excel export =========================================================



        //business logic end here

        outp.close();
        outp=null;

      }
      catch(Exception ioe){
        ioe.printStackTrace();
        response.setContentType("text/html");
        PrintWriter out = response.getWriter();
        out.println(ServletUtilities.getHeader(request));
        out.println("<html><body><p><strong>Error encountered</strong> with file writing. Check the relevant log.</p>");
        out.println("<p>Please let the webmaster know you encountered an error at: OccurrenceSearchExportExcelFile servlet</p></body></html>");
        out.println(ServletUtilities.getFooter(context));
        out.close();
        outp.close();
        outp=null;
      }


    }
    catch(Exception e) {
      e.printStackTrace();
      response.setContentType("text/html");
      PrintWriter out = response.getWriter();
      out.println(ServletUtilities.getHeader(request));
      out.println("<html><body><p><strong>Error encountered</strong></p>");
        out.println("<p>Please let the webmaster know you encountered an error at: OccurrenceSearchExportExcelFile servlet</p></body></html>");
        out.println(ServletUtilities.getFooter(context));
        out.close();
    }

    myShepherd.rollbackDBTransaction();
    myShepherd.closeDBTransaction();

      //now write out the file
      response.setContentType("application/msexcel");
      response.setHeader("Content-Disposition","attachment;filename="+filename);
      ServletContext ctx = getServletContext();
      //InputStream is = ctx.getResourceAsStream("/encounters/"+filename);
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
