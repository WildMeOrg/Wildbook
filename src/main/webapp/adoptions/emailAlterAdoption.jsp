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

  Properties stripeProps = ShepherdProperties.getProperties("stripeKeys.properties", "", context);
  if (stripeProps == null) {
       System.out.println("There are no available API keys for Stripe!");
  }
  String stripePublicKey = stripeProps.getProperty("publicKey");

  String id = "";
  String adopterName = "";
  String adopterAddress = "";
  String adopterEmail = "";
  String adopterImage="";
  String adopterQuote = "";
  String adoptionManager = "";
  String sharkForm = "";
  String notes = "";
  String adoptionType = "";

  // Simple flag to change landing page after form submission.

  session.setAttribute( "emailEdit", true );

  String number = (String)session.getAttribute("number");

  String sharkID = "";
  if (request.getParameter("number") != null) {
    sharkID = request.getParameter("number");
    session.setAttribute( "sharkID", request.getParameter("number"));
  }

  String adoptionID = "";
  if (request.getParameter("adoption") != null) {
    adoptionID = request.getParameter("adoption");
    session.setAttribute( "adoptionID", request.getParameter("adoption"));
  }

  String stripeID = "";
  if (request.getParameter("stripeID") != null) {
    stripeID = request.getParameter("stripeID");
    session.setAttribute( "stripeID", request.getParameter("stripeID"));
  }

  %>



<jsp:include page="../header.jsp" flush="true" />
<link rel="stylesheet" href="css/createadoption.css">

<div class="container maincontent">
  <form id="adoption-form" action="../AdoptionAction" method="post" enctype="multipart/form-data" name="adoption_submission" target="_self" dir="ltr" lang="en">
		<div class="form-header">
			<h2>Update Adoption Profile</h2>
			<img src="../cust/mantamatcher/img/circle-divider.png"/>
		</div>
		<div class="input-col-1">
      <input name="number" type="hidden" value="<%=adoptionID%>" placeholder="">
			<input id="sharkId" type="hidden" name="shark" value="<%=sharkID%>" placeholder="">
			<div class="input-group">
			  <span class="input-group-addon">Change Adopter Name</span>
			  <input class=" input-l-width" name="adopterName" type="text" value="<%=adopterName%>">
			</div>
			<div class="input-group">
			  <span class="input-group-addon">Change Adopter Email</span>
			  <input class=" input-l-width" name="adopterEmail" type="text" value="<%=adopterEmail%>"><br/>
			</div>
			<div class="input-group">
			  <span class="input-group-addon">Change Address</span>
			  <input class=" input-l-width" name="adopterAddress" type="text" value="<%=adopterAddress%>">
			</div>
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

      <%-- Recaptcha widget --%>
      <%= ServletUtilities.captchaWidget(request) %>

	    <button class="large" type="submit" name="Submit" value="Submit">Update Profile<span class="button-icon" aria-hidden="true"></span></button>
    </div>

  </form>

  <%-- END ADOPTION UPDATE FORM -- BEGIN PAYMENT UPDATE FORM --%>

  <form action="../StripeUpdate" method="POST" id="payment-form" lang="en">
		<div class="form-header">
	    <h2>Financial Information</h2>
			<img src="../cust/mantamatcher/img/circle-divider.png"/>
	  </div>
	  <span class="payment-errors"></span>
		<div class="input-col-1">
			<div class="input-group">
				<span class="input-group-addon">Name On Card</span>
				<input type="text" class="input-l-width" name="nameOnCard">
			</div>
			<div class="input-group">
			  <span class="input-group-addon">Card Number</span>
			  <input type="text" class="input-l-width" data-stripe="number">
			</div>
			<div class="input-group">
			  <span class="input-group-addon">Expiration</span>
			  <input type="text" class="input-s-width" data-stripe="exp_month" placeholder="MM">
			  <input type="text" class="input-s-width" data-stripe="exp_year" placeholder="YY">
			</div>
  		<div class="input-group">
  		  <span class="input-group-addon">CVC</span>
  		  <input type="text" class="input-s-width" data-stripe="cvc">
  		</div>
  		<div class="input-group">
  		  <span class="input-group-addon">Billing Zip</span>
  		  <input type="text" class="input-m-width" data-stripe="address_zip">
  		</div>
  	  <button type="submit" class="large submit" value="Update Payment">Update Payment<span class="button-icon" aria-hidden="true"></button>
    </div>
	</form>




</div>

  <%
    myShepherd.rollbackDBTransaction();
    myShepherd.closeDBTransaction();
  %>

<jsp:include page="../footer.jsp" flush="true"/>


<!-- Javascript Section -->
<script type="text/javascript" src="https://js.stripe.com/v2/"></script>
<script type="text/javascript">
	// Publishable Key
	Stripe.setPublishableKey('<%=stripePublicKey%>');

	var stripeResponseHandler = function(status, response) {
		var $form = $('#payment-form');

		if (response.error) {
  		// show errors
  		$form.find('.payment-errors').text(response.error.message);
  		$form.find('button').prop('disabled', false);
		} else {
  		// token contains id, last 4 card digits, and card type
  		var token = response.id;
  		//  submit to the server
  		$form.append($('<input type="hidden" name="stripeToken" />').val(token));
  		// and re-submit
		 $form.get(0).submit();
		}
	};

	jQuery(function($) {
		$('#payment-form').submit(function(e) {
		 var $form = $(this);
		 Stripe.card.createToken($form, stripeResponseHandler);
		 // Prevent the form from submitting with the default action
		 return false;
		});
	});
</script>
