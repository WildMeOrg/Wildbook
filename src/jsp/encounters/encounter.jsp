<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java"
	import="java.awt.Dimension, org.apache.sanselan.*, com.drew.imaging.jpeg.*, com.drew.metadata.*, org.ecocean.servlet.*,java.util.ArrayList,java.util.GregorianCalendar,java.util.StringTokenizer,org.ecocean.*,java.text.DecimalFormat, javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Enumeration, java.net.URL, java.net.URLConnection, java.io.InputStream, java.io.FileInputStream, java.io.File, java.util.Iterator,java.util.Properties, java.util.Iterator"%>
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
if(session.getAttribute("langCode")!=null){langCode=(String)session.getAttribute("langCode");}
	
	
//let's load encounters.properties
Properties encprops=new Properties();
encprops.load(getClass().getResourceAsStream("/bundles/"+langCode+"/encounter.properties"));
				

String num=request.getParameter("number").replaceAll("\\+","").trim();


Shepherd myShepherd=new Shepherd();
Extent allKeywords=myShepherd.getPM().getExtent(Keyword.class,true);		
Query kwQuery=myShepherd.getPM().newQuery(allKeywords);
boolean proceed=true;
boolean haveRendered=false;


%>

<html>
<head>
<title><%=encprops.getProperty("encounter") %> <%=num%></title>
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


<!--
	1 ) Reference to the files containing the JavaScript and CSS.
	These files must be located on your server.
-->

<script type="text/javascript" src="../highslide/highslide/highslide-with-gallery.js"></script>
<link rel="stylesheet" type="text/css" href="../highslide/highslide/highslide.css" />

<!--
	2) Optionally override the settings defined at the top
	of the highslide.js file. The parameter hs.graphicsDir is important!
-->

<script type="text/javascript">
hs.graphicsDir = '../highslide/highslide/graphics/';
hs.align = 'center';
hs.transitions = ['expand', 'crossfade'];
hs.outlineType = 'rounded-white';
hs.fadeInOut = true;
//hs.dimmingOpacity = 0.75;

// Add the controlbar
hs.addSlideshow({
	//slideshowGroup: 'group1',
	interval: 5000,
	repeat: false,
	useControls: true,
	fixedControls: 'fit',
	overlayOptions: {
		opacity: 0.75,
		position: 'bottom center',
		hideOnMouseOut: true
	}
});

</script>	



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

