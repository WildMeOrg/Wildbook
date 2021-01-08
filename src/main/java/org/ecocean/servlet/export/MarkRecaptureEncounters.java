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
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.Arrays;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.joda.time.format.*;
import org.joda.time.*; 
import org.ecocean.*;
import org.ecocean.servlet.ServletUtilities;

public class MarkRecaptureEncounters extends HttpServlet{

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
    myShepherd.setAction("MarkRecaptureEncounters.class");
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
    String sessionsSummary="Summary of capture sessions:\n";
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
    String inpFilename = "MarkRecaptureEncounters_" + request.getRemoteUser() + ".inp";
    


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
      
      Vector<Encounter> rEncounters = new Vector<Encounter>();
      EncounterQueryResult result = EncounterQueryProcessor.processQuery(myShepherd, request, order);
      rEncounters = result.getResult();
      
      String histories = addHistories(rEncounters, numSessions, request, start, end);
            
      String header="";
      String footer="";
      
      if(request.getParameter("includeQueryComments")!=null){
        header="\r\n\r\n/* \r\nQuery parameters:\n"+result.getQueryPrettyPrint().replaceAll("<br />", "\r\n")+"\r\n\r\n";
        header+= sessionsSummary;
        footer="\r\n\r\n/*\r\nSourceURL:\r\n"+"http://"+CommonConfiguration.getURLLocation(request)+"/MarkRecaptureEncounters?"+request.getQueryString()+"\r\n*/\r\n\r\n";
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

  public static boolean hasTrue(boolean[] bArr) {
    for (boolean b: bArr) if (b) return true;
    return false;
  }
  
 public static String addHistories(Vector<Encounter> rEncounters, int numSessions, HttpServletRequest request, DateTime[] start, DateTime[] end) {


    List<String> individualIDs  = Encounter.getIndividualIDs(rEncounters);
    int numIndividuals = individualIDs.size();
    Map<String,Integer> idToRow = new HashMap<String,Integer>();
    for (int i=0; i<numIndividuals; i++) {
      idToRow.put(individualIDs.get(i), i);
    }

    boolean[][] markRecapture = new boolean[numIndividuals][numSessions]; // populates with all false values

    boolean includeIndID = Util.stringExists(request.getParameter("includeIndividualID"));
    Map<String,String> indIDToDisplayName = (includeIndID) ? new HashMap<String,String>() : null;

    // for each encounter, add a 1 in the markRecapture matrix in the correct spot
    for (Encounter enc: rEncounters) {
      String indID = enc.getIndividualID();
      if (indID == null) continue;
      int rowNum = idToRow.get(indID);
      for (int session=0; session<numSessions; session++) {
        if (enc.wasInPeriod(start[session],end[session])) markRecapture[rowNum][session] = true;
      }
      // store the individual's displayName if need be
      if (includeIndID && !indIDToDisplayName.containsKey(indID)) {
        indIDToDisplayName.put(indID, enc.getIndividual().getDisplayName(request));
      }
    }

    // done constructing the markRecapture matrix, now we construct the string 
    StringBuilder result = new StringBuilder();
    for (int i=0; i<numIndividuals; i++) {
      boolean[] row = markRecapture[i];
      String boolRow = Arrays.toString(row);
      result.append(boolRow);
      // final column of row
      String finalColumn = (hasTrue(row)) ? " 1;" : " 0;";
      result.append(finalColumn);
      // append individual info if necessary
      if (includeIndID) {
        String indID = individualIDs.get(i);
        String displayName = indIDToDisplayName.get(indID);
        String webUrl = MarkedIndividual.getWebUrl(indID, request);
        String indDetails = "\t\t/* "+displayName+" ("+webUrl+") */";
        result.append(indDetails);
      }
      result.append("\n");

    }
    String bigResult = result.toString();
    // do all the string replacements at the end for efficiency (?)
    bigResult = bigResult.replaceAll("true", "1")
                         .replaceAll("false","0")
                         .replace("[","")
                         .replace("]","")
                         .replaceAll(", ","");
    return bigResult;
  }
  
  
}
