<html><head><title>KitizenScience: survey results (alpha)</title>
<script src="../javascript/excel.js"></script>
<script>
function exportExcel(aEl) {
    var d = new Date();
    aEl.download = 'kitizen-science-user-export-' + d.toISOString().substr(0,10) + '.xls';
    aEl.href = exportTableToExcelUri(document.getElementById('data'), 'User Export');
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
java.util.Iterator,
java.util.List,
java.util.ArrayList,
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
<thead><tr>
<%!
private static String cleanDate(Long ms) {
    if ((ms == null) || (ms < 1L)) return "";
    DateTime dt = new DateTime(ms);
    return dt.toString().substring(0,16).replaceAll("T", " ");
}

%><%

Shepherd myShepherd = new Shepherd("context0");
myShepherd.beginDBTransaction();
//JSONObject key = SystemValue.getJSONObject(myShepherd, "trialKey");


String jdoql = "SELECT FROM org.ecocean.User";
Query q = myShepherd.getPM().newQuery(jdoql);
q.setOrdering("emailAddress.toLowerCase()");
Collection all = (Collection) (q.execute());

//String filter = request.getParameter("filter");

List<String> header = new ArrayList<String>();
header.add("username");
header.add("uuid");
header.add("email");
header.add("trials");
header.add("u-w");
header.add("signup");
header.add("last login");

for (String h : header) {
    out.println("<th>" + h + "</th>");
}
out.println("</tr></thead><tbody>");

for (Object o : all) {
    User user = (User)o;

    String sql = "SELECT count(*) FROM \"CATTEST\" WHERE \"USERNAME\"='" + user.getUsername() + "';";
    q = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
    List results = (List)q.execute();
    Long count = (Long) results.iterator().next();

    out.println("<tr>");
    //out.println("<td>[" + user.getUsername() + "]</td>");
    out.println("<td>" + user.getUsername() + "</td>");
    out.println("<td>" + user.getUUID() + "</td>");
    out.println("<td>" + user.getEmailAddress() + "</td>");
    out.println("<td>" + count + "</td>");
    out.println("<td>" + ("U-W".equals(user.getAffiliation()) ? "Y" : "") + "</td>");
    out.println("<td>" + cleanDate(user.getDateInMilliseconds()) + "</td>");
    out.println("<td>" + cleanDate(user.getLastLogin()) + "</td>");
    out.println("</tr>");
}
q.closeAll();

myShepherd.rollbackDBTransaction();


%></tbody></table></body></html>
