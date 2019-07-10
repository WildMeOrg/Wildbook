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
		org.ecocean.media.MediaAsset,
              org.joda.time.DateTime,
              javax.jdo.Query,
              java.util.Collection,java.util.HashMap,
              org.datanucleus.api.rest.orgjson.JSONException,
              java.net.URL,
              org.ecocean.cache.*
              "
%>

<%!
public ArrayList<org.datanucleus.api.rest.orgjson.JSONObject> getExemplarImages(Shepherd myShepherd, MarkedIndividual indy,HttpServletRequest req, int numResults, QueryCache qc) throws JSONException {
    //System.out.println("here!");
    long time1=System.currentTimeMillis();
	ArrayList<org.datanucleus.api.rest.orgjson.JSONObject> al=new ArrayList<org.datanucleus.api.rest.orgjson.JSONObject>();
    //boolean haveProfilePhoto=false;
    //String jdoql="SELECT FROM org.ecocean.media.MediaAsset WHERE enc.individualID == \""+indy.getIndividualID()+"\" && (enc.dynamicProperties == null || enc.dynamicProperties == \"\" || enc.dynamicProperties.toLowerCase().indexOf(\"publicview=no\") == -1) && enc.annotations.contains(annot) && annot.mediaAsset == this VARIABLES org.ecocean.Annotation annot;org.ecocean.Encounter enc ORDER BY enc.dwcDateAddedLong DESC RANGE 0, "+numResults;
     String jdoql="SELECT FROM org.ecocean.Annotation WHERE enc.individualID == \""+indy.getIndividualID()+"\" && (enc.dynamicProperties == null || enc.dynamicProperties.toLowerCase().indexOf(\"publicview=no\") == -1) && enc.annotations.contains(this) VARIABLES org.ecocean.Encounter enc ORDER BY enc.dwcDateAddedLong DESC RANGE 0, "+numResults;
    
    //System.out.println(jdoql);
    Vector<Annotation> assets=new Vector<Annotation>();


	    Query query=myShepherd.getPM().newQuery(jdoql);
	    //query.setOrdering(order);
	    //query.setRange(0, (numResults));
	    Collection c2 = (Collection) (query.execute());
	    assets=new Vector<Annotation>(c2);
	    query.closeAll();

	    
	    //System.out.println("here2 with assets="+assets.size()+" for indy "+indy.getIndividualID()+" after query: "+jdoql);
	    
		//String photographerName="Bob";
	    
	    
	        for (Annotation ann: assets) {
	          //if (!ann.isTrivial()) continue;
	          //System.out.println("Here3!");
			  MediaAsset ma=ann.getMediaAsset();
	          //if (ma != null) {
	            //JSONObject j = new JSONObject();
	            JSONObject j = ma.sanitizeJson(req, new JSONObject(),myShepherd);
	
	            String context = ServletUtilities.getContext(req);
	
	            URL u = ma.safeURL(myShepherd, req, "halfpage");
	
	            ////////// hacky temporary until all converted to have halfpage /////////////
	            if ((u == null) || (u.toString().indexOf("halfpage") < 0)) u = ma.webURL();
	
	            j.put("urlDisplay", ((u == null) ? "" : u.toString()));
	
	            //now we need a mid (if we have it)
	            ArrayList<MediaAsset> kids = ma.findChildrenByLabel(myShepherd, "_mid");
	            if ((kids != null) && (kids.size() > 0) && (kids.get(0).webURL() != null)) {
	                j.put("urlMid", kids.get(0).webURL().toString());
	                //System.out.println("Found a mid for: "+indy.getIndividualID());
	            } else {
	                j.put("urlMid", ((u == null) ? "" : u.toString()));  //we reuse urlDisplay value :/
	            }
	
				/*
	            if ((j!=null) && (photographerName!=null) && (!photographerName.equals(""))) {
	              j.put("photographer",photographerName);
	            }
				*/
	
	            if ((j!=null)&&(ma.getMimeTypeMajor()!=null)&&(ma.getMimeTypeMajor().equals("image"))) {
	
	
	              //ok, we have a viable candidate
	
	              //put ProfilePhotos at the beginning
	              if(ma.hasKeyword("ProfilePhoto")){
	            	  al.add(0, j);
	            	  //System.out.println("Found a ProfilePhoto for: "+indy.getIndividualID());
	            	}
	              //do nothing and don't include it if it has NoProfilePhoto keyword
	              else if(ma.hasKeyword("NoProfilePhoto")){
	            	  //System.out.println("Found a NoProfilePhoto for: "+indy.getIndividualID());
	              }
	              //otherwise, just add it to the bottom of the stack
	              else{
	                al.add(j);
	                //System.out.println("Found a regular photo for: "+indy.getIndividualID());
	              }
	
	            }
	
	
	          //}
	          if(al.size()==numResults){return al;}
	        }
	        long time2=System.currentTimeMillis();
	        System.out.println("getExemplar time: "+(time2-time1));
	    return al;

    
    
    

  }
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

