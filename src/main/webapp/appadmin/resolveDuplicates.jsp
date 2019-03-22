<%@ page contentType="text/html; charset=utf-8" language="java"
     import="org.ecocean.*,
java.util.Collection,
java.io.IOException,
java.util.ArrayList,
java.util.Arrays,
javax.jdo.Query,
java.util.List,
java.util.Iterator,
java.util.Map,
java.util.HashMap,
java.lang.reflect.Method,
java.lang.reflect.Field,
org.json.JSONArray,
org.json.JSONObject,

org.ecocean.servlet.ServletUtilities,
org.ecocean.media.*
              "
%><%!

static boolean hasValue(Object v) {
    if (v == null) return false;
    if (v instanceof List) {
        List x = (List)v;
        return (x.size() > 0);
    }
    if (v instanceof String) {
        String x = (String)v;
        return (Util.stringExists(x) && !x.toLowerCase().equals("none") && !x.toLowerCase().equals("no estimate provided") && !x.toLowerCase().equals("none provided") && !x.toLowerCase().equals("unknown"));
    }
    if (v instanceof Boolean) return true;
    if (v instanceof Double) {
        Double x = (Double)v;
        return (x != 0.0);
    }
    if (v instanceof Long) {
        Long x = (Long)v;
        return (x != 0L);
    }
    if (v instanceof Integer) {
        Integer x = (Integer)v;
        return (x != 0);
    }
System.out.println("**** " + v.getClass());
    return true;
}

%><%

boolean data = "data".equals(request.getQueryString());
String context = ServletUtilities.getContext(request);
Shepherd myShepherd = new Shepherd(context);
myShepherd = new Shepherd("context0");








if (data) {
    String sql = "select \"MEDIAASSET\".\"ID\" as assetId, \"MEDIAASSET\".\"ACMID\" as assetAcmId,\"MEDIAASSET\".\"PARAMETERS\" as assetParams,\"ANNOTATION\".\"ID\" as annotId, \"ANNOTATION\".\"ACMID\" as annotAcmId, \"ENCOUNTER\".\"CATALOGNUMBER\" as encId, \"ENCOUNTER\".\"INDIVIDUALID\" as indivId from \"MEDIAASSET\" join \"MEDIAASSET_FEATURES\" on (\"ID\" = \"ID_OID\") join \"ANNOTATION_FEATURES\" using (\"ID_EID\") join \"ANNOTATION\" on (\"ANNOTATION_FEATURES\".\"ID_OID\" = \"ANNOTATION\".\"ID\") join \"ENCOUNTER_ANNOTATIONS\" on (\"ANNOTATION_FEATURES\".\"ID_OID\" = \"ENCOUNTER_ANNOTATIONS\".\"ID_EID\") join \"ENCOUNTER\" on (\"ENCOUNTER_ANNOTATIONS\".\"CATALOGNUMBER_OID\" = \"ENCOUNTER\".\"CATALOGNUMBER\") where \"MEDIAASSET\".\"ACMID\" is not null AND \"ANNOTATION\".\"ACMID\" is not null order by \"MEDIAASSET\".\"ID\" limit 300000";

    Map<String,Integer> count = new HashMap<String,Integer>();
    List<List<String>> all = new ArrayList<List<String>>();
    Query q = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
    List results = (List)q.execute();
    Iterator it = results.iterator();
    while (it.hasNext()) {
        Object[] row = (Object[]) it.next();
        List<String> lrow = new ArrayList<String>();
        String assetAcmId = (String)row[1];
        String annotAcmId = (String)row[4];
        lrow.add(assetAcmId);
        lrow.add(annotAcmId);
        lrow.add(Integer.toString((Integer)row[0]));
        lrow.add((String)row[3]);  //annotId
        JSONObject params = Util.stringToJSONObject((String)row[2]);  //asset.params
        if (params != null) {
            lrow.add(params.optString("path"));
        } else {
            lrow.add("");
        }
        lrow.add((String)row[5]); //encId
        lrow.add((String)row[6]); //indivId
        all.add(lrow);

        if (count.get(assetAcmId) == null) count.put(assetAcmId, 0);
        if (count.get(annotAcmId) == null) count.put(annotAcmId, 0);
        count.put(assetAcmId, count.get(assetAcmId) + 1);
        count.put(annotAcmId, count.get(annotAcmId) + 1);
    }

    JSONArray jall = new JSONArray();
    for (List<String> row : all) {
        Integer assetCt = count.get(row.get(0));
        if (assetCt == null) assetCt = 1;
        Integer annotCt = count.get(row.get(1));
        if (annotCt == null) annotCt = 1;
        if (assetCt + annotCt < 3) continue;
        JSONArray jrow = new JSONArray(row);
        jrow.put(assetCt);
        jrow.put(annotCt);
        jall.put(jrow);
    }
    out.println(jall.toString());


    return;
}

