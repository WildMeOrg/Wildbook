<%@ page contentType="text/html; charset=utf-8" language="java" import="org.ecocean.servlet.ServletUtilities, org.ecocean.*, java.util.Properties, java.util.Date, java.util.Enumeration, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException" %>
<%


//handle some cache-related security
response.setHeader("Cache-Control", "no-cache");
//Forces caches to obtain a new copy of the page from the origin server
response.setHeader("Cache-Control", "no-store");
//Directs caches not to store the page under any circumstance
response.setDateHeader("Expires", 0);
//Causes the proxy cache to see the page as "stale"
response.setHeader("Pragma", "no-cache");
//HTTP 1.0 backward compatibility

String context="context0";
context=ServletUtilities.getContext(request);

	//language setup
	String langCode="en";
	if(session.getAttribute("langCode")!=null){langCode=(String)session.getAttribute("langCode");}

	Properties props=new Properties();
	props.load(getClass().getResourceAsStream("/bundles/"+langCode+"/submit.properties"));

	Shepherd myShepherd = new Shepherd(context);
	myShepherd.setAction("adoptashark.jsp");
	myShepherd.beginDBTransaction();
	
	int count = myShepherd.getNumAdoptions();
	int allSharks = myShepherd.getNumMarkedIndividuals();
	int countAdoptable = allSharks - count;
	
	Adoption tempAD = null;

	boolean edit = false;
	boolean isOwner = true;
	boolean acceptedPayment = true;

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

	String servletURL = "../AdoptionAction";

	if (request.getParameter("number") != null) {
		tempAD = myShepherd.getAdoption(request.getParameter("number"));
		edit = true;
		//servletURL = "/editAdoption";
		id = tempAD.getID();
		adopterName = tempAD.getAdopterName();
		adopterAddress = tempAD.getAdopterAddress();
		adopterEmail = tempAD.getAdopterEmail();
		if((tempAD.getAdopterImage()!=null)&&(!tempAD.getAdopterImage().trim().equals(""))){
			adopterImage = tempAD.getAdopterImage();
		}
		adoptionStartDate = tempAD.getAdoptionStartDate();
		adoptionEndDate = tempAD.getAdoptionEndDate();
		adopterQuote = tempAD.getAdopterQuote();
		adoptionManager = tempAD.getAdoptionManager();
		sharkForm = tempAD.getMarkedIndividual();
		if (tempAD.getEncounter() != null) {
			encounterForm = tempAD.getEncounter();
		}
		notes = tempAD.getNotes();
		adoptionType = tempAD.getAdoptionType();
	}


%>
<jsp:include page="header.jsp" flush="true"/>

<div class="container maincontent">



		  <h2 class="intro">Adopt a Shark! There are currently <%=countAdoptable%> sharks that need to be adopted.</h2>


						<table><tr>
			<td valign="top"><p>You can support the ongoing research of the Wildbook for Whale Sharks photo-identification library by adopting a whale shark. A whale shark adoption allows you to:
			<ul>
			  <li>support cutting-edge whale shark research</li>
	    <li> receive email updates of resightings of your adopted shark</li>
		<li>display your photo and a quote from you on the shark's page in our library</li>
		</ul>
			<p>Funds raised by shark adoptions are used to offset the costs of maintaining this global library and to support new and existing research projects for the world's most mysterious fish.</p>
			<p>You can adopt a shark at the following levels:</p>
			<ul>
			<li> Children's adoption = USD $25/year</li>
			  <li> Individual adoption = USD $50/year</li>
	    <li>Group adoption = USD $200/year </li>
	          <li>Corporate adoption = USD $1000/year</li>
		</ul>
			<p>The cost of your adoption is tax deductible in the United States through Wild Me, a 501(c)(3) non-profit organization.</p>
			</td>
	 <td width="400" align="left">
		<p align="center"><a href="http://www.whaleshark.org/individuals.jsp?number=A-001"><img src="images/sample_adoption.gif" border="0" /></a>
		</p>
		<p align="center"><strong>
				  Sample whale shark adoption for whale shark A-001. <br />
	    </strong><strong><a href="http://www.whaleshark.org/individuals.jsp?number=A-003">Click here to see an example. </a> </strong></p>
	  </td></table>
			</p>

			<table><tr><td>
			<h3>Creating an adoption</h3>
			<p>To adopt a whale shark, follow these steps.</p>
			<p>1. Make the appropriate donation using the appropriate PayPal button below.</p>
