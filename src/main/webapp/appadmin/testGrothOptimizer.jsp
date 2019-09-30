<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,
org.ecocean.grid.optimization.*,
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

Shepherd myShepherd=new Shepherd(context);
myShepherd.setAction("testGrothAnalysis.jsp");

int numFixes=0;


%>

<html>
<head>
<title>Test Groth Optimization</title>
</head>


<body>

<h1>Testing Groth Optimization</h1>


<%
    double[] result = new double[4];

	try {
		GrothParameterOptimizer gpo = new GrothParameterOptimizer();
                gpo.setMaxIter(1200);
                gpo.setMaxEval(1200);

                gpo.getGrothAnalysis().setNumComparisonsEach(250);
                gpo.getGrothAnalysis().setMaxSpots(21);

                System.out.println("Trying to optimize parameters...");
                result = gpo.doOptimize();
                System.out.println("Done optimizing????");
	
	} catch (Exception e) {

		e.printStackTrace();

	} finally {
                myShepherd.closeDBTransaction();
	}
%>  

</body>
</html>
