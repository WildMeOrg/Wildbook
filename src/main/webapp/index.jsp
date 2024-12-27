	<%@ page contentType="text/html; charset=utf-8" language="java"
     import="org.ecocean.*,
              org.ecocean.servlet.ServletUtilities,
              java.util.ArrayList,
			  java.util.Calendar,
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

//String mapKey = CommonConfiguration.getGoogleMapsKey(context);
String langCode=ServletUtilities.getLanguageCode(request);


// Make a properties object for lang support.
Properties props = new Properties();
// Grab the properties file with the correct language strings.
props = ShepherdProperties.getProperties("index.properties", langCode,context);





//let's quickly get the data we need from Shepherd

int numMarkedIndividuals=0;
int numEncounters=0;
//int numDataContributors=0;
//int numUsersWithRoles=0;
int numUsers=0;

int numLeafy = 0;
int numWeedy = 0;

long avgSightingsPerYear = 0;

QueryCache qc=QueryCacheFactory.getQueryCache(context);

myShepherd.beginDBTransaction();

//String url = "login.jsp";
//response.sendRedirect(url);
//RequestDispatcher dispatcher = getServletContext().getRequestDispatcher(url);
//dispatcher.forward(request, response);


try{

    //numMarkedIndividuals=myShepherd.getNumMarkedIndividuals();
    numMarkedIndividuals=qc.getQueryByName("numMarkedIndividuals").executeCountQuery(myShepherd).intValue();
    numEncounters=numUsers=qc.getQueryByName("numEncounters").executeCountQuery(myShepherd).intValue();
    //numEncounters=qc.getQueryByName("numEncounters").executeCountQuery(myShepherd).intValue();
    //numDataContributors=myShepherd.getAllUsernamesWithRoles().size();
    //numDataContributors=qc.getQueryByName("numUsersWithRoles").executeCountQuery(myShepherd).intValue();
    numUsers=qc.getQueryByName("numUsers").executeCountQuery(myShepherd).intValue();
	//numUsersWithRoles = numUsers-numDataContributors;
	numLeafy=qc.getQueryByName("numLeafyIndividuals3").executeCountQuery(myShepherd).intValue();
	numWeedy=qc.getQueryByName("numWeedyIndividuals3").executeCountQuery(myShepherd).intValue();

	/*
	if (numEncounters>0) {
			Encounter oldestEnc = (Encounter) qc.getQueryByName("oldestEncounterMillis").executeQuery(myShepherd).get(0);
			Encounter youngestEnc = (Encounter) qc.getQueryByName("youngestEncounterMillis").executeQuery(myShepherd).get(0);
			long oldDate = oldestEnc.getDWCDateAddedLong();
			long newDate = youngestEnc.getDWCDateAddedLong();
			long yearSpan = (newDate - oldDate) / 31556952000L;
			if (yearSpan > 1) {
                avgSightingsPerYear = Math.round(numEncounters / yearSpan);
            } else {
                avgSightingsPerYear = numEncounters;
			}

	}
	*/

	//if (youngestEnc!=null&&oldestEnc!=null&&youngestEnc.get(0)!=null&&oldestEnc.get(0)!=null) {

		//long oldestMillis = oldestEnc.get(0).getDWCDateAddedLong();
		//long youngestMillis = youngestEnc.get(0).getDWCDateAddedLong();
		//Calendar cal = Calendar.getInstance();
		//cal.setTimeInMillis(oldestMillis);
		//int oldestYear = cal.get(Calendar.YEAR);
		//cal.setTimeInMillis(youngestMillis);
		//int youngestYear = cal.get(Calendar.YEAR);
	//}
}
catch(Exception e){
    System.out.println("INFO: *** If you are seeing an exception here (via index.jsp) your likely need to setup QueryCache");
    System.out.println("      *** This entails configuring a directory via cache.properties and running appadmin/testQueryCache.jsp");
    e.printStackTrace();
}
finally{
   //myShepherd.rollbackDBTransaction();
   //myShepherd.closeDBTransaction();
}
%>
<section class="hero container-fluid splash-section relative" style="height: 600px;">
	<!--
	<div class="center-block">
	</div>
	-->
	<h2 id="main-splash"><%=props.getProperty("mainSplash") %></h2>
	<span class="splash-submit glyphicon glyphicon-large glyphicon-chevron-down white down"></span>
	<!--
	<div id="splash-div">
	</div>
	<div class="center-block">
	</div>
	-->
