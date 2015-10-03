<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,
java.net.*,
org.ecocean.grid.*,
org.ecocean.neural.*,
	com.fastdtw.timeseries.TimeSeriesBase.*,
	com.fastdtw.dtw.*,
	com.fastdtw.util.Distances,
	com.fastdtw.timeseries.TimeSeriesBase.Builder,
	com.fastdtw.timeseries.*,
	weka.core.*,
	org.ecocean.grid.msm.*,
	weka.classifiers.meta.*,
	weka.classifiers.*,
	org.apache.commons.math.stat.descriptive.SummaryStatistics,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>

<%

String context="context0";
context=ServletUtilities.getContext(request);

	Shepherd myShepherd=new Shepherd(context);

ArrayList<String> suspectValues=new ArrayList<String>();

int numMatchLinks=0;
int numFalseLinks=0;

%>

<html>
<head>
<title>Matching Performance</title>

</head>


<body>
<%

//training metrics
double intersectionProportion=0.2;
if(request.getParameter("intersectionProportion")!=null){intersectionProportion=(new Double(request.getParameter("intersectionProportion"))).doubleValue();}


int chartWidth=800;

myShepherd.beginDBTransaction();


//ArrayList<String> matchLinks=new ArrayList<String>();
//ArrayList<String> falseLinks=new ArrayList<String>();
//ArrayList<String> mergedLinks=new ArrayList<String>();

String genusSpecies="Physetermacrocephalus";
if(request.getParameter("genusSpecies")!=null){
	genusSpecies=request.getParameter("genusSpecies");
}

String pathToClassifierFile=TrainNetwork.getAbsolutePathToClassifier(genusSpecies,request);
String instancesFileFullPath=TrainNetwork.getAbsolutePathToInstances(genusSpecies, request);

System.out.println("     I expect to find a classifier file here: "+pathToClassifierFile);
System.out.println("     I expect to find an instances file here: "+instancesFileFullPath);

//Instances instances=GridManager.getAdaboostInstances(request, instancesFileFullPath);
Instances instances=TrainNetwork.getAdaboostInstances(request, instancesFileFullPath);
AdaBoostM1 booster=TrainNetwork.getAdaBoostClassifier(request, pathToClassifierFile, instances);


