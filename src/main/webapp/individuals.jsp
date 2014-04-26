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

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java"
         import="com.drew.imaging.jpeg.JpegMetadataReader,com.drew.metadata.Directory, 	   
		 org.joda.time.DateTime,com.drew.metadata.Metadata,com.drew.metadata.Tag,org.ecocean.*,org.ecocean.social.*,org.ecocean.servlet.ServletUtilities,java.io.File, java.util.*, org.ecocean.genetics.*" %>

<%
String context="context0";
context=ServletUtilities.getContext(request);
  //handle some cache-related security
  response.setHeader("Cache-Control", "no-cache"); //Forces caches to obtain a new copy of the page from the origin server
  response.setHeader("Cache-Control", "no-store"); //Directs caches not to store the page under any circumstance
  response.setDateHeader("Expires", 0); //Causes the proxy cache to see the page as "stale"
  response.setHeader("Pragma", "no-cache"); //HTTP 1.0 backward compatibility

  //setup data dir
  String rootWebappPath = getServletContext().getRealPath("/");
  File webappsDir = new File(rootWebappPath).getParentFile();
  File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));
  //if(!shepherdDataDir.exists()){shepherdDataDir.mkdir();}
  File encountersDir=new File(shepherdDataDir.getAbsolutePath()+"/encounters");
  //if(!encountersDir.exists()){encountersDir.mkdir();}
  //File thisEncounterDir = new File(encountersDir, number);

//setup our Properties object to hold all properties
  Properties props = new Properties();
  //String langCode = "en";
  String langCode=ServletUtilities.getLanguageCode(request);
  


  //load our variables for the submit page

 // props.load(getClass().getResourceAsStream("/bundles/" + langCode + "/individuals.properties"));
  props = ShepherdProperties.getProperties("individuals.properties", langCode);
	
  String markedIndividualTypeCaps = props.getProperty("markedIndividualTypeCaps");
  String nickname = props.getProperty("nickname");
  String nicknamer = props.getProperty("nicknamer");
  String alternateID = props.getProperty("alternateID");
  String sex = props.getProperty("sex");
  String setsex = props.getProperty("setsex");
  String numencounters = props.getProperty("numencounters");
  String encnumber = props.getProperty("number");
  String dataTypes = props.getProperty("dataTypes");
  String date = props.getProperty("date");
  String size = props.getProperty("size");
  String spots = props.getProperty("spots");
  String location = props.getProperty("location");
  String mapping = props.getProperty("mapping");
  String mappingnote = props.getProperty("mappingnote");
  String setAlternateID = props.getProperty("setAlternateID");
  String setNickname = props.getProperty("setNickname");
  String unknown = props.getProperty("unknown");
  String noGPS = props.getProperty("noGPS");
  String update = props.getProperty("update");
  String additionalDataFiles = props.getProperty("additionalDataFiles");
  String delete = props.getProperty("delete");
  String none = props.getProperty("none");
  String addDataFile = props.getProperty("addDataFile");
  String sendFile = props.getProperty("sendFile");
  String researcherComments = props.getProperty("researcherComments");
  String edit = props.getProperty("edit");
  String matchingRecord = props.getProperty("matchingRecord");
  String tryAgain = props.getProperty("tryAgain");
  String addComments = props.getProperty("addComments");
  String record = props.getProperty("record");
  String getRecord = props.getProperty("getRecord");
  String allEncounters = props.getProperty("allEncounters");
  String allIndividuals = props.getProperty("allIndividuals");

  String name = request.getParameter("number").trim();
  Shepherd myShepherd = new Shepherd(context);



%>

<html>
<head prefix="og:http://ogp.me/ns#">

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
        

<!-- social meta start -->
<meta property="og:site_name" content="<%=CommonConfiguration.getHTMLTitle(context) %> - <%=props.getProperty("markedIndividualTypeCaps") %> <%=request.getParameter("number") %>" />

<link rel="canonical" href="http://<%=CommonConfiguration.getURLLocation(request) %>/individuals.jsp?number=<%=request.getParameter("number") %>" />

<meta itemprop="name" content="<%=props.getProperty("markedIndividualTypeCaps")%> <%=request.getParameter("number")%>" />
<meta itemprop="description" content="<%=CommonConfiguration.getHTMLDescription(context)%>" />
<%
if (request.getParameter("number")!=null) {
	myShepherd.beginDBTransaction();
		if(myShepherd.isMarkedIndividual(name)){
			MarkedIndividual indie=myShepherd.getMarkedIndividual(name);
			Vector myEncs=indie.getEncounters();
			int numEncs=myEncs.size();
			for(int p=0;p<numEncs;p++){
				Encounter metaEnc = (Encounter)myEncs.get(p);
				int numImgs=metaEnc.getImages().size();
				if((metaEnc.getImages()!=null)&&(numImgs>0)){
					for(int b=0;b<numImgs;b++){
						SinglePhotoVideo metaSPV=metaEnc.getImages().get(b);
						%>
						<meta property="og:image" content="http://<%=CommonConfiguration.getURLLocation(request) %>/<%=CommonConfiguration.getDataDirectoryName(context) %>/encounters/<%=metaEnc.getCatalogNumber()+"/"+metaSPV.getFilename()%>" />
						<link rel="image_src" href="http://<%=CommonConfiguration.getURLLocation(request) %>/<%=CommonConfiguration.getDataDirectoryName(context) %>/encounters/<%=(metaEnc.getCatalogNumber()+"/"+metaSPV.getFilename())%>" / >
<%
			}
		}
		}
}
		myShepherd.rollbackDBTransaction();
}
%>

<meta property="og:title" content="<%=CommonConfiguration.getHTMLTitle(context) %> - <%=props.getProperty("markedIndividualTypeCaps") %> <%=request.getParameter("number") %>" />
<meta property="og:description" content="<%=CommonConfiguration.getHTMLDescription(context)%>" />

<meta property="og:url" content="http://<%=CommonConfiguration.getURLLocation(request) %>/individuals.jsp?number=<%=request.getParameter("number") %>" />


<meta property="og:type" content="website" />

<!-- social meta end -->
 
  
  <style type="text/css">
    <!--
    .style1 {
      color: #000000;
      font-weight: bold;
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
      border-width: 0px 0px 0px 0px;
      margin: 0px;
    }

    div.scroll {
      height: 200px;
      overflow: auto;
      border: 1px solid #666;
      background-color: #ccc;
      padding: 8px;
    }

table.tissueSample {
    border-width: 1px;
    border-spacing: 2px;
    border-color: gray;
    border-collapse: collapse;
    background-color: white;
}
table.tissueSample th {
    border-width: 1px;
    padding: 1px;
    border-style: solid;
    border-color: gray;
    background-color: #99CCFF;
    -moz-border-radius: ;
}
table.tissueSample td {
    border-width: 1px;
    padding: 2px;
    border-style: solid;
    border-color: gray;
    background-color: white;
    -moz-border-radius: ;
}
    -->
  </style>


  <!--
    1 ) Reference to the files containing the JavaScript and CSS.
    These files must be located on your server.
  -->

  <script type="text/javascript" src="highslide/highslide/highslide-with-gallery.js"></script>
  <link rel="stylesheet" type="text/css" href="highslide/highslide/highslide.css"/>

  <!--
    2) Optionally override the settings defined at the top
    of the highslide.js file. The parameter hs.graphicsDir is important!
  -->

  <script type="text/javascript">
    hs.graphicsDir = 'highslide/highslide/graphics/';
    hs.align = 'center';
    hs.transitions = ['expand', 'crossfade'];
    hs.outlineType = 'rounded-white';
    hs.fadeInOut = true;
    //hs.dimmingOpacity = 0.75;

    //define the restraining box
    hs.useBox = true;
    hs.width = 810;
    hs.height = 500;

    //block right-click user copying if no permissions available
    <%
    if(request.getUserPrincipal()==null){
    %>
    hs.blockRightClick = true;
    <%
    }
    %>

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

<!--  FACEBOOK SHARE BUTTON -->
<div id="fb-root"></div>
<script>(function(d, s, id) {
  var js, fjs = d.getElementsByTagName(s)[0];
  if (d.getElementById(id)) return;
  js = d.createElement(s); js.id = id;
  js.src = "//connect.facebook.net/en_US/all.js#xfbml=1";
  fjs.parentNode.insertBefore(js, fjs);
}(document, 'script', 'facebook-jssdk'));</script>

<!-- GOOGLE PLUS-ONE BUTTON -->
<script type="text/javascript">
  (function() {
    var po = document.createElement('script'); po.type = 'text/javascript'; po.async = true;
    po.src = 'https://apis.google.com/js/plusone.js';
    var s = document.getElementsByTagName('script')[0]; s.parentNode.insertBefore(po, s);
  })();
</script>

<link href="http://ajax.googleapis.com/ajax/libs/jqueryui/1.8.4/themes/base/jquery-ui.css" rel="stylesheet" type="text/css" />



</head>

<body <%if (request.getParameter("noscript") == null) {%>
onunload="GUnload()" <%}%>>
<div id="wrapper">
<div id="page">
<jsp:include page="header.jsp" flush="true">

	<jsp:param name="isAdmin" value="<%=request.isUserInRole(\"admin\")%>" />
