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
	String from_user=props.getProperty("nav_text");
	String view_all=props.getProperty("view_all");
	String all_encounters_text=props.getProperty("all_encounters_text");
	String viewing=props.getProperty("viewing");
	String view_all_user=props.getProperty("view_all_user");
	String view_all_unapproved=props.getProperty("view_all_unapproved");
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
<link href="<%=CommonConfiguration.getCSSURLLocation() %>"
	rel="stylesheet" type="text/css" />
<link rel="shortcut icon"
	href="<%=CommonConfiguration.getHTMLShortcutIcon() %>" />
</head>


<body>
<div id="wrapper">
<div id="page"><jsp:include page="../header.jsp" flush="true">
	<jsp:param name="isResearcher"
		value="<%=request.isUserInRole("researcher")%>" />
	<jsp:param name="isManager"
		value="<%=request.isUserInRole("manager")%>" />
	<jsp:param name="isReviewer"
		value="<%=request.isUserInRole("reviewer")%>" />
	<jsp:param name="isAdmin" value="<%=request.isUserInRole("admin")%>" />
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
totalCount=myShepherd.getNumUnapprovedEncounters();



%>
<table width="810px">
	<tr>
		<td>
		<h1 class="unapproved_encounters"><%=view_all_unapproved %></h1>
		</td>
	</tr>
</table>

<table>
	<tr>
		<td>
		<p><%=unapproved_text.replaceAll("COUNT", Integer.toString(totalCount)) %> <%=nav_text %></p>
		</td>
	</tr>
</table>


<table id="results" border="0" width="810px">
	<tr class="paging">
		<td align="left">
		<%


