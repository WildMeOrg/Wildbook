<%@ page contentType="text/html; charset=utf-8" language="java"
         import="java.util.Calendar,java.util.ArrayList,org.ecocean.servlet.ServletUtilities,org.apache.commons.lang.WordUtils,org.ecocean.*, java.util.Properties" %>

<%

Calendar cal=Calendar.getInstance();
String dato=(new Integer(cal.get(Calendar.MONTH)+1)).toString()+"/1/"+(new Integer(cal.get(Calendar.YEAR))).toString();

//handle some cache-related security
response.setHeader("Cache-Control","no-cache"); //Forces caches to obtain a new copy of the page from the origin server
response.setHeader("Cache-Control","no-store"); //Directs caches not to store the page under any circumstance
response.setDateHeader("Expires", 0); //Causes the proxy cache to see the page as "stale"
response.setHeader("Pragma","no-cache"); //HTTP 1.0 backward compatibility 

//setup our Properties object to hold all properties
	//Properties props=new Properties();
	//String langCode="en";
	
	

%>	

<!--cssplay.co.uk menus-->
<style type="text/css">
/* ================================================================ 
This copyright notice must be untouched at all times.

The original version of this stylesheet and the associated (x)html
is available at http://www.cssplay.co.uk/menus/simple_vertical.html
Copyright (c) 2005-2007 Stu Nicholls. All rights reserved.
This stylesheet and the associated (x)html may be modified in any 
way to fit your requirements.
=================================================================== */

/* Add a margin - for this demo only - and a relative position with a high z-index to make it appear over any element below */
#menu_container {margin:0px 0 120px 0px; position:relative; width:810px; height:25px; z-index:100;}

/* Get rid of the margin, padding and bullets in the unordered lists */
#pmenu, #pmenu ul {padding:0; margin:0; list-style-type: none;z-index:99;}

/* Set up the link size, color and borders */
#pmenu a, #pmenu a:visited {display:block; font-size:11px; color:#FFFFFF height:25px; line-height:24px; text-decoration:none; text-indent:5px; border:2px solid #FFFFFF; border-width:1px 0 1px 1px;z-index:99;}

/* Set up the sub level borders */
#pmenu li ul li a, #pmenu li ul li a:visited {border-width:0 1px 1px 1px;z-index:99;}
#pmenu li a.enclose, #pmenu li a.enclose:visited {border-width:1px;z-index:99;}

/* Set up the list items */
#pmenu li {float:left; background:#909090;z-index:99;}

/* For Non-IE browsers and IE7 */
#pmenu li:hover {position:relative;z-index:99;}
/* Make the hovered list color persist */
#pmenu li:hover > a {background:#0082CC; color:#FFFFFF;z-index:99;}
/* Set up the sublevel lists with a position absolute for flyouts and overrun padding. The transparent gif is for IE to work */
#pmenu li ul {display:none;z-index:99;}
/* For Non-IE and IE7 make the sublevels visible on list hover. This is all it needs */
#pmenu li:hover > ul {display:block; position:absolute; top:-11px; left:80px; padding:10px 30px 30px 30px; /**background:transparent url(transparent.gif);*/ width:180px;z-index:99;}
/* Position the first sub level beneath the top level liinks */
#pmenu > li:hover > ul {left:-30px; top:16px;z-index:99;}

/* get rid of the table */
#pmenu table {position:absolute; border-collapse:collapse; top:0; left:0; z-index:100; font-size:1em;}

/* For IE5.5 and IE6 give the hovered links a position relative and a change of background and foreground color. This is needed to trigger IE to show the sub levels */
* html #pmenu li a:hover {position:relative; background:#909090; color:#FFFFFF;z-index:99;}

/* For accessibility of the top level menu when tabbing */
#pmenu li a:active, #pmenu li a:focus {background:#90900; color:#FFFFFF;z-index:99;}

/* Set up the pointers for the sub level indication */
#pmenu li.fly {background:#909090;z-index:99;}
#pmenu li.drop {background:#909090;z-index:99;}


/* This lot is for IE5.5 and IE6 ONLY and is necessary to make the sublevels appear */

