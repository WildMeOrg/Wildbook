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

public class EncounterSetState extends HttpServlet {
    public void init(ServletConfig config)
    throws ServletException {
        super.init(config);
    }

    public void doGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        doPost(request, response);
    }

    private void setDateLastModified(Encounter enc) {
        String strOutputDateTime = ServletUtilities.getDate();

        enc.setDWCDateLastModified(strOutputDateTime);
    }

    public void doPost(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        String context = "context0";

        context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("EncounterSetState.class");
        // set up for response
        response.setContentType("text/html");
        PrintWriter out = response.getWriter();
        boolean locked = false;
        boolean isOwner = true;
        if ((request.getParameter("number") != null) && (request.getParameter("state") != null)) {
            myShepherd.beginDBTransaction();
            Encounter changeMe = myShepherd.getEncounter(request.getParameter("number"));
            setDateLastModified(changeMe);
            String state = request.getParameter("state");
            String oldScar = "None";

            try {
                if (changeMe.getState() != null) {
                    oldScar = changeMe.getState();
                }
                changeMe.setState(state);
                changeMe.addComments("<p><em>" + request.getRemoteUser() + " on " +
                    (new java.util.Date()).toString() + "</em><br>Changed state from " + oldScar +
                    " to " + state + ".</p>");
            } catch (Exception le) {
                locked = true;
                le.printStackTrace();
                myShepherd.rollbackDBTransaction();
            }
            if (!locked) {
                myShepherd.commitDBTransaction();
                // out.println(ServletUtilities.getHeader(request));
                out.println("<strong>Success:</strong> Encounter state has been updated from " +
                    oldScar + " to " + state + ".");
                response.setStatus(HttpServletResponse.SC_OK);

                String message = "Encounter " + request.getParameter("number") +
                    " state has been updated from " + oldScar + " to " + state + ".";
                ServletUtilities.informInterestedParties(request, request.getParameter("number"),
                    message, context);
            } else {
                // out.println(ServletUtilities.getHeader(request));
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.println(
                    "<strong>Failure:</strong> Encounter state was NOT updated because another user is currently modifying this reconrd. Please try to reset the scarring again in a few seconds.");
            }
        } else {
            // out.println(ServletUtilities.getHeader(request));
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.println(
                "<strong>Error:</strong> I don't have enough information to complete your request.");
        }
        out.close();
        myShepherd.closeDBTransaction();
    }
}
