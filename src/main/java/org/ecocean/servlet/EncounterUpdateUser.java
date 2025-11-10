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

import org.datanucleus.api.jdo.JDOPersistenceManager;
import org.datanucleus.api.rest.RESTUtils;

public class EncounterUpdateUser extends HttpServlet {
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
        myShepherd.setAction("EncounterUpdateUser.class");
        // set up for response
        response.setContentType("application/json");
        PrintWriter out = response.getWriter();
        boolean locked = false;

        myShepherd.beginDBTransaction();
        if ((request.getParameter("encounter") != null) && (request.getParameter("uuid") != null) &&
            (request.getParameter("type") != null) &&
            (myShepherd.isEncounter(request.getParameter("encounter"))) &&
            (ServletUtilities.isUserAuthorizedForEncounter(myShepherd.getEncounter(
            request.getParameter("encounter")), request))) {
            Encounter changeMe = myShepherd.getEncounter(request.getParameter("encounter"));
            setDateLastModified(changeMe);
            String type = request.getParameter("type").trim();
            String uuid = request.getParameter("uuid").trim();

            try {
                User user = myShepherd.getUserByUUID(uuid);
                if (user == null) {
                    response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    out.println("{\"error\": \"User not found\"}");
                    myShepherd.rollbackDBTransaction();
                    out.close();
                    myShepherd.closeDBTransaction();
                    return;
                }

                // Verify the user is associated with this encounter in the specified role
                boolean userInRole = false;
                if (type.equals("submitter") && changeMe.getSubmitters() != null && 
                    changeMe.getSubmitters().contains(user)) {
                    userInRole = true;
                } else if (type.equals("photographer") && changeMe.getPhotographers() != null && 
                           changeMe.getPhotographers().contains(user)) {
                    userInRole = true;
                } else if (type.equals("informOther") && changeMe.getInformOthers() != null && 
                           changeMe.getInformOthers().contains(user)) {
                    userInRole = true;
                }

                if (!userInRole) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    out.println("{\"error\": \"User is not associated with this encounter in the specified role\"}");
                    myShepherd.rollbackDBTransaction();
                    out.close();
                    myShepherd.closeDBTransaction();
                    return;
                }

                // Update email address if provided
                if (request.getParameter("emailAddress") != null) {
                    String emailAddress = request.getParameter("emailAddress").trim();
                    String originalEmail = request.getParameter("originalEmail");
                    
                    if (emailAddress.equals("")) {
                        user.setEmailAddress(null);
                    } else {
                        // Check if email is being changed
                        if (originalEmail == null || !originalEmail.trim().equals(emailAddress)) {
                            // Email is being changed, check for uniqueness
                            User existingUser = myShepherd.getUserByEmailAddress(emailAddress);
                            if (existingUser != null && !existingUser.getUUID().equals(user.getUUID())) {
                                // Email is already taken by another user
                                response.setStatus(HttpServletResponse.SC_CONFLICT);
                                out.println("{\"error\": \"Email address is already in use by another user\"}");
                                myShepherd.rollbackDBTransaction();
                                out.close();
                                myShepherd.closeDBTransaction();
                                return;
                            }
                        }
                        user.setEmailAddress(emailAddress);
                    }
                }

                // Update affiliation if provided
                if (request.getParameter("affiliation") != null) {
                    String affiliation = request.getParameter("affiliation").trim();
                    if (affiliation.equals("")) {
                        user.setAffiliation(null);
                    } else {
                        user.setAffiliation(affiliation);
                    }
                }

                // Update full name if provided
                if (request.getParameter("fullName") != null) {
                    String fullName = request.getParameter("fullName").trim();
                    if (fullName.equals("")) {
                        user.setFullName(null);
                    } else {
                        user.setFullName(fullName);
                    }
                }

                changeMe.addComments("<p><em>" + request.getRemoteUser() + " on " +
                    (new java.util.Date()).toString() + "</em><br>Updated user " +
                    user.getUUID() + " of type " + type + ".</p>");

                // Return the updated User object
                String responseJSON = RESTUtils.getJSONObjectFromPOJO(user,
                    ((JDOPersistenceManager)myShepherd.getPM()).getExecutionContext()).toString();
                myShepherd.commitDBTransaction();
                response.setStatus(HttpServletResponse.SC_OK);
                out.println(responseJSON);
            } catch (Exception le) {
                locked = true;
                le.printStackTrace();
                myShepherd.rollbackDBTransaction();
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                out.println("{\"error\": \"" + le.getMessage() + "\"}");
            }
            if (locked) {
                out.println("{\"error\": \"Encounter was NOT updated because another user is currently modifying this record. Please try again in a few seconds.\"}");
            }
        } else {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            myShepherd.rollbackDBTransaction();
            out.println("{\"error\": \"Invalid request parameters\"}");
        }
        out.close();
        myShepherd.closeDBTransaction();
    }
}

