package org.ecocean.ai.servlet.export;


import javax.jdo.annotations.Query;
import javax.servlet.*;
import javax.servlet.http.*;

import java.io.*;
import java.util.*;

import org.apache.commons.lang3.StringEscapeUtils;
import org.ecocean.*;
import org.ecocean.media.*;
import org.ecocean.servlet.ServletUtilities;

import java.lang.StringBuffer;

import org.ecocean.ai.nmt.azure.DetectTranslate;
import org.ecocean.ai.utilities.AIUtilities;
import org.ecocean.identity.IBEISIA;

public class ExportWekaPredictorARFF extends HttpServlet{

  
  public void init(ServletConfig config) throws ServletException {
      super.init(config);
    }

  
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException,IOException {
      doPost(request, response);
  }
  
  private static boolean hasRunDetection(MediaAsset ma, Shepherd myShepherd){
    List<MediaAsset> children=YouTubeAssetStore.findFrames(ma, myShepherd);
    if(children!=null){
      int numChildren=children.size();
      for(int i=0;i<numChildren;i++){
        MediaAsset child=children.get(i);
        if((child.getDetectionStatus()!=null)&&(child.getDetectionStatus().equals(IBEISIA.STATUS_COMPLETE))){
          return true;
        }
      }
    }
    return false;
  }
    


  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException{
    
    
    
    
    //set the response
    String context=ServletUtilities.getContext(request);
    
    //setup the output dir
    File dataDir = CommonConfiguration.getDataDirectory(request.getSession().getServletContext(), context);
    File wekaModels = new File(dataDir,"wekaModels");
    if(!wekaModels.exists())wekaModels.mkdir();
    File outputFile=new File(wekaModels,"youTubePredictor.arff");
    
    //let's return a response to let the user know we've started
    //this servlet takes a long time to run
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    out.println(ServletUtilities.getHeader(request));  
    out.println("<p><strong>Export underway. It's a slow process. When it is done, you will find it at: "+outputFile.getAbsolutePath()+"</strong></p>");
    out.println(ServletUtilities.getFooter(context));
    out.close();
    
    
    
    Shepherd myShepherd = new Shepherd(context);
    myShepherd.setAction("ExportWekaPredictorARFF.class");
    
    myShepherd.beginDBTransaction();

    int numFixes=0;

    try{
      
        String filter="SELECT FROM org.ecocean.media.MediaAsset WHERE store instanceof org.ecocean.media.YouTubeAssetStore";
        
        
        javax.jdo.Query query = myShepherd.getPM().newQuery(filter);
        Collection c = (Collection) (query.execute());
        ArrayList<MediaAsset> results=new ArrayList<MediaAsset>(c);
        //Long result=(Long)query.execute();
        //int numResults=result.intValue();
        query.closeAll();
        
        ArrayList<MediaAsset> notRunYoutubeAssets=new ArrayList<MediaAsset>();
        
        for(int i=0;i<results.size();i++){
          MediaAsset mas=results.get(i);
          if(!hasRunDetection(mas,myShepherd)){
            results.remove(i);
            notRunYoutubeAssets.add(mas);
            i--;
          }       
          
        }
        
        
        int numResults=results.size();
        StringBuffer sb=new StringBuffer();
        
        String header="%\n%Num YouTube MediaAssets (videos) cataloged: "+numResults+"\n";
        sb.append(header);
        

        
        ArrayList<MediaAsset> poorDataVideos=new ArrayList<MediaAsset>();
        ArrayList<MediaAsset> goodDataVideos=new ArrayList<MediaAsset>();
        

          sb.append("@RELATION YouTubeWhaleShark\n\n@ATTRIBUTE description String\n@ATTRIBUTE class {good,poor}\n\n@data\n");

        int gotLangCode = 0;
        for(int i=0;i<numResults;i++){
          
          //YouTubeAsset itself
          MediaAsset ma=results.get(i);

                //JSONObject data=md.getData();
                if ((ma.getMetadata() != null)) {
                  MediaAssetMetadata md = ma.getMetadata(); 
                  if (md.getData() != null) {
                  
                  String videoID=ma.getMetadata().getData().getJSONObject("detailed").optString("id");
            String videoTitle="[unknown]";
            String videoTitleShort=videoTitle;
            if(videoTitle.length()>1000){videoTitleShort=videoTitle.substring(0,1000);}
            if(md.getData().optJSONObject("basic") != null){
              videoTitle=md.getData().getJSONObject("basic").optString("title");
              
              
            }
            String videoDescription="[no description]";
            String videoDescriptionShort=videoDescription;
            if(videoDescription.length()>1000){videoDescriptionShort=videoDescription.substring(0,1000);}
            String videoTags="[no tags]";
            String videoTagsShort=videoTags;
            if(videoTags.length()>1000){videoTagsShort=videoTags.substring(0,1000);}
            if(md.getData().getJSONObject("detailed")!=null){
              videoDescription=md.getData().getJSONObject("detailed").optString("description");
              videoTags=md.getData().getJSONObject("detailed").getJSONArray("tags").toString();                    
            }
              String qFilter="SELECT FROM org.ecocean.Encounter WHERE (occurrenceRemarks.indexOf('"+videoID+"') != -1) && ( state=='approved' || state=='unidentifiable')";
              javax.jdo.Query newQ=myShepherd.getPM().newQuery(qFilter);
              Collection d=(Collection)newQ.execute();
              ArrayList<Encounter> encresults=new ArrayList<Encounter>(d);
              newQ.closeAll();
              int numEncs=encresults.size();


              if(numEncs>0){
                goodDataVideos.add(ma);
                
                //detect and translate
                videoTitle=DetectTranslate.translateIfNotEnglish(videoTitle);
                videoTags=DetectTranslate.translateIfNotEnglish(videoTags);
                videoDescription=DetectTranslate.translateIfNotEnglish(videoDescription);   
                
    
                  sb.append("'"+AIUtilities.youtubePredictorPrepareString(videoTitle+" "+videoDescription+" "+videoTags)+"',good \n");
                    
                
              }
              else{
                
                
                  String pFilter="SELECT FROM org.ecocean.Encounter WHERE (occurrenceRemarks.indexOf('"+videoID+"') != -1) && ( state=='auto_sourced')";
                  javax.jdo.Query newP=myShepherd.getPM().newQuery(pFilter);
                  Collection e=(Collection)newP.execute();
                  ArrayList<Encounter> encresults2=new ArrayList<Encounter>(e);
                  newP.closeAll();
                if(encresults2.size()==0){
                  poorDataVideos.add(ma);
                  
                    //detect and translate
                    //detect and translate
                videoTitle=DetectTranslate.translateIfNotEnglish(videoTitle);
                videoTags=DetectTranslate.translateIfNotEnglish(videoTags);
                videoDescription=DetectTranslate.translateIfNotEnglish(videoDescription); 

                    sb.append("'"+StringEscapeUtils.escapeEcmaScript(videoTitle+" "+videoDescription+" "+videoTags)+"',poor\n");

                }
              }
              
              
                  
                  
                  }
                }

          
          

          
        
        }
        
        sb.append(
           "%Num discard assets (negative training data): "+poorDataVideos.size()+"\n"+
           "%Num approved assets (positive training data): "+goodDataVideos.size()+"\n"
         );

         
          BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile));
          writer.write(sb.toString());
          writer.close();

    }
    catch(Exception e){


      e.printStackTrace();
    }
    finally{
      myShepherd.rollbackDBTransaction();
      myShepherd.closeDBTransaction();

    }

  }
  
  }
