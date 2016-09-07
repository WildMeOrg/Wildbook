<%@ page contentType="text/html; charset=utf-8" language="java"
     import="org.ecocean.*,
              org.ecocean.servlet.ServletUtilities,
              java.util.ArrayList,
              java.util.List,
              java.util.Map,
              java.util.Vector,
              java.util.Iterator,
              java.util.Properties,
              java.util.StringTokenizer,
              org.datanucleus.api.rest.orgjson.JSONObject,
              org.datanucleus.api.rest.orgjson.JSONArray,
              org.joda.time.DateTime
              "
%>



<jsp:include page="header.jsp" flush="true"/>



<%



// All this fuss before the html is from individualSearchResults
String context="context0";
context=ServletUtilities.getContext(request);

Properties props = new Properties();
String langCode=ServletUtilities.getLanguageCode(request);

props = ShepherdProperties.getProperties("individuals.properties", langCode,context);

//String langCode = "en";
String urlLoc = "http://" + CommonConfiguration.getURLLocation(request);

//some sorting and filtering work
String sortString="";
if(request.getParameter("sort")!=null){
	sortString="&sort="+request.getParameter("sort");
}
//locationCodeField
String locationCodeFieldString="";
if(request.getParameter("locationCodeField")!=null){
	locationCodeFieldString="&locationCodeField="+request.getParameter("locationCodeField");
}

//props.load(getClass().getResourceAsStream("/bundles/" + langCode + "/individualSearchResults.properties"));
// range of the images being displayed

int numIndividualsOnPage=18;

int startNum = 0;
int endNum = numIndividualsOnPage;
try {
  if (request.getParameter("startNum") != null) {
    startNum = (new Integer(request.getParameter("startNum"))).intValue();
  }
  if (request.getParameter("endNum") != null) {
    endNum = (new Integer(request.getParameter("endNum"))).intValue();
  }
} catch (NumberFormatException nfe) {
  startNum = 0;
  endNum = numIndividualsOnPage;
}

int listNum = endNum;

int day1 = 1, day2 = 31, month1 = 1, month2 = 12, year1 = 0, year2 = 3000;
try {
  month1 = (new Integer(request.getParameter("month1"))).intValue();
} catch (Exception nfe) {
}
try {
  month2 = (new Integer(request.getParameter("month2"))).intValue();
} catch (Exception nfe) {
}
try {
  year1 = (new Integer(request.getParameter("year1"))).intValue();
} catch (Exception nfe) {
}
try {
  year2 = (new Integer(request.getParameter("year2"))).intValue();
} catch (Exception nfe) {
}

Shepherd myShepherd=null;
myShepherd=new Shepherd(context);

int numResults = 0;


Vector<MarkedIndividual> rIndividuals = new Vector<MarkedIndividual>();
myShepherd.beginDBTransaction();
String order ="nickName ASC NULLS LAST";

request.setAttribute("rangeStart", startNum);
request.setAttribute("rangeEnd", endNum);
MarkedIndividualQueryResult result = IndividualQueryProcessor.processQuery(myShepherd, request, order);

rIndividuals = result.getResult();




//handle any null errors better
if((rIndividuals==null)||(result.getResult()==null)){rIndividuals=new Vector<MarkedIndividual>();}

//if not logged in, filter out animals that have PublicView=no on all encounters
//if(request.getUserPrincipal()==null){
	for(int q=0;q<rIndividuals.size();q++){
		MarkedIndividual indy=rIndividuals.get(q);
		boolean ok2Include=false;
		for (Encounter enc : indy.getDateSortedEncounters()) {
		      if((enc.getDynamicPropertyValue("PublicView")==null)||(enc.getDynamicPropertyValue("PublicView").equals("Yes"))){
		    	  	if((enc.getAnnotations()!=null)&&(enc.getAnnotations().size()>0)){
		    	  		ok2Include=true;
		      		}
		      }
		}
		if(!ok2Include){rIndividuals.remove(q);q--;}
	}
//}

if (rIndividuals.size() < listNum) {
  listNum = rIndividuals.size();
}


%>




<%


//let's quickly get the data we need from Shepherd

int numMarkedIndividuals=0;
int numEncounters=0;
int numDataContributors=0;

myShepherd.beginDBTransaction();

%>

