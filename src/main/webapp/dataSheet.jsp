<%@ page contentType="text/html; charset=utf-8" language="java"
  import="
    org.ecocean.servlet.ServletUtilities,
    org.ecocean.*,
    org.ecocean.datacollection.*,
    javax.jdo.Extent,
    javax.jdo.Query,
    java.io.File,
    java.util.List,
    java.util.ArrayList,
    java.util.Properties,
    java.util.Enumeration,
    java.lang.reflect.Method,
    org.ecocean.security.Collaboration" %>


<jsp:include page="header.jsp" flush="true"/>
<!-- IMPORTANT style import for table printed by ClassEditTemplate.java -->
<link rel="stylesheet" href="css/classEditTemplate.css" />
<script src="javascript/timepicker/jquery-ui-timepicker-addon.js"></script>
<script type="text/javascript" src="javascript/classEditTemplate.js"></script>




<%

  String context="context0";
  context=ServletUtilities.getContext(request);
  Shepherd myShepherd = new Shepherd(context);

  //handle some cache-related security
  response.setHeader("Cache-Control", "no-cache"); //Forces caches to obtain a new copy of the page from the origin server
  response.setHeader("Cache-Control", "no-store"); //Directs caches not to store the page under any circumstance
  response.setDateHeader("Expires", 0); //Causes the proxy cache to see the page as "stale"
  response.setHeader("Pragma", "no-cache"); //HTTP 1.0 backward compatibility

  //setup data dir
  String rootWebappPath = getServletContext().getRealPath("/");
  File webappsDir = new File(rootWebappPath).getParentFile();
  File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));
  Properties props = new Properties();
  String langCode=ServletUtilities.getLanguageCode(request);


  Properties encprops = ShepherdProperties.getProperties("encounter.properties", langCode, context);

  //Properties stuprops = ShepherdProperties.getProperties("studySite.properties", langCode, context);
  Properties dsProps = ShepherdProperties.getProperties("dataSheet.properties", langCode, context);


  String dataSheetID = request.getParameter("number");
  String newSheet = request.getParameter("new");


  int nFieldsPerSubtable = 8;

  System.out.println("beginning dataSheet.jsp!");


  DataSheet dsheet;
  if (dataSheetID!=null) {
    dsheet = myShepherd.getDataSheet(dataSheetID);
    System.out.println("myShepherd grabbed DataSheet #"+dataSheetID+". dsheet = "+dsheet);
  }
  else {
    System.out.println("NEW DataSheet!!!: myShepherd did not find a DataSheet # upon loading dataSheet.jsp");
    dsheet = new DataSheet(request);
    myShepherd.beginDBTransaction();
    boolean commitResp = myShepherd.storeNewDataSheet(dsheet);
    dataSheetID = dsheet.getID();
    System.out.println("dsheet.ID="+dataSheetID+" and myShepherd commit = "+commitResp+". isDataSheet = "+myShepherd.isDataSheet(dataSheetID)+" and isDataCollectionEvent = "+myShepherd.isDataCollectionEvent(dataSheetID));

    System.out.println("++++now trying the class-ed findDataCollectionEvent");
    DataSheet ds2 = myShepherd.findDataCollectionEvent(dsheet.getClass(), dataSheetID);
    System.out.println("++++the result of that is "+ds2);




  }
  System.out.println("dataSheet.jsp: loaded dsheet = "+dsheet);

  String[] dataSheetFieldGetters = new String[]{"getName", "getType", "getLatitude", "getLongitude", "getSamplingProtocol", "getDatasetName", "getInstitutionID", "getCollectionID","getDatasetID"};

  String[] dataSheetFieldDTGetters = new String[]{"getEventStartDateTime", "getEventEndDateTime"};

  String saving = request.getParameter("save");
  boolean needToSave = (saving != null);

  String newThreatSheet = request.getParameter("newThreatSheet");
  if (Util.stringExists(newThreatSheet)) {
    System.out.println("**** adding threat sheet to datasheet "+dataSheetID );
    //dsheet.addConfigDataPoints("threatSheet", context);
    dsheet.setType("Threat Sheet");
    myShepherd.commitDBTransaction();
    myShepherd.beginDBTransaction();
  }

  String newHabitatSheet = request.getParameter("newHabitatSheet");
  if (Util.stringExists(newHabitatSheet)) {
    System.out.println("**** adding habitat threat sheet to datasheet "+dataSheetID );
    dsheet.addConfigDataPoints("habitatThreats", context);
    if (dsheet.getType()==null || dsheet.getType().equals("DataSheet")){
      dsheet.setType("Habitat Sheet");
      myShepherd.commitDBTransaction();
      myShepherd.beginDBTransaction();
    }
  }

  String newPopulationSheet = request.getParameter("newPopulationSheet");
  if (Util.stringExists(newPopulationSheet)) {
    System.out.println("**** adding population threat sheet to datasheet "+dataSheetID );
    dsheet.addConfigDataPoints("populationThreats", context);
    if (dsheet.getType()==null || dsheet.getType().equals("DataSheet")){
      dsheet.setType("Population Sheet");
      myShepherd.commitDBTransaction();
      myShepherd.beginDBTransaction();
    }
  }


  if (needToSave) {
    System.out.println("");
    System.out.println("DATASHEET.JSP: Saving updated info...");
    Enumeration en = request.getParameterNames();


    while (en.hasMoreElements()) {
      String pname = (String) en.nextElement();
      String value = request.getParameter(pname);
      System.out.println("parsing parameter "+pname);
      if (pname.indexOf("dat:") == 0) {
        String methodName = "set" + pname.substring(4,5).toUpperCase() + pname.substring(5);
        String getterName = "get" + methodName.substring(3);
        System.out.println("DataSheet.jsp: about to call ClassEditTemplate.updateObjectField("+dsheet+", "+methodName+", "+value+");");
        ClassEditTemplate.updateObjectField(dsheet, methodName, value);
      }

      else if (pname.indexOf("dat-dp-") == 0) {
        // looks like stu-dp-dsNUM: _____. now to parse the NUM
        String beforeColon = pname.split(":")[0];
        String dpID = beforeColon.substring(7);
        System.out.println("  looks like a change was detected on DataPoint "+dpID);
        DataPoint dp = myShepherd.getDataPoint(dpID);
        System.out.println("  now I have dp and its labeled string = "+dp.toLabeledString());
        System.out.println("  its old value = "+dp.getValueString());
        System.out.println("checkone");
        dp.setValueFromString(value);
        System.out.println("checktwo");
        System.out.println("  its new value = "+dp.getValueString());
      }
    }
    myShepherd.commitDBTransaction();
    System.out.println("DATASHEET.JSP: Transaction committed");
    System.out.println("");
  }
