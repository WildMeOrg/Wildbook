<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java"
	import="org.ecocean.*,java.util.GregorianCalendar, org.ecocean.Shepherd"%>

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

<%
GregorianCalendar cal=new GregorianCalendar();
int nowYear=cal.get(1);

%>


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
<table width="720">
	<tr>
		<td>
		<p>
		<h1 class="intro">Encounter Search Criteria <a
			href="<%=CommonConfiguration.getWikiLocation()%>searching#encounter_search"
			target="_blank"><img src="../images/information_icon_svg.gif"
			alt="Help" border="0" align="absmiddle" /></a></h1>
		</p>
		<p><em>Select from the criteria below to tailor your search
		among the sightings stored in the database.</em></p>
		<form action="searchResults.jsp" method="get" name="search"
			id="search">
		<table>
			<tr>
				<td>
				<table width="715" align="left">
					<tr>
						<td width="154"><strong>Encounter types to search</strong>:</td>
						<td width="208"><label> <input type="checkbox"
							name="approved" value="acceptedEncounters" checked>Approved
						encounters</input></label></td>

						<%if(request.getRemoteUser()!=null){%>
						<td width="188"><label> <input name="unapproved"
							type="checkbox" value="allEncounters" checked>Unapproved
						encounters</input></label></td>
						<%}%>
						<td width="145"><label> <input name="unidentifiable"
							type="checkbox" value="allEncounters" checked>Unidentifiable</input></label>
						</td>


					</tr>
				</table>
				</td>
			</tr>

			<tr>
				<td>
				<table width="357" align="left">
					<tr>
						<td width="62"><strong>Sex is: </strong></td>
						<td width="76"><label> <input name="male"
							type="checkbox" id="male" value="male" checked> Male</label></td>

						<td width="79"><label> <input name="female"
							type="checkbox" id="female" value="female" checked>
						Female</label></td>
						<td width="112"><label> <input name="unknown"
							type="checkbox" id="unknown" value="unknown" checked>
						Unknown</label></td>
					</tr>
				</table>
				</td>
			</tr>

			<tr>
				<td>
				<table width="310" align="left">
					<tr>
						<td width="77"><strong>Status is: </strong></td>
						<td width="90"><label> <input name="alive"
							type="checkbox" id="alive" value="alive" checked> Alive</label></td>

						<td width="127"><label> <input name="dead"
							type="checkbox" id="dead" value="dead" checked> Dead</label></td>
					</tr>
				</table>
				</td>
			</tr>

			<%if(request.getRemoteUser()!=null){%>
			<tr>
				<td><input name="resightOnly" type="checkbox" id="resightOnly"
					value="true"> Include only encounters for marked
				individuals that have been sighted at least <select
					name="numResights" id="numResights">
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
					<option value="13">13</option>
					<option value="14">14</option>
					<option value="15">15</option>
				</select> time(s). </td>
			</tr>
			<%}%>


			<tr>
				<td><strong>Length is: </strong> <select name="selectLength"
					size="1">
					<option value="gt">&gt;</option>
					<option value="lt">&lt;</option>
					<option value="eq">=</option>
				</select> <select name="lengthField" id="lengthField">
					<option value="skip" selected>None</option>
					<option value="1.0">1</option>
					<option value="2.0">2</option>
					<option value="3.0">3</option>
					<option value="4.0">4</option>
					<option value="5.0">5</option>
					<option value="6.0">6</option>
					<option value="7.0">7</option>
					<option value="8.0">8</option>
					<option value="9.0">9</option>
					<option value="10.0">10</option>
					<option value="11.0">11</option>
					<option value="12.0">12</option>
					<option value="13.0">13</option>
					<option value="14.0">14</option>
					<option value="15.0">15</option>
					<option value="16.0">16</option>
					<option value="17.0">17</option>
					<option value="18.0">18</option>
					<option value="19.0">19</option>
					<option value="20.0">20</option>
				</select> Meters</td>
			</tr>
			<tr>
				<td>
				<p><strong>Location name contains:</strong> <input
					name="locationField" type="text" size="60"> <br> <em>Leave
				blank to accept all locations in your search. This field IS NOT
				case-sensitive.</em></p>
				</td>
			</tr>
			<tr>
				<td>
				<p><strong>Location ID starts with:</strong><em> <input
					name="locationCodeField" type="text" id="locationCodeField"
					size="7"> <span class="para"><a
					href="<%=CommonConfiguration.getWikiLocation()%>location_codes"
					target="_blank"><img src="../images/information_icon_svg.gif"
					alt="Help" border="0" align="absmiddle" /></a></span> <br> Leave blank
				to accept all locations in your search, fill in the location code
				digit by digit to narrow the location of your search. Example: Use </em>1a1<em>
				as the location ID for Northern Ningaloo Marine Park. </em></p>
				</td>
			</tr>



			<%if(request.getRemoteUser()!=null){%>
			<tr>
				<td>
				<p><strong>Submitter or photographer name contains:</strong> <input
					name="nameField" type="text" size="60"> <br> <em>Leave
				blank to accept all names in your search. This field IS NOT
				case-sensitive.</em></p>
				</td>
			</tr>
			<tr>
				<td>
				<p><strong>Alternate ID starts with:</strong> <em> <input
					name="alternateIDField" type="text" id="alternateIDField" size="10"
					maxlength="35"> <span class="para"><a
					href="<%=CommonConfiguration.getWikiLocation()%>alternateID"
					target="_blank"><img src="../images/information_icon_svg.gif"
					alt="Help" width="15" height="15" border="0" align="absmiddle" /></a></span>
				<br></em></p>
				</td>
			</tr>
			<%}%>


			<tr>
				<td><strong>Sighting dates:</strong></td>
			</tr>
			<tr>
				<td>
				<table width="720">
					<tr>
						<td width="670"><label> <input name="dateLimit"
							type="checkbox" id="dateLimit" value="dateLimit"> Range:<em>
						&nbsp;Day</em> <em> <select name="day1" id="day1">
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
							<option value="13">13</option>
							<option value="14">14</option>
							<option value="15">15</option>
							<option value="16">16</option>
							<option value="17">17</option>
							<option value="18">18</option>
							<option value="19">19</option>
							<option value="20">20</option>
							<option value="21">21</option>
							<option value="22">22</option>
							<option value="23">23</option>
							<option value="24">24</option>
							<option value="25">25</option>
							<option value="26">26</option>
							<option value="27">27</option>
							<option value="28">28</option>
							<option value="29">29</option>
							<option value="30">30</option>
							<option value="31">31</option>
						</select> Month</em> <em> <select name="month1" id="month1">
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
							<% for(int q=20;q>=0;q--) { %>
							<option vale="<%=(nowYear-q)%>"><%=(nowYear-q)%></option>

							<% } %>
						</select> &nbsp;to <em>&nbsp;Day</em> <em> <select name="day2"
							id="day2">
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
							<option value="16">16</option>
							<option value="17">17</option>
							<option value="18">18</option>
							<option value="19">19</option>
							<option value="20">20</option>
							<option value="21">21</option>
							<option value="22">22</option>
							<option value="23">23</option>
							<option value="24">24</option>
							<option value="25">25</option>
							<option value="26">26</option>
							<option value="27">27</option>
							<option value="28">28</option>
							<option value="29">29</option>
							<option value="30">30</option>
							<option value="31" selected>31</option>
						</select> Month</em> <em> <select name="month2" id="month2">
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
							<% for(int p=1;p<20;p++) { %>
							<option vale="<%=(nowYear-p)%>"><%=(nowYear-p)%></option>

							<% } %>
						</select></label></td>
					</tr>
				</table>
				</td>
			</tr>

			<tr>
				<td>
				<%
				if(request.isUserInRole("researcher")) {
			%>
				<p><input name="export" type="checkbox" id="export" value="true">
				<strong>Generate a tab-delimited file of results for export
				to Excel (only content you have permissions for will be exported)</strong><br>
				&nbsp;&nbsp;&nbsp;&nbsp;<input name="locales" type="checkbox"
					id="locales" value="true"> Excel: Add locale coordinates
				for encounters without GPS</p>
				</p>
				<%
				}
				
				
				if(request.isUserInRole("researcher")) {
				%>
				<p><input name="generateKML" type="checkbox" id="generateKML"
					value="true"> <strong>Generate a KML file of
				results for Google Earth</strong><br> &nbsp;&nbsp;&nbsp;&nbsp;<input
					name="addTimeStamp" type="checkbox" id="addTimeStamp" value="true">
				Add a timestamp to the KML to animate on a timeline </p>
				<%
				}
	
				if(request.isUserInRole("manager")) {
				%>
				<p><input name="generateEmails" type="checkbox"
					id="generateEmails" value="true"> <strong>Generate
				a file list of submitter and photographer emails</strong></p>
				<%
				}
				%>

				</p>
				<p><em> <input name="submitSearch" type="submit"
					id="submitSearch" value="Go Search"></em>
				</td>
			</tr>
		</table>
		</form>
		</td>
	</tr>
</table>
<br> <jsp:include page="../footer.jsp" flush="true" />
</div>
</div>
<!-- end page --></div>
<!--end wrapper -->

</body>
</html>


