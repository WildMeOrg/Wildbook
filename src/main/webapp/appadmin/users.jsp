<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.servlet.ServletUtilities,java.util.List,org.ecocean.*,
         java.util.Properties, java.util.Collection, java.util.Vector,java.util.ArrayList,
         org.datanucleus.api.rest.orgjson.JSONArray, org.json.JSONObject,
         org.datanucleus.api.rest.RESTUtils, org.datanucleus.api.jdo.JDOPersistenceManager,
         org.datanucleus.api.jdo.JDOPersistenceManager,org.datanucleus.FetchGroup,javax.jdo.*,
         org.datanucleus.ExecutionContext, org.datanucleus.PersistenceNucleusContext,
         org.datanucleus.api.jdo.JDOPersistenceManagerFactory" %>
<%@ page import="org.ecocean.shepherd.core.Shepherd" %>
<%@ page import="org.ecocean.shepherd.core.ShepherdProperties" %>


<style type="text/css">
	.required-missing {
		outline: solid 4px rgba(255,0,0,0.5);
		background-color: #FF0;
	}
</style>
  <%

String context="context0";
context=ServletUtilities.getContext(request);

Properties props = new Properties();
String langCode=ServletUtilities.getLanguageCode(request);
props = ShepherdProperties.getProperties("users.properties", langCode,context);
String localUsername="";
String localEmail="";

Shepherd myShepherd = new Shepherd(context);
myShepherd.setAction("users.jsp");

List<String> roles=CommonConfiguration.getIndexedPropertyValues("role",context);
List<String> roleDefinitions=CommonConfiguration.getIndexedPropertyValues("roleDefinition",context);
int numRoles=roles.size();
int numRoleDefinitions=roleDefinitions.size();
int numResults = 0;

List<User> users = new ArrayList<User>();
PersistenceManager pm=myShepherd.getPM();
JDOPersistenceManager jdopm = (JDOPersistenceManager)myShepherd.getPM();
PersistenceManagerFactory pmf = pm.getPersistenceManagerFactory();
PersistenceNucleusContext nucCtx = ((JDOPersistenceManagerFactory)pmf).getNucleusContext();
ExecutionContext ec = jdopm.getExecutionContext();



//javax.jdo.FetchGroup grp = pmf.getFetchGroup(User.class, "users");
//grp.addMember("username").addMember("lastLogin").addMember("emailAddress").addMember("fullName").addMember("affiliation");
//javax.jdo.FetchGroup grp2 = pmf.getFetchGroup(Organization.class, "orgs");
//grp2.addMember("name").addMember("description");

//myShepherd.getPM().getFetchPlan().setGroup("orgs").addGroup("users");
//pm.getFetchPlan().setMaxFetchDepth(-1);
myShepherd.beginDBTransaction();


//String currentEmail = request.getParameter("email")	;
String currentUUID = request.getParameter("uuid")	;

