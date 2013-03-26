package org.ecocean.servlet.export;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
//import java.util.StringTokenizer;
import java.util.Vector;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ecocean.*;
import org.ecocean.servlet.ServletUtilities;

public class SimpleCMROutput extends HttpServlet{

 private static final int BYTES_DOWNLOAD = 1024;

  
  public void init(ServletConfig config) throws ServletException {
      super.init(config);
    }

  
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException,IOException {
      doPost(request, response);
  }
    


  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException{
    
    //set the response
    
    
    Shepherd myShepherd = new Shepherd();
    String order = "";
    
    //determine the number of capture sessions
    int numSessions=0;
    if(request.getParameter("numberSessions")!=null){
      try{
        Integer sess=new Integer(request.getParameter("numberSessions"));
        numSessions=sess.intValue();
      } 
      catch(NumberFormatException nfe){nfe.printStackTrace();}
    }
    System.out.println("numSessions detected is: "+numSessions);
    
  //Let's setup our email export file options
    String inpFilename = "SimpleMarkRecapture_" + request.getRemoteUser() + ".inp";
    
    //setup data dir
    String rootWebappPath = getServletContext().getRealPath("/");
    File webappsDir = new File(rootWebappPath).getParentFile();
    File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName());
    if(!shepherdDataDir.exists()){shepherdDataDir.mkdir();}
    File encountersDir=new File(shepherdDataDir.getAbsolutePath()+"/encounters");
    if(!encountersDir.exists()){encountersDir.mkdir();}
    
    File inpFile = new File(encountersDir.getAbsolutePath()+"/"+ inpFilename);

    myShepherd.beginDBTransaction();
    
    
    try {
      
      //set up the output stream
      FileOutputStream fos = new FileOutputStream(inpFile);
      OutputStreamWriter outp = new OutputStreamWriter(fos);
      
      Vector<MarkedIndividual> rIndividuals = new Vector<MarkedIndividual>();
      MarkedIndividualQueryResult result = IndividualQueryProcessor.processQuery(myShepherd, request, order);
      rIndividuals = result.getResult();
      
      System.out.println("Num matching individuals for SimpleMarkRecapture export: "+rIndividuals.size());
      System.out.println("Query URL for SimpleMarkRecapture export: "+request.getQueryString());

      


      String histories = addHistories(rIndividuals,numSessions, request);
      String header="";
      String footer="";
      
      if(request.getParameter("includeQueryComments")!=null){
        header="\n/* Query parameters:\n"+result.getQueryPrettyPrint().replaceAll("<br />", "\n")+"\n*/\n\n";
        footer="/*\nSourceURL:\n"+"http://"+CommonConfiguration.getURLLocation(request)+"/SimpleCMROutput?"+request.getQueryString()+"\n*/\n\n";
      }
      
      outp.write(header+histories+footer);
      outp.close();
    
          
        //now write out the file
        response.setContentType("text/plain");
        response.setHeader("Content-Disposition","attachment;filename="+inpFilename);
        //ServletContext ctx = getServletContext();
        //InputStream is = ctx.getResourceAsStream("/encounters/"+emailFilename);
       InputStream is=new FileInputStream(inpFile);
        
        
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
      e.printStackTrace();
      response.setContentType("text/html");
      PrintWriter out = response.getWriter();
      out.println(ServletUtilities.getHeader(request));  
      out.println("<html><body><p><strong>Error encountered</strong></p>");
        out.println("<p>Please let the webmaster know you encountered an error at: "+this.getServletName()+" servlet.</p></body></html>");
        out.println(ServletUtilities.getFooter());
        out.close();
    }
    myShepherd.rollbackDBTransaction();
    myShepherd.closeDBTransaction();

      
    }
  
 private String addHistories(Vector<MarkedIndividual> rIndividuals, int numSessions, HttpServletRequest request) {

    StringBuffer histories = new StringBuffer();
    int numIndividuals=rIndividuals.size();
    System.out.println("numSessions in method is: "+numSessions);
    for(int i=0;i<numIndividuals;i++){
        MarkedIndividual indie=rIndividuals.get(i);
        String thisRecord="";
        
        for(int j=0;j<numSessions;j++){
          
          //establish startDate
          
          
          //establish endDate
          
          
          //add the zero or the one
          thisRecord+="0";
          
          
        }
        
        String includeID="";
        if(request.getParameter("includeIndividualID")!=null){
          includeID="     /* "+indie.getIndividualID()+" */";
        }
      histories.append(thisRecord+";"+includeID+"\n");
    }

    return histories.toString();

  } //end for
  
  
}
