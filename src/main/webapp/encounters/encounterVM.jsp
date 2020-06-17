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
<%@ page contentType="text/html; charset=utf-8" language="java" %>
<%@ page import="java.text.DecimalFormat" %>
<%@ page import="java.util.*" %>
<%@ page import="javax.jdo.Extent" %>
<%@ page import="javax.jdo.Query" %>
<%@ page import="org.ecocean.*" %>
<%@ page import="org.ecocean.servlet.ServletUtilities" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>         


<%
  String context="context0";
  context=ServletUtilities.getContext(request);

  // Get encounter number
  String num = request.getParameter("number").replaceAll("\\+", "").trim();
  String mediaAssetId = request.getParameter("mediaAssetId");

  // Set up references to our file system components
  String rootWebappPath = getServletContext().getRealPath("/");
  String baseDir = ServletUtilities.dataDir(context, rootWebappPath);
/*
  File webappsDir = new File(rootWebappPath).getParentFile();
  File encounterDir = new File(imageEnc.dir(baseDir));
  File encounterDir = new File(encountersDir, num);
  String rootWebappPath = getServletContext().getRealPath("/");
  String encUrlDir = "/" + CommonConfiguration.getDataDirectoryName(context) + imageEnc.dir("");
*/

  //GregorianCalendar cal = new GregorianCalendar();
  //int nowYear = cal.get(1);

  // Handle some cache-related security
  response.setHeader("Cache-Control", "no-cache"); //Forces caches to obtain a new copy of the page from the origin server
  response.setHeader("Cache-Control", "no-store"); //Directs caches not to store the page under any circumstance
  response.setDateHeader("Expires", 0); //Causes the proxy cache to see the page as "stale"
  response.setHeader("Pragma", "no-cache"); //HTTP 1.0 backward compatibility

  // GPS decimal formatter
  DecimalFormat gpsFormat = new DecimalFormat("###.####");

  // Handle translation
  String langCode = ServletUtilities.getLanguageCode(request);
  Properties encprops = ShepherdProperties.getProperties("encounter.properties", langCode);
  Properties vmProps = ShepherdProperties.getProperties("visualMatcher.properties", langCode);

  pageContext.setAttribute("num", num);

  Shepherd myShepherd = new Shepherd(context);
  myShepherd.setAction("encounterVM.jsp");
  Extent allKeywords = myShepherd.getPM().getExtent(Keyword.class, true);
  Query kwQuery = myShepherd.getPM().newQuery(allKeywords);
  boolean proceed = true;
  boolean haveRendered = false;

  pageContext.setAttribute("set", encprops.getProperty("set"));
%>



<jsp:include page="../header.jsp" flush="true"/>


<link href="../css/encounterVM.css" rel="stylesheet" type="text/css" />
<script src="../javascript/encounterVM.js"></script>


<script>
        var mediaAssetId = <%=((mediaAssetId == null) ? "false" : mediaAssetId)%>;
	var patterningCodes = [
<%
    List<String> pcodes = myShepherd.getAllPatterningCodes();
    for (String c : pcodes) {
        if ((c == null) || c.equals("")) continue;
        out.println("'" + c + "',\n");
    }
%>
	];

<%
    /* note: used to use commonconfig, e.g.
		String l = CommonConfiguration.getProperty("locationID" + i, context);
        but now getting this way (from encounter fields)
    */
    String locs = "";
    List<String> lids = myShepherd.getAllLocationIDs();
    for (String l : lids) {
        locs += "\t'" + l + "',\n";
    }
%>

	var regions = [
<%=locs%>];

	var encounterNumber = false;
	$('body').ready(function() {
		initVM($('#vm-content'), encounterNumber);
	});
</script>

<div class="container maincontent">


<div id="candidate-full-zoom"></div>


			<%
  			myShepherd.beginDBTransaction();

  			if (myShepherd.isEncounter(num)) {
    			try {

      			Encounter enc = myShepherd.getEncounter(num);
      			pageContext.setAttribute("enc", enc);
				int numImages=myShepherd.getAllSinglePhotoVideosForEncounter(enc.getCatalogNumber()).size();
      
				//let's see if this user has ownership and can make edits
      			boolean isOwner = ServletUtilities.isUserAuthorizedForEncounter(enc, request);
      			pageContext.setAttribute("editable", isOwner && CommonConfiguration.isCatalogEditable(context));
      			boolean loggedIn = false;
      			try{
      				if(request.getUserPrincipal()!=null){loggedIn=true;}
      			}
      			catch(NullPointerException nullLogged){}
      			
      			String headerBGColor="FFFFFC";
      			//if(CommonConfiguration.getProperty(()){}
    			%>
 

<script>encounterNumber = '<%=enc.getCatalogNumber()%>';</script>
<div id="vm-content"></div>

<div>
	<span id="zoom-message"></span>
</div>



<%

kwQuery.closeAll();
myShepherd.rollbackDBTransaction();
myShepherd.closeDBTransaction();
kwQuery=null;
myShepherd=null;

}
catch(Exception e){
	e.printStackTrace();
	%>
	<p>Hit an error.<br /> <%=e.toString()%></p>


<%
}

	}  //end if this is an encounter
    else {
  		myShepherd.rollbackDBTransaction();
  		myShepherd.closeDBTransaction();
		%>
		<p class="para">There is no encounter #<%=num%> in the database. Please double-check the encounter number and try again.</p>

<%
}
%>


</div>



<jsp:include page="../footer.jsp" flush="true"/>




