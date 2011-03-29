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
         import="jxl.Workbook,jxl.write.*, org.dom4j.Document, org.dom4j.DocumentHelper, org.dom4j.Element,org.ecocean.*, org.ecocean.servlet.ServletUtilities, java.io.File, java.io.FileOutputStream, java.io.OutputStreamWriter, java.util.*" %>


<html>
<head>
<%!
  public void finalize(WritableWorkbook workbook) {
    try {
      workbook.write();
    } catch (Exception e) {
      System.out.println("Unknown error writing output Excel file...");
      e.printStackTrace();
    }
  }
%>

<%!
  public String addEmails(Vector encs) {

    StringBuffer contributors = new StringBuffer();
    int size = encs.size();
    for (int f = 0; f < size; f++) {

      Encounter tempEnc = (Encounter) encs.get(f);

      //calculate the number of submitter contributors
      if ((tempEnc.getSubmitterEmail() != null) && (!tempEnc.getSubmitterEmail().equals(""))) {
        //check for comma separated list
        if (tempEnc.getSubmitterEmail().indexOf(",") != -1) {
          //break up the string
          StringTokenizer stzr = new StringTokenizer(tempEnc.getSubmitterEmail(), ",");
          while (stzr.hasMoreTokens()) {
            String token = stzr.nextToken();
            if (contributors.indexOf(token) == -1) {
              contributors.append(token + "\n");
            }
          }
        } else if (contributors.indexOf(tempEnc.getSubmitterEmail()) == -1) {
          contributors.append(tempEnc.getSubmitterEmail() + "\n");
        }
      }

      //calculate the number of photographer contributors
      if ((tempEnc.getPhotographerEmail() != null) && (!tempEnc.getPhotographerEmail().equals(""))) {
        //check for comma separated list
        if (tempEnc.getPhotographerEmail().indexOf(",") != -1) {
          //break up the string
          StringTokenizer stzr = new StringTokenizer(tempEnc.getPhotographerEmail(), ",");
          while (stzr.hasMoreTokens()) {
            String token = stzr.nextToken();
            if (contributors.indexOf(token) == -1) {
              contributors.append(token + "\n");
            }
          }
        } else if (contributors.indexOf(tempEnc.getPhotographerEmail()) == -1) {
          contributors.append(tempEnc.getPhotographerEmail() + "\n");
        }
      }


    }

    return contributors.toString();

  } //end for
%>


<%


  //let's load encounterSearch.properties
  String langCode = "en";
  if (session.getAttribute("langCode") != null) {
    langCode = (String) session.getAttribute("langCode");
  }

  Properties encprops = new Properties();
  encprops.load(getClass().getResourceAsStream("/bundles/" + langCode + "/searchResults.properties"));


  Shepherd myShepherd = new Shepherd();

//setup our locale properties for use with Excel export
  Properties props = new Properties();
  try {
    props.load(getClass().getResourceAsStream("/bundles/en/locales.properties"));
  } catch (Exception e) {
    System.out.println("     Could not load locales.properties in the encounter search results.");
    e.printStackTrace();
  }


  int startNum = 1;
  int endNum = 10;

//Let's setup our email export file options
  String emailFilename = "emailResults_" + request.getRemoteUser() + ".txt";
  File emailFile = new File(getServletContext().getRealPath(("/encounters/" + emailFilename)));


//let's set up our Excel spreasheeting operations
  String filenameOBIS = "searchResults_OBIS_" + request.getRemoteUser() + ".xls";
  String filenameExport = "searchResults_" + request.getRemoteUser() + ".xls";
  String kmlFilename = "KMLExport_" + request.getRemoteUser() + ".kml";
  File fileOBIS = new File(getServletContext().getRealPath(("/encounters/" + filenameOBIS)));
  File fileExport = new File(getServletContext().getRealPath(("/encounters/" + filenameExport)));

