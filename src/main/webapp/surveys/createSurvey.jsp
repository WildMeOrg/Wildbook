<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,org.ecocean.movement.*,
java.io.*,java.util.*, java.io.FileInputStream, java.util.Date, java.text.SimpleDateFormat, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>

<%
String context="context0";
context=ServletUtilities.getContext(request);
String langCode=ServletUtilities.getLanguageCode(request);
Shepherd myShepherd=new Shepherd(context);

Properties props = new Properties();
Properties surveyProps = new Properties();
 
myShepherd.beginDBTransaction();

props = ShepherdProperties.getProperties("createSurvey.properties", langCode,context);
surveyProps = ShepherdProperties.getProperties("createSurvey.properties", langCode,context);


%>

<jsp:include page="../header.jsp" flush="true" />

<script type="text/javascript">

</script>

<div class="container maincontent">
	<div class="row">
		<div class="col-md-3">
		</div>
	
		<div class="col-md-6">
			<h3><%=props.getProperty("createSurvey") %></h3>
			<label><%=props.getProperty("addSurveyDesc") %></label>
			<hr/>		
				 <div title="<%=props.getProperty("addTag")%>" class="editFormSurvey">
					 <form name="addTag" action="../SurveyCreate" method="post" class="editFormSurvey">
						
						<label><%=props.getProperty("date")%></label>
						<div class="input-group date" data-provide="datepicker">
						    <input name="date" title="Date" type="text" class="form-control datepicker" id="addSurveyInput0"/>
						    <div class="input-group-addon">
						        <span class="glyphicon glyphicon-th"></span>
						    </div>
						</div>
						
						
						<label><%=props.getProperty("project")%></label>
						<input name="project" title="Project" type="text" class="form-control" id="addSurveyInput1" />
						
						<label><%=props.getProperty("organization")%></label>
						<input name="organization" title="Organization" type="text" class="form-control" id="addSurveyInput2" />
						
						<label><%=props.getProperty("startTime")%></label>
						<div class="input-group start-clockpicker">
						    <input name="startTime" title="Start Time" type="text" class="form-control" value="12:00" id="addTagInput3"/>
						    <span class="input-group-addon">
						        <span class="glyphicon glyphicon-time"></span>
						    </span>
						</div>
						
						<label><%=props.getProperty("endTime")%></label>
						<div class="input-group end-clockpicker">
						    <input name="endTime" title="End Time" type="text" class="form-control" value="12:00" id="addTagInput4"/>
						    <span class="input-group-addon">
						        <span class="glyphicon glyphicon-time"></span>
						    </span>
						</div>
						
						<label><%=props.getProperty("effort")%></label>
						<input name="effort" title="Effort" type="number" class="form-control" id="addTagInput5" step="0.1" />
						
						<label><%=props.getProperty("type")%>:</label>
						<label><small><%=props.getProperty("surveyTypes")%></small></label>
						<input name="surveyType" title="Survey Type" type="text" class="form-control" id="addTagInput6" />
						
						<label><%=props.getProperty("comments")%>:</label>
						<input name="comments" title="Comments" type="text" class="form-control" id="addTagInput7" />
						
						<div id="trackForm" style="display:none;">
							<hr/>
							<label><strong><%=props.getProperty("trackProps")%></strong></label><br/>
							<label><%=props.getProperty("vessel")%>:</label>
							<input name="vessel" title="Vessel" type="text" class="form-control" id="addTrackInput1" />
							
							<label><%=props.getProperty("locationID")%>:</label>
							<input name="locationID" title="Hatteras, Onslow Bay ect." type="text" class="form-control" id="addTrackInput2" />
							
							<label><%=props.getProperty("type")%>:</label>
							<label><small><%=props.getProperty("trackTypes")%></label>
							<input name="type" title="Survey Type" type="text" class="form-control" id="addTrackInput3" />
							
						</div>
						
						<input name="Add Survey Track" type="submit" id="surveyTrackSubmitBtn" value="<%=props.getProperty("submit")%>" class="btn btn-sm editFormBtn" />
				   								
						<input name="Define Survey Track" type="button" id="addSurveyTrackBtn" value="<%=props.getProperty("defineTrack")%>" class="btn btn-sm editFormBtn" />
				   		<input name="Hide Survey Track Options" type="button" id="hideSurveyTrackBtn" value="<%=props.getProperty("hideTrack")%>" class="btn btn-sm editFormBtn" />		    
				    
				    </form>
				</div>
			<div id="errorSpan"></div>
		
		</div>
		<div class="col-md-12">

		</div>

	</div>
</div>

<script type="text/javascript">
    $(document).ready(function(){
    	
    	$('#hideSurveyTrackBtn').hide();
    	
        $('.start-clockpicker').clockpicker({donetext: 'Set'});
        $('.end-clockpicker').clockpicker({donetext: 'Set'});   
        
        $('.datepicker').datepicker({
            format: 'mm/dd/yyyy',
            startDate: '-3d'
        });
        
        $('#addSurveyTrackBtn').click(function(){
        	$('#addSurveyTrackBtn').hide();
        	$('#hideSurveyTrackBtn').show();
        	$('#trackForm').slideDown();
        });
        $('#hideSurveyTrackBtn').click(function(){
        	$('#addSurveyTrackBtn').show();
        	$('#hideSurveyTrackBtn').hide();
        	$('#trackForm').slideUp();
        });
    });    
</script>

<jsp:include page="../footer.jsp" flush="true" />


