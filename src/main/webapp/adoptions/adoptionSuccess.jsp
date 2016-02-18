<%@ page contentType="text/html; charset=utf-8" language="java" %>
<%@ page import="java.awt.*" %>
<%@ page import="java.io.File" %>
<%@ page import="java.util.Properties" %>
<%@ page import="org.ecocean.*" %>
<%@ page import="org.ecocean.servlet.ServletUtilities" %>
<%@ page import="java.text.MessageFormat" %>
<%@ taglib uri="http://www.sunwesttek.com/di" prefix="di" %>
<%
  String context = ServletUtilities.getContext(request);
  String langCode = ServletUtilities.getLanguageCode(request);
  Properties props = ShepherdProperties.getProperties("adoption.properties", langCode, context);
  Properties propsAct = ShepherdProperties.getProperties("actionResults.properties", langCode, context);
  String number = request.getParameter("id");
  Shepherd myShepherd = new Shepherd(context);


  //setup data dir
    String rootWebappPath = getServletContext().getRealPath("/");
    File webappsDir = new File(rootWebappPath).getParentFile();
    File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));
     File adoptionsDir=new File(shepherdDataDir.getAbsolutePath()+"/adoptions");
  if(!adoptionsDir.exists()){adoptionsDir.mkdirs();}
  
  
  File thisAdoptionDir = new File(adoptionsDir.getAbsolutePath()+"/" + request.getParameter("number"));
  if(!thisAdoptionDir.exists()){thisAdoptionDir.mkdirs();}
  
%>

    <jsp:include page="../header.jsp" flush="true" />
    
        <div class="container maincontent">
          <%

            //get all needed DB reads out of the way in case Dynamic Image fails
            String addText = "adopter.jpg";
            boolean hasImages = true;
            String shark = "";
            
            String thumbLocation = "file-" +adoptionsDir.getAbsolutePath()+"/"+ number + "/thumb.jpg";
        	addText =  adoptionsDir.getAbsolutePath()+"/" + number + "/" + addText;

        
            myShepherd.beginDBTransaction();
            try {
              Adoption ad = myShepherd.getAdoption(number);
              shark = ad.getMarkedIndividual();
              if (ad.getAdopterImage() != null) {
                //addText = ad.getAdopterImage();
              } else {
                hasImages = false;
              }

            } 
            catch (Exception e) {
              System.out.println("Error encountered in adoptionSuccess.jsp!");
              e.printStackTrace();
            }
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();

	if(!addText.equals("")){
		

            	File file2process = new File(addText);
            	if(file2process.exists()){
            		


            	int intWidth = 190;
            	int intHeight = 190;
            	int thumbnailHeight = 190;
            	int thumbnailWidth = 190;
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
            	} 
            	else {
              		thumbnailWidth = intWidth;
              		thumbnailHeight = intHeight;
           		 }


            	
          		%>
          		
      
          		
          <di:img width="<%=thumbnailWidth %>" height="<%=thumbnailHeight %>" border="0"
                  fillPaint="#D7E0ED" output="<%=thumbLocation%>" expAfter="0" threading="limited"
                  align="left" valign="left">
            <di:image width="<%=Integer.toString(intWidth) %>"
                      height="<%=Integer.toString(intHeight) %>" srcurl="<%=addText%>"/>
       
          </di:img>
		<%
		}
		}
		%>
          <h1 class="intro"><%=propsAct.getProperty("page.title")%></h1>

          <div id="actionResultMessage">
            <p>
              <span class="prefix"><%=propsAct.getProperty("action.title.messagePrefix.success")%></span>
              <span class="content"><%=propsAct.getProperty("adoption.create.message.success")%></span>
            </p>
          </div>
          <div id="actionResultComment">
            <p class="prefix"><strong><%=propsAct.getProperty("action.title.commentPrefix.success")%></strong></p>
            <div class="content"><%=MessageFormat.format(propsAct.getProperty("adoption.create.comment.success"), number)%></div>
          </div>

          <p><a href="http://<%=CommonConfiguration.getURLLocation(request)%>/adoptions/adoption.jsp?number=<%=number%>"><%=MessageFormat.format(propsAct.getProperty("adoption.create.link.success"), number)%></a></p>


        </div>
        <jsp:include page="../footer.jsp" flush="true"/>
  