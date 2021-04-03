
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,org.ecocean.media.*,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>




<%

String context="context0";
context=ServletUtilities.getContext(request);

Shepherd myShepherd=new Shepherd(context);
myShepherd.setAction("idReviewExport.jsp");



%>

<html>
<head>
<title>ID Review Export</title>

</head>


<body>
<pre>
<%

myShepherd.beginDBTransaction();


String filter="SELECT FROM org.ecocean.MarkedIndividual WHERE encounters.contains(enc) && enc.specificEpithet == 'crocuta' VARIABLES org.ecocean.Encounter enc";
String iaClass="hyaena";
String baseURL="https://africancarnivore.wildbook.org";
String exportFullPath="/tmp/idReviewExport.csv";


try {
	
    Query q=myShepherd.getPM().newQuery(filter);
    Collection c=(Collection) (q.execute());
    ArrayList<MarkedIndividual> indies = new ArrayList<MarkedIndividual>(c);
	q.closeAll();
	

	
	StringBuffer sb=new StringBuffer("UUID,IndividualName,Review URL,Num Lefts, Num Rights\n");

	for(MarkedIndividual indy:indies){
		
		int numLefts=0;
		int numRights=0;
		
		List<Encounter> encs=indy.getEncounters();

		for(Encounter enc:encs){
			List<Annotation> annots=enc.getAnnotations();
			
			for(Annotation annot:annots){
				if(annot.getIAClass() !=null && annot.getIAClass().equals(iaClass)){

					if(annot.getViewpoint()!=null){
						if(annot.getViewpoint().indexOf("left")!=-1){numLefts++;}
						else if(annot.getViewpoint().indexOf("right")!=-1){numRights++;}
						
					}
					
					
				}
			}
		}
		
		sb.append(indy.getIndividualID()+","+indy.getDisplayName()+","+baseURL+"/encounters/thumbnailSearchResults.jsp?individualIDExact="+indy.getIndividualID()+","+numLefts+","+numRights+"\n");
		
	}
	
	Util.writeToFile(sb.toString(), exportFullPath);
	
	%>
	<pre><%=sb.toString() %></pre>
	<p>Exported file written to: <%=exportFullPath %></p>
	<%
	
}
catch(Exception e){
	e.printStackTrace();
}
finally{
	myShepherd.rollbackDBTransaction();
	myShepherd.closeDBTransaction();

}

%>
</pre>


</body>
</html>
