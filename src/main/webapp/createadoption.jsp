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

<div class="container maincontent">
  <section class="centered">
    <h2>Thank you for your support!</h2>
		<h3>Query Shark: <%= session.getAttribute("queryShark") %> Paid Status: <%= sessionPaid %> Has Nickname?: <%= hasNickName %> NickName: <%= nick %></h3>
    <h3>There are currently <%=countAdoptable%> sharks available for adoption.</h3>
    <p>
      Below, you will be able to enter financial information, choose your shark, and create your profile.
    </p>
  </section>

<%-- BEGIN STRIPE FORM --%>
<h3>Financial Information</h3>
<form action="StripePayment" method="POST" id="payment-form" lang="en">
  <span class="payment-errors"></span>
	<div class="input-group">
		<span class="input-group-addon">Adoption Type</span>
		<select id='planName' class="input-l-width" name="planName">
			<option  value="none" selected="selected">No Subscription</option>
			<option value="individual">Individual $5/Month</option>
			<option value="group">Group adoption - $20/Month</option>
			<option value="corporate">Corporate adoption - $120/Month</option>
	</select>
	</div>
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
</form>
<hr>
<%-- END STRIPE FORM - BEGIN ADOPTION FORM--%>


<h3>Adoption Profile</h3>
	<form id="adoption-form" style="display:none;" action="AdoptionAction" method="post" enctype="multipart/form-data" name="adoption_submission" target="_self" dir="ltr" lang="en">
	<div class="input-group">
	  <span class="input-group-addon">Shark ID</span>
	  <input id="sharkId" class=" input-m-width" name="shark" type="text" value="<%=sessionShark%>" placeholder="Browse the gallery and find the shark that suits you">  <%if (!shark.equals("")) { %>
	    <a href="individuals.jsp?number<%=shark%>">Link</a> <%
	      }
	    %>
	</div>
	<div class="input-group">
	  <span class="input-group-addon">Adoption Starts</span>
		<h4 class="adoptionStartHeader"></h4>
	</div>
	<input class="input-m-width adoptionStartDate" name="adoptionStartDate" type="hidden" value="<%=adoptionStartDate%>">
	<%-- Create logic to calculate how long shark would be adopted for with lump sum. --%>
	<%-- <div class="input-group">
	  <span class="input-group-addon">Adoption Ends</span>
	  <input class=" input-m-width" name="adoptionEndDate" type="text" value="<%=adoptionEndDate%>">
	</div> --%>
	<div class="input-group">
	  <span class="input-group-addon">Adopter Name</span>
	  <input class=" input-l-width" name="adopterName" type="text" value="<%=adopterName%>">
	</div>
	<div class="input-group">
	  <span class="input-group-addon">Adopter Email</span>
	  <input class=" input-l-width" name="adopterEmail" type="text" value="<%=adopterEmail%>"><br/>
	</div>
	<div class="input-group">
	  <span class="input-group-addon">Address</span>
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
	  <input class="disabled-input input-l-width" name="theFile1" type="file" size="30" value="<%=adopterImageString%>">&nbsp;&nbsp;
	  <%
	  if ((adopterImage != null) && (!adopterImageString.equals(""))) {
	  %>
	    <img src="/<%=CommonConfiguration.getDataDirectoryName(context) %>/adoptions/<%=id%>/thumb.jpg" align="absmiddle"/>&nbsp;
	    <%
	      }
	    %>
	</div>
	<div class="input-group">
	  <span class="input-group-addon">Quote</span>
	  <textarea class="" name="adopterQuote" id="adopterQuote" placeholder="Create a custom profile message (e.g. Why are research and conservation for this species important?)."><%=adopterQuote%>
	  </textarea>
	</div>

	<%
		if ((hasNickName == false )||(nick.equals(""))) {
	%>
	<h4>This shark doesn't have a nickname yet! Choose one you like.</h4>
	<div class="input-group">
		<span class="input-group-addon">Shark Nickname:</span>
		<input class="input-l-width" type="text" name="newNickName" id="newNickName" value=""	 ></input>
	</div>


	<%
		}
	%>

	<!-- No submit button unless payment is accepted. May switch to totally non visible form prior to payment. -->
	  <%
	    if (acceptedPayment) {
	  %>
	    <button class="large" type="submit" name="Submit" value="Submit"><span class="button-icon" aria-hidden="true">Finish Adoption</span></button>
	  <%
	    }
	  %>
	<%
	  if (acceptedPayment) {
	%>
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

	function formSwitcher() {
		if (<%= sessionPaid %> === true) {
			$("#payment-form").hide();
			$("#adoption-form").show();
		}
	}
	formSwitcher();

	jQuery(function($) {
		$('#payment-form').submit(function(e) {
		 var $form = $(this);

		 // Disable the submit button to prevent repeated clicks
		 /*$form.find('button').prop('disabled', true);*/
		 // Enable input fields for building profile
			/*$(".disabled-input").prop('disabled', false);*/

		 Stripe.card.createToken($form, stripeResponseHandler);

		 formSwitcher();
		 // Prevent the form from submitting with the default action
		 return false;
		});
	});
</script>

<!-- Auto populate start date with current date. -->
<script>
  var myDate, day, month, year, date;
  myDate = new Date();
  day = myDate.getDate();
  if (day <10)
    day = "0" + day;
  month = myDate.getMonth() + 1;
  if (month < 10)
    month = "0" + month;
  year = myDate.getFullYear();
  date = year + "-" + month + "-" + day;
  $(".adoptionStartDate").val(date);
	$(".adoptionStartHeader").text(date);
</script>
