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

	Shepherd myShepherd = new Shepherd(context);
	myShepherd.setAction	("createadoption.jsp");
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
	String sessionShark = null;
	if (session.getAttribute( "queryShark") != null) {
		sessionShark =(String)session.getAttribute( "queryShark");
	}
	Boolean sessionPaid = false;
	if (session.getAttribute( "paid") != null) {
		sessionPaid =(Boolean)session.getAttribute( "paid");
	}

	boolean hasNickName = true;
	String nick = "";
	try {
		if (sessionShark != null) {
			MarkedIndividual mi = myShepherd.getMarkedIndividual(sessionShark);
			nick = mi.getNickName();
			if ((nick.equals("Unassigned"))||(nick.equals(""))) {
				hasNickName = false;
			}
		}
	} catch (Exception e) {
		System.out.println("Error looking up nickname!!");
		e.printStackTrace();
	}


	int count = myShepherd.getNumAdoptions();
	int allSharks = myShepherd.getNumMarkedIndividuals();
	int countAdoptable = allSharks - count;

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

%>

<jsp:include page="header.jsp" flush="true"/>
<link rel="stylesheet" href="css/createadoption.css">
<div class="container maincontent">
  <section class="centered">
    <h1>Thank you for your support!</h1>
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
				<span class="input-group-addon">Custom Amount</span>
				<input type="number" class="input-l-width" min="5" max="1000000" name="amount" placeholder="Optional">
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
			<%-- Passes selected shark through servlet so we get to keep it after payment. --%>
			<input id="selectedShark" type="hidden" name="selectedShark" value="">
			<button type="submit" class="large submit" value="Submit Payment">Next<span class="button-icon" aria-hidden="true"></button>
		</div>
	</form>
	<%-- END STRIPE FORM --%>




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
	Stripe.setPublishableKey('pk_test_yiqozX1BvmUhmcFwoFioHcff');

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

		 // Disable the submit button to prevent repeated clicks
		 /*$form.find('button').prop('disabled', true);*/
		 // Enable input fields for building profile
			/*$(".disabled-input").prop('disabled', false);*/

		 Stripe.card.createToken($form, stripeResponseHandler);
		 // Prevent the form from submitting with the default action
		 return false;
		});
	});
</script>
