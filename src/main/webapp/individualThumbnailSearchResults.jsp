<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.servlet.ServletUtilities,org.ecocean.*,java.io.File,java.io.FileInputStream, java.util.*, org.ecocean.security.Collaboration" %>


  <%
  String context="context0";
  context=ServletUtilities.getContext(request);
	List<Collaboration> collabs = Collaboration.collaborationsForCurrentUser(request);
  //setup data dir
  String rootWebappPath = getServletContext().getRealPath("/");
  File webappsDir = new File(rootWebappPath).getParentFile();
  File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));
  //if(!shepherdDataDir.exists()){shepherdDataDir.mkdirs();}
  File encountersDir=new File(shepherdDataDir.getAbsolutePath()+"/encounters");
  //if(!encountersDir.exists()){encountersDir.mkdirs();}
  
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
    //String langCode = "en";
    String langCode=ServletUtilities.getLanguageCode(request);
    

    Properties encprops = new Properties();
    //encprops.load(getClass().getResourceAsStream("/bundles/" + langCode + "/individualThumbnailSearchResults.properties"));
    encprops = ShepherdProperties.getProperties("individualThumbnailSearchResults.properties", langCode,context);


    Shepherd myShepherd = new Shepherd(context);
    myShepherd.setAction("individualThumbnailSearchResults.jsp");

    //Iterator allIndividuals;
    Vector<MarkedIndividual> rIndividuals = new Vector<MarkedIndividual>();

    myShepherd.beginDBTransaction();

    MarkedIndividualQueryResult queryResult = IndividualQueryProcessor.processQuery(myShepherd, request, "individualID ascending");
    rIndividuals = queryResult.getResult();

    String[] keywords = request.getParameterValues("keyword");
    if (keywords == null) {
      keywords = new String[0];
    }

    //int numThumbnails = myShepherd.getNumMarkedIndividualThumbnails(rIndividuals.iterator(), keywords);
	int numThumbnails=0;
	List<SinglePhotoVideo> thumbLocs=new ArrayList<SinglePhotoVideo>();
	thumbLocs=myShepherd.getMarkedIndividualThumbnails(request, rIndividuals.iterator(), startNum, endNum, keywords);
	
    
    String queryString = "";
    if (request.getQueryString() != null) {
      queryString = request.getQueryString();
    }

  %>
 
 
 
   <jsp:include page="header.jsp" flush="true"/>
 
 
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
  
  hs.height = 250;
  hs.align = 'auto';
	hs.anchor = 'top';

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

<style type="text/css">
  #tabmenu {
    color: #000;
    border-bottom: 1px solid #CDCDCD;
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
    color: #000;
    background: #E6EEEE;
    
    border: 1px solid #CDCDCD;
    padding: 2px 5px 0px 5px;
    margin: 0;
    text-decoration: none;
    border-bottom: 0px solid #FFFFFF;
  }

  #tabmenu a.active {
    background: #8DBDD8;
    color: #000000;
    border-bottom: 1px solid #8DBDD8;
  }

  #tabmenu a:hover {
    color: #000;
    background: #8DBDD8;
  }

  #tabmenu a:visited {
    
  }

  #tabmenu a.active:hover {
    color: #000;
    border-bottom: 1px solid #8DBDD8;
  }
  
  

  div.scroll {
    height: 200px;
    overflow: auto;
    border: 1px solid #666;
    background-color: #ccc;
    padding: 8px;
  }
</style>



<div class="container maincontent">


      <h1 class="intro">
        <%
          if (request.getParameter("noQuery") == null) {
	        %>
	        <%=encprops.getProperty("searchTitle")%>
	        <%
        	
          } 
        else {
        %>
        <%=encprops.getProperty("title")%>
        <%
          }
        %>
      </h1>



      <p><%=encprops.getProperty("belowMatches")%> <%=startNum%> - <%=endNum%>&nbsp; </p>



