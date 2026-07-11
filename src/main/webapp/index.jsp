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
<%@ page import="org.ecocean.shepherd.core.Shepherd" %>
<%@ page import="org.ecocean.shepherd.core.ShepherdProperties" %>


<jsp:include page="header.jsp" flush="true"/>

<%
String context=ServletUtilities.getContext(request);

//set up our Shepherd

Shepherd myShepherd=new Shepherd(context);
myShepherd.setAction("index.jsp");

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

QueryCache qc=QueryCacheFactory.getQueryCache(context);

myShepherd.beginDBTransaction();
try{
    // Landing-page counts, cached application-wide for 10 minutes.
    // Encounter/individual counts come from OpenSearch _count (~1ms, no Postgres,
    // no DataNucleus object materialization - the old per-request full-table
    // .size() serialized every request thread on the metadata monitor under
    // concurrent homepage hits and could freeze the whole JVM). User/role counts
    // stay as DB-side COUNT queries: tiny tables, not indexed in OpenSearch.
    // On refresh failure the previous cached values keep serving.
    Long countsAt = (Long)application.getAttribute("wbLandingCountsAt");
    if ((countsAt == null) || ((System.currentTimeMillis() - countsAt.longValue()) > 600000L)) {
        try {
            org.ecocean.OpenSearch os = new org.ecocean.OpenSearch();
            int osNumEnc = os.queryCount("encounter", new org.json.JSONObject(
                "{\"query\":{\"bool\":{\"must_not\":[{\"term\":{\"state\":\"unidentifiable\"}}]}}}"));
            int osNumIndiv = os.queryCount("individual", new org.json.JSONObject(
                "{\"query\":{\"match_all\":{}}}"));
            javax.jdo.Query cq;
            cq = myShepherd.getPM().newQuery("SELECT count(distinct username) FROM org.ecocean.Role");
            int dbNumContributors = ((Long)cq.execute()).intValue();
            cq.closeAll();
            cq = myShepherd.getPM().newQuery("SELECT count(this) FROM org.ecocean.User");
            int dbNumUsers = ((Long)cq.execute()).intValue();
            cq.closeAll();
            if ((osNumEnc >= 0) && (osNumIndiv >= 0)) {
                application.setAttribute("wbNumEncounters", Integer.valueOf(osNumEnc));
                application.setAttribute("wbNumMarkedIndividuals", Integer.valueOf(osNumIndiv));
                application.setAttribute("wbNumDataContributors", Integer.valueOf(dbNumContributors));
                application.setAttribute("wbNumUsers", Integer.valueOf(dbNumUsers));
                // timestamp LAST: presence implies all four values are present
                application.setAttribute("wbLandingCountsAt", Long.valueOf(System.currentTimeMillis()));
            }
        } catch (Exception statsEx) {
            System.out.println("index.jsp: landing counts refresh failed, serving cached: " + statsEx);
        }
    }
    Integer wbCached;
    wbCached = (Integer)application.getAttribute("wbNumEncounters");
    numEncounters = (wbCached == null) ? 0 : wbCached.intValue();
    wbCached = (Integer)application.getAttribute("wbNumMarkedIndividuals");
    numMarkedIndividuals = (wbCached == null) ? 0 : wbCached.intValue();
    wbCached = (Integer)application.getAttribute("wbNumDataContributors");
    numDataContributors = (wbCached == null) ? 0 : wbCached.intValue();
    wbCached = (Integer)application.getAttribute("wbNumUsers");
    numUsers = (wbCached == null) ? 0 : wbCached.intValue();
    numUsersWithRoles = numUsers-numDataContributors;
}
catch(Exception e){
    e.printStackTrace();
}
finally{
	//myShepherd.rollbackDBTransaction();
	//myShepherd.closeDBTransaction();
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
    height: 50vw;
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
	margin-top: 55%;
}



/* The container for our text and stuff */
#messageBox{
    position: absolute;  top: -150px;  left: -30px;
    display: flex;
    align-items: center;
    justify-content: center;
    width: 100%;
    height:100%;
}





@media screen and (min-width: 1350px) {
	h2.vidcap {
	    font-size: 3.3em;
	    margin-top: -35%; 
	}
	

}

@media screen and (min-width: 1350px) {
	#messageBox div{
	    margin-left: -15%;
	    margin-top: 15%;
	    padding-left: 15px;
	    padding-top: 15px;
	}
}

