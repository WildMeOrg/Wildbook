<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,
org.ecocean.cache.*,
org.json.*,
java.io.*,java.util.*, java.io.FileInputStream, 
java.util.concurrent.ThreadPoolExecutor,
java.io.File, java.io.FileNotFoundException, 
org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, 
java.lang.StringBuffer, java.util.Vector, java.util.Iterator, 
java.lang.NumberFormatException"
%>

<%

String context="context0";
context=ServletUtilities.getContext(request);



int numFixes=0;

String emailAddress=null;

if(request.getParameter("sendToEmail")!=null){
	emailAddress=request.getParameter("sendToEmail");
}

%>

<html>
<head>
<title>Test Emails</title>
</head>


<body>

<h1>Testing Emails</h1>

<%
if(emailAddress==null){
%>

<p>Please indicate the email address to send test emails to using the ?sendToEmail= parameter in the URL.</p>

<%
}
else{
	
	Shepherd myShepherd=new Shepherd(context);
	myShepherd.setAction("testEmail.jsp");

	ThreadPoolExecutor es = MailThreadExecutorService.getExecutorService();
	
	myShepherd.beginDBTransaction();
	
	try{
		
		%>
		<p>Trying to send a collaboration invite to: <%=emailAddress %></p>
		<%
		Map<String, String> tagMap1 = new HashMap<>();
		tagMap1.put("@USER@", "testUser");
		tagMap1.put("@TEXT_CONTENT@", "This is an invite message from me! Please collaborate with me!");
		tagMap1.put("@SENDER@", "wantingAnInvite");
		tagMap1.put("@SENDER-EMAIL@", "wantingAnEmail@example.com");
		tagMap1.put("@LINK@", String.format("//%s/myAccount.jsp", CommonConfiguration.getURLLocation(request)));
		
		tagMap1.put("@CONTEXT_NAME@", ContextConfiguration.getNameForContext(context));
		es.execute(new NotificationMailer(context, "en", emailAddress, "collaborationInvite", tagMap1));
		
		
		%>
		<p>Trying to send a new Encounter submission email to: <%=emailAddress %></p>
		<%
		List<Encounter> encs=myShepherd.getMostRecentIdentifiedEncountersByDate(1);
		if(encs.size()>0){
			Map<String, String> tagMap2=NotificationMailer.createBasicTagMap(request, encs.get(0));
			es.execute(new NotificationMailer(context, "en", emailAddress, "newSubmission", tagMap2));
			%>
			<p>Trying to send a new Encounter submission summary email to: <%=emailAddress %></p>
			<%
			es.execute(new NotificationMailer(context, "en", emailAddress, "newSubmission-summary", tagMap2));
			
			%>
			<p>Trying to send an Encounter delete email to: <%=emailAddress %></p>
			<%
			tagMap2.put("@USER@", "testUser");
			es.execute(new NotificationMailer(context, null, emailAddress, "encounterDelete", tagMap2));
			
			Iterator sharkies=myShepherd.getAllMarkedIndividuals();
			if(sharkies.hasNext()){
				
				%>
				<p>Trying to send a new Individual email with random data to: <%=emailAddress %></p>
				<%
				MarkedIndividual indy=(MarkedIndividual)sharkies.next();
				Map<String, String> tagMap3=NotificationMailer.createBasicTagMap(request, indy, encs.get(0));
				es.execute(new NotificationMailer(context, "en", emailAddress, "individualCreate", tagMap3));
				
				%>
				<p>Trying to send a new Encounter added to existing MarkedIndividual email with random data to: <%=emailAddress %></p>
				<%
				es.execute(new NotificationMailer(context, "en", emailAddress, "individualAddEncounter", tagMap3));
				
				
				%>
				<p>Trying to send a your MarkedIndividual has been resighted email with random data to: <%=emailAddress %></p>
				<%
				es.execute(new NotificationMailer(context, "en", emailAddress, "individualUpdate", tagMap3));
				
				
			}
			
			
			

		}
		
		
		

		
		
		
		
		
		
	
	}
	catch(Exception e){
		
	}
	finally{
		myShepherd.rollbackDBTransaction();
		myShepherd.closeDBTransaction();
	
	}

}
%>

</body>
</html>
