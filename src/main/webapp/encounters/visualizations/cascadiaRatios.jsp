<%@ page contentType="text/html; charset=utf-8" 
	language="java" 
	import="java.awt.geom.*,
	com.reijns.I3S.*,
	org.apache.commons.math.stat.descriptive.SummaryStatistics,
	java.awt.geom.Point2D.Double,
	org.ecocean.servlet.ServletUtilities,org.ecocean.grid.*,java.awt.Dimension,org.ecocean.*, org.ecocean.servlet.*, java.util.*,javax.jdo.*,
	java.io.File,
	org.ecocean.grid.* ,
	org.ecocean.neural.TrainNetwork,
	java.awt.geom.*,
	java.awt.geom.Point2D.Double"
	%>


<%
int chartWidth=810;
%>
<script type="text/javascript" src="https://www.google.com/jsapi"></script>
    <script type="text/javascript">
    
    var chartWidth=<%=chartWidth %>;
    var chartHeight=500;
    

    google.load('visualization', '1.1', {packages: ['line', 'corechart']});


<%

String context="context0";
context=ServletUtilities.getContext(request);
if(CommonConfiguration.useSpotPatternRecognition(context)){
	
	
String encNum = request.getParameter("enc1");
String encNum2 = request.getParameter("enc2");



Shepherd myShepherd = new Shepherd(context);
		  
//let's set up references to our file system components
		 
			
try {
	
  	

	
	
	
  //get the encounter number
 
  //set up the JDO pieces and Shepherd
  myShepherd.beginDBTransaction();
  Encounter enc1=myShepherd.getEncounter(encNum);
  Encounter enc2=myShepherd.getEncounter(encNum2);
  EncounterLite theEnc=new EncounterLite(enc1);
  EncounterLite theEnc2=new EncounterLite(enc2);
 
      
      
      
      SuperSpot[] oldReferenceSpots=theEnc.getLeftReferenceSpots();
      System.out.println("oldRefSpots size is: "+oldReferenceSpots.length);
      Line2D.Double oldLine=new Line2D.Double(oldReferenceSpots[0].getCentroidX(), oldReferenceSpots[0].getCentroidY(), oldReferenceSpots[1].getCentroidX(), oldReferenceSpots[1].getCentroidY());
      double measure1a=Math.abs(oldReferenceSpots[1].getCentroidX()-oldReferenceSpots[0].getCentroidX());
      double measure2a=Math.abs(oldReferenceSpots[1].getCentroidX()-oldReferenceSpots[9].getCentroidX());
      double measure3a=Math.abs(oldReferenceSpots[1].getCentroidX()-oldReferenceSpots[8].getCentroidX());
      double measure4a=Math.abs(oldReferenceSpots[8].getCentroidX()-oldReferenceSpots[7].getCentroidX());
      double measure5a=Math.abs(oldReferenceSpots[1].getCentroidX()-oldReferenceSpots[6].getCentroidX());
      double measure6a=Math.abs(oldReferenceSpots[6].getCentroidX()-oldReferenceSpots[5].getCentroidX());
      double measure7a=Math.abs(oldReferenceSpots[1].getCentroidX()-oldReferenceSpots[4].getCentroidX());
      double measure8a=Math.abs(oldReferenceSpots[4].getCentroidX()-oldReferenceSpots[3].getCentroidX());
	  double r1a=measure2a/measure4a;
	  double r2a=measure4a/measure6a;
	  double r3a=measure6a/measure8a;
	  double r4a=measure3a/measure4a;
	  double r5a=measure5a/measure6a;
	  double r6a=measure7a/measure8a;
      
      
      
      
      
      
      SuperSpot[] newReferenceSpots=theEnc2.getLeftReferenceSpots();
      System.out.println("newRefSpots size is: "+newReferenceSpots.length);
      
      Line2D.Double newLine=new Line2D.Double(newReferenceSpots[0].getCentroidX(), newReferenceSpots[0].getCentroidY(), newReferenceSpots[1].getCentroidX(), newReferenceSpots[1].getCentroidY());
      double measure1b=Math.abs(newReferenceSpots[1].getCentroidX()-newReferenceSpots[0].getCentroidX());
      double measure2b=Math.abs(newReferenceSpots[1].getCentroidX()-newReferenceSpots[9].getCentroidX());
      double measure3b=Math.abs(newReferenceSpots[1].getCentroidX()-newReferenceSpots[8].getCentroidX());
      double measure4b=Math.abs(newReferenceSpots[8].getCentroidX()-newReferenceSpots[7].getCentroidX());
      double measure5b=Math.abs(newReferenceSpots[1].getCentroidX()-newReferenceSpots[6].getCentroidX());
      double measure6b=Math.abs(newReferenceSpots[6].getCentroidX()-newReferenceSpots[5].getCentroidX());
      double measure7b=Math.abs(newReferenceSpots[1].getCentroidX()-newReferenceSpots[4].getCentroidX());
      double measure8b=Math.abs(newReferenceSpots[4].getCentroidX()-newReferenceSpots[3].getCentroidX());
	  double r1b=measure2b/measure4b;
	  double r2b=measure4b/measure6b;
	  double r3b=measure6b/measure8b;
	  double r4b=measure3b/measure4b;
	  double r5b=measure5b/measure6b;
	  double r6b=measure7b/measure8b;
	  
	  double combinedr1=Math.abs(1-r1a/r1b);
	  double combinedr2=Math.abs(1-r2a/r2b);
	  double combinedr3=Math.abs(1-r3a/r3b);
	  double combinedr4=Math.abs(1-r4a/r4b);
	  double combinedr5=Math.abs(1-r5a/r5b);
	  double combinedr6=Math.abs(1-r6a/r6b);
	  
	  double totalDiff=combinedr1+combinedr2+combinedr3+combinedr4+combinedr5+combinedr6;
	  

  String langCode=ServletUtilities.getLanguageCode(request);
  

  
  //java.awt.geom.Point2D.Double[] theEnc2ControlSpots=null;
  
  Properties encprops = ShepherdProperties.getProperties("encounter.properties", langCode, context);
//handle translation

  

	



	    
	    
	    
	

  




	    String enc1MI="Unknown";
	    if(enc1.getIndividualID()!=null){enc1MI=enc1.getIndividualID();}
	    String enc2MI="Unknown";
	    if(enc2.getIndividualID()!=null){enc2MI=enc2.getIndividualID();}
	    
	
    
    
    %>
	   </script> 
	    <h2>Comparison: <a href="encounter.jsp?number=<%=enc1.getCatalogNumber() %>"><%=enc1MI %></a> vs <a href="encounter.jsp?number=<%=enc2.getCatalogNumber() %>"><%=enc2MI %></a></h2>

<p><i>Lower is better.</i></p>

	    
	    <table><tr>
	    
	    

<td valign="top">

<p>Cascadia total ratio diff: <%=totalDiff %></p>


<%
}	//end try
catch(Exception e) {
  e.printStackTrace();
}
finally {
  myShepherd.rollbackDBTransaction();
  myShepherd.closeDBTransaction();
}

}
%>
</td></tr></table>

