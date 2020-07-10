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
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import javax.jdo.Query;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import org.ecocean.Shepherd;
import org.ecocean.ShepherdRO;
import org.ecocean.Util;
import org.ecocean.SystemLog;
import org.ecocean.Occurrence;
import org.ecocean.CommonConfiguration;
import org.ecocean.User;
import org.ecocean.Role;
import org.ecocean.Organization;
import org.ecocean.security.Collaboration;
import org.ecocean.configuration.*;
import org.json.JSONObject;
import org.json.JSONArray;
import java.util.Iterator;
import java.util.Collection;
import java.lang.reflect.Method;


public class RestServletV2 extends HttpServlet {
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

        //first handle special cases (where arg is NOT a classname)
        if (payload.optString("class", "__FAIL__").equals("login")) {
            handleLogin(request, response, payload, instanceId, context);
            return;
        }
        if (payload.optString("class", "__FAIL__").equals("logout")) {
            handleLogout(request, response, payload, instanceId, context);
            return;
        }
        if (payload.optString("id", "__FAIL__").equals("list")) {
            handleList(request, response, payload, instanceId, context);
            return;
        }
        if (payload.optString("class", "__FAIL__").equals("configuration")) {
            handleConfiguration(request, response, payload, instanceId, context);
            return;
        }

        JSONObject rtn = new JSONObject();
        rtn.put("success", false);

        try {
            JSONObject result = handleGetObject(request, response, payload, instanceId, context);
            if (result == null) {
                rtn.put("success", false);
            } else {
                rtn.put("result", result);
                rtn.put("success", true);
            }
        } catch (Exception ex) {
            rtn.put("message", _rtnMessage("error", payload, ex.toString()));
        }
/*
        String id = payload.optString("id", null);
        if (id == null) {
            rtn.put("message", _rtnMessage("error"));
        } else {
        }
*/


        rtn.put("transactionId", instanceId);
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

    private JSONObject handleGetObject(HttpServletRequest request, HttpServletResponse response, JSONObject payload, String instanceId, String context) throws ServletException, IOException {
        String id = payload.optString("id", null);
        if (id == null) throw new IOException("null id value");
        String cls = payload.optString("class", null);
        if (cls == null) throw new IOException("null class value");
        JSONObject rtn = null;
        ShepherdRO myShepherd = new ShepherdRO(context);
        myShepherd.setAction("RestServletV2.handleGetObject");
        myShepherd.beginDBTransaction();

        switch (cls) {
            case "org.ecocean.Occurrence":
                //Occurrence occ = myShepherd.getPM().getObjectById(Occurrence.class, id);
                Occurrence occ = myShepherd.getOccurrence(id);
                if (occ != null) {
                    try {
                        // TODO make a generic way to do "sizeable" expansion here
                        rtn = new JSONObject();
                        rtn.put("id", occ.getId());
                        rtn.put("version", occ.getVersion());
                        rtn.put("_fixme", true);
                    } catch (Exception ex) {
                        myShepherd.rollbackDBTransaction();
                        myShepherd.closeDBTransaction();
                        throw new IOException("JSONConversion - " + ex.toString());
                    }
                }
                break;

            case "org.ecocean.User":
                User user = myShepherd.getUserByUUID(id);
                if (user != null) {
                    try {
                        rtn = Util.toggleJSONObject(user.uiJson(request, true));
                        rtn.put("lastLogin", user.getLastLogin());
                        rtn.put("version", user.getVersion());
                        rtn.remove("uuid");
                        rtn.remove("organizations");
                        rtn.put("id", user.getUUID());
                        rtn.put("userURL", user.getUserURL());
                        rtn.put("acceptedUserAgreement", user.getAcceptedUserAgreement());
                        rtn.put("receiveEmails", user.getReceiveEmails());
                        rtn.put("sharing", user.hasSharing());
                        if (!Util.collectionIsEmptyOrNull(user.getOrganizations())) {
                            JSONArray jarr = new JSONArray();
                            for (Organization org : user.getOrganizations()) {
                                if (org == null) continue;
                                JSONObject jo = new JSONObject();
                                jo.put("id", org.getId());
                                jo.put("version", org.getVersion());
                                jarr.put(jo);
                            }
                            rtn.put("organizations", jarr);
                        }
                        if (user.getUserImage() != null) rtn.put("profileImageUrl", "/" + CommonConfiguration.getDataDirectoryName(context) + "/users/" + user.getUsername() + "/" + user.getUserImage().getFilename());
                    } catch (org.datanucleus.api.rest.orgjson.JSONException ex) {
                        myShepherd.rollbackDBTransaction();
                        myShepherd.closeDBTransaction();
                        throw new IOException("JSONConversion - " + ex.toString());
                    }
                }
                break;

            case "org.ecocean.Organization":
                Organization org = myShepherd.getOrganization(id);
                if (org != null) {
                    rtn = org.toJSONObject();
                    rtn.put("version", org.getVersion());
                }
                break;

            default:
                myShepherd.rollbackDBTransaction();
                myShepherd.closeDBTransaction();
                throw new IOException("bad class");
        }
/*

        String jdo = "SELECT FROM " + className;
///TODO set fetchDepth = 0 or whatever to make fast
        Query query = myShepherd.getPM().newQuery("JDOQL", jdo);
        Collection c = (Collection) (query.execute());
        Iterator it = c.iterator();
        while (it.hasNext()) {
            Object obj = it.next();
            String id = null;
            Long version = null;
            try {
                Method m = obj.getClass().getMethod("getId", new Class[0]);
                id = (String)m.invoke(obj);
                m = obj.getClass().getMethod("getVersion", new Class[0]);
                version = (Long)m.invoke(obj);
            } catch (Exception ex) {
                System.out.println("handleList threw " + ex.toString());
            }
            if (id == null) break;  //we dont try others cuz if this one failed, they all likely will!
            JSONObject j = new JSONObject();
            j.put("id", id);
            j.put("version", version);
            rtn.put(j);
        }
        query.closeAll();
*/
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
        return rtn;
    }

