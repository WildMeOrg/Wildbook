<%@ page contentType="text/html; charset=utf-8" language="java"
     import="org.ecocean.*,
              org.ecocean.servlet.ServletUtilities,
              java.util.ArrayList,
              java.util.List,
              java.util.Map,
              java.util.Iterator,
              java.util.Properties,
              java.util.StringTokenizer,
              org.ecocean.cache.*
              "
%>



<jsp:include page="header.jsp" flush="true"/>

<%
String context=ServletUtilities.getContext(request);

//set up our Shepherd

Shepherd myShepherd=null;
myShepherd=new Shepherd(context);
myShepherd.setAction("index.jsp");


String langCode=ServletUtilities.getLanguageCode(request);

//check for and inject a default user 'tomcat' if none exists
// Make a properties object for lang support.
Properties props = new Properties();
// Grab the properties file with the correct language strings.
props = ShepherdProperties.getProperties("index.properties", langCode,context);


//check for and inject a default user 'tomcat' if none exists
if (!CommonConfiguration.isWildbookInitialized(myShepherd)) {
  System.out.println("WARNING: index.jsp has determined that CommonConfiguration.isWildbookInitialized()==false!");
  %>
    <script type="text/javascript">
      console.log("Wildbook is not initialized!");
    </script>
  <%
  StartupWildbook.initializeWildbook(request, myShepherd);
}






//let's quickly get the data we need from Shepherd

int numMarkedIndividuals=0;
int numEncounters=0;
int numDataContributors=0;
int numUsersWithRoles=0;
int numUsers=0;
myShepherd.beginDBTransaction();
QueryCache qc=QueryCacheFactory.getQueryCache(context);

//String url = "/react/login";
//response.sendRedirect(url);
//RequestDispatcher dispatcher = getServletContext().getRequestDispatcher(url);
//dispatcher.forward(request, response);


try{


    //numMarkedIndividuals=myShepherd.getNumMarkedIndividuals();
    numMarkedIndividuals=qc.getQueryByName("numMarkedIndividuals").executeCountQuery(myShepherd).intValue();
    numEncounters=myShepherd.getNumEncounters();
    //numEncounters=qc.getQueryByName("numEncounters").executeCountQuery(myShepherd).intValue();
    //numDataContributors=myShepherd.getAllUsernamesWithRoles().size();
    numDataContributors=qc.getQueryByName("numUsersWithRoles").executeCountQuery(myShepherd).intValue();
    numUsers=qc.getQueryByName("numUsers").executeCountQuery(myShepherd).intValue();
    numUsersWithRoles = numUsers-numDataContributors;


}
catch(Exception e){
    System.out.println("INFO: *** If you are seeing an exception here (via index.jsp) your likely need to setup QueryCache");
    System.out.println("      *** This entails configuring a directory via cache.properties and running appadmin/testQueryCache.jsp");
    e.printStackTrace();
}

%>

<style>




#fullScreenDiv{
    width:100%;
   /* Set the height to match that of the viewport. */
    
    width: auto;
    padding:0!important;
    margin: 0!important;
    position: relative;
}
#video{    
    width: 100vw; 
    height: auto;
    object-fit: cover;
    left: 0px;
    top: 0px;
    z-index: -1;
}

h2.vidcap {
	font-size: 2.4em;
	
	color: #fff;
	font-weight:300;
	text-shadow: 1px 2px 2px #333;
	margin-top: 35%;
}



/* The container for our text and stuff */
#messageBox{
    position: absolute;  top: 0;  left: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    width: 100%;
    height:100%;
}

@media screen and (min-width: 851px) {
	h2.vidcap {
	    font-size: 3.3em;
	    margin-top: -45%;
	}
}

@media screen and (max-width: 850px) and (min-width: 551px) {

	
	#fullScreenDiv{
	    width:100%;
	   /* Set the height to match that of the viewport. */
	    
	    width: auto;
	    padding-top:-10px!important;
	    margin: 0!important;
	    position: relative;
	}
	
	h2.vidcap {
	    font-size: 2.4em;
	    margin-top: 55%;
	}
	
}
@media screen and (max-width: 550px) {

	
	#fullScreenDiv{
	    width:100%;
	   /* Set the height to match that of the viewport. */
	    
	    width: auto;
	    padding-top:-10px!important;
	    margin: 0!important;
	    position: relative;
	}
	
	h2.vidcap {
	    font-size: 1.8em;
	    margin-top: 100%;
	}
	
}
 

</style>
<section style="padding-bottom: 0px;padding-top:0px;" class="container-fluid main-section relative videoDiv">

        
   <div id="fullScreenDiv">
        <div id="videoDiv">           
            <video playsinline preload id="video" autoplay muted>
            <source src="images/MS_humpback_compressed.webm#t=,3:05" type="video/webm"></source>
            <source src="images/MS_humpback_compressed.mp4#t=,3:05" type="video/mp4"></source>
            </video> 
        </div>
        <div id="messageBox"> 
            <div>
                <h2 class="vidcap"><%=props.getProperty("4cetaceanResearch") %></h2>

            </div>
        </div>   
    </div>

  


