
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
	java.util.ArrayList,
	org.ecocean.media.*,
	java.util.Collections
	"%>

<%

response.setContentType("application/json");

%>
{
"encounters": [
    

<%

String context="context0";
	Shepherd myShepherd=new Shepherd(context);
myShepherd.setAction("listImages.jsp");

long range=2500;

myShepherd.beginDBTransaction();

String filter = "select from org.ecocean.Encounter where (spots != null || rightSpots!=null)";
Query q = myShepherd.getPM().newQuery(filter);
q.setRange(0,range);
Collection c=(Collection)q.execute();
ArrayList<Encounter> encounters = new ArrayList<Encounter>(c);

int counter=0;

try{


	for(Encounter enc:encounters){
	
		
		
				counter++;
				
				//kick off a scan
				MediaAsset spotLeftMA = null;
				MediaAsset spotRightMA = null;
				ArrayList<MediaAsset> allSpotMAs = enc.findAllMediaByLabel(myShepherd, "_spot");
				////////if ((allSpotMAs != null) && (allSpotMAs.size() > 0)) spotLeftMA = allSpotMAs.get(0);
				//// warning, hack to get around bug cause by gap in code changes post-migration
				if (allSpotMAs != null) {
					Collections.reverse(allSpotMAs);
					for (MediaAsset maL : allSpotMAs) {
					if (maL.getFilename().indexOf("extractRight") < 0) {
					  spotLeftMA = maL;
					  break;
					}
					}
				}
				allSpotMAs = enc.findAllMediaByLabel(myShepherd, "_spotRight");
				if ((allSpotMAs != null) && (allSpotMAs.size() > 0)) spotRightMA = allSpotMAs.get(allSpotMAs.size() - 1);

				if((enc.getSpots()!=null && enc.getSpots().size()>3 && spotLeftMA!=null)||(enc.getRightSpots()!=null && enc.getRightSpots().size()>3 && spotRightMA!=null)){
					
				
				String individualID="";
				if(enc.getIndividual()!=null){individualID=enc.getIndividual().getIndividualID();}
				
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
	  			
	  			boolean hasLeft=false;
				boolean hasRight=false;
	  			
	  			
	  			if(enc.getSpots()!=null && spotLeftMA!=null){
	  				hasLeft=true;
	
	  				%>
	  					
	  			    "leftReferenceImageURL":"<%=spotLeftMA.webURL() %>",
	  				
	  	
	  				 	"leftSpots": [
	  				<%
	  				
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
		  			
		  		  	%>
		  		  	]
		  		  	<%
		  			
	  			}
	
	
	  			if(enc.getRightSpots()!=null && spotRightMA!=null){
	  				
	  				hasRight=true;
	  				
	  				if(hasLeft){
	  					%>
	  					,
	  					<%
	  				}
	  						
	  				
	  				%>
	  					"rightReferenceImageURL":"<%=spotRightMA.webURL() %>",
					 	"rightSpots": [
					<%
					
	  				
	  				ArrayList<SuperSpot> spots=enc.getRightSpots();
		  			int numSpots=spots.size();

		  			for(int p=0;p<numSpots;p++){
		  				SuperSpot theSpot=spots.get(p);
		  				String comma=",";
		  				if(p==(numSpots-1)){comma="";}
		  				%>
		  				{"x": "<%=theSpot.getCentroidX() %>","y": "<%=theSpot.getCentroidY() %>"}<%=comma %>
		  				<%
		  			}
		  			
		  			%>
		  			]
		  			<%
	  			}
	  			%>
	  			
	}			
				
				
				
				<%
			
			
	} //end if
	
	} //end for
	


} 
catch(Exception ex) {

	System.out.println("!!!An error occurred on page. The error was:");
	ex.printStackTrace();



}
finally{
	myShepherd.rollbackDBTransaction();
	myShepherd.closeDBTransaction();
	myShepherd=null;
}
%>
]
}

