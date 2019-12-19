<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>

<%

String context="context0";
context=ServletUtilities.getContext(request);

Shepherd myShepherd=new Shepherd(context);

ArrayList<String> fixList=new ArrayList<String>();
fixList.add("832e9d2e-dd1b-4d76-8ecb-0a2de42fbdbc");
fixList.add("5026c2aa-7deb-480a-8cef-fd22e9c275a1");

fixList.add("6e6816aa-dca1-412a-a2f6-c9c95e36fbb9");
		fixList.add("2bdf4297-5e28-4bb4-8d5f-12c01cb4a389");
				fixList.add("2bdf4297-5e28-4bb4-8d5f-12c01cb4a389");
						fixList.add("35525f56-ca60-4769-b978-f0f8cf5d8a0c");
								fixList.add("5343aec8-45a5-44dc-8a8b-b125e17af405");
										fixList.add("d7230ec7-91e9-417a-9a3c-30deae8c8b48");
												fixList.add("70536a66-4722-4972-bc49-7dcb35948a87");
														fixList.add("fd7e9597-f95c-46fd-93cc-6674e8a414e0");

														fixList.add("d6356a98-ccaa-4898-ba41-2b7dbc55bb77");
																fixList.add("40dbf202-f8b9-4fae-8717-2aba2be7fc8c");
																		fixList.add("8b592df5-b137-4b0c-b73a-b073d8d0a927");
																				fixList.add("da8d32fa-ad78-4342-b19e-59d17b289635");
																						fixList.add("6a01d273-d977-4d6b-b9bd-a1d1e7d484c4");
																								fixList.add("c4232ef3-dd3c-4e6d-95a0-c98297ef2bfb");
																										fixList.add("69330c61-be62-44a8-b945-b188482cce34");
																												fixList.add("832e9d2e-dd1b-4d76-8ecb-0a2de42fbdbc");
																														fixList.add("7d21ec34-dff8-48a8-9170-165317b56da6");
																																fixList.add("a8a5664d-a771-410a-bb67-c51367ca225f");
																																		fixList.add("7095f68a-a8ee-4c7a-8c1b-b962f63b4e01");
																																				fixList.add("b7d401cc-0733-423e-944a-dd055d1d6a90");
																																						fixList.add("4c7a260e-8d55-43d3-925f-c5a66436f683");
																																								fixList.add("8a7dc598-7195-477b-abf3-1b7213110cbb");
																																										fixList.add("5d6f2918-bfce-4fb5-b0eb-f263dfa308d7");
																																										
																																										fixList.add("72fff4f0-49ca-4bc4-90e9-91395c8bc916");
																																										
																																										fixList.add("130cbcc6-6d81-457a-a756-85689f6bb079");















%>

<html>
<head>
<title>Fix Some Fields</title>

</head>


<body>

<%

myShepherd.beginDBTransaction();


String filter="SELECT FROM org.ecocean.MarkedIndividual WHERE encounters.contains(enc1515) && ( enc1515.submitterID == \"lsteiner\" ) VARIABLES org.ecocean.Encounter enc1515";



try {

	Query q=myShepherd.getPM().newQuery(filter);
	Collection c = (Collection) (q.execute());
	ArrayList<MarkedIndividual> indies=new ArrayList<MarkedIndividual>(c);
	q.closeAll();
	
	int numIndies=indies.size();
	%>
	<p>Unmatched Indies</p>
	<ol>
	<%
	
	for(MarkedIndividual indy:indies){
		boolean matchAgainst=false;
		List<Encounter> encs=indy.getEncounterList();
		int numAnnots=0;
		for(Encounter enc:encs){
			List<Annotation> annots=enc.getAnnotations();
			for(Annotation annot:annots){
				if(annot.getMatchAgainst()){
					matchAgainst=true;
				}
				else if(fixList.contains(annot.getId())){
					annot.setMatchAgainst(true);
					myShepherd.updateDBTransaction();
				}
				numAnnots++;
			}
			
		}
		if(!matchAgainst && numAnnots>0){
			%>
			<li><a target="_blank" href="../encounters/thumbnailSearchResults.jsp?individualIDExact=<%=indy.getIndividualID() %>"><%=indy.getDisplayName() %> (<%=numAnnots %>)</a></li>
			<%
		}
	}

	%>
	</ol>
	<p>Total number indies: <%=numIndies %></p>
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



</body>
</html>