try{


/*
Vector encounters=myShepherd.getAllEncountersNoFilterAsVector();
int numEncs=encounters.size();
for(int i=0;i<(numEncs-1);i++){
  for(int j=(i+1);j<numEncs;j++){
    
    Encounter enc1=(Encounter)encounters.get(i);
    Encounter enc2=(Encounter)encounters.get(j);
    
    if(((enc1.getSpots()!=null)&&(enc1.getSpots().size()>0)&&(enc1.getRightSpots()!=null))&&((enc1.getRightSpots().size()>0))&&((enc2.getSpots()!=null)&&(enc2.getSpots().size()>0)&&(enc2.getRightSpots()!=null)&&((enc2.getRightSpots().size()>0)))){
        try{
          //System.out.println("Learning: "+enc1.getCatalogNumber()+" and "+enc2.getCatalogNumber());
          
          //if both have spots, then we need to compare them
       
          //first, are they the same animal?
          //default is 1==no
          double output=1;
          if((enc1.getIndividualID()!=null)&&(!enc1.getIndividualID().toLowerCase().equals("unassigned"))){
            if((enc2.getIndividualID()!=null)&&(!enc2.getIndividualID().toLowerCase().equals("unassigned"))){
              //train a match
              if(enc1.getIndividualID().equals(enc2.getIndividualID())){output=0;}
            }
            
          }
          
          
          //http://localhost:8080/wildbook-5.4.0-DEVELOPMENT/encounters/intersectVisualization.jsp?enc1=8280807b-5dff-4b4c-a2a8-bfe2ec9ec054&enc2=ea5d275f-814e-4775-8fa9-fcfa3efb5d10
          
        if(output==1){
        	falseLinks.add(enc1.getCatalogNumber()+":"+enc2.getCatalogNumber());
              
        }
        else{
        	matchLinks.add(enc1.getCatalogNumber()+":"+enc2.getCatalogNumber());
        }
          
        }
        catch(Exception e){
        	e.printStackTrace();
        }
    }

  }
}
*/


//create our hashmaps of incorrect match scores
//Hashtable<Double,Integer> intersectionHashtable = new Hashtable<Double,Integer>();
//populateNewHashtable(intersectionHashtable,3);
//Hashtable<Integer,Integer> dtwHashtable = new Hashtable<Integer,Integer>();
//populateNewHashtable(dtwHashtable,3);
//Hashtable<Integer,Integer> i3sHashtable = new Hashtable<Integer,Integer>();
//populateNewHashtable(i3sHashtable,3);
//Hashtable<Integer,Integer> proportionHashtable = new Hashtable<Integer,Integer>();
//populateNewHashtable(proportionHashtable,3);
Hashtable<Double,Integer> overallHashtable = new Hashtable<Double,Integer>();

ArrayList<Double> intersectionValues=new ArrayList<Double>();
ArrayList<Double> dtwValues=new ArrayList<Double>();
ArrayList<Double> i3sValues=new ArrayList<Double>();
ArrayList<Double> proportionValues=new ArrayList<Double>();
ArrayList<Double> msmValues=new ArrayList<Double>();

//create hastables of coreect
//Hashtable<Double,Integer> intersectionCorrectHashtable = new Hashtable<Double,Integer>();
//populateNewHashtable(intersectionCorrectHashtable,3);	
//Hashtable<Integer,Integer> dtwCorrectHashtable = new Hashtable<Integer,Integer>();
//populateNewHashtable(dtwCorrectHashtable,3);	
//Hashtable<Integer,Integer> i3sCorrectHashtable = new Hashtable<Integer,Integer>();
//populateNewHashtable(i3sCorrectHashtable,3);	
//Hashtable<Integer,Integer> proportionCorrectHashtable = new Hashtable<Integer,Integer>();
//populateNewHashtable(proportionCorrectHashtable,3);	
Hashtable<Double,Integer> overallCorrectHashtable = new Hashtable<Double,Integer>();
	
ArrayList<Double> intersectionCorrectValues=new ArrayList<Double>();
ArrayList<Double> dtwCorrectValues=new ArrayList<Double>();
ArrayList<Double> i3sCorrectValues=new ArrayList<Double>();
ArrayList<Double> proportionCorrectValues=new ArrayList<Double>();
ArrayList<Double> msmCorrectValues=new ArrayList<Double>();


double correctScoreTotal=0;
int numCorrectScores=0;

double incorrectScoreTotal=0;
int numIncorrectScores=0;


int numInstances=instances.numInstances();

if(numInstances>1000)numInstances=1000;

for(int i=0;i<numInstances;i++){
	
	Instance myInstance=instances.instance(i);
	//if(((enc1.getSpots()!=null)&&(enc1.getSpots().size()>0)&&(enc1.getRightSpots()!=null))&&((enc1.getRightSpots().size()>0))&&((enc2.getSpots()!=null)&&(enc2.getSpots().size()>0)&&(enc2.getRightSpots()!=null)&&((enc2.getRightSpots().size()>0)))){
        try{
          
          //if both have spots, then we need to compare them
       
          //first, are they the same animal?
          //default is 1==no
          double output=1;
      
     /*
       // Create the instance
          Instance iExample = new Instance(6);
          iExample.setValue((Attribute)fvWekaAttributes.elementAt(0), numIntersections.doubleValue());
          iExample.setValue((Attribute)fvWekaAttributes.elementAt(1), distance.doubleValue());
          iExample.setValue((Attribute)fvWekaAttributes.elementAt(2), i3sScore);
          iExample.setValue((Attribute)fvWekaAttributes.elementAt(3), proportion.doubleValue());
          iExample.setValue((Attribute)fvWekaAttributes.elementAt(4), msm.doubleValue());
          
          if(output==0){
            iExample.setValue((Attribute)fvWekaAttributes.elementAt(5), "match");
          }
          else{
            iExample.setValue((Attribute)fvWekaAttributes.elementAt(5), "nonmatch");
          }
     */
          //System.out.println(myInstance.stringValue(5));
          if(myInstance.stringValue(5).equals("match")){output=0;}
          
          
          //HolmbergIntersection
          Double numIntersections=new Double(myInstance.value(0));
          
          java.lang.Double distance = new Double(myInstance.value(1));
          
          
          //I3S
          double i3sScore=new Double(myInstance.value(2)).doubleValue();
          //Proportion metric
          Double proportion=new Double(myInstance.value(3));
          
          
          Double msmValue=new Double(myInstance.value(4));
          
          
     
     
          Instance iExample = new Instance(6);
          
          iExample.setDataset(instances);
          iExample.setValue(0, numIntersections.doubleValue());
          iExample.setValue(1, distance);
          iExample.setValue(2,  i3sScore);
          iExample.setValue(3, (new Double(proportion).doubleValue()));
          iExample.setValue(4, (new Double(msmValue).doubleValue()));
          
          
          double[] fDistribution = booster.distributionForInstance(iExample);
          
          
          //double thisScore=TrainNetwork.getOverallFlukeMatchScore(request, numIntersections, distance.doubleValue(), i3sScore, new Double(proportion),intersectionStats,dtwStats,i3sStats, proportionStats, intersectionStdDev,dtwStdDev,i3sStdDev,proportionStdDev,intersectHandicap, dtwHandicap,i3sHandicap,proportionHandicap);
          double thisScore=TrainNetwork.round(fDistribution[0], 5);
         System.out.println(thisScore);
          
          
          //getOverallFlukeMatchScore(HttpServletRequest request, double intersectionsValue, double dtwValue, double i3sValue, double proportionsValue, double numStandardDevs, SummaryStatistics intersectionStats, SummaryStatistics dtwStats,SummaryStatistics i3sStats, SummaryStatistics proportionStats)
            if(output==0){
            	
            	numMatchLinks++;
            	
            	//overall
            	Double score=(new Double(thisScore)); 
            	
            	if(overallCorrectHashtable.get(score)==null){
            		overallCorrectHashtable.put(score, 0);
            	}
            	Integer numValue=overallCorrectHashtable.get(score).intValue()+1;
            	
            	overallCorrectHashtable.put(score, numValue);
            	correctScoreTotal+=score;
            	numCorrectScores++;
            	
            	//intersection
            	intersectionCorrectValues.add(numIntersections);
            	
            	//FastDTW
            	dtwCorrectValues.add(distance);
            	
            	//I3S
            	i3sCorrectValues.add(i3sScore);
            	
            	//Proportion
            	proportionCorrectValues.add(proportion);
            	
            	msmCorrectValues.add(msmValue);
            	
            }
            else{
            	
            	numFalseLinks++;
            	//overall
            	
            	double score=(new Double(thisScore)); 
            	if(overallHashtable.get(score)==null){
            		overallHashtable.put(score, 0);
            	}
            	Integer numValue=overallHashtable.get(score).intValue()+1;
            	overallHashtable.put(score, numValue);
            	incorrectScoreTotal+=score;
            	numIncorrectScores++;
            	
            	//intersection
            	intersectionValues.add(numIntersections);
            	
            	//FastDTW
            	dtwValues.add(distance);
            	
            	//I3S
            	i3sValues.add(i3sScore);
            	
            	//Proportion
            	proportionValues.add(proportion);
            	
            	msmValues.add(msmValue);
            }
            
          
          
        
      }
      catch(Exception e){
        e.printStackTrace();
      }

        
        
    //  }
	
}	



myShepherd.rollbackDBTransaction();
	myShepherd.closeDBTransaction();
	myShepherd=null;
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
        var overallCorrectData = new google.visualization.DataTable();
        overallCorrectData.addColumn('number', 'score');
        overallCorrectData.addColumn('number', 'matching');
        
        overallCorrectData.addRows([
      	<%
      	
      	Enumeration<Double> correctKeys=overallCorrectHashtable.keys();
      	while(correctKeys.hasMoreElements()){
      		Double nextElem=correctKeys.nextElement();	
      	
      	  //for(double i=0;i<=1;){
      		  %>
      		  [<%=nextElem %>,<%=(overallCorrectHashtable.get(nextElem).doubleValue()/numMatchLinks) %>],
      		  <%
      		 
      	  }           
      	%>              
		]);
      	
      	
      	
     	 // Create the data table.
       var overallIncorrectData = new google.visualization.DataTable();
       overallIncorrectData.addColumn('number', 'score');
       overallIncorrectData.addColumn('number', 'nonmatching');
     	
       
     	overallIncorrectData.addRows([
		<%
		Enumeration<Double> incorrectKeys=overallHashtable.keys();
		while(incorrectKeys.hasMoreElements()){
      		Double nextElem=incorrectKeys.nextElement();	
			  %>
			  [<%=nextElem %>,<%=(overallHashtable.get(nextElem).doubleValue()/numFalseLinks) %>],
			  <%
			 
		  }           
		%>           
     	               
     	               
		]);
      	
      	
      	
      	var joinedData = google.visualization.data.join(overallIncorrectData, overallCorrectData, 'full', [[0, 0]], [1], [1]);
      	
      	

	        
	        var options = {'title':'Overall Scoring Performance: Matches vs Non-matches',
                    'width':chartWidth,
                    'height':chartHeight,
                    'pointSize': 5,
                    'color': 'yellow',
                    series: {
                        0: { color: 'red' },
                     	1: {color: 'green'},

                       
                      },
                      vAxis: {title: "% of type (match or non-match) total"},
                      hAxis: {title: "Overall Score"},
                    };

	        // Instantiate and draw our chart, passing in some options.
	        var chart = new google.visualization.LineChart(document.getElementById('overallchart_div'));
	        chart.draw(joinedData, options);
	        
	      }
      	              
      	              
