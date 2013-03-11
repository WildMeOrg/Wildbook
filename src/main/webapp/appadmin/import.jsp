<%--
  ~ The Shepherd Project - A Mark-Recapture Framework
  ~ Copyright (C) 2011 Jason Holmberg
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

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.CommonConfiguration,java.util.Properties" %>
<%

  //setup our Properties object to hold all properties
  Properties props = new Properties();
  String langCode = "en";



  //set up the file input stream
  //FileInputStream propsInputStream=new FileInputStream(new File((new File(".")).getCanonicalPath()+"/webapps/ROOT/WEB-INF/classes/bundles/"+langCode+"/submit.properties"));
   props.load(getClass().getResourceAsStream("/bundles/" + langCode + "/submit.properties"));



%>

<html>
<head>
  <title>Data Import
  </title>
  <meta http-equiv="Content-Type" content="text/html; charset=utf-8"/>
  <meta name="Description"
        content="<%=CommonConfiguration.getHTMLDescription() %>"/>
  <meta name="Keywords"
        content="<%=CommonConfiguration.getHTMLKeywords() %>"/>
  <meta name="Author" content="<%=CommonConfiguration.getHTMLAuthor() %>"/>
  <link href="<%=CommonConfiguration.getCSSURLLocation(request) %>"
        rel="stylesheet" type="text/css"/>
  <link rel="shortcut icon"
        href="<%=CommonConfiguration.getHTMLShortcutIcon() %>"/>

  <link rel="shortcut icon" href="images/favicon.ico"/>

</head>

<body>
<div id="wrapper">
  <div id="page">
    <jsp:include page="../header.jsp" flush="true">

    <jsp:param name="isAdmin" value="<%=request.isUserInRole(\"admin\")%>" />
    </jsp:include>
    <div id="main">

      <div id="maincol-wide">

        <div id="maintext">
          <h1 class="intro">Data Import</h1>
        </div>

        <p>Use the following forms to import data into the Shepherd Project.</p>
		<p><strong>SRGD Data Import</strong></p>
        <p>The SRGD data format was developed under the GeneGIS initiative and can be used to import genetic and identity data into the Shepherd Project.</p>
		<p>
		<table>
  <tr>
    <td class="para">
      <form action="../ImportSRGD" method="post" enctype="multipart/form-data" name="ImportSRGD">
	   <strong>
	   <img align="absmiddle" src="../images/CSV.png"/> SRGD CSV file:</strong>&nbsp;
        <input name="file2add" type="file" size="40" />
        <p><input name="addtlFile" type="submit" id="addtlFile" value="Upload" /></p>
		</form>
    </td>
  </tr>
</table>
</p>
      </div>
      <!-- end maintext -->

    </div>
    <!-- end maincol -->

    <jsp:include page="../footer.jsp" flush="true"/>
  </div>
  <!-- end page -->
</div>
<!--end wrapper -->
</body>
</html>
