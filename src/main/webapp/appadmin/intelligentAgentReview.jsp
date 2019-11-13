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
java.text.SimpleDateFormat,
java.text.DateFormat,
org.joda.time.*,
org.joda.time.format.DateTimeFormat,
com.google.api.services.youtube.YouTube.CommentThreads,
com.google.api.services.youtube.model.*,
com.google.api.services.youtube.model.CommentSnippet,
org.ecocean.identity.IBEISIA,
java.util.concurrent.atomic.AtomicInteger"
%>

<%!
private static boolean hasRunDetection(MediaAsset ma, Shepherd myShepherd){
	List<MediaAsset> children=YouTubeAssetStore.findFrames(ma, myShepherd);
	if(children!=null){
		int numChildren=children.size();
		for(int i=0;i<numChildren;i++){
			MediaAsset child=children.get(i);
			if((child.getDetectionStatus()!=null)&&(child.getDetectionStatus().equals(IBEISIA.STATUS_COMPLETE))){
				return true;
			}
		}
	}
	return false;
}

%>

<%!
public static void annotateChildrenOfYouTubeMediaAssetWithDateLocation(MediaAsset ma, HttpServletRequest request, Shepherd myShepherd, String context, AtomicInteger numVideosWithID,AtomicInteger numVideos, AtomicInteger numUncuratedVideos, AtomicInteger numCommentedVideos,AtomicInteger numCommentedVideosReplies,ArrayList<MediaAsset> goodDataVideos,ArrayList<MediaAsset> poorDataVideos, boolean persistDifferences, AtomicInteger numDatesFound, AtomicInteger numLocationIDsFound){
	
	boolean videoHasID=false;
	boolean videoIsCurated=false;
	boolean hasWildMeComment=false;
	boolean hasWildMeCommentReplies=false;
	//String relativeDate = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
    
	//int parentId=ma.getId();
	
		
	
	
	//video has metadata for analysis?
    if ((ma.getMetadata() != null)) {
      numVideos.incrementAndGet();
      MediaAssetMetadata md = ma.getMetadata();	
      
      //video metadata is not null, so proceed
      if (md.getData() != null) {
    	
    	//setup our metadata fields  
    	String videoID=ma.getMetadata().getData().getJSONObject("detailed").optString("id"); 
		//String videoComments="";
		//String videoCommentsClean="";
	
		

		//Let's get the Encounter objects related to this video
		//JDOQL query
		
		//String numFilter="SELECT FROM org.ecocean.Encounter WHERE (occurrenceRemarks.indexOf('"+videoID+"') != -1)";
		//String numFilter="SELECT FROM org.ecocean.Encounter WHERE annotations.contains(annot) && annot.mediaAsset.parentId == ma2.id && ma2.parentId == "+parentId+" VARIABLES org.ecocean.Annotation annot;org.ecocean.media.MediaAsset ma2";
		//String numFilter="SELECT FROM org.ecocean.media.MediaAsset WHERE parentId == "+parentId + " && enc.annotations.contains(annot) && annot.mediaAsset == this VARIABLES";
		//Query numQ=myShepherd.getPM().newQuery(numFilter);
		
		Query numQ = myShepherd.getPM().newQuery("javax.jdo.query.SQL","select * from \"ENCOUNTER\" where \"OCCURRENCEREMARKS\" like \'%"+videoID+"%\';");
		numQ.setClass(Encounter.class);
		Collection numd=(Collection)numQ.execute();
		ArrayList<Encounter> encresults=new ArrayList<Encounter>(numd);
		numQ.closeAll();
		int numEncs=encresults.size();
		


		

		

		
		//let's iterate our matching Encounters
		//first, check if any have been approved (curated) and count them
		
		
		
		/*
		for(int y=0;y<numEncs;y++){
			Encounter enc=encresults.get(y);
			if((enc.getState()!=null)&&((enc.getState().equals("approved"))||(enc.getState().equals("unidentifiable")))){
				if(!goodDataVideos.contains(ma))goodDataVideos.add(ma);
				videoIsCurated=true;
			}

			if((enc.getIndividualID()!=null)&&(!enc.getIndividualID().equals("")))videoHasID=true;
			
			
			//break out if our conditions have been met.
			if(videoIsCurated && videoHasID)break;
			
			
		}
		if(!videoIsCurated)numUncuratedVideos.incrementAndGet();
		*/
		
		
		
		
		//Occurrence occur=null;		

		//if we have matching encounters, then the video is either uncurated, or it has been determined to have useful data (curated)
		if(numEncs>0){
			
			//numVideos.incrementAndGet();
			
			//JDOQL query
			
			//String idFilter="SELECT FROM org.ecocean.Encounter WHERE individualID != null && (occurrenceRemarks.indexOf('"+videoID+"') != -1)";
			
			Query idQ = myShepherd.getPM().newQuery("javax.jdo.query.SQL","select * from \"ENCOUNTER\" where \"OCCURRENCEREMARKS\" like \'%"+videoID+"%\' and \"INDIVIDUAL\" is not null;");
			idQ.setClass(Encounter.class);
			
			//Query idQ=myShepherd.getPM().newQuery(idFilter);
			Collection idd=(Collection)idQ.execute();
			ArrayList<Encounter> idencresults=new ArrayList<Encounter>(idd);
			idQ.closeAll();
			int numIDEncs=idencresults.size();
			if(numIDEncs>0){
				videoHasID=true;
				numVideosWithID.incrementAndGet();
			}
			
			
			
			//JDOQL query
			String stateFilter="SELECT FROM org.ecocean.Encounter WHERE (state == \"approved\" || state == \"unidentifiable\" ) && (occurrenceRemarks.indexOf('"+videoID+"') != -1)";
			
			//select * from "ENCOUNTER" where "OCCURRENCEREMARKS" like '%l3BcXf-LrMk%' and ("STATE" = 'approved' or "STATE" = 'unidentifiable');
			Query stateQ = myShepherd.getPM().newQuery("javax.jdo.query.SQL","select * from \"ENCOUNTER\" where \"OCCURRENCEREMARKS\" like \'%"+videoID+"%\' and (\"STATE\" = \'approved\' or \"STATE\" = \'unidentifiable\');");
			stateQ.setClass(Encounter.class);
			
			//Query stateQ=myShepherd.getPM().newQuery(stateFilter);
			Collection stated=(Collection)stateQ.execute();
			ArrayList<Encounter> stateencresults=new ArrayList<Encounter>(stated);
			stateQ.closeAll();
			int numStateEncs=stateencresults.size();
			if(numStateEncs>0){
				videoIsCurated=true;
				if(!goodDataVideos.contains(ma))goodDataVideos.add(ma);
			}
			else{
				numUncuratedVideos.incrementAndGet();
			}
			
			
			
			//check for Occurrence
			//String occurID="";
					
			//grab the first Encounter for analysis		
			Encounter enc=encresults.get(0);
					
			//get the current values for date and location ID		
			String currentDate="";
			String currentLocationID="";
			if(enc.getDate()!=null)currentDate=enc.getDate().replaceAll("Unknown", "");	
			if(enc.getLocationID()!=null)currentLocationID=enc.getLocationID().replaceAll("None", "");	
			
			//our encounters should all have an Occurrence, one per video
			if(enc.getOccurrenceID()!=null){
				Occurrence occur=myShepherd.getOccurrence(enc.getOccurrenceID());
				
				
				//let's get all our YouTube video metadata and comments
				
				List<CommentThread> comments=YouTube.getVideoCommentsList(occur, context);
				if((comments==null)||(comments.size()==0)){
					//videoComments="";
					//videoCommentsClean="";
				}
				else{
					boolean isWildMeComment=false;
			        int numComments=comments.size();
					for(int f=0;f<numComments;f++) {
			            CommentThread ct=comments.get(f);

			            CommentThreadSnippet cts=ct.getSnippet();
			            
			            Comment topLevelComment=cts.getTopLevelComment();
			            CommentSnippet commentSnippet=topLevelComment.getSnippet();
			            String authorName="";
			            if((commentSnippet!=null)&&(commentSnippet.getAuthorDisplayName()!=null)){
			            	authorName=commentSnippet.getAuthorDisplayName();
			            	if(authorName.equals("Wild Me"))isWildMeComment=true;
			            }

			              	if(isWildMeComment){
			              		hasWildMeComment=true;
			              	}

			            
			            if(ct.getReplies()!=null){
			            	 CommentThreadReplies ctr=ct.getReplies();
			             
				            List<Comment> replies=ctr.getComments();
				            int numReplies=0;
				            if(replies!=null)numReplies=replies.size();
				            if(numReplies>0){
				            	if(isWildMeComment)hasWildMeCommentReplies=true;
				            }
			             }

			            
			        }

					
				}
				
				
				
				//occurID=occur.getOccurrenceID();


				
				
			}
			


			if(goodDataVideos.contains(ma)&& !currentDate.equals("")){numDatesFound.incrementAndGet();}
			

              
              if(goodDataVideos.contains(ma) && !currentLocationID.equals("")){numLocationIDsFound.incrementAndGet();}
              

  
			
		}
		//this video had no encounters, probably been curated as having no value
		else{
			if(!poorDataVideos.contains(ma)){
				poorDataVideos.add(ma);
				//numUncuratedVideos.decrementAndGet();
				
				}
		}

      
      }
      //video metadata is null, not much we can do here
      else{if(!poorDataVideos.contains(ma))poorDataVideos.add(ma);}
      
      if(hasWildMeComment)numCommentedVideos.incrementAndGet();
      if(hasWildMeCommentReplies)numCommentedVideosReplies.incrementAndGet();
      
    }
    //video had no metadata, not much we can do here
    else{
    	if(!poorDataVideos.contains(ma))poorDataVideos.add(ma);
    }

	
	//if(videoHasID)numVideosWithID.incrementAndGet();
	
	
}

