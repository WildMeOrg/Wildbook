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

// handles operations to sharks. possible operations include, create new, add encounter, remove encounter from
public class IndividualSetNickName extends HttpServlet {
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
        request.setCharacterEncoding("UTF-8");
        String context = "context0";
        context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("IndividualSetNickname.class");
        // set up for response
        response.setContentType("text/html");
        PrintWriter out = response.getWriter();
        boolean locked = false;
        String sharky = "None", nickname = "", namer = "";

        sharky = request.getParameter("individual");
        nickname = request.getParameter("nickname");
        namer = request.getParameter("namer");
        myShepherd.beginDBTransaction();
        if ((myShepherd.isMarkedIndividual(sharky)) && (request.getParameter("nickname") != null) &&
            (request.getParameter("namer") != null)) {
            MarkedIndividual myShark = myShepherd.getMarkedIndividual(sharky);
            try {
                myShark.setNickName(nickname);
                myShark.setNickNamer(namer);
            } catch (Exception le) {
                locked = true;
                myShepherd.rollbackDBTransaction();
                myShepherd.closeDBTransaction();
            }
            if (!locked) {
                myShepherd.commitDBTransaction();
                myShepherd.closeDBTransaction();
                // out.println(ServletUtilities.getHeader(request));
                out.println(
                    "<strong>Success!</strong> I have successfully changed the nickname for " +
                    sharky + " to " + nickname + ".</p>");

                // out.println("<p><a href=\""+request.getScheme()+"://" + CommonConfiguration.getURLLocation(request) + "/individuals.jsp?number=" +
                // sharky + "\">Return to " + sharky + "</a></p>\n");
                // out.println(ServletUtilities.getFooter(context));
                String message = "The nickname for " + sharky + " was set as " + nickname + ".";
            } else {
                // out.println(ServletUtilities.getHeader(request));
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.println(
                    "<strong>Failure!</strong> This record is currently being modified by another user. Please wait a few seconds before trying to nickname this individual again.");
                // out.println("<p><a href=\""+request.getScheme()+"://" + CommonConfiguration.getURLLocation(request) + "/individuals.jsp?number=" +
                // sharky + "\">Return to " + sharky + "</a></p>\n");
                // out.println(ServletUtilities.getFooter(context));
            }
        } else {
            myShepherd.rollbackDBTransaction();
            // out.println(ServletUtilities.getHeader(request));
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.println(
                "<strong>Error:</strong> I was unable to set the nickname. I cannot find the shark that you intended it for in the database.");
            // out.println(ServletUtilities.getFooter(context));
        }
        out.close();
        myShepherd.closeDBTransaction();
    }
}