%>
<!doctype html>
<html><head><title>Resolve Duplicates</title>

    <!-- Bootstrap CSS -->
    <link rel="stylesheet" href="https://stackpath.bootstrapcdn.com/bootstrap/4.2.1/css/bootstrap.min.css" integrity="sha384-GJzZqFGwb1QTTN6wy59ffF1BuGJpLSa9DkKMp0DgiMDm4iYMj70gZWKYbI706tWS" crossorigin="anonymous">
    <link rel="stylesheet" href="https://use.fontawesome.com/releases/v5.6.3/css/all.css" integrity="sha384-UHRtZLI+pbxtHCWp1t77Bi1L4ZtiqrqD80Kn4Z8NTSRyMA2Fd33n5dQ8lWUE00s/" crossorigin="anonymous">
    <link rel="stylesheet" href="https://unpkg.com/bootstrap-table@1.13.4/dist/bootstrap-table.min.css">

    <!-- jQuery first, then Popper.js, then Bootstrap JS, and then Bootstrap Table JS -->
    <script src="https://code.jquery.com/jquery-3.3.1.min.js" integrity="sha256-FgpCb/KJQlLNfOu91ta32o/NMZxltwRo8QtmkMRdAu8=" crossorigin="anonymous"></script>
    <script src="https://cdnjs.cloudflare.com/ajax/libs/popper.js/1.14.6/umd/popper.min.js" integrity="sha384-wHAiFfRlMFy6i5SRaxvfOCifBUQy1xHdJ/yoi7FRNXMRBu5WHdZYu1hA6ZOblgut" crossorigin="anonymous"></script>
    <script src="https://stackpath.bootstrapcdn.com/bootstrap/4.2.1/js/bootstrap.min.js" integrity="sha384-B0UglyR+jN6CkvvICOB2joaf5I4l3gm9GU6Hc1og6Ls7i6U/mkkaduKaBhlAXv9k" crossorigin="anonymous"></script>
    <script src="https://cdnjs.cloudflare.com/ajax/libs/core-js/2.6.2/core.min.js"></script>
    <script src="https://unpkg.com/bootstrap-table@1.13.4/dist/bootstrap-table.min.js"></script>

<%

String resolveAnnotAcmId = request.getParameter("resolveAnnotAcmId");
if (resolveAnnotAcmId != null) {
%>
<style>
#encs {
    margin-right: 100px;
}

.enc {
    padding: 8px;
    margin: 5px 20px;
    position: relative;
}

.enc-chosen {
    background-color: #ABE;
}

.enc:hover {
    background-color: #DD8;
}

.props {
    width: 40%;
    display: inline-block;
}

.prop {
    overflow: hidden;
    white-space: nowrap;
    max-height: 1.5em;
    font-size: 0.85em;
    margin: 2px;
    display: none;
    padding: 0px 8px;
    background-color: #CCC;
    border-radius: 4px;
}

.prop:hover {
    overflow: auto;
    background-color: #DD4;
    max-height: 10em;
    white-space: normal;
}

.prop-getIndividualID {
    color: #FFF !important;
    background-color: #444 !important;
}

.controls {
    position: absolute;
    padding-left: 10px;
    top: 30px;
    display: inline-block;
}

.bonus {
}

.enc:hover .semi-hidden {
    background-color: #DEDEDE;
    display: block;
}

</style>
<script>
$(document).ready(function() {
    findDiff();
    resort();
    $('#encs .enc:first').addClass('enc-chosen');
});

var semiHidden = [
    'getModified',
    'getDateInMilliseconds',
    'getDWCDateAdded',
    'getDWCDateAddedLong',
    'getDWCDateLastModified',
];
var pval = {};
function findDiff() {
    $('.enc').each(function(i, el) {
        var encId = el.id;
        $(el).find('.prop').each(function(j, pel) {
//console.log('%o -> %o', el.id, pel.getAttribute('data-prop'));
            var prop = pel.getAttribute('data-prop');
            if (semiHidden.includes(prop)) {
                $(pel).addClass('semi-hidden');
                return;
            }
            var val = $(pel).find('.val').text();
//console.log('%o [%o] -> %o', encId, prop, val);
            if (pval[prop] == undefined) {
                pval[prop] = val;
            } else if (pval[prop] != val) {
//console.log('diff found in prop %s (via enc %s) %o => %o', prop, encId, pval[prop], val);
                $('.prop-' + prop).show();
                pval[prop] = false;
            }
        });
    });
    for (p in pval) {
        if (pval[p] === false) continue;
        $('.prop-' + p).show().addClass('bonus');
    }
}