<section class="gallery hero container-fluid main-section relative">
    <div class="container-fluid relative">
        <div class="col-lg-12 bc4">
            <!--<h1 class="hidden">Wildbook</h1>-->
            <h2 class="jumboesque">Tutustu Terttuun<br/> Ja Muihin Norppiin</h2>
            <!--
            <button id="watch-movie" class="large light">
				Watch the movie
				<span class="button-icon" aria-hidden="true">
			</button>
    -->
        </div>

	</div>

</section>

<style type="text/css">
  nav.gallery-nav div ul.nav>li>a {
    color: white;
		font-family: 'UniversLTW01-59UltraCn',sans-serif;
		font-weight: 300;
		font-size: 1.6em;
  }
  /* overwrites a weird WB edge-case color */
  nav.gallery-nav div ul.nav>li>a.dropdown-toggle[aria-expanded=false]:focus {
    background-color: initial;
  }
  nav.gallery-nav div ul.nav>li.dropdown>ul.dropdown-menu>li>a {
    display: block !important;
  }

</style>

<nav class="navbar navbar-default gallery-nav">
  <div class="container-fluid">
    <ul class="nav navbar-nav" >

      <li>
    <a class="btn-link" href="gallery.jsp?sort=dateTimeLatestSighting">Uudet norpat</a>
      </li>
    <li class="dropdown">
      <a href="#" class="dropdown-toggle" data-toggle="dropdown" role="button" aria-expanded="false">Havainnot alueittain <span class="caret"></span></a>
      <ul class="dropdown-menu" role="menu">
        <li><a href="<%=urlLoc %>/gallery.jsp?locationCodeField=PS"> Pohjois-Saimaa</a></li>
        <li><a href="<%=urlLoc %>/gallery.jsp?locationCodeField=HV"> Haukivesi</a></li>
        <li><a href="<%=urlLoc %>/gallery.jsp?locationCodeField=JV"> Joutenvesi</a></li>
        <li><a href="<%=urlLoc %>/gallery.jsp?locationCodeField=PEV"> Pyyvesi – Enonvesi</a></li>
        <li><a href="<%=urlLoc %>/gallery.jsp?locationCodeField=KV"> Kolovesi</a></li>
        <li><a href="<%=urlLoc %>/gallery.jsp?locationCodeField=PV"> Pihlajavesi</a></li>
        <li><a href="<%=urlLoc %>/gallery.jsp?locationCodeField=PUV"> Puruvesi</a></li>
        <li><a href="<%=urlLoc %>/gallery.jsp?locationCodeField=KS"> Lepist&ouml;nselk&auml; – Katosselk&auml; – Haapaselk&auml;</a></li>
        <li><a href="<%=urlLoc %>/gallery.jsp?locationCodeField=LL"> Luonteri – Lietvesi</a></li>
        <li><a href="<%=urlLoc %>/gallery.jsp?locationCodeField=ES"> Etel&auml;-Saimaa</a></li>
         </ul>
    </li>

    <li>
   <a  class="btn-link" style="color: white;
								font-family: 'UniversLTW01-59UltraCn',sans-serif;
								font-weight: 300;
								font-size: 1.6em;" href="gallery.jsp?sort=numberEncounters">Kuvatuimmat norpat</a>
      </li>


    </ul>

  </div>
</nav>

