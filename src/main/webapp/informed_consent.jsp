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

        request.setAttribute("pageTitle", "Kitizen Science &gt; Informed Consent");
%>

<jsp:include page="header.jsp" flush="true" />

<div class="container maincontent">

<h2>
Informed Consent for Study of Kitizen Science Volunteers
</h2>

<h3>Testing the Photo Processing Workflow of an Online Citizen Science Project About Cats</h3>

<p>
<b>Protocol Number: KitizenScience_v1.0
<br/>
	Principal Investigator: Sabrina Aeluro, Director of Kitizen Science
<br>
	Contact: kitizenscience@gmail.com
<br>
<br>
	This informed consent page covers your agreement to be not just a volunteer, but also a subject of our research.  Kitizen Science and this study is open to all people over the age of 18.
</b>
</p>

<h3>
	Purpose of this study
</h3>

<p>
	We collect survey and demographic data so we can understand our volunteers and the experience you bring to Kitizen Science, and so we can compare our volunteer demographics with those of other citizen science projects.  We also track volunteer activity on our website and app, including the total amount of time you spend logged in as a volunteer, the number of cat photo submissions you upload or process, and the route and distance you walk if you volunteer to use our app to conduct cat surveys.
</p>

<h3>
How we use this data
</h3>

<p>
This data will not be published in a way that makes it identifiable to you as an individual but will be published and shared in discussing our volunteers and how they participate in Kitizen Science.  This includes reporting the average amount of time volunteers spend on Kitizen Science per month, the gender breakdown of our volunteers, and other metrics of engagement and activity.  We also include the routes and distances traveled by app users in the statistical analysis of cat populations.
</p>

<h3>
Risks and benefits
</h3>

<p>
	Our survey questions are designed to be minimally intrusive, inoffensive, and are not intended to cause stress or place you at risk.
<br>
<br>
	Although this study may not benefit you directly and there is no compensation provided, we appreciate your efforts to support Kitizen Science.
<br>
<br>
	The study does not require the collection of any potentially personally identifying information apart from an email address.  Your email address is confidential and will not be published as part of this research.  While efforts are taken to ensure the privacy and security of your responses, in the event of a data breach, your survey answers and other data could be linked to your email address.  Only the researchers involved in this study and the people overseeing the study including IntegReview IRB will have access to your study records.
</p>

<h3>
	Other information
</h3>

<p>
	Please ask questions to decide if you want to be in the study.  You may stop participating at any time and you can do so without penalty or loss of benefits to which you are otherwise entitled.  You will not lose any of your legal rights by agreeing to participate in this study.
</p>

<h3>
Research oversight
</h3>

<p>
If you do not want to contact the investigator or study staff, if you have concerns or complaints about the research, or to ask questions about your rights as a study subject you may contact IntegReview.  IntegReview’s policy indicates that all concerns/complaints are to be submitted in writing for review at a convened IRB meeting to:
<br>
<br>
	Email address:
<br>
<br>
	integreview@integreview.com
<br>
<br>
	Mailing address:
<br>
<br>
	Chairperson
<br>
IntegReview IRB
<br>
3815 S. Capital of Texas Highway
<br>
Suite 320
<br>
Austin, Texas 78704
<br>
<br>
	IntegReview has reviewed the information in this consent form.  This does not mean IntegReview has approved your being in the study.  You must consider the information in this consent form for yourself and decide if you want to be in this study.
</p>

<h3>
Subject's statement
</h3>

<p>
	Proceeding with this survey and Kitizen Science implies my consent to participate in this research.  I may download or save a copy of this page to keep for my records.
<br>
<br>
	I consent to participate in this study.
</p>

</div>


<jsp:include page="footer.jsp" flush="true" />

