<%@ page contentType="text/html; charset=utf-8" 
	language="java" 
	import="java.awt.geom.Line2D,com.reijns.I3S.*,com.reijns.I3S.Point2D,org.ecocean.servlet.ServletUtilities,org.ecocean.grid.*,java.awt.Dimension,org.ecocean.*, org.ecocean.servlet.*, java.util.*,javax.jdo.*,java.io.File" %>

<script type="text/javascript" src="https://www.google.com/jsapi"></script>
    <script type="text/javascript">
    
    var chartWidth=810;
    var chartHeight=500;
    

    google.load('visualization', '1.1', {packages: ['line', 'corechart']});
    google.setOnLoadCallback(drawChart);

      // Callback that creates and populates a data table,
      // instantiates the pie chart, passes in the data and
      // draws it.
      function drawChart() {

        // Create the data table.
        var data = new google.visualization.DataTable();
      	data.addColumn('number', 'x');
      	data.addColumn('number', 'y');
      	data.addColumn({type: 'string', role: 'annotation'});
        
      	data.addRows([
         
       

<%

String context="context0";
context=ServletUtilities.getContext(request);
if(CommonConfiguration.useSpotPatternRecognition(context)){
	
	
String encNum = request.getParameter("encounterNumber");



Shepherd myShepherd = new Shepherd(context);
		  
//let's set up references to our file system components
		 
			String rootWebappPath = getServletContext().getRealPath("/");
		  File webappsDir = new File(rootWebappPath).getParentFile();
		  File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));
		  File encountersDir=new File(shepherdDataDir.getAbsolutePath()+"/encounters");
		  //File encounterDir = new File(encountersDir, encNum);
		
		
		File encounterDir = new File(Encounter.dir(shepherdDataDir, encNum));
		
try {
	
  	

	
	
	
  //get the encounter number
 
  //set up the JDO pieces and Shepherd
  myShepherd.beginDBTransaction();
  Encounter theEnc=myShepherd.getEncounter(encNum);

  //String langCode = "en";

  String langCode=ServletUtilities.getLanguageCode(request);
  

  
  Point2D[] newEncControlSpots=null;
  
  Properties encprops = ShepherdProperties.getProperties("encounter.properties", langCode, context);
//handle translation

  
if((theEnc.getRightSpots()!=null)||(theEnc.getSpots()!=null)){
	
	//EncounterLite enc=new EncounterLite(theEnc);
	// = new Point2D[3];
	//SuperSpot[] newspotsTemp = new SuperSpot[0];
	//newspotsTemp = (SuperSpot[]) enc.getRightSpots().toArray(newspotsTemp);
	ArrayList<SuperSpot> spots=new ArrayList<SuperSpot>();
	if(theEnc.getSpots()!=null){spots.addAll(theEnc.getSpots());}
	if(theEnc.getRightSpots()!=null){spots.addAll(theEnc.getRightSpots());}
    newEncControlSpots = theEnc.getThreeLeftFiducialPoints();
    
    //Line2D.Double newLeftLine=new Line2D.Double(newEncControlSpots[0].getX(),newEncControlSpots[0].getY(),newEncControlSpots[1].getX(),newEncControlSpots[1].getY());
    //Line2D.Double newRightLine=new Line2D.Double(newEncControlSpots[1].getX(),newEncControlSpots[1].getY(),newEncControlSpots[2].getX(),newEncControlSpots[2].getY());

    //double rightmostSpot=theEnc.getRightmostRightSpot();
	//double rightHighestSpot=theEnc.getHighestRightSpot();
	
	int newSpotsLength = spots.size();
 
	    for (int t = 0; t < newSpotsLength; t++) {
	    %>  
	      [<%=spots.get(t).getTheSpot().getCentroidX() %>,(<%=spots.get(t).getTheSpot().getCentroidY() %>),'<%=spots.get(t).getType() %>'],
	      <%
	    }

	%>
	

	]);

	        // Set chart options
	        var options = {'title':'Render Fluke',
	                       'width':chartWidth,
	                       'height':chartHeight,
	                       'pointSize': 5
	                       };

	        // Instantiate and draw our chart, passing in some options.
	        var chart = new google.visualization.LineChart(document.getElementById('chart_div'));
	        chart.draw(data, options);
	      }
	    </script>
	    
	    <p>Left control spots</p>
	    <%
	    if(newEncControlSpots!=null){
	    	for(int i=0;i<newEncControlSpots.length;i++){
	    		Point2D theSpot=newEncControlSpots[i];
	    %>
	    
	    <%=theSpot.getX() %>,<%=theSpot.getY() %><br />
	    
	    <%
	    	}
	    }
	    %>
	    <p>Right control spots</p>
	    <%
	    newEncControlSpots=theEnc.getThreeRightFiducialPoints();
	    if(newEncControlSpots!=null){
	    	for(int i=0;i<newEncControlSpots.length;i++){
	    		Point2D theSpot=newEncControlSpots[i];
	    %>
	    
	    <%=theSpot.getX() %>,<%=theSpot.getY() %><br />
	    
	    <%
	    }
	    }
	    %>


	<%
}


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


<div id="chart_div"></div>


