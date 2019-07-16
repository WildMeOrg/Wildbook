<%@ page contentType="text/html; charset=utf-8" 
		language="java"
        import="org.ecocean.servlet.ServletUtilities,org.ecocean.*, java.util.Properties" %>
<style>
label {
    font-size: 0.9em;
    width: 12em;
}
</style>
<%

String context = ServletUtilities.getContext(request);

  //setup our Properties object to hold all properties
  //String langCode = "en";
  String langCode=ServletUtilities.getLanguageCode(request);

    String modeString = request.getParameter("mode");
    boolean instrOnly = Util.requestParameterSet(request.getParameter("instructions"));

//set up the file input stream
  Properties props = new Properties();
  //props.load(getClass().getResourceAsStream("/bundles/" + langCode + "/login.properties"));
  props = ShepherdProperties.getProperties("login.properties", langCode,context);

    int mode = -1;
    try {
        mode = Integer.parseInt(modeString);
    } catch (NumberFormatException nfe) {};


    int fromMode = -2;
    try {
        fromMode = Integer.parseInt(request.getParameter("fromMode"));
    } catch (NumberFormatException nfe) {};

    if (fromMode == -1) {
        mode = 0;

    } else if (fromMode == 0) {
        mode = 1;

    } else if (fromMode == 1) {
        mode = 2;

    } else if (fromMode == 2) {
        mode = 3;
    }

    if (instrOnly) mode = 3;

    //////session.setAttribute("error", "<b>FAKE</b> error fromMode=" + fromMode);
%>



  <!-- Make sure window is not in a frame -->


<jsp:include page="header.jsp" flush="true"/>

<div class="container maincontent">

              <h1 class="intro">Participating in Kitizen Science</h1>

              <p align="left">
		
<div style="padding: 10px;" class="error">
<%
if (session.getAttribute("error") != null) {
	out.println("<p class=\"error\">" + session.getAttribute("error") + "</p>");
	session.removeAttribute("error");
}
%>
</div>
              
