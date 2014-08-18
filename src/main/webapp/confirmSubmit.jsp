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
         import="org.ecocean.*, org.ecocean.servlet.ServletUtilities, java.awt.*,java.io.File,java.util.Properties, java.util.StringTokenizer, java.util.Vector, java.util.concurrent.ThreadPoolExecutor, javax.servlet.http.HttpSession" %>
<%@ taglib uri="http://www.sunwesttek.com/di" prefix="di" %>

<%
String context="context0";
context=ServletUtilities.getContext(request);
  String number = request.getParameter("number").trim();
  Shepherd myShepherd = new Shepherd(context);
	//HttpSession session = request.getSession(false);


	String filesOKMessage = "";
	if (session.getAttribute("filesOKMessage") != null) { filesOKMessage = session.getAttribute("filesOKMessage").toString(); }
	String filesBadMessage = "";
	if (session.getAttribute("filesBadMessage") != null) { filesBadMessage = session.getAttribute("filesBadMessage").toString(); }

//setup our Properties object to hold all properties
  Properties props = new Properties();
  //String langCode = "en";
  String langCode=ServletUtilities.getLanguageCode(request);
  

  //set up the file input stream
  //props.load(getClass().getResourceAsStream("/bundles/" + langCode + "/submit.properties"));
  props = ShepherdProperties.getProperties("submit.properties", langCode,context);



  //email_props.load(getClass().getResourceAsStream("/bundles/confirmSubmitEmails.properties"));


  //link path to submit page with appropriate language
  String submitPath = "submit.jsp";
  
  //let's set up references to our file system components
  String rootWebappPath = getServletContext().getRealPath("/");
  File webappsDir = new File(rootWebappPath).getParentFile();
  File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));
  if(!shepherdDataDir.exists()){shepherdDataDir.mkdirs();}
  File encountersDir=new File(shepherdDataDir.getAbsolutePath()+"/encounters");
  if(!encountersDir.exists()){encountersDir.mkdirs();}
  File thisEncounterDir = null;// = new File();  //gets set after we have encounter


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
  new_message.append("The "+CommonConfiguration.getProperty("htmlTitle",context)+" library has received a new encounter submission. You can " +
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

	String rootDir = getServletContext().getRealPath("/");
	String baseDir = ServletUtilities.dataDir(context, rootDir);

  if (!number.equals("fail")) {

    myShepherd.beginDBTransaction();
    try {
      Encounter enc = myShepherd.getEncounter(number);
      
      
			thisEncounterDir = new File(enc.dir(baseDir));
			String thisEncDirString=Encounter.dir(shepherdDataDir,enc.getCatalogNumber());
			thisEncounterDir=new File(thisEncDirString);
			if(!thisEncounterDir.exists()){thisEncounterDir.mkdirs();System.out.println("I am making the encDir: "+thisEncDirString);}
			
			
			
      if ((enc.getAdditionalImageNames() != null) && (enc.getAdditionalImageNames().size() > 0)) {
        addText = (String)enc.getAdditionalImageNames().get(0);
      }
      if ((enc.getLocationCode() != null) && (!enc.getLocationCode().equals("None"))) {
        
    	  
    	  //the old way was to load a list of email addresses from a properties files using the locationID as the property key
    	  //informMe = email_props.getProperty(enc.getLocationCode());
        
        //the new way loads email addresses based on User object roles matching location ID
        informMe=myShepherd.getAllUserEmailAddressesForLocationID(enc.getLocationID(),context);
        
        
      } else {
        hasImages = false;
      }
      new_message.append("Location: " + enc.getLocation() + "\n");
      new_message.append("Date: " + enc.getDate() + "\n");
      if(enc.getSex()!=null){
      	new_message.append("Sex: " + enc.getSex() + "\n");
      }
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

  String thumbLocation = "file-"+thisEncounterDir.getAbsolutePath() + "/thumb.jpg";
  if (myShepherd.isAcceptableVideoFile(addText)) {
    addText = rootWebappPath+"/images/video_thumb.jpg";
  } 
  else if(myShepherd.isAcceptableImageFile(addText)){
    addText = thisEncounterDir.getAbsolutePath() + "/" + addText;
  }
  else if(addText.equals("")){
	  addText = rootWebappPath+"/images/no_images.jpg";
  }


  //File file2process = new File(getServletContext().getRealPath(("/" + addText)));

  File file2process = new File(addText);
	File thumbFile = new File(thumbLocation.substring(5));


  if(file2process.exists() && myShepherd.isAcceptableImageFile(file2process.getName())){
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

<%
}
%>

<h1 class="intro"><%=props.getProperty("success") %></h1>

<p><strong><%=props.getProperty("thankYou") %></strong></p>

<p><strong><%=props.getProperty("confirmFilesOK") %>:</strong> <%=filesOKMessage %></p>
<p><strong><%=props.getProperty("confirmFilesBad") %>:</strong> <%=filesBadMessage %></p>

<p><%=props.getProperty("futureReference") %> <strong><%=number%></strong>.</p>

<%=props.getProperty("questions") %> <a href="mailto:<%=CommonConfiguration.getAutoEmailAddress(context) %>"><%=CommonConfiguration.getAutoEmailAddress(context) %></a></p>

<p>
	<a href="http://<%=CommonConfiguration.getURLLocation(request)%>/encounters/encounter.jsp?number=<%=number%>"><%=props.getProperty("viewEncounter") %> <%=number%></a>.
</p>
<%


if(CommonConfiguration.sendEmailNotifications(context)){

  Vector e_images = new Vector();




  //get the email thread handler
  ThreadPoolExecutor es = MailThreadExecutorService.getExecutorService();


  //email the new submission address defined in commonConfiguration.properties
  es.execute(new NotificationMailer(CommonConfiguration.getMailHost(context), CommonConfiguration.getAutoEmailAddress(context), CommonConfiguration.getNewSubmissionEmail(context), ("New encounter submission: " + number), new_message.toString(), e_images,context));

  //now email those assigned this location code
  if (informMe != null) {

    if (informMe.indexOf(",") != -1) {

      StringTokenizer str = new StringTokenizer(informMe, ",");
      while (str.hasMoreTokens()) {
        String token = str.nextToken().trim();
        if (!token.equals("")) {
          es.execute(new NotificationMailer(CommonConfiguration.getMailHost(context), CommonConfiguration.getAutoEmailAddress(context), token, ("New encounter submission: " + number), new_message.toString(), e_images,context));

        }
      }

    } else {
      es.execute(new NotificationMailer(CommonConfiguration.getMailHost(context), CommonConfiguration.getAutoEmailAddress(context), informMe, ("New encounter submission: " + number), new_message.toString(), e_images,context));
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
        String personalizedThanksMessage = CommonConfiguration.appendEmailRemoveHashString(request, thanksmessage, token,context);
        //System.out.println(personalizedThanksMessage);

        es.execute(new NotificationMailer(CommonConfiguration.getMailHost(context), CommonConfiguration.getAutoEmailAddress(context), token, ("New encounter submission: " + number), personalizedThanksMessage, e_images,context));

      }
    }

  } 
  else {
    String personalizedThanksMessage = CommonConfiguration.appendEmailRemoveHashString
      (request, thanksmessage, submitter,context);

    //System.out.println(personalizedThanksMessage);

    es.execute(new NotificationMailer(CommonConfiguration.getMailHost(context), CommonConfiguration.getAutoEmailAddress(context), submitter, ("New encounter submission: " + number), personalizedThanksMessage, e_images,context));
  }


  if (emailPhoto) {
    if (photographer.indexOf(",") != -1) {
      StringTokenizer str = new StringTokenizer(photographer, ",");
      while (str.hasMoreTokens()) {
        String token = str.nextToken().trim();
        if (!token.equals("")) {
          String personalizedThanksMessage = CommonConfiguration.appendEmailRemoveHashString
            (request, thanksmessage, token,context);

          es.execute(new NotificationMailer(CommonConfiguration.getMailHost(context), CommonConfiguration.getAutoEmailAddress(context), token, ("New encounter submission: " + number), personalizedThanksMessage, e_images,context));
        }
      }
    } else {
      String personalizedThanksMessage = CommonConfiguration.appendEmailRemoveHashString(request, thanksmessage, photographer,context);

      es.execute(new NotificationMailer(CommonConfiguration.getMailHost(context), CommonConfiguration.getAutoEmailAddress(context), photographer, ("New encounter submission: " + number), personalizedThanksMessage, e_images,context));
    }
  }

  if (!informOthers.equals("")) {
    if (informOthers.indexOf(",") != -1) {
      StringTokenizer str = new StringTokenizer(informOthers, ",");
      while (str.hasMoreTokens()) {
        String token = str.nextToken().trim();
        if (!token.equals("")) {
          String personalizedThanksMessage = CommonConfiguration.appendEmailRemoveHashString(request, thanksmessage, token,context);

          es.execute(new NotificationMailer(CommonConfiguration.getMailHost(context), CommonConfiguration.getAutoEmailAddress(context), token, ("New encounter submission: " + number), personalizedThanksMessage, e_images,context));
        }
      }
    } else {
      String personalizedThanksMessage = CommonConfiguration.appendEmailRemoveHashString(request, thanksmessage, informOthers,context);

      es.execute(new NotificationMailer(CommonConfiguration.getMailHost(context), CommonConfiguration.getAutoEmailAddress(context), informOthers, ("New encounter submission: " + number), personalizedThanksMessage, e_images,context));
    }
  }
  es.shutdown();
}

myShepherd=null;

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
