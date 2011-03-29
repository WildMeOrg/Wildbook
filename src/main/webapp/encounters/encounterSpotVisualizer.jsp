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
         import="commonSense.math.linear.Matrix, org.ecocean.*,java.awt.*,java.io.IOException, java.io.InputStream, java.net.URL, java.net.URLConnection, java.util.ArrayList" %>
<%@ taglib uri="http://www.sunwesttek.com/di" prefix="di" %>


<%!
  public static double angle(double x1, double y1, double x2, double y2) {
    double dx = x2 - x1, dy = y2 - y1, PI = (float) Math.PI;
    double angle = 0.0f;

    if (dx == 0)
      if (dy == 0) angle = 0;
      else if (dy > 0) angle = PI / 2;
      else angle = PI * 3 / 2;
    else if (dy == 0)
      if (dx > 0) angle = 0;
      else angle = PI;
    else if (dx < 0) angle = Math.atan(dy / dx) + PI;
    else if (dy < 0) angle = Math.atan(dy / dx) + (2 * PI);
    else angle = Math.atan(dy / dx);

    return angle;
  }
%>


<%
  String num = request.getParameter("number");
//int number=(new Integer(num)).intValue();
  Shepherd myShepherd = new Shepherd();
  boolean proceed = true;
  String side = "Left";

//mahalanobis variables
  int intThisClusterCentroidX = 0;
  int intThisClusterCentroidY = 0;
  double[] spots_MahaDistances = new double[0];
  SuperSpot[] newSpots = new SuperSpot[0];

  if ((request.getParameter("rightSide") != null) && (request.getParameter("rightSide").equals("true"))) {
    side = "Right";
  }

%>

