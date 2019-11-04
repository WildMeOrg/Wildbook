<%@ page contentType="text/html; charset=utf-8" language="java" %>
<%@ page import="java.text.NumberFormat" %>
<%@ page import="java.util.*" %>
<%@ page import="org.ecocean.*" %>
<%@ page import="org.ecocean.servlet.ServletUtilities" %>
<%@ page import="org.ecocean.cache.*" %>

<jsp:include page="header.jsp" flush="true"/>

<%
	String context = ServletUtilities.getContext(request);
	String langCode = ServletUtilities.getLanguageCode(request);
	Locale locale = new Locale(langCode);
	NumberFormat nf = NumberFormat.getIntegerInstance(locale);
	Properties props = ShepherdProperties.getProperties("index.properties", langCode, context);
	Map<String, String> locMap = CommonConfiguration.getIndexedValuesMap("locationID", context);

//set up our Shepherd

Shepherd myShepherd=null;
myShepherd=new Shepherd(context);

	//check for and inject a default user 'tomcat' if none exists

	//check usernames and passwords
	myShepherd.beginDBTransaction();

try {
    StartupWildbook.ensureTomcatUserExists(myShepherd);
} catch (Exception e) {
    e.printStackTrace();
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
    



        //numMarkedIndividuals=myShepherd.getNumMarkedIndividuals();
        numMarkedIndividuals=qc.getQueryByName("numMarkedIndividuals").executeCountQuery(myShepherd).intValue();
        numEncounters=myShepherd.getNumEncounters();
        //numEncounters=qc.getQueryByName("numEncounters", context).executeCountQuery(myShepherd).intValue();
        //numDataContributors=myShepherd.getAllUsernamesWithRoles().size();
        numDataContributors=qc.getQueryByName("numUsersWithRoles").executeCountQuery(myShepherd).intValue();
        numUsers=qc.getQueryByName("numUsers").executeCountQuery(myShepherd).intValue();
        numUsersWithRoles = numUsers-numDataContributors;


    }
    catch(Exception e){
        e.printStackTrace();
    }

    

%>

<section class="hero container-fluid main-section relative">
    <div class="container relative">
        <div class="col-xs-12 col-sm-10 col-md-8 col-lg-6">
            <h1 class="hidden">MantaMatcher</h1>
            <h2><%=props.getProperty("mainStrapline")%></h2>
            <!--
            <button id="watch-movie" class="large light">
				Watch the movie 
				<span class="button-icon" aria-hidden="true">
			</button>
			-->
            <a href="submit.jsp">
                <button class="large"><%=props.getProperty("buttonReport")%><span class="button-icon" aria-hidden="true"></button>
            </a>
            <br>
            <a href="adoptamanta.jsp">
                <button class="large heroBtn">Adopt a Manta<span class="button-icon" aria-hidden="true"></button>
            </a>
            <br>
        </div>

	</div>

    
</section>

