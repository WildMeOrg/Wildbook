<%@ page contentType="text/html; charset=utf-8" language="java"
     import="org.ecocean.*,
java.util.Collection,
java.util.List,
java.util.ArrayList,
javax.jdo.*,
java.util.Set,
java.util.HashSet,
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

private Set<Encounter> findEncs(MediaAsset ma, Shepherd myShepherd) {
    Set<Encounter> encs = new HashSet<Encounter>();
    for (Annotation ann : ma.getAnnotations()) {
        Encounter enc = Encounter.findByAnnotation(ann, myShepherd);
        if (enc != null) encs.add(enc);
    }
    return encs;
}

%><html>
<head>
    <title>Codex CustomField LabeledKeyword Migration Helper</title>
    <link rel="stylesheet" href="m.css" />
</head>
<body>
<p>
This will help migrate <b>Encounter (MediaAsset) LabeledKeywords</b> to Codex CustomFields.
</p>
<hr />



<%

String context = "context0";
Shepherd myShepherd = new Shepherd(context);
boolean commit = Util.requestParameterSet(request.getParameter("commit"));
myShepherd.beginDBTransaction();

Set<String> kwIds = new HashSet<String>();
Set<String> kwLabels = new HashSet<String>();
List<LabeledKeyword> lkws = myShepherd.getAllLabeledKeywords();
for (LabeledKeyword lkw : lkws) {
    //out.println("<p>" + lkw + "</p>");
    kwIds.add(lkw.getIndexname());
    kwLabels.add(lkw.getLabel());
}

Map<String,CustomFieldDefinition> cfdMap = new HashMap<String,CustomFieldDefinition>();
for (String label : kwLabels) {
    CustomFieldDefinition cfd = new CustomFieldDefinition("org.ecocean.Encounter", "string", label);
    out.println("<p><b>" + label + ":</b> " + cfd + "</p>");
    CustomFieldDefinition found = CustomFieldDefinition.find(myShepherd, cfd);
    if (found != null) {
        out.println("<p>collision with existing cfd: <b>" + found + "</b></p>");
        myShepherd.rollbackDBTransaction();
        return;
    }
    cfdMap.put(label, cfd);
}


String sql = "SELECT * FROM \"MEDIAASSET\" WHERE \"ID\" IN (SELECT DISTINCT(\"ID_OID\") FROM \"MEDIAASSET_KEYWORDS\" WHERE \"INDEXNAME_EID\" IN ('" + String.join("', '", kwIds) + "'));";

Query query = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
query.setClass(MediaAsset.class);
Collection c = (Collection)query.execute();
List<MediaAsset> allMA = new ArrayList<MediaAsset>(c);
query.closeAll();

%>
<p><b><%=allMA.size()%> MediaAssets</b> with LabeledKeywords</p>

<%

if (!commit) {
    myShepherd.rollbackDBTransaction();
%>
<hr /><p><b>commit=false</b>, not modifying anything</p>
<p><a href="?commit=true">Create CustomFields for LabeledKeywords and migrate data</a></p>
<%
    return;
}


out.println("<hr /><h2>Migrating data</h2>");

for (CustomFieldDefinition cfd : cfdMap.values()) {
    myShepherd.getPM().makePersistent(cfd);
    cfdMap.put(cfd.getName(), cfd);
}

int ct = 0;
for (MediaAsset ma : allMA) {
    ct++;
    System.out.print("labeledkeywords.jsp [" + ct + "/" + allMA.size()+ "] MediaAsset id=" + ma.getId() + ": ");
    Set<Encounter> encs = findEncs(ma, myShepherd);
    if (encs.size() < 1) {
        System.out.println("no encs found");
        continue;
    }
    Set<LabeledKeyword> kws = new HashSet<LabeledKeyword>();
    for (Keyword kw : ma.getKeywords()) {
        if (!(kw instanceof LabeledKeyword)) continue;
        LabeledKeyword lkw = (LabeledKeyword)kw;
        if (!cfdMap.containsKey(lkw.getLabel())) {
            out.println("<p class=\"error\">could not find CustomFieldDefinition for label=<b>" + lkw.getLabel() + "</b></p>");
            myShepherd.rollbackDBTransaction();
            return;
        }
        kws.add(lkw);
    }
    if (kws.size() < 1) {
        System.out.println("no LabeledKeywords found");
        continue;
    }
    System.out.println(kws.size() + " LabeledKeywords for " + encs.size() + " Encounters");
    out.println("<p><b>MediaAsset " + ma.getId() + "</b> (" + kws.size() + " LabeledKeywords):<ul>");
    for (Encounter enc : encs) {
        out.println("<li>" + enc + "<ol>");
        for (LabeledKeyword kw : kws) {
            CustomFieldDefinition cfd = cfdMap.get(kw.getLabel());
            CustomFieldValue cfv = CustomFieldValue.makeSpecific(cfd, kw.getValue());
            enc.addCustomFieldValue(cfv);
            out.println("<li>");
            out.println("(kw <i>" + kw.getDisplayName() + "</i>) => <span class=\"muted smaller\">");
            out.println(cfv);
            out.println("</span></li>");
        }
        out.println("</ol></li>");
    }
out.println("</ul><h3>done</h3></p>");
}


myShepherd.commitDBTransaction();

%>


</body></html>