<div class="container-fluid">
  <section class="container-fluid main-section front-gallery galleria">

    <%
    int numVisible = rIndividuals.size();
    int neededRows = (numVisible+1)/2;
    int numSightings = 0;

    if(request.getParameter("locationCodeField")!=null) {%>

      <style>
        .row#location-header {
          padding: 15px 15px 0px 15px;
          background: #e1e1e1;
        }
        .row#location-header h2 {
          margin-bottom: 0px;
        }
      </style>

      <div class="row" id="location-header">
      <%
      String locID = request.getParameter("locationCodeField");
      String locCode=locID.replaceAll("PS","Pohjois-Saimaa")
  	   		.replaceAll("HV","Haukivesi")
            .replaceAll("JV","Joutenvesi")
         	.replaceAll("PEV","Pyyvesi - Enonvesi")
			.replaceAll("KV","Kolovesi")
			.replaceAll("PV","Pihlajavesi")
			.replaceAll("PUV","Puruvesi")
			.replaceAll("KS","Lepist&ouml;nselk&auml; - Katosselk&auml; - Haapaselk&auml;")
			.replaceAll("LL","Luonteri – Lietvesi")
			.replaceAll("ES","Etel&auml;-Saimaa");
      numSightings = myShepherd.getNumMarkedIndividualsSightedAtLocationID(locID);

      String numVisibleDisclaimer = (numVisible!=numSightings) ? ("("+numVisible+" avoin kaikille)") : "";

      %>

        <h2><%=locCode %></h2>
        <h3><em><%=numSightings%> tunnistettua yksil&ouml;&auml; (kaikki eiv&auml;t n&auml;y galleriassa)</em></h3>
      </div>
    <% } %>

      <%
      int maxRows=(int)numIndividualsOnPage/2;
      for (int i = 0; i < neededRows && i < maxRows; i++) {
        %>
        <div class="row gunit-row">
        <%
        MarkedIndividual[] pair = new MarkedIndividual[2];
        if((i*2)<numVisible && rIndividuals.get(i*2)!=null){
        	pair[0]=rIndividuals.get(i*2);
        }
        if((i*2+1)<numVisible && rIndividuals.get(i*2+1)!=null){
        	pair[1]=rIndividuals.get(i*2+1);
        }

        String[] pairUrl = new String[2];
        String[] pairName = new String[2];
        String[] pairNickname = new String[2];
        String[] pairCopyright = new String[2];
        String[] pairMediaAssetID = new String[2];

        // construct a panel showing each individual
        for (int j=0; j<2; j++) {
        	if(pair[j]!=null){
          MarkedIndividual indie = pair[j];
          ArrayList<JSONObject> al = indie.getExemplarImages(request);
          JSONObject maJson=new JSONObject();
          if(al.size()>0){maJson=al.get(0);}
          pairCopyright[j] =
          maJson.optString("photographer");
          if ((pairCopyright[j]!=null)&&!pairCopyright[j].equals("")) {
            pairCopyright[j] =  "&copy; " +pairCopyright[j]+" / WWF";
          } else {
            pairCopyright[j] = "&copy; WWF";
          }
          pairMediaAssetID[j]=maJson.optString("id");
          pairUrl[j] = maJson.optString("url", urlLoc+"/cust/mantamatcher/img/hero_manta.jpg");
          pairName[j] = indie.getIndividualID();
          pairNickname[j] = pairName[j];
          if (!indie.getNickName().equals("Unassigned") && indie.getNickName()!=null && !indie.getNickName().equals("")) pairNickname[j] = indie.getNickName();
          %>
          <div class="col-xs-6">
            <div class="gallery-unit" id="gunit<%=i*2+j%>">
              <div class="crop" title="<%=pairName[j]%>">
                <img src="<%=pairUrl[j]%>" id="<%=pairName[j]%>" alt="<%=pairNickname[j]%>" />
                <p class="image-copyright"> <%=pairCopyright[j]%> </p>
              </div>
              <p><strong><%=pairNickname[j]%></strong></p>
            </div>
          </div>
          <div id="arrow<%=i*2+j%>" class="arrow-up <%=(j==0) ? "left" : "right"%> " style="display: none"></div>
          <%
        }
        }
        %>
        </div>
        <div class="row">
        <%
        // now a second row containing each individual's info panel (hidden at first)
        for (int j=0; j<2; j++) {
          if(pair[j]==null) continue;
          %>
          <div class="col-sm-12 gallery-info" id="ginfo<%=i*2+j%>" style="display: none">


            <div class="gallery-inner">
              <div class="super-crop seal-gallery-pic active">
                <div class="crop">
                  <img src="<%=pairUrl[j]%>" id="<%=pairName[j]%>" alt="<%=pairNickname[j]%>" />
                  <p class="image-copyright"> <%=pairCopyright[j]%> </p>
                </div>
              </div>
              <%
              // display=none copies of the above for each additional image
              ArrayList<JSONObject> al = pair[j].getExemplarImages(request);
              for (int extraImgNo=1; extraImgNo<al.size(); extraImgNo++) {
                JSONObject newMaJson = new JSONObject();
                newMaJson = al.get(extraImgNo);
                String newUrl = newMaJson.optString("url", urlLoc+"/cust/mantamatcher/img/hero_manta.jpg");

                String copyright = newMaJson.optString("photographer");
                if ((copyright!=null)&&!copyright.equals("")) {
                  copyright =  "&copy; " +copyright+" / WWF";
                } else {
                  copyright = "&copy; WWF";
                }



                %>
                <div class="super-crop seal-gallery-pic">
                  <div class="crop">
                    <img src="<%=newUrl%>" id="<%=pairName[j]%>" alt="<%=pairNickname[j]%>" />
                    <p class="image-copyright"> <%=copyright%> </p>
                    <script>console.log("<%=pairName[j]%>: added extra image <%=newUrl%>");</script>
                  </div>
                </div>
                <%
              }
              %>

              <img class="seal-scroll scroll-back" border="0" alt="" src="<%=urlLoc%>/cust/mantamatcher/img/wwf-blue-arrow-left.png"/>

              <img class="seal-scroll scroll-fwd" border="0" alt="" src="<%=urlLoc%>/cust/mantamatcher/img/wwf-blue-arrow-right.png"/>






              <span class="galleryh2"><%=pairNickname[j]%></span>
              <span style="font-size:1.5rem;color:#999;text-align:right;float:right;margin-top:4px;bottom:0;">
                <%
                String imageURL=pairUrl[j].replaceAll(":", "%3A").replaceAll("/", "%2F").replaceFirst("52.40.15.8", "norppagalleria.wwf.fi");
                String shareTitle=CommonConfiguration.getHTMLTitle(context)+": "+pairName[j];
                if(pairNickname[j]!=null){shareTitle=CommonConfiguration.getHTMLTitle(context)+": "+pairNickname[j];}
                %>

                <a href="https://www.facebook.com/sharer/sharer.php?u=http://norppagalleria.wwf.fi/gallery.jsp&title=<%=shareTitle %>&endorseimage=http://norppagalleria.wwf.fi/images/image_for_sharing_individual.jpg" title="Jaa Facebookissa" class="btnx" target="_blank" rel="external" >
                	<i class="icon icon-facebook-btn" aria-hidden="true"></i>
                </a>

                <a target="_blank" rel="external" href="http://twitter.com/intent/tweet?status=WWF:n Norppagalleriassa voit tutustua kaikkiin tunnistettuihin saimaannorppiin. +http://norppagalleria.wwf.fi/"><i class="icon icon-twitter-btn" aria-hidden="true"></i></a>
                <a target="_blank" rel="external" href="https://plus.google.com/share?url=http://norppagalleria.wwf.fi/gallery.jsp"><i class="icon icon-google-plus-btn" aria-hidden="true"></i></a>
              </span>
              <table><tr>
                <td>
                  <ul>
                    <li>
                      <%=props.getProperty("individualID")%>: <%=pairName[j]%>
                    </li>
                    <li>
                      <%=props.getProperty("nickname")%>: <%=pairNickname[j]%>
                    </li>
                    <li>
                      <%
                        String sexValue = pair[j].getSex();
                        if (sexValue.equals("male") || sexValue.equals("female") || sexValue.equals("unknown")) {sexValue=props.getProperty(sexValue);}
                      %>
                      <%=props.getProperty("sex")%> <%=sexValue%>
                    </li>
                  </ul>
                </td>
                <td>
                  <ul>
                    <li>
                      <%
                      String timeOfBirth=props.getProperty("unknown");
                      //System.out.println("Time of birth is: "+sharky.getTimeOfBirth());
                      if(pair[j].getTimeOfBirth()>0){
                      	String timeOfBirthFormat="yyyy-MM-d";
                      	timeOfBirth=(new DateTime(pair[j].getTimeOfBirth())).toString(timeOfBirthFormat);
                      }
                      %>
                      <%=props.getProperty("birthdate")%>: <%=timeOfBirth%>
                    </li>

                      <%
                      String timeOfDeath=props.getProperty("unknown");
                      //System.out.println("Time of birth is: "+sharky.getTimeOfBirth());
                      if(pair[j].getTimeofDeath()>0){
                      	String timeOfDeathFormat="yyyy-MM-d";
                      	timeOfDeath=(new DateTime(pair[j].getTimeofDeath())).toString(timeOfDeathFormat);
                      	%>
                      	<li>
                      		<%=props.getProperty("deathdate")%>: <%=timeOfDeath%>
                    	</li>
                      	<%
                      }
                      %>

                    <li>
                      <%=props.getProperty("numencounters")%>: <%=pair[j].totalEncounters()%>
                    </li>
                     <li>
                     <%
                     String wheres="";
                     ArrayList<String> locIDs=pair[j].participatesInTheseLocationIDs();
                     int numLocIDs=locIDs.size();
                     for(int q=0;q<numLocIDs;q++){
                    	 wheres=wheres+locIDs.get(q).replaceAll("PS","Pohjois-Saimaa")
                    	  	   		.replaceAll("HV","Haukivesi")
                    	            .replaceAll("JV","Joutenvesi")
                    	         	.replaceAll("PEV","Pyyvesi - Enonvesi")
                    				.replaceAll("KV","Kolovesi")
                    				.replaceAll("PV","Pihlajavesi")
                    				.replaceAll("PUV","Puruvesi")
                    				.replaceAll("KS","Lepist&ouml;nselk&auml; - Katosselk&auml; - Haapaselk&auml;")
                    				.replaceAll("LL","Luonteri – Lietvesi")
                    				.replaceAll("ES","Etel&auml;-Saimaa");
                     }
                     if(wheres.endsWith(",")){wheres = wheres.substring(0, wheres.length()-1);}
                     %>
                      Alue: <%=wheres%>
                    </li>
                    <%
                    String yearRange="";
                    if(pair[j].getEarliestSightingYear()<5000){
                    	int earlyYear=pair[j].getEarliestSightingYear();
                    	yearRange=""+earlyYear;
                    	if((pair[j].getDateSortedEncounters()[0].getYear()>0)&&(pair[j].getDateSortedEncounters()[0].getYear()!=earlyYear)){
                    		yearRange=yearRange+"-"+pair[j].getDateSortedEncounters()[0].getYear();
                    	}
                    }
                    %>
                    <li>Havaittu vuosina: <%=yearRange %></li>
                    
                  </ul>
                </td>
              </tr></table>

              <% if(request.getUserPrincipal()!=null){ %>
              <p style="text-align:right; padding-right: 10px; padding-right:1.5rem">
                To see more, go <a href="<%=urlLoc%>/individuals.jsp?number=<%=pairName[j]%>">here</a>.
              </p>
              <% } %>

            </div>
          </div>
          <%
        }
        %>
        </div>
        <%
      }

      %>
      <div class="row" style="background:#e1e1e1;">

        <p style="text-align:center">

          <%
          if (startNum>0) {
            int newStart = Math.max(startNum-numIndividualsOnPage,0);
            %>
            <a href="<%=urlLoc%>/gallery.jsp?startNum=<%=newStart%>&endNum=<%=newStart+numIndividualsOnPage%><%=sortString %><%=locationCodeFieldString %>"> <img border="0" alt="" src="<%=urlLoc%>/cust/mantamatcher/img/wwf-blue-arrow-left.png"> </a> &nbsp;&nbsp;&nbsp;&nbsp;
            <%
          }
          %>

          Lataa lis&auml;&auml; norppia &nbsp;&nbsp;&nbsp;&nbsp;

          <a href= "<%=urlLoc%>/gallery.jsp?startNum=<%=endNum%>&endNum=<%=endNum+numIndividualsOnPage%><%=sortString %><%=locationCodeFieldString %>"> <img border="0" alt="" src="<%=urlLoc%>/cust/mantamatcher/img/wwf-blue-arrow-right.png"/></a>
        </p>

      </row>

  </section>
