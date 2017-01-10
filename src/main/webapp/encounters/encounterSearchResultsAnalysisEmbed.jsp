<%--
  ~ Wildbook - A Mark-Recapture Framework
  ~ Copyright (C) 2015 \
  
  
  Jason Holmberg
  ~
  ~ This program is free software; you can redistribute it and/or
  ~ modify it under the terms of the GNU General Public License
  ~ as published by the Free Software Foundation; either version 2
  ~ of the License, or (at your option) any later version.
  ~
  ~ This program is distributed in the hope that it will be useful,
  ~ but WITHOUT ANY WARRANTY; without even the implied warranty of
  ~ MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  ~ GNU General Public License for more details.
  ~
  ~ You should have received a copy of the GNU General Public License
  ~ along with this program; if not, write to the Free Software
  ~ Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
  --%>

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">

<%@ page contentType="text/html; charset=utf-8" language="java"
         import="javax.jdo.Query, org.ecocean.servlet.ServletUtilities,java.text.DecimalFormat,org.ecocean.Util.MeasurementDesc,org.apache.commons.math.stat.descriptive.SummaryStatistics,java.util.Vector,java.util.Properties,org.ecocean.genetics.*,java.util.*,java.net.URI, org.ecocean.*, org.ecocean.security.Collaboration" %>


  <%
  //System.out.println("jdoQLstring is: "+request.getParameter("jdoqlString"));
  String context="context0";
  context=ServletUtilities.getContext(request);
  
  DecimalFormat df = new DecimalFormat("#.##");

    //let's load encounterSearch.properties
    //String langCode = "en";
    String langCode=ServletUtilities.getLanguageCode(request);
    
    Properties encprops = new Properties();
    //encprops.load(getClass().getResourceAsStream("/bundles/" + langCode + "/searchResultsAnalysis.properties"));
    encprops=ShepherdProperties.getProperties("searchResultsAnalysis.properties", langCode, context);
    
    
    Properties haploprops = new Properties();
    //haploprops.load(getClass().getResourceAsStream("/bundles/haplotypeColorCodes.properties"));
	haploprops=ShepherdProperties.getProperties("haplotypeColorCodes.properties", "",context);

		Properties collabProps = new Properties();
 		collabProps=ShepherdProperties.getProperties("collaboration.properties", langCode, context);
    
    //get our Shepherd
    Shepherd myShepherd = new Shepherd(context);
    myShepherd.setAction("encounterSearchResultsAnalysisEmbed.jsp");


    
 	//prep for measurements summary
 	List<MeasurementDesc> measurementTypes=Util.findMeasurementDescs("en",context);
 	int numMeasurementTypes=measurementTypes.size();
 	SummaryStatistics[] measurementValues=new SummaryStatistics[numMeasurementTypes];
 	SummaryStatistics[] measurementValuesMales=new SummaryStatistics[numMeasurementTypes];
 	SummaryStatistics[] measurementValuesFemales=new SummaryStatistics[numMeasurementTypes];
 	for(int b=0;b<measurementValues.length;b++){
 		measurementValues[b]=new SummaryStatistics();
 		measurementValuesMales[b]=new SummaryStatistics();
 		measurementValuesFemales[b]=new SummaryStatistics();

 	}

 	
 	//prep for biomeasurements summary
 	List<MeasurementDesc> bioMeasurementTypes=Util.findBiologicalMeasurementDescs("en",context);
 	int numBioMeasurementTypes=bioMeasurementTypes.size();
 	SummaryStatistics[] bioMeasurementValues=new SummaryStatistics[numBioMeasurementTypes];
 	SummaryStatistics[] bioMeasurementValuesMales=new SummaryStatistics[numBioMeasurementTypes];
 	SummaryStatistics[] bioMeasurementValuesFemales=new SummaryStatistics[numBioMeasurementTypes];
 	for(int b=0;b<bioMeasurementValues.length;b++){
 		bioMeasurementValues[b]=new SummaryStatistics();
 		bioMeasurementValuesMales[b]=new SummaryStatistics();
 		bioMeasurementValuesFemales[b]=new SummaryStatistics();
 	}



    int numResults = 0;

    //set up the vector for matching encounters
    Vector rEncounters = new Vector();

    //kick off the transaction
    myShepherd.beginDBTransaction();

    //start the query and get the results
    String order = "";
    //EncounterQueryResult queryResult = EncounterQueryProcessor.processQuery(myShepherd, request, order);
    //rEncounters = queryResult.getResult();

    Query acceptedEncounters = myShepherd.getPM().newQuery(request.getParameter("jdoqlString"));
    acceptedEncounters.setOrdering("dwcDateAddedLong ascending");
    Collection c = (Collection) (acceptedEncounters.execute());
    rEncounters=new Vector(c);
    acceptedEncounters.closeAll();
    
		Vector blocked = Encounter.blocked(rEncounters, request);
		boolean accessible = (blocked.size() < 1);

    //let's prep the HashTable for the haplo pie chart
    List<String> allHaplos2=myShepherd.getAllHaplotypes(); 
    int numHaplos2 = allHaplos2.size();
    Hashtable<String,Integer> pieHashtable = new Hashtable<String,Integer>();
 	for(int gg=0;gg<numHaplos2;gg++){
 		String thisHaplo=allHaplos2.get(gg);
 		pieHashtable.put(thisHaplo, new Integer(0));
 	}
    
 	//let's prep the HashTable for the sex pie chart
 	Hashtable<String,Integer> sexHashtable = new Hashtable<String,Integer>();
 	sexHashtable.put("male", new Integer(0));
 	sexHashtable.put("female", new Integer(0));
 	sexHashtable.put("unknown", new Integer(0));
 	
 	//let's prep the HashTable for the species pie chart
 	  List<String> allSpecies2=CommonConfiguration.getIndexedPropertyValues("genusSpecies",context); 
 	  int numSpecies2 = allSpecies2.size();
 	  Hashtable<String,Integer> speciesHashtable = new Hashtable<String,Integer>();
 		for(int gg=0;gg<numSpecies2;gg++){
 			String thisSpecies=allSpecies2.get(gg);
 			
 			StringTokenizer tokenizer=new StringTokenizer(thisSpecies," ");
 	  		if(tokenizer.countTokens()>=2){

 	  			thisSpecies=tokenizer.nextToken()+" "+tokenizer.nextToken().replaceAll(",","").replaceAll("_"," ");
 	          	//enc.setGenus(tokenizer.nextToken());
 	          	//enc.setSpecificEpithet();

 	  	    }
 			
 			speciesHashtable.put(thisSpecies, new Integer(0));
 		}
 		
 		
 		//let's prep the HashTable for the country pie chart
 		  List<String> allCountries=myShepherd.getAllCountries(); 
 		  int numCountries= allCountries.size();
 		  Hashtable<String,Integer> countriesHashtable = new Hashtable<String,Integer>();
 			for(int gg=0;gg<numCountries;gg++){
 				String thisCountry=allCountries.get(gg);
 				if(thisCountry!=null){
 					countriesHashtable.put(thisCountry, new Integer(0));
 				}
 				
 			}
 			
 	 		//let's prep the HashTable for the state pie chart
 	 		  List<String> states=CommonConfiguration.getIndexedPropertyValues("encounterState",context);
 	 		  int numStates= states.size();
 	 		  Hashtable<String,Integer> statesHashtable = new Hashtable<String,Integer>();
 	 			for(int gg=0;gg<numStates;gg++){
 	 				String thisState=states.get(gg);
 	 				if(thisState!=null){
 	 					statesHashtable.put(thisState, new Integer(0));
 	 				}
 	 				
 	 			}
 			
 			
 	 		//let's prep the HashTable for the assigned users pie chart
 	 		  List<User> allUsers=myShepherd.getAllUsers(); 
 	 		  int numUsers= allUsers.size();
 	 		  Hashtable<String,Integer> usersHashtable = new Hashtable<String,Integer>();
 	 			for(int gg=0;gg<numUsers;gg++){
 	 				String thisUser=allUsers.get(gg).getUsername();
 	 				if(thisUser!=null){
 	 					usersHashtable.put(thisUser, new Integer(0));
 	 				}
 	 				
 	 			}
 	
 			
 			//let's prep the data structures for the discovery curve
 			Hashtable<Integer,Integer> discoveryCurveInflectionPoints= new Hashtable<Integer,Integer>();
 			ArrayList<String> dailyDuplicates=new ArrayList<String>();
 					
 			//let's prep the data structures for weekly frequency
 			Hashtable<Integer,Integer> frequencyWeeks = new Hashtable<Integer,Integer>();
 			//ArrayList<String> dailyDuplicates2=new ArrayList<String>();
 			for(int p=1;p<=53;p++){
 				frequencyWeeks.put(p, 0);
 			}	
 			
 			//let's prep the bar charts for encounters per year
 			Hashtable<Integer,Integer> encountersPerYear= new Hashtable<Integer,Integer>();
 					
 		
 	int numPhotos=0;
 	int numContributors=0;
 	int numIdentified=0;
 	StringBuffer contributors=new StringBuffer();
 	int resultSize=rEncounters.size();
 	ArrayList<String> markedIndividuals=new ArrayList<String>();
 	int numUniqueEncounters=0;
 	 for(int y=0;y<resultSize;y++){
 		 
 		 
 		 Encounter thisEnc=(Encounter)rEncounters.get(y);
 		 
 		 //iterate up unique encounters number
 		 numUniqueEncounters++;

 		 //calculate number photos collected
 		 if(thisEnc.getAdditionalImageNames()!=null){
 		 	numPhotos=numPhotos+thisEnc.getAnnotations().size();
 		 }
 			
 		//calculate the number of submitter contributors
 		if((thisEnc.getSubmitterEmail()!=null)&&(!thisEnc.getSubmitterEmail().equals(""))) {
 				//check for comma separated list
 				if(thisEnc.getSubmitterEmail().indexOf(",")!=-1) {
 					//break up the string
 					StringTokenizer stzr=new StringTokenizer(thisEnc.getSubmitterEmail(),",");
 					while(stzr.hasMoreTokens()) {
 						String token=stzr.nextToken();
 						if (contributors.indexOf(token)==-1) {
 							contributors.append(token);
 							numContributors++;
 						}
 					}
 				}
 				else if (contributors.indexOf(thisEnc.getSubmitterEmail())==-1) {
 					contributors.append(thisEnc.getSubmitterEmail());
 					numContributors++;
 				}
 			}
 			

 			
 			
 			//calculate the number of photographer contributors
 			if((thisEnc.getPhotographerEmail()!=null)&&(!thisEnc.getPhotographerEmail().equals(""))) {
 				//check for comma separated list
 				if(thisEnc.getPhotographerEmail().indexOf(",")!=-1) {
 					//break up the string
 					StringTokenizer stzr=new StringTokenizer(thisEnc.getPhotographerEmail(),",");
 					while(stzr.hasMoreTokens()) {
 						String token=stzr.nextToken();
 						if (contributors.indexOf(token)==-1) {
 							contributors.append(token);
 							numContributors++;
 						}
 					}
 				}
 				else if (contributors.indexOf(thisEnc.getPhotographerEmail())==-1) {
 					contributors.append(thisEnc.getPhotographerEmail());
 					numContributors++;
 				}
 			}
 		 
 			if((thisEnc.getIndividualID()!=null)&&(!thisEnc.getIndividualID().toLowerCase().equals("unassigned"))){numIdentified++;}
 		 
 		//calculate marked individuals	 
 		 if((thisEnc.getIndividualID()!=null)&&(!thisEnc.getIndividualID().toLowerCase().equals("unassigned"))&&(!markedIndividuals.contains(thisEnc.getIndividualID().trim()))){
 			 
 			
 			 
 			 //add this individual to the list
 			 markedIndividuals.add(thisEnc.getIndividualID().trim());
 			
 			 //check for a daily duplicate
 			// String dailyDuplicateUniqueID=thisEnc.getIndividualID()+":"+thisEnc.getYear()+":"+thisEnc.getMonth()+":"+thisEnc.getDay();
 			// if(!dailyDuplicates.contains(dailyDuplicateUniqueID)){
 			//	dailyDuplicates.add(dailyDuplicateUniqueID);
 				 //set a discovery curve inflection point
 				discoveryCurveInflectionPoints.put(numUniqueEncounters, markedIndividuals.size());
 			 //}
 			 //else{numUniqueEncounters--;}

 		 }
 		 
 		 //weekly frequency tabulation
 		 if((thisEnc.getYear()>-1)&&(thisEnc.getMonth()>-1)&&(thisEnc.getDay()>-1)){
 			 GregorianCalendar cal=new GregorianCalendar(thisEnc.getYear(),thisEnc.getMonth()-1, thisEnc.getDay());
 			 int weekOfYear=cal.get(Calendar.WEEK_OF_YEAR);
 			 %>
 			 
 			 <!-- zzzAdding this date: week of year is <%=weekOfYear  %> for date: <%=thisEnc.getDate() %> -->
 			 
 			 <%
 			 Integer valueForWeek=frequencyWeeks.get(weekOfYear)+1;
 			 frequencyWeeks.put(weekOfYear, valueForWeek);
 		 }
 		 
 		 //year submitted tabulation
 		 if(thisEnc.getDWCDateAddedLong()!=null){
 			
 			 org.joda.time.DateTime myDateAdded =new org.joda.time.DateTime(thisEnc.getDWCDateAddedLong());
 			 Integer year=new Integer(myDateAdded.getYear());
 			 
 			 if(!encountersPerYear.containsKey(year)){
 				 encountersPerYear.put(year, new Integer(0));
 				
 			 }
 			 
 			Integer valueForYear=encountersPerYear.get(year)+1;
 			encountersPerYear.put(year, valueForYear);
 			//System.out.println("    I just put: "+year+":"+valueForYear);	 
 	        
 		 }
 		 	
 		 
 		 //haplotype ie chart prep
 		 	if(thisEnc.getHaplotype()!=null){
      	   		if(pieHashtable.containsKey(thisEnc.getHaplotype().trim())){
      		   		Integer thisInt = pieHashtable.get(thisEnc.getHaplotype().trim())+1;
      		   		pieHashtable.put(thisEnc.getHaplotype().trim(), thisInt);
      	   		}
 	 		}
 		 
 	 		 //state ie chart prep
 		 	if(thisEnc.getState()!=null){
      	   		if(statesHashtable.containsKey(thisEnc.getState().trim())){
      		   		Integer thisInt = statesHashtable.get(thisEnc.getState().trim())+1;
      		   		statesHashtable.put(thisEnc.getState().trim(), thisInt);
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
 	    
 		//check the encounter species
		 
		 if((thisEnc.getGenus()!=null)&&(thisEnc.getSpecificEpithet()!=null)){
			 String encGenusSpecies=thisEnc.getGenus()+" "+thisEnc.getSpecificEpithet();
			 if(speciesHashtable.containsKey(encGenusSpecies)){
	      		   Integer thisInt = speciesHashtable.get(encGenusSpecies)+1;
	      		   speciesHashtable.put(encGenusSpecies, thisInt);
	      		   //numSpeciesEntries++;
	      	   }
			 
		 }
		 
		 
		 //check the Encounter country
		 
		 if(thisEnc.getCountry()!=null){
			 if(countriesHashtable.containsKey(thisEnc.getCountry())){
	      		   Integer thisInt = countriesHashtable.get(thisEnc.getCountry())+1;
	      		   countriesHashtable.put(thisEnc.getCountry(), thisInt);
	      	 		//numCountryEntries++;  
			 }
		 }
		 
		 //check the Encounter user
		 if(thisEnc.getSubmitterID()!=null){
			 if(usersHashtable.containsKey(thisEnc.getSubmitterID())){
	      		   Integer thisInt = usersHashtable.get(thisEnc.getSubmitterID())+1;
	      		   usersHashtable.put(thisEnc.getSubmitterID(), thisInt);
	      	 		//numCountryEntries++;  
			 }
		 }
 	    
 		//measurement
		for(int b=0;b<numMeasurementTypes;b++){
			if(thisEnc.getMeasurement(measurementTypes.get(b).getType())!=null){
				
					measurementValues[b].addValue(thisEnc.getMeasurement(measurementTypes.get(b).getType()).getValue().doubleValue());

					//males versus females analysis
					if((thisEnc.getSex()!=null)&&(thisEnc.getSex().equals("male"))){
						measurementValuesMales[b].addValue(thisEnc.getMeasurement(measurementTypes.get(b).getType()).getValue().doubleValue());
					}
					else if((thisEnc.getSex()!=null)&&(thisEnc.getSex().equals("female"))){
						measurementValuesFemales[b].addValue(thisEnc.getMeasurement(measurementTypes.get(b).getType()).getValue().doubleValue());
					}

			}
		}
		
 		//biomeasurement tabulation
		for(int b=0;b<numBioMeasurementTypes;b++){
			if(thisEnc.getBiologicalMeasurement(bioMeasurementTypes.get(b).getType())!=null){
				
					bioMeasurementValues[b].addValue(thisEnc.getBiologicalMeasurement(bioMeasurementTypes.get(b).getType()).getValue().doubleValue());

					
					//males versus females analysis
					if((thisEnc.getSex()!=null)&&(thisEnc.getSex().equals("male"))){
						bioMeasurementValuesMales[b].addValue(thisEnc.getBiologicalMeasurement(bioMeasurementTypes.get(b).getType()).getValue().doubleValue());
					}
					else if((thisEnc.getSex()!=null)&&(thisEnc.getSex().equals("female"))){
						bioMeasurementValuesFemales[b].addValue(thisEnc.getBiologicalMeasurement(bioMeasurementTypes.get(b).getType()).getValue().doubleValue());
					}
					

					
			}
		}
 	    
 	    
 		 
 	 }	
 	 
 	 
 	 
 	 
  %>

   
  

  
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
  



<% if (accessible) { %>
    
<script type="text/javascript" src="https://www.google.com/jsapi"></script>

<script type="text/javascript">
      google.load("visualization", "1", {packages:["corechart"]});
      
      google.setOnLoadCallback(drawHaploChart);
      function drawHaploChart() {
        var data = new google.visualization.DataTable();
        data.addColumn('string', '<%=encprops.getProperty("haplotype") %>');
        data.addColumn('number', '<%=encprops.getProperty("numberRecorded") %>');
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
          title: '<%=encprops.getProperty("haplotypeChartTitle") %>',
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
					'#<%=haploColor %>',
					<%
                   }
                   %>
                   
                   
          ]
        };

        var chart = new google.visualization.PieChart(document.getElementById('chart_div'));
        chart.draw(data, options);
      }
      
      
      google.setOnLoadCallback(drawStateChart);
      function drawStateChart() {
        var statesdata = new google.visualization.DataTable();
        statesdata.addColumn('string', '<%=encprops.getProperty("state") %>');
        statesdata.addColumn('number', '<%=encprops.getProperty("number") %>');
        statesdata.addRows([
          <%
         
          
          for(int hh=0;hh<numStates;hh++){
          %>
          ['<%=states.get(hh)%>',    <%=statesHashtable.get(states.get(hh))%>]
		  <%
		  if(hh<(numStates-1)){
		  %>
		  ,
		  <%
		  }
          }
		  %>
          
        ]);

        var stateoptions = {
          width: 450, height: 300,
          title: '<%=encprops.getProperty("encounterStateTitle") %>',

        };

        var stateschart = new google.visualization.PieChart(document.getElementById('states_div'));
        stateschart.draw(statesdata, stateoptions);
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
          title: '<%=encprops.getProperty("sexChartTitle") %>',
          colors: ['#0000FF','#FF00FF','<%=haploColor%>']
        };

        var chart = new google.visualization.PieChart(document.getElementById('sexchart_div'));
        chart.draw(data, options);
      }
      
      google.setOnLoadCallback(drawSpeciesChart);
      function drawSpeciesChart() {
        var speciesData = new google.visualization.DataTable();
        speciesData.addColumn('string', '<%=encprops.getProperty("species") %>');
        speciesData.addColumn('number', '<%=encprops.getProperty("numberRecorded") %>');
        speciesData.addRows([
          <%
          List<String> allSpecies=CommonConfiguration.getIndexedPropertyValues("genusSpecies",context); 
          int numSpecies = speciesHashtable.size();
          Enumeration<String> speciesKeys=speciesHashtable.keys();

          while(speciesKeys.hasMoreElements()){
        	  String keyName=speciesKeys.nextElement();
        	  //System.out.println(keyName);
          %>
          ['<%=keyName%>',    <%=speciesHashtable.get(keyName) %>]
		  <%
		  if(speciesKeys.hasMoreElements()){
		  %>
		  ,
		  <%
		  }
         }
		 %>
          
        ]);
     var speciesOptions = {
          width: 450, height: 300,
          title: '<%=encprops.getProperty("speciesChartTitle") %>',
          //colors: ['#0000FF','#FF00FF']
        };
      var speciesChart = new google.visualization.PieChart(document.getElementById('specieschart_div'));
        speciesChart.draw(speciesData, speciesOptions);
      }
      
      
      //countries chart
       google.setOnLoadCallback(drawCountriesChart);
      function drawCountriesChart() {
        var countriesData = new google.visualization.DataTable();
        countriesData.addColumn('string', '<%=encprops.getProperty("country") %>');
        countriesData.addColumn('number', '<%=encprops.getProperty("numberRecorded") %>');
        countriesData.addRows([
          <%
          //List<String> allCountries=myShepherd.getAllCountries(); 
          //int numSpecies = speciesHashtable.size();
          Enumeration<String> countriesKeys=countriesHashtable.keys();

          while(countriesKeys.hasMoreElements()){
        	  String keyName=countriesKeys.nextElement();
        	  //System.out.println(keyName);
          %>
          ['<%=keyName%>',    <%=countriesHashtable.get(keyName) %>]
		  <%
		  if(countriesKeys.hasMoreElements()){
		  %>
		  ,
		  <%
		  }
         }
		 %>
          
        ]);
     var countriesOptions = {
          width: 450, height: 300,
          title: '<%=encprops.getProperty("countryChartTitle") %>',
          //colors: ['#0000FF','#FF00FF']
        };
      var countriesChart = new google.visualization.PieChart(document.getElementById('countrieschart_div'));
        countriesChart.draw(countriesData, countriesOptions);
      }
      
      //users chart
      google.setOnLoadCallback(drawUsersChart);
     function drawUsersChart() {
       var usersData = new google.visualization.DataTable();
       usersData.addColumn('string', '<%=encprops.getProperty("user") %>');
       usersData.addColumn('number', '<%=encprops.getProperty("encountersAssigned") %>');
       usersData.addRows([
         <%
         Enumeration<String> usersKeys=usersHashtable.keys();

         while(usersKeys.hasMoreElements()){
       	  String keyName=usersKeys.nextElement();
       	 %>
         ['<%=keyName%>',    <%=usersHashtable.get(keyName) %>]
		  <%
		  if(usersKeys.hasMoreElements()){
		  %>
		  ,
		  <%
		  }
        }
		 %>
         
       ]);
    var usersOptions = {
         width: 450, height: 300,
         title: '<%=encprops.getProperty("userEncountersTitle") %>',
         
       };
     var usersChart = new google.visualization.PieChart(document.getElementById('userschart_div'));
       usersChart.draw(usersData, usersOptions);
     }
      
      
      //discovery curve
      google.setOnLoadCallback(drawDiscoveryCurve);
     function drawDiscoveryCurve() {
       var discoveryCurveData = new google.visualization.DataTable();
       discoveryCurveData.addColumn('number', '<%=encprops.getProperty("numberEncounters") %>');
       discoveryCurveData.addColumn('number', '<%=encprops.getProperty("numberIndividuals") %>');
       discoveryCurveData.addRows([
         <%
         Enumeration<Integer> discoveryKeys=discoveryCurveInflectionPoints.keys();

         while(discoveryKeys.hasMoreElements()){
       	  Integer keyName=discoveryKeys.nextElement();
       	  //System.out.println(keyName);
         %>
         [<%=keyName.toString()%>,<%=discoveryCurveInflectionPoints.get(keyName).toString() %>]
		  <%
		  if(discoveryKeys.hasMoreElements()){
		  %>
		  ,
		  <%
		  }
        }
		 %>
         
       ]);
    var discoveryCurveOptions = {
         width: 450, height: 300,
         title: '<%=encprops.getProperty("discoveryCurveTitle") %> (n=<%=markedIndividuals.size()%>)',
         hAxis: {title: '<%=encprops.getProperty("discoveryEncounters") %>'},
         vAxis: {title: '<%=encprops.getProperty("numberIndividuals") %>'},
         pointSize: 3,
       };
     var discoveryCurveChart = new google.visualization.ScatterChart(document.getElementById('discoveryCurve_div'));
     discoveryCurveChart.draw(discoveryCurveData, discoveryCurveOptions);
     }
     
   //frequency chart
     google.setOnLoadCallback(drawFrequencyChart);
    function drawFrequencyChart() {
      var frequencyData = new google.visualization.DataTable();
      frequencyData.addColumn('number', '<%=encprops.getProperty("weekNumber") %>');
      frequencyData.addColumn('number', '<%=encprops.getProperty("numberEncounters") %>');
      frequencyData.addRows([
        <%
        //Enumeration<Integer> discoveryKeys=discoveryCurveInflectionPoints.keys();

        for(int q=1;q<=53;q++){
      	  //Integer keyName=discoveryKeys.nextElement();
      	  //System.out.println(keyName);
        %>
        [<%=q%>,<%=frequencyWeeks.get(new Integer(q)).toString() %>]
		  <%
		  if(q<53){
		  %>
		  ,
		  <%
		  }
       }
		 %>
        
      ]);
   var frequencyChartOptions = {
        width: 450, height: 300,
        title: '<%=encprops.getProperty("weeklyTitle") %>',
        hAxis: {title: '<%=encprops.getProperty("weekNumber") %>'},
        vAxis: {title: '<%=encprops.getProperty("numberEncounters") %>'},
      };
    var frequencyChart = new google.visualization.ColumnChart(document.getElementById('frequency_div'));
    frequencyChart.draw(frequencyData, frequencyChartOptions);
    
    
   }
   
    
    //date added chart
    google.setOnLoadCallback(drawYearAddedChart);
   function drawYearAddedChart() {
     var yearAddedData = new google.visualization.DataTable();
     yearAddedData.addColumn('string', '<%=encprops.getProperty("weekNumber") %>');
     yearAddedData.addColumn('number', '<%=encprops.getProperty("numberEncounters") %>');
     yearAddedData.addRows([
       <%
       
       
       
       //let's do some quality control
       int numYears=encountersPerYear.size();
       
       
       //first determine list range
       int minYearAddedValue=999999;
       int maxYearAddedValue=-1;
       Enumeration<Integer> years=encountersPerYear.keys();
       //System.out.println("numYears is:"+numYears);
      
       while(years.hasMoreElements()){
    	   Integer thisYear=years.nextElement();
    	   if(thisYear<minYearAddedValue)minYearAddedValue=thisYear;
    	   if(thisYear>maxYearAddedValue)maxYearAddedValue=thisYear;

       }
       

       
       for(int q=minYearAddedValue;q<=maxYearAddedValue;q++){
     	  if(!encountersPerYear.containsKey(new Integer(q))){encountersPerYear.put(new Integer(q), new Integer(0));}
       		%>
       		['<%=q%>',<%=encountersPerYear.get(new Integer(q)).toString() %>]
		  	<%
		  	if(q<maxYearAddedValue){
		  	%>
		  	,
		  	<%
		  	}
      	}
		 %>
       
     ]);

    var yearAddedChartOptions = {
       width: 450, height: 300,
       title: '<%=encprops.getProperty("encountersByYearTitle") %>',
       hAxis: {title: '<%=encprops.getProperty("year") %>'},
       vAxis: {title: '<%=encprops.getProperty("numberEncounters") %>'},
     };
   var yearAddedChart = new google.visualization.ColumnChart(document.getElementById('yearadded_div'));
   yearAddedChart.draw(yearAddedData, yearAddedChartOptions);
    
   }
   
   
   //total encounters by year chart
   google.setOnLoadCallback(drawYearTotalsChart);
  function drawYearTotalsChart() {
    var yearTotalsData = new google.visualization.DataTable();
    yearTotalsData.addColumn('string', '<%=encprops.getProperty("year") %>');
    yearTotalsData.addColumn('number', '<%=encprops.getProperty("numberEncounters") %>');
    yearTotalsData.addRows([
      <%

      int additionTotal=0;
      for(int q=minYearAddedValue;q<=maxYearAddedValue;q++){
    	  if(!encountersPerYear.containsKey(new Integer(q))){encountersPerYear.put(new Integer(q), new Integer(0));}
	
      		%>
      		['<%=q%>',<%=(encountersPerYear.get(new Integer(q))+additionTotal) %>]
		  	<%
		  	if(q<maxYearAddedValue){
		  	%>
		  	,
		  	<%
		  	}
      		additionTotal+=encountersPerYear.get(new Integer(q));
     	}
		 %>
      
    ]);

   var yearTotalsChartOptions = {
      width: 450, height: 300,
      title: '<%=encprops.getProperty("encounterTotalsTitle") %>',
      hAxis: {title: '<%=encprops.getProperty("year") %>'},
      vAxis: {title: '<%=encprops.getProperty("numberEncounters") %>'},
    };
  var yearTotalsChart = new google.visualization.ColumnChart(document.getElementById('yeartotals_div'));
  yearTotalsChart.draw(yearTotalsData, yearTotalsChartOptions);
   
  }
      
