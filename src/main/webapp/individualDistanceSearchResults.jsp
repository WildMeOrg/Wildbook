<%--
  ~ The Shepherd Project - A Mark-Recapture Framework
  ~ Copyright (C) 2014 Jason Holmberg
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
         import="org.ecocean.servlet.ServletUtilities,java.text.DecimalFormat,java.util.Map,java.util.Iterator,java.util.Set,java.util.TreeMap,java.util.StringTokenizer,org.ecocean.*, org.ecocean.genetics.distance.*,java.util.Properties, java.util.Vector,java.util.ArrayList" %>
<%@ taglib uri="http://www.sunwesttek.com/di" prefix="di" %>


<html>
<head>
  <%

  String context="context0";
  context=ServletUtilities.getContext(request);
    //let's load out properties
    Properties props = new Properties();
    //String langCode = "en";
    String langCode=ServletUtilities.getLanguageCode(request);
    
    //props.load(getClass().getResourceAsStream("/bundles/" + langCode + "/individualDistanceSearchResults.properties"));
    props = ShepherdProperties.getProperties("individualDistanceSearchResults.properties", langCode);





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


    Shepherd myShepherd = new Shepherd(context);



    int numResults = 0;

	MarkedIndividual compareAgainst=new MarkedIndividual();
    Vector<MarkedIndividual> rIndividuals = new Vector<MarkedIndividual>();
    myShepherd.beginDBTransaction();
    String order ="";

    MarkedIndividualQueryResult result = IndividualQueryProcessor.processQuery(myShepherd, request, order);
    rIndividuals = result.getResult();
    int numIndividuals=rIndividuals.size();
    
    String individualDistanceSearchID="";

    
    if((request.getParameter("individualDistanceSearch")!=null)&&(myShepherd.isMarkedIndividual(request.getParameter("individualDistanceSearch")))){
    	compareAgainst=myShepherd.getMarkedIndividual(request.getParameter("individualDistanceSearch"));
    	if(rIndividuals.contains(compareAgainst)){rIndividuals.remove(compareAgainst);numIndividuals--;}
    	individualDistanceSearchID=request.getParameter("individualDistanceSearch");
    	    
    }
    else if((request.getParameter("encounterNumber")!=null)&&(myShepherd.isEncounter(request.getParameter("encounterNumber")))){
    	Encounter enc=myShepherd.getEncounter(request.getParameter("encounterNumber"));
    	
    	if((enc.getIndividualID()!=null)&&(!enc.getIndividualID().toLowerCase().equals("unassigned"))&&(myShepherd.isMarkedIndividual(enc.getIndividualID()))){
    		compareAgainst=myShepherd.getMarkedIndividual(enc.getIndividualID());
    		if(rIndividuals.contains(compareAgainst)){rIndividuals.remove(compareAgainst);numIndividuals--;}
    		individualDistanceSearchID=compareAgainst.getIndividualID();
    	}
    	
    	/**
    	//DOES NOT WORK YET
    	else{
    		compareAgainst.setIndividualID("Unknown");
    		myShepherd.getPM().makePersistent(compareAgainst);
    		compareAgainst.addEncounter(enc);
    		enc.setIndividualID("Unknown");
    		myShepherd.commitDBTransaction();
    		myShepherd.beginDBTransaction();
    		individualDistanceSearchID="Unknown";
    	}
    	**/
    	
    	
    }
    	   
    
    ArrayList<String> loci=myShepherd.getAllLoci();
    int numLoci=loci.size();
    String[] theLoci=new String[numLoci];
    for(int q=0;q<numLoci;q++){
    	theLoci[q]=loci.get(q);
    }
    
    String compareAgainstAllelesString=compareAgainst.getFomattedMSMarkersString(theLoci);
    
    
    //ArrayList<String> indieNames=new ArrayList<String>();
    String[] indieNames=new String[numIndividuals+1];
  //String individualDistanceSearchID=request.getParameter("individualDistanceSearch");
    indieNames[0]=individualDistanceSearchID;
  
    
    for(int i=0;i<numIndividuals;i++){
    	String indieName=rIndividuals.get(i).getIndividualID();
    	indieNames[i+1]=indieName;
    }
    

    //String[] myNames=(String[])indieNames.toArray();
    //String[] myLoci=(String[])loci.toArray();
    String distanceOutput=ShareDst.getDistanceOuput(indieNames, theLoci,false, false,"\n"," ",context);

    /**
    //DOES NOT WORK YET
    if(individualDistanceSearchID.equals("Unknown")){
    	MarkedIndividual unknown=myShepherd.getMarkedIndividual("Unknown");
    	unknown.removeEncounter(unknown.getEncounter(0));
    	myShepherd.throwAwayMarkedIndividual(unknown);
    	myShepherd.commitDBTransaction();
    	myShepherd.beginDBTransaction();
    }
**/

  %>
  <title><%=CommonConfiguration.getHTMLTitle(context) %>
  </title>
  <meta http-equiv="Content-Type" content="text/html; charset=utf-8"/>
  <meta name="Description"
        content="<%=CommonConfiguration.getHTMLDescription(context) %>"/>
  <meta name="Keywords"
        content="<%=CommonConfiguration.getHTMLKeywords(context) %>"/>
  <meta name="Author" content="<%=CommonConfiguration.getHTMLAuthor(context) %>"/>
  <link href="<%=CommonConfiguration.getCSSURLLocation(request,context) %>"
        rel="stylesheet" type="text/css"/>
  <link rel="shortcut icon"
        href="<%=CommonConfiguration.getHTMLShortcutIcon(context) %>"/>

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
<p>Reference Individual ID: <%=compareAgainst.getIndividualID() %>
<%
String compareAgainstHaplotype="";
if(compareAgainst.getHaplotype()!=null){
	compareAgainstHaplotype=compareAgainst.getHaplotype();
}
String compareAgainstGeneticSex="";
if(compareAgainst.getGeneticSex()!=null){
	compareAgainstGeneticSex=compareAgainst.getGeneticSex();
}
%>
<br/>Haplotype: <%=compareAgainstHaplotype %>
<br/>Genetic sex: <%=compareAgainstGeneticSex %>
<br /><span style="color: #909090">Microsatellite marks for this individual are shown below in gray for comparison.</span>
</p>

