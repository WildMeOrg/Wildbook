<%@ page contentType="application/javascript; charset=utf-8" language="java"
         import="org.ecocean.servlet.ServletUtilities,org.ecocean.*, java.util.Properties,org.slf4j.Logger,org.slf4j.LoggerFactory,
javax.jdo.Query,
org.json.JSONObject
" %>
<%

//   create table volunteer_log (id varchar(36) not null, timestamp bigint default extract(epoch from now()) * 1000, content text);
// in order for the ability to *create* via jdo sql query, you need to add this line to jdoconfig.properties:
//    datanucleus.query.sql.allowAll = true

String context = ServletUtilities.getContext(request);
Shepherd myShepherd = new Shepherd(context);
myShepherd.setAction("utick");
myShepherd.beginDBTransaction();

JSONObject rtn = new JSONObject();
User user = AccessControl.getUser(request, myShepherd);
if (user == null) {
    rtn.put("success", false);
    rtn.put("error", "user not logged in");
    out.println(rtn.toString());
    myShepherd.rollbackDBTransaction();
    return;
}

//can pass in arbitrary json via post or ?content=
JSONObject content = null;
try {
    content = ServletUtilities.jsonFromHttpServletRequest(request);
} catch (org.json.JSONException ex) { }
if (content == null) content = Util.stringToJSONObject(request.getParameter("content"));
if (content == null) content = new JSONObject();
//System.out.println("content => " + content);

Long init = (Long)session.getAttribute("log.init");
if (init == null) {
    init = System.currentTimeMillis();
    session.setAttribute("log.init", init);
    content.put("sessionInit", true);
} else {
    content.put("timeSinceInit", System.currentTimeMillis() - init);
}

content.put("ip", ServletUtilities.getRemoteHost(request));
content.put("timeInit", init);

System.out.println("[INFO] utick t=" + System.currentTimeMillis() + ", uid=" + user.getUUID() + ", uri=" + content.optString("uri", null));
String sql = "INSERT INTO volunteer_log (id, content) values (?, ?)";
Query q = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
q.execute(user.getUUID(), content.toString());
myShepherd.commitDBTransaction();

rtn.put("success", true);
rtn.put("content", content);
out.println(rtn);




%>

