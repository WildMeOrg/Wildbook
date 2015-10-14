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

	org.ecocean.neural.TrainNetwork"
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
		 
			String rootWebappPath = getServletContext().getRealPath("/");
		  File webappsDir = new File(rootWebappPath).getParentFile();
		  File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));
		  File encountersDir=new File(shepherdDataDir.getAbsolutePath()+"/encounters");
		  //File encounterDir = new File(encountersDir, encNum);
		
		
		File encounterDir = new File(Encounter.dir(shepherdDataDir, encNum));
		if(!encounterDir.exists()){
			encounterDir.mkdirs();
		}
		
try {
	
  	

	
	
	
  //get the encounter number
 
  //set up the JDO pieces and Shepherd
  myShepherd.beginDBTransaction();
  Encounter enc1=myShepherd.getEncounter(encNum);
  Encounter enc2=myShepherd.getEncounter(encNum2);
  EncounterLite theEnc=new EncounterLite(enc1);
  EncounterLite theEnc2=new EncounterLite(enc2);
  
  

  double stdDev=0.1;



  String langCode=ServletUtilities.getLanguageCode(request);
  

  
  //java.awt.geom.Point2D.Double[] newEncControlSpots=null;
  
  Properties encprops = ShepherdProperties.getProperties("encounter.properties", langCode, context);
//handle translation

  

	
	//EncounterLite enc=new EncounterLite(theEnc);
	// = new Point2D[3];
	//SuperSpot[] newspotsTemp = new SuperSpot[0];
	//newspotsTemp = (SuperSpot[]) enc.getRightSpots().toArray(newspotsTemp);
	ArrayList<SuperSpot> spots=new ArrayList<SuperSpot>();
	if(theEnc.getSpots()!=null){spots.addAll(theEnc.getSpots());}
	if(theEnc.getRightSpots()!=null){spots.addAll(theEnc.getRightSpots());}
    //newEncControlSpots = theEnc.getThreeLeftFiducialPoints();
    
	  
	  java.awt.geom.Point2D.Double[] theEncControlSpots=new java.awt.geom.Point2D.Double[3];
	  theEncControlSpots[0]=new java.awt.geom.Point2D.Double(9999999,0);
	  theEncControlSpots[1]=new java.awt.geom.Point2D.Double(0,-999999);
	  theEncControlSpots[2]=new java.awt.geom.Point2D.Double(-99999,0);
	  //Builder theBuilder = TimeSeriesBase.builder();
	  for(int i=0;i<spots.size();i++){
		  SuperSpot mySpot=spots.get(i);
		  
		  //get the rightmost spot
		  if(mySpot.getCentroidX()>theEncControlSpots[2].getX()){theEncControlSpots[2]=new java.awt.geom.Point2D.Double(mySpot.getCentroidX(),mySpot.getCentroidY());}
		  
		  //get the bottommost spot
		  if(mySpot.getCentroidY()>theEncControlSpots[1].getY()){theEncControlSpots[1]=new java.awt.geom.Point2D.Double(mySpot.getCentroidX(),mySpot.getCentroidY());}
		  
		  //get the leftmost spot
		  if(mySpot.getCentroidX()<theEncControlSpots[0].getX()){theEncControlSpots[0]=new java.awt.geom.Point2D.Double(mySpot.getCentroidX(),mySpot.getCentroidY());}
		  
		  //let's do our FastDTW stuff too
			
			//theBuilder.add(spots.get(i).getCentroidX(),spots.get(i).getCentroidY());  
		    
		  
	  }	
	  
	  //TimeSeries theTimeSeries=theBuilder.build();
	  

	//affine creation
	  //AffineTransform atInvert =new AffineTransform(at2);
	  //atInvert.invert();
	  //end affinbe creation
    
    
    
    
    //let's create theEnc fingerprint
    SuperSpot[] newspotsTemp = new SuperSpot[0];
    newspotsTemp = (SuperSpot[]) spots.toArray(newspotsTemp);
    int newSpotsLength = newspotsTemp.length;
    java.awt.geom.Point2D.Double[] newEncounterSpots = new java.awt.geom.Point2D.Double[newSpotsLength];
    for (int i = 0; i < newSpotsLength; i++) {
      newEncounterSpots[i] = new java.awt.geom.Point2D.Double(newspotsTemp[i].getTheSpot().getCentroidX(), newspotsTemp[i].getTheSpot().getCentroidY());
    }
    java.awt.geom.Point2D.Double[] newOrigEncounterSpots = new java.awt.geom.Point2D.Double[spots.size()];
     for (int z = 0; z < newOrigEncounterSpots.length; z++) {
      newOrigEncounterSpots[z] = new java.awt.geom.Point2D.Double(spots.get(z).getCentroidX(), spots.get(z).getCentroidY());
    }
    //FingerPrint newPrint = new FingerPrint(newOrigEncounterSpots, newEncounterSpots, newEncControlSpots);
   	//theEnc.doAffine(newPrint);
    
  
    
    //Line2D.Double newLeftLine=new Line2D.Double(newEncControlSpots[0].getX(),newEncControlSpots[0].getY(),newEncControlSpots[1].getX(),newEncControlSpots[1].getY());
    //Line2D.Double newRightLine=new Line2D.Double(newEncControlSpots[1].getX(),newEncControlSpots[1].getY(),newEncControlSpots[2].getX(),newEncControlSpots[2].getY());

    //double rightmostSpot=theEnc.getRightmostRightSpot();
	//double rightHighestSpot=theEnc.getHighestRightSpot();
	

		//map the transformed points
		for (int t = 0; t < newSpotsLength; t++) {
			 java.awt.geom.Point2D.Double originalPoint=new java.awt.geom.Point2D.Double(spots.get(t).getCentroidX(),spots.get(t).getCentroidY());
			 java.awt.geom.Point2D.Double transformedPoint=new java.awt.geom.Point2D.Double();
			 //at2.transform(originalPoint, transformedPoint);
			 %>  
			 [
			  <%=spots.get(t).getCentroidX() %>,
			  -<%=spots.get(t).getCentroidY() %>
			  ],
		 
			 <%
		}

	    
	    
	    
	%>
	
	
	
	

	]);
      	
      	
      	
      	 // Create the data table.
        var data2 = new google.visualization.DataTable();
      	data2.addColumn('number', 'x');
      	data2.addColumn('number', 'y');
      	
        
      	data2.addRows([
         
       

<%

  

	
	//EncounterLite enc=new EncounterLite(theEnc);
	// = new Point2D[3];
	//SuperSpot[] newspotsTemp = new SuperSpot[0];
	//newspotsTemp = (SuperSpot[]) enc.getRightSpots().toArray(newspotsTemp);
	ArrayList<SuperSpot> spots2=new ArrayList<SuperSpot>();
	if(theEnc2.getSpots()!=null){spots2.addAll(theEnc2.getSpots());}
	if(theEnc2.getRightSpots()!=null){spots2.addAll(theEnc2.getRightSpots());}
    //newEncControlSpots = theEnc2.getThreeLeftFiducialPoints();
    
	  java.awt.geom.Point2D.Double[] newEncControlSpots=new java.awt.geom.Point2D.Double[3];
	  newEncControlSpots[0]=new java.awt.geom.Point2D.Double(9999999,0);
	  newEncControlSpots[1]=new java.awt.geom.Point2D.Double(0,-999999);
	  newEncControlSpots[2]=new java.awt.geom.Point2D.Double(-99999,0);
	  Builder newBuilder = TimeSeriesBase.builder();
	  
	  for(int i=0;i<spots2.size();i++){
		  SuperSpot mySpot=spots2.get(i);
		  
		  //get the rightmost spot
		  if(mySpot.getCentroidX()>newEncControlSpots[2].getX()){newEncControlSpots[2]=new java.awt.geom.Point2D.Double(mySpot.getCentroidX(),mySpot.getCentroidY());}
		  
		  //get the bottommost spot
		  if(mySpot.getCentroidY()>newEncControlSpots[1].getY()){newEncControlSpots[1]=new java.awt.geom.Point2D.Double(mySpot.getCentroidX(),mySpot.getCentroidY());}
		  
		  //get the leftmost spot
		  if(mySpot.getCentroidX()<newEncControlSpots[0].getX()){newEncControlSpots[0]=new java.awt.geom.Point2D.Double(mySpot.getCentroidX(),mySpot.getCentroidY());}
		  
		  //newBuilder.add(spots2.get(i).getCentroidX(),spots2.get(i).getCentroidY());  
		  
	  }	
	  
	  //TimeSeries newTimeSeries=newBuilder.build();
    
    AffineTransform at=EncounterLite.deriveAffineTransform(
    		newEncControlSpots[0].getX(),
    		newEncControlSpots[0].getY(),
    		newEncControlSpots[1].getX(),
    		newEncControlSpots[1].getY(),
    		newEncControlSpots[2].getX(),
    		newEncControlSpots[2].getY(),
    		theEncControlSpots[0].getX(),
    		theEncControlSpots[0].getY(),
    		theEncControlSpots[1].getX(),
    		theEncControlSpots[1].getY(),
    		theEncControlSpots[2].getX(),
    		theEncControlSpots[2].getY()
   ); 		
    
    
   // AffineTransform at2=EncounterLite.calculateTransform(theEncControlSpots,newEncControlSpots);
	  
    
    

    int newSpotsLength2 = spots2.size();
    //java.awt.geom.Point2D.Double[] transformedSpots=new java.awt.geom.Point2D.Double[newSpotsLength2];
    
 
 //just map the points
	


	for (int t = 0; t < newSpotsLength2; t++) {
		 java.awt.geom.Point2D.Double originalPoint=new java.awt.geom.Point2D.Double(spots2.get(t).getCentroidX(),spots2.get(t).getCentroidY());
		 java.awt.geom.Point2D.Double transformedPoint=new java.awt.geom.Point2D.Double();
		 at.transform(originalPoint, transformedPoint);
		 %>  
		 [
		  <%=transformedPoint.getX() %>, -<%=transformedPoint.getY() %>
		  ],
		 <%
	}



	    
	    
	    
	%>
	
	
	
	

	]);
      	
      	
      	
      	var joinedData = google.visualization.data.join(data, data2, 'full', [[0, 0]], [1], [1]);
      	
      	

	        
	        var options = {'title':'Render Fluke',
                    'width':chartWidth,
                    'height':chartHeight,
                    'pointSize': 5,
                    'color': 'yellow',
                    series: {
                        0: { color: 'blue' },
                     	1: {color: 'yellow'},
                     	2: {color: 'green'},
                     	3: {color: 'green'}
                       
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
	    
	    double intersectionProportion=0.18;
	    
	    
	    //let's try some fun intersection analysis
	    int newPrintSize=spots2.size();
	    int thisPrintSize=spots.size();
	    java.lang.Double numIntersections=EncounterLite.getImprovedHolmbergIntersectionScore(theEnc, theEnc2,intersectionProportion);
	    double finalInter=-1;
	   	if(numIntersections!=null){finalInter=numIntersections;}
	   
	   	
	   	//Fast DTW analysis
	   	 TimeWarpInfo twi=EncounterLite.fastDTW(theEnc, theEnc2, 30);
    
    java.lang.Double distance = new java.lang.Double(-1);
    if(twi!=null){
    	WarpPath wp=twi.getPath();
        String myPath=wp.toString();
    	distance=new java.lang.Double(twi.getDistance());
    }		
    
    //modified I3S analysis
    I3SMatchObject newDScore=EncounterLite.improvedI3SScan(new EncounterLite(enc1), new EncounterLite(enc2));
    double newScore=-1;
    if(newDScore!=null){newScore=newDScore.getI3SMatchValue();}

    //proportional analysis
    double propor=EncounterLite.getFlukeProportion(theEnc,theEnc2);

    
    
    %>
	    
	    <h2>Comparison: <a href="encounter.jsp?number=<%=enc1.getCatalogNumber() %>"><%=enc1MI %></a> vs <a href="encounter.jsp?number=<%=enc2.getCatalogNumber() %>"><%=enc2MI %></a></h2>
</h3>
<p><i>higher is better. score is out of a maximum for 12 points.</i></p>

	    
	    <table><tr>
	    
	    <td valign="top">
	    
	    
	  <h4>Holmberg Intersection (prop=<%=intersectionProportion %>)</h4>  
	    
	    <p><i>Higher is better. 1 is the max score.</i></p>
	    <p>Num. intersections/total possible is: <%=finalInter %>
	    
	    </p>

	
<h4>Fast DTW</h4>	
<p><i>Lower is better</i></p>
<p>
FastDTW distance: <%=distance %>
</p>

<h4>Modified I3S (Improved Affine)</h4>

<p><i>Lower is better</i></p>
<p>
Score: <%=newScore %>
</p>

<h4>Natural (Pre-affine) Fluke Proportions</h4>
<p><i>Lower is better</i></p>

Proportional difference: <%=propor %>


</td>

<td valign="top">

<%
String baseDir = ServletUtilities.dataDir(context, rootWebappPath);
String thisEncounterDir = enc1.subdir();
String newEncounterDir = enc2.subdir();
%>

<div id="chart_div"></div>

<table width="<%=chartWidth %>px">
<tr>
<td><img src="http://localhost:8080/<%=CommonConfiguration.getDataDirectoryName(context) %>/encounters/<%=thisEncounterDir.replaceAll("\\\\", "/") %>/<%=enc1.getSpotImageFileName() %>" width="<%=((int)(chartWidth/2)) %>px" height="*" /></td>
<td><img src="http://localhost:8080/<%=CommonConfiguration.getDataDirectoryName(context) %>/encounters/<%=newEncounterDir.replaceAll("\\\\", "/") %>/<%=enc2.getSpotImageFileName() %>" width="<%=((int)(chartWidth/2)) %>px" height="*" /></td>
</tr>

</table>

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

