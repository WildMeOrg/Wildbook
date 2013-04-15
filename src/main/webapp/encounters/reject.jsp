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

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.CommonConfiguration, java.util.Properties" %>
<html>
<head>
  <title><%=CommonConfiguration.getHTMLTitle() %>
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


</head>
<%
  //handle some cache-related security
  response.setHeader("Cache-Control", "no-cache"); //Forces caches to obtain a new copy of the page from the origin server
  response.setHeader("Cache-Control", "no-store"); //Directs caches not to store the page under any circumstance
  response.setDateHeader("Expires", 0); //Causes the proxy cache to see the page as "stale"
  response.setHeader("Pragma", "no-cache"); //HTTP 1.0 backward compatibility


//setup our Properties object to hold all properties
  Properties props = new Properties();
  String langCode = "en";

  //check what language is requested
  if (request.getParameter("langCode") != null) {
    if (request.getParameter("langCode").equals("fr")) {
      langCode = "fr";
    }
    if (request.getParameter("langCode").equals("de")) {
      langCode = "de";
    }
    if (request.getParameter("langCode").equals("es")) {
      langCode = "es";
    }
  }

  //set up the file input stream
  //FileInputStream propsInputStream=new FileInputStream(new File((new File(".")).getCanonicalPath()+"/webapps/ROOT/WEB-INF/classes/bundles/"+langCode+"/submit.properties"));
  props.load(getClass().getResourceAsStream("/bundles/" + langCode + "/submit.properties"));


  //load our variables for the submit page
  String title = props.getProperty("submit_title");
  String submit_maintext = props.getProperty("submit_maintext");
  String submit_reportit = props.getProperty("reportit");
  String submit_language = props.getProperty("language");
  String what_do = props.getProperty("what_do");
  String read_overview = props.getProperty("read_overview");
  String see_all_encounters = props.getProperty("see_all_encounters");
  String see_all_sharks = props.getProperty("see_all_sharks");
  String report_encounter = props.getProperty("report_encounter");
  String log_in = props.getProperty("log_in");
  String contact_us = props.getProperty("contact_us");
  String search = props.getProperty("search");
  String encounter = props.getProperty("encounter");
  String shark = props.getProperty("shark");
  String join_the_dots = props.getProperty("join_the_dots");
  String menu = props.getProperty("menu");
  String last_sightings = props.getProperty("last_sightings");
  String more = props.getProperty("more");
  String ws_info = props.getProperty("ws_info");
  String about = props.getProperty("about");
  String contributors = props.getProperty("contributors");
  String forum = props.getProperty("forum");
  String blog = props.getProperty("blog");
  String area = props.getProperty("area");
  String match = props.getProperty("match");

  //link path to submit page with appropriate language
  String submitPath = "submit.jsp?langCode=" + langCode;


%>
<body>
<div id="wrapper">
  <div id="page">
    <jsp:include page="../header.jsp" flush="true">
      <jsp:param name="isAdmin" value="<%=request.isUserInRole(\"admin\")%>" />
    </jsp:include>
    <div id="main">
      <div id="leftcol">
        <div id="menu"></div>
        <!-- end menu --></div>
      <!-- end leftcol -->
      <div id="maincol-wide">

        <div id="maintext">
          <table border="0">
            <tr>
              <td>
                <h1 class="intro">Reject an encounter</h1>

                <p>You are about to <strong>REJECT</strong> a submitted encounter
                  from the visual database. Choose <strong>Save as
                    Unidentifiable</strong> if this encounter is not valid for photographic
                  mark-recapture but may contain valuable data for future use. If you
                  choose <strong>Permanently delete</strong>, all data contained within
                  this encounter will be removed from the database<em>. However,
                    the webmaster can restore the encounter in case you accidentally
                    delete it </em>. Choose <strong>Cancel</strong> to retain the encounter in
                  the visual database as is.</p>
              </td>
            </tr>
            <tr>
              <td>
                <p>&nbsp;</p>

                <p>Do you want to reject encounter <%=request.getParameter("number")%>?</p>
                <table width="400" border="0" cellpadding="5" cellspacing="0">
                  <tr>
                    <td align="right" valign="top">
                      <form name="rej_save_form" method="post"
                            action="../EncounterSetAsUnidentifiable"><input name="action"
                                                                            type="hidden"
                                                                            id="action"
                                                                            value="rej_but_save">
                        <input
                          name="number" type="hidden"
                          value=<%=request.getParameter("number")%>> <input
                          name="yes" type="submit" id="yes" value="Save as Unidentifiable"></form>
                    </td>
                    <td align="center" valign="top">
                      <form name="reject_form" method="post" action="../EncounterDelete">
                        <input name="action" type="hidden" id="action" value="reject">
                        <input name="number" type="hidden"
                               value=<%=request.getParameter("number")%>> <input
                        name="yes" type="submit" id="yes" value="Permanently delete"></form>
                    </td>
                    <td align="left" valign="top">
                      <form name="form2" method="post" action="encounter.jsp">
                        <input name="number" type="hidden"
                               value=<%=request.getParameter("number")%>> <input name="no"
                                                                                 type="submit"
                                                                                 id="no"
                                                                                 value="Cancel">
                      </form>
                    </td>
                  </tr>
                </table>
              </td>
            </tr>
          </table>

        </div>
        <!-- end maintext --></div>
      <!-- end maincol -->
      <jsp:include page="../footer.jsp" flush="true"/>
    </div>
    <!-- end page --></div>
  <!--end wrapper -->
</body>
</html>
