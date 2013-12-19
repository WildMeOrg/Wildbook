<%@ page contentType="text/html; charset=utf-8" language="java" import="org.apache.commons.lang.WordUtils,java.util.Properties, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, java.util.Calendar, org.ecocean.*" %>

<%

Calendar cal=Calendar.getInstance();
String dato=(new Integer(cal.get(Calendar.MONTH)+1)).toString()+"/1/"+(new Integer(cal.get(Calendar.YEAR))).toString();

//handle some cache-related security
response.setHeader("Cache-Control","no-cache"); //Forces caches to obtain a new copy of the page from the origin server
response.setHeader("Cache-Control","no-store"); //Directs caches not to store the page under any circumstance
response.setDateHeader("Expires", 0); //Causes the proxy cache to see the page as "stale"
response.setHeader("Pragma","no-cache"); //HTTP 1.0 backward compatibility 

//setup our Properties object to hold all properties
	Properties props=new Properties();
	String langCode="en";
	
	//check what language is requested
	//if(request.getParameter("langCode")!=null){
	if(session.getAttribute("langCode")!=null){
		//if(request.getParameter("langCode").equals("fr")) {langCode="fr";}
		//if(request.getParameter("langCode").equals("de")) {langCode="de";}
		//if(request.getParameter("langCode").equals("es")) {langCode="es";}
		langCode=(String)session.getAttribute("langCode");
	}
	if(request.getParameter("langCode")!=null){
	//if(session.getAttribute("langCode")!=null){
		if(request.getParameter("langCode").equals("fr")) {langCode="fr";}
		if(request.getParameter("langCode").equals("de")) {langCode="de";}
		if(request.getParameter("langCode").equals("es")) {langCode="es";}
		if(request.getParameter("langCode").equals("en")) {langCode="en";}
		//langCode=(String)session.getAttribute("langCode");
	}
	
	//set up the file input stream
	props.load(getClass().getResourceAsStream("/bundles/"+langCode+"/header.properties"));
	
	//test comment
	
	//load our variables for the overview page
	String title=props.getProperty("overview_title");
	String overview_maintext=props.getProperty("overview_maintext");
	String overview_reportit=props.getProperty("overview_reportit");
	String overview_language=props.getProperty("overview_language");
	String what_do=props.getProperty("what_do");
	String read_overview=props.getProperty("read_overview");
	String see_all_encounters=props.getProperty("see_all_encounters");
	String see_all_sharks=props.getProperty("see_all_sharks");
	String report_encounter=props.getProperty("report_encounter");
	String log_in=props.getProperty("log_in");
	String contact_us=props.getProperty("contact_us");
	String search=props.getProperty("search");
	String encounter=props.getProperty("encounter");
	String shark=props.getProperty("shark");
	String join_the_dots=props.getProperty("join_the_dots");
	String menu=props.getProperty("menu");
	String last_sightings=props.getProperty("last_sightings");
	String more=props.getProperty("more");
	String ws_info=props.getProperty("ws_info");
	String about=props.getProperty("about");
	String forum=props.getProperty("forum");
	String blog=props.getProperty("blog");
	String area=props.getProperty("area");
	String match=props.getProperty("match");
	
	//link path to submit page with appropriate language
	String submitPath="submit.jsp?langCode="+langCode;

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

</style>





	<div id="header"><img name="masthead" src="http://<%=CommonConfiguration.getURLLocation(request) %>/images/masthead.jpg" width="810px" height="300px" border="0" usemap="#m_masthead" alt="">

<map name="m_masthead">
	<area shape="rect" coords="649,156,785,295" href="http://www.wildme.org" alt="Sun" />
	<area shape="rect" coords="12,218,380,288" href="http://www.wildme.org/wildbook" alt="Sun" />
	<area shape="rect" coords="700,100,800,0" href="http://www.indiegogo.com/projects/wild-me-bringing-wildlife-into-social-media" alt="Sun" />
</map>

</div>
<div id="header_menu">



