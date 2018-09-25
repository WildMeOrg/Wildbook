<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,org.ecocean.ai.nmt.google.*,
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
com.google.api.services.youtube.model.CommentSnippet"%>

<%!
private String translateIfNotEnglish(String text){
	String shortForm=text;
	try{
	if(shortForm.length()>500){shortForm=shortForm.substring(0,499);}
		String langCode=DetectTranslate.detectLanguage(shortForm);
		if((!langCode.toLowerCase().equals("en"))&&(!langCode.toLowerCase().equals("und"))){
			System.out.println("Translating: "+text);
			text=DetectTranslate.translateToEnglish(text).replaceAll(",", " ").replaceAll("\n", " ").replaceAll("'", "").replaceAll("\"", "").replaceAll("′","").replaceAll("’","").toLowerCase();
			System.out.println("Translated to: "+text);
		}
	}
	catch(Exception e){}
	return text;
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

</head>


<body>
<table border="1">
<tr><th>Occurrence</th><th>Current Date</th><th>Potentially Matched Date</th><th>LocationID</th><th>New LocationID</th><th>Title Only</th><th>Description Only</th><th>Comments Only</th><th>Relative date</th></tr>
<%

myShepherd.beginDBTransaction();

int numVideos=0;
int numCommentedVideos=0;
int numCommentedVideosReplies=0;

try{
	
		String filter="SELECT FROM org.ecocean.media.MediaAsset WHERE store instanceof org.ecocean.media.YouTubeAssetStore";
		
		
		Query query = myShepherd.getPM().newQuery(filter);
		Collection c = (Collection) (query.execute());
		ArrayList<MediaAsset> results=new ArrayList<MediaAsset>(c);
		//Long result=(Long)query.execute();
		//int numResults=result.intValue();
		query.closeAll();
		int numResults=results.size();
		
		%>
%		
%Num YouTube MediaAssets (videos) cataloged: <%=numResults %><br>
		<%
		

		
		ArrayList<MediaAsset> poorDataVideos=new ArrayList<MediaAsset>();
		ArrayList<MediaAsset> goodDataVideos=new ArrayList<MediaAsset>();
		
		for(int i=0;i<numResults;i++){
			
			//YouTubeAsset itself
			MediaAsset ma=results.get(i);
			
			boolean hasWildMeComment=false;
			boolean hasWildMeCommentReplies=false;
			String relativeDate = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
		    
			
            //JSONObject data=md.getData();
            if ((ma.getMetadata() != null)) {
              numVideos++;
              MediaAssetMetadata md = ma.getMetadata();	
              if (md.getData() != null) {
            	
            	String videoID=ma.getMetadata().getData().getJSONObject("detailed").optString("id");
				String videoTitle="[unknown]";
				String videoTitleShort=videoTitle;
				String videoComments="";
				if(videoTitle.length()>1000){videoTitleShort=videoTitle.substring(0,1000);}
				if(md.getData().optJSONObject("basic") != null){
					videoTitle=md.getData().getJSONObject("basic").optString("title").replaceAll(",", " ").replaceAll("\n", " ").replaceAll("'", "").replaceAll("\"", "").replaceAll("′","").replaceAll("’","").toLowerCase();
					
					
				}
				String videoDescription="[no description]";
				String videoDescriptionShort=videoDescription;
				if(videoDescription.length()>1000){videoDescriptionShort=videoDescription.substring(0,1000);}
				String videoTags="[no tags]";
				String videoTagsShort=videoTags;
				if(videoTags.length()>1000){videoTagsShort=videoTags.substring(0,1000);}
				if(md.getData().getJSONObject("detailed")!=null){
					videoDescription=md.getData().getJSONObject("detailed").optString("description").replaceAll(",", " ").replaceAll("\n", " ").replaceAll("'", "").replaceAll("\"", "").replaceAll("’","").replaceAll("′","").toLowerCase();
					videoTags=md.getData().getJSONObject("detailed").getJSONArray("tags").toString().replaceAll(",", " ").replaceAll("\n", " ").replaceAll("'", "").replaceAll("\"", "").replaceAll("′","").replaceAll("’","").toLowerCase();	
					
				}
    			String qFilter="SELECT FROM org.ecocean.Encounter WHERE (occurrenceRemarks.indexOf('"+videoID+"') != -1)";
    			Query newQ=myShepherd.getPM().newQuery(qFilter);
    			Collection d=(Collection)newQ.execute();
    			ArrayList<Encounter> encresults=new ArrayList<Encounter>(d);
    			newQ.closeAll();
    			int numEncs=encresults.size();
    			
    			Occurrence occur=null;
    			
    			Properties props=ShepherdProperties.getProperties("submitActionClass.properties", "",context);
                
    			
    			if(numEncs>0){
    				
    				//check for Occurrence
    				String occurID="";
    				Encounter enc=encresults.get(0);
    				String currentDate="";
    				String currentLocationID="";
    				if(enc.getDate()!=null)currentDate=enc.getDate().replaceAll("Unknown", "");	
    				if(enc.getLocationID()!=null)currentLocationID=enc.getLocationID().replaceAll("None", "");	
    				
    				if(enc.getOccurrenceID()!=null){
    					occur=myShepherd.getOccurrence(enc.getOccurrenceID());
    					
    					
    					
    					List<CommentThread> comments=YouTube.getVideoCommentsList(occur, context);
    					if((comments==null)||(comments.size()==0))videoComments="";
    					else{
    						boolean isWildMeComment=false;
    				        int numComments=comments.size();
    				        videoComments+="<ul>\n";
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
    				            String style="";
  				              	if(isWildMeComment){
  				              		style="color: green;";
  				              		hasWildMeComment=true;
  				              	}
    				            videoComments+="<li style=\""+style+"\">"+authorName+":"+translateIfNotEnglish(topLevelComment.getSnippet().getTextDisplay());
    				            
    				            if(ct.getReplies()!=null){
    				            	 CommentThreadReplies ctr=ct.getReplies();
    				             
	    				            List<Comment> replies=ctr.getComments();
	    				            int numReplies=0;
	    				            if(replies!=null)numReplies=replies.size();
	    				            if(numReplies>0){
	    				            	if(isWildMeComment)hasWildMeCommentReplies=true;
	    				            	videoComments+="<ul>\n";
		    				            for(int g=0;g<numReplies;g++) {
		    				            
		    				              Comment reply=replies.get(g);
		    				              
		    				              videoComments+="<li>"+translateIfNotEnglish(reply.getSnippet().getTextDisplay())+"</li>";
		      				            
		    				             }
		    				            videoComments+="</ul>\n";
	    				            }
    				             }

    				            videoComments+="</li>\n";
    				            
    				        }
    						videoComments+="</ul>\n";
    						
    					}
    					
    					
    					occurID=occur.getOccurrenceID();

    					String tempRelativeDate=YouTube.getVideoPublishedAt(occur, context);
    					if((tempRelativeDate!=null)&&(tempRelativeDate.indexOf("T")!=-1)){
    						tempRelativeDate=tempRelativeDate.substring(0,tempRelativeDate.indexOf("T"));
    					}
    					if((tempRelativeDate!=null)&&(!tempRelativeDate.equals(""))){
    						
    						DateTimeFormatter parser2 = DateTimeFormat.forPattern("yyyy-MM-dd");
    						DateTime time = parser2.parseDateTime(tempRelativeDate);
    						relativeDate=time.toString(parser2);
    						
    					}
    				}
    				videoTitle=translateIfNotEnglish(videoTitle);
    				videoTags=translateIfNotEnglish(videoTags);
    				videoDescription=translateIfNotEnglish(videoDescription);	
    				videoComments=translateIfNotEnglish(videoComments);
    				
    				StringBuffer sb=new StringBuffer("");
    				
    				sb.append(videoTitle+" "+videoDescription+" "+videoTags+" "+videoComments);
    				

    				
    				String newDetectedDate="";
    				try{
    					newDetectedDate=SUTime.parseDateStringForBestDate(request, sb.toString(), relativeDate).replaceAll("null","");
    				}
    				catch(Exception e){}
    				//get video date
    				
    				
    				String newLocationID="";
    	              String lowercaseRemarks=sb.toString().toLowerCase();
    	              try{
    	                Enumeration m_enum = props.propertyNames();
    	                while (m_enum.hasMoreElements()) {
    	                  String aLocationSnippet = ((String) m_enum.nextElement()).trim();
    	                  //System.out.println("     Looking for: "+aLocationSnippet);
    	                  if (lowercaseRemarks.indexOf(aLocationSnippet) != -1) {
    	                	  newLocationID = props.getProperty(aLocationSnippet);
    	                    //System.out.println(".....Building an idea of location: "+location);
    	                  }
    	                }

    	              }
    	              catch(Exception e){
    	                e.printStackTrace();
    	              }
    	              if(newLocationID==null)newLocationID="";
    				%>
    				
    				<tr><td><%=occurID %></td><td><%=currentDate %></td><td><%=newDetectedDate %></td><td><%=currentLocationID %></td><td><%=newLocationID %></td><td><%=videoTitle %></td><td><%=videoDescription %></td><td><%=videoComments %></td><td><%=relativeDate %></td></tr>
    				<%
    				
    			}

    			
    			
              
              
              }
              
              if(hasWildMeComment)numCommentedVideos++;
              if(hasWildMeCommentReplies)numCommentedVideosReplies++;
              
            }

			
			

			
		
		}
		
	%>
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

</table>

<p>Num videos: <%=numVideos %></p>
<p>Num commented videos: <%=numCommentedVideos %></p>
<p>Num commented videos with replies: <%=numCommentedVideosReplies %></p>
<p>Percentage responding: <%=(new Double(numCommentedVideosReplies/numCommentedVideos)).toString() %> </p>

</body>
</html>
