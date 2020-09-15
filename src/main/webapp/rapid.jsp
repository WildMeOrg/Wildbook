<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.servlet.ServletUtilities,org.ecocean.*,
org.joda.time.DateTime,
org.ecocean.servlet.importer.ImportTask,
org.ecocean.ia.Task,
org.ecocean.media.MediaAsset,
org.ecocean.media.Feature,
javax.jdo.Query,
org.json.JSONObject,
org.json.JSONArray,
org.apache.commons.io.FileUtils,
java.io.File,
java.util.Arrays,
java.util.Map,
java.util.HashMap,
java.util.List,
java.util.Collection,
java.util.ArrayList,
java.util.Properties,org.slf4j.Logger,org.slf4j.LoggerFactory" %>
<%!
private static String encDiv(Annotation ann, Encounter enc) {
    if (enc == null) return "<!-- null enc -->";
    String h = shortId(enc.getCatalogNumber());
    Long m = enc.getDateInMilliseconds();
    if (m != null) h = (new DateTime(m)).toString().substring(0,10);
    h = "<a target=\"_new\" title=\"Go to Encounter\" href=\"encounters/encounter.jsp?number=" + enc.getCatalogNumber() + "\">" + h + "</a>";
    if (enc.getSex() != null) h += " / " + enc.getSex();
    if (enc.getLifeStage() != null) h += " / " + enc.getLifeStage();
    return "<div class=\"enc-div\">" + h + "</div>";
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

private static String shortId(String id) {
    return id.substring(0,8);
}

private boolean taggedNew(Encounter enc) {
    if (enc == null) return false;
    if (enc.getDynamicProperties() == null) return false;
System.out.println(">>>>>> " + enc.getDynamicProperties());
    if (enc.getDynamicProperties().matches(".*newNameMatch_\\d+=.*")) return true;
    if (enc.getDynamicProperties().matches(".*newName_\\d+=.*")) return true;
    return false;
}

private String isNewName(Encounter enc, List<Encounter> encs, Shepherd myShepherd) {
    if ((enc == null) || !enc.hasMarkedIndividual() || Util.collectionIsEmptyOrNull(encs)) return "";
    if (!taggedNew(enc)) {
        System.out.println("isNewName(): " + enc.getCatalogNumber() + " is not tagged newName");
        return "N";
        //return "NO-TAG";
    }
    MarkedIndividual indiv = enc.getIndividual();
    int numEnc = indiv.numEncounters();
    if (numEnc < 2) return "Y";  //easy but should never actually be 0
    System.out.println("isNewName(): " + enc.getCatalogNumber() + " one of " + numEnc + " encs for indiv=" + indiv.getId());
    for (Encounter otherEnc : indiv.getEncounters()) {
        if (encs.contains(otherEnc)) {
            System.out.println("isNewName(): " + otherEnc.getCatalogNumber() + " exists in this import");
        } else if (!taggedNew(otherEnc)) {
            System.out.println("isNewName(): " + otherEnc.getCatalogNumber() + " not tagged newName; assuming matched later???");
        } else {
            System.out.println("isNewName(): " + otherEnc.getCatalogNumber() + " IS tagged newName, but NOT in import; not new!");
            return "N";
            //return "no-previous";
        }
    }
    return "Y";
    //return "Yes-fellthru";
}

private String isResighted(Encounter enc, List<Encounter> encs, Shepherd myShepherd) {
    if ((enc == null) || !enc.hasMarkedIndividual() || Util.collectionIsEmptyOrNull(encs)) return "";
    MarkedIndividual indiv = enc.getIndividual();
    int numEnc = indiv.numEncounters();
    if (numEnc < 2) return "N";  // "no-only";  //easy but should never actually be 0
    System.out.println("isResighted(): " + enc.getCatalogNumber() + " one of " + numEnc + " encs for indiv=" + indiv.getId());
    boolean badComp = false;
    Long encMillis = enc.getDateInMilliseconds();
    if (encMillis == null) return "error";
    for (Encounter otherEnc : indiv.getEncounters()) {
        if (encs.contains(otherEnc)) {
            System.out.println("isResighted(): " + otherEnc.getCatalogNumber() + " exists in this import");
            continue;
        }
        Long otherMillis = otherEnc.getDateInMilliseconds();
        if (otherMillis == null) {
            System.out.println("isResighted(): " + otherEnc.getCatalogNumber() + " has no comparable timestamp! skipping");
            badComp = true;
            continue;
        }
        if (otherMillis < encMillis) {
            System.out.println("isResighted(): " + otherEnc.getCatalogNumber() + " is OLDER; resighted!");
            return "Y";
        }
        //otherwise, must be newer?  so we just continue....
    }
    if (badComp) return "error-badcomp";
    return "N";  // "no-fellthru";  //make it here, we must be new
}

private String intString(Integer i) {
    if (i == null) return "";
    return i.toString();
}
%>
<%

String context = ServletUtilities.getContext(request);
Shepherd myShepherd = new Shepherd(context);
myShepherd.setAction("rapid.jsp");
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
  
String taskId = request.getParameter("taskId");
String jdoql = null;
ImportTask itask = null;

if (taskId != null) {
    try {
        itask = (ImportTask) (myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(ImportTask.class, taskId), true));
    } catch (Exception ex) {}
    if ((itask == null) || !(adminMode || user.equals(itask.getCreator()))) {
        out.println("<h1 class=\"error\">taskId " + taskId + " is invalid</h1>");
        return;
    }
}

