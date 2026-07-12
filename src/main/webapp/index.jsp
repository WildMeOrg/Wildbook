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

Shepherd myShepherd=null;
myShepherd=new Shepherd(context);
myShepherd.setAction("index.jsp");

String mapKey = CommonConfiguration.getGoogleMapsKey(context);
String langCode=ServletUtilities.getLanguageCode(request);

//check for and inject a default user 'tomcat' if none exists
// Make a properties object for lang support.
Properties props = new Properties();
// Grab the properties file with the correct language strings.
props = ShepherdProperties.getProperties("index.properties", langCode,context);







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
    System.out.println("INFO: *** If you are seeing an exception here (via index.jsp) your likely need to setup QueryCache");
    System.out.println("      *** This entails configuring a directory via cache.properties and running appadmin/testQueryCache.jsp");
    e.printStackTrace();
}
finally{
   myShepherd.rollbackDBTransaction();
   myShepherd.closeDBTransaction();
}
%>

<section class="hero container-fluid main-section relative">
    <div class="container relative">
        <div class="col-xs-12 col-sm-10 col-md-8 col-lg-6">
            <h1 class="hidden">Wildbook</h1>

            <!-- Main Splash "Wildbook helps you identify..." -->
            <h2><%=props.getProperty("mainSplash") %></h2>
            <!--
            <button id="watch-movie" class="large light">
				Watch the movie
				<span class="button-icon" aria-hidden="true">
			</button>
			-->
            <a href="submit.jsp">
                <button class="large"><%= props.getProperty("reportEncounter") %><span class="button-icon" aria-hidden="true"></button>
            </a>
        </div>

            </div>


</section>

<section class="container text-center main-section">

	<h2 class="section-header"><%=props.getProperty("howItWorksH") %></h2>

  <!-- All carousel text can be modified in the index properties files -->

	<div id="howtocarousel" class="carousel slide" data-ride="carousel">
		<ol class="list-inline carousel-indicators slide-nav">
	        <li data-target="#howtocarousel" data-slide-to="0" class="active">1. <%=props.getProperty("carouselPhoto") %><span class="caret"></span></li>
	        <li data-target="#howtocarousel" data-slide-to="1" class="">2. <%=props.getProperty("carouselSubmit") %><span class="caret"></span></li>
	        <li data-target="#howtocarousel" data-slide-to="2" class="">3. <%=props.getProperty("carouselVerify") %><span class="caret"></span></li>
	        <li data-target="#howtocarousel" data-slide-to="3" class="">4. <%=props.getProperty("carouselMatching") %><span class="caret"></span></li>
	    </ol>
		<div class="carousel-inner text-left">
			<div class="item active">
				<div class="col-xs-12 col-sm-6 col-md-6 col-lg-6">
					<h3><%=props.getProperty("innerPhotoH3") %></h3>
					<p class="lead">
						<%=props.getProperty("innerPhotoP") %>
					</p>

				</div>
				<div class="col-xs-12 col-sm-4 col-sm-offset-2 col-md-4 col-md-offset-2 col-lg-4 col-lg-offset-2">
					<img class="pull-right" src="images/step-1.jpg" alt=""  />
				</div>
			</div>
			<div class="item">
				<div class="col-xs-12 col-sm-6 col-md-6 col-lg-6">
          <h3><%=props.getProperty("innerSubmitH3") %></h3>
          <p class="lead">
            <%=props.getProperty("innerSubmitP") %>
          </p>
				</div>
				<div class="col-xs-12 col-sm-4 col-sm-offset-2 col-md-4 col-md-offset-2 col-lg-4 col-lg-offset-2">
					<img class="pull-right" src="images/step-2.jpg" alt=""  />
				</div>
			</div>
			<div class="item">
				<div class="col-xs-12 col-sm-6 col-md-6 col-lg-6">
          <h3><%=props.getProperty("innerVerifyH3") %></h3>
          <p class="lead">
            <%=props.getProperty("innerVerifyP") %>
          </p>
				</div>
				<div class="col-xs-12 col-sm-4 col-sm-offset-2 col-md-4 col-md-offset-2 col-lg-4 col-lg-offset-2">
					<img class="pull-right" src="images/step-3.jpg" alt=""  />
				</div>
			</div>
			<div class="item">
				<div class="col-xs-12 col-sm-6 col-md-6 col-lg-6">
          <h3><%=props.getProperty("innerMatchingH3") %></h3>
          <p class="lead">
            <%=props.getProperty("innerMatchingP") %>
          </p>
				</div>
				<div class="col-xs-12 col-sm-4 col-sm-offset-2 col-md-4 col-md-offset-2 col-lg-4 col-lg-offset-2">
					<img class="pull-right" src="images/step-4.jpg" alt=""  />
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
            myShepherd.beginDBTransaction();
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
                        
                    </div>
                </section>
            <%
            } // end if

            }
            catch(Exception e){e.printStackTrace();}
            finally{

            	myShepherd.rollbackDBTransaction();
            }
            %>


            <section class="col-xs-12 col-sm-6 col-md-4 col-lg-4 padding focusbox">
                <div class="focusbox-inner opec">
                    <h2><%=props.getProperty("latestEncs") %></h2>
<!--
                    <h2><%=props.getProperty("latestAnimalEncounters") %></h2>
-->
                    <ul class="encounter-list list-unstyled">

                       <%
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
                       myShepherd.beginDBTransaction();
                       try{
	                       for(int i=0;i<numResults;i++){
	                           Encounter thisEnc=latestIndividuals.get(i);
	                           %>
	                            <li>
	                                <img src="cust/mantamatcher/img/giraffe-silhouette.svg" alt="" width="85px" height="75px" class="pull-left" />
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
                    myShepherd.beginDBTransaction();
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
	                                    <p><a href="user.jsp?id=<%=thisUser.getUUID()%>" title=""><%=spotter %></a>, <span><%=numUserEncs %> <%=props.getProperty("encounters") %><span></p>
	                                </li>

	                           <%
	                           numUsersToDisplay--;
	                    }
	                   } //end while
                    }
                    catch(Exception e){e.printStackTrace();}
                    finally{myShepherd.rollbackDBTransaction();}

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
<!--
                    <img src="cust/mantamatcher/img/why-we-do-this.png" alt="" class="pull-left col-xs-7 col-sm-4 col-md-4 col-lg-4 col-xs-offset-2 col-sm-offset-1 col-md-offset-1 col-lg-offset-1" />
-->
                    <div xclass="col-xs-12 col-sm-6 col-md-6 col-lg-6 text-left">
                        <h1><%=props.getProperty("whyWeDoThis") %></h1>
                        <p class="lead"><%=props.getProperty("whyBody") %></p>
                        <a href="overview.jsp" title=""><%=props.getProperty("whyMore") %></a>
                    </div>
                </div>
            </article>
        <main>

    </section>
</div>



<jsp:include page="footer.jsp" flush="true"/>



<%
myShepherd.rollbackDBTransaction();
myShepherd.closeDBTransaction();
myShepherd=null;
%>
