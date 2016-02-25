<%@ page contentType="text/html; charset=utf-8" language="java" %>
<%@ page import="java.text.MessageFormat" %>
<%@ page import="java.text.NumberFormat" %>
<%@ page import="java.util.*" %>
<%@ page import="org.apache.commons.math.stat.descriptive.SummaryStatistics" %>
<%@ page import="org.ecocean.*" %>
<%@ page import="org.ecocean.Util.MeasurementDesc" %>
<%@ page import="org.ecocean.genetics.*" %>
<%@ page import="org.ecocean.servlet.ServletUtilities" %>
<%@ page import="org.springframework.mock.web.MockHttpServletRequest" %>
<jsp:include page="../header.jsp" flush="true" />
<%
	String context = ServletUtilities.getContext(request);
	String langCode = ServletUtilities.getLanguageCode(request);
	Locale locale = new Locale(langCode);
	Properties props = ShepherdProperties.getProperties("searchComparisonResults.properties", langCode, context);
	Properties propsAnalysis = ShepherdProperties.getProperties("individualSearchResultsAnalysis.properties", langCode, context);
	Properties propsAnalysisShared = ShepherdProperties.getProperties("searchResultsAnalysis_shared.properties", langCode, context);
	Properties propsShared = ShepherdProperties.getProperties("searchResults_shared.properties", langCode, context);
	Properties haploprops = ShepherdProperties.getProperties("haplotypeColorCodes.properties", "", context);
    
    //get our Shepherd
    Shepherd myShepherd = new Shepherd(context);

    int numResults = 0;

    //set up the vector for matching encounters
    Vector query1Individuals = new Vector();
    Vector query2Individuals = new Vector();

    //kick off the transaction
    myShepherd.beginDBTransaction();

    //start the query and get the results
    String order = "";
    //EncounterQueryResult queryResult1 = EncounterQueryProcessor.processQuery(myShepherd, request, order);
    HttpServletRequest request1=(MockHttpServletRequest)session.getAttribute("locationSearch1");
    
    if(request1!=null){
    
    MarkedIndividualQueryResult queryResult1 = IndividualQueryProcessor.processQuery(myShepherd, request1, order);
    //System.out.println(((MockHttpServletRequest)session.getAttribute("locationSearch1")).getQueryString());
    query1Individuals = queryResult1.getResult();
    MarkedIndividualQueryResult queryResult2 = IndividualQueryProcessor.processQuery(myShepherd, request, order);
    query2Individuals = queryResult2.getResult();
    

    //general stats summary setup
	List<MeasurementDesc> measurementTypes=Util.findMeasurementDescs(langCode, context);
	int numMeasurementTypes=measurementTypes.size();
	List<MeasurementDesc> bioMeasurementTypes=Util.findBiologicalMeasurementDescs(langCode, context);
	int numBioMeasurementTypes=bioMeasurementTypes.size();
	
	
    //Search 1 stats summary
	//prep for measurements summary
	SummaryStatistics[] measurementValues1=new SummaryStatistics[numMeasurementTypes];
	SummaryStatistics[] measurementValuesMales1=new SummaryStatistics[numMeasurementTypes];
	SummaryStatistics[] measurementValuesFemales1=new SummaryStatistics[numMeasurementTypes];
	SummaryStatistics[] measurementValuesNew1=new SummaryStatistics[numMeasurementTypes];
	SummaryStatistics[] measurementValuesResights1=new SummaryStatistics[numMeasurementTypes];
	String[] smallestIndies1=new String[numMeasurementTypes];
	String[] largestIndies1=new String[numMeasurementTypes];
	for(int b=0;b<measurementValues1.length;b++){
		measurementValues1[b]=new SummaryStatistics();
		measurementValuesMales1[b]=new SummaryStatistics();
		measurementValuesFemales1[b]=new SummaryStatistics();
		measurementValuesNew1[b]=new SummaryStatistics();
		measurementValuesResights1[b]=new SummaryStatistics();
		smallestIndies1[b]="";
		largestIndies1[b]="";
	}
	//prep for biomeasurements summary
	SummaryStatistics[] bioMeasurementValues1=new SummaryStatistics[numBioMeasurementTypes];
	SummaryStatistics[] bioMeasurementValuesMales1=new SummaryStatistics[numBioMeasurementTypes];
	SummaryStatistics[] bioMeasurementValuesFemales1=new SummaryStatistics[numBioMeasurementTypes];
	SummaryStatistics[] bioMeasurementValuesNew1=new SummaryStatistics[numBioMeasurementTypes];
	SummaryStatistics[] bioMeasurementValuesResights1=new SummaryStatistics[numBioMeasurementTypes];
	String[] bioSmallestIndies1=new String[numBioMeasurementTypes];
	String[] bioLargestIndies1=new String[numBioMeasurementTypes];
	for(int b=0;b<bioMeasurementValues1.length;b++){
		bioMeasurementValues1[b]=new SummaryStatistics();
		bioMeasurementValuesMales1[b]=new SummaryStatistics();
		bioMeasurementValuesFemales1[b]=new SummaryStatistics();
		bioMeasurementValuesNew1[b]=new SummaryStatistics();
		bioMeasurementValuesResights1[b]=new SummaryStatistics();
		bioSmallestIndies1[b]="";
		bioLargestIndies1[b]="";
	}
	//end search 1 stats summary
	
    
    //Search 2 stats summary
	//prep for measurements summary
	SummaryStatistics[] measurementValues2=new SummaryStatistics[numMeasurementTypes];
	SummaryStatistics[] measurementValuesMales2=new SummaryStatistics[numMeasurementTypes];
	SummaryStatistics[] measurementValuesFemales2=new SummaryStatistics[numMeasurementTypes];
	SummaryStatistics[] measurementValuesNew2=new SummaryStatistics[numMeasurementTypes];
	SummaryStatistics[] measurementValuesResights2=new SummaryStatistics[numMeasurementTypes];
	String[] smallestIndies2=new String[numMeasurementTypes];
	String[] largestIndies2=new String[numMeasurementTypes];
	for(int b=0;b<measurementValues2.length;b++){
		measurementValues2[b]=new SummaryStatistics();
		measurementValuesMales2[b]=new SummaryStatistics();
		measurementValuesFemales2[b]=new SummaryStatistics();
		measurementValuesNew2[b]=new SummaryStatistics();
		measurementValuesResights2[b]=new SummaryStatistics();
		smallestIndies2[b]="";
		largestIndies2[b]="";
	}
	//prep for biomeasurements summary
	SummaryStatistics[] bioMeasurementValues2=new SummaryStatistics[numBioMeasurementTypes];
	SummaryStatistics[] bioMeasurementValuesMales2=new SummaryStatistics[numBioMeasurementTypes];
	SummaryStatistics[] bioMeasurementValuesFemales2=new SummaryStatistics[numBioMeasurementTypes];
	SummaryStatistics[] bioMeasurementValuesNew2=new SummaryStatistics[numBioMeasurementTypes];
	SummaryStatistics[] bioMeasurementValuesResights2=new SummaryStatistics[numBioMeasurementTypes];
	String[] bioSmallestIndies2=new String[numBioMeasurementTypes];
	String[] bioLargestIndies2=new String[numBioMeasurementTypes];
	for(int b=0;b<bioMeasurementValues2.length;b++){
		bioMeasurementValues2[b]=new SummaryStatistics();
		bioMeasurementValuesMales2[b]=new SummaryStatistics();
		bioMeasurementValuesFemales2[b]=new SummaryStatistics();
		bioMeasurementValuesNew2[b]=new SummaryStatistics();
		bioMeasurementValuesResights2[b]=new SummaryStatistics();
		bioSmallestIndies2[b]="";
		bioLargestIndies2[b]="";
	}
	//end search 2 stats summary
	
		NumberFormat df = NumberFormat.getInstance(locale);
		df.setMaximumFractionDigits(2);

    List matchedIndividuals = new ArrayList();
    int query1Size=query1Individuals.size();
    for(int y=0;y<query1Size;y++){
    	matchedIndividuals.add(query1Individuals.get(y));
    }
    
   //for(int y=0;y<matchedIndividuals.size();y++){
   // 	if(!query2Results.contains(matchedIndividuals.get(y))){matchedIndividuals.remove(y);y--;}
   //}
    
    matchedIndividuals.retainAll(query2Individuals);
    int numMatchedIndividuals=matchedIndividuals.size();
    
    //let's prep the HashTable for the haplo pie chart
    ArrayList<String> allHaplos2=myShepherd.getAllHaplotypes(); 
    int numHaplos2 = allHaplos2.size();
    Hashtable<String,Integer> pieHashtable1 = new Hashtable<String,Integer>();
    Hashtable<String,Integer> pieHashtable2 = new Hashtable<String,Integer>();
 	for(int gg=0;gg<numHaplos2;gg++){
 		String thisHaplo=allHaplos2.get(gg);
 		pieHashtable1.put(thisHaplo, new Integer(0));
 		pieHashtable2.put(thisHaplo, new Integer(0));
 	}
    
 	//let's prep the HashTables for the sex pie charts
 	Hashtable<String,Integer> sexHashtable1 = new Hashtable<String,Integer>();
 	sexHashtable1.put("male", new Integer(0));
 	sexHashtable1.put("female", new Integer(0));
 	sexHashtable1.put("unknown", new Integer(0));
 	
 	Hashtable<String,Integer> sexHashtable2 = new Hashtable<String,Integer>();
 	sexHashtable2.put("male", new Integer(0));
 	sexHashtable2.put("female", new Integer(0));
 	sexHashtable2.put("unknown", new Integer(0));
 	
 	
 	int resultSize1=query1Individuals.size();
 	int resultSize2=query2Individuals.size();
 	
	//more results1 analysis 	
 	 ArrayList<String> markedIndividuals1=new ArrayList<String>();
 	int year1=(new Integer(request1.getParameter("year1"))).intValue();
 	int year2=(new Integer(request1.getParameter("year2"))).intValue();
 	int month1=(new Integer(request1.getParameter("month1"))).intValue();
 	int month2=(new Integer(request1.getParameter("month2"))).intValue();
 	
 	
 	 for(int y=0;y<resultSize1;y++){
 		 MarkedIndividual thisEnc=(MarkedIndividual)query1Individuals.get(y);
 		 if((!markedIndividuals1.contains(thisEnc.getIndividualID()))){markedIndividuals1.add(thisEnc.getIndividualID());}
 		 //haplotype ie chart prep
 		 if(thisEnc.getHaplotype()!=null){
      	   if(pieHashtable1.containsKey(thisEnc.getHaplotype().trim())){
      		   Integer thisInt = pieHashtable1.get(thisEnc.getHaplotype().trim())+1;
      		   pieHashtable1.put(thisEnc.getHaplotype().trim(), thisInt);
      	   }
 	 	}
 		 
 	    //sex pie chart 	 
 	    if(thisEnc.getSex()!=null){
 			if(thisEnc.getSex().equals("male")){
 		   		Integer thisInt = sexHashtable1.get("male")+1;
  		   		sexHashtable1.put("male", thisInt);
 			}
 			else if(thisEnc.getSex().equals("female")){
  		   		Integer thisInt = sexHashtable1.get("female")+1;
  		   		sexHashtable1.put("female", thisInt);
 			}
 			else{
 	    		Integer thisInt = sexHashtable1.get("unknown")+1;
   		    	sexHashtable1.put("unknown", thisInt);
 	    	}
 	    }
 	    else{
 	    	Integer thisInt = sexHashtable1.get("unknown")+1;
   		    sexHashtable1.put("unknown", thisInt);
 	    }
 		 
 		//measurement
		for(int b=0;b<numMeasurementTypes;b++){
			if(thisEnc.getAverageMeasurementInPeriod(year1, month1, year2, month2, measurementTypes.get(b).getType())!=null){
				
					double val = thisEnc.getAverageMeasurementInPeriod(year1, month1, year2, month2, measurementTypes.get(b).getType()).doubleValue();
					measurementValues1[b].addValue(val);
					
					//smallest vs largest analysis
					if(val<=measurementValues1[b].getMin()){
						smallestIndies1[b]=thisEnc.getIndividualID();
					}
					else if(val>=measurementValues1[b].getMax()){
						largestIndies1[b]=thisEnc.getIndividualID();
					}
					
					//males versus females analysis
					if((thisEnc.getSex()!=null)&&(thisEnc.getSex().equals("male"))){
						measurementValuesMales1[b].addValue(val);
					}
					else if((thisEnc.getSex()!=null)&&(thisEnc.getSex().equals("female"))){
						measurementValuesFemales1[b].addValue(val);
					}
					
					//first sights vs resights analysis
					 if(thisEnc.getEarliestSightingTime()<(new GregorianCalendar(Integer.parseInt(request1.getParameter("year1")),Integer.parseInt(request1.getParameter("month1")),Integer.parseInt(request1.getParameter("day1")))).getTimeInMillis()){
						 measurementValuesResights1[b].addValue(val);
							
				 		   
					 }
					 else{
						 measurementValuesNew1[b].addValue(val);
							
					 }
					
					
					
			}
		}
		
 		//biomeasurement tabulation
		for(int b=0;b<numBioMeasurementTypes;b++){
			if(thisEnc.getAverageBiologicalMeasurementInPeriod(year1, month1, year2, month2, bioMeasurementTypes.get(b).getType())!=null){
				
					double val = thisEnc.getAverageBiologicalMeasurementInPeriod(year1, month1, year2, month2, bioMeasurementTypes.get(b).getType()).doubleValue();
					bioMeasurementValues1[b].addValue(val);
					
					//smallest vs largest analysis
					if(val<=bioMeasurementValues1[b].getMin()){
						bioSmallestIndies1[b]=thisEnc.getIndividualID();
					}
					else if(val>=bioMeasurementValues1[b].getMax()){
						bioLargestIndies1[b]=thisEnc.getIndividualID();
					}
					
					//males versus females analysis
					if((thisEnc.getSex()!=null)&&(thisEnc.getSex().equals("male"))){
						bioMeasurementValuesMales1[b].addValue(val);
					}
					else if((thisEnc.getSex()!=null)&&(thisEnc.getSex().equals("female"))){
						bioMeasurementValuesFemales1[b].addValue(val);
					}
					
					//first sights vs resights analysis
					 if(thisEnc.getEarliestSightingTime()<(new GregorianCalendar(Integer.parseInt(request1.getParameter("year1")),Integer.parseInt(request1.getParameter("month1")),Integer.parseInt(request1.getParameter("day1")))).getTimeInMillis()){
						 bioMeasurementValuesResights1[b].addValue(val);
							
				 		   
					 }
					 else{
						 bioMeasurementValuesNew1[b].addValue(val);
							
					 }
					
					
					
			}
		}
		
 	    
 	    
 	 }	
 	 //end results analysis 1
 	 
 	 //more results2 analysis 	
 	  year1=(new Integer(request.getParameter("year1"))).intValue();
 	  year2=(new Integer(request.getParameter("year2"))).intValue();
 	  month1=(new Integer(request.getParameter("month1"))).intValue();
 	 month2=(new Integer(request.getParameter("month2"))).intValue();
 	 ArrayList<String> markedIndividuals2=new ArrayList<String>();
 	 for(int y=0;y<resultSize2;y++){
 		 MarkedIndividual thisEnc=(MarkedIndividual)query2Individuals.get(y);
 		 if((!markedIndividuals2.contains(thisEnc.getIndividualID()))){markedIndividuals2.add(thisEnc.getIndividualID());}
 		 //haplotype ie chart prep
 		 if(thisEnc.getHaplotype()!=null){
      	   if(pieHashtable2.containsKey(thisEnc.getHaplotype().trim())){
      		   Integer thisInt = pieHashtable2.get(thisEnc.getHaplotype().trim())+1;
      		   pieHashtable2.put(thisEnc.getHaplotype().trim(), thisInt);
      	   }
 	 	}
 		 
 	    //sex pie chart 
 	    if(thisEnc.getSex()!=null){
 			if(thisEnc.getSex().equals("male")){
 		   		Integer thisInt = sexHashtable2.get("male")+1;
  		   		sexHashtable2.put("male", thisInt);
 			}
 			else if(thisEnc.getSex().equals("female")){
  		   		Integer thisInt = sexHashtable2.get("female")+1;
  		   		sexHashtable2.put("female", thisInt);
 			}
 			else{
 	    		Integer thisInt = sexHashtable2.get("unknown")+1;
   		    	sexHashtable2.put("unknown", thisInt);
 	    	}
 	    }
 	    else{
 	    	Integer thisInt = sexHashtable2.get("unknown")+1;
   		    sexHashtable2.put("unknown", thisInt);
 	    }
 	    
 	    
 		//measurement
		for(int b=0;b<numMeasurementTypes;b++){
			if(thisEnc.getAverageMeasurementInPeriod(year1, month1, year2, month2, measurementTypes.get(b).getType())!=null){
				
					double val = thisEnc.getAverageMeasurementInPeriod(year1, month1, year2, month2, measurementTypes.get(b).getType()).doubleValue();
					measurementValues2[b].addValue(val);
					
					//smallest vs largest analysis
					if(val<=measurementValues2[b].getMin()){
						smallestIndies2[b]=thisEnc.getIndividualID();
					}
					else if(val>=measurementValues2[b].getMax()){
						largestIndies2[b]=thisEnc.getIndividualID();
					}
					
					//males versus females analysis
					if((thisEnc.getSex()!=null)&&(thisEnc.getSex().equals("male"))){
						measurementValuesMales2[b].addValue(val);
					}
					else if((thisEnc.getSex()!=null)&&(thisEnc.getSex().equals("female"))){
						measurementValuesFemales2[b].addValue(val);
					}
					
					//first sights vs resights analysis
					 if(thisEnc.getEarliestSightingTime()<(new GregorianCalendar(Integer.parseInt(request.getParameter("year1")),Integer.parseInt(request.getParameter("month1")),Integer.parseInt(request.getParameter("day1")))).getTimeInMillis()){
						 measurementValuesResights2[b].addValue(val);
							
				 		   
					 }
					 else{
						 measurementValuesNew2[b].addValue(val);
							
					 }
					
					
					
			}
		}
		
 		//biomeasurement tabulation
		for(int b=0;b<numBioMeasurementTypes;b++){
			if(thisEnc.getAverageBiologicalMeasurementInPeriod(year1, month1, year2, month2, bioMeasurementTypes.get(b).getType())!=null){
				
					double val = thisEnc.getAverageBiologicalMeasurementInPeriod(year1, month1, year2, month2, bioMeasurementTypes.get(b).getType()).doubleValue();
					bioMeasurementValues2[b].addValue(val);
					
					//smallest vs largest analysis
					if(val<=bioMeasurementValues2[b].getMin()){
						bioSmallestIndies2[b]=thisEnc.getIndividualID();
					}
					else if(val>=bioMeasurementValues2[b].getMax()){
						bioLargestIndies2[b]=thisEnc.getIndividualID();
					}
					
					//males versus females analysis
					if((thisEnc.getSex()!=null)&&(thisEnc.getSex().equals("male"))){
						bioMeasurementValuesMales2[b].addValue(val);
					}
					else if((thisEnc.getSex()!=null)&&(thisEnc.getSex().equals("female"))){
						bioMeasurementValuesFemales2[b].addValue(val);
					}
					
					//first sights vs resights analysis
					 if(thisEnc.getEarliestSightingTime()<(new GregorianCalendar(Integer.parseInt(request.getParameter("year1")),Integer.parseInt(request.getParameter("month1")),Integer.parseInt(request.getParameter("day1")))).getTimeInMillis()){
						 bioMeasurementValuesResights2[b].addValue(val);
							
				 		   
					 }
					 else{
						 bioMeasurementValuesNew2[b].addValue(val);
							
					 }
					
					
					
			}
		}
 	    
 		 
 	 }	
 	 //end results analysis 2
 	 
 
 	 
  %>

    <style type="text/css">

      #map {
        width: 600px;
        height: 400px;
      }

			table.comparison {
				width: 100%;
			}

			table.comparison tr td{
      	vertical-align: top;
      }
      
      table.comparison tr{
      	vertical-align: top;
      }

    </style>
  


  


