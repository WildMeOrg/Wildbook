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
	weka.classifiers.meta.AdaBoostM1,
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

<jsp:include page="../header.jsp" flush="true"/>

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
File classifierFile=new File(pathToClassifierFile);
long dateModified=classifierFile.lastModified();
LocalDateTime dt=new LocalDateTime(dateModified);
DateTimeFormatter fmt = ISODateTimeFormat.date();
String strOutputDateTime = fmt.print(dt);

System.out.println("     I expect to find a classifier file here: "+pathToClassifierFile);
System.out.println("     I expect to find an instances file here: "+instancesFileFullPath);

Instances instances=TrainNetwork.getWekaInstances(request, instancesFileFullPath);



Classifier booster=TrainNetwork.getWekaClassifier(request, pathToClassifierFile, instances);
//String optionString = "-P 100 -S 1 -I 10 -W weka.classifiers.trees.RandomForest -- -I 100 -K 0 -S 1";
//booster.setOptions(weka.core.Utils.splitOptions(optionString));


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
Hashtable<Double,Integer> bayesOverallHashtable = new Hashtable<Double,Integer>();

ArrayList<Double> intersectionValues=new ArrayList<Double>();
ArrayList<Double> dtwValues=new ArrayList<Double>();
ArrayList<Double> i3sValues=new ArrayList<Double>();
ArrayList<Double> proportionValues=new ArrayList<Double>();
ArrayList<Double> msmValues=new ArrayList<Double>();
ArrayList<Double> swaleValues=new ArrayList<Double>();
ArrayList<Double> eucValues=new ArrayList<Double>();

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
ArrayList<Double> swaleCorrectValues=new ArrayList<Double>();
ArrayList<Double> eucCorrectValues=new ArrayList<Double>();

//adaboost avg score comparison
double correctScoreTotal=0;
int numCorrectScores=0;
double incorrectScoreTotal=0;
int numIncorrectScores=0;



int numInstances=instances.numInstances();

if(numInstances>5000)numInstances=5000;