//let's set up some cell formats
  WritableCellFormat floatFormat = new WritableCellFormat(NumberFormats.FLOAT);
  WritableCellFormat integerFormat = new WritableCellFormat(NumberFormats.INTEGER);

//let's write out headers for the OBIS export file
  WritableWorkbook workbookOBIS = Workbook.createWorkbook(fileOBIS);
  WritableSheet sheet = workbookOBIS.createSheet("Search Results", 0);
  Label label0 = new Label(0, 0, "Date Last Modified");
  sheet.addCell(label0);
  Label label1 = new Label(1, 0, "Institution Code");
  sheet.addCell(label1);
  Label label2 = new Label(2, 0, "Collection Code");
  sheet.addCell(label2);
  Label label2a = new Label(3, 0, "Catalog Number");
  sheet.addCell(label2a);
  Label label3 = new Label(4, 0, "Record URL");
  sheet.addCell(label3);
  Label label5 = new Label(5, 0, "Scientific Name");
  sheet.addCell(label5);
  Label label6 = new Label(6, 0, "Basis of record");
  sheet.addCell(label6);
  Label label7 = new Label(7, 0, "Citation");
  sheet.addCell(label7);
  Label label8 = new Label(8, 0, "Kingdom");
  sheet.addCell(label8);
  Label label9 = new Label(9, 0, "Phylum");
  sheet.addCell(label9);
  Label label10 = new Label(10, 0, "Class");
  sheet.addCell(label10);
  Label label11 = new Label(11, 0, "Order");
  sheet.addCell(label11);
  Label label12 = new Label(12, 0, "Family");
  sheet.addCell(label12);
  Label label13 = new Label(13, 0, "Genus");
  sheet.addCell(label13);
  Label label14 = new Label(14, 0, "species");
  sheet.addCell(label14);
  Label label15 = new Label(15, 0, "Year Identified");
  sheet.addCell(label15);
  Label label16 = new Label(16, 0, "Month Identified");
  sheet.addCell(label16);
  Label label17 = new Label(17, 0, "Day Identified");
  sheet.addCell(label17);
  Label label18 = new Label(18, 0, "Year Collected");
  sheet.addCell(label18);
  Label label19 = new Label(19, 0, "Month Collected");
  sheet.addCell(label19);
  Label label20 = new Label(20, 0, "Day Collected");
  sheet.addCell(label20);
  Label label21 = new Label(21, 0, "Time of Day");
  sheet.addCell(label21);
  Label label22 = new Label(22, 0, "Locality");
  sheet.addCell(label22);
  Label label23 = new Label(23, 0, "Longitude");
  sheet.addCell(label23);
  Label label24 = new Label(24, 0, "Latitude");
  sheet.addCell(label24);
  Label label25 = new Label(25, 0, "Sex");
  sheet.addCell(label25);
  Label label26 = new Label(26, 0, "Notes");
  sheet.addCell(label26);
  Label label27 = new Label(27, 0, "Length (m)");
  sheet.addCell(label27);
  Label label28 = new Label(28, 0, "Marked Individual");
  sheet.addCell(label28);
  Label label29 = new Label(29, 0, "Location code");
  sheet.addCell(label29);

//let's write out headers for the normal export file
  WritableWorkbook workbookExport = Workbook.createWorkbook(fileExport);
  WritableSheet sheetExport = workbookExport.createSheet("Search Results", 0);
  Label label0E = new Label(0, 0, encprops.getProperty("markedIndividual"));
  sheetExport.addCell(label0E);
  Label label0F = new Label(1, 0, encprops.getProperty("number"));
  sheetExport.addCell(label0F);
  Label label1E = new Label(2, 0, encprops.getProperty("alternateID"));
  sheetExport.addCell(label1E);
  Label label2E = new Label(3, 0, encprops.getProperty("submitterName"));
  sheetExport.addCell(label2E);
  Label label2aE = new Label(4, 0, encprops.getProperty("date"));
  sheetExport.addCell(label2aE);
  Label label3E = new Label(5, 0, encprops.getProperty("vessel"));
  sheetExport.addCell(label3E);
  Label label5E = new Label(6, 0, encprops.getProperty("eventID"));
  sheetExport.addCell(label5E);
  Label label6E = new Label(7, 0, encprops.getProperty("location"));
  sheetExport.addCell(label6E);
  Label label7E = new Label(8, 0, encprops.getProperty("locationID"));
  sheetExport.addCell(label7E);


