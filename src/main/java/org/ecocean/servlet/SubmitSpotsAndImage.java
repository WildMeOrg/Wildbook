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

import com.oreilly.servlet.multipart.FilePart;
import com.oreilly.servlet.multipart.MultipartParser;
import com.oreilly.servlet.multipart.ParamPart;
import com.oreilly.servlet.multipart.Part;

import org.ecocean.CommonConfiguration;
import org.ecocean.Util;
import org.ecocean.grid.EncounterLite;
import org.ecocean.grid.GridManager;
import org.ecocean.grid.GridManagerFactory;
import org.ecocean.Encounter;
import org.ecocean.Shepherd;
import org.ecocean.SuperSpot;
import org.ecocean.media.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import org.json.JSONObject;
import org.json.JSONArray;

import javax.xml.bind.DatatypeConverter;

/**
 *
 * This servlet allows the user to upload an extracted, processed patterning file that corresponds to
 * a previously uploaded set of spots. This file is then used for visualization of the extracted pattern
 * and visualizations of potentially matched spots.
 * @author jholmber
 *
 */
public class SubmitSpotsAndImage extends HttpServlet {


  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }


  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }


  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    String context = ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);
    myShepherd.setAction("SubmitSpotsAndImage.class");

    myShepherd.beginDBTransaction();
    JSONObject json = ServletUtilities.jsonFromHttpServletRequest(request);
    int maId = json.optInt("mediaAssetId", -1);
    if (maId < 0) {
      myShepherd.rollbackDBTransaction();
      myShepherd.closeDBTransaction();
      throw new IOException("invalid mediaAssetId");
    }
    String encId = json.optString("encId", null);
    if (encId == null) {
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
        throw new IOException("invalid encId");
    }
    Encounter enc = myShepherd.getEncounter(encId);
    if (enc == null) {
      myShepherd.rollbackDBTransaction();
      myShepherd.closeDBTransaction();
      throw new IOException("invalid encId");
    }
    boolean rightSide = json.optBoolean("rightSide", false);
    ArrayList<SuperSpot> spots = parseSpots(json.optJSONArray("spots"));
    if (spots == null) {
      myShepherd.rollbackDBTransaction();
      myShepherd.closeDBTransaction();
      throw new IOException("invalid spots");
    }
    ArrayList<SuperSpot> refSpots = parseSpots(json.optJSONArray("refSpots"));
    if (refSpots == null) {
      myShepherd.rollbackDBTransaction();
      myShepherd.closeDBTransaction();
      throw new IOException("invalid refSpots");
    }

    AssetStore store = AssetStore.getDefault(myShepherd);
    //this should put it in the same old (pre-MediaAsset) location to maintain url pattern
    
    
    //setup data dir
    String rootWebappPath = getServletContext().getRealPath("/");
    File webappsDir = new File(rootWebappPath).getParentFile();
    File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));
    //if(!shepherdDataDir.exists()){shepherdDataDir.mkdirs();}
    //File encountersDir=new File(shepherdDataDir.getAbsolutePath()+"/encounters");
    //if(!encountersDir.exists()){encountersDir.mkdirs();}
    String thisEncDirString=Encounter.dir(shepherdDataDir,encId);
    File thisEncounterDir=new File(thisEncDirString);
    if(!thisEncounterDir.exists()){thisEncounterDir.mkdirs();System.out.println("I am making the encDir: "+thisEncDirString);}
    
    //now persist
    JSONObject params = store.createParameters(new File("encounters/" + Encounter.subdir(encId) + "/extract" + (rightSide ? "Right" : "") + encId + ".jpg"));
System.out.println("====> params = " + params);
    MediaAsset ma = store.create(params);
    ma.copyInBase64(json.optString("imageData", null));
    ma.addLabel("_spot" + (rightSide ? "Right" : ""));  //we are sticking with "legacy" '_spot' for left
    ma.setParentId(maId);
    ma.addDerivationMethod("spotTool", json.optJSONObject("imageToolValues"));
    //ma.updateMinimalMetadata();
    MediaAssetFactory.save(ma, myShepherd);
System.out.println("created???? " + ma);

    if (rightSide) {
        enc.setRightSpots(spots);
        enc.setRightReferenceSpots(refSpots);
    } 
    else {
        enc.setSpots(spots);
        enc.setLeftReferenceSpots(refSpots);
    }
    
    //reset the entry in the GridManager graph
    GridManager gm = GridManagerFactory.getGridManager();
    gm.addMatchGraphEntry(encId, new EncounterLite(enc));
    
    myShepherd.commitDBTransaction();
    myShepherd.closeDBTransaction();

    JSONObject rtn = new JSONObject("{\"success\": true}");
    rtn.put("spotAssetId", ma.getId());
    rtn.put("spotAssetUrl", ma.webURL());
    response.setContentType("text/plain");
    PrintWriter out = response.getWriter();
    out.println(rtn.toString());
    out.close();
  }


    private ArrayList<SuperSpot> parseSpots(JSONArray arr) {
      try{
        if ((arr == null) || (arr.length() < 1)) return null;
        ArrayList<SuperSpot> spots = new ArrayList<SuperSpot>();
        for (int i = 0 ; i < arr.length() ; i++) {
            JSONArray pt = arr.optJSONArray(i);
            if (pt == null) throw new RuntimeException("parseSpots() invalid point at i=" + i);
            if (pt.length() != 2) throw new RuntimeException("parseSpots() invalid point length=" + pt.length() + " at i=" + i);
            Double x = pt.optDouble(0);
            Double y = pt.optDouble(1);
            if ((x == null) || (y == null)) throw new RuntimeException("parseSpots() invalid point (" + x + "," + y + ") at i=" + i);
            spots.add(new SuperSpot(x, y, -1.0));
        }
        return spots;
      }
      catch(Exception e){
        e.printStackTrace();
        
      }
      return null;
    }

}


