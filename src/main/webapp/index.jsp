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
         import="org.ecocean.ShepherdProperties,org.ecocean.servlet.ServletUtilities,org.apache.shiro.crypto.*,org.apache.shiro.util.*,org.apache.shiro.crypto.hash.*,org.ecocean.*,org.ecocean.servlet.ServletUtilities,org.ecocean.grid.GridManager,org.ecocean.grid.GridManagerFactory, java.util.Properties,java.util.ArrayList" %>


<%

String langCode=ServletUtilities.getLanguageCode(request);

   String context=ServletUtilities.getContext(request);

  //grab a gridManager
  //GridManager gm = GridManagerFactory.getGridManager();
  //int numProcessors = gm.getNumProcessors();
  //int numWorkItems = gm.getIncompleteWork().size();

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
	  		myShepherd.getPM().makePersistent(newRole1);
	  		Role newRole4=new Role("tomcat","destroyer");
	  		myShepherd.getPM().makePersistent(newRole4);
	  		
	  		System.out.println("Creating tomcat user account...");
	  	}
	}
	


	myShepherd.commitDBTransaction();
  	

//setup our Properties object to hold all properties

  

  //Properties props = new Properties();
  //props.load(getClass().getResourceAsStream("/bundles/" + langCode + "/overview.properties"));

  
  Properties props = ShepherdProperties.getProperties("overview.properties", langCode);

  

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
  <link href="<%=CommonConfiguration.getCSSURLLocation(request,context) %>"
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
      
      <div id="maincol-wide">

        <div id="maintext">
          <h1 class="intro">Overview</h1>

          <p class="caption">OceanSmart is a collaboration of researchers, naturalists, conservation organisations and citizen scientists, working together to create a visual database of encounters of individually catalogued marine animals in the Atlantic Ocean. The library is maintained and used by a consortium of marine biologists to collect and analyze sighting data to learn more about these amazing creatures.</p>

<p class="caption">OceanSmart uses photographs of flukes, dorsal fins, and any scars, to distinguish between individual animals. Cutting-edge software supports rapid identification using pattern recognition and photo management tools.
You too can assist with research, by submitting photos and sighting data. The information you submit will be used in mark-recapture studies to help with the global conservation of these magnificent species.</p>
        
        </div>

        <div>
          <h1 class="intro">Data Contributors</h1>

          <p class="caption"><strong>CaribWhale</strong></p>					
<p class="caption">CaribWhale is an association of Caribbean whale watch operators and supporters who are committed to responsible ecotourism. It is a voluntary recognition, conservation, and education program with established criteria that members follow in order to provide the most enriching and safest possible whale watching experience for both passengers and cetaceans. On the most basic level, CaribWhale Operator members are committed to: Responsible and low-impact viewing practices, engaging and educational on-board programming, promoting conservation practices to the public.	

<p class="caption"><strong>Dominica Sperm Whale Project</strong></p>
<p class="caption">Since 2005 the Dominican Sperm Whale Project has worked to unravel mysteries surrounding these ocean giants. The population of whales in the Caribbean has given us the unique opportunity to live among sperm whales and, for the first time, to come to know them not just as animals, but as individuals within families. Our program is the first to have followed families of whales across years. Now nine years into the program, we have followed many calves from birth through weaning and we now know that some individuals have been using the region since 1984. Having spent thousands of hours with over 20 different sperm whale families, we have uncovered mysteries about the sperm whales’ diet, genetics, social relationships, and dialects.  Find out more about Dominica Sperm Whale Project.</p>


<p class="caption"><strong>Elding Whale Watch</strong></p>
<p class="caption">The largest whale watching company is Iceland, Elding contributes sightings to the Ocean Smart database to help make matches between the humpbacks northern feeding grounds, with sightings gathered in the calving and breeding grounds by Caribbean participants.</p>

        </div>

        <div id="context">
          <h1 class="intro">Supporters</h1>

          <p class="caption">
          <table>
          <tr><td style="padding:5px;"><img src="images/logo_WHMSI.jpg" /></td><td style="padding:5px;"><img src="images/OAS_Seal_ENG_Principal.gif" /></td></tr>
          <tr><td><img src="images/caribwhale-logo.jpg" /></td>
          <td><img src="images/DSWPlogoLongText.png" /></td></tr>
          
          </table>
          
          </p>

        </div>


      </div>
      <!-- end maincol -->
      <div id="rightcol">



          <div class="module">
            <h3>Data Sharing</h3>
            <img src="images/OBIS_logo.gif" /><br/>
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
