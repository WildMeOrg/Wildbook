<%@ page contentType="text/html; charset=utf-8" language="java" import="org.ecocean.servlet.ServletUtilities,java.util.ArrayList,java.util.List,java.util.ListIterator,java.util.Properties, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*, org.apache.commons.lang3.StringEscapeUtils" %>
<%

//setup our Properties object to hold all properties
	Properties props=new Properties();
	String langCode=ServletUtilities.getLanguageCode(request);
	
	String context="context0";
	context=ServletUtilities.getContext(request);
	
	//set up the file input stream
	//FileInputStream propsInputStream=new FileInputStream(new File((new File(".")).getCanonicalPath()+"/webapps/ROOT/WEB-INF/classes/bundles/"+langCode+"/submit.properties"));
	//props.load(getClass().getResourceAsStream("/bundles/"+langCode+"/whoweare.properties"));
	props=ShepherdProperties.getProperties("whoweare.properties", langCode, context);
    
        request.setAttribute("pageTitle", "Kitizen Science &gt; Why Kitizen Science is Needed");
	
%>

<jsp:include page="header.jsp" flush="true" />

<div class="container maincontent">


<h1>Why Kitizen Science is Needed</h1>
<h2><img src="images/why_momandkittens.jpg" width="459" height="250" hspace="10" vspace="10" align="right" />Definitions and history   </h2>
<p>First, a short definition of terms used on this page and elsewhere on the website.  There are a lot of names for programs that sterilize unowned outdoor cats and return them to where they were trapped (trap-neuter-return, shelter-neuter-return, return to field, community cat programs).  We use &quot;spay/neuter program&quot; because it is an all-inclusive and neutral term.  There are also many names for unowned outdoor cats (feral cat, community cat, stray cat, working cat, barn cat).  We have chosen to use &quot;free-roaming cat&quot; as the most all-inclusive and neutral term.   </p>
<p>Depending on who you ask, the idea of using spay/neuter programs to control free-roaming cat populations in the United States started in the early 1990s in Newburyport, MA, Orlando, FL, or Washington, DC.  The concept seeks to stabilize and decrease a free-roaming cat population by using surgical sterilization (removing the testicles or ovaries and uterus) to prevent new kitten births and allowing the adults to live out their life spans.  This is an alternative to culling, the traditional animal control method of capturing unowned cats, impounding them to an animal shelter, and ultimately euthanizing them. </p>
<h2><a name="effectsof" id="effectsof"></a>Effects of spay/neuter programs</h2>
<p>There is broad public support for using spay/neuter programs instead of culling to manage free-roaming cat populations, and there is peer-reviewed research that these spay/neuter efforts can correlate with decreases in shelter intake, lower shelter euthanasia of cats, and reduced cat nuisance complaint calls.  However, there is little evidence as to whether spay/neuter programs control or reduce <em>the size of cat populations</em>.   </p>
<p>Those who are involved with spay/neuter programs believe that their work is reducing cat populations.  It's easy to find people in cat welfare circles who can tell you about their personal experiences over years or decades of trapping cats and taking care of cat colonies while watching populations decline or disappear over time.  Although these oral histories are compelling to cat advocates, personal stories aren't scientific evidence that spay/neuter programs can reduce cat populations. </p>
<p>Read about scientific evidence on <a href="spayneuter.jsp">the effects of spay/neuter programs</a>. </p>
<h2><img src="images/why_catgraph.jpg" width="454" height="300" hspace="10" vspace="10" align="left" /><a name="metrics" id="metrics"></a>Metrics and impact assessment</h2>
<p>The best policies and programs are those that are shown by data to be proven effective at doing the things they claim to do.  Rigorously testing what works best helps a field move forward (and save more lives).    </p>
<p>Most of the spay/neuter research published by veterinarians and animal welfare professionals focuses on the metric that is most important to cat lovers: reducing the deaths of cats.  However, for those who oppose using spay/neuter programs and push for widespread culling of cats, reducing cat deaths is not the issue that matters to them.  Metrics championed by cat advocates as proof that spay/neuter programs work - decreases in shelter intake, lower shelter euthanasia of cats, and reduced cat nuisance complaint calls – are often viewed by cat advocates as evidence for cat population decreases.  Fewer cats entering animal shelters could mean that there are fewer free-roaming cats outdoors, or it <em>could</em> mean that a given shelter simply changed their intake policies.    </p>
<p>The reduced intake of cats to animal shelters is an <em>indirect metric</em> of a decrease in free-roaming cat populations, as is a decrease in shelter euthanasia or cat-related complaint calls to animal control.  Whereas this type of data is easy to obtain and track over time – and it's better to track these indirect metrics than nothing at all – there is a need for better tools for monitoring the <em>direct metric</em> of the number of cats on the outdoor landscape.  Of the ways that one can conduct an impact assessment of a spay/neuter program, this is the most difficult metric to study.  Few people in cat welfare and veterinary worlds know about methods to quantify populations of free-roaming animals.  Wildlife researchers and population ecologists, however, have an array of methods to do so.</p>
<h2><a name="vetmed" id="vetmed"></a>Veterinary medicine and population ecology</h2>
<p>Veterinarians generally focus on <em>individual-level organismal biology</em>: genetics, cellular biology, physiology, and an individual animal's health and welfare.  Veterinary education includes some training in epidemiology – the study of disease in populations of animals – but this is mostly limited to meat safety in animal agriculture operations and zoonotic diseases like rabies.  Veterinarians are great at what they do, but they don't have the training to quantify and monitor population sizes of free-roaming and wild animals.   </p>
<p>Population ecologists are wildlife researchers that focus on <em>groups of animals</em> and the size and dynamics (changes in size) of a population.  They can't diagnose an animal with a medical condition or provide treatment the way a veterinarian can, but a population ecologist can study questions that involve animal reproduction, behavior, adaptations to living near humans, such as, &quot;How many offspring do coyotes have in the suburbs of California versus the wilds of Alaska?&quot;   </p>
<p>Community ecologists go a step further and study how populations of <em>many species</em> interact together as a network.  They explore questions such as, &quot;What happens to coyote populations when there is a huge increase in mouse populations, and what effect will that have on grasshopper populations the following year?&quot;</p>
<p align="center"><img src="images/why_biologicalorganization.jpg" width="1000" height="183" hspace="10" vspace="10" align="middle" /></p>
<h2><a name="collaborations" id="collaborations"></a>Collaborations to fix big issues</h2>
<p>When spay/neuter organizations, veterinarians, and population ecologists work together, big things can happen and we can tackle complex issues together.  Two such collaborations are already occurring.   </p>
<p><a href="http://www.dccatcount.org/" target="_blank">The DC Cat Count</a> is a collaboration between PetSmart Charities, The Humane Society of the United States, the Humane Rescue Alliance, the Smithsonian Conservation Biology Institute, and the ASPCA.  Together, these institutions are conducting a large, complex three year study of the population dynamics of cats in Washington, DC, and the interconnected flow of cats between free-roaming, owned pets, and shelter animals.   </p>
<p><a href="https://www.catssafeathome.org/hayden-island-project" target="_blank">The Hayden Island Cat Project</a> is a collaboration between the Feral Cat Coalition of Oregon and Portland Audubon.  Together, they are conducting annual transect count surveys of an island neighborhood in Portland, Oregon to monitor the cat population and see if the island's spay/neuter program is reducing the number of cats.</p>
<h2><img src="images/why_handswithphone.jpg" width="281" height="300" hspace="10" vspace="10" align="right" /><a name="potential" id="potential"></a>Every spay/neuter program is a potential experiment </h2>
<p>Kitizen Science was founded to conduct impact assessment research of the hard work around the country that is already occurring to sterilize cats.  Once our methods are validated and any needed adjustments are made, Kitizen Science will be seeking partnerships with feline spay/neuter organizations to work together to monitor their program impacts over time.  (Are you interested in being one of those groups?  Email kitizenscience@gmail.com.)   </p>
<p>Having rigorous impact assessments conducted by a third party will be useful to individual groups, and this knowledge is even more valuable once aggregated and compared.  Because Kitizen Science is low-cost and volunteer-driven, it can be deployed in many locations for a reasonable cost, which maximizes the amount of useful information per funding dollar.  Whether a spay/neuter program in a given location is demonstrated to be highly impactful or not, understanding both scenarios is useful to the big picture so we can learn how to do more of what works and less of what doesn't work.</p>


</div>
<jsp:include page="footer.jsp" flush="true" />

