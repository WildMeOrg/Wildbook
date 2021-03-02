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
        System.out.println("deleteMe now try getting info. from json data instead of params");
        JSONObject jsonIn = ServletUtilities.jsonFromHttpServletRequest(request);
        if(jsonIn!=null){
          String mediaAssetId = jsonIn.optString("maId", null);
          String rotationDesired = jsonIn.optString("rotate", null);
          if(Util.stringExists(mediaAssetId) && Util.stringExists(rotationDesired)){
            MediaAsset mediaAsset = myShepherd.getMediaAsset(mediaAssetId);
            System.out.println("deleteMe mediaAssetId is " + mediaAssetId);
            System.out.println("deleteMe mediaAssetId from actual mediaAsset object is: " + mediaAsset.getId());

            //since the 90 degree rotations don't accumulate automatically, here's logic to handle that
            ArrayList<String> labels = mediaAsset.getLabels();
            int rotationLableCounter = 0;
            if(rotationDesired.equals("rotate90")){
              if(labels!=null && labels.size()>0){
                System.out.println("deleteMe labels.size() is: " + labels.size());
                System.out.println("deleteMe labels are: " + labels.toString());
                for(String currentLabel: labels){
                  System.out.println("deleteMe currentLabel is: " + currentLabel);
                  if(currentLabel.equals("rotate90")){
                    System.out.println("deleteMe got here currentLabel is rotate90!");
                    mediaAsset.removeLabel("rotate90");
                    // myShepherd.updateDBTransaction();
                    System.out.println("deleteMe got here 1");
                    mediaAsset.addLabel("rotate180");
                    // myShepherd.updateDBTransaction();
                    System.out.println("deleteMe got here 2");
                    rotationLableCounter++;
                    System.out.println("deleteMe got here 3");
                  }
                  if(currentLabel.equals("rotate180")){
                    System.out.println("deleteMe got here 4");
                    System.out.println("deleteMe got here currentLabel is rotate180!");
                    mediaAsset.removeLabel("rotate180");
                    // myShepherd.updateDBTransaction();
                    mediaAsset.addLabel("rotate270");
                    // myShepherd.updateDBTransaction();
                    rotationLableCounter++;
                  }
                  if(currentLabel.equals("rotate270")){
                    System.out.println("deleteMe got here 5");
                    System.out.println("deleteMe got here currentLabel is rotate270!");
                    mediaAsset.removeLabel("rotate270");
                    // myShepherd.updateDBTransaction();
                    rotationLableCounter++;
                  }
                  System.out.println("deleteMe got here 6");
                }//end for labels
                System.out.println("deleteMe got here 7");
              } //end if labels exist
              System.out.println("deleteMe got here 8");
              if(rotationLableCounter<1){
                System.out.println("deleteMe got here 9");
                System.out.println("deleteMe rotationCounter never incremented. rotating plain old 90 degrees...");
                mediaAsset.addLabel(rotationDesired);
                // myShepherd.updateDBTransaction();
              }
            }
            System.out.println("deleteMe about to update transaction");
            System.out.println("deleteMe got here 10");
            cloneRotatedMediaAssetForDetection(mediaAsset, myShepherd);
            myShepherd.updateDBTransaction(); //update before that redoAllChildren knows which labels to actually apply
            System.out.println("deleteMe got here 11");
            mediaAsset.redoAllChildren(myShepherd);
            System.out.println("deleteMe got here 12");
            myShepherd.updateDBTransaction();
            ArrayList<String> labelsAfterUpdate = mediaAsset.getLabels();
            if(labelsAfterUpdate!=null && labelsAfterUpdate.size()>0) System.out.println("deleteMe labelsAfterUpdate are: " + labelsAfterUpdate.toString());

            System.out.println("deleteMe got here 13");
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

  public void cloneRotatedMediaAssetForDetection(MediaAsset mediaAsset, Shepherd myShepherd){
    List<MediaAsset> masterChildren = mediaAsset.findChildrenByLabel(myShepherd, "_master");
    if(masterChildren != null && masterChildren.size()>0){
      if(masterChildren.size()>1){
        //TODO ack!?
        System.out.println("deleteMe should not be here. More than one master child!");
      } else{
        MediaAsset masterChild = masterChildren.get(0);
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
            System.out.println("Error creating file in cloneRotatedMediaAssetForDetection");
            e.printStackTrace();
          }
          try{
            Files.copy(currentPath, newFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            JSONObject sp = astore.createParameters(newFile);
            MediaAsset newParent = null;
            try {
              newParent = astore.copyIn(newFile, sp);
            } catch (Exception e) {
              System.out.println("Error copying in the new asset in cloneRotatedMediaAssetForDetection");
              e.printStackTrace();
            }
            newParent.updateMetadata();
            newParent.addLabel("_original");
            MediaAssetFactory.save(newParent, myShepherd);
            newParent.updateStandardChildren(myShepherd);
            Encounter enc = myShepherd.getEncounter(mediaAsset);
            if(enc != null){
              enc.addMediaAsset(newParent);
              Task task = org.ecocean.ia.IA.intakeMediaAssets(myShepherd, enc.getMedia(), new Task());
              myShepherd.storeNewTask(task);
            }

          } catch (Exception e){
            System.out.println("Error copying file in cloneRotatedMediaAssetForDetection method of MediaAssetModify.java");
            e.printStackTrace();
          }

        }
      }
    }
  }
}
