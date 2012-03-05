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
         import="com.drew.imaging.jpeg.JpegMetadataReader,com.drew.metadata.Directory, com.drew.metadata.Metadata,com.drew.metadata.Tag,org.ecocean.*,org.ecocean.servlet.ServletUtilities,java.io.File, java.util.*, org.ecocean.genetics.*" %>

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
  if (request.isUserInRole("admin")) {
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
.full_screen_map {
position: absolute !important;
top: 0px !important;
left: 0px !important;
z-index: 1 !imporant;
width: 100% !important;
height: 100% !important;
margin-top: 0px !important;
margin-bottom: 8px !important;
</style>

<script src="http://maps.google.com/maps/api/js?sensor=false"></script>
<script type="text/javascript" src="http://ajax.googleapis.com/ajax/libs/jquery/1.4.1/jquery.min.js"></script>
  <script type="text/javascript" src="encounters/StyledMarker.js"></script>
  
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
    if(!request.isUserInRole("imageProcessor")){
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

<h1><strong><span class="para"><img src="images/tag_big.gif" width="50px" height="*"
                                    align="absmiddle"/></span>
  <%=markedIndividualTypeCaps %>
</strong>: <%=sharky.getName()%>
</h1>
<a name="alternateid"></a>

<p><img align="absmiddle" src="images/alternateid.gif"> <%=alternateID %>:
  <%=sharky.getAlternateID()%> <%if (hasAuthority && CommonConfiguration.isCatalogEditable()) {%>[<a
    href="individuals.jsp?number=<%=name%>&edit=alternateid#alternateid"><%=edit%>
  </a>]<%}%>
</p>
<%
  if (hasAuthority && (request.getParameter("edit") != null) && (request.getParameter("edit").equals("alternateid"))) {%>
<br>
<table border="1" cellpadding="1" cellspacing="0" bordercolor="#000000"
       bgcolor="#99CCFF">
  <tr>
    <td align="left" valign="top"><span class="style1"><%=setAlternateID %>:</span></td>
  </tr>
  <tr>
    <td align="left" valign="top">
      <form name="set_alternateid" method="post"
            action="IndividualSetAlternateID"><input name="individual"
                                                     type="hidden"
                                                     value="<%=request.getParameter("number")%>"> <%=alternateID %>
        :
        <input name="alternateid" type="text" id="alternateid" size="15"
               maxlength="150"><br> <input name="Name" type="submit"
                                           id="Name" value="<%=update %>"></form>
    </td>
  </tr>
</table>
</a><br> <%}%>
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
  <%=nickname %>: <%=myNickname%>
  <%if (hasAuthority && CommonConfiguration.isCatalogEditable()) {%>[<a
  href="individuals.jsp?number=<%=name%>&edit=nickname#nickname"><%=edit %>
</a>]<%}%>
  <br/>
  <%=nicknamer %>: <%=myNicknamer%>

  <br/>
  <%
    }


    if (CommonConfiguration.isCatalogEditable() && isOwner && (request.getParameter("edit") != null) && (request.getParameter("edit").equals("nickname"))) {%>
  <br/><br/>
  <a name="nickname">
    <table border="1" cellpadding="1" cellspacing="0" bordercolor="#000000" bgcolor="#99CCFF">
      <tr>
        <td align="left" valign="top"><span class="style1"><%=setNickname %>:</span></td>
      </tr>
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
  </a>
  <br/> <%}%>

</p>
<p><%=sex %>: <%=sharky.getSex()%> <%if (isOwner && CommonConfiguration.isCatalogEditable()) {%>[<a
  href="individuals.jsp?number=<%=name%>&edit=sex#sex"><%=edit %>
</a>]<%}%><br>
  <%
    //edit sex
    if (CommonConfiguration.isCatalogEditable() && isOwner && (request.getParameter("edit") != null) && (request.getParameter("edit").equals("sex"))) {%>
  <br><a name="sex">
    <table border="1" cellpadding="1" cellspacing="0" bordercolor="#000000"
           bgcolor="#99CCFF">
      <tr>
        <td align="left" valign="top"><span class="style1"><%=setsex %>:</span></td>
      </tr>
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
  </a><br> <%}%>

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
  <font size="-1">[<a
    href="individuals.jsp?number=<%=request.getParameter("number").trim()%>&edit=dynamicproperty&name=<%=nm%>#dynamicproperty">edit</a>]</font>
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
  <%
boolean showLogEncs=false;
if (isOwner) {
	showLogEncs=true;
}%>
<p><strong><%=(sharky.totalEncounters() + sharky.totalLogEncounters())%>
</strong>
  <%=numencounters %>
</p>

<table id="results" width="100%">
  <tr class="lineitem">
    <td class="lineitem" bgcolor="#99CCFF"></td>
    <td class="lineitem" align="left" valign="top" bgcolor="#99CCFF"><strong><%=encnumber %>
    </strong></td>
    <td class="lineitem" align="left" valign="top" bgcolor="#99CCFF"><strong><%=alternateID %>
    </strong></td>


    <td class="lineitem" align="left" valign="top" bgcolor="#99CCFF"><strong><%=date %>
    </strong></td>
    <td class="lineitem" align="left" valign="top" bgcolor="#99CCFF"><strong><%=location %>
    </strong></td>
    <td class="lineitem" align="left" valign="top" bgcolor="#99CCFF"><strong><%=sex %>
    </strong></td>
    <%
      if (isOwner && CommonConfiguration.useSpotPatternRecognition()) {
    %>

    <td align="left" valign="top" bgcolor="#99CCFF"><strong><%=spots %>
    </strong></td>
    <%}%>
  </tr>
  <%
    Encounter[] dateSortedEncs = sharky.getDateSortedEncounters(showLogEncs);

    int total = dateSortedEncs.length;
    for (int i = 0; i < total; i++) {
      Encounter enc = dateSortedEncs[i];
      
        Vector encImages = enc.getAdditionalImageNames();
        String imgName = "";
        
          imgName = "/"+CommonConfiguration.getDataDirectoryName()+"/encounters/" + enc.getEncounterNumber() + "/thumb.jpg";
        


  %>
  <tr>
    <td width="100" class="lineitem"><a
      href="http://<%=CommonConfiguration.getURLLocation(request)%>/encounters/encounter.jsp?number=<%=enc.getEncounterNumber()%>"><img
      src="<%=imgName%>" alt="encounter" border="0"/></a></td>
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
    <td class="lineitem"><%=none%>
    </td>
    <%
      }
    %>


    <td class="lineitem"><%=enc.getDate()%>
    </td>
    <td class="lineitem"><%=enc.getLocation()%>
    </td>
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
  </tr>
  <%
      
    } //end for

  %>


</table>


<!-- Start thumbnail gallery -->


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

<!-- Start genetics -->
<br />
<a name="tissueSamples"></a>
<p class="para"><img align="absmiddle" src="images/microscope.gif" /><strong><%=props.getProperty("tissueSamples") %></strong></p>
<p>
<%
List<TissueSample> tissueSamples=sharky.getAllTissueSamples();

int numTissueSamples=tissueSamples.size();
if(numTissueSamples>0){
%>
<table width="100%" class="tissueSample">
<tr><th><strong><%=props.getProperty("sampleID") %></strong></th><th><strong><%=props.getProperty("values") %></strong></th><th><strong><%=props.getProperty("analyses") %></strong></th></tr>
<%
for(int j=0;j<numTissueSamples;j++){
	TissueSample thisSample=tissueSamples.get(j);
	%>
	<tr><td><span class="caption"><a href="encounters/encounter.jsp?number=<%=thisSample.getCorrespondingEncounterNumber() %>#tissueSamples"><%=thisSample.getSampleID()%></a></span></td><td><span class="caption"><%=thisSample.getHTMLString() %></span></td>
	
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
<%
}

%>
<!-- End genetics -->

<!-- Start mapping -->
<br />
<p><strong><img src="images/2globe_128.gif" width="64" height="64" align="absmiddle"/><%=mapping %></strong></p>
<%
  Vector haveGPSData = sharky.returnEncountersWithGPSData();
  int havegpsSize=haveGPSData.size();
  if (havegpsSize > 0) {
%>


    <script type="text/javascript">
      function initialize() {
        var center = new google.maps.LatLng(0,0);
        var mapZoom = 1;
    	if($("#map_canvas").hasClass("full_screen_map")){mapZoom=3;}
    	var bounds = new google.maps.LatLngBounds();
        
        var map = new google.maps.Map(document.getElementById('map_canvas'), {
          zoom: mapZoom,
          center: center,
          mapTypeId: google.maps.MapTypeId.HYBRID
        });

    	  //adding the fullscreen control to exit fullscreen
    	  var fsControlDiv = document.createElement('DIV');
    	  var fsControl = new FSControl(fsControlDiv, map);
    	  fsControlDiv.index = 1;
    	  map.controls[google.maps.ControlPosition.TOP_RIGHT].push(fsControlDiv);

        
        var markers = [];
 
 
        
        <%


 for(int y=0;y<havegpsSize;y++){
	 Encounter thisEnc=(Encounter)haveGPSData.get(y);
	 

 %>
          
          var latLng = new google.maps.LatLng(<%=thisEnc.getDecimalLatitude()%>, <%=thisEnc.getDecimalLongitude()%>);
          bounds.extend(latLng);
           <%

           
           //currently unused programatically
           String markerText="";
           
           String haploColor="CC0000";
           if((props.getProperty("defaultMarkerColor")!=null)&&(!props.getProperty("defaultMarkerColor").trim().equals(""))){
        	   haploColor=props.getProperty("defaultMarkerColor");
           }
		   
           
           %>
           var marker = new StyledMarker({styleIcon:new StyledIcon(StyledIconTypes.MARKER,{color:"<%=haploColor%>",text:"<%=markerText%>"}),position:latLng,map:map});
	    

            google.maps.event.addListener(marker,'click', function() {
                 (new google.maps.InfoWindow({content: '<strong><a target=\"_blank\" href=\"http://<%=CommonConfiguration.getURLLocation(request)%>/individuals.jsp?number=<%=thisEnc.isAssignedToMarkedIndividual()%>\"><%=thisEnc.isAssignedToMarkedIndividual()%></a></strong><br /><table><tr><td><img align=\"top\" border=\"1\" src=\"/<%=CommonConfiguration.getDataDirectoryName()%>/encounters/<%=thisEnc.getEncounterNumber()%>/thumb.jpg\"></td><td>Date: <%=thisEnc.getDate()%><br />Sex: <%=thisEnc.getSex()%><%if(thisEnc.getSizeAsDouble()!=null){%><br />Size: <%=thisEnc.getSize()%> m<%}%><br /><br /><a target=\"_blank\" href=\"http://<%=CommonConfiguration.getURLLocation(request)%>/encounters/encounter.jsp?number=<%=thisEnc.getEncounterNumber()%>\" >Go to encounter</a></td></tr></table>'})).open(map, this);
             });
 
	
          markers.push(marker);
          map.fitBounds(bounds); 
 
 <%
 

} 

 %>
 

      }
      
      

      function fullScreen(){
    		$("#map_canvas").addClass('full_screen_map');
    		$('html, body').animate({scrollTop:0}, 'slow');
    		initialize();
    		
    		//hide header
    		$("#header_menu").hide();
    		
    		if(overlaysSet){overlaysSet=false;setOverlays();}
    		//alert("Trying to execute fullscreen!");
    	}


    	function exitFullScreen() {
    		$("#header_menu").show();
    		$("#map_canvas").removeClass('full_screen_map');

    		initialize();
    		if(overlaysSet){overlaysSet=false;setOverlays();}
    		//alert("Trying to execute exitFullScreen!");
    	}


    	//making the exit fullscreen button
    	function FSControl(controlDiv, map) {

    	  // Set CSS styles for the DIV containing the control
    	  // Setting padding to 5 px will offset the control
    	  // from the edge of the map
    	  controlDiv.style.padding = '5px';

    	  // Set CSS for the control border
    	  var controlUI = document.createElement('DIV');
    	  controlUI.style.backgroundColor = '#f8f8f8';
    	  controlUI.style.borderStyle = 'solid';
    	  controlUI.style.borderWidth = '1px';
    	  controlUI.style.borderColor = '#a9bbdf';;
    	  controlUI.style.boxShadow = '0 1px 3px rgba(0,0,0,0.5)';
    	  controlUI.style.cursor = 'pointer';
    	  controlUI.style.textAlign = 'center';
    	  controlUI.title = 'Toggle the fullscreen mode';
    	  controlDiv.appendChild(controlUI);

    	  // Set CSS for the control interior
    	  var controlText = document.createElement('DIV');
    	  controlText.style.fontSize = '12px';
    	  controlText.style.fontWeight = 'bold';
    	  controlText.style.color = '#000000';
    	  controlText.style.paddingLeft = '4px';
    	  controlText.style.paddingRight = '4px';
    	  controlText.style.paddingTop = '3px';
    	  controlText.style.paddingBottom = '2px';
    	  controlUI.appendChild(controlText);
    	  //toggle the text of the button
    	   if($("#map_canvas").hasClass("full_screen_map")){
    	      controlText.innerHTML = 'Exit Fullscreen';
    	    } else {
    	      controlText.innerHTML = 'Fullscreen';
    	    }

    	  // Setup the click event listeners: toggle the full screen

    	  google.maps.event.addDomListener(controlUI, 'click', function() {

    	   if($("#map_canvas").hasClass("full_screen_map")){
    	    exitFullScreen();
    	    } else {
    	    fullScreen();
    	    }
    	  });

    	}

      
      
      google.maps.event.addDomListener(window, 'load', initialize);
    </script>


<p><%=mappingnote %>
</p>

 <div id="map_canvas" style="width: 770px; height: 510px; "></div>

<%} else {%>
<p><%=noGPS %>
</p>
<br>
<%
  }


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
%>

<br />
<p><img align="absmiddle" src="images/Crystal_Clear_app_kaddressbook.gif"> <strong><%=researcherComments %>
</strong>: </p>

<p><%=sharky.getComments().replaceAll("\n", "<br>")%>
</p>
<%
  if (CommonConfiguration.isCatalogEditable()) {
%>
<p>

<form action="IndividualAddComment" method="post" name="addComments">
  <input name="user" type="hidden" value="<%=request.getRemoteUser()%>" id="user">
  <input name="individual" type="hidden" value="<%=sharky.getName()%>" id="individual">
  <input name="action" type="hidden" value="comments" id="action">

  <p><textarea name="comments" cols="60" id="comments"></textarea> <br>
    <input name="Submit" type="submit" value="<%=addComments %>">
</form>
</p>
<%
    } //if isEditable


  }
%>


</p>


</td>
</tr>
</table>

<%

} else {

  //let's check if the entered name is actually an alternate ID
  ArrayList al = myShepherd.getMarkedIndividualsByAlternateID(name);
  ArrayList al2 = myShepherd.getMarkedIndividualsByNickname(name);
  ArrayList al3 = myShepherd.getEncountersByAlternateID(name);

  if (al.size() > 0) {
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
} else if (myShepherd.isEncounter(name)) {
%>
<meta http-equiv="REFRESH"
      content="0;url=http://<%=CommonConfiguration.getURLLocation(request)%>/encounters/encounter.jsp?number=<%=name%>">
</HEAD>
<%
} else {
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
<p><font color="#990000"><a href="encounters/allEncounters.jsp"><%=allEncounters %>
</a></font></p>

<p><font color="#990000"><a href="allIndividuals.jsp"><%=allIndividuals %>
</a></font></p>
<%
      }
    }
  } catch (Exception eSharks_jsp) {
    System.out.println("Caught and handled an exception in individuals.jsp!");
    eSharks_jsp.printStackTrace();
  }


%>
</td>
</tr>
</table>
</div><!-- end maintext -->
</div><!-- end main-wide -->

<%
  if (CommonConfiguration.allowAdoptions()) {
%>

<div id="rightcol">
  <div id="menu">


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

<%
  myShepherd.rollbackDBTransaction();
  myShepherd.closeDBTransaction();

%>
<jsp:include page="footer.jsp" flush="true"/>
</div>
<!-- end page --></div>
<!--end wrapper -->
</body>
</html>

