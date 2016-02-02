package org.ecocean.servlet.export;
import javax.servlet.*;
import javax.servlet.http.*;

import java.io.*;
import java.util.*;

import org.ecocean.*;
import org.ecocean.genetics.*;
import org.ecocean.servlet.ServletUtilities;

import javax.jdo.*;

import java.lang.StringBuffer;

import jxl.write.*;
import jxl.Workbook;


public class EncounterSearchExportExcelFile extends HttpServlet{

  private static final int BYTES_DOWNLOAD = 1024;


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



    Vector rEncounters = new Vector();
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


        EncounterQueryResult queryResult = EncounterQueryProcessor.processQuery(myShepherd, request, "year descending, month descending, day descending");
        rEncounters = queryResult.getResult();

				Vector blocked = Encounter.blocked(rEncounters, request);
				if (blocked.size() > 0) {
					response.setContentType("text/html");
					PrintWriter out = response.getWriter();
					out.println(ServletUtilities.getHeader(request));
					out.println("<html><body><p><strong>Access denied.</strong></p>");
					out.println(ServletUtilities.getFooter(context));
					out.close();
					return;
				}

        int numMatchingEncounters=rEncounters.size();

       //business logic start here

        //load the optional locales
        Properties props = new Properties();
        try {
          props=ShepherdProperties.getProperties("locationIDGPS.properties", "",context);

        } catch (Exception e) {
          System.out.println("     Could not load locales.properties EncounterSearchExportExcelFile.");
          e.printStackTrace();
        }

      //let's set up some cell formats
        WritableCellFormat floatFormat = new WritableCellFormat(NumberFormats.FLOAT);
        WritableCellFormat integerFormat = new WritableCellFormat(NumberFormats.INTEGER);

      //let's write out headers for the OBIS export file
        WritableWorkbook workbookOBIS = Workbook.createWorkbook(excelFile);
        WritableSheet sheet = workbookOBIS.createSheet("Survey", 0);
        Label label0 = new Label(0, 1, "SHARK-ID is unique");
        sheet.addCell(label0);
        Label label1 = new Label(1, 1, "SHARK-ID");
        sheet.addCell(label1);
        Label label2 = new Label(2, 1, "IMAGE NAME");
        sheet.addCell(label2);
        Label label2a = new Label(3, 1, "LOCATION");
        sheet.addCell(label2a);
        Label label3 = new Label(4, 1, "YEAR");
        sheet.addCell(label3);
        Label label5 = new Label(5, 1, "MONTH");
        sheet.addCell(label5);
        Label label6 = new Label(6, 1, "GENDER");
        sheet.addCell(label6);
        Label label7 = new Label(7, 1, "FLANK");
        sheet.addCell(label7);
        Label label8 = new Label(8, 1, "PHOTOGRAPHER");
        sheet.addCell(label8);
        Label label9 = new Label(9, 1, "MATCH");
        sheet.addCell(label9);
        Label label10 = new Label(10, 1, "MIGRATION");
        sheet.addCell(label10);
        Label label11 = new Label(11, 1, "HOOK MARK OTHER NONE");
        sheet.addCell(label11);
        Label label12 = new Label(12, 1, "LASER");
        sheet.addCell(label12);
        Label label13 = new Label(13, 1, "PRE CAUD SIZE CM");
        sheet.addCell(label13);
        Label label14 = new Label(14, 1, "TOTAL SIZE CM");
        sheet.addCell(label14);
        Label label15 = new Label(15, 1, "AGE");
        sheet.addCell(label15);
        Label label16 = new Label(16, 1, "WATER TEMP");
        sheet.addCell(label16);
        Label label17 = new Label(17, 1, "LAT");
        sheet.addCell(label17);
        Label label18 = new Label(18, 1, "LONG");
        sheet.addCell(label18);
        Label label19 = new Label(19, 1, "# SHARKS AT CAVE (MP)");
        sheet.addCell(label19);
        Label label20 = new Label(20, 1, "# SHARKS AT OVERHANG (MP)");
        sheet.addCell(label20);
        Label label21 = new Label(21, 1, "COMMENTS");
        sheet.addCell(label21);

        // Excel export =========================================================
        int count = 1; // there is a blank row on top
        Label infoLabel  = new Label(2, 0, "No of Sharks Filtered\n(counted by exporter)");
        sheet.addCell(infoLabel);
        Label infoLabel2 = new Label(3, 0, Integer.toString(numMatchingEncounters));
        sheet.addCell(infoLabel2);

