<%@ page import="java.util.*" %>

<jsp:include page="header.jsp" flush="true"/>

<style>
    table {
        border-collapse: collapse;
        width: 100%;
    }

    td, th {
        border: 1px solid #dddddd;
        text-align: left;
        padding: 8px;
    }

    tr:nth-child(even) {
        background-color: #dddddd;
    }
</style>

<div class="container maincontent">

    <h2>Failed Tasks</h2>
    <table>
        <tr>
            <th>ID</th>
            <th>Encounters</th>
            <th>Type</th>
            <th>Status</th>
            <th>Created</th>
            <th>Modified</th>
            <th>Action</th>
        </tr>
        <%
            List<HashMap<String, Object>> tasks = (List<HashMap<String, Object>>) request.getAttribute("tasks");
            for (HashMap<String, Object> task : tasks) {
        %>
        <tr>
            <td><%= task.get("id") %>
            </td>
            <td>
                <ul>
                    <%
                        for (String encounterId : (ArrayList<String>) task.get("encounterIdList")) {
                    %>
                    <li><a href="/encounters/encounter.jsp?number=<%= encounterId %>">
                        <%= encounterId %>
                    </a></li>
                    <% } %>
                </ul>
            </td>
            <td><%= task.get("taskType") %>
            </td>
            <td><%= task.get("status") %>
            </td>
            <td><%= task.get("created") %>
            </td>
            <td><%= task.get("modified") %>
            </td>
            <td>
                <form method="post" action="retry">
                    <input type="hidden" name="taskId" value="<%= task.get("id") %>"/>
                    <button type="submit">Retry</button>
                </form>
            </td>
        </tr>
        <% } %>
    </table>

</div>

<jsp:include page="footer.jsp" flush="true"/>
