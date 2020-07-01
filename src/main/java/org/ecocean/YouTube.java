package org.ecocean;

//import javax.servlet.http.HttpServletRequest;
import java.net.URL;
import org.json.JSONObject;
//import org.ecocean.ParseDateLocation.ParseDateLocation;
import org.ecocean.media.AssetStoreType;
//import org.ecocean.servlet.ServletUtilities;
//import org.ecocean.translate.DetectTranslate;

import java.io.File;
import java.util.List;
import java.util.Properties;
//import java.util.Properties;
//import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.io.IOException;
import java.net.MalformedURLException;
import java.security.NoSuchAlgorithmException;
import java.security.InvalidKeyException;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
//import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;

//import com.google.api.services.samples.youtube.cmdline.Auth;
import com.google.api.services.youtube.model.*;
import com.google.api.services.youtube.YouTube.CommentThreads;

import org.ecocean.ai.nlp.SUTime;
import org.ecocean.ai.nmt.azure.DetectTranslate;
import org.ecocean.media.*;

import java.util.ArrayList;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import java.text.SimpleDateFormat;

import java.util.concurrent.atomic.AtomicInteger;

import javax.jdo.Query;
import javax.servlet.http.HttpServletRequest;


// see: https://developers.google.com/youtube/v3/code_samples/java#search_by_keyword

public class YouTube {
    private static String apiKey = null;
    private static String refreshToken = null;
    private static com.google.api.services.youtube.YouTube youtubeOauthCreds;
    private static com.google.api.services.youtube.YouTube youtubeAPIKey;
    public static final double EXTRACT_FPS = 0.5;  //note: this *must* be synced with value in config/youtube_extract.sh

  //private String storyMediaURL;
  //public void setStoryTellerEmail(String email){this.storyTellerEmail=email;}

    public static void init(String context) {
        //String context = ServletUtilities.getContext(request);
        Properties googleProps = org.ecocean.ShepherdProperties.getProperties("googleKeys.properties","");
      
        apiKey = googleProps.getProperty("youtube_api_key");
        
        youtubeAPIKey = new com.google.api.services.youtube.YouTube.Builder(new NetHttpTransport(), new JacksonFactory(), new HttpRequestInitializer() {
          public void initialize(com.google.api.client.http.HttpRequest request) throws IOException {
          }
        }).setApplicationName("wildbook-youtube").build();
        
        String CLIENT_ID= googleProps.getProperty("youtube_client_id");
        String CLIENT_SECRET= googleProps.getProperty("youtube_client_secret");;
        System.out.println("CLIENT_ID: "+CLIENT_ID);
        System.out.println("CLIENT_SECRET: "+CLIENT_SECRET);
        refreshToken = googleProps.getProperty("refresh_token");
        System.out.println("refreshToke: "+refreshToken);
        HttpTransport HTTP_TRANSPORT = new NetHttpTransport();
        JsonFactory JSON_FACTORY = new JacksonFactory();
       
        Credential credential = new GoogleCredential.Builder()
            .setTransport(HTTP_TRANSPORT)
            .setJsonFactory(JSON_FACTORY)
            .setClientSecrets(CLIENT_ID, CLIENT_SECRET)
            .build();
        credential.setRefreshToken(refreshToken);
      
        
        youtubeOauthCreds = new com.google.api.services.youtube.YouTube.Builder(
            HTTP_TRANSPORT, JSON_FACTORY, credential)
                .setApplicationName("wildbook-youtube")
                .build();
        
      }

    public static boolean isActive() {
        return (apiKey != null);
    }
    public static boolean isActive2() {
      return (refreshToken !=null);
    }

    public static boolean validId(String id) {
        if (id == null) return false;
        return id.matches("^[a-zA-Z0-9_-]{11}$");
    }

    public static JSONObject simpleInfo(String id) throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException, InvalidKeyException {
        if (!validId(id)) return null;
        URL url = new URL("https://www.youtube.com/oembed?url=http://www.youtube.com/watch?v=" + id + "&format=json");
        return RestClient.get(url);
        //TODO handle 404 for bad ids, etc....
    }

    /* /usr/local/bin/youtube_get.sh ID tempdir
will generate:
-rw-rw-r-- 1 jon jon   253156 Sep  8 00:10 V_ozofiGPJo.jpg
-rw------- 1 jon jon    30282 Sep  8 00:10 V_ozofiGPJo.info.json
-rw-rw-r-- 1 jon jon 36398939 Aug 23 08:13 V_ozofiGPJo.mp4
use .json to populate ma.metadata.detailed  -- note: set .detailed = { processing: true } before background grab
*/

    //this fills a directory with grabbed goodies from youtube (synchronously) and returns a list of Files that are its contents
    public static List<File> grab(String id, File targetDir) {
        if ((id == null) || (targetDir == null)) return null;
        if (!targetDir.isDirectory()) throw new RuntimeException(targetDir + " is not a directory");
        String[] cmd = new String[]{"/usr/local/bin/youtube_get.sh", id, targetDir.toString()};
        ProcessBuilder pb = new ProcessBuilder();
        pb.command(cmd);
System.out.println("before .grab() ===[");

        try {
            Process proc = pb.start();
            proc.waitFor();
System.out.println("]=== done with .grab()");
        } catch (Exception ex) {
            throw new RuntimeException("YouTube.grab(" + id + ", " + targetDir + ") failed: " + ex.toString());
        }

        File[] flist = targetDir.listFiles();
        if ((flist == null) || (flist.length < 1)) throw new RuntimeException(targetDir + " is empty; grab failed");
        return Arrays.asList(flist);
    }

