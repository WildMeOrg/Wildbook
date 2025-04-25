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

    .pagination {
        width: 100%;
        text-align: center;
    }

    .pagination a {
        padding-left: 7px;
        padding-right: 7px;
        padding-bottom: 4px;
    }

    .top-bar {
        display: flex;
        align-items: center;
    }

    .top-bar > * {
        margin: 10px;
        margin-left: 0;
    }
</style>

<div class="container maincontent">

    <h2>Failed Tasks</h2>

    <div class="top-bar">
        <%
            String taskTypeQueryParam = (String) request.getAttribute("taskTypeQueryParam");
            String detectionDisabled = taskTypeQueryParam.equals("detection") ? "disabled" : "";
            String matcherDisabled = taskTypeQueryParam.equals("matcher") ? "disabled" : "";
        %>

        <h4>Task type:</h4>
        <button type="button" class="btn" onclick="window.location.href='?type=detection'" <%= detectionDisabled %>>
            Detection
        </button>
        <button type="button" class="btn" onclick="window.location.href='?type=matcher'" <%= matcherDisabled %>>
            Matcher
        </button>

        <button type="button" class="btn" id="retry-selected" style="margin-left: auto;">
            Retry All Selected
        </button>
    </div>

    <table>
        <tr>
            <th>
                <input type="checkbox" name="select-all"/>
            </th>
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
            if (tasks.size() > 0) {
                for (HashMap<String, Object> task : tasks) {
        %>
        <tr>
            <td>
                <input type="checkbox" name="taskIds[]" value="<%= task.get("id") %>"/>
            </td>
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
        <% }
        } else { %>
        <tr>
            <td colspan="7" style="text-align: center;">No Results.</td>
        </tr>
        <% } %>
    </table>

    <div class="pagination">
        <% Boolean previousPage = (Boolean) request.getAttribute("previousPage"); %>
        <% Boolean nextPage = (Boolean) request.getAttribute("nextPage"); %>
        <% int currentPage = (Integer) request.getAttribute("page"); %>

        <% if (previousPage) { %>
        <a href="?type=<%= taskTypeQueryParam %>&page=<%= currentPage - 1 %>">&laquo; Previous</a>
        <% } %>

        <a href="?type=<%= taskTypeQueryParam %>&page=<%= currentPage %>">Page <%= currentPage %>
        </a>

        <% if (nextPage) { %>
        <a href="?type=<%= taskTypeQueryParam %>&page=<%= currentPage + 1 %>">Next &raquo;</a>
        <% } %>
    </div>
</div>

<script>
    function setWindowLocationHref (u) {
        window.location.href = u;
    }

    document.addEventListener('DOMContentLoaded', function () {
        const checkboxes = document.querySelectorAll('input[name="taskIds[]"]');
        const selectAllCheckbox = document.querySelector('input[name="select-all"]');
        selectAllCheckbox.addEventListener('change', function () {
            const checked = this.checked;
            checkboxes.forEach(checkbox => {
                checkbox.checked = checked;
            });
        });

        const retrySelected = document.querySelector('#retry-selected');
        retrySelected.addEventListener('click', function () {
            const checkedCheckboxes = document.querySelectorAll('input[name="taskIds[]"]:checked');

            if (checkedCheckboxes.length > 0) {
                const ids = Array.from(checkedCheckboxes).map((cb) => {return cb.value}).join(',');
                setWindowLocationHref('/retry?taskIds=' + ids);
            } else {
                alert('Nothing selected. Select some tasks to retry first.');
            }
        });
    });
</script>

<jsp:include page="footer.jsp" flush="true"/>
