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
    myShepherd.setAction("EncounterSearchExportExcelFile.class");
    

    
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
        WritableSheet sheet = workbookOBIS.createSheet("Search Results", 0);
        Label label0 = new Label(0, 0, "Date Last Modified");
        sheet.addCell(label0);
        Label label1 = new Label(1, 0, "Institution Code");
        sheet.addCell(label1);
        Label label2 = new Label(2, 0, "Collection Code");
        sheet.addCell(label2);
        Label label2a = new Label(3, 0, "Catalog Number");
        sheet.addCell(label2a);
        Label label3 = new Label(4, 0, "Record URL");
        sheet.addCell(label3);
        Label label5 = new Label(5, 0, "Scientific Name");
        sheet.addCell(label5);
        Label label6 = new Label(6, 0, "Basis of record");
        sheet.addCell(label6);
        Label label7 = new Label(7, 0, "Citation");
        sheet.addCell(label7);
        Label label8 = new Label(8, 0, "Kingdom");
        sheet.addCell(label8);
        Label label9 = new Label(9, 0, "Phylum");
        sheet.addCell(label9);
        Label label10 = new Label(10, 0, "Class");
        sheet.addCell(label10);
        Label label11 = new Label(11, 0, "Order");
        sheet.addCell(label11);
        Label label12 = new Label(12, 0, "Family");
        sheet.addCell(label12);
        Label label13 = new Label(13, 0, "Genus");
        sheet.addCell(label13);
        Label label14 = new Label(14, 0, "species");
        sheet.addCell(label14);
        Label label15 = new Label(15, 0, "Year Identified");
        sheet.addCell(label15);
        Label label16 = new Label(16, 0, "Month Identified");
        sheet.addCell(label16);
        Label label17 = new Label(17, 0, "Day Identified");
        sheet.addCell(label17);
        Label label18 = new Label(18, 0, "Year Collected");
        sheet.addCell(label18);
        Label label19 = new Label(19, 0, "Month Collected");
        sheet.addCell(label19);
        Label label20 = new Label(20, 0, "Day Collected");
        sheet.addCell(label20);
        Label label21 = new Label(21, 0, "Time of Day");
        sheet.addCell(label21);
        Label label22 = new Label(22, 0, "Locality");
        sheet.addCell(label22);
        Label label23 = new Label(23, 0, "Longitude");
        sheet.addCell(label23);
        Label label24 = new Label(24, 0, "Latitude");
        sheet.addCell(label24);
        Label label25 = new Label(25, 0, "Sex");
        sheet.addCell(label25);
        Label label26 = new Label(26, 0, "Notes");
        sheet.addCell(label26);
        Label label27 = new Label(27, 0, "Length (m)");
        sheet.addCell(label27);
        Label label28 = new Label(28, 0, "Marked Individual");
        sheet.addCell(label28);
        Label label29 = new Label(29, 0, "Location ID");
        sheet.addCell(label29);
        Label label30 = new Label(30, 0, "Submitter Email Address");
        sheet.addCell(label30);
        
        // Excel export =========================================================
        int count = 0;

         for(int i=0;i<numMatchingEncounters;i++){
            Encounter enc=(Encounter)rEncounters.get(i);
            count++;
            numResults++;
            
            //OBIS formt export
            Label lNumber = new Label(0, count, enc.getDWCDateLastModified());
            sheet.addCell(lNumber);
            Label lNumberx1 = new Label(1, count, CommonConfiguration.getProperty("institutionCode",context));
            sheet.addCell(lNumberx1);
            Label lNumberx2 = new Label(2, count, CommonConfiguration.getProperty("catalogCode",context));
            sheet.addCell(lNumberx2);
            Label lNumberx3 = new Label(3, count, enc.getEncounterNumber());
            sheet.addCell(lNumberx3);
            Label lNumberx4 = new Label(4, count, ("http://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" + enc.getEncounterNumber()));
            sheet.addCell(lNumberx4);
            
            if((enc.getGenus()!=null)&&(enc.getSpecificEpithet()!=null)){
              Label lNumberx5 = new Label(5, count, (enc.getGenus() + " " + enc.getSpecificEpithet()));
              sheet.addCell(lNumberx5);
            }
            else if(CommonConfiguration.getProperty("genusSpecies0",context)!=null){
              Label lNumberx5 = new Label(5, count, (CommonConfiguration.getProperty("genusSpecies0",context)));
              sheet.addCell(lNumberx5);
            }
            
            
            
            Label lNumberx6 = new Label(6, count, "P");
            sheet.addCell(lNumberx6);
            Calendar toDay = Calendar.getInstance();
            int year = toDay.get(Calendar.YEAR);
            Label lNumberx7 = new Label(7, count, CommonConfiguration.getProperty("citation",context));
            sheet.addCell(lNumberx7);
            Label lNumberx8 = new Label(8, count, CommonConfiguration.getProperty("kingdom",context));
            sheet.addCell(lNumberx8);
            Label lNumberx9 = new Label(9, count, CommonConfiguration.getProperty("phylum",context));
            sheet.addCell(lNumberx9);
            Label lNumberx10 = new Label(10, count, CommonConfiguration.getProperty("class",context));
            sheet.addCell(lNumberx10);
            Label lNumberx11 = new Label(11, count, CommonConfiguration.getProperty("order",context));
            sheet.addCell(lNumberx11);
            Label lNumberx13 = new Label(12, count, CommonConfiguration.getProperty("family",context));
            sheet.addCell(lNumberx13);
            
              if((enc.getGenus()!=null)&&(enc.getSpecificEpithet()!=null)){
                Label lNumberx14 = new Label(13, count, enc.getGenus());
                sheet.addCell(lNumberx14);
                Label lNumberx15 = new Label(14, count, enc.getSpecificEpithet());
                sheet.addCell(lNumberx15);
              }
              else if(CommonConfiguration.getProperty("genusSpecies0",context)!=null){
                StringTokenizer str=new StringTokenizer(CommonConfiguration.getProperty("genusSpecies0",context)," ");
                if(str.countTokens()>1){
                  Label lNumberx14 = new Label(13, count, str.nextToken());
                  sheet.addCell(lNumberx14);
                  Label lNumberx15 = new Label(14, count, str.nextToken());
                  sheet.addCell(lNumberx15);
                }
              }
            
            if (enc.getYear() > 0) {
              Label lNumberx16 = new Label(15, count, Integer.toString(enc.getYear()));
              sheet.addCell(lNumberx16);
              Label lNumberx19 = new Label(18, count, Integer.toString(enc.getYear()));
              sheet.addCell(lNumberx19);
            }
            if (enc.getMonth() > 0) {
              Label lNumberx17 = new Label(16, count, Integer.toString(enc.getMonth()));
              sheet.addCell(lNumberx17);
              Label lNumberx20 = new Label(19, count, Integer.toString(enc.getMonth()));
              sheet.addCell(lNumberx20);
            }
            if (enc.getDay() > 0) {
              Label lNumberx18 = new Label(17, count, Integer.toString(enc.getDay()));
              sheet.addCell(lNumberx18);
              Label lNumberx21 = new Label(20, count, Integer.toString(enc.getDay()));
              sheet.addCell(lNumberx21);
            }
            
            if(enc.getHour()>-1){
              Label lNumberx22 = new Label(21, count, (enc.getHour() + ":" + enc.getMinutes()));
              sheet.addCell(lNumberx22);
            }
            
            Label lNumberx23 = new Label(22, count, enc.getLocation());
            sheet.addCell(lNumberx23);
            if ((enc.getDWCDecimalLatitude() != null) && (enc.getDWCDecimalLongitude() != null)) {
              Label lNumberx24 = new Label(23, count, enc.getDWCDecimalLongitude());
              sheet.addCell(lNumberx24);
              Label lNumberx25 = new Label(24, count, enc.getDWCDecimalLatitude());
              sheet.addCell(lNumberx25);
            }
            //check for available locale coordinates
            //this functionality is primarily used for data export to iobis.org
            else if ((enc.getLocationCode() != null) && (!enc.getLocationCode().equals(""))) {
              try {
                String lc = enc.getLocationCode();
                if (props.getProperty(lc) != null) {
                  String gps = props.getProperty(lc);
                  StringTokenizer st = new StringTokenizer(gps, ",");
                  Label lNumberx25 = new Label(24, count, st.nextToken());
                  sheet.addCell(lNumberx25);
                  Label lNumberx24 = new Label(23, count, st.nextToken());
                  sheet.addCell(lNumberx24);
                }
              } catch (Exception e) {
                e.printStackTrace();
                System.out.println("     I hit an error getting locales in searchResults.jsp.");
              }
            }
            if ((enc.getSex()!=null)&&(!enc.getSex().equals("unknown"))) {
              Label lSex = new Label(25, count, enc.getSex());
              sheet.addCell(lSex);
            }
            if(enc.getComments()!=null){
              Label lNumberx26 = new Label(26, count, enc.getComments().replaceAll("<br>", ". ").replaceAll("\n", "").replaceAll("\r", ""));
              sheet.addCell(lNumberx26);
            }
            if(enc.getSizeAsDouble()!=null){
              Label lNumberx27 = new Label(27, count, enc.getSizeAsDouble().toString());
              sheet.addCell(lNumberx27);
            }
            if (enc.getIndividualID()!=null) {
              Label lNumberx28 = new Label(28, count, enc.getIndividualID());
              sheet.addCell(lNumberx28);
            }
            if (enc.getLocationCode() != null) {
              Label lNumberx29 = new Label(29, count, enc.getLocationCode());
              sheet.addCell(lNumberx29);
            }
            if (enc.getSubmitterEmail() != null) {
                Label lNumberx30 = new Label(30, count, enc.getSubmitterEmail());
                sheet.addCell(lNumberx30);
            }

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