//setup the KML output
  Document document = DocumentHelper.createDocument();
  Element root = document.addElement("kml");
  root.addAttribute("xmlns", "http://www.opengis.net/kml/2.2");
  root.addAttribute("xmlns:gx", "http://www.google.com/kml/ext/2.2");
  Element docElement = root.addElement("Document");

  boolean addTimeStamp = false;
  boolean generateKML = false;
  if (request.getParameter("generateKML") != null) {
    generateKML = true;
  }
  if (request.getParameter("addTimeStamp") != null) {
    addTimeStamp = true;
  }

//add styles first if necessary
//Element styleElement1 = docElement.addElement( "Style" );

//should we generate emails
  boolean generateEmails = false;
  if (request.getParameter("generateEmails") != null) {
    generateEmails = true;
  }


  try {

    if (request.getParameter("startNum") != null) {
      startNum = (new Integer(request.getParameter("startNum"))).intValue();
    }
    if (request.getParameter("endNum") != null) {
      endNum = (new Integer(request.getParameter("endNum"))).intValue();
    }

  } catch (NumberFormatException nfe) {
    startNum = 1;
    endNum = 10;
  }

  int numResults = 0;


  Vector rEncounters = new Vector();

  myShepherd.beginDBTransaction();

  EncounterQueryResult queryResult = EncounterQueryProcessor.processQuery(myShepherd, request, "year descending, month descending, day descending");
  rEncounters = queryResult.getResult();


//--let's estimate the number of results that might be unique

  int numUniqueEncounters = 0;
  int numUnidentifiedEncounters = 0;
  int numDuplicateEncounters = 0;
  ArrayList uniqueEncounters = new ArrayList();
  for (int q = 0; q < rEncounters.size(); q++) {
    Encounter rEnc = (Encounter) rEncounters.get(q);
    if (!rEnc.isAssignedToMarkedIndividual().equals("Unassigned")) {
      String assemblage = rEnc.getIndividualID() + ":" + rEnc.getYear() + ":" + rEnc.getMonth() + ":" + rEnc.getDay();
      if (!uniqueEncounters.contains(assemblage)) {
        numUniqueEncounters++;
        uniqueEncounters.add(assemblage);
      } else {
        numDuplicateEncounters++;
      }
    } else {
      numUnidentifiedEncounters++;
    }

  }

//--end unique counting------------------------------------------


//let's print out the contributors file
  if (generateEmails) {
    try {
      String contribs = addEmails(rEncounters);
      FileOutputStream fos = new FileOutputStream(emailFile);
      OutputStreamWriter outp = new OutputStreamWriter(fos);
      outp.write(contribs);
      outp.close();
    } catch (Exception e) {
      e.printStackTrace();
%>
<p>Failed to write out the contributors file!</p>
<%
    }

  }
%>
<title><%=CommonConfiguration.getHTMLTitle()%>
</title>
<meta http-equiv="Content-Type" content="text/html; charset=utf-8"/>
<meta name="Description"
      content="<%=CommonConfiguration.getHTMLDescription()%>"/>
<meta name="Keywords"
      content="<%=CommonConfiguration.getHTMLKeywords()%>"/>
<meta name="Author" content="<%=CommonConfiguration.getHTMLAuthor()%>"/>
<link href="<%=CommonConfiguration.getCSSURLLocation(request)%>"
      rel="stylesheet" type="text/css"/>
<link rel="shortcut icon"
      href="<%=CommonConfiguration.getHTMLShortcutIcon()%>"/>
