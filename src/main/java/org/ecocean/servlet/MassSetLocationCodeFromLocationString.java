package org.ecocean.servlet;

import org.ecocean.CommonConfiguration;
import org.ecocean.Encounter;
import org.ecocean.Shepherd;

import javax.jdo.Extent;
import javax.jdo.Query;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Iterator;

public class MassSetLocationCodeFromLocationString extends HttpServlet {
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
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("MassSetLocationCodeFromLocationString.class");

        // set up for response
        response.setContentType("text/html");
        PrintWriter out = response.getWriter();
        boolean locked = false;
        int count = 0;
        String locCode = "", matchString = "";
        matchString = request.getParameter("matchString").toLowerCase();
        locCode = request.getParameter("locCode");
        if ((locCode != null) && (matchString != null) && (!matchString.equals("")) &&
            (!locCode.equals(""))) {
            Extent encClass = myShepherd.getPM().getExtent(Encounter.class, true);
            Query query = myShepherd.getPM().newQuery(encClass);
            myShepherd.beginDBTransaction();
            try {
                Iterator<Encounter> it = myShepherd.getAllEncounters(query);
                while (it.hasNext()) {
                    Encounter tempEnc = it.next();
                    if ((tempEnc.getLocation() != null) &&
                        (tempEnc.getLocation().toLowerCase().indexOf(matchString) != -1)) {
                        tempEnc.setLocationCode(locCode);
                        tempEnc.addComments("<p><em>" + request.getRemoteUser() + " on " +
                            (new java.util.Date()).toString() + "</em><br>" + "Set location ID: " +
                            locCode + ".");
                        myShepherd.commitDBTransaction();
                        myShepherd.beginDBTransaction();
                        count++;
                    }
                } // end while
            } catch (Exception le) {
                locked = true;
            } finally {
                query.closeAll();
                myShepherd.rollbackDBTransaction();
                myShepherd.closeDBTransaction();
            }
            if (!locked) {
                // myShepherd.commitDBTransaction();
                // myShepherd.closeDBTransaction();
                out.println(ServletUtilities.getHeader(request));
                out.println(
                    ("<strong>Success!</strong> I have successfully changed the location code to " +
                    locCode + " for " + count + " encounters based on the location string: " +
                    matchString + "."));
                out.println("<p><a href=\"" + request.getScheme() + "://" +
                    CommonConfiguration.getURLLocation(request) +
                    "/appadmin/admin.jsp\">Return to the Administration page.</a></p>\n");
                out.println(ServletUtilities.getFooter(context));
            }
            // failure due to exception
            else {
                out.println(ServletUtilities.getHeader(request));
                out.println(
                    "<strong>Failure!</strong> An encounter is currently being modified by another user. Please wait a few seconds before trying to execute this operation again.");
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