<% if (mode < 0) { %>

<div class="explanation-section">

<p>
Our first phase of validation tests are running from now until October 29, 2019.  This validation test is looking at how good humans are at by-eye photo identifications of cats taken with smart phones.  You can read the
<a href="register.jsp?instructions">instructions page</a>
first to see more about what this trial involves.
</p>

<p>
    <form method="post">
    <input type="submit" value="Register to Participate" />
    <input type="hidden" name="fromMode" value="-1" />
    </form>

</div>

<% }
if (mode == 0) {
%>

<div id="consent-section">
<h2>
UNIVERSITY OF WASHINGTON -
CONSENT FORM
</h2>

<h3>Testing Volunteers' Ability to Identify Individual Cats from Photos</h3>

<p>
<b>Researcher: Sabrina Aeluro, graduate student at the University of Washington<br />
Study email: kitizenscience@gmail.com</b>
</p>

<h3>
Researcher's statement and purpose of study
</h3>

<p>
The purpose of this study is to test volunteers' abilities to make correct photo identifications of free-roaming cats using an online citizen science platform.  The cat photos in this study are of outdoor cats in their normal environment, and no cats were harmed in the collection of these photos.  This study is open to all people over the age of 18 who are interested in cats.
</p>

<p>
The purpose of this consent form is to give you the information you will need to help you decide whether to be in the study or not.  Please read the form carefully.  You may ask questions about the purpose of the research, what we would ask you to do, the possible risks and benefits, your rights as a volunteer, and anything else about the research or this form that is not clear.  When we have answered all your questions, you can decide if you want to be in the study or not.  This process is called “informed consent.”  You may save a copy of this form for your records.
</p>

<h3>
Study procedures
</h3>

<p>
After registering for the study website, this study starts with a short survey about volunteers' backgrounds and personal demographics, and then participants will be presented with photo matching trials.  Once a trial has started, volunteers will be shown two photos and asked to select whether the same cat is pictured in both photos.  Volunteers can do as many or as few matching trials as they like.
</p>

<h3>
Risks, stress, or discomfort
</h3>

<p>
This study is designed with the aim to be minimally intrusive, inoffensive, and is not intended to cause stress or place subjects at risk.
</p>

<h3>
Alternatives to taking part in this study
</h3>

<p>
You have the option to not take part in this study.
</p>

<h3>
Benefits of the study
</h3>

<p>
While there is no individual benefit or compensation for participating in this study, your answers will help validate the methods of Kitizen Science, a new citizen science program for monitoring the impacts of spay/neuter programs on free-roaming cat populations.
</p>

<h3>
Confidentiality of research information
</h3>

<p>
The study does not require the collection of any personally identifying information apart from an email address.  Your email address is confidential and will not be published as part of this research.  While efforts are taken to ensure the privacy and security of your responses, in the event of a data breach, your survey answers and photo matching data could be linked to you email address.
</p>

<p>
Government or university staff sometimes review studies such as this one to make sure they are being done safely and legally.  If a review of this study takes place, your responses may be examined.  The reviewers will protect your privacy.  The study records will not be used to put you at legal risk of harm.
</p>

<h3>
Other information
</h3>

<p>
You may refuse to participate and you are free to withdraw from this study at any time without penalty or loss of benefits to which you are otherwise entitled.
</p>

<h3>
Research-related injury
</h3>

<p>
If you think you have been harmed from being in this research, contact Sabrina Aeluro via the study email address: kitizenscience@gmail.com.  The UW does not normally provide compensation for harm except through its discretionary program for medical injury.  However, the law may allow you to seek other compensation if the harm is the fault of the researchers.  You do not waive any right to seek payment by signing this consent form.
</p>

<h3>
Subject's statement 
</h3>

<p>
This study has been explained to me.  I volunteer to take part in this research.  I have had a chance to ask questions.  If I have questions later about the research, or if I have been harmed by participating in this study, I can contact the researcher listed on this consent form.  If I have questions about my rights as a research subject, I can call the University of Washington Human Subjects Division at 206-543-0098 or call collect at 206-221-5940.
</p>

<p>
I consent to participate in this study.
</p>


<div>
<form method="post">
    <input type="submit" value="Yes" />
    <input type="hidden" name="fromMode" value="0" />

    <input type="button" value="No" onClick="window.location.href='./';" />
</form>
</div>

</div>


<% }
if (mode == 1) {
    Properties recaptchaProps = ShepherdProperties.getProperties("recaptcha.properties", "", context);
%>

<div id="register-section">
<h2>Register an account</h2>


<form method="post">
<input type="hidden" name="fromMode" value="1" />

<div>
	<label for="username">Username</label>
	<input type="text" id="username" name="username" maxlength="50" />
</div>
<div>
	<label for="email">Email address</label>
	<input type="email" id="email" name="email" class="ui-autocomplete-input" maxlength="50" />
</div>

<div>
    <label>Password</label>
    <input type="password" name="password1" />
</div>
<div>
    <label>Password (confirm)</label>
    <input type="password" name="password2" />
</div>

<div>
    <label for="agree-terms">Agree to terms (etc)?</label> <input id="agree-terms" name="agree-terms" type="checkbox" />
</div>


<div id="myCaptcha" style="margin-top: 20px;"></div>
<script>
    var captchaWidgetId;
    function onloadCallback() {
        captchaWidgetId = grecaptcha.render(
            'myCaptcha', {
                'sitekey' : '<%=recaptchaProps.getProperty("siteKey") %>',
                'theme' : 'light'
            }
        );
    }
</script>
<script src="https://www.google.com/recaptcha/api.js?render=explicit&onload=onloadCallback"></script>

<input type="submit" name="submit" value="Register" />

</form>


</div>
              
<% }
if (mode == 2) {
%>
<div id="survey-section">
<h2>Survey</h2>
<p>Please fill out this short survey.... </p>
<form method="post">
<input type="hidden" name="fromMode" value="2" />

<p>
.....
</p>

<input type="submit" value="Submit survey" />
</form>
</div>

<% }
if (mode == 3) {
%>
<div id="instructions">
<h2>Instructions!</h2>
<p>do some stuff.....
</p>
</div>

<% } %>
            </div>
            
          <jsp:include page="footer.jsp" flush="true"/>

