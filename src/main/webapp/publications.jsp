<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
        <%@ page contentType="text/html; charset=utf-8" language="java" import="org.ecocean.servlet.ServletUtilities,java.util.Properties, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*" %>
<%

//setup our Properties object to hold all properties
	Properties props=new Properties();
	String langCode="en";
	
	String context="context0";
	context=ServletUtilities.getContext(request);

	
	//check what language is requested
	if(request.getParameter("langCode")!=null){
		if(request.getParameter("langCode").equals("fr")) {langCode="fr";}
		if(request.getParameter("langCode").equals("de")) {langCode="de";}
		if(request.getParameter("langCode").equals("es")) {langCode="es";}
	}
	
	//set up the file input stream
	//FileInputStream propsInputStream=new FileInputStream(new File((new File(".")).getCanonicalPath()+"/webapps/ROOT/WEB-INF/classes/bundles/"+langCode+"/submit.properties"));
	props.load(getClass().getResourceAsStream("/bundles/"+langCode+"/submit.properties"));
	
	
	//load our variables for the submit page
	String title=props.getProperty("submit_title");
	String submit_maintext=props.getProperty("submit_maintext");
	String submit_reportit=props.getProperty("reportit");
	String submit_language=props.getProperty("language");
	String what_do=props.getProperty("what_do");
	String read_overview=props.getProperty("read_overview");
	String see_all_encounters=props.getProperty("see_all_encounters");
	String see_all_sharks=props.getProperty("see_all_sharks");
	String report_encounter=props.getProperty("report_encounter");
	String log_in=props.getProperty("log_in");
	String contact_us=props.getProperty("contact_us");
	String search=props.getProperty("search");
	String encounter=props.getProperty("encounter");
	String shark=props.getProperty("shark");
	String join_the_dots=props.getProperty("join_the_dots");
	String menu=props.getProperty("menu");
	String last_sightings=props.getProperty("last_sightings");
	String more=props.getProperty("more");
	String ws_info=props.getProperty("ws_info");
	String about=props.getProperty("about");
	String contributors=props.getProperty("contributors");
	String forum=props.getProperty("forum");
	String blog=props.getProperty("blog");
	String area=props.getProperty("area");
	String match=props.getProperty("match");
	
	//link path to submit page with appropriate language
	String submitPath="submit.jsp?langCode="+langCode;
	
%>

<html>
<head>
<title><%=CommonConfiguration.getHTMLTitle(context) %></title>
<meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
<meta name="Description" content="<%=CommonConfiguration.getHTMLDescription(context) %>" />
<meta name="Keywords" content="<%=CommonConfiguration.getHTMLKeywords(context) %>" />
<meta name="Author" content="<%=CommonConfiguration.getHTMLAuthor(context) %>" />
<link href="<%=CommonConfiguration.getCSSURLLocation(request,context) %>" rel="stylesheet" type="text/css" />
<link rel="shortcut icon" href="<%=CommonConfiguration.getHTMLShortcutIcon(context) %>" />


</head>

<body>
<div id="wrapper">
<div id="page">
<jsp:include page="header.jsp" flush="true">
	<jsp:param name="isResearcher" value="<%=request.isUserInRole(\"researcher\")%>"/>
	<jsp:param name="isManager" value="<%=request.isUserInRole(\"manager\")%>"/>
	<jsp:param name="isReviewer" value="<%=request.isUserInRole(\"reviewer\")%>"/>
	<jsp:param name="isAdmin" value="<%=request.isUserInRole(\"admin\")%>"/>
</jsp:include>	
<div id="main">
	
	<div id="maincol-wide-solo">
		
		<div id="maintext">
		  <h1 class="intro">Publications</h1>
		  <ul>
		  <li><a href="#acknowl">Acknowledging Wildbook for Whale Sharks in a publication</a></li>
		  <li><a href="#scipubs">Scientific publications</a></li>
		  </ul>
		</div>
		
		<p>&nbsp;</p>
	
		<a name="acknowl"></a><strong>Acknowledging Wildbook for Whale Sharks in a publication</strong>
		<p><em>If use of the Wildbook for Whale Sharks library made a significant contribution to a research project, please make the following acknowledgement in any resulting publication: </em></p>
		<p>This research has made use of data and software tools provided by <em>Wildbook for Whale Sharks</em>, an online mark-recapture database operated by the non-profit scientific organization <em>Wild Me</em> with support from public donations and the Qatar Whale Shark Research Project.</p>
		
		<p>&nbsp;</p>
	
		<a name="scipubs"></a><strong>Scientific publications</strong>
		<p><em>The following reports and publications have either directly used data from whaleshark.org or contributed to its ultimate development and launch in 2003.</em></p>
		
		<p>McKinney J, Hoffmayer ER, Holmberg J, Graham R, de la Parra R et al. (2013) Regional connectivity of whale sharks demonstrated using photo-identification - Western Atlantic, 1999 - 2013. PeerJ PrePrints 1:e98v1
		<br />
		<a href="http://dx.doi.org/10.7287/peerj.preprints.98v1">Web link</a>
		</p>
		
		<p>Bonner SJ &amp; Holmberg, J (2013), Mark-Recapture with Multiple, Non-Invasive Marks. Biometrics. doi: 10.1111/biom.12045<br /><a href="http://onlinelibrary.wiley.com/doi/10.1111/biom.12045/abstract">Web link</a></p>
		
		<p>Hueter RE, Tyminski JP, de la Parra R (2013) Horizontal Movements, Migration Patterns, and Population Structure of Whale Sharks in the Gulf of Mexico and Northwestern Caribbean Sea. PLoS ONE 8(8): e71883. doi:10.1371/journal.pone.0071883
		<br /><a href="http://www.plosone.org/article/info%3Adoi%2F10.1371%2Fjournal.pone.0071883">Web link</a>
		</p>
		
		<p>Fox S, Foisy I, De La Parra Venegas R, Galvan Pastoriza BE, Graham RT, Hoffmayer ER, Holmberg J, Pierce SJ. (2013) Population structure and residency of whale sharks Rhincodon typus at Utila, Bay Islands, Honduras. Journal of Fish Biology