</section>

<section class="container text-center main-section">

	<h2 class="section-header"><%=props.getProperty("howItWorksH") %></h2>

  	<p class="lead"><%=props.getProperty("howItWorksHDescription") %></p>
  	
  	<h3 class="section-header"><%=props.getProperty("howItWorks1") %></h3>
  	<p class="lead"><%=props.getProperty("howItWorks1Description") %></p>
  	<img width="500px" height="*" style="max-width: 100%;" height="*" class="lazyload" src="cust/mantamatcher/img/individual_placeholder_image.jpg" data-src="images/detectionSpermWhale.jpg" />
		  	
  	
  	<h3 class="section-header"><%=props.getProperty("howItWorks2") %></h3>
  	<p class="lead"><%=props.getProperty("howItWorks2Description") %></p>
  	<img width="500px" height="*" style="max-width: 100%;" height="*" class="lazyload" src="cust/mantamatcher/img/individual_placeholder_image.jpg" data-src="images/CurvRank_matches.jpg" />
		
		
	<h3 class="section-header"><%=props.getProperty("howItWorks4") %></h3>
  	<p class="lead"><%=props.getProperty("howItWorks4Description") %></p>
  	<img width="500px" height="*" style="max-width: 100%;" height="*" class="lazyload" src="cust/mantamatcher/img/individual_placeholder_image.jpg" data-src="images/action.jpg" />
		
  	
  	<h2 class="section-header"><%=props.getProperty("howItWorks3") %></h2>
  	<p class="lead"><%=props.getProperty("howItWorks3Description") %></p>
  	
  	<div class="row">
  		<section class="col-xs-12 col-sm-6 col-md-4 col-lg-4 padding focusbox" height="500px">
		  	<div class="focusbox-inner opec">
		  	<img width="400px" style="max-width: 100%;" height="*" class="lazyload" src="cust/mantamatcher/img/individual_placeholder_image.jpg" data-src="images/hotspotter.jpg" />
		  	<em><%=props.getProperty("megapteraMatching") %></em>
	  	</section>
	  	
  		<section class="col-xs-12 col-sm-6 col-md-4 col-lg-4 padding focusbox" height="500px">
		  	<div class="focusbox-inner opec">
		  	<img width="400px" style="max-width: 100%;" height="*" class="lazyload" src="cust/mantamatcher/img/individual_placeholder_image.jpg" data-src="images/spermWhaleTrailingEdge.jpg" />
		  	<em><%=props.getProperty("physeterMatching") %></em>
	  	</section>
	  	
  		<section class="col-xs-12 col-sm-6 col-md-4 col-lg-4 padding focusbox">
		  	<div class="focusbox-inner opec">
		  	<img height="*" style="max-width: 100%;" width="400px" class="lazyload pull-left" src="cust/mantamatcher/img/individual_placeholder_image.jpg" data-src="images/tracedFin.jpg" />
		  	<div><em><%=props.getProperty("tursiopsMatching") %></em></div>
	  	</section>
	  	
  		<section class="col-xs-12 col-sm-6 col-md-4 col-lg-4 padding focusbox">
		  	<div class="focusbox-inner opec">
		  	<img width="400px" style="max-width: 100%;" height="*" class="lazyload" src="cust/mantamatcher/img/individual_placeholder_image.jpg" data-src="images/rightWHaleID.jpg" />
		  	<em><%=props.getProperty("eubalaenaMatching") %></em>
	  	</section>
	  	
  	</div>
  	
  	

  	<p class="lead"><%=props.getProperty("moreSoon") %></p>

</section>

