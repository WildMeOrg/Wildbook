package org.ecocean.servlet;


import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpSession;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.authc.UsernamePasswordToken;
import java.io.IOException;
import java.io.PrintWriter;
import org.ecocean.Shepherd;
import org.ecocean.Util;
import org.ecocean.User;
import org.ecocean.SystemValue;
import org.json.JSONObject;
import org.json.JSONArray;


public class LoginUserJson extends HttpServlet {
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
    }

    public void doOptions(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        ServletUtilities.doOptions(request, response);
    }

    public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        handleRequest(request, response, _parseUrl(request, Util.stringToJSONObject(request.getParameter("content"))));
    }
    public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        JSONObject payload = new JSONObject();
        try {
            payload = ServletUtilities.jsonFromHttpServletRequest(request);
        } catch (Exception ex) {
            _log("failed to parse json payload from request: " + ex.toString());
        }
        handleRequest(request, response, _parseUrl(request, payload));
    }

    //this will get /class/id from the url and massage it into json (which will take overwrite values from inJson if they exist)
    private JSONObject _parseUrl(final HttpServletRequest request, JSONObject inJson) {
        if (request.getPathInfo() == null) return inJson;
        if (inJson == null) inJson = new JSONObject();
        String[] parts = request.getPathInfo().split("/");  //dont forget has leading / like:  "/class/id"
        if (parts.length > 1) inJson.put("class", parts[1]);
        if (parts.length > 2) inJson.put("id", parts[2]);
        inJson.put("login", request.getParameter("login"));
        inJson.put("password", request.getParameter("password"));
        return inJson;
    }

    private void handleRequest(HttpServletRequest request, HttpServletResponse response, JSONObject payload) throws ServletException, IOException {
        response.setHeader("Access-Control-Allow-Origin", "*");  //allow us stuff from localhost
        String context = ServletUtilities.getContext(request);
        String instanceId = Util.generateUUID();
        String httpMethod = request.getMethod();

        if (payload == null) payload = new JSONObject();
        payload.put("_queryString", request.getQueryString());
        boolean debug = (payload.optBoolean("_debug", false) || ((request.getQueryString() != null) && request.getQueryString().matches(".*_debug.*")));

        if (debug) _log(instanceId, "payload: " + payload.toString());
        if (payload.optString("class", "__FAIL__").equals("login")) {  //special case
            handleLogin(request, response, payload, instanceId, context);
            return;
        }
        if (payload.optString("class", "__FAIL__").equals("logout")) {  //special case
            handleLogout(request, response, payload, instanceId, context);
            return;
        }

        JSONObject rtn = new JSONObject();
        rtn.put("success", false);
/*
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("LoginUserJson.handleContent");
        myShepherd.commitDBTransaction();
        myShepherd.closeDBTransaction();
*/


        rtn.put("__instanceId", instanceId);
        if (debug) {
            _log(instanceId, "rtn: " + rtn.toString());
            JSONObject jbug = new JSONObject();
            jbug.put("payload", payload);
            jbug.put("timestamp", System.currentTimeMillis());
            jbug.put("remoteHost", ServletUtilities.getRemoteHost(request));
            jbug.put("method", httpMethod);
            jbug.put("queryString", request.getQueryString());
            jbug.put("pathInfo", request.getPathInfo());
            rtn.put("_debug", jbug);
        }
        response.setContentType("application/javascript");
        PrintWriter out = response.getWriter();
        out.println(rtn.toString());
        out.close();
    }
    private void handleLogin(HttpServletRequest request, HttpServletResponse response, JSONObject payload, String instanceId, String context) throws ServletException, IOException {
        if ((payload == null) || (context == null)) throw new IOException("invalid paramters");
        JSONObject rtn = new JSONObject();
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("LoginUserJson.handleLogin");
        response.setContentType("application/javascript");
        PrintWriter out = response.getWriter();

        rtn.put("success", false);
        User user = myShepherd.getUser(payload.optString("login", null));
        if (user == null) {
            _log(instanceId, "invalid login with payload=" + payload);
            rtn.put("message", "access denied");
            response.setStatus(401);
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
            out.println(rtn.toString());
            out.close();
            return;
        }
        //potentially could do something like 429 for too many tries, 4XX for acct disabled, etc.

        //do the actual login...
        try {
            UsernamePasswordToken token = new UsernamePasswordToken(user.getUsername(),
                ServletUtilities.hashAndSaltPassword(payload.optString("password", Util.generateUUID()), user.getSalt()) );
            Subject subject = SecurityUtils.getSubject();			
            subject.login(token);
        } catch (Exception ex) {
            _log(instanceId, "invalid login with payload=" + payload + "; threw " + ex.toString());
            rtn.put("message", "access denied");
            response.setStatus(401);
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
            out.println(rtn.toString());
            out.close();
            return;
        }

/*   FIXME
		  	if((CommonConfiguration.getProperty("showUserAgreement",context)!=null)&&(CommonConfiguration.getProperty("userAgreementURL",context)!=null)&&(CommonConfiguration.getProperty("showUserAgreement",context).equals("true"))&&(!user.getAcceptedUserAgreement())){
*/
        JSONObject surv = SystemValue.getJSONObject(myShepherd, "survey_response_phase3_" + user.getUUID());
        rtn.put("submittedSurvey", !(surv == null));
        //rtn.put("needsUserAgreement", false);
        rtn.put("id", user.getUUID());
        rtn.put("previousLogin", user.getLastLogin());
        rtn.put("success", true);
        _log(instanceId, "successful login user=" + user);
        user.setLastLogin(System.currentTimeMillis());
        myShepherd.commitDBTransaction();
        myShepherd.closeDBTransaction();
        response.setContentType("application/javascript");
        out.println(rtn.toString());
        out.close();
    }
    private void handleLogout(HttpServletRequest request, HttpServletResponse response, JSONObject payload, String instanceId, String context) throws ServletException, IOException {
        JSONObject rtn = new JSONObject();
            //see:  http://jsecurity.org/api/index.html?org/jsecurity/web/DefaultWebSecurityManager.html
        Subject subject = SecurityUtils.getSubject();
        if (subject != null) subject.logout();
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        response.setContentType("application/javascript");
        PrintWriter out = response.getWriter();
        rtn.put("success", true);
        out.println(rtn.toString());
        out.close();
    }

    private void _log(String msg) {
        _log("-", msg);
    }
    private void _log(String id, String msg) {
        System.out.println("[LoginUserJson:" + id + "] " + msg);
    }

}
  
  