//let's see if this user has ownership and can make edits
boolean isOwner = ServletUtilities.isUserAuthorizedForEncounter(enc, request);
String loggedIn="false";
if (session.getAttribute("logged")!=null) {
		Object OBJloggedIn=session.getAttribute("logged");
		loggedIn=(String)OBJloggedIn;
}
//end user identity and authorization check
%>
<table width="720" border="0" cellpadding="3" cellspacing="5">
<tr ><td colspan="3">
<%
if (enc.wasRejected()) {%>
<table width="810">
	<tr>
		<td bgcolor="#0033CC" colspan="3">
		<p><font color="#FFFFFF" size="4"><%=encprops.getProperty("unidentifiable_title") %>: <%=num%><%=livingStatus %> </font>
		</td>
	</tr>
</table>
</p>
<%

} else if(!enc.approved){%>
<table width="810">
	<tr>
		<td bgcolor="#CC6600" colspan="3">
		<p><font color="#FFFFFF" size="4"><%=encprops.getProperty("unapproved_title") %>: <%=num%><%=livingStatus %></font>
		</td>
	</tr>
</table>
<%} else {
%>

<p><font size="4"><strong><%=encprops.getProperty("title") %></strong>: <%=num%><%=livingStatus %></font></p>
<%
if(enc.getEventID()!=null){
%>
<p class="para"><%=encprops.getProperty("eventID") %>: 
<%=enc.getEventID() %>
</p>
<%
}
%>
<%}%> <%
	if (enc.isAssignedToMarkedIndividual().equals("Unassigned")) {
%>
<p class="para"><img align="absmiddle" src="../images/tag_big.gif" width="50px" height="*">
<%=encprops.getProperty("identified_as") %>: <%=enc.isAssignedToMarkedIndividual()%> <%
 	if(isOwner&&CommonConfiguration.isCatalogEditable()) {
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
<%=encprops.getProperty("identified_as") %>: <a
	href="../individuals.jsp?langCode=<%=langCode%>&number=<%=enc.isAssignedToMarkedIndividual()%><%if(request.getParameter("noscript")!=null){%>&noscript=true<%}%>"><%=enc.isAssignedToMarkedIndividual()%></a></font>
<%
 	if(isOwner&&CommonConfiguration.isCatalogEditable()) {
 %>[<a href="encounter.jsp?number=<%=num%>&edit=manageShark">edit</a>]<%
 	}
 	if (isOwner) {
 %><br> <img align="absmiddle"
	src="../images/Crystal_Clear_app_matchedBy.gif"> <%=encprops.getProperty("matched_by") %>: <%=enc.getMatchedBy()%>
<%
 	if(isOwner&&CommonConfiguration.isCatalogEditable()) {
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
<%=encprops.getProperty("status")%>: <%=enc.getLivingStatus()%> <%
 	if(isOwner&&CommonConfiguration.isCatalogEditable()) {
 %>[<a
	href="encounter.jsp?number=<%=num%>&edit=livingStatus#livingStatus">edit</a>]<%
 	}
 %>
</p>


<p class="para"><img align="absmiddle"
	src="../images/alternateid.gif"> <%=encprops.getProperty("alternate_id")%>: <%=enc.getAlternateID()%>
<%
 	if(isOwner&&CommonConfiguration.isCatalogEditable()) {
 %>[<a href="encounter.jsp?number=<%=num%>&edit=alternateid#alternateid">edit</a>]<%
 	}
 %>
</p>
<%


if((loggedIn.equals("true"))&&(enc.getSubmitterID()!=null)) {
%>
<p class="para"><img align="absmiddle"
	src="../images/Crystal_Clear_app_Login_Manager.gif"> <%=encprops.getProperty("assigned_user")%>: <%=enc.getSubmitterID()%> <%
 	if(isOwner&&CommonConfiguration.isCatalogEditable()) {
 	%><font size="-1">[<a href="encounter.jsp?number=<%=num%>&edit=user#user">edit</a>]</font>
<%
 	}
 %>
</p>
<%
	}
%>
</td></tr>
	<tr>
	<%
	if(CommonConfiguration.isCatalogEditable()){
	%>
		<td width="170" align="left" valign="top" bgcolor="#99CCFF">
		<%
 	//start deciding menu bar contents

 //if not logged in
 if(session.getAttribute("logged")==null) {
 %>
		<p class="para"><a href="../welcome.jsp?reflect=<%=request.getRequestURI()%>?number=<%=num%>"><%=encprops.getProperty("login")%></a></p>

		<%
			} 
		//if logged in, limit commands displayed			
		else {
		%>
		<p align="center" class="para"><font color="#000000" size="+1"><strong>
		<%=encprops.getProperty("action") %> <font color="#000000" size="+1"><strong><img src="../images/Crystal_Clear_app_advancedsettings.gif" width="29" height="29" align="absmiddle" /></strong></font> <%=encprops.getProperty("uppercaseEdit") %> </strong></font><br> <br> <em><font
			size="-1"><%=encprops.getProperty("editarea")%></font></em>
		</p>
		<%
			//manager-level commands
				if(isOwner) {
				
			
			//approve new encounter
			if ((!enc.approved)&&(isOwner)) {
		%>
		<table width="175" border="1" cellpadding="1" cellspacing="0"
			bordercolor="#000000" bgcolor="#CECFCE">
			<tr>
				<td height="30" class="para">
				<p><font color="#990000"><font color="#990000">&nbsp;<img
					align="absmiddle" src="../images/check_green.png" /></font> <strong><%=encprops.getProperty("approve_encounter")%></strong></font></p>
				<p><font color="#990000"><font color="#990000"><a
					href="<%=CommonConfiguration.getWikiLocation()%>approving_an_encounter"
					target="_blank">&nbsp;<img src="../images/information_icon_svg.gif" alt="Help" border="0" align="absmiddle" /></a></font> </font><span class="style2"><%=encprops.getProperty("approval_checklist")%></span></p>
				</td>
			</tr>
			<tr>
				<td>
				<form name="approve_form" method="post" action="../EncounterApprove">
				<input name="action" type="hidden" id="action" value="approve">
				<input name="number" type="hidden"
					value=<%=request.getParameter("number")%>> <input
					name="approve" type="submit" id="approve" value="<%=encprops.getProperty("approve")%> "></form>
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
				<td align="left" valign="top" class="para"><strong><font color="#990000"><%=encprops.getProperty("setLocationID")%>:</font></strong></td>
			</tr>
			<tr>
				<td align="left" valign="top">
				<form name="addLocCode" action="../EncounterSetLocationID"
					method="post"><input name="code" type="text" size="5"
					maxlength="5"> <input name="number" type="hidden"
					value=<%=num%>> <input name="action" type="hidden"
					value="addLocCode"> <input name="Set Location ID"
					type="submit" id="Add" value="<%=encprops.getProperty("setLocationID")%>"></form>
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
					color="#990000"><%=encprops.getProperty("setAlternateID")%>:</font></strong></td>
			</tr>
			<tr>
				<td align="left" valign="top">
				<form name="setAltID" action="../EncounterSetAlternateID"
					method="post"><input name="alternateid" type="text"
					size="10" maxlength="50"> <input name="encounter"
					type="hidden" value=<%=num%>> <input name="Set"
					type="submit" id="<%=encprops.getProperty("set")%>" value="Set"></form>
				</td>
			</tr>
		</table>
		</a><br> <%
		}
		
		//encounter set dynamic property
		if(CommonConfiguration.isCatalogEditable()&&isOwner&&(request.getParameter("edit")!=null)&&(request.getParameter("edit").equals("dynamicproperty"))){
		%> <a name="dynamicproperty"><br>
		<table width="150" border="1" cellpadding="1" cellspacing="0" bordercolor="#000000" bgcolor="#CCCCCC">
			<tr>
				<td align="left" valign="top" class="para"><table><tr><td><img align="absmiddle" src="../images/lightning_dynamic_props.gif" /></td><td><strong><font color="#990000"> <%=encprops.getProperty("initCapsSet")%> <%=request.getParameter("name")%></font></strong></td></tr></table></td>
			</tr>
			<tr>
				<td align="left" valign="top">
				<form name="setDynProp" action="../EncounterSetDynamicProperty" method="post">
				<%
				if(enc.getDynamicPropertyValue(request.getParameter("name"))!=null){
				%>
				<input name="value" type="text" size="10" maxlength="500" value="<%=enc.getDynamicPropertyValue(request.getParameter("name"))%>">
				<%
				}
				else{
				%>
				<input name="value" type="text" size="10" maxlength="500">
				<%
				}
				%>
					<input name="number" type="hidden" value="<%=num%>"> 
					<input name="name" type="hidden" value="<%=request.getParameter("name")%>"> 
					<input name="Set" type="submit" id="<%=encprops.getProperty("set")%>" value="<%=encprops.getProperty("initCapsSet")%>"></form>
				</td>
			</tr>
		</table>
		</a><br> <%
		}
		
		//encounter add dynamic property
		if(isOwner&&CommonConfiguration.isCatalogEditable()){
		%> <a name="add_dynamicproperty"><br>
		<table width="150" border="1" cellpadding="1" cellspacing="0" bordercolor="#000000" bgcolor="#CCCCCC">
			<tr>
				<td align="left" valign="top" class="para"><table><tr><td><img align="absmiddle" src="../images/lightning_dynamic_props.gif" /></td><td><strong><font color="#990000"><%=encprops.getProperty("addDynamicProperty")%></font></strong></td></tr></table></td>
			</tr>
			<tr>
				<td align="left" valign="top" class="para">
				<form name="addDynProp" action="../EncounterSetDynamicProperty" method="post">
					<%=encprops.getProperty("propertyName")%>:<br /><input name="name" type="text" size="10" maxlength="50"><br />
					<%=encprops.getProperty("propertyValue")%>:<br /><input name="value" type="text" size="10" maxlength="500">
					<input name="number" type="hidden" value="<%=num%>"> 
					<input name="Set" type="submit" id="<%=encprops.getProperty("set")%>" value="<%=encprops.getProperty("initCapsSet")%>"></form>
				</td>
			</tr>
		</table>
		</a><br> <%
		}
		
				
				
				//set informothers
			if((isOwner)&&(request.getParameter("edit")!=null)&&(request.getParameter("edit").equals("others"))){
		%> <a name="others"><br>
		<table width="150" border="1" cellpadding="1" cellspacing="0" bordercolor="#000000" bgcolor="#CCCCCC">
			<tr>
				<td align="left" valign="top" class="para"><strong><%=encprops.getProperty("setOthersToInform")%> 
				</td>
			</tr>
			<tr>
				<td align="left" valign="top">
				<form name="setOthers" action="../EncounterSetInformOthers" method="post">
				<input name="encounter" type="hidden" value="<%=num%>"> 
				<input name="informothers" type="text" size="28" <%if(enc.getInformOthers()!=null){%>
					value="<%=enc.getInformOthers().trim()%>" <%}%> maxlength="1000">
				<br> <input name="Set" type="submit" id="Set" value="<%=encprops.getProperty("set")%>"></form>
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
					src="../images/Crystal_Clear_app_matchedBy.gif" /> <strong><%=encprops.getProperty("matchedBy")%>:</strong></font></td>
			</tr>
			<tr>
				<td align="left" valign="top">
				<form name="setMBT" action="../EncounterSetMatchedBy" method="post">
				<select name="matchedBy" id="matchedBy">
					<option value="Unmatched first encounter"><%=encprops.getProperty("unmatchedFirstEncounter")%></option>
					<option value="Visual inspection"><%=encprops.getProperty("visualInspection")%></option>
					<option value="Pattern match" selected><%=encprops.getProperty("patternMatch")%></option>
				</select> <input name="number" type="hidden" value=<%=num%>> 
				<input name="setMB" type="submit" id="setMB" value="<%=encprops.getProperty("set")%>"></form>
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
					color="#990000">
					<img align="absmiddle" src="../images/tag_small.gif" /><br></br>
				<strong><%=encprops.getProperty("add2MarkedIndividual")%>:</strong></font></td>
			</tr>
			<tr>
				<td align="left" valign="top">
				<form name="add2shark" action="../IndividualAddEncounter"
					method="post"><%=encprops.getProperty("individual")%>: <input name="individual" type="text" size="10" maxlength="50"><br> <%=encprops.getProperty("matchedBy")%>:<br>
				<select name="matchType" id="matchType">
					<option value="Unmatched first encounter"><%=encprops.getProperty("unmatchedFirstEncounter")%></option>
					<option value="Visual inspection"><%=encprops.getProperty("visualInspection")%></option>
					<option value="Pattern match" selected><%=encprops.getProperty("patternMatch")%></option>
				</select> <br> <input name="noemail" type="checkbox" value="noemail">
				<%=encprops.getProperty("suppressEmail")%><br> <input name="number" type="hidden"
					value=<%=num%>> <input name="action" type="hidden"
					value="add"> <input name="Add" type="submit" id="Add"
					value="<%=encprops.getProperty("add")%>"></form>
				</td>
			</tr>
		</table>
		</a><br> <%
		  	}
		  	  //Remove from MarkedIndividual if not unassigned
		  	  if((!enc.isAssignedToMarkedIndividual().equals("Unassigned"))&&CommonConfiguration.isCatalogEditable()&&isOwner&&(request.getParameter("edit")!=null)&&(request.getParameter("edit").equals("manageShark"))) {
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
						<td><strong> <%=encprops.getProperty("removeFromMarkedIndividual")%></strong></td>
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
					value="<%=encprops.getProperty("remove")%>"></form>
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
					color="#990000">
					<img align="absmiddle" src="../images/tag_small.gif" /> <strong><%=encprops.getProperty("createMarkedIndividual")%>:</strong></font></td>
			</tr>
			<tr>
				<td align="left" valign="top">
				<form name="createShark" method="post" action="../IndividualCreate">
				<input name="number" type="hidden" value="<%=num%>"> <input
					name="action" type="hidden" value="create"> <input
					name="individual" type="text" id="individual" size="10"
					maxlength="50"
					value="<%=getNextIndividualNumber(enc, myShepherd)%>"><br>
				<%
						if(isOwner){
					%> <input name="noemail" type="checkbox" value="noemail">
				<%=encprops.getProperty("suppressEmail")%><br> <%
						}
					%> <input name="Create" type="submit" id="Create" value="<%=encprops.getProperty("create")%>"></form>
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
					color="#990000"><%=encprops.getProperty("resetGPS")%>:</font></span><br> <font size="-1"><%=encprops.getProperty("noteGPS")%></font>
				</td>
			</tr>
			<tr>
				<td>
				<form name="resetGPSform" method="post" action="../EncounterSetGPS">
				<input name="action" type="hidden" value="resetGPS">
				<p><strong><%=encprops.getProperty("latitude")%>:</strong><br> <select name="lat"
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
					<option value="South" selected><%=encprops.getProperty("south")%></option>
					<option value="North"><%=encprops.getProperty("north")%></option>
				</select><br> <strong><%=encprops.getProperty("latitude")%>:</strong><br> <select
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
					<option value="West" selected><%=encprops.getProperty("west")%></option>
					<option value="East"><%=encprops.getProperty("east")%></option>
				</select> <input name="number" type="hidden" value=<%=num%>> <input
					name="setGPSbutton" type="submit" id="setGPSbutton" value="<%=encprops.getProperty("setGPS")%>">
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
					color="#990000"><%=encprops.getProperty("setLocation")%>:</font></strong></td>
			</tr>
			<tr>
				<td align="left" valign="top">
				<form name="setLocation" action="../EncounterSetLocation"
					method="post"><textarea name="location" size="15"><%=enc.getLocation()%></textarea>
				<input name="number" type="hidden" value=<%=num%>> <input
					name="action" type="hidden" value="setLocation"> <input
					name="Add" type="submit" id="Add" value="<%=encprops.getProperty("setLocation")%>"></form>
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
					color="#990000"><%=encprops.getProperty("editSubmittedComments")%>:</font></strong></td>
			</tr>
			<tr>
				<td align="left" valign="top">
				<form name="setComments" action="../EncounterSetOccurrenceRemarks"
					method="post"><textarea name="fixComment" size="15"><%=enc.getComments()%></textarea>
				<input name="number" type="hidden" value=<%=num%>> <input
					name="action" type="hidden" value="editComments"> <input
					name="EditComm" type="submit" id="EditComm" value="<%=encprops.getProperty("submitEdit")%>"></form>
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
					color="#990000"><%=encprops.getProperty("editContactInfo")%>:</font></strong></td>
			</tr>
			<tr>
				<td></td>
			</tr>
			<tr>
				<td align="left" valign="top">
				<form name="setPersonalDetails"
					action="../EncounterSetSubmitterPhotographerContactInfo"
					method="post"><label> <input type="radio"
					name="contact" value="submitter"><%=encprops.getProperty("submitter")%></label> <br><label>
				<input type="radio" name="contact" value="photographer"><%=encprops.getProperty("photographer")%></label>
				<br> <%=encprops.getProperty("name")%><br><input name="name" type="text" size="20"
					maxlength="100"> <%=encprops.getProperty("email")%><br><input name="email"
					type="text" size="20"> <%=encprops.getProperty("phone")%><br><input name="phone"
					type="text" size="20" maxlength="100"> <%=encprops.getProperty("phone")%><br><input
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
					color="#990000"><%=encprops.getProperty("resetSex")%>:</font></strong></td>
			</tr>
			<tr>
				<td align="left" valign="top">
				<form name="setxencshark" action="../EncounterSetSex" method="post">
				<select name="selectSex" size="1" id="selectSex">
					<option value="unknown" selected><%=encprops.getProperty("unknown")%></option>
					<option value="male"><%=encprops.getProperty("male")%></option>
					<option value="female"><%=encprops.getProperty("female")%></option>
				</select> <input name="number" type="hidden" value="<%=num%>" id="number">
				<input name="action" type="hidden" value="setEncounterSex">
				<input name="Add" type="submit" id="Add" value="<%=encprops.getProperty("resetSex")%>"></form>
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
					src="../images/life_icon.gif"> <%=encprops.getProperty("resetStatus")%>:</font></strong></td>
			</tr>
			<tr>
				<td align="left" valign="top">
				<form name="livingStatusForm" action="../EncounterSetLivingStatus"
					method="post"><select name="livingStatus" id="livingStatus">
					<option value="alive" selected><%=encprops.getProperty("alive")%></option>
					<option value="dead"><%=encprops.getProperty("dead")%></option>
				</select> <input name="encounter" type="hidden" value="<%=num%>" id="number">
				<input name="Add" type="submit" id="Add" value="<%=encprops.getProperty("resetStatus")%>"></form>
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
					color="#990000"><%=encprops.getProperty("resetEncounterDate")%>:</font></strong></td>
			</tr>
			<tr>
				<td align="left" valign="top">
				<form name="setxencshark" action="../EncounterResetDate" method="post">
				<em><%=encprops.getProperty("day")%></em> <select name="day" id="day">
					<option value="0">?</option>
					<%
									for(int pday=1;pday<32;pday++) {
								%>
					<option value="<%=pday%>"><%=pday%></option>
					<%
      								}
      							%>
				</select><br> <em>&nbsp;<%=encprops.getProperty("month")%></em> <select name="month" id="month">
					<option value="-1">?</option>
					<%
      								for(int pmonth=1;pmonth<13;pmonth++) {
      							%>
					<option value="<%=pmonth%>"><%=pmonth%></option>
					<%
      								}
      							%>
				</select><br> <em>&nbsp;<%=encprops.getProperty("year")%></em> <select name="year" id="year">
					<option value="-1">?</option>

					<%
																	for(int pyear=nowYear;pyear>(nowYear-50);pyear--) {
																%>
					<option value="<%=pyear%>"><%=pyear%></option>
					<%
      								}
      							%>
				</select><br> <em>&nbsp;<%=encprops.getProperty("hour")%></em> <select name="hour" id="hour">
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
				</select><br> <em>&nbsp;<%=encprops.getProperty("minutes")%></em> <select name="minutes" id="minutes">
					<option value="00" selected>:00</option>
					<option value="15">:15</option>
					<option value="30">:30</option>
					<option value="45">:45</option>
				</select><br> <input name="number" type="hidden" value="<%=num%>"
					id="number"> <input name="action" type="hidden"
					value="changeEncounterDate"> <input name="AddDate"
					type="submit" id="AddDate" value="<%=encprops.getProperty("setDate")%>">
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
					color="#990000"><%=encprops.getProperty("setSize")%>:</font></strong></td>
			</tr>
			<tr>
				<td align="left" valign="top">
				<form name="setencsize" action="../EncounterSetSize" method="post">
				<input name="lengthField" type="text" id="lengthField" size="8"
					maxlength="8"> <%=encprops.getProperty("meters")%><br> <em><%=encprops.getProperty("useZeroIfUnknown")%></em><br>
				<input name="lengthUnits" type="hidden" id="lengthUnits"
					value="Meters"> <select name="guessList" id="guessList">
					<option value="directly measured"><%=encprops.getProperty("directlyMeasured")%></option>
					<option value="submitter's guess"><%=encprops.getProperty("personalGuess")%></option>
					<option value="guide/researcher's guess" selected><%=encprops.getProperty("guessOfGuide")%></option>
				</select> <input name="number" type="hidden" value="<%=num%>" id="number">
				<input name="action" type="hidden" value="setEncounterSize">
				<input name="Add" type="submit" id="Add" value="<%=encprops.getProperty("setSize")%>"></form>
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
					color="#990000"><%=encprops.getProperty("setDepth")%>:</font></strong></td>
			</tr>
			<tr>
				<td align="left" valign="top">
				<form name="setencdepth" action="../EncounterSetMaximumDepth" method="post">
					<input name="depth" type="text" id="depth" size="10"> <%=encprops.getProperty("meters")%> <input name="lengthUnits" type="hidden"
					id="lengthUnits" value="Meters"> <input name="number"
					type="hidden" value="<%=num%>" id="number"> <input
					name="action" type="hidden" value="setEncounterDepth"> <input
					name="AddDepth" type="submit" id="AddDepth" value="<%=encprops.getProperty("setDepth")%>">
				</form>
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
					color="#990000"><%=encprops.getProperty("setElevation")%>:</font></strong></td>
			</tr>
			<tr>
				<td align="left" valign="top">
				<form name="setencelev" action="../EncounterSetMaximumElevation" method="post">
						<input name="elevation" type="text" id="elevation" size="10"> Meters <input name="lengthUnits" type="hidden" id="lengthUnits" value="Meters"> 
						<input name="number" type="hidden" value="<%=num%>" id="number"> 
						<input name="action" type="hidden" value="setEncounterElevation"> 
						<input name="AddElev" type="submit" id="AddElev" value="<%=encprops.getProperty("setElevation")%>">
				  </form>
				</td>
			</tr>
		</table>
		</a><br /> <%
			}

		if((request.getParameter("edit")!=null)&&(request.getParameter("edit").equals("user"))){
		%> <a name="user">
		<table width="150" border="1" cellpadding="1" cellspacing="0" bordercolor="#000000" bgcolor="#CCCCCC">
			<tr>
				<td align="left" valign="top" class="para"><font
					color="#990000"><img align="absmiddle"
					src="../images/Crystal_Clear_app_Login_Manager.gif" /> <strong><%=encprops.getProperty("assignUser")%>:</strong></font></td>
			</tr>
			<tr>
				<td align="left" valign="top">
				<form name="asetSubmID" action="../EncounterSetSubmitterID"
					method="post"><input name="submitter" type="text" size="10"
					maxlength="50"> <input name="number" type="hidden"
					value=<%=num%>> <input name="Assign" type="submit"
					id="Assign" value="<%=encprops.getProperty("assign")%>"></form>
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
					color="#990000"><%=encprops.getProperty("editScarring")%>:</font></strong></td>
			</tr>
			<tr>
				<td align="left" valign="top">
				<form name="setencsize" action="../EncounterSetScarring" method="post">
				<textarea name="scars" size="15"><%=enc.getDistinguishingScar()%></textarea> 
				<input name="number" type="hidden" value="<%=num%>" id="number">
				<input name="action" type="hidden" value="setScarring"> <input
					name="Add" type="submit" id="scar" value="<%=encprops.getProperty("resetScarring")%>"></form>
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
					action="../ScanTaskHandler"><input name="action" type="hidden"
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
	  if (isOwner&&CommonConfiguration.isCatalogEditable()) {
	  %>
		<table width="150" border="1" cellpadding="1" cellspacing="0" bordercolor="#000000" bgcolor="#CECFCE">
			<tr>
				<td>
				<p class="para"><font color="#990000"><img
					align="absmiddle" src="../images/cancel.gif" /> <strong><%=encprops.getProperty("rejectEncounter")%></strong></font></p>
				<p class="para"><font color="#990000"><strong><font color="#990000"><font color="#990000"><a
					href="<%=CommonConfiguration.getWikiLocation()%>approving_an_encounter"
					target="_blank"><img src="../images/information_icon_svg.gif"
					alt="Help" border="0" align="absmiddle" /></a> </font></font></strong></font><span class="style4"><%=encprops.getProperty("moreInfo")%> </span></p>
				</td>
			</tr>
			<tr>
				<td>
				<form name="reject_form" method="post" action="reject.jsp">
				<input name="action" type="hidden" id="action" value="reject">
				<input name="number" type="hidden" value=<%=num%>> <input
					name="reject" type="submit" id="reject" value="<%=encprops.getProperty("rejectEncounter")%>"></form>
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
				<p><strong><font color="#990000"><%=encprops.getProperty("setIdentifiable")%></font></strong></p>
				<p><font color="#990000"><font color="#990000"><a
					href="<%=CommonConfiguration.getWikiLocation()%>approving_an_encounter"
					target="_blank"><img src="../images/information_icon_svg.gif"
					alt="Help" border="0" align="absmiddle" /></a></font> </font><span class="style4"><%=encprops.getProperty("moreInfo")%> </span></p>
				</td>
			</tr>
			<tr>
				<td>
				<form name="reacceptEncounter" method="post"
					action="../EncounterSetIdentifiable"><input name="action"
					type="hidden" id="action" value="reaccept"> <input
					name="number" type="hidden" value=<%=num%>> <input
					name="reject" type="submit" id="reject" value="<%=encprops.getProperty("reaccept")%>"></form>
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
			if(CommonConfiguration.isCatalogEditable()){
	  	  %>
		<table border="1" cellpadding="2" cellspacing="0" bordercolor="#000000" bgcolor="#CCCCCC">
			<tr>
				<td align="left" valign="top" class="para">
				<font color="#990000"><img
					align="absmiddle" src="../images/thumbnail_image.gif" /></font> <strong><%=encprops.getProperty("resetThumbnail")%></strong>&nbsp;</font></td>
			</tr>
			<tr>
				<td align="left">
				<form action="../resetThumbnail.jsp" method="get" enctype="multipart/form-data" name="resetThumbnail">
				<input name="number" type="hidden" value="<%=num%>" id="numreset"><br />
				<%=encprops.getProperty("useImage")%>: <select name="imageNum">
					<%
					for (int rmi2=1;rmi2<=numImages;rmi2++){
				%>
					<option value="<%=rmi2%>"><%=rmi2%></option>
					<%
					}
				%>
				</select><br /> 
				<input name="resetSubmit" type="submit" id="resetSubmit" value="<%=encprops.getProperty("resetThumbnail")%>"></form>
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
				<td height="30" class="para">&nbsp; <%=encprops.getProperty("currentValue")%>: <%=enc.getOKExposeViaTapirLink()%></td>
			</tr>
			<tr>
				<td>
				<form name="approve_form" method="post"
					action="../EncounterSetTapirLinkExposure"><input name="action"
					type="hidden" id="action" value="tapirLinkExpose"> <input
					name="number" type="hidden" value=<%=num%>> <input
					name="approve" type="submit" id="approve" value="<%=encprops.getProperty("change")%>"></form>
				</td>
			</tr>
		</table>
		</a> <br /> <%
		}
	  	//end isOwner permissions
	  	  }
	  	  
	  	  	  //end else if-edit not null
	  	  }
	  	  
	  	  //add e-mail for tracking
	  	 
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
      </table><br />--> 

		<p>&nbsp;</p>
		<%
				}
			%>
		
		</td>
		<%
		}
		%>

<td align="left" valign="top">
<table border="0" cellspacing="0" cellpadding="5">
		
			<tr>
				<td width="300" align="left" valign="top">
				<p class="para"><strong><%=encprops.getProperty("date") %></strong><br /> 
				<a href="http://<%=CommonConfiguration.getURLLocation()%>/xcalendar/calendar.jsp?scDate=<%=enc.getMonth()%>/1/<%=enc.getYear()%>">
					<%=enc.getDate()%>
				</a> 
				<%
				if(isOwner&&CommonConfiguration.isCatalogEditable()) {
 					%><font size="-1">[<a href="encounter.jsp?number=<%=num%>&edit=date#date">edit</a>]</font> <%
        		}
        		%>
				<br />
				
				<%=encprops.getProperty("verbatimEventDate")%>: 
				<%
				if(enc.getVerbatimEventDate()!=null){
				%>
				<%=enc.getVerbatimEventDate()%>
				<%
				}
				else {
				%>
				<%=encprops.getProperty("none") %>
				<%
				}
				if(isOwner&&CommonConfiguration.isCatalogEditable()) {
 					%> <font size="-1">[<a href="encounter.jsp?number=<%=num%>&edit=verbatimdate#verbatimdate">edit</a>]</font> <%
        		}
        		%>
				<p class="para"><strong><%=encprops.getProperty("location") %></strong><br /> <%=enc.getLocation()%>
				<%
 	if(isOwner&&CommonConfiguration.isCatalogEditable()) {
 %><font size="-1">[<a href="encounter.jsp?number=<%=num%>&edit=location#location">edit</a>]</font>
				<%
 	}
 %>
 <br /> 

             <em><%=encprops.getProperty("locationID") %></em>: <%=enc.getLocationCode()%>
				<%
 				if(isOwner&&CommonConfiguration.isCatalogEditable()) {%>
 					<font size="-1">[<a href="encounter.jsp?number=<%=num%>&edit=loccode#loccode">edit</a>]</font>
					<a href="<%=CommonConfiguration.getWikiLocation()%>location_codes" target="_blank"><img src="../images/information_icon_svg.gif" alt="Help" border="0" align="absmiddle"></a> <%
				}
				%><br /> 
				<em><%=encprops.getProperty("latitude") %></em>: 
					<%
			  			if((enc.getDWCDecimalLatitude()!=null)&&(!enc.getDWCDecimalLatitude().equals("-9999.0"))) {
			  		%>
			  				<br /> <%=gpsFormat.format(Double.parseDouble(enc.getDWCDecimalLatitude()))%>
			  		<%
			  			}
 					%>
 				<br /> <em><%=encprops.getProperty("longitude") %></em>: 
 					<%
			  			if((enc.getDWCDecimalLongitude()!=null)&&(!enc.getDWCDecimalLongitude().equals("-9999.0"))) {
			  		%>
			  				<br /> <%=gpsFormat.format(Double.parseDouble(enc.getDWCDecimalLongitude()))%>
			  		<%
			  			}
 					%>
  				<br /> <%
			   	if(isOwner&&CommonConfiguration.isCatalogEditable()) {
			   		%><font size="-1">[<a href="encounter.jsp?number=<%=num%>&edit=gps#gps">edit</a>]</font>
				<%
			   	}
			   %><br /> <a href="#map"><%=encprops.getProperty("view_map") %></a> 
				
				</p>
				
				<!-- Display size so long as show_size is not false in commonCnfiguration.properties-->
				<%
				if(CommonConfiguration.showProperty("size")){
				%>
					<p class="para"><strong><%=encprops.getProperty("size") %></strong><br /> <%
      				if(enc.getSize()>0) {%>
						<%=enc.getSize()%> <%=enc.getMeasureUnits()%>
					<%
 					} else {
 					%><%=encprops.getProperty("unknown") %><%
 					}
					 %> <br /> (<em><%=encprops.getProperty("method") %>: <%=enc.getSizeGuess()%></em>) <%
 					if(isOwner&&CommonConfiguration.isCatalogEditable()) {%>
						<font size="-1">[<a href="encounter.jsp?number=<%=num%>&edit=size">edit</a>]</font>
					<%
 					}
				}
 %>
		
		<!-- Display maximumDepthInMeters so long as show_maximumDepthInMeters is not false in commonCOnfiguration.properties-->
		<%
		if(CommonConfiguration.showProperty("maximumDepthInMeters")){
		%>
		<p class="para"><strong><%=encprops.getProperty("depth") %></strong><br /> 
		<%
            	if(enc.getDepth()>=0) {
            %> <%=enc.getDepth()%> <%=enc.getMeasureUnits()%> <%
 	  			} else {
 	  		%> <%=encprops.getProperty("unknown") %><%
 	  			} 
				if(isOwner&&CommonConfiguration.isCatalogEditable()) {
 	  		%><font size="-1">[<a href="encounter.jsp?number=<%=num%>&edit=depth#depth">edit</a>]</font>
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
		<p class="para"><strong><%=encprops.getProperty("elevation") %></strong><br /> 
		
			<%=enc.getMaximumElevationInMeters()%> meters
		<%
 	 

		if(isOwner&&CommonConfiguration.isCatalogEditable()) {
 	  		%><font size="-1">[<a href="encounter.jsp?number=<%=num%>&edit=elevation#elevation">edit</a>]</font>
				<%
 	  	}
 	  		%>
		</p>
		<%
		}
		%>	
		<!-- End Display maximumElevationInMeters -->
			
				<p class="para"><strong><%=encprops.getProperty("sex") %></strong><br /> <%=enc.getSex()%> <%
 	if(isOwner&&CommonConfiguration.isCatalogEditable()) {
 %><font size="-1">[<a
					href="encounter.jsp?number=<%=num%>&edit=sex#sex">edit</a>]</font>
				<%
 	}
 %>
				<p class="para"><strong><%=encprops.getProperty("scarring") %></strong><br /> <%=enc.getDistinguishingScar()%>
	<%
 	if(isOwner&&CommonConfiguration.isCatalogEditable()) {
 	%>
 	<font size="-1">[<a href="encounter.jsp?number=<%=num%>&edit=scar#scar">edit</a>]</font>
	<%
 	}
 	%>

<p class="para"><strong><%=encprops.getProperty("behavior") %></strong> <br /> 
<%
if(enc.getBehavior()!=null){
%>
<%=enc.getBehavior()%>
<%
}
else {
%>
<%=encprops.getProperty("none")%>
<%
}
if(isOwner&&CommonConfiguration.isCatalogEditable()) {
 	%>
 	 <font size="-1">[<a href="encounter.jsp?number=<%=num%>&edit=behavior#behavior">edit</a>]</font>
	<%
 	}
%>
</p>
<%

if(enc.getDynamicProperties()!=null){
		 //let's create a TreeMap of the properties
        StringTokenizer st=new StringTokenizer(enc.getDynamicProperties(), ";");
        while(st.hasMoreTokens()){
          String token = st.nextToken();
          int equalPlace=token.indexOf("=");
		  String nm=token.substring(0,(equalPlace));
		  String vl=token.substring(equalPlace+1);
		  %>
		  <p class="para"><img align="absmiddle" src="../images/lightning_dynamic_props.gif"> <strong><%=nm%></strong><br />  <%=vl%>
		  <%
		  if(isOwner&&CommonConfiguration.isCatalogEditable()) {
 		  %>
 		       <font size="-1">[<a href="encounter.jsp?number=<%=num%>&edit=dynamicproperty&name=<%=nm%>#dynamicproperty">edit</a>]</font>
		  <%
 	      }
 		  %>
		  </p>
		  
		  
		  
		  <%
        }

%>


<%
}
%>

		<p class="para"><strong><%=encprops.getProperty("comments") %></strong><br /> <%=enc.getComments()%><br />
				<%
      	if(isOwner&&CommonConfiguration.isCatalogEditable()) {
      %><font size="-1">[<a href="encounter.jsp?number=<%=num%>&edit=comments#comments">edit</a>]</font>
				<%
      	}
      %>
				
				</p>


				<p class="para"><strong><%=encprops.getProperty("submitter") %></strong> <%
 	if(isOwner&&CommonConfiguration.isCatalogEditable()) {
 %><font size="-1">[<a href="encounter.jsp?number=<%=num%>&edit=contact#contact">edit</a>]</font>
				<%
 	}
 %><br /> <%=enc.getSubmitterName()%><br /> <%
		if (isOwner) {
			
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

				<p class="para"><strong><%=encprops.getProperty("photographer") %></strong> <%
 	if(isOwner&&CommonConfiguration.isCatalogEditable()) {
 %><font size="-1">[<a
					href="encounter.jsp?number=<%=num%>&edit=contact#contact">edit</a>]</font>
				<%
 	}
 %><br /> <%=enc.getPhotographerName()%><br /> <%
	if (isOwner) {
%> <%=enc.getPhotographerEmail()%><br /> <%=enc.getPhotographerPhone()%><br />
				<%=enc.getPhotographerAddress()%>


				<p class="para"><strong><%=encprops.getProperty("inform_others") %></strong> <%
 	if(isOwner&&CommonConfiguration.isCatalogEditable()) {
 %><font size="-1">[<a href="encounter.jsp?number=<%=num%>&edit=others#others">edit</a>]</font>
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
		%><%=encprops.getProperty("none") %><%
			}
		%> <%
	}
 
		 if (isOwner) {
%>
	<!-- Display spot patterning so long as show_spotpatterning is not false in commonCOnfiguration.properties-->
		<%
		if(CommonConfiguration.useSpotPatternRecognition()){
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
				
				
				
	
				<td width="250" align="left" valign="top">
				<p class="para"><img align="absmiddle" src="../images/Crystal_Clear_device_camera.gif" width="37px" height="*"><strong>&nbsp;<%=encprops.getProperty("images")%></strong><br /> <%
	  				if (session.getAttribute("logged")!=null) {
	  			%> <em><%=encprops.getProperty("click2view")%></em>
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
								<td class="para"><em><%=encprops.getProperty("image") %> <%=imageCount%></em></td>
							</tr>
							<%
					if (isOwner) {
				%>
							<tr>
								<td class="para"><img align="absmiddle"
									src="../images/Crystal_Clear_action_find.gif"> <strong><%=encprops.getProperty("image_commands") %></strong>:<br /> <font size="-1">
								 [<a
									href="../kwSearch.jsp?primaryImageName=<%=(num+"/"+(addTextFile.replaceAll(" ","%20")))%>"><%=encprops.getProperty("look4photos") %></a>] </font></td>
							</tr>

							<%
				}
				if (isOwner) {
					int totalKeywords=myShepherd.getNumKeywords();
					
					
					
			%>

							<tr>
								<td class="para">
								<%
		 						if (isOwner&&CommonConfiguration.isCatalogEditable()) {
		 						%>
								<img align="absmiddle" src="../images/cancel.gif"> <strong><%=encprops.getProperty("remove_keyword") %></strong>
								<%
		 						}
		 						else {
								%>
								<strong><%=encprops.getProperty("matchingKeywords") %></strong>
								<%
		 						}
								%>
								<br /> 
								<%
								Iterator indexes=myShepherd.getAllKeywords();
								if(totalKeywords>0){
								boolean haveAddedKeyword=false;
								for(int m=0;m<totalKeywords;m++) {
									Keyword word=(Keyword)indexes.next();
									if(word.isMemberOf(addText)){
										haveAddedKeyword=true;
							
									if(CommonConfiguration.isCatalogEditable()){
									%>
									<a href="../KeywordHandler?number=<%=num%>&action=removePhoto&photoName=<%=addTextFile%>&keyword=<%=word.getIndexname()%>">
									<%
									}
									%>
									"<%=word.getReadableName()%>"
									<%
									if(CommonConfiguration.isCatalogEditable()){
									%>
									</a>
									<%
									}
									%>
									&nbsp;
								<%
								} //end if
							} //end for
							if(!haveAddedKeyword){%>
								
								<%=encprops.getProperty("none_assigned")%>
								
							<% }
						} //end if
						else { %>
							<%=encprops.getProperty("none_defined")%>
							
							
						<% }
							%>
								</td>
							</tr>
							<%
							if(CommonConfiguration.isCatalogEditable()){
							%>
							<tr>
								<td>
								
								<table>
									<tr>
										<td class="para"><img align="absmiddle"
											src="../images/keyword_icon_small.gif"> <strong><%=encprops.getProperty("add_keyword") %> <a href="<%=CommonConfiguration.getWikiLocation()%>photo_keywords" target="_blank">
											<img src="../images/information_icon_svg.gif" alt="Help" border="0" align="absmiddle" /></a></strong></td>
									</tr>
									<tr>
										<td class="para">
										<%
										if(totalKeywords>0){
										%>
										<form action="../KeywordHandler" method="post" name="keyword">
											<select name="keyword" id="keyword">
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

											</select> 
											<input name="number" type="hidden" value=<%=num%>> 
											<input name="action" type="hidden" value="addPhoto"> 
											<input name="photoName" type="hidden" value="<%=addTextFile%>">
											<input name="AddKW" type="submit" id="AddKW" value="<%=encprops.getProperty("add") %>">
										  </form>
											<%
										}
										else {
											%>
											<%=encprops.getProperty("no_keywords") %>
										<%
										}
										%>
											
										</td>
									</tr>
								</table>
								
								</td>
							</tr>
							<%
							}
							%>

							<%
							
					
								}
							%>
							<tr>
								<td>
								<%
				boolean isBMP=false;
				boolean isVideo=false;
				if(addTextFile.toLowerCase().indexOf(".bmp")!=-1) {isBMP=true;}
				if(myShepherd.isAcceptableVideoFile(addTextFile)) {isVideo=true;}
			if(isOwner&&(!isBMP)&&(!isVideo)) {
			%> 
				<a href="<%=num%>/<%=addTextFile%>" class="highslide" onclick="return hs.expand(this)" title="Click to enlarge">
			<%
			}
			else if(isOwner) {
				%> 
				<a href="<%=addText%>" class="highslide" onclick="return hs.expand(this)" title="Click to enlarge"> <%
			}
					
					String thumbLocation="file-"+num+"/"+imageCount+".jpg";
					File processedImage=new File(getServletContext().getRealPath(("/"+CommonConfiguration.getImageDirectory()+"/"+num+"/"+imageCount+".jpg")));

					
					
					int intWidth = 250;
					int intHeight = 200;
					int thumbnailHeight=200;
					int thumbnailWidth = 250;
					
					File file2process=new File(getServletContext().getRealPath(("/"+CommonConfiguration.getImageDirectory()+"/"+addText)));
					Dimension imageDimensions = org.apache.sanselan.Sanselan.getImageSize(file2process);
						
					String width = Double.toString(imageDimensions.getWidth());
					String height = Double.toString(imageDimensions.getHeight());
					
					intHeight=((new Double(height)).intValue());
					intWidth=((new Double(width)).intValue());
				
					if(intWidth>thumbnailWidth){
						double scalingFactor = intWidth/thumbnailWidth;
						intWidth=(int)(intWidth/scalingFactor);
						intHeight=(int)(intHeight/scalingFactor);
						if(intHeight<thumbnailHeight){thumbnailHeight = intHeight;}
					}
					else{
						thumbnailWidth = intWidth;
						thumbnailHeight = intHeight;
					}
					int copyrightTextPosition=(int)(thumbnailHeight/3);
					
					
					
					if(isVideo) {
				%> <img width="250" height="200" alt="video <%=enc.getLocation()%>"
									src="../images/video.jpg" border="0" align="left" valign="left">
		
								</a>
								
								
								<%
					
			
							}
					else if ((!processedImage.exists())&&(!haveRendered)) {
								haveRendered=true;
								//System.out.println("Using DynamicImage to render thumbnail: "+num);
								//System.gc();
								

								
								
						%> 
						<di:img width="<%=thumbnailWidth %>" height="<%=thumbnailHeight %>"
									imgParams="rendering=speed,quality=low" border="0"
									output="<%=thumbLocation%>" expAfter="0" threading="limited"
									fillPaint="#FFFFFF" align="left" valign="left">
								<di:image width="<%=Integer.toString(thumbnailWidth) %>" height="<%=Integer.toString(thumbnailHeight) %>" composite="70" srcurl="<%=addText%>" />
								<di:rectangle x="0" y="<%=copyrightTextPosition %>" width="<%=thumbnailWidth %>" composite="30" height="13" fillPaint="#99CCFF"></di:rectangle>
								
								<di:text x="4" y="<%=copyrightTextPosition %>" align="left" font="Arial-bold-11" fillPaint="#000000"><%=encprops.getProperty("nocopying") %></di:text>
						 </di:img> 
						 <img width="<%=thumbnailWidth %>" alt="photo <%=enc.getLocation()%>" src="<%=(num+"/"+imageCount+".jpg")%>" border="0" align="left" valign="left"> <%
				if (isOwner) {
			%>
								</a>
								<%
				}
			%> <%
				} else if((!processedImage.exists())&&(haveRendered)) {
			%> <img width="250" height="200" alt="photo <%=enc.getLocation()%>"
									src="../images/processed.gif" border="0" align="left" valign="left">
								<%
					if (session.getAttribute("logged")!=null) {
				%></a>
								<%
					}
				%> <%
				}else {
			%> <img id="img<%=imageCount%> " width="<%=thumbnailWidth %>" alt="photo <%=enc.getLocation()%>"
									src="<%=(num+"/"+imageCount+".jpg")%>" border="0" align="left"
									valign="left"> <%
					if (session.getAttribute("logged")!=null) {
				%></a>
					<div class="highslide-caption">
					<h3><%=encprops.getProperty("imageMetadata") %></h3>
					<table ><tr>
						<td align="left" valign="top">
   								
   						<table>
   						<tr><td align="left" valign="top"><span class="caption"><%=encprops.getProperty("location") %>: <%=enc.getLocation() %></span></td></tr>
										<tr><td><span class="caption"><%=encprops.getProperty("locationID") %>: <%=enc.getLocationID() %></span></td></tr>
										<tr><td><span class="caption"><%=encprops.getProperty("date") %>: <%=enc.getDate() %></span></td></tr>
										<tr><td><span class="caption"><%=encprops.getProperty("individualID") %>: <a href="../individuals.jsp?number=<%=enc.getIndividualID() %>"><%=enc.getIndividualID() %></a></span></td></tr>
										<tr><td><span class="caption"><%=encprops.getProperty("title") %>: <a href="encounter.jsp?number=<%=enc.getCatalogNumber() %>"><%=enc.getCatalogNumber() %></a></span></td></tr>
										<tr>
										<td><span class="caption">
											<%=encprops.getProperty("matchingKeywords") %>
											<%
									        Iterator it = myShepherd.getAllKeywords();  
											while(it.hasNext()) {
												Keyword word=(Keyword)it.next();
									              
									              
									                if(word.isMemberOf(num+"/"+addTextFile)) {
									                	%>
														<br /><%= word.getReadableName()%>
														
														<%
														
									                  
									                }
									          
									            }
											%>
										</span></td>
										</tr>
   						
   						</table>
   						
   								
   						</td>
					
					
					
										<%
										if(CommonConfiguration.showEXIFData()){
										%>
					<td align="left" valign="top">
					
					
					<span class="caption">
						<ul>
					<%
					if((addTextFile.toLowerCase().endsWith("jpg"))||(addTextFile.toLowerCase().endsWith("jpeg"))){
						File exifImage=new File(getServletContext().getRealPath(("/"+CommonConfiguration.getImageDirectory()+"/"+num+"/"+addTextFile)));
						Metadata metadata = JpegMetadataReader.readMetadata(exifImage);
						// iterate through metadata directories 
						Iterator directories = metadata.getDirectoryIterator();
						while (directories.hasNext()) { 
							Directory directory = (Directory)directories.next(); 
							// iterate through tags and print to System.out  
							Iterator tags = directory.getTagIterator(); 
							while (tags.hasNext()) { 
								Tag tag = (Tag)tags.next(); 
								
								%>
								<li><%=tag.toString() %></li>
								<% 
							} 
						} 
					
					}					
					%>
   									
   								</ul>
   								</span>
   								</td>
   					<%
					}
   					%>
   							
   								
   								</tr></table>
   
   					</div>
   								
								<%
					}
				
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
							<p><img src="../alert.gif"> <strong><%=encprops.getProperty("badfile") %>:</strong> <%=addTextFile%> <%
					if (isOwner&&CommonConfiguration.isCatalogEditable()) {
				%> <br /><a href="/encounterRemoveImage?number=<%=(num)%>&filename=<%=(addTextFile.replaceAll(" ","%20"))%>&position=<%=imageCount%>"><%=encprops.getProperty("clickremove") %></a></p>
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
							<p><%=e.getMessage()%></p>
							<%
						}
					} //close while
					%>
						
				</table>

				<p class="para">
				<%
		 			if (isOwner&&CommonConfiguration.isCatalogEditable()) {
		 		%>
				<table width="250" bgcolor="#99CCFF">
					<tr>
						<td class="para">
						<form action="../EncounterAddImage" method="post"
							enctype="multipart/form-data" name="encounterAddImage"><input
							name="action" type="hidden" value="imageadder" id="action">
						<input name="number" type="hidden" value="<%=num%>" id="shark">
						<strong><img align="absmiddle"
							src="../images/upload_small.gif" /> <%=encprops.getProperty("addfile") %>:</strong><br />
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
							align="absmiddle" src="../images/cancel.gif" /> <%=encprops.getProperty("removefile") %>: </strong> <select name="position">
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
		 	if (isOwner&&CommonConfiguration.useSpotPatternRecognition()&&((enc.getNumSpots()>0)||(enc.getNumRightSpots()>0))) {
		 	

		 			
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
		 			//ImageInfo iInfo=new ImageInfo();
		 			
					
		 			
		 			if((uploadedFile.exists())&&(uploadedFile.length()>0)&&(enc.getNumSpots()>0)) {

		 				Dimension imageDimensions = org.apache.sanselan.Sanselan.getImageSize(uploadedFile);
		 				
		 				//iInfo.setInput(new FileInputStream(uploadedFile));
		 				if (!extractImage.exists()) {
		 					//System.out.println("Made it here.");
		 					
		 					height+=Double.toString(imageDimensions.getHeight());
		 					width+=Double.toString(imageDimensions.getWidth());
		 					//height+=iInfo.getHeight();
		 					//width+=iInfo.getWidth();
		 					
		 					
		 					
		 					//System.out.println(height+"and"+width);
		 					int intHeight=((new Double(height)).intValue());
		 					int intWidth=((new Double(width)).intValue());
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
									
									//iInfo=new ImageInfo();
									Dimension imageDimensions = org.apache.sanselan.Sanselan.getImageSize(uploadedRightFile);
		 				
									//iInfo.setInput(new FileInputStream(uploadedRightFile));
									if (!extractRightImage.exists()) {
										//System.out.println("Made it here.");
										//heightR+=iInfo.getHeight();
										//widthR+=iInfo.getWidth();
										//System.out.println(height+"and"+width);
										
										heightR+=Double.toString(imageDimensions.getHeight());
		 								widthR+=Double.toString(imageDimensions.getWidth());
										
										
										int intHeightR=((new Double(heightR)).intValue());
										int intWidthR=((new Double(widthR)).intValue());
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
				
		
		
		<%
		if(CommonConfiguration.allowAdoptions()){
		%>
				<div class="module">
					<jsp:include page="encounterAdoptionEmbed.jsp" flush="true">
						<jsp:param name="num" value="<%=num%>" />
				</jsp:include>
				</div>
		<%
		}
		%>			  </td>
			</tr>
	  </table>
		<p>
		<%
	  	  	  	if (request.getParameter("noscript")==null) {
	  	  	  %>
		<hr>
		<p><a name="map"><strong><img
			src="../images/2globe_128.gif" width="56" height="56"
			align="absmiddle" /></a><%=encprops.getProperty("mapping") %></strong></p>
		<%
	  	if((enc.getDWCDecimalLatitude()!=null)&&(enc.getDWCDecimalLongitude()!=null)) {
	  %>
		<p><%=encprops.getProperty("map_note") %></p>


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
		<p><%=encprops.getProperty("nomap") %></p>
		<br /> <%
		
		}
		
		
		if(session.getAttribute("logged")!=null){
		%> 
		
		
		<br />
		<hr>
		<table><tr><td valign="top">
		<img align="absmiddle"  src="../images/Crystal_Clear_app_kaddressbook.gif"> 
		</td>
		<td valign="top">
		<%=encprops.getProperty("auto_comments")%>: </p>
		<%
		if (enc.getRComments()!=null) {
		%>
			<p class="para"><%=enc.getRComments().replaceAll("\n","<br />")%></p>
		<%
		}
		if(CommonConfiguration.isCatalogEditable()){
		%>
		<form action="../EncounterAddComment" method="post" name="addComments">
		<p class="para">
			<input name="user" type="hidden" value="<%=request.getRemoteUser()%>" id="user"> 
			<input name="number" type="hidden" value="<%=enc.getEncounterNumber()%>" id="number"> 
			<input name="action" type="hidden" value="enc_comments" id="action"> 

		<p>
			<textarea name="comments" cols="50" id="comments"></textarea> <br />
			<input name="Submit" type="submit" value="<%=encprops.getProperty("add_comment")%>">
		</p>
		</form>
		</td></tr></table>
		<%
		}
		}
	  	}
		
		%>
		
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

		<p><font size="-1"><%=encprops.getProperty("trackingEmails")%>: <%
	  
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