</head>

<style type="text/css">
  #tabmenu {
    color: #000;
    border-bottom: 2px solid black;
    margin: 12px 0px 0px 0px;
    padding: 0px;
    z-index: 1;
    padding-left: 10px
  }

  #tabmenu li {
    display: inline;
    overflow: hidden;
    list-style-type: none;
  }

  #tabmenu a, a.active {
    color: #DEDECF;
    background: #000;
    font: bold 1em "Trebuchet MS", Arial, sans-serif;
    border: 2px solid black;
    padding: 2px 5px 0px 5px;
    margin: 0;
    text-decoration: none;
    border-bottom: 0px solid #FFFFFF;
  }

  #tabmenu a.active {
    background: #FFFFFF;
    color: #000000;
    border-bottom: 2px solid #FFFFFF;
  }

  #tabmenu a:hover {
    color: #ffffff;
    background: #7484ad;
  }

  #tabmenu a:visited {
    color: #E8E9BE;
  }

  #tabmenu a.active:hover {
    background: #7484ad;
    color: #DEDECF;
    border-bottom: 2px solid #000000;
  }
</style>


<body onload="initialize()" onunload="GUnload()">
<div id="wrapper">
<div id="page">
<jsp:include page="../header.jsp" flush="true">

  <jsp:param name="isAdmin" value="<%=request.isUserInRole(\"admin\")%>" />
</jsp:include>
<div id="main">

<ul id="tabmenu">

  <li><a class="active"><%=encprops.getProperty("table")%>
  </a></li>
  <li><a
    href="thumbnailSearchResults.jsp?<%=request.getQueryString().replaceAll("startNum","uselessNum").replaceAll("endNum","uselessNum") %>"><%=encprops.getProperty("matchingImages")%>
  </a></li>
  <li><a
    href="mappedSearchResults.jsp?<%=request.getQueryString().replaceAll("startNum","uselessNum").replaceAll("endNum","uselessNum") %>"><%=encprops.getProperty("mappedResults")%>
  </a></li>
  <li><a
    href="../xcalendar/calendar2.jsp?<%=request.getQueryString().replaceAll("startNum","uselessNum").replaceAll("endNum","uselessNum") %>"><%=encprops.getProperty("resultsCalendar")%>
  </a></li>

</ul>


<table width="810px" border="0" cellspacing="0" cellpadding="0">
  <tr>
    <td>
      <p>

      <h1 class="intro"><%=encprops.getProperty("title")%>
      </h1>
      </p>    <p><%=encprops.getProperty("belowMatches")%>
    </p>
    </td>
  </tr>
</table>

<%
  if (request.getParameter("export") != null) {
%>
<p><%=encprops.getProperty("exportedExcel")%>:
  <a
    href="http://<%=CommonConfiguration.getURLLocation(request)%>/encounters/<%=filenameExport%>"><%=filenameExport%>
  </a><br>
  <em><%=encprops.getProperty("rightClickLink")%>
  </em>
</p>

<p><%=encprops.getProperty("exportedOBIS")%>:
  <a
    href="http://<%=CommonConfiguration.getURLLocation(request)%>/encounters/<%=filenameOBIS%>"><%=filenameOBIS%>
  </a><br>
  <em><%=encprops.getProperty("rightClickLink")%>
  </em>
</p>
<%
  }
%> <%
  if (request.getParameter("generateKML") != null) {
%>
<p><%=encprops.getProperty("exportedKML")%>: <a
  href="http://<%=CommonConfiguration.getURLLocation(request)%>/encounters/<%=kmlFilename%>"><%=kmlFilename%>
</a><br>
  <em><%=encprops.getProperty("rightClickLink")%>
  </em>
</p>
<%
  }
