<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.servlet.ServletUtilities,java.text.DecimalFormat,javax.jdo.*,org.ecocean.genetics.*,java.util.*,java.net.URI, org.ecocean.*,org.ecocean.Util.MeasurementDesc,org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics" %>


  <%

  
  //get our Shepherd
  String context="context0";
  context=ServletUtilities.getContext(request);

    //let's load encounterSearch.properties
    //String langCode = "en";
  String langCode=ServletUtilities.getLanguageCode(request);
  
    Properties encprops = new Properties();
    //encprops.load(getClass().getResourceAsStream("/bundles/" + langCode + "/individualSearchResultsAnalysis.properties"));
    encprops = ShepherdProperties.getProperties("individualSearchResultsAnalysis.properties", langCode,context);


    Properties measurementLabels=ShepherdProperties.getProperties("commonConfigurationLabels.properties", langCode, context);
    
    
    
    
    Properties haploprops = new Properties();
    //haploprops.load(getClass().getResourceAsStream("/bundles/haplotypeColorCodes.properties"));
	haploprops=ShepherdProperties.getProperties("haplotypeColorCodes.properties", "",context);
   
    
    
    Shepherd myShepherd = new Shepherd(context);
    myShepherd.setAction("individualSearchResultsAnalysis.jsp");
    
    DecimalFormat df = new DecimalFormat("#.##");

    int numResults = 0;
    int numResultsWithTissueSamples=0;
    int numResultsWithGeneticSex=0;
    int numResultsWithMsMarkers=0;
    int numResultsWithHaplotype=0;
    
	//prep for measurements summary
	List<MeasurementDesc> measurementTypes=Util.findMeasurementDescs("en",context);
	int numMeasurementTypes=measurementTypes.size();
	SynchronizedSummaryStatistics[] measurementValues=new SynchronizedSummaryStatistics[numMeasurementTypes];
	SynchronizedSummaryStatistics[] measurementValuesMales=new SynchronizedSummaryStatistics[numMeasurementTypes];
	SynchronizedSummaryStatistics[] measurementValuesFemales=new SynchronizedSummaryStatistics[numMeasurementTypes];
	SynchronizedSummaryStatistics[] measurementValuesNew=new SynchronizedSummaryStatistics[numMeasurementTypes];
	SynchronizedSummaryStatistics[] measurementValuesResights=new SynchronizedSummaryStatistics[numMeasurementTypes];
	String[] smallestIndies=new String[numMeasurementTypes];
	String[] largestIndies=new String[numMeasurementTypes];
	for(int b=0;b<measurementValues.length;b++){
		measurementValues[b]=new SynchronizedSummaryStatistics();
		measurementValuesMales[b]=new SynchronizedSummaryStatistics();
		measurementValuesFemales[b]=new SynchronizedSummaryStatistics();
		measurementValuesNew[b]=new SynchronizedSummaryStatistics();
		measurementValuesResights[b]=new SynchronizedSummaryStatistics();
		smallestIndies[b]="";
		largestIndies[b]="";
	}

	
	//prep for biomeasurements summary
	List<MeasurementDesc> bioMeasurementTypes=Util.findBiologicalMeasurementDescs("en",context);
	int numBioMeasurementTypes=bioMeasurementTypes.size();
	SynchronizedSummaryStatistics[] bioMeasurementValues=new SynchronizedSummaryStatistics[numBioMeasurementTypes];
	SynchronizedSummaryStatistics[] bioMeasurementValuesMales=new SynchronizedSummaryStatistics[numBioMeasurementTypes];
	SynchronizedSummaryStatistics[] bioMeasurementValuesFemales=new SynchronizedSummaryStatistics[numBioMeasurementTypes];
	SynchronizedSummaryStatistics[] bioMeasurementValuesNew=new SynchronizedSummaryStatistics[numBioMeasurementTypes];
	SynchronizedSummaryStatistics[] bioMeasurementValuesResights=new SynchronizedSummaryStatistics[numBioMeasurementTypes];
	String[] bioSmallestIndies=new String[numBioMeasurementTypes];
	String[] bioLargestIndies=new String[numBioMeasurementTypes];
	for(int b=0;b<bioMeasurementValues.length;b++){
		bioMeasurementValues[b]=new SynchronizedSummaryStatistics();
		bioMeasurementValuesMales[b]=new SynchronizedSummaryStatistics();
		bioMeasurementValuesFemales[b]=new SynchronizedSummaryStatistics();
		bioMeasurementValuesNew[b]=new SynchronizedSummaryStatistics();
		bioMeasurementValuesResights[b]=new SynchronizedSummaryStatistics();
		bioSmallestIndies[b]="";
		bioLargestIndies[b]="";
	}
	
	//retrieve dates from the URL
 int day1 = 1, day2 = 31, month1 = 1, month2 = 12, year1 = 0, year2 = 3000;
    try {
      month1 = (new Integer(request.getParameter("month1"))).intValue();
    } catch (NumberFormatException nfe) {
    }
    try {
      month2 = (new Integer(request.getParameter("month2"))).intValue();
    } catch (NumberFormatException nfe) {
    }
    try {
      year1 = (new Integer(request.getParameter("year1"))).intValue();
    } catch (NumberFormatException nfe) {
    }
    try {
      year2 = (new Integer(request.getParameter("year2"))).intValue();
    } catch (NumberFormatException nfe) {
    }

	
	
    //kick off the transaction
    myShepherd.beginDBTransaction();

    //start the query and get the results
    String order = "";
    Vector<MarkedIndividual> rIndividuals = new Vector<MarkedIndividual>();
    myShepherd.beginDBTransaction();

    MarkedIndividualQueryResult result = IndividualQueryProcessor.processQuery(myShepherd, request, order);
    rIndividuals = result.getResult();
    
    //let's prep the HashTable for the haplo pie chart
    List<String> allHaplos2=myShepherd.getAllHaplotypes(); 
    int numHaplos2 = allHaplos2.size();
    Hashtable<String,Integer> pieHashtable = new Hashtable<String,Integer>();
 	for(int gg=0;gg<numHaplos2;gg++){
 		String thisHaplo=allHaplos2.get(gg);
 		pieHashtable.put(thisHaplo, new Integer(0));
 	}
 	
 	//let's prep the max years between sightings column chart
	Query yearsCoverageQuery=myShepherd.getPM().newQuery(result.getJDOQLRepresentation().replaceFirst("SELECT FROM","SELECT max(maxYearsBetweenResightings) FROM"));
 	int numYearsCoverage=0;
 	try{numYearsCoverage=1+((Integer)yearsCoverageQuery.execute()).intValue();yearsCoverageQuery.closeAll();}
 	catch(Exception e){
 		e.printStackTrace();
 		yearsCoverageQuery.closeAll();
 	}
 	int[] resightingYearsArray=new int[numYearsCoverage];
 	for(int t=0;t<numYearsCoverage;t++){
 		resightingYearsArray[t]=0;
 	}
    
 	//let's prep the HashTable for the sex pie chart
 	Hashtable<String,Integer> sexHashtable = new Hashtable<String,Integer>();
 	sexHashtable.put("male", new Integer(0));
 	sexHashtable.put("female", new Integer(0));
 	sexHashtable.put("unknown", new Integer(0));
 	
 	//let's prep for the firstSightings table
 	Hashtable<String,Integer> firstSightingsHashtable = new Hashtable<String,Integer>();
 	firstSightingsHashtable.put("First sighting", new Integer(0));
 	firstSightingsHashtable.put("Previously sighted", new Integer(0));

	 Float maxTravelDistance=new Float(0);
	 long maxTimeBetweenResights=0;
	 String longestResightedIndividual="";
	 String farthestTravelingIndividual="";
	 

	 
 	int resultSize=rIndividuals.size();
 	 for(int y=0;y<resultSize;y++){
 		MarkedIndividual thisEnc=(MarkedIndividual)rIndividuals.get(y);
 		 
 		//genetic analysis checks
 		//and haplotype ie chart prep
 		 if(thisEnc.getHaplotype()!=null){
      	   if(pieHashtable.containsKey(thisEnc.getHaplotype().trim())){
      		   Integer thisInt = pieHashtable.get(thisEnc.getHaplotype().trim())+1;
      		   pieHashtable.put(thisEnc.getHaplotype().trim(), thisInt);
      		   numResultsWithHaplotype++;
      	   }
 	 	} 
 		if(thisEnc.hasMsMarkers()){numResultsWithMsMarkers++;} 
 		if(thisEnc.hasGeneticSex()){numResultsWithGeneticSex++;}
 		
 		//measurement
		for(int b=0;b<numMeasurementTypes;b++){
			if(thisEnc.getAverageMeasurementInPeriod(year1, month1, year2, month2, measurementTypes.get(b).getType())!=null){
				
					measurementValues[b].addValue(thisEnc.getAverageMeasurementInPeriod(year1, month1, year2, month2, measurementTypes.get(b).getType()).doubleValue());
					
					//smallest vs largest analysis
					if(thisEnc.getAverageMeasurementInPeriod(year1, month1, year2, month2, measurementTypes.get(b).getType()).doubleValue()<=measurementValues[b].getMin()){
						smallestIndies[b]=thisEnc.getIndividualID();
					}
					else if(thisEnc.getAverageMeasurementInPeriod(year1, month1, year2, month2, measurementTypes.get(b).getType()).doubleValue()>=measurementValues[b].getMax()){
						largestIndies[b]=thisEnc.getIndividualID();
					}
					
					//males versus females analysis
					if((thisEnc.getSex()!=null)&&(thisEnc.getSex().equals("male"))){
						measurementValuesMales[b].addValue(thisEnc.getAverageMeasurementInPeriod(year1, month1, year2, month2, measurementTypes.get(b).getType()).doubleValue());
					}
					else if((thisEnc.getSex()!=null)&&(thisEnc.getSex().equals("female"))){
						measurementValuesFemales[b].addValue(thisEnc.getAverageMeasurementInPeriod(year1, month1, year2, month2, measurementTypes.get(b).getType()).doubleValue());
					}
					
					//first sights vs resights analysis
					 if(thisEnc.getEarliestSightingTime()<(new GregorianCalendar(year1,(month1-1),day1)).getTimeInMillis()){
						 measurementValuesResights[b].addValue(thisEnc.getAverageMeasurementInPeriod(year1, month1, year2, month2, measurementTypes.get(b).getType()).doubleValue());
							
				 		   
					 }
					 else{
						 measurementValuesNew[b].addValue(thisEnc.getAverageMeasurementInPeriod(year1, month1, year2, month2, measurementTypes.get(b).getType()).doubleValue());
							
					 }
					
					
					
			}
		}
		
 		//biomeasurement tabulation
		for(int b=0;b<numBioMeasurementTypes;b++){
			if(thisEnc.getAverageBiologicalMeasurementInPeriod(year1, month1, year2, month2, bioMeasurementTypes.get(b).getType())!=null){
				
					double val=thisEnc.getAverageBiologicalMeasurementInPeriod(year1, month1, year2, month2, bioMeasurementTypes.get(b).getType()).doubleValue();
				
					bioMeasurementValues[b].addValue(val);
					//System.out.println(bioMeasurementTypes.get(b).getType()+":"+val);
					//smallest vs largest analysis
					if(val<=bioMeasurementValues[b].getMin()){
						bioSmallestIndies[b]=thisEnc.getIndividualID();
					}
					else if(val>=bioMeasurementValues[b].getMax()){
						bioLargestIndies[b]=thisEnc.getIndividualID();
					}
					
					//males versus females analysis
					if((thisEnc.getSex()!=null)&&(thisEnc.getSex().equals("male"))){
						bioMeasurementValuesMales[b].addValue(val);
					}
					else if((thisEnc.getSex()!=null)&&(thisEnc.getSex().equals("female"))){
						bioMeasurementValuesFemales[b].addValue(val);
					}
					
					//first sights vs resights analysis
					 if(thisEnc.getEarliestSightingTime()<(new GregorianCalendar(year1,(month1-1),day1)).getTimeInMillis()){
						 bioMeasurementValuesResights[b].addValue(val);
							
				 		   
					 }
					 else{
						 bioMeasurementValuesNew[b].addValue(val);
							
					 }
					
					
					
			}
		}
		
 		 
 	    //sex pie chart 	
 	    if(thisEnc.getSex()!=null){
 			if(thisEnc.getSex().equals("male")){
 		   		Integer thisInt = sexHashtable.get("male")+1;
  		   		sexHashtable.put("male", thisInt);
 			}
 			else if(thisEnc.getSex().equals("female")){
  		   		Integer thisInt = sexHashtable.get("female")+1;
  		   		sexHashtable.put("female", thisInt);
 			}
 	   		else{
 	    		Integer thisInt = sexHashtable.get("unknown")+1;
   		    	sexHashtable.put("unknown", thisInt);
 	    	}
 	    }
 	    else{
 	    	Integer thisInt = sexHashtable.get("unknown")+1;
   		    sexHashtable.put("unknown", thisInt);
 	    }
 	    
		 //max distance calc
		 if (thisEnc.getMaxDistanceBetweenTwoSightings()>maxTravelDistance){
			 maxTravelDistance=thisEnc.getMaxDistanceBetweenTwoSightings();
			 farthestTravelingIndividual=thisEnc.getIndividualID();
		 }
		 
		 //max time calc
		 if (thisEnc.getMaxTimeBetweenTwoSightings()>maxTimeBetweenResights){
			 maxTimeBetweenResights=thisEnc.getMaxTimeBetweenTwoSightings();
			 longestResightedIndividual=thisEnc.getIndividualID();
		 }
		 
		 //maxYearsBetweenSightings calc
		 resightingYearsArray[thisEnc.getMaxNumYearsBetweenSightings()]++;
		 
		 //firstSightings distribution
		 if(thisEnc.getEarliestSightingTime()<(new GregorianCalendar(year1,(month1-1),day1)).getTimeInMillis()){
	 		   Integer thisInt = firstSightingsHashtable.get("Previously sighted")+1;
	  		   firstSightingsHashtable.put("Previously sighted", thisInt);
	 		   
		 }
		 else{
			 Integer thisInt = firstSightingsHashtable.get("First sighting")+1;
	  		   firstSightingsHashtable.put("First sighting", thisInt);
		 }
		 
 		 
 	 }	
 	 


 	 
 	 
  %>


    <style type="text/css">
     
      #map {
        width: 600px;
        height: 400px;
      }

    </style>
  

