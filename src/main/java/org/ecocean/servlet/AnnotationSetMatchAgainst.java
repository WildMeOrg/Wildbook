package org.ecocean.servlet;

import org.datanucleus.api.rest.orgjson.JSONObject;
import org.ecocean.Annotation;
import org.ecocean.Shepherd;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

import java.io.IOException;
import java.io.PrintWriter;

// Set alternateID for this encounter/sighting
public class AnnotationSetMatchAgainst extends HttpServlet {
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
        myShepherd.setAction("AnnotationSetMatchAgainst.class");
        // set up for response
        response.setContentType("application/json");
        PrintWriter out = response.getWriter();
        JSONObject jobj = new JSONObject();

        myShepherd.beginDBTransaction();
        try {
            Annotation annot = null;
            if (request.getParameter("annotationID") != null) {
                String annotationID = request.getParameter("annotationID").trim();
                annot = myShepherd.getAnnotation(annotationID);
            }
            if (annot != null) {
                if (request.getParameter("value") != null &&
                    (request.getParameter("value").trim().toLowerCase().equals("true") ||
                    request.getParameter("value").trim().toLowerCase().equals("false"))) {
                    jobj.put("id", annot.getId());
                    boolean madeChange = false;
                    if (request.getParameter("value").trim().toLowerCase().equals("true")) {
                        annot.setMatchAgainst(true);
                        madeChange = true;
                        jobj.put("matchAgainst", true);
                    } else if (request.getParameter("value").trim().toLowerCase().equals("false")) {
                        annot.setMatchAgainst(false);
                        madeChange = true;
                        jobj.put("matchAgainst", false);
                    } else {
                        jobj.put("exception", "The value parameter must be exactly true or false.");
                    }
                    if (madeChange) {
                        response.setStatus(HttpServletResponse.SC_OK);
                        myShepherd.updateDBTransaction();
                        org.ecocean.ShepherdPMF.getPMF(context).getDataStoreCache().evict(
                            myShepherd.getPM().getObjectId(annot));

                        // jobj = RESTUtils.getJSONObjectFromPOJO(annot, ((JDOPersistenceManager)myShepherd.getPM()).getExecutionContext());
                    }
                }
            } else {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                jobj.put("exception", "I could not find the specified annotation in the database.");
            }
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            e.printStackTrace();
        }
        out.println(jobj.toString());
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
        out.close();
    }
}