</script>


<script type="text/javascript" src="https://www.google.com/jsapi"></script>
    <script type="text/javascript">
		google.setOnLoadCallback(drawInterChart);

      // Callback that creates and populates a data table,
      // instantiates the pie chart, passes in the data and
      // draws it.
      function drawInterChart() {

        // Create the data table.
        var interCorrectData = new google.visualization.DataTable();
        interCorrectData.addColumn('number', 'score');
        interCorrectData.addColumn('number', 'matching');
        
        interCorrectData.addRows([
                                  
         <%
         Collections.sort(intersectionCorrectValues);
        
      	  for(int y=0;y<intersectionCorrectValues.size();y++){
      		double position=(double)y/intersectionCorrectValues.size();
    		  
      		  %>
      		  [<%=position %>,<%=intersectionCorrectValues.get(y) %>],
      		  <%
      	  }           
      	%>              
		]);
      	
      	
      	
     	 // Create the data table.
       var interIncorrectData = new google.visualization.DataTable();
       interIncorrectData.addColumn('number', 'score');
       interIncorrectData.addColumn('number', 'nonmatching');
     	
       
       interIncorrectData.addRows([
		<%
         Collections.sort(intersectionValues);
        
      	  for(int y=0;y<intersectionValues.size();y++){
      		  double position=(double)y/intersectionValues.size();
      		  %>
      		  [<%=position %>,<%=intersectionValues.get(y) %>],
      		  <%
      	  }           
      	%>           
     	               
     	               
		]);
      	
      	
      	
      	var joinedData = google.visualization.data.join(interIncorrectData, interCorrectData, 'full', [[0, 0]], [1], [1]);
      	
      	

	        
	        var options = {'title':'Overall Scoring Distribution: Holmberg Intersection',
                    'width':chartWidth,
                    'height':chartHeight,
                    'pointSize': 5,
                    'color': 'yellow',
                    series: {
                        0: { color: 'red' },
                     	1: {color: 'green'},

                       
                      },
                      vAxis: {title: "Score (higher is better)"},
                      hAxis: {title: "fraction matches"},
                    };

	        // Instantiate and draw our chart, passing in some options.
	        var chart = new google.visualization.LineChart(document.getElementById('intersectchart_div'));
	        chart.draw(joinedData, options);
	        
	      }
      	              
      	              
