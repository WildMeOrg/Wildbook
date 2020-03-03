<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,org.ecocean.ia.*,java.util.*,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>



<%

String context="context0";
context=ServletUtilities.getContext(request);

Shepherd myShepherd=new Shepherd(context);
myShepherd.setAction("standardizeSexDetermination.jsp");

boolean commit=false;
if((request.getParameter("commit")!=null)&&(request.getParameter("commit").equals("true"))){commit=true;}


%>

<html>
<head>
<title>Standardize Sex Determination</title>

</head>


<body>
<h1>Standardize Sex Determinations</h1>
<p>Use &commit=true to commit changes.</p>
<h2>Encounter.sex values</h2>
<ul>
<%

myShepherd.beginDBTransaction();


//ENCOUNTERS
Iterator<Encounter> encs=myShepherd.getAllEncounters();
HashMap<String, Integer> listedSexes=new HashMap<String, Integer>(); 
listedSexes.put("null",new Integer(0));

try {

	while(encs.hasNext()){
		
		Encounter enc=encs.next();
		boolean madeChange=false;
		
		//let's do some correction
		if(enc.getSex()!=null && enc.getSex().equals("M")){
			enc.setSex("male");
			madeChange=true;
		}
		else if(enc.getSex()!=null && enc.getSex().equals("Male")){
			enc.setSex("male");
			madeChange=true;
		}
		else if(enc.getSex()!=null && enc.getSex().equals("M?")){
			enc.setSex("Probable Male");
			madeChange=true;
		}
		else if(enc.getSex()!=null && enc.getSex().equals("F")){
			enc.setSex("female");
			madeChange=true;
		}
		else if(enc.getSex()!=null && enc.getSex().equals("Female")){
			enc.setSex("female");
			madeChange=true;
		}
		else if(enc.getSex()!=null && enc.getSex().equals("U")){
			enc.setSex("unknown");
			madeChange=true;
		}
		else if(enc.getSex()!=null && enc.getSex().equals("Unknown")){
			enc.setSex("unknown");
			madeChange=true;
		}
		else if(enc.getSex()!=null && enc.getSex().equals("")){
			enc.setSex(null);
			madeChange=true;
		}
		else if(enc.getSex()!=null && enc.getSex().equals("?")){
			enc.setSex("unknown");
			madeChange=true;
		}
		else if(enc.getSex()!=null && enc.getSex().equals("Probable Male")){
			enc.addComments("Migrated Probable Male sex determination to male.");
			enc.setSex("male");
			madeChange=true;
		}
		else if(enc.getSex()!=null && enc.getSex().equals("Probable Female")){
			enc.addComments("Migrated Probable Female sex determination to female.");
			enc.setSex("female");
			madeChange=true;
		}
		
		
		
		
		if(commit&&madeChange){myShepherd.updateDBTransaction();}
		
		
		if(enc.getSex()!=null && !listedSexes.containsKey(enc.getSex())){
			listedSexes.put(enc.getSex(), 1);
		}
		else if(enc.getSex()!=null){
			Integer inty=listedSexes.get(enc.getSex());
			inty=inty+1;
			listedSexes.put(enc.getSex(), inty);
		}
		else {
			Integer inty=listedSexes.get("null");
			inty=inty+1;
			listedSexes.put("null", inty);
		}
		
		
	}
	
	

	Set<String> sexes=listedSexes.keySet();
	for(String sex:sexes){
		%>
		<li><%=sex %>:<%=listedSexes.get(sex) %></li>
		<%
	}
	
	%>
	</ul>
	
	<h2>Individual.sex values</h2>
	<ul>
	
	<%
	
	
	
	//INDIVIDUALS
	Iterator<MarkedIndividual> indies=myShepherd.getAllMarkedIndividuals();
	HashMap<String, Integer> listedIndySexes=new HashMap<String, Integer>(); 
	listedIndySexes.put("null",new Integer(0));
	
	while(indies.hasNext()){
		
		MarkedIndividual enc=indies.next();
		boolean madeChange=false;
	
		if(enc.getSex()!=null && !listedIndySexes.containsKey(enc.getSex())){
			listedIndySexes.put(enc.getSex(), 1);
		}
		else if(enc.getSex()!=null){
			Integer inty=listedIndySexes.get(enc.getSex());
			inty=inty+1;
			listedIndySexes.put(enc.getSex(), inty);
		}
		else {
			Integer inty=listedIndySexes.get("null");
			inty=inty+1;
			listedIndySexes.put("null", inty);
		}
		
		//let's do some correction
		if(enc.getSex()!=null && enc.getSex().equals("M")){
			enc.setSex("male");
			madeChange=true;
		}
		else if(enc.getSex()!=null && enc.getSex().equals("Male")){
			enc.setSex("male");
			madeChange=true;
		}
		else if(enc.getSex()!=null && enc.getSex().equals("M?")){
			enc.setSex("Probable Male");
			madeChange=true;
		}
		else if(enc.getSex()!=null && enc.getSex().equals("F")){
			enc.setSex("female");
			madeChange=true;
		}
		else if(enc.getSex()!=null && enc.getSex().equals("Female")){
			enc.setSex("female");
			madeChange=true;
		}
		else if(enc.getSex()!=null && enc.getSex().equals("U")){
			enc.setSex("unknown");
			madeChange=true;
		}
		else if(enc.getSex()!=null && enc.getSex().equals("Unknown")){
			enc.setSex("unknown");
			madeChange=true;
		}
		else if(enc.getSex()!=null && enc.getSex().equals("")){
			enc.setSex(null);
			madeChange=true;
		}
		else if(enc.getSex()!=null && enc.getSex().equals("?")){
			enc.setSex("unknown");
			madeChange=true;
		}
		else if(enc.getSex()!=null && enc.getSex().equals("Probable Male")){
			enc.addComments("Migrated Probable Male sex determination to male.");
			enc.setSex("male");
			madeChange=true;
		}
		else if(enc.getSex()!=null && enc.getSex().equals("Probable Female")){
			enc.addComments("Migrated Probable Female sex determination to female.");
			enc.setSex("female");
			madeChange=true;
		}
		
		
		
		
		if(commit&&madeChange){myShepherd.updateDBTransaction();}
		
	}
	
	Set<String> indySexes=listedIndySexes.keySet();
	for(String sex:indySexes){
		%>
		<li><%=sex %>:<%=listedIndySexes.get(sex) %></li>
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
</ul>

</body>
</html>