/* change the drop down levels from display:none; to visibility:hidden; */
* html #pmenu li ul {visibility:hidden; display:block; position:absolute; top:-11px; left:80px; padding:10px 30px 30px 30px;z-index:99; /**background:transparent url(transparent.gif);*/}

/* keep the third level+ hidden when you hover on first level link */
#pmenu li a:hover ul ul{
visibility:hidden;
z-index:99;
}
/* keep the fourth level+ hidden when you hover on second level link */
#pmenu li a:hover ul a:hover ul ul{
visibility:hidden;
z-index:99;
}
/* keep the fifth level hidden when you hover on third level link */
#pmenu li a:hover ul a:hover ul a:hover ul ul{
visibility:hidden;
z-index:99;
}
/* keep the sixth level hidden when you hover on fourth level link */
#pmenu li a:hover ul a:hover ul a:hover ul a:hover ul ul {
visibility:hidden;
z-index:99;
}

/* make the second level visible when hover on first level link and position it */
#pmenu li a:hover ul {
visibility:visible; left:-30px; top:14px; lef\t:-31px; to\p:15px;
z-index:99;
}

/* make the third level visible when you hover over second level link and position it and all further levels */
#pmenu li a:hover ul a:hover ul{ 
visibility:visible; top:-11px; left:80px;
z-index:99;
}
/* make the fourth level visible when you hover over third level link */
#pmenu li a:hover ul a:hover ul a:hover ul { 
visibility:visible;
z-index:99;
}
/* make the fifth level visible when you hover over fourth level link */
#pmenu li a:hover ul a:hover ul a:hover ul a:hover ul { 
visibility:visible;
z-index:99;
}
/* make the sixth level visible when you hover over fifth level link */
#pmenu li a:hover ul a:hover ul a:hover ul a:hover ul a:hover ul { 
visibility:visible;
z-index:99;
}
/* If you can see the pattern in the above IE5.5 and IE6 style then you can add as many sub levels as you like */

/* A class used by the jQuery UI CSS framework for their dialogs. */
.ui-front {
    z-index:1000000 !important; /* The default is 100. !important overrides the default. */
}

</style>

<%

String context="context0";
context=ServletUtilities.getContext(request);

  //handle some cache-related security
  response.setHeader("Cache-Control", "no-cache"); //Forces caches to obtain a new copy of the page from the origin server
  response.setHeader("Cache-Control", "no-store"); //Directs caches not to store the page under any circumstance
  response.setDateHeader("Expires", 0); //Causes the proxy cache to see the page as "stale"
  response.setHeader("Pragma", "no-cache"); //HTTP 1.0 backward compatibility

//setup our Properties object to hold all properties
  Properties props = new Properties();
  //String langCode = "en";
  String langCode=ServletUtilities.getLanguageCode(request);
  

  //set up the file input stream
  //props.load(getClass().getResourceAsStream("/bundles/" + langCode + "/header.properties"));
  props = ShepherdProperties.getProperties("header.properties", langCode);


%>
		<script type="text/javascript" src="http://ajax.googleapis.com/ajax/libs/jquery/1.9.1/jquery.min.js"></script>
	<script type="text/javascript" src="http://<%=CommonConfiguration.getURLLocation(request) %>/javascript/jquery.cookie.js"></script>
	
	

	<div id="header"><img name="masthead" src="http://<%=CommonConfiguration.getURLLocation(request) %>/images/masthead.jpg" width="810px" height="300px" border="0" usemap="#m_masthead" alt="">

<map name="m_masthead">
	<area shape="rect" coords="649,156,785,295" href="http://www.wildme.org" alt="Sun" />
	<area shape="rect" coords="12,218,380,288" href="http://www.wildme.org/wildbook" alt="Sun" />
</map>

</div>
<div id="header_menu">



