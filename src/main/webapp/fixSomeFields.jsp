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



while(allEncs.hasNext()){
	

	Encounter sharky=(Encounter)allEncs.next();


	
	if((sharky.getDWCDateAdded()!=null)&&(sharky.getDWCDateAddedLong()==null)){
		String isoTime=sharky.getDWCDateAdded();
		
		if(isoTime.indexOf("T")!=-1){isoTime=isoTime.substring(0,isoTime.indexOf("T"));}
		
	
		
		try{
			org.joda.time.DateTime dt=fmt.parseDateTime(isoTime);
			sharky.setDWCDateAdded(new Long(dt.getMillis()));
			
			if(sharky.getDWCDateAdded().indexOf("T")!=-1){sharky.setDWCDateAdded(isoTime);}
			
		    myShepherd.commitDBTransaction();
		    myShepherd.beginDBTransaction();
		}
		catch(Exception e){
			numIssues++;
			%>
			<%=sharky.getCatalogNumber() %> was an issue with isoDateTime: <%=sharky.getDWCDateAdded() %> <br />
			<%
		}
		
			
	}
	else if((sharky.getDWCDateAdded()==null)&&(sharky.getDWCDateAddedLong()!=null)){
		org.joda.time.DateTime dt=new org.joda.time.DateTime(sharky.getDWCDateAddedLong());
		sharky.setDWCDateAdded(dt.toString(fmt));
		myShepherd.commitDBTransaction();
	    myShepherd.beginDBTransaction();
	}

	
	//check for old, incorrect dates
	/*
	org.joda.time.DateTime dt=new org.joda.time.DateTime(sharky.getDWCDateAddedLong());
	
	String encYear=Integer.toString(sharky.getYear());
	String encSubmissionYear=Integer.toString(dt.getYear());		
	if((sharky.getYear()>0)&&(!Util.isUUID(sharky.getCatalogNumber()))&&(sharky.getCatalogNumber().indexOf(encSubmissionYear)==-1)){
		numIssues++;
		int my200Index=sharky.getCatalogNumber().indexOf("200");
		String probableYear=sharky.getCatalogNumber().substring(my200Index,(my200Index+4));
		
		%>
		<p><%=sharky.getCatalogNumber() %> has a submission year of <%=encSubmissionYear %>, which I want to set to <%=probableYear %>.</p>
		<%
		
		sharky.setDWCDateAdded(probableYear);
		
		sharky.setDWCDateAdded(parser1.parseDateTime(probableYear).getMillis());
		myShepherd.commitDBTransaction();
	    myShepherd.beginDBTransaction();
	}
	*/
	
	//fix for lack of assignment of Occurrence IDs to Encounter
	


	

}



while(allSharks.hasNext()){

	MarkedIndividual sharky=(MarkedIndividual)allSharks.next();
	sharky.refreshDependentProperties(context);
	myShepherd.commitDBTransaction();
	myShepherd.beginDBTransaction();
	
/*
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
	}
*/
	
}


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