int numIndividualsOnPage=10;

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


QueryCache qc=QueryCacheFactory.getQueryCache(context);

Shepherd myShepherd=null;
myShepherd=new Shepherd(context);
myShepherd.setAction("gallery.jsp");

int numResults = 0;


Vector<MarkedIndividual> rIndividuals = new Vector<MarkedIndividual>();
myShepherd.beginDBTransaction();
String order ="nickName ASC NULLS LAST";

request.setAttribute("rangeStart", startNum);
request.setAttribute("rangeEnd", endNum);
long time1=System.currentTimeMillis();
StringBuffer prettyPrint=new StringBuffer("");
Map<String,Object> paramMap = new HashMap<String, Object>();
String ssjdo=IndividualQueryProcessor.queryStringBuilder(request, prettyPrint, paramMap);
//ssjdo=ssjdo.replaceFirst("WHERE", "WHERE enc.dynamicProperties.indexOf(\"PublicView=Yes\") > -1 &&  enc.annotations.size() > 0 && ");
ssjdo=ssjdo.replaceFirst("WHERE", "WHERE (enc.dynamicProperties == null || enc.dynamicProperties.toLowerCase().indexOf(\"publicview=no\") == -1) &&  enc.annotations.size() > 0 && ");

Query query=myShepherd.getPM().newQuery(ssjdo);
query.setOrdering(order);
query.setRange(startNum, endNum);
Collection c2 = (Collection) (query.execute());
rIndividuals=new Vector<MarkedIndividual>(c2);
query.closeAll();

//old way -- slow?

//MarkedIndividualQueryResult result = IndividualQueryProcessor.processQuery(myShepherd, request, order);
//rIndividuals = result.getResult();



//handle any null errors better
//if((rIndividuals==null)||(result.getResult()==null)){rIndividuals=new Vector<MarkedIndividual>();}



if (rIndividuals.size() < listNum) {
  listNum = rIndividuals.size();
}


%>




<%

long time2=System.currentTimeMillis();
//let's quickly get the data we need from Shepherd

int numMarkedIndividuals=0;
int numEncounters=0;
int numDataContributors=0;

//myShepherd.beginDBTransaction();

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

/* just plain hide these now */
	.image-copyright {
		display: none;
	}


</style>