String svKey = "rapid_completed_" + taskId;

String setComplete = request.getParameter("complete");
if (setComplete != null) {
    myShepherd.beginDBTransaction();
    JSONObject m = SystemValue.getJSONObject(myShepherd, svKey);
    if (m == null) m = new JSONObject();
    m.put(setComplete, true);
    SystemValue.set(myShepherd, svKey, m);
    myShepherd.commitDBTransaction();
    myShepherd.closeDBTransaction();
    response.sendRedirect("rapid.jsp?taskId=" + taskId + "#row-" + setComplete);
    return;
}

if (request.getParameter("status") != null) {
    myShepherd.beginDBTransaction();
    JSONObject m = SystemValue.getJSONObject(myShepherd, svKey);
    if (m == null) m = new JSONObject();
    myShepherd.rollbackDBTransaction();
    myShepherd.closeDBTransaction();
    out.println(m.toString(4));
    return;
}

if (Util.requestParameterSet(request.getParameter("export"))) {
    List<List> rows = new ArrayList<List>();
    rows.add(Arrays.asList(new String[]{
        "Encounter",
        "Date",
        "URL",
        "Other Num",
        "Occur ID",
        "Indiv",
        "New name",
        "Resight",
        "Image(s)",
        "Species",
        "Sex",
        "Life stage",
        "Lat/Lon",
        "Vegetation",
        "Terrain",
        "Group sz",
        "Num adults",
        "Num AF",
        "Num AM",
        "Num SubAd",
        "Num SubF",
        "Num SubM",
        "Num calv"
    }));
    myShepherd.beginDBTransaction();
    List<Encounter> allEncs = itask.getAllEncounters(myShepherd);
    for (Encounter enc : allEncs) {
        if (enc == null) continue;
        Occurrence occ = myShepherd.getOccurrence(enc);
        List<String> row = new ArrayList<String>();
        row.add(enc.getCatalogNumber());
        String dt = enc.getDate();
        if ((dt != null) && (dt.length() > 10)) dt = dt.substring(0,10);
        row.add(dt);
        row.add("https://giraffespotter.org/encounters/encounter.jsp?number=" + enc.getCatalogNumber());
        row.add(enc.getOtherCatalogNumbers());
        row.add((occ == null) ? "" : occ.getOccurrenceID());
        if (enc.hasMarkedIndividual()) {
            row.add(enc.getIndividual().getDisplayName());
        } else {
            row.add("");
        }
        row.add(isNewName(enc, allEncs, myShepherd));
        row.add(isResighted(enc, allEncs, myShepherd));
        ArrayList<MediaAsset> assets = enc.getMedia();
        if (Util.collectionIsEmptyOrNull(assets)) {
            row.add("");
        } else {
            List<String> fn = new ArrayList<String>();
            for (MediaAsset ma : assets) {
                fn.add(ma.getFilename());
            }
            row.add(String.join(", ", fn));
        }
        row.add(enc.getTaxonomyString());
        row.add(enc.getSex());
        row.add(enc.getLifeStage());
        String ll = "";
        if ((enc.getDecimalLatitude() != null) && (enc.getDecimalLongitude() != null)) ll = enc.getDecimalLatitude() + ", " + enc.getDecimalLongitude();
        row.add(ll);
        if (occ != null) {
            row.add(occ.getVegetation());
            row.add(occ.getTerrain());
            row.add(intString(occ.getGroupSize()));
            row.add(intString(occ.getNumAdults()));
            row.add(intString(occ.getNumAdultFemales()));
            row.add(intString(occ.getNumAdultMales()));
            row.add(intString(occ.getNumSubAdults()));
            row.add(intString(occ.getNumSubFemales()));
            row.add(intString(occ.getNumSubMales()));
            row.add(intString(occ.getNumCalves()));
        }
        //jarr.put(new JSONArray(row));
        //out.println("<tr><td>" + String.join("</td><td>", row) + "</td></tr>");
        rows.add(row);
    }
    myShepherd.rollbackDBTransaction();
    myShepherd.closeDBTransaction();
    String filename = "giraffespotter_rapid_response_export_" + taskId + "_" + (new DateTime()).toString().substring(0,19) + ".xlsx";
    File xlsf = new File("/tmp/" + filename);
    org.ecocean.servlet.export.ExportExcelFile.quickExcel(rows, xlsf);
    response.setHeader("Content-type", "application/vnd.ms-excel");
    response.setHeader("Content-disposition", "attachment; filename=\"" + xlsf.getName() + "\"");
    FileUtils.copyFile(xlsf, response.getOutputStream());
    return;
}

