<html>
<head><title>Wildbook Setup (alpha!)</title>
<style>
body {
    font-family: arial, sans;
}

.note {
    padding: 3px 8px;
    background-color: #CCC;
    border-radius: 4px;
}

.warn {
    color: #F33;
}

.go {
    color: #0A2;
}

</style>
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
             org.apache.commons.text.WordUtils,
             org.ecocean.security.Collaboration,
             org.ecocean.ContextConfiguration
              "
%>

<h1>setup</h1>

<%
String context = ServletUtilities.getContext(request);

// if ("doPPSR".equals(request.getQueryString())) {
//     JSONObject rtn = PPSR.register(context);
//     out.println("<h1>PPSR returned:</h1><xmp>");
//     out.println((rtn == null) ? "null" : rtn.toString(4));
//     out.println("</xmp>");
//     return;
// }

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

<hr />
<h1>
<a target="_new" href="https://www.citsci.org/CWIS438/Websites/CitSci/PPSR_CORE_Documentation.php">PPSR</a>
support via
<a target="_new" href="https://scistarter.com/api#add">SciStarter</a>
</h1>
<p class="note">
kind of experimental?  read <b>ppsr.properties</b> for information
</p>


</body>
</html>
