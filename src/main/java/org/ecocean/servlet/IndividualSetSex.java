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

public class IndividualSetSex extends HttpServlet {
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
        myShepherd.setAction("IndividualSetSex.class");
        // set up for response
        response.setContentType("text/html");
        PrintWriter out = response.getWriter();
        boolean locked = false;
        // String action = request.getParameter("action");
        if ((request.getParameter("individual") != null) &&
            (request.getParameter("selectSex") != null)) {
            myShepherd.beginDBTransaction();
            MarkedIndividual changeMe = myShepherd.getMarkedIndividual(request.getParameter(
                "individual"));
            String oldSex = "null";
            String newSex = "null";
            try {
                if (changeMe.getSex() != null) {
                    oldSex = changeMe.getSex();
                }
                if (request.getParameter("selectSex") != null) {
                    changeMe.setSex(request.getParameter("selectSex"));
                    newSex = request.getParameter("selectSex");
                } else { changeMe.setSex(null); }
                // changeMe.setSex(request.getParameter("selectSex"));

                changeMe.addComments("<p><em>" + request.getRemoteUser() + " on " +
                    (new java.util.Date()).toString() + "</em><br>Changed sex from " + oldSex +
                    " to " + newSex + ".</p>");
            } catch (Exception le) {
                // System.out.println("Hit locked exception on action: "+action);
                locked = true;
                le.printStackTrace();
                myShepherd.rollbackDBTransaction();
            }
            if (!locked) {
                myShepherd.commitDBTransaction();
                // out.println(ServletUtilities.getHeader(request));
                response.setStatus(HttpServletResponse.SC_OK);
                out.println("<strong>Success:</strong> Sex has been updated from " + oldSex +
                    " to " + request.getParameter("selectSex") + ".");
                // out.println("<p><a href=\""+request.getScheme()+"://" + CommonConfiguration.getURLLocation(request) + "/individuals.jsp?number=" +
                // request.getParameter("individual") + "\">Return to <strong>" + request.getParameter("individual") + "</strong></a></p>\n");
                // out.println("<p><a
                // href=\"http://"+CommonConfiguration.getURLLocation()+"/encounters/encounter.jsp?number="+request.getParameter("number")+"\">Return
                // to encounter #"+request.getParameter("number")+"</a></p>\n");
                /*
                 * List<String> allStates=CommonConfiguration.getIndexedPropertyValues("encounterState",context);
                   int allStatesSize=allStates.size();
                   if(allStatesSize>0){
                   for(int i=0;i<allStatesSize;i++){
                    String stateName=allStates.get(i);
                    //out.println("<p><a href=\"/react/encounter-search?state="+stateName+"\">View all "+stateName+" encounters</a></font></p>");
                   }
                   }*/
                // out.println("<p><a href=\"individualSearchResults.jsp\">View all individuals</a></font></p>");
                // out.println(ServletUtilities.getFooter(context));
                String message = "The sex for " + request.getParameter("individual") +
                    " has been updated from " + oldSex + " to " +
                    request.getParameter("selectSex") + ".";
                ServletUtilities.informInterestedIndividualParties(request,
                    request.getParameter("individual"), message, context);
            } else {
                // out.println(ServletUtilities.getHeader(request));
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.println(
                    "<strong>Failure:</strong> Sex was NOT updated. This record is currently being modified by another user. Please try this operation again in a few seconds.");
                // out.println("<p><a href=\""+request.getScheme()+"://" + CommonConfiguration.getURLLocation(request) + "/individuals.jsp?number=" +
                // request.getParameter("individual") + "\">Return to <strong>" + request.getParameter("individual") + "</strong></a></p>\n");
                // out.println("<p><a
                // href=\"http://"+CommonConfiguration.getURLLocation()+"/encounters/encounter.jsp?number="+request.getParameter("number")+"\">Return
                // to encounter #"+request.getParameter("number")+"</a></p>\n");
                /*List<String> allStates=CommonConfiguration.getIndexedPropertyValues("encounterState",context);
                   int allStatesSize=allStates.size();
                   if(allStatesSize>0){
                   for(int i=0;i<allStatesSize;i++){
                    String stateName=allStates.get(i);
                    //out.println("<p><a href=\"/react/encounter-search?state="+stateName+"\">View all "+stateName+" encounters</a></font></p>");
                   }
                   }*/
                // out.println("<p><a href=\"individualSearchResults.jsp\">View all individuals</a></font></p>");
                // out.println(ServletUtilities.getFooter(context));
            }
        } else {
            // out.println(ServletUtilities.getHeader(request));
            out.println(
                "<strong>Error:</strong> I don't have enough information to complete your request.");
            // out.println("<p><a href=\""+request.getScheme()+"://" + CommonConfiguration.getURLLocation(request) + "/individuals.jsp?number=" +
            // request.getParameter("individual") + "\">Return to <strong>" + request.getParameter("individual") + "</strong></a></p>\n");
            /*List<String> allStates=CommonConfiguration.getIndexedPropertyValues("encounterState",context);
               int allStatesSize=allStates.size();
               if(allStatesSize>0){
               for(int i=0;i<allStatesSize;i++){
                String stateName=allStates.get(i);
                out.println("<p><a href=\"/react/encounter-search?state="+stateName+"\">View all "+stateName+" encounters</a></font></p>");
               }
               }*/
            // out.println("<p><a href=\"individualSearchResults.jsp\">View all individuals</a></font></p>");
            // out.println(ServletUtilities.getFooter(context));
        }
        out.close();
        myShepherd.closeDBTransaction();
    }
}