@media screen and (max-width: 1000px) and (min-width: 501px) {
	#messageBox div{


	    padding-left: 15px;
	    padding-top: 210px;
	}
	
	h2.vidcap {
		font-size: 1.2em;
		margin-top: 70%;
	}
	
	#messageBox div button.large{
		font-size: 1.0em;
	}
	
	#fullScreenDiv{
	    width:100%;
	   /* Set the height to match that of the viewport. */
	    
	    width: auto;
	    padding-top:50px!important;
	    margin: 0!important;
	    position: relative;
	}
	
}

@media screen and (max-width: 500px) {
	#messageBox div{


	    padding-left: 15px;
	    padding-top: 250px;
	}
	
	h2.vidcap {
		font-size: 1.0em;
		margin-top: 85%;
	}
	
	#messageBox div button.large{
		font-size: 0.5em;
	}
	
	#fullScreenDiv{
	    width:100%;
	   /* Set the height to match that of the viewport. */
	    
	    width: auto;
	    padding-top:150px!important;
	    margin: 0!important;
	    position: relative;
	}
	
}
 

</style>
<section style="padding-bottom: 0px;padding-top:0px;" class="container-fluid main-section relative videoDiv">

        
   <div id="fullScreenDiv">
        <div id="videoDiv">           
            <video playsinline preload id="video" autoplay muted poster="images/whaleshark_lander.jpg">
            <source src="images/whaleshark_lander.webm#t=,3" type="video/webm"></source>
            <source src="images/whaleshark_lander.mp4#t=,3" type="video/mp4"></source>
            </video> 
        </div>
        <div id="messageBox"> 
            <div>
                <h2 class="vidcap">You can help study sharks!</h2>
                                    <a href="submit.jsp">
                <button class="large heroBtn">Report your sightings<span class="button-icon" aria-hidden="true"></button>
            </a>


            </div>
        </div>   
        

    </div>

  


</section>

<section class="container text-center main-section">

	<h2 class="section-header">How it works</h2>
	<p class="lead">The Wildbook for Sharks photo-identification library is a visual database of various shark encounters and of individually catalogued sharks. The library is maintained and used by marine biologists to collect and analyze shark sighting data to learn more about these amazing creatures.The Wildbook uses photographs of the skin patterning behind the gills of each shark, and any scars, to distinguish between individual animals. Cutting-edge software supports rapid identification using pattern recognition and photo management tools. You too can assist with shark research, by submitting photos and sighting data. The information you submit will be used in mark-recapture studies to help with the global conservation of these threatened species.</p>
	<p class="lead">The Wildbook uses photographs of the skin patterning behind the gills of each shark, and any scars, to distinguish between individual animals. Cutting-edge software supports rapid identification using pattern recognition and photo management tools.

