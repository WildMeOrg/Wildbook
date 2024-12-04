package org.ecocean.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Iterator;

import javax.jdo.Query;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

import org.ecocean.Shepherd;
import org.ecocean.User;
import org.ecocean.Util;
import org.json.JSONArray;
import org.json.JSONObject;

public class UserGetSimpleJSON extends HttpServlet {
    private static final long serialVersionUID = 1L;

    @Override public void init(final ServletConfig config)
    throws ServletException {
        super.init(config);
    }

    @Override public void doPost(final HttpServletRequest request,
        final HttpServletResponse response)
    throws ServletException, IOException {
        doGet(request, response);
    }

    @Override public void doGet(final HttpServletRequest request,
        final HttpServletResponse response)
    throws ServletException, IOException {
        String searchUser = request.getParameter("searchUser");
        JSONArray rtn = new JSONArray();
        String context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);

        myShepherd.beginDBTransaction();
        // this user search is duplicated in OrganizationEdit, but there was a permission conflict
        if (searchUser != null) {
            String clean = Util.basicSanitize(searchUser).toLowerCase();
            String jdo = "SELECT FROM org.ecocean.User WHERE username.toLowerCase().matches('.*" +
                clean + ".*') || fullName.toLowerCase().matches('.*" + clean + ".*')";
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
}
