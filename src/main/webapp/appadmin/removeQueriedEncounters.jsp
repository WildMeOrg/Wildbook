<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,
org.ecocean.datacollection.*,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>

<%

String context="context0";
context=ServletUtilities.getContext(request);
Shepherd myShepherd=new Shepherd(context);
myShepherd.setAction("removeQueriedEncounters.jsp");

String commit = request.getParameter("commit");

String limit = request.getParameter("limit");
%>

<html>
<head>
<title>Remove Test Encounters...</title>

</head>

<body>
<ul>
<%

String filter = "this.submitterOrganization == 'Olive Ridley Project' && this.dwcDateAdded.startsWith('2019-09-05')";
Collection c = null;
List<Encounter> encs = null;
try {
    Extent encClass = myShepherd.getPM().getExtent(Encounter.class, true);
    Query acceptedEncounters = myShepherd.getPM().newQuery(encClass, filter);
    c = (Collection) (acceptedEncounters.execute());
    encs = new ArrayList<>(c);          
    //int size=c.size();
    acceptedEncounters.closeAll();
    System.out.println("Hello! I have "+encs.size()+" encounters to delete!");
} catch (Exception e) {
    System.out.println("Exception retrieving encounters...");
    e.printStackTrace();
} 

int count = 0;
try{
    System.out.println("commit = "+commit);
    System.out.println("limit = "+limit);
    for (Encounter enc : encs) {
        count++;
        if (count%100 == 0) {
            System.out.println("[COUNT"+count+"] catalogNumber = "+enc.getCatalogNumber());   
        }
        if ("true".equals(commit)&&limit!=null&&count<Integer.valueOf(limit)) {
            myShepherd.beginDBTransaction();
            System.out.println("Made it in to the delete block!");
            if((enc.getOccurrenceID()!=null)&&(myShepherd.isOccurrence(enc.getOccurrenceID()))) {
                System.out.println("Occurrence... ");
                Occurrence occur=myShepherd.getOccurrence(enc.getOccurrenceID());
                occur.removeEncounter(enc);
                enc.setOccurrenceID(null);
                
                if(occur.getNumberEncounters()==0){
                myShepherd.throwAwayOccurrence(occur);
                }
                myShepherd.commitDBTransaction();
                myShepherd.beginDBTransaction();
            }

            //Set all associated annotations matchAgainst to false
            System.out.println("Dont match me...");
            enc.useAnnotationsForMatching(false);
            
            //break association with User object submitters
            if(enc.getSubmitters()!=null){
                System.out.println("No submitters...");
                enc.setSubmitters(null);
                myShepherd.commitDBTransaction();
                myShepherd.beginDBTransaction();
            }
            
            //break asociation with User object photographers
            if(enc.getPhotographers()!=null){
                enc.setPhotographers(null);
                myShepherd.commitDBTransaction();
                myShepherd.beginDBTransaction();
            }
            //now delete for good
            System.out.println("Actual delete...");
            myShepherd.beginDBTransaction();
            myShepherd.throwAwayEncounter(enc);
            myShepherd.commitDBTransaction();
            System.out.println("Fin!");
        } else {
            break;
        }
    }
    myShepherd.closeDBTransaction();
    System.out.println("FINISHED DELETION!");
} catch (Exception e){
	e.printStackTrace();
	myShepherd.rollbackDBTransaction();
}

%>

</ul>

</body>
</html>

