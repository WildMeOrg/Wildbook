


<%@ page contentType="text/html; charset=iso-8859-1" language="java"
         import="org.ecocean.servlet.ServletUtilities,org.dom4j.Document, org.dom4j.Element,org.dom4j.io.SAXReader, org.ecocean.*, org.ecocean.grid.MatchComparator, org.ecocean.grid.MatchObject, org.ecocean.grid.GridManager, org.ecocean.grid.GridManagerFactory, java.io.File, java.util.Arrays, java.util.Iterator, java.util.List,
org.ecocean.grid.ScanTask,
java.util.ArrayList,
org.json.JSONArray,
java.text.MessageFormat,
java.util.Properties,
java.util.Vector" %>
<%@ page import="org.ecocean.shepherd.core.Shepherd" %>
<%@ page import="org.ecocean.shepherd.core.ShepherdProperties" %>

<%

String context="context0";
context=ServletUtilities.getContext(request);

//let's set up references to our file system components
String rootWebappPath = getServletContext().getRealPath("/");
File webappsDir = new File(rootWebappPath).getParentFile();
File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));
File encountersDir=new File(shepherdDataDir.getAbsolutePath()+"/encounters");

  String num="";
  ArrayList<String> locationIDs = new ArrayList<String>();
  boolean scanInProgress = false;
  boolean scanTaskExists = false;
  int numComplete = 0;
  int numTotal = 0;
  long scanTaskStartTime = -1;

  if(request.getParameter("number")!=null){
	Shepherd myShepherd=new Shepherd(context);
	myShepherd.setAction("scanEndApplet.jsp");
	myShepherd.beginDBTransaction();
	if(myShepherd.isEncounter(ServletUtilities.preventCrossSiteScriptingAttacks(request.getParameter("number")))){
  		num = ServletUtilities.preventCrossSiteScriptingAttacks(request.getParameter("number"));
	}

	//get any scantask locationID lists and check scan status
        String taskID = request.getParameter("taskID");
        boolean isRightSide = (request.getParameter("rightSide") != null) && request.getParameter("rightSide").equals("true");
        if (taskID == null) taskID = "scan" + (isRightSide ? "R" : "L") + num;
	if(taskID != null) {
		ScanTask st=myShepherd.getScanTask(taskID);
		if(st!=null){
			scanTaskExists = true;
			if(st.getLocationIDFilters()!=null){
				locationIDs=st.getLocationIDFilters();
			}
			if(!st.hasFinished()){
				scanInProgress = true;
				scanTaskStartTime = st.getStartTime();
				// Check how much work is done via GridManager
				GridManager gm = GridManagerFactory.getGridManager();
				numComplete = gm.getNumWorkItemsCompleteForTask(taskID);
				numTotal = numComplete + gm.getNumWorkItemsIncompleteForTask(taskID);
			}
		}
	}
	myShepherd.rollbackDBTransaction();
	myShepherd.closeDBTransaction();
  }
  String encSubdir = Encounter.subdir(num);

  String sessionId = session.getId();
  boolean xmlOK = false;
  SAXReader xmlReader = new SAXReader();
  File file = new File("foo");
  String scanDate = "";
  String C = "";
  String R = "";
  String epsilon = "";
  String Sizelim = "";
  String maxTriangleRotation = "";
  String side2 = "";

  // Determine the language for i18n
  String langCode = "en";
  if (session.getAttribute("langCode") != null) langCode = (String) session.getAttribute("langCode");
  Properties i18n = ShepherdProperties.getProperties("commonConfigurationLabels.properties", langCode, context);
%>
<jsp:include page="../header.jsp" flush="true"/>

<style type="text/css">

  #tabmenu {
    color: #000;
    border-bottom: 1px solid #CDCDCD;
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

  #tabmenu a, a.active {
    color: #000;
    background: #E6EEEE;

    border: 1px solid #CDCDCD;
    padding: 2px 5px 0px 5px;
    margin: 0;
    text-decoration: none;
    border-bottom: 0px solid #FFFFFF;
  }

  #tabmenu a.active {
    background: #8DBDD8;
    color: #000000;
    border-bottom: 1px solid #8DBDD8;
  }

  #tabmenu a:hover {
    color: #000;
    background: #8DBDD8;
  }

  #tabmenu a:visited {

  }

  #tabmenu a.active:hover {
    color: #000;
    border-bottom: 1px solid #8DBDD8;
  }

  td, th {
    border: 1px solid black;
    padding: 5px;
}

