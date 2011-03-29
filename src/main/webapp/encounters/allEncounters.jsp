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
<%@ page contentType="text/html; charset=utf-8" language="java" import="java.util.Properties, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>

<%

//setup our Properties object to hold all properties
	Properties props=new Properties();
	String langCode="en";
	
	//check what language is requested
	if(session.getAttribute("langCode")!=null){langCode=(String)session.getAttribute("langCode");}

	//set up the props file input stream
	props.load(getClass().getResourceAsStream("/bundles/"+langCode+"/allEncounters.properties"));
	
	
	//load our variables for the page
	String see_all_sharks=props.getProperty("see_all_sharks");
	String encounter=props.getProperty("encounter");
	String shark=props.getProperty("shark");
	String records=props.getProperty("records");
	String next=props.getProperty("next");
	String last=props.getProperty("last");
	String previous=props.getProperty("previous");
	String image=props.getProperty("image");
	String series_code=props.getProperty("series_code");
	String area=props.getProperty("area");
	String match=props.getProperty("match");
	String name=props.getProperty("name");
	String text=props.getProperty("text");
	String unidentifiable_text=props.getProperty("unidentifiable_text");
	String unapproved_text=props.getProperty("unapproved_text");
	String individual=props.getProperty("individual");
	String view_all_unidentified=props.getProperty("view_all_unidentified");
	String nav_text=props.getProperty("nav_text");
	String from_user=props.getProperty("from_user");
	String view_all=props.getProperty("view_all");
	String all_encounters_text=props.getProperty("all_encounters_text");
	String viewing=props.getProperty("viewing");
	String view_all_user=props.getProperty("view_all_user");
	String number=props.getProperty("number");
	String error=props.getProperty("error");
	String date=props.getProperty("date");
	String locationID=props.getProperty("locationID");
	String location=props.getProperty("location");
	String size=props.getProperty("size");
	String tags=props.getProperty("tags");
	String sex=props.getProperty("sex");
	
	
	Shepherd myShepherd=new Shepherd();
	String currentSort="nosort";
	String displaySort="";
	if (request.getParameter("sort")!=null) {currentSort=request.getParameter("sort");
		if(request.getParameter("sort").startsWith("name")){displaySort=" sorted by Name";}
		else if(request.getParameter("sort").startsWith("series")){displaySort=" sorted by Series Code";}
		else if(request.getParameter("sort").startsWith("sex")){displaySort=" sorted by Sex";}
		else if(request.getParameter("sort").startsWith("encounters")){displaySort=" sorted by # Encounters";}
	}
	currentSort="&amp;sort="+currentSort;
	int lowCount=1, highCount=10;
	if ((request.getParameter("start")!=null)&&(request.getParameter("end")!=null)) {
		lowCount=(new Integer(request.getParameter("start"))).intValue();
		highCount=(new Integer(request.getParameter("end"))).intValue();
	}	


%>

<html>
<head>
<title><%=CommonConfiguration.getHTMLTitle() %></title>
<meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
<meta name="Description"
	content="<%=CommonConfiguration.getHTMLDescription() %>" />
<meta name="Keywords"
	content="<%=CommonConfiguration.getHTMLKeywords() %>" />
<meta name="Author" content="<%=CommonConfiguration.getHTMLAuthor() %>" />
<link href="<%=CommonConfiguration.getCSSURLLocation(request) %>"
	rel="stylesheet" type="text/css" />
<link rel="shortcut icon"
	href="<%=CommonConfiguration.getHTMLShortcutIcon() %>" />
</head>


<body>
<div id="wrapper">
<div id="page"><jsp:include page="../header.jsp" flush="true">
	<jsp:param name="isAdmin" value="<%=request.isUserInRole(\"admin\")%>" />
</jsp:include>

<div id="main">


<div id="maincol-wide-solo">
<div id="maintext">
<%

myShepherd.beginDBTransaction();
//build a query
Extent encClass=myShepherd.getPM().getExtent(Encounter.class, true);
Query query=myShepherd.getPM().newQuery(encClass);

