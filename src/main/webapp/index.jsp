<%@ page contentType="text/html; charset=utf-8" language="java"
     import="org.ecocean.*,
              org.ecocean.servlet.ServletUtilities,
              java.util.ArrayList,
              java.util.List,
              java.util.Map,
              java.util.Iterator,
              java.util.Properties,
              java.util.StringTokenizer,
              org.ecocean.cache.*,
              org.ecocean.metrics.Prometheus
              "
%>



<jsp:include page="header.jsp" flush="true"/>

<%
String context=ServletUtilities.getContext(request);


String langCode=ServletUtilities.getLanguageCode(request);

// Make a properties object for lang support.
Properties props = new Properties();
// Grab the properties file with the correct language strings.
props = ShepherdProperties.getProperties("index.properties", langCode,context);



//let's quickly get the data we need from Shepherd

int numMarkedIndividuals=0;
int numEncounters=0;
int numNests=0;

int numDataContributors=0;
int numUsersWithRoles=0;
int numUsers=0;

//QueryCache qc=QueryCacheFactory.getQueryCache(context);

//myShepherd.beginDBTransaction();

//String url = "login.jsp";
//response.sendRedirect(url);
//RequestDispatcher dispatcher = getServletContext().getRequestDispatcher(url);
//dispatcher.forward(request, response);


try{


    //numMarkedIndividuals=myShepherd.getNumMarkedIndividuals();
    //numMarkedIndividuals=qc.getQueryByName("numMarkedIndividuals").executeCountQuery(myShepherd).intValue();
    //numEncounters=myShepherd.getNumEncounters();
    //numNests=myShepherd.getNumNests();
    //numDataContributors=myShepherd.getAllUsernamesWithRoles().size();
    
    numDataContributors = (new Double(Prometheus.getValue("wildbook_users_total"))).intValue();
    
    
    //numUsersWithRoles = myShepherd.getNumUsers()-numDataContributors;
    //numEncounters=qc.getQueryByName("numEncounters", context).executeCountQuery(myShepherd).intValue();
    //numDataContributors=myShepherd.getAllUsernamesWithRoles().size();
    //numDataContributors=qc.getQueryByName("numUsersWithRoles").executeCountQuery(myShepherd).intValue();
    //numUsers=qc.getQueryByName("numUsers").executeCountQuery(myShepherd).intValue();
    //numUsersWithRoles = numUsers-numDataContributors;

    numMarkedIndividuals=(new Double(Prometheus.getValue("wildbook_individuals_total"))).intValue();
    //numEncounters=myShepherd.getNumEncounters();
    numEncounters=(new Double(Prometheus.getValue("wildbook_encounters_total"))).intValue();
    

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

<section class="hero container-fluid main-section relative">
    <div class="container relative">
        <div class="col-xs-12 col-sm-10 col-md-8 col-lg-6">
            <h1 class="hidden">Wildbook</h1>
            <h2>Welcome to the Internet...</br> of Turtles!</h2>
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
			<img  src="images/how_it_works_bellyshot_of_manta.jpg" alt=""  />
		</div>
	</div>

	<hr>

	<div class="row"> 
		<div class="hidden-xs col-sm-6  col-md-6  col-lg-6">
			<img  src="images/how_it_works_submit.jpg" alt=""  />
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
			<img  src="images/how_it_works_researcher_verification.jpg" alt=""/>
		</div>
	</div>

	<hr>

	<div class="row"> 
		<div class="hidden-xs col-sm-6  col-md-6  col-lg-6">
			<img  src="images/how_it_works_matching_process.jpg" alt=""  />
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
			<img  src="images/how_it_works_match_result.jpg" alt=""  />
		</div>
	</div>

</div>

</section>

<div class="container-fluid relative data-section">

    <aside class="container main-section">
        <div class="row">

            <!-- Random user profile to select -->
            <%
          //set up our Shepherd

            Shepherd myShepherd=new Shepherd(context);
            myShepherd.setAction("index.jsp");
            myShepherd.beginDBTransaction();
            
            try{
            
            
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
	    
	            %>
	
	
	            <section class="col-xs-12 col-sm-6 col-md-4 col-lg-4 padding focusbox">
	                <div class="focusbox-inner opec">
	                    <h2>Latest turtle encounters</h2>
	                    <ul class="encounter-list list-unstyled">
	
	                       <%
	                       List<Encounter> latestIndividuals=myShepherd.getMostRecentIdentifiedEncountersByDate(3);
	                       int numResults=latestIndividuals.size();
	                       
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
		                                <p><a href="encounters/encounter.jsp?number=<%=thisEnc.getCatalogNumber() %>" title=""><%=thisEnc.getIndividual().getDisplayName() %></a></p>
	
	
		                            </li>
		                        <%
		                        }
							}
	                       catch(Exception e){e.printStackTrace();}
	              
	
	                        %>
	
	                    </ul>
	                    <a href="encounters/searchResults.jsp?state=approved" title="" class="cta"><%=props.getProperty("seeMoreEncs") %></a>
	                </div>
	            </section>
	            <section class="col-xs-12 col-sm-6 col-md-4 col-lg-4 padding focusbox">
	                <div class="focusbox-inner opec">
	                    <h2><%=props.getProperty("topSpotters")%></h2>
	                    <ul class="encounter-list list-unstyled">
	                    <%
	                    
	                    try{
		                    //System.out.println("Date in millis is:"+(new org.joda.time.DateTime()).getMillis());
	                            long startTime = System.currentTimeMillis() - Long.valueOf(1000L*60L*60L*24L*30L);
	
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
		                                    <img src="<%=profilePhotoURL %>" width="80px" height="*" alt="" class="pull-left" />
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
	        <%
            }
            catch(Exception g){
            	g.printStackTrace();
            }
            
            finally{
            	
            	myShepherd.rollbackAndClose();
            }
            
            
            
            %>
            
            
        </div>
    </aside>