%> <%
  if (generateEmails) {
%>
<p><%=encprops.getProperty("exportedEmail")%>: <a
  href="http://<%=CommonConfiguration.getURLLocation(request)%>/encounters/<%=emailFilename%>"><%=emailFilename%>
</a><br>
  <em><%=encprops.getProperty("rightClickLink")%>
  </em>
</p>
<%
  }
%>

<table width="810px">
<tr>
  <td class="lineitem" bgcolor="#99CCFF"></td>
  <td class="lineitem" align="left" valign="top" bgcolor="#99CCFF">
    <strong><%=encprops.getProperty("markedIndividual")%>
    </strong></td>
  <td class="lineitem" align="left" valign="top" bgcolor="#99CCFF">
    <strong><%=encprops.getProperty("number")%>
    </strong></td>
  <td class="lineitem" align="left" valign="top" bgcolor="#99CCFF">
    <strong><%=encprops.getProperty("alternateID")%>
    </strong></td>
  <td class="lineitem" align="left" valign="top" bgcolor="#99CCFF">
    <strong><%=encprops.getProperty("submitterName")%>
    </strong></td>
  <td class="lineitem" align="left" valign="top" bgcolor="#99CCFF">
    <strong><%=encprops.getProperty("date")%>
    </strong></td>

  <td class="lineitem" align="left" valign="top" bgcolor="#99CCFF">
    <strong><%=encprops.getProperty("location")%>
    </strong></td>
  <td class="lineitem" align="left" valign="top" bgcolor="#99CCFF">
    <strong><%=encprops.getProperty("locationID")%>
    </strong></td>
</tr>

