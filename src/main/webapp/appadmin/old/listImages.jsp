
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

%>
{
"mediaAssets": [
    

<%

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
int counter=0;

try{


while(encounters.hasNext()){
	counter++;
			Encounter enc=(Encounter)encounters.next();
			String individualID="";
			if(enc.getIndividualID()!=null){individualID=enc.getIndividualID();}
			
			if(counter>1){
			%>
			,
			<%	
			}
			
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
	    
  	],
  	
  	"edgePoints": [
  			<%
  			if(enc.getSpots()!=null){
	  			ArrayList<SuperSpot> spots=enc.getSpots();
	  			int numSpots=spots.size();
	  			
	  			for(int p=0;p<numSpots;p++){
	  				SuperSpot theSpot=spots.get(p);
	  				String comma=",";
	  				if(p==(numSpots-1)){comma="";}
	  				%>
	  				{"x": "<%=theSpot.getCentroidX() %>","y": "<%=theSpot.getCentroidY() %>"}<%=comma %>
	  				<%
	  			}
  			}

  			if(enc.getRightSpots()!=null){
  				ArrayList<SuperSpot> spots=enc.getRightSpots();
	  			int numSpots=spots.size();
	  			if(numSpots>0){
	  			%>
	  			,
	  			<%
	  			}
	  			for(int p=0;p<numSpots;p++){
	  				SuperSpot theSpot=spots.get(p);
	  				String comma=",";
	  				if(p==(numSpots-1)){comma="";}
	  				%>
	  				{"x": "<%=theSpot.getCentroidX() %>","y": "<%=theSpot.getCentroidY() %>"}<%=comma %>
	  				<%
	  			}
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
]
}

