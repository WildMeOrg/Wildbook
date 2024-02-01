<%@ page contentType="text/html; charset=utf-8"
		import="java.util.GregorianCalendar,
                 org.ecocean.servlet.ServletUtilities,
                 org.ecocean.*,
                 java.util.Properties,
                 java.util.List,
                 java.util.Locale" %>

<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>

<link href="tools/bootstrap/css/bootstrap.min.css" rel="stylesheet"/>
	
<jsp:include page="header.jsp" flush="true"/>

<%

//handle some cache-related security
response.setHeader("Cache-Control", "no-cache");
//Forces caches to obtain a new copy of the page from the origin server
response.setHeader("Cache-Control", "no-store");
//Directs caches not to store the page under any circumstance
response.setDateHeader("Expires", 0);
//Causes the proxy cache to see the page as "stale"
response.setHeader("Pragma", "no-cache");

String context = ServletUtilities.getContext(request);

//setup our Properties object to hold all properties
  Properties props = new Properties();
  //String langCode = "en";
  String langCode=ServletUtilities.getLanguageCode(request);

  props = ShepherdProperties.getProperties("submitSurvey.properties", langCode, context);

    //set up the file input stream
    //props.load(getClass().getResourceAsStream("/bundles/" + langCode + "/submit.properties"));
    
    // Not there yet!
%>

<script>
	$(function() {
		$("#startTime").datepicker();
		$("#endTime").datepicker();
	});
</script>

<div class="container-fluid page-content" role="main">

<div class="container maincontent">
  
  <div class="col-xs-2 col-sm-2 col-md-2 col-lg-2">
  </div>  
  <div class="col-xs-6 col-sm-6 col-md-6 col-lg-6">
	<h2>New Survey Submission</h2>
  </div>  
  <hr />
	
<form action="SubmitSurvey" method="POST" name="surveySubmission" target="_self" dir="ltr" lang="en">
<div class="row">
  <div class="col-xs-2 col-sm-2 col-md-2 col-lg-2">
  </div>

  <div class="col-xs-2 col-sm-2 col-md-8 col-lg-8">
	  <div class="row">
	    <div class="form-group">
	    <h3><%=props.getProperty("projectName")%></h3>
	          
	      <div class="col-xs-10 col-sm-12 col-md-6 col-lg-6">
	        <label class="control-label text-danger"><%=props.getProperty("projectLabel") %></label>
	      </div>
	      
	      <div class="col-xs-12 col-sm-12 col-md-6 col-lg-6">
	        <input name="project" type="text" id="project" size="40" class="form-control">
	      </div>
	    </div>
	    
	    <br>
	    
	    <div class="form-group">
	    <h3><%=props.getProperty("organizationName")%></h3>
	    
	      <div class="col-xs-12 col-sm-12 col-md-6 col-lg-6">
	        <label class="control-label text-danger"><%=props.getProperty("organizationLabel") %></label>
	      </div>
	        
	      <div class="col-xs-12 col-sm-12 col-md-6 col-lg-6">
	        <input name="organization" type="text" id="organization" size="40" class="form-control">
	      </div>
	    </div>
	    
	    <br>
	    
	    <div class="form-group">
	    <h3><%=props.getProperty("surveyType")%></h3>
	          
	      <div class="col-xs-10 col-sm-12 col-md-6 col-lg-6">
	        <label class="control-label text-danger"><%=props.getProperty("typeLabel") %></label>
	      </div>
	      
	      <div class="col-xs-12 col-sm-12 col-md-6 col-lg-6">
	        <input name="surveyType" type="text" id="surveyType" size="40" class="form-control">
	      </div>
	    </div>
	    
	    <br>
	    
	    <div class="form-group">
	    <h3><%=props.getProperty("surveyDate")%></h3>
	    
	      <div class="col-xs-12 col-sm-12 col-md-6 col-lg-6">
	        <label class="control-label text-danger"><%=props.getProperty("dateLabel") %></label>
	      </div>
	        
	      <div class="col-xs-12 col-sm-12 col-md-6 col-lg-6">
	        <input name="dateString" type="text" id="dateString" size="40" class="form-control">
	      </div>
	    </div>
	    
	    <br>
	    
	    <div class="form-group">
	    <h3><%=props.getProperty("startTime")%></h3>
	    
	      <div class="col-xs-12 col-sm-12 col-md-6 col-lg-6">
	        <label class="control-label text-danger"><%=props.getProperty("startTimeLabel") %></label>
	      </div>
	        
	      <div class="col-xs-12 col-sm-12 col-md-6 col-lg-6">
	        <input name="startTime" type="text" id="startTime" size="40" class="form-control" value="">
	      </div>
	    </div>
	    
	    <div class="form-group">
	    <h3><%=props.getProperty("endTime")%></h3>
	    
	      <div class="col-xs-12 col-sm-12 col-md-6 col-lg-6">
	        <label class="control-label text-danger"><%=props.getProperty("endTimeLabel") %></label>
	      </div>
	        
	      <div class="col-xs-12 col-sm-12 col-md-6 col-lg-6">
	        <input name="endTime" type="text" id="endTime" size="40" class="form-control" value="">
	      </div>
	    </div>	    	    
	    	    

	  </div>
  	</div>
</div>
<br>		

<p class="text-center">
  <button class="large" type="submit" name="Submit" value="Submit">
    <%=props.getProperty("submitButton")%>
    <span class="button-icon" aria-hidden="true" />
  </button>
</p>


<p>&nbsp;</p>



<p>&nbsp;</p>
</form>

</div>
</div>

<jsp:include page="footer.jsp" flush="true"/>