<ul id="pmenu">
<li style="background:#909090;"><a href="http://<%=CommonConfiguration.getURLLocation(request) %>" style="margin:0px 0 0px 0px; position:relative; width:50px; height:25px; z-index:100;"><strong>Home</strong></a></li>
<li class="drop"><a href="http://<%=CommonConfiguration.getURLLocation(request) %>/index.jsp?langCode=<%=langCode%>" style="margin:0px 0 0px 0px; position:relative; width:45px; height:25px;z-index:100;"><strong>Learn</strong><!--[if IE 7]><!--></a><!--<![endif]-->
	<!--[if lte IE 6]><table><tr><td><![endif]-->
	<ul>
		<li><a href="http://<%=CommonConfiguration.getURLLocation(request) %>/photographing.jsp?langCode=<%=langCode%>" class="enclose" style="margin:0px 0 0px 0px; position:relative; width:200px; height:25px;z-index:100;">How to Photograph</a></li>
		<li><a href="http://<%=CommonConfiguration.getURLLocation(request) %>/publications.jsp?langCode=<%=langCode%>" class="enclose" style="margin:0px 0 0px 0px; position:relative; width:200px; height:25px;z-index:100;">Publications</a></li>
		<li><a href="http://www.wildme.org/wildbook" class="enclose" style="margin:0px 0 0px 0px; position:relative; width:200px; height:25px;z-index:100;">Learn about Wildbook</a></li>
	</ul>
	<!--[if lte IE 6]></td></tr></table></a><![endif]-->

</li>
<li class="drop"><a href="http://<%=CommonConfiguration.getURLLocation(request) %>/submit.jsp?langCode=<%=langCode%>" style="margin:0px 0 0px 0px; position:relative; width:80px; height:25px;z-index:100;"><strong>Participate</strong><!--[if IE 7]><!--></a><!--<![endif]-->
	<!--[if lte IE 6]><table><tr><td><![endif]-->
	<ul>

	<li><a href="http://<%=CommonConfiguration.getURLLocation(request) %>/submit.jsp?langCode=<%=langCode%>" class="enclose" style="margin:0px 0 0px 0px; position:relative; width:160px; height:25px;z-index:100;">Report an Encounter</a></li>
	<li><a href="http://<%=CommonConfiguration.getURLLocation(request) %>/wiki/doku.php?id=how_to_join_sharkgrid" class="enclose" style="margin:0px 0 0px 0px; position:relative; width:160px; height:25px;z-index:100;">Join sharkGrid</a></li>
	<li><a href="http://<%=CommonConfiguration.getURLLocation(request) %>/adoptashark.jsp" class="enclose" style="margin:0px 0 0px 0px; position:relative; width:160px; height:25px;z-index:100;">Adopt a Shark</a></li>
	
	</ul>
	<!--[if lte IE 6]></td></tr></table></a><![endif]-->