var wayBig = 999999;
function resort() {
    var guts = $('.enc').sort(function (a, b) {
        var ja = $(a);
        var jb = $(b);
        var contentA = parseInt(ja.attr('data-numanns')) + parseInt(ja.attr('data-adjust'));
        var contentB = parseInt(jb.attr('data-numanns')) + parseInt(jb.attr('data-adjust'));
        if (ja.hasClass('enc-chosen')) contentA = wayBig;
        if (jb.hasClass('enc-chosen')) contentB = wayBig;
        var rtn = (contentA < contentB) ? -1 : (contentA > contentB) ? 1 : 0;
        return -rtn;  //cuz we want desc sort!
    });
    $('#encs').html(guts);
}


var val2 = 100;
function moveTo2(encId) {
    $('#' + encId).attr('data-adjust', val2);
    val2 += 100;
    resort();
}

function makeMain(encId) {
    $('.enc-chosen').removeClass('enc-chosen');
    $('#' + encId).addClass('enc-chosen');
    resort();
}

</script>
</head><body>

<div id="encs">
<%
    Class<?> cls = Encounter.class;
    //Field fields[] = cls.getDeclaredFields();
    Method methods[] = cls.getDeclaredMethods();

    String filter = "SELECT FROM org.ecocean.Encounter WHERE this.annotations.contains(ann) && ann.acmId == '" + Util.basicSanitize(resolveAnnotAcmId) + "'";
    Query query = myShepherd.getPM().newQuery(filter);
    query.setOrdering("catalogNumber");
    Collection c = (Collection) (query.execute());
    List<Encounter> encs = new ArrayList<Encounter>(c);
    query.closeAll();

    //List<String> skip = Arrays.asList("getClone", "getMedia", "getEncounterNumber", "getAnnotations", "getID", "getPrimaryMediaAsset", "getAccessControl", "getDWCGlobalUniqueIdentifier", "getOKExposeViaTapirLink");
    List<String> okMethods = Arrays.asList("getAge", "getAlternateID", "getAssignedUsername", "getBehavior", "getBodyCondition", "getComments", "getCountry", "getDate", "getDateInMilliseconds", "getDay", "getDecimalLatitudeAsDouble", "getDecimalLongitudeAsDouble", "getDepth", "getDepthAsDouble", "getDistinguishingScar", "getDWCDateAdded", "getDWCDateAddedLong", "getDWCDateLastModified", "getDynamicProperties", "getEndDateInMilliseconds", "getEndDateTime", "getEndDecimalLatitudeAsDouble", "getEndDecimalLongitudeAsDouble", "getEventID", "getGeneticSex", "getGenus", "getHaplotype", "getHour", "getIdentificationRemarks", "getImmunoglobin", "getIndividualID", "getInformOthers", "getInformOthersEmails", "getInjured", "getInterestedResearchers", "getLifeStage", "getLivingStatus", "getLocation", "getLocationCode", "getLocationID", "getMatchedBy", "getMaximumDepthInMeters", "getMaximumElevationInMeters", "getMeasurements", "getMeasurementUnit", "getMeasureUnits", "getMetalTags", "getMinutes", "getModified", "getMonth", "getOccurrenceID", "getOccurrenceRemarks", "getOtherCatalogNumbers", "getParasiteLoad", "getPatterningCode", "getPhotographerAddress", "getPhotographerEmail", "getPhotographerEmails", "getPhotographerName", "getPhotographerPhone", "getPhotographers", "getPointLocation", "getRComments", "getRecordedBy", "getReleaseDateLong", "getReproductiveStage", "getSampleTakenForDiet", "getSatelliteTag", "getSex", "getShortDate", "getSizeAsDouble", "getSizeGuess", "getSoil", "getSpecificEpithet", "getStartDateTime", "getState", "getSubmitterAddress", "getSubmitterEmail", "getSubmitterEmails", "getSubmitterID", "getSubmitterName", "getSubmitterOrganization", "getSubmitterPhone", "getSubmitterProject", "getSubmitters", "getSurveyID", "getSurveyTrackID", "getTaxonomyString", "getTissueSamples", "getVerbatimEventDate", "getVerbatimLocality", "getYear");

    for (Encounter enc : encs) {
        int numAnns = ((enc.getAnnotations() == null) ? 0 : enc.getAnnotations().size());
        out.println("<div data-adjust=\"0\" data-numanns=\"" + numAnns + "\" class=\"enc\" id=\"" + enc.getCatalogNumber() + "\">");
        out.println("<b>" + enc.getCatalogNumber() + "</b> (" + numAnns + " anns)<br /><div class=\"props\">");
        for (Method m : methods) {
            if (!m.getName().startsWith("get")) continue;
            if (m.getParameters().length != 0) continue;
            if (!okMethods.contains(m.getName())) continue;
//System.out.println("OK: " + m.getName());
            try {
                Object val = m.invoke(enc);
                if (hasValue(val)) out.println("<div data-prop=\"" + m.getName() + "\" class=\"prop-" + m.getName() + " prop\">" + m.getName() + ": <b class=\"val\">" + val + "</b></div>");
            } catch (java.lang.reflect.InvocationTargetException ex) {}
            //out.println("<div class=\"prop\">" + m.getName() + "</div>");
        }
/*
        for (Field f : fields) {
            try {
                out.println("<div class=\"prop\">" + f.getName() + ": <b>" + f.get(enc) + "</b></div>");
            } catch (java.lang.IllegalAccessException ex) {}
        }
*/
%>
    </div>
    <div class="controls">
        <input type="button" value="move to #2" onClick="return moveTo2('<%=enc.getCatalogNumber()%>');" />
        <input type="button" value="make main" onClick="return makeMain('<%=enc.getCatalogNumber()%>');" />
    </div>
</div>
<%
        out.println("</div></div>");
    }
    out.println("</div></body></html>");
    return;  //resolveAnnotAcmId end
}