<style type="text/css">
  #tabmenu {
    color: #000;
    border-bottom: 1px solid #CDCDCD;
    margin: 12px 0px 0px 0px;
    padding: 0px;
    z-index: 1;
    padding-left: 10px
  }

  #tabmenu li {
    display: inline;
    overflow: hidden;
    list-style-type: none;
  }

  #tabmenu a, a.active {
    color: #000;
    background: #E6EEEE;
     
    border: 1px solid #CDCDCD;
    padding: 2px 5px 0px 5px;
    margin: 0;
    text-decoration: none;
    border-bottom: 0px solid #FFFFFF;
  }

  #tabmenu a.active {
    background: #8DBDD8;
    color: #000000;
    border-bottom: 1px solid #8DBDD8;
  }

  #tabmenu a:hover {
    color: #000;
    background: #8DBDD8;
  }

  #tabmenu a:visited {
    
  }

  #tabmenu a.active:hover {
    color: #000;
    border-bottom: 1px solid #8DBDD8;
  }
  
  
  
</style>

  <jsp:include page="header.jsp" flush="true"/>
 
  
      <script>
        function getQueryParameter(name) {
          name = name.replace(/[\[]/, "\\\[").replace(/[\]]/, "\\\]");
          var regexS = "[\\?&]" + name + "=([^&#]*)";
          var regex = new RegExp(regexS);
          var results = regex.exec(window.location.href);
          if (results == null)
            return "";
          else
            return results[1];
        }
  </script>
  



    
