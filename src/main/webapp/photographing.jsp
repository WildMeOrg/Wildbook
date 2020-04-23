<%@ page contentType="text/html; charset=utf-8" language="java" import="org.ecocean.servlet.ServletUtilities,java.util.Properties, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*" %>
<%

//setup our Properties object to hold all properties
	
	
	String context="context0";
	context=ServletUtilities.getContext(request);
	
%>


<jsp:include page="header.jsp" flush="true"/>





<div class="container maincontent">




	<h2>How to photograph African Carnivores for Wildbook</h2>


	<p>
	Identifying distinct individuals is essential to increasing the accuracy and completeness of population counts of these iconic species.  With group-oriented species like African wild dogs and lions, having images that enable us to identify how many animals are in the group you spotted, along with where and when you saw them, is also valuable.
	</p>
			
	<p>
	Remember too that a set of <strong>different</strong> photos of the <strong>same</strong> pack, pride or individual can, collectively, provide more information than just a single image.
	</p>


	<p>
	So we want <strong>ALL</strong> of your photos of any of these species!!!  Even “bad” photos have useful information.
	</p>

	<h3 class="header-primary">General Tips</h3>

	<p>
	For individual identification, images should be:
		<ol>
			<li>
			Of one or more distinguishable individuals (see additional details below by species)
				<ul>
					<li>Side of ‘spotted’ species</li>
					<li>Whisker spots on lions</li>
				</ul>
			</li>
			<li>Relatively large with decent resolution</li>
			<li>In focus and not blurry</li>
		</ol>
	</p>

	<p>
		For animals spotted in <strong>groups</strong>, even a group of two, we want those photos too!  Mothers with pups or cubs, a wandering pair of wild dogs, a coalition of lions, mating leopards or cheetahs; all of these are wonderful sources of information.  Who is seen with whom, when and where, adds immense value. 
	</p>

	<p>
		And if you’ve snapped photos of <i>more than one of these species</i> in the same place at the same time – wonderful!  Please share these with our research community as well.
	</p>

	<h3 class="header-primary">Additionally, photos with:</h3>

	<p>
		<ul>
			<li>Tails & testicles – help with sexing and ID</li>

			<li>Nose, face & neck – help with age and ID</li>

			<li>Scars, ear notches & other unusual features – help with age and ID</li>
		</ul>
	</p>


	<h4 class="header-primary">African Wild Dogs</h4>
	<p>
	The <strong>coat pattern</strong> on the African Wild Dog is unique to each individual.  In fact, it’s so unique that the pattern on one side of the dog is different and unique from the pattern on the other side of the animal.  So clear photos of either side of a wild dog are great; photos of <i>both</i> sides of each wild dog are even better!
	</p>
	<p>
	Meanwhile, have you ever checked out a wild dog’s tail?  There are a few common patterns, but some are quite rare.  A picture from “behind” can help sex the animal and add other interesting data points to the overall African wild dog research.	
	</p>


	<h4 class="header-primary">Leopards</h4>
	<p>
	Leopard spots are called <strong>rosettes</strong> and are the key to individual identification.  Like with wild dogs, the rosette pattern on each side of the leopard is unique from not just other leopards’ rosette patterns, but even from the other side of the same leopard!  So photos of either side of the leopard are great, but photos of both sides are even better.
	</p>
	<p>
	Rosettes vary in their size, shape and pattern making identification extremely complex for the human eye.  But with the right photos, Wildbook can easily and automatically “spot” the distinctions on each leopard coat and identify known and unknown individuals.
	</p>



	<h4 class="header-primary">Cheetahs</h4>
	<p>
	Where cheetahs are concerned, <strong>full body</strong> photos from either (or even better – both) sides are the best kind to help distinguish individuals.  Patterns on a cheetah’s legs and tail, along with the spot pattern on the rest of the body, are used to identify unique individuals.
	</p>
	<p>
	Wildbook can match individuals from incomplete images – that is, photos that contain only part of the distinct coat pattern of an individual, so we want any cheetah photos you’re able to share. 
	</p>
	<p>
	Single cheetahs can be hard to spot so photos of more than one cheetah are even more special – a mother and her cubs, a coalition of males or a mating pair – all of these make for very valuable data for the research community.
	</p>

	<h4 class="header-primary">Lions</h4>
	<p>
	Ironically, researchers use the smallest “spots” to identify Africa’s largest cats.  The pattern of tiny <strong>whisker spots</strong> on a lion’s face are unique – to each side of the face as well as amongst different lions.  Because these are so small, the tighter the profile picture is, the less magnification is needed to spot the spots and the easier identification becomes.
	</p>
	<p>
	Similar to African wild dogs who cavort in packs, lions are often found hanging out together in groups called prides.  It’s very valuable to researchers to know which lions were seen together, when and where, so we appreciate any wider angle photos you have of a pride of lions, a lioness and her cubs or a coalition of males.
	</p>
	<p>
		Since lions don’t have spotted coat patterns like the other species in this Wildbook, researchers often rely on scars and other unusual features like manes, tails and even nose colour & spots to help identify individual images.
		<mark>So with lions, all photos are good – muzzle, full body and group photos all add value to their research community.</mark>
	</p>

	<h4 class="header-primary">Spotted Hyaenas</h4>
	<p>
	Like the other spotted species mentioned here, individual hyaenas are identified by the <strong>spot pattern</strong> on their coats.
	</p>
	<p>
	While spotted hyaenas might seem an unusual choice, they have a direct impact on, and are themselves directly impacted by, the other species included in this African Carnivore Wildbook.  So understanding the spotted hyaena’s population numbers, locations, movements and survival rates helps researchers understand the same about other African carnivores.
	</p>
	<p>
		Photos of either side (or both) of hyaenas you’ve spotted on your travels, and any of their hyaena companions, will help keep their populations healthy and improve the conservation of all carnivores in Africa. 
	</p>

	<hr/>

	<h3 class="header-primary">Examples</h3>



	<h4 class="header-primary">Profile samples:</h4>

	<div class="row">
		<div class="col-xs-12 col-sm-6 col-md-4 col-lg-4">
			<img src="images/howtophotograph/Perfect dog pic.jpg" alt="Perfect dog pic" width="100%" />
		</div>

		<div class="col-xs-12 col-sm-6 col-md-4 col-lg-4">
			<img src="images/howtophotograph/Cheetah at sunset Selinda.jpg" alt="Cheetah at sunset Selinda" width="100%" />
		</div>

		<div class="col-xs-12 col-sm-6 col-md-4 col-lg-4">
			<img src="images/howtophotograph/Leopardess at Duba2.PNG.jpg" alt="Leopardess at Duba2.PNG.jpg" width="100%" />
		</div>
	</div>
	<br/>
	<div class="row">
		<div class="col-xs-12 col-sm-6 col-md-4 col-lg-4">
			<img src="images/howtophotograph/Collared lioness w cubs Meno.jpg" alt="Collared lioness w cubs Meno" width="100%" />
		</div>

		<div class="col-xs-12 col-sm-6 col-md-4 col-lg-4">
			<img src="images/howtophotograph/Lion pride late evening female1.JPG" alt="Lion pride late evening female" width="100%" />
		</div>

		<div class="col-xs-12 col-sm-6 col-md-4 col-lg-4">
			<img src="images/howtophotograph/Male leopard Selinda.jpg" alt="Male leopard Selinda" width="100%" />
		</div>
	</div>
	<br/>
	<div class="row">
		<div class="col-xs-12 col-sm-12 col-md-6 col-lg-6">
			<img src="images/howtophotograph/Hyaena pic.jpg" alt="Hyaena pic" width="100%" />
		</div>

		<div class="col-xs-12 col-sm-12 col-md-6 col-lg-6">
			<img src="images/howtophotograph/Sitting hyaena.JPG" alt="Sitting hyaena" width="100%" />
		</div>
	</div>
	
	<br class="half-break">
	
	<h4 class="header-primary">It’s a boy!  Great rear end shots:</h4>

	<div class="row">
		<div class="col-xs-12 col-sm-6 col-md-6 col-lg-6">
			<img src="images/howtophotograph/Its a boy wild dog at Duba.jpg" alt="Its a boy wild dog at Duba" width="100%" />
		</div>

		<div class="col-xs-12 col-sm-6 col-md-6 col-lg-6">
			<img src="images/howtophotograph/Its a boy cheetah Selinda.jpg" alt="Its a boy cheetah Selinda" width="100%" />
		</div>
	</div>

	<br class="half-break">
	
	<h4 class="header-primary">Great group photos – lots of pics of same pride/pack of animals:</h4>

	<div class="row">
		<div class="col-xs-12 col-sm-6 col-md-6 col-lg-6">
			<img src="images/howtophotograph/Lion pride late evening all.JPG" alt="Lion pride late evening all" width="100%" />
		</div>

		<div class="col-xs-6 col-sm-3 col-md-3 col-lg-3">
			<img src="images/howtophotograph/Lion pride late evening female1.JPG" alt="Lion pride late evening female 1" width="100%" />
		</div>

		<div class="col-xs-6 col-sm-3 col-md-3 col-lg-3">
			<img src="images/howtophotograph/Lion pride late evening male1.JPG" alt="Lion pride late evening male 1" width="100%" />
		</div>
	</div>

	<br/>

	<div class="row">
		<div class="col-xs-12 col-sm-12 col-md-6 col-lg-6">
			<img src="images/howtophotograph/Group of dogs Selinda.jpg" alt="Group of dogs Selinda" width="100%" />
		</div>

		<div class="col-xs-12 col-sm-12 col-md-6 col-lg-6">
			<img src="images/howtophotograph/Group of dogs Selinda2.jpg" alt="Group of dogs Selinda 2" width="100%" />
		</div>
	</div>

	<br class="half-break">
	
	<h4 class="header-primary">Interesting but not very useful:</h4>
	
	<div class="row">
		<div class="col-xs-12 col-sm-6 col-md-4 col-lg-4">
			<img src="images/howtophotograph/Lioness and 2 cubs in blue Selinda.JPG" alt="Lioness and 2 cubs in blue Selinda" width="100%" />
		</div>

		<div class="col-xs-12 col-sm-6 col-md-4 col-lg-4">
			<img src="images/howtophotograph/Dogs in riverbed at Duba.jpg" alt="Dogs in riverbed at Duba" width="100%" />
		</div>

		<div class="col-xs-12 col-sm-6 col-md-4 col-lg-4">
			<img src="images/howtophotograph/Adorable dog but not for ID.JPG" alt="Adorable dog but not for ID" width="100%" />
		</div>
	</div>
	
	<br/>

</div> <!-- end container/maincontent -->

<jsp:include page="footer.jsp" flush="true" />

