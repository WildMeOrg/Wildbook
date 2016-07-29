<%@ page contentType="text/html; charset=utf-8" language="java"
         import="javax.jdo.Query,org.ecocean.*,org.ecocean.servlet.ServletUtilities,java.io.File, java.util.*, org.ecocean.genetics.*, org.ecocean.security.Collaboration, com.google.gson.Gson" %>

<%

String blocker = "";
String context="context0";
context=ServletUtilities.getContext(request);

  //handle some cache-related security
  response.setHeader("Cache-Control", "no-cache"); //Forces caches to obtain a new copy of the page from the origin server
  response.setHeader("Cache-Control", "no-store"); //Directs caches not to store the page under any circumstance
  response.setDateHeader("Expires", 0); //Causes the proxy cache to see the page as "stale"
  response.setHeader("Pragma", "no-cache"); //HTTP 1.0 backward compatibility

  //setup data dir
  String rootWebappPath = getServletContext().getRealPath("/");
  File webappsDir = new File(rootWebappPath).getParentFile();
  File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));
  //if(!shepherdDataDir.exists()){shepherdDataDir.mkdirs();}
  File encountersDir=new File(shepherdDataDir.getAbsolutePath()+"/encounters");
  //if(!encountersDir.exists()){encountersDir.mkdirs();}
  //File thisEncounterDir = new File(encountersDir, number);

//setup our Properties object to hold all properties
  Properties props = new Properties();
  //String langCode = "en";
  String langCode=ServletUtilities.getLanguageCode(request);



  //load our variables for the submit page

  //props.load(getClass().getResourceAsStream("/bundles/" + langCode + "/occurrence.properties"));
  props = ShepherdProperties.getProperties("occurrence.properties", langCode,context);

	Properties collabProps = new Properties();
 	collabProps=ShepherdProperties.getProperties("collaboration.properties", langCode, context);

  String name = request.getParameter("number").trim();
  Shepherd myShepherd = new Shepherd(context);



  boolean isOwner = false;
  if (request.getUserPrincipal()!=null) {
    isOwner = true;
  }

%>



  <style type="text/css">
    <!--
    .style1 {
      color: #000000;
      font-weight: bold;
    }



    div.scroll {
      height: 200px;
      overflow: auto;
      border: 1px solid #666;
      background-color: #ccc;
      padding: 8px;
    }


    -->
  </style>


  <jsp:include page="header.jsp" flush="true"/>


  <!--
    1 ) Reference to the files containing the JavaScript and CSS.
    These files must be located on your server.
  -->

  <script type="text/javascript" src="highslide/highslide/highslide-with-gallery.js"></script>
  <link rel="stylesheet" type="text/css" href="highslide/highslide/highslide.css"/>

  <!--
    2) Optionally override the settings defined at the top
    of the highslide.js file. The parameter hs.graphicsDir is important!
  -->

  <script type="text/javascript">
    hs.graphicsDir = 'highslide/highslide/graphics/';

    hs.transitions = ['expand', 'crossfade'];
    hs.outlineType = 'rounded-white';
    hs.fadeInOut = true;
    //hs.dimmingOpacity = 0.75;

    //define the restraining box
    hs.useBox = true;
    hs.width = 810;
    hs.height = 250;
    hs.align = 'auto';
  	hs.anchor = 'top';

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

<!--  FACEBOOK LIKE BUTTON -->
<div id="fb-root"></div>
<script>(function(d, s, id) {
  var js, fjs = d.getElementsByTagName(s)[0];
  if (d.getElementById(id)) return;
  js = d.createElement(s); js.id = id;
  js.src = "//connect.facebook.net/en_US/all.js#xfbml=1";
  fjs.parentNode.insertBefore(js, fjs);
}(document, 'script', 'facebook-jssdk'));</script>

<!-- GOOGLE PLUS-ONE BUTTON -->
<script type="text/javascript">
  (function() {
    var po = document.createElement('script'); po.type = 'text/javascript'; po.async = true;
    po.src = 'https://apis.google.com/js/plusone.js';
    var s = document.getElementsByTagName('script')[0]; s.parentNode.insertBefore(po, s);
  })();
</script>



<div class="container maincontent">

