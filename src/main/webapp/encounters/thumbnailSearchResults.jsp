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
         import="org.ecocean.servlet.ServletUtilities,javax.jdo.Query,com.drew.imaging.jpeg.JpegMetadataReader,com.drew.metadata.Directory,com.drew.metadata.Metadata, com.drew.metadata.Tag,org.ecocean.*,java.io.File, java.util.*" %>

<html>
<head>


  <%
  
  String context="context0";
  context=ServletUtilities.getContext(request);
  
  //setup data dir
  String rootWebappPath = getServletContext().getRealPath("/");
  File webappsDir = new File(rootWebappPath).getParentFile();
  File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));
  //if(!shepherdDataDir.exists()){shepherdDataDir.mkdir();}
  File encountersDir=new File(shepherdDataDir.getAbsolutePath()+"/encounters");
  //if(!encountersDir.exists()){encountersDir.mkdir();}

  
    int startNum = 1;
    int endNum = 45;

    try {

      if (request.getParameter("startNum") != null) {
        startNum = (new Integer(request.getParameter("startNum"))).intValue();
      }
      if (request.getParameter("endNum") != null) {
        endNum = (new Integer(request.getParameter("endNum"))).intValue();
      }

    } catch (NumberFormatException nfe) {
      startNum = 1;
      endNum = 45;
    }

//let's load thumbnailSearch.properties
    String langCode = "en";
    if (session.getAttribute("langCode") != null) {
      langCode = (String) session.getAttribute("langCode");
    }

    Properties encprops = new Properties();
    //encprops.load(getClass().getResourceAsStream("/bundles/" + langCode + "/thumbnailSearchResults.properties"));
    encprops = ShepherdProperties.getProperties("thumbnailSearchResults.properties", langCode);

    Shepherd myShepherd = new Shepherd(context);

    ArrayList<SinglePhotoVideo> rEncounters = new ArrayList<SinglePhotoVideo>();

    myShepherd.beginDBTransaction();
    EncounterQueryResult queryResult = new EncounterQueryResult(new Vector<Encounter>(), "", "");
	
  	StringBuffer prettyPrint=new StringBuffer("");
  	Map<String,Object> paramMap = new HashMap<String, Object>();

  	/**
  	String filter="";
    if (request.getParameter("noQuery") == null) {
    	filter="SELECT from org.ecocean.SinglePhotoVideo WHERE ("+EncounterQueryProcessor.queryStringBuilder(request, prettyPrint, paramMap).replaceAll("SELECT FROM", "SELECT DISTINCT catalogNumber FROM")+").contains(this.correspondingEncounterNumber)";
    } 
    else {

		filter="SELECT from org.ecocean.SinglePhotoVideo";
    	
    }
    */
    
    String[] keywords = request.getParameterValues("keyword");
    if (keywords == null) {
      keywords = new String[0];
    }

    if (request.getParameter("noQuery") == null) {
	  queryResult = EncounterQueryProcessor.processQuery(myShepherd, request, "year descending, month descending, day descending");
	
    rEncounters=myShepherd.getThumbnails(request, queryResult.getResult().iterator(), startNum, endNum, keywords);
    }
    else{
    	Query allQuery=myShepherd.getPM().newQuery("SELECT from org.ecocean.SinglePhotoVideo WHERE correspondingEncounterNumber != null");    	
    	allQuery.setRange(startNum, endNum);
    	rEncounters=new ArrayList<SinglePhotoVideo>((Collection<SinglePhotoVideo>)allQuery.execute());
   }

  %>
  <title><%=CommonConfiguration.getHTMLTitle(context) %>
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


  <!--
    1 ) Reference to the files containing the JavaScript and CSS.
    These files must be located on your server.
  -->

  <script type="text/javascript" src="../highslide/highslide/highslide-with-gallery.js"></script>
  <link rel="stylesheet" type="text/css" href="../highslide/highslide/highslide.css"/>

  <!--
    2) Optionally override the settings defined at the top
    of the highslide.js file. The parameter hs.graphicsDir is important!
  -->

  <script type="text/javascript">
  hs.graphicsDir = '../highslide/highslide/graphics/';
  hs.align = 'center';
  hs.showCredits = false;

  //transition behavior
  hs.transitions = ['expand', 'crossfade'];
  hs.outlineType = 'rounded-white';
  hs.fadeInOut = true;
  hs.transitionDuration = 0;
  hs.expandDuration = 0;
  hs.restoreDuration = 0;
  hs.numberOfImagesToPreload = 15;
  hs.dimmingDuration = 0;

  // define the restraining box
  hs.useBox = true;
  hs.width = 810;
  hs.height=500;

    //block right-click user copying if no permissions available
    <%
    if(request.getUserPrincipal()==null){
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
</head>
<style type="text/css">
  #tabmenu {
    color: #000;
    border-bottom: 2px solid black;
    margin: 12px 0px 0px 0px;
    padding: 0px;
    z-index: 1;
    padding-left: 10px
  }

  #tabmenu li {
    display: inline;
    overflow: hidden;
    list-style-type: none;
  }

  #tabmenu a, a.active {
    color: #DEDECF;
    background: #000;
    font: bold 1em "Trebuchet MS", Arial, sans-serif;
    border: 2px solid black;
    padding: 2px 5px 0px 5px;
    margin: 0;
    text-decoration: none;
    border-bottom: 0px solid #FFFFFF;
  }

  #tabmenu a.active {
    background: #FFFFFF;
    color: #000000;
    border-bottom: 2px solid #FFFFFF;
  }

  #tabmenu a:hover {
    color: #ffffff;
    background: #7484ad;
  }

  #tabmenu a:visited {
    color: #E8E9BE;
  }

  #tabmenu a.active:hover {
    background: #7484ad;
    color: #DEDECF;
    border-bottom: 2px solid #000000;
  }

  div.scroll {
    height: 200px;
    overflow: auto;
    border: 1px solid #666;
    background-color: #ccc;
    padding: 8px;
  }
