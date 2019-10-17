<%@ page contentType="application/javascript; charset=utf-8" language="java"
         import="org.ecocean.servlet.ServletUtilities,org.ecocean.*, java.util.Properties,org.slf4j.Logger,org.slf4j.LoggerFactory,
javax.jdo.Query,
org.json.JSONObject
" %>
<%

//   create table volunteer_log (id varchar(36) not null, timestamp bigint default extract(epoch from now()) * 1000, content text);

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

JSONObject content = Util.stringToJSONObject(request.getParameter("content"));  //can pass in arbitrary json
if (content == null) content = new JSONObject();

Long init = (Long)session.getAttribute("log.init");
if (init == null) {
    init = System.currentTimeMillis();
    session.setAttribute("log.init", init);
    content.put("sessionInit", true);
} else {
    content.put("timeSinceInit", System.currentTimeMillis() - init);
}

content.put("ip", ServletUtilities.getRemoteHost(request));
content.put("uri", request.getRequestURI());
content.put("timeInit", init);

String sql = "INSERT INTO volunteer_log (id, content) values (?, ?)";
Query q = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
q.execute(user.getUUID(), content.toString());
myShepherd.commitDBTransaction();

rtn.put("success", true);
rtn.put("content", content);
out.println(rtn);




%>

