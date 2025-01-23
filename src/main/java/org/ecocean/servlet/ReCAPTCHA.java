package org.ecocean.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.util.Properties;
import java.util.Map;
import java.util.HashMap;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import org.ecocean.*;
import org.json.JSONObject;

/*
   captcha-related functionality
   TODO: this should be eventually renamed captcha-agnostically, but for now code is being left in for ReCAPTCHA
   backwards compatibility.

   note that this has some useful public utility functions, as well as is a servlet which can do standalone
   verification
*/
public class ReCAPTCHA extends HttpServlet {
    private final static String ATTRIBUTE_PASSED = "captcha-passed";

    public void init(ServletConfig config)
    throws ServletException {
        super.init(config);
    }

    public void doOptions(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        ServletUtilities.doOptions(request, response);
    }

    // please note this sets session attribute "reCAPTCHA-passed" (boolean) so that can be used directly or (prefered) by .sessionIsHuman()
    public void doGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        response.setHeader("Access-Control-Allow-Origin", "*"); // allow us stuff from localhost
        response.setContentType("application/json");
        PrintWriter out = response.getWriter();
        String procaptchaValue = request.getParameter("procaptchaValue");
        if (procaptchaValue != null) {
            out.println(jsonResultsProcaptcha(request, procaptchaValue));
        } else {
            out.println(jsonResults(request, request.getParameter("recaptchaValue")));
        }
        out.close();
    }

    public void doPost(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        response.setHeader("Access-Control-Allow-Origin", "*"); // allow us stuff from localhost
        response.setContentType("application/json");
        JSONObject j = ServletUtilities.jsonFromHttpServletRequest(request);
        PrintWriter out = response.getWriter();
        String procaptchaValue = null;
        if (j != null) procaptchaValue = j.optString("procaptchaValue", null);
        boolean useEnterprise = (j != null) && j.optBoolean("useEnterprise", false);
        if (procaptchaValue != null) {
            out.println(jsonResultsProcaptcha(request, procaptchaValue));
        } else if (useEnterprise) {
            out.println(jsonResultsEnterprise(request, j.optString("recaptchaToken", null)));
        } else {
            out.println((j == null) ? null : jsonResults(request, j.optString("recaptchaValue", null)));
        }
        out.close();
    }

    private String jsonResults(HttpServletRequest request, String recaptchaValue) {
        JSONObject rtn = new JSONObject("{\"success\": false}");

        if ((request == null) || (recaptchaValue == null)) {
            rtn.put("error", "recaptchaValue not set (or bad request)");
            return rtn.toString();
        }
        boolean valid = captchaIsValid(ServletUtilities.getContext(request), recaptchaValue,
            ServletUtilities.getRemoteHost(request));
        request.getSession().setAttribute(ATTRIBUTE_PASSED, valid);
        rtn.put("valid", valid);
        rtn.put("success", true);
        return rtn.toString();
    }

    /* see webapps/captchaExample.jsp for implementation */
    // note: this only handles single-widget (per page) ... if we need multiple, will have to extend things here
    public static String captchaWidget(HttpServletRequest request) {
        return captchaWidget(request, null, null);
    }

    // params & tagAttributes, see:  https://developers.google.com/recaptcha/docs/display#config
    public static String captchaWidget(HttpServletRequest request, String params,
        String tagAttributes) {
        String context = ServletUtilities.getContext(request);
        Properties recaptchaProps = ShepherdProperties.getProperties("recaptcha.properties", "",
            context);

        if (recaptchaProps == null)
            return
                    "<div class=\"error captcha-error captcha-missing-properties\">Unable to get captcha settings.</div>";
        String siteKey = recaptchaProps.getProperty("siteKey");
        String secretKey = recaptchaProps.getProperty("secretKey"); // really dont need this here
        if ((siteKey == null) || (secretKey == null))
            return
                    "<div class=\"error captcha-error captcha-missing-key\">Unable to get captcha key settings.</div>";
        return
                "<script>function recaptchaCompleted() { return (grecaptcha && grecaptcha.getResponse(0)); }</script>\n"
                + "<script src='https://www.google.com/recaptcha/api.js" + ((params ==
                null) ? "" : "?" + params) + "' async defer></script>\n" +
                "<div class=\"g-recaptcha\" data-sitekey=\"" + siteKey + "\" " + ((tagAttributes ==
                null) ? "" : tagAttributes) + "></div>";
    }

    // https://developers.google.com/recaptcha/docs/verify
    // note: catcha can only be tested once.... see sessionIsHuman() below for sustained verification
    public static boolean captchaIsValid(HttpServletRequest request) {
        return captchaIsValid(ServletUtilities.getContext(request),
                request.getParameter("g-recaptcha-response"), ServletUtilities.getRemoteHost(request));
    }

    public static boolean captchaIsValid(String context, String uresp, String remoteIP) {
        if (context == null) context = "context0";
        Properties recaptchaProps = ShepherdProperties.getProperties("recaptcha.properties", "",
            context);
        if (recaptchaProps == null) {
            System.out.println("WARNING: no recaptcha.properties for captchaIsValid(); failing");
            return false;
        }
        String siteKey = recaptchaProps.getProperty("siteKey"); // really dont need this here
        String secretKey = recaptchaProps.getProperty("secretKey");
        if ((siteKey == null) || (secretKey == null)) {
            System.out.println("WARNING: could not determine keys for captchaIsValid(); failing");
            return false;
        }
        if (uresp == null) {
            System.out.println("WARNING: recaptcha value is null in captchaIsValid(); failing");
            return false;
        }
        JSONObject cdata = new JSONObject();
        cdata.put("secret", secretKey);
        cdata.put("remoteip", remoteIP); // i guess this is technically optional (so we dont care if null?)
        cdata.put("response", uresp);
        JSONObject gresp = null;
        try {
            gresp = RestClient.post(new URL("https://www.google.com/recaptcha/api/siteverify"),
                cdata);
        } catch (Exception ex) {
            System.out.println(
                "WARNING: exception calling captcha api in captchaIsValid(); failing: " +
                ex.toString());
            return false;
        }
        if (gresp == null) { // would this ever happen?
            System.out.println(
                "WARNING: null return from captcha api in captchaIsValid(); failing");
            return false;
        }
        System.out.println("INFO: captchaIsValid() api call returned: " + gresp.toString());
        boolean valid = gresp.optBoolean("success", false);
        return valid;
    }

    public static boolean captchaIsValidEnterprise(String context, String token, String remoteIP) {
        String EXPECTED_ACTION = "VALIDATE";
        if (context == null) context = "context0";
        Properties recaptchaProps = ShepherdProperties.getProperties("recaptcha.properties", "",
            context);
        if (recaptchaProps == null) {
            System.out.println("WARNING: no recaptcha.properties for captchaIsValid(); failing");
            return false;
        }
        String siteKey = recaptchaProps.getProperty("enterpriseSiteKey");
        String apiKey = recaptchaProps.getProperty("enterpriseApiKey");
        if ((siteKey == null) || (apiKey == null)) {
            System.out.println("WARNING: could not determine keys for captchaIsValid(); failing");
            return false;
        }
        if (token == null) {
            System.out.println("WARNING: recaptcha token is null in captchaIsValid(); failing");
            return false;
        }
        JSONObject cdata = new JSONObject();
        JSONObject cevent = new JSONObject();
        cevent.put("token", token);
        cevent.put("userIpAddress", remoteIP);
        cevent.put("siteKey", siteKey);
        cevent.put("expectedAction", EXPECTED_ACTION);
        cdata.put("event", cevent);
        JSONObject gresp = null;
        try {
            gresp = RestClient.postJSON(new URL("https://recaptchaenterprise.googleapis.com/v1/projects/wildme-dev/assessments?key=" + apiKey), cdata, null);
        } catch (Exception ex) {
            System.out.println(
                "WARNING: exception calling captcha api in captchaIsValid(); failing: " +
                ex.toString());
            return false;
        }
        if (gresp == null) { // would this ever happen?
            System.out.println(
                "WARNING: null return from captcha api in captchaIsValid(); failing");
            return false;
        }
        System.out.println("INFO: captchaIsValid() api call returned: " + gresp.toString());
        JSONObject riskAnalysis = gresp.optJSONObject("riskAnalysis");
        JSONObject tokenProperties = gresp.optJSONObject("tokenProperties");
        JSONObject event = gresp.optJSONObject("event");
        System.out.println(" > riskAnalysis " + riskAnalysis);
        System.out.println(" > tokenProperties " + tokenProperties);
        System.out.println(" > event " + event);
        if ((riskAnalysis == null) || (tokenProperties == null) || (event == null)) return false;
        if (riskAnalysis.optDouble("score", 0.0) < 0.5) return false;
        return true;
    }

    public static boolean captchaIsValidProcaptcha(String context, String token) {
        if (context == null) context = "context0";
        Properties captchaProps = ShepherdProperties.getProperties("captcha.properties", "", context);
        if (captchaProps == null) {
            System.out.println("WARNING: no captcha.properties for captchaIsValid(); failing");
            return false;
        }
        String secretKey = captchaProps.getProperty("procaptchaSecretKey");
        //String siteKey = captchaProps.getProperty("procaptchaSiteKey");
        if (secretKey == null) {
            System.out.println("WARNING: could not determine secretKey for captchaIsValid(); failing");
            return false;
        }
        if (token == null) {
            System.out.println("WARNING: captcha token is null in captchaIsValid(); failing");
            return false;
        }
        JSONObject cdata = new JSONObject();
        cdata.put("token", token);
        cdata.put("secret", secretKey);
        JSONObject resp = null;
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("User-Agent", "Wildbook/wildme.org");  // :( we need this or we get a 403 from their api
        try {
            resp = RestClient.postJSON(new URL("https://api.prosopo.io/siteverify"), cdata, headers);
        } catch (Exception ex) {
            System.out.println(
                "WARNING: exception calling captcha api in captchaIsValid(); failing: " +
                ex.toString());
            return false;
        }
        if (resp == null) { // would this ever happen?
            System.out.println(
                "WARNING: null return from captcha api in captchaIsValid(); failing");
            return false;
        }
        System.out.println("INFO: captchaIsValid() api call returned: " + resp.toString());
        return "ok".equals(resp.optString("status")) && resp.optBoolean("verified", false);
    }

    private String jsonResultsEnterprise(HttpServletRequest request, String recaptchaToken) {
        JSONObject rtn = new JSONObject("{\"success\": false}");

        if ((request == null) || (recaptchaToken == null)) {
            rtn.put("error", "recaptchaToken not set (or bad request)");
            return rtn.toString();
        }
        boolean valid = captchaIsValidEnterprise(ServletUtilities.getContext(request), recaptchaToken,
            ServletUtilities.getRemoteHost(request));
        request.getSession().setAttribute(ATTRIBUTE_PASSED, valid);
        rtn.put("valid", valid);
        rtn.put("success", true);
        return rtn.toString();
    }

    private String jsonResultsProcaptcha(HttpServletRequest request, String value) {
        JSONObject rtn = new JSONObject("{\"success\": false}");

        if ((request == null) || (value == null)) {
            rtn.put("error", "value not set (or bad request)");
            return rtn.toString();
        }
        boolean valid = captchaIsValidProcaptcha(ServletUtilities.getContext(request), value);
        request.getSession().setAttribute(ATTRIBUTE_PASSED, valid);
        rtn.put("valid", valid);
        rtn.put("success", true);
        return rtn.toString();
    }

    /*
        this does best guess at "are we human"? based on one of two things:
        1. is the user logged in?  if so: YES 2. if not, did they previously pass ReCAPTCHA (based on session attribute)?
     */
    public static boolean sessionIsHuman(HttpServletRequest request) {
        if (!AccessControl.isAnonymous(request)) return true;
        if (request.getSession().getAttribute(ATTRIBUTE_PASSED) == null) return false;
        Boolean passed = (Boolean)request.getSession().getAttribute(ATTRIBUTE_PASSED);
        System.out.println("-------------> sessionIsHuman() session attribute = " + passed);
        return passed;
    }
}
