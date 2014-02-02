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
         import="java.util.Map,java.util.Iterator,java.util.Set,java.util.TreeMap,java.util.StringTokenizer,org.ecocean.*, org.ecocean.genetics.distance.*,java.util.Properties, java.util.Vector,java.util.ArrayList" %>
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
    props.load(getClass().getResourceAsStream("/bundles/" + langCode + "/individualDistanceSearchResults.properties"));





    int day1 = 1, day2 = 31, month1 = 1, month2 = 12, year1 = 0, year2 = 3000;
    try {
      month1 = (new Integer(request.getParameter("month1"))).intValue();
    } catch (Exception nfe) {
    }
    try {
      month2 = (new Integer(request.getParameter("month2"))).intValue();
    } catch (Exception nfe) {
    }
    try {
      year1 = (new Integer(request.getParameter("year1"))).intValue();
    } catch (Exception nfe) {
    }
    try {
      year2 = (new Integer(request.getParameter("year2"))).intValue();
    } catch (Exception nfe) {
    }


    Shepherd myShepherd = new Shepherd();



    int numResults = 0;


    Vector<MarkedIndividual> rIndividuals = new Vector<MarkedIndividual>();
    myShepherd.beginDBTransaction();
    String order ="";

    MarkedIndividualQueryResult result = IndividualQueryProcessor.processQuery(myShepherd, request, order);
    rIndividuals = result.getResult();
    int numIndividuals=rIndividuals.size();
    
    if((request.getParameter("individualDistanceSearch")!=null)&&(myShepherd.isMarkedIndividual(request.getParameter("individualDistanceSearch")))){
    	MarkedIndividual compareAgainst=myShepherd.getMarkedIndividual(request.getParameter("individualDistanceSearch"));
    	if(rIndividuals.contains(compareAgainst)){rIndividuals.remove(compareAgainst);numIndividuals--;}
    }
    
    ArrayList<String> loci=myShepherd.getAllLoci();
    int numLoci=loci.size();
    String[] theLoci=new String[numLoci];
    for(int q=0;q<numLoci;q++){
    	theLoci[q]=loci.get(q);
    }
    
    
    //ArrayList<String> indieNames=new ArrayList<String>();
    String[] indieNames=new String[numIndividuals+1];
    if(request.getParameter("individualDistanceSearch")!=null){
    	String individualDistanceSearchID=request.getParameter("individualDistanceSearch");
    	indieNames[0]=individualDistanceSearchID;
    }
    
    for(int i=0;i<numIndividuals;i++){
    	String indieName=rIndividuals.get(i).getIndividualID();
    	indieNames[i+1]=indieName;
    }
    
    String individualDistanceSearchID="";
    
    if(request.getParameter("individualDistanceSearch")!=null){
    	individualDistanceSearchID=request.getParameter("individualDistanceSearch");
    	//indieNames.add(0,individualDistanceSearchID);
    }
    
    //String[] myNames=(String[])indieNames.toArray();
    //String[] myLoci=(String[])loci.toArray();
    String distanceOutput=ShareDst.getDistanceOuput(indieNames, theLoci,false, false,"\n"," ");



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
</head>

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
      <br/>

      <h1 class="intro"><span class="para"><img src="images/wild-me-logo-only-100-100.png" width="35"
                                                align="absmiddle"/>
        <%=props.getProperty("title")%> <a href="individuals.jsp?number=<%=individualDistanceSearchID %>"><%=individualDistanceSearchID %></a>
      </h1>

    </td>
  </tr>
</table>

<%
TreeMap<String,String> returnedValues=new TreeMap<String,String>();

StringTokenizer str=new StringTokenizer(distanceOutput,"\n");
int numLines=str.countTokens();

for(int f=0;f<numLines;f++){
	String line=str.nextToken();
	StringTokenizer thisEntry=new StringTokenizer(line, " ");
	returnedValues.put(thisEntry.nextToken(), thisEntry.nextToken());
}

Map myMap=MyFuns.sortMapByDoubleValue(returnedValues);

//now do something
%>
<table id="results">
<tr class="lineitem"><th class="lineitem"  bgcolor="#99CCFF">Marked Individual</th><th class="lineitem"  bgcolor="#99CCFF">Distance</th></tr>
<%

Set<String> keys=myMap.keySet();
Iterator keyIter=keys.iterator();
while(keyIter.hasNext()){
	String individualID=(String)keyIter.next();
	String value=(String)myMap.get(individualID);
	%>
	<tr class="lineitem"><td class="lineitem" ><a href="individuals.jsp?number=<%=individualID %>"><%=individualID %></a></td><td class="lineitem"><%=value %></td></tr>
	<%
}

%>

</table>
<%

  myShepherd.rollbackDBTransaction();
myShepherd.closeDBTransaction();
myShepherd=null;


%>


<p>

<%
  if (request.getParameter("noQuery") == null) {
%>
<table>
  <tr>
    <td align="left">

      <p><strong><%=props.getProperty("queryDetails")%>
      </strong></p>

      <p class="caption"><strong><%=props.getProperty("prettyPrintResults") %>
      </strong><br/>
        <%=result.getQueryPrettyPrint().replaceAll("locationField", props.getProperty("location")).replaceAll("locationCodeField", props.getProperty("locationID")).replaceAll("verbatimEventDateField", props.getProperty("verbatimEventDate")).replaceAll("Sex", props.getProperty("sex")).replaceAll("Keywords", props.getProperty("keywords")).replaceAll("alternateIDField", (props.getProperty("alternateID"))).replaceAll("alternateIDField", (props.getProperty("size")))%>
      </p>

      <p class="caption"><strong><%=props.getProperty("jdoql")%>
      </strong><br/>
        <%=result.getJDOQLRepresentation()%>
      </p>

    </td>
  </tr>
</table>
<%
  }
%>
</p>



<p></p>
<jsp:include page="footer.jsp" flush="true"/>
</div>
</div>
<!-- end page --></div>
<!--end wrapper -->
</body>
</html>


