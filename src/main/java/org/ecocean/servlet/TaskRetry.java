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
import org.json.JSONArray;
import org.json.JSONObject;

public class TaskRetry extends HttpServlet {
    PersistenceManagerFactory pmf;
    PersistenceNucleusContext nucCtx;

    public void init(ServletConfig config)
            throws ServletException {
        super.init(config);
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, java.io.IOException {
        doPost(request, response);
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, java.io.IOException {
        String servletID = Util.generateUUID();

        String singleTaskId = request.getParameter("taskId");
        String taskIdsQueryParam = request.getParameter("taskIds");
        ArrayList<String> taskIds = new ArrayList<>();
        if (singleTaskId != null && !singleTaskId.isEmpty()) {
            taskIds.add(singleTaskId);
        } else if (taskIdsQueryParam != null && !taskIdsQueryParam.isEmpty()) {
            taskIds = new ArrayList<>(Arrays.asList(taskIdsQueryParam.split(",")));
        }

        // process all tasks, forward once
        String context = ServletUtilities.getContext(request);
        getPMF(request, servletID);
        PersistenceManager pm = pmf.getPersistenceManager();
        List<String> retried = new ArrayList<>();
        List<String> alreadyQueued = new ArrayList<>();
        Map<String, String> failures = new LinkedHashMap<>();

        for (String taskId : taskIds) {

            try {
                Task task = pm.getObjectById(Task.class, taskId);
                if (task == null) {
                    failures.put(taskId, "Task not found");
                    continue;
                }
                String message = task.getQueueResumeMessage();

                if (message == null || message.isEmpty()) {
                    String baseUrl = IA.getBaseURL(context);
                    Query q = pm.newQuery(
                            "javax.jdo.query.SQL",
                            "SELECT toma.\"ID_EID\" FROM \"TASK\" t JOIN \"TASK_OBJECTMEDIAASSETS\" toma ON t.\"ID\" = toma.\"ID_OID\" WHERE t.\"ID\" = '" + taskId + "'"
                    );
                    q.setResultClass(Integer.class);
                    @SuppressWarnings("unchecked")
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

                    JSONArray retriedList = new JSONArray().put(taskId);
                    JSONObject queueResumeObj = new JSONObject().put("retriedDetection", retriedList);

                    Transaction tx = pm.currentTransaction();

                  try {
                        tx.begin();
                        SqlHelper.executeRawSql(
                                pm,
                                "UPDATE \"TASK\" SET \"QUEUERESUMEMESSAGE\" = '" + queueResumeObj.toString()
                                        + "', \"MODIFIED\" = " + System.currentTimeMillis()
                                        + " WHERE \"ID\" = '" + task.getId() + "'"
                        );
                        tx.commit();
                    } catch (Exception e) {
                        if (tx.isActive()) tx.rollback();
                        failures.put(taskId, "Failed to persist queue resume message: " + e.getMessage());
                        continue;
                    }


                    org.ecocean.servlet.IAGateway.addToQueue(context, message);
                    retried.add(taskId);

                } else {
                    JSONObject messageObj = new JSONObject(message);
                    messageObj.put("isRetriedFailedTask", true);
                    message = messageObj.toString();

                    task.setStatus("retried");
                    org.ecocean.servlet.IAGateway.addToQueue(context, message);
                    alreadyQueued.add(taskId);
                }

                org.ecocean.servlet.IAGateway.addToQueue(context, message);

                request.setAttribute("type", request.getParameter("type"));
                request.setAttribute("page", request.getParameter("page"));
                request.getRequestDispatcher("/taskManagerRetry.jsp").forward(request, response);
                
            } catch (Exception e) {
                failures.put(taskId, e.getClass().getSimpleName() + ": " + e.getMessage());
            } finally {
                pm.close();
            }


            request.setAttribute("type", request.getParameter("type"));
            request.setAttribute("page", request.getParameter("page"));
            request.setAttribute("retried", retried);
            request.setAttribute("alreadyQueued", alreadyQueued);
            request.setAttribute("failures", failures);
            request.getRequestDispatcher("/taskManagerRetry.jsp").forward(request, response);
        }
    }

    private void getPMF(HttpServletRequest req, String servletID) {
        String context = ServletUtilities.getContext(req);
        ShepherdPMF.setShepherdState("TaskRetry.class" + "_" + servletID, "new");
        pmf = ShepherdPMF.getPMF(context);
        this.nucCtx = ((JDOPersistenceManagerFactory) pmf).getNucleusContext();
    }
}
