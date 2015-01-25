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
<%@ page contentType="text/html; charset=iso-8859-1" language="java"
         import="org.ecocean.servlet.ServletUtilities,org.ecocean.*" %>



<%

String context="context0";
context=ServletUtilities.getContext(request);

  Shepherd myShepherd = new Shepherd(context);

//handle some cache-related security
  response.setHeader("Cache-Control", "no-cache"); //Forces caches to obtain a new copy of the page from the origin server
  response.setHeader("Cache-Control", "no-store"); //Directs caches not to store the page under any circumstance
  response.setDateHeader("Expires", 0); //Causes the proxy cache to see the page as "stale"
  response.setHeader("Pragma", "no-cache"); //HTTP 1.0 backward compatibility
%>

<html>
<head>
  <title><%=CommonConfiguration.getHTMLTitle(context) %>
  </title>
  <meta http-equiv="Content-Type" content="text/html; charset=utf-8"/>
  <meta name="Description"
        content="<%=CommonConfiguration.getHTMLDescription(context) %>"/>
  <meta name="Keywords"
        content="<%=CommonConfiguration.getHTMLKeywords(context) %>"/>
  <meta name="Author" content="<%=CommonConfiguration.getHTMLAuthor(context) %>"/>
  <link href="<%=CommonConfiguration.getCSSURLLocation(request,context) %>"
        rel="stylesheet" type="text/css"/>
  <link rel="shortcut icon"
        href="<%=CommonConfiguration.getHTMLShortcutIcon(context) %>"/>

  <style type="text/css">
    <!--
    .style1 {
      color: #FF0000
    }

    -->
  </style>
</head>

<body>
<div id="wrapper">
  <div id="page">
    <jsp:include page="../header.jsp" flush="true">

      <jsp:param name="isAdmin" value="<%=request.isUserInRole(\"admin\")%>" />
    </jsp:include>
    <div id="main">
      <p>

      <h1 class="intro">Library Administration</h1>
      </p>
      <table width="600" border="1">
        <tr>
          <td>
            <p><font size="+1">Restore a Deleted Encounter</font></p>

            <form name="restoreEncounter" method="post"
                  action="../ResurrectDeletedEncounter">
              <p>Encounter number: <input name="number" type="text" id="number"
                                          size="20" maxlength="50"> <br> <input name="Restore"
                                                                                type="submit"
                                                                                id="Restore"
                                                                                value="Restore"></p>
            </form>
          </td>
        </tr>
      </table>
      <p></p>
      <table width="600" border="1">
        <tr>
          <td>
            <p><font size="+1">Update Email Address of Submitter or
              Photographer Across the Entire Library</font></p>

            <form name="updateEmail" method="post" action="../UpdateEmailAddress">
              <p>Old Email Address: <input name="findEmail" type="text"
                                           id="findEmail" size="25" maxlength="50">

              <p>New Email Address: <input name="replaceEmail" type="text"
                                           id="replaceEmail" size="25" maxlength="50"> <br> <input
                name="Update" type="submit" id="Update" value="Update"></p>
            </form>
          </td>
        </tr>
      </table>
      <p>&nbsp;</p>
      <table width="600" border="1">
        <tr>
          <td>
            <p><font size="+1">Swap old location code for new across
              all encounters</font></p>

            <p class="style1"><em><strong>WARNING</strong></em>: This changes
              the location code for encounters from an old to a new value. This is a
              non-trivial change and should only be done after significant
              deliberation.</p>

            <form name="massSwapLocCode" method="post" action="../MassSwapLocationCode">
              <p>Old location code: <input name="oldLocCode" type="text"
                                           id="oldLocCode" size="10" maxlength="10">

              <p>New location code: <input name="newLocCode" type="text"
                                           id="newLocCode" size="10" maxlength="10"> <br/>
                <br> <input name="Update" type="submit" id="Update"
                            value="Update"></p>
            </form>
          </td>
        </tr>
      </table>
      <p>&nbsp;</p>
      <table width="600" border="1">
        <tr>
          <td>
            <p><font size="+1">Expose all approved encounters to the
              GBIF. </font></p>

            <form name="exposeGBIF" method="post" action="../MassExposeGBIF">

              <input name="Expose to GBIF" type="submit" id="Expose to GBIF"
                     value="Expose to GBIF">
              </p></form>
          </td>
        </tr>
      </table>
      <p>&nbsp;</p>
      <table width="600" border="1">
        <tr>
          <td>
            <p><font size="+1">Set the location code for all encounters
              matching a string</font></p>

            <p class="style1"><em><strong>WARNING</strong></em>: This changes
              the location code for encounters from an old to a new value. This is a
              non-trivial change and should only be done after significant
              deliberation.</p>

            <form name="massSetLocationCodeFromLocationString" method="post"
                  action="../MassSetLocationCodeFromLocationString">
              <p>Text string to match (case insensitive): <input
                name="matchString" type="text" id="matchString" size="50"
                maxlength="999">

              <p>Location code to assign: <input name="locCode" type="text"
                                                 id="locCode" size="10" maxlength="10"> <br/>
                <br> <input name="Update" type="submit" id="Update"
                            value="Update"></p>
            </form>
          </td>
        </tr>
      </table>

      <p>&nbsp;</p>
      <table width="600" border="1">
        <tr>
          <td>
            <p><font size="+1">Set Inform Others for all encounters
              matching a string</font></p>

            <p><font size="+1">Matches are made on submitter and
              photographer names and email addresses </font></p>

            <form name="massSetInformOthers" method="post"
                  action="../MassSetInformOthers">
              <p>Text string to match (case insensitive): <input
                name="matchString" type="text" id="matchString" size="50"
                maxlength="100"/>

              <p>Inform others email addresses to assign: <input
                name="informEmail" type="text" id="informEmail" size="50"
                maxlength="999"> <br/>
                <br /> <input name="Update" type="submit" id="Update"
                            value="Update"></p>
            </form>
          </td>
        </tr>
      </table>

      <p>&nbsp;</p>
      <table width="600" border="1">
        <tr>
          <td>
            <p><img src="../images/Warning_icon.png" width="25px" height="*" align="absmiddle" />  <font size="+1">Delete All Data PERMANENTLY</font>
            <br /><br /><em>Warning! This will delete ALL of your data. Your user account must have the 'destroyer' user role for this function to work, and this option is not available for any account by default. This option is only meant for
            Shepherd Project instances that have transient data (i.e., the Shepherd Project is not the primary data store).</em>
            </p>

            <form onsubmit="return confirm('Are you sure you want to delete all encounters? WARNING! This will cause complete data loss!');" name="deleteAll" method="post" action="../DeleteAllDataPermanently">

              <input name="deleteAllData" type="submit" id="deleteAllData" value="Delete All Data PERMANENTLY">
              </p></form>
          </td>
        </tr>
      </table>
      


      <jsp:include page="../footer.jsp" flush="true"/>
    </div>
  </div>
  <!-- end page --></div>
<!--end wrapper -->
</body>
</html>


