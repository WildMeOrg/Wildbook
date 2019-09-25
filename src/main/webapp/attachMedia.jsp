<style>

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

#occs {
    margin: 20px;
}

#occs th {
    padding: 0 8px;
}
#occs td {
    padding: 5px;
    max-width: 9em;
    overflow: hidden;
    white-space: nowrap;
}

#occs td, #occs th {
    font-size: 1.2em !important;
}

#occs td.td-occid {
    width: 4em;
    overflow: hidden;
    white-space: nowrap;
}

.td-int {
    text-align: right;
    padding-right: 30px !important;
}

.td-num-0, .dull {
    color: #BBB !important;
}

.hover-note {
    margin-right: 7px;
    color: blue;
    cursor: pointer;
}

#occs tr {
    cursor: pointer;
}
#occs tr:hover td {
    background-color: #BFF !important;
}

.occ-enc td {
    padding: 1px 5px;
}
.occ-enc tr {
    border: solid 2px #888;
}

.ph-info {
    font-size: 0.9em;
    background-color: #FFA;
    color: #888;
    padding: 0 5px;
    margin: 0 2px;
    border-radius: 3px;
}


/* for uploader */

div#file-activity {
	font-family: sans;
	border: solid 2px black;
	padding: 8px;
	margin: 20px;
	height: 450px;
	overflow-y: scroll;
}
div.file-item {
	position: relative;
	background-color: #DDD;
	border-radius: 3px;
	margin: 2px;
}

div.file-item div {
	display: inline-block;
	padding: 3px 7px;
}
.file-name {
	width: 30%;
}
.file-size {
	width: 8%;
}

.file-bar {
	position: absolute;
	width: 0;
	height: 100%;
	padding: 0 !important;
	left: 0;
	border-radius: 3px;
	background-color: rgba(100,100,100,0.3);
}

.new-media-asset-wrapper {
    position: relative;
    display: inline-block;
    margin: 8px;
}
.new-media-asset-wrapper img {
    max-height: 200px;
}

.new-media-asset-filename {
    position: absolute;
    bottom: 0;
    left: 0;
    font-weight: bold;
    width: 100%;
    background-color: rgba(255,255,255,0.8);
    text-align: center;
    padding: 3px;
}

#updone {
    margin-top: 20px;
    font-size: 1.2em;
}

.bulk-media {
    display: inline-block;
    padding: 3px;
    margin: 3px;
    background-color: #DDD;
    width: 150px;
    position: relative;
    min-height: 75px;
}

.bulk-media .bulk-info {
    left: 0;
    overflow: hidden;
    width: 100%;
    font-size: 0.65em;
    font-weight: bold;
    position: absolute;
    background-color: rgba(0,0,0,0.3);
    color: white;
    padding: 0 2px;
}
.bulk-media .bulk-info:hover {
    width: auto;
    z-index: 100;
    background-color: #444;
}

.bulk-media img {
    width: 150px;
}


#bulk-media-list,
#app-data-list {
    width: 48.5%;
    padding: 5px;
    display: inline-block;
    margin: 2px;
    background-color: #EEE;
    vertical-align: top;
}

</style>

<%@ page contentType="text/html; charset=utf-8" 
		language="java"
        import="org.ecocean.servlet.ServletUtilities,org.ecocean.*,
org.ecocean.datacollection.Instant,
org.ecocean.media.MediaAsset,
org.ecocean.media.Feature,
org.json.JSONObject,
org.json.JSONArray,
java.util.Collection,
java.util.Arrays,
java.util.ArrayList,
java.util.List,
java.util.HashMap,
java.util.Map,
javax.jdo.*,
java.util.Properties" %>

<%!

private static String emptyTd() {
    return "<td class=\"dull\">&#x2014;</td>";
}

private static String submittersTd(Occurrence occ) {
    if (occ == null) return emptyTd();
    if (Util.collectionIsEmptyOrNull(occ.getSubmitters())) return emptyTd();
    return "<td title=\"number of submitters: " + occ.getSubmitters().size() + "\">" + occ.getSubmitters().get(0).getDisplayName() + "</td>";
}

private static String niceLL(final Double ll) {
    if (ll == null) return "-";
    return new Double(Math.floor(ll * 1000) / 1000).toString();
}

