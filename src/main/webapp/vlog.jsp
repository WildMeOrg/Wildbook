<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.servlet.ServletUtilities,org.ecocean.*,
javax.jdo.Query,
org.joda.time.DateTime,
java.util.Set,
java.util.HashSet,
java.util.Map,
java.util.HashMap,
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
private static String colonTime(long sec) {
    return String.format(
        "%02d:%02d:%02d",
        (sec / 3600L) % 3600L,
        (sec / 60L) % 60L,
        sec % 60L
    );
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

//username, email, grand total hours:min:sec spent (across all volunteer roles), total Cat and Mouse time, total submissions processed, total Cat Walk time (to pull from app of when they log in and log out?), total Cat Walks encounter submissions, total Super Volunteer time, registration date, last active date


//this will find activity on the queue.jsp page (i.e. supervolunteer time)

Map<String,Long> queueTime = new HashMap<String,Long>();
Map<String,Long> lastActive = new HashMap<String,Long>();
String sql = "SELECT id, timestamp, content FROM volunteer_log WHERE timestamp > " + since + " ORDER BY timestamp";
//String sql = "SELECT id, timestamp, content FROM volunteer_log WHERE timestamp < 0";
Query q = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
q.execute();
List results = (List)q.execute();
Iterator it = results.iterator();
while (it.hasNext()) {
    Object[] fields = (Object[])it.next();
    String uid = (String)fields[0];
    long ts = (long)fields[1];
    lastActive.put(uid, ts);
    //DateTime dt = new DateTime(ts);
    String c = (String)fields[2];
    JSONObject j = Util.stringToJSONObject(c);
    if (!j.optBoolean("active", false)) continue;
    String uri = j.optString("uri", null);
    if (uri == null) continue;
    if (!uri.matches(".*queue.jsp.*")) continue;
    boolean sessionInit = j.optBoolean("sessionInit", false);
    long tsince = j.optLong("timeSinceInit", 0l);
    long tdelta = Math.round(tsince / 1000l);
    String pageId = j.optString("pageId", "-");
    queueTime.put(uid + ":" + pageId, tdelta);
    //out.println("<p>" + pkey + " <b>" + tdelta + "</b></p>");
    //out.println("<p>" + (sessionInit ? "<b style=\"color: blue\">INIT</b> " : "") + "<b>" + uri + "</b> " + dt + "<br /><b style=\"background-color: #FFA\">" + tdelta + "</b> " + pageId + "<br /><span style=\"color: #777; font-size: 0.8em;\">" + j.toString() + "</span></p>");
}
q.closeAll();

List<String> pkeys = new ArrayList<String>(queueTime.keySet());
for (String pkey : pkeys) {
    String uid = pkey.substring(0,36);
    if (queueTime.get(uid) == null) queueTime.put(uid, 0L);
    queueTime.put(uid, queueTime.get(uid) + queueTime.get(pkey));
    queueTime.remove(pkey);
}



q = myShepherd.getPM().newQuery("SELECT FROM org.ecocean.Decision WHERE property == 'match'");
q.setOrdering("timestamp");
q.execute();
Collection c = (Collection)q.execute();
List<Decision> decs = new ArrayList<Decision>(c);
q.closeAll();
myShepherd.rollbackDBTransaction();

Map<String,Long> cmTime = new HashMap<String,Long>();
Map<String,Long> cmCount = new HashMap<String,Long>();

for (Decision dec : decs) {
    JSONObject val = dec.getValue();
    long initTime = val.optLong("initTime", 0l);
    long attrSaveTime = val.optLong("attrSaveTime", 0l);
    long matchSaveTime = val.optLong("matchSaveTime", 0l);
    long totalTime = 0l;
    if ((initTime > 0l) && (attrSaveTime > 0l)) totalTime += (attrSaveTime - initTime);
    if ((attrSaveTime > 0l) && (matchSaveTime > 0l)) {
        totalTime += (matchSaveTime - attrSaveTime);
    } else if ((initTime > 0l) && (matchSaveTime > 0l)) {
        totalTime += (matchSaveTime - initTime);
    }
    String uid = dec.getUser().getUUID();
    if (cmTime.get(uid) == null) cmTime.put(uid, 0L);
    if (cmCount.get(uid) == null) cmCount.put(uid, 0L);
    cmTime.put(uid, cmTime.get(uid) + (totalTime / 1000l));
    cmCount.put(uid, cmCount.get(uid) + 1);
    //out.println("<p><b>" + new DateTime(initTime) + "</b> <i>attr save: <b>" + niceTime(attrSaveTime - initTime) + "</b> | match save: <b>" + niceTime(matchSaveTime - attrSaveTime) + "</b> | total <b>" + niceTime(matchSaveTime - initTime) + "</b></p>");
    //out.println("<p>" + val + "</p>");
}

//out.println(cmTime);
//out.println(cmCount);

Set<String> uids = new HashSet<String>(queueTime.keySet());
uids.addAll(cmTime.keySet());

// basically stats are columns "total" through "super volunteer time":
String[] head = new String[]{"username", "email", "user id", "total", "cat & mouse time", "cat/mouse submissions processed", "cat walk time", "cat walk submissions", "super volunteer time", "registration date", "last active date"};
List<String[]> rows = new ArrayList<String[]>();
rows.add(head);

for (String uid : uids) {
    String[] row = new String[head.length];
    User user = myShepherd.getUserByUUID(uid);
    row[0] = user.getUsername();
    row[1] = user.getEmailAddress();
    row[2] = uid;
    long total = 0l;
    if (cmTime.get(uid) == null) {
        row[4] = "-";
        row[5] = "-";
    } else {
        total += cmTime.get(uid);
        row[4] = colonTime(cmTime.get(uid));
        row[5] = Long.toString(cmCount.get(uid));
    }

    //cat walk doesnt exist yet
    row[6] = "n/a";
    row[7] = "n/a";

    if (queueTime.get(uid) == null) {
        row[8] = "-";
    } else {
        total += queueTime.get(uid);
        row[8] = colonTime(queueTime.get(uid));
    }

    row[3] = colonTime(total);

    if (lastActive.get(uid) == null) {
        row[10] = "-";
    } else {
        row[10] = new DateTime(lastActive.get(uid)).toString();
    }

    row[9] = "-";
    q = myShepherd.getPM().newQuery("javax.jdo.query.SQL", "SELECT \"VERSION\" FROM \"SYSTEMVALUE\" WHERE \"KEY\" = 'survey_response_" + uid + "'");
    results = (List)q.execute();
    it = results.iterator();
    if (it.hasNext()) {
        Long surveyTime = (Long)it.next();
        row[9] = new DateTime(surveyTime).toString();
    }

    rows.add(row);
}

out.println("<table border=\"1\">");
for (String[] row : rows) {
    out.println("<tr><td>" + String.join("</td><td>", row) + "</td></tr>");
}
out.println("</table>");


myShepherd.rollbackDBTransaction();
myShepherd.closeDBTransaction();

%>