%>

<script src="http://maps.google.com/maps/api/js?sensor=false&language=<%=langCode%>"></script>

<div class="container maincontent">
  <form method="post" onsubmit="return classEditTemplate.checkBeforeDeletion()" action="dataSheet.jsp?number=<%=dataSheetID%>" id="classEditTemplateForm">

  <div class="row">



  <div class="col-sm-6 col-xs-12">
    <h1><%= dsProps.getProperty("DataSheet") %></h1>
    <p class="dataSheetidlabel"><em>id <%=dataSheetID%></em><p>

      <table class="dataSheet-field-table edit-table">
        <%

        if (dsheet!=null) {

        for (String getterName : dataSheetFieldGetters) {
          Method dataSheetMeth = dsheet.getClass().getMethod(getterName);
          System.out.println("  Data Sheet.jsp: Tryna get that method "+getterName+" isNullMethod = "+(dataSheetMeth==null));
          if (ClassEditTemplate.isDisplayableGetter(dataSheetMeth)) {
            ClassEditTemplate.printOutClassFieldModifierRow((Object) dsheet, dataSheetMeth, out);
          }
        }

        // for (String getterName : dataSheetFieldDTGetters) {
        //   Method dataSheetMeth = dsheet.getClass().getMethod(getterName);
        //   if (ClassEditTemplate.isDisplayableGetter(dataSheetMeth)) {
        //     ClassEditTemplate.printOutDateTimeModifierRow((Object) dsheet, dataSheetMeth, out);
        //   }
        // }

        %>
      </table>

      <% // need to display data points
      System.out.println("dataSheet.jsp: beginning to parse data points for sheet "+dsheet);
      int nFields = dsheet.size();
      int nSubtables = Util.getNumSections(nFields, nFieldsPerSubtable);
      int dataPointN = 0;
      System.out.println("               nDataPoints="+nFields);
      System.out.println("               nSubtables="+nSubtables);


      for (int tableN=0; tableN < nSubtables; tableN++) {

      %>
      <table class="nest-field-table edit-table" style="float: left">
      <%

        for (int subTableI=0; dataPointN < nFields && subTableI < nFieldsPerSubtable; dataPointN++, subTableI++) {
          DataPoint dp = dsheet.getData().get(dataPointN);
          ClassEditTemplate.printOutClassFieldModifierRow((Object) dsheet, dp, out);
        }
      %>
      </table>
      <% } %>
    </hr></br>
    <div class="submit" style="display:inline-block;position:center;">
      <input type="submit" name="save" value="Save" />
      <!--<input type="submit" name="newThreatSheet" value="Add New Threat Sheet" />-->
      <input type="submit" name="newHabitatSheet" value="Add Habitat Threat fields" />
      <input type="submit" name="newPopulationSheet" value="Add Population Threat fields" />
      <span class="note" style="position:absolute;bottom:9"></span>
    </div>
  </div>


</form>

<div class="col-sm-6 col-xs-12">

<h2> Map </h2>
<em>
</em>


<p><em>For illustration only. To set position, please use form.</em></p>

<script type="text/javascript">

  var map;
  var marker;
  var center = new google.maps.LatLng(0, 0);

  function placeMarker(location) {

  	if(marker!=null){marker.setMap(null);}
  	marker = new google.maps.Marker({
  	  position: location,
  	  map: map,
  	  visible: true
  	});

    var ne_lat_element = document.getElementById('lat');
    var ne_long_element = document.getElementById('longitude');

    ne_lat_element.value = location.lat();
    ne_long_element.value = location.lng();
	}

  function initialize() {
    var mapZoom = 7;
    var center = new google.maps.LatLng(-0.9538, -90.2);
    map = new google.maps.Map(document.getElementById('map_canvas'), {
      zoom: mapZoom,
      center: center,
      mapTypeId: google.maps.MapTypeId.HYBRID,
      zoomControl: true,
      scaleControl: false,
      scrollwheel: false,
      disableDoubleClickZoom: true,
	  });

  	if(marker!=null){
	    marker.setMap(map);
    }

  	google.maps.event.addListener(map, 'click', function(event) {
	    placeMarker(event.latLng);
    });

  }




  // START MAP and GPS SETTER

  var markers = [];
  var latLng = new google.maps.LatLng(<%=dsheet.getLatitude()%>, <%=dsheet.getLongitude()%>);


  <%
  String markerText="";

  String haploColor="CC0000";
  String defaultMarkerColor = encprops.getProperty("defaultMarkerColor");
  if(defaultMarkerColor!=null){
    haploColor=defaultMarkerColor;
  }
  %>

  marker = new google.maps.Marker({
    icon: 'https://chart.googleapis.com/chart?chst=d_map_pin_letter&chld=<%=markerText%>|<%=haploColor%>',
    position:latLng,
    map:map
  });

  <%
  if((dsheet.getLatitude()==null)||(dsheet.getLongitude()==null)){
  %>  marker.setVisible(false); <%
  } %>

  markers.push(marker);
  google.maps.event.addDomListener(window, 'load', initialize);
</script>

<%
if((request.getUserPrincipal()!=null)){
%>
  <div id="map_canvas" style="width: 100%; height: 350px; overflow: hidden;"></div>
<%
}
else {
%>
<p><%=encprops.getProperty("nomap") %></p>
<%
}
%>
<!-- adding ne submit GPS-->



<%
  String longy="";
  String laty="";
  if(dsheet.getLatitude()!=null){laty=dsheet.getLatitude().toString();}
  if(dsheet.getLongitude()!=null){longy=dsheet.getLongitude().toString();}

%>



    <a name="gps"></a>
    <div>
      <br>
      <div class="highlight resultMessageDiv" id="gpsErrorDiv"></div>

      <br/>
    </div>
<br /> <br />

<% //endif
} else {
%>
  <p><em>Error: was not able to find datasheet number <%=dataSheetID%></em></p>
  <p><em>myShepherd.isDataSheet(__) = <%=myShepherd.isDataSheet(dataSheetID)%></em></p>
  <p><em>myShepherd.getDataSheet(__) = <%=myShepherd.getDataSheet(dataSheetID)%></em></p>
  <%
}
%>


</div>





<style>

  table.dataSheet-field-table {
    table-layout: fixed;
    margin-bottom: 2em;
  }

</style>





<jsp:include page="footer.jsp" flush="true"/>
