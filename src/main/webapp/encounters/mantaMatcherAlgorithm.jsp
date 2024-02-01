<%@ page contentType="text/html; charset=utf-8" language="java" %>
<%@ page import="org.ecocean.*" %>
<%@ page import="org.ecocean.mmutil.*" %>
<%@ page import="java.text.*" %>
<%@ page import="java.util.*" %>
<%@ page import="java.io.File" %>
<%@ page import="java.net.URLEncoder" %>
<%@ page import="org.ecocean.servlet.*" %>

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

  String langCode = ServletUtilities.getLanguageCode(request);
  Properties encprops = ShepherdProperties.getProperties("encounter.properties", langCode, context);

  if (isAuthorized && hasPhotos) {
%>
  <div id="mma" class="section">
    <p class="sectionTitle"><%= encprops.getProperty("mma.sectionTitle") %></p>
<%
    List<SinglePhotoVideo> photos = enc.getSinglePhotoVideo();
    for (int t = 0; t < photos.size(); t++) {
      SinglePhotoVideo spv = photos.get(t);
      if (!MediaUtilities.isAcceptableImageFile(spv.getFile()))
        continue;
      Map<String, File> mmFiles = MantaMatcherUtilities.getMatcherFilesMap(spv);
      File matchOutputRegional = mmFiles.get("TXT-REGIONAL");
      File matchOutputAll = mmFiles.get("TXT");
      File mmFT = mmFiles.get("FT");
      if (!MantaMatcherUtilities.checkMatcherFilesExist(spv.getFile()))
        continue;
%>
    <div class="subSection featureRegion">
      <p class="subSectionTitle featureRegionTitle"><%= MessageFormat.format(encprops.getProperty("mma.featureImage"), t + 1) %></p>

      <%--Start: featureRegionImage--%>
      <div class="subSubSection featureRegionImage">
        <p><img src="/<%=shepherdDataDir.getName() %>/encounters/<%=Encounter.subdir(enc.getCatalogNumber()) %>/<%=URLEncoder.encode(mmFT.getName(), "UTF-8")%>"/></p>
        <div class="formRemoveCR">
          <form action="../EncounterAddMantaPattern" method="post" name="EncounterRemoveMantaPattern">
            <input name="action" type="hidden" value="imageremove" id="actionRemove"/>
            <input name="number" type="hidden" value="<%=encNum%>" id="number"/>
            <input name="dataCollectionEventID" type="hidden" value="<%=spv.getDataCollectionEventID() %>" id="dataCollectionEventID"/>
            <p><input name="removeMMPatternFile" type="submit" id="removeMMPatternFile" value="<%= encprops.getProperty("mma.submit.remove") %>"/></p>
          </form>
        </div>
      </div>
      <%--End: featureRegionImage--%>

      <%--Start: featureRegionResults--%>
      <div class="subSubSection featureRegionResults">
        <p class="subSubSectionTitle"><%= encprops.getProperty("mma.inspectResults") %></p>
<%
        if (enc.getLocationID() != null && !matchOutputRegional.exists()) {
%>
        <div class="resultsRegional">
          <p><%= MessageFormat.format(encprops.getProperty("mma.resultsNotFoundRegional"), enc.getLocationID()) %></p>
          <div class="scanRegionalForm">
            <form action="../EncounterAddMantaPattern" method="post" name="EncounterScanMantaPattern">
              <input name="action" type="hidden" value="rescanRegional" id="actionScanRegional"/>
              <input name="number" type="hidden" value="<%=encNum%>" id="number"/>
              <input name="dataCollectionEventID" type="hidden" value="<%=spv.getDataCollectionEventID() %>" id="dataCollectionEventID"/>
              <p><input name="scanFile" type="submit" id="scanRegionalFile" value="<%= MessageFormat.format(encprops.getProperty("mma.submit.scanRegional"), enc.getLocationID()) %>"/></p>
            </form>
          </div>
        </div>
<%
        } else if (enc.getLocationID() != null) {
%>
        <div class="resultsRegional">
          <p><%= MessageFormat.format(encprops.getProperty("mma.resultsFoundRegional"), enc.getLocationID()) %> <a href="../MantaMatcher/displayResultsRegional?spv=<%=spv.getDataCollectionEventID() %>"><%= MessageFormat.format(encprops.getProperty("mma.resultsLinkRegional"), enc.getLocationID()) %></a></p>
          <p class="smallish"><%= MessageFormat.format(encprops.getProperty("mma.resultsCreated"), new Date(matchOutputRegional.lastModified())) %></p>
          <div class="formRescanRegional">
            <form action="../EncounterAddMantaPattern" method="post" name="EncounterRescanMantaPattern">
              <input name="action" type="hidden" value="rescanRegional" id="actionRescanRegional"/>
              <input name="number" type="hidden" value="<%=encNum%>" id="number"/>
              <input name="dataCollectionEventID" type="hidden" value="<%=spv.getDataCollectionEventID() %>" id="dataCollectionEventID"/>
              <p><input name="rescanFile" type="submit" id="rescanRegionalFile" value="<%= MessageFormat.format(encprops.getProperty("mma.submit.rescanRegional"), enc.getLocationID()) %>"/></p>
            </form>
          </div>
        </div>
<%
        }
        if (!matchOutputAll.exists()) {
%>
        <div class="resultsGlobal">
          <p><%= encprops.getProperty("mma.resultsNotFound") %></p>
          <div class="formScan">
            <form action="../EncounterAddMantaPattern" method="post" name="EncounterScanMantaPattern">
              <input name="action" type="hidden" value="rescan" id="actionScan"/>
              <input name="number" type="hidden" value="<%=encNum%>" id="number"/>
              <input name="dataCollectionEventID" type="hidden" value="<%=spv.getDataCollectionEventID() %>" id="dataCollectionEventID"/>
              <p><input name="scanFile" type="submit" id="scanFile" value="<%= encprops.getProperty("mma.submit.scan") %>"/></p>
            </form>
          </div>
        </div>
<%
        } else {
%>
        <div class="resultsGlobal">
          <p><%= encprops.getProperty("mma.resultsFound")%> <a href="../MantaMatcher/displayResults?spv=<%=spv.getDataCollectionEventID() %>"><%= encprops.getProperty("mma.resultsLink") %></a></p>
          <p class="smallish"><%= MessageFormat.format(encprops.getProperty("mma.resultsCreated"), new Date(matchOutputAll.lastModified())) %></p>
          <div class="formRescan">
            <form action="../EncounterAddMantaPattern" method="post" name="EncounterRescanMantaPattern">
              <input name="action" type="hidden" value="rescan" id="actionRescan"/>
              <input name="number" type="hidden" value="<%=encNum%>" id="number"/>
              <input name="dataCollectionEventID" type="hidden" value="<%=spv.getDataCollectionEventID() %>" id="dataCollectionEventID"/>
              <p><input name="rescanFile" type="submit" id="rescanFile" value="<%= encprops.getProperty("mma.submit.rescan") %>"/></p>
            </form>
          </div>
        </div>
<%
        }
%>
      </div><%--End: featureRegionResults--%>
    </div><%--End: featureRegion--%>
<%
    }
%>

    <div class="subSection featureRegionUpload">
      <p class="subSectionTitle"><%= encprops.getProperty("mma.upload") %></p>
      <div class="formUpload">
        <form action="../EncounterAddMantaPattern" method="post" enctype="multipart/form-data" name="EncounterAddMantaPattern">
          <input name="action" type="hidden" value="imageadd" id="actionUpload"/>
          <input name="number" type="hidden" value="<%=encNum%>" id="number"/>
<%
    if (enc.getSinglePhotoVideo().size() == 1) { // If only one photo uploaded, we already know reference photo.
%>
          <input name="photoNumber" type="hidden" value="<%=enc.getImages().get(0).getDataCollectionEventID()%>" id="photoNumber"/>
<%
    } else { // Otherwise we need to ask user which uploaded photo is to be used for reference.
%>
          <p>
            <%= encprops.getProperty("mma.uploadFor") %>
            <select name="photoNumber">
<%
      for (int rmi = 0; rmi < enc.getSinglePhotoVideo().size(); rmi++) {
%>
              <option value="<%=enc.getImages().get(rmi).getDataCollectionEventID()%>"><%=(rmi+1)%></option>
<%
      }
%>
            </select>
          </p>
<%
    }
%>
          <p>
            <strong><img align="absmiddle" src="../images/upload_small.gif"/> <%= encprops.getProperty("mma.upload.selectFile") %></strong>
            <input name="file2add" type="file" size="20"/>
          </p>
          <p>
            <input name="addtlMMFile" type="submit" id="addtlMMFile" value="<%= encprops.getProperty("mma.submit.upload") %>"/>
          </p>
        </form>
      </div><%--End form--%>
    </div><%--End featureRegionUpload--%>

  </div><%--End section 'mma'--%>

<%--Scan dialog--%>
<div id="dlgScan" title="MantaMatcher algorithm" style="display:none">
  <table>
    <tr>
      <td align="left" valign="top">
        <%= encprops.getProperty("mma.waitForScan") %>
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
<%--END: Scan dialog--%>
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
