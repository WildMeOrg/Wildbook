package org.ecocean.servlet;

import org.ecocean.CommonConfiguration;
import org.ecocean.Encounter;
import org.ecocean.Shepherd;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

public class EncounterSetTapirLinkExposure extends HttpServlet {
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
        myShepherd.setAction("EncounterSetTapirLinkExposure.class");
        // set up for response
        response.setContentType("text/html");
        PrintWriter out = response.getWriter();
        boolean locked = false, isOwner = true;
        String action = request.getParameter("action");
        // System.out.println("Action is: "+action);
        if (action != null) {
            if ((action.equals("tapirLinkExpose"))) {
                if (!(request.getParameter("number") == null)) {
                    myShepherd.beginDBTransaction();
                    Encounter newenc = myShepherd.getEncounter(request.getParameter("number"));

                    try {
                        if (newenc.getOKExposeViaTapirLink()) {
                            newenc.setOKExposeViaTapirLink(false);
                        } else {
                            newenc.setOKExposeViaTapirLink(true);
                        }
                        // newenc.addComments("<p><em>"+request.getRemoteUser()+" on "+(new java.util.Date()).toString()+"</em><br>"+"Approved this
                        // encounter for TapirLink exposure.");
                    } catch (Exception le) {
                        System.out.println("Hit locked exception on action: " + action);
                        locked = true;
                        le.printStackTrace();
                        myShepherd.rollbackDBTransaction();
                        // myShepherd.closeDBTransaction();
                    }
                    if (!locked) {
                        myShepherd.commitDBTransaction(action);
                        out.println(ServletUtilities.getHeader(request));
                        response.setStatus(HttpServletResponse.SC_OK);
                        out.println("<strong>Success:</strong> I have changed encounter " +
                            request.getParameter("number") + " TapirLink exposure status.");
                        out.println("<p><a href=\"" + request.getScheme() + "://" +
                            CommonConfiguration.getURLLocation(request) +
                            "/encounters/encounter.jsp?number=" + request.getParameter("number") +
                            "\">Return to encounter #" + request.getParameter("number") +
                            "</a>.</p>\n");
                        List<String> allStates = CommonConfiguration.getIndexedPropertyValues(
                            "encounterState", context);
                        int allStatesSize = allStates.size();
                        if (allStatesSize > 0) {
                            for (int i = 0; i < allStatesSize; i++) {
                                String stateName = allStates.get(i);
                                out.println("<p><a href=\"/react/encounter-search?state=" +
                                    stateName + "\">View all " + stateName +
                                    " encounters</a></font></p>");
                            }
                        }
                        out.println(
                            "<p><a href=\"individualSearchResults.jsp\">View all individuals</a></font></p>");

                        out.println(ServletUtilities.getFooter(context));
                    } else {
                        out.println(ServletUtilities.getHeader(request));
                        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                        out.println("<strong>Failure:</strong> I have NOT changed encounter " +
                            request.getParameter("number") +
                            " TapirLink status. This encounter is currently being modified by another user, or an unknown error occurred.");
                        out.println("<p><a href=\"" + request.getScheme() + "://" +
                            CommonConfiguration.getURLLocation(request) +
                            "/encounters/encounter.jsp?number=" + request.getParameter("number") +
                            "\">Return to encounter #" + request.getParameter("number") +
                            "</a></p>\n");
                        List<String> allStates = CommonConfiguration.getIndexedPropertyValues(
                            "encounterState", context);
                        int allStatesSize = allStates.size();
                        if (allStatesSize > 0) {
                            for (int i = 0; i < allStatesSize; i++) {
                                String stateName = allStates.get(i);
                                out.println("<p><a href=\"/react/encounter-search?state=" +
                                    stateName + "\">View all " + stateName +
                                    " encounters</a></font></p>");
                            }
                        }
                        out.println(
                            "<p><a href=\"individualSearchResults.jsp\">View all individual</a></font></p>");
                        out.println(ServletUtilities.getFooter(context));
                    }
                } else {
                    out.println(ServletUtilities.getHeader(request));
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    out.println(
                        "<strong>Error:</strong> I don't know which new encounter you're trying to approve.");
                    out.println(ServletUtilities.getFooter(context));
                }
            } else {
                out.println(ServletUtilities.getHeader(request));
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.println(
                    "<p>I didn't understand your command, or you are not authorized for this action.</p>");
                out.println("<p>Please try again or <a href=\"welcome.jsp\">login here</a>.");
                out.println(ServletUtilities.getFooter(context));
            }
        } else {
            out.println(ServletUtilities.getHeader(request));
            out.println(
                "<p>I did not receive enough data to process your command. No action was indicated to me.</p>");
            out.println("<p>Please try again or <a href=\"welcome.jsp\">login here</a>.");
            out.println(ServletUtilities.getFooter(context));
        }
        out.close();
        myShepherd.closeDBTransaction();
    }
}