You too can assist with shark research, by submitting photos and sighting data. The information you submit will be used in mark-recapture studies to help with the global conservation of this threatened species.</p>

	<div id="howtocarousel" class="carousel slide" data-ride="carousel">
		<ol class="list-inline carousel-indicators slide-nav">
	        <li data-target="#howtocarousel" data-slide-to="0" class="active">1. Photograph an animal<span class="caret"></span></li>
	        <li data-target="#howtocarousel" data-slide-to="1" class="">2. Submit photo/video<span class="caret"></span></li>
	        <li data-target="#howtocarousel" data-slide-to="2" class="">3. Researcher verification<span class="caret"></span></li>
	        <li data-target="#howtocarousel" data-slide-to="3" class="">4. Matching process<span class="caret"></span></li>
	        <li data-target="#howtocarousel" data-slide-to="4" class="">5. Match result<span class="caret"></span></li>
	    </ol>
		<div class="carousel-inner text-left">
			<div class="item active">
				<div class="col-xs-12 col-sm-6 col-md-6 col-lg-6">
					<h3>Photograph the flanks</h3>
					<p class="lead">
						Each shark has an individual fingerprint, such as the spots behind the gills on the sides of a whale shark or the 1st dorsal fin of a white shark. Get an image or video of their &ldquo;print&rdquo; and we can match that pattern to others already in the database, or your shark might be completely new to the database.
					</p>
					<p class="lead">
						<a href="photographing.jsp" title="">See the photography guide</a>
					</p>
				</div>
				<div class="col-xs-12 col-sm-4 col-sm-offset-2 col-md-4 col-md-offset-2 col-lg-4 col-lg-offset-2">
					<img class="pull-right" src="cust/mantamatcher/img/bellyshotofmanta.jpg" alt=""  />
				</div>
			</div>
			<div class="item">
				<div class="col-xs-12 col-sm-6 col-md-6 col-lg-6">
					<h3>Submit photo/video</h3>
					<p class="lead">
						You can upload files from your computer. Be sure to enter when and where you saw the shark, and add other information, such as scarring and sex, if you can. You will receive email updates when your animal is processed by a researcher.
					</p>
				</div>
				<div class="col-xs-12 col-sm-4 col-sm-offset-2 col-md-4 col-md-offset-2 col-lg-4 col-lg-offset-2">
					<img class="pull-right" src="images/how_it_works_submit.jpg" alt=""  />
				</div>
			</div>
			<div class="item">
				<div class="col-xs-12 col-sm-6 col-md-6 col-lg-6">
					<h3>Researcher verification</h3>
					<p class="lead">
						When you submit an identification photo, a local researcher receives a notification. This researcher will double check that the information you submitted is correct and add any additional information.
					</p>
				</div>
				<div class="col-xs-12 col-sm-4 col-sm-offset-2 col-md-4 col-md-offset-2 col-lg-4 col-lg-offset-2">
					<img class="pull-right" src="images/how_it_works_researcher_verification.jpg" alt=""  />
				</div>
			</div>
			<div class="item">
				<div class="col-xs-12 col-sm-6 col-md-6 col-lg-6">
					<h3>Matching process</h3>
					<p class="lead">
						Once a researcher is happy with all the data accompanying the identification photo using pattern matching algorithms. The algorithms are like facial recognition software for animal patterns.
					</p>
				</div>
				<div class="col-xs-12 col-sm-4 col-sm-offset-2 col-md-4 col-md-offset-2 col-lg-4 col-lg-offset-2">
					<img class="pull-right" src="images/how_it_works_matching_process.jpg" alt=""  />
				</div>
			</div>
			<div class="item">
				<div class="col-xs-12 col-sm-6 col-md-6 col-lg-6">
					<h3>Match Result</h3>
					<p class="lead">
						The algorithm (or manual comparison) provides researchers with a ranked selection of possible matches. Researchers will then visually confirm a match to existing sharks of the same species in the database, or create a new individual profile.
					</p>
				</div>
				<div class="col-xs-12 col-sm-4 col-sm-offset-2 col-md-4 col-md-offset-2 col-lg-4 col-lg-offset-2">
					<img class="pull-right" src="images/how_it_works_match_result.jpg" alt=""  />
				</div>
			</div>
		</div>
	</div>
</section>

