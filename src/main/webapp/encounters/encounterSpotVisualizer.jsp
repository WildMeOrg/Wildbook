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
         import="org.ecocean.*,java.awt.*,java.io.IOException, java.io.InputStream, java.net.URL, java.net.URLConnection, java.util.ArrayList" %>
<%@ taglib uri="http://www.sunwesttek.com/di" prefix="di" %>





<%
  String num = request.getParameter("number");
//int number=(new Integer(num)).intValue();
  Shepherd myShepherd = new Shepherd();
  boolean proceed = true;
  String side = "Left";

//mahalanobis variables
 // int intThisClusterCentroidX = 0;
 // int intThisClusterCentroidY = 0;
 // double[] spots_MahaDistances = new double[0];
 // SuperSpot[] newSpots = new SuperSpot[0];

  if ((request.getParameter("rightSide") != null) && (request.getParameter("rightSide").equals("true"))) {
    side = "Right";
  }

%>

<html>
<head>

  <title>Spot Visualization for Encounter <%=num%></title>
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

<body bgcolor="#FFFFFF" link="#990000">
<div id="wrapper">
<div id="page">
<jsp:include page="../header.jsp" flush="true">

  <jsp:param name="isAdmin" value="<%=request.isUserInRole(\"admin\")%>" />
  
</jsp:include>

<div id="main">
<%
  myShepherd.beginDBTransaction();
