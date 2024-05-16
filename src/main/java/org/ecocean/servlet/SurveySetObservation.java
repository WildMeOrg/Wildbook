package org.ecocean.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

import org.ecocean.CommonConfiguration;
import org.ecocean.Observation;
import org.ecocean.Shepherd;
import org.ecocean.Survey;

public class SurveySetObservation extends HttpServlet {
    /**
     *
     */
    private static final long serialVersionUID = 1L;

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
        String context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);

        myShepherd.setAction("SurveySetObservation.class");
        System.out.println("Reached Observation setting servlet...");
        // set up for response
        response.setContentType("text/html");
        PrintWriter out = response.getWriter();
        boolean locked = false;
        String redirectURL = "/surveys/survey.jsp";
        if ((request.getParameter("number") != null) && (request.getParameter("name") != null)) {
            String name = request.getParameter("name");
            String id = request.getParameter("number");
            String value = request.getParameter("value");
            System.out.println("Setting Observation... Name : " + name + " ID : " + id +
                " Value : " + value);

            Survey sv = null;
            try {
                sv = myShepherd.getSurvey(id);
            } catch (Exception e) {
                System.out.println("NPE trying to retrieve survey from shepherd.");
                e.printStackTrace();
            }
            Observation obs = null;
            String newValue = "null";
            String oldValue = "null";
            if (sv.getObservationByName(name) != null) {
                oldValue = sv.getObservationByName(name).getValue();
            }
            if ((request.getParameter("value") != null) &&
                (!request.getParameter("value").equals(""))) {
                newValue = value;
            }
            myShepherd.beginDBTransaction();
            try {
                if (newValue.equals("null")) {
                    sv.removeObservation(name);
                    System.out.println("Servlet trying to remove Observation " + name);
                } else {
                    if (sv.getObservationByName(name) != null && value != null) {
                        Observation existing = sv.getObservationByName(name);
                        existing.setValue(value);
                    } else {
                        obs = new Observation(name, newValue, "Survey", sv.getID());
                        myShepherd.storeNewObservation(obs);
                        sv.addObservation(obs);
                        System.out.println("Success setting Observation!");
                    }
                }
            } catch (Exception le) {
                System.out.println("Hit locked exception.");
                locked = true;
                le.printStackTrace();
                myShepherd.rollbackDBTransaction();
            }
            if (!locked) {
                sv.setDWCDateLastModified();
                myShepherd.commitDBTransaction();
                out.println(ServletUtilities.getHeader(request));
                if (!newValue.equals("")) {
                    out.println("<strong>Success:</strong> Survey Observation " + name +
                        " has been updated from <i>" + oldValue + "</i> to <i>" + newValue +
                        "</i>.");
                } else {
                    out.println("<strong>Success:</strong> Survey Observation " + name +
                        " was removed. The old value was <i>" + oldValue + "</i>.");
                }
                out.println("<p><a href=\"" + request.getScheme() + "://" +
                    CommonConfiguration.getURLLocation(request) + redirectURL + "?surveyID=" +
                    request.getParameter("number") + "\">Return to Survey " +
                    request.getParameter("number") + "</a></p>\n");
                out.println(ServletUtilities.getFooter(context));
                String message = "Survey " + request.getParameter("number") + " Observation " +
                    name + " has been updated from \"" + oldValue + "\" to \"" + newValue + "\".";
                ServletUtilities.informInterestedParties(request, request.getParameter("number"),
                    message, context);
            } else {
                myShepherd.rollbackDBTransaction();
                out.println(ServletUtilities.getHeader(request));
                out.println("<strong>Failure:</strong> Survey Observation " + name +
                    " was NOT updated because another user is currently modifying this reconrd. Please try to reset the value again in a few seconds.");
                out.println("<p><a href=\"" + request.getScheme() + "://" +
                    CommonConfiguration.getURLLocation(request) + redirectURL + "?surveyID=" +
                    request.getParameter("number") + "\">Return to survey " +
                    request.getParameter("number") + "</a></p>\n");
                out.println(ServletUtilities.getFooter(context));
            }
        } else {
            myShepherd.rollbackDBTransaction();
            out.println(ServletUtilities.getHeader(request));
            out.println(
                "<strong>Error:</strong> I don't have enough information to complete your request.");
            out.println("<p><a href=\"" + request.getScheme() + "://" +
                CommonConfiguration.getURLLocation(request) + redirectURL + "?surveyID=" +
                request.getParameter("number") + "\">Return to survey #" +
                request.getParameter("number") + "</a></p>\n");
            out.println(ServletUtilities.getFooter(context));
        }
        out.close();
        myShepherd.closeDBTransaction();
    }
}
