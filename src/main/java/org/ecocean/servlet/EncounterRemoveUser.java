package org.ecocean.servlet;

import org.ecocean.Encounter;
import org.ecocean.Shepherd;
import org.ecocean.User;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

import java.io.IOException;
import java.io.PrintWriter;

import java.util.List;

public class EncounterRemoveUser extends HttpServlet {
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
        myShepherd.setAction("EncounterRemoveUser.class");
        // set up for response
        response.setContentType("text/html");
        PrintWriter out = response.getWriter();
        boolean locked = false;
        boolean isOwner = true;

        myShepherd.beginDBTransaction();
        if ((request.getParameter("encounter") != null) && (request.getParameter("type") != null) &&
            (request.getParameter("uuid") != null) &&
            (myShepherd.isEncounter(request.getParameter("encounter")))) {
            Encounter changeMe = myShepherd.getEncounter(request.getParameter("encounter"));
            setDateLastModified(changeMe);
            String type = request.getParameter("type").trim();

            try {
                User user = myShepherd.getUserByUUID(request.getParameter("uuid"));
                if (type.equals("submitter")) {
                    List<User> users = changeMe.getSubmitters();
                    users.remove(user);
                    changeMe.setSubmitters(users);
                    changeMe.addComments("<p><em>" + request.getRemoteUser() + " on " +
                        (new java.util.Date()).toString() + "</em><br>Removed user " +
                        user.getUUID() + " of type " + type + ".</p>");
                    myShepherd.updateDBTransaction();
                    response.setStatus(HttpServletResponse.SC_OK);
                } else if (type.equals("photographer")) {
                    List<User> users = changeMe.getPhotographers();
                    users.remove(user);
                    changeMe.setPhotographers(users);
                    changeMe.addComments("<p><em>" + request.getRemoteUser() + " on " +
                        (new java.util.Date()).toString() + "</em><br>Removed user " +
                        user.getUUID() + " of type " + type + ".</p>");
                    myShepherd.updateDBTransaction();
                    response.setStatus(HttpServletResponse.SC_OK);
                } else if (type.equals("informOther")) {
                    List<User> users = changeMe.getInformOthers();
                    users.remove(user);
                    changeMe.setInformOthers(users);
                    changeMe.addComments("<p><em>" + request.getRemoteUser() + " on " +
                        (new java.util.Date()).toString() + "</em><br>Removed user " +
                        user.getUUID() + " of type " + type + ".</p>");
                    myShepherd.updateDBTransaction();
                    response.setStatus(HttpServletResponse.SC_OK);
                } else {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                }
            } catch (Exception le) {
                locked = true;
                le.printStackTrace();
                // myShepherd.rollbackDBTransaction();
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            } finally {
                myShepherd.rollbackDBTransaction();
                myShepherd.closeDBTransaction();
                out.println(response);
            }
            if (locked) {
                out.println(
                    "<strong>Failure:</strong> Encounter state was NOT updated because another user is currently modifying this reconrd. Please try to reset the scarring again in a few seconds.");
            }
        } else {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
        }
        out.close();
        // myShepherd.closeDBTransaction();
    }
}
