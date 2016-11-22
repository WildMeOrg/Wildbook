<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.servlet.ServletUtilities,org.ecocean.*, java.util.*, java.util.Vector" %>
<%@ taglib uri="http://www.sunwesttek.com/di" prefix="di" %>


  <%
  
  String context="context0";
  context=ServletUtilities.getContext(request);

    //let's load out properties
    Properties props = new Properties();
    //String langCode = "en";
    String langCode=ServletUtilities.getLanguageCode(request);
    
    //props.load(getClass().getResourceAsStream("/bundles/" + langCode + "/individualSearchResultsExport.properties"));
    props = ShepherdProperties.getProperties("individualSearchResultsExport.properties", langCode,context);



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
 

<jsp:include page="header.jsp" flush="true" />

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
  
  
</style>

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
  
   <div class="container maincontent">

      <h1><%=props.getProperty("title")%></h1>
  
  
<ul id="tabmenu">

<%
//String queryString="";
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
<form name="simpleCMR" action="//<%=CommonConfiguration.getURLLocation(request)%>/SimpleCMROutput?<%=additionalParameters %>" method="get">

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

</div>


<jsp:include page="footer.jsp" flush="true"/>



