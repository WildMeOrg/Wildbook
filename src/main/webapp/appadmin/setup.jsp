<html>
<head><title>Wildbook Setup (alpha!)</title>
</head>
<body>
<%@ page contentType="text/html; charset=utf-8" language="java"
     import="org.ecocean.ShepherdProperties,
             org.ecocean.servlet.ServletUtilities,
             org.ecocean.CommonConfiguration,
             org.ecocean.Shepherd,
             org.ecocean.User,
             java.util.ArrayList,
             java.util.List,
             java.util.Properties,
org.json.JSONObject,
             org.apache.commons.lang.WordUtils,
             org.ecocean.security.Collaboration,
             org.ecocean.ContextConfiguration
              "
%>

<h1>setup</h1>

<%
String context = ServletUtilities.getContext(request);
//String langCode=ServletUtilities.getLanguageCode(request);
Shepherd myShepherd = new Shepherd(context);
myShepherd.setAction("setup.jsp");

//this should reset the db-stored server info based on the URL of this request
CommonConfiguration.ensureServerInfo(myShepherd, request);

JSONObject serverInfo = CommonConfiguration.getServerInfo(myShepherd);
String url = CommonConfiguration.getServerURL(context);

Properties jdoprops = ShepherdProperties.getProperties("jdoconfig.properties", "", context);

%>

<ul>
<li>server base url (from db, <i>reset NOW</i>): <b><%=url%></b></li>
<li>db connection string: <b><%=jdoprops.getProperty("datanucleus.ConnectionURL")%></b></li>
</ul>

</body>
</html>
