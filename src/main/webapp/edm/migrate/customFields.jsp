<%@ page contentType="text/html; charset=utf-8" language="java"
     import="org.ecocean.*,
java.util.Collection,
java.util.List,
java.util.ArrayList,
javax.jdo.*,
java.util.Arrays,
org.json.JSONObject,
java.lang.reflect.*,

org.ecocean.api.ApiCustomFields,
org.ecocean.configuration.*,
org.ecocean.customfield.*,
org.ecocean.MigrationUtil,

org.ecocean.media.*
              "
%><html>
<head>
    <title>Codex CustomField Migration Helper</title>
    <link rel="stylesheet" href="m.css" />
</head>
<body>
<p>
This will help migrate non-supported fields to Codex <i>CustomFields</i>.
<br />
If you wish to migrate <b>Encounter measurements</b>, please see <a href="measurements.jsp">measurements.jsp</a>.
</p>
<hr />



<%

Shepherd myShepherd = new Shepherd("context0");
myShepherd.beginDBTransaction();

String fieldName = request.getParameter("fieldName");
String className = request.getParameter("className");
boolean commit = Util.requestParameterSet(request.getParameter("commit"));
if ((className == null) || (fieldName == null)) {
%>
<form method="GET">
<select name="className">
<option>Encounter</option>
<option>Occurrence</option>
<option>MarkedIndividual</option>
</select>

<input name="fieldName" placeholder="field (property) name" />
<p>
    <input type="submit" value="Validate" />
</p>

<%
    return;
}

Class cls = null;
String categoryType = null;
switch (className) {
    case "Encounter":
        cls = Encounter.class;
        categoryType = "encounter";
        break;
    case "Occurrence":
        cls = Occurrence.class;
        categoryType = "sighting";
        break;
    case "MarkedIndividual":
        categoryType = "individual";
        cls = MarkedIndividual.class;
}

if (cls == null) throw new RuntimeException("invalid className= passed");

Field field = cls.getDeclaredField(fieldName);
String categoryId = MigrationUtil.getOrMakeCustomFieldCategory(myShepherd, categoryType, "General");
JSONObject schema = new JSONObject();
schema.put("category", categoryId);
schema.put("_migration", System.currentTimeMillis());

CustomFieldDefinition cfd = new CustomFieldDefinition(field, schema);
out.println("<p>new cfd: <b>" + cfd + "</b><xmp>" + cfd.toJSONObject().toString(4) + "</xmp></p>");
CustomFieldDefinition found = CustomFieldDefinition.find(myShepherd, cfd);
if (found != null) {
    out.println("<p>collision with existing cfd: <b>" + found + "</b></p>");
    myShepherd.rollbackDBTransaction();
    return;
}

if (!commit) {
    myShepherd.rollbackDBTransaction();
%>
<hr /><p><b>commit=false</b>, not modifying anything</p>
<p><a href="?className=<%=className%>&fieldName=<%=fieldName%>&commit=true">Create CustomField and migrate data</a></p>
<%
    return;
}

String jdoql = "SELECT FROM org.ecocean." + className + " WHERE ";
if (cfd.getMultiple()) {
    jdoql += fieldName + ".size() > 0";
} else {
    jdoql += fieldName + " != null";
}
//jdoql += " RANGE 0,10";  //debugging only

out.println("<p>jdoql = <b>" + jdoql + "</b></p><hr /><h1>Migrating field values:</h1>");

myShepherd.getPM().makePersistent(cfd);

Query query = myShepherd.getPM().newQuery(jdoql);
Collection c = (Collection) (query.execute());
List<ApiCustomFields> all = new ArrayList<ApiCustomFields>(c);
query.closeAll();
int ct = 1;
for (ApiCustomFields obj : all) {
    Object val = obj.migrateFieldValue(cfd, field);
    out.println("<p>" + obj + "</p>");
    System.out.println("customFields.jsp: [" + ct + "/" + all.size() + "] migrated " + cfd + " on " + obj);
    ct++;
}

//update configuration to reflect changes in CustomFieldDefinitions
String[] classes = {"Encounter", "Occurrence", "MarkedIndividual"};
for (String cfcls : classes) {
    String key = "site.custom.customFields." + cfcls;
    ConfigurationUtil.setConfigurationValue(myShepherd, key, CustomFieldDefinition.getDefinitionsAsJSONObject(myShepherd, "org.ecocean." + cfcls));
}
ConfigurationUtil.resetValueCache("site");


myShepherd.commitDBTransaction();
System.out.println("customFields.jsp: DONE");

%>


</body></html>
