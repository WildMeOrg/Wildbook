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
         import="org.ecocean.servlet.ServletUtilities,org.ecocean.Adoption, org.ecocean.CommonConfiguration,org.ecocean.Shepherd,java.awt.*, java.io.File" %>
<%@ taglib uri="http://www.sunwesttek.com/di" prefix="di" %>
<%
String context="context0";
context=ServletUtilities.getContext(request);
  String number = request.getParameter("id");
  Shepherd myShepherd = new Shepherd(context);


  //String langCode = "en";
  String langCode=ServletUtilities.getLanguageCode(request);
  

  //setup data dir
    String rootWebappPath = getServletContext().getRealPath("/");
    File webappsDir = new File(rootWebappPath).getParentFile();
    File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));
     File adoptionsDir=new File(shepherdDataDir.getAbsolutePath()+"/adoptions");
  if(!adoptionsDir.exists()){adoptionsDir.mkdirs();}
  
  
  File thisAdoptionDir = new File(adoptionsDir.getAbsolutePath()+"/" + request.getParameter("number"));
  if(!thisAdoptionDir.exists()){thisAdoptionDir.mkdirs();}
  
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
    <jsp:include page="../header.jsp" flush="true">
      <jsp:param name="isAdmin" value="<%=request.isUserInRole(\"admin\")%>" />
    </jsp:include>
    <div id="main">

      <div id="maincol-wide-solo">

        <div id="maintext">
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
          <h1 class="intro">Success</h1>

          <p><strong>The adoption was successfully added/edited. </strong></p>

          <p>For future reference, this adoption is numbered <strong><%=number%>
          </strong>.</p>

          <p>If you have any questions, please reference this number when contacting
            us.</p>

          <p><a
            href="http://<%=CommonConfiguration.getURLLocation(request)%>/adoptions/adoption.jsp?number=<%=number%>">View
            adoption #<%=number%>
          </a>.</p>


        </div>
        <!-- end maintext --></div>
      <!-- end maincol -->
        <jsp:include page="../footer.jsp" flush="true"/>
    </div>
    <!-- end page --></div>
  <!--end wrapper -->
</body>
</html>