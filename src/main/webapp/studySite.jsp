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

  Properties stuprops = ShepherdProperties.getProperties("studySite.properties", langCode, context);


  String studySiteID = request.getParameter("number");
  int nFieldsPerSubtable = 8;

  System.out.println("beginning studySite.jsp!");


  StudySite sitey;
  if (studySiteID!=null) {
    sitey = myShepherd.getStudySite(studySiteID);
    System.out.println("myShepherd grabbed StudySite #"+studySiteID);

    if (sitey.getLocationID()!=null) {
      String loc = sitey.getLocationID();
      System.out.println("Sitey's locationID = "+loc);
      List<StudySite> sites = myShepherd.getStudySitesAtLocation(loc);
      System.out.println("Found "+sites.size()+" other sites at this location");
      List<String> siteIDs = new ArrayList<String>();
      for (StudySite siteyPeer : sites) {
        siteIDs.add(siteyPeer.getID());
      }
      System.out.println("StudySite IDs = "+siteIDs);


    }


  }
  else {
    System.out.println("new StudySite error!: myShepherd failed to find a StudySite # upon loading studySite.jsp");
    sitey = new StudySite(Util.generateUUID()); // TODO: fix this case
    myShepherd.storeNewStudySite(sitey);
    studySiteID = sitey.getID();
  }

  String[] studySiteFieldGetters1 = new String[]{"getName", "getTypeOfSite", "getUtmX", "getUtmY"};

  String[] epsgCodes = GeocoordConverter.epsgCodes();

  String[] studySiteFieldGetters2 = new String[]{"getGovernmentArea", "getPopulation", "getHuntingState", "getDaysNotWorking", "getLure", "getReward", "getTypeOfCamera", "getTrapsPerNight", "getComments"};



  String[] studySiteFieldDTGetters = new String[]{"getDate", "getDateEnd"};


  String saving = request.getParameter("save");



  boolean needToSave = (saving != null);


  if (needToSave) {
    System.out.println("");
    System.out.println("STUDYSITE.JSP: Saving updated info...");
    Enumeration en = request.getParameterNames();


    while (en.hasMoreElements()) {
      String pname = (String) en.nextElement();
      String value = request.getParameter(pname);
      System.out.println("parsing parameter "+pname);
      if (pname.indexOf("stu:") == 0) {
        String methodName = "set" + pname.substring(4,5).toUpperCase() + pname.substring(5);
        String getterName = "get" + methodName.substring(3);
        System.out.println("StudySite.jsp: about to call ClassEditTemplate.updateObjectField("+sitey+", "+methodName+", "+value+");");
        ClassEditTemplate.updateObjectField(sitey, methodName, value);
      }

      else if (pname.indexOf("stu-dp-") == 0) {
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
      else if (pname.indexOf("dat-") == 0) {
        String beforeColon = pname.split(":")[0];
        String dpID = beforeColon.substring(4);
        System.out.println("  Found a change on datasheet "+dpID);
      }
    }
    myShepherd.commitDBTransaction();
    System.out.println("STUDYSITE.JSP: Transaction committed");
    System.out.println("");
  }
%>

<script src="http://maps.google.com/maps/api/js?sensor=false&language=<%=langCode%>"></script>

<div class="container maincontent">
  <div class="row">

  <form method="post" onsubmit="return classEditTemplate.checkBeforeDeletion()" action="studySite.jsp?number=<%=studySiteID%>" id="classEditTemplateForm">


  <div class="col-sm-6 col-xs-12">
    <h1><%= stuprops.getProperty("StudySite") %></h1>
    <p class="studySiteidlabel"><em>id <%=studySiteID%></em><p>

      <table class="studySite-field-table edit-table">
        <%

        if (sitey.getName()!=null) {
          try {
            System.out.println("sitey has a name; let's see if I can query on it");
            StudySite sitey2 = myShepherd.getStudySiteByName(sitey.getName());
            System.out.println("sitey2 exists = "+(sitey2!=null));
            System.out.println("sitey2 id = "+sitey2.getID());
            System.out.println("sitey2 name = "+sitey2.getName());

          }
          catch (Exception e) {
            System.out.println("Exception on getStudySiteByName!");
            e.printStackTrace();
          }
        }

        Method locationIDMeth = sitey.getClass().getMethod("getLocationID");

        Method epsgCodeMeth = sitey.getClass().getMethod("getEpsgProjCode");

        ArrayList<String> possLocationsAList = CommonConfiguration.getSequentialPropertyValues("locationID", context);
        String[] possLocations = possLocationsAList.toArray(new String[possLocationsAList.size()]);

        //ClassEditTemplate.printOutClassFieldModifierRow((Object) sitey, locationIDMeth, possLocations, out);

        // TODO: handle epsgProjCode with select and options, like possLocations above




        for (String getterName : studySiteFieldGetters1) {
          Method studySiteMeth = sitey.getClass().getMethod(getterName);
          if (ClassEditTemplate.isDisplayableGetter(studySiteMeth)) {
            ClassEditTemplate.printOutClassFieldModifierRow((Object) sitey, studySiteMeth, out);
          }
        }

        ClassEditTemplate.printOutClassFieldModifierRow((Object) sitey, epsgCodeMeth, epsgCodes, out);

        for (String getterName : studySiteFieldGetters2) {
          Method studySiteMeth = sitey.getClass().getMethod(getterName);
          if (ClassEditTemplate.isDisplayableGetter(studySiteMeth)) {
            ClassEditTemplate.printOutClassFieldModifierRow((Object) sitey, studySiteMeth, out);
          }
        }


        for (String getterName : studySiteFieldDTGetters) {
          Method studySiteMeth = sitey.getClass().getMethod(getterName);
          if (ClassEditTemplate.isDisplayableGetter(studySiteMeth)) {
            ClassEditTemplate.printOutDateTimeModifierRow((Object) sitey, studySiteMeth, out);
          }
        }

        %>
      </table>
    </hr>
    <div class="submit" style="position:relative">
      <input type="submit" name="save" value="Save" />
      <span class="note" style="position:absolute;bottom:9"></span>
    </div>
</form>
</div>

<div class="col-sm-6">

<h2> Map </h2>
<em>
<%=stuprops.getProperty("projectionComment")%>
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
    var mapZoom = 1;
    var center = new google.maps.LatLng(0, 0);
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
  var latLng = new google.maps.LatLng(<%=sitey.getLatitude()%>, <%=sitey.getLongitude()%>);


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
  if((sitey.getLatitude()==null)&&(sitey.getLongitude()==null)){
  %>  marker.setVisible(false); <%
  } %>

  markers.push(marker);
  google.maps.event.addDomListener(window, 'load', initialize);
</script>

<%
if((request.getUserPrincipal()!=null)){
%>
  <div id="map_canvas" style="width: 510px; height: 350px; overflow: hidden;"></div>
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
  if(sitey.getLatitude()!=null){laty=sitey.getLatitude().toString();}
  if(sitey.getLongitude()!=null){longy=sitey.getLongitude().toString();}

%>



    <a name="gps"></a>
    <div>
      <br>
      <div class="highlight resultMessageDiv" id="gpsErrorDiv"></div>

      <br/>
    </div>
<br /> <br />
</div>





<style>

  table.studySite-field-table {
    table-layout: fixed;
    margin-bottom: 2em;
  }

</style>





<jsp:include page="../footer.jsp" flush="true"/>
