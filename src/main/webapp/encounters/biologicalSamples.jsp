<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.joda.time.format.DateTimeFormat,
         org.joda.time.format.DateTimeFormatter,
         org.joda.time.LocalDateTime,
         java.util.Locale,
         java.math.BigDecimal,
         java.math.RoundingMode,
         org.ecocean.servlet.ServletUtilities,
         com.drew.imaging.jpeg.JpegMetadataReader,
         com.drew.metadata.Directory,
         com.drew.metadata.Metadata,
         com.drew.metadata.Tag,
         org.ecocean.*,
         org.ecocean.media.MediaAsset,
         java.util.regex.Pattern,
         org.ecocean.servlet.ServletUtilities,
         org.ecocean.Util,org.ecocean.Measurement,
         org.ecocean.Util.*, org.ecocean.genetics.*,
         org.ecocean.servlet.importer.ImportTask,
         org.ecocean.tag.*, java.awt.Dimension,
         org.json.JSONObject,
         org.json.JSONArray,
         org.ecocean.ia.WbiaQueueUtil,
         javax.jdo.Extent, javax.jdo.Query,
         java.io.File, java.text.DecimalFormat,
         org.ecocean.servlet.importer.ImportTask,
         org.apache.commons.lang3.StringEscapeUtils,
         org.apache.commons.codec.net.URLCodec,
         org.ecocean.metrics.Prometheus,
         java.util.*,org.ecocean.security.Collaboration" %>
<%@ page import="org.ecocean.shepherd.core.Shepherd" %>
<%@ page import="org.ecocean.shepherd.core.ShepherdProperties" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>

<%!
    //note: locIds is modified, such that it contains all the IDs we traversed
    private static String traverseLocationIdTree(final JSONObject locIdTree, List<String> locIds, final String encLocationId, final Map<String,Long> locCount) {
        String rtn = "";
        if (locIdTree == null) return rtn;  //snh

        boolean isRoot = locIdTree.optBoolean("_isRoot", false);
        String id = locIdTree.optString("id", null);
        if (!isRoot && (id == null)) throw new RuntimeException("LocationID tree is missing IDs in sub-tree: " + locIdTree);
        if (id != null) {
            if (!locIds.contains(id)) locIds.add(id);
            boolean active = id.equals(encLocationId);
            long ct = 0;
            if (locCount.get(id) != null) ct = locCount.get(id);
            String name = locIdTree.optString("name", id);
            String desc = locIdTree.optString("description", null);
            if (desc == null) {
                desc = "";
            } else {
                desc = " title=\"" + desc.replaceAll("'", "\\'") + "\" ";
            }
            rtn += "<li class=\"item\">";
            rtn += "<input id=\"mfl-" + id + "\" name=\"match-filter-location-id\" value=\"" + id + "\" type=\"checkbox\"" + (active ? " checked " : "") + " />";
            rtn += "<label " + desc + (active ? "class=\"item-checked\"" : "") + " for=\"mfl-" + id + "\">" + name + " <span class=\"item-count\">" + ct + "</span></label>";
        }

        List<String> kidVals = new ArrayList<String>();
        JSONArray kids = locIdTree.optJSONArray("locationID");
        if (kids != null) for (int i = 0 ; i < kids.length() ; i++) {
            JSONObject k = kids.optJSONObject(i);
            if (k == null) continue;
            String kval = traverseLocationIdTree(k, locIds, encLocationId, locCount);
            if (!kval.equals("")) kidVals.add(kval);
        }
        if (kidVals.size() > 0) rtn += "<ul class=\"ul-secondary\">" + String.join("\n", kidVals) + "</ul>";

        if (id != null) rtn += "</li>";
        return rtn;
    }

  //shepherd must have an open trasnaction when passed in
  public String getNextIndividualNumber(Encounter enc, Shepherd myShepherd, String context) {
    String returnString = "";
    try {
      String lcode = enc.getLocationCode();
      if ((lcode != null) && (!lcode.equals(""))) {

        //let's see if we can find a string in the mapping properties file
        Properties props = new Properties();
        //set up the file input stream
        //props.load(getClass().getResourceAsStream("/bundles/newIndividualNumbers.properties"));
        props= ShepherdProperties.getProperties("newIndividualNumbers.properties", "",context);
		System.out.println("Trying to find locationID code");
        //let's see if the property is defined
        if (props.getProperty(lcode) != null) {
          returnString = escapeSpecialRegexChars(props.getProperty(lcode));

          String nextID=MultiValue.nextUnusedValueForKey("*",returnString, myShepherd, "%03d");
          System.out.println("nextID: "+nextID);

          return nextID;
        }
      }
		return returnString;
    }
    catch (Exception e) {
      e.printStackTrace();
      return returnString;
    }
  }

	/*
	public boolean checkAccessKey(HttpServletRequest request, Encounter enc) {
		if ((request == null) || (enc == null)) return false;
		JSONObject jobj = new JSONObject();
		String accessKey = request.getParameter("accessKey");
		if (accessKey == null) return false;
		for (MediaAsset ma : enc.getMedia()) {
			JSONObject p = ma.getParameters();
			if (p == null) return false;
			if (!accessKey.equals(p.optString("accessKey", null))) return false;
		}
		return true;
	}
	*/

	private String escapeSpecialRegexChars(String str) {
		Pattern SPECIAL_REGEX_CHARS = Pattern.compile("[{}()\\[\\].+*?^$\\\\|]");
	    return SPECIAL_REGEX_CHARS.matcher(str).replaceAll("\\\\$0");
	}
%>
<link type='text/css' rel='stylesheet' href='../javascript/timepicker/jquery-ui-timepicker-addon.css' />

<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Material+Symbols+Outlined:opsz,wght,FILL,GRAD@48,400,1,0" />

<!-- Select2 CSS -->
<link href="https://cdn.jsdelivr.net/npm/select2@4.1.0-beta.1/dist/css/select2.min.css" rel="stylesheet" />

<!-- jQuery -->
<script src="https://ajax.googleapis.com/ajax/libs/jquery/3.5.1/jquery.min.js"></script>

<!-- Select2 JS -->
<script src="https://cdnjs.cloudflare.com/ajax/libs/select2/4.0.8/js/select2.min.js" defer></script>

<%
String context="context0";
context=ServletUtilities.getContext(request);
//get encounter number
String num = request.getParameter("number").replaceAll("\\+", "").trim();

//let's set up references to our file system components
String rootWebappPath = getServletContext().getRealPath("/");
File webappsDir = new File(rootWebappPath).getParentFile();
File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));
File encountersDir=new File(shepherdDataDir.getAbsolutePath()+"/encounters");
File encounterDir = new File(encountersDir, num);

//basic Encounter permissions: false by default
boolean isOwner=false;
boolean encounterIsPublic=false;

  GregorianCalendar cal = new GregorianCalendar();
  int nowYear = cal.get(1);

//handle some cache-related security
  response.setHeader("Cache-Control", "no-cache"); //Forces caches to obtain a new copy of the page from the origin server
  response.setHeader("Cache-Control", "no-store"); //Directs caches not to store the page under any circumstance
  response.setDateHeader("Expires", 0); //Causes the proxy cache to see the page as "stale"
  response.setHeader("Pragma", "no-cache"); //HTTP 1.0 backward compatibility

//gps decimal formatter
  DecimalFormat gpsFormat = new DecimalFormat("###.####");

//handle translation
  //String langCode = "en";
String langCode=ServletUtilities.getLanguageCode(request);

// Use to encode special characters. Prompted by occurrence ID link containing ampersand not working.
URLCodec urlCodec = new URLCodec();

//let's load encounters.properties
  //Properties encprops = new Properties();
  //encprops.load(getClass().getResourceAsStream("/bundles/" + langCode + "/encounter.properties"));
  
  pageContext.setAttribute("num", num);

  Shepherd myShepherd = new Shepherd(context);
  myShepherd.setAction("encounter.jsp1");
  //Extent allKeywords = myShepherd.getPM().getExtent(Keyword.class, true);
  //Query kwQuery = myShepherd.getPM().newQuery(allKeywords);
//System.out.println("???? query=" + kwQuery);
  boolean proceed = true;
  boolean haveRendered = false;

  Properties collabProps = new Properties();
  collabProps=ShepherdProperties.getProperties("collaboration.properties", langCode, context);


  String mapKey = CommonConfiguration.getGoogleMapsKey(context);
%>

<jsp:include page="../header.jsp" flush="true"/>

<script src="//maps.google.com/maps/api/js?key=<%=mapKey%>&language=<%=langCode%>"></script>


  <!--
    1 ) Reference to the files containing the JavaScript and CSS.
    These files must be located on your server.
  -->
  <link rel="stylesheet" type="text/css" href="../css/encounterStyles.css">

  <!--
    2) Optionally override the settings defined at the top
    of the highslide.js file. The parameter hs.graphicsDir is important!
  -->

  <script src="../javascript/timepicker/jquery-ui-timepicker-addon.js"></script>

<script src="../javascript/qualityChecks.js"></script>

<script src="../javascript/imageTools.js"></script>
<div class="container maincontent">