try{

int totalCount=0;
if (request.getParameter("rejects")!=null) {   
	totalCount=myShepherd.getNumUnidentifiableEncounters();
%>
<table id="results" border="0">
	<tr>
		<td colspan="4">
		<h1><%=view_all_unidentified %></h1>
		</td>
	</tr>
	<tr>
		<th class="caption" colspan="4"><%=unidentifiable_text.replaceAll("COUNT", Integer.toString(totalCount))%> <%=nav_text %></font>
		</p>
		</td>
	</tr>
</table>


<%} else if((request.getParameter("unapproved")!=null)&&(session.getAttribute("logged")!=null)) {
	
%>
<table>
	<tr>
		<td bgcolor="#CC6600" colspan="4">
		<p><strong><%=unapproved_text %></strong></p>
		</td>
	</tr>
</table>

<table>
	<tr>
		<td>
		<p><%=nav_text %></p>
		</td>
	</tr>
</table>

<%
  	totalCount=myShepherd.getNumUnapprovedEncounters();
  
  } 
  else if(request.getParameter("user")!=null) {
	totalCount=myShepherd.getNumUserEncounters(request.getParameter("user"));
%>

<table id="results" border="0">
	<tr>
		<td colspan="4">
		<h1><%=from_user %>: <em><%=request.getParameter("user")%></em></h1>

		</td>
	</tr>



	<tr>
		<td class="caption">
		<p><%=view_all_user.replaceAll("COUNT", Integer.toString(totalCount)).replaceAll("USERNAME", request.getParameter("user")
				) %> <%=nav_text %></p>
		</td>
	</tr>
</table>

<%} else {
		totalCount= myShepherd.getNumEncounters();
		//System.out.println("     Got the total number of encounters.");
%>
<table id="results" border="0">
	<tr>
		<td colspan="4">
		<h1><%=view_all %></h1>
		</td>
	</tr>
	<tr>
		<td class="caption" colspan="4"><%=all_encounters_text.replaceAll("COUNT", Integer.toString(totalCount)) %> <%=nav_text %>
		</td>
	</tr>
</table>





<%}%>
<table id="results" border="0" width="810px">
	<tr class="paging">
		<td align="left">
		<%

String rejectsLink="";
if ((session.getAttribute("logged")!=null)&&(request.getParameter("rejects")!=null)) {rejectsLink="&rejects=true";}
String unapprovedLink="";
if ((session.getAttribute("logged")!=null)&&(request.getParameter("unapproved")!=null)) {unapprovedLink="&unapproved=true";}
String userLink="";
if (request.getParameter("user")!=null) {userLink="&user="+request.getParameter("user");}

if (highCount<totalCount) {%> <a
			href="http://<%=CommonConfiguration.getURLLocation(request) %>/encounters/allEncounters.jsp?start=<%=(lowCount+10)%>&amp;end=<%=(highCount+10)%><%=rejectsLink%><%=unapprovedLink%><%=userLink%><%=currentSort%>"><%=next %></a>
		<%} 
  if ((lowCount-10)>=1) {%> | <a
			href="http://<%=CommonConfiguration.getURLLocation(request) %>/encounters/allEncounters.jsp?start=<%=(lowCount-10)%>&amp;end=<%=(highCount-10)%><%=rejectsLink%><%=unapprovedLink%><%=userLink%><%=currentSort%>"><%=previous %></a>
		<%}%>
		</td>
		<td colspan="8" align="right">
		<%
 String startNum="1";
 String endNum="10";
  try{
 if((request.getParameter("unapproved")!=null)&&(totalCount<10)) {
 	endNum=(new Integer(totalCount)).toString();
 }
 
  if((request.getParameter("rejects")!=null)&&(totalCount<10)) {
 	endNum=(new Integer(totalCount)).toString();
 }
 
 
 if(request.getParameter("start")!=null){
 	startNum=request.getParameter("start");
 }
 if((request.getParameter("end")!=null)&&(!request.getParameter("end").equals("99999"))){
 	endNum=request.getParameter("end");
 }
 else if((request.getParameter("end")!=null)&&(request.getParameter("end").equals("99999"))){
 		endNum=(new Integer(totalCount)).toString();
 	}

 if(((new Integer(endNum)).intValue())>totalCount) {
 	endNum=(new Integer(totalCount)).toString();
 }
 } catch(NumberFormatException nfe) {}
 
 %> 
 <%=viewing %>: <%=lowCount%> - <%=highCount%><%=displaySort%></td>
	</tr>

	<tr class="lineitem">
		<td bgcolor="#99CCFF" class="lineitem">&nbsp;</td>
		<td align="left" valign="top" bgcolor="#99CCFF" class="lineitem"><strong><%=number %></strong>
		 <br />
		<%if(request.getRemoteUser()!=null){%><a
			href="http://<%=CommonConfiguration.getURLLocation(request)%>/encounters/allEncounters.jsp?sort=numberup<%=rejectsLink%><%=unapprovedLink%><%=userLink%>&amp;start=<%=(lowCount)%>&amp;end=<%=(highCount)%>"><img
			src="../images/arrow_up.gif" width="11" height="6" border="0" alt="up" />
		</a><a
			href="http://<%=CommonConfiguration.getURLLocation(request)%>/encounters/allEncounters.jsp?sort=numberdown<%=rejectsLink%><%=unapprovedLink%><%=userLink%>&amp;start=<%=(lowCount)%>&amp;end=<%=(highCount)%>"><img
			src="../images/arrow_down.gif" width="11" height="6" border="0"
			alt="down" /> </a>
		<%}%>
		</td>
		<td align="left" valign="top" bgcolor="#99CCFF" class="lineitem"><strong><%=date %></strong><br />
		<%if(request.getRemoteUser()!=null){%><a
			href="http://<%=CommonConfiguration.getURLLocation(request)%>/encounters/allEncounters.jsp?sort=dateup<%=rejectsLink%><%=unapprovedLink%><%=userLink%>&amp;start=<%=(lowCount)%>&amp;end=<%=(highCount)%>"><img
			src="../images/arrow_up.gif" width="11" height="6" border="0" alt="up" /></a>
		<a
			href="http://<%=CommonConfiguration.getURLLocation(request)%>/encounters/allEncounters.jsp?sort=datedown<%=rejectsLink%><%=unapprovedLink%><%=userLink%>&amp;start=<%=(lowCount)%>&amp;end=<%=(highCount)%>"><img
			src="../images/arrow_down.gif" width="11" height="6" border="0"
			alt="down" /></a>
		<%}%>
		</td>
		<td width="90" align="left" valign="top" bgcolor="#99CCFF"
			class="lineitem"><strong><%=location %></strong>

		</td>
		
		<td width="40" align="left" valign="top" bgcolor="#99CCFF"
			class="lineitem"><strong><%=locationID %></strong> <a
			href="http://<%=CommonConfiguration.getURLLocation(request)%>/encounters/allEncounters.jsp?sort=locationCodeup<%=rejectsLink%><%=unapprovedLink%><%=userLink%>&amp;start=<%=(lowCount)%>&amp;end=<%=(highCount)%>"><img
			src="../images/arrow_up.gif" width="11" height="6" border="0" alt="up" /></a>
		<a
			href="http://<%=CommonConfiguration.getURLLocation(request)%>/encounters/allEncounters.jsp?sort=locationCodedown<%=rejectsLink%><%=unapprovedLink%><%=userLink%>&amp;start=<%=(lowCount)%>&amp;end=<%=(highCount)%>"><img
			src="../images/arrow_down.gif" width="11" height="6" border="0"
			alt="down" /></a></td>
	
		<td align="left" valign="top" bgcolor="#99CCFF" class="lineitem"><strong><%=size %>
		</strong><br />
		<%if(request.getRemoteUser()!=null){%><a
			href="http://<%=CommonConfiguration.getURLLocation(request)%>/encounters/allEncounters.jsp?sort=sizeup<%=rejectsLink%><%=unapprovedLink%><%=userLink%>&amp;start=<%=(lowCount)%>&amp;end=<%=(highCount)%>"><img
			src="../images/arrow_up.gif" width="11" height="6" border="0" alt="up" /></a>
		<a
			href="http://<%=CommonConfiguration.getURLLocation(request)%>/encounters/allEncounters.jsp?sort=sizedown<%=rejectsLink%><%=unapprovedLink%><%=userLink%>&amp;start=<%=(lowCount)%>&amp;end=<%=(highCount)%>"><img
			src="../images/arrow_down.gif" width="11" height="6" border="0"
			alt="down" /></a>
		<%}%>
		</td>
		<td align="left" valign="top" bgcolor="#99CCFF" class="lineitem"><strong><%=sex %></strong><br />
		<%if(request.getRemoteUser()!=null){%><a
			href="http://<%=CommonConfiguration.getURLLocation(request)%>/encounters/allEncounters.jsp?sort=sexup<%=rejectsLink%><%=unapprovedLink%><%=userLink%>&amp;start=<%=(lowCount)%>&amp;end=<%=(highCount)%>"><img
			src="../images/arrow_up.gif" width="11" height="6" border="0" alt="up" /></a>
		<a
			href="http://<%=CommonConfiguration.getURLLocation(request)%>/encounters/allEncounters.jsp?sort=sexdown<%=rejectsLink%><%=unapprovedLink%><%=userLink%>&amp;start=<%=(lowCount)%>&amp;end=<%=(highCount)%>"><img
			src="../images/arrow_down.gif" width="11" height="6" border="0"
			alt="down" /></a>
		<%}%>
		</td>
		<td width="60" align="left" valign="top" bgcolor="#99CCFF"
			class="lineitem"><strong><%=individual%></strong> <%if(request.getRemoteUser()!=null){%><a
			href="http://<%=CommonConfiguration.getURLLocation(request)%>/encounters/allEncounters.jsp?sort=assignedup<%=rejectsLink%><%=unapprovedLink%><%=userLink%>&amp;start=<%=(lowCount)%>&amp;end=<%=(highCount)%>"><img
			src="../images/arrow_up.gif" width="11" height="6" border="0" alt="up" /></a>
		<a
			href="http://<%=CommonConfiguration.getURLLocation(request)%>/encounters/allEncounters.jsp?sort=assigneddown<%=rejectsLink%><%=unapprovedLink%><%=userLink%>&amp;start=<%=(lowCount)%>&amp;end=<%=(highCount)%>"><img
			src="../images/arrow_down.gif" width="11" height="6" border="0"
			alt="down" /></a>
		<%}%>
		</td>

		<%
		if(CommonConfiguration.useSpotPatternRecognition()){
		%>
			<td align="left" valign="top" bgcolor="#99CCFF" class="lineitem"><strong><font color="#000000"><%=tags %></font></strong></td>
		<%
		}
		%>
	
	</tr>
	<%		

  			Iterator allEncounters;

			int total=totalCount;
			int iterTotal=totalCount;
			if ((session.getAttribute("logged")!=null)&&(request.getParameter("rejects")!=null)&&(request.getParameter("sort")!=null)) {
					
					iterTotal=totalCount;
					query=ServletUtilities.setRange(query,iterTotal,highCount,lowCount);
					

				if (request.getParameter("sort").equals("sizeup")) {
					allEncounters=myShepherd.getAllUnidentifiableEncounters(query, "size ascending");
					}
				else if (request.getParameter("sort").equals("sizedown")) {
					allEncounters=myShepherd.getAllUnidentifiableEncounters(query, "size descending");
					}
				else if (request.getParameter("sort").equals("locationCodeup")) {
					allEncounters=myShepherd.getAllUnidentifiableEncounters(query, "locationID ascending");
					}
				else if (request.getParameter("sort").equals("locationCodedown")) {
					allEncounters=myShepherd.getAllUnidentifiableEncounters(query, "locationID descending");
					}
				else if (request.getParameter("sort").equals("sexup")) {
					allEncounters=myShepherd.getAllUnidentifiableEncounters(query, "sex ascending");
					}
				else if (request.getParameter("sort").equals("sexdown")) {
					allEncounters=myShepherd.getAllUnidentifiableEncounters(query, "sex descending");
					}
				else if (request.getParameter("sort").equals("numberup")) {
					allEncounters=myShepherd.getAllUnidentifiableEncounters(query, "catalogNumber ascending");
					}
				else if (request.getParameter("sort").equals("numberdown")) {
					allEncounters=myShepherd.getAllUnidentifiableEncounters(query, "catalogNumber descending");
					}
				else if (request.getParameter("sort").equals("dateup")) {
					allEncounters=myShepherd.getAllUnidentifiableEncounters(query, "year ascending, month ascending, day ascending, hour ascending, minutes ascending");
					}
				else if (request.getParameter("sort").equals("datedown")) {
					allEncounters=myShepherd.getAllUnidentifiableEncounters(query, "year descending, month descending, day descending, hour descending, minutes descending");
					}
				else if (request.getParameter("sort").equals("assignedup")) {
					allEncounters=myShepherd.getAllUnidentifiableEncounters(query, "individualID ascending");
					}
				else if (request.getParameter("sort").equals("assigneddown")) {
					allEncounters=myShepherd.getAllUnidentifiableEncounters(query, "individualID descending");
					}
				else {allEncounters=myShepherd.getAllUnidentifiableEncounters(query);}
					}
			else if((session.getAttribute("logged")!=null)&&(request.getParameter("rejects")!=null)) {


				query=ServletUtilities.setRange(query,iterTotal,highCount,lowCount);
					
				allEncounters=myShepherd.getAllUnidentifiableEncounters(query);
			}
			
			
			//user-based sorting
			else if ((request.getParameter("user")!=null)&&(request.getParameter("sort")!=null)) {
				query.setFilter("this.submitterID == \""+request.getParameter("user")+"\"");

						
						ServletUtilities.setRange(query,iterTotal,highCount,lowCount);
		
				if (request.getParameter("sort").equals("sizeup")) {
					allEncounters=myShepherd.getSortedUserEncounters(query, "size ascending");
					}
				else if (request.getParameter("sort").equals("sizedown")) {
					allEncounters=myShepherd.getSortedUserEncounters(query, "size descending");
					}
				else if (request.getParameter("sort").equals("locationCodeup")) {
					allEncounters=myShepherd.getSortedUserEncounters(query, "locationID ascending");
					}
				else if (request.getParameter("sort").equals("locationCodedown")) {
					
					allEncounters=myShepherd.getSortedUserEncounters( query, "locationID descending" );
					}
				else if (request.getParameter("sort").equals("sexup")) {
					//query.setFilter("this.approved && this.submitterID == \""+request.getParameter("user")+"\"");
					allEncounters=myShepherd.getSortedUserEncounters( query, "sex ascending" );
					}
				else if (request.getParameter("sort").equals("sexdown")) {
					//query.setFilter("this.approved && this.submitterID == \""+request.getParameter("user")+"\"");
					allEncounters=myShepherd.getSortedUserEncounters( query, "sex descending" );
					}
				else if (request.getParameter("sort").equals("numberup")) {
					//query.setFilter("this.approved && this.submitterID == \""+request.getParameter("user")+"\"");
					allEncounters=myShepherd.getSortedUserEncounters( query, "catalogNumber ascending" );
					}
				else if (request.getParameter("sort").equals("numberdown")) {
					//query.setFilter("this.approved && this.submitterID == \""+request.getParameter("user")+"\"");
					allEncounters=myShepherd.getSortedUserEncounters( query, "catalogNumber descending" );
					}
				else if (request.getParameter("sort").equals("dateup")) {
					//query.setFilter("this.approved && this.submitterID == \""+request.getParameter("user")+"\"");
					allEncounters=myShepherd.getSortedUserEncounters( query, "year ascending, month ascending, day ascending, hour ascending, minutes ascending" );
					}
				else if (request.getParameter("sort").equals("datedown")) {
					//query.setFilter("this.approved && this.submitterID == \""+request.getParameter("user")+"\"");
					allEncounters=myShepherd.getSortedUserEncounters( query, "year descending, month descending, day descending, hour descending, minutes descending" );
					}
				else if (request.getParameter("sort").equals("assignedup")) {
					//query.setFilter("this.approved && this.submitterID == \""+request.getParameter("user")+"\"");
					allEncounters=myShepherd.getSortedUserEncounters( query, "individualID ascending" );
					}
				else if (request.getParameter("sort").equals("assigneddown")) {
					//query.setFilter("this.approved && this.submitterID == \""+request.getParameter("user")+"\"");
					allEncounters=myShepherd.getSortedUserEncounters( query, "individualID descending" );
					}
				else {
					//query.setFilter(("this.approved && this.submitterID == \""+user+"\""));
					allEncounters=myShepherd.getUserEncounters(query, request.getParameter("user"));
					}
			}
			//end user-based sorting
			
		
			else if(request.getParameter("user")!=null) {
				query.setFilter(("this.approved && this.submitterID == \""+request.getParameter("user")+"\""));
				allEncounters=myShepherd.getUserEncounters(query, request.getParameter("user"));
				}
			else if(request.getParameter("sort")!=null) {
			
						iterTotal=myShepherd.getNumApprovedEncounters();

						
						query=ServletUtilities.setRange(query,iterTotal,highCount,lowCount);
			

				if (request.getParameter("sort").equals("sizeup")) {
					query.setFilter("!this.unidentifiable && this.approved == true");
					allEncounters=myShepherd.getAllEncounters(query, "size ascending");
					}
				else if (request.getParameter("sort").equals("sizedown")) {
					query.setFilter("!this.unidentifiable && this.approved == true");
					allEncounters=myShepherd.getAllEncounters(query, "size descending");
					}
				else if (request.getParameter("sort").equals("sexup")) {
					query.setFilter("!this.unidentifiable && this.approved == true");
					allEncounters=myShepherd.getAllEncounters(query, "sex ascending");
					}
				else if (request.getParameter("sort").equals("sexdown")) {
					query.setFilter("!this.unidentifiable && this.approved == true");
					allEncounters=myShepherd.getAllEncounters(query, "sex descending");
					}
				else if (request.getParameter("sort").equals("locationCodeup")) {
					query.setFilter("!this.unidentifiable && this.approved == true");
					allEncounters=myShepherd.getAllEncounters(query, "locationID ascending");
					}
				else if (request.getParameter("sort").equals("locationCodedown")) {
					query.setFilter("!this.unidentifiable && this.approved == true");
					allEncounters=myShepherd.getAllEncounters(query, "locationID descending");
					}
				else if (request.getParameter("sort").equals("numberup")) {
					query.setFilter("!this.unidentifiable && this.approved == true");
					allEncounters=myShepherd.getAllEncounters(query, "catalogNumber ascending");
					}
				else if (request.getParameter("sort").equals("numberdown")) {
					query.setFilter("!this.unidentifiable && this.approved == true");
					allEncounters=myShepherd.getAllEncounters(query, "catalogNumber descending");
					}
				else if (request.getParameter("sort").equals("dateup")) {
					query.setFilter("!this.unidentifiable && this.approved == true");
					allEncounters=myShepherd.getAllEncounters(query, "year ascending, month ascending, day ascending, hour ascending, minutes ascending");
					}
				else if (request.getParameter("sort").equals("datedown")) {
					query.setFilter("!this.unidentifiable && this.approved == true");
					allEncounters=myShepherd.getAllEncounters(query, "year descending, month descending, day descending, hour descending, minutes descending");
					}
				else if (request.getParameter("sort").equals("assignedup")) {
					query.setFilter("!this.unidentifiable && this.approved == true");
					allEncounters=myShepherd.getAllEncounters(query, "individualID ascending");
					}
				else if (request.getParameter("sort").equals("assigneddown")) {
					query.setFilter("!this.unidentifiable && this.approved == true");
					allEncounters=myShepherd.getAllEncounters(query, "individualID descending");
					}
				else {
					query.setFilter("!this.unidentifiable && this.approved == true");
					allEncounters=myShepherd.getAllEncounters(query, "dwcDateAdded descending");
					}
				}
			else{
				//allEncounters=myShepherd.getAllEncounters();
				iterTotal=totalCount-myShepherd.getNumUnapprovedEncounters();
			
				
				//query.setRange((iterTotal-highCount),(totalCount-lowCount+1));
				query=ServletUtilities.setRange(query,iterTotal,highCount,lowCount);

				query.setFilter("!this.unidentifiable && this.approved == true");
				allEncounters=myShepherd.getAllEncounters(query, "dwcDateAdded descending");
			}
			

			int countMe=0;
			while (allEncounters.hasNext()) {
				countMe++;
				Encounter enc=(Encounter)allEncounters.next(); 

					try{
					%>
	<tr class="lineitems">
		<td width="102" height="60" class="lineitems"><a
			href="http://<%=CommonConfiguration.getURLLocation(request) %>/encounters/encounter.jsp?number=<%=enc.getEncounterNumber()%>"><img
			src="http://<%=CommonConfiguration.getURLLocation(request)%>/encounters/<%=(enc.getEncounterNumber()+"/thumb.jpg")%>"
			 alt="encounter photo" border="0" /></a></td>

		<td class="lineitems"><a
			href="http://<%=CommonConfiguration.getURLLocation(request) %>/encounters/encounter.jsp?number=<%=enc.getEncounterNumber()%>"><%=enc.getEncounterNumber()%></a></td>
		<td class="lineitems">
		<a
			href="http://<%=CommonConfiguration.getURLLocation(request)%>/xcalendar/calendar.jsp?scDate=<%=enc.getMonth()%>/1/<%=enc.getYear()%>">
		<%=enc.getShortDate()%>
		
		</a>
	
		</td>
		<td width="90" class="lineitems"><%=enc.getLocation()%></td>
	
		<td class="lineitems"><%=enc.getLocationCode()%></td>
		<%
	if(enc.getSize()!=0) {
	%>
		<td class="lineitems"><%=enc.getSize()%></td>
		<%} else {%>
		<td class="lineitems">-</td>
		<%}
	String theSex=enc.getSex();
	if(theSex.equals("male")) {theSex="M";}
	else if (theSex.equals("female")) {theSex="F";}
	else {theSex="-";}
	%>
		<td class="lineitems"><%=theSex%></td>
		<%
	if (enc.isAssignedToMarkedIndividual().trim().toLowerCase().equals("unassigned")) {
%>
		<td class="lineitems">&nbsp;</td>
		<%
	} else {
%>
		<td class="lineitems"><a href="http://<%=CommonConfiguration.getURLLocation(request) %>/individuals.jsp?number=<%=enc.isAssignedToMarkedIndividual()%>"><%=enc.isAssignedToMarkedIndividual()%></a></td>
		<%
	}
	
	//spot patterning data
	if(CommonConfiguration.useSpotPatternRecognition()){
	if(((enc.getSpots()==null)&&(enc.getRightSpots()==null))) {%>
		<td class="lineitems">&nbsp;</td>
		<% } 
	else if((enc.getSpots().size()>0)&&(enc.getRightSpots().size()>0)) {%>
		<td class="lineitems">LR</td>
		<%}
	else if(enc.getSpots().size()>0) {%>
		<td class="lineitems">L</td>
		<%} 
	else if(enc.getRightSpots().size()>0) {%>
			<td class="lineitems">R</td>
	<%}
	}	
		
		
		
	  } catch(javax.jdo.JDOUserException jdoe) {
  		
		System.out.println("I hit a javax.jdo.JDOUserException in allEncounters.jsp!!!!");
		jdoe.printStackTrace();
		}
//}  
  }%>
	</tr>
	<tr class="paging">
		<td align="left">
		<%
if (highCount<totalCount) {%> <a
			href="http://<%=CommonConfiguration.getURLLocation(request) %>/encounters/allEncounters.jsp?start=<%=(lowCount+10)%>&amp;end=<%=(highCount+10)%><%=rejectsLink%><%=unapprovedLink%><%=userLink%><%=currentSort%>"><%=next %></a>
		<%} 
  if ((lowCount-10)>=0) {%> | <a
			href="http://<%=CommonConfiguration.getURLLocation(request) %>/encounters/allEncounters.jsp?start=<%=(lowCount-10)%>&amp;end=<%=(highCount-10)%><%=rejectsLink%><%=unapprovedLink%><%=userLink%><%=currentSort%>"><%=previous %></a>
		<%}%>
		</td>
		<td colspan="8" align="right"><%=viewing %>: <%=lowCount%> - <%=highCount%><%=displaySort%>

		</td>
	</tr>
</table>

<%
 //System.out.println("allEncounters.jsp page is attempting to commit a transaction...");
query.closeAll();

myShepherd.rollbackDBTransaction();
myShepherd.closeDBTransaction();
encClass=null;
query=null;
myShepherd=null;
%>
<p></p>

<%
//end try
} catch(Exception ex) {

	System.out.println("!!!An error occurred on page allEncounters.jsp. The error was:");
	ex.printStackTrace();
	//System.out.println("allEncounters.jsp page is attempting to rollback a transaction because of an exception...");
	query.closeAll();
	query=null;
	encClass=null;
	myShepherd.rollbackDBTransaction();
	myShepherd.closeDBTransaction();
	myShepherd=null;
	%>

<p><strong><%=error %></strong></p>
<p></p>


<%
}
%>
</div>
<!-- end maintext --></div>
<!-- end main-wide --></div>
<!-- end main --> <jsp:include page="../footer.jsp" flush="true" /></div>
<!-- end page --></div>
<!--end wrapper -->
</body>
</html>