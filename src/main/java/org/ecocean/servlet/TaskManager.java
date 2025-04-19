package org.ecocean.servlet;

import javax.servlet.*;
import javax.servlet.http.*;
import javax.jdo.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

import org.datanucleus.PersistenceNucleusContext;
import org.datanucleus.api.jdo.JDOPersistenceManagerFactory;
import org.ecocean.*;
import org.ecocean.ia.IA;
import org.json.JSONObject;
import org.json.JSONArray;
import org.ecocean.ia.Task;

import java.util.HashMap;

import javax.jdo.PersistenceManager;

public class TaskManager extends HttpServlet {
    PersistenceManagerFactory pmf;
    PersistenceNucleusContext nucCtx;

    public void init(ServletConfig config)
            throws ServletException {
        super.init(config);
    }

    public void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, java.io.IOException {
        String servletID = Util.generateUUID();
        String context = ServletUtilities.getContext(request);
        getPMF(context, servletID);
        Shepherd myShepherd = new Shepherd(context);
        PersistenceManager pm = pmf.getPersistenceManager();

        // Get pagination parameters with defaults
        int page = 1;
        int pageSize = 10;

        try {
            page = Integer.parseInt(request.getParameter("page"));
        } catch (NumberFormatException e) {
            // Use default if page is not a number or not provided
        }

        try {
            pageSize = Integer.parseInt(request.getParameter("size"));
        } catch (NumberFormatException e) {
            // Use default if size is not a number or not provided
        }

        try {
            String sql2 = "select count(*) as count from \"TASK\" where \"STATUS\" is null";
            Query q2 = pm.newQuery("javax.jdo.query.SQL", sql2);
            q2.setResultClass(Integer.class);
            int count = ((List<Integer>) q2.execute()).get(0).intValue();
            Integer pageCount = new Integer((int) ((double) count / (double) pageSize));

            if (page > pageCount) {
                page = pageCount;
            } else if (page < 1) {
                page = 1;
            }

            Query query = pm.newQuery(
                    "javax.jdo.query.SQL",
                    "select \"ID\", \"QUEUERESUMEMESSAGE\", \"CREATED\", \"MODIFIED\", type from (select t.*, 1 as type from \"TASK\" t where t.\"ID\" not in (select tc.\"ID_OID\" from \"TASK_CHILDREN\" tc) and t.\"ID\" not in (select tc.\"ID_EID\" from \"TASK_CHILDREN\" tc) union select t.*, 2 as type from \"TASK\" t where t.\"ID\" in (select tc.\"ID_EID\" from \"TASK_CHILDREN\" tc) and t.\"STATUS\" is null) order by \"CREATED\" limit " + pageSize + " offset " + (page - 1) * pageSize
            );
            query.setResultClass(Object[].class);
            List<Object[]> results = (List<Object[]>) query.execute();

            List<HashMap<String, Object>> taskList = new ArrayList<>();
            for (Object[] row : results) {
                HashMap<String, Object> task = new HashMap<>();

                task.put("ID", row[0]);
                task.put("QUEUERESUMEMESSAGE", row[1]);
                task.put("CREATED", row[2]);
                task.put("MODIFIED", row[3]);
                task.put("TYPE", row[4]);

                taskList.add(task);
            }

            List<HashMap<String, Object>> tasks = new ArrayList<>(taskList.size());
            for (HashMap<String, Object> task : taskList) {
                HashMap<String, Object> taskMap = new HashMap<>();

                try {
                    String message = (String) task.get("QUEUERESUMEMESSAGE");
                    String taskType = ((Integer) task.get("TYPE")) == 1 ? "Detection" : "Matcher";
                    JSONArray annotationIdJSONArray = new JSONArray();
                    if (taskType.equals("Matcher")) {
                        JSONObject queueResumeMessageObj = new JSONObject(message);

                        if (queueResumeMessageObj.has("annotationIds")) {
                            taskType = "HotSpotter pattern-matcher";
                            annotationIdJSONArray = queueResumeMessageObj.getJSONArray("annotationIds");
                        } else if (queueResumeMessageObj.has("opt")) {
                            taskType = "MiewID Matcher";
                            JSONObject identifyObj = queueResumeMessageObj.getJSONObject("identify");
                            annotationIdJSONArray = identifyObj.getJSONArray("annotationIds");
                        }
                    } else {
                        String sql = "SELECT DISTINCT \"ea\".\"ID_EID\" FROM \"TASK\" \"t\" JOIN \"TASK_OBJECTMEDIAASSETS\" \"tom\" ON \"t\".\"ID\" = \"tom\".\"ID_OID\" JOIN \"MEDIAASSET_FEATURES\" \"mf\" ON \"tom\".\"ID_EID\" = \"mf\".\"ID_OID\" JOIN \"ANNOTATION_FEATURES\" \"af\" ON \"mf\".\"ID_EID\" = \"af\".\"ID_EID\" JOIN \"ENCOUNTER_ANNOTATIONS\" \"ea\" ON \"af\".\"ID_OID\" = \"ea\".\"ID_EID\" WHERE \"t\".\"ID\" = '" + task.get("ID") + "'";
                        Query q = pm.newQuery("javax.jdo.query.SQL", sql);
                        q.setResultClass(String.class);
                        List<String> queryResult = (List<String>) q.execute();

                        annotationIdJSONArray = new JSONArray();
                        for (String item : queryResult) {
                            annotationIdJSONArray.put(item);
                        }
                    }

                    try {
                        List<String> encounterIdList = new ArrayList<>();
                        for (int i = 0; i < annotationIdJSONArray.length(); i++) {
                            String annotationId = annotationIdJSONArray.getString(i);

                            Query query2 = pm.newQuery(
                                    "javax.jdo.query.SQL",
                                    "SELECT ea.\"CATALOGNUMBER_OID\" FROM \"ENCOUNTER_ANNOTATIONS\" ea JOIN \"ANNOTATION\" a ON ea.\"ID_EID\" = a.\"ID\" WHERE a.\"ID\" = '" + annotationId + "'"
                            );
                            query2.setResultClass(String.class);
                            List<Object> queryResult = (List<Object>) query2.execute();

                            for (Object encounterId : queryResult) {
                                encounterIdList.add((String) encounterId);
                            }
                        }

                        taskMap.put("encounterIdList", encounterIdList);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    taskMap.put("taskType", taskType);
                    taskMap.put("id", task.get("ID"));
                    taskMap.put("created", new java.util.Date((long) task.get("CREATED")));
                    taskMap.put("modified", new java.util.Date((long) task.get("MODIFIED")));

                    tasks.add(taskMap);
                } catch (Exception e) {
                    System.out.println("Error fetching the encounter or encounter ID: ");
                    e.printStackTrace();
                }
            }

            try {
                String u = IA.getProperty(context, "IBEISIARestUrlGetJobStatuses");
                if (u == null) {
                    throw new MalformedURLException("configuration value IBEISIARestUrlGetJobStatuses is not set");
                }

                StringBuilder sb = new StringBuilder();
                for (Map<String, Object> map : tasks) {
                    String id = (String) map.get("id");
                    if (sb.length() > 0) sb.append(",");
                    sb.append(id);
                }
                String jobIds = sb.toString();
                URL url = new URL(u + "?jobids=" + jobIds);
                JSONObject statuses = (JSONObject) RestClient.get(url).get("response");

                for (HashMap<String, Object> task : tasks) {
                    String id = (String) task.get("id");
                    String status = statuses.getString(id);
                    task.put("status", status);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            request.setAttribute("tasks", tasks);
            request.setAttribute("page", new Integer(page));
            request.setAttribute("pageCount", pageCount);
            request.setAttribute("previousPage", new Boolean(page > 1));
            request.setAttribute("nextPage", new Boolean(page < pageCount));
            request.getRequestDispatcher("/taskManager.jsp").forward(request, response);
        } finally {
            pm.close();
        }
    }

    private void getPMF(String context, String servletID) {
        ShepherdPMF.setShepherdState("TaskManager.class" + "_" + servletID, "new");
        pmf = ShepherdPMF.getPMF(context);
        this.nucCtx = ((JDOPersistenceManagerFactory) pmf).getNucleusContext();
    }
}