<ul id="pmenu">
<li style="background:#909090;"><a href="http://<%=CommonConfiguration.getURLLocation(request) %>" style="margin:0px 0 0px 0px; position:relative; width:50px; height:25px; z-index:100;"><strong><%=props.getProperty("home") %></strong></a></li>
<li class="drop"><a href="http://<%=CommonConfiguration.getURLLocation(request) %>/index.jsp" style="margin:0px 0 0px 0px; position:relative; width:70px; height:25px;z-index:100;"><strong><%=props.getProperty("learn") %></strong><!--[if IE 7]><!--></a><!--<![endif]-->
	<!--[if lte IE 6]><table><tr><td><![endif]-->
	<ul>
		<li><a href="http://<%=CommonConfiguration.getURLLocation(request) %>/photographing.jsp" class="enclose" style="margin:0px 0 0px 0px; position:relative; width:200px; height:25px;z-index:100;"><%=props.getProperty("howToPhotograph") %></a></li>
		<li><a href="http://<%=CommonConfiguration.getURLLocation(request) %>/publications.jsp" class="enclose" style="margin:0px 0 0px 0px; position:relative; width:200px; height:25px;z-index:100;"><%=props.getProperty("publications") %></a></li>
		<li><a href="http://www.wildme.org/wildbook" class="enclose" style="margin:0px 0 0px 0px; position:relative; width:200px; height:25px;z-index:100;"><%=props.getProperty("learnAboutShepherd") %></a></li>
	</ul>
	<!--[if lte IE 6]></td></tr></table></a><![endif]-->

</li>
<li class="drop"><a href="http://<%=CommonConfiguration.getURLLocation(request) %>/submit.jsp" style="margin:0px 0 0px 0px; position:relative; width:80px; height:25px;z-index:100;"><strong><%=props.getProperty("participate") %></strong><!--[if IE 7]><!--></a><!--<![endif]-->
	<!--[if lte IE 6]><table><tr><td><![endif]-->
	<ul>

	<li><a href="http://<%=CommonConfiguration.getURLLocation(request) %>/submit.jsp" class="enclose" style="margin:0px 0 0px 0px; position:relative; width:160px; height:25px;z-index:100;"><%=props.getProperty("report") %></a></li>
	<li><a href="http://<%=CommonConfiguration.getURLLocation(request) %>/wiki/doku.php?id=how_to_join_sharkgrid" class="enclose" style="margin:0px 0 0px 0px; position:relative; width:160px; height:25px;z-index:100;">sharkGrid</a></li>
	<li><a href="http://<%=CommonConfiguration.getURLLocation(request) %>/adoptashark.jsp" class="enclose" style="margin:0px 0 0px 0px; position:relative; width:160px; height:25px;z-index:100;"><%=props.getProperty("adoptAShark") %></a></li>
	
	</ul>
	<!--[if lte IE 6]></td></tr></table></a><![endif]-->

</li>
<li class="drop"><a href="http://<%=CommonConfiguration.getURLLocation(request) %>/individualSearchResults.jsp" style="margin:0px 0 0px 0px; position:relative; width:75px; height:25px; z-index:100;"><strong><%=props.getProperty("individuals") %></strong><!--[if IE 7]><!--></a><!--<![endif]-->
	<!--[if lte IE 6]><table><tr><td><![endif]-->
	<ul>
		<li><a href="http://<%=CommonConfiguration.getURLLocation(request) %>/individualSearchResults.jsp" class="enclose" style="margin:0px 0 0px 0px; position:relative; width:150px; height:25px;z-index:99;"><%=props.getProperty("viewAll") %> <%=props.getProperty("individuals") %></a></li>

	</ul>
	<!--[if lte IE 6]></td></tr></table></a><![endif]-->