<div class="container-fluid relative data-section">

    <aside class="container main-section">
        <div class="row">

            <!-- Random user profile to select -->
            <%
            //Shepherd myShepherd=new Shepherd(context);
            //myShepherd.beginDBTransaction();
			try{
	            // featured user: cache only the picked USERNAME (10 min); per-request cost
	            // is one primary-key lookup instead of materializing the whole user table
	            String wbFeatName = (String)application.getAttribute("wbFeaturedUsername");
	            Long wbFeatAt = (Long)application.getAttribute("wbFeaturedUsernameAt");
	            if ((wbFeatName == null) || (wbFeatAt == null) || ((System.currentTimeMillis() - wbFeatAt.longValue()) > 600000L)) {
	                User wbPick = myShepherd.getRandomUserWithPhotoAndStatement();
	                if (wbPick != null) {
	                    wbFeatName = wbPick.getUsername();
	                    application.setAttribute("wbFeaturedUsername", wbFeatName);
	                    application.setAttribute("wbFeaturedUsernameAt", Long.valueOf(System.currentTimeMillis()));
	                }
	            }
	            User featuredUser = (wbFeatName == null) ? null : myShepherd.getUser(wbFeatName);
	            if(featuredUser!=null){
	                String profilePhotoURL="images/empty_profile.jpg";
	                if(featuredUser.getUserImage()!=null){
	                	profilePhotoURL="/"+CommonConfiguration.getDataDirectoryName(context)+"/users/"+featuredUser.getUsername()+"/"+featuredUser.getUserImage().getFilename();
	                }

	            %>
	                <section class="col-xs-12 col-sm-6 col-md-4 col-lg-4 padding focusbox">
	                    <div class="focusbox-inner opec">
	                        <h2>Our contributors</h2>
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
	                        <a href="whoAreWe.jsp" title="" class="cta">Show me all the contributors</a>
	                    </div>
	                </section>
	            <%
	            }
			}
			catch(Exception e){e.printStackTrace();}
			finally{
	            //myShepherd.rollbackDBTransaction();
	            //myShepherd.closeDBTransaction();
			}
            %>


            <section class="col-xs-12 col-sm-6 col-md-4 col-lg-4 padding focusbox">
                <div class="focusbox-inner opec">
                    <h2>Latest shark encounters</h2>
                    <ul class="encounter-list list-unstyled">

                       <%
                       //Shepherd myShepherd=new Shepherd(context);


                       //myShepherd.beginDBTransaction();
                       try{
                    	   // latest encounters: cache the 3 catalog numbers (10 min); per-request cost
                    	   // is three primary-key lookups
                    	   java.util.List<String> wbLatestNums = (java.util.List<String>)application.getAttribute("wbLatestEncNums");
                    	   Long wbLatestAt = (Long)application.getAttribute("wbLatestEncNumsAt");
                    	   if ((wbLatestNums == null) || (wbLatestAt == null) || ((System.currentTimeMillis() - wbLatestAt.longValue()) > 600000L)) {
                    	       wbLatestNums = new java.util.ArrayList<String>();
                    	       for (Encounter wbE : myShepherd.getMostRecentIdentifiedEncountersByDate(3)) {
                    	           wbLatestNums.add(wbE.getCatalogNumber());
                    	       }
                    	       application.setAttribute("wbLatestEncNums", wbLatestNums);
                    	       application.setAttribute("wbLatestEncNumsAt", Long.valueOf(System.currentTimeMillis()));
                    	   }
                    	   List<Encounter> latestIndividuals = new java.util.ArrayList<Encounter>();
                    	   for (String wbNum : wbLatestNums) {
                    	       Encounter wbE = myShepherd.getEncounter(wbNum);
                    	       if (wbE != null) latestIndividuals.add(wbE);
                    	   }
                           int numResults=latestIndividuals.size();

	                       for(int i=0;i<numResults;i++){
	                           Encounter thisEnc=latestIndividuals.get(i);
	                           %>
	                            <li>
	                                <img src="cust/mantamatcher/img/whalesharkbw.png" alt="" class="pull-left" />
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
	                                <p><a href="encounters/encounter.jsp?number=<%=thisEnc.getCatalogNumber() %>" title=""><%=thisEnc.getIndividual().getDisplayName() %></a></p>


	                            </li>
	                        <%
	                        }
                       }
                       catch(Exception e){e.printStackTrace();}
                       finally{
	                       //myShepherd.rollbackDBTransaction();
	                       //myShepherd.closeDBTransaction();
                       }
                        %>

                    </ul>
                    <a href="encounters/searchResults.jsp?state=approved" title="" class="cta">See more encounters</a>
                </div>
            </section>
            <section class="col-xs-12 col-sm-6 col-md-4 col-lg-4 padding focusbox">
                <div class="focusbox-inner opec">
                    <h2>Top spotters (past 30 days)</h2>
                    <ul class="encounter-list list-unstyled">
                    <%
                    //Shepherd myShepherd=new Shepherd(context);
                    //myShepherd.beginDBTransaction();

                    try{
	                    //System.out.println("Date in millis is:"+(new org.joda.time.DateTime()).getMillis());
                            // top spotters: cache the username->count map (plain strings/ints, 10 min).
                            // The uncached call scans the encounter date range AND does a DB lookup per
                            // active submitter on EVERY homepage hit - the main source of page slowness.
                            Map<String,Integer> spotters = (Map<String,Integer>)application.getAttribute("wbTopSpotters");
                            Long wbSpottersAt = (Long)application.getAttribute("wbTopSpottersAt");
                            if ((spotters == null) || (wbSpottersAt == null) || ((System.currentTimeMillis() - wbSpottersAt.longValue()) > 600000L)) {
                                long startTime = System.currentTimeMillis() - Long.valueOf(1000L*60L*60L*24L*30L);
                                spotters = myShepherd.getTopUsersSubmittingEncountersSinceTimeInDescendingOrder(startTime);
                                application.setAttribute("wbTopSpotters", spotters);
                                application.setAttribute("wbTopSpottersAt", Long.valueOf(System.currentTimeMillis()));
                            }
	                    int numUsersToDisplay=3;
	                    if(spotters.size()<numUsersToDisplay){numUsersToDisplay=spotters.size();}
	                    Iterator<String> keys=spotters.keySet().iterator();
	                    Iterator<Integer> values=spotters.values().iterator();
	                    while((keys.hasNext())&&(numUsersToDisplay>0)){
	                          String spotter=keys.next();
	                          int numUserEncs=values.next().intValue();
	                          if(myShepherd.getUser(spotter)!=null){
	                        	  String profilePhotoURL="images/empty_profile.jpg";
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
	                                    <p><a href="#" title=""><%=spotter %></a>, <span><%=numUserEncs %> encounters<span></p>
	                                </li>

	                           <%
	                           numUsersToDisplay--;
	                    }
	                   } //end while
                    }
                    catch(Exception e){e.printStackTrace();}
                    finally{
                    	//myShepherd.rollbackDBTransaction();
                        //myShepherd.closeDBTransaction();
                    }


                   %>

                    </ul>
                    <a href="whoAreWe.jsp" title="" class="cta">See all spotters</a>
                </div>
            </section>
        </div>
    </aside>