<%
  myShepherd.beginDBTransaction();
  try {
    if (myShepherd.isOccurrence(name)) {


      Occurrence sharky = myShepherd.getOccurrence(name);
      boolean hasAuthority = ServletUtilities.isUserAuthorizedForOccurrence(sharky, request);


			ArrayList collabs = Collaboration.collaborationsForCurrentUser(request);
			boolean visible = sharky.canUserAccess(request);

			if (!visible) {
  			ArrayList<String> uids = sharky.getAllAssignedUsers();
				ArrayList<String> possible = new ArrayList<String>();
				for (String u : uids) {
					Collaboration c = null;
					if (collabs != null) c = Collaboration.findCollaborationWithUser(u, collabs);
					if ((c == null) || (c.getState() == null)) {
						User user = myShepherd.getUser(u);
						String fullName = u;
						if (user.getFullName()!=null) fullName = user.getFullName();
						possible.add(u + ":" + fullName.replace(",", " ").replace(":", " ").replace("\"", " "));
					}
				}
				String cmsg = "<p>" + collabProps.getProperty("deniedMessage") + "</p>";
				cmsg = cmsg.replace("'", "\\'");

				if (possible.size() > 0) {
    			String arr = new Gson().toJson(possible);
					blocker = "<script>$(document).ready(function() { $.blockUI({ message: '" + cmsg + "' + _collaborateMultiHtml(" + arr + ") }) });</script>";
				} else {
					cmsg += "<p><input type=\"button\" onClick=\"window.history.back()\" value=\"BACK\" /></p>";
					blocker = "<script>$(document).ready(function() { $.blockUI({ message: '" + cmsg + "' }) });</script>";
				}
			}
			out.println(blocker);

%>

<table><tr>

<td valign="middle">
 <h1><strong><img align="absmiddle" src="images/occurrence.png" />&nbsp;<%=props.getProperty("occurrence") %></strong>: <%=sharky.getOccurrenceID()%></h1>
<p class="caption"><em><%=props.getProperty("description") %></em></p>
 <table><tr valign="middle">
  <td>
    <!-- Google PLUS-ONE button -->
<g:plusone size="small" annotation="none"></g:plusone>
</td>
<td>
<!--  Twitter TWEET THIS button -->
<a href="https://twitter.com/share" class="twitter-share-button" data-count="none">Tweet</a>
<script>!function(d,s,id){var js,fjs=d.getElementsByTagName(s)[0];if(!d.getElementById(id)){js=d.createElement(s);js.id=id;js.src="//platform.twitter.com/widgets.js";fjs.parentNode.insertBefore(js,fjs);}}(document,"script","twitter-wjs");</script>
</td>
<td>
<!-- Facebook LIKE button -->
<div class="fb-like" data-send="false" data-layout="button_count" data-width="100" data-show-faces="false"></div>
</td>
</tr></table> </td></tr></table>

<p><%=props.getProperty("groupBehavior") %>:
<%
if(sharky.getGroupBehavior()!=null){
%>
	<%=sharky.getGroupBehavior() %>
<%
}
%>
&nbsp; <%if (hasAuthority && CommonConfiguration.isCatalogEditable(context)) {%><a id="groupB" style="color:blue;cursor: pointer;"><img align="absmiddle" width="20px" height="20px" style="border-style: none;" src="images/Crystal_Clear_action_edit.png" /></a><%}%>
</p>


<div id="dialogGroupB" title="<%=props.getProperty("setGroupBehavior") %>" style="display:none">

<table border="1" cellpadding="1" cellspacing="0" bordercolor="#FFFFFF">

  <tr>
    <td align="left" valign="top">
      <form name="set_groupBhevaior" method="post" action="OccurrenceSetGroupBehavior">
            <input name="number" type="hidden" value="<%=request.getParameter("number")%>" />
            <%=props.getProperty("groupBehavior") %>:

        <%
        if(CommonConfiguration.getProperty("occurrenceGroupBehavior0",context)==null){
        %>
        <textarea name="behaviorComment" type="text" id="behaviorComment" maxlength="500"></textarea>
        <%
        }
        else{
        %>

        	<select name="behaviorComment" id="behaviorComment">
        		<option value=""></option>

   				<%
   				boolean hasMoreStages=true;
   				int taxNum=0;
   				while(hasMoreStages){
   	  				String currentLifeStage = "occurrenceGroupBehavior"+taxNum;
   	  				if(CommonConfiguration.getProperty(currentLifeStage,context)!=null){
   	  				%>

   	  	  			<option value="<%=CommonConfiguration.getProperty(currentLifeStage,context)%>"><%=CommonConfiguration.getProperty(currentLifeStage,context)%></option>
   	  				<%
   					taxNum++;
      				}
      				else{
         				hasMoreStages=false;
      				}

   				}
   			%>
  			</select>


        <%
        }
        %>
        <input name="groupBehaviorName" type="submit" id="Name" value="<%=props.getProperty("set") %>">
      </form> <!-- end of setGroupBehavior form -->
    </td>
  </tr>
</table>

                         		</div>
                         		<!-- popup dialog script -->
<script>
var dlgGroupB = $("#dialogGroupB").dialog({
  autoOpen: false,
  draggable: false,
  resizable: false,
  width: 600
});

$("a#groupB").click(function() {
  dlgGroupB.dialog("open");
});
</script>


<p><%=props.getProperty("numMarkedIndividuals") %>: <%=sharky.getMarkedIndividualNamesForThisOccurrence().size() %></p>

<p><%=props.getProperty("estimatedNumMarkedIndividuals") %>:
<%
if(sharky.getIndividualCount()!=null){
%>
	<%=sharky.getIndividualCount() %>
<%
}
%>
&nbsp; <%if (hasAuthority && CommonConfiguration.isCatalogEditable(context)) {%><a id="indies" style="color:blue;cursor: pointer;"><img align="absmiddle" width="20px" height="20px" style="border-style: none;" src="images/Crystal_Clear_action_edit.png" /></a><%}%>
</p>


<div class="row">
<form method="post" action="occurrence.jsp" id="occform">
<input name="number" type="hidden" value="<%=sharky.getOccurrenceID()%>" />


<p>
<strong>Habitat</strong>
<input name="occ:habitat" value="<%=sharky.getHabitat()%>" />
</p>

<p>
<strong>Group Size</strong>
<input name="occ:groupSize" value="<%=sharky.getGroupSize()%>" />
</p>

<p>
<strong>Number Territorial Males</strong>
<input name="occ:numTerMales" value="<%=sharky.getNumTerMales()%>" />
</p>

<p>
<strong>Number Bachelor Males</strong>
<input name="occ:numBachMales" value="<%=sharky.getNumBachMales()%>" />
</p>

<p>
<strong>Number Lactating Females</strong>
<input name="occ:numLactFemales" value="<%=sharky.getNumLactFemales()%>" />
</p>

<p>
<strong>Number Non-lactating Females</strong>
<input name="occ:numNonLactFemales" value="<%=sharky.getNumNonLactFemales()%>" />
</p>


<div style="position: relative; height: 40px;">

<div style="position: absolute; top: 0; left: 0;">
<strong>Decimal Latitude</strong>
<input name="occ:decimalLatitude" value="<%=sharky.getDecimalLatitude()%>" />
</div>

<div style="position: absolute; top: 0; right: 0;">
<strong>Decimal Longitude</strong>
<input name="occ:decimalLongitude" value="<%=sharky.getDecimalLongitude()%>" />
</div>

</div>

<p>
<strong>Distance (meters)</strong>
<input name="occ:distance" value="<%=sharky.getDistance()%>" />
</p>

<p>
<strong>Bearing (degrees from north)</strong>
<input name="occ:bearing" value="<%=sharky.getBearing()%>" />
</p>

<div class="submit">
<input type="submit" name="save" value="Save" />
<div class="note"></div>
</div>

</form>
</div> <!--row -->

<script type="text/javascript">
$(document).ready(function() {

  var changedFields = {};

	$('#occform input,#occform select').change(function() {
    var str = $(this).val();
    console.log('Change handler called on elem' + $(this).attr("name") + " to new val " + str);
    changedFields[$(this).attr("name")] = str;
		$('.submit').addClass('changes-made');
		$('.submit .note').html('changes made. please save.');
    console.log('changedFields = '+JSON.stringify(changedFields));
    <%  /*out.println("WHOAH WE HAVE A CHANGE HERE");*/
        System.out.println("WHOAH WE HAVE A CHANGE HERE");
    %>
	});
	$('span.relationship').hover(function(ev) {
//$('tr[data-indiv="07_091"]').hide();
console.log(ev);
		var jel = $(ev.target);
		if (ev.type == 'mouseenter') {
			var p = jel.data('partner');
			$('tr[data-indiv="' + p + '"]').addClass('rel-partner');
		} else {
			$('.rel-partner').removeClass('rel-partner');
		}
	});
	$('.enc-row').each(function(i, el) {
		var eid = el.getAttribute('data-id');
		el.setAttribute('title', 'click for: ' + eid);
	});
	$('.enc-row').click(function(el) {
		var eid = el.currentTarget.getAttribute('data-id');
		var w = window.open('encounters/encounter.jsp?number=' + eid, '_blank');
		w.focus();
		return false;
	});

	$('.col-sex').each(function(i, el) {
		var p = $('<select><option value="">select sex</option><option>unknown</option><option>male</option><option>female</option></select>');
		p.click( function(ev) { ev.stopPropagation(); } );
		p.change(function() {
			columnChange(this);
		});
		p.val($(el).html());
		$(el).html(p);
		//console.log('%o: %o', i, el);
	});

	$('.col-ageAtFirstSighting').each(function(i, el) {
		if (!$(el).parent().data('indiv')) return;
		var p = $('<input style="width: 30px;" placeholder="yrs" />');
		p.click( function(ev) { ev.stopPropagation(); } );
		p.change(function() {
			columnChange(this);
		});
		p.val($(el).html());
		$(el).html(p);
	});

	$('.col-zebraClass').each(function(i, el) {
		var p = $('<select><option value="Unknown">Unknown</option><option>LF</option><option>NLF</option><option>TM</option><option>BM</option><option>JUV</option><option>FOAL</option></select>');
		p.click( function(ev) { ev.stopPropagation(); } );
		p.change(function() {
			columnChange(this);
		});
		p.val($(el).html());
		$(el).html(p);
	});


});


function columnChange(el) {
	var jel = $(el);
	var prop = jel.parent().data('prop');
	var eid = jel.parent().parent().data('id');
	$('[name="' + eid + ':' + prop + '"]').remove();  //clear exisiting
	$('<input>').attr({
		name: eid + ':' + prop,
		type: 'hidden',
		value: jel.val(),
	}).appendTo($('#occform'));

	$('.submit').addClass('changes-made');
	$('.submit .note').html('changes made. please save.');

}


var relEid = false;
function relAdd(ev) {
console.log(ev);
	var jel = $(ev.target);
	var eid = jel.parent().parent().data('id');
	relEid = eid;
	ev.stopPropagation();
	$('#relationships-form input[type="text"]').val(undefined);
	$('#relationships-form select').val(undefined);
	$('#relationships-form').appendTo(ev.target).show();
	$('#relationships-form input[type="text"]').focus();
}

function relSave(ev) {
	ev.stopPropagation();
	if (!relEid) return;

	var partner = $('#rel-child-id').val();

	$('<input>').attr({
		name: relEid + ':_relMother',
		type: 'hidden',
		value: partner,
	}).appendTo($('#occform'));
	//$('#rel-child-id').val('');

	$('#row-enc-' + relEid + ' .col-relationships').prepend('<span data-partner="' + partner + '" class="relationship relType-familial relRole-mother">' + partner + '</span>');

	relEid = false;
	$('#relationships-form').hide();
	$('.submit').addClass('changes-made');
	$('.submit .note').html('changes made. please save.');
}

function relCancel(ev) {
	ev.stopPropagation();
	relEid = false;
	$('#relationships-form').hide();
}

</script>




<div id="dialogIndies" title="<%=props.getProperty("setIndividualCount") %>" style="display:none">

<table border="1" cellpadding="1" cellspacing="0" bordercolor="#FFFFFF" >

  <tr>
    <td align="left" valign="top">
      <form name="set_individualCount" method="post" action="OccurrenceSetIndividualCount">
            <input name="number" type="hidden" value="<%=request.getParameter("number")%>" />
            <%=props.getProperty("newIndividualCount") %>:

        <input name="count" type="text" id="count" size="5" maxlength="7"></input>
        <input name="individualCountButton" type="submit" id="individualCountName" value="<%=props.getProperty("set") %>">
        </form>
    </td>
  </tr>
</table>

                         		</div>
                         		<!-- popup dialog script -->
<script>
var dlgIndies = $("#dialogIndies").dialog({
  autoOpen: false,
  draggable: false,
  resizable: false,
  width: 600
});

$("a#indies").click(function() {
  dlgIndies.dialog("open");
});
</script>




<p><%=props.getProperty("locationID") %>:
<%
if(sharky.getLocationID()!=null){
%>
	<%=sharky.getLocationID() %>
<%
}
%>
</p>
<table id="encounter_report" width="100%">
<tr>

<td align="left" valign="top">

<p><strong><%=sharky.getNumberEncounters()%>
</strong>
  <%=props.getProperty("numencounters") %>
</p>

<table id="results" width="100%">
  <tr class="lineitem">
    <td class="lineitem" align="left" valign="top" bgcolor="#99CCFF"><strong><%=props.getProperty("date") %></strong></td>
    <td class="lineitem" align="left" valign="top" bgcolor="#99CCFF"><strong><%=props.getProperty("individualID") %></strong></td>

    <td class="lineitem" align="left" valign="top" bgcolor="#99CCFF"><strong><%=props.getProperty("location") %></strong></td>
    <td class="lineitem" bgcolor="#99CCFF"><strong><%=props.getProperty("dataTypes") %></strong></td>
    <td class="lineitem" align="left" valign="top" bgcolor="#99CCFF"><strong><%=props.getProperty("encnum") %></strong></td>
    <td class="lineitem" align="left" valign="top" bgcolor="#99CCFF"><strong><%=props.getProperty("alternateID") %></strong></td>

    <td class="lineitem" align="left" valign="top" bgcolor="#99CCFF"><strong><%=props.getProperty("sex") %></strong></td>
    <%
      if (isOwner && CommonConfiguration.useSpotPatternRecognition(context)) {
    %>

    	<td align="left" valign="top" bgcolor="#99CCFF">
    		<strong><%=props.getProperty("spots") %></strong>
    	</td>
    <%
    }
    %>
   <td class="lineitem" align="left" valign="top" bgcolor="#99CCFF"><strong><%=props.getProperty("behavior") %></td>
 <td class="lineitem" align="left" valign="top" bgcolor="#99CCFF"><strong><%=props.getProperty("haplotype") %></td>

  </tr>
  <%
    Encounter[] dateSortedEncs = sharky.getDateSortedEncounters(false);

    int total = dateSortedEncs.length;
    for (int i = 0; i < total; i++) {
      Encounter enc = dateSortedEncs[i];

        Vector encImages = enc.getAdditionalImageNames();
        String imgName = "";
				String encSubdir = enc.subdir();

          imgName = "/"+CommonConfiguration.getDataDirectoryName(context)+"/encounters/" + encSubdir + "/thumb.jpg";

  %>
  <tr>
      <td class="lineitem"><%=enc.getDate()%>
    </td>
    <td class="lineitem">
    	<%
    	if((enc.getIndividualID()!=null)&&(!enc.getIndividualID().toLowerCase().equals("unassigned"))){
    	%>
    	<a href="individuals.jsp?number=<%=enc.getIndividualID()%>"><%=enc.getIndividualID()%></a>
    	<%
    	}
    	else{
    	%>
    	&nbsp;
    	<%
    	}
    	%>
    </td>
    <%
    String location="&nbsp;";
    if(enc.getLocation()!=null){
    	location=enc.getLocation();
    }
    %>
    <td class="lineitem"><%=location%>
    </td>
    <td width="100" height="32px" class="lineitem">
    	<a href="http://<%=CommonConfiguration.getURLLocation(request)%>/encounters/encounter.jsp?number=<%=enc.getEncounterNumber()%>">

    		<%
    		//if the encounter has photos, show photo folder icon
    		if((enc.getImages()!=null) && (enc.getImages().size()>0)){
    		%>
    			<img src="images/Crystal_Clear_filesystem_folder_image.png" height="32px" width="*" />
    		<%
    		}

    		//if the encounter has a tissue sample, show an icon
    		if((enc.getTissueSamples()!=null) && (enc.getTissueSamples().size()>0)){
    		%>
    			<img src="images/microscope.gif" height="32px" width="*" />
    		<%
    		}
    		//if the encounter has a measurement, show the measurement icon
    		if(enc.hasMeasurements()){
    		%>
    			<img src="images/ruler.png" height="32px" width="*" />
        	<%
    		}
    		%>

    	</a>
    </td>
    <td class="lineitem"><a
      href="http://<%=CommonConfiguration.getURLLocation(request)%>/encounters/encounter.jsp?number=<%=enc.getEncounterNumber()%><%if(request.getParameter("noscript")!=null){%>&noscript=null<%}%>"><%=enc.getEncounterNumber()%>
    </a></td>

    <%
      if (enc.getAlternateID() != null) {
    %>
    <td class="lineitem"><%=enc.getAlternateID()%>
    </td>
    <%
    } else {
    %>
    <td class="lineitem"><%=props.getProperty("none")%>
    </td>
    <%
      }
    %>


<%
String sexValue="&nbsp;";
if(enc.getSex()!=null){sexValue=enc.getSex();}
%>
    <td class="lineitem"><%=sexValue %></td>

    <%
      if (CommonConfiguration.useSpotPatternRecognition(context)) {
    %>
    <%if (((enc.getSpots().size() == 0) && (enc.getRightSpots().size() == 0)) && (isOwner)) {%>
    <td class="lineitem">&nbsp;</td>
    <% } else if (isOwner && (enc.getSpots().size() > 0) && (enc.getRightSpots().size() > 0)) {%>
    <td class="lineitem">LR</td>
    <%} else if (isOwner && (enc.getSpots().size() > 0)) {%>
    <td class="lineitem">L</td>
    <%} else if (isOwner && (enc.getRightSpots().size() > 0)) {%>
    <td class="lineitem">R</td>
    <%
        }
      }
    %>


    <td class="lineitem">
    <%
    if(enc.getBehavior()!=null){
    %>
    <%=enc.getBehavior() %>
    <%
    }
    else{
    %>
    &nbsp;
    <%
    }
    %>
    </td>

  <td class="lineitem">
    <%
    if(enc.getHaplotype()!=null){
    %>
    <%=enc.getHaplotype() %>
    <%
    }
    else{
    %>
    &nbsp;
    <%
    }
    %>
    </td>
  </tr>
  <%

    } //end for

  %>


</table>


<!-- Start thumbnail gallery -->

<br />
<p>
  <strong><%=props.getProperty("imageGallery") %>
  </strong></p>

    <%
    String[] keywords=keywords=new String[0];
		int numThumbnails = myShepherd.getNumThumbnails(sharky.getEncounters().iterator(), keywords);
		if(numThumbnails>0){
		%>

<table id="results" border="0" width="100%">
    <%


			int countMe=0;
			//Vector thumbLocs=new Vector();
			ArrayList<SinglePhotoVideo> thumbLocs=new ArrayList<SinglePhotoVideo>();

			int  numColumns=3;
			int numThumbs=0;
			  if (CommonConfiguration.allowAdoptions(context)) {
				  ArrayList adoptions = myShepherd.getAllAdoptionsForMarkedIndividual(name,context);
				  int numAdoptions = adoptions.size();
				  if(numAdoptions>0){
					  numColumns=2;
				  }
			  }

			try {


			    Query query = myShepherd.getPM().newQuery("SELECT from org.ecocean.Encounter WHERE occurrenceID == \""+sharky.getOccurrenceID()+"\"");
		        //query.setFilter("SELECT "+jdoqlQueryString);
		        query.setResult("catalogNumber");
		        Collection c = (Collection) (query.execute());
		        ArrayList<String> enclist = new ArrayList<String>(c);
		        query.closeAll();

			    thumbLocs=myShepherd.getThumbnails(myShepherd,request, enclist, 1, 99999, keywords);
				numThumbs=thumbLocs.size();
			%>

  <tr valign="top">
 <td>
 <!-- HTML Codes by Quackit.com -->
<div style="text-align:left;border:1px solid black;width:100%;height:400px;overflow-y:scroll;overflow-x:scroll;">

      <%
      						while(countMe<numThumbs){
							//for(int columns=0;columns<numColumns;columns++){
								if(countMe<numThumbs) {
									//String combined ="";
									//if(myShepherd.isAcceptableVideoFile(thumbLocs.get(countMe).getFilename())){
									//	combined = "http://" + CommonConfiguration.getURLLocation(request) + "/images/video.jpg" + "BREAK" + thumbLocs.get(countMe).getCorrespondingEncounterNumber() + "BREAK" + thumbLocs.get(countMe).getFilename();
									//}
									//else{
									//	combined= thumbLocs.get(countMe).getCorrespondingEncounterNumber() + "/" + thumbLocs.get(countMe).getDataCollectionEventID() + ".jpg" + "BREAK" + thumbLocs.get(countMe).getCorrespondingEncounterNumber() + "BREAK" + thumbLocs.get(countMe).getFilename();

									//}

									//StringTokenizer stzr=new StringTokenizer(combined,"BREAK");
									//String thumbLink=stzr.nextToken();
									//String encNum=stzr.nextToken();
									//int fileNamePos=combined.lastIndexOf("BREAK")+5;
									//String fileName=combined.substring(fileNamePos).replaceAll("%20"," ");
									String thumbLink="";
									boolean video=true;
									if(!myShepherd.isAcceptableVideoFile(thumbLocs.get(countMe).getFilename())){
										thumbLink="/"+CommonConfiguration.getDataDirectoryName(context)+"/encounters/"+Encounter.subdir(thumbLocs.get(countMe).getCorrespondingEncounterNumber())+"/"+thumbLocs.get(countMe).getDataCollectionEventID()+".jpg";
										video=false;
									}
									else{
										thumbLink="http://"+CommonConfiguration.getURLLocation(request)+"/images/video.jpg";

									}
									String link="/"+CommonConfiguration.getDataDirectoryName(context)+"/encounters/"+Encounter.subdir(thumbLocs.get(countMe).getCorrespondingEncounterNumber())+"/"+thumbLocs.get(countMe).getFilename();

							%>



      <table align="left" width="<%=100/numColumns %>%">
        <tr>
          <td valign="top">

              <%
			if(isOwner){
												%>
            <a href="<%=link%>"
            <%
            if(thumbLink.indexOf("video.jpg")==-1){
            %>
            	class="highslide" onclick="return hs.expand(this)"
            <%
            }
            %>
            >
            <%
            }
             %>
              <img src="<%=thumbLink%>" alt="photo" border="1" title="Click to enlarge"/>
              <%
                if (isOwner) {
              %>
            </a>
              <%
			}

			%>

            <div
            <%
            if(!thumbLink.endsWith("video.jpg")){
            %>
            class="highslide-caption"
            <%
            }
            %>
            >

              <table>
                <tr>
                  <td align="left" valign="top">

                    <table>
                      <%

                        int kwLength = keywords.length;
                        Encounter thisEnc = myShepherd.getEncounter(thumbLocs.get(countMe).getCorrespondingEncounterNumber());
                      %>



                      <tr>
                        <td><span
                          class="caption"><%=props.getProperty("location") %>: <%=thisEnc.getLocation() %></span>
                        </td>
                      </tr>
                      <tr>
                        <td><span
                          class="caption"><%=props.getProperty("locationID") %>: <%=thisEnc.getLocationID() %></span>
                        </td>
                      </tr>
                      <tr>
                        <td><span
                          class="caption"><%=props.getProperty("date") %>: <%=thisEnc.getDate() %></span>
                        </td>
                      </tr>
                      <tr>
                        <td><span class="caption"><%=props.getProperty("catalogNumber") %>: <a
                          href="encounters/encounter.jsp?number=<%=thisEnc.getCatalogNumber() %>"><%=thisEnc.getCatalogNumber() %>
                        </a></span></td>
                      </tr>
                        <tr>
                        <td><span class="caption"><%=props.getProperty("individualID") %>:

                        <%
                        		if((thisEnc.getIndividualID()!=null)&&(!thisEnc.getIndividualID().toLowerCase().equals("unassigned"))){
                        		%>
                        			<a href="individuals.jsp?number=<%=thisEnc.getIndividualID() %>"><%=thisEnc.getIndividualID() %></a>
                        		<%
                        		}
                        		%>

                        </span></td>
                      </tr>
                      <%
                        if (thisEnc.getVerbatimEventDate() != null) {
                      %>
                      <tr>

                        <td><span
                          class="caption"><%=props.getProperty("verbatimEventDate") %>: <%=thisEnc.getVerbatimEventDate() %></span>
                        </td>
                      </tr>
                      <%
                        }
                      %>
                      <tr>
                        <td><span class="caption">
											<%=props.getProperty("matchingKeywords") %>
											<%
											 //while (allKeywords2.hasNext()) {
					                          //Keyword word = (Keyword) allKeywords2.next();


					                          //if (word.isMemberOf(encNum + "/" + fileName)) {
											  //if(thumbLocs.get(countMe).getKeywords().contains(word)){

					                            //String renderMe = word.getReadableName();
												List<Keyword> myWords = thumbLocs.get(countMe).getKeywords();
												int myWordsSize=myWords.size();
					                            for (int kwIter = 0; kwIter<myWordsSize; kwIter++) {
					                              //String kwParam = keywords[kwIter];
					                              //if (kwParam.equals(word.getIndexname())) {
					                              //  renderMe = "<strong>" + renderMe + "</strong>";
					                              //}
					                      		 	%>
					 								<br/><%= ("<strong>" + myWords.get(kwIter).getReadableName() + "</strong>")%>
					 								<%
					                            }




					                          //    }
					                       // }

                          %>
										</span></td>
                      </tr>
                    </table>
                    <br/>

                    <%
                      if (CommonConfiguration.showEXIFData(context)) {

            	if(!thumbLink.endsWith("video.jpg")){
           		 %>
					<span class="caption">
						<div class="scroll">
						<span class="caption">
					<%
            if ((thumbLocs.get(countMe).getFilename().toLowerCase().endsWith("jpg")) || (thumbLocs.get(countMe).getFilename().toLowerCase().endsWith("jpeg"))) {
              File exifImage = new File(encountersDir.getAbsolutePath() + "/" + Encounter.subdir(thisEnc.getCatalogNumber()) + "/" + thumbLocs.get(countMe).getFilename());
              	%>
            	<%=Util.getEXIFDataFromJPEGAsHTML(exifImage) %>
            	<%
               }
                %>


   								</span>
            </div>
   								</span>
   			<%
            	}
   			%>


                  </td>
                  <%
                    }
                  %>
                </tr>
              </table>
            </div>


</td>
</tr>

 <%
            if(!thumbLink.endsWith("video.jpg")){
 %>
<tr>
  <td><span class="caption"><%=props.getProperty("location") %>: <%=thisEnc.getLocation() %></span>
  </td>
</tr>
<tr>
  <td><span
    class="caption"><%=props.getProperty("locationID") %>: <%=thisEnc.getLocationID() %></span></td>
</tr>
<tr>
  <td><span class="caption"><%=props.getProperty("date") %>: <%=thisEnc.getDate() %></span></td>
</tr>
<tr>
  <td><span class="caption"><%=props.getProperty("catalogNumber") %>: <a
    href="encounters/encounter.jsp?number=<%=thisEnc.getCatalogNumber() %>"><%=thisEnc.getCatalogNumber() %>
  </a></span></td>
</tr>
                        <tr>
                        	<td>
                        		<span class="caption"><%=props.getProperty("individualID") %>:
                        		<%
                        		if((thisEnc.getIndividualID()!=null)&&(!thisEnc.getIndividualID().toLowerCase().equals("unassigned"))){
                        		%>
                        			<a href="individuals.jsp?number=<%=thisEnc.getIndividualID() %>"><%=thisEnc.getIndividualID() %></a>
                        		<%
                        		}
                        		%>
                        		</span>
                        	</td>
                      </tr>
<tr>
  <td><span class="caption">
											<%=props.getProperty("matchingKeywords") %>
											<%
                        //int numKeywords=myShepherd.getNumKeywords();
											 //while (allKeywords2.hasNext()) {
					                          //Keyword word = (Keyword) allKeywords2.next();


					                          //if (word.isMemberOf(encNum + "/" + fileName)) {
											  //if(thumbLocs.get(countMe).getKeywords().contains(word)){

					                            //String renderMe = word.getReadableName();
												//List<Keyword> myWords = thumbLocs.get(countMe).getKeywords();
												//int myWordsSize=myWords.size();
					                            for (int kwIter = 0; kwIter<myWordsSize; kwIter++) {
					                              //String kwParam = keywords[kwIter];
					                              //if (kwParam.equals(word.getIndexname())) {
					                              //  renderMe = "<strong>" + renderMe + "</strong>";
					                              //}
					                      		 	%>
					 								<br/><%= ("<strong>" + myWords.get(kwIter).getReadableName() + "</strong>")%>
					 								<%
					                            }




					                          //    }
					                       // }

                          %>
										</span></td>
</tr>
<%

            }
%>
</table>

<%

      countMe++;
    } //end if
  } //endFor
%>
</div>

</td>
</tr>
<%



} catch (Exception e) {
  e.printStackTrace();
%>
<tr>
  <td>
    <p><%=props.getProperty("error")%>
    </p>.
  </td>
</tr>
<%
  }
%>

</table>
</div>
<%
} else {
%>

<p><%=props.getProperty("noImages")%></p>

<%
  }
%>

</table>
<!-- end thumbnail gallery -->



<br/>



<%

  if (isOwner) {
%>
<br />


<br />
<p><img align="absmiddle" src="images/Crystal_Clear_app_kaddressbook.gif"> <strong><%=props.getProperty("researcherComments") %>
</strong>: </p>

<div style="text-align:left;border:1px solid black;width:100%;height:400px;overflow-y:scroll;overflow-x:scroll;">

<p><%=sharky.getComments().replaceAll("\n", "<br>")%>
</p>
</div>
<%
  if (CommonConfiguration.isCatalogEditable(context)) {
%>
<p>

<form action="OccurrenceAddComment" method="post" name="addComments">
  <input name="user" type="hidden" value="<%=request.getRemoteUser()%>" id="user">
  <input name="number" type="hidden" value="<%=sharky.getOccurrenceID()%>" id="number">
  <input name="action" type="hidden" value="comments" id="action">

  <p><textarea name="comments" cols="60" id="comments"></textarea> <br>
    <input name="Submit" type="submit" value="<%=props.getProperty("addComments") %>">
</form>
</p>
<%
    } //if isEditable


  } //if isOwner
%>


</p>


</td>
</tr>


</table>

</td>
</tr>
</table>
</div><!-- end maintext -->
</div><!-- end main-wide -->


<br />
<table>
<tr>
<td>

      <jsp:include page="individualMapEmbed.jsp" flush="true">
        <jsp:param name="occurrence_number" value="<%=name%>"/>
      </jsp:include>
</td>
</tr>
</table>
<%

}

//could not find the specified individual!
else {



%>


<p><%=props.getProperty("matchingRecord") %>:
<br /><strong><%=request.getParameter("number")%>
</strong><br/><br />
  <%=props.getProperty("tryAgain") %>
</p>


<%
      }
	  %>
      </td>
</tr>
</table>




      <%

  } catch (Exception eSharks_jsp) {
    System.out.println("Caught and handled an exception in occurrence.jsp!");
    eSharks_jsp.printStackTrace();
  }



  myShepherd.rollbackDBTransaction();
  myShepherd.closeDBTransaction();

%>
</div>
<jsp:include page="footer.jsp" flush="true"/>
