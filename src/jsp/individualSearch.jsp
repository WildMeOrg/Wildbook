<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java"
	import="java.util.ArrayList, org.ecocean.*, javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Enumeration, java.util.Iterator, java.util.GregorianCalendar, java.util.Properties"%>
<%
Shepherd myShepherd=new Shepherd();
Extent allKeywords=myShepherd.getPM().getExtent(Keyword.class,true);		
Query kwQuery=myShepherd.getPM().newQuery(allKeywords);

GregorianCalendar cal=new GregorianCalendar();
int nowYear=cal.get(1);

int firstYear = 1980;
myShepherd.beginDBTransaction();
try{
	firstYear = myShepherd.getEarliestSightingYear();
	nowYear = myShepherd.getLastSightingYear();
}
catch(Exception e){
	e.printStackTrace();
}

//let's load out properties
Properties props=new Properties();
String langCode="en";
if(session.getAttribute("langCode")!=null){langCode=(String)session.getAttribute("langCode");}
props.load(getClass().getResourceAsStream("/bundles/"+langCode+"/individualSearch.properties"));

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
		<h1 class="intro"><strong><span class="para">
		<img src="images/tag_big.gif" width="50" align="absmiddle" /></span></strong>
		<%=props.getProperty("title")%></h1>
		</p>
		<p><em><%=props.getProperty("instructions")%></em></p>
		<form action="individualSearchResults.jsp" method="get" name="search"
			id="search">
		<table>

			<tr>
				<td>
				<table width="557" align="left">
					<tr>
						<td width="62"><strong><%=props.getProperty("sex")%>: </strong></td>
						<td width="62"><label> <input name="sex" type="radio"
							value="all" checked> <%=props.getProperty("all")%></label></td>
						<td width="138"><label> <input name="sex"
							type="radio" value="mf"> <%=props.getProperty("maleOrFemale")%></label></td>

						<td width="76"><label> <input type="radio" name="sex"
							value="male"> <%=props.getProperty("male")%></label></td>

						<td width="79"><label> <input type="radio" name="sex"
							value="female"><%=props.getProperty("female")%></label></td>
						<td width="112"><label> <input type="radio"
							name="sex" value="unknown"> <%=props.getProperty("unknown")%></label></td>
					</tr>
				</table>
				</td>
			</tr>

			<tr>
				<td><%=props.getProperty("maxYearsBetweenResights")%>: <select
					name="numResightsOperator" id="numResightsOperator">
					<option value="greater" selected="selected">&#8250;=</option>
					<option value="equals">=</option>
					<option value="less">&#8249;=</option>
				</select> &nbsp; <select name="numResights" id="numResights">
					<%
					
					int maxYearsBetweenResights = 0;
					try{
						maxYearsBetweenResights = Math.abs(nowYear-firstYear);
					}
					catch(Exception e){}
					
					%>
					
					<option value="0" selected="selected">0</option>
					
					<%
					for(int u=1;u<=maxYearsBetweenResights;u++){
					%>
					<option value="<%=u%>"><%=u%></option>
					<%
					}
					%>
				</select> <%=props.getProperty("yearsApart")%></td>
			</tr>
			<%
myShepherd.beginDBTransaction();
int totalKeywords=myShepherd.getNumKeywords();
%>
			<tr>
				<td><p><%=props.getProperty("hasKeywordPhotos")%></p>
				<%
				
				if(totalKeywords>0){
				%>
				
				<select multiple size="<%=(totalKeywords+1) %>" name="keyword" id="keyword">
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
				<%
				}
				else{
					%>
					
					<p><em><%=props.getProperty("noKeywords")%></em></p>
					
					<%
					
				}
				%>
				</td>
			</tr>
			<%
myShepherd.rollbackDBTransaction();
%>
			<tr>
				<td><p><%=props.getProperty("hasAtLeast")%> <select name="numspots" id="numspots">
					<option value="0" selected>0</option>
					<option value="10">10</option>
					<option value="20">20</option>
					<option value="30">30</option>
					<option value="40">40</option>
					<option value="50">50</option>
				</select> <%=props.getProperty("leftSpots")%></p></td>
			</tr>
			<tr>
				<td><strong><%=props.getProperty("lengthIs")%>: </strong> <select
					name="selectLength" size="1">
					<option value="greater">&gt;</option>
					<option value="less">&lt;</option>
				</select> <select name="lengthField" id="lengthField">
					<option value="0" selected><%=props.getProperty("unknown")%></option>
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
				</select> <%=props.getProperty("meters")%></td>
			</tr>
<tr>
				<td>
				<p><strong><%=props.getProperty("locationID")%>:</strong> <span class="para"><a href="<%=CommonConfiguration.getWikiLocation()%>locationID"
					target="_blank"><img src="images/information_icon_svg.gif"
					alt="Help" border="0" align="absmiddle" /></a></span> <br> 
					(<em><%=props.getProperty("locationIDExample")%></em>)</p>

				<%
				ArrayList<String> locIDs = myShepherd.getAllLocationIDs();
				int totalLocIDs=locIDs.size();

				
				if(totalLocIDs>0){
				%>
				
				<select multiple size="<%=(totalLocIDs+1) %>" name="locationCodeField" id="locationCodeField">
					<option value="None"></option>
				<% 
			  	for(int n=0;n<totalLocIDs;n++) {
					String word=locIDs.get(n);
					if(!word.equals("")){
				%>
					<option value="<%=word%>"><%=word%></option>
				<%}
					}
				%>
				</select>
				<%
				}
				else{
					%>
					<p><em><%=props.getProperty("noLocationIDs")%></em></p>
					<%
				}
				%>
				
				</td>
			</tr>
			<tr>
				<td>
				<p><strong><%=props.getProperty("alternateID")%>:</strong> <em> <input
					name="alternateIDField" type="text" id="alternateIDField" size="25"
					maxlength="100"> <span class="para"><a
					href="<%=CommonConfiguration.getWikiLocation()%>alternateID"
					target="_blank"><img src="images/information_icon_svg.gif"
					alt="Help" width="15" height="15" border="0" align="absmiddle" /></a></span>
				<br></em></p>
				</td>
			</tr>

			<tr>
				<td><strong><%=props.getProperty("sightingDates")%>:</strong></td>
			</tr>
			<tr>
				<td>
				<table width="720">
					<tr>
						<td width="670"><label> <em> </em><em><%=props.getProperty("month")%></em> <em>
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
						</select> <%=props.getProperty("year")%></em> 
						
						<select name="year1" id="year1">
							<% for(int q=firstYear;q<=nowYear;q++) { %>
							<option value="<%=q%>" 
							
							<%
							if(q==firstYear){
							%>
								selected
							<%
							}
							%>
							><%=q%></option>

							<% } %>
						</select>
						
						&nbsp;to <em>&nbsp;</em><em><%=props.getProperty("month")%></em> <em> <select
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
						</select> <%=props.getProperty("year")%></em> 
						
						<select name="year2" id="year2">
							<% for(int q=nowYear;q>=firstYear;q--) { %>
							<option value="<%=q%>" 
							
							<%
							if(q==nowYear){
							%>
								selected
							<%
							}
							%>
							><%=q%></option>

							<% } %>
						</select>
						
						 </label></td>
					</tr>
				</table>
				</td>
			</tr>

			<tr>
				<td>
		
				<p><em> <input name="submitSearch" type="submit"
					id="submitSearch" value="<%=props.getProperty("goSearch")%>"></em>
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


