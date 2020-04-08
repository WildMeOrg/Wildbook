<%@ page contentType="text/html; charset=utf-8" language="java"
     import="org.ecocean.*,
java.util.Map,
java.io.File,
org.json.JSONObject,

org.ecocean.configuration.*,

org.ecocean.media.*
              "
%>




<%

Shepherd myShepherd = new Shepherd("context0");

//out.println(ConfigurationUtil.getConfigAsJSONObject());
//out.println("<p>" + ConfigurationUtil.getConfiguration(myShepherd, "cache.bar") + "</p>");
out.println(ConfigurationUtil.getConfiguration(myShepherd, "cache.bar").toJSONObject());

%>



