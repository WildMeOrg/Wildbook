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

package org.ecocean.servlet;


import org.ecocean.CommonConfiguration;
import org.ecocean.Encounter;
import org.ecocean.Shepherd;
import org.ecocean.Util;
import org.ecocean.media.*;
import org.ecocean.identity.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.json.JSONObject;
import org.json.JSONArray;

import java.net.MalformedURLException;
import java.security.NoSuchAlgorithmException;
import java.security.InvalidKeyException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;


public class IAGateway extends HttpServlet {

  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }


  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    JSONObject res = new JSONObject("{\"success\": false, \"error\": \"unknown\"}");
    if (request.getParameter("getJobResult") != null) {
        try {
            res = IBEISIA.getJobResult(request.getParameter("getJobResult"));
        } catch (Exception ex) {
            throw new IOException(ex.toString());
        }
    }
    response.setContentType("text/plain");
    PrintWriter out = response.getWriter();
    out.println(res.toString());
    out.close();
  }




  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    String context = ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);

    response.setContentType("text/plain");
    PrintWriter out = response.getWriter();

    JSONObject j = ServletUtilities.jsonFromHttpServletRequest(request);
    JSONObject res = new JSONObject();

    if (j.optJSONArray("detect") != null) {
        ArrayList<MediaAsset> mas = new ArrayList<MediaAsset>();
        JSONArray ids = j.getJSONArray("detect");
        for (int i = 0 ; i < ids.length() ; i++) {
            int id = ids.optInt(i, 0);
System.out.println(id);
            if (id < 1) continue;
            MediaAsset ma = MediaAssetFactory.load(id, myShepherd);
            if (ma != null) mas.add(ma);
        }
        if (mas.size() > 0) {
            try {
                String baseUrl = CommonConfiguration.getServerURL(request, request.getContextPath());
                res.put("sendMediaAssets", IBEISIA.sendMediaAssets(mas));
                res.put("sendDetect", IBEISIA.sendDetect(mas, baseUrl));
            } catch (Exception ex) {
                throw new IOException(ex.toString());
            }
        }
    }

    res.put("_in", j);
    res.put("success", true);

    out.println(res.toString());
    out.close();
    //myShepherd.closeDBTransaction();
  }

}
  

