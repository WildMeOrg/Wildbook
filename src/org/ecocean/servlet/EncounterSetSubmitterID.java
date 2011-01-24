package org.ecocean.servlet;

import org.ecocean.CommonConfiguration;
import org.ecocean.Encounter;
import org.ecocean.Shepherd;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

//import javax.jdo.*;
//import com.poet.jdo.*;


public class EncounterSetSubmitterID extends HttpServlet {


  public void init(ServletConfig config) throws ServletException {
    super.init(config);

  }


  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

    // Here we forward to the appropriate page using the request dispatcher

    //getServletContext().getRequestDispatcher("/Noget.html").forward(req, res);
    doPost(request, response);
  }

  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    Shepherd myShepherd = new Shepherd();
    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean locked = false;

    String encounterNumber = "None", submitter = "N/A";
    String prevSubmitter = "N/A";
    myShepherd.beginDBTransaction();


    encounterNumber = request.getParameter("number");
    submitter = request.getParameter("submitter");
    if ((myShepherd.isEncounter(encounterNumber)) && (request.getParameter("number") != null)) {
      Encounter sharky = myShepherd.getEncounter(encounterNumber);

      try {


        if (sharky.getSubmitterID() != null) {
          prevSubmitter = sharky.getSubmitterID();
        }

        sharky.setSubmitterID(submitter);
        sharky.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>" + "Changed Library submitter ID from " + prevSubmitter + " to " + submitter + ".</p>");

      } catch (Exception le) {
        locked = true;
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
      }

      if (!locked) {
        myShepherd.commitDBTransaction();
        myShepherd.closeDBTransaction();
        out.println(ServletUtilities.getHeader());
        out.println("<strong>Success!</strong> I have successfully changed the Library submitter ID for encounter " + encounterNumber + " from " + prevSubmitter + " to " + submitter + ".</p>");

        out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation() + "/encounters/encounter.jsp?number=" + encounterNumber + "\">Return to encounter " + encounterNumber + "</a></p>\n");
        out.println(ServletUtilities.getFooter());
        String message = "The submitter ID for encounter " + encounterNumber + " was changed from " + prevSubmitter + " to " + submitter + ".";
        ServletUtilities.informInterestedParties(encounterNumber, message);
      } else {

        out.println(ServletUtilities.getHeader());
        out.println("<strong>Failure!</strong> This encounter is currently being modified by another user. Please wait a few seconds before trying to remove this data file again.");

        out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation() + "/encounters/encounter.jsp?number=" + encounterNumber + "\">Return to encounter " + encounterNumber + "</a></p>\n");
        out.println(ServletUtilities.getFooter());

      }
    } else {

      out.println(ServletUtilities.getHeader());
      out.println("<strong>Error:</strong> I was unable to set the submitter ID. I cannot find the encounter that you intended it for in the database, or I wasn't sure what file you wanted to remove.");
      out.println(ServletUtilities.getFooter());

    }
    out.close();
    myShepherd.rollbackDBTransaction();
    myShepherd.closeDBTransaction();
  }


}
	
	