</div>

<%
myShepherd.closeDBTransaction();
myShepherd=null;
%>




<script src="<%=urlLoc %>/javascript/galleryFuncs.js"></script>
<script src="<%=urlLoc %>/javascript/imageCropper.js"></script>
<script>

  $( window ).resize(function(){
    imageCropper.cropGridPics();
    imageCropper.cropInnerPics();
  });

  // handles gallery-info hiding/showing
  $('.gallery-unit').click( function() {
    var thisId = this.id.split('gunit')[1];
    var target = '#ginfo'+thisId;
    var targetArrow = '#arrow'+thisId;
    if ($(target).hasClass('active')) {
      $(target).slideToggle(800, function() {
        $(targetArrow).hide(0, function() {
          $(target).removeClass('active');
          $(targetArrow).removeClass('active');
        });
      });
    }
    else {
      var currentPosition=''
      $('.gallery-info.active').hide(0, function() {
        $('.gallery-info.active').removeClass('active');
        $('div.arrow-up.active').hide(0, function() {
          $('div.arrow-up.active').removeClass('active');
        })
      });
      $(targetArrow).toggle(0, function() {
        $(targetArrow).addClass('active');
        $(target).slideToggle(800);
        $(target).addClass('active');
        imageCropper.cropInnerPics();
      })
    }
  });

  $('.seal-scroll.scroll-fwd').click( function() {
    console.log('beginning scroll logic');
    var $active = $(this).siblings('.seal-gallery-pic.active');
    var $next = imageCropper.nextWrap($active, '.seal-gallery-pic');
    $active.toggle(0);
    $next.toggle(0, function () {
      console.log("next is toggled!");
      $active.removeClass('active');
      $next.addClass('active');
    });
  });

  $('.seal-scroll.scroll-back').click( function() {
    console.log('beginning scroll logic');
    var $active = $(this).siblings('.seal-gallery-pic.active');
    var $prev = imageCropper.prevWrap($active, '.seal-gallery-pic');
    $active.toggle(0);
    $prev.toggle(0, function () {
      console.log("next is toggled!");
      $active.removeClass('active');
      $prev.addClass('active');
    });
  })



  // a little namespace for gallery functions
  var gallery = {};