<script type="text/javascript" src="https://www.google.com/jsapi"></script>

<script type="text/javascript">
      google.load("visualization", "1", {packages:["corechart"]});
      google.setOnLoadCallback(drawHaploChart);
      function drawHaploChart() {
        var data = new google.visualization.DataTable();
        data.addColumn('string', 'Haplotype');
        data.addColumn('number', 'No. Recorded');
        data.addRows([
          <%
          List<String> allHaplos=myShepherd.getAllHaplotypes(); 
          int numHaplos = allHaplos.size();
          

          
          for(int hh=0;hh<numHaplos;hh++){
          %>
          ['<%=allHaplos.get(hh)%>',    <%=pieHashtable.get(allHaplos.get(hh))%>]
		  <%
		  if(hh<(numHaplos-1)){
		  %>
		  ,
		  <%
		  }
          }
		  %>
          
        ]);

        var options = {
          width: 450, height: 300,
          title: '<%=encprops.getProperty("haplotypesMatchedIndividuals") %>',
          colors: [
                   <%
                   String haploColor="CC0000";
                   if((encprops.getProperty("defaultMarkerColor")!=null)&&(!encprops.getProperty("defaultMarkerColor").trim().equals(""))){
                	   haploColor=encprops.getProperty("defaultMarkerColor");
                   }   

                   
                   for(int yy=0;yy<numHaplos;yy++){
                       String haplo=allHaplos.get(yy);
                       if((haploprops.getProperty(haplo)!=null)&&(!haploprops.getProperty(haplo).trim().equals(""))){
                     	  haploColor = haploprops.getProperty(haplo);
                        }
					%>
					'#<%=haploColor%>',
					<%
                   }
                   %>
                   
                   
          ]
        };

        var chart = new google.visualization.PieChart(document.getElementById('chart_div'));
        chart.draw(data, options);
      }
      
      google.setOnLoadCallback(drawSexChart);
      function drawSexChart() {
        var data = new google.visualization.DataTable();
        data.addColumn('string', '<%=encprops.getProperty("sex") %>');
        data.addColumn('number', '<%=encprops.getProperty("numberRecorded") %>');
        data.addRows([

          ['<%=encprops.getProperty("male") %>',    <%=sexHashtable.get("male")%>],
           ['<%=encprops.getProperty("female") %>',    <%=sexHashtable.get("female")%>],
           ['<%=encprops.getProperty("unknown") %>',    <%=sexHashtable.get("unknown")%>]
          
        ]);

        <%
        haploColor="CC0000";
        if((encprops.getProperty("defaultMarkerColor")!=null)&&(!encprops.getProperty("defaultMarkerColor").trim().equals(""))){
     	   haploColor=encprops.getProperty("defaultMarkerColor");
        }
        
        %>
        var options = {
          width: 450, height: 300,
          title: '<%=encprops.getProperty("sexDistribution") %>',
          colors: ['#0000FF','#FF00FF','<%=haploColor%>']
        };

        var chart = new google.visualization.PieChart(document.getElementById('sexchart_div'));
        chart.draw(data, options);
      }
      
      
      google.setOnLoadCallback(drawFirstSightingChart);
      function drawFirstSightingChart() {
        var data = new google.visualization.DataTable();
        data.addColumn('string', '<%=encprops.getProperty("status") %>');
        data.addColumn('number', '<%=encprops.getProperty("numberRecorded") %>');
        data.addRows([

          ['<%=encprops.getProperty("firstSighting") %>',    <%=firstSightingsHashtable.get("First sighting")%>],
           ['<%=encprops.getProperty("previouslyIdentified") %>',    <%=firstSightingsHashtable.get("Previously sighted")%>]
           

        ]);


        var options = {
          width: 450, height: 300,
          title: '<%=encprops.getProperty("newPreviousDistribution") %>',
          colors: ['#336600','#CC9900']
        };

        var chart = new google.visualization.PieChart(document.getElementById('firstSighting_div'));
        chart.draw(data, options);
      }
      
      
      
      
      <%
      if(numYearsCoverage>0){
      %>
      //num years analysis
      google.load("visualization", "1", {packages:["corechart"]});
      google.setOnLoadCallback(drawColumnChart);
      function drawColumnChart() {
        var data = new google.visualization.DataTable();
        data.addColumn('string', '<%=encprops.getProperty("calendarYearsBetweenResights") %>');
        data.addColumn('number', '<%=encprops.getProperty("numberMarkedIndividuals") %>');
        data.addRows([
        <%              
        for(int p=0;p<numYearsCoverage;p++){
        %>
          ['<%=p%>', <%=resightingYearsArray[p]%>]
		<%
		if(p<(numYearsCoverage-1)){
		%>
		,
		<%
		}
        }
		%>
        ]);

        var options = {
          width: 400, height: 240,
          title: '<%=encprops.getProperty("calendarYearsTitle") %>',
          hAxis: {title: '<%=encprops.getProperty("distributionYears") %>', titleTextStyle: {color: 'red'}}
        };

        var chart = new google.visualization.ColumnChart(document.getElementById('columnchart_div'));
        chart.draw(data, options);
      }
      <%
      }
      %>
      
      
