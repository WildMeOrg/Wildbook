
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
	java.util.List
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


String filter="SELECT FROM org.ecocean.Encounter WHERE images.contains(dce15) && genus == 'Megaptera' && specificEpithet == 'novaeangliae' && ((dateInMilliseconds >= 1167609600000) && (dateInMilliseconds <= 1483228740000)) VARIABLES org.ecocean.SinglePhotoVideo dce15";
Query query=myShepherd.getPM().newQuery(filter);
Collection coll = (Collection) (query.execute());
Iterator encounters = coll.iterator();
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
	"images": [	
	<%
	
	List<SinglePhotoVideo> images= enc.getSinglePhotoVideo();
	int numImages=0;
	if(images!=null){numImages=images.size();}
	for(int p=0;p<numImages;p++){
		
		SinglePhotoVideo spv=images.get(p);
		String spotFilenameURL="http://www.flukebook.org/caribwhale_data_dir/encounters/"+Encounter.subdir(enc.getCatalogNumber())+"/"+spv.getFilename();
		%>
		"<%=spotFilenameURL %>"
		<%
		if((p+1)<numImages){
		%>
		,
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

