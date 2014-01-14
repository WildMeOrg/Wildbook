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

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.*, java.util.Properties, java.util.Vector,java.util.ArrayList" %>
<%@ taglib uri="http://www.sunwesttek.com/di" prefix="di" %>


<html>
<head>
  <%

    //let's load out properties
    Properties props = new Properties();
    String langCode = "en";
    if (session.getAttribute("langCode") != null) {
      langCode = (String) session.getAttribute("langCode");
    }
    props.load(getClass().getResourceAsStream("/bundles/" + langCode + "/community.properties"));



    Shepherd myShepherd = new Shepherd();



    int numResults = 0;


    ArrayList<MarkedIndividual> rIndividuals = new ArrayList<MarkedIndividual>();
    myShepherd.beginDBTransaction();
    String order ="";

    //MarkedIndividualQueryResult result = IndividualQueryProcessor.processQuery(myShepherd, request, order);
    rIndividuals = myShepherd.getAllMarkedIndividualsInCommunity(request.getParameter("name"));


  %>
  <title><%=CommonConfiguration.getHTMLTitle() %>
  </title>
  <meta http-equiv="Content-Type" content="text/html; charset=utf-8"/>
  <meta name="Description"
        content="<%=CommonConfiguration.getHTMLDescription() %>"/>
  <meta name="Keywords"
        content="<%=CommonConfiguration.getHTMLKeywords() %>"/>
  <meta name="Author" content="<%=CommonConfiguration.getHTMLAuthor() %>"/>
  <link href="<%=CommonConfiguration.getCSSURLLocation(request) %>"
        rel="stylesheet" type="text/css"/>
  <link rel="shortcut icon"
        href="<%=CommonConfiguration.getHTMLShortcutIcon() %>"/>

</head>
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
<body>
<div id="wrapper">
<div id="page">
<jsp:include page="header.jsp" flush="true">

  <jsp:param name="isAdmin" value="<%=request.isUserInRole(\"admin\")%>" />
</jsp:include>
<div id="main">

<table width="810" border="0" cellspacing="0" cellpadding="0">
  <tr>
    <td>

       <h1><strong><img align="absmiddle" src="images/occurrence.png" />&nbsp;<%=props.getProperty("community") %></strong>: <%=request.getParameter("name")%></h1>
<p class="caption"><em><%=props.getProperty("description") %></em></p>

    </td>
  </tr>
</table>


<table width="810" id="results">
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
   									String imgName = "/"+CommonConfiguration.getDataDirectoryName()+"/encounters/" + myPhoto.getCorrespondingEncounterNumber() + "/thumb.jpg";
   			                       
   								%>                         
                            		<a href="individuals.jsp?number=<%=indie.getName()%>"><img src="<%=imgName%>" alt="<%=indie.getName()%>" border="0"/></a>
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
      href="http://<%=CommonConfiguration.getURLLocation(request)%>/individuals.jsp?number=<%=indie.getName()%>"><%=indie.getName()%>
    </a>
      <%
        if ((indie.getAlternateID() != null) && (!indie.getAlternateID().equals("None")) && (!indie.getAlternateID().equals(""))) {
      %> <br /><font size="-1"><%=props.getProperty("alternateID")%>: <%=indie.getAlternateID()%>
      </font> <%
        }
      if(temp.getYear()>0){
      %>
      <br /><font size="-1"><%=props.getProperty("firstIdentified")%>: <%=temp.getMonth() %>
        /<%=temp.getYear() %>
      </font>
      <%
      }
      if(CommonConfiguration.showProperty("showTaxonomy")){
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

    <td class="lineitem"><%=indie.getSex()%>
    </td>

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
  myShepherd.rollbackDBTransaction();

%>





<p></p>
<jsp:include page="footer.jsp" flush="true"/>
</div>
</div>
<!-- end page --></div>
<!--end wrapper -->
</body>
</html>