JSONObject completedMap = SystemValue.getJSONObject(myShepherd, svKey);
if (completedMap == null) completedMap = new JSONObject();


%>
<jsp:include page="header.jsp" flush="true"/>
<style>

a.button {
    font-weight: bold;
    font-size: 0.8em;
    background-color: #AAA;
    border-radius: 4px;
    padding: 0 6px;
    text-decoration: none;
    cursor: pointer;
}
a.button:hover {
    background-color: #999;
    text-decoration: none;
}

.asset-row {
    display: flex;
    margin-top: 20px;
    padding: 5px;
}

.asset-row-complete {
    background-color: #d8fdf0;
}

.asset-wrapper {
    min-width: 450px;
    position: relative;
    xdisplay: inline-block;
}
.asset-wrapper img {
    height: 300px;
}
.asset-filename {
    position: absolute;
    bottom: 0;
    left: 0;
    width: 100%;
    background-color: rgba(255,255,255,0.7);
    font-weight: bold;
    color: black;
    text-align: center;
}

.annotlist {
    xdisplay: inline-block;
    flex: 1;
    overflow: hidden;
}

.annot-row {
    margin: 6px;
    padding: 4px;
    border-radius: 7px;
    background-color: #DDD;
}
.annot-row:hover, .annot-hilite {
    background-color: #DDA !important;
    outline: solid #444 1px;
}

.annot-id {
    font-size: 0.7em;
}
.annot-id, .annot-indiv, .enc-div, .proc-state, .annot-action {
    display: inline-block;
    margin: 0 3px;
}
.proc-state, .annot-action {
    float: right;
}

.proc-state {
    border-radius: 12px;
    width: 1.8em;
    text-align: center;
    background-color: #888;
    color: #FFF;
}

.proc-state-complete {
    background-color: #4D8;
}
.proc-state-review {
    background-color: #0c77c3;
}

.annot-action {
    width: 10em;
}

.feature-box {
    position: absolute;
    border: dashed 2px yellow;
    opacity: 0.4;
    cursor: pointer;
}
.feature-box:hover, .feature-hilite {
    opacity: 1.0 !important;
}
.feature-note {
    position: absolute;
    bottom: 0;
    left: 0;
    width: 100%;
    font-size: 0.75em;
    font-weight: bold;
    background-color: rgba(255,255,255,0.7);
    color: black;
    text-align: center;
}

#info-panel {
    position: fixed;
    left: 10px;
    padding: 10px;
}
#summary {
    padding: 10px;
}
</style>


<!--
    <script src="javascript/bootstrap-table/bootstrap-table.min.js"></script>
    <link rel="stylesheet" href="javascript/bootstrap-table/bootstrap-table.min.css" />
-->
<script>
var taskId = '<%=taskId%>';
var assetData = {};
function checkRows() {
    $('.asset-row').each(function(i, el) {
        var jel = $(el);
        var incomplete = jel.find('.annot-row').length - jel.find('.annot-state-complete').length;
        if (incomplete < 1) jel.addClass('asset-row-complete');
    });
    updateSummary();
    $('.annot-row').on('mouseover', function(ev) {
        var aid = ev.currentTarget.id.substring(10);
        $('.feature-' + aid).addClass('feature-hilite');
    });
    $('.annot-row').on('mouseout', function(ev) {
        var aid = ev.currentTarget.id.substring(10);
        $('.feature-' + aid).removeClass('feature-hilite');
    });
}


