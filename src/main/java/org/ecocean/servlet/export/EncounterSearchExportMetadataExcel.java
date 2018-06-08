package org.ecocean.servlet.export;
import javax.servlet.*;
import javax.servlet.http.*;

import java.io.*;
import java.util.*;

import org.ecocean.*;
import org.ecocean.genetics.*;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.security.*;

import javax.jdo.*;

import java.lang.StringBuffer;

import jxl.write.*;
import jxl.Workbook;


public class EncounterSearchExportMetadataExcel extends HttpServlet {

  private static final int BYTES_DOWNLOAD = 1024;

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

      // business logic start here
      WritableWorkbook excelWorkbook = Workbook.createWorkbook(excelFile);
      WritableSheet sheet = excelWorkbook.createSheet("Search Results", 0);
      String[] colHeaders = new String[]{
        "catalog number",
        "individual ID",
        "occurrence ID",
        "year",
        "month",
        "day",
        "hour",
        "minutes",
        "milliseconds (definitive datetime)",
        "latitude",
        "longitude",
        "locationID",
        "country",
        "other catalog numbers",
        "submitter name",
        "submitter organization",
        "sex",
        "behavior",
        "life stage",
        "group role",
        "researcher comments",
        "verbatim locality",
        "alternate ID",
        "web URL",
        "individual web URL",
        "occurrence web URL"
      };
      for (int i=0; i<colHeaders.length; i++) {
        sheet.addCell(new Label(i, 0, colHeaders[i]));
      }

      // Security: categorize hidden encounters with the initializer
      HiddenEncReporter hiddenData = new HiddenEncReporter(rEncounters, request);

      // Excel export =========================================================
      int row = 0;
      for (int i=0;i<numMatchingEncounters;i++) {

        Encounter enc=(Encounter)rEncounters.get(i);
        // Security: skip this row if user doesn't have permission to view this encounter
        if (hiddenData.contains(enc)) continue;
        row++;

        // write data cells (corresponding to colHeaders above)
        sheet.addCell(new Label(0,  row, enc.getCatalogNumber()));
        sheet.addCell(new Label(1,  row, enc.getIndividualID()));
        sheet.addCell(new Label(2,  row, enc.getOccurrenceID()));
        sheet.addCell(new Label(3,  row, String.valueOf(enc.getYear())));
        sheet.addCell(new Label(4,  row, String.valueOf(enc.getMonth())));
        sheet.addCell(new Label(5,  row, String.valueOf(enc.getDay())));
        sheet.addCell(new Label(6,  row, String.valueOf(enc.getHour())));
        sheet.addCell(new Label(7,  row, enc.getMinutes()));
        sheet.addCell(new Label(8,  row, String.valueOf(enc.getDateInMilliseconds())));
        sheet.addCell(new Label(9,  row, enc.getDecimalLatitude()));
        sheet.addCell(new Label(10, row, enc.getDecimalLongitude()));
        sheet.addCell(new Label(11, row, enc.getLocationID()));
        sheet.addCell(new Label(12, row, enc.getCountry()));
        sheet.addCell(new Label(13, row, enc.getOtherCatalogNumbers()));
        sheet.addCell(new Label(14, row, enc.getSubmitterName()));
        sheet.addCell(new Label(15, row, enc.getSubmitterOrganization()));
        sheet.addCell(new Label(16, row, enc.getSex()));
        sheet.addCell(new Label(17, row, enc.getBehavior()));
        sheet.addCell(new Label(18, row, enc.getLifeStage()));
        sheet.addCell(new Label(19, row, enc.getGroupRole()));
        sheet.addCell(new Label(20, row, enc.getRComments()));
        sheet.addCell(new Label(21, row, enc.getVerbatimLocality()));
        sheet.addCell(new Label(22, row, enc.getAlternateID()));
        sheet.addCell(new Label(23, row, ServletUtilities.getEncounterUrl(enc.getCatalogNumber(),request)));
        sheet.addCell(new Label(24, row, ServletUtilities.getIndividualUrl(enc.getIndividualID(),request)));
        sheet.addCell(new Label(25, row, ServletUtilities.getOccurrenceUrl(enc.getOccurrenceID(),request)));
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
