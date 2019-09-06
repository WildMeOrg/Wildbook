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

        request.setAttribute("pageTitle", "Kitizen Science &gt; Who We Are");
%>

<jsp:include page="header.jsp" flush="true" />

<div class="container maincontent">

<h1>Who We Are</h1>
<p>During our beta period, Kitizen Science is being developed as a Master's project lead by a cat lady in a Wildlife Science program, supported by a thesis committee of wildlife researchers and a nonprofit board  of  veterinarians.  (Read more about the importance of <a href="why.jsp">veterinarians and wildlife population ecologists working together</a> to address cat overpopulation.)</p>
<p><strong>Follow us on social media: </strong></p>
<p><a href="https://www.facebook.com/kitizenscience/" target="_blank">Facebook</a>, <a href="https://twitter.com/kitizenscience" target="_blank">Twitter</a>, <a href="https://www.instagram.com/kitizenscience/" target="_blank">Instagram</a>. </p>
<p><strong>Join our mailing list for news and updates:</strong></p>


<div align="center">
  <table width="400" border="0" align="left" cellpadding="0" cellspacing="0">
    <tr>
      <td>
          
          
  <!-- Begin Mailchimp Signup Form -->
  <link href="//cdn-images.mailchimp.com/embedcode/classic-10_7.css" rel="stylesheet" type="text/css">
  <style type="text/css">
	#mc_embed_signup{background:#fff; clear:left; width:500px;}
	/* Add your own Mailchimp form style overrides in your site stylesheet or in this style block.
	   We recommend moving this block and the preceding CSS link to the HEAD of your HTML file. */
</style>
  <div id="mc_embed_signup">
  <form action="https://gmail.us20.list-manage.com/subscribe/post?u=abd67dc212c12b5072c70a518&amp;id=bd017987f4" method="post" id="mc-embedded-subscribe-form" name="mc-embedded-subscribe-form" class="validate" target="_blank" novalidate>
    <div id="mc_embed_signup_scroll">
      
  <div class="mc-field-group">
    <label for="mce-EMAIL"><span class="style2">Email Address </span></label>
    <div align="center">
      <input type="email" value="" name="EMAIL" class="required email" id="mce-EMAIL">
      </div>
  </div>
	  <div id="mce-responses" class="clear">
	    <div class="response" id="mce-error-response" style="display:none"></div>
		  <div class="response" id="mce-success-response" style="display:none"></div>
	  </div>    <!-- real people should not fill this in and expect good things - do not remove this or risk form bot signups-->
      <div style="position: absolute; left: -5000px;" aria-hidden="true"><input type="text" name="b_abd67dc212c12b5072c70a518_bd017987f4" tabindex="-1" value=""></div>
      <div class="clear">
        <div align="center">
          <input type="submit" value="Subscribe" name="subscribe" id="mc-embedded-subscribe" class="button">
        </div>
      </div>
      </div>
  </form>
  </div>
  <script type='text/javascript' src='//s3.amazonaws.com/downloads.mailchimp.com/js/mc-validate.js'></script><script type='text/javascript'>(function($) {window.fnames = new Array(); window.ftypes = new Array();fnames[0]='EMAIL';ftypes[0]='email';fnames[3]='ADDRESS';ftypes[3]='address';fnames[4]='PHONE';ftypes[4]='phone';}(jQuery));var $mcj = jQuery.noConflict(true);</script>
  <!--End mc_embed_signup-->      </td>
    </tr>
  </table>
</div>

<p><br />
  <br />
  <br />
  <br />
  <br />
