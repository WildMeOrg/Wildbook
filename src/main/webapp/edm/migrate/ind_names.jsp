<%@ page contentType="text/html; charset=utf-8" language="java"
     import="org.ecocean.*,
java.util.Collection,
java.util.List,
java.util.Iterator,
java.nio.file.Path,
java.util.ArrayList,
java.io.File,
javax.jdo.*,
java.util.Arrays,
java.util.Map,
java.util.HashMap,
java.util.Set,
java.io.BufferedReader,
java.io.FileReader,
java.util.UUID,
java.util.HashSet,
org.json.JSONObject,
org.json.JSONArray,
java.sql.Connection,
java.sql.PreparedStatement,
java.sql.DriverManager,
java.lang.reflect.*,
org.ecocean.MigrationUtil,
org.ecocean.Util.MeasurementDesc,
org.ecocean.api.ApiCustomFields,
org.ecocean.customfield.*,
org.ecocean.servlet.ServletUtilities,

org.ecocean.media.*
              "
%><%!

private String filePreview(String name) throws java.io.IOException {
    File path = new File(MigrationUtil.getDir(), name);
    BufferedReader br = new BufferedReader(new FileReader(path));
    String content = "";
    String line;
    while ( ((line = br.readLine()) != null) && (content.length() < 3500) ) {
        content += line + "\n";
    }
    String rtn = content;
    if (rtn.length() > 3000) rtn = rtn.substring(0, 3000) + "\n\n   [... preview truncated ...]";
    return "<div>This file located at: <i class=\"code\">" + path.toString() + "</i><br /><textarea class=\"preview\">" + rtn + "</textarea></div>";
}

private String coerceOwnerId(Encounter enc, Shepherd myShepherd) {
    String sub = enc.getSubmitterID();
    if (sub == null) return null;
    User user = myShepherd.getUser(sub);
    if (user != null) return user.getUUID();
    return null;
}

private String namesSql(Shepherd myShepherd, MarkedIndividual indiv) {
    if (!Util.stringExists(indiv.getId()) || (indiv.getNames() == null)) return "";
    JSONObject names = indiv.getNames().getValues();
    if (names == null) return "";
    String creatorGuid = MigrationUtil.getPublicUserId(myShepherd);
    if (indiv.numEncounters() > 0) for (Encounter enc : indiv.getEncounters()) {
        User user = null;
        if (enc.getSubmitterID() != null) user = myShepherd.getUser(enc.getSubmitterID());
        if (user != null) {
            creatorGuid = user.getUUID();
            break;
        }
    }
    String sqlRtn = "\n-- " + indiv + "  [creator " + creatorGuid + "]\n";
    for (Object k : names.keySet()) {
        String key = (String)k;
        String context = key;
        if (context.equals("*")) context = "default";
        if (!Util.stringExists(context)) {
            sqlRtn += "-- oops empty context\n";
            continue;
        }
        JSONArray vals = names.optJSONArray(key);
        if (vals == null) {
            sqlRtn += "-- oops could not get array for key=" + key + "\n";
            continue;
        }
        if (vals.length() < 1) {
            sqlRtn += "-- oops empty array for key=" + key + "\n";
            continue;
        }
        for (int i = 0 ; i < vals.length() ; i++) {
            String value = vals.optString(i);
            if ((value == null) || (value.equals(""))) {
                sqlRtn += "-- oops element=" + i + " is empty or not a string, for key=" + key + "\n";
                continue;
            }
            String sqlIns = "INSERT INTO name (created, updated, viewed, guid, context, value, individual_guid, creator_guid) VALUES (now(), now(), now(), ?, ?, ?, ?, ?);\n";
            sqlIns = MigrationUtil.sqlSub(sqlIns, Util.generateUUID());
            sqlIns = MigrationUtil.sqlSub(sqlIns, ((i == 0) ? context : context + (i-1)));
            sqlIns = MigrationUtil.sqlSub(sqlIns, value);
            sqlIns = MigrationUtil.sqlSub(sqlIns, indiv.getId());
            sqlIns = MigrationUtil.sqlSub(sqlIns, creatorGuid);
            sqlRtn += sqlIns;
        }
    }
    return sqlRtn;
}


%><html>
<head>
    <title>Codex Individual Name(s) Houston Migration</title>
    <link rel="stylesheet" href="m.css" />
</head>
<body>
<p>
<h2>This will help migrate <b>Individuals Names</b> to Houston within Codex.</h2>
</p>
<hr />


<%
MigrationUtil.setDir(request.getParameter("migrationDir"));
String checkDir = MigrationUtil.checkDir();
%>
<p>
migrationDir: <b><%=checkDir%></b>
</p>
<%

boolean debug = false;
String context = "context0";
Shepherd myShepherd = new Shepherd(context);
boolean commit = Util.requestParameterSet(request.getParameter("commit"));
myShepherd.beginDBTransaction();


String jdoql = "SELECT FROM org.ecocean.MarkedIndividual ORDER BY id";
if (debug) jdoql += " RANGE 0,500";
Query query = myShepherd.getPM().newQuery(jdoql);
Collection c = (Collection) (query.execute());
List<MarkedIndividual> allInd = new ArrayList<MarkedIndividual>(c);
query.closeAll();


if (!commit) {
    out.println("<textarea class=\"preview\">");
    int ct = 0;
    for (MarkedIndividual indiv : allInd) {
        String sql = namesSql(myShepherd, indiv);
        if (Util.stringExists(sql)) {
            out.println(sql);
            ct++;
        }
        if (ct > 100) break;
    }
    myShepherd.rollbackDBTransaction();
%>
</textarea>
<p>
    <b><%=allInd.size()%> Individuals</b>
</p>
<hr /><p><b>commit=false</b>, truncated sql</p>
<p><a href="?commit=true">Generate actual sql</a></p>
<%
    return;
}


String fname = "houston_08_ind_names.sql";
MigrationUtil.writeFile(fname, "");
String content = "BEGIN;\n";
int ct = 0;
for (MarkedIndividual indiv : allInd) {
    ct++;
    String sql = namesSql(myShepherd, indiv);
    content += sql;
    if (ct % 100 == 0) {
        System.out.println("migration/ind_names.jsp [" + ct + "/" + allInd.size() + "] individuals processed");
        MigrationUtil.appendFile(fname, content);
        content = "";
    }
}
content += "END;\n";
MigrationUtil.appendFile(fname, content);
out.println(filePreview(fname));

myShepherd.rollbackDBTransaction();

System.out.println("migration/ind_names.jsp DONE");
%>

<h1>DONE</h1>

</body></html>