for(int i=0;i<numInstances;i++){
	
	Instance myInstance=instances.instance(i);
	//System.out.println("myInstance: "+myInstance.toString());
	//if(((enc1.getSpots()!=null)&&(enc1.getSpots().size()>0)&&(enc1.getRightSpots()!=null))&&((enc1.getRightSpots().size()>0))&&((enc2.getSpots()!=null)&&(enc2.getSpots().size()>0)&&(enc2.getRightSpots()!=null)&&((enc2.getRightSpots().size()>0)))){
        try{
          
          //if both have spots, then we need to compare them
       
          //first, are they the same animal?
          //default is 1==no
          double output=1;
      

          //System.out.println(myInstance.stringValue(5));
          if(myInstance.stringValue(9).equals("match")){output=0;}
          
          
          //HolmbergIntersection
          Double numIntersections=new Double(myInstance.value(0));
          
          java.lang.Double distance = new Double(myInstance.value(1));
          
          
          //I3S
          double i3sScore=new Double(myInstance.value(2)).doubleValue();
          //Proportion metric
          double proportion=new Double(myInstance.value(3)).doubleValue();
          
          
          
          Double msmValue=new Double(myInstance.value(4));
          
          Double swaleValue=new Double(myInstance.value(5));
          
          Double dateDiff=new Double(myInstance.value(6));
          
          Double eucValue=new Double(myInstance.value(7));
          
          Double patterningDiff=new Double(myInstance.value(8));
        
     
          Instance iExample = new DenseInstance(TrainNetwork.getWekaAttributesPerSpecies(genusSpecies).size()-1);
          
          iExample.setDataset(instances);
          iExample.setValue(0, numIntersections.doubleValue());
          iExample.setValue(1, distance);
          iExample.setValue(2,  i3sScore);
          iExample.setValue(3, (new Double(proportion).doubleValue()));
          iExample.setValue(4, (new Double(msmValue).doubleValue()));
          iExample.setValue(5, (new Double(swaleValue).doubleValue()));
          iExample.setValue(6, (new Double(dateDiff).doubleValue()));
          iExample.setValue(7, (new Double(eucValue).doubleValue()));
          iExample.setValue(8, (new Double(patterningDiff).doubleValue()));
          
          
          double[] fDistribution = booster.distributionForInstance(iExample);

          
          //double thisScore=TrainNetwork.getOverallFlukeMatchScore(request, numIntersections, distance.doubleValue(), i3sScore, new Double(proportion),intersectionStats,dtwStats,i3sStats, proportionStats, intersectionStdDev,dtwStdDev,i3sStdDev,proportionStdDev,intersectHandicap, dtwHandicap,i3sHandicap,proportionHandicap);
          double thisScore=TrainNetwork.round(fDistribution[0], 6);

          //getOverallFlukeMatchScore(HttpServletRequest request, double intersectionsValue, double dtwValue, double i3sValue, double proportionsValue, double numStandardDevs, SummaryStatistics intersectionStats, SummaryStatistics dtwStats,SummaryStatistics i3sStats, SummaryStatistics proportionStats)
            if(output==0){
            	
            	numMatchLinks++;
            	
            	//overall
            	Double score=(new Double(TrainNetwork.round(thisScore,7))); 
            
            	if(overallCorrectHashtable.get(score)==null){
            		overallCorrectHashtable.put(score, 0);
            		
            	}

            	Integer numValue=overallCorrectHashtable.get(score).intValue()+1;
            	//Integer numBayesValue=bayesOverallCorrectHashtable.get(bayesScore).intValue()+1;
            	
            	overallCorrectHashtable.put(score, numValue);
            	//bayesOverallCorrectHashtable.put(bayesScore, numBayesValue);
            	correctScoreTotal+=score;
            	numCorrectScores++;
            	//correctBayesScoreTotal+=bayesScore;
            	
            	//intersection
            	intersectionCorrectValues.add(numIntersections);
            	
            	//FastDTW
            	dtwCorrectValues.add(distance);
            	
            	//I3S
            	i3sCorrectValues.add(i3sScore);
            	
            	//Proportion
            	proportionCorrectValues.add(proportion);
            	
            	msmCorrectValues.add(msmValue);
            	
            	swaleCorrectValues.add(swaleValue);
            	
            	eucCorrectValues.add(eucValue);
            	
            }
            else{
            	
            	numFalseLinks++;
            	//overall
            	
            	Double score=(new Double(TrainNetwork.round(thisScore,7))); 
            	//Double bayesScore=(new Double(TrainNetwork.round(thisBayesScore,4)));  
            	if(overallHashtable.get(score)==null){
            		overallHashtable.put(score, 0);
            	}
            	//if(bayesOverallHashtable.get(bayesScore)==null){
            	//	bayesOverallHashtable.put(bayesScore, 0);
            	//}
            	Integer numValue=overallHashtable.get(score).intValue()+1;
            	//Integer numBayesValue=bayesOverallHashtable.get(bayesScore).intValue()+1;
            	//bayesOverallHashtable.put(bayesScore, numBayesValue);
            	overallHashtable.put(thisScore,numValue);
            	incorrectScoreTotal+=score;
            	numIncorrectScores++;
            	//incorrectBayesScoreTotal+=bayesScore;
            	
            	//intersection
            	intersectionValues.add(numIntersections);
            	
            	//FastDTW
            	dtwValues.add(distance);
            	
            	//I3S
            	i3sValues.add(i3sScore);
            	
            	//Proportion
            	proportionValues.add(proportion);
            	
            	msmValues.add(msmValue);
            	
            	swaleValues.add(swaleValue);
            	
            	eucValues.add(eucValue);
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
      	
      	

	        
	        var options = {'title':'AdaBoost Overall Scoring Performance: Matches vs Non-matches',
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
    		  if(intersectionCorrectValues.get(y)<1){
      		  %>
      		 	 [<%=position %>,<%=intersectionCorrectValues.get(y) %>],
      		  <%
      	  	}
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
      		if(intersectionValues.get(y)<1){
      		  %>
      		  [<%=position %>,<%=intersectionValues.get(y) %>],
      		  <%
      		}
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
    		  if(msmCorrectValues.get(y)<15){
      		  %>
      		  [<%=position %>,<%=msmCorrectValues.get(y) %>],
      		  <%
    		  }
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
      		if(msmValues.get(y)<15){
      		  %>
      		  [<%=position %>,<%=msmValues.get(y) %>],
      		  <%
      	  }    
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


<script type="text/javascript" src="https://www.google.com/jsapi"></script>
    <script type="text/javascript">
		google.setOnLoadCallback(drawSwaleChart);

      // Callback that creates and populates a data table,
      // instantiates the pie chart, passes in the data and
      // draws it.
      function drawSwaleChart() {

        // Create the data table.
        var swaleCorrectData = new google.visualization.DataTable();
        swaleCorrectData.addColumn('number', 'score');
        swaleCorrectData.addColumn('number', 'matching');
        
        swaleCorrectData.addRows([
                                  
         <%
         Collections.sort(swaleCorrectValues);
        
      	  for(int y=0;y<swaleCorrectValues.size();y++){
      		double position=(double)y/swaleCorrectValues.size();
    	
      		  %>
      		  [<%=position %>,<%=swaleCorrectValues.get(y) %>],
      		  <%
    	
      	  }           
      	%>              
		]);
      	
      	
      	
     	 // Create the data table.
       var swaleIncorrectData = new google.visualization.DataTable();
       swaleIncorrectData.addColumn('number', 'score');
       swaleIncorrectData.addColumn('number', 'nonmatching');
     	
       
       swaleIncorrectData.addRows([
		<%
         Collections.sort(swaleValues);
		
      	  for(int y=0;y<swaleValues.size();y++){
      		  double position=(double)y/swaleValues.size();
      		  %>
      		  [<%=position %>,<%=swaleValues.get(y) %>],
      		  <%
      	    
		}
      	%>           
     	               
     	               
		]);
      	
      	
      	
      	var joinedData = google.visualization.data.join(swaleIncorrectData, swaleCorrectData, 'full', [[0, 0]], [1], [1]);
      	
      	

	        
	        var options = {'title':'Overall Scoring Distribution: Swale',
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
	        var chart = new google.visualization.LineChart(document.getElementById('swalechart_div'));
	        chart.draw(joinedData, options);
	        
	      }
      	              
      	              
</script>

<script type="text/javascript" src="https://www.google.com/jsapi"></script>
    <script type="text/javascript">
		google.setOnLoadCallback(drawEucChart);

      // Callback that creates and populates a data table,
      // instantiates the pie chart, passes in the data and
      // draws it.
      function drawEucChart() {

        // Create the data table.
        var eucCorrectData = new google.visualization.DataTable();
        eucCorrectData.addColumn('number', 'score');
        eucCorrectData.addColumn('number', 'matching');
        
        eucCorrectData.addRows([
                                  
         <%
         Collections.sort(eucCorrectValues);
        
      	  for(int y=0;y<eucCorrectValues.size();y++){
      		double position=(double)y/eucCorrectValues.size();
    	
      		  %>
      		  [<%=position %>,<%=eucCorrectValues.get(y) %>],
      		  <%
    	
      	  }           
      	%>              
		]);
      	
      	
      	
     	 // Create the data table.
       var eucIncorrectData = new google.visualization.DataTable();
       eucIncorrectData.addColumn('number', 'score');
       eucIncorrectData.addColumn('number', 'nonmatching');
     	
       
       eucIncorrectData.addRows([
		<%
         Collections.sort(eucValues);
		
      	  for(int y=0;y<eucValues.size();y++){
      		  double position=(double)y/eucValues.size();
      		  %>
      		  [<%=position %>,<%=eucValues.get(y) %>],
      		  <%
      	    
		}
      	%>           
     	               
     	               
		]);
      	
      	
      	
      	var joinedData = google.visualization.data.join(eucIncorrectData, eucCorrectData, 'full', [[0, 0]], [1], [1]);
      	
      	

	        
	        var options = {'title':'Overall Scoring Distribution: Euclidean Distance',
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
	        var chart = new google.visualization.LineChart(document.getElementById('eucchart_div'));
	        chart.draw(joinedData, options);
	        
	      }
      	              
      	              
</script>

<div class="container maincontent">

<h1>Algorithm Analysis: <%=genusSpecies %></h1>
<p>This page helps us understand how the individual algorithms and the overall AdaBoost are performing across the training set of data.</p>
<p>This classifier file was built on: <%=strOutputDateTime %></p>
<ul>
<li>Matches: <%=msmCorrectValues.size() %></li>
<li>Nonmatches: <%=msmValues.size() %></li>
</ul>

<h2>Overall Scoring</h2>

<div id="overallchart_div"></div>
<p>AdaBoost: Average match vs non-match score diff per encounter: <%=(correctScoreTotal/numCorrectScores-incorrectScoreTotal/numIncorrectScores) %></p>



<h2>Individual Algorithm Behavior</h2>

<h3>Holmberg Intersection</h3>
<p>The higher the green line is above the red line, the better the algorithm performs.</p>
<div id="intersectchart_div"></div>

<h3>FastDTW</h3>
<p>The lower the green line is below the red line, the better the algorithm is performing.</p>
<div id="dtwchart_div"></div>

<h3>Modified I3S (Spot)</h3>
<p>The lower the green line is below the red line, the better the algorithm is performing.</p>
<div id="i3schart_div"></div>

<h3>Proportional Comparison</h3>
<p>The lower the green line is below the red line, the better the algorithm is performing.</p>
<div id="proportionchart_div"></div>

<h3>Merge-Split-Move (MSM)</h3>
<p>The lower the green line is below the red line, the better the algorithm is performing. <br>
<a href="http://vlm1.uta.edu/~athitsos/publications/stefan_tkde2012_preprint.pdf">More info...</a></p>
<div id="msmchart_div"></div>

<h3>Sequence Weighted Alignment (Swale)</h3>
<p>The higher the green line is above the red line, the better the algorithm is performing. <br>
<a href="http://wwweb.eecs.umich.edu/db/files/sigmod07timeseries.pdf">More info...</a></p>
<div id="swalechart_div"></div>

<h3>Euclidean Distance</h3>
<p>The lower the green line is below the red line, the better the algorithm is performing. <br>
</p>
<div id="eucchart_div"></div>


</div>
<h2>Testing Success</h2>
<%

//Instances train = ...   // from somewhere
//Instances test = ...    // from somewhere

int numTestInstances=25;
if(request.getParameter("numTestInstances")!=null){
	numTestInstances=(new Integer(request.getParameter("numTestInstances"))).intValue();
	if(numTestInstances>instances.numInstances()){numTestInstances=25;}
}
int numTrainingInstances=numTestInstances;
if(request.getParameter("numTrainingInstances")!=null){
	numTrainingInstances=(new Integer(request.getParameter("numTrainingInstances"))).intValue();
	if(numTrainingInstances>instances.numInstances()){numTrainingInstances=numTestInstances;}
}
double falseClassMultiplier=2;
if(request.getParameter("falseClassMultiplier")!=null){
	falseClassMultiplier=(new Double(request.getParameter("falseClassMultiplier"))).doubleValue();
}

//prep weka for AdaBoost
// Declare numeric attributes
Attribute intersectAttr = new Attribute("intersect");
Attribute fastDTWAttr = new Attribute("fastDTW");
Attribute i3sAttr = new Attribute("I3S");
Attribute proportionAttr = new Attribute("proportion");
Attribute msmAttr = new Attribute("MSM");

//class vector
// Declare the class attribute along with its values
ArrayList fvClassVal = new ArrayList(2);
fvClassVal.add("match");
fvClassVal.add("nonmatch");
Attribute ClassAttribute = new Attribute("theClass", fvClassVal);
Attribute swaleAttr = new Attribute("Swale");     
Attribute dateAttr = new Attribute("dateDiffLong");  
Attribute eucAttr = new Attribute("EuclideanDistance");  
Attribute patterningCodeDiffAttr = new Attribute("PatterningCodeDiff"); 

//define feature vector
// Declare the feature vector
ArrayList fvWekaAttributes = new ArrayList(6);
fvWekaAttributes.add(intersectAttr);
fvWekaAttributes.add(fastDTWAttr);
fvWekaAttributes.add(i3sAttr);
fvWekaAttributes.add(proportionAttr);
fvWekaAttributes.add(msmAttr);
fvWekaAttributes.add(swaleAttr);
fvWekaAttributes.add(dateAttr);
fvWekaAttributes.add(eucAttr);
fvWekaAttributes.add(patterningCodeDiffAttr);
fvWekaAttributes.add(ClassAttribute);


Instances isTrainingSet = new Instances("Rel", fvWekaAttributes, numTestInstances);
isTrainingSet.setClassIndex(7);

Instances classifierSet = new Instances("Rel", fvWekaAttributes, numTestInstances);
classifierSet.setClassIndex(7);

int sampledTrueInstances=0;
while(sampledTrueInstances<numTestInstances){
	Random myRan=new Random();
	int selected=myRan.nextInt(instances.numInstances()-1);
	Instance popMe=instances.instance(selected);
	if(popMe.stringValue(9).equals("match")){
		instances.delete(selected);
		isTrainingSet.add(popMe);
		sampledTrueInstances++;
	}
}
int sampledFalseInstances=0;
while(sampledFalseInstances<numTestInstances){
	Random myRan=new Random();
	int selected=myRan.nextInt(instances.numInstances()-1);
	Instance popMe=instances.instance(selected);
	if(popMe.stringValue(9).equals("nonmatch")){
		instances.delete(selected);
		isTrainingSet.add(popMe);
		sampledFalseInstances++;
	}
}

int sampledTrueClassInstances=0;
while(sampledTrueClassInstances<numTrainingInstances){
	Random myRan=new Random();
	int selected=myRan.nextInt(instances.numInstances()-1);
	Instance popMe=instances.instance(selected);
	if(popMe.stringValue(9).equals("match")){
		instances.delete(selected);
		classifierSet.add(popMe);
		sampledTrueClassInstances++;
	}
}
int sampledFalseClassInstances=0;
while(sampledFalseClassInstances<(numTrainingInstances*falseClassMultiplier)){
	Random myRan=new Random();
	int selected=myRan.nextInt(instances.numInstances()-1);
	Instance popMe=instances.instance(selected);
	if(popMe.stringValue(9).equals("nonmatch")){
		instances.delete(selected);
		classifierSet.add(popMe);
		sampledFalseClassInstances++;
	}
}

//AdaBoostM1 cls=new AdaBoostM1();
//cls.buildClassifier(instances);
//cls.setOptions(weka.core.Utils.splitOptions(optionString));


// evaluate classifier and print some statistics
//Evaluation eval = new Evaluation(isTrainingSet);
//eval.evaluateModel(cls, isTrainingSet);


%>
TBD
<%
} 
catch(Exception ex) {

	ex.printStackTrace();
}
%>

<jsp:include page="../footer.jsp" flush="true"/>
