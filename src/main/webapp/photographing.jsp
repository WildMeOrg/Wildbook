<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
        <%@ page contentType="text/html; charset=utf-8" language="java" import="java.util.Properties, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*" %>
<%

//setup our Properties object to hold all properties
	Properties props=new Properties();
	String langCode="en";
	
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
<title><%=CommonConfiguration.getHTMLTitle() %></title>
<meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
<meta name="Description" content="<%=CommonConfiguration.getHTMLDescription() %>" />
<meta name="Keywords" content="<%=CommonConfiguration.getHTMLKeywords() %>" />
<meta name="Author" content="<%=CommonConfiguration.getHTMLAuthor() %>" />
<link href="<%=CommonConfiguration.getCSSURLLocation(request) %>" rel="stylesheet" type="text/css" />
<link rel="shortcut icon" href="<%=CommonConfiguration.getHTMLShortcutIcon() %>" />



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
		  <h2>Photographing a Manta</h2>
		</div>
			
			<p>Did you know that by photographing a manta ray you can directly contribute to a global effort to better understand and protect these amazing animals? 

		<p>
		
		<h3>Taking the perfect ID shot</h3>
		
		<p>Manta rays are predominantly encountered on SCUBA, however in some areas of the world snorkelers are also able to engage with these gentle giants. For this reason we focus on tips for taking ID shots whilst SCUBA diving.</p>
		
		<p>
		If you spot a manta ray on a dive and have the opportunity to take an ID picture please do. However please focus foremost on ensuring that you have a good encounter with this animal and that you are following the recommended code of conduct for the region.  
		</p>
		
		<p>When the opportunity arises, try to remain stationary allowing the manta ray to control the encounter. If you remain stationary, mantas will often approach you and may even pass overhead. Avoid rushing towards a manta as this may scare them away from you, ending the encounter. By positioning yourself below the manta during the encounter you are already set up to take the perfect ID shot. 
		</p>
		
		
		<table>
		
		<tr><td>
		<table>
		
		<tr>
		<td><img src="images/alfredi_standardized_id_area.jpg"/></td>
		</tr>
		<tr>
		<td align="center"><strong>Fig 1. Manta alfredi standardized ID area <br /></strong></td>
		
		</tr>
		
		</table>
		</td></tr>
		
		
		<tr><td>
		<table>
		<tr>
		<td><img src="images/birostris_standardized_id_area.jpg"/></td>
		</tr>
		<tr>
		<td align="center"><strong>Fig 2. Manta birostris standardized ID area</strong></td>
		</tr>
		
		</table>
		
		</td></tr>
		</table>
		
		<p>
		As the manta ray approaches get your camera ready. Try holding your breath if it looks like it might swim over you. Some mantas enjoy the tickle of the bubbles, but most don't and this could scare them away. As the manta passes overhead try to get the entire underside in frame. If you have a wide-angle lens this may be easy, if you do not, please do not be concerned. For a positive ID we only need one image of the standardized area on the underside of the animal. If possible, take as many consecutive photos as you can (especially if there are remoras or other fish concealing the ID area).  This technique should ensure that you capture all of the information possible from the animal. When uploading photos it is important to try and sex each individual ray. It is also important to not if the animal had any deformities, distinguishing marks (e.g. abrasions from fishing nets, hooks or boat strikes), or scars (e.g. shark bites).
		</p>
		
				<table>
				<tr>
				<td>
				<img width="810px" height="*" src="images/good_vs_bad_id_shot.jpg" />
				</td>
				</tr>
				<tr>
				<td align="center">
				<strong>Fig 3. Good vs bad ID shots</strong>
				</td>
				</tr>
		</table>
		
		<a name="sex"><h3>Determining sex in your photo</h3></a>
		
		<p>Determining the sex of a manta is usually quick and easy. If you have taken a good ID shot of the standardized area, you will be able to ID the animal's sex from this photo. If not, check your other photos to see if you can see the pelvic fins. Pelvic fins are the two small fins located at the rear of the animal by the tail. 
		</p>
		
		
		<table>
		<tr>
		<td>
		<img width="810px" height="*" src="images/determining_sex.jpg" />
		</td>
		</tr>
		<tr>
		<td align="center">
		<strong>Fig 4. Determining sex</strong>
		</td>
		</tr>
		</table>
		
		
		<p>
		Female manta rays do not have any external reproductive organs so all that you will see in the image will be fins. Because there are no organs to judge by, determining the maturity status of a female ray can be difficult. If the individual was visibly pregnant or possessed reproductive scars (bite marks obtained during mating) on either or their wing tips then they can be classified as mature.  
		</p>
		
				<table>
				<tr>
				<td>
				<img width="810px" height="*" src="images/female_maturity.jpg" />
				</td>
				</tr>
				<tr>
				<td align="center">
				<strong>Fig 5. Sexual maturity in females</strong>
				</td>
				</tr>
		</table>
		<p>
		Male manta rays do have external reproductive organs, call claspers, that protrude off of these pelvic fins. Immature males will have small claspers and mature males will have large claspers that protrude past the edge of the pelvic fins. 
		</p>
		
		<p>
		The preparation of your pictures to upload is simple, yet critical to the identification process. If they are not prepared correctly it may result in a false positive or misidentification. Ideally when editing photos, you may want to alter several things. Cropping the photo so that the manta is full frame will make viewing the natural spot pattern easier. When cropping, you may also have to simultaneously rotate the photo so that the body is centered within the frame.  Using photo editing programs like Photoshop or Picasa, you can also alter the contrast and color saturation to make patterns stand out.
		</p>
		
		<table>
						<tr>
						<td>
						<img width="810px" height="*" src="images/edit_quality.jpg" />
						</td>
						</tr>
						<tr>
						<td align="center">
						<strong>Fig 6. Manta image edit quality</strong>
						</td>
						</tr>
		</table>
		
		<p>
		Once you have edited your manta photos, or frame grabs from video, you are ready do <a href="http://www.mantamatcher.org/submit.jsp">directly upload your photos to the manta matchers website</a>. Every contribution is extremely important, so even if you only have a single photo to upload, you're helping to enhance our knowledge about these mysterious animals. 
		</p>
		<p>By supplying us with your email address in your encounter report, the manta matcher database will automatically notify you if your individual manta is reported or re-sighted again. 
		</p>
		
		<p>Thank you for contributing to this global effort to protect Manta Rays!
		</p>
		
		
		
			<p>&nbsp;</p>
	</div>
	<!-- end maintext -->

  </div><!-- end maincol -->

<jsp:include page="footer.jsp" flush="true" />
</div><!-- end page -->
</div><!--end wrapper -->
</body>
</html>
