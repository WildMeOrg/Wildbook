<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>

<%

String context="context0";
context=ServletUtilities.getContext(request);

	Shepherd myShepherd=new Shepherd(context);

// pg_dump -Ft sharks > sharks.out

//pg_restore -d sharks2 /home/webadmin/sharks.out


%>

<html>
<head>
<title>Fix Some Fields</title>

</head>


<body>
<%

myShepherd.beginDBTransaction();

//build queries

Extent encClass=myShepherd.getPM().getExtent(Encounter.class, true);
Query encQuery=myShepherd.getPM().newQuery(encClass);
Iterator allEncs;





Extent sharkClass=myShepherd.getPM().getExtent(MarkedIndividual.class, true);
Query sharkQuery=myShepherd.getPM().newQuery(sharkClass);
Iterator allSharks;



try{



	
allEncs=myShepherd.getAllEncounters(encQuery);
allSharks=myShepherd.getAllMarkedIndividuals(sharkQuery);

int numIssues=0;

DateTimeFormatter fmt = ISODateTimeFormat.date();
DateTimeFormatter parser1 = ISODateTimeFormat.dateOptionalTimeParser();

String urlToThumbnailJSPPage="http://dev.flukebook.org/";


while(allEncs.hasNext()){
	

	Encounter sharky=(Encounter)allEncs.next();
	if(sharky.getCatalogNumber().indexOf(".")!=-1){
		if((sharky.getSinglePhotoVideo()!=null)&&(sharky.getSinglePhotoVideo().size()>0)){
		try{
    		//System.out.println("Trying to render a thumbnail for: "+IDKey+ "as "+thumbnailTheseImages.get(q));
    		String urlString=urlToThumbnailJSPPage+"resetThumbnail.jsp?number="+sharky.getCatalogNumber()+"&imageNum=1";
    		String urlString2=urlToThumbnailJSPPage+"encounters/encounter.jsp?number="+sharky.getCatalogNumber()+"&imageNum=1";
    		URL url = new URL(urlString);
    		URL url2 = new URL(urlString2);
      		BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream()));
      		in.close();
      		Thread.sleep(500);
      		BufferedReader in2 = new BufferedReader(new InputStreamReader(url2.openStream()));
      		in2.close();
      		Thread.sleep(500);
  		} 
  		catch (Exception e) {
    
    	//System.out.println("Error trying to render the thumbnail for "+IDKey+".");
    	e.printStackTrace();
    
  }
	}
  
}
}

/*

while(allSharks.hasNext()){

	MarkedIndividual sharky=(MarkedIndividual)allSharks.next();
	sharky.refreshDependentProperties(context);
	myShepherd.commitDBTransaction();
	myShepherd.beginDBTransaction();
	

	
}
*/



myShepherd.commitDBTransaction();
	myShepherd.closeDBTransaction();
	myShepherd=null;
%>


<p>Done successfully!</p>
<p><%=numIssues %> issues found.</p>


<%
} 
catch(Exception ex) {

	System.out.println("!!!An error occurred on page fixSomeFields.jsp. The error was:");
	ex.printStackTrace();
	//System.out.println("fixSomeFields.jsp page is attempting to rollback a transaction because of an exception...");
	encQuery.closeAll();
	encQuery=null;
	//sharkQuery.closeAll();
	//sharkQuery=null;
	myShepherd.rollbackDBTransaction();
	myShepherd.closeDBTransaction();
	myShepherd=null;

}
%>


</body>
</html>
