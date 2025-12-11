<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.opensearch.client.Request,
java.util.List, java.util.ArrayList,
org.json.JSONObject,
org.ecocean.*,
org.ecocean.shepherd.core.*
"%>

<%!
    /**
     * Formats the OpenSearch cat indices JSON output as an HTML table
     * @param jsonData The JSON response from _cat/indices?format=json
     * @return HTML table string
     */
    private String formatIndicesTable(String jsonData) {
        if (jsonData == null || jsonData.trim().isEmpty()) {
            return "<p>No indices data available</p>";
        }

        StringBuilder formatted = new StringBuilder();
        formatted.append("<div class='table-wrapper'>");
        formatted.append("<table class='indices-table'>");

        try {
            // Parse the JSON array
            org.json.JSONArray indices = new org.json.JSONArray(jsonData);

            if (indices.length() == 0) {
                return "<p>No indices found</p>";
            }

            // Define headers in the order we want them displayed
            String[] headers = {
                    "health", "status", "index", "uuid", "pri", "rep",
                    "docs.count", "docs.deleted", "store.size", "pri.store.size"
            };

            // Create table header
            formatted.append("<thead><tr>");
            for (String header : headers) {
                // Make headers more readable
                String displayHeader = header.replace(".", " ").replace("_", " ");
                displayHeader = displayHeader.substring(0, 1).toUpperCase() + displayHeader.substring(1);
                formatted.append("<th>").append(displayHeader).append("</th>");
            }
            formatted.append("</tr></thead>");

            // Create table body
            formatted.append("<tbody>");

            // Sort indices by name for consistency
            java.util.List<org.json.JSONObject> sortedIndices = new ArrayList<org.json.JSONObject>();
            for (int i = 0; i < indices.length(); i++) {
                sortedIndices.add(indices.getJSONObject(i));
            }
            java.util.Collections.sort(sortedIndices, new java.util.Comparator<org.json.JSONObject>() {
                public int compare(org.json.JSONObject a, org.json.JSONObject b) {
                    return a.optString("index", "").compareTo(b.optString("index", ""));
                }
            });

            // Process each index
            for (org.json.JSONObject indexData : sortedIndices) {
                // Skip system indices if desired (those starting with .)
                String indexName = indexData.optString("index", "");
                // Optional: uncomment to hide system indices
                // if (indexName.startsWith(".")) continue;

                formatted.append("<tr>");

                for (String field : headers) {
                    String value = indexData.optString(field, "-");

                    // Apply special formatting based on field type
                    if (field.equals("health")) {
                        if (value.equals("green")) {
                            formatted.append("<td><span class='status-indicator status-green'>●</span> green</td>");
                        } else if (value.equals("yellow")) {
                            formatted.append("<td><span class='status-indicator status-yellow'>●</span> yellow</td>");
                        } else if (value.equals("red")) {
                            formatted.append("<td><span class='status-indicator status-red'>●</span> red</td>");
                        } else {
                            formatted.append("<td>").append(value).append("</td>");
                        }
                    } else if (field.equals("status")) {
                        formatted.append("<td class='index-status'>").append(value).append("</td>");
                    } else if (field.equals("index")) {
                        // Highlight system indices
                        if (value.startsWith(".")) {
                            formatted.append("<td style='color: #6c757d; font-style: italic;'>").append(value).append("</td>");
                        } else {
                            formatted.append("<td style='font-weight: 500;'>").append(value).append("</td>");
                        }
                    } else if (field.equals("uuid")) {
                        formatted.append("<td style='font-family: monospace; font-size: 0.85em; color: #6c757d;'>")
                                .append(value).append("</td>");
                    } else if (field.equals("pri") || field.equals("rep") ||
                            field.contains("docs") || field.contains("size")) {
                        // Numeric fields
                        formatted.append("<td class='numeric-cell'>").append(value).append("</td>");
                    } else {
                        formatted.append("<td>").append(value).append("</td>");
                    }
                }

                formatted.append("</tr>");
            }

            formatted.append("</tbody>");

            // Add summary row
            formatted.append("<tfoot><tr style='background: #f8f9fa; font-weight: 500;'>");
            formatted.append("<td colspan='3'>Total: ").append(indices.length()).append(" indices</td>");

            // Calculate totals for numeric columns
            long totalDocs = 0;
            long totalDeleted = 0;
            for (int i = 0; i < indices.length(); i++) {
                org.json.JSONObject idx = indices.getJSONObject(i);
                try {
                    totalDocs += Long.parseLong(idx.optString("docs.count", "0"));
                    totalDeleted += Long.parseLong(idx.optString("docs.deleted", "0"));
                } catch (NumberFormatException e) {
                    // Skip if not a number
                }
            }

            formatted.append("<td colspan='4'></td>");
            formatted.append("<td class='numeric-cell'>").append(totalDocs).append("</td>");
            formatted.append("<td class='numeric-cell'>").append(totalDeleted).append("</td>");
            formatted.append("<td colspan='2'></td>");
            formatted.append("</tr></tfoot>");

        } catch (Exception e) {
            // If JSON parsing fails, show error and raw data
            formatted = new StringBuilder();
            formatted.append("<div class='error-message' style='margin: 10px 0;'>");
            formatted.append("<p><strong>Error parsing indices data:</strong> ").append(e.getMessage()).append("</p>");
            formatted.append("<p>Raw JSON response:</p>");
            formatted.append("<pre style='background: #2b2b2b; color: #f8f8f2; padding: 10px; border-radius: 3px; overflow-x: auto; font-size: 12px;'>");

            // Try to pretty-print the JSON if possible
            try {
                org.json.JSONArray jsonArray = new org.json.JSONArray(jsonData);
                formatted.append(jsonArray.toString(2));
            } catch (Exception ex) {
                // If that fails too, just show raw data
                formatted.append(jsonData);
            }

            formatted.append("</pre></div>");
        }

        formatted.append("</table>");
        formatted.append("</div>");

        return formatted.toString();
    }

    /**
     * Safely gets JSON response with error handling
     */
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
%>