</style>
<body>
<div id="wrapper">
<div id="page">
<jsp:include page="../header.jsp" flush="true">
  <jsp:param name="isAdmin" value="<%=request.isUserInRole(\"admin\")%>" />
</jsp:include>
<div id="main">

<%
  String rq = "";
  if (request.getQueryString() != null) {
    rq = request.getQueryString();
  }
  if (request.getParameter("noQuery") == null) {
%>

<ul id="tabmenu">

  <li><a
    href="searchResults.jsp?<%=rq.replaceAll("startNum","uselessNum").replaceAll("endNum","uselessNum") %>"><%=encprops.getProperty("table")%>
  </a></li>
  <li><a class="active"><%=encprops.getProperty("matchingImages")%>
  </a></li>
  <li><a
    href="mappedSearchResults.jsp?<%=rq.replaceAll("startNum","uselessNum").replaceAll("endNum","uselessNum") %>"><%=encprops.getProperty("mappedResults")%>
  </a></li>
  <li><a
    href="../xcalendar/calendar2.jsp?<%=rq.replaceAll("startNum","uselessNum").replaceAll("endNum","uselessNum") %>"><%=encprops.getProperty("resultsCalendar")%>
  </a></li>
        <li><a
     href="searchResultsAnalysis.jsp?<%=request.getQueryString() %>"><%=encprops.getProperty("analysis")%>
   </a></li>
 <li><a
     href="exportSearchResults.jsp?<%=request.getQueryString() %>"><%=encprops.getProperty("export")%>
   </a></li>
</ul>
<%
  }
%>

<table width="810" border="0" cellspacing="0" cellpadding="0">
  <tr>
    <td>
      <p>

      <h1 class="intro"><%=encprops.getProperty("title")%>
      </h1>
      </p>


      <p><%=encprops.getProperty("belowMatches")%> <%=startNum%>
        - <%=endNum%> <%=encprops.getProperty("thatMatched")%>
      </p>
    </td>
  </tr>
</table>

<%
  String qString = rq;
  int startNumIndex = qString.indexOf("&startNum");
  if (startNumIndex > -1) {
    qString = qString.substring(0, startNumIndex);
  }

