package org.ecocean.servlet;

import org.ecocean.*;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;
import java.util.Vector;

public class DontTrack extends HttpServlet {
    public void init(ServletConfig config)
    throws ServletException {
        super.init(config);
    }

    public void doGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        doPost(request, response);
    }

    public void doPost(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        String context = "context0";

        context = ServletUtilities.getContext(request);
        String langCode = ServletUtilities.getLanguageCode(request);
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("DontTrack.class");
        // set up for response
        response.setContentType("text/html");
        PrintWriter out = response.getWriter();
        boolean locked = false;
        String email = "None", encounterNumber = "None", shark = "None";
        Encounter enc = null;
        myShepherd.beginDBTransaction();
        if ((request.getParameter("number") != null) &&
            (myShepherd.isEncounter(request.getParameter("number"))) &&
            (request.getParameter("email") != null)) {
            email = request.getParameter("email");
            encounterNumber = request.getParameter("number");

            enc = myShepherd.getEncounter(encounterNumber);
            // int positionInList=0;
            try {
                Vector interested = enc.getInterestedResearchers();
                // int initNumberImages=interested.size();
                // remove copyrighted images to allow them to be reset
                for (int i = 0; i < interested.size(); i++) {
                    String thisEmail = (String)interested.get(i);
                    if (thisEmail.equals(email)) {
                        interested.remove(i);
                        i--;
                    }
                }
                // enc.addComments("<p><em>"+request.getRemoteUser()+" on "+(new java.util.Date()).toString()+"</em><br>"+"Removed encounter image
                // graphic: "+fileName+".</p>");
            } catch (Exception le) {
                locked = true;
                myShepherd.rollbackDBTransaction();
                myShepherd.closeDBTransaction();
            }
            if (!locked) {
                myShepherd.commitDBTransaction();
                myShepherd.closeDBTransaction();
                out.println(ServletUtilities.getHeader(request));
                out.println(
                    "<strong>Success!</strong> I have successfully stopped the tracking of encounter#"
                    + encounterNumber + " for e-mail address " + email + ".");

                out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) +
                    "/encounters/encounter.jsp?number=" + encounterNumber + "\">Go to encounter " +
                    encounterNumber + "</a></p>\n");
                out.println(ServletUtilities.getFooter(context));
                // Send email
                Map<String, String> tagMap = NotificationMailer.createBasicTagMap(request, enc);
                NotificationMailer mailer = new NotificationMailer(context, null, email,
                    "encounterTrackingStopped", tagMap);
// ThreadPoolExecutor es = MailThreadExecutorService.getExecutorService();
// es.execute(mailer);
// es.shutdown();
            } else {
                out.println(ServletUtilities.getHeader(request));
                out.println(
                    "<strong>Failure!</strong> This encounter is currently being modified by another user, or the database is locked. Please wait a few seconds before trying to remove this e-mail address from tracking again.");
                out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) +
                    "/encounters/encounter.jsp?number=" + encounterNumber +
                    "\">Return to encounter " + encounterNumber + "</a></p>\n");
                out.println(ServletUtilities.getFooter(context));
            }
        }
        // stop tracking a MarkedIndividual
        else if ((request.getParameter("individual") != null) &&
            (myShepherd.isMarkedIndividual(request.getParameter("individual"))) &&
            (request.getParameter("email") != null)) {
            email = request.getParameter("email");
            shark = request.getParameter("individual");
            MarkedIndividual sharkie = myShepherd.getMarkedIndividual(shark);

            // myShepherd.beginDBTransaction();

            try {
                Vector interested = sharkie.getInterestedResearchers();
                // int initNumberImages=interested.size();
                // remove copyrighted images to allow them to be reset
                for (int i = 0; i < interested.size(); i++) {
                    String thisEmail = (String)interested.get(i);
                    if (thisEmail.equals(email)) {
                        interested.remove(i);
                        i--;
                    }
                }
                // enc.addComments("<p><em>"+request.getRemoteUser()+" on "+(new java.util.Date()).toString()+"</em><br>"+"Removed encounter image
                // graphic: "+fileName+".</p>");
            } catch (Exception le) {
                locked = true;
                myShepherd.rollbackDBTransaction();
                myShepherd.closeDBTransaction();
            }
            if (!locked) {
                myShepherd.commitDBTransaction();
                myShepherd.closeDBTransaction();
                out.println(ServletUtilities.getHeader(request));
                out.println(
                    "<strong>Success!</strong> I have successfully stopped the tracking of " +
                    shark + " for e-mail address " + email + ".");
                response.setStatus(HttpServletResponse.SC_OK);
                out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) +
                    "/individuals.jsp?number=" + shark + "\">Go to " + shark + "</a></p>\n");
                out.println(ServletUtilities.getFooter(context));

                String message = "This is a confirmation that e-mail tracking of data changes to " +
                    shark + " has now been stopped.";
                Map<String, String> tagMap = NotificationMailer.createBasicTagMap(request, enc);
                NotificationMailer mailer = new NotificationMailer(context, null, email,
                    "encounterTrackingStopped", tagMap);
// ThreadPoolExecutor es = MailThreadExecutorService.getExecutorService();
// es.execute(mailer);
// es.shutdown();
            } else {
                out.println(ServletUtilities.getHeader(request));
                out.println(
                    "<strong>Failure!</strong> This record is currently being modified by another user, or the database is locked. Please wait a few seconds before trying to remove this e-mail address from tracking again.");
                out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) +
                    "/individuals.jsp?number=" + shark + "\">Return to " + shark + "</a></p>\n");
                out.println(ServletUtilities.getFooter(context));
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            }
        } else {
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
            out.println(ServletUtilities.getHeader(request));
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.println(
                "<strong>Error:</strong> I was unable to remove your e-mail address from the tracking list. I cannot find the encounter or marked individual that you indicated in the database, or you did not provide a valid e-mail address.");
            out.println(ServletUtilities.getFooter(context));
        }
        out.close();
    }
}
