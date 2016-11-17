<%@ page contentType="text/html; charset=utf-8" language="java" import="org.ecocean.servlet.*, org.ecocean.*, java.util.Properties, java.util.Date, java.util.Enumeration, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException" %>

<%

String context="context0";
context=ServletUtilities.getContext(request);
  //handle some cache-related security
  response.setHeader("Cache-Control", "no-cache"); //Forces caches to obtain a new copy of the page from the origin server
  response.setHeader("Cache-Control", "no-store"); //Directs caches not to store the page under any circumstance
  response.setDateHeader("Expires", 0); //Causes the proxy cache to see the page as "stale"
  response.setHeader("Pragma", "no-cache"); //HTTP 1.0 backward compatibility

  Shepherd myShepherd = new Shepherd(context);
  myShepherd.setAction	("emailAlterAdoption.jsp");
  myShepherd.beginDBTransaction();

  String id = "";
  String adopterName = "";
  String adopterAddress = "";
  String adopterEmail = "";
  String adopterImage="";
  String adoptionStartDate = "";
  String adoptionEndDate = "";
  String adopterQuote = "";
  String adoptionManager = "";
  String sharkForm = "";
  String encounterForm = "";
  String notes = "";
  String adoptionType = "";

  // Simple flag to change landing page after form submission.

  session.setAttribute( "emailEdit", true );

  String sharkID = "";
  if (request.getParameter("number") != null) {
    sharkID = request.getParameter("number");
  }

  String adoptionID = "";
  if (request.getParameter("adoption") != null) {
    adoptionID = request.getParameter("adoption");
  }

  String stripeID = "";
  if (request.getParameter("stripeID") != null) {
    stripeID = request.getParameter("stripeID");
  }

  %>



<jsp:include page="../header.jsp" flush="true" />
<link rel="stylesheet" href="css/createadoption.css">

  <div class="container maincontent">
    <table border="0">
      <tr>
        <form id="adoption-form" style="display:none;" action="../AdoptionAction" method="post" enctype="multipart/form-data" name="adoption_submission" target="_self" dir="ltr" lang="en">
      		<div class="form-header">
      			<h2>Update Adoption Profile</h2>
      			<img src="../cust/mantamatcher/img/circle-divider.png"/>
            <br>
      		</div>
      		<div class="input-col-1">
      			<div class="input-group">
      			  <input id="sharkId" class=" input-m-width" name="shark" type="hidden" value="<%=sharkID%>" placeholder="">
      			</div>
          </div>
          <br>
      			<div class="input-group">
      				<span class="input-group-addon">Change Shark Nickname</span>
      				<input class="input-l-width" type="text" name="newNickName" id="newNickName"></input>
      			</div>
            <br>
      			<div class="input-group">
      			  <span class="input-group-addon">Change Adopter Name</span>
      			  <input class=" input-l-width" name="adopterName" type="text" value="<%=adopterName%>">
      			</div>
            <br>
      			<div class="input-group">
      			  <span class="input-group-addon">Change Adopter Email</span>
      			  <input class=" input-l-width" name="adopterEmail" type="text" value="<%=adopterEmail%>"><br/>
      			</div>
            <br>
      			<div class="input-group">
      			  <span class="input-group-addon">Change Address</span>
      			  <input class=" input-l-width" name="adopterAddress" type="text" value="<%=adopterAddress%>">
      			</div>
            <br>
            <div class="input-group">
              <span class="input-group-addon">Profile Photo</span>
              <%
              String adopterImageString="";
              if(adopterImage!=null){
                adopterImageString=adopterImage;
              }
              %>
              <input class="input-l-width" name="theFile1" type="file" size="30" value="<%=adopterImageString%>">&nbsp;&nbsp;
              <%
              if ((adopterImage != null) && (!adopterImageString.equals(""))) {
              %>
                <img src="/<%=CommonConfiguration.getDataDirectoryName(context) %>/adoptions/<%=id%>/thumb.jpg" align="absmiddle"/>&nbsp;
                <%
                  }
                %>
            </div>
	    <br>			
            </tr>
          </table>
        </td>
      </tr>

        <%-- Recaptcha widget --%>
        <%= ServletUtilities.captchaWidget(request) %>

		    <button class="large" type="submit" name="Submit" value="Submit">Update<span class="button-icon" aria-hidden="true"></span></button>
      </form>
    </table>
  </div>

  <%
    myShepherd.rollbackDBTransaction();
    myShepherd.closeDBTransaction();
  %>

<jsp:include page="../footer.jsp" flush="true"/>
