
<%@ page contentType="text/html; charset=utf-8" language="java"
         import="com.drew.imaging.jpeg.JpegMetadataReader, com.drew.metadata.Directory, com.drew.metadata.Metadata, com.drew.metadata.Tag, org.ecocean.*,org.ecocean.servlet.ServletUtilities,org.ecocean.Util,org.ecocean.Measurement, org.ecocean.Util.*, org.ecocean.genetics.*, org.ecocean.tag.*, java.awt.Dimension, javax.jdo.Extent, javax.jdo.Query, java.io.File, java.text.DecimalFormat, java.util.*" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>         



<%

//get encounter number
String num = request.getParameter("number").replaceAll("\\+", "").trim();
String context="context0";
context=ServletUtilities.getContext(request);


//let's set up references to our file system components
String rootWebappPath = getServletContext().getRealPath("/");
String baseDir = ServletUtilities.dataDir(context, rootWebappPath);
/*
File webappsDir = new File(rootWebappPath).getParentFile();
File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName());
File encountersDir=new File(shepherdDataDir.getAbsolutePath()+"/encounters");
File encounterDir = new File(encountersDir, num);
File thisEncounterDir = new File(imageEnc.dir(baseDir));
String encUrlDir = "/" + CommonConfiguration.getDataDirectoryName(context) + imageEnc.dir("");
*/

String crExistsUrl = null;

  //GregorianCalendar cal = new GregorianCalendar();
  //int nowYear = cal.get(1);


//handle some cache-related security
  response.setHeader("Cache-Control", "no-cache"); //Forces caches to obtain a new copy of the page from the origin server
  response.setHeader("Cache-Control", "no-store"); //Directs caches not to store the page under any circumstance
  response.setDateHeader("Expires", 0); //Causes the proxy cache to see the page as "stale"
  response.setHeader("Pragma", "no-cache"); //HTTP 1.0 backward compatibility

//gps decimal formatter
  DecimalFormat gpsFormat = new DecimalFormat("###.####");

//handle translation
  String langCode = "en";

  //check what language is requested
  if (session.getAttribute("langCode") != null) {
    langCode = (String) session.getAttribute("langCode");
  }


//let's load encounters.properties
  //Properties encprops = new Properties();
  //encprops.load(getClass().getResourceAsStream("/bundles/" + langCode + "/encounter.properties"));

  Properties encprops = ShepherdProperties.getProperties("encounter.properties", langCode);


  pageContext.setAttribute("num", num);


  Shepherd myShepherd = new Shepherd(context);
  Extent allKeywords = myShepherd.getPM().getExtent(Keyword.class, true);
  Query kwQuery = myShepherd.getPM().newQuery(allKeywords);
  boolean proceed = true;
  boolean haveRendered = false;

  pageContext.setAttribute("set", encprops.getProperty("set"));

if (request.getParameter("number")!=null) {
	
		if(myShepherd.isEncounter(num)){
			Encounter metaEnc = myShepherd.getEncounter(num);
			int numImgs=metaEnc.getImages().size();
			if((metaEnc.getImages()!=null)&&(numImgs>0)){
				for(int b=0;b<numImgs;b++){
				SinglePhotoVideo metaSPV=metaEnc.getImages().get(b);

			}
		}
		}
}
%>

  <style type="text/css">
    <!--

    .style2 {
      color: #000000;
      font-size: small;
    }

    .style3 {
      font-weight: bold
    }

    .style4 {
      color: #000000
    }

    table.adopter {
      border-width: 1px 1px 1px 1px;
      border-spacing: 0px;
      border-style: solid solid solid solid;
      border-color: black black black black;
      border-collapse: separate;
      background-color: white;
    }

    table.adopter td {
      border-width: 1px 1px 1px 1px;
      padding: 3px 3px 3px 3px;
      border-style: none none none none;
      border-color: gray gray gray gray;
      background-color: white;
      -moz-border-radius: 0px 0px 0px 0px;
      font-size: 12px;
      color: #330099;
    }

    table.adopter td.name {
      font-size: 12px;
      text-align: center;
    }

    table.adopter td.image {
      padding: 0px 0px 0px 0px;
    }

    div.scroll {
      height: 200px;
      overflow: auto;
      border: 1px solid #666;
      background-color: #ccc;
      padding: 8px;
    }

    -->




th.measurement{
	 font-size: 0.9em;
	 font-weight: normal;
	 font-style:italic;
}

td.measurement{
	 font-size: 0.9em;
	 font-weight: normal;
}

</style>



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

  .ui-dialog-titlebar-close { display: none; }
  code { font-size: 2em; }

</style>

<jsp:include page="../header.jsp" flush="true"/>

<link href="../css/cr.css" rel="stylesheet" type="text/css" />
<script src="../javascript/cr.js"></script>


<script>
	var encounterNumber = false;
</script>



