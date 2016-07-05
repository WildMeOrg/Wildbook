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
String order ="";
//if(request.getParameter("sort")!=null){
//	order=request.getParameter("sort");
//}

request.setAttribute("rangeStart", startNum);
request.setAttribute("rangeEnd", endNum);
MarkedIndividualQueryResult result = IndividualQueryProcessor.processQuery(myShepherd, request, order);

rIndividuals = result.getResult();

//handle any null errors better
if((rIndividuals==null)||(result.getResult()==null)){rIndividuals=new Vector<MarkedIndividual>();}

if (rIndividuals.size() < listNum) {
  listNum = rIndividuals.size();
}


%>


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



<%


//let's quickly get the data we need from Shepherd

int numMarkedIndividuals=0;
int numEncounters=0;
int numDataContributors=0;

myShepherd.beginDBTransaction();

%>



<div class="container maincontent">
<h1><%=props.getProperty("gallery") %></h1>
<nav class="navbar navbar-default gallery-nav">
  <div class="container-fluid">
    <button type="button" class="btn-link"><a href="gallery.jsp?sort=dateTimeLatestSighting"><%=props.getProperty("recentSightings") %></a></button>

    <button type="button" class="btn-link"><a href="gallery.jsp?sort=numberLocations"><%=props.getProperty("mostTraveled") %></a></button>

    <button type="button" class="btn-link"><a href="gallery.jsp?sort=numberEncounters"><%=props.getProperty("mostSightings") %></a></button>

  </div>
</nav>

  <section class="container-fluid main-section front-gallery galleria">


    <% if(request.getParameter("locationCodeField")!=null) {%>

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
      String locCode=request.getParameter("locationCodeField")
       		.replaceAll("PS","Pohjois-Saimaa")
  	   		.replaceAll("HV","Haukivesi")
            .replaceAll("JV","Joutenvesi")
         	.replaceAll("PEV","Pyyvesi - Enonvesi")
			.replaceAll("KV","Kulovesi")
			.replaceAll("PV","Pihlajavesi")
			.replaceAll("PUV","Puruvesi")
			.replaceAll("KS","Lepist&ouml;nselk&auml; - Katosselk&auml; - Haapaselk&auml;")
			.replaceAll("LL","Luonteri – Lietvesi")
			.replaceAll("ES","Etel&auml;-Saimaa");
      %>
      
        <h2><%=locCode %></h2>
      </div>
    <% } %>

      <%
      int maxRows=(int)numIndividualsOnPage/2;
      for (int i = 0; i < rIndividuals.size()/2 && i < maxRows; i++) {
        %>
        <div class="row gunit-row">
        <%
        MarkedIndividual[] pair = new MarkedIndividual[2];
        if(rIndividuals.get(i*2)!=null){
        	pair[0]=rIndividuals.get(i*2);
        }
        if(rIndividuals.get(i*2)!=null){
        	pair[1]=rIndividuals.get(i*2+1);
        }

        String[] pairUrl = new String[2];
        String[] pairName = new String[2];
        String[] pairNickname = new String[2];
        String[] pairCopyright = new String[2];
        // construct a panel showing each individual
        for (int j=0; j<2; j++) {
        	if(pair[j]!=null){
          MarkedIndividual indie = pair[j];
          JSONObject maJson = indie.getExemplarImage(request);
          pairCopyright[j] = indie.getExemplarPhotographer();
          if ((pairCopyright[j]!=null)&&!pairCopyright[j].equals("")) {
            pairCopyright[j] =  "&copy; " +pairCopyright[j];
          }
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
          %>
          <div class="col-sm-12 gallery-info" id="ginfo<%=i*2+j%>" style="display: none">


            <div class="gallery-inner">
              <div class="super-crop">
                <div class="crop">
                  <img src="<%=pairUrl[j]%>" id="<%=pairName[j]%>" alt="<%=pairNickname[j]%>" />
                  <p class="image-copyright"> <%=pairCopyright[j]%> </p>
                </div>
              </div>


              <span class="galleryh2"><%=pairNickname[j]%></span>
              <span style="font-size:1.5rem;color:#999;text-align:right;float:right;margin-top:4px;bottom:0;">
                <a href=#><i class="icon icon-facebook-btn" aria-hidden="true"></i></a>
                <a href=#><i class="icon icon-twitter-btn" aria-hidden="true"></i></a>
                <a href=#><i class="icon icon-google-plus-btn" aria-hidden="true"></i></a>
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
                        if (sexValue.equals("male") || sexValue.equals("female")) {sexValue=props.getProperty(sexValue);}
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
                      <li>
                      <%
                      String timeOfDeath=props.getProperty("unknown");
                      //System.out.println("Time of birth is: "+sharky.getTimeOfBirth());
                      if(pair[j].getTimeofDeath()>0){
                      	String timeOfDeathFormat="yyyy-MM-d";
                      	timeOfDeath=(new DateTime(pair[j].getTimeofDeath())).toString(timeOfDeathFormat);
                      }
                      %>
                      <%=props.getProperty("deathdate")%>: <%=timeOfDeath%>
                    </li>
                    <li>
                      <%=props.getProperty("numencounters")%>: <%=pair[j].totalEncounters()%>
                    </li>
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
            int newStart = Math.min(startNum-numIndividualsOnPage,0);
            %>
            <a href="<%=urlLoc%>/gallery.jsp?startNum=<%=newStart%>&endNum=<%=newStart+numIndividualsOnPage%>"> <img border="0" alt="" src="<%=urlLoc%>/cust/mantamatcher/img/wwf-blue-arrow-left.png"> </a> &nbsp;&nbsp;&nbsp;&nbsp;
            <%
          }
          %>

          <a href= "<%=urlLoc%>/gallery.jsp?startNum=<%=endNum%>&endNum=<%=endNum+numIndividualsOnPage%>"> <img border="0" alt="" src="<%=urlLoc%>/cust/mantamatcher/img/wwf-blue-arrow-right.png"/></a>
        </p>

      </div>

  </section>
</div>

<%
myShepherd.closeDBTransaction();
myShepherd=null;
%>


<script>

  // little namespace for gallery funcs
  var galFunc = {};

  galFunc.cropPics = function(selector, ratio) {
    var image_width = $( selector ).parent().width();
    var desired_height = image_width * 1.0/ratio;
    $( selector ).height(desired_height);
    $( selector+' img').css('min-height', desired_height.toString()+'px');

    // center image vertically
    $( selector+' img').each(function(index, value) {
      var vertical_offset = ($(this).height() - desired_height)/2.0;
      $(this).css('margin-top','-'+vertical_offset.toString()+'px');
    });

    $( selector+' img').width('100%');
  };

  galFunc.cropInnerPics = function() {
    galFunc.cropPics('.gallery-info.active .gallery-inner .crop', 16.0/9);
  };


  galFunc.cropGridPics = function() {
    galFunc.cropPics('.gallery-unit .crop', 16.0/9);
  };


  $( document ).ready(function() {
    galFunc.cropGridPics();
  });
  $( window ).resize(function(){
    galFunc.cropGridPics();
    galFunc.cropInnerPics();
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
        galFunc.cropInnerPics();
      })
    }
  });

  // a little namespace for gallery functions
  var gallery = {};

</script>


<jsp:include page="footer.jsp" flush="true"/>
