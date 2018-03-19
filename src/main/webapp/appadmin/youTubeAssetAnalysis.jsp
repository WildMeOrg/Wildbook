<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,
java.io.*,org.json.JSONObject,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,org.ecocean.media.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>

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

<ul>
<%

myShepherd.beginDBTransaction();

int numFixes=0;

try{
	
		String filter="SELECT FROM org.ecocean.media.MediaAsset WHERE store instanceof org.ecocean.media.YouTubeAssetStore";
		
		//String filter="SELECT count(this) FROM org.ecocean.media.MediaAsset WHERE store instanceof org.ecocean.media.YouTubeAssetStore";
		
		Query query = myShepherd.getPM().newQuery(filter);
		Collection c = (Collection) (query.execute());
		ArrayList<MediaAsset> results=new ArrayList<MediaAsset>(c);
		//Long result=(Long)query.execute();
		//int numResults=result.intValue();
		query.closeAll();
		int numResults=results.size();
		
		%>
		<li>Num YouTube MediaAssets (videos) catalogged: <%=numResults %></li>
		<%
		
		
		/*
		YouTubeAssetStore ytas=YouTubeAssetStore.find(myShepherd);
		Iterator it=myShepherd.getAllMediaAssets();
		while(it.hasNext()){
			MediaAsset ma=(MediaAsset)it.next();
			if(ma.getStore()==ytas){numFixes++;}
		}
		*/
		
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
				if(md.getData().optJSONObject("basic") != null){
					videoTitle=md.getData().getJSONObject("basic").optString("title").replaceAll(",", " ").replaceAll("\n", " ").replaceAll("'", "").replaceAll("\"", "").replaceAll("′","").toLowerCase();
				}
				String videoDescription="[no description]";
				String videoTags="[no tags]";
				if(md.getData().getJSONObject("detailed")!=null){
					videoDescription=md.getData().getJSONObject("detailed").optString("description").replaceAll(",", " ").replaceAll("\n", " ").replaceAll("'", "").replaceAll("\"", "").replaceAll("′","").toLowerCase();
					videoTags=md.getData().getJSONObject("detailed").getJSONArray("tags").toString().replaceAll(",", " ").replaceAll("\n", " ").replaceAll("'", "").replaceAll("\"", "").replaceAll("′","").toLowerCase();
	                   
				}
    			String qFilter="SELECT FROM org.ecocean.Encounter WHERE (occurrenceRemarks.indexOf('"+videoID+"') != -1) && ( state=='approved' || state=='unidentifiable')";
    			Query newQ=myShepherd.getPM().newQuery(qFilter);
    			Collection d=(Collection)newQ.execute();
    			ArrayList<Encounter> encresults=new ArrayList<Encounter>(d);
    			newQ.closeAll();
    			int numEncs=encresults.size();
    			if(numEncs>0){
    				goodDataVideos.add(ma);
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
    					sb.append("'"+videoTitle+"','"+videoDescription+"','"+videoTags+"',poor\n");
    				}
    			}
    			
    			
              
              
              }
            }

			
			

			
		
		}
		
	%>
	<li>Num discard assets (negative training data): <%=poorDataVideos.size() %></li>
   <li>Num approved assets (positive training data): <%=goodDataVideos.size() %></li>
	</ul>
	
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