</li>
<li class="drop"><a
      
      style="margin: 0px 0 0px 0px; position: relative; width: 90px; height: 25px; z-index: 100;"><strong><%=props.getProperty("encounters")%>
    </strong><!--[if IE 7]><!--></a><!--<![endif]-->
      <!--[if lte IE 6]>
      <table>
        <tr>
          <td><![endif]-->
      <ul>
      
      	<!-- list encounters by state -->
      						<%
      						boolean moreStates=true;
      						int cNum=0;
							while(moreStates){
	  								String currentLifeState = "encounterState"+cNum;
	  								if(CommonConfiguration.getProperty(currentLifeState,context)!=null){
	  									
	  									if((!CommonConfiguration.getProperty(currentLifeState,context).equals("unapproved"))||(request.getUserPrincipal()!=null)){
	  									%>
										<li>
        									<a href="http://<%=CommonConfiguration.getURLLocation(request) %>/encounters/searchResults.jsp?state=<%=CommonConfiguration.getProperty(currentLifeState,context) %>" class="enclose" style="margin: 0px 0 0px 0px; position: relative; width: 210px; height: 25px;z-index: 100;">
        										<%=props.getProperty("viewEncounters").trim().replaceAll(" ",(" "+WordUtils.capitalize(CommonConfiguration.getProperty(currentLifeState,context))+" "))%>
        									</a>
        								</li>
										<%
	  									}
										cNum++;
  									}
  									else{
     									moreStates=false;
  									}
  
							} //end while
      						%>
        

        <li><a
          href="http://<%=CommonConfiguration.getURLLocation(request) %>/encounters/thumbnailSearchResults.jsp?noQuery=true"
          class="enclose"
          style="margin: 0px 0 0px 0px; position: relative; width: 210px; height: 25px;"><%=props.getProperty("viewImages")%>
        </a></li>

        <li><a
          href="http://<%=CommonConfiguration.getURLLocation(request) %>/xcalendar/calendar.jsp"
          class="enclose"
          style="margin: 0px 0 0px 0px; position: relative; width: 210px; height: 25px;"><%=props.getProperty("encounterCalendar")%>
        </a></li>


<%
if(request.getUserPrincipal()!=null){
%>
      
        <li>
        	<a href="http://<%=CommonConfiguration.getURLLocation(request) %>/encounters/searchResults.jsp?username=<%=request.getRemoteUser()%>" class="enclose" style="margin: 0px 0 0px 0px; position: relative; width: 210px; height: 25px;">
        		<%=props.getProperty("viewMySubmissions")%>
        	</a>
        </li>
<%
}
%>



      </ul>
      <!--[if lte IE 6]></td></tr></table></a><![endif]--></li>
<li class="drop"><a href="http://<%=CommonConfiguration.getURLLocation(request) %>/welcome.jsp?reflect=http://<%=CommonConfiguration.getURLLocation(request) %>/encounters/encounterSearch.jsp" style="margin:0px 0 0px 0px; position:relative; width:70px; height:25px; z-index:100;"><strong><%=props.getProperty("search") %></strong>

<!--[if IE 7]><!--></a><!--<![endif]-->



	<!--[if lte IE 6]><table><tr><td><![endif]-->
	<ul>
	

		<li><a href="http://<%=CommonConfiguration.getURLLocation(request) %>/encounters/encounterSearch.jsp" class="enclose" style="margin:0px 0 0px 0px; position:relative; width:170px; height:25px;z-index:99;"><%=props.getProperty("encounterSearch") %></a></li>
		<li><a href="http://<%=CommonConfiguration.getURLLocation(request) %>/individualSearch.jsp" class="enclose" style="margin:0px 0 0px 0px; position:relative; width:170px; height:25px;z-index:99;"><%=props.getProperty("individualSearch") %></a></li>
		<li><a href="http://<%=CommonConfiguration.getURLLocation(request) %>/encounters/searchComparison.jsp" class="enclose" style="margin:0px 0 0px 0px; position:relative; width:170px; height:25px;z-index:99;"><%=props.getProperty("locationSearch") %></a></li>
		
		<li><a href="http://<%=CommonConfiguration.getURLLocation(request) %>/googleSearch.jsp" class="enclose" style="margin:0px 0 0px 0px; position:relative; width:170px; height:25px;"><%=props.getProperty("googleSearch") %></a></li>
	
	</ul>
	<!--[if lte IE 6]></td></tr></table></a><![endif]-->

