<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>

<%

String context="context0";
context=ServletUtilities.getContext(request);

	Shepherd myShepherd=new Shepherd(context);

// pg_dump -Ft sharks > sharks.out

//pg_restore -d sharks2 /home/webadmin/sharks.out


%>

<html>
<head>
<title>Fix Some Fields</title>

</head>


<body>
<%

myShepherd.beginDBTransaction();

//build queries

Extent encClass=myShepherd.getPM().getExtent(Encounter.class, true);
Query encQuery=myShepherd.getPM().newQuery(encClass);
Iterator<Encounter> allEncs;





Extent sharkClass=myShepherd.getPM().getExtent(MarkedIndividual.class, true);
Query sharkQuery=myShepherd.getPM().newQuery(sharkClass);
Iterator<MarkedIndividual> allSharks;

/*
String isoTime=sharky.getDWCDateAdded();
int month = -1;
if      (encName.contains("JAN")) {month = 1;}
else if (encName.contains("FEB")) {month = 2;}
else if (encName.contains("MAR")) {month = 3;}
else if (encName.contains("APR")) {month = 4;}
else if (encName.contains("MAY")) {month = 5;}
else if (encName.contains("JUN")) {month = 6;}
else if (encName.contains("JUL")) {month = 7;}
else if (encName.contains("AUG")) {month = 8;}
else if (encName.contains("SEP")) {month = 9;}
else if (encName.contains("OCT")) {month = 10;}
else if (encName.contains("NOV")) {month = 11;}
else if (encName.contains("DEC")) {month = 12;}

sharky.setMonth(month);
*/


try{




allEncs=myShepherd.getAllEncounters(encQuery);
//allSharks=myShepherd.getAllMarkedIndividuals(sharkQuery);

int numIssues=0;

DateTimeFormatter fmt = ISODateTimeFormat.date();
DateTimeFormatter parser1 = ISODateTimeFormat.dateOptionalTimeParser();

int numMissingDays = 0;
int numParseIntExceptions = 0;
ArrayList<String> exceptionEncIDs = new ArrayList<String>();

boolean committing = true;

while(allEncs.hasNext()){


	Encounter sharky=allEncs.next();

	try{
	if((sharky.getCatalogNumber()!=null)&&(sharky.getCatalogNumber()!="Unassigned")){
		String encName = sharky.getCatalogNumber();



    // checks if it is both missing a day, and has a name that can be parsed to find a day
    if (sharky.getDay() == 0 && sharky.getCatalogNumber()!=null && sharky.getCatalogNumber().length()>8) {
      %><br />
      <%=sharky.getCatalogNumber() %> has no day! <br />
      <%
      numMissingDays++;

      String dayStr = sharky.getCatalogNumber().substring(7,9);
      %>
       &nbsp&nbsp&nbsp&nbsp dayString = <%=dayStr%>  <br />
      <%

      try {
        int dayInt = Integer.parseInt(dayStr);
        %>
         &nbsp&nbsp&nbsp&nbsp dayInt = <%=dayInt%>  <br />
        <%
        if (committing) {
          sharky.setDay(dayInt);
        }
      } catch (NumberFormatException e) {
        numParseIntExceptions++;
        %>
        &nbsp&nbsp&nbsp&nbsp<%=sharky.getCatalogNumber() %> has an un-parseable day <br />
        <%

        if (sharky.getCatalogNumber().length()>15) {
          %>
          &nbsp&nbsp&nbsp&nbsp CANDIDATE FOR DELETION <br />
          <%
          exceptionEncIDs.add(sharky.getCatalogNumber());
        }

      }


    }


		myShepherd.commitDBTransaction();
		myShepherd.beginDBTransaction();
		}
	}
		catch(Exception e){
			numIssues++;
			%>
			<%=sharky.getCatalogNumber() %> has an issue with month. <br />
			<%
		}


	}

  %>
    <br/>to-delete bad encounters:<br/>
  <%
  for (String badEncID : exceptionEncIDs) {
    %><br/>&nbsp&nbsp&nbsp&nbsp<%=badEncID%>
    <%
  }

  myShepherd.commitDBTransaction();
	myShepherd.closeDBTransaction();
	myShepherd=null;
%>


<p>Done successfully!</p>
<p><%=numIssues %> issues found.</p>
<p><%=numMissingDays %> encounters with no "day" field.</p>



<%
}
catch(Exception ex) {

	System.out.println("!!!An error occurred on page fixSomeFields.jsp. The error was:");
	ex.printStackTrace();
	//System.out.println("fixSomeFields.jsp page is attempting to rollback a transaction because of an exception...");
	encQuery.closeAll();
	encQuery=null;
	//sharkQuery.closeAll();
	//sharkQuery=null;
	myShepherd.rollbackDBTransaction();
	myShepherd.closeDBTransaction();
	myShepherd=null;

}
%>


</body>
</html>
