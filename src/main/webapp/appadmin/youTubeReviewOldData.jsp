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
weka.core.Instances,
java.util.concurrent.atomic.AtomicInteger"%>

<%!
private static String translateIfNotEnglish(String text){
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

<%!

public static String annotateChildrenOfYouTubeMediaAssetWithDateLocation(MediaAsset ma, HttpServletRequest request, Shepherd myShepherd, String context, AtomicInteger numVideosWithID,AtomicInteger numVideos, AtomicInteger numUncuratedVideos, AtomicInteger numCommentedVideos,AtomicInteger numCommentedVideosReplies,ArrayList<MediaAsset> goodDataVideos,ArrayList<MediaAsset> poorDataVideos, boolean persistDifferences, AtomicInteger numDatesFound, AtomicInteger numLocationIDsFound){

	//if we're going to persist changes, ensure the Shepherd object is ready
	if(persistDifferences && !myShepherd.getPM().currentTransaction().isActive()){
		myShepherd.beginDBTransaction();
	}
	
	//the return string of HTML content
	String resultsHTML="";
	
	//weka predictor preparation answering the question: does this video description suggest a real world whale shark sighting?
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

	
	boolean videoHasID=false;
	boolean hasWildMeComment=false;
	boolean hasWildMeCommentReplies=false;
	String relativeDate = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
    
	//video has metadata for analysis?
    if ((ma.getMetadata() != null)) {
      numVideos.incrementAndGet();
      MediaAssetMetadata md = ma.getMetadata();	
      
      //video metadata is not null, so proceed
      if (md.getData() != null) {
    	
    	//setup our metadata fields  
    	String videoID=ma.getMetadata().getData().getJSONObject("detailed").optString("id");
		String videoTitle="[unknown]";
		String videoTitleShort=videoTitle;
		String videoComments="";
		String videoCommentsClean="";
		String locIDWords="";
		String videoDescription="[no description]";
		String videoTags="[no tags]";
		
		//start capturing metadata about the YouTube video
		
		//video title
		if(videoTitle.length()>1000){videoTitleShort=videoTitle.substring(0,1000);}
		if(md.getData().optJSONObject("basic") != null){
			videoTitle=md.getData().getJSONObject("basic").optString("title").replaceAll(",", " ").replaceAll("\n", " ").replaceAll("'", "").replaceAll("\"", "").replaceAll("′","").replaceAll("’","").toLowerCase();
		}
		
		//video description
		String videoDescriptionShort=videoDescription;
		if(videoDescription.length()>1000){videoDescriptionShort=videoDescription.substring(0,1000);}
		
		//video tags
		String videoTagsShort=videoTags;
		if(videoTags.length()>1000){videoTagsShort=videoTags.substring(0,1000);}
		if(md.getData().getJSONObject("detailed")!=null){
			videoDescription=md.getData().getJSONObject("detailed").optString("description").replaceAll(",", " ").replaceAll("\n", " ").replaceAll("'", "").replaceAll("\"", "").replaceAll("’","").replaceAll("′","").toLowerCase();
			videoTags=md.getData().getJSONObject("detailed").getJSONArray("tags").toString().replaceAll(",", " ").replaceAll("\n", " ").replaceAll("'", "").replaceAll("\"", "").replaceAll("′","").replaceAll("’","").toLowerCase();		
		}
		
		//Let's get the Encounter objects related to this video
		//JDOQL query
		String qFilter="SELECT FROM org.ecocean.Encounter WHERE (occurrenceRemarks.indexOf('"+videoID+"') != -1)";
		Query newQ=myShepherd.getPM().newQuery(qFilter);
		Collection d=(Collection)newQ.execute();
		ArrayList<Encounter> encresults=new ArrayList<Encounter>(d);
		newQ.closeAll();
		int numEncs=encresults.size();
		
		//let's iterate our matching Encounters
		//first, check if any have been approved (curated) and count them
		boolean videoIsCurated=false;
		for(int y=0;y<numEncs;y++){
			Encounter enc=encresults.get(y);
			if((enc.getState()!=null)&&((enc.getState().equals("approved"))||(enc.getState().equals("unidentifiable")))){
				if(!goodDataVideos.contains(ma))goodDataVideos.add(ma);
				videoIsCurated=true;
			}

			if((enc.getIndividualID()!=null)&&(!enc.getIndividualID().equals("")))videoHasID=true;
		}
		if(!videoIsCurated)numUncuratedVideos.incrementAndGet();
		
		
		Occurrence occur=null;		
		LinkedProperties props=(LinkedProperties)ShepherdProperties.getProperties("submitActionClass.properties", "",context);

		String chosenStyleDate="";
		String chosenStyleLocation="";
		
		//if we have matching encounters, then the video is either uncurated, or it has been determined to have useful data (curated)
		if(numEncs>0){
			
			//check for Occurrence
			String occurID="";
					
			//grab the first Encounter for analysis		
			Encounter enc=encresults.get(0);
					
			//get the current values for date and location ID		
			String currentDate="";
			String currentLocationID="";
			if(enc.getDate()!=null)currentDate=enc.getDate().replaceAll("Unknown", "");	
			if(enc.getLocationID()!=null)currentLocationID=enc.getLocationID().replaceAll("None", "");	
			
			//our encounters should all have an Occurrence, one per video
			if(enc.getOccurrenceID()!=null){
				occur=myShepherd.getOccurrence(enc.getOccurrenceID());
				
				
				//let's get all our YouTube video metadata and comments
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

				//prep the YouTube video date for SUTimee analysis
				String tempRelativeDate=null;
				try{		
					tempRelativeDate=YouTube.getVideoPublishedAt(occur, context);
				}
				catch(Exception e){}
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
			
			//let's do some translation to English for standardization
			videoTitle=translateIfNotEnglish(videoTitle);
			videoTags=translateIfNotEnglish(videoTags);
			videoDescription=translateIfNotEnglish(videoDescription);	
			//videoComments=translateIfNotEnglish(videoComments);
			
			StringBuffer sb=new StringBuffer("");
			
			sb.append(videoTitle+" "+videoDescription+" "+videoTags+" "+videoCommentsClean);
			

			//get video date with SUTime
			String newDetectedDate="";
			try{
				newDetectedDate=SUTime.parseDateStringForBestDate(request, sb.toString(), relativeDate).replaceAll("null","");
			}
			catch(Exception e){}
			if(!newDetectedDate.equals("")){numDatesFound.incrementAndGet();}
			
			//determine new LocationID, including comments
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
              if(!newLocationID.equals("")){numLocationIDsFound.incrementAndGet();}
              
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
            	  
			  
			  //here is where we would put logic to update encounters if appropriate
			  if(persistDifferences){
				boolean madeAChange=false;
				
				for(int y=0;y<numEncs;y++){
					Encounter thisEnc=encresults.get(y);	
					chosenStyleDate+="year: "+thisEnc.getYear()+";millis:"+thisEnc.getDateInMilliseconds()+";";
					
				  	//SET LOCATION ID
				  	//first, if we even found a location ID in comments, lets' consider it.
				  	//otherwise, there's no point
				  	
				  	if((newLocationID!=null)&&(!newLocationID.trim().equals(""))){
					  
						//next, if we have a new locationID and one was not set before, then this is an easy win
					  	if((thisEnc.getLocationID()==null)||(thisEnc.getLocationID().trim().equals(""))){
					  		thisEnc.setLocationID(newLocationID);
					  		madeAChange=true;
					  	}
					  	else if(!thisEnc.getLocationID().trim().equals(newLocationID.trim())){
						  	//ok, the location IDs are different, now what?
							
						  	//maybe the newLocationID further specifies the older locationID, that would be a win		
						    if(newLocationID.trim().startsWith(thisEnc.getLocationID().trim())){
						    	thisEnc.setLocationID(newLocationID.trim());
						    	madeAChange=true;
						    }
						  	//if the Encounter is not yet approved, then we can reset it as well since it's uncurated and may have been incorrectly detected with older values
						    else if((enc.getState()!=null)&&(enc.getState().equals("unapproved"))){
						    	thisEnc.setLocationID(newLocationID.trim());
						    	madeAChange=true;
						    }
						    else{
						    	//we have to respect a human's previous judgment on setting this value
						    }
								  
					  	}
					  	else{
						  	//nothing needed, they're identical so no change is needed
					  	}
						
				  	}
						
					
						
						if(madeAChange)chosenStyleLocation="font-style: italic;";
						chosenStyleDate+="madeit: here;";
						//let's check and fix date
						if((newDetectedDate!=null)&&(!newDetectedDate.trim().equals(""))){
							
							//well we have something to analyze at least
							//DateTimeFormatter parser3 = DateTimeFormat.forPattern("yyyy-MM-dd");
							DateTimeFormatter parser3 = ISODateTimeFormat.dateParser();
							DateTime dt=parser3.parseDateTime(newDetectedDate);
							
							//check for the easy case
							if((thisEnc.getDateInMilliseconds()==null)||(thisEnc.getYear()<=0)){
								thisEnc.setDateInMilliseconds(dt.getMillis());
								chosenStyleDate+="font-style: italic; color: red;";
								madeAChange=true;
							}
							//if it's unapproved/uncurated, trust the newer value
							else if(thisEnc.getState().equals("auto_sourced")){
								thisEnc.setDateInMilliseconds(dt.getMillis());
								chosenStyleDate+="font-style: italic; color: green;";
								madeAChange=true;
							}
							
							
							
						}
						
						//now persist
						if(madeAChange){
							//myShepherd.commitDBTransaction();
							//myShepherd.beginDBTransaction();
						}
						
						
				  	}
			  	
			  
			 
			  
			  }
			  
			  resultsHTML="<tr "+rowClass+"><td><a target=\"_blank\" href=\"https://www.whaleshark.org/occurrence.jsp?number="+occurID+"\">"+occurID+"</a></td><td><a target=\"_blank\" href=\"https://www.youtube.com/watch?v="+videoID+"\">"+videoID+"</a></td><td>"+currentDate+"</td><td><p style=\""+chosenStyleDate+"\">"+newDetectedDate+"</p></td><td>"+currentLocationID+"</td><td><p style=\""+chosenStyleLocation+"\">"+newLocationID+"</p></td><td>"+videoTitle+"</td><td>"+videoDescription+"</td><td>"+videoComments+"</td><td>"+videoCommentsClean+"<br><br>LocID Words: "+locIDWords+"</br></br></td><td>"+relativeDate+"</td></tr>";
				
			  
			  
			
		}
		//this video had no encounters, probably been curated as having no value
		else{
			if(!poorDataVideos.contains(ma)){
				poorDataVideos.add(ma);
				numUncuratedVideos.decrementAndGet();
				
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

	
	if(videoHasID)numVideosWithID.incrementAndGet();
	
	return resultsHTML;
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
		int numResults=results.size();
		
		%>
%		
%Num YouTube MediaAssets (videos) cataloged: <%=numResults %><br>
		<%
		

		
		ArrayList<MediaAsset> poorDataVideos=new ArrayList<MediaAsset>();
		ArrayList<MediaAsset> goodDataVideos=new ArrayList<MediaAsset>();
		
		for(int i=1801;i<2000;i++){
		//for(int i=0;i<numResults;i++){
			
			
			
			//YouTubeAsset itself
			MediaAsset ma=results.get(i);
			
			String returnedHTML=annotateChildrenOfYouTubeMediaAssetWithDateLocation(ma,  request,  myShepherd, context,  numVideosWithID, numVideos, numUncuratedVideos, numCommentedVideos, numCommentedVideosReplies,goodDataVideos,poorDataVideos, true,numDatesFound, numLocationIDsFound);

			%>
			<%=returnedHTML %>
			<%
			
		}
		
	%>
	
	</table>

<h2>About the Agent</h2>
<p>Num videos processed: <%=numVideos.intValue() %></p>
<p>How many videos have been marked approved/unidentifiable? <%=goodDataVideos.size() %></p>
<p>How many videos were deemed to have no valuable data? <%=poorDataVideos.size() %></p>
<p>How many videos resulted in IDs? <%=numVideosWithID.intValue() %></p>
<p>How many videos are still uncurated? <%=numUncuratedVideos.intValue() %></p>
<p>How many videos had detectable dates? <%=numDatesFound %></p>
<p>How many videos had locationIDs? <%=numLocationIDsFound %></p>

<p>Sanity check: <%=numUncuratedVideos.intValue() %> uncurated + <%=poorDataVideos.size() %> worthless + <%=goodDataVideos.size() %>curated = <%=(goodDataVideos.size()+poorDataVideos.size() + numUncuratedVideos.intValue()) %> of <%=numVideos %> total videos possible</p>

<h2>About the People</h2>
<p>How productive is the agent versus a human? TBD</p>
<p>What is the average OP response time to our questions? TBD</p>
<p>Num commented videos: <%=numCommentedVideos.intValue() %></p>
<p>Num commented videos with replies: <%=numCommentedVideosReplies.intValue() %></p>
<p>Percentage responding: <%=(new Double((double)numCommentedVideosReplies.intValue()/numCommentedVideos.intValue()*100)).toString() %>% </p>
	
	
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
