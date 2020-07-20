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



%>

<html>
<head>
<title>Merge Iceland</title>

</head>


<body>

<p>Potential duplicates:</p>
<ol>
<%

myShepherd.beginDBTransaction();

int pending=0;
int duped=0;

Query q=myShepherd.getPM().newQuery("SELECT FROM org.ecocean.MarkedIndividual WHERE encounters.contains(enc) && enc.locationID == 'Iceland' && enc.genus == 'Megaptera' VARIABLES org.ecocean.Encounter enc");

try {
	
	Collection c=(Collection)q.execute();
	ArrayList<MarkedIndividual> al=new ArrayList<MarkedIndividual>(c);
	
	for(MarkedIndividual indy:al){
		try{
			System.out.println(indy.getIndividualID());
			
			
			int sizeALD=0;
			int sizeALN=0;
			ArrayList<MarkedIndividual> alD=new ArrayList<MarkedIndividual>();
			ArrayList<MarkedIndividual> alN=new ArrayList<MarkedIndividual>();
			
			if(indy.getDisplayName()!=null){
				String checkDisplayName="SELECT FROM org.ecocean.MarkedIndividual WHERE (individualID != '"+indy.getIndividualID()+"' && names.valuesAsString.toLowerCase().indexOf('"+indy.getDisplayName().toLowerCase()+"') != -1) && encounters.contains(enc) && enc.locationID == 'Iceland' && enc.genus == 'Megaptera' VARIABLES org.ecocean.Encounter enc";
				
				Query checkD=myShepherd.getPM().newQuery(checkDisplayName);
				Collection d=(Collection)checkD.execute();
				alD=new ArrayList<MarkedIndividual>(d);
				sizeALD=alD.size();
				checkD.closeAll();
				
			}
			
			if(indy.getNickName()!=null){
				String checkNickname="SELECT FROM org.ecocean.MarkedIndividual WHERE (individualID != '"+indy.getIndividualID()+"' && names.valuesAsString.toLowerCase().indexOf('"+indy.getNickName().toLowerCase()+"') != -1) && encounters.contains(enc) && enc.locationID == 'Iceland' && enc.genus == 'Megaptera' VARIABLES org.ecocean.Encounter enc";
				
				Query checkN=myShepherd.getPM().newQuery(checkNickname);
				Collection n=(Collection)checkN.execute();
				alN=new ArrayList<MarkedIndividual>(n);
				sizeALN=alN.size();
				checkN.closeAll();
			}
			
			if(sizeALD>0 || sizeALN>0){
				
				%>
				
				<li><strong>Found duplicates for: <a target="_blank" href="../individuals.jsp?number=<%=indy.getIndividualID() %>">link</a>:<%=indy.getDisplayName() %>:<%=indy.getNickName() %></strong><ul>
				
				<%
			
				if(sizeALD>0){
					for(MarkedIndividual dupe:alD){
						%>
						<li><a target="_blank" href="../individuals.jsp?number=<%=dupe.getIndividualID() %>">link</a>---<%=dupe.getDisplayName() %>---<%=dupe.getNickName() %>  <a target="_blank" href="../merge.jsp?individualA=<%=indy.getIndividualID() %>&individualB=<%=dupe.getIndividualID() %>">merge?</a></li>
						<%
					}
				}
				
				
				if(sizeALN>0){
					for(MarkedIndividual dupe:alN){
						%>
						<li><a target="_blank" href="../individuals.jsp?number=<%=dupe.getIndividualID() %>">link</a>---<%=dupe.getDisplayName() %>---<%=dupe.getNickName() %>  <a target="_blank" href="../merge.jsp?individualA=<%=indy.getIndividualID() %>&individualB=<%=dupe.getIndividualID() %>">merge?</a></li>
						<%
					}
				}
				
				%>
				</ul></li>
				<%
			
			}
			
		}
		catch(Exception f){
			f.printStackTrace();
		}
		
	} //end for loop on individuals
		
	
	
}
catch(Exception e){
	e.printStackTrace();
}
finally{
	q.closeAll();
	myShepherd.rollbackDBTransaction();
	myShepherd.closeDBTransaction();

}

%>
</ol>


</body>
</html>
