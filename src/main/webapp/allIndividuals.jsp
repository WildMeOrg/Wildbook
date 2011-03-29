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
         import="org.ecocean.CommonConfiguration, org.ecocean.Encounter, org.ecocean.MarkedIndividual, org.ecocean.Shepherd,org.ecocean.servlet.ServletUtilities,javax.jdo.Extent,javax.jdo.FetchPlan, javax.jdo.Query, java.util.Iterator, java.util.Properties" %>

<%

    //setup our Properties object to hold all properties
    String langCode = "en";
    if (session.getAttribute("langCode") != null) {
        langCode = (String) session.getAttribute("langCode");
    }


    //set up the file input stream
    Properties props = new Properties();
    props.load(getClass().getResourceAsStream("/bundles/" + langCode + "/allIndividuals.properties"));

    //load our variables for the page
    String sex = props.getProperty("sex");
    String see_all_sharks = props.getProperty("see_all_sharks");
    String encounter = props.getProperty("encounter");
    String shark = props.getProperty("shark");
    String records = props.getProperty("records");
    String next = props.getProperty("next");
    String previous = props.getProperty("previous");
    String image = props.getProperty("image");
    String series_code = props.getProperty("series_code");
    String area = props.getProperty("area");
    String match = props.getProperty("match");
    String name = props.getProperty("name");
    String text = props.getProperty("text");


    //link path to submit page with appropriate language
    String submitPath = "submit.jsp";

    Shepherd myShepherd = new Shepherd();
    String currentSort = "nosort";
    String displaySort = "";
    if (request.getParameter("sort") != null) {
        currentSort = request.getParameter("sort");
        if (request.getParameter("sort").startsWith("name")) {
            displaySort = " sorted by Name";
        } else if (request.getParameter("sort").startsWith("series")) {
            displaySort = " sorted by Series Code";
        } else if (request.getParameter("sort").startsWith("sex")) {
            displaySort = " sorted by Sex";
        } else if (request.getParameter("sort").startsWith("encounters")) {
            displaySort = " sorted by # Encounters";
        }
    }
    currentSort = "&amp;sort=" + currentSort;
    int lowCount = 1, highCount = 10;
    if ((request.getParameter("start") != null) && (request.getParameter("end") != null)) {
        lowCount = (new Integer(request.getParameter("start"))).intValue();
        highCount = (new Integer(request.getParameter("end"))).intValue();
        if ((highCount > (lowCount + 9)) && (session.getAttribute("logged") != null)) {
            highCount = lowCount + 9;
        }
    }

    myShepherd.beginDBTransaction();
    //build a query
    Extent sharkClass = myShepherd.getPM().getExtent(MarkedIndividual.class, true);
    Query query = myShepherd.getPM().newQuery(sharkClass);

    int totalCount = myShepherd.getNumMarkedIndividuals();

%>
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