<%
  if (request.getParameter("noQuery") == null) {
%>
<ul id="tabmenu">


  <li><a href="individualSearchResults.jsp?<%=queryString.replaceAll("startNum","uselessNum").replaceAll("endNum","uselessNum") %>"><%=encprops.getProperty("table")%>
  </a></li>
  <li><a class="active"><%=encprops.getProperty("matchingImages")%>
  </a></li>
     <li><a href="individualMappedSearchResults.jsp?<%=queryString.replaceAll("startNum","uselessNum").replaceAll("endNum","uselessNum") %>"><%=encprops.getProperty("mappedResults")%>
  </a></li>
  <li><a href="individualSearchResultsAnalysis.jsp?<%=queryString.replaceAll("startNum","uselessNum").replaceAll("endNum","uselessNum") %>"><%=encprops.getProperty("analysis")%>
  </a></li>
    <li><a href="individualSearchResultsExport.jsp?<%=queryString.replaceAll("startNum","uselessNum").replaceAll("endNum","uselessNum") %>"><%=encprops.getProperty("export")%>
  </a></li>
</ul>
<%
  }
%>

<table width="810" border="0" cellspacing="0" cellpadding="0">
  <tr>
    <td>



      <p><%=encprops.getProperty("belowMatches")%> <%=startNum%> - <%=endNum%>&nbsp;
        <%
          if (request.getParameter("noQuery") == null) {
        %>
        <%=encprops.getProperty("thatMatchedSearch")%>
      </p>
      <%
      } else {
      %>
        <%=encprops.getProperty("thatMatched")%></p>
      <%
        }
      %>
    </td>
  </tr>
</table>

<%
  String qString = queryString;
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
        href="individualThumbnailSearchResults.jsp?<%=qString%>&startNum=<%=(startNum-45)%>&endNum=<%=(startNum-1)%>"><img
        src="images/Black_Arrow_left.png" width="28" height="28" border="0" align="absmiddle"
        title="<%=encprops.getProperty("seePreviousResults")%>"/></a> <a
        href="individualThumbnailSearchResults.jsp?<%=qString%>&startNum=<%=(startNum-45)%>&endNum=<%=(startNum-1)%>"><%=(startNum - 45)%>
        - <%=(startNum - 1)%>
      </a></p>
    </td>
    <%
      }
    %>
    <td align="right">
      <p><a
        href="individualThumbnailSearchResults.jsp?<%=qString%>&startNum=<%=(startNum+45)%>&endNum=<%=(endNum+45)%>"><%=(startNum + 45)%>
        - <%=(endNum + 45)%>
      </a> <a
        href="individualThumbnailSearchResults.jsp?<%=qString%>&startNum=<%=(startNum+45)%>&endNum=<%=(endNum+45)%>"><img
        src="images/Black_Arrow_right.png" border="0" align="absmiddle"
        title="<%=encprops.getProperty("seeNextResults")%>"/></a></p>
    </td>
  </tr>
</table>