<div class="container maincontent">
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

						List<SinglePhotoVideo> spvs = myShepherd.getAllSinglePhotoVideosForEncounter(enc.getCatalogNumber());
						//String dataDir = CommonConfiguration.getDataDirectoryName() + "/encounters/" + num;
						String dataDir = CommonConfiguration.getDataDirectoryName(context) + enc.dir("");

						String filename = request.getParameter("filename");

						SinglePhotoVideo match = null;

						for (SinglePhotoVideo s : spvs) {
							if (myShepherd.isAcceptableImageFile(s.getFilename())) {
								if (s.getFilename().equals(filename)) match = s;
							}
						}
						if (match == null) match = spvs.get(0);

						if (enc.getMmaCompatible()) {
							File tryCR = new File(match.getFullFileSystemPath().replaceFirst(".([^.]+)$", "_CR.$1"));
							if (tryCR.exists()) crExistsUrl = match.getFilename().replaceFirst(".([^.]+)$", "_CR.$1");
						}

						String imgUrl = "";
						String matchFilename = "";
						String matchSPVID = "";
						if (match != null) {
							matchFilename = match.getFilename();
							matchSPVID=match.getDataCollectionEventID();
							imgUrl = "/" + dataDir + "/" + matchFilename;
							//List k = match.getKeywords();
						}

						if (crExistsUrl != null) {
							crExistsUrl = "/" + dataDir + "/" + crExistsUrl;
						}

    			%>
 

<script>
	encounterNumber = '<%=num%>';

	$('body').ready(function() {
		CRtool.init({
			imgEl: document.getElementById('cr-work-img'),
			wCanvas: document.getElementById('cr-work-canvas'),
			oCanvas: document.getElementById('cr-overlay-canvas'),
			infoEl: document.getElementById('cr-info'),
			controlEl: document.getElementById('cr-info')
		});

		//sometimes .toWork() doesnt fire via .init() above.  wtf?  TODO fix!
		window.setTimeout(function() { CRtool.toWork(); }, 1100);
	});


/* note: we now actually try jpeg, as file size is much smaller. but it will just return png if browser does not support jpeg
   either way, we still have to check for data-size limitations (currently hardcoded at 2M which is tomcat default. */
	function crSave() {
		var cvs = document.createElement('canvas');  //only used if we need to shrink it
		var scale = 1;
		var maxSize = 2000000;

		var base64 = document.getElementById('cr-work-canvas').toDataURL("image/jpeg");
		var i = base64.indexOf(';base64,');
		if (i > -1) base64 = base64.substr(i + 8);
console.log('1) base64.length = ' + base64.length);
		while (base64.length > maxSize) {
			scale *= 0.8;
			CRtool.scaleCanvas(scale, CRtool.wCanvas, cvs);
			base64 = cvs.toDataURL("image/jpeg");
			var i = base64.indexOf(';base64,');
			if (i > -1) base64 = base64.substr(i + 8);
console.log(scale + ') base64.length = ' + base64.length);
		}
		$('#cr-form input[name="pngData"]').val(base64);
/*
console.log('<%=matchFilename%>');
console.log('have base64 to send to server for ' + encounterNumber);
*/
		$('#cr-form').submit();
	}

</script>

<div style="position: relative; padding: 10px; background-color: #888; width: 100%; height: 400px;">
	<img id="cr-work-img" src="<%=imgUrl%>" />
	<canvas id="cr-overlay-canvas" style="display: none;" ></canvas>
	<canvas id="cr-work-canvas"  ></canvas>
	<!-- <div id="cr-info"></div> -->
	<div id="cr-controls">
		<input type="button" value="Save as Candidate Region" onClick="crSave()" />
	</div>
</div>

<form method="POST" id="cr-form" action="../EncounterAddMantaPattern" >
	<input type="hidden" name="encounterID" value="<%=num%>" />
	<input type="hidden" name="matchFilename" value="<%=matchFilename%>" />
	<input type="hidden" name="photoNumber" value="<%=matchSPVID%>" />
	<input type="hidden" name="action" value="imageadd2" />
	<input type="hidden" name="pngData" value="" />
</form>


<% if (crExistsUrl != null) { %>
<div style="width: 100%; padding: 10px; background-color: #FCB; min-height: 110px; margin-top: 15px;">
	<img src="<%=crExistsUrl%>" style="float: left; max-height: 90px; margin: 8px;" />
	<div style="padding-top: 20px;"><b>Note: a Candidate Region image already exists.  Saving will overwrite this.</b></div>
</div>
<%
}

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


<form action="encounter.jsp" method="post" name="encounter"><strong>Go
  to encounter: </strong> <input name="number" type="text" value="<%=num%>" size="20"> <input name="Go" type="submit" value="Submit" /></form>


<p><font color="#990000"><a href="../individualSearchResults.jsp">View all individuals</a></font></p>


<%
}
%>


</div>



<jsp:include page="../footer.jsp" flush="true"/>



