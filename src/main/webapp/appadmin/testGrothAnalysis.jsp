<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,
org.ecocean.cache.*,
org.json.*,
java.io.*,java.util.*, java.io.FileInputStream, 
java.util.concurrent.ThreadPoolExecutor,
java.io.File, java.io.FileNotFoundException, 
org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, 
java.lang.StringBuffer, java.util.Vector, java.util.Iterator, 
java.lang.NumberFormatException,
org.ecocean.grid.optimization.*
"
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
<title>Test Groth Analysis</title>
</head>


<body>

<h1>Testing Groth Analysis</h1>


<%

	try{
		

		/*
		  //Modified Groth algorithm parameters
  private String epsilon = "0.01";
  private String R = "50";
  private String Sizelim = "0.9999";
  private String maxTriangleRotation = "10";
  private String C = "0.99";
  private String secondRun = "true";
		*/
		
		if(request.getParameter("flush")!=null){
			GrothAnalysis.flush();
		}
		
		int numMatches=500;
		int numMatchedComparisons=1000;
		if(request.getParameter("numMatches")!=null){
			try{
				numMatches=(new Integer(request.getParameter("numMatches").trim())).intValue();
			}
			catch(Exception e){e.printStackTrace();}
		}
		if(request.getParameter("numMatchedComparisons")!=null){
			try{
				numMatchedComparisons=(new Integer(request.getParameter("numMatchedComparisons").trim())).intValue();
			}
			catch(Exception e){e.printStackTrace();}
		}
		
		try{
			GrothAnalysis.flush();
			Integer result=GrothAnalysis.getMatchedRankSum(numMatchedComparisons,numMatches, 0.01, 50.0, 0.9999, 10.0, 0.99, "left", 50, false,1,0.0,null);
			%>
			
			<p>Result is: <%=result %></p> 
			<%	
		}
		catch(Exception e){
			e.printStackTrace();
			%>
			<p>Hit an error.</p>
			<%
		}
		
	
		
		
		
	
	}
	catch(Exception e){
		e.printStackTrace();
	}
	finally{

	
	}


%>

</body>
</html>
