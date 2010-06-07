<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java"
	import="org.ecocean.servlet.*,java.util.ArrayList,java.util.GregorianCalendar,java.util.StringTokenizer,org.ecocean.*,java.text.DecimalFormat, javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Enumeration, java.net.URL, java.net.URLConnection, java.io.InputStream, java.io.FileInputStream, java.io.File, java.util.Iterator,java.util.Properties"%>
<%@ taglib uri="di" prefix="di"%>

<%!

	//shepherd must have an open trasnaction when passed in
    public String getNextIndividualNumber(Encounter enc, Shepherd myShepherd) {
        String returnString="";
		try {
			String lcode=enc.getLocationCode();
			if((lcode!=null)&&(!lcode.equals(""))){
				
				//let's see if we can find a string in the mapping properties file
				Properties props=new Properties();
				//set up the file input stream
				props.load(getClass().getResourceAsStream("/bundles/en/newSharkNumbers.properties"));
				
				
				//let's see if the property is defined
				if(props.getProperty(lcode)!=null){
					returnString=props.getProperty(lcode);
					
					
					int startNum=1;
					boolean keepIterating=true;
					
					//let's iterate through the potential individuals
					while(keepIterating){
						String startNumString=Integer.toString(startNum);
						if(startNumString.length()<3){
							while(startNumString.length()<3){startNumString="0"+startNumString;}
						}
						String compositeString=returnString+startNumString;
						if(!myShepherd.isMarkedIndividual(compositeString)){
							keepIterating=false;
							returnString=compositeString;
						}
						else{startNum++;}
					
					}
					return returnString;
					
				}
				
			
			
			}			
			return returnString;
        } 
		catch (Exception e) {
			e.printStackTrace();
			return returnString;
		}
    }

%>

<%

GregorianCalendar cal=new GregorianCalendar();
int nowYear=cal.get(1);



//handle some cache-related security
response.setHeader("Cache-Control","no-cache"); //Forces caches to obtain a new copy of the page from the origin server
response.setHeader("Cache-Control","no-store"); //Directs caches not to store the page under any circumstance
response.setDateHeader("Expires", 0); //Causes the proxy cache to see the page as "stale"
response.setHeader("Pragma","no-cache"); //HTTP 1.0 backward compatibility 

//gps decimal formatter
DecimalFormat gpsFormat = new DecimalFormat("###.####");

//handle translation
String langCode="en";
	
	//check what language is requested
	if(request.getParameter("langCode")!=null){
		if(request.getParameter("langCode").equals("fr")) {langCode="fr";}
		if(request.getParameter("langCode").equals("de")) {langCode="de";}
		if(request.getParameter("langCode").equals("es")) {langCode="es";}
	}

String num=request.getParameter("number").replaceAll("\\+","").trim();


Shepherd myShepherd=new Shepherd();
Extent allKeywords=myShepherd.getPM().getExtent(Keyword.class,true);		
Query kwQuery=myShepherd.getPM().newQuery(allKeywords);
boolean proceed=true;
boolean haveRendered=false;


%>

<html>
<head>
<title>Encounter# <%=num%></title>
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
<style type="text/css">
<!--
.style1 {
	color: #000066
}

.style2 {
	color: #000000;
	font-size: small;
}

.style3 {
	font-weight: bold
}

.style4 {
	color: #000000
}

table.adopter {
	border-width: 1px 1px 1px 1px;
	border-spacing: 0px;
	border-style: solid solid solid solid;
	border-color: black black black black;
	border-collapse: separate;
	background-color: white;
}

table.adopter td {
	border-width: 1px 1px 1px 1px;
	padding: 3px 3px 3px 3px;
	border-style: none none none none;
	border-color: gray gray gray gray;
	background-color: white;
	-moz-border-radius: 0px 0px 0px 0px;
	font-size: 12px;
	color: #330099;
}

table.adopter td.name {
	font-size: 12px;
	text-align: center;
}

table.adopter td.image {
	padding: 0px 0px 0px 0px;
}
-->
</style>
</head>

<body <%if(request.getParameter("noscript")==null){%>
	onload="initialize()" onunload="GUnload()" <%}%>>
<div id="wrapper">
<div id="page">
<jsp:include page="../header.jsp" flush="true">
	<jsp:param name="isResearcher"
		value="<%=request.isUserInRole("researcher")%>" />
	<jsp:param name="isManager"
		value="<%=request.isUserInRole("manager")%>" />
	<jsp:param name="isReviewer"
		value="<%=request.isUserInRole("reviewer")%>" />
	<jsp:param name="isAdmin" value="<%=request.isUserInRole("admin")%>" />
	<jsp:param name="langCode" value="<%=langCode%>" />
</jsp:include>
<div id="main">
<%
myShepherd.beginDBTransaction();



