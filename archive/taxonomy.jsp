<%@ page contentType="text/html; charset=utf-8" language="java"
     import="org.ecocean.*,
java.util.List,
java.util.Collection,
java.util.ArrayList,
org.ecocean.servlet.ServletUtilities,
javax.jdo.*,
org.ecocean.media.*
              "
%>
<%

String context = ServletUtilities.getContext(request);
Shepherd myShepherd = new Shepherd(context);

String id = request.getParameter("id");
String editId = request.getParameter("edit-id");

if (editId != null) {
    myShepherd.beginDBTransaction();
    Taxonomy tax = myShepherd.getTaxonomyById(editId);
    if (tax == null) {
        out.println("<p>invalid id=" + editId + "</p>");
        return;
    }
    tax.setNonSpecific("on".equals(request.getParameter("nonSpecific")));
    String cnAll = request.getParameter("commonNames");
    if (cnAll != null) for (String cn : cnAll.split("\\s*[\\r\\n]+\\s*")) {
        if (Util.stringExists(cn)) tax.addCommonName(cn);
    }
    String sciName = request.getParameter("scientificName");
    if (Util.stringExists(sciName)) tax.setScientificName(sciName);
    try {
        int tsn = Integer.parseInt(request.getParameter("itisTsn"));
        if (tsn > 0) tax.setItisTsn(tsn);
    } catch (Exception ex) {}

    myShepherd.commitDBTransaction();
    out.println("<p>Saved id=<a href=\"taxonomy.jsp?id=" + editId + "\">" + editId + "</a> <i>" + tax.getScientificName() + "</i></p>");
    out.println("<a href=\"taxonomy.jsp\">List All</a>");
    return;
}

%>
<html><head><title>Taxonomy</title>
<script src="../tools/jquery/js/jquery.min.js"></script>
<script>
function itisSearch(text) {
    $('#itis-search-value').val(text);
    document.getElementById('itis-form').submit();
}

function gotItis(data) {
    var val = document.getElementById('commonNames');
    if (!data || !data.commonNameList || !data.commonNameList.commonNames || !data.commonNameList.commonNames.length) alert('lookup failed');
    for (var i = 0 ; i < data.commonNameList.commonNames.length ; i++) {
        val.value += data.commonNameList.commonNames[i].commonName + '\n';
    }
    console.log(data);
}

function itisLookup(tsn) {
    if (tsn < 1) return alert('enter ITIS value above first');
    $('#itis-lookup-button').hide();
    var url = 'https://www.itis.gov/ITISWebService/jsonservice/getFullRecordFromTSN?jsonp=gotItis&tsn=' + tsn;
    var s = document.createElement('script');
    s.src = url;
    document.body.appendChild(s);
}

</script>

<style>
body {
    font-family: sans, arial;
}

div.cols {
    column-count: 5;
}

.tax-item a {
    text-decoration: none;
}
.non-specific-false:hover {
    background-color: #FF6;
}
.non-specific-true:hover {
    background-color: #FFC;
}


.tax-item {
    font-size: 0.75em;
    padding: 1px 5px;
}
.non-specific-false a {
    color: #222;
}
.non-specific-true a {
    color: #888;
}

#commonNames {
    width: 40em;
    height: 10em;
}

.tax-item a i {
    font-size: 0.9em;
    background-color: #CEF;
}
</style>

</head><body>

<%
if (id == null) {
    Query q = myShepherd.getPM().newQuery("SELECT FROM org.ecocean.Taxonomy");
    q.setOrdering("nonSpecific, scientificName");
    Collection c = (Collection) (q.execute());
    List<Taxonomy> taxs = new ArrayList<Taxonomy>(c);
    q.closeAll();
    if (taxs.size() < 1) {
        out.println("<p>no Taxonomy</p>");
    } else {
        out.println("<div class=\"cols\">");
        for (Taxonomy tx : taxs) {
            out.println("<div class=\"tax-item non-specific-" + tx.getNonSpecific() + "\"><a title=\"" + tx.getId() + "\" href=\"taxonomy.jsp?id=" + tx.getId() + "\">" + tx.getScientificName() + ((tx.getItisTsn() == null) ? "" : " <i>" + tx.getItisTsn() + "</i>") + "</a>");
            if (!Util.collectionIsEmptyOrNull(tx.getCommonNames())) out.println(" - " + Util.collectionSize(tx.getCommonNames()));
            out.println("</div>");
        }
        out.println("</ul></div>");
    }

} else if (id != null) {
    Taxonomy tax = myShepherd.getTaxonomyById(id);
    if (tax == null) {
        out.println("<p>unknown Taxonomy id=" + id + "</p>");
        return;
    }
%>

<h1><%=tax.getId()%></h1>

<form id="tax-form">
<p>
<b>Scientific Name</b><br />
<input value="<%=tax.getScientificName()%>" id="scientificName" />
</p>
<input name="edit-id" value="<%=tax.getId()%>" type="hidden" />

<p>
<b>ITIS Number</b> <input type="button" onClick="return itisSearch(document.getElementById('scientificName').value);" value="search on sci name" /><br />
<input value="<%=(tax.getItisTsn() == null) ? "" : tax.getItisTsn() %>" name="itisTsn" id="itisTsn" />
</p>

<p>
<label for="nonSpecific"><b>Non-specific</b></label>
<input type="checkbox" <%=(tax.getNonSpecific() ? "checked" : "")%> name="nonSpecific" id="nonSpecific" />
</p>

<p>
<b>Common name(s):</b> <input id="itis-lookup-button" type="button" onClick="return itisLookup(document.getElementById('itisTsn').value);" value="lookup via ITIS.gov" /><br />
<textarea name="commonNames" id="commonNames"><%
if (!Util.collectionIsEmptyOrNull(tax.getCommonNames())) {
    for (String cn : tax.getCommonNames()) {
        out.println(cn);
    }
}
%></textarea>

<p>
<input type="submit" value="Save" />
</p>

</form>

<a href="taxonomy.jsp">List All</a>

<form id="itis-form" target="_new" method="post"
    action="https://www.itis.gov/servlet/SingleRpt/SingleRpt">
<input type="hidden" name="search_value" id="itis-search-value" />
<input type="hidden" name="search_topic" value="all" />
<input type="hidden" name="search_span" value="containing" />
<input type="hidden" name="search_kingdom" value="Animal" />
<input type="hidden" name="categories" value="All" />
<input type="hidden" name="source" value="html" />
<input type="hidden" name="search_credRating" value="All" />
</form>
<%
}


%>

</body></html>