</script>

<script src="cust/mantamatcher/js/google_maps_style_vars.js"></script>
<script src="cust/mantamatcher/js/richmarker-compiled.js"></script>


<style>
  section.main-section.galleria div.row.gunit-row {
    background:#e1e1e1;
    padding-top:15px;
  }

  .gunit-row {
    position: relative;
  }

  .gallery-info {
    background: #4a494a;
    padding: 15px;
  }
  .gallery-info h2 {
    color: #16696d;
  }
  .gallery-info table {
    width: 100%;
  }
  .gallery-info td {
    width:50%;
  }

  .gallery-unit .crop, .gallery-inner .crop {
    text-align: center;
    overflow: hidden;
  }

  // seal-scrolling css
  .gallery-inner {
    position: relative;
  }
  .seal-scroll {
    cursor:pointer;
    position: absolute;
    top: 40%;
  }
  .seal-scroll.scroll-fwd {
    right: 10%;
  }
  .seal-scroll.scroll-back {
    left: 10%;
  }

  .super-crop.seal-gallery-pic {
    display: none;
  }
  .super-crop.seal-gallery-pic.active {
    display: block;
  }

  p.image-copyright {
    	text-align: right;
    	position: absolute;
    	top: 5px;
    	right: 25px;
    	color: #fff;
    	font-size: 0.8rem;
  }
  .gallery-inner p.image-copyright {
    top: 30px;
    right: 110px;
  }
  @media(max-width: 768px) {
    .gallery-unit p.image-copyright {
      display: none;
    }
    .gallery-inner p.image-copyright {
      right: 35px;
    }
  }


  .galleryh2 {
    color: #16696d !important;
    font-weight: 700 !important;
    font-size: 36px !important;
    line-height: 1.3em !important;
    font-family: "UniversLTW01-59UltraCn",Helvetica,Arial,sans-serif !important;
    padding: 27px;
  }


  .gallery-inner {
    background: #fff;
    padding: 10px;
  }

  @media(min-width: 768px) {
    .gallery-inner {
      margin-left: 75px;
      margin-right: 75px;
    }
  }

  .gallery-inner img {
    display: block;
    margin: auto;
  }
  .gallery-nav {
    margin-bottom: 0;
  }
  div.arrow-up {
  	width: 0;
  	height: 0;
  	border-left: 15px solid transparent;  /* left arrow slant */
  	border-right: 15px solid transparent; /* right arrow slant */
  	border-bottom: 15px solid #4a494a; /* bottom, add background color here */
  	font-size: 0;
  	line-height: 0;
    position: absolute;
    bottom: 0;
  }
  div.arrow-up.left {
    left: 25%;
  }
  div.arrow-up.right {
    left: 75%;
  }


  .gallery-unit {
    cursor: pointer;
    cursor: hand;
  }

</style>




<jsp:include page="footer.jsp" flush="true"/>
