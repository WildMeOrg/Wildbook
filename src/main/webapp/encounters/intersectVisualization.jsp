<%@ page contentType="text/html; charset=utf-8" 
	language="java" 
	import="java.awt.geom.*,
	com.reijns.I3S.*,
	org.apache.commons.math.stat.descriptive.SummaryStatistics,
	java.awt.geom.Point2D.Double ,org.ecocean.servlet.ServletUtilities,org.ecocean.grid.*,java.awt.Dimension,org.ecocean.*, org.ecocean.servlet.*, java.util.*,javax.jdo.*,java.io.File" %>

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
	  for(int i=0;i<spots.size();i++){
		  SuperSpot mySpot=spots.get(i);
		  
		  //get the rightmost spot
		  if(mySpot.getCentroidX()>theEncControlSpots[2].getX()){theEncControlSpots[2]=new java.awt.geom.Point2D.Double(mySpot.getCentroidX(),mySpot.getCentroidY());}
		  
		  //get the bottommost spot
		  if(mySpot.getCentroidY()>theEncControlSpots[1].getY()){theEncControlSpots[1]=new java.awt.geom.Point2D.Double(mySpot.getCentroidX(),mySpot.getCentroidY());}
		  
		  //get the leftmost spot
		  if(mySpot.getCentroidX()<theEncControlSpots[0].getX()){theEncControlSpots[0]=new java.awt.geom.Point2D.Double(mySpot.getCentroidX(),mySpot.getCentroidY());}
		  
		  
	  }	
	  
	  System.out.print("old Enc Control spots: ");
	  for(int t=0;t<3;t++){
		  System.out.print(" ("+theEncControlSpots[t].getX()+","+theEncControlSpots[t].getY()+" )");
	  }
	  System.out.println("");
	  

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
			  <%=spots.get(t).getCentroidY() %>
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
	  for(int i=0;i<spots2.size();i++){
		  SuperSpot mySpot=spots2.get(i);
		  
		  //get the rightmost spot
		  if(mySpot.getCentroidX()>newEncControlSpots[2].getX()){newEncControlSpots[2]=new java.awt.geom.Point2D.Double(mySpot.getCentroidX(),mySpot.getCentroidY());}
		  
		  //get the bottommost spot
		  if(mySpot.getCentroidY()>newEncControlSpots[1].getY()){newEncControlSpots[1]=new java.awt.geom.Point2D.Double(mySpot.getCentroidX(),mySpot.getCentroidY());}
		  
		  //get the leftmost spot
		  if(mySpot.getCentroidX()<newEncControlSpots[0].getX()){newEncControlSpots[0]=new java.awt.geom.Point2D.Double(mySpot.getCentroidX(),mySpot.getCentroidY());}
		  
		  
	  }	
	  System.out.print("new Enc Control spots: ");
	  for(int t=0;t<3;t++){
		  System.out.print(" ("+newEncControlSpots[t].getX()+","+newEncControlSpots[t].getY()+" )");
	  }
	  System.out.println("");

    
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
		  <%=transformedPoint.getX() %>, <%=transformedPoint.getY() %>
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
	    
	    
	  <h2>intersectionScore</h2>  
	    <%
	    //let's try some fun intersection analysis
	    int newPrintSize=spots2.size();
	    int thisPrintSize=spots.size();
	    int numIntersections=0;
	    StringBuffer anglesOfIntersection=new StringBuffer("");
	    for(int i=0;i<(newPrintSize-1);i++){
	      //for(int j=i+1;j<newPrintSize;j++){
	      int j=i+1;
	      
	         java.awt.geom.Point2D.Double originalStartPoint=new java.awt.geom.Point2D.Double(spots2.get(i).getCentroidX(),spots2.get(i).getCentroidY());
			 java.awt.geom.Point2D.Double transformedStartPoint=new java.awt.geom.Point2D.Double();
			 at.transform(originalStartPoint, transformedStartPoint);
			 
			 java.awt.geom.Point2D.Double originalEndPoint=new java.awt.geom.Point2D.Double(spots2.get(j).getCentroidX(),spots2.get(j).getCentroidY());
			 java.awt.geom.Point2D.Double transformedEndPoint=new java.awt.geom.Point2D.Double();
			 at.transform(originalEndPoint, originalStartPoint);
	        
	        java.awt.geom.Point2D.Double newStart=(new  java.awt.geom.Point2D.Double(transformedStartPoint.getX(),transformedStartPoint.getY()));
	        java.awt.geom.Point2D.Double newEnd=(new  java.awt.geom.Point2D.Double(transformedEndPoint.getX(),transformedEndPoint.getY()) ) ;
	        java.awt.geom.Line2D.Double newLine=new java.awt.geom.Line2D.Double(newStart,newEnd  );
	      
	        //now compare to thisPattern
	        for(int m=0;m<(thisPrintSize-1);m++){
	 
	              int n=m+1;
	              
	              java.awt.geom.Point2D.Double thisStart=(new  java.awt.geom.Point2D.Double(spots.get(m).getCentroidX(),spots.get(m).getCentroidY()));
	              java.awt.geom.Point2D.Double thisEnd=(new  java.awt.geom.Point2D.Double(spots.get(n).getCentroidX(),spots.get(n).getCentroidY()) );   
	              java.awt.geom.Line2D.Double thisLine=new java.awt.geom.Line2D.Double(thisStart,thisEnd);
	              
	             // if(((newStart.getX()<=thisEnd.getX()))){
	                if(newLine.intersectsLine(thisLine)){
	                  numIntersections++;
	                  String intersectionAngle=java.lang.Double.toString(EncounterLite.angleBetween2Lines(newLine, thisLine));
	                  anglesOfIntersection.append(intersectionAngle+",");
	                 }
	                //else{System.out.println("["+newStart.getX()+","+newStart.getY()+","+newEnd.getX()+","+newEnd.getY()+"]"+" does not intersect with "+"["+thisStart.getX()+","+thisStart.getY()+","+thisEnd.getX()+","+thisEnd.getY()+"]");}
	                
	                //short circuit to end if the comparison line is past the new line
	                //if(newEnd.getX()<thisStart.getX()){
	                //  m=thisPrintSize;
	                //}
	             // }
	            
	           
	            
	            
	            
	          
	        }
	        
	      
	      //}
	      
	      
	    }
	    
	    %>
	    
	    <p>Num. intersections is: <%=numIntersections %><br />
	    <%
	    SummaryStatistics stats=new SummaryStatistics();
	    String angles=anglesOfIntersection.toString();
	    StringTokenizer str=new StringTokenizer(angles,",");
        
		
        while(str.hasMoreTokens()){
				
				String token=str.nextToken();
				double value=(new java.lang.Double(token)).doubleValue();
				stats.addValue(value);
        }
	    %>
	    
	    Std Dev. of Intersection angles: <%=stats.getStandardDeviation()/(2*Math.PI) %> degrees
	    </p>
	    



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


