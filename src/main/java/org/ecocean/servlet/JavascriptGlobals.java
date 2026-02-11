/* this creates a javascript output that contains a bunch of useful data for javascript given this context/language */
package org.ecocean.servlet;

import org.ecocean.*;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

import java.lang.reflect.Field;

import java.io.*;
import java.util.*;

import com.google.gson.Gson;

import org.ecocean.identity.IBEISIA;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.shepherd.core.ShepherdProperties;


public class JavascriptGlobals extends HttpServlet {
    public void init(ServletConfig config)
    throws ServletException {
        super.init(config);
    }

    public void doGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        doPost(request, response);
    }

    public void doPost(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        // FIX-prevent caching
        response.setHeader("Cache-Control", "no-cache"); // Forces caches to obtain a new copy of the page from the origin server
        response.setHeader("Cache-Control", "no-store"); // Directs caches not to store the page under any circumstance
        response.setDateHeader("Expires", 0); // Causes the proxy cache to see the page as "stale"
        response.setHeader("Pragma", "no-cache"); // HTTP 1.0 backward compatibility

        String context = "context0";
        context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("JavascriptGlobals.class1");

        String username = ((request.getUserPrincipal() ==
            null) ? "" : request.getUserPrincipal().getName());
        String langCode = ServletUtilities.getLanguageCode(request);
        String gtmKey = CommonConfiguration.getGoogleTagManagerKey(context);
        String gaId = CommonConfiguration.getGoogleAnalyticsId(context);
        String gMapKey = CommonConfiguration.getGoogleMapsKey(context);
        HashMap rtn = new HashMap();

        rtn.put("context", context);
        rtn.put("username", username);
        rtn.put("sessionIsHuman", ReCAPTCHA.sessionIsHuman(request));
        rtn.put("langCode", langCode);
        rtn.put("baseUrl", request.getContextPath());
        rtn.put("rootDir",
            (new File(getServletContext().getRealPath("/")).getParentFile()).toString());
        rtn.put("dataUrl", "/" + CommonConfiguration.getDataDirectoryName(context));
        rtn.put("validEmailRegexPattern", Util.validEmailRegexPattern());

        HashMap props = new HashMap();
        HashMap lang = new HashMap();

        lang.put("visualMatcher",
            ShepherdProperties.getProperties("visualMatcher.properties", langCode, context));

        lang.put("collaboration",
            ShepherdProperties.getProperties("collaboration.properties", langCode, context));

        props.put("lang", lang);
        rtn.put("properties", props);

        HashMap classDefn = new HashMap();

        // add all classes we want to access info about in the javascript world
        Class[] classes = new Class[3];
        classes[0] = org.ecocean.Encounter.class;
        classes[1] = org.ecocean.MarkedIndividual.class;
        classes[2] = org.ecocean.SinglePhotoVideo.class;

        ApiAccess access = new ApiAccess();
        for (Class cls : classes) {
            HashMap defn = new HashMap();
            Field[] fields = cls.getDeclaredFields();
            HashMap fhm = new HashMap();
            for (Field f : fields) {
                fhm.put(f.getName(), f.getType().getName());
            }
            defn.put("fields", fhm);
            defn.put("permissions", access.permissions(cls.getName(), request));
            classDefn.put(cls.getName(), defn);
        }
        rtn.put("classDefinitions", classDefn);

        HashMap uploader = new HashMap();
        uploader.put("type", "local");
        rtn.put("uploader", uploader);

        LinkedHashMap<String, String> kw = new LinkedHashMap<String, String>();
        myShepherd.beginDBTransaction();
        Iterator<Keyword> keywords = myShepherd.getAllKeywords();
        while (keywords.hasNext()) {
            Keyword k = keywords.next();
            kw.put(k.getIndexname(), k.getReadableName());
        }
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
        rtn.put("keywords", kw);
        rtn.put("gtmKey", gtmKey);
        rtn.put("gaId", gaId);
        rtn.put("gMapKey", gMapKey);

        // this might throw an exception in various ways, so we swallow them here
        try {
            rtn.put("iaStatus", IBEISIA.iaStatus(request));
        } catch (Exception ex) {
            System.out.println("WARNING: JavascriptGlobals iaStatus threw " + ex.toString());
            rtn.put("iaStatus", false);
        }
        response.setContentType("text/javascript");
        response.setCharacterEncoding("UTF-8");
        String js = "//JavascriptGlobals\nvar wildbookGlobals = " + new Gson().toJson(rtn) + "\n\n";
        PrintWriter out = response.getWriter();
        out.println(js);
        out.close();
    }


    public void propvalToHashMap(String name, String val, HashMap h) {
// System.out.println("name->" + name);
        if (name.equals("secret")) return; 
        if (h == null) h = new HashMap();
        int i = name.indexOf(".");
        if (i < 0) {
            h.put(name, val);
            return;
        }
        String key = name.substring(0, i);
// System.out.println("HASH key="+key);
        if (h.get(key) == null) h.put(key, new HashMap());
        HashMap sub = (HashMap)h.get(key);
        propvalToHashMap(name.substring(i + 1), val, sub);
    }
}
