<%@ page contentType="text/html; charset=utf-8" language="java"
     import="org.ecocean.*,
java.util.Collection,
java.io.IOException,
java.util.Map,
java.util.HashMap,
java.util.Iterator,
java.util.ArrayList,
javax.jdo.Query,
java.util.List,
java.util.Map,
org.json.JSONObject,
org.json.JSONArray,

org.ecocean.servlet.ServletUtilities,
org.ecocean.media.*
              "

%><%!

private static List<Taxonomy> getAllTaxonomies(Shepherd myShepherd) {
    Query q = myShepherd.getPM().newQuery("SELECT FROM org.ecocean.Taxonomy");
    q.setOrdering("scientificName");
    Collection c = (Collection) (q.execute());
    List<Taxonomy> txs = new ArrayList<Taxonomy>(c);
    q.closeAll();
    return txs;
}

%><%

String context = ServletUtilities.getContext(request);
Shepherd myShepherd = new Shepherd(context);
myShepherd = new Shepherd("context0");

String id = request.getParameter("id");
if (id != null) {
%><html><head><title>Taxonomy Tool: edit <%=id%></title>
<script>
var myId = '<%=id%>';
function findTaxonomy(txt) {
    var found = [];
    if (!txt || (txt.length < 2)) return found;
    for (var i = 0 ; i < txMap.length ; i++) {
        if (txMap[i].indexOf(txt) > -1) found.push(txMap[i].substring(0,36));
    }
    return found;
}

function findMergeInto(el) {
    //console.log(el.value);
    var found = findTaxonomy(el.value);
    var div = document.getElementById('merge-into');
    if (found.length < 1) {
        div.innerHTML = '';
        return;
    }
    var h = '';
    for (var i = 0 ; i < found.length ; i++) {
        var det = txDetails[found[i]];
        if (!det) det = {
            scientificName: '?' + found[i] + '?',
            nonSpecific: true
        };
        h += '<div title="' + found[i] + (det.nonSpecific ? ' * NON-SPECIFIC" class="non-specific' : '') + '">';
        h += '<b>' + det.scientificName + '</b>';
        h += ' <a target="_new" href="taxonomyTool.jsp?id=' + found[i] + '" title="info">[?]</a>';
        h += ' <a target="_new" href="taxonomyTool.jsp?absorb=' + myId + '&into=' + found[i] + '" title="MERGE!!!">[!]</a>';
        h += '</div>';
    }
    div.innerHTML = h;
}
</script>
<style>
body {
    font-family: arial, sans;
}
#merge-into {
    max-height: 200px;
    padding: 8px;
}
.non-specific {
    xbackground-color: #FAA !important;
    background-color: #FAA;
}

#merge-into div {
    display: inline-block;
    margin: 1px 3px;
    padding: 1px 8px;
    background-color: #DDD;
    font-size: 0.9em;
    border-radius: 3px;
}
#merge-into div:hover {
    background-color: #FFC;
}

</style>
</head><body>
<%
    Taxonomy tax = null;
    try {
      tax = (Taxonomy)myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(Taxonomy.class, id), true);
    } catch (Exception ex) {}
    if (tax == null) {
        out.println("<p>Unknown Taxonomy id=<b>" + id + "</b></p>");
        return;
    }
    JSONObject txDetails = new JSONObject();
    JSONArray txMap = new JSONArray();
    for (Taxonomy tx : getAllTaxonomies(myShepherd)) {
        JSONObject d = new JSONObject();
        d.put("scientificName", tx.getScientificName());
        d.put("nonSpecific", tx.getNonSpecific());
        d.put("commonNames", new JSONArray(tx.getCommonNames()));
        txDetails.put(tx.getId(), d);
        List<String> names = new ArrayList<String>();
        names.add(tx.getScientificName());
        names.addAll(tx.getCommonNames());
        txMap.put(tx.getId() + ":" + String.join("|", names));
    }
    
%>
<script>
var txDetails = <%=txDetails.toString(4)%>;
var txMap = <%=txMap.toString(4)%>;
</script>

<h1>Taxonomy <%=id%></h1>
<h2><i><%=tax.getScientificName()%></i></h2>
<div>
    Merge into <input onKeyUp="findMergeInto(this)" /><br />
    <div id="merge-into">
    </div>
</div>
<p><b>Occurrences:</b> <ul>
<%
    for (Occurrence occ : tax.getOccurrences(myShepherd)) {
        out.println("<li><a href=\"../occurrence.jsp?number=" + occ.getOccurrenceID() + "\">" + occ.getOccurrenceID() + "</a></li>");
    }
    out.println("</ul></p><p><b>Common names:</b><ul>");
    if (!Util.collectionIsEmptyOrNull(tax.getCommonNames())) for (String cn : tax.getCommonNames()) {
        out.println("<li>" + cn + "</li>");
    }
    out.println("</ul></p>");
    return;  //end id= section
}

%><html><head><title>Taxonomy Tool</title>
<script src="https://ajax.googleapis.com/ajax/libs/jquery/3.3.1/jquery.min.js"></script>
<script src="../javascript/tablesorter/jquery.tablesorter.js"></script>
<link rel="stylesheet" href="../javascript/tablesorter/themes/blue/style.css" type="text/css" media="print, projection, screen" />
<script>

$(document).ready(function() {
    $('#all-tax').tablesorter();
});

</script>
<style>
body {
    font-family: sans, arial;
}
.td-num-0, .dull {
    color: #BBB !important;
}

tr:hover td {
    background-color: #BFF !important;
}

tr.non-specific td {
    /* background-color: #FFA !important; */
    color: #999 !important;
}
</style>
</head>

<body><%

    String sql = "select \"ID_EID\" as id, count(*) as ct from \"OCCURRENCE_TAXONOMIES\" group by id order by ct desc;";
    Query q = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
    List results = (List)q.execute();
    Map<String,Long> tcount = new HashMap<String,Long>();
    Iterator it = results.iterator();
    while (it.hasNext()) {
        Object[] row = (Object[]) it.next();
        String tid = (String)row[0];
        Long ct = (Long)row[1];
        tcount.put(tid, ct);
    }

    List<Taxonomy> txs = getAllTaxonomies(myShepherd);
    out.println("<table id=\"all-tax\" class=\"tablesorter\"><thead><tr><th>ID</th><th>&#x2248;</th><th>Scientific name</th><th># occs</th><th># names</th></tr></thead><tbody>");
    for (Taxonomy tx : txs) {
        out.println("<tr class=\"" + (tx.getNonSpecific() ? "non-specific" : "specific") + "\">");
        out.println("<td><a href=\"taxonomyTool.jsp?id=" + tx.getId() + "\">" + tx.getId().substring(0,8) + "</a></td>");
        out.println("<td>" + (tx.getNonSpecific() ? "&#x2713;" : "") + "</td>");
        out.println("<td>" + tx.getScientificName() + "</td>");
        long tc = ((tcount.get(tx.getId()) == null) ? 0L : tcount.get(tx.getId()));
        out.println("<td class=\"td-num-" + tc + "\">" + tc + "</td>");
        int nm = Util.collectionSize(tx.getCommonNames());
        out.println("<td class=\"td-num-" + nm + "\">" + nm + "</td>");
        out.println("</tr>");
    }
    out.println("</tbody></table>");

%>
</body></html>
