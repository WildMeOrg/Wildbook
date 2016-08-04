<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,org.ecocean.media.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>

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
<p>Testing the python script.</p>
<ul>
<%

myShepherd.beginDBTransaction();

//build queries

int numFixes=0;
String maSetId = "603fd5ce-dbbf-4025-ba25-670219d043b6";

try {

  MediaAssetSet maSet = myShepherd.getMediaAssetSet("603fd5ce-dbbf-4025-ba25-670219d043b6");

  /*
  for (MediaAsset ma : maSet.getMediaAssets()) {
    ma.setUserLatitude(0.0);
    ma.setUserLongitude(0.0);
    myShepherd.commitDBTransaction();
    myShepherd.beginDBTransaction();
  }*/


  String currentPath = Cluster.runJonsScript(maSet.getMediaAssets(), myShepherd);

  List<Occurrence> occurrences = Cluster.runJonsClusterer();

  String command = Cluster.buildCommand(maSet.getMediaAssets());

  String output = Cluster.runPythonCommand(command);


  %><li>Current <%=currentPath%></li><%
  %><li>Command = <%=command%></li><%
  %><li>Output = <%=output%></li><%

  try {
    int[] parsedOutput = Cluster.parseJonsOutput(output);
    %><li>parsedOutput = [<%
      for (int elem: parsedOutput) {
        %><%=elem%>, <%
      }
    %>]</li><%
  } catch (Exception ex) {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream ps = new PrintStream(baos);
    ex.printStackTrace(ps);
    ps.close();
    %><li>ERROR ERROR ERROR</li><%
    %><li><%=baos.toString()%></li><%

  }



}
catch (Exception ex) {


	System.out.println("!!!An error occurred on page fixSomeFields.jsp. The error was:");
	ex.printStackTrace();
	myShepherd.rollbackDBTransaction();


}
finally{

	myShepherd.closeDBTransaction();
	myShepherd=null;
}
%>

</ul>
<p>Done successfully</p>
</body>
</html>
