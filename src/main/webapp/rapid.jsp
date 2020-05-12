<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.servlet.ServletUtilities,org.ecocean.*,
org.joda.time.DateTime,
org.ecocean.servlet.importer.ImportTask,
org.ecocean.ia.Task,
org.ecocean.media.MediaAsset,
javax.jdo.Query,
org.json.JSONArray,
java.util.Map,
java.util.HashMap,
java.util.List,
java.util.Collection,
java.util.ArrayList,
java.util.Properties,org.slf4j.Logger,org.slf4j.LoggerFactory" %>
<%!
private static String encDiv(Annotation ann, Encounter enc) {
    if (enc == null) return "<!-- null enc -->";
    return "<div class=\"enc-div\">[enc:" + enc.getCatalogNumber() + "]</div>";
}

private static String procStateCode(Annotation ann, List<Task> tasks) {
    if (ann == null) return "(null annot)";
    int tsize = Util.collectionSize(tasks);
    Task recent = ((tsize < 1) ? null : tasks.get(tsize - 1));
    return ann.getIdentificationStatus() + ":" + ((recent == null) ? "<null>" : recent.getId());
}
private static String procStateDisplay(Annotation ann, List<Task> tasks) {
    return "<div class=\"proc-state\">" + procStateCode(ann, tasks) + "</div>";
}

%>
<%

String context = ServletUtilities.getContext(request);
Shepherd myShepherd = new Shepherd(context);
myShepherd.setAction("imports.jsp");
User user = AccessControl.getUser(request, myShepherd);
if (user == null) {
    response.sendError(401, "access denied");
    return;
}
boolean adminMode = ("admin".equals(user.getUsername()));

/*
//setup our Properties object to hold all properties
  Properties props = new Properties();
  //String langCode = "en";
  String langCode=ServletUtilities.getLanguageCode(request);
  props = ShepherdProperties.getProperties("login.properties", langCode,context);
*/
  


%>
<jsp:include page="header.jsp" flush="true"/>
<style>
.bootstrap-table {
    height: min-content;
}
.dim, .ct0 {
    color: #AAA;
}

.yes {
    color: #0F5;
}
.no {
    color: #F20;
}

.smaller {
    font-size: 0.84em;
}
.fname-toggle-true {
    background-color: rgba(255,255,0,0.2);
}
.has-trivial {
    font-style: oblique;
    color: #A80;
}

a.button {
    font-weight: bold;
    font-size: 0.9em;
    background-color: #AAA;
    border-radius: 4px;
    padding: 0 6px;
    text-decoration: none;
    cursor: pointer;
}
a.button:hover {
    background-color: #DDA;
    text-decoration: none;
}
</style>


    <script src="javascript/bootstrap-table/bootstrap-table.min.js"></script>
    <link rel="stylesheet" href="javascript/bootstrap-table/bootstrap-table.min.css" />


<div class="container maincontent">

<%

String taskId = request.getParameter("taskId");
String jdoql = null;
ImportTask itask = null;

if (taskId != null) {
    try {
        itask = (ImportTask) (myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(ImportTask.class, taskId), true));
    } catch (Exception ex) {}
/*
    if ((itask == null) || !(adminMode || user.equals(itask.getCreator()))) {
        out.println("<h1 class=\"error\">taskId " + taskId + " is invalid</h1>");
        return;
    }
*/
}

Map<String,Encounter> encMap = new HashMap<String,Encounter>();
List<MediaAsset> mas = new ArrayList<MediaAsset>();
for (Encounter enc : itask.getEncounters()) {
    if (!enc.hasAnnotations()) continue;
    for (Annotation ann : enc.getAnnotations()) {
        encMap.put(ann.getId(), enc);
        MediaAsset ma = ann.getMediaAsset();
        if ((ma != null) && !mas.contains(ma)) mas.add(ma);
        //out.println(ann.getId() + "<br />");
    }
}

for (MediaAsset ma : mas) {
%>
<div class="asset-row" id="asset-row-<%=ma.getId()%>">
    <div class="asset-wrapper" id="asset-wrapper-<%=ma.getId()%>">
        <img src="<%=ma.safeURL(request)%>" />
        <div class="asset-filename"><%=ma.getFilename()%></div>
    </div>

    <div class="annotlist">
<%
for (Annotation ann : ma.getAnnotations()) { 
    List<Task> tasks = ann.getRootIATasks(myShepherd);
    int tsize = Util.collectionSize(tasks);
    Encounter enc = encMap.get(ann.getId());
    MarkedIndividual indiv = (enc == null) ? null : enc.getIndividual();
    String state = procStateCode(ann, tasks);
%>
        <div class="annot-row annot-state-<%=state%>" id="annot-row-<%=ann.getId()%>">
            <div class="annot-id"><%=ann.getId()%></div>
            <div class="annot-indiv"><%=(indiv == null) ? "unnamed" : indiv.getDisplayName(request)%></div>
            <%=encDiv(ann, enc)%>
            <%=procStateDisplay(ann, tasks)%>
        </div>
<% } %>
    </div>

</div>
<%
}


%>

</div>

<jsp:include page="footer.jsp" flush="true"/>

