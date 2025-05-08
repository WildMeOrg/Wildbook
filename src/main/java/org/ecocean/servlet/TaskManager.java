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
import org.json.JSONArray;
import org.json.JSONObject;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.jdo.datastore.JDOConnection;
import javax.jdo.PersistenceManager;

import org.datanucleus.api.jdo.JDOPersistenceManager;

public class TaskManager extends HttpServlet {
    PersistenceManagerFactory pmf;
    PersistenceNucleusContext nucCtx;

    public void init(ServletConfig config)
            throws ServletException {
        super.init(config);
    }

    private String getDetectionTasksSql(int limit, int offset) {
        return "SELECT t.\"ID\" as id, t.\"QUEUERESUMEMESSAGE\" as qresumemsg, t.\"CREATED\" as created, t.\"MODIFIED\" as modified " +
                "FROM \"TASK\" t " +
                "WHERE NOT EXISTS ( " +
                "  SELECT 1 FROM \"TASK_CHILDREN\" tc WHERE tc.\"ID_OID\" = t.\"ID\" " +
                ") " +
                "AND NOT EXISTS ( " +
                "  SELECT 1 FROM \"TASK_CHILDREN\" tc WHERE tc.\"ID_EID\" = t.\"ID\" " +
                ") " +
                "AND EXISTS ( " +
                "  SELECT 1 " +
                "  FROM \"TASK_OBJECTMEDIAASSETS\" \"tom\" " +
                "    JOIN \"MEDIAASSET_FEATURES\" \"mf\" ON \"tom\".\"ID_EID\" = \"mf\".\"ID_OID\" " +
                "    JOIN \"ANNOTATION_FEATURES\" \"af\" ON \"mf\".\"ID_EID\" = \"af\".\"ID_EID\" " +
                "    JOIN \"ENCOUNTER_ANNOTATIONS\" \"ea\" ON \"af\".\"ID_OID\" = \"ea\".\"ID_EID\" " +
                "  WHERE \"tom\".\"ID_OID\" = \"t\".\"ID\" " +
                "    AND \"ea\".\"ID_EID\" IS NOT NULL " +
                ") " +
                "AND (t.\"QUEUERESUMEMESSAGE\" NOT LIKE '%\"retriedDetection\":%' OR t.\"QUEUERESUMEMESSAGE\" IS NULL) " +
                "ORDER BY t.\"CREATED\" DESC " +
                "LIMIT " + limit + " OFFSET " + offset;
    }

    private String getMatcherTasksSql(int limit, int offset) {
        return "SELECT t.\"ID\" as id, t.\"QUEUERESUMEMESSAGE\" as qresumemsg, t.\"CREATED\" as created, t.\"MODIFIED\" as modified " +
                "FROM \"TASK\" t " +
                "WHERE t.\"STATUS\" IS NULL " +
                "AND t.\"QUEUERESUMEMESSAGE\" IS NOT NULL " +
                "AND EXISTS ( " +
                "  SELECT 1 FROM \"TASK_CHILDREN\" tc WHERE tc.\"ID_EID\" = t.\"ID\" " +
                ") " +
                "AND EXISTS ( " +
                "  SELECT 1 " +
                "  FROM json_array_elements_text( " +
                "           COALESCE( " +
                "               t.\"QUEUERESUMEMESSAGE\"::json->'annotationIds', " +
                "               t.\"QUEUERESUMEMESSAGE\"::json->'identify'->'annotationIds' " +
                "           ) " +
                "       ) AS annotation_id " +
                "  JOIN \"ANNOTATION\" a ON a.\"ID\" = annotation_id::text " +
                "  JOIN \"ENCOUNTER_ANNOTATIONS\" ea ON ea.\"ID_EID\" = a.\"ID\" " +
                ") " +
                "AND (t.\"QUEUERESUMEMESSAGE\" NOT LIKE '%\"retriedMatcher\":%' OR t.\"QUEUERESUMEMESSAGE\" IS NULL) " +
                "ORDER BY t.\"CREATED\" DESC " +
                "LIMIT " + limit + " OFFSET " + offset;
    }

    private String getRetriedDetectionTasksSql(int limit, int offset) {
        return "SELECT t.\"ID\" as id, t.\"QUEUERESUMEMESSAGE\" as qresumemsg, t.\"CREATED\" as created, t.\"MODIFIED\" as modified " +
                "FROM \"TASK\" t " +
                "WHERE t.\"QUEUERESUMEMESSAGE\" LIKE '%\"retriedDetection\":%' " +
                "ORDER BY t.\"MODIFIED\" DESC " +
                "LIMIT " + limit + " OFFSET " + offset;
    }

