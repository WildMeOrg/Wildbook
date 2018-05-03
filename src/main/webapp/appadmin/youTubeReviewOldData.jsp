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
com.google.api.services.youtube.model.CommentSnippet,
weka.classifiers.Classifier,
weka.core.Instance,
weka.core.Attribute,
weka.core.DenseInstance, org.ecocean.ai.weka.Classify,
weka.core.Instances"%>

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

<%!
public static Double classify(weka.core.Instance instance, String fullPathToClassifierFile) {
    Double result=-1.0;

    try {
      // load classifier from file
      Classifier cls_co = (Classifier) weka.core.SerializationHelper.read(fullPathToClassifierFile);

      
      return new Double(cls_co.classifyInstance(instance));
    }
    catch(Exception e) {
      e.printStackTrace();
    }
    return result;   
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
<tr><th>Occurrence</th><th>VideoID<th>Current Date</th><th>Potentially Matched Date</th><th>LocationID</th><th>New LocationID</th><th>Title Only</th><th>Description Only</th><th>Comments Only</th><th>Raw Comments</th><th>Relative date</th></tr>
<%

myShepherd.beginDBTransaction();

int numVideos=0;
int numCommentedVideos=0;
int numCommentedVideosReplies=0;
int numVideosWithID=0;
int numUncuratedVideos=0;


//weka locationID
ArrayList<Attribute> attributeList = new ArrayList<Attribute>(2);
ArrayList<Attribute> attributeList2 = new ArrayList<Attribute>(2);
Attribute desc = new Attribute("description", true);
Attribute merged = new Attribute("merged", true);
List<String> classVal2 = myShepherd.getAllLocationIDs();
classVal2.remove(0);
ArrayList<String> classVal = new ArrayList<String>();
classVal.add("good");
classVal.add("poor");
attributeList2.add(desc);
attributeList2.add(new Attribute("@@class@@",classVal2));
attributeList.add(merged);
attributeList.add(new Attribute("@@class@@",classVal));
String locIDpath="/data/whaleshark_data_dirs/shepherd_data_dir/wekaModels/whaleSharkLocationIDClassifier.model";
String path="/data/whaleshark_data_dirs/shepherd_data_dir/wekaModels/youtubeRandomForest.model";




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
		
		for(int i=1701;i<1800;i++){
		//for(int i=0;i<numResults;i++){
			
			boolean videoHasID=false;
			
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
				String videoCommentsClean="";
				String locIDWords="";
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
    			
    			//check if any have been approved
    			boolean videoIsCurated=false;
    			for(int y=0;y<numEncs;y++){
    				Encounter enc=encresults.get(y);
    				if((enc.getState()!=null)&&((enc.getState().equals("approved"))||(enc.getState().equals("unidentifiable")))){
    					if(!goodDataVideos.contains(ma))goodDataVideos.add(ma);
    					videoIsCurated=true;
    				}

    				if((enc.getIndividualID()!=null)&&(!enc.getIndividualID().equals("")))videoHasID=true;
    			}
    			if(!videoIsCurated)numUncuratedVideos++;
    			
    			Occurrence occur=null;
    			
    			LinkedProperties props=(LinkedProperties)ShepherdProperties.getProperties("submitActionClass.properties", "",context);
                
    			
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
    					if((comments==null)||(comments.size()==0)){
    						videoComments="";
    						videoCommentsClean="";
    					}
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
  				              		style="color: green;font-weight: bold;";
  				              		hasWildMeComment=true;
  				              	}
    				            videoComments+="<li style=\""+style+"\">"+authorName+": "+translateIfNotEnglish(topLevelComment.getSnippet().getTextDisplay());
    				            
    				            videoCommentsClean+=translateIfNotEnglish(topLevelComment.getSnippet().getTextDisplay()).toLowerCase()+" ";
    				            
    				            
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
		    				              videoCommentsClean+=translateIfNotEnglish(reply.getSnippet().getTextDisplay()).toLowerCase()+" ";
			      				            
		    				             }
		    				            videoComments+="</ul>\n";
	    				            }
    				             }

    				            videoComments+="</li>\n";
    				            style="";
    				            
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
    				
    				StringBuffer sbOriginalText=new StringBuffer("");
    				sbOriginalText.append(videoTitle+" "+videoDescription+" "+videoTags+" "+videoCommentsClean);
    				
    				
    				videoTitle=translateIfNotEnglish(videoTitle);
    				videoTags=translateIfNotEnglish(videoTags);
    				videoDescription=translateIfNotEnglish(videoDescription);	
    				//videoComments=translateIfNotEnglish(videoComments);
    				
    				StringBuffer sb=new StringBuffer("");
    				
    				sb.append(videoTitle+" "+videoDescription+" "+videoTags+" "+videoCommentsClean);
    				

    				
    				String newDetectedDate="";
    				try{
    					newDetectedDate=SUTime.parseDateStringForBestDate(request, sb.toString(), relativeDate).replaceAll("null","");
    				}
    				catch(Exception e){}
    				//get video date
    				
    				
    				String newLocationID="";
    	              String lowercaseRemarks=sb.toString().toLowerCase();
    	              try{
    	            	  
    	            	  
    	                Iterator m_enum = props.orderedKeys().iterator();
    	                while (m_enum.hasNext()) {
    	                  String aLocationSnippet = ((String) m_enum.next()).replaceFirst("\\s++$", "");
    	                  //System.out.println("     Looking for: "+aLocationSnippet);
    	                  if (lowercaseRemarks.indexOf(aLocationSnippet) != -1) {
    	                	  newLocationID = props.getProperty(aLocationSnippet);
    	                	  locIDWords+=" "+ aLocationSnippet;
    	                    //System.out.println(".....Building an idea of location: "+location);
    	                  }
    	                }
    	                /*
    	            	  Instances data2 = new Instances("TestInstances",attributeList2,2);
    	            	  data2.setClassIndex(data2.numAttributes()-1);
    	            	  Instance pos2 = new DenseInstance(data2.numAttributes());
    	            	  pos2.setValue(desc, sbOriginalText.toString().replaceAll("[^A-Za-z0-9 ]", "").replace("\n", "").trim());
    	            	  data2.add(pos2);
    	            	  pos2.setDataset(data2);
    	            	  
    	            	  newLocationID=pos2.classAttribute().value(classify(pos2, locIDpath).intValue());
    	                */

    	              }
    	              catch(Exception e){
    	                e.printStackTrace();
    	              }
    	              if(newLocationID==null)newLocationID="";
    	              
    	              //predict if this is a good video
    	              Instances data = new Instances("TestInstances",attributeList,2);
    	           		data.setClassIndex(data.numAttributes()-1);
    	            	  Instance pos = new DenseInstance(data.numAttributes());
    	            	  pos.setValue(merged, lowercaseRemarks.replaceAll("whale shark", "whaleshark"));
    	            	  data.add(pos);
    	            	  pos.setDataset(data);
    	              
    	            	  Double prediction = classify(pos,path);
    	            	  String rowClass="";
    	            	  if(prediction.intValue()==1)rowClass="class=\"rowhighlight\"";
    	            	  
    				%>
    				
    				<tr <%=rowClass %>><td><a target="_blank" href="https://www.whaleshark.org/occurrence.jsp?number=<%=occurID %>"><%=occurID %></a></td><td><a target="_blank" href="https://www.youtube.com/watch?v=<%=videoID %>"><%=videoID %></a></td><td><%=currentDate %></td><td><%=newDetectedDate %></td><td><%=currentLocationID %></td><td><%=newLocationID %></td><td><%=videoTitle %></td><td><%=videoDescription %></td><td><%=videoComments %></td><td><%=videoCommentsClean %><br><br>LocID Words: <%=locIDWords %></br></br></td><td><%=relativeDate %></td></tr>
    				<%
    				
    			}
    			else{
    				if(!poorDataVideos.contains(ma)){
    					poorDataVideos.add(ma);
    					numUncuratedVideos--;
    					
    					}
    			}
    			

    			
    			
              
              
              }
              else{if(!poorDataVideos.contains(ma))poorDataVideos.add(ma);}
              
              if(hasWildMeComment)numCommentedVideos++;
              if(hasWildMeCommentReplies)numCommentedVideosReplies++;
              
            }
            else{
            	if(!poorDataVideos.contains(ma))poorDataVideos.add(ma);
            }

			
			

			
			if(videoHasID)numVideosWithID++;
		}
		
	%>
	
	</table>

<h2>About the Agent</h2>
<p>Num videos processed: <%=numVideos %></p>
<p>How many videos have been marked approved/unidentifiable? <%=goodDataVideos.size() %></p>
<p>How many videos were deemed to have no valuable data? <%=poorDataVideos.size() %></p>
<p>How many videos resulted in IDs? <%=numVideosWithID %></p>
<p>How many videos are still uncurated? <%=numUncuratedVideos %></p>

<p>Sanity check: <%=numUncuratedVideos %> uncurated + <%=poorDataVideos.size() %> worthless + <%=goodDataVideos.size() %>curated = <%=(goodDataVideos.size()+poorDataVideos.size() + numUncuratedVideos) %> of <%=numVideos %> total videos possible</p>

<h2>About the People</h2>
<p>How productive is the agent versus a human? TBD</p>
<p>What is the average OP response time to our questions? TBD</p>
<p>Num commented videos: <%=numCommentedVideos %></p>
<p>Num commented videos with replies: <%=numCommentedVideosReplies %></p>
<p>Percentage responding: <%=(new Double((double)numCommentedVideosReplies/numCommentedVideos*100)).toString() %>% </p>
	
	
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