<table cellpadding="5">

<tr>
	<td width="250px" valign="top"><em>Use the button below if you would like your Adoption funds directed to Wild Me. Wild Me offers tax deductability in the United States as a 501(c)(3) nonprofit organization.</em></td>

	<!--
	<td width="250px" valign="top"><em>Use the button on the right if you would like your Adoption funds directed to ECOCEAN (Australia).</em></td>
	-->

</tr>
<tr>
<!--  
<td>
<form action="https://www.paypal.com/cgi-bin/webscr" method="post">
<input type="hidden" name="cmd" value="_s-xclick">
<input type="hidden" name="hosted_button_id" value="5075222">
<input type="image" src="https://www.paypal.com/en_US/i/btn/btn_donateCC_LG.gif" border="0" name="submit" alt="PayPal - The safer, easier way to pay online!">
<img alt="" border="0" src="https://www.paypal.com/en_US/i/scr/pixel.gif" width="1" height="1">
</form>
</td>
<--
<!--
<td><form action="https://www.paypal.com/cgi-bin/webscr" method="post" target="_top">

<input type="hidden" name="cmd" value="_s-xclick">
<input type="hidden" name="hosted_button_id" value="47YS8D5TXGZBY">
<input type="image" src="https://www.paypalobjects.com/en_US/i/btn/btn_donateCC_LG.gif" border="0" name="submit" alt="PayPal - The safer, easier way to pay online!">
<img alt="" border="0" src="https://www.paypalobjects.com/en_AU/i/scr/pixel.gif" width="1" height="1">
</form>
 </td>
 -->
</tr>
</table>

<%-- BEGIN STRIPE FORM --%>
<br>
<h3>Stripe Form:</h3>
<form action="/your-charge-code" method="POST" id="payment-form">
  <span class="payment-errors"></span>

  <div class="form-row">
    <label>
      <span>Card Number</span>
      <input type="text" size="20" data-stripe="number">
    </label>
  </div>

  <div class="form-row">
    <label>
      <span>Expiration (MM/YY)</span>
      <input type="text" size="2" data-stripe="exp_month">
    </label>
    <span> / </span>
    <input type="text" size="2" data-stripe="exp_year">
  </div>

  <div class="form-row">
    <label>
      <span>CVC</span>
      <input type="text" size="4" data-stripe="cvc">
    </label>
  </div>

  <div class="form-row">
    <label>
      <span>Billing Zip</span>
      <input type="text" size="6" data-stripe="address_zip">
    </label>
  </div>

  <input type="submit" class="submit" value="Submit Payment">
</form>

<h3>End Stripe Form -- Begin Adoption Form</h3>
<%-- END STRIPE FORM - BEGIN ADOPTION FORM--%>
<br/>

<%
  String shark = "";
  if (request.getParameter("individual") != null) {
    shark = request.getParameter("individual");
  }
%>


<table class="adoption">
<tr>
<td>

<h3><a name="create" id="create"></a>Create adoption</h3>

