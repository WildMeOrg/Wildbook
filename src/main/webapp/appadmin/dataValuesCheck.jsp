<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,org.ecocean.ia.*,java.util.*,
org.ecocean.media.*,org.ecocean.servlet.importer.ImportTask,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>


<%
String context="context0";
context=ServletUtilities.getContext(request);

Shepherd myShepherd=new Shepherd(context);
myShepherd.setAction("dataValuesCheck.jsp.jsp");


%>

<jsp:include page="../header.jsp" flush="true" />

<style>
table{
	border-collapse: collapse;
}
table, th, td {
  border: 1px solid black;
}
th, td {
	padding: 5px;
  	text-align: left;
}

</style>

<div class="container maincontent">
<h1>Questionable (Data) Values</h1>
      <p>Data checks below look for suspect data values, such as unsupported species values or bad GPS coordinates.</p>
<%

myShepherd.beginDBTransaction();



try {
	
	//find Encounters with bad Encounter.genus and Encounter.specificEpithet values
	%>
	<h2>Encounters with Genus and Species values not defined in commonConfiguration.properties</h2>
	<%
	
	List<String> species=CommonConfiguration.getIndexedPropertyValues("genusSpecies", context);
	String query="select from org.ecocean.Encounter where catalogNumber != null";
	StringBuffer sbSpecies=new StringBuffer();
	for(String str:species){
		StringTokenizer tknzr=new StringTokenizer(str," ");
		if(tknzr.countTokens()>1){
			String genus=tknzr.nextToken();
			String specificEpithet=tknzr.nextToken();
			while(tknzr.hasMoreTokens())specificEpithet+=" "+tknzr.nextToken();
			sbSpecies.append(" && (genus != '"+genus+"' && specificEpithet != '"+specificEpithet+"')");
		}
	}
	if(!sbSpecies.toString().equals("")){
		query+=sbSpecies.toString();
		Query q=myShepherd.getPM().newQuery(query);
		Collection c= (Collection)q.execute();
		if(c.size()>0){
			ArrayList<Encounter> encs=new ArrayList<Encounter>(c);
			%>
			<p><%=encs.size() %> encounters had unsupported genus and species values.</p>
			<table>
			<thead><tr><td><strong>Encounter Number</strong></td><td><strong>Genus and species values</strong></td></tr></thead>
			<%
			for(Encounter enc:encs){
			%>
				<tr><td><a href="../encounters/encounter.jsp?number=<%=enc.getCatalogNumber() %>" target="_blank"><%=enc.getCatalogNumber() %></a></td><td><%=enc.getGenus() %> <%=enc.getSpecificEpithet() %></td></tr>
			<%
			}
			%>
			</table>
			<%
		}
		else{
			%>
			<p>No problematic values were found.</p>
			<%
		}
		q.closeAll();
	}
	else{
		%>
		<p>No species definitions were found.</p>
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



</div>


      <jsp:include page="../footer.jsp" flush="true"/>