    private void handleLogin(HttpServletRequest request, HttpServletResponse response, JSONObject payload, String instanceId, String context) throws ServletException, IOException {
        if ((payload == null) || (context == null)) throw new IOException("invalid paramters");
        JSONObject rtn = new JSONObject();
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("RestServletV2.handleLogin");
        response.setContentType("application/javascript");
        PrintWriter out = response.getWriter();

        rtn.put("success", false);
        rtn.put("transactionId", instanceId);
        User user = myShepherd.getUserByWhatever(payload.optString("login", null));
        if (user == null) {
            _log(instanceId, "invalid login with payload=" + payload);
            rtn.put("message", _rtnMessage("access_denied"));
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
            rtn.put("message", _rtnMessage("access_denied"));
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
        rtn.put("needsUserAgreement", false);
        rtn.put("previousLogin", user.getLastLogin());
        rtn.put("success", true);
        rtn.put("message", _rtnMessage("success"));
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
        rtn.put("transactionId", instanceId);
        rtn.put("message", _rtnMessage("success"));
        out.println(rtn.toString());
        out.close();
    }

/*
    if payload.id exists, this is considered a GET of that value.  otherwise, payload *keys* will be considered ids, with values
    representing what to SET on those ids.
*/
    private void handleConfiguration(HttpServletRequest request, HttpServletResponse response, JSONObject payload, String instanceId, String context) throws ServletException, IOException {
        if ((payload == null) || (context == null)) throw new IOException("invalid paramters");
        if (payload.optString("_queryString").equals("tree")) payload.put("tree", true);
        payload.remove("class");
        payload.remove("_queryString");
        boolean isAdmin = request.isUserInRole("admin");
        JSONObject rtn = new JSONObject();
        rtn.put("success", false);
        rtn.put("transactionId", instanceId);
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("RestServletV2.handleConfiguration");
        myShepherd.beginDBTransaction();
        response.setContentType("application/javascript");
        PrintWriter out = response.getWriter();

        String id = payload.optString("id", null);
        if (id != null) {  //get value
            Configuration conf = ConfigurationUtil.getConfiguration(myShepherd, id);
            JSONObject meta = conf.getMeta();
            if (id.matches("^__bundle_\\w+")) {
                JSONObject bundle = readConfigBundle(id.substring(9));
                if (bundle == null) {
                    rtn.put("message", _rtnMessage("invalid_bundle_id"));
                } else {
                    rtn.put("bundleId", id);
                    JSONArray arr = bundle.optJSONArray("bundle");
                    if (arr != null) {
                        JSONArray kids = new JSONArray();
                        for (int i = 0 ; i < arr.length() ; i++) {
                            String bid = arr.optString(i, null);
                            if (bid == null) continue;
                            conf = ConfigurationUtil.getConfiguration(myShepherd, bid);
                            JSONObject kid = confGetTree(conf, isAdmin);
                            if (kid != null) kids.put(kid);
                        }
                        rtn.put("children", kids);
                    }
                    rtn.put("success", true);
                }
            } else if (!conf.isValid(meta) && payload.optBoolean("tree", false) && conf.hasChildren()) {
                Util.mergeJSONObjects(rtn, confGetTree(conf, isAdmin));
                rtn.put("success", true);
            } else if (!conf.isValid(meta)) {
                JSONObject jerr = new JSONObject();
                jerr.put("id", id);
                rtn.put("message", _rtnMessage("invalid_configuration_id", jerr));
            } else if (conf.isPrivate(meta) && !isAdmin) {
                JSONObject jerr = new JSONObject();
                jerr.put("id", id);
                rtn.put("message", _rtnMessage("access_denied_configuration", jerr));
                response.setStatus(401);
            } else {
                rtn.put("success", true);
                Util.mergeJSONObjects(rtn, __confJSONObject(conf, meta, isAdmin));
            }
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
            out.println(rtn.toString());
            out.close();
            return;
        }

        if (!isAdmin) {
            _log(instanceId, "invalid config set access with payload=" + payload);
            rtn.put("message", _rtnMessage("access_denied"));
            response.setStatus(401);
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
            out.println(rtn.toString());
            out.close();
            return;
        }

        List<String> updated = new ArrayList<String>();
        List<Configuration> updatedConfs = new ArrayList<Configuration>();
rtn.put("_payload", payload);

        try {
            for (Object k : payload.keySet()) {
                String key = (String)k;
                if (key.equals("foo")) throw new org.ecocean.DataDefinitionException("fake foo blah");
                Configuration conf = ConfigurationUtil.setConfigurationValue(myShepherd, key, payload.get(key));
                updatedConfs.add(conf);
                _log(instanceId, ">>>> SET key=" + key + " <= " + payload.get(key) + " => " + conf);
                rtn.put("success", true);
                updated.add(key);
            }
        } catch (Exception ex) {
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
            rtn.put("message", _rtnMessage("configuration_set_error", null, ex.toString()));
            _log(instanceId, "ERROR - rolling back db transaction due to exception on SET operation: " + ex.toString());
            out.println(rtn.toString());
            out.close();
            return;
        }

        myShepherd.commitDBTransaction();
        myShepherd.closeDBTransaction();
        //easiest way to update ROOT caches (let them reload when needed) now that we know we are persisted
        for (Configuration conf : updatedConfs) {
            conf.resetRootCache();
        }
        rtn.put("updated", new JSONArray(updated));
        rtn.put("message", _rtnMessage("success"));
        out.println(rtn.toString());
        out.close();
    }

    //this is just utility to turn conf into a JSONObject for both single and tree modes
    private JSONObject __confJSONObject(Configuration conf, JSONObject meta, boolean isAdmin) {
        if (conf == null) return null;
        JSONObject rtn = new JSONObject();
        if (meta == null) meta = conf.getMeta();
        if (conf.isPrivate(meta) && !isAdmin) return null;
        if (!conf.isValid(meta)) return null;
        rtn.put("id", conf.getId());
        if (conf.isPrivate(meta)) rtn.put("private", true);
        if (conf.hasValue()) {
            rtn.put("value", conf.getContent().get(ConfigurationUtil.VALUE_KEY));
        } else if (meta.has("defaultValue")) {
            rtn.put("valueNotSet", true);
            rtn.put("usingDefault", true);
            rtn.put("value", meta.get("defaultValue"));
        } else {
            rtn.put("valueNotSet", true);
            rtn.put("message", _rtnMessage("configuration_no_value"));
        }
        return rtn;
    }

    private JSONObject confGetTree(Configuration conf, boolean isAdmin) {
        JSONObject me = __confJSONObject(conf, null, isAdmin);
        if ((conf == null) || !conf.hasChildren()) return me;
        if (me == null) {  //means i am not valid, but i do have kids
            me = new JSONObject();
            me.put("id", conf.getId());
            me.put("groupingOnly", true);
        }
        JSONArray kids = new JSONArray();
        for (String key : conf.getChildKeys()) {
            String kidId = conf.getId() + ConfigurationUtil.ID_DELIM + key;
//System.out.println(key + " => " + conf.getContent());
            Configuration kconf = new Configuration(kidId, ((conf.getContent() == null) ? null : conf.getContent().optJSONObject(key)));
//System.out.println(key + " => " + kidId + " ===> " + kconf);
            JSONObject ktree = confGetTree(kconf, isAdmin);
            if (ktree != null) kids.put(ktree);
        }
        if (kids.length() > 0) me.put("children", kids);
        return me;
    }

    private void handleList(HttpServletRequest request, HttpServletResponse response, JSONObject payload, String instanceId, String context) throws ServletException, IOException {
        String className = payload.optString("class", null);
        if (className == null) throw new ServletException("empty class name");
        JSONArray rtn = new JSONArray();
        ShepherdRO myShepherd = new ShepherdRO(context);
        myShepherd.setAction("RestServletV2.handleList");

        if (className.equals("org.ecocean.security.Collaboration")) {
            handleListCollaboration(myShepherd, response);
            return;
        }
        if (className.equals("org.ecocean.Role")) {
            handleListRole(myShepherd, response);
            return;
        }

        String jdo = "SELECT FROM " + className;
///TODO set fetchDepth = 0 or whatever to make fast
        Query query = myShepherd.getPM().newQuery("JDOQL", jdo);
        Collection c = (Collection) (query.execute());
        Iterator it = c.iterator();
        while (it.hasNext()) {
            Object obj = it.next();
            String id = null;
            Long version = null;
            try {
                Method m = obj.getClass().getMethod("getId", new Class[0]);
                id = (String)m.invoke(obj);
                m = obj.getClass().getMethod("getVersion", new Class[0]);
                version = (Long)m.invoke(obj);
            } catch (Exception ex) {
                System.out.println("handleList threw " + ex.toString());
            }
            if (id == null) break;  //we dont try others cuz if this one failed, they all likely will!
            JSONObject j = new JSONObject();
            j.put("id", id);
            j.put("version", version);
            rtn.put(j);
        }
        query.closeAll();
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
        response.setContentType("application/javascript");
        PrintWriter out = response.getWriter();
        out.println(rtn.toString());
        out.close();
    }

    private void handleListCollaboration(Shepherd myShepherd, HttpServletResponse response) throws ServletException, IOException {
        JSONArray jarr = new JSONArray();
        String jdo = "SELECT FROM org.ecocean.security.Collaboration";
        Query query = myShepherd.getPM().newQuery("JDOQL", jdo);
        Collection c = (Collection) (query.execute());
        Iterator it = c.iterator();
        while (it.hasNext()) {
            Object obj = it.next();
            Collaboration collab = (Collaboration)obj;
            JSONObject jc = new JSONObject();
            jc.put("version", collab.getDateTimeCreated());
            jc.put("state", collab.getState());
            jc.put("_legacyId", collab.getUsername1() + ":" + collab.getUsername2());
            jc.put("id", Util.stringToUUID(collab.getUsername1() + ":" + collab.getUsername2()));
            User u1 = myShepherd.getUser(collab.getUsername1());
            User u2 = myShepherd.getUser(collab.getUsername2());
            if ((u1 == null) || (u2 == null)) {
                jc.put("error", "invalid username");
            } else {
                JSONArray uarr = new JSONArray();
                uarr.put(u1.getId());
                uarr.put(u2.getId());
                jc.put("userIds", uarr);
            }
            jarr.put(jc);
        }
        query.closeAll();
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
        response.setContentType("application/javascript");
        PrintWriter out = response.getWriter();
        out.println(jarr.toString());
        out.close();
    }

    private void handleListRole(Shepherd myShepherd, HttpServletResponse response) throws ServletException, IOException {
        String jdo = "SELECT FROM org.ecocean.Role";
        Map<String,Set<String>> rmap = new HashMap<String,Set<String>>();
        Query query = myShepherd.getPM().newQuery("JDOQL", jdo);
        Collection c = (Collection) (query.execute());
        Iterator it = c.iterator();
        while (it.hasNext()) {
            Object obj = it.next();
            Role role = (Role)obj;
            User user = myShepherd.getUser(role.getUsername());
            if (user == null) continue;
            if (!rmap.containsKey(role.getRolename())) rmap.put(role.getRolename(), new HashSet<String>());
            rmap.get(role.getRolename()).add(user.getUUID());
        }
        JSONArray rtn = new JSONArray();
        for (String rname : rmap.keySet()) {
            JSONObject r = new JSONObject();
            r.put("name", rname);
            r.put("id", Util.stringToUUID(rname));
            r.put("users", new JSONArray(rmap.get(rname)));
            r.put("version", 0);
            rtn.put(r);
        }
        query.closeAll();
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
        response.setContentType("application/javascript");
        PrintWriter out = response.getWriter();
        out.println(rtn);
        out.close();
    }

    private JSONObject _rtnMessage(String key, JSONObject args, String details) {
        if (key == null) return null;
        JSONObject m = new JSONObject();
        m.put("key", key);
        if (args != null) m.put("args", args);
        if (details != null) m.put("details", details);
        return m;
    }
    private JSONObject _rtnMessage(String key, JSONObject args) {
        return _rtnMessage(key, args, null);
    }
    private JSONObject _rtnMessage(String key) {
        return _rtnMessage(key, null, null);
    }

    // for bundleName=setup, actual file would be: config-json/__bundle_setup.json
    public static JSONObject readConfigBundle(String bundleName) {
        String fname = "/__bundle_" + bundleName + ".json";
        File bfile = new File(ConfigurationUtil.dirOverride() + fname);
        if (!bfile.exists()) bfile = new File(ConfigurationUtil.dir() + fname);
        if (!bfile.exists()) return null;
        return ConfigurationUtil.readJson(bfile);
    }

    private void _log(String msg) {
        _log("-", msg);
    }
    private void _log(String id, String msg) {
        SystemLog.log("[RestServletV2:" + id + "] " + msg);
    }

}
  
  
