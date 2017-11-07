<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,org.joda.time.*,java.text.DateFormat,java.text.*,
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
<h1>Recent Encounter Rodeo</h1>

<ul>
<%

myShepherd.beginDBTransaction();

int numFixes=0;
// String rootDir;

try {
	if (myShepherd.isEncounter("4b05efec-2985-44e8-b2f2-ace1e56c6e16")) {
		Encounter missingEnc = myShepherd.getEncounter("4b05efec-2985-44e8-b2f2-ace1e56c6e16");
		out.println("+                      ****++++ Here's the one that should show up: "+String.valueOf(missingEnc.getDWCDateAddedLong()));
	    out.println("\n****++++ Date String: "+missingEnc.getDWCDateAdded());
	    out.println("\n****++++ Date LastModified: "+missingEnc.getDWCDateLastModified()+"                      +");	
	}
	// rootDir = request.getSession().getServlet().getServletContext().getRealPath("/");
	// String baseDir = ServletUtilities.dataDir(context, rootDir).replaceAll("dev_data_dir", "wildbook_data_dir");
   int num = 1;
   //ArrayList<Encounter> encs = myShepherd.getMostRecentIdentifiedEncountersByDate(num);
   Iterator<Encounter> encs = myShepherd.getAllEncountersNoQuery();
   //out.println("\n	 ******** Array size? Should be "+num+"... : "+encs.size());
   Encounter enc = null;
   while (encs.hasNext()){
     enc = encs.next();
     System.out.println("Num: "+num);
     num++;	  
     out.println("\n **** Current Date Long: "+String.valueOf(enc.getDWCDateAddedLong()));
     out.println("\n **** Current Date String: "+enc.getDWCDateAdded());
     out.println("\n **** Current Date LastModified: "+enc.getDWCDateLastModified());
     String format = null;
     if (enc.getDWCDateAdded().length()>11) {
     	format = "yyyy-MM-dd kk:mm:ss";
     } else {
	format = "yyyy-MM-dd";	
     }
	
     DateFormat df = new SimpleDateFormat(format);
     Date date = df.parse(enc.getDWCDateAdded());
     DateTime dt = new DateTime(date);
     out.println("----------New DateTime Made with Date String? "+dt.getYear()+"-"+dt.getMonthOfYear()+"-"+dt.getDayOfMonth());
     out.println("----------Millis From New DateTime? "+dt.getMillis());
     //System.out.println(" **** here is what i think location is: " + enc.getLocation());
     DateTime now = new DateTime();
     out.println("\n Right Now  : "+now.getMillis());
     out.println("\n Value To Change  : "+enc.getDWCDateAddedLong());
     boolean commitSwitch = false;
     if (commitSwitch) {
	     myShepherd.beginDBTransaction();
	     if (dt!=null&&dt.getMillis()<now.getMillis()) {
		     enc.setDWCDateAdded(dt.getMillis());
		     myShepherd.commitDBTransaction();
	     }
	     out.println(" New Value : "+enc.getDWCDateAddedLong());    	 
     }
       //String locTemp = "";
       //try {
       //  props=ShepherdProperties.getProperties("submitActionClass.properties", "",context);
       //	 String location = enc.getLocation().toLowerCase();
       // Enumeration m_enum = props.propertyNames();
       // while (m_enum.hasMoreElements()) {
       //    String aLocationSnippet = ((String) m_enum.nextElement()).trim();
       //    if (location.indexOf(aLocationSnippet) != -1) {
       //      locCode = props.getProperty(aLocationSnippet);
       //    }
       //    if (locCode != null && locCode != "" && locCode != "None") {
       // 	   try {
       //          myShepherd.beginDBTransaction();   		   
       // 
       //     	   myShepherd.commitDBTransaction();    
       //     	   System.out.println(" **** New Location ID? : " + enc.getLocationID()); 
       //  	   } catch (Exception e) {
       //  		   System.out.println(" Failed to change location ID! "); 
       // 	   	   e.printStackTrace();
       // 	   }
       //    } else {
       // 	   System.out.println(" **** Hmm, didn't like locCode : " + locCode); 
       //    }
       //  }
       //} catch (Exception props_e) {
       //  props_e.printStackTrace();
       //  System.out.println("!!!! Threw an Exception trying tyo get the props !!!!");
       //}
       //	   numFixes++;     
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
</body>
</html>
