<%@ page contentType="text/html; charset=utf-8" language="java"
     import="org.ecocean.*,
java.util.List,
java.util.ArrayList,
java.util.Map,
java.io.File,
org.json.JSONObject,

org.ecocean.configuration.*,

org.ecocean.media.*
              "
%>




<%

Shepherd myShepherd = new Shepherd("context0");
myShepherd.beginDBTransaction();

//out.println(ConfigurationUtil.getMetaAsJSONObject());
//out.println("<p>" + ConfigurationUtil.getConfiguration(myShepherd, "cache.bar") + "</p>");

//out.println(ConfigurationUtil.getConfiguration(myShepherd, "cache.bar").toJSONObject());


out.println(ConfigurationUtil.getConfiguration(myShepherd, "cache.bar").toJSONObject());

out.println(  ConfigurationUtil.getConfigurationValue(myShepherd, "cache.bar")  );

/*

JSONObject jobj = new JSONObject();
List<String> path = ConfigurationUtil.idPath("test.foo");
out.println(ConfigurationUtil.setDeepJSONObject(jobj, path, 2170));

*/

myShepherd.commitDBTransaction();

%>



