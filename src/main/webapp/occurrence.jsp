<%--
  ~ The Shepherd Project - A Mark-Recapture Framework
  ~ Copyright (C) 2012 Jason Holmberg
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

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java"
         import="com.drew.imaging.jpeg.JpegMetadataReader,com.drew.metadata.Directory, 	   
		 com.drew.metadata.Metadata,com.drew.metadata.Tag,org.ecocean.*,org.ecocean.servlet.ServletUtilities,java.io.File, java.util.*, org.ecocean.genetics.*" %>

<%

  //handle some cache-related security
  response.setHeader("Cache-Control", "no-cache"); //Forces caches to obtain a new copy of the page from the origin server
  response.setHeader("Cache-Control", "no-store"); //Directs caches not to store the page under any circumstance
  response.setDateHeader("Expires", 0); //Causes the proxy cache to see the page as "stale"
  response.setHeader("Pragma", "no-cache"); //HTTP 1.0 backward compatibility

  //setup data dir
  String rootWebappPath = getServletContext().getRealPath("/");
  File webappsDir = new File(rootWebappPath).getParentFile();
  File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName());
  //if(!shepherdDataDir.exists()){shepherdDataDir.mkdir();}
  File encountersDir=new File(shepherdDataDir.getAbsolutePath()+"/encounters");
  //if(!encountersDir.exists()){encountersDir.mkdir();}
  //File thisEncounterDir = new File(encountersDir, number);

//setup our Properties object to hold all properties
  Properties props = new Properties();
  String langCode = "en";

  if (session.getAttribute("langCode") != null) {
    langCode = (String) session.getAttribute("langCode");
  }


  //load our variables for the submit page

  props.load(getClass().getResourceAsStream("/bundles/" + langCode + "/occurrence.properties"));

  String name = request.getParameter("number").trim();
  Shepherd myShepherd = new Shepherd();



  boolean isOwner = false;
  if (request.isUserInRole("admin")) {
    isOwner = true;
  }

%>

<html>
<head>

  <title><%=CommonConfiguration.getHTMLTitle() %>
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
    .style1 {
      color: #000000;
      font-weight: bold;
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


  <!--
    1 ) Reference to the files containing the JavaScript and CSS.
    These files must be located on your server.
  -->

  <script type="text/javascript" src="highslide/highslide/highslide-with-gallery.js"></script>
  <link rel="stylesheet" type="text/css" href="highslide/highslide/highslide.css"/>

  <!--
    2) Optionally override the settings defined at the top
    of the highslide.js file. The parameter hs.graphicsDir is important!
  -->

  <script type="text/javascript">
    hs.graphicsDir = 'highslide/highslide/graphics/';
    hs.align = 'center';
    hs.transitions = ['expand', 'crossfade'];
    hs.outlineType = 'rounded-white';
    hs.fadeInOut = true;
    //hs.dimmingOpacity = 0.75;

    //define the restraining box
    hs.useBox = true;
    hs.width = 810;
    hs.height = 500;

    //block right-click user copying if no permissions available
    <%
    if(!request.isUserInRole("imageProcessor")){
    %>
    hs.blockRightClick = true;
    <%
    }
    %>

    // Add the controlbar
    hs.addSlideshow({
      //slideshowGroup: 'group1',
      interval: 5000,
      repeat: false,
      useControls: true,
      fixedControls: 'fit',
      overlayOptions: {
        opacity: 0.75,
        position: 'bottom center',
        hideOnMouseOut: true
      }
    });

  </script>

<!--  FACEBOOK LIKE BUTTON -->
<div id="fb-root"></div>
<script>(function(d, s, id) {
  var js, fjs = d.getElementsByTagName(s)[0];
  if (d.getElementById(id)) return;
  js = d.createElement(s); js.id = id;
  js.src = "//connect.facebook.net/en_US/all.js#xfbml=1";
  fjs.parentNode.insertBefore(js, fjs);
}(document, 'script', 'facebook-jssdk'));</script>

<!-- GOOGLE PLUS-ONE BUTTON -->
<script type="text/javascript">
  (function() {
    var po = document.createElement('script'); po.type = 'text/javascript'; po.async = true;
    po.src = 'https://apis.google.com/js/plusone.js';
    var s = document.getElementsByTagName('script')[0]; s.parentNode.insertBefore(po, s);
  })();
