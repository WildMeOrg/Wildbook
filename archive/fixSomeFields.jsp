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



%>

<html>
<head>
<title>Fix Some Fields</title>

</head>


<body>





<ul>
<%

int countAnnots=0;
int matchAgainst=0;

myShepherd.beginDBTransaction();
try{
	
	String filter="SELECT FROM org.ecocean.Encounter";
	Query q=myShepherd.getPM().newQuery(filter);
	Collection c=(Collection)q.execute();
	ArrayList<Encounter> allEncs=new ArrayList<Encounter>(c);

	for(Encounter enc:allEncs){
		
		List<Annotation> annots=enc.getAnnotations();
		for(Annotation annot:annots){
			countAnnots++;
			if(annot.getIAClass()!=null){
				if(annot.getMatchAgainst())matchAgainst++;
				annot.setMatchAgainst(true);
				myShepherd.updateDBTransaction();
			}
			
		}

	}
	myShepherd.rollbackDBTransaction();
	

}
catch(Exception e){
	myShepherd.rollbackDBTransaction();
}
finally{
	myShepherd.closeDBTransaction();

}

%>
<p>Num Annots: <%=countAnnots %></p>
<p>Match Against: <%=matchAgainst %></p>
</ul>



</body>
</html>
