<%@ page contentType="text/html; charset=utf-8" 
		language="java"
        import="org.ecocean.servlet.ServletUtilities,
java.io.IOException,
org.json.JSONObject,
org.json.JSONArray,
java.util.List,
java.util.ArrayList,
org.ecocean.servlet.ReCAPTCHA,
org.ecocean.*, java.util.Properties" %>
<%!

private static User registerUser(Shepherd myShepherd, String username, String email, String pw1, String pw2) throws java.io.IOException {
    if (!Util.stringExists(username)) throw new IOException("Invalid username format");
    if (!Util.isValidEmailAddress(email)) throw new IOException("Invalid email format");
    if (!Util.stringExists(pw1) || !Util.stringExists(pw2) || !pw1.equals(pw2)) throw new IOException("Password invalid or do not match");
    if (pw1.length() < 8) throw new IOException("Password is too short");
    username = username.toLowerCase();
    User exists = myShepherd.getUser(username);
    if ((exists != null) || username.equals("admin")) throw new IOException("Invalid username");
    String salt = Util.generateUUID();
    String hashPass = ServletUtilities.hashAndSaltPassword(pw1, salt);
    User user = new User(username, hashPass, salt);
    user.setEmailAddress(email);
    user.setNotes("<p data-time=\"" + System.currentTimeMillis() + "\">created via registration.</p>");
    Role role = new Role(username, "subject");
    role.setContext(myShepherd.getContext());
    myShepherd.getPM().makePersistent(role);
    return user;
}

%>
<style>
label {
    font-size: 0.9em;
    width: 30em;
    font-weight: bold !important;
}

.error {
    background-color: #FAA;
    padding: 3px 20px;
    border-radius: 10px;
}

#survey-section p {
    margin-top: 30px;
    padding: 10px;
    border-radius: 4px;
}

#survey-section p.required {
    background-color: #FAA;
}

#survey-section .top {
    vertical-align: top;
}
.big-button {
    background-color: #9dc327;
    border-radius: 8px;
    padding: 10px 20px;
    color: white !important;
    font-size: 1.2em;
}

</style>
<%

String context = ServletUtilities.getContext(request);
Shepherd myShepherd = new Shepherd(context);
myShepherd.setAction("register.jsp");
myShepherd.beginDBTransaction();
boolean rollback = true;

  //setup our Properties object to hold all properties
  //String langCode = "en";
  String langCode=ServletUtilities.getLanguageCode(request);
  boolean loggedIn = !AccessControl.isAnonymous(request);

    String modeString = request.getParameter("mode");
    boolean instrOnly = Util.requestParameterSet(request.getParameter("instructions"));

