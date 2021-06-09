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
import java.util.Enumeration;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import org.ecocean.Shepherd;
import org.ecocean.ShepherdRO;
import org.ecocean.Util;
import org.ecocean.SystemLog;
import org.ecocean.UserValue;
import org.ecocean.MarkedIndividual;
import org.ecocean.Encounter;
import org.ecocean.Occurrence;
import org.ecocean.Taxonomy;
import org.ecocean.CommonConfiguration;
import org.ecocean.customfield.CustomFieldDefinition;
import org.ecocean.customfield.CustomFieldException;
import org.ecocean.User;
import org.ecocean.Role;
import org.ecocean.Organization;
import org.ecocean.security.Collaboration;
import org.ecocean.configuration.*;
import org.ecocean.api.ApiHttpServlet;
import org.ecocean.api.ApiCustomFields;
import org.ecocean.api.query.QueryParser;
import org.ecocean.api.ApiValueException;
import org.ecocean.api.ApiDeleteCascadeException;
import org.json.JSONObject;
import org.json.JSONArray;
import java.util.Iterator;
import java.util.Collection;
import java.lang.reflect.Method;


public class RestServletV2 extends ApiHttpServlet {
    public static String USER_ROLENAME_ADMIN = "admin";

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
            payload = ServletUtilities.jsonAnyFromHttpServletRequest(request);
System.out.println("######>>>>>> payload=" + payload);
        } catch (Exception ex) {
            SystemLog.error("failed to parse json payload from request {}", this, ex);
        }
        handleRequest(request, response, _parseUrl(request, payload));
    }
    public void doPatch(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        doPost(request, response);  //handled via request.getMethod()
    }
    public void doDelete(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        handleDelete(request, response);
    }

    //this will get /class/id from the url and massage it into json (which will take overwrite values from inJson if they exist)
    private JSONObject _parseUrl(final HttpServletRequest request, JSONObject inJson) {
        if (request.getPathInfo() == null) return inJson;
        if (inJson == null) inJson = new JSONObject();
        String[] parts = request.getPathInfo().split("/");  //dont forget has leading / like:  "/class/id"
        if (parts.length > 1) inJson.put("class", parts[1]);
        if (parts.length > 2) inJson.put("id", parts[2]);

        //normalize special ?args as part of the json payload (to allow either way to work)
        Enumeration<String> allParams = request.getParameterNames();
        JSONObject dets = new JSONObject();
        while (allParams.hasMoreElements()) {
            String par = allParams.nextElement();
            if (!par.startsWith("detail-")) continue;
            dets.put(par.substring(7), request.getParameter(par));
        }
        if (dets.length() > 0) inJson.put("detailLevel", dets);

        return inJson;
    }

    private void handleRequest(HttpServletRequest request, HttpServletResponse response, JSONObject payload) throws ServletException, IOException {
        response.setHeader("Access-Control-Allow-Origin", "*");  //allow us stuff from localhost
        response.setCharacterEncoding("UTF-8");
        String context = ServletUtilities.getContext(request);
        String instanceId = Util.generateUUID();
        String httpMethod = request.getMethod();

        if (payload == null) payload = new JSONObject();
        payload.put("_queryString", request.getQueryString());
        boolean debug = (payload.optBoolean("_debug", false) || ((request.getQueryString() != null) && request.getQueryString().matches(".*_debug.*")));

        SystemLog.debug("RestServlet.handleRequest() instance={} method={} payload={}", instanceId, httpMethod, payload.toString());

        if ("PATCH".equals(httpMethod)) {
            handlePatch(request, response, payload, instanceId, context);
            return;
        }

        //first handle special cases (where arg is NOT a classname)
        if (payload.optString("class", "__FAIL__").equals("login")) {
            handleLogin(request, response, payload, instanceId, context);
            return;
        }
        if (payload.optString("class", "__FAIL__").equals("logout")) {
            handleLogout(request, response, payload, instanceId, context);
            return;
        }
        if (payload.optString("class", "__FAIL__").equals("UserValue")) {
            handleUserValue(request, response, payload, instanceId, context);
            return;
        }
        if (payload.optString("id", "__FAIL__").equals("list")) {
            handleList(request, response, payload, instanceId, context);
            return;
        }
        if (payload.optString("class", "__FAIL__").startsWith("configuration")) {
            handleConfiguration(request, response, payload, instanceId, context);
            return;
        }
        if (payload.optString("class", "__FAIL__").startsWith("init")) {
            handleInit(request, response, payload, instanceId, context);
            return;
        }
        if (payload.optJSONObject("query") != null) {
            QueryParser.handleQuery(request, response, payload, instanceId, context);
            return;
        }

        //now we handle generic POST (aka make new thing)
        if ("POST".equals(httpMethod)) {
            handlePost(request, response, payload, instanceId, context);
            return;
        }

        //from here, assumed to be GET
        JSONObject rtn = new JSONObject();
        rtn.put("success", false);

        try {
            JSONObject result = handleGetObject(request, response, payload, instanceId, context);
            if (result == null) {
                response.setStatus(404);
                rtn.put("message", _rtnMessage("not_found"));
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
            SystemLog.debug("RestServlet.handleRequest() instance={} return rtn={}", instanceId, rtn.toString());
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
        String rtnS = rtn.toString();
        response.setContentLength(rtnS.getBytes("UTF-8").length);
        out.println(rtnS);
        out.close();
    }

    private JSONObject handleGetObject(HttpServletRequest request, HttpServletResponse response, JSONObject payload, String instanceId, String context) throws ServletException, IOException {
        String id = payload.optString("id", null);
        if (id == null) throw new IOException("null id value");
        String cls = payload.optString("class", null);
        if (cls == null) throw new IOException("null class value");
        JSONObject rtn = null;
        //ShepherdRO myShepherd = new ShepherdRO(context);
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("RestServletV2.handleGetObject");
        myShepherd.beginDBTransaction();

        switch (cls) {
            case "org.ecocean.Occurrence":
                Occurrence occ = myShepherd.getOccurrence(id);
                if (occ != null) {
                    try {
                        rtn = occ.asApiJSONObject(payload.optJSONObject("detailLevel"));
                    } catch (Exception ex) {
                        myShepherd.rollbackDBTransaction();
                        myShepherd.closeDBTransaction();
                        throw new IOException("JSONConversion - " + ex.toString());
                    }
                }
                break;

            case "org.ecocean.Encounter":
                Encounter enc = myShepherd.getEncounter(id);
                if (enc != null) {
                    try {
                        rtn = enc.asApiJSONObject(payload.optJSONObject("detailLevel"));
                    } catch (Exception ex) {
                        myShepherd.rollbackDBTransaction();
                        myShepherd.closeDBTransaction();
                        throw new IOException("JSONConversion - " + ex.toString());
                    }
                }
                break;

            case "org.ecocean.MarkedIndividual":
                MarkedIndividual ind = myShepherd.getMarkedIndividual(id);
                if (ind != null) {
                    try {
                        rtn = ind.asApiJSONObject(payload.optJSONObject("detailLevel"));
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

    //kinda hacky case to deal with UserValues
    private JSONObject handleUserValue(HttpServletRequest request, HttpServletResponse response, JSONObject payload, String instanceId, String context) throws ServletException, IOException {
        if ((payload == null) || (context == null)) throw new IOException("invalid paramters");
        String key = payload.optString("id", null);
        if (key == null) throw new IOException("handleUserValue got null key");
        payload.remove("id");
        payload.remove("class");
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("RestServletV2.handleUserValue");
        response.setContentType("application/javascript");
        PrintWriter out = response.getWriter();
        JSONObject rtn = new JSONObject();
        rtn.put("key", key);
        rtn.put("success", false);
        rtn.put("transactionId", instanceId);
        //TODO FIXME handle actual user, duh?
        JSONObject val = null;
        if ("POST".equals(request.getMethod())) {
            UserValue.set(myShepherd, key, payload);
            val = payload;
        } else {
            val = UserValue.getJSONObject(myShepherd, key);
        }
        if (val != null) {
            rtn.put("success", true);
            rtn.put("response", val);
        }
        myShepherd.commitDBTransaction();
        myShepherd.closeDBTransaction();
        String rtnS = rtn.toString();
        response.setContentLength(rtnS.getBytes("UTF-8").length);
        out.println(rtnS);
        out.close();
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
            SystemLog.warn("RestServlet.handleLogin() instance={} invalid login with payload={}", instanceId, payload);
            rtn.put("message", _rtnMessage("access_denied"));
            response.setStatus(401);
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
            String rtnS = rtn.toString();
            response.setContentLength(rtnS.getBytes("UTF-8").length);
            out.println(rtnS);
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
            SystemLog.warn("RestServlet.handleLogin() instance={} invalid login with payload={} threw {}", instanceId, payload, ex.toString());
            rtn.put("message", _rtnMessage("access_denied"));
            response.setStatus(401);
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
            String rtnS = rtn.toString();
            response.setContentLength(rtnS.getBytes("UTF-8").length);
            out.println(rtnS);
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
        SystemLog.info("RestServlet.handleLogin() instance={} successful login user={}", instanceId, user);
        user.setLastLogin(System.currentTimeMillis());
        myShepherd.commitDBTransaction();
        myShepherd.closeDBTransaction();
        response.setContentType("application/javascript");
        String rtnS = rtn.toString();
        response.setContentLength(rtnS.getBytes("UTF-8").length);
        out.println(rtnS);
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
        String rtnS = rtn.toString();
        response.setContentLength(rtnS.getBytes("UTF-8").length);
        out.println(rtnS);
        out.close();
    }

/*
    if payload.id exists, this is considered a GET of that value.  otherwise, payload *keys* will be considered ids, with values
    representing what to SET on those ids.
*/
    private void handleConfiguration(HttpServletRequest request, HttpServletResponse response, JSONObject payload, String instanceId, String context) throws ServletException, IOException {
        if ((payload == null) || (context == null)) throw new IOException("invalid paramters");
        if (payload.optString("_queryString").equals("tree")) payload.put("tree", true);
SystemLog.debug("RestServlet.handleConfiguration() instance={} payload={}", instanceId, payload);
        boolean definition = "configurationDefinition".equals(payload.optString("class"));
        payload.remove("class");
        payload.remove("_queryString");
        boolean isAdmin = request.isUserInRole(USER_ROLENAME_ADMIN);
        JSONObject rtn = new JSONObject();
        rtn.put("success", false);
        rtn.put("transactionId", instanceId);
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("RestServletV2.handleConfiguration");
        myShepherd.beginDBTransaction();
        response.setContentType("application/javascript");
        PrintWriter out = response.getWriter();

        String id = payload.optString("id", null);

        /*
            this is the case where we POST to a *single* configuration like:  configuration/foo.bar
            we allow two cases here:
                (1) if value is non-json (since we can only pass in json!), we look for _value key
                (2) if that does not exist, we take entire payload as value
        */
        if ("POST".equals(request.getMethod()) && (id != null)) {
            payload.remove("id");
            JSONObject newPayload = new JSONObject();
            if (payload.has("_value")) {
                newPayload.put(id, payload.get("_value"));
            } else {
                newPayload.put(id, payload);
            }
            id = null;
            payload = newPayload;
            SystemLog.debug("RestServlet.handleConfiguration() instance={} POST single generated payload={}", instanceId, payload);
        }

        if (id != null) {  //get value
            Configuration conf = ConfigurationUtil.getConfiguration(myShepherd, id);
            JSONObject meta = conf.getMeta();
            if (id.matches("^__bundle_\\w+")) {
                JSONObject bundle = readConfigBundle(id.substring(9));
                if (bundle == null) {
                    rtn.put("message", _rtnMessage("invalid_bundle_id"));
                    response.setStatus(400);
                } else {
                    long bversion = 0l;
                    JSONObject res = new JSONObject();
                    res.put("bundleId", id);
                    JSONArray arr = bundle.optJSONArray("bundle");
                    if (arr != null) {
                        JSONObject content = new JSONObject();
                        for (int i = 0 ; i < arr.length() ; i++) {
                            String bid = arr.optString(i, null);
                            if (bid == null) continue;
                            if (bid.equals("_system")) {
                                content.put("_system", Util.getSystemInfoJSONObject(myShepherd));
                                continue;
                            }
                            conf = ConfigurationUtil.getConfiguration(myShepherd, bid);
                            if (conf.getModified() > bversion) bversion = conf.getModified();
                            JSONObject kid = confGetTree(conf, isAdmin, definition, myShepherd);
                            if (kid != null) content.put(conf.getId(), kid);
                        }
                        res.put("configuration", content);
                        res.put("version", bversion);
                    }
                    rtn.put("response", res);
                    rtn.put("success", true);
                }
            } else if (!conf.isValid(meta) && payload.optBoolean("tree", false) && conf.hasChildren()) {
                rtn.put("response", confGetTree(conf, isAdmin, definition, myShepherd));
                rtn.put("success", true);
            } else if (!conf.isValid(meta)) {
                JSONObject jerr = new JSONObject();
                jerr.put("id", id);
                rtn.put("message", _rtnMessage("invalid_configuration_id", jerr));
                response.setStatus(400);
            } else if (conf.isPrivate(meta) && !isAdmin) {
                JSONObject jerr = new JSONObject();
                jerr.put("id", id);
                rtn.put("message", _rtnMessage("access_denied_configuration", jerr));
                response.setStatus(401);
            } else {
                rtn.put("success", true);
                rtn.put("response", __confJSONObject(conf, meta, isAdmin, definition, myShepherd));
            }
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
            String rtnS = rtn.toString();
            response.setContentLength(rtnS.getBytes("UTF-8").length);
            out.println(rtnS);
            out.close();
            return;
        }

/*  FIXME!!!!!!!!!!!!!!!!!  temp security hole for pre-houston testing  .. have at it, internet!
        if (definition || !isAdmin) {  //we dont set using configDefinition!
            SystemLog.warn("RestServlet.handleConfiguration() instance={} invalid config set access with payload={}", instanceId, payload);
            rtn.put("message", _rtnMessage("access_denied"));
            response.setStatus(401);
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
            out.println(rtn.toString());
            out.close();
            return;
        }
*/

        List<String> updated = new ArrayList<String>();
        List<Configuration> updatedConfs = new ArrayList<Configuration>();
rtn.put("_payload", payload);
        List<String> updatedCFD = new ArrayList<String>();

        try {
            for (Object k : payload.keySet()) {
                String key = (String)k;
                Configuration conf = null;
                if (key.startsWith("site.custom.customFields.")) {
                    String cname = key.substring(25);
                    JSONObject val = payload.optJSONObject(key);
                    if (val == null) throw new IOException(key + " is not a valid JSON object");
                    JSONArray defnArr = val.optJSONArray("definitions");
                    if (defnArr == null) {
                        SystemLog.info("RestServlet.handleConfiguration() instance={} key {} not passed a definitions array; skipping", instanceId, key);
                    } else {
                        for (int i = 0 ; i < defnArr.length() ; i++) {
                            JSONObject dj = defnArr.optJSONObject(i);
                            if (dj == null) throw new IOException(key + " was not passed valid JSON object at position " + i);
                            dj.put("className", "org.ecocean." + cname);
//System.out.println(key + "[" + i + "] => " + dj);
                            CustomFieldDefinition cfd = CustomFieldDefinition.updateCustomFieldDefinition(myShepherd, dj);  //throws Exception if badness
                            updatedCFD.add(cfd.getId());
                            //since this was added, we want to set the conf using *all cfds* for this key:
                        }
                    }
                    conf = ConfigurationUtil.setConfigurationValue(myShepherd, key, CustomFieldDefinition.getDefinitionsAsJSONObject(myShepherd, "org.ecocean." + cname));
                } else if (key.equals("site.species")) {
                    JSONArray setTaxs = new JSONArray();  //what we *actually* will set it to, if all goes well
                    JSONArray jtaxs = payload.optJSONArray(key);
                    if (jtaxs == null) throw new IOException(key + " requires array of Taxonomy");
                    for (int i = 0 ; i < jtaxs.length() ; i++) {
                        JSONObject jtx = jtaxs.optJSONObject(i);
                        if (jtx == null) throw new IOException(key + " has no valid Taxonomy object at offset " + i);
                        String txId = jtx.optString("id", null);
                        String txSN = jtx.optString("scientificName", null);
                        Taxonomy tx = null;
                        if (txId != null) tx = myShepherd.getTaxonomyById(txId);
                        if ((tx == null) && (txSN != null)) tx = myShepherd.getTaxonomy(txSN);
                        if (tx == null) {  //if we get here, we need to make one
                            if (txId != null) throw new IOException(key + " passed invalid Taxonomy id=" + txId);  //but not if we have an id!!
                            if (txSN == null) throw new IOException(key + " passed invalid Taxonomy scientificName");  //nor missing sciname
                            tx = new Taxonomy(txSN, jtx.optString("commonName", null));
                            int itis = jtx.optInt("itisTsn", -1);
                            if (itis > 0) tx.setItisTsn(itis);
                            List<String> cnames = new ArrayList<String>();
                            JSONArray cnjarr = jtx.optJSONArray("commonNames");
                            if (cnjarr != null) {
                                for (int cni = 0 ; cni < cnjarr.length() ; cni++) {
                                    String cn = cnjarr.optString(cni, null);
                                    if (cn != null) cnames.add(cn);
                                }
                            }
                            if (cnames.size() > 0) tx.setCommonNames(cnames);
                            myShepherd.getPM().makePersistent(tx);
                        }
                        setTaxs.put(tx.asApiJSONObject());
                    }
                    SystemLog.debug("site.species setTaxs={}", setTaxs);
                    conf = ConfigurationUtil.setConfigurationValue(myShepherd, key, setTaxs);  //now just set it, like any other

                } else {
                    conf = ConfigurationUtil.setConfigurationValue(myShepherd, key, payload.get(key));
                }
                updatedConfs.add(conf);
                SystemLog.debug(instanceId, ">>>> instance={} SET key={} <= {} => {}", instanceId, key, payload.get(key), conf);
                updated.add(key);
            }
            rtn.put("success", true);  //if we made it thru every key without exception
        } catch (Exception ex) {
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
            rtn.put("message", _rtnMessage("configuration_set_error", null, ex.toString()));
            SystemLog.error("RestServlet.handleConfiguration() instance={} rolling back db transaction due to exception on SET operation: {}", instanceId, ex.toString());
            String rtnS = rtn.toString();
            response.setContentLength(rtnS.getBytes("UTF-8").length);
            out.println(rtnS);
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
        rtn.put("updatedCustomFieldDefinitionIds", new JSONArray(updatedCFD));
        rtn.put("message", _rtnMessage("success"));
        String rtnS = rtn.toString();
        response.setContentLength(rtnS.getBytes("UTF-8").length);
        out.println(rtnS);
        out.close();
    }

    //this is just utility to turn conf into a JSONObject for both single and tree modes
    private JSONObject __confJSONObject(Configuration conf, JSONObject meta, boolean isAdmin, boolean definition, Shepherd myShepherd) {
        if (conf == null) return null;
        JSONObject rtn = new JSONObject();
        if (meta == null) meta = conf.getMeta();
        if (conf.isPrivate(meta) && !isAdmin) return null;
        if (!conf.isValid(meta)) return null;
        if (definition) return conf.toFrontEndJSONObject(myShepherd);
        rtn.put("id", conf.getId());
        if (conf.isPrivate(meta)) rtn.put("private", true);
        if (conf.hasValue()) {
            rtn.put("value", conf.getContent().get(ConfigurationUtil.VALUE_KEY));
        } else if (meta.has("defaultValue")) {
            rtn.put("valueNotSet", true);
            rtn.put("usingDefault", true);
            rtn.put("value", meta.get("defaultValue"));
        } else {
            //rtn.put("value", JSONObject.NULL);
            rtn.put("value", new JSONArray());  //ben wants this an empty array for easy of js
            rtn.put("valueNotSet", true);
            rtn.put("message", _rtnMessage("configuration_no_value"));
        }
        rtn.put("version", conf.getModified());
        return rtn;
    }

    private JSONObject confGetTree(Configuration conf, boolean isAdmin, boolean definition, Shepherd myShepherd) {
        JSONObject me = __confJSONObject(conf, null, isAdmin, definition, myShepherd);
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
            JSONObject ktree = confGetTree(kconf, isAdmin, definition, myShepherd);
            if (ktree != null) kids.put(ktree);
        }
        if (kids.length() > 0) me.put("children", kids);
        return me;
    }
    private void handleInit(HttpServletRequest request, HttpServletResponse response, JSONObject payload, String instanceId, String context) throws ServletException, IOException {
        if ((payload == null) || (context == null)) throw new IOException("invalid paramters");
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("RestServletV2.handleInit");
        myShepherd.beginDBTransaction();
        response.setContentType("application/javascript");
        PrintWriter out = response.getWriter();
        JSONObject rtn = new JSONObject();
        rtn.put("success", false);

        JSONObject initAdmin = payload.optJSONObject("admin_user_initialized");
        if (initAdmin != null) {
            List<String> admins = myShepherd.getAllUsernamesWithRolename(USER_ROLENAME_ADMIN);
            if (!Util.collectionIsEmptyOrNull(admins)) {
                myShepherd.rollbackDBTransaction();
                myShepherd.closeDBTransaction();
                rtn.put("message", _rtnMessage("init_admin_user_exists_error"));
            } else {
                User newAdmin = null;
                try {
                    newAdmin = User.createAdminUser(myShepherd, initAdmin.optString("username", null), initAdmin.optString("email", null), initAdmin.optString("password", null));
                } catch (IOException ex) {
                    ex.printStackTrace();
                    rtn.put("details", ex.toString());
                }
                if (newAdmin == null) {
                    myShepherd.rollbackDBTransaction();
                    myShepherd.closeDBTransaction();
                    rtn.put("message", _rtnMessage("init_admin_error"));
                } else {
                    rtn.put("success", true);
                    rtn.put("username", newAdmin.getUsername());
                    myShepherd.commitDBTransaction();
                    myShepherd.closeDBTransaction();
                }
            }
        }

        String rtnS = rtn.toString();
        response.setContentLength(rtnS.getBytes("UTF-8").length);
        out.println(rtnS);
        out.close();
    }

    private void handleList(HttpServletRequest request, HttpServletResponse response, JSONObject payload, String instanceId, String context) throws ServletException, IOException {
        String className = payload.optString("class", null);
        if (className == null) throw new ServletException("empty class name");
        JSONArray rtn = new JSONArray();
        //ShepherdRO myShepherd = new ShepherdRO(context);
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("RestServletV2.handleList");

        if (className.equals("org.ecocean.security.Collaboration")) {
            handleListCollaboration(myShepherd, response);
            return;
        }
        if (className.equals("org.ecocean.Role")) {
            handleListRole(myShepherd, response);
            return;
        }

        Long sinceVersion = null;
        if (request.getParameter("sinceVersion") != null) {
            try {
                sinceVersion = Long.parseLong(request.getParameter("sinceVersion"));
            } catch (NumberFormatException nex) {
                SystemLog.warn("could not parse passed sinceVersion=", nex);
            }
        }

        String jdo = "SELECT FROM " + className;
        if (sinceVersion != null) jdo += " WHERE version > " + sinceVersion.toString();
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
            j.put("version", (version == null) ? 0 : version);
            rtn.put(j);
        }
        query.closeAll();
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
        response.setContentType("application/javascript");
        PrintWriter out = response.getWriter();
        String rtnS = rtn.toString();
        response.setContentLength(rtnS.getBytes("UTF-8").length);
        out.println(rtnS);
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
        String rtnS = jarr.toString();
        response.setContentLength(rtnS.getBytes("UTF-8").length);
        out.println(rtnS);
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
        String rtnS = rtn.toString();
        response.setContentLength(rtnS.getBytes("UTF-8").length);
        out.println(rtnS);
        out.close();
    }

    //create a new object, yup
    private void handlePost(HttpServletRequest request, HttpServletResponse response, JSONObject payload, String instanceId, String context) throws ServletException, IOException {
        if ((payload == null) || (context == null)) throw new IOException("invalid paramters");
        JSONObject rtn = new JSONObject();
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("RestServletV2.handlePost");
        response.setContentType("application/javascript");
        PrintWriter out = response.getWriter();

        SystemLog.debug("POST ON PAYLOAD " + payload);
        rtn.put("success", false);
        rtn.put("transactionId", instanceId);

        String cls = payload.optString("class", null);
        if (cls == null) {
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
            SystemLog.error("RestServlet.handlePost() passed null class id, instance={}", instanceId);
            throw new IOException("RestServlet.handlePost() passed null class");
        }


        try {
            final String LOG_POST_SUCCESS = "RestServlet.handlePost() instance={} created={}";  
            if (cls.equals("org.ecocean.Occurrence")) {
                Occurrence occ = Occurrence.fromApiJSONObject(myShepherd, payload);
                myShepherd.getPM().makePersistent(occ);
                SystemLog.info(LOG_POST_SUCCESS, instanceId, occ);
                myShepherd.commitDBTransaction();
                rtn.put("success", true);
                rtn.put("result", occ.asApiJSONObject());   //TODO what detail to pass?
            } else if (cls.equals("org.ecocean.Encounter")) {
                throw new IOException("Encounter cannot be created via POST, only as part of Sightings POST or PATCH");  //DEX-286
                /*
                Encounter enc = Encounter.fromApiJSONObject(myShepherd, payload);
                myShepherd.getPM().makePersistent(enc);
                SystemLog.info(LOG_POST_SUCCESS, instanceId, enc);
                myShepherd.commitDBTransaction();
                rtn.put("success", true);
                rtn.put("result", enc.asApiJSONObject());   //TODO what detail to pass?
                */
            } else if (cls.equals("org.ecocean.MarkedIndividual")) {
                MarkedIndividual individual = MarkedIndividual.fromApiJSONObject(myShepherd, payload);
                myShepherd.getPM().makePersistent(individual);
                SystemLog.info(LOG_POST_SUCCESS, instanceId, individual);
                myShepherd.commitDBTransaction();
                rtn.put("success", true);
                rtn.put("result", individual.asApiJSONObject());   //TODO what detail to pass?
            } else {
                SystemLog.error("RestServlet.handlePost() passed invalid class {}, instance={}", cls, instanceId);
                rtn.put("message", _rtnMessage("invalid_class", payload));
                myShepherd.rollbackDBTransaction();
                response.setStatus(400);
            }
        } catch (ApiValueException ex) {
            SystemLog.error("RestServlet.handlePost() invalid value {}", ex.toString(), ex);
            rtn.put("message", _rtnMessage("error", payload, ex.toString()));
            rtn.put("errorFields", new JSONArray(ex.getFields()));
            myShepherd.rollbackDBTransaction();
            response.setStatus(601);
        } catch (Exception ex) {
            SystemLog.error("RestServlet.handlePost() threw exception {}", ex.toString(), ex);
            rtn.put("message", _rtnMessage("error", payload, ex.toString()));
            myShepherd.rollbackDBTransaction();
            response.setStatus(400);
        }

        myShepherd.closeDBTransaction();
        String rtnS = rtn.toString();
        response.setContentLength(rtnS.getBytes("UTF-8").length);
        out.println(rtnS);
        out.close();
        return;
    }

    //basically change some object - see https://tools.ietf.org/html/rfc6902
    private void handlePatch(HttpServletRequest request, HttpServletResponse response, JSONObject payload, String instanceId, String context) throws ServletException, IOException {
        if ((payload == null) || (context == null)) throw new IOException("invalid paramters");
        JSONObject rtn = new JSONObject();
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("RestServletV2.handlePatch");
        myShepherd.beginDBTransaction();
        response.setContentType("application/javascript");
        PrintWriter out = response.getWriter();

        SystemLog.debug("PATCH ON PAYLOAD " + payload);
        rtn.put("success", false);
        rtn.put("transactionId", instanceId);

        //these are for verifying that cascade is allowed; using same headers as DELETE does
        payload.put(ApiCustomFields.KEY_DELETE_CASCADE_INDIVIDUAL, Util.booleanNotFalse(request.getHeader("x-allow-delete-cascade-individual")));
        payload.put(ApiCustomFields.KEY_DELETE_CASCADE_SIGHTING, Util.booleanNotFalse(request.getHeader("x-allow-delete-cascade-sighting")));

        String id = payload.optString("id", null);  //not sure id is a real thing (due to path), but definitely not required
        String cls = payload.optString("class", null);
        if (cls == null) {
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
            SystemLog.error("RestServlet.handlePatch() passed null class, instance={}", instanceId);
            throw new IOException("RestServlet.handlePatch() passed null class");
        }

        if (cls.equals("configuration")) {  //right now, patch only really supported on customFields
            try {
                rtn.put("result", ConfigurationUtil.apiPatch(myShepherd, payload));
                //now we update configuration for all the custom values cuz we dont know which we just changed.  :/
                String[] classes = {"Encounter", "Occurrence", "MarkedIndividual"};
                for (String cfcls : classes) {
                    String key = "site.custom.customFields." + cfcls;
                    ConfigurationUtil.setConfigurationValue(myShepherd, key, CustomFieldDefinition.getDefinitionsAsJSONObject(myShepherd, "org.ecocean." + cfcls));
                }
                ConfigurationUtil.resetValueCache("site");
            } catch (CustomFieldException ex) {
                SystemLog.error("RestServlet.handlePatch() on configuration threw {}", ex.toString(), ex);
                rtn.put("message", _rtnMessage("error", payload, ex.toString()));
                if (ex.getNumValues() > 0) {
                    rtn.put("hasValues", true);
                    rtn.put("numValues", ex.getNumValues());
                }
                response.setStatus(602);
                myShepherd.rollbackDBTransaction();
                myShepherd.closeDBTransaction();
                String rtnS = rtn.toString();
                response.setContentLength(rtnS.getBytes("UTF-8").length);
                out.println(rtnS);
                out.close();
                return;
            } catch (Exception ex) {
                SystemLog.error("RestServlet.handlePatch() on configuration threw {}", ex.toString(), ex);
                rtn.put("message", _rtnMessage("error", payload, ex.toString()));
                response.setStatus(603);
                myShepherd.rollbackDBTransaction();
                myShepherd.closeDBTransaction();
                String rtnS = rtn.toString();
                response.setContentLength(rtnS.getBytes("UTF-8").length);
                out.println(rtnS);
                out.close();
                return;
            }

        } else if (cls.equals("org.ecocean.Occurrence")) {
            //TODO handle case where *no* id set, and patch path is multiple
            Occurrence occ = myShepherd.getOccurrence(id);
            if (occ == null) {
                SystemLog.warn("RestServlet.handlePatch() instance={} invalid occurrence with payload={}", instanceId, payload);
                rtn.put("message", _rtnMessage("not_found"));
                response.setStatus(404);
                myShepherd.rollbackDBTransaction();
                myShepherd.closeDBTransaction();
                out.println(rtn.toString());
                out.close();
                return;
            } else {
                try {
                    JSONArray patchRes = occ.apiPatch(myShepherd, payload);  //this means *all* patches must succeed
                    if (occ.isJDODeleted()) {
                        JSONObject del = new JSONObject();
                        del.put("deletedSighting", id);
                        rtn.put("result", del);
                    } else {
                        rtn.put("result", occ.asApiJSONObject());
                    }
                    rtn.put("patchResults", patchRes);
                } catch (ApiValueException ex) {
                    SystemLog.error("RestServlet.handlePatch() invalid value {}", ex.toString(), ex);
                    rtn.put("message", _rtnMessage("error", payload, ex.toString()));
                    rtn.put("errorFields", new JSONArray(ex.getFields()));
                    response.setStatus(601);
                    myShepherd.rollbackDBTransaction();
                    myShepherd.closeDBTransaction();
                    String rtnS = rtn.toString();
                    response.setContentLength(rtnS.getBytes("UTF-8").length);
                    out.println(rtnS);
                    out.close();
                    return;
                } catch (ApiDeleteCascadeException ex) {
                    SystemLog.error("RestServlet.handlePatch() cascade conflict {}", ex.toString(), ex);
                    rtn.put("message", _rtnMessage("error", payload, ex.toString()));
                    response.setStatus(602);
                    myShepherd.rollbackDBTransaction();
                    myShepherd.closeDBTransaction();
                    String rtnS = rtn.toString();
                    response.setContentLength(rtnS.getBytes("UTF-8").length);
                    out.println(rtnS);
                    out.close();
                    return;
                } catch (Exception ex) {
                    SystemLog.error("RestServlet.handlePatch() generic exception {}", ex.toString(), ex);
                    rtn.put("message", _rtnMessage("error", payload, ex.toString()));
                    response.setStatus(500);
                    myShepherd.rollbackDBTransaction();
                    myShepherd.closeDBTransaction();
                    String rtnS = rtn.toString();
                    response.setContentLength(rtnS.getBytes("UTF-8").length);
                    out.println(rtnS);
                    out.close();
                    return;
                }
            }

        } else if (cls.equals("org.ecocean.Encounter")) {
            //TODO handle case where *no* id set, and patch path is multiple
            Encounter enc = myShepherd.getEncounter(id);
            if (enc == null) {
                SystemLog.warn("RestServlet.handlePatch() instance={} invalid encounter with payload={}", instanceId, payload);
                rtn.put("message", _rtnMessage("not_found"));
                response.setStatus(404);
                myShepherd.rollbackDBTransaction();
                myShepherd.closeDBTransaction();
                out.println(rtn.toString());
                out.close();
                return;
            } else {
                try {
                    JSONArray patchRes = enc.apiPatch(myShepherd, payload);  //this means *all* patches must succeed
                    rtn.put("result", enc.asApiJSONObject());
                    rtn.put("patchResults", patchRes);
                } catch (ApiValueException ex) {
                    SystemLog.error("RestServlet.handlePatch() invalid value {}", ex.toString(), ex);
                    rtn.put("message", _rtnMessage("error", payload, ex.toString()));
                    rtn.put("errorFields", new JSONArray(ex.getFields()));
                    response.setStatus(601);
                    myShepherd.rollbackDBTransaction();
                    myShepherd.closeDBTransaction();
                    String rtnS = rtn.toString();
                    response.setContentLength(rtnS.getBytes("UTF-8").length);
                    out.println(rtnS);
                    out.close();
                    return;
/*
                } catch (ApiDeleteCascadeException ex) {
                    SystemLog.error("RestServlet.handlePatch() cascade conflict {}", ex.toString(), ex);
                    rtn.put("message", _rtnMessage("error", payload, ex.toString()));
                    response.setStatus(602);
                    myShepherd.rollbackDBTransaction();
                    myShepherd.closeDBTransaction();
                    String rtnS = rtn.toString();
                    response.setContentLength(rtnS.getBytes("UTF-8").length);
                    out.println(rtnS);
                    out.close();
                    return;
*/
                } catch (Exception ex) {
                    SystemLog.error("RestServlet.handlePatch() generic exception {}", ex.toString(), ex);
                    rtn.put("message", _rtnMessage("error", payload, ex.toString()));
                    response.setStatus(500);
                    myShepherd.rollbackDBTransaction();
                    myShepherd.closeDBTransaction();
                    String rtnS = rtn.toString();
                    response.setContentLength(rtnS.getBytes("UTF-8").length);
                    out.println(rtnS);
                    out.close();
                    return;
                }
            }

        } else {
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
            SystemLog.error("RestServlet.handlePatch() passed invalid class '{}' (id '{}', instance={})", cls, id, instanceId);
            throw new IOException("RestServlet.handlePatch() passed invalid class " + cls);
        }

        //if we fall thru, we assume success!
        myShepherd.commitDBTransaction();
        myShepherd.closeDBTransaction();
        rtn.put("success", true);

        String rtnS = rtn.toString();
        response.setContentLength(rtnS.getBytes("UTF-8").length);
        out.println(rtnS);
        out.close();
        return;
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

    private void handleDelete(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setCharacterEncoding("UTF-8");
        String context = ServletUtilities.getContext(request);
        String instanceId = Util.generateUUID();
        JSONObject rtn = new JSONObject();
        response.setContentType("application/javascript");
        PrintWriter out = response.getWriter();
        rtn.put("success", false);
        rtn.put("transactionId", instanceId);

        String cls = null;
        String id = null;
        if (request.getPathInfo() != null) {
            String[] parts = request.getPathInfo().split("/");  //dont forget has leading / like:  "/class/id"
            if (parts.length > 1) cls = parts[1];
            if (parts.length > 2) id = parts[2];
        }
        if ((cls == null) || (id == null)) {
            rtn.put("message", _rtnMessage("invalid_class_or_id"));
            String rtnS = rtn.toString();
            response.setStatus(406);
            response.setContentLength(rtnS.getBytes("UTF-8").length);
            out.println(rtnS);
            out.close();
            return;
        }

        boolean allowCascadeIndividual = Util.booleanNotFalse(request.getHeader("x-allow-delete-cascade-individual"));
        boolean allowCascadeSighting = Util.booleanNotFalse(request.getHeader("x-allow-delete-cascade-sighting"));
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("RestServletV2.handleDelete");
        myShepherd.beginDBTransaction();

        Occurrence parentOcc = null;
        ApiCustomFields obj = null;  //takes care of *most* cases
        switch (cls) {
            case "org.ecocean.Encounter":
                obj = myShepherd.getEncounter(id);
                parentOcc = myShepherd.getOccurrence((Encounter)obj);
                break;
            case "org.ecocean.Occurrence":
                obj = myShepherd.getOccurrence(id);
                break;
            case "org.ecocean.MarkedIndividual":
                obj = myShepherd.getMarkedIndividual(id);
                break;
            default:
                myShepherd.rollbackDBTransaction();
                myShepherd.closeDBTransaction();
                JSONObject jerr = new JSONObject();
                jerr.put("id", id);
                jerr.put("class", cls);
                rtn.put("message", _rtnMessage("invalid_class", jerr));
                String rtnS = rtn.toString();
                response.setStatus(406);
                response.setContentLength(rtnS.getBytes("UTF-8").length);
                out.println(rtnS);
                out.close();
                return;
        }

        if (obj == null) {
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
            JSONObject jerr = new JSONObject();
            jerr.put("id", id);
            jerr.put("class", cls);
            rtn.put("message", _rtnMessage("not_found", jerr));
            String rtnS = rtn.toString();
            response.setStatus(404);
            response.setContentLength(rtnS.getBytes("UTF-8").length);
            out.println(rtnS);
            out.close();
            return;
        }

        try {
            obj.delete(myShepherd, allowCascadeSighting, allowCascadeIndividual);

        } catch (ApiDeleteCascadeException casc) {
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
            SystemLog.warn("RestServlet.handleDelete() failed on {} due to cascade {}", obj, casc);
            JSONObject jerr = new JSONObject();
            jerr.put("id", id);
            jerr.put("class", cls);
            rtn.put("message", _rtnMessage("cascade", jerr));
            rtn.put("details", casc.toString());
            String rtnS = rtn.toString();
            response.setStatus(603);
            response.setContentLength(rtnS.getBytes("UTF-8").length);
            out.println(rtnS);
            out.close();
            return;

        } catch (Exception ex) {
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
            SystemLog.error("RestServlet.handleDelete() failed on {}", obj, ex);
            throw ex;
        }
        SystemLog.info("RestServlet.handleDelete() deleted {} id={}, instance={}", cls, id, instanceId);

        if ((parentOcc != null) && parentOcc.isJDODeleted()) {
            JSONObject del = new JSONObject();
            del.put("deletedSighting", parentOcc.getId());
            rtn.put("result", del);
        }

        myShepherd.commitDBTransaction();
        myShepherd.closeDBTransaction();
        rtn.put("success", true);
        String rtnS = rtn.toString();
        response.setContentLength(rtnS.getBytes("UTF-8").length);
        out.println(rtnS);
        out.close();
    }

}
  
  