</script>

    
<div class="container maincontent">

 <h1 class="intro"><%=encprops.getProperty("title")%></h1>

 <ul id="tabmenu">
 <%
String queryString = "";
if (request.getQueryString() != null) {
  queryString = request.getQueryString();
}
%>
 
  <li><a href="individualSearchResults.jsp?<%=queryString.replaceAll("startNum","uselessNum").replaceAll("endNum","uselessNum") %>"><%=encprops.getProperty("table")%>
  </a></li>
  <li><a href="individualThumbnailSearchResults.jsp?<%=queryString.replaceAll("startNum","uselessNum").replaceAll("endNum","uselessNum") %>"><%=encprops.getProperty("matchingImages")%>
  </a></li>
   <li><a href="individualMappedSearchResults.jsp?<%=queryString.replaceAll("startNum","uselessNum").replaceAll("endNum","uselessNum") %>"><%=encprops.getProperty("mappedResults")%>
  </a></li>
  <li><a class="active"><%=encprops.getProperty("analysis")%>
  </a></li>
    <li><a href="individualSearchResultsExport.jsp?<%=queryString.replaceAll("startNum","uselessNum").replaceAll("endNum","uselessNum") %>"><%=encprops.getProperty("export")%>
  </a></li>
 
 </ul>


<p><%=encprops.getProperty("numberMarkedIndividuals") %> <%=resultSize %>
<ul>
<li><%=encprops.getProperty("numberGenotype") %> <%=numResultsWithMsMarkers %>
<li><%=encprops.getProperty("numberHaplotype") %> <%=numResultsWithHaplotype %></li>
<li><%=encprops.getProperty("numberGeneticSex") %> <%=numResultsWithGeneticSex %></li>
</ul>
</p>
<%

