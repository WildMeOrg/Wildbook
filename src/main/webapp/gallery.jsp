<%@ page contentType="text/html; charset=utf-8" language="java"
     import="org.ecocean.*,
              org.ecocean.servlet.ServletUtilities,
	      org.ecocean.media.MediaAsset,
              java.util.ArrayList,
              java.util.List,
              java.util.Map,
              java.util.Vector,
              java.util.Iterator,
              java.util.Properties,
              java.util.StringTokenizer,
              org.datanucleus.api.rest.orgjson.JSONObject,
              org.datanucleus.api.rest.orgjson.JSONArray,
              org.joda.time.DateTime,
              java.util.Collection,
              javax.jdo.*
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
String urlLoc = "//" + CommonConfiguration.getURLLocation(request);

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

Shepherd myShepherd=new Shepherd(context);
myShepherd.setAction("gallery.jsp");


int numResults = 0;


Vector<MarkedIndividual> rIndividuals = new Vector<MarkedIndividual>();


myShepherd.beginDBTransaction();

int count = myShepherd.getNumAdoptions();
int allSharks = myShepherd.getNumMarkedIndividuals();
int countAdoptable = allSharks - count;

if(request.getParameter("adoptableSharks")!=null){
	//get current time minus two years
	Long twoYears=new Long("63072000000");
	long currentDate=System.currentTimeMillis()-twoYears.longValue();
    String filter="SELECT FROM org.ecocean.MarkedIndividual WHERE encounters.contains(enc) && (enc.dateInMilliseconds >= "+currentDate+") && ((nickName == null)||(nickName == '')) VARIABLES org.ecocean.Encounter enc";
    Query query=myShepherd.getPM().newQuery(filter);
    query.setOrdering("numberEncounters descending");
    query.setRange(startNum, endNum);
    Collection c = (Collection) (query.execute());
	rIndividuals=new Vector<MarkedIndividual>(c);
    query.closeAll();
    if(rIndividuals==null){rIndividuals=new Vector<MarkedIndividual>();}
}
else{
	String order ="nickName ASC NULLS LAST";

	request.setAttribute("rangeStart", startNum);
	request.setAttribute("rangeEnd", endNum);
	MarkedIndividualQueryResult result = IndividualQueryProcessor.processQuery(myShepherd, request, order);

	rIndividuals = result.getResult();

	//handle any null errors better
	if((rIndividuals==null)||(result.getResult()==null)){rIndividuals=new Vector<MarkedIndividual>();}

}



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



<%


//let's quickly get the data we need from Shepherd

int numMarkedIndividuals=0;
int numEncounters=0;
int numDataContributors=0;

//myShepherd.beginDBTransaction();

%>

<div class="container maincontent">
<h1><%=props.getProperty("gallery") %></h1>
<nav class="navbar navbar-default gallery-nav">
  <div class="container-fluid">
    <button type="button" class="btn-link"><a href="gallery.jsp?sort=dateTimeLatestSighting"><%=props.getProperty("recentSightings") %></a></button>

    <button type="button" class="btn-link"><a href="gallery.jsp?sort=numberLocations"><%=props.getProperty("mostTraveled") %></a></button>

    <button type="button" class="btn-link"><a href="gallery.jsp?sort=numberEncounters"><%=props.getProperty("mostSightings") %></a></button>

    <button type="button" class="btn-link"><a href="gallery.jsp?adoptableSharks=true"><%=props.getProperty("adoptableSharks") %></a></button>

  </div>
</nav>

