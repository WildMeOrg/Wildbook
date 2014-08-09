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
			newRole1.setContext("context0");
	  		myShepherd.getPM().makePersistent(newRole1);
	  		Role newRole4=new Role("tomcat","destroyer");
			newRole4.setContext("context0");
	  		myShepherd.getPM().makePersistent(newRole4);
	  		
	  		System.out.println("Creating tomcat user account...");
	  	}
	}
	


	myShepherd.commitDBTransaction();
  	

//setup our Properties object to hold all properties

  

  Properties props = new Properties();
  //props.load(getClass().getResourceAsStream("/bundles/" + langCode + "/overview.properties"));

  
	try{
		props = ShepherdProperties.getProperties("overview.properties", langCode);
	}
	catch(Exception e){
		e.printStackTrace();
	}

  

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
        
        
        
        
          <h1 class="intro">Flukebook: Where people and animals connect</h1>

<ul>
	<li>Contribute sightings and follow individual cetaceans across time</li>
	<li>Access powerful data management, analysis, and photoidentification tools</li>
	<li>Connect with people who work with and are passionate about cetaceans</li>
</ul>
        

<h1 class="intro">What is Flukebook?</h1>

<p class="caption">Flukebook is a free, online resource created to assist in the conservation of whales and dolphins in the Atlantic Ocean by improving our knowledge of them. Flukebook helps researchers manage, share, analyze, and archive cetacean data, and provides a portal for citizen scientists to contribute to marine conservation and enables them to follow individual animals they have met. Flukebook has photo-identification data for XXXX individuals of XX species with over XXX sightings ranging from 19XX to today.</p>
        



<h1 class="intro">Why use Flukebook?</h1>
<p class="caption">There is a lot of data out there but until now it’s been scattered due to the sheer geographic range of the animals and the people who want to protect them. 
For the first time, Flukebook provides a place for researchers, conservationists and citizen scientists to work together, consolidate existing research and fill in data gaps. With your help Flukebook will become the richest source of sighting data, providing invaluable information for scientists to collect and analyze to learn more about cetaceans, and ultimately help the global conservation of these magnificent species. 
<h1 class="intro">Flukebook:</h1>
<ul>
	<li>Helps you manage your data</li>
	<li>Provides free data backup </li>
	<li>Keeps your data safe while offering different levels of sharing</li>
	<li>Helps build networks and strengthens research</li>
</ul>


<h1 class="intro">Flukebook is easy to use</h1>

<p class="caption">Everything is already built-in, all you need to do is upload your images along with sighting information and Flukebook does the rest – You’ll be led through matching your whales against the catalogue and how to contribute to growing global collaborations.
</p>


<h1 class="intro">How does Flukebook work?</h1>
<p class="caption">Flukebook uses photographs of flukes, dorsal fins, and any scars, to distinguish between individual animals. Cutting-edge software supports rapid identification using pattern recognition and photo management tools. 
Not a marine researcher? We need your help too. Anyone can contribute to Flukebook. All you need is to submit your photos and any other sighting information and Flukebook can do the rest. Eventually you can even follow an individual whale – find out who it’s friends with, where it goes, and who has seen them lately.
</p>

<h1 class="intro">What does Flukebook do?</h1>
<h3 class="intro">Manage and backup your data</h2>
<p class="caption">You can import photos and sightings data to Flukebook in many common picture formats (Jpeg, RAW, TIFF) and from common photo-management tools like Instagram, Photobucket, or Flickr. Once imported, you can view your sightings online, follow individuals you have identified, add metadata, manage individual data, and use all the other Flukebook features described here. Having your data in Flukebook serves as a free backup of data you have stored on your personal computer.
Live data connection: We are developing a connection between Flukebook and sightings collections apps like Conserve.IO’s SpotterPRO app.
</p>
<h3 class="intro">Researchers control their own data</h2>
<p class="caption">You keep full ownership and control access to your data in Flukebook. Flexible permission settings for data owners mean you can easily share different levels of access with collaborators and the public. Flukebook can be used within-organizations with access to all of the features without sharing any data. 
</p>
<h3 class="intro">Build networks and strengthen research</h2>
<p class="caption"></p>Flukebook helps researchers, organizations and individuals to work together by enabling collaboration when photoidentification matches are made using the built-in matching software. The potential match list will be redacted if your sharing is turned off. You will be able to see that your individuals potentially match other datasets, but you cannot access the data until both data owners agree to privately and reciprocally share data access. By doing this, Flukebook allows for the simple compilation of datasets for multi-institutional collaborative projects. 
</p>

<p class="caption">All photographic, location, and supplementary data, once imported to Flukebook, are in the same format. This feature, together with the ability to share your data privately with specific collaborators, overcomes one of the major hurdles to combining disparate, international datasets. This makes Flukebook a powerful tool for researchers looking for collaborators, prospective graduate students, conservation groups looking for data to help advocacy goals, government developing management plans, and people hoping to change the world.
</p>

<h3 class="intro">Analysis tools</h2>
<p class="caption">Flukebook provides tools for managing photoidentification, molecular sampling, and sightings databases and includes a growing number of features to help users work with additional software by linking to external programs, including ArcGIS, SOCPROG, Genepop, GenAIEx, WinBugs, and Google Earth. 
</p>
<p class="caption">
Flukebook uses a newly designed automated fluke and dorsal fin matching program to accelerate within- and between-organization identifications. You can also use Flukebook to easily annotate weather parameters to your photoidentification or sightings dataset using the NCEP-DOE Reanalysis 2 dataset, provided by the US National Oceanographic and Atmospheric Association (NOAA). Flukebook uses this dataset to provide an estimate of wind speed and direction, temperature, and other variables for each time-location photograph or sighting in your dataset.
 </p>
<h3 class="intro">Archive – saving information for the future</h2>
<p class="caption">Collecting animal identification data takes enormous time, effort, and funding, and can also impact the animals over time. We believe that these data provide invaluable records about citizens of the oceans and should be preserved for future generations. A generation from now, how will these populations have changed? After researchers have published their work, what will happen to their raw data? Will your data be available to help answer new questions about socio-ecology, haplotype evolution, and ocean scale change by the next generation of marine mammalogists? To support this, Flukebook’s infrastructure for archiving identification datasets that will be stored on servers at XXXXXX. 
</p>
        
        
        
        
        
        
        
        
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
