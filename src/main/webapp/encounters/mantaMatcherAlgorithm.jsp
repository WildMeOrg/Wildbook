<%@ page contentType="text/html; charset=utf-8" language="java" import="org.ecocean.*, org.ecocean.mmutil.*, java.text.*, java.util.*,javax.jdo.*,java.io.File, org.ecocean.servlet.*" %>

<%--
  ~ The Shepherd Project - A Mark-Recapture Framework
  ~ Copyright (C) 2012 Jason Holmberg
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

Shepherd myShepherd = new Shepherd(context);
try {
  //get the encounter number
  String encNum = request.getParameter("encounterNumber");

  //set up the JDO pieces and Shepherd
  myShepherd.beginDBTransaction();
  Encounter enc = myShepherd.getEncounter(encNum);
  boolean hasPhotos=false;
  if (enc.getSinglePhotoVideo() != null && enc.getSinglePhotoVideo().size() > 0) {
    hasPhotos = true;
  }
  boolean isAuthorized = request.isUserInRole("admin") || request.isUserInRole("imageProcessor");

  //let's set up references to our file system components
  File shepherdDataDir = CommonConfiguration.getDataDirectory(getServletContext(), context);
%>

<%
  if (isAuthorized && hasPhotos) {
%>
  <p><strong>Matching Algorithm</strong></p>
<%
    List<SinglePhotoVideo> photos = enc.getSinglePhotoVideo();
    for (int t = 0; t < photos.size(); t++){
      SinglePhotoVideo spv = photos.get(t);
      if (!MediaUtilities.isAcceptableImageFile(spv.getFile()))
        continue;
      Map<String, File> mmFiles = MantaMatcherUtilities.getMatcherFilesMap(spv);
      DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
      File matchOutputRegional = mmFiles.get("TXT-REGIONAL");
      File matchOutputAll = mmFiles.get("TXT");
      File mmFT = mmFiles.get("FT");
      if (MantaMatcherUtilities.checkMatcherFilesExist(spv.getFile())) {
%>
  <p style="background-color:#f0f0f0;"><em>Extracted Feature Image for Image <%=(t+1) %>.</em></p>
  <p><img width="300px" height="*" src="/<%=shepherdDataDir.getName() %>/encounters/<%=Encounter.subdir(enc.getCatalogNumber()) %>/<%=mmFT.getName()%>"/></p>
  <p><em>Remove the processed, cropped manta patterning image.</em></p>
  <p>
    <form action="../EncounterAddMantaPattern" method="post" name="EncounterRemoveMantaPattern">
      <input name="action" type="hidden" value="imageremove" id="actionRemove" />
      <input name="number" type="hidden" value="<%=encNum%>" id="number" />
      <input name="dataCollectionEventID" type="hidden" value="<%=spv.getDataCollectionEventID() %>" id="dataCollectionEventID" />
      <p><input name="removeMMPatternFile" type="submit" id="removeMMPatternFile" value="Remove the file" /></p>
    </form>
  </p>
<%
        if (enc.getLocationID() != null && !matchOutputRegional.exists()) {
%>
  <p>No regional &quot;<%=enc.getLocationID()%>&quot; match results file was found.</p>
  <p><em>Scan patterning image against regional &quot;<%=enc.getLocationID()%>&quot; database.</em></p>
  <p>
    <form action="../EncounterAddMantaPattern" method="post" name="EncounterScanMantaPattern">
      <input name="action" type="hidden" value="rescanRegional" id="actionScanRegional" />
      <input name="number" type="hidden" value="<%=encNum%>" id="number" />
      <input name="dataCollectionEventID" type="hidden" value="<%=spv.getDataCollectionEventID() %>" id="dataCollectionEventID" />
      <p><input name="scanFile" type="submit" id="scanRegionalFile" value="Scan (regional)" /></p>
    </form>
   </p>
<%
        } else if (enc.getLocationID() != null) {
%>
  <p><em>Inspect the algorithm results</em></p>
  <p>A regional &quot;<%=enc.getLocationID()%>&quot; match results file was found: <a href="../MantaMatcher/displayResultsRegional?spv=<%=spv.getDataCollectionEventID() %>" target="_blank">Click here</a> <span class="smallish">(created <%=dateFormat.format(new Date(matchOutputRegional.lastModified()))%>)</span></p>
  <p><em>Rescan manta patterning image against regional &quot;<%=enc.getLocationID()%>&quot; database.</em></p>
  <p>
    <form action="../EncounterAddMantaPattern" method="post" name="EncounterRescanMantaPattern">
      <input name="action" type="hidden" value="rescanRegional" id="actionRescanRegional" />
      <input name="number" type="hidden" value="<%=encNum%>" id="number" />
      <input name="dataCollectionEventID" type="hidden" value="<%=spv.getDataCollectionEventID() %>" id="dataCollectionEventID" />
      <p><input name="rescanFile" type="submit" id="rescanRegionalFile" value="Rescan (regional)" /></p>
    </form>
  </p>
<%
        }
%>
<%
        if (!matchOutputAll.exists()) {
%>
  <p>No global database match results file was found.</p>
  <p><em>Scan patterning image against global manta database.</em></p>
  <p>
    <form action="../EncounterAddMantaPattern" method="post" name="EncounterScanMantaPattern">
      <input name="action" type="hidden" value="rescan" id="actionScan" />
      <input name="number" type="hidden" value="<%=encNum%>" id="number" />
      <input name="dataCollectionEventID" type="hidden" value="<%=spv.getDataCollectionEventID() %>" id="dataCollectionEventID" />
      <p><input name="scanFile" type="submit" id="scanFile" value="Scan (global)" /></p>
    </form>
  </p>
<%
        } else {
%>
  <p><em>Inspect the algorithm results</em></p>
  <p>A global database match results file was found: <a href="../MantaMatcher/displayResults?spv=<%=spv.getDataCollectionEventID() %>" target="_blank">Click here</a> <span class="smallish">(created <%=dateFormat.format(new Date(matchOutputAll.lastModified()))%>)</span></p>
  <p><em>Rescan manta patterning image against global manta database.</em></p>
  <p>
    <form action="../EncounterAddMantaPattern" method="post" name="EncounterRescanMantaPattern">
      <input name="action" type="hidden" value="rescan" id="actionRescan" />
      <input name="number" type="hidden" value="<%=encNum%>" id="number" />
      <input name="dataCollectionEventID" type="hidden" value="<%=spv.getDataCollectionEventID() %>" id="dataCollectionEventID" />
      <p><input name="rescanFile" type="submit" id="rescanFile" value="Rescan (global)" /></p>
    </form>
  </p>
<%
        }
      }
    }
%>
  <div id="dlgScan" title="MantaMatcher algorithm" style="display:none">
  <table>
    <tr>
      <td align="left" valign="top">
        Please wait while the algorithm completes the scan.
      </td>
    </tr>
  </table>
  </div>
  <script>
    var dlgScan = $("#dlgScan").dialog({
      autoOpen: false,
      draggable: false,
      resizable: false,
      modal: true,
      width: 600,
      close: function(event, ui) {
        window.stop();
      }
    });
    $("input#scanRegionalFile").click(function() {
      dlgScan.dialog("open");
    });
    $("input#rescanRegionalFile").click(function() {
      dlgScan.dialog("open");
    });
    $("input#scanFile").click(function() {
      dlgScan.dialog("open");
    });
    $("input#rescanFile").click(function() {
      dlgScan.dialog("open");
    });
  </script>
  <br />
  <p style="background-color:#f0f0f0;"><em>Upload or replace a processed, cropped manta patterning image.</em></p>
  <p>
    <form action="../EncounterAddMantaPattern" method="post" enctype="multipart/form-data" name="EncounterAddMantaPattern">
      <input name="action" type="hidden" value="imageadd" id="actionUpload" />
      <input name="number" type="hidden" value="<%=encNum%>" id="number" />
<%

    // If only one photo uploaded, we already know reference photo.
    if (enc.getSinglePhotoVideo().size() == 1) {
%>
      <input name="photoNumber" type="hidden" value="<%=enc.getImages().get(0).getDataCollectionEventID()%>" id="photoNumber" />
<%
    } else {
    // Otherwise we need to ask user which uploaded photo is to be used for reference.
%>
      <p>Image to upload processed image for: <select name="photoNumber">
<%
      for (int rmi = 0; rmi < enc.getSinglePhotoVideo().size(); rmi++) {
%>
        <option value="<%=enc.getImages().get(rmi).getDataCollectionEventID()%>"><%=(rmi+1)%></option>
<%
      }
%>
      </select><p/>
<%
    }
%>
      <p><strong><img align="absmiddle" src="../images/upload_small.gif"/> Select file:</strong>
      <input name="file2add" type="file" size="20" /></p>
      <p><input name="addtlMMFile" type="submit" id="addtlMMFile" value="Upload" /></p>
    </form></p>
<%
  }
}
catch(Exception e) {
  e.printStackTrace();
}
finally {
  myShepherd.rollbackDBTransaction();
  myShepherd.closeDBTransaction();
}
%>