<html>
<head>

  <title>Encounter <%=num%></title>
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
//if (myShepherd.isEncounter(num)) {
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
  try {
    connEnc = encURL.openConnection();
    //System.out.println("Opened new encounter connection");
    encStream = connEnc.getInputStream();

  } catch (IOException ioe) {
    System.out.println("I failed to get the image input stream while using the spotVisualizer");
    canDirectMap = false;
%><p>I could not connect to and find the spot image.</p>
<%
  }


  //ImageInfo newEncII=new ImageInfo();
  Dimension imageDimensions = org.apache.sanselan.Sanselan.getImageSize(encStream, ("extract" + num + ".jpg"));


  if (!canDirectMap) {
    //newEncII.setInput(encStream);
    //if(!newEncII.check()){canDirectMap=false;System.out.println("I could not read the encounter image file while using the spotVisualizer.");
%>
<p>I could not connect to and find the spot image.</p>
<%
} else {
  int encImageWidth = (int) imageDimensions.getWidth();
  int encImageHeight = (int) imageDimensions.getHeight();
  int numSpots = 0;
  if (side.equals("Right")) {
    numSpots = enc.getRightSpots().size();
  } else {
    numSpots = enc.getSpots().size();
  }
  StringBuffer xmlData = new StringBuffer();

  //xmlData.append("&lt;?xml version=\"1.0\" encoding=\"UTF-8\"?&gt;<br>&lt;encounter number=\""+num+"\" assignedToShark=\""+enc.isAssignedToMarkedIndividual()+"\"&gt;<br>");

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

    double thisClusterCentroidX = 0;
    double thisClusterCentroidY = 0;
    double[][] thisMahalArray = new double[spots.size()][2];
    for (int sm = 0; sm < spots.size(); sm++) {
      thisMahalArray[sm][0] = ((SuperSpot) spots.get(sm)).getTheSpot().getCentroidX();
      thisMahalArray[sm][1] = ((SuperSpot) spots.get(sm)).getTheSpot().getCentroidY();
      thisClusterCentroidX = thisClusterCentroidX + ((SuperSpot) spots.get(sm)).getTheSpot().getCentroidX();
      thisClusterCentroidY = thisClusterCentroidY + ((SuperSpot) spots.get(sm)).getTheSpot().getCentroidY();
    }
    commonSense.math.linear.Matrix thisM = new Matrix(thisMahalArray);
    intThisClusterCentroidX = (int) (thisClusterCentroidX / spots.size());
    intThisClusterCentroidY = (int) (thisClusterCentroidY / spots.size());

    newSpots = new SuperSpot[spots.size()];

    //get the Mahalanobis distances
    spots_MahaDistances = commonSense.math.linear.Distances.mahalanobis(thisM);

    double[] p0 = new double[3];
    p0[0] = intThisClusterCentroidX;
    p0[1] = intThisClusterCentroidY;
    p0[2] = 0;
    for (int sm = 0; sm < spots.size(); sm++) {


      double[] pi = new double[3];
      pi[0] = ((SuperSpot) spots.get(sm)).getTheSpot().getCentroidX();
      pi[1] = ((SuperSpot) spots.get(sm)).getTheSpot().getCentroidY();
      pi[2] = 0;

      double[] p0i = new double[3];
      p0i[0] = pi[0] - p0[0];
      p0i[1] = pi[1] - p0[1];
      p0i[2] = 0;
      double thetai = Math.atan2(p0i[1], p0i[0]);
      //double thetai=Math.atan2(pi[1]-p0i[1],pi[0]-p0i[0])*180.0/Math.PI;
      //double thetai=angle(p0i[0], p0i[1],pi[0], pi[1]);

      double i_mahaX = 4 * spots_MahaDistances[sm] * Math.cos(thetai);
      double i_mahaY = 4 * spots_MahaDistances[sm] * Math.sin(thetai);

      //quadrant 1
      //if((pi[0]>=p0[0])&&(pi[1]>=p0[1])){Math.abs(i_mahaX);Math.abs(i_mahaY);}
      //quadrant 2
      //else if((pi[0]>=p0[0])&&(pi[1]<=p0[1])) {Math.abs(i_mahaX);Math.abs(i_mahaY);i_mahaY=i_mahaY*-1;}
      //quadrant 3
      //else if((pi[0]<=p0[0])&&(pi[1]<=p0[1])) {Math.abs(i_mahaX);Math.abs(i_mahaY);i_mahaY=i_mahaY*-1;i_mahaX=i_mahaX*-1;}
      //quadrant 4
      //else {Math.abs(i_mahaX);Math.abs(i_mahaY);i_mahaX=i_mahaX*-1;}

      newSpots[sm] = new SuperSpot(new Spot(0, (i_mahaX + intThisClusterCentroidX), (i_mahaY + intThisClusterCentroidY)));


    }
    if (request.getRemoteUser().equals("nobody")) {
      for (int numIter2 = 0; numIter2 < numSpots; numIter2++) {
        int theX = ((int) (newSpots[numIter2].getTheSpot().getCentroidX()));
        int theY = ((int) (newSpots[numIter2].getTheSpot().getCentroidY()));
        //xmlData.append("     &lt;spot centroidX=\""+spots[numIter2].getTheSpot().getCentroidX()+"\" centroidY=\""+spots[numIter2].getTheSpot().getCentroidY()+"\"/&gt;<br>");
  %>
  <di:circle x="<%=theX%>" y="<%=theY%>" radius="4" fillPaint="#006400"></di:circle>

  <%
    } //end for

    //display the x and y center averages

  %>
  <di:circle x="<%=intThisClusterCentroidX%>"
             y="<%=intThisClusterCentroidY%>" radius="4" fillPaint="#00FF00"></di:circle>
  <%}%>
</di:img>

<!-- Put the image URL in now -->
<img src="<%=(num+"/"+side+"SideSpotsMapped.jpg")%>" border="0" align="left" valign="left">


<%if (request.getRemoteUser().equals("admin")) {%>

<p>Mahalanobis distances:</p>
<%for (int numIter2 = 0; numIter2 < numSpots; numIter2++) {%> <br><%=spots_MahaDistances[numIter2]%>

<%
    }
  }
%>


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
      <jsp:include page="../footer.jsp" flush="true"></jsp:include>
    </td>
  </tr>
</table>
</div>
</div>
</div>
</body>
</html>


