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

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java"
         import="com.drew.imaging.jpeg.JpegMetadataReader,com.drew.metadata.Directory, 	   
		 com.drew.metadata.Metadata,com.drew.metadata.Tag,org.ecocean.*,org.ecocean.servlet.ServletUtilities,java.io.File, java.util.*, org.ecocean.genetics.*" %>

<%

  //handle some cache-related security
  response.setHeader("Cache-Control", "no-cache"); //Forces caches to obtain a new copy of the page from the origin server
  response.setHeader("Cache-Control", "no-store"); //Directs caches not to store the page under any circumstance
  response.setDateHeader("Expires", 0); //Causes the proxy cache to see the page as "stale"
  response.setHeader("Pragma", "no-cache"); //HTTP 1.0 backward compatibility

  //setup data dir
  String rootWebappPath = getServletContext().getRealPath("/");
  File webappsDir = new File(rootWebappPath).getParentFile();
  File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName());
  //if(!shepherdDataDir.exists()){shepherdDataDir.mkdir();}
  File encountersDir=new File(shepherdDataDir.getAbsolutePath()+"/encounters");
  //if(!encountersDir.exists()){encountersDir.mkdir();}
  //File thisEncounterDir = new File(encountersDir, number);

//setup our Properties object to hold all properties
  Properties props = new Properties();
  String langCode = "en";

  if (session.getAttribute("langCode") != null) {
    langCode = (String) session.getAttribute("langCode");
  }


  //load our variables for the submit page

  props.load(getClass().getResourceAsStream("/bundles/" + langCode + "/individuals.properties"));

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
  Shepherd myShepherd = new Shepherd();



  boolean isOwner = false;
  if (request.getUserPrincipal()!=null) {
    isOwner = true;
  }

%>

<html>
<head>

  <title><%=CommonConfiguration.getHTMLTitle() %>
  </title>
  <meta http-equiv="Content-Type" content="text/html; charset=utf-8"/>
  <meta name="Description"
        content="<%=CommonConfiguration.getHTMLDescription() %>"/>
  <meta name="Keywords"
        content="<%=CommonConfiguration.getHTMLKeywords() %>"/>
  <meta name="Author" content="<%=CommonConfiguration.getHTMLAuthor() %>"/>
  <link href="<%=CommonConfiguration.getCSSURLLocation(request) %>"
        rel="stylesheet" type="text/css"/>
  <link rel="shortcut icon"
        href="<%=CommonConfiguration.getHTMLShortcutIcon() %>"/>
        


 
  
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

<!--  FACEBOOK LIKE BUTTON -->
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
<script src="http://ajax.googleapis.com/ajax/libs/jquery/1.4.2/jquery.min.js"></script>
<script src="http://ajax.googleapis.com/ajax/libs/jqueryui/1.8.4/jquery-ui.min.js"></script>



</head>

<body <%if (request.getParameter("noscript") == null) {%>
  onload="initialize()" onunload="GUnload()" <%}%>>
<div id="wrapper">
<div id="page">
<jsp:include page="header.jsp" flush="true">

	<jsp:param name="isAdmin" value="<%=request.isUserInRole(\"admin\")%>" />
</jsp:include>
<div id="main">

