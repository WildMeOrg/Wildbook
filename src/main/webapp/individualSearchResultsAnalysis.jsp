<%@ page contentType="text/html; charset=utf-8" language="java" %>
<%@ page import="java.text.MessageFormat" %>
<%@ page import="java.text.NumberFormat" %>
<%@ page import="java.util.*" %>
<%@ page import="javax.jdo.*" %>
<%@ page import="org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics" %>
<%@ page import="org.ecocean.*" %>
<%@ page import="org.ecocean.Util.MeasurementDesc" %>
<%@ page import="org.ecocean.servlet.ServletUtilities" %>
<jsp:include page="header.jsp" flush="true"/>
<%
  String context = ServletUtilities.getContext(request);

  String langCode = ServletUtilities.getLanguageCode(request);
	Locale locale = new Locale(langCode);
  Properties props = ShepherdProperties.getProperties("individualSearchResultsAnalysis.properties", langCode, context);
  Properties propsAnalysisShared = ShepherdProperties.getProperties("searchResultsAnalysis_shared.properties", langCode, context);
  Properties propsShared = ShepherdProperties.getProperties("searchResults_shared.properties", langCode, context);
  Properties haploprops = ShepherdProperties.getProperties("haplotypeColorCodes.properties", "", context);

  Shepherd myShepherd = new Shepherd(context);
    
	NumberFormat df = NumberFormat.getInstance(locale);
	df.setMaximumFractionDigits(2);

  int numResults = 0;
  int numResultsWithTissueSamples=0;
  int numResultsWithGeneticSex=0;
  int numResultsWithMsMarkers=0;
  int numResultsWithHaplotype=0;
    
	//prep for measurements summary
	List<MeasurementDesc> measurementTypes=Util.findMeasurementDescs(langCode, context);
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
	List<MeasurementDesc> bioMeasurementTypes=Util.findBiologicalMeasurementDescs(langCode, context);
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
    ArrayList<String> allHaplos2=myShepherd.getAllHaplotypes(); 
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

					double val = thisEnc.getAverageMeasurementInPeriod(year1, month1, year2, month2, measurementTypes.get(b).getType()).doubleValue();
					measurementValues[b].addValue(val);
					
					//smallest vs largest analysis
					if(val<=measurementValues[b].getMin()){
						smallestIndies[b]=thisEnc.getIndividualID();
					}
					else if(val>=measurementValues[b].getMax()){
						largestIndies[b]=thisEnc.getIndividualID();
					}
					
					//males versus females analysis
					if((thisEnc.getSex()!=null)&&(thisEnc.getSex().equals("male"))){
						measurementValuesMales[b].addValue(val);
					}
					else if((thisEnc.getSex()!=null)&&(thisEnc.getSex().equals("female"))){
						measurementValuesFemales[b].addValue(val);
					}
					
					//first sights vs resights analysis
					 if(thisEnc.getEarliestSightingTime()<(new GregorianCalendar(year1,(month1-1),day1)).getTimeInMillis()){
						 measurementValuesResights[b].addValue(val);
							
				 		   
					 }
					 else{
						 measurementValuesNew[b].addValue(val);
							
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
    font: 0.5em "Arial, sans-serif;
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
      google.load("visualization", "1", {packages:["corechart"], language: '<%=langCode%>'});
      google.setOnLoadCallback(drawHaploChart);
      function drawHaploChart() {
        var data = new google.visualization.DataTable();
        data.addColumn('string', '<%=props.getProperty("chart.haplo.dataName.haplotype")%>');
        data.addColumn('number', '<%=props.getProperty("chart.haplo.dataName.number")%>');
        data.addRows([
          <%
          ArrayList<String> allHaplos=myShepherd.getAllHaplotypes(); 
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
          title: '<%=props.getProperty("chart.haplo.title")%>',
          colors: [
                   <%
                   String haploColor="CC0000";
                   if((props.getProperty("defaultMarkerColor")!=null)&&(!props.getProperty("defaultMarkerColor").trim().equals(""))){
                	   haploColor=props.getProperty("defaultMarkerColor");
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
        data.addColumn('string', '<%=props.getProperty("chart.sex.dataName.sex")%>');
        data.addColumn('number', '<%=props.getProperty("chart.sex.dataName.number")%>');
        data.addRows([

          ['<%=props.getProperty("chart.sex.male")%>',    <%=sexHashtable.get("male")%>],
           ['<%=props.getProperty("chart.sex.female")%>',    <%=sexHashtable.get("female")%>],
           ['<%=props.getProperty("chart.sex.unknown")%>',    <%=sexHashtable.get("unknown")%>]
          
        ]);

        <%
        haploColor="CC0000";
        if((props.getProperty("defaultMarkerColor")!=null)&&(!props.getProperty("defaultMarkerColor").trim().equals(""))){
     	   haploColor=props.getProperty("defaultMarkerColor");
        }
        
        %>
        var options = {
          width: 450, height: 300,
          title: '<%=props.getProperty("chart.sex.title")%>',
          colors: ['#0000FF','#FF00FF','<%=haploColor%>']
        };

        var chart = new google.visualization.PieChart(document.getElementById('sexchart_div'));
        chart.draw(data, options);
      }
      
      
      google.setOnLoadCallback(drawFirstSightingChart);
      function drawFirstSightingChart() {
        var data = new google.visualization.DataTable();
        data.addColumn('string', '<%=props.getProperty("chart.newPrevious.dataName.status")%>');
        data.addColumn('number', '<%=props.getProperty("chart.newPrevious.dataName.number")%>');
        data.addRows([

          ['<%=props.getProperty("chart.newPrevious.firstSighting")%>',    <%=firstSightingsHashtable.get("First sighting")%>],
           ['<%=props.getProperty("chart.newPrevious.previouslyIdentified")%>',    <%=firstSightingsHashtable.get("Previously sighted")%>]
           

        ]);


        var options = {
          width: 450, height: 300,
          title: '<%=props.getProperty("chart.newPrevious.title")%>',
          colors: ['#336600','#CC9900']
        };

        var chart = new google.visualization.PieChart(document.getElementById('firstSighting_div'));
        chart.draw(data, options);
      }
      
      
      
      
      <%
      if(numYearsCoverage>0){
      %>
      //num years analysis
      google.load("visualization", "1", {packages:["corechart"], language: '<%=langCode%>'});
      google.setOnLoadCallback(drawColumnChart);
      function drawColumnChart() {
        var data = new google.visualization.DataTable();
        data.addColumn('string', '<%=props.getProperty("chart.yearsBetween.dataName.years")%>');
        data.addColumn('number', '<%=props.getProperty("chart.yearsBetween.dataName.number")%>');
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
          title: '<%=props.getProperty("chart.yearsBetween.title")%>',
          hAxis: {title: '<%=props.getProperty("chart.yearsBetween.haxis.title")%>', titleTextStyle: {color: 'red'}}
        };

        var chart = new google.visualization.ColumnChart(document.getElementById('columnchart_div'));
        chart.draw(data, options);
      }
      <%
      }
      %>
      
      
</script>

    
<div class="container maincontent">

 <h1 class="intro"><%=props.getProperty("title")%></h1>

 <ul id="tabmenu">
 <%
String queryString = "";
if (request.getQueryString() != null) {
  queryString = request.getQueryString();
}
%>
 
  <li><a href="individualSearchResults.jsp?<%=queryString.replaceAll("startNum","uselessNum").replaceAll("endNum","uselessNum")%>"><%=propsShared.getProperty("table")%></a></li>
  <li><a href="individualThumbnailSearchResults.jsp?<%=queryString.replaceAll("startNum","uselessNum").replaceAll("endNum","uselessNum")%>"><%=propsShared.getProperty("matchingImages")%></a></li>
	<li><a href="individualMappedSearchResults.jsp?<%=queryString.replaceAll("startNum","uselessNum").replaceAll("endNum","uselessNum")%>"><%=propsShared.getProperty("mappedResults")%></a></li>
  <li><a class="active"><%=propsShared.getProperty("analysis")%></a></li>
	<li><a href="individualSearchResultsExport.jsp?<%=queryString.replaceAll("startNum","uselessNum").replaceAll("endNum","uselessNum")%>"><%=propsShared.getProperty("export")%></a></li>
 
 </ul>


<p><%=StringUtils.format(locale, propsAnalysisShared.getProperty("numberMatchingIndividuals"), resultSize)%>
<ul>
	<li><%=StringUtils.format(locale, props.getProperty("numberGenotype"), numResultsWithMsMarkers)%></li>
	<li><%=StringUtils.format(locale, props.getProperty("numberHaplotype"), numResultsWithHaplotype)%></li>
	<li><%=StringUtils.format(locale, props.getProperty("numberGeneticSex"), numResultsWithGeneticSex)%></li>
</ul>
</p>
<%

if(maxTravelDistance>0){
%>
<p><%=MessageFormat.format(props.getProperty("individualLargestDistance"), farthestTravelingIndividual, "individuals.jsp?number="+farthestTravelingIndividual, df.format(maxTravelDistance/1000))%></p>
 <%
}
if(maxTimeBetweenResights>0){
	 //long maxTimeBetweenResights=0;
	 //String longestResightedIndividual="";
	 double bigTime=((double)maxTimeBetweenResights/1000/60/60/24/365);
%>
	<p><%=MessageFormat.format(props.getProperty("individualLongestTime"), longestResightedIndividual, "individuals.jsp?number="+longestResightedIndividual, df.format(bigTime))%></p>
 <%
}
%>
<p><strong><%=propsAnalysisShared.getProperty("section.measurements")%></strong></p>
<%
 		// Measurements
		if (measurementTypes.size() > 0) {
			for (int b = 0; b < numMeasurementTypes; b++) {

				// Report averages
				if (measurementValues[b].getN() > 0) {
					String sd = MessageFormat.format(propsAnalysisShared.getProperty("standardDeviation"), df.format(measurementValues[b].getStandardDeviation()));
					%>
	<p><%=MessageFormat.format(propsAnalysisShared.getProperty("mean"), measurementTypes.get(b).getLabel(), df.format(measurementValues[b].getMean()), measurementTypes.get(b).getUnitsLabel(), sd, measurementValues[b].getN())%><br />
		<ul>
			<!-- Largest -->
			<li><%=MessageFormat.format(propsAnalysisShared.getProperty("largest"), df.format(measurementValues[b].getMax()), measurementTypes.get(b).getUnitsLabel())%> (<a href="individuals.jsp?number=<%=largestIndies[b]%>"><%=largestIndies[b]%></a>)</li>
			<!-- Smallest -->
			<li><%=MessageFormat.format(propsAnalysisShared.getProperty("smallest"), df.format(measurementValues[b].getMin()), measurementTypes.get(b).getUnitsLabel())%> (<a href="individuals.jsp?number=<%=smallestIndies[b]%>"><%=smallestIndies[b]%></a>)</li>
			<!-- Males -->
    	<%  if (measurementValuesMales[b].getN() == 0) { %>
			<li><%=propsAnalysisShared.getProperty("meanMales0")%></li>
	    <%  } else { %>
			<li><%=MessageFormat.format(propsAnalysisShared.getProperty("meanMales"), df.format(measurementValuesMales[b].getMean()), measurementTypes.get(b).getUnitsLabel(), MessageFormat.format(propsAnalysisShared.getProperty("standardDeviation"), df.format(measurementValuesMales[b].getStandardDeviation())), measurementValuesMales[b].getN())%></li>
	    <%  } %>
			<!-- Females -->
	    <%  if (measurementValuesFemales[b].getN() == 0) { %>
			<li><%=propsAnalysisShared.getProperty("meanFemales0")%></li>
	    <%  } else { %>
			<li><%=MessageFormat.format(propsAnalysisShared.getProperty("meanFemales"), df.format(measurementValuesFemales[b].getMean()), measurementTypes.get(b).getUnitsLabel(), MessageFormat.format(propsAnalysisShared.getProperty("standardDeviation"), df.format(measurementValuesFemales[b].getStandardDeviation())), measurementValuesFemales[b].getN())%></li>
	    <%  } %>
			<!-- New -->
	    <%  if (measurementValuesNew[b].getN() == 0) { %>
			<li><%=propsAnalysisShared.getProperty("meanNewIndividuals0")%></li>
	    <%  } else { %>
			<li><%=MessageFormat.format(propsAnalysisShared.getProperty("meanNewIndividuals"), df.format(measurementValuesNew[b].getMean()), measurementTypes.get(b).getUnitsLabel(), MessageFormat.format(propsAnalysisShared.getProperty("standardDeviation"), df.format(measurementValuesNew[b].getStandardDeviation())), measurementValuesNew[b].getN())%></li>
	    <%  } %>
			<!-- Resight -->
	    <%  if (measurementValuesResights[b].getN() == 0) { %>
			<li><%=propsAnalysisShared.getProperty("meanExistingIndividuals0")%></li>
	    <%  } else { %>
			<li><%=MessageFormat.format(propsAnalysisShared.getProperty("meanExistingIndividuals"), df.format(measurementValuesResights[b].getMean()), measurementTypes.get(b).getUnitsLabel(), MessageFormat.format(propsAnalysisShared.getProperty("standardDeviation"), df.format(measurementValuesResights[b].getStandardDeviation())), measurementValuesResights[b].getN())%></li>
	    <%  } %>
		</ul>
				<%
				}
				else {
					%>
	<p><%=MessageFormat.format(propsAnalysisShared.getProperty("mean0"), measurementTypes.get(b).getLabel(), propsAnalysisShared.getProperty("noMeasurementValues"))%>
					<%
				}
				%>
	</p>
				<%
			}
		}
		else {
			%>
			<p><%=propsAnalysisShared.getProperty("noMeasurementTypes")%></p>
			<% 
		}
%>

<p><strong><%=propsAnalysisShared.getProperty("section.bioChemMeasurements")%></strong></p>
<%
 		// Bio-measurements
		if (bioMeasurementTypes.size() > 0) {
			for (int b = 0; b < numMeasurementTypes; b++) {

				// Report averages
				if (bioMeasurementValues[b].getN() > 0) {
					String sd = MessageFormat.format(propsAnalysisShared.getProperty("standardDeviation"), df.format(bioMeasurementValues[b].getStandardDeviation()));
					%>
	<p><%=MessageFormat.format(propsAnalysisShared.getProperty("mean"), bioMeasurementTypes.get(b).getLabel(), df.format(bioMeasurementValues[b].getMean()), bioMeasurementTypes.get(b).getUnitsLabel(), sd, bioMeasurementValues[b].getN())%><br />
		<ul>
			<!-- Largest -->
			<li><%=MessageFormat.format(propsAnalysisShared.getProperty("largest"), df.format(bioMeasurementValues[b].getMax()), bioMeasurementTypes.get(b).getUnitsLabel())%> (<a href="individuals.jsp?number=<%=bioLargestIndies[b]%>"><%=bioLargestIndies[b]%></a>)</li>
			<!-- Smallest -->
			<li><%=MessageFormat.format(propsAnalysisShared.getProperty("smallest"), df.format(bioMeasurementValues[b].getMin()), bioMeasurementTypes.get(b).getUnitsLabel())%> (<a href="individuals.jsp?number=<%=bioSmallestIndies[b]%>"><%=bioSmallestIndies[b]%></a>)</li>
			<!-- Males -->
    	<%  if (bioMeasurementValuesMales[b].getN() == 0) { %>
			<li><%=propsAnalysisShared.getProperty("meanMales0")%></li>
	    <%  } else { %>
			<li><%=MessageFormat.format(propsAnalysisShared.getProperty("meanMales"), df.format(bioMeasurementValuesMales[b].getMean()), bioMeasurementTypes.get(b).getUnitsLabel(), MessageFormat.format(propsAnalysisShared.getProperty("standardDeviation"), df.format(bioMeasurementValuesMales[b].getStandardDeviation())), bioMeasurementValuesMales[b].getN())%></li>
	    <%  } %>
			<!-- Females -->
	    <%  if (bioMeasurementValuesFemales[b].getN() == 0) { %>
			<li><%=propsAnalysisShared.getProperty("meanFemales0")%></li>
	    <%  } else { %>
			<li><%=MessageFormat.format(propsAnalysisShared.getProperty("meanFemales"), df.format(bioMeasurementValuesFemales[b].getMean()), bioMeasurementTypes.get(b).getUnitsLabel(), MessageFormat.format(propsAnalysisShared.getProperty("standardDeviation"), df.format(bioMeasurementValuesFemales[b].getStandardDeviation())), bioMeasurementValuesFemales[b].getN())%></li>
	    <%  } %>
			<!-- New -->
	    <%  if (bioMeasurementValuesNew[b].getN() == 0) { %>
			<li><%=propsAnalysisShared.getProperty("meanNewIndividuals0")%></li>
	    <%  } else { %>
			<li><%=MessageFormat.format(propsAnalysisShared.getProperty("meanNewIndividuals"), df.format(bioMeasurementValuesNew[b].getMean()), bioMeasurementTypes.get(b).getUnitsLabel(), MessageFormat.format(propsAnalysisShared.getProperty("standardDeviation"), df.format(bioMeasurementValuesNew[b].getStandardDeviation())), bioMeasurementValuesNew[b].getN())%></li>
	    <%  } %>
			<!-- Resight -->
	    <%  if (bioMeasurementValuesResights[b].getN() == 0) { %>
			<li><%=propsAnalysisShared.getProperty("meanExistingIndividuals0")%></li>
	    <%  } else { %>
			<li><%=MessageFormat.format(propsAnalysisShared.getProperty("meanExistingIndividuals"), df.format(bioMeasurementValuesResights[b].getMean()), bioMeasurementTypes.get(b).getUnitsLabel(), MessageFormat.format(propsAnalysisShared.getProperty("standardDeviation"), df.format(bioMeasurementValuesResights[b].getStandardDeviation())), bioMeasurementValuesResights[b].getN())%></li>
	    <%  } %>
		</ul>
				<%
				}
				else {
					%>
	<p><%=MessageFormat.format(propsAnalysisShared.getProperty("mean0"), measurementTypes.get(b).getLabel(), propsAnalysisShared.getProperty("noMeasurementValues"))%>
					<%
				}
				%>
	</p>
				<%
			}
		}
		else {
			%>
			<p><%=propsAnalysisShared.getProperty("noMeasurementTypes")%></p>
			<%
		}

		try {
 %>
 
<p><strong><%=propsAnalysisShared.getProperty("section.charting")%></strong></p>

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
<%
	if (request.getParameter("noQuery") == null) {
%>
 <table>
  <tr>
    <td align="left">

      <p><strong><%=propsShared.getProperty("queryDetails")%>
      </strong></p>

      <p class="caption"><strong><%=propsShared.getProperty("prettyPrintResults")%>
      </strong><br/>
        <%=result.getQueryPrettyPrint().replaceAll("locationField", propsShared.getProperty("location")).replaceAll("locationCodeField", propsShared.getProperty("locationID")).replaceAll("verbatimEventDateField", propsShared.getProperty("verbatimEventDate")).replaceAll("alternateIDField", propsShared.getProperty("alternateID")).replaceAll("behaviorField", propsShared.getProperty("behavior")).replaceAll("Sex", propsShared.getProperty("sex")).replaceAll("nameField", propsShared.getProperty("nameField")).replaceAll("selectLength", propsShared.getProperty("selectLength")).replaceAll("numResights", propsShared.getProperty("numResights")).replaceAll("vesselField", propsShared.getProperty("vesselField"))%>
      </p>

      <p class="caption"><strong><%=propsShared.getProperty("jdoql")%>
      </strong><br/>
        <%=result.getJDOQLRepresentation()%>
      </p>

    </td>
  </tr>
</table>
<%
	}
%>
 </div>
 
 
 <jsp:include page="footer.jsp" flush="true"/>


