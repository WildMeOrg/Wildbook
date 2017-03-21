<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.servlet.ServletUtilities,
org.ecocean.media.*,
org.ecocean.*,
org.datanucleus.api.rest.orgjson.JSONObject,
org.datanucleus.api.rest.orgjson.JSONArray,
org.ecocean.servlet.ServletUtilities,org.ecocean.Util,org.ecocean.Measurement, org.ecocean.Util.*, org.ecocean.genetics.*, org.ecocean.tag.*, java.awt.Dimension, javax.jdo.Extent, javax.jdo.Query, java.io.File, java.io.FileInputStream,java.text.DecimalFormat,
java.util.*,javax.servlet.http.HttpServletRequest
" %>
<%@ taglib uri="http://www.sunwesttek.com/di" prefix="di" %>
<%--
  ~ The Shepherd Project - A Mark-Recapture Framework
  ~ Copyright (C) 2011 Jason Holmberg
  ~
  ~ This program is free software; you can redistribute it and/or
  ~ modify it under the terms of the GNU General Public License
  ~ as published by the Free Software Foundation; either version 2
  ~ of the License, or (at your option) any later version.
  ~
  ~ This program is distributed in the hope that it will be useful,
  ~ but WITHOUT ANY WARRANTY; without even the implied warranty of
  ~ MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  ~ GNU General Public License for more details.
  ~
  ~ You should have received a copy of the GNU General Public License
  ~ along with this program; if not, write to the Free Software
  ~ Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
  --%>



<%!
// This utility function might belong elsewhere, in Wildbook at-large
// finds the most recent MediaAsset (as JSON) for an individual, returning a null JSONObject if none is found
public JSONObject getExemplarImage(MarkedIndividual indie, HttpServletRequest req) throws org.datanucleus.api.rest.orgjson.JSONException {
  Encounter[] galleryEncs = indie.getDateSortedEncounters();
  for (Encounter enc : galleryEncs) {
    ArrayList<Annotation> anns = enc.getAnnotations();
    if ((anns == null) || (anns.size() < 1)) {
      continue;
    }
    for (Annotation ann: anns) {
      if (!ann.isTrivial()) continue;
      MediaAsset ma = ann.getMediaAsset();
      if (ma != null) {
        //JSONObject j = new JSONObject();
        JSONObject j = ma.sanitizeJson(req, new JSONObject());
        if (j!=null) return j;
      }
    }
  }
  return new JSONObject();
}
%>


<%
String context="context0";
context=ServletUtilities.getContext(request);
Shepherd imageShepherd = new Shepherd(context);
myShepherd.setAction("individualGalleryPanel.jsp");
imageShepherd.beginDBTransaction();
String indID = request.getParameter("individualID");
String urlLoc = "//" + CommonConfiguration.getURLLocation(request);
String imgUrl = urlLoc+"/cust/mantamatcher/img/hero_manta.jpg";
String nickname = indID;

try {
  MarkedIndividual indie = imageShepherd.getMarkedIndividual(indID);
  Encounter[] galleryEncs = indie.getDateSortedEncounters();
  String langCode=ServletUtilities.getLanguageCode(request);
  Properties encprops = new Properties();
  encprops = ShepherdProperties.getProperties("encounter.properties", langCode,context);
  //JSONObject maJson = new JSONObject();
  //JSONObject maJson = getExemplarImage(indie, request);
  JSONObject maJson = indie.getExemplarImage(request);
  imgUrl = maJson.optString("url", urlLoc+"/cust/mantamatcher/img/hero_manta.jpg");
  if (!indie.getNickName().equals("Unassigned") && indie.getNickName()!=null && !indie.getNickName().equals("")) nickname = indie.getNickName();
  // loop through encs until we get a good representative MediaAsset
}
catch(Exception e){e.printStackTrace();}
finally{
	imageShepherd.rollbackDBTransaction();
	imageShepherd.commitDBTransaction();
}

%>

<div class="col-md-6">
  <a href="<%=urlLoc%>/gallery.jsp"><img src="<%=imgUrl%>" alt="<%=nickname%>"/></a>
</div>

<div class="col-md-6 full-height">
  <h2 class="greenish"><%=nickname%></h2>
  <p>
    Mik&auml; ihmeen Lorem ipsum?

    Lorem ipsum on 1500-luvulta l&auml;htien olemassa ollut t&auml;yteteksti, jota k&auml;ytet&auml;&auml;n usein ulkoasun testaamiseen graafisessa suunnittelussa, kun mit&auml;&auml;n oikeata sis&auml;lt&ouml;&auml; ei viel&auml; ole. Lorem ipsumia k&auml;ytet&auml;&auml;n n&auml;ytt&auml;m&auml;&auml;n, milt&auml; esimerkiksi kirjasin tai julkaisun tekstin asettelu n&auml;ytt&auml;v&auml;t
  </p>
  <p class=pfooter-link>
    <a href="<%=urlLoc%>/gallery.jsp">Siirry galleriaan >></a>
  </p>
</div>