    //this fills a directory with framegrabs from input video file (synchronously) and returns a list of Files that are its contents
    // note: heavy lifting is done by youtube_extract.sh so look there for details (e.g. it will create the dir if needed/possible)
    public static List<File> extractFrames(File videoFile, File targetDir) {
        if ((videoFile == null) || (targetDir == null)) return null;
        if (!videoFile.exists()) throw new RuntimeException(videoFile + " does not exist");
        String[] cmd = new String[]{"/usr/local/bin/youtube_extract.sh", videoFile.toString(), targetDir.toString()};
        ProcessBuilder pb = new ProcessBuilder();
        pb.command(cmd);
System.out.println("before .extractFrames() ===[");

        try {
            Process proc = pb.start();
            proc.waitFor();
System.out.println("]=== done with .extractFrames()");
        } catch (Exception ex) {
            throw new RuntimeException("YouTube.extractFrames(" + videoFile + ", " + targetDir + ") failed: " + ex.toString());
        }

        File[] flist = targetDir.listFiles();
        if ((flist == null) || (flist.length < 1)) throw new RuntimeException(targetDir + " is empty; extractFrames failed");
        return Arrays.asList(flist);
    }


/*
    public static ((((not sure what it returns)))) commentOnVideo(String ytId, String comment) {
    }
*/

    public static List<SearchResult> searchByKeyword(String keyword, String context) {
        return searchByKeyword(keyword, -1, context);
    }
    public static List<SearchResult> searchByKeyword(String keyword, long pubAfter, String context) {  //pubAfter is ms since epoch
        //if (!isActive()) throw new RuntimeException("YouTube API not active (invalid api key?)");
        if (youtubeAPIKey == null) init(context);
        try {
            // Define the API request for retrieving search results.
            com.google.api.services.youtube.YouTube.Search.List search = youtubeAPIKey.search().list("id,snippet");
            search.setKey(apiKey);
            search.setQ(keyword);
            if (pubAfter > 0) search.setPublishedAfter(new com.google.api.client.util.DateTime(pubAfter));

            // Restrict the search results to only include videos. See:
            // https://developers.google.com/youtube/v3/docs/search/list#type
            search.setType("video");

            // To increase efficiency, only retrieve the fields that the application uses.
            
            //TBD - add comments
            
            search.setFields("items(id/kind,id/videoId,snippet/title,snippet/thumbnails/default/url,snippet/description,snippet/publishedAt)");
            search.setMaxResults(50l);

            // Call the API and print results.
            SearchListResponse searchResponse = search.execute();
            return searchResponse.getItems();
        } catch (GoogleJsonResponseException e) {
            System.err.println("ERROR: YouTube.searchByKeyword() had a service error: " + e.getDetails().getCode() + " : " + e.getDetails().getMessage());
        } catch (IOException e) {
            System.err.println("ERROR: YouTube.searchByKeyword() had an IOException: " + e.getCause() + " : " + e.getMessage());
        } catch (Throwable t) {
            t.printStackTrace();
        }
        return null;
    }
    public static String postQuestion(String questionToPost,String videoId, Occurrence occur, String context) { 
//      if (!isActive2()) throw new RuntimeException("YouTube API refresh token not active (invalid token?)");
      
      if(!isActive()) init(context);
      
      //if (youtube == null) throw new RuntimeException("YouTube API google credentials 'youtube2' is null");
      if (youtubeOauthCreds == null) init(context);
      
      try {
        
        HashMap<String, String> parameters = new HashMap<>();
        parameters.put("part", "snippet");
        //The part parameter identifies properties that the API response will include. Set the parameter value to snippet
        //The snippet object contains basic details about the comment
       
        CommentThread commentThread = new CommentThread();
        CommentThreadSnippet snippet = new CommentThreadSnippet();
        Comment topLevelComment = new Comment();
        CommentSnippet commentSnippet = new CommentSnippet();
        //set values for these snippet properties: snippet.videoId and snippet.topLevelComment.snippet.textOriginal
        commentSnippet.set("textOriginal", questionToPost); 
        commentSnippet.set("videoId", videoId);
//        commentSnippet.setVideoId(videoId);

        topLevelComment.setSnippet(commentSnippet);
        snippet.setTopLevelComment(topLevelComment);
        commentThread.setSnippet(snippet);

        com.google.api.services.youtube.YouTube.CommentThreads.Insert commentThreadsInsertRequest = youtubeOauthCreds.commentThreads().insert(parameters.get("part").toString(), commentThread);
        commentThreadsInsertRequest.setKey(apiKey);
        CommentThread response = commentThreadsInsertRequest.execute();
          
//        System.out.println(response);
        String commentId = response.getSnippet().getTopLevelComment().getId();
        occur.setSocialMediaQueryCommentID(commentId);
        return commentId;

    } catch (GoogleJsonResponseException e) {
        e.printStackTrace();
        System.err.println("There was a service error: " + e.getDetails().getCode() + " : " + e.getDetails().getMessage());
    } catch (Throwable t) {
        t.printStackTrace();
    }
    return null;
  }
    