    private String getRetriedMatcherTasksSql(int limit, int offset) {
        return "SELECT t.\"ID\" as id, t.\"QUEUERESUMEMESSAGE\" as qresumemsg, t.\"CREATED\" as created, t.\"MODIFIED\" as modified " +
                "FROM \"TASK\" t " +
                "WHERE t.\"QUEUERESUMEMESSAGE\" LIKE '%\"retriedMatcher\":%' " +
                "ORDER BY t.\"MODIFIED\" DESC " +
                "LIMIT " + limit + " OFFSET " + offset;
    }

    private String toCountQuery(String sql) {
        // Normalize spacing and remove trailing semicolon
        String s = sql.trim().replaceAll("\\s+", " ");
        if (s.endsWith(";")) s = s.substring(0, s.length() - 1);

        // Find the FROM
        int fromIdx = s.toLowerCase().indexOf(" from ");
        if (fromIdx < 0) throw new IllegalArgumentException("No FROM clause found");

        // Cut off any ORDER BY / LIMIT / OFFSET after the FROM block
        String afterFrom = s.substring(fromIdx + 1);  // includes the "from"
        String[] cutPoints = {" order by ", " limit ", " offset "};
        int cutIdx = afterFrom.length();
        for (String cp : cutPoints) {
            int i = afterFrom.toLowerCase().indexOf(cp);
            if (i >= 0 && i < cutIdx) cutIdx = i;
        }
        String fromPart = afterFrom.substring(0, cutIdx);

        // Build the count query
        return "SELECT COUNT(*) " + fromPart;
    }

    private String getSql(boolean showRetried, int limit, int offset, String taskType) {
        if (showRetried) {
            if (taskType.toLowerCase().equals("matcher")) {
                return getRetriedMatcherTasksSql(limit, offset);
            } else {
                return getRetriedDetectionTasksSql(limit, offset);
            }
        } else {
            if (taskType.toLowerCase().equals("matcher")) {
                return getMatcherTasksSql(limit, offset);
            } else {
                return getDetectionTasksSql(limit, offset);
            }
        }
    }

