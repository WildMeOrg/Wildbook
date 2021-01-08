/*
 * Wildbook - A Mark-Recapture Framework
 * Copyright (C) 2011-2016 Jason Holmberg
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package org.ecocean.servlet;

import org.json.JSONObject;
import javax.servlet.http.HttpServlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.json.JSONObject;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Properties;
import java.net.URL;
import org.ecocean.*;



/* some ReCAPTCHA-related stuff .... note that this has some useful public utility functions, as well as is
   a servlet which can do standalone verification */
public class ReCAPTCHA extends HttpServlet {
    private final static String ATTRIBUTE_PASSED = "reCAPTCHA-passed";

    public void init(ServletConfig config) throws ServletException {
        super.init(config);
    }


    public void doOptions(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        ServletUtilities.doOptions(request, response);
    }

    //please note this sets session attribute "reCAPTCHA-passed" (boolean) so that can be used directly or (prefered) by .sessionIsHuman()
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setHeader("Access-Control-Allow-Origin", "*");  //allow us stuff from localhost
        response.setContentType("application/json");
        PrintWriter out = response.getWriter();
        out.println(jsonResults(request, request.getParameter("recaptchaValue")));
        out.close();
    }

    public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setHeader("Access-Control-Allow-Origin", "*");  //allow us stuff from localhost
        response.setContentType("application/json");
        JSONObject j = ServletUtilities.jsonFromHttpServletRequest(request);
        PrintWriter out = response.getWriter();
        out.println((j == null) ? null : jsonResults(request, j.optString("recaptchaValue", null)));
        out.close();
    }

    private String jsonResults(HttpServletRequest request, String recaptchaValue) {
        JSONObject rtn = new JSONObject("{\"success\": false}");
        if ((request == null) || (recaptchaValue == null)) {
            rtn.put("error", "recaptchaValue not set (or bad request)");
            return rtn.toString();
        }
        boolean valid = captchaIsValid(ServletUtilities.getContext(request), recaptchaValue, request.getRemoteAddr());
        request.getSession().setAttribute(ATTRIBUTE_PASSED, valid);
        rtn.put("valid", valid);
        rtn.put("success", true);
        return rtn.toString();
    }

    /* see webapps/captchaExample.jsp for implementation */
    //note: this only handles single-widget (per page) ... if we need multiple, will have to extend things here
    public static String captchaWidget(HttpServletRequest request) {
        return captchaWidget(request, null, null);
    }
    // params & tagAttributes, see:  https://developers.google.com/recaptcha/docs/display#config
    public static String captchaWidget(HttpServletRequest request, String params, String tagAttributes) {
        String context = ServletUtilities.getContext(request);
        Properties recaptchaProps = ShepherdProperties.getProperties("recaptcha.properties", "", context);
        if (recaptchaProps == null) return "<div class=\"error captcha-error captcha-missing-properties\">Unable to get captcha settings.</div>";
        String siteKey = recaptchaProps.getProperty("siteKey");
        String secretKey = recaptchaProps.getProperty("secretKey");  //really dont need this here
        if ((siteKey == null) || (secretKey == null)) return "<div class=\"error captcha-error captcha-missing-key\">Unable to get captcha key settings.</div>";
        return "<script>function recaptchaCompleted() { return (grecaptcha && grecaptcha.getResponse(0)); }</script>\n" +
            "<script src='https://www.google.com/recaptcha/api.js" + ((params == null) ? "" : "?" + params) + "' async defer></script>\n" +
            "<div class=\"g-recaptcha\" data-sitekey=\"" + siteKey + "\" " + ((tagAttributes == null) ? "" : tagAttributes) + "></div>";
    }

    //  https://developers.google.com/recaptcha/docs/verify
    //note: catcha can only be tested once.... see sessionIsHuman() below for sustained verification
    public static boolean captchaIsValid(HttpServletRequest request) {
        return captchaIsValid(ServletUtilities.getContext(request), request.getParameter("g-recaptcha-response"), request.getRemoteAddr());
    }
    public static boolean captchaIsValid(String context, String uresp, String remoteIP) {
        if (context == null) context = "context0";
        Properties recaptchaProps = ShepherdProperties.getProperties("recaptcha.properties", "", context);
        if (recaptchaProps == null) {
            System.out.println("WARNING: no recaptcha.properties for captchaIsValid(); failing");
            return false;
        }
        String siteKey = recaptchaProps.getProperty("siteKey");  //really dont need this here
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
        cdata.put("remoteip", remoteIP);  //i guess this is technically optional (so we dont care if null?)
        cdata.put("response", uresp);
        JSONObject gresp = null;
        try {
            gresp = RestClient.post(new URL("https://www.google.com/recaptcha/api/siteverify"), cdata);
        } catch (Exception ex) {
            System.out.println("WARNING: exception calling captcha api in captchaIsValid(); failing: " + ex.toString());
            return false;
        }
        if (gresp == null) {  //would this ever happen?
            System.out.println("WARNING: null return from captcha api in captchaIsValid(); failing");
            return false;
        }
        System.out.println("INFO: captchaIsValid() api call returned: " + gresp.toString());
        boolean valid = gresp.optBoolean("success", false);
        return valid;
    }

    /*
        this does best guess at "are we human"? based on one of two things:
        1. is the user logged in?  if so: YES
        2. if not, did they previously pass ReCAPTCHA (based on session attribute)?
    */
    public static boolean sessionIsHuman(HttpServletRequest request) {
        if (!AccessControl.isAnonymous(request)) return true;
        if (request.getSession().getAttribute(ATTRIBUTE_PASSED) == null) return false;
        Boolean passed = (Boolean)request.getSession().getAttribute(ATTRIBUTE_PASSED);
System.out.println("-------------> sessionIsHuman() session attribute = " + passed);
        return passed;
    }
}