</script>




</head>

<body <%if (request.getParameter("noscript") == null) {%> onload="initialize()" onunload="GUnload()" <%}%>>
<div id="wrapper">
<div id="page">
<jsp:include page="header.jsp" flush="true">

	<jsp:param name="isAdmin" value="<%=request.isUserInRole(\"admin\")%>" />
</jsp:include>
<div id="main">


<div id="maincol-wide-solo">

<div id="maintext">
<%
  myShepherd.beginDBTransaction();
  try {
    if (myShepherd.isOccurrence(name)) {


      Occurrence sharky = myShepherd.getOccurrence(name);
      boolean hasAuthority = ServletUtilities.isUserAuthorizedForOccurrence(sharky, request);

%>

<table><tr>

<td valign="middle">
 <h1><strong><%=props.getProperty("occurrence") %></strong>: <%=sharky.getOccurrenceID()%></h1>
<p class="caption"><em><%=props.getProperty("description") %></em></p>
 <table><tr valign="middle">  
  <td>
    <!-- Google PLUS-ONE button -->
<g:plusone size="small" annotation="none"></g:plusone>
</td>
<td>
<!--  Twitter TWEET THIS button -->
<a href="https://twitter.com/share" class="twitter-share-button" data-count="none">Tweet</a>
<script>!function(d,s,id){var js,fjs=d.getElementsByTagName(s)[0];if(!d.getElementById(id)){js=d.createElement(s);js.id=id;js.src="//platform.twitter.com/widgets.js";fjs.parentNode.insertBefore(js,fjs);}}(document,"script","twitter-wjs");</script>
</td>
<td>
<!-- Facebook LIKE button -->
<div class="fb-like" data-send="false" data-layout="button_count" data-width="100" data-show-faces="false"></div>
</td>
</tr></table> </td></tr></table>

<p><%=props.getProperty("groupBehavior") %>: 
<%
if(sharky.getGroupBehavior()!=null){
%>
	<%=sharky.getGroupBehavior() %>
<%
}
%>
&nbsp; <%if (hasAuthority && CommonConfiguration.isCatalogEditable()) {%>[<a
    href="occurrence.jsp?number=<%=name%>&edit=groupBehavior#groupBehavior"><%=props.getProperty("edit")%>
  </a>]<%}%>
</p>
<%
  if (hasAuthority && (request.getParameter("edit") != null) && (request.getParameter("edit").equals("groupBehavior"))) {
%>
<br />
<a name="setGroupBehavior">
<table border="1" cellpadding="1" cellspacing="0" bordercolor="#000000" bgcolor="#99CCFF">
  <tr>
    <td align="left" valign="top"><span class="style1"><%=props.getProperty("setGroupBehavior") %></span></td>
  </tr>
  <tr>
    <td align="left" valign="top">
      <form name="set_groupBhevaior" method="post" action="OccurrenceSetGroupBehavior">
            <input name="number" type="hidden" value="<%=request.getParameter("number")%>" /> 
            <%=props.getProperty("groupBehavior") %>:
        
        <%
        if(CommonConfiguration.getProperty("occurrenceGroupBehavior0")==null){
        %>
        <textarea name="behaviorComment" type="text" id="behaviorComment" maxlength="500"></textarea> 
        <%
        }
        else{   
        %>
        	
        	<select name="behaviorComment" id="behaviorComment">
        		<option value=""></option>
   
   				<%
   				boolean hasMoreStages=true;
   				int taxNum=0;
   				while(hasMoreStages){
   	  				String currentLifeStage = "occurrenceGroupBehavior"+taxNum;
   	  				if(CommonConfiguration.getProperty(currentLifeStage)!=null){
   	  				%>
   	  	 
   	  	  			<option value="<%=CommonConfiguration.getProperty(currentLifeStage)%>"><%=CommonConfiguration.getProperty(currentLifeStage)%></option>
   	  				<%
   					taxNum++;
      				}
      				else{
         				hasMoreStages=false;
      				}
      
   				}
   			%>
  			</select>
        
        
        <%
        }
        %>
        <input name="groupBehaviorName" type="submit" id="Name" value="<%=props.getProperty("set") %>">
        </form>
    </td>
  </tr>
</table>
</a><br /> 
<%}%>
<p><%=props.getProperty("numMarkedIndividuals") %>: <%=sharky.getMarkedIndividualNamesForThisOccurrence().size() %></p>

<p><%=props.getProperty("estimatedNumMarkedIndividuals") %>: 
<%
if(sharky.getIndividualCount()!=null){
%>
	<%=sharky.getIndividualCount() %>
<%
}
%>
&nbsp; <%if (hasAuthority && CommonConfiguration.isCatalogEditable()) {%>[<a
    href="occurrence.jsp?number=<%=name%>&edit=individualCount#individualCount"><%=props.getProperty("edit")%>
  </a>]<%}%>
</p>

<%
  if (hasAuthority && (request.getParameter("edit") != null) && (request.getParameter("edit").equals("individualCount"))) {
%>


<a name="individualCount">
<table border="1" cellpadding="1" cellspacing="0" bordercolor="#000000" bgcolor="#99CCFF">
  <tr>
    <td align="left" valign="top"><span class="style1"><%=props.getProperty("setIndividualCount") %></span></td>
  </tr>
  <tr>
    <td align="left" valign="top">
      <form name="set_individualCount" method="post" action="OccurrenceSetIndividualCount">
            <input name="number" type="hidden" value="<%=request.getParameter("number")%>" /> 
            <%=props.getProperty("newIndividualCount") %>:

        <input name="count" type="text" id="count" size="5" maxlength="7"></input> 
        <input name="individualCountButton" type="submit" id="individualCountName" value="<%=props.getProperty("set") %>">
        </form>
    </td>
  </tr>
</table>
</a><br />


<%
  }
%>
<p><%=props.getProperty("locationID") %>: 
<%
if(sharky.getLocationID()!=null){
%>
	<%=sharky.getLocationID() %>
<%
}
%>
</p>
<table id="encounter_report" width="100%">
<tr>

<td align="left" valign="top">

<p><strong><%=sharky.getNumberEncounters()%>
</strong>
  <%=props.getProperty("numencounters") %>
</p> 

<table id="results" width="100%">
  <tr class="lineitem">
      <td class="lineitem" align="left" valign="top" bgcolor="#99CCFF"><strong><%=props.getProperty("date") %></strong></td>
    <td class="lineitem" align="left" valign="top" bgcolor="#99CCFF"><strong><%=props.getProperty("individualID") %></strong></td>
    
    <td class="lineitem" align="left" valign="top" bgcolor="#99CCFF"><strong><%=props.getProperty("location") %></strong></td>
    <td class="lineitem" bgcolor="#99CCFF"><strong><%=props.getProperty("dataTypes") %></strong></td>
    <td class="lineitem" align="left" valign="top" bgcolor="#99CCFF"><strong><%=props.getProperty("encnum") %></strong></td>
    <td class="lineitem" align="left" valign="top" bgcolor="#99CCFF"><strong><%=props.getProperty("alternateID") %></strong></td>

    <td class="lineitem" align="left" valign="top" bgcolor="#99CCFF"><strong><%=props.getProperty("sex") %></strong></td>
    <%
      if (isOwner && CommonConfiguration.useSpotPatternRecognition()) {
    %>

    	<td align="left" valign="top" bgcolor="#99CCFF">
    		<strong><%=props.getProperty("spots") %></strong>
    	</td>
    <%
    }
    %>
   <td class="lineitem" align="left" valign="top" bgcolor="#99CCFF"><strong><%=props.getProperty("behavior") %></td>
 <td class="lineitem" align="left" valign="top" bgcolor="#99CCFF"><strong><%=props.getProperty("haplotype") %></td>
 
  </tr>
  <%
    Encounter[] dateSortedEncs = sharky.getDateSortedEncounters(false);

    int total = dateSortedEncs.length;
    for (int i = 0; i < total; i++) {
      Encounter enc = dateSortedEncs[i];
      
        Vector encImages = enc.getAdditionalImageNames();
        String imgName = "";
        
          imgName = "/"+CommonConfiguration.getDataDirectoryName()+"/encounters/" + enc.getEncounterNumber() + "/thumb.jpg";
        
  %>
  <tr>
      <td class="lineitem"><%=enc.getDate()%>
    </td>
    <td class="lineitem">
    	<%
    	if((enc.getIndividualID()!=null)&&(!enc.getIndividualID().toLowerCase().equals("unassigned"))){
    	%>
    	<a href="individuals.jsp?number=<%=enc.getIndividualID()%>"><%=enc.getIndividualID()%></a>
    	<%
    	}
    	else{
    	%>
    	&nbsp;
    	<%
    	}
    	%>
    </td>
    <%
    String location="&nbsp;";
    if(enc.getLocation()!=null){
    	location=enc.getLocation();
    }
    %>
    <td class="lineitem"><%=location%>
    </td>
    <td width="100" height="32px" class="lineitem">
    	<a href="http://<%=CommonConfiguration.getURLLocation(request)%>/encounters/encounter.jsp?number=<%=enc.getEncounterNumber()%>">
    		
    		<%
    		//if the encounter has photos, show photo folder icon
    		if((enc.getImages()!=null) && (enc.getImages().size()>0)){
    		%>
    			<img src="images/Crystal_Clear_filesystem_folder_image.png" height="32px" width="*" />
    		<%
    		}
    		
    		//if the encounter has a tissue sample, show an icon
    		if((enc.getTissueSamples()!=null) && (enc.getTissueSamples().size()>0)){
    		%>
    			<img src="images/microscope.gif" height="32px" width="*" />
    		<%
    		}
    		//if the encounter has a measurement, show the measurement icon
    		if(enc.hasMeasurements()){
    		%>	
    			<img src="images/ruler.png" height="32px" width="*" />
        	<%	
    		}
    		%>
    		
    	</a>
    </td>
    <td class="lineitem"><a
      href="http://<%=CommonConfiguration.getURLLocation(request)%>/encounters/encounter.jsp?number=<%=enc.getEncounterNumber()%><%if(request.getParameter("noscript")!=null){%>&noscript=null<%}%>"><%=enc.getEncounterNumber()%>
    </a></td>

    <%
      if (enc.getAlternateID() != null) {
    %>
    <td class="lineitem"><%=enc.getAlternateID()%>
    </td>
    <%
    } else {
    %>
    <td class="lineitem"><%=props.getProperty("none")%>
    </td>
    <%
      }
    %>



    <td class="lineitem"><%=enc.getSex()%>
    </td>

    <%
      if (CommonConfiguration.useSpotPatternRecognition()) {
    %>
    <%if (((enc.getSpots().size() == 0) && (enc.getRightSpots().size() == 0)) && (isOwner)) {%>
    <td class="lineitem">&nbsp;</td>
    <% } else if (isOwner && (enc.getSpots().size() > 0) && (enc.getRightSpots().size() > 0)) {%>
    <td class="lineitem">LR</td>
    <%} else if (isOwner && (enc.getSpots().size() > 0)) {%>
    <td class="lineitem">L</td>
    <%} else if (isOwner && (enc.getRightSpots().size() > 0)) {%>
    <td class="lineitem">R</td>
    <%
        }
      }
    %>
    
  
    <td class="lineitem">
    <%
    if(enc.getBehavior()!=null){
    %>
    <%=enc.getBehavior() %>
    <%	
    }
    else{
    %>
    &nbsp;
    <%	
    }
    %>
    </td>
    
  <td class="lineitem">
    <%
    if(enc.getHaplotype()!=null){
    %>
    <%=enc.getHaplotype() %>
    <%	
    }
    else{
    %>
    &nbsp;
    <%	
    }
    %>
    </td>
  </tr>
  <%
      
    } //end for

  %>


</table>


<!-- Start thumbnail gallery -->

<br />
<p>
  <strong><%=props.getProperty("imageGallery") %>
  </strong></p>

    <%
    String[] keywords=keywords=new String[0];
		int numThumbnails = myShepherd.getNumThumbnails(sharky.getEncounters().iterator(), keywords);
		if(numThumbnails>0){	
		%>

<table id="results" border="0" width="100%">
    <%

			
			int countMe=0;
			//Vector thumbLocs=new Vector();
			ArrayList<SinglePhotoVideo> thumbLocs=new ArrayList<SinglePhotoVideo>();
			
			int  numColumns=3;
			int numThumbs=0;
			  if (CommonConfiguration.allowAdoptions()) {
				  ArrayList adoptions = myShepherd.getAllAdoptionsForMarkedIndividual(name);
				  int numAdoptions = adoptions.size();
				  if(numAdoptions>0){
					  numColumns=2;
				  }
			  }

			try {
				thumbLocs=myShepherd.getThumbnails(request, sharky.getEncounters().iterator(), 1, 99999, keywords);
				numThumbs=thumbLocs.size();
			%>

  <tr valign="top">
 <td>
 <!-- HTML Codes by Quackit.com -->
<div style="text-align:left;border:1px solid black;width:100%;height:400px;overflow-y:scroll;overflow-x:scroll;">

      <%
      						while(countMe<numThumbs){
							//for(int columns=0;columns<numColumns;columns++){
								if(countMe<numThumbs) {
									//String combined ="";
									//if(myShepherd.isAcceptableVideoFile(thumbLocs.get(countMe).getFilename())){
									//	combined = "http://" + CommonConfiguration.getURLLocation(request) + "/images/video.jpg" + "BREAK" + thumbLocs.get(countMe).getCorrespondingEncounterNumber() + "BREAK" + thumbLocs.get(countMe).getFilename();
									//}
									//else{
									//	combined= thumbLocs.get(countMe).getCorrespondingEncounterNumber() + "/" + thumbLocs.get(countMe).getDataCollectionEventID() + ".jpg" + "BREAK" + thumbLocs.get(countMe).getCorrespondingEncounterNumber() + "BREAK" + thumbLocs.get(countMe).getFilename();
							              
									//}

									//StringTokenizer stzr=new StringTokenizer(combined,"BREAK");
									//String thumbLink=stzr.nextToken();
									//String encNum=stzr.nextToken();
									//int fileNamePos=combined.lastIndexOf("BREAK")+5;
									//String fileName=combined.substring(fileNamePos).replaceAll("%20"," ");
									String thumbLink="";
									boolean video=true;
									if(!myShepherd.isAcceptableVideoFile(thumbLocs.get(countMe).getFilename())){
										thumbLink="/"+CommonConfiguration.getDataDirectoryName()+"/encounters/"+thumbLocs.get(countMe).getCorrespondingEncounterNumber()+"/"+thumbLocs.get(countMe).getDataCollectionEventID()+".jpg";
										video=false;
									}
									else{
										thumbLink="http://"+CommonConfiguration.getURLLocation(request)+"/images/video.jpg";
										
									}
									String link="/"+CommonConfiguration.getDataDirectoryName()+"/encounters/"+thumbLocs.get(countMe).getCorrespondingEncounterNumber()+"/"+thumbLocs.get(countMe).getFilename();
						
							%>

   
    
      <table align="left" width="<%=100/numColumns %>%">
        <tr>
          <td valign="top">
			
              <%
			if(isOwner){
												%>
            <a href="<%=link%>" 
            <%
            if(thumbLink.indexOf("video.jpg")==-1){
            %>
            	class="highslide" onclick="return hs.expand(this)"
            <%
            }
            %>
            >
            <%
            }
             %>
              <img src="<%=thumbLink%>" alt="photo" border="1" title="Click to enlarge"/>
              <%
                if (isOwner) {
              %>
            </a>
              <%
			}
            
			%>

            <div 
            <%
            if(!thumbLink.endsWith("video.jpg")){
            %>
            class="highslide-caption"
            <%
            }
            %>
            >

              <table>
                <tr>
                  <td align="left" valign="top">

                    <table>
                      <%

                        int kwLength = keywords.length;
                        Encounter thisEnc = myShepherd.getEncounter(thumbLocs.get(countMe).getCorrespondingEncounterNumber());
                      %>
                      
                      

                      <tr>
                        <td><span
                          class="caption"><%=props.getProperty("location") %>: <%=thisEnc.getLocation() %></span>
                        </td>
                      </tr>
                      <tr>
                        <td><span
                          class="caption"><%=props.getProperty("locationID") %>: <%=thisEnc.getLocationID() %></span>
                        </td>
                      </tr>
                      <tr>
                        <td><span
                          class="caption"><%=props.getProperty("date") %>: <%=thisEnc.getDate() %></span>
                        </td>
                      </tr>
                      <tr>
                        <td><span class="caption"><%=props.getProperty("catalogNumber") %>: <a
                          href="encounters/encounter.jsp?number=<%=thisEnc.getCatalogNumber() %>"><%=thisEnc.getCatalogNumber() %>
                        </a></span></td>
                      </tr>
                        <tr>
                        <td><span class="caption"><%=props.getProperty("individualID") %>: <a
                          href="individuals.jsp?number=<%=thisEnc.getIndividualID() %>"><%=thisEnc.getIndividualID() %>
                        </a></span></td>
                      </tr>
                      <%
                        if (thisEnc.getVerbatimEventDate() != null) {
                      %>
                      <tr>

                        <td><span
                          class="caption"><%=props.getProperty("verbatimEventDate") %>: <%=thisEnc.getVerbatimEventDate() %></span>
                        </td>
                      </tr>
                      <%
                        }
                      %>
                      <tr>
                        <td><span class="caption">
											<%=props.getProperty("matchingKeywords") %>
											<%
											 //while (allKeywords2.hasNext()) {
					                          //Keyword word = (Keyword) allKeywords2.next();
					                          
					                          
					                          //if (word.isMemberOf(encNum + "/" + fileName)) {
											  //if(thumbLocs.get(countMe).getKeywords().contains(word)){
					                        	  
					                            //String renderMe = word.getReadableName();
												List<Keyword> myWords = thumbLocs.get(countMe).getKeywords();
												int myWordsSize=myWords.size();
					                            for (int kwIter = 0; kwIter<myWordsSize; kwIter++) {
					                              //String kwParam = keywords[kwIter];
					                              //if (kwParam.equals(word.getIndexname())) {
					                              //  renderMe = "<strong>" + renderMe + "</strong>";
					                              //}
					                      		 	%>
					 								<br/><%= ("<strong>" + myWords.get(kwIter).getReadableName() + "</strong>")%>
					 								<%
					                            }




					                          //    }
					                       // } 

                          %>
										</span></td>
                      </tr>
                    </table>
                    <br/>

                    <%
                      if (CommonConfiguration.showEXIFData()) {
                   
            	if(!thumbLink.endsWith("video.jpg")){
           		 %>							
					<span class="caption">
						<div class="scroll">	
						<span class="caption">
					<%
            if ((thumbLocs.get(countMe).getFilename().toLowerCase().endsWith("jpg")) || (thumbLocs.get(countMe).getFilename().toLowerCase().endsWith("jpeg"))) {
              try{
              File exifImage = new File(encountersDir.getAbsolutePath() + "/" + thisEnc.getCatalogNumber() + "/" + thumbLocs.get(countMe).getFilename());
              Metadata metadata = JpegMetadataReader.readMetadata(exifImage);
              // iterate through metadata directories
              Iterator directories = metadata.getDirectoryIterator();
              while (directories.hasNext()) {
                Directory directory = (Directory) directories.next();
                // iterate through tags and print to System.out
                Iterator tags = directory.getTagIterator();
                while (tags.hasNext()) {
                  Tag tag = (Tag) tags.next();

          %>
								<%=tag.toString() %><br/>
								<%
                      }
                    }
                    } //end try
            catch(Exception e){
            	 %>
		            <p>Cannot read metadata for this file.</p>
            	<%
            	System.out.println("Cannout read metadata for: "+thumbLocs.get(countMe).getFilename());
            	e.printStackTrace();
            }

                  }
                %>
   									
   								
   								</span>
            </div>
   								</span>
   			<%
            	}
   			%>


                  </td>
                  <%
                    }
                  %>
                </tr>
              </table>
            </div>
            

</td>
</tr>

 <%
            if(!thumbLink.endsWith("video.jpg")){
 %>
<tr>
  <td><span class="caption"><%=props.getProperty("location") %>: <%=thisEnc.getLocation() %></span>
  </td>
</tr>
<tr>
  <td><span
    class="caption"><%=props.getProperty("locationID") %>: <%=thisEnc.getLocationID() %></span></td>
</tr>
<tr>
  <td><span class="caption"><%=props.getProperty("date") %>: <%=thisEnc.getDate() %></span></td>
</tr>
<tr>
  <td><span class="caption"><%=props.getProperty("catalogNumber") %>: <a
    href="encounters/encounter.jsp?number=<%=thisEnc.getCatalogNumber() %>"><%=thisEnc.getCatalogNumber() %>
  </a></span></td>
</tr>
                        <tr>
                        <td><span class="caption"><%=props.getProperty("individualID") %>: <a
                          href="individuals.jsp?number=<%=thisEnc.getIndividualID() %>"><%=thisEnc.getIndividualID() %>
                        </a></span></td>
                      </tr>
<tr>
  <td><span class="caption">
											<%=props.getProperty("matchingKeywords") %>
											<%
                        //int numKeywords=myShepherd.getNumKeywords();
											 //while (allKeywords2.hasNext()) {
					                          //Keyword word = (Keyword) allKeywords2.next();
					                          
					                          
					                          //if (word.isMemberOf(encNum + "/" + fileName)) {
											  //if(thumbLocs.get(countMe).getKeywords().contains(word)){
					                        	  
					                            //String renderMe = word.getReadableName();
												//List<Keyword> myWords = thumbLocs.get(countMe).getKeywords();
												//int myWordsSize=myWords.size();
					                            for (int kwIter = 0; kwIter<myWordsSize; kwIter++) {
					                              //String kwParam = keywords[kwIter];
					                              //if (kwParam.equals(word.getIndexname())) {
					                              //  renderMe = "<strong>" + renderMe + "</strong>";
					                              //}
					                      		 	%>
					 								<br/><%= ("<strong>" + myWords.get(kwIter).getReadableName() + "</strong>")%>
					 								<%
					                            }




					                          //    }
					                       // } 

                          %>
										</span></td>
</tr>
<%

            }
%>
</table>

<%

      countMe++;
    } //end if
  } //endFor
%>
</div>

</td>
</tr>
<%



} catch (Exception e) {
  e.printStackTrace();
%>
<tr>
  <td>
    <p><%=props.getProperty("error")%>
    </p>.
  </td>
</tr>
<%
  }
%>

</table>
</div>
<%
} else {
%>

<p><%=props.getProperty("noImages")%></p>

<%
  }
%>

</table>
<!-- end thumbnail gallery -->



<br/>



<%

  if (isOwner) {
%>
<br />


<br />
<p><img align="absmiddle" src="images/Crystal_Clear_app_kaddressbook.gif"> <strong><%=props.getProperty("researcherComments") %>
</strong>: </p>

<div style="text-align:left;border:1px solid black;width:100%;height:400px;overflow-y:scroll;overflow-x:scroll;">

<p><%=sharky.getComments().replaceAll("\n", "<br>")%>
</p>
</div>
<%
  if (CommonConfiguration.isCatalogEditable()) {
%>
<p>

<form action="OccurrenceAddComment" method="post" name="addComments">
  <input name="user" type="hidden" value="<%=request.getRemoteUser()%>" id="user">
  <input name="number" type="hidden" value="<%=sharky.getOccurrenceID()%>" id="number">
  <input name="action" type="hidden" value="comments" id="action">

  <p><textarea name="comments" cols="60" id="comments"></textarea> <br>
    <input name="Submit" type="submit" value="<%=props.getProperty("addComments") %>">
</form>
</p>
<%
    } //if isEditable


  } //if isOwner
%>


</p>


</td>
</tr>


</table>

</td>
</tr>
</table>
</div><!-- end maintext -->
</div><!-- end main-wide -->


<br />
<table>
<tr>
<td>

      <jsp:include page="individualMapEmbed.jsp" flush="true">
        <jsp:param name="occurrence_number" value="<%=name%>"/>
      </jsp:include>
</td>
</tr>
</table>
<%

} 

//could not find the specified individual!
else {



%>


<p><%=props.getProperty("matchingRecord") %>:
<br /><strong><%=request.getParameter("number")%>
</strong><br/><br />
  <%=props.getProperty("tryAgain") %>
</p>


<%
      }
	  %>
      </td>
</tr>
</table>
</div><!-- end maintext -->
<jsp:include page="footer.jsp" flush="true"/>
</div><!-- end main-wide -->
      
      <%
    
  } catch (Exception eSharks_jsp) {
    System.out.println("Caught and handled an exception in individuals.jsp!");
    eSharks_jsp.printStackTrace();
  }



  myShepherd.rollbackDBTransaction();
  myShepherd.closeDBTransaction();

%>

</div>

</div>

<!-- end page -->
<!--end wrapper -->
</body>
</html>

