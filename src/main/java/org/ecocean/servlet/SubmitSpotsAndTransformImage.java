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

import org.ecocean.*;
import org.ecocean.media.MediaAsset;
import org.ecocean.media.MediaAssetFactory;
import org.ecocean.media.Feature;
import org.ecocean.media.FeatureType;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import com.google.gson.*;
import java.io.File;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import org.json.JSONObject;


public class SubmitSpotsAndTransformImage extends HttpServlet {


  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }


  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }

  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    String context="context0";
    context=ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);
    myShepherd.setAction("SubmitSpotsAndTransformImage.class");
    response.setContentType("application/json");
    PrintWriter out = response.getWriter();

    StringBuffer jb = new StringBuffer();
    String line = null;
    try {
        BufferedReader reader = request.getReader();
        while ((line = reader.readLine()) != null) {
            jb.append(line);
        }
    } catch (Exception e) { }

System.out.println("GOT " + jb.toString());

    JsonElement jel = new JsonParser().parse(jb.toString());
    JsonObject jobj = jel.getAsJsonObject();
    int id = jobj.get("id").getAsInt();
    boolean isDorsalFin = false;
    try {
        isDorsalFin = jobj.get("isDorsalFin").getAsBoolean();
    } catch (Exception ex) {}

    myShepherd.beginDBTransaction();
    
    FeatureType.initAll(myShepherd);
  
    
    MediaAsset ma = MediaAssetFactory.load(id, myShepherd);
    if (ma == null) {
        out.print("{\"error\": \"invalid MediaAsset id " + id + "\"}");
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
        return;
    }

    Encounter enc = Encounter.findByMediaAsset(ma, myShepherd);
    if (enc == null) {
        out.print("{\"error\": \"could not find Encounter for MediaAsset=" + id + "\"}");
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
        return;
    }

//List<List<Individual>> group = new ArrayList<List<Individual>>
    List<ArrayList<SuperSpot>> spots = new ArrayList<ArrayList<SuperSpot>>();
    ArrayList<SuperSpot> refSpots = new ArrayList<SuperSpot>();
/*
            double ref3x = (new Double(request.getParameter("ref3x"))).doubleValue();
            double ref3y = (new Double(request.getParameter("ref3y"))).doubleValue();

            ArrayList<SuperSpot> refs = new ArrayList<SuperSpot>();
            refs.add(new SuperSpot(ref1x, ref1y));
*/
    JsonArray paths = jobj.getAsJsonArray("paths");
    for (int i = 0 ; i < paths.size() ; i++) {  //should be only 0-2 (left, right)
        if (paths.get(i) != null) {
            spots.add(new ArrayList<SuperSpot>());
            if (!paths.get(i).isJsonArray()) continue;
            JsonArray p = paths.get(i).getAsJsonArray();
            for (int j = 0 ; j < p.size() ; j++) {
                JsonArray pt = p.get(j).getAsJsonArray();
                double x = pt.get(0).getAsDouble();
                double y = pt.get(1).getAsDouble();
System.out.println("pathspot[" + i + "]: " + x + ", " + y);
                spots.get(i).add(new SuperSpot(x, y, new Double(-2.0)));
            }
        }
    }
    JsonArray jspots = jobj.getAsJsonArray("points");
    if (jspots != null) {
        double notchX = -1;
        //we only make ref spots out of the first 3 .. we probably should check type tho in case order is wrong? 
        int rmax = jspots.size();
        if (rmax > 3) rmax = 3;
        for (int i = 0 ; i < rmax ; i++) {
            JsonArray pt = jspots.get(i).getAsJsonArray();
            double x = pt.get(0).getAsDouble();
            double y = pt.get(1).getAsDouble();
            //String type = pt.get(2).getAsString();
System.out.println(ma + " refspot[" + i + "]: " + x + ", " + y);
            refSpots.add(new SuperSpot(x, y, new Double(-2.0)));
            if (i == 1) notchX = x;
        }

        /////if ((refSpots.size() > 1) && (refSpots.get(0).getCentroidX() == refSpots.get(1).getCentroidX())) isDorsalFin = true;
System.out.println("isDorsalFin? " + isDorsalFin);

        if (isDorsalFin && (jspots.size() >= 10)) {  //dorsal has 10 reference spots that we care about, so we grab those too
            notchX = -1;  //mostly irrelevant for dorsal fins
            for (int i = 3 ; i < 10 ; i++) {
                JsonArray pt = jspots.get(i).getAsJsonArray();
                double x = pt.get(0).getAsDouble();
                double y = pt.get(1).getAsDouble();
                //String type = pt.get(2).getAsString();
System.out.println("refspot [b]: " + x + ", " + y);
                refSpots.add(new SuperSpot(x, y, new Double(-2.0)));
                ///if (i == 1) notchX = x;
            }
            rmax = 10;  //so spots can pick up from here below
        }

        //now we add any remaining spots to the appropriate side
        for (int i = rmax ; i < jspots.size() ; i++) {
            JsonArray pt = jspots.get(i).getAsJsonArray();
            double x = pt.get(0).getAsDouble();
            double y = pt.get(1).getAsDouble();
            int side = 0;
            if ((notchX > -1) && (x >= notchX)) side = 1;
            spots.get(side).add(new SuperSpot(x, y, new Double(-2.0)));
        }
    }


    float clientWidth = jobj.get("clientWidth").getAsFloat();
    JsonArray t = jobj.getAsJsonArray("transform");
    float[] transform = new float[t.size()];
    for (int i = 0 ; i < t.size() ; i++) {
        transform[i] = t.get(i).getAsFloat();
    }

    float pixelScale = 1;
    if ((clientWidth > 0) && (ma.getWidth() > 0)) pixelScale = (float)ma.getWidth() / clientWidth;