    public static String getReplies(Occurrence occur, String context) {  //pubAfter is ms since epoch
      //if (!isActive2()) throw new RuntimeException("YouTube API refresh token not active (invalid token?)");
      //if (youtube == null) throw new RuntimeException("YouTube API google credentials 'youtube2' is null");
      if (youtubeOauthCreds == null) init(context);
      try {
        String commentId=occur.getSocialMediaQueryCommentID();
        CommentListResponse commentsListResponse = youtubeOauthCreds.comments().list("snippet")
            .setParentId(commentId).setTextFormat("plainText").setKey(apiKey).execute();
        List<Comment> comments = commentsListResponse.getItems();
        String replies = "";
        if (comments.isEmpty()) {
            System.out.println("Can't get comment replies.");
            replies += "none";
            
        }else {
            // Print information from the API response.
            System.out
                    .println("\n================== Returned Comment Replies ==================\n");
            for (Comment commentReply : comments) {       
                CommentSnippet snippet = commentReply.getSnippet();
                replies += snippet.getTextDisplay();
                occur.setSocialMediaQueryCommentReplies(replies);
                
                System.out.println("  - Author: " + snippet.getAuthorDisplayName());
                System.out.println("  - Reply: " + snippet.getTextDisplay());
                System.out
                        .println("\n-------------------------------------------------------------\n");
            } 

            return replies;
        }
      }catch (GoogleJsonResponseException e) {
      e.printStackTrace();
      System.err.println("There was a service error: " + e.getDetails().getCode() + " : " + e.getDetails().getMessage());
      } catch (Throwable t) {
      t.printStackTrace();
      }
        return null;
    }
    
   /* public static String sendReply(String commentId, String commentToPost) {  //pubAfter is ms since epoch
      if (!isActive2()) throw new RuntimeException("YouTube API refresh token not active (invalid token?)");
      if (youtube2 == null) throw new RuntimeException("YouTube API google credentials 'youtube2' is null");
      try {
        HashMap<String, String> parameters = new HashMap<>();
        parameters.put("part", "snippet");
//        CommentThreadListResponse videoCommentsListResponse = youtube.commentThreads()
//                  .list("snippet").setVideoId(videoId).setTextFormat("plainText").execute();
//          List<CommentThread> videoComments = videoCommentsListResponse.getItems();

//          CommentThread firstComment = videoComments.get(0);
          // Will use this thread as parent to new reply.
//          String parentId = firstComment.getId();
          
        String parentId=commentId;
        Comment comment = new Comment();
        CommentSnippet snippet = new CommentSnippet();
        snippet.set("parentId", parentId);
        snippet.set("textOriginal", commentToPost);

        comment.setSnippet(snippet);

        com.google.api.services.youtube.YouTube.Comments.Insert commentsInsertRequest = youtube.comments().insert(parameters.get("part").toString(), comment);
        commentsInsertRequest.setKey(apiKey);
        Comment response = commentsInsertRequest.execute();
        System.out.println(response);
        
        
        String receipt = response.getSnippet().getTextDisplay();
        return receipt;
      }catch (GoogleJsonResponseException e) {
          e.printStackTrace();
          System.err.println("There was a service error: " + e.getDetails().getCode() + " : " + e.getDetails().getMessage());
      } catch (Throwable t) {
          t.printStackTrace();

      }
        return null;
    }
    
    */

    public static void postOccurrenceMessageToYouTubeIfAppropriate(String message, Occurrence occur, Shepherd myShepherd, String context){
        System.out.println("--Entering YouTube.postOccurrenceMessageToYouTubeIfAppropriate");
      
        //validate if we should even be worrying about YouTube mediaassets for this occurrence
        if((occur.getSocialMediaSourceID()!=null)&&(occur.hasMediaAssetFromRootStoreType(myShepherd, AssetStoreType.YouTube))){
          if(!isActive()) init(context);
          System.out.println("This occurrence has a YouTube Media Asset, so let's try to post to the OP the message I was given: "+message);
          //first, does this Occurrence have a commentID?
          String videoID=occur.getSocialMediaSourceID().replaceFirst("youtube:", "");

          String concatReplies=getVideoComments(occur, context);
          if((concatReplies==null)||(concatReplies.indexOf(message)==-1)){
              //we ourselves haven't posted this before (i.e., don't harass user with multiple, similar comments)
              System.out.println("Replying to a previous YouTube comment");

              postQuestion(message,videoID, occur, context);
              
          }
        }
        else{
          System.out.println("This is not a candidate for YouTube feedback.");
        }
        
        System.out.println("--Exiting YouTube.postOccurrenceMessageToYouTubeIfAppropriate");
        
      
    }
    
    /**
     * Returns a long String of comments and replies for an Occurrence that was derived from a YouTube video.
     * Use this String for generic NLP processing
     * 
     * @param occur The Occurrence that derived from a YouTube video.
     * @param context Standard context used in Wildbook.
     * @return A long, pretty-print String of comments and replies without hierarchy.
     */
    public static String getVideoComments(Occurrence occur, String context) {  //pubAfter is ms since epoch
      StringBuffer response=new StringBuffer("");
      if (youtubeOauthCreds == null) init(context);
      try {

        java.util.List<CommentThread> comments=getVideoCommentsList(occur, context);
        int numComments=comments.size();
        for(int f=0;f<numComments;f++) {
            CommentThread ct=comments.get(f);
            CommentThreadReplies ctr=ct.getReplies();
            List<Comment> replies=ctr.getComments();
            int numReplies=replies.size();
            CommentThreadSnippet cts=ct.getSnippet();
            Comment topLevelComment=cts.getTopLevelComment();
            response.append(topLevelComment.getSnippet().getTextDisplay());
            for(int g=0;g<numReplies;g++) {
              Comment reply=replies.get(g);
              response.append(" "+reply.getSnippet().getTextDisplay());
            }
            
        }
        return response.toString();
      }

      catch (Exception t) {
        t.printStackTrace();
      }
      return null;
    }
    