Volume 83, Issue 3, pages 574-587, September 2013 <br /><a href="http://onlinelibrary.wiley.com/doi/10.1111/jfb.12195/abstract">Web link</a></p>
		
		<p> Robinson DP, Jaidah MY, Jabado RW, Lee-Brooks K, Nour El-Din NM, et al. (2013) Whale Sharks, Rhincodon typus, Aggregate around Offshore Platforms in Qatari Waters of the Arabian Gulf to Feed on Fish Spawn. PLoS ONE 8(3): e58255. doi:10.1371/journal.pone.0058255
		<br /><a href="http://www.plosone.org/article/info%3Adoi%2F10.1371%2Fjournal.pone.0058255">Web link</a>
		</p>
		
		<p>Davies, Tim K., Stevens, Guy, Meekan, Mark G., Struve, Juliane, and Rowcliffe, J. Marcus (2012) Can citizen science monitor whale-shark aggregations? Investigating bias in mark-recapture modelling using identification photographs sourced from the public. Wildlife Research 39, 696-704.<br /><a href="http://www.publish.csiro.au/paper/WR12092">Web link</a></p>
		
		<p>Marshall AD &amp; SJ Pierce (2012) The use and abuse of photographic identification in sharks and rays. Journal of Fish Biology 80: 1361-1379</p>
	
		<p>Catlin J, Jones T, Norman B &amp; Wood D. Consolidation in a wildlife tourism industry: the changing impact of whale shark tourist expenditure in the Ningaloo Coast region. <em>International Journal of Tourism Research</em>,
Volume 12, Issue 2, pages 134-148, March/April 2010. </p>
		<p>Catlin J, Jones R, Jones T, Norman B and Wood D (2010). Discovering Wildlife Tourism: A Whale Shark Tourism Case Study. <em>Current Issues in Tourism</em>,

