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
org.json.JSONArray,
org.json.JSONObject,

org.ecocean.servlet.ServletUtilities,
org.ecocean.media.*
              "
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
<!doctype html>
<html><head><title>Resolve Duplicates</title>
<style>
    #result-table td {
        padding: 1px 4px;
        white-space: nowrap;
        overflow-x: hidden;
        max-width: 14em;
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
    'obrowse.jsp?type=MediaAsset&id=',
    'obrowse.jsp?type=Annotation&id=',
    'obrowse.jsp?type=MediaAsset&id=',
    'obrowse.jsp?type=Annotation&id=',
    '',
    'encounters/encounter.jsp?number=',
    'individuals.jsp?number='
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
            if (colNum == 1) id = ev.target.parentElement.childNodes[3].innerText;
            openInTab('../' + urlPrefix[colNum] + id);
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
<div id="status-message"></div>


<table id="result-table"></table>


</body>
</html>