<%
  Vector haveGPSData = new Vector();
  int count = 0;

  for (int f = 0; f < rEncounters.size(); f++) {

    Encounter enc = (Encounter) rEncounters.get(f);
    count++;
    numResults++;
    if ((enc.getDWCDecimalLatitude() != null) && (enc.getDWCDecimalLongitude() != null)) {
      haveGPSData.add(enc);
    }


    if ((numResults >= startNum) && (numResults <= endNum)) {
%>
<tr class="lineitem">
  <td width="100" class="lineitem"><img src="<%=(enc.getEncounterNumber()+"/thumb.jpg")%>"></td>

  <%
    if (enc.isAssignedToMarkedIndividual().trim().toLowerCase().equals("unassigned")) {
  %>
  <td class="lineitem"><%=encprops.getProperty("unassigned")%>
  </td>
  <%
  } else {
  %>
  <td class="lineitem"><a
    href="../individuals.jsp?number=<%=enc.isAssignedToMarkedIndividual()%>"><%=enc.isAssignedToMarkedIndividual()%>
  </a></td>
  <%
    }
  %>
  <td class="lineitem"><a
    href="http://<%=CommonConfiguration.getURLLocation(request)%>/encounters/encounter.jsp?number=<%=enc.getEncounterNumber()%>"><%=enc.getEncounterNumber()%>
  </a>
  </td>
  <td class="lineitem">
    <%
      if ((enc.getAlternateID() != null) && (!enc.getAlternateID().equals("None"))) {
    %>
    <%=enc.getAlternateID()%><%
  } else {
  %>
    None
    <%
      }
    %>
  </td>

  <td class="lineitem"><%=enc.getSubmitterName()%>
  </td>
  <td class="lineitem"><%=enc.getDate()%>
  </td>

  <td class="lineitem"><%=enc.getLocation()%>
  </td>
  <td class="lineitem"><%=enc.getLocationCode()%>
  </td>
</tr>
<%
    } //end if to control number displayed

    // Excel export =========================================================

    if ((request.getParameter("export") != null) && (ServletUtilities.isUserAuthorizedForEncounter(enc, request))) {
      try {

        //OBIS formt export
        Label lNumber = new Label(0, count, enc.getDWCDateLastModified());
        sheet.addCell(lNumber);
        Label lNumberx1 = new Label(1, count, CommonConfiguration.getProperty("institutionCode"));
        sheet.addCell(lNumberx1);
        Label lNumberx2 = new Label(2, count, CommonConfiguration.getProperty("catalogCode"));
        sheet.addCell(lNumberx2);
        Label lNumberx3 = new Label(3, count, enc.getEncounterNumber());
        sheet.addCell(lNumberx3);
        Label lNumberx4 = new Label(4, count, ("http://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" + enc.getEncounterNumber()));
        sheet.addCell(lNumberx4);
        Label lNumberx5 = new Label(5, count, (CommonConfiguration.getProperty("genus") + " " + CommonConfiguration.getProperty("species")));
        sheet.addCell(lNumberx5);
        Label lNumberx6 = new Label(6, count, "P");
        sheet.addCell(lNumberx6);
        Calendar toDay = Calendar.getInstance();
        int year = toDay.get(Calendar.YEAR);
        Label lNumberx7 = new Label(7, count, CommonConfiguration.getProperty("citation"));
        sheet.addCell(lNumberx7);
        Label lNumberx8 = new Label(8, count, CommonConfiguration.getProperty("kingdom"));
        sheet.addCell(lNumberx8);
        Label lNumberx9 = new Label(9, count, CommonConfiguration.getProperty("phylum"));
        sheet.addCell(lNumberx9);
        Label lNumberx10 = new Label(10, count, CommonConfiguration.getProperty("class"));
        sheet.addCell(lNumberx10);
        Label lNumberx11 = new Label(11, count, CommonConfiguration.getProperty("order"));
        sheet.addCell(lNumberx11);
        Label lNumberx13 = new Label(12, count, CommonConfiguration.getProperty("family"));
        sheet.addCell(lNumberx13);
        Label lNumberx14 = new Label(13, count, CommonConfiguration.getProperty("genus"));
        sheet.addCell(lNumberx14);
        Label lNumberx15 = new Label(14, count, CommonConfiguration.getProperty("species"));
        sheet.addCell(lNumberx15);
        if (enc.getYear() > 0) {
          Label lNumberx16 = new Label(15, count, Integer.toString(enc.getYear()));
          sheet.addCell(lNumberx16);
          Label lNumberx19 = new Label(18, count, Integer.toString(enc.getYear()));
          sheet.addCell(lNumberx19);
        }
        if (enc.getMonth() > 0) {
          Label lNumberx17 = new Label(16, count, Integer.toString(enc.getMonth()));
          sheet.addCell(lNumberx17);
          Label lNumberx20 = new Label(19, count, Integer.toString(enc.getMonth()));
          sheet.addCell(lNumberx20);
        }
        if (enc.getDay() > 0) {
          Label lNumberx18 = new Label(17, count, Integer.toString(enc.getDay()));
          sheet.addCell(lNumberx18);
          Label lNumberx21 = new Label(20, count, Integer.toString(enc.getDay()));
          sheet.addCell(lNumberx21);
        }
        Label lNumberx22 = new Label(21, count, (enc.getDay() + ":" + enc.getMinutes()));
        sheet.addCell(lNumberx22);
        Label lNumberx23 = new Label(22, count, enc.getLocation());
        sheet.addCell(lNumberx23);
        if ((enc.getDWCDecimalLatitude() != null) && (enc.getDWCDecimalLongitude() != null)) {
          Label lNumberx24 = new Label(23, count, enc.getDWCDecimalLongitude());
          sheet.addCell(lNumberx24);
          Label lNumberx25 = new Label(24, count, enc.getDWCDecimalLatitude());
          sheet.addCell(lNumberx25);
        }
        //check for available locale coordinates
        //this functionality is primarily used for data export to iobis.org
        else if ((enc.getLocationCode() != null) && (!enc.getLocationCode().equals(""))) {
          try {
            String lc = enc.getLocationCode();
            if (props.getProperty(lc) != null) {
              String gps = props.getProperty(lc);
              StringTokenizer st = new StringTokenizer(gps, ",");
              Label lNumberx25 = new Label(24, count, st.nextToken());
              sheet.addCell(lNumberx25);
              Label lNumberx24 = new Label(23, count, st.nextToken());
              sheet.addCell(lNumberx24);
            }
          } catch (Exception e) {
            e.printStackTrace();
            System.out.println("     I hit an error getting locales in searchResults.jsp.");
          }
        }
        if (!enc.getSex().equals("unknown")) {
          Label lSex = new Label(25, count, enc.getSex());
          sheet.addCell(lSex);
        }
        Label lNumberx26 = new Label(26, count, enc.getComments().replaceAll("<br>", ". ").replaceAll("\n", "").replaceAll("\r", ""));
        sheet.addCell(lNumberx26);

        if (enc.getSize() > 0) {
          Label lNumberx27 = new Label(27, count, Double.toString(enc.getSize()));
          sheet.addCell(lNumberx27);
        }
        if (!enc.isAssignedToMarkedIndividual().equals("Unassigned")) {
          Label lNumberx28 = new Label(28, count, enc.isAssignedToMarkedIndividual());
          sheet.addCell(lNumberx28);
        }
        if (enc.getLocationCode() != null) {
          Label lNumberx29 = new Label(29, count, enc.getLocationCode());
          sheet.addCell(lNumberx29);
        }


        //whew - now let's generate the simple export format
        if ((enc.isAssignedToMarkedIndividual() != null) && (!enc.isAssignedToMarkedIndividual().equals("Unassigned"))) {
          Label lNumberx28e = new Label(0, count, enc.isAssignedToMarkedIndividual());
          sheetExport.addCell(lNumberx28e);
        }
        Label lNumberx29e = new Label(1, count, enc.getEncounterNumber());
        sheetExport.addCell(lNumberx29e);
        if (enc.getAlternateID() != null) {
          Label lNumberx30e = new Label(2, count, enc.getAlternateID());
          sheetExport.addCell(lNumberx30e);
        }
        if (enc.getSubmitterName() != null) {
          Label lNumberx31e = new Label(3, count, enc.getSubmitterName());
          sheetExport.addCell(lNumberx31e);
        }
        Label lNumberx32e = new Label(4, count, enc.getDate());
        sheetExport.addCell(lNumberx32e);
        if (enc.getDynamicPropertyValue("Vessel") != null) {
          Label lNumberx33e = new Label(5, count, enc.getDynamicPropertyValue("Vessel"));
          sheetExport.addCell(lNumberx33e);
        }
        if (enc.getEventID() != null) {
          Label lNumberx34e = new Label(6, count, enc.getEventID());
          sheetExport.addCell(lNumberx34e);
        }
        if (enc.getLocation() != null) {
          Label lNumberx35e = new Label(7, count, enc.getLocation());
          sheetExport.addCell(lNumberx35e);
        }
        if (enc.getLocationID() != null) {
          Label lNumberx36e = new Label(8, count, enc.getLocationID());
          sheetExport.addCell(lNumberx36e);
        }
      } catch (Exception we) {
        System.out.println("jExcel error processing search results...");
        we.printStackTrace();
      }
    }


  } //end while

  // end Excel export =========================================================