if (myShepherd.isEncounter(num)) {

try{

Encounter enc=myShepherd.getEncounter(num);
String livingStatus="";
if(enc.getLivingStatus().equals("dead")){livingStatus=" (deceased)";}
int numImages=enc.getAdditionalImageNames().size();

boolean isOwner = ServletUtilities.isUserAuthorizedForEncounter(enc, request);



		String loggedIn="false";
		if (session.getValue("logged")!=null) {
			Object OBJloggedIn=session.getValue("logged");
			loggedIn=(String)OBJloggedIn;}
%> <%if (enc.wasRejected()) {%>
<table width="810">
	<tr>
		<td bgcolor="#0033CC">
		<p><font color="#FFFFFF" size="4">Unidentifiable Encounter
		Number: <%=num%><%=livingStatus %> </font>
		</td>
	</tr>
</table>
</p>
<%

} else if(!enc.approved){%>
<table width="810">
	<tr>
		<td bgcolor="#CC6600">
		<p><font color="#FFFFFF" size="4">UNAPPROVED Encounter
		Number: <%=num%><%=livingStatus %></font>
		</td>
	</tr>
</table>
<%} else {
%>
<p><font size="4"><strong>Encounter Number</strong>: <%=num%><%=livingStatus %>
</font></p>
<%}%> <%
	if (enc.isAssignedToMarkedIndividual().equals("Unassigned")) {
%>
<p class="para"><img align="absmiddle" src="../images/tag_big.gif" width="50px" height="*">
Identified as: <%=enc.isAssignedToMarkedIndividual()%> <%
 	if(isOwner) {
 %><font size="-1">[<a
	href="encounter.jsp?number=<%=num%>&edit=manageShark">edit</a>]</font>
<%
 	}
 %>
</p>
<%
	} else {
%>
<p class="para"><img align="absmiddle" src="../images/tag_big.gif" width="50px" height="*">
Identified as: <a
	href="../individuals.jsp?langCode=<%=langCode%>&number=<%=enc.isAssignedToMarkedIndividual()%><%if(request.getParameter("noscript")!=null){%>&noscript=true<%}%>"><%=enc.isAssignedToMarkedIndividual()%></a></font>
<%
 	if(isOwner) {
 %>[<a href="encounter.jsp?number=<%=num%>&edit=manageShark">edit</a>]<%
 	}
 	if (isOwner) {
 %><br> <img align="absmiddle"
	src="../images/Crystal_Clear_app_matchedBy.gif"> Matched by: <%=enc.getMatchedBy()%>
<%
 	if(isOwner) {
 %>[<a
	href="encounter.jsp?number=<%=num%>&edit=manageMatchedBy#matchedBy">edit</a>]<%
 	}
 %> <%
	}
%>
</p>
<%
	} //end else

%>
<p class="para"><img align="absmiddle" src="../images/life_icon.gif">
Status: <%=enc.getLivingStatus()%> <%
 	if(isOwner) {
 %>[<a
	href="encounter.jsp?number=<%=num%>&edit=livingStatus#livingStatus">edit</a>]<%
 	}
 %>
</p>


<p class="para"><img align="absmiddle"
	src="../images/alternateid.gif"> Alternate ID: <%=enc.getAlternateID()%>
<%
 	if(isOwner) {
 %>[<a href="encounter.jsp?number=<%=num%>&edit=alternateid#alternateid">edit</a>]<%
 	}
 %>
</p>
<%


if((loggedIn.equals("true"))&&(enc.getSubmitterID()!=null)) {
%>
<p class="para"><img align="absmiddle"
	src="../images/Crystal_Clear_app_Login_Manager.gif"> Assigned to</font><font
	size="-1"> Library user: <%=enc.getSubmitterID()%> <%
 	if(request.isUserInRole("manager")) {
 %><font size="-1">[<a
	href="encounter.jsp?number=<%=num%>&edit=user#user">edit</a>]</font>
<%
 	}
 %>
</p>
<%
	}
%>

<table width="720" border="0" cellpadding="3" cellspacing="5">
	<tr>
		<td width="170" align="left" valign="top" bgcolor="#99CCFF">
		<%
 	//start deciding menu bar contents

 //if not logged in
 if((loggedIn.equals("false"))) {
 %>
		<p class="para">If you have an account, you can <a
			href="../welcome.jsp?reflect=<%=request.getRequestURI()%>?number=<%=num%>">login
		here</a>.</p>

		<%
			} 
		//if logged in, limit commands displayed			
		else {
		%>
		<p align="center" class="para"><font color="#000000" size="+1"><strong>
		Action <font color="#000000" size="+1"><strong><img
			src="../images/Crystal_Clear_app_advancedsettings.gif" width="29"
			height="29" align="absmiddle" /></strong></font> Edit</strong></font><br> <br> <em><font
			size="-1">This area contains commands currently available to
		you or edit commands that you have selected from the right.</font></em>
		</p>
		<%
			//manager-level commands
				if((request.isUserInRole("manager"))||(isOwner)) {
				
			
			//approve new encounter
			if ((!enc.approved)&&(isOwner)) {
		%>
		<table width="175" border="1" cellpadding="1" cellspacing="0"
			bordercolor="#000000" bgcolor="#CECFCE">
			<tr>
				<td height="30" class="para">
				<p><font color="#990000"><font color="#990000">&nbsp;<img
					align="absmiddle" src="../images/check_green.png" /></font> <strong>Approve
				encounter</strong></font></p>
				<p><font color="#990000"><font color="#990000"><a
					href="<%=CommonConfiguration.getWikiLocation()%>approving_an_encounter"
					target="_blank">&nbsp;<img
					src="../images/information_icon_svg.gif" alt="Help" border="0"
					align="absmiddle" /></a></font> </font><span class="style2">Approval
				checklist</span></p>
				</td>
			</tr>
			<tr>
				<td>
				<form name="approve_form" method="post" action="../EncounterApprove">
				<input name="action" type="hidden" id="action" value="approve">
				<input name="number" type="hidden"
					value=<%=request.getParameter("number")%>> <input
					name="approve" type="submit" id="approve" value="Approve"></form>
				</td>
			</tr>
		</table>
		<br> <%
				}
				//set location code
				if((isOwner)&&(request.getParameter("edit")!=null)&&(request.getParameter("edit").equals("loccode"))){
			%> <a name="loccode"><br>
		<table width="150" border="1" cellpadding="1" cellspacing="0"
			bordercolor="#000000" bgcolor="#CCCCCC">
			<tr>
				<td align="left" valign="top" class="para"><strong><font
					color="#990000">Set location ID:</font></strong></td>
			</tr>
			<tr>
				<td align="left" valign="top">
				<form name="addLocCode" action="/EncounterSetLocationID"
					method="post"><input name="code" type="text" size="5"
					maxlength="5"> <input name="number" type="hidden"
					value=<%=num%>> <input name="action" type="hidden"
					value="addLocCode"> <input name="Set Location ID"
					type="submit" id="Add" value="Set Location ID"></form>
				</td>
			</tr>
		</table>
		</a><br> <%
			}
				
				//set alternateid
			if((isOwner)&&(request.getParameter("edit")!=null)&&(request.getParameter("edit").equals("alternateid"))){
		%> <a name="alternateid"><br>
		<table width="150" border="1" cellpadding="1" cellspacing="0"
			bordercolor="#000000" bgcolor="#CCCCCC">
			<tr>
				<td align="left" valign="top" class="para"><strong><font
					color="#990000">Set alternate ID:</font></strong></td>
			</tr>
			<tr>
				<td align="left" valign="top">
				<form name="setAltID" action="../EncounterSetAlternateID"
					method="post"><input name="alternateid" type="text"
					size="10" maxlength="50"> <input name="encounter"
					type="hidden" value=<%=num%>> <input name="Set"
					type="submit" id="Set" value="Set"></form>
				</td>
			</tr>
		</table>
		</a><br> <%
			}
				
				
				//set informothers
			if((isOwner)&&(request.getParameter("edit")!=null)&&(request.getParameter("edit").equals("others"))){
		%> <a name="others"><br>
		<table width="150" border="1" cellpadding="1" cellspacing="0"
			bordercolor="#000000" bgcolor="#CCCCCC">
			<tr>
				<td align="left" valign="top" class="para"><strong><font
					color="#990000">Set others to inform:</font></strong><br> Separate
				multiple email addresses with a comma. 
				</td>
			</tr>
			<tr>
				<td align="left" valign="top">
				<form name="setOthers" action="../EncounterSetInformOthers"
					method="post"><input name="encounter" type="hidden"
					value=<%=num%>> <input name="informothers" type="text"
					size="28" <%if(enc.getInformOthers()!=null){%>
					value="<%=enc.getInformOthers().trim()%>" <%}%> maxlength="1000">
				<br> <input name="Set" type="submit" id="Set" value="Set"></form>
				</td>
			</tr>
		</table>
		</a><br> <%
			}
				
				//set matchedBy type
			if((isOwner)&&(request.getParameter("edit")!=null)&&(request.getParameter("edit").equals("manageMatchedBy"))){
		%> <a name="matchedBy">
		<table width="150" border="1" cellpadding="1" cellspacing="0"
			bordercolor="#000000" bgcolor="#CCCCCC">
			<tr>
				<td align="left" valign="top" class="para"><font
					color="#990000"><img align="absmiddle"
					src="../images/Crystal_Clear_app_matchedBy.gif" /> <strong>Matched
				by:</strong></font></td>
			</tr>
			<tr>
				<td align="left" valign="top">
				<form name="setMBT" action="../EncounterSetMatchedBy" method="post">
				<select name="matchedBy" id="matchedBy">
					<option value="Unmatched first encounter">Unmatched first
					encounter</option>
					<option value="Visual inspection">Visual inspection</option>
					<option value="Pattern match" selected>Pattern match</option>
				</select> <input name="number" type="hidden" value=<%=num%>> <input
					name="setMB" type="submit" id="setMB" value="Set"></form>
				</td>
			</tr>
		</table>
		</a><br> <%
			}
				
				
			  //add this encounter to a MarkedIndividual object
			  if ((isOwner)&&(request.getParameter("edit")!=null)&&(request.getParameter("edit").equals("manageShark"))) {
		%> <a name="manageShark">
		<table width="150" border="1" cellpadding="1" cellspacing="0"
			bordercolor="#000000" bgcolor="#CCCCCC">
			<tr>
				<td align="left" valign="top" class="para"><font
					color="#990000"><img align="absmiddle"
					src="../images/tag_big.gif" width="50px" height="*" /><br></br>
				<strong>Add to marked individual:</strong></font></td>
			</tr>
			<tr>
				<td align="left" valign="top">
				<form name="add2shark" action="../IndividualAddEncounter"
					method="post">Individual: <input name="individual"
					type="text" size="10" maxlength="50"><br> Matched by:<br>
				<select name="matchType" id="matchType">
					<option value="Unmatched first encounter">Unmatched first
					encounter</option>
					<option value="Visual inspection">Visual inspection</option>
					<option value="Pattern match" selected>Pattern match</option>
				</select> <br> <input name="noemail" type="checkbox" value="noemail">
				suppress e-mail/RSS<br> <input name="number" type="hidden"
					value=<%=num%>> <input name="action" type="hidden"
					value="add"> <input name="Add" type="submit" id="Add"
					value="Add"></form>
				</td>
			</tr>
		</table>
		</a><br> <%
		  	}
		  	  //Remove from MarkedIndividual if not unassigned
		  	  if((!enc.isAssignedToMarkedIndividual().equals("Unassigned"))&&isOwner&&(request.getParameter("edit")!=null)&&(request.getParameter("edit").equals("manageShark"))) {
		  %>
		<table width="150" border="1" cellpadding="1" cellspacing="0"
			bordercolor="#000000" bgcolor="#CCCCCC">
			<tr>
				<td align="left" valign="top" class="para"><font
					color="#990000">
				<table>
					<tr>
						<td><font color="#990000"><img align="absmiddle"
							src="../images/cancel.gif" /></font></td>
						<td><strong> Remove from marked individual</strong></td>
					</tr>
				</table>
				</font></td>
			</tr>
			<tr>
				<td align="left" valign="top">
				<form action="../IndividualRemoveEncounter" method="post"
					name="removeShark"><input name="number" type="hidden"
					value=<%=num%>> <input name="action" type="hidden"
					value="remove"> <input type="submit" name="Submit"
					value="Remove"></form>
				</td>
			</tr>
		</table>
		<br> <%
      	}
      	  //create new MarkedIndividual with name
      	  if(isOwner&&(request.getParameter("edit")!=null)&&(request.getParameter("edit").equals("manageShark"))){
      %>
		<table width="150" border="1" cellpadding="1" cellspacing="0"
			bordercolor="#000000" bgcolor="#CCCCCC">
			<tr>
				<td align="left" valign="top" class="para"><font
					color="#990000"><img align="absmiddle"
					src="../images/tag_big.gif" width="50px" height="*" /> <strong>Create marked
				individual:</strong></font></td>
			</tr>
			<tr>
				<td align="left" valign="top">
				<form name="createShark" method="post" action="../IndividualCreate">
				<input name="number" type="hidden" value=<%=num%>> <input
					name="action" type="hidden" value="create"> <input
					name="individual" type="text" id="individual" size="10"
					maxlength="50"
					value="<%=getNextIndividualNumber(enc, myShepherd)%>"><br>
				<%
						if(request.isUserInRole("manager")){
					%> <input name="noemail" type="checkbox" value="noemail">
				suppress e-mail<br> <%
						}
					%> <input name="Create" type="submit" id="Create" value="Create"></form>
				</td>
			</tr>
		</table>
		<br> <%
			}
				if((request.getParameter("edit")!=null)&&(request.getParameter("edit").equals("gps"))){
		%> <a name="gps">
		<table width="150" border="1" cellpadding="1" cellspacing="0"
			bordercolor="#000000" bgcolor="#CCCCCC">
			<tr>
				<td align="left" valign="top" class="para"><span class="style3"><font
					color="#990000">Reset GPS:</font></span><br> <em><font size="-1">Note</font></em><font
					size="-1">: Reset to all blanks if unknown. </font>
				</td>
			</tr>
			<tr>
				<td>
				<form name="resetGPSform" method="post" action="../EncounterSetGPS">
				<input name="action" type="hidden" value="resetGPS">
				<p><strong>Latitude:</strong><br> <select name="lat"
					id="lat">
					<option value="" selected="selected"></option>
					<%
           		for(int gps1=0;gps1<91;gps1++){
           	%>
					<option value="<%=gps1%>"><%=gps1%></option>
					<%
				}
			%>
				</select> <font size="+1">&deg;</font> <select name="gpsLatitudeMinutes"
					id="gpsLatitudeMinutes">
					<option value="" selected="selected"></option>
					<%
            	for(int gps1=0;gps1<60;gps1++){
            %>
					<option value="<%=gps1%>"><%=gps1%></option>
					<%
				}
			%>
				</select> <font size="+1">'</font> <br> <select
					name="gpsLatitudeSeconds" id="gpsLatitudeSeconds">
					<option value="" selected="selected"></option>
					<option value="0">0</option>
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
					<option value="31">31</option>
					<option value="32">32</option>
					<option value="33">33</option>
					<option value="34">34</option>
					<option value="35">35</option>
					<option value="36">36</option>
					<option value="37">37</option>
					<option value="38">38</option>
					<option value="39">39</option>
					<option value="40">40</option>
					<option value="41">41</option>
					<option value="42">42</option>
					<option value="43">43</option>
					<option value="44">44</option>
					<option value="45">45</option>
					<option value="46">46</option>
					<option value="47">47</option>
					<option value="48">48</option>
					<option value="49">49</option>
					<option value="50">50</option>
					<option value="51">51</option>
					<option value="52">52</option>
					<option value="53">53</option>
					<option value="54">54</option>
					<option value="55">55</option>
					<option value="56">56</option>
					<option value="57">57</option>
					<option value="58">58</option>
					<option value="59">59</option>
				</select> <font size="+1">&quot;</font> <select name="latDirection"
					id="latDirection">
					<option value="South" selected>South</option>
					<option value="North">North</option>
				</select><br> <strong>Longitude:</strong><br> <select
					name="longitude" id="longitude">
					<option value="" selected="selected"></option>
					<%
            	for(int gps1=0;gps1<181;gps1++){
            %>
					<option value="<%=gps1%>"><%=gps1%></option>
					<%
				}
			%>
				</select> <font size="+1">&deg;</font> <select name="gpsLongitudeMinutes"
					id="gpsLongitudeMinutes">
					<option value="" selected="selected"></option>
					<%
		  		for(int gps1=0;gps1<60;gps1++){
		  	%>
					<option value="<%=gps1%>"><%=gps1%></option>
					<%
				}
			%>
				</select> <font size="+1">'</font> <br> <select
					name="gpsLongitudeSeconds" id="gpsLongitudeSeconds">
					<option value="" selected="selected"></option>
					<option value="0">0</option>
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
					<option value="31">31</option>
					<option value="32">32</option>
					<option value="33">33</option>
					<option value="34">34</option>
					<option value="35">35</option>
					<option value="36">36</option>
					<option value="37">37</option>
					<option value="38">38</option>
					<option value="39">39</option>
					<option value="40">40</option>
					<option value="41">41</option>
					<option value="42">42</option>
					<option value="43">43</option>
					<option value="44">44</option>
					<option value="45">45</option>
					<option value="46">46</option>
					<option value="47">47</option>
					<option value="48">48</option>
					<option value="49">49</option>
					<option value="50">50</option>
					<option value="51">51</option>
					<option value="52">52</option>
					<option value="53">53</option>
					<option value="54">54</option>
					<option value="55">55</option>
					<option value="56">56</option>
					<option value="57">57</option>
					<option value="58">58</option>
					<option value="59">59</option>
				</select> <font size="+1">&quot;</font> <select name="longDirection"
					id="longDirection">
					<option value="West" selected>West</option>
					<option value="East">East</option>
				</select> <input name="number" type="hidden" value=<%=num%>> <input
					name="setGPSbutton" type="submit" id="setGPSbutton" value="Set GPS">
				</p></form>
				</td>
			</tr>
		</table>
		</a><br> <%
			}
				//set location for sighting
			if(isOwner&&(request.getParameter("edit")!=null)&&(request.getParameter("edit").equals("location"))){
		%> <a name="location">
		<table width="150" border="1" cellpadding="1" cellspacing="0"
			bordercolor="#000000" bgcolor="#CCCCCC">
			<tr>
				<td align="left" valign="top" class="para"><strong><font
					color="#990000">Set location:</font></strong></td>
			</tr>
			<tr>
				<td align="left" valign="top">
				<form name="setLocation" action="../EncounterSetLocation"
					method="post"><textarea name="location" size="15"><%=enc.getLocation()%></textarea>
				<input name="number" type="hidden" value=<%=num%>> <input
					name="action" type="hidden" value="setLocation"> <input
					name="Add" type="submit" id="Add" value="Set Location"></form>
				</td>
			</tr>
		</table>
		</a><br> <%
			}
				
				//update submitted comments for sighting
			if(isOwner&&(request.getParameter("edit")!=null)&&(request.getParameter("edit").equals("comments"))){
		%> <a name="comments">
		<table width="150" border="1" cellpadding="1" cellspacing="0"
			bordercolor="#000000" bgcolor="#CCCCCC">
			<tr>
				<td align="left" valign="top" class="para"><strong><font
					color="#990000">Edit submitted comments:</font></strong></td>
			</tr>
			<tr>
				<td align="left" valign="top">
				<form name="setComments" action="../EncounterSetOccurrenceRemarks"
					method="post"><textarea name="fixComment" size="15"><%=enc.getComments()%></textarea>
				<input name="number" type="hidden" value=<%=num%>> <input
					name="action" type="hidden" value="editComments"> <input
					name="EditComm" type="submit" id="EditComm" value="Submit Edit"></form>
				</td>
			</tr>
		</table>
		</a><br> <%
			}
				//reset contact info
			if(isOwner&&(request.getParameter("edit")!=null)&&(request.getParameter("edit").equals("contact"))){
		%> <a name="contact">
		<table width="150" border="1" cellpadding="1" cellspacing="0"
			bordercolor="#000000" bgcolor="#CCCCCC">
			<tr>
				<td align="left" valign="top" class="para"><strong><font
					color="#990000">Edit contact information:</font></strong></td>
			</tr>
			<tr>
				<td></td>
			</tr>
			<tr>
				<td align="left" valign="top">
				<form name="setPersonalDetails"
					action="../EncounterSetSubmitterPhotographerContactInfo"
					method="post"><label> <input type="radio"
					name="contact" value="submitter">Submitter</label> <br><label>
				<input type="radio" name="contact" value="photographer">Photographer</label>
				<br> Name<br><input name="name" type="text" size="20"
					maxlength="100"> Email<br><input name="email"
					type="text" size="20"> Phone<br><input name="phone"
					type="text" size="20" maxlength="100"> Address<br><input
					name="address" type="text" size="20" maxlength="100"> <input
					name="number" type="hidden" value=<%=num%>> <input
					name="action" type="hidden" value="editcontact"> <input
					name="EditContact" type="submit" id="EditContact" value="Update">
				</form>
				</td>
			</tr>
		</table>
		</a><br> <%
							}
						//--------------------------
						//edit sex reported for sighting	
		if((request.getParameter("edit")!=null)&&(request.getParameter("edit").equals("sex"))){
						%> <a name="sex"></a>
		<table width="150" border="1" cellpadding="1" cellspacing="0"
			bordercolor="#000000" bgcolor="#CCCCCC">
			<tr>
				<td align="left" valign="top" class="para"><strong><font
					color="#990000">Reset sex:</font></strong></td>
			</tr>
			<tr>
				<td align="left" valign="top">
				<form name="setxencshark" action="../EncounterSetSex" method="post">
				<select name="selectSex" size="1" id="selectSex">
					<option value="unsure" selected>unsure</option>
					<option value="male">male</option>
					<option value="female">female</option>
				</select> <input name="number" type="hidden" value="<%=num%>" id="number">
				<input name="action" type="hidden" value="setEncounterSex">
				<input name="Add" type="submit" id="Add" value="Set Sex"></form>
				</td>
			</tr>
		</table>
		<br> <%
			}
			
		if((request.getParameter("edit")!=null)&&(request.getParameter("edit").equals("livingStatus"))){
						%> <a name="livingStatus"></a>
		<table width="150" border="1" cellpadding="1" cellspacing="0"
			bordercolor="#000000" bgcolor="#CCCCCC">
			<tr>
				<td align="left" valign="top" class="para"><strong><font
					color="#990000"><img align="absmiddle"
					src="../images/life_icon.gif"> Reset status:</font></strong></td>
			</tr>
			<tr>
				<td align="left" valign="top">
				<form name="livingStatusForm" action="../EncounterSetLivingStatus"
					method="post"><select name="livingStatus" id="livingStatus">
					<option value="alive" selected>alive</option>
					<option value="dead">dead</option>
				</select> <input name="encounter" type="hidden" value="<%=num%>" id="number">
				<input name="Add" type="submit" id="Add" value="Set Status"></form>
				</td>
			</tr>
		</table>
		<br> <%
			}
			
			
			
				//reset encounter date
				if((request.getParameter("edit")!=null)&&(request.getParameter("edit").equals("date"))){
		%> <a name="date">
		<table width="150" border="1" cellpadding="1" cellspacing="0"
			bordercolor="#000000" bgcolor="#CCCCCC">
			<tr>
				<td align="left" valign="top" class="para"><strong><font
					color="#990000">Reset encounter date:</font></strong></td>
			</tr>
			<tr>
				<td align="left" valign="top">
				<form name="setxencshark" action="../EncounterResetDate" method="post">
				<em>Day</em> <select name="day" id="day">
					<option value="0">?</option>
					<%
									for(int pday=1;pday<32;pday++) {
								%>
					<option value="<%=pday%>"><%=pday%></option>
					<%
      								}
      							%>
				</select><br> <em>&nbsp;Month</em> <select name="month" id="month">
					<option value="-1">?</option>
					<%
      								for(int pmonth=1;pmonth<13;pmonth++) {
      							%>
					<option value="<%=pmonth%>"><%=pmonth%></option>
					<%
      								}
      							%>
				</select><br> <em>&nbsp;Year</em> <select name="year" id="year">
					<option value="-1">?</option>

					<%
																	for(int pyear=nowYear;pyear>(nowYear-50);pyear--) {
																%>
					<option value="<%=pyear%>"><%=pyear%></option>
					<%
      								}
      							%>
				</select><br> <em>&nbsp;Hour</em> <select name="hour" id="hour">
					<option value="-1" selected>?</option>
					<option value="6">6 am</option>
					<option value="7">7 am</option>
					<option value="8">8 am</option>
					<option value="9">9 am</option>
					<option value="10">10 am</option>
					<option value="11">11 am</option>
					<option value="12">12 pm</option>
					<option value="13">1 pm</option>
					<option value="14">2 pm</option>
					<option value="15">3 pm</option>
					<option value="16">4 pm</option>
					<option value="17">5 pm</option>
					<option value="18">6 pm</option>
					<option value="19">7 pm</option>
					<option value="20">8 pm</option>
				</select><br> <em>&nbsp;Minutes</em> <select name="minutes" id="minutes">
					<option value="00" selected>:00</option>
					<option value="15">:15</option>
					<option value="30">:30</option>
					<option value="45">:45</option>
				</select><br> <input name="number" type="hidden" value="<%=num%>"
					id="number"> <input name="action" type="hidden"
					value="changeEncounterDate"> <input name="AddDate"
					type="submit" id="AddDate" value="Set Date">
				</form>
				</td>
			</tr>
		</table>
		</a><br> <%
			}
				
				//reset size reported for sighting
				if((request.getParameter("edit")!=null)&&(request.getParameter("edit").equals("size"))){
		%> <a name="size">
		<table width="150" border="1" cellpadding="1" cellspacing="0"
			bordercolor="#000000" bgcolor="#CCCCCC">
			<tr>
				<td align="left" valign="top" class="para"><strong><font
					color="#990000">Reset reported size:</font></strong></td>
			</tr>
			<tr>
				<td align="left" valign="top">
				<form name="setencsize" action="../EncounterSetSize" method="post">
				<input name="lengthField" type="text" id="lengthField" size="8"
					maxlength="8"> Meters<br> <em>Use 0 if unknown</em><br>
				<input name="lengthUnits" type="hidden" id="lengthUnits"
					value="Meters"> <select name="guessList" id="guessList">
					<option value="directly measured">directly measured</option>
					<option value="submitter's guess">personal guess</option>
					<option value="guide/researcher's guess" selected>guess of
					guide/researcher</option>
				</select> <input name="number" type="hidden" value="<%=num%>" id="number">
				<input name="action" type="hidden" value="setEncounterSize">
				<input name="Add" type="submit" id="Add" value="Set Size"></form>
				</td>
			</tr>
		</table>
		</a><br> <%
			}

				//reset water depth
				if((request.getParameter("edit")!=null)&&(request.getParameter("edit").equals("depth"))){
		%> <a name="depth">
		<table width="150" border="1" cellpadding="1" cellspacing="0"
			bordercolor="#000000" bgcolor="#CCCCCC">
			<tr>
				<td align="left" valign="top" class="para"><strong><font
					color="#990000">Reset water depth:</font></strong></td>
			</tr>
			<tr>
				<td align="left" valign="top">
				<form name="setencdepth" action="../EncounterSetMaximumDepth"
					method="post"><input name="depth" type="text" id="depth"
					size="10"> Meters <input name="lengthUnits" type="hidden"
					id="lengthUnits" value="Meters"> <input name="number"
					type="hidden" value="<%=num%>" id="number"> <input
					name="action" type="hidden" value="setEncounterDepth"> <input
					name="AddDepth" type="submit" id="AddDepth" value="Set Depth"></form>
				</td>
			</tr>
		</table>
		</a><br> <%
			}
			
			
				//reset elevation
		if((request.getParameter("edit")!=null)&&(request.getParameter("edit").equals("elevation"))){
		%> <a name="elevation">
		<table width="150" border="1" cellpadding="1" cellspacing="0"
			bordercolor="#000000" bgcolor="#CCCCCC">
			<tr>
				<td align="left" valign="top" class="para"><strong><font
					color="#990000">Reset elevation:</font></strong></td>
			</tr>
			<tr>
				<td align="left" valign="top">
				<form name="setencelev" action="../EncounterSetMaximumElevation" method="post">
						<input name="elevation" type="text" id="elevation" size="10"> Meters <input name="lengthUnits" type="hidden" id="lengthUnits" value="Meters"> 
						<input name="number" type="hidden" value="<%=num%>" id="number"> 
						<input name="action" type="hidden" value="setEncounterElevation"> 
						<input name="AddElev" type="submit" id="AddElev" value="Set Elevation">
					</form>
				</td>
			</tr>
		</table>
		</a><br /> <%
			}

		if((request.getParameter("edit")!=null)&&(request.getParameter("edit").equals("user"))){
		%> <a name="user">
		<table width="150" border="1" cellpadding="1" cellspacing="0"
			bordercolor="#000000" bgcolor="#CCCCCC">
			<tr>
				<td align="left" valign="top" class="para"><font
					color="#990000"><img align="absmiddle"
					src="../images/Crystal_Clear_app_Login_Manager.gif" /> <strong>Assign
				to user:</strong></font></td>
			</tr>
			<tr>
				<td align="left" valign="top">
				<form name="asetSubmID" action="../encounterSetSubmitterID"
					method="post"><input name="submitter" type="text" size="10"
					maxlength="50"> <input name="number" type="hidden"
					value=<%=num%>> <input name="Assign" type="submit"
					id="Assign" value="Assign"></form>
				</td>
			</tr>
		</table>
		</a><br /> <%
		}

	//reset scarring
			if((request.getParameter("edit")!=null)&&(request.getParameter("edit").equals("scar"))){
	%> <a name="scar">
		<table width="150" border="1" cellpadding="1" cellspacing="0"
			bordercolor="#000000" bgcolor="#CCCCCC">
			<tr>
				<td align="left" valign="top" class="para"><strong><font
					color="#990000">Reset scarring:</font></strong></td>
			</tr>
			<tr>
				<td align="left" valign="top">
				<form name="setencsize" action="../EncounterSetScarring" method="post">
				<select name="scars">
					<option value="0" selected>None</option>
					<option value="1">Tail (caudal) fin</option>
					<option value="2">1st dorsal fin</option>
					<option value="3">2nd dorsal fin</option>
					<option value="4">Left pectoral fin</option>
					<option value="5">Right pectoral fin</option>
					<option value="6">Head</option>
					<option value="7">Body</option>
				</select> <input name="number" type="hidden" value="<%=num%>" id="number">
				<input name="action" type="hidden" value="setScarring"> <input
					name="Add" type="submit" id="scar" value="Reset Scarring"></form>
				</td>
			</tr>
		</table>
		</a><br /> <%
			}

		//kick off a scan
				if (((enc.getNumSpots()>0)||(enc.getNumRightSpots()>0))&&isOwner) {
		%> <br />
		<table width="150" border="1" cellpadding="1" cellspacing="0"
			bgcolor="#CCCCCC">
			<tr>
				<td align="left" valign="top">
				<p class="para"><font color="#990000"><strong><img
					align="absmiddle" src="../images/Crystal_Clear_action_find.gif" />
				Find Pattern Match <a
					href="<%=CommonConfiguration.getWikiLocation()%>sharkgrid"
					target="_blank"><img src="../images/information_icon_svg.gif"
					alt="Help" border="0" align="absmiddle"></a><br />
				</strong> Scan entire database on the <a href="http://www.sharkgrid.org">sharkGrid</a>
				using the <a
					href="http://www.blackwell-synergy.com/doi/pdf/10.1111/j.1365-2664.2005.01117.x">Modified
				Groth</a> and <a
					href="http://www.blackwell-synergy.com/doi/abs/10.1111/j.1365-2664.2006.01273.x?journalCode=jpe">I3S</a>
				algorithms</font>
				<div id="formDiv">
				<form name="formSharkGrid" id="formSharkGrid" method="post"
					action="../scanTaskHandler"><input name="action" type="hidden"
					id="action" value="addTask"> <input name="encounterNumber"
					type="hidden" value=<%=num%>>

				<table width="200">
					<tr>
						<%
							  	if((enc.getSpots()!=null)&&(enc.getSpots().size()>0)) {
							  %>
						<td class="para"><label> <input name="rightSide"
							type="radio" value="false" checked> left-side</label></td>
						<%
									}
								%>
						<%
           				      	if((enc.getRightSpots()!=null)&&(enc.getRightSpots().size()>0)&&(enc.getSpots()!=null)&&(enc.getSpots().size()==0)) {
           				      %>
						<td class="para"><label> <input type="radio"
							name="rightSide" value="true" checked> right-side</label></td>
						<%
									}
													else if((enc.getRightSpots()!=null)&&(enc.getRightSpots().size()>0)) {
								%>
						<td class="para"><label> <input type="radio"
							name="rightSide" value="true"> right-side</label></td>
						<%
									}
								%>
					</tr>
				</table>

				<input name="writeThis" type="hidden" id="writeThis" value="true">
				<br /> <input name="scan" type="submit" id="scan"
					value="Start Scan"
					onclick="submitForm(document.getElementById('formSharkGrid'))">
				<input name="cutoff" type="hidden" value="0.02"></form>
				</p>
				</div>
				</td>
			</tr>
		</table>
		<br /> <!--
			<%}
			
			if (((enc.getNumSpots()>0)||(enc.getNumRightSpots()>0))&&isOwner) {%>
			<table width="150" border="1" cellpadding="1" cellspacing="0" bgcolor="#CCCCCC">
        		<tr>
          			<td align="left" valign="top">
		  				<form name="formSingleScan" method="get" action="appletScan.jsp">
              				<p class="para"><font color="#990000"><strong>Groth:</strong> Scan against one other encounter</font>
						    <table width="200">
							  <tr>
							    <%if(enc.getNumSpots()>0) {%>
								<td width="93" class="para"><label>
							      <input name="rightSide" type="radio" value="false" checked>
							      left-side</label></td>
						    <%}%>
							<%if(enc.getNumRightSpots()>0) {%>
							    <td width="95" class="para"><label>
							      <input type="radio" name="rightSide" value="true">
							      right-side</label></td>
								  <%}%>
						    </tr>
							
						  </table>
							
   						      <input name="singleComparison" type="text" size="15" maxlength="50">
						      <input name="scan" type="submit" id="scan" value="Scan">
  						      <input name="number" type="hidden" value=<%=num%>>
  						      <input name="R" type="hidden" value="8">
  						      <input name="Sizelim" type="hidden" value="0.85">
								<input name="cutoff" type="hidden" value="0.02">
  						      <input name="epsilon" type="hidden" value="0.01">
						      <input name="C" type="hidden" value="0.99">
						      <input name="maxTriangleRotation" type="hidden" value="10">
							  
				      </form>
		  			</td>
				</tr>
			</table><br />
			--> <%
	  	}


	  //reject encounter
	  if (isOwner) {
	  %>
		<table width="150" border="1" cellpadding="1" cellspacing="0"
			bordercolor="#000000" bgcolor="#CECFCE">
			<tr>
				<td>
				<p class="para"><font color="#990000"><img
					align="absmiddle" src="../images/cancel.gif" /> <strong>Reject
				encounter</strong></font></p>
				<p class="para"><font color="#990000"><strong><font
					color="#990000"><font color="#990000"><a
					href="<%=CommonConfiguration.getWikiLocation()%>approving_an_encounter"
					target="_blank"><img src="../images/information_icon_svg.gif"
					alt="Help" border="0" align="absmiddle" /></a> </font></font></strong></font><span class="style4">More
				info </span></p>
				</td>
			</tr>
			<tr>
				<td>
				<form name="reject_form" method="post" action="reject.jsp">
				<input name="action" type="hidden" id="action" value="reject">
				<input name="number" type="hidden" value=<%=num%>> <input
					name="reject" type="submit" id="reject" value="Reject"></form>
				</td>
			</tr>
		</table>
		<br /> <%
	  	}
	  	  if ((enc.wasRejected())&&isOwner) {
	  %>
		<table width="150" border="1" cellpadding="1" cellspacing="0"
			bordercolor="#000000" bgcolor="#CECFCE">
			<tr>
				<td class="para">
				<p><strong><font color="#990000">Reset as
				Identifiable</font></strong></p>
				<p><font color="#990000"><font color="#990000"><a
					href="<%=CommonConfiguration.getWikiLocation()%>approving_an_encounter"
					target="_blank"><img src="../images/information_icon_svg.gif"
					alt="Help" border="0" align="absmiddle" /></a></font> </font><span class="style4">More
				info </span></p>
				</td>
			</tr>
			<tr>
				<td>
				<form name="reacceptEncounter" method="post"
					action="../EncounterSetIdentifiable"><input name="action"
					type="hidden" id="action" value="reaccept"> <input
					name="number" type="hidden" value=<%=num%>> <input
					name="reject" type="submit" id="reject" value="Reaccept"></form>
				</td>
			</tr>
		</table>
		<br /> <%
	  	}
	  	  //remove spot data
	  	  if(isOwner) {
	  	  
	  	  if((request.getParameter("edit")!=null)&&(request.getParameter("edit").equals("rmSpots"))){
	  %> <a name="rmSpots">
		<table border="1" cellpadding="1" cellspacing="0"
			bordercolor="#000000" bgcolor="#CCCCCC">
			<tr>
				<td align="left" valign="top" class="para"><strong><font
					color="#990000">Remove spot data:</font></strong></td>
			</tr>
			<tr>
				<td align="left" valign="top">
				<center>
				<form name="removeSpots" method="post"
					action="../EncounterRemoveSpots">
				<table width="200">
					<tr>
						<%
					if(enc.getSpots().size()>0) {
				%>
						<td><label> <input name="rightSide" type="radio"
							value="false"> left-side</label></td>
						<%
           			}
           				if(enc.getRightSpots().size()>0) {
           		%>
						<td><label> <input type="radio" name="rightSide"
							value="true"> right-side</label></td>
						<%
						}
					%>
					</tr>
				</table>
				<input name="number" type="hidden" value=<%=num%>> <input
					name="action" type="hidden" value="removeSpots"> <input
					name="Remove3" type="submit" id="Remove3" value="Remove"></form>
				</center>
				</td>
			</tr>
		</table>
		</a><br /> <%
	  	}
	  	  if((request.getParameter("edit")!=null)&&(request.getParameter("edit").equals("spotImage"))){
	  %> <a name="spotImage">
		<table border="1" cellpadding="1" cellspacing="0"
			bordercolor="#000000" bgcolor="#CCCCCC">
			<tr>
				<td class="para">
				<form action="../EncounterAddSpotFile" method="post"
					enctype="multipart/form-data" name="addSpotsFile"><input
					name="action" type="hidden" value="fileadder" id="action">
				<input name="number" type="hidden" value="<%=num%>" id="shark">
				<font color="#990000"><strong><img align="absmiddle"
					src="../images/upload_small.gif" /></strong> <strong>Set spot
				image file:</strong></font><br /> <label><input name="rightSide"
					type="radio" value="false"> left</label><br /> <label><input
					name="rightSide" type="radio" value="true"> right</label><br />
				<br /> <input name="file2add" type="file" size="15"><br />
				<input name="addtlFile" type="submit" id="addtlFile"
					value="Upload spot image"></form>
				</td>
			</tr>
		</table>
		</a><br /> <%
	  	  	}
	  	  %>
		<table border="1" cellpadding="2" cellspacing="0"
			bordercolor="#000000" bgcolor="#CCCCCC">
			<tr>
				<td align="left" valign="top" class="para"><font
					color="#990000"><font color="#990000"><img
					align="absmiddle" src="../images/thumbnail_image.gif" /></font> <strong>Reset
				thumbnail</strong>&nbsp;</font></td>
			</tr>
			<tr>
				<td align="left">
				<form action="../resetThumbnail.jsp" method="get"
					enctype="multipart/form-data" name="resetThumbnail"><input
					name="number" type="hidden" value="<%=num%>" id="numreset"><br />
				Use image: <select name="imageNum">
					<%
					for (int rmi2=1;rmi2<=numImages;rmi2++){
				%>
					<option value="<%=rmi2%>"><%=rmi2%></option>
					<%
					}
				%>
				</select><br /> <input name="resetSubmit" type="submit" id="resetSubmit"
					value="Reset Thumbnail"></form>
				</td>
			</tr>
		</table>
		<br /> <a name="tapirlink">
		<table width="175" border="1" cellpadding="1" cellspacing="0"
			bordercolor="#000000" bgcolor="#CECFCE">
			<tr>
				<td height="30" class="para"><font color="#990000">&nbsp;<img
					align="absmiddle" src="../images/interop.gif" /> <strong>TapirLink?</strong>
				<a href="<%=CommonConfiguration.getWikiLocation()%>tapirlink"
					target="_blank"><img src="../images/information_icon_svg.gif"
					alt="Help" border="0" align="absmiddle" /></a></font></td>
			</tr>
			<tr>
				<td height="30" class="para">&nbsp; Current value is: <%=enc.getOKExposeViaTapirLink()%></td>
			</tr>
			<tr>
				<td>
				<form name="approve_form" method="post"
					action="../EncounterSetTapirLinkExposure"><input name="action"
					type="hidden" id="action" value="tapirLinkExpose"> <input
					name="number" type="hidden" value=<%=num%>> <input
					name="approve" type="submit" id="approve" value="Change"></form>
				</td>
			</tr>
		</table>
		</a> <br /> <%
	  	//end isOwner permissions
	  	  }
	  	  
	  	  	  //end else if-edit not null
	  	  }
	  	  
	  	  //add e-mail for tracking
	  	  if(request.isUserInRole("researcher")){
	  %> <!--<br /><table width="150" border="1" cellpadding="1" cellspacing="0" bordercolor="#000000" bgcolor="#CCCCCC">
      	<tr>
        	<td align="left" valign="top" class="para"><font color="#990000">Track data changes to this encounter via email address:</font></td>
        </tr>
        <tr>
        	<td align="left" valign="top"> 
           		<form name="trackShark" method="post" action="../TrackIt">
		  			<input name="number" type="hidden" value=<%=num%>>
              		<input name="email" type="text" id="email" size="20" maxlength="50">
              		<input name="Track" type="submit" id="Track" value="Track">
            	</form>
			</td>
        </tr>
      </table><br />
	        <table width="150" border="1" cellpadding="1" cellspacing="0" bordercolor="#000000" bgcolor="#CCCCCC">
      	<tr>
        	<td align="left" valign="top" class="para"><font color="#990000">Remove email address from tracking:</font></td>
        </tr>
        <tr>
        	<td align="left" valign="top"> 
           		<form name="trackShark" method="post" action="../DontTrack">
		  			<input name="number" type="hidden" value=<%=num%>>
              		<input name="email" type="text" id="email" size="20" maxlength="50">
              		<input name="Remove" type="submit" id="RemoveTrack" value="Remove">
            	</form>
			</td>
        </tr>
      </table><br />--> <%
			}
		%>

		<p>&nbsp;</p>
		<%
				}
			%>
		
		</td>


		<td align="left" valign="top">
		<table border="0" cellspacing="0" cellpadding="5">
			<tr>
				<td align="left" valign="top">
				<p class="para"><strong>Date</strong>: <%
       	if((request.isUserInRole("researcher"))){
       %><a
					href="http://<%=CommonConfiguration.getURLLocation()%>/xcalendar/calendar.jsp?scDate=<%=enc.getMonth()%>/1/<%=enc.getYear()%>">
				<%
       	}
       %><%=enc.getDate()%>
				<%
       	if((request.isUserInRole("researcher"))){
       %>
				</a>
				<%
       	}
       %> <%
 	if(isOwner) {
 %><font size="-1">[<a
					href="encounter.jsp?number=<%=num%>&edit=date#date">edit</a>]</font> <%
        	}
        %>
				<p class="para"><strong>Location</strong>: <%=enc.getLocation()%>
				<%
 	if(isOwner) {
 %><font size="-1">[<a
					href="encounter.jsp?number=<%=num%>&edit=location#location">edit</a>]</font>
				<%
 	}
 %><br /> <%
              	  	  	  	if (request.isUserInRole("researcher")) {
              	  	  	  %> <em>Location ID</em>: <%=enc.getLocationCode()%>
				<%
 	if(isOwner) {
 %><font size="-1">[<a
					href="encounter.jsp?number=<%=num%>&edit=loccode#loccode">edit</a>]</font>
				<a href="<%=CommonConfiguration.getWikiLocation()%>location_codes"
					target="_blank"><img src="../images/information_icon_svg.gif"
					alt="Help" border="0" align="absmiddle"></a> <%
					}
				%><br /> <em>Latitude</em>: <%
			  	if(!enc.getGPSLatitude().equals("")) {
			  %><br /><%=enc.getGPSLatitude()%> <%
 	if(enc.getDWCDecimalLatitude()!=null){
 %>(<%=gpsFormat.format(Double.parseDouble(enc.getDWCDecimalLatitude()))%>)<%
 	}}
 %><br /> <em>Longitude</em>: <%
			  	if(!enc.getGPSLongitude().equals("")) {
			  %><br /><%=enc.getGPSLongitude()%> <%
  	if(enc.getDWCDecimalLongitude()!=null){
  %>(<%=gpsFormat.format(Double.parseDouble(enc.getDWCDecimalLongitude()))%>)<%
  	}}
  %><br /> <%
			   	if(isOwner) {
			   %><font size="-1">[<a
					href="encounter.jsp?number=<%=num%>&edit=gps#gps">edit</a>]</font>
				<%
			   	}
			   %><br /> <a href="#map">View map</a> <%
			  	}
			  %>
				
				</p>
				
				<!-- Display size so long as show_size is not false in commonCnfiguration.properties-->
				<%
				if(CommonConfiguration.showProperty("size")){
				%>
					<p class="para"><strong>Reported size</strong>: <%
      				if(enc.getSize()>0) {%>
						<%=enc.getSize()%> <%=enc.getMeasureUnits()%>
					<%
 					} else {
 					%>Unknown<%
 					}
					 %> <br /> (<em>Method: <%=enc.getSizeGuess()%></em>) <%
 					if(isOwner) {%>
						<font size="-1">[<a href="encounter.jsp?number=<%=num%>&edit=size">edit</a>]</font>
					<%
 					}
				}
 %>
		
		<!-- Display maximumDepthInMeters so long as show_maximumDepthInMeters is not false in commonCOnfiguration.properties-->
		<%
		if(CommonConfiguration.showProperty("maximumDepthInMeters")){
		%>
		<p class="para"><strong>Water depth</strong>: 
		<%
            	if(enc.getDepth()>=0) {
            %> <%=enc.getDepth()%> <%=enc.getMeasureUnits()%> <%
 	  			} else {
 	  		%> Unknown<%
 	  			} 
				if(isOwner) {
 	  		%><font size="-1">[<a
					href="encounter.jsp?number=<%=num%>&edit=depth#depth">edit</a>]</font>
				<%
 	  			}
 	  		%>
		</p>
		<%
		}
		%>	
		<!-- End Display maximumDepthInMeters -->
		
		<!-- Display maximumElevationInMeters so long as show_maximumElevationInMeters is not false in commonCOnfiguration.properties-->
		<%
		if(CommonConfiguration.showProperty("maximumElevationInMeters")){
		%>
		<p class="para"><strong>Elevation</strong>: 
		
			<%=enc.getMaximumElevationInMeters()%> meters
		<%
 	 

		if(isOwner) {
 	  		%><font size="-1">[<a
					href="encounter.jsp?number=<%=num%>&edit=elevation#elevation">edit</a>]</font>
				<%
 	  	}
 	  		%>
		</p>
		<%
		}
		%>	
		<!-- End Display maximumElevationInMeters -->
			
				<p class="para"><strong>Sex</strong>: <%=enc.getSex()%> <%
 	if(isOwner) {
 %><font size="-1">[<a
					href="encounter.jsp?number=<%=num%>&edit=sex#sex">edit</a>]</font>
				<%
 	}
 %>
				<p class="para"><strong>Noticeable scarring</strong>: <%=enc.getDistinguishingScar()%>
				<%
 	if(isOwner) {
 %><font size="-1">[<a
					href="encounter.jsp?number=<%=num%>&edit=scar#scar">edit</a>]</font>
				<%
 	}
 %>

				<p class="para"><strong>Additional comments</strong><br /> <%=enc.getComments()%><br />
				<%
      	if(isOwner) {
      %><font size="-1">[<a
					href="encounter.jsp?number=<%=num%>&edit=comments#comments">edit</a>]</font>
				<%
      	}
      %>
				
				</p>


				<p class="para"><strong>Submitter</strong> <%
 	if(isOwner) {
 %><font size="-1">[<a
					href="encounter.jsp?number=<%=num%>&edit=contact#contact">edit</a>]</font>
				<%
 	}
 %><br /> <%=enc.getSubmitterName()%><br /> <%
		if (request.isUserInRole("researcher")) {
			
			if(enc.getSubmitterEmail().indexOf(",")!=-1) {
		//break up the string
		StringTokenizer stzr=new StringTokenizer(enc.getSubmitterEmail(),",");
		
		while(stzr.hasMoreTokens()) {
	%> <%=stzr.nextToken()%><br /> <%
				}
				
					}
					else {
			%> <%=enc.getSubmitterEmail()%><br /> <%
			}
		%> <%=enc.getSubmitterPhone()%><br /> <%=enc.getSubmitterAddress()%>
				<%
	}
%>

				<p class="para"><strong>Photographer</strong> <%
 	if(isOwner) {
 %><font size="-1">[<a
					href="encounter.jsp?number=<%=num%>&edit=contact#contact">edit</a>]</font>
				<%
 	}
 %><br /> <%=enc.getPhotographerName()%><br /> <%
	if (request.isUserInRole("researcher")) {
%> <%=enc.getPhotographerEmail()%><br /> <%=enc.getPhotographerPhone()%><br />
				<%=enc.getPhotographerAddress()%>


				<p class="para"><strong>Others to inform</strong> <%
 	if(isOwner) {
 %><font size="-1">[<a
					href="encounter.jsp?number=<%=num%>&edit=others#others">edit</a>]</font>
				<%
 	}
 %><br /> <%
        	if(enc.getInformOthers()!=null){
        		
        		if(enc.getInformOthers().indexOf(",")!=-1) {
        	//break up the string
        	StringTokenizer stzr=new StringTokenizer(enc.getInformOthers(),",");
        	
        	while(stzr.hasMoreTokens()) {
        %> <%=stzr.nextToken()%><br /> <%
				}
				
					}
					else{
			%> <%=enc.getInformOthers()%><br /> <%
			}
				}
				else {
		%>None<%
			}
		%> <%
	}
 
		 if (isOwner) {
%>
	<!-- Display spot patterning so long as show_spotpatterning is not false in commonCOnfiguration.properties-->
		<%
		if(CommonConfiguration.showProperty("spotpatterning")){
		%>
			
				<p class="para"><strong>Ready to scan</strong> <a
					href="<%=CommonConfiguration.getWikiLocation()%>processing_a_new_encounter"
					target="_blank"><img src="../images/information_icon_svg.gif"
					alt="Help" border="0" align="absmiddle"></a> <br />
				<%
 	String ready="No. Please add spot data.";
 	  	if ((enc.getNumSpots()>0)||(enc.getNumRightSpots()>0)) {
 	           ready="Yes. ";
 	   if(enc.getNumSpots()>0) {
 	   		ready+=" "+enc.getNumSpots()+" left-side spots added.";
 	   }
 	   if(enc.getNumRightSpots()>0) {
 	   	ready+=" "+enc.getNumRightSpots()+" right-side spots added.";
 	   }
 	   		
 	  }
 		%> 
		<%=ready%> 
		<% 
		if((enc.getNumSpots()>0)||(enc.getNumRightSpots()>0)) { %> 
			<br /><font size="-1">[<a href="encounter.jsp?number=<%=num%>&edit=rmSpots#rmSpots">remove left or right spots</a>]</font> <%
	  	}

	  	if(((new File((new File(".")).getCanonicalPath()+File.separator+"webapps"+File.separator+"ROOT"+File.separator+"encounters"+File.separator+num+File.separator+"lastFullScan.xml")).exists())&&(enc.getNumSpots()>0)) {
	  		%> <br /><br /><a
					href="scanEndApplet.jsp?writeThis=true&number=<%=num%>">Groth:
				Left-side scan results</a> <%
	  			}
	  			if(((new File((new File(".")).getCanonicalPath()+File.separator+"webapps"+File.separator+"ROOT"+File.separator+"encounters"+File.separator+num+File.separator+"lastFullRightScan.xml")).exists())&&(enc.getNumRightSpots()>0)) {
	  		%> <br /><br /><a
					href="scanEndApplet.jsp?writeThis=true&number=<%=num%>&rightSide=true">Groth:
				Right-side scan results</a> <%
	  			}
	  			if(((new File((new File(".")).getCanonicalPath()+File.separator+"webapps"+File.separator+"ROOT"+File.separator+"encounters"+File.separator+num+File.separator+"lastFullI3SScan.xml")).exists())&&(enc.getNumSpots()>0)) {
	  		%> <br /><br /><a
					href="i3sScanEndApplet.jsp?writeThis=true&number=<%=num%>&I3S=true">I3S:
				Left-side scan results</a> <%
	  			}
	  			if(((new File((new File(".")).getCanonicalPath()+File.separator+"webapps"+File.separator+"ROOT"+File.separator+"encounters"+File.separator+num+File.separator+"lastFullRightI3SScan.xml")).exists())&&(enc.getNumRightSpots()>0)) {
	  		%> <br /><br /><a
					href="i3sScanEndApplet.jsp?writeThis=true&number=<%=num%>&rightSide=true&I3S=true">I3S:
				Right-side scan results</a> <%
	  			}
	  		} //end if-owner
		} //end if show spots
	  		%>
		<!-- End Display spot patterning so long as show_spotpatterning is not false in commonConfiguration.properties-->
		
		
				</td>
				<td align="left" valign="top">
				<p class="para"><strong>Images</strong><br /> <%
	  				if (request.isUserInRole("researcher")) {
	  			%> <em>Click any image to view the originally submitted
				version in your browser</em>.
				</p>
				<%
			}
		%>
				<table>
					<%
        	Enumeration images=enc.getAdditionalImageNames().elements();
        		int imageCount=0;
        		while (images.hasMoreElements()) {
        		imageCount++;
        		String addTextFile=(String)images.nextElement();
        		try{
        		if((myShepherd.isAcceptableImageFile(addTextFile))||(myShepherd.isAcceptableVideoFile(addTextFile))) {
        			String addText=num+"/"+addTextFile;
        %>
					<tr>
						<td>
						<table>
							<tr>
								<td class="para"><em>Image <%=imageCount%></em></td>
							</tr>
							<%
					if (isOwner) {
				%>
							<tr>
								<td class="para"><img align="absmiddle"
									src="../images/Crystal_Clear_action_find.gif"> <strong>Image
								Commands</strong>: <font size="-1">
								<%
 	if (request.isUserInRole("nobody")) {
 %><form
									action="../EncounterRemoveImage?number=<%=(num)%>&filename=<%=(addTextFile.replaceAll(" ","%20"))%>"
									method="post" name="remove_photo"><input name="stupid"
									type="text" size="5" maxlength="5"><input name="Remove"
									type="submit" id="Rem_photo" value="Remove"></form>
								<%
 	}
 %> [<a
									href="../kwSearch.jsp?primaryImageName=<%=(num+"/"+(addTextFile.replaceAll(" ","%20")))%>">look
								for similar photos</a>] </font></td>
							</tr>

							<%
				}
				if (isOwner) {
					int totalKeywords=myShepherd.getNumKeywords();
			%>

							<tr>
								<td class="para"><img align="absmiddle"
									src="../images/cancel.gif"> <strong>Remove
								keyword:</strong> <%
					Iterator indexes=myShepherd.getAllKeywords();
							for(int m=0;m<totalKeywords;m++) {
								Keyword word=(Keyword)indexes.next();
								if(word.isMemberOf(addText)){
				%> &nbsp;<a
									href="/keywordHandler?number=<%=num%>&action=removePhoto&photoName=<%=addTextFile%>&keyword=<%=word.getIndexname()%>"><%=word.getReadableName()%></a>&nbsp;|
								<%
								} //end if
										}
							%>
								</td>
							</tr>
							<tr>
								<td>
								<form action="../KeywordHandler" method="post" name="keyword">
								<table>
									<tr>
										<td class="para"><img align="absmiddle"
											src="../images/tag.gif"> <strong>Add keyword <a
											href="<%=CommonConfiguration.getWikiLocation()%>photo_keywords"
											target="_blank"><img
											src="../images/information_icon_svg.gif" alt="Help"
											border="0" align="absmiddle" /></a></strong></td>
									</tr>
									<tr>
										<td><select name="keyword" id="keyword">
											<option value=" " selected>&nbsp;</option>
											<%
              	Iterator keys=myShepherd.getAllKeywords(kwQuery);
              	  	for(int n=0;n<totalKeywords;n++) {
              			Keyword word=(Keyword)keys.next();
              			String indexname=word.getIndexname();
              			String readableName=word.getReadableName();
              %>
											<option value="<%=indexname%>"><%=readableName%></option>
											<%
			 	 	}
			 	 %>

										</select> <input name="number" type="hidden" value=<%=num%>> <input
											name="action" type="hidden" value="addPhoto"> <input
											name="photoName" type="hidden" value="<%=addTextFile%>">
										<input name="AddKW" type="submit" id="AddKW" value="Add"></td>
									</tr>
								</table>
								</form>
								</td>
							</tr>

							<%
								}
							%>
							<tr>
								<td>
								<%
				boolean isBMP=false;
				boolean isVideo=false;
				if(addTextFile.toLowerCase().indexOf(".bmp")!=-1) {isBMP=true;}
				if((addTextFile.toLowerCase().indexOf(".mov")!=-1)||(addTextFile.toLowerCase().indexOf(".wmv")!=-1)||(addTextFile.toLowerCase().indexOf(".mpg")!=-1)||(addTextFile.toLowerCase().indexOf(".avi")!=-1)||(addTextFile.toLowerCase().indexOf(".mp4")!=-1)) {isVideo=true;}
				if((!isBMP)&&(!isVideo)) {
			%> <a href="imageViewer.jsp?number=<%=num%>&src=<%=addTextFile%>">
								<%
					}
						else {
				%> <a href="<%=addText%>"> <%
					}
					
					String thumbLocation="file-"+num+"/"+imageCount+".jpg";
					//try{}
					//catch(Exception e){}
					//File processedImage=new File(((new File(".")).getCanonicalPath()).replace('\\','/')+"/"+CommonConfiguration.getImageDirectory()+File.separator+num+"/"+imageCount+".jpg");
					File processedImage=new File(getServletContext().getRealPath(("/"+CommonConfiguration.getImageDirectory()+"/"+num+"/"+imageCount+".jpg")));

					
					if(isVideo) {
				%> <img width="250" height="200" alt="video <%=enc.getLocation()%>"
									src="../images/video.jpg" border="0" align="left" valign="left">
		
								</a>
								<%
					
			
							}
							else if ((!processedImage.exists())&&(!haveRendered)) {
								haveRendered=true;
								System.out.println("Using DynamicImage to render thumbnail: "+num);
								System.gc();
						%> <di:img width="250" height="200"
									imgParams="rendering=speed,quality=low" border="0"
									output="<%=thumbLocation%>" expAfter="0" threading="limited"
									fillPaint="#FFFFFF" align="left" valign="left">
									<di:image width="250" height="*" composite="70"
										srcurl="<%=addText%>" />
									<di:rectangle x="0" y="50" width="300" composite="30"
										height="13" fillPaint="#99CCFF"></di:rectangle>
									<di:image x="229" y="47" srcurl="copyright.gif"></di:image>
									<di:text x="4" y="50" align="left" font="Arial-bold-11"
										fillPaint="#000000">Unauthorised copying not permitted</di:text>
								</di:img> <img width="250" height="200"
									alt="photo <%=enc.getLocation()%>"
									src="<%=(num+"/"+imageCount+".jpg")%>" border="0" align="left"
									valign="left"> <%
				if (request.isUserInRole("researcher")) {
			%>
								</a>
								<%
				}
			%> <%
				} else if((!processedImage.exists())&&(haveRendered)) {
			%> <img width="250" height="200" alt="photo <%=enc.getLocation()%>"
									src="../images/processed.gif" border="0" align="left" valign="left">
								<%
					if ((request.isUserInRole("researcher"))||(request.isUserInRole(enc.getLocationCode()))) {
				%></a>
								<%
					}
				%> <%
				}else {
			%> <img width="250" height="200" alt="photo <%=enc.getLocation()%>"
									src="<%=(num+"/"+imageCount+".jpg")%>" border="0" align="left"
									valign="left"> <%
					if (request.isUserInRole("researcher")) {
				%></a>
								<%
					}
				%> <%
								}
							%>
								</td>
							</tr>

						</table>

						<%
						}
							else {
					%>
						<tr>
							<td>
							<p><img src="../alert.gif"> <strong>Unacceptable
							file submission:</strong> <%=addTextFile%> <%
					if (isOwner) {
				%> <br /><a
								href="/encounterRemoveImage?number=<%=(num)%>&filename=<%=(addTextFile.replaceAll(" ","%20"))%>&position=<%=imageCount%>">Click
							here to remove.</a></p>
							<%
					}
				%>
							</td>
						</tr>
						<%
					} //close else of if
						} //close try
						catch(Exception e){
							e.printStackTrace();
				%>
						<p>I hit an error trying to display a file: <%=addTextFile%></p>
						<%
						}
						} //close while
					%>
						
				</table>

				<p class="para">
				<%
		 			if (isOwner) {
		 		%>
				<table width="250" bgcolor="#99CCFF">
					<tr>
						<td class="para">
						<form action="../EncounterAddImage" method="post"
							enctype="multipart/form-data" name="encounterAddImage"><input
							name="action" type="hidden" value="imageadder" id="action">
						<input name="number" type="hidden" value="<%=num%>" id="shark">
						<strong><img align="absmiddle"
							src="../images/upload_small.gif" /> Add new image or video file:</strong><br />
						<input name="file2add" type="file" size="20">
						<p><input name="addtlFile" type="submit" id="addtlFile"
							value="Upload"></p></form>

						</td>
					</tr>
				</table>
				<br />
				<table width="250" bgcolor="#99CCFF">
					<tr>
						<td class="para">
						<form action="../EncounterRemoveImage" method="post"
							name="encounterRemoveImage"><input name="action"
							type="hidden" value="imageremover" id="action"> <input
							name="number" type="hidden" value=<%=num%>> <strong><img
							align="absmiddle" src="../images/cancel.gif" /> Remove
						image\video: </strong> <select name="position">
							<%
					for (int rmi=1;rmi<=imageCount;rmi++){
				%>
							<option value="<%=rmi%>"><%=rmi%></option>
							<%
					}
				%>
						</select><br />

						<p><input name="rmFile" type="submit" id="rmFile"
							value="Remove"></p></form>

						</td>
					</tr>
				</table>

				<%
				 	}
				 %>





				<p>
				<%
		 	if (isOwner&&((enc.getNumSpots()>0)||(enc.getNumRightSpots()>0))) {
		 	

		 			
		 			//File extractImage=new File(((new File(".")).getCanonicalPath()).replace('\\','/')+"/"+CommonConfiguration.getImageDirectory()+File.separator+num+"/extract"+num+".jpg");
		 			File extractImage=new File(getServletContext().getRealPath(("/"+CommonConfiguration.getImageDirectory()+"/"+num+"/extract"+num+".jpg")));

		 			//File extractRightImage=new File(((new File(".")).getCanonicalPath()).replace('\\','/')+"/"+CommonConfiguration.getImageDirectory()+File.separator+num+"/extractRight"+num+".jpg");
		 			File extractRightImage=new File(getServletContext().getRealPath(("/"+CommonConfiguration.getImageDirectory()+"/"+num+"/extractRight"+num+".jpg")));

		 			
		 			//File uploadedFile=new File(((new File(".")).getCanonicalPath()).replace('\\','/')+"/"+CommonConfiguration.getImageDirectory()+File.separator+num+"/"+enc.getSpotImageFileName());
		 			File uploadedFile=new File(getServletContext().getRealPath(("/"+CommonConfiguration.getImageDirectory()+"/"+num+"/"+enc.getSpotImageFileName())));

		 			
		 			//File uploadedRightFile=new File(((new File(".")).getCanonicalPath()).replace('\\','/')+"/"+CommonConfiguration.getImageDirectory()+File.separator+num+"/"+enc.getRightSpotImageFileName());
		 			File uploadedRightFile=new File(getServletContext().getRealPath(("/"+CommonConfiguration.getImageDirectory()+"/"+num+"/"+enc.getRightSpotImageFileName())));

		 			
		 			String extractLocation="file-"+num+"/extract"+num+".jpg";
		 			String extractRightLocation="file-"+num+"/extractRight"+num+".jpg";
		 			String addText=num+"/"+enc.getSpotImageFileName();
		 			String addTextRight=num+"/"+enc.getRightSpotImageFileName();
		 			//System.out.println(addText);
		 			String height="";
		 			String width="";
		 			String heightR="";
		 			String widthR="";
		 			

		 			//System.out.println(extractImage.exists());
		 			//System.out.println(uploadedFile.exists());
		 			//System.out.println(iInfo.check());
		 			ImageInfo iInfo=new ImageInfo();
		 			

		 			
		 			if((uploadedFile.exists())&&(uploadedFile.length()>0)&&(enc.getNumSpots()>0)) {

		 				
		 				iInfo.setInput(new FileInputStream(uploadedFile));
		 				if ((!extractImage.exists())&&(iInfo.check())) {
		 					//System.out.println("Made it here.");
		 					height+=iInfo.getHeight();
		 					width+=iInfo.getWidth();
		 					//System.out.println(height+"and"+width);
		 					int intHeight=((new Integer(height)).intValue());
		 					int intWidth=((new Integer(width)).intValue());
		 					//System.out.println("Made it here: "+enc.hasSpotImage+" "+enc.hasRightSpotImage);
		 					System.gc();
		 %> <di:img width="<%=intWidth%>" height="<%=intHeight%>"
					imgParams="rendering=speed,quality=low" expAfter="0" border="0"
					threading="limited" output="<%=extractLocation%>">
					<di:image srcurl="<%=addText%>" />
				</di:img> <%
							}
										}
									//set the right file
									
									if((uploadedRightFile.exists())&&(uploadedRightFile.length()>0)&&(enc.getNumRightSpots()>0)) {
									
									iInfo=new ImageInfo();
									iInfo.setInput(new FileInputStream(uploadedRightFile));
									if ((!extractRightImage.exists())&&(iInfo.check())) {
										//System.out.println("Made it here.");
										heightR+=iInfo.getHeight();
										widthR+=iInfo.getWidth();
										//System.out.println(height+"and"+width);
										int intHeightR=((new Integer(heightR)).intValue());
										int intWidthR=((new Integer(widthR)).intValue());
										System.gc();
						%> <di:img width="<%=intWidthR%>" height="<%=intHeightR%>"
					imgParams="rendering=speed,quality=low" expAfter="0"
					threading="limited" border="0" output="<%=extractRightLocation%>">
					<di:image srcurl="<%=addTextRight%>" />
				</di:img> <%
						}
								}
								
								
								String fileloc=(num+"/"+enc.getSpotImageFileName());
								String filelocR=(num+"/"+enc.getRightSpotImageFileName());
					%>
				<p class="para"><strong>Spot data image files used for
				matching</strong><br /> <font size="-1">[<a
					href="encounter.jsp?number=<%=num%>&edit=spotImage#spotImage">reset
				left or right spot data image</a>]</font><br /> <br /> <%
				if((enc.getNumSpots()>0)) {
			%> Left-side<em>.</em><em> Click the image to view the full size
				original. <a href="encounterSpotVisualizer.jsp?number=<%=num%>">Click
				here to see the left-side spots mapped to the left-side image.</a> </em><br />
				<a href="<%=fileloc%>"><img src="<%=fileloc%>" alt="image"
					width="250"></a> <%
 				}
 			%> <br /><br /> <%
				//--
				if((enc.getNumRightSpots()>0)) {
			%> Right-side<em>.</em><em> Click the image to view the full
				size original. <a
					href="encounterSpotVisualizer.jsp?number=<%=num%>&rightSide=true">Click
				here to see the right-side spots mapped to the right-side image.</a> </em><br />
				<a href="<%=filelocR%>"><img src="<%=filelocR%>" alt="image"
					width="250"></a> <%
 			              	} 
 			              	//--
 			              		
 			              			  
 			              	  }
 			              %>
				
				</p>
				
		
				<div class="module">
					<jsp:include page="encounterAdoptionEmbed.jsp" flush="true">
						<jsp:param name="num" value="<%=num%>" />
				</jsp:include>
				</div>
				
	
				
				
				</td>
			</tr>

		</table>
		<p>
		<%
	  	  	  	if (((request.isUserInRole("researcher"))||(request.isUserInRole("search"))&&(request.getParameter("noscript")==null))) {
	  	  	  %>
		<hr>
		<p><a name="map"><strong><img
			src="../images/2globe_128.gif" width="56" height="56"
			align="absmiddle" /></a>Mapping</strong></p>
		<%
	  	if((enc.getDWCDecimalLatitude()!=null)&&(enc.getDWCDecimalLongitude()!=null)) {
	  %>
		<p><i>Note</i>: If you zoom in too quickly, Google Maps may claim
		that it does not have the needed maps. Zoom back out, wait a few
		seconds to allow maps to load in the background, and then zoom in
		again.</p>


		<script
			src="http://maps.google.com/maps?file=api&amp;v=2&amp;key=<%=CommonConfiguration.getGoogleMapsKey() %>"
			type="text/javascript"></script> <script type="text/javascript">
    function initialize() {
      if (GBrowserIsCompatible()) {
        var map = new GMap2(document.getElementById("map_canvas"));
        
		
		<%
		double centroidX=0;
		double centroidY=0;
		centroidX=Double.parseDouble(enc.getDWCDecimalLatitude());
		centroidY=Double.parseDouble(enc.getDWCDecimalLongitude());
		%>
			map.setCenter(new GLatLng(<%=centroidX%>, <%=centroidY%>), 1);
			map.addControl(new GSmallMapControl());
        	map.addControl(new GMapTypeControl());
			map.setMapType(G_HYBRID_MAP);
			<%//encounter latitude
				double myLat=(new Double(enc.getDWCDecimalLatitude())).doubleValue();
				double myLong=(new Double(enc.getDWCDecimalLongitude())).doubleValue();%>
				          var point1 = new GLatLng(<%=myLat%>,<%=myLong%>, false);
						  var marker1 = new GMarker(point1);
						  GEvent.addListener(marker1, "click", function(){
						  	window.location="http://<%=CommonConfiguration.getURLLocation()%>/encounters/encounter.jsp?number=<%=enc.getEncounterNumber()%>";
						  });
						  GEvent.addListener(marker1, "mouseover", function(){
						  	marker1.openInfoWindowHtml("<strong><a target=\"_blank\" href=\"http://<%=CommonConfiguration.getURLLocation()%>/individuals.jsp?number=<%=enc.isAssignedToMarkedIndividual()%>\"><%=enc.isAssignedToMarkedIndividual()%></a></strong><br /><table><tr><td><img align=\"top\" border=\"1\" src=\"http://<%=CommonConfiguration.getURLLocation()%>/encounters/<%=enc.getEncounterNumber()%>/thumb.jpg\"></td><td>Date: <%=enc.getDate()%><br />Sex: <%=enc.getSex()%><br />Size: <%=enc.getSize()%> m<br /><br /><a target=\"_blank\" href=\"http://<%=CommonConfiguration.getURLLocation()%>/encounters/encounter.jsp?number=<%=enc.getEncounterNumber()%>\" >Go to encounter</a></td></tr></table>");
						  });
						  map.addOverlay(marker1);
		
		
      }
    }
    </script>
		<div id="map_canvas" style="width: 510px; height: 350px"></div>

		<%} else {%>
		<p>No GPS data is available for mapping.</p>
		<br /> <%}%> <br />
		<hr>
		<form action="../EncounterAddComment" method="post" name="addComments">
		<p class="para"><input name="user" type="hidden"
			value="<%=request.getRemoteUser()%>" id="user"> <input
			name="number" type="hidden" value="<%=enc.getEncounterNumber()%>"
			id="number"> <input name="action" type="hidden"
			value="enc_comments" id="action"> <img align="absmiddle"
			src="../images/Crystal_Clear_app_kaddressbook.gif"> <strong>Automated\Researcher
		comments</strong> (<em>Text or HTML</em>): </p>
		<%
	if (enc.getRComments()!=null) {
	%>
		<p class="para"><%=enc.getRComments().replaceAll("\n","<br />")%></p>
		<%
	}
	%>

		<p><textarea name="comments" cols="50" id="comments"></textarea> <br />
		<input name="Submit" type="submit" value="Add comments">
		</p>
		</form>
		<%}%>
		
		</p>
		</td>
	</tr>

</table>
<br />
<table>
	<tr>
		<td>
		<%
  Vector trackers=enc.getInterestedResearchers();
if((isOwner)&&(trackers.size()>0)) {%>

		<p><font size="-1">E-mail addresses currently tracking this
		encounter: <%
	  
	  	int numTrack=trackers.size();
		for(int track=0;track<numTrack;track++) {%> <a
			href="mailto:<%=((String)trackers.get(track))%>"><%=((String)trackers.get(track))%></a></a>&nbsp;|&nbsp;
		<%}%> </font></p>

		<%}%>
		</td>
	</tr>
</table>
<%
kwQuery.closeAll();
myShepherd.rollbackDBTransaction();
myShepherd.closeDBTransaction();
kwQuery=null;
myShepherd=null;

}
catch(Exception e){
	e.printStackTrace();
%>
<p>Hit an error. <%=e.toString()%></p>
</body>
</html>
<%
}

} else {
myShepherd.rollbackDBTransaction();
myShepherd.closeDBTransaction();
%>
<p class="para">There is no encounter #<%=num%> in the database.
Please double-check the encounter number and try again.</p>

<form action="encounter.jsp" method="post" name="encounter"><strong>Go
to encounter: </strong> <input name="number" type="text" value="<%=num%>"
	size="20"> <input name="Go" type="submit" value="Submit"></form>
<p><font color="#990000"><a href="allEncounters.jsp">View
all encounters</a></font></p>
<p><font color="#990000"><a href="../allIndividuals.jsp">View
all individuals</a></font></p>
<p></p>
<%}%>
<jsp:include page="../footer.jsp" flush="true" />


</div>
</div>
<!-- end page -->
</div>
<!--end wrapper -->

<%
if(request.getParameter("noscript")==null){
%>


<script type="text/javascript">
 
function submitForm(oForm)
{
  // Hide the code in first div tag
  document.getElementById('formDiv').style.display = 'none';
 
  // Display code in second div tag
  //document.getElementById('pleaseWaitDiv').style.display = 'block';
 
  oForm.submit();
}
 
</script>
<%
}
%>




</body>
</html>


