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

import org.joda.time.format.*;
import org.joda.time.*; 
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
    
    String context="context0";
    context=ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);
    myShepherd.setAction("SimpleCMROutput.class");
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
    
    DateTime[] start=new DateTime[numSessions];
    DateTime[] end=new DateTime[numSessions];
    String sessionsSummary="/*\n\nSummary of capture sessions:\n";
    for(int j=0;j<numSessions;j++){
      
      //establish startDate
      DateTimeFormatter parser1 = ISODateTimeFormat.date();
      String startdate = request.getParameter(("datepicker"+j+"start"));
      start[j]=parser1.parseDateTime(startdate);
      //System.out.println(("datepicker"+j+"start")+": "+start.toString());
      
      //establish endDate
      DateTimeFormatter parser2 = ISODateTimeFormat.date();
      String enddate = request.getParameter(("datepicker"+j+"end"));
      end[j]=parser2.parseDateTime(enddate);
      //System.out.println(("datepicker"+j+"end")+": "+end.toString());
      
      sessionsSummary+=("Session "+(j+1)+": "+start[j].toString()+" to "+end[j].toString()+"\n");
      
    }
    sessionsSummary+="\n*/\n";
    
  //Let's setup our email export file options
    String inpFilename = "SimpleMarkRecapture_" + request.getRemoteUser() + ".inp";
    
    //setup data dir
    String rootWebappPath = getServletContext().getRealPath("/");
    File webappsDir = new File(rootWebappPath).getParentFile();
    File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));
    if(!shepherdDataDir.exists()){shepherdDataDir.mkdirs();}
    File encountersDir=new File(shepherdDataDir.getAbsolutePath()+"/encounters");
    if(!encountersDir.exists()){encountersDir.mkdirs();}
    
    File inpFile = new File(encountersDir.getAbsolutePath()+"/"+ inpFilename);

    myShepherd.beginDBTransaction();
    
    
    try {
      
      //set up the output stream
      FileOutputStream fos = new FileOutputStream(inpFile);
      OutputStreamWriter outp = new OutputStreamWriter(fos);
      
      Vector<MarkedIndividual> rIndividuals = new Vector<MarkedIndividual>();
      MarkedIndividualQueryResult result = IndividualQueryProcessor.processQuery(myShepherd, request, order);
      rIndividuals = result.getResult();
      


      String histories = addHistories(rIndividuals,numSessions, request, start, end);
      String header=sessionsSummary;
      String footer="";
      
      if(request.getParameter("includeQueryComments")!=null){
        header+="\r\n\r\n/* \r\nQuery parameters:\n"+result.getQueryPrettyPrint().replaceAll("<br />", "\r\n")+"\r\n*/\r\n\r\n";
        footer="/*\r\nSourceURL:\r\n"+"http://"+CommonConfiguration.getURLLocation(request)+"/SimpleCMROutput?"+request.getQueryString()+"\r\n*/\r\n\r\n";
      }
      
      if(request.getParameter("includeQueryComments")!=null){
        outp.write(header);
      }
      outp.write(histories);
      if(request.getParameter("includeQueryComments")!=null){
        outp.write(footer);
      }
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
        out.println(ServletUtilities.getFooter(context));
        out.close();
    }
    myShepherd.rollbackDBTransaction();
    myShepherd.closeDBTransaction();

      
    }
  
 private String addHistories(Vector<MarkedIndividual> rIndividuals, int numSessions, HttpServletRequest request, DateTime[] start, DateTime[] end) {

    StringBuffer histories = new StringBuffer();
    int numIndividuals=rIndividuals.size();
    //System.out.println("numSessions in method is: "+numSessions);
    for(int i=0;i<numIndividuals;i++){
        MarkedIndividual indie=rIndividuals.get(i);
        String thisRecord="";
        
        for(int j=0;j<numSessions;j++){
          
          
          boolean wasSighted=false;
          //add the zero or the one
          
          //remember that the folowing methods use GregorianCalendar internally, so month needs to be -1 as Gregorian counts January as month 0
          if(request.getParameter("locationCodeField")!=null){
            if(indie.wasSightedInPeriod(start[j].getYear(), (start[j].getMonthOfYear()-1), start[j].getDayOfMonth(), end[j].getYear(),(end[j].getMonthOfYear()-1), end[j].getDayOfMonth(), request.getParameter("locationCodeField").trim())){
              wasSighted=true;
            }
          }
          else{
            if(indie.wasSightedInPeriod(start[j].getYear(), (start[j].getMonthOfYear()-1), start[j].getDayOfMonth(), end[j].getYear(),(end[j].getMonthOfYear()-1), end[j].getDayOfMonth())){
              wasSighted=true;
            }
          }
          
          if(wasSighted){
            thisRecord+="1";
           }
          else{
            thisRecord+="0";
          }
          
        }
        
        String includeID="";
        if(request.getParameter("includeIndividualID")!=null){
          includeID="     /* "+indie.getIndividualID()+" */";
        }
      if(thisRecord.indexOf("1")!=-1){
        histories.append(thisRecord+" 1;"+includeID+"\r\n");
      }
    }

    return histories.toString();

  } //end for
  
  
}