<html>
<head>
    <title>Wildbook OpenSearch Index Information</title>
    <meta name="viewport" content="width=device-width, initial-scale=1.0">

    <style>
        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }

        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
            font-size: 14px;
            line-height: 1.5;
            color: #333;
            background: #f4f4f4;
            padding: 15px;
        }

        .container {
            max-width: 1600px;
            margin: 0 auto;
        }

        h1 {
            color: #2c3e50;
            margin-bottom: 20px;
            font-size: 1.8em;
            font-weight: 500;
            border-bottom: 2px solid #dee2e6;
            padding-bottom: 10px;
        }

        .status-info {
            background: white;
            padding: 15px;
            border-radius: 4px;
            margin-bottom: 20px;
            border: 1px solid #dee2e6;
        }

        .metrics-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
            gap: 10px;
            margin-bottom: 15px;
        }

        .metric-item {
            padding: 8px;
            background: #f8f9fa;
            border-radius: 3px;
            border-left: 3px solid #6c757d;
            font-size: 0.9em;
        }

        .metric-label {
            font-weight: 600;
            color: #495057;
            display: inline-block;
            min-width: 250px;
            font-family: 'Courier New', monospace;
            font-size: 0.85em;
        }

        .metric-value {
            color: #212529;
            font-family: 'Courier New', monospace;
        }

        .indexing-status {
            padding: 10px;
            background: #e9ecef;
            border-radius: 3px;
            font-size: 0.9em;
        }

        .active-true {
            color: #28a745;
            font-weight: 600;
        }

        .active-false {
            color: #dc3545;
            font-weight: 600;
        }

        .table-wrapper {
            overflow-x: auto;
            margin: 15px 0;
        }

        .indices-table {
            width: 100%;
            border-collapse: collapse;
            background: white;
            font-size: 0.85em;
        }

        .indices-table th {
            background: #495057;
            color: white;
            padding: 8px;
            text-align: left;
            font-weight: 500;
            font-size: 0.85em;
            white-space: nowrap;
            position: sticky;
            top: 0;
        }

        /* Right-align numeric column headers to match their data */
        .indices-table th:nth-child(5),  /* Pri */
        .indices-table th:nth-child(6),  /* Rep */
        .indices-table th:nth-child(7),  /* Docs count */
        .indices-table th:nth-child(8),  /* Docs deleted */
        .indices-table th:nth-child(9),  /* Store size */
        .indices-table th:nth-child(10) { /* Pri store size */
            text-align: right;
        }

        .indices-table td {
            padding: 6px 8px;
            border-bottom: 1px solid #dee2e6;
            white-space: nowrap;
        }

        .indices-table tr:hover {
            background: #f8f9fa;
        }

        .status-indicator {
            font-size: 1.2em;
        }

        .status-green {
            color: #28a745;
        }

        .status-yellow {
            color: #ffc107;
        }

        .status-red {
            color: #dc3545;
        }

        .index-status {
            font-weight: 500;
        }

        .numeric-cell {
            font-family: 'Courier New', monospace;
            text-align: right;
        }

        h2 {
            margin-top: 30px;
            padding: 10px 15px;
            background: #495057;
            color: white;
            border-radius: 3px;
            font-size: 1.2em;
            font-weight: 500;
            display: flex;
            justify-content: space-between;
            align-items: center;
        }

        h3 {
            color: #495057;
            margin: 15px 0 10px 0;
            font-size: 1em;
            font-weight: 600;
            padding-bottom: 8px;
            border-bottom: 1px solid #dee2e6;
            display: flex;
            justify-content: space-between;
            align-items: center;
        }

        .section-wrapper {
            background: white;
            padding: 15px;
            border-radius: 3px;
            margin-bottom: 15px;
            border: 1px solid #dee2e6;
        }

        .json-viewer {
            width: 100%;
            min-height: 200px;
            max-height: 400px;
            padding: 10px;
            border: 1px solid #dee2e6;
            border-radius: 3px;
            background: #2b2b2b;
            color: #f8f8f2;
            font-family: 'Monaco', 'Menlo', 'Consolas', 'Courier New', monospace;
            font-size: 12px;
            line-height: 1.4;
            resize: vertical;
            overflow: auto;
            white-space: pre;
        }

        .copy-button {
            padding: 5px 10px;
            background: #6c757d;
            color: white;
            border: none;
            border-radius: 3px;
            cursor: pointer;
            font-size: 12px;
            font-weight: 500;
            transition: background 0.2s ease;
        }

        .copy-button:hover {
            background: #5a6268;
        }

        .copy-button.copied {
            background: #28a745;
        }

        .toggle-button {
            background: transparent;
            border: 1px solid white;
            color: white;
            padding: 3px 8px;
            border-radius: 3px;
            cursor: pointer;
            font-size: 11px;
            transition: background 0.2s ease;
        }

        .toggle-button:hover {
            background: rgba(255,255,255,0.1);
        }

        .error-message {
            background: #f8d7da;
            border: 1px solid #f5c6cb;
            border-radius: 3px;
            padding: 15px;
            margin: 20px 0;
            color: #721c24;
        }

        .error-message h3 {
            color: #721c24;
            border-bottom-color: #f5c6cb;
        }

        .error-message pre {
            margin-top: 10px;
            padding: 8px;
            background: rgba(255,255,255,0.5);
            border-radius: 3px;
            overflow-x: auto;
            font-size: 12px;
        }

        .refresh-info {
            text-align: right;
            color: #6c757d;
            font-size: 0.85em;
            margin-top: 20px;
            padding-top: 10px;
            border-top: 1px solid #dee2e6;
        }

        a {
            color: #0066cc;
            text-decoration: none;
        }

        a:hover {
            text-decoration: underline;
            color: #0052a3;
        }

        code {
            font-family: 'Monaco', 'Menlo', 'Consolas', 'Courier New', monospace;
        }

        @media (max-width: 768px) {
            .metrics-grid {
                grid-template-columns: 1fr;
            }

            .indices-table {
                font-size: 0.75em;
            }

            .json-viewer {
                font-size: 11px;
            }
        }
    </style>
