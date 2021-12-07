<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,
org.ecocean.grid.optimization.*,
org.ecocean.cache.*,
org.json.*,
java.nio.file.*,java.util.*, java.io.FileInputStream, 
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

                gpo.getGrothAnalysis().flush();

                // for each ITERATION, the function is EVALUATED n (value below) times. 
                gpo.setMaxIter(1);


                //^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
                // the total number of times the optimizer will call the .value() method
                // also, the max number of "moves" it will make throws an exception if exceeded, 
                // so set to a number it would be unreasonable for it to go to
                gpo.setMaxEval(200);

                gpo.setParameterScaling(new double[] {1.0, 100.0, 1.0, 100.0, 1.0});

                gpo.setUpperBounds(new double[] {0.15, 50.0, 0.9999, 30.0, 0.999});
                gpo.setLowerBounds(new double[] {0.0005, 5.0, 0.85, 5.0, 0.9});
                
                gpo.setInitialGuess(new double[] {0.1, 50.0, 0.9999, 10.0, 0.99});

                // i'm still not 100% on how this works, but higher numbers up complexity and 
                // it has to do with the number of places the optimizer will explore between value A and B.
                // with a 5 variable function the range is 7-21
                gpo.setBOBYQInterpolationPoints(14);

                gpo.getGrothAnalysis().setMaxSpots(32);
                gpo.getGrothAnalysis().useMatchedRanking(true);

                gpo.getGrothAnalysis().normalizeTopN(true);
                //gpo.getGrothAnalysis().numToNormalize(5);

                // min for ranking eval, unless using normalized scores
                gpo.setGoalTypeAsMax();

                //^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
                // comparisons for ranked matching function
                gpo.getGrothAnalysis().setNumComparisonsEach(50);
                // num comparisons for matched ranking function.. 
                // evals per method call are matchedRankEvals * numComparisons each
                gpo.getGrothAnalysis().setMatchedRankEvalsEach(20);
                //^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

                //gpo.getGrothAnalysis().useWeightsForTargetScore(true, 100, 0.1);

                System.out.println("Trying to optimize parameters...");

                //is a double[] 
                result = gpo.doOptimize();

                // *** This WILL work no matter how you optimize *** the int it takes is how many good & bad comparisons
                // you want to end up in the csv file that goes to /wildbook_data_dir/optimization
                //gpo.writeResultsToFile(500);
                        

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
