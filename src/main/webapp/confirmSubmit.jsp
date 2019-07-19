<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.*, org.ecocean.servlet.ServletUtilities, 
         java.io.File, java.util.*, javax.servlet.http.HttpSession, org.ecocean.ia.Task" %>

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
  //String photographer = "None";
  //boolean emailPhoto = false;
  //get all needed DB reads out of the way in case Dynamic Image fails
  String addText = "";
  //boolean hasImages = true;
  //String submitter = "";
  //String informOthers = "";
  //String informMe = "";

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

        
      } 
      //else {
      //  hasImages = false;
      //}
      new_message.append("Location: " + enc.getLocation() + "<br>");
      new_message.append("Date: " + enc.getDate() + "<br>");
      if(enc.getSex()!=null){
      	new_message.append("Sex: " + enc.getSex() + "<br>");
      }
      //new_message.append("Submitter: " + enc.getSubmitterName() + "<br>");
      //new_message.append("Email: " + enc.getSubmitterEmail() + "<br>");
      //new_message.append("Photographer: " + enc.getPhotographerName() + "<br>");
      //new_message.append("Email: " + enc.getPhotographerEmail() + "<br>");
      new_message.append("Comments: " + enc.getComments() + "<br>");
      new_message.append("</body></html>");
      //submitter = enc.getSubmitterEmail();
     /*
      if ((enc.getPhotographerEmail() != null) && (!enc.getPhotographerEmail().equals("None")) && (!enc.getPhotographerEmail().equals(""))) {
        photographer = enc.getPhotographerEmail();
        emailPhoto = true;
      }
	*/
     


    } catch (Exception e) {
      System.out.println("Error encountered in confirmSubmit.jsp:");
      e.printStackTrace();
    }
    myShepherd.rollbackDBTransaction();
    //myShepherd.closeDBTransaction();
    
  }

  String taskId = request.getParameter("taskId").trim();
%>

<h1 class="intro"><%=props.getProperty("success") %></h1>

<p><strong><%=props.getProperty("thankYou") %></strong></p>

<p><strong><%=props.getProperty("confirmFilesOK") %>:</strong> <%=filesOKMessage %></p>
<p><strong><%=props.getProperty("confirmFilesBad") %>:</strong> <%=filesBadMessage %></p>

<p><%=props.getProperty("futureReference") %> <strong><%=number%></strong>.</p>

<%=props.getProperty("questions") %> <a href="mailto:<%=CommonConfiguration.getAutoEmailAddress(context) %>"><%=CommonConfiguration.getAutoEmailAddress(context) %></a></p>

<%
if (taskId!=null&&!"".equals(taskId)) {
%>

<p>
  <b><a href="//<%=CommonConfiguration.getURLLocation(request)%>/iaResults.jsp?taskId=<%=taskId%>"><%=props.getProperty("viewIAResults") %></b></a>.
</p>
<br>
<%
}
%>

<p>
	<a href="//<%=CommonConfiguration.getURLLocation(request)%>/encounters/encounter.jsp?number=<%=number%>"><%=props.getProperty("viewEncounter") %> <%=number%></a>.
</p>
<%


myShepherd.closeDBTransaction();
myShepherd=null;

%>
</div>

<jsp:include page="footer.jsp" flush="true"/>

