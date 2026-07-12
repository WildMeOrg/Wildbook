<%@ page contentType="text/html; charset=utf-8" language="java"
     import="org.ecocean.*,
              org.ecocean.servlet.ServletUtilities,
              java.util.ArrayList,
              java.util.List,
              java.util.Map,
              java.util.Iterator,
              java.util.Properties,
              java.util.StringTokenizer,
              org.ecocean.cache.*,org.ecocean.shepherd.core.*,
              org.ecocean.CommonConfiguration
              "
%>



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
if (!CommonConfiguration.isWildbookInitialized(myShepherd)) {
  System.out.println("WARNING: index.jsp has determined that CommonConfiguration.isWildbookInitialized()==false!");
  %>
    <script type="text/javascript">
      console.log("Wildbook is not initialized!");
    </script>
  <%
  StartupWildbook.initializeWildbook(request, myShepherd);
}
// Make a properties object for lang support.
Properties props = new Properties();
// Grab the properties file with the correct language strings.
props = ShepherdProperties.getProperties("index.properties", langCode,context);


%>




<%


//let's quickly get the data we need from Shepherd

int numMarkedIndividuals=0;
int numEncounters=0;
int numDataContributors=0;
int numUsersWithRoles=0;
int numUsers=0;

QueryCache qc=QueryCacheFactory.getQueryCache(context);

myShepherd.beginDBTransaction();

//String url = "login.jsp";
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
    e.printStackTrace();
}
finally{
   myShepherd.rollbackDBTransaction();
   myShepherd.closeDBTransaction();
}
%>

<section class="hero container-fluid main-section relative" id="hero-section">
    <div class="container relative">
        <div class="col-xs-12 col-sm-10 col-md-8 col-lg-6">
            <h2><%= props.getProperty("mainSplash") %></h2>
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

<script type="text/javascript">
// Hero Image Carousel
(function() {
    // Define the hero images to rotate through
    // Add new images to the heroes folder and list them here
    var heroImages = [
        'cust/mantamatcher/img/heroes/jaguar.jpg',
        // Add more images here as they become available:
        'cust/mantamatcher/img/heroes/africanElephant.jpg',
        'cust/mantamatcher/img/heroes/ocelot.jpg',
        'cust/mantamatcher/img/heroes/sandCat.jpg',
        'cust/mantamatcher/img/heroes/stripedHyena.jpg',
		'cust/mantamatcher/img/heroes/wolverine.jpg'
    ];

    // Only run carousel if there are multiple images
    if (heroImages.length > 1) {
        var currentIndex = 0;
        var heroSection = document.getElementById('hero-section');
        var dataSection = document.querySelector('.data-section');

        // Function to change the background image
        function rotateHeroImage() {
            currentIndex = (currentIndex + 1) % heroImages.length;
            var imageUrl = 'url("' + heroImages[currentIndex] + '")';

            if (heroSection) {
                heroSection.style.backgroundImage = imageUrl;
            }
            if (dataSection) {
                dataSection.style.backgroundImage = imageUrl;
            }
        }

        // Rotate images every 6 seconds
        setInterval(rotateHeroImage, 6000);
    }
})();
</script>

<!-- usedta be the carousel -->
<!-- add different tints for the divs and a litttle bold and color to the headers -->
<section class="container text-center main-section">
	<h2 class="section-header"><%=props.getProperty("howItWorksH") %></h2>
        <div class="index-info-tile-1 col-xs-12 col-sm-6 col-md-6 col-lg-6">
            <h3 class="section-header"><%=props.getProperty("innerPhotoH3") %></h3>
            <p class="lead">
                <%=props.getProperty("innerPhotoP") %>
            </p>
        </div>
        <div class="index-info-tile-2 col-xs-12 col-sm-6 col-md-6 col-lg-6">
            <h3 class="section-header"><%=props.getProperty("innerSubmitH3") %></h3>
            <p class="lead">
                <%=props.getProperty("innerSubmitP") %>
            </p>
        </div>
        <div class="index-info-tile-3 col-xs-12 col-sm-6 col-md-6 col-lg-6">
            <h3 class="section-header"><%=props.getProperty("innerVerifyH3") %></h3>
            <p class="lead">
                <%=props.getProperty("innerVerifyP") %>
            </p>
        </div>
        <div class="index-info-tile-4 col-xs-12 col-sm-6 col-md-6 col-lg-6">
            <h3 class="section-header"><%=props.getProperty("innerMatchingH3") %></h3>
            <p class="lead">
                <%=props.getProperty("innerMatchingP") %>
            </p>
        </div>

        <div class="col-xs-12 col-sm-12 col-md-12 col-lg-12">
            <h3 class="section-header"><%=props.getProperty("innerResultH3") %></h3>
        </div>

        <div class="index-info-image col-xs-12 col-sm-6 col-md-6 col-lg-6">

            <img src="cust/mantamatcher/img/jag-detect.png" />
            <label><%=props.getProperty("resultImageLabel1") %></label>
        </div>

        <div class="index-info-image col-xs-12 col-sm-6 col-md-6 col-lg-6">

            <img src="cust/mantamatcher/img/jag-heatmap.png" />
            <label><%=props.getProperty("resultImageLabel2") %></label>
        </div>

        <div class="index-info-tile-5 col-xs-12 col-sm-12 col-md-12 col-lg-12">
            <br>
            <p class="lead">
                <%=props.getProperty("innerResultP") %>
            </p>
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
                    <a href="encounters/searchResults.jsp?state=approved" title="" class="cta"><%=props.getProperty("seeMoreEncs") %></a>
                </div>
            </section>

        </div>
    </aside>
</div>

<div class="container-fluid">
    <section class="container text-center  main-section">
        <div class="row">
            <section class="col-xs-12 col-sm-3 col-md-3 col-lg-3 padding">
                <p class="brand-primary"><i><span class="massive"><%=numMarkedIndividuals %></span><%=props.getProperty("identifiedAnimals") %></i></p>
            </section>
            <section class="col-xs-12 col-sm-3 col-md-3 col-lg-3 padding">
                <p class="brand-primary"><i><span class="massive"><%=numEncounters %></span> <%=props.getProperty("reportedSightings") %></i></p>
            </section>
            <section class="col-xs-12 col-sm-3 col-md-3 col-lg-3 padding">

                <p class="brand-primary"><i><span class="massive"><%=numUsersWithRoles %></span><%=props.getProperty("citizenScientists") %></i></p>
            </section>
            <section class="col-xs-12 col-sm-3 col-md-3 col-lg-3 padding">

                <p class="brand-primary"><i><span class="massive"><%=numDataContributors %></span><%=props.getProperty("researcherCount") %></i></p>
            </section>
        </div>

        <hr/>



    </section>
</div>


 


<jsp:include page="footer.jsp" flush="true"/>



<%
myShepherd.closeDBTransaction();
myShepherd=null;
%>
