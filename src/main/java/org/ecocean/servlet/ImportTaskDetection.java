package org.ecocean.servlet;

import org.datanucleus.api.rest.orgjson.JSONObject;
import org.ecocean.ia.IAUtils;
import org.ecocean.servlet.importer.ImportTask;
import org.ecocean.Shepherd;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

// Set alternateID for this encounter/sighting
public class ImportTaskDetection extends HttpServlet {
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
        myShepherd.setAction("ImportTaskDetection.class");
        // set up for response
        response.setContentType("application/json");
        PrintWriter out = response.getWriter();
        JSONObject jobj = new JSONObject();

        myShepherd.beginDBTransaction();
        try {
            ImportTask it = null;
            if (request.getParameter("importTaskID") != null) {
                String taskID = request.getParameter("importTaskID").trim();
                it = myShepherd.getImportTask(taskID);
            }
            if (it != null) {
                List<String> taskIDs = IAUtils.intakeMediaAssets(it, myShepherd);
                response.setStatus(HttpServletResponse.SC_OK);
                // jobj.put("exception", "I could not find the specified ImportTask in the database.");
                jobj.put("taskID", taskIDs);
            } else {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                jobj.put("exception", "I could not find the specified ImportTask in the database.");
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