%>



<%

String context="context0";
context=ServletUtilities.getContext(request);

Shepherd myShepherd=new Shepherd(context);
myShepherd.setAction("intelligentAgentReview.jsp");

%>

<jsp:include page="../header.jsp" flush="true" />

<div class="container maincontent">

	<h1 class="intro"><img src="../images/wildbookIntelligentAgent.gif" width="200px" height="150px"> Intelligent Agent Data Review</h1>





<h2>Analysis of Performance</h2>

<ul>
<%

List<String> unique=new ArrayList<String>();
List<String> duplicate=new ArrayList<String>();

myShepherd.beginDBTransaction();
try{
	
    List<Encounter> encs=null;
    String filter="SELECT FROM org.ecocean.Encounter WHERE catalogNumber != null && ( submitterID == \"wildbookai\" ) && state == \"approved\" && individual.individualID != null";  
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
    	String localFilter="SELECT FROM org.ecocean.Encounter WHERE submitterID != \"wildbookai\" && individual.individualID == \""+id+"\" ";  
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
	
	String filter2="SELECT FROM org.ecocean.media.MediaAsset WHERE store instanceof org.ecocean.media.YouTubeAssetStore";
	
	
	Query query2 = myShepherd.getPM().newQuery(filter2);
	Collection c2 = (Collection) (query2.execute());
	ArrayList<MediaAsset> results=new ArrayList<MediaAsset>(c2);
	query.closeAll();
	
	//let's make each one considered has actually been run
	ArrayList<MediaAsset> notRunYoutubeAssets=new ArrayList<MediaAsset>();
	
	for(int i=0;i<results.size();i++){
	
		MediaAsset mas=results.get(i);
		if(!hasRunDetection(mas,myShepherd)){
			results.remove(i);
			notRunYoutubeAssets.add(mas);
			i--;
		}				
		
	}
	
	
	//reset counter
	int numResults=results.size();
	%>
	Num YouTube MediaAssets (videos) cataloged: <%=numResults %><br>
	<%
	//for(int i=1801;i<2000;i++){
	for(int i=0;i<numResults;i++){
		
		
		
		//YouTubeAsset itself
		MediaAsset ma=results.get(i);
		
		annotateChildrenOfYouTubeMediaAssetWithDateLocation(ma,  request,  myShepherd, context,  numVideosWithID, numVideos, numUncuratedVideos, numCommentedVideos, numCommentedVideosReplies,goodDataVideos,poorDataVideos, true,numDatesFound, numLocationIDsFound);

		
	}
	
	
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


<p>Sanity check: <%=numUncuratedVideos.intValue() %> uncurated + <%=poorDataVideos.size() %> worthless + <%=goodDataVideos.size() %> curated = <%=(goodDataVideos.size()+poorDataVideos.size() + numUncuratedVideos.intValue()) %> of <%=numVideos %> total videos possible</p>
 	
 	<h2>OP Response to Agent Comments</h2>
 	
 	<p>Num commented videos: <%=numCommentedVideos.intValue() %></p>
	<p>Num commented videos with replies: <%=numCommentedVideosReplies.intValue() %></p>
	<p>Percentage responding: <%=(new Double((double)numCommentedVideosReplies.intValue()/numCommentedVideos.intValue()*100)).toString() %>% </p>
	
 	
    <h2>Collected Data</h2>
    

<%

String jdoqlString="SELECT FROM org.ecocean.Encounter where submitterID == 'wildbookai'";

%>
    <jsp:include page="../encounters/encounterSearchResultsAnalysisEmbed.jsp" flush="true">
    	<jsp:param name="jdoqlString" value="<%=jdoqlString %>" />
    </jsp:include>


<h2>Export</h2>

<p><a href="//<%=CommonConfiguration.getURLLocation(request)%>/ExportWekaPredictorARFF">Export Weka predictor arff file to the server file system (slow process)</a></p>

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

