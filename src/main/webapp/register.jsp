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
    username = username.trim();
    if (!Util.isValidEmailAddress(email)) throw new IOException("Invalid email format");
    if (!Util.stringExists(pw1) || !Util.stringExists(pw2) || !pw1.equals(pw2)) throw new IOException("Password invalid or do not match");
    if (pw1.length() < 8) throw new IOException("Password is too short");
    User exists = myShepherd.getUser(username);
    if (exists == null) exists = myShepherd.getUserByEmailAddress(email);
    if ((exists != null) || username.equals("admin")) throw new IOException("Invalid username/email");
    String salt = Util.generateUUID();
    String hashPass = ServletUtilities.hashAndSaltPassword(pw1, salt);
    User user = new User(username, hashPass, salt);
    user.setEmailAddress(email);
    user.setNotes("<p data-time=\"" + System.currentTimeMillis() + "\">created via registration.</p>");
    Role role = new Role(username, "cat_mouse_volunteer");
    role.setContext(myShepherd.getContext());
    myShepherd.getPM().makePersistent(role);
    return user;
}

%><%
String context = ServletUtilities.getContext(request);
Shepherd myShepherd = new Shepherd(context);
myShepherd.setAction("register.jsp");
myShepherd.beginDBTransaction();
User thisUser = AccessControl.getUser(request, myShepherd);
if (Util.requestParameterSet(request.getParameter("recordQuiz"))) {
    if (thisUser == null) {
        Object u = session.getAttribute("user");
        if (u != null) thisUser = (User)u;
    }
    JSONObject rtn = new JSONObject();
    if (thisUser == null) {
        rtn.put("success", false);
        rtn.put("error", "could not find user");
    } else {
        SystemValue.set(myShepherd, "quiz_completed_" + thisUser.getUUID(), System.currentTimeMillis());
        rtn.put("success", true);
        rtn.put("id", thisUser.getUUID());
    }
    response.setContentType("application/javascript");
    System.out.println("INFO: recordQuiz for " + rtn);
    out.println(rtn.toString());
    myShepherd.commitDBTransaction();
    return;
}

%>
<style>
.mobile-note {
    margin: 30px 0 0 0;
}

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
    border: 0;
    color: #fff;
    line-height: 2em;
    padding: 7px 13px;
    font-weight: 300;
    vertical-align: middle;
    margin-right: 10px;
    margin-top: 15px;
    text-decoration: none !important;
}

.register-quiz-div {
    text-align: center;
    padding: 14px;
    border-radius: 8px;
}

.register-quiz-div-error {
    background-color: red;
}
.register-quiz-div-correct {
    background-color: #bff223;
}
.register-select-error {
    outline: solid 4px red;
    background-color: #FBB;
}

.register-quiz-div select {
    margin: 0 6px;
}

#quiz-button {
    display: none;
}

#proceed-div .big-button {
    transform: scale(1.5);
    display: inline-block;
}

</style>
<%

request.setAttribute("pageTitle", "Kitizen Science &gt; Participate in Online Data Processing Tasks");
boolean rollback = true;

boolean uwMode = Util.booleanNotFalse(SystemValue.getString(myShepherd, "uwMode"));
  //setup our Properties object to hold all properties
  //String langCode = "en";
  String langCode=ServletUtilities.getLanguageCode(request);
  boolean loggedIn = !AccessControl.isAnonymous(request);

boolean phase2User = false;
if (thisUser != null) {
    String[] validRoles = new String[]{"admin", "super_volunteer", "cat_mouse_volunteer", "cat_walk_volunteer"};
    List<Role> userRoles = myShepherd.getAllRolesForUserInContext(thisUser.getUsername(), context);
    for (String vr : validRoles) {
        for (Role r : userRoles) {
            if (vr.equals(r.getRolename())) phase2User = true;
        }
    }
}

    String modeString = request.getParameter("mode");
    boolean instrOnly = Util.requestParameterSet(request.getParameter("instructions"));
    boolean passedQuiz = Util.requestParameterSet(request.getParameter("_passedQuiz_"));

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
            System.out.println("WARNING: registerUser() threw " + ex.getMessage());
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
            String key = "survey_response_phase2_" + request.getParameter("user_uuid");
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

              <h2 class="intro">Cat &amp; Mouse: Do Online Microtasks</h2>

              <p align="left">
		