    /**
     * Returns an array of comments and replies for an Occurrence that was derived from a YouTube video.
     * Use this array for fine-grained NLP processing.
     * 
     * @param occur The Occurrence that derived from a YouTube video.
     * @param context Standard context used in Wildbook.
     * @return An array of comments. You can iterate through each comment looking for replies.
     */
    public static List<CommentThread> getVideoCommentsList(Occurrence occur, String context) {  //pubAfter is ms since epoch
      List<CommentThread> comments=new ArrayList<CommentThread>();
      if (youtubeOauthCreds == null) init(context);
      try {
        HashMap<String, String> parameters = new HashMap<>();
        parameters.put("part", "snippet,replies");
        String videoID=occur.getSocialMediaSourceID().replaceFirst("youtube:", "");
        parameters.put("videoId", videoID);

        CommentThreads.List commentThreadsListByVideoIdRequest = youtubeOauthCreds.commentThreads().list(parameters.get("part").toString());
        if (parameters.containsKey("videoId") && parameters.get("videoId") != "") {
            commentThreadsListByVideoIdRequest.setVideoId(parameters.get("videoId").toString());
        }

        CommentThreadListResponse commentsResponse = commentThreadsListByVideoIdRequest.execute();
        comments=commentsResponse.getItems();

      }
      catch (GoogleJsonResponseException e) {
        e.printStackTrace();
        System.err.println("There was a service error: " + e.getDetails().getCode() + " : " + e.getDetails().getMessage());
      } 
      catch (Throwable t) {
        t.printStackTrace();
      }
      return comments;
    }
    
    
    
    
    //Given an Occurrence created from a YouTube video, return the video description
    public static String getVideoDescription(Occurrence occur, Shepherd myShepherd) {
      String desc=null;
      if((occur!=null)&&(occur.hasMediaAssetFromRootStoreType(myShepherd, AssetStoreType.YouTube))){
        if((occur.getEncounters()!=null)&&(occur.getEncounters().size()>0)) {
          Encounter enc=occur.getEncounters().get(0);
          ArrayList<MediaAsset> assets=enc.getMedia();
          if(assets!=null) {
            MediaAsset ma=assets.get(0);
            MediaAsset parentRoot=ma.getParentRoot(myShepherd);
              MediaAssetMetadata mdata=ma.getMetadata();
              JSONObject data=mdata.getData();
                if ((parentRoot.getMetadata() != null) && (parentRoot.getMetadata().getData() != null)) {
                    if (parentRoot.getMetadata().getData().optJSONObject("detailed") != null) {
                        desc = parentRoot.getMetadata().getData().getJSONObject("detailed").optString("description", "[no description]"); 
                    }
                }
              
              }
          }
      }
      return desc;
    }
    
  //Given an Occurrence created from a YouTube video, return the video tags
    public static String getVideoTags(Occurrence occur, Shepherd myShepherd) {
      String tags=null;
      if((occur!=null)&&(occur.hasMediaAssetFromRootStoreType(myShepherd, AssetStoreType.YouTube))){
        if((occur.getEncounters()!=null)&&(occur.getEncounters().size()>0)) {
          Encounter enc=occur.getEncounters().get(0);
          ArrayList<MediaAsset> assets=enc.getMedia();
          if(assets!=null) {
            MediaAsset ma=assets.get(0);
            MediaAsset parentRoot=ma.getParentRoot(myShepherd);
              MediaAssetMetadata mdata=ma.getMetadata();
              JSONObject data=mdata.getData();
                if ((parentRoot.getMetadata() != null) && (parentRoot.getMetadata().getData() != null)) {
                  if (parentRoot.getMetadata().getData().getJSONObject("detailed").optJSONArray("tags") != null) {
                    tags = parentRoot.getMetadata().getData().getJSONObject("detailed").getJSONArray("tags").toString(); 
                    }
                }
              
              }
          }
      }
      return tags;
    }
    