<div class="row" id="mainHeader">
  <div class="col-sm-12">
			<%
  			myShepherd.beginDBTransaction();
			Properties encprops = ShepherdProperties.getOrgProperties("encounter.properties", langCode, context, request, myShepherd);
			pageContext.setAttribute("set", encprops.getProperty("set"));

			boolean useCustomProperties = User.hasCustomProperties(request, myShepherd); // don't want to call this a bunch


  			if (myShepherd.isEncounter(num)) {
    			try {

      			Encounter enc = myShepherd.getEncounter(num);
            	//System.out.println("Got encounter "+enc+" with dataSource "+enc.getDataSource()+" and submittedDate "+enc.getDWCDateAdded());
            	String encNum = enc.getCatalogNumber();
            	
            	
				//let's see if this user has ownership and can make edits
      			isOwner = ServletUtilities.isUserAuthorizedForEncounter(enc, request,myShepherd);
            	encounterIsPublic = ServletUtilities.isEncounterOwnedByPublic(enc);
            	boolean encounterCanBeEditedByAnyLoggedInUser = encounterIsPublic && request.getUserPrincipal() != null;
            	pageContext.setAttribute("editable", (isOwner || encounterCanBeEditedByAnyLoggedInUser) && CommonConfiguration.isCatalogEditable(context));
      			boolean loggedIn = false;
      			try{
      				if(request.getUserPrincipal()!=null){loggedIn=true;}
      			}
      			catch(NullPointerException nullLogged){}
            	
            	
				boolean visible = isOwner || encounterIsPublic || encounterCanBeEditedByAnyLoggedInUser || enc.canUserAccess(request);
				System.out.println("visible: "+visible);
				//if (!visible) visible = checkAccessKey(request, enc);
				if (!visible) {

	              // remove any potentially-sensitive data, labeled with the secure-field class
	              %>
	              <script type="text/javascript">
	                $(window).on('load',function() {
	                  $('.secure-field').remove();
	                });
	              </script>
	              <%

					String blocker = "";
					List<Collaboration> collabs = Collaboration.collaborationsForCurrentUser(request);
					Collaboration c = Collaboration.findCollaborationWithUser(enc.getAssignedUsername(), collabs);
					String cmsg = "<p>" + collabProps.getProperty("deniedMessage") + "</p>";
					String uid = null;
					String name = null;
            				String blockerOptions = "overlayCSS: { backgroundColor: '#000', opacity: 1.0, cursor:'wait'}";
					if (request.getUserPrincipal() == null) {
						cmsg = "<p>Access limited.</p>";
					} if ((c == null) || (c.getState() == null)) {
						uid = enc.getAssignedUsername();
						name = enc.getSubmitterName();
						if ((name == null) || name.equals("N/A")) name = enc.getAssignedUsername();
					} else if (c.getState().equals(Collaboration.STATE_INITIALIZED)) {
						cmsg += "<p>" + collabProps.getProperty("deniedMessagePending") + "</p>";
					} else if (c.getState().equals(Collaboration.STATE_REJECTED)) {
						cmsg += "<p>" + collabProps.getProperty("deniedMessageRejected") + "</p>";
					}

					cmsg = cmsg.replace("'", "\\'");
					if (!User.isUsernameAnonymous(uid) && (request.getUserPrincipal() != null)) {
						blocker = "<script>$(window).on('load',function() { $.blockUI({ message: '" + cmsg + "' + _collaborateHtml('" + uid + "', '" + name.replace("'", "\\'") + "') }) });</script>";
					} else {
						blocker = "<script>$(window).on('load',function() { $.blockUI({ message: '<p>" + cmsg + "' + collabBackOrCloseButton() + '</p>' }) });</script>";
					}
					out.println(blocker);
				} //end if !visible


      			pageContext.setAttribute("enc", enc);
      			String livingStatus = "";
      			if ((enc.getLivingStatus()!=null)&&(enc.getLivingStatus().equals("dead"))) {
        			livingStatus = " (deceased)";
      			}

				if (request.getParameter("refreshImages") != null) {
					System.out.println("refreshing images!!! ==========");
					//enc.refreshAssetFormats(context, ServletUtilities.dataDir(context, rootWebappPath));
					enc.refreshAssetFormats(myShepherd);
					System.out.println("============ out ==============");
				}
      			String headerBGColor="FFFFFC";
      			//if(CommonConfiguration.getProperty(()){}
    			%>
    						<%
    						//int stateInt=-1;
    						String classColor="approved_encounters";
							boolean moreStates=true;
							int cNum=0;
							while(moreStates){
	  								String currentLifeState = "encounterState"+cNum;
	  								if(CommonConfiguration.getProperty(currentLifeState,context)!=null){

										if(CommonConfiguration.getProperty(currentLifeState,context).equals(enc.getState())){
											//stateInt=taxNum;
											moreStates=false;
											if(CommonConfiguration.getProperty(("encounterStateCSSClass"+cNum),context)!=null){
												classColor=CommonConfiguration.getProperty(("encounterStateCSSClass"+cNum),context);
											}
										}
										cNum++;
  									}
  									else{
     									moreStates=false;
  									}

								} //end while

				String individuo="<a id=\"topid\">"+encprops.getProperty("unassigned")+"</a>";
				if(enc.hasMarkedIndividual() && enc.getIndividual()!=null) {
          		String dispName = enc.getIndividual().getDisplayName(request, myShepherd);
					individuo=encprops.getProperty("of")+"&nbsp;<a id=\"topid\" href=\"../individuals.jsp?id="+enc.getIndividualID()+"\">" + dispName + "</a>";
				}
    			%>
               	<h1 class="<%=classColor%>" id="headerText">
                	<%=encprops.getProperty("title") %> <%=individuo %></a> <%=livingStatus %>
                </h1>


<% String dup = enc.getDynamicPropertyValue("duplicateOf");  if (dup != null) { %>
<div style="display: inline-block; padding: 1px 5px; background-color: #AAA; color: #833; border-radius: 4px;">This encounter is marked as a <b>duplicate of <a href="encounter.jsp?number=<%=dup%>"><%=dup%></a></b>.</div><% } %>

    			<p class="caption"><em><%=encprops.getProperty("description") %></em></p>
          </div>
        </div>
<!-- end main header row -->

	<!-- main display area -->

				<div class="container">
					<div class="row secure-field">


            <div class="col-xs-12 col-sm-6" style="vertical-align: top;padding-left: 10px;">


  <%-- START RIGHT COLUMN --%>
  <div class="col-xs-12 col-sm-6" style="vertical-align:top">

<!-- 
    <%
      pageContext.setAttribute("showReleaseDate", CommonConfiguration.showReleaseDate(context));
    %>
    
   
     -->    
<!-- 
<%
String queryString="SELECT FROM org.ecocean.Encounter WHERE catalogNumber == \""+num+"\"";
%> -->
    <%-- START IMAGES --%>
<% if (false) { %>
  <div id="add-image-zone" class="bc4">
    <jsp:include page="encounterMediaGallery.jsp" flush="true">
      <jsp:param name="encounterNumber" value="<%=num%>" />
      <jsp:param name="queryString" value="<%=queryString%>" />
      <jsp:param name="isOwner" value="<%=isOwner %>" />
      <jsp:param name="loggedIn" value="<%=loggedIn %>" />
    </jsp:include>

    <% if (isOwner || encounterCanBeEditedByAnyLoggedInUser) { %>
      </div>
    </div>
    <% } %>
  </div>
<% } %>
<%-- END IMAGES --%>



      <%

  String isLoggedInValue="true";
  String isOwnerValue="true";

  if(!loggedIn){isLoggedInValue="false";}
  if(!isOwner){isOwnerValue="false";}

%>
  </div>
</div>
</div>

<!-- end two columns here -->

<%
if(loggedIn){
%>
<script type="text/javascript">
  $(window).on('load',function() {
    $(".addBioSample").click(function() {
      $("#dialogSample").toggle();
    });
  });
</script>


<hr />
<a name="tissueSamples"></a>
<p class="para"><img align="absmiddle" src="../images/microscope.gif" />
    <strong><%=encprops.getProperty("tissueSamples") %></strong>
</p>
    <p class="para">
    	<a class="addBioSample toggleBtn" class="launchPopup toggleBtn"><img align="absmiddle" width="24px" style="border-style: none;" src="../images/Crystal_Clear_action_edit_add.png" /></a>&nbsp;<a class="addBioSample toggleBtn" class="launchPopup"><%=encprops.getProperty("addTissueSample") %></a>
    </p>

<%
if ((isOwner || encounterCanBeEditedByAnyLoggedInUser)  && CommonConfiguration.isCatalogEditable(context)){
%>
<div id="dialogSample" title="<%=encprops.getProperty("setTissueSample")%>" style="display:none">

<form id="setTissueSample" action="../EncounterSetTissueSample" method="post">
<table cellspacing="2" bordercolor="#FFFFFF" >
    <tr>

      	<td>

          <%=encprops.getProperty("sampleID")%> (<%=encprops.getProperty("required")%>)</td><td>
          <%
          TissueSample thisSample=new TissueSample();
          String sampleIDString="";
          if((request.getParameter("edit")!=null)&&(request.getParameter("edit").equals("tissueSample"))&&(request.getParameter("sampleID")!=null) && (request.getParameter("function")!=null) && (request.getParameter("function").equals("1")) &&(myShepherd.isTissueSample(request.getParameter("sampleID"), request.getParameter("number")))){
        	  sampleIDString=request.getParameter("sampleID");
        	  thisSample=myShepherd.getTissueSample(sampleIDString, enc.getCatalogNumber());

          }
          %>
          <input name="sampleID" type="text" size="20" maxlength="100" value="<%=sampleIDString %>" />
        </td>
     </tr>

     <tr>
     	<td>
          <%
          String alternateSampleID="";
          if(thisSample.getAlternateSampleID()!=null){alternateSampleID=thisSample.getAlternateSampleID();}
          %>
          <%=encprops.getProperty("alternateSampleID")%></td><td><input name="alternateSampleID" type="text" size="20" maxlength="100" value="<%=alternateSampleID %>" />
       </td>
   	</tr>

    <tr>
    	<td>
          <%
          String tissueType="";
          if(thisSample.getTissueType()!=null){tissueType=thisSample.getTissueType();}
          %>
          <%=encprops.getProperty("tissueType")%>
       </td>
       <td>
              <%
              if(CommonConfiguration.getProperty("tissueType0",context)==null){
              %>
              <input name="tissueType" type="text" size="20" maxlength="50" />
              <%
              }
              else{
            	  //iterate and find the locationID options
            	  %>
            	  <select name="tissueType" id="tissueType">
						            	<option value=""></option>

						       <%
						       boolean hasMoreLocs=true;
						       int tissueTaxNum=0;
						       while(hasMoreLocs){
						       	  String currentLoc = "tissueType"+tissueTaxNum;
						       	  if(CommonConfiguration.getProperty(currentLoc,context)!=null){

						       		  String selected="";
						       		  if(tissueType.equals(CommonConfiguration.getProperty(currentLoc,context))){selected="selected=\"selected\"";}
						       	  	%>

						       	  	  <option value="<%=CommonConfiguration.getProperty(currentLoc,context)%>" <%=selected %>><%=CommonConfiguration.getProperty(currentLoc,context)%></option>
						       	  	<%
						       		tissueTaxNum++;
						          }
						          else{
						             hasMoreLocs=false;
						          }

						       }
						       %>


						      </select>


            <%
              }
              %>
           </td></tr>

          <tr><td>
          <%
          String preservationMethod="";
          if(thisSample.getPreservationMethod()!=null){preservationMethod=thisSample.getPreservationMethod();}
          %>
          <%=encprops.getProperty("preservationMethod")%></td><td><input name="preservationMethod" type="text" size="20" maxlength="100" value="<%=preservationMethod %>"/>
          </td></tr>

          <tr><td>
          <%
          String storageLabID="";
          if(thisSample.getStorageLabID()!=null){storageLabID=thisSample.getStorageLabID();}
          %>
          <%=encprops.getProperty("storageLabID")%></td><td><input name="storageLabID" type="text" size="20" maxlength="100" value="<%=storageLabID %>"/>
          </td></tr>

          <tr><td>
          <%
          String samplingProtocol="";
          if(thisSample.getSamplingProtocol()!=null){samplingProtocol=thisSample.getSamplingProtocol();}
          %>
          <%=encprops.getProperty("samplingProtocol")%></td><td><input name="samplingProtocol" type="text" size="20" maxlength="100" value="<%=samplingProtocol %>" />
          </td></tr>

          <tr><td>
          <%
          String samplingEffort="";
          if(thisSample.getSamplingEffort()!=null){samplingEffort=thisSample.getSamplingEffort();}
          %>
          <%=encprops.getProperty("samplingEffort")%></td><td><input name="samplingEffort" type="text" size="20" maxlength="100" value="<%=samplingEffort%>"/>
     		</td></tr>

			<tr><td>
          <%
          String fieldNumber="";
          if(thisSample.getFieldNumber()!=null){fieldNumber=thisSample.getFieldNumber();}
          %>
		  <%=encprops.getProperty("fieldNumber")%></td><td><input name="fieldNumber" type="text" size="20" maxlength="100" value="<%=fieldNumber %>" />
          </td></tr>


          <tr><td>
          <%
          String fieldNotes="";
          if(thisSample.getFieldNotes()!=null){fieldNotes=thisSample.getFieldNotes();}
          %>
           <%=encprops.getProperty("fieldNotes")%></td><td><input name="fieldNNotes" type="text" size="20" maxlength="100" value="<%=fieldNotes %>" />
          </td></tr>

          <tr><td>
          <%
          String eventRemarks="";
          if(thisSample.getEventRemarks()!=null){eventRemarks=thisSample.getEventRemarks();}
          %>
          <%=encprops.getProperty("eventRemarks")%></td><td><input name="eventRemarks" type="text" size="20" value="<%=eventRemarks %>" />
          </td></tr>

          <tr><td>
          <%
          String institutionID="";
          if(thisSample.getInstitutionID()!=null){institutionID=thisSample.getInstitutionID();}
          %>
          <%=encprops.getProperty("institutionID")%></td><td><input name="institutionID" type="text" size="20" maxlength="100" value="<%=institutionID %>" />
          </td></tr>


          <tr><td>
          <%
          String collectionID="";
          if(thisSample.getCollectionID()!=null){collectionID=thisSample.getCollectionID();}
          %>
          <%=encprops.getProperty("collectionID")%></td><td><input name="collectionID" type="text" size="20" maxlength="100" value="<%=collectionID %>" />
          </td></tr>

          <tr><td>
          <%
          String collectionCode="";
          if(thisSample.getCollectionCode()!=null){collectionCode=thisSample.getCollectionCode();}
          %>
          <%=encprops.getProperty("collectionCode")%></td><td><input name="collectionCode" type="text" size="20" maxlength="100" value="<%=collectionCode %>" />
          </td></tr>

          <tr><td>
          <%
          String datasetID="";
          if(thisSample.getDatasetID()!=null){datasetID=thisSample.getDatasetID();}
          %>
			<%=encprops.getProperty("datasetID")%></td><td><input name="datasetID" type="text" size="20" maxlength="100" value="<%=datasetID %>" />
          </td></tr>


          <tr><td>
          <%
          String datasetName="";
          if(thisSample.getDatasetName()!=null){datasetName=thisSample.getDatasetName();}
          %>
          <%=encprops.getProperty("datasetName")%></td><td><input name="datasetName" type="text" size="20" maxlength="100" value="<%=datasetName %>" />
			</td></tr>


            <tr><td colspan="2">
            	<input name="encounter" type="hidden" value="<%=num%>" />
            	<input name="action" type="hidden" value="setTissueSample" />
            	<input name="EditTissueSample" type="submit" id="EditTissueSample" value="<%=encprops.getProperty("set")%>" class="btn btn-sm editFormBtn"/>
   			</td></tr>
      </td>
    </tr>
  </table>
</form>
</div>
<%
}

//setup the javascript to handle displaying an edit tissue sample dialog box
if((request.getParameter("sampleID")!=null) && (request.getParameter("edit")!=null) && request.getParameter("edit").equals("tissueSample") && (myShepherd.isTissueSample(request.getParameter("sampleID"), request.getParameter("number")))){
%>
<script>
dlgSample.dialog("open");
</script>

<%
}
%>


<p>
<%
//List<TissueSample> tissueSamples=enc.getTissueSamples();
List<TissueSample> tissueSamples=myShepherd.getAllTissueSamplesForEncounter(enc.getCatalogNumber());

if((tissueSamples!=null)&&(tissueSamples.size()>0)){

	int numTissueSamples=tissueSamples.size();

%>
<table width="100%" class="table table-bordered table-striped tissueSampleTable">
<tr><th><%=encprops.getProperty("sampleID") %></th><th><%=encprops.getProperty("values") %></th><th><%=encprops.getProperty("analyses") %></th><th><%=encprops.getProperty("editTissueSample") %></th><th><%=encprops.getProperty("removeTissueSample") %></th></tr>
<%
for(int j=0;j<numTissueSamples;j++){
	TissueSample thisSample=tissueSamples.get(j);
	%>
	<tr><td><span class="caption"><%=thisSample.getSampleID() %></span></td><td><span class="caption"><%=thisSample.getHTMLString() %></span></td>

	<td><table>
		<%
		int numAnalyses=thisSample.getNumAnalyses();
		List<GeneticAnalysis> gAnalyses = thisSample.getGeneticAnalyses();
		for(int g=0;g<numAnalyses;g++){
			GeneticAnalysis ga = gAnalyses.get(g);
			if(ga.getAnalysisType().equals("MitochondrialDNA")){
				MitochondrialDNAAnalysis mito=(MitochondrialDNAAnalysis)ga;
				%>
				<tr><td style="border-style: none;"><strong><span class="caption"><%=encprops.getProperty("haplotype") %></strong></span></strong> <span class="caption"><%=mito.getHaplotype() %> <a id="haplo<%=thisSample.getSampleID() %>" class="toggleBtn"><img width="20px" height="20px" style="border-style: none;" src="../images/Crystal_Clear_action_edit.png" /></a> <a onclick="return confirm('<%=encprops.getProperty("deleteHaplotype") %>');" href="../TissueSampleRemoveHaplotype?encounter=<%=enc.getCatalogNumber()%>&sampleID=<%=thisSample.getSampleID()%>&analysisID=<%=mito.getAnalysisID() %>"><img width="20px" height="20px" style="border-style: none;" src="../images/cancel.gif" /></a>
				<%
				if(!mito.getSuperHTMLString().equals("")){
				%>
				<em>
				<br /><%=encprops.getProperty("analysisID")%>: <%=mito.getAnalysisID()%>
				<br /><%=mito.getSuperHTMLString()%>
				</em>
				<%
				}
				%>
				</span>


							<%
if ((isOwner || encounterCanBeEditedByAnyLoggedInUser) && CommonConfiguration.isCatalogEditable(context)){
%>
<!-- start haplotype popup -->
<script type="text/javascript">
  $(window).on('load',function() {
    $("#haplo<%=thisSample.getSampleID() %>").click(function() {
      $("#dialogHaplotype<%=thisSample.getSampleID() %>").toggle();
    });
  });
</script>

<div id="dialogHaplotype<%=thisSample.getSampleID() %>" title="<%=encprops.getProperty("setHaplotype")%>" style="display:none">
<form id="setHaplotype<%=thisSample.getSampleID() %>" action="../TissueSampleSetHaplotype" method="post">
<table cellpadding="1" cellspacing="0" bordercolor="#FFFFFF">

  <tr>
    <td>


        <%=encprops.getProperty("analysisID")%> (<%=encprops.getProperty("required")%>)</td><td>
        <%
        MitochondrialDNAAnalysis mtDNA=new MitochondrialDNAAnalysis();
        mtDNA=mito;
        %>
        <input name="analysisID" type="text" size="20" maxlength="100" value="<%=mtDNA.getAnalysisID() %>" /></td>
   </tr>
   <tr>
        <%
        String haplotypeString="";
        try{
        	if(mtDNA.getHaplotype()!=null){haplotypeString=mtDNA.getHaplotype();}
        }
        catch(NullPointerException npe34){}
        %>
        <td><%=encprops.getProperty("haplotype")%> (<%=encprops.getProperty("required")%>)</td><td>
        <input name="haplotype" type="text" size="20" maxlength="100" value="<%=haplotypeString %>" />
 		</td></tr>

 		 <tr>
 		 <%
        String processingLabTaskID="";
        if(mtDNA.getProcessingLabTaskID()!=null){processingLabTaskID=mtDNA.getProcessingLabTaskID();}
        %>
        <td><%=encprops.getProperty("processingLabTaskID")%></td><td>
        <input name="processingLabTaskID" type="text" size="20" maxlength="100" value="<%=processingLabTaskID %>" />
 		</td></tr>

 		<tr><td>
  		 <%
        String processingLabName="";
        if(mtDNA.getProcessingLabName()!=null){processingLabName=mtDNA.getProcessingLabName();}
        %>
        <%=encprops.getProperty("processingLabName")%></td><td>
        <input name="processingLabName type="text" size="20" maxlength="100" value="<%=processingLabName %>" />
 		</td></tr>

 		<tr><td>
   		 <%
        String processingLabContactName="";
        if(mtDNA.getProcessingLabContactName()!=null){processingLabContactName=mtDNA.getProcessingLabContactName();}
        %>
        <%=encprops.getProperty("processingLabContactName")%></td><td>
        <input name="processingLabContactName type="text" size="20" maxlength="100" value="<%=processingLabContactName %>" />
 		</td></tr>

 		<tr><td>
   		 <%
        String processingLabContactDetails="";
        if(mtDNA.getProcessingLabContactDetails()!=null){processingLabContactDetails=mtDNA.getProcessingLabContactDetails();}
        %>
        <%=encprops.getProperty("processingLabContactDetails")%></td><td>
        <input name="processingLabContactDetails type="text" size="20" maxlength="100" value="<%=processingLabContactDetails %>" />
 		</td></tr>
 		<tr><td colspan="2">
 		  <input name="sampleID" type="hidden" value="<%=thisSample.getSampleID() %>" />
          <input name="number" type="hidden" value="<%=num%>" />
          <input name="action" type="hidden" value="setHaplotype" />
          <input name="EditTissueSample" type="submit" id="EditTissueSample" value="<%=encprops.getProperty("set")%>" />

    </td>
  </tr>
</table>
	</form>

</div>

<%-- <script>
var dlgHaplotype<%=mito.getAnalysisID() %> = $("#dialogHaplotype<%=mito.getAnalysisID() %>").dialog({
  autoOpen: false,
  draggable: false,
  resizable: false,
  width: 600
});

$("a#haplo<%=mito.getAnalysisID() %>").click(function() {
  dlgHaplotype<%=mito.getAnalysisID() %>.dialog("open");

});
</script> --%>
<!-- end haplotype popup -->
<%
}
%>
				</td></tr></li>
			<%
			}
			else if(ga.getAnalysisType().equals("SexAnalysis")){
				SexAnalysis mito=(SexAnalysis)ga;
				%>
				<tr><td style="border-style: none;"><strong><span class="caption"><%=encprops.getProperty("geneticSex") %></strong></span></strong>: <span class="caption"><%=mito.getSex() %>
				<%
				if(!mito.getSuperHTMLString().equals("")){
				%>
				<em>
				<br /><%=encprops.getProperty("analysisID")%>: <%=mito.getAnalysisID()%>
				<br /><%=mito.getSuperHTMLString()%>
				</em>
				<%
				}
				%>
				</span></td>
        <td style="border-style: none;">
          <a id="setSex<%=thisSample.getSampleID() %>" class="launchPopup toggleBtn"><img width="20px" height="20px" style="border-style: none;" src="../images/Crystal_Clear_action_edit.png" />
        </a>

				<%
if ((isOwner || encounterCanBeEditedByAnyLoggedInUser) && CommonConfiguration.isCatalogEditable(context)){
%>
<!-- start genetic sex popup -->
<script type="text/javascript">
  $("#setSex<%=thisSample.getSampleID() %>").click(function() {
    $("#dialogSexSet<%=thisSample.getSampleID().replaceAll("[-+.^:,]","") %>").toggle();
  });

</script>
<div id="dialogSexSet<%=thisSample.getSampleID().replaceAll("[-+.^:,]","") %>" title="<%=encprops.getProperty("setSexAnalysis")%>" style="display:none">

<form name="setSexAnalysis" action="../TissueSampleSetSexAnalysis" method="post">

<table cellpadding="1" cellspacing="0" bordercolor="#FFFFFF">
  <tr>
    <td>

        <%=encprops.getProperty("analysisID")%> (<%=encprops.getProperty("required")%>)<br />
        <%
        SexAnalysis mtDNA=new SexAnalysis();
        String analysisIDString="";
        if (mito.getAnalysisID()!=null) analysisIDString = mito.getAnalysisID();
        %>
        </td><td><input name="analysisID" type="text" size="20" maxlength="100" value="<%=analysisIDString %>" /><br />
        </td></tr>
        <tr><td>
        <%
        String haplotypeString="";

        try{
          if(mito.getSex()!=null){haplotypeString=mito.getSex();}
        } catch (NullPointerException npe34){}

        ArrayList<String> sexDefs = CommonConfiguration.getSequentialPropertyValues("sex", context);

        if (sexDefs!=null&&haplotypeString!=null) {
          System.out.println("haplotypeString??? "+haplotypeString);
          System.out.println("sexDefs:  "+Arrays.toString(sexDefs.toArray()));
          sexDefs.remove(haplotypeString);
        }
        %>
        <%=encprops.getProperty("geneticSex")%> (<%=encprops.getProperty("required")%>)<br />
        </td><td>
          <select name="sex" id="geneticSexSelect">
            <option value="<%=haplotypeString%>" selected><%=haplotypeString%></option>
            <%
            for (String sexDef : sexDefs) {
            %>
              <option value="<%=sexDef%>"><%=sexDef%></option>
            <%
            }
            %>
          </select>
        </td></tr>

		<tr><td>
		 <%
      String processingLabTaskID="";
      if(mito.getProcessingLabTaskID()!=null){processingLabTaskID=mito.getProcessingLabTaskID();}
      %>
      <%=encprops.getProperty("processingLabTaskID")%><br />
      </td><td><input name="processingLabTaskID" type="text" size="20" maxlength="100" value="<%=processingLabTaskID %>" />
	</td></tr>

		<tr><td>
		 <%
      String processingLabName="";
      if(mito.getProcessingLabName()!=null){processingLabName=mito.getProcessingLabName();}
      %>
      <%=encprops.getProperty("processingLabName")%><br />
      </td><td><input name="processingLabName type="text" size="20" maxlength="100" value="<%=processingLabName %>" />
</td></tr>

		<tr><td>
 		 <%
      String processingLabContactName="";
      if(mito.getProcessingLabContactName()!=null){processingLabContactName=mito.getProcessingLabContactName();}
      %>
      <%=encprops.getProperty("processingLabContactName")%><br />
      </td><td><input name="processingLabContactName type="text" size="20" maxlength="100" value="<%=processingLabContactName %>" />
</td></tr>

		<tr><td>
 		 <%
      String processingLabContactDetails="";
      if(mito.getProcessingLabContactDetails()!=null){processingLabContactDetails=mito.getProcessingLabContactDetails();}
      %>
      <%=encprops.getProperty("processingLabContactDetails")%><br />
      </td><td><input name="processingLabContactDetails type="text" size="20" maxlength="100" value="<%=processingLabContactDetails %>" />
</td></tr>

		<tr><td>
		  <input name="sampleID" type="hidden" value="<%=thisSample.getSampleID()%>" />
        <input name="number" type="hidden" value="<%=num%>" />
        <input name="action" type="hidden" value="setSexAnalysis" />
        <input name="EditTissueSampleSexAnalysis" type="submit" id="EditTissueSampleSexAnalysis" value="<%=encprops.getProperty("set")%>" />

  </td>
</tr>
</table>
  </form>
</div>
<!-- end genetic sex popup -->
<%
}
%>

				</td>
				<td style="border-style: none;"><a onclick="return confirm('<%=encprops.getProperty("deleteGenetic") %>');" href="../TissueSampleRemoveSexAnalysis?encounter=<%=enc.getCatalogNumber()%>&sampleID=<%=thisSample.getSampleID()%>&analysisID=<%=mito.getAnalysisID() %>"><img style="border-style: none;width: 20px;height: 20px;" src="../images/cancel.gif" /></a></td></tr>
			<%
			}
			else if(ga.getAnalysisType().equals("MicrosatelliteMarkers")){
				MicrosatelliteMarkersAnalysis mito=(MicrosatelliteMarkersAnalysis)ga;

			%>
			<tr>
				<td style="border-style: none;">
					<p><span class="caption"><strong><%=encprops.getProperty("msMarkers") %></strong></span>
					<a class="launchPopup toggleBtn" id="msmarkersSet<%=thisSample.getSampleID()%>"><img width="20px" height="20px" style="border-style: none;" src="../images/Crystal_Clear_action_edit.png" /></a>

        <a onclick="return confirm('<%=encprops.getProperty("deleteMSMarkers") %>');" href="../TissueSampleRemoveMicrosatelliteMarkers?encounter=<%=enc.getCatalogNumber()%>&sampleID=<%=thisSample.getSampleID()%>&analysisID=<%=mito.getAnalysisID() %>">
        <img style="border-style: none;width: 20px;height: 20px;" src="../images/cancel.gif" />
      </a>
					<%
					if((enc.getIndividualID()!=null)&&(request.getUserPrincipal()!=null)){
					%>
					<a href="../individualSearch.jsp?individualDistanceSearch=<%=enc.getIndividualID()%>"><img height="20px" width="20px" align="absmiddle" alt="Individual-to-Individual Genetic Distance Search" src="../images/Crystal_Clear_app_xmag.png"></img></a>
					<%
					}
					%>
					</p>
					<span class="caption"><%=mito.getAllelesHTMLString() %>
						<%
									if(!mito.getSuperHTMLString().equals("")){
									%>
									<em>
									<br /><%=encprops.getProperty("analysisID")%>: <%=mito.getAnalysisID()%>
									<br /><%=mito.getSuperHTMLString()%>
									</em>
									<%
									}
				%>

					</span>
															<%
if ((isOwner || encounterCanBeEditedByAnyLoggedInUser) && CommonConfiguration.isCatalogEditable(context)){
%>

<!-- start ms marker popup -->
<script type="text/javascript">
  $(window).on('load',function() {
    $("#msmarkersSet<%=thisSample.getSampleID()%>").click(function() {
      $("#dialogMSMarkersSet<%=thisSample.getSampleID().replaceAll("[-+.^:,]","")%>").toggle();
    });
  });
</script>

<div id="dialogMSMarkersSet<%=thisSample.getSampleID().replaceAll("[-+.^:,]","")%>" title="<%=encprops.getProperty("setMsMarkers")%>" style="display:none">

<form id="setMsMarkers" action="../TissueSampleSetMicrosatelliteMarkers" method="post">

<table cellpadding="1" cellspacing="0" bordercolor="#FFFFFF">
  <tr>
    <td align="left" valign="top">

        <%=encprops.getProperty("analysisID")%> (<%=encprops.getProperty("required")%>)</td><td>
        <%
        MicrosatelliteMarkersAnalysis msDNA=new MicrosatelliteMarkersAnalysis();
        msDNA=mito;
        String analysisIDString=msDNA.getAnalysisID();
        %>
        <input name="analysisID" type="text" size="20" maxlength="100" value="<%=analysisIDString %>" /></td></tr>

		<tr><td>
 		 <%
        String processingLabTaskID="";
        if(msDNA.getProcessingLabTaskID()!=null){processingLabTaskID=msDNA.getProcessingLabTaskID();}
        %>
        <%=encprops.getProperty("processingLabTaskID")%><br />
        </td><td><input name="processingLabTaskID" type="text" size="20" maxlength="100" value="<%=processingLabTaskID %>" />
 		</td></tr>

 		<tr><td>
  		 <%
        String processingLabName="";
        if(msDNA.getProcessingLabName()!=null){processingLabName=msDNA.getProcessingLabName();}
        %>
        <%=encprops.getProperty("processingLabName")%><br />
        </td><td><input name="processingLabName" type="text" size="20" maxlength="100" value="<%=processingLabName %>" />
 		</td></tr>

 		<tr><td>
   		 <%
        String processingLabContactName="";
        if(msDNA.getProcessingLabContactName()!=null){processingLabContactName=msDNA.getProcessingLabContactName();}
        %>
        <%=encprops.getProperty("processingLabContactName")%><br />
        </td><td><input name="processingLabContactName" type="text" size="20" maxlength="100" value="<%=processingLabContactName %>" />
 		</td></tr>

 		<tr><td>
   		 <%
        String processingLabContactDetails="";
        if(msDNA.getProcessingLabContactDetails()!=null){processingLabContactDetails=msDNA.getProcessingLabContactDetails();}
        %>
        <%=encprops.getProperty("processingLabContactDetails")%><br />
        </td><td><input name="processingLabContactDetails" type="text" size="20" maxlength="100" value="<%=processingLabContactDetails %>" />
 		</td></tr>
 		<tr><td>
 		<%
 		//begin setting up the loci and alleles
 	      int numPloids=2; //most covered species will be diploids
 	      try{
 	        numPloids=(new Integer(CommonConfiguration.getProperty("numPloids",context))).intValue();
 	      }
 	      catch(Exception e){System.out.println("numPloids configuration value did not resolve to an integer.");e.printStackTrace();}

 	      int numLoci=10;
 	      try{
 	 	  	numLoci=(new Integer(CommonConfiguration.getProperty("numLoci",context))).intValue();
 	 	  }
 	 	  catch(Exception e){System.out.println("numLoci configuration value did not resolve to an integer.");e.printStackTrace();}

 		  for(int locus=0;locus<numLoci;locus++){
 			 String locusNameValue="";
 			 if((msDNA.getLoci()!=null)&&(locus<msDNA.getLoci().size())){locusNameValue=msDNA.getLoci().get(locus).getName();}
 		  %>
			<br /><%=encprops.getProperty("locus") %>: <input name="locusName<%=locus %>" type="text" size="10" value="<%=locusNameValue %>" /><br />
 				<%
 				for(int ploid=0;ploid<numPloids;ploid++){
 					Integer ploidValue=0;
 					if((msDNA.getLoci()!=null)&&(locus<msDNA.getLoci().size())&&(msDNA.getLoci().get(locus).getAllele(ploid)!=null)){ploidValue=msDNA.getLoci().get(locus).getAllele(ploid);}

 				%>
 				<%=encprops.getProperty("allele") %>: <input name="allele<%=locus %><%=ploid %>" type="text" size="10" value="<%=ploidValue %>" /><br />


 				<%
 				}
 				%>

		  <%
 		  }  //end for loci looping
		  %>

		  <tr><td colspan="2">
 		  <input name="sampleID" type="hidden" value="<%=thisSample.getSampleID()%>" />
          <input name="number" type="hidden" value="<%=num%>" />

          <input name="EditTissueSample" type="submit" id="EditTissueSample" value="<%=encprops.getProperty("set")%>" />
    </td></tr>
    </td>
  </tr>
</table>
	  </form>
</div>

<%-- <script>
var dlgMSMarkersSet<%=thisSample.getSampleID().replaceAll("[-+.^:,]","")%> = $("#dialogMSMarkersSet<%=thisSample.getSampleID().replaceAll("[-+.^:,]","")%>").dialog({
  autoOpen: false,
  draggable: false,
  resizable: false,
  width: 600
});


</script> --%>
<!-- end ms markers popup -->
<%
}
%>
				</td></tr>
			<%
			}
			else if(ga.getAnalysisType().equals("BiologicalMeasurement")){
				BiologicalMeasurement mito=(BiologicalMeasurement)ga;
				%>
				<tr><td style="border-style: none;"><strong><span class="caption"><%=mito.getMeasurementType()%> <%=encprops.getProperty("measurement") %></span></strong><br /> <span class="caption"><%=mito.getValue().toString() %> <%=mito.getUnits() %> (<%=mito.getSamplingProtocol() %>)
				<%
				if(!mito.getSuperHTMLString().equals("")){
				%>
				<em>
				<br /><%=encprops.getProperty("analysisID")%>: <%=mito.getAnalysisID()%>
				<br /><%=mito.getSuperHTMLString()%>
				</em>
				<%
				}
				%>
				</span></td><td style="border-style: none;"><a class="launchPopup toggleBtn" id="setBioMeasure<%=thisSample.getSampleID() %>"><img width="20px" height="20px" style="border-style: none;" src="../images/Crystal_Clear_action_edit.png" /></a>

<%
if ((isOwner || encounterCanBeEditedByAnyLoggedInUser) && CommonConfiguration.isCatalogEditable(context)){
%>
<!-- start biomeasure popup -->
<div id="dialogSetBiomeasure4<%=thisSample.getSampleID().replaceAll("[-+.^:,]","") %>" title="<%=encprops.getProperty("setBiologicalMeasurement")%>" style="display:none">
  <form action="../TissueSampleSetMeasurement" method="post">

<table cellpadding="1" cellspacing="0" bordercolor="#FFFFFF">

<tr>
<td>

    <%=encprops.getProperty("analysisID")%> (<%=encprops.getProperty("required")%>)<br />
    <%
    BiologicalMeasurement mtDNA=mito;
    String analysisIDString=mtDNA.getAnalysisID();

    %>
    </td><td><input name="analysisID" type="text" size="20" maxlength="100" value="<%=analysisIDString %>" /><br />
    </td></tr>

    <tr><td>
    <%
    String type="";
    if(mtDNA.getMeasurementType()!=null){type=mtDNA.getMeasurementType();}
    %>
    <%=encprops.getProperty("type")%> (<%=encprops.getProperty("required")%>)
    </td><td>


     		<%
     		List<String> values=CommonConfiguration.getIndexedPropertyValues("biologicalMeasurementType",context);
 			int numProps=values.size();
 			List<String> measurementUnits=CommonConfiguration.getIndexedPropertyValues("biologicalMeasurementUnits",context);
 			int numUnitsProps=measurementUnits.size();

     		if(numProps>0){

     			%>
     			<p><select size="<%=(numProps+1) %>" name="measurementType" id="measurementType">
     			<%

     			for(int y=0;y<numProps;y++){
     				String units="";
     				if(numUnitsProps>y){units="&nbsp;("+measurementUnits.get(y)+")";}
     				String selected="";
     				if((mtDNA.getMeasurementType()!=null)&&(mtDNA.getMeasurementType().equals(values.get(y)))){
     					selected="selected=\"selected\"";
     				}
     			%>
     				<option value="<%=values.get(y) %>" <%=selected %>><%=values.get(y) %><%=units %></option>
     			<%
     			}
     			%>
     			</select>
				</p>
			<%
     		}
     		else{
			%>
    			<input name="measurementType" type="text" size="20" maxlength="100" value="<%=type %>" />
    		<%
     		}
    %>
    </td></tr>

    <tr><td>
    <%
    String thisValue="";
    if(mtDNA.getValue()!=null){thisValue=mtDNA.getValue().toString();}
    %>
    <%=encprops.getProperty("value")%> (<%=encprops.getProperty("required")%>)<br />
    </td><td><input name="value" type="text" size="20" maxlength="100" value="<%=thisValue %>"></input>
    </td></tr>

    <tr><td>
	<%
    String thisSamplingProtocol="";
    if(mtDNA.getSamplingProtocol()!=null){thisSamplingProtocol=mtDNA.getSamplingProtocol();}
    %>
    <%=encprops.getProperty("samplingProtocol")%>
    </td><td>

     		<%
     		List<String> protovalues=CommonConfiguration.getIndexedPropertyValues("biologicalMeasurementSamplingProtocols",context);
 			int protonumProps=protovalues.size();

     		if(protonumProps>0){

     			%>
     			<p><select size="<%=(protonumProps+1) %>" name="samplingProtocol" id="samplingProtocol">
     			<%

     			for(int y=0;y<protonumProps;y++){
     				String selected="";
     				if((mtDNA.getSamplingProtocol()!=null)&&(mtDNA.getSamplingProtocol().equals(protovalues.get(y)))){
     					selected="selected=\"selected\"";
     				}
     			%>
     				<option value="<%=protovalues.get(y) %>" <%=selected %>><%=protovalues.get(y) %></option>
     			<%
     			}
     			%>
     			</select>
				</p>
			<%
     		}
     		else{
			%>
    			<input name="samplingProtocol" type="text" size="20" maxlength="100" value="<%=type %>" />
    		<%
     		}
			%>
			</td></tr>

    <tr><td>
    <%
    String processingLabTaskID="";
    if(mtDNA.getProcessingLabTaskID()!=null){processingLabTaskID=mtDNA.getProcessingLabTaskID();}
    %>
    <%=encprops.getProperty("processingLabTaskID")%><br />
    </td><td><input name="processingLabTaskID" type="text" size="20" maxlength="100" value="<%=processingLabTaskID %>" />
</td></tr>

    <tr><td>
		 <%
    String processingLabName="";
    if(mtDNA.getProcessingLabName()!=null){processingLabName=mtDNA.getProcessingLabName();}
    %>
    <%=encprops.getProperty("processingLabName")%><br />
    </td><td><input name="processingLabName" type="text" size="20" maxlength="100" value="<%=processingLabName %>" />

</td></tr>

    <tr><td>
		 <%
    String processingLabContactName="";
    if(mtDNA.getProcessingLabContactName()!=null){processingLabContactName=mtDNA.getProcessingLabContactName();}
    %>
    <%=encprops.getProperty("processingLabContactName")%><br />
    </td><td><input name="processingLabContactName" type="text" size="20" maxlength="100" value="<%=processingLabContactName %>" />
</td></tr>

    <tr><td>
		 <%
    String processingLabContactDetails="";
    if(mtDNA.getProcessingLabContactDetails()!=null){processingLabContactDetails=mtDNA.getProcessingLabContactDetails();}
    %>
    <%=encprops.getProperty("processingLabContactDetails")%><br />
    </td><td><input name="processingLabContactDetails" type="text" size="20" maxlength="100" value="<%=processingLabContactDetails %>" />
</td></tr>

    <tr><td>
		  <input name="sampleID" type="hidden" value="<%=thisSample.getSampleID()%>" />
      <input name="encounter" type="hidden" value="<%=num%>" />
      <input name="action" type="hidden" value="setBiologicalMeasurement" />
      <input name="EditTissueSampleBiomeasurementAnalysis" type="submit" id="EditTissueSampleBioMeasurementAnalysis" value="<%=encprops.getProperty("set")%>" />

</td>
</tr>
</table>
	 </form>
</div>

<script>
var dlgSetBiomeasure<%=thisSample.getSampleID().replaceAll("[-+.^:,]","") %> = $("#dialogSetBiomeasure4<%=thisSample.getSampleID().replaceAll("[-+.^:,]","") %>").dialog({
  autoOpen: false,
  draggable: false,
  resizable: false,
  width: 600
});

$("a#setBioMeasure<%=thisSample.getSampleID() %>").click(function() {
  dlgSetBiomeasure<%=thisSample.getSampleID().replaceAll("[-+.^:,]","") %>.dialog("open");

});
</script>
<!-- end biomeasure popup -->
<%
}
%>
				</td>
				<td style="border-style: none;"><a onclick="return confirm('<%=encprops.getProperty("deleteBio") %>');" href="../TissueSampleRemoveBiologicalMeasurement?encounter=<%=enc.getCatalogNumber()%>&sampleID=<%=thisSample.getSampleID()%>&analysisID=<%=mito.getAnalysisID() %>"><img width="20px" height="20px" style="border-style: none;" src="../images/cancel.gif" /></a></td>
			</tr>
			<%
			}
		}
		%>
		</table>

    <script type="text/javascript">
    $(window).on('load',function() {
        $(".addHaplotype<%=thisSample.getSampleID() %>").click(function() {
            var x = $("#dialogHaplotype<%=thisSample.getSampleID().replaceAll("[-+.^:,]","") %>");
            if (x.style.display === "none") {
                x.style.display = "block";
            } else {
                x.style.display = "none";
            }
        });
    });
    </script>
		<p>
      <span class="caption">
        <a class="addHaplotype<%=thisSample.getSampleID() %> toggleBtn">
        <img align="absmiddle" width="20px" height="20px" style="border-style: none;" src="../images/Crystal_Clear_action_edit_add.png"/>
        </a>
      <a class="toggleBtn addHaplotype<%=thisSample.getSampleID().replaceAll("[-+.^:,]","") %>"><%=encprops.getProperty("addHaplotype") %></a>
    </span>
  </p>
		<%
if ((isOwner || encounterCanBeEditedByAnyLoggedInUser) && CommonConfiguration.isCatalogEditable(context)){
%>
<!-- start haplotype popup -->
<script type="text/javascript">
  $(window).on('load',function() {
    $(".addHaplotype<%=thisSample.getSampleID() %>").click(function() {
      $("#dialogHaplotype4<%=thisSample.getSampleID().replaceAll("[-+.^:,]","") %>").toggle();
    });
  });
</script>
<div id="dialogHaplotype4<%=thisSample.getSampleID().replaceAll("[-+.^:,]","") %>" title="<%=encprops.getProperty("setHaplotype")%>" style="display:none">
<form id="setHaplotype" action="../TissueSampleSetHaplotype" method="post">
<table cellpadding="1" cellspacing="0">

  <tr>
    <td>
        <%=encprops.getProperty("analysisID")%> (<%=encprops.getProperty("required")%>)</td><td>
        <%
        MitochondrialDNAAnalysis mtDNA=new MitochondrialDNAAnalysis();
        String analysisIDString="";
        //if((request.getParameter("function")!=null)&&(request.getParameter("function").equals("2"))&&(request.getParameter("edit")!=null) && (request.getParameter("edit").equals("haplotype")) && (request.getParameter("analysisID")!=null)&&(myShepherd.isGeneticAnalysis(request.getParameter("sampleID"),request.getParameter("number"),request.getParameter("analysisID"),"MitochondrialDNA"))){
      	//    analysisIDString=request.getParameter("analysisID");
      	//	mtDNA=myShepherd.getMitochondrialDNAAnalysis(request.getParameter("sampleID"), enc.getCatalogNumber(),analysisIDString);
        //}
        %>
        <input name="analysisID" type="text" size="20" maxlength="100" value="<%=analysisIDString %>" /></td>
   </tr>
   <tr>
        <%
        String haplotypeString="";
        try{
        	if(mtDNA.getHaplotype()!=null){haplotypeString=mtDNA.getHaplotype();}
        }
        catch(NullPointerException npe34){}
        %>
        <td><%=encprops.getProperty("haplotype")%> (<%=encprops.getProperty("required")%>)</td><td>
        <input name="haplotype" type="text" size="20" maxlength="100" value="<%=haplotypeString %>" />
 		</td></tr>

 		 <tr>
 		 <%
        String processingLabTaskID="";
        if(mtDNA.getProcessingLabTaskID()!=null){processingLabTaskID=mtDNA.getProcessingLabTaskID();}
        %>
        <td><%=encprops.getProperty("processingLabTaskID")%></td><td>
        <input name="processingLabTaskID" type="text" size="20" maxlength="100" value="<%=processingLabTaskID %>" />
 		</td></tr>

 		<tr><td>
  		 <%
        String processingLabName="";
        if(mtDNA.getProcessingLabName()!=null){processingLabName=mtDNA.getProcessingLabName();}
        %>
        <%=encprops.getProperty("processingLabName")%></td><td>
        <input name="processingLabName type="text" size="20" maxlength="100" value="<%=processingLabName %>" />
 		</td></tr>

 		<tr><td>
   		 <%
        String processingLabContactName="";
        if(mtDNA.getProcessingLabContactName()!=null){processingLabContactName=mtDNA.getProcessingLabContactName();}
        %>
        <%=encprops.getProperty("processingLabContactName")%></td><td>
        <input name="processingLabContactName type="text" size="20" maxlength="100" value="<%=processingLabContactName %>" />
 		</td></tr>

 		<tr><td>
   		<%
        String processingLabContactDetails="";
        if(mtDNA.getProcessingLabContactDetails()!=null){processingLabContactDetails=mtDNA.getProcessingLabContactDetails();}
        %>
        <%=encprops.getProperty("processingLabContactDetails")%></td><td>
        <input name="processingLabContactDetails type="text" size="20" maxlength="100" value="<%=processingLabContactDetails %>" />
 		</td></tr>
 		<tr><td colspan="2">
 		  <input name="sampleID" type="hidden" value="<%=thisSample.getSampleID()%>" />
          <input name="number" type="hidden" value="<%=num%>" />
          <input name="action" type="hidden" value="setHaplotype" />
          <input name="EditTissueSample" type="submit" id="EditTissueSample" value="<%=encprops.getProperty("set")%>" />

    </td>
  </tr>
</table>
	</form>

</div>

<!-- end haplotype popup -->
<%
}
%>		<p>
      <span class="caption">
        <a class="msmarkersAdd<%=thisSample.getSampleID()%> toggleBtn">
        <img align="absmiddle" width="20px" height="20px" style="border-style: none;" src="../images/Crystal_Clear_action_edit_add.png"/>
        </a>
        <a class="msmarkersAdd<%=thisSample.getSampleID()%> toggleBtn"><%=encprops.getProperty("addMsMarkers") %></a>
      </span>
    </p>
<%
if ((isOwner || encounterCanBeEditedByAnyLoggedInUser) && CommonConfiguration.isCatalogEditable(context)){
%>
<!-- start sat tag metadata -->
<script type="text/javascript">
  $(window).on('load',function() {
    $(".msmarkersAdd<%=thisSample.getSampleID()%>").click(function() {
      $("#dialogMSMarkersAdd<%=thisSample.getSampleID().replaceAll("[-+.^:,]","")%>").toggle();
    });
  });
</script>

<div id="dialogMSMarkersAdd<%=thisSample.getSampleID().replaceAll("[-+.^:,]","")%>" title="<%=encprops.getProperty("setMsMarkers")%>" style="display:none">

<form id="setMsMarkers" action="../TissueSampleSetMicrosatelliteMarkers" method="post">

<table cellpadding="1" cellspacing="0" bordercolor="#FFFFFF">
  <tr>
    <td align="left" valign="top">

        <%=encprops.getProperty("analysisID")%> (<%=encprops.getProperty("required")%>)</td><td>
        <%
        MicrosatelliteMarkersAnalysis msDNA=new MicrosatelliteMarkersAnalysis();
        String analysisIDString="";
        %>
        <input name="analysisID" type="text" size="20" maxlength="100" value="<%=analysisIDString %>" /></td></tr>

		<tr><td>
 		 <%
        String processingLabTaskID="";
        if(msDNA.getProcessingLabTaskID()!=null){processingLabTaskID=msDNA.getProcessingLabTaskID();}
        %>
        <%=encprops.getProperty("processingLabTaskID")%><br />
        </td><td><input name="processingLabTaskID" type="text" size="20" maxlength="100" value="<%=processingLabTaskID %>" />
 		</td></tr>

 		<tr><td>
  		 <%
        String processingLabName="";
        if(msDNA.getProcessingLabName()!=null){processingLabName=msDNA.getProcessingLabName();}
        %>
        <%=encprops.getProperty("processingLabName")%><br />
        </td><td><input name="processingLabName" type="text" size="20" maxlength="100" value="<%=processingLabName %>" />
 		</td></tr>

 		<tr><td>
   		 <%
        String processingLabContactName="";
        if(msDNA.getProcessingLabContactName()!=null){processingLabContactName=msDNA.getProcessingLabContactName();}
        %>
        <%=encprops.getProperty("processingLabContactName")%><br />
        </td><td><input name="processingLabContactName" type="text" size="20" maxlength="100" value="<%=processingLabContactName %>" />
 		</td></tr>

 		<tr><td>
   		 <%
        String processingLabContactDetails="";
        if(msDNA.getProcessingLabContactDetails()!=null){processingLabContactDetails=msDNA.getProcessingLabContactDetails();}
        %>
        <%=encprops.getProperty("processingLabContactDetails")%><br />
        </td><td><input name="processingLabContactDetails" type="text" size="20" maxlength="100" value="<%=processingLabContactDetails %>" />
 		</td></tr>
 		<tr><td>
 		<%
 		//begin setting up the loci and alleles
 	      int numPloids=2; //most covered species will be diploids
 	      try{
 	        numPloids=(new Integer(CommonConfiguration.getProperty("numPloids",context))).intValue();
 	      }
 	      catch(Exception e){System.out.println("numPloids configuration value did not resolve to an integer.");e.printStackTrace();}

 	      int numLoci=10;
 	      try{
 	 	  	numLoci=(new Integer(CommonConfiguration.getProperty("numLoci",context))).intValue();
 	 	  }
 	 	  catch(Exception e){System.out.println("numLoci configuration value did not resolve to an integer.");e.printStackTrace();}

 		  for(int locus=0;locus<numLoci;locus++){
 			 String locusNameValue="";
 			 if((msDNA.getLoci()!=null)&&(locus<msDNA.getLoci().size())){locusNameValue=msDNA.getLoci().get(locus).getName();}
 		  %>
			<br /><%=encprops.getProperty("locus") %>: <input name="locusName<%=locus %>" type="text" size="10" value="<%=locusNameValue %>" /><br />
 				<%
 				for(int ploid=0;ploid<numPloids;ploid++){
 					Integer ploidValue=0;
 					if((msDNA.getLoci()!=null)&&(locus<msDNA.getLoci().size())&&(msDNA.getLoci().get(locus).getAllele(ploid)!=null)){ploidValue=msDNA.getLoci().get(locus).getAllele(ploid);}

 				%>
 				<%=encprops.getProperty("allele") %>: <input name="allele<%=locus %><%=ploid %>" type="text" size="10" value="<%=ploidValue %>" /><br />


 				<%
 				}
 				%>

		  <%
 		  }  //end for loci loop
		  %>

		  <tr><td colspan="2">
 		  <input name="sampleID" type="hidden" value="<%=thisSample.getSampleID()%>" />
          <input name="number" type="hidden" value="<%=num%>" />

          <input name="EditTissueSample" type="submit" id="EditTissueSample" value="<%=encprops.getProperty("set")%>" />
    </td></tr>
    </td>
  </tr>
</table>
	  </form>
</div>
<!-- end ms markers popup -->
<%
}
%>
<p>
  <span class="caption">
    <a class="addSex<%=thisSample.getSampleID() %> toggleBtn">
    <img align="absmiddle" width="20px" height="20px" style="border-style: none;" src="../images/Crystal_Clear_action_edit_add.png"/>
  </a>
  <a class="addSex<%=thisSample.getSampleID() %> toggleBtn"><%=encprops.getProperty("addGeneticSex") %></a>
</span>
</p>

<%
if ((isOwner || encounterCanBeEditedByAnyLoggedInUser) && CommonConfiguration.isCatalogEditable(context)){
%>

<!-- start genetic sex popup -->
<script type="text/javascript">
  $(window).on('load',function() {
    $(".addSex<%=thisSample.getSampleID() %>").click(function() {
      $("#dialogSex4<%=thisSample.getSampleID().replaceAll("[-+.^:,]","") %>").toggle();

    });
  });
</script>

<div id="dialogSex4<%=thisSample.getSampleID().replaceAll("[-+.^:,]","") %>" title="<%=encprops.getProperty("setSexAnalysis")%>" style="display:none">

<form name="setSexAnalysis" action="../TissueSampleSetSexAnalysis" method="post">

<table cellpadding="1" cellspacing="0" bordercolor="#FFFFFF">
  <tr>
    <td>

        <%=encprops.getProperty("analysisID")%> (<%=encprops.getProperty("required")%>)<br />
        <%
        SexAnalysis mtDNA=new SexAnalysis();
        String analysisIDString="";
        %>
        </td><td><input name="analysisID" type="text" size="20" maxlength="100" value="<%=analysisIDString %>" /><br />
        </td></tr>
        <tr><td>
        <%
        String haplotypeString="";

        try{
          if(mtDNA.getSex()!=null){haplotypeString=mtDNA.getSex();}
        } catch (NullPointerException npe34){}

        ArrayList<String> sexDefs = CommonConfiguration.getSequentialPropertyValues("sex", context);

        if (sexDefs!=null&&haplotypeString!=null&&sexDefs.contains(haplotypeString)) {
          sexDefs.remove(haplotypeString);
        }
        %>
        <%=encprops.getProperty("geneticSex")%> (<%=encprops.getProperty("required")%>)<br />
        </td><td>
          <select name="sex" id="geneticSexSelect">
            <%
            if (sexDefs!=null&&haplotypeString!=null&&sexDefs.contains(haplotypeString)) {
              %>
              <option value="<%=haplotypeString%>" selected><%=haplotypeString%></option>
              <%
            }
            for (String sexDef : sexDefs) {
            %>
              <option value="<%=sexDef%>"><%=sexDef%></option>
            <%
            }
            %>
          </select>
        </td></tr>

		<tr><td>
		 <%
      String processingLabTaskID="";
      if(mtDNA.getProcessingLabTaskID()!=null){processingLabTaskID=mtDNA.getProcessingLabTaskID();}
      %>
      <%=encprops.getProperty("processingLabTaskID")%><br />
      </td><td><input name="processingLabTaskID" type="text" size="20" maxlength="100" value="<%=processingLabTaskID %>" />
	</td></tr>

		<tr><td>
		 <%
      String processingLabName="";
      if(mtDNA.getProcessingLabName()!=null){processingLabName=mtDNA.getProcessingLabName();}
      %>
      <%=encprops.getProperty("processingLabName")%><br />
      </td><td><input name="processingLabName type="text" size="20" maxlength="100" value="<%=processingLabName %>" />
</td></tr>

		<tr><td>
 		 <%
      String processingLabContactName="";
      if(mtDNA.getProcessingLabContactName()!=null){processingLabContactName=mtDNA.getProcessingLabContactName();}
      %>
      <%=encprops.getProperty("processingLabContactName")%><br />
      </td><td><input name="processingLabContactName type="text" size="20" maxlength="100" value="<%=processingLabContactName %>" />
</td></tr>

		<tr><td>
 		 <%
      String processingLabContactDetails="";
      if(mtDNA.getProcessingLabContactDetails()!=null){processingLabContactDetails=mtDNA.getProcessingLabContactDetails();}
      %>
      <%=encprops.getProperty("processingLabContactDetails")%><br />
      </td><td><input name="processingLabContactDetails type="text" size="20" maxlength="100" value="<%=processingLabContactDetails %>" />
</td></tr>

		<tr><td>
		  <input name="sampleID" type="hidden" value="<%=thisSample.getSampleID()%>" />
        <input name="number" type="hidden" value="<%=num%>" />
        <input name="action" type="hidden" value="setSexAnalysis" />
        <input name="EditTissueSampleSexAnalysis" type="submit" id="EditTissueSampleSexAnalysis" value="<%=encprops.getProperty("set")%>" />

  </td>
</tr>
</table>
  </form>

</div>
<!-- end genetic sex -->
<%
}
%>
		<p>
      <span class="caption">
      <a class="toggleBtn addBioMeasure<%=thisSample.getSampleID() %>">
        <img align="absmiddle" width="20px" height="20px" style="border-style: none;" src="../images/Crystal_Clear_action_edit_add.png"/>
      </a>
      <a class="toggleBtn addBioMeasure<%=thisSample.getSampleID() %>"><%=encprops.getProperty("addBiologicalMeasurement") %></a>
    </span>
  </p>

		<%
if ((isOwner || encounterCanBeEditedByAnyLoggedInUser) && CommonConfiguration.isCatalogEditable(context)){
%>
<!-- start genetic sex -->
<script type="text/javascript">
  $(window).on('load',function() {
    $(".addBioMeasure<%=thisSample.getSampleID() %>").click(function() {
      $("#dialogBiomeasure4<%=thisSample.getSampleID().replaceAll("[-+.^:,]","") %>").toggle();

    });
  });
</script>

<div id="dialogBiomeasure4<%=thisSample.getSampleID().replaceAll("[-+.^:,]","") %>" title="<%=encprops.getProperty("setBiologicalMeasurement")%>" style="display:none">
  <form name="setBiologicalMeasurement" action="../TissueSampleSetMeasurement" method="post">

<table cellpadding="1" cellspacing="0" bordercolor="#FFFFFF">


<tr>
<td>

    <%=encprops.getProperty("analysisID")%> (<%=encprops.getProperty("required")%>)<br />
    <%
    BiologicalMeasurement mtDNA=new BiologicalMeasurement();
    String analysisIDString="";

    %>
    </td><td><input name="analysisID" type="text" size="20" maxlength="100" value="<%=analysisIDString %>" /><br />
    </td></tr>

    <tr><td>
    <%
    String type="";
    if(mtDNA.getMeasurementType()!=null){type=mtDNA.getMeasurementType();}
    %>
    <%=encprops.getProperty("type")%> (<%=encprops.getProperty("required")%>)
    </td><td>


     		<%
     		List<String> values=CommonConfiguration.getIndexedPropertyValues("biologicalMeasurementType",context);
 			int numProps=values.size();
 			List<String> measurementUnits=CommonConfiguration.getIndexedPropertyValues("biologicalMeasurementUnits",context);
 			int numUnitsProps=measurementUnits.size();

     		if(numProps>0){

     			%>
     			<p><select size="<%=(numProps+1) %>" name="measurementType" id="measurementType">
     			<%

     			for(int y=0;y<numProps;y++){
     				String units="";
     				if(numUnitsProps>y){units="&nbsp;("+measurementUnits.get(y)+")";}
     				String selected="";
     				if((mtDNA.getMeasurementType()!=null)&&(mtDNA.getMeasurementType().equals(values.get(y)))){
     					selected="selected=\"selected\"";
     				}
     			%>
     				<option value="<%=values.get(y) %>" <%=selected %>><%=values.get(y) %><%=units %></option>
     			<%
     			}
     			%>
     			</select>
				</p>
			<%
     		}
     		else{
			%>
    			<input name="measurementType" type="text" size="20" maxlength="100" value="<%=type %>" />
    		<%
     		}
    %>
    </td></tr>

    <tr><td>
    <%
    String thisValue="";
    if(mtDNA.getValue()!=null){thisValue=mtDNA.getValue().toString();}
    %>
    <%=encprops.getProperty("value")%> (<%=encprops.getProperty("required")%>)<br />
    </td><td><input name="value" type="text" size="20" maxlength="100" value="<%=thisValue %>"></input>
    </td></tr>

    <tr><td>
	<%
    String thisSamplingProtocol="";
    if(mtDNA.getSamplingProtocol()!=null){thisSamplingProtocol=mtDNA.getSamplingProtocol();}
    %>
    <%=encprops.getProperty("samplingProtocol")%>
    </td><td>

     		<%
     		List<String> protovalues=CommonConfiguration.getIndexedPropertyValues("biologicalMeasurementSamplingProtocols",context);
 			int protonumProps=protovalues.size();

     		if(protonumProps>0){

     			%>
     			<p><select size="<%=(protonumProps+1) %>" name="samplingProtocol" id="samplingProtocol">
     			<%

     			for(int y=0;y<protonumProps;y++){
     				String selected="";
     				if((mtDNA.getSamplingProtocol()!=null)&&(mtDNA.getSamplingProtocol().equals(protovalues.get(y)))){
     					selected="selected=\"selected\"";
     				}
     			%>
     				<option value="<%=protovalues.get(y) %>" <%=selected %>><%=protovalues.get(y) %></option>
     			<%
     			}
     			%>
     			</select>
				</p>
			<%
     		}
     		else{
			%>
    			<input name="samplingProtocol" type="text" size="20" maxlength="100" value="<%=type %>" />
    		<%
     		}
			%>
			</td></tr>

    <tr><td>
    <%
    String processingLabTaskID="";
    if(mtDNA.getProcessingLabTaskID()!=null){processingLabTaskID=mtDNA.getProcessingLabTaskID();}
    %>
    <%=encprops.getProperty("processingLabTaskID")%><br />
    </td><td><input name="processingLabTaskID" type="text" size="20" maxlength="100" value="<%=processingLabTaskID %>" />
</td></tr>

    <tr><td>
		 <%
    String processingLabName="";
    if(mtDNA.getProcessingLabName()!=null){processingLabName=mtDNA.getProcessingLabName();}
    %>
    <%=encprops.getProperty("processingLabName")%><br />
    </td><td><input name="processingLabName" type="text" size="20" maxlength="100" value="<%=processingLabName %>" />

</td></tr>

    <tr><td>
		 <%
    String processingLabContactName="";
    if(mtDNA.getProcessingLabContactName()!=null){processingLabContactName=mtDNA.getProcessingLabContactName();}
    %>
    <%=encprops.getProperty("processingLabContactName")%><br />
    </td><td><input name="processingLabContactName" type="text" size="20" maxlength="100" value="<%=processingLabContactName %>" />
</td></tr>

    <tr><td>
		 <%
    String processingLabContactDetails="";
    if(mtDNA.getProcessingLabContactDetails()!=null){processingLabContactDetails=mtDNA.getProcessingLabContactDetails();}
    %>
    <%=encprops.getProperty("processingLabContactDetails")%><br />
    </td><td><input name="processingLabContactDetails" type="text" size="20" maxlength="100" value="<%=processingLabContactDetails %>" />
</td></tr>

    <tr><td>
		  <input name="sampleID" type="hidden" value="<%=thisSample.getSampleID()%>" />
      <input name="encounter" type="hidden" value="<%=num%>" />
      <input name="action" type="hidden" value="setBiologicalMeasurement" />
      <input name="EditTissueSampleBiomeasurementAnalysis" type="submit" id="EditTissueSampleBioMeasurementAnalysis" value="<%=encprops.getProperty("set")%>" />

</td>
</tr>
</table>
	 </form>
</div>
<!-- end biomeasure popup -->
<%
}
%>
	</td>


	<td><a id="sample" href="biologicalSamples.jsp?number=<%=enc.getCatalogNumber() %>&sampleID=<%=thisSample.getSampleID()%>&edit=tissueSample&function=1"><img width="24px" style="border-style: none;" src="../images/Crystal_Clear_action_edit.png" /></a></td><td><a onclick="return confirm('<%=encprops.getProperty("deleteTissue") %>');" href="../EncounterRemoveTissueSample?encounter=<%=enc.getCatalogNumber()%>&sampleID=<%=thisSample.getSampleID()%>"><img style="border-style: none;width: 20px;height: 20px;" src="../images/cancel.gif" /></a></td></tr>
	<%
}
%>
</table>
</p>
<%
}
else {
%>
	<p class="para"><%=encprops.getProperty("noTissueSamples") %></p>
<%
}

}

//now iterate through the jspImport# declarations in encounter.properties and import those files locally
int currentImportNum=0;
while(encprops.getProperty(("jspImport"+currentImportNum))!=null){
	  String importName=encprops.getProperty(("jspImport"+currentImportNum));
	//let's set up references to our file system components

%>
	<hr />
		<jsp:include page="<%=importName %>" flush="true">
			<jsp:param name="isAdmin" value="<%=request.isUserInRole(\"admin\")%>" />
			<jsp:param name="encounterNumber" value="<%=num%>" />
    		<jsp:param name="isOwner" value="<%=isOwner %>" />
		</jsp:include>

    <%

 currentImportNum++;
} //end while for jspImports


%>

</p>
</td>
</tr>

</table>

<script>
var iaMatchFilterAnnotationIds = [];
function iaMatchFilterGo() {
    var data = {
        v2: true,
        taskParameters: {
            matchingSetFilter: {},
            matchingAlgorithms: []
        },
        annotationIds: iaMatchFilterAnnotationIds,
        fastlane: true
    };
    var keyMap = {
        'match-filter-location-id': 'locationIds',
        'match-filter-owner': 'owner'
    };

    var optArray=[];


		<%
    IAJsonProperties iaConfig = IAJsonProperties.iaConfig();
    Taxonomy taxy = enc.getTaxonomy(myShepherd);

Map<String,JSONObject> identConfigs = new HashMap<String,JSONObject>();
try {
    for (String iaClass : iaConfig.getValidIAClasses(taxy)) {
        for (JSONObject idOpt: iaConfig.identOpts(taxy, iaClass)) {
            String key = idOpt.toString();
            if (identConfigs.containsKey(key)) {
                identConfigs.get(key).getJSONArray("_iaClasses").put(iaClass);
            } else {
                JSONArray iac = new JSONArray();
                iac.put(iaClass);
                idOpt.put("_iaClasses", iac);
                identConfigs.put(key, idOpt);
            }
        }
    }
} catch (Exception ex) {
    out.println("// <!-- identConfigs/iaConfig ERROR: " + ex.toString() + "; please see catalina.out -->");
    ex.printStackTrace();
}

//we need to keep this in the same order so we can get values out in the same way
List<JSONObject> identConfigsValues = new ArrayList<JSONObject>();
for (JSONObject val : identConfigs.values()) {
    identConfigsValues.add(val);
    //now we add this js line to add it in same order:
%>
        optArray.push(<%=val.toString()%>);
<%
}
%>

$('.ia-match-filter-dialog input').each(function(i, el) {
        if ((el.type != 'checkbox') || !el.checked) return;
        var key = keyMap[el.name] || '_UNKNOWN_';
        if (!data.taskParameters.matchingSetFilter[key]) data.taskParameters.matchingSetFilter[key] = [];

        if(el.name=="match-filter-algorithm"){
        	data.taskParameters.matchingAlgorithms.push(optArray[el.defaultValue]);
        }
        else{
        	data.taskParameters.matchingSetFilter[key].push(el.defaultValue);
        }

    });
console.log('SENDING ===> %o', data);
    wildbook.IA.getPluginByType('IBEIS').restCall(data, function(xhr, textStatus) {
console.log('RETURNED ========> %o %o', textStatus, xhr.responseJSON.taskId);
        wildbook.openInTab('../iaResults.jsp?taskId=' + xhr.responseJSON.taskId);
    });
    iaMatchFilterAnnotationIds = [];  //clear it out in case user sends again from this page
    $('.ia-match-filter-dialog').hide();
}

var encText = '<%=encprops.getProperty("encounter")%>';
var noneText = '<%=encprops.getProperty("none")%>';
var selectedText = '<%=encprops.getProperty("selected")%>';
function iaMatchFilterLocationCountUpdate() {
    var ct = 0;
    var vals = [];
    $('#ia-match-filter-location input:checked').each(function(i,el) {
        vals.push(el.nextElementSibling.firstChild.nodeValue);
        ct += parseInt($(el).parent().find('.item-count:first').text());
    });
    if ($('#match-filter-location-unlabeled').is(':checked')) ct += parseInt($('#match-filter-location-unlabeled').parent().find('.item-count').text());
    if (ct < 1) {
        $('#total-location-count').text(noneText + ' ' + selectedText);
    } else {
        $('#total-location-count').text(ct + ' ' + encText + ((ct == 1) ? '' : 's') + ' (' + vals.length + ' ' + selectedText + ')');
    }
    return true;
}
function adjustLocationCheckboxes(el) {
    $(el).parent().find('ul input').each(function(i, inp) {
        inp.checked = el.checked;
    });
    return true;
}

$(window).on('load',function() {
    adjustLocationCheckboxes( $('.ul-root input:checked')[0] );  //this will check all below the default-checked one
    iaMatchFilterLocationCountUpdate();
    $('.ul-root input[type="checkbox"]').on('change', function(ev) {
        adjustLocationCheckboxes(ev.target);
        iaMatchFilterLocationCountUpdate();
    });
/*
    $('#ia-match-filter-location input').on('change', function(ev) {
        iaMatchFilterLocationCountUpdate()
    });
*/
});
</script>
<!-- 
<div class="ia-match-filter-dialog">

<%

	String queueStatementID="";
	int wbiaIDQueueSize = WbiaQueueUtil.getSizeDetectionJobQueue(false);
	if(wbiaIDQueueSize==0){
		queueStatementID = "The machine learning queue is empty and ready for work.";
	}
	else if(Prometheus.getValue("wildbook_wbia_turnaroundtime_detection")!=null){
		String val=Prometheus.getValue("wildbook_wbia_turnaroundtime_detection");
		try{
			Double d = Double.parseDouble(val);
			d=d/60.0;
			queueStatementID = "There are currently "+wbiaIDQueueSize+" ID jobs in the small batch queue. Time to completion is averaging "+(int)Math.round(d)+" minutes based on recent matches. Your time may be faster or slower.";
		}
		catch(Exception de){de.printStackTrace();}
	}
	if(!queueStatementID.equals("")){
	%>
	
	<%
	}
	%>

<%

//kwQuery.closeAll();


}
catch(Exception e){
	e.printStackTrace();
	%>
	<p>Hit an error.<br /> <%=e.toString()%></p>


<%
}
finally{
	myShepherd.rollbackDBTransaction();
	myShepherd.closeDBTransaction();
	//kwQuery=null;
	myShepherd=null;
}

	}  //end if this is an encounter
    else {
  		myShepherd.rollbackDBTransaction();
  		myShepherd.closeDBTransaction();
		%> -->

<form action="biologicalSamples.jsp" method="post" name="encounter"><strong>Go
  to encounter: </strong> <input name="number" type="text" value="" size="20"> <input name="Go" type="submit" value="Submit" /></form>

<%
}
%>

</div>
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
