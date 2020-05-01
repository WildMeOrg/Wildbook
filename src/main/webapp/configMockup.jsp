<%@ page contentType="text/html; charset=utf-8" language="java"
     import="org.ecocean.*,
java.util.List,
java.util.ArrayList,
java.util.Arrays,
java.util.Map,
java.util.HashMap,
java.io.File,
org.json.JSONObject,

org.ecocean.configuration.*

              "
%><%

Shepherd myShepherd = new Shepherd("context0");
myShepherd.beginDBTransaction();

//ConfigurationUtil.init();   //not required, but clears cache for us (for testing)

String id = request.getParameter("id");
if (id != null) {
    Configuration conf = ConfigurationUtil.getConfiguration(myShepherd, id);
    response.setContentType("text/plain");
    out.println(conf.toFrontEndJSONObject(myShepherd).toString());
    myShepherd.rollbackDBTransaction();
    myShepherd.closeDBTransaction();
    return;
}

%><html><head>
<title>Configuration Mockup</title>
<script src="tools/jquery/js/jquery.min.js"></script>
<script src="javascript/configuration.js"></script>
<style>
body {
    font-family: sans, arial;
}
.warn {
    color: #641;
}

.top-menu {
    position: relative;
    margin: 6px;
    border: solid 2px #888;
    background-color: #AAA;
    width: 25%;
    height: 100%;
    padding: 8px;
}

.up-arrow {
    padding: 2px 5px;
    position: absolute;
    top: 3px;
    right: 3px;
    text-align: center;
    background-color: #88B;
    color: #002;
    cursor: pointer;
}

#debug {
    position: absolute;
    right: 10px;
    bottom: 10px;
    background-color: #EEE;
    width: 50%;
    font-size: 0.8em;
    height: 5em;
    overflow-y: scroll;
}

.c-label {
    font-weight: bold;
}
.c-description {
    font-size: 0.8em;
    padding: 2px 7px;
    color: #444;
}

.c-help {
    margin: 3px 20px;
    background-color: #444;
    color: #CCC;
    font-size: 0.8em;
    border-radius: 3px;
    padding: 4px;
}
.c-link {
    cursor: pointer;
    margin: 4px 6px;
    padding: 0 5px;
    background-color: #CCC;
}
.c-link:hover {
    background-color: #DDC;
}
.c-menulabel {
    font-weight: bold;
    font-size: 0.9em;
    color: #444;
}
.c-menudescription {
    margin: 0 0 0 7px;
    font-size: 0.7em;
    color: #555;
}

.c-settable {
    backround-color: #FFF;
    border: solid red 4px;
    margin: 4px;
    padding: 4px;
    color: #00B;
    font-size: 0.9em;
}

.c-panel {
    background-color: #D8D8D8;
    margin: 16px;
    border-radius: 8px;
    padding: 8px;
}

</style>

</head>
<body onLoad="wbConf.init()">
<div class="top-menu" id="menu"></div>
<div id="debug"></div>
</body></html>
<%
myShepherd.commitDBTransaction();
%>
