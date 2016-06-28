<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.servlet.ServletUtilities,
org.ecocean.media.*,
org.ecocean.*,
org.datanucleus.api.rest.orgjson.JSONObject,
org.datanucleus.api.rest.orgjson.JSONArray,
org.ecocean.servlet.ServletUtilities,org.ecocean.Util,org.ecocean.Measurement, org.ecocean.Util.*, org.ecocean.genetics.*, org.ecocean.tag.*, java.awt.Dimension, javax.jdo.Extent, javax.jdo.Query, java.io.File, java.io.FileInputStream,java.text.DecimalFormat,
java.util.*,javax.servlet.http.HttpServletRequest,
java.util.TreeMap
" %>

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






<%
String context="context0";
context=ServletUtilities.getContext(request);
Shepherd imageShepherd = new Shepherd(context);
imageShepherd.beginDBTransaction();
String indID = request.getParameter("individualID");
String urlLoc = "http://" + CommonConfiguration.getURLLocation(request);
String imgUrl = urlLoc+"/cust/mantamatcher/img/hero_manta.jpg";
String nickname = indID;

//define our individual text
String Phs106 = "Pullervo on Suomen tunnetuin saimaannorppa. Se nousi koko kansan tietoisuuteen Norppaliven toisena p&auml;&auml;t&auml;hten&auml; toukokuussa 2016. Pullervo tunnettiin ennen koodinimell&auml; Phs106. Sen nime&auml;miseksi k&auml;ynnistettiin kilpailu, ja ehdotuksia nimeksi tuli yli 6 000. Pullervo on v&auml;ritykselt&auml;&auml;n erityisen vaalea ja isohko uros.";
String Phs142 = "Siiri oli toinen Norppalivess&auml; n&auml;hdyist&auml; saimaannorpista. Se on n&auml;hty samalla lepokivell&auml; jo kolmena per&auml;kk&auml;isen&auml; vuotena. Vuonna 2015 Siiri oli It&auml;-Suomen yliopiston tutkijoilla l&auml;hetinseurannassa, jolloin sen liikkeist&auml; saatiin t&auml;rke&auml;&auml; tietoa tutkimusk&auml;ytt&ouml;&ouml;n.";
String Phs014 = "Haukivedell&auml; vuonna 2008 syntyneen Tertun el&auml;m&auml;n alkutaivalta seurattiin tarkasti sen karvaan kiinnitetyn radiol&auml;hettimen avulla. L&auml;hetin putosi kuitenkin seuraavana vuonna Tertun kyydist&auml;, sill&auml; norpat vaihtavat karvansa s&auml;&auml;nn&ouml;llisesti. Seuraavaa havaintoa saatiin odottaa muutaman vuoden ajan. Sen j&auml;lkeen Terttu on n&auml;hty vuosittain, aina alle seitsem&auml;n kilometrin p&auml;&auml;ss&auml; syntym&auml;paikastaan. Vuonna 2015 saatiin iloisia perheuutisia, kun Terttu synnytti poikasen.";
String Phs023 = "Haukivedell&auml; uiskenteleva Teemu on tallennettu todenn&auml;k&ouml;isesti moniin kotialbumeihin. Se on koko Saimaan useimmiten tavattu norppa, joka ei huomiota tai veneit&auml; kavahda. Ensimm&auml;isen kerran Teemu n&auml;htiin vuonna 2006. Vuonna 2009 Teemulle asennettiin radiol&auml;hetin, jolloin se my&ouml;s punnittiin: painoa oli kertynyt komeat 103 kiloa. Teemu vaikuttaa olevan varsin kotiseuturakas eik&auml; se ei juuri harhaile pois tutuilta vesilt&auml;.";
String Phs052 = "Ritva alkaa olla kunnioitettavassa i&auml;ss&auml;, ja se onkin pisimp&auml;&auml;n tunnettu saimaannorppa. Ensimm&auml;isen kerran Ritva havaittiin vuonna 1998 Haukivedell&auml;, jolloin tutkijat seurasivat sen liikkeit&auml; radiol&auml;hettimen avulla. Ritva on naaraaksi varsin suuri. Se painoi vuonna 1998 per&auml;ti 95 kiloa. Vuonna 1999 Ritva synnytti kuutin, mink&auml; j&auml;lkeen sit&auml; ei n&auml;hty pitk&auml;&auml;n aikaan. Viime vuosina Ritvasta on kuitenkin lukuisia havaintoja, kaikki alle nelj&auml;n kilometrin s&auml;teell&auml; toisistaan.";
String Phs045 = "Arka-norpasta erityisen tekev&auml;t sen perhesiteet. Arka on nimitt&auml;in toinen ensimm&auml;isist&auml; varmistetuista norppakaksosista. Kaksoset syntyiv&auml;t vuonna 2009 Pihlajavedell&auml;, ja Arka-naaraasta on lukuisia havaintoja vuosien varrelta. Vuonna 2015 se kuvattiin j&auml;&auml;ll&auml; poikasen kanssa. Samana kes&auml;n&auml; Arka jakoi lepopaikan toisen aikuisen norpan kanssa. Kyseess&auml; ei kuitenkaan ollut sen kaksonen Parka, josta ei valitettavasti ole tehty havaintoja vuoden 2009 j&auml;lkeen.";
String Phs087 = "Mitro on reipas liikkeiss&auml;&auml;n. Se havaittiin ensimm&auml;isen kerran Pihlajavedell&auml; vuonna 2011. Se loikoili samoilla tienoilla my&ouml;s seuraavana vuonna, mutta vuonna 2013 Mitro yll&auml;tti tutkijat t&auml;ysin: se tallentui nimitt&auml;in riistakamerakuviin yli 35 kilometrin p&auml;&auml;ss&auml; aiemmin suosimastaan paikasta. Mitro oli my&ouml;s GPS- seurannassa, jonka aikana sen havaittiin tehneen yli 60 kilometrin uintilenkin ja k&auml;yneen aina Puruvedell&auml; asti. Se ei kaihda seuraa, vaan k&ouml;ll&ouml;ttelee usein toisen norpan kanssa vierekk&auml;isill&auml; kivill&auml;. Mitro on kuvattu my&ouml;s makoilemasta rantaruohikolla.";

TreeMap<String,String> ids=new TreeMap<String,String>();
ids.put("Phs106", Phs106);
ids.put("Phs142", Phs142);
ids.put("Phs014", Phs014);
ids.put("Phs023", Phs023);
ids.put("Phs052", Phs052);
ids.put("Phs045", Phs045);
ids.put("Phs087", Phs087);



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
  <p><%=ids.get(indID) %></p>
  <p class=pfooter-link>
    <a href="<%=urlLoc%>/gallery.jsp">Siirry galleriaan >></a>
  </p>
</div>