<script src="http://maps.google.com/maps/api/js?sensor=false&language=<%=langCode%>"></script>
    
<script type="text/javascript" src="https://www.google.com/jsapi"></script>

<script type="text/javascript">

//let's build some maps

var center = new google.maps.LatLng(0, 0);
var map1;
var map2;

var selectedRectangle1;
var selectedRectangle2;

  function initialize() {
	//alert("initializing map!");
	//overlaysSet=false;
	var mapZoom = 1;



	  



		
		<%
		
        //set the previous maps search box if set
        if((request1.getParameter("ne_lat")!=null) && (request1.getParameter("ne_long")!=null) && (request1.getParameter("sw_lat")!=null) && (request1.getParameter("ne_long")!=null)&&(!request1.getParameter("ne_lat").trim().equals("")) &&(!request1.getParameter("ne_long").trim().equals("")) && (!request1.getParameter("sw_lat").trim().equals("")) && (!request1.getParameter("sw_long").trim().equals(""))){
        %>    
        
    	  map1 = new google.maps.Map(document.getElementById('map_canvas1'), {
    		  zoom: mapZoom,
    		  center: center,
    		  mapTypeId: google.maps.MapTypeId.HYBRID
    		});
      	  
      	  
        	//create the selection response rectangle
      	  selectedRectangle1 = new google.maps.Rectangle({
      	  	map: map1,
      	  	visible: true,
      	      strokeColor: "#0000FF",
      	      fillColor: "#0000FF"
      	  });
        	
            	//create the coordinates
            	var neCoord=new google.maps.LatLng(<%=request1.getParameter("ne_lat")%>,<%=request1.getParameter("ne_long")%>);
            	var swCoord=new google.maps.LatLng(<%=request1.getParameter("sw_lat")%>,<%=request1.getParameter("sw_long")%>);
            	var search1Bounds = new google.maps.LatLngBounds(
            		swCoord,
            		neCoord
            	);

            	//create the rectangle
            	var search1Rectangle = new google.maps.Rectangle({
            		bounds:search1Bounds,
            		map: map1,
            	    strokeColor: "#ff0000",
            	    fillColor: "#ff0000"
            	});
            	map1.fitBounds(search1Bounds);
            	
            <%
        	}
			if((request.getParameter("ne_lat")!=null) && (request.getParameter("ne_long")!=null) && (request.getParameter("sw_lat")!=null) && (request.getParameter("ne_long")!=null)&&(!request.getParameter("ne_lat").trim().equals("")) &&(!request.getParameter("ne_long").trim().equals("")) && (!request.getParameter("sw_lat").trim().equals("")) && (!request.getParameter("sw_long").trim().equals(""))){
		       
            %>
      	  map2 = new google.maps.Map(document.getElementById('map_canvas2'), {
    		  zoom: mapZoom,
    		  center: center,
    		  mapTypeId: google.maps.MapTypeId.HYBRID
    		});



    	
    		//create the selection response rectangle
    	  selectedRectangle2 = new google.maps.Rectangle({
    	  	map: map2,
    	  	visible: true,
    	      strokeColor: "#0000FF",
    	      fillColor: "#0000FF"
    	  });
    		
        	//create the coordinates
        	var neCoord2=new google.maps.LatLng(<%=request.getParameter("ne_lat")%>,<%=request.getParameter("ne_long")%>);
        	var swCoord2=new google.maps.LatLng(<%=request.getParameter("sw_lat")%>,<%=request.getParameter("sw_long")%>);
        	var search2Bounds = new google.maps.LatLngBounds(
        		swCoord2,
        		neCoord2
        	);

        	//create the rectangle 2
        	var search2Rectangle = new google.maps.Rectangle({
        		bounds:search2Bounds,
        		map: map2,
        	    strokeColor: "#ff0000",
        	    fillColor: "#ff0000"
        	});
	        map2.fitBounds(search2Bounds);
        <%    
        }		
		%>

  }   //end initialize function          
  
  google.maps.event.addDomListener(window, 'load', initialize);
  

