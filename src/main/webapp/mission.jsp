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
    
        request.setAttribute("pageTitle", "Kitizen Science &gt; Mission, Questions, Timeline");

%>

<jsp:include page="header.jsp" flush="true" />

<div class="container maincontent">

<style type="text/css">
<!--
.style1 {
	font-size: 12px;
	font-style: italic;
}
-->
</style>


<h1>Mission, Questions, and Timeline</h1>
<p><em>First, a short definition of terms used on this page and elsewhere on the website.  There are a lot of names for programs that sterilize unowned outdoor cats (trap-neuter-return, shelter-neuter-return, return to field, community cat programs) and return them to where they were trapped.  Here, we use &quot;spay/neuter program&quot; as our most all-inclusive and neutral term.  There are also many names for unowned outdoor cats (feral cat, community cat, stray cat, working cat, barn cat).  We have chosen to use &quot;free-roaming cat&quot; as our most all-inclusive and neutral term. </em></p>
<h2><img src="images/about_greycatsleeping.jpg" width="350" height="180" hspace="10" vspace="10" align="right" />Vision</h2>
<p>Kitizen Science wants to build a better evidence base about how sterilization programs affect the size of free-roaming cat populations in urban and suburban North America with the applied goal of learning the most effective ways of reducing free-roaming cat overpopulation using cat-friendly methods.</p>
<h2>Mission</h2>
<p>Monitoring the impact of spay/neuter programs on free-roaming cat populations.</p>
<h2>Research questions   </h2>
<p>Pairing rigorous population ecology research methods with volunteer-driven data collection and processing, we are focusing on the following important questions.   </p>
<table width="90%" border="0" align="center" cellpadding="0" cellspacing="0">
  <tr>
    <td><p><strong>Are sterilization programs effective at reducing free-roaming cat populations?</strong>  There is a collection of <a href="spayneuter.jsp">published research</a> showing that spay/neuter programs can reduce feline shelter intake, feline shelter euthanasia, and cat-related nuisance complaints, and some mathematical modeling studies that support spay/neuter as an effective management strategy, but there is little evidence (one way or the other) about whether spay/neuter programs can reduce free-roaming cat numbers.  Long term controlled experiments – the gold standard of research – have not been conducted on the impact of spay/neuter programs on free-roaming cat numbers in urban and suburban areas in North America.</p>
    <p><strong>How do levels of sterilization coverage vary in their impact on free-roaming cat populations?</strong>  We want to know the real world “tipping points” of sterilization coverage that must be reached to see a population decline in free-roaming cats.  Statistical simulations have made a range of estimates regarding the proportion of intact (unsterilized) cats that would need to be sterilized to see a reduction in cat population sizes, but the results of these models have not been field tested.   </p>
    <p><strong>How long after implementing a sterilization program can we expect to see a decrease in cat numbers?</strong>  Spay/neuter management of free-roaming cats allows for animals to live out their lives without reproducing.  Because there are widely varying claims about the life spans of free-roaming cats, it's hard to know without long term field studies how long it takes for cat populations to start declining from natural causes.   </p>
    <p><strong>How do the answers to these questions change in different contexts</strong>?  We want to learn whether sterilization is more effective at reducing free-roaming cat populations in hotter locations or cooler locations, urban habitats or suburban habitats, higher income or lower income neighborhoods, and more.  There are many variables that might influence the success of spay/neuter programs, which is why we aren't planning to conduct just a single study of a single location. </p></td>
  </tr>
</table>
<p><span class="style1"><img src="images/about_tuxedocat.jpg" width="174" height="300" hspace="10" vspace="10" align="left" /></span>Our unique approach keeps our overhead low and mobilizes citizen science volunteers, allowing us to gather more data at a reasonable cost so we can take a comparative approach to studying spay/neuter programs.  Kitizen Science is a long-term monitoring project that will take years before we start being able to see trends and draw conclusions, but this information has the potential to revolutionize the way spay/neuter programs are implemented and change the policy conversation around free-roaming cat management.  While we work towards shaping the bigger picture for tomorrow, we’re excited to be part of engaging the cat advocate community about the need for higher quality research.   </p>
<h2>Timeline</h2>
<p><strong>Beta period (2019 and 2020)</strong>: Test our approach in three phases of validation trials and make any needed changes to improve data collection and analysis.</p>
<p><strong>Next ten years (2021 onwards)</strong>: Launch monitoring projects based on controlled field experiments in multiple locations, with the intention to follow these experiments over a long period of time.  We want to partner with spay/neuter organizations to plan treatment areas (with targeted spay/neuter) and control areas (with no targeted spay/neuter programs) so we can compare how the population sizes of free-roaming cats change in each.</p>



  </div>

<jsp:include page="footer.jsp" flush="true" />