</jsp:include>
  <script src="http://code.jquery.com/ui/1.10.2/jquery-ui.js"></script>



<div id="main">

<%
  if (CommonConfiguration.allowAdoptions(context)) {
	  ArrayList adoptions = myShepherd.getAllAdoptionsForMarkedIndividual(name,context);
	  int numAdoptions = adoptions.size();
	  if(numAdoptions>0){
%>
<div id="maincol-wide">
<%
}
  }
  else {
%>
<div id="maincol-wide-solo">
<%
}
%>
<div id="maintext">
<%
  myShepherd.beginDBTransaction();
  try {
    if (myShepherd.isMarkedIndividual(name)) {


      MarkedIndividual sharky = myShepherd.getMarkedIndividual(name);
      boolean isOwner = ServletUtilities.isUserAuthorizedForIndividual(sharky, request);

%>

<table><tr>
<td>
<span class="para"><img src="images/wild-me-logo-only-100-100.png" width="75px" height="75px" align="absmiddle"/></span>
</td>
<td valign="middle">
 <h1><strong> <%=markedIndividualTypeCaps %></strong>: <%=sharky.getIndividualID()%></h1>
<p class="caption"><em><%=props.getProperty("description") %></em></p>
 </td></tr></table>
 <p> <table><tr valign="middle">  
  <td>
    <!-- Google PLUS-ONE button -->
<g:plusone size="small" annotation="none"></g:plusone>
</td>
<td>
<!--  Twitter TWEET THIS button -->
<a href="https://twitter.com/share" class="twitter-share-button" data-count="none">Tweet</a>
<script>!function(d,s,id){var js,fjs=d.getElementsByTagName(s)[0];if(!d.getElementById(id)){js=d.createElement(s);js.id=id;js.src="//platform.twitter.com/widgets.js";fjs.parentNode.insertBefore(js,fjs);}}(document,"script","twitter-wjs");</script>
</td>
<td>
<!-- Facebook LIKE button -->
		<div class="fb-share-button" data-href="http://<%=CommonConfiguration.getURLLocation(request) %>/individuals.jsp?number=<%=request.getParameter("number") %>" data-type="button_count"></div></td>
						<%
if(CommonConfiguration.isIntegratedWithWildMe(context)){
%>
<td>
<a href="http://fb.wildme.org/wildme/public/profile/<%=sharky.getIndividualID()%>" target="_blank"><img src="images/wild-me-link.png" /></a>
</td>
<%
}
%>
</tr>
</table></p>
<a name="alternateid"></a>
<%
String altID="";
if(sharky.getAlternateID()!=null){
	altID=sharky.getAlternateID();
}

%>
<p><img align="absmiddle" src="images/alternateid.gif"> <%=alternateID %>:
  <%=altID%> <%if (isOwner && CommonConfiguration.isCatalogEditable(context)) {%><a style="color:blue;cursor: pointer;" id="alternateID"><img align="absmiddle" width="20px" height="20px" style="border-style: none;" src="images/Crystal_Clear_action_edit.png" /></a><%}%>

  
</p>


<!-- Now prep the popup dialog -->
<div id="dialogAlternateID" title="<%=setAlternateID %>" style="display:none">
<table border="1" cellpadding="1" cellspacing="0" bordercolor="#FFFFFF">

  <tr>
    <td align="left" valign="top">
      <form name="set_alternateid" method="post" action="IndividualSetAlternateID">
      	<input name="individual" type="hidden" value="<%=request.getParameter("number")%>" /> <%=alternateID %>:
        <input name="alternateid" type="text" id="alternateid" size="15" maxlength="150" value="<%=altID %>" /><br /> <input name="Name" type="submit" id="Name" value="<%=update %>"></form>
    </td>
  </tr>
</table>

</div>
                         		<!-- popup dialog script -->
<script>
var dlg = $("#dialogAlternateID").dialog({
  autoOpen: false,
  draggable: false,
  resizable: false,
  width: 600
});

$("a#alternateID").click(function() {
  dlg.dialog("open");
});
</script>


</p>
<%
    if(CommonConfiguration.showProperty("showTaxonomy",context)){
    
    String genusSpeciesFound=props.getProperty("notAvailable");
    if(sharky.getGenusSpecies()!=null){genusSpeciesFound=sharky.getGenusSpecies();}
    %>
    
        <p><img align="absmiddle" src="images/taxontree.gif">
          <%=props.getProperty("taxonomy")%>: <em><%=genusSpeciesFound%></em>
       </p>

<%
}
%>

<p>
  <%
    if (CommonConfiguration.allowNicknames(context)) {

      String myNickname = "";
      if (sharky.getNickName() != null) {
        myNickname = sharky.getNickName();
      }
      String myNicknamer = "";
      if (sharky.getNickNamer() != null) {
        myNicknamer = sharky.getNickNamer();
      }

  %>
  <table border="0"><tr><td>
  <%=nickname %>: <%=myNickname%></td>
  <td>
  <%if (isOwner && CommonConfiguration.isCatalogEditable(context)) {%><a id="nickname" style="color:blue;cursor: pointer;"><img align="absmiddle" width="20px" height="20px" style="border-style: none;" src="images/Crystal_Clear_action_edit.png" /></a><%}%>
  </td>
  </tr>
  <tr><td>
  <%=nicknamer %>: <%=myNicknamer%>
</td><td>&nbsp;</td>
</tr>
</table>
  <%
    }

%>
  <!-- Now prep the popup dialog -->
<div id="dialogNickname" title="<%=setNickname %>" style="display:none">
    <table border="1" cellpadding="1" cellspacing="0" bordercolor="#FFFFFF">

      <tr>
        <td align="left" valign="top">
          <form name="nameShark" method="post" action="IndividualSetNickName">
            <input name="individual" type="hidden"
                   value="<%=request.getParameter("number")%>"> <%=nickname %>:
            <input name="nickname" type="text" id="nickname" size="15"
                   maxlength="50"><br> <%=nicknamer %>: <input name="namer"
                                                               type="text" id="namer" size="15"
                                                               maxlength="50"><br> <input
            name="Name" type="submit" id="Name" value="<%=update %>"></form>
        </td>
      </tr>
    </table>
    </div>
                         		<!-- popup dialog script -->
<script>
var dlgNick = $("#dialogNickname").dialog({
  autoOpen: false,
  draggable: false,
  resizable: false,
  width: 500
});

$("a#nickname").click(function() {
  dlgNick.dialog("open");
});
</script>


</p>
<p><%=sex %>: <%=sharky.getSex()%> <%if (isOwner && CommonConfiguration.isCatalogEditable(context)) {%><a id="sex" style="color:blue;cursor: pointer;"><img align="absmiddle" width="20px" height="20px" style="border-style: none;" src="images/Crystal_Clear_action_edit.png" /></a><%}%><br />
  <%
    //edit sex
    if (CommonConfiguration.isCatalogEditable(context) && isOwner) {%>
  
    <!-- Now prep the popup dialog -->
<div id="dialogSex" title="<%=setsex %>" style="display:none">
  
    <table border="1" cellpadding="1" cellspacing="0" bordercolor="#FFFFFF">

      <tr>
        <td align="left" valign="top">
          <form name="setxsexshark" action="IndividualSetSex" method="post">

            <select name="selectSex" size="1" id="selectSex">
              <option value="unknown"><%=props.getProperty("unknown") %></option>
              <option value="male"><%=props.getProperty("male") %></option>
              <option value="female"><%=props.getProperty("female") %></option>
            </select><br> <input name="individual" type="hidden" value="<%=name%>" id="individual" /> 
            <input name="Add" type="submit" id="Add" value="<%=update %>" />
          </form>
        </td>
      </tr>
    </table>
    
        </div>
                         		<!-- popup dialog script -->
<script>
var dlgSex = $("#dialogSex").dialog({
  autoOpen: false,
  draggable: false,
  resizable: false,
  width: 500
});

$("a#sex").click(function() {
  dlgSex.dialog("open");
});
</script>
    
   <%}%>

</p>

<!-- start birth date -->
<a name="birthdate"></a>
<%
String timeOfBirth="";
//System.out.println("Time of birth is: "+sharky.getTimeOfBirth());
if(sharky.getTimeOfBirth()>0){
	String timeOfBirthFormat="yyyy-MM-d";
	if(props.getProperty("birthdateJodaFormat")!=null){
		timeOfBirthFormat=props.getProperty("birthdateJodaFormat");
	}
	timeOfBirth=(new DateTime(sharky.getTimeOfBirth())).toString(timeOfBirthFormat);
	
}

String displayTimeOfBirth=timeOfBirth;
//if(displayTimeOfBirth.indexOf("-")!=-1){displayTimeOfBirth=displayTimeOfBirth.substring(0,displayTimeOfBirth.indexOf("-"));}

%>
<p><%=props.getProperty("birthdate")  %>:
  <%=displayTimeOfBirth%> <%if (isOwner && CommonConfiguration.isCatalogEditable(context)) {%><a style="color:blue;cursor: pointer;" id="birthdate"><img align="absmiddle" width="20px" height="20px" style="border-style: none;" src="images/Crystal_Clear_action_edit.png" /></a><%}%>
</p>


<!-- Now prep the popup dialog -->
<div id="dialogBirthDate" title="<%=props.getProperty("setBirthDate") %>" style="display:none">
<table border="1" cellpadding="1" cellspacing="0" bordercolor="#FFFFFF">

<tr><td align="left" valign="top">
	<strong>
      		<font color="#990000"> <%=props.getProperty("clickDate")%>
      		</font>
      	</strong>
      	  	<br /><%=props.getProperty("dateFormat")%>
      	<br /> <font size="-1"><%=props.getProperty("leaveBlank")%></font>
    
</td></tr>

  <tr>
    <td align="left" valign="top">
      <form name="set_birthdate" method="post" action="IndividualSetYearOfBirth">
      
    
      	<input name="individual" type="hidden" value="<%=request.getParameter("number")%>" /> 
      	<%=props.getProperty("birthdate")  %>:
        <input name="timeOfBirth" type="text" id="timeOfBirth" size="15" maxlength="150" value="<%=timeOfBirth %>" />
        
        <br /> <input name="birthy" type="submit" id="birthy" value="<%=update %>"></form>
    </td>
  </tr>
</table>

</div>
                         		<!-- popup dialog script -->
<script>
var dlgBirthDate = $("#dialogBirthDate").dialog({
  autoOpen: false,
  draggable: false,
  resizable: false,
  width: 600
});

$("a#birthdate").click(function() {
	dlgBirthDate.dialog("open");
});
</script>
</p>
<!-- end birth date -->


<!-- start death date -->
<a name="deathdate"></a>
<%
String timeOfDeath="";
if(sharky.getTimeofDeath()>0){
	String timeOfDeathFormat="yyyy-MM-d";
	if(props.getProperty("deathdateJodaFormat")!=null){
		timeOfDeathFormat=props.getProperty("deathdateJodaFormat");
	}
	timeOfDeath=(new DateTime(sharky.getTimeofDeath())).toString(timeOfDeathFormat);
}
String displayTimeOfDeath=timeOfDeath;
//if(displayTimeOfDeath.indexOf("-")!=-1){displayTimeOfDeath=displayTimeOfDeath.substring(0,displayTimeOfDeath.indexOf("-"));}

%>
<p><%=props.getProperty("deathdate")  %>:
  <%=displayTimeOfDeath%> <%if (isOwner && CommonConfiguration.isCatalogEditable(context)) {%><a style="color:blue;cursor: pointer;" id="deathdate"><img align="absmiddle" width="20px" height="20px" style="border-style: none;" src="images/Crystal_Clear_action_edit.png" /></a><%}%>
</p>


<!-- Now prep the popup dialog -->
<div id="dialogDeathDate" title="<%=props.getProperty("setDeathDate") %>" style="display:none">
<table border="1" cellpadding="1" cellspacing="0" bordercolor="#FFFFFF">

<tr><td align="left" valign="top">
	<strong>
      		<font color="#990000"> <%=props.getProperty("clickDate")%>
      		</font>
      	</strong>
      	<br /><%=props.getProperty("dateFormat")%>
      	<br /> <font size="-1"><em><%=props.getProperty("leaveBlank")%></em></font>
    
</td></tr>

  <tr>
    <td align="left" valign="top">
      <form name="set_deathdate" method="post" action="IndividualSetYearOfDeath">
      	<input name="individual" type="hidden" value="<%=request.getParameter("number")%>" /> 
      	<%=props.getProperty("deathdate")  %>:
        <input name="timeOfDeath" type="text" id="timeOfDeath" size="15" maxlength="150" value="<%=timeOfDeath %>" /><br /> <input name="deathy" type="submit" id="deathy" value="<%=update %>"></form>
    </td>
  </tr>
</table>

</div>
                         		<!-- popup dialog script -->
<script>
var dlgDeathDate = $("#dialogDeathDate").dialog({
  autoOpen: false,
  draggable: false,
  resizable: false,
  width: 600
});

$("a#deathdate").click(function() {
	dlgDeathDate.dialog("open");
});
</script>
</p>
<!-- end death date -->

<%

  if (sharky.getDynamicProperties() != null) {
    //let's create a TreeMap of the properties
    StringTokenizer st = new StringTokenizer(sharky.getDynamicProperties(), ";");
    while (st.hasMoreTokens()) {
      String token = st.nextToken();
      int equalPlace = token.indexOf("=");
      String nm = token.substring(0, (equalPlace));
      String vl = token.substring(equalPlace + 1);
%>
<p class="para"><img align="absmiddle" src="images/lightning_dynamic_props.gif"> <strong><%=nm%>
</strong><br/> <%=vl%>
  <%
    if (isOwner && CommonConfiguration.isCatalogEditable(context)) {
  %>
  <font size="-1"><a
    href="http://<%=CommonConfiguration.getURLLocation(request) %>/individuals.jsp?number=<%=request.getParameter("number").trim()%>&edit=dynamicproperty&name=<%=nm%>#dynamicproperty"><img align="absmiddle" width="20px" height="20px" style="border-style: none;" src="images/Crystal_Clear_action_edit.png" /></a></font>
  <%
    }
  %>
</p>


<%
    }

  }
%>
<table id="encounter_report" width="100%">
<tr>

<td align="left" valign="top">

<p><strong><%=sharky.totalEncounters()%>
</strong>
  <%=numencounters %>
</p>

<table id="results" width="100%">
  <tr class="lineitem">
      <td class="lineitem" align="left" valign="top" bgcolor="#99CCFF"><strong><%=date %></strong></td>
    <td class="lineitem" align="left" valign="top" bgcolor="#99CCFF"><strong><%=location %></strong></td>
    <td class="lineitem" bgcolor="#99CCFF"><strong><%=dataTypes %></strong></td>
    <td class="lineitem" align="left" valign="top" bgcolor="#99CCFF"><strong><%=encnumber %></strong></td>
    <td class="lineitem" align="left" valign="top" bgcolor="#99CCFF"><strong><%=alternateID %></strong></td>



    <td class="lineitem" align="left" valign="top" bgcolor="#99CCFF"><strong><%=sex %></strong></td>
    <%
      if (isOwner && CommonConfiguration.useSpotPatternRecognition(context)) {
    %>

    	<td align="left" valign="top" bgcolor="#99CCFF">
    		<strong><%=spots %></strong>
    	</td>
    <%
    }
    %>
    <td class="lineitem" align="left" valign="top" bgcolor="#99CCFF"><strong><%=props.getProperty("sightedWith") %></td>
    <td class="lineitem" align="left" valign="top" bgcolor="#99CCFF"><strong><%=props.getProperty("behavior") %></td>
 
  </tr>
  <%
    Encounter[] dateSortedEncs = sharky.getDateSortedEncounters();

    int total = dateSortedEncs.length;
    for (int i = 0; i < total; i++) {
      Encounter enc = dateSortedEncs[i];
      
        Vector encImages = enc.getAdditionalImageNames();
        String imgName = "";
        
          imgName = "/"+CommonConfiguration.getDataDirectoryName(context)+"/encounters/" + enc.getEncounterNumber() + "/thumb.jpg";
        
  %>
  <tr>
      <td class="lineitem"><%=enc.getDate()%>
    </td>
    <td class="lineitem">
    <% 
    if(enc.getLocation()!=null){
    %>
    <%=enc.getLocation()%>
    <%
    }
    else{
    %>
    &nbsp;
    <%
    }
    %>
    </td>
    <td width="100" height="32px" class="lineitem">
    	<a href="http://<%=CommonConfiguration.getURLLocation(request)%>/encounters/encounter.jsp?number=<%=enc.getEncounterNumber()%>">
    		
    		<%
    		//if the encounter has photos, show photo folder icon
    		if((enc.getImages()!=null) && (enc.getImages().size()>0)){
    		%>
    			<img src="images/Crystal_Clear_filesystem_folder_image.png" height="32px" width="32px" />
    		<%
    		}
    		
    		//if the encounter has a tissue sample, show an icon
    		if((enc.getTissueSamples()!=null) && (enc.getTissueSamples().size()>0)){
    		%>
    			<img src="images/microscope.gif" height="32px" width="32px" />
    		<%
    		}
    		//if the encounter has a measurement, show the measurement icon
    		if(enc.hasMeasurements()){
    		%>	
    			<img src="images/ruler.png" height="32px" width="32px" />
        	<%	
    		}
    		%>
    		
    	</a>
    </td>
    <td class="lineitem"><a
      href="http://<%=CommonConfiguration.getURLLocation(request)%>/encounters/encounter.jsp?number=<%=enc.getEncounterNumber()%><%if(request.getParameter("noscript")!=null){%>&noscript=null<%}%>"><%=enc.getEncounterNumber()%>
    </a></td>

    <%
      if (enc.getAlternateID() != null) {
    %>
    <td class="lineitem"><%=enc.getAlternateID()%>
    </td>
    <%
    } else {
    %>
    <td class="lineitem">
    </td>
    <%
      }
    %>



    <td class="lineitem"><%=enc.getSex()%>
    </td>

    <%
      if (CommonConfiguration.useSpotPatternRecognition(context)) {
    %>
    <%if (((enc.getSpots().size() == 0) && (enc.getRightSpots().size() == 0)) && (isOwner)) {%>
    <td class="lineitem">&nbsp;</td>
    <% } else if (isOwner && (enc.getSpots().size() > 0) && (enc.getRightSpots().size() > 0)) {%>
    <td class="lineitem">LR</td>
    <%} else if (isOwner && (enc.getSpots().size() > 0)) {%>
    <td class="lineitem">L</td>
    <%} else if (isOwner && (enc.getRightSpots().size() > 0)) {%>
    <td class="lineitem">R</td>
    <%
        }
      }
    %>
    
    <td class="lineitem">
    <%
    if(myShepherd.getOccurrenceForEncounter(enc.getCatalogNumber())!=null){
    	Occurrence thisOccur=myShepherd.getOccurrenceForEncounter(enc.getCatalogNumber());
    	ArrayList<String> otherOccurs=thisOccur.getMarkedIndividualNamesForThisOccurrence();
    	if(otherOccurs!=null){
    		int numOtherOccurs=otherOccurs.size();
    		for(int j=0;j<numOtherOccurs;j++){
    			String thisName=otherOccurs.get(j);
    			if(!thisName.equals(sharky.getIndividualID())){
    				if(j<20){
    			
    				%>
    					<a href="http://<%=CommonConfiguration.getURLLocation(request) %>/individuals.jsp?number=<%=thisName%>"><%=thisName %></a>&nbsp;
    				<%	
    				}

    			}
    		}
    		if(numOtherOccurs>=20){
			%>
			    &nbsp;<em><%=props.getProperty("andMore") %></em>
			<%
    		}
    		if(numOtherOccurs>1){
    		%>
    		<br /><br /><em><a href="occurrence.jsp?number=<%=thisOccur.getOccurrenceID()%>"><%=props.getProperty("moreOccurrences") %></a></em>
    		<%
    		}
    	}
    }
    //new comment
    else{
    %>	
    	&nbsp;
    <%
    }
    %>
    
    </td>
    <td class="lineitem">
    <%
    if(enc.getBehavior()!=null){
    %>
    <%=enc.getBehavior() %>
    <%	
    }
    if(myShepherd.getOccurrenceForEncounter(enc.getCatalogNumber())!=null){
    	Occurrence thisOccur=myShepherd.getOccurrenceForEncounter(enc.getCatalogNumber());
    	if((thisOccur!=null)&&(thisOccur.getGroupBehavior()!=null)){
   		 %>
    	<br /><br /><em><%=props.getProperty("groupBehavior") %></em><br /><%=thisOccur.getGroupBehavior() %>
    	<%	
    	}
    }
    %>
    </td>
  </tr>
  <%
      
    } //end for

  %>


</table>


<!-- Start thumbnail gallery -->

<br />
<p>
  <strong><%=props.getProperty("imageGallery") %>
  </strong></p>

    <%
    String[] keywords=keywords=new String[0];
		int numThumbnails = myShepherd.getNumThumbnails(sharky.getEncounters().iterator(), keywords);
		if(numThumbnails>0){	
		%>

<table id="results" border="0" width="100%">
    <%

			
			int countMe=0;
			//Vector thumbLocs=new Vector();
			ArrayList<SinglePhotoVideo> thumbLocs=new ArrayList<SinglePhotoVideo>();
			
			int  numColumns=3;
			int numThumbs=0;
			  if (CommonConfiguration.allowAdoptions(context)) {
				  ArrayList adoptions = myShepherd.getAllAdoptionsForMarkedIndividual(name,context);
				  int numAdoptions = adoptions.size();
				  if(numAdoptions>0){
					  numColumns=2;
				  }
			  }

			try {
				thumbLocs=myShepherd.getThumbnails(request, sharky.getEncounters().iterator(), 1, 99999, keywords);
				numThumbs=thumbLocs.size();
			%>

  <tr valign="top">
 <td>
 <!-- HTML Codes by Quackit.com -->
<div style="text-align:left;border:1px solid black;width:100%;height:400px;overflow-y:scroll;overflow-x:scroll;">

      <%
      						while(countMe<numThumbs){
							//for(int columns=0;columns<numColumns;columns++){
								if(countMe<numThumbs) {
									//String combined ="";
									//if(myShepherd.isAcceptableVideoFile(thumbLocs.get(countMe).getFilename())){
									//	combined = "http://" + CommonConfiguration.getURLLocation(request) + "/images/video.jpg" + "BREAK" + thumbLocs.get(countMe).getCorrespondingEncounterNumber() + "BREAK" + thumbLocs.get(countMe).getFilename();
									//}
									//else{
									//	combined= thumbLocs.get(countMe).getCorrespondingEncounterNumber() + "/" + thumbLocs.get(countMe).getDataCollectionEventID() + ".jpg" + "BREAK" + thumbLocs.get(countMe).getCorrespondingEncounterNumber() + "BREAK" + thumbLocs.get(countMe).getFilename();
							              
									//}

									//StringTokenizer stzr=new StringTokenizer(combined,"BREAK");
									//String thumbLink=stzr.nextToken();
									//String encNum=stzr.nextToken();
									//int fileNamePos=combined.lastIndexOf("BREAK")+5;
									//String fileName=combined.substring(fileNamePos).replaceAll("%20"," ");
									String thumbLink="";
									boolean video=true;
									if(!myShepherd.isAcceptableVideoFile(thumbLocs.get(countMe).getFilename())){
										thumbLink="/"+CommonConfiguration.getDataDirectoryName(context)+"/encounters/"+thumbLocs.get(countMe).getCorrespondingEncounterNumber()+"/"+thumbLocs.get(countMe).getDataCollectionEventID()+".jpg";
										video=false;
									}
									else{
										thumbLink="http://"+CommonConfiguration.getURLLocation(request)+"/images/video.jpg";
										
									}
									String link="/"+CommonConfiguration.getDataDirectoryName(context)+"/encounters/"+thumbLocs.get(countMe).getCorrespondingEncounterNumber()+"/"+thumbLocs.get(countMe).getFilename();
						
							%>

   
    
      <table align="left" width="<%=100/numColumns %>%">
        <tr>
          <td valign="top">
			
              <%
			if(isOwner){
												%>
            <a href="<%=link%>" 
            <%
            if(thumbLink.indexOf("video.jpg")==-1){
            %>
            	class="highslide" onclick="return hs.expand(this)"
            <%
            }
            %>
            >
            <%
            }
             %>
              <img src="<%=thumbLink%>" alt="photo" border="1" title="Click to enlarge"/>
              <%
                if (isOwner) {
              %>
            </a>
              <%
			}
            
			%>

            <div 
            <%
            if(!thumbLink.endsWith("video.jpg")){
            %>
            class="highslide-caption"
            <%
            }
            %>
            >

              <table>
                <tr>
                  <td align="left" valign="top">

                    <table>
                      <%

                        int kwLength = keywords.length;
                        Encounter thisEnc = myShepherd.getEncounter(thumbLocs.get(countMe).getCorrespondingEncounterNumber());
                      %>
                      
                      

                      <tr>
                        <td>
                        	<span class="caption"><%=props.getProperty("location") %>: 
                        		<%
                        		if(thisEnc.getLocation()!=null){
                        		%>
                        			<%=thisEnc.getLocation() %>
                        		<%
                        		}
                        		else {
                        		%>
                        			&nbsp;
                        		<%
                        		}
                        		%>
                        	</span>
                        </td>
                      </tr>
                      <tr>
                        <td>
                        	<span class="caption"><%=props.getProperty("locationID") %>: 
				                        		<%
				                        		if(thisEnc.getLocationID()!=null){
				                        		%>
				                        			<%=thisEnc.getLocationID() %>
				                        		<%
				                        		}
				                        		else {
				                        		%>
				                        			&nbsp;
				                        		<%
				                        		}
				                        		%>
                        	</span>
                        </td>
                      </tr>
                      <tr>
                        <td><span
                          class="caption"><%=props.getProperty("date") %>: <%=thisEnc.getDate() %></span>
                        </td>
                      </tr>
                      <tr>
                        <td><span class="caption"><%=props.getProperty("catalogNumber") %>: <a
                          href="encounters/encounter.jsp?number=<%=thisEnc.getCatalogNumber() %>"><%=thisEnc.getCatalogNumber() %>
                        </a></span></td>
                      </tr>
                      <%
                        if (thisEnc.getVerbatimEventDate() != null) {
                      %>
                      <tr>

                        <td><span
                          class="caption"><%=props.getProperty("verbatimEventDate") %>: <%=thisEnc.getVerbatimEventDate() %></span>
                        </td>
                      </tr>
                      <%
                        }
                      %>
                      <tr>
                        <td><span class="caption">
											<%=props.getProperty("matchingKeywords") %>
											<%
											 //while (allKeywords2.hasNext()) {
					                          //Keyword word = (Keyword) allKeywords2.next();
					                          
					                          
					                          //if (word.isMemberOf(encNum + "/" + fileName)) {
											  //if(thumbLocs.get(countMe).getKeywords().contains(word)){
					                        	  
					                            //String renderMe = word.getReadableName();
												List<Keyword> myWords = thumbLocs.get(countMe).getKeywords();
												int myWordsSize=myWords.size();
					                            for (int kwIter = 0; kwIter<myWordsSize; kwIter++) {
					                              //String kwParam = keywords[kwIter];
					                              //if (kwParam.equals(word.getIndexname())) {
					                              //  renderMe = "<strong>" + renderMe + "</strong>";
					                              //}
					                      		 	%>
					 								<br/><%= ("<strong>" + myWords.get(kwIter).getReadableName() + "</strong>")%>
					 								<%
					                            }




					                          //    }
					                       // } 

                          %>
										</span></td>
                      </tr>
                    </table>
                    <br/>

                    <%
                      if (CommonConfiguration.showEXIFData(context)) {
                   
            	if(!thumbLink.endsWith("video.jpg")){
           		 %>							
					<span class="caption">
						<div class="scroll">	
						<span class="caption">
					<%
            if ((thumbLocs.get(countMe).getFilename().toLowerCase().endsWith("jpg")) || (thumbLocs.get(countMe).getFilename().toLowerCase().endsWith("jpeg"))) {
              try{
              File exifImage = new File(encountersDir.getAbsolutePath() + "/" + thisEnc.getCatalogNumber() + "/" + thumbLocs.get(countMe).getFilename());
              if(exifImage.exists()){              
              	Metadata metadata = JpegMetadataReader.readMetadata(exifImage);
              	// iterate through metadata directories
              	Iterator directories = metadata.getDirectoryIterator();
              	while (directories.hasNext()) {
                	Directory directory = (Directory) directories.next();
                	// iterate through tags and print to System.out
                	Iterator tags = directory.getTagIterator();
                	while (tags.hasNext()) {
                  		Tag tag = (Tag) tags.next();

          				%>
								<%=tag.toString() %><br/>
								<%
                    }
                }
              } //end if
              else{
            	  %>
		            <p>File not found on file system. No EXIF data available.</p>
          		<%  
              }
            } //end try
            catch(Exception e){
            	 %>
		            <p>Cannot read metadata for this file.</p>
            	<%
            	System.out.println("Cannout read metadata for: "+thumbLocs.get(countMe).getFilename());
            	e.printStackTrace();
            }

                  }
                %>
   									
   								
   								</span>
            </div>
   								</span>
   			<%
            	}
   			%>


                  </td>
                  <%
                    }
                  %>
                </tr>
              </table>
            </div>
            

</td>
</tr>

 <%
            if(!thumbLink.endsWith("video.jpg")){
 %>
<tr>
  <td>
  	<span class="caption"><%=props.getProperty("location") %>: 
	                        		<%
	                        		if(thisEnc.getLocation()!=null){
	                        		%>
	                        			<%=thisEnc.getLocation() %>
	                        		<%
	                        		}
	                        		else {
	                        		%>
	                        			&nbsp;
	                        		<%
	                        		}
	                        		%>
                        	</span>
  </td>
</tr>
<tr>
  <td>
 	<span class="caption"><%=props.getProperty("locationID") %>: 
                        		<%
                        		if(thisEnc.getLocationID()!=null){
                        		%>
                        			<%=thisEnc.getLocationID() %>
                        		<%
                        		}
                        		else {
                        		%>
                        			&nbsp;
                        		<%
                        		}
                        		%>
                        	</span> 
   </td>
</tr>
<tr>
  <td><span class="caption"><%=props.getProperty("date") %>: <%=thisEnc.getDate() %></span></td>
</tr>
<tr>
  <td><span class="caption"><%=props.getProperty("catalogNumber") %>: <a
    href="encounters/encounter.jsp?number=<%=thisEnc.getCatalogNumber() %>"><%=thisEnc.getCatalogNumber() %>
  </a></span></td>
</tr>
<tr>
  <td><span class="caption">
											<%=props.getProperty("matchingKeywords") %>
											<%
                        //int numKeywords=myShepherd.getNumKeywords();
											 //while (allKeywords2.hasNext()) {
					                          //Keyword word = (Keyword) allKeywords2.next();
					                          
					                          
					                          //if (word.isMemberOf(encNum + "/" + fileName)) {
											  //if(thumbLocs.get(countMe).getKeywords().contains(word)){
					                        	  
					                            //String renderMe = word.getReadableName();
												//List<Keyword> myWords = thumbLocs.get(countMe).getKeywords();
												//int myWordsSize=myWords.size();
					                            for (int kwIter = 0; kwIter<myWordsSize; kwIter++) {
					                              //String kwParam = keywords[kwIter];
					                              //if (kwParam.equals(word.getIndexname())) {
					                              //  renderMe = "<strong>" + renderMe + "</strong>";
					                              //}
					                      		 	%>
					 								<br/><%= ("<strong>" + myWords.get(kwIter).getReadableName() + "</strong>")%>
					 								<%
					                            }




					                          //    }
					                       // } 

                          %>
										</span></td>
</tr>
<%

            }
%>
</table>

<%

      countMe++;
    } //end if
  } //endFor
%>
</div>

</td>
</tr>
<%



} catch (Exception e) {
  e.printStackTrace();
%>
<tr>
  <td>
    <p><%=props.getProperty("error")%>
    </p>.
  </td>
</tr>
<%
  }
%>

</table>
</div>
<%
} else {
%>

<p><%=props.getProperty("noImages")%></p>

<%
  }
%>

</table>
<!-- end thumbnail gallery -->

<br />
<%
if(CommonConfiguration.showUsersToPublic(context)){
%>
<p>
  <strong><%=props.getProperty("collaboratingResearchers") %></strong> (click each to learn more)
</p>
  
     <p class="para">
    <table >
     <tr>
     <td>
                         
                         
                         <%
                         myShepherd.beginDBTransaction();
                         
                         ArrayList<User> relatedUsers =  myShepherd.getAllUsersForMarkedIndividual(sharky);
                         int numUsers=relatedUsers.size();
                         if(numUsers>0){
                         for(int userNum=0;userNum<numUsers;userNum++){	
                        	 
                        	 User thisUser=relatedUsers.get(userNum);
                        	 String username=thisUser.getUsername();
                         	 %>
                                
                                <table align="left">
                                	<%
                         	
                         		
                                	String profilePhotoURL="images/empty_profile.jpg";
                    		    
                         		if(thisUser.getUserImage()!=null){
                         			profilePhotoURL="/"+CommonConfiguration.getDataDirectoryName(context)+"/users/"+thisUser.getUsername()+"/"+thisUser.getUserImage().getFilename();

                         		}
                         		%>
                     			<tr><td><center><div style="height: 50px">
						<a style="color:blue;cursor: pointer;" id="username<%=userNum%>"><img style="height: 100%" border="1" align="top" src="<%=profilePhotoURL%>"  /></a>
					</div></center></td></tr>
                     			<%
                         		String displayName="";
                         		if(thisUser.getFullName()!=null){
                         			displayName=thisUser.getFullName();
                         		
                         		%>
                         		<tr><td style="border:none"><center><a style="color:blue;cursor: pointer;" id="username<%=userNum%>" style="font-weight:normal;border:none"><%=displayName %></a></center></td></tr>
                         		<%	
                         		}
                         		
                         		%>
                         	</table>
                         		
                         		<!-- Now prep the popup dialog -->
                         		<div id="dialog<%=userNum%>" title="<%=displayName %>" style="display:none">
                         			<table cellpadding="3px"><tr><td>
                         			<div style="height: 150px"><img border="1" align="top" src="<%=profilePhotoURL%>" style="height: 100%" />
                         			</td>
                         			<td><p>
                         			<%
                         			if(thisUser.getAffiliation()!=null){
                         			%>
                         			<strong>Affiliation:</strong> <%=thisUser.getAffiliation() %><br />
                         			<%	
                         			}
                         			
                         			if(thisUser.getUserProject()!=null){
                         			%>
                         			<strong>Research Project:</strong> <%=thisUser.getUserProject() %><br />
                         			<%	
                         			}
                         			
                         			if(thisUser.getUserURL()!=null){
                             			%>
                             			<strong>Web site:</strong> <a style="font-weight:normal;color: blue" class="ecocean" href="<%=thisUser.getUserURL()%>"><%=thisUser.getUserURL() %></a><br />
                             			<%	
                             			}
                         			
                         			if(thisUser.getUserStatement()!=null){
                             			%>
                             			<br /><em>"<%=thisUser.getUserStatement() %>"</em>
                             			<%	
                             			}
                         			%>
                         			</p>
                         			</td></tr></table>
                         		</div>
                         		<!-- popup dialog script -->

					<script>
					    var dlg<%=userNum%> = $("#dialog<%=userNum%>").dialog({
					      autoOpen: false,
					      draggable: false,
					      resizable: false,
					      width: 500
					    });
					    
					    $("a#username<%=userNum%>").click(function() {
					      dlg<%=userNum%>.dialog("open");
					    });
					</script>

                         		
                         		<% 
                         	} //end for loop of users
                         	
                         } //end if loop if there are any users
                         else{
                        %>	 
                         
                        	 <p><%=props.getProperty("noCollaboratingResearchers") %></p>
                        <%	 
                         }
                        
                        %>
                        </td>

    
    </tr></table></p>
  <%
} //end if showUsersToGeneralPublic
  %>
  
  
  
  
  
  
  
  
  
  
  
  

<!-- Start genetics -->
<br />
<a name="tissueSamples"></a>
<p><img align="absmiddle" src="images/microscope.gif" /><strong><%=props.getProperty("tissueSamples") %></strong></p>
<p>
<%
List<TissueSample> tissueSamples=sharky.getAllTissueSamples();

int numTissueSamples=tissueSamples.size();
if(numTissueSamples>0){
%>
<table width="100%" class="tissueSample">
<tr>
	<th><strong><%=props.getProperty("sampleID") %></strong></th>
	<th><strong><%=props.getProperty("correspondingEncounterNumber") %></strong></th>
	<th><strong><%=props.getProperty("values") %></strong></th>
	<th><strong><%=props.getProperty("analyses") %></strong></th></tr>
<%
for(int j=0;j<numTissueSamples;j++){
	TissueSample thisSample=tissueSamples.get(j);
	%>
	<tr>
		<td><span class="caption"><a href="encounters/encounter.jsp?number=<%=thisSample.getCorrespondingEncounterNumber() %>#tissueSamples"><%=thisSample.getSampleID()%></a></span></td>
		<td><span class="caption"><a href="encounters/encounter.jsp?number=<%=thisSample.getCorrespondingEncounterNumber() %>#tissueSamples"><%=thisSample.getCorrespondingEncounterNumber()%></a></span></td>
		<td><span class="caption"><%=thisSample.getHTMLString() %></span>
		</td>
	
	<td><table>
		<%
		int numAnalyses=thisSample.getNumAnalyses();
		List<GeneticAnalysis> gAnalyses = thisSample.getGeneticAnalyses();
		for(int g=0;g<numAnalyses;g++){
			GeneticAnalysis ga = gAnalyses.get(g);
			if(ga.getAnalysisType().equals("MitochondrialDNA")){
				MitochondrialDNAAnalysis mito=(MitochondrialDNAAnalysis)ga;
				%>
				<tr><td style="border-style: none;"><strong><span class="caption"><%=props.getProperty("haplotype") %></strong></span></strong>: <span class="caption"><%=mito.getHaplotype() %></span></td></tr></li>
			<%
			}
			else if(ga.getAnalysisType().equals("SexAnalysis")){
				SexAnalysis mito=(SexAnalysis)ga;
				%>
				<tr><td style="border-style: none;"><strong><span class="caption"><%=props.getProperty("geneticSex") %></strong></span></strong>: <span class="caption"><%=mito.getSex() %></span></td></tr></li>
			<%
			}
			else if(ga.getAnalysisType().equals("MicrosatelliteMarkers")){
				MicrosatelliteMarkersAnalysis mito=(MicrosatelliteMarkersAnalysis)ga;
				
			%>
			<tr>
				<td style="border-style: none;">
					<p><span class="caption"><strong><%=props.getProperty("msMarkers") %></strong></span>&nbsp;
					<%
					if(request.getUserPrincipal()!=null){
					%>
					<a href="individualSearch.jsp?individualDistanceSearch=<%=sharky.getIndividualID()%>"><img height="20px" width="20px" align="absmiddle" alt="Individual-to-Individual Genetic Distance Search" src="images/Crystal_Clear_app_xmag.png"></img></a>
					<%
					}
					%>
					</p>
					<span class="caption"><%=mito.getAllelesHTMLString() %></span>
				</td>
				</tr></li>
			
			<% 
			}
			else if(ga.getAnalysisType().equals("BiologicalMeasurement")){
				BiologicalMeasurement mito=(BiologicalMeasurement)ga;
				%>
				<tr><td style="border-style: none;"><strong><span class="caption"><%=mito.getMeasurementType()%> <%=props.getProperty("measurement") %></span></strong><br /> <span class="caption"><%=mito.getValue().toString() %> <%=mito.getUnits() %> (<%=mito.getSamplingProtocol() %>)
				<%
				if(!mito.getSuperHTMLString().equals("")){
				%>
				<em>
				<br /><%=props.getProperty("analysisID")%>: <%=mito.getAnalysisID()%>
				<br /><%=mito.getSuperHTMLString()%>
				</em>
				<%
				}
				%>
				</span></td></tr></li>
			<%
			}
		}
		%>
		</table>

	</td>
	
	
	</tr>
	<%
}
%>
</table>
</p>
<%
}
else {
%>
	<p class="para"><%=props.getProperty("noTissueSamples") %></p>
	<!-- End genetics -->
<%
}
%>

<br/>
<a name="socialRelationships"></a>
<p><strong><%=props.getProperty("social")%></strong></p>
<%
if (isOwner && CommonConfiguration.isCatalogEditable(context)) {
%>
<p class="para">
	<a id="addRelationship" class="launchPopup">
		<img align="absmiddle" width="24px" style="border-style: none;" src="images/Crystal_Clear_action_edit_add.png"/>
	</a>
	<a id="addRelationship" class="launchPopup">
		<%=props.getProperty("addRelationship") %>
	</a>
</p>
<%
}
%>


<!-- start relationship popup code -->
<%
if (isOwner && CommonConfiguration.isCatalogEditable(context)) {
%>
<div id="dialogRelationship" title="<%=props.getProperty("setRelationship")%>" style="display:none; z-index: 99999 !important">  

<form id="setRelationship" action="RelationshipCreate" method="post">
<table cellspacing="2" bordercolor="#FFFFFF" >

<%
	String markedIndividual1Name="";
String markedIndividual2Name="";
String markedIndividual1Role="";
String markedIndividual2Role="";
String type="";
String startTime="";
String endTime="";
String bidirectional="";
String markedIndividual1DirectionalDescriptor="";
String markedIndividual2DirectionalDescriptor="";
String communityName="";

//if(myShepherd.isRelationship(request.getParameter("type"), request.getParameter("markedIndividualName1"), request.getParameter("markedIndividualName2"), request.getParameter("markedIndividualRole1"), request.getParameter("markedIndividualRole2"),false)){

	if(request.getParameter("persistenceID")!=null){	

	//Relationship myRel=myShepherd.getRelationship(request.getParameter("type"), request.getParameter("markedIndividualName1"), request.getParameter("markedIndividualName2"), request.getParameter("markedIndividualRole1"), request.getParameter("markedIndividualRole2"));
	
	Object identity = myShepherd.getPM().newObjectIdInstance(org.ecocean.social.Relationship.class, request.getParameter("persistenceID"));
	
	Relationship myRel=(Relationship)myShepherd.getPM().getObjectById(identity);
	
	if(myRel.getMarkedIndividualName1()!=null){
		markedIndividual1Name=myRel.getMarkedIndividualName1();
	}
	if(myRel.getMarkedIndividualName2()!=null){
		markedIndividual2Name=myRel.getMarkedIndividualName2();
	}
	if(myRel.getMarkedIndividualRole1()!=null){
		markedIndividual1Role=myRel.getMarkedIndividualRole1();
	}
	if(myRel.getMarkedIndividualRole2()!=null){
		markedIndividual2Role=myRel.getMarkedIndividualRole2();
	}
	if(myRel.getType()!=null){
		type=myRel.getType();
	}
	if(myRel.getMarkedIndividual1DirectionalDescriptor()!=null){
		markedIndividual1DirectionalDescriptor=myRel.getMarkedIndividual1DirectionalDescriptor();
	}
	if(myRel.getMarkedIndividual2DirectionalDescriptor()!=null){
		markedIndividual2DirectionalDescriptor=myRel.getMarkedIndividual2DirectionalDescriptor();
	}
	
	if(myRel.getStartTime()>-1){
		startTime=(new DateTime(myRel.getStartTime())).toString();
	}
	if(myRel.getEndTime()>-1){
		endTime=(new DateTime(myRel.getEndTime())).toString();
	}
	
	if(myRel.getBidirectional()!=null){
		bidirectional=myRel.getBidirectional().toString();
	}
	
	if(myRel.getRelatedSocialUnitName()!=null){
		communityName=myRel.getRelatedSocialUnitName();
	}
	
	
}
%>

    <tr>
      	<td width="200px">
          <strong><%=props.getProperty("type")%></strong><br />
          <div style="font-size: smaller;">(<%=props.getProperty("required")%>)</div></td>
        <td>
        	<select name="type">
			<%
				List<String> types=CommonConfiguration.getIndexedValues("relationshipType",context);
				int numTypes=types.size();
				for(int g=0;g<numTypes;g++){
					
					String selectedText="";
					if(type.equals(types.get(g))){selectedText="selected=\"selected\"";}
			%>      
          		<option <%=selectedText%>><%=types.get(g)%></option>
          	<%
          		}
          	%>
          	</select>
          
          
        </td>
     </tr>
     <tr>
     	<td>
          
          <strong><%=props.getProperty("individualID1")%></strong><br />
           <div style="font-size: smaller;">(<%=props.getProperty("required")%>)</div>
           </td>
          <td>
          
             <%
                       	if((markedIndividual1Name.equals(""))&&(markedIndividual2Name.equals(""))){
                       %>
               			<%=sharky.getIndividualID()%><input type="hidden" name="markedIndividualName1" value="<%=sharky.getIndividualID()%>"/>
               			
               		<%
               			               			}
               			               		            else if(!markedIndividual1Name.equals(sharky.getIndividualID())){
               			               		%>
        		<input name="markedIndividualName1" type="text" size="20" maxlength="100" value="<%=markedIndividual1Name%>" /> 
       		<%
        			}
        		        	else{
        		%>
       			<%=markedIndividual1Name%><input type="hidden" name="markedIndividualName1" value="<%=sharky.getIndividualID()%>"/>
       		<%
       			}
       		%>
       </td>
   	</tr>
   	<tr>
     	<td>
          <strong><%=props.getProperty("individualRole1")%></strong>
          <br /> <div style="font-size: smaller;">(<%=props.getProperty("required")%>)</div>
         </td>
         <td>
         	
         <select name="markedIndividualRole1">
			<%
				List<String> roles=CommonConfiguration.getIndexedValues("relationshipRole",context);
				int numRoles=roles.size();
				for(int g=0;g<numRoles;g++){
					
					String selectedText="";
					if(markedIndividual1Role.equals(roles.get(g))){selectedText="selected=\"selected\"";}
			%>      
          		<option <%=selectedText%>><%=roles.get(g)%></option>
          	<%
          		}
          	%>
          	</select>
         
         </td>
         
         <td>
         	<%=props.getProperty("markedIndividual1DirectionalDescriptor")%>
         </td>
         <td>
         	<input name="markedIndividual1DirectionalDescriptor" type="text" size="20" maxlength="100" value="<%=markedIndividual1DirectionalDescriptor%>" />       
         </td>
         
   	</tr>
   	
    <tr>
     	<td><strong><%=props.getProperty("individualID2")%></strong></td>
        <td>
   			<%
   				if(!markedIndividual2Name.equals(sharky.getIndividualID())){
   			%>
        		<input name="markedIndividualName2" type="text" size="20" maxlength="100" value="<%=markedIndividual2Name%>" /> 
       		<%
        			}
        		        	else{
        		%>
       			<%=markedIndividual2Name%><input type="hidden" name="markedIndividualName2" value="<%=sharky.getIndividualID()%>"/>
       		<%
       			}
       		%>
       </td>
   	</tr>
   	<tr>
     	<td>
          
          <strong><%=props.getProperty("individualRole2")%></strong>
          <br /> <div style="font-size: smaller;">(<%=props.getProperty("required")%>)</div></td>
          <td>
          	<select name="markedIndividualRole2">
			<%
				for(int g=0;g<numRoles;g++){
					
					String selectedText="";
					if(markedIndividual2Role.equals(roles.get(g))){selectedText="selected=\"selected\"";}
			%>      
          		<option <%=selectedText%>><%=roles.get(g)%></option>
          	<%
          		}
          	%>
          	</select></td>
       <td>
         	<%=props.getProperty("markedIndividual2DirectionalDescriptor")%>
         </td>
         <td>
         	<input name="markedIndividual2DirectionalDescriptor" type="text" size="20" maxlength="100" value="<%=markedIndividual2DirectionalDescriptor%>" />       
         </td>
   	</tr>
   	
   <tr>
     	<td>
          
          <strong><%=props.getProperty("relatedCommunityName")%></strong></td><td><input name="relatedCommunityName" type="text" size="20" maxlength="100" value="<%=communityName%>" /> 
       </td>
   	</tr> 	
   	
   	   <tr>
     	<td>
          
          <strong><%=props.getProperty("startTime")%></strong></td>
          <td><input name="startTime" type="text" size="20" maxlength="100" value="<%=startTime%>" /> 
       </td>
       </tr>
       <tr>
       <td>
          
         <strong><%=props.getProperty("endTime")%></strong></td>
          <td><input name="endTime" type="text" size="20" maxlength="100" value="<%=endTime%>" /> 
       </td>
       
   	</tr> 	
   	
   	<tr>
     	<td>
          
          <strong><%=props.getProperty("bidirectional")%></strong>
       </td>
       <td>
          	<select name="bidirectional">
          	
          	
          		<option value=""></option>
          		<%
          			String selected="";
          		          	if(bidirectional.equals("true")){
          		          		selected="selected=\"selected\"";
          		          	}
          		%>
          		<option value="true" <%=selected%>>true</option>
          		<%
          			selected="";
          		          	if(bidirectional.equals("false")){
          		          		selected="selected=\"selected\"";
          		          	}
          		%>
          		<option value="false" <%=selected%>>false</option>
          	</select>
          	 
       </td>
   	</tr> 

            
    <tr><td colspan="2">
            	<input name="EditRELATIONSHIP" type="submit" id="EditRELATIONSHIP" value="<%=props.getProperty("update") %>" />
   			</td>
   	</tr>
   			
   			
      </td>
    </tr>
  </table>
  
  <%
    	if(request.getParameter("persistenceID")!=null){
    %>
  	<input name="persistenceID" type="hidden" value="<%=request.getParameter("persistenceID")%>"/>
  <%
  	}
  %>
  
</form>	
</div>
                         		<!-- popup dialog script -->
<script>
var dlgRel = $("#dialogRelationship").dialog({
  autoOpen: false,
  draggable: false,
  resizable: false,
  width: 600
});

$("a#addRelationship").click(function() {
  dlgRel.dialog("open");
  //$("#setRelationship").find("input[type=text], textarea").val("");
  
  
});
</script>   
<%
   	}

   //setup the javascript to handle displaying an edit tissue sample dialog box
   if( (request.getParameter("edit")!=null) && request.getParameter("edit").equals("relationship")){
   %>
<script>
dlgRel.dialog("open");
</script>  

<%
  	}	

  //end relationship code

  ArrayList<Relationship> relationships=myShepherd.getAllRelationshipsForMarkedIndividual(sharky.getIndividualID());

  if(relationships.size()>0){
  %>


<table width="100%" class="tissueSample">
<th><strong><%=props.getProperty("roles")%></strong></th><th><strong><%=props.get("relationshipWith")%></strong></th><th><strong><%=props.getProperty("type")%></strong></th><th><strong><%=props.getProperty("community")%></strong></th>
<%
	if (isOwner && CommonConfiguration.isCatalogEditable(context)) {
%>
<th><%=props.getProperty("numSightingsTogether")%></th>
<th><strong><%=props.getProperty("edit")%></strong></th><th><strong><%=props.getProperty("remove")%></strong></th>
<%
	}
%>

</tr>
<%
	int numRels=relationships.size();
for(int f=0;f<numRels;f++){
	Relationship myRel=relationships.get(f);
	String indieName1=myRel.getMarkedIndividualName1();
	String indieName2=myRel.getMarkedIndividualName2();
	String otherIndyName=indieName2;
	String thisIndyRole="";
	String otherIndyRole="";
	if(myRel.getMarkedIndividualRole1()!=null){thisIndyRole=myRel.getMarkedIndividualRole1();}
	if(myRel.getMarkedIndividualRole2()!=null){otherIndyRole=myRel.getMarkedIndividualRole2();}
	if(otherIndyName.equals(sharky.getIndividualID())){
		otherIndyName=indieName1;
		thisIndyRole=myRel.getMarkedIndividualRole2();
		otherIndyRole=myRel.getMarkedIndividualRole1();
	}
	MarkedIndividual otherIndy=myShepherd.getMarkedIndividual(otherIndyName);
	String type="";
	if(myRel.getType()!=null){type=myRel.getType();}
	
	String community="";
	if(myRel.getRelatedSocialUnitName()!=null){community=myRel.getRelatedSocialUnitName();}
%>
	<tr>
	<td><em><%=thisIndyRole %></em>-<%=otherIndyRole %></td>
	<td>
	<a target="_blank" href="http://<%=CommonConfiguration.getURLLocation(request) %>/individuals.jsp?number=<%=otherIndy.getIndividualID()%>"><%=otherIndy.getIndividualID() %></a>
		<%
		if(otherIndy.getNickName()!=null){
		%>
		<br /><%=props.getProperty("nickname") %>: <%=otherIndy.getNickName()%>
		<%	
		}
		if(otherIndy.getAlternateID()!=null){
		%>
		<br /><%=props.getProperty("alternateID") %>: <%=otherIndy.getAlternateID()%>
		<%
		}
		if(otherIndy.getSex()!=null){
		%>
			<br /><span class="caption"><%=props.getProperty("sex") %>: <%=otherIndy.getSex() %></span>
		<%
		}
		
		if(otherIndy.getHaplotype()!=null){
		%>
			<br /><span class="caption"><%=props.getProperty("haplotype") %>: <%=otherIndy.getHaplotype() %></span>
		<%
		}
		%>
	</td>
	<td><%=type %></td>
	<td><a href="socialUnit.jsp?name=<%=community%>"><%=community %></a></td>
	
	<%
	if (isOwner && CommonConfiguration.isCatalogEditable(context)) {
		
		String persistenceID=myShepherd.getPM().getObjectId(myRel).toString();
		
		//int bracketLocation=persistenceID.indexOf("[");
		//persistenceID=persistenceID.substring(0,bracketLocation);

	%>
	<td>
	<%=myShepherd.getNumCooccurrencesBetweenTwoMarkedIndividual(otherIndy.getIndividualID(),sharky.getIndividualID()) %>
	
	</td>
	
	<td>
		<a href="http://<%=CommonConfiguration.getURLLocation(request) %>/individuals.jsp?number=<%=request.getParameter("number") %>&edit=relationship&type=<%=myRel.getType()%>&markedIndividualName1=<%=myRel.getMarkedIndividualName1() %>&markedIndividualRole1=<%=myRel.getMarkedIndividualRole1() %>&markedIndividualName2=<%=myRel.getMarkedIndividualName2() %>&markedIndividualRole2=<%=myRel.getMarkedIndividualRole2()%>&persistenceID=<%=persistenceID%>"><img width="24px" style="border-style: none;" src="images/Crystal_Clear_action_edit.png" /></a>
	</td>
	<td>
		<a onclick="return confirm('Are you sure you want to delete this relationship?');" href="RelationshipDelete?type=<%=myRel.getType()%>&markedIndividualName1=<%=myRel.getMarkedIndividualName1() %>&markedIndividualRole1=<%=myRel.getMarkedIndividualRole1() %>&markedIndividualName2=<%=myRel.getMarkedIndividualName2() %>&markedIndividualRole2=<%=myRel.getMarkedIndividualRole2()%>&persistenceID=<%=persistenceID%>"><img style="border-style: none;" src="images/cancel.gif" /></a>
	</td>
	<%
	}
	%>
	
	</tr>
<%


}
%>

</table>
<br/>
<%
}
else {
%>
	<p class="para"><%=props.getProperty("noSocial") %></p><br />
<%
}
//

%>




<a name="cooccurrence"></a>
<p><strong><%=props.getProperty("cooccurrence")%></strong></p>

<%
ArrayList<Map.Entry> otherIndies=myShepherd.getAllOtherIndividualsOccurringWithMarkedIndividual(sharky.getIndividualID());

if(otherIndies.size()>0){
	
//ok, let's iterate the social relationships
%>


<table width="100%" class="tissueSample">
<th><strong><%=props.get("sightedWith") %></strong></th><th><strong><%=props.getProperty("numSightingsTogether") %></strong></th></tr>
<%

Iterator<Map.Entry> othersIterator=otherIndies.iterator();
while(othersIterator.hasNext()){
	Map.Entry indy=othersIterator.next();
	MarkedIndividual occurIndy=myShepherd.getMarkedIndividual((String)indy.getKey());
	%>
	<tr><td>
	<a target="_blank" href="http://<%=CommonConfiguration.getURLLocation(request) %>/individuals.jsp?number=<%=occurIndy.getIndividualID()%>"><%=occurIndy.getIndividualID() %></a>
		<%
		if(occurIndy.getSex()!=null){
		%>
			<br /><span class="caption"><%=props.getProperty("sex") %>: <%=occurIndy.getSex() %></span>
		<%
		}
		
		if(occurIndy.getHaplotype()!=null){
		%>
			<br /><span class="caption"><%=props.getProperty("haplotype") %>: <%=occurIndy.getHaplotype() %></span>
		<%
		}
		%>
	</td>
	<td><%=((Integer)indy.getValue()).toString() %></td></tr>
	<%
}
%>
</table>
<%
}
else {
%>
	<p class="para"><%=props.getProperty("noCooccurrences") %></p><br />
<%
}
//



  if (isOwner) {
%>
<br />
<p>
<strong><img align="absmiddle" src="images/48px-Crystal_Clear_mimetype_binary.png" /> <%=additionalDataFiles %></strong> 
<%if ((sharky.getDataFiles()!=null)&&(sharky.getDataFiles().size() > 0)) {%>
</p>
<table>
  <%
    Vector addtlFiles = sharky.getDataFiles();
    for (int pdq = 0; pdq < addtlFiles.size(); pdq++) {
      String file_name = (String) addtlFiles.get(pdq);
  %>

  <tr>
    <td><a href="/<%=CommonConfiguration.getDataDirectoryName(context) %>/individuals/<%=sharky.getName()%>/<%=file_name%>"><%=file_name%>
    </a></td>
    <td>&nbsp;&nbsp;&nbsp;[<a
      href="IndividualRemoveDataFile?individual=<%=name%>&filename=<%=file_name%>"><%=delete %>
    </a>]
    </td>
  </tr>

  <%}%>
</table>
<%} else {%> <%=none %>
</p>
<%
  }
  if (CommonConfiguration.isCatalogEditable(context)) {
%>
<form action="IndividualAddFile" method="post"
      enctype="multipart/form-data" name="addDataFiles"><input
  name="action" type="hidden" value="fileadder" id="action"> <input
  name="individual" type="hidden" value="<%=sharky.getName()%>"
  id="individual">

  <p><%=addDataFile %>:</p>

  <p><input name="file2add" type="file" size="50"></p>

  <p><input name="addtlFile" type="submit" id="addtlFile"
            value="<%=sendFile %>"></p></form>
<%
  }




  }
%>




</td>
</tr>


</table>

</td>
</tr>
</table>
</div><!-- end maintext -->
</div><!-- end main-wide -->
<%
  if (CommonConfiguration.allowAdoptions(context)) {
%>

<div id="rightcol" style="vertical-align: top;">
  <div id="menu" style="vertical-align: top;">


    <div class="module">
      <jsp:include page="individualAdoptionEmbed.jsp" flush="true">
        <jsp:param name="name" value="<%=name%>"/>
      </jsp:include>
    </div>


  </div><!-- end menu -->
  </div><!-- end rightcol -->

  <%
   }
%>

<br /><br />
<table>
<tr>
<td>

      <jsp:include page="individualMapEmbed.jsp" flush="true">
        <jsp:param name="name" value="<%=name%>"/>
      </jsp:include>
</td>
</tr>
</table>

<%
if(isOwner){
%>
<p><img align="absmiddle" src="images/Crystal_Clear_app_kaddressbook.gif"> <strong><%=researcherComments %></strong>: </p>

<div style="text-align:left;border:1px solid black;width:100%;height:400px;overflow-y:scroll;overflow-x:scroll;">
	<p><%=sharky.getComments().replaceAll("\n", "<br>")%></p>
</div>
<%
  if (CommonConfiguration.isCatalogEditable(context) && isOwner) {
%>
<p>
	<form action="IndividualAddComment" method="post" name="addComments">
  		<input name="user" type="hidden" value="<%=request.getRemoteUser()%>" id="user">
  		<input name="individual" type="hidden" value="<%=sharky.getName()%>" id="individual">
  		<input name="action" type="hidden" value="comments" id="action">

  		<p><textarea name="comments" cols="60" id="comments"></textarea> <br />
    			<input name="Submit" type="submit" value="<%=addComments %>">
	</form>
</p>
<%
    } //if isEditable

}

} 

//could not find the specified individual!
else {

  //let's check if the entered name is actually an alternate ID
  ArrayList al = myShepherd.getMarkedIndividualsByAlternateID(name);
  ArrayList al2 = myShepherd.getMarkedIndividualsByNickname(name);
  ArrayList al3 = myShepherd.getEncountersByAlternateID(name);

  if (myShepherd.isEncounter(name)) {
	  %>
	  <meta http-equiv="REFRESH"
	        content="0;url=http://<%=CommonConfiguration.getURLLocation(request)%>/encounters/encounter.jsp?number=<%=name%>">
	  </HEAD>
	  <%
	  } 
	  else if(myShepherd.isOccurrence(name)) {
	  %>
	  <meta http-equiv="REFRESH"
	        content="0;url=http://<%=CommonConfiguration.getURLLocation(request)%>/occurrence.jsp?number=<%=name%>">
	  </HEAD>
	  <%	
	  }
  
	  else if (al.size() > 0) {
    //just grab the first one
    MarkedIndividual shr = (MarkedIndividual) al.get(0);
    String realName = shr.getName();
%>

<meta http-equiv="REFRESH"
      content="0;url=http://<%=CommonConfiguration.getURLLocation(request)%>/individuals.jsp?number=<%=realName%>">
</HEAD>
<%
} else if (al2.size() > 0) {
  //just grab the first one
  MarkedIndividual shr = (MarkedIndividual) al2.get(0);
  String realName = shr.getName();
%>

<meta http-equiv="REFRESH"
      content="0;url=http://<%=CommonConfiguration.getURLLocation(request)%>/individuals.jsp?number=<%=realName%>">
</HEAD>
<%
} else if (al3.size() > 0) {
  //just grab the first one
  Encounter shr = (Encounter) al3.get(0);
  String realName = shr.getEncounterNumber();
%>

<meta http-equiv="REFRESH"
      content="0;url=http://<%=CommonConfiguration.getURLLocation(request)%>/encounters/encounter.jsp?number=<%=realName%>">
</HEAD>
<%
} 
else {
%>


<p><%=matchingRecord %>: <strong><%=name%>
</strong><br/>
  <%=tryAgain %>
</p>

<p>

<form action="individuals.jsp" method="get" name="sharks"><strong><%=record %>:</strong>
  <input name="number" type="text" id="number" value=<%=name%>> <input
    name="sharky_button" type="submit" id="sharky_button"
    value="<%=getRecord %>"></form>
</p>
<p><font color="#990000"><a href="encounters/searchResults.jsp"><%=allEncounters %>
</a></font></p>

<p><font color="#990000"><a href="individualSearchResults.jsp"><%=allIndividuals %>
</a></font></p>
<%
      }
	  %>
      </td>
</tr>
</table>
</div><!-- end maintext -->
</div><!-- end main-wide -->
      
      <%
    }
  } catch (Exception eSharks_jsp) {
    System.out.println("Caught and handled an exception in individuals.jsp!");
    eSharks_jsp.printStackTrace();
  }



  myShepherd.rollbackDBTransaction();
  myShepherd.closeDBTransaction();

%>
<jsp:include page="footer.jsp" flush="true"/>
</div>



<!-- end page --></div>
<!--end wrapper -->
</body>
</html>

