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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.jdo.datastore.JDOConnection;
import javax.jdo.PersistenceManager;

import org.datanucleus.api.jdo.JDOPersistenceManager;

class SqlHelper {
    public static List<Object[]> executeRawSql(PersistenceManager pm, String sql) {
        JDOConnection jdoConn = null;
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            // 1) Obtain JDOConnection and unwrap JDBC Connection
            jdoConn = ((JDOPersistenceManager) pm).getDataStoreConnection();
            conn = (Connection) jdoConn.getNativeConnection();

            // 2) Prepare your raw SQL (no ? placeholders)
            ps = conn.prepareStatement(sql);

            // 3) Execute directly
            rs = ps.executeQuery();

            // 4) Read results
            int colCount = rs.getMetaData().getColumnCount();
            List<Object[]> results = new ArrayList<>();
            while (rs.next()) {
                Object[] row = new Object[colCount];
                for (int c = 1; c <= colCount; c++) {
                    row[c - 1] = rs.getObject(c);
                }
                results.add(row);
            }
            return results;

        } catch (Exception e) {
            throw new RuntimeException("Error executing raw SQL", e);
        } finally {
            // 5) Clean up JDBC resources
            try {
                if (rs != null) rs.close();
            } catch (Exception ignored) {
            }
            try {
                if (ps != null) ps.close();
            } catch (Exception ignored) {
            }
            // 6) Close JDOConnection (returns JDBC Connection to the pool)
            try {
                if (jdoConn != null) jdoConn.close();
            } catch (Exception ignored) {
            }
        }
    }
}

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
                "ORDER BY t.\"CREATED\" DESC " +
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

    private String getSql(int limit, int offset, String taskType) {
        if (taskType.toLowerCase().equals("matcher")) {
            return getMatcherTasksSql(limit, offset);
        } else {
            return getDetectionTasksSql(limit, offset);
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
        int pageSize = 15;
        String taskTypeQueryParam = "detection";

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

        try {
//            Query q = pm.newQuery("javax.jdo.query.SQL", getSql(pageSize, (page - 1) * pageSize, taskTypeQueryParam));
//            q.setResultClass(Object[].class);
//            List<Object[]> results = (List<Object[]>) q.execute();
            List<Object[]> results = SqlHelper.executeRawSql(pm, getSql(pageSize, (page - 1) * pageSize, taskTypeQueryParam));


//            Query q2 = pm.newQuery("javax.jdo.query.SQL", getSql(1, (page) * pageSize, taskTypeQueryParam));
//            q2.setResultClass(Object[].class);
//            List<Object[]> nextPageResults = (List<Object[]>) q2.execute();
            List<Object[]> nextPageResults = SqlHelper.executeRawSql(pm, getSql(1, (page) * pageSize, taskTypeQueryParam));

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
                    if (taskType.equals("Matcher")) {
                        String message = (String) task.get("QUEUERESUMEMESSAGE");
                        JSONObject queueResumeMessageObj = new JSONObject(message);

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
//                        Query q3 = pm.newQuery("javax.jdo.query.SQL", encounterIDsSql);
//                        q3.setResultClass(String.class);
//                        encounterIdList = (List<String>) q3.execute();
                        List<Object[]> rows = SqlHelper.executeRawSql(pm, encounterIDsSql);
                        encounterIdList = rows.stream()
                                .map(row -> row[0] != null ? row[0].toString() : null)
                                .collect(Collectors.toList());
                    } catch (Exception e) {
                    }

                    taskMap.put("encounterIdList", encounterIdList);
                    taskMap.put("taskType", taskType);
                    taskMap.put("id", task.get("ID"));
                    taskMap.put("created", new java.util.Date((long) task.get("CREATED")));
                    taskMap.put("modified", new java.util.Date((long) task.get("MODIFIED")));

                    if (!encounterIdList.isEmpty()) {
                        tasks.add(taskMap);
                    }
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
//                e.printStackTrace();
            }

            request.setAttribute("tasks", tasks);
            request.setAttribute("taskTypeQueryParam", taskTypeQueryParam);
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