%>
<table width="810px">
  <tr>
    <%
      if ((startNum) > 1) {%>
    <td align="left">
      <p><a
        href="thumbnailSearchResults.jsp?<%=qString%>&startNum=<%=(startNum-45)%>&endNum=<%=(startNum-1)%>"><img
        src="../images/Black_Arrow_left.png" width="28" height="28" border="0" align="absmiddle"
        title="<%=encprops.getProperty("seePreviousResults")%>"/></a> <a
        href="thumbnailSearchResults.jsp?<%=qString%>&startNum=<%=(startNum-45)%>&endNum=<%=(startNum-1)%>"><%=(startNum - 45)%>
        - <%=(startNum - 1)%>
      </a></p>
    </td>
    <%
      }
    %>
    <td align="right">
      <p><a
        href="thumbnailSearchResults.jsp?<%=qString%>&startNum=<%=(startNum+45)%>&endNum=<%=(endNum+45)%>"><%=(startNum + 45)%>
        - <%=(endNum + 45)%>
      </a> <a
        href="thumbnailSearchResults.jsp?<%=qString%>&startNum=<%=(startNum+45)%>&endNum=<%=(endNum+45)%>"><img
        src="../images/Black_Arrow_right.png" border="0" align="absmiddle"
        title="<%=encprops.getProperty("seeNextResults")%>"/></a></p>
    </td>
  </tr>
</table>


