

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=iso-8859-1" language="java"
         import="org.ecocean.servlet.ServletUtilities,org.dom4j.Document, org.dom4j.Element, org.dom4j.io.SAXReader, org.ecocean.*, org.ecocean.grid.I3SMatchComparator, org.ecocean.grid.I3SMatchObject, java.io.File, java.util.Arrays,
java.util.ArrayList,
org.json.JSONArray,
java.util.Iterator, java.util.List, java.util.Vector" %>

<%

String context="context0";
context=ServletUtilities.getContext(request);

  //session.setMaxInactiveInterval(6000);
  String num="";
    ArrayList<String> locationIDs = new ArrayList<String>();
  if(request.getParameter("number")!=null){
	Shepherd myShepherd=new Shepherd(context);
	myShepherd.setAction("scanEndApplet.jsp");
	myShepherd.beginDBTransaction();
	if(myShepherd.isEncounter(ServletUtilities.preventCrossSiteScriptingAttacks(request.getParameter("number")))){
  		num = ServletUtilities.preventCrossSiteScriptingAttacks(request.getParameter("number"));
	}
	myShepherd.rollbackDBTransaction();
	myShepherd.closeDBTransaction();
  }
  String encSubdir = Encounter.subdir(num);
  //Shepherd myShepherd = new Shepherd(context);
  //if (request.getParameter("writeThis") == null) {
  //  myShepherd = (Shepherd) session.getAttribute(request.getParameter("number"));
  //}
  //Shepherd altShepherd = new Shepherd(context);
  String sessionId = session.getId();
  boolean xmlOK = false;
  SAXReader xmlReader = new SAXReader();
  File file = new File("foo");
  String scanDate = "";
  String side2 = "";

  //setup data dir
  String rootWebappPath = getServletContext().getRealPath("/");
  File webappsDir = new File(rootWebappPath).getParentFile();
  File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));
  //if(!shepherdDataDir.exists()){shepherdDataDir.mkdirs();}
  File encountersDir=new File(shepherdDataDir.getAbsolutePath()+"/encounters");
  //if(!encountersDir.exists()){encountersDir.mkdirs();}
	//String encSubdir = Encounter.subdir(num);
  //File thisEncounterDir = new File(encountersDir, encSubdir);   //never used??

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


.tr-location-nonlocal {
    opacity: 0.6;
    display: none;
}

.match-side-img-wrapper {
    width: 1000px;
    display: inline-block;
    position: relative;
    height: 400px;
    cursor: crosshair;
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
    height: 9.1em;
    background-color: #DDD;
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
</style>

<style>
td, th {
    border: 1px solid black;
    padding: 5px;
}

</style>


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
      finalXMLFile = new File(encountersDir.getAbsolutePath()+"/" + encSubdir + "/lastFullRightScan.xml");
      locationIDXMLFile = new File(encountersDir.getAbsolutePath()+"/" + encSubdir + "/lastFullRightLocationIDScan.xml");

      side2 = "right";
      fileSider = "&rightSide=true";
    } 
    else {
      finalXMLFile = new File(encountersDir.getAbsolutePath()+"/" + encSubdir + "/lastFullScan.xml");
      locationIDXMLFile = new File(encountersDir.getAbsolutePath()+"/" + encSubdir + "/lastFullLocationIDScan.xml");
    }

    if (finalXMLFile.exists()) {
	  %>
	  <li><a
	    href="scanEndApplet.jsp?writeThis=true&number=<%=num%><%=fileSider%>">Modified
	    Groth (Full)</a></li>
	
	  <%
    }
  %>
  <li><a class="active">I3S</a></li>


</ul>