function updateSummary() {
    var total = $('.asset-row').length;
    var complete = $('.asset-row-complete').length;
    var h = '<b>' + complete + '</b> complete | ';
    h += '<b>' + (total - complete) + '</b> pending | ';
    h += '<b>' + total + '</b> total';
    $('#summary').html(h);
}

function toggleComplete() {
    $('.asset-row-complete').toggle();
}


function assetLoaded(imgEl) {
    var jel = $(imgEl);
    var scale = imgEl.height / imgEl.naturalHeight;
    var wrap = jel.closest('.asset-wrapper');
    var mid = wrap.attr('id').substring(14);
    if (!assetData[mid] || (Object.keys(assetData[mid]).length < 2)) return;
    for (var annId in assetData[mid]) {
        for (var i = 0 ; i < assetData[mid][annId].length ; i++) {
            if (assetData[mid][annId][i].origHeight) scale = imgEl.height / assetData[mid][annId][i].origHeight;
            if (assetData[mid][annId][i].type != 'org.ecocean.boundingBox') continue;
            if (!assetData[mid][annId][i].parameters) continue;
            var fbox = $('<div data-annotid="' + annId + '" class="feature-' + annId + ' feature-box"><div class="feature-note">' + annId.substring(0,8) + '</div></div>');
            fbox.css('width', (assetData[mid][annId][i].parameters.width * scale) + 'px');
            fbox.css('height', (assetData[mid][annId][i].parameters.height * scale) + 'px');
            fbox.css('left', (assetData[mid][annId][i].parameters.x * scale) + 'px');
            fbox.css('top', (assetData[mid][annId][i].parameters.y * scale) + 'px');
            if (assetData[mid][annId][i].parameters.theta) fbox.css('transform', 'rotate(' +  assetData[mid][annId][i].parameters.theta + 'rad)');
            //console.log('%s %d: %o', annId, i, assetData[mid][annId][i]);
            wrap.append(fbox);
            fbox.on('mouseover', function(ev) {
                var aid = ev.currentTarget.getAttribute('data-annotid');
                $('#annot-row-' + aid).addClass('annot-hilite');
            });
            fbox.on('mouseout', function(ev) {
                var aid = ev.currentTarget.getAttribute('data-annotid');
                $('#annot-row-' + aid).removeClass('annot-hilite');
            });
        }
    }
}

</script>


<div class="container maincontent">

<div id="info-panel">
    <input type="button" onClick="toggleComplete();" value="Toggle complete visibility" />
    <div id="summary"></div>
    <input type="button" onClick="window.location.href='rapid.jsp?taskId=<%=taskId%>&export';" value="Export Excel" />
</div>

<%

Map<String,Encounter> encMap = new HashMap<String,Encounter>();
List<Annotation> anns = new ArrayList<Annotation>();
List<MediaAsset> mas = new ArrayList<MediaAsset>();
for (Encounter enc : itask.getEncounters()) {
    if (!enc.hasAnnotations()) continue;
    for (Annotation ann : enc.getAnnotations()) {
        if (!anns.contains(ann)) anns.add(ann);
        encMap.put(ann.getId(), enc);
        MediaAsset ma = ann.getMediaAsset();
        if (ma == null) continue;
        if (!mas.contains(ma)) mas.add(ma);
        for (Annotation a : ma.getAnnotations()) {  //this catches second+ annots made via detection!
            if (!anns.contains(a)) anns.add(a);
        }
    }
}

//now we find encs for those secondary+ annots
for (Annotation ann : anns) {
    if (encMap.containsKey(ann.getId())) continue;
    Encounter enc = ann.findEncounter(myShepherd);
    if (enc != null) encMap.put(ann.getId(), enc);
}