<table id="results" border="0" width="100%">
    <%

			
			int countMe=0;
			ArrayList<SinglePhotoVideo> thumbLocs=new ArrayList<SinglePhotoVideo>();
			
			try {
				//thumbLocs=myShepherd.getThumbnails(request, rEncounters.iterator(), startNum, endNum, keywords);
				thumbLocs=rEncounters;	
				//System.out.println("thumLocs.size="+thumbLocs.size());
					for(int rows=0;rows<15;rows++) {		%>

  <tr valign="top">

      <%
							for(int columns=0;columns<3;columns++){
								if(countMe<thumbLocs.size()) {
									String thumbLink="";
									boolean video=true;
									if(!myShepherd.isAcceptableVideoFile(thumbLocs.get(countMe).getFilename())){
										thumbLink="/"+CommonConfiguration.getDataDirectoryName(context)+"/encounters/"+thumbLocs.get(countMe).getCorrespondingEncounterNumber()+"/"+thumbLocs.get(countMe).getDataCollectionEventID()+".jpg";
										video=false;
									}
									else{
										thumbLink="http://"+CommonConfiguration.getURLLocation(request)+"/images/video.jpg";
										
									}
									String link="/"+CommonConfiguration.getDataDirectoryName(context)+"/encounters/"+thumbLocs.get(countMe).getCorrespondingEncounterNumber()+"/"+thumbLocs.get(countMe).getFilename();
						
							%>

    <td>
      <table>
        <tr>
          <td valign="top">
            <a href="<%=link%>" 
            
            <%
            if(!thumbLink.endsWith("video.jpg")){
            %>
            class="highslide" onclick="return hs.expand(this)"
            <%
            }
            %>
            
            >
            <img src="<%=thumbLink%>" alt="photo" border="1" title="Click to enlarge"/></a>

            <div 
            <%
            if(!thumbLink.endsWith("video.jpg")){
            %>
            class="highslide-caption"
            <%
            }
            %>
            >
			<%
            if(!thumbLink.endsWith("video.jpg")){
            	%>
              <h3><%=(countMe + startNum) %></h3>
            <%
            }
            %>
              <%
                if ((request.getParameter("referenceImageName") != null)&&(!thumbLink.endsWith("video.jpg"))) {
              %>
              <h4>Reference Image</h4>
              <table id="table<%=(countMe+startNum) %>">
                <tr>
                  <td>

                    <img width="790px" 
                    
                    <%
            		if(!thumbLink.endsWith("video.jpg")){
            		%>
                    class="highslide-image"
                    <%
            		}
                    %>
                    
                    id="refImage<%=(countMe+startNum) %>"
                         src="/<%=CommonConfiguration.getDataDirectoryName(context) %>/encounters/<%=request.getParameter("referenceImageName") %>"/>

                  </td>
                </tr>
              </table>
              <%
                }
              %>
              
              <%
            	if(!thumbLink.endsWith("video.jpg")){
            	%>
              <h4><%=encprops.getProperty("imageMetadata") %>
              </h4>
              <%
            	}
              %>

 				
              <table>
                <tr>
                  <td align="left" valign="top">



                    <table>
                      <%

                        int kwLength = keywords.length;
                        Encounter thisEnc = myShepherd.getEncounter(thumbLocs.get(countMe).getCorrespondingEncounterNumber());
                      %>
                      
                      	<%
            	if(!thumbLink.endsWith("video.jpg")){
            	%>
                      <tr>
                        <td><span class="caption"><em><%=(countMe + startNum) %>
                        </em></span></td>
                      </tr>
                      <tr>
                        <td>
                        	<span class="caption"><%=encprops.getProperty("location") %>: 
                          			<%
  			try{
  				if(thisEnc.getLocation()!=null){
  				%>
  				<em><%=thisEnc.getLocation() %></em>
  				<%
  				}
  			}
  			catch(Exception e){}
  			%>
                          	</span>
                        </td>
                      </tr>
                      <tr>
                        <td><span
                          class="caption"><%=encprops.getProperty("locationID") %>: 
                          <%
  			try{
  				if(thisEnc.getLocationID()!=null){
  				%>
  				<em><%=thisEnc.getLocationID() %></em>
  				<%
  				}
  			}
  			catch(Exception e){}
  			%>
                          </span>
                        </td>
                      </tr>
                      <tr>
                        <td><span class="caption">
                        	<%=encprops.getProperty("date") %>: 
                        	<%
                        	try{
                        		if(thisEnc.getDate()!=null){
                        		%>
                        			<%=thisEnc.getDate() %>
                        		<%
                        		}
                        	}
                        	catch(Exception e){}
                        	%>
                          </span>
                        </td>
                      </tr>
                      <tr>
                        <td><span class="caption"><%=encprops.getProperty("individualID") %>: 
                        	<%
                        	try{
                        	if((thisEnc.getIndividualID()!=null)&&(!thisEnc.getIndividualID().equals("Unassigned"))){
                        	%>
                        	<a href="../individuals.jsp?number=<%=thisEnc.getIndividualID() %>">
                        	
                        	<%=thisEnc.getIndividualID() %>
                        	
                        	</a>
                        	<%
                        	}
                        	}
                        catch(Exception e){}
                        	%>
                        	
                        </span></td>
                      </tr>
                        <%
		            			if(CommonConfiguration.showProperty("showTaxonomy",context)){
		            				%>
		                            <tr>
		                            <td>
		                              <span class="caption">
		                            		<em><%=encprops.getProperty("taxonomy") %>: 
		                            		<%
		                            		try{
		                            		if((thisEnc.getGenus()!=null)&&(thisEnc.getSpecificEpithet()!=null)){
		      		            		      
		                            		%>
		                            		<%=(thisEnc.getGenus()+" "+thisEnc.getSpecificEpithet())%>
		                            		<%
		                            		}
		                            		}
		                            		catch(Exception e){}
		                            		%>
		                            		</em>
		                            	</span>
		                            </td>
		                            </tr>
		                           <%
		            			
		      		     }
                      %>
                      <tr>
                        <td><span class="caption"><%=encprops.getProperty("catalogNumber") %>: 
                        <%
                        try{
                        if(thisEnc.getCatalogNumber()!=null){
                        %>
                        <a href="encounter.jsp?number=<%=thisEnc.getCatalogNumber() %>">
                          <%=thisEnc.getCatalogNumber() %>
                        </a>
                        <%
                        }
                        }
                        catch(Exception e){}
                        %>
                        </span></td>
                      </tr>
                      <%
                      try{
                        if (thisEnc.getVerbatimEventDate() != null) {
                      %>
                      <tr>

                        <td><span
                          class="caption"><%=encprops.getProperty("verbatimEventDate") %>: <%=thisEnc.getVerbatimEventDate() %></span>
                        </td>
                      </tr>
                      <%
                        
                        }
                      }
                      catch(Exception e){}

                        if (request.getParameter("keyword") != null) {
                      %>


                      <tr>
                        <td><span class="caption">
											<%=encprops.getProperty("matchingKeywords") %>
											<%
                        //Iterator allKeywords2 = myShepherd.getAllKeywords();
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
 								<br/><%=myWords.get(kwIter).getReadableName()%>
 								<%
                            }




                          //    }
                       // } 
                            }

                          %>
										</span></td>
                      </tr>
                      <%
                        }
                      %>

                    </table>
                    
                    	<%
            	if(!thumbLink.endsWith("video.jpg")){
            	%>
                    <br/>
                    <%
            	}
                    %>

                    <%
                      if (CommonConfiguration.showEXIFData(context)&&!thumbLink.endsWith("video.jpg")) {
                    %>
                    
                    <p><strong>EXIF Data</strong></p>
												
				   <span class="caption">
						<div class="scroll">
						<span class="caption">
					<%
            if ((thumbLocs.get(countMe).getFilename().toLowerCase().endsWith("jpg")) || (thumbLocs.get(countMe).getFilename().toLowerCase().endsWith("jpeg"))) {
              try{
              	File exifImage = new File(encountersDir.getAbsolutePath() + "/" + thisEnc.getCatalogNumber() + "/" + thumbLocs.get(countMe).getFilename());
              	if(exifImage.exists()){
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
              	} //end if
              	else{
            	 %>
		            <p>File not found on file system. No EXIF data available.</p>
          		<%  
              	}
              } //end try
              catch(Exception e){
              %>
              <p>Cannot read metadata for this file.</p>
              <%
              	e.printStackTrace();
              }
             }
                %>
   									</span>
            </div>
   								</span>


                  </td>
                  <%
                    }
                  %>
                </tr>
              </table>
            </div>
</div>
</td>
</tr>


<tr>
  <td>
  		<span class="caption">
  			<%=encprops.getProperty("location") %>: 
  			<%
  			try{
  				if(thisEnc.getLocation()!=null){
  				%>
  				<em><%=thisEnc.getLocation() %></em>
  				<%
  				}
  			}
  			catch(Exception e){}
  			%>
  		</span>
  </td>
</tr>
<tr>
  <td><span
    class="caption"><%=encprops.getProperty("locationID") %>: 
    <%
  			try{
  				if(thisEnc.getLocationID()!=null){
  				%>
  				<em><%=thisEnc.getLocationID() %></em>
  				<%
  				}
  			}
  			catch(Exception e){}
  			%>
    </span>
  </td>
</tr>
<tr>
  <td>
  	<span class="caption"><%=encprops.getProperty("date") %>: 
  		<%
        try{
  			if(thisEnc.getDate()!=null){
                        	%>
                        		<%=thisEnc.getDate() %>
                        	<%
                        	}
        }
  	catch(Exception e){}
                        	%>
  	</span>
  </td>
</tr>
<tr>
  <td><span class="caption"><%=encprops.getProperty("individualID") %>: 
      						<%
      						try{
                        	if((thisEnc.getIndividualID()!=null)&&(!thisEnc.getIndividualID().equals("Unassigned"))){
                        	%>
                        	<a href="../individuals.jsp?number=<%=thisEnc.getIndividualID() %>">
                        	
                        	<%=thisEnc.getIndividualID() %>
                        	
                        	</a>
                        	<%
            				}
      						}
  							catch(Exception e){}
                        	%>
  </span></td>
</tr>
 <%
		            			if(CommonConfiguration.showProperty("showTaxonomy",context)){
		            				try{
		            				if((thisEnc.getGenus()!=null)&&(thisEnc.getSpecificEpithet()!=null)){
		            		      %>
		                            <tr>
		                            <td>
		                              <span class="caption">
		                            		<em><%=encprops.getProperty("taxonomy") %>: <%=(thisEnc.getGenus()+" "+thisEnc.getSpecificEpithet())%></em>
		                            	</span>
		                            </td>
		                            </tr>
		                           <%
		                           }
		            				}
		            				catch(Exception e){}
		      		     }
                      %>
<tr>
  <td><span class="caption"><%=encprops.getProperty("catalogNumber") %>: 
  <%
  try{
  if(thisEnc.getCatalogNumber()!=null){
  %>
  <a href="encounter.jsp?number=<%=thisEnc.getCatalogNumber() %>"><%=thisEnc.getCatalogNumber() %>
  </a>
  <%
  }
  }
  catch(Exception e){}
  %>
  </span></td>
</tr>
<tr>
  <td><span class="caption">
											<%=encprops.getProperty("matchingKeywords") %>
											<%
                        //int numKeywords=myShepherd.getNumKeywords();
									          //Iterator allKeywords2 = myShepherd.getAllKeywords();
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
					 								<br/><%=myWords.get(kwIter).getReadableName() %>
					 								<%
					                            }




					                          //    }
					                       // } 

                          %>
										</span></td>
</tr>

</table>
</td>
<%

      countMe++;
    } //end if
  } //endFor
