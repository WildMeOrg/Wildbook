<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.servlet.ServletUtilities,org.ecocean.CommonConfiguration, org.ecocean.Encounter, org.ecocean.Shepherd, java.awt.*, java.io.File, org.ecocean.SinglePhotoVideo" %>
<%@ taglib uri="http://www.sunwesttek.com/di" prefix="di" %>

<%
String context="context0";
context=ServletUtilities.getContext(request);
 
%>

    <jsp:include page="header.jsp" flush="true" />
      
        <div class="container maincontent">
          <%
          Shepherd myShepherd = new Shepherd(context);
					
          try {
        	  
        	  String number = request.getParameter("number").trim();
			  
			  Encounter enc = myShepherd.getEncounter(number);
			  
        	  int imageNum = 1;
        	  try {
        	    imageNum = (new Integer(request.getParameter("imageNum"))).intValue();
        	  } catch (Exception cce) {
        	  }
        	  
        	  //setup data dir
        	  String rootWebappPath = getServletContext().getRealPath("/");
        	  File webappsDir = new File(rootWebappPath).getParentFile();
        	  File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));
        	  File encountersDir=new File(shepherdDataDir.getAbsolutePath()+"/encounters");
        	  File thisEncounterDir = new File(encountersDir, Encounter.subdir(number));


        	  
            String addText = "";
            if (request.getParameter("imageName") != null) {
              addText = request.getParameter("imageName");
              addText = encountersDir.getAbsolutePath()+"/" + Encounter.subdir(request.getParameter("number")) + "/" + addText;

            } 
            else {
              
              myShepherd.beginDBTransaction();
              addText = (String) enc.getAdditionalImageNames().get((imageNum - 1));
              if (myShepherd.isAcceptableVideoFile(addText)) {
                addText = getServletContext().getRealPath("/")+"/images/video_thumb.jpg";
              } else {
                addText = encountersDir.getAbsolutePath()+"/"+ Encounter.subdir(request.getParameter("number")) + "/" + addText;
              }
              myShepherd.rollbackDBTransaction();
              //myShepherd.closeDBTransaction();
            }

            int intWidth = 100;
            int intHeight = 75;
            int thumbnailHeight = 75;
            int thumbnailWidth = 100;


            File file2process = new File(addText);
            String thumbPath = encountersDir.getAbsolutePath()+"/" + Encounter.subdir(number) + "/thumb.jpg";
            String thumbLocation = "file-" + thumbPath;

						//first try the new (optional) background method
						SinglePhotoVideo spv = null;
						if ((imageNum > 0) && (enc.getImages() != null)) spv = enc.getImages().get(imageNum - 1);
						if ((spv != null) && spv.scaleTo(context, thumbnailWidth, thumbnailHeight, thumbPath)) {
							System.out.println("attempting to create " + thumbPath + " in background.");


						///// if that fails (not configured, etc) then use existing method:

              //ImageInfo iInfo=new ImageInfo();
             } else if ((file2process.exists()) && (file2process.length() > 0)) {
                //iInfo.setInput(new FileInputStream(file2process));
                String height = "";
                String width = "";


                Dimension imageDimensions = org.apache.sanselan.Sanselan.getImageSize(file2process);

                //height+=iInfo.getHeight();
                //width+=iInfo.getWidth();

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


              }
            


            //generate the thumbnail image
          %>
          <di:img width="<%=thumbnailWidth %>" height="<%=thumbnailHeight %>" border="0"
                  fillPaint="#ffffff" output="<%=thumbLocation%>" expAfter="0" threading="limited"
                  align="left" valign="left">
            <di:image width="<%=Integer.toString(intWidth) %>"
                      height="<%=Integer.toString(intHeight) %>" srcurl="<%=addText%>"/>
          </di:img>

          <h1 class="intro">Success</h1>

          <p>I have successfully reset the thumbnail image for encounter number <strong><%=number%></strong>.</p>

          <p><a
            href="//<%=CommonConfiguration.getURLLocation(request)%>/encounters/encounter.jsp?number=<%=number%>">View encounter <%=number%>.</a></p>


       
        
        <%
        } catch (Exception e) {
              //e.printStackTrace();
          %>

          <p>Hit an error trying to generate the thumbnail. Either the specified encounter or image does not exist.</p>

	
          <%
            }
          if(!myShepherd.getPM().isClosed()){
        	  myShepherd.rollbackDBTransaction();
              myShepherd.closeDBTransaction();
          }
          
         myShepherd=null;
          %>
       </div>
     
      <jsp:include page="footer.jsp" flush="true"/>
  