private static String niceDouble(final Double d) {
    if (d == null) return "-";
    return new Double(Math.floor(d * 100) / 100).toString();
}

private static Map<String,String> getTripInfo(Occurrence occ) {
    Map<String,String> typeLabel = new HashMap<String,String>();
    typeLabel.put("ci", "Channel Is");
    typeLabel.put("wa", "WhaleAlert");
    typeLabel.put("cw", "CaribWhale");

    Map<String,String> rtn = new HashMap<String,String>();
    String src = occ.getSource();
    if (src == null) src = ":??:-1";
    String f[] = src.split(":");
    if (f.length > 1) {
        rtn.put("typeCode", f[1]);
        rtn.put("typeLabel", (typeLabel.get(f[1]) == null) ? "???" : typeLabel.get(f[1]));
    } else {
        rtn.put("typeCode", "??");
        rtn.put("typeLabel", "???");
    }
    rtn.put("id", (f.length > 2) ? f[2] : "-1");
    return rtn;
}

private static String phString(JSONObject ftp) {
    if (ftp == null) return "";
    String phnote = "";
    phnote += "PID Code: " + ftp.optString("PID Code", "-");
    phnote += "; Card #" + ftp.optString("Card Number", "-");
    int s = ftp.optInt("Image Start", 0);
    int e = ftp.optInt("Image End", 0);
    if (s * e > 0) phnote += ", images " + s + "-" + e;
    return phnote;
}

%>

<%

String context = ServletUtilities.getContext(request);

  //setup our Properties object to hold all properties
  //String langCode = "en";
  String langCode=ServletUtilities.getLanguageCode(request);
boolean showUpload = false;
  


//set up the file input stream
  Properties props = new Properties();
  //props.load(getClass().getResourceAsStream("/bundles/" + langCode + "/login.properties"));
  props = ShepherdProperties.getProperties("login.properties", langCode,context);


%>



  <!-- Make sure window is not in a frame -->

  <script language="JavaScript" type="text/javascript">

    <!--
    if (window.self != window.top) {
      window.open(".", "_top");
    }
    // -->

  </script>
<jsp:include page="header.jsp" flush="true"/>


<script src="tools/flow.min.js"></script>
<script src="javascript/uploader.js"></script>

<script src="javascript/tablesorter/jquery.tablesorter.js"></script>
<link rel="stylesheet" href="javascript/tablesorter/themes/blue/style.css" type="text/css" media="print, projection, screen" />

<script>
$(document).ready(function() {
    $('#occs').tablesorter();
    //$('#occs tbody td:nth-child(3)').on('click', function(ev) {
    $('#occs tr').on('click', function(ev) {
        var occId = ev.currentTarget.getAttribute('id');
        wildbook.openInTab('attachMedia.jsp?id=' + occId);
    });

    uploaderInit(uploadFinished);
});

function updateStatus(s) {
    document.getElementById('updone').innerHTML = s;
}

function uploadFinished() {
    updateStatus('<b>upload finished</b>, processing...');
    var assetData = [];
    $('#file-activity .file-item .file-name').each(function(i, el) {
        assetData.push({ filename: el.innerText, accessKey: user.accessKey });
    });
    var mac = {
        MediaAssetCreate: [
            {
                assets: assetData
            }
        ],
        skipIA: true
    };
console.log("sending MediaAssetCreate: %o", mac);

    $.ajax({
        url: 'MediaAssetCreate',
        contentType: 'application/javascript',
        type: 'POST',
        data: JSON.stringify(mac),
        complete: function(x, stat) {
            console.log('MediaAssetCreate COMPLETE status = %o; xhr=%o', stat, x);
            if (x && x.responseJSON && x.responseJSON.success && x.responseJSON.allMediaAssetIds) {
                updateStatus('<b>images uploaded</b>, processing...');
                createdMediaAssets(x.responseJSON.withoutSet);
            } else {
                updateStatus('<b class="error">Error sending images: ' + stat + '</b>');
            }
        },
        dataType: 'json'
    });

}

