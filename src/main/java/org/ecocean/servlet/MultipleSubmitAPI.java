package org.ecocean.servlet;


import org.ecocean.CommonConfiguration;
import org.ecocean.Util;
import org.ecocean.Encounter;
import org.ecocean.Shepherd;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.media.AssetStore;
import org.ecocean.media.MediaAsset;
import org.ecocean.media.MediaAssetFactory;
import org.ecocean.Annotation;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.*;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Collection;

import com.google.gson.*;

import org.json.JSONObject;
import org.json.JSONArray;

import org.joda.time.format.*;
import org.joda.time.*;


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
        System.out.println("PART NAMES:");

        String jsonStr = request.getParameter("json-data");
        System.out.println("Sent JSON (as string) : "+jsonStr);
        JsonObject json = new JsonParser().parse(jsonStr).getAsJsonObject();
        System.out.println("Assert is JSONObject "+json.getClass());
        
        String numEncStr = json.get("number-encounters").getAsString();
        int numEncs = 0;
        System.out.println("number-encounters: "+numEncStr);
        if (hasVal(numEncStr)) {
            numEncs = Integer.valueOf(numEncStr);
        }

        Collection<Part> partCol = request.getParts();
        ArrayList<Part> params = new ArrayList<>(partCol);

        for (Part param : params) {
            String filename = "";
            if (param.getSubmittedFileName()!=null) filename = param.getSubmittedFileName();
            System.out.println("Param- Name:"+param.getName()+" Type: "+param.getContentType()+"  Filename: "+filename);
        }

        if ("true".equals(json.get("recaptcha-checked").getAsString())) {
            try {
                JsonObject encImgLists = json.getAsJsonObject("enc-image-lists");
                Shepherd myShepherd = new Shepherd(context);
                AssetStore astore = AssetStore.getDefault(myShepherd);
                for (int i=0;i<numEncs;i++) {
                    System.out.println("Making anns for enc "+i);
                    
                    try {
                        Encounter enc = createEncounter(json, i);
                        myShepherd.storeNewEncounter(enc);
                        System.out.println("Created Encounter id="+enc.getID());
                        JsonArray arr = encImgLists.getAsJsonArray(String.valueOf(i));
                        for (int j=0;j<arr.size();j++) {
                            String keyNum = arr.get(j).getAsString();
                            Part encPart = request.getPart("image-file-"+keyNum);
                            System.out.println("Got part for ann num="+j);
                            Annotation ann = makeMediaAssetFromPart(encPart, astore, enc, myShepherd);
                            enc.addAnnotation(ann);
                            System.out.println("Created Annotation id="+ann.getId());
                        }
                    } catch (Exception e) {
                        System.out.println("Exception creating Encounter and assets!");
                        e.printStackTrace();
                    }
                    rtn.put("success", "true");
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                // send the created assets to intakeAnnotations? 
            }
        }
        out.println(rtn);
        out.close();
    }

    // Similar to the methods in EncounterForm
    private Annotation makeMediaAssetFromPart(Part part, AssetStore astore, Encounter enc, Shepherd myShepherd) {
        JSONObject sParams = astore.createParameters(new File(enc.subdir()+File.separator+part.getSubmittedFileName()));
        sParams.put("key", Util.hashDirectories(enc.getID())+"/"+part.getName());
        MediaAsset ma = new MediaAsset(astore, sParams);
        File uploadFile = ma.localPath().toFile();
        File uploadDir = uploadFile.getParentFile();
        if (!uploadDir.exists()) uploadDir.mkdirs();
        try {
            System.out.println("Gonna try ma.localPath().toString() --> "+ma.localPath().toString());
            part.write(ma.localPath().toString());
        } catch (Exception e) { 
            e.printStackTrace();
        }
        if (uploadFile.exists()) {
            try {
                ma.addLabel("_original");
                ma.copyIn(uploadFile);
                ma.validateSourceImage();

                ma.updateMetadata();
            } catch (IOException ioe) {
                System.out.println("SEVERE: IOException copying image file "+part.getSubmittedFileName()+" to MediaAsset with id="+ma.getId());
                ioe.printStackTrace();
            }
            // oh fuuukk... we need to handle species.
            MediaAssetFactory.save(ma, myShepherd);
            System.out.println("Trying to updateStandardChildren...");
            ma.updateStandardChildren(myShepherd);
            ma.updateMinimalMetadata();
            Annotation ann = new Annotation("",ma);
            myShepherd.storeNewAnnotation(ann); 
            return ann;
        }
        return null;
    }

    private Encounter createEncounter(JsonObject json, int encIdx) {
        String locStr = json.get("enc-data-"+encIdx).getAsJsonObject().get("location").getAsString();
        String dateStr = json.get("enc-data-"+encIdx).getAsJsonObject().get("date").getAsString();
        DateTimeFormatter formatter = DateTimeFormat.forPattern("MM/dd/yyyy");
        DateTime dt = null;
        if (hasVal(dateStr)) {
            dt = formatter.parseDateTime(dateStr);
        }
        Encounter enc = new Encounter(dt, locStr);
        String comments = json.get("enc-data-"+encIdx).getAsJsonObject().get("comments").getAsString();
        if (hasVal(comments)) {
            enc.setComments(comments);
        }
        System.out.println("This locationId for new encounter: "+locStr);
        System.out.println("The locationId FROM the encounter: "+enc.getLocationID());
        return enc;
    }

    private boolean hasVal(String str) {
        if (str!=null&&!"".equals(str)) return true;
        return false;
    }
}