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
java.net.URL,
org.ecocean.servlet.ServletUtilities,
org.ecocean.media.*
              "
%>

<%!
private String scrubUrl(URL u) {
    if (u == null) return (String)null;
    return u.toString().replaceAll("#", "%23");
}



%>

<script src="../tools/jquery/js/jquery.min.js"></script>
<script src="../javascript/annot.js"></script>
<style>

.img-margin {
    float: right;
    display: inline-block;
    oveflow-hidden;
}



#img-wrapper {
    position: relative;

    overflow: hidden;
}
.featurebox {
    position: absolute;
    width: 100%;
    height: 100%;
    top: 0;
    left: 0;
    outline: dashed 2px rgba(255,255,0,0.8);
    box-shadow: 0 0 0 2px rgba(0,0,0,0.6);
}
.mediaasset {
	position: relative;
}
.mediaasset img {
	position: absolute;
	max-width: 350px;
}



table {
  border-collapse: collapse;
}

table, th, td {
  border: 1px solid black;
}

tr.shared th {
	font-weight: bold;
	padding: 7px;
}

tr.shared td{
	padding: 3px;
}

</style>

<script>
var features = {};

var zoomedId = false;
function toggleZoom(featId) {
console.log('featId=%o', featId);
    var imgEl = $('img')[0];
    if (zoomedId == featId) {
        zoomedId = false;
        unzoomFeature(imgEl);
        $('.featurebox').show();
        return;
    }
console.log('feature=%o', features[featId]);
    if (!features || !features[featId]) return;
    $('.featurebox').hide();
    zoomToFeature(imgEl, { parameters: features[featId] });
    zoomedId = featId;
}
function addFeature(id, bbox) {
    features[id] = bbox;
}

function drawFeatures() {
    for (id in features) {
        drawFeature(id);
    }
}
function drawFeature(id) {
    if (!(id in features)) return;
    var bbox = features[id];
    var el = $('#img-wrapper-'+id);
    var img = $('#'+id);
    var f = $('<div title="' + id + '" id="feature-' + id + '" class="featurebox" />');
    el.append(f);
    if (!bbox || !bbox.width) return;  //trivial annot, so leave it as whole image
    var scale = img.height / img.naturalHeight;
    f.css('width', (bbox.width * scale) + 'px');
    f.css('height', (bbox.height * scale) + 'px');
    f.css('left', (bbox.x * scale) + 'px');
    f.css('top', (bbox.y * scale) + 'px');
    if (bbox.theta) f.css('transform', 'rotate(' +  bbox.theta + 'rad)');
}
</script>

<jsp:include page="../header.jsp" flush="true" />

<div class="container maincontent">
     

      <h1>Annotations with Multiple Individual IDs</h1>
      <p>These are data errors, indicating where one annotation has been assigned to one or more Encounters of at least two different individuals.</p>
      <p>Goal: the table below should be empty.</p>
<table>
<tr class="shared">
	<th>Annotation</th>
	<th>#</th>
	<th>Annotation ACM ID</th>
	<th>Individual 1</th>
	<th>Individual 2</th>
	<th>Merge Individuals?</th>