function createdMediaAssets(assets) {
console.warn('done!?!?!?! %o', assets);
    var added = $('<div id="new-media-assets" />');
    var maIds = [];
    for (var i = 0 ; i < assets.length ; i++) {
        var h = '<div class="new-media-asset-wrapper">';
        h += '<img src="' + assets[i].url + '" />';
        h += '<div title="' + assets[i].id + '" class="new-media-asset-filename">' + assets[i].filename + '</div>';
        h += '</div>';
        added.append(h);
        maIds.push(assets[i].id);
    }
    $('#file-activity').hide().after(added);
    if (addToEncounterId) {
        attachMediaAssets(addToEncounterId, maIds);
    } else {
        createEncounter(maIds);
    }
}

function attachMediaAssets(encId, maIds) {
    if (!encId || !Array.isArray(maIds)) return;
    var data = {
        attach: true,
        EncounterID: encId,
        mediaAssetIds: maIds
    };
    $.ajax({
        url: 'MediaAssetAttach',
        contentType: 'application/javascript',
        type: 'POST',
        data: JSON.stringify(data),
        complete: function(x, stat) {
            console.log('attachMediaAssets() COMPLETE status = %o; xhr=%o', stat, x);
            if (x && x.responseJSON && x.responseJSON.success && x.responseJSON.maIds) {
                updateStatus('<b>Complete.</b> ' + x.responseJSON.maIds.length + ' image' + ((x.responseJSON.maIds.length == 1) ? '' : 's') + ' attached to <b><a target="_new" href="encounters/encounter.jsp?number=' + encId + '">Encounter ' + encId.substring(0,8) + '</a></b>.');
            } else {
                updateStatus('<b class="error">Error attaching images: ' + stat + '</b>');
            }
        },
        dataType: 'json'
    });
}

//  TODO need a way to properly set species!!
function createEncounter(maIds) {
    if (!Array.isArray(maIds)) return;
    var srcs = [];
    for (var i = 0 ; i < maIds.length ; i++) {
        srcs.push({ mediaAssetId: maIds[i] });
    }
    var data = {
        accessKey: user.accessKey,
        occurrenceId: occurrence.id,
        dateString: occurrence.date.toISOString(),
        dateMilliseconds: occurrence.date.getTime(),
        email: user.email,
        species: 'unknown',
        userId: user.id,
        sources: srcs
    };
console.log('DATA => %o', data);
    $.ajax({
        url: 'EncounterCreate',
        contentType: 'application/javascript',
        type: 'POST',
        data: JSON.stringify(data),
        complete: function(x, stat) {
            console.log('createEncounter() COMPLETE status = %o; xhr=%o', stat, x);
            if (x && x.responseJSON && x.responseJSON.success && x.responseJSON.encounterId) {
                updateStatus('<b>Complete.</b> Created <b><a target="_new" href="encounters/encounter.jsp?number=' + x.responseJSON.encounterId + '">new Encounter ' + x.responseJSON.encounterId.substring(0,8) + '</a></b> with ' + x.responseJSON.assets.length + ' image' + ((x.responseJSON.assets.length == 1) ? '' : 's') + '.');
            } else {
                var err = stat;
                if (x.responseJSON && x.responseJSON.error) err = x.responseJSON.error;
                updateStatus('<b class="error">Error creating encounter: ' + err + '</b>');
            }
        },
        dataType: 'json'
    });
}
</script>


<div class="container maincontent">
</div>

<%
String id = request.getParameter("id");
Occurrence occ = null;

