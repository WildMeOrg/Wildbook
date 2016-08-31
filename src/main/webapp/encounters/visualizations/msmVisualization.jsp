<%@ page contentType="text/html; charset=utf-8" 
	language="java" 
	import="java.awt.geom.*,
	com.reijns.I3S.*,
	org.apache.commons.math.stat.descriptive.SummaryStatistics,
	java.awt.geom.Point2D.Double,
	org.ecocean.servlet.ServletUtilities,org.ecocean.grid.*,java.awt.Dimension,org.ecocean.*, org.ecocean.servlet.*, java.util.*,javax.jdo.*,
	java.io.File,
	com.fastdtw.timeseries.TimeSeriesBase.*,
	com.fastdtw.dtw.*,
	com.fastdtw.util.Distances,
	com.fastdtw.timeseries.TimeSeriesBase.Builder,
	com.fastdtw.timeseries.*,
	org.ecocean.grid.* ,
	org.ecocean.grid.msm.*,
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
    google.setOnLoadCallback(drawChart);

      // Callback that creates and populates a data table,
      // instantiates the pie chart, passes in the data and
      // draws it.
      function drawChart() {

        // Create the data table.
        var data = new google.visualization.DataTable();
      	data.addColumn('number', 'x');
      	data.addColumn('number', 'y');
        
      	data.addRows([
         
       

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
  EncounterLite theEnc=new EncounterLite(enc2);
  EncounterLite theEnc2=new EncounterLite(enc1);

  //start code copied from MSM.java

      
      ArrayList<SuperSpot> oldSpots=theEnc.getSpots();
  if(theEnc.getRightSpots()!=null){
  	oldSpots.addAll(theEnc.getRightSpots());
  }
      Collections.sort(oldSpots, new XComparator());
      
      //let's prefilter old spots for outlies outside the bounds
      
      for(int i=0;i<oldSpots.size();i++){
    	  SuperSpot theSpot=oldSpots.get(i);
    	  if(theSpot.getCentroidX()<=theEnc.getLeftReferenceSpots()[0].getCentroidX()){
    		  oldSpots.remove(i);
    		  i--;
    	  }
    	  if(theSpot.getCentroidX()>=theEnc.getLeftReferenceSpots()[2].getCentroidX()){
    		  oldSpots.remove(i);
    		  i--;
    	  }
      }
      
      
      int numOldSpots=oldSpots.size();
      double[] OLD_VALUES=new double[numOldSpots];
      double[] NEW_VALUES=new double[numOldSpots];
      
      SuperSpot[] oldReferenceSpots=theEnc.getLeftReferenceSpots();
      Line2D.Double oldLine=new Line2D.Double(oldReferenceSpots[0].getCentroidX(), oldReferenceSpots[0].getCentroidY(), oldReferenceSpots[2].getCentroidX(), oldReferenceSpots[2].getCentroidY());
      double oldLineWidth=Math.abs(oldReferenceSpots[2].getCentroidX()-oldReferenceSpots[0].getCentroidX());
      if(EncounterLite.isDorsalFin(theEnc)){
    	  oldLineWidth=Math.abs(theEnc.getLeftmostSpot()-theEnc.getRightmostSpot());
      }
      
      System.out.println(" Old line width is: "+oldLineWidth);
      
      SuperSpot[] newReferenceSpots=theEnc2.getLeftReferenceSpots();
      Line2D.Double newLine=new Line2D.Double(newReferenceSpots[0].getCentroidX(), newReferenceSpots[0].getCentroidY(), newReferenceSpots[2].getCentroidX(), newReferenceSpots[2].getCentroidY());
      double newLineWidth=Math.abs(newReferenceSpots[2].getCentroidX()-newReferenceSpots[0].getCentroidX());
      if(EncounterLite.isDorsalFin(theEnc2)){
    	  newLineWidth=Math.abs(theEnc2.getLeftmostSpot()-theEnc2.getRightmostSpot());
   	   }
      System.out.println(" New line width is: "+newLineWidth);
      
      //first populate OLD_VALUES - easy
      
      for(int i=0;i<numOldSpots;i++){
        SuperSpot theSpot=oldSpots.get(i);
        java.awt.geom.Point2D.Double thePoint=new java.awt.geom.Point2D.Double(theSpot.getCentroidX(),theSpot.getCentroidY());
        OLD_VALUES[i]=oldLine.ptLineDist(thePoint)/oldLineWidth;
        %>  
		 [
		  <%=((theSpot.getCentroidX()-oldReferenceSpots[0].getCentroidX())/oldLineWidth) %>,
		  <%=OLD_VALUES[i] %>
		  ],
	 
		 <%
        
      }
      
      
      //second populate NEW_VALUES - trickier
      
      //create an array of lines made from all point pairs in theEnc2
      
      ArrayList<SuperSpot> newSpots=theEnc2.getSpots();
      if(theEnc2.getRightSpots()!=null){
      	newSpots.addAll(theEnc2.getRightSpots());
      }
      Collections.sort(newSpots, new XComparator());
      int numtheEnc2Spots=newSpots.size();
      Line2D.Double[] newLines=new Line2D.Double[numtheEnc2Spots-1];
      for(int i=0;i<(numtheEnc2Spots-1);i++){
        //convert y coords to distance from newLine
        double x1=(newSpots.get(i).getCentroidX()-newReferenceSpots[0].getCentroidX())/newLineWidth;
        double x2=(newSpots.get(i+1).getCentroidX()-newReferenceSpots[0].getCentroidX())/newLineWidth;
        double yCoord1=newLine.ptLineDist(newSpots.get(i).getCentroidX(), newSpots.get(i).getCentroidY())/newLineWidth;
        double yCoord2=newLine.ptLineDist(newSpots.get(i+1).getCentroidX(), newSpots.get(i+1).getCentroidY())/newLineWidth;
        newLines[i]=new Line2D.Double(x1, yCoord1, x2, yCoord2);
      }
      int numNewLines=newLines.length;
      
      
      %>
  	
  	
  	
  	

  	]);
        	
        	
        	
        	 // Create the data table.
          var data2 = new google.visualization.DataTable();
        	data2.addColumn('number', 'x');
        	data2.addColumn('number', 'y');
        	
          
        	data2.addRows([
           
         

  <%
      
      
      //now iterate and create our points
      for(int i=0;i<numOldSpots;i++){
    	  System.out.println("Iterating!");
        SuperSpot theSpot=oldSpots.get(i);
        double xCoordFraction=(theSpot.getCentroidX()-oldReferenceSpots[0].getCentroidX())/oldLineWidth;
        System.out.println("Iterating xCoordFraction: "+xCoordFraction);
        Line2D.Double theReallyLongLine=new Line2D.Double(xCoordFraction, -99999999, xCoordFraction, 99999999);
        
        //now we need to find where this point falls on the theEnc2 pattern
        Line2D.Double intersectionLine=null;
        int lineIterator=0;
        while((lineIterator<numNewLines)){
          //System.out.println("     Comparing line: ["+newLines[lineIterator].getX1()+","+newLines[lineIterator].getY1()+","+newLines[lineIterator].getX2()+","+newLines[lineIterator].getY2()+"]"+" to ["+theReallyLongLine.getX1()+","+theReallyLongLine.getY1()+","+theReallyLongLine.getX2()+","+theReallyLongLine.getY2()+"]");
          if(newLines[lineIterator].intersectsLine(theReallyLongLine)){
            intersectionLine=newLines[lineIterator];
            System.out.println("     !!!!!!FOUND the INTERSECT!!!!!!");
          }
          lineIterator++;
        }
        try{
        	
        	System.out.println("     lineY1="+intersectionLine.getY1()+" and Y2="+intersectionLine.getY2());
	        double slope=(intersectionLine.getY2()-intersectionLine.getY1())/(intersectionLine.getX2()-intersectionLine.getX1());
	        double yCoord=intersectionLine.getY1()+(xCoordFraction-intersectionLine.getX1())*slope;
	        if(yCoord>0){NEW_VALUES[i]=yCoord;}
	        else{NEW_VALUES[i]=0;}
	        
	        System.out.println("     ycoord "+yCoord+" at "+xCoordFraction+ " and slope is: "+slope);
        }
		catch(Exception e){
			System.out.println("Hit an exception with spot: ["+theSpot.getCentroidX()+","+theSpot.getCentroidY()+"]");
			NEW_VALUES[i]=0;
		}
		 %>  
		 [
		  <%=xCoordFraction %>, <%=NEW_VALUES[i] %>
		  ],
		 <%
        
        
      }
      
      java.lang.Double matchResult=new java.lang.Double(MSM.MSM_Distance(OLD_VALUES, NEW_VALUES));
      
  //end code copied from MSM.java



  String langCode=ServletUtilities.getLanguageCode(request);
  

  
  //java.awt.geom.Point2D.Double[] theEnc2ControlSpots=null;
  
  Properties encprops = ShepherdProperties.getProperties("encounter.properties", langCode, context);
//handle translation

  

	



	    
	    
	    
	

  






	    
	    
	    
	%>
	
	
	
	

	]);
      	
      	
      	
      	var joinedData = google.visualization.data.join(data, data2, 'full', [[0, 0]], [1], [1]);
      	
      	

	        
	        var options = {'title':'Render Fluke',
                    'width':chartWidth,
                    'height':chartHeight,
                    'pointSize': 5,
                    'color': 'yellow',
                    'dataOpacity': 0.5,
                    'lineWidth': 0,
                    series: {
                        0: { color: 'blue' },
                     	1: {color: 'green'},
                     	2: {color: 'red'},
                     	3: {color: 'yellow'}
                       
                      }
                    };

	        // Instantiate and draw our chart, passing in some options.
	        var chart = new google.visualization.LineChart(document.getElementById('chart_div'));
	        chart.draw(joinedData, options);
	        
	      }
	    </script>
	    
	    <%
	    String enc1MI="Unknown";
	    if(enc1.getIndividualID()!=null){enc1MI=enc1.getIndividualID();}
	    String enc2MI="Unknown";
	    if(enc2.getIndividualID()!=null){enc2MI=enc2.getIndividualID();}
	    
	
    
    
    %>
	    
	    <h2>Comparison: <a href="../encounter.jsp?number=<%=enc1.getCatalogNumber() %>"><%=enc1MI %></a> vs <a href="../encounter.jsp?number=<%=enc2.getCatalogNumber() %>"><%=enc2MI %></a></h2>
</h3>
<p><i>Lower is better.</i></p>

	    
	    <table><tr>
	    
	    

<td valign="top">

<p>Match result: <%=matchResult.toString() %></p>

<p>EncounterLite Match result: <%=MSM.getMSMDistance(theEnc, theEnc2) %></p>
<div id="chart_div"></div>



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