<form action="<%=servletURL%>" method="post"
      enctype="multipart/form-data" name="adoption_submission"
      target="_self" dir="ltr" lang="en">

  <table>
    <tr>
      <td>Name:</td>
      <td><input name="adopterName" type="text" size="30"
                 value="<%=adopterName%>"></input></td>
    </tr>
    <tr valign="top">
      <td>Email:</td>
      <td><input name="adopterEmail" type="text" size="30"
                 value="<%=adopterEmail%>"></input><br/>

      </td>
    </tr>
    <tr>
      <td>Address:</td>
      <td><input name="adopterAddress" type="text" size="30"
                 value="<%=adopterAddress%>"></input></td>
    </tr>
    <tr>
      <td>Image:</td>
      <%
      String adopterImageString="";
      if(adopterImage!=null){
    	  adopterImageString=adopterImage;
    	}
      %>
      <td><input name="theFile1" type="file" size="30" value="<%=adopterImageString%>"></input>&nbsp;&nbsp;
      <%
      if ((adopterImage != null) && (!adopterImageString.equals(""))) {
      %>
        <img src="/<%=CommonConfiguration.getDataDirectoryName(context) %>/adoptions/<%=id%>/thumb.jpg" align="absmiddle"/>&nbsp;
        <%
          }
        %>
      <p>Image may be used on the adoption page. Check here if you do not wish to have the picture of you or the recipient viewable 
      from whaleshark.org. [x]</p>
      </td>
    </tr>


    <tr>
      <td valign="top">Adopter quote:</td>
      <td>Why are research and conservation for this species important?<br><textarea
        name="adopterQuote" cols="40" id="adopterQuote" rows="10"><%=adopterQuote%>
      </textarea>
      </td>
    </tr>


    <tr>
      <td>ID of Shark to Adopt:</td>
      <td><input name="shark" type="text" size="30"
                 value="<%=sharkForm%>"> </input> <%if (!sharkForm.equals("")) { %>
        <a href="../individuals.jsp?number=<%=sharkForm%>">Link</a> <%
          }
        %>
      </td>
    </tr>


    <tr>
      <td>Support Level:</td>
      <td><select name="adoptionType">
        <%

          if (adoptionType.equals("Individual adoption")) {
        %>
        <option value="Individual adoption" selected="selected">Individual
          adoption - $50
        </option>
        <%
        } else {
        %>
        <option value="Individual adoption">Individual adoption - $50</option>
        <%
          }


          if (adoptionType.equals("Group adoption")) {
        %>
        <option value="Group adoption" selected="selected">Group
          adoption - $200
        </option>
        <%
        } else {
        %>
        <option value="Group adoption">Group adoption - $200</option>
        <%
          }


          if (adoptionType.equals("Corporate adoption")) {
        %>
        <option value="Corporate adoption" selected="selected">Corporate
          adoption - $1000
        </option>
        <%
        } else {
        %>
        <option value="Corporate adoption">Corporate adoption - $1000</option>
        <%
          }
        %>


      </select></td>
    </tr>


    <tr>
      <td>Adoption start date:</td>
      <td><input id="adoptionStartDate" name="adoptionStartDate"
                 type="text" size="30" value="<%=adoptionStartDate%>"> <em>(e.g.
        2009-05-15) </input> </em></td>
    </tr>

    <tr>
      <td>Adoption end date:</td>
      <td><input name="adoptionEndDate" type="text" size="30"
                 value="<%=adoptionEndDate%>"> </input> <em>(e.g. 2010-05-15) </em></td>
    </tr>

    <!--
			 			 <tr>
			 <td>Adoption end date:</td>
			 <td><div id="calendar2"></div>
   				 <div id="date2">
				  <input  class="dateField" id="adoptionEndDate" name="adoptionEndDate" type="text" size="30" value="<%=adoptionEndDate%>"></input>
			</div>
				</td>
			</tr>
			 -->
    <tr>
      <td align="left" valign="top">Adoption notes:</td>
      <td><textarea name="notes" cols="40" id="notes" rows="10"><%=notes%>
      </textarea>

        <%
          if (request.getParameter("number") != null) {
        %> <br/>
        <input type="hidden" name="number" value="<%=id%>"/> <%
          }

        %>
      </td>
    </tr>

    <%
      if (acceptedPayment) {
    %>

    <tr>
      <td><input type="submit" name="Submit" value="Submit"/></td>
    </tr>

    <%
      }
    %>
  </table>
  <br/>

  <%
    if (acceptedPayment) {
  %>
</form>
<%
  }
%>
</td>
</tr>
</table>
<br/>

<%
  myShepherd.rollbackDBTransaction();
  myShepherd.closeDBTransaction();
%>


	<p><em><strong>Thank you for adopting a shark and supporting our global research efforts! </strong></em></p>
	</td>
	</tr></table>
</div>
<jsp:include page="footer.jsp" flush="true" />