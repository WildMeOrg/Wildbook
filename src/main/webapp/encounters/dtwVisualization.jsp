<%@ page contentType="text/html; charset=utf-8" 
	language="java" 
	import="java.awt.geom.Line2D,com.reijns.I3S.*,com.reijns.I3S.Point2D,org.ecocean.servlet.ServletUtilities,org.ecocean.grid.*,java.awt.Dimension,org.ecocean.*, org.ecocean.servlet.*, java.util.*,javax.jdo.*,java.io.File" %>

<script type="text/javascript" src="https://www.google.com/jsapi"></script>
    <script type="text/javascript">

    google.load('visualization', '1.1', {packages: ['line', 'corechart']});
    google.setOnLoadCallback(drawChart);

      // Callback that creates and populates a data table,
      // instantiates the pie chart, passes in the data and
      // draws it.
      function drawChart() {

        // Create the data table.
        var data = new google.visualization.DataTable();
      	data.addColumn('number', 'x');
      	data.addColumn('number', "y");
        
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
  

  
  
  Properties encprops = ShepherdProperties.getProperties("encounter.properties", langCode, context);
//handle translation

  
if(theEnc.getRightSpots()!=null){
	
	EncounterLite enc=new EncounterLite(theEnc);
	Point2D[] newEncControlSpots = new Point2D[3];
	SuperSpot[] newspotsTemp = new SuperSpot[0];
	newspotsTemp = (SuperSpot[]) enc.getRightSpots().toArray(newspotsTemp);
    newEncControlSpots = enc.getThreeRightFiducialPoints();
    
    Line2D.Double newLeftLine=new Line2D.Double(newEncControlSpots[0].getX(),newEncControlSpots[0].getY(),newEncControlSpots[1].getX(),newEncControlSpots[1].getY());
    Line2D.Double newRightLine=new Line2D.Double(newEncControlSpots[1].getX(),newEncControlSpots[1].getY(),newEncControlSpots[2].getX(),newEncControlSpots[2].getY());

	
	int newSpotsLength = newspotsTemp.length;
	Point2D[] newOrigEncounterSpots = new Point2D[newSpotsLength];
	Point2D[] newEncounterSpots = new Point2D[newSpotsLength];
    for (int i = 0; i < newSpotsLength; i++) {
      newEncounterSpots[i] = new Point2D(newspotsTemp[i].getTheSpot().getCentroidX(), newspotsTemp[i].getTheSpot().getCentroidY());
    }
    for (int z = 0; z < newOrigEncounterSpots.length; z++) {
        newOrigEncounterSpots[z] = new Point2D(newEncounterSpots[z].getX(), newEncounterSpots[z].getY());
      }
	
	FingerPrint newPrint = new FingerPrint(newOrigEncounterSpots, newEncounterSpots, newEncControlSpots);
    
	int sizeNewPrint=newPrint.fpp.length;
	
	  double newHighestControlSpot=newEncControlSpots[0].getY();
	    if(newEncControlSpots[2].getY()>newHighestControlSpot){newHighestControlSpot=newEncControlSpots[2].getY();}
	    
	    for (int t = 0; t < sizeNewPrint; t++) {
	      double myX=(newPrint.fpp[t].getX()-newEncControlSpots[0].getX())/(newEncControlSpots[2].getX()-newEncControlSpots[0].getX());
	      double myY=0;
	      
	      if(myX<=newEncControlSpots[1].getX()){
	        //myY=newLeftLine.ptLineDist(new java.awt.geom.Point2D.Double(newPrint.fpp[t].getX(),newPrint.fpp[t].getY()))/(newHighestControlSpot-newEncControlSpots[1].getY()); 
	        //double s = (newLeftLine.y2 - newLeftLine.y1) * newPrint.fpp[t].getX() + (newLeftLine.x1 - newLeftLine.x2) * newPrint.fpp[t].getY() + (newLeftLine.x2 * newLeftLine.y1 - newLeftLine.x1 * newLeftLine.y2);
	        myY=(newPrint.fpp[t].getY()-newEncControlSpots[1].getY())/(newHighestControlSpot-newEncControlSpots[1].getY());
	        
	        //myY=amplifyY(myY,s);
	      }
	      else{
	        //myY=newRightLine.ptLineDist(new java.awt.geom.Point2D.Double(newPrint.fpp[t].getX(),newPrint.fpp[t].getY()))/(newHighestControlSpot-newEncControlSpots[1].getY()); 
	        //double s = (newRightLine.y2 - newRightLine.y1) * newPrint.fpp[t].getX() + (newRightLine.x1 - newRightLine.x2) * newPrint.fpp[t].getY() + (newRightLine.x2 * newRightLine.y1 - newRightLine.x1 * newRightLine.y2);
	        myY=(newPrint.fpp[t].getY()-newEncControlSpots[1].getY())/(newHighestControlSpot-newEncControlSpots[1].getY());
	        
	        //myY=amplifyY(myY,s);
	        
	        
	      }
	      %>
	      
	      [<%=myX%>,<%=myY%>],
	      <%
	      
	      
	      //System.out.println("     myY new distance to line is: "+myY);
	      //b1.add(myX,myY);     
	    }
	
	
	
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

]);

        // Set chart options
        var options = {'title':'Render Fluke',
                       'width':400,
                       'height':300};

        // Instantiate and draw our chart, passing in some options.
        var chart = new google.visualization.LineChart(document.getElementById('chart_div'));
        chart.draw(data, options);
      }
    </script>

<div id="chart_div"></div>