</section>
<section class="hero-bottom container-fluid splash-section relative" style="height: 800px;">
	<a class="splash-submit" href="submit.jsp">
		<button class="index-submit-button"><%= props.getProperty("reportEncounter") %><span class="button-icon index-button-icon" aria-hidden="true"></button>
	</a>
</section>


<section class="container text-center main-section">

	<h2 class="section-header"><%=props.getProperty("howItWorksH") %></h2>

  <!-- carousel is gone now, forever? -->

<div class="carousel-inner text-left">

	<div class="row">
		<div class="col-xs-12 col-sm-7 col-md-7 col-lg-7">
			<h3><%=props.getProperty("innerPhotoH3") %></h3>
			<p class="lead">
				<%=props.getProperty("innerPhotoP") %>
			</p>
		</div>
		<div class="hidden-xs col-sm-5  col-md-5  col-lg-5">
			<img  src="images/how_it_works.png" alt=""  />
		</div>
	</div>

	<hr>

	<div class="row">
		<div class="hidden-xs col-sm-6  col-md-6  col-lg-6">
			<img  src="images/submit_photo_id.png" alt=""  />
		</div>
		<div class="col-xs-12 col-sm-6 col-md-6 col-lg-6">
			<h3><%=props.getProperty("innerSubmitH3") %></h3>
			<p class="lead">
				<%=props.getProperty("innerSubmitP") %>
			</p>
		</div>
	</div>

	<hr>

	<div class="row">
		<div class="col-xs-12 col-sm-6 col-md-6 col-lg-6">
			<h3><%=props.getProperty("innerVerifyH3") %></h3>
			<p class="lead">
				<%=props.getProperty("innerVerifyP") %>
			</p>
		</div>
		<div class="hidden-xs col-sm-6  col-md-6  col-lg-6">
			<img  src="cust/mantamatcher/img/ResearcherVerSD.png" alt=""/>
		</div>
	</div>

	<hr>

	<div class="row">
		<div class="hidden-xs col-sm-6  col-md-6  col-lg-6">
			<img  src="images/dragon_bounding.png" alt=""  />
		</div>
		<div class="col-xs-12 col-sm-6 col-md-6 col-lg-6">
			<h3><%=props.getProperty("innerMatchingH3") %></h3>
			<p class="lead">
				<%=props.getProperty("innerMatchingP") %>
			</p>
		</div>
	</div>

	<hr>

	<div class="row">
		<div class="col-xs-12 col-sm-6 col-md-6 col-lg-6">
			<h3><%=props.getProperty("innerResultH3") %></h3>
			<p class="lead">
				<%=props.getProperty("innerResultP") %>
			</p>
		</div>
		<div class="hidden-xs col-sm-6  col-md-6  col-lg-6">
			<img  src="images/dragon_match_round.png" alt=""  />
		</div>
	</div>

</div>

</section>

