package org.ecocean.servlet.export;
import javax.servlet.*;
import javax.servlet.http.*;

import java.io.*;
import java.util.*;
import java.text.SimpleDateFormat;

import org.ecocean.*;
import org.ecocean.genetics.*;
import org.ecocean.social.Relationship;
import org.ecocean.servlet.ServletUtilities;
import static org.ecocean.Util.toString;

import javax.jdo.*;

import java.lang.reflect.Method;
import java.lang.StringBuffer;

import jxl.write.*;
import jxl.Workbook;
import jxl.CellType;


public class SwotExport extends HttpServlet{

  private static final int BYTES_DOWNLOAD = 1024;


  private void addStringToCell(WritableSheet ws, int row, int col, String str) throws jxl.write.WriteException {
    System.out.println("Adding string "+str+" to cell");
    if (str==null) return;
    System.out.println("Still adding string "+str+" to cell");
    Label lbl = new Label(col, row, str);
    ws.addCell(lbl);
  }

  public void init(ServletConfig config) throws ServletException {
      super.init(config);
    }


  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException,IOException {
      doPost(request, response);
  }

  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

    //set the response

    String context="context0";
    context=ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);

    // String query = request.getParameter("query");
    // String[] headers = request.getParameterValues("headers");
    // String[] columns = request.getParameterValues("columns");
    // Collection c = (Collection) myShepherd.getPM().newQuery(query).execute();
    // Vector v = new Vector(c);
    // Class cls = v.get(0).getClass();

    String filename = request.getParameter("filename");
    if (filename == null) {
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat df = new SimpleDateFormat("yyyyMMddHHmmss");
        filename = "export-" + df.format(cal.getTime()) + ".xls";
    }
    String sheetname = request.getParameter("sheetname");
    if (sheetname == null) sheetname = "Export";

    String newFileName = "/tmp/" + filename;
    File oldFile = new File("/data/wildbook_data_dir/SWOTDatasheet2015.xls");
    Workbook oldBook;
    try {
      oldBook = Workbook.getWorkbook(oldFile);
      System.out.println("Successfully grabbed old workbook with "+oldBook.getNumberOfSheets()+" sheets.");


      File excelFile = new File("/tmp/" + filename);

      FileOutputStream exfos = new FileOutputStream(excelFile);
      OutputStreamWriter exout = new OutputStreamWriter(exfos);
      //WritableCellFormat floatFormat = new WritableCellFormat(NumberFormats.FLOAT);
      //WritableCellFormat integerFormat = new WritableCellFormat(NumberFormats.INTEGER);
      //WritableWorkbook workbook = Workbook.createWorkbook(excelFile);
      //WritableSheet sheet = workbook.createSheet(sheetname, 0);

      WritableWorkbook workbook = Workbook.createWorkbook(excelFile, oldBook);


      int sheetRow = 6;


      NestQueryResult queryResult = NestQueryProcessor.processQuery(myShepherd, request, "year descending, month descending, day descending");
      Vector returnedNests = queryResult.getResult();


      Iterator all = returnedNests.iterator();
      Nest nest = null;

      WritableSheet dataProviderSheet = workbook.getSheet(0);
      WritableSheet nestingBeachSheet = workbook.getSheet(1);

      User thisUser = myShepherd.getLoggedInUser(request);
      if (thisUser != null) {
        System.out.println("SwotExport for user "+thisUser.getUsername());
        System.out.println("    with full name =  "+thisUser.getFullName());
      } else {
        System.out.println("SwotExport: user is not logged in");
      }

      try {


          int rowNum = 4;

          addStringToCell(dataProviderSheet, rowNum, 1, "standard");

          addStringToCell(dataProviderSheet, rowNum, 2, "submitter");

          if (thisUser!= null) {
            addStringToCell(dataProviderSheet, rowNum, 4, thisUser.getFirstName());

            addStringToCell(dataProviderSheet, rowNum, 5, thisUser.getLastName());

            addStringToCell(dataProviderSheet, rowNum, 6, thisUser.getAffiliation());

            addStringToCell(dataProviderSheet, rowNum, 7, thisUser.getEmailAddress());

          }

          int nestRowNum = 6;
          while (all.hasNext()) {
              nest = (Nest) all.next();

              // nest ID and name info
              addStringToCell(nestingBeachSheet, nestRowNum, 27, "Wildbook nest ID="+nest.getID()+"\nname = "+nest.getName());

              // required fields for SWOT
              addStringToCell(nestingBeachSheet, nestRowNum, 1, Util.toString(nest.getYear()));
              addStringToCell(nestingBeachSheet, nestRowNum, 2, nest.getSpecies());
              addStringToCell(nestingBeachSheet, nestRowNum, 3, nest.getCountry());
              addStringToCell(nestingBeachSheet, nestRowNum, 4, nest.getOrganization());
              addStringToCell(nestingBeachSheet, nestRowNum, 5, nest.getProvince());
              addStringToCell(nestingBeachSheet, nestRowNum, 6, nest.getBeachName());
              addStringToCell(nestingBeachSheet, nestRowNum, 7, Util.toString(nest.getLatitude()));
              addStringToCell(nestingBeachSheet, nestRowNum, 8, Util.toString(nest.getLongitude()));



              addStringToCell(nestingBeachSheet, nestRowNum, 26, "IoT Wildbook Export "+filename);


              System.out.println("SwotExport: starting to export Nest "+nest.getID());

              nestRowNum++;

          }


          //Label;


          workbook.write();
          workbook.close();
      } catch (Exception wex) {
          System.out.println("exception writing excel: " + wex.toString());
      }


      //response.setContentType("application/vnd.ms-excel");
      response.setContentType("application/octet-stream");
      response.setHeader("Content-Transfer-nestoding", "binary");
      response.setHeader("Content-Disposition", "filename=" + filename);

      InputStream is = new FileInputStream(excelFile);
      OutputStream os = response.getOutputStream();
      byte[] buf = new byte[1000];
      for (int n = is.read(buf) ; n > -1 ; n = is.read(buf)) {
          os.write(buf, 0, n);
      }
      os.flush();
      os.close();
      is.close();

    } catch (jxl.read.biff.BiffException biff) {
      System.out.println("What the fuck, Biff?");
    }




  }

}
