<%@ page contentType="text/html; charset=utf-8" language="java"
     import="org.ecocean.*,

java.io.IOException,

javax.jdo.Query,

java.lang.reflect.Method,
java.lang.reflect.Field,
org.json.JSONArray,
org.json.JSONObject,
java.net.URL,
org.ecocean.servlet.ServletUtilities,
org.ecocean.media.*,
org.ecocean.servlet.importer.ImportTask,
java.util.*,
org.ecocean.genetics.distance.*
              "
%>

<%!
private String scrubUrl(URL u) {
    if (u == null) return (String)null;
    return u.toString().replaceAll("#", "%23");
}

%>

<%!
private String getImportTaskSummary(String taskID, Shepherd myShepherd){
	String summary="(";
	
	ImportTask task=myShepherd.getImportTask(taskID);
	if(task!=null){
		summary+=task.getCreator().getFullName()+";";
		summary+="created: "+task.getCreated().toString()+";";
		summary+="images: "+task.getMediaAssets().size()+"";
	}
	else{summary+="single encounter submissions";}
	
	summary+=")";
	return summary;
}

%>

<%!
//Method for sorting the TreeMap based on values
public static <K, V extends Comparable<V> > Map<K, V>
valueSort(final Map<K, V> map)
{
    // Static Method with return type Map and
    // extending comparator class which compares values
    // associated with two keys
    Comparator<K> valueComparator = new Comparator<K>()
    {
          
        public int compare(K k1, K k2)
        {

            int comp = map.get(k1).compareTo(map.get(k2));
           
            if (comp == 0){return 0;}

            else if(comp>0){return -1;}
            else{return 1;}
        
    	}
    };

    // SortedMap created using the comparator
    Map<K, V> sorted = new TreeMap<K, V>(valueComparator);

    sorted.putAll(map);

    return sorted;
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
      <p><em>Goal: the table below should be empty.</em></p>
      
      <%
      
    //let's track some meta stats
      HashMap<String, Integer> bulkImports = new HashMap<String,Integer>();
      HashMap<String, Integer> bulkImportsPairs = new HashMap<String,Integer>();
      	

      Shepherd myShepherd = new Shepherd(request);
      myShepherd.setAction("sharedAnnotations.jsp");
      myShepherd.beginDBTransaction();
      try{
      
	      if(request.isUserInRole("admin")){
	      %>
	      <p>Select the user to review data for:
	      	<select name="simulateUser" id="simulateUser" onchange="self.location=self.location.origin+self.location.pathname+'?simulateUser='+this.value">
	      		<%
	      		
	      		List<User> permittedUsers=new ArrayList<User>();
	      		User me = myShepherd.getUser(request);
	      		if(me!=null)permittedUsers.add(me);
	      		
	      		if(request.isUserInRole("admin")){
	      			permittedUsers = myShepherd.getNativeUsersWithoutAnonymous();
	      		}
	      		
	      		String selectedUser="";
	      		
	      		if(request.getParameter("simulateUser")!=null){selectedUser=request.getParameter("simulateUser");}
	      		for(User user:permittedUsers){
	      			String selectedString="";
	      			String fullname=user.getUsername();
	      			if(user.getFullName()!=null)fullname = user.getFullName();
	      			//show the current admin user as selected
	      			if(user.getUsername().equals(me.getUsername()) && selectedUser.equals("")){
	      				selectedString="selected=\"selected\"";
	      			}
	      			//sow the selected simulateUser as selected
	      			else if(!selectedUser.equals("") && user.getUsername().equals(selectedUser)){selectedString="selected=\"selected\"";}
	      		%>
	      			<option value="<%=user.getUsername() %>" <%=selectedString %>><%=fullname %></option>
	      		<%
	      		}
	      		%>
	      	
	      	</select>
	      </p>
	      <%
	      }
	      %>

	<%
	
	ArrayList<String> acmIds=new ArrayList<String>();
	
	String username = request.getRemoteUser();
	String usernameFilter=" enc1.submitterID == '"+username+"' && ";
	if(request.isUserInRole("admin") && request.getParameter("showAll")!=null){
		usernameFilter="";
	}
	else if(request.getParameter("simulateUser")!=null){
		if(request.isUserInRole("admin")){
			username=request.getParameter("simulateUser");
			usernameFilter=" enc1.submitterID == '"+username+"' && ";
		}
	}
	
	
		String context = ServletUtilities.getContext(request);
		//String filter2="select distinct acmId from org.ecocean.Annotation where acmId != null && "+usernameFilter+" enc1.annotations.contains(annot2) && enc2.annotations.contains(this) && enc1.catalogNumber != enc2.catalogNumber && id!= annot2.id && acmId==annot2.acmId VARIABLES org.ecocean.Encounter enc1; org.ecocean.Encounter enc2; org.ecocean.Annotation annot2";
	    String filter2="select distinct acmId from org.ecocean.Annotation where acmId != null && "+usernameFilter+" enc1.annotations.contains(annot2) && enc2.annotations.contains(this) && enc1.catalogNumber != enc2.catalogNumber && id!= annot2.id && acmId==annot2.acmId VARIABLES org.ecocean.Encounter enc1; org.ecocean.Encounter enc2; org.ecocean.Annotation annot2";
	    
		System.out.println("JDOQL filter: "+filter2);
		Query q = myShepherd.getPM().newQuery(filter2);
	    
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
	    	//if(ct<=500){
		
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
				        	String indy="none";
				        	if(enc.getIndividual()!=null)indy=" ("+enc.getIndividual().getDisplayName()+")";
				        	String date="unknown";
				        	if(enc.getDate()!=null)date=enc.getDate();
				        	String locationID="";
				        	if(enc.getLocationCode()!=null)locationID=enc.getLocationCode();
				        	String submitterID="";
				        	if(enc.getSubmitterID()!=null){
				        		submitterID=enc.getSubmitterID();
				        		if(myShepherd.getUser(submitterID)!=null && myShepherd.getUser(submitterID).getFullName()!=null)submitterID=myShepherd.getUser(submitterID).getFullName();
				        	}
				        	
				        	list+="<li><a target=\"_blank\" href=\"../encounters/encounter.jsp?number="+enc.getCatalogNumber()+"\">name: "+indy+" | date: "+date+" | locationID: "+locationID+" | user: "+submitterID+" </a></li>";
				        	
				        	//tabulate import task contribution
				        	if(enc.getImportTask(myShepherd)!=null){
								ImportTask tasky=enc.getImportTask(myShepherd);
								if(!bulkImports.containsKey(tasky.getId())){bulkImports.put(tasky.getId(), new Integer(1));}
								else{
									int distribCount=bulkImports.get(tasky.getId()).intValue();
									distribCount++;
									bulkImports.put(tasky.getId(), distribCount);
								}
				        	}
				        	
				        }
				        
				        //let's look at overlapping Encounter pairs too
				        int numEncs=results2.size();
				        for(int i=0;i<(numEncs-1);i++){
				        	for(int j=1;j<(numEncs);j++){
				        		Encounter one = results2.get(i);
				        		Encounter two = results2.get(j);
				        		//if they're not from bulk imports, then we're done here
				        		if(one.getImportTask(myShepherd)==null && two.getImportTask(myShepherd)==null)continue;
				        		String taskID1 = "single submission";
				        		String taskID2 = "single submission";
				        		if(one.getImportTask(myShepherd)!=null)taskID1=one.getImportTask(myShepherd).getId();
				        		if(two.getImportTask(myShepherd)!=null)taskID2=two.getImportTask(myShepherd).getId();
				        	
				        		String key = taskID1+":"+taskID2;
				        		int compare = taskID1.compareTo(taskID2);
				        		if (compare > 0) {
				        		    //one is larger 
				        			key = taskID2+":"+taskID1;
				        		}
				        		
								if(!bulkImportsPairs.containsKey(key)){bulkImportsPairs.put(key, new Integer(1));}
								else{
									int distribCount=bulkImportsPairs.get(key).intValue();
									distribCount++;
									bulkImportsPairs.put(key, distribCount);
								}
				        		
				        		
				        	}
				        }
				        
				        list+="</ul></td>";
				
					    list+="</tr>";
					    if(ct<=500)out.println(list);
						ct++;
			    	}
		    	}
		    	catch(Exception f){
		    		f.printStackTrace();
		    	}
		    //}
	    }
	    %>
	    </table>
	
	    
	    
	    <h2>Individual bulk import contributions to duplicate annotations</h2>
	    <p><em>The following list of bulk imports contributed to duplicate annotations.</em></p>
	    <ul>
	    <%
	 	Map<String, Integer> sorted=valueSort(bulkImports);
	    for(String taskID:sorted.keySet()){
	    	//String taskID=(String)it.next();
	    	ImportTask task=myShepherd.getImportTask(taskID);
	    	%>
	    	<li><a href="../import.jsp?taskId=<%=taskID %>" target="_blank"><%=taskID %></a>: <%=bulkImports.get(taskID) %> from user: <%=task.getCreator().getFullName() %></li>
	    	<%
	    }
	    
	    %>
	    </ul>
	        <h2>Overlapping bulk import pairs</h2>
	    <p><em>The following list of overlapping bulk import pairs contributed to duplicate annotations.</em></p>
	        <table>
				<tr class="shared">
					<th>Task 1</th>
					<th>Task 2</th>
					<th>Number duplicate annotations</th>
		
				</tr>
	    <%
	 	Map<String, Integer> sorted2=valueSort(bulkImportsPairs);
	    for(String key:sorted2.keySet()){
	    	//String taskID=(String)it.next();
	    	String[] result = key.split(":");
	    	String taskID1 = result[0];
	    	String taskID2= result[1];
	    	
	    	if(!taskID1.equals(taskID2)){
	    		
	        	//let's get summary information
	        	String task1Summary=getImportTaskSummary(taskID1,myShepherd);
	        	String task2Summary=getImportTaskSummary(taskID2,myShepherd);
	    		
	    	%>
	    	<tr><td><a href="../import.jsp?taskId=<%=taskID1 %>" target="_blank"><%=task1Summary %></a></td><td><a href="../import.jsp?taskId=<%=taskID2 %>" target="_blank"><%=task2Summary %></a></td><td><%=bulkImportsPairs.get(key) %></td></tr>
	    	<%
	    	}
	    }
	    
	    %>
	    </table>
	            <h2>Bulk imports with internal duplicates</h2>
	    <p><em>The following list of bulk imports contain intra-import duplication of images and annotations.</em></p>
	    <ul>
	    <%
	 	for(String key:sorted2.keySet()){
	    	//String taskID=(String)it.next();
	    	String[] result = key.split(":");
	    	String taskID1 = result[0];
	    	String taskID2= result[1];
	    	if(taskID1.equals(taskID2)){
	        	//let's get summary information
	        	String task1Summary=getImportTaskSummary(taskID1,myShepherd);
	
	    	%>
	    	<li><a href="../import.jsp?taskId=<%=taskID1 %>" target="_blank"><%=task1Summary %></a> <%=bulkImportsPairs.get(key) %> duplicates</li>
	    	<%
	    	}
	    }
	    
	    %>
	    </ul>
	    <%

}
catch(Exception e){
	e.printStackTrace();
}
finally{
    myShepherd.rollbackDBTransaction();
    myShepherd.closeDBTransaction();
}

%>
</div>
<jsp:include page="../footer.jsp" flush="true"/>