<div class="container-fluid relative data-section">

    <aside class="container main-section">
        <div class="row">

            <!-- Random user profile to select -->
            <%
            //myShepherd.beginDBTransaction();
            try{
								User featuredUser=myShepherd.getRandomUserWithPhotoAndStatement();
            if(featuredUser!=null){
                String profilePhotoURL="images/user-profile-white-transparent.png";
                if(featuredUser.getUserImage()!=null){
                	profilePhotoURL="/"+CommonConfiguration.getDataDirectoryName(context)+"/users/"+featuredUser.getUsername()+"/"+featuredUser.getUserImage().getFilename();
                }

            %>
                <section class="col-xs-12 col-sm-6 col-md-4 col-lg-4 padding focusbox">
                    <div class="focusbox-inner opec">
                        <h2><%=props.getProperty("ourContributors") %></h2>
                        <div>
                            <img src="cust/mantamatcher/img/individual_placeholder_image.jpg" data-src="<%=profilePhotoURL %>" width="80px" height="*" alt="" class="pull-left lazyload" />
                            <p><%=featuredUser.getFullName() %>
                                <%
                                if(featuredUser.getAffiliation()!=null){
                                %>
                                <i><%=featuredUser.getAffiliation() %></i>
                                <%
                                }
                                %>
                            </p>
                            <p><%=featuredUser.getUserStatement() %></p>
                        </div>
                        <a href="whoAreWe.jsp" title="" class="cta"><%=props.getProperty("showContributors") %></a>
                    </div>
                </section>
            <%
            } // end if

            }
            catch(Exception e){e.printStackTrace();}
            finally{

            	//myShepherd.rollbackDBTransaction();
            }
            %>


            <section class="col-xs-12 col-sm-6 col-md-4 col-lg-4 padding focusbox">
                <div class="focusbox-inner opec">
                    <h2><%=props.getProperty("latestAnimalEncounters") %></h2>
                    <ul class="encounter-list list-unstyled">

                       <%
                       List<Encounter> latestIndividuals=myShepherd.getMostRecentIdentifiedEncountersByDate(3);
                       int numResults=latestIndividuals.size();
                       myShepherd.beginDBTransaction();
                       try{
	                       for(int i=0;i<numResults;i++){
	                           Encounter thisEnc=latestIndividuals.get(i);
	                           %>
	                            <li>
	                                <img src="cust/mantamatcher/img/manta-silhouette.png" alt="" width="85px" height="75px" class="pull-left" />
	                                <small>
	                                    <time>
	                                        <%=thisEnc.getDate() %>
	                                        <%
	                                        if((thisEnc.getLocationID()!=null)&&(!thisEnc.getLocationID().trim().equals(""))){
	                                        %>/ <%=thisEnc.getLocationID() %>
	                                        <%
	                                           }
	                                        %>
	                                    </time>
	                                </small>
	                                <p><a href="encounters/encounter.jsp?number=<%=thisEnc.getCatalogNumber() %>" title=""><%=thisEnc.getDisplayName() %></a></p>


	                            </li>
	                        <%
	                        }
						}
                       catch(Exception e){e.printStackTrace();}
                       finally{
                    	   myShepherd.rollbackDBTransaction();

                       }

                        %>

                    </ul>
                    <a href="/react/encounter-search?state=approved" title="" class="cta"><%=props.getProperty("seeMoreEncs") %></a>
                </div>
            </section>
            <section class="col-xs-12 col-sm-6 col-md-4 col-lg-4 padding focusbox">
                <div class="focusbox-inner opec">
                    <h2><%=props.getProperty("topSpotters")%></h2>
                    <ul class="encounter-list list-unstyled">
                    <%
                    //myShepherd.beginDBTransaction();
                    try{
	                    //System.out.println("Date in millis is:"+(new org.joda.time.DateTime()).getMillis());
                            long startTime = System.currentTimeMillis() - Long.valueOf(1000L*60L*60L*24L*30L);

	                    System.out.println("  I think my startTime is: "+startTime);

	                    Map<String,Integer> spotters = myShepherd.getTopUsersSubmittingEncountersSinceTimeInDescendingOrder(startTime);
	                    int numUsersToDisplay=3;
	                    if(spotters.size()<numUsersToDisplay){numUsersToDisplay=spotters.size();}
	                    Iterator<String> keys=spotters.keySet().iterator();
	                    Iterator<Integer> values=spotters.values().iterator();
	                    while((keys.hasNext())&&(numUsersToDisplay>0)){
	                          String spotter=keys.next();
	                          int numUserEncs=values.next().intValue();
	                          if(!spotter.equals("siowamteam") && !spotter.equals("admin") && !spotter.equals("tomcat") && myShepherd.getUser(spotter)!=null){
	                        	  String profilePhotoURL="images/user-profile-white-transparent.png";
	                              User thisUser=myShepherd.getUser(spotter);
	                              if(thisUser.getUserImage()!=null){
	                              	profilePhotoURL="/"+CommonConfiguration.getDataDirectoryName(context)+"/users/"+thisUser.getUsername()+"/"+thisUser.getUserImage().getFilename();
	                              }
	                              //System.out.println(spotters.values().toString());
	                            Integer myInt=spotters.get(spotter);
	                            //System.out.println(spotters);

	                          %>
	                                <li>
	                                    <img src="cust/mantamatcher/img/individual_placeholder_image.jpg" data-src="<%=profilePhotoURL %>" width="80px" height="*" alt="" class="pull-left lazyload" />
	                                    <%
	                                    if(thisUser.getAffiliation()!=null){
	                                    %>
	                                    <small><%=thisUser.getAffiliation() %></small>
	                                    <%
	                                      }
	                                    %>
	                                    <p><a href="#" title=""><%=spotter %></a>, <span><%=numUserEncs %> <%=props.getProperty("encounters") %><span></p>
	                                </li>

	                           <%
	                           numUsersToDisplay--;
	                    }
	                   } //end while
                    }
                    catch(Exception e){e.printStackTrace();}
                    //finally{myShepherd.rollbackDBTransaction();}

                   %>

                    </ul>
                    <a href="whoAreWe.jsp" title="" class="cta"><%=props.getProperty("allSpotters") %></a>
                </div>
            </section>
        </div>
    </aside>
