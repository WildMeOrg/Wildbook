<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*, org.ecocean.media.*,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>

<%

String context="context0";
context=ServletUtilities.getContext(request);

Shepherd myShepherd=new Shepherd(context);



%>

<html>
<head>
<title>Fix Some Fields</title>

</head>


<body>
<h1>Fixing some fields</h1>
<h2>
<% 
File testingRootDir = new File();
out.println("Here is the root file dir for Tomcat! : "+testingRootDir.getAbsolutePath());
%>
</h2>
<ul>
<%

myShepherd.beginDBTransaction();

int numFixes=0;
// String rootDir;

try {

	// rootDir = request.getSession().getServlet().getServletContext().getRealPath("/");
	// String baseDir = ServletUtilities.dataDir(context, rootDir).replaceAll("dev_data_dir", "wildbook_data_dir");

    Iterator allEncounters=myShepherd.getAllEncountersNoQuery();

    boolean committing=true;

   Encounter enc;
   Properties props = new Properties();
   
   while(allEncounters.hasNext()){
  
     enc=(Encounter)allEncounters.next();
     String locCode = "";
     System.out.println(" **** here is what i think locationID is: " + enc.getLocationID());
     System.out.println(" **** here is what i think location is: " + enc.getLocation());
     
     
       String locTemp = "";
       try {
         props=ShepherdProperties.getProperties("submitActionClass.properties", "",context);
		 String location = enc.getLocation().toLowerCase();
         Enumeration m_enum = props.propertyNames();
         while (m_enum.hasMoreElements()) {
           String aLocationSnippet = ((String) m_enum.nextElement()).trim();
           if (location.indexOf(aLocationSnippet) != -1) {
             locCode = props.getProperty(aLocationSnippet);
           }
           if (locCode != null && locCode != "" && locCode != "None") {
        	   try {
    	           myShepherd.beginDBTransaction();   		   
        		   enc.setLocationID(locCode);
            	   myShepherd.commitDBTransaction();    
            	   System.out.println(" **** New Location ID? : " + enc.getLocationID()); 
        	   } catch (Exception e) {
        		   System.out.println(" Failed to change location ID! "); 
        	   	   e.printStackTrace();
        	   }
           } else {
        	   System.out.println(" **** Hmm, didn't like locCode : " + locCode); 
           }
         }
       } catch (Exception props_e) {
         props_e.printStackTrace();
         System.out.println("!!!! Threw an Exception trying tyo get the props !!!!");
       }
	   numFixes++;     
   }
   myShepherd.closeDBTransaction();

}
catch(Exception e){
	myShepherd.rollbackDBTransaction();
} finally{
	myShepherd.closeDBTransaction();
}

%>

</ul>
<p>Done successfully: <%=numFixes %></p>

</body>
</html>
