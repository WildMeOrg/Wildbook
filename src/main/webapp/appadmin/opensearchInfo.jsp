<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.opensearch.client.Request,
java.util.List, java.util.ArrayList,
java.util.Collections,
java.util.Iterator,
org.json.JSONObject,
org.json.JSONArray,
org.ecocean.*,
org.ecocean.shepherd.core.*
"%>

<%!
    /**
     * Formats the OpenSearch cat indices JSON output as an HTML table
     */
    private String formatIndicesTable(String jsonData) {
        if (jsonData == null || jsonData.trim().isEmpty()) return "<p>No indices data available</p>";

        StringBuilder formatted = new StringBuilder();
        formatted.append("<div class='table-wrapper'>");
        formatted.append("<table class='indices-table'>");
        try {
            org.json.JSONArray indices = new org.json.JSONArray(jsonData);
            if (indices.length() == 0) return "<p>No indices found</p>";

            String[] headers = {
                    "health", "status", "index", "uuid", "pri", "rep",
                    "docs.count", "docs.deleted", "store.size", "pri.store.size"
            };
            formatted.append("<thead><tr>");
            for (String header : headers) {
                String displayHeader = header.replace(".", " ").replace("_", " ");
                displayHeader = displayHeader.substring(0, 1).toUpperCase() + displayHeader.substring(1);
                formatted.append("<th>").append(displayHeader).append("</th>");
            }
            formatted.append("</tr></thead><tbody>");

            // Sort indices by name
            java.util.List<org.json.JSONObject> sortedIndices = new ArrayList<org.json.JSONObject>();
            for (int i = 0; i < indices.length(); i++) sortedIndices.add(indices.getJSONObject(i));
            Collections.sort(sortedIndices, (a, b) -> a.optString("index", "").compareTo(b.optString("index", "")));

            for (org.json.JSONObject indexData : sortedIndices) {
                formatted.append("<tr>");
                for (String field : headers) {
                    String value = indexData.optString(field, "-");
                    if (field.equals("health")) {
                        String color = "gray";
                        if ("green".equals(value)) color = "green";
                        else if ("yellow".equals(value)) color = "yellow";
                        else if ("red".equals(value)) color = "red";
                        formatted.append("<td><span class='status-indicator status-" + color + "'>●</span> " + value + "</td>");
                    } else if (field.equals("index")) {
                        if (value.startsWith(".")) formatted.append("<td style='color: #6c757d; font-style: italic;'>" + value + "</td>");
                        else formatted.append("<td style='font-weight: 500;'>" + value + "</td>");
                    } else if (field.equals("pri") || field.equals("rep") || field.contains("docs") || field.contains("size")) {
                        // User requested strict left alignment, so we do not apply 'numeric-cell' class here
                        formatted.append("<td>" + value + "</td>");
                    } else {
                        formatted.append("<td>" + value + "</td>");
                    }
                }
                formatted.append("</tr>");
            }
            formatted.append("</tbody><tfoot><tr style='background: #f8f9fa; font-weight: 500;'>");
            formatted.append("<td colspan='3'>Total: ").append(indices.length()).append(" indices</td>");

            long totalDocs = 0;
            long totalDeleted = 0;
            for (int i = 0; i < indices.length(); i++) {
                org.json.JSONObject idx = indices.getJSONObject(i);
                try {
                    totalDocs += Long.parseLong(idx.optString("docs.count", "0"));
                    totalDeleted += Long.parseLong(idx.optString("docs.deleted", "0"));
                } catch (Exception e) {}
            }
            formatted.append("<td colspan='3'></td>");
            formatted.append("<td>").append(totalDocs).append("</td>");
            formatted.append("<td>").append(totalDeleted).append("</td>");
            formatted.append("<td colspan='2'></td></tr></tfoot>");

        } catch (Exception e) {
            formatted = new StringBuilder("<div class='error-message'>Error parsing table: " + e.getMessage() + "</div>");
        }
        formatted.append("</table></div>");
        return formatted.toString();
    }

    private JSONObject safeGetJSON(OpenSearch os, String endpoint) {
        try {
            Request req = new Request("GET", endpoint);
            String response = os.getRestResponse(req);
            return new JSONObject(response);
        } catch (Exception e) {
            JSONObject error = new JSONObject();
            error.put("error", e.getMessage());
            return error;
        }
    }

    private JSONArray safeGetJSONArray(OpenSearch os, String endpoint) {
        try {
            Request req = new Request("GET", endpoint);
            String response = os.getRestResponse(req);
            return new JSONArray(response);
        } catch (Exception e) {
            return new JSONArray();
        }
    }
%>