<%
  Vector initresults = new Vector();
  Document doc;
  Element root;
  String side = "left";

  if (request.getParameter("writeThis") == null) {
    //initresults=myShepherd.matches;
    if ((request.getParameter("rightSide") != null) && (request.getParameter("rightSide").equals("true"))) {
      side = "right";
    }
  } else {

//read from the written XML here if flagged
    try {
      if ((request.getParameter("rightSide") != null) && (request.getParameter("rightSide").equals("true"))) {
        //file=new File((new File(".")).getCanonicalPath()+File.separator+"webapps"+File.separator+"ROOT"+File.separator+"encounters"+File.separator+num+File.separator+"lastFullRightI3SScan.xml");
        file = new File(encountersDir.getAbsolutePath()+"/" + encSubdir + "/lastFullRightI3SScan.xml");

        side = "right";
      } 
      else {
        //file=new File((new File(".")).getCanonicalPath()+File.separator+"webapps"+File.separator+"ROOT"+File.separator+"encounters"+File.separator+num+File.separator+"lastFullI3SScan.xml");
        file = new File(encountersDir.getAbsolutePath()+"/" + encSubdir + "/lastFullI3SScan.xml");
      }
      doc = xmlReader.read(file);
      root = doc.getRootElement();
      scanDate = root.attributeValue("scanDate");
      xmlOK = true;
    } 
	catch (Exception ioe) {
      System.out.println("Error accessing the stored scan XML data for encounter: " + num);
      ioe.printStackTrace();
      //initresults=myShepherd.matches;
      xmlOK = false;
    }

  }
  I3SMatchObject[] matches = new I3SMatchObject[0];
  if (!xmlOK) {
    int resultsSize = initresults.size();
    System.out.println(resultsSize);
    matches = new I3SMatchObject[resultsSize];
    for (int a = 0; a < resultsSize; a++) {
      matches[a] = (I3SMatchObject) initresults.get(a);
    }

  }
%>

<p>

<h2>I3S Scan Results</h2>
</p>
<p>The following encounter(s) received the best
  match values using the I3S algorithm against a <%=side%>-side scan of
  encounter <a href="encounter.jsp?number=<%=num%>"><%=num%></a>.</p>


<%
  if (xmlOK) {%>
<p><img src="../images/Crystal_Clear_action_flag.png" width="28px" height="28px" hspace="2" vspace="2" align="absmiddle"><strong>&nbsp;Saved
  scan data may be old and invalid. Check the date below and run a fresh
  scan for the latest results.</strong></p>

<p><em>Date of scan: <%=scanDate%>
</em></p>

<%}%>

