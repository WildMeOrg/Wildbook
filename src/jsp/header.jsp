<%@ page contentType="text/html; charset=utf-8" language="java"
	import="java.util.Properties, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, java.util.Calendar, org.ecocean.*"%>

<%


//handle some cache-related security
response.setHeader("Cache-Control","no-cache"); //Forces caches to obtain a new copy of the page from the origin server
response.setHeader("Cache-Control","no-store"); //Directs caches not to store the page under any circumstance
response.setDateHeader("Expires", 0); //Causes the proxy cache to see the page as "stale"
response.setHeader("Pragma","no-cache"); //HTTP 1.0 backward compatibility 

//setup our Properties object to hold all properties
	Properties props=new Properties();
	String langCode="en";
	
	if(session.getAttribute("langCode")!=null){
		langCode=(String)session.getAttribute("langCode");
	}
	
	//set up the file input stream
	props.load(getClass().getResourceAsStream("/bundles/"+langCode+"/header.properties"));
	

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
#pmenu a, #pmenu a:visited {display:block; font-size:11px; color:#fff; height:25px; line-height:24px; text-decoration:none; text-indent:5px; border:1px solid #000; border-width:1px 0 1px 1px;z-index:99;}

/* Set up the sub level borders */
#pmenu li ul li a, #pmenu li ul li a:visited {border-width:0 1px 1px 1px;z-index:99;}
#pmenu li a.enclose, #pmenu li a.enclose:visited {border-width:1px;z-index:99;}

/* Set up the list items */
#pmenu li {float:left; background:#7484ad;z-index:99;}

/* For Non-IE browsers and IE7 */
#pmenu li:hover {position:relative;z-index:99;}
/* Make the hovered list color persist */
#pmenu li:hover > a {background:#dfd7ca; color:#c00;z-index:99;}
/* Set up the sublevel lists with a position absolute for flyouts and overrun padding. The transparent gif is for IE to work */
#pmenu li ul {display:none;z-index:99;}
/* For Non-IE and IE7 make the sublevels visible on list hover. This is all it needs */
#pmenu li:hover > ul {display:block; position:absolute; top:-11px; left:80px; padding:10px 30px 30px 30px; /**background:transparent url(transparent.gif);*/ width:120px;z-index:99;}
/* Position the first sub level beneath the top level liinks */
#pmenu > li:hover > ul {left:-30px; top:16px;z-index:99;}

/* get rid of the table */
#pmenu table {position:absolute; border-collapse:collapse; top:0; left:0; z-index:100; font-size:1em;}

/* For IE5.5 and IE6 give the hovered links a position relative and a change of background and foreground color. This is needed to trigger IE to show the sub levels */
* html #pmenu li a:hover {position:relative; background:#dfd7ca; color:#c00;z-index:99;}

/* For accessibility of the top level menu when tabbing */
#pmenu li a:active, #pmenu li a:focus {background:#dfd7ca; color:#c00;z-index:99;}

/* Set up the pointers for the sub level indication */
#pmenu li.fly {background:#7484ad;z-index:99;}
#pmenu li.drop {background:#7484ad;z-index:99;}


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



<div id="header"><img name="masthead"
	src="<%=CommonConfiguration.getURLToMastheadGraphic() %>" width="810"
	height="150" border="0" usemap="#m_masthead" alt=""></div>
