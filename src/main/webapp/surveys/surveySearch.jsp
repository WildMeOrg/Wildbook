<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.servlet.ServletUtilities,org.ecocean.*, javax.jdo.Extent, javax.jdo.Query, java.util.ArrayList, java.util.List, java.util.GregorianCalendar, java.util.Iterator, java.util.Properties, java.io.IOException" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>

<%!
// here I'll define some methods that will end up in classEditTemplate

public static void printStringFieldSearchRow(String fieldName, javax.servlet.jsp.JspWriter out, Properties nameLookup) throws IOException, IllegalAccessException {
  // note how fieldName is variously manipulated in this method to make element ids and contents
  String displayName = getDisplayName(fieldName, nameLookup);
  out.println("<tr id=\""+fieldName+"Row\">");
  out.println("  <td id=\""+fieldName+"Title\">"+displayName+"</td>");
  out.println("  <td><input name=\""+fieldName+"\"/></td>");
  out.println("</tr>");

}

public static String getDisplayName(String fieldName, Properties nameLookup) throws IOException, IllegalAccessException {
  // Tries to lookup a translation and defaults to some string manipulation
  return (nameLookup.getProperty(fieldName, ClassEditTemplate.prettyFieldName(fieldName)));
}
%>



<%
String context="context0";
context=ServletUtilities.getContext(request);
  Shepherd myShepherd = new Shepherd(context);
  //myShepherd.setAction("individualSearch.jsp");
  Extent allKeywords = myShepherd.getPM().getExtent(Keyword.class, true);
  Query kwQuery = myShepherd.getPM().newQuery(allKeywords);

  GregorianCalendar cal = new GregorianCalendar();
  int nowYear = cal.get(1);

  int firstYear = 1980;
  myShepherd.beginDBTransaction();
  try {
    firstYear = myShepherd.getEarliestSightingYear();
    nowYear = myShepherd.getLastSightingYear();
  } catch (Exception e) {
    e.printStackTrace();
  }

//let's load out properties
  Properties props = new Properties();
  //String langCode = "en";
  String langCode=ServletUtilities.getLanguageCode(request);

  //props.load(getClass().getResourceAsStream("/bundles/" + langCode + "/individualSearch.properties"));
  // Properties occProps = new Properties();
  // occProps = ShepherdProperties.getProperties("occurrence.properties", langCode,context);
  
  
  Properties occProps = ShepherdProperties.getProperties("occurrence.properties", langCode,context);

  props = ShepherdProperties.getProperties("individualSearch.properties", langCode,context);
  
  Properties svyProps = ShepherdProperties.getProperties("survey.properties", langCode,context);
  // The file uses properties for surveys, occurrences, and individual search. To avoid repeating oneself?
  // there has to be a better way. 
  
  String mapKey = CommonConfiguration.getGoogleMapsKey(context);

%>


<jsp:include page="../header.jsp" flush="true"/>

    <!-- Sliding div content: STEP1 Place inside the head section -->
  <script type="text/javascript" src="../javascript/animatedcollapse.js"></script>

  <script type="text/javascript">
    //animatedcollapse.addDiv('location', 'fade=1')
    animatedcollapse.addDiv('map', 'fade=1')
    animatedcollapse.addDiv('date', 'fade=1')
    animatedcollapse.addDiv('observation', 'fade=1')
    animatedcollapse.addDiv('tags', 'fade=1')
    animatedcollapse.addDiv('identity', 'fade=1')
    animatedcollapse.addDiv('metadata', 'fade=1')
    animatedcollapse.addDiv('export', 'fade=1')
    animatedcollapse.addDiv('genetics', 'fade=1')
	animatedcollapse.addDiv('social', 'fade=1')
	animatedcollapse.addDiv('patternrecognition', 'fade=1')

    animatedcollapse.ontoggle = function($, divobj, state) { //fires each time a DIV is expanded/contracted
      //$: Access to jQuery
      //divobj: DOM reference to DIV being expanded/ collapsed. Use "divobj.id" to get its ID
      //state: "block" or "none", depending on state
    }
    animatedcollapse.init()
  </script>
  <!-- /STEP2 Place inside the head section -->

<script src="//maps.google.com/maps/api/js?key=<%=mapKey%>&language=<%=langCode%>"></script>
<script src="../encounters/visual_files/keydragzoom.js" type="text/javascript"></script>
<script type="text/javascript" src="../javascript/geoxml3.js"></script>
<script type="text/javascript" src="../javascript/ProjectedOverlay.js"></script>

  <!-- /STEP2 Place inside the head section -->




