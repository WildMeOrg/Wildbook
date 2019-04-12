<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.servlet.ServletUtilities,java.util.List,org.ecocean.*, java.util.Properties, java.util.Collection, java.util.Vector,java.util.ArrayList, org.datanucleus.api.rest.orgjson.JSONArray, org.json.JSONObject, org.datanucleus.api.rest.RESTUtils, org.datanucleus.api.jdo.JDOPersistenceManager" %>

  <%

  	String context="context0";
  	context=ServletUtilities.getContext(request);

  
    //let's load out properties
    Properties props = new Properties();
    String langCode=ServletUtilities.getLanguageCode(request);
	props = ShepherdProperties.getProperties("users.properties", langCode,context);

    Shepherd myShepherd = new Shepherd(context);
    myShepherd.setAction("users.jsp");

  	List<String> roles=CommonConfiguration.getIndexedPropertyValues("role",context);
	List<String> roleDefinitions=CommonConfiguration.getIndexedPropertyValues("roleDefinition",context);
	int numRoles=roles.size();
  	int numRoleDefinitions=roleDefinitions.size();
    int numResults = 0;

    List<User> users = new ArrayList<User>();
    myShepherd.beginDBTransaction();

	//String currentEmail = request.getParameter("email")	;
	String currentUUID = request.getParameter("uuid")	;
    
    try{
	    String order ="username ASC NULLS LAST";	    
	
	    users = myShepherd.getAllUsers();
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
	    int usersSize=users.size();
	
	    int count = 0;

	
		JDOPersistenceManager jdopm = (JDOPersistenceManager)myShepherd.getPM();
		JSONArray jsonobj = RESTUtils.getJSONArrayFromCollection((Collection)users, jdopm.getExecutionContext());
		String indsJson = jsonobj.toString();
		
	    JSONArray rolesobj=	RESTUtils.getJSONArrayFromCollection((Collection)allRoles, jdopm.getExecutionContext());
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
			label: '<%=props.getProperty("username")%>',
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
			key: 'affiliation',
			label: '<%=props.getProperty("affiliation")%>',
			value: _colAffiliation
		},
		{
			key: 'lastLogin',
			label: '<%=props.getProperty("lastLogin")%>',
			value: _colLastLogin
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
	
	var sortCol = -1;
	var sortReverse = false;
	
	
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
	
		$('#results-table').on('mousewheel', function(ev) {  //firefox? DOMMouseScroll
			if (!sTable.opts.sliderElement) return;
			ev.preventDefault();
			var delta = Math.max(-1, Math.min(1, (event.wheelDelta || -event.detail)));
			if (delta != 0) nudge(-delta);
		});
	
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
	
	
	function xxxshow() {
		$('#results-table td').html('');
		for (var i = 0 ; i < results.length ; i++) {
			//$('#results-table tbody tr')[i].title = searchResults[results[i]].individualID;
			$('#results-table tbody tr')[i].setAttribute('data-id', searchResults[results[i]].uuid);
			for (var c = 0 ; c < colDefn.length ; c++) {
				$('#results-table tbody tr')[i].children[c].innerHTML = sTable.values[results[i]][c];
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
	
		sTable.sliderSet(100 - (start / (searchResults.length - howMany)) * 100);
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
	
	function _colAffiliation(o) {
		if (o.affiliation == undefined) return '';
		return o.affiliation;
	}


	
	
	function _colRowNum(o) {
		return o._rowNum;
	}
	

	
	
	function _colLastLogin(o) {
		if (!o.lastLogin) return '';
		if (o.lastLogin == -1) return '';
		var s = new Date(o.lastLogin).toISOString();
		return s.slice(0, 10);
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
	</table>

	
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

	<h4 class="intro"><a name="editUser" /></a>Create/Edit a User</h4>
	<p>
	<%
	String isEditAddition="";
	if(request.getParameter("isEdit")!=null){isEditAddition="&isEdit=true";}
	%>
    	
    		    <table class="tissueSample">
    		    

    		    
    		    <%
    		    //let's set up any pre-defined values if appropriate
    		    String localUsername="";
    		    String localAffiliation="";
    		    String localEmail="";
    		    String localFullName="";
    		    String profilePhotoURL="../images/empty_profile.jpg";
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
    		    else if(request.getParameter("uuid")==null){
                	
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
        												<img src="../images/upload_small.gif" align="absmiddle" />&nbsp;Upload photo:<br /> 
		        		    						 <input name="username" type="hidden" value="<%=localUsername%>" id="profileUploadUsernameField" />
        												<input name="file2add" type="file" style="width: 200px"/>
        												<input name="addtlFile" type="submit" id="addtlFile" value="Upload" />
        											</form>
		        		    					</td>
		        		    				</tr>
		        		    				<%
		        		    				if(hasProfilePhoto){
		        		    				%>
		        		    					<tr><td style="border: solid 0">Delete profile photo:&nbsp;<a href="../UserRemoveProfileImage?username=<%=localUsername%>"><img src="../images/cancel.gif" width="16px" height="16px" align="absmiddle" /></a></td></tr>
		        		    			
		        		    				<%
		        		    				}
		        		    			}
		        		    			%>
		        		    			</table>
		        		    		
		        		    	</td>
		        	<form action="../UserCreate?context=context0<%=isEditAddition %>" method="post" id="newUser" accept-charset="UTF-8">	    
    		    	<td><table width="100%" class="tissueSample">
      				<tr><td colspan="3"><em>This function allows you to create a new user account and assign appropriate roles. Available roles are independently configured, listed in commonConfiguration.properties, and matched to the URL-based functions of the Shepherd Project in the Apache Shiro filter in web.xml.</em></td></tr>
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
                   		
                        <td>Username: <input name="username" type="text" size="15" maxlength="90" value="<%=localUsername %>" ></input></td>
                        
                        <td>Password: <input name="password" type="password" size="15" maxlength="90"></input></td>
                        <td>Confirm Password: <input name="password2" type="password" size="15" maxlength="90"></input></td>
                        
                        

            		</tr>
                    <tr><td colspan="3">Full name: <input name="fullName" type="text" size="15" maxlength="90" value="<%=localFullName %>"></input></td></tr>
                    <tr><td colspan="2">Email address: <input name="emailAddress" type="text" size="15" maxlength="90" value="<%=localEmail %>"></input></td><td colspan="1">Receive automated emails? <input type="checkbox" name="receiveEmails" value="receiveEmails" <%=receiveEmails %>/></td></tr>
                    <tr><td colspan="3">Affiliation: <input name="affiliation" type="text" size="15" maxlength="90" value="<%=localAffiliation %>"></input></td></tr>
                     <tr><td colspan="3">Research Project: <input name="userProject" type="text" size="15" maxlength="90" value="<%=userProject %>"></input></td></tr>
                          
                    <tr><td colspan="3">Project URL: <input name="userURL" type="text" size="15" maxlength="90" value="<%=userURL %>"></input></td></tr>
		     <tr><td colspan="3" valign="top">User Statement (255 char. max): <textarea name="userStatement" size="100" maxlength="255"><%=userStatement%></textarea></td></tr>                  
                    
                    <tr>
						<td colspan="3">
							<input class="btn btn-sm btn-block" name="Create" type="submit" id="Create" value="Create" />
						
						</td>
					</tr>
            </table>
            </td>
            <td>
            <table>
           
            <%
            List<String> contexts=ContextConfiguration.getContextNames();
            int numContexts=contexts.size();
            for(int d=0;d<numContexts;d++){
            	%>
            	 <tr>
            <td>
            
            
            Roles for <%=ContextConfiguration.getNameForContext(("context"+d)) %>(multi-select): 
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
									
					    		    	
								%>
             					 <option value="<%=roles.get(q)%>" <%=selected%>><%=roles.get(q)%></option>
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
            if((request.getParameter("isEdit")!=null)&&(request.isUserInRole("admin"))){
            %>
            <h2>Do you want to delete this user?</h2>
            <table width="100%">
                <tr>
			      <td height="30" class="para" colspan="2">
			        <form onsubmit="return confirm('Are you sure you want to delete this user?');" name="deleteUser" class="editFormMeta" method="post" action="../UserDelete?context=context0" >
			              <input name="uuid" type="hidden" value="<%=uuid%>" />
			              <input align="absmiddle" name="approve" type="submit" class="btn btn-sm btn-block deleteUserBtn" id="deleteUserButton" style="background-color: red;" value="Delete User" />
			        </form>
			      	
			      </td>
			    </tr>
    		</table>
            <%
            }
            %>

<%
    }
    catch(Exception e){
    %>
    
    <p>Exception on page!</p>
    <p><%=e.getMessage() %></p>
    
    <%	
    }
    finally{
      myShepherd.rollbackDBTransaction();
      myShepherd.closeDBTransaction();
    }

%>






    <%
    if((CommonConfiguration.getProperty("showUserAgreement",context)!=null)&&(CommonConfiguration.getProperty("showUserAgreement",context).equals("true"))){
    %>
            <p>&nbsp;</p>
      <table class="tissueSample" style="border: 1px solid black;" width="100%" border="1">
        <tr>
          <td>
            <p><font size="+1">Reset User Agreement Acceptance for All Users</font></p>
            <p>This command resets all User accounts such that each user must reaccept the User Agreement upon the next login.</p>

            <form name="UserResetAcceptedUserAgreement" method="post" action="../UserResetAcceptedUserAgreement?context=context0">

              <input name="UserResetAcceptedUserAgreementButton" type="submit" id="UserResetAcceptedUserAgreementButton" value="Reset">
              </p></form>
          </td>
        </tr>
      </table>
	<%
	}
	%>

</div>
<jsp:include page="../footer.jsp" flush="true"/>