<div style="padding: 10px;">
<%
if (session.getAttribute("error") != null) {
	out.println(session.getAttribute("error"));
	session.removeAttribute("error");
}

%>
</div>
              
<% if (mode < 0) {
    if (uwMode) {
%>

<p>
Our photo matching study is now closed.
</p>


<p>
Our second validation study, open to everyone, will begin in January 2020!
</p>


<%  } else {  //not uwMode %>

<div class="explanation-section">

<%= NoteField.buildHtmlDiv("900928b8-0375-4f7a-8037-59a0209c5803", request, myShepherd) %>


<div class="org-ecocean-notefield-default" id="default-900928b8-0375-4f7a-8037-59a0209c5803">

<img src="images/participate_manatdesk.jpg" width="324" height="300" hspace="10" vspace="10" align="right" />

<p>Kitizen Science is now open for online &quot;Cat &amp; Mouse&quot; volunteers! You can participate from anywhere so long as you have a laptop/desktop computer, a reliable internet connection, and the ability to read and understand English.</p>
<p>Kitizen Science's online cat photo processing system is set up as a collection of &quot;microtasks,&quot; where volunteers are presented with a cat submission consisting of one or more photos. You will select attributes about the cat such as its primary color and whether it is a kitten or adult, and then compare it to suggested matches in our cat database to decide if the cat is new to our system or if it has a match. By building a record of when and where individual cats are sighted during cat surveys, you are helping us take a large number of photos and distill them into data in a format we can analyze.</p>
<p>Some cats are harder to see than others, and you won't always get to see good details. All that we ask is that you do your best with each submission. Similar to other photo classification citizen science programs, Kitizen Science records and compares the opinions of multiple volunteers so that occasional mistakes are outvoted by a majority.</p>
<p>There is no time limit or minimum commitment required, so this is a great way to participate in animal welfare research in a way that works with your own busy schedule. We estimate each submission will take a few minutes to process once you become familiar with our workflow. You can process a maximum of 24 submissions per day.  (People tend to become less successful at these tasks when they have been staring at photos for long periods of time.)</p>
<p><a href="register.jsp?instructions">You can read the instructions</a> for more information before deciding whether you want to sign up as a volunteer - these instructions will also provided as part of the volunteer registration process.</p>


</div>


<p>
    <form method="post">
    <input type="submit" value="Register to Participate" />
    <input type="hidden" name="fromMode" value="-1" />
    </form>
</p>



<%
    }

  if (loggedIn && phase2User) { %>
<!--
    <b>You are logged in already.  <a href="queue.jsp">Please proceed to study.</a></b>
-->
<% } else if (loggedIn) {  // NOT phase2... yet?  %>
<!--
    <p>You are logged in, but <b>not yet registered for this study</b>.  Please continue to consent to this study.</p>

    <form method="post">
    <input type="submit" value="Continue" />
    <input type="hidden" name="fromMode" value="-1" />
    </form>
-->

<% } else { %>

<!--
<p>
    <form method="post" style="display: none;" blocked>
    <input type="submit" value="Register to Participate" />
    <input type="hidden" name="fromMode" value="-1" />
    </form>
</p>
-->

<% } %>

</div>

<% }
if (mode == 0) {

/////  NOTE: the section below should have exact text from informed_consent.jsp, if changes happen there
%>

<div id="consent-section">

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


<div>
<form method="post">
    <input type="submit" value="Yes" />
    <input type="hidden" name="fromMode" value="0" />

    <input type="button" value="No" onClick="window.location.href='./';" />
</form>
</div>

</div>


<%

}

if ((mode == 1) && loggedIn && !phase2User) {
    System.out.println("INFO: legacy user consented; upgrading " + thisUser);
    Role role = new Role(thisUser.getUsername(), "cat_mouse_volunteer");
    role.setContext(myShepherd.getContext());
    myShepherd.getPM().makePersistent(role);
    rollback = false;
    session.setAttribute("user", thisUser);
    mode = 2;
}

