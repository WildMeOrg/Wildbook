package org.ecocean.servlet.export;
import javax.servlet.*;
import javax.servlet.http.*;

import java.io.*;
import java.util.*;
import java.util.Map;
import java.util.HashMap;

import org.ecocean.*;
import org.ecocean.genetics.*;
import org.ecocean.servlet.ServletUtilities;

import javax.jdo.*;

import java.lang.StringBuffer;

import jxl.write.*;
import jxl.Workbook;


public class OccurrenceSearchExportGtm extends HttpServlet {

  private static final int BYTES_DOWNLOAD = 1024;

  private static String cleanToString(Object obj) {
    if (obj==null) return "";
    return obj.toString();
  }

  private static Map<String, Integer> getIndividualIDMap(String[] allIndivIDs) {
    Map<String, Integer> individualIDMap = new HashMap<String, Integer>();
    for(int i=0; i<allIndivIDs.length; i++) {
      individualIDMap.put(allIndivIDs[i], i);
    }
    return individualIDMap;
  }

  private static Map<String, Occurrence> getOccurrenceMap(Vector initialOccs) {
    Map<String, Occurrence> occurrenceMap = new HashMap<String, Occurrence>();
    for (int i=0; i<initialOccs.size(); i++) {
      Occurrence occ=(Occurrence)initialOccs.get(i);
      occurrenceMap.put(occ.getOccurrenceID(), occ);
    }
    return occurrenceMap;
  }

  private static Map<Long, List<String>> getMillisToOccIdMap(Vector initialOccs) {
    Map<Long, List<String>> millisToOccIdMap = new HashMap<Long, List<String>>();
    for (int i=0; i<initialOccs.size(); i++) {
      Occurrence occ=(Occurrence)initialOccs.get(i);
      Long millis = occ.getMillisRobust();
      if (millis!=null) {
        if (!millisToOccIdMap.containsKey(millis)) millisToOccIdMap.put(millis, new ArrayList<String>());
        millisToOccIdMap.get(millis).add(occ.getOccurrenceID());
      }
    }
    return millisToOccIdMap;
  }

  private static Long[] getSortedMillis(Map<Long, List<String>> millisToOccIdMap) {
    Set<Long> millisSet = millisToOccIdMap.keySet();
    Long[] sortedMillis = millisSet.toArray(new Long[millisSet.size()]);
    Arrays.sort(sortedMillis);
    return sortedMillis;
  }


  private Set<Object> getDatelessOccurrences(Vector initialOccs) {
    Set badOccs = new HashSet<Object>();
    for (int i=0; i<initialOccs.size(); i++) {
      Occurrence occ=(Occurrence)initialOccs.get(i);
      Long millis = occ.getMillis();
      if (millis == null) millis = occ.getMillisFromEncounterAvg(); // tries to compute millis if it is not stored
      if (millis == null) badOccs.add(initialOccs.get(i));
    }
    System.out.println("getDatelessOccurrences found "+badOccs.size()+" dateless occurrences out of "+initialOccs.size());
    return badOccs;
  }


  public void init(ServletConfig config) throws ServletException {
      super.init(config);
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

      //set up the output stream
      FileOutputStream fos = new FileOutputStream(excelFile);
      OutputStreamWriter outp = new OutputStreamWriter(fos);

      try{


        OccurrenceQueryResult queryResult = OccurrenceQueryProcessor.processQuery(myShepherd, request, "");
        rOccurrences = queryResult.getResult();

        boolean removedDateless = rOccurrences.removeAll(getDatelessOccurrences(rOccurrences));
        int numMatchingOccurrences=rOccurrences.size();
        System.out.println("Returning "+numMatchingOccurrences+" occurrences. removedDateless = "+removedDateless);



       //business logic start here

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


        // =============================== START INDIVID MAP-RECORD
        WritableSheet individualsSheet = workbookOBIS.createSheet("Individual Number Reference", 0);
        individualsSheet.addCell(new Label(0, 0, "GTM member number"));
        individualsSheet.addCell(new Label(1, 0, "Wildbook IndividualID"));

        int count = 0;
        Set<String> allIndivIDs = new HashSet<String>();
         for(int i=0;i<numMatchingOccurrences;i++){
            Occurrence occ=(Occurrence)rOccurrences.get(i);
            allIndivIDs.addAll(occ.getIndividualIDs());
            count++;
            numResults++;
         } //end for loop iterating encounters

        String[] allIndivIDsOrdered = allIndivIDs.toArray(new String[allIndivIDs.size()]);
        for (int i=0; i<allIndivIDsOrdered.length; i++) {
          individualsSheet.addCell(new Label(0, i+1, Integer.toString(i)));
          individualsSheet.addCell(new Label(1, i+1, allIndivIDsOrdered[i]));
        }
        // this way the "m" part of gtm won't contain up to 50 UUIDs per row
        Map<String, Integer> indivIDMap = getIndividualIDMap(allIndivIDsOrdered);
        // ================================= END INDIVID MAP-RECORD


        // =============================== START GTM SHEET
        WritableSheet gtmSheet = workbookOBIS.createSheet("GTM", 1);



        String[] gtmColHeaders = new String[]{
          "group",
          "time",
          "members"
        };

        for (int i=0; i<gtmColHeaders.length; i++) {
          gtmSheet.addCell(new Label(i, 0, gtmColHeaders[i]));
        }

        Map<Long, List<String>> millisToOccIdMap = getMillisToOccIdMap(rOccurrences);
        Long[] sortedMillis = getSortedMillis(millisToOccIdMap);
        Map<String, Occurrence> occurrenceMap = getOccurrenceMap(rOccurrences);


        count = 0;
        for(int i=0;i<sortedMillis.length;i++){
          Long millis = sortedMillis[i];
          for (String occId : millisToOccIdMap.get(millis)) {
            // group
            gtmSheet.addCell(new Label(0, i+1, occId.toString()));
            // time
            gtmSheet.addCell(new Label(1, i+1, millis.toString()));
            // members
            List<String> members = new ArrayList<String>(occurrenceMap.get(occId).getIndividualIDs());
            for (int j=0; j<members.size(); j++) {
              Integer newID = indivIDMap.get(members.get(j));
              gtmSheet.addCell(new Label(2+j, i+1, newID.toString()));
            }
            if ((i%50)==0) System.out.println("printing occurrence "+i+" = "+occId);
          }

          count++;
          numResults++;

        } //end for loop iterating encounters


        // count = 0;
        //  for(int i=0;i<numMatchingOccurrences;i++){
        //     Occurrence enc=(Occurrence)rOccurrences.get(i);
        //     if ((i%50)==0) System.out.println("printing occurrence "+i+" = "+enc.getOccurrenceID());
        //
        //     count++;
        //     numResults++;
        //
        //  } //end for loop iterating encounters

         // ================================= END GTM SHEET





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
