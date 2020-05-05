<%@ page contentType="text/plain; charset=utf-8" 
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
    Role role = new Role(username, "cat_walk_volunteer");
    role.setContext(myShepherd.getContext());
    myShepherd.getPM().makePersistent(role);
    return user;
}

%><%
String context = ServletUtilities.getContext(request);
Shepherd myShepherd = new Shepherd(context);
myShepherd.setAction("remote.jsp");
myShepherd.beginDBTransaction();

/*
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
*/

///// first lets see if survey is complete:

        JSONObject rtn = new JSONObject();
        rtn.put("success", false);

        String errorMessage = "Unknown error message";
        String[] fields = new String[]{"cat_volunteer", "have_cats", "disability", "citsci", "age", "retired", "gender", "ethnicity", "education", "how_hear"};
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
            errorMessage = String.join(", ", errors);
            rtn.put("error", errorMessage);
            out.println(rtn.toString());
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
            return;
        }

        boolean reg_terms = !(request.getParameter("agree-terms") == null);
        String reg_username = request.getParameter("username");
        String reg_email = request.getParameter("email");
        String reg_password1 = request.getParameter("password1");
        String reg_password2 = request.getParameter("password2");
        String key = request.getParameter("key");

        User user = null;
        String wantKey = org.ecocean.media.AssetStore.hexStringSHA256("k1tsc1:" + reg_username);
        System.out.println("key=[" + key + "] vs wantKey=[" + wantKey + "] on username=[" + reg_username + "]");

        boolean ok = ((key != null) && key.equals(wantKey));
        if (!ok) errorMessage = "Invalid response";
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
            rtn.put("error", errorMessage);
            out.println(rtn.toString());
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
            return;
        }


            myShepherd.getPM().makePersistent(user);
            System.out.println("[INFO] remote.jsp created " + user);
///if we get this far, save survey too
            String surv_key = "survey_response_phase3_" + user.getUUID();
            SystemValue.set(myShepherd, surv_key, resp);
            rtn.put("success", true);
            rtn.put("userId", user.getUUID());
            rtn.put("username", user.getUsername());
            out.println(rtn.toString());
            myShepherd.commitDBTransaction();
            myShepherd.closeDBTransaction();

%>
