<%@ page contentType="text/html; charset=utf-8" language="java" import="org.ecocean.servlet.ServletUtilities,java.util.ArrayList,java.util.List,java.util.ListIterator,java.util.Properties, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*, org.apache.commons.lang3.StringEscapeUtils" %>
<%

//setup our Properties object to hold all properties
	Properties props=new Properties();
	String langCode=ServletUtilities.getLanguageCode(request);
	
	String context="context0";
	context=ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);
	
	//set up the file input stream
	//FileInputStream propsInputStream=new FileInputStream(new File((new File(".")).getCanonicalPath()+"/webapps/ROOT/WEB-INF/classes/bundles/"+langCode+"/submit.properties"));
	//props.load(getClass().getResourceAsStream("/bundles/"+langCode+"/whoweare.properties"));
	props=ShepherdProperties.getProperties("whoweare.properties", langCode, context);
        request.setAttribute("pageTitle", "Kitizen Science &gt; How Kitizen Science Works");
    
	
%>

<jsp:include page="header.jsp" flush="true" />

<div class="container maincontent">


<h1>How Kitizen Science Works</h1>
<%= NoteField.buildHtmlDiv("bd4d8139-03e1-4f03-b0d1-e0af49f83e50", request, myShepherd) %>
<div class="org-ecocean-notefield-default" id="default-bd4d8139-03e1-4f03-b0d1-e0af49f83e50">
<p><em>First, a short definition of terms used on this page and elsewhere on the website.  There are a lot of names for programs that sterilize unowned outdoor cats (trap-neuter-return, shelter-neuter-return, return to field, community cat programs) and return them to where they were trapped.  Here, we use &quot;spay/neuter program&quot; as our most all-inclusive and neutral term.  There are also many names for unowned outdoor cats (feral cat, community cat, stray cat, working cat, barn cat).  We have chosen to use &quot;free-roaming cat&quot; as our most all-inclusive and neutral term. </em></p>
</div>

<h2><img src="images/how_photographingcats.jpg" width="382" height="300" hspace="10" vspace="10" align="left" /><a name=whatcitsci id="whatcitsci"></a>What is &quot;citizen science&quot;? </h2>
<%= NoteField.buildHtmlDiv("bd4d8139-03e1-4f03-b0d1-e0af49f83e51", request, myShepherd) %>
<div class="org-ecocean-notefield-default" id="default-bd4d8139-03e1-4f03-b0d1-e0af49f83e51">
<p><a href="https://en.wikipedia.org/wiki/Citizen_science" target="_blank">Citizen science</a> projects bring together researchers and the community to study a topic of interest by involving volunteers in roles such as data collection and data organization.  These projects not only reduce the cost of conducting research, but also engage the public in the scientific process around issues that matter to them.  Many of the largest and longest-running citizen science programs are about monitoring songbirds (like the <a href="https://www.audubon.org/conservation/join-christmas-bird-count" target="_blank">Christmas Bird Count</a>), but the citizen science framework is also used to learn about a variety of other issues: <a href="https://spacehack.org" target="_blank">exploring astronomy</a>, <a href="https://fold.it/portal/" target="_blank">folding proteins</a>, <a href="https://xerces.org/citizen-science/" target="_blank">tracking the decline of bees and insects</a>, <a href="https://www.epa.gov/citizen-science/examples-citizen-science-projects-supported-epa" target="_blank">testing air and water quality</a>, <a href="https://coralwatch.org/" target="_blank">studying coral reef bleaching</a>, <a href="http://www.meadowatch.org" target="_blank">timing when wildflowers bloom</a>, and more.  Our technical partners, Wildbook, collaborate on projects for amazing animals such as <a href="https://www.whaleshark.org/" target="_blank">whale sharks</a>, <a href="https://www.mantamatcher.org/" target="_blank">manta rays</a>, <a href="https://iot.wildbook.org/" target="_blank">sea turtles</a>, <a href="https://giraffespotter.org/" target="_blank">giraffe</a>, <a href="https://www.flukebook.org/" target="_blank">whales</a>, and big cats like <a href="https://jaguar.wildbook.org/" target="_blank">jaguars</a> and <a href="https://lynx.wildbook.org" target="_blank">lynx</a>. </p>
<p>Kitizen Science was founded to use citizen science methods to monitor the impacts of spay/neuter programs on free-roaming cat populations in partnerships with spay/neuter organizations.  Kitizen Science will not be operating clinics ourselves, but partnering with those who do in order to conduct impact assessments.  Our community of volunteers will be both local and global thanks to the internet, so you can participate whether or not you live near a study site. </p>
</div>