</script>

<script type="text/javascript" src="https://www.google.com/jsapi"></script>
    <script type="text/javascript">
		google.setOnLoadCallback(drawDTWChart);

      // Callback that creates and populates a data table,
      // instantiates the pie chart, passes in the data and
      // draws it.
      function drawDTWChart() {

        // Create the data table.
        var dtwCorrectData = new google.visualization.DataTable();
        dtwCorrectData.addColumn('number', 'score');
        dtwCorrectData.addColumn('number', 'matching');
        
        dtwCorrectData.addRows([
                                  
         <%
         Collections.sort(dtwCorrectValues);
        
      	  for(int y=0;y<dtwCorrectValues.size();y++){
      		double position=(double)y/dtwCorrectValues.size();
    		  
      		  %>
      		  [<%=position %>,<%=dtwCorrectValues.get(y) %>],
      		  <%
      	  }           
      	%>              
		]);
      	
      	
      	
     	 // Create the data table.
       var dtwIncorrectData = new google.visualization.DataTable();
       dtwIncorrectData.addColumn('number', 'score');
       dtwIncorrectData.addColumn('number', 'nonmatching');
     	
       
       dtwIncorrectData.addRows([
		<%
         Collections.sort(dtwValues);
        
      	  for(int y=0;y<dtwValues.size();y++){
      		  double position=(double)y/dtwValues.size();
      		  %>
      		  [<%=position %>,<%=dtwValues.get(y) %>],
      		  <%
      	  }           
      	%>           
     	               
     	               
		]);
      	
      	
      	
      	var joinedData = google.visualization.data.join(dtwIncorrectData, dtwCorrectData, 'full', [[0, 0]], [1], [1]);
      	
      	

	        
	        var options = {'title':'Overall Scoring Distribution: FastDTW',
                    'width':chartWidth,
                    'height':chartHeight,
                    'pointSize': 5,
                    'color': 'yellow',
                    series: {
                        0: { color: 'red' },
                     	1: {color: 'green'},

                       
                      },
                      vAxis: {title: "Score (lower is better)"},
                      hAxis: {title: "fraction matches"},
                    };

	        // Instantiate and draw our chart, passing in some options.
	        var chart = new google.visualization.LineChart(document.getElementById('dtwchart_div'));
	        chart.draw(joinedData, options);
	        
	      }
      	              
      	              
