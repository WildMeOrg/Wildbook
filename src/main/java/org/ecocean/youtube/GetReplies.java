package org.ecocean.youtube;
//import java.io.BufferedReader;
import java.io.IOException;
//import java.io.InputStreamReader;
import java.util.List;
//import java.util.HashMap;

import org.ecocean.CommonConfiguration;
import org.ecocean.servlet.ServletUtilities;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.Comment;
import com.google.api.services.youtube.model.CommentSnippet;
//import com.google.api.services.youtube.model.CommentThread;
import com.google.api.services.youtube.model.CommentListResponse;
//import com.google.api.services.youtube.model.CommentThreadListResponse;
//import com.google.common.collect.Lists;

public class GetReplies {
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

	public static void main(String[] args) throws IOException {
	    YouTube youtube = getYouTubeService();
	    try {
//	        HashMap<String, String> parameters = new HashMap<>();
//	        parameters.put("part", "snippet");
//	        parameters.put("parentId", "z13icrq45mzjfvkpv04ce54gbnjgvroojf0");
//	        YouTube.Comments.List commentsListRequest = youtube.comments().list(parameters.get("part").toString());
//	        if (parameters.containsKey("parentId") && parameters.get("parentId") != "") {
//	            commentsListRequest.setParentId(parameters.get("parentId").toString());
//	        }
//	        CommentListResponse response = commentsListRequest.execute();	        
//	        System.out.println(response);
	    	
	    	
	        
	        CommentListResponse commentsListResponse = youtube.comments().list("snippet")
                    .setParentId("z13lchyolwvgxz5yw04cifdossigzb4wof4").setTextFormat("plainText").execute();
            List<Comment> comments = commentsListResponse.getItems();

            if (comments.isEmpty()) {
                System.out.println("Can't get comment replies.");
            }else {
                // Print information from the API response.
                System.out
                        .println("\n================== Returned Comment Replies ==================\n");
                for (Comment commentReply : comments) {
                    CommentSnippet snippet = commentReply.getSnippet();
                    System.out.println("  - Author: " + snippet.getAuthorDisplayName());
                    System.out.println("  - Comment: " + snippet.getTextDisplay());
                    System.out
                            .println("\n-------------------------------------------------------------\n");
                } 
	        
	    }
	}catch (GoogleJsonResponseException e) {
        e.printStackTrace();
        System.err.println("There was a service error: " + e.getDetails().getCode() + " : " + e.getDetails().getMessage());
    } catch (Throwable t) {
        t.printStackTrace();
    }
	    
  }
	    
}
