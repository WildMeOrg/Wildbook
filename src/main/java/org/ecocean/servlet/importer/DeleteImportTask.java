package org.ecocean.servlet.importer;


import org.ecocean.Annotation;
import org.ecocean.CommonConfiguration;
import org.ecocean.Encounter;
import org.ecocean.MarkedIndividual;
import org.ecocean.Occurrence;
import org.ecocean.Shepherd;
import org.ecocean.security.Collaboration;
import org.ecocean.servlet.ServletUtilities;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.util.ArrayList;
import java.util.List;
import java.io.*;

public class DeleteImportTask extends HttpServlet {

  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }

  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }

  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    String context="context0";
    context=ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);
    myShepherd.setAction("DeleteImportTask.class");
    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean locked = false;
    
    
    myShepherd.beginDBTransaction();
    if (request.getParameter("taskID")!=null && myShepherd.getImportTask(request.getParameter("taskID"))!=null && Collaboration.canUserAccessImportTask(myShepherd.getImportTask(request.getParameter("taskID")),request)) {
    
      try {
        
        ImportTask task=myShepherd.getImportTask(request.getParameter("taskID"));
        List<Encounter> allEncs=task.getEncounters();
        int total=allEncs.size();
        int count=0;

         while(count < total){

           Encounter enc= allEncs.get(count);
           count++;
           Occurrence occ = myShepherd.getOccurrence(enc);
           MarkedIndividual mark = myShepherd.getMarkedIndividualQuiet(enc.getIndividualID());

           if (enc.getAnnotations()!=null) {
             for (Annotation ann: enc.getAnnotations()) {
               myShepherd.throwAwayAnnotation(ann);
             }
           }

           // get weird foreign key errors related to ENCOUNTER_ANNOTATIONS without this
           enc.setAnnotations(new ArrayList<Annotation>());

           //handle occurrences
           if (occ!=null) {
               occ.removeEncounter(enc);
               myShepherd.updateDBTransaction();
               if(occ.getEncounters().size()==0) {
                 myShepherd.throwAwayOccurrence(occ);
                 myShepherd.updateDBTransaction();
               }
           }
           
           //handle markedindividual
           if(mark!=null) {
             mark.removeEncounter(enc);
             myShepherd.updateDBTransaction();
             if(mark.getEncounters().size()==0) {
               myShepherd.throwAwayMarkedIndividual(mark);
               myShepherd.updateDBTransaction();
             }
           }
         
            if(task!=null) {
                   task.removeEncounter(enc);
                   task.addLog("Servlet DeleteImportTask removed Encounter: "+enc.getCatalogNumber());
                   myShepherd.updateDBTransaction();
            }

             try {
               myShepherd.throwAwayEncounter(enc);
             } 
             catch (Exception e) {
               System.out.println("Exception on throwAwayEncounter!!");
               e.printStackTrace();
             }

             myShepherd.updateDBTransaction();
         }
        myShepherd.getPM().deletePersistent(task); 
        myShepherd.commitDBTransaction();
      } 
      catch (Exception le) {
        locked = true;
        le.printStackTrace();
        myShepherd.rollbackDBTransaction();
      }
      finally{
        myShepherd.closeDBTransaction();
      }

      if (!locked) {
        out.println(ServletUtilities.getHeader(request));
        out.println("<strong>Success!</strong> I have successfully removed ImportTask " + request.getParameter("taskID") + ".");
        out.println("<p><a href=\"//" + CommonConfiguration.getURLLocation(request) + "/imports.jsp\">Return to Imports</a></p>\n");
        
        response.setStatus(HttpServletResponse.SC_OK);
        out.println(ServletUtilities.getFooter(context));
      }
      else {

        out.println(ServletUtilities.getHeader(request));
        out.println("<strong>Failure!</strong> I failed to delete this ImportTask. Check the logs for more details.");
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/adoptions/adoption.jsp\">Return to the Adoption Create/Edit page.</a></p>\n");
        out.println(ServletUtilities.getFooter(context));

      }

    } 
    else {
      myShepherd.rollbackDBTransaction();
      myShepherd.closeDBTransaction();
      out.println(ServletUtilities.getHeader(request));
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      out.println("<strong>Error:</strong> I was unable to remove your ImportTask. I cannot find the task that you intended it for in the database.");
      out.println(ServletUtilities.getFooter(context));
      myShepherd.rollbackDBTransaction();
      myShepherd.closeDBTransaction();
    }
    out.close();
    
  }


}
