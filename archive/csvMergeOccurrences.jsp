<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.json.JSONArray,
org.json.JSONException,
java.math.BigDecimal,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>
<%!
private Occurrence getOccurrence(String[] details, Shepherd myShepherd){
	Occurrence occur = null;
	Encounter enc=getEncounter(details, myShepherd,4,4);
	if(enc==null)enc=getEncounter(details, myShepherd,5,4);
	if(enc==null)enc=getEncounter(details, myShepherd,4,5);
	if(enc==null)enc=getEncounter(details, myShepherd,6,6);
	if(enc==null)enc=getEncounter(details, myShepherd,6,5);
	if(enc==null)enc=getEncounter(details, myShepherd,5,6);
	if(enc!=null) occur=myShepherd.getOccurrenceForEncounter(enc.getCatalogNumber());
	if(occur!=null) System.out.println("...occur succeeded: "+occur.getOccurrenceID());
	return occur;
}

private Encounter getEncounter(String[] details, Shepherd myShepherd, int truncScaleX,int truncScaleY){
	Encounter enc = null;
	BigDecimal lat = new BigDecimal(details[4]);
	BigDecimal roundOff = lat.setScale(truncScaleX, BigDecimal.ROUND_HALF_EVEN);
	
	BigDecimal longe = new BigDecimal(details[5]);
	BigDecimal roundOff2 = longe.setScale(truncScaleX, BigDecimal.ROUND_HALF_EVEN);
	
	String filter = "select from org.ecocean.Encounter where (individual.individualID == '"+details[0]+"' || individual.names.valuesAsString.indexOf('"+details[0]+"') != -1) && year == "+details[1]+" && month == "+details[2]+" && day == "+details[3]+" && decimalLatitude == "+roundOff+" && decimalLongitude == "+roundOff2;
	//String filter = "SELECT FROM org.ecocean.Encounter WHERE (individual.individualID == 'OM00-003' || individual.names.valuesAsString.toLowerCase().indexOf('om00-003') != -1)";
	System.out.println("Filter: "+filter);
	Query q = myShepherd.getPM().newQuery(filter);
	Collection c = (Collection)q.execute();
	ArrayList<Encounter> encs = new ArrayList<Encounter>(c);
	System.out.println("..."+encs.size()+" result" );
	if(encs.size()>0)enc=encs.get(0);
	if(enc!=null) System.out.println("...succeeded!");
	q.closeAll();
	return enc;
}


%>
<%

String context="context0";
context=ServletUtilities.getContext(request);

Shepherd myShepherd=new Shepherd(context);
myShepherd.setAction("csvMergeOccurrences.jsp");


%>

<html>
<head>
<title>CSV Merge Occurrences</title>

</head>


<body>

<%

myShepherd.beginDBTransaction();



try {

if(request.getParameterValues("occur")!=null){
	String[] requests = request.getParameterValues("occur");
	if(requests.length>1){
		String root = requests[0];
		String[] details = root.split(",");
		Occurrence rootOccurrence = getOccurrence(details,myShepherd);
		if(rootOccurrence!=null){
			for(int i=1;i<requests.length;i++){
				String root2 = requests[i];
				String[] details2 = root2.split(",");
				Encounter enc=getEncounter(details2, myShepherd,4,4);
				if(enc==null)enc=getEncounter(details2, myShepherd,5,4);
				if(enc==null)enc=getEncounter(details2, myShepherd,4,5);
				if(enc==null)enc=getEncounter(details2, myShepherd,6,6);
				if(enc==null)enc=getEncounter(details2, myShepherd,6,5);
				if(enc==null)enc=getEncounter(details2, myShepherd,5,6);
				if(enc!=null){
					System.out.println("Attempting to migrate encounter: "+enc.getCatalogNumber());
					//let's make the change!
					Occurrence lilOccur = myShepherd.getOccurrenceForEncounter(enc.getCatalogNumber());
					
						System.out.println("...clear to merge!");
						System.out.println("...old occurrenceID: "+enc.getOccurrenceID());
						if(lilOccur!=null && !lilOccur.getOccurrenceID().equals(rootOccurrence.getOccurrenceID())){
							lilOccur.removeEncounter(enc);
							myShepherd.updateDBTransaction();
						}
						enc.setOccurrenceID(rootOccurrence.getOccurrenceID());
						myShepherd.updateDBTransaction();
						
						System.out.println("...new occurrenceID: "+enc.getOccurrenceID());
						
						
						rootOccurrence.addEncounter(enc);
						myShepherd.updateDBTransaction();
						
						if(lilOccur!=null && !lilOccur.getOccurrenceID().equals(rootOccurrence.getOccurrenceID())&&lilOccur.getEncounters().size()==0){
							myShepherd.getPM().deletePersistent(lilOccur);
							myShepherd.updateDBTransaction();
							System.out.println("Removed old occurence!");
						}
						%>
						<p>Merged to new occurrence: <a target="_blank" href="../occurrence.jsp?number=<%=rootOccurrence.getOccurrenceID() %>">link</a></p>
						<%
						

					
				}
				else{
					%>
					<p>I couldn't find the target to merge!</p>
					<%
				}
			}
		}
		else{
			%>
			<p>Root occurrence was null.</p>
			<%
		}
	}
	else{
		%>
		<p>Only 1 request found. Not enough.</p>
		<%
	}
}
else{
	%>
	<p>No requests found</p>
	<%
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
