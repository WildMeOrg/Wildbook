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
		
		ArrayList
		
		
		for(int i=0;i<numResults;i++){
			
			MediaAsset ma=results.get(i);
			ma.getO
			
		}
		
	
	


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
<%=numFixes %>
</ul>

</body>
</html>
