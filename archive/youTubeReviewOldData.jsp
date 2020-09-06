<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,org.ecocean.ai.nmt.azure.*,
java.io.*,org.json.JSONObject,java.util.*, java.io.FileInputStream, 
java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,
org.ecocean.media.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, 
java.util.Iterator, java.lang.NumberFormatException,
org.ecocean.ai.nlp.*,
org.ecocean.ai.utilities.*,
java.text.SimpleDateFormat,
java.text.DateFormat,
org.joda.time.*,
org.joda.time.format.DateTimeFormat,
com.google.api.services.youtube.YouTube.CommentThreads,
com.google.api.services.youtube.model.*,
com.google.api.services.youtube.model.CommentSnippet,
weka.classifiers.Classifier,
weka.core.Instance,
weka.core.Attribute,
weka.core.DenseInstance, org.ecocean.ai.weka.Classify,
weka.core.Instances,
java.util.concurrent.atomic.AtomicInteger,
org.ecocean.identity.IBEISIA"%>

<%!
public String getCurlList(MediaAsset ma, Shepherd myShepherd, String iaURL){
ArrayList<MediaAsset> children=ma.findChildren(myShepherd);
	StringBuffer sb=new StringBuffer();
	for(int i=0;i<children.size();i++){
		MediaAsset child=children.get(i);
			ArrayList<MediaAsset> grandchildren=child.findChildren(myShepherd);
			int size=grandchildren.size();

			if(size>0){
				for(int j=0;j<size;j++){

					sb.append(grandchildren.get(j).getId()+",");
					
				}
			}
			
	}
	
	//curl -s -X POST -H 'Content-Type: application/json' -d '{ "v2": true, "mediaAssetIds": [ 715160] }' https://www.whaleshark.org/ia
	
	String prepend="curl -s -X POST -H 'Content-Type: application/json' -d '{ \"v2\": true, \"mediaAssetIds\": [ ";
	String post = "] }' "+iaURL;
	return prepend+sb.toString()+post;
}
%>


<%

String context="context0";
context=ServletUtilities.getContext(request);

Shepherd myShepherd=new Shepherd(context);
%>

<html>
<head>
<title>YouTube Details</title>
<style>

tr.rowhighlight td, tr.rowhighlight th{
    background-color:#A9A9A9;
}
</style>
</head>


<body>
<table border="1">
<tr><th>Occurrence</th><th>VideoID<th>Current Date</th><th>Potentially Matched Date</th><th>LocationID</th><th>New LocationID</th><th>Title Only</th><th>Description Only</th><th>Comments Only</th><th>Raw Comments</th><th>Relative date</th><th>Language: Stored/Detected</th></tr>
<%

myShepherd.beginDBTransaction();

AtomicInteger numVideos=new AtomicInteger(0);
AtomicInteger  numCommentedVideos=new AtomicInteger(0);
AtomicInteger  numCommentedVideosReplies=new AtomicInteger(0);
AtomicInteger  numVideosWithID=new AtomicInteger(0);
AtomicInteger  numUncuratedVideos=new AtomicInteger(0);
AtomicInteger numDatesFound=new AtomicInteger(0);
AtomicInteger numLocationIDsFound=new AtomicInteger(0);



try{
	
		String filter="SELECT FROM org.ecocean.media.MediaAsset WHERE store instanceof org.ecocean.media.YouTubeAssetStore";
		
		
		Query query = myShepherd.getPM().newQuery(filter);
		Collection c = (Collection) (query.execute());
		ArrayList<MediaAsset> results=new ArrayList<MediaAsset>(c);
		//Long result=(Long)query.execute();
		//int numResults=result.intValue();
		query.closeAll();
		
		//let's make each one considered has actually been run
		ArrayList<MediaAsset> notRunYoutubeAssets=new ArrayList<MediaAsset>();
		
		for(int i=0;i<results.size();i++){
		
			MediaAsset mas=results.get(i);
			if(!ParseDateLocation.hasRunDetection(mas,myShepherd)){
				results.remove(i);
				notRunYoutubeAssets.add(mas);
				i--;
			}				
			
		}
		
		
		//reset counter
		int numResults=results.size();
		%>
%		
%Num YouTube MediaAssets (videos) cataloged: <%=numResults %><br>
		<%
		

		
		ArrayList<MediaAsset> poorDataVideos=new ArrayList<MediaAsset>();
		ArrayList<MediaAsset> goodDataVideos=new ArrayList<MediaAsset>();
		
		String suDirPath=request.getSession().getServletContext().getRealPath("/");
		  
		
		//for(int i=1801;i<2000;i++){
		for(int i=0;i<numResults;i++){
			
			
			
			//YouTubeAsset itself
			MediaAsset ma=results.get(i);
			
			String returnedHTML=ParseDateLocation.annotateChildrenOfYouTubeMediaAssetWithDateLocation(ma,  suDirPath,  myShepherd, context,  numVideosWithID, numVideos, numUncuratedVideos, numCommentedVideos, numCommentedVideosReplies,goodDataVideos,poorDataVideos, true,numDatesFound, numLocationIDsFound);

			%>
			<%=returnedHTML %>
			<%
			
		}
		
	%>
	
	</table>

<h2>About the Agent</h2>
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

<h2>About the People</h2>
<p>How productive is the agent versus a human? TBD</p>
<p>What is the average OP response time to our questions? TBD</p>
<p>Num commented videos: <%=numCommentedVideos.intValue() %></p>
<p>Num commented videos with replies: <%=numCommentedVideosReplies.intValue() %></p>
<p>Percentage responding: <%=(new Double((double)numCommentedVideosReplies.intValue()/numCommentedVideos.intValue()*100)).toString() %>% </p>
	
	<hr></hr>
	<p>Unrun/failed MediaAssets for detection:<br>
	
	

	<ul>
	<%
	int numNotRun=notRunYoutubeAssets.size();
	for(int q=0;q<numNotRun;q++){
		
		MediaAsset nra=notRunYoutubeAssets.get(q);
		%>
		<li><a href="../obrowse.jsp?type=MediaAssetMetadata&id=<%=nra.getId() %>"><%=nra.getId() %></a></li>
		<%
	}
	
	
	%>
	
	
	</ul>
	
	
	
	
	
	</p>
	
	<%
	


}
catch(Exception e){

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





</body>
</html>
