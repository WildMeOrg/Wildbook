<%@ page contentType="text/html; charset=utf-8" language="java" import="org.ecocean.servlet.ServletUtilities,java.awt.Dimension,org.ecocean.*,
org.ecocean.media.MediaAsset,
java.net.URL,
org.ecocean.servlet.*, java.util.*,javax.jdo.*,java.io.File" %>
<%@ taglib uri="http://www.sunwesttek.com/di" prefix="di" %>
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

String context="context0";
context=ServletUtilities.getContext(request);
if(CommonConfiguration.useSpotPatternRecognition(context)){
	
	
String encNum = request.getParameter("encounterNumber");


Shepherd myShepherd = new Shepherd(context);
myShepherd.setAction("spotMatchingAlgorithm.jsp");
		  
//let's set up references to our file system components
		 
			String rootWebappPath = getServletContext().getRealPath("/");
		  File webappsDir = new File(rootWebappPath).getParentFile();
		  File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));
		  File encountersDir=new File(shepherdDataDir.getAbsolutePath()+"/encounters");
		  //File encounterDir = new File(encountersDir, encNum);
		
		
		File encounterDir = new File(Encounter.dir(shepherdDataDir, encNum));
		
try {
	
  	

	
	
	
  //get the encounter number
 
  //set up the JDO pieces and Shepherd
  myShepherd.beginDBTransaction();
  Encounter enc=myShepherd.getEncounter(encNum);

  //String langCode = "en";

  String langCode=ServletUtilities.getLanguageCode(request);
  

  
  
  Properties encprops = ShepherdProperties.getProperties("encounter.properties", langCode, context);
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
  <p><a name="spotpatternmatching"></a><strong>Spot Matching Algorithms (Modified Groth and I3S)</strong></p>



	<!-- Display spot patterning so long as show_spotpatterning is not false in commonCOnfiguration.properties-->

	
  <p class="para"><strong><em>Extracted Spots</em></strong></p>
  		<%
		//kick off a scan
		MediaAsset spotLeftMA = null;
		MediaAsset spotRightMA = null;
		ArrayList<MediaAsset> allSpotMAs = enc.findAllMediaByLabel(myShepherd, "_spot");
		////////if ((allSpotMAs != null) && (allSpotMAs.size() > 0)) spotLeftMA = allSpotMAs.get(0);
//// warning, hack to get around bug cause by gap in code changes post-migration
if (allSpotMAs != null) {
  for (MediaAsset maL : allSpotMAs) {
    if (maL.getFilename().indexOf("extractRight") < 0) {
      spotLeftMA = maL;
      break;
    }
  }
}
		allSpotMAs = enc.findAllMediaByLabel(myShepherd, "_spotRight");
		if ((allSpotMAs != null) && (allSpotMAs.size() > 0)) spotRightMA = allSpotMAs.get(0);
		if (((enc.getNumSpots()>0)||(enc.getNumRightSpots()>0))) {
		%> 
		
		<table width="100%" border="0" cellpadding="1" cellspacing="0">
			<tr>
  				<td align="left" valign="top">
    				<p class="para">
    					
  						<%
						String ready="Please add spot data.";
	  					if ((enc.getNumSpots()>0)||(enc.getNumRightSpots()>0)) {ready="";
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
  						if(((enc.getNumSpots()>0)||(enc.getNumRightSpots()>0)) && isOwner && CommonConfiguration.isCatalogEditable(context)) { %>
							<font size="-1">
								<a id="rmspots" class="launchPopup"><img align="absmiddle" src="../images/cancel.gif"/></a> <a id="rmspots" class="launchPopup">Remove spots</a>
							</font> 
  

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
						

	  				} //end if has spots
  					%>
  
  
				</td>
			</tr>
		</table>
		
			
			<p class="para"><strong><em>Pattern Matching Results</em></strong></p>
			<%
  			File leftScanResults = new File(encounterDir.getAbsolutePath() + "/lastFullScan.xml");
  			File rightScanResults = new File(encounterDir.getAbsolutePath() + "/lastFullRightScan.xml");
  			File I3SScanResults = new File(encounterDir.getAbsolutePath() + "/lastFullI3SScan.xml");
  			File rightI3SScanResults = new File(encounterDir.getAbsolutePath() + "/lastFullRightI3SScan.xml");

  	//if((CommonConfiguration.getProperty("algorithms", context)!=null)&&(CommonConfiguration.getProperty("algorithms", context).indexOf("ModifiedGroth")!=-1)){
  				
	  		if((leftScanResults.exists())&&(enc.getNumSpots()>0)) {
	  		%> 
	  			
	  			<a class="para" href="scanEndApplet.jsp?writeThis=true&number=<%=encNum%>">Groth: Left-side scan results</a><br />
	  		<%
	  		}
	  		if((rightScanResults.exists())&&(enc.getNumRightSpots()>0)) {
	  		%> 
	  			
	  			<a class="para" href="scanEndApplet.jsp?writeThis=true&number=<%=encNum%>&rightSide=true">Groth: Right-side scan results</a><br /> 
	  		<%
	  		}
		//}
	  		
  	//if((CommonConfiguration.getProperty("algorithms", context)!=null)&&(CommonConfiguration.getProperty("algorithms", context).indexOf("I3S")!=-1)){
			
	  		if((I3SScanResults.exists())&&(enc.getNumSpots()>0)) {
	  		%> 
	  			
	  			<a class="para" href="i3sScanEndApplet.jsp?writeThis=true&number=<%=encNum%>&I3S=true">I3S: Left-side scan results</a><br /> <%
	  		}
	  		if((rightI3SScanResults.exists())&&(enc.getNumRightSpots()>0)) {
	  		%> 
	  			
	  			<a class="para" href="i3sScanEndApplet.jsp?writeThis=true&number=<%=encNum%>&rightSide=true&I3S=true">I3S: Right-side scan results</a><br /> 
	  			<%
	  		}
	  		
	  		
  	//}


		} //if use spot pattern recognition
		else{
		%>
		<p>No spots have been added.</p>
		<%	
		}

%>



  	<p>
    <%
		 	if (request.getParameter("isOwner").equals("true")&&CommonConfiguration.useSpotPatternRecognition(context)&&((enc.getNumSpots()>0)||(enc.getNumRightSpots()>0))) {
		 	

		 			
		 			//File extractImage=new File(((new File(".")).getCanonicalPath()).replace('\\','/')+"/"+CommonConfiguration.getImageDirectory()+File.separator+imageEncNum+"/extract"+imageEncNum+".jpg");
		 			File extractImage=new File(encounterDir.getAbsolutePath()+"/extract"+encNum+".jpg");

		 			//File extractRightImage=new File(((new File(".")).getCanonicalPath()).replace('\\','/')+"/"+CommonConfiguration.getImageDirectory()+File.separator+imageEncNum+"/extractRight"+imageEncNum+".jpg");
		 			File extractRightImage=new File(encounterDir.getAbsolutePath()+"/extractRight"+encNum+".jpg");

		 			
		 			//File uploadedFile=new File(((new File(".")).getCanonicalPath()).replace('\\','/')+"/"+CommonConfiguration.getImageDirectory()+File.separator+imageEncNum+"/"+imageEnc.getSpotImageFileName());
		 			File uploadedFile=new File(encounterDir.getAbsolutePath()+"/"+enc.getSpotImageFileName());

		 			
		 			//File uploadedRightFile=new File(((new File(".")).getCanonicalPath()).replace('\\','/')+"/"+CommonConfiguration.getImageDirectory()+File.separator+imageEncNum+"/"+imageEnc.getRightSpotImageFileName());
		 			File uploadedRightFile=new File(encounterDir.getAbsolutePath()+"/"+enc.getRightSpotImageFileName());

		 			
		 			String extractLocation="file-"+encounterDir.getAbsolutePath()+"/extract"+encNum+".jpg";
		 			String extractRightLocation="file-"+encounterDir.getAbsolutePath()+"/extractRight"+encNum+".jpg";
		 			
		 			
		 			String addText=enc.getSpotImageFileName();
		 			String addTextRight=enc.getRightSpotImageFileName();
		 			//System.out.println(addText);
		 			String height="";
		 			String width="";
		 			String heightR="";
		 			String widthR="";
		 			
		 			
		 			if((uploadedFile.exists())&&(uploadedFile.isFile())&&(uploadedFile.length()>0)&&(enc.getNumSpots()>0)) {

		 				//System.out.println("     uploadedFile exists!");
		 				
		 				Dimension imageDimensions = org.apache.sanselan.Sanselan.getImageSize(uploadedFile);
		 				

		 				//iInfo.setInput(new FileInputStream(uploadedFile));
		 				if (!extractImage.exists()) {
		 					System.out.println("Made it here.");
		 					
		 					height+=Double.toString(imageDimensions.getHeight());
		 					width+=Double.toString(imageDimensions.getWidth());
		 					//height+=iInfo.getHeight();
		 					//width+=iInfo.getWidth();
		 					
		 					
		 					
		 					//System.out.println(height+"and"+width);
		 					int intHeight=((new Double(height)).intValue());
		 					int intWidth=((new Double(width)).intValue());
		 					//System.out.println("Made it here: "+imageEnc.hasSpotImage+" "+imageEnc.hasRightSpotImage);
		 					//System.gc();
		 					%>
  							<di:img width="<%=intWidth%>" height="<%=intHeight%>" imgParams="rendering=speed,quality=low" expAfter="0" border="0" threading="limited" output="<%=extractLocation%>">
          						<%
          						String src_ur_value=encounterDir.getAbsolutePath()+"/"+addText;
          			
          						%>
    							<di:image srcurl="<%=src_ur_value%>"/>
  							</di:img> 
  							
  							<%
							}

						}
									//set the right file
									
						if((uploadedRightFile.exists())&&uploadedRightFile.isFile()&&(uploadedRightFile.length()>0)&&(enc.getNumRightSpots()>0)) {
									
									//iInfo=new ImageInfo();
									Dimension imageDimensions = org.apache.sanselan.Sanselan.getImageSize(uploadedRightFile);
		 				

									//iInfo.setInput(new FileInputStream(uploadedRightFile));
									if (!extractRightImage.exists()) {
										System.out.println("extractRight does not exist: Made it here.");
										//heightR+=iInfo.getHeight();
										//widthR+=iInfo.getWidth();
										//System.out.println(height+"and"+width);
										
										heightR+=Double.toString(imageDimensions.getHeight());
		 								widthR+=Double.toString(imageDimensions.getWidth());
										
										
										int intHeightR=((new Double(heightR)).intValue());
										int intWidthR=((new Double(widthR)).intValue());
										System.gc();
										%>
  										<di:img width="<%=intWidthR%>" height="<%=intHeightR%>" imgParams="rendering=speed,quality=low" expAfter="0" threading="limited" border="0" output="<%=extractRightLocation%>">
          									<%
          									String src_ur_value=encounterDir.getAbsolutePath()+"/"+addTextRight;
          									%>
    										<di:image srcurl="<%=src_ur_value%>"/>
  										</di:img> 
  									<%
									}
									else{
										System.out.println("extractRight exists at: "+extractRightImage.getAbsolutePath());
										
									}

								}
									
					%>

<p class="para"><strong><em>Spot data image files used for matching</em></strong><br/> 
<font size="-1">[<a id="changespotimage" class="launchPopup">reset left or right spot data image</a>]</font><br/>
  

 
<div id="dialogChangeSpotImage" title="Set Spot Image File" style="display:none">  
<table cellpadding="1" cellspacing="0" bordercolor="#FFFFFF" >
    <tr>
      <td class="para">
        <form action="../EncounterAddSpotFile" method="post"
              enctype="multipart/form-data" name="addSpotsFile"><input
          name="action" type="hidden" value="fileadder" id="action">
          <input name="number" type="hidden" value="<%=encNum%>" id="shark">
          <font color="#990000"><strong><img align="absmiddle" src="../images/upload_small.gif"/></strong> 
           </font><br/> <label><input name="rightSide" type="radio" value="false" />left</label><br/> <label>
           
           <input name="rightSide" type="radio" value="true" /> right</label><br/>
          <br/> 
          <input name="file2add" type="file" size="15" /><br/>
          <input name="addtlFile" type="submit" id="addtlFile" value="Upload spot image" />
          </form>
      </td>
    </tr>
  </table>
	
</div>

<script>
var dlgChangeSpotImage = $("#dialogChangeSpotImage").dialog({
  autoOpen: false,
  draggable: false,
  resizable: false,
  width: 600
});

$("a#changespotimage").click(function() {
  dlgChangeSpotImage.dialog("open");
});

var spotJson = {};
</script>   
<!-- end reset spot image popup --> 

  <table border="0" cellpadding="5"><tr>
  <%
	String spotJsonLeft = null;
	String spotJsonRight = null;

	ArrayList<SuperSpot> spots = new ArrayList<SuperSpot>();

  if ((enc.getNumSpots() > 0) && (spotLeftMA != null)) {
	URL spotMAUrl = spotLeftMA.webURL();
      spots = enc.getSpots();
			spotJsonLeft = "[";
			for (SuperSpot s : spots) {
				spotJsonLeft += "{ \"type\": \"spot\", \"xy\" : [ " + s.getCentroidX() + "," + s.getCentroidY() + "] },\n";
			}
			spotJsonLeft += "];";
%>
<td valign="top" class="spot-td spot-td-left"><div>Left-side</div>
<div id="spot-image-wrapper-left">
	<img src="<%=spotMAUrl%>" alt="image" id="spot-image-left" />
	<canvas id="spot-image-canvas-left"></canvas>
</div>
</td> 
  <%
    }

    if ((enc.getNumRightSpots() > 0) && (spotRightMA != null)) {
	URL spotMAUrl = spotRightMA.webURL();
      spots = enc.getRightSpots();
			spotJsonRight = "[";
			for (SuperSpot s : spots) {
				spotJsonRight += "{ \"type\": \"spot\", \"xy\" : [ " + s.getCentroidX() + "," + s.getCentroidY() + "] },\n";
			}
			spotJsonRight += "];";
  %>
<td valign="top" class="spot-td spot-td-right"><div>Right-side</div>
<div id="spot-image-wrapper-right">
	<img src="<%=spotMAUrl%>" alt="image" id="spot-image-right"  />
	<canvas id="spot-image-canvas-right"></canvas>
</div>
</td> 
      <%
      }

if (enc.getNumSpots() + enc.getNumRightSpots() > 0) {

%>
<script type="text/javascript">

<%
	if (spotJsonRight != null) out.println("spotJson.right = " + spotJsonRight);
	if (spotJsonLeft != null) out.println("spotJson.left = " + spotJsonLeft);
%>

var itool = {};

function spotImageInit() {
	if (spotJson.left) spotInit('left');
	if (spotJson.right) spotInit('right');
}



function spotInit(side) {
	var opts = {
		toolsEnabled: {
			cropRotate: false,
			spotDisplay: true
		},

		spots: spotJson[side],

		imgEl: document.getElementById('spot-image-' + side),

		//wCanvas: document.getElementById('imageTools-workCanvas'),
		//oCanvas: document.getElementById('imageTools-overlayCanvas'),
		lCanvas: document.getElementById('spot-image-canvas-' + side)
	};

	opts.lCanvas.width = opts.imgEl.width;
	opts.lCanvas.height = opts.imgEl.height;

	console.info('initializing itool[%s] with opts %o', side, opts);
	itool[side] = new ImageTools(opts);

	itool[side].setRectFrom2(0,0,0, itool[side].imgEl.width, itool[side].imgEl.height);

	itool[side].scale = opts.lCanvas.width / itool[side].imgEl.naturalWidth;
	itool[side].drawSpots();
}


function spotCheckImage(side) {
	if (!spotJson[side]) return true;
console.log('spotCheckImage(%s) ?', side);
	var jimg = $('#spot-image-' + side);
	if (jimg[0].complete && jimg[0].width) return true;
console.log(side + 'not yet complete!');
	jimg.bind('load', function() { spotCheckImages(); });
	return false;
}


var initStarted = false;
function spotCheckImages() {
	var ready = spotCheckImage('right') && spotCheckImage('left');
console.log('spotCheckImages() got ' + ready);
	if (!ready || initStarted) return;
	initStarted = true;
	spotImageInit();
}

$(document).ready(function() {
	spotCheckImages();
});
</script>
<% } %>

</tr></table>
<!-- END Pattern recognition image pieces -->		
<%

    }

				if(isOwner && ((enc.getNumSpots()>0)||(enc.getNumRightSpots()>0))){ 
				%>
				<br />
  					  <p class="para"><strong><em>Scan for Matches</em></strong></p>
  					<img align="absmiddle" src="../images/Crystal_Clear_app_xmag.png" width="30px" height="30px" /> Scan entire database using the 
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
          						<%
          						if(request.isUserInRole("admin")){
          						%>
          						<tr><i>Optional JDOQL filter: </i> <input name="jdoql" type="text" id="jdoql" size="80"/> </tr>
        						<%
          						}
        						%>
        					</table>

        					<input name="writeThis" type="hidden" id="writeThis" value="true" />
        					<br/> 
        					<input name="scan" type="submit" id="scan" value="Start Scan" onclick="submitForm(document.getElementById('formSharkGrid'))" />
        					<input name="cutoff" type="hidden" value="0.02" />
        				</form>

					</div>
					<%
					}
					
	

}	//end try
catch(Exception e) {
  e.printStackTrace();
}
finally {
  myShepherd.rollbackDBTransaction();
  myShepherd.closeDBTransaction();
}
}
%>