<div>
<ul id="pmenu">
	<li style="background: #000066;"><a
		href="http://<%=CommonConfiguration.getURLLocation() %>"
		style="margin: 0px 0 0px 0px; position: relative; width: 95px; height: 25px; z-index: 100;"><strong><%=props.getProperty("home")%></strong></a></li>
	<li class="drop"><a
		href="http://<%=CommonConfiguration.getURLLocation() %>/index.jsp"
		style="margin: 0px 0 0px 0px; position: relative; width: 75px; height: 25px; z-index: 100;"><strong><%=props.getProperty("learn")%></strong><!--[if IE 7]><!--></a><!--<![endif]-->
	<!--[if lte IE 6]><table><tr><td><![endif]-->
	<ul>
		<li><a
			href="http://<%=CommonConfiguration.getURLLocation() %>/index.jsp"
			class="enclose"
			style="margin: 0px 0 0px 0px; position: relative; width: 160px; height: 25px; z-index: 100;"><%=props.getProperty("intro")%></strong></a></li>

		</ul>
	<!--[if lte IE 6]></td></tr></table></a><![endif]--></li>
	<li class="drop"><a
		href="http://<%=CommonConfiguration.getURLLocation() %>/submit.jsp"
		style="margin: 0px 0 0px 0px; position: relative; width: 90px; height: 25px; z-index: 100;"><strong><%=props.getProperty("participate")%></strong><!--[if IE 7]><!--></a><!--<![endif]-->
	<!--[if lte IE 6]><table><tr><td><![endif]-->
	<ul>

		<li><a
			href="http://<%=CommonConfiguration.getURLLocation() %>/submit.jsp"
			class="enclose"
			style="margin: 0px 0 0px 0px; position: relative; width: 160px; height: 25px; z-index: 100;"><%=props.getProperty("report")%></a></li>

	</ul>
	<!--[if lte IE 6]></td></tr></table></a><![endif]--></li>
	<li class="drop">
		<a href="http://<%=CommonConfiguration.getURLLocation() %>/allIndividuals.jsp" style="margin: 0px 0 0px 0px; position: relative; width: 100px; height: 25px; z-index: 100;">
		<strong><%=props.getProperty("individuals")%></strong><!--[if IE 7]><!--></a><!--<![endif]--> <!--[if lte IE 6]><table><tr><td><![endif]-->
	<ul>
		<li><a href="http://<%=CommonConfiguration.getURLLocation() %>/allIndividuals.jsp" class="enclose" style="margin: 0px 0 0px 0px; position: relative; width: 130px; height: 25px;"><%=props.getProperty("viewAll")%></a></li>
		
	</ul>
	<!--[if lte IE 6]></td></tr></table></a><![endif]--></li>
	<li class="drop"><a
		href="http://<%=CommonConfiguration.getURLLocation() %>/encounters/allEncounters.jsp?start=1&end=10"
		style="margin: 0px 0 0px 0px; position: relative; width: 100px; height: 25px; z-index: 100;"><strong><%=props.getProperty("encounters")%></strong><!--[if IE 7]><!--></a><!--<![endif]-->
	<!--[if lte IE 6]><table><tr><td><![endif]-->
	<ul>
		<li><a
			href="http://<%=CommonConfiguration.getURLLocation() %>/encounters/allEncounters.jsp?start=1&end=10"
			class="enclose"
			style="margin: 0px 0 0px 0px; position: relative; width: 210px; height: 25px;z-index: 100;"><%=props.getProperty("viewEncounters")%></a></li>

			<li><a href="http://<%=CommonConfiguration.getURLLocation() %>/individualThumbnailSearchResults.jsp?noQuery=true" class="enclose" style="margin: 0px 0 0px 0px; position: relative; width: 130px; height: 25px;"><%=props.getProperty("viewImages")%></a></li>
		
		<li><a
			href="http://<%=CommonConfiguration.getURLLocation() %>/xcalendar/calendar.jsp"
			class="enclose"
			style="margin: 0px 0 0px 0px; position: relative; width: 210px; height: 25px;"><%=props.getProperty("encounterCalendar")%></a></li>


		<li><a
			href="http://<%=CommonConfiguration.getURLLocation() %>/encounters/allEncountersUnapproved.jsp?start=1&end=10&sort=nosort"
			class="enclose"
			style="margin: 0px 0 0px 0px; position: relative; width: 210px; height: 25px;"><%=props.getProperty("viewUnapproved")%></a></li>
		
		<%
		if((request.getParameter("isAdmin")!=null)&&(request.getParameter("isAdmin").equals("true"))) {
		%>
			<li><a href="http://<%=CommonConfiguration.getURLLocation() %>/encounters/allEncounters.jsp?start=1&end=10&sort=nosort&user=<%=request.getRemoteUser()%>"
					class="enclose" style="margin: 0px 0 0px 0px; position: relative; width: 210px; height: 25px;"><%=props.getProperty("viewMySubmissions")%></a>
			</li>
		<%
		}
		%>

		<li>
			<a href="http://<%=CommonConfiguration.getURLLocation() %>/encounters/allEncounters.jsp?start=1&end=10&sort=nosort&rejects=true"
			class="enclose"
			style="margin: 0px 0 0px 0px; position: relative; width: 210px; height: 25px;"><%=props.getProperty("viewUnidentifiable")%></a></li>

	


	</ul>
	<!--[if lte IE 6]></td></tr></table></a><![endif]--></li>
	<li class="drop">
		<a href="http://<%=CommonConfiguration.getURLLocation() %>/welcome.jsp?reflect=http://<%=CommonConfiguration.getURLLocation() %>/encounters/encounterSearch.jsp"
		style="margin: 0px 0 0px 0px; position: relative; width: 85px; height: 25px; z-index: 100;"><strong><%=props.getProperty("search")%></strong>

	<!--[if IE 7]><!--></a><!--<![endif]--> <!--[if lte IE 6]><table><tr><td><![endif]-->
	<ul>


		<li>
			<a href="http://<%=CommonConfiguration.getURLLocation() %>/encounters/encounterSearch.jsp" class="enclose" style="margin: 0px 0 0px 0px; position: relative; width: 130px; height: 25px;">
			<%=props.getProperty("encounterSearch")%></a></li>
		<li><a
			href="http://<%=CommonConfiguration.getURLLocation() %>/individualSearch.jsp"
			class="enclose"
			style="margin: 0px 0 0px 0px; position: relative; width: 130px; height: 25px;"><%=props.getProperty("individualSearch")%></a></li>
		<li><a
			href="http://<%=CommonConfiguration.getURLLocation() %>/googleSearch.jsp"
			class="enclose"
			style="margin: 0px 0 0px 0px; position: relative; width: 130px; height: 25px;"><%=props.getProperty("googleSearch")%></a></li>

	</ul>
	<!--[if lte IE 6]></td></tr></table></a><![endif]--></li>



	<li class="drop" ><a
		href="http://<%=CommonConfiguration.getURLLocation() %>/welcome.jsp?reflect=http://<%=CommonConfiguration.getURLLocation() %>/appadmin/admin.jsp"
		style="margin: 0px 0 0px 0px; position: relative; width: 90px; height: 25px; z-index: 100;"><strong><%=props.getProperty("administer")%></strong>
	<!--[if IE 7]><!--></a><!--<![endif]--> <!--[if lte IE 6]><table><tr><td><![endif]-->
	<ul>
		<li><a
			href="http://<%=CommonConfiguration.getWikiLocation() %>library_access_policy"
			target="_blank" class="enclose"
			style="margin: 0px 0 0px 0px; position: relative; width: 190px; height: 25px;"><%=props.getProperty("accessPolicy")%></a></li>
		<li><a
			href="http://<%=CommonConfiguration.getWikiLocation() %>"
			target="_blank" class="enclose"
			style="margin: 0px 0 0px 0px; position: relative; width: 190px; height: 25px;"><%=props.getProperty("userWiki")%></a></li>

		<%
		if(CommonConfiguration.useSpotPatternRecognition()){
		%>
		<li><a
			href="http://<%=CommonConfiguration.getURLLocation() %>/software/software.jsp"
			class="enclose"
			style="margin: 0px 0 0px 0px; position: relative; width: 190px; height: 25px;"><%=props.getProperty("software")%></a></li>
		<li><a href="http://<%=CommonConfiguration.getURLLocation() %>/appadmin/scanTaskAdmin.jsp"
			class="enclose"
			style="margin: 0px 0 0px 0px; position: relative; width: 190px; height: 25px;"><%=props.getProperty("grid")%></a>
		</li>
		<%
		}
		%>
		
		
		<li><a
			href="http://<%=CommonConfiguration.getURLLocation() %>/appadmin/admin.jsp"
			class="enclose"
			style="margin: 0px 0 0px 0px; position: relative; width: 190px; height: 25px;"><%=props.getProperty("general")%></a></li>
		<li><a
			href="<%=CommonConfiguration.getTapirLinkURL() %>"
			class="enclose"
			style="margin: 0px 0 0px 0px; position: relative; width: 190px; height: 25px;"><%=props.getProperty("tapirLink")%></a></li>
		<%
		if(CommonConfiguration.useTapirLinkURL()){
		%>
		<li><a href="http://<%=CommonConfiguration.getURLLocation() %>/appadmin/kwAdmin.jsp"
			class="enclose" style="margin: 0px 0 0px 0px; position: relative; width: 190px; height: 25px;"><%=props.getProperty("photoKeywords")%></a>
		</li>
		<%
		}
		
		if(CommonConfiguration.allowAdoptions()){
		%>
		<li class="drop"><a
			href="http://<%=CommonConfiguration.getURLLocation() %>/<%=CommonConfiguration.getAdoptionDirectory() %>/adoption.jsp"
			style="margin: 0px 0 0px 0px; position: relative; width: 190px; height: 25px; z-index: 100;"><strong><%=props.getProperty("adoptions")%></strong>
		<img
			src="http://<%=CommonConfiguration.getURLLocation() %>/images/white_triangle.gif"
			border="0" align="absmiddle"><!--[if IE 7]><!--></a><!--<![endif]-->
		<!--[if lte IE 6]><table><tr><td><![endif]-->
		<ul>
			<li><a
				href="http://<%=CommonConfiguration.getURLLocation() %>/<%=CommonConfiguration.getAdoptionDirectory() %>/adoption.jsp"
				class="enclose"
				style="margin: 0px 0 0px 80px; position: relative; width: 190px; height: 25px;"><%=props.getProperty("createEditAdoption")%></a></li>
			<li
				style="margin: 0px 0 0px 80px; position: relative; width: 191px; height: 26px;"><a
				href="http://<%=CommonConfiguration.getURLLocation() %>/<%=CommonConfiguration.getAdoptionDirectory() %>/allAdoptions.jsp"
				class="enclose"
				style="margin: 0px 0 0px 0px; position: relative; width: 190px; height: 25px;"><%=props.getProperty("viewAllAdoptions")%></a></li>

		</ul>
		</li>
		
		<%
		}
		%>
		

		<!--[if lte IE 6]></td></tr></table></a><![endif]--> 
		
	</ul>
	<!--[if lte IE 6]></td></tr></table></a><![endif]--></li>

<li><a href="http://<%=CommonConfiguration.getURLLocation() %>/contactus.jsp" style="margin:0px 0 0px 0px; position:relative; width:90px; height:25px; z-index:100;"><strong><%=props.getProperty("contactUs")%></strong></a></li>
	<%if(request.getRemoteUser()==null) {%>
	<li><a
		href="http://<%=CommonConfiguration.getURLLocation() %>/welcome.jsp"
		style="margin: 0px 0 0px 0px; position: relative; width: 76px; height: 25px; z-index: 100;"><strong><%=props.getProperty("login")%></strong></a></li>
	<%} else {%>
	<li><a
		href="http://<%=CommonConfiguration.getURLLocation() %>/logout.jsp"
		style="margin: 0px 0 0px 0px; position: relative; width: 76px; height: 25px; z-index: 100;"><strong><%=props.getProperty("logout")%></strong></a></li>
	<%}%>

</ul>
</div>
