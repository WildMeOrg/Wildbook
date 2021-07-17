<%@ page contentType="text/html; charset=utf-8" language="java" import="org.ecocean.servlet.*, org.ecocean.*, java.util.Properties, java.util.Date, java.util.Enumeration, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException" %>
<jsp:include page="header.jsp" flush="true" />
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
	myShepherd.setAction("createadoption.jsp");
	myShepherd.beginDBTransaction();
	try{
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
		sessionShark =((String)session.getAttribute( "queryShark")).trim();
	}
	Boolean sessionPaid = false;
	if (session.getAttribute( "paid") != null) {
		sessionPaid =(Boolean)session.getAttribute( "paid");
	}

	session.setAttribute( "emailEdit", false );

	boolean hasNickName = true;
	String nick = "";
	try {
		if ((sessionShark != null)&&(myShepherd.getMarkedIndividual(sessionShark)!=null)) {
			MarkedIndividual mi = myShepherd.getMarkedIndividual(sessionShark);
			nick = mi.getNickName();
			if (((nick==null) || nick.equals("Unassigned"))||(nick.equals(""))) {
				hasNickName = false;
			}
		}
	}
	catch (Exception e) {
		System.out.println("Error looking up nickname: "+sessionShark);
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

<link rel="stylesheet" href="css/createadoption.css">
<style type="text/css">
	#adoption-html-wrapper {
		margin-left: 4.7em;
	}
</style>
<script type="text/javascript">
	$(document).ready(function () {
		let adoptionHtml = '';
		adoptionHtml += '<script src="https://donorbox.org/widget.js" paypalExpress="false"></scr' + 'ipt>';
		let adoptionUrl = '<%=CommonConfiguration.getAdoptionCampaignUrl(context)%>';
		adoptionHtml += '<iframe allowpaymentrequest="" frameborder="0" height="900px" name="donorbox" scrolling="no" seamless = "seamless" src = "' + adoptionUrl + '"style = "max-width: 500px; min-width: 250px; max-height:none!important" width = "100%" ></iframe > ';
		$('#adoption-html-wrapper').html(adoptionHtml);

	});
</script>

<div class="container maincontent">
  <section class="centered">
    <h2>Thank you for your support!</h2>
    <h4>After filling out the financial information, you will be able to create your profile and choose and nickname your animal.</h4>

     <h3>Financial Information</h3>
		<img id="circle-divider" src="cust/mantamatcher/img/circle-divider.png"/><br><br>

  </section>

  <section class="centered" id="adoption-html-wrapper">

  </section>

	<form id="adoption-form" style="display:none;" action="AdoptionAction" method="post" enctype="multipart/form-data" name="adoption_submission" target="_self" dir="ltr" lang="en">
		<div class="form-header">
			<h2>Adoption Profile</h2>
			<img src="cust/mantamatcher/img/circle-divider.png"/>
		</div>
		<div class="input-col-1">
			<div class="input-group">
			  <span class="input-group-addon">Shark ID</span>
			  <input id="sharkId" class=" input-m-width" name="shark" type="text" value="<%=sessionShark%>" placeholder="Browse the gallery and find the shark that suits you">  <%if (!shark.equals("")) { %>
			   <%
			      }
			    %>
			</div>
			<%
				if ((hasNickName == false )||(nick.equals(""))) {
			%>
			<div class="input-group">
				<span class="input-group-addon">Shark Nickname</span>
				<input class="input-l-width" type="text" name="newNickName" id="newNickName"></input>
			</div>
			<%
				}
			%>
			<input class="input-m-width adoptionStartDate" name="adoptionStartDate" type="hidden" value="<%=adoptionStartDate%>">

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
			  <span class="input-group-addon">Message</span>
			  <textarea name="adopterQuote" id="adopterQuote" placeholder="Enter a personal or gift message here. (e.g. Why is research and conservation of this species important?) here."><%=adopterQuote%>
			  </textarea>
			</div>

			<%-- Recaptcha widget --%>
			<%= ServletUtilities.captchaWidget(request) %>

			<!-- No submit button unless payment is accepted. May switch to totally non visible form prior to payment. -->
			  <%
			    if (acceptedPayment) {
			  %>
			    <button class="large" type="submit" name="Submit" value="Submit">Finish Adoption<span class="button-icon" aria-hidden="true"></span></button>
			  <%
			    }
			  %>
			<%
			  if (acceptedPayment) {
			%>
		</div>
	</form>
	<%
	}
	%>
</div>

<!-- Javascript Section -->

<%
	}catch (Exception e){
		e.printStackTrace();
	}finally{
		myShepherd.rollbackDBTransaction();
		myShepherd.closeDBTransaction();
	}
%>
<jsp:include page="footer.jsp" flush="true" />