List<String> colName = Arrays.asList("assetAcmId", "annotAcmId", "assetId", "annotId", "path", "encId", "indivId", "assetAcmCt", "annotAcmCt");
List<String> colLabel = Arrays.asList("asset acm", "annot acm", "asset id", "annot id", "filename", "enc", "indiv", "asset acm ct", "annot acm ct");


JSONArray colDefn = new JSONArray();
for (int i = 0 ; i < colName.size() ; i++) {
    JSONObject jc = new JSONObject();
    jc.put("field", colName.get(i));
    jc.put("title", colLabel.get(i));
    colDefn.put(jc);
}


%>

<style>

    .instruction {
        padding: 8px;
        font-size: 0.9em;
    }

    #result-table td {
        padding: 1px 4px;
        white-space: nowrap;
        overflow-x: hidden;
        max-width: 12em;
    }
    #controls {
        padding: 10px;
        display: inline-block;
    }
    #status-message {
        color: #238;
        font-size: 0.8em;
        padding: 5px;
    }

    #result-table tbody {
        font-size: 0.8em;
    }

    .muted {
        color: #999;
    }

    #result-table tbody td.more {
        cursor: zoom-in;
    }
    td.more-link {
        cursor: pointer !important;
    }

</style>


<script type="application/javascript">

var currentServerTime = <%=System.currentTimeMillis()%>;
var colDefn = <%= colDefn.toString() %>;
var tableEl;
var rawData = null;
var shiftDown = false;

function init() {
    tableEl = $('#result-table');
    $(document).on('keyup keydown', function(ev) {
        if (ev.keyCode != 17) return;
        shiftDown = (ev.type == 'keydown');
        if (shiftDown) {
            $('.more').addClass('more-link');
        } else {
            $('.more-link').removeClass('more-link');
        }
    });
    status('reading data....');
    $.ajax({
        url: 'resolveDuplicates.jsp?data',
        dataType: 'json',
        success: function(d) {
            status('');
            rawData = d;
            mkTable();
        },
        error: function(x) {
            status('<b style="color: red;">ERROR loading data</b>');
            console.log('error fetching data %o', x);
        },
        contentType: 'application/json',
        type: 'GET'
    });
}


function resetTable() {
    $('.bootstrap-table').remove();
    $('#result-table').remove();
    tableEl = $('<div id="result-table" />');
    tableEl.appendTo($('body'));
}

function searchTable(text) {
    theTable.bootstrapTable('resetSearch', text);
    if (text) {
        status('filtering on <b>' + text + '</b> &nbsp <i title="show all data" class="muted" style="cursor: pointer;" onClick="previousSearch = \'\'; searchTable(false)">reset filter</i>');
    } else {
        status('');
    }
    //postTableUpdate();  //done by onPageUpdate
}

