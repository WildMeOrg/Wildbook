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

//////
//import java.io.*;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.concurrent.ThreadPoolExecutor;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.ecocean.CommonConfiguration;
import org.ecocean.MailThreadExecutorService;
import org.ecocean.Util;
import org.ecocean.MarkedIndividual;
import org.ecocean.Keyword;
import org.ecocean.Encounter;
import org.ecocean.Occurrence;
import org.ecocean.Measurement;
import org.ecocean.Shepherd;
import org.ecocean.media.*;
import org.ecocean.ia.Task;
import org.ecocean.NotificationMailer;
import org.ecocean.ShepherdProperties;
import org.ecocean.SinglePhotoVideo;
import org.ecocean.tag.AcousticTag;
import org.ecocean.tag.MetalTag;
import org.ecocean.tag.SatelliteTag;
import org.ecocean.identity.IBEISIA;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.json.JSONObject;
//import java.lang.*;
//import java.util.List;
/*
import org.ecocean.CommonConfiguration;
import org.ecocean.Encounter;
import org.ecocean.Shepherd;
import org.ecocean.SinglePhotoVideo;
import org.ecocean.User;
*/


import org.apache.shiro.web.util.WebUtils;
//import org.ecocean.*;
import org.ecocean.security.SocialAuth;
import org.ecocean.Annotation;
import org.ecocean.CommonConfiguration;
import org.ecocean.Shepherd;
import org.ecocean.AccessControl;
import org.ecocean.User;
import org.pac4j.core.context.J2EContext;
import org.pac4j.core.context.WebContext;
import org.pac4j.oauth.client.FacebookClient;
//import org.pac4j.oauth.client.YahooClient;
import org.pac4j.oauth.credentials.OAuthCredentials;
import org.pac4j.oauth.profile.facebook.FacebookProfile;
import org.ecocean.mmutil.FileUtilities;

/**
 * Uploads a new image to the file system and associates the image with an Encounter record
 *
 * @author jholmber
 */
public class SimpleEncounterForm extends HttpServlet {

  @Override
public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }

  @Override
public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }

private final String UPLOAD_DIRECTORY = "/tmp";

    //little helper function for pulling values as strings even if null (not set via form)
    private String getVal(HashMap fv, String key) {
        if (fv.get(key) == null) {
            return "";
        }
        return fv.get(key).toString();
    }

  public static final String ERROR_PROPERTY_MAX_LENGTH_EXCEEDED = "The maximum upload length has been exceeded by the client.";

  @Override
public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");

        HashMap fv = new HashMap();

        //IMPORTANT - processingNotes can be used to add notes on data handling (e.g., poorly formatted dates) that can be reconciled later by the reviewer
        //Example usage: processingNotes.append("<p>Error encountered processing this date submitted by user: "+getVal(fv, "datepicker")+"</p>");
        StringBuffer processingNotes=new StringBuffer();

        HttpSession session = request.getSession(true);
    String context="context0";
    context=ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);
    myShepherd.setAction("EncounterForm.class");
System.out.println("in context " + context);
    PrintWriter out = response.getWriter();

    User user = AccessControl.getUser(request, myShepherd);
    if (user == null) {
        response.sendError(401, "access denied");
        response.setContentType("text/plain");
        out.println("access denied");
        myShepherd.closeDBTransaction();
        return;
    }

        //request.getSession()getServlet().getServletContext().getRealPath("/"));
        String rootDir = getServletContext().getRealPath("/");
