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

// pg_dump -Ft sharks > sharks.out

//pg_restore -d sharks2 /home/webadmin/sharks.out


%>

<html>
<head>
<title>Fix Some Fields</title>

</head>


<body>
<p>Spurious encounters to remove.</p>
<ul>
<%



//build queries

int numFixes=0;


try{


	String rootDir = getServletContext().getRealPath("/");
	String baseDir = ServletUtilities.dataDir(context, rootDir).replaceAll("dev_data_dir", "caribwhale_data_dir");
	
	
	

	Iterator allEncs=myShepherd.getAllEncounters();
	


while(allEncs.hasNext()){
	
	myShepherd.beginDBTransaction();
	Encounter enc2remove=(Encounter)allEncs.next();
	boolean locked=false;

	if((enc2remove.getIndividualID()==null)||(enc2remove.getIndividualID().toLowerCase().trim().equals("unassigned"))){
		numFixes++;
		
		
		///START
		
		
      	if (myShepherd.getOccurrenceForEncounter(enc2remove.getCatalogNumber())!=null) {
	        String old_name = myShepherd.getOccurrenceForEncounter(enc2remove.getCatalogNumber()).getOccurrenceID();
	        boolean wasRemoved = false;
	        String name_s = "";
	        try {
	          Occurrence removeFromMe = myShepherd.getOccurrenceForEncounter(enc2remove.getCatalogNumber());
	          name_s = removeFromMe.getOccurrenceID();
	          while (removeFromMe.getEncounters().contains(enc2remove)) {
	            removeFromMe.removeEncounter(enc2remove);
	          }
	
	
	          enc2remove.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>" + "Removed from occurrence " + old_name + ".</p>");
	          removeFromMe.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>" + "Removed encounter " + request.getParameter("number") + ".</p>");
	          enc2remove.setOccurrenceID(null);
	          
	          if (removeFromMe.getEncounters().size() == 0) {
	            myShepherd.throwAwayOccurrence(removeFromMe);
	            wasRemoved = true;
	          }
	
	        } 
	        catch (java.lang.NullPointerException npe) {
	          npe.printStackTrace();
	          locked = true;
	          //myShepherd.rollbackDBTransaction();
	
	        } 
	        catch (Exception le) {
	          le.printStackTrace();
	          locked = true;
	          //myShepherd.rollbackDBTransaction();
	
	        }
	        if(!locked){myShepherd.commitDBTransaction();}
	        else{myShepherd.rollbackDBTransaction();}
	        myShepherd.beginDBTransaction();
	
			
			//END
			
			
		
	}
		
		myShepherd.throwAwayEncounter(enc2remove);
		myShepherd.commitDBTransaction();
		
	
} //end if
else{myShepherd.rollbackDBTransaction();}
	
	
} //end while

} //end try
catch(Exception e){e.printStackTrace();}

myShepherd.closeDBTransaction();

%>




</ul>
<p>Done successfully: <%=numFixes %> encounters deleted</p>
</body>
</html>
