<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,
java.io.*,java.util.*, 
java.io.FileInputStream, 
java.io.File, 
java.io.FileNotFoundException, 
org.ecocean.*,org.ecocean.servlet.*,org.ecocean.media.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, 
java.lang.NumberFormatException,
java.util.concurrent.atomic.AtomicInteger"
%>

<%

String context="context0";
context=ServletUtilities.getContext(request);

Shepherd myShepherd=new Shepherd(context);

%>

<jsp:include page="../header.jsp" flush="true" />

<div class="container maincontent">

	<h1 class="intro">Intelligent Agent Review</h1>



    <h2>Collected Data</h2>
    

<%

String jdoqlString="SELECT FROM org.ecocean.Encounter where submitterID == 'wildbookai'";

%>
    <jsp:include page="../encounters/encounterSearchResultsAnalysisEmbed.jsp" flush="true">
    	<jsp:param name="jdoqlString" value="<%=jdoqlString %>" />
    </jsp:include>

<h2>Analysis of Performance</h2>

<ul>
<%

List<String> unique=new ArrayList<String>();
List<String> duplicate=new ArrayList<String>();

myShepherd.beginDBTransaction();
try{
	
    List<Encounter> encs=null;
    String filter="SELECT FROM org.ecocean.Encounter WHERE catalogNumber != null && ( submitterID == \"wildbookai\" ) && state == \"approved\" && individualID != null";  
    Query query=myShepherd.getPM().newQuery(filter);
    Collection c = (Collection) (query.execute());
    encs=new ArrayList<Encounter>(c);
    query.closeAll();

    int numEncs=encs.size();
    %>

  <ul>
    <%
    
    for(int i=0;i<numEncs;i++){
    	
    	//this is our reference encounter
    	Encounter enc=encs.get(i);
    	String locID="";
    	
    	String id=enc.getIndividualID();
    	int year=enc.getYear();
    	int month=enc.getMonth();
    	int day = enc.getDay();
    	String localFilter="SELECT FROM org.ecocean.Encounter WHERE submitterID != \"wildbookai\" && individualID == \""+id+"\" ";  
    	if(enc.getLocationID()!=null)localFilter+=" && locationID == \""+enc.getLocationID() + "\"";
        if(year>0)localFilter+=" && year == "+year;
    	if(month>-1)localFilter+=" && month == "+month;
        if(day>0)localFilter+=" && day == "+day;
    	List<Encounter> duples=null;
    	Query m_query=myShepherd.getPM().newQuery(localFilter);
    	Collection d = (Collection) (m_query.execute());
    	duples=new ArrayList<Encounter>(d);
    	m_query.closeAll();
    	if(duples.size()>0){duplicate.add(enc.getCatalogNumber());}
    	else{unique.add(enc.getCatalogNumber());}
    	
    }
    
    %>
    </ul>
    
    <p>Is this unique effort?
	    <ul>
		    <li><%=duplicate.size() %> duplicate encounters also collected at same or greater level of specificity by a human</li>
		    <li><%=unique.size() %> unique encounters collected only by the agent</li>
		    <li><strong><%=(new Double(100*(double)unique.size()/(unique.size()+duplicate.size()))) %>% unique effort</strong></li>
		</ul>
    </p>
    
    
    
    <%
    
   //let's prep the HashTable original effort

    Hashtable<String,Integer> pieEffortHashtable = new Hashtable<String,Integer>();
 	
 	
 	%>
 	<script>
 	  google.setOnLoadCallback(drawOriginalEffortChart);
      function drawOriginalEffortChart() {
        var data = new google.visualization.DataTable();
        data.addColumn('string', 'type');
        data.addColumn('number', 'number');
        data.addRows([
           ['duplicate',    <%=duplicate.size() %>],
           ['unique',    <%=unique.size() %>],
        ]);

        <%
        
        %>
        var options = {
          width: 450, height: 300,
          title: 'Original vs. Duplicate Effort',
          colors: ['#0000FF','#FF00FF']
        };

        var chart = new google.visualization.PieChart(document.getElementById('duplicatechart_div'));
        chart.draw(data, options);
      }
      </script>
      <div id="duplicatechart_div"></div>
 	
 	<h2>Analysis By YouTube Video</h2>
 	
 	<%
 	
 	AtomicInteger numVideos=new AtomicInteger(0);
 	AtomicInteger  numCommentedVideos=new AtomicInteger(0);
 	AtomicInteger  numCommentedVideosReplies=new AtomicInteger(0);
 	AtomicInteger  numVideosWithID=new AtomicInteger(0);
 	AtomicInteger  numUncuratedVideos=new AtomicInteger(0);
 	AtomicInteger numDatesFound=new AtomicInteger(0);
 	AtomicInteger numLocationIDsFound=new AtomicInteger(0);
 	
	ArrayList<MediaAsset> poorDataVideos=new ArrayList<MediaAsset>();
	ArrayList<MediaAsset> goodDataVideos=new ArrayList<MediaAsset>();
	
 	
 	%>
 	
<p>Num videos processed: <%=numVideos.intValue() %></p>
<p>How many videos have been marked approved/unidentifiable? <%=goodDataVideos.size() %>
	<ul>
		<li>How many videos resulted in IDs? <%=numVideosWithID.intValue() %></li>
		<li>How many videos had detectable dates? <%=numDatesFound %></li>
		<li>How many videos had locationIDs? <%=numLocationIDsFound %></li>
	</ul>
</p>
<p>How many videos were deemed to have no valuable data? <%=poorDataVideos.size() %></p>

<p>How many videos are still uncurated? <%=numUncuratedVideos.intValue() %></p>


<p>Sanity check: <%=numUncuratedVideos.intValue() %> uncurated + <%=poorDataVideos.size() %> worthless + <%=goodDataVideos.size() %>curated = <%=(goodDataVideos.size()+poorDataVideos.size() + numUncuratedVideos.intValue()) %> of <%=numVideos %> total videos possible</p>
 	

 	<%
    

}
catch(Exception e){
	myShepherd.rollbackDBTransaction();
	%>
	<p>Reported error: <%=e.getMessage() %> <%=e.getStackTrace().toString() %></p>
	<%
	e.printStackTrace();
}
finally{
	myShepherd.rollbackDBTransaction();
	myShepherd.closeDBTransaction();

}

%>

</ul>
</div>

<jsp:include page="../footer.jsp" flush="true"/>

