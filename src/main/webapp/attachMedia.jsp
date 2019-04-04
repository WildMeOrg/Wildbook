<style>

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

.td-int {
    text-align: right;
    padding-right: 30px !important;
}

.td-num-0, .dull {
    color: #AAA;
}

</style>

<%@ page contentType="text/html; charset=utf-8" 
		language="java"
        import="org.ecocean.servlet.ServletUtilities,org.ecocean.*,
org.ecocean.media.Feature,
java.util.Collection,
java.util.Arrays,
java.util.ArrayList,
java.util.List,
javax.jdo.*,
java.util.Properties" %>

<%!

private static String submittersTd(Occurrence occ) {
    if (occ == null) return "<td class=\"dull\">X</td>";
    if (Util.collectionIsEmptyOrNull(occ.getSubmitters())) return "<td class=\"dull\">-</td>";
    return "<td title=\"number of submitters: " + occ.getSubmitters().size() + "\">" + occ.getSubmitters().get(0).getDisplayName() + "</td>";
}
%>

<%

String context = ServletUtilities.getContext(request);

  //setup our Properties object to hold all properties
  //String langCode = "en";
  String langCode=ServletUtilities.getLanguageCode(request);
  


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


<script src="javascript/tablesorter/jquery.tablesorter.js"></script>
<link rel="stylesheet" href="javascript/tablesorter/themes/blue/style.css" type="text/css" media="print, projection, screen" />

<script>
$(document).ready(function() {
    $('#occs').tablesorter();
});
</script>


<div class="container maincontent">
</div>

<%
if (AccessControl.isAnonymous(request)) {
    out.println("<h1>Please <a href=\"login.jsp\">login</a></h1>");

} else {
    Shepherd myShepherd = new Shepherd(context);
    myShepherd.beginDBTransaction();
    User user = AccessControl.getUser(request, myShepherd);
    boolean admin = request.isUserInRole("admin");
    //String filter = "SELECT FROM org.ecocean.Occurrence WHERE source != null";
    String filter = "SELECT FROM org.ecocean.Occurrence WHERE source.matches('SpotterConserveIO:.*')";
    if (!admin) filter += " && submitters.contains(u) && u.uuid == '" + user.getUUID() + "'";
    Query query = myShepherd.getPM().newQuery(filter);
    query.setOrdering("dateTimeCreated");
    Collection coll = (Collection) query.execute();
    if (coll.size() < 1) {
        out.println("<h2>None available</h2>");
    } else {
%>
<table class="tablesorter" id="occs"><thead><tr>
<%
        List<String> heads = new ArrayList<String>(Arrays.asList(new String[]{"Type", "Trip #", "Occ ID", "Date/Time", "# Encs", "Photos", "Placeholder"}));
        if (admin) heads.add("User(s)");
        for (String h : heads) {
            out.println("<th>" + h + "</th>");
        }
%>
</tr></thead>
<tbody>
<%
        for (Object o : coll) {
            String row = "<tr>";
            Occurrence occ = (Occurrence) o;
            String[] src = null;
            if (occ.getSource() != null) src = occ.getSource().split(":");
            row += "<td>" + (((src != null) && (src.length > 2)) ? src[1] : "??") + "</td>";
            row += "<td class=\"td-int\">" + (((src != null) && (src.length > 2)) ? src[2] : "-") + "</td>";
            row += "<td>" + occ.getOccurrenceID() + "</td>";
            row += "<td>" + occ.getDateTimeCreated() + "</td>";
            row += "<td class=\"td-int td-num-" + occ.getNumberEncounters() + "\">" + occ.getNumberEncounters() + "</td>";
            int numPhotos = 0;
            int numPlaceholders = 0;
            if (occ.getNumberEncounters() > 0) {
                for (Encounter enc : occ.getEncounters()) {
                    if (enc.getAnnotations() == null) continue;
                    for (Annotation ann : enc.getAnnotations()) {
                        if (Util.collectionIsEmptyOrNull(ann.getFeatures())) continue;
                        Feature ft = ann.getFeatures().get(0);
                        if (ft.getMediaAsset() != null) {   //we do *not* check feature type in this case.  should we?
                            numPhotos++;
                        } else if (ft.isType("org.ecocean.MediaAssetPlaceholder")) {
                            numPlaceholders++;
                        }
                    }
                }
            }
            row += "<td class=\"td-int td-num-" + numPhotos + "\">" + numPhotos + "</td>";
            row += "<td class=\"td-int td-num-" + numPlaceholders + "\">" + numPlaceholders + "</td>";
            if (admin) row += submittersTd(occ);
            out.println(row + "</tr>");
        }
        out.println("</tbody></table>");
    }
    query.closeAll();
    myShepherd.rollbackDBTransaction();
}
%>
          <jsp:include page="footer.jsp" flush="true"/>
