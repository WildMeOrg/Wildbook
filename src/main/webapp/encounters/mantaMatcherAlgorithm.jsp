<%@ page contentType="text/html; charset=utf-8" language="java" import="org.ecocean.*, org.ecocean.util.*, java.util.*,javax.jdo.*,java.io.File" %>

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
Shepherd myShepherd = new Shepherd();
try {
  //get the encounter number
  String encNum = request.getParameter("encounterNumber");

  //set up the JDO pieces and Shepherd
  myShepherd.beginDBTransaction();
  Encounter enc=myShepherd.getEncounter(encNum);
  boolean hasPhotos=false;
  if (enc.getSinglePhotoVideo() != null && enc.getSinglePhotoVideo().size() > 0) {
    hasPhotos=true;
  }

  //let's set up references to our file system components
  String rootWebappPath = getServletContext().getRealPath("/");
  File webappsDir = new File(rootWebappPath).getParentFile();
  File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName());

%>
  <p><strong>Matching Algorithm</strong></p>
<%
  if (hasPhotos) {

    List<SinglePhotoVideo> photos = enc.getSinglePhotoVideo();
    for(int t = 0; t < photos.size(); t++){
      SinglePhotoVideo spv = photos.get(t);
      Map<String, File> mmFiles = MantaMatcherUtilities.getMatcherFilesMap(spv);
      File matchOutput = mmFiles.get("TXT");
      File mmCR = mmFiles.get("CR");
      if (mmCR.exists()) {
        File mmFT = mmFiles.get("FT");
        if (mmFT.exists() && (request.isUserInRole("admin") || request.isUserInRole("imageProcessor"))) {
%>
          <p style="background-color:#f0f0f0;"><em>Extracted Feature Image for Image <%=(t+1) %>.</em></p>
            <p><img width="300px" height="*" src="/<%=shepherdDataDir.getName() %>/encounters/<%=encNum %>/<%=mmFT.getName()%>"/></p>
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
            if (!matchOutput.exists()) {
%>
              <p>No match results file was found.</p>
                <p><em>Scan the manta patterning image.</em></p>
                       <p>
                         <form action="../EncounterAddMantaPattern" method="post" name="EncounterScanMantaPattern">
                          <input name="action" type="hidden" value="rescan" id="actionScan" />
                          <input name="number" type="hidden" value="<%=encNum%>" id="number" />
                          <input name="dataCollectionEventID" type="hidden" value="<%=spv.getDataCollectionEventID() %>" id="dataCollectionEventID" />

                        <p><input name="scanFile" type="submit" id="scanFile" value="Scan" /></p>
                    </form>
                   </p>
<%
            } else {
%>
              <p><em>Inspect the algorithm results</em></p>
              <p>A match results file was found: <a href="../MantaMatcher/displayResults?spv=<%=spv.getDataCollectionEventID() %>" target="_blank">Click here.</a></p>

              <p><em>Rescan the manta patterning image.</em></p>
                       <p>
                         <form action="../EncounterAddMantaPattern" method="post" name="EncounterRescanMantaPattern">
                           <input name="action" type="hidden" value="rescan" id="actionRescan" />
                           <input name="number" type="hidden" value="<%=encNum%>" id="number" />
                           <input name="dataCollectionEventID" type="hidden" value="<%=spv.getDataCollectionEventID() %>" id="dataCollectionEventID" />

                        <p><input name="rescanFile" type="submit" id="rescanFile" value="Rescan" /></p>
                    </form>
                   </p>
<%
            }
          }//if ftfile exists
        }
      }


    if (request.isUserInRole("admin") || request.isUserInRole("imageProcessor")) {
%>
<br />
<p style="background-color:#f0f0f0;"><em>Upload or replace a processed, cropped manta patterning image.</em></p>
      <p><form action="../EncounterAddMantaPattern" method="post" enctype="multipart/form-data" name="EncounterAddMantaPattern">
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
  } else {
%>
  <p>No photos found for encounter.</p>
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
