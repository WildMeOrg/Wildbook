<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java"
	import="java.awt.Dimension, org.apache.sanselan.*,java.util.concurrent.ThreadPoolExecutor,java.util.Vector, java.io.FileReader, java.io.BufferedReader, java.util.Properties, java.util.Enumeration, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException,org.ecocean.*"%>
<%@ taglib uri="di" prefix="di"%>
<%
String number=request.getParameter("id");
Shepherd myShepherd=new Shepherd();


String langCode="en";


%>

<html>
<head>
<title><%=CommonConfiguration.getHTMLTitle() %></title>
<meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
<meta name="Description"
	content="<%=CommonConfiguration.getHTMLDescription() %>" />
<meta name="Keywords"
	content="<%=CommonConfiguration.getHTMLKeywords() %>" />
<meta name="Author" content="<%=CommonConfiguration.getHTMLAuthor() %>" />
<link href="<%=CommonConfiguration.getCSSURLLocation() %>"
	rel="stylesheet" type="text/css" />
<link rel="shortcut icon"
	href="<%=CommonConfiguration.getHTMLShortcutIcon() %>" />

</head>

<body>
<div id="wrapper">
<div id="page"><jsp:include page="../header.jsp" flush="true">
	<jsp:param name="isResearcher"
		value="<%=request.isUserInRole("researcher")%>" />
	<jsp:param name="isManager"
		value="<%=request.isUserInRole("manager")%>" />
	<jsp:param name="isReviewer"
		value="<%=request.isUserInRole("reviewer")%>" />
	<jsp:param name="isAdmin" value="<%=request.isUserInRole("admin")%>" />
</jsp:include>
<div id="main">

<div id="maincol-wide-solo">

<div id="maintext">
<%

		//get all needed DB reads out of the way in case Dynamic Image fails
		String addText="";
		boolean hasImages=true;
		String shark="";
		

		
			myShepherd.beginDBTransaction();
			try{
				Adoption ad=myShepherd.getAdoption(number);
				shark=ad.getMarkedIndividual();
				if(ad.getAdopterImage()!=null) {
					addText=ad.getAdopterImage();
				}
				else{hasImages=false;}

			}
			catch(Exception e) {
				System.out.println("Error encountered in adoptionSuccess.jsp!");
				e.printStackTrace();
			}
			myShepherd.rollbackDBTransaction();	
			myShepherd.closeDBTransaction();


			File file2process=new File(getServletContext().getRealPath(("/"+CommonConfiguration.getAdoptionDirectory()+"/"+number+"/"+addText)));
			
			int intWidth = 190;
			int intHeight = 190;
			int thumbnailHeight=190;
			int thumbnailWidth = 190;
			
			
			String height="";
			String width="";
				
				
			Dimension imageDimensions = org.apache.sanselan.Sanselan.getImageSize(file2process);
				
			//height+=iInfo.getHeight();
			//width+=iInfo.getWidth();
			
			width = Double.toString(imageDimensions.getWidth());
			height = Double.toString(imageDimensions.getHeight());
			
			intHeight=((new Double(height)).intValue());
			intWidth=((new Double(width)).intValue());
		
			if(intWidth>thumbnailWidth){
				double scalingFactor = intWidth/thumbnailWidth;
				intWidth=(int)(intWidth/scalingFactor);
				intHeight=(int)(intHeight/scalingFactor);
				if(intHeight<thumbnailHeight){thumbnailHeight = intHeight;}
			}
			else{
				thumbnailWidth = intWidth;
				thumbnailHeight = intHeight;
			}
			
			
		String thumbLocation="file-"+number+"/thumb.jpg";
		addText="http://"+CommonConfiguration.getURLLocation()+"/"+CommonConfiguration.getAdoptionDirectory()+"/"+number+"/"+addText;

			%> 
	<di:img width="<%=thumbnailWidth %>" height="<%=thumbnailHeight %>" border="0" fillPaint="#D7E0ED" output="<%=thumbLocation%>" expAfter="0" threading="limited" align="left" valign="left">
		<di:image width="<%=Integer.toString(intWidth) %>" height="<%=Integer.toString(intHeight) %>" srcurl="<%=addText%>" />	
	</di:img>
	


<h1 class="intro">Success</h1>
<p><strong>The adoption was successfully added/edited. </strong></p>
<p>For future reference, this adoption is numbered <strong><%=number%></strong>.</p>
<p>If you have any questions, please reference this number when contacting
us.</p>

<p><a href="http://<%=CommonConfiguration.getURLLocation()%>/<%=CommonConfiguration.getAdoptionDirectory()%>/adoption.jsp?number=<%=number%>">View
adoption #<%=number%></a>.</p>


</div>
<!-- end maintext --></div>
<!-- end maincol --> <jsp:include page="../footer.jsp" flush="true" />
</div>
<!-- end page --></div>
<!--end wrapper -->
</body>
</html>