</div>

<div class="container-fluid">
    <section class="container text-center  main-section">
       <div class="row">
            <section class="col-xs-12 col-sm-3 col-md-3 col-lg-3 padding">
                <p class="brand-primary"><i><span class="massive"><%=numMarkedIndividuals %></span> <%=props.getProperty("identifiedAnimals") %></i></p>
            </section>
            <section class="col-xs-12 col-sm-3 col-md-3 col-lg-3 padding">
                <p class="brand-primary"><i><span class="massive"><%=numEncounters %></span> <%=props.getProperty("reportedSightings") %></i></p>
            </section>
            <section class="col-xs-12 col-sm-3 col-md-3 col-lg-3 padding">

                <p class="brand-primary"><i><span class="massive"><%=numUsersWithRoles %></span> <%=props.getProperty("citizenScientists") %></i></p>
            </section>
            <section class="col-xs-12 col-sm-3 col-md-3 col-lg-3 padding">

                <p class="brand-primary"><i><span class="massive"><%=numDataContributors %></span> <%=props.getProperty("researchVolunteers") %></i></p>
            </section>
        </div>

        <hr/>

        <main class="container">
            <article class="text-center">
                <div class="row">
                    <img src="cust/mantamatcher/img/individual_placeholder_image.jpg" data-src="cust/mantamatcher/img/DSWP2015-20150408_081746a_Kopi.jpg" alt="" class="pull-left col-xs-7 col-sm-4 col-md-4 col-lg-4 col-xs-offset-2 col-sm-offset-1 col-md-offset-1 col-lg-offset-1 lazyload" />
                   
<div class="col-xs-12 col-sm-6 col-md-6 col-lg-6 text-left">
                        <h1><%=props.getProperty("whyWeDoThis") %></h1>
                        <p class="lead">
                            <i>"Sperm whales roam so vastly that no one research group can study them across their range. PhotoID as a tool for conservation and research finds power in numbers and international, inter-institutional collaboration. Flukebook enables us to do this easily."</i><br>- Shane Gero, <i>The Dominica Sperm Whale Project</i></p>
                        
                    </div>
                </div>
            </article>
        <main>

    </section>
</div>


<%
if((CommonConfiguration.getProperty("allowAdoptions", context)!=null)&&(CommonConfiguration.getProperty("allowAdoptions", context).equals("true"))){
%>
<div class="container-fluid">
    <section class="container main-section">

        <!-- Complete header for adoption section in index properties file -->
        <%=props.getProperty("adoptionHeader") %>
        <section class="adopt-section row">

            <!-- Complete text body for adoption section in index properties file -->
            <div class=" col-xs-12 col-sm-6 col-md-6 col-lg-6">
              <%=props.getProperty("adoptionBody") %>
            </div>
            <%
            myShepherd.beginDBTransaction();
            try{
	            Adoption adopt=myShepherd.getRandomAdoptionWithPhotoAndStatement();
	            if(adopt!=null){
	            %>
	            	<div class="adopter-badge focusbox col-xs-12 col-sm-6 col-md-6 col-lg-6">
		                <div class="focusbox-inner" style="overflow: hidden;">
		                	<%
		                    String profilePhotoURL="/"+CommonConfiguration.getDataDirectoryName(context)+"/adoptions/"+adopt.getID()+"/thumb.jpg";

		                	%>
		                    <img src="cust/mantamatcher/img/individual_placeholder_image.jpg" data-src="<%=profilePhotoURL %>" alt="" class="pull-right round lazyload">
		                    <h2><small>Meet an adopter:</small><%=adopt.getAdopterName() %></h2>
		                    <%
		                    if(adopt.getAdopterQuote()!=null){
		                    %>
			                    <blockquote>
			                        <%=adopt.getAdopterQuote() %>
			                    </blockquote>
		                    <%
		                    }
		                    %>
		                </div>
		            </div>

	            <%
				}
            }
            catch(Exception e){e.printStackTrace();}
            finally{myShepherd.rollbackDBTransaction();}

            %>


        </section>

        <hr/>
        <%= props.getProperty("donationText") %>
    </section>
</div>
<%
}
%>

<jsp:include page="footer.jsp" flush="true"/>


<%
myShepherd.rollbackDBTransaction();
myShepherd.closeDBTransaction();
myShepherd=null;
%>