.tr-location-nonlocal {
    opacity: 0.6;
    display: none;
}

.match-side-img-wrapper {
    width: 1000px;
    display: inline-block;
    position: relative;
    cursor: crosshair;
    height: 400px;
}

.match-side-spot {
    width: 9px;
    height: 9px;
    border-radius: 5px;
    background-color: #888;
    position: absolute;
    border: solid 1px black;
    transform: scale(1.5);
}
.match-spot-highlight {
    border-color: yellow;
    transform: scale(3.0);
}

#spot-display {}
.match-side {
    text-align: center;
    display: inline-block;
    position: relative;
    width: 49%;

}
.match-side img {
    position: absolute;
    left: 0;
    top: 0;
    height: 400px;
}
.match-side-info {
    min-height: 9.1em;
    background-color: #DDD;
    padding: 4px 0;
    overflow-wrap: break-word;
}

#match-controls {
    height: 5em;
}
#match-info {
    width: 70%;
    display: inline-block;
}
#match-controls input {
    position: absolute;
    display: none;
}
#match-button-next {
    right: 0px;
}
#match-button-prev {
    left: 0px;
}

.match-side-attribute-label,
.match-side-attribute-value {
    line-height: 1.3em;
    display: inline-block;
    vertical-align: middle;
}
.match-side-attribute-value {
    text-align: left;
    white-space: nowrap;
    overflow: hidden;
    width: 60%;
}
.match-side-attribute-label {
    width: 39%;
    font-weight: bold;
    font-size: 0.8em;
    text-align: right;
    padding-right: 10px;
}

.table-row-highlight {
    background-color: #FF8;
}

.scan-waiting {
    text-align: center;
    padding: 40px 20px;
    margin: 20px 0;
    background-color: #f0f8ff;
    border: 1px solid #b0d4f1;
    border-radius: 8px;
}
.scan-waiting h2 {
    color: #333;
}
.scan-waiting .spinner {
    border: 4px solid #f3f3f3;
    border-top: 4px solid #3498db;
    border-radius: 50%;
    width: 40px;
    height: 40px;
    animation: spin 1s linear infinite;
    margin: 20px auto;
}
@keyframes spin {
    0% { transform: rotate(0deg); }
    100% { transform: rotate(360deg); }
}
.scan-progress {
    margin: 10px 0;
    font-size: 1.1em;
}
.scan-progress-bar {
    width: 300px;
    height: 20px;
    background-color: #e0e0e0;
    border-radius: 10px;
    margin: 10px auto;
    overflow: hidden;
}
.scan-progress-fill {
    height: 100%;
    background-color: #3498db;
    border-radius: 10px;
    transition: width 0.3s ease;
}
</style>

<%
// If scan is in progress, add auto-refresh
if (scanInProgress) {
%>
<meta http-equiv="refresh" content="15" />
<%
}
%>

<div class="container maincontent">

<ul id="tabmenu">
  <li><a
    href="encounter.jsp?number=<%=num%>">Encounter
    <%=num%>
  </a></li>


  <%
    String fileSider = "";
    File finalXMLFile;
    File locationIDXMLFile;
    if ((request.getParameter("rightSide") != null) && (request.getParameter("rightSide").equals("true"))) {
      finalXMLFile = new File(encountersDir.getAbsolutePath()+"/"+ encSubdir + "/lastFullRightI3SScan.xml");
      locationIDXMLFile = new File(encountersDir.getAbsolutePath()+"/"+ encSubdir + "/lastFullRightLocationIDScan.xml");


      side2 = "right";
      fileSider = "&rightSide=true";
    } else {
      finalXMLFile = new File(encountersDir.getAbsolutePath()+"/" + encSubdir + "/lastFullI3SScan.xml");
      locationIDXMLFile = new File(encountersDir.getAbsolutePath()+"/" + encSubdir + "/lastFullLocationIDScan.xml");
    }


    if (locationIDXMLFile.exists()) {
  %>

  <%
    }
    %>

    <li><a class="active">Modified Groth (Full)</a></li>
    <%

    if (finalXMLFile.exists()) {
  %>

  <li><a
    href="i3sScanEndApplet.jsp?writeThis=true&number=<%=num%>&I3S=true<%=fileSider%>">I3S</a>
  </li>
  <%
    }


  %>


</ul>

