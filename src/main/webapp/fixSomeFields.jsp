<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="java.net.*,java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>

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





//Extent sharkClass=myShepherd.getPM().getExtent(MarkedIndividual.class, true);
//Query sharkQuery=myShepherd.getPM().newQuery(sharkClass);
//Iterator allSharks;



try{


	ArrayList<User> allUsers=myShepherd.getAllUsers();
	int numUsers=allUsers.size();
	for(int i=0;i<numUsers;i++){
		
		
		User sharky=allUsers.get(i);
		if(sharky.getUserImage()!=null){
			SinglePhotoVideo sing=sharky.getUserImage();
			sing.setCorrespondingUsername(sharky.getUsername());
			%>
			<p>Setting image (<%=sing.getDataCollectionEventID() %>) for: <%=sing.getCorrespondingUsername() %></p>
			<%
			myShepherd.commitDBTransaction();
			myShepherd.beginDBTransaction();
		}
		
	}
	
//allEncs=myShepherd.getAllEncounters(encQuery);
//allSharks=myShepherd.getAllMarkedIndividuals(sharkQuery);



/*
while(allEncs.hasNext()){
	

	Encounter sharky=(Encounter)allEncs.next();
	


	
	//fix for lack of assignment of Occurrence IDs to Encounter
if(myShepherd.getOccurrenceForEncounter(sharky.getCatalogNumber())!=null){
	Occurrence occur=myShepherd.getOccurrenceForEncounter(sharky.getCatalogNumber());
	sharky.setOccurrenceID(occur.getOccurrenceID());
}
else{
	sharky.setOccurrenceID(null);
}


	

}

*/


//while(allSharks.hasNext()){

//	MarkedIndividual sharky=(MarkedIndividual)allSharks.next();
	
	//populate max years between resightings
	/*
	if(sharky.totalLogEncounters()>0){
		//int numLogEncounters=);
		for(int i=0;i<sharky.totalLogEncounters();i++){
			Encounter enc=sharky.getLogEncounter(i);
			sharky.removeLogEncounter(enc);
			sharky.addEncounter(enc);
			i--;
			//check if log encounters still exist
			numLogEncounters++;
			
		}
	}*/
	//sharky.resetMaxNumYearsBetweenSightings();
	
	//if(sharky.getSex().equals("unsure")){sharky.setSex("unknown");}
	
//}


myShepherd.commitDBTransaction();
	myShepherd.closeDBTransaction();
	myShepherd=null;
%>


<p>Done successfully!</p>


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