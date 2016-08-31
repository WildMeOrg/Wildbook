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



%>

<html>
<head>
<title>Set Marked Individual Species</title>

</head>


<body>
<p>Setting species for all MarkedIndividuals using their method setTaxonomyFromEncounters()</p>


<ul>
<%

myShepherd.beginDBTransaction();

int numFixes=0;
int unNamed = 0;

try {

	String rootDir = getServletContext().getRealPath("/");
	String baseDir = ServletUtilities.dataDir(context, rootDir).replaceAll("dev_data_dir", "caribwhale_data_dir");

  Iterator allIndividuals=myShepherd.getAllMarkedIndividuals();

  boolean committing=false;


  while(allIndividuals.hasNext()){

    MarkedIndividual indie = (MarkedIndividual) allIndividuals.next();
    String taxonomy = indie.getGenusSpecies();

    if (taxonomy == null || taxonomy.equals("")) {
      unNamed++;
      indie.setTaxonomyFromEncounters();
      taxonomy = indie.getGenusSpecies();
    }

    %><p>Individual <%=indie.getIndividualID()%> has new taxonomy <%=indie.getGenusSpecies()%> </p><%


    if (committing) {
      numFixes++;
  		myShepherd.commitDBTransaction();
  		myShepherd.beginDBTransaction();
    }
  }
}

catch(Exception e){
	myShepherd.rollbackDBTransaction();
}
finally{
	myShepherd.closeDBTransaction();

}

%>

</ul>
<p>Done successfully: <%=numFixes %></p>
<p><%=unNamed %> unnamed whales found</p>


</body>
</html>
