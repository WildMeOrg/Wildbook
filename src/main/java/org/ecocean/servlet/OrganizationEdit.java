/*
 * Wildbook - A Mark-Recapture Framework
 * Copyright (C) 2011-2018 Jason Holmberg
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package org.ecocean.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Collection;
import java.util.Iterator;

import javax.jdo.Query;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ecocean.Shepherd;
import org.ecocean.Annotation;
import org.ecocean.User;
import org.ecocean.Util;
import org.ecocean.Organization;
import org.ecocean.AccessControl;
import org.ecocean.media.MediaAsset;
import org.ecocean.media.MediaAssetFactory;
import org.json.JSONObject;
import org.json.JSONArray;


public class OrganizationEdit extends HttpServlet {
    private static final long serialVersionUID = 1L;

    @Override
    public void init(final ServletConfig config) throws ServletException {
        super.init(config);
    }


    @Override
    public void doGet(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException {
        //doPost(request, response);
        String searchUser = request.getParameter("searchUser");
        String searchOrg = request.getParameter("searchOrg");
        JSONArray rtn = new JSONArray();
        String context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.beginDBTransaction();

        if (searchUser != null) {
            String clean = Util.basicSanitize(searchUser).toLowerCase();
            String jdo = "SELECT FROM org.ecocean.User WHERE username.toLowerCase().matches('.*" + clean + ".*') || fullName.toLowerCase().matches('.*" + clean + ".*')";
            Query query = myShepherd.getPM().newQuery(jdo);
            Collection c = (Collection)query.execute();
            Iterator it = c.iterator();
            while (it.hasNext()) {
                User u = (User)it.next();
                JSONObject uj = new JSONObject();
                uj.put("id", u.getUUID());
                uj.put("fullName", u.getFullName());
                uj.put("username", u.getUsername());
                rtn.put(uj);
            }
            query.closeAll();

        } else if (searchOrg != null) {
            String clean = Util.basicSanitize(searchOrg).toLowerCase();
            String jdo = "SELECT FROM org.ecocean.Organization WHERE name.toLowerCase().matches('.*" + clean + ".*') || description.toLowerCase().matches('.*" + clean + ".*')";
            Query query = myShepherd.getPM().newQuery(jdo);
            Collection c = (Collection)query.execute();
            Iterator it = c.iterator();
            while (it.hasNext()) {
                Organization o = (Organization)it.next();
                JSONObject oj = new JSONObject();
                oj.put("id", o.getId());
                oj.put("name", o.getName());
                rtn.put(oj);
            }
            query.closeAll();

        } else {
            myShepherd.rollbackAndClose();
            response.sendError(401, "access denied");
            return;
        }


        myShepherd.rollbackAndClose();
        PrintWriter out = response.getWriter();
        response.setContentType("text/json");
        out.println(rtn.toString());
        out.close();
    }

    @Override
    public void doPost(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException {
        String context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.beginDBTransaction();
        JSONObject jsonIn = ServletUtilities.jsonFromHttpServletRequest(request);
        JSONObject rtn = new JSONObject("{\"success\": false}");
        PrintWriter out = response.getWriter();
        String orgId = jsonIn.optString("id", null);
        Organization org = Organization.load(orgId, myShepherd);
        User user = AccessControl.getUser(request, myShepherd);

        boolean isAdmin = false;
        if (user != null) isAdmin = myShepherd.doesUserHaveRole(user.getUsername(), "admin", context);

        if (user == null) {
            rtn.put("error", "access denied");
            response.sendError(401, "access denied");

        //only "admin" can create a new **top-level** org, otherwise user must create under an existing one
        //we need to have some sort of OrgSuperUser Role i suppose?   TODO
        } else if ((org == null) && (orgId == null) && isAdmin && (jsonIn.optString("create", null) != null)) {
            Organization newOrg = new Organization(jsonIn.getString("create"));
            newOrg.addMember(user);
            myShepherd.getPM().makePersistent(newOrg);
            rtn.put("success", true);
            rtn.put("newOrg", newOrg.toJSONObject());

        } else if (org == null) {  //from here on out, *must* be in context of some existing Organization
            rtn.put("error", "invalid Organization id=" + orgId);

        } else if (!org.canManage(user, myShepherd)) {
            rtn.put("error", "access denied");
            response.sendError(401, "access denied");

        } else {
            //note: this will kill off the merged(from) org as well!
            if (jsonIn.optString("mergeFrom", null) != null) {
                String mergeFromId = jsonIn.getString("mergeFrom");
                Organization other = Organization.load(mergeFromId, myShepherd);
                if (other == null) {
                    rtn.put("error", "invalid Organization id=" + mergeFromId);
                } else if (!other.canManage(user, myShepherd)) {
                    rtn.put("error", "access denied to " + mergeFromId);
                } else {
                    System.out.println("INFO: OrganizationEdit mergeFrom is deleting " + other.toJSONObject());
                    int added = org.mergeFrom(other);
                    rtn.put("message", "merged " + added + " member(s) from [deleted] id=" + mergeFromId);
                    myShepherd.getPM().deletePersistent(other);
                    rtn.put("success", true);
                }

            } else if (jsonIn.optJSONArray("addUsers") != null) {
                JSONArray uids = jsonIn.getJSONArray("addUsers");
                List<User> newMembers = new ArrayList<User>();
                for (int i = 0 ; i < uids.length() ; i++) {
                    User mem = myShepherd.getUserByUUID(uids.optString(i, "__FAIL__"));
                    if ((mem != null) && !newMembers.contains(mem)) {
                        newMembers.add(mem);
                    }
                }
                int added = org.addMembers(newMembers);
                rtn.put("success", true);
                rtn.put("message", "added " + added + " member(s) to " + org.toString());

            } else if (jsonIn.optJSONArray("removeUsers") != null) {
                boolean deep = jsonIn.optBoolean("deep", false);
                JSONArray uids = jsonIn.getJSONArray("removeUsers");
                int rmCt = 0;
                int orgCt = 0;
                for (int i = 0 ; i < uids.length() ; i++) {
                    String uid = uids.optString(i, null);
                    if (uid == null) continue;
                    User mem = myShepherd.getUserByUUID(uid);
                    if (mem == null) continue;
                    if (deep) {
                        orgCt += org.removeMemberDeep(mem);
                    } else {
                        orgCt++;
                        org.removeMember(mem);
                    }
                    rmCt++;
                }
                rtn.put("success", true);
                rtn.put("deep", deep);
                rtn.put("message", "removed " + rmCt + " member(s) from " + orgCt + " org(s) via " + org.toString());

            } else if (jsonIn.optString("create", null) != null) {
                Organization newOrg = new Organization(jsonIn.getString("create"));
                newOrg.addMember(user);
                org.addChild(newOrg);
                myShepherd.getPM().makePersistent(newOrg);
                rtn.put("success", true);
                rtn.put("newOrg", newOrg.toJSONObject());

            } else if (jsonIn.optString("addChild", null) != null) {
                String kidId = jsonIn.getString("addChild");
                Organization kid = Organization.load(kidId, myShepherd);
                if (kid == null) {
                    rtn.put("error", "invalid Organization id=" + kidId);
                } else if (!kid.canManage(user, myShepherd)) {
                    rtn.put("error", "access denied to " + kidId);
                } else {
                    List<Organization> kids = org.addChild(kid);
                    if (kids == null) {
                        System.out.println("WARNING: failed OrganizationEdit addChild " + kid + " onto " + org);
                        rtn.put("error", "failed to add child -- probably related already");
                    } else {
                        System.out.println("INFO: success OrganizationEdit addChild " + kid + " onto " + org);
                        rtn.put("message", "added " + kid + " to " + org);
                        rtn.put("success", true);
                    }
                }

            } else if (jsonIn.optString("removeChild", null) != null) {
                String kidId = jsonIn.getString("removeChild");
                Organization kid = Organization.load(kidId, myShepherd);
                if (kid == null) {
                    rtn.put("error", "invalid Organization id=" + kidId);
                } else if (!kid.canManage(user, myShepherd)) {
                    rtn.put("error", "access denied to " + kidId);
                } else {
                    boolean ok = org.removeChild(kid);
                    if (ok) {
                        System.out.println("INFO: success OrganizationEdit removeChild " + kid + " from " + org);
                        rtn.put("message", "removed " + kid + " from " + org);
                        rtn.put("success", true);
                    } else {
                        System.out.println("WARNING: failed OrganizationEdit removeChild " + kid + " from " + org);
                        rtn.put("error", "failed to remove child");
                    }
                }
            } else if (jsonIn.optJSONObject("edit") != null) {
                JSONObject ej = jsonIn.getJSONObject("edit");
                boolean changesMade = false;
                String name = ej.optString("name", null);
                String desc = ej.optString("description", null);
                String url = ej.optString("url", null);
                int logoId = ej.optInt("logoMediaAssetId", -1);
                if (name != null) {
                    if (name.equals("")) name = null;  //to set empty
                    org.setName(name);
                    changesMade = true;
                }
                if (desc != null) {
                    if (desc.equals("")) desc = null;
                    org.setDescription(desc);
                    changesMade = true;
                }
                if (url != null) {
                    if (url.equals("")) url = null;
                    org.setUrl(url);
                    changesMade = true;
                }
                if (logoId == 0) {  //reset empty
                    org.setLogo(null);
                    changesMade = true;
                } else if (logoId > 0) {
                    MediaAsset logoMA = MediaAssetFactory.load(logoId, myShepherd);
                    if (logoMA == null) {
                        rtn.put("warning", "logoMediaAssetId invalid id=" + logoId);
                    } else {
                        org.setLogo(logoMA);
                        changesMade = true;
                    }
                }
                if (changesMade) {
                    rtn.put("success", true);
                    rtn.put("org", org.toJSONObject());
                } else {
                    rtn.put("error", "no changes were made");
                }

            } else if (jsonIn.optBoolean("delete", false)) {
                System.out.println("INFO: OrganizationEdit is deleting " + org.toJSONObject());
                myShepherd.getPM().deletePersistent(org);
                rtn.put("success", true);

            } else {
                rtn.put("error", "unknown command");
            }

        }

        if (rtn.optBoolean("success", false)) {
            myShepherd.commitDBTransaction();
        } else {
            myShepherd.rollbackDBTransaction();
        }
        response.setContentType("text/json");
        out.println(rtn);
        out.close();
    }
}
