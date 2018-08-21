<%@ page contentType="text/html; charset=utf-8" language="java" %>
<%@ page import="org.ecocean.*" %>
<%@ page import="org.ecocean.mmutil.*" %>
<%@ page import="java.text.*" %>
<%@ page import="java.util.*" %>
<%@ page import="java.io.File, org.ecocean.media.*" %>
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

<style type="text/css">
  #mma .sectionTitle {
    font-weight: bold;
  }
  #mma .subSection {
    clear: both;
    font-size: 0.9em;
  }
  #mma .subSectionTitle {
    background-color: #f0f0f0;
    padding: 0.5em;
    font-style: italic;
  }
  #mma .subSubSection {
  }
  #mma .subSubSectionTitle {
    background-color: #f0f0f0;
    padding: 0.5em;
    font-style: italic;
  }
  #mma .featureRegion {
    width: 100%;
  }
  #mma .featureRegionImage {
    float: left;
    width: 380px;
    padding: 0 10px 0 0;
  }
  #mma .featureRegionImage img {
    max-width: 100%;
    height: auto;
  }
  #mma .featureRegionResults {
    float: right;
    width: 410px;
    padding: 0 0 0 10px;
  }
  #mma .featureRegionResults p {
    margin: 0;
  }
  #mma .mmaResults {
    margin-top: 0.5em;
    padding: 0.5em 1em 1em 1em;
    background-color: #f0f0f0
  }
  #mma .mmaResultDetailsTable td {
    padding: 0.15em;
  }
  #mma .mmaResultLink a {
    font-weight: bolder;
  }
  #mma .mmaResultDate {
    white-space: nowrap;
  }
  #mma .mmaResultsLocation {
    font-weight: bolder;
  }
  .newScanCheckboxTable td {
    vertical-align: top;
  }
  button.smaller, input[type="button"].smaller, input[type="submit"].smaller {
    font-size: 80%;
    padding: 3px 7px;
  }
