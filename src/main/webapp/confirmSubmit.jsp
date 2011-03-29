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
         import="org.ecocean.*, org.ecocean.servlet.ServletUtilities, java.awt.*,java.io.File,java.util.Properties, java.util.StringTokenizer, java.util.Vector, java.util.concurrent.ThreadPoolExecutor" %>
<%@ taglib uri="http://www.sunwesttek.com/di" prefix="di" %>

<%
  String number = request.getParameter("number");
  Shepherd myShepherd = new Shepherd();

//setup our Properties object to hold all properties
  Properties props = new Properties();
  String langCode = "en";
  Properties email_props = new Properties();

  //check what language is requested
  if (session.getAttribute("langCode") != null) {
    langCode = (String) session.getAttribute("langCode");
  }

  //set up the file input stream
  props.load(getClass().getResourceAsStream("/bundles/" + langCode + "/submit.properties"));


  email_props.load(getClass().getResourceAsStream("/bundles/" + langCode + "/confirmSubmitEmails.properties"));


  //link path to submit page with appropriate language
  String submitPath = "submit.jsp";


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
<%
  StringBuffer new_message = new StringBuffer();
  new_message.append("The library has received a new whale shark encounter submission. You can " +
    "view it at:\nhttp://" + CommonConfiguration.getURLLocation(request) +
    "/encounters/encounter" +
    ".jsp?number="+ number);
  new_message.append("\n\nQuick stats:\n");
  String photographer = "None";
  boolean emailPhoto = false;
  //get all needed DB reads out of the way in case Dynamic Image fails
  String addText = "";
  boolean hasImages = true;
  String submitter = "";
  String informOthers = "";
  String informMe = "";
  if (!number.equals("fail")) {

    myShepherd.beginDBTransaction();
    try {
      Encounter enc = myShepherd.getEncounter(number);
      if ((enc.getAdditionalImageNames() != null) && (enc.getAdditionalImageNames().size() > 0)) {
        addText = (String) enc.getAdditionalImageNames().get(0);
      }
      if ((enc.getLocationCode() != null) && (!enc.getLocationCode().equals("None"))) {
        informMe = email_props.getProperty(enc.getLocationCode());
      } else {
        hasImages = false;
      }
      new_message.append("Location: " + enc.getLocation() + "\n");
      new_message.append("Date: " + enc.getDate() + "\n");
      new_message.append("Size: " + enc.getSize() + " " + enc.getMeasureUnits() + "\n");
      new_message.append("Sex: " + enc.getSex() + "\n");
      new_message.append("Submitter: " + enc.getSubmitterName() + "\n");
      new_message.append("Email: " + enc.getSubmitterEmail() + "\n");
      new_message.append("Photographer: " + enc.getPhotographerName() + "\n");
      new_message.append("Email: " + enc.getPhotographerEmail() + "\n");
      new_message.append("Comments: " + enc.getComments() + "\n");
      submitter = enc.getSubmitterEmail();
      if ((enc.getPhotographerEmail() != null) && (!enc.getPhotographerEmail().equals("None")) && (!enc.getPhotographerEmail().equals(""))) {
        photographer = enc.getPhotographerEmail();
        emailPhoto = true;
      }

      if ((enc.getInformOthers() != null) && (!enc.getInformOthers().equals(""))) {
        informOthers = enc.getInformOthers();
      }

    } catch (Exception e) {
      System.out.println("Error encountered in confirmSubmit.jsp:");
      e.printStackTrace();
    }
    myShepherd.rollbackDBTransaction();
    myShepherd.closeDBTransaction();
  }

  String thumbLocation = "file-encounters/" + number + "/thumb.jpg";
  if (myShepherd.isAcceptableVideoFile(addText)) {
    addText = "images/video_thumb.jpg";
  } else {
    addText = "encounters/" + number + "/" + addText;
  }


  File file2process = new File(getServletContext().getRealPath(("/" + addText)));

  int intWidth = 100;
  int intHeight = 75;
  int thumbnailHeight = 75;
  int thumbnailWidth = 100;


  String height = "";
  String width = "";

  Dimension imageDimensions = org.apache.sanselan.Sanselan.getImageSize(file2process);

  width = Double.toString(imageDimensions.getWidth());
  height = Double.toString(imageDimensions.getHeight());

  intHeight = ((new Double(height)).intValue());
  intWidth = ((new Double(width)).intValue());

  if (intWidth > thumbnailWidth) {
    double scalingFactor = intWidth / thumbnailWidth;
    intWidth = (int) (intWidth / scalingFactor);
    intHeight = (int) (intHeight / scalingFactor);
    if (intHeight < thumbnailHeight) {
      thumbnailHeight = intHeight;
    }
  } else {
    thumbnailWidth = intWidth;
    thumbnailHeight = intHeight;
  }


%>


<di:img width="<%=thumbnailWidth %>" height="<%=thumbnailHeight %>" border="0" fillPaint="#ffffff"
        output="<%=thumbLocation%>" expAfter="0" threading="limited" align="left" valign="left">
  <di:image width="<%=Integer.toString(intWidth) %>" height="<%=Integer.toString(intHeight) %>"
            srcurl="<%=addText%>"/>
</di:img>

<h1 class="intro">Success</h1>

<p><strong>Thank you for submitting your encounter! </strong></p>

