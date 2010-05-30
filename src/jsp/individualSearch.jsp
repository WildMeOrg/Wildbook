<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=iso-8859-1" language="java"
	import="org.ecocean.*, javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Enumeration, java.util.Iterator, java.util.GregorianCalendar"%>
<%
Shepherd myShepherd=new Shepherd();
Extent allKeywords=myShepherd.getPM().getExtent(Keyword.class,true);		
Query kwQuery=myShepherd.getPM().newQuery(allKeywords);

GregorianCalendar cal=new GregorianCalendar();
int nowYear=cal.get(1);

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
<div id="page"><jsp:include page="header.jsp" flush="true">
	<jsp:param name="isResearcher"
		value="<%=request.isUserInRole("researcher")%>" />
	<jsp:param name="isManager"
		value="<%=request.isUserInRole("manager")%>" />
	<jsp:param name="isReviewer"
		value="<%=request.isUserInRole("reviewer")%>" />
	<jsp:param name="isAdmin" value="<%=request.isUserInRole("admin")%>" />
</jsp:include>
<div id="main">
<table width="720">
	<tr>
		<td>
		<p>
		<h1 class="intro"><strong><span class="para"><img
			src="images/markedIndividualIcon.gif" width="26" height="51" align="absmiddle" /></span></strong>
		Shark Search Criteria</h1>
		</p>
		<p><em>Select from the criteria below to tailor your search
		among the individually identified sharks stored in the database.</em></p>
		<form action="individualSearchResults.jsp" method="get" name="search"
			id="search">
		<table>

			<tr>
				<td>
				<table width="557" align="left">
					<tr>
						<td width="62"><strong>Sex is: </strong></td>
						<td width="62"><label> <input name="sex" type="radio"
							value="all" checked> All</label></td>
						<td width="138"><label> <input name="sex"
							type="radio" value="mf"> Male or Female</label></td>

						<td width="76"><label> <input type="radio" name="sex"
							value="male"> Male</label></td>

						<td width="79"><label> <input type="radio" name="sex"
							value="female">Female</label></td>
						<td width="112"><label> <input type="radio"
							name="sex" value="unsure"> Unknown</label></td>
					</tr>
				</table>
				</td>
			</tr>

			<tr>
				<td>Maximum years between resightings is: <select
					name="numResightsOperator" id="numResightsOperator">
					<option value="greater" selected="selected">&#8250;=</option>
					<option value="equals">=</option>
					<option value="less">&#8249;=</option>
				</select> &nbsp; <select name="numResights" id="numResights">
					<option value="0" selected="selected">0</option>
					<option value="1">1</option>
					<option value="2">2</option>
					<option value="3">3</option>
					<option value="4">4</option>
					<option value="5">5</option>
					<option value="6">6</option>
					<option value="7">7</option>
					<option value="8">8</option>
					<option value="9">9</option>
					<option value="10">10</option>
					<option value="11">11</option>
					<option value="12">12</option>
					<option value="13">13</option>
					<option value="14">14</option>
					<option value="15">15</option>
				</select> year(s) apart.</td>
			</tr>
			<%
myShepherd.beginDBTransaction();
int totalKeywords=myShepherd.getNumKeywords();
%>
			<tr>
				<td>Has photos showing these feature(s). Select one or more.<br>
				<select multiple size="5" name="keyword" id="keyword">
					<option value="None"></option>
					<% 
				

			  	Iterator keys=myShepherd.getAllKeywords(kwQuery);
			  	for(int n=0;n<totalKeywords;n++) {
					Keyword word=(Keyword)keys.next();
				%>
					<option value="<%=word.getIndexname()%>"><%=word.getReadableName()%></option>
					<%}
				
				%>

				</select>
				</td>
			</tr>
			<%
