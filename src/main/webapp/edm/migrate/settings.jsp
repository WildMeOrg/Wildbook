<%@ page contentType="text/html; charset=utf-8" language="java"
     import="org.ecocean.*,
java.util.Collection,
java.util.List,
java.nio.file.Path,
java.util.ArrayList,
javax.jdo.*,
java.util.Iterator,
java.util.Arrays,
java.util.Map,
java.util.HashMap,
org.json.JSONObject,
org.json.JSONArray,
java.lang.reflect.*,
org.ecocean.Util.MeasurementDesc,
org.ecocean.MigrationUtil,
org.ecocean.api.ApiCustomFields,
org.ecocean.customfield.*,
org.ecocean.configuration.*,

org.ecocean.media.*
              "
%><%!


%><html>
<head>
    <title>Codex Site Settings Migration</title>
    <link rel="stylesheet" href="m.css" />
</head>
<body>
<p>
This will help copy site configuration options to Codex.
</p>
<hr />

<h2>Proposed site settings:</h2>
<form method="POST">

<%

String context = "context0";
Shepherd myShepherd = new Shepherd(context);
boolean commit = Util.requestParameterSet(request.getParameter("site.name"));

//myShepherd.beginDBTransaction();


Map<String,String> migMap = new HashMap<String,String>();
migMap.put("site.name", "htmlTitle");
migMap.put("site.general.metaKeywords", "htmlKeywords");
migMap.put("site.general.description", "htmlDescription");

for (String configKey : migMap.keySet()) {
    Configuration conf = null;
    if (commit) {
        String setVal = request.getParameter(configKey);
        if ("".equals(setVal)) setVal = null;
        conf = ConfigurationUtil.setConfigurationValue(myShepherd, configKey, setVal);
        conf.resetRootCache();
    } else {
        conf = ConfigurationUtil.getConfiguration(myShepherd, configKey);
    }

    String confVal = null;
    if (conf.hasValue()) confVal = conf.getValueAsString();
    String legacyVal = CommonConfiguration.getPropertyLEGACY(migMap.get(configKey), context);
    String val = confVal;
    if (val == null) val = legacyVal;
    if (val == null) val = "";

%>

<div>
<hr /><b><%=configKey%></b><%=(commit ? " <i class=\"important\">updated</i>" : "")%><br />
<input name="<%=configKey%>" style="width: 100em;" value="<%=val%>" />

<br />
<p class="muted">
See current <a target="_new" href="../../api/v0/configuration/<%=configKey%>">configuration <b><%=configKey%></b></a>.
</p>
</div>

<%

}

%>

<input type="submit" name="submit" value="Save" />

</body></html>
