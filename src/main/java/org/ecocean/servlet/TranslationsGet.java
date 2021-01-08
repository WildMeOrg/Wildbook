package org.ecocean.servlet;

import org.ecocean.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.json.JSONObject;
import org.json.JSONException;

import java.io.*;
import java.util.Properties;
import java.util.Set;

public class TranslationsGet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    public void init(ServletConfig config) throws ServletException {
        super.init(config);
    }

    public void doOptions(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        ServletUtilities.doOptions(request, response);
    }

    public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        doPost(request, response);
    }

    public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Access-Control-Allow-Origin", "*");
        PrintWriter out = null;
        try {
            out = response.getWriter();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        System.out.println("==> In TranslationsGet Servlet ");
        String context= ServletUtilities.getContext(request);
        JSONObject res = new JSONObject();
        JSONObject j = ServletUtilities.jsonFromHttpServletRequest(request);

        try {
            String propsName = j.optString("propsName", null);
            String langCode=ServletUtilities.getLanguageCode(request);
            if (Util.stringExists(propsName)) {
                if (!Util.stringExists(langCode)) {
                    langCode = "en";
                } 
                Properties props = ShepherdProperties.getProperties(propsName, langCode, context);
                if (props!=null&&!props.isEmpty()) {
                    Set<Object> keys = props.keySet();
                    for (Object keyOb : keys) {
                        String key = (String) keyOb;
                        res.put(key, props.getProperty(key));
                    }
                } else {
                    addErrorMessage(res, "TranslationGet: No properties file named "+propsName+" was found for langCode "+langCode+", or no properties were defined in it.");
                    response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                }  
            } else {
                addErrorMessage(res, "TranslationGet: No name was provided for a properties file to get.");
                response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            }  

            if (out!=null) {
                response.setStatus(HttpServletResponse.SC_OK);
                out.println(res);
                out.close();
            }

        } catch (NullPointerException npe) {
            npe.printStackTrace();
            addErrorMessage(res, "TranslationGet: NullPointerException npe while getting translations.");
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } catch (JSONException je) {
            je.printStackTrace();
            addErrorMessage(res, "TranslationGet: JSONException je while getting translations.");
          response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } catch (Exception e) {
            e.printStackTrace();
            addErrorMessage(res, "TranslationGet: Exception e while getting translations.");
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } finally {
            if (out!=null) {
                out.println(res);
                out.close();
            }
        }
    }

    private void addErrorMessage(JSONObject res, String error) {
        res.put("error", error);
    }


}