<div class="container-fluid">
  <section class="container-fluid main-section front-gallery galleria">

  <% if (request.getParameter("adoptableSharks")!=null) { %>
    <h3><%=props.getProperty("numAdoptable").replaceAll("%NUM%", (new Integer(countAdoptable)).toString()) %></h3>
    <p><%=props.getProperty("adoptOne") %> <strong><a href="adoptashark.jsp"><%=props.getProperty("learnMore") %></a></strong></p>
  <% } %>

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
			.replaceAll("KV","Kolovesi")
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
        String[] pairMediaAssetID = new String[2];
        // construct a panel showing each individual
        for (int j=0; j<2; j++) {
        	if(pair[j]!=null){
          MarkedIndividual indie = pair[j];
          for (Encounter enJ : indie.getDateSortedEncounters()) {
            for (MediaAsset maJ : enJ.getMedia()) {
              if (maJ.getMetadata() != null) maJ.getMetadata().getDataAsString();
            }
          }
          ArrayList<JSONObject> al = indie.getExemplarImages(request);
          JSONObject maJson=new JSONObject();
          if(al.size()>0){maJson=al.get(0);}
          pairCopyright[j] =
          maJson.optString("photographer");
          if ((pairCopyright[j]!=null)&&!pairCopyright[j].equals("")) {
            pairCopyright[j] =  "&copy; " +pairCopyright[j];
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
                <img class="lazyload" src="cust/mantamatcher/img/individual_placeholder_image.jpg" data-src="<%=pairUrl[j]%>" id="<%=pairName[j]%>" alt="<%=pairNickname[j]%>" />
                <%
                if(pairCopyright[j]!=null){
               	%>
                	<p class="image-copyright"> <%=pairCopyright[j]%> </p>
                <%
                }
                %>
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
              <div class="super-crop seal-gallery-pic active">
                <div class="crop">
                  <img class="lazyload" src="cust/mantamatcher/img/individual_placeholder_image.jpg" data-src="<%=pairUrl[j]%>" id="<%=pairName[j]%>" alt="<%=pairNickname[j]%>" />
                  <%
                  if(pairCopyright[j]!=null){
               	  %>
                	<p class="image-copyright"> <%=pairCopyright[j]%> </p>
                  <%
                  }
                  %>
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
                    <img class="lazyload" src="cust/mantamatcher/img/individual_placeholder_image.jpg" data-src="<%=newUrl%>" id="<%=pairName[j]%>" alt="<%=pairNickname[j]%>" />
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

                <a href="https://www.facebook.com/sharer/sharer.php?u=<%=urlLoc %>/gallery.jsp&title=<%=shareTitle %>&endorseimage=<%=urlLoc %>/images/image_for_sharing_individual.jpg" title="Wildbook" class="btnx" target="_blank" rel="external" >
                	<i class="icon icon-facebook-btn" aria-hidden="true"></i>
                </a>

                <a target="_blank" rel="external" href="http://twitter.com/intent/tweet?status=<%=shareTitle %>+<%=urlLoc %>/gallery.jsp"><i class="icon icon-twitter-btn" aria-hidden="true"></i></a>
                <a target="_blank" rel="external" href="https://plus.google.com/share?url=<%=urlLoc %>/gallery.jsp"><i class="icon icon-google-plus-btn" aria-hidden="true"></i></a>
              </span>
              <table><tr>
                <td>
                  <p>
                    <%=props.getProperty("individualID")%>: <%=pairName[j]%>
                  </p>
                  <p>
                    <%=props.getProperty("nickname")%>: <%=pairNickname[j]%>
                  </p>
                  <p>
                    <%
                      String sexValue = pair[j].getSex();
                      if (sexValue.equals("male") || sexValue.equals("female") || sexValue.equals("unknown")) {sexValue=props.getProperty(sexValue);}
                    %>
                    <%=props.getProperty("sex")%> <%=sexValue%>
                  </p>
                </td>
                <td>
                  <p>
                    <%=props.getProperty("numencounters")%>: <%=pair[j].totalEncounters()%>
                  </p>
                  <div class="gallery-btn-group">
                  <%
                  if(CommonConfiguration.allowAdoptions(context)){
                  %>
                    <a href="<%=urlLoc%>/createadoption.jsp?number=<%=pairName[j]%>"><button class="large adopt"><%=props.getProperty("adoptMe") %><span class="button-icon" aria-hidden="true"></button></a>
                  <%
                  }
                  %>  
                    <a href="<%=urlLoc%>/individuals.jsp?number=<%=pairName[j]%>"><button class="large adopt"><%=props.getProperty("viewProfile") %><span class="button-icon" aria-hidden="true"></button></a>
                  </div>
                </td>
              </tr></table>
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

          <%=props.getProperty("seeMore") %>

&nbsp;&nbsp;&nbsp;&nbsp;<a href= "<%=urlLoc%>/gallery.jsp?startNum=<%=endNum%>&endNum=<%=endNum+numIndividualsOnPage%><%=sortString %><%=locationCodeFieldString %>"> <img border="0" alt="" src="<%=urlLoc%>/cust/mantamatcher/img/wwf-blue-arrow-right.png"/></a>
        </p>

      </row>

  </section>
</div>
</div>

<%
myShepherd.rollbackDBTransaction();
myShepherd.closeDBTransaction();
myShepherd=null;
%>




<script src="<%=urlLoc %>/javascript/galleryFuncs.js"></script>
<script src="<%=urlLoc %>/javascript/imageCropper.js"></script>
<script>

  imageCropper.cropPicsGalleryPage = function() {
    console.log("========Cropping Gallery Pictures=======");
    imageCropper.cropGridPics();
    imageCropper.cropInnerPics();
  }

  $( "img.lazyloaded:eq(  )" ).load(function() {
    console.log('WRONG lazyload image loaded');
    //imageCropper.cropPicsGalleryPage();
  });

  $( "img:last" ).load(function() {
    console.log('lazyloadED image loaded');
    //imageCropper.cropPicsGalleryPage();
  })

  var nIndividuals = <%=numIndividualsOnPage%>;
  console.log("individuals per page = "+nIndividuals);
  var lastIndiv = nIndividuals-1;

  imageCropper.testCrop = function() {
    console.log("LAST IMAGE LOADED");
    imageCropper.cropPicsGalleryPage();
    $(window).trigger('resize');
    $(window).trigger('resize');
    imageCropper.cropPicsGalleryPage();
    console.log("I'm not sure this will print");
  }

  $( "img:eq("+lastIndiv+")").load( imageCropper.testCrop() );



/*  $( window ).load(function() {
    console.log("loaded; waiting!");
    setTimeout(imageCropper.cropPicsGalleryPage, 1000);
  })*/


  $( window ).resize(function(){
    imageCropper.cropPicsGalleryPage();
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


<jsp:include page="footer.jsp" flush="true"/>
