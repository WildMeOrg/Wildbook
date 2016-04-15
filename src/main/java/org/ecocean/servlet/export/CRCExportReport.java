package org.ecocean.servlet.export;

import javax.servlet.*;
import javax.servlet.http.*;
import java.io.*;
import java.lang.Boolean;
import java.util.*;
import org.ecocean.*;
import org.ecocean.grid.MatchObject;
import org.ecocean.media.*;
import org.ecocean.servlet.ServletUtilities;
import jxl.write.*;
import jxl.Workbook;
import org.ecocean.identity.*;
import org.json.JSONObject;


public class CRCExportReport extends HttpServlet{
  
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
    String filename = "encounterSearchResults_CRCexport_" + request.getRemoteUser() + ".xls";
    
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
        
        Label labelNegA = new Label(0, 0, "Encounter");
        sheet.addCell(labelNegA);
        
        Label label0 = new Label(1, 0, "Filename");
        sheet.addCell(label0);
        Label label1 = new Label(2, 0, "Alternate ID");
        sheet.addCell(label1);
        Label label2 = new Label(3, 0, "Match Found (Y/N)");
        sheet.addCell(label2);
        Label label2a = new Label(4, 0, "Matched Splash ID or Filename of Alternate ID");
        sheet.addCell(label2a);
        Label label3 = new Label(5, 0, "Alternate ID of Match");
        sheet.addCell(label3);

        // Excel export =========================================================
        int count = 0;

         for(int i=0;i<numMatchingEncounters;i++){
            Encounter enc=(Encounter)rEncounters.get(i);
            
            List<Annotation> annots=enc.getAnnotations();
            int numAnnots=annots.size();
            for(int j=0;j<numAnnots;j++){
              Annotation annot=annots.get(j);
              if(annot.getMediaAsset()!=null){
                MediaAsset asset=annot.getMediaAsset();
                String localFilename="FILENAME";
                if(asset.webURLString()!=null){
                  localFilename=asset.webURLString();
                  if(localFilename.indexOf("/")!=-1){
                    int lastIndex=localFilename.lastIndexOf("/");
                    localFilename=localFilename.substring(lastIndex+1);
                  }
                }
                count++;
                numResults++;
                
              //set the filename
                Label lNumberNeg = new Label(0, count, enc.getCatalogNumber());
                sheet.addCell(lNumberNeg);
                
                //set the filename
                Label lNumber = new Label(1, count, localFilename);
                sheet.addCell(lNumber);
                
                //set Encounter.alternateID
                String altID="";
                if(enc.getAlternateID()!=null){altID=enc.getAlternateID();}
                Label lNumberx1 = new Label(2, count, altID);
                sheet.addCell(lNumberx1);
                
                
                
                
                
                //set whether any result has surfaced
                String matchString="";
                String matchID="";
                String matchAlternateID="";
                
                //OK, now check for the existence of any match
                String[] jobID=IBEISIA.findTaskIDsFromObjectID(annot.getId(), myShepherd);
                if(jobID!=null){
                  int numJobs=jobID.length;
                  for(int k=0;k<numJobs;k++){
                    String taskID=jobID[k];
                    HashMap<String,Object> ires = IBEISIA.getTaskResultsAsHashMap(taskID, myShepherd);
                    if ((ires.get("success") != null) && (Boolean)ires.get("success") && (ires.get("results") != null)) {
                        System.out.println("got legit results from IBEIS-IA" + ires.toString());
                        
                        //we can now say at least that analysis was run, even if no result came back
                        matchString="N";
                        
                        HashMap<String,Object> map = (HashMap<String,Object>)ires.get("results");
                        if(map!=null){
                          Set<String> keys=map.keySet();
                          Iterator<String> iter=keys.iterator();
                          while(iter.hasNext()){
                            HashMap<String,Double> iscores = (HashMap<String,Double>)map.get(iter.next());  //the thing we are really after, encNum=>score
                            if(iscores!=null){
                              Set<String> encNums=iscores.keySet();
                              Iterator<String> iter2=encNums.iterator();
                              while(iter2.hasNext()){
                                String encNum=iter2.next();
                                if(myShepherd.isEncounter(encNum)){
                                  Encounter matchEnc=myShepherd.getEncounter(encNum);
                                  String matchIndy="";
                                  String matchAlt="";
                                  if(matchEnc.getIndividualID()!=null){
                                    matchIndy=matchEnc.getIndividualID();
                                    if(!matchID.equals("")){matchIndy=", "+matchIndy;}
                                    
                                    if(matchEnc.getAlternateID()!=null){
                                      matchAlt=matchAlt+matchEnc.getAlternateID();
                                      if(!matchAlternateID.equals("")){matchAlt=", "+matchAlt;}
                                      
                                    }
                                  }
                                  matchID=matchID+matchIndy;
                                  matchAlternateID=matchAlternateID+matchAlt;
                                }
                              }
                            }
                            
                            
                            
                          }
                        }
                    }
                  }
                  
                }
              //NEED HELP HERE
                
                if((!matchID.equals(""))||(!matchAlternateID.equals(""))){matchString="Y";}
                
                Label lNumberx2 = new Label(3, count, matchString);
                sheet.addCell(lNumberx2);
                Label lNumberx3 = new Label(4, count, matchID);
                sheet.addCell(lNumberx3);
                Label lNumberx4 = new Label(5, count, matchAlternateID);
                sheet.addCell(lNumberx4);
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