<nav class="navbar navbar-default gallery-nav">
  <div class="container-fluid">
    <ul class="nav navbar-nav" >

      <li>
    <a class="btn-link" href="gallery.jsp?sort=dateTimeLatestSighting">Uudet havainnot</a>
      </li>
    <li class="dropdown">
      <a href="#" class="dropdown-toggle" data-toggle="dropdown" role="button" aria-expanded="false">Havainnot alueittain <span class="caret"></span></a>
      <ul class="dropdown-menu" role="menu">
        <li><a href="<%=urlLoc %>/gallery.jsp?locationCodeField=PS" rel="nofollow"> Pohjois-Saimaa</a></li>
        <li><a href="<%=urlLoc %>/gallery.jsp?locationCodeField=HV" rel="nofollow"> Haukivesi</a></li>
        <li><a href="<%=urlLoc %>/gallery.jsp?locationCodeField=JV" rel="nofollow"> Joutenvesi</a></li>
        <li><a href="<%=urlLoc %>/gallery.jsp?locationCodeField=PEV" rel="nofollow"> Pyyvesi – Enonvesi</a></li>
        <li><a href="<%=urlLoc %>/gallery.jsp?locationCodeField=KV" rel="nofollow"> Kolovesi</a></li>
        <li><a href="<%=urlLoc %>/gallery.jsp?locationCodeField=PV" rel="nofollow"> Pihlajavesi</a></li>
        <li><a href="<%=urlLoc %>/gallery.jsp?locationCodeField=PUV" rel="nofollow"> Puruvesi</a></li>
        <li><a href="<%=urlLoc %>/gallery.jsp?locationCodeField=KS" rel="nofollow"> Lepist&ouml;nselk&auml; – Katosselk&auml; – Haapaselk&auml;</a></li>
        <li><a href="<%=urlLoc %>/gallery.jsp?locationCodeField=LL" rel="nofollow"> Luonteri – Lietvesi</a></li>
        <li><a href="<%=urlLoc %>/gallery.jsp?locationCodeField=ES" rel="nofollow"> Etel&auml;-Saimaa</a></li>
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
        ArrayList<JSONObject>[] exemps = new ArrayList[2];
        if((i*2)<numVisible && rIndividuals.get(i*2)!=null){
        	pair[0]=rIndividuals.get(i*2);
        }
        if((i*2+1)<numVisible && rIndividuals.get(i*2+1)!=null){
        	pair[1]=rIndividuals.get(i*2+1);
        }

        String[] pairUrl = new String[2];
        String[] pairUrlMid = new String[2];
        String[] pairName = new String[2];
        String[] pairNickname = new String[2];
        String[] pairCopyright = new String[2];
        String[] pairMediaAssetID = new String[2];

        // construct a panel showing each individual
        for (int j=0; j<2; j++) {
        	if(pair[j]!=null){
          MarkedIndividual indie = pair[j];
/*
for (Encounter enx : indie.getDateSortedEncounters()) {
System.out.println("========> " + enx.getAnnotations());
}
*/
///// note: this below is a workaround for the metadata bug that needs fixing
/*
for (Object obJ : indie.getEncounters()) {
	Encounter enJ=(Encounter)obJ;
	for (MediaAsset maJ : enJ.getMedia()) {
		if (maJ.getMetadata() != null) maJ.getMetadata().getDataAsString();
	}
}
*/

          ArrayList<JSONObject> al = getExemplarImages(myShepherd, indie,request,5, qc);
			exemps[j]=al;
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
          pairUrl[j] = maJson.optString("urlDisplay", urlLoc+"/cust/mantamatcher/img/noimage.jpg"); //backup if urlMid is not found
          pairUrlMid[j] = (maJson.optString("urlMid").equals("") ? pairUrl[j] : maJson.getString("urlMid"));
          pairName[j] = indie.getIndividualID();
          Encounter[] sortedEncs=pair[j].getDateSortedEncounters();
          pairNickname[j] = pairName[j];
          if (!indie.getNickName().equals("Unassigned") && indie.getNickName()!=null && !indie.getNickName().equals("")) pairNickname[j] = indie.getNickName();
          %>
          <div class="col-xs-6">
            <div class="gallery-unit" id="gunit<%=i*2+j%>">
              <div class="crop" id="div-crop-<%=pairName[j]%>" title="<%=pairName[j]%>">
<!--label:mid-->
                <img src="<%=pairUrlMid[j]%>" id="mid-<%=pairName[j]%>" alt="<%=pairNickname[j]%>" />
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
<!--label:halfpage-->
                  <img src="<%=pairUrl[j]%>" id="halfpage-<%=pairName[j]%>" alt="<%=pairNickname[j]%>" />
                  <p class="image-copyright"> <%=pairCopyright[j]%> </p>
                </div>
              </div>
              <%
              // display=none copies of the above for each additional image
              //ArrayList<JSONObject> al = getExemplarImages(myShepherd,pair[j],request,5,qc);
            	ArrayList<JSONObject> al =exemps[j];
              for (int extraImgNo=1; extraImgNo<al.size(); extraImgNo++) {
                JSONObject newMaJson = new JSONObject();
                newMaJson = al.get(extraImgNo);
                String newUrl = newMaJson.optString("url", urlLoc+"/cust/mantamatcher/img/noimage.jpg");

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
            <a href="<%=urlLoc%>/gallery.jsp?startNum=<%=newStart%>&endNum=<%=newStart+numIndividualsOnPage%><%=sortString %><%=locationCodeFieldString %>" rel="nofollow"> <img border="0" alt="" src="<%=urlLoc%>/cust/mantamatcher/img/wwf-blue-arrow-left.png"> </a> &nbsp;&nbsp;&nbsp;&nbsp;
            <%
          }
          %>

          Lataa lis&auml;&auml; norppia &nbsp;&nbsp;&nbsp;&nbsp;

          <%
         // if (endNum<rIndividuals.size()) {
          %>

          <a href= "<%=urlLoc%>/gallery.jsp?startNum=<%=endNum%>&endNum=<%=endNum+numIndividualsOnPage%><%=sortString %><%=locationCodeFieldString %>" rel="nofollow"> <img border="0" alt="" src="<%=urlLoc%>/cust/mantamatcher/img/wwf-blue-arrow-right.png"/></a>
        
          <%
         // }
          %>

        </p>

      </row>

  </section>
</div>

<%
myShepherd.rollbackDBTransaction();
myShepherd.closeDBTransaction();
myShepherd=null;
long time3=System.currentTimeMillis();

//show debug times
if(request.getParameter("debug")!=null){
%>
	<p>Time query: <%=time2-time1 %> and time display: <%=time3-time2 %></p>
	<p>SS JDO: <%=ssjdo %></p>

<%
}
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