<table id="results" border="0" width="100%">
    <%

			
			int countMe=0;
			//Vector thumbLocs=new Vector();
		
			
			try {
				//thumbLocs=myShepherd.getMarkedIndividualThumbnails(request, rIndividuals.iterator(), startNum, endNum, keywords);
				
				
				//now let's order these alphabetical by the highest keyword
				//Cascadia Research only! TBD--remove on release of Shepherd Project
				//Collections.sort(thumbLocs, (new ThumbnailKeywordComparator()));
				
				
				
			
					for(int rows=0;rows<15;rows++) {		%>

  <tr valign="top">

      <%
							for(int columns=0;columns<3;columns++){
								if(countMe<thumbLocs.size()) {
									Encounter thisEnc = myShepherd.getEncounter(thumbLocs.get(countMe).getCorrespondingEncounterNumber());
									boolean visible = thisEnc.canUserAccess(request);
									String encSubdir = thisEnc.subdir();

									String thumbLink="";
									boolean video=true;
									if(!myShepherd.isAcceptableVideoFile(thumbLocs.get(countMe).getFilename())){
										thumbLink=thumbLocs.get(countMe).getWebURL();
										video=false;
									}
									else{
										thumbLink="//"+CommonConfiguration.getURLLocation(request)+"/images/video.jpg";
										
									}
									String link=thumbLocs.get(countMe).getWebURL();
						
							%>

    <td>
      <table class="<%= (visible ? "" : " no-access") %>">
        <tr>
          <td valign="top">
<% if (visible) { %>
            <a href="<%=link%>" 
            	<%
            	if(!thumbLink.endsWith("video.jpg")){
            	%>
            class="highslide" onclick="return hs.expand(this)"
            <%
            }
            %>
>
<% } else { %><a><% } %>
            <img width="250px" height="*" class="lazyload" src="//<%=CommonConfiguration.getURLLocation(request) %>/cust/mantamatcher/img/individual_placeholder_image.jpg" data-src="<%=thumbLink%>" alt="photo" border="1" title="<%= (visible ? encprops.getProperty("clickEnlarge") : "") %>" /></a>

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
              <h3><%=(countMe + startNum) %>
              </h3>
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
                        //Encounter thisEnc = myShepherd.getEncounter(thumbLocs.get(countMe).getCorrespondingEncounterNumber());
                      %>
                      <tr>
                      <% 
                      if(!thumbLink.endsWith("video.jpg")){
                    	  
                      %>
                        <td><span class="caption"><em><%=(countMe + startNum) %>
                        </em></span></td>
                      </tr>
                      <tr>
                        <td><span
                          class="caption"><%=encprops.getProperty("location") %>: <%=thisEnc.getLocation() %></span>
                        </td>
                      </tr>
                      <tr>
                        <td><span
                          class="caption"><%=encprops.getProperty("locationID") %>: <%=thisEnc.getLocationID() %></span>
                        </td>
                      </tr>
                      <tr>
                        <td><span
                          class="caption"><%=encprops.getProperty("date") %>: <%=thisEnc.getDate() %></span>
                        </td>
                      </tr>

                      <tr>
                        <td><span class="caption"><%=encprops.getProperty("individualID") %>: <a
                          href="individuals.jsp?number=<%=thisEnc.getIndividualID() %>" target="_blank"><%=thisEnc.getIndividualID() %>
                        </a></span></td>
                      </tr>
                      
                      <%
      			if(CommonConfiguration.showProperty("showTaxonomy",context)){
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
                      %>
                      <tr>
                        <td><span class="caption"><%=encprops.getProperty("catalogNumber") %>: <a
                          href="encounters/encounter.jsp?number=<%=thisEnc.getCatalogNumber() %>" target="_blank"><%=thisEnc.getCatalogNumber() %>
                        </a></span></td>
                      </tr>
                      <%
                        if (thisEnc.getVerbatimEventDate() != null) {
                      %>
                      <tr>

                        <td><span
                          class="caption"><%=encprops.getProperty("verbatimEventDate") %>: <%=thisEnc.getVerbatimEventDate() %></span>
                        </td>
                      </tr>
                      <%
                        }
                        if (request.getParameter("keyword") != null) {
                      %>
                      <tr>
                        <td><span class="caption">
											<%=encprops.getProperty("matchingKeywords") %>
											<%
                      						List<Keyword> myWords = thumbLocs.get(countMe).getKeywords();
											if(myWords!=null){	
												int myWordsSize=myWords.size();
						                            for (int kwIter = 0; kwIter<myWordsSize; kwIter++) {
						                              %>
						 								<br/><%= ("<strong>" + myWords.get(kwIter).getReadableName() + "</strong>")%>
						 								<%
						                            }
											}    





                          %>
										</span></td>
                      </tr>
                      <%
                        }
                        }
                      %>

                    </table>
                    
                    <%
                    if(!thumbLink.endsWith("video.jpg")){
                    %>
                    <br/>


                    <%
								}
                      if (CommonConfiguration.showEXIFData(context)&&!thumbLink.endsWith("video.jpg")) {
                    %>

                 


                  </td>
                  <%
                    }
                  %>
                </tr>
              </table>
              <%
              
              %>
            </div>
</div>
</td>
</tr>


<tr>
  <td>
<%
	if (!visible) out.println("<div class=\"lock-right\">" + thisEnc.collaborationLockHtml(collabs) + "</div>");
%>
    <span class="caption"><%=encprops.getProperty("location") %>: <%=thisEnc.getLocation() %></span></td>
</tr>
<tr>
  <td><span
    class="caption"><%=encprops.getProperty("locationID") %>: <%=thisEnc.getLocationID() %></span>
  </td>
</tr>
<tr>
  <td><span class="caption"><%=encprops.getProperty("date") %>: <%=thisEnc.getDate() %></span></td>
</tr>
<tr>
  <td><span class="caption"><%=encprops.getProperty("individualID") %>: <a
    href="individuals.jsp?number=<%=thisEnc.getIndividualID() %>" target="_blank"><%=thisEnc.getIndividualID() %>
  </a></span></td>
</tr>
                      <%
      			if(CommonConfiguration.showProperty("showTaxonomy",context)){
      				if((thisEnc.getGenus()!=null)&&(thisEnc.getSpecificEpithet()!=null)){
      		      %>
                      <tr>
                      <td>
                        <span class="caption">
                      		<%=encprops.getProperty("taxonomy") %>: <em><%=(thisEnc.getGenus()+" "+thisEnc.getSpecificEpithet())%></em>
                      	</span>
                      </td>
                      </tr>
                     <%
                     }
		     }
                      %>
<tr>
  <td><span class="caption"><%=encprops.getProperty("catalogNumber") %>: <a
    href="encounters/encounter.jsp?number=<%=thisEnc.getCatalogNumber() %>" target="_blank"><%=thisEnc.getCatalogNumber() %>
  </a></span></td>
</tr>
<tr>
  <td><span class="caption">
											<%=encprops.getProperty("matchingKeywords") %>
											<%
												List<Keyword> myWords = thumbLocs.get(countMe).getKeywords();
												if(myWords!=null){		
													int myWordsSize=myWords.size();
							                            for (int kwIter = 0; kwIter<myWordsSize; kwIter++) {
							                          
							                      		 	%>
							 								<br/><%= ("<strong>" + myWords.get(kwIter).getReadableName() + "</strong>")%>
							 								<%
							                            }
												}




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
        href="individualThumbnailSearchResults.jsp?<%=qString%>&startNum=<%=(startNum-90)%>&endNum=<%=(startNum-46)%>"><img
        src="images/Black_Arrow_left.png" width="28" height="28" border="0" align="absmiddle"
        title="<%=encprops.getProperty("seePreviousResults")%>"/></a> <a
        href="individualThumbnailSearchResults.jsp?<%=qString%>&startNum=<%=(startNum-90)%>&endNum=<%=(startNum-46)%>"><%=(startNum - 90)%>
        - <%=(startNum - 46)%>
      </a></p>
    </td>
    <%
      }
    %>
    <td align="right">
      <p><a
        href="individualThumbnailSearchResults.jsp?<%=qString%>&startNum=<%=startNum%>&endNum=<%=endNum%>"><%=startNum%>
        - <%=endNum%>
      </a> <a
        href="individualThumbnailSearchResults.jsp?<%=qString%>&startNum=<%=startNum%>&endNum=<%=endNum%>"><img
        src="images/Black_Arrow_right.png" border="0" align="absmiddle"
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
        <%=queryResult.getQueryPrettyPrint().replaceAll("locationField", encprops.getProperty("location")).replaceAll("locationCodeField", encprops.getProperty("locationID")).replaceAll("verbatimEventDateField", encprops.getProperty("verbatimEventDate")).replaceAll("alternateIDField", encprops.getProperty("alternateID")).replaceAll("behaviorField", encprops.getProperty("behavior")).replaceAll("Sex", encprops.getProperty("sex")).replaceAll("nameField", encprops.getProperty("nameField")).replaceAll("selectLength", encprops.getProperty("selectLength")).replaceAll("numResights", encprops.getProperty("numResights")).replaceAll("vesselField", encprops.getProperty("vesselField")).replaceAll("alternateIDField", (encprops.getProperty("alternateID"))).replaceAll("alternateIDField", (encprops.getProperty("size")))%>
      </p>

      <p class="caption"><strong><%=encprops.getProperty("jdoql")%>
      </strong><br/>
        <%=queryResult.getJDOQLRepresentation()%>
      </p>

    </td>
  </tr>
</table>
<%
  }
%>

</div>

<jsp:include page="footer.jsp" flush="true"/>