Volume 13, Issue 4.</p>
		<p>Jones T, Wood D, Catlin J &amp; Norman B (2009). Expenditure and ecotourism: predictors of expenditure for whale shark tour participants. <em>Journal of Ecotourism</em> Volume 8, Issue 1: 32-50.</p>
		<p>Norman B (2009) ECOCEAN Best Practice Whale Shark Ecotourism UNEP MANUAL. Technical Report (United Nations Environment Program - Regional Seas) 7pp.<br />
	      <a href="ECOCEAN%20Best%20Practice%20Whale%20Shark%20Ecotourism%20UNEP%20MANUAL.pdf">Web link</a>.		</p>
		<p>Holmberg J &amp; Norman B (2009) ECOCEAN Whale Shark Photo-identification - UNEP MANUAL. Technical Report (United Nations Environment Program - Regional Seas) 69pp.<br />
	    <a href="ECOCEAN%20Whale%20Shark%20Photo-identification%20UNEP%20MANUAL.pdf">Web link</a>.		</p>
		<p>Holmberg J, Norman B &amp; Arzoumanian Z (2009) Estimating population size, structure, and residency time for whale sharks Rhincodon typus through collaborative photo-identification. <em>Endangered Species Research, </em> (7) 39-53.<br /> 
	      <a href="http://www.int-res.com/articles/esr2009/7/n007p039.pdf">Web link</a>. </p>
		<p>Jones T, Wood D, Catlin J &amp; Norman, B (2009) Expenditure and ecotourism: predictors of expenditure for whale shark tour participants. <em>Journal of Ecotourism</em>, (8) 32-50. <a href="http://www.informaworld.com/smpp/content%7Edb=all?content=10.1080/14724040802517922"><br />
	    Web link</a>. </p>
		<p>Gleiss AC, Norman B, Liebsch N, Francis C &amp; Wilson RP (2009) A new prospect for tagging large free-swimming sharks with motion-sensitive data-loggers. <em>Fisheries Research </em>97: 11-16. <a href="http://www.sciencedirect.com/science?_ob=ArticleURL&_udi=B6T6N-4V7MSDP-1&_user=10&_coverDate=04%2F30%2F2009&_rdoc=4&_fmt=high&_orig=browse&_srch=doc-info(%23toc%235035%232009%23999029998%23980057%23FLA%23display%23Volume)&_cdi=5035&_sort=d&_docanchor=&_ct=22&_acct=C000050221&_version=1&_urlVersion=0&_userid=10&md5=3102bda502b5793b48f2b8eb52773d1c"><br />
	    Web link</a>. </p>
		<p>Holmberg J, Norman B &amp; Arzoumanian Z (2008) Robust, comparable population metrics through collaborative photo-monitoring of whale sharks <em>Rhincodon typus </em>. <em>Ecological Applications </em> 18(1): 222-223. <a href="http://www.esajournals.org/doi/abs/10.1890/07-0315.1"><br />
	    Web link</a>. </p>
		<p>Norman B. &amp; Holmberg J (2007) A Cooperative Approach for Generating Robust Population Metrics for Whale Sharks <em>Rhincodon typus. </em> In: Maldini D, Meck Maher D, Troppoli D, Studer M, and Goebel J, editors. Translating Scientific Results into Conservation Actions: New Roles, Challenges and Solutions for 21st Century Scientists. Boston : Earthwatch Institute; 2007. <a href="Norman_Holmberg_Earthwatch_2007.pdf"><br />
	    Web link</a>. </p>
		<p>Norman B &amp; Stevens J (2007) Size and maturity status of the whale shark ( <em>Rhincodon typus </em>) at Ningaloo Reef in Western Australia. <em>Fisheries Research </em>Vol. 84, Issue 1, 1-136. Whale Sharks: Science, Conservation and Management - Proceedings of the First International Whale Shark Conference, First International Whale Shark Conference Australia 09-12 May 2005. T. R. Irvine and J. K. Keesing (Eds). <a href="http://www.sciencedirect.com/science?_ob=ArticleURL&_udi=B6T6N-4MC12HB-K&_user=10&_rdoc=1&_fmt=&_orig=search&_sort=d&view=c&_acct=C000050221&_version=1&_urlVersion=0&_userid=10&md5=03c783c026ce09b67f822ae3d7341a74"><br />
	    Web link</a>. </p>
		<p>Norman B &amp; Catlin J (2007) Economic importance of conserving whale sharks. Unpublished Report for the International Fund for Animal Welfare (IFAW), Sydney 18pp. <strong></strong></p>
		<p>Arzoumanian Z, Holmberg J &amp; Norman B (2005) An astronomical pattern-matching algorithm for computer-aided identification of whale sharks <em>Rhincodon typus </em>. <em>Journal of Applied Ecology </em> 42, 999-1011. <a href="http://www3.interscience.wiley.com/journal/118735310/abstract?CRETRY=1&SRETRY=0"><br />
	    Web link</a>. </p>
		<p>Norman BM (2005) Whale shark critical habitats and movement patterns within Australian waters. <em>Technical Report (DEH Natural Heritage Trust Project) </em>46pp. </p>
		<p>Norman BM (2004) Review of the current conservation concerns for the whale shark ( <em>Rhincodon typus </em>): A regional perspective. <em>Technical Report (NHT Coast &amp; Clean Seas Project No. 2127) </em>74pp. <em></em></p>
		<p>Norman B (2002) CITES Identification Manual: Whale Shark ( <em>Rhincodon typus </em> Smith 1829). Commonwealth of Australia. <a href="http://www.environment.gov.au/coasts/publications/whale-shark-id/index.html"><br />
	    Web link</a>. </p>
		<p>Norman BM, Newbound D &amp; Knott B (2000) A new species of Pandaridae (Copepoda), from the whale shark <em>Rhincodon typus </em> (Smith) . <em>Journal of Natural History </em> 34:3, 355-366. <a href="http://www.ingentaconnect.com/content/tandf/tnah/2000/00000034/00000003/art00004?token=0044129e186720297d76253e7b2a4a467a24425e3b6b6d3f4e4b252493777d450b13"><br />
	    Web link</a>. </p>
		<p>Norman BM (2000) In: <em>2000 IUCN Red List of Threatened Species. </em> IUCN, Gland, Switzerland and Cambridge, UK. Xviii+61 pp. (Book &amp; CD). </p>
		<p>Norman BM (1999) Aspects of the biology and ecotourism industry of the whale shark <em>Rhincodon typus </em>in north-western Australia. MPhil. Thesis (Murdoch University, Western Australia). <a href="http://wwwlib.murdoch.edu.au/adt/browse/view/adt-MU20071003.121017"><br />
	    Web link</a>. </p>
		<p>Gunn JS, Stevens JD, Davis TLO &amp; Norman BM (1999) Observations on the short-term movements and behaviour of whale sharks ( <em>Rhincodon typus </em>) at Ningaloo Reef, Western Australia. <em>Mar. Biol </em>. 135: 553-559. <a href="http://www.springerlink.com/content/68mmnfxa2vprhp7a/"><br />
	    Web link</a>. </p>
		

	</div>
	<!-- end maintext -->

  </div><!-- end maincol -->

<jsp:include page="footer.jsp" flush="true" />
</div>
<!-- end page -->
</div><!--end wrapper -->
</body>
</html>