%>
</table>


<%
  if ((request.getParameter("export") != null) && (request.getParameter("startNum") == null)) {
    finalize(workbookOBIS);
    finalize(workbookExport);
  }
  workbookOBIS.close();
  workbookExport.close();

  myShepherd.rollbackDBTransaction();

  startNum = startNum + 10;
  endNum = endNum + 10;

  if (endNum > numResults) {
    endNum = numResults;
  }
  String numberResights = "";
  if (request.getParameter("numResights") != null) {
    numberResights = "&numResights=" + request.getParameter("numResights");
  }
  String qString = request.getQueryString();
  int startNumIndex = qString.indexOf("&startNum");
  if (startNumIndex > -1) {
    qString = qString.substring(0, startNumIndex);
  }

%>
<table width="810px">
  <tr>
    <%
      if ((startNum - 10) > 1) {
    %>
    <td align="left">
      <p>
        <a
          href="searchResults.jsp?<%=qString%><%=numberResights%>&startNum=<%=(startNum-20)%>&endNum=<%=(startNum-11)%>"><img
          src="../images/Black_Arrow_left.png" width="28" height="28" border="0" align="absmiddle"
          title="<%=encprops.getProperty("seePreviousResults")%>"/></a> <a
        href="searchResults.jsp?<%=qString%><%=numberResights%>&startNum=<%=(startNum-20)%>&endNum=<%=(startNum-11)%>"><%=(startNum - 20)%>
        - <%=(startNum - 11)%>
      </a>
      </p></td>
    <%
      }
      if (startNum < numResults) {
    %>
    <td align="right">
      <p><a
        href="searchResults.jsp?<%=qString%><%=numberResights%>&startNum=<%=startNum%>&endNum=<%=endNum%>"><%=startNum%>
        - <%=endNum%>
      </a> <a
        href="searchResults.jsp?<%=qString%><%=numberResights%>&startNum=<%=startNum%>&endNum=<%=endNum%>"><img
        src="../images/Black_Arrow_right.png" border="0" align="absmiddle"
        title="<%=encprops.getProperty("seeNextResults")%>"/></a>

      </p>
    </td>
    <%
      }
    %>
  </tr>
