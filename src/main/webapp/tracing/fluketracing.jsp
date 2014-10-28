
<%--
  ~ Wildbook - A Mark-Recapture Framework
  ~ Copyright (C) 2008-2014 Jason Holmberg
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
         import="org.ecocean.servlet.ServletUtilities,com.drew.imaging.jpeg.JpegMetadataReader, com.drew.metadata.Directory, com.drew.metadata.Metadata, com.drew.metadata.Tag, org.ecocean.*,org.ecocean.servlet.ServletUtilities,org.ecocean.Util,org.ecocean.Measurement, org.ecocean.Util.*, org.ecocean.genetics.*, org.ecocean.tag.*, java.awt.Dimension, javax.jdo.Extent, javax.jdo.Query, java.io.File, java.text.DecimalFormat, java.util.*" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>         

<%!
  //shepherd must have an open trasnaction when passed in
  public String getNextIndividualNumber(Encounter enc, Shepherd myShepherd, String context) {
    String returnString = "";
    try {
      String lcode = enc.getLocationCode();
      if ((lcode != null) && (!lcode.equals(""))) {
        //let's see if we can find a string in the mapping properties file
        Properties props = new Properties();
        //set up the file input stream
        //props.load(getClass().getResourceAsStream("/bundles/newIndividualNumbers.properties"));
        props=ShepherdProperties.getProperties("newIndividualNumbers.properties", "",context);
        //let's see if the property is defined
        if (props.getProperty(lcode) != null) {
          returnString = props.getProperty(lcode);
          int startNum = 1;
          boolean keepIterating = true;
          //let's iterate through the potential individuals
          while (keepIterating) {
            String startNumString = Integer.toString(startNum);
            if (startNumString.length() < 3) {
              while (startNumString.length() < 3) {
                startNumString = "0" + startNumString;
              }
            }
            String compositeString = returnString + startNumString;
            if (!myShepherd.isMarkedIndividual(compositeString)) {
              keepIterating = false;
              returnString = compositeString;
            } else {
              startNum++;
            }
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
	String context="context0";
	context=ServletUtilities.getContext(request);
	//get encounter number
	String num = request.getParameter("number").replaceAll("\\+", "").trim();
	//let's set up references to our file system components
	String rootWebappPath = getServletContext().getRealPath("/");
	File webappsDir = new File(rootWebappPath).getParentFile();
	File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));
	File encountersDir=new File(shepherdDataDir.getAbsolutePath()+"/encounters");
	File encounterDir = new File(encountersDir, num);
	GregorianCalendar cal = new GregorianCalendar();
	int nowYear = cal.get(1);
	//handle some cache-related security
	response.setHeader("Cache-Control", "no-cache"); //Forces caches to obtain a new copy of the page from the origin server
	response.setHeader("Cache-Control", "no-store"); //Directs caches not to store the page under any circumstance
	response.setDateHeader("Expires", 0); //Causes the proxy cache to see the page as "stale"
	response.setHeader("Pragma", "no-cache"); //HTTP 1.0 backward compatibility
	//gps decimal formatter
	DecimalFormat gpsFormat = new DecimalFormat("###.####");
	//handle translation
	//String langCode = "en";
	String langCode=ServletUtilities.getLanguageCode(request); 
	//let's load encounters.properties
  	//Properties encprops = new Properties();
  	//encprops.load(getClass().getResourceAsStream("/bundles/" + langCode + "/encounter.properties"));
	Properties encprops = ShepherdProperties.getProperties("encounter.properties", langCode, context);	
	pageContext.setAttribute("num", num);	
	// get the Shepherd class based on context
	Shepherd myShepherd = new Shepherd(context);
	Extent allKeywords = myShepherd.getPM().getExtent(Keyword.class, true);
	Query kwQuery = myShepherd.getPM().newQuery(allKeywords);
	boolean proceed = true;
	boolean haveRendered = false;	
	pageContext.setAttribute("set", encprops.getProperty("set"));
%>

<html>

<head prefix="og:http://ogp.me/ns#">
	<title><%=CommonConfiguration.getHTMLTitle(context) %> - <%=encprops.getProperty("encounter") %> <%=num%>
	</title>
	<meta http-equiv="Content-Type" content="text/html; charset=utf-8"/>
	<meta name="Description"  content="<%=CommonConfiguration.getHTMLDescription(context) %>"/>
	<meta name="Keywords" content="<%=CommonConfiguration.getHTMLKeywords(context) %>"/>
	<meta name="Author" content="<%=CommonConfiguration.getHTMLAuthor(context) %>"/> 
	<!-- social meta start -->
	<meta property="og:site_name" content='<%=CommonConfiguration.getHTMLTitle(context) %> - <%=encprops.getProperty("encounter") %> <%=request.getParameter("number") %>' />
	<link rel="canonical" href='http://<%=CommonConfiguration.getURLLocation(request) %>/encounters/encounter.jsp?number=<%=request.getParameter("number") %>' />
	<meta itemprop="name" content='<%=encprops.getProperty("encounter")%> <%=request.getParameter("number")%>' />
	<meta itemprop="description" content="<%=CommonConfiguration.getHTMLDescription(context)%>" />
	<meta property="og:title" content='<%=CommonConfiguration.getHTMLTitle(context) %> - <%=encprops.getProperty("encounter") %> <%=request.getParameter("number") %>' />
	<meta property="og:description" content="<%=CommonConfiguration.getHTMLDescription(context)%>" />
	<meta property="og:url" content='http://<%=CommonConfiguration.getURLLocation(request) %>/encounters/encounter.jsp?number=<%=request.getParameter("number") %>' />
	<meta property="og:type" content="website" />
	<!-- social meta end -->  
	<link rel="stylesheet" type="text/css" href="<%=CommonConfiguration.getCSSURLLocation(request,context) %>"/>
	<link rel="shortcut icon" href="<%=CommonConfiguration.getHTMLShortcutIcon(context) %>"/>
	<link rel="stylesheet" href="css/standard.css" type="text/css"/>
	<link rel="stylesheet" href="css/tools.css" type="text/css"/>
	<link rel="stylesheet" href="css/main.css" type="text/css"/>
	<!-- Load the Paper.js library which will control the canvas for image and vector rendering -->
	<script type="text/javascript" src="js/paper.js"></script>
	<!-- Load the local page control script -->
	<script type="text/javascript" src="js/tracing.js"></script>
	<!-- Create a short script to initialize the paper script object and its image -->
	<%
	Encounter traceEncounter = myShepherd.getEncounter(num);
	SinglePhotoVideo tracePhoto=traceEncounter.getImages().get(0);
	%>
	<script type="text/javascript">
		//paper.install(window);
		window.onload = function() {
			// get the canvas to be used by the paper script
			var canvas = document.getElementById('myCanvas');
			// assign the canvas to the paperscript paper object
			paper.setup(canvas);
			// create a new tool to process mouse and keyboard events
			paper.tool = new paper.Tool()
			paper.tool.attach('mousedown',comEcostatsTracing.onMouseDown);
			// show the current image
			comEcostatsTracing.addImage('/<%=CommonConfiguration.getDataDirectoryName(context)+traceEncounter.dir("")+"/"+tracePhoto.getFilename()%>');
		}
	</script>
</head>
<!--added below for improved map selection -->
<body>
	<div id="wrapper">
		<div id="page">
			<jsp:include page="../header.jsp" flush="true">
  				<jsp:param name="isAdmin" value='<%=request.isUserInRole(\"admin\")%>' />
			</jsp:include>								
			<div id="main">
			<%
	  			myShepherd.beginDBTransaction();	
				if (myShepherd.isEncounter(num)) {
		    			try {
		      			Encounter enc = myShepherd.getEncounter(num);
		      			pageContext.setAttribute("enc", enc);
		      			String livingStatus = "";
		      			if ((enc.getLivingStatus()!=null)&&(enc.getLivingStatus().equals("dead"))) {
		        			livingStatus = " (deceased)";
		      			}
						int numImages=myShepherd.getAllSinglePhotoVideosForEncounter(enc.getCatalogNumber()).size();
						//let's see if this user has ownership and can make edits
		      			boolean isOwner = ServletUtilities.isUserAuthorizedForEncounter(enc, request);
		      			pageContext.setAttribute("editable", isOwner && CommonConfiguration.isCatalogEditable(context));
		      			boolean loggedIn = false;
		      			try{
		      				if(request.getUserPrincipal()!=null){loggedIn=true;}
		      			}catch(NullPointerException nullLogged){}      			
		      			String headerBGColor="FFFFFC";
		      		%>
					<%
  					String classColor="approved_encounters";
					boolean moreStates=true;
					int cNum=0;
					while(moreStates){
 						String currentLifeState = "encounterState"+cNum;
 						if(CommonConfiguration.getProperty(currentLifeState,context)!=null){
							if(CommonConfiguration.getProperty(currentLifeState,context).equals(enc.getState())){
								moreStates=false;
								if(CommonConfiguration.getProperty(("encounterStateCSSClass"+cNum),context)!=null){
									classColor=CommonConfiguration.getProperty(("encounterStateCSSClass"+cNum),context);
								}
							}
							cNum++;
						}else{
   							moreStates=false;
						}
					} //end while
  					%> 	
  				
  					<!-- 
   					<p class="warnDiv"><strong><%=encprops.getProperty("title") %></strong>: <%=num%><%=livingStatus %></p>	    						
	    			<p class="caption"><em><%=encprops.getProperty("description") %></em></p>
	    			-->
					<!-- content image divide -->
				    <div>
				    	<input type="button" class="winButton draw" value="Draw" onclick="comEcostatsTracing.onDraw(event);"/>
				    	<input type="button" class="winButton photo" value="Image tools" onclick="comEcostatsTracing.onImageTools(event);"/>
				    </div>
				    <div>
				    	<table width="800px" style="border-collapse: collapse;border: hidden;">
				    		<tr>
					    		<td colspan="3" width="800px"> 
					    			<!-- layer for drawing nodes on each fluke -->
								    <div id="drawdiv" class="window" style="min-height:140px")>
								    	 <div id="top" class="bold top">Drawing Fluke Nodes</div> 
								    	 <!-- 
								    	 <div class="innerWindow floatright">
								    	 	<input id="shownodes" type="checkbox"/>Show nodes
								    	 </div> 
								    	 -->
								    	 <div class="panel">
					    					<div>
												Edit type:
												<select id="node_edit_type" name="node_edit_type" onchange="comEcostatsTracing.editNodeChange(event);" >
													<option value="add_nodes" selected="true">Add nodes</option>
													<option value="type_nodes">Node types</option>
													<option value="remove_nodes">Remove nodes</option>
													<option value="insert_nodes">Insert nodes</option>
												</select>
										    	Fluke:
										    	 <select id="finlr" name="finlr" onchange="comEcostatsTracing.editNodeChange(event);">
										    	 	<option value="left" selected="true">left fluke tracing</option>
										    	 	<option value="right">right fluke tracing</option>
										    	 </select>
									    	</div>
					    					<p/>
								    	 	<div>
								    	 		<input type="button" class="winButton view" value="Show" onclick="comEcostatsTracing.showTrace(event);"/>
										    	<input type="button" class="winButton hide" value="Hide" onclick="comEcostatsTracing.hideTrace(event);"/>
						    					<input type="button" class="winButton eraser" value="Clear" onclick="comEcostatsTracing.onClear(event);"/>
						    					<input type="button" class="winButton calculate" value="Auto draw" />
						    					<input type="button" class="winButton save" value="Save" onclick="comEcostatsTracing.saveTracings();"/>
					    					</div>
								    	 </div>
								    </div>
								    <!-- layer for editing nodes on each fluke -->
								    <div id="removediv" class="window" style="visibility:hidden;;display: none;min-height:140px")>
									    <div id="top" class="bold top">Image Tools</div> 
							    	 	<div class="panel">
							    	 	    Image options: 
				    						<input type="button" class="winButton redo" value="Flip image" onclick="comEcostatsTracing.onFlip(event);"/>
				   							<input type="button" class="winButton zoomin" value="Zoom in" onclick="comEcostatsTracing.zoomIn(event)" />
				   							<input type="button" class="winButton zoomout" value="Zoom out" onclick="comEcostatsTracing.zoomOut(event)" />
				    					</div>
				    					<div class="panel">
				    						<!-- Zoom: <input type="number" id="myRange" value="100" min="0" max="200" onchange="rangechange(event);">% -->
				    						<!-- Zoom: <input type="number" id="iZoom" value="100" min="0" max="1000" step="10" onchange="comEcostatsTracing.zoom(event);">-->
				    						Rotate: <input type="number" width="10px" id="myRotate" value="0" min="-180" max="180" onchange="comEcostatsTracing.rotatechange(event);">
				    					</div>
								    </div>
					    		</td>
				    		</tr>
				    		<tr>
				    			<td align="left" style="border:0px;text-align:left;font-weight: bold;">Left Fluke</td>
				    			<td align="right" style="border:0px;text-align:right;font-weight: bold;">Right Fluke</td>
				    		</tr>
				    		<tr>
				    			<td colspan="3" align="center"><canvas id="myCanvas" style="cursor: crosshair" resize="true" width="800px" height="800px"></canvas></td>
				    		</tr>
				    	</table>						
					</div>
				<%
				kwQuery.closeAll();
				myShepherd.rollbackDBTransaction();
				myShepherd.closeDBTransaction();
				kwQuery=null;
				myShepherd=null;
				
				}// div main
				catch(Exception e){
					e.printStackTrace();
					%>
						<p>Hit an error.<br /> <%=e.toString()%></p>
					</body>
					</html>
					<%
					}
		    //end if this is an encounter
			} else {
		  		myShepherd.rollbackDBTransaction();
		  		myShepherd.closeDBTransaction();
				%>
				<p class="para">There is no encounter #<%=num%> in the database. Please double-check the encounter number and try again.</p>
				<form action="encounter.jsp" method="post" name="encounter"><strong>Go to encounter: </strong> <input name="number" type="text" value="<%=num%>" size="20"> <input name="Go" type="submit" value="Submit" /></form>
				<p><font color="#990000"><a href="../individualSearchResults.jsp">View all individuals</a></font></p>
		  <%}%>
		
		</div>		
		<jsp:include page="../footer.jsp" flush="true"/>
	</div><!-- end page -->
</div><!--end wrapper -->
</body>
</html>

