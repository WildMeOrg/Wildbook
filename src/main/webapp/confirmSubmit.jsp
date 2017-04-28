<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.*, org.ecocean.servlet.ServletUtilities, java.awt.Dimension, java.io.File, java.util.*, java.util.concurrent.ThreadPoolExecutor, javax.servlet.http.HttpSession" %>
<%@ taglib uri="http://www.sunwesttek.com/di" prefix="di" %>

<jsp:include page="header.jsp" flush="true"/>

<%
String context="context0";
context=ServletUtilities.getContext(request);
  String number = request.getParameter("number").trim();
  Shepherd myShepherd = new Shepherd(context);
  myShepherd.setAction("confirmSubmit.jsp");
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


<div class="container maincontent">
<%
  StringBuffer new_message = new StringBuffer();
new_message.append("<html><body>");

  new_message.append("The "+CommonConfiguration.getProperty("htmlTitle",context)+" library has received a new encounter submission. You can " +
    "view it at:<br>" + CommonConfiguration.getURLLocation(request) +
    "/encounters/encounter" +
    ".jsp?number="+ number);
  new_message.append("<br><br>Quick stats:<br>");
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

  Encounter enc = null;

  if (!number.equals("fail")) {

    myShepherd.beginDBTransaction();
    try {
      enc = myShepherd.getEncounter(number);
      
      
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
      new_message.append("Location: " + enc.getLocation() + "<br>");
      new_message.append("Date: " + enc.getDate() + "<br>");
      if(enc.getSex()!=null){
      	new_message.append("Sex: " + enc.getSex() + "<br>");
      }
      new_message.append("Submitter: " + enc.getSubmitterName() + "<br>");
      new_message.append("Email: " + enc.getSubmitterEmail() + "<br>");
      new_message.append("Photographer: " + enc.getPhotographerName() + "<br>");
      new_message.append("Email: " + enc.getPhotographerEmail() + "<br>");
      new_message.append("Comments: " + enc.getComments() + "<br>");
      new_message.append("</body></html>");
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
	<a href="//<%=CommonConfiguration.getURLLocation(request)%>/encounters/encounter.jsp?number=<%=number%>"><%=props.getProperty("viewEncounter") %> <%=number%></a>.
</p>
<%


if(CommonConfiguration.sendEmailNotifications(context)){

  // Retrieve background service for processing emails
  ThreadPoolExecutor es = MailThreadExecutorService.getExecutorService();

  // Email new submission address(es) defined in commonConfiguration.properties
  Map<String, String> tagMap = NotificationMailer.createBasicTagMap(request, enc);
  List<String> mailTo = NotificationMailer.splitEmails(CommonConfiguration.getNewSubmissionEmail(context));
  String mailSubj = "New encounter submission: " + number;
  for (String emailTo : mailTo) {
    NotificationMailer mailer = new NotificationMailer(context, langCode, emailTo, "newSubmission-summary", tagMap);
    mailer.setUrlScheme(request.getScheme());
    es.execute(mailer);
  }

  // Email those assigned this location code
  if (informMe != null) {
    List<String> cOther = NotificationMailer.splitEmails(informMe);
    for (String emailTo : cOther) {
    	NotificationMailer mailer = new NotificationMailer(context, null, emailTo, "newSubmission-summary", tagMap);
    	mailer.setUrlScheme(request.getScheme());
      	es.execute(mailer);
    }
  }

  // Add encounter dont-track tag for remaining notifications (still needs email-hash assigned).
  tagMap.put(NotificationMailer.EMAIL_NOTRACK, "number=" + enc.getCatalogNumber());

  // Email submitter and photographer
  if (submitter != null) {
    List<String> cOther = NotificationMailer.splitEmails(submitter);
    for (String emailTo : cOther) {
      String msg = CommonConfiguration.appendEmailRemoveHashString(request, "", emailTo, context);
      tagMap.put(NotificationMailer.EMAIL_HASH_TAG, Encounter.getHashOfEmailString(emailTo));
      NotificationMailer mailer=new NotificationMailer(context, null, emailTo, "newSubmission", tagMap);
      mailer.setUrlScheme(request.getScheme());
      es.execute(mailer);
    }
  }
  if (emailPhoto && photographer != null) {
    List<String> cOther = NotificationMailer.splitEmails(photographer);
    for (String emailTo : cOther) {
      String msg = CommonConfiguration.appendEmailRemoveHashString(request, "", emailTo, context);
      tagMap.put(NotificationMailer.EMAIL_HASH_TAG, Encounter.getHashOfEmailString(emailTo));
      NotificationMailer mailer=new NotificationMailer(context, null, emailTo, "newSubmission", tagMap);
      mailer.setUrlScheme(request.getScheme());
      es.execute(mailer);
    }
  }

  // Email interested others
  if (informOthers != null) {
    List<String> cOther = NotificationMailer.splitEmails(informOthers);
    for (String emailTo : cOther) {
      String msg = CommonConfiguration.appendEmailRemoveHashString(request, "", emailTo, context);
      tagMap.put(NotificationMailer.EMAIL_HASH_TAG, Encounter.getHashOfEmailString(emailTo));
      NotificationMailer mailer=new NotificationMailer(context, null, emailTo, "newSubmission", tagMap);
      mailer.setUrlScheme(request.getScheme());
      es.execute(mailer);
    }
  }
  es.shutdown();
}

myShepherd=null;

%>
</div>

<jsp:include page="footer.jsp" flush="true"/>

