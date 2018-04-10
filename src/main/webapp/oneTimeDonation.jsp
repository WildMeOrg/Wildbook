<%@ page contentType="text/html; charset=utf-8" language="java" import="org.ecocean.servlet.*, org.ecocean.*, java.util.Properties, java.util.Date, java.util.Enumeration, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException" %>
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

	Properties stripeProps = ShepherdProperties.getProperties("stripeKeys.properties", "", context);
	if (stripeProps == null) {
			 System.out.println("There are no available API keys for Stripe!");
	}
	String stripePublicKey = stripeProps.getProperty("publicKey");

	Shepherd myShepherd = new Shepherd(context);
	myShepherd.setAction	("oneTimeDonation.jsp");
	myShepherd.beginDBTransaction();

	String shark = "";
	if (request.getParameter("number") != null) {
		shark = request.getParameter("number");
	}

	// Necessary to persist your selected shark across multiple form submissions.
	// Payment status is also stored in session.
	if (request.getParameter("number") != null) {
		session.setAttribute( "queryShark", request.getParameter("number") );
	}

	Boolean sessionPaid = false;
	if (session.getAttribute( "paid") != null) {
		sessionPaid =(Boolean)session.getAttribute( "paid");
	}

	session.setAttribute( "emailEdit", false );

	boolean acceptedPayment = true;

  String id = "";


%>

<jsp:include page="header.jsp" flush="true"/>
<link rel="stylesheet" href="css/createadoption.css">
<div class="container maincontent">
  <section class="centered">
    <h2>Thank you for your support!</h2>
    <h4>This donation will go directly to Whaleshark.org.</h4>
  </section>

	<%-- BEGIN STRIPE FORM --%>
	<form action="StripePayment" method="POST" id="payment-form" lang="en">
		<div class="form-header">
	    <h2>Financial Information</h2>
			<img src="cust/mantamatcher/img/circle-divider.png"/>
	  </div>
	  <span class="payment-errors"></span>
		<div class="input-col-1">
			<div class="input-group">
				<input type="hidden" name="planName" value="none">
			</div>
			<div class="input-group">
				<span class="input-group-addon">Amount in Dollars</span>
				<input type="number" class="input-l-width" min="5" max="1000000" name="amount" placeholder="Minimum $5">
			</div>
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
		</div>
		<div class="input-col-2">
			<div class="input-group">
			  <span class="input-group-addon">CVC</span>
			  <input type="text" class="input-s-width" data-stripe="cvc">
			</div>
			<div class="input-group">
			  <span class="input-group-addon">Billing Zip</span>
			  <input type="text" class="input-m-width" data-stripe="address_zip">
			</div>
			<div class="input-group">
				<span class="input-group-addon">Billing Email</span>
				<input type="text" class="input-l-width" name="email">
			</div>
			<button type="submit" class="large submit" value="Submit Payment">Make Donation<span class="button-icon" aria-hidden="true"></button>
		</div>
	</form>
	<%-- END STRIPE FORM - BEGIN ADOPTION FORM--%>

			<%
			  if (acceptedPayment) {
			%>
		</div>
	</form>
	<%
	}
	%>
	<%
	  myShepherd.rollbackDBTransaction();
	  myShepherd.closeDBTransaction();
	%>
</div>

<jsp:include page="footer.jsp" flush="true" />

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
		 return false;
		});
	});
</script>
