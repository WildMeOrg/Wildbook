package org.ecocean.servlet.export;

import javax.servlet.*;
import javax.servlet.http.*;

import java.io.*;
import java.util.*;

import org.ecocean.*;
import org.ecocean.genetics.BiologicalMeasurement;
import org.ecocean.servlet.ServletUtilities;
import org.springframework.mock.web.MockHttpServletRequest;

import jxl.write.*;
import jxl.*;

import org.ecocean.Util.MeasurementDesc;


//adds spots to a new encounter
public class SOCPROGExport extends HttpServlet{
  
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
    myShepherd.setAction("SOCPROGExport.class");
    

    //set up the files
    String filename = "SOCPROGExport_" + request.getRemoteUser() + ".xls";
    
    //setup data dir
    String rootWebappPath = getServletContext().getRealPath("/");
    File webappsDir = new File(rootWebappPath).getParentFile();
    File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));
    if(!shepherdDataDir.exists()){shepherdDataDir.mkdirs();}
    File encountersDir=new File(shepherdDataDir.getAbsolutePath()+"/encounters");
    if(!encountersDir.exists()){encountersDir.mkdirs();}
    File excelFile = new File(encountersDir.getAbsolutePath()+"/"+ filename);

    int numPopulations=2;

    myShepherd.beginDBTransaction();
    
    
    try {
      
    //set up the vector for matching encounters
      Vector query1Individuals = new Vector();
      Vector query2Individuals = new Vector();

      //kick off the transaction
      myShepherd.beginDBTransaction();

      //start the query and get the results
      String order = "";
      //HttpServletRequest request1=(MockHttpServletRequest)request.getSession().getAttribute("locationSearch1");
    
      if(request!=null){
    
        //MarkedIndividualQueryResult queryResult1 = IndividualQueryProcessor.processQuery(myShepherd, request1, order);
        //System.out.println(((MockHttpServletRequest)session.getAttribute("locationSearch1")).getQueryString());
        //query1Individuals = queryResult1.getResult();
        //int numSearch1Individuals = query1Individuals.size();
        
        MarkedIndividualQueryResult queryResult2 = IndividualQueryProcessor.processQuery(myShepherd, request, order);
        query2Individuals = queryResult2.getResult();
        int numSearch2Individuals = query2Individuals.size();
      
      //set up the output stream
      FileOutputStream fos = new FileOutputStream(excelFile);
      OutputStreamWriter outp = new OutputStreamWriter(fos);
      
      try{

       //business logic start here
        
        //load the optional locales
        Properties props = new Properties();
        try {
          props=ShepherdProperties.getProperties("locationIDGPS.properties", "",context);
        } catch (Exception e) {
          //System.out.println("     Could not load locationIDGPS.properties in class GenalexExportCodominantMSDataBySize.");
          e.printStackTrace();
        }
        
      //let's set up some cell formats
        WritableCellFormat floatFormat = new WritableCellFormat(NumberFormats.FLOAT);
        WritableCellFormat integerFormat = new WritableCellFormat(NumberFormats.INTEGER);

      //let's write out headers for the OBIS export file
        WritableWorkbook workbookOBIS = Workbook.createWorkbook(excelFile);
        WritableSheet sheet = workbookOBIS.createSheet("Wildbook SOCPROG Data Export", 0);
        WritableSheet sheet2 = workbookOBIS.createSheet("Additional data", 1);


        
        Label indieLabel = new Label(0, 0, "Date");
        sheet.addCell(indieLabel);
        
        
        
        Label popLabel = new Label(1, 0, "Lat");
        sheet.addCell(popLabel);

        Label popLabel2 = new Label(2, 0, "Long");
        sheet.addCell(popLabel2);
        
        Label popLabel3 = new Label(3, 0, "ElevationOrDepth");
        sheet.addCell(popLabel3);
        
        Label popLabel3a = new Label(4, 0, "LocationID");
        sheet.addCell(popLabel3a);

        Label popLabel4 = new Label(5, 0, "ID");
        sheet.addCell(popLabel4);
        
        
        //sheet 2 entries
        Label popLabel4a = new Label(0, 0, "ID");
        sheet2.addCell(popLabel4a);
        
        Label popLabel7 = new Label(1, 0, "OccurrenceID");
        sheet2.addCell(popLabel7);
        
        Label popLabel7a = new Label(2, 0, "SocialUnit");
        sheet2.addCell(popLabel7a);
        
        Label popLabel5 = new Label(3, 0, "Sex");
        sheet2.addCell(popLabel5);
        
        Label popLabel6 = new Label(4, 0, "Behavior");
        sheet2.addCell(popLabel6);
        
        Label popLabel8 = new Label(5, 0, "Haplotype");
        sheet2.addCell(popLabel8);
        
        Label popLabel9 = new Label(6, 0, "RecaptureStatus");
        sheet2.addCell(popLabel9);
        
        List<MeasurementDesc> measurementTypes=Util.findMeasurementDescs("en",context);
        int numMeasurementTypes=measurementTypes.size();
        for(int j=0;j<numMeasurementTypes;j++){
          String measureName=measurementTypes.get(j).getType();
          Label popLabelX = new Label((j+7), 0, measureName);
          sheet2.addCell(popLabelX);
        }
         
        List<MeasurementDesc> bioMeasurementTypes=Util.findBiologicalMeasurementDescs("en",context);
        int numBioMeasurementTypes=bioMeasurementTypes.size();
        for(int j=0;j<numBioMeasurementTypes;j++){
          String measureName=bioMeasurementTypes.get(j).getType();
          Label popLabelX = new Label((j+7+numMeasurementTypes), 0, measureName);
          sheet2.addCell(popLabelX);
        }
        
        
        DateFormat customDateFormat = new DateFormat ("MM/dd/yy hh:mm");
        WritableCellFormat dateFormat = new WritableCellFormat (customDateFormat);
        
        WritableCellFormat numbersFormat=new WritableCellFormat(new  jxl.write.NumberFormat("#.#####"));
        numbersFormat.setShrinkToFit(true);
        
        //later, we might ant to add columns for Lat and Long
       
         int count = 0;

           
            Vector iterateMe=query2Individuals;
            
            
            for(int k=0;k<iterateMe.size();k++){
              
              MarkedIndividual indy=(MarkedIndividual)iterateMe.get(k);
              //System.out.println("          Individual: "+indy.getIndividualID());
              Vector encs=indy.getEncounters();
              int numEncs=encs.size();
              for(int j=0;j<numEncs;j++){
                  Encounter enc=(Encounter)encs.get(j);
                  if((enc.getLocationID()!=null)||((enc.getLongitudeAsDouble()!=null)&&(enc.getLatitudeAsDouble()!=null))){
                    
                    if((enc.getDateInMilliseconds()!=null)&&(enc.getDateInMilliseconds()>0)){
                      
                    count++;
                    
                    GregorianCalendar localCalendar = new GregorianCalendar();
                    localCalendar.setTimeInMillis(enc.getDateInMilliseconds());
                    
                    
                    //jxl.write.DateTime encLabel = new jxl.write.DateTime(0, count, enc.getDate().replaceAll("-", "/"));
                    jxl.write.DateTime encLabel = new jxl.write.DateTime(0, count, localCalendar.getTime(),dateFormat);
                    sheet.addCell(encLabel);
                    
                    
                    if((enc.getLongitudeAsDouble()!=null)&&(enc.getLatitudeAsDouble()!=null)){
                      jxl.write.Number popLabel1a = new jxl.write.Number(1, count, enc.getLatitudeAsDouble(),numbersFormat);
                      sheet.addCell(popLabel1a);

                    
                      jxl.write.Number popLabel2a = new jxl.write.Number(2, count, enc.getLongitudeAsDouble(),numbersFormat);
                      sheet.addCell(popLabel2a);
                    }
                    else{
                      
                      jxl.write.Label popLabel1a = new jxl.write.Label(1, count, "NaN");
                      sheet.addCell(popLabel1a);

                    
                      jxl.write.Label popLabel2a = new jxl.write.Label(2, count, "NaN");
                      sheet.addCell(popLabel2a);
                      
                    }
                    
                    if((enc.getMaximumDepthInMeters()!=null)||(enc.getMaximumElevationInMeters()!=null)){
                      if(enc.getMaximumDepthInMeters()!=null){
                        jxl.write.Number popLabel3c = new jxl.write.Number(3, count, enc.getMaximumDepthInMeters(),numbersFormat);
                        sheet.addCell(popLabel3c);
                      }
                      else{
                        jxl.write.Number popLabel3c = new jxl.write.Number(3, count, enc.getMaximumElevationInMeters(),numbersFormat);
                        sheet.addCell(popLabel3c);
                      }
                    }
                    else{
                      
                      jxl.write.Label popLabel3c = new jxl.write.Label(3, count, "NaN");
                      sheet.addCell(popLabel3c);
                      
                    }
                    
                    
                    if(enc.getLocationID()!=null){
                      Label popLabel3d = new Label(4, count, enc.getLocationID());
                      sheet.addCell(popLabel3d);
                    }
                    
                    
                    //
                    if(enc.getIndividualID()!=null){
                      Label popLabel4a1 = new Label(5, count, enc.getIndividualID().replaceAll("[^a-zA-Z0-9]", ""));
                      sheet.addCell(popLabel4a1);
                      
                      Label popLabel4a2 = new Label(0, count, enc.getIndividualID());
                      sheet2.addCell(popLabel4a2);
                      
                    }
                    
                    
                    if(myShepherd.getOccurrenceForEncounter(enc.getCatalogNumber())!=null){
                      Occurrence oc=myShepherd.getOccurrenceForEncounter(enc.getCatalogNumber());
                      Label popLabel7b = new Label(1, count, oc.getOccurrenceID());
                      sheet2.addCell(popLabel7b);
                      
                    }
                    
                    List<String> mySocialUnits=myShepherd.getAllSocialUnitsForMarkedIndividual(indy.getIndividualID());
                    if(mySocialUnits.size()>0){
                      Label popLabel7c = new Label(2, count, mySocialUnits.get(0));
                      sheet2.addCell(popLabel7c);
                    }
                    
                    if(enc.getSex()!=null){
                      Label popLabel5a = new Label(3, count, enc.getSex());
                      sheet2.addCell(popLabel5a);
                    }
                    
                    
                    if(enc.getBehavior()!=null){
                      Label popLabel6a = new Label(4, count, enc.getBehavior());
                      sheet2.addCell(popLabel6a);
                    }
                    
                    
                    if(enc.getHaplotype()!=null){
                      Label popLabel8a = new Label(5, count, enc.getHaplotype());
                      sheet2.addCell(popLabel8a);
                    }
                    
                      String cmrStatus="Resight";
                      if(indy.getDateSortedEncounters(true)[0].getCatalogNumber().equals(enc.getCatalogNumber())){
                        cmrStatus = "New";
                      }
                      Label popLabel9a = new Label(6, count, cmrStatus);
                      sheet2.addCell(popLabel9a);
                    
                      
                      for(int m=0;m<numMeasurementTypes;m++){
                        String measureName=measurementTypes.get(m).getType();
                        if((enc.hasMeasurement(measureName))&&(enc.getMeasurement(measureName)!=null)){
                          Measurement mmnt=enc.getMeasurement(measureName);
                          jxl.write.Number popLabelX = new jxl.write.Number((m+7), count, mmnt.getValue(),numbersFormat);
                          sheet2.addCell(popLabelX);
                        }
                      }
                       
                      for(int m=0;m<numBioMeasurementTypes;m++){
                        String measureName=bioMeasurementTypes.get(m).getType();
                        if(enc.hasBiologicalMeasurement(measureName)){
                          BiologicalMeasurement bm=enc.getBiologicalMeasurement(measureName);
                          if((bm!=null)&&(bm.getValue()!=null)){
                            jxl.write.Number popLabelX = new jxl.write.Number((m+7+numMeasurementTypes), count, bm.getValue(),numbersFormat);
                            sheet2.addCell(popLabelX);
                          }
                        }
                      }
                    
                    
                  }
                    
                  }

              }
                         
             
            }
            
            
           
         workbookOBIS.write();
         workbookOBIS.close();
         
            

      // end Excel export =========================================================

        
        outp.close();
        outp=null;
        
      }
      catch(Exception ioe){
        ioe.printStackTrace();
        response.setContentType("text/html");
        PrintWriter out = response.getWriter();
        out.println(ServletUtilities.getHeader(request));
        out.println("<html><body><p><strong>Error encountered</strong> with file writing. Check the relevant log.</p>");
        out.println("<p>Please let the webmaster know you encountered an error at: SOCPROGExport servlet</p></body></html>");
        out.println(ServletUtilities.getFooter(context));
        out.close();
        outp.close();
        outp=null;
      }
      
    }
    }
    catch(Exception e) {
      e.printStackTrace();
      response.setContentType("text/html");
      PrintWriter out = response.getWriter();
      out.println(ServletUtilities.getHeader(request));  
      out.println("<html><body><p><strong>Error encountered</strong></p>");
        out.println("<p>Please let the webmaster know you encountered an error at: SOCPROGExport servlet</p></body></html>");
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