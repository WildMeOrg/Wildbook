package org.ecocean.servlet;

import org.ecocean.CommonConfiguration;
import org.ecocean.Encounter;
import org.ecocean.Shepherd;

import javax.jdo.Query;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Iterator;

// handles operations to sharks. possible operations include, create new, add encounter, remove encounter from
public class MassSwapLocationCode extends HttpServlet {
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

        // set up for response
        response.setContentType("text/html");
        PrintWriter out = response.getWriter();
        boolean locked = false;
        boolean madeChanges = false;
        int count = 0;
        String newLocCode = "", oldLocCode = "";
        oldLocCode = request.getParameter("oldLocCode");
        newLocCode = request.getParameter("newLocCode");
        if ((oldLocCode != null) && (!oldLocCode.trim().equals("")) && (newLocCode != null) &&
            (!newLocCode.equals(""))) {
            oldLocCode = oldLocCode.trim();
            newLocCode = newLocCode.trim();
            Shepherd myShepherd = new Shepherd(context);
            myShepherd.setAction("MassSwapLocationCode.class");
            myShepherd.beginDBTransaction();
            Query query = myShepherd.getPM().newQuery(
                "SELECT FROM org.ecocean.Encounter WHERE ( locationID == \"" + oldLocCode + "\" )");
            try {
                Iterator<Encounter> it = myShepherd.getAllEncounters(query);
                while (it.hasNext()) {
                    Encounter tempEnc = it.next();
                    if (tempEnc.getLocationCode().equals(oldLocCode)) {
                        tempEnc.setLocationCode(newLocCode);
                        myShepherd.commitDBTransaction();
                        myShepherd.beginDBTransaction();
                        madeChanges = true;
                        count++;
                    }
                } // end while
            } catch (Exception le) {
                locked = true;
            } finally {
                myShepherd.rollbackDBTransaction();
                myShepherd.closeDBTransaction();
                query.closeAll();
            }
            if (!locked) {
                out.println(ServletUtilities.getHeader(request));
                out.println(
                    ("<strong>Success!</strong> I have successfully changed the location code " +
                    oldLocCode + " to " + newLocCode + " for " + count + " encounters."));
                out.println("<p><a href=\"" + request.getScheme() + "://" +
                    CommonConfiguration.getURLLocation(request) +
                    "/appadmin/admin.jsp\">Return to the Administration page.</a></p>\n");
                out.println(ServletUtilities.getFooter(context));
            }
            // failure due to exception
            else {
                out.println(ServletUtilities.getHeader(request));
                out.println(
                    "<strong>Failure!</strong> An encounter is currently being modified by another user. Please wait a few seconds before trying to remove this data file again.");
                out.println(ServletUtilities.getFooter(context));
            }
        } else {
            out.println(ServletUtilities.getHeader(request));
            out.println(
                "<strong>Error:</strong> I was unable to set the location code as requested due to missing parameter values.");
            out.println(ServletUtilities.getFooter(context));
        }
        out.close();
    }
}
