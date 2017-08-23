<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,org.ecocean.media.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>

<%

String context="context0";
context=ServletUtilities.getContext(request);

Shepherd myShepherd=new Shepherd(context);



%>

<html>
<head>
<title>Fix Some Fields</title>

</head>


<body>

<ul>
<%

myShepherd.beginDBTransaction();

int numFixes=0;

try{
	Occurrence occur=myShepherd.getOccurrence("f433d9e8-fe37-4427-8f7d-9d7a9491a95c");
	 int numEncs=occur.getEncounters().size();
     for(int k=0;k<numEncs;k++){
       
       ArrayList<MediaAsset> assets=occur.getEncounters().get(k).getMedia();
       int numAssets=assets.size();
       for(int i=0;i<numAssets;i++){
       MediaAsset ma=assets.get(i);
       %>
       <li><%=ma.getId()%>
       <%
         AssetStore mas=ma.getStore();
       	%>
       	...<%=mas.getType() %>
       	<%
       	//if(ma){}
       	
         if(occur.hasMediaAssetFromRootStoreType(myShepherd, AssetStoreType.YouTube)){
        	 %>
        	 ...YEAH YOUTUBE PARENT with video ID: <%=ma.getParent(myShepherd).getId() %>!
        	 <%
         }
       %>
       </li>
       <%
       }
     }
     %>
     </ul>
     <%
     
     //TBD-support more than just en language
     Properties ytProps=ShepherdProperties.getProperties("quest.properties", "en");
     String message=ytProps.getProperty("individualAddEncounter").replaceAll("%INDIVIDUAL%", "TST_ID");
     System.out.println("Will post back to YouTube OP this message if appropriate: "+message);
     YouTube.postOccurrenceMessageToYouTubeIfAppropriate(message, occur, myShepherd);

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

</body>
</html>
