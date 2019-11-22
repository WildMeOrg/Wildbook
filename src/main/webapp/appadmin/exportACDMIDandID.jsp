<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,org.ecocean.media.*,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>

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

<%

myShepherd.beginDBTransaction();


String filter="SELECT FROM org.ecocean.MarkedIndividual WHERE encounters.contains(enc) && encounters.contains(enc1515) &&( enc1515.submitterID == \"CRC\" ) VARIABLES org.ecocean.Encounter enc;org.ecocean.Encounter enc1515";


//Create a FetchGroup on the PMF called "TestGroup" for MyClass
//FetchGroup grp = pm.getPersistenceManagerFactory().getFetchGroup(MarkedIndividual.class, "TestGroup");
//grp.addMember("field1").addMember("field2");


try {
	
    Query q=myShepherd.getPM().newQuery(filter);
    Collection c=(Collection) (q.execute());
    ArrayList<MarkedIndividual> list = new ArrayList<MarkedIndividual>(c);
	q.closeAll();
	
	for(MarkedIndividual indy:list){
		List<Encounter> encs=indy.getEncounterList();
		for(Encounter enc:encs){
			List<MediaAsset> assets=enc.getMedia();
			for(MediaAsset asset:assets){
				if(asset.getAcmId()!=null){
					%>
					<%=asset.getAcmId() %>,<%=indy.getIndividualID() %><br>
					<%
				}
			}
		}
	}
	
}
catch(Exception e){
	e.printStackTrace();
}
finally{
	myShepherd.rollbackDBTransaction();
	myShepherd.closeDBTransaction();

}

%>



</body>
</html>