if (AccessControl.isAnonymous(request)) {
    out.println("<h1>Please <a href=\"login.jsp\">login</a></h1>");

} else if (Util.requestParameterSet(id)) {
    Shepherd myShepherd = new Shepherd(context);
    myShepherd.setAction("attachMedia.jsp");
    myShepherd.beginDBTransaction();
    occ = myShepherd.getOccurrence(id);
    if (occ == null) {  //TODO also some security check that user can access this occurrence!!
        out.println("<h2>Invalid ID " + id + "</h2>");
    	myShepherd.rollbackAndClose();
        return;
    }
    Map<String,String> tripInfo = getTripInfo(occ);

    User user = AccessControl.getUser(request, myShepherd);
    String accessKey = Util.generateUUID();
////// TODO also get user who owns occ!!!!!!!
%>
<script>
    var user = {
        id: '<%=user.getUUID()%>',
        accessKey: '<%=accessKey%>',
        email: '<%=user.getEmailAddress()%>'
    };
</script>
<div style="padding: 0 20px;">
<a class="button" href="attachMedia.jsp">back to list</a>
<h2>Sighting <a href="occurrence.jsp?number=<%=occ.getOccurrenceID()%>">[<%=occ.getOccurrenceID().substring(0,8)%>]</a></h2>
<script>
    var occurrence = {
        id: '<%=occ.getOccurrenceID()%>',
        date: new Date('<%=occ.getDateTimeCreated()%>')
    };
</script>
<p>
<%=tripInfo.get("typeLabel")%>
<b>Trip ID = <%=tripInfo.get("id")%></b>
<i><%=occ.getDateTimeCreated()%></i>
</p>

<%
if (!Util.collectionIsEmptyOrNull(occ.getTaxonomies())) {
    out.println("<p><b>Species: <i>");
    List<String> tname = new ArrayList<String>();
    for (Taxonomy tax : occ.getTaxonomies()) {
        if (!tname.contains(tax.getScientificName())) tname.add(tax.getScientificName());
    }
    out.println(String.join(", ", tname) + "</i></b></p>");
}

if (occ.getSightingPlatform() != null) {
    out.println("<p>Vessel: <i>" + occ.getSightingPlatform() + "</i></p>");
}
%>

<p>
<%
String survId = occ.getCorrespondingSurveyID();
if (survId == null) {
    out.println("<i>No survey associated</i>");
} else {
    out.println("Survey: <a href=\"surveys/survey.jsp?surveyID=" + survId + "\"><b>" + survId.substring(0,8) + "</b></a>");
}
%>
</p>
<p><i>Comments:</i> <%=occ.getComments()%></p>

<%

if (!Util.collectionIsEmptyOrNull(occ.getBehaviors())) {
    out.println("<p><i>Behaviors:</i><ul>");
    for (Instant behav : occ.getBehaviors()) {
        out.println("<li>" + behav.getValue().toString().substring(0,19) + " <b>" + behav.getName() + "</b></li>");
    }
    out.println("</ul></p>");
}

String toEncId = request.getParameter("toEncounter");
Encounter addToEncounter = null;
if (toEncId != null) addToEncounter = myShepherd.getEncounter(toEncId);
if (addToEncounter != null) {
    //TODO other safety checks here like can user alter this encounter?
    if (!occ.getEncounters().contains(addToEncounter)) addToEncounter = null;
}

%>
<script>var addToEncounterId = <%=((addToEncounter == null) ? "false" : "'" + addToEncounter.getCatalogNumber() + "'")%>;</script>
<%

if (Util.requestParameterSet(request.getParameter("newEncounter"))) {
    showUpload = true;
%>


<h2>New encounter</h2>

<%


} else if ((toEncId != null) && (addToEncounter == null)) {
    out.println("<h1 class=\"error\">Invalid Encounter id=" + toEncId + "</h1>");

} else if (addToEncounter != null) {
    showUpload = true;
%>

<h2>Adding images to Encounter <%=addToEncounter.getCatalogNumber().substring(0,8)%></h2>
<p>
<a target="_new" href="encounters/encounter.jsp?number=<%=addToEncounter.getCatalogNumber()%>" class="button">link</a><br />
Date: <b><%=addToEncounter.getDate()%></b>
<br />
Taxonomy: <b><%=addToEncounter.getTaxonomyString()%></b>
<br />
<%
    if (addToEncounter.numAnnotations() < 1) {
        out.println("<i>Currently contains no images.</i>");
    } else {
        int imgCt = 0;
        out.println("Image notes: <ul>");
        boolean showedParam = false;
        for (Annotation ann : addToEncounter.getAnnotations()) {
            if (Util.collectionIsEmptyOrNull(ann.getFeatures())) continue;
            Feature ft = ann.getFeatures().get(0);
            if (ft.getMediaAsset() != null) {   //we do *not* check feature type in this case.  should we?
                imgCt++;
            } else if (ft.isType("org.ecocean.MediaAssetPlaceholder") && !showedParam) {
                out.println("<li>" + phString(ft.getParameters()) + "</li>");
                showedParam = true;
            }
        }
        if (imgCt > 0) out.println("<li>Already includes " + imgCt + " image" + ((imgCt == 1) ? "" : "s") + "</li>");
        out.println("</ul>");
    }
%>
</p>

<%
} else {
%>
<table class="occ-enc">
<%
for (Encounter enc : occ.getEncounters()) {
    out.println("<tr>");
    out.println("<td><a href=\"encounters/encounter.jsp?number=" + enc.getCatalogNumber() + "\">" + enc.getCatalogNumber().substring(0,8) + "</a></td>");
    out.println("<td>" + enc.getDate() + "</td>");
    out.println("<td>" + enc.getTaxonomyString() + "</td>");

    List<JSONObject> ph = new ArrayList<JSONObject>();
    List<MediaAsset> media = new ArrayList<MediaAsset>();
    String phinfo = "";
    if (enc.getAnnotations() != null) {
        for (Annotation ann : enc.getAnnotations()) {
            if (Util.collectionIsEmptyOrNull(ann.getFeatures())) continue;
            Feature ft = ann.getFeatures().get(0);
System.out.println(ft.getParameters());
            if (ft.getMediaAsset() != null) {   //we do *not* check feature type in this case.  should we?
                media.add(ft.getMediaAsset());
            } else if (ft.isType("org.ecocean.MediaAssetPlaceholder")) {
                if (phinfo.equals("")) phinfo = "<span class=\"ph-info\">" + phString(ft.getParameters()) + "</span> ";
                ph.add(ft.getParameters());
            }
        }
    }

    out.println("<td>" + media.size() + " photos added</td>");  //TODO do something with media list!
    out.println("<td>" + phinfo + "</td>");
    out.println("<td><a href=\"attachMedia.jsp?id=" + occ.getOccurrenceID() + "&toEncounter=" + enc.getCatalogNumber() + "\" class=\"button\" style=\"padding: 1px 8px\">add photos here</a></td>");
    out.println("</tr>");
}
%>
</table>
</div>

<p style="padding: 10px 20px;" >
    <a href="attachMedia.jsp?id=<%=occ.getOccurrenceID()%>&newEncounter" class="button">add new encounter and photos</a>
</p>
<%
    
    }  //end newEncounter option
	myShepherd.commitDBTransaction();
	myShepherd.closeDBTransaction();
} else {
    Shepherd myShepherd = new Shepherd(context);
    myShepherd.setAction("attachMedia.jsp2");
    myShepherd.beginDBTransaction();
    User user = AccessControl.getUser(request, myShepherd);
    boolean admin = (user != null) && "admin".equals(user.getUsername());
    //String filter = "SELECT FROM org.ecocean.Occurrence WHERE source != null";
    String filter = "SELECT FROM org.ecocean.Occurrence WHERE source.matches('SpotterConserveIO:.*')";
    if (admin && (request.getParameter("uuid") != null)) {
        filter += " && submitters.contains(u) && u.uuid == '" + Util.basicSanitize(request.getParameter("uuid")) + "'";
    } else if (!admin) {
        filter += " && submitters.contains(u) && u.uuid == '" + user.getUUID() + "'";
    }
    Query query = myShepherd.getPM().newQuery(filter);
    query.setOrdering("dateTimeCreated desc");
    Collection coll = (Collection) query.execute();
    if (coll.size() < 1) {
        out.println("<h2>None available</h2>");
    } else {
%>
<h2>Listing of imported Ocean Alert data</h2>

<script src="javascript/exif.js"></script>
<script type="text/javascript">
function folderToggle(el) {
    if ($(el).is(':checked')) {
        $('#file-chooser').attr('directory', '');
        $('#file-chooser').attr('webkitdirectory', '');
    } else {
        $('#file-chooser').attr('directory', null);
        $('#file-chooser').attr('webkitdirectory', null);
    }
    return true;
}

function populateImage(md) {
    if (!md.div || !md.file) return;
    var img = $(md.div).find('img');
    var reader = new FileReader();
//console.log('populateImage() img => %o', img);
    reader.onload = function() {
console.log('DONE with img=%o', img);
        img.prop('src', reader.result);
    };
    reader.readAsDataURL(md.file);
}

var mediaData = [];
function filesChanged2(inp) {
    $('#upcontrols').hide();
    //filesChanged(inp);  //in uploader.js
    var maxSizeBytes = 3000000000;
    var f = '';
    if (inp.files && inp.files.length) {
        var all = [];
        mediaData[inp.files.length - 1] = false;  //set mediaData to length of # of files
        for (var i = 0 ; i < inp.files.length ; i++) {
            inp.files[i]._offset = i;
            mediaData[i] = { file: inp.files[i], complete: false };
            if (inp.files[i].size > maxSizeBytes) {
                mediaData[i].error = 'too big';
                mediaData[i].complete = true;
                all.push('<span class="error">' + inp.files[i].name + ' (' + Math.round(inp.files[i].size / (1024*1024)) + 'MB is too big, xxxxMB max)</span>');
            } else {
                mediaData[i].div = $('<div class="bulk-media" data-offset="' + i + '"><img /></div>');
                $('#bulk-media-list').append(mediaData[i].div);
                populateImage(mediaData[i]);
                mediaData[i].div.append('<div style="bottom: 0;" class="bulk-info">' + inp.files[i].name + '</div>');
                all.push(inp.files[i].name + ' (' + Math.round(inp.files[i].size / 1024) + 'k)');
                EXIF.getData(inp.files[i], function() { gotExif(this); });
            }
        }
        f = '<b>' + inp.files.length + ' file' + ((inp.files.length == 1) ? '' : 's') + ':</b> ' + all.join(', ');
    } else {
        f = inp.value;
    }
    document.getElementById('input-file-list').innerHTML = f;
}


function checkMediaDataComplete() {
    for (var i = 0 ; i < mediaData.length ; i++) {
        if (!mediaData[i] || !mediaData[i].complete) return;  //meh, not done
    }
console.info('OFFSET... DONE mediaData!!!!!');
    var sorted = $('.bulk-media');
    sorted.sort(function(a, b) {
        var sortA = a.getAttribute('data-sort') * 1;
        var sortB = b.getAttribute('data-sort') * 1;
        if (sortA > sortB) return 1;
        if (sortA < sortB) return -1;
        return 0;
    });
    $('#bulk-media-list').html(sorted);
    filterList();
}


function filterList() {
    var dates = [];
    for (var i = 0 ; i < mediaData.length ; i++) {
        if (!mediaData[i].exifParsed.dt) continue;
        for (var j = 0 ; j < mediaData[i].exifParsed.dt.length ; j++) {
            var dt = mediaData[i].exifParsed.dt[j].substr(0,10);
            if (dates.indexOf(dt) < 0) dates.push(dt);
        }
    }
console.info('dates => %o', dates);
    $('#occs tbody tr').hide();
    for (var i = 0 ; i < dates.length ; i++) {
        $('tr.date-' + dates[i]).show();
    }
/*
    $('#occs tbody tr').each(function(i, el) {
        var dt = el.querySelector('td:nth-child(4)').innerText;
        //var lat = el.querySelector('td:nth-child(7)').innerText;
        //var lon = el.querySelector('td:nth-child(8)').innerText;
//console.log('%o %o %o', dt, lat, lon);
//console.log('%o', dt);
        if (dates.indexOf(dt.substr(0,10)) < 0) {
            $(el).hide();
        } else {
            $(el).show();
        }
    });
*/
}

//TODO Bearing, Altitude
function gotExif(file) {
console.info("OFFSET = %o", file._offset);
    mediaData[file._offset].exifParsed = {
        dt: exifFindDateTimes(file.exifdata),
        ll: exifFindLatLon(file.exifdata)
    };
    var d = '?';
    for (var i = 0 ; i < mediaData[file._offset].exifParsed.dt.length ; i++) {
        if (mediaData[file._offset].exifParsed.dt[i].length > d.length) d = mediaData[file._offset].exifParsed.dt[i];
    }
    mediaData[file._offset].div.attr('data-sort', new Date(d).getTime());
    mediaData[file._offset].div.append('<div style="bottom: 1.4em;" class="bulk-info">' + d + '</div>');
    mediaData[file._offset].complete = true;
    checkMediaDataComplete();
}


function exifFindDateTimes(exif) {
    var dtList = [];
    for (var key in exif) {
        if (key.toLowerCase().indexOf('date') < 0) continue;
        var clean = cleanupDateTime(exif[key]);
        if (clean && (dtList.indexOf(clean) < 0)) dtList.push(clean);
    }
    return dtList;
}

function exifFindLatLon(exif) {
    var llList = [];
    //unknown if these keys are "standard".  :(  doubt it.
    var lat = cleanupLatLon(exif.GPSLatitudeRef, exif.GPSLatitude);
    var lon = cleanupLatLon(exif.GPSLongitudeRef, exif.GPSLongitude);
    if (!lat || !lon) return;
    var clean = lat + ', ' + lon;
    if (clean && (llList.indexOf(clean) < 0)) llList.push(clean);
    return llList;
}

function cleanupDateTime(dt) {
    var f = dt.split(/\D+/);
    if (f.length == 3) return f.join('-');
    if ((f.length == 5) || (f.length == 6)) return f.slice(0,3).join('-') + ' ' + f.slice(3,6).join(':');
    return null;
}

function cleanupLatLon(llDir, ll) {
    var sign = ((llDir == 'W' || llDir == 'S') ? -1 : 1);
    if (!ll || (ll.length != 3)) return null;
    return Math.round(sign * dms2dd(ll[0], ll[1], ll[2]) * 1000000) / 1000000;
}

function dms2dd(d, m, s) {
    return d + (m / 60) + (s / 3600);
}

function exifDTSet(el) {
    $('#datepicker').val(el.value);
}

function exifLLSet(el) {
    var ll = [ '', '' ];
    if (el.value.indexOf(', ') >= 0) ll = el.value.split(', ');
    $('#lat').val(ll[0]);
    $('#longitude').val(ll[1]);
    updateMap();
}

</script>

<div id="file-activity">
    <div id="bulk-media-list"></div>
    <div id="app-data-list"></div>
</div>
<div id="updone"></div>
<div id="input-file-list"></div>
<div id="upcontrols" style="padding: 20px;">
	<!-- webkitdirectory directory -->
	<input type="file" id="file-chooser" multiple accept="audio/*,video/*,image/*" onChange="return filesChanged2(this)" /> 

        <div>
            <input type="checkbox" onClick="return folderToggle(this);" /> <b>Use folders</b>
        </div>

    <div style="display: none;padding: 20px 0;">
        <a class="button" id="upload-button">begin upload</a>
        <a class="button" href="attachMedia.jsp?id=xxx" title="return to listing for Sighting xxxx">cancel</a>
    </div>

</div>

<table class="tablesorter" id="occs"><thead><tr>
<%
        List<String> heads = new ArrayList<String>(Arrays.asList(new String[]{"Type", "Trip #", "Sight", "Date/Time", "Species", "#Adult,Calv", "Lat", "Lon", "Bearing", "# Encs", "Photos", "Placeholder", "# Behav", "Modified"}));
        if (admin) heads.add("User(s)");
        for (String h : heads) {
            out.println("<th>" + h + "</th>");
        }
%>
</tr></thead>
<tbody>
<%
        JSONObject forJS = new JSONObject();
        for (Object o : coll) {
            occ = (Occurrence) o;
            JSONObject jsObj = new JSONObject();
            jsObj.put("id", occ.getOccurrenceID());

            String rowClass = "date-" + occ.getDateTimeCreated().substring(0,10);
            List<Taxonomy> tax = occ.getTaxonomies(); //TODO also check encs???
            List<String> tnames = new ArrayList<String>();
            if (!Util.collectionIsEmptyOrNull(tax)) {
                for (Taxonomy t : tax) {
                    tnames.add(t.getScientificName());
                }
            }
            for (String tn : tnames) {
                rowClass += " tax-" + tn.replaceAll(" ", "-").toLowerCase();
            }

            String row = "<tr class=\"" + rowClass + "\" id=\"" + occ.getOccurrenceID() + "\">";
            Map<String,String> tripInfo = getTripInfo(occ);
            row += "<td>" + tripInfo.get("typeLabel") + "</td>";
            row += "<td class=\"td-int\">" + tripInfo.get("id") + "</td>";
            row += "<td class=\"td-occid\">" + occ.getOccurrenceID().substring(0,8) + "</td>";
            row += "<td>" + occ.getDateTimeCreated().substring(0,16) + "</td>";
            if (tnames.size() < 1) {
                row += emptyTd();
            } else {
                row += "<td>" + String.join(", ", tnames) + "</td>";
            }
            int numAdults = (occ.getNumAdults() == null) ? 0 : occ.getNumAdults();
            int numCalves = (occ.getNumCalves() == null) ? 0 : occ.getNumCalves();
            row += "<td class=\"td-intx td-num-" + (numAdults + numCalves) + "\">" + numAdults + ", " + numCalves + "</td>";
            row += "<td>" + niceLL(occ.getDecimalLatitude()) + "</td>";
            row += "<td>" + niceLL(occ.getDecimalLongitude()) + "</td>";
            row += "<td>" + niceDouble(occ.getBearing()) + "</td>";
            row += "<td class=\"td-int td-num-" + occ.getNumberEncounters() + "\">" + occ.getNumberEncounters() + "</td>";
            int numPhotos = 0;
            int numPlaceholders = 0;
            List<String> phlist = new ArrayList<String>();
            if (occ.getNumberEncounters() > 0) {
                JSONArray jse = new JSONArray();
                for (Encounter enc : occ.getEncounters()) {
                    JSONObject jenc = new JSONObject();
                    jenc.put("id", enc.getCatalogNumber());
                    jenc.put("tax", enc.getTaxonomyString());
                    jenc.put("dt", enc.getDate());
//occ-enc
                    if (enc.getAnnotations() == null) {
                        jse.put(jenc);
                        continue;
                    }
                    int encPhotos = 0;
                    String encPh = "";
                    for (Annotation ann : enc.getAnnotations()) {
                        if (Util.collectionIsEmptyOrNull(ann.getFeatures())) continue;
                        Feature ft = ann.getFeatures().get(0);
                        if (ft.getMediaAsset() != null) {   //we do *not* check feature type in this case.  should we?
                            numPhotos++;
                            encPhotos++;
                        } else if (ft.isType("org.ecocean.MediaAssetPlaceholder")) {
                            String phs = phString(ft.getParameters());
                            encPh += phs + " ";
                            if (!phlist.contains(phs)) phlist.add(phs);
                            numPlaceholders++;
                        }
                    }
                    jenc.put("numPhotos", encPhotos);
                    if (!encPh.equals("")) jenc.put("ph", encPh);
                    jse.put(jenc);
                }
                jsObj.put("encs", jse);
            }

            String phnote = "";
            if (phlist.size() > 0) {
                phnote = String.join(" | ", phlist);
                //phnote = "<span class=\"hover-note\" title=\"" + phnote + "\">\u2731</span>";
                phnote = " title=\"" + phnote + "\" ";
            }
            row += "<td class=\"td-int td-num-" + numPhotos + "\">" + numPhotos + "</td>";
            row += "<td " + phnote + " class=\"td-int td-num-" + numPlaceholders + "\">" + numPlaceholders + "</td>";
            int numBehav = Util.collectionSize(occ.getBehaviors());
            row += "<td class=\"td-int td-num-" + numBehav + "\">" + numBehav + "</td>";
            row += (Util.stringExists(occ.getDWCDateLastModified()) ? "<td>" + occ.getDWCDateLastModified() + "</td>" : emptyTd());
            if (admin) row += submittersTd(occ);
            out.println(row + "</tr>");
            forJS.put(occ.getOccurrenceID(), jsObj);
        }
        out.println("</tbody></table>");
        out.println("<script>var appData = " + forJS.toString(4) + ";</script>");
    }
    query.closeAll();
    myShepherd.rollbackAndClose();
}

if (showUpload) {
%>

<div id="file-activity"></div>
<div id="updone"></div>
<div id="upcontrols" style="padding: 20px;">
	<!-- webkitdirectory directory -->
	<input type="file" id="file-chooser" multiple accept="audio/*,video/*,image/*" xonChange="return filesChanged(this)" /> 
    <div style="padding: 20px 0;">
        <a class="button" id="upload-button">begin upload</a>
        <a class="button" href="attachMedia.jsp?id=<%=occ.getOccurrenceID()%>" title="return to listing for Sighting <%=occ.getOccurrenceID().substring(0,8)%>">cancel</a>
    </div>

</div>

<% } %>
<div style="padding-top: 20px;"></div>
          <jsp:include page="footer.jsp" flush="true"/>
