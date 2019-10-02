<html><head><title>KitizenScience: survey results (alpha)</title>
<script src="../javascript/excel.js"></script>
<script>
function exportExcel(aEl) {
    var d = new Date();
    aEl.download = 'kitizen-science-survey-results-' + d.toISOString().substr(0,10) + '.xls';
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
<%

Shepherd myShepherd = new Shepherd("context0");
myShepherd.beginDBTransaction();
//JSONObject key = SystemValue.getJSONObject(myShepherd, "trialKey");


String jdoql = "SELECT FROM org.ecocean.SystemValue WHERE key.startsWith('survey_response_')";
Query q = myShepherd.getPM().newQuery(jdoql);
q.setOrdering("version");
Collection all = (Collection) (q.execute());

List<String> header = new ArrayList<String>();
header.add("username");
header.add("mode");

for (Object o : all) {
    SystemValue sv = (SystemValue)o;
    JSONObject surv = sv.getValue().getJSONObject("value");
    Iterator it = surv.keys();
    while (it.hasNext()) {
        String key = (String)it.next();
        if (!header.contains(key)) header.add(key);
    }
}


for (String h : header) {
    out.println("<th>" + h + "</th>");
}
out.println("</tr></thead><tbody>");

for (Object o : all) {
    SystemValue sv = (SystemValue)o;
    JSONObject surv = sv.getValue().getJSONObject("value");
    out.println("<tr>");
    User user = myShepherd.getUserByUUID(surv.optString("user_uuid", "_NO_"));
    surv.put("username", (user == null) ? "???" : user.getUsername());
    surv.put("mode", (((user != null) && "U-W".equals(user.getAffiliation())) ? "uw" : "pub"));

    for (String key : header) {
        String value = surv.optString(key, "-");
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
