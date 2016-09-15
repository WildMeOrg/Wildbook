<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,
java.io.*,java.util.*, org.ecocean.media.MediaAsset,java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>

<%

String context="context0";
context=ServletUtilities.getContext(request);

	Shepherd myShepherd=new Shepherd(context);

// pg_dump -Ft sharks > sharks.out

//pg_restore -d sharks2 /home/webadmin/sharks.out


%>

<html>
<head>
<title>All Dolphins</title>

</head>


<body>
<p>Tursiops truncatus with multiple distinct photos</p>
<ol>
<%

myShepherd.beginDBTransaction();

//build queries



Iterator allSharks;

int count=0;

try{


allSharks=myShepherd.getAllMarkedIndividuals();

while(allSharks.hasNext()){
	
	MarkedIndividual indy=(MarkedIndividual)allSharks.next();
	
	if((indy.getGenusSpecies()!=null)&&((indy.getGenusSpecies().equals("Tursiops truncatus")))){
		count++;
		Vector encs=indy.getEncounters();
		ArrayList<String> photoNames=new ArrayList<String>();
		if(encs!=null){
			int numEncs=encs.size();
			
			for(int i=0;i<numEncs;i++){
				Encounter enc=(Encounter)encs.get(i);
				List<MediaAsset> photos=enc.getMedia();
				if(photos!=null){
					int numPhotos=photos.size();
					for(int k=0;k<numPhotos;k++){
						MediaAsset p=photos.get(k);
						URL u = p.safeURL(myShepherd, request);
						String filename = ((u == null) ? null : u.toString());
						if(!photoNames.contains(filename)){photoNames.add(filename);}
					}
				}
			}
	}
		
		if(photoNames.size()>1 ){
			%>
			<li><a target="_blank" href="individuals.jsp?number=<%=indy.getIndividualID() %>"><%=indy.getIndividualID() %></a> with <%=photoNames.size() %></li>
			<%
		}
		
	}
}

%>

<p>Done successfully: <%=count %></p>


<%
} 
catch(Exception ex) {

	System.out.println("!!!An error occurred on page fixSomeFields.jsp. The error was:");
	ex.printStackTrace();
	//System.out.println("fixSomeFields.jsp page is attempting to rollback a transaction because of an exception...");

	//sharkQuery.closeAll();
	//sharkQuery=null;


}
finally{
	myShepherd.rollbackDBTransaction();
	myShepherd.closeDBTransaction();
	myShepherd=null;
}
%>

</ol>
</body>
</html>
