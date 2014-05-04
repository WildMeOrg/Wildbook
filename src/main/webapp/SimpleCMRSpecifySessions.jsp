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

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.servlet.ServletUtilities,org.ecocean.*, java.util.*, java.util.Vector" %>
<%@ taglib uri="http://www.sunwesttek.com/di" prefix="di" %>


<html>
<head>
  <%
  
  String context="context0";
  context=ServletUtilities.getContext(request);

    //let's load out properties
    Properties props = new Properties();
    //String langCode = "en";
    String langCode=ServletUtilities.getLanguageCode(request);
    
    //props.load(getClass().getResourceAsStream("/bundles/" + langCode + "/individualSearchResultsExport.properties"));
    props = ShepherdProperties.getProperties("individualSearchResultsExport.properties", langCode);



int numSessions=0;
if(request.getParameter("numberSessions")!=null){
	try{
		Integer sess=new Integer(request.getParameter("numberSessions"));
		numSessions=sess.intValue();
	}	
	catch(NumberFormatException nfe){nfe.printStackTrace();}
}

String queryString="";
if(request.getQueryString()!=null){
	queryString=request.getQueryString();


	Enumeration params=request.getParameterNames();
	while(params.hasMoreElements()){

		String name=(String)params.nextElement();
		String value=request.getParameter(name);
		
		queryString+=("&"+name+"="+value);
		
	}
	
	
}

  %>
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
 <link rel="stylesheet" href="http://code.jquery.com/ui/1.10.2/themes/smoothness/jquery-ui.css" />

  
</head>
<style type="text/css">
  #tabmenu {
    color: #000;
    border-bottom: 2px solid black;
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
    color: #DEDECF;
    background: #000;
    font: bold 1em "Trebuchet MS", Arial, sans-serif;
    border: 2px solid black;
    padding: 2px 5px 0px 5px;
    margin: 0;
    text-decoration: none;
    border-bottom: 0px solid #FFFFFF;
  }

  #tabmenu a.active {
    background: #FFFFFF;
    color: #000000;
    border-bottom: 2px solid #FFFFFF;
  }

  #tabmenu a:hover {
    color: #ffffff;
    background: #7484ad;
  }

  #tabmenu a:visited {
    color: #E8E9BE;
  }

  #tabmenu a.active:hover {
    background: #7484ad;
    color: #DEDECF;
    border-bottom: 2px solid #000000;
  }
</style>
<body>
<div id="wrapper">
<div id="page">
<jsp:include page="header.jsp" flush="true">

  <jsp:param name="isAdmin" value="<%=request.isUserInRole(\"admin\")%>" />
</jsp:include>
 <script src="http://code.jquery.com/ui/1.10.2/jquery-ui.js"></script>
  
   <script>
   
    $(function() {
    <%
    for(int f=0;f<numSessions;f++){
    %>
    	$( "#datepicker<%=f%>start" ).datepicker().datepicker('option', 'dateFormat', 'yy-mm-dd');
      $( "#datepicker<%=f%>end" ).datepicker().datepicker('option', 'dateFormat', 'yy-mm-dd');
    <%
    }
    %>
    
    });
  
  
  </script>
<div id="main">
<ul id="tabmenu">


  <li><a href="individualSearchResults.jsp?<%=queryString.replaceAll("startNum","uselessNum").replaceAll("endNum","uselessNum") %>"><%=props.getProperty("table")%>
  </a></li>
  <li><a href="individualThumbnailSearchResults.jsp?<%=queryString.replaceAll("startNum","uselessNum").replaceAll("endNum","uselessNum") %>"><%=props.getProperty("matchingImages")%>
  </a></li>
   <li><a href="individualMappedSearchResults.jsp?<%=queryString.replaceAll("startNum","uselessNum").replaceAll("endNum","uselessNum") %>"><%=props.getProperty("mappedResults")%>
  </a></li>
  <li><a href="individualSearchResultsAnalysis.jsp?<%=queryString.replaceAll("startNum","uselessNum").replaceAll("endNum","uselessNum") %>"><%=props.getProperty("analysis")%>
  </a></li>
    <li><a class="active"><%=props.getProperty("export")%>
  </a></li>

</ul>
<table width="810" border="0" cellspacing="0" cellpadding="0">
  <tr>
    <td>
      <br/>

      <h1 class="intro">
        <%=props.getProperty("title")%></h1><br /><em><strong>Simple Mark-Recapture History File Export: Step 2 Date Selection</strong></em>
      

    </td>
  </tr>
</table>


<p>
You specified:
<ul>
<li>Capture sessions: <%=request.getParameter("numberSessions")%></li>
<%
if(request.getParameter("includeIndividualID")!=null){
%>
<li>Include individual ID as a comment after each line.</li>
<%
}

if(request.getParameter("includeQueryComments")!=null){
%>
<li>Include selected query options as comments as well.</li>
<%
}
%>
</ul>
</p>


<p>Please fill out the date fields below for the start and end of each session and then click the <strong>Submit</strong> button to obtain your capture history file.</p>

<%

String additionalParameters="&numberSessions="+numSessions;

if(request.getParameter("includeIndividualID")!=null){
	additionalParameters+="&includeIndividualID=includeIndividualID";
}
if(request.getParameter("includeQueryComments")!=null){
	additionalParameters+="&includeQueryComments=includeQueryComments";
}
%>


<p>
<form name="simpleCMR" action="http://<%=CommonConfiguration.getURLLocation(request)%>/SimpleCMROutput?<%=additionalParameters %>" method="get">

<%
Enumeration params=request.getParameterNames();
while(params.hasMoreElements()){

	String name=(String)params.nextElement();
	String value=request.getParameter(name);
%>
	<input type="hidden" id="<%=name %>" name="<%=name %>" value="<%=value %>" />
<%
}
%>

<table>
<%
for(int i=0;i<numSessions;i++){
%>
<tr>
	<td bgcolor="#99CCFF">Session <%=(i+1)%>&nbsp;</td>
	<td bgcolor="#C0C0C0">Start: <input type="text" id="datepicker<%=i%>start" name="datepicker<%=i%>start" /></td>
	<td bgcolor="#C0C0C0">End: <input type="text" id="datepicker<%=i%>end" name="datepicker<%=i%>end" /></td>
</tr>
<%
}
%>
<tr><td colspan="3"><input type="submit" value="Submit"></td></tr>
</table>

</form>
</p>








<p></p>
<jsp:include page="footer.jsp" flush="true"/>
</div>
</div>
<!-- end page --></div>
<!--end wrapper -->
</body>
</html>