</style>

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
  if ((enc.getAnnotations()!=null)&&(enc.getAnnotations().size()>0)) {
    hasPhotos = true;
  }
  boolean isAuthorized = request.isUserInRole("admin") || request.isUserInRole("imageProcessor");

  //let's set up references to our file system components
  File shepherdDataDir = CommonConfiguration.getDataDirectory(getServletContext(), context);

  String langCode = ServletUtilities.getLanguageCode(request);
  Properties encprops = ShepherdProperties.getProperties("encounter.properties", langCode, context);

  if (isAuthorized && hasPhotos) {
%>
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
  $("input#scanFile").click(function() {
    dlgNewScan.dialog("close");
    dlgScan.dialog("open");
  });
  $("input#rescanFile").click(function() {
    dlgScan.dialog("open");
  });
</script>
<%--END: Scan dialog--%>
  <div id="mma" class="section">
    <p class="sectionTitle"><%= encprops.getProperty("mma.sectionTitle") %></p>
<%
    List<MediaAsset> photos = enc.getMedia();
    for (int t = 0; t < photos.size(); t++) {
      MediaAsset ma=photos.get(t);
      SinglePhotoVideo spv = new SinglePhotoVideo(encNum,ma.getFilename(),(enc.subdir())+File.separator+ma.getFilename());
      String spvKey = String.format("spv%d", t);
      if (!MediaUtilities.isAcceptableImageFile(spv.getFile()))
        continue;
      if (!MantaMatcherUtilities.checkMatcherFilesExist(spv.getFile()))
        continue;
      Map<String, File> mmFiles = MantaMatcherUtilities.getMatcherFilesMap(spv);
      File mmFT = mmFiles.get("FT");
      Set<MantaMatcherScan> mmaScans = MantaMatcherUtilities.loadMantaMatcherScans(context, spv);
%>
    <div class="subSection featureRegion">
      <p class="subSectionTitle featureRegionTitle"><%= MessageFormat.format(encprops.getProperty("mma.featureImage"), t + 1) %></p>

      <%--Start: featureRegionImage--%>
      <div class="subSubSection featureRegionImage">
        <p><img src="/<%=shepherdDataDir.getName() %>/encounters/<%=Encounter.subdir(enc.getCatalogNumber()) %>/<%=URLEncoder.encode(mmFT.getName(), "UTF-8")%>"/></p>
        <div class="formRemoveCR">
          <form action="../EncounterAddMantaPattern" method="post" name="EncounterRemoveMantaPattern">
            <input name="action" type="hidden" value="imageremove" id="actionRemove-remove"/>
            <input name="number" type="hidden" value="<%=encNum%>" id="number-remove"/>
            <input name="dataCollectionEventID" type="hidden" value="<%=spv.getDataCollectionEventID() %>" id="dataCollectionEventID-remove"/>
            <p><input name="removeMMPatternFile" type="submit" id="removeMMPatternFile" value="<%= encprops.getProperty("mma.submit.remove") %>"/></p>
          </form>
        </div>
      </div>
      <%--End: featureRegionImage--%>

      <%--Start: featureRegionResults--%>
      <div class="subSubSection featureRegionResults">
        <%--<p class="subSubSectionTitle"><%= encprops.getProperty("mma.inspectResults") %></p>--%>
<%
        if (enc.getLocationID() != null && mmaScans.isEmpty()) {
%>
        <div class="mmaResults">
          <p><%= encprops.getProperty("mma.resultsNotFound") %></p>
        </div>
<%
        } else if (enc.getLocationID() != null) {
          Set<String> allLocationIDs = new HashSet<String>(CommonConfiguration.getIndexedPropertyValues("locationID", context));
          // Loop over each MMA scan...
          for (MantaMatcherScan mmaScan : mmaScans) {
            String dispLocIDs = mmaScan.getLocationIdString("<span class=\"mmaResultsLocation\">", "</span>", ", ");
            if (mmaScan.getLocationIds().equals(allLocationIDs)) {
              dispLocIDs = "<span class=\"mmaResultsLocation\">" + encprops.getProperty("mma.location.global") + "</span>";
            }
            String resultsURL = MantaMatcherUtilities.createMantaMatcherResultsLink(request, spv, mmaScan.getId());
%>
        <div class="mmaResults">
          <table class="mmaResultDetailsTable">
            <tbody>
            <tr>
              <td class="mmaResultLocationsText"><%= encprops.getProperty("mma.resultsFound") %></td>
              <td class="mmaResultLocations"><%= dispLocIDs %></td>
            </tr>
            <tr>
              <td class="mmaResultLink"><a href="<%=resultsURL%>"><%= encprops.getProperty("mma.resultsLink") %></a></td>
              <td class="mmaResultDate"><%= MessageFormat.format(encprops.getProperty("mma.resultsCreated"), mmaScan.getDateTime()) %></td>
            </tr>
            </tbody>
          </table>
          <div class="formRescan">
            <table class="rescanFormsTable">
              <tbody>
              <tr>
                <td>
                  <form action="../EncounterAddMantaPattern" method="post">
                    <input name="action" type="hidden" value="removeScan"/>
                    <input name="number" type="hidden" value="<%=encNum%>"/>
                    <input name="dataCollectionEventID" type="hidden" value="<%=mmaScan.getDataCollectionEventId()%>"/>
                    <input name="scanId" type="hidden" value="<%=mmaScan.getId()%>"/>
                    <input name="removeScanFile" type="submit" class="smaller" value="<%= encprops.getProperty("mma.submit.removeScan") %>"/>
                  </form>
                </td>
                <td>
                  <form action="../EncounterAddMantaPattern" method="post">
                    <input name="action" type="hidden" value="rescan"/>
                    <input name="number" type="hidden" value="<%=encNum%>"/>
                    <input name="dataCollectionEventID" type="hidden" value="<%=mmaScan.getDataCollectionEventId()%>"/>
<%
  for (String loc : mmaScan.getLocationIds()) {
%>
                    <input name="locationID" type="hidden" value="<%=loc%>"/>
<%
  }
%>
                    <input name="scanId" type="hidden" value="<%=mmaScan.getId()%>"/>
                    <input name="rescanFile" type="submit" id="rescanFile-<%=spvKey%>" class="smaller" value="<%= encprops.getProperty("mma.submit.rescan") %>"/>
                  </form>
                </td>
              </tr>
              </tbody>
            </table>
          </div>
        </div>
<%
          }
        }
%>
        <div class="mmaResults">
          <form id="formNewScan-<%=spvKey%>">
<%  if (MantaMatcherUtilities.checkMatcherFilesUsable(spv.getFile())) { %>
            <button id="buttonNewScan-<%=spvKey%>" class="smaller" title="<%= encprops.getProperty("mma.button.newScan.title") %>"><%= encprops.getProperty("mma.button.newScan") %></button>
<%  } else { %>
            <button disabled id="buttonNewScan-<%=spvKey%>" class="smaller" title="<%= encprops.getProperty("mma.button.newScan.disabled.title") %>"><%= encprops.getProperty("mma.button.newScan.disabled") %></button>
<%  } %>
          </form>
        </div>
        <!-- New Scan popup dialog -->
        <div id="dialogNewScan-<%=spvKey%>" title="<%=encprops.getProperty("mma.dialogTitle.selectLocations")%>" style="display:none">
          <table border="1" cellpadding="1" cellspacing="0" bordercolor="#FFFFFF">
            <tbody>
            <tr>
              <td align="left" valign="top">
                <div class="scanForm">
                  <form id="scanForm-<%=spvKey%>" action="../EncounterAddMantaPattern" method="post" name="EncounterScanMantaPattern">
                    <table class="newScanCheckboxTable">
                      <tbody>
                      <tr>
                        <td>
<%
          Map<String, String> allLocationsMap = CommonConfiguration.getIndexedValuesMap("locationID", context);
          int colCount = 1;
          int maxPerCol = 12;
          while ((allLocationsMap.size() / colCount) > maxPerCol)
            colCount++;
          int itemCounter = 0;
          for (Map.Entry<String, String> me : allLocationsMap.entrySet()) {
            if (itemCounter > 0 && (itemCounter % maxPerCol) == 0) {
%>
                        </td>
                        <td>
<%
            }
            itemCounter++;
            if (me.getValue().equals(enc.getLocationID())) {
%>
                        <input name="locationID" type="checkbox" value="<%=me.getValue()%>" class="encLocation" checked="checked"/>&nbsp;<%=me.getValue()%><br/>
<%
            } else {
%>
                        <input name="locationID" type="checkbox" value="<%=me.getValue()%>"/>&nbsp;<%=me.getValue()%><br/>
<%
            }
          }
%>
                        </td>
                      </tr>
                      </tbody>
                    </table>
                    <button id="selectAll-<%=spvKey%>" class="smaller"><%= encprops.getProperty("mma.button.selectAll") %></button>
                    <button id="selectNone-<%=spvKey%>" class="smaller"><%= MessageFormat.format(encprops.getProperty("mma.button.selectNone"), enc.getLocationID()) %></button>
                    <input name="action" type="hidden" value="rescan"/>
                    <input name="number" type="hidden" value="<%=encNum%>"/>
                    <input name="dataCollectionEventID" type="hidden" value="<%=spv.getDataCollectionEventID() %>"/>
                    <p><input type="submit" id="scanFile-<%=spvKey%>" value="<%= encprops.getProperty("mma.submit.scan") %>"/></p>
                  </form>
                </div>
              </td>
            </tr>
            </tbody>
          </table>
        </div>
        <script>
          var dlgNewScan_<%=spvKey%> = $("div#dialogNewScan-<%=spvKey%>").dialog({
            autoOpen: false,
            draggable: false,
            resizable: false,
            width: 600
          });
          $("button#buttonNewScan-<%=spvKey%>").click(function(e) {
            e.preventDefault();
            dlgNewScan_<%=spvKey%>.dialog("open");
          });
          // Define checkbox selections.
          $('button#selectAll-<%=spvKey%>').click(function(e) {
            e.preventDefault();
            $('form#scanForm-<%=spvKey%> input[name="locationID"]').prop('checked', 'checked');
          });
          $('button#selectNone-<%=spvKey%>').click(function(e) {
            e.preventDefault();
            $('form#scanForm-<%=spvKey%> input[name="locationID"]').prop('checked', false);
            $('form#scanForm-<%=spvKey%> input[name="locationID"].encLocation').prop('checked', 'checked');
          });
          // Configure generic scanning popup based on this scan.
          $("input#scanFile-<%=spvKey%>").click(function() {
            dlgNewScan_<%=spvKey%>.dialog("close");
            dlgScan.dialog("open");
          });
          $("input#rescanFile-<%=spvKey%>").click(function() {
            dlgScan.dialog("open");
          });
          </script>
        <!-- End: New Scan popup dialog -->

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