if(maxTravelDistance>0){
%>
<p><%=encprops.getProperty("individualLargestDistance") %> <a href="individuals.jsp?number=<%=farthestTravelingIndividual %>"><%=farthestTravelingIndividual %></a> (<%=df.format(maxTravelDistance/1000) %> km)</p>
 <%
}
if(maxTimeBetweenResights>0){
	 //long maxTimeBetweenResights=0;
	 //String longestResightedIndividual="";
	 double bigTime=((double)maxTimeBetweenResights/1000/60/60/24/365);
%>
<p><%=encprops.getProperty("individualLongestTime") %> <a href="individuals.jsp?number=<%=longestResightedIndividual %>"><%=longestResightedIndividual %></a> (<%=df.format(bigTime) %> years)</p>
 <%
}
%>
<p><strong><%=encprops.getProperty("measurements") %></strong></p>
<%
 		//measurement
		
		if(measurementTypes.size()>0){
			for(int b=0;b<numMeasurementTypes;b++){
			%>
				<p><%=encprops.getProperty("mean") %> <%=measurementLabels.getProperty(measurementTypes.get(b).getType()+".label")%>: 
				<% 
				
				//now report averages
				if(measurementValues[b].getN()>0){
				%>
				&nbsp;<%=df.format(measurementValues[b].getMean()) %>&nbsp;<%=measurementLabels.getProperty(measurementTypes.get(b).getUnits()+".label") %> (<%=encprops.getProperty("standardDeviation") %> <%=df.format(measurementValues[b].getStandardDeviation()) %>) N=<%=measurementValues[b].getN() %><br />
				<ul>
					<li><%=encprops.getProperty("largest") %> <%=df.format(measurementValues[b].getMax()) %> <%=measurementLabels.getProperty(measurementTypes.get(b).getUnits()+".label") %> (<a href="individuals.jsp?number=<%=largestIndies[b] %>"><%=largestIndies[b] %></a>)</li>
					<li><%=encprops.getProperty("smallest") %> <%=df.format(measurementValues[b].getMin()) %> <%=measurementLabels.getProperty(measurementTypes.get(b).getUnits()+".label") %> (<a href="individuals.jsp?number=<%=smallestIndies[b] %>"><%=smallestIndies[b] %></a>)</li>
					<li><%=encprops.getProperty("meanMales") %> <%=df.format(measurementValuesMales[b].getMean()) %>&nbsp;<%=measurementLabels.getProperty(measurementTypes.get(b).getUnits()+".label") %> (<%=encprops.getProperty("standardDeviation") %> <%=df.format(measurementValuesMales[b].getStandardDeviation()) %>) N=<%=measurementValuesMales[b].getN() %></li>
					<li><%=encprops.getProperty("meanFemales") %> <%=df.format(measurementValuesFemales[b].getMean()) %>&nbsp;<%=measurementLabels.getProperty(measurementTypes.get(b).getUnits()+".label") %> (<%=encprops.getProperty("standardDeviation") %> <%=df.format(measurementValuesFemales[b].getStandardDeviation()) %>) N=<%=measurementValuesFemales[b].getN() %></li>
					<li><%=encprops.getProperty("meanNew") %> <%=df.format(measurementValuesNew[b].getMean()) %>&nbsp;<%=measurementLabels.getProperty(measurementTypes.get(b).getUnits()+".label") %> (<%=encprops.getProperty("standardDeviation") %> <%=df.format(measurementValuesNew[b].getStandardDeviation()) %>) N=<%=measurementValuesNew[b].getN() %></li>
					<li><%=encprops.getProperty("meanResight") %> <%=df.format(measurementValuesResights[b].getMean()) %>&nbsp;<%=measurementLabels.getProperty(measurementTypes.get(b).getUnits()+".label") %> (<%=encprops.getProperty("standardDeviation") %> <%=df.format(measurementValuesResights[b].getStandardDeviation()) %>) N=<%=measurementValuesResights[b].getN() %></li>	
				</ul>
				<%
				}
				else{
					%>
					&nbsp;<%=encprops.getProperty("noValues") %>
					<%
				}
				
				%>
				</p>
			<%
			}
		}
		else{
			%>
			<p><%=encprops.getProperty("noTypes") %></p>
			<% 
		}