</li>


		  
                    <li <%if(session.getAttribute("logged") != null) {%>class="drop"<%}%>><a href="http://<%=CommonConfiguration.getURLLocation(request) %>/welcome.jsp?reflect=http://<%=CommonConfiguration.getURLLocation(request) %>/appadmin/admin.jsp" style="margin:0px 0 0px 0px; position:relative; width:85px; height:25px; z-index:100;"><strong><%=props.getProperty("administer")%></strong>
				 							<!--[if IE 7]><!--></a><!--<![endif]-->
						<!--[if lte IE 6]><table><tr><td><![endif]-->
						<ul>
						
	<%
      if(request.getUserPrincipal()!=null){
      %>
        <li>
        	<a href="http://<%=CommonConfiguration.getURLLocation(request) %>/myAccount.jsp" class="enclose" style="margin: 0px 0 0px 0px; position: relative; width: 190px; height: 25px;">
        		<%=props.getProperty("myAccount")%>
        	</a>
        </li>
     <%
     
     }
     %>							<li>
								<a href="http://<%=CommonConfiguration.getURLLocation(request) %>/wiki/doku.php?id=ecocean_library_access_policy" target="_blank" class="enclose" style="margin:0px 0 0px 0px; position:relative; width:190px; height:25px;z-index:99;"><%=props.getProperty("accessPolicy")%></a>
							 </li>
							 <li>   
								<a href="http://<%=CommonConfiguration.getURLLocation(request) %>/wiki" target="_blank" class="enclose" style="margin:0px 0 0px 0px; position:relative; width:190px; height:25px;z-index:99;"><%=props.getProperty("userWiki")%></a>
							</li>

						
					
						<li><a href="http://<%=CommonConfiguration.getURLLocation(request) %>/software.jsp" class="enclose" style="margin:0px 0 0px 0px; position:relative; width:190px; height:25px;z-index:99;" ><%=props.getProperty("software")%></a></li>
						<li><a href="http://<%=CommonConfiguration.getURLLocation(request) %>/appadmin/scanTaskAdmin.jsp" class="enclose" style="margin:0px 0 0px 0px; position:relative; width:190px; height:25px;z-index:99;">sharkGrid</a></li>
						
							<li><a
							          href="http://<%=CommonConfiguration.getURLLocation(request) %>/appadmin/users.jsp?context=context0"
							          class="enclose"
							          style="margin: 0px 0 0px 0px; position: relative; width: 190px; height: 25px;"><%=props.getProperty("userManagement")%>
        </a></li>

					<%
					
					if((request.getParameter("isAdmin")!=null)&&(request.getParameter("isAdmin").equals("true"))) {%>
					
	        			<li><a href="http://<%=CommonConfiguration.getURLLocation(request) %>/appadmin/admin.jsp" class="enclose" style="margin:0px 0 0px 0px; position:relative; width:190px; height:25px;z-index:99;" ><%=props.getProperty("general")%></a></li>
     <li><a
	          href="http://<%=CommonConfiguration.getURLLocation(request) %>/appadmin/logs.jsp"
	          class="enclose"
	          style="margin: 0px 0 0px 0px; position: relative; width: 190px; height: 25px;">Logs
        </a></li>            			
<li><a href="http://<%=CommonConfiguration.getURLLocation(request) %>/tapirlink/admin/configurator.php?resource=RhincodonTypus" class="enclose" style="margin:0px 0 0px 0px; position:relative; width:190px; height:25px;z-index:99;">TapirLink</a></li>
	        			<li><a href="http://<%=CommonConfiguration.getURLLocation(request) %>/appadmin/kwAdmin.jsp" class="enclose" style="margin:0px 0 0px 0px; position:relative; width:190px; height:25px;z-index:99;"><%=props.getProperty("photoKeywords")%></a></li>
						<li><a href="http://<%=CommonConfiguration.getURLLocation(request) %>/appadmin/stats.jsp" class="enclose" style="margin:0px 0 0px 0px; position:relative; width:190px; height:25px;z-index:99;">Statistics</a></li>
						
						<li><a href="http://<%=CommonConfiguration.getURLLocation(request) %>/javadoc/index.html" class="enclose" style="margin:0px 0 0px 0px; position:relative; width:190px; height:25px;z-index:99;">Javadoc</a></li>
						
						
						<li class="drop"><a href="http://<%=CommonConfiguration.getURLLocation(request) %>/adoptions/adoption.jsp" style="margin:0px 0 0px 0px; position:relative; width:190px; height:25px; z-index:100;"><strong><%=props.getProperty("adoptions")%></strong> <img src="http://<%=CommonConfiguration.getURLLocation(request) %>/images/white_triangle.gif" border="0" align="absmiddle"><!--[if IE 7]><!--></a><!--<![endif]-->
		<!--[if lte IE 6]><table><tr><td><![endif]-->
		<ul>
			<li><a href="http://<%=CommonConfiguration.getURLLocation(request) %>/adoptions/adoption.jsp" class="enclose" style="margin:0px 0 0px 80px; position:relative; width:190px; height:25px;z-index:99;"><%=props.getProperty("createEditAdoption")%></a></li>
			<li style="margin:0px 0 0px 80px; position:relative; width:191px; height:26px;"><a href="http://<%=CommonConfiguration.getURLLocation(request) %>/adoptions/allAdoptions.jsp" class="enclose" style="margin:0px 0 0px 0px; position:relative; width:190px; height:25px;z-index:99;"><%=props.getProperty("viewAllAdoptions")%></a></li>
		
		</ul>
		<!--[if lte IE 6]></td></tr></table></a><![endif]-->

						
						
						<%}%>
	
										</ul>
						<!--[if lte IE 6]></td></tr></table></a><![endif]-->
				 </li>
                 
                 
                  <li><a href="http://<%=CommonConfiguration.getURLLocation(request) %>/whoAreWe.jsp" style="margin:0px 0 0px 0px; position:relative; width:85px; height:25px; z-index:100;"><strong><%=props.getProperty("peoplePartners")%></strong>
  
            <!--[if IE 7]><!--></a><!--<![endif]-->



	<!--[if lte IE 6]><table><tr><td><![endif]-->
	<ul>
	
		<li><a href="http://<%=CommonConfiguration.getURLLocation(request) %>/whoAreWe.jsp" class="enclose" style="margin:0px 0 0px 0px; position:relative; width:243px; height:25px z-index:99;"><%=props.getProperty("whoWeAre")%></a></li>

	</ul>
    
  <!--[if IE 7]><!--></a><!--<![endif]-->
	<!--[if lte IE 6]><table><tr><td><![endif]-->
				 
                
                
        