    public void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, java.io.IOException {
        String servletID = Util.generateUUID();
        String context = ServletUtilities.getContext(request);
        getPMF(context, servletID);
        Shepherd myShepherd = new Shepherd(context);
        PersistenceManager pm = pmf.getPersistenceManager();

        int page = 1;
        int pageSize = 30;
        String taskTypeQueryParam = "detection";
        Boolean showRetried = false;

        try {
            page = Integer.parseInt(request.getParameter("page"));
        } catch (NumberFormatException e) {
            // Use default if not a number or not provided
        }

        try {
            pageSize = Integer.parseInt(request.getParameter("size"));
        } catch (NumberFormatException e) {
            // Use default if not a number or not provided
        }

        String taskTypeQueryParam2 = request.getParameter("type");
        if (taskTypeQueryParam2 != null && taskTypeQueryParam2.equalsIgnoreCase("matcher")) {
            taskTypeQueryParam = "matcher";
        } else {
            // Use default
        }

        String showRetried2 = request.getParameter("retried");
        if (showRetried2 != null && showRetried2.equalsIgnoreCase("true")) {
            showRetried = true;
        } else {
            // Use default
        }

        try {
            List<Object[]> results = SqlHelper.executeRawSql(pm, getSql(showRetried, pageSize, (page - 1) * pageSize, taskTypeQueryParam));

            List<Object[]> nextPageResults = SqlHelper.executeRawSql(pm, getSql(showRetried, 1, (page) * pageSize, taskTypeQueryParam));

            List<HashMap<String, Object>> taskList = new ArrayList<>();
            for (Object[] row : results) {
                HashMap<String, Object> task = new HashMap<>();

                task.put("ID", row[0]);
                task.put("QUEUERESUMEMESSAGE", row[1]);
                task.put("CREATED", row[2]);
                task.put("MODIFIED", row[3]);

                taskList.add(task);
            }

            List<HashMap<String, Object>> tasks = new ArrayList<>(taskList.size());
            for (HashMap<String, Object> task : taskList) {
                HashMap<String, Object> taskMap = new HashMap<>();

                try {
                    String taskType = taskTypeQueryParam.equalsIgnoreCase("detection") ? "Detection" : "Matcher";

                    String encounterIDsSql;
                    List<String> encounterIdList = new ArrayList<>();

                    String message = (String) task.get("QUEUERESUMEMESSAGE");
                    JSONObject queueResumeMessageObj;
                    if (message != null && !message.isEmpty()) {
                        queueResumeMessageObj = new JSONObject(message);
                    } else {
                        queueResumeMessageObj = new JSONObject();
                    }

                    if (taskType.equals("Matcher")) {
                        if (queueResumeMessageObj.has("annotationIds")) {
                            taskType = "HotSpotter pattern-matcher";
                        } else if (queueResumeMessageObj.has("opt")) {
                            taskType = "MiewID Matcher";
                        }

                        encounterIDsSql = "SELECT DISTINCT ea.\"CATALOGNUMBER_OID\" as encounterid " +
                                "FROM \"TASK\" t " +
                                "JOIN LATERAL ( " +
                                "  SELECT json_array_elements_text( " +
                                "           COALESCE( " +
                                "               t.\"QUEUERESUMEMESSAGE\"::json->'annotationIds', " +
                                "               t.\"QUEUERESUMEMESSAGE\"::json->'identify'->'annotationIds' " +
                                "           ) " +
                                "       ) AS annotation_id " +
                                ") AS annotations ON true " +
                                "JOIN \"ANNOTATION\" a ON a.\"ID\" = annotations.annotation_id::text " +
                                "JOIN \"ENCOUNTER_ANNOTATIONS\" ea ON ea.\"ID_EID\" = a.\"ID\" " +
                                "WHERE t.\"ID\" = '" + task.get("ID") + "'";
                    } else {
                        encounterIDsSql = "SELECT DISTINCT ea.\"CATALOGNUMBER_OID\" as encounterid " +
                                "FROM \"TASK\" t " +
                                "JOIN \"TASK_OBJECTMEDIAASSETS\" tom ON t.\"ID\" = tom.\"ID_OID\" " +
                                "JOIN \"MEDIAASSET_FEATURES\" mf ON tom.\"ID_EID\" = mf.\"ID_OID\" " +
                                "JOIN \"ANNOTATION_FEATURES\" af ON mf.\"ID_EID\" = af.\"ID_EID\" " +
                                "JOIN \"ENCOUNTER_ANNOTATIONS\" ea ON af.\"ID_OID\" = ea.\"ID_EID\" " +
                                "WHERE t.\"ID\" = '" + task.get("ID") + "'";
                    }

                    try {
                        List<Object[]> rows = SqlHelper.executeRawSql(pm, encounterIDsSql);
                        encounterIdList = rows.stream()
                                .map(row -> row[0] != null ? row[0].toString() : null)
                                .collect(Collectors.toList());
                    } catch (Exception e) {
                    }

                    JSONArray jsonArray = new JSONArray();
                    try {
                        jsonArray = queueResumeMessageObj.getJSONArray("retriedDetection");

                    } catch (Exception e) {
                        try {
                            jsonArray = queueResumeMessageObj.getJSONArray("retriedMatcher");

                        } catch (Exception e2) {
                        }
                    }
                    List<String> retriedTasks = new ArrayList<>();
                    for (int j = 0; j < jsonArray.length(); j++) {
                        retriedTasks.add(jsonArray.getString(j));
                    }

                    taskMap.put("encounterIdList", encounterIdList);
                    taskMap.put("taskType", taskType);
                    taskMap.put("id", task.get("ID"));
                    taskMap.put("created", new java.util.Date((long) task.get("CREATED")));
                    taskMap.put("modified", new java.util.Date((long) task.get("MODIFIED")));
                    taskMap.put("retriedTasks", retriedTasks);

                    if (!encounterIdList.isEmpty()) {
                        tasks.add(taskMap);
                    }
                } catch (Exception e) {
                    System.out.println("Error fetching the encounter or encounter ID: ");
                    e.printStackTrace();
                }
            }

            request.setAttribute("tasks", tasks);
            request.setAttribute("taskTypeQueryParam", taskTypeQueryParam);
            request.setAttribute("showRetried", showRetried);
            request.setAttribute("page", new Integer(page));
            request.setAttribute("previousPage", new Boolean(page > 1));
            request.setAttribute("nextPage", new Boolean(nextPageResults.size() > 0));
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
