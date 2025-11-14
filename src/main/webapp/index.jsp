<%@ page contentType="text/html; charset=utf-8" language="java"
     import="org.ecocean.*,
              org.ecocean.servlet.ServletUtilities,
              java.util.ArrayList,
              java.util.HashMap,
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
//int numEncounters=0;
int numSightings=0;
int numDataContributors=0;
int numUsersWithRoles=0;
int numUsers=0;
myShepherd.beginDBTransaction();
QueryCache qc=QueryCacheFactory.getQueryCache(context);

//String url = "login.jsp";
//response.sendRedirect(url);
//RequestDispatcher dispatcher = getServletContext().getRequestDispatcher(url);
//dispatcher.forward(request, response);

long pageStartTime = System.currentTimeMillis();
long operationStartTime = 0;

try{
    operationStartTime = System.currentTimeMillis();
    //numMarkedIndividuals=myShepherd.getNumMarkedIndividuals();
    numMarkedIndividuals=qc.getQueryByName("numMarkedIndividuals").executeCountQuery(myShepherd).intValue();
    System.out.println("[PERF] index.jsp: numMarkedIndividuals query took " + (System.currentTimeMillis() - operationStartTime) + " ms");
    
    //operationStartTime = System.currentTimeMillis();
    //numEncounters=myShepherd.getNumEncounters();
    //System.out.println("[PERF] index.jsp: getNumEncounters() took " + (System.currentTimeMillis() - operationStartTime) + " ms");
    
    operationStartTime = System.currentTimeMillis();
    numSightings=myShepherd.getNumOccurrences();
    System.out.println("[PERF] index.jsp: getNumOccurrences() took " + (System.currentTimeMillis() - operationStartTime) + " ms");
    
    //numEncounters=qc.getQueryByName("numEncounters").executeCountQuery(myShepherd).intValue();
    //numDataContributors=myShepherd.getAllUsernamesWithRoles().size();
    operationStartTime = System.currentTimeMillis();
    numDataContributors=qc.getQueryByName("numUsersWithRoles").executeCountQuery(myShepherd).intValue();
    System.out.println("[PERF] index.jsp: numUsersWithRoles query took " + (System.currentTimeMillis() - operationStartTime) + " ms");
    
    operationStartTime = System.currentTimeMillis();
    numUsers=qc.getQueryByName("numUsers").executeCountQuery(myShepherd).intValue();
    System.out.println("[PERF] index.jsp: numUsers query took " + (System.currentTimeMillis() - operationStartTime) + " ms");
    
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


<section class="hero container-fluid main-section relative main-background">
    
</section>



<section class="container text-center main-section">

	<h2 class="section-header"><%=props.getProperty("howItWorksH") %></h2>

  	<p class="lead"><%=props.getProperty("howItWorksHDescription") %></p>

  	<!-- <h3 class="section-header"><%=props.getProperty("howItWorks1") %></h3> -->
  	<p class="lead"><%=props.getProperty("howItWorks1Description") %></p>
  	<img width="500px" height="*" style="max-width: 100%;" height="*" class="lazyload" src="cust/mantamatcher/img/puppy_with_big_ears.JPG" data-src="images/index_detection.jpg" />

  	<!--
  	<h3 class="section-header"><%=props.getProperty("howItWorks2") %></h3>
  	<p class="lead"><%=props.getProperty("howItWorks2Description") %></p>
  	<img width="500px" height="*" style="max-width: 100%;" height="*" class="lazyload" src="cust/mantamatcher/img/puppy_with_big_ears.JPG" data-src="cust/mantamatcher/img/leopard_howitworks2.jpg" />


	<h3 class="section-header"><%=props.getProperty("howItWorks4") %></h3>
  	<p class="lead"><%=props.getProperty("howItWorks4Description") %></p>
  	<img width="500px" height="*" style="max-width: 100%;" height="*" class="lazyload" src="cust/mantamatcher/img/puppy_with_big_ears.JPG" data-src="cust/mantamatcher/img/puppy_with_big_ears.JPG" />
    -->

</section>

<div class="container-fluid relative data-section">

    <aside class="container main-section">
        <!-- Empty row - Top Spotters moved to full-width section below -->
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
                <p class="brand-primary"><i><span class="massive"><%=numSightings %></span> <%=props.getProperty("reportedSightings") %></i></p>
            </section>

            <%
			if(numUsersWithRoles>0){
			%>
            <section class="col-xs-12 col-sm-3 col-md-3 col-lg-3 padding">

                <p class="brand-primary"><i><span class="massive"><%=numUsersWithRoles %></span> <%=props.getProperty("citizenScientists") %></i></p>
            </section>

            <%
}

            if(numDataContributors>0){

            %>
            <section class="col-xs-12 col-sm-3 col-md-3 col-lg-3 padding">

                <p class="brand-primary"><i><span class="massive"><%=numDataContributors %></span> <%=props.getProperty("researchVolunteers") %></i></p>
            </section>

            <%
            }
            %>
        </div>

        <hr/>

    </section>
</div>

<!-- Top Spotters (Past 30 Days) - Full Width Section -->
<div class="container-fluid relative data-section">
    <section class="container main-section">
        <h2 class="section-header text-center"><%=props.getProperty("topSpotters")%></h2>
        
        <%
        // Get top 12 spotters from last 30 days
        try{
            long startTime = System.currentTimeMillis() - Long.valueOf(1000L*60L*60L*24L*30L);
            operationStartTime = System.currentTimeMillis();
            Map<String,Integer> spotters = myShepherd.getTopUsersSubmittingEncountersSinceTimeInDescendingOrder(startTime);
            System.out.println("[PERF] index.jsp: getTopUsersSubmittingEncountersSinceTimeInDescendingOrder() took " + (System.currentTimeMillis() - operationStartTime) + " ms");
            
            // Collect up to 12 valid users
            ArrayList<Map<String,Object>> validSpotters = new ArrayList<Map<String,Object>>();
            Iterator<String> keys=spotters.keySet().iterator();
            Iterator<Integer> values=spotters.values().iterator();
            
            while(keys.hasNext() && validSpotters.size() < 12){
                String spotter=keys.next();
                int numUserEncs=values.next().intValue();
                if(!spotter.equals("siowamteam") && !spotter.equals("admin") && !spotter.equals("tomcat") && myShepherd.getUser(spotter)!=null){
                    User thisUser=myShepherd.getUser(spotter);
                    String profilePhotoURL="images/user-profile-white-transparent.png";
                    if(thisUser.getUserImage()!=null){
                        profilePhotoURL="/"+CommonConfiguration.getDataDirectoryName(context)+"/users/"+thisUser.getUsername()+"/"+thisUser.getUserImage().getFilename();
                    }
                    
                    Map<String,Object> spotterData = new HashMap<String,Object>();
                    spotterData.put("user", thisUser);
                    spotterData.put("username", spotter);
                    spotterData.put("encounters", numUserEncs);
                    spotterData.put("photo", profilePhotoURL);
                    validSpotters.add(spotterData);
                }
            }
            
            // Split into 3 columns
            int col1End = Math.min(4, validSpotters.size());
            int col2End = Math.min(8, validSpotters.size());
            int col3End = Math.min(12, validSpotters.size());
        %>
        
        <div class="row">
            <!-- Column 1: Top 1-4 -->
            <% if(col1End > 0){ %>
            <section class="col-xs-12 col-sm-4 col-md-4 col-lg-4 padding focusbox">
                <div class="focusbox-inner opec">
                    <ul class="encounter-list list-unstyled">
                    <%
                    for(int i=0; i<col1End; i++){
                        Map<String,Object> data = validSpotters.get(i);
                        User thisUser = (User)data.get("user");
                        String spotter = (String)data.get("username");
                        int numUserEncs = (Integer)data.get("encounters");
                        String profilePhotoURL = (String)data.get("photo");
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
                            <p><a href="#" title=""><%=spotter %></a>, <span><%=numUserEncs %> <%=props.getProperty("encounters") %></span></p>
                        </li>
                    <%
                    }
                    %>
                    </ul>
                </div>
            </section>
            <% } %>
            
            <!-- Column 2: Top 5-8 -->
            <% if(col2End > col1End){ %>
            <section class="col-xs-12 col-sm-4 col-md-4 col-lg-4 padding focusbox">
                <div class="focusbox-inner opec">
                    <ul class="encounter-list list-unstyled">
                    <%
                    for(int i=col1End; i<col2End; i++){
                        Map<String,Object> data = validSpotters.get(i);
                        User thisUser = (User)data.get("user");
                        String spotter = (String)data.get("username");
                        int numUserEncs = (Integer)data.get("encounters");
                        String profilePhotoURL = (String)data.get("photo");
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
                            <p><a href="#" title=""><%=spotter %></a>, <span><%=numUserEncs %> <%=props.getProperty("encounters") %></span></p>
                        </li>
                    <%
                    }
                    %>
                    </ul>
                </div>
            </section>
            <% } %>
            
            <!-- Column 3: Top 9-12 -->
            <% if(col3End > col2End){ %>
            <section class="col-xs-12 col-sm-4 col-md-4 col-lg-4 padding focusbox">
                <div class="focusbox-inner opec">
                    <ul class="encounter-list list-unstyled">
                    <%
                    for(int i=col2End; i<col3End; i++){
                        Map<String,Object> data = validSpotters.get(i);
                        User thisUser = (User)data.get("user");
                        String spotter = (String)data.get("username");
                        int numUserEncs = (Integer)data.get("encounters");
                        String profilePhotoURL = (String)data.get("photo");
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
                            <p><a href="#" title=""><%=spotter %></a>, <span><%=numUserEncs %> <%=props.getProperty("encounters") %></span></p>
                        </li>
                    <%
                    }
                    %>
                    </ul>
                </div>
            </section>
            <% } %>
        </div>
        
        <%
        }
        catch(Exception e){e.printStackTrace();}
        %>
        
    </section>
</div>

<div class="container-fluid">
    <section class="container text-center main-section">

        <main class="container">
            <article class="text-center">
                <div class="row">
                    <img src="cust/mantamatcher/img/tico_meeting.jpg" data-src="cust/mantamatcher/img/tico_meeting.jpg" alt="Tico McNutt Quote Image" class="pull-left col-xs-7 col-sm-4 col-md-4 col-lg-4 col-xs-offset-2 col-sm-offset-1 col-md-offset-1 col-lg-offset-1 lazyload" />

<div class="col-xs-12 col-sm-6 col-md-6 col-lg-6 text-left">
                        <h1><%=props.getProperty("whyWeDoThis") %></h1>
                        <p class="lead">
                            <i>“Iconic, rare, spectacular and fearsome, Africa's large predators are among our planet's most revered - and most threatened. Knowledge about these predators' population connectivity is critical to mapping the strategy to ensure their future and the health of the ecosystems Africans and Africa's wildlife depend on. The ACW is the tool that will enable conservationists and wildlife ecologists to document that critical connectivity.”</i><br>- JW McNutt, <i>PhD., Director, Wild Entrust; Founder and Director, Botswana Predator Conservation</i></p>

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
                operationStartTime = System.currentTimeMillis();
	            Adoption adopt=myShepherd.getRandomAdoptionWithPhotoAndStatement();
                System.out.println("[PERF] index.jsp: getRandomAdoptionWithPhotoAndStatement() took " + (System.currentTimeMillis() - operationStartTime) + " ms");
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
System.out.println("[PERF] index.jsp: Total page load time: " + (System.currentTimeMillis() - pageStartTime) + " ms");
%>
