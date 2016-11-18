<%@ page contentType="text/html; charset=utf-8" language="java" import="org.ecocean.servlet.ServletUtilities,org.ecocean.*,java.util.Properties, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException" %>
<%

String context="context0";
context=ServletUtilities.getContext(request);

	//language setup
	String langCode="en";
	if(session.getAttribute("langCode")!=null){langCode=(String)session.getAttribute("langCode");}

	Properties props=new Properties();
	props.load(getClass().getResourceAsStream("/bundles/"+langCode+"/submit.properties"));



%>
<jsp:include page="header.jsp" flush="true"/>
<link rel="stylesheet" href="css/createadoption.css">

<div class="container maincontent adoption-page">
	<h1 class="intro-adopt">Adopt a Shark</h1>
	<h2>Support cutting edge whale shark research.</h2>
	<section class="adoption-details">
		<p>
			Adopt a whale shark, give it a nickname, and receive updates each time it's spotted! Funds
			raised by shark adoptions are used to offset the costs of maintaining this global library and
			to support new and existing research projects for the world's most mysterious fish.
		</p>
		<a href="gallery.jsp?adoptableSharks=true"><button type="button" name="make adoption" class="large">Choose a shark<span class="button-icon" aria-hidden="true"></button></a>
	</section>
	<section id="custom-donation-image">
		<img src="cust/mantamatcher/img/shark-donation-scale.jpeg" alt="donation options" />
	</section>
	<%--If you dont want to use a custom image for your donation scale, comment out #custom-donation-image above and use the code below for some simple circles with price options  --%>
	<%-- <section class="donations">
		<article class="donation-option-group">
			<div class="donation-option">
				<div class="donation-circle">
					<p>Fry</p>
					<p>$2/m</p>
				</div>
				<p>
					A great option for children
				</p>
			</div>
			<div class="donation-option">
				<div class="donation-circle">
					<p>Whopper</p>
					<p>$5/m</p>
				</div>
				<p>
					Makes an excellent gift!
				</p>
			</div>
			<div class="donation-option">
				<div class="donation-circle">
					<p>Behemoth</p>
					<p>$20/m</p>
				</div>
				<p>
					Big families and groups
				</p>
			</div>
			<div class="donation-option">
				<div class="donation-circle">
					<p>Legend</p>
					<p>$1000/yr</p>
				</div>
				<p>
					Corporate adoptions and legends of the sea
				</p>
			</div>
			<div class="donation-option">
				<div class="donation-circle">
					<p>Custom</p>
					<p>$?/yr</p>
				</div>
				<p>
					We appreciate your support of ongoing whale shark research
				</p>
			</div>
		</article>
	</section> --%>

	<section class="adopters-featured">
			<h2>Join the Family!</h2>
			<article class="adopter-feature-gallery">
				<div class="adopter">
					<div class="adopter-header" >
						<p>
							Whale Shark Adopter
						</p>
					</div>
					<img src="cust/mantamatcher/img/someguy.jpg" alt="" />
					<div class="adopter-details">
						<p>
							Some Guy
						</p>
						<p>
							Adopted Finnegan
						</p>
						<p>
							We're going to need a bigger fish tank.
						</p>
					</div>
				</div>
				<div class="adopter">
					<div class="adopter-header" >
						<p>
							Whale Shark Adopter
						</p>
					</div>
					<img src="cust/mantamatcher/img/someguy.jpg" alt="" />
					<div class="adopter-details">
						<p>
							Some Guy
						</p>
						<p>
							Adopted Finnegan
						</p>
						<p>
							Dun dun. Dun dun. Dun dun dun dun dun dun dun dun, da naaa
						</p>
					</div>
				</div>
				<div class="adopter">
					<div class="adopter-header" >
						<p>
							Whale Shark Adopter
						</p>
					</div>
					<img src="cust/mantamatcher/img/someguy.jpg" alt="" />
					<div class="adopter-details">
						<p>
							Some Guy
						</p>
						<p>
							Adopted Finnegan
						</p>
						<p>
							Dun dun. Dun dun. Dun dun dun dun dun dun dun dun, da naaa
						</p>
					</div>
				</div>
				<div class="adopter">
					<div class="adopter-header" >
						<p>
							Whale Shark Adopter
						</p>
					</div>
					<img src="cust/mantamatcher/img/someguy.jpg" alt="" />
					<div class="adopter-details">
						<p>
							Some Guy
						</p>
						<p>
							Adopted Finnegan
						</p>
						<p>
							Dun dun. Dun dun. Dun dun dun dun dun dun dun dun, da naaa
						</p>
					</div>
				</div>
			</article>
		</section>




</div>
<jsp:include page="footer.jsp" flush="true" />
