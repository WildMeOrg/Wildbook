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

  //grab a gridManager
  GridManager gm = GridManagerFactory.getGridManager();
  int numProcessors = gm.getNumProcessors();
  int numWorkItems = gm.getIncompleteWork().size();

  Shepherd myShepherd = new Shepherd();
  
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
  	  		myShepherd.getPM().makePersistent(newRole1);
	  		Role newRole4=new Role("tomcat","destroyer");
	  		myShepherd.getPM().makePersistent(newRole4);
	  		
	  		Role newRole5=new Role("tomcat","manager");
	  		myShepherd.getPM().makePersistent(newRole5);
	  		
	  		Role newRole6=new Role("tomcat","adoption");
	  		myShepherd.getPM().makePersistent(newRole6);
	  		
	  		Role newRole7=new Role("tomcat","imageProcessor");
	  		myShepherd.getPM().makePersistent(newRole7);
	  		
	  		Role newRole8=new Role("tomcat","approve");
				  		myShepherd.getPM().makePersistent(newRole8);
				  		
				  		Role newRole9=new Role("tomcat","identifier");
							  		myShepherd.getPM().makePersistent(newRole9);
							  		
							  		Role newRole2=new Role("tomcat","researcher");
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
  <title><%=CommonConfiguration.getHTMLTitle()%>
  </title>
  <meta http-equiv="Content-Type" content="text/html; charset=utf-8"/>
  <meta name="Description"
        content="<%=CommonConfiguration.getHTMLDescription() %>"/>
  <meta name="Keywords"
        content="<%=CommonConfiguration.getHTMLKeywords() %>"/>
  <meta name="Author" content="<%=CommonConfiguration.getHTMLAuthor() %>"/>
  <link href="<%=CommonConfiguration.getCSSURLLocation(request) %>"
        rel="stylesheet" type="text/css"/>
  <link rel="shortcut icon"
        href="<%=CommonConfiguration.getHTMLShortcutIcon() %>"/>


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
      <div id="maincol">

        <div id="maintext">
          <h1 class="intro">Overview</h1>

          <p class="caption">Manta Matcher represents the first global manta ray database. This site was
specifically designed to manage manta ray sightings and identifications across their
distribution. After the success of the <a href="http://www.whaleshark.org">Wildbook for Whale Sharks</a> database, Manta Matcher
was a logical follow-up. Manta rays are widely distributed, migratory, and have
unique spot patterning on their ventral surface that can be used to permanently
identify individuals. At the present time, this database will enable researchers to
upload and organize individually identified manta rays in their populations. This
system is also intended to promote collaborations by way of cross-referencing
databases to check for both regional and long distance movement.</p>

<p class="caption">An automated component to this site is currently in development. This component
will greatly enhance the functionality of the site by allowing faster and more
accurate cross- referencing.</p>

<p class="caption">It is our expectation that researchers will ultimately be able use sightings data to
determine the abundance, trends, movements, and population structure of manta
ray populations at individual aggregation sites across the globe. Using encounter
photos and encounter information with mantas, you too can help update and
maintain this global database.</p>
        
        </div>

        <div>
          <h1 class="intro">Data Contributors</h1>

          <p class="caption">This project was the brainchild of Dr. Andrea Marshall and her team at the Marine
Megafauna Association in Mozambique who have been collecting manta ray
identification images across the world for the last decade. Her team's contributions
are joined by multiple manta ray research programs from across the world
including, Laje Viva Institute in Brazil, the Pacific Elasmobranch Foundation in
Ecuador, Project Manta in eastern Australia, HAMER in Hawaii, and the Maldivian
Manta Ray Project. Manta Matcher is supported by a number of agencies and
organizations with additional support from a number of governments for effort in
specific regions.</p>
        </div>




      </div>
      <!-- end maincol -->
      <div id="rightcol">


        <div class="module">
          <h3>Find Record</h3>

          <form name="form2" method="get" action="individuals.jsp">
            <em>Enter a marked animal number, encounter number, animal nickname, or alternate
              ID.</em><br/>
            <input name="number" type="text" id="shark" size="25"/>
            <input type="hidden" name="langCode" value="<%=langCode%>"/><br/>
            <input name="Go" type="submit" id="Go2" value="Go"/>
          </form>

        </div>
        
      


        <div class="module">
          <h3>RSS/Atom Feeds</h3>

          <p align="left"><a href="rss.xml"><img src="images/rssfeed.gif"
                                                 width="80" height="15" border="0"
                                                 alt="RSS News Feed"/></a></p>

          <p align="left"><a href="atom.xml"><img
            src="images/atom-feed-icon.gif" border="0" alt="ATOM News Feed"/></a></p>
        </div>


      </div>
      <!-- end rightcol --></div>
    <!-- end main -->
    <jsp:include page="footer.jsp" flush="true"/>
  </div>
  <!-- end page --></div>
<!--end wrapper -->

</body>
</html>
