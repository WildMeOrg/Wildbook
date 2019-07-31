<html><head><title>KitizenScience: survey results (alpha)</title>
<script src="../javascript/excel.js"></script>
<script>
function exportExcel(aEl) {
    var d = new Date();
    aEl.download = 'kitizen-science-survey-results-' + d.toISOString().substr(0,10) + '.xlsx';
    aEl.href = exportTableToExcelUri(document.getElementById('data'), 'Survey Results');
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
<%

Shepherd myShepherd = new Shepherd("context0");
myShepherd.beginDBTransaction();
//JSONObject key = SystemValue.getJSONObject(myShepherd, "trialKey");


List<String> header = null;
String jdoql = "SELECT FROM org.ecocean.SystemValue WHERE key.startsWith('survey_response_')";
Query q = myShepherd.getPM().newQuery(jdoql);
q.setOrdering("version");
Collection all = (Collection) (q.execute());
for (Object o : all) {
    SystemValue sv = (SystemValue)o;
    JSONObject surv = sv.getValue().getJSONObject("value");
    if (header == null) {
        header = new ArrayList<String>();
        header.add("username");
        out.println("<th>username</th>");
        Iterator it = surv.keys();
        while (it.hasNext()) {
            String key = (String)it.next();
            header.add(key);
            out.println("<th>" + key + "</th>");
        }
        out.println("</tr></thead><tbody>");
    }
    out.println("<tr>");
    User user = myShepherd.getUserByUUID(surv.optString("user_uuid", "_NO_"));
    surv.put("username", (user == null) ? "???" : user.getUsername());

    for (String key : header) {
        String value = surv.optString(key, "ERROR");
        if (key.equals("ethnicity") || key.equals("have_cats")) {
            JSONArray varr = surv.optJSONArray(key);
            if (varr != null) {
                List<String> v = new ArrayList<String>();
                for (int i = 0 ; i < varr.length() ; i++) {
                    v.add(varr.optString(i, "??"));
                }
                value = String.join(" | ", v);
            }
        }
        out.println("<td>" + value + "</td>");
    }
    out.println("</tr>");
}
q.closeAll();

myShepherd.rollbackDBTransaction();


%></tbody></table></body></html>
