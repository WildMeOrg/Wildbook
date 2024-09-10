package org.ecocean.servlet;

import org.ecocean.MarkedIndividual;
import org.ecocean.Shepherd;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

import java.io.IOException;
import java.io.PrintWriter;

public class IndividualRemoveDataFile extends HttpServlet {
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
        myShepherd.setAction("IndividualRemoveDataFile.class");
        // set up for response
        response.setContentType("text/html");
        PrintWriter out = response.getWriter();
        boolean locked = false;
        String fileName = "None";
        String individualName = "None";

        fileName = request.getParameter("filename").replaceAll("%20", " ");
        individualName = request.getParameter("individual");
        myShepherd.beginDBTransaction();
        if ((request.getParameter("individual") != null) &&
            (request.getParameter("filename") != null) &&
            (myShepherd.isMarkedIndividual(individualName))) {
            MarkedIndividual sharky = myShepherd.getMarkedIndividual(individualName);
            int positionInList = 0;
            try {
                sharky.removeDataFile(fileName);
                sharky.addComments("<p><em>" + request.getRemoteUser() + " on " +
                    (new java.util.Date()).toString() + "</em><br>" + "Removed " + individualName +
                    " data file: " + fileName + ".</p>");
            } catch (Exception le) {
                locked = true;
                myShepherd.rollbackDBTransaction();
            }
            if (!locked) {
                myShepherd.commitDBTransaction();
                // out.println(ServletUtilities.getHeader(request));
                response.setStatus(HttpServletResponse.SC_OK);
                out.println(
                    "<strong>Success!</strong> I have successfully removed the data file. When returning to the individual's page, please make sure to refresh your browser to see the changes. Changes may not be visible until you have done so.");

                // out.println("<p><a href=\""+request.getScheme()+"://" + CommonConfiguration.getURLLocation(request) + "/individuals.jsp?number=" +
                // individualName + "\">Return to " + individualName + "</a></p>\n");
                // out.println(ServletUtilities.getFooter(context));
            } else {
                // out.println(ServletUtilities.getHeader(request));
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.println(
                    "<strong>Failure!</strong> This record is currently being modified by another user. Please wait a few seconds before trying to remove this data file again.");

                // out.println("<p><a href=\""+request.getScheme()+"://" + CommonConfiguration.getURLLocation(request) + "/individuals.jsp?number=" +
                // individualName + "\">Return to " + individualName + "</a></p>\n");
                // out.println(ServletUtilities.getFooter(context));
            }
        } else {
            myShepherd.rollbackDBTransaction();
            // out.println(ServletUtilities.getHeader(request));
            out.println(
                "<strong>Error:</strong> I was unable to remove your data file. I cannot find the record that you intended it for in the database, or I wasn't sure what file you wanted to remove.");
            // out.println(ServletUtilities.getFooter(context));
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        }
        out.close();
        myShepherd.closeDBTransaction();
    }
}
