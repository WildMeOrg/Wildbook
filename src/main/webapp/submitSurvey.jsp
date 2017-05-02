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

</script>

<div class="container-fluid page-content" role="main">

<div class="container maincontent">
  
  
<hr />

<fieldset>
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
	    <h3><%=props.getProperty("surveyType")%></h3>
	    
	      <div class="col-xs-12 col-sm-12 col-md-6 col-lg-6">
	        <label class="control-label text-danger"><%=props.getProperty("typeLabel") %></label>
	      </div>
	        
	      <div class="col-xs-12 col-sm-12 col-md-6 col-lg-6">
	        <input name="type" type="text" id="type" size="40" class="form-control">
	      </div>
	    </div>	    
	    
	  </div>
  	</div>
</div>
<br>		

<%
//add locationID to fields selectable
if(CommonConfiguration.getIndexedPropertyValues("locationID", context).size()>0){
%>
    <div class="form-group required">
      <div class="col-xs-12 col-sm-12 col-md-4 col-lg-4">
        <label class="control-label"><%=props.getProperty("studySites") %></label>
      </div>

      <div class="col-xs-12 col-sm-12 col-md-4 col-lg-4">
        <select name="locationID" id="locationID" class="form-control">
            <option value="" selected="selected"></option>
                  <%
                         boolean hasMoreLocationsIDs=true;
                         int locNum=0;

                         while(hasMoreLocationsIDs){
                               String currentLocationID = "locationID"+locNum;
                               if(CommonConfiguration.getProperty(currentLocationID,context)!=null){
                                   %>

                                     <option value="<%=CommonConfiguration.getProperty(currentLocationID,context)%>"><%=CommonConfiguration.getProperty(currentLocationID,context)%></option>
                                   <%
                                 locNum++;
                            }
                            else{
                               hasMoreLocationsIDs=false;
                            }

                       }

     %>
      </select>
      </div>
    </div>
<%
}

if(CommonConfiguration.showProperty("showCountry",context)){

%>
          <div class="form-group required">
      <div class="col-xs-6 col-sm-6 col-md-4 col-lg-4">
        <label class="control-label"><%=props.getProperty("country") %></label>
      </div>

      <div class="col-xs-6 col-sm-6 col-md-6 col-lg-8">
        <select name="locationID" id="locationID" class="form-control">
            <option value="" selected="selected"></option>
            <%
            String[] locales = Locale.getISOCountries();
			for (String countryCode : locales) {
				Locale obj = new Locale("", countryCode);
				String currentCountry = obj.getDisplayCountry();
                %>
			<option value="<%=currentCountry %>"><%=currentCountry%></option>
            <%
            }
			%>
   		</select>
      </div>
    </div>

<%
}  //end if showCountry

%>

    <div>
      <div class=" form-group form-inline">
        <div class="col-xs-12 col-sm-6">
          <label class="control-label pull-left"><%=props.getProperty("submit_gpslatitude") %>&nbsp;</label>
          <input class="form-control" name="lat" type="text" id="lat"> &deg;
        </div>

        <div class="col-xs-12 col-sm-6">
          <label class="control-label  pull-left"><%=props.getProperty("submit_gpslongitude") %>&nbsp;</label>
          <input class="form-control" name="longitude" type="text" id="longitude"> &deg;
        </div>
      </div>

      <p class="help-block">
        <%=props.getProperty("gpsConverter") %></p>
    </div>


<%
if(CommonConfiguration.showProperty("maximumDepthInMeters",context)){
%>
 <div class="form-inline">
      <label class="control-label"><%=props.getProperty("submit_depth")%></label>
      <input class="form-control" name="depth" type="text" id="depth">
      &nbsp;<%=props.getProperty("submit_meters")%> <br>
    </div>
<%
}

if(CommonConfiguration.showProperty("maximumElevationInMeters",context)){
%>
 <div class="form-inline">
      <label class="control-label"><%=props.getProperty("submit_elevation")%></label>
      <input class="form-control" name="elevation" type="text" id="elevation">
      &nbsp;<%=props.getProperty("submit_meters")%> <br>
    </div>
<%
}
%>

