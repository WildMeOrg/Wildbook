<%@ page contentType="text/html; charset=utf-8" 
	language="java" 
	import="java.awt.geom.*,com.reijns.I3S.*,com.reijns.I3S.Point2D,org.ecocean.servlet.ServletUtilities,org.ecocean.grid.*,java.awt.Dimension,org.ecocean.*, org.ecocean.servlet.*, java.util.*,javax.jdo.*,java.io.File" %>

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
		
try {
	
  	

	
	
	
  //get the encounter number
 
  //set up the JDO pieces and Shepherd
  myShepherd.beginDBTransaction();
  EncounterLite theEnc=new EncounterLite(myShepherd.getEncounter(encNum));
  EncounterLite theEnc2=new EncounterLite(myShepherd.getEncounter(encNum2));
  



  String langCode=ServletUtilities.getLanguageCode(request);
  

  
  java.awt.geom.Point2D.Double[] newEncControlSpots=null;
  
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
	

 
	    for (int t = 0; t < spots.size(); t++) {
	    %>  
	      [<%=spots.get(t).getCentroidX() %>,<%=spots.get(t).getCentroidY() %>],
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
    
    //let's create theEnc2 fingerprint
    newspotsTemp = new SuperSpot[0];
    newspotsTemp = (SuperSpot[]) spots2.toArray(newspotsTemp);
    newSpotsLength = newspotsTemp.length;
    newEncounterSpots = new java.awt.geom.Point2D.Double[newSpotsLength];
    for (int i = 0; i < newSpotsLength; i++) {
      newEncounterSpots[i] = new java.awt.geom.Point2D.Double(spots2.get(i).getTheSpot().getCentroidX(), spots2.get(i).getTheSpot().getCentroidY());
    }
    newOrigEncounterSpots = new java.awt.geom.Point2D.Double[spots2.size()];
     for (int z = 0; z < newOrigEncounterSpots.length; z++) {
      newOrigEncounterSpots[z] = new java.awt.geom.Point2D.Double(spots2.get(z).getCentroidX(), spots2.get(z).getCentroidY());
    }
    

    
    AffineTransform at=new AffineTransform(
    		theEnc.getLeftReferenceSpots()[0].getCentroidX(),
    		theEnc.getLeftReferenceSpots()[0].getCentroidY(),
    		theEnc.getLeftReferenceSpots()[1].getCentroidX(),
    		theEnc.getLeftReferenceSpots()[1].getCentroidY(),
    		theEnc.getLeftReferenceSpots()[2].getCentroidX(),
    		theEnc.getLeftReferenceSpots()[2].getCentroidY()
   	);
    
    int newSpotsLength2 = spots2.size();
    //java.awt.geom.Point2D.Double[] transformedSpots=new java.awt.geom.Point2D.Double[newSpotsLength2];
    /*
    at.transform(newOrigEncounterSpots,
            0,
            transformedSpots,
            0,
            newSpotsLength2);
*/
 
 
	 for (int t = 0; t < newSpotsLength2; t++) {
		 %>  
		 [<%=spots2.get(t).getCentroidX() %>,<%=spots2.get(t).getCentroidY() %>],
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
                       
                      }
                    };

	        // Instantiate and draw our chart, passing in some options.
	        var chart = new google.visualization.LineChart(document.getElementById('chart_div'));
	        chart.draw(joinedData, options);
	        
	      }
	    </script>
	    
	    
	    
	    
	    
	    



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


<div id="chart_div"></div>
<p>Hello!</p>


