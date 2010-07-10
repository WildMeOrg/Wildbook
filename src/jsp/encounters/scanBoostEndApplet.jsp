<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=iso-8859-1" language="java"
	import="org.ecocean.*, javax.jdo.*, java.lang.StringBuffer, java.lang.Integer, java.util.*, java.net.URL, java.net.URLConnection, java.io.InputStream, java.io.IOException, org.dom4j.*, org.dom4j.io.SAXReader, java.io.File"%>
<html>
<%
String num=request.getParameter("number");
boolean xmlOK=false;
SAXReader xmlReader = new SAXReader();
File file=new File("foo");
String scanDate="";
String C="";
String R="";
String epsilon="";
String Sizelim="";
String maxTriangleRotation="";
String side2="";
String fileSider="";
String sessionId=session.getId();
Shepherd myShepherd=new Shepherd();
%>

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

#tabmenu a,a.active {
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
<div id="page"><jsp:include page="../header.jsp" flush="true">
	<jsp:param name="isResearcher"
		value="<%=request.isUserInRole("researcher")%>" />
	<jsp:param name="isManager"
		value="<%=request.isUserInRole("manager")%>" />
	<jsp:param name="isReviewer"
		value="<%=request.isUserInRole("reviewer")%>" />
	<jsp:param name="isAdmin" value="<%=request.isUserInRole("admin")%>" />
</jsp:include>
<body>




<div id="main">

<ul id="tabmenu">
	<li><a
		href="encounter.jsp?number=<%=request.getParameter("number")%>">Encounter
	<%=request.getParameter("number")%></a></li>


	<%	
	File finalXMLFile2;
	if((request.getParameter("rightSide")!=null)&&(request.getParameter("rightSide").equals("true"))) {
		finalXMLFile2=new File((new File(".")).getCanonicalPath()+File.separator+"webapps"+File.separator+"ROOT"+File.separator+"encounters"+File.separator+num+File.separator+"lastFullRightScan.xml");
		side2="right";
		fileSider="&rightSide=true";
	}
	else {
		finalXMLFile2=new File((new File(".")).getCanonicalPath()+File.separator+"webapps"+File.separator+"ROOT"+File.separator+"encounters"+File.separator+num+File.separator+"lastFullScan.xml");
	}	
	if(finalXMLFile2.exists()) {
%>
	<li><a
		href="scanEndApplet.jsp?writeThis=true&number=<%=request.getParameter("number")%><%=fileSider%>">Modified
	Groth</a></li>
	<%
}

	 
	File finalXMLFile;
	if((request.getParameter("rightSide")!=null)&&(request.getParameter("rightSide").equals("true"))) {
		finalXMLFile=new File((new File(".")).getCanonicalPath()+File.separator+"webapps"+File.separator+"ROOT"+File.separator+"encounters"+File.separator+num+File.separator+"lastFullRightI3SScan.xml");
		side2="right";
		fileSider="&rightSide=true";
	}
	else {
		finalXMLFile=new File((new File(".")).getCanonicalPath()+File.separator+"webapps"+File.separator+"ROOT"+File.separator+"encounters"+File.separator+num+File.separator+"lastFullI3SScan.xml");
	}	
	if(finalXMLFile.exists()) {
%>

	<li><a
		href="i3sScanEndApplet.jsp?writeThis=true&number=<%=request.getParameter("number")%>&I3S=true<%=fileSider%>">I3S</a></li>

	<%
}
%>
	<li><a class="active">sharkBoost</a></li>
</ul>

<%
Vector initresults=new Vector();
Document doc;
Element root;
String side="left";

//read from the written XML here if flagged
try {
	if((request.getParameter("rightSide")!=null)&&(request.getParameter("rightSide").equals("true"))) {
		file=new File((new File(".")).getCanonicalPath()+File.separator+"webapps"+File.separator+"ROOT"+File.separator+"encounters"+File.separator+num+File.separator+"lastBoostRightScan.xml");
		side="right";
	}
	else {
		file=new File((new File(".")).getCanonicalPath()+File.separator+"webapps"+File.separator+"ROOT"+File.separator+"encounters"+File.separator+num+File.separator+"lastBoostScan.xml");
	}
	doc = xmlReader.read(file);
	root=doc.getRootElement();
	scanDate=root.attributeValue("scanDate");
	xmlOK=true;

}
catch(java.io.IOException ioe) {
	System.out.println("Error accessing the stored boost scan XML data for encounter: "+num);
	ioe.printStackTrace();
	xmlOK=false;
}


MatchObject[] matches=new MatchObject[0];
%>

<p>
<h2>sharkBoost Scan Results <a
	href="<%=CommonConfiguration.getWikiLocation()%>scan_results"
	target="_blank"><img src="../images/information_icon_svg.gif"
	alt="Help" border="0" align="absmiddle"></a></h2>