if (highCount<totalCount) {%> <a
			href="http://<%=CommonConfiguration.getURLLocation() %>/encounters/allEncountersUnapproved.jsp?start=<%=(lowCount+10)%>&amp;end=<%=(highCount+10)%><%=currentSort%>"><%=next %></a>
		<%} 
  if ((lowCount-10)>=1) {%> | <a
			href="http://<%=CommonConfiguration.getURLLocation() %>/encounters/allEncountersUnapproved.jsp?start=<%=(lowCount-10)%>&amp;end=<%=(highCount-10)%><%=currentSort%>"><%=previous %></a>
		<%}%>
		</td>
		<td colspan="8" align="right">
		<%
 String startNum="1";
 String endNum="10";
  try{
 if(totalCount<10) {
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
		<td align="left" valign="top" bgcolor="#99CCFF" class="lineitem"><strong><%=number %></strong><br />
		(<%=last %> 4) <br />
		<%if(request.getRemoteUser()!=null){%><a
			href="http://<%=CommonConfiguration.getURLLocation()%>/encounters/allEncountersUnapproved.jsp?sort=numberup&amp;start=<%=(lowCount)%>&amp;end=<%=(highCount)%>"><img
			src="/images/arrow_up.gif" width="11" height="6" border="0" alt="up" />
		</a><a
			href="http://<%=CommonConfiguration.getURLLocation()%>/encounters/allEncountersUnapproved.jsp?sort=numberdown&amp;start=<%=(lowCount)%>&amp;end=<%=(highCount)%>"><img
			src="/images/arrow_down.gif" width="11" height="6" border="0"
			alt="down" /> </a>
		<%}%>
		</td>
		<td align="left" valign="top" bgcolor="#99CCFF" class="lineitem"><strong><%=date %></strong><br />
		<%if(request.getRemoteUser()!=null){%><a
			href="http://<%=CommonConfiguration.getURLLocation()%>/encounters/allEncountersUnapproved.jsp?sort=dateup&amp;start=<%=(lowCount)%>&amp;end=<%=(highCount)%>"><img
			src="/images/arrow_up.gif" width="11" height="6" border="0" alt="up" /></a>
		<a
			href="http://<%=CommonConfiguration.getURLLocation()%>/encounters/allEncountersUnapproved.jsp?sort=datedown&amp;start=<%=(lowCount)%>&amp;end=<%=(highCount)%>"><img
			src="/images/arrow_down.gif" width="11" height="6" border="0"
			alt="down" /></a>
		<%}%>
		</td>
		<td width="90" align="left" valign="top" bgcolor="#99CCFF"
			class="lineitem"><strong><%=location %></strong>
		</td>
		
		<td width="40" align="left" valign="top" bgcolor="#99CCFF"
			class="lineitem"><strong><%=locationID %></strong> <a
			href="http://<%=CommonConfiguration.getURLLocation()%>/encounters/allEncountersUnapproved.jsp?sort=locationCodeup&amp;start=<%=(lowCount)%>&amp;end=<%=(highCount)%>"><img
			src="/images/arrow_up.gif" width="11" height="6" border="0" alt="up" /></a>
		<a
			href="http://<%=CommonConfiguration.getURLLocation()%>/encounters/allEncountersUnapproved.jsp?sort=locationCodedown&amp;start=<%=(lowCount)%>&amp;end=<%=(highCount)%>"><img
			src="/images/arrow_down.gif" width="11" height="6" border="0"
			alt="down" /></a></td>
	
		<td align="left" valign="top" bgcolor="#99CCFF" class="lineitem"><strong><%=size %>
		</strong><br />
		<%if(request.getRemoteUser()!=null){%><a
			href="http://<%=CommonConfiguration.getURLLocation()%>/encounters/allEncountersUnapproved.jsp?sort=sizeup&amp;start=<%=(lowCount)%>&amp;end=<%=(highCount)%>"><img
			src="/images/arrow_up.gif" width="11" height="6" border="0" alt="up" /></a>
		<a
			href="http://<%=CommonConfiguration.getURLLocation()%>/encounters/allEncountersUnapproved.jsp?sort=sizedown&amp;start=<%=(lowCount)%>&amp;end=<%=(highCount)%>"><img
			src="/images/arrow_down.gif" width="11" height="6" border="0"
			alt="down" /></a>
		<%}%>
		</td>
		<td align="left" valign="top" bgcolor="#99CCFF" class="lineitem"><strong><%=sex %></strong><br />
		<%if(request.getRemoteUser()!=null){%><a
			href="http://<%=CommonConfiguration.getURLLocation()%>/encounters/allEncountersUnapproved.jsp?sort=sexup&amp;start=<%=(lowCount)%>&amp;end=<%=(highCount)%>"><img
			src="/images/arrow_up.gif" width="11" height="6" border="0" alt="up" /></a>
		<a
			href="http://<%=CommonConfiguration.getURLLocation()%>/encounters/allEncountersUnapproved.jsp?sort=sexdown&amp;start=<%=(lowCount)%>&amp;end=<%=(highCount)%>"><img
			src="/images/arrow_down.gif" width="11" height="6" border="0"
			alt="down" /></a>
		<%}%>
		</td>
		<td width="60" align="left" valign="top" bgcolor="#99CCFF"
			class="lineitem"><strong><%=individual%></strong> <%if(request.getRemoteUser()!=null){%><a
			href="http://<%=CommonConfiguration.getURLLocation()%>/encounters/allEncountersUnapproved.jsp?sort=assignedup&amp;start=<%=(lowCount)%>&amp;end=<%=(highCount)%>"><img
			src="/images/arrow_up.gif" width="11" height="6" border="0" alt="up" /></a>
		<a
			href="http://<%=CommonConfiguration.getURLLocation()%>/encounters/allEncountersUnapproved.jsp?sort=assigneddown&amp;start=<%=(lowCount)%>&amp;end=<%=(highCount)%>"><img
			src="/images/arrow_down.gif" width="11" height="6" border="0"
			alt="down" /></a>
		<%}%>
		</td>



		<td align="left" valign="top" bgcolor="#99CCFF" class="lineitem"><strong><font
			color="#000000"><%=tags %></font></strong></td>
	
	</tr>
	<%		

  			Iterator allEncounters;

			int total=totalCount;
			int iterTotal=totalCount;

				query=ServletUtilities.setRange(query,iterTotal,highCount,lowCount);
			
					query.setFilter("!this.unidentifiable && this.approved == false");

			if(request.getParameter("sort")!=null){
					
				if (request.getParameter("sort").equals("sizeup")) {
					allEncounters=myShepherd.getUnapprovedEncounters(query, "size ascending");
					}
				else if (request.getParameter("sort").equals("sizedown")) {
					allEncounters=myShepherd.getUnapprovedEncounters(query, "size descending");
					}
				else if (request.getParameter("sort").equals("locationCodeup")) {
					allEncounters=myShepherd.getUnapprovedEncounters(query, "locationID ascending");
					}
				else if (request.getParameter("sort").equals("locationCodedown")) {
					allEncounters=myShepherd.getUnapprovedEncounters(query, "locationID descending");
					}
				else if (request.getParameter("sort").equals("sexup")) {
					allEncounters=myShepherd.getUnapprovedEncounters(query, "sex ascending");
					}
				else if (request.getParameter("sort").equals("sexdown")) {
					allEncounters=myShepherd.getUnapprovedEncounters(query, "sex descending");
					}
				else if (request.getParameter("sort").equals("numberup")) {
					allEncounters=myShepherd.getUnapprovedEncounters(query, "catalogNumber ascending");
					}
				else if (request.getParameter("sort").equals("numberdown")) {
					allEncounters=myShepherd.getUnapprovedEncounters(query, "catalogNumber descending");
					}
				else if (request.getParameter("sort").equals("dateup")) {
					allEncounters=myShepherd.getUnapprovedEncounters(query, "year ascending, month ascending, day ascending, hour ascending, minutes ascending");
					}
				else if (request.getParameter("sort").equals("datedown")) {
					allEncounters=myShepherd.getUnapprovedEncounters(query, "year descending, month descending, day descending, hour descending, minutes descending");
					}
				else if (request.getParameter("sort").equals("assignedup")) {
					allEncounters=myShepherd.getUnapprovedEncounters(query, "individualID ascending");
					}
				else if (request.getParameter("sort").equals("assigneddown")) {
					allEncounters=myShepherd.getUnapprovedEncounters(query, "individualID descending");
					}
				else {
					
					allEncounters=myShepherd.getUnapprovedEncounters(query, "dwcDateAdded descending");
				}
				}
				else {
	
					allEncounters=myShepherd.getUnapprovedEncounters(query, "dwcDateAdded descending");
				}
			
			//end unapproved sorting
			
			

			int countMe=0;
			while (allEncounters.hasNext()) {
				countMe++;
				Encounter enc=(Encounter)allEncounters.next(); 

					try{
					%>
	<tr class="lineitems">
		<td width="102" height="60" class="lineitems"><a
			href="http://<%=CommonConfiguration.getURLLocation() %>/encounters/encounter.jsp?number=<%=enc.getEncounterNumber()%>"><img
			src="http://<%=CommonConfiguration.getURLLocation()%>/encounters/<%=(enc.getEncounterNumber()+"/thumb.jpg")%>" alt="encounter photo" border="0" /></a></td>
		<%
		int encNumLast=enc.getEncounterNumber().length();
		String encNumShort=enc.getEncounterNumber();
		if(encNumLast>4){
			encNumShort=enc.getEncounterNumber().substring((encNumLast-4),encNumLast);
		}
	
	%>
		<td class="lineitems"><a
			href="http://<%=CommonConfiguration.getURLLocation() %>/encounters/encounter.jsp?number=<%=enc.getEncounterNumber()%>"><%=encNumShort%></a></td>
		<td class="lineitems">
		<a
			href="http://<%=CommonConfiguration.getURLLocation()%>/xcalendar/calendar.jsp?scDate=<%=enc.getMonth()%>/1/<%=enc.getYear()%>">
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
		<td class="lineitems"><a
			href="http://<%=CommonConfiguration.getURLLocation() %>/individuals.jsp?number=<%=enc.isAssignedToMarkedIndividual()%>"><%=enc.isAssignedToMarkedIndividual()%></a></td>
		<%
	}
	if(((enc.getSpots()==null)&&(enc.getRightSpots()==null))) {%>
		<td class="lineitems">&nbsp;</td>
		<% } else if((enc.getSpots().size()>0)&&(enc.getRightSpots().size()>0)) {%>
		<td class="lineitems">LR</td>
		<%}else if(enc.getSpots().size()>0) {%>
		<td class="lineitems">L</td>
		<%} else if(enc.getRightSpots().size()>0) {%>
		<td class="lineitems">R</td>
		<%}
	  } catch(javax.jdo.JDOUserException jdoe) {
  		
		System.out.println("I hit a javax.jdo.JDOUserException in allEncountersUnapproved.jsp!!!!");
		jdoe.printStackTrace();
		}
//}  
  }%>
	</tr>
	<tr class="paging">
		<td align="left">
		<%
if (highCount<totalCount) {%> <a
			href="http://<%=CommonConfiguration.getURLLocation() %>/encounters/allEncountersUnapproved.jsp?start=<%=(lowCount+10)%>&amp;end=<%=(highCount+10)%><%=currentSort%>"><%=next %></a>
		<%} 
  if ((lowCount-10)>=0) {%> | <a
			href="http://<%=CommonConfiguration.getURLLocation() %>/encounters/allEncountersUnapproved.jsp?start=<%=(lowCount-10)%>&amp;end=<%=(highCount-10)%><%=currentSort%>"><%=previous %></a>
		<%}%>
		</td>
		<td colspan="8" align="right"><%=viewing %>: <%=lowCount%> - <%=highCount%><%=displaySort%>

		</td>
	</tr>
</table>

<%
 //System.out.println("allEncountersUnapproved.jsp page is attempting to commit a transaction...");
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

	System.out.println("!!!An error occurred on page allEncountersUnapproved.jsp. The error was:");
	ex.printStackTrace();
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