<%
  if (CommonConfiguration.allowAdoptions()) {
	  ArrayList adoptions = myShepherd.getAllAdoptionsForMarkedIndividual(name);
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
      boolean hasAuthority = ServletUtilities.isUserAuthorizedForIndividual(sharky, request);

%>

<table><tr>
<td>
<span class="para"><img src="images/tag_big.gif" width="75px" height="75px" align="absmiddle"/></span>
</td>
<td valign="middle">
 <h1><strong> <%=markedIndividualTypeCaps %>
</strong>: <%=sharky.getIndividualID()%></h1>
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
<div class="fb-like" data-send="false" data-layout="button_count" data-width="100" data-show-faces="false"></div>
</td>
</tr></table></p>
<a name="alternateid"></a>
<%
String altID="";
if(sharky.getAlternateID()!=null){
	altID=sharky.getAlternateID();
}

%>
<p><img align="absmiddle" src="images/alternateid.gif"> <%=alternateID %>:
  <%=altID%> <%if (hasAuthority && CommonConfiguration.isCatalogEditable()) {%><a style="color:blue;cursor: pointer;" id="alternateID"><img align="absmiddle" width="20px" height="20px" style="border-style: none;" src="images/Crystal_Clear_action_edit.png" /></a><%}%>

  
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
    if(CommonConfiguration.showProperty("showTaxonomy")){
    
    String genusSpeciesFound=props.getProperty("notAvailable");
    if(sharky.getGenusSpecies()!=null){genusSpeciesFound=sharky.getGenusSpecies();}
    %>
    
        <p class="para"><img align="absmiddle" src="images/taxontree.gif">
          <%=props.getProperty("taxonomy")%>: <em><%=genusSpeciesFound%></em>
       </p>

<%
}
%>

<p>
  <%
    if (CommonConfiguration.allowNicknames()) {

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
  <%if (hasAuthority && CommonConfiguration.isCatalogEditable()) {%><a id="nickname" style="color:blue;cursor: pointer;"><img align="absmiddle" width="20px" height="20px" style="border-style: none;" src="images/Crystal_Clear_action_edit.png" /></a><%}%>
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
<p><%=sex %>: <%=sharky.getSex()%> <%if (isOwner && CommonConfiguration.isCatalogEditable()) {%><a id="sex" style="color:blue;cursor: pointer;"><img align="absmiddle" width="20px" height="20px" style="border-style: none;" src="images/Crystal_Clear_action_edit.png" /></a><%}%><br />
  <%
    //edit sex
    if (CommonConfiguration.isCatalogEditable() && isOwner) {%>
  
    <!-- Now prep the popup dialog -->
<div id="dialogSex" title="<%=setsex %>" style="display:none">
  
    <table border="1" cellpadding="1" cellspacing="0" bordercolor="#FFFFFF">

      <tr>
        <td align="left" valign="top">
          <form name="setxsexshark" action="IndividualSetSex" method="post">

            <select name="selectSex" size="1" id="selectSex">
              <option value="unknown">unknown</option>
              <option value="male">male</option>
              <option value="female">female</option>
            </select><br> <input name="individual" type="hidden" value="<%=name%>"
                                 id="individual"> <input name="Add" type="submit" id="Add"
                                                         value="<%=update %>">
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
    if (isOwner && CommonConfiguration.isCatalogEditable()) {
  %>
  <font size="-1"><a
    href="individuals.jsp?number=<%=request.getParameter("number").trim()%>&edit=dynamicproperty&name=<%=nm%>#dynamicproperty"><img align="absmiddle" width="20px" height="20px" style="border-style: none;" src="images/Crystal_Clear_action_edit.png" /></a></font>
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
      if (isOwner && CommonConfiguration.useSpotPatternRecognition()) {
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
        
          imgName = "/"+CommonConfiguration.getDataDirectoryName()+"/encounters/" + enc.getEncounterNumber() + "/thumb.jpg";
        
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
      if (CommonConfiguration.useSpotPatternRecognition()) {
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
    					<a href="individuals.jsp?number=<%=thisName%>"><%=thisName %></a>&nbsp;
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
			  if (CommonConfiguration.allowAdoptions()) {
				  ArrayList adoptions = myShepherd.getAllAdoptionsForMarkedIndividual(name);
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
										thumbLink="/"+CommonConfiguration.getDataDirectoryName()+"/encounters/"+thumbLocs.get(countMe).getCorrespondingEncounterNumber()+"/"+thumbLocs.get(countMe).getDataCollectionEventID()+".jpg";
										video=false;
									}
									else{
										thumbLink="http://"+CommonConfiguration.getURLLocation(request)+"/images/video.jpg";
										
									}
									String link="/"+CommonConfiguration.getDataDirectoryName()+"/encounters/"+thumbLocs.get(countMe).getCorrespondingEncounterNumber()+"/"+thumbLocs.get(countMe).getFilename();
						
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
                        <td><span
                          class="caption"><%=props.getProperty("location") %>: <%=thisEnc.getLocation() %></span>
                        </td>
                      </tr>
                      <tr>
                        <td><span
                          class="caption"><%=props.getProperty("locationID") %>: <%=thisEnc.getLocationID() %></span>
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
                      if (CommonConfiguration.showEXIFData()) {
                   
            	if(!thumbLink.endsWith("video.jpg")){
           		 %>							
					<span class="caption">
						<div class="scroll">	
						<span class="caption">
					<%
            if ((thumbLocs.get(countMe).getFilename().toLowerCase().endsWith("jpg")) || (thumbLocs.get(countMe).getFilename().toLowerCase().endsWith("jpeg"))) {
              try{
              File exifImage = new File(encountersDir.getAbsolutePath() + "/" + thisEnc.getCatalogNumber() + "/" + thumbLocs.get(countMe).getFilename());
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
  <td><span class="caption"><%=props.getProperty("location") %>: <%=thisEnc.getLocation() %></span>
  </td>
</tr>
<tr>
  <td><span
    class="caption"><%=props.getProperty("locationID") %>: <%=thisEnc.getLocationID() %></span></td>
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
if(CommonConfiguration.showUsersToPublic()){
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
                         			profilePhotoURL="/"+CommonConfiguration.getDataDirectoryName()+"/users/"+thisUser.getUsername()+"/"+thisUser.getUserImage().getFilename();

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
					<p><span class="caption"><strong><%=props.getProperty("msMarkers") %></strong></span></p>
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
	<a target="_blank" href="individuals.jsp?number=<%=occurIndy.getIndividualID()%>"><%=occurIndy.getIndividualID() %></a>
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
}
else {
%>
	<p class="para"><%=props.getProperty("noSocial") %></p><br />
<%
}
//

%>
</table>
<%

  if (isOwner) {
%>
<br />
<p>
<strong><img align="absmiddle" src="images/48px-Crystal_Clear_mimetype_binary.png" /> <%=additionalDataFiles %></strong>: 
<%if (sharky.getDataFiles().size() > 0) {%>
</p>
<table>
  <%
    Vector addtlFiles = sharky.getDataFiles();
    for (int pdq = 0; pdq < addtlFiles.size(); pdq++) {
      String file_name = (String) addtlFiles.get(pdq);
  %>

  <tr>
    <td><a href="/<%=CommonConfiguration.getDataDirectoryName() %>/individuals/<%=sharky.getName()%>/<%=file_name%>"><%=file_name%>
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
  if (CommonConfiguration.isCatalogEditable()) {
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
  if (CommonConfiguration.allowAdoptions()) {
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
  if (CommonConfiguration.isCatalogEditable() && isOwner) {
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