System.out.println("pixelScale -> " + pixelScale);
    transform[4] *= pixelScale;
    transform[5] *= pixelScale;
System.out.println("transform matrix -> " + Arrays.toString(transform));
    //note: for imagemagick, translate x,y will be moved to clipping offset

    // if no modification was done to image, we dont need a new MediaAsset and the Annotation already exists
    boolean spotMAisNew = false;
    MediaAsset spotMA = ma;

    float sizeMult = 1;
    if (!Util.isIdentityMatrix(transform)) {
        //first we construct our feature for the shape we have (and the new Annotation will need this)
        JSONObject fparams = new JSONObject();
        //if (ma.getWidth() > 3000) sizeMult = 3000 / ma.getWidth();  //a little bit of size sanity
        fparams.put("width", ma.getWidth() * sizeMult);
        fparams.put("height", ma.getHeight() * sizeMult);
        fparams.put("transformMatrix", transform);
        Feature ft = new Feature("org.ecocean.boundingBox", fparams);
        ma.addFeature(ft);
        Annotation annot = new Annotation(enc.getTaxonomyString(), ft);
System.out.println("annotation -> " + annot);
        enc.addAnnotation(annot);
        spotMAisNew = true;
        spotMA = ft.createMediaAsset();
System.out.println("NEW (transformed) spotMA created -> " + spotMA);
    }

    /*
        we dont want to make a duplicate Annotation (which *usually* means the trivial one -- since we (should!) already have that.
        note however, that there is the slim(??) chance someone does the *exact* same transform that was done previously.  that will cause this
        enc.addAnnotation to blow up with a psql constraint complaint.  ugh.
    */

    //now we add the spots (as a Feature) to the spotMA
    if (spotMA != null) {
        if (spotMAisNew) spotMA.setMinimalMetadata((int)Math.round(ma.getWidth() * sizeMult), (int)Math.round(ma.getHeight() * sizeMult), "image/jpeg");

//////TODO how do we make this generic, for sided-spots (whalesharks dorsal) vs fluke vs dorsal etc...
        JSONObject params = new JSONObject();
//what *is* the deal with sidedness here? did we flip that in js ... i forget!  TODO
        String typePrefix = "org.ecocean.flukeEdge";
        if (isDorsalFin) typePrefix = "org.ecocean.dorsalEdge";
        if (refSpots.size() > 0) {
            JSONObject p = new JSONObject();
            p.put("spots", SuperSpot.listToJSONArray(refSpots));
            Feature f = new Feature(typePrefix + ".referenceSpots", p);
            spotMA.addFeature(f);
        }
        JSONObject p = new JSONObject();
        if ((spots.get(0) != null) && (spots.get(0).size() > 0)) {
            p.put("spotsLeft", SuperSpot.listToJSONArray(spots.get(0)));
        }
        if ((spots.get(1) != null) && (spots.get(1).size() > 0)) {
            p.put("spotsRight", SuperSpot.listToJSONArray(spots.get(1)));
        }
        if (p.has("spotsLeft") || p.has("spotsRight")) {
            Feature f = new Feature(typePrefix + ".edgeSpots", p);
            spotMA.addFeature(f);
        }
    }

/*
    if ((spots.get(0) != null) && (spots.get(0).size() > 0)) {
        //enc.setSpots(spots.get(0));
        //enc.setNumLeftSpots(spots.get(0).size());
    }
    if ((spots.get(1) != null) && (spots.get(1).size() > 0)) {
        //enc.setRightSpots(spots.get(1));
        //enc.setNumRightSpots(spots.get(1).size());
    }
    if (refSpots.size() > 0) {  ///not sure this logic is right
        //enc.setRightReferenceSpots(refSpots);
        //enc.setLeftReferenceSpots(refSpots);
    }
*/


    if (spotMAisNew) {
        spotMA.setAccessControl(request);
        MediaAssetFactory.save(spotMA, myShepherd);
    }

    myShepherd.commitDBTransaction();
    
    myShepherd.beginDBTransaction();
    

    HashMap m = new HashMap();
    m.put("success", !(spotMA == null));
    if (spotMA != null) {
        m.put("spotMAId", spotMA.getId());
        System.out.println("SubmitSpotsAndTranform " + (spotMAisNew ? "generated a new" : "put spots on existing") + " spotMA id=" + spotMA.getId());
    } 
    else {
        System.out.println("ERROR: SubmitSpotsAndTranform failed to generate a spot MediaAsset!");
    }
    /////m.put("name", name);  TODO return url to MA!!
    Gson gson = new Gson();
    out.println(gson.toJson(m));
    
    myShepherd.commitDBTransaction();
    myShepherd.closeDBTransaction();
    
    out.flush();
    out.close();
  }

}
