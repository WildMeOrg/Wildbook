<%@ page contentType="text/html; charset=utf-8" language="java" import="org.ecocean.servlet.ServletUtilities,
org.ecocean.media.MediaAsset,
org.ecocean.media.Feature,
org.json.JSONArray,
org.json.JSONObject,
java.awt.Dimension,org.ecocean.*, org.ecocean.servlet.*, java.util.*,javax.jdo.*,java.io.File,org.ecocean.neural.TrainNetwork" %>
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
  <h2>Fluke Matching Visualization</h2>



	<!-- Display spot patterning so long as show_spotpatterning is not false in commonCOnfiguration.properties-->

	
  <h3>Extracted Trailing Edges</h3>
  		<%
		
		if (enc.getNumSpots()>0) {
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
                									if (enc.getNumSpots() > 0) {
              										%>
              											<td>
              												<label> <input name="rightSide" type="radio" value="false"> left-side</label>
              											</td>
              										<%
                									}
                									if (enc.getNumRightSpots() > 0) {
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
		
			
			<%
	  		
		} //if use spot pattern reognition
		else{
		%>
		<p>No trailing edge has been added.</p>
		<%	
		}

%>



  	<p>
    <%
	///MediaAsset spotMA = MediaAsset.findOneByLabel(enc.getMedia(), myShepherd, "_annotation");
	ArrayList<MediaAsset> fmas = enc.findAllMediaByFeatureId(myShepherd, new String[]{"org.ecocean.flukeEdge.edgeSpots", "org.ecocean.dorsalEdge.edgeSpots"});
	MediaAsset spotMA = null;
	if (fmas.size() > 0) spotMA = fmas.get(0);

	String fileloc = null;

	if (request.getParameter("isOwner").equals("true") && CommonConfiguration.useSpotPatternRecognition(context) && (spotMA != null)) {
		 	

	//TODO in future we could have multiple spot files but in that case much of this spot junk would have to change
	//  so must therefore use "generic image suite" to show this at some point!!!  ####
        ArrayList<Feature> features = null;
        if (spotMA != null) {
                fileloc = spotMA.webURL().toString();
                features = spotMA.getFeatures();
        }
//System.out.println(spotMA);
//System.out.println(fileloc);

%>


  <table border="0" cellpadding="5"><tr>
  <%
//	String spotJson = null;

	//ArrayList<SuperSpot> spots = new ArrayList<SuperSpot>();

//combine both flukes to one image
  if (fileloc != null) {
%>
<td valign="top" class="spot-td spot-td-left">
<div id="spot-image-wrapper-left">
	<img src="<%=fileloc%>" alt="image" id="spot-image-left" />
	<canvas id="spot-image-canvas-left"></canvas>
</div>
</td> 
<%
    }
%>


<script type="text/javascript">
	var spotJson = {};
<%
	if (features == null) {
		out.println("var features = null;\n");
	} else {
		JSONArray fts = new JSONArray();
		for (Feature f : features) {
			JSONObject fj = new JSONObject();
			fj.put("id", f.getId());
			fj.put("revision", f.getRevision());
			fj.put("type", f.getType().getId());
			fj.put("parameters", f.getParameters());
			fts.put(fj);
		}
		out.println("var features = " + fts.toString());
	}
/*
	if (spotJson != null) out.println("spotJson.left = " + spotJson);

	//currently i set *both* left & right ref spots with same values; but this may change
	if (enc.getLeftReferenceSpots() != null) {
		spotJson = "";
      		spots = enc.getLeftReferenceSpots();
		for (SuperSpot s : spots) {
			spotJson += "{ \"type\": \"ref\", \"xy\" : [ " + s.getCentroidX() + "," + s.getCentroidY() + "] },\n";
		}
		out.println("spotJson.left = spotJson.left.concat([" + spotJson + "]);\n");
	}
*/
%>

var spotJson = {};

//this is a hack/solution to bridge oldschool code with newschool Features
function featuresToSpots() {
	if (!features) return;
	spotJson.left = [];
	spotJson.right = [];
	for (var i = 0 ; i < features.length ; i++) {
		if ((features[i].type.indexOf('referenceSpots') > -1) && features[i].parameters && features[i].parameters.spots) {
			for (var j = 0 ; j < features[i].parameters.spots.length ; j++) {
				//we throw ref spots on the left for some historical reason
				spotJson.left.push({ type: 'ref', xy: [ features[i].parameters.spots[j][0], features[i].parameters.spots[j][1] ] });
			}
		}
			
		if ((features[i].type.indexOf('edgeSpots') > -1) && features[i].parameters) {
			for (var p in features[i].parameters) {
				if (p.indexOf('spots') < 0) continue;
				var side = p.substr(5).toLowerCase();
				if ((side != 'right') && (side != 'left')) continue;
				side = 'left';  //TODO we only are now supporting a single image format... must be generalized!
				for (var j = 0 ; j < features[i].parameters[p].length ; j++) {
					spotJson[side].push({ type: 'spot', xy: [ features[i].parameters[p][j][0], features[i].parameters[p][j][1] ] });
				}
			}
		} 
	}
console.warn(spotJson);
}

var itool = {};

function spotImageInit() {
	if (spotJson.left) spotInit('left');
	//if (spotJson.right) spotInit('right');
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
	//var jimg = $('#spot-image-' + side);
	var jimg = $('#spot-image-' + side);
	if (!jimg || !jimg[0]) return true;
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
	featuresToSpots();
	spotCheckImages();
});
</script>


</tr></table>
<!-- END Pattern recognition image pieces -->	
<h3>Pattern Matching Results</h3>
			<%
  			File scanResults = new File(encounterDir.getAbsolutePath() + "/flukeMatching.json");
  			
	  		if(scanResults.exists()) {
	  		%> 
	  			
	  			<a class="para" href="flukeScanEndApplet.jsp?number=<%=enc.getCatalogNumber() %>">Scan Results</a><br />
	  		<%
	  		}
	  		%>
<h3>Scan for Matches</h3>	
<%

    }

				if(isOwner && ((enc.getNumSpots()>0)||(enc.getNumRightSpots()>0))){
					
					if((enc.getGenus()!=null)&&(enc.getSpecificEpithet()!=null)){
						
							//we also need to check for a classifier file
							File classifierFile=new File(TrainNetwork.getAbsolutePathToClassifier((enc.getGenus()+enc.getSpecificEpithet()),request));
							if(classifierFile.exists()){
								%>
								<br />
				  					 <img align="absmiddle" src="../images/Crystal_Clear_app_xmag.png" width="30px" height="30px" /> Scan entire database.
				  					
				    				<div id="formDiv">
				      					<form name="formSharkGrid" id="formSharkGrid" method="post" action="../ScanTaskHandler">
				      						<input name="action" type="hidden" id="action" value="addTask" /> 
				      						<input name="encounterNumber" type="hidden" value="<%=encNum%>" />
				        						<table width="200px">
				          							
				          						<%
				          						if(request.isUserInRole("admin")){
				          						%>
				          							<tr><i>Optional JDOQL filter: </i> <input name="jdoql" type="text" id="jdoql" size="80"/> </tr>
				        						<%
				          						}
				        						%>
				        					</table>
				
				        					<input name="writeThis" type="hidden" id="writeThis" value="true" />
				        					<input type="hidden" name="rightSide" value="true" checked="checked" />
				            						
				        					<br/> 
				        					<input name="scan" type="submit" id="scan" value="Start Scan" onclick="submitForm(document.getElementById('formSharkGrid'))" />
				        					<input name="cutoff" type="hidden" value="0.02" />
				        				</form>
				
									</div>
									<%
							}
							else{
							%>	
							
								<p>No classifier file can be found for this species. Please contact your Wildbook administrator and request a matching classifier be created to support computer vision and matching for this species.</p>
							
							<%	
							}
						}//end if genus species 
					else{
						%>
						<p>You must set the genus and species for this encounter before you can perform matching to ensure only matches from the correct species appear.</p>
						<%
					}
					
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