    //Given an Occurrence created from a YouTube video, return the video title
    public static String getVideoTitle(Occurrence occur, Shepherd myShepherd) {
      String title=null;
      if((occur!=null)&&(occur.hasMediaAssetFromRootStoreType(myShepherd, AssetStoreType.YouTube))){
        if((occur.getEncounters()!=null)&&(occur.getEncounters().size()>0)) {
          Encounter enc=occur.getEncounters().get(0);
          ArrayList<MediaAsset> assets=enc.getMedia();
          if(assets!=null) {
            MediaAsset ma=assets.get(0);
            MediaAsset parentRoot=ma.getParentRoot(myShepherd);
              MediaAssetMetadata mdata=ma.getMetadata();
              JSONObject data=mdata.getData();
                if ((parentRoot.getMetadata() != null) && (parentRoot.getMetadata().getData() != null)) {
                  if (parentRoot.getMetadata().getData().optJSONObject("basic") != null) {
                    title=parentRoot.getMetadata().getData().getJSONObject("basic").optString("title", "[unknown]");
                   }
                }
              
              }
          }
      }
      return title;
    }
    
    
    public static String getVideoPublishedAt(Occurrence occur, String context) {  //pubAfter is ms since epoch
      if(!isActive())init(context);
      StringBuffer response=new StringBuffer("");
      if (youtubeOauthCreds == null) init(context);
      try {
        HashMap<String, String> parameters = new HashMap<>();
        parameters.put("part", "snippet,replies");
        String videoID=occur.getSocialMediaSourceID().replaceFirst("youtube:", "");
        parameters.put("part", "snippet,contentDetails");
        parameters.put("id", videoID);
        
        //System.out.println("Trying to get publishedAt for video: "+videoID);

        com.google.api.services.youtube.YouTube.Videos.List listVideosRequest = youtubeAPIKey.videos().list(parameters.get("part").toString());
        listVideosRequest.setId(videoID); // add list of video IDs here
        listVideosRequest.setKey(apiKey);
        VideoListResponse listResponse = listVideosRequest.execute();

        Video vid = listResponse.getItems().get(0);
        //System.out.println("I have a video!");
        response.append(vid.getSnippet().getPublishedAt().toString());
        
        //System.out.println("PublishedAt: "+ response);

        return response.toString();
      }
      catch (GoogleJsonResponseException e) {
        e.printStackTrace();
        System.err.println("There was a service error in publishedAt: " + e.getDetails().getCode() + " : " + e.getDetails().getMessage());
      } 
      catch (Throwable t) {
        t.printStackTrace();
      }
      return null;
    }
    
