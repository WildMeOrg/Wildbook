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

/**
 * Exposes all approved encounters to the GBIF.
 *
 * @author jholmber
 */
public class MassExposeGBIF extends HttpServlet {
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
        myShepherd.setAction("MassExposeGBIF.class");

        // set up for response
        response.setContentType("text/html");
        PrintWriter out = response.getWriter();
        boolean locked = false;
        boolean madeChanges = false;
        int count = 0;
        Extent encClass = myShepherd.getPM().getExtent(Encounter.class, true);
        Query query = myShepherd.getPM().newQuery(encClass);

        myShepherd.beginDBTransaction();
        try {
            Iterator<Encounter> it = myShepherd.getAllEncounters(query);
            while (it.hasNext()) {
                Encounter tempEnc = it.next();
                if (!tempEnc.getOKExposeViaTapirLink()) {
                    tempEnc.setOKExposeViaTapirLink(true);
                    madeChanges = true;
                    count++;
                }
            } // end while
        } catch (Exception le) {
            locked = true;
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
        }
        query.closeAll();
        if (!madeChanges) {
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
        }
        // success!!!!!!!!
        else if (!locked) {
            myShepherd.commitDBTransaction();
            myShepherd.closeDBTransaction();
            out.println(ServletUtilities.getHeader(request));
            out.println(("<strong>Success!</strong> I have successfully exposed " + count +
                " additional encounters to the GBIF."));
            out.println("<p><a href=\"" + request.getScheme() + "://" +
                CommonConfiguration.getURLLocation(request) +
                "/appadmin/admin.jsp\">Return to the Administration page.</a></p>\n");
            out.println(ServletUtilities.getFooter(context));
        }
        // failure due to exception
        else {
            out.println(ServletUtilities.getHeader(request));
            out.println(
                "<strong>Failure!</strong> I could not change the GBIF status of unexposed encounters.");
            out.println(ServletUtilities.getFooter(context));
        }
        out.close();
    }
}