//set up the file input stream
  Properties props = new Properties();
  //props.load(getClass().getResourceAsStream("/bundles/" + langCode + "/login.properties"));
  props = ShepherdProperties.getProperties("login.properties", langCode,context);
    boolean reg_terms = false;
    String reg_username = "";
    String reg_email = "";
    String reg_password1 = "";
    String reg_password2 = "";

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
        reg_terms = !(request.getParameter("agree-terms") == null);
        reg_username = request.getParameter("username");
        reg_email = request.getParameter("email");
        reg_password1 = request.getParameter("password1");
        reg_password2 = request.getParameter("password2");

        User user = null;
        String errorMessage = "Unknown error message";
        boolean ok = ReCAPTCHA.captchaIsValid(request);
        if (!ok) errorMessage = "You may be a robot";
        if (ok && !reg_terms) {
            ok = false;
            errorMessage = "Please agree to terms and conditions";
        }
        if (ok) try {
            user = registerUser(myShepherd, reg_username, reg_email, reg_password1, reg_password2);
        } catch (java.io.IOException ex) {
            errorMessage = ex.getMessage();
        }

        if (user == null) {
            session.setAttribute("error", "<div class=\"error\">We have encountered an error creating your account: <b>" + errorMessage + "</b>");
            mode = 1;
        } else {
            myShepherd.getPM().makePersistent(user);
            rollback = false;
            System.out.println("[INFO] register.jsp created " + user);
            session.setAttribute("user", user);
            mode = 2;
        }

    } else if (fromMode == 2) {
        String[] fields = new String[]{"user_uuid", "cat_volunteer", "have_cats", "disability", "citsci", "age", "retired", "gender", "ethnicity", "education", "how_hear"};
        JSONObject resp = new JSONObject();
        List<String> errors = new ArrayList<String>();
        for (int i = 0 ; i < fields.length ; i++) {
            String val = request.getParameter(fields[i]);
//System.out.println(fields[i] + ": (" + val + ")");
            if ((val == null) || (val.trim().equals(""))) {
                errors.add("missing a value for " + fields[i]);
                continue;
            }
            if (fields[i].equals("have_cats") || fields[i].equals("ethnicity")) {
                JSONArray mult = new JSONArray(request.getParameterValues(fields[i]));
                resp.put(fields[i], mult);
            } else {
                resp.put(fields[i], val);
            }
        }
System.out.println("survey response: " + resp.toString());
        if (errors.size() > 0) {
            session.setAttribute("error", "<div class=\"error\">We have encountered an error in your survey response: <b>" + String.join(", ", errors) + "</b>");
            mode = 2;
        } else {
            String key = "survey_response_" + request.getParameter("user_uuid");
            SystemValue.set(myShepherd, key, resp);
            rollback = false;
            mode = 3;
        }
    }

    if (instrOnly) mode = 3;

%>



  <!-- Make sure window is not in a frame -->


<jsp:include page="header.jsp" flush="true"/>

<div class="container maincontent">

              <h1 class="intro">Participating in Kitizen Science</h1>

              <p align="left">
		
<div style="padding: 10px;">
<%
if (session.getAttribute("error") != null) {
	out.println(session.getAttribute("error"));
	session.removeAttribute("error");
}

%>
</div>
              
