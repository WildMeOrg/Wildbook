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
    java.util.Arrays,
    java.lang.reflect.Method,
    org.ecocean.security.Collaboration" %>


<jsp:include page="header.jsp" flush="true"/>
<!-- IMPORTANT style import for table printed by ClassEditTemplate.java -->
<link rel="stylesheet" href="css/classEditTemplate.css" />
<script src="javascript/timepicker/jquery-ui-timepicker-addon.js"></script>
<link type='text/css' rel='stylesheet' href='javascript/timepicker/jquery-ui-timepicker-addon.css' />
<script type="text/javascript" src="javascript/classEditTemplate.js"></script>

<%

  String context="context0";
  context=ServletUtilities.getContext(request);
  Shepherd myShepherd = new Shepherd(context);
  String mapKey = CommonConfiguration.getGoogleMapsKey(context);


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
  String[] epsgCodesArr = GeocoordConverter.epsgCodes();
  List<String> epsgCodes = Arrays.asList(epsgCodesArr);

%>

<div class="container maincontent">
    <div class="row">
        <form onsubmit="return confirm('<%=stuprops.getProperty("confirmMerge") %>');" name="mergeStudySites" class="editFormMeta" method="post" action="../StudySiteMerge">
            <h1><%=stuprops.getProperty("mergeHeader") %></h1>
            <hr>
            <p><%= stuprops.getProperty("mergeInstructions") %></p>
            <br>
            <div class="col-sm-6 col-xs-12">
                <h3><%= stuprops.getProperty("StudySite") %> 1</h3>
                <br>
                <input name="studySiteId1" type="text"/>
            </div>
            <div class="col-sm-6 col-xs-12">
                <h3><%= stuprops.getProperty("StudySite") %> 2</h3>
                <br>
                <input name="studySiteId2" type="text"/>
            </div>
            <div class="col-sm-6">
                <br>
                <input name="mergeStudySites" type="submit" class="" id="mergeButton" value="<%=stuprops.getProperty("mergeStudySite") %>" />
            </div>
        </form>
    </div>
</div>

<jsp:include page="footer.jsp" flush="true"/>
