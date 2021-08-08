<%@ page contentType="text/html; charset=utf-8" language="java"
     import="org.ecocean.*,
java.util.Collection,
java.util.List,
java.util.ArrayList,
javax.jdo.*,
java.util.Arrays,
java.util.Map,
java.util.HashMap,
org.json.JSONObject,
java.lang.reflect.*,
org.ecocean.Util.MeasurementDesc,
org.ecocean.api.ApiCustomFields,
org.ecocean.customfield.*,

org.ecocean.media.*
              "
%><%!

private String measDesc(MeasurementDesc desc) {
    String d = desc.getType();
    d += " / " + desc.getLabel();
    d += " / " + desc.getUnits();
    d += " / " + desc.getUnitsLabel();
    return d;
}

private String cfdNameFromMeasurementDesc(MeasurementDesc desc) {
    String cfdName = desc.getType();
    String units = desc.getUnits();
    if ((units != null) && !units.equals("nounits") && !units.equals("")) cfdName = cfdName + "_" + units.toLowerCase();
    return cfdName;
}

%><html>
<head>
    <title>Codex CustomField Measurement Migration Helper</title>
</head>
<body>
<p>
This will help migrate <b>Encounter measurements</b> to Codex.
</p>
<hr />



<%

String context = "context0";
Shepherd myShepherd = new Shepherd(context);
boolean commit = Util.requestParameterSet(request.getParameter("commit"));
myShepherd.beginDBTransaction();

List<MeasurementDesc> descs = Util.findMeasurementDescs("en", context);
List<CustomFieldDefinition> cfds = new ArrayList<CustomFieldDefinition>();
for (MeasurementDesc desc : descs) {
    CustomFieldDefinition cfd = new CustomFieldDefinition("org.ecocean.Encounter", "double", cfdNameFromMeasurementDesc(desc));
    CustomFieldDefinition found = CustomFieldDefinition.find(myShepherd, cfd);
    if (found != null) {
        out.println("<p>collision with existing cfd: <b>" + found + "</b></p>");
        myShepherd.rollbackDBTransaction();
        return;
    }
    cfds.add(cfd);
    String show = measDesc(desc);
    %>
<p><b><%=show%></b><br /><%=cfd.toString()%></p>
    <%
}

if (!commit) {
    myShepherd.rollbackDBTransaction();
%>
<hr /><p><b>commit=false</b>, not modifying anything</p>
<p><a href="?commit=true">Create CustomFields for Measurements and migrate data</a></p>
<%
    return;
}


String jdoql = "SELECT FROM org.ecocean.Encounter WHERE measurements.size() > 0";
//jdoql += " RANGE 0,10";  //debugging only

out.println("<p>jdoql = <b>" + jdoql + "</b></p><hr /><h1>Migrating field values:</h1>");

Map<String, CustomFieldDefinition> cfdMap = new HashMap<String, CustomFieldDefinition>();
for (CustomFieldDefinition cfd : cfds) {
    myShepherd.getPM().makePersistent(cfd);
    cfdMap.put(cfd.getName(), cfd);
}

Query query = myShepherd.getPM().newQuery(jdoql);
Collection c = (Collection) (query.execute());
List<Encounter> all = new ArrayList<Encounter>(c);
query.closeAll();
int ect = 1;
for (Encounter enc : all) {
    out.println("<hr /><b>" + enc + "</b>");
    int ct = 0;
    for (MeasurementDesc desc : descs) {
        Measurement ms = myShepherd.getMeasurementOfTypeForEncounter(desc.getType(), enc.getCatalogNumber());
        if (ms != null) {
            String nm = cfdNameFromMeasurementDesc(desc);
            CustomFieldDefinition cfd = cfdMap.get(nm);
            if (cfd == null) {
                out.println("<p>could not find CustomFieldDefinition that matched name=" + nm + " for " + ms + "</p>");
                myShepherd.rollbackDBTransaction();
                return;
            }
            out.println("<br />" + cfd + " => " + ms.getValue());
            CustomFieldValue cfv = CustomFieldValue.makeSpecific(cfd, ms.getValue());
            enc.addCustomFieldValue(cfv);
            ct++;
        }
    }
    System.out.println("migrateMeasurements.jsp: [" + ect + "/" + all.size() + "] migrated " + ct + " Measurements on " + enc);
    ect++;
}


myShepherd.commitDBTransaction();

%>


</body></html>
