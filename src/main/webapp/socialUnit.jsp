<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.servlet.ServletUtilities,org.ecocean.*, java.util.*,org.ecocean.social.*" %>


  <%
  String context="context0";
  context=ServletUtilities.getContext(request);

    //let's load out properties
    Properties props = new Properties();
    //String langCode = "en";
    String langCode=ServletUtilities.getLanguageCode(request);
    
    //props.load(getClass().getResourceAsStream("/bundles/" + langCode + "/socialunit.properties"));
    props = ShepherdProperties.getProperties("socialunit.properties", langCode,context);


	if(request.getParameter("name")!=null){

	    Shepherd myShepherd = new Shepherd(context);
	    myShepherd.setAction("socialUnit.jsp");
	
	    
	    SocialUnit su=myShepherd.getSocialUnit(request.getParameter("name"));
	    if(su!=null){
		  
		    int numResults = 0;
		
		
		    List<MarkedIndividual> rIndividuals = su.getMarkedIndividuals();
		    myShepherd.beginDBTransaction();
		    try{
			    String order ="";

			
			  %>
			 
			<style type="text/css">
			  #tabmenu {
			    color: #000;
			    border-bottom: 2px solid black;
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
			    color: #DEDECF;
			    background: #000;
			    font: bold 1em "Trebuchet MS", Arial, sans-serif;
			    border: 2px solid black;
			    padding: 2px 5px 0px 5px;
			    margin: 0;
			    text-decoration: none;
			    border-bottom: 0px solid #FFFFFF;
			  }
			
			  #tabmenu a.active {
			    background: #FFFFFF;
			    color: #000000;
			    border-bottom: 2px solid #FFFFFF;
			  }
			
			  #tabmenu a:hover {
			    color: #ffffff;
			    background: #7484ad;
			  }
			
			  #tabmenu a:visited {
			    color: #E8E9BE;
			  }
			
			  #tabmenu a.active:hover {
			    background: #7484ad;
			    color: #DEDECF;
			    border-bottom: 2px solid #000000;
			  }
			</style>
			
			
			<jsp:include page="header.jsp" flush="true" />
			
			   <div class="container maincontent">
			
			<h1><img align="absmiddle" src="images/occurrence.png" />&nbsp;<%=props.getProperty("community") %> <%=request.getParameter("name")%></h1>
			<p class="caption"><em><%=props.getProperty("description") %></em></p>
			
			  
			
			
			<table width="100%" id="results">
			  <tr class="lineitem">
			    <td class="lineitem" bgcolor="#99CCFF"></td>
			    <td class="lineitem" align="left" valign="top" bgcolor="#99CCFF">
			      <strong><%=props.getProperty("markedIndividual")%>
			      </strong></td>
			    <td class="lineitem" align="left" valign="top" bgcolor="#99CCFF">
			      <strong><%=props.getProperty("numEncounters")%>
			      </strong></td>
			    <td class="lineitem" align="left" valign="top" bgcolor="#99CCFF">
			      <strong><%=props.getProperty("maxYearsBetweenResights")%>
			      </strong></td>
			    <td class="lineitem" align="left" valign="top" bgcolor="#99CCFF">
			      <strong><%=props.getProperty("sex")%>
			      </strong></td>
			    <td class="lineitem" align="left" valign="top" bgcolor="#99CCFF">
			      <strong><%=props.getProperty("numLocationsSighted")%>
			      </strong></td>
			
			  </tr>
			
			  <%
			
			    //set up the statistics counters
			    
			
			    Vector histories = new Vector();
			    int rIndividualsSize=rIndividuals.size();
			    
			    int count = 0;
			    int numNewlyMarked = 0;
			    
			    for (int f = 0; f < rIndividualsSize; f++) {
			     
			      count++;
			
			
			        MarkedIndividual indie = (MarkedIndividual) rIndividuals.get(f);
			        //check if this individual was newly marked in this period
			        Encounter[] dateSortedEncs = indie.getDateSortedEncounters();
			        int sortedLength = dateSortedEncs.length - 1;
			        Encounter temp = dateSortedEncs[sortedLength];
			        ArrayList<SinglePhotoVideo> photos=indie.getAllSinglePhotoVideo();
			        
			  %>
			  <tr class="lineitem">
			    <td class="lineitem" width="102" bgcolor="#FFFFFF" >
			    
			       							<%
			   								if(photos.size()>0){ 
			   									SinglePhotoVideo myPhoto=photos.get(0);
			   									String imgName = "/"+CommonConfiguration.getDataDirectoryName(context)+"/encounters/" + myPhoto.getCorrespondingEncounterNumber() + "/thumb.jpg";
			   			                       
			   								%>                         
			                            		<a href="individuals.jsp?number=<%=indie.getIndividualID()%>"><img src="<%=imgName%>" alt="<%=indie.getName()%>" border="0"/></a>
			                            	<%
			   								}
			   								else{
			   								%>
			   									&nbsp;	
			                            	<%
			   								}
			                            	%>
			      </td>
			    <td class="lineitem"><a
			      href="//<%=CommonConfiguration.getURLLocation(request)%>/individuals.jsp?number=<%=indie.getIndividualID()%>"><%=indie.getName()%>
			    </a>
			      <%
		
			      if(temp.getYear()>0){
			      %>
			      <br /><font size="-1"><%=props.getProperty("firstIdentified")%>: <%=temp.getMonth() %>
			        /<%=temp.getYear() %>
			      </font>
			      <%
			      }
			      if(CommonConfiguration.showProperty("showTaxonomy",context)){
			      	if(indie.getGenusSpecies()!=null){
			      %>
			      	<br /><em><font size="-1"><%=indie.getGenusSpecies()%></font></em>
			      <%
			      	}
			      }
			      %>
			
			    </td>
			    <td class="lineitem"><%=indie.totalEncounters()%>
			    </td>
			
			    <td class="lineitem"><%=indie.getMaxNumYearsBetweenSightings()%>
			    </td>
			
			<%
			String sexValue="&nbsp;";
			if(indie.getSex()!=null){sexValue=indie.getSex();}
			%>
			    <td class="lineitem"><%=sexValue %></td>
			
			
			    <td class="lineitem"><%=indie.participatesInTheseLocationIDs().size()%>
			    </td>
			  </tr>
			  <%
			  
			    } //end for
			    boolean includeZeroYears = true;
			
			    boolean subsampleMonths = false;
			    if (request.getParameter("subsampleMonths") != null) {
			      subsampleMonths = true;
			    }
			    numResults = count;
			  %>
			</table>
			
			
			<%
		    }
		    catch(Exception e){
		    	e.printStackTrace();
		    }
		    
	    }
	    else{
	    	
	    	//couldn't find that social unit
	    	
	    }
	  myShepherd.rollbackDBTransaction();
	  myShepherd.closeDBTransaction();
	}
	else{
		
		//no social unit specified with the ?name= attribute
		
		
	}
  

%>


</div>

<jsp:include page="footer.jsp" flush="true"/>