<%
TreeMap<String,String> returnedValues=new TreeMap<String,String>();

StringTokenizer str=new StringTokenizer(distanceOutput,"\n");
int numLines=str.countTokens();

for(int f=0;f<numLines;f++){
	String line=str.nextToken();
	StringTokenizer thisEntry=new StringTokenizer(line, " ");
	if(f>0)returnedValues.put(thisEntry.nextToken(), thisEntry.nextToken());
}

Map myMap=MyFuns.sortMapByDoubleValue(returnedValues);

//now do something
%>

<p>Potential Matches: <%=(indieNames.length-1) %> individuals</p>

<table id="results">
<tr class="lineitem">
	<th class="lineitem"  bgcolor="#99CCFF">Individual</th>
	<th class="lineitem"  bgcolor="#99CCFF">Gen. Distance</th>
	<th class="lineitem"  bgcolor="#99CCFF">No. Co-occur.</th>
	<th class="lineitem"  bgcolor="#99CCFF">Min. dist. (km)</th>
	<th class="lineitem"  bgcolor="#99CCFF">Haplo.</th>
	<th class="lineitem"  bgcolor="#99CCFF">Gen. Sex</th>
	<th class="lineitem"  bgcolor="#99CCFF">Microsatellite Markers</th>
</tr>

<%

Set<String> keys=myMap.keySet();
Iterator keyIter=keys.iterator();
while(keyIter.hasNext()){
	String individualID=(String)keyIter.next();
	MarkedIndividual thisIndie=myShepherd.getMarkedIndividual(individualID);
	String value=(String)myMap.get(individualID);
	double val=(new Double(value)).doubleValue();
	DecimalFormat df = new DecimalFormat("######.##");
	//if(val<0.714){
	%>
	<tr class="lineitem">
		<td class="lineitem" ><a href="individuals.jsp?number=<%=individualID %>"><%=individualID %></a></td>
		<td class="lineitem"><%=value %></td>
		
		<td class="lineitem"><%=myShepherd.getNumCooccurrencesBetweenTwoMarkedIndividual(individualID, compareAgainst.getIndividualID()) %></td>
		<%
		String minDistance="&nbsp;";
		Float calcMin=compareAgainst.getMinDistanceBetweenTwoMarkedIndividuals(thisIndie);
		if(calcMin>=0){minDistance=df.format((new Float(calcMin/1000)).longValue());}
		%>
		<td class="lineitem"><a target="_blank" href="individualMappedSearchResults.jsp?individualID=<%=individualID%>&individualID=<%=compareAgainst.getIndividualID()%>"><%=minDistance %></a></td>
		<%
		String thisHaplo="&nbsp;";
		if(thisIndie.getHaplotype()!=null){thisHaplo=thisIndie.getHaplotype();}
		%>
		<td class="lineitem"><%=thisHaplo %></td>
		<td class="lineitem"><%=thisIndie.getGeneticSex() %></td>
		<td class="lineitem">
			<table>
				<tr>
						<%
		for(int y=0;y<numLoci;y++){
			%>
			<td><span style="font-style: italic"><%=theLoci[y] %></span></td><td><span style="font-style: italic"><%=theLoci[y] %></span></td>
			<%
		}
		%>
				</tr>
				<tr>
					<td><%=thisIndie.getFomattedMSMarkersString(theLoci).replaceAll(" ", "</td><td>") %></td>
				</tr>
				
				<tr>
					<td><span style="color: #909090"><%=compareAgainstAllelesString.replaceAll(" ", "</span></td><td><span style=\"color: #909090\">") %></span></td>
				</tr>
				
			</table>
		
		</td>
		
	</tr>
	<%
	//}
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