%>
</tr>
<%
  } //endFor

} catch (Exception e) {
  e.printStackTrace();
%>
<tr>
  <td>
    <p><%=encprops.getProperty("error")%>
    </p>.</p>
  </td>
</tr>
<%
  }
%>

</table>

<%


  startNum = startNum + 45;
  endNum = endNum + 45;

%>

<table width="810px">
  <tr>
    <%
      if ((startNum - 45) > 1) {%>
    <td align="left">
      <p><a
        href="thumbnailSearchResults.jsp?<%=qString%>&startNum=<%=(startNum-90)%>&endNum=<%=(startNum-46)%>"><img
        src="../images/Black_Arrow_left.png" width="28" height="28" border="0" align="absmiddle"
        title="<%=encprops.getProperty("seePreviousResults")%>"/></a> <a
        href="thumbnailSearchResults.jsp?<%=qString%>&startNum=<%=(startNum-90)%>&endNum=<%=(startNum-46)%>"><%=(startNum - 90)%>
        - <%=(startNum - 46)%>
      </a></p>
    </td>
    <%
      }
    %>
    <td align="right">
      <p><a
        href="thumbnailSearchResults.jsp?<%=qString%>&startNum=<%=startNum%>&endNum=<%=endNum%>"><%=startNum%>
        - <%=endNum%>
      </a> <a
        href="thumbnailSearchResults.jsp?<%=qString%>&startNum=<%=startNum%>&endNum=<%=endNum%>"><img
        src="../images/Black_Arrow_right.png" border="0" align="absmiddle"
        title="<%=encprops.getProperty("seeNextResults")%>"/></a></p>
    </td>
  </tr>