</p>
<p><strong>The following encounter(s) received the highest
match values against a <%=side%>-side scan of encounter# <a
	href="http://<%=CommonConfiguration.getURLLocation()%>/encounters/encounter.jsp?number=<%=num%>"><%=num%></a>.</strong></p>


<% 
 if (xmlOK) {%>
<p><img src="../alert.gif" hspace="2" vspace="2" align="absmiddle"><strong>Saved
scan data may be old and invalid. Check the date below and run a fresh
scan for the latest results.</strong></p>
<p><em>Date of scan: <%=scanDate%></em></p>
<%}%>
<table width="524" border="1" cellspacing="0" cellpadding="5">
	<tr>

		<td width="355" align="left" valign="top">
		<table width="100%" border="1" align="left" cellpadding="3">
			<tr align="left" valign="top">
				<td><strong>Shark</strong></td>
				<td><strong> Encounter</strong></td>

				<td><strong>Boosted Score </strong></td>

			</tr>
			<%

		doc = xmlReader.read(file);
		root=doc.getRootElement();
		Iterator matchsets=root.elementIterator("match");
		while (matchsets.hasNext()) {
			Element match=(Element)matchsets.next();
			List encounters=match.elements("encounter");
			Element enc1=(Element)encounters.get(0);
			Element enc2=(Element)encounters.get(1);
			%>
			<tr align="left" valign="top">
				<td>
				<table width="62">

					<tr>
						<td width="60" align="left"><a
							href="http://<%=CommonConfiguration.getURLLocation()%>/individuals.jsp?number=<%=enc1.attributeValue("assignedToShark")%>"><%=enc1.attributeValue("assignedToShark")%></a></td>
					</tr>
				</table>
				</td>
				<%if (enc1.attributeValue("number").equals("N/A")) {%>
				<td>N/A</td>
				<%} else {%>
				<td><a
					href="http://<%=CommonConfiguration.getURLLocation()%>/encounters/encounter.jsp?number=<%=enc1.attributeValue("number")%>"><%=enc1.attributeValue("number")%></a></td>
				<%
			 }
			
				String finalscore="&nbsp;";
				try{
					if(match.attributeValue("matchScore")!=null) {finalscore=match.attributeValue("matchScore");}
				}
				catch(NullPointerException npe) {}
				
				//trim the length of finalscore
				if(finalscore.length()>7) {finalscore=finalscore.substring(0,6);}

			 %>
				<td><%=finalscore%></td>

			</tr>

			<%	
			
		
		}




	
  %>

		</table>
		</td>
	</tr>
</table>
</tr>
</table>

<p><font size="+1">Visualizations for Potential Matches (as
scored above)</font></p>

<p>
<%
String feedURL="http://"+CommonConfiguration.getURLLocation()+"/TrackerFeed?number="+num;
String baseURL="http://"+CommonConfiguration.getURLLocation()+"/encounters/";



myShepherd=null;
doc=null;
root=null;
initresults=null;
file=null;
xmlReader=null;

//System.out.println("Base URL is: "+baseURL);
if(xmlOK) {
	if((request.getParameter("rightSide")!=null)&&(request.getParameter("rightSide").equals("true"))) {
		feedURL=baseURL+num+"/lastBoostRightScan.xml?";
		}
	else {
		feedURL=baseURL+num+"/lastBoostScan.xml?";
	}
}
String rightSA="";
if((request.getParameter("rightSide")!=null)&&(request.getParameter("rightSide").equals("true"))) {
	rightSA="&filePrefix=extractRight";
}
System.out.println("I made it to the Flash without exception.");
%> <OBJECT id=sharkflash
	codeBase=http://download.macromedia.com/pub/shockwave/cabs/flash/swflash.cab#version=6,0,0,0
	height=450 width=800 classid=clsid:D27CDB6E-AE6D-11cf-96B8-444553540000>
	<PARAM NAME="movie"
		VALUE="tracker.swf?sessionId=<%=sessionId%>&rootURL=<%=CommonConfiguration.getURLLocation()%>&baseURL=<%=baseURL%>&feedurl=<%=feedURL%><%=rightSA%>">
	<PARAM NAME="quality" VALUE="high">
	<PARAM NAME="scale" VALUE="exactfit">
	<PARAM NAME="bgcolor" VALUE="#ddddff"><EMBED
		src="tracker.swf?sessionId=<%=sessionId%>&rootURL=<%=CommonConfiguration.getURLLocation()%>&baseURL=<%=baseURL%>&feedurl=<%=feedURL%>&time=<%=System.currentTimeMillis()%><%=rightSA%>"
		quality=high scale=exactfit bgcolor=#ddddff swLiveConnect=TRUE
		WIDTH="800" HEIGHT="450" NAME="sharkflash" ALIGN=""
		TYPE="application/x-shockwave-flash"
		PLUGINSPAGE="http://www.macromedia.com/go/getflashplayer"></EMBED>
</OBJECT></p>
<jsp:include page="../footer.jsp" flush="true" /></div>
</div>
<!-- end page --></div>
<!--end wrapper -->
</body>
</html>