try {

	String order ="lastLogin DESC NULLS LAST";

  	users = myShepherd.getAllUsers(order);
	numResults=users.size();

	List<Role> allRoles=myShepherd.getAllRoles();
	//int numRoles=allRoles.size();

	  %>





	<jsp:include page="../header.jsp" flush="true"/>

	<script src="../javascript/underscore-min.js"></script>
	<script src="../javascript/backbone-min.js"></script>
	<script src="../javascript/core.js"></script>
	<script src="../javascript/classes/Base.js"></script>

	<link rel="stylesheet" href="../javascript/tablesorter/themes/blue/style.css" type="text/css" media="print, projection, screen" />

	<link rel="stylesheet" href="../css/pageableTable.css" />
	<script src="../javascript/tsrt.js"></script>


	<div class="container maincontent">


	      <h1 class="intro">
	        <%=props.getProperty("title")%>
	      </h1>


	  <%

    //set up the statistics counters


    //Vector histories = new Vector();
    //int usersSize=users.size();

    int count = 0;
    JSONArray jsonobj=new JSONArray();
    for(User user:users){
    	jsonobj.put(user.uiJson(request, true));
    }


		//JSONArray jsonobj = RESTUtils.getJSONArrayFromCollection((Collection)users, ec);
		String indsJson = jsonobj.toString();

	   JSONArray rolesobj=	RESTUtils.getJSONArrayFromCollection((Collection)allRoles, ec);
		String rolesJson = rolesobj.toString();


	%>

	<script type="text/javascript">

	var searchResults = <%=indsJson%>;
	var allRoles=<%=rolesJson%>;


	var resultsTable;


	$(document).keydown(function(k) {
		if ((k.which == 38) || (k.which == 40) || (k.which == 33) || (k.which == 34)) k.preventDefault();
		if (k.which == 38) return tableDn();
		if (k.which == 40) return tableUp();
		if (k.which == 33) return nudge(-howMany);
		if (k.which == 34) return nudge(howMany);
	});

	var colDefn = [

		{
			key: 'username',
			label: "<%=props.getProperty("username") %>",
			value: _colUsername,
			sortValue: function(o) { return o.username; },
		},

		{
			key: 'fullName',
			label: '<%=props.getProperty("fullname")%>',
			value: _colFullName
		},
		{
			key: 'emailAddress',
			label: '<%=props.getProperty("emailAddress")%>',
			value: _colEmailAddress
		},
		{
			key: 'organization',
			label: '<%=props.getProperty("organization")%>',
			value: _colOrganization
		},
		{
			key: 'lastLogin',
			label: '<%=props.getProperty("lastLogin")%>',
			value: _colLastLogin,
			sortValue: _colLastLoginSort
		},
		{
			key: 'roles',
			label: '<%=props.getProperty("roles")%>',
			value: _getRolesForUser
		}

	];


	var howMany = 10;
	var start = 0;
	var results = [];

	var sortCol = 4;
	var sortReverse = true;


	var counts = {
		total: 0,
		ided: 0,
		unid: 0,
		dailydup: 0,
	};

	var sTable = false;
	//var searchResultsObjects = [];

	function doTable() {


		sTable = new SortTable({
			data: searchResults,
			perPage: howMany,
			sliderElement: $('#results-slider'),
			columns: colDefn,
		});

		$('#results-table').addClass('tablesorter').addClass('pageableTable');
		var th = '<thead><tr>';
			for (var c = 0 ; c < colDefn.length ; c++) {
				var cls = 'ptcol-' + colDefn[c].key;
				if (!colDefn[c].nosort) {
					if (sortCol < 0) { //init
						sortCol = c;
						cls += ' headerSortUp';
					}
					cls += ' header" onClick="return headerClick(event, ' + c + ');';
				}
				th += '<th class="' + cls + '">' + colDefn[c].label + '</th>';
			}
		$('#results-table').append(th + '</tr></thead>');
		for (var i = 0 ; i < howMany ; i++) {
			var r = '<tr onClick="return rowClick(this);" class="clickable pageableTable-visible">';
			for (var c = 0 ; c < colDefn.length ; c++) {
				r += '<td class="ptcol-' + colDefn[c].key + '"></td>';
			}
			r += '</tr>';
			$('#results-table').append(r);
		}

		sTable.initSort();
		sTable.initValues();


		newSlice(sortCol);

		$('#progress').hide();
		sTable.sliderInit();
		show();
		computeCounts();
		displayCounts();

		$('#results-table').on('wheel', function(ev) {  //firefox? DOMMouseScroll
			if (!sTable.opts.sliderElement) return;
			ev.preventDefault();
			var delta = Math.max(-1, Math.min(1, (event.wheelDelta || -event.detail)));
			if (delta != 0) nudge(-delta);
		});
		nudge(-1);

	}

	function rowClick(el) {
		console.log(el);
		var w = window.open('users.jsp?isEdit=true&uuid=' + el.getAttribute('data-id')+'#editUser', '_self');
		w.focus();
		return false;
	}

	function headerClick(ev, c) {
		start = 0;
		ev.preventDefault();
		console.log(c);
		if (sortCol == c) {
			sortReverse = !sortReverse;
		} else {
			sortReverse = false;
		}
		sortCol = c;

		$('#results-table th.headerSortDown').removeClass('headerSortDown');
		$('#results-table th.headerSortUp').removeClass('headerSortUp');
		if (sortReverse) {
			$('#results-table th.ptcol-' + colDefn[c].key).addClass('headerSortUp');
		} else {
			$('#results-table th.ptcol-' + colDefn[c].key).addClass('headerSortDown');
		}
	console.log('sortCol=%d sortReverse=%o', sortCol, sortReverse);
		newSlice(sortCol, sortReverse);
		show();
	}






	function show() {
		$('#results-table td').html('');
		$('#results-table tbody tr').show();
		for (var i = 0 ; i < results.length ; i++) {
			//$('#results-table tbody tr')[i].title = 'Encounter ' + searchResults[results[i]].id;
			$('#results-table tbody tr')[i].setAttribute('data-id', searchResults[results[i]].uuid);
			for (var c = 0 ; c < colDefn.length ; c++) {
				$('#results-table tbody tr')[i].children[c].innerHTML = '<div>' + sTable.values[results[i]][c] + '</div>';
			}
		}
		if (results.length < howMany) {
			$('#results-slider').hide();
			for (var i = 0 ; i < (howMany - results.length) ; i++) {
				$('#results-table tbody tr')[i + results.length].style.display = 'none';
			}
		} else {
			$('#results-slider').show();
		}

		//if (sTable.opts.sliderElement) sTable.opts.sliderElement.slider('option', 'value', 100 - (start / (searchResults.length - howMany)) * 100);
		sTable.sliderSet(100 - (start / (sTable.matchesFilter.length - howMany)) * 100);
		displayPagePosition();
	}


	function computeCounts() {
		counts.total = sTable.matchesFilter.length;
		return;  //none of the below applies here! (cruft from encounters for prosperity)
		counts.unid = 0;
		counts.ided = 0;
		counts.dailydup = 0;
		var uniq = {};

		for (var i = 0 ; i < counts.total ; i++) {
			console.log('>>>>> what up? %o', searchResults[sTable.matchesFilter[i]]);
			var iid = searchResults[sTable.matchesFilter[i]].individualID;
			if (iid == 'Unassigned') {
				counts.unid++;
			} else {
				var k = iid + ':' + searchResults[sTable.matchesFilter[i]].get('year') + ':' + searchResults[sTable.matchesFilter[i]].get('month') + ':' + searchResults[sTable.matchesFilter[i]].get('day');
				if (!uniq[k]) {
					uniq[k] = true;
					counts.ided++;
				} else {
					counts.dailydup++;
				}
			}
		}
	}


	function displayCounts() {
		for (var w in counts) {
			$('#count-' + w).html(counts[w]);
		}
	}


	function displayPagePosition() {
		if (sTable.matchesFilter.length < 1) {
			$('#table-info').html('<b>no matches found</b>');
			return;
		}

		var max = start + howMany;
		if (sTable.matchesFilter.length < max) max = sTable.matchesFilter.length;
		$('#table-info').html((start+1) + ' - ' + max + ' of ' + sTable.matchesFilter.length);
	}
	function newSlice(col, reverse) {
		results = sTable.slice(col, start, start + howMany, reverse);
	}



	function nudge(n) {
		start += n;
		if ((start + howMany) > sTable.matchesFilter.length) start = sTable.matchesFilter.length - howMany;
		if (start < 0) start = 0;
	console.log('start -> %d', start);
		newSlice(sortCol, sortReverse);
		show();
	}

	function tableDn() {
		return nudge(-1);
		start--;
		if (start < 0) start = 0;
		newSlice(sortCol, sortReverse);
		show();
	}

	function tableUp() {
		return nudge(1);
		start++;
		if (start > sTable.matchesFilter.length - 1) start = sTable.matchesFilter.length - 1;
		newSlice(sortCol, sortReverse);
		show();
	}



	////////
	$(document).ready( function() {
		wildbook.init(function() { doTable(); });
	});


	var tableContents = document.createDocumentFragment();


	function _colUsername(o) {
		if (o.username == undefined) return '';
		var i = '<b>' + o.username + '</b>';
		return i;
	}

	function _colFullName(o) {
		if (o.fullName == undefined) return '';
		return o.fullName;
	}

	function _colEmailAddress(o) {
		if (o.emailAddress == undefined) return '';
		return o.emailAddress;
	}

	function _colOrganization(o) {
		if (o.organizations == undefined) return '';
		var orgs=JSON.parse("["+o.organizations+"]");
		var result='';
		for(var i = 0; i < orgs.length; i++) {
		    result = result+orgs[i].name+'<br>';

		}
		return result;
	}




	function _colRowNum(o) {
		return o._rowNum;
	}




	function _colLastLogin(o) {
		if (!o.lastLogin) return '';
		if (o.lastLogin === "-1") return '';
		var s = new Date(parseInt(o.lastLogin)).toString();
		return s;
	}

	function _colLastLoginSort(o) {
		var m = o.lastLogin;
		if (!m) return '';
		//var d = wildbook.parseDate(m);
		//if (!wildbook.isValidDate(d)) return '';
		//return d.getTime();
		return m;
	}


	function _textExtraction(n) {
		var s = $(n).text();
		var skip = new RegExp('^(none|unassigned|)$', 'i');
		if (skip.test(s)) return 'zzzzz';
		return s;
	}

	function applyFilter() {
		var t = $('#filter-text').val();
		console.log(t);
		sTable.filter(t);
		start = 0;
		newSlice(1);
		show();
		computeCounts();
		displayCounts();
	}

	function _getRolesForUser(o){
		if(!o.username) return '';
		var uName=o.username;
		var myRoles='';
		//return 'test';
		$.each( allRoles, function( i, val ) {
			  if(val.username === uName) myRoles=myRoles+'<br>'+val.context+':'+val.rolename;
		});
		return myRoles.replace('<br>','');

	}

	//Checkbox switch for delete user
	function deleteUser() {
		if (document.getElementById("deleteCheck").checked) {
			$('#deleteButton').show();
			$('#Create').hide();
		} else {
			$('#deleteButton').hide();
			$('#Create').show();
		}
	}


	</script>

	<p class="table-filter-text">
	<input placeholder="<%=props.getProperty("filterByText") %>" id="filter-text" onChange="return applyFilter()" />
	<input type="button" value="<%=props.getProperty("filter") %>" />
	<input type="button" value="<%=props.getProperty("clear") %>" onClick="$('#filter-text').val(''); applyFilter(); return true;" />
	<span style="margin-left: 40px; color: #888; font-size: 0.8em;" id="table-info"></span>
	</p>

	<div class="pageableTable-wrapper">
		<div id="progress">loading...</div>
		<table id="results-table"></table>
		<div id="results-slider"></div>
	</div>


	<%
	    boolean includeZeroYears = true;
	    numResults = count;
	  %>


	<p>
	<table width="810" border="0" cellspacing="0" cellpadding="0">
	  <tr>
	    <td align="left">
	      <p><strong><%=props.getProperty("totalUsers")%>
	      </strong>: <span id="count-total"></span>
	      </p>


	    </td>
	    <%

    %>
  </tr>
</table>
</p>

	<h4 class="intro"><a name="editUser" /></a><%=props.getProperty("createEditUser")%></h4>
	<p>
	<%
	String isEditAddition="";
	if(request.getParameter("isEdit")!=null){isEditAddition="&isEdit=true";}
	%>

    <table class="tissueSample">

    <%
    //let's set up any pre-defined values if appropriate
    String localAffiliation="";
    String localFullName="";
    String profilePhotoURL="../images/user-profile-grey-grey.png";
    String userProject="";
    String userStatement="";
    String userURL="";
    String uuid="";
    String receiveEmails="checked=\"checked\"";
    boolean hasProfilePhoto=false;

    if((request.getParameter("isEdit")!=null)&&(myShepherd.getUserByUUID(request.getParameter("uuid"))!=null)){
    	User thisUser=myShepherd.getUserByUUID(request.getParameter("uuid"));
    	if(thisUser.getUsername()!=null){
    		localUsername=thisUser.getUsername();
    	}
    	if(thisUser.getAffiliation()!=null){
    		localAffiliation=thisUser.getAffiliation();
    	}
    	if(thisUser.getEmailAddress()!=null){
    		localEmail=thisUser.getEmailAddress();
    	}
    	if(!thisUser.getReceiveEmails()){receiveEmails="";}
    	if(thisUser.getFullName()!=null){
    		localFullName=thisUser.getFullName();
    	}
    	if(thisUser.getUserProject()!=null){
  			userProject=thisUser.getUserProject();
    	}
    	if(thisUser.getUserStatement()!=null){
    		userStatement=thisUser.getUserStatement();
    	}
    	if(thisUser.getUserURL()!=null){
    		userURL=thisUser.getUserURL();
    	}
    	if(thisUser.getUserImage()!=null){
    		profilePhotoURL="/"+CommonConfiguration.getDataDirectoryName(context)+"/users/"+thisUser.getUsername()+"/"+thisUser.getUserImage().getFilename();
    	}
    	if(thisUser.getUserImage()!=null){hasProfilePhoto=true;}
    	uuid=thisUser.getUUID();
    }
    else if (request.getParameter("uuid")==null){
       uuid=Util.generateUUID();
    }

    %>

    <tr>
    	<td style="width: 200px;">
    		<table style="border: solid 0;">
    			<tr>
    				<td style="border: solid 0;border-spacing: 0;width: 200px;">
    					<img src="<%=profilePhotoURL%>" width="200px" height="*" />
    				</td>
    			</tr>
    			<%
    			if(request.getParameter("isEdit")!=null){
    			%>
    			<tr>
    					<td style="border: solid 0">
    						<form action="../UserAddProfileImage?context=context0" method="post" enctype="multipart/form-data" name="UserAddProfileImage">
								<img src="../images/upload_small.gif" align="absmiddle" />&nbsp;<%=props.getProperty("uploadPhoto")%><br />
    						 <input name="username" type="hidden" value="<%=localUsername%>" id="profileUploadUsernameField" />
								<input name="file2add" type="file" style="width: 200px"/>
								<input name="addtlFile" type="submit" id="addtlFile" value="Upload" />
							</form>
    					</td>
    				</tr>
    				<%
    				if(hasProfilePhoto){
    				%>
    					<tr><td style="border: solid 0"><%=props.getProperty("deleteProfile")%>&nbsp;<a href="../UserRemoveProfileImage?username=<%=localUsername%>"><img src="../images/cancel.gif" width="16px" height="16px" align="absmiddle" /></a></td></tr>

    				<%
    				}
    			}
    			%>
    			</table>
    	</td>
    	<form action="../UserCreate?context=context0<%=isEditAddition %>" method="post" id="newUser" accept-charset="UTF-8">
    	<td><table width="100%" class="tissueSample">
			<tr><td colspan="3"><em><%=props.getProperty("functionalFormDescription")%></em></td></tr>
			<tr>

        <%
        String disabled="";
        String readonly="";
        if(request.getParameter("isEdit")!=null){
        	//disabled="disabled=\"disabled\"";
        	readonly="readonly=\"readonly\"";
        }
    		%>
   	 		<input name="uuid" type="hidden" value="<%=uuid %>" id="uuid" />

		<div class="form-group required">
			<td><%=props.getProperty("username")%> <input autocomplete="off" name="username" id="username_input" type="text" size="15" maxlength="90" value="<%=localUsername %>" ></input></td>
		</div>


        <td><%=props.getProperty("password")%> <input name="password" type="password" size="15" maxlength="90" autocomplete="new-password"></input></td>
        <td><%=props.getProperty("confirm")%> <input autocomplete="off" name="password2" type="password" size="15" maxlength="90"></input></td>

    	</tr>

      <tr><td colspan="3"><%=props.getProperty("fullname")%> <input autocomplete="off" name="fullName" type="text" size="15" maxlength="90" value="<%=localFullName %>"></input></td></tr>

	<div class="form-group required">
		<tr><td colspan="2"><%=props.getProperty("emailAddress")%> <input type="email" autocomplete="off" name="emailAddress" id="emailAddress_input" type="text" size="15" maxlength="90" value="<%=localEmail %>"></input></td><td colspan="1">Receive automated emails? <input type="checkbox" name="receiveEmails" value="receiveEmails" <%=receiveEmails %>/></td></tr>
	</div>


      <tr><td colspan="3"><%=props.getProperty("affiliation")%> <input name="affiliation" type="text" size="15" maxlength="90" value="<%=localAffiliation %>"></input></td></tr>

      <tr><td colspan="3"><%=props.getProperty("researchProject")%> <input name="userProject" type="text" size="15" maxlength="90" value="<%=userProject %>"></input></td></tr>

      <tr><td colspan="3"><%=props.getProperty("projectURL")%> <input name="userURL" type="text" size="15" maxlength="90" value="<%=userURL %>"></input></td></tr>

			<tr><td colspan="3" valign="top"><%=props.getProperty("researchStatement")%> <textarea name="userStatement" size="100" maxlength="255"><%=userStatement%></textarea></td></tr>

      <tr>
				<td colspan="3">
					<input class="btn btn-sm btn-block" type="button" name="Create"  id="Create" value="<%=props.getProperty("save")%>" onclick="sendButtonClicked();" />
				</td>
			</tr>
    </table>
    </td>
    <td>
    <table>

    	<%
	    List<String> contexts=ContextConfiguration.getContextNames();
	    int numContexts=contexts.size();
	    for(int d=0;d<numContexts;d++) {
    	%>
    	<tr>
    		<td style="border-style: none;">
    			<%=props.getProperty("rolesFor")%> <%=ContextConfiguration.getNameForContext(("context"+d)) %> (multi-select)
    		</td>
    	</tr>
    	<tr>
    		<td style="border-style: none;">
	          <select multiple="multiple" name="context<%=d %>rolename" id="rolename" size="5">
	            <option value=""></option>
							<%
							for(int q=0;q<numRoles;q++){
								String selected="";
								if((request.getParameter("isEdit")!=null)&&(myShepherd.getUserByUUID(request.getParameter("uuid").trim())!=null)){
									if(myShepherd.doesUserHaveRole(localUsername,roles.get(q),("context"+d))){
										selected="selected=\"true\"";
									}
								}

								//now one last check: only let someone who has a role assign the role
								if(request.isUserInRole("admin") || request.isUserInRole(roles.get(q))){
									%><option value="<%=roles.get(q)%>" <%=selected%>><%=roles.get(q)%></option><%
								}
							}%>
	    			</select>
    		</td>
    	</tr>
    	<tr><td style="border-style: none;"><%=props.getProperty("organizationMembership")%></td></tr>
    	<tr>
    		<td style="border-style: none;">

    			<select multiple="multiple" name="organization" id="organization" size="5">
		            <option value=""></option>
	    	    	<%

		    		List<Organization> orgs=new ArrayList<Organization>();
	    	    	User orgAdminUser=myShepherd.getUser(request);
		    		if(request.isUserInRole("admin")){orgs=myShepherd.getAllOrganizations();}
		    		else if(request.getRemoteUser()!=null && (myShepherd.getUser(request)!=null)){
		    			orgs = myShepherd.getAllOrganizationsForUser(orgAdminUser);
		    		}
		    		int numOrgs=orgs.size();
					for(Organization org:orgs){
						String selected="";
						if((request.getParameter("isEdit")!=null)&&(myShepherd.getUserByUUID(request.getParameter("uuid").trim())!=null)){
							User thisUser=myShepherd.getUserByUUID(request.getParameter("uuid"));
							if(myShepherd.getAllOrganizationsForUser(thisUser).contains(org)){
								selected="selected=\"true\"";
							}
						}

						%>
						<option value="<%=org.getId() %>" <%=selected%>><%=org.getName()%></option>
						<%
					}
					%>
	    		</select>

    		</td>
    	</tr>



    <%
    }
    %>
    </table>

    </td>
    </form>
    </tr>
    </table>

    </p>

    <%

    if(		request.getParameter("isEdit")!=null
    		&& request.getParameter("uuid") != null
    		&& myShepherd.getUserByUUID(request.getParameter("uuid"))!=null
    	    && request.getUserPrincipal().getName()!=null
    	    && myShepherd.getUsername(request)!=null
    	    && myShepherd.getUser(myShepherd.getUsername(request))!=null
    	    //to delete a user either be admin or orgAdmin in at least one of the same orgs
    	    && (
    	              request.isUserInRole("admin")
    	              || (request.isUserInRole("orgAdmin") && myShepherd.getAllCommonOrganizationsForTwoUsers(myShepherd.getUserByUUID(request.getParameter("uuid")), myShepherd.getUser(myShepherd.getUsername(request))).size()>0
    	    ))
    ){
    %>
    <h2><%=props.getProperty("deleteUserQuestion")%></h2>
    <table width="100%">
      <tr>
    		<td height="30" class="para" colspan="2">
      		<form onsubmit="return confirm('<%=props.getProperty("sureDelete")%>');" name="deleteUser" class="editFormMeta" method="post" action="../UserDelete?context=context0" >
	          <input name="uuid" type="hidden" value="<%=uuid%>" />
	          <input align="absmiddle" name="approve" type="submit" class="btn btn-sm btn-block deleteUserBtn" id="deleteUserButton" style="background-color: red;" value="<%=props.getProperty("deleteUser")%>" />
        	</form>
	      </td>
	    </tr>
		</table>
  <%
  }
} catch(Exception e){
	e.printStackTrace();
	%>
	<p>Exception on page!</p>
	<p><%=e.getMessage() %></p>
	<%
}
finally{
  myShepherd.rollbackDBTransaction();
  myShepherd.closeDBTransaction();
}