</table>
<%
  myShepherd.rollbackDBTransaction();
  myShepherd.closeDBTransaction();

  if (request.getParameter("noQuery") == null) {
%>
<table>
  <tr>
    <td align="left">



      <p><strong><%=encprops.getProperty("queryDetails")%>
      </strong></p>

      <p class="caption"><strong><%=encprops.getProperty("prettyPrintResults") %>
      </strong><br/>
        <%=queryResult.getQueryPrettyPrint().replaceAll("locationField", encprops.getProperty("location")).replaceAll("locationCodeField", encprops.getProperty("locationID")).replaceAll("verbatimEventDateField", encprops.getProperty("verbatimEventDate")).replaceAll("alternateIDField", encprops.getProperty("alternateID")).replaceAll("behaviorField", encprops.getProperty("behavior")).replaceAll("Sex", encprops.getProperty("sex")).replaceAll("nameField", encprops.getProperty("nameField")).replaceAll("selectLength", encprops.getProperty("selectLength")).replaceAll("numResights", encprops.getProperty("numResights")).replaceAll("vesselField", encprops.getProperty("vesselField"))%>
      </p>
      


    </td>
  </tr>
</table>
<%
  }
%>

<br/>
<jsp:include page="../footer.jsp" flush="true"/>
</div>
</div>
<!-- end page --></div>
<!--end wrapper -->

</body>
</html>


