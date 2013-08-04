<%@ page contentType="text/html; charset=utf-8" language="java" import="org.ecocean.*, org.ecocean.servlet.*, java.util.*,javax.jdo.*,java.io.File" %>

<%--
  ~ The Shepherd Project - A Mark-Recapture Framework
  ~ Copyright (C) 2013 Jason Holmberg
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

<%

String encNum = request.getParameter("encounterNumber");


Shepherd myShepherd = new Shepherd();
		  
//let's set up references to our file system components
		  String rootWebappPath = getServletContext().getRealPath("/");
		  File webappsDir = new File(rootWebappPath).getParentFile();
		  File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName());
		  File encountersDir=new File(shepherdDataDir.getAbsolutePath()+"/encounters");
		  File encounterDir = new File(encountersDir, encNum);

		
try {
  //get the encounter number
 
  //set up the JDO pieces and Shepherd
  myShepherd.beginDBTransaction();
  Encounter enc=myShepherd.getEncounter(encNum);

  String langCode = "en";

  //check what language is requested
  if (session.getAttribute("langCode") != null) {
    langCode = (String) session.getAttribute("langCode");
  }
  
  Properties encprops = ShepherdProperties.getProperties("encounter.properties", langCode);
//handle translation

  
  boolean isOwner = ServletUtilities.isUserAuthorizedForEncounter(enc, request);
	
  
  boolean hasPhotos=false;
  if (enc.getSinglePhotoVideo() != null && enc.getSinglePhotoVideo().size() > 0) {
    hasPhotos=true;
  }

  //let's set up references to our file system components
  //String rootWebappPath = getServletContext().getRealPath("/");
  //File webappsDir = new File(rootWebappPath).getParentFile();
  //File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName());

%>
  <p><strong>Spot Matching Algorithms (Modified Groth and I3S)</strong></p>


	<!-- Display spot patterning so long as show_spotpatterning is not false in commonCOnfiguration.properties-->
  	<%
	if(CommonConfiguration.useSpotPatternRecognition()){
	%>

	<p class="para">  
  
  		<%
		//kick off a scan
		if (((enc.getNumSpots()>0)||(enc.getNumRightSpots()>0))) {
		%> 
		<br/>
		<table width="100%" border="0" cellpadding="1" cellspacing="0">
			<tr>
  				<td align="left" valign="top">
    				<p class="para">
    					<font color="#990000">
    						<strong>
    							<img align="absmiddle" src="../images/Crystal_Clear_action_find.gif"/>Find Pattern Match
    						</strong>
    					</font> 
    					<a href="<%=CommonConfiguration.getWikiLocation()%>sharkgrid" target="_blank"><img src="../images/information_icon_svg.gif" alt="Help" border="0" align="absmiddle"></a><br/>
                         <br/><br/>
  						<%
						String ready="No. Please add spot data.";
	  					if ((enc.getNumSpots()>0)||(enc.getNumRightSpots()>0)) {ready="Yes. ";
	   						if(enc.getNumSpots()>0) {
	   							ready+=" "+enc.getNumSpots()+" left-side spots added.";
	   						}
	   						if(enc.getNumRightSpots()>0) {
	   							ready+=" "+enc.getNumRightSpots()+" right-side spots added.";
	   						}
	   		
	  					}
						%>
  						<em><%=ready%></em><br />
  
  						<%
  						if((enc.getNumSpots()>0)||(enc.getNumRightSpots()>0)) { %>
							<font size="-1">
								<a id="rmspots" class="launchPopup"><img align="absmiddle" src="../images/cancel.gif"/></a> <a id="rmspots" class="launchPopup">Remove spots</a>
							</font> 
  
   							<%
							if (isOwner && CommonConfiguration.isCatalogEditable()) {
							%>

							<div id="dialogRmSpots" title="<%=encprops.getProperty("removeSpotData")%>" style="display:none">  
								<form name="removeSpots" method="post" action="../EncounterRemoveSpots">
         
									<table cellpadding="1" cellspacing="0" bordercolor="#FFFFFF">
 										<tr>
    										<td align="left" valign="top">
      
         										<table width="200">
            										<tr>
              										<%
                									if (enc.getSpots().size() > 0) {
              										%>
              											<td>
              												<label> <input name="rightSide" type="radio" value="false"> left-side</label>
              											</td>
              										<%
                									}
                									if (enc.getRightSpots().size() > 0) {
              										%>
              										<td>
              											<label> <input type="radio" name="rightSide" value="true"> right-side</label>
              										</td>
              										<%
                									}
              										%>
            									</tr>
            									<tr>
            										<td>
            											<input name="number" type="hidden" value="<%=encNum%>" /> 
          												<input name="action" type="hidden" value="removeSpots" /> 
          												<input name="Remove3" type="submit" id="Remove3" value="Remove" />
      												</td>
      											</tr>
          									</table>
          
    									</td>
  									</tr>
								</table>
							</form>
						</div>

						<script>
						var dlgRmSpots = $("#dialogRmSpots").dialog({
							autoOpen: false,
							draggable: false,
							resizable: false,
							width: 600
						});

						$("a#rmspots").click(function() {
							dlgRmSpots.dialog("open");
						});
						</script>   
						<!-- end remove spots popup --> 
						<%
						} //end if is owner

	  				} //end if has spots
  					%>
  
  
  					<br />
  					Scan entire database on the <a href="http://www.sharkgrid.org">sharkGrid</a> using the 
  					<a href="http://www.blackwell-synergy.com/doi/pdf/10.1111/j.1365-2664.2005.01117.x">Modified Groth</a> and 
  					<a href="http://www.blackwell-synergy.com/doi/abs/10.1111/j.1365-2664.2006.01273.x?journalCode=jpe">I3S</a> algorithms.

    				<div id="formDiv">
      					<form name="formSharkGrid" id="formSharkGrid" method="post" action="../ScanTaskHandler">
      						<input name="action" type="hidden" id="action" value="addTask" /> 
      						<input name="encounterNumber" type="hidden" value="<%=encNum%>" />
        						<table width="200px">
          							<tr>
            						<%
              						if ((enc.getSpots() != null) && (enc.getSpots().size() > 0)) {
            						%>
            							<td class="para">
            								<label>
            									<input name="rightSide" type="radio" value="false" checked="checked" /> left-side
            								</label>
            							</td>
            						<%
              						}
            						
              						if ((enc.getRightSpots() != null) && (enc.getRightSpots().size() > 0) && (enc.getSpots() != null) && (enc.getSpots().size() == 0)) {
            						%>
            							<td class="para">
            								<label>
            									<input type="radio" name="rightSide" value="true" checked="checked" />right-side
            								</label>
            							</td>
            						<%
            						} 
              						else if ((enc.getRightSpots() != null) && (enc.getRightSpots().size() > 0)) {
            						%>
            						<td class="para">
            							<label> 
            								<input type="radio" name="rightSide" value="true" /> right-side
            							</label>
            						</td>
            						<%
              						}
            						%>
          						</tr>
        					</table>

        					<input name="writeThis" type="hidden" id="writeThis" value="true" />
        					<br/> 
        					<input name="scan" type="submit" id="scan" value="Start Scan" onclick="submitForm(document.getElementById('formSharkGrid'))" />
        					<input name="cutoff" type="hidden" value="0.02" />
        				</form>

					</div>
				</td>
			</tr>
		</table>
		<br/>
			<%
			
  

		

  			File leftScanResults = new File(encounterDir.getAbsolutePath() + "/lastFullScan.xml");
  			File rightScanResults = new File(encounterDir.getAbsolutePath() + "/lastFullRightScan.xml");
  			File I3SScanResults = new File(encounterDir.getAbsolutePath() + "/lastFullI3SScan.xml");
  			File rightI3SScanResults = new File(encounterDir.getAbsolutePath() + "/lastFullRightI3SScan.xml");

  	
	  		if((leftScanResults.exists())&&(enc.getNumSpots()>0)) {
	  		%> 
	  			<br/>
	  			<br/>
	  			<a href="scanEndApplet.jsp?writeThis=true&number=<%=encNum%>">Groth: Left-side scan results</a> 
	  		<%
	  		}
	  		if((rightScanResults.exists())&&(enc.getNumRightSpots()>0)) {
	  		%> 
	  			<br/>
	  			<br/>
	  			<a href="scanEndApplet.jsp?writeThis=true&number=<%=encNum%>&rightSide=true">Groth: Right-side scan results</a> 
	  		<%
	  		}
	  		if((I3SScanResults.exists())&&(enc.getNumSpots()>0)) {
	  		%> 
	  			<br/>
	  			<br/>
	  			<a href="i3sScanEndApplet.jsp?writeThis=true&number=<%=encNum%>&I3S=true">I3S: Left-side scan results</a> <%
	  		}
	  		if((rightI3SScanResults.exists())&&(enc.getNumRightSpots()>0)) {
	  		%> 
	  			<br/>
	  			<br/>
	  			<a href="i3sScanEndApplet.jsp?writeThis=true&number=<%=encNum%>&rightSide=true&I3S=true">I3S: Right-side scan results</a> 
	  			<%
	  		}
	  		//} //end if-owner
	
	  		%>
			<!-- End Display spot patterning so long as show_spotpatterning is not false in commonConfiguration.properties-->

  


<%
		} //if use spot pattern reognition
	}
}	//end try
catch(Exception e) {
  e.printStackTrace();
}
finally {
  myShepherd.rollbackDBTransaction();
  myShepherd.closeDBTransaction();
}
%>