<html>
<head>
    <title>Wildbook OpenSearch Dashboard</title>
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif; font-size: 14px; line-height: 1.5; color: #333; background: #f4f4f4; padding: 15px; }
        .container { max-width: 1600px; margin: 0 auto; }
        h1 { color: #2c3e50; margin-bottom: 20px; font-size: 1.8em; font-weight: 500; border-bottom: 2px solid #dee2e6; padding-bottom: 10px; }
        .status-info { background: white; padding: 15px; border-radius: 4px; margin-bottom: 20px; border: 1px solid #dee2e6; }
        .metrics-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 10px; margin-bottom: 15px; }
        .metric-item { padding: 8px; background: #f8f9fa; border-radius: 3px; border-left: 3px solid #6c757d; font-size: 0.9em; }
        .metric-label { font-weight: 600; color: #495057; display: block; font-size: 0.85em; margin-bottom: 4px; }
        .metric-value { color: #212529; font-family: 'Courier New', monospace; font-size: 1.1em; font-weight: bold; }
        .table-wrapper { overflow-x: auto; margin: 15px 0; }
        .indices-table { width: 100%; border-collapse: collapse; background: white; font-size: 0.85em; }
        .indices-table th { background: #495057; color: white; padding: 8px; text-align: left; white-space: nowrap; position: sticky; top: 0; }
        /* Explicitly left align all cells as requested */
        .indices-table td { padding: 6px 8px; border-bottom: 1px solid #dee2e6; white-space: nowrap; text-align: left; }
        .indices-table tr:hover { background: #f8f9fa; }

        .status-indicator { font-size: 1.2em; }
        .status-green { color: #28a745; }
        .status-yellow { color: #ffc107; }
        .status-red { color: #dc3545; }

        h2 { margin-top: 30px; padding: 10px 15px; background: #495057; color: white; border-radius: 3px; font-size: 1.2em; font-weight: 500; display: flex; justify-content: space-between; align-items: center; }

        /* Restored original styling for H3 to space-between to push Copy button to right */
        h3 { color: #495057; margin: 15px 0 10px 0; font-size: 1em; font-weight: 600; padding-bottom: 8px; border-bottom: 1px solid #dee2e6; display: flex; justify-content: space-between; align-items: center; }

        .section-wrapper { background: white; padding: 15px; border-radius: 3px; margin-bottom: 15px; border: 1px solid #dee2e6; }

        /* Restored original dimensions */
        .json-viewer { width: 100%; min-height: 200px; max-height: 400px; padding: 10px; border: 1px solid #dee2e6; border-radius: 3px; background: #2b2b2b; color: #f8f8f2; font-family: 'Monaco', 'Menlo', 'Consolas', 'Courier New', monospace; font-size: 12px; line-height: 1.4; resize: vertical; overflow: auto; white-space: pre; }

        .copy-button { padding: 5px 10px; background: #6c757d; color: white; border: none; border-radius: 3px; cursor: pointer; font-size: 12px; font-weight: 500; transition: background 0.2s ease; }
        .copy-button:hover { background: #5a6268; }
        .copy-button.copied { background: #28a745; }

        .toggle-button { background: transparent; border: 1px solid white; color: white; padding: 3px 8px; border-radius: 3px; cursor: pointer; font-size: 11px; transition: background 0.2s ease; }
        .toggle-button:hover { background: rgba(255,255,255,0.1); }

        .error-message { background: #f8d7da; border: 1px solid #f5c6cb; border-radius: 3px; padding: 15px; margin: 20px 0; color: #721c24; }
        .error-message pre { margin-top: 10px; padding: 8px; background: rgba(255,255,255,0.5); border-radius: 3px; overflow-x: auto; font-size: 12px; }

        .refresh-info { text-align: right; color: #6c757d; font-size: 0.85em; margin-top: 20px; padding-top: 10px; border-top: 1px solid #dee2e6; }
        .index-content { display: none; }
        a { color: #0066cc; text-decoration: none; }
        a:hover { text-decoration: underline; color: #0052a3; }

        @media (max-width: 768px) {
            .metrics-grid { grid-template-columns: 1fr; }
            .indices-table { font-size: 0.75em; }
            .json-viewer { font-size: 11px; }
        }
    </style>
</head>
<body>
<div class="container">
    <h1>Wildbook OpenSearch Dashboard</h1>

    <%
        try {
            Shepherd myShepherd = new Shepherd(request);
            OpenSearch os = new OpenSearch();

            // --- DATA FETCHING (Batch Optimized) ---

            // 1. Cluster Health
            JSONObject clusterHealth = safeGetJSON(os, "_cluster/health");
            String status = clusterHealth.optString("status", "red");
            String statusColor = "green".equals(status) ? "#28a745" : ("yellow".equals(status) ? "#ffc107" : "#dc3545");

            // 2. Pending Tasks
            JSONArray pendingTasks = safeGetJSONArray(os, "_cat/pending_tasks?format=json");
            int pendingCount = pendingTasks.length();
            String pendingColor = pendingCount > 0 ? (pendingCount > 50 ? "#dc3545" : "#ffc107") : "#28a745";

            // 3. Indices Stats
            Request indicesReq = new Request("GET", "_cat/indices?format=json");
            String indicesData = os.getRestResponse(indicesReq);

            // 4. Bulk Mappings & Settings
            JSONObject allMappings = safeGetJSON(os, "_all/_mappings");
            JSONObject allSettings = safeGetJSON(os, "_all/_settings");
    %>

    <div class="status-info" style="border-left: 5px solid <%= statusColor %>;">
        <h2 style="margin-top: 0; background: none; color: #333; padding: 0; border: none;">
            Cluster Status: <span style="color: <%= statusColor %>; font-weight: bold;"><%= status.toUpperCase() %></span>
        </h2>
        <div class="metrics-grid">
            <div class="metric-item">
                <span class="metric-label">Cluster Name</span>
                <span class="metric-value"><%= clusterHealth.optString("cluster_name", "-") %></span>
            </div>
            <div class="metric-item">
                <span class="metric-label">Nodes / Data Nodes</span>
                <span class="metric-value"><%= clusterHealth.optInt("number_of_nodes", 0) %> / <%= clusterHealth.optInt("number_of_data_nodes", 0) %></span>
            </div>
            <div class="metric-item">
                <span class="metric-label">Active Shards</span>
                <span class="metric-value"><%= clusterHealth.optInt("active_shards", 0) %></span>
            </div>
            <div class="metric-item">
                <span class="metric-label">Unassigned Shards</span>
                <span class="metric-value"><%= clusterHealth.optInt("unassigned_shards", 0) %></span>
            </div>
            <div class="metric-item" style="border-left-color: <%= pendingColor %>">
                <span class="metric-label">Pending Tasks</span>
                <span class="metric-value"><%= pendingCount %></span>
            </div>
        </div>

        <% if (pendingCount > 0) { %>
        <div style="margin-top: 10px; font-size: 0.9em;">
            <strong>Warning:</strong> There are <%= pendingCount %> tasks waiting in the queue.
            <button class="copy-button" onclick="toggleSection('pending-tasks-list', this)">Show</button>
            <div id="pending-tasks-list" style="display:none; margin-top:5px;">
                <textarea class="json-viewer" readonly><%= pendingTasks.toString(2) %></textarea>
            </div>
        </div>
        <% } %>
    </div>

    <div class="status-info">
        <h3 style="border:none; margin-top:0;">App Configuration</h3>
        <div class="metrics-grid">
            <div class="metric-item">
                <span class="metric-label">Background Indexing</span>
                <span class="metric-value" style="color: <%= os.indexingActiveBackground() ? "#28a745" : "#6c757d" %>">
                    <%= os.indexingActiveBackground() %>
                </span>
            </div>
            <div class="metric-item">
                <span class="metric-label">Foreground Indexing</span>
                <span class="metric-value" style="color: <%= os.indexingActiveForeground() ? "#28a745" : "#6c757d" %>">
                    <%= os.indexingActiveForeground() %>
                </span>
            </div>
            <div class="metric-item">
                <span class="metric-label">Scroll Time</span>
                <span class="metric-value"><%= os.SEARCH_SCROLL_TIME %></span>
            </div>
        </div>
    </div>

    <div class="status-info">
        <h2 style="margin-top: 0; padding: 10px; background: #495057;">
            Indices Overview
            <button class="toggle-button" onclick="toggleSection('indices-overview', this)">Hide</button>
        </h2>
        <div id="indices-overview">
            <%= formatIndicesTable(indicesData) %>

            <div style="margin-top: 15px; padding: 12px; background: #f0f7ff; border-left: 4px solid #0066cc; border-radius: 3px;">
                <strong>Administrative Actions:</strong>
                <div style="margin-top: 8px; display: flex; flex-wrap: wrap; gap: 15px;">
                    <%
                        // DYNAMICALLY GENERATE SYNC LINKS
                        for (String validIndex : OpenSearch.VALID_INDICES) {
                            String displayName = validIndex.replace("_", " ");
                            displayName = Character.toUpperCase(displayName.charAt(0)) + displayName.substring(1);
                    %>
                    <a href="./opensearchSync.jsp?indexName=<%= validIndex %>" target="_blank" style="text-decoration: none; color: #0066cc; font-weight: 500;">
                            <span style="border: 1px solid #cce5ff; background: white; padding: 4px 8px; border-radius: 4px;">
                                &#x21bb; Sync <%= displayName %>s
                            </span>
                    </a>
                    <%
                        }
                    %>
                </div>
            </div>
        </div>
    </div>

    <%
        List<String> discoveredIndices = new ArrayList<String>(allMappings.keySet());
        Collections.sort(discoveredIndices);

        for (String indexName : discoveredIndices) {
            String uniqueId = indexName.replace("-", "_").replace(".", "_");
            JSONObject specificMapping = allMappings.optJSONObject(indexName);
            if (specificMapping == null) specificMapping = new JSONObject();

            JSONObject specificSettings = new JSONObject();
            if (allSettings.has(indexName)) {
                specificSettings = allSettings.getJSONObject(indexName).optJSONObject("settings");
            }

            JSONObject sampleDoc = safeGetJSON(os, indexName + "/_search?pretty=true&q=*:*&size=1");
    %>

    <h2>
        <%= indexName %>
        <button class="toggle-button" onclick="toggleIndex('<%= uniqueId %>', this)">Show</button>
    </h2>

    <div id="index_<%= uniqueId %>" class="index-content">
        <div class="section-wrapper">
            <h3>Mapping <button class="copy-button" onclick="copyToClipboard('mapping_<%= uniqueId %>', this)">Copy JSON</button></h3>
            <textarea id="mapping_<%= uniqueId %>" class="json-viewer" readonly><%= specificMapping.toString(4) %></textarea>
        </div>

        <div class="section-wrapper">
            <h3>Settings <button class="copy-button" onclick="copyToClipboard('settings_<%= uniqueId %>', this)">Copy JSON</button></h3>
            <textarea id="settings_<%= uniqueId %>" class="json-viewer" readonly><%= specificSettings.toString(4) %></textarea>
        </div>

        <div class="section-wrapper">
            <h3>Sample Document <button class="copy-button" onclick="copyToClipboard('sample_<%= uniqueId %>', this)">Copy JSON</button></h3>
            <textarea id="sample_<%= uniqueId %>" class="json-viewer" readonly><%= sampleDoc.toString(4) %></textarea>
        </div>
    </div>

    <%
        } // end loop

        myShepherd.rollbackAndClose();
    } catch (Exception e) {
    %>
    <div class="error-message">
        <h3>Fatal Error</h3>
        <pre><%= e.getMessage() %></pre>
        <p>Stack trace:</p>
        <pre><% e.printStackTrace(new java.io.PrintWriter(out)); %></pre>
    </div>
    <% } %>

    <div class="refresh-info">
        Page generated at: <%= new LocalDateTime() %>
        <button class="copy-button" onclick="location.reload()">Refresh</button>
    </div>
</div>

<script>
    // Restored Original Copy Functionality
    function copyToClipboard(textareaId, button) {
        const textarea = document.getElementById(textareaId);
        const tempTextarea = document.createElement('textarea');
        tempTextarea.value = textarea.value;
        tempTextarea.style.position = 'absolute';
        tempTextarea.style.left = '-9999px';
        document.body.appendChild(tempTextarea);

        tempTextarea.select();
        tempTextarea.setSelectionRange(0, 99999);

        try {
            document.execCommand('copy');
            const originalText = button.innerHTML;
            button.innerHTML = 'Copied!';
            button.classList.add('copied');
            setTimeout(() => {
                button.innerHTML = originalText;
                button.classList.remove('copied');
            }, 2000);
        } catch (err) {
            alert('Failed to copy');
        }
        document.body.removeChild(tempTextarea);
    }

    // New Dynamic Toggle Function
    // Handles toggling display AND updating button text
    function toggleSection(sectionId, btn) {
        const section = document.getElementById(sectionId);
        if (section.style.display === 'none') {
            section.style.display = 'block';
            if(btn) btn.innerHTML = 'Hide';
        } else {
            section.style.display = 'none';
            if(btn) btn.innerHTML = 'Show';
        }
    }

    function toggleIndex(indexId, btn) {
        const content = document.getElementById('index_' + indexId);
        if (content.style.display === 'block') {
            content.style.display = 'none';
            if(btn) btn.innerHTML = 'Show';
        } else {
            content.style.display = 'block';
            if(btn) btn.innerHTML = 'Hide';
        }
    }

    // Restored Original Auto-Resize Script
    document.addEventListener('DOMContentLoaded', function() {
        const textareas = document.querySelectorAll('.json-viewer');
        textareas.forEach(textarea => {
            const lines = textarea.value.split('\n').length;
            const lineHeight = 20;
            const calculatedHeight = Math.min(600, Math.max(300, lines * lineHeight));
            textarea.style.height = calculatedHeight + 'px';
        });
    });

    document.addEventListener('keydown', function(e) {
        if ((e.ctrlKey || e.metaKey) && e.key === 'r') {
            e.preventDefault();
            location.reload();
        }
    });
</script>
</body>
</html>
