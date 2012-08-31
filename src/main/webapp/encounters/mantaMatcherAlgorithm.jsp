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
File processedImage=new File(thisEncounterDir, "mantaProcessedImage.jpg");
if(!processedImage.exists()){

%>
<p>No processed image was found.</p>
<%
}
else{
%>
<p>A processed image was found.</p>
<img src="/<%=shepherdDataDir.getName() %>/encounters/<%=encNum %>/<%=processedImage%>"/>
<%	
}


}


catch(Exception e){
	e.printStackTrace();
}
%>