System.out.println("rootDir=" + rootDir);

    //set up for response
    response.setContentType("text/plain");
    boolean locked = false;

    String fileName = "None";
    String username = "None";
    String fullPathFilename="";

        boolean fileSuccess = false;  //kinda pointless now as we just build sentFiles list now at this point (do file work at end)
        String doneMessage = "";
        List<String> filesOK = new ArrayList<String>();
        HashMap<String, String> filesBad = new HashMap<String, String>();

        List<FileItem> formFiles = new ArrayList<FileItem>();

        long maxSizeMB = CommonConfiguration.getMaxMediaSizeInMegabytes(context);
        long maxSizeBytes = maxSizeMB * 1048576;

        if (ServletFileUpload.isMultipartContent(request)) {
          
            try {
                ServletFileUpload upload = new ServletFileUpload(new DiskFileItemFactory());
                upload.setHeaderEncoding("UTF-8");
                List<FileItem> multiparts = upload.parseRequest(request);

                for(FileItem item : multiparts){
                    if (item.isFormField()) {  //plain field
                        fv.put(item.getFieldName(), ServletUtilities.preventCrossSiteScriptingAttacks(item.getString("UTF-8").trim()));  //TODO do we want trim() here??? -jon
//System.out.println("got regular field (" + item.getFieldName() + ")=(" + item.getString("UTF-8") + ")");
                    } else if (item.getName().startsWith("socialphoto_")) {
                        System.out.println(item.getName() + ": " + item.getString("UTF-8"));
                    } else {  //file
//System.out.println("content type???? " + item.getContentType());   TODO note, the helpers only check extension
                        if (item.getSize() > maxSizeBytes) {
                            filesBad.put(item.getName(), "file is larger than " + maxSizeMB + "MB");
                        } else if (myShepherd.isAcceptableImageFile(item.getName()) || myShepherd.isAcceptableVideoFile(item.getName()) ) {
                            formFiles.add(item);
                            filesOK.add(item.getName());
                        } else {
                            filesBad.put(item.getName(), "invalid type of file");
                        }
                    }
                }

                doneMessage = "File Uploaded Successfully";
                fileSuccess = true;

            } catch (Exception ex) {
                doneMessage = "File Upload Failed due to " + ex;
            }

        } else {
            doneMessage = "Sorry this Servlet only handles file upload request";
            System.out.println("Not a multi-part form submission!");
        }

        session.setAttribute("filesOKMessage", (filesOK.isEmpty() ? "none" : Arrays.toString(filesOK.toArray())));
        String badmsg = "";
        for (String key : filesBad.keySet()) {
            badmsg += key + " (" + getVal(filesBad, key) + ") ";
        }
        if (badmsg.equals("")) { badmsg = "none"; }
        session.setAttribute("filesBadMessage", badmsg);

    JSONObject rtn = new JSONObject();

        if (fileSuccess) {


      String locCode = "";
      System.out.println(" **** here is what i think locationID is: " + fv.get("locationID"));
            if ((fv.get("locationID") != null) && !fv.get("locationID").toString().equals("")) {
                locCode = fv.get("locationID").toString();
            }




System.out.println("about to do enc()");

            //Encounter enc = new Encounter(day, month, year, hour, minutes, guess, getVal(fv, "location"));
            Encounter enc = new Encounter();
            String encID = enc.generateEncounterNumber();
            enc.setEncounterNumber(encID);
            
            AssetStore astore = AssetStore.getDefault(myShepherd);
            ArrayList<Annotation> newAnnotations = new ArrayList<Annotation>();

            //for directly uploaded files
            for (FileItem item : formFiles) {
              //convert each FileItem into a MediaAsset
              makeMediaAssetsFromJavaFileItemObject(item, encID, astore, enc, newAnnotations, "Felis", "catus");
            }

            enc.setAnnotations(newAnnotations);

            enc.setGenus("Felis");
            enc.setSpecificEpithet("catus");

            List<User> submitters = new ArrayList<User>();
            submitters.add(user);
            enc.setSubmitters(submitters);
            enc.setSubmitterID(user.getUsername());

      //enc.setComments(getVal(fv, "comments").replaceAll("\n", "<br>"));
              //enc.setLifeStage(fv.get("lifeStage").toString());
      //enc.setSex(getVal(fv, "sex"));

        enc.setDecimalLatitude(Util.getDecimalCoordFromString(getVal(fv, "lat")));
        enc.setDecimalLongitude(Util.getDecimalCoordFromString(getVal(fv, "lon")));
        enc.setLocationID(getVal(fv, "locationID"));
        enc.setBehavior(getVal(fv, "behavior"));

    String origDateTime = getVal(fv, "datetime");
    enc.setVerbatimEventDate(origDateTime);
    System.out.println("SimpleEncounterForm[" + encID + "]: origDateTime=" + origDateTime);
    DateTime dt = null;
    try {
        dt = new DateTime(origDateTime);
        System.out.println("SimpleEncounterForm[" + encID + "]: " + origDateTime + " => " + dt);
    } catch (Exception ex) {
        ex.printStackTrace();
    }
    if (dt != null) enc.setDateInMilliseconds(dt.getMillis());

      //enc.addComments("<p>Submitted on " + (new java.util.Date()).toString() + " from address: " + ServletUtilities.getRemoteHost(request) + "</p>");

        enc.setState("test");
      if (!getVal(fv, "locCode").equals("")) {
        enc.setLocationCode(locCode);
      }
      if (!getVal(fv, "country").equals("")) {
        enc.setCountry(getVal(fv, "country"));
      }

      //String guid = CommonConfiguration.getGlobalUniqueIdentifierPrefix(context) + encID;


        //this will try to set from MediaAssetMetadata -- ymmv
        //if (!llSet) enc.setLatLonFromAssets();
        //if (enc.getYear() < 1) enc.setDateFromAssets();

                String newnum = myShepherd.storeNewEncounter(enc, encID);
                enc.refreshAssetFormats(myShepherd);
                rtn.put("success", true);
                rtn.put("encounterId", newnum);

                //*after* persisting this madness, then lets kick MediaAssets to IA for whatever fate awaits them
                //  note: we dont send Annotations here, as they are always(forever?) trivial annotations, so pretty disposable

                // might want to set detection status here (on the main thread)

                for (MediaAsset ma: enc.getMedia()) {
                    ma.setDetectionStatus(IBEISIA.STATUS_INITIATED);
                }

                Task parentTask = null;  //this is *not* persisted, but only used so intakeMediaAssets will inherit its params
                if (locCode != null) {
                    parentTask = new Task();
                    JSONObject tp = new JSONObject();
                    JSONObject mf = new JSONObject();
                    mf.put("locationId", locCode);
                    tp.put("matchingSetFilter", mf);
                    parentTask.setParameters(tp);
                }
String task = "<TASK>";
/*
                Task task = org.ecocean.ia.IA.intakeMediaAssets(myShepherd, enc.getMedia(), parentTask);  //TODO are they *really* persisted for another thread (queue)
                myShepherd.storeNewTask(task);
*/
                Logger log = LoggerFactory.getLogger(EncounterForm.class);
                log.info("New encounter submission: <a href=\""+request.getScheme()+"://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" + encID+"\">"+encID+"</a>");
System.out.println("ENCOUNTER SAVED???? newnum=" + newnum + "; IA => " + task);
                org.ecocean.ShepherdPMF.getPMF(context).getDataStoreCache().evictAll();


///END  END 
      //return a forward to display.jsp
      System.out.println("Ending data submission.");

    }  //end "if (fileSuccess)

    myShepherd.closeDBTransaction();
    //return null;
    out.println(rtn.toString());
  }


  private void makeMediaAssetsFromJavaFileItemObject(FileItem item, String encID, AssetStore astore, Encounter enc, ArrayList<Annotation> newAnnotations, String genus, String specificEpithet){
    JSONObject sp = astore.createParameters(new File(enc.subdir() + File.separator + item.getName()));
    sp.put("key", Util.hashDirectories(encID) + "/" + item.getName());
    MediaAsset ma = new MediaAsset(astore, sp);
    File tmpFile = ma.localPath().toFile();  //conveniently(?) our local version to save ma.cacheLocal() from having to do anything?
    File tmpDir = tmpFile.getParentFile();
    if (!tmpDir.exists()) tmpDir.mkdirs();
//System.out.println("attempting to write uploaded file to " + tmpFile);
    try {
      item.write(tmpFile);
    } catch (Exception ex) {
        System.out.println("Could not write " + tmpFile + ": " + ex.toString());
    }
    if (tmpFile.exists()) {
      
      try{
        ma.addLabel("_original");
        ma.copyIn(tmpFile);
        ma.validateSourceImage();
        ma.updateMetadata();
        newAnnotations.add(new Annotation(Util.taxonomyString(genus, specificEpithet), ma));
      }
      catch(IOException ioe){
        System.out.println("Hit an IOException trying to transform file "+item.getName()+" into a MediaAsset in EncounterFom.class.");
        ioe.printStackTrace();
      }
        
        
    } 
    else {
        System.out.println("failed to write file " + tmpFile);
    }
  }
  
  private void makeMediaAssetsFromJavaFileObject(File item, String encID, AssetStore astore, Encounter enc, ArrayList<Annotation> newAnnotations, String genus, String specificEpithet){
    
    System.out.println("Entering makeMediaAssetsFromJavaFileObject");
    
    JSONObject sp = astore.createParameters(new File(enc.subdir() + File.separator + item.getName()));
    sp.put("key", Util.hashDirectories(encID) + "/" + item.getName());
    MediaAsset ma = new MediaAsset(astore, sp);
    File tmpFile = ma.localPath().toFile();  //conveniently(?) our local version to save ma.cacheLocal() from having to do anything?
    File tmpDir = tmpFile.getParentFile();
    if (!tmpDir.exists()) tmpDir.mkdirs();
//System.out.println("attempting to write uploaded file to " + tmpFile);
    try {
      FileUtilities.copyFile(item, tmpFile);
      //item.write(tmpFile);
    } catch (Exception ex) {
        System.out.println("Could not write " + tmpFile + ": " + ex.toString());
    }
    if (tmpFile.exists()) {
      
      try{
        ma.addLabel("_original");
        ma.copyIn(tmpFile);
        ma.validateSourceImage();
        ma.updateMetadata();
        newAnnotations.add(new Annotation(Util.taxonomyString(genus, specificEpithet), ma));
        System.out.println("Added new annotation for: "+item.getName());
      }
      catch(IOException ioe){
        System.out.println("Hit an IOException trying to transform file "+item.getName()+" into a MediaAsset in EncounterFom.class.");
        ioe.printStackTrace();
      }
        
        
    } 
    else {
        System.out.println("failed to write file " + tmpFile);
    }
  }


}
