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
import java.util.HashMap;
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
//import com.google.api.services.youtube.YouTube;
//import com.google.api.services.samples.youtube.cmdline.Auth;
import com.google.api.services.youtube.model.*;
import com.google.api.services.youtube.YouTube.CommentThreads;

import org.ecocean.media.*;

import java.util.ArrayList;

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
        Properties googleProps = ShepherdProperties.getProperties("googleKeys.properties","");
      
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
            
            search.setFields("items(id/kind,id/videoId,snippet/title,snippet/thumbnails/default/url,snippet/description)");
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
    
    
    public static String getVideoComments(Occurrence occur, String context) {  //pubAfter is ms since epoch
      //if (!isActive2()) throw new RuntimeException("YouTube API refresh token not active (invalid token?)");
      //if (youtube == null) throw new RuntimeException("YouTube API google credentials 'youtube2' is null");
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

        CommentThreadListResponse response = commentThreadsListByVideoIdRequest.execute();
        return response.toString();
      }
      catch (GoogleJsonResponseException e) {
        e.printStackTrace();
        System.err.println("There was a service error: " + e.getDetails().getCode() + " : " + e.getDetails().getMessage());
      } 
      catch (Throwable t) {
        t.printStackTrace();
      }
      return null;
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
    
    
    
    
}


