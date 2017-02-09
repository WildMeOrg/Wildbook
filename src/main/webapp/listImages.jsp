<?xml version="1.0" encoding="UTF-8"?>
<%@ page contentType="text/xml; charset=utf-8" language="java" import="java.util.Properties, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>

<%
String context="context0";
	Shepherd myShepherd=new Shepherd(context);

%>





<sharks>						

<%

myShepherd.beginDBTransaction();


Iterator allSharks=myShepherd.getAllMarkedIndividuals();

try{


while(allSharks.hasNext()){

	MarkedIndividual sharky=(MarkedIndividual)allSharks.next();
	
	if(sharky.wasSightedInLocationCode("1a")){

		%>
		
		<shark number="<%=sharky.getName()%>" href="http://www.whaleshark.org/individuals.jsp?number=<%=sharky.getName()%>">
		
		<%

		Vector encounters=sharky.getEncounters();
		int numEncs=encounters.size();
		
		for(int j=0;j<numEncs;j++){
		
			Encounter enc=(Encounter)encounters.get(j);
			%>

			<encounter number="<%=enc.getCatalogNumber()%>" href="http://www.whaleshark.org/encounters/encounter.jsp?number=<%=enc.getCatalogNumber()%>">

			<%			
			
			//process the submitted photos
			if(request.getParameter("extractOnly")==null){
				int numPhotos=enc.getAdditionalImageNames().size();
				for(int i=0;i<numPhotos; i++){
	
					String imagePath=(String)enc.getAdditionalImageNames().get(i);
					%>
		
					<img href="<%=enc.dir("http://www.whaleshark.org/shepherd_data_dir") %>/<%=imagePath.replaceAll("&","&amp;")%>" />
		
					<%
	
				}
			}
			
			//process the extracted photos
			
			//process left
			if((request.getParameter("left")!=null)&&(enc.getSpotImageFileName()!=null)&&(!enc.getSpotImageFileName().trim().equals(""))){
				String imagePath=enc.getSpotImageFileName();
				%>
					
					<img href="http://www.whaleshark.org/shepherd_data_dir/encounters/<%=enc.getEncounterNumber()%>/<%=imagePath.replaceAll("&","&amp;")%>" />
					
				<%
			}
			
				//process right
				if((request.getParameter("right")!=null)&&(enc.getRightSpotImageFileName()!=null)&&(!enc.getRightSpotImageFileName().trim().equals(""))){
						String imagePath=enc.getRightSpotImageFileName();
						%>
							
							<img href="http://www.whaleshark.org/shepherd_data_dir/encounters/<%=enc.getEncounterNumber()%>/<%=imagePath.replaceAll("&","&amp;")%>" />
							
						<%
			}
			
			
			
			%>
			</encounter>
			<%
		
		}
		%>
		
		</shark>
		<%
	
	}
	
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