<div class="counter-div container-fluid relative data-section">

    <aside class="container user-activity-section">
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
                            <img src="<%=profilePhotoURL %>" width="80px" height="*" alt="" class="pull-left" />
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
            } // end if

            }
            catch(Exception e){e.printStackTrace();}
            finally{

            	//myShepherd.rollbackDBTransaction();
            }
            %>


            <section class="col-xs-12 col-sm-6 col-md-4 col-lg-4 padding focusbox">
                <div class="focusbox-inner opec">
                    <h2>Latest seadragon encounters</h2>
                    <ul class="encounter-list list-unstyled">

                       <%

                
                       try{
                           List<Encounter> latestIndividuals=myShepherd.getMostRecentIdentifiedEncountersByDate(3);
                           int numResults=latestIndividuals.size();
	                       for(int i=0;i<numResults;i++){
	                           Encounter thisEnc=latestIndividuals.get(i);
	                           %>
	                            <li>
	                                <img src="cust/mantamatcher/img/DragonSil2.png" alt="" width="85px" height="75px" class="pull-left" />
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

                       }

                        %>

                    </ul>
                    <a href="encounters/searchResults.jsp?state=approved" title="" class="cta"><%=props.getProperty("seeMoreEncs") %></a>
                </div>
            </section>
            <section class="col-xs-12 col-sm-6 col-md-4 col-lg-4 padding focusbox">
                <div class="focusbox-inner opec">
                    <h2>Top submitters (past 30 days)</h2>
                    <ul class="encounter-list list-unstyled">
                    <%
                    
                    ArrayList<String> ignoreThese=new ArrayList<String>();
                    ignoreThese.add("tomcat");
                    ignoreThese.add("siowamteam");
                    ignoreThese.add("Chrissy");
                    ignoreThese.add("admin");
                    ignoreThese.add("Nerida Wilson");
                    
                    
                    //myShepherd.beginDBTransaction();
                    try{
	                    //System.out.println("Date in millis is:"+(new org.joda.time.DateTime()).getMillis());
                        long startTime = System.currentTimeMillis() - Long.valueOf(2592000000L);

	                    Map<String,Integer> spotters = myShepherd.getTopSubmittersSinceTimeInDescendingOrder(startTime,ignoreThese);
	                    int numUsersToDisplay=3;
	                    if(spotters.size()<numUsersToDisplay){numUsersToDisplay=spotters.size();}
	                    Iterator<String> keys=spotters.keySet().iterator();
	                    Iterator<Integer> values=spotters.values().iterator();
	                    while((keys.hasNext())&&(numUsersToDisplay>0)){
	                          
	                    	String spotter=keys.next();
	                    	System.out.println("spotter: "+spotter);
	                          int numUserEncs=values.next().intValue();
	                          String profilePhotoURL="images/user-profile-white-transparent.png";
	                          User thisUser=myShepherd.getUserByUUID(spotter);
	                          if(thisUser!=null && !(thisUser.getUsername()!=null && thisUser.getUsername().equals("siowamteam"))){
		                        	
	                              if(thisUser.getUserImage()!=null){
	                              	profilePhotoURL="/"+CommonConfiguration.getDataDirectoryName(context)+"/users/"+thisUser.getUsername()+"/"+thisUser.getUserImage().getFilename();
	                              }
	                              //System.out.println(spotters.values().toString());
	                            Integer myInt=spotters.get(spotter);
	                            //System.out.println(spotters);

	                          %>
	                                <li>
	                                    <img src="<%=profilePhotoURL %>" width="80px" height="*" alt="" class="pull-left" />
	                                    <%
	                                    if(thisUser.getAffiliation()!=null){
	                                    %>
	                                    <small><%=thisUser.getAffiliation() %></small>
	                                    <%
	                                      }
	                                    %>
	                                    <p><em><%=thisUser.getFullName() %></em> <span><%=numUserEncs %> <%=props.getProperty("encounters") %><span></p>
	                                </li>

	                           <%
	                           numUsersToDisplay--;
	                    }
	                   } //end while
                    }
                    catch(Exception e){e.printStackTrace();}
                    finally{
                    	//myShepherd.rollbackDBTransaction();
                    	}

                   %>

                    </ul>
                    <a href="whoAreWe.jsp" title="" class="cta"><%=props.getProperty("allSpotters") %></a>
                </div>
            </section>
             <section class="col-xs-12 col-sm-6 col-md-4 col-lg-4 padding focusbox">
                <div class="focusbox-inner opec">
                    <h2>Top photographers (past 30 days)</h2>
                    <ul class="encounter-list list-unstyled">
                    <%
                    //myShepherd.beginDBTransaction();
                    try{
	                    //System.out.println("Date in millis is:"+(new org.joda.time.DateTime()).getMillis());
                        long startTime = System.currentTimeMillis() - Long.valueOf(2592000000L);

	                    Map<String,Integer> spotters = myShepherd.getTopPhotographersSinceTimeInDescendingOrder(startTime,ignoreThese);
	                    int numUsersToDisplay=3;
	                    if(spotters.size()<numUsersToDisplay){numUsersToDisplay=spotters.size();}
	                    Iterator<String> keys=spotters.keySet().iterator();
	                    Iterator<Integer> values=spotters.values().iterator();
	                    while((keys.hasNext())&&(numUsersToDisplay>0)){
	                          
	                    	String spotter=keys.next();
	                    	System.out.println("spotter: "+spotter);
	                          int numUserEncs=values.next().intValue();
	                          String profilePhotoURL="images/user-profile-white-transparent.png";
	                          User thisUser=myShepherd.getUserByUUID(spotter);
	                          if(thisUser!=null && !(thisUser.getUsername()!=null && thisUser.getUsername().equals("siowamteam"))){
		                        	
	                              if(thisUser.getUserImage()!=null){
	                              	profilePhotoURL="/"+CommonConfiguration.getDataDirectoryName(context)+"/users/"+thisUser.getUsername()+"/"+thisUser.getUserImage().getFilename();
	                              }
	                              //System.out.println(spotters.values().toString());
	                            Integer myInt=spotters.get(spotter);
	                            //System.out.println(spotters);

	                          %>
	                                <li>
	                                    <img src="<%=profilePhotoURL %>" width="80px" height="*" alt="" class="pull-left" />
	                                    <%
	                                    if(thisUser.getAffiliation()!=null){
	                                    %>
	                                    <small><%=thisUser.getAffiliation() %></small>
	                                    <%
	                                      }
	                                    %>
	                                    <p><em><%=thisUser.getFullName() %></em> <span><%=numUserEncs %> <%=props.getProperty("encounters") %><span></p>
	                                </li>

	                           <%
	                           numUsersToDisplay--;
	                    }
	                   } //end while
                    }
                    catch(Exception e){e.printStackTrace();}
                    finally{
                    	//myShepherd.rollbackDBTransaction();
                    }

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
            <section class="col-xs-12 col-sm-4 col-md-4 col-lg-4 padding">
                <p class="brand-primary"><i><span class="massive"><%=numWeedy%></span>weedy seadragons identified</i></p>
            </section>
            <section class="col-xs-12 col-sm-4 col-md-4 col-lg-4 padding">
                <p class="brand-primary"><i><span class="massive"><%=numLeafy%></span>leafy seadragons identified</i></p>
			</section>

			<section class="col-xs-12 col-sm-4 col-md-4 col-lg-4 padding">
                <p class="brand-primary"><i><span class="massive"><%=numEncounters%></span>encounters</i></p>
			</section>

        </div>

        <hr/>

        <main class="container">
            <article class="text-center">
                <div class="row">
                    <img src="cust/mantamatcher/img/WhyWeDoThisSD.png" alt="" class="pull-left col-xs-7 col-sm-4 col-md-4 col-lg-4 col-xs-offset-2 col-sm-offset-1 col-md-offset-1 col-lg-offset-1" />
                    <div class="col-xs-12 col-sm-6 col-md-6 col-lg-6 text-left">
                        <h1><%=props.getProperty("whyWeDoThis") %></h1>
                        <a href="//SeadragonSearch.org/#contributors" title="Contributors"><p class="lead"><%=props.getProperty("contributors") %></a></p>

                    </div>
                </div>
            </article>
        <main>

    </section>
</div>





<jsp:include page="footer.jsp" flush="true"/>



<%
myShepherd.rollbackAndClose();
myShepherd=null;
%>
