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

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.apache.shiro.crypto.*,org.apache.shiro.util.*,org.apache.shiro.crypto.hash.*,org.ecocean.*,org.ecocean.servlet.ServletUtilities,org.ecocean.grid.GridManager,org.ecocean.grid.GridManagerFactory, java.util.Properties,java.util.ArrayList" %>


<%

  //grab a gridManager
  GridManager gm = GridManagerFactory.getGridManager();
  int numProcessors = gm.getNumProcessors();
  int numWorkItems = gm.getIncompleteWork().size();

  Shepherd myShepherd = new Shepherd();
  
  	

//setup our Properties object to hold all properties

  //language setup
  String langCode = "en";
  if (session.getAttribute("langCode") != null) {
    langCode = (String) session.getAttribute("langCode");
  }

  Properties props = new Properties();
  props.load(getClass().getResourceAsStream("/bundles/" + langCode + "/overview.properties"));


%>

<html xmlns="http://www.w3.org/1999/xhtml">
<head>
  <title><%=CommonConfiguration.getHTMLTitle()%>
  </title>
  <meta http-equiv="Content-Type" content="text/html; charset=utf-8"/>
  <meta name="Description"
        content="<%=CommonConfiguration.getHTMLDescription() %>"/>
  <meta name="Keywords"
        content="<%=CommonConfiguration.getHTMLKeywords() %>"/>
  <meta name="Author" content="<%=CommonConfiguration.getHTMLAuthor() %>"/>
  <link href="<%=CommonConfiguration.getCSSURLLocation(request) %>"
        rel="stylesheet" type="text/css"/>
  <link rel="shortcut icon"
        href="<%=CommonConfiguration.getHTMLShortcutIcon() %>"/>


  <style type="text/css">
    <!--

    table.adopter {
      border-width: 1px 1px 1px 1px;
      border-spacing: 0px;
      border-style: solid solid solid solid;
      border-color: black black black black;
      border-collapse: separate;
      background-color: white;
    }

    table.adopter td {
      border-width: 1px 1px 1px 1px;
      padding: 3px 3px 3px 3px;
      border-style: none none none none;
      border-color: gray gray gray gray;
      background-color: white;
      -moz-border-radius: 0px 0px 0px 0px;
      font-size: 12px;
      color: #330099;
    }

    table.adopter td.name {
      font-size: 12px;
      text-align: center;
    }

    table.adopter td.image {
      padding: 0px 0px 0px 0px;
    }

    .style2 {
      font-size: x-small;
      color: #000000;
    }

    -->
  </style>

</head>

<body>
<div id="wrapper">
  <div id="page">
    <jsp:include page="header.jsp" flush="true">
      <jsp:param name="isAdmin" value="<%=request.isUserInRole(\"admin\")%>" />
    </jsp:include>
    <div id="main">
     
      <div id="maincol-wide-solo">

        <div id="maintext">
        
        <%
          if((request.getParameter("username")!=null)&&(request.getParameter("password")!=null)){
          %>
          
          <strong><em>Our records indicate that you have not yet accepted the User Agreement. Acceptance is required to use this resource. Please read the agreement below and click the "Accept" button to proceed or "Reject" to decline and return to the home page. </em></strong>
          <%
          }
          %>
        <br />
        <br />
          <h1 class="intro">Flukebook Database Data Use Policy</h1>
          
          

          <h3 class="intro">Introduction</h2>
          
<p>The OceanSmart Database was established in 2013 as part of a cooperative research program conducted by whale watching operators, scientists, and regional partners with the goal of better mapping distribution and individual movements of marine megafauna. The data is comprised of two major datasets, a Sightings database and the Identification database, both are curated by the Atlantic Whale Sightings Consortium Advisory Panel.</p>

<p>The Sightings database contains records of thousands of cetacean sightings throughout the world. The Identification database contains all known photographed sightings of whales by Consortium members and partners. In addition to photographed sightings, the database contains any record that can lead to an individual identification.  Research groups, whale watch vessels, and individual mariners contributed these sightings. Photographed sightings are matched to whales in the identification database whenever possible so that individual animals can be monitored over time.</p>

<p>Contributors to the datasets are given first access to the data, and contributors have full and unrestricted access to use of their own data.  After that, proposals for data access from scientists, students or other individuals with a bona fide purpose will be reviewed by the board in concert with appropriate data contributors. The Consortium has an obligation to protect the rights of contributors by placing restrictions or conditions upon access to, and use of, the materials within it.</p>

<h3 class="intro">Data Access Protocols</h2>


<p>Data access may be requested from scientists, students, or other individuals with a bona fide purpose.</p>

<p>Data access will not be granted for open-ended, exploratory investigations.</p>



<p>In order to ensure that research being planned or currently conducted by contributors is not compromised and that proper authorship of all major data contributors occurs, any request for data must be submitted to the Consortium in the form of a concise proposal by email containing the following:</p>

