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
         import="org.ecocean.*,org.ecocean.servlet.ServletUtilities,java.util.Properties" %>
<%

String context="context0";
context=ServletUtilities.getContext(request);

  //setup our Properties object to hold all properties
  Properties props = new Properties();
  //String langCode = "en";
  String langCode=ServletUtilities.getLanguageCode(request);
  

  //set up the file input stream
  //FileInputStream propsInputStream=new FileInputStream(new File((new File(".")).getCanonicalPath()+"/webapps/ROOT/WEB-INF/classes/bundles/"+langCode+"/submit.properties"));
  //props.load(propsInputStream);
  //props.load(getClass().getResourceAsStream("/bundles/" + langCode + "/submit.properties"));
  props = ShepherdProperties.getProperties("overview.properties", langCode,context);


%>

<html>
<head>
  <title><%=CommonConfiguration.getHTMLTitle(context) %>
  </title>
  
  <meta http-equiv="Content-Type" content="text/html; charset=utf-8"/>
  <meta name="Description"
        content="<%=CommonConfiguration.getHTMLDescription(context) %>"/>
  <meta name="Keywords"
        content="<%=CommonConfiguration.getHTMLKeywords(context) %>"/>
  <meta name="Author" content="<%=CommonConfiguration.getHTMLAuthor(context) %>"/>
  <link href="<%=CommonConfiguration.getCSSURLLocation(request,context) %>"
        rel="stylesheet" type="text/css"/>
  <link rel="shortcut icon"
        href="<%=CommonConfiguration.getHTMLShortcutIcon(context) %>"/>

 
<style>
.report-section {
	padding: 10px;
	margin-top: 10px;
	background-color: #EEE;
}

#shortly {
	padding: 20px;
	height: 80px;
	background-color: #FFC;
	border: solid 3px black;
	border-radius: 20px;
	
}

#monthly-filename, #individual-filename {
	width: 260px;
}

</style>

</head>

<body>
<div id="wrapper">
  <div id="page">
    <jsp:include page="header.jsp" flush="true">

    <jsp:param name="isAdmin" value="<%=request.isUserInRole(\"admin\")%>" />
    </jsp:include>
    <div id="main">
<script>
var now = new Date();
var dname = now.toISOString();
dname = dname.substr(0,4) + dname.substr(5,2) + dname.substr(8,2);

$(document).ready(function() {
	$('#monthly-filename').val('report-monthly-' + dname + '.xls');
	$('#individual-filename').val('report-individuals-' + dname + '.xls');
});

function reportMonthly() {
	$('.report-button').hide();
	var mname = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
	var filename = $('#monthly-filename').val() || 'report.xls';

	var url = 'ExportExcelFile?query=SELECT FROM org.ecocean.MarkedIndividual&filename=' + filename + '&columns=individualID&columns=sexOrGuess&headers=ID&headers=Sex&_ibeisHack';
	var y = now.getFullYear() - 1; 
	var m = now.getMonth();
	for (var i = 0 ; i < 12 ; i++) {
		m++;
		if (m > 12) {
			m = 1;
			y++;
		}
		url += '&columns=sightedForMonth:' + y + ':' + m + '&headers=' + mname[m-1] + new String(y).substr(2);
	}
	$('#shortly-wrapper').show();
	window.location.href = url;
	window.setTimeout(function() {
		$('#shortly-wrapper').hide();
		$('.report-button').show();
	}, 4500);
}

function reportIndividual() {
	$('.report-button').hide();
	$('#shortly-wrapper').show();
	var filename = $('#individual-filename').val() || 'report.xls';
	var url = 'ExportExcelIndividualReport?filename=' + filename;
	window.location.href = url;
	window.setTimeout(function() {
		$('#shortly-wrapper').hide();
		$('.report-button').show();
	}, 4500);
}

</script>

      <div id="maincol-wide">

	<div id="shortly-wrapper" style="position: fixed; top: 200px; width: 400px; margin-left: 100px; text-align: center; display: none;">
		<div id="shortly"><b>Your download will begin shortly. Please wait....</b></div>
	</div>

<h1>Reports</h1>

<div class="report-section">

<p><b>Monthly sighting report <span id="monthly-detail"></span></b></p>

<p><input placeholder="filename" id="monthly-filename" /></p>

<input class="report-button" type="button" value="download monthly sighting report" onClick="return reportMonthly();" />
</div>

<div class="report-section">

<p><b>Total individual report</b></p>

<p><input placeholder="filename" id="individual-filename" /></p>

<input class="report-button" type="button" value="download individual report" onClick="return reportIndividual();" />
</div>





    </div>
    <!-- end maincol -->

    <jsp:include page="footer.jsp" flush="true"/>
  </div>
  <!-- end page -->
</div>
<!--end wrapper -->
</body>
</html>