</head>
<body>
<div class="container">
    <h1>Wildbook OpenSearch Index Information</h1>

    <%
        try {
            Shepherd myShepherd = new Shepherd(request);
            OpenSearch os = new OpenSearch();
    %>

    <div class="status-info">
        <h2 style="margin-top: 0;">Configuration Parameters</h2>
        <div class="metrics-grid">
            <div class="metric-item">
                <span class="metric-label">SEARCH_SCROLL_TIME:</span>
                <span class="metric-value"><%= os.SEARCH_SCROLL_TIME %></span>
            </div>
            <div class="metric-item">
                <span class="metric-label">SEARCH_PIT_TIME:</span>
                <span class="metric-value"><%= os.SEARCH_PIT_TIME %></span>
            </div>
            <div class="metric-item">
                <span class="metric-label">BACKGROUND_DELAY_MINUTES:</span>
                <span class="metric-value"><%= os.BACKGROUND_DELAY_MINUTES %></span>
            </div>
            <div class="metric-item">
                <span class="metric-label">BACKGROUND_SLICE_SIZE:</span>
                <span class="metric-value"><%= os.BACKGROUND_SLICE_SIZE %></span>
            </div>
            <div class="metric-item">
                <span class="metric-label">BACKGROUND_PERMISSIONS_MINUTES:</span>
                <span class="metric-value"><%= os.BACKGROUND_PERMISSIONS_MINUTES %></span>
            </div>
            <div class="metric-item">
                <span class="metric-label">BACKGROUND_PERMISSIONS_MAX_FORCE_MINUTES:</span>
                <span class="metric-value"><%= os.BACKGROUND_PERMISSIONS_MAX_FORCE_MINUTES %></span>
            </div>
        </div>

        <div class="indexing-status">
            <strong>Active Indexing:</strong>
            Foreground = <span class="active-<%= os.indexingActiveForeground() %>"><%= os.indexingActiveForeground() %></span>
            &nbsp;|&nbsp;
            Background = <span class="active-<%= os.indexingActiveBackground() %>"><%= os.indexingActiveBackground() %></span>
        </div>
    </div>

    <%
        // Get indices overview in JSON format
        Request req = new Request("GET", "_cat/indices?format=json");
        String indicesData = os.getRestResponse(req);
    %>

    <div class="status-info">
        <h2 style="margin-top: 0;">
            Indices Overview
            <button class="toggle-button" onclick="toggleSection('indices-overview')">Hide/Show</button>
        </h2>
        <div id="indices-overview" class="collapsible-content">
            <%= formatIndicesTable(indicesData) %>
            <div style="margin-top: 15px; padding: 12px; background: #f0f7ff; border-left: 4px solid #0066cc; border-radius: 3px;">
                <strong style="color: #0066cc;">Index Re-sync:</strong> To re-sync an individual index, visit
                <code style="background: white; padding: 2px 6px; border-radius: 3px; font-size: 0.9em; border: 1px solid #dee2e6;">./appadmin/opensearchSync.jsp?indexName=&lt;indexname&gt;</code>
                <br/>
                <div style="margin-top: 8px;">
                    <strong>Quick links:</strong>
                    <a href="./opensearchSync.jsp?indexName=encounter" target="_blank" style="margin: 0 8px;">Sync Encounters</a> |
                    <a href="./opensearchSync.jsp?indexName=individual" target="_blank" style="margin: 0 8px;">Sync Individuals</a> |
                    <a href="./opensearchSync.jsp?indexName=occurrence" target="_blank" style="margin: 0 8px;">Sync Occurrences</a> |
                    <a href="./opensearchSync.jsp?indexName=annotation" target="_blank" style="margin: 0 8px;">Sync Annotations</a> |
                    <a href="./opensearchSync.jsp?indexName=media_asset" target="_blank" style="margin: 0 8px;">Sync Media Assets</a>
                </div>
            </div>
        </div>
    </div>

    <%
        // Process each valid index
        for (String indexName : OpenSearch.VALID_INDICES) {
            String uniqueId = indexName.replace("-", "_").replace(".", "_");

            // Get mappings
            JSONObject mappings = safeGetJSON(os, indexName + "/_mappings");

            // Get settings
            JSONObject settings = null;
            try {
                settings = os.getSettings(indexName);
            } catch (Exception e) {
                settings = new JSONObject();
                settings.put("error", "Unable to retrieve settings: " + e.getMessage());
            }

            // Get sample document
            JSONObject sampleDoc = safeGetJSON(os, indexName + "/_search?pretty=true&q=*:*&size=1");
    %>

    <h2>
        <%= indexName %>
        <button class="toggle-button" onclick="toggleIndex('<%= uniqueId %>')">Hide/Show All</button>
    </h2>

    <div id="index_<%= uniqueId %>" class="index-content">
        <div class="section-wrapper">
            <h3>
                Mapping
                <button class="copy-button" onclick="copyToClipboard('mapping_<%= uniqueId %>', this)">Copy JSON</button>
            </h3>
            <textarea id="mapping_<%= uniqueId %>" class="json-viewer" readonly><%= mappings.toString(4) %></textarea>
        </div>

        <div class="section-wrapper">
            <h3>
                Settings
                <button class="copy-button" onclick="copyToClipboard('settings_<%= uniqueId %>', this)">Copy JSON</button>
            </h3>
            <textarea id="settings_<%= uniqueId %>" class="json-viewer" readonly><%= settings.toString(4) %></textarea>
        </div>

        <div class="section-wrapper">
            <h3>
                Sample Document
                <button class="copy-button" onclick="copyToClipboard('sample_<%= uniqueId %>', this)">Copy JSON</button>
            </h3>
            <textarea id="sample_<%= uniqueId %>" class="json-viewer" readonly><%= sampleDoc.toString(4) %></textarea>
        </div>
    </div>

    <%
        } // end for each index

        myShepherd.rollbackAndClose();

    } catch (Exception e) {
    %>

    <div class="error-message">
        <h3>Error retrieving OpenSearch information</h3>
        <pre><%= e.getMessage() %></pre>
        <p>Stack trace:</p>
        <pre>
<%
    for (StackTraceElement element : e.getStackTrace()) {
        out.println(element.toString());
    }
%>
            </pre>
    </div>

    <%
        }
    %>

    <div class="refresh-info">
        Page generated at: <%= new LocalDateTime() %>
        <button class="copy-button" onclick="location.reload()">Refresh</button>
    </div>