<%
  // ============================================================
  // SCAN IN PROGRESS: Show waiting message with progress
  // ============================================================
  if (scanInProgress) {
%>

<div class="scan-waiting">
    <div class="spinner"></div>
    <h2><%=MessageFormat.format(i18n.getProperty("scanInProgress.title", "Scan in progress for encounter {0}"), num)%></h2>
    <p><%=i18n.getProperty("scanInProgress.message", "The pattern matching scan is running. Results will appear here when complete.")%></p>

    <%
    if (numTotal > 0) {
        int pct = (int) ((numComplete * 100.0) / numTotal);
    %>
    <div class="scan-progress">
        <%=MessageFormat.format(i18n.getProperty("scanInProgress.progress", "{0} of {1} comparisons complete"), String.valueOf(numComplete), String.valueOf(numTotal))%>
        <div class="scan-progress-bar">
            <div class="scan-progress-fill" style="width: <%=pct%>%"></div>
        </div>
    </div>
    <%
    } else {
    %>
    <p><%=i18n.getProperty("scanInProgress.queuing", "Preparing comparisons...")%></p>
    <%
    }
    %>
    <p style="color: #888; font-size: 0.9em;"><%=i18n.getProperty("scanInProgress.autoRefresh", "This page will automatically refresh every 15 seconds.")%></p>
</div>

<%
  } else {
    // ============================================================
    // SCAN COMPLETE or NO SCAN: Show results
    // ============================================================
%>

<%
  Vector initresults = new Vector();
  Document doc;
  Element root;
  String side = "left";

    try {
      if ((request.getParameter("rightSide") != null) && (request.getParameter("rightSide").equals("true"))) {
        file = new File(encountersDir.getAbsolutePath()+"/" + encSubdir + "/lastFullRightScan.xml");
        side = "right";
      } else {
        file = new File(encountersDir.getAbsolutePath()+"/" + encSubdir + "/lastFullScan.xml");
      }
      doc = xmlReader.read(file);
      root = doc.getRootElement();
      scanDate = root.attributeValue("scanDate");
      xmlOK = true;
      R = root.attributeValue("R");
      C = root.attributeValue("C");
      maxTriangleRotation = root.attributeValue("maxTriangleRotation");
      Sizelim = root.attributeValue("Sizelim");
      epsilon = root.attributeValue("epsilon");
    } catch (Exception ioe) {
      System.out.println("Error accessing the stored scan XML data for encounter: " + num);
      ioe.printStackTrace();
      xmlOK = false;
    }

  MatchObject[] matches = new MatchObject[0];
  if (!xmlOK) {
    int resultsSize = initresults.size();
    System.out.println(resultsSize);
    matches = new MatchObject[resultsSize];
    for (int a = 0; a < resultsSize; a++) {
      matches[a] = (MatchObject) initresults.get(a);
    }
    R = request.getParameter("R");
    C = request.getParameter("C");
    maxTriangleRotation = request.getParameter("maxTriangleRotation");
    Sizelim = request.getParameter("Sizelim");
    epsilon = request.getParameter("epsilon");
  }
%>

<p>

<%
  if (!xmlOK && !scanTaskExists) {
    // No scan has ever been run and no XML exists
%>
<h2><%=i18n.getProperty("scanResults.noResults.title", "No Scan Results")%></h2>
<p><%=i18n.getProperty("scanResults.noResults.message", "No pattern matching scan has been run for this encounter yet.")%></p>
<%
  } else if (!xmlOK && scanTaskExists) {
    // Scan task finished but XML was not written (failed or partial write)
%>
<h2><%=i18n.getProperty("scanResults.failed.title", "Scan Failed")%></h2>
<p><%=i18n.getProperty("scanResults.failed.message", "The scan task completed but results could not be written. Please try running the scan again.")%></p>
<%
  } else if (xmlOK) {
    // We have results to display
%>
<h2>Modified Groth Scan Results</h2>
</p>
<p>The following encounter(s) received the highest
  match values against a <%=side%>-side scan of encounter <a
    href="//<%=CommonConfiguration.getURLLocation(request)%>/encounters/encounter.jsp?number=<%=num%>"><%=num%></a>.</p>


<%
  if (xmlOK) {%>
<p><img src="../images/Crystal_Clear_action_flag.png" width="28px" height="28px" hspace="2" vspace="2" align="absmiddle">&nbsp;<strong>Saved
  scan data may be old and invalid. Check the date below and run a fresh
  scan for the latest results.</strong></p>

<p><em>Date of scan: <%=scanDate%>
</em></p>
<%}%>

<p><a href="#resultstable">See the table below for score breakdowns.</a></p>




<%
    String feedURL = "//" + CommonConfiguration.getURLLocation(request) + "/TrackerFeed?number=" + num;
    String baseURL = "/"+CommonConfiguration.getDataDirectoryName(context)+"/encounters/";

    if (xmlOK) {
      if ((request.getParameter("rightSide") != null) && (request.getParameter("rightSide").equals("true"))) {
        feedURL = baseURL + encSubdir + "/lastFullRightScan.xml?";
      } else {
        feedURL = baseURL + encSubdir + "/lastFullScan.xml?";
      }
    }
    String rightSA = "";
    if ((request.getParameter("rightSide") != null) && (request.getParameter("rightSide").equals("true"))) {
      rightSA = "&filePrefix=extractRight";
    }

java.util.Random rnd = new java.util.Random();
%>
<style>
    .match-side-spot-0 { background-color: #F00; border: dotted 1px #FF4; }
    .match-side-spot-1 { background-color: #0F0; border: dotted 1px #FF4; }
    .match-side-spot-2 { background-color: #00F; border: dotted 1px #FF4; }
<%
for (int i = 3 ; i < 50 ; i++) {
    out.println(".match-side-spot-" + i + " { background-color: rgb(" + rnd.nextInt(256) + "," + rnd.nextInt(256) + "," + rnd.nextInt(256) + "); }");
}
out.println("</style>");
  %>

<script src="../javascript/spotCompare.js"></script>

<script>
//these (must) override spotCompare.js values
localLocationIds = <%=new JSONArray(locationIDs)%>;
subdirPrefix = '/<%=shepherdDataDir.getName()%>/encounters';
rightSide = <%=side2.equals("right")%>;

$(document).ready(function() {
    spotInit(subdirPrefix + '/<%=encSubdir%>/<%=file.getName()%>');
});
</script>

<div id="spot-display">
    <div class="match-side" id="match-side-0">
        <div class="match-side-img-wrapper">
            <img id="match-image-left" onLoad="return matchImgDone(this, 1)" />
        </div>
        <div class="match-side-info"></div>
    </div>
    <div class="match-side" id="match-side-1">
        <div class="match-side-img-wrapper">
            <img id="match-image-right" onLoad="return matchImgDone(this, 0)" />
        </div>
        <div class="match-side-info"></div>
    </div>
    <div id="match-controls">
        <div id="match-info"></div>
        <div style="position: relative; display: inline-block; width: 20%; height: 3em;">
            <input id="match-button-prev" type="button" value="previous" onClick="return spotDisplayButton(-1)" />
            <input id="match-button-next" type="button" value="next" onClick="return  spotDisplayButton(1)" />
        </div>
    </div>
</div>

<script>
// get image and image container widths, compare them and transform to fit

let leftImage = document.getElementById('match-image-left');
$(leftImage).load(function() {
  fitLeftImage();
});

function fitLeftImage() {
  let leftImgContainer = document.getElementById('match-side-0');
  let lRect = leftImage.getBoundingClientRect();
  console.log("current left image width: "+lRect.width);
  console.log("current left container width: "+leftImgContainer.clientWidth);
  if (lRect.width>leftImgContainer.clientWidth) {
    console.log("image WIDTH is out of bounds!");
    let newWidthScale = (leftImgContainer.clientWidth/lRect.width);
    console.log("new scale: "+newWidthScale);
    $(leftImgContainer).find('.match-side-img-wrapper').css("transform-origin", "left bottom");
    $(leftImgContainer).find('.match-side-img-wrapper').css("transform", "scale("+newWidthScale+")");

  }
  if (lRect.height>leftImgContainer.clientHeight) {
    console.log("image HEIGHT is out of bounds!");
    let newHeightScale = (leftImgContainer.clientHeight/lRect.height);
    $(leftImgContainer).find('.match-side-img-wrapper').css("transform", "scale("+newHeightScale+")");
    console.log("new scale: "+newHeightScale);
  }
};

let rightImage = document.getElementById('match-image-right');
$(rightImage).load(function() {
  fitRightImage();
});

function fitRightImage() {
  let rRect = rightImage.getBoundingClientRect();
  let rightImgContainer = document.getElementById('match-side-1');
  console.log("current right image width: "+rRect.width);
  console.log("current right container width: "+rightImgContainer.clientWidth);
  if (rRect.width>rightImgContainer.clientWidth) {
    console.log("image WIDTH is out of bounds!");
    let newWidthScale = (rightImgContainer.clientWidth/rRect.width);
    console.log("new scale: "+newWidthScale);
    $(rightImgContainer).find('.match-side-img-wrapper').css("transform-origin", "left bottom");
    $(rightImgContainer).find('.match-side-img-wrapper').css("transform", "scale("+newWidthScale+")");

  }
  if (rRect.height>rightImgContainer.clientHeight) {
    console.log("image HEIGHT is out of bounds!");
    let newHeightScale = (rightImgContainer.clientHeight/rRect.height);
    $(leftImgContainer).find('.match-side-img-wrapper').css("transform", "scale("+newHeightScale+")");
    console.log("new scale: "+newHeightScale);
  }
};

</script>

<div>
    <div id="mode-message"></div>
    <input type="button" id="mode-button-local" value="Show only nearby matches" onClick="return toggleLocalMode(true);"/>
    <input type="button" id="mode-button-all" value="Show all matches" onClick="return toggleLocalMode(false);"/>
</div>
</p>

      <a name="resultstable"></a>

      <table class="tablesorter" width="800px">
      <thead>
        <tr align="left" valign="top">
          <th><strong>Individual ID</strong></th>
          <th><strong> Encounter</strong></th>
          <th><strong>Fraction Matched Triangles </strong></th>
          <th><strong>Match Score </strong></th>

          <th><strong>logM std. dev.</strong></th>
          <th><strong>Confidence</strong></th>
          <th><strong>Matched Keywords</strong></th>

        </tr>
        </thead>
        <tbody>
        <%
          Shepherd indShepherd = new Shepherd(context);
          indShepherd.setAction("scanEndApplet.jsp_displayNames");
          indShepherd.beginDBTransaction();
          java.util.HashMap<String, String> displayNameCache = new java.util.HashMap<>();

          if (!xmlOK) {

            MatchObject[] results = new MatchObject[1];
            results = matches;
            Arrays.sort(results, new MatchComparator());
            for (int p = 0; p < results.length; p++) {
              if ((results[p].matchValue != 0) || (request.getAttribute("singleComparison") != null)) {
                String indId_mo = results[p].getIndividualName();
                String displayName_mo = displayNameCache.get(indId_mo);
                if (displayName_mo == null && indId_mo != null) {
                    MarkedIndividual ind_mo = indShepherd.getMarkedIndividual(indId_mo);
                    displayName_mo = (ind_mo != null) ? ind_mo.getDisplayName() : indId_mo;
                    displayNameCache.put(indId_mo, displayName_mo);
                }
                if (displayName_mo == null) displayName_mo = "Unknown";
              %>
        <tr>
          <td>
            <a
                  href="//<%=CommonConfiguration.getURLLocation(request)%>/individuals.jsp?number=<%=indId_mo%>"><%=displayName_mo%>
                </a>
          </td>
          <%if (results[p].encounterNumber.equals("N/A")) {%>
          <td>N/A</td>
          <%} else {%>
          <td><a
            href="//<%=CommonConfiguration.getURLLocation(request)%>/encounters/encounter.jsp?number=<%=results[p].encounterNumber%>"><%=results[p].encounterNumber%>
          </a></td>
          <%
            }
            String adjustedMatchValueString = (new Double(results[p].adjustedMatchValue)).toString();
            if (adjustedMatchValueString.length() > 5) {
              adjustedMatchValueString = adjustedMatchValueString.substring(0, 5);
            }
          %>
          <td><%=(adjustedMatchValueString)%><br>
          </td>
          <%
            String finalscore2 = (new Double(results[p].matchValue * results[p].adjustedMatchValue)).toString();

            //trim the length of finalscore
            if (finalscore2.length() > 7) {
              finalscore2 = finalscore2.substring(0, 6);
            }
          %>
          <td><%=finalscore2%>
          </td>

          <td><font size="-2"><%=results[p].getLogMStdDev()%>
          </font></td>
          <td><font size="-2"><%=results[p].getEvaluation()%>
          </font></td>

        </tr>

        <%
              //end if matchValue!=0 loop
            }
            //end for loop
          }

//or use XML output here
        } else {
          doc = xmlReader.read(file);
          root = doc.getRootElement();

          Iterator matchsets = root.elementIterator("match");
            int ct = 0;
          while (matchsets.hasNext()) {
            Element match = (Element) matchsets.next();
            List encounters = match.elements("encounter");
            Element enc1 = (Element) encounters.get(0);
            Element enc2 = (Element) encounters.get(1);
            String indId_xml = enc1.attributeValue("assignedToShark");
            String displayName_xml = displayNameCache.get(indId_xml);
            if (displayName_xml == null && indId_xml != null) {
                MarkedIndividual ind_xml = indShepherd.getMarkedIndividual(indId_xml);
                displayName_xml = (ind_xml != null) ? ind_xml.getDisplayName() : indId_xml;
                displayNameCache.put(indId_xml, displayName_xml);
            }
            if (displayName_xml == null) displayName_xml = "Unknown";
        %>

        <tr id="table-row-<%=ct%>" align="left" valign="top"
class="tr-location-<%=(locationIDs.contains(enc1.attributeValue("locationID")) ? "local" : "nonlocal")%>"
 style="cursor: pointer;" onClick="spotDisplayPair(<%=ct%>);" title="jump to this match pair">
          <td>
            <a target="_new" title="open individual" href="//<%=CommonConfiguration.getURLLocation(request)%>/individuals.jsp?number=<%=indId_xml%>">
            	<%=displayName_xml%>
            </a>
          </td>
          <%if (enc1.attributeValue("number").equals("N/A")) {%>
          <td>N/A</td>
          <%} else {%>
          <td><a target="_new" title="open Encounter"
            href="//<%=CommonConfiguration.getURLLocation(request)%>/encounters/encounter.jsp?number=<%=enc1.attributeValue("number")%>">Link
          </a></td>
          <%
            }
            String adjustedpoints = "No Adj.";
            adjustedpoints = match.attributeValue("adjustedpoints");
            if (adjustedpoints == null) {
              adjustedpoints = "&nbsp;";
            }
            if (adjustedpoints.length() > 5) {
              adjustedpoints = adjustedpoints.substring(0, 5);
            } else {
              adjustedpoints = adjustedpoints + "<br>";
            }
          %>
          <td><%=adjustedpoints%>
          </td>
          <%
            String finalscore = "&nbsp;";
            try {
              if (match.attributeValue("finalscore") != null) {
                finalscore = match.attributeValue("finalscore");
              }
            } catch (NullPointerException npe) {
            }

            //trim the length of finalscore
            if (finalscore.length() > 7) {
              finalscore = finalscore.substring(0, 6);
            }

          %>
          <td><%=finalscore%>
          </td>


          <td><font size="-2"><%=match.attributeValue("logMStdDev")%>
          </font></td>
          <%
            String evaluation = "No Adj.";
            evaluation = match.attributeValue("evaluation");
            if (evaluation == null) {
              evaluation = "&nbsp;";
            }

          %>
          <td><font size="-2"><%=evaluation%>
          </font></td>
          <td>
            <%
              String keywords = "";
              for (Iterator i = match.elementIterator("keywords"); i.hasNext();) {
                Element kws = (Element) i.next();
                // iterate the keywords themselves
                for (Iterator j = kws.elementIterator("keyword"); j.hasNext();) {
                  Element kws2 = (Element) j.next();
                  keywords = keywords + "<li>" + kws2.attributeValue("name") + "</li>";
                }
              }
              if (keywords.length() <= 1) {
                keywords = "&nbsp;";
              }
            %> <font size="-2">
            <ul><%=keywords%>
            </ul>
          </font></td>
        </tr>

        <%


        ct++;
            }
          }

        %>
</tbody>
      </table>



  <%
          indShepherd.rollbackDBTransaction();
          indShepherd.closeDBTransaction();



	//myShepherd.closeDBTransaction();
    //myShepherd = null;
    doc = null;
    root = null;
    initresults = null;
    file = null;
    xmlReader = null;

if ((request.getParameter("epsilon") != null) && (request.getParameter("R") != null)) {%>
      <p><font size="+1">Custom Scan</font></p>
      <%} else {%>
      <p><font size="+1">Standard Scan</font></p>
      <%}%>
      <p>For this scan, the following variables were used:</p>
      <ul>


        <li>epsilon (<%=epsilon%>)</li>
        <li>R (<%=R%>)</li>
        <li>Sizelim (<%=Sizelim%>)</li>
        <li>C (<%=C%>)</li>
        <li>Max. Triangle Rotation (<%=maxTriangleRotation%>)</li>

      </ul>
<%
  } // end if xmlOK
%>
<br />
<%
  } // end else (not scanInProgress)
%>
</div>
<jsp:include page="../footer.jsp" flush="true"/>
