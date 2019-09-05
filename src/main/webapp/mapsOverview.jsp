<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.servlet.ServletUtilities, org.ecocean.CommonConfiguration, java.io.File" %>
<%@ taglib uri="http://www.sunwesttek.com/di" prefix="di" %>
<%
String context="context0";
context=ServletUtilities.getContext(request);

  //String langCode = "en";
  String langCode=ServletUtilities.getLanguageCode(request);


  //setup data dir

%>

    <jsp:include page="/header.jsp" flush="true" />

        <div class="container maincontent">
<h2>New BOEM MAPS concepts</h2>
<p>
Flukebook now contains a new data layer called <b>Surveys</b>, along with its sub-division, <b>Survey Track</b>.  Together these are used to measure <i>effort</i> and reference associated Sightings and other meta-data.

These are populated by importing data from the Ocean Alert app.
</p>

<p>
The relationship between <b>Surveys</b>, <b>Survey Tracks</b>, and the existing concepts of <b>Sightings</b> and <b>Encounters</b> is illustrated below.  The new structures are on the left.
</p>

<div style="text-align: center;">
<img src="https://docs.google.com/drawings/d/e/2PACX-1vSNBGYev37bxYgCv0zM5s_GlRIAvHEjwp1QR7sJzAZelv-PvdbiXqijUUCOeGhD___tS9b9UsT3xImC/pub?w=1440&h=1080" style="width: 750px;" />
</div>

<hr />

<h2>Finding App Data and Attaching Images</h2>

<p>
Your corresponding imported app data can be found at the <a target="_new" href="attachMedia.jsp"><u><b>attachMedia.jsp</b></u></a> page.  You will see a listing similar to the one shown below.  By default, it is sorted by <b>Trip #</b> (from the app) and <b>Date/Time</b> on the left.
</p>

<div style="text-align: center;">
<img style="width: 900px;" src="images/grab-listing-example.png" />
</div>

<p>
The number of <i>encounters</i> and attached <i>photos</i> are listed on the right.  To attach images to the data, click on the row
you wish to affect and upload from the new screen that opens.
</p>

<hr />
<div style="text-align: center;">
<b>The short video below walks through some of the concepts described on this page.</b>
</div>

<div style="text-align: center;">
<iframe width="750" height="422" src="https://www.youtube.com/embed/2RWZTc9jbes" frameborder="0" allow="accelerometer; autoplay; encrypted-media; gyroscope; picture-in-picture" allowfullscreen></iframe>

</div>

</p>
        </div>

    <jsp:include page="/footer.jsp" flush="true"/>
