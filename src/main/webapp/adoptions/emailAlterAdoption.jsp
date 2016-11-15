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
	myShepherd.setAction	("emailAlterAdoption.jsp");
	myShepherd.beginDBTransaction();

	String adoptionID = "";
	if (request.getParameter("adoption") != null) {
		adoptionID = request.getParameter("adoption");
	}

  String sharkID = "";
  if (request.getParameter("number") != null) {
    sharkID = request.getParameter("number");
  }

  String stripeID = "";
  if (request.getParameter("stripeID") != null) {
    stripeID = request.getParameter("stripeID");
  }


	// Necessary to persist your selected shark across multiple form submissions.
	// Payment status is also stored in session.
	// if (request.getParameter("number") != null) {
	// 	session.setAttribute( "queryShark", request.getParameter("number") );
	// }
	// String sessionShark = null;
	// if (session.getAttribute( "queryShark") != null) {
	// 	sessionShark =(String)session.getAttribute( "queryShark");
	// }
	// Boolean sessionPaid = false;
	// if (session.getAttribute( "paid") != null) {
	// 	sessionPaid =(Boolean)session.getAttribute( "paid");
	// }

	boolean hasNickName = true;
	String nick = "";
	try {
		if (sharkID != null) {
			MarkedIndividual mi = myShepherd.getMarkedIndividual(sharkID);
			nick = mi.getNickName();
			if ((nick.equals("Unassigned"))||(nick.equals(""))) {
				hasNickName = false;
			}
		}
	} catch (Exception e) {
		System.out.println("Error looking up shark nickname for email alteration!!");
		e.printStackTrace();
	}

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

%>

<jsp:include page="header.jsp" flush="true"/>
<link rel="stylesheet" href="css/createadoption.css">
<div class="container maincontent">
  <section class="centered">
    <h2>Alter Your Adoption Information</h2>

  </section>

	<%-- BEGIN STRIPE FORM --%>

  <%-- ADD FORM TO CHANGE PAYMENT CARD IF DESIRED --%>

	<%-- END STRIPE FORM - BEGIN ADOPTION FORM--%>



	<form id="adoption-form" action="AdoptionAction" method="post" enctype="multipart/form-data" name="adoption_submission" target="_self" dir="ltr" lang="en">
		<div class="form-header">
			<h2>Adoption Profile</h2>
			<img src="cust/mantamatcher/img/circle-divider.png"/>
		</div>
		<div class="input-col-1">
			<div class="input-group">
			  <input id="sharkId" class=" input-m-width" name="number" type="hidden" value="<%=adoptionID%>" >
			</div>

			<div class="input-group">
				<span class="input-group-addon">Change Shark Nickname</span>
				<input class="input-l-width" type="text" name="newNickName" id="newNickName"></input>
			</div>
			<div class="input-group">
			  <span class="input-group-addon">Change Adopter Name</span>
			  <input class=" input-l-width" name="adopterName" type="text" value="<%=adopterName%>">
			</div>
			<div class="input-group">
			  <span class="input-group-addon">Change Adopter Email</span>
			  <input class=" input-l-width" name="adopterEmail" type="text" value="<%=adopterEmail%>"><br/>
			</div>
			<div class="input-group">
			  <span class="input-group-addon">Change Adopter Address</span>
			  <input class=" input-l-width" name="adopterAddress" type="text" value="<%=adopterAddress%>">
			</div>
			<div class="input-group">
			  <span class="input-group-addon">Change Adopter Profile Photo</span>
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
		</div>
		<div class="input-col-2">
			<div class="input-group">
			  <span class="input-group-addon">Change Your Quote</span>
			  <textarea name="adopterQuote" id="adopterQuote" placeholder="Enter a personal or gift message here. (e.g. Why is research and conservation of this species important?) here."><%=adopterQuote%>
			  </textarea>
			</div>
			    <button class="large" type="submit" name="Submit" value="Submit">Finish Update<span class="button-icon" aria-hidden="true"></span></button>

		</div>
	</form>
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
		 Stripe.card.createToken($form, stripeResponseHandler);
		 // Prevent the form from submitting with the default action
		 return false;
		});
	});
</script>
