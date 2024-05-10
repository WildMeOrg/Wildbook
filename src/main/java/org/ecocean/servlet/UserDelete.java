package org.ecocean.servlet;

import org.ecocean.*;

import java.util.List;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

import java.io.*;

public class UserDelete extends HttpServlet {
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
        // context=ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);

        myShepherd.setAction("UserDelete.class");
        // set up for response
        response.setContentType("text/html");
        PrintWriter out = response.getWriter();
        boolean locked = false;

        myShepherd.beginDBTransaction();
        // if ((request.getParameter("uuid")!=null)&&(myShepherd.getUserByUUID(request.getParameter("uuid"))!=null)) {
        if (request.getParameter("uuid") != null &&
            myShepherd.getUserByUUID(request.getParameter("uuid")) != null &&
            request.getUserPrincipal().getName() != null &&
            myShepherd.getUsername(request) != null &&
            myShepherd.getUser(myShepherd.getUsername(request)) != null
            // to delete a user either be admin or orgAdmin in at least one of the same orgs
            && (request.isUserInRole("admin") ||
            (request.isUserInRole("orgAdmin") &&
            myShepherd.getAllCommonOrganizationsForTwoUsers(myShepherd.getUserByUUID(
            request.getParameter("uuid")),
            myShepherd.getUser(myShepherd.getUsername(request))).size() > 0))) {
            try {
                User ad = myShepherd.getUserByUUID(request.getParameter("uuid"));
                // first delete the roles
                if (ad.getUsername() != null) {
                    List<Role> roles = myShepherd.getAllRolesForUser(ad.getUsername());
                    int numRoles = roles.size();
                    for (int i = 0; i < numRoles; i++) {
                        Role r = roles.get(i);
                        // if(myShepherd.getUser(r.getUsername())!=null){
                        myShepherd.getPM().deletePersistent(r);
                        myShepherd.commitDBTransaction();
                        myShepherd.beginDBTransaction();
                        // }
                    }
                }
                // remove the User from Encounters
                List<Encounter> encs = myShepherd.getEncountersForSubmitter(ad);
                for (int l = 0; l < encs.size(); l++) {
                    Encounter enc = encs.get(l);
                    List<User> peeps = enc.getSubmitters();
                    peeps.remove(ad);
                    enc.setSubmitters(peeps);
                    myShepherd.commitDBTransaction();
                    myShepherd.beginDBTransaction();
                }
                encs = myShepherd.getEncountersForPhotographer(ad, myShepherd);
                for (int l = 0; l < encs.size(); l++) {
                    Encounter enc = encs.get(l);
                    List<User> peeps = enc.getPhotographers();
                    peeps.remove(ad);
                    enc.setPhotographers(peeps);
                    myShepherd.commitDBTransaction();
                    myShepherd.beginDBTransaction();
                }
                // now delete the user
                myShepherd.getPM().deletePersistent(ad);
                myShepherd.commitDBTransaction();
            } catch (Exception le) {
                locked = true;
                le.printStackTrace();
                myShepherd.rollbackDBTransaction();
            } finally {
                myShepherd.closeDBTransaction();
            }
            if (!locked) {
                // myShepherd.commitDBTransaction();
                // myShepherd.closeDBTransaction();
                out.println(ServletUtilities.getHeader(request));
                out.println("<strong>Success!</strong> I have successfully removed user account '" +
                    request.getParameter("uuid") + "'.");

                out.println("<p><a href=\"" + request.getScheme() + "://" +
                    CommonConfiguration.getURLLocation(request) +
                    "/appadmin/users.jsp?context=context0" + "\">Return to User Administration" +
                    "</a></p>\n");
                out.println(ServletUtilities.getFooter(context));
            } else {
                out.println(ServletUtilities.getHeader(request));
                out.println(
                    "<strong>Failure!</strong> I failed to delete this user account. Check the logs for more details.");

                out.println("<p><a href=\"" + request.getScheme() + "://" +
                    CommonConfiguration.getURLLocation(request) +
                    "/appadmin/users.jsp?context=context0" + "\">Return to User Administration" +
                    "</a></p>\n");
                out.println(ServletUtilities.getFooter(context));
            }
        } else {
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
            out.println(ServletUtilities.getHeader(request));
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.println(
                "<strong>Error:</strong> I was unable to remove the user account. I cannot find the user in the database.");
            out.println(ServletUtilities.getFooter(context));
        }
        out.close();
    }
}