</fieldset>
<hr />
  <fieldset>
    <div class="row">
      <div class="col-xs-12 col-lg-6">
        <h3><%=props.getProperty("aboutYou") %></h3>
        <p class="help-block"><%=props.getProperty("submit_contactinfo") %></p>
        <div class="form-group form-inline">
          <div class="col-xs-6 col-md-4">
            <label class="text-danger control-label"><%=props.getProperty("submit_name") %></label>
          </div>
          <div class="col-xs-6 col-lg-8">
            <input class="form-control" name="submitterName" type="text" id="submitterName" size="24" value="">
          </div>
        </div>

        <div class="form-group form-inline">

          <div class="col-xs-6 col-md-4">
            <label class="text-danger control-label"><%=props.getProperty("submit_email") %></label>
          </div>
          <div class="col-xs-6 col-lg-8">
            <input class="form-control" name="submitterEmail" type="text" id="submitterEmail" size="24" value="">
          </div>
        </div>
      </div>

      <div class="col-xs-12 col-lg-6">
        <h3><%=props.getProperty("aboutPhotographer") %></h3>

        <div class="form-group form-inline">
          <div class="col-xs-6 col-md-4">
            <label class="control-label"><%=props.getProperty("submit_name") %></label>
          </div>
          <div class="col-xs-6 col-lg-8">
            <input class="form-control" name="photographerName" type="text" id="photographerName" size="24">
          </div>
        </div>

        <div class="form-group form-inline">
          <div class="col-xs-6 col-md-4">
            <label class="control-label"><%=props.getProperty("submit_email") %></label>
          </div>
          <div class="col-xs-6 col-lg-8">
            <input class="form-control" name="photographerEmail" type="text" id="photographerEmail" size="24">
          </div>
        </div>
      </div>

    </div>
  </fielset>

  <hr/>

    <div class="form-group">
      <div class="col-xs-6 col-md-4">
        <label class="control-label"><%=props.getProperty("submit_comments") %></label>
      </div>
      <div class="col-xs-6 col-lg-8">
        <textarea class="form-control" name="comments" id="comments" rows="5"></textarea>
      </div>
    </div>
  </fieldset>




  <h4 class="accordion">
    <a href="javascript:animatedcollapse.toggle('advancedInformation')" style="text-decoration:none">
      <img src="images/Black_Arrow_down.png" width="14" height="14" border="0" align="absmiddle">
      <%=props.getProperty("advancedInformation") %>
    </a>
  </h4>

    <div id="advancedInformation" fade="1" style="display: none;">

      <h3><%=props.getProperty("aboutAnimal") %></h3>

      <fieldset>

        <div class="form-group">
          <div class="col-xs-6 col-md-4">
            <label class="control-label"><%=props.getProperty("submit_sex") %></label>
          </div>

          <div class="col-xs-6 col-lg-8">
            <label class="radio-inline">
              <input type="radio" name="sex" value="male"> <%=props.getProperty("submit_male") %>
            </label>
            <label class="radio-inline">
              <input type="radio" name="sex" value="female"> <%=props.getProperty("submit_female") %>
            </label>
            <label class="radio-inline">
              <input name="sex" type="radio" value="unknown" checked="checked"> <%=props.getProperty("submit_unsure") %>
            </label>
          </div>
        </div>
        </fieldset>
        <hr>
        <fieldset>
<%

if(CommonConfiguration.showProperty("showTaxonomy",context)){

%>

      <div class="form-group">
          <div class="col-xs-6 col-md-4">
            <label class="control-label"><%=props.getProperty("species") %></label>
          </div>

          <div class="col-xs-6 col-lg-8">
            <select class="form-control" name="genusSpecies" id="genusSpecies">
             	<option value="" selected="selected"><%=props.getProperty("submit_unsure") %></option>
  <%

  					List<String> species=CommonConfiguration.getIndexedPropertyValues("genusSpecies", context);
  					int numGenusSpeciesProps=species.size();
  					String selected="";
  					if(numGenusSpeciesProps==1){selected="selected=\"selected\"";}

                     if(CommonConfiguration.showProperty("showTaxonomy",context)){

                    	for(int q=0;q<numGenusSpeciesProps;q++){
                           String currentGenuSpecies = "genusSpecies"+q;
                           if(CommonConfiguration.getProperty(currentGenuSpecies,context)!=null){
                               %>
                                 <option value="<%=CommonConfiguration.getProperty(currentGenuSpecies,context)%>" <%=selected %>><%=CommonConfiguration.getProperty(currentGenuSpecies,context).replaceAll("_"," ")%></option>
                               <%

                        }


                   }
                   }
 %>
  </select>
    </div>
        </div>

        <%
}

%>

  <div class="form-group">
          <div class="col-xs-6 col-md-4">
            <label class="control-label"><%=props.getProperty("status") %></label>
          </div>

          <div class="col-xs-6 col-lg-8">
            <select class="form-control" name="livingStatus" id="livingStatus">
              <option value="alive" selected="selected"><%=props.getProperty("alive") %></option>
              <option value="dead"><%=props.getProperty("dead") %></option>
            </select>
          </div>
        </div>

				<div class="form-group">
					<div class="col-xs-6 col-md-4">
						<label class="control-label"><%=props.getProperty("alternate_id") %></label>
					</div>

					<div class="col-xs-6 col-lg-8">
						<input class="form-control" name="alternateID" type="text" id="alternateID" size="75">
					</div>
				</div>


        <div class="form-group">
          <div class="col-xs-6 col-md-4">
            <label class="control-label"><%=props.getProperty("submit_behavior") %></label>
          </div>

          <div class="col-xs-6 col-lg-8">
            <input class="form-control" name="behavior" type="text" id="behavior" size="75">
          </div>
        </div>


           <div class="form-group">
          <div class="col-xs-6 col-md-4">
            <label class="control-label"><%=props.getProperty("submit_scars") %></label>
          </div>

          <div class="col-xs-6 col-lg-8">
            <input class="form-control" name="scars" type="text" id="scars" size="75">
          </div>
        </div>



</fieldset>
<%
    pageContext.setAttribute("showMeasurements", CommonConfiguration.showMeasurements(context));