</div>

<div class="container-fluid">
    <section class="container text-center  main-section">
        <div class="row">
            <section class="col-xs-12 col-sm-3 col-md-3 col-lg-3 padding">
                <p class="brand-primary"><i><span class="massive"><%=numMarkedIndividuals %></span> identified Turtles</i></p>
            </section>
            <section class="col-xs-12 col-sm-3 col-md-3 col-lg-3 padding">
                <p class="brand-primary"><i><span class="massive"><%=numEncounters %></span> reported sightings</i></p>
            </section>

            <section class="col-xs-12 col-sm-3 col-md-3 col-lg-3 padding">

                <p class="brand-primary"><i><span class="massive"><%=numDataContributors %></span> researchers and volunteers</i></p>
            </section>
        </div>

        <hr/>

        <main class="container">
        
            <article class="text-center">
                <div class="row">
                    
                    <div>
                       
                        <p class="lead"><img src="cust/mantamatcher/img/turtleWhy.png" alt="" class="pull-left" width="50%" height="50%" /> <h1><%=props.getProperty("whyWeDoThis") %></h1>
                        <p class="lead">"The 'gold standard' for sea turtle population monitoring programs are long-term capture-mark-recapture (CMR) studies on nesting
beaches as well as foraging areas for populations. Comprehensive CMR studies facilitate
robust abundance assessments and diagnoses of population trends, which, in turn, inform
effective conservation management efforts."
<br>
-<a href="https://static1.squarespace.com/static/5b80290bee1759a50e3a86b3/t/5baba504104c7bbff39ac4ad/1537975557038/SWOT_MinimumDataStandards_TechReport.pdf" target=""_blank>State of the World's Sea Turtles(SWOT) Minimum Data Standards for Nesting Beach Monitoring Technical Report</a>
</p>
                        
                    </div>
                </div>
            </article>

        </main>

    </section>
</div>

<div class="container-fluid">
    <section class="container main-section">
					<article class="text-center">
                <div class="row">
                    
                    <div>
                       
						<h1>Created with Support From</h1>
						<p><img src="cust/mantamatcher/img/Nouv_logoABF-web.png" height="50%" width="*"  />
						<img width="25%" height="*"  src="cust/mantamatcher/img/cedtm--20180418-124741.png" />
						<img width="25%" height="*"  src="cust/mantamatcher/img/ms_logo.png" />
						<img width="10%" height="*"  src="cust/mantamatcher/img/1200px-WWF_logo.svg.png" />
						<img width="20%" height="*"  src="cust/mantamatcher/img/awi_logo.svg" />
						</p>
						
                        
                    </div>
                </div>
            </article>
			</section>
			</div>

<jsp:include page="footer.jsp" flush="true"/>


