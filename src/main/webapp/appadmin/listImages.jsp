<?xml version="1.0" encoding="UTF-8"?>
<%@ page contentType="text/xml; charset=utf-8" language="java" import="java.util.Properties, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.media.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.*, java.lang.NumberFormatException"%>

<%
String context="context0";
	Shepherd myShepherd=new Shepherd(context);
	myShepherd.setAction("listImages.jsp");

%>





<sharks>						

<%

myShepherd.beginDBTransaction();


Iterator allSharks=myShepherd.getAllMarkedIndividuals();

try{


while(allSharks.hasNext()){

	MarkedIndividual sharky=(MarkedIndividual)allSharks.next();
	
	//if(sharky.wasSightedInLocationCode("1a2")){

		%>
		
		<shark number="<%=sharky.getName()%>" href="https://www.whaleshark.org/individuals.jsp?number=<%=sharky.getName()%>">
		
		<%

		Vector encounters=sharky.getEncounters();
		int numEncs=encounters.size();
		
		for(int j=0;j<numEncs;j++){
		
			Encounter enc=(Encounter)encounters.get(j);
			%>

			<encounter number="<%=enc.getCatalogNumber()%>" href="https://www.whaleshark.org/encounters/encounter.jsp?number=<%=enc.getCatalogNumber()%>">

			<%			
			
			//process the submitted photos
			if(request.getParameter("extractOnly")==null){
				ArrayList<MediaAsset> assets=enc.getMedia();
				int numPhotos=assets.size();
				for(int i=0;i<numPhotos; i++){
					MediaAsset ma=assets.get(i);
					String imagePath=ma.webURL().toString();
					%>
		
					<img href="<%=imagePath.replaceAll("&","&amp;") %>" />
		
					<%
	
				}
			}
			
			//process the extracted photos
			
			//process left
			if((request.getParameter("left")!=null)&&(enc.getSpotImageFileName()!=null)&&(!enc.getSpotImageFileName().trim().equals(""))&&(enc.getSpots()!=null)&&(enc.getSpots().size()>0)){
				String imagePath=enc.getSpotImageFileName();
				
				ArrayList<SuperSpot> spots=enc.getSpots();
				int numSpots=spots.size();
				%>
					
					<img href="https://www.whaleshark.org/<%=CommonConfiguration.getDataDirectoryName(context) %>/encounters/<%=enc.subdir() %>/extract<%=enc.getCatalogNumber() %>.jpg" type="left">
						<%
						
						if((enc.getLeftReferenceSpots()!=null)&&(enc.getLeftReferenceSpots().size()==3)){
							ArrayList<SuperSpot> leftSpots=enc.getLeftReferenceSpots();
						%>
						<spot type="ref0" x="<%=leftSpots.get(0).getCentroidX() %>" y="<%=leftSpots.get(0).getCentroidY() %>" />
						<spot type="ref1" x="<%=leftSpots.get(1).getCentroidX() %>" y="<%=leftSpots.get(1).getCentroidY() %>" />
						<spot type="ref2" x="<%=leftSpots.get(2).getCentroidX() %>" y="<%=leftSpots.get(2).getCentroidY() %>" />
						<%
						}
						for(int k=0;k<numSpots;k++){
						%>
							<spot type="spot" x="<%=spots.get(k).getCentroidX() %>" y="<%=spots.get(k).getCentroidY() %>" />
						<%
						}
						%>
					
					</img>
					
				<%
				}
			
			
			//process left
			if((request.getParameter("right")!=null)&&(enc.getRightSpotImageFileName()!=null)&&(!enc.getRightSpotImageFileName().trim().equals(""))&&(enc.getRightSpots()!=null)&&(enc.getRightSpots().size()>0)){
				String imagePath=enc.getRightSpotImageFileName();
				
				ArrayList<SuperSpot> spots=enc.getRightSpots();
				int numSpots=spots.size();
				%>
					
					<img href="https://www.whaleshark.org/<%=CommonConfiguration.getDataDirectoryName(context) %>/encounters/<%=enc.subdir() %>/extractR<%=enc.getCatalogNumber() %>.jpg" type="right">
						<%
						
						if((enc.getRightReferenceSpots()!=null)&&(enc.getRightReferenceSpots().size()==3)){
							ArrayList<SuperSpot> leftSpots=enc.getRightReferenceSpots();
						%>
						<spot type="ref0" x="<%=leftSpots.get(0).getCentroidX() %>" y="<%=leftSpots.get(0).getCentroidY() %>" />
						<spot type="ref1" x="<%=leftSpots.get(1).getCentroidX() %>" y="<%=leftSpots.get(1).getCentroidY() %>" />
						<spot type="ref2" x="<%=leftSpots.get(2).getCentroidX() %>" y="<%=leftSpots.get(2).getCentroidY() %>" />
						<%
						}
						for(int k=0;k<numSpots;k++){
						%>
							<spot type="spot" x="<%=spots.get(k).getCentroidX() %>" y="<%=spots.get(k).getCentroidY() %>" />
						<%
						}
						%>
					
					</img>
					
				<%
				}

			
			
			
			%>
			</encounter>
			<%
		
		}
		%>
		
		</shark>
		<%
	
	//}
	
}

myShepherd.rollbackDBTransaction();
	myShepherd.closeDBTransaction();
	myShepherd=null;
%>


<%
} 
catch(Exception ex) {

	System.out.println("!!!An error occurred on page. The error was:");
	ex.printStackTrace();

	myShepherd.rollbackDBTransaction();
	myShepherd.closeDBTransaction();
	myShepherd=null;

}
%>

</sharks>