</script>

<script type="text/javascript" src="https://www.google.com/jsapi"></script>
    <script type="text/javascript">
		google.setOnLoadCallback(drawI3SChart);

      // Callback that creates and populates a data table,
      // instantiates the pie chart, passes in the data and
      // draws it.
      function drawI3SChart() {

        // Create the data table.
        var i3sCorrectData = new google.visualization.DataTable();
        i3sCorrectData.addColumn('number', 'score');
        i3sCorrectData.addColumn('number', 'matching');
        
        i3sCorrectData.addRows([
                                  
         <%
         Collections.sort(i3sCorrectValues);
        
      	  for(int y=0;y<i3sCorrectValues.size();y++){
      		double position=(double)y/i3sCorrectValues.size();
    		  if(i3sCorrectValues.get(y)<2){
      		  %>
      		  [<%=position %>,<%=i3sCorrectValues.get(y) %>],
      		  <%
      	  }           
      }
      	%>              
		]);
      	
      	
      	
     	 // Create the data table.
       var i3sIncorrectData = new google.visualization.DataTable();
       i3sIncorrectData.addColumn('number', 'score');
       i3sIncorrectData.addColumn('number', 'nonmatching');
     	
       
       i3sIncorrectData.addRows([
		<%
         Collections.sort(i3sValues);
        
      	  for(int y=0;y<i3sValues.size();y++){
      		  double position=(double)y/i3sValues.size();
      		if(i3sValues.get(y)<2){
      		  %>
      		  [<%=position %>,<%=i3sValues.get(y) %>],
      		  <%
      		}
      	  }           
      	%>           
     	               
     	               
		]);
      	
      	
      	
      	var joinedData = google.visualization.data.join(i3sIncorrectData, i3sCorrectData, 'full', [[0, 0]], [1], [1]);
      	
      	

	        
	        var options = {'title':'Overall Scoring Distribution: Modified I3S with Improved Affine Transform',
                    'width':chartWidth,
                    'height':chartHeight,
                    'pointSize': 5,
                    'color': 'yellow',
                    series: {
                        0: { color: 'red' },
                     	1: {color: 'green'},

                       
                      },
                      vAxis: {title: "Score (lower is better)"},
                      hAxis: {title: "fraction matches"},
                    };

	        // Instantiate and draw our chart, passing in some options.
	        var chart = new google.visualization.LineChart(document.getElementById('i3schart_div'));
	        chart.draw(joinedData, options);
	        
	      }
      	              
      	              
</script>