<li class="drop"><a href="https://www.facebook.com/wildmeorg" target="_blank" style="margin:0px 0 0px 0px; position:relative; width:60px; height:25px; z-index:100;"><strong><%=props.getProperty("news")%></strong><!--[if IE 7]><!--></a><!--<![endif]-->
	<!--[if lte IE 6]><table><tr><td><![endif]-->
	<ul>
<li><a href="https://www.facebook.com/wildmeorg" target="_blank" style="margin:0px 0 0px 0px; position:relative; width:150px; height:25px; z-index:100;"><%=props.getProperty("wildMeNews")%></a></li>
</ul>

  
	<!--[if lte IE 6]><table><tr><td><![endif]-->
    
           
<li><a href="http://<%=CommonConfiguration.getURLLocation(request) %>/contactus.jsp" style="margin:0px 0 0px 0px; position:relative; width:80px; height:25px; z-index:100;"><strong><%=props.getProperty("contactUs")%></strong></a></li>
<%if(request.getRemoteUser()==null) {%>
			<li><a href="http://<%=CommonConfiguration.getURLLocation(request) %>/welcome.jsp" style="margin:0px 0 0px 0px; position:relative; width:55px; height:25px; z-index:100;"><strong><%=props.getProperty("login")%></strong></a></li>
<%} else {%>

			<li><a href="http://<%=CommonConfiguration.getURLLocation(request) %>/logout.jsp" style="margin:0px 0 0px 0px; position:relative; width:55px; height:25px; z-index:100;"><strong><%=props.getProperty("logout")%></strong></a></li>
<%}%>

</ul>
</div>

<!-- define our JavaScript -->
	<script type="text/javascript" src="http://ajax.googleapis.com/ajax/libs/jquery/1.9.1/jquery.min.js"></script>
	<script type="text/javascript" src="http://<%=CommonConfiguration.getURLLocation(request) %>/javascript/jquery.cookie.js"></script>
	