</table>
<p>
<table width="810" border="0" cellspacing="0" cellpadding="0">
  <tr>
    <td align="left">
      <p><strong><%=encprops.getProperty("matchingEncounters")%>
      </strong>: <%=numResults%>
        <%
          if (request.isUserInRole("admin")) {
        %>
        <br/>
        <%=numUniqueEncounters%> <%=encprops.getProperty("identifiedUnique")%><br/>
        <%=numUnidentifiedEncounters%> <%=encprops.getProperty("unidentified")%><br/>
        <%=(numDuplicateEncounters)%> <%=encprops.getProperty("dailyDuplicates")%>
        <%
          }
        %>
      </p>
      <%
        myShepherd.beginDBTransaction();
      %>
      <p><strong><%=encprops.getProperty("totalEncounters")%>
      </strong>: <%=(myShepherd.getNumEncounters() + (myShepherd.getNumUnidentifiableEncounters()))%>
      </p>
    </td>
    <%
      myShepherd.rollbackDBTransaction();
    %>
  </tr>
</table>

<table>
  <tr>
    <td align="left">

      <p><strong><%=encprops.getProperty("queryDetails")%>
      </strong></p>

      <p class="caption"><strong><%=encprops.getProperty("prettyPrintResults") %>
      </strong><br/>
        <%=queryResult.getQueryPrettyPrint().replaceAll("locationField", encprops.getProperty("location")).replaceAll("locationCodeField", encprops.getProperty("locationID")).replaceAll("verbatimEventDateField", encprops.getProperty("verbatimEventDate")).replaceAll("alternateIDField", encprops.getProperty("alternateID")).replaceAll("behaviorField", encprops.getProperty("behavior")).replaceAll("Sex", encprops.getProperty("sex")).replaceAll("nameField", encprops.getProperty("nameField")).replaceAll("selectLength", encprops.getProperty("selectLength")).replaceAll("numResights", encprops.getProperty("numResights")).replaceAll("vesselField", encprops.getProperty("vesselField"))%>
      </p>

      <p class="caption"><strong><%=encprops.getProperty("jdoql")%>
      </strong><br/>
        <%=queryResult.getJDOQLRepresentation()%>
      </p>

    </td>
  </tr>
</table>


</p>
<br>

<%
  myShepherd.rollbackDBTransaction();
  myShepherd.closeDBTransaction();
  rEncounters = null;

%>
<jsp:include page="../footer.jsp" flush="true"/>
</div>
</div>
<!-- end page --></div>
<!--end wrapper -->

</body>
</html>