<p><a href="#resultstable">See the table below for score breakdowns.</a></p>
		  <%


		    String feedURL = "//" + CommonConfiguration.getURLLocation(request) + "/TrackerFeed?number=" + num;
		    String baseURL = "/"+CommonConfiguration.getDataDirectoryName(context)+"/encounters/";
		    //System.out.println("Base URL is: " + baseURL);
		    if (xmlOK) {
		      if ((request.getParameter("rightSide") != null) && (request.getParameter("rightSide").equals("true"))) {
		        feedURL = baseURL + encSubdir + "/lastFullRightI3SScan.xml?";
		      } else {
		        feedURL = baseURL + encSubdir + "/lastFullI3SScan.xml?";
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
            <img onLoad="return matchImgDone(this, 1)" />
        </div>
        <div class="match-side-info"></div>
    </div>
    <div class="match-side" id="match-side-1">
        <div class="match-side-img-wrapper">
            <img onLoad="return matchImgDone(this, 0)" />
        </div>
        <div class="match-side-info"></div>
    </div>
    <div id="match-controls">
        <div id="match-info"></div>
        <div style="position: relative; display: inline-block; width: 20%; height: 3em;">
            <input id="match-button-prev" type="button" value="previous" onClick="return spotDisplayButton(-1)" />
            <input id="match-button-next" type="button" value="next" onClick="return spotDisplayButton(1)" />
        </div>
    </div>
</div>

<div>
    <div id="mode-message"></div>
    <input type="button" id="mode-button-local" value="Show only nearby matches" onClick="return toggleLocalMode(true);"/>
    <input type="button" id="mode-button-all" value="Show all matches" onClick="return toggleLocalMode(false);"/>
</div>
</p>

<a name="resultstable"></a>
<table class="tablesorter">

<table width="800px">
  <thead>

        <tr align="left" valign="top">
          <th><strong>Shark</strong></th>
          <th><strong> Encounter</strong></th>
          <th><strong>Match Score </strong></th>


    </tr>
        </thead>
        <tbody>
        <%
          if (!xmlOK) {

            I3SMatchObject[] results = new I3SMatchObject[1];
            results = matches;
            Arrays.sort(results, new I3SMatchComparator());
            for (int p = 0; p < results.length; p++) {
              if ((results[p].matchValue != 0) || (request.getAttribute("singleComparison") != null)) {%>
        <tr align="left" valign="top">

                <td width="60" align="left">
                <%
                  String localIndividualName = results[p].getIndividualName();
                  if (Util.isUUID(localIndividualName)) {
                    Shepherd nameShepherd=new Shepherd(context);
                    nameShepherd.setAction("i3ScanEndApplet.jsp displayName render");
                    nameShepherd.beginDBTransaction();
                    try{
                    	if(nameShepherd.getMarkedIndividual(localIndividualName)!=null){
                    		localIndividualName = nameShepherd.getMarkedIndividual(localIndividualName).getDisplayName();
                    	}
                    }
                    catch(Exception e){
                      System.out.println("Error retrieving display name in the case where xml is not OK for individual UUID: "+localIndividualName);
                      e.printStackTrace();
                    } 
                    finally{
                      nameShepherd.rollbackDBTransaction();
                    	nameShepherd.closeDBTransaction();
                      nameShepherd=null;
                    }
                  }
                %>
                <a href="//<%=CommonConfiguration.getURLLocation(request)%>/individuals.jsp?number=<%=results[p].getIndividualName()%>"><%=localIndividualName%>
                </a></td>

          <%if (results[p].encounterNumber.equals("N/A")) {%>
          <td>N/A</td>
          <%} else {%>
          <td><a
            href="//<%=CommonConfiguration.getURLLocation(request)%>/encounters/encounter.jsp?number=<%=results[p].encounterNumber%>">Link
          </a></td>
          <%
            }
            String finalscore2 = (new Double(results[p].matchValue)).toString();

            //trim the length of finalscore
            if (finalscore2.length() > 7) {
              finalscore2 = finalscore2.substring(0, 6);
            }
          %>
          <td><%=finalscore2%>
          </td>


        </tr>

        <%
              //end if matchValue!=0 loop
            }
            //end for loop
          }

//or use XML output here
        } 
          else {
          doc = xmlReader.read(file);
          root = doc.getRootElement();

          Iterator matchsets = root.elementIterator("match");
            int ct = 0;
          while (matchsets.hasNext()) {
            Element match = (Element) matchsets.next();
            List encounters = match.elements("encounter");
            Element enc1 = (Element) encounters.get(0);
            Element enc2 = (Element) encounters.get(1);
            String enc1IndId = enc1.attributeValue("assignedToShark");
            String localIndividualName = enc1IndId;
            if(enc1IndId != null && !enc1IndId.equals("")){
              Shepherd nameShepherd=new Shepherd(context);
              nameShepherd.setAction("i3ScanEndApplet.jsp displayName render 2");
              nameShepherd.beginDBTransaction();
              try{
            	MarkedIndividual localIndy=nameShepherd.getMarkedIndividual(enc1IndId); 
                if(localIndy!=null)localIndividualName = localIndy.getDisplayName();  
              }
              catch(Exception e){
                System.out.println("Error retrieving local display name in the case where xml is OK");
                e.printStackTrace();
              }
              finally{
                nameShepherd.rollbackDBTransaction();
                nameShepherd.closeDBTransaction();
                nameShepherd=null;
              }
            }
        %>
        <tr id="table-row-<%=ct%>" align="left" valign="top"
class="tr-location-<%=(locationIDs.contains(enc1.attributeValue("locationID")) ? "local" : "nonlocal")%>"
 style="cursor: pointer;" onClick="spotDisplayPair(<%=ct%>);" title="jump to this match pair">

                <td width="60" align="left">
            <a target="_new" title="open individual" href="//<%=CommonConfiguration.getURLLocation(request)%>/individuals.jsp?number=<%=enc1.attributeValue("assignedToShark")%>">
            	<%=localIndividualName%>
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


          <%
            String evaluation = "No Adj.";
            evaluation = match.attributeValue("evaluation");
            if (evaluation == null) {
              evaluation = "&nbsp;";
            }

          %>

        </tr>

        <%


        ct++;
            }


          }

        %>


</tbody>
</table>


<p>

<p>
  <%


//myShepherd.rollbackDBTransaction();
   //myShepherd = null;
    doc = null;
    root = null;
    initresults = null;
    file = null;
    xmlReader = null;



%>
<br />
</div>
<jsp:include page="../footer.jsp" flush="true"/>
