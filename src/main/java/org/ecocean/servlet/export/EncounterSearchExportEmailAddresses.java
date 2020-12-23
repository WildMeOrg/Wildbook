package org.ecocean.servlet.export;


import javax.servlet.*;
import javax.servlet.http.*;

import java.io.*;
import java.util.*;

import org.ecocean.*;
import org.ecocean.servlet.ServletUtilities;

import java.lang.StringBuffer;


//adds spots to a new encounter
public class EncounterSearchExportEmailAddresses extends HttpServlet{
  
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
    myShepherd.setAction("EncounterSearchExportEmailAddresses.class");
    

    
    Vector rEncounters = new Vector();
    
  //Let's setup our email export file options
    String emailFilename = "emailResults_" + request.getRemoteUser() + ".txt";
    
    //setup data dir
    String rootWebappPath = getServletContext().getRealPath("/");
    File webappsDir = new File(rootWebappPath).getParentFile();
    File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));
    if(!shepherdDataDir.exists()){shepherdDataDir.mkdirs();}
    File encountersDir=new File(shepherdDataDir.getAbsolutePath()+"/encounters");
    if(!encountersDir.exists()){encountersDir.mkdirs();}
    
    File emailFile = new File(encountersDir.getAbsolutePath()+"/"+ emailFilename);

    myShepherd.beginDBTransaction();
    
    
    try {
      EncounterQueryResult queryResult = EncounterQueryProcessor.processQuery(myShepherd, request, "year descending, month descending, day descending");
      rEncounters = queryResult.getResult();
      
			Vector blocked = Encounter.blocked(rEncounters, request);
			if (blocked.size() > 0) {
				response.setContentType("text/html");
				PrintWriter out = response.getWriter();
				out.println(ServletUtilities.getHeader(request));  
				out.println("<html><body><p><strong>Access denied. Not all data is owned by you.</strong></p>");
				out.println(ServletUtilities.getFooter(context));
				out.close();
		    myShepherd.rollbackDBTransaction();
		    myShepherd.closeDBTransaction();
				return;
			}
      
      //set up the output stream
      FileOutputStream fos = new FileOutputStream(emailFile);
      OutputStreamWriter outp = new OutputStreamWriter(fos);
      
      //int numMatchingEncounters=rEncounters.size();


            String contribs = addEmails(rEncounters);

            outp.write(contribs);
            outp.close();
    
          
        //now write out the file
        response.setContentType("text/plain");
        response.setHeader("Content-Disposition","attachment;filename="+emailFilename);
        //ServletContext ctx = getServletContext();
        //InputStream is = ctx.getResourceAsStream("/encounters/"+emailFilename);
       InputStream is=new FileInputStream(emailFile);
        
        
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
        out.println("<p>Please let the webmaster know you encountered an error at: "+this.getServletName()+" servlet</p></body></html>");
        out.println(ServletUtilities.getFooter(context));
        out.close();
    }
    myShepherd.rollbackDBTransaction();
    myShepherd.closeDBTransaction();

      
    }
  
  public String addEmails(Vector encs) {

    StringBuffer contributors = new StringBuffer();
    int size = encs.size();
    for (int f = 0; f < size; f++) {

      Encounter tempEnc = (Encounter) encs.get(f);

      //calculate the number of submitter contributors
      if (tempEnc.getSubmitters()!=null) {
        List<String> subs=tempEnc.getSubmitterEmails();
        for(String email:subs) {if(!contributors.toString().contains(email))contributors.append(email+ "\n");}
      }

      //calculate the number of photographer contributors
      if (tempEnc.getPhotographers()!=null) {
        List<String> subs=tempEnc.getPhotographerEmails();
        for(String email:subs) {if(!contributors.toString().contains(email))contributors.append(email+ "\n");}
      }


    }

    return contributors.toString();

  } //end for
  
  }