myShepherd.rollbackDBTransaction();
%>
			<tr>
				<td>Has at least <select name="numspots" id="numspots">
					<option value="0" selected>0</option>
					<option value="10">10</option>
					<option value="20">20</option>
					<option value="30">30</option>
					<option value="40">40</option>
					<option value="50">50</option>
				</select> left-side spot(s).</td>
			</tr>
			<tr>
				<td><strong>Average estimated\measured length was: </strong> <select
					name="selectLength" size="1">
					<option value="greater">&gt;</option>
					<option value="less">&lt;</option>
				</select> <select name="lengthField" id="lengthField">
					<option value="0" selected>Unknown</option>
					<option value="0.5">0.5</option>
					<option value="1">1</option>
					<option value="1.5">1.5</option>
					<option value="2">2</option>
					<option value="2.5">2.5</option>
					<option value="3">3</option>
					<option value="3.5">3.5</option>
					<option value="4">4</option>
					<option value="4.5">4.5</option>
					<option value="5">5</option>
					<option value="5.5">5.5</option>
					<option value="6">6</option>
					<option value="6.5">6.5</option>
					<option value="7">7</option>
					<option value="7.5">7.5</option>
					<option value="8">8</option>
					<option value="8.5">8.5</option>
					<option value="9">9</option>
					<option value="9.5">9.5</option>
					<option value="10">10</option>
					<option value="10.5">10.5</option>
					<option value="11">11</option>
					<option value="11.5">11.5</option>
					<option value="12">12</option>
					<option value="12.5">12.5</option>
					<option value="13">13</option>
					<option value="13.5">13.5</option>
					<option value="14">14</option>
					<option value="14.5">14.5</option>
					<option value="15">15</option>
					<option value="15.5">15.5</option>
					<option value="16">16</option>
					<option value="16">16.5</option>
					<option value="17">17</option>
					<option value="17.5">17.5</option>
					<option value="18">18</option>
					<option value="18.5">18.5</option>
					<option value="19">19</option>
					<option value="19.5">19.5</option>
					<option value="20">20</option>
				</select> Meters</td>
			</tr>
			<tr>
				<td>
				<p><strong>Location ID starts with:</strong><em> <input
					name="locationCodeField" type="text" id="locationCodeField"
					size="10" maxlength="25"> <span class="para"><a
					href="<%=CommonConfiguration.getWikiLocation()%>location_codes"
					target="_blank"><img src="images/information_icon_svg.gif"
					alt="Help" width="15" height="15" border="0" align="absmiddle" /></a></span>
				<br> Leave blank to accept all locations in your search. Fill
				in the location ID digit by digit to narrow the location of your
				search. You can specify multiple location IDs separated by a comma
				to find only those individuals sighted at least once in all.
				Example: Use </em>1a1<em> as the location ID for Northern Ningaloo Marine
				Park.</em></p>
				</td>
			</tr>
			<tr>
				<td>
				<p><strong>Alternate ID starts with:</strong> <em> <input
					name="alternateIDField" type="text" id="alternateIDField" size="25"
					maxlength="100"> <span class="para"><a
					href="<%=CommonConfiguration.getWikiLocation()%>alternateID"
					target="_blank"><img src="images/information_icon_svg.gif"
					alt="Help" width="15" height="15" border="0" align="absmiddle" /></a></span>
				<br></em></p>
				</td>
			</tr>

			<tr>
				<td><strong>At least one sighting within these dates:</strong></td>
			</tr>
			<tr>
				<td>
				<table width="720">
					<tr>
						<td width="670"><label> <em> </em><em>Month</em> <em>
						<select name="month1" id="month1">
							<option value="1" selected>1</option>
							<option value="2">2</option>
							<option value="3">3</option>
							<option value="4">4</option>
							<option value="5">5</option>
							<option value="6">6</option>
							<option value="7">7</option>
							<option value="8">8</option>
							<option value="9">9</option>
							<option value="10">10</option>
							<option value="11">11</option>
							<option value="12">12</option>
						</select> Year</em> <select name="year1" id="year1">
							<option><%=nowYear%></option>
							<% for(int p=1;p<30;p++) { 
			  	if(p!=29){
			  
			  %>
							<option value="<%=(nowYear-p)%>"><%=(nowYear-p)%></option>

							<% 
				}
				else { %>
							<option value="<%=(nowYear-p)%>" selected><%=(nowYear-p)%></option>

							<%}
				} %>
						</select> &nbsp;to <em>&nbsp;</em><em>Month</em> <em> <select
							name="month2" id="month2">
							<option value="1">1</option>
							<option value="2">2</option>
							<option value="3">3</option>
							<option value="4">4</option>
							<option value="5">5</option>
							<option value="6">6</option>
							<option value="7">7</option>
							<option value="8">8</option>
							<option value="9">9</option>
							<option value="10">10</option>
							<option value="11">11</option>
							<option value="12" selected>12</option>
						</select> Year</em> <select name="year2" id="year2">
							<option selected="selected"><%=nowYear%></option>
							<% for(int p=1;p<30;p++) { %>
							<option vale="<%=(nowYear-p)%>"><%=(nowYear-p)%></option>

							<% } %>
						</select> </label></td>
					</tr>
				</table>
				</td>
			</tr>

			<tr>
				<td>
				<%
				if(request.isUserInRole("manager")) {
			%>
				<p><input name="export" type="checkbox" id="export" value="true">
				Generate a <em>frequency format</em> capture history file for
				modeling of results (<a
					href="http://www.cnr.colostate.edu/~gwhite/mark/mark.htm">Program
				Mark</a>\<a
					href="http://www.mesc.usgs.gov/products/software/clostest/clostest.asp">CloseTest</a>)</p>
				<p><input name="capture" type="checkbox" id="capture"
					value="true"> Generate a CAPTURE-compatible capture history
				file for modeling
				<p><input name="subsampleMonths" type="checkbox"
					id="subsampleMonths" value="subsampleMonths"> Subsample by
				months</p>


				<%}%>
				<p><em> <input name="submitSearch" type="submit"
					id="submitSearch" value="Go Search"></em>
				</td>
			</tr>
		</table>
		</form>
		</td>
	</tr>
</table>
<br> <jsp:include page="footer.jsp" flush="true" />
</div>
</div>
<!-- end page --></div>
<!--end wrapper -->

<%
kwQuery.closeAll();
myShepherd.closeDBTransaction();
kwQuery=null;
myShepherd=null;
%>

</body>
</html>


