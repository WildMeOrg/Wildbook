<html><head><title>KitizenScience: results (alpha)</title>
<script src="../javascript/excel.js"></script>
<script>
function exportExcel(aEl) {
    var d = new Date();
    aEl.download = 'kitizen-science-trial-results-' + d.toISOString().substr(0,10) + '.xls';
    aEl.href = exportTableToExcelUri(document.getElementById('data'), 'Trial Results');
    return true;
}
</script>
</head><style>
body {
    font-family: arial;
}
td {
    padding: 2px 10px;
    border: solid 1px #AAA;
    font-size: 0.85em;
}
tr.start {
    background-color: #ABF;
}
</style>
<%@ page contentType="text/html; charset=utf-8" language="java"
     import="org.ecocean.*,
java.io.File,
java.util.List,
org.joda.time.DateTime,
java.util.Collection,
java.nio.file.Files,
java.nio.charset.Charset,
javax.jdo.Query,
org.json.JSONObject,
org.json.JSONArray
              "
%><body>
<p>
    <a href="#" onClick="return exportExcel(this)">download as excel</a>
</p>
<table id="data">
<thead>
<tr>
    <th>trial id</th>
    <th>seq</th>
    <th>time</th>
    <th>timestamp</th>
    <th>username</th>
    <th>trial</th>
    <th>library</th>
    <th>match?</th>
    <th>response</th>
    <th>result</th>
</tr>
</thead>
<tbody>
<%!
private static String correctAnswer(String trial, String library, JSONObject key) {
    if (key.optJSONArray(trial) == null) return "no";
    JSONArray matches = key.getJSONArray(trial);
    for (int i = 0 ; i < matches.length() ; i++) {
        if (library.equals(matches.getString(i))) return "yes";
    }
    return "no";
}
%><%

Shepherd myShepherd = new Shepherd("context0");
myShepherd.beginDBTransaction();
JSONObject key = SystemValue.getJSONObject(myShepherd, "trialKey");

int idStart = 0;
try {
    idStart = Integer.parseInt(request.getParameter("idStart"));
} catch (NumberFormatException ex) {}


String jdoql = "SELECT FROM org.ecocean.CatTest WHERE id >= " + idStart;
Query q = myShepherd.getPM().newQuery(jdoql);
q.setOrdering("timestamp");
Collection all = (Collection) (q.execute());
for (Object o : all) {
    CatTest ct = (CatTest)o;
    JSONArray res = ct.getResultsAsJSONArray();
    long start = 0;
    long prev = 0;

    for (int i = 0 ; i < res.length() ; i++) {
        out.println("<tr" + ((start < 1) ? " class=\"start\"" : "") + "><td>" + ct.getId() + "</td><td>" + (i + 1) + "</td>");
        JSONObject pair = res.optJSONObject(i);
        if (pair == null) throw new RuntimeException("non-JSONObject at i=" + i + " for CatTest id=" + ct.getId());
        if (start < 1) {
            start = pair.optLong("t", -1L);
            DateTime st = new DateTime(start);
            out.println("<td>" + st.toString().substring(0,19).replace("T", " ") + "</td>");
            prev = start;
        } else {
            long t = pair.optLong("t", -2L);
            //double sec = Math.round((t - start) / 100d) / 10d;
            double min = Math.round(((t - start) / 60) / 10d) / 100d;
            double delta = Math.round((t - prev) / 100d) / 10d;
            out.println("<td>+ " + min + " min &nbsp; \u0394" + delta + "s</td>");
            prev = t;
        }
        out.println("<td>" + (pair.optLong("t", -3L) / 1000L) + "</td>");
        out.println("<td>" + ct.getUsername() + "</td>");
        out.println("<td>" + ct.getTrial() + "</td>");
        String test = pair.getJSONObject("test").getString("indivId");
        out.println("<td>" + test + "</td>");
        String correct = correctAnswer(ct.getTrial(), test, key);
        out.println("<td>" + correct + "</td>");
        String resp = pair.getString("response");
        out.println("<td>" + resp + "</td>");
        String resType = null;
        switch (correct + resp) {
            case "nono":
                resType = "true-negative";
                break;
            case "yesyes":
                resType = "true-positive";
                break;
            case "noyes":
                resType = "false-positive";
                break;
            case "yesno":
                resType = "false-negative";
                break;
        }
        out.println("<td>" + resType + "</td>");
        out.println("</tr>");
    }
}
q.closeAll();

myShepherd.rollbackDBTransaction();


%></tbody></table></body></html>
