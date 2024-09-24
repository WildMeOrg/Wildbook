package org.ecocean.servlet;

import org.ecocean.Encounter;
import org.ecocean.Shepherd;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

import java.io.IOException;
import java.io.PrintWriter;

public class EncounterSetSex extends HttpServlet {
    public void init(ServletConfig config)
    throws ServletException {
        super.init(config);
    }

    private void setDateLastModified(Encounter enc) {
        String strOutputDateTime = ServletUtilities.getDate();

        enc.setDWCDateLastModified(strOutputDateTime);
    }

    public void doGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        doPost(request, response);
    }

    public void doPost(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        String context = "context0";

        context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("EncounterSetSex.class");
        // set up for response
        response.setContentType("text/html");
        PrintWriter out = response.getWriter();
        boolean locked = false, isOwner = true;
        boolean isAssigned = false;

        /**
           if(request.getParameter("number")!=null){
           myShepherd.beginDBTransaction();
           if(myShepherd.isEncounter(request.getParameter("number"))) {
           Encounter verifyMyOwner=myShepherd.getEncounter(request.getParameter("number"));
           String locCode=verifyMyOwner.getLocationCode();

           //check if the encounter is assigned
              if((verifyMyOwner.getSubmitterID()!=null)&&(request.getRemoteUser()!=null)&&(verifyMyOwner.getSubmitterID().equals(request.getRemoteUser()))){
           isAssigned=true;
           }

           //if the encounter is assigned to this user, they have permissions for it...or if they're a manager
              if((request.isUserInRole("admin"))||(isAssigned)){
           isOwner=true;
           }
           //if they have general location code permissions for the encounter's location code else if(request.isUserInRole(locCode)){isOwner=true;}
           }
           myShepherd.rollbackDBTransaction();
           }
         */
        String action = request.getParameter("action");
        System.out.println("Action is: " + action);
        if (action != null) {
            if (action.equals("setEncounterSex")) {
                if ((request.getParameter("number") != null) &&
                    (request.getParameter("selectSex") != null)) {
                    myShepherd.beginDBTransaction();
                    Encounter changeMe = myShepherd.getEncounter(request.getParameter("number"));
                    setDateLastModified(changeMe);
                    String oldSex = "null";

                    try {
                        if (changeMe.getSex() != null) {
                            oldSex = changeMe.getSex();
                        }
                        if (request.getParameter("selectSex") != null) {
                            changeMe.setSex(request.getParameter("selectSex"));
                        } else { changeMe.setSex(null); }
                        changeMe.addComments("<p><em>" + request.getRemoteUser() + " on " +
                            (new java.util.Date()).toString() + "</em><br>Changed sex from " +
                            oldSex + " to " + request.getParameter("selectSex") + ".</p>");
                    } catch (Exception le) {
                        System.out.println("Hit locked exception on action: " + action);
                        locked = true;
                        le.printStackTrace();
                        myShepherd.rollbackDBTransaction();
                    }
                    if (!locked) {
                        myShepherd.commitDBTransaction(action);
                        // out.println(ServletUtilities.getHeader(request));
                        response.setStatus(HttpServletResponse.SC_OK);
                        out.println(
                            "<strong>Success:</strong> encounter sex has been updated from " +
                            oldSex + " to " + request.getParameter("selectSex") + ".");
                        String message = "The sex for encounter #" +
                            request.getParameter("number") + "has been updated from " + oldSex +
                            " to " + request.getParameter("selectSex") + ".";
                        ServletUtilities.informInterestedParties(request,
                            request.getParameter("number"), message, context);
                    } else {
                        // out.println(ServletUtilities.getHeader(request));
                        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                        out.println(
                            "<strong>Failure:</strong> Encounter sex was NOT updated because another user is currently modifying the record for this encounter.");
                    }
                } else {
                    // out.println(ServletUtilities.getHeader(request));
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    out.println(
                        "<strong>Error:</strong> I don't have enough information to complete your request.");

                    // out.println("<p><a href=\"individualSearchResults.jsp\">View all individuals</a></font></p>");
                    // out.println(ServletUtilities.getFooter(context));
                }
            } else {
                // out.println(ServletUtilities.getHeader(request));
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.println(
                    "<p>I didn't understand your command, or you are not authorized for this action.</p>");
                // out.println("<p>Please try again or <a href=\"welcome.jsp\">login here</a>.");
                // out.println(ServletUtilities.getFooter(context));
            }
        } else {
            // out.println(ServletUtilities.getHeader(request));
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.println(
                "<p>I did not receive enough data to process your command. No action was indicated to me.</p>");
            // out.println("<p>Please try again or <a href=\"welcome.jsp\">login here</a>.");
            // out.println(ServletUtilities.getFooter(context));
        }
        out.close();
        myShepherd.closeDBTransaction();
    }
}
