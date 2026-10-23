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

import org.ecocean.shepherd.core.Shepherd;
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

    static void clearUnownedRoles(Shepherd sh, String username) {
        javax.jdo.Query<?> query = sh.getPM().newQuery(Role.class,
            "username == :username");
        try {
            sh.getPM().deletePersistentAll((java.util.Collection<?>)query.execute(username));
        } finally { query.closeAll(); }
    }

    static boolean usernameAvailable(Shepherd sh, String username, String userId) {
        if (username == null || username.isBlank()) return true;
        javax.jdo.Query<?> query = sh.getPM().newQuery(User.class, "username == :username && uuid != :id");
        try {
            query.setResult("count(this)");
            return ((Number)query.execute(username, userId)).longValue() == 0;
        } finally { query.closeAll(); }
    }

    static void preserveSubmissionRole(List<Role> rolesToReplace, String username, boolean siteAdmin) {
        rolesToReplace.removeIf(role -> {
            if (Role.canEditRole(role.getRolename(), siteAdmin)) return false;
            role.setUsername(username);
            return true;
        });
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
                    boolean siteAdmin = request.isUserInRole("admin");
                    User reqUser = myShepherd.getUser(request);
                    boolean existingUser = myShepherd.getUserByUUID(uuid) != null;
                    if (existingUser) {
                        newUser = myShepherd.getUserByUUID(uuid);
                        originalUsername = newUser.getUsername();
                    } else {
                        newUser = new User(uuid);
                    }
                    if (!canEditServiceAccount(siteAdmin, newUser)) {
                        response.sendError(HttpServletResponse.SC_FORBIDDEN);
                        myShepherd.rollbackDBTransaction();
                        return;
                    }
                    if (existingUser && !canEditExistingUser(myShepherd, reqUser, siteAdmin, newUser)) {
                        response.sendError(HttpServletResponse.SC_FORBIDDEN);
                        myShepherd.rollbackDBTransaction();
                        return;
                    }
                    // requested organizations are checked before anything is persisted
                    List<Organization> selectedOrgs = parseOrganizations(myShepherd,
                        request.getParameterValues("organization"));
                    if (!canAddOrganizations(myShepherd, reqUser, siteAdmin, selectedOrgs)) {
                        response.sendError(HttpServletResponse.SC_FORBIDDEN);
                        myShepherd.rollbackDBTransaction();
                        return;
                    }
                    if (username != null) username = username.trim();
                    if (!usernameAvailable(myShepherd, username, uuid)) {
                        response.sendError(HttpServletResponse.SC_CONFLICT);
                        myShepherd.rollbackDBTransaction();
                        return;
                    }
                    if (username != null && !username.equals(originalUsername)) {
                        clearUnownedRoles(myShepherd, username);
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
                            // GH-1545: use the (email, uuid) ctor so the email is not stored as the UUID.
                            // The single-arg User(String uuid) ctor was misused here, producing
                            // accounts whose primary key is the email address.
                            newUser = new User(email, Util.generateUUID());
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
                        preexistingRoles = originalUsername == null ? new ArrayList<Role>()
                            : myShepherd.getAllRolesForUser(originalUsername);
                        // Keep administrator-managed enrollment on unrelated non-admin edits.
                        preserveSubmissionRole(preexistingRoles, newUser.getUsername(), request.isUserInRole("admin"));
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
                                if (!thisRole.trim().equals("") && Role.canEditRole(thisRole, request.isUserInRole("admin"))) {
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

                    // handle org requests (parsed and checked above)
                    for (Organization org : selectedOrgs) {
                        // OK - add to new organizations
                        if (!preexistingOrgs.contains(org)) {
                            org.addMember(newUser);
                            myShepherd.commitDBTransaction();
                            myShepherd.beginDBTransaction();
                        }
                    }

                    // OK - remove to no longer selected orgs by seeing what the requesting user could have requested but didn't.

                    // possible set the orgAdmin could have set
                    List<Organization> reqOrgs = new ArrayList<Organization>();
                    if (myShepherd.getUser(request) != null) {
                        User user = myShepherd.getUser(request);
                        if (siteAdmin) {
                            reqOrgs = myShepherd.getAllOrganizations();
                        } else {
                            reqOrgs = myShepherd.getAllOrganizationsForUser(user);
                        }
                    }
                    // whittle down to those entries where the User could have been added by reqUser but wasn't intentionally
                    reqOrgs.removeAll(selectedOrgs);
                    for (Organization rOrg : reqOrgs) {
                        // for each org the requesting user could have selected for this user but didn't, remove this user from that org
                        if (rOrg.hasMember(newUser)) {
                            rOrg.removeMember(newUser);
                        }
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
                            String tmpUsr1Password = Util.generateUUID(); // random and discarded; this user will be gone very soon
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

    // service accounts (per-organization API identities) may only be edited by site admins
    static boolean canEditServiceAccount(boolean requesterIsSiteAdmin, User target) {
        return requesterIsSiteAdmin || target == null || !target.isServiceAccount();
    }

    private static final String[] PRIVILEGED_ROLES = {
        "admin", Organization.ROLE_MANAGER, Organization.ROLE_ADMIN
    };

    /**
     * Site admins may edit any existing user, and anyone may edit their own account (matched by
     * account id). Others may edit a user only when that user holds no admin or
     * organization-management role in any context and shares a direct organization with them.
     * Role grants, organization additions and service-account edits are checked separately.
     */
    static boolean canEditExistingUser(Shepherd sh, User requester, boolean requesterIsSiteAdmin,
        User target) {
        if (requesterIsSiteAdmin) return true;
        if (requester == null || target == null) return false;
        if (requester.getId() != null && requester.getId().equals(target.getId())) return true;
        if (holdsPrivilegedRole(sh, target.getUsername())) return false;
        List<String> requesterOrgIds = organizationIds(sh.getAllOrganizationsForUser(requester));
        for (Organization org : sh.getAllOrganizationsForUser(target)) {
            if (requesterOrgIds.contains(org.getId())) return true;
        }
        return false;
    }

    /** Site admins may add any organization; others only organizations they are a direct member of. */
    static boolean canAddOrganizations(Shepherd sh, User requester, boolean requesterIsSiteAdmin,
        List<Organization> requested) {
        if (requesterIsSiteAdmin || requested == null || requested.isEmpty()) return true;
        if (requester == null) return false;
        List<String> requesterOrgIds = organizationIds(sh.getAllOrganizationsForUser(requester));
        for (Organization org : requested) {
            if (org == null || !requesterOrgIds.contains(org.getId())) return false;
        }
        return true;
    }

    // persisted grants in every context, read fresh from the datastore
    private static boolean holdsPrivilegedRole(Shepherd sh, String username) {
        if (username == null || username.isBlank()) return false;
        javax.jdo.Query<?> query = sh.getPM().newQuery(Role.class,
            "username == :username && (rolename == :a || rolename == :b || rolename == :c)");
        try {
            query.setIgnoreCache(true);
            query.setResult("count(this)");
            Object n = query.executeWithArray(username, PRIVILEGED_ROLES[0], PRIVILEGED_ROLES[1],
                PRIVILEGED_ROLES[2]);
            return ((Number)n).longValue() > 0;
        } finally { query.closeAll(); }
    }

    private static List<String> organizationIds(List<Organization> orgs) {
        List<String> ids = new ArrayList<String>();
        for (Organization org : orgs) ids.add(org.getId());
        return ids;
    }

    // existing organizations named by the request; blank and unknown ids are ignored
    static List<Organization> parseOrganizations(Shepherd sh, String[] orgIds) {
        List<Organization> orgs = new ArrayList<Organization>();
        if (orgIds == null) return orgs;
        for (String orgId : orgIds) {
            if (orgId == null || orgId.trim().equals("")) continue;
            Organization org = sh.getOrganization(orgId.trim());
            if (org != null && !orgs.contains(org)) orgs.add(org);
        }
        return orgs;
    }
}
