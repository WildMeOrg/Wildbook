package org.ecocean.servlet;

import org.apache.commons.lang3.StringEscapeUtils;
import org.ecocean.*;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;

public class UserCreate extends HttpServlet {
    public void init(ServletConfig config)
    throws ServletException {
        super.init(config);
    }

    public void doGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        doPost(request, response);
    }

    private void addErrorMessage(JSONObject res, String error) {
        res.put("error", error);
    }

    public void doPost(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        String context = "context0";
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("UserCreate.class");

        // set up the user directory
        // setup data dir
        String rootWebappPath = getServletContext().getRealPath("/");
        File webappsDir = new File(rootWebappPath).getParentFile();
        File shepherdDataDir = new File(webappsDir,
            CommonConfiguration.getDataDirectoryName(context));
        if (!shepherdDataDir.exists()) { shepherdDataDir.mkdirs(); }
        File usersDir = new File(shepherdDataDir.getAbsolutePath() + "/users");
        if (!usersDir.exists()) { usersDir.mkdirs(); }
        // set up for response
        response.setContentType("text/html; charset=UTF-8");
        PrintWriter out = response.getWriter();
        boolean createThisUser = false;
        String addedRoles = "";
        boolean isEdit = false;
        if (request.getParameter("isEdit") != null) {
            isEdit = true;
            // System.out.println("isEdit is TRUE in UserCreate!");
        }
        // create a new Role from an encounter
        if ((request.getParameter("uuid") != null) &&
            (!request.getParameter("uuid").trim().equals(""))) {
            String uuid = request.getParameter("uuid");
            String username = null;
            if (request.getParameter("username") != null) {
                username = request.getParameter("username");
            }
            String email = null;
            if ((request.getParameter("emailAddress") != null) &&
                (!request.getParameter("emailAddress").trim().equals(""))) {
                email = request.getParameter("emailAddress").trim();
            }
            String password = "";
            if ((request.getParameter("password") != null) &&
                (!request.getParameter("password").trim().equals("")))
                password = request.getParameter("password").trim();
            String password2 = "";
            if ((request.getParameter("password2") != null) &&
                (!request.getParameter("password2").trim().equals("")))
                password2 = request.getParameter("password2").trim();
            if ((password.equals(password2)) || (isEdit)) {
                User newUser = null;
                String originalUsername = null;
                try {
                    myShepherd.beginDBTransaction();
                    if (myShepherd.getUserByUUID(uuid) != null) {
                        newUser = myShepherd.getUserByUUID(uuid);
                        originalUsername = newUser.getUsername();
                    } else {
                        newUser = new User(uuid);
                    }
                    if (myShepherd.getUserByUUID(uuid) == null) {
                        // new User
                        // System.out.println("hashed password: "+hashedPassword+" with salt "+salt + " from source password "+password);
                        if ((username != null) && (!password.equals(""))) {
                            String salt = ServletUtilities.getSalt().toHex();
                            String hashedPassword = ServletUtilities.hashAndSaltPassword(password,
                                salt);
                            newUser = new User(username, hashedPassword, salt);
                        } else {
                            newUser = new User(email);
                        }
                        myShepherd.getPM().makePersistent(newUser);
                        createThisUser = true;
                    } else {
                        newUser = myShepherd.getUserByUUID(uuid);
                        if ((!password.equals("")) & (password.equals(password2))) {
                            String salt = ServletUtilities.getSalt().toHex();
                            String hashedPassword = ServletUtilities.hashAndSaltPassword(password,
                                salt);
                            newUser.setPassword(hashedPassword);
                            newUser.setSalt(salt);
                        }
                    }
                    // here handle all of the other User fields (e.g., email address, etc.)
                    if ((request.getParameter("username") != null) &&
                        (!request.getParameter("username").trim().equals(""))) {
                        newUser.setUsername(request.getParameter("username").trim());
                    } else if (isEdit && (request.getParameter("username") != null) &&
                        (request.getParameter("username").trim().equals(""))) {
                        newUser.setUsername(null);
                    }
                    if ((request.getParameter("fullName") != null) &&
                        (!request.getParameter("fullName").trim().equals(""))) {
                        newUser.setFullName(request.getParameter("fullName").trim());
                    } else if (isEdit && (request.getParameter("fullName") != null) &&
                        (request.getParameter("fullName").trim().equals(""))) {
                        newUser.setFullName(null);
                    }
                    if (request.getParameter("receiveEmails") != null) {
                        newUser.setReceiveEmails(true);
                    } else { newUser.setReceiveEmails(false); }
                    if ((request.getParameter("emailAddress") != null) &&
                        (!request.getParameter("emailAddress").trim().equals(""))) {
                        newUser.setEmailAddress(request.getParameter("emailAddress").trim());
                    } else if (isEdit && (request.getParameter("emailAddress") != null) &&
                        (request.getParameter("emailAddress").trim().equals(""))) {
                        newUser.setEmailAddress(null);
                    }
                    if ((request.getParameter("affiliation") != null) &&
                        (!request.getParameter("affiliation").trim().equals(""))) {
                        newUser.setAffiliation(request.getParameter("affiliation").trim());
                    } else if (isEdit && (request.getParameter("affiliation") != null) &&
                        (request.getParameter("affiliation").trim().equals(""))) {
                        newUser.setAffiliation(null);
                    }
                    if ((request.getParameter("userProject") != null) &&
                        (!request.getParameter("userProject").trim().equals(""))) {
                        newUser.setUserProject(request.getParameter("userProject").trim());
                    } else if (isEdit && (request.getParameter("userProject") != null) &&
                        (request.getParameter("userProject").trim().equals(""))) {
                        newUser.setUserProject(null);
                    }
                    if ((request.getParameter("userStatement") != null) &&
                        (!request.getParameter("userStatement").trim().equals(""))) {
                        newUser.setUserStatement(request.getParameter("userStatement").trim());
                    } else if (isEdit && (request.getParameter("userStatement") != null) &&
                        (request.getParameter("userStatement").trim().equals(""))) {
                        newUser.setUserStatement(null);
                    }
                    if ((request.getParameter("userURL") != null) &&
                        (!request.getParameter("userURL").trim().equals(""))) {
                        newUser.setUserURL(request.getParameter("userURL").trim());
                    } else if (isEdit && (request.getParameter("userURL") != null) &&
                        (request.getParameter("userURL").trim().equals(""))) {
                        newUser.setUserURL(null);
                    }
                    newUser.RefreshDate();

                    // now handle roles
                    // if this is not a new user, we need to blow away all old roles
                    List<Role> preexistingRoles = new ArrayList<Role>();
                    if (!createThisUser) {
                        // get existing roles for this existing user
                        preexistingRoles = myShepherd.getAllRolesForUser(username);
                        myShepherd.getPM().deletePersistentAll(preexistingRoles);
                    }
                    // start role processing

                    List<String> contexts = ContextConfiguration.getContextNames();
                    int numContexts = contexts.size();
                    // System.out.println("numContexts is: "+numContexts);
                    for (int d = 0; d < numContexts; d++) {
                        String[] roles = request.getParameterValues("context" + d + "rolename");
                        if (roles != null) {
                            int numRoles = roles.length;
                            // System.out.println("numRoles in context"+d+" is: "+numRoles);
                            for (int i = 0; i < numRoles; i++) {
                                String thisRole = roles[i].trim();
                                if (!thisRole.trim().equals("")) {
                                    Role role = new Role();
                                    if (myShepherd.getRole(thisRole, username,
                                        ("context" + d)) == null) {
                                        role.setRolename(thisRole);
                                        role.setUsername(username);
                                        role.setContext("context" + d);
                                        myShepherd.getPM().makePersistent(role);
                                        addedRoles += ("SEPARATORSTART" + roles[i] +
                                            "SEPARATOREND");
                                        // System.out.println(addedRoles);
                                        myShepherd.commitDBTransaction();
                                        myShepherd.beginDBTransaction();
                                        // System.out.println("Creating role: context"+d+thisRole);
                                    }
                                }
                            }
                        }
                    }
                    // end role processing

                    // now handle organizations

                    // current list of orgs for the user
                    List<Organization> preexistingOrgs = new ArrayList<Organization>();
                    if (!createThisUser) {
                        preexistingOrgs = myShepherd.getAllOrganizationsForUser(newUser);
                    }
                    User reqUser = myShepherd.getUser(request);

                    // handle org requests
                    String[] orgs = request.getParameterValues("organization");
                    ArrayList<Organization> selectedOrgs = new ArrayList<Organization>();
                    if (orgs != null) {
                        int numOrgs = orgs.length;
                        // System.out.println("numRoles in context"+d+" is: "+numRoles);
                        for (int i = 0; i < numOrgs; i++) {
                            String thisOrg = orgs[i].trim();
                            if (!thisOrg.trim().equals("")) {
                                if (myShepherd.getOrganization(thisOrg) != null) {
                                    Organization org = myShepherd.getOrganization(thisOrg);
                                    selectedOrgs.add(org);
                                    // OK - add to new organizations
                                    if (!preexistingOrgs.contains(org)) {
                                        org.addMember(newUser);
                                        myShepherd.commitDBTransaction();
                                        myShepherd.beginDBTransaction();
                                    }
                                }
                            }
                        }
                    } // end if orgs==null

                    // OK - remove to no longer selected orgs by seeing what the requesting user could have requested but didn't.

                    // possible set the orgAdmin could have set
                    List<Organization> reqOrgs = new ArrayList<Organization>();
                    if (myShepherd.getUser(request) != null) {
                        User user = myShepherd.getUser(request);
                        if (request.isUserInRole("admin")) {
                            reqOrgs = myShepherd.getAllOrganizations();
                        } else {
                            reqOrgs = myShepherd.getAllOrganizationsForUser(user);
                        }
                    }
                    // whittle down to those entries where the User could have been added by reqUser but wasn't intentionally
                    reqOrgs.removeAll(selectedOrgs);
                    for (Organization rOrg : reqOrgs) {
                        // for each org the requesting user could have selected for this user but didn't, remove this user from that org
                        rOrg.removeMember(newUser);
                    }
                    // output success statement
                    out.println(ServletUtilities.getHeader(request));
                    if (createThisUser) {
                        out.println("<strong>Success:</strong> User '" +
                            StringEscapeUtils.escapeHtml4(username) +
                            "' was successfully created with added roles: <ul>" +
                            addedRoles.replaceAll("SEPARATORSTART",
                            "<li>").replaceAll("SEPARATOREND", "</li>") + "</ul>");
                    } else {
                        out.println("<strong>Success:</strong> User '" +
                            StringEscapeUtils.escapeHtml4(username) +
                            "' was successfully updated and has assigned roles: <ul>" +
                            addedRoles.replaceAll("SEPARATORSTART",
                            "<li>").replaceAll("SEPARATOREND", "</li>") + "</ul>");
                    }
                    out.println("<p><a href=\"" + request.getScheme() + "://" +
                        CommonConfiguration.getURLLocation(request) +
                        "/appadmin/users.jsp?context=context0" +
                        "\">Return to User Administration" + "</a></p>\n");
                    out.println(ServletUtilities.getFooter(context));

                    myShepherd.updateDBTransaction();

                    // now that the new or updated user data is persisted, if the username changed, let's consolidate all of the stuff that belonged
                    // to the *old* username (if exists) into the new version of the user by consolidating a user with the username of
                    // originalUsername into the newUser. This action both exploits and closes (in the case of edited usernames, at least) the
                    // security flaw that a user can be created and inherit already-existing assets if it happens to have the username of the assets'
                    // [previous] owner.
                    User tempUserWithOriginalUserName = null;
                    if (originalUsername != null && !originalUsername.equals("") &&
                        newUser.getUsername() != null &&
                        !newUser.getUsername().equals(originalUsername)) {
                        try {
                            String tmpUsrSalt = ServletUtilities.getSalt().toHex();
                            String tmpUsr1Password = "tomcat123"; // it does not matter; this user will be gone very soon
                            String tmpUsr1HashedPassword = ServletUtilities.hashAndSaltPassword(
                                tmpUsr1Password, tmpUsrSalt);
                            tempUserWithOriginalUserName = new User(originalUsername,
                                tmpUsr1HashedPassword, tmpUsrSalt);                                     // this user is now magically associated with
                                                                                                        // encounters with submitterId of
                                                                                                        // originalUsername
                            myShepherd.getPM().makePersistent(tempUserWithOriginalUserName);
                            UserConsolidate.consolidateUserForUserEdit(myShepherd, newUser,
                                tempUserWithOriginalUserName);
                        } catch (Exception e) {
                            System.out.println(
                                "error while trying to assign the original user's data to the user account with the new edits");
                            e.printStackTrace();
                        }
                    }
                } catch (Exception e) {
                    myShepherd.rollbackDBTransaction();
                    e.printStackTrace();
                } finally {
                    myShepherd.closeDBTransaction();
                    myShepherd = null;
                }
            } else {
                // output failure statement
                out.println(ServletUtilities.getHeader(request));
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.println(
                    "<strong>Failure:</strong> User was NOT successfully created. Your passwords did not match.");
                out.println("<p><a href=\"" + request.getScheme() + "://" +
                    CommonConfiguration.getURLLocation(request) +
                    "/appadmin/users.jsp?context=context0" + "\">Return to User Administration" +
                    "</a></p>\n");
                out.println(ServletUtilities.getFooter(context));
            }
        } else {
            // output failure statement
            out.println(ServletUtilities.getHeader(request));
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.println(
                "<strong>Failure:</strong> User was NOT successfully created. I did not have all of the username and password information I needed.");
            out.println("<p><a href=\"" + request.getScheme() + "://" +
                CommonConfiguration.getURLLocation(request) +
                "/appadmin/users.jsp?context=context0" + "\">Return to User Administration" +
                "</a></p>\n");
            out.println(ServletUtilities.getFooter(context));
        }
        out.close();
    }
}