<script type="text/javascript" src="https://www.google.com/jsapi"></script>
    <script type="text/javascript">
		google.setOnLoadCallback(drawProportionsChart);

      // Callback that creates and populates a data table,
      // instantiates the pie chart, passes in the data and
      // draws it.
      function drawProportionsChart() {

        // Create the data table.
        var proportionCorrectData = new google.visualization.DataTable();
        proportionCorrectData.addColumn('number', 'score');
        proportionCorrectData.addColumn('number', 'matching');
        
        proportionCorrectData.addRows([
                                  
         <%
         Collections.sort(proportionCorrectValues);
        
      	  for(int y=0;y<proportionCorrectValues.size();y++){
      		double position=(double)y/proportionCorrectValues.size();
    		  if(proportionCorrectValues.get(y)<5){
      		  %>
      		  [<%=position %>,<%=proportionCorrectValues.get(y) %>],
      		  <%
    		  }
      	  }           
      	%>              
		]);
      	
      	
      	
     	 // Create the data table.
       var proportionIncorrectData = new google.visualization.DataTable();
       proportionIncorrectData.addColumn('number', 'score');
       proportionIncorrectData.addColumn('number', 'nonmatching');
     	
       
       proportionIncorrectData.addRows([
		<%
         Collections.sort(proportionValues);
        
      	  for(int y=0;y<proportionValues.size();y++){
      		 if(proportionValues.get(y)<5){
      		  double position=(double)y/proportionValues.size();
      		  %>
      		  [<%=position %>,<%=proportionValues.get(y) %>],
      		  <%
      		 }
      	  }           
      	%>           
     	               
     	               
		]);
      	
      	
      	
      	var joinedData = google.visualization.data.join(proportionIncorrectData, proportionCorrectData, 'full', [[0, 0]], [1], [1]);
      	
      	

	        
	        var options = {'title':'Overall Scoring Distribution: Fluke Proportions (height-width)',
                    'width':chartWidth,
                    'height':chartHeight,
                    'pointSize': 5,
                    'color': 'yellow',
                    series: {
                        0: { color: 'red' },
                     	1: {color: 'green'},

                       
                      },
                      vAxis: {title: "Score (lower is better)"},
                      hAxis: {title: "fraction matches"},
                    };

	        // Instantiate and draw our chart, passing in some options.
	        var chart = new google.visualization.LineChart(document.getElementById('proportionchart_div'));
	        chart.draw(joinedData, options);
	        
	      }
      	              
      	              
</script>

<script type="text/javascript" src="https://www.google.com/jsapi"></script>
    <script type="text/javascript">
		google.setOnLoadCallback(drawMSMChart);

      // Callback that creates and populates a data table,
      // instantiates the pie chart, passes in the data and
      // draws it.
      function drawMSMChart() {

        // Create the data table.
        var msmCorrectData = new google.visualization.DataTable();
        msmCorrectData.addColumn('number', 'score');
        msmCorrectData.addColumn('number', 'matching');
        
        msmCorrectData.addRows([
                                  
         <%
         Collections.sort(msmCorrectValues);
        
      	  for(int y=0;y<msmCorrectValues.size();y++){
      		double position=(double)y/msmCorrectValues.size();
    		  
      		  %>
      		  [<%=position %>,<%=msmCorrectValues.get(y) %>],
      		  <%
      	  }           
      	%>              
		]);
      	
      	
      	
     	 // Create the data table.
       var msmIncorrectData = new google.visualization.DataTable();
       msmIncorrectData.addColumn('number', 'score');
       msmIncorrectData.addColumn('number', 'nonmatching');
     	
       
       msmIncorrectData.addRows([
		<%
         Collections.sort(msmValues);
        
      	  for(int y=0;y<msmValues.size();y++){
      		  double position=(double)y/msmValues.size();
      		  %>
      		  [<%=position %>,<%=msmValues.get(y) %>],
      		  <%
      	  }           
      	%>           
     	               
     	               
		]);
      	
      	
      	
      	var joinedData = google.visualization.data.join(msmIncorrectData, msmCorrectData, 'full', [[0, 0]], [1], [1]);
      	
      	

	        
	        var options = {'title':'Overall Scoring Distribution: MSM',
                    'width':chartWidth,
                    'height':chartHeight,
                    'pointSize': 5,
                    'color': 'yellow',
                    series: {
                        0: { color: 'red' },
                     	1: {color: 'green'},

                       
                      },
                      vAxis: {title: "Score (lower is better)"},
                      hAxis: {title: "fraction matches"},
                    };

	        // Instantiate and draw our chart, passing in some options.
	        var chart = new google.visualization.LineChart(document.getElementById('msmchart_div'));
	        chart.draw(joinedData, options);
	        
	      }
      	              
      	              
</script>


<h1>Algorithm Analysis</h1>

<h2>Overall Scoring</h2>

<div id="overallchart_div"></div>
<p>Average match vs non-match score diff per encounter: <%=(correctScoreTotal/numCorrectScores-incorrectScoreTotal/numIncorrectScores) %></p>

<h2>Individual Algorithm Behavior</h2>

<div id="intersectchart_div"></div>

<div id="dtwchart_div"></div>

<div id="i3schart_div"></div>

<div id="dtwchart_div"></div>

<div id="proportionchart_div"></div>

<div id="msmchart_div"></div>






<%
} 
catch(Exception ex) {

	ex.printStackTrace();
}
%>


</body>
</html>
