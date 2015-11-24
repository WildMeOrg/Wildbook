<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>

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
<p>Photo paths to fix.</p>
<ul>
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


	String rootDir = getServletContext().getRealPath("/");
	String baseDir = ServletUtilities.dataDir(context, rootDir).replaceAll("dev_data_dir", "caribwhale_data_dir");
	
allEncs=myShepherd.getAllEncountersForSpecies("Megaptera", "novaeangliae").iterator();
allSharks=myShepherd.getAllMarkedIndividuals(sharkQuery);

ArrayList<String> kwords=new ArrayList<String>();
kwords.add("5U");
kwords.add("5S");
kwords.add("5R");
kwords.add("5M");
kwords.add("5C");
kwords.add("4C");
kwords.add("4B");
kwords.add("4A");
kwords.add("3C");
kwords.add("3B");
kwords.add("3A");
kwords.add("2C");
kwords.add("2B");
kwords.add("2A");
kwords.add("1");



while(allEncs.hasNext()){
	
	Encounter enc=(Encounter)allEncs.next();

	if(enc.getPatterningCode()==null){
		int numKwords=kwords.size();
		for(int i=0;i<numKwords;i++){
			String keyword=kwords.get(i);
			if(enc.hasKeyword(myShepherd.getKeyword(keyword))){
				enc.setPatterningCode(keyword);
				myShepherd.commitDBTransaction();
				myShepherd.beginDBTransaction();
			}
			else if((enc.getIndividualID()!=null)&&(!enc.getIndividualID().toLowerCase().equals("unassigned"))){
				MarkedIndividual indy=myShepherd.getMarkedIndividual(enc.getIndividualID());
				if(indy.getPatterningCode()!=null){enc.setPatterningCode(indy.getPatterningCode());}
			}
		}
	}
	
}

%>




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


}
finally{
	myShepherd.rollbackDBTransaction();
	myShepherd.closeDBTransaction();
	myShepherd=null;
}
%>

</ul>
<p>Done successfully!</p>
</body>
</html>
