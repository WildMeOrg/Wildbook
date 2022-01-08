<%@ page contentType="text/html; charset=utf-8" language="java"
     import="org.ecocean.*,
java.util.Collection,
java.util.List,
java.util.ArrayList,
javax.jdo.*,
java.util.Arrays,
org.json.JSONObject,
org.ecocean.MigrationUtil,
java.lang.reflect.*,
java.time.ZonedDateTime,
java.time.ZoneOffset,
java.io.BufferedReader,
java.io.FileReader,
java.io.File,

org.ecocean.api.ApiCustomFields,
org.ecocean.customfield.*,

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

private String cdtSql(ComplexDateTime cdt, Encounter enc) {
    if (cdt == null) return "";
    String sqlIns = "INSERT INTO complex_date_time (guid, datetime, timezone, specificity) VALUES (?, ?, ?, ?);";
    sqlIns = MigrationUtil.sqlSub(sqlIns, MigrationUtil.partnerGuid(enc.getId()));
    String dt = cdt.gmtZonedDateTime().toString().substring(0,16).replaceFirst("T", " ");
    sqlIns = MigrationUtil.sqlSub(sqlIns, dt);
    sqlIns = MigrationUtil.sqlSub(sqlIns, "UTC" + simpleOffset(cdt));
    sqlIns = MigrationUtil.sqlSub(sqlIns, specificity(enc));
    return "-- " + cdt + " on " + enc + "\n" + sqlIns + "\n\n";
}

private String cdtSql(ComplexDateTime cdt, Occurrence occ) {
    return "-- foo\n";
}


private String occSql(Occurrence occ, String tz) {
    if (occ == null) return "";
    if (Util.collectionIsEmptyOrNull(occ.getEncounters())) return "-- OUCH: " + occ + " has no encounters\n\n";
    Encounter first = null;
    long start = System.currentTimeMillis() * 2l;
    for (Encounter enc : occ.getEncounters()) {
        ComplexDateTime cdt = enc.deriveComplexDateTime(tz);
        if (cdt == null) continue;
        Long gmt = cdt.gmtLong();
        if (gmt < start) {
            start = gmt;
            first = enc;
        }
    }

    String sqlIns = "INSERT INTO complex_date_time (guid, datetime, timezone, specificity) VALUES (?, ?, ?, ?);";
    sqlIns = MigrationUtil.sqlSub(sqlIns, MigrationUtil.partnerGuid(occ.getId()));
    if (first == null) {
        sqlIns = MigrationUtil.sqlSub(sqlIns, "2000-01-01 01:23:45.6789");
        sqlIns = MigrationUtil.sqlSub(sqlIns, "UTC+0000");
        sqlIns = MigrationUtil.sqlSub(sqlIns, "year");
        return "-- OOF: " + occ + " could not get date/time from encounters; using fallback\n" + sqlIns + "\n\n";
    }

    ComplexDateTime cdt = first.deriveComplexDateTime(tz);
    String dt = cdt.gmtZonedDateTime().toString().substring(0,16).replaceFirst("T", " ");
    sqlIns = MigrationUtil.sqlSub(sqlIns, dt);
    sqlIns = MigrationUtil.sqlSub(sqlIns, "UTC" + simpleOffset(cdt));
    sqlIns = MigrationUtil.sqlSub(sqlIns, specificity(first));
    return "-- " + cdt + " on " + occ + " from " + first + "\n" + sqlIns + "\n\n";
}


/*
                 guid                 |          datetime          | timezone | specificity 
--------------------------------------+----------------------------+----------+-------------
 42515161-da4d-41f7-9ea8-f574016a6326 | 2021-12-29 16:43:52.247568 | UTC+0000 | time
 f1b57c93-4e5d-4794-8e3f-aa339aee8e6b | 2014-01-01 09:00:00        | UTC+0000 | time
 d718d685-0652-4d24-a90e-982fd803aaf6 | 2014-01-01 09:00:00        | UTC+0000 | time
*/