</li>
<li class="drop"><a href="http://<%=CommonConfiguration.getURLLocation(request) %>/individualSearchResults.jsp" style="margin:0px 0 0px 0px; position:relative; width:90px; height:25px; z-index:100;"><strong>View Sharks</strong><!--[if IE 7]><!--></a><!--<![endif]-->
	<!--[if lte IE 6]><table><tr><td><![endif]-->
	<ul>
		<li><a href="http://<%=CommonConfiguration.getURLLocation(request) %>/individualSearchResults.jsp" class="enclose" style="margin:0px 0 0px 0px; position:relative; width:130px; height:25px;z-index:99;">View All Sharks</a></li>

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
	  								if(CommonConfiguration.getProperty(currentLifeState)!=null){
	  									
	  									if((!CommonConfiguration.getProperty(currentLifeState).equals("unapproved"))||(request.getUserPrincipal()!=null)){
	  									%>
										<li>
        									<a href="http://<%=CommonConfiguration.getURLLocation(request) %>/encounters/searchResults.jsp?state=<%=CommonConfiguration.getProperty(currentLifeState) %>" class="enclose" style="margin: 0px 0 0px 0px; position: relative; width: 210px; height: 25px;z-index: 100;">
        										<%=props.getProperty("viewEncounters").trim().replaceAll(" ",(" "+WordUtils.capitalize(CommonConfiguration.getProperty(currentLifeState))+" "))%>
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
<li class="drop"><a href="http://<%=CommonConfiguration.getURLLocation(request) %>/welcome.jsp?reflect=http://<%=CommonConfiguration.getURLLocation(request) %>/encounters/encounterSearch.jsp" style="margin:0px 0 0px 0px; position:relative; width:55px; height:25px; z-index:100;"><strong>Search</strong>

<!--[if IE 7]><!--></a><!--<![endif]-->



	<!--[if lte IE 6]><table><tr><td><![endif]-->
	<ul>
	

		<li><a href="http://<%=CommonConfiguration.getURLLocation(request) %>/encounters/encounterSearch.jsp?langCode=<%=langCode%>" class="enclose" style="margin:0px 0 0px 0px; position:relative; width:170px; height:25px;z-index:99;">Encounter Search</a></li>
		<li><a href="http://<%=CommonConfiguration.getURLLocation(request) %>/individualSearch.jsp?langCode=<%=langCode%>" class="enclose" style="margin:0px 0 0px 0px; position:relative; width:170px; height:25px;z-index:99;">Shark Search</a></li>
		<li><a href="http://<%=CommonConfiguration.getURLLocation(request) %>/encounters/searchComparison.jsp?langCode=<%=langCode%>" class="enclose" style="margin:0px 0 0px 0px; position:relative; width:170px; height:25px;z-index:99;">Search Comparison</a></li>
		
		<li><a href="http://<%=CommonConfiguration.getURLLocation(request) %>/googleSearch.jsp" class="enclose" style="margin:0px 0 0px 0px; position:relative; width:170px; height:25px;">Google Search</a></li>
	
	</ul>
	<!--[if lte IE 6]></td></tr></table></a><![endif]-->

</li>


		  
                    <li <%if(session.getAttribute("logged") != null) {%>class="drop"<%}%>><a href="http://<%=CommonConfiguration.getURLLocation(request) %>/welcome.jsp?reflect=http://<%=CommonConfiguration.getURLLocation(request) %>/appadmin/admin.jsp" style="margin:0px 0 0px 0px; position:relative; width:80px; height:25px; z-index:100;"><strong>Administer</strong>
				 							<!--[if IE 7]><!--></a><!--<![endif]-->
						<!--[if lte IE 6]><table><tr><td><![endif]-->
						<ul>
							<li>
								<a href="http://<%=CommonConfiguration.getURLLocation(request) %>/wiki/doku.php?id=ecocean_library_access_policy" target="_blank" class="enclose" style="margin:0px 0 0px 0px; position:relative; width:190px; height:25px;z-index:99;">Access Policy</a>
							 </li>
							 <li>   
								<a href="http://<%=CommonConfiguration.getURLLocation(request) %>/wiki" target="_blank" class="enclose" style="margin:0px 0 0px 0px; position:relative; width:190px; height:25px;z-index:99;">User Wiki</a>
							</li>

						
					
						<li><a href="http://<%=CommonConfiguration.getURLLocation(request) %>/software.jsp" class="enclose" style="margin:0px 0 0px 0px; position:relative; width:190px; height:25px;z-index:99;" >Client Software</a></li>
						<li><a href="http://<%=CommonConfiguration.getURLLocation(request) %>/appadmin/scanTaskAdmin.jsp?langCode=<%=langCode%>" class="enclose" style="margin:0px 0 0px 0px; position:relative; width:190px; height:25px;z-index:99;">sharkGrid</a></li>
						
							<li><a
							          href="http://<%=CommonConfiguration.getURLLocation(request) %>/appadmin/users.jsp"
							          class="enclose"
							          style="margin: 0px 0 0px 0px; position: relative; width: 190px; height: 25px;">User Management
        </a></li>
						
					<%
					
					if((request.getParameter("isAdmin")!=null)&&(request.getParameter("isAdmin").equals("true"))) {%>
					
	        			<li><a href="http://<%=CommonConfiguration.getURLLocation(request) %>/appadmin/admin.jsp?langCode=<%=langCode%>" class="enclose" style="margin:0px 0 0px 0px; position:relative; width:190px; height:25px;z-index:99;" >General</a></li>
     <li><a
	          href="http://<%=CommonConfiguration.getURLLocation(request) %>/appadmin/logs.jsp"
	          class="enclose"
	          style="margin: 0px 0 0px 0px; position: relative; width: 190px; height: 25px;">Logs
        </a></li>            			
<li><a href="http://<%=CommonConfiguration.getURLLocation(request) %>/tapirlink/admin/configurator.php?resource=RhincodonTypus" class="enclose" style="margin:0px 0 0px 0px; position:relative; width:190px; height:25px;z-index:99;">TapirLink</a></li>
	        			<li><a href="http://<%=CommonConfiguration.getURLLocation(request) %>/appadmin/kwAdmin.jsp?langCode=<%=langCode%>" class="enclose" style="margin:0px 0 0px 0px; position:relative; width:190px; height:25px;z-index:99;">Photo Keywords</a></li>
						<li><a href="http://<%=CommonConfiguration.getURLLocation(request) %>/appadmin/stats.jsp?langCode=<%=langCode%>" class="enclose" style="margin:0px 0 0px 0px; position:relative; width:190px; height:25px;z-index:99;">Statistics</a></li>
						
						<li><a href="http://<%=CommonConfiguration.getURLLocation(request) %>/javadoc/index.html" class="enclose" style="margin:0px 0 0px 0px; position:relative; width:190px; height:25px;z-index:99;">Javadoc</a></li>
						
						
						<li class="drop"><a href="http://<%=CommonConfiguration.getURLLocation(request) %>/adoptions/adoption.jsp" style="margin:0px 0 0px 0px; position:relative; width:190px; height:25px; z-index:100;"><strong>Adoptions</strong> <img src="http://<%=CommonConfiguration.getURLLocation(request) %>/images/white_triangle.gif" border="0" align="absmiddle"><!--[if IE 7]><!--></a><!--<![endif]-->
		<!--[if lte IE 6]><table><tr><td><![endif]-->
		<ul>
			<li><a href="http://<%=CommonConfiguration.getURLLocation(request) %>/adoptions/adoption.jsp" class="enclose" style="margin:0px 0 0px 80px; position:relative; width:190px; height:25px;z-index:99;">Create/edit adoption</a></li>
			<li style="margin:0px 0 0px 80px; position:relative; width:191px; height:26px;"><a href="http://<%=CommonConfiguration.getURLLocation(request) %>/adoptions/allAdoptions.jsp" class="enclose" style="margin:0px 0 0px 0px; position:relative; width:190px; height:25px;z-index:99;">View all adoptions</a></li>
		
		</ul>
		<!--[if lte IE 6]></td></tr></table></a><![endif]-->

						
						
						<%}%>
	
										</ul>
						<!--[if lte IE 6]></td></tr></table></a><![endif]-->
				 </li>
                 
                 
                  <li><a href="http://<%=CommonConfiguration.getURLLocation(request) %>/provateSector.jsp" style="margin:0px 0 0px 0px; position:relative; width:130px; height:25px; z-index:100;"><strong>People/Partners </strong>
  
            <!--[if IE 7]><!--></a><!--<![endif]-->



	<!--[if lte IE 6]><table><tr><td><![endif]-->
	<ul>
	
		<li><a href="http://<%=CommonConfiguration.getURLLocation(request) %>/whoAreWe.jsp?langCode=<%=langCode%>" class="enclose" style="margin:0px 0 0px 0px; position:relative; width:243px; height:25px z-index:99;">Who We Are</a></li>
		<li><a href="http://<%=CommonConfiguration.getURLLocation(request) %>/partners.jsp?langCode=<%=langCode%>" class="enclose" style="margin:0px 0 0px 0px; position:relative; width:243px; height:25px; z-index:99;">Partner Organizations & Individuals</a></li>
		
	</ul>
    
  <!--[if IE 7]><!--></a><!--<![endif]-->
	<!--[if lte IE 6]><table><tr><td><![endif]-->
				 
                
                
        
<li class="drop"><a href="http://whalesharknews.wordpress.com/" target="_blank" style="margin:0px 0 0px 0px; position:relative; width:45px; height:25px; z-index:100;"><strong>News</strong><!--[if IE 7]><!--></a><!--<![endif]-->
	<!--[if lte IE 6]><table><tr><td><![endif]-->
	<ul>
<li><a href="http://whalesharknews.wordpress.com/" target="_blank" style="margin:0px 0 0px 0px; position:relative; width:130px; height:25px; z-index:100;">View Shark News</a></li>
</ul>

  
	<!--[if lte IE 6]><table><tr><td><![endif]-->
    
           
<li><a href="http://<%=CommonConfiguration.getURLLocation(request) %>/contactus.jsp" style="margin:0px 0 0px 0px; position:relative; width:80px; height:25px; z-index:100;"><strong>Contact Us</strong></a></li>
<%if(request.getRemoteUser()==null) {%>
			<li><a href="http://<%=CommonConfiguration.getURLLocation(request) %>/welcome.jsp?langCode=<%=langCode%>" style="margin:0px 0 0px 0px; position:relative; width:50px; height:25px; z-index:100;"><strong>Login</strong></a></li>
<%} else {%>

			<li><a href="http://<%=CommonConfiguration.getURLLocation(request) %>/logout.jsp?langCode=<%=langCode%>" style="margin:0px 0 0px 0px; position:relative; width:50px; height:25px; z-index:100;"><strong>Logout</strong></a></li>
<%}%>

</ul>
</div>
