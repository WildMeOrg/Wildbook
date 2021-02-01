<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,org.ecocean.media.*,org.ecocean.identity.*,
org.ecocean.grid.*,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>

<%

String context="context0";
context=ServletUtilities.getContext(request);

Shepherd myShepherd=new Shepherd(context);

ArrayList<String> problemChildren=new ArrayList<String>();

%>

<html>
<head>
<title>Fix Some Fields</title>

</head>


<body>
<p>
<%

myShepherd.beginDBTransaction();

int numFixes=0;



try {

	String filter="select from org.ecocean.MarkedIndividual where encounters.contains(enc) && enc.genus == 'Eschrichtius'";
	Query q=myShepherd.getPM().newQuery(filter);
	Collection c=(Collection)q.execute();
	ArrayList<MarkedIndividual> indies=new ArrayList<MarkedIndividual>(c);
	q.closeAll();

	for(MarkedIndividual indy:indies){
		
		boolean qualifies=false;
		int numLeft=0;
		int numRight=0;
		
		List<Encounter> encs= indy.getEncounterList();
		for(Encounter enc:encs){
			List<Annotation> annots = enc.getAnnotations();
			for(Annotation annot:annots){
				if(annot.getViewpoint()!=null){
					if(annot.getMatchAgainst() && annot.getViewpoint().indexOf("left")!=-1){
						numLeft++;
					}
					if(annot.getMatchAgainst() && annot.getViewpoint().indexOf("right")!=-1){
						numRight++;
					}
				}	
			}
		}
		
		if(numLeft>2||numRight>2)qualifies=true;
		
		if(qualifies){
			numFixes++;
		%>
			<%=indy.getDisplayName() %>,https://www.flukebook.org/encounters/thumbnailSearchResults.jsp?individualIDExact=<%=indy.getIndividualID() %><br>
		<%
		}
	}

}
catch(Exception e){
	e.printStackTrace();
	myShepherd.rollbackDBTransaction();
}
finally{
	myShepherd.closeDBTransaction();

}



%>
</p>
<p>Num qualifying: <%=numFixes %></p>

</body>
</html>
