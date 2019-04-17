package org.ecocean.servlet;


import org.ecocean.CommonConfiguration;
import org.ecocean.Encounter;
import org.ecocean.Shepherd;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.media.AssetStore;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.*;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Collection;

import com.google.gson.*;

import org.json.JSONObject;
import org.json.JSONArray;

@MultipartConfig
public class MultipleSubmitAPI extends HttpServlet {
    static final long serialVersionUID = 1L;

    public void init(ServletConfig config) throws ServletException {
        super.init(config);
    }

    public void doOptions(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        ServletUtilities.doOptions(request, response);
    }

    // BEGIN ALL THE GETS -- (posts below)

    public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        System.out.println("Sent GET to MultipleSubmitAPI");
        String context = ServletUtilities.getContext(request);
        PrintWriter out = response.getWriter();
        response.setContentType("application/json");
        JSONObject rtn = new JSONObject();
        rtn.put("success", "false");
        try {
            String getLocations = request.getParameter("getLocations");
            if (getLocations!=null&&"true".equals(getLocations)) {
                rtn = getLocations(rtn, context);
            }
            rtn.put("success", "true");
        } catch (Exception e) {
            e.printStackTrace();
        }
        out.println(rtn);
        out.close();
    }
    
    private JSONObject getLocations(JSONObject rtn, String context) {        
        JSONArray rtnArr = new JSONArray();
        List<String> locs = new ArrayList<>();
        if (CommonConfiguration.getIndexedPropertyValues("locationID", context).size()>0) {
            System.out.println("Gonna try and return "+CommonConfiguration.getIndexedPropertyValues("locationID", context).size()+" location Ids.");
            locs = CommonConfiguration.getIndexedPropertyValues("locationID", context);
            for (String loc : locs) {
                rtnArr.put(loc);
            }
            rtn.put("locationIds", rtnArr);
        }      
        return rtn;
    }

    // BEGIN ALL THE POSTS

    public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        System.out.println("Sent POST to MultipleSubmitAPI");
        String context = ServletUtilities.getContext(request);
        PrintWriter out = response.getWriter();
        response.setContentType("application/json");
        JSONObject rtn = new JSONObject();
        rtn.put("success", "false");
        System.out.println("Recaptcha checked? : "+request.getParameter("recaptcha-checked"));
        System.out.println("PARAM NAMES:");

        String numEncStr = request.getParameter("number-encounters");
        int numEncs = 0;
        System.out.println("number-encounters: "+numEncStr);
        if (hasVal(numEncStr)) {
            numEncs = Integer.valueOf(numEncStr);
        }

        String jsonStr = request.getParameter("json-data");
        System.out.println("Sent JSON (as string) : "+jsonStr);
        JsonObject json = new JsonParser().parse(jsonStr).getAsJsonObject();
        System.out.println("Assert is JSONObject "+json.getClass());
        
        Collection<Part> partCol = request.getParts();
        ArrayList<Part> params = new ArrayList<>(partCol);
        for (Part param : params) {
            System.out.println("Param- Name:"+param.getName()+" Type: "+param.getContentType());
        }

        Part rePart = request.getPart("recaptcha-checked");
        String reString = rePart.getSubmittedFileName();
        System.out.println("Filename reString: "+reString);

        if ("true".equals(request.getParameter("recaptcha-checked"))) {
            try {
                String submitting = request.getParameter("submitEncounters");
                System.out.println("Submitting = "+submitting);
                if (submitting!=null&&"true".equals(submitting)) {

                    Shepherd myShepherd = new Shepherd(context);

                    AssetStore astore = AssetStore.getDefault(myShepherd);

                    //make encs, then mas
                    rtn = postEncountersAndAssets(rtn, context, request);

                    //rtn = makeMediaAssetFromPart(part, "encId", astore);
                }
                rtn.put("success", "true");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        out.println(rtn);
        out.close();
    }

    private JSONObject makeMediaAssetFromPart(Part part, String encId, AssetStore astore) {
        // Need to get an asset store
        JSONObject rtn = new JSONObject();
        return rtn;
    }

    private JSONObject postEncountersAndAssets(JSONObject rtn, String context, HttpServletRequest request) {

        //get number encounters

        //get number images 

        //create encounter 1 and metadata, prior (?) to making asset 

        //repeat until done

        return rtn;
    }

    private boolean hasVal(String str) {
        if (str!=null&&!"".equals(str)) return true;
        return false;
    }
}