
<%@ page contentType="text/xml; charset=utf-8" language="java" import="
	java.util.Properties, 
	java.io.FileInputStream, 
	java.io.File, 
	java.io.FileNotFoundException, 
	org.ecocean.*,
	org.ecocean.servlet.*,
	javax.jdo.*, 
	java.lang.StringBuffer, 
	java.util.Vector, 
	java.util.Iterator, 
	java.lang.NumberFormatException,
	java.util.Collection,
	com.google.gson.*,
	java.util.ArrayList
	"%>

<%

response.setContentType("application/json");

String context="context0";
	Shepherd myShepherd=new Shepherd(context);


myShepherd.beginDBTransaction();

Collection c=null;
String genus="Megaptera";
String species="novaeangliae";

if(request.getParameter("genus")!=null){genus=request.getParameter("genus");}
if(request.getParameter("species")!=null){species=request.getParameter("species");}

c=myShepherd.getAllEncountersForSpeciesWithSpots(genus, species);
Iterator encounters = c.iterator();


try{


while(encounters.hasNext()){

			Encounter enc=(Encounter)encounters.next();
			String individualID="";
			if(enc.getIndividualID()!=null){individualID=enc.getIndividualID();}
			%>
{
	
	"catalogNumber": "<%=enc.getCatalogNumber() %>",
	"individualID":	"<%=individualID %>",	
	<%
	String spotFilenameURL="";
	if(enc.getSpotImageFileName()!=null){spotFilenameURL="http://www.flukebook.org/caribwhale_data_dir/encounters/"+Encounter.subdir(enc.getCatalogNumber())+"/"+enc.getSpotImageFileName();}
	

	%>
		
    "referenceImageURL":"<%=spotFilenameURL %>",
    

    "referencePoints": [
    	<%
    	if(enc.getLeftReferenceSpots()!=null){
    		ArrayList<SuperSpot> refSpots=enc.getLeftReferenceSpots();
    	%>
		    {
		      "x": "<%=refSpots.get(0).getCentroidX() %>",
		      "y": "<%=refSpots.get(0).getCentroidY() %>"
		    },
		    {
		      "x": "<%=refSpots.get(1).getCentroidX() %>",
		      "y": "<%=refSpots.get(1).getCentroidY() %>"
		    },
		    {
		      "x": "<%=refSpots.get(2).getCentroidX() %>",
		      "y": "<%=refSpots.get(2).getCentroidY() %>"
		    }
	    <%
    	}
	    %>
	    
  	]		
}			
			
			
			
			<%
		
		
	
	
}

myShepherd.rollbackDBTransaction();
	myShepherd.closeDBTransaction();
	myShepherd=null;

} 
catch(Exception ex) {

	System.out.println("!!!An error occurred on page. The error was:");
	ex.printStackTrace();

	myShepherd.rollbackDBTransaction();
	myShepherd.closeDBTransaction();
	myShepherd=null;

}
%>