<ol>
<li>Name of the requesting institution(s) and of the Principal Investigator;</li>
<li>Outline of the proposed work, including questions being addressed, hypotheses tested or  anticipated management application;</li>
<li>Anticipated data requirements;</li>
<li>Anticipated products of the work (e.g. scientific paper, student thesis)</li>
<li>Estimated time frame to completion of the study</li>
</ol>

<h4 class="intro">Review procedure for proposals for publication purposes:</h3>


<p>Proposals will be reviewed within four weeks of submission by three Board members with knowledge of the type of work being proposed and/or a curatorial role with the data. In some cases, proposals will also be sent for review to those that contributed substantial portions of the data being requested. The review will be focused on ensuring that duplication of effort is minimized, that proposed analyses seem appropriate, and that potential coauthors are identified. The committee will review the proposal and discuss appropriate authorship given their knowledge of who contributed the majority of the data required for the proposed project. Their recommendations for authorship will be sent to the applicant. Once authorship has been agreed upon the data will be released. Every effort will be made to provide information in a timely fashion but since this is a largely voluntary effort, timeliness can not be guaranteed.</p>

<p>The the reviewers will maintain proposals as confidential and ideas or hypotheses that they may contain will not be shared with third parties. The only exception would be if the reviewers wish to obtain confidential peer review of the proposed work in order to judge its feasibility or merit; this would only be done with prior approval of the applicant. the Consortium will encourage multi-investigator proposals where interests of several investigators overlap.
Grounds for the rejection of a proposal will include lack of investigator qualifications, lack of necessary resources, an assessment that the scope of the project is unreasonably large or not feasible within the proposed time frame, unwillingness of the investigator(s) to acknowledge or offer authorship to major data contributors, a determination that the proposed work is already underway by someone else, or if the applicant has violated a past data access agreement.</p>

<h3 class="intro">Data Access Conditions</h3>

<p>The presentation of data here does not constitute publication. All data remain copyright of the project partners. Maps or data on this website may not be used or referenced without explicit written consent.
 </p>

<p>Provision of any data will be made subject to the conditions given below, to which the applicant must agree within his/her proposal. These conditions are designed to eliminate misunderstandings, and to protect both the applicant and the data contributors.</p>
<p>Conditions for data use intended for publication (includes peer-reviewed publications, thesis work, education modules, and other uses that are not strictly managerial):
<ul>
<li>The applicant will use the requested materials for only those purposes set forth in his/her proposal. Requests for significant departures from the scope of the proposal must be submitted in writing to the review board for approval.</li>
<li>The applicant will not share the requested materials with any third party.</li>
<li>The applicant agrees to complete the work in the time frame given, although requests for  reasonable extensions of this time frame will be considered.</li>
<li>The applicant agrees to publish the results in a refereed journal in a timely manner. Failure to do this constitutes unfair monopolization of data with no benefit to conservation.</li>
<li>When data is used for management use the following disclaimer must be used “Raw sighting data from the flukebook sightings database are not always effort-corrected and the management documents in which they are used are not peer reviewed.</li>
<li>If the management analyses result in publishable information, the applicant is required to submit an additional request for publication. If someone has already applied for data to publish on a similar analysis, the Consortium will encourage a dialog among the parties, but publication rights will go to the applicant who first applied for data under the publication request process.</li>
<li>One electronic copy of any document or other product produced using Consortium data provided including workshop proceedings, or other unpublished “gray literature” will be provided to be kept on file.</li>
</ul>
</p>

<h3 class="intro">Citation of Data</h3>

<p>In text:  Flukebook (<em>YEAR</em>)</p>
<p>In Literature Cited: Flukebook (<em>YEAR</em>). Sightings and Identification Database <em>MM/DD/YYYY</em> (Flukebook, Roseau, Dominica, W.I.).</p>
<p>If you have any questions, please contact <a href="mailto:info@flukebook.org">info@flukebook.org</a>.</p>

<h3 class="intro">Acceptance</h3>
<p>I have read and understand all of the conditions for data access and use listed and agree to be bound by them.</p>


        
          
          
          
          
          <%
          if((request.getParameter("username")!=null)&&(request.getParameter("password")!=null)){
          %>
          <p><table cellpadding="5"><tr><td>
          	<form name="accept_agreement" action="LoginUser" method="post">
          		<input type="hidden" name="username" value="<%=request.getParameter("username")%>" />
          		<input type="hidden" name="password" value="<%=request.getParameter("password")%>" />
          		<input type="submit" id="acceptUserAgreement" name="acceptUserAgreement" value="Accept"/>
          	</form>
          </td>
          <td><form name="reject_agreement" action="index.jsp" method="get">
          		<input type="submit" name="rejectUserAgreement" value="Reject"/>
          	</form></td>
          </tr></table>
          </p>
          <%
          }
          %>
        </div>

  


      </div>
      <!-- end maincol -->
    </div>
    <!-- end main -->
    <jsp:include page="footer.jsp" flush="true"/>
  </div>
  <!-- end page --></div>
<!--end wrapper -->

</body>
</html>
