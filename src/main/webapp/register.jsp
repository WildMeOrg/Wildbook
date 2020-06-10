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
    username = username.toLowerCase().trim();
    if (!Util.isValidEmailAddress(email)) throw new IOException("Invalid email format");
    if (!Util.stringExists(pw1) || !Util.stringExists(pw2) || !pw1.equals(pw2)) throw new IOException("Password invalid or do not match");
    if (pw1.length() < 8) throw new IOException("Password is too short");
    username = username.toLowerCase();
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

request.setAttribute("pageTitle", "Kitizen Science &gt; Participate");
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
        String[] fields = new String[]{"user_uuid", "cat_volunteer", "have_cats", "disability", "citsci", "citsci_collecting", "age", "gender", "ethnicity", "education", "how_hear", "where_live"};
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

              <h1 class="intro">Participating in Online Tasks</h1>

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

<img src="images/participate_manatdesk.jpg" width="324" height="300" hspace="10" vspace="10" align="right" />

<p>
The second of our three validation studies is about testing the online workflow for Kitizen Science. This builds on the first study, which asked volunteers to compare two cat photos and decide if they are a match. Now, we are doing a trial of how the online side of Kitizen Science works. We want to learn how many volunteers should be processing each submission, how successful volunteers are at using our interface, and also receive feedback from you about what you think of this interface.
</p>

<p xstyle="display: none;">
You can <a href="register.jsp?instructions">read the online study instructions</a>
before deciding whether you want to volunteer.
</p>

<p>
This study is open from March 9 to June 30, 2020.
</p>

<p>
    <form method="post">
    <input type="submit" value="Register to Participate" />
    <input type="hidden" name="fromMode" value="-1" />
    </form>
</p>


