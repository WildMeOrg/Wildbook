<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.servlet.ServletUtilities,org.ecocean.*,
javax.jdo.Query,
org.joda.time.DateTime,
java.util.List,
java.util.ArrayList,
java.util.Collection,
java.util.Iterator,
org.json.JSONObject
" %><%!
private static String niceTime(long msec) {
    if (msec < 0l) return "-";
    double sec = Math.round(msec / 1000l);
    if (sec < 76) return sec + " sec";
    if (sec < (60*61)) return (Math.floor(new Double(sec / 6d)) / 10d) + " min";
    return (Math.floor(new Double(sec / 360d)) / 10d) + " hr";
}
%><%

//   create table volunteer_log (id varchar(36) not null, timestamp bigint default extract(epoch from now()) * 1000, content text);
// in order for the ability to *create* via jdo sql query, you need to add this line to jdoconfig.properties:
//    datanucleus.query.sql.allowAll = true

String context = ServletUtilities.getContext(request);
Shepherd myShepherd = new Shepherd(context);
myShepherd.setAction("vlog");
myShepherd.beginDBTransaction();

//long since = 1577836800123l;
long since = 1580850674297l;

//String sql = "SELECT id, timestamp, content FROM volunteer_log WHERE timestamp > " + since + " ORDER BY timestamp";
String sql = "SELECT id, timestamp, content FROM volunteer_log WHERE timestamp < 0";
Query q = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
q.execute();
List results = (List)q.execute();
Iterator it = results.iterator();

//String prevUser = null;
while (it.hasNext()) {
    Object[] fields = (Object[])it.next();
    String uid = (String)fields[0];
    long ts = (long)fields[1];
    DateTime dt = new DateTime(ts);
    String c = (String)fields[2];
/*
    if (!uid.equals(prevUser)) {
        out.println("\n" + uid);
        prevUser = uid;
    }
*/
    JSONObject j = Util.stringToJSONObject(c);
    String uri = j.optString("uri", null);
    if (uri == null) continue;
    if (!uri.matches(".*encounterDecide.jsp.*")) continue;
    boolean sessionInit = j.optBoolean("sessionInit", false);
    long tsince = j.optLong("timeSinceInit", 0l);
    long tdelta = Math.round(tsince / 1000l);
    String pageId = j.optString("pageId", "-");
    out.println("<p>" + (sessionInit ? "<b style=\"color: blue\">INIT</b> " : "") + "<b>" + uri + "</b> " + dt + "<br /><b style=\"background-color: #FFA\">" + tdelta + "</b> " + pageId + "<br /><span style=\"color: #777; font-size: 0.8em;\">" + j.toString() + "</span></p>");
}
q.closeAll();


q = myShepherd.getPM().newQuery("SELECT FROM org.ecocean.Decision WHERE property == 'match'");
q.setOrdering("timestamp");
q.execute();
Collection c = (Collection)q.execute();
List<Decision> decs = new ArrayList<Decision>(c);
q.closeAll();
myShepherd.rollbackDBTransaction();

for (Decision dec : decs) {
    JSONObject val = dec.getValue();
    long initTime = val.optLong("initTime", 0l);
    long attrSaveTime = val.optLong("attrSaveTime", 0l);
    long matchSaveTime = val.optLong("matchSaveTime", 0l);
    out.println("<p><b>" + new DateTime(initTime) + "</b> <i>attr save: <b>" + niceTime(attrSaveTime - initTime) + "</b> | match save: <b>" + niceTime(matchSaveTime - attrSaveTime) + "</b> | total <b>" + niceTime(matchSaveTime - initTime) + "</b></p>");
    //out.println("<p>" + val + "</p>");
}


%>