        String value = "";
         for(int i=0;i<numMatchingEncounters;i++){
            Encounter enc=(Encounter)rEncounters.get(i);
            // count is the current row number
            count++;
            numResults++;

            // COLUMN BY COLUMN

            // find if this is the first encounter of the individual
            value = enc.getIndividualID();
            if (value!=null && !value.equals("") && value != "Unassigned") {
              MarkedIndividual indie = myShepherd.getMarkedIndividual(enc.getIndividualID());
              if (indie!=null) {
                Encounter[] sorted = indie.getDateSortedEncounters();
                if (sorted.length>0) {
                  value = "";
                  boolean isFirstEnc = (sorted[0].equals(indie));
                  if (isFirstEnc) {value="New";}
                  else {value="Exists";}
                  Label lUnique = new Label(0, count, value);
                  sheet.addCell(lUnique);
                }
              }

              Label lIndiv = new Label(1, count, enc.getIndividualID());
              sheet.addCell(lIndiv);
            }

            value = enc.getCatalogNumber();
            if (value!=null && !value.equals("")) {
              Label lCatalog = new Label(2,count,value);
              sheet.addCell(lCatalog);
            }

            value = enc.getLocationID();
            if (value!=null && !value.equals("")) {
              Label lLocation = new Label(3, count, value.substring(0,2));
              sheet.addCell(lLocation);
            }

            int year = enc.getYear();
            if (year > 2000) {
              year-=2000;
            }
            Label lYear = new Label(4, count, Integer.toString(year));
            sheet.addCell(lYear);

            int month = enc.getMonth();
            if (month > -1) {
              String monthStr = "";
              switch (month) {
                case 1:
                  monthStr = "JAN";
                  break;
                case 2:
                  monthStr = "FEB";
                  break;
                case 3:
                  monthStr = "MAR";
                  break;
                case 4:
                  monthStr = "APR";
                  break;
                case 5:
                  monthStr = "MAY";
                  break;
                case 6:
                  monthStr = "JUN";
                  break;
                case 7:
                  monthStr = "JUL";
                  break;
                case 8:
                  monthStr = "AUG";
                  break;
                case 9:
                  monthStr = "SEP";
                  break;
                case 10:
                  monthStr = "OCT";
                  break;
                case 11:
                  monthStr = "NOV";
                  break;
                case 12:
                  monthStr = "DEC";
                  break;
                default:
                  monthStr = Integer.toString(month);
              }
              Label lMonth = new Label(5, count, monthStr);
              sheet.addCell(lMonth);
            }

            Label lSex = new Label(6, count, enc.getSex());
            sheet.addCell(lSex);

            Label lFlank = new Label(7, count, enc.getDynamicPropertyValue("flank"));
            sheet.addCell(lFlank);

            Label lPhotog = new Label(8, count, enc.getPhotographerName());
            sheet.addCell(lPhotog);

            Label lMigration = new Label(10, count, enc.getDynamicPropertyValue("Migration"));
            sheet.addCell(lMigration);

            Label lHook = new Label(11, count, enc.getDynamicPropertyValue("Hookmark"));
            sheet.addCell(lHook);

            Label lLaser = new Label(12, count, enc.getDynamicPropertyValue("Laser"));
            sheet.addCell(lLaser);


            Measurement pcLength = enc.getMeasurement("precaudallength");
            if (pcLength!=null) {
              Label lPCLength = new Label(13, count, Double.toString(pcLength.getValue()));
              sheet.addCell(lPCLength);
            }

            Double size = enc.getSizeAsDouble();
            if (size!=null) {
              Label lSize = new Label(14, count, Double.toString(size));
              sheet.addCell(lSize);
            }

            value = enc.getLifeStage();
            if (value!=null && !value.equals("")) {
              String age = "";
              switch (value) {
                case "adult": age = "a";
                case "juvenile": age = "j";
                case "sub-adult": age = "s";
              }
              Label lAge = new Label(15, count, age);
              sheet.addCell(lAge);
            }

            Measurement temperature = enc.getMeasurement("Temp.");
            if (temperature!=null) {
              Label lTemp = new Label(16, count, Double.toString(temperature.getValue()));
              sheet.addCell(lTemp);
            }

            Label lLat = new Label(17, count, enc.getDecimalLatitude());
            sheet.addCell(lLat);

            Label lLong = new Label(18, count, enc.getDecimalLongitude());
            sheet.addCell(lLong);

            Label lCave = new Label(19, count, enc.getDynamicPropertyValue("# sharks in cave"));
            sheet.addCell(lCave);

            Label lOverhang = new Label(20, count, enc.getDynamicPropertyValue("# sharks at overhang"));
            sheet.addCell(lOverhang);

            Label lComments = new Label(21, 0, enc.getComments());
            sheet.addCell(lComments);
         } //end for loop iterating encounters

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
        out.println("<p>Please let the webmaster know you encountered an error at: EncounterSearchExportExcelFile servlet</p></body></html>");
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
        out.println("<p>Please let the webmaster know you encountered an error at: EncounterSearchExportExcelFile servlet</p></body></html>");
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
