<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="java.util.StringTokenizer,com.drew.imaging.jpeg.*, com.drew.metadata.*,java.util.Iterator,java.util.ArrayList,org.ecocean.servlet.*,org.ecocean.*, java.util.Vector, java.util.Enumeration, java.lang.Math,java.util.Properties, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException"%>

<%

//handle some cache-related security
response.setHeader("Cache-Control","no-cache"); //Forces caches to obtain a new copy of the page from the origin server
response.setHeader("Cache-Control","no-store"); //Directs caches not to store the page under any circumstance
response.setDateHeader("Expires", 0); //Causes the proxy cache to see the page as "stale"
response.setHeader("Pragma","no-cache"); //HTTP 1.0 backward compatibility 


//setup our Properties object to hold all properties
	Properties props=new Properties();
	String langCode="en";

	if(session.getAttribute("langCode")!=null){langCode=(String)session.getAttribute("langCode");}
	
	
	
	//load our variables for the submit page
	
	props.load(getClass().getResourceAsStream("/bundles/"+langCode+"/individuals.properties"));
	
	String markedIndividualTypeCaps=props.getProperty("markedIndividualTypeCaps");
	String nickname=props.getProperty("nickname");
	String nicknamer=props.getProperty("nicknamer");
	String alternateID=props.getProperty("alternateID");
	String sex=props.getProperty("sex");
	String setsex=props.getProperty("setsex");
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
	
	String name=request.getParameter("number").trim();
	Shepherd myShepherd=new Shepherd();

	
	boolean isOwner=false;
	if(request.isUserInRole("admin")){isOwner=true;}
	
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
-->
</style>

	
<!--
	1 ) Reference to the files containing the JavaScript and CSS.
	These files must be located on your server.
-->

<script type="text/javascript" src="highslide/highslide/highslide-with-gallery.js"></script>
<link rel="stylesheet" type="text/css" href="highslide/highslide/highslide.css" />

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

<div id="maincol-wide-solo">

<div id="maintext">
<%
			myShepherd.beginDBTransaction();
