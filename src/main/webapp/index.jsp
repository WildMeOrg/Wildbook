<%@ page contentType="text/html; charset=utf-8" language="java"
     import="org.ecocean.*,
              org.ecocean.servlet.ServletUtilities,
              java.util.ArrayList,
              java.util.Map,
              java.util.Iterator
              "
%>



<jsp:include page="header2.jsp" flush="true"/>



<script src="cust/mantamatcher/js/google_maps_style_vars.js"></script>


<%

String context=ServletUtilities.getContext(request);

//let's quickly get the data we need from Shepherd

int numMarkedIndividuals=0;
int numEncounters=0;
int numDataContributors=0;
Shepherd myShepherd=null;

try{
    myShepherd=new Shepherd(context);
    myShepherd.beginDBTransaction();
    
    numMarkedIndividuals=myShepherd.getNumMarkedIndividuals();
    numEncounters=myShepherd.getNumEncounters();
    numDataContributors=myShepherd.getNumUsers();

    
}
catch(Exception e){
    e.printStackTrace();
}
finally{
    if(myShepherd!=null){
        if(myShepherd.getPM()!=null){
            myShepherd.rollbackDBTransaction();
            if(!myShepherd.getPM().isClosed()){myShepherd.closeDBTransaction();}
        }
    }
}
%>

<section class="hero container-fluid main-section relative">
    <div class="container relative">
        <div class="col-xs-12 col-sm-10 col-md-8 col-lg-6">
            <h1 class="hidden">Manta Matcher</h1>
            <h2>You can help photograph, <br/> identify and protect mantas!</h2>
            <button id="watch-movie" class="large light">
				Watch the movie 
				<span class="button-icon" aria-hidden="true">
			</button>
            <a href="submit.jsp">
                <button class="large">Report encounter<span class="button-icon" aria-hidden="true"></button>
            </a>
        </div>

	</div>
	 <div class="video-wrapper">
		<div class="embed-container">
			<iframe id="herovideo" src="http://player.vimeo.com/video/123083341?api=1&amp;player_id=herovideo" frameborder="0" webkitAllowFullScreen mozallowfullscreen allowFullScreen></iframe>
		</div>
	</div>
    
</section>

