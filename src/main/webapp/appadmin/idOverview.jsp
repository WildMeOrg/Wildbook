<%@ page contentType="text/html; charset=utf-8" language="java"
     import="org.ecocean.*,
org.ecocean.servlet.ServletUtilities,
javax.jdo.*,
java.util.Iterator,
java.util.Arrays,
java.util.List,
java.io.File,
org.json.JSONArray,
org.json.JSONObject,
org.ecocean.media.*
              "
%><%


String context = ServletUtilities.getContext(request);
Shepherd myShepherd = new Shepherd(context);
List<String> colName = Arrays.asList("encId", "encTimestamp", "encLocId", "assetId", "annotId", "indivId", "assetUrl");
List<String> colClass = Arrays.asList("java.lang.String", "java.lang.Long", "java.lang.String", "java.lang.Integer", "java.lang.String", "java.lang.String", "java.lang.String");
List<String> colLabel = Arrays.asList("enc id", "time", "loc id", "ma", "ann", "indiv", "img");

String rootDir = getServletContext().getRealPath("/");
//String dataDir = ServletUtilities.dataDir(context, rootDir);
String fileName = "idOverviewData.json";
String dataFile = rootDir + fileName;

if (Util.requestParameterSet(request.getParameter("generateData"))) {
    String sqlMagic = "SELECT \"ENCOUNTER\".\"CATALOGNUMBER\" as encId," +
        "\"ENCOUNTER\".\"DATEINMILLISECONDS\" as encTimestamp," +
        "\"ENCOUNTER\".\"LOCATIONID\" as encLocId," +
        "\"MEDIAASSET\".\"ID\" as assetId," +
        "\"ENCOUNTER_ANNOTATIONS\".\"ID_EID\" as annotId," +
        "\"ENCOUNTER\".\"INDIVIDUALID\" as indivId FROM " +
        "\"MEDIAASSET\" join \"MEDIAASSET_FEATURES\" on (\"ID\" = \"ID_OID\") " +
        "JOIN \"ANNOTATION_FEATURES\" USING (\"ID_EID\") " + 
        "JOIN \"ENCOUNTER_ANNOTATIONS\" on (\"ANNOTATION_FEATURES\".\"ID_OID\" = \"ENCOUNTER_ANNOTATIONS\".\"ID_EID\") " +
        "JOIN \"ENCOUNTER\" on (\"ENCOUNTER_ANNOTATIONS\".\"CATALOGNUMBER_OID\" = \"ENCOUNTER\".\"CATALOGNUMBER\") " +
        "ORDER BY \"MEDIAASSET\".\"ID\";";

    Query q = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sqlMagic);
    List results = (List)q.execute();
    Iterator it = results.iterator();
    JSONObject rtn = new JSONObject();
    //JSONArray colArr = new JSONArray(colName);
    //rtn.put("cols", colArr);

    JSONArray dataArr = new JSONArray();
    int count = 0;
    while (it.hasNext()) {
        if (count % 1000 == 0) System.out.println("overview generate count=" + count);
        Object[] fields = (Object[])it.next();
        JSONArray jrow = new JSONArray();
        for (int i = 0 ; i < colName.size() - 1; i++) {
            Class<?> cls = Class.forName(colClass.get(i));
            jrow.put(cls.cast(fields[i]));
        }

        int maId = jrow.optInt(3, -1);
        if (maId > 0) {
            MediaAsset ma = MediaAssetFactory.load(maId, myShepherd);
            if (ma == null) {
                jrow.put(JSONObject.NULL);
            } else {
                try {
                    jrow.put(ma.webURL());
                } catch (Exception ex) {
                    jrow.put(JSONObject.NULL);
                }
            }
        }

/*   rows with elements as jsonobj
        JSONObject jrow = new JSONObject();
        for (int i = 0 ; i < colName.size() ; i++) {
            Class<?> cls = Class.forName(colClass.get(i));
            jrow.put(colName.get(i), cls.cast(fields[i]));
        }
*/
        dataArr.put(jrow);
        count++;
    }
    ////rtn.put("data", dataArr);

    String dataStr = dataArr.toString();
    response.setContentType("text/plain");
    Util.writeToFile(dataStr, dataFile);
    out.println(count + " encounters saved to " + fileName);
    return;
}

JSONArray colDefn = new JSONArray();
for (int i = 0 ; i < colName.size() ; i++) {
    JSONObject jc = new JSONObject();
    jc.put("field", colName.get(i));
    jc.put("title", colLabel.get(i));
    colDefn.put(jc);
}


