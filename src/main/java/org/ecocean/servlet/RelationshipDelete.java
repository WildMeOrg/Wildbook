package org.ecocean.servlet;

import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.social.*;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

import java.io.IOException;
import java.io.PrintWriter;

public class RelationshipDelete extends HttpServlet {
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
        // set up for response
        response.setContentType("text/html");
        PrintWriter out = response.getWriter();
        String context = "context0";
        context = ServletUtilities.getContext(request);
        if ((request.getParameter("persistenceID") != null) &&
            (!request.getParameter("persistenceID").equals(""))) {
            Shepherd myShepherd = new Shepherd(context);
            myShepherd.setAction("RelationshipDelete.class");

            myShepherd.beginDBTransaction();
            try {
                Relationship rel = myShepherd.getRelationship(
                    request.getParameter("persistenceID"));
                if (rel != null) {
                    myShepherd.getPM().deletePersistent(rel);
                    myShepherd.updateDBTransaction();
                    out.println("<strong>Success:</strong> The relationship of type " +
                        request.getParameter("type") + " between " +
                        request.getParameter("markedIndividualName1") + " and " +
                        request.getParameter("markedIndividualName2") + " was deleted.");
                } else if (Shepherd.parseRelationshipKey(request.getParameter("persistenceID")) ==
                    null) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    out.println(
                        "<strong>Failure:</strong> That is not a valid relationship id.");
                } else {
                    response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    out.println(
                        "<strong>Failure:</strong> The relationship to delete was not found. It may have already been deleted.");
                }
            } catch (Exception e) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                out.println(
                    "<strong>Failure:</strong> The relationship could not be deleted due to a server error. Have your administrator check the log files.");
                e.printStackTrace();
            } finally {
                myShepherd.rollbackAndClose();
                out.close();
            }
        } else {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.println(
                "<strong>Failure:</strong> I did not have all of the information required.");
            out.close();
        }
    }
}
