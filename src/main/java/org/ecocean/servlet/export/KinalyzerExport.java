package org.ecocean.servlet.export;
import javax.servlet.*;
import javax.servlet.http.*;

import java.io.*;
import java.util.*;

import org.ecocean.*;

import java.lang.StringBuffer;

//import javax.jdo.Query;


import org.ecocean.genetics.*;
import org.ecocean.servlet.ServletUtilities;

import java.net.URI;
//import java.text.NumberFormat;;




//adds spots to a new encounter
public class KinalyzerExport extends HttpServlet{
  

  private static final int BYTES_DOWNLOAD = 1024;
  
  public void init(ServletConfig config) throws ServletException {
      super.init(config);
    }

  
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException,IOException {
      doPost(request, response);
  }
    


  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException{
    
    //set the response
    response.setContentType("text/csv");
    //PrintWriter out = response.getWriter();
    
    //get our Shepherd
    String context="context0";
    context=ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);
    myShepherd.setAction("KinalyzerExport");

    //setup data dir
    String rootWebappPath = getServletContext().getRealPath("/");
    File webappsDir = new File(rootWebappPath).getParentFile();
    File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));
    if(!shepherdDataDir.exists()){shepherdDataDir.mkdirs();}
    File encountersDir=new File(shepherdDataDir.getAbsolutePath()+"/encounters");
    if(!encountersDir.exists()){encountersDir.mkdirs();}

    String kinFilename = "kinalyzer_export_" + request.getRemoteUser() + ".csv";
    File kinFile = new File(encountersDir.getAbsolutePath()+"/" + kinFilename);


    try{


      //set up the output stream
      FileOutputStream fos = new FileOutputStream(kinFile);
      OutputStreamWriter outp = new OutputStreamWriter(fos);
      
      //set up the vector for matching encounters
      Vector query2Individuals = new Vector();

      //kick off the transaction
      myShepherd.beginDBTransaction();

      //start the query and get the results
      String order = "";

      if(request!=null){

        MarkedIndividualQueryResult queryResult2 = IndividualQueryProcessor.processQuery(myShepherd, request, order);
        query2Individuals = queryResult2.getResult();
        int numSearch2Individuals = query2Individuals.size();
        
        //now let's start writing output
        
        
        //Lines 2+: write the loci
        //let's calculate Fst for each of the loci
        //iterate through the loci
        List<String> loci=myShepherd.getAllLoci();
        int numLoci=loci.size();

        //List<String> haplos=myShepherd.getAllHaplotypes();
        //int numHaplos=haplos.size();
        
        
        //now write out POP2 for search2
        for(int i=0;i<numSearch2Individuals;i++){
          MarkedIndividual indie=(MarkedIndividual)query2Individuals.get(i);
          boolean hasValues=false;
          //outp.write("Sample_ID,Individual_ID,Latitude,Longitude,Date_Time,Region,Sex,Haplotype"+locusString.toString()+",Occurrence_ID\n");
          
          
          String lociString=indie.getIndividualID()+",";
          //NumberFormat myFormat = NumberFormat.getInstance();
          //myFormat.setMinimumIntegerDigits(3);
          for(int r=0;r<numLoci;r++){
            String locus=loci.get(r);
            ArrayList<Integer> values=indie.getAlleleValuesForLocus(locus);
            if(indie.getAlleleValuesForLocus(locus).size()==2){
              lociString+=(values.get(0)+",");
              lociString+=(values.get(1)+",");
              hasValues=true;
            }
            else if(indie.getAlleleValuesForLocus(locus).size()==1){
              lociString+=(values.get(0)+","+values.get(0)+",");
              hasValues=true;
            }
            else{lociString+="-1,-1,";}

          }
          
          int length=lociString.length();

          if(hasValues)outp.write(lociString.substring(0, (length-1))+"\r\n");
         
          //test
          
        }
        
      }
      myShepherd.rollbackDBTransaction();
      myShepherd.closeDBTransaction();
      
      
      outp.close();
      outp=null;
      
      //now write out the file
      response.setContentType("text/csv");
      response.setHeader("Content-Disposition","attachment;filename="+kinFilename);
      ServletContext ctx = getServletContext();
      //InputStream is = ctx.getResourceAsStream("/encounters/"+gisFilename);
     InputStream is=new FileInputStream(kinFile);
      
      
      int read=0;
      byte[] bytes = new byte[BYTES_DOWNLOAD];
      OutputStream os = response.getOutputStream();
     
      while((read = is.read(bytes))!= -1){
        os.write(bytes, 0, read);
      }
      os.flush();
      os.close(); 
      

    }
    catch(Exception e) {
      //out.println("<p><strong>Error encountered</strong></p>");
      //out.println("<p>Please let the webmaster know you encountered an error at: KinalyzerExport servlet.</p>");
      e.printStackTrace();
      myShepherd.rollbackDBTransaction();
      myShepherd.closeDBTransaction();
    }
    myShepherd=null;
    //out.close();
    //out=null;
  }

  
  }