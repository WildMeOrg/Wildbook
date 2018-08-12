/*
 * The Shepherd Project - A Mark-Recapture Framework
 * Copyright (C) 2011 Jason Holmberg
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

/* this creates a javascript output that contains a bunch of useful data for javascript given this context/language */
package org.ecocean.servlet;

import org.ecocean.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.lang.reflect.Field;

import java.io.*;
import java.util.*;

import org.ecocean.security.SocialAuth;
import org.ecocean.servlet.ReCAPTCHA;
import org.ecocean.identity.IBEISIA;

import org.w3c.dom.Document;
import com.google.gson.Gson;

public class JavascriptGlobals extends HttpServlet {


  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }


  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }

  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    
    //FIX-prevent caching
    response.setHeader("Cache-Control", "no-cache"); //Forces caches to obtain a new copy of the page from the origin server
    response.setHeader("Cache-Control", "no-store"); //Directs caches not to store the page under any circumstance
    response.setDateHeader("Expires", 0); //Causes the proxy cache to see the page as "stale"
    response.setHeader("Pragma", "no-cache"); //HTTP 1.0 backward compatibility
    
    String context="context0";
    context = ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);
    myShepherd.setAction("JavascriptGlobals.class1");
    
		String username = ((request.getUserPrincipal() == null) ? "" : request.getUserPrincipal().getName());

		String langCode = ServletUtilities.getLanguageCode(request);
		//Properties props = new Properties();
		//props = ShepherdProperties.getProperties("collaboration.properties", langCode, context);

		HashMap rtn = new HashMap();

		rtn.put("context", context);
		rtn.put("username", username);
                rtn.put("sessionIsHuman", ReCAPTCHA.sessionIsHuman(request));
		rtn.put("langCode", langCode);
		rtn.put("baseUrl", request.getContextPath());
		rtn.put("rootDir", (new File(getServletContext().getRealPath("/")).getParentFile()).toString());
		rtn.put("dataUrl", "/" + CommonConfiguration.getDataDirectoryName(context));
                rtn.put("validEmailRegexPattern", Util.validEmailRegexPattern());

		HashMap props = new HashMap();
		HashMap lang = new HashMap();


		//lang.put("collaboration", ShepherdProperties.getProperties("collaboration.properties", langCode, context));
		lang.put("visualMatcher", ShepherdProperties.getProperties("visualMatcher.properties", langCode, context));

		lang.put("collaboration", ShepherdProperties.getProperties("collaboration.properties", langCode, context));


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
			//HashMap ap = access.permissions(cls.getName(), request);
			//defn.put("permissions", ap);
			defn.put("permissions", access.permissions(cls.getName(), request));
			classDefn.put(cls.getName(), defn);
		}
		rtn.put("classDefinitions", classDefn);

    //TODO we could do this for all sorts of property files too?
    Properties authprops = SocialAuth.authProps(context);
    if (authprops != null) {
        for (String pn : authprops.stringPropertyNames()) {
            propvalToHashMap(pn, authprops.getProperty(pn), rtn);
        }
    }


    HashMap uploader = new HashMap();
    String s3key = CommonConfiguration.getProperty("s3upload_accessKeyId", context);
    if (s3key == null) {
        uploader.put("type", "local");
    } else {
        uploader.put("type", "s3direct");
        uploader.put("s3_accessKeyId", s3key);
        uploader.put("s3_secretAccessKey", CommonConfiguration.getProperty("s3upload_secretAccessKey", context));
        uploader.put("s3_region", CommonConfiguration.getProperty("s3upload_region", context));
        uploader.put("s3_bucket", CommonConfiguration.getProperty("s3upload_bucket", context));
    }

    rtn.put("uploader", uploader);

    LinkedHashMap<String,String> kw = new LinkedHashMap<String,String>();
    myShepherd.beginDBTransaction();
    Iterator<Keyword> keywords = myShepherd.getAllKeywords();
    while (keywords.hasNext()) {
        Keyword k = keywords.next();
        kw.put(k.getIndexname(), k.getReadableName());
    }
    myShepherd.rollbackDBTransaction();
    myShepherd.closeDBTransaction();
    rtn.put("keywords", kw);

    rtn.put("iaStatus", IBEISIA.iaStatus(request));

    response.setContentType("text/javascript");
    response.setCharacterEncoding("UTF-8");
    String js = "//JavascriptGlobals\nvar wildbookGlobals = " + new Gson().toJson(rtn) + "\n\n";
    PrintWriter out = response.getWriter();
		out.println(js);
    out.close();
  }


//wildbookGlobals.properties.lang.collaboration.invitePromptOne



    public void propvalToHashMap(String name, String val, HashMap h) {
//System.out.println("name->" + name);
        if (name.equals("secret")) return;  //TODO **totally** hactacular, but we dont want social secret keys sent out -- maybe pass in optional blacklist???
        if (h == null) h = new HashMap();
        int i = name.indexOf(".");
        if (i < 0) {
            h.put(name, val);
            return;
        }
        String key = name.substring(0,i);
//System.out.println("HASH key="+key);
        if (h.get(key) == null) h.put(key, new HashMap());
/* TODO handle case where prop file might have foo.bar = 1 and then foo.bar.baz = 2 */
        HashMap sub = (HashMap)h.get(key);
        propvalToHashMap(name.substring(i+1), val, sub);
    }


}
  
  
