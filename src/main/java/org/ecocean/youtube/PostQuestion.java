package org.ecocean.youtube;
import org.ecocean.CommonConfiguration;
import org.ecocean.servlet.ServletUtilities;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.youtube.model.*;
import com.google.api.services.youtube.YouTube;
import java.io.IOException;
import java.util.HashMap;


public class PostQuestion {

  public static YouTube getYouTubeService() throws IOException {
      String context = ServletUtilities.getContext(request);
      String CLIENT_ID= CommonConfiguration.getProperty("youtube_client_id", context);
      String CLIENT_SECRET= CommonConfiguration.getProperty("youtube_client_secret", context);;
      String refreshToken = CommonConfiguration.getProperty("refresh_token", context);
      
      HttpTransport HTTP_TRANSPORT = new NetHttpTransport();

      JsonFactory JSON_FACTORY = new JacksonFactory();
     
      Credential credential = new GoogleCredential.Builder()
            .setTransport(HTTP_TRANSPORT)
            .setJsonFactory(JSON_FACTORY)
            .setClientSecrets(CLIENT_ID, CLIENT_SECRET)
            .build();
      credential.setRefreshToken(refreshToken);
      
      return new YouTube.Builder(
      HTTP_TRANSPORT, JSON_FACTORY, credential)
          .setApplicationName("wildbook-youtube")
          .build();
  }


  public static CommentThreadSnippet searchQuestId(String QuestionToPost,String videoId) throws IOException {

     try {
         YouTube youtube = getYouTubeService();
         
         HashMap<String, String> parameters = new HashMap<>();
         parameters.put("part", "snippet");
         //The part parameter identifies properties that the API response will include. Set the parameter value to snippet
         //The snippet object contains basic details about the comment
        
         CommentThread commentThread = new CommentThread();
         CommentThreadSnippet snippet = new CommentThreadSnippet();
         Comment topLevelComment = new Comment();
         CommentSnippet commentSnippet = new CommentSnippet();
         //set values for these snippet properties: snippet.videoId and snippet.topLevelComment.snippet.textOriginal
         commentSnippet.set("textOriginal", "Testing changes.");
         commentSnippet.set("videoId", "8ARqat7DtfM");
//         commentSnippet.setVideoId(videoId);

         topLevelComment.setSnippet(commentSnippet);
         snippet.setTopLevelComment(topLevelComment);
         commentThread.setSnippet(snippet);

         YouTube.CommentThreads.Insert commentThreadsInsertRequest = youtube.commentThreads().insert(parameters.get("part").toString(), commentThread);

         CommentThread response = commentThreadsInsertRequest.execute();
         System.out.println(response);
//         return response.getSnippet();

     } catch (GoogleJsonResponseException e) {
         e.printStackTrace();
         System.err.println("There was a service error: " + e.getDetails().getCode() + " : " + e.getDetails().getMessage());
     } catch (Throwable t) {
         t.printStackTrace();
     }
     return null;
 }
}
