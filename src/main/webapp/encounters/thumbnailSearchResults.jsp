f<%@ page contentType="text/html; charset=utf-8"
		language="java"
 		import="org.ecocean.servlet.ServletUtilities,javax.jdo.Query,com.drew.imaging.jpeg.JpegMetadataReader,com.drew.metadata.Metadata, com.drew.metadata.Tag, org.ecocean.mmutil.MediaUtilities,org.ecocean.*,java.io.File, java.util.*,org.ecocean.security.Collaboration, java.io.FileInputStream, javax.jdo.Extent" %>


  <%

  String context="context0";
  context=ServletUtilities.getContext(request);

  //setup data dir
  String rootWebappPath = getServletContext().getRealPath("/");
  File webappsDir = new File(rootWebappPath).getParentFile();
  File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));
  //if(!shepherdDataDir.exists()){shepherdDataDir.mkdirs();}
  File encountersDir=new File(shepherdDataDir.getAbsolutePath()+"/encounters");
  //if(!encountersDir.exists()){encountersDir.mkdirs();}

	int encounterIncrementer=10;
  
    int startNum = 0;
    int endNum = encounterIncrementer;

    try {

      if (request.getParameter("startNum") != null) {
        startNum = (new Integer(request.getParameter("startNum"))).intValue();
      }
      if (request.getParameter("endNum") != null) {
        endNum = (new Integer(request.getParameter("endNum"))).intValue();
      }

    } catch (NumberFormatException nfe) {
      startNum = 0;
      endNum = encounterIncrementer;
    }


//let's load thumbnailSearch.properties
    //String langCode = "en";
    String langCode=ServletUtilities.getLanguageCode(request);


    Properties encprops = new Properties();
    //encprops.load(getClass().getResourceAsStream("/bundles/" + langCode + "/thumbnailSearchResults.properties"));
    encprops = ShepherdProperties.getProperties("thumbnailSearchResults.properties", langCode, context);

    //Shepherd myShepherd = new Shepherd(context);
    //myShepherd.setAction("thumbnailSearchResults.jsp");

    List<SinglePhotoVideo> rEncounters = new ArrayList<SinglePhotoVideo>();

    //myShepherd.beginDBTransaction();
    //EncounterQueryResult queryResult = new EncounterQueryResult(new Vector<Encounter>(), "", "");

  	StringBuffer prettyPrint=new StringBuffer("");
  	Map<String,Object> paramMap = new HashMap<String, Object>();



    String[] keywords = request.getParameterValues("keyword");
    if (keywords == null) {
      keywords = new String[0];
    }

		List<Collaboration> collabs = Collaboration.collaborationsForCurrentUser(request);


    //if (request.getParameter("noQuery") == null) {


    	String queryString=EncounterQueryProcessor.queryStringBuilder(request, prettyPrint, paramMap);



  %>
 <jsp:include page="../header.jsp" flush="true"/>

  <!--
    1 ) Reference to the files containing the JavaScript and CSS.
    These files must be located on your server.
  -->

  <script type="text/javascript" src="../highslide/highslide/highslide-with-gallery.js"></script>
  <link rel="stylesheet" type="text/css" href="../highslide/highslide/highslide.css"/>

  <!--
    2) Optionally override the settings defined at the top
    of the highslide.js file. The parameter hs.graphicsDir is important!
  -->

  <script type="text/javascript">
  hs.graphicsDir = '../highslide/highslide/graphics/';
  hs.align = 'auto';
  hs.showCredits = false;
  hs.anchor = 'top';

  //transition behavior
  hs.transitions = ['expand', 'crossfade'];
  hs.outlineType = 'rounded-white';
  hs.fadeInOut = true;
  hs.transitionDuration = 0;
  hs.expandDuration = 0;
  hs.restoreDuration = 0;
  hs.numberOfImagesToPreload = 15;
  hs.dimmingDuration = 0;

  // define the restraining box
  hs.useBox = true;
  hs.width = 810;
  hs.height=250;

    //block right-click user copying if no permissions available
    <%
    if(request.getUserPrincipal()==null){
    %>
    hs.blockRightClick = true;
    <%
    }
    %>

    // Add the controlbar
    hs.addSlideshow({
      //slideshowGroup: 'group1',
      interval: 5000,
      repeat: false,
      useControls: true,
      fixedControls: 'fit',
      overlayOptions: {
        opacity: 0.75,
        position: 'bottom center',
        hideOnMouseOut: true
      }
    });

  </script>
</head>
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

  div.scroll {
    height: 200px;
    overflow: auto;
    border: 1px solid #666;
    background-color: #ccc;
    padding: 8px;
  }
</style>


<div class="container maincontent">