</div>

<div class="container-fluid">
    <section class="container text-center  main-section">
        <div class="row">
            <section class="col-xs-12 col-sm-3 col-md-3 col-lg-3 padding">
                <p class="brand-primary"><i><span class="massive"><%=numMarkedIndividuals %></span> identified sharks</i></p>
            </section>
            <section class="col-xs-12 col-sm-3 col-md-3 col-lg-3 padding">
                <p class="brand-primary"><i><span class="massive"><%=numEncounters %></span> reported sightings</i></p>
            </section>
            <section class="col-xs-12 col-sm-3 col-md-3 col-lg-3 padding">

                <p class="brand-primary"><i><span class="massive"><%=numUsersWithRoles %></span> citizen scientists</i></p>
            </section>
            <section class="col-xs-12 col-sm-3 col-md-3 col-lg-3 padding">

                <p class="brand-primary"><i><span class="massive"><%=numDataContributors %></span> researchers and volunteers</i></p>
            </section>
        </div>

        <hr/>

        <main class="container">
            <article class="text-center">
                <div class="row">
                      <h1 style="color: #005589;-webkit-text-fill-color: #005589;">Why we do this</h1>
                        <p class="lead">
                            <i>&ldquo;Sharkbook, previously Wildbook for Whale Sharks, has pioneered the way for a new generation of global scale, collaborative wildlife projects that blend citizen science and computer vision to help researchers get bigger and more detailed pictures of some of the world's most mysterious species.&rdquo;</i> - Jason Holmberg, Information Architect</p>
                        <a href="whoAreWe.jsp" title="">Learn more about the Researchers and Volunteers</a><br>
                        <a href="http://www.wildme.org" title="">Learn more about Wild Me and Wildbook</a>
                    
                </div>
            </article>
        <main>

    </section>
</div>



<div class="container-fluid">
    <section class="container main-section">
        <h2 class="section-header">How can I help?</h2>
        <p class="lead text-center">If you are not on site, there are still other ways to get engaged</p>

        <%
           myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
            
            %>
        <section class="donate-section">
            <div class="col-xs-12 col-sm-6 col-md-6 col-lg-6">
                <h3>Donate</h3>
                <p>Donations, including in-kind, large or small, are always welcome. Your support helps the continued development of our project and can support effective, science-based conservation management, and safeguard these sharks and their habitat.</p>
                <p>You can make a one time donation or <a href="adoptashark.jsp" title="More information about donations">learn about adopting an animal</a></p>
            </div>
            <div class="col-xs-12 col-sm-5 col-md-5 col-lg-5 col-sm-offset-1 col-md-offset-1 col-lg-offset-1">
                <a href="oneTimeDonation.jsp">
	                <button class="large">
	                    One Time Donation
	                    <span class="button-icon" aria-hidden="true">
	                </button>
                </a>
            </div>
        </section>
    </section>
</div>

<div class="container-fluid">
    <section class="container main-section">
        <h2 class="section-header">Development</h2>
        <p class="lead text-center">Sharkbook is maintained and developed by the nonprofit organization <a href="https://www.wildme.org" target="_blank">Wild Me</a> with significant support and input from the research community. This site is a flagship project of <a target="_blank" href="https://github.com/wildmeorg">Wild Me's Wildbook and WBIA open source projects</a>. <a href="http://www.simonjpierce.com/">Dr. Simon Pierce</a> provides scientific oversight and guidance.</p>
</section>
</div>


<jsp:include page="footer.jsp" flush="true"/>

<!-- 
<script>
window.addEventListener("resize", function(e) { $("#map_canvas").height($("#map_canvas").width()*0.662); });
google.maps.event.addDomListener(window, "resize", function() {
	 google.maps.event.trigger(map, "resize");
	 map.fitBounds(bounds);
	});
</script>
 -->
