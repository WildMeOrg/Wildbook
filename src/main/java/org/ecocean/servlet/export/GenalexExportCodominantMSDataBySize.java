package org.ecocean.servlet.export;
import javax.servlet.*;
import javax.servlet.http.*;

import java.io.*;
import java.util.*;

import org.ecocean.*;
import org.ecocean.servlet.ServletUtilities;
import org.springframework.mock.web.MockHttpServletRequest;

import jxl.write.*;
import jxl.Workbook;


//adds spots to a new encounter
public class GenalexExportCodominantMSDataBySize extends HttpServlet{
  
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
    myShepherd.setAction("GenalexExportCodominantMSDataBySize.class");
    
    //in case we're doing haplotype export
    List<String> haplos=myShepherd.getAllHaplotypes();
    int numHaplos=haplos.size();
 
    
    //set up the files
    String filename = "genalexExportMSData_" + request.getRemoteUser() + ".xls";
    
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
      HttpServletRequest request1=(MockHttpServletRequest)request.getSession().getAttribute("locationSearch1");
    
      if((request!=null)&&(request1!=null)){
    
        MarkedIndividualQueryResult queryResult1 = IndividualQueryProcessor.processQuery(myShepherd, request1, order);
        //System.out.println(((MockHttpServletRequest)session.getAttribute("locationSearch1")).getQueryString());
        query1Individuals = queryResult1.getResult();
        int numSearch1Individuals = query1Individuals.size();
        
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
          //props.load(getClass().getResourceAsStream("/bundles/locales.properties"));
          props=ShepherdProperties.getProperties("locationIDGPS.properties", "",context);
          
        } catch (Exception e) {
          System.out.println("     Could not load locationIDGPS.properties in class GenalexExportCodominantMSDataBySize.");
          e.printStackTrace();
        }
        
      //let's set up some cell formats
        WritableCellFormat floatFormat = new WritableCellFormat(NumberFormats.FLOAT);
        WritableCellFormat integerFormat = new WritableCellFormat(NumberFormats.INTEGER);

      //let's write out headers for the OBIS export file
        WritableWorkbook workbookOBIS = Workbook.createWorkbook(excelFile);
        WritableSheet sheet = workbookOBIS.createSheet("Shepherd Project GenAlEx Export Microsatellite Data", 0);
        if(request.getParameter("exportHaplos")!=null){
          Label label0 = new Label(0, 0, "1");
          sheet.addCell(label0);
        }
        else{
          Label label0 = new Label(0, 0, (new Integer(myShepherd.getAllLoci().size())).toString());
          sheet.addCell(label0);
        }
        Label label1 = new Label(1, 0, (new Integer((numSearch1Individuals+numSearch2Individuals))).toString());
        sheet.addCell(label1);
        Label label2 = new Label(2, 0, (new Integer(numPopulations)).toString());
        sheet.addCell(label2);
        Label label2a = new Label(3, 0, (new Integer(numSearch1Individuals)).toString());
        sheet.addCell(label2a);
        Label label3 = new Label(4, 0, (new Integer(numSearch2Individuals)).toString());
        sheet.addCell(label3);
        
        Label label4 = new Label(0, 1, "Individual Search Comparison");
        sheet.addCell(label4);
        Label label5 = new Label(3, 1, "Search 1:"+queryResult1.getQueryPrettyPrint());
        sheet.addCell(label5);
        Label label6 = new Label(4, 1, "Search 2:"+queryResult2.getQueryPrettyPrint());
        sheet.addCell(label6);
        