private String specificity(Encounter enc) {
    String spec = "time";
    if (enc.getHour() < 0) spec = "day";
    if (enc.getDay() < 1) spec = "month";
    if (enc.getMonth() < 1) spec = "year";
    return spec;
}

private String simpleOffset(ComplexDateTime cdt) {
    if (cdt == null) return null;
    ZonedDateTime zdt = cdt.getZonedDateTime();
    if (zdt == null) return null;
    ZoneOffset zo = zdt.getOffset();
    if (zo == null) return null;
    return zo.toString().replaceFirst(":", "");
}


            ////if ((st == null) || (st.gmtLong() > enct.gmtLong())) st = enct;

%><html>
<head>
    <title>Codex Date/Time Migration Helper</title>
    <link rel="stylesheet" href="m.css" />
</head>
<body>
<p>
This will help migrate to Codex ComplexDateTime values.
</p>




<%

boolean commit = Util.requestParameterSet(request.getParameter("commit"));
String tz = request.getParameter("timeZone");
if (tz == null) tz = "UTC+00:00";

Shepherd myShepherd = new Shepherd("context0");
myShepherd.beginDBTransaction();

%>
<p>
Note: in order to best support <b>time zones</b>, you should have added <i class="code">timeZone</i> values to <b>LocationID.json</b> data,
otherwise all times will be converted using a universal time zone.
</p>
<p>
Default time zone is <b><%=tz%></b> (can be overridden with <i class="code">?timeZone=xxx</i> in url).
</p>
<hr />
<%

String jdoql = "SELECT FROM org.ecocean.Encounter ORDER BY id";
if (!commit) jdoql += " RANGE 0,25";

String fname = "houston_04_ComplexDateTimes.sql";
if (commit) MigrationUtil.writeFile(fname, "");
String content = "BEGIN;\n";

myShepherd.rollbackDBTransaction();

System.out.println("migration/datetime.jsp DONE");

Query query = myShepherd.getPM().newQuery(jdoql);
Collection c = (Collection) (query.execute());
List<Encounter> all = new ArrayList<Encounter>(c);
query.closeAll();

// now occurrences/sightings
jdoql = "SELECT FROM org.ecocean.Occurrence ORDER BY id";
if (!commit) jdoql += " RANGE 0,50";
query = myShepherd.getPM().newQuery(jdoql);
c = (Collection) (query.execute());
List<Occurrence> allOcc = new ArrayList<Occurrence>(c);
query.closeAll();

out.println("<ul>");
int ct = 0;
for (Encounter enc : all) {
    ct++;
    ComplexDateTime cdt = enc.deriveComplexDateTime(tz);
    if (cdt == null) continue;
    out.println("<li>" + enc.getCatalogNumber() + " " + enc.getDate() + " => <b>" + cdt + "</b></li>");
    if (commit) {
        content += cdtSql(cdt, enc);
        if (ct % 100 == 0) {
            System.out.println("migration/datetime.jsp [" + ct + "/" + all.size() + "] encounters processed");
            MigrationUtil.appendFile(fname, content);
            content = "";
        }
    }
}
out.println("</ul>");




if (commit) {
    content += "\n\nEND;\n";
    MigrationUtil.appendFile(fname, content);
} else {
%>
<hr /><p><b>commit=false</b>, not modifying anything</p>
<p><a href="?commit=true&timeZone=<%=tz%>">Migrate DateTime values for Encounters and Occurrences</a></p>
<%
    myShepherd.rollbackDBTransaction();
    myShepherd.closeDBTransaction();
    return;
}



content = "BEGIN;\n\n";
ct = 0;
for (Occurrence occ : allOcc) {
    ct++;
    content += occSql(occ, tz);
    if (ct % 100 == 0) {
        System.out.println("migration/datetime.jsp [" + ct + "/" + allOcc.size() + "] occurrences processed");
        MigrationUtil.appendFile(fname, content);
        content = "";
    }
}
content += "\n\nEND;\n";

MigrationUtil.appendFile(fname, content);
out.println(filePreview(fname));

myShepherd.rollbackDBTransaction();
myShepherd.closeDBTransaction();


%>


<p>done.</p>
</body></html>