<body>
<div id="wrapper">
    <div id="page">
        <jsp:include page="header.jsp" flush="true">

      <jsp:param name="isAdmin" value="<%=request.isUserInRole(\"admin\")%>" />
        </jsp:include>
        <div id="main">

            <div id="maincol-wide-solo">

                <div id="maintext">
                    <table id="results" border="0" width="810px">
                        <tr>
                            <td colspan="5">
                                <h1><span class="intro"><img src="images/tag_big.gif" width="50px"
                                                             height="*"
                                                             align="absmiddle"/></span> <%=see_all_sharks%>
                                </h1>
                            </td>
                        </tr>
                        <tr>
                            <th class="caption"
                                colspan="5"><%=text.replaceAll("COUNT", Integer.toString(totalCount))%>
                            </th>
                        </tr>
                        <tr class="paging">
                            <td width="101" align="left">
                                <%if (highCount < totalCount) {%> <a
                                    href="allIndividuals.jsp?start=<%=(lowCount+10)%>&amp;end=<%=(highCount+10)%><%=currentSort%>"><%=next%>
                            </a>
                                <%
                                    }
                                    if ((lowCount - 10) >= 1) {
                                %> | <a
                                    href="allIndividuals.jsp?start=<%=(lowCount-10)%>&amp;end=<%=(highCount-10)%><%=currentSort%>"><%=previous%>
                            </a>
                                <%}%>
                            </td>
                            <td align="right" colspan="4">
                                <%
                                    String startNum = "1";
                                    String endNum = "10";
                                    if (request.getParameter("start") != null) {
                                        startNum = request.getParameter("start");
                                    }
                                    if ((request.getParameter("end") != null) && (!request.getParameter("end").equals("99999"))) {
                                        endNum = request.getParameter("end");
                                    } else if ((request.getParameter("end") != null) && (request.getParameter("end").equals("99999"))) {
                                        endNum = (new Integer(totalCount)).toString();
                                    }
                                %>
                                <%=records%>: <%=lowCount%> - <%=highCount%><%=displaySort%>
                            </td>
                        </tr>
                        <tr class="lineitem">
                            <td width="101" bgcolor="#99CCFF" class="lineitem"><strong><%=image%>
                            </strong></td>
                            <td width="133" align="left" valign="top" bgcolor="#99CCFF"
                                class="lineitem"><strong><%=name%>
                            </strong><br/>
                                <%if (request.getRemoteUser() != null) {%><a
                                        href="allIndividuals.jsp?sort=nameup&amp;start=<%=(lowCount)%>&amp;end=<%=(highCount)%>"><img
                                        src="arrow_up.gif" width="11" height="6" border="0"></a> <a
                                        href="allIndividuals.jsp?sort=namedown&amp;start=<%=(lowCount)%>&amp;end=<%=(highCount)%>"><img
                                        src="arrow_down.gif" width="11" height="6" border="0"></a>
                                <%}%>
                            </td>
                            <td width="146" align="left" valign="top" bgcolor="#99CCFF"
                                class="lineitem"><strong><%=encounter%>
                            </strong><br/>
                                <%if (request.getRemoteUser() != null) {%><a
                                        href="allIndividuals.jsp?sort=encountersup&amp;start=<%=(lowCount)%>&amp;end=<%=(highCount)%>"><img
                                        src="arrow_up.gif" width="11" height="6" border="0"></a> <a
                                        href="allIndividuals.jsp?sort=encountersdown&amp;start=<%=(lowCount)%>&amp;end=<%=(highCount)%>"><img
                                        src="arrow_down.gif" width="11" height="6" border="0"></a>
                                <%}%>
                            </td>
                            <td width="119" align="left" valign="top" bgcolor="#99CCFF"
                                class="lineitem"><strong><%=sex%>
                            </strong><br/>
                                <%if (request.getRemoteUser() != null) {%><a
                                        href="allIndividuals.jsp?sort=sexup&amp;start=<%=(lowCount)%>&amp;end=<%=(highCount)%>"><img
                                        src="arrow_up.gif" width="11" height="6" border="0"></a> <a
                                        href="allIndividuals.jsp?sort=sexdown&amp;start=<%=(lowCount)%>&amp;end=<%=(highCount)%>"><img
                                        src="arrow_down.gif" width="11" height="6" border="0"></a>
                                <%}%>
                            </td>

                        </tr>
                        <%
                            myShepherd.getPM().getFetchPlan().setGroup("allSharks_min");
                            int total = myShepherd.getNumMarkedIndividuals();

                            Iterator allSharks;
                            //query.setRange((totalCount-highCount),(totalCount-lowCount+1));
                            ServletUtilities.setRange(query, total, highCount, lowCount);
                            if (request.getParameter("sort") != null) {
                                if (request.getParameter("sort").equals("sexup")) {
                                    allSharks = myShepherd.getAllMarkedIndividuals(query, "sex ascending");
                                } else if (request.getParameter("sort").equals("sexdown")) {
                                    allSharks = myShepherd.getAllMarkedIndividuals(query, "sex descending");
                                } else if (request.getParameter("sort").equals("encountersup")) {
                                    allSharks = myShepherd.getAllMarkedIndividuals(query, "numberEncounters ascending");
                                } else if (request.getParameter("sort").equals("encountersdown")) {
                                    allSharks = myShepherd.getAllMarkedIndividuals(query, "numberEncounters descending");
                                } else if (request.getParameter("sort").equals("nameup")) {
                                    allSharks = myShepherd.getAllMarkedIndividuals(query, "name ascending");
                                } else if (request.getParameter("sort").equals("namedown")) {
                                    allSharks = myShepherd.getAllMarkedIndividuals(query, "name descending");
                                } else if (request.getParameter("sort").equals("seriesup")) {
                                    allSharks = myShepherd.getAllMarkedIndividuals(query, "seriesCode ascending");
                                } else if (request.getParameter("sort").equals("seriesdown")) {
                                    allSharks = myShepherd.getAllMarkedIndividuals(query, "seriesCode descending");
                                } else {
                                    allSharks = myShepherd.getAllMarkedIndividuals(query);
                                }


                            } else {
                                allSharks = myShepherd.getAllMarkedIndividuals(query);
                            }

                            int countMe = 0;
                            while (allSharks.hasNext()) {
                                MarkedIndividual sharky = (MarkedIndividual) allSharks.next();


                                countMe++;
                                //if ((countMe>=lowCount)&&(countMe<=highCount)) {
                                myShepherd.getPM().getFetchPlan().setGroup(FetchPlan.DEFAULT);
                                Encounter enc = (Encounter) sharky.getEncounter(0);
                                String imgName = "encounters/" + enc.getEncounterNumber() + "/thumb.jpg";
                        %>
                        <tr>
                            <td width="101" height="60" class="lineitem"><a
                                    href="individuals.jsp?number=<%=sharky.getName()%>"><img
                                    src="<%=imgName%>" alt="<%=sharky.getName()%>" border="0"/></a>
                            </td>
                            <td class="lineitems"><a
                                    href="individuals.jsp?number=<%=sharky.getName()%>"><%=sharky.getName()%>
                            </a></td>
                            <td class="lineitems"><%=sharky.totalEncounters()%>
                            </td>
                            <td class="lineitems"><%=sharky.getSex()%>
                            </td>

                        </tr>
                        <%
                                //	} //end if
                                //	} //end if
                            } //end while
                        %>
                        <tr class="paging">
                            <td width="101" align="left">
                                <%
                                    if (highCount < totalCount) {%> <a
                                    href="allIndividuals.jsp?start=<%=(lowCount+10)%>&amp;end=<%=(highCount+10)%><%=currentSort%>"><%=next%>
                            </a>
                                <%
                                    }
                                    if ((lowCount - 10) >= 1) {
                                %> | <a
                                    href="allIndividuals.jsp?start=<%=(lowCount-10)%>&amp;end=<%=(highCount-10)%><%=currentSort%>"><%=previous%>
                            </a>
                                <%}%>
                            </td>
                            <td align="right" colspan="4"><%=records%>: <%=lowCount%>
                                - <%=highCount%><%=displaySort%>
                            </td>
                        </tr>
                    </table>
                </div>
                <!-- end maintext --></div>
            <!-- end main-wide --></div>
        <!-- end main -->
        <jsp:include page="footer.jsp" flush="true"/>
    </div>
    <!-- end page --></div>
<!--end wrapper -->
<%
    query.closeAll();
    myShepherd.rollbackDBTransaction();
    myShepherd.closeDBTransaction();
    query = null;
    sharkClass = null;
    myShepherd = null;
%>
</body>
</html>