<p>For future reference, this encounter has been assigned the number
  <strong><%=number%>
  </strong>.</p>

<p>If you have any questions, please reference this number when <a
  href="mailto:<%=CommonConfiguration.getAutoEmailAddress() %>">contacting
  us.</a></p>

<p><a
  href="http://<%=CommonConfiguration.getURLLocation(request)%>/encounters/encounter.jsp?number=<%=number%>&langCode=<%=langCode%>">View
  encounter #<%=number%>
</a>. <em>This may initially take a minute or more to fully load as we dynamically copy-protect your
  new image(s).</em></p>
<%

  Vector e_images = new Vector();

  //get the email thread handler
  ThreadPoolExecutor es = MailThreadExecutorService.getExecutorService();


  //email the new submission address defined in commonConfiguration.properties
  es.execute(new NotificationMailer(CommonConfiguration.getMailHost(), CommonConfiguration.getAutoEmailAddress(), CommonConfiguration.getNewSubmissionEmail(), ("New encounter submission: " + number), new_message.toString(), e_images));

  //now email those assigned this location code
  if (informMe != null) {

    if (informMe.indexOf(",") != -1) {

      StringTokenizer str = new StringTokenizer(informMe, ",");
      while (str.hasMoreTokens()) {
        String token = str.nextToken().trim();
        if (!token.equals("")) {
          es.execute(new NotificationMailer(CommonConfiguration.getMailHost(), CommonConfiguration.getAutoEmailAddress(), token, ("New encounter submission: " + number), new_message.toString(), e_images));

        }
      }

    } else {
      es.execute(new NotificationMailer(CommonConfiguration.getMailHost(), CommonConfiguration.getAutoEmailAddress(), informMe, ("New encounter submission: " + number), new_message.toString(), e_images));
    }
  }

  //thank the submitter and photographer
  String thanksmessage = ServletUtilities.getText("thankyou.txt");

  //add the encounter link
  thanksmessage += "\nEncounter :" + number + "\nhttp://" + CommonConfiguration.getURLLocation
    (request) + "/encounters/encounter.jsp?number=" + number;

  //add the removal message

  if (submitter.indexOf(",") != -1) {

    StringTokenizer str = new StringTokenizer(submitter, ",");
    while (str.hasMoreTokens()) {
      String token = str.nextToken().trim();
      if (!token.equals("")) {
        String personalizedThanksMessage = CommonConfiguration.appendEmailRemoveHashString
          (request, thanksmessage, token);
        //System.out.println(personalizedThanksMessage);

        es.execute(new NotificationMailer(CommonConfiguration.getMailHost(), CommonConfiguration.getAutoEmailAddress(), token, ("New encounter submission: " + number), personalizedThanksMessage, e_images));

      }
    }

  } else {
    String personalizedThanksMessage = CommonConfiguration.appendEmailRemoveHashString
      (request, thanksmessage, submitter);

    //System.out.println(personalizedThanksMessage);

    es.execute(new NotificationMailer(CommonConfiguration.getMailHost(), CommonConfiguration.getAutoEmailAddress(), submitter, ("New encounter submission: " + number), personalizedThanksMessage, e_images));
  }


  if (emailPhoto) {
    if (photographer.indexOf(",") != -1) {
      StringTokenizer str = new StringTokenizer(photographer, ",");
      while (str.hasMoreTokens()) {
        String token = str.nextToken().trim();
        if (!token.equals("")) {
          String personalizedThanksMessage = CommonConfiguration.appendEmailRemoveHashString
            (request, thanksmessage, token);

          es.execute(new NotificationMailer(CommonConfiguration.getMailHost(), CommonConfiguration.getAutoEmailAddress(), token, ("New encounter submission: " + number), personalizedThanksMessage, e_images));
        }
      }
    } else {
      String personalizedThanksMessage = CommonConfiguration.appendEmailRemoveHashString
        (request, thanksmessage, photographer);

      es.execute(new NotificationMailer(CommonConfiguration.getMailHost(), CommonConfiguration.getAutoEmailAddress(), photographer, ("New encounter submission: " + number), personalizedThanksMessage, e_images));
    }
  }

  if (!informOthers.equals("")) {
    if (informOthers.indexOf(",") != -1) {
      StringTokenizer str = new StringTokenizer(informOthers, ",");
      while (str.hasMoreTokens()) {
        String token = str.nextToken().trim();
        if (!token.equals("")) {
          String personalizedThanksMessage = CommonConfiguration.appendEmailRemoveHashString
            (request, thanksmessage, token);

          es.execute(new NotificationMailer(CommonConfiguration.getMailHost(), CommonConfiguration.getAutoEmailAddress(), token, ("New encounter submission: " + number), personalizedThanksMessage, e_images));
        }
      }
    } else {
      String personalizedThanksMessage = CommonConfiguration.appendEmailRemoveHashString
        (request, thanksmessage, informOthers);

      es.execute(new NotificationMailer(CommonConfiguration.getMailHost(), CommonConfiguration.getAutoEmailAddress(), informOthers, ("New encounter submission: " + number), personalizedThanksMessage, e_images));
    }
  }


%>
</div>
<!-- end maintext --></div>
<!-- end maincol -->
<jsp:include page="footer.jsp" flush="true"/>
</div>
<!-- end page --></div>
<!--end wrapper -->
</body>
</html>