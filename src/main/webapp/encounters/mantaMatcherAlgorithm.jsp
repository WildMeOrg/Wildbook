<%@ page contentType="text/html; charset=utf-8" language="java" import="org.ecocean.*, java.util.*,javax.jdo.*,java.io.File" %>

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

try {

//get the encounter number
String encNum = request.getParameter("encounterNumber");
	
//set up the JDO pieces and Shepherd
Shepherd myShepherd = new Shepherd();


//let's set up references to our file system components
String rootWebappPath = getServletContext().getRealPath("/");
File webappsDir = new File(rootWebappPath).getParentFile();
File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName());
File encountersDir=new File(shepherdDataDir.getAbsolutePath()+"/encounters");
File thisEncounterDir = new File(encountersDir, encNum);


%>
<p><strong>Matching Algorithm (under development)</strong></p>

<%
File matchOutput=new File(thisEncounterDir, "matchOutput.xhtml");
File processedImage=new File(thisEncounterDir, "mantaProcessedImage_CR.jpg");
File enhancedImage=new File(thisEncounterDir, "mantaProcessedImage_EH.jpg");
if(!processedImage.exists()){

%>
<p>No candidate region image was found.</p>
<%
}
else{
%>
<p>A candidate region image was found.<br />
<img src="/<%=shepherdDataDir.getName() %>/encounters/<%=encNum %>/<%=processedImage.getName()%>"/></p>

<%	
	if(!enhancedImage.exists()){
	%>
		<p>No enhanced image was found.</p>
	<%
	}
	else{
		%>
		<p>An enhanced image was found.<br />
			<img src="/<%=shepherdDataDir.getName() %>/encounters/<%=encNum %>/<%=enhancedImage.getName()%>"/></p>
		
		<%
		if(!matchOutput.exists()){
			%>
			<p>No match results file was found.</p>
			<%
		}
		else{
			%>
			<p>A match results file was found.<br />
			<img src="/<%=shepherdDataDir.getName() %>/encounters/<%=encNum %>/<%=matchOutput.getName()%>"/></p>
		
			<%
		}
	}

}
if((request.isUserInRole("admin"))||(request.isUserInRole("imageProcessor"))){
%>

<br />
<p><em>Upload or replace the processed, cropped manta patterning image.</em></p>
      <p><form action="../EncounterAddMantaPattern" method="post"
            enctype="multipart/form-data" name="EncounterAddMantaPattern"><input
        name="action" type="hidden" value="imageadd" id="action" />
        <input name="number" type="hidden" value="<%=encNum%>" id="number" />
        <strong><img align="absmiddle"
                     src="../images/upload_small.gif"/> Select file:</strong><br/>
        <input name="file2add" type="file" size="20" />

        <p><input name="addtlFile" type="submit" id="addtlFile"
                  value="Upload" /></p>
     </form></p>
     <%
     if(processedImage.exists()){
     %>
     <p><em>Remove the processed, cropped manta patterning image.</em></p>
           <p>
           <form action="../EncounterAddMantaPattern" method="post"
            name="EncounterAddMantaPattern"><input
        name="action" type="hidden" value="imageremove" id="action" />
        <input name="number" type="hidden" value="<%=encNum%>" id="number" />

        <p><input name="addtlFile" type="submit" id="addtlFile"
                  value="Remove the file" /></p>
     </form></p>
<%
     }
}
}

catch(Exception e){
	e.printStackTrace();
}
%>