<style type="text/css">v\:* {
  behavior: url(#default#VML);
}</style>

<style type="text/css">
.full_screen_map {
position: absolute !important;
top: 0px !important;
left: 0px !important;
z-index: 1 !imporant;
width: 100% !important;
height: 100% !important;
margin-top: 0px !important;
margin-bottom: 8px !important;
</style>

<script>
  function resetMap() {
    var ne_lat_element = document.getElementById('ne_lat');
    var ne_long_element = document.getElementById('ne_long');
    var sw_lat_element = document.getElementById('sw_lat');
    var sw_long_element = document.getElementById('sw_long');

    ne_lat_element.value = "";
    ne_long_element.value = "";
    sw_lat_element.value = "";
    sw_long_element.value = "";

  }
</script>

<div class="container maincontent">
<table width="720">
<tr>
<td>
<p>
<%
String titleString=svyProps.getProperty("surveySearch");
String formAction="surveySearchResults.jsp";
%>


<h1 class="intro"><strong><span class="para">
		<img src="../images/wild-me-logo-only-100-100.png" width="50" align="absmiddle"/></span></strong>
  <%=titleString%>
</h1>
</p>

<p><em><strong><%=occProps.getProperty("searchInstructions")%>
</strong></em></p>


<form action="<%=formAction %>" method="get" name="search" id="search">

<table width="810px">



<tr>
  <td>
    <h4 class="intro" style="background-color: #cccccc; padding:3px; border: 1px solid #000066; "><a
      href="javascript:animatedcollapse.toggle('date')" style="text-decoration:none"><img
      src="../images/Black_Arrow_down.png" width="14" height="14" border="0" align="absmiddle"/> <font
      color="#000000"><%=props.getProperty("dateFilters") %></font></a></h4>
  </td>
</tr>

<tr>
  <td>
    <div id="date" style="display:none;">
      <p><%=svyProps.getProperty("dateInstructions") %></p>

<!--  date of birth and death -->
      <p><strong><%=occProps.getProperty("dateStart")+" "+occProps.getProperty("range")%>:</strong></p>
      <table>
      	<tr>
      		<td><%=occProps.getProperty("start") %> <input type="text" id="eventStartDate-From" name="sartTimeFrom" class="addDatePicker"/></td>
      		<td><%=occProps.getProperty("end") %> <input type="text" id="eventStartDate-To" name="startTimeTo" class="addDatePicker"/></td>
      	</tr>
      </table>

      <p><strong><%=occProps.getProperty("dateEnd")+" "+occProps.getProperty("range")%>:</strong></p>
      <table>
      	<tr>
      		<td><%=occProps.getProperty("start") %> <input type="text" id="eventEndDate-From" name="endTimeFrom" class="addDatePicker"/></td>
      		<td><%=occProps.getProperty("end") %> <input type="text" id="eventEndDate-To" name="endTimeTo" class="addDatePicker"/></td>
      	</tr>
      </table>

      <script>
      $(function() {
        $('.addDatePicker').datepicker();
        console.log("Done setting datepickers!");
      });
      </script>

    </div>
  </td>
</tr>

<%
  pageContext.setAttribute("showMetalTags", CommonConfiguration.showMetalTags(context));
  pageContext.setAttribute("showAcousticTag", CommonConfiguration.showAcousticTag(context));
  pageContext.setAttribute("showSatelliteTag", CommonConfiguration.showSatelliteTag(context));
%>

  <tr id="FieldsTitleRow">
    <td>
      <h4 class="intro" style="background-color: #cccccc; padding:3px; border: 1px solid #000066; "><a
        href="javascript:animatedcollapse.toggle('tags')" style="text-decoration:none"><img
        src="../images/Black_Arrow_down.png" width="14" height="14" border="0" align="absmiddle"/> <font
        color="#000000"><%=occProps.getProperty("observations") %></font></a></h4>
    </td>
  </tr>
	  	<!-- Begin search code for Observations --> 
	<tr>
		<td>
		  	<div id="tags" style="display:none;">
				<br/>
				<!-- Allow a key and value for each observation, allow user to add additional fields. -->
				<p>
					<label><%=occProps.getProperty("obSearchHeader")%></label>
					<label><small><%=occProps.getProperty("obSearchDesc")%></small></label>
				</p>
				<p>
					<input name="observationKey1" type="text" id="observationKey1" value="" placeholder="Observation Name">
					<input name="observationValue1" type="text" id="observationValue1" value="" placeholder="Observation Value">
				</p>
				<div id="additionalObsFields">
				
				
				</div>
				<input name="numSearchedObs" type="hidden" id="numSearchedObs" value="1" >
				<input name="AddAnotherObBtn" type="button" id="addAnotherObBtn" value="<%=occProps.getProperty("addAnotherOb")%>" class="btn btn-sm" />				
				<br/>
		  	</div>
		</td>
	</tr>	
		
		
<script>
	$(document).ready(function(){
		// Set to 2 because the first Observation is #1, and this variable will not be used until another observation is made.
		var num = 2;
		$('#addAnotherObBtn').click(function(){
			var obField = '<p><input name="observationKey'+num+'" type="text" id="observationKey'+num+'" value="" placeholder="Observation Name"><input name="observationValue'+num+'" type="text" id="observationValue'+num+'" value="" placeholder="Observation Value"></p>';	
			$('#additionalObsFields').append(obField);	
			$('#numSearchedObs').val(num); 
			num++;		
		});
	});
</script>
  	

<tr>
  <td>

    <h4 class="intro" style="background-color: #cccccc; padding:3px; border: 1px solid #000066; "><a
      href="javascript:animatedcollapse.toggle('metadata')" style="text-decoration:none"><img
      src="images/Black_Arrow_down.png" width="14" height="14" border="0" align="absmiddle"/>
      <font color="#000000"><%=props.getProperty("metadataFilters") %></font></a></h4>
  </td>
</tr>

<tr>
<td>
  <div id="metadata" style="display:none; ">
  <p><%=props.getProperty("metadataInstructions") %></p>

	<strong><%=props.getProperty("username")%></strong><br />
      <%
      	Shepherd inShepherd=new Shepherd("context0");
      //inShepherd.setAction("individualSearch.jsp2");
        List<User> users = inShepherd.getAllUsers();
        int numUsers = users.size();

      %>

      <select multiple size="5" name="username" id="username">
        <option value="None"></option>
        <%
          for (int n = 0; n < numUsers; n++) {
            String username = users.get(n).getUsername();
            String userFullName=username;
            if(users.get(n).getFullName()!=null){
            	userFullName=users.get(n).getFullName();
            }

        	%>
        	<option value="<%=username%>"><%=userFullName%></option>
        	<%
          }
        %>
      </select>
<%
inShepherd.rollbackDBTransaction();
inShepherd.closeDBTransaction();

%>
</div>
</td>
</tr>

 <tr id="FieldsTitleRow">
   <td>
     <h4 class="intro" style="background-color: #cccccc; padding:3px; border: 1px solid #000066; "><a
       href="javascript:animatedcollapse.toggle('tags')" style="text-decoration:none"><img
       src="images/Black_Arrow_down.png" width="14" height="14" border="0" align="absmiddle"/> <font
       color="#000000"><%=occProps.getProperty("fieldsTitle") %></font></a></h4>
   </td>
 </tr>
 <tr id="fieldsContentRow">
   <td>
       <div id="tags" style="display:none;">
           <p><%=occProps.getProperty("fieldsInstructions") %></p>
           <%
           // here we'll programatically create divs that allow for searching through metadata fields
           %>
             <h5>Simple Search Criteria</h5>
             <table>
             <%
             for (String fieldName : SurveyQueryProcessor.SIMPLE_STRING_FIELDS) {
               printStringFieldSearchRow(fieldName, out, svyProps);
             }
             %>
             </table>
             </table>
       </div>
    </td>
 </tr>





<%
  myShepherd.rollbackDBTransaction();
%>


<tr>
  <td>


  </td>
</tr>
</table>
<br />
<input name="submitSearch" type="submit" id="submitSearch"
                   value="<%=props.getProperty("goSearch")%>" />
</form>
</td>
</tr>
</table>
<br>
</div>

<script>
/* the below function removes any blank-valued params from the form just before submitting, making the searchResults.jsp url MUCH cleaner and more readable */
$('#submitSearch').submit(function() {
  $(this)
    .find('input[name]')
    .filter(function () {
        return !this.value;
    })
    .prop('name', '');
  });
</script>



<jsp:include page="../footer.jsp" flush="true"/>


<%
  kwQuery.closeAll();
  myShepherd.closeDBTransaction();
  kwQuery = null;
  myShepherd = null;
%>