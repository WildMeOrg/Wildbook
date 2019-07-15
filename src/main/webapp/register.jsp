
<%@ page contentType="text/html; charset=utf-8" 
		language="java"
        import="org.ecocean.servlet.ServletUtilities,org.ecocean.*, java.util.Properties" %>


<%

String context = ServletUtilities.getContext(request);

  //setup our Properties object to hold all properties
  //String langCode = "en";
  String langCode=ServletUtilities.getLanguageCode(request);

    String modeString = request.getParameter("mode");

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
<h2>About Kitizen Science</h2>

<p>
Kitizen Science is a project
......
</p>


<h2>Submission Rules</h2>


<p>
Photos must be free-roaming outdoor cats (feral or friendly).  Owned or cared-for outdoor cats are fine, so long as they are outdoor cats rather than indoor-only pets.  Try to submit several photos of the cat from different angles so we can see their full coat pattern.  Photos can contain more than one cat, but if possible, take photos with only one cat in each image.  Do not photograph a cat more than once per day (more than one encounter), but you can - and should -  submit the same cat on multiple days.  We're trying to build a history of cats and when they are seen, so don't feel like you're not participating well if you submit the one cat who lives in your yard every day.  We want it!
</p>


<h2>How it Works</h2>

<p>
Photo submissions are made online by registered volunteers at kitizenscience.org using the <i>"Report Encounter"</i> link.  Once logged in, the "Report Encounter" page will allow you to upload photos, click on a map where the photo was taken, and add information about the cat.  If using a mobile phone to take your cat photos, please turn on location services/GPS photo information so that we can get more data from your photos.  Mobile devices are the easiest way to shoot and submit photos, since you can do everything from one place, but you are welcome to use any type of digital camera and computer.  This website should work in all browsers and on mobile devices, but please let us know if you have any issues.
</P>


<h2>How to Submit Photo Encounters</h2>


<p>
After registering for the website, log into kitizenscience.org, and then click on Report an Encounter in the top menu bar.  This takes you to the encounter submission page.  Here, you upload photos, select the date and time when they were taken, and click on a map with the location.  Zoom in as close as possible when marking the location on the map.  We would also like you to add some basic information about the cat in the photo to help us organize your photos.  Under the section About the Animal, you can add the cat's sex (if known), whether it's alive or dead, any names you or others use for the cat, the cat's approximate age, whether the cat has either ear tipped (cut) to indicate that it has been spayed/neutered, and the the cat's primary 1-3 colors.  (You don't need to list every color on a cat, so a brown tabby with a small orange or streak would just be listed here as a brown cat.)  If there are multiple cats in your photo submission, just pick one and fill in the data for that cat.
</p>

<p>
These instructions can be viewed again later by clicking on the Participate link in the top menu, and selecting <i>"How to Submit Photos."</i>
</p>

<p>
    <form method="post">
    <input type="submit" value="Register to use Kitizen Science" />
    <input type="hidden" name="fromMode" value="-1" />
    </form>

</div>

<% }
if (mode == 0) {
%>

<div id="consent-section">
<h2>Informed Consent to Participate</h2>


<p>(a bunch of stuff about consent)</p>


<h2>Research Volunteer Agreement</h2>

<p>Do you agree?</p>

<div>
<form method="post">
    <input type="submit" value="Agree" />
    <input type="hidden" name="fromMode" value="0" />
</form>
<form method="post">
    <input type="submit" value="Disagree" />
    <input type="hidden" name="fromMode" value="-2" />
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
	<label for="fullname">Full name:</label>
	<input type="text" id="fullname" name="fullname" maxlength="50" />
</div>
<div>
	<label for="email">Email address:</label>
	<input type="email" id="email" name="email" maxlength="50" />
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
<input type="checkbox" /> Agree to terms?  etc?
</div>


<div id="myCaptcha" style="width: 50%;margin: 0 auto; "></div>
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