%>
<!doctype html>
<html><head><title>ID Overview</title>
<style>
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
    img.tiny {
        max-height: 75px;
    }

    div.enc-annot {
        max-height: 200px;
        position: relative;
    }
    .enc-annot img {
        max-width: 100px;
        max-height: 100%;
    }
    .enc-annot-ma, .enc-annot-id, .enc-annot-indiv {
        width: 100%;
        position: absolute;
        background-color: rgba(0,0,0,0.3);
        color: rgba(255,255,255,0.8) !important;
        display: inline-block;
        text-decoration: none;
        font-size: 0.9em;
        padding-left: 2px;
        cursor: pointer;
    }
    .enc-annot-ma {
        top: 0;
        left: 0;
    }
    .enc-annot-id {
        bottom: 0;
        left: 0;
        overflow-x: hidden;
        white-space: nowrap;
    }
    .enc-annot-indiv {
        display: none;
        bottom: 1.3em;
        left: 0;
    }
    .enc-annot-ma:hover, .enc-annot-id:hover, .enc-annot-indiv:hover {
        background-color: #444;
        color: #FFF !important;
    }

    .enc-annot:hover .enc-annot-indiv {
        display: inline-block;
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

var colDefn = <%= colDefn.toString() %>;
var tableEl;
var rawData = null;

function init() {
    tableEl = $('#result-table');
    status('reading data....');
    $.ajax({
        url: '<%=fileName%>',
        dataType: 'json',
        success: function(d) {
            rawData = d;
            //rawTable();
            encTable();
        },
        error: function(x) {
            console.log('error fetching data %o', x);
            alert('ERROR loading data');
        },
        contentType: 'application/json',
/*
        xhr: function() {
            var xhr = new window.XMLHttpRequest();
            //var xhr = $.ajaxSettings.xhr();
            xhr.addEventListener("progress", function(evt) {
                if (evt.lengthComputable) {
                    console.log('%o %o', evt.loaded, evt.total);
                }
            }, false);
            return xhr;
        },
*/
        type: 'GET'
    });

/*
    $('#test-table').bootstrapTable({
        url: 'idOverview.jsp?data',
        pagination: true,
        search: true,
        columns: <%=colDefn.toString()%>
    });
*/
}


function resetTable() {
    $('.bootstrap-table').remove();
    $('#result-table').remove();
    tableEl = $('<div id="result-table" />');
    tableEl.appendTo($('body'));
}

function rawTable() {
    resetTable();
    var cols = new Array();
    for (var i = 0 ; i < colDefn.length ; i++) {
        cols.push(Object.assign({ sortable: true }, colDefn[i]));
/*
        if (colDefn[i].field == 'assetId') cols.push({
            sortable: false,
            field: 'maUrl',
            title: 'img'
        });
*/
    }
    cols[6].sortable = false;
    tableEl.bootstrapTable({
        data: convertData(function(row) {
            var newRow = {};
            for (var i = 0 ; i < row.length ; i++) {
                newRow[colDefn[i].field] = row[i];
            }
            if (newRow.assetUrl) newRow.assetUrl = '<ximg class="tiny" src="' + newRow.assetUrl + '" />';
            return newRow;
        }),
        search: true,
        pagination: true,
        columns: cols
    });
    postTableUpdate();
}
 

function encTable() {
    status('starting to create table');
    resetTable();
    var cols = new Array();
    var edata = encData();
    tableEl.bootstrapTable({
        data: edata,
        search: true,
        pagination: true,
        sortName: 'encId',
        columns: encDataCols
    });
    postTableUpdate();
    $('.enc-annot').parent().css('padding', '1px');
}

var encDataCache = false;
var encDataCols = new Array();
var MAX_LEN = 8;
function encData() {
    if (encDataCache) return encDataCache;

    //make it the first time, boo :(
    encDataCols.push(
        { field: 'encId', title: 'enc', sortable: true },
        { field: 'encTimestamp', title: 'date', sortable: true },
        { field: 'indivId', title: 'indiv(s)', sortable: true }
    );
    var e = {};
    var maxLen = 0;
    for (var i = 0 ; i < rawData.length ; i++) {
//if (i % 100 == 0) console.info('%d of %d', i, rawData.length);
        if (!e[rawData[i][0]]) e[rawData[i][0]] = new Array();
        e[rawData[i][0]].push([ rawData[i][3], rawData[i][4], rawData[i][5], rawData[i][6], rawData[i][1] ]);
        if (e[rawData[i][0]].length > maxLen) maxLen = e[rawData[i][0]].length;
    }

    if (maxLen > MAX_LEN) {
        //alert('too many annots on some encounters; truncated from (max) ' + maxLen + ' to ' + MAX_LEN);
        console.warn('too many annots on some encounters; truncated from (max) ' + maxLen + ' to ' + MAX_LEN);
        maxLen = MAX_LEN;
    }
    for (var i = 0 ; i < maxLen ; i++) {
        encDataCols.push({
            field: 'annot' + i,
            title: 'Ann ' + i,
            sortable: false
        });
    }
    encDataCache = new Array();
    for (var eid in e) {
        var row = { encId: eid };
        row.indivId = encIndivCell(e[eid]);
        row.encTimestamp = toDateString(e[eid]);
        var i = 0;
        while ((i < e[eid].length) && (i < maxLen)) {
            row['annot' + i] = encAnnot(e[eid][i]);
            i++;
        }
        encDataCache.push(row);
    }
    return encDataCache;
}

var dtimer = null;
function updateDataStatus() {
    dtimer = window.setTimeout(function() {
        status(new Date());
        updateDataStatus();
    }, 1000);
console.log('started? %o', dtimer);
}

function toDateString(milli) {
    if (!milli[0][4]) return null;
    var d = new Date(milli[0][4]);
    return d.toISOString().substr(0,10);
}

function encIndivCell(annots) {
    if (!annots) return '';
    var inds = {};
    for (var i = 0 ; i < annots.length ; i++) {
        if (annots[i][2]) inds[annots[i][2]] = 1;
    }
    return Object.keys(inds).join(',');
}

function encAnnot(data) {
    var h = '<div class="enc-annot">';
    if (data[3]) h += '<img src="' + data[3] + '" />';
    h += '<a class="enc-annot-ma">' + data[0] + '</a>';
    h += '<a class="enc-annot-id" title="' + data[1] + '">' + data[1] + '</a>';
    if (data[2]) h += '<a class="enc-annot-indiv">' + data[2] + '</a>';
    h += '</div>';
    return h;
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


function postTableUpdate() {
    status('&nbsp;');
    $('.search').after('<div id="controls">' +
        '<input type="button" onClick="encTable()" value="by encounter" />' +
        '<input type="button" onClick="rawTable()" value="raw data" />' +
    '</div>');
}

function status(msg) {
    $('#status-message').html(msg);
}

</script>

</head>
<body onLoad="init()">
<div id="status-message"></div>


<table id="result-table"></table>


</body>
</html>