<div id="header_menu" style="background-color: #D7E0ED;clear: left">
<table width="810px">
	<tr>
		<td class="caption" class="caption" style="text-align: left;" align="left">
		<table><tr><td><%=props.getProperty("findRecord") %></td><td><form name="form2" method="get" action="http://<%=CommonConfiguration.getURLLocation(request) %>/individuals.jsp">
            <input name="number" type="text" id="shark" size="25"/>
            <input type="hidden" name="langCode" value="<%=langCode%>"/>
            <input name="Go" type="submit" id="Go2" value="<%=props.getProperty("search")%>"/>
          </form></td></table>
		 
		          
		</td>
		
		<%
		ArrayList<String> supportedLanguages=CommonConfiguration.getSequentialPropertyValues("language", context);
		int numSupportedLanguages=supportedLanguages.size();
		
		if(numSupportedLanguages>1){
		%>
			<td class="caption" class="caption" style="text-align: left;" align="left">
				<table align="left">
				<tr>
					<td><%=props.getProperty("selectLanguage") %></td>
					<td>
					
					<%
					for(int h=0;h<numSupportedLanguages;h++){
						String selected="";
						if(ServletUtilities.getLanguageCode(request).equals(supportedLanguages.get(h))){selected="selected=\"selected\"";}
						String myLang=supportedLanguages.get(h);
					%>
						<img style="cursor: pointer" id="flag_<%=myLang %>" src="http://<%=CommonConfiguration.getURLLocation(request) %>/images/flag_<%=myLang %>.gif" />
						<script type="text/javascript">
	
							$( "#flag_<%=myLang%>" ).click(function() {
		
								//alert( "Handler for .change() called with new value: "+$( "#langCode option:selected" ).text() +" with value "+ $( "#langCode option:selected").val());
								$.cookie("wildbookLangCode", "<%=myLang%>", {
			   						path    : '/',          //The value of the path attribute of the cookie 
			                           //(default: path of page that created the cookie).
		   
			   						secure  : false          //If set to true the secure attribute of the cookie
			                           //will be set and the cookie transmission will
			                           //require a secure protocol (defaults to false).
								});
			
								//alert("I have set the wildbookContext cookie to value: "+$.cookie("wildbookContext"));
								location.reload(true);
			
							});
	
						</script>
					<%
					}
					%>
				
			
					</td>
				</tr>
			</table>
			
			<td>
		
		<%
		}
		
		
		
		ArrayList<String> contextNames=ContextConfiguration.getContextNames();
		int numContexts=contextNames.size();
		if(numContexts>1){
		%>
		
		<td  class="caption" style="text-align: right;" align="right">
			<table align="right">
				<tr>
					<td><%=props.getProperty("switchContext") %></td>
					<td>
						<form>
							<select id="context" name="context">
					<%
					for(int h=0;h<numContexts;h++){
						String selected="";
						if(ServletUtilities.getContext(request).equals(("context"+h))){selected="selected=\"selected\"";}
					%>
					
						<option value="context<%=h%>" <%=selected %>><%=contextNames.get(h) %></option>
					<%
					}
					%>
							</select>
						</form>
			
					</td>
				</tr>
			</table>
		 
		</td>
			<%
		}
		%>
		
	<script type="text/javascript">
		
	$( "#context" ).change(function() {
			
  			//alert( "Handler for .change() called with new value: "+$( "#context option:selected" ).text() +" with value "+ $( "#context option:selected").val());
  			$.cookie("wildbookContext", $( "#context option:selected").val(), {
  			   path    : '/',          //The value of the path attribute of the cookie 
  			                           //(default: path of page that created the cookie).
			   
  			   secure  : false          //If set to true the secure attribute of the cookie
  			                           //will be set and the cookie transmission will
  			                           //require a secure protocol (defaults to false).
  			});
  			
  			//alert("I have set the wildbookContext cookie to value: "+$.cookie("wildbookContext"));
  			location.reload(true);
  			
		});
	
	</script>


	
	</tr>
</table>

</div>