%>
<c:if test="${showMeasurements}">
<hr>
 <fieldset>
<%
    pageContext.setAttribute("items", Util.findMeasurementDescs(langCode,context));
    pageContext.setAttribute("samplingProtocols", Util.findSamplingProtocols(langCode,context));
%>

 <div class="form-group">
           <h3><%=props.getProperty("measurements") %></h3>


<div class="col-xs-12 col-lg-8">
  <table class="measurements">
  <tr>
  <th><%=props.getProperty("type") %></th><th><%=props.getProperty("size") %></th><th><%=props.getProperty("units") %></th><c:if test="${!empty samplingProtocols}"><th><%=props.getProperty("samplingProtocol") %></th></c:if>
  </tr>
  <c:forEach items="${items}" var="item">
    <tr>
    <td>${item.label}</td>
    <td><input name="measurement(${item.type})" id="${item.type}"/><input type="hidden" name="measurement(${item.type}units)" value="${item.units}"/></td>
    <td><c:out value="${item.unitsLabel}"/></td>
    <c:if test="${!empty samplingProtocols}">
      <td>
        <select name="measurement(${item.type}samplingProtocol)">
        <c:forEach items="${samplingProtocols}" var="optionDesc">
          <option value="${optionDesc.name}"><c:out value="${optionDesc.display}"/></option>
        </c:forEach>
        </select>
      </td>
    </c:if>
    </tr>
  </c:forEach>
  </table>
   </div>
        </div>
         </fieldset>
</c:if>




      <hr/>

       <fieldset>
        <h3><%=props.getProperty("tags") %></h3>
      <%
  pageContext.setAttribute("showMetalTags", CommonConfiguration.showMetalTags(context));
  pageContext.setAttribute("showAcousticTag", CommonConfiguration.showAcousticTag(context));
  pageContext.setAttribute("showSatelliteTag", CommonConfiguration.showSatelliteTag(context));
  pageContext.setAttribute("metalTags", Util.findMetalTagDescs(langCode,context));
%>

<c:if test="${showMetalTags and !empty metalTags}">

 <div class="form-group">
          <div class="col-xs-6 col-md-4">
            <label><%=props.getProperty("physicalTags") %></label>
          </div>

<div class="col-xs-12 col-lg-8">
    <table class="metalTags">
    <tr>
      <th><%=props.getProperty("location") %></th><th><%=props.getProperty("tagNumber") %></th>
    </tr>
    <c:forEach items="${metalTags}" var="metalTagDesc">
      <tr>
        <td><c:out value="${metalTagDesc.locationLabel}:"/></td>
        <td><input name="metalTag(${metalTagDesc.location})"/></td>
      </tr>
    </c:forEach>
    </table>
  </div>
  </div>
</c:if>

<c:if test="${showAcousticTag}">
 <div class="form-group">
          <div class="col-xs-6 col-md-4">
            <label><%=props.getProperty("acousticTag") %></label>
          </div>
<div class="col-xs-12 col-lg-8">
      <table class="acousticTag">
      <tr>
      <td><%=props.getProperty("serialNumber") %></td>
      <td><input name="acousticTagSerial"/></td>
      </tr>
      <tr>
        <td><%=props.getProperty("id") %></td>
        <td><input name="acousticTagId"/></td>
      </tr>
      </table>
    </div>
    </div>
</c:if>

<c:if test="${showSatelliteTag}">
 <div class="form-group">
          <div class="col-xs-6 col-md-4">
            <label><%=props.getProperty("satelliteTag") %></label>
          </div>
<%
  pageContext.setAttribute("satelliteTagNames", Util.findSatelliteTagNames(context));
%>
<div class="col-xs-12 col-lg-8">
      <table class="satelliteTag">
      <tr>
        <td><%=props.getProperty("name") %></td>
        <td>
            <select name="satelliteTagName">
              <c:forEach items="${satelliteTagNames}" var="satelliteTagName">
                <option value="${satelliteTagName}">${satelliteTagName}</option>
              </c:forEach>
            </select>
        </td>
      </tr>
      <tr>
        <td><%=props.getProperty("serialNumber") %></td>
        <td><input name="satelliteTagSerial"/></td>
      </tr>
      <tr>
        <td><%=props.getProperty("argosNumber") %></td>
        <td><input name="satelliteTagArgosPttNumber"/></td>
      </tr>
      </table>
    </div>
    </div>
</c:if>

      </fieldset>

<hr/>

      <div class="form-group">
        <label class="control-label"><%=props.getProperty("otherEmails") %></label>
        <input class="form-control" name="informothers" type="text" id="informothers" size="75">
        <p class="help-block"><%=props.getProperty("multipleEmailNote") %></p>
      </div>
      </div>



      <p class="text-center">
        <button class="large" type="submit" onclick="return sendButtonClicked();">
          <%=props.getProperty("submit_send") %>
          <span class="button-icon" aria-hidden="true" />
        </button>
      </p>


<p>&nbsp;</p>



<p>&nbsp;</p>
</form>

</div>
</div>

<jsp:include page="footer.jsp" flush="true"/>