<%
    }

  if (loggedIn && phase2User) { %>
    <b>You are logged in already.  <a href="queue.jsp">Please proceed to study.</a></b>
<% } else if (loggedIn) {  // NOT phase2... yet?  %>
    <p>You are logged in, but <b>not yet registered for this study</b>.  Please continue to consent to this study.</p>

    <form method="post">
    <input type="submit" value="Continue" />
    <input type="hidden" name="fromMode" value="-1" />
    </form>

<% } else { %>

<p>
    <form method="post" style="display: none;" blocked>
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
<h2>Register a new account</h2>

<script>
var regexUsername = new RegExp('^[a-z0-9]+$');
function checkAccount() {
    var msg = 'Please correct the following problems:';
    var ok = true;

    $('#username').val( $('#username').val().trim().toLowerCase() );
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

<h2>Login to an existing account</h2>
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
		'where_live',
		'age',
		'gender',
		'ethnicity',
		'education',
		'have_cats',
    'cat_volunteer',
    'disability',
    'citsci',
		'citsci_collecting',
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
				if (surveyRequired[i] == 'where_live') {
            numChecked = el.val().trim().length;
        }
				if (surveyRequired[i] == 'gender') {
            numChecked = el.val().trim().length;
        }
				if (surveyRequired[i] == 'education') {
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
Please answer this short survey about yourself so we can understand our volunteers and the experience you bring to Kitizen Science, and so we can compare our volunteer demographics with those of other citizen science projects.
</p>

<p>
Where do you live?
<select class="top" name="where_live">
  <option value="0">Choose Location</option>
	<optgroup label="US States/Territories">
		<option value="AK">Alaska</option>
		<option value="AL">Alabama</option>
		<option value="AR">Arkansas</option>
		<option value="AS">American Samoa</option>
		<option value="AZ">Arizona</option>
		<option value="CA">California</option>
		<option value="CO">Colorado</option>
		<option value="CT">Connecticut</option>
		<option value="DC">District of Columbia</option>
		<option value="DE">Delaware</option>
		<option value="FL">Florida</option>
		<option value="GA">Georgia</option>
		<option value="GU">Guam</option>
		<option value="HI">Hawaii</option>
		<option value="IA">Iowa</option>
		<option value="ID">Idaho</option>
		<option value="IL">Illinois</option>
		<option value="IN">Indiana</option>
		<option value="KS">Kansas</option>
		<option value="KY">Kentucky</option>
		<option value="LA">Louisiana</option>
		<option value="MA">Massachusetts</option>
		<option value="MD">Maryland</option>
		<option value="ME">Maine</option>
		<option value="MI">Michigan</option>
		<option value="MN">Minnesota</option>
		<option value="MO">Missouri</option>
		<option value="MS">Mississippi</option>
		<option value="MT">Montana</option>
		<option value="NC">North Carolina</option>
		<option value="ND">North Dakota</option>
		<option value="MP">Northern Mariana Islands</option>
		<option value="NE">Nebraska</option>
		<option value="NH">New Hampshire</option>
		<option value="NJ">New Jersey</option>
		<option value="NM">New Mexico</option>
		<option value="NV">Nevada</option>
		<option value="NY">New York</option>
		<option value="OH">Ohio</option>
		<option value="OK">Oklahoma</option>
		<option value="OR">Oregon</option>
		<option value="PA">Pennsylvania</option>
		<option value="PR">Puerto Rico</option>
		<option value="RI">Rhode Island</option>
		<option value="SC">South Carolina</option>
		<option value="SD">South Dakota</option>
		<option value="TN">Tennessee</option>
		<option value="TX">Texas</option>
		<option value="UM">United States Minor Outlying Islands</option>
		<option value="VI">United States Virgin Islands</option>
		<option value="UT">Utah</option>
		<option value="VA">Virginia</option>
		<option value="VT">Vermont</option>
		<option value="WA">Washington</option>
		<option value="WI">Wisconsin</option>
		<option value="WV">West Virginia</option>
		<option value="WY">Wyoming</option>
	</optgroup>
	<optgroup label="Non-US Country">
		<option value="Afghanistan">Afghanistan</option>
		<option value="Albania">Albania</option>
		<option value="Algeria">Algeria</option>
		<option value="Andorra">Andorra</option>
		<option value="Angola">Angola</option>
		<option value="Anguilla">Anguilla</option>
		<option value="Antigua & Barbuda">Antigua & Barbuda</option>
		<option value="Argentina">Argentina</option>
		<option value="Armenia">Armenia</option>
		<option value="Aruba">Aruba</option>
		<option value="Australia">Australia</option>
		<option value="Austria">Austria</option>
		<option value="Azerbaijan">Azerbaijan</option>
		<option value="Bahamas">Bahamas</option>
		<option value="Bahrain">Bahrain</option>
		<option value="Bangladesh">Bangladesh</option>
		<option value="Barbados">Barbados</option>
		<option value="Belarus">Belarus</option>
		<option value="Belgium">Belgium</option>
		<option value="Belize">Belize</option>
		<option value="Benin">Benin</option>
		<option value="Bermuda">Bermuda</option>
		<option value="Bhutan">Bhutan</option>
		<option value="Bolivia">Bolivia</option>
		<option value="Bonaire">Bonaire</option>
		<option value="Bosnia & Herzegovina">Bosnia & Herzegovina</option>
		<option value="Botswana">Botswana</option>
		<option value="Brazil">Brazil</option>
		<option value="British Indian Ocean Terr">British Indian Ocean Terr</option>
		<option value="Brunei">Brunei</option>
		<option value="Bulgaria">Bulgaria</option>
		<option value="Burkina Faso">Burkina Faso</option>
		<option value="Burundi">Burundi</option>
		<option value="Cambodia">Cambodia</option>
		<option value="Cameroon">Cameroon</option>
		<option value="Canada">Canada</option>
		<option value="Canary Islands">Canary Islands</option>
		<option value="Cape Verde">Cape Verde</option>
		<option value="Cayman Islands">Cayman Islands</option>
		<option value="Central African Republic">Central African Republic</option>
		<option value="Chad">Chad</option>
		<option value="Channel Islands">Channel Islands</option>
		<option value="Chile">Chile</option>
		<option value="China">China</option>
		<option value="Christmas Island">Christmas Island</option>
		<option value="Cocos Island">Cocos Island</option>
		<option value="Colombia">Colombia</option>
		<option value="Comoros">Comoros</option>
		<option value="Congo">Congo</option>
		<option value="Cook Islands">Cook Islands</option>
		<option value="Costa Rica">Costa Rica</option>
		<option value="Cote d'Ivoire">Cote d'Ivoire</option>
		<option value="Croatia">Croatia</option>
		<option value="Cuba">Cuba</option>
		<option value="Curacao">Curacao</option>
		<option value="Cyprus">Cyprus</option>
		<option value="Czech Republic">Czech Republic</option>
		<option value="Denmark">Denmark</option>
		<option value="Djibouti">Djibouti</option>
		<option value="Dominica">Dominica</option>
		<option value="Dominican Republic">Dominican Republic</option>
		<option value="East Timor">East Timor</option>
		<option value="Ecuador">Ecuador</option>
		<option value="Egypt">Egypt</option>
		<option value="El Salvador">El Salvador</option>
		<option value="Equatorial Guinea">Equatorial Guinea</option>
		<option value="Eritrea">Eritrea</option>
		<option value="Estonia">Estonia</option>
		<option value="Ethiopia">Ethiopia</option>
		<option value="Falkland Islands">Falkland Islands</option>
		<option value="Faroe Islands">Faroe Islands</option>
		<option value="Fiji">Fiji</option>
		<option value="Finland">Finland</option>
		<option value="France">France</option>
		<option value="French Guiana">French Guiana</option>
		<option value="French Polynesia">French Polynesia</option>
		<option value="French Southern Terr">French Southern Terr</option>
		<option value="Gabon">Gabon</option>
		<option value="Gambia">Gambia</option>
		<option value="Georgia">Georgia</option>
		<option value="Germany">Germany</option>
		<option value="Ghana">Ghana</option>
		<option value="Gibraltar">Gibraltar</option>
		<option value="Greece">Greece</option>
		<option value="Greenland">Greenland</option>
		<option value="Grenada">Grenada</option>
		<option value="Guadeloupe">Guadeloupe</option>
		<option value="Guatemala">Guatemala</option>
		<option value="Guinea">Guinea</option>
		<option value="Guyana">Guyana</option>
		<option value="Haiti">Haiti</option>
		<option value="Hawaii">Hawaii</option>
		<option value="Honduras">Honduras</option>
		<option value="Hong Kong">Hong Kong</option>
		<option value="Hungary">Hungary</option>
		<option value="Iceland">Iceland</option>
		<option value="Indonesia">Indonesia</option>
		<option value="India">India</option>
		<option value="Iran">Iran</option>
		<option value="Iraq">Iraq</option>
		<option value="Ireland">Ireland</option>
		<option value="Isle of Man">Isle of Man</option>
		<option value="Israel">Israel</option>
		<option value="Italy">Italy</option>
		<option value="Jamaica">Jamaica</option>
		<option value="Japan">Japan</option>
		<option value="Jordan">Jordan</option>
		<option value="Kazakhstan">Kazakhstan</option>
		<option value="Kenya">Kenya</option>
		<option value="Kiribati">Kiribati</option>
		<option value="Kuwait">Kuwait</option>
		<option value="Kyrgyzstan">Kyrgyzstan</option>
		<option value="Laos">Laos</option>
		<option value="Latvia">Latvia</option>
		<option value="Lebanon">Lebanon</option>
		<option value="Lesotho">Lesotho</option>
		<option value="Liberia">Liberia</option>
		<option value="Libya">Libya</option>
		<option value="Liechtenstein">Liechtenstein</option>
		<option value="Lithuania">Lithuania</option>
		<option value="Luxembourg">Luxembourg</option>
		<option value="Macau">Macau</option>
		<option value="Macedonia">Macedonia</option>
		<option value="Madagascar">Madagascar</option>
		<option value="Malaysia">Malaysia</option>
		<option value="Malawi">Malawi</option>
		<option value="Maldives">Maldives</option>
		<option value="Mali">Mali</option>
		<option value="Malta">Malta</option>
		<option value="Marshall Islands">Marshall Islands</option>
		<option value="Martinique">Martinique</option>
		<option value="Mauritania">Mauritania</option>
		<option value="Mauritius">Mauritius</option>
		<option value="Mayotte">Mayotte</option>
		<option value="Mexico">Mexico</option>
		<option value="Midway Islands">Midway Islands</option>
		<option value="Moldova">Moldova</option>
		<option value="Monaco">Monaco</option>
		<option value="Mongolia">Mongolia</option>
		<option value="Montserrat">Montserrat</option>
		<option value="Morocco">Morocco</option>
		<option value="Mozambique">Mozambique</option>
		<option value="Myanmar">Myanmar</option>
		<option value="Namibia">Namibia</option>
		<option value="Nauru">Nauru</option>
		<option value="Nepal">Nepal</option>
		<option value="Netherland Antilles">Netherland Antilles</option>
		<option value="Netherlands">Netherlands</option>
		<option value="Nevis">Nevis</option>
		<option value="New Caledonia">New Caledonia</option>
		<option value="New Zealand">New Zealand</option>
		<option value="Nicaragua">Nicaragua</option>
		<option value="Niger">Niger</option>
		<option value="Nigeria">Nigeria</option>
		<option value="Niue">Niue</option>
		<option value="Norfolk Island">Norfolk Island</option>
		<option value="Norway">Norway</option>
		<option value="North Korea">North Korea</option>
		<option value="Oman">Oman</option>
		<option value="Pakistan">Pakistan</option>
		<option value="Palau Island">Palau Island</option>
		<option value="Palestine">Palestine</option>
		<option value="Panama">Panama</option>
		<option value="Papua New Guinea">Papua New Guinea</option>
		<option value="Paraguay">Paraguay</option>
		<option value="Peru">Peru</option>
		<option value="Philippines">Philippines</option>
		<option value="Pitcairn Island">Pitcairn Island</option>
		<option value="Poland">Poland</option>
		<option value="Portugal">Portugal</option>
		<option value="Qatar">Qatar</option>
		<option value="Republic of Montenegro">Republic of Montenegro</option>
		<option value="Republic of Serbia">Republic of Serbia</option>
		<option value="Reunion">Reunion</option>
		<option value="Romania">Romania</option>
		<option value="Russia">Russia</option>
		<option value="Rwanda">Rwanda</option>
		<option value="St Barthelemy">St Barthelemy</option>
		<option value="St Eustatius">St Eustatius</option>
		<option value="St Helena">St Helena</option>
		<option value="St Kitts & Nevis">St Kitts & Nevis</option>
		<option value="St Lucia">St Lucia</option>
		<option value="St Maarten">St Maarten</option>
		<option value="St Pierre & Miquelon">St Pierre & Miquelon</option>
		<option value="St Vincent & Grenadines">St Vincent & Grenadines</option>
		<option value="Saipan">Saipan</option>
		<option value="Samoa">Samoa</option>
		<option value="Samoa American">Samoa American</option>
		<option value="San Marino">San Marino</option>
		<option value="Sao Tome & Principe">Sao Tome & Principe</option>
		<option value="Saudi Arabia">Saudi Arabia</option>
		<option value="Senegal">Senegal</option>
		<option value="Seychelles">Seychelles</option>
		<option value="Sierra Leone">Sierra Leone</option>
		<option value="Singapore">Singapore</option>
		<option value="Slovakia">Slovakia</option>
		<option value="Slovenia">Slovenia</option>
		<option value="Solomon Islands">Solomon Islands</option>
		<option value="Somalia">Somalia</option>
		<option value="South Africa">South Africa</option>
		<option value="South Korea">South Korea</option>
		<option value="Spain">Spain</option>
		<option value="Sri Lanka">Sri Lanka</option>
		<option value="Sudan">Sudan</option>
		<option value="Suriname">Suriname</option>
		<option value="Swaziland">Swaziland</option>
		<option value="Sweden">Sweden</option>
		<option value="Switzerland">Switzerland</option>
		<option value="Syria">Syria</option>
		<option value="Tahiti">Tahiti</option>
		<option value="Taiwan">Taiwan</option>
		<option value="Tajikistan">Tajikistan</option>
		<option value="Tanzania">Tanzania</option>
		<option value="Thailand">Thailand</option>
		<option value="Togo">Togo</option>
		<option value="Tokelau">Tokelau</option>
		<option value="Tonga">Tonga</option>
		<option value="Trinidad & Tobago">Trinidad & Tobago</option>
		<option value="Tunisia">Tunisia</option>
		<option value="Turkey">Turkey</option>
		<option value="Turkmenistan">Turkmenistan</option>
		<option value="Turks & Caicos">Turks & Caicos</option>
		<option value="Tuvalu">Tuvalu</option>
		<option value="Uganda">Uganda</option>
		<option value="United Kingdom">United Kingdom</option>
		<option value="Ukraine">Ukraine</option>
		<option value="United Arab Emirates">United Arab Emirates</option>
		<option value="United Kingdom">United Kingdom</option>
		<option value="Uruguay">Uruguay</option>
		<option value="Uzbekistan">Uzbekistan</option>
		<option value="Vanuatu">Vanuatu</option>
		<option value="Vatican City">Vatican City</option>
		<option value="Venezuela">Venezuela</option>
		<option value="Vietnam">Vietnam</option>
		<option value="Virgin Islands (British)">Virgin Islands (British)</option>
		<option value="Wake Island">Wake Island</option>
		<option value="Wallis & Futuna">Wallis & Futuna</option>
		<option value="Yemen">Yemen</option>
		<option value="Zaire">Zaire</option>
		<option value="Zambia">Zambia</option>
		<option value="Zimbabwe">Zimbabwe</option>
	</optgroup>
</select>
</p>

<p>
	What is your current age?
	<select class="top" name="age">
	    <option value="0">Choose Age</option>
	<%
	    for (int i = 18 ; i <= 100 ; i++) {
	        out.println("<option>" + i + "</option>\n");
	    }
	%>
	</select>
</p>

<p>
	What is your gender?
	<select class="top" name="gender">
	    <option value="0">Choose Gender</option>
	<%
			String[] genders = {"Woman", "Man", "Nonbinary/Other"};
	    for (int i = 0 ; i < genders.length ; i++) {
	        out.println("<option>" + genders[i] + "</option>\n");
	    }
	%>
	</select>
</p>

<p>
	What is your race/ethnicity (select multiple if appropriate)?
	<%
			String[] raceEthnicities = {"American Indian or Alaska Native", "Asian", "Black or African American", "Hispanic or Latino", "Middle Eastern", "Native Hawaiian or Pacific Islander", "White"};
	    for (int i = 0 ; i < raceEthnicities.length ; i++) {
	        out.println("<br /><input id='" + raceEthnicities[i] + "_input' type='checkbox' value='" + raceEthnicities[i] + "' name='ethnicity' /> <label for='" + raceEthnicities[i] + "'>" + raceEthnicities[i] + "</label>\n");
	    }
	%>
	</select>
</p>

<p>
	What is your highest level of education?
	<select class="top" name="education">
	    <option value="0">Choose Education Level</option>
	<%
			String[] educationLevel = {"Less than high school", "High school", "Technical/Associate's degree or some college", "Bachelor's degree", "Graduate/professional degree"};
	    for (int i = 0 ; i < educationLevel.length ; i++) {
	        out.println("<option>" + educationLevel[i] + "</option>\n");
	    }
	%>
	</select>
</p>

<p>
Do you currently have a cat/cats?
<br /><input id="have_cats_yes_pet" type="checkbox" value="Yes, a pet cat/cats" name="have_cats" /> <label for="have_cats_yes_pet">Yes, a pet cat/cats</label>
<br /><input id="have_cats_yes_feral" type="checkbox" value="Yes, I care for feral/free-roaming cats" name="have_cats" /> <label for="have_cats_yes_feral">Yes, I care for feral/free-roaming cats</label>
<br /><input id="have_cats_no" type="checkbox" value="No" name="have_cats" /> <label for="have_cats_no">No</label>
</p>

<p>
Are you currently involved in volunteering with cats?
<br /><input id="cat_volunteer_yes" type="radio" value="Yes" name="cat_volunteer" /> <label for="cat_volunteer_yes">Yes</label>
<br /><input id="cat_volunteer_no" type="radio" value="No" name="cat_volunteer" /> <label for="cat_volunteer_no">No</label>
<br /><input id="cat_volunteer_past" type="radio" value="Not now, but in the past" name="cat_volunteer" /> <label for="cat_volunteer_past">Not now, but in the past</label>
</p>

<p>
Do you have a disability or personal limitation (such as being a parent/caregiver) that prevents you from volunteering with cats in a typical setting like a shelter?
<br /><input id="disability_no" type="radio" value="No" name="disability" /> <label for="disability_no">No</label>
<br /><input id="disability_yes" type="radio" value="Yes" name="disability" /> <label for="disability_yes">Yes</label>
<br /><input id="disability_sometimes" type="radio" value="Sometimes" name="disability" /> <label for="disability_sometimes">Sometimes</label>
</p>

<p>
Have you ever participated in an online citizen science project doing image identification or classification (apart from Kitizen Science's validation studies)?
<br /><input id="citsci_no" type="radio" value="No" name="citsci" /> <label for="citsci_no">No</label>
<br /><input id="citsci_yes" type="radio" value="Yes" name="citsci" /> <label for="citsci_yes">Yes</label>
</p>

<p>
Have you previously participated in a citizen science project collecting photos or data about animals or nature?
<br /><input id="citsci_collecting_no" type="radio" value="No" name="citsci_collecting" /> <label for="citsci_collecting_no">No</label>
<br /><input id="citsci_collecting_yes" type="radio" value="Yes" name="citsci_collecting" /> <label for="citsci_collecting_yes">Yes</label>
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

<h1>Instructions for the Online Workflow Study</h1>

<p>The second of our three validation studies is about testing the online workflow for Kitizen Science.  This builds on the first study, which asked volunteers to compare two cat photos and decide if they are a match.  Now, we are doing a trial of how the online side of Kitizen Science works.  We want to learn how many volunteers should be processing each submission, how successful volunteers are at using our interface, and also receive feedback from you about what you think of this interface.  You will receive a short survey via email after this study ends asking for your feedback and for you to rate aspects of the website.</p>
<p class="style2">This study is open from March 9 to May 31, 2020.</p>
<h2><a name="requirements" id="requirements"></a>Computer requirements</h2></a>
<p>
Because of the small screen size of web browsers on smart phones and small tablets, we do not want you to process submissions on smaller devices. Please use a desktop or laptop computer.
</p>
<h2>Rules</h2>
<p>We ask that you create only one login for Kitizen Science, and each login only has one person using it.  We are looking at how participant demographics might change your success at processing submissions, so we need one set of demographic information to be tied to one user account.  We also ask that you don't ask friends for help during your participation – we want to see how successful you are while working on your own.</p>
<p>There is a quiz at the end of this instruction page to ensure that you have read the instructions and understand what is being asked of you. </p>
<h2>General instructions</h2>
<p>In this study, you will be presented with a cat photo submission and asked to do two things: to assign attributes to the cat in the photo (primary color/pattern, life stage, ear tip, collar, and sex if visible), and then to decide if the cat has a match in the system  or if they are a new cat to the system. These test photos were obtained in the same way that our project will gather data in the real world.  That means some cats are harder to see than others, and you won't always get to see good details.</p>
<p class="style2">There are 168 submissions in the system, and you'll be presented with one randomly.  You can complete all of them or only a few – either way, we value your time and energy and Kitizen Science always aims to make participation flexible.  We estimate each submission will each take a few minutes to process once you become familiar with the workflow.  You can process a maximum of 24 submissions per day.  (Observer fatigue can cause people to become less successful when they have been staring at photos for extended periods of time.)</p>
<h2>Step 1: assigning cat attributes</h2>
<p>For each submission, you start out by assigning attributes to a cat. You may have one, two, or three photos of the cat, and you may or may not be able to see the cat's whole body. Base your attribute assignments only on what you are sure you can see.</p>
<p>Some photos will have more than one cat. The  cat whose attributes you are assigning and  matching  will be highlighted  in a green box.</p>
<p>First, select a cat's primary color or pattern group.  These are our 8 categories:</p>
<table width="80%" border="0" align="center" cellpadding="10" cellspacing="0">
  <tr>
    <td width="550" valign="top"><div align="center"><strong>Black</strong>:  or with a  very small patch of white</div></td>
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
    <td width="551" valign="top"><div align="center"><strong>Kitten</strong>: Under about 6 months</div></td>
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
<p>Third, select whether the cat has an ear tip removed (a sign the cat has been surgically sterilized), and on which side, or select unknown. Depending on how much eartip was removed at surgery time, these can be very hard to see.</p>
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
<p>Fourth, select whether the cat is wearing a collar (yes, no, or unknown).</p>
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
<p>Lastly, select the cat's sex if it is obvious what the cat's sex is (male, female, or unknown).  A photo could contain an unsterilized male cat's rear end where you can see testicles, or a photo of an unsterilized female could show visible mammary gland development or depict a mother with her kittens.  Most cats will be labeled as unknown sex.</p>
<table width="80%" border="0" align="center" cellpadding="10" cellspacing="0">
  <tr>
    <td width="551" valign="top"><div align="center"><strong>Male - Testicles visible</strong></div></td>
    <td width="50" valign="top">&nbsp;</td>
    <td width="498" valign="top"><div align="center"><strong> Female - Nursing or with small kittens</strong></div></td>
  </tr>
  <tr>
    <td><div align="center"><img src="images/instructions_male.jpg" width="402" height="300" /></div></td>
    <td width="50">&nbsp;</td>
    <td><div align="center"><img src="images/instructions_female.jpg" width="402" height="300" /></div></td>
  </tr>
</table>
<p>&nbsp;</p>
<h2>Step 2: cat matching</h2>
<p>After saving a cat's attributes, our system will look for likely potential matches based on those attributes, showing you cats that are most likely to be a match.  Some cat submissions will have a match in the system, and some won't.  You decide if one of the potential matches is the same cat, or click to decide that the submission is a new cat to the system without a match.</p>
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
    <td valign="top"><div align="center">Remember that not every cat photo is going to be a great one, and sometimes you won't have the best view.  Try to do your best with the angle you have. </div></td>
  </tr>
  <tr>
    <td><div align="center"><img src="images/whattolookfor_longfur.jpg" width="307" height="250" /></div></td>
    <td width="50">&nbsp;</td>
    <td><div align="center"><img src="images/whattolookfor_backside.jpg" width="213" height="250" /></div></td>
  </tr>
</table>
<p>&nbsp;</p>
<h2>That's everything!</h2><p>We hope this is a fun and straightforward study.  If you have any questions, please email kitizenscience@gmail.com.</p>
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
