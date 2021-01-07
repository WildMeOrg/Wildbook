<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,org.ecocean.grid.*,java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>

<%

String context="context0";
context=ServletUtilities.getContext(request);

	//Shepherd myShepherd=new Shepherd(context);

// pg_dump -Ft sharks > sharks.out

//pg_restore -d sharks2 /home/webadmin/sharks.out


%>

<html>
<head>
<title>Groth Tuner</title>

</head>


<body>

<%

//String context="context0";
context=ServletUtilities.getContext(request);

	Shepherd myShepherd=new Shepherd(context);
	myShepherd.beginDBTransaction();
	try{
		Encounter enc1=myShepherd.getEncounter(request.getParameter("enc1"));
		Encounter enc2=myShepherd.getEncounter(request.getParameter("enc2"));
		EncounterLite el1=new EncounterLite(enc1);
		EncounterLite el2=new EncounterLite(enc2);
		
		GridManager gm=new GridManager();



      out.write("\r\n");
      out.write("\r\n");
      out.write("<html>\r\n");
      out.write("<head>\r\n");
      out.write("<title>Tuner: ");
      out.print(enc1.getCatalogNumber() );
      out.write(" vs. ");
      out.print(enc2.getCatalogNumber() );
      out.write("</title>\r\n");
      out.write("\r\n");
      out.write("</head>\r\n");
      out.write("\r\n");
      out.write("\r\n");
      out.write("<body>\r\n");
      out.write("\r\n");
      out.write("<table>\r\n");
      out.write("<tr><td>Score</td><td>epsilon</td><td>R</td><td>Sizelim</td><td>logM</td><td>Fraction Matched</td></tr>\r\n");


double epsilon=0.1;
int R=5;
double Sizelim=1.0;
double maxTriangleRotation=10;
double C=0.99;

while(epsilon>0){
	R=10;
	while(R<100){
		Sizelim=1.0;
		while(Sizelim>0){
			
			//finally let's do some Groth!
			SuperSpot[] newspotsTemp = new SuperSpot[0];
			newspotsTemp=(SuperSpot[])el2.getRightSpots().toArray(newspotsTemp);
			System.out.println("Newspotstemp is: "+newspotsTemp.toString());
			//MatchObject mo=el1.getPointsForBestMatch(newspotsTemp, epsilon, R, Sizelim, maxTriangleRotation, C, true, false);
			MatchObject mo=EncounterLite.getModifiedGroth4Flukes(el1, el2, epsilon, R, Sizelim, maxTriangleRotation, C, true);
			String adjustedMatchValueString = (new Double(mo.adjustedMatchValue)).toString();
			String finalscore2 = (new Double(mo.matchValue * mo.adjustedMatchValue)).toString();

			
      out.write("\r\n");
      out.write("\t\t\t\r\n");
      out.write("\t\t\t<tr><td>");
      out.print(finalscore2 );
      out.write("</td><td>");
      out.print(epsilon );
      out.write("</td><td>");
      out.print(R );
      out.write("</td><td>");
      out.print(Sizelim );
      out.write("</td><td>");
      out.print(mo.getLogMStdDev() );
      out.write("</td><td>");
      out.print(adjustedMatchValueString );
      out.write("</td></tr>\r\n");
      out.write("\t\t\t\r\n");
      out.write("\t\t\t\r\n");
      out.write("\t\t\t");

			Sizelim=Sizelim-0.1;
		}
	
		R=R+5;
	}
	
	epsilon=epsilon-0.001;
}






      out.write("\r\n");
      out.write("</table>\r\n");
      out.write("\r\n");
      out.write("<p>Done successfully!</p>\r\n");
      out.write("\r\n");
      out.write("\r\n");

} 
catch(Exception ex) {

	System.out.println("!!!An error occurred on page fixSomeFields.jsp. The error was:");
	ex.printStackTrace();
	//System.out.println("fixSomeFields.jsp page is attempting to rollback a transaction because of an exception...");


}
finally{
	myShepherd.rollbackDBTransaction();
	myShepherd.closeDBTransaction();
	myShepherd=null;
}

%>



</body>
</html>