        Label indieLabel = new Label(0, 2, "Individual ID");
        sheet.addCell(indieLabel);
        Label popLabel = new Label(1, 2, "Population");
        sheet.addCell(popLabel);
        List<String> loci=myShepherd.getAllLoci();
        int numLoci=loci.size();
        int locusColumn=2;
        if(request.getParameter("exportHaplos")!=null){
          
          Label haploLabel = new Label(locusColumn, 2, "mtDNA");
          sheet.addCell(haploLabel);
          
        }
        else{
          for(int r=0;r<numLoci;r++){
            String locus=loci.get(r);
            if((request.getParameter("hasMSMarkers")!=null)||((request.getParameter(locus)!=null)&&(!request.getParameter(locus).equals(""))&&(request1.getParameter(locus)!=null)&&(!request1.getParameter(locus).equals("")))){
          
              Label lociLabel = new Label(locusColumn, 2, locus);
              sheet.addCell(lociLabel);
              locusColumn++;
            
              //Genalex 2010 fails if the second column label is added
              //Label lociLabel2 = new Label(locusColumn, 2, locus);
              //sheet.addCell(lociLabel2);
              locusColumn++;
            }
          }
        }
        
        //later, we might ant to add columns for Lat and Long
       
         int count = 2;

         for(int i=0;i<numPopulations;i++){
           
            Vector iterateMe=new Vector();
            if(i==0){
              iterateMe=query1Individuals;
              System.out.println("     Iterating population 1...");
            }
            else{
              iterateMe=query2Individuals;
              System.out.println("     Iterating population 2...");
            }
            
            for(int k=0;k<iterateMe.size();k++){
              
              MarkedIndividual indy=(MarkedIndividual)iterateMe.get(k);
              //System.out.println("          Individual: "+indy.getIndividualID());
              
              count++;
              
              Label lNumber = new Label(0, count, indy.getIndividualID()+"_"+(i+1));
              sheet.addCell(lNumber);
              
              Label popNumber = new Label(1, count, ("Search Result "+(i+1)));
              sheet.addCell(popNumber);
              
              locusColumn=2;
              
              if(request.getParameter("exportHaplos")!=null){
                
                
                if(numHaplos>0){
                  //now add the haplotype
                    if(indy.getHaplotype()!=null){
                      String haplo=indy.getHaplotype();
                      Integer haploNum = new Integer(haplos.indexOf(haplo)+1);
                      Label lociLabel = new Label(locusColumn, count, haploNum.toString());
                      sheet.addCell(lociLabel);
                    }
                    else{
                      Label lociLabel = new Label(locusColumn, count, "0");
                      sheet.addCell(lociLabel);
                    }
                  }
                
              }
              else{
              for(int r=0;r<numLoci;r++){
                String locus=loci.get(r);
                if((request.getParameter("hasMSMarkers")!=null)||((request.getParameter(locus)!=null)&&(!request.getParameter(locus).equals(""))&&(request1.getParameter(locus)!=null)&&(!request1.getParameter(locus).equals("")))){
                  
                
                if(indy.hasLocus(locus)){
                  ArrayList<Integer> vals=indy.getAlleleValuesForLocus(locus);
                  if(vals.size()==1){
                    Label lociLabel = new Label(locusColumn, count, vals.get(0).toString());
                    sheet.addCell(lociLabel);
                    locusColumn++;
                    Label lociLabel2 = new Label(locusColumn, count, vals.get(0).toString());
                    sheet.addCell(lociLabel2);
                    locusColumn++;
                  }
                  
                  else if(vals.size()==2){
                    Label lociLabel = new Label(locusColumn, count, vals.get(0).toString());
                    sheet.addCell(lociLabel);
                    locusColumn++;
                    Label lociLabel2 = new Label(locusColumn, count, vals.get(1).toString());
                    sheet.addCell(lociLabel2);
                    locusColumn++;
                  }
                  else{
                    Label lociLabel = new Label(locusColumn, count, "0");
                    sheet.addCell(lociLabel);
                    locusColumn++;
                    Label lociLabel2 = new Label(locusColumn, count, "0");
                    sheet.addCell(lociLabel2);
                    locusColumn++;
                  }
                  
                }
                else{
                  Label lociLabel = new Label(locusColumn, count, "0");
                  sheet.addCell(lociLabel);
                  locusColumn++;
                  Label lociLabel2 = new Label(locusColumn, count, "0");
                  sheet.addCell(lociLabel2);
                  locusColumn++;
                }
                
                
              }

                
              }
            }
           
             
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