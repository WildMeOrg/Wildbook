package org.ecocean.servlet;

import javax.servlet.*;
import javax.servlet.http.*;
import javax.jdo.*;
import java.util.*;

import org.datanucleus.PersistenceNucleusContext;
import org.datanucleus.api.jdo.JDOPersistenceManagerFactory;
import org.ecocean.ShepherdPMF;
import org.ecocean.Util;
import org.ecocean.ia.IA;
import org.ecocean.ia.Task;
import org.json.JSONObject;

import javax.jdo.JDOUserException;
import javax.jdo.PersistenceManager;

public class TaskRetry extends HttpServlet {
    PersistenceManagerFactory pmf;
    PersistenceNucleusContext nucCtx;

    public void init(ServletConfig config)
            throws ServletException {
        super.init(config);
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, java.io.IOException {
        String servletID = Util.generateUUID();
        getPMF(request, servletID);
        String context = ServletUtilities.getContext(request);
        PersistenceManager pm = pmf.getPersistenceManager();
        Transaction tx = pm.currentTransaction();
        Boolean isMatcherTypeTask = true;

        try {
            String taskId = request.getParameter("taskId");
            Task task = pm.getObjectById(Task.class, taskId);

            if (task != null && task.getStatus2() == null) {
                String message = task.getQueueResumeMessage();

                if (message == null || message.isEmpty()) {
                    isMatcherTypeTask = false;

                    String baseUrl = IA.getBaseURL(context);
                    Query q = pm.newQuery(
                            "javax.jdo.query.SQL",
                            "SELECT toma.\"ID_EID\" FROM \"TASK\" t JOIN \"TASK_OBJECTMEDIAASSETS\" toma ON t.\"ID\" = toma.\"ID_OID\" WHERE t.\"ID\" = '" + taskId + "'"
                    );
                    q.setResultClass(Integer.class);
                    List<Integer> mediaAssetIds = (List<Integer>) q.execute();
                    JSONObject detectObj = new JSONObject();
                    detectObj.put("mediaAssetIds", mediaAssetIds);

                    JSONObject parametersObj = task.getParameters();
                    parametersObj.put("taskId", taskId);
                    parametersObj.put("detect", detectObj);
                    parametersObj.put("__context", context);
                    parametersObj.put("__baseUrl", baseUrl);
                    parametersObj.remove("matchingSetFilter");
                    parametersObj.remove("ibeis.detection");

                    JSONObject detectArgsObj = parametersObj.getJSONObject("detectArgs");
                    parametersObj.put("__detect_args", detectArgsObj);
                    parametersObj.remove("detectArgs");

                    message = parametersObj.toString();
                }

                org.ecocean.servlet.IAGateway.addToQueue(context, message);

                tx.begin();
                if (isMatcherTypeTask) {
                    task.setStatus("retried");
                }
                tx.commit();
//            pm.deletePersistent(task);
                request.getRequestDispatcher("/taskManagerRetry.jsp").forward(request, response);
            }
        } catch (Exception e) {
            if (tx.isActive()) {
                tx.rollback();
            }
            e.printStackTrace();
        } finally {
            pm.close();
        }
    }

    private void getPMF(HttpServletRequest req, String servletID) {
        String context = ServletUtilities.getContext(req);
        ShepherdPMF.setShepherdState("TaskRetry.class" + "_" + servletID, "new");
        pmf = ShepherdPMF.getPMF(context);
        this.nucCtx = ((JDOPersistenceManagerFactory) pmf).getNucleusContext();
    }
}
