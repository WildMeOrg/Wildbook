<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.CommonConfiguration,java.util.Properties, org.ecocean.servlet.ServletUtilities,
org.ecocean.AccessControl" %>
<%

  //setup our Properties object to hold all properties
  
  String langCode = ServletUtilities.getLanguageCode(request);

  //set up the file input stream
  //FileInputStream propsInputStream=new FileInputStream(new File((new File(".")).getCanonicalPath()+"/webapps/ROOT/WEB-INF/classes/bundles/"+langCode+"/submit.properties"));
  //props.load(propsInputStream);
  
  

  
  boolean loggedIn = !AccessControl.isAnonymous(request);
  String context=ServletUtilities.getContext(request);
        request.setAttribute("pageTitle", "Kitizen Science &gt; Monitoring the Impact of Spay/Neuter Programs on Free-roaming Cat Populations");

%>
<jsp:include page="header.jsp" flush="true"/>
<style>
.big-button {
    background-color: #9dc327;
    border-radius: 8px;
    padding: 10px 20px;
    color: white !important;
    font-size: 1.2em;
}
</style>

<div class="container maincontent">

<h2>Every spay/neuter project is a potential experiment</h2>
<p>Kitizen Science wants to build a better evidence base about how sterilization programs affect the size of free-roaming cat populations in urban and suburban North America with the applied goal of learning the most effective ways of reducing free-roaming cat overpopulation using cat-friendly methods. </p>
<p>Pairing rigorous population ecology research methods with volunteer-driven data collection and processing, we are focusing on the following important questions: Are sterilization programs effective at reducing free-roaming cat populations? How do levels of sterilization coverage vary in their impact on free-roaming cat populations? How long after implementing a sterilization program can we expect to see a decrease in cat numbers? How do the answers to these questions change in different contexts?  Read <a href="mission.jsp">more about our mission</a>.</p>
<p><img src="images/index_catgraph_notail.jpg" width="495" height="400" hspace="10" vspace="10" align="right" />There is broad public support for using spay/neuter programs instead of culling to manage free-roaming cat populations, and there is peer-reviewed  research that these spay/neuter efforts can correlate with decreases in shelter intake, lower shelter euthanasia of cats, and reduced cat nuisance complaint calls.  However, there is  little  evidence as to whether spay/neuter programs control or reduce the  number of cats on the outdoor landscape, and there is a need for better tools for monitoring this directly. Learn about <a href="why.jsp">why our work is needed</a> and   the current <a href="spayneuter.jsp">research into spay/neuter and TNR</a>.</p>
<p>Kitizen Science will not be operating clinics ourselves, but partnering with those who do in order to conduct impact assessments.  Our community of volunteers will be both local and global thanks to the internet, so you can participate whether or not you live near a study site.  See <a href="how.jsp">more about how our program works</a>. </p>
<p>Kitizen Science is currently in its beta stage.  We will be conducting three validation studies over the next year, and we are seeking volunteers.  The first validation study - which involves looking at two photos of cats and deciding if they are the same cat - is open from now until the end of October 6, 2019.  This should be a fun and simple study, and volunteers can participate as much or as little as your schedule allows.</p>

<div class="margin-top: 20px;">&nbsp;</div>

<% if (loggedIn) { %>
<p align="center"><a class="big-button" href="compare.jsp">Proceed to Study</a></p>
<% } else { //is logged in %>
<p style="display: none;" align="center"><a class="big-button" href="register.jsp">Participate in Kitizen Science!</a></p>
<% } %>


</div>


<jsp:include page="footer.jsp" flush="true" />