</script>
<script type="text/javascript">
      google.load("visualization", "1", {packages:["corechart"]});
      google.setOnLoadCallback(drawHaploChart1);
      function drawHaploChart1() {
        var data = new google.visualization.DataTable();
        data.addColumn('string', '<%=propsAnalysis.getProperty("chart.haplo.dataName.haplotype")%>');
        data.addColumn('number', '<%=propsAnalysis.getProperty("chart.haplo.dataName.number")%>');
        data.addRows([
          <%
          ArrayList<String> allHaplos1=myShepherd.getAllHaplotypes(); 
          int numHaplos1 = allHaplos1.size();
          

          
          for(int hh=0;hh<numHaplos1;hh++){
          %>
          ['<%=allHaplos1.get(hh)%>',    <%=pieHashtable1.get(allHaplos1.get(hh))%>]
		  <%
		  if(hh<(numHaplos1-1)){
		  %>
		  ,
		  <%
		  }
		  
          }
		  %>
          
        ]);

        var options = {
          width: 450, height: 300,
          title: '<%=propsAnalysis.getProperty("chart.haplo.title")%>',
          colors: [
                   <%
                   String haploColor="CC0000";
                   if((propsShared.getProperty("defaultMarkerColor")!=null)&&(!propsShared.getProperty("defaultMarkerColor").trim().equals(""))){
                	   haploColor=propsShared.getProperty("defaultMarkerColor");
                   }   

                   
                   for(int yy=0;yy<numHaplos1;yy++){
                       String haplo=allHaplos1.get(yy);
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

        var chart1 = new google.visualization.PieChart(document.getElementById('chart_div1'));
        chart1.draw(data, options);

      }
      
      google.setOnLoadCallback(drawSexChart);
      function drawSexChart() {
        var data = new google.visualization.DataTable();
        data.addColumn('string', '<%=propsAnalysis.getProperty("chart.sex.dataName.sex")%>');
        data.addColumn('number', '<%=propsAnalysis.getProperty("chart.sex.dataName.number")%>');
        data.addRows([

          ['<%=propsAnalysis.getProperty("chart.sex.male")%>',    <%=sexHashtable1.get("male")%>],
           ['<%=propsAnalysis.getProperty("chart.sex.female")%>',    <%=sexHashtable1.get("female")%>],
           ['<%=propsAnalysis.getProperty("chart.sex.unknown")%>',    <%=sexHashtable1.get("unknown")%>]
          
        ]);

        <%
        haploColor="CC0000";
        if((propsShared.getProperty("defaultMarkerColor")!=null)&&(!propsShared.getProperty("defaultMarkerColor").trim().equals(""))){
     	   haploColor=propsShared.getProperty("defaultMarkerColor");
        }
        
        %>
        var options = {
          width: 450, height: 300,
          title: '<%=propsAnalysis.getProperty("chart.sex.title")%>',
          colors: ['#0000FF','#FF00FF','<%=haploColor%>']
        };

        var chart1 = new google.visualization.PieChart(document.getElementById('sexchart_div1'));
        chart1.draw(data, options);
      }
      
      
</script>

<script type="text/javascript">
      google.load("visualization", "1", {packages:["corechart"]});
      google.setOnLoadCallback(drawHaploChart2);
      function drawHaploChart2() {
        var data = new google.visualization.DataTable();
        data.addColumn('string', '<%=propsAnalysis.getProperty("chart.haplo.dataName.haplotype")%>');
        data.addColumn('number', '<%=propsAnalysis.getProperty("chart.haplo.dataName.number")%>');
        data.addRows([
          <%
          ArrayList<String> allHaplos2a=myShepherd.getAllHaplotypes(); 
          int numHaplos2a = allHaplos2a.size();
          

          
          for(int hh=0;hh<numHaplos2a;hh++){
          %>
          ['<%=allHaplos2a.get(hh)%>',    <%=pieHashtable2.get(allHaplos2a.get(hh))%>]
		  <%
		  //test comment
		  if(hh<(numHaplos2a-1)){
		  %>
		  ,
		  <%
		  }
          }
		  %>
          
        ]);

        var options = {
          width: 450, height: 300,
          title: '<%=propsAnalysis.getProperty("chart.haplo.title")%>',
          colors: [
                   <%
                   haploColor="CC0000";
                   if((propsShared.getProperty("defaultMarkerColor")!=null)&&(!propsShared.getProperty("defaultMarkerColor").trim().equals(""))){
                	   haploColor=propsShared.getProperty("defaultMarkerColor");
                   }   

                   
                   for(int yy=0;yy<numHaplos2a;yy++){
                       String haplo=allHaplos2a.get(yy);
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

        var chart2 = new google.visualization.PieChart(document.getElementById('chart_div2'));
        chart2.draw(data, options);
      }
      
      google.setOnLoadCallback(drawSexChart);
      function drawSexChart() {
        var data = new google.visualization.DataTable();
        data.addColumn('string', '<%=propsAnalysis.getProperty("chart.sex.dataName.sex")%>');
        data.addColumn('number', '<%=propsAnalysis.getProperty("chart.sex.dataName.number")%>');
        data.addRows([

          ['<%=propsAnalysis.getProperty("chart.sex.male")%>',    <%=sexHashtable2.get("male")%>],
           ['<%=propsAnalysis.getProperty("chart.sex.female")%>',    <%=sexHashtable2.get("female")%>],
           ['<%=propsAnalysis.getProperty("chart.sex.unknown")%>',    <%=sexHashtable2.get("unknown")%>]
          
        ]);

        <%
        haploColor="CC0000";
        if((propsAnalysisShared.getProperty("defaultMarkerColor")!=null)&&(!propsAnalysisShared.getProperty("defaultMarkerColor").trim().equals(""))){
     	   haploColor=propsAnalysisShared.getProperty("defaultMarkerColor");
        }
        
        %>
        var options = {
          width: 450, height: 300,
          title: '<%=propsAnalysis.getProperty("chart.sex.title")%>',
          colors: ['#0000FF','#FF00FF','<%=haploColor%>']
        };

        var chart2 = new google.visualization.PieChart(document.getElementById('sexchart_div2'));
        chart2.draw(data, options);
      }
      
      
</script>




     <div class="container maincontent">
 
<h1><%=props.getProperty("title")%></h1>
      
<table width="810px">
	<tr>
		<td bgcolor="#EEEEFF">
			<p><strong><%=props.getProperty("comparisonOverview")%></strong></p>
			<p><%=MessageFormat.format(props.getProperty("sharedMarkedIndividuals"), numMatchedIndividuals)%></p>
			
			 <%
			 
				//next we need to calculate the combined population size, since there are overlapping individuals
				//first, let's get the combined ist of marked individuals
				ArrayList<MarkedIndividual> totalPopulation = new ArrayList<MarkedIndividual>();
 			for(int y=0;y<query1Size;y++){
 				totalPopulation.add(((MarkedIndividual)query1Individuals.get(y)));
 			}
 			int query2Size=query2Individuals.size();
 			for(int y=0;y<query2Size;y++){
 				if(!totalPopulation.contains(((MarkedIndividual)query2Individuals.get(y)))){totalPopulation.add(((MarkedIndividual)query2Individuals.get(y)));}
 			}
 			int totalPopulationSize=totalPopulation.size();
 			
			 if(((request.getParameter("hasHaplotype")!=null) || ((request.getParameter("haplotypeField")!=null)&&(request1.getParameter("haplotypeField")!=null))) &&(query1Individuals.size()>0)&&(query2Individuals.size()>0)){
				 //now we need to calculate some inbreeding statistics using haplotypes
 			
 				//first get all haplotypes
 				ArrayList<String> allHaplos=myShepherd.getAllHaplotypes();
			 	int numHaplosHere=allHaplos.size();
 			
				/**  HFStatistics approach! **/			 	
				HashMap<String,Integer> haploMap=new HashMap<String,Integer>(numHaplosHere);
				int mapNum=0;
				for(int y=0;y<numHaplosHere;y++){
					//if(!allHaplos.get(y).equals("HET")){
						haploMap.put(allHaplos.get(y), new Integer(mapNum));
					//}
					mapNum++;
				}
				HFStatistics fstats=new HFStatistics(2);
				for(int k=0;k<query1Size;k++){
					MarkedIndividual indie=(MarkedIndividual)query1Individuals.get(k);
					int myHaploIntRep=haploMap.get(indie.getHaplotype()).intValue()+1;
					fstats.loadIndividual(myHaploIntRep, 1);
				}
				
				for(int k=0;k<query2Size;k++){
					MarkedIndividual indie=(MarkedIndividual)query2Individuals.get(k);
					int myHaploIntRep=haploMap.get(indie.getHaplotype()).intValue()+1;
					fstats.loadIndividual(myHaploIntRep, 2);
				}
				/** End HFStatistics approach **/
				
  	
	
        		
			%>
			<p><strong><%=props.getProperty("haplotypes")%></strong><br />
			<%
				try {
				%>
					F<sub>st</sub> = <%=df.format(fstats.getTheta())%> (Weir and Cockerham 1984 method)<br />

				<%
				}
				catch(Exception e){e.printStackTrace();}
					
			}
		
		//let's calculate Fst for each of the loci
		//iterate through the loci
		ArrayList<String> loci=myShepherd.getAllLoci();
		int numLoci=loci.size();
		for(int r=0;r<numLoci;r++){
			String locus=loci.get(r);
			if(((request.getParameter("hasMSMarkers")!=null)||((request.getParameter(locus)!=null)&&(request1.getParameter(locus)!=null))) && (query1Individuals.size()>0)&&(query2Individuals.size()>0)){
				

				
				//ok, now we need all possible allele values for this locus
				
				ArrayList<Integer> matchingValues=new ArrayList<Integer>();
				FStatistics fstats=new FStatistics(2);
				
				for(int k=0;k<query1Size;k++){
					MarkedIndividual indie=(MarkedIndividual)query1Individuals.get(k);
					ArrayList<Integer> localValues=indie.getAlleleValuesForLocus(locus);
					int localValuesSize=localValues.size();

					if(localValuesSize==1){
						fstats.loadIndividual(localValues.get(0).intValue(), localValues.get(0).intValue(), 1);
					}
					else if(localValuesSize==2){
						fstats.loadIndividual(localValues.get(0).intValue(), localValues.get(1).intValue(), 1);
					}
					
					
				}
				
				for(int k=0;k<query2Size;k++){
					MarkedIndividual indie=(MarkedIndividual)query2Individuals.get(k);
					ArrayList<Integer> localValues=indie.getAlleleValuesForLocus(locus);
					int localValuesSize=localValues.size();

					if(localValuesSize==1){
						fstats.loadIndividual(localValues.get(0).intValue(), localValues.get(0).intValue(), 2);
					}
					else if(localValuesSize==2){
						fstats.loadIndividual(localValues.get(0).intValue(), localValues.get(1).intValue(), 2);
					}
					
					
				}
			
				%>
				
				<p><strong><%=locus %></strong><br />
				<%
				try {
				%>
					F<sub>st</sub> = <%=df.format(fstats.getTheta())%><br />
					(Weir and Cockerham 1984 method)</p>
				<%
				}
				catch(Exception e){e.printStackTrace();}
				
				
			}
		}
		
			

 			%>
			</td>
	</tr>
</table>


<%

     try {
 %>
 
<table class="comparison">
<tr>
<th>
<%if((request1.getParameter("searchNameField")!=null)&&(!request1.getParameter("searchNameField").equals(""))){ %>
<%=request1.getParameter("searchNameField")%>
<%
}
else{
%>
<%=props.getProperty("search1Results")%>
<%
}
%>
</th>
<th>
<%if((request.getParameter("searchNameField")!=null)&&(!request.getParameter("searchNameField").equals(""))){ %>
<%=request.getParameter("searchNameField")%>
<%
}
else{
%>
<%=props.getProperty("search2Results")%>
<%
}
%>
</th>
</tr>
<tr>
	<td>
		<p><%=MessageFormat.format(propsAnalysisShared.getProperty("numberMatchingIndividuals"), query1Individuals.size())%></p>
	</td>
	<td>
		<p><%=MessageFormat.format(propsAnalysisShared.getProperty("numberMatchingIndividuals"), query2Individuals.size())%></p>
	</td>
</tr>
<tr>
<!-- Measurements section for Search results 1 -->
<td>
<p><strong><%=propsAnalysisShared.getProperty("section.measurements")%></strong></p>
<%
		// Measurement (search 1)
		if (measurementTypes.size() > 0) {
			for (int b = 0; b < numMeasurementTypes; b++) {

				// Report averages
				if (measurementValues1[b].getN() > 0) {
					String sd = MessageFormat.format(propsAnalysisShared.getProperty("standardDeviation"), df.format(measurementValues1[b].getStandardDeviation()));
%>
	<p><%=MessageFormat.format(propsAnalysisShared.getProperty("mean"), measurementTypes.get(b).getLabel(), df.format(measurementValues1[b].getMean()), measurementTypes.get(b).getUnitsLabel(), sd, measurementValues1[b].getN())%></p>
	<ul>
		<!-- Largest -->
		<li><%=MessageFormat.format(propsAnalysisShared.getProperty("largest"), df.format(measurementValues1[b].getMax()), measurementTypes.get(b).getUnitsLabel())%> (<a href="../individuals.jsp?number=<%=largestIndies1[b]%>"><%=largestIndies1[b]%></a>)</li>
		<!-- Smallest -->
		<li><%=MessageFormat.format(propsAnalysisShared.getProperty("smallest"), df.format(measurementValues1[b].getMin()), measurementTypes.get(b).getUnitsLabel())%> (<a href="../individuals.jsp?number=<%=smallestIndies1[b]%>"><%=smallestIndies1[b]%></a>)</li>
		<!-- Males -->
<%  			if (measurementValuesMales1[b].getN() == 0) { %>
		<li><%=propsAnalysisShared.getProperty("meanMales0")%></li>
<%  			} else { %>
		<li><%=MessageFormat.format(propsAnalysisShared.getProperty("meanMales"), df.format(measurementValuesMales1[b].getMean()), measurementTypes.get(b).getUnitsLabel(), MessageFormat.format(propsAnalysisShared.getProperty("standardDeviation"), df.format(measurementValuesMales1[b].getStandardDeviation())), measurementValuesMales1[b].getN())%></li>
<%  			} %>
		<!-- Females -->
<%  			if (measurementValuesFemales1[b].getN() == 0) { %>
		<li><%=propsAnalysisShared.getProperty("meanFemales0")%></li>
<%  			} else { %>
		<li><%=MessageFormat.format(propsAnalysisShared.getProperty("meanFemales"), df.format(measurementValuesFemales1[b].getMean()), measurementTypes.get(b).getUnitsLabel(), MessageFormat.format(propsAnalysisShared.getProperty("standardDeviation"), df.format(measurementValuesFemales1[b].getStandardDeviation())), measurementValuesFemales1[b].getN())%></li>
<%  			} %>
		<!-- New -->
<%  			if (measurementValuesNew1[b].getN() == 0) { %>
		<li><%=propsAnalysisShared.getProperty("meanNewIndividuals0")%></li>
<%  			} else { %>
		<li><%=MessageFormat.format(propsAnalysisShared.getProperty("meanNewIndividuals"), df.format(measurementValuesNew1[b].getMean()), measurementTypes.get(b).getUnitsLabel(), MessageFormat.format(propsAnalysisShared.getProperty("standardDeviation"), df.format(measurementValuesNew1[b].getStandardDeviation())), measurementValuesNew1[b].getN())%></li>
<%  			} %>
		<!-- Resight -->
<%  			if (measurementValuesResights1[b].getN() == 0) { %>
		<li><%=propsAnalysisShared.getProperty("meanExistingIndividuals0")%></li>
<%  			} else { %>
		<li><%=MessageFormat.format(propsAnalysisShared.getProperty("meanExistingIndividuals"), df.format(measurementValuesResights1[b].getMean()), measurementTypes.get(b).getUnitsLabel(), MessageFormat.format(propsAnalysisShared.getProperty("standardDeviation"), df.format(measurementValuesResights1[b].getStandardDeviation())), measurementValuesResights1[b].getN())%></li>
<%  			} %>
	</ul>
<%
				}
				else {
%>
	<p><%=MessageFormat.format(propsAnalysisShared.getProperty("mean0"), propsAnalysisShared.getProperty("noMeasurementValues"))%></p>
<%
				}
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
 		// Biomeasurement (search 1)
		if (bioMeasurementTypes.size() > 0) {
			for (int b = 0; b < numMeasurementTypes; b++) {

				// Report averages
				if (bioMeasurementValues1[b].getN() > 0) {
					String sd = MessageFormat.format(propsAnalysisShared.getProperty("standardDeviation"), df.format(bioMeasurementValues1[b].getStandardDeviation()));
%>
	<p><%=MessageFormat.format(propsAnalysisShared.getProperty("mean"), bioMeasurementTypes.get(b).getLabel(), df.format(bioMeasurementValues1[b].getMean()), bioMeasurementTypes.get(b).getUnitsLabel(), sd, bioMeasurementValues1[b].getN())%></p>
	<ul>
		<!-- Largest -->
		<li><%=MessageFormat.format(propsAnalysisShared.getProperty("largest"), df.format(bioMeasurementValues1[b].getMax()), bioMeasurementTypes.get(b).getUnitsLabel())%> (<a href="../individuals.jsp?number=<%=bioLargestIndies1[b]%>"><%=bioLargestIndies1[b]%></a>)</li>
		<!-- Smallest -->
		<li><%=MessageFormat.format(propsAnalysisShared.getProperty("smallest"), df.format(bioMeasurementValues1[b].getMin()), bioMeasurementTypes.get(b).getUnitsLabel())%> (<a href="../individuals.jsp?number=<%=bioSmallestIndies1[b]%>"><%=bioSmallestIndies1[b]%></a>)</li>
		<!-- Males -->
<%  			if (bioMeasurementValuesMales1[b].getN() == 0) { %>
		<li><%=propsAnalysisShared.getProperty("meanMales0")%></li>
<%  			} else { %>
		<li><%=MessageFormat.format(propsAnalysisShared.getProperty("meanMales"), df.format(bioMeasurementValuesMales1[b].getMean()), bioMeasurementTypes.get(b).getUnitsLabel(), MessageFormat.format(propsAnalysisShared.getProperty("standardDeviation"), df.format(bioMeasurementValuesMales1[b].getStandardDeviation())), bioMeasurementValuesMales1[b].getN())%></li>
<%  			} %>
		<!-- Females -->
<%  			if (bioMeasurementValuesFemales1[b].getN() == 0) { %>
		<li><%=propsAnalysisShared.getProperty("meanFemales0")%></li>
<%  			} else { %>
		<li><%=MessageFormat.format(propsAnalysisShared.getProperty("meanFemales"), df.format(bioMeasurementValuesFemales1[b].getMean()), bioMeasurementTypes.get(b).getUnitsLabel(), MessageFormat.format(propsAnalysisShared.getProperty("standardDeviation"), df.format(bioMeasurementValuesFemales1[b].getStandardDeviation())), bioMeasurementValuesFemales1[b].getN())%></li>
<%  			} %>
		<!-- New -->
<%  			if (bioMeasurementValuesNew1[b].getN() == 0) { %>
		<li><%=propsAnalysisShared.getProperty("meanNewIndividuals0")%></li>
<%  			} else { %>
		<li><%=MessageFormat.format(propsAnalysisShared.getProperty("meanNewIndividuals"), df.format(bioMeasurementValuesNew1[b].getMean()), bioMeasurementTypes.get(b).getUnitsLabel(), MessageFormat.format(propsAnalysisShared.getProperty("standardDeviation"), df.format(bioMeasurementValuesNew1[b].getStandardDeviation())), bioMeasurementValuesNew1[b].getN())%></li>
<%  			} %>
		<!-- Resight -->
<%  			if (bioMeasurementValuesResights1[b].getN() == 0) { %>
		<li><%=propsAnalysisShared.getProperty("meanExistingIndividuals0")%></li>
<%  			} else { %>
		<li><%=MessageFormat.format(propsAnalysisShared.getProperty("meanExistingIndividuals"), df.format(bioMeasurementValuesResights1[b].getMean()), bioMeasurementTypes.get(b).getUnitsLabel(), MessageFormat.format(propsAnalysisShared.getProperty("standardDeviation"), df.format(bioMeasurementValuesResights1[b].getStandardDeviation())), bioMeasurementValuesResights1[b].getN())%></li>
<%  			} %>
	</ul>
<%
				}
				else {
%>
	<p><%=MessageFormat.format(propsAnalysisShared.getProperty("mean0"), bioMeasurementTypes.get(b).getLabel(), propsAnalysisShared.getProperty("noMeasurementValues"))%></p>
<%
				}
			}
		}
		else {
%>
			<p><%=propsAnalysisShared.getProperty("noMeasurementTypes")%></p>
<%
		}
%>
</td>

<!-- Measurements section for Search results 2 -->
<td>
	<p><strong><%=propsAnalysisShared.getProperty("section.measurements")%></strong></p>
<%
 		// Measurement (search 2)
		if (measurementTypes.size() > 0) {
			for (int b = 0; b < numMeasurementTypes; b++) {

				// Report averages
				if (measurementValues2[b].getN() > 0) {
					String sd = MessageFormat.format(propsAnalysisShared.getProperty("standardDeviation"), df.format(measurementValues2[b].getStandardDeviation()));
%>
	<p><%=MessageFormat.format(propsAnalysisShared.getProperty("mean"), measurementTypes.get(b).getLabel(), df.format(measurementValues2[b].getMean()), measurementTypes.get(b).getUnitsLabel(), sd, measurementValues2[b].getN())%></p>
	<ul>
		<!-- Largest -->
		<li><%=MessageFormat.format(propsAnalysisShared.getProperty("largest"), df.format(measurementValues2[b].getMax()), measurementTypes.get(b).getUnitsLabel())%> (<a href="../individuals.jsp?number=<%=largestIndies2[b]%>"><%=largestIndies2[b]%></a>)</li>
		<!-- Smallest -->
		<li><%=MessageFormat.format(propsAnalysisShared.getProperty("smallest"), df.format(measurementValues2[b].getMin()), measurementTypes.get(b).getUnitsLabel())%> (<a href="../individuals.jsp?number=<%=smallestIndies2[b]%>"><%=smallestIndies2[b]%></a>)</li>
		<!-- Males -->
<%  		if (measurementValuesMales2[b].getN() == 0) { %>
		<li><%=propsAnalysisShared.getProperty("meanMales0")%></li>
<%  		} else { %>
		<li><%=MessageFormat.format(propsAnalysisShared.getProperty("meanMales"), df.format(measurementValuesMales2[b].getMean()), measurementTypes.get(b).getUnitsLabel(), MessageFormat.format(propsAnalysisShared.getProperty("standardDeviation"), df.format(measurementValuesMales2[b].getStandardDeviation())), measurementValuesMales2[b].getN())%></li>
<%  		} %>
		<!-- Females -->
<%  		if (measurementValuesFemales2[b].getN() == 0) { %>
		<li><%=propsAnalysisShared.getProperty("meanFemales0")%></li>
<%  		} else { %>
		<li><%=MessageFormat.format(propsAnalysisShared.getProperty("meanFemales"), df.format(measurementValuesFemales2[b].getMean()), measurementTypes.get(b).getUnitsLabel(), MessageFormat.format(propsAnalysisShared.getProperty("standardDeviation"), df.format(measurementValuesFemales2[b].getStandardDeviation())), measurementValuesFemales2[b].getN())%></li>
<%  		} %>
		<!-- New -->
<%  		if (measurementValuesNew2[b].getN() == 0) { %>
		<li><%=propsAnalysisShared.getProperty("meanNewIndividuals0")%></li>
<%  		} else { %>
		<li><%=MessageFormat.format(propsAnalysisShared.getProperty("meanNewIndividuals"), df.format(measurementValuesNew2[b].getMean()), measurementTypes.get(b).getUnitsLabel(), MessageFormat.format(propsAnalysisShared.getProperty("standardDeviation"), df.format(measurementValuesNew2[b].getStandardDeviation())), measurementValuesNew2[b].getN())%></li>
<%  		} %>
		<!-- Resight -->
<%  if (measurementValuesResights2[b].getN() == 0) { %>
		<li><%=propsAnalysisShared.getProperty("meanExistingIndividuals0")%></li>
<%  		} else { %>
		<li><%=MessageFormat.format(propsAnalysisShared.getProperty("meanExistingIndividuals"), df.format(measurementValuesResights2[b].getMean()), measurementTypes.get(b).getUnitsLabel(), MessageFormat.format(propsAnalysisShared.getProperty("standardDeviation"), df.format(measurementValuesResights2[b].getStandardDeviation())), measurementValuesResights2[b].getN())%></li>
<%  		} %>
	</ul>
<%
				}
				else {
%>
	<p><%=MessageFormat.format(propsAnalysisShared.getProperty("mean0"), measurementTypes.get(b).getLabel(), propsAnalysisShared.getProperty("noMeasurementValues"))%></p>
<%
				}
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
		// Biomeasurement (search 2)
		if (bioMeasurementTypes.size() > 0) {
			for (int b = 0; b < numMeasurementTypes; b++) {

				// Report averages
				if (bioMeasurementValues2[b].getN() > 0) {
					String sd = MessageFormat.format(propsAnalysisShared.getProperty("standardDeviation"), df.format(bioMeasurementValues2[b].getStandardDeviation()));
%>
	<p><%=MessageFormat.format(propsAnalysisShared.getProperty("mean"), bioMeasurementTypes.get(b).getLabel(), df.format(bioMeasurementValues2[b].getMean()), bioMeasurementTypes.get(b).getUnitsLabel(), sd, bioMeasurementValues2[b].getN())%></p>
	<ul>
		<!-- Largest -->
		<li><%=MessageFormat.format(propsAnalysisShared.getProperty("largest"), df.format(bioMeasurementValues2[b].getMax()), bioMeasurementTypes.get(b).getUnitsLabel())%> (<a href="../individuals.jsp?number=<%=bioLargestIndies2[b]%>"><%=bioLargestIndies2[b]%></a>)</li>
		<!-- Smallest -->
		<li><%=MessageFormat.format(propsAnalysisShared.getProperty("smallest"), df.format(bioMeasurementValues2[b].getMin()), bioMeasurementTypes.get(b).getUnitsLabel())%> (<a href="../individuals.jsp?number=<%=bioSmallestIndies2[b]%>"><%=bioSmallestIndies2[b]%></a>)</li>
		<!-- Males -->
<%  			if (bioMeasurementValuesMales2[b].getN() == 0) { %>
		<li><%=propsAnalysisShared.getProperty("meanMales0")%></li>
<%  			} else { %>
		<li><%=MessageFormat.format(propsAnalysisShared.getProperty("meanMales"), df.format(bioMeasurementValuesMales2[b].getMean()), bioMeasurementTypes.get(b).getUnitsLabel(), MessageFormat.format(propsAnalysisShared.getProperty("standardDeviation"), df.format(bioMeasurementValuesMales2[b].getStandardDeviation())), bioMeasurementValuesMales2[b].getN())%></li>
<%  			} %>
		<!-- Females -->
<%  			if (bioMeasurementValuesFemales2[b].getN() == 0) { %>
		<li><%=propsAnalysisShared.getProperty("meanFemales0")%></li>
<%  			} else { %>
		<li><%=MessageFormat.format(propsAnalysisShared.getProperty("meanFemales"), df.format(bioMeasurementValuesFemales2[b].getMean()), bioMeasurementTypes.get(b).getUnitsLabel(), MessageFormat.format(propsAnalysisShared.getProperty("standardDeviation"), df.format(bioMeasurementValuesFemales2[b].getStandardDeviation())), bioMeasurementValuesFemales2[b].getN())%></li>
<%  			} %>
		<!-- New -->
<%  			if (bioMeasurementValuesNew2[b].getN() == 0) { %>
		<li><%=propsAnalysisShared.getProperty("meanNewIndividuals0")%></li>
<%  			} else { %>
		<li><%=MessageFormat.format(propsAnalysisShared.getProperty("meanNewIndividuals"), df.format(bioMeasurementValuesNew2[b].getMean()), bioMeasurementTypes.get(b).getUnitsLabel(), MessageFormat.format(propsAnalysisShared.getProperty("standardDeviation"), df.format(bioMeasurementValuesNew2[b].getStandardDeviation())), bioMeasurementValuesNew2[b].getN())%></li>
<%  			} %>
		<!-- Resight -->
<%  			if (bioMeasurementValuesResights2[b].getN() == 0) { %>
		<li><%=propsAnalysisShared.getProperty("meanExistingIndividuals0")%></li>
<%  			} else { %>
		<li><%=MessageFormat.format(propsAnalysisShared.getProperty("meanExistingIndividuals"), df.format(bioMeasurementValuesResights2[b].getMean()), bioMeasurementTypes.get(b).getUnitsLabel(), MessageFormat.format(propsAnalysisShared.getProperty("standardDeviation"), df.format(bioMeasurementValuesResights2[b].getStandardDeviation())), bioMeasurementValuesResights2[b].getN())%></li>
<%  			} %>
	</ul>
<%
				}
				else {
%>
	<p><%=MessageFormat.format(propsAnalysisShared.getProperty("mean0"), bioMeasurementTypes.get(b).getLabel(), propsAnalysisShared.getProperty("noMeasurementValues"))%></p>
<%
				}
			}
		}
		else {
%>
			<p><%=propsAnalysisShared.getProperty("noMeasurementTypes")%></p>
<%
		}
%>
</td>
</tr>

<tr>
	<td>
	<table class="comparison"><tr><td>
	
 			<div id="chart_div1"></div>
 		

 		
 		</td></tr></table>
 	</td>
 	<td>
 	<table class="comparison"><tr><td>
 		 <div id="chart_div2"></div>
 		 </td></tr></table>
 	</td>
 </tr>		
<tr>
	<td>
	<table class="comparison"><tr><td>
		<div id="sexchart_div1"></div>
		</td></tr></table>
	</td>
	<td>
	<table class="comparison"><tr><td>
		<div id="sexchart_div2"></div>
		</td></tr></table>
	</td>
</tr>
<tr>
	<td>
		<%
        //set the previous maps search box if set
        if((request1.getParameter("ne_lat")!=null) && (request1.getParameter("ne_long")!=null) && (request1.getParameter("sw_lat")!=null) && (request1.getParameter("ne_long")!=null)&&(!request1.getParameter("ne_lat").trim().equals("")) &&(!request1.getParameter("ne_long").trim().equals("")) && (!request1.getParameter("sw_lat").trim().equals("")) && (!request1.getParameter("sw_long").trim().equals(""))){
        %>   
        <table class="comparison"><tr><td>
			<div id="map_canvas1" style="width: 300px; height: 200px; "></div>
			</td></tr></table>
		<%
        }
        else{
		%>
		<table class="comparison"><tr><td>
			<p><%=props.getProperty("noGPS")%></p>
			</td></tr></table>
		<%
        }
		%>
	</td>
	<td>
		<%
        //set the previous maps search box if set
        if((request.getParameter("ne_lat")!=null) && (request.getParameter("ne_long")!=null) && (request.getParameter("sw_lat")!=null) && (request.getParameter("ne_long")!=null)&&(!request.getParameter("ne_lat").trim().equals("")) &&(!request.getParameter("ne_long").trim().equals("")) && (!request.getParameter("sw_lat").trim().equals("")) && (!request.getParameter("sw_long").trim().equals(""))){
        %>   
        <table class="comparison"><tr><td>
			<div id="map_canvas2" style="width: 300px; height: 200px; "></div>
			</td></tr></table>
		<%
        }
        else{
		%>
			<table class="comparison"><tr><td>
				<p><%=props.getProperty("noGPS")%></p>
			</td></tr></table>
		<%
        }
		%>
	</td>		
</tr>


<tr>
	<td>
		<div>
			<table class="comparison"><tr><td>
      		<p><strong><%=propsShared.getProperty("queryDetails")%></strong></p>

      		<p class="caption"><strong><%=propsShared.getProperty("prettyPrintResults")%></strong><br/>
        		<%=queryResult1.getQueryPrettyPrint().replaceAll("locationField", propsShared.getProperty("location")).replaceAll("locationCodeField", propsShared.getProperty("locationID")).replaceAll("verbatimEventDateField", propsShared.getProperty("verbatimEventDate")).replaceAll("alternateIDField", propsShared.getProperty("alternateID")).replaceAll("behaviorField", propsShared.getProperty("behavior")).replaceAll("Sex", propsShared.getProperty("sex")).replaceAll("nameField", propsShared.getProperty("nameField")).replaceAll("selectLength", propsShared.getProperty("selectLength")).replaceAll("numResights", propsShared.getProperty("numResights")).replaceAll("vesselField", propsShared.getProperty("vesselField"))%>
      		</p>

      		
      
		<%
		if(request.getParameter("debug")!=null){ 
			
			//another test  comment
			
		%>   
			<p class="caption"><strong><%=propsShared.getProperty("jdoql")%></strong>
      		<br/>
        	<%=queryResult1.getJDOQLRepresentation()%></p>
      	<%
     	}
      	%>


		</td></tr></table>
	</div>
</td>

<td>
<div>
		<table class="comparison"><tr><td>
      <p><strong><%=propsShared.getProperty("queryDetails")%>
      </strong></p>

      <p class="caption"><strong><%=propsShared.getProperty("prettyPrintResults")%>
      </strong><br/>
        <%=queryResult2.getQueryPrettyPrint().replaceAll("locationField", propsShared.getProperty("location")).replaceAll("locationCodeField", propsShared.getProperty("locationID")).replaceAll("verbatimEventDateField", propsShared.getProperty("verbatimEventDate")).replaceAll("alternateIDField", propsShared.getProperty("alternateID")).replaceAll("behaviorField", propsShared.getProperty("behavior")).replaceAll("Sex", propsShared.getProperty("sex")).replaceAll("nameField", propsShared.getProperty("nameField")).replaceAll("selectLength", propsShared.getProperty("selectLength")).replaceAll("numResights", propsShared.getProperty("numResights")).replaceAll("vesselField", propsShared.getProperty("vesselField"))%>
      </p>

      	<%
      	if(request.getParameter("debug")!=null){ 
      		%>   
			<p class="caption"><strong><%=propsShared.getProperty("jdoql")%></strong>
      		<br/>
        	<%=queryResult2.getJDOQLRepresentation()%></p>
      	<%
     	}
      	
      	//test comment
      	
      	//another test comment
      	
      	%>
      </td></tr></table>
</div>
 </td>
 </tr>
 
 <tr>
	 <td>
		 <p><strong><%=props.getProperty("exportOptions")%></strong></p>
		 <p><%=props.getProperty("genepopExport")%>: <a href="http://<%=CommonConfiguration.getURLLocation(request)%>/GenePopExport?<%=request.getQueryString()%>"><%=props.getProperty("link")%></a></p>
		 <p><%=props.getProperty("genalexExportMS")%>: <a href="http://<%=CommonConfiguration.getURLLocation(request)%>/GenalexExportCodominantMSDataBySize?<%=request.getQueryString()%>"><%=props.getProperty("link")%></a></p>
		 <p><%=props.getProperty("genalexExportHaplotypes")%>: <a href="http://<%=CommonConfiguration.getURLLocation(request)%>/GenalexExportCodominantMSDataBySize?<%=request.getQueryString()%>&exportHaplos=true"><%=props.getProperty("link")%></a></p>
	 </td>
 </tr>
 </table>
 <%
     } 
     catch (Exception e) {
       e.printStackTrace();
       %>
       <script type="text\javascript">
       		alert("I hit an exception!");
       </script>
       <%
       
     }

   myShepherd.rollbackDBTransaction();
   myShepherd.closeDBTransaction();
   query1Individuals = null;
   query2Individuals = null;
%>

 </div>
 
<jsp:include page="../footer.jsp" flush="true"/>

<%
    }
    else {
%>
    <!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
    <html>
    <head>
    <meta http-equiv="REFRESH" content="0;url=searchComparison.jsp" />
    </head>
    <body>
    </body>
    </html>
<%
    }
%>