<section class="container text-center main-section">
    
    <h2 class="section-header">How it works</h2>

    <div id="howtocarousel" class="carousel slide" data-ride="carousel">
        <ol class="list-inline carousel-indicators slide-nav">
            <li data-target="#howtocarousel" data-slide-to="0" class="active">1. Photograph a manta<span class="caret"></span></li>
            <li data-target="#howtocarousel" data-slide-to="1" class="">2. Submit photo/video<span class="caret"></span></li>
            <li data-target="#howtocarousel" data-slide-to="2" class="">3. Researcher verification<span class="caret"></span></li>
            <li data-target="#howtocarousel" data-slide-to="3" class="">4. Matching process<span class="caret"></span></li>
            <li data-target="#howtocarousel" data-slide-to="4" class="">5. Match result<span class="caret"></span></li>
        </ol> 
        <div class="carousel-inner text-left">
            <div class="item active">
                <div class="col-xs-12 col-sm-6 col-md-6 col-lg-6">
                    <h3>Photograph the belly of a manta</h3>
                    <p class="lead">
                        Each manta has an individual fingerprint: the pattern of spots on its belly. Get an image or video of their “bellyprint" and we can match that pattern to mantas already in the database, or your manta might be completely new to the database.</p>
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
                        You can upload files from your computer, or take them directly from your Flickr or Facebook account. Be sure to enter when and where you saw the manta, and add other information, such as species or sex, if you can. You will receive email updates when your manta is processed by a researcher.</p>
                </div>
                <div class="col-xs-12 col-sm-4 col-sm-offset-2 col-md-4 col-md-offset-2 col-lg-4 col-lg-offset-2">
                    <img class="pull-right" src="cust/mantamatcher/img/bellyshotofmanta.jpg" alt=""  />
                </div>
            </div>
            <div class="item">
                <div class="col-xs-12 col-sm-6 col-md-6 col-lg-6">
                    <h3>Researcher verfication</h3>
                    <p class="lead">
                        When you submit a manta identification photo, a local researcher receives a notification. This researcher will double check that the information you submitted is correct (so don’t worry if you are unsure about which species you saw!). </p>
                </div>
                <div class="col-xs-12 col-sm-4 col-sm-offset-2 col-md-4 col-md-offset-2 col-lg-4 col-lg-offset-2">
                    <img class="pull-right" src="cust/mantamatcher/img/bellyshotofmanta.jpg" alt=""  />
                </div>
            </div>
            <div class="item">
                <div class="col-xs-12 col-sm-6 col-md-6 col-lg-6">
                    <h3>Matching process</h3>
                    <p class="lead">
                        Once a researcher is happy with all the data accompanying the identification photo, they will run the Manta Matcher algorithm. The algorithm is like facial recognition software for manta bellies. </p>
                </div>
                <div class="col-xs-12 col-sm-4 col-sm-offset-2 col-md-4 col-md-offset-2 col-lg-4 col-lg-offset-2">
                    <img class="pull-right" src="cust/mantamatcher/img/bellyshotofmanta.jpg" alt=""  />
                </div>
            </div>
            <div class="item">
                <div class="col-xs-12 col-sm-6 col-md-6 col-lg-6">
                    <h3>Match Result</h3>
                    <p class="lead">
                        The algorithm provides researchers with a ranked selection of possible matches. Researchers will then visually confirm a match to an existing manta in the database, or create a new manta profile. </p>
                </div>
                <div class="col-xs-12 col-sm-4 col-sm-offset-2 col-md-4 col-md-offset-2 col-lg-4 col-lg-offset-2">
                    <img class="pull-right" src="cust/mantamatcher/img/bellyshotofmanta.jpg" alt=""  />
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
            User featuredUser=myShepherd.getRandomUserWithPhotoAndStatement();
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
                        <a href="whoAreWe.jsp" title="" class="cta">Show me all the contributers</a>
                    </div>
                </section>
            <%
            }
            myShepherd.rollbackDBTransaction();
            %>
            
            
            <section class="col-xs-12 col-sm-6 col-md-4 col-lg-4 padding focusbox">
                <div class="focusbox-inner opec">
                    <h2>Latest manta encounters</h2>
                    <ul class="encounter-list list-unstyled">
                       
                       <%
                       ArrayList<Encounter> latestIndividuals=myShepherd.getMostRecentIdentifiedEncountersByDate(3);
                       int numResults=latestIndividuals.size();
                       myShepherd.beginDBTransaction();
                       for(int i=0;i<numResults;i++){
                           Encounter thisEnc=latestIndividuals.get(i);
                           %>
                            <li>
                                <img src="cust/mantamatcher/img/manta-silhouette.svg" alt="" class="pull-left" />
                                <small>
                                    <time>
                                        <%=thisEnc.getDate() %>
                                        <%
                                        if((thisEnc.getLocation()!=null)&&(!thisEnc.getLocation().trim().equals(""))){
                                        %>/ <%=thisEnc.getLocation() %>
                                        <%
                                           }
                                        %>
                                    </time>
                                </small>
                                <p><a href="encounters/encounter.jsp?number=<%=thisEnc.getCatalogNumber() %>" title=""><%=thisEnc.getIndividualID() %></a></p>
                           
                           
                            </li>
                        <%
                        }
                        myShepherd.rollbackDBTransaction();
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
                    myShepherd.beginDBTransaction();
                    
                    System.out.println("Date in millis is:"+(new org.joda.time.DateTime()).getMillis());
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
                                    <img src="<%=profilePhotoURL %>" width="80px" height="*" alt="" class="pull-left" />
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
                    }    }
                   myShepherd.rollbackDBTransaction();
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
            <section class="col-xs-12 col-sm-4 col-md-4 col-lg-4 padding">
                <p class="brand-primary"><i><span class="massive"><%=numMarkedIndividuals %></span> identified individuals</i></p>
            </section>
            <section class="col-xs-12 col-sm-4 col-md-4 col-lg-4 padding">
                <p class="brand-primary"><i><span class="massive"><%=numEncounters %></span> reported encounters</i></p>
            </section>
            <section class="col-xs-12 col-sm-4 col-md-4 col-lg-4 padding">
                
                <p class="brand-primary"><i><span class="massive"><%=numDataContributors %></span> contributors</i></p>
            </section>
        </div>

        <hr/>

        <main class="container">
            <article class="text-center">
                <div class="row">
                    <img src="cust/mantamatcher/img/why-we-do-this.png" alt="" class="pull-left col-xs-7 col-sm-4 col-md-4 col-lg-4 col-xs-offset-2 col-sm-offset-1 col-md-offset-1 col-lg-offset-1" />
                    <div class="col-xs-12 col-sm-6 col-md-6 col-lg-6 text-left">
                        <h1>Why we do this</h1>
                        <p class="lead">
                            <i>"Manta Matcher will revolutionize global research on these threatened rays and help change the way field researchers approach scientific research on wild animals by means of successful and meaningful public involvement.”</i> - Andrea Marshall</p>
                        <a href="#" title="">I want to know more</a>
                    </div>
                </div>
            </article>
        <main>
        
    </section>
