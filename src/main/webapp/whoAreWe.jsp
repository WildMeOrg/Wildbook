<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
        <%@ page contentType="text/html; charset=utf-8" language="java" import="java.util.ArrayList,java.util.Properties, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*" %>
<%

//setup our Properties object to hold all properties
	Properties props=new Properties();
	String langCode="en";
	

	
	//set up the file input stream
	//FileInputStream propsInputStream=new FileInputStream(new File((new File(".")).getCanonicalPath()+"/webapps/ROOT/WEB-INF/classes/bundles/"+langCode+"/submit.properties"));
	props.load(getClass().getResourceAsStream("/bundles/"+langCode+"/whoweare.properties"));
	
	
%>

<html>
<head>
<title><%=CommonConfiguration.getHTMLTitle() %></title>
<meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
<meta name="Description" content="<%=CommonConfiguration.getHTMLDescription() %>" />
<meta name="Keywords" content="<%=CommonConfiguration.getHTMLKeywords() %>" />
<meta name="Author" content="<%=CommonConfiguration.getHTMLAuthor() %>" />
<link href="<%=CommonConfiguration.getCSSURLLocation(request) %>" rel="stylesheet" type="text/css" />
<link rel="shortcut icon" href="<%=CommonConfiguration.getHTMLShortcutIcon() %>" />


  <script src="http://code.jquery.com/jquery-1.9.1.js"></script>
  <script src="http://code.jquery.com/ui/1.10.2/jquery-ui.js"></script>
  <link href="http://ajax.googleapis.com/ajax/libs/jqueryui/1.8.4/themes/base/jquery-ui.css" rel="stylesheet" type="text/css" />
  
  <style type="text/css">
    <!--
    .style1 {
      color: #FF0000
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
</head>

<body>
<div id="wrapper">
<div id="page">
<jsp:include page="header.jsp" flush="true">

	<jsp:param name="isAdmin" value="<%=request.isUserInRole(\"admin\")%>"/>
</jsp:include>	
<div id="main">

	<div id="maincol-wide-solo">

		<div id="maintext">
		  <h1 class="intro"><%=props.getProperty("title") %></h1>
		</div>
		
		<ul>
			<li><a href="#leaders"><%=props.getProperty("leaders") %></a></li>
			<%
			if(CommonConfiguration.showUsersToPublic()){
			%>	
				<li><a href="#collaborators"><%=props.getProperty("collaborators") %></a></li>
			<%
			}
			%>
			
		</ul>
			
		
		
			<a name="leaders"></a>
			<h3><%=props.getProperty("leaders") %></h3>

			<p><em>ZAVEN ARZOUMANIAN, Director, President, Co-founder</em></p>
			<p>Zaven Arzoumanian earned degrees in Physics at McGill University (B.Sc.) and Princeton University (M.A., Ph.D.). His research interests include the astrophysics of neutron stars and black holes, testing theories of gravitation and the nature of matter in extreme environments, and new technologies for instrumentation in radio and X-ray astronomy. Zaven is employed as a contract scientist at NASA's Goddard Space Flight Center in Greenbelt, MD, USA. Zaven’s interest in Whale Sharks and pattern-matching computations for biological applications was cultivated in a collaboration with Jason Holmberg and Brad Norman, culminating in the development of a technique for matching spot patterns adapted from astronomy, published in the Journal of Applied Ecology (May 2005).<br />
			  Code 662<br />
			  NASA Goddard Space Flight Center<br />
			  Greenbelt, MD 20771<br />
			  USA<br />
			  <a href="mailto:zaven@wildme.org">zaven@wildme.org</a></p>
<p><img src="images/zaven.jpg" width="450" height="293" border="1" /></p>
			
			<p>&nbsp;</p>
			<p><em>JASON HOLMBERG, Director, Secretary, Information Architect, Co-founder</em></p>
			<p>Jason Holmberg started working on this Wildbook in 2002 and has logged over 8,000 hours of development time. As Information Architect, he has designed and implemented new tools to support digital pattern recognition for Whale Sharks. Using Jaon’s tools, the project has been able to categorize and manage a large amount of Whale Shark data and to identify individual animals from multiple photos taken by different researchers many years apart. Jason’s interface also encourages public participation in Whale Shark research via an automated email system that sends individual data contributors automatic updates on individual Whale Shark re-sightings. Jason also undertook further field-testing of the project’s methodology and technology in the Galapagos Islands in October 2004, Honduras in March 2005 and Australia in April 2005. He gave two talks at the International Whale Shark Conference in Perth, Australia in May 2005 and later that year accepted a Duke’s Choice Award on behalf of the team for innovative use of Java technology for Whale Shark data management and pattern recognition. Jason co-authored a widely lauded paper with Zaven Arzoumanian and Brad Norman on a 12-year population study at Ningaloo Marine Park in Ecological Applications (January 2008).<br />
			  Wild Me Information Architect<br />
			  1726 N Terry Street<br />
			  Portland, Oregon 97217 <br />
			  USA<br />
			  <a href="mailto:jason@whaleshark.org">jason@whaleshark.org</a></p>
<p><img src="images/holmberg.jpg" width="450" height="293" border="1" /> </p>
	<p>&nbsp;</p>
			<p><em>ANDREA MARSHALL, Science Coordinator</em></p>
			<p>Andrea Marshall is a conservation biologist, working in Mozambique on the  ecology of both species of manta rays. Educated in the United States and Australia, Andrea was the first person in the world to complete a PhD on manta rays. After completing her thesis Andrea stayed on to spearhead the conservation efforts of these species in Mozambique. After almost a decade of work in the region, she founded the Marine Megafauna Association.  Her world-leading manta ray research program examines aspects of their biology, reproductive ecology, habitat use, migrations and social behaviour. Aside from dramatically increasing the level of knowledge on manta rays themselves, Andrea’s discovery of a new giant species of manta ray in 2008 was one of the largest new species to have been described by any scientist in the last 50 years. In addition to her busy international research schedule Andrea spends many months of the year working on conservation initiatives at home and abroad.</p>
<p><img src="images/AndreaGiantManta2.jpg" width="450" height="300" border="1" /> </p>
			<p>&nbsp;</p>
			<p><em>MARK MCBRIDE, Director</em></p>
			<p>Mark McBride is a software developer at Twitter Inc., building APIs that allow developers to access Twitter capabilities. He is also on the board of Wild Me. He is interested in all aspects of software development, as well as sustainability and conservation.</p>
<p><img src="images/mcbride.jpg" width="450" height="300" border="1" /> </p>
			<p>&nbsp;</p>
			
    			<p><em>SIMON PIERCE, Director, Science Coordinator</em></p>
    			<p>Simon Pierce is a conservation ecologist focusing on threatened marine species, particularly sharks and rays. Simon, a New Zealand native, has been based in Mozambique since 2005 where he leads research efforts on a large year-round aggregation of whale sharks. Simon's whale shark research examines the population ecology, movements and conservation status of these enormous fishes. His research is designed to bridge the gap between science in management in order to develop and implement effective conservation solutions. Simon is a Lead Scientist at the Marine Megafauna Association in Mozambique and Executive Director of Eyes on the Horizon, a national Mozambican marine conservation organisation. He holds a BSc degree from Victoria University of Wellington (NZ) and a BSc (Hons) and PhD from The University of Queensland (Australia).</p>
<p><img src="images/SimonPierce.jpg" width="450" height="300" border="1" /> </p>
				
			<p>&nbsp;</p>
			<p><em>ZAID ZAID, Director</em><br />
			  Zaid A. Zaid is a senior associate in the Litigation/Controversy Department, and a member of the Investigations and Criminal Litigation Practice Group. Zaid completed a summer clerkship in the Office of the Legal Advisor at the United States Department of State, where he drafted memos concerning the Iran-US Claims Tribunal and researched for NAFTA Chapter 11 investment claims against the US Government. Zaid was a political officer in the Foreign Service from 1999 to 2006, and he worked at US embassies in Syria, Tunis, Cairo, the US Mission to the United Nations, and with the Coalition Provisional Authority in Iraq as the liaison to the Iraqi Governing Council as well as at the US Embassy in Baghdad. He is a member of the New York State Bar Association, the American Bar Association. He is the chair of the Fletcher Alumni Club of Washington DC, a board member of Wild Me, a member of the American Constitution Society, a term member with the Council on Foreign Relations, and a fellow with the Truman National Security Project. <br />
			  1875 Pennsylvania Avenue NW<br />
			  Washington, DC 20006<br />
			  USA<br />
			  <a href="mailto:zaidazaid@gmail.com">zaidazaid@gmail.com</a></p>
	<p><img src="images/zaid.jpg" alt="" width="450" height="293" border="1" /></p>
			<p>&nbsp;</p>
			<p><em>Jacob Levenson, Director</em><br />
			  TBD</p>

			<p>&nbsp;</p>
	<%
	if(CommonConfiguration.showUsersToPublic()){

	Shepherd myShepherd = new Shepherd();
	 myShepherd.beginDBTransaction();
     ArrayList<User> allUsers=myShepherd.getAllUsers();
     int numUsers=allUsers.size();
     
 	%>		
	<a name="collaborators"></a>
	<h3><%=props.getProperty("collaborators") %> (<%=numUsers %>)</h3>
<table>
<%
     
     
     int userNum=-1;
     for(int i=0;i<numUsers;i++){
    	 userNum++;
       	User thisUser=allUsers.get(i);
       	String username=thisUser.getUsername();
    	 %>
          <tr><td> 
           <table align="left">
           	<%
    	
    		
           	String profilePhotoURL="images/empty_profile.jpg";
		    
    		if(thisUser.getUserImage()!=null){
    			profilePhotoURL="/"+CommonConfiguration.getDataDirectoryName()+"/users/"+thisUser.getUsername()+"/"+thisUser.getUserImage().getFilename();

    		}
    		%>
			<tr><td><div style="height: 50px">
	<a style="color:blue;cursor: pointer;" id="username<%=userNum%>"><img style="height: 100%" border="1" align="top" src="<%=profilePhotoURL%>"  /></a>
</div></td></tr>
			<%
    		String displayName="";
    		if(thisUser.getFullName()!=null){
    			displayName=thisUser.getFullName();
    		
    		%>
    		<tr><td style="border:none"><center><a style="color:blue;cursor: pointer;" id="username<%=userNum%>" style="font-weight:normal;border:none"><%=displayName %></a></center></td></tr>
    		<%	
    		}
    		
    		%>
    	</table>
    
    		
    		<!-- Now prep the popup dialog -->
    		<div id="dialog<%=userNum%>" title="<%=displayName %>" style="display:none">
    			<table cellpadding="3px"><tr><td>
    			<div style="height: 150px"><img border="1" align="top" src="<%=profilePhotoURL%>" style="height: 100%" />
    			</td>
    			<td><p>
    			<%
    			if(thisUser.getAffiliation()!=null){
    			%>
    			<strong>Affiliation:</strong> <%=thisUser.getAffiliation() %><br />
    			<%	
    			}
    			
    			if(thisUser.getUserProject()!=null){
    			%>
    			<strong>Research Project:</strong> <%=thisUser.getUserProject() %><br />
    			<%	
    			}
    			
    			if(thisUser.getUserURL()!=null){
        			%>
        			<strong>Web site:</strong> <a style="font-weight:normal;color: blue" class="ecocean" href="<%=thisUser.getUserURL()%>"><%=thisUser.getUserURL() %></a><br />
        			<%	
        			}
    			
    			if(thisUser.getUserStatement()!=null){
        			%>
        			<br /><em>"<%=thisUser.getUserStatement() %>"</em>
        			<%	
        			}
    			%>
    			</p>
    			</td></tr></table>
    		</div>
    		<!-- popup dialog script -->

<script>
   var dlg<%=userNum%> = $("#dialog<%=userNum%>").dialog({
     autoOpen: false,
     draggable: false,
     resizable: false,
     width: 500
   });
   
   $("a#username<%=userNum%>").click(function() {
     dlg<%=userNum%>.dialog("open");
   });
</script>
	</td></tr>
    		
    		<% 
       	
       	
     } //end looping through users
     %>
     </table>
     <%
	} //end if(CommonConfiguration.showUsersToPublic()){
	%>

	
	
	<!-- end maintext -->

  </div><!-- end maincol -->

<jsp:include page="footer.jsp" flush="true" />
</div><!-- end page -->
</div><!--end wrapper -->
</body>
</html>