if (myShepherd.isEncounter(num)) {
    Encounter enc = myShepherd.getEncounter(num);


%>


<table cellpadding="5">
<tr>
<td>
<p><font size="4"><strong><%=side%>-side Spot
  Visualization for Encounter Number: <a
    href="encounter.jsp?number=<%=num%>"><%=enc.getEncounterNumber()%>
  </a>
</strong></font></p>


<%
  if (enc.isAssignedToMarkedIndividual().equals("Unassigned")) {
%>
<p><font size="-1">Belongs to: <%=enc.isAssignedToMarkedIndividual()%>
</font></p>
<%
} else {
%>
<p><font size="-1">Belongs to: <a
  href="../individuals.jsp?number=<%=enc.isAssignedToMarkedIndividual()%>"><%=enc.isAssignedToMarkedIndividual()%>
</a></font></p>
<%
  }
  if ((side.equals("Right")) && (enc.getRightSpots() == null)) {
%>
<p>No right-side spot data is available for this encounter.</p>
<%
} else if ((side.equals("Left")) && (enc.getSpots() == null)) {
%>
<p>No left-side spot data is available for this encounter.</p>


<%
} else if ((side.equals("Right")) && (enc.rightSpotImageFileName.equals(""))) {
%>
<p>No right-side spot extraction image has been defined for this
  encounter.</p>

<%
} else if ((side.equals("Left")) && (enc.spotImageFileName.equals(""))) {
%>
<p>No spot extraction image has been defined for this encounter.</p>
<%
} else {


  //now let's set up the image mapping variables as needed
  String fileloc = "";
  if ((request.getParameter("rightSide") != null) && (request.getParameter("rightSide").equals("true"))) {
    fileloc = (enc.getEncounterNumber() + "/extractRight" + num + ".jpg");
  } else {
    fileloc = (enc.getEncounterNumber() + "/extract" + num + ".jpg");
  }
  URL encURL = new URL("http://" + CommonConfiguration.getURLLocation(request) + "/encounters/" + fileloc);
  //System.out.println(encURL.toString());
  URLConnection connEnc;
  InputStream encStream = null;
  boolean canDirectMap = true;
  Dimension imageDimensions = null;
  try {
    connEnc = encURL.openConnection();
    //System.out.println("Opened new encounter connection");
    encStream = connEnc.getInputStream();
    imageDimensions = org.apache.sanselan.Sanselan.getImageSize(encStream, ("extract" + num + ".jpg"));

  } 
  catch (IOException ioe) {
    System.out.println("I failed to get the image input stream while using the spotVisualizer");
    canDirectMap = false;
	%>
	<p>I could not connect to and find the spot image.</p>
	<p><%=encURL.toString() %></p>
	<%
  }

  if (canDirectMap) {
  	int encImageWidth = (int) imageDimensions.getWidth();
  	int encImageHeight = (int) imageDimensions.getHeight();
  	int numSpots = 0;
 	 if (side.equals("Right")) {
    	numSpots = enc.getRightSpots().size();
  	} 
 	else {
    	numSpots = enc.getSpots().size();
  	}
  	StringBuffer xmlData = new StringBuffer();

  	String thumbLocation = "file-" + num + "/" + side + "SideSpotsMapped.jpg";

	%>
	<di:img width="<%=encImageWidth%>"
        height="<%=encImageHeight%>"
        imgParams="rendering=speed,quality=low" border="0" expAfter="0"
        threading="limited" fillPaint="#000000" align="top" valign="left"
        output="<%=thumbLocation %>">
  	<di:image srcurl="<%=fileloc%>"/>
  	<%


    ArrayList spots = enc.getSpots();


    ArrayList refSpots = null;

    try {
      refSpots = enc.getLeftReferenceSpots();
    } catch (Exception e) {
    }

    if (side.equals("Right")) {
      spots = enc.getRightSpots();
      try {
        refSpots = enc.getRightReferenceSpots();
      } catch (Exception e) {
      }
    }
    for (int numIter2 = 0; numIter2 < numSpots; numIter2++) {
      int theX = (int) ((SuperSpot) spots.get(numIter2)).getTheSpot().getCentroidX();
      int theY = (int) ((SuperSpot) spots.get(numIter2)).getTheSpot().getCentroidY();
      xmlData.append("     &lt;spot centroidX=\"" + ((SuperSpot) spots.get(numIter2)).getTheSpot().getCentroidX() + "\" centroidY=\"" + ((SuperSpot) spots.get(numIter2)).getTheSpot().getCentroidY() + "\"/&gt;<br>");
  %>
  <di:circle x="<%=theX%>" y="<%=theY%>" radius="4" fillPaint="#FF0000"></di:circle>

  <%
    } //end for

    //now map reference spots if they exist
    try {
      if (refSpots != null) {
        int theX1 = ((int) (((SuperSpot) refSpots.get(0)).getTheSpot().getCentroidX()));
        int theY1 = ((int) (((SuperSpot) refSpots.get(0)).getTheSpot().getCentroidY()));
        xmlData.append("     &lt;refspot centroidX=\"" + theX1 + "\" centroidY=\"" + theY1 + "\"/&gt;<br>");
        int theX2 = ((int) (((SuperSpot) refSpots.get(1)).getTheSpot().getCentroidX()));
        int theY2 = ((int) (((SuperSpot) refSpots.get(1)).getTheSpot().getCentroidY()));
        xmlData.append("     &lt;refspot centroidX=\"" + theX2 + "\" centroidY=\"" + theY2 + "\"/&gt;<br>");

        int theX3 = ((int) (((SuperSpot) refSpots.get(2)).getTheSpot().getCentroidX()));
        int theY3 = ((int) (((SuperSpot) refSpots.get(2)).getTheSpot().getCentroidY()));

        xmlData.append("     &lt;refspot centroidX=\"" + theX3 + "\" centroidY=\"" + theY3 + "\"/&gt;<br>");

  %>
  <di:circle x="<%=theX1%>" y="<%=theY1%>" radius="4"
             fillPaint="#FF9900"></di:circle>
  <%if (side.equals("Right")) {%>
  <di:text font="Arial-bold-12" fillPaint="#FF9900" x="<%=(theX1-45)%>"
           y="<%=(theY1+15)%>">5th top</di:text>
  <%} else {%>
  <di:text font="Arial-bold-12" fillPaint="#FF9900" x="<%=(theX1)%>"
           y="<%=(theY1+15)%>">5th top</di:text>
  <%}%>

  <di:circle x="<%=theX2%>" y="<%=theY2%>" radius="4"
             fillPaint="#FF9900"></di:circle>
  <%if (side.equals("Right")) {%>
  <di:text font="Arial-bold-12" fillPaint="#FF9900" x="<%=(theX2+10)%>"
           y="<%=(theY2+15)%>">posterior pectoral</di:text>
  <%} else {%>
  <di:text font="Arial-bold-12" fillPaint="#FF9900"
           x="<%=(theX2-120)%>" y="<%=(theY2+15)%>">posterior pectoral</di:text>
  <%}%>

  <di:circle x="<%=theX3%>" y="<%=theY3%>" radius="4"
             fillPaint="#FF9900"></di:circle>
  <%if (side.equals("Right")) {%>
  <di:text font="Arial-bold-12" fillPaint="#FF9900" x="<%=(theX3-60)%>"
           y="<%=(theY3+15)%>">5th bottom</di:text>
  <%} else {%>
  <di:text font="Arial-bold-12" fillPaint="#FF9900" x="<%=(theX3)%>"
           y="<%=(theY3+15)%>">5th bottom</di:text>
  <%}%>
  <%
      }
    } catch (Exception e) {
    }

    
    
%>
</di:img>

<!-- Put the image URL in now -->
<img src="<%=(num+"/"+side+"SideSpotsMapped.jpg")%>" border="0" align="left" valign="left">




</td>
</tr>
</table>

<% }


}


} else {%>
<p>There is no encounter <%=request.getParameter("number")%> in the
  database. Please double-check the encounter number and try again.</p>

<form action="encounter.jsp" method="post" name="encounter"><strong>Go
  to encounter: </strong> <input name="number" type="text"
                                 value="<%=request.getParameter("number")%>" size="20"> <input
  name="Go" type="submit" value="Submit"></form>
<p><font color="#990000"><a href="allEncounters.jsp">View
  all encounters</a></font></p>

<p><font color="#990000"><a href="../allIndividuals.jsp">View
  all sharks</a></font></p>

<p></p>
<%
  }
  myShepherd.rollbackDBTransaction();
  myShepherd.closeDBTransaction();
%>
<table width="800" cellpadding="5">
  <tr>
    <td>
      
    </td>
  </tr>
</table>
<jsp:include page="../footer.jsp" flush="true"/>
</div>
</div>
</div>
</body>
</html>