    /*
     * Given a MediaAsset from a YouTubeAssetStore, annotate the Encounters of its derived Occurrence with any
     * Encounter.locationID and Encounter date information that can be extracted from the title, comments, tags, and descriptions, including
     * replies to our own automated comments. 
     * 
     * This is the simple form of the method for calling without megtrics assessment.
     * 
     * 
     */
    public static void annotateChildrenOfYouTubeMediaAssetWithDateLocation(MediaAsset ma, String rootDir, Shepherd myShepherd, String context){   
      annotateChildrenOfYouTubeMediaAssetWithDateLocation(ma, rootDir, myShepherd, context, new AtomicInteger(0) ,new AtomicInteger(0) , new AtomicInteger(0) , new AtomicInteger(0) ,new AtomicInteger(0) ,new ArrayList<MediaAsset>(),new ArrayList<MediaAsset>(), true, new AtomicInteger(0) , new AtomicInteger(0) );   
    }
    
    
    /*
     * Given a MediaAsset from a YouTubeAssetStore, annotate the Encounters of its derived Occurrence with any
     * Encounter.locationID and Encounter date information that can be extracted from the title, comments, tags, and descriptions, including
     * replies to our own automated comments. 
     * 
     * This method includes the ability to pass in variables for counting/statistics purposes and to test the method with the ability to not persist changes.
     *
     * @return An HTML table row of data <tr> about the video, Encounter(s), and comemnts posted on YouTube.  
     * 
     */
    public static String annotateChildrenOfYouTubeMediaAssetWithDateLocation(MediaAsset ma, String rootDir, Shepherd myShepherd, String context, AtomicInteger numVideosWithID,AtomicInteger numVideos, AtomicInteger numUncuratedVideos, AtomicInteger numCommentedVideos,AtomicInteger numCommentedVideosReplies,ArrayList<MediaAsset> goodDataVideos,ArrayList<MediaAsset> poorDataVideos, boolean persistDifferences, AtomicInteger numDatesFound, AtomicInteger numLocationIDsFound){

      //if we're going to persist changes, ensure the Shepherd object is ready
      if(persistDifferences && !myShepherd.getPM().currentTransaction().isActive()){
        myShepherd.beginDBTransaction();
      }
      
      //the return string of HTML content
      String resultsHTML="";
      
      
      /*
      //weka predictor preparation answering the question: does this video description suggest a real world whale shark sighting?
      ArrayList<Attribute> attributeList = new ArrayList<Attribute>(2);
      ArrayList<Attribute> attributeList2 = new ArrayList<Attribute>(2);
      Attribute desc = new Attribute("description", true);
      Attribute merged = new Attribute("merged", true);
      List<String> classVal2 = myShepherd.getAllLocationIDs();
      classVal2.remove(0);
      ArrayList<String> classVal = new ArrayList<String>();
      classVal.add("good");
      classVal.add("poor");
      attributeList2.add(desc);
      attributeList2.add(new Attribute("@@class@@",classVal2));
      attributeList.add(merged);
      attributeList.add(new Attribute("@@class@@",classVal));
      String locIDpath="/data/whaleshark_data_dirs/shepherd_data_dir/wekaModels/whaleSharkLocationIDClassifier.model";
      String path="/data/whaleshark_data_dirs/shepherd_data_dir/wekaModels/youtubeRandomForest.model";
    */
      
      boolean videoHasID=false;
      boolean hasWildMeComment=false;
      boolean hasWildMeCommentReplies=false;
      String relativeDate = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        
      //video has metadata for analysis?
        if ((ma.getMetadata() != null)) {
          numVideos.incrementAndGet();
          MediaAssetMetadata md = ma.getMetadata(); 
          
          //video metadata is not null, so proceed
          if (md.getData() != null) {
          
          //setup our metadata fields  
          String videoID=ma.getMetadata().getData().getJSONObject("detailed").optString("id");
        String videoTitle="[unknown]";
        String videoTitleShort=videoTitle;
        String videoComments="";
        String videoCommentsClean="";
        String locIDWords="";
        String videoDescription="[no description]";
        String videoTags="[no tags]";
        
        //start capturing metadata about the YouTube video
        
        //video title
        if(videoTitle.length()>1000){videoTitleShort=videoTitle.substring(0,1000);}
        if(md.getData().optJSONObject("basic") != null){
          videoTitle=md.getData().getJSONObject("basic").optString("title").replaceAll(",", " ").replaceAll("\n", " ").replaceAll("'", "").replaceAll("\"", "").replaceAll("′","").replaceAll("’","").toLowerCase();
        }
        
        //video description
        String videoDescriptionShort=videoDescription;
        if(videoDescription.length()>1000){videoDescriptionShort=videoDescription.substring(0,1000);}
        
        //video tags
        String videoTagsShort=videoTags;
        if(videoTags.length()>1000){videoTagsShort=videoTags.substring(0,1000);}
        if(md.getData().getJSONObject("detailed")!=null){
          videoDescription=md.getData().getJSONObject("detailed").optString("description").replaceAll(",", " ").replaceAll("\n", " ").replaceAll("'", "").replaceAll("\"", "").replaceAll("’","").replaceAll("′","").toLowerCase();
          videoTags=md.getData().getJSONObject("detailed").getJSONArray("tags").toString().replaceAll(",", " ").replaceAll("\n", " ").replaceAll("'", "").replaceAll("\"", "").replaceAll("′","").replaceAll("’","").toLowerCase();   
        }
        
        //Let's get the Encounter objects related to this video
        //JDOQL query
        String qFilter="SELECT FROM org.ecocean.Encounter WHERE (occurrenceRemarks.indexOf('"+videoID+"') != -1)";
        Query newQ=myShepherd.getPM().newQuery(qFilter);
        Collection d=(Collection)newQ.execute();
        ArrayList<Encounter> encresults=new ArrayList<Encounter>(d);
        newQ.closeAll();
        int numEncs=encresults.size();
        
        //let's iterate our matching Encounters
        //first, check if any have been approved (curated) and count them
        boolean videoIsCurated=false;
        for(int y=0;y<numEncs;y++){
          Encounter enc=encresults.get(y);
          if((enc.getState()!=null)&&((enc.getState().equals("approved"))||(enc.getState().equals("unidentifiable")))){
            if(!goodDataVideos.contains(ma))goodDataVideos.add(ma);
            videoIsCurated=true;
          }

          if((enc.getIndividualID()!=null)&&(!enc.getIndividualID().equals("")))videoHasID=true;
        }
        if(!videoIsCurated)numUncuratedVideos.incrementAndGet();
        
        
        Occurrence occur=null;    
        LinkedProperties props=(LinkedProperties)ShepherdProperties.getProperties("submitActionClass.properties", "",context);

        String chosenStyleDate="";
        String chosenStyleLocation="";
        
        //if we have matching encounters, then the video is either uncurated, or it has been determined to have useful data (curated)
        if(numEncs>0){
          
          //check for Occurrence
          String occurID="";
              
          //grab the first Encounter for analysis   
          Encounter enc=encresults.get(0);
              
          //get the current values for date and location ID   
          String currentDate="";
          String currentLocationID="";
          if(enc.getDate()!=null)currentDate=enc.getDate().replaceAll("Unknown", ""); 
          if(enc.getLocationID()!=null)currentLocationID=enc.getLocationID().replaceAll("None", "");  
          
          //our encounters should all have an Occurrence, one per video
          if(enc.getOccurrenceID()!=null){
            occur=myShepherd.getOccurrence(enc.getOccurrenceID());
            
            
            //let's get all our YouTube video metadata and comments
            List<CommentThread> comments=YouTube.getVideoCommentsList(occur, context);
            if((comments==null)||(comments.size()==0)){
              videoComments="";
              videoCommentsClean="";
            }
            else{
              boolean isWildMeComment=false;
                  int numComments=comments.size();
                  videoComments+="<ul>\n";
              for(int f=0;f<numComments;f++) {
                      CommentThread ct=comments.get(f);

                      CommentThreadSnippet cts=ct.getSnippet();
                      
                      Comment topLevelComment=cts.getTopLevelComment();
                      CommentSnippet commentSnippet=topLevelComment.getSnippet();
                      String authorName="";
                      if((commentSnippet!=null)&&(commentSnippet.getAuthorDisplayName()!=null)){
                        authorName=commentSnippet.getAuthorDisplayName();
                        if(authorName.equals("Wild Me"))isWildMeComment=true;
                      }
                      String style="";
                          if(isWildMeComment){
                            style="color: green;font-weight: bold;";
                            hasWildMeComment=true;
                          }
                      videoComments+="<li style=\""+style+"\">"+authorName+": "+DetectTranslate.translateIfNotEnglish(topLevelComment.getSnippet().getTextDisplay());
                      
                      videoCommentsClean+=DetectTranslate.translateIfNotEnglish(topLevelComment.getSnippet().getTextDisplay()).toLowerCase()+" ";
                      
                      
                      if(ct.getReplies()!=null){
                         CommentThreadReplies ctr=ct.getReplies();
                       
                        List<Comment> replies=ctr.getComments();
                        int numReplies=0;
                        if(replies!=null)numReplies=replies.size();
                        if(numReplies>0){
                          if(isWildMeComment)hasWildMeCommentReplies=true;
                          videoComments+="<ul>\n";
                            for(int g=0;g<numReplies;g++) {
                            
                              Comment reply=replies.get(g);
                              
                              videoComments+="<li>"+DetectTranslate.translateIfNotEnglish(reply.getSnippet().getTextDisplay())+"</li>";
                              videoCommentsClean+=DetectTranslate.translateIfNotEnglish(reply.getSnippet().getTextDisplay()).toLowerCase()+" ";
                                
                             }
                            videoComments+="</ul>\n";
                        }
                       }

                      videoComments+="</li>\n";
                      style="";
                      
                  }
              videoComments+="</ul>\n";
              
            }
            
            
            occurID=occur.getOccurrenceID();

            //prep the YouTube video date for SUTimee analysis
            String tempRelativeDate=null;
            try{    
              tempRelativeDate=YouTube.getVideoPublishedAt(occur, context);
            }
            catch(Exception e){}
            if((tempRelativeDate!=null)&&(tempRelativeDate.indexOf("T")!=-1)){
              tempRelativeDate=tempRelativeDate.substring(0,tempRelativeDate.indexOf("T"));
            }
            if((tempRelativeDate!=null)&&(!tempRelativeDate.equals(""))){
              DateTimeFormatter parser2 = DateTimeFormat.forPattern("yyyy-MM-dd");
              DateTime time = parser2.parseDateTime(tempRelativeDate);
              relativeDate=time.toString(parser2);  
            }
            
            
          }
          
          StringBuffer sbOriginalText=new StringBuffer("");
          sbOriginalText.append(videoTitle+" "+videoDescription+" "+videoTags+" "+videoCommentsClean);
          
          //let's do some translation to English for standardization
          videoTitle=DetectTranslate.translateIfNotEnglish(videoTitle);
          videoTags=DetectTranslate.translateIfNotEnglish(videoTags);
          videoDescription=DetectTranslate.translateIfNotEnglish(videoDescription); 
          //videoComments=translateIfNotEnglish(videoComments);
          
          StringBuffer sb=new StringBuffer("");
          
          sb.append(videoTitle+" "+videoDescription+" "+videoTags+" "+videoCommentsClean);
          

          //get video date with SUTime
          String newDetectedDate="";
          try{
            newDetectedDate=SUTime.parseDateStringForBestDate(rootDir, sb.toString(), relativeDate).replaceAll("null","");
          }
          catch(Exception e){}
          if(!newDetectedDate.equals("")){numDatesFound.incrementAndGet();}
          
          //determine new LocationID, including comments
          String newLocationID="";
          String lowercaseRemarks=sb.toString().toLowerCase();
          try{
                    
                    Iterator m_enum = props.orderedKeys().iterator();
                    while (m_enum.hasNext()) {
                      String aLocationSnippet = ((String) m_enum.next()).replaceFirst("\\s++$", "");
                      //System.out.println("     Looking for: "+aLocationSnippet);
                      if (lowercaseRemarks.indexOf(aLocationSnippet) != -1) {
                        newLocationID = props.getProperty(aLocationSnippet);
                        locIDWords+=" "+ aLocationSnippet;
                        //System.out.println(".....Building an idea of location: "+location);
                      }
                    }
                    /*
                    Instances data2 = new Instances("TestInstances",attributeList2,2);
                    data2.setClassIndex(data2.numAttributes()-1);
                    Instance pos2 = new DenseInstance(data2.numAttributes());
                    pos2.setValue(desc, sbOriginalText.toString().replaceAll("[^A-Za-z0-9 ]", "").replace("\n", "").trim());
                    data2.add(pos2);
                    pos2.setDataset(data2);
                    
                    newLocationID=pos2.classAttribute().value(classify(pos2, locIDpath).intValue());
                    */

           }
           catch(Exception e){
                    e.printStackTrace();
                  }
                  if(newLocationID==null)newLocationID="";
                  if(!newLocationID.equals("")){numLocationIDsFound.incrementAndGet();}
                  
                  //predict if this is a good video
                  /*
                  Instances data = new Instances("TestInstances",attributeList,2);
                  data.setClassIndex(data.numAttributes()-1);
                  Instance pos = new DenseInstance(data.numAttributes());
                  pos.setValue(merged, lowercaseRemarks.replaceAll("whale shark", "whaleshark"));
                  data.add(pos);
                  pos.setDataset(data);
                  
                  Double prediction = classify(pos,path);
                  String rowClass="";
                  if(prediction.intValue()==1)rowClass="class=\"rowhighlight\"";
                  */  
            
            //here is where we would put logic to update encounters if appropriate
            if(persistDifferences){
            boolean madeAChange=false;
            
            for(int y=0;y<numEncs;y++){
              Encounter thisEnc=encresults.get(y);
              //chosenStyleDate+="year: "+thisEnc.getYear()+";millis:"+thisEnc.getDateInMilliseconds()+";locationID: "+thisEnc.getLocationID()+";";
              
                //SET LOCATION ID
                //first, if we even found a location ID in comments, lets' consider it.
                //otherwise, there's no point
                
                if((newLocationID!=null)&&(!newLocationID.trim().equals(""))){
                
                //next, if we have a new locationID and one was not set before, then this is an easy win
                  if((thisEnc.getLocationID()==null)||(thisEnc.getLocationID().trim().equals(""))||(thisEnc.getLocationID().trim().equals("None"))){
                    thisEnc.setLocationID(newLocationID);
                    madeAChange=true;
                  }
                  else if(!thisEnc.getLocationID().trim().equals(newLocationID.trim())){
                    //ok, the location IDs are different, now what?
                  
                    //maybe the newLocationID further specifies the older locationID, that would be a win   
                    if(newLocationID.trim().startsWith(thisEnc.getLocationID().trim())){
                      thisEnc.setLocationID(newLocationID.trim());
                      madeAChange=true;
                    }
                    //if the Encounter is not yet approved, then we can reset it as well since it's uncurated and may have been incorrectly detected with older values
                    else if((thisEnc.getState()!=null)&&(thisEnc.getState().equals("auto_sourced"))){
                      thisEnc.setLocationID(newLocationID.trim());
                      madeAChange=true;
                    }
                      
                  }

                
                }

              
              //now persist
              if(madeAChange){
                myShepherd.commitDBTransaction();
                myShepherd.beginDBTransaction();
              }
              if(madeAChange)chosenStyleLocation="font-style: italic;";
                
                
                
                //reset for date
                madeAChange=false;
                
                chosenStyleDate+="madeit: here;";
                //let's check and fix date
                if((newDetectedDate!=null)&&(!newDetectedDate.trim().equals(""))){
                  
                  //well we have something to analyze at least
                  //DateTimeFormatter parser3 = DateTimeFormat.forPattern("yyyy-MM-dd");
                  DateTimeFormatter parser3 = ISODateTimeFormat.dateParser();
                  DateTime dt=parser3.parseDateTime(newDetectedDate);
                  
                  
                  /*
                  if((thisEnc.getMonth()==1)&&(newDetectedDate.length()==4)){
                    thisEnc.setMonth(-1);
                    thisEnc.setDay(-1);
                    madeAChange=true;
                    thisEnc.setHour(-1);
                  }
                  */
                  
                  //check for the easy case
                  if((thisEnc.getDateInMilliseconds()==null)||(thisEnc.getYear()<=0)){
                    
                    if(newDetectedDate.length()==10){
                      thisEnc.setYear(dt.getYear());
                      thisEnc.setMonth(dt.getMonthOfYear());
                      thisEnc.setDay(dt.getDayOfMonth());
                      thisEnc.setHour(-1);
                    }
                    else if(newDetectedDate.length()==7){
                      thisEnc.setYear(dt.getYear());
                      thisEnc.setMonth(dt.getMonthOfYear());
                      thisEnc.setDay(-1);
                      
                    }
                    else if(newDetectedDate.length()==4){
                      thisEnc.setYear(dt.getYear());
                      thisEnc.setMonth(-1);
                      
                    }
                    
                    //thisEnc.setDateInMilliseconds(dt.getMillis());
                    
                    
                    chosenStyleDate+="font-style: italic; color: red;";
                    madeAChange=true;
                  }
                  //if it's unapproved/uncurated, trust the newer value
                  else if(thisEnc.getState().equals("auto_sourced")){
                    
                    if(newDetectedDate.length()==10){
                      thisEnc.setYear(dt.getYear());
                      thisEnc.setMonth(dt.getMonthOfYear());
                      thisEnc.setDay(dt.getDayOfMonth());
                    }
                    else if(newDetectedDate.length()==7){
                      thisEnc.setYear(dt.getYear());
                      thisEnc.setMonth(dt.getMonthOfYear());
                      
                    }
                    else if(newDetectedDate.length()==4){
                      thisEnc.setYear(dt.getYear());
                      
                    }
                    chosenStyleDate+="font-style: italic; color: green;";
                    madeAChange=true;
                  }
                  
                  
                  
                  
                }
                
                //now persist
                if(madeAChange){
                  myShepherd.commitDBTransaction();
                  myShepherd.beginDBTransaction();
                }
                
                
                }

            }
            
            resultsHTML="<tr><td><a target=\"_blank\" href=\"https://www.whaleshark.org/occurrence.jsp?number="+occurID+"\">"+occurID+"</a></td><td><a target=\"_blank\" href=\"https://www.youtube.com/watch?v="+videoID+"\">"+videoID+"</a></td><td>"+currentDate+"</td><td><p style=\""+chosenStyleDate+"\">"+newDetectedDate+"</p></td><td>"+currentLocationID+"</td><td><p style=\""+chosenStyleLocation+"\">"+newLocationID+"</p></td><td>"+videoTitle+"</td><td>"+videoDescription+"</td><td>"+videoComments+"</td><td>"+videoCommentsClean+"<br><br>LocID Words: "+locIDWords+"</br></br></td><td>"+relativeDate+"</td></tr>";   
          
        }
        //this video had no encounters, probably been curated as having no value
        else{
          if(!poorDataVideos.contains(ma)){
            poorDataVideos.add(ma);
            numUncuratedVideos.decrementAndGet();
            
            }
        }
        

        
        
          
          
          }
          //video metadata is null, not much we can do here
          else{if(!poorDataVideos.contains(ma))poorDataVideos.add(ma);}
          
          if(hasWildMeComment)numCommentedVideos.incrementAndGet();
          if(hasWildMeCommentReplies)numCommentedVideosReplies.incrementAndGet();
          
        }
        //video had no metadata, not much we can do here
        else{
          if(!poorDataVideos.contains(ma))poorDataVideos.add(ma);
        }

      
      if(videoHasID)numVideosWithID.incrementAndGet();
      
      return resultsHTML;
    }
    
    
}