for (MediaAsset ma : mas) {
%>
<div class="asset-row" id="asset-row-<%=ma.getId()%>">
    <div class="asset-wrapper" id="asset-wrapper-<%=ma.getId()%>">
        <img onLoad="assetLoaded(this);" data-src="<%=ma.safeURL(request)%>" />
        <div class="asset-filename"><%=ma.getFilename()%></div>
    </div>

    <div class="annotlist">
<%
JSONObject adata = new JSONObject();
for (Annotation ann : ma.getAnnotations()) {
    JSONArray fts = new JSONArray();
    for (Feature ft : ann.getFeatures()) {
        JSONObject jf = new JSONObject();
        jf.put("type", ft.getType());
        String foo = ft.getParametersAsString();  //silly dn busting
        jf.put("parameters", ft.getParameters());
        jf.put("origWidth", ma.getWidth());
        jf.put("origHeight", ma.getHeight());
        fts.put(jf);
    }
    adata.put(ann.getId(), fts);
    //List<Task> tasks = ann.getRootIATasks(myShepherd);
    List<Task> tasks = Task.getTasksFor(ann, myShepherd);
    int tsize = Util.collectionSize(tasks);
    Encounter enc = encMap.get(ann.getId());
    MarkedIndividual indiv = (enc == null) ? null : enc.getIndividual();
    String state = "processing";
    if (completedMap.optBoolean(ann.getId(), false)) {
        state = "complete";
    } else if (tsize > 0) {
        state = "review";
    }
%>
        <a name="row-<%=ann.getId()%>"></a>
        <div class="annot-row annot-state-<%=state%>" id="annot-row-<%=ann.getId()%>">
<% if (adminMode) { %>
            <div class="annot-id"><a target="_new" href="obrowse.jsp?type=Annotation&id=<%=ann.getId()%>"><%=shortId(ann.getId())%></a></div>
<% } else { %>
            <div class="annot-id"><%=shortId(ann.getId())%></div>
<% } %>
            <div class="annot-indiv">
<% if (indiv == null) { %>
                <span style="color: #AAA">unnamed</span>
<% } else { %>
                <a target="_new" href="individuals.jsp?id=<%=indiv.getIndividualID()%>"><b><%=indiv.getDisplayName(request)%></b></a>
<% } %>
            </div>
            <%=encDiv(ann, enc)%>
            <div class="proc-state proc-state-<%=state%>" title="status=<%=ann.getIdentificationStatus()%>; tasks=<%=tsize%>">
<% if (state.equals("review")) { %>
                <i class="el el-eye-open"></i>
<% } else if (state.equals("complete")) { %>
                <i class="el el-ok"></i>
<% } else { %>
                <i class="el el-hourglass"></i>
<% } %>
            </div>
            <div class="annot-action">
<%
if (state.equals("review")) {
    if (Util.collectionIsEmptyOrNull(tasks)) throw new RuntimeException("empty tasks for " + ann);
    Task reviewTask = tasks.get(0);  //fallback
    for (Task tk : tasks) {
        if (tk.countObjectAnnotations() == 1) reviewTask = tk;  //specific to this annot
    }
%>
                <a class="button" target="_new" href="iaResults.jsp?taskId=<%=reviewTask.getId()%>">Review</a>
<% }
if (!state.equals("complete")) { %>
                <a class="button" href="rapid.jsp?taskId=<%=taskId%>&complete=<%=ann.getId()%>">Mark Complete</a>
<% } %>
            </div>
        </div>
<% } %>
<script>
    assetData['<%=ma.getId()%>'] = <%=adata.toString()%>;
</script>
    </div>

</div>
<%
}


%>

</div>
<script>
function markComplete(annotId) {
    $('#annot-row-' + annotId).removeClass('annot-state-review').removeClass('annot-state-processing').addClass('annot-state-complete');
    $('#annot-row-' + annotId + ' .annot-action').remove();
    $('#annot-row-' + annotId + ' .proc-state').attr('class', 'proc-state proc-state-complete').html('<i class="el el-ok"></i>');
}

var updateInProgress = false;
$(window).on('focus', function() {
    if (updateInProgress) return;
    updateInProgress = true;
    $('#wild-me-badge').css('transform', 'rotate(1.0rad)');
    $.ajax({
        url: 'rapid.jsp?taskId=' + taskId + '&status',
        type: 'GET',
        dataType: 'json',
        complete: function(d) {
            if (d.responseJSON) {
console.log('updating %o', d.responseJSON);
                for (var aid in d.responseJSON) {
                    if (d.responseJSON[aid]) markComplete(aid);
                }
                checkRows();
                updateSummary();
            }
            $('#wild-me-badge').css('transform', 'rotate(0.0rad)');
            updateInProgress = false;
        }
    });
});
$(document).ready(function() {
    $('.asset-row img').each(function(i, el) {
        var s = el.getAttribute('data-src');
        if (s) el.src = s;
    });
    checkRows();
});
</script>

<jsp:include page="footer.jsp" flush="true"/>