if((CommonConfiguration.getProperty("showUserAgreement",context)!=null)&&(CommonConfiguration.getProperty("showUserAgreement",context).equals("true"))){
    %>
  <p>&nbsp;</p>
  <table class="tissueSample" style="border: 1px solid black;" width="100%" border="1">
    <tr>
      <td>
        <p><font size="+1"><%=props.getProperty("resetTitle")%></font></p>
        <p><%=props.getProperty("resetText")%></p>

        <form name="UserResetAcceptedUserAgreement" method="post" action="../UserResetAcceptedUserAgreement?context=context0">

          <input name="UserResetAcceptedUserAgreementButton" type="submit" id="UserResetAcceptedUserAgreementButton" value="Reset">
          </p></form>
      </td>
    </tr>
  </table>
	<%
}%>
<script>
	function sendButtonClicked() {
		//check for nulls
		let userNameVal = $('#username_input').val();
		if (!userNameVal) {
			$('#username_input').closest('.form-group').addClass('required-missing');
			window.setTimeout(function () { alert('Username cannot be empty.'); }, 100);
			return false;
		}

		let emailVal = $('#emailAddress_input').val();
		const re = /^(([^<>()\[\]\\.,;:\s@"]+(\.[^<>()\[\]\\.,;:\s@"]+)*)|(".+"))@((\[[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\])|(([a-zA-Z\-0-9]+\.)+[a-zA-Z]{2,}))$/;

		if (!emailVal) {
			$('#emailAddress_input').closest('.form-group').addClass('required-missing');
			window.setTimeout(function () { alert('Email address cannot be empty.'); }, 100);
			return false;
		}

		if (!re.test(emailVal.toLowerCase())) {
			$('#emailAddress_input').closest('.form-group').addClass('required-missing');
			window.setTimeout(function () { alert('Please provide a valid email address.'); }, 100);
			return false;
		}

		//check for existing username and email address (the latter nested in the ajax of the former)
		let localUsername = '<%=localUsername%>';
		let localEmail = '<%=localEmail%>';
		let checkBoth = false;
		if(emailVal != localEmail && userNameVal != localUsername){
			checkBoth = true;
			doAjaxForExistingUsername(userNameVal, emailVal, checkBoth);
		}
		if(userNameVal != localUsername && emailVal == localEmail){// only check for its existence if it doesn't match what the user's username/email address already is
			doAjaxForExistingUsername(userNameVal, emailVal, checkBoth);
		}
		if (emailVal != localEmail && userNameVal == localUsername) { // only check for its existence if it doesn't match what the user's username/email address already is
			doAjaxForExistingEmailAddress(emailVal);
		}
		if(emailVal == localEmail && userNameVal == localUsername){ //handle case where the user edits other things besides username and email
			submitForm();
		}
	}

	function doAjaxForExistingUsername(username, emailAddress, checkBoth) {
		let jsonRequest = {};
		jsonRequest['checkForExistingUsernameDesired'] = true;
		jsonRequest['username'] = username;
		$.ajax({
			url: wildbookGlobals.baseUrl + '../UserCheck',
			type: 'POST',
			data: JSON.stringify(jsonRequest),
			dataType: 'json',
			contentType: 'application/json',
			success: function (data) {
				console.log("data from doAjaxForExistingUsername:");
				console.log(data);
				if (data && data.existingUserResultsJson && data.existingUserResultsJson.doesUserExistAlready) {
					window.setTimeout(function () { alert('Username already claimed by another user. Please try another unique username.'); }, 100);
				}
				if (data && data.existingUserResultsJson && !data.existingUserResultsJson.doesUserExistAlready) {
					if(checkBoth){
						doAjaxForExistingEmailAddress(emailAddress);
					}else{
						submitForm();
					}
				}
				if (!data || !data.existingUserResultsJson) {
					window.setTimeout(function () { alert('Updating user information was not successful. Please refresh the page and try again.'); }, 100);
				}
			},
			error: function (x, y, z) {
				console.warn('%o %o %o', x, y, z);
			}
		});
	}

	function doAjaxForExistingEmailAddress(emailAddress) {
			let jsonRequest = {};
			jsonRequest['checkForExistingEmailDesired'] = true;
			jsonRequest['emailAddress'] = emailAddress;
			$.ajax({
				url: wildbookGlobals.baseUrl + '../UserCheck',
				type: 'POST',
				data: JSON.stringify(jsonRequest),
				dataType: 'json',
				contentType: 'application/json',
				success: function (data) {
					console.log("data from doAjaxForExistingEmailAddress:");
					console.log(data);
					if (data && data.existingEmailAddressResultsJson && data.existingEmailAddressResultsJson.doesEmailAddressExistAlready) {
						window.setTimeout(function () { alert('Email address already claimed by another user. Please try another unique email address.'); }, 100);
					}
					if (data && data.existingEmailAddressResultsJson && !data.existingEmailAddressResultsJson.doesEmailAddressExistAlready) {
						submitForm();
					}
					if (!data || !data.existingEmailAddressResultsJson) {
						window.setTimeout(function () { alert('Updating user information was not successful. Please refresh the page and try again.'); }, 100);
					}
				},
				error: function (x, y, z) {
					console.warn('%o %o %o', x, y, z);
				}
			});
		}

	function submitForm() {
		document.forms['newUser'].submit();
	}
</script>
</div>
<jsp:include page="../footer.jsp" flush="true"/>