<% if (mode < 0) { %>

<div class="explanation-section">

<img src="images/participate_manatdesk.jpg" style="height: 300px; float: right; margin-top: -5em;" />
<p>
Our first phase of validation tests are running from now until October 6, 2019.
</p>

<p>
This validation test is looking at how good humans are at by-eye photo identifications of cats taken with smart phones.
</p>

<p>
You can read the
<a href="register.jsp?instructions">instructions page</a>
first to see more about what this trial involves.
</p>

<%  if (loggedIn) { %>
    <b>You are logged in already.  <a href="compare.jsp">Please proceed to study.</a></b>
<% } else { %>

<p>
    <form method="post">
    <input type="submit" value="Register to Participate" />
    <input type="hidden" name="fromMode" value="-1" />
    </form>
</p>

<% } %>

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

<script>
var regexUsername = new RegExp('^[a-z0-9]+$');
function checkAccount() {
    var msg = 'Please correct the following problems:';
    var ok = true;

    if (!regexUsername.test($('#username').val().trim().toLowerCase())) {
        msg += '\n- Username must be only letters and numbers';
        ok = false;
    }

    var p1 = $('[name="password1"]').val().trim();
    var p2 = $('[name="password2"]').val().trim();
    if ((p1 == '') || (p1 != p2)) {
        msg += '\n- Passwords do not match or are empty';
        ok = false;
    }
    if (p1.length < 8) {
        msg += '\n- Password should be at least 8 characters long';
        ok = false;
    }

    if (!wildbook.isValidEmailAddress($('#email').val())) {
        msg += '\n- Email address is invalid format';
        ok = false;
    }

    if (!$('#agree-terms').is(':checked')) {
        msg += '\n- Must agree to the Terms and Conditions';
        ok = false;
    }

/*
    if (xxx) {
        msg += '\n- Prove you are not a robot';
        ok = false;
    }
*/

    if (!ok) {
        alert(msg);
    }
    return ok;
}

</script>

<form method="post" onSubmit="return checkAccount();">
<input type="hidden" name="fromMode" value="1" />

<div>
	<label for="username">Username</label>
	<input type="text" value="<%=reg_username%>" id="username" name="username" maxlength="50" />
</div>
<div>
	<label for="email">Email address</label>
	<input type="email" value="<%=reg_email%>" id="email" name="email" class="ui-autocomplete-input" maxlength="50" />
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
    <label for="agree-terms">Agree to <a target="_new" href="terms.jsp">Terms and Conditions</a>?</label> <input id="agree-terms" name="agree-terms" type="checkbox" <%=(reg_terms ? "checked" : "")%> />
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

<script>
var surveyRequired = [
    'cat_volunteer',
    'disability',
    'have_cats',
    'citsci',
    'age',
    'retired',
    'ethnicity',
    'gender',
    'education',
    'how_hear'
];
function checkSurvey() {
    $('.required').removeClass('required');
    var ok = true;
    var msg = 'You must complete the form: ';
    for (var i = 0 ; i < surveyRequired.length ; i++) {
        var el = $('[name="' + surveyRequired[i] + '"]');
        var numChecked = $('[name="' + surveyRequired[i] + '"]:checked').length;
        if (surveyRequired[i] == 'age') {
            numChecked = parseInt(el.val());
        }
        if (surveyRequired[i] == 'how_hear') {
            numChecked = el.val().trim().length;
        }

        if (numChecked < 1) {
            var q = el.parent().first()[0].firstChild.nodeValue;  //ugh!
            el.parent().addClass('required');
            ok = false;
            msg += '\n- ' + q.trim();
        }
    }
    if (!ok) {
        window.scrollTo({top:250});
        alert(msg);
    }
    return ok;
}
</script>

<form onSubmit="return checkSurvey();" method="post">

<h2>Survey</h2>
<input type="hidden" name="user_uuid" value="<%
    User regu = (User)session.getAttribute("user");
    if (regu != null) out.print(regu.getUUID());
%>" />

<% if (regu != null) { %>
<p><b style="font-size: 1.3em;">Your user <u><%=regu.getUsername()%></u> has been created.</b></p>
<% } %>
<p>
We would like you to answer this short survey about yourself so we can understand our audience and your experience.  The demographic questions are included so that we can compare participants in Kitizen Science with other citizen science projects.  Specifically, we are interested in knowing whether the demographics of Kitizen Science are similar, or different, from other projects.
</p>

<p>
Are you currently involved in volunteering with cats in some way?
<br /><input id="cat_volunteer_yes" type="radio" value="Yes" name="cat_volunteer" /> <label for="cat_volunteer_yes">Yes</label>
<br /><input id="cat_volunteer_no" type="radio" value="No" name="cat_volunteer" /> <label for="cat_volunteer_no">No</label>
<br /><input id="cat_volunteer_past" type="radio" value="Not now, but in the past" name="cat_volunteer" /> <label for="cat_volunteer_past">Not now, but in the past</label>
</p>

<p>
Do you have a disability or personal limitation (such as being a parent/caregiver) that prevents you from volunteering with cats in a typical offline setting like a shelter?
<br /><input id="disability_no" type="radio" value="No" name="disability" /> <label for="disability_no">No</label>
<br /><input id="disability_yes" type="radio" value="Yes" name="disability" /> <label for="disability_yes">Yes</label>
<br /><input id="disability_sometimes" type="radio" value="Sometimes" name="disability" /> <label for="disability_sometimes">Sometimes</label>
</p>

<p>
Do you currently have a cat/cats in your care?
<br /><input id="have_cats_yes_pet" type="checkbox" value="Yes, a pet cat/cats" name="have_cats" /> <label for="have_cats_yes_pet">Yes, a pet cat/cats</label>
<br /><input id="have_cats_yes_feral" type="checkbox" value="Yes, I care for feral/free-roaming cats" name="have_cats" /> <label for="have_cats_yes_feral">Yes, I care for feral/free-roaming cats</label>
<br /><input id="have_cats_no" type="checkbox" value="No" name="have_cats" /> <label for="have_cats_no">No</label>
</p>

<p>
Have you ever participated in an online citizen science project doing image identification or classification?
<br /><input id="citsci_no" type="radio" value="No" name="citsci" /> <label for="citsci_no">No</label>
<br /><input id="citsci_yes" type="radio" value="Yes" name="citsci" /> <label for="citsci_yes">Yes</label>
</p>

<p>
What is your current age?
<select class="top" name="age">
    <option value="0">choose age</option>
<%
    for (int i = 18 ; i <= 100 ; i++) {
        out.println("<option>" + i + "</option>\n");
    }
%>
</select>
</p>

<p>
Are you retired?
<br /><input id="retired_no" type="radio" value="No" name="retired" /> <label for="retired_no">No</label>
<br /><input id="retired_yes" type="radio" value="Yes" name="retired" /> <label for="retired_yes">Yes</label>
</p>

<p>
What is your gender?
<br /><input id="gender_woman" type="radio" value="Woman" name="gender" /> <label for="gender_woman">Woman</label>
<br /><input id="gender_man" type="radio" value="Man" name="gender" /> <label for="gender_man">Man</label>
<br /><input id="gender_other" type="radio" value="Other" name="gender" /> <label for="gender_other">Non-binary/Other</label>
</p>

<p>
What is your race/ethnicity (select multiple if appropriate):
<br /><input id="ethnicity_aian" type="checkbox" value="American Indian or Alaska Native" name="ethnicity" /> <label for="ethnicity_aian">American Indian or Alaska Native</label>
<br /><input id="ethnicity_asian" type="checkbox" value="Asian" name="ethnicity" /> <label for="ethnicity_asian">Asian</label>
<br /><input id="ethnicity_baa" type="checkbox" value="Black or African American" name="ethnicity" /> <label for="ethnicity_baa">Black or African American</label>
<br /><input id="ethnicity_hisp" type="checkbox" value="Hispanic or Latino" name="ethnicity" /> <label for="ethnicity_hisp">Hispanic or Latino</label>
<br /><input id="ethnicity_me" type="checkbox" value="Middle Eastern" name="ethnicity" /> <label for="ethnicity_me">Middle Eastern</label>
<br /><input id="ethnicity_nhpi" type="checkbox" value="Native Hawaiian or Pacific Islander" name="ethnicity" /> <label for="ethnicity_nhpi">Native Hawaiian or Pacific Islander</label>
<br /><input id="ethnicity_white" type="checkbox" value="White" name="ethnicity" /> <label for="ethnicity_white">White</label>
</p>

<p>
Highest level of education:
<br /><input id="education_less" type="radio" value="Less than high school" name="education" /> <label for="education_less">Less than high school</label>
<br /><input id="education_hs" type="radio" value="High school" name="education" /> <label for="education_hs">High School</label>
<br /><input id="education_some" type="radio" value="some college" name="education" /> <label for="education_some">Technical school, Associate's degree, or some college</label>
<br /><input id="education_bach" type="radio" value="Bachelor's degree" name="education" /> <label for="education_bach">Bachelor's degree</label>
<br /><input id="education_grad" type="radio" value="Graduate/professional degree" name="education" /> <label for="education_grad">Graduate/professional degree</label>
</p>

<p>
How did you hear about Kitizen Science?
<textarea style="width: 30em; height: 5em;" class="top" name="how_hear"></textarea>
</p>

<input type="hidden" name="fromMode" value="2" />


<input type="submit" value="Submit survey" />

</form>
</div>


<% /*

Thanks!  Here's the survey page text for the UW-only registrants:

==

We would like you to answer this short survey about yourself so we can understand our audience and your experience.  The demographic questions are included so that we can compare participants in Kitizen Science with other citizen science projects.  Specifically, we are interested in knowing whether the demographics of Kitizen Science are similar, or different, from other projects.

Do you currently have a cat/cats in your care? [Checkboxes, can select multiple: Yes, a pet cat/cats; Yes, I care for feral/free-roaming cats, No]

Have you ever participated in an online citizen science project doing image identification or classification? [Drop-down: Yes; No]

Have you ever volunteered to do image identification or classification as part of research that is NOT online citizen science, such as viewing camera trap images for UW wildlife researchers? [Drop-down: Yes; No]

What is your current standing in school? [Dropdown: Freshman; Sophomore; Junior; Senior; Master's Student; Doctoral Student]

*/ %>

<% }
if (mode == 3) {
%>
<div id="instructions">

<h1>Instructions for Validation Study 1</h1><p>&nbsp;</p>
<p>The first of our three validation tests is about determining how good humans are at identifying cats from photos by making matches between one cat photo and a library of cat photos.  Similar tests have been conducted on many types of animals since it's important for researchers to understand and plan for human error rates. </p>
<p>This study is running from August 5 to October 6, 2019.  You can join at any time during that period.  You can complete all 50 matching trials, or only a few – either way, we value your time and energy and Kitizen Science always aims to make participation flexible.  We estimate each matching trial will each take 30-60 minutes to complete, depending on your personal pace. You can complete a maximum of 2 matching trials per day.  Previous studies of photo identification in animals have suggested that observer fatigue can cause people to become less successful when they have been staring at photos for extended periods of time. </p>
<h2>Enrollment process</h2><p>This study asks you to consent to participate as a research volunteer, register for the website, and answer some demographic questions.  We don't need to know your name, but you will need to register for the website with an email address. </p>
<h2>Rules</h2><p>We ask that you create only one login for Kitizen Science, and each login only has one person using it.  We are looking at how participant demographics might change ability to identify cats in photos, so we need one set of demographic information to be tied to one user account.  We also ask that you don't ask friends for help during your participation – we want to see how successful you are while working on your own. </p>
<h2>Trial instructions</h2><p>After logging in, you will be presented with matching trials.  Click to start a trial.  Once you complete a trial, you won't be presented with the same one again.   </p>
<p>Once you start a trial, you will have a &quot;Cat to Match&quot; photo on the left side of the screen and a &quot;Cat Library&quot; on the right side, with the options to click &quot;yes&quot; or &quot;no&quot; and zoom on either photo.   </p>
<p>During each individual trial, the Cat to Match photo will stay the same as you click through all of the Cat Library photos, and there may be one, multiple, or no matching cats in the library.  There is no &quot;I'm unsure&quot;  because we want you to make your best guess.  After clicking through all images in the Cat Library, the trial is complete, and you may do another trial or log off.  The Cat Library is the same in all trials and contains 120 photos. </p>
<p>Most photos are taken at a distance, so make sure to click photos to zoom all the way in.  (Clicking on an image zooms in on it, and you are zoomed in all the way once clicking no longer increases the image size.)  These test photos were obtained in the same way that our project will gather data in the real world: by taking photos of free-roaming cats as they are seen walking through a neighborhood while not trespassing on private property.  That means some cats are harder to see than others, and you won't always get to see good details.</p>
<h2>How to compare similar cats</h2><p>Even two similar-looking cats can be separated if you examine them closely.  Here are some details to look for when comparing two cats.</p>
<table width="80%" border="0" align="center" cellpadding="10" cellspacing="0">
  <tr>
    <td width="551" valign="top"><div align="center">Is the cat's ear tip removed (a marker  of sterilization)?  These can be hard to see at a distance or in cats with a small amount of  ear tip removed. Either ear may be tipped.</div></td>
    <td width="50" valign="top">&nbsp;</td>
    <td width="498" valign="top"><div align="center">Is the cat wearing a collar?  Keep in mind that collars can be added or removed, unlike fur coat patterns. Don't rely only on collars, but it can be one clue.</div></td>
  </tr>
  <tr>
    <td><div align="center"><img src="http://www.kitizenscience.org/images/whattolookfor_eartip.jpg" width="287" height="250" /></div></td>
    <td width="50">&nbsp;</td>
    <td><div align="center"><img src="http://www.kitizenscience.org/images/whattolookfor_collar.jpg" width="287" height="250" /></div></td>
  </tr>
  <tr>
    <td height="50">&nbsp;</td>
    <td width="50">&nbsp;</td>
    <td height="50">&nbsp;</td>
  </tr>
  <tr>
    <td valign="top"><div align="center">Faces provide a lot of clues.  Does the cat have a strong &quot;M&quot; pattern on his forehead?  What position and color are the stripes on his cheeks?  Is there a stripe on his nose?</div></td>
    <td width="50" valign="top">&nbsp;</td>
    <td valign="top"><div align="center">Tabby cats can look similar, but the arrangement of their stripes differs.  Some have wider or thinner stripes, darker or lighter stripes, running at different angles. </div></td>
  </tr>
  <tr>
    <td><div align="center"><img src="http://www.kitizenscience.org/images/whattolookfor_face.jpg" width="287" height="250" /></div></td>
    <td width="50">&nbsp;</td>
    <td><div align="center"><img src="http://www.kitizenscience.org/images/whattolookfor_flanks.jpg" width="287" height="250" /></div></td>
  </tr>
  <tr>
    <td height="50">&nbsp;</td>
    <td width="50">&nbsp;</td>
    <td height="50">&nbsp;</td>
  </tr>
  <tr>
    <td valign="top"><div align="center">Look at her legs.  Does she  have  &quot;mittens&quot;?  Dark  or large stripes?  If you're looking at photos of a left and right side of a cat, is a distinctive mark on the same side of the cat's body?</div></td>
    <td width="50" valign="top">&nbsp;</td>
    <td valign="top"><div align="center">Tails can also be different, and some cats have shorter or kinked tails. (Sometimes cats are sitting on their tails in a photo, so you can't see anything.)</div></td>
  </tr>
  <tr>
    <td><div align="center"><img src="http://www.kitizenscience.org/images/whattolookfor_frontlegs.jpg" width="287" height="250" /></div></td>
    <td width="50">&nbsp;</td>
    <td><div align="center"><img src="http://www.kitizenscience.org/images/whattolookfor_tail.jpg" width="238" height="250" /></div></td>
  </tr>
  <tr>
    <td height="50">&nbsp;</td>
    <td width="50">&nbsp;</td>
    <td height="50">&nbsp;</td>
  </tr>
  <tr>
    <td valign="top"><div align="center">How long is the cat's fur? Long, medium, or short fur?  (Sometimes  cats have tangled mats of fur shaved during the spay/neuter process, but bald spots don't stay long.)</div></td>
    <td width="50" valign="top">&nbsp;</td>
    <td valign="top"><div align="center">Remember that not every cat photo is going to be a great one, and sometimes you won't have the best view.  Try to do your best with the angle you have. </div></td>
  </tr>
  <tr>
    <td><div align="center"><img src="http://www.kitizenscience.org/images/whattolookfor_longfur.jpg" width="307" height="250" /></div></td>
    <td width="50">&nbsp;</td>
    <td><div align="center"><img src="http://www.kitizenscience.org/images/whattolookfor_backside.jpg" width="213" height="250" /></div></td>
  </tr>
</table>
<p>&nbsp;</p>
<h2>That's everything!</h2><p>We hope this is a fun and straightforward study.  If you have any questions, please email kitizenscience@gmail.com.</p>

<p>&nbsp;</p>


<% if (!instrOnly) {
        if (loggedIn) {
%>

<p align="center"><a class="big-button" href="compare.jsp">Proceed to Study</a></p>

<%      } else { //is logged in %>

<p align="center"><a class="big-button" href="compare.jsp">Login to Proceed to Study</a></p>

<%      } %>

</div>

<% }

}

if (rollback) {
    myShepherd.rollbackDBTransaction();
} else {
    System.out.println("register.jsp committing db transaction");
    myShepherd.commitDBTransaction();
}

%>
            </div>
            
          <jsp:include page="footer.jsp" flush="true"/>

