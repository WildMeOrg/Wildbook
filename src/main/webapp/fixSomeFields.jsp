<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="java.net.*,java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>

<%


	Shepherd myShepherd=new Shepherd();

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

//empty comment



try{

	int numEmptyChildren=0;
	File encountersDataDir=new File("/opt/tomcat6/webapps/shepherd_data_dir/encounters");
	File[] children=encountersDataDir.listFiles();
	int numChildren=children.length;
	%>
	<p>Total number of directories: <%=numChildren %></p><p>
	<%
	
	for(int i=0;i<numChildren;i++){
		File thisFile=children[i];
		String filename=thisFile.getName();
		if((thisFile.isDirectory())&&(!myShepherd.isEncounter(filename))){
			%>
			<%=filename %>:<%=thisFile.listFiles().length %><br />
			<%
			numEmptyChildren++;
			if(thisFile.listFiles().length==0){thisFile.delete();}
		}
	}
	
	%>
	</p>
	<p>Num empty children=<%=numEmptyChildren %></p>
	<%
	
	
//allEncs=myShepherd.getAllEncounters(encQuery);
//allSharks=myShepherd.getAllMarkedIndividuals(sharkQuery);

//int numLogEncounters=0;

//while(allEncs.hasNext()){
	
	//change state
	//Encounter sharky=(Encounter)allEncs.next();
	
	/*
	if((sharky.getAssignedUsername()==null)||(sharky.getAssignedUsername().trim().replaceAll("NONE", "").equals(""))){
		
		String autoText=sharky.getRComments();
		if(autoText.indexOf("admin")!=-1){sharky.setSubmitterID("admin");}
		
		//iterate through users and assign those instead if appropriate
		ArrayList<User> users=myShepherd.getAllUsers();
		int numUsers = users.size();
		for(int i=0;i<numUsers;i++){
			User thisUser=users.get(i);
			if(autoText.indexOf(thisUser.getUsername())!=-1){
				sharky.setSubmitterID(thisUser.getUsername());
			}
		}
		
	}
	*/
	
	//if(sharky.getSex().equals("unsure")){sharky.setSex("unknown");}
	
	//if(sharky.getApproved()){sharky.setState("approved");}
	//else if(sharky.getUnidentifiable()){sharky.setState("unidentifiable");}
	//else{sharky.setState("unapproved");}
	
	//change to SinglePhotoVideo
	//int numPhotos=sharky.getImages().size();
	//List<SinglePhotoVideo> images=sharky.getImages();

	/*
	//fix for lack of assignment of Occurrence IDs to Encounter
if(myShepherd.getOccurrenceForEncounter(sharky.getCatalogNumber())!=null){
	Occurrence occur=myShepherd.getOccurrenceForEncounter(sharky.getCatalogNumber());
	sharky.setOccurrenceID(occur.getOccurrenceID());
}
else{
	sharky.setOccurrenceID(null);
}
*/
	/*
	
	for(int i=0;i<numPhotos;i++){
		

		try{
			File file=new File("/opt/tomcat6/webapps/ROOT/encounters/"+sharky.getCatalogNumber()+"/"+sharky.getImages().get(i).getDataCollectionEventID()+".jpg");
			if(!file.exists()){
				URL url = new URL("http://www.whaleshark.org/encounters/encounter.jsp?number="+sharky.getCatalogNumber());
				BufferedReader in=new BufferedReader(new InputStreamReader(url.openStream()));
				in.close();
				in=null;
				url=null;
			}
		}
		catch(Exception e){}
	
		
		//SinglePhotoVideo single=new SinglePhotoVideo(sharky.getCatalogNumber(), ((String)sharky.additionalImageNames.get(i)), ("/opt/tomcat6/webapps/ROOT/encounters/"+sharky.getCatalogNumber()+((String)sharky.additionalImageNames.get(i))));
		//SinglePhotoVideo single=images.get(i);
		//single.
		//set keywords
		//String checkString=sharky.getEncounterNumber() + "/" + (String)sharky.additionalImageNames.get(i);
		//Iterator keywords=myShepherd.getAllKeywords();
	//	while(keywords.hasNext()){
		//	Keyword word=(Keyword)keywords.next();
			//if(word.isMemberOf(checkString)){single.addKeyword(word);}
		//}
		//sharky.addSinglePhotoVideo(single);
	
	}
	*/

//}


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


myShepherd.rollbackDBTransaction();
	myShepherd.closeDBTransaction();
	myShepherd=null;
%>


<p>Done successfully!</p>


<%
} 
catch(Exception ex) {

	System.out.println("!!!An error occurred on page allEncounters.jsp. The error was:");
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