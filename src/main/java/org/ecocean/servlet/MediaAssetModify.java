package org.ecocean.servlet;
import java.io.File;
import java.util.List;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import org.ecocean.*;
import org.ecocean.media.*;
import java.nio.file.Path;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.ecocean.ia.Task;

import java.io.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.ThreadPoolExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.joda.time.DateTime;

import org.json.JSONObject;
import org.json.JSONException;




public class MediaAssetModify extends HttpServlet {
  /** SLF4J logger instance for writing log entries. */
  public static Logger log = LoggerFactory.getLogger(WorkspaceDelete.class);

  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }

  public void doOptions(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
      response.setHeader("Access-Control-Allow-Origin", "*");
      response.setHeader("Access-Control-Allow-Methods", "GET, POST");
      if (request.getHeader("Access-Control-Request-Headers") != null) response.setHeader("Access-Control-Allow-Headers", request.getHeader("Access-Control-Request-Headers"));
      //response.setContentType("text/plain");
  }



  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }

  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    response.setHeader("Access-Control-Allow-Origin", "*");  //allow us stuff from localhost

    String context="context0";
    context=ServletUtilities.getContext(request);
    String langCode = ServletUtilities.getLanguageCode(request);
    Shepherd myShepherd = new Shepherd(context);
    myShepherd.setAction("MediaAssetModify.class");
    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean locked = false;

    JSONObject res = new JSONObject("{\"success\": \"false\"}");

    boolean isOwner = true;


    // ServletUtilities.informInterestedParties(request, request.getParameter("number"), message,context);
    myShepherd.beginDBTransaction();

    String id="";

    try {

      id = request.getParameter("id");
      if (id==null) {
        throw new IOException("MediaAssetModify servlet requires an 'id' argument.");
      }


      MediaAsset ma = myShepherd.getMediaAsset(id);

      if (ma==null) {
        throw new IOException("No MediaAsset in database with id "+request.getParameter("id"));
      } else {

        if (request.getParameter("lat")!=null) {
          ma.setUserLatitude(Double.valueOf(request.getParameter("lat")));
          res.put("setLatitude",Double.valueOf(request.getParameter("lat")));
        }

        if (request.getParameter("long")!=null) {
          ma.setUserLongitude(Double.valueOf(request.getParameter("long")));
          res.put("setLongitude",Double.valueOf(request.getParameter("long")));
        }

        if (request.getParameter("datetime")!=null) {
          ma.setUserDateTime(DateTime.parse(request.getParameter("datetime")));
          res.put("setDateTime",DateTime.parse(request.getParameter("datetime")).toString());
        }

      }

      res.put("success","true");
    } catch (Exception edel) {
      try{
        //now try getting info. from json data instead of params
        JSONObject jsonIn = ServletUtilities.jsonFromHttpServletRequest(request);
        if(jsonIn!=null){
          String mediaAssetId = jsonIn.optString("maId", null);
          String rotationDesired = jsonIn.optString("rotate", null);
          if(Util.stringExists(mediaAssetId) && Util.stringExists(rotationDesired) ){
            MediaAsset mediaAsset = myShepherd.getMediaAsset(mediaAssetId);
            if(Util.stringExists(rotationDesired) && !rotationDesired.equals("") && rotationDesired.indexOf("rotate")>-1){
              purgeRotationLabelsFrom(mediaAsset, myShepherd);
              mediaAsset.addLabel(rotationDesired);
            }
            myShepherd.updateDBTransaction(); //update before redoAllChildren knows which labels to actually apply
            mediaAsset.redoAllChildren(myShepherd);
            myShepherd.updateDBTransaction();
            cloneRotatedMediaAssetAndSendToDetection(mediaAsset, myShepherd);
            res.put("success","true");
          }
        } else{
          throw new IOException("MediaAssetModify servlet requires an 'id' argument or json data. Both are missing.");
        }
      } catch(Exception except2){
        locked = true;
        log.warn("Failed to modify MediaAsset: " + request.getParameter("id"), edel);
        edel.printStackTrace();
        myShepherd.rollbackDBTransaction();
      }
    }


    if (!locked) {
      myShepherd.commitDBTransaction();
    }

    out.println(res.toString());
    out.close();
    myShepherd.closeDBTransaction();
  }

  public void cloneRotatedMediaAssetAndSendToDetection(MediaAsset mediaAsset, Shepherd myShepherd){
    List<MediaAsset> masterChildren = mediaAsset.findChildrenByLabel(myShepherd, "_master");
    if(masterChildren != null && masterChildren.size()>0){
      if(masterChildren.size()>1){
        System.out.println("BUG! Should not be here. More than one master child!");
      } else{
        MediaAsset masterChild = masterChildren.get(0);
        purgeRotationLabelsFrom(masterChild, myShepherd);
        if(masterChild != null){
          AssetStore astore = AssetStore.getDefault(myShepherd);
          Path currentPath = masterChild.localPath();
          String currentPathFilename = currentPath.getFileName().toString();
          String newFilename = "backup_" + currentPathFilename;
          int pathDepth = currentPath.getNameCount();
          String subPathStr = currentPath.subpath(0, pathDepth-1).toString();
          File newFile = new File('/' + subPathStr + '/' + newFilename);
          try{
            Files.createFile(newFile.toPath());
          } catch(Exception e){
            System.out.println("Error: Did not create file in cloneRotatedMediaAssetAndSendToDetection; it's possible that this is because the backup file already exists");
            // e.printStackTrace();
          }
          try{
            Files.copy(currentPath, newFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            JSONObject sp = astore.createParameters(newFile);
            MediaAsset newParent = null;
            try {
              newParent = astore.copyIn(newFile, sp);
            } catch (Exception e) {
              e.printStackTrace();
            }
            newParent.updateMetadata();
            newParent.addLabel("_original");
            ArrayList<String> labels = mediaAsset.getLabels();
            for(String currentLable: labels){ //give the non-rotation labels to the clone
              if(currentLable.indexOf("rotate")<0 && currentLable.indexOf("old") < 0){
                newParent.addLabel(currentLable);
              }
            }
            mediaAsset.addLabel("old"); //assign old to the old one so that you can use that to filter it out of the dom later
            MediaAssetFactory.save(newParent, myShepherd);
            newParent.updateStandardChildren(myShepherd);
            List<Encounter> associatedEncounters = myShepherd.getEncounters(mediaAsset);
            if(associatedEncounters != null && associatedEncounters.size() > 0){
              for(Encounter enc: associatedEncounters){
                enc.addMediaAsset(newParent);
                Task task = org.ecocean.ia.IA.intakeMediaAssets(myShepherd, enc.getMedia(), new Task()); //left doing this every time in... I assume that it won't kick off if an identical media asset has already been done; I think I've seen this before at least? -Mark F.
                myShepherd.storeNewTask(task);
              }
            }
          } catch (Exception e){
            System.out.println("Error copying file in cloneRotatedMediaAssetAndSendToDetection method of MediaAssetModify.java");
            e.printStackTrace();
          }
        }
      }
    }
  }

  public void purgeRotationLabelsFrom(MediaAsset originalMediaAsset, Shepherd myShepherd){
    ArrayList<String> labels = originalMediaAsset.getLabels();
    if(labels!=null && labels.size()>0){
      for(String currentLabel: labels){
        if(currentLabel.indexOf("rotate") > -1 || currentLabel.indexOf("old") > -1 || currentLabel.indexOf("_original") > -1){
          //there's a rotate or "old" or "_original" label. Exterminate!
          originalMediaAsset.removeLabel(currentLabel);
        }
      }
    }
    try{
      originalMediaAsset.redoAllChildren(myShepherd);
    }catch(Exception e){
      System.out.println("Error with redoAllChildren in purgeRotationLabelsFrom method of MediaAssetModify.java");
      e.printStackTrace();
    }
    myShepherd.updateDBTransaction();
  }
}