<%
  String rq = "";
  if (request.getQueryString() != null) {
    rq = request.getQueryString();
  }
  if (request.getParameter("noQuery") == null) {
%>

<table width="810px" border="0" cellspacing="0" cellpadding="0">
  <tr>
    <td>
      <p>

      <h1 class="intro"><%=encprops.getProperty("title")%>
      </h1>
      </p>

    </td>
  </tr>
</table>

<ul id="tabmenu">

  <li><a
    href="searchResults.jsp?<%=rq.replaceAll("startNum","uselessNum").replaceAll("endNum","uselessNum") %>"><%=encprops.getProperty("table")%>
  </a></li>
  <li><a class="active"><%=encprops.getProperty("matchingImages")%>
  </a></li>
  <li><a
    href="mappedSearchResults.jsp?<%=rq.replaceAll("startNum","uselessNum").replaceAll("endNum","uselessNum") %>"><%=encprops.getProperty("mappedResults")%>
  </a></li>
  <li><a
    href="../xcalendar/calendar2.jsp?<%=rq.replaceAll("startNum","uselessNum").replaceAll("endNum","uselessNum") %>"><%=encprops.getProperty("resultsCalendar")%>
  </a></li>
        <li><a
     href="searchResultsAnalysis.jsp?<%=request.getQueryString() %>"><%=encprops.getProperty("analysis")%>
   </a></li>
 <li><a
     href="exportSearchResults.jsp?<%=request.getQueryString() %>"><%=encprops.getProperty("export")%>
   </a></li>
</ul>
<%
  }
%>
<br>
<p><%=encprops.getProperty("belowMatches")%> <%=encprops.getProperty("thatMatched")%>
      </p>

<%
  String qString = rq;
  int startNumIndex = qString.indexOf("&startNum");
  if (startNumIndex > -1) {
    qString = qString.substring(0, startNumIndex);
  }

%>
<table width="100%">
  <tr>
    <%
      if (startNum > 1) {
      %>
    <td align="left">
      <p>
      <a
        href="thumbnailSearchResults.jsp?<%=qString%>&startNum=<%=(startNum-encounterIncrementer)%>&endNum=<%=(startNum-1)%>"><img
        src="../images/Black_Arrow_left.png" width="28" height="28" border="0" align="absmiddle"
        title="<%=encprops.getProperty("seePreviousResults")%>"/> <%=encprops.getProperty("previous")%></a>
         
      </p>
    </td>
    <%
      }
    %>
    <td align="right">
      <p><a
        href="thumbnailSearchResults.jsp?<%=qString%>&startNum=<%=startNum+10%>&endNum=<%=endNum+10%>">
        <%=encprops.getProperty("next")%> <img
        src="../images/Black_Arrow_right.png" width="28" height="28" border="0" align="absmiddle"
        title="<%=encprops.getProperty("seePreviousResults")%>"/>
      </a>
      </p>
    </td>
  </tr>
</table>



        <jsp:include page="encounterMediaGallery.jsp" flush="true">
					<jsp:param name="grid" value="true" />
        	<jsp:param name="queryString" value="<%=queryString %>" />
        	<jsp:param name="rangeStart" value="<%=startNum %>" />
        	<jsp:param name="rangeEnd" value="<%=endNum %>" />

        </jsp:include>

<%


  startNum = startNum + encounterIncrementer;
  endNum = endNum + encounterIncrementer;

%>

<table width="100%">
  <tr>
    <%
      if ((startNum - encounterIncrementer) > 1) {%>
    <td align="left">
      <p><a
        href="thumbnailSearchResults.jsp?<%=qString%>&startNum=<%=(startNum-90)%>&endNum=<%=(startNum-46)%>"><img
        src="../images/Black_Arrow_left.png" width="28" height="28" border="0" align="absmiddle"
        title="<%=encprops.getProperty("seePreviousResults")%>"/> <%=encprops.getProperty("previous")%></a>
        </p>
    </td>
    <%
      }
    %>
    <td align="right">
      <p><a
        href="thumbnailSearchResults.jsp?<%=qString%>&startNum=<%=startNum%>&endNum=<%=endNum%>"><%=encprops.getProperty("next")%> <img
        src="../images/Black_Arrow_right.png" border="0" align="absmiddle"
        title="<%=encprops.getProperty("seeNextResults")%>"/></a></p>
    </td>
  </tr>
</table>


</div>

<!--db: These are the necessary tools for photoswipe.-->
<%
String urlLoc = "//" + CommonConfiguration.getURLLocation(request);
String pswipedir = urlLoc+"/photoswipe";
%>
<link rel='stylesheet prefetch' href='<%=pswipedir %>/photoswipe.css'>
<link rel='stylesheet prefetch' href='<%=pswipedir %>/default-skin/default-skin.css'>
<!--  <p>Looking for photoswipe in <%=pswipedir%></p>-->
<jsp:include page="../photoswipe/photoswipeTemplate.jsp" flush="true"/>
<script src='<%=pswipedir%>/photoswipe.js'></script>
<script src='<%=pswipedir%>/photoswipe-ui-default.js'></script>
<jsp:include page="../footer.jsp" flush="true"/>
