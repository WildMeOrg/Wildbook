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

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.apache.shiro.crypto.*,org.apache.shiro.util.*,org.apache.shiro.crypto.hash.*,org.ecocean.*,org.ecocean.servlet.ServletUtilities,org.ecocean.grid.GridManager,org.ecocean.grid.GridManagerFactory, java.util.Properties,java.util.ArrayList" %>


<%

String context="context0";
context=ServletUtilities.getContext(request);

  //grab a gridManager
  GridManager gm = GridManagerFactory.getGridManager();
  int numProcessors = gm.getNumProcessors();
  int numWorkItems = gm.getIncompleteWork().size();

  Shepherd myShepherd = new Shepherd(context);
  
  	//check usernames and passwords
	myShepherd.beginDBTransaction();
  	ArrayList<User> users=myShepherd.getAllUsers();
  	if(users.size()==0){
  		String salt=ServletUtilities.getSalt().toHex();
        String hashedPassword=ServletUtilities.hashAndSaltPassword("tomcat123", salt);
        //System.out.println("Creating default hashed password: "+hashedPassword+" with salt "+salt);
        
        
  		User newUser=new User("tomcat",hashedPassword,salt);
  		myShepherd.getPM().makePersistent(newUser);
  		System.out.println("Creating tomcat user account...");
  		
  	  	ArrayList<Role> roles=myShepherd.getAllRoles();
  	  	if(roles.size()==0){
  	  	System.out.println("Creating tomcat roles...");
  	  		
  	  		Role newRole1=new Role("tomcat","admin");
  	  		newRole1.setContext("context0");
  	  		myShepherd.getPM().makePersistent(newRole1);
	  		Role newRole4=new Role("tomcat","destroyer");
	  		newRole4.setContext("context0");
	  		myShepherd.getPM().makePersistent(newRole4);
	  		
	  		Role newRole5=new Role("tomcat","manager");
	  		newRole5.setContext("context0");
	  		myShepherd.getPM().makePersistent(newRole5);
	  		
	  		Role newRole6=new Role("tomcat","adoption");
	  		newRole6.setContext("context0");
	  		myShepherd.getPM().makePersistent(newRole6);
	  		
	  		Role newRole7=new Role("tomcat","imageProcessor");
	  		newRole7.setContext("context0");
	  		myShepherd.getPM().makePersistent(newRole7);
	  		
	  		Role newRole8=new Role("tomcat","approve");
	  		newRole8.setContext("context0");
				  		myShepherd.getPM().makePersistent(newRole8);
				  		
				  		Role newRole9=new Role("tomcat","identifier");
				  		newRole9.setContext("context0");
							  		myShepherd.getPM().makePersistent(newRole9);
							  		
							  		Role newRole2=new Role("tomcat","researcher");
							  		newRole2.setContext("context0");
										  		myShepherd.getPM().makePersistent(newRole2);
	  		
	  		
	  		
	  		
	  		System.out.println("Creating tomcat user account...");
  	  	}
  	}
  	


  	myShepherd.commitDBTransaction();
  	

//setup our Properties object to hold all properties

  //language setup
  String langCode = "en";
  if (session.getAttribute("langCode") != null) {
    langCode = (String) session.getAttribute("langCode");
  }

  Properties props = new Properties();
  props.load(getClass().getResourceAsStream("/bundles/" + langCode + "/overview.properties"));


%>

<html xmlns="http://www.w3.org/1999/xhtml">
<head>
  <title><%=CommonConfiguration.getHTMLTitle(context)%>
  </title>
  <meta http-equiv="Content-Type" content="text/html; charset=utf-8"/>
  <meta name="Description"
        content="<%=CommonConfiguration.getHTMLDescription(context) %>"/>
  <meta name="Keywords"
        content="<%=CommonConfiguration.getHTMLKeywords(context) %>"/>
  <meta name="Author" content="<%=CommonConfiguration.getHTMLAuthor(context) %>"/>
  <link href="<%=CommonConfiguration.getCSSURLLocation(request, context) %>"
        rel="stylesheet" type="text/css"/>
  <link rel="shortcut icon"
        href="<%=CommonConfiguration.getHTMLShortcutIcon(context) %>"/>


  <style type="text/css">
    <!--

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

    .style2 {
      font-size: x-small;
      color: #000000;
    }

    -->
  </style>

