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


<%
        String scores = null;
        double[] result = new double[3];

        GrothParameterOptimizer gpo = null;

	try {

		gpo = new GrothParameterOptimizer();
                gpo.setMaxIter(1000);
                gpo.setMaxEval(250);
                //gpo.setInitialGuess(new double[] {0.1, 50.0, 0.9999, 10.0, 0.99});
                //double[] upperBounds = new double[] {0.15, 50.0, 0.9999, 30.0, 0.999};
                //double[] lowerBounds = new double[] {0.0005, 5.0, 0.85, 5.0, 0.9};

                //Parameter order: {epsilon, R, sizeLim, maxTriangleRotation, C} 
                // I think that parameters >1 are not getting adjusted properly esp. R and rotation. 
                // this kinda makes sense as we are not specifying 'steps'


                // you have to do this first because i haven't made it so you don't have to do it first yet
                gpo.setParameterScaling(new double[] {1.0, 100.0, 1.0, 100.0, 1.0});

                gpo.setUpperBounds(new double[] {0.15, 50.0, 0.9999, 30.0, 0.999});
                gpo.setLowerBounds(new double[] {0.0005, 5.0, 0.85, 5.0, 0.9});

                gpo.setInitialGuess(new double[] {0.0005, 5.0, 0.85, 5.0, 0.9});
                gpo.setBOBYQInterpolationPoints(20);
                gpo.getGrothAnalysis().setNumComparisonsEach(75);
                gpo.getGrothAnalysis().setMaxSpots(17);
                //gpo.getGrothAnalysis().useWeightsForTargetScore(true, 100, 0.1);

                System.out.println("Trying to optimize parameters...");

                //is a double[] 
                result = gpo.doOptimize();

                System.out.println("Done optimizing????");
	
	} catch (Exception e) {

		e.printStackTrace();

	} finally {

                //scores = Arrays.toString(scores);

                //scores = gpo.getGrothAnalysis().getMatchScores();

                myShepherd.closeDBTransaction();
	}
%>  

<h1>Testing Groth Optimization</h1>

<script>





</script>


<p><%=scores%></p>


</body>
</html>