</tr>
<%
Shepherd myShepherd = new Shepherd(request);
myShepherd.setAction("sharedAnnotations.jsp");
myShepherd.beginDBTransaction();
try{
	String sql = "SELECT \"ID\",\"ACMID\" FROM \"ANNOTATION\" WHERE \"ACMID\" IN (SELECT acmId FROM (SELECT \"ACMID\" AS acmId, COUNT(DISTINCT(\"INDIVIDUALID_OID\")) AS ct FROM \"ANNOTATION\" JOIN \"ENCOUNTER_ANNOTATIONS\" ON (\"ANNOTATION\".\"ID\" = \"ENCOUNTER_ANNOTATIONS\".\"ID_EID\") JOIN \"MARKEDINDIVIDUAL_ENCOUNTERS\" ON (\"ENCOUNTER_ANNOTATIONS\".\"CATALOGNUMBER_OID\" = \"MARKEDINDIVIDUAL_ENCOUNTERS\".\"CATALOGNUMBER_EID\") WHERE \"ACMID\" IS NOT NULL GROUP BY acmId) AS counts WHERE ct > 1) ORDER BY \"ACMID\", \"ID\";";
    String context = ServletUtilities.getContext(request);

    Query q = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
    List results = (List)q.execute();
    
    Iterator it = results.iterator();
    String prev = "";

    int ct = 1;
    ArrayList<String> acmIds=new ArrayList<String>();
    
    while (it.hasNext()) {
        Object[] row = (Object[]) it.next();
        List<String> lrow = new ArrayList<String>();
        String id = (String)row[0];
        String acmId = (String)row[1];

        if(!acmIds.contains(acmId)){
        	acmIds.add(acmId);
	        String filter="SELECT FROM org.ecocean.MarkedIndividual where encounters.contains(enc) && enc.annotations.contains(annot) && annot.acmId =='"+acmId+"' VARIABLES org.ecocean.Encounter enc;org.ecocean.Annotation annot";
	        Query q2 = myShepherd.getPM().newQuery(filter);
	        List results2 = (List)q2.execute();
	        
	        Iterator it2 = results2.iterator();
	        MarkedIndividual prevy=null;
	        String list = "";
	        while (it2.hasNext()) {
	        	MarkedIndividual indy=(MarkedIndividual)it2.next();
		        if(prevy!=null){
		        	Annotation annot=myShepherd.getAnnotation(id);
		        	MediaAsset ma=annot.getMediaAsset();
		        	Feature f=null;
		        	String fid="";

		        	if(annot.getFeatures()!=null && annot.getFeatures().size()>0){
		        		f=annot.getFeatures().get(0);
		        		fid=f.getId();
		        	}
	        		list += "<tr class=\"shared\">";
	        		list+="<td><div class=\"img-margin\"><div id=\"img-wrapper-"+fid+"\"><img id=\""+f.getId()+"\" width=\"100px\" onLoad=\"drawFeatures();\" title=\".webURL() " + ma.webURL() + "\" src=\"" + scrubUrl(ma.webURL()) + "\" /></div></div></td>";
	        		list+="<td>"+ct+"</td>";
		        	list+="<td><a target=\"_new\" href=\"../obrowse.jsp?type=Annotation&acmid=" + acmId + "\">" + id + "</a></td>";
		        	list+="<td><a target=\"_blank\" href=\"../individuals.jsp?id=PREVIOUSID\">PREVIOUS_DISPLAYNAME</a></td>";
		        	list+="<td><a target=\"_blank\" href=\"../individuals.jsp?id=CURRENTID\">CURRENT_DISPLAYNAME</a></td>";
		        	list+="<td><a target=\"_blank\" href=\"../merge.jsp?individualA=CURRENTID&individualB=PREVIOUSID\">link</a>";
		        	//if(f!=null)list+= "<script>addFeature('" + fid + "', " + f.getParametersAsString() + ");</script>";
		        	list+="</td>";     
		        	list+="</tr>";
		        	list=list.replaceAll("PREVIOUSID",prevy.getIndividualID());
		        	list=list.replaceAll("CURRENTID",indy.getIndividualID());
		        	list=list.replaceAll("CURRENT_DISPLAYNAME",indy.getDisplayName());
		        	list=list.replaceAll("PREVIOUS_DISPLAYNAME",prevy.getDisplayName());
	        		out.println(list);
	        		ct++;
		        }
		        prevy=indy;
		        
	        }
	        q2.closeAll();
    	}
    }
    q.closeAll();
}
catch(Exception e){
	e.printStackTrace();
}
finally{
    myShepherd.rollbackDBTransaction();
    myShepherd.closeDBTransaction();
}

%>
</table>
</div>

<jsp:include page="../footer.jsp" flush="true"/>