</head>

<body>
<div id="wrapper">
  <div id="page">
    <jsp:include page="header.jsp" flush="true">
      <jsp:param name="isAdmin" value="<%=request.isUserInRole(\"admin\")%>" />
    </jsp:include>
    <div id="main">
      <div id="leftcol">
        <div id="menu">

		<div class="module">
    	<h3>Data Sharing</h3>
    	<p><a href="http://data.gbif.org/datasets/provider/261"><center><img src="images/gbif.gif" border="0" alt="Data sharing with the Global Biodiversity Information Facility"/></center></a></p>
		
		<p><a href="http://www.iobis.org/"><center><img src="images/OBIS_logo.gif" alt="Data sharing with the Ocean Biogeographic Information System" border="0" />
		</center></a></p>
		<p><a href="ttp://www.coml.org/"><center><img src="images/coml.gif" alt="Data sharing with the census of marine life" border="0" />
		</center></a></p>
	</div>

        </div>
        <!-- end menu --></div>
      <!-- end leftcol -->
      <div id="maincol-wide">

        <div id="maintext">
          <h1 class="intro">Overview</h1>

          <p class="caption">
Manta Matcher represents the first global online database for manta rays. This innovative site was specifically designed to manage manta ray sightings and identifications across their distribution. 
</p><p class="caption">
After the success of the <a href="http://www.whaleshark.org/">Wildbook for Whale Sharks</a> database, Manta Matcher was a logical follow-up for <a href="http://www.wildme.org/">WildMe</a> and partner organization <a href="http://www.marinemegafauna.org/">Marine Megafauna Foundation</a>. 

</p><p class="caption">
Manta rays are threatened species, vulnerable to extinction. Researchers across the globe are studying these animals in a bid to protect remaining populations. Monitoring wild populations can be difficult since manta rays are elusive, widely distributed and highly migratory. However, all manta rays have unique spot patterning on their undersides that can be used to permanently identify individuals. This ‘bellyprint’ allows researchers to track individuals over space and time and better monitor wild populations. 

</p><p class="caption">
Currently the Manta Matcher database enables researchers to upload, organize, and individually identify the manta rays in their populations. Manta Matcher was also intended to promote scientific collaboration by way of cross-referencing regional databases of manta rays to check for exchanges, learn more about their migratory patterns and life history traits and track long distance movements.

</p><p class="caption">
An automated algorithm, which matches the unique spot patterning of new entries to the existing global database is one of the novel features of this site. This component greatly enhances the functionality of the site by allowing faster and more accurate cross-referencing within and between manta ray databases.

</p><p class="caption">
It is our expectation that researchers will ultimately be able use sightings data to determine the abundance, trends, movements, and population structure of manta ray populations at individual aggregation sites across the globe. This information will help to assess their conservation status and manage wild populations more effectively.

</p><p class="caption">
Manta Matcher, like all of the Wildbooks, encourages public contributions. If you have previously encountered a manta ray and you have images of the underside of the individual, please consider uploading it to the Manta Matcher database. Now that you know about Manta Matcher, we also hope that you choose to share images from your future encounters with manta rays with us! Your contributions can provide valuable information to our participating researchers. Remember, every image is important! 

</p><p class="caption">
Join us in supporting this important global project. By building a network of researchers and citizen scientists around the globe we really can start solving some of the mysteries of the manta rays!
</p>

        </div>


      </div>
      <!-- end maincol -->
  </div>
    <!-- end main -->
    <jsp:include page="footer.jsp" flush="true"/>
  </div>
  <!-- end page --></div>
<!--end wrapper -->

</body>
</html>
