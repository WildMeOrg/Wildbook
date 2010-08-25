<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java"
	import="java.awt.Dimension, java.util.Vector, java.io.FileReader, java.io.BufferedReader, java.util.Properties, java.util.Enumeration, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException,org.ecocean.*, org.apache.sanselan.*"%>
<%@ taglib uri="di" prefix="di"%>
<%
String number=request.getParameter("number");
int imageNum=1;
try{
	imageNum=(new Integer(request.getParameter("imageNum"))).intValue();
}
catch(Exception cce){}



	
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
<div id="page"><jsp:include page="header.jsp" flush="true">
	<jsp:param name="isResearcher"
		value="<%=request.isUserInRole("researcher")%>" />
	<jsp:param name="isManager"
		value="<%=request.isUserInRole("manager")%>" />
	<jsp:param name="isReviewer"
		value="<%=request.isUserInRole("reviewer")%>" />
	<jsp:param name="isAdmin" value="<%=request.isUserInRole("admin")%>" />
</jsp:include>
<div id="main">

<div id="maincol-wide">

<div id="maintext">
<%
		
		String addText="";
		if(request.getParameter("imageName")!=null){
			addText=request.getParameter("imageName");
			addText="encounters/"+request.getParameter("number")+"/"+addText;
			
		}
		else {
			Shepherd myShepherd=new Shepherd();
			myShepherd.beginDBTransaction();
			Encounter enc=myShepherd.getEncounter(number);
			addText=(String)enc.getAdditionalImageNames().get((imageNum-1));	
			if(myShepherd.isAcceptableVideoFile(addText)){addText="images/video_thumb.jpg";}
			else{addText="encounters/"+request.getParameter("number")+"/"+addText;}
			myShepherd.rollbackDBTransaction();
			myShepherd.closeDBTransaction();
		}
		
		int intWidth = 100;
		int intHeight = 75;
		int thumbnailHeight=75;
		int thumbnailWidth = 100;
		
		
		File file2process=new File(getServletContext().getRealPath(("/"+addText)));
		
		try{
			

			
			//ImageInfo iInfo=new ImageInfo();
			if((file2process.exists())&&(file2process.length()>0)) {
				//iInfo.setInput(new FileInputStream(file2process));
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
				
				
			}
		}
		catch(Exception e){
			e.printStackTrace();
			%>
			
			<p>Hit an error trying to use Sanselan to determine image size and scaling factors.</p>
		
			<%
		}
			

		
		
		String thumbLocation="file-encounters/"+number+"/thumb.jpg";

		//generate the thumbnail image
			%> 
		<di:img width="<%=thumbnailWidth %>" height="<%=thumbnailHeight %>" border="0" fillPaint="#ffffff" output="<%=thumbLocation%>" expAfter="0" threading="limited" align="left" valign="left">
			<di:image width="<%=Integer.toString(intWidth) %>" height="<%=Integer.toString(intHeight) %>" srcurl="<%=addText%>" />
		</di:img>
		
<h1 class="intro">Success</h1>
<p>I have successfully reset the thumbnail image for encounter
number <strong><%=number%></strong>!</p>
<p><a
	href="http://<%=CommonConfiguration.getURLLocation()%>/encounters/encounter.jsp?number=<%=number%>">View
encounter #<%=number%></a>.</p>


</div>
<!-- end maintext --></div>
<!-- end maincol --> <jsp:include page="footer.jsp" flush="true" /></div>
<!-- end page --></div>
<!--end wrapper -->
</body>
</html>