<section class="container text-center main-section">
	
	<h2 class="section-header"><%=props.getProperty("howItWorks-title")%></h2>

	<div id="howtocarousel" class="carousel slide" data-ride="carousel">
		<ol class="list-inline carousel-indicators slide-nav">
	        <li data-target="#howtocarousel" data-slide-to="0" class="active">1. <%=props.getProperty("howItWorks-step1")%><span class="caret"></span></li>
	        <li data-target="#howtocarousel" data-slide-to="1" class="">2. <%=props.getProperty("howItWorks-step2")%><span class="caret"></span></li>
	        <li data-target="#howtocarousel" data-slide-to="2" class="">3. <%=props.getProperty("howItWorks-step3")%><span class="caret"></span></li>
	        <li data-target="#howtocarousel" data-slide-to="3" class="">4. <%=props.getProperty("howItWorks-step4")%><span class="caret"></span></li>
	        <li data-target="#howtocarousel" data-slide-to="4" class="">5. <%=props.getProperty("howItWorks-step5")%><span class="caret"></span></li>
	    </ol> 
		<div class="carousel-inner text-left">
			<div class="item active">
				<div class="col-xs-12 col-sm-6 col-md-6 col-lg-6">
					<h3><%=props.getProperty("howItWorks-step1")%></h3>
					<p class="lead">
						<%=props.getProperty("howItWorks-step1-text")%>
					</p>
					<p class="lead">
						<a href="photographing.jsp" title=""><%=props.getProperty("howItWorks-step1-link")%></a>
					</p>
				</div>
				<div class="col-xs-12 col-sm-4 col-sm-offset-2 col-md-4 col-md-offset-2 col-lg-4 col-lg-offset-2">
					<img class="pull-right" src="images/how_it_works_bellyshot_of_manta.jpg" alt=""  />
				</div>
			</div>
			<div class="item">
				<div class="col-xs-12 col-sm-6 col-md-6 col-lg-6">
					<h3><%=props.getProperty("howItWorks-step2")%></h3>
					<p class="lead">
						<%=props.getProperty("howItWorks-step2-text")%>
					</p>
				</div>
				<div class="col-xs-12 col-sm-4 col-sm-offset-2 col-md-4 col-md-offset-2 col-lg-4 col-lg-offset-2">
					<img class="pull-right" src="images/how_it_works_submit.jpg" alt=""  />
				</div>
			</div>
			<div class="item">
				<div class="col-xs-12 col-sm-6 col-md-6 col-lg-6">
					<h3><%=props.getProperty("howItWorks-step3")%></h3>
					<p class="lead">
						<%=props.getProperty("howItWorks-step3-text")%>
					</p>
				</div>
				<div class="col-xs-12 col-sm-4 col-sm-offset-2 col-md-4 col-md-offset-2 col-lg-4 col-lg-offset-2">
					<img class="pull-right" src="images/how_it_works_researcher_verification.jpg" alt=""  />
				</div>
			</div>
			<div class="item">
				<div class="col-xs-12 col-sm-6 col-md-6 col-lg-6">
					<h3><%=props.getProperty("howItWorks-step4")%></h3>
					<p class="lead">
						<%=props.getProperty("howItWorks-step4-text")%>
					</p>
				</div>
				<div class="col-xs-12 col-sm-4 col-sm-offset-2 col-md-4 col-md-offset-2 col-lg-4 col-lg-offset-2">
					<img class="pull-right" src="images/how_it_works_matching_process.jpg" alt=""  />
				</div>
			</div>
			<div class="item">
				<div class="col-xs-12 col-sm-6 col-md-6 col-lg-6">
					<h3><%=props.getProperty("howItWorks-step5")%></h3>
					<p class="lead">
						<%=props.getProperty("howItWorks-step5-text")%>
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
            //myShepherd.beginDBTransaction();
            User featuredUser=myShepherd.getRandomUserWithPhotoAndStatement();
            if(featuredUser!=null){
                String profilePhotoURL="images/empty_profile.jpg";
                if(featuredUser.getUserImage()!=null){
                	profilePhotoURL="/"+CommonConfiguration.getDataDirectoryName(context)+"/users/"+featuredUser.getUsername()+"/"+featuredUser.getUserImage().getFilename();
                } 
            
            %>
                <section class="col-xs-12 col-sm-6 col-md-4 col-lg-4 padding focusbox">
                    <div class="focusbox-inner opec">
                        <h2><%=props.getProperty("contributors")%></h2>
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
                        <a href="whoAreWe.jsp" title="" class="cta"><%=props.getProperty("contributors-linkText")%></a>
                    </div>
                </section>
            <%
            }
            //myShepherd.rollbackDBTransaction();
            %>
            
            
            <section class="col-xs-12 col-sm-6 col-md-4 col-lg-4 padding focusbox">
                <div class="focusbox-inner opec">
                    <h2><%=props.getProperty("latestEncounters")%></h2>
                    <ul class="encounter-list list-unstyled">
                       
                       <%
                       ArrayList<Encounter> latestIndividuals=myShepherd.getMostRecentIdentifiedEncountersByDate(3);
                       int numResults=latestIndividuals.size();
                       //myShepherd.beginDBTransaction();
                       for(int i=0;i<numResults;i++){
                           Encounter thisEnc=latestIndividuals.get(i);
                           %>
                            <li>
                                <img src="cust/mantamatcher/img/manta-silhouette.svg" alt="" width="85px" height="75px" class="pull-left" />
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
                        //myShepherd.rollbackDBTransaction();
                        %>
                       
                    </ul>
                    <a href="encounters/searchResults.jsp?state=approved" title="" class="cta"><%=props.getProperty("latestEncounters-more")%></a>
                </div>
            </section>
            <section class="col-xs-12 col-sm-6 col-md-4 col-lg-4 padding focusbox">
                <div class="focusbox-inner opec">
                    <h2><%=props.getProperty("topSpotters_30")%></h2>
                    <ul class="encounter-list list-unstyled">
                    <%
                    //myShepherd.beginDBTransaction();
                    
                    //System.out.println("Date in millis is:"+(new org.joda.time.DateTime()).getMillis());
                    long startTime=(new org.joda.time.DateTime()).getMillis()+(1000*60*60*24*30);
                    
                    System.out.println("  I think my startTime is: "+startTime);
                    
                    Map<String,Integer> spotters = myShepherd.getTopUsersSubmittingEncountersSinceTimeInDescendingOrder(startTime);
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
                   //myShepherd.rollbackDBTransaction();
                   %>
                        
                    </ul>   
                    <a href="whoAreWe.jsp" title="" class="cta"><%=props.getProperty("allSpotters")%></a>
                </div>
            </section>
        </div>
    </aside>
</div>

<div class="container-fluid">
    <section class="container text-center  main-section">
        <div class="row">
            <section class="col-xs-12 col-sm-4 col-md-4 col-lg-4 padding">
                <p class="brand-primary"><i><span class="massive"><%=nf.format(numMarkedIndividuals)%></span> <%=props.getProperty("statText-identified")%></i></p>
            </section>
            <section class="col-xs-12 col-sm-4 col-md-4 col-lg-4 padding">
                <p class="brand-primary"><i><span class="massive"><%=nf.format(numEncounters)%></span> <%=props.getProperty("statText-encounters")%></i></p>
            </section>
            <section class="col-xs-12 col-sm-4 col-md-4 col-lg-4 padding">
                
                <p class="brand-primary"><i><span class="massive"><%=nf.format(numDataContributors)%></span> <%=props.getProperty("statText-contributors")%></i></p>
            </section>
        </div>

        <hr/>

        <main class="container">
            <article class="text-center">
                <div class="row">
                    <img src="cust/mantamatcher/img/why-we-do-this.png" alt="" class="pull-left col-xs-7 col-sm-4 col-md-4 col-lg-4 col-xs-offset-2 col-sm-offset-1 col-md-offset-1 col-lg-offset-1" />
                    <div class="col-xs-12 col-sm-6 col-md-6 col-lg-6 text-left">
                        <h1><%=props.getProperty("whyWeDoThis-title")%></h1>
                        <p class="lead">
													<%=props.getProperty("whyWeDoThis-text")%>
                        </p>
                        <a href="overview.jsp" title=""><%=props.getProperty("whyWeDoThis-more")%></a>
                    </div>
                </div>
            </article>
        <main>
        
    </section>
</div>


<div class="container-fluid">
    <section class="container main-section">
        <h2 class="section-header"><%=props.getProperty("help-title")%></h2>
        <p class="lead text-center"><%=props.getProperty("help-text")%></p>

        <section class="adopt-section row">
            <div class=" col-xs-12 col-sm-6 col-md-6 col-lg-6">
                <h3 class="uppercase"><%=props.getProperty("help-adopt-title")%></h3>
                <ul>
                    <li><%=props.getProperty("help-adopt-text1")%></li>
                    <li><%=props.getProperty("help-adopt-text2")%></li>
                    <li><%=props.getProperty("help-adopt-text3")%></li>
                </ul>
                <a href="adoptamanta.jsp" title="<%=props.getProperty("help-adopt-linkText")%>"><%=props.getProperty("help-adopt-linkText")%></a>
            </div>
            <%
            //myShepherd.beginDBTransaction();
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
            //myShepherd.rollbackDBTransaction();
            %>
            
            
        </section>
        <hr />
        <section class="donate-section">
            <div class="col-xs-12 col-sm-6 col-md-6 col-lg-6">
                <h3><%=props.getProperty("help-donate-title")%></h3>
                <p><%=props.getProperty("help-donate-text")%></p>
                <a href="adoptamanta.jsp" title="<%=props.getProperty("help-donate-linkText")%>"><%=props.getProperty("help-donate-linkText")%></a>
            </div>
            <div class="col-xs-12 col-sm-5 col-md-5 col-lg-5 col-sm-offset-1 col-md-offset-1 col-lg-offset-1">
                <a href="adoptamanta.jsp">
	                <button class="large contrast">
	                    <%=props.getProperty("help-donate-title")%>
	                    <span class="button-icon" aria-hidden="true">
	                </button>
                </a>
            </div>
        </section>
    </section>
</div>


<jsp:include page="footer.jsp" flush="true"/>

<%
myShepherd.rollbackDBTransaction();
myShepherd.closeDBTransaction();
myShepherd=null;
%>
