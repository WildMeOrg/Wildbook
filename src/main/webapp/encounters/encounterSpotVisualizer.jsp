

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=iso-8859-1" language="java"
         import="org.ecocean.servlet.ServletUtilities,org.ecocean.*,java.awt.*,java.io.*, java.net.URL, java.net.URLConnection, java.util.ArrayList" %>
<%@ page import="org.ecocean.shepherd.core.Shepherd" %>
<%@ taglib uri="http://www.sunwesttek.com/di" prefix="di" %>





<%

String context="context0";
context=ServletUtilities.getContext(request);

  String num = request.getParameter("number");
//int number=(new Integer(num)).intValue();
  Shepherd myShepherd = new Shepherd(context);
  boolean proceed = true;
  String side = "Left";

//mahalanobis variables
 // int intThisClusterCentroidX = 0;
 // int intThisClusterCentroidY = 0;
 // double[] spots_MahaDistances = new double[0];
 // SuperSpot[] newSpots = new SuperSpot[0];
 
     //setup data dir
    String rootWebappPath = getServletContext().getRealPath("/");
    File webappsDir = new File(rootWebappPath).getParentFile();
    File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));
    //if(!shepherdDataDir.exists()){shepherdDataDir.mkdirs();}
    File encountersDir=new File(shepherdDataDir.getAbsolutePath()+"/encounters");
    //if(!encountersDir.exists()){encountersDir.mkdirs();}
    
    File encounterDir = new File(Encounter.dir(shepherdDataDir, num));

  if ((request.getParameter("rightSide") != null) && (request.getParameter("rightSide").equals("true"))) {
    side = "Right";
  }

%>

<html>
<head>

  <title>Spot Visualization for Encounter <%=num%></title>
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
  Visualization for Encounter:<br /> <a
    href="encounter.jsp?number=<%=num%>"><%=enc.getEncounterNumber()%>
  </a>
</strong></font></p>


<%
  if (enc.getIndividualID()==null) {
%>
<p><font size="-1">Belongs to: 
</font></p>
<%
} else {
%>
<p><font size="-1">Belongs to: <a
  href="../individuals.jsp?number=<%=enc.getIndividualID()%>"><%=enc.getIndividualID()%>
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
    fileloc = "extractRight" + num + ".jpg";
  } else {
    fileloc = "extract" + num + ".jpg";
  }
  InputStream encStream = null;
  boolean canDirectMap = true;
  Dimension imageDimensions = null;
  FileInputStream fip=new FileInputStream(new File(encounterDir.getAbsolutePath()+"/" + fileloc));
  try {
    //connEnc = encURL.openConnection();
    //System.out.println("Opened new encounter connection");
    //encStream = connEnc.getInputStream();
    imageDimensions = org.apache.sanselan.Sanselan.getImageSize(fip, ("extract" + num + ".jpg"));

  } 
  catch (IOException ioe) {
    System.out.println("I failed to get the image input stream while using the spotVisualizer");
    canDirectMap = false;
	%>
	<p>I could not connect to and find the spot image at: <%=(encounterDir.getAbsolutePath()+"/" + fileloc) %></p>

	<%
  }
  fip.close();
  fip=null;

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

  	String thumbLocation = "file-" + encountersDir.getAbsolutePath()+"/"+ Encounter.subdir(num) + "/" + side + "SideSpotsMapped.jpg";
	
	%>
	<di:img width="<%=encImageWidth%>"
        height="<%=encImageHeight%>"
        imgParams="rendering=speed,quality=low" border="0" expAfter="0"
        threading="limited" fillPaint="#000000" align="top" valign="left"
        output="<%=thumbLocation %>">
        <%
        //System.out.println(encountersDir.getAbsolutePath()+"/"+fileloc);
        String src_url=encounterDir.getAbsolutePath()+"/"+fileloc;
        //String src_ur_value=encounterDir.getAbsolutePath()+"/"+addText;
        %>
    
  	<di:image srcurl="<%=src_url%>"/>
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
    	e.printStackTrace();
    }

    
    
%>
</di:img>

<!-- Put the image URL in now -->
<img src="/<%=CommonConfiguration.getDataDirectoryName(context) %>/encounters/<%=(Encounter.subdir(num)+"/"+side+"SideSpotsMapped.jpg")%>" border="0" align="left" valign="left">




</td>
</tr>
</table>

<% }


}


} else {%>
<p>There is no encounter <%=request.getParameter("number")%> in the
  database. Please double-check the encounter number and try again.</p>

<form action="encounter.jsp" method="post" name="encounter"><strong>Go
  to encounter: </strong> 
  <input name="number" type="text" value="<%=request.getParameter("number")%>" size="20" /> 
            <input name="Go" type="submit" value="Submit" />
</form>


<p><font color="#990000"><a href="../individualSearchResults.jsp">View
  all marked individuals</a></font></p>

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


