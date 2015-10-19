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
	
	
	<%! 
	
	 public static java.lang.Double getHolmbergIntersectionScore(EncounterLite theEnc,EncounterLite theEnc2){
	      
	      try{
	        ArrayList<SuperSpot> spots=new ArrayList<SuperSpot>();
	        if(theEnc.getSpots()!=null){spots.addAll(theEnc.getSpots());}
	        if(theEnc.getRightSpots()!=null){spots.addAll(theEnc.getRightSpots());}
	        //sort the Array - lowest x to highest X coordinate
	        Collections.sort(spots, new XComparator());
	        //let's prefilter old spots for outlies outside the bounds
	        for(int i=0;i<spots.size();i++){
	                    if(theEnc.getLeftReferenceSpots()[0].getCentroidX()<theEnc.getLeftReferenceSpots()[2].getCentroidX()){
            SuperSpot theSpot=spots.get(i);
            if(theSpot.getCentroidX()<=theEnc.getLeftReferenceSpots()[0].getCentroidX()){
              spots.remove(i);
              i--;
            }
            if(theSpot.getCentroidX()>=theEnc.getLeftReferenceSpots()[2].getCentroidX()){
              spots.remove(i);
              i--;
            }
        }
	        }
	        int numOldSpots=spots.size();
	        
	        
	        
	          
	       java.awt.geom.Point2D.Double[] theEncControlSpots=new java.awt.geom.Point2D.Double[3];
	       
	         ArrayList<SuperSpot> spots2=new ArrayList<SuperSpot>();
	        if(theEnc2.getSpots()!=null){spots2.addAll(theEnc2.getSpots());}
	        if(theEnc2.getRightSpots()!=null){spots2.addAll(theEnc2.getRightSpots());}
	          //newEncControlSpots = theEnc2.getThreeLeftFiducialPoints();
	          
	          java.awt.geom.Point2D.Double[] newEncControlSpots=new java.awt.geom.Point2D.Double[3];
	      
	          newEncControlSpots[0]=new java.awt.geom.Point2D.Double(theEnc2.getLeftReferenceSpots()[0].getCentroidX(),theEnc2.getLeftReferenceSpots()[0].getCentroidY());
	          newEncControlSpots[1]=new java.awt.geom.Point2D.Double(theEnc2.getLeftReferenceSpots()[1].getCentroidX(),theEnc2.getLeftReferenceSpots()[1].getCentroidY());
	          newEncControlSpots[2]=new java.awt.geom.Point2D.Double(theEnc2.getLeftReferenceSpots()[2].getCentroidX(),theEnc2.getLeftReferenceSpots()[2].getCentroidY());
	          
	          
	          //return to using persisted refSpots
	          theEncControlSpots[0]=new java.awt.geom.Point2D.Double(theEnc.getLeftReferenceSpots()[0].getCentroidX(),theEnc.getLeftReferenceSpots()[0].getCentroidY());
	          theEncControlSpots[1]=new java.awt.geom.Point2D.Double(theEnc.getLeftReferenceSpots()[1].getCentroidX(),theEnc.getLeftReferenceSpots()[1].getCentroidY());
	          theEncControlSpots[2]=new java.awt.geom.Point2D.Double(theEnc.getLeftReferenceSpots()[2].getCentroidX(),theEnc.getLeftReferenceSpots()[2].getCentroidY());
	          
	          Collections.sort(spots2, new XComparator());
	          
	        //new arrays
	          ArrayList<SuperSpot> formerSpots=new ArrayList<SuperSpot>();
	          ArrayList<SuperSpot> newerSpots=new ArrayList<SuperSpot>();
	        
	        SuperSpot[] oldReferenceSpots=theEnc.getLeftReferenceSpots();
	        Line2D.Double oldLine=new Line2D.Double(oldReferenceSpots[0].getCentroidX(), oldReferenceSpots[0].getCentroidY(), oldReferenceSpots[2].getCentroidX(), oldReferenceSpots[2].getCentroidY());
	        double oldLineWidth=Math.abs(oldReferenceSpots[2].getCentroidX()-oldReferenceSpots[0].getCentroidX());
	        
	        SuperSpot[] newReferenceSpots=theEnc2.getLeftReferenceSpots();
	        Line2D.Double newLine=new Line2D.Double(newReferenceSpots[0].getCentroidX(), newReferenceSpots[0].getCentroidY(), newReferenceSpots[2].getCentroidX(), newReferenceSpots[2].getCentroidY());
	        double newLineWidth=Math.abs(newReferenceSpots[2].getCentroidX()-newReferenceSpots[0].getCentroidX());
	        
	        
	        //first populate OLD_VALUES - easy
	        
	        for(int i=0;i<numOldSpots;i++){
	          SuperSpot theSpot=spots.get(i);
	          java.awt.geom.Point2D.Double thePoint=new java.awt.geom.Point2D.Double(theSpot.getCentroidX(),theSpot.getCentroidY());
	          SuperSpot m_spot=new SuperSpot(thePoint.getX()/oldLineWidth,oldLine.ptLineDist(thePoint)/oldLineWidth);
	          formerSpots.add(m_spot);
	          //System.out.println(".....adding: ["+m_spot.getCentroidX()+","+m_spot.getCentroidY()+"]");
	        }
	        
	        
	        //second populate NEW_VALUES - trickier
	        
	        //create an array of lines made from all point pairs in newEnc
	        
	        
	        ArrayList<SuperSpot> newSpots=new ArrayList<SuperSpot>();
			if(theEnc2.getSpots()!=null){newSpots.addAll(theEnc2.getSpots());};
	        if(theEnc2.getRightSpots()!=null){newSpots.addAll(theEnc2.getRightSpots());};
	        int numNewEncSpots=newSpots.size();
	        Line2D.Double[] newLines=new Line2D.Double[numNewEncSpots-1];
	        Collections.sort(newSpots, new XComparator());
	        for(int i=0;i<(numNewEncSpots-1);i++){
	          //convert y coords to distance from newLine
	          double x1=(newSpots.get(i).getCentroidX()-newReferenceSpots[0].getCentroidX())/newLineWidth;
	          double x2=(newSpots.get(i+1).getCentroidX()-newReferenceSpots[0].getCentroidX())/newLineWidth;
	          double yCoord1=newLine.ptLineDist(newSpots.get(i).getCentroidX(), newSpots.get(i).getCentroidY())/newLineWidth;
	          double yCoord2=newLine.ptLineDist(newSpots.get(i+1).getCentroidX(), newSpots.get(i+1).getCentroidY())/newLineWidth;
	          newLines[i]=new Line2D.Double(x1, yCoord1, x2, yCoord2);
	        }
	        int numNewLines=newLines.length;
	        
	        
	        
	        //now iterate and create our points
	        for(int i=0;i<numOldSpots;i++){
	          SuperSpot theSpot=spots.get(i);
	          double xCoordFraction=(theSpot.getCentroidX()-oldReferenceSpots[0].getCentroidX())/oldLineWidth;
	          Line2D.Double theReallyLongLine=new Line2D.Double(xCoordFraction, -99999999, xCoordFraction, 99999999);
	          
	          //now we need to find where this point falls on the newEnc pattern
	          Line2D.Double intersectionLine=null;
	          int lineIterator=0;
	          while((intersectionLine==null)&&(lineIterator<numNewLines)){
	            //System.out.println("     Comparing line: ["+newLines[lineIterator].getX1()+","+newLines[lineIterator].getY1()+","+newLines[lineIterator].getX2()+","+newLines[lineIterator].getY2()+"]"+" to ["+theReallyLongLine.getX1()+","+theReallyLongLine.getY1()+","+theReallyLongLine.getX2()+","+theReallyLongLine.getY2()+"]");
	            if(newLines[lineIterator].intersectsLine(theReallyLongLine)){
	              intersectionLine=newLines[lineIterator];
	              //System.out.println("!!!!!!FOUND the INTERSECT!!!!!!");
	            }
	            lineIterator++;
	          }
	          try{
	            double slope=(intersectionLine.getY2()-intersectionLine.getY1())/(intersectionLine.getX2()-intersectionLine.getX1());
	            double yCoord=intersectionLine.getY1()+(xCoordFraction-intersectionLine.getX1())*slope;
	            
	            //Point2D.Double thePoint=new Point2D.Double(xCoordFraction,yCoord);
	            
	            SuperSpot m_spot=new SuperSpot(formerSpots.get(i).getCentroidX(),yCoord);
	            //System.out.println(".....adding: ["+m_spot.getCentroidX()+","+yCoord+"]");
	            newerSpots.add(m_spot);
	          }
	          catch(Exception e){
	            //System.out.println("Hit an exception with spot: ["+theSpot.getCentroidX()+","+theSpot.getCentroidY()+"]");
	            SuperSpot m_spot=new SuperSpot(formerSpots.get(i).getCentroidX(),0);
	            newerSpots.add(m_spot);
	          }
	        }
	          //continue old method
	          
	          //in advance of any intersection
	          //create a list of Poin2DDouble Pair proportional distances from the notch
	          ArrayList<java.lang.Double> intersectionsProportionalDistances=new ArrayList<java.lang.Double>();
	          
	          //let's try some fun intersection analysis
	          spots=formerSpots;
	          spots2=newerSpots;
	          int newPrintSize=spots2.size();
	          int thisPrintSize=spots.size();
	          
	          //calculate smallest array size and then -1 for max number of potential lines to match
	          int maxIntersectingLines=newPrintSize-1;
	          if(thisPrintSize<newPrintSize){maxIntersectingLines=thisPrintSize-1;}
	          
	          double numIntersections=0;
	          StringBuffer anglesOfIntersection=new StringBuffer("");
	          for(int i=0;i<(newPrintSize-1);i++){
	            //for(int j=i+1;j<newPrintSize;j++){
	            int j=i+1;
	            
	            java.awt.geom.Point2D.Double transformedStartPoint=new java.awt.geom.Point2D.Double(spots2.get(i).getCentroidX(),spots2.get(i).getCentroidY());
	            
	            java.awt.geom.Point2D.Double transformedEndPoint=new java.awt.geom.Point2D.Double(spots2.get(j).getCentroidX(),spots2.get(j).getCentroidY());
	            
	              java.awt.geom.Point2D.Double newStart=(new  java.awt.geom.Point2D.Double(transformedStartPoint.getX(),transformedStartPoint.getY()));
	              java.awt.geom.Point2D.Double newEnd=(new  java.awt.geom.Point2D.Double(transformedEndPoint.getX(),transformedEndPoint.getY()) ) ;
	              java.awt.geom.Line2D.Double newLine2=new java.awt.geom.Line2D.Double(newStart,newEnd  );
	            
	              //now compare to thisPattern
	              int m=0;
	              boolean foundIntersect=false;
	              while((!foundIntersect)&&(m<(thisPrintSize-1))){
	       
	                    int n=m+1;
	                    
	                    java.awt.geom.Point2D.Double thisStart=(new  java.awt.geom.Point2D.Double(spots.get(m).getCentroidX(),spots.get(m).getCentroidY()));
	                    java.awt.geom.Point2D.Double thisEnd=(new  java.awt.geom.Point2D.Double(spots.get(n).getCentroidX(),spots.get(n).getCentroidY()) );   
	                    java.awt.geom.Line2D.Double thisLine=new java.awt.geom.Line2D.Double(thisStart,thisEnd);
	                    
	                    if(newLine2.intersectsLine(thisLine)){
	                        numIntersections++;
	                        foundIntersect=true;
	                        String intersectionAngle=java.lang.Double.toString(EncounterLite.angleBetween2Lines(newLine2, thisLine));
	                        anglesOfIntersection.append(intersectionAngle+",");
	                        
	                        //calculate proportional distance to test if intersection was valid in original space
	                        //untranslate new points since they were mapped into this points
	                        java.awt.geom.Point2D.Double intersectionPoint=EncounterLite.getIntersectionPoint(newLine2,thisLine);
	          
	                        
	                      }
	                    m++;
	              }
	              
	            
	            
	            
	          }
	          return new java.lang.Double((numIntersections/maxIntersectingLines));
	          //return (numIntersections);
	      
	    }
	     catch(Exception e){
	       e.printStackTrace();
	       return new java.lang.Double(0.0);
	     }
	    
	    
	  }
	
	
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

		  
	  }	
	  

    
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
    
  
    

    int newSpotsLength2 = spots2.size();



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
	    java.lang.Double numIntersections=getHolmbergIntersectionScore(theEnc, theEnc2);
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

