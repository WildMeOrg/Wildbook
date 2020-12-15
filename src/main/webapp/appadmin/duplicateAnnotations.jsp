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
     

      <h1>Annotations Duplicated in Two or More Encounters</h1>
      <p>These are data duplications, indicating where one annotation has been assigned to one or more Encounters.</p>
      <p>Goal: the table below should be empty.</p>

<%

ArrayList<String> acmIds=new ArrayList<String>();

Shepherd myShepherd = new Shepherd(request);
myShepherd.setAction("sharedAnnotations.jsp");
myShepherd.beginDBTransaction();
try{
	String context = ServletUtilities.getContext(request);

    Query q = myShepherd.getPM().newQuery("select distinct acmId from org.ecocean.Annotation where acmId != null && enc1.annotations.contains(annot2) && enc2.annotations.contains(this) && enc1.catalogNumber != enc2.catalogNumber && id!= annot2.id && acmId==annot2.acmId VARIABLES org.ecocean.Encounter enc1; org.ecocean.Encounter enc2; org.ecocean.Annotation annot2");
    Collection c = (Collection)q.execute();
    ArrayList<String> results=new ArrayList<String>(c);
    q.closeAll();
    String prev = "";

    int ct = 1;

    %>
        <p>Duplicated Annotations: <%=results.size() %></p>
        <p>Only the first 500 are shown.</p>
    <table>
<tr class="shared">
	<th>Annotation</th>
	<th>#</th>
	<th>Annotation ACM ID</th>
	<th>Encounters</th>
</tr>

    <%
    
    for(String acmId:results) {
    	if(ct<=500){
	
	    	List<Annotation> annots=myShepherd.getAnnotationsWithACMId(acmId);
	    	Annotation annot=annots.get(0);
	    	try{
		    	if(annot.getMediaAsset()!=null){
		    		acmIds.add(annot.getAcmId());
		    		MediaAsset ma=annot.getMediaAsset();
			    	Feature f=null;
			    	String fid="";
			
			    	if(annot.getFeatures()!=null && annot.getFeatures().size()>0){
			    		f=annot.getFeatures().get(0);
			    		fid=f.getId();
			    	}
			    	String list = "<tr class=\"shared\">";
					list+="<td><div class=\"img-margin\"><div id=\"img-wrapper-"+fid+"\"><img id=\""+fid+"\" width=\"100px\" onLoad=\"drawFeatures();\" title=\".webURL() " + ma.webURL() + "\" src=\"" + scrubUrl(ma.webURL()) + "\" /></div></div></td>";
					list+="<td>"+ct+"</td>";
					list+="<td><a target=\"_new\" href=\"../obrowse.jsp?type=Annotation&acmid=" + annot.getAcmId() + "\">" + annot.getAcmId() + "</a></td>";
			    	
					String filter="SELECT FROM org.ecocean.Encounter where annotations.contains(annot) && annot.acmId =='"+annot.getAcmId()+"' VARIABLES org.ecocean.Annotation annot";
			        Query q2 = myShepherd.getPM().newQuery(filter);
			        Collection c2 = (Collection)q2.execute();
			        List<Encounter> results2 = new ArrayList<Encounter>(c2);
			        q2.closeAll();
			        
			        list+="<td><ul>";
			        for(Encounter enc:results2){
			        	String indy="";
			        	if(enc.getIndividual()!=null)indy=" ("+enc.getIndividual().getDisplayName()+")";
			        	list+="<li><a target=\"_blank\" href=\"../encounters/encounter.jsp?number="+enc.getCatalogNumber()+"\">"+enc.getCatalogNumber()+indy+"</a></li>";
			        }
			        list+="</ul></td>";
			
				    list+="</tr>";
				    out.println(list);
					ct++;
		    	}
	    	}
	    	catch(Exception f){
	    		f.printStackTrace();
	    	}
	    }
    }

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
