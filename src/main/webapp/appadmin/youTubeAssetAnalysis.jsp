<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,org.ecocean.ai.nmt.google.*,
java.io.*,org.json.JSONObject,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,org.ecocean.media.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>

<%!
private String translateIfNotEnglish(String text){
	String shortForm=text;
	if(shortForm.length()>500){shortForm=shortForm.substring(0,499);}
	String langCode=DetectTranslate.detectLanguage(shortForm);
	if((!langCode.toLowerCase().equals("en"))&&(!langCode.toLowerCase().equals("und"))){
		System.out.println("Translating: "+text);
		text=DetectTranslate.translateToEnglish(text).replaceAll(",", " ").replaceAll("\n", " ").replaceAll("'", "").replaceAll("\"", "").replaceAll("′","").replaceAll("’","").toLowerCase();
		System.out.println("Translated to: "+text);
	}
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


<%

myShepherd.beginDBTransaction();

int numFixes=0;

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
		StringBuffer sb=new StringBuffer("@RELATION YouTubeWhaleShark\n\n@ATTRIBUTE title String\n@ATTRIBUTE description String\n@ATTRIBUTE tags String\n@ATTRIBUTE class {good,poor}\n\n@data\n");
		
		for(int i=0;i<numResults;i++){
			
			//YouTubeAsset itself
			MediaAsset ma=results.get(i);

            //JSONObject data=md.getData();
            if ((ma.getMetadata() != null)) {
              MediaAssetMetadata md = ma.getMetadata();	
              if (md.getData() != null) {
            	
            	String videoID=ma.getMetadata().getData().getJSONObject("detailed").optString("id");
				String videoTitle="[unknown]";
				String videoTitleShort=videoTitle;
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
    			String qFilter="SELECT FROM org.ecocean.Encounter WHERE (occurrenceRemarks.indexOf('"+videoID+"') != -1) && ( state=='approved' || state=='unidentifiable')";
    			Query newQ=myShepherd.getPM().newQuery(qFilter);
    			Collection d=(Collection)newQ.execute();
    			ArrayList<Encounter> encresults=new ArrayList<Encounter>(d);
    			newQ.closeAll();
    			int numEncs=encresults.size();
    			if(numEncs>0){
    				goodDataVideos.add(ma);
    				
    				//detect and translate
    				videoTitle=translateIfNotEnglish(videoTitle);
    				videoTags=translateIfNotEnglish(videoTags);
    				videoDescription=translateIfNotEnglish(videoDescription);		
    				
    				
    				sb.append("'"+videoTitle+"','"+videoDescription+"','"+videoTags+"',good\n");
    			}
    			else{
    				
    				
        			String pFilter="SELECT FROM org.ecocean.Encounter WHERE (occurrenceRemarks.indexOf('"+videoID+"') != -1) && ( state=='auto_sourced')";
        			Query newP=myShepherd.getPM().newQuery(pFilter);
        			Collection e=(Collection)newP.execute();
        			ArrayList<Encounter> encresults2=new ArrayList<Encounter>(e);
        			newP.closeAll();
    				if(encresults2.size()==0){
    					poorDataVideos.add(ma);
    					
        				//detect and translate
        				//detect and translate
    				videoTitle=translateIfNotEnglish(videoTitle);
    				videoTags=translateIfNotEnglish(videoTags);
    				videoDescription=translateIfNotEnglish(videoDescription);	
    					
    					sb.append("'"+videoTitle+"','"+videoDescription+"','"+videoTags+"',poor\n");
    				}
    			}
    			
    			
              
              
              }
            }

			
			

			
		
		}
		
	%>
%Num discard assets (negative training data): <%=poorDataVideos.size() %><br>
%Num approved assets (positive training data): <%=goodDataVideos.size() %><br />
%<br>
	<pre><%=sb.toString() %></pre>
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
