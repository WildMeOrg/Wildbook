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

</style>

<%@ page contentType="text/html; charset=utf-8" 
		language="java"
        import="org.ecocean.servlet.ServletUtilities,org.ecocean.*,
org.ecocean.media.Feature,
org.json.JSONObject,
java.util.Collection,
java.util.Arrays,
java.util.ArrayList,
java.util.List,
java.util.HashMap,
java.util.Map,
javax.jdo.*,
java.util.Properties" %>

<%!

private static String submittersTd(Occurrence occ) {
    if (occ == null) return "<td class=\"dull\">X</td>";
    if (Util.collectionIsEmptyOrNull(occ.getSubmitters())) return "<td class=\"dull\">-</td>";
    return "<td title=\"number of submitters: " + occ.getSubmitters().size() + "\">" + occ.getSubmitters().get(0).getDisplayName() + "</td>";
}

private static Map<String,String> getTripInfo(Occurrence occ) {
    Map<String,String> typeLabel = new HashMap<String,String>();
    typeLabel.put("ci", "Channel Is");
    typeLabel.put("wa", "WhaleAlert");

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
    //$('#occs tbody td:nth-child(3)').on('click', function(ev) {
    $('#occs tr').on('click', function(ev) {
        var occId = $(ev.currentTarget).find(':nth-child(3)').text();
        window.location.href = 'attachMedia.jsp?id=' + occId;
    });
});
</script>


<div class="container maincontent">
</div>

<%
String id = request.getParameter("id");

if (AccessControl.isAnonymous(request)) {
    out.println("<h1>Please <a href=\"login.jsp\">login</a></h1>");

} else if (Util.requestParameterSet(id)) {
    Shepherd myShepherd = new Shepherd(context);
    myShepherd.beginDBTransaction();
    Occurrence occ = myShepherd.getOccurrence(id);
    if (occ == null) {  //TODO also some security check that user can access this occurrence!!
        out.println("<h2>Invalid ID " + id + "</h2>");
        return;
    }
    Map<String,String> tripInfo = getTripInfo(occ);

%>
<div style="padding: 0 20px;">
<h2><%=occ.getOccurrenceID()%></h2>
<p>
<%=tripInfo.get("typeLabel")%>
<b>Trip ID = <%=tripInfo.get("id")%></b>
</p>
<p>
<%
String survId = occ.getCorrespondingSurveyID();
if (survId == null) {
    out.println("<i>No survey associated</i>");
} else {
    out.println("Survey: <a href=\"surveys/survey.jsp?surveyID.jsp=" + survId + "\"><b>" + survId.substring(0,8) + "</b></a>");
}
%>
</p>
<p><%=occ.getComments()%></p>
</div>
<%
    

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
            Map<String,String> tripInfo = getTripInfo(occ);
            row += "<td>" + tripInfo.get("typeLabel") + "</td>";
            row += "<td class=\"td-int\">" + tripInfo.get("id") + "</td>";
            row += "<td>" + occ.getOccurrenceID() + "</td>";
            row += "<td>" + occ.getDateTimeCreated() + "</td>";
            row += "<td class=\"td-int td-num-" + occ.getNumberEncounters() + "\">" + occ.getNumberEncounters() + "</td>";
            int numPhotos = 0;
            int numPlaceholders = 0;
            List<String> phlist = new ArrayList<String>();
            if (occ.getNumberEncounters() > 0) {
                for (Encounter enc : occ.getEncounters()) {
                    if (enc.getAnnotations() == null) continue;
                    for (Annotation ann : enc.getAnnotations()) {
                        if (Util.collectionIsEmptyOrNull(ann.getFeatures())) continue;
                        Feature ft = ann.getFeatures().get(0);
                        if (ft.getMediaAsset() != null) {   //we do *not* check feature type in this case.  should we?
                            numPhotos++;
                        } else if (ft.isType("org.ecocean.MediaAssetPlaceholder")) {
                            String phs = phString(ft.getParameters());
                            if (!phlist.contains(phs)) phlist.add(phs);
                            numPlaceholders++;
                        }
                    }
                }
            }

            String phnote = "";
            if (phlist.size() > 0) {
                phnote = String.join(" | ", phlist);
                //phnote = "<span class=\"hover-note\" title=\"" + phnote + "\">\u2731</span>";
                phnote = " title=\"" + phnote + "\" ";
            }
            row += "<td class=\"td-int td-num-" + numPhotos + "\">" + numPhotos + "</td>";
            row += "<td " + phnote + " class=\"td-int td-num-" + numPlaceholders + "\">" + numPlaceholders + "</td>";
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