<table width="100%" border="0" cellspacing="0" cellpadding="0">
  <tr>
    <td colspan="2"><h2>Executive Director and Chief Cat Scientist</h2></td>
  </tr>
  <tr>
    <td width="160" height="210" valign="top"><div align="left"><img src="images/about_sabrinaaeluro.jpg" width="150" height="200" align="top" /></div></td>
    <td height="210" valign="top"><strong>Sabrina Aeluro</strong> is an animal lover who decided to go back to school to become a science-based, professional-strength cat lady. She originally thought she would become a veterinarian, but got hooked on research. She is interested in free-roaming cat behavior in urban landscapes and conducting impact assessments of spay/neuter programs - all with the aim of learning more effective ways of reducing populations of free-roaming cats using cat-friendly methods. Sabrina loves being part of the growing movement to build collaborations between &quot;cat people&quot; and &quot;wildlife people,&quot; and she also studies the human dimensions of cat management and how feral cat advocacy groups operate. Sabrina attends the University of Washington in Seattle where she earned a Bachelor's degree in Biology and an Applied Animal Behavior Certificate. She is now working on a Master's degree in Wildlife Science in the Predator Ecology Lab, plus a Graduate Certificate in One Health. Validating Kitizen Science is her Master's project. Sabrina also enjoys travel, vegan cooking, scuba diving, volunteering with the Feral Cat Spay/Neuter Project and Animal Balance, and her two blind cats, <a href="https://www.facebook.com/blindcathoneybee" target="_blank">Honey Bee and Fig</a>. Her personal website is <a href="https://www.aeluro.com/" target="_blank">aeluro.com</a>.</td>
  </tr>
  <tr>
    <td colspan="2"><h2><br />
    Nonprofit Board Members - More Coming Soon</h2></td>
  </tr>
  <tr>
    <td width="160" height="210" valign="top"><div align="left"><img src="images/about_jenbuchanan.jpg" width="150" height="200" align="top" /></div></td>
    <td height="210" valign="top"><strong>Dr. Jennifer Buchanan</strong> discovered her love for animals growing up on a cattle ranch in Southern Oregon.  After earning her Doctorate in Veterinary Medicine at Oregon State University in 2010, she jumped right into shelter medicine, taking a position at Seattle Humane Society.  During her time there she continued to evolve with the shelter and developed protocols and policies based on the newly published ASV guidelines. It was through this work that she became invested in community cat welfare and in 2015, she joined <a href="http://www.feralcatproject.org/" target="_blank">Feral Cat Spay/Neuter Project</a> as their Lead Veterinarian. She is currently pursuing her Master’s degree through the University of Florida. Dr. Buchanan is proud and excited to be a part of Kitizen Science because she recognizes the need for long term, sustainable and humane community cat management practices. By focusing on evidence-based studies and maintaining a balanced viewpoint she hopes to support high quality research that can be used to build bridges between all animal and ecological advocates. In addition to work and school, Jennifer enjoys hanging out with her kids (two and four-legged) in the forest behind her house and playing table-top games with her husband after the kids are in bed.</td>
  </tr>
  <tr>
    <td colspan="2"><h2><br />
    Scientific Advisor</h2></td>
  </tr>
  <tr>
    <td width="160" height="210" valign="top"><div align="left"><img src="images/about_aaronwirsing.jpg" width="150" height="200" align="top" /></div></td>
    <td height="210" valign="top">Hailing from South Carolina, <strong>Dr. Aaron Wirsing</strong> grew up fascinated by predators and their relationships with other species. Not much has changed; after earning an AB degree in Biology from Bowdoin College, he went on pursue Masters research focused on interactions between snowshoe hares and their predators at the University of Idaho, followed by a doctorate from Simon Fraser University (Vancouver, British Columbia, Canada), where he studied the impacts of tiger sharks on large prey species (sea cows and sea turtles) and seagrass ecosystem dynamics in Western Australia. A post-doc then took him to the Florida Everglades, where he worked with bull sharks and alligators. Today, two pictures of orcas chasing prey that Dr. Wirsing drew as a 5th grader hang in his office at the University of Washington, where he leads projects addressing the ecology and conservation of large carnivores such as gray wolves, cougars, and brown bears as PI of the Predator Ecology Lab. When not teaching and advising graduate students, Dr. Wirsing enjoys running and spending time with his wife, Brooke, and two cats, Ryan and Echo (pictured). His lab website is <a href="https://www.predatorecology.com/" target="_blank">predatorecology.com</a>.<br />
    <br />
    <br />
    <br /></td>
  </tr>
  <tr>
    <td colspan="2"><h2><br />
    Technology</h2></td>
  </tr>
  <tr>
    <td width="160" height="210" valign="top"><img src="images/about_wildbook.jpg" width="150" height="200" align="top" /></td>
    <td height="210" valign="top"><strong>Wildbook®</strong> is an open source software framework to support collaborative mark-recapture, molecular ecology, and social ecology studies, especially where citizen science and artificial intelligence can help scale up projects. It is developed by the non-profit Wild Me (PI Jason Holmberg) and research partners at the University of Illinois-Chicago (PI Tanya Berger-Wolf), Rensselaer Polytechnic Institute (PI Charles V. Stewart), and Princeton University (PI Daniel Rubenstein). <a href="https://www.wildbook.org/doku.php" target="_blank">Wildbook</a> provides a technical foundation (database, APIs, computer vision, etc.) for wildlife research projects that are tracking individual animals in a wildlife population using natural markings, genetic identifiers, or vocalizations, engaging citizen scientists and/or using social media to collect sighting information, looking to build a collaborative, distributed research network for a migratory and/or global species, and looking to develop a new animal biometrics solution (e.g., pattern matching from photos) for one or more species.<br />
    <br />
    <br />
    <br /></td>
  </tr>
  <tr>
    <td width="160" height="210" valign="top"><div align="left"><img src="images/about_jonvanoast.jpg" width="150" height="200" align="top" /></div></td>
    <td height="210" valign="top"><strong>Jon Van Oast</strong> is the technical lead for Kitizen Science. He is senior engineer at <a href="https://www.wildme.org/" target="_blank">Wild Me</a>, where he helps maintain and develop <a href="https://www.wildbook.org/doku.php" target="_blank">Wildbook</a>. He helps maintain <a href="https://giraffespotter.org/" target="_blank">GiraffeSpotter - Wildbook for Giraffe</a>  as well as providing support for many other species. Jon has been developing online collaborative software for over twenty years, with a strong interest in open source software/hardware, open data, citizen science, and conservation. He likes dogs, but decidedly leans cat.</td>
  </tr>
</table>

</div>

<jsp:include page="footer.jsp" flush="true" />