if (mode == 1) {
    Properties recaptchaProps = ShepherdProperties.getProperties("recaptcha.properties", "", context);
%>

<div id="register-section">
<h3>Register a new account</h3>

<script>
var regexUsername = new RegExp('^[a-z0-9]+$');
function checkAccount() {
    var msg = 'Please correct the following problems:';
    var ok = true;

    $('#username').val( $('#username').val().trim() );
    if (!regexUsername.test($('#username').val())) {
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

<h3>Login to an existing account</h3>
<p>Already have an account from a previous study on Kitizen Science?</p>

<input type="button" value="Login" onClick="window.location.href='queue.jsp';" />

</div>
              
<% }
if (mode == 2) {

%>
<div id="survey-section">

<script>
//non-uw version
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

<% if (regu != null) {

    if (loggedIn) {  //guess this means legacyUser in-process
%>
<p><b style="font-size: 1.3em;">Updating account <u><%=regu.getUsername()%></u> for this study.</b></p>
<%
    } else {
%>
<p><b style="font-size: 1.3em;">Your user <u><%=regu.getUsername()%></u> has been created.</b></p>
<%
    }
}
%>
<p>
We would like you to answer this short survey about yourself so we can understand our audience and your experience.  The demographic questions are included so that we can compare participants in Kitizen Science with other citizen science projects.  Specifically, we are interested in knowing whether the demographics of Kitizen Science are similar, or different, from other projects.
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
Have you ever participated in an online citizen science project doing image identification or classification (NOT including our 2019 cat matching study)?
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

<%

} if (mode == 3) {
        if (instrOnly || passedQuiz) out.println("<style>#register-quiz, .quiz-note { display: none; }</style>");
        if (!passedQuiz) out.println("<style>#proceed-div { display: none; }</style>");
%>
<div id="instructions">

<h2>Instructions</h2>

<p>Kitizen Science's online cat photo processing system is set up as a collection of &quot;microtasks,&quot; where volunteers are presented with a cat submission consisting of one or more photos.  You will select attributes about the cat such as its primary color and whether it is a kitten or adult, and then compare it to suggested matches in our cat database to decide if the cat is new to our system or if it has a match. By building a record of when and where individual cats are sighted during cat surveys, you are helping us take a large number of photos and distill them into data in a format we can analyze.</p>
<p>Some cats are harder to see than others, and you won't always get to see good details. All that we ask is that you do your best with each  submission. Similar to other photo classification citizen science programs, Kitizen Science  records and compares the opinions of multiple volunteers so that occasional mistakes are outvoted by a majority.</p>
<p>There is no time limit or minimum commitment required, so this is a great way to participate in animal welfare research in a way that works with your own busy schedule.  We estimate each submission will  take a few minutes to process once you become familiar with our workflow.  You can process a maximum of 24 submissions per day.  (People tend to become less successful at these tasks when they have been staring at photos for long periods of time.)</p>
<h2 class="style2">Key rules</h2>
<p>We ask that you create only one login for Kitizen Science, and each login  has only one person using it.  We will be noting volunteer demographics summaries in reporting facts  about our program, so we want to have one set of demographic information  tied to one user account.  We also ask that you work by yourself and don't ask friends for help in identifying cats.</p>
<p>Because of the small screen size of smartphones and many tablets, we do not want you to process submissions on these smaller devices. Please use a standard desktop or laptop computer.</p>
<p>There is a quiz at the end of this instruction page to ensure that you have read the instructions and understand what is being asked of you.</p>
<h2>Step 1: Assigning cat attributes</h2>
<p>For each submission, you start out by assigning attributes to a cat.  You will have one or more photos of a cat, and you may or may not be able to see the cat's whole body.  Base your attribute assignments only on what you are certain that you can see.</p>
<p align="center"><img src="images/instructions_focalcat.jpg" width="600" height="250" /></p>
<p>Some photos will have more than one cat. The   cat whose attributes you are assigning and  matching  will be emphasized  by a  green box with thicker lines, whereas background cats in the same photo will be in  a box with  thinner lines. </p>
<p>First, select a cat's primary color or pattern group.  These are our 8 color categories:</p>
<table width="80%" border="0" align="center" cellpadding="10" cellspacing="0">
  <tr>
    <td width="550" valign="top"><div align="center"><strong>Black</strong>:  solid or with a   small patch of white</div></td>
    <td width="50" valign="top">&nbsp;</td>
    <td width="550" valign="top"><div align="center"><strong>Black &amp; White</strong></div></td>
  </tr>
  <tr>
    <td width="550"><div align="center"><img src="images/instructions_black.jpg" width="402" height="300" /></div></td>
    <td width="50">&nbsp;</td>
    <td width="550"><div align="center"><img src="images/instructions_bw.jpg" width="402" height="300" /></div></td>
  </tr>
  <tr>
    <td width="550" height="50">&nbsp;</td>
    <td width="50">&nbsp;</td>
    <td width="550" height="50">&nbsp;</td>
  </tr>
  <tr>
    <td width="550" valign="top"><div align="center"><strong>Grey or Brown Tabby/Torbie</strong></div></td>
    <td width="50" valign="top">&nbsp;</td>
    <td width="550" valign="top"><div align="center"><strong>Tabby/Torbie &amp; White</strong></div></td>
  </tr>
  <tr>
    <td width="550"><div align="center"><img src="images/instructions_tabby.jpg" width="402" height="300" /></div></td>
    <td width="50">&nbsp;</td>
    <td width="550"><div align="center"><img src="images/instructions_tabwhite.jpg" width="402" height="300" /></div></td>
  </tr>
  <tr>
    <td width="550" height="50">&nbsp;</td>
    <td width="50">&nbsp;</td>
    <td width="550" height="50">&nbsp;</td>
  </tr>
  <tr>
    <td width="550" valign="top"><div align="center"><strong>Orange</strong>: peach through dark orange</div></td>
    <td width="50" valign="top">&nbsp;</td>
    <td width="550" valign="top"><div align="center"><strong>Dark Grey</strong></div></td>
  </tr>
  <tr>
    <td width="550"><div align="center"><img src="images/instructions_orange.jpg" width="402" height="300" /></div></td>
    <td width="50">&nbsp;</td>
    <td width="550"><div align="center"><img src="images/instructions_grey.jpg" width="402" height="300" /></div></td>
  </tr>
  <tr>
    <td width="550" height="50">&nbsp;</td>
    <td width="50">&nbsp;</td>
    <td width="550" height="50">&nbsp;</td>
  </tr>
  <tr>
    <td width="550" valign="top"><div align="center"><strong>Calico/Tortoiseshell</strong>: including diluted/muted</div></td>
    <td width="50" valign="top">&nbsp;</td>
    <td width="550" valign="top"><div align="center"><strong>Beige/White/Siamese</strong></div></td>
  </tr>
  <tr>
    <td width="550"><div align="center"><img src="images/instructions_tortical.jpg" width="402" height="300" /></div></td>
    <td width="50">&nbsp;</td>
    <td width="550"><div align="center"><img src="images/instructions_light.jpg" width="402" height="300" /></div></td>
  </tr>
</table>
<p>&nbsp;</p>
<p>Second, select the cat's life stage (kitten or adult).</p>
<table width="80%" border="0" align="center" cellpadding="10" cellspacing="0">
  <tr>
    <td width="551" valign="top"><div align="center"><strong>Kitten</strong>: under about 6 months</div></td>
    <td width="50" valign="top">&nbsp;</td>
    <td width="498" valign="top"><div align="center"><strong>Adult</strong></div></td>
  </tr>
  <tr>
    <td><div align="center"><img src="images/instructions_kitten.jpg" width="402" height="300" /></div></td>
    <td width="50">&nbsp;</td>
    <td><div align="center"><img src="images/instructions_adult.jpg" width="402" height="300" /></div></td>
  </tr>
</table>
<p>&nbsp;</p>
<p>Third, select whether the cat has an ear tip removed (a sign the cat has been surgically sterilized), and on which side, or select unknown. Depending on how much eartip was removed at surgery time, these can be very hard to see. If you are not certain, select &quot;unknown.&quot;</p>
<table width="80%" border="0" align="center" cellpadding="10" cellspacing="0">
  <tr>
    <td width="551" valign="top"><div align="center"><strong>Yes - Cat's Left</strong></div></td>
    <td width="50" valign="top">&nbsp;</td>
    <td width="498" valign="top"><div align="center"><strong>Yes - Cat's Right</strong></div></td>
  </tr>
  <tr>
    <td><div align="center"><img src="images/instructions_tipleft.jpg" width="402" height="300" /></div></td>
    <td width="50">&nbsp;</td>
    <td><div align="center"><img src="images/instructions_tipright.jpg" width="402" height="300" /></div></td>
  </tr>
  <tr>
    <td height="50">&nbsp;</td>
    <td width="50">&nbsp;</td>
    <td height="50">&nbsp;</td>
  </tr>
  <tr>
    <td valign="top"><div align="center"><strong>No</strong></div></td>
    <td width="50" valign="top">&nbsp;</td>
    <td valign="top"><div align="center"></div></td>
  </tr>
  <tr>
    <td><div align="center"><img src="images/instructions_untipped.jpg" width="402" height="300" /></div></td>
    <td width="50">&nbsp;</td>
    <td><div align="center"></div></td>
  </tr>
</table>
<p>&nbsp;</p>
<p>Fourth, select whether the cat is wearing a collar. If you are not certain, select &quot;unknown.&quot;</p>
<table width="80%" border="0" align="center" cellpadding="10" cellspacing="0">
  <tr>
    <td width="551" valign="top"><div align="center"><strong>Yes</strong></div></td>
    <td width="50" valign="top">&nbsp;</td>
    <td width="498" valign="top"><div align="center"><strong>No</strong></div></td>
  </tr>
  <tr>
    <td><div align="center"><img src="images/instructions_collar.jpg" width="402" height="300" /></div></td>
    <td width="50">&nbsp;</td>
    <td><div align="center"><img src="images/instructions_nocollar.jpg" width="402" height="300" /></div></td>
  </tr>
</table>
<p>&nbsp;</p>
<p>Lastly, select the cat's sex.  A photo could contain an unsterilized male cat's rear end where you can see testicles, or a photo of an unsterilized female could show visible mammary gland development or depict a mother with her kittens.  Most cats will be labeled as unknown  because sex generally isn't visible.</p>
<table width="80%" border="0" align="center" cellpadding="10" cellspacing="0">
  <tr>
    <td width="551" valign="top"><div align="center"><strong>Male:</strong> testicles visible</div></td>
    <td width="50" valign="top">&nbsp;</td>
    <td width="498" valign="top"><div align="center"><strong> Female: </strong> nursing kittens</div></td>
  </tr>
  <tr>
    <td><div align="center"><img src="images/instructions_male.jpg" width="402" height="300" /></div></td>
    <td width="50">&nbsp;</td>
    <td><div align="center"><img src="images/instructions_female.jpg" width="402" height="300" /></div></td>
  </tr>
</table>
<p>&nbsp;</p>
<h2>Step 2: Cat matching</h2>
<p>After saving a cat's attributes, our  program  looks for  potential matches based on a cat's attributes and its physical proximity to other cats in our system, showing you a list of cats that are most likely to be a match.  Some cat submissions will have a match in the system, and some won't.  You decide if one of the potential matches is the same cat, or click to decide that the submission is a new cat to the system without a match.</p>
<p>&nbsp;</p>
<h2>How to compare similar cats</h2><p>Even two similar-looking cats can be separated if you examine them closely.  Here are some details to look for when comparing two cats.</p>
<table width="80%" border="0" align="center" cellpadding="10" cellspacing="0">
  <tr>
    <td width="551" valign="top"><div align="center">Is the cat's ear tip removed (a marker  of sterilization)?  These can be hard to see at a distance or in cats with a small amount of  ear tip removed. Either ear may be tipped.</div></td>
    <td width="50" valign="top">&nbsp;</td>
    <td width="498" valign="top"><div align="center">Is the cat wearing a collar?  Keep in mind that collars can be added or removed, unlike fur coat patterns. Don't rely only on collars, but it can be one clue.</div></td>
  </tr>
  <tr>
    <td><div align="center"><img src="images/whattolookfor_eartip.jpg" width="287" height="250" /></div></td>
    <td width="50">&nbsp;</td>
    <td><div align="center"><img src="images/whattolookfor_collar.jpg" width="287" height="250" /></div></td>
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
    <td><div align="center"><img src="images/whattolookfor_face.jpg" width="287" height="250" /></div></td>
    <td width="50">&nbsp;</td>
    <td><div align="center"><img src="images/whattolookfor_flanks.jpg" width="287" height="250" /></div></td>
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
    <td><div align="center"><img src="images/whattolookfor_frontlegs.jpg" width="287" height="250" /></div></td>
    <td width="50">&nbsp;</td>
    <td><div align="center"><img src="images/whattolookfor_tail.jpg" width="238" height="250" /></div></td>
  </tr>
  <tr>
    <td height="50">&nbsp;</td>
    <td width="50">&nbsp;</td>
    <td height="50">&nbsp;</td>
  </tr>
  <tr>
    <td valign="top"><div align="center">How long is the cat's fur? Long, medium, or short fur?  (Sometimes  cats have tangled mats of fur shaved during the spay/neuter process, but bald spots don't stay long.)</div></td>
    <td width="50" valign="top">&nbsp;</td>
    <td valign="top"><div align="center">Remember that not every cat photo is going to be a great one, and sometimes you won't have a good view.  Try to do your best with the angle you have. </div></td>
  </tr>
  <tr>
    <td><div align="center"><img src="images/whattolookfor_longfur.jpg" width="307" height="250" /></div></td>
    <td width="50">&nbsp;</td>
    <td><div align="center"><img src="images/whattolookfor_backside.jpg" width="213" height="250" /></div></td>
  </tr>
</table>
<p>&nbsp;</p>
<h2>That's everything!</h2>
<p>If you have any questions, please email kitizenscience@gmail.com.</p>
<p>&nbsp;</p>

<div id="register-quiz">
<script type="text/javascript">
    var quizUnset = 99;
$(document).ready(function() {
    var quizq = [
        [ 'Choose a color/pattern', 'Black', 'Black & White', 'Grey or Brown Tabby/Torbie', 'Tabby/Torbie & White', 'Orange', 'Dark Grey', 'Calico/Tortoiseshell', 'Beige/White/Siamese' ],
        ['Choose life stage', 'Adult', 'Kitten'],
        ['Ear tip removed?', 'Yes - Cat\'s Left', 'Yes - Cat\'s Right', 'No'],
        ['Wearing collar?', 'Yes', 'No', 'Unknown'],
        ['Choose a sex', 'Male', 'Female', 'Unknown']
    ];
    $('.register-quiz-div').each(function(elI, el) {
        var h = '';
        for (var i = 0 ; i < quizq.length ; i++) {
            h += '<select id="quiz-' + i + '">';
            h += '<option value="">' + quizq[i][0] + '</option>';
            for (var j = 1 ; j < quizq[i].length ; j++) {
                h += '<option>' + quizq[i][j] + '</option>';
            }
            h += '</select>';
        }
        $(el).append(h);
        $('.register-quiz-div select').on('change', function(ev) {
            quizUnset = 0;
            //$(ev.target.parentElement).find('select').removeClass('register-select-error');
            ev.target.classList.remove('register-select-error');
            $(ev.target.parentElement).removeClass('register-quiz-div-error').removeClass('register-quiz-div-correct');
            $('.register-quiz-div select').each(function(i, el) {
                if (!el.selectedIndex) quizUnset++;
            });
            $('#quiz-unanswered-count').text(quizUnset);
            if (quizUnset < 2) {
                $('#quiz-blocker').hide();
                $('#quiz-button').show();
            } else {
                $('#quiz-blocker').show();
                $('#quiz-button').hide();
            }
        });
    });
});

function quizButton(faked) {
/*
    if (quizUnset > 0) {
        alert('You must answer all of the quiz questions.');
        return false;
    }
*/
    var qans = [
        [6,1,3,2,3],
        [4,1,3,1,3],
        [2,1,2,2,3],
        [5,2,3,2,3]
    ];
    var passedQuiz = true;
    $('.register-quiz-div input').removeClass('register-select-error');
    $('.register-quiz-div').each(function(qi, el) {
        var wrong = 0;
        $(el).find('select').each(function(seli, sel) {
console.log('%d) %d: %d [%d]', qi, seli, sel.selectedIndex, qans[qi][seli]);
            if (sel.selectedIndex != qans[qi][seli]) {
                sel.classList.add('register-select-error');
                wrong++;
            }
        });
        if (wrong > 0) {
            //$('#register-quiz-' + qi).addClass('register-quiz-div-error').removeClass('register-quiz-div-correct');
            $('#register-quiz-' + qi).removeClass('register-quiz-div-correct');
            passedQuiz = false;
        } else {
            $('#register-quiz-' + qi).addClass('register-quiz-div-correct');
        }
    });
    if (faked) passedQuiz = true;
    if (passedQuiz) {
        $('#quiz-blocker').hide();
        $('#quiz-button').hide();
        $.ajax({
            url: 'register.jsp?recordQuiz',
            type: 'GET',
            dataType: 'json',
            complete: function(x) {
                console.info('recordQuiz returned: %o', x);
                if (!x || !x.responseJSON || !x.responseJSON.success) {
                    alert('Sorry, there was an error with processing the quiz');
                } else {
                    $('#proceed-div').show();
                }
            }
        });

    } else {
        window.setTimeout(function() { alert('You have some quiz answers which are incorrect.  They are in RED.'); }, 200);
    }
    return false;
}
</script>

<h2>Quiz</h2>
<p>Please select cat attributes for each of these four sample cats. You can attempt to answer each quiz item as many times as you like, but you won't be able to move on until all questions have been answered correctly.</p>
<table width="80%" border="0" align="center" cellpadding="10" cellspacing="0">
  <tr>
    <td><h2 align="center"><strong>Cat 1</strong></h2>
      <p align="center"><img src="images/quizcat1.jpg" width="800" height="500" /></p>

<div class="register-quiz-div" id="register-quiz-0"></div>

  </tr>
  <tr>
    <td><div align="center"></div></td>
  </tr>
  <tr>
    <td valign="top"><h2 align="center"><strong>Cat 2</strong></h2>
      <p align="center"><img src="images/quizcat2.jpg" width="800" height="500" /></p>

<div class="register-quiz-div" id="register-quiz-1"></div>

  </tr>
  <tr>
    <td><div align="center"></div></td>
  </tr>
  <tr>
    <td><h2 align="center"><strong>Cat 3</strong></h2>
      <p align="center"><img src="images/quizcat3.jpg" width="800" height="500" /></p>

<div class="register-quiz-div" id="register-quiz-2"></div>

  </tr>
  <tr>
    <td valign="top"><div align="center"></div></td>
  </tr>
  <tr>
    <td><h2 align="center"><strong>Cat 4</strong></h2>
      <p align="center"><img src="images/quizcat4.jpg" width="800" height="500" /></p>

<div class="register-quiz-div" id="register-quiz-3"></div>

  </tr>
</table>
<p>&nbsp;</p>


<div style="text-align: center;">
    <div id="quiz-blocker">
        <h2>Complete the quiz for all four cats above to continue.</h2>
        <p>Questions remaining: <b id="quiz-unanswered-count">20</b></p>
    </div>

    <a id="quiz-button" class="big-button" href="#" onClick="return quizButton();">Submit my quiz answers, proceed to study</a>
</div>

</div><!-- end quiz -->

<p>&nbsp;</p>


<% if (!instrOnly) {
        if (loggedIn) {
%>

<p align="center" id="proceed-div"><a class="big-button" href="queue.jsp">Congratulations!  You answered everything correctly.  Proceed to study.</a></p>

<%      } else { //is logged in %>

<p align="center" id="proceed-div"><a class="big-button" href="queue.jsp">Congratulations!  You answered everything correctly.  Login to proceed to study.</a></p>

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