try{
	if (myShepherd.isMarkedIndividual(name)) {
	
	
		MarkedIndividual sharky=myShepherd.getMarkedIndividual(name);
		boolean hasAuthority = ServletUtilities.isUserAuthorizedForIndividual(sharky, request);

%>

<h1><strong><span class="para"><img src="images/tag_big.gif" width="50px" height="*" align="absmiddle" /></span>
<%=markedIndividualTypeCaps %></strong>: <%=sharky.getName()%></h1>
 <a name="alternateid"></a>
<p><img align="absmiddle" src="images/alternateid.gif"> <%=alternateID %>:
<%=sharky.getAlternateID()%> <%if(hasAuthority&&CommonConfiguration.isCatalogEditable()) {%>[<a
	href="individuals.jsp?number=<%=name%>&edit=alternateid#alternateid"><%=edit%></a>]<%}%>
</p>
<%
if(hasAuthority&&(request.getParameter("edit")!=null)&&(request.getParameter("edit").equals("alternateid"))){%>
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
			type="hidden" value="<%=request.getParameter("number")%>"> <%=alternateID %>:
		<input name="alternateid" type="text" id="alternateid" size="15"
			maxlength="150"><br> <input name="Name" type="submit"
			id="Name" value="<%=update %>"></form>
		</td>
	</tr>
</table>
</a><br> <%}%>
</p>


<p>
<%
if(CommonConfiguration.allowNicknames()){

		String myNickname="";
		if(sharky.getNickName()!=null){myNickname=sharky.getNickName();}
		String myNicknamer="";
		if(sharky.getNickNamer()!=null){myNicknamer=sharky.getNickNamer();}
	
	%>
<%=nickname %>: <%=myNickname%> 
	<%if(hasAuthority&&CommonConfiguration.isCatalogEditable()) {%>[<a href="individuals.jsp?number=<%=name%>&edit=nickname#nickname"><%=edit %></a>]<%}%>
 <br />
 <%=nicknamer %>: <%=myNicknamer%>

<br />
<%
	}


	 if(CommonConfiguration.isCatalogEditable()&&isOwner&&(request.getParameter("edit")!=null)&&(request.getParameter("edit").equals("nickname"))){%>
		<br /><br />
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
								type="text" id="namer" size="15" maxlength="50"><br> <input
								name="Name" type="submit" id="Name" value="<%=update %>"></form>
				</td>
			</tr>
		</table>
	</a>
	<br /> <%}%>

</p>
<p><%=sex %>: <%=sharky.getSex()%> <%if(isOwner&&CommonConfiguration.isCatalogEditable()) {%>[<a
	href="individuals.jsp?number=<%=name%>&edit=sex#sex"><%=edit %></a>]<%}%><br>
<%
		//edit sex
		if (CommonConfiguration.isCatalogEditable()&&isOwner&&(request.getParameter("edit")!=null)&&(request.getParameter("edit").equals("sex"))) {%>
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
<table id="encounter_report" width="100%">
	<tr>

		<td align="left" valign="top">
		<%
boolean showLogEncs=false;
if (isOwner) {
	showLogEncs=true;
}%>
		<p><strong><%=(sharky.totalEncounters()+sharky.totalLogEncounters())%></strong>
		<%=numencounters %></p>

		<table id="results" width="100%">
			<tr class="lineitem">
				<td class="lineitem" bgcolor="#99CCFF"></td>
				<td class="lineitem" align="left" valign="top" bgcolor="#99CCFF"><strong><%=encnumber %></strong></td>
				<td class="lineitem" align="left" valign="top" bgcolor="#99CCFF"><strong><%=alternateID %></strong></td>
				
				
				<td class="lineitem" align="left" valign="top" bgcolor="#99CCFF"><strong><%=date %></strong></td>
				<td class="lineitem" align="left" valign="top" bgcolor="#99CCFF"><strong><%=location %></strong></td>
				<td class="lineitem" align="left" valign="top" bgcolor="#99CCFF"><strong><%=sex %></strong></td>
				<%
	 if(isOwner && CommonConfiguration.useSpotPatternRecognition()) {
	 %>

				<td align="left" valign="top" bgcolor="#99CCFF"><strong><%=spots %></strong></td>
	<%}%>
			</tr>
			<%
			Encounter[] dateSortedEncs=sharky.getDateSortedEncounters(showLogEncs);

			int total=dateSortedEncs.length;
			for (int i=0; i<total;i++) {
				Encounter enc=dateSortedEncs[i];
					if((enc.isApproved())||(isOwner)) {
						Vector encImages=enc.getAdditionalImageNames();
						String imgName="";
						if(enc.isApproved()){
							imgName="encounters/"+enc.getEncounterNumber()+"/thumb.jpg";
						}
						else {
						          imgName="images/logbook.gif";
						}
						
						
		%>
			<tr>
				<td width="100" class="lineitem"><a
					href="http://<%=CommonConfiguration.getURLLocation()%>/encounters/encounter.jsp?number=<%=enc.getEncounterNumber()%>"><img
					src="<%=imgName%>" alt="encounter" border="0" /></a></td>
				<td class="lineitem"><a href="http://<%=CommonConfiguration.getURLLocation()%>/encounters/encounter.jsp?number=<%=enc.getEncounterNumber()%><%if(request.getParameter("noscript")!=null){%>&noscript=null<%}%>"><%=enc.getEncounterNumber()%></a></td>
				
				<%
				if(enc.getAlternateID()!=null){
				%>
				<td class="lineitem"><%=enc.getAlternateID()%></td>
				<%
				}
				else {
				%>
				<td class="lineitem"><%=none%></td>
				<%
				}
				%>
				
				
				<td class="lineitem"><%=enc.getDate()%></td>
				<td class="lineitem"><%=enc.getLocation()%></td>
				<td class="lineitem"><%=enc.getSex()%></td>
				
				<%
				if(CommonConfiguration.useSpotPatternRecognition()){
				%>
				<%if(((enc.getSpots().size()==0)&&(enc.getRightSpots().size()==0))&&(isOwner)) {%>
				<td class="lineitem">&nbsp;</td>
				<% } else if(isOwner&&(enc.getSpots().size()>0)&&(enc.getRightSpots().size()>0)) {%>
				<td class="lineitem">LR</td>
				<%}else if(isOwner&&(enc.getSpots().size()>0)) {%>
				<td class="lineitem">L</td>
				<%} else if(isOwner&&(enc.getRightSpots().size()>0)) {%>
				<td class="lineitem">R</td>
				<%
				}
				}
				%>
			</tr>
			<%}
		} //end for

%>




		</table>
		
			
				
				<!-- Start thumbnail gallery -->
				
				
				

		<p>
		<strong><%=props.getProperty("imageGallery") %></strong><br />
		

		<table id="results" border="0" width="100%" >
	<%		

			
			int countMe=0;
			Vector thumbLocs=new Vector();
			String[] keywords=keywords=new String[0];
			int numThumbnails = myShepherd.getNumThumbnails(sharky.getEncounters().iterator(), keywords);
			
			try {
				thumbLocs=myShepherd.getThumbnails(sharky.getEncounters().iterator(), 1, 99999, keywords);

			
			
					for(int rows=0;rows<15;rows++) {		%>

						<tr valign="top">

							<%
							for(int columns=0;columns<3;columns++){
								if(countMe<thumbLocs.size()) {
									String combined=(String)thumbLocs.get(countMe);
									StringTokenizer stzr=new StringTokenizer(combined,"BREAK");
									String thumbLink=stzr.nextToken();
									String encNum=stzr.nextToken();
									int fileNamePos=combined.lastIndexOf("BREAK")+5;
									String fileName=combined.substring(fileNamePos);
									boolean video=true;
									if(!thumbLink.endsWith("video.jpg")){
										thumbLink="http://"+CommonConfiguration.getURLLocation()+"/encounters/"+thumbLink;
										video=false;
									}
									String link="http://"+CommonConfiguration.getURLLocation()+"/encounters/"+encNum+"/"+fileName;
						
							%>

									<td>
										<table>
										<tr>
											<td valign="top">
											
												<%
												if(isOwner){
												%>
													<a href="<%=link%>" class="highslide" onclick="return hs.expand(this)">
												<%
												}
												%>
												<img src="<%=thumbLink%>" alt="photo" border="1" title="Click to enlarge" />
												<%
												if(isOwner){
												%>
													</a>
												<%
												}
												%>
										
										<div class="highslide-caption">
										
										<table>
											<tr>
												<td align="left" valign="top">
										
												<table>
										<%
											
										int kwLength=keywords.length;
										Encounter thisEnc = myShepherd.getEncounter(encNum);
										%>
										<tr><td><span class="caption"><em><%=(countMe+1) %>/<%=numThumbnails %></em></span></td></tr>
										<tr><td><span class="caption"><%=props.getProperty("location") %>: <%=thisEnc.getLocation() %></span></td></tr>
										<tr><td><span class="caption"><%=props.getProperty("locationID") %>: <%=thisEnc.getLocationID() %></span></td></tr>
										<tr><td><span class="caption"><%=props.getProperty("date") %>: <%=thisEnc.getDate() %></span></td></tr>
										<tr><td><span class="caption"><%=props.getProperty("catalogNumber") %>: <a href="<%= CommonConfiguration.getImageDirectory()%>/encounter.jsp?number=<%=thisEnc.getCatalogNumber() %>"><%=thisEnc.getCatalogNumber() %></a></span></td></tr>
										<%
										if(thisEnc.getVerbatimEventDate()!=null){
										%>
											<tr>
											
											<td><span class="caption"><%=props.getProperty("verbatimEventDate") %>: <%=thisEnc.getVerbatimEventDate() %></span></td></tr>
										<%
										}
										%>
										<tr>
										<td><span class="caption">
											<%=props.getProperty("matchingKeywords") %>
											<%
											//int numKeywords=myShepherd.getNumKeywords();
											Iterator allKeywords2=myShepherd.getAllKeywords();
											
											while(allKeywords2.hasNext()){
												Keyword word=(Keyword)allKeywords2.next();
									            if(word.isMemberOf(encNum+"/"+fileName)) {
									            	
									            	String renderMe=word.getReadableName();
									                	
										          	for(int kwIter=0;kwIter<kwLength;kwIter++) {
											              String kwParam=keywords[kwIter];
											              if(kwParam.equals(word.getIndexname())) {
											            	  renderMe="<strong>"+renderMe+"</strong>";
											              }
											       }	
									            	

								                	%>
													<br /><%= renderMe%>
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
					if((fileName.toLowerCase().endsWith("jpg"))||(fileName.toLowerCase().endsWith("jpeg"))){
						File exifImage=new File(getServletContext().getRealPath(("/"+CommonConfiguration.getImageDirectory()+"/"+thisEnc.getCatalogNumber()+"/"+fileName)));
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
											</tr>
										</table>
</div>
												</div>
											</td>
										</tr>
							
										
										<tr><td><span class="caption"><%=props.getProperty("location") %>: <%=thisEnc.getLocation() %></span></td></tr>
										<tr><td><span class="caption"><%=props.getProperty("locationID") %>: <%=thisEnc.getLocationID() %></span></td></tr>
										<tr><td><span class="caption"><%=props.getProperty("date") %>: <%=thisEnc.getDate() %></span></td></tr>
										<tr><td><span class="caption"><%=props.getProperty("catalogNumber") %>: <a href="<%=CommonConfiguration.getImageDirectory() %>/encounter.jsp?number=<%=thisEnc.getCatalogNumber() %>"><%=thisEnc.getCatalogNumber() %></a></span></td></tr>
										<tr>
										<td><span class="caption">
											<%=props.getProperty("matchingKeywords") %>
											<%
											//int numKeywords=myShepherd.getNumKeywords();
											Iterator allKeywords=myShepherd.getAllKeywords();
											
											while(allKeywords.hasNext()){
												Keyword word=(Keyword)allKeywords.next();
									            if(word.isMemberOf(encNum+"/"+fileName)) {
									            	
									            	String renderMe=word.getReadableName();
									                	
										          	for(int kwIter=0;kwIter<kwLength;kwIter++) {
											              String kwParam=keywords[kwIter];
											              if(kwParam.equals(word.getIndexname())) {
											            	  renderMe="<strong>"+renderMe+"</strong>";
											              }
											       }	
									            	

								                	%>
													<br /><%= renderMe%>
													<%
									              
									            }
									         }
											
											%>
										</span></td>
										</tr>
										
										</table>
									</td>
							<%
					
								countMe++;
								} //end if
							} //endFor
							%>
					</tr>
				<%} //endFor
	
				} catch(Exception e) {
					e.printStackTrace();
				%>
	<tr>
		<td>
		<p><%=props.getProperty("error")%></p>.</p>
		</td>
	</tr>
	<%}
%>

</table>
		
		
		
		<!-- end thumbnail gallery -->

		<p><strong><img src="images/2globe_128.gif" width="64" height="64" align="absmiddle" /><%=mapping %></strong></p>
		<%
	  Vector haveGPSData=new Vector();
	  haveGPSData=sharky.returnEncountersWithGPSData();
	  if(haveGPSData.size()>0) {
	
	
	%>
		<p><%=mappingnote %></p>


		<script
			src="http://maps.google.com/maps?file=api&amp;v=2&amp;key=<%=CommonConfiguration.getGoogleMapsKey() %>"
			type="text/javascript"></script> <script type="text/javascript">
    function initialize() {
      if (GBrowserIsCompatible()) {
        var map = new GMap2(document.getElementById("map_canvas"));
        
		
		<%
			double centroidX=0;
			int countPoints=0;
			double centroidY=0;
			for(int c=0;c<haveGPSData.size();c++) {
				Encounter mapEnc=(Encounter)haveGPSData.get(c);
				countPoints++;
				centroidX+=Double.parseDouble(mapEnc.getDWCDecimalLatitude());
				centroidY+=Double.parseDouble(mapEnc.getDWCDecimalLongitude());
			}
			centroidX=centroidX/countPoints;
			centroidY=centroidY/countPoints;
			
			%>
			map.setCenter(new GLatLng(<%=centroidX%>, <%=centroidY%>), 1);
			map.addControl(new GSmallMapControl());
        	map.addControl(new GMapTypeControl());
			map.setMapType(G_HYBRID_MAP);
			<%
			for(int t=0;t<haveGPSData.size();t++) {

				Encounter mapEnc=(Encounter)haveGPSData.get(t);
		
				double myLat=(new Double(mapEnc.getDWCDecimalLatitude())).doubleValue();
				double myLong=(new Double(mapEnc.getDWCDecimalLongitude())).doubleValue();

	
		%>
				          var point<%=t%> = new GLatLng(<%=myLat%>,<%=myLong%>, false);
						  var marker<%=t%> = new GMarker(point<%=t%>);
						  GEvent.addListener(marker<%=t%>, "click", function(){
						  	window.location="http://<%=CommonConfiguration.getURLLocation()%>/encounters/encounter.jsp?number=<%=mapEnc.getEncounterNumber()%>";
						  });
						  GEvent.addListener(marker<%=t%>, "mouseover", function(){
						  	marker<%=t%>.openInfoWindowHtml("<%=markedIndividualTypeCaps%>: <strong><a target=\"_blank\" href=\"http://<%=CommonConfiguration.getURLLocation()%>/individuals.jsp?number=<%=mapEnc.isAssignedToMarkedIndividual()%>\"><%=mapEnc.isAssignedToMarkedIndividual()%></a></strong><br><table><tr><td><img align=\"top\" border=\"1\" src=\"http://<%=CommonConfiguration.getURLLocation()%>/encounters/<%=mapEnc.getEncounterNumber()%>/thumb.jpg\"></td><td>Date: <%=mapEnc.getDate()%><br>Sex: <%=mapEnc.getSex()%><br>Size: <%=mapEnc.getSize()%> m<br><br><a target=\"_blank\" href=\"http://<%=CommonConfiguration.getURLLocation()%>/encounters/encounter.jsp?number=<%=mapEnc.getEncounterNumber()%>\" >Go to encounter</a></td></tr></table>");
						  });

						  
						  map.addOverlay(marker<%=t%>);
			
		<%	
			}
		%>
		
		
      }
    }
    </script>
		<div id="map_canvas" style="width: 510px; height: 350px"></div>

		<%} else {%>
		<p><%=noGPS %></p>
		<br> 
		<%}
		
	
    if (isOwner) {
%>
		
		<p><strong><%=additionalDataFiles %></strong>: <%if (sharky.getDataFiles().size()>0) {%>
		</p>
		<table>
			<%  
	  Vector addtlFiles=sharky.getDataFiles();
	  for (int pdq=0; pdq<addtlFiles.size(); pdq++) {
	  String file_name=(String)addtlFiles.get(pdq);
	  %>

			<tr>
				<td><img src="disk.gif"> <a
					href="sharks/<%=sharky.getName()%>/<%=file_name%>"><%=file_name%></a></td>
				<td>&nbsp;&nbsp;&nbsp;[<a
					href="IndividualRemoveDataFile?individual=<%=name%>&filename=<%=file_name%>"><%=delete %></a>]</td>
			</tr>

			<%}%>
		</table>
		<%} else {%> <%=none %>
		</p>
		<%}
		if(CommonConfiguration.isCatalogEditable()){
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
		

		
		<p><strong><%=researcherComments %></strong>: </p>
		<p><%=sharky.getComments().replaceAll("\n","<br>")%></p>
		<%
		if(CommonConfiguration.isCatalogEditable()){
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

} 
	else {

	//let's check if the entered name is actually an alternate ID
	ArrayList al = myShepherd.getMarkedIndividualsByAlternateID(name);
	ArrayList al2 = myShepherd.getMarkedIndividualsByNickname(name);
	ArrayList al3 = myShepherd.getEncountersByAlternateID(name);
	
	if(al.size()>0){
		//just grab the first one
		MarkedIndividual shr = (MarkedIndividual)al.get(0);
		String realName = shr.getName();
		%>

<meta http-equiv="REFRESH"
	content="0;url=http://<%=CommonConfiguration.getURLLocation()%>/individuals.jsp?number=<%=realName%>">
</HEAD>
<%
	}
	else if(al2.size()>0){
		//just grab the first one
		MarkedIndividual shr = (MarkedIndividual)al2.get(0);
		String realName = shr.getName();
		%>

<meta http-equiv="REFRESH"
	content="0;url=http://<%=CommonConfiguration.getURLLocation()%>/individuals.jsp?number=<%=realName%>">
</HEAD>
<%
	}
	else if(al3.size()>0){
		//just grab the first one
		Encounter shr = (Encounter)al3.get(0);
		String realName = shr.getEncounterNumber();
		%>

<meta http-equiv="REFRESH"
	content="0;url=http://<%=CommonConfiguration.getURLLocation()%>/encounters/encounter.jsp?number=<%=realName%>">
</HEAD>
<%
	}
	else if(myShepherd.isEncounter(name)){
		%>
<meta http-equiv="REFRESH"
	content="0;url=http://<%=CommonConfiguration.getURLLocation()%>/encounters/encounter.jsp?number=<%=name%>">
</HEAD>
<%
	}
	else{ 
%>




<p><%=matchingRecord %>: <strong><%=name%></strong>
<%=tryAgain %>.</p>
<p>
<form action="individuals.jsp" method="get" name="sharks"><strong><%=record %>:</strong>
<input name="number" type="text" id="number" value=<%=name%>> <input
	name="sharky_button" type="submit" id="sharky_button"
	value="<%=getRecord %>"></form>
</p>
<p><font color="#990000"><a href="encounters/allEncounters.jsp"><%=allEncounters %></a></font></p>
<p><font color="#990000"><a href="allIndividuals.jsp"><%=allIndividuals %></a></font></p>
<%}
}
}
catch(Exception eSharks_jsp){
	System.out.println("Caught and handled an exception in individuals.jsp!");
	eSharks_jsp.printStackTrace();
}


%>

</div>
<!-- end maintext --></div>
<!-- end main-wide -->

<%
if(CommonConfiguration.allowAdoptions()){
%>

<div id="rightcol">
<div id="menu">


<div class="module">
<jsp:include page="individualAdoptionEmbed.jsp" flush="true">
						<jsp:param name="name" value="<%=name%>" />
				</jsp:include>
</div>


</div>
<!-- end menu --></div>
<!-- end rightcol --> 
<%
}
%>

<%
		myShepherd.rollbackDBTransaction();
		myShepherd.closeDBTransaction();
	
	%> <jsp:include page="footer.jsp" flush="true" /></div>
<!-- end page --></div>
<!--end wrapper -->
</body>
</html>