<h2><a name="sizepop" id="sizepop"></a>How can you know the size of a cat population?</h2>
<p><img src="images/how_calicobutt.jpg" width="178" height="454" hspace="10" vspace="10" align="right" />
<%= NoteField.buildHtmlDiv("bd4d8139-03e1-4f03-b0d1-e0af49f83e52", request, myShepherd) %>
<div class="org-ecocean-notefield-default" id="default-bd4d8139-03e1-4f03-b0d1-e0af49f83e52">
If you operate a spay/neuter program, you may have heard that you can estimate the cats in an area by taking the human population and dividing it by some number.  That's a great way to make a ballpark guess at how many animals your program might serve when you are applying for your first grants, but this ratio method is not meant for monitoring changes in cat populations over time.  If you reduce the cat population in your target area by 25%, that doesn't automatically reduce the human population, and vice versa. </p>
<p>Unless you are working in a small area like a neighborhood or RV park where you can conduct a true census (identify every single cat), it is not possible to count all the cats in a large area.  Cats aren't trees: they move and hide, and won't line up and sit still to be counted.  So, how can we track the number of free-roaming cats in an area if a true census isn't possible? </p>
<p>Thankfully, there is no need for us to reinvent the wheel.  Assessing an animal group's size is one of the fundamental tools of a wildlife population ecologist's toolkit. </p>
<p>There are many methods for estimating animal populations, and Kitizen Science is blending two of them to create a system in the perfect middle ground of being scientifically rigorous but still simple enough for volunteers to do.  Our program will be collecting data from volunteers taking photos of outdoor cats along set paths called <a href="https://en.wikipedia.org/wiki/Transect" target="_blank">transects</a>, and the data will be processed and analyzed using photographic <a href="https://en.wikipedia.org/wiki/Mark_and_recapture" target="_blank">mark-recapture  analysis</a> (also referred to as photographic mark-resight or photographic capture-recapture).</p>
<h2><a name="photocmr" id="photocmr"></a>What is photographic mark-recapture? </h2>
<p>Think back to the problem preventing us from conducting a cat census: cats move around.  While this might seem like a barrier, it is actually the thing that makes photographic mark-recapture work.  Traditionally, this would mean physically capturing some animals in a population, marking them in a visible way, releasing these animals to mingle with others, capturing some members of this population again, and counting how many were previously marked.  From that information, you can estimate how many animals are in the larger population. </p>
<p>Mark-recapture population estimation involves a lot of statistics, but here is a simple example.  You trap 20 cats and spray them with dye, then set them free to move around in the local cat population.  The next day, you come back and trap 20 cats again, and 10 out of 20 of these cats were dyed.  From that, you can infer that there are about 40 cats in the area, since it appears that the 20 cats you sprayed with dye make up about half of the population.  (In the real world, this is more complicated mathematically and it includes more variables, but this example conveys the basic principle.<br />
</p>
</div>

<p align="center"><img src="images/how_markrecapture.jpg" width="1000" height="195" hspace="10" vspace="10" align="middle" /><br />
</p>
<%= NoteField.buildHtmlDiv("bd4d8139-03e1-4f03-b0d1-e0af49f83e53", request, myShepherd) %>
<div class="org-ecocean-notefield-default" id="default-bd4d8139-03e1-4f03-b0d1-e0af49f83e53">
<p>Kitizen Science is using <em>photographic</em> mark-recapture because it does not require stressful capturing or handling of animals.  Instead of a cat being &quot;marked&quot; physically, it is &quot;marked&quot; by being identified as a unique individual based on its natural coat patterns.  (Animal welfare is a top priority of Kitizen Science, and our research methods are completely non-invasive.  The cats being studied won't even realize it!)  Not all cats in an area need to be uniquely identifiable, such as solid black or grey coat colors.</p>
</div>

<h2><a name="howdoes" id="howdoes"></a>How does Kitizen Science work? </h2>
<p><img src="images/how_laptopcomparison.jpg" width="396" height="300" hspace="10" vspace="10" align="left" />
<%= NoteField.buildHtmlDiv("bd4d8139-03e1-4f03-b0d1-e0af49f83e54", request, myShepherd) %>
<div class="org-ecocean-notefield-default" id="default-bd4d8139-03e1-4f03-b0d1-e0af49f83e54">
<p>Kitizen Science is built using the <a href="http://wildbook.org/doku.php" target="_blank">Wildbook  citizen science platform</a>, which crowdsources photo data organization/classification work.  We have two volunteer roles.  People who live near an area with an active monitoring project can participate as <em>Cat Walk Volunteers</em> to follow a map and photograph any cat they see outdoors.  These photos will be uploaded securely via our smart phone app and sent to our web servers for processing by our second group. <em>Cat and Mouse Volunteers</em> can be located anywhere in the world so long as they have a computer and an internet connection.  They will be sorting and processing photo submissions, and multiple volunteers will be verifying each submission to improve accuracy.  By comparing photo submissions to other cats in the area, and matching cats, a profile is built for each unique-looking animal.  These profiles form capture histories for mark-recapture statistical analysis.  This process will be repeated annually to monitor whether numbers of free-roaming cats are going up or down over time. </p>
<h2><a name="computers" id="computers"></a>Can't computers do this instead of volunteers?</h2>
<p>It might be possible in a decade or two, but not yet.  Many of Wildbook's other projects use artificial intelligence (also known as machine learning) to identify and match animals, but this type of technology doesn't work yet on domestic cats.  It may sound counterintuitive, but cats are actually too different for computer algorithms to be able to &quot;see&quot; cats of various color patterns as the same species.  A jaguar with her spotted coat pattern can more easily be compared against the precise spacing of spots on other jaguars, but a grey cat and a black-and-white tuxedo cat don't even look like the same species to a computer.  While Wildbook will help us to cut down on some work through its cool artificial intelligence algorithms, identifications still need to be done by human volunteers.</p>
<h2><a name="infoprivacy" id="infoprivacy"></a>What if this information gets into the wrong hands? </h2>
<p>Free-roaming cat caretakers and advocates can be wary of those perceived as outsiders in order to protect the safety of cats in their care.  We want to assure the cat advocate community that Kitizen Science is not seeking to create a database of private information about cats and colonies.  We understand your concerns and have carefully designed our program to address them by collecting the least amount of information possible to accomplish our goals. </p>
</div>

<p><img src="images/how_neighborhood.jpg" width="300" height="300" hspace="10" vspace="10" align="right" />
<%= NoteField.buildHtmlDiv("bd4d8139-03e1-4f03-b0d1-e0af49f83e55", request, myShepherd) %>
<div class="org-ecocean-notefield-default" id="default-bd4d8139-03e1-4f03-b0d1-e0af49f83e55">
In our connected world, leaks and hacks happen frequently, and data you thought were private can suddenly become public.  There are two basic ways of dealing with private information.  There is <em>privacy by policy</em>, where a business or organization collects private information and has terms and conditions that promise to not release private information without your consent.  However, this is based on trust and is still vulnerable to hackers and accidental leaks.  By contrast, there is <em>privacy by design</em>, which means that private information is not collected in the first place, so there is nothing sensitive to be leaked or stolen.  Kitizen Science has been created as the latter: privacy by design. </p>
<p>We do not want to know about colony locations, names of caretakers, locations of feeding stations, veterinary medical records, or anything that is private or sensitive.  Kitizen Science only collects information that any person could gather simply by walking down the street: the location of a cat at a moment in time.  (The &quot;magic&quot; of Kitizen Science is in our research methods and what we can do with statistics based on information obtained from photographing cats in public.)  A cat wanders for multiple blocks around his feeding station or sleeping place, so knowing that an orange cat was spotted on the intersection of Maple Street and Oak Avenue doesn't tell you where that cat will be months or years from now or who is feeding him. </p>
<p>No entity collecting private information can guarantee that the data could never be stolen or misused, but we can prevent the exposure of private information about cats and their caretakers by never collecting it.</p>
</div>

<p>&nbsp; </p>

</div>

<jsp:include page="footer.jsp" flush="true" />

