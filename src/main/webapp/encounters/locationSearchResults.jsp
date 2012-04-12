<%--
  ~ The Shepherd Project - A Mark-Recapture Framework
  ~ Copyright (C) 2011 Jason Holmberg
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
         import="org.springframework.mock.web.MockHttpServletRequest,java.util.Vector,java.util.Properties,org.ecocean.genetics.*,java.util.*,java.net.URI, org.ecocean.*" %>



<html>
<head>



  <%


    //let's load encounterSearch.properties
    String langCode = "en";
    if (session.getAttribute("langCode") != null) {
      langCode = (String) session.getAttribute("langCode");
    }
    Properties encprops = new Properties();
    encprops.load(getClass().getResourceAsStream("/bundles/" + langCode + "/locationSearchResults.properties"));

    Properties haploprops = new Properties();
    haploprops.load(getClass().getResourceAsStream("/bundles/haplotypeColorCodes.properties"));

    
    //get our Shepherd
    Shepherd myShepherd = new Shepherd();






    int numResults = 0;

    //set up the vector for matching encounters
    Vector rEncounters1 = new Vector();
    Vector rEncounters2 = new Vector();

    //kick off the transaction
    myShepherd.beginDBTransaction();

    //start the query and get the results
    String order = "";
    //EncounterQueryResult queryResult1 = EncounterQueryProcessor.processQuery(myShepherd, request, order);
    EncounterQueryResult queryResult1 = EncounterQueryProcessor.processQuery(myShepherd, (MockHttpServletRequest)session.getAttribute("locationSearch1"), order);
    //System.out.println(((MockHttpServletRequest)session.getAttribute("locationSearch1")).getQueryString());
    rEncounters1 = queryResult1.getResult();
    EncounterQueryResult queryResult2 = EncounterQueryProcessor.processQuery(myShepherd, request, order);
    rEncounters2 = queryResult2.getResult();
    
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
 	
 	
 	int resultSize1=rEncounters1.size();
 	int resultSize2=rEncounters2.size();
 	
	//more results1 analysis 	
 	 ArrayList<String> markedIndividuals1=new ArrayList<String>();
 	 for(int y=0;y<resultSize1;y++){
 		 Encounter thisEnc=(Encounter)rEncounters1.get(y);
 		 if((!thisEnc.equals("Unassigned"))&&(!markedIndividuals1.contains(thisEnc.getIndividualID().trim()))){markedIndividuals1.add(thisEnc.getIndividualID().trim());}
 		 //haplotype ie chart prep
 		 if(thisEnc.getHaplotype()!=null){
      	   if(pieHashtable1.containsKey(thisEnc.getHaplotype().trim())){
      		   Integer thisInt = pieHashtable1.get(thisEnc.getHaplotype().trim())+1;
      		   pieHashtable1.put(thisEnc.getHaplotype().trim(), thisInt);
      	   }
 	 	}
 		 
 	    //sex pie chart 	 
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
 	 //end results analysis 1
 	 
 	 //more results2 analysis 	
 	 ArrayList<String> markedIndividuals2=new ArrayList<String>();
 	 for(int y=0;y<resultSize2;y++){
 		 Encounter thisEnc=(Encounter)rEncounters2.get(y);
 		 if((!thisEnc.equals("Unassigned"))&&(!markedIndividuals2.contains(thisEnc.getIndividualID().trim()))){markedIndividuals2.add(thisEnc.getIndividualID().trim());}
 		 //haplotype ie chart prep
 		 if(thisEnc.getHaplotype()!=null){
      	   if(pieHashtable2.containsKey(thisEnc.getHaplotype().trim())){
      		   Integer thisInt = pieHashtable2.get(thisEnc.getHaplotype().trim())+1;
      		   pieHashtable2.put(thisEnc.getHaplotype().trim(), thisInt);
      	   }
 	 	}
 		 
 	    //sex pie chart 	 
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
 	 //end results analysis 2
 	 
 
 	 
  %>

  <title><%=CommonConfiguration.getHTMLTitle()%>
  </title>
  <meta http-equiv="Content-Type" content="text/html; charset=utf-8"/>
  <meta name="Description" content="<%=CommonConfiguration.getHTMLDescription()%>"/>
  <meta name="Keywords" content="<%=CommonConfiguration.getHTMLKeywords()%>"/>
  <meta name="Author" content="<%=CommonConfiguration.getHTMLAuthor()%>"/>
  <link href="<%=CommonConfiguration.getCSSURLLocation(request)%>" rel="stylesheet" type="text/css"/>
  <link rel="shortcut icon" href="<%=CommonConfiguration.getHTMLShortcutIcon()%>"/>


    <style type="text/css">
      body {
        margin: 0;
        padding: 10px 20px 20px;
        font-family: Arial;
        font-size: 16px;
      }



      #map {
        width: 600px;
        height: 400px;
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
      google.load("visualization", "1", {packages:["corechart"]});
      google.setOnLoadCallback(drawHaploChart1);
      function drawHaploChart1() {
        var data = new google.visualization.DataTable();
        data.addColumn('string', 'Haplotype');
        data.addColumn('number', 'No. Recorded');
        data.addRows([
          <%
          ArrayList<String> allHaplos1=myShepherd.getAllHaplotypes(); 
          int numHaplos1 = allHaplos1.size();
          

          
          for(int hh=0;hh<numHaplos1;hh++){
          %>
          ['<%=allHaplos1.get(hh)%>',    <%=pieHashtable1.get(allHaplos1.get(hh))%>],
		  <%
          }
		  %>
          
        ]);

        var options = {
          width: 450, height: 300,
          title: 'Haplotypes in Matched Encounters',
          colors: [
                   <%
                   String haploColor="CC0000";
                   if((encprops.getProperty("defaultMarkerColor")!=null)&&(!encprops.getProperty("defaultMarkerColor").trim().equals(""))){
                	   haploColor=encprops.getProperty("defaultMarkerColor");
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
        data.addColumn('string', 'Sex');
        data.addColumn('number', 'No. Recorded');
        data.addRows([

          ['male',    <%=sexHashtable1.get("male")%>],
           ['female',    <%=sexHashtable1.get("female")%>],
           ['unknown',    <%=sexHashtable1.get("unknown")%>],
          
        ]);

        <%
        haploColor="CC0000";
        if((encprops.getProperty("defaultMarkerColor")!=null)&&(!encprops.getProperty("defaultMarkerColor").trim().equals(""))){
     	   haploColor=encprops.getProperty("defaultMarkerColor");
        }
        
        %>
        var options = {
          width: 450, height: 300,
          title: 'Sex Distribution in Matched Encounters',
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
        data.addColumn('string', 'Haplotype');
        data.addColumn('number', 'No. Recorded');
        data.addRows([
          <%
          ArrayList<String> allHaplos2a=myShepherd.getAllHaplotypes(); 
          int numHaplos2a = allHaplos2a.size();
          

          
          for(int hh=0;hh<numHaplos2a;hh++){
          %>
          ['<%=allHaplos2a.get(hh)%>',    <%=pieHashtable2.get(allHaplos2a.get(hh))%>],
		  <%
          }
		  %>
          
        ]);

        var options = {
          width: 450, height: 300,
          title: 'Haplotypes in Matched Encounters',
          colors: [
                   <%
                   haploColor="CC0000";
                   if((encprops.getProperty("defaultMarkerColor")!=null)&&(!encprops.getProperty("defaultMarkerColor").trim().equals(""))){
                	   haploColor=encprops.getProperty("defaultMarkerColor");
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
        data.addColumn('string', 'Sex');
        data.addColumn('number', 'No. Recorded');
        data.addRows([

          ['male',    <%=sexHashtable2.get("male")%>],
           ['female',    <%=sexHashtable2.get("female")%>],
           ['unknown',    <%=sexHashtable2.get("unknown")%>],
          
        ]);

        <%
        haploColor="CC0000";
        if((encprops.getProperty("defaultMarkerColor")!=null)&&(!encprops.getProperty("defaultMarkerColor").trim().equals(""))){
     	   haploColor=encprops.getProperty("defaultMarkerColor");
        }
        
        %>
        var options = {
          width: 450, height: 300,
          title: 'Sex Distribution in Matched Encounters',
          colors: ['#0000FF','#FF00FF','<%=haploColor%>']
        };

        var chart2 = new google.visualization.PieChart(document.getElementById('sexchart_div2'));
        chart2.draw(data, options);
      }
      
      
</script>



    
  </head>
 <body onunload="GUnload()">
 <div id="wrapper">
 <div id="page">
<jsp:include page="../header.jsp" flush="true">

  <jsp:param name="isAdmin" value="<%=request.isUserInRole(\"admin\")%>" />
</jsp:include>
 <div id="main">
 
 <table width="810px" border="0" cellspacing="0" cellpadding="0">
   <tr>
     <td>
       <br/>
 
       <h1 class="intro"><%=encprops.getProperty("title")%>
       </h1>
     </td>
   </tr>
</table>


<%


 //test comment

     try {
 %>
 
<table>
<tr><th><%=encprops.getProperty("search1Results") %></th><th><%=encprops.getProperty("search2Results") %></th></tr>
<tr>
<td>
 <div id="chart_div1"></div>
<div id="sexchart_div1"></div>
<div>
      <p><strong><%=encprops.getProperty("queryDetails")%>
      </strong></p>

      <p class="caption"><strong><%=encprops.getProperty("prettyPrintResults") %>
      </strong><br/>
        <%=queryResult1.getQueryPrettyPrint().replaceAll("locationField", encprops.getProperty("location")).replaceAll("locationCodeField", encprops.getProperty("locationID")).replaceAll("verbatimEventDateField", encprops.getProperty("verbatimEventDate")).replaceAll("alternateIDField", encprops.getProperty("alternateID")).replaceAll("behaviorField", encprops.getProperty("behavior")).replaceAll("Sex", encprops.getProperty("sex")).replaceAll("nameField", encprops.getProperty("nameField")).replaceAll("selectLength", encprops.getProperty("selectLength")).replaceAll("numResights", encprops.getProperty("numResights")).replaceAll("vesselField", encprops.getProperty("vesselField"))%>
      </p>

      <p class="caption"><strong><%=encprops.getProperty("jdoql")%>
      </strong><br/>
        <%=queryResult1.getJDOQLRepresentation()%>
      </p>
</div>
</td>

<td>
 <div id="chart_div2"></div>
<div id="sexchart_div2"></div>
<div>

      <p><strong><%=encprops.getProperty("queryDetails")%>
      </strong></p>

      <p class="caption"><strong><%=encprops.getProperty("prettyPrintResults") %>
      </strong><br/>
        <%=queryResult2.getQueryPrettyPrint().replaceAll("locationField", encprops.getProperty("location")).replaceAll("locationCodeField", encprops.getProperty("locationID")).replaceAll("verbatimEventDateField", encprops.getProperty("verbatimEventDate")).replaceAll("alternateIDField", encprops.getProperty("alternateID")).replaceAll("behaviorField", encprops.getProperty("behavior")).replaceAll("Sex", encprops.getProperty("sex")).replaceAll("nameField", encprops.getProperty("nameField")).replaceAll("selectLength", encprops.getProperty("selectLength")).replaceAll("numResights", encprops.getProperty("numResights")).replaceAll("vesselField", encprops.getProperty("vesselField"))%>
      </p>

      <p class="caption"><strong><%=encprops.getProperty("jdoql")%>
      </strong><br/>
        <%=queryResult2.getJDOQLRepresentation()%>
      </p>
</div>
 </td>
 </tr>
 
 </table>
 
 <%
 
     } 
     catch (Exception e) {
       e.printStackTrace();
     }
 



 
 
   myShepherd.rollbackDBTransaction();
   myShepherd.closeDBTransaction();
   rEncounters1 = null;
   rEncounters2 = null;
 
%>

 
 <jsp:include page="../footer.jsp" flush="true"/>
</div>
</div>
<!-- end page --></div>
<!--end wrapper -->

</body>
</html>