</script>

<% } %>
    

 
<% if (accessible) { %>
<br>
 <p><%=encprops.getProperty("numMatchingEncounters") %> <%=resultSize %></p>
 <ul>
 	<li><%=encprops.getProperty("numberIdentified") %> <%=numIdentified %></li>
 	<li><%=encprops.getProperty("numberMarkedIndividuals") %> <%=markedIndividuals.size() %></li>
 	<li><%=encprops.getProperty("numMediaAssets") %> <%=numPhotos %></li>
 	<li><%=encprops.getProperty("numContributors") %> <%=numContributors %></li>
 </ul>

<p><strong><%=encprops.getProperty("measurements") %></strong></p>
<%
 		//measurement
		
		if(measurementTypes.size()>0){
			for(int b=0;b<numMeasurementTypes;b++){
			%>
				<p><%=encprops.getProperty("mean") %> <%= measurementTypes.get(b).getType()%> 
				<% 
				
				//now report averages
				if(measurementValues[b].getN()>0){
				%>
				&nbsp;<%=df.format(measurementValues[b].getMean()) %>&nbsp;<%=measurementTypes.get(b).getUnits() %> (<%=encprops.getProperty("standardDeviation") %> <%=df.format(measurementValues[b].getStandardDeviation()) %>) N=<%=measurementValues[b].getN() %><br />
				<ul>
					<li><%=encprops.getProperty("meanMales") %> <%=df.format(measurementValuesMales[b].getMean()) %>&nbsp;<%=measurementTypes.get(b).getUnits() %> (<%=encprops.getProperty("standardDeviation") %> <%=df.format(measurementValuesMales[b].getStandardDeviation()) %>) N=<%=measurementValuesMales[b].getN() %></li>
					<li><%=encprops.getProperty("meanFemales") %> <%=df.format(measurementValuesFemales[b].getMean()) %>&nbsp;<%=measurementTypes.get(b).getUnits() %> (<%=encprops.getProperty("standardDeviation") %> <%=df.format(measurementValuesFemales[b].getStandardDeviation()) %>) N=<%=measurementValuesFemales[b].getN() %></li>
				</ul>
				<%
				}
				else{
					%>
					&nbsp;<%=encprops.getProperty("noMeasurement") %>
					<%
				}
				
				%>
				</p>
			<%
			}
		}
		else{
			%>
			<p><%=encprops.getProperty("noMeasurementTypes") %></p>
			<% 
		}
%>
<p><strong><%=encprops.getProperty("biochemicalMeasurements") %></strong></p>
<%
 		//measurement
		
		if(bioMeasurementTypes.size()>0){
			for(int b=0;b<numBioMeasurementTypes;b++){
			%>
				<p><%=encprops.getProperty("mean") %> <%= bioMeasurementTypes.get(b).getType()%> 
				<% 
				
				//now report averages
				if(bioMeasurementValues[b].getN()>0){
				%>
				&nbsp;<%=df.format(bioMeasurementValues[b].getMean()) %>&nbsp;<%=bioMeasurementTypes.get(b).getUnits() %> (<%=encprops.getProperty("standardDeviation") %> <%=df.format(bioMeasurementValues[b].getStandardDeviation()) %>) N=<%=bioMeasurementValues[b].getN() %><br />
				<ul>
					<li><%=encprops.getProperty("meanMales") %> <%=df.format(bioMeasurementValuesMales[b].getMean()) %>&nbsp;<%=bioMeasurementTypes.get(b).getUnits() %> (<%=encprops.getProperty("standardDeviation") %> <%=df.format(bioMeasurementValuesMales[b].getStandardDeviation()) %>) N=<%=bioMeasurementValuesMales[b].getN() %></li>
					<li><%=encprops.getProperty("meanFemales") %> <%=df.format(bioMeasurementValuesFemales[b].getMean()) %>&nbsp;<%=bioMeasurementTypes.get(b).getUnits() %> (<%=encprops.getProperty("standardDeviation") %> <%=df.format(bioMeasurementValuesFemales[b].getStandardDeviation()) %>) N=<%=bioMeasurementValuesFemales[b].getN() %></li>
					</ul>
				<%
				}
				else{
					%>
					&nbsp;<%=encprops.getProperty("noMeasurement") %>
					<%
				}
				
				%>
				</p>
			<%
			}
		}
		else{
			%>
			<p><%=encprops.getProperty("noMeasurementTypes") %></p>
			<% 
		}


     try {
 %>
 
<p><strong><%=encprops.getProperty("charts") %></strong></p>

 <div id="chart_div"></div>

<div id="sexchart_div"></div>

 <%
        if(CommonConfiguration.showProperty("showTaxonomy",context)){
        %>
		<div id="specieschart_div"></div>
		<%
        }
		
        if(CommonConfiguration.showProperty("showCountry",context)){
        %>
		<div id="countrieschart_div"></div>
		<%
        }
		%>
 	<div id="discoveryCurve_div"></div>
 	<div id="frequency_div"></div>
 	<div id="userschart_div"></div>
 	<div id="states_div"></div>
 	<div id="yearadded_div"></div>
 	<div id="yeartotals_div"></div>
 <%
 
     } 
     catch (Exception e) {
       e.printStackTrace();
     }
 



 
 
   myShepherd.rollbackDBTransaction();
   myShepherd.closeDBTransaction();
   rEncounters = null;
 
%>


<% } else {  //no access %>

	<p><%=collabProps.getProperty("functionalityBlockedMessage")%></p>

<% } %>