var sortOn = 'assetAcmId';
var theTable;
function mkTable() {
    resetTable();
    var cols = new Array();
    for (var i = 0 ; i < colDefn.length ; i++) {
        cols.push(Object.assign({ sortable: true }, colDefn[i]));
    }
    theTable = tableEl.bootstrapTable({
        data: convertData(function(row) {
            var newRow = {};
            for (var i = 0 ; i < row.length ; i++) {
                newRow[colDefn[i].field] = row[i];
            }
            var x = newRow.path.lastIndexOf('/');
            if (x > -1) newRow.path = newRow.path.substring(x + 1);
            return newRow;
        }),
        search: true,
        onPostBody: function() {
            tableTweak();
            postTableUpdate();
console.log('POST BODY');
        },
        onSort: function(name, order) {
            sortOn = name;
        },
        pagination: true,
        pageSize: 20,
        columns: cols
    });
    //postTableUpdate();  //handled by onPostBody above!
}
 

function tableTweak() {
    var cn = getColNum(sortOn);
    if (cn < 0) return;
    if ((cn == 0) || (cn == 2) || (cn == 7)) {
        colorize(0);
        colorize(2);
        return;
    }
    if ((cn == 1) || (cn == 8)) {
        colorize(1);
        return;
    }
    colorize(cn);
}

function colorize(cn) {
    $('tr td:nth-child(' + (cn+1) + ')').each(function(i, el) {
        var jel = $(el);
        var t = jel.text();
        var c;
        if (t.length < 6) {
            c = Math.floor(255 - t.substr(1,2));
        } else {
            c = Math.floor(255 - (parseInt(t.substr(5,2), 16) / 2));
        }
//console.log('%s -> %s', jel.text(), c);
        jel.css('background-color', 'rgb(' + c + ',' + c + ',' + c + ')');
    });
}

function getColNum(name) {
    for (var i = 0 ; i < colDefn.length ; i++) {
        if (colDefn[i].field == name) return i;
    }
    return -1;
}
function toDateString(milli) {
    if (!milli[0][4]) return null;
    var d = new Date(milli[0][4]);
    return d.toISOString().substr(0,10);
}

//this takes each row (from rawData) of flat data and returns each row as a proper json obj
function convertData(rowFunc) {
    var rtn = new Array();
    for (var i = 0 ; i < rawData.length ; i++) {
        var newRow = rowFunc(rawData[i]);
        if (newRow) rtn.push(newRow);
    }
    return rtn;
}


var previousSearch = null;
var urlPrefix = [
    '../obrowse.jsp?type=MediaAsset&id=',
    'resolveDuplicates.jsp?resolveAnnotAcmId=',
    '../obrowse.jsp?type=MediaAsset&id=',
    '../obrowse.jsp?type=Annotation&id=',
    '',
    '../encounters/encounter.jsp?number=',
    '../individuals.jsp?number='
];
function postTableUpdate() {
    $('tbody tr td').filter(':nth-child(1), :nth-child(2), :nth-child(3), :nth-child(4), :nth-child(6), :nth-child(7)').addClass('more').on('click', function(ev) {
        //var colNum = $(ev.target.parentElement).data('index');
        var colNum = ev.target.cellIndex;
        var searchOn = ev.target.innerText;
        if (colNum == 2) {
            searchOn = ev.target.parentElement.childNodes[0].innerText;
        }
        console.log('shift=%o, colNum=%o, searchOn=%s [%s], ev=%o', shiftDown, colNum, searchOn, previousSearch, ev);

        if (shiftDown) {
            var id = ev.target.innerText;
            if (colNum == 0) id = ev.target.parentElement.childNodes[2].innerText;
            //if (colNum == 1) id = ev.target.parentElement.childNodes[3].innerText;
            openInTab(urlPrefix[colNum] + id);
            return;
        }

        if (previousSearch == searchOn) {
            previousSearch = '';
            searchTable(false);
            status('<i class="muted">search reset</i>');
        } else {
            searchTable(searchOn);
            previousSearch = searchOn;
        }
    });
}

function status(msg) {
    $('#status-message').html(msg);
}

function openInTab(url) {
    var win = window.open(url, '_blank');
    if (win) win.focus();
}

</script>

</head>
<body onLoad="init()">
<div class="instruction">
<b>CTRL</b> - link-mode (click to open in new tab)
</div>
<div id="status-message"></div>


<table id="result-table"></table>


</body>
</html>