</div>

<div class="container-fluid main-section">
    <h2 class="section-header">Encounters around the world</h2>
    <div id="map-canvas"></div>
    <div>
        GOOGLE MAPS INTEGRATION
    </div>
</div>

<div class="container-fluid">
    <section class="container main-section">
        <h2 class="section-header">How can i Help out of the water</h2>
        <p class="lead text-center">If you are not getting into the blue, there are still other ways to get engaged</p>

        <section class="adopt-section row">
            <div class=" col-xs-12 col-sm-6 col-md-6 col-lg-6">
                <h3 class="uppercase">Adopt a manta</h3>
                <ul>
                    <li>Support individual research programs in different regions</li>
					<li>Receive email updates when we resight your adopted manta</li>
					<li>Display your photo and a quote on the manta's page in our database</li>
</ul>
                <a href="adoptamanta.jsp" title="">Learn more about adopting a manta</a>
            </div>
            <div class="adopter-badge focusbox col-xs-12 col-sm-6 col-md-6 col-lg-6">
                <div class="focusbox-inner">
                    <img src="cust/mantamatcher/img/adopter_casaberry.jpg" alt="" class="pull-right round">
                    <h2><small>Meet an adopter:</small>Casa Berry</h2>
                    <blockquote>
                        “We just fell in love with the manta Angel. She made us interested in mantas, and made us relise their importance in the eco syste
                    </blockquote>
                </div>
            </div>
        </section>
        <hr />
        <section class="donate-section">
            <div class="col-xs-12 col-sm-6 col-md-6 col-lg-6">
                <h3>Donate</h3>
                <p>Donations, including in-kind, large or small, are always welcome. Your support helps the continued development of MantaMatcher and can support effective, science-based conservation management, and safeguard these rays.</p>
                <a href="#" title="More information about donations">Learn more about how to donate</a>
            </div>
            <div class="col-xs-12 col-sm-5 col-md-5 col-lg-5 col-sm-offset-1 col-md-offset-1 col-lg-offset-1">
                <button class="large contrast">
                    Become a sponsor
                    <span class="button-icon" aria-hidden="true">
                </button>
            </div>
        </section>
    </section>
</div>


<jsp:include page="footer2.jsp" flush="true"/>

<%
myShepherd.closeDBTransaction();
myShepherd=null;
%>