%>

<p><strong><%=encprops.getProperty("bioMeasurements") %></strong></p>
<%
 		//measurement
		
		if(bioMeasurementTypes.size()>0){
			for(int b=0;b<numBioMeasurementTypes;b++){
			%>
				<p><%=encprops.getProperty("mean") %> <%=measurementLabels.getProperty(bioMeasurementTypes.get(b).getType()+".label")%>: 
				<% 
				
				//now report averages
				if(bioMeasurementValues[b].getN()>0){
				%>
				&nbsp;<%=df.format(bioMeasurementValues[b].getMean()) %>&nbsp;<%=measurementLabels.getProperty(bioMeasurementTypes.get(b).getUnits()+".label") %> (<%=encprops.getProperty("standardDeviation") %> <%=df.format(bioMeasurementValues[b].getStandardDeviation()) %>) N=<%=bioMeasurementValues[b].getN() %><br />
				<ul>
					<li><%=encprops.getProperty("largest") %> <%=df.format(bioMeasurementValues[b].getMax()) %> <%=measurementLabels.getProperty(bioMeasurementTypes.get(b).getUnits()+".label") %> (<a href="individuals.jsp?number=<%=bioLargestIndies[b] %>"><%=bioLargestIndies[b] %></a>)</li>
					<li><%=encprops.getProperty("smallest") %> <%=df.format(bioMeasurementValues[b].getMin()) %> <%=measurementLabels.getProperty(bioMeasurementTypes.get(b).getUnits()+".label") %> (<a href="individuals.jsp?number=<%=bioSmallestIndies[b] %>"><%=bioSmallestIndies[b] %></a>)</li>
					<li><%=encprops.getProperty("meanMales") %> <%=df.format(bioMeasurementValuesMales[b].getMean()) %>&nbsp;<%=measurementLabels.getProperty(bioMeasurementTypes.get(b).getUnits()+".label") %> (<%=encprops.getProperty("standardDeviation") %> <%=df.format(bioMeasurementValuesMales[b].getStandardDeviation()) %>) N=<%=bioMeasurementValuesMales[b].getN() %></li>
					<li><%=encprops.getProperty("meanFemales") %> <%=df.format(bioMeasurementValuesFemales[b].getMean()) %>&nbsp;<%=measurementLabels.getProperty(bioMeasurementTypes.get(b).getUnits()+".label") %> (<%=encprops.getProperty("standardDeviation") %> <%=df.format(bioMeasurementValuesFemales[b].getStandardDeviation()) %>) N=<%=bioMeasurementValuesFemales[b].getN() %></li>
					<li><%=encprops.getProperty("meanNew") %> <%=df.format(bioMeasurementValuesNew[b].getMean()) %>&nbsp;<%=measurementLabels.getProperty(bioMeasurementTypes.get(b).getUnits()+".label") %> (<%=encprops.getProperty("standardDeviation") %> <%=df.format(bioMeasurementValuesNew[b].getStandardDeviation()) %>) N=<%=bioMeasurementValuesNew[b].getN() %></li>
					<li><%=encprops.getProperty("meanResight") %> <%=df.format(bioMeasurementValuesResights[b].getMean()) %>&nbsp;<%=measurementLabels.getProperty(bioMeasurementTypes.get(b).getUnits()+".label") %> (<%=encprops.getProperty("standardDeviation") %> <%=df.format(bioMeasurementValuesResights[b].getStandardDeviation()) %>) N=<%=bioMeasurementValuesResights[b].getN() %></li>	
				</ul>
				<%
				}
				else{
					%>
					&nbsp;<%=encprops.getProperty("noValues") %>
					<%
				}
				
				%>
				</p>
			<%
			}
		}
		else{
			%>
			<p><%=encprops.getProperty("noTypes") %></p>
			<% 
		}

     try {
 %>
 
<p><strong><%=encprops.getProperty("charting") %></strong></p>

 <div id="chart_div"></div>

<div id="sexchart_div"></div>

<div id="firstSighting_div"></div>

<%
if(numYearsCoverage>0){
%>
 <div id="columnchart_div"></div>
 <%
}
     } 
     catch (Exception e) {
       e.printStackTrace();
     }
 



 
 
   myShepherd.rollbackDBTransaction();
   myShepherd.closeDBTransaction();
   rIndividuals = null;
 
%>
 <table>
  <tr>
    <td align="left">

      <p><strong><%=encprops.getProperty("queryDetails")%>
      </strong></p>

      <p class="caption"><strong><%=encprops.getProperty("prettyPrintResults") %>
      </strong><br/>
        <%=result.getQueryPrettyPrint().replaceAll("locationField", encprops.getProperty("location")).replaceAll("locationCodeField", encprops.getProperty("locationID")).replaceAll("verbatimEventDateField", encprops.getProperty("verbatimEventDate")).replaceAll("alternateIDField", encprops.getProperty("alternateID")).replaceAll("behaviorField", encprops.getProperty("behavior")).replaceAll("Sex", encprops.getProperty("sex")).replaceAll("nameField", encprops.getProperty("nameField")).replaceAll("selectLength", encprops.getProperty("selectLength")).replaceAll("numResights", encprops.getProperty("numResights")).replaceAll("vesselField", encprops.getProperty("vesselField"))%>
      </p>

      <p class="caption"><strong><%=encprops.getProperty("jdoql")%>
      </strong><br/>
        <%=result.getJDOQLRepresentation()%>
      </p>

    </td>
  </tr>
</table>
 
 </div>
 
 
 <jsp:include page="footer.jsp" flush="true"/>


