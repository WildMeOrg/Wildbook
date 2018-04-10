<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.servlet.ServletUtilities,org.ecocean.Adoption, org.ecocean.MarkedIndividual, org.ecocean.CommonConfiguration,org.ecocean.Shepherd,java.awt.*, java.io.File" %>
<%@ taglib uri="http://www.sunwesttek.com/di" prefix="di" %>
<%
String context="context0";
context=ServletUtilities.getContext(request);
  String number = request.getParameter("id");
  Shepherd myShepherd = new Shepherd(context);
  myShepherd.setAction("adoptionSuccess.jsp");


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

    <jsp:include page="../header.jsp" flush="true" />

        <div class="container maincontent">
          <%

            //get all needed DB reads out of the way in case Dynamic Image fails
            String addText = "adopter.jpg";
            String markedIndividual = "";
            boolean hasImages = true;
            String shark = "";

            String thumbLocation = "file-" +adoptionsDir.getAbsolutePath()+"/"+ number + "/thumb.jpg";
        	addText =  adoptionsDir.getAbsolutePath()+"/" + number + "/" + addText;


            myShepherd.beginDBTransaction();
            String nickName = "";
            try {
              Adoption ad = myShepherd.getAdoption(number);
              shark = ad.getMarkedIndividual();
              if (ad.getAdopterImage() != null) {
                //addText = ad.getAdopterImage();
              } else {
                hasImages = false;
              }
              if (myShepherd.getMarkedIndividual(shark) != null) {
                MarkedIndividual mi = myShepherd.getMarkedIndividual(shark);
                nickName = mi.getNickName();
                markedIndividual = mi.getIndividualID();
              }
            }
            catch (Exception e) {
              System.out.println("Error encountered in adoptionSuccess.jsp!");
              e.printStackTrace();
            }
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();

	if(!addText.equals("")){

		System.out.println("xx thumLoc is: "+thumbLocation);
		System.out.println("xx addText is: "+addText);
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
          <h1 class="intro">Thank you for your support!</h1>
          <h3> You've adopted <%=nickName%> - <%=shark%></h3>
          <p>
            For future reference, your adoption is numbered <strong><%=number%>
            </strong>. If you have any questions, please reference this number when contacting us.
          </p>
          <p>
            You will be receiving an email detailing your adoption shortly. Please retain a copy of this email in the event that you wish to make any changes to your adoption. If you don't see an email, please check your junk folder.
          </p>
          <p><a href="//<%=CommonConfiguration.getURLLocation(request)%>/individuals.jsp?number=<%=markedIndividual%>">
            View your adopted shark's profile</a>.
          </p>
        </div>
        <jsp:include page="../footer.jsp" flush="true"/>
