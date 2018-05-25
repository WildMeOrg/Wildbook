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
import java.text.SimpleDateFormat;

import jxl.write.*;
import jxl.Workbook;


public class IndividualSearchExportAllData extends HttpServlet{

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

    Vector rIndividuals = new Vector();
    int numResults = 0;

    //set up the files
    String filename = "individualSearchResults_export_" + request.getRemoteUser() + ".xls";

    //setup data dir
    String rootWebappPath = getServletContext().getRealPath("/");
    File webappsDir = new File(rootWebappPath).getParentFile();
    File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));
    if(!shepherdDataDir.exists()){shepherdDataDir.mkdirs();}
    File individualsDir=new File(shepherdDataDir.getAbsolutePath()+"/individuals");
    if(!individualsDir.exists()) {
        individualsDir.mkdirs();
    }

    File excelFile = new File(individualsDir.getAbsolutePath()+"/"+ filename);

    myShepherd.beginDBTransaction();

    try {

      //set up the output stream
      FileOutputStream fos = new FileOutputStream(excelFile);
      OutputStreamWriter outp = new OutputStreamWriter(fos);

      try{

        MarkedIndividualQueryResult queryResult = IndividualQueryProcessor.processQuery(myShepherd, request, "dateFirstIdentified descending");
        rIndividuals = queryResult.getResult();

        int numMatchingIndividuals=rIndividuals.size();

        //load the optional locales
        Properties props = new Properties();
        try {
          props=ShepherdProperties.getProperties("locationIDGPS.properties", "",context);
        } catch (Exception e) {
          System.out.println("     Could not load locales.properties IndividualSearchExportAllData.");
          e.printStackTrace();
        }

      //let's set up some cell formats
        //WritableCellFormat floatFormat = new WritableCellFormat(NumberFormats.FLOAT);
        //WritableCellFormat integerFormat = new WritableCellFormat(NumberFormats.INTEGER);
        WritableWorkbook workbook = Workbook.createWorkbook(excelFile);
        WritableSheet sheet = workbook.createSheet("Individuals", 0);
        Label label0 = new Label(0, 0, "individualID");
        sheet.addCell(label0);
        Label label1 = new Label(1, 0, "alternateid");
        sheet.addCell(label1);
        Label label2 = new Label(2, 0, "comments");
        sheet.addCell(label2);
        Label label2a = new Label(3, 0, "sex");
        sheet.addCell(label2a);
        Label label3 = new Label(4, 0, "genus");
        sheet.addCell(label3);
        Label label5 = new Label(5, 0, "specificEpithet");
        sheet.addCell(label5);
        Label label6 = new Label(6, 0, "nickName");
        sheet.addCell(label6);
        Label label7 = new Label(7, 0, "numberOfEncounters");
        sheet.addCell(label7);
        Label label8 = new Label(8, 0, "numberAdditionalDataFiles");
        sheet.addCell(label8);
        Label label9 = new Label(9, 0, "numberLocations");
        sheet.addCell(label9);
        Label label10 = new Label(10, 0, "dateFirstIdentified");
        sheet.addCell(label10);
        Label label11 = new Label(11, 0, "dateTimeCreated");
        sheet.addCell(label11);
        Label label12 = new Label(12, 0, "dateTimeLatestSighting");
        sheet.addCell(label12);
        Label label13 = new Label(13, 0, "dynamicProperties");
        sheet.addCell(label13);
        Label label14 = new Label(14, 0, "patterningCode");
        sheet.addCell(label14);
        Label label15 = new Label(15, 0, "maxYearsBetweenResightings");
        sheet.addCell(label15);
        Label label16 = new Label(16, 0, "timeOfBirth");
        sheet.addCell(label16);
        Label label17 = new Label(17, 0, "timeOfDeath");
        sheet.addCell(label17);

        String value = "";
        int count = 0;
         for(int i=0;i<numMatchingIndividuals;i++){
            count++;
            numResults++;

            MarkedIndividual mi = (MarkedIndividual) rIndividuals.get(i);
            value = mi.getIndividualID();
            if (value!=null && !value.equals("")) {
              Label indyIDLabel = new Label(0, count, mi.getIndividualID());
              sheet.addCell(indyIDLabel);
            }

            value = mi.getAlternateID();
            if (value!=null && !value.equals("")) {
              Label altID = new Label(1,count,value);
              sheet.addCell(altID);
            }

            value = mi.getComments();
            if (value!=null && !value.equals("")) {
              Label comments = new Label(2, count, value);
              sheet.addCell(comments);
            }

            value = mi.getSex();
            if (value!=null && !value.equals("")) {
                Label sex = new Label(3, count, value);
                sheet.addCell(sex);  
            }

            value = mi.getGenus();
            if (value!=null && !value.equals("")) {
                Label genus = new Label(4, count, value);
                sheet.addCell(genus);  
            }

            value = mi.getSpecificEpithet();
            if (value!=null && !value.equals("")) {
                Label specEp = new Label(5, count, value);
                sheet.addCell(specEp);  
            }

            value = mi.getNickName();
            if (value!=null && !value.equals("")) {
                Label nickName = new Label(6, count, value);
                sheet.addCell(nickName);  
            }

            int numEnc = mi.totalEncounters();
            if (value!=null && !value.equals("")) {
                Label encs = new Label(7, count, String.valueOf(numEnc));
                sheet.addCell(encs);  
            }

            int numData = mi.getDataFiles().size();
            Label numDataLabel = new Label(8, count, String.valueOf(numData));
            sheet.addCell(numDataLabel);

            int numLocs = mi.getNumberLocations();
            Label numLocLabel = new Label(9, count, String.valueOf(numLocs));
            sheet.addCell(numLocLabel);

            value = mi.getDateFirstIdentified();
            if (value!=null && !value.equals("")) {
                Label dayID = new Label(10, count, value);
                sheet.addCell(dayID);  
            }

            value = mi.getDateTimeCreated();
            if (value!=null && !value.equals("")) {
                Label dayMade = new Label(11, count, value);
                sheet.addCell(dayMade);  
            }

            value = mi.getDateLatestSighting();
            if (value!=null && !value.equals("")) {
                Label lastSight = new Label(12, count, value);
                sheet.addCell(lastSight);  
            }

            value = mi.getDynamicProperties();
            if (value!=null && !value.equals("")) {
                Label dynProps = new Label(13, count, value);
                sheet.addCell(dynProps);  
            }

            value = mi.getPatterningCode();
            if (value!=null && !value.equals("")) {
                Label pCode = new Label(14, count, value);
                sheet.addCell(pCode);  
            }

            int maxInterval = mi.getMaxNumYearsBetweenSightings();
            Label maxIntLabel = new Label(15, count, String.valueOf(maxInterval));
            sheet.addCell(maxIntLabel);

            Date epoch = new Date();

            long birth = mi.getTimeOfBirth();
            String bString = "";
            if (epoch.getTime()<birth) {
              bString = milliToDate(birth);
            }
            Label birthLabel = new Label(16, count, bString);
            sheet.addCell(birthLabel);

            long death = mi.getTimeofDeath();
            String dString = "";
            if (epoch.getTime()<death) {
              dString = milliToDate(death);
            }
            Label deathLabel = new Label(17, count, dString);
            sheet.addCell(deathLabel);

         } //end for loop iterating indys

         workbook.write();
         workbook.close();


        outp.close();
        outp=null;

      }
      catch(Exception ioe){
        ioe.printStackTrace();
        response.setContentType("text/html");
        PrintWriter out = response.getWriter();
        out.println(ServletUtilities.getHeader(request));
        out.println("<html><body><p><strong>Error encountered</strong> with file writing. Check the relevant log.</p>");
        out.println("<p>Please let the webmaster know you encountered an error at: IndividualSearchExportAllData servlet</p></body></html>");
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
        out.println("<p>Please let the webmaster know you encountered an error at: IndividualSearchExportAllData servlet</p></body></html>");
        out.println(ServletUtilities.getFooter(context));
        out.close();
    }

    myShepherd.rollbackDBTransaction();
    myShepherd.closeDBTransaction();

      //now write out the file
      response.setContentType("application/msexcel");
      response.setHeader("Content-Disposition","attachment;filename="+filename);
      ServletContext ctx = getServletContext();
      //InputStream is = ctx.getResourceAsStream("/individuals/"+filename);
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

    private String milliToDate(long millis) {
        String date = "";
        Date dt = new Date(millis);
        SimpleDateFormat df = new SimpleDateFormat("MM-dd-yyyy");
        date = df.format(dt);
        return date;
    }

  }