</div>

<script>
    // Copy to clipboard function with visual feedback
    function copyToClipboard(textareaId, button) {
        const textarea = document.getElementById(textareaId);

        // Create a temporary textarea to copy from (to avoid selection issues)
        const tempTextarea = document.createElement('textarea');
        tempTextarea.value = textarea.value;
        tempTextarea.style.position = 'absolute';
        tempTextarea.style.left = '-9999px';
        document.body.appendChild(tempTextarea);

        // Select and copy
        tempTextarea.select();
        tempTextarea.setSelectionRange(0, 99999); // For mobile devices

        try {
            document.execCommand('copy');

            // Visual feedback
            const originalText = button.innerHTML;
            button.innerHTML = 'Copied!';
            button.classList.add('copied');

            setTimeout(() => {
                button.innerHTML = originalText;
                button.classList.remove('copied');
            }, 2000);
        } catch (err) {
            alert('Failed to copy to clipboard');
        }

        // Clean up
        document.body.removeChild(tempTextarea);
    }

    // Toggle section visibility
    function toggleSection(sectionId) {
        const section = document.getElementById(sectionId);
        if (section.style.display === 'none') {
            section.style.display = 'block';
        } else {
            section.style.display = 'none';
        }
    }

    // Toggle entire index content
    function toggleIndex(indexId) {
        const indexContent = document.getElementById('index_' + indexId);
        if (indexContent.style.display === 'none') {
            indexContent.style.display = 'block';
        } else {
            indexContent.style.display = 'none';
        }
    }

    // Auto-resize textareas based on content
    document.addEventListener('DOMContentLoaded', function() {
        const textareas = document.querySelectorAll('.json-viewer');
        textareas.forEach(textarea => {
            // Set initial height based on content
            const lines = textarea.value.split('\n').length;
            const lineHeight = 20; // approximate line height in pixels
            const calculatedHeight = Math.min(600, Math.max(300, lines * lineHeight));
            textarea.style.height = calculatedHeight + 'px';
        });
    });

    // Add keyboard shortcut for refresh (Ctrl+R or Cmd+R)
    document.addEventListener('keydown', function(e) {
        if ((e.ctrlKey || e.metaKey) && e.key === 'r') {
            e.preventDefault();
            location.reload();
        }
    });
</script>
</body>
</html>
