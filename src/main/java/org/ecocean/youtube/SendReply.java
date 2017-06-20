package org.ecocean.youtube;
import com.google.api.client.auth.oauth2.Credential;

//import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
//import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
//import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
//import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
//import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.json.JsonFactory;
//import com.google.api.client.util.store.FileDataStoreFactory;

//import com.google.api.services.youtube.YouTubeScopes;
import com.google.api.services.youtube.model.*;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.Comment;
import com.google.api.services.youtube.model.CommentSnippet;
import com.google.api.services.youtube.model.CommentThread;
//import com.google.api.services.youtube.model.CommentListResponse;
import com.google.api.services.youtube.model.CommentThreadListResponse;
//import com.google.common.collect.Lists;


import java.io.IOException;
//import java.io.InputStream;
//import java.io.InputStreamReader;
//import java.util.Arrays;
//import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import org.ecocean.CommonConfiguration;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.*;

public class SendReply {
	
//	public static YouTube getYouTubeService() throws IOException {
//    	  String context = ServletUtilities.getContext(request);
//        String CLIENT_ID= CommonConfiguration.getProperty("youtube_client_id", context);
//        String CLIENT_SECRET= CommonConfiguration.getProperty("youtube_client_secret", context);;
//        String refreshToken = CommonConfiguration.getProperty("refresh_token", context);
//          
//	  	  HttpTransport HTTP_TRANSPORT = new NetHttpTransport();
//
//	  	  JsonFactory JSON_FACTORY = new JacksonFactory();
//	  	 
//	  	  Credential credential = new GoogleCredential.Builder()
//	  			    .setTransport(HTTP_TRANSPORT)
//	  			    .setJsonFactory(JSON_FACTORY)
//	  			    .setClientSecrets(CLIENT_ID, CLIENT_SECRET)
//	  			    .build();
//	  	  credential.setRefreshToken(refreshToken);
//	  	  
//        return new YouTube.Builder(
//        HTTP_TRANSPORT, JSON_FACTORY, credential)
//            .setApplicationName("wildbook-youtube")
//            .build();
//    }
	
	public static void main(String[] args) throws IOException {
//		YouTube youtube = getYouTubeService();
	    YouTube.init(request);
		
	    try {
	        HashMap<String, String> parameters = new HashMap<>();
	        parameters.put("part", "snippet");
	        CommentThreadListResponse videoCommentsListResponse = youtube.commentThreads()
                    .list("snippet").setVideoId("JhIcP4K-M6c").setTextFormat("plainText").execute();
            List<CommentThread> videoComments = videoCommentsListResponse.getItems();

            CommentThread firstComment = videoComments.get(0);
            // Will use this thread as parent to new reply.
//            String parentId = firstComment.getId();
            
            String parentId="z13lchyolwvgxz5yw04cifdossigzb4wof4";
	        Comment comment = new Comment();
	        CommentSnippet snippet = new CommentSnippet();
	        snippet.set("parentId", parentId);
	        snippet.set("textOriginal", "Wow.Thanks");
	        snippet.set("viewerRating", "like");

	        comment.setSnippet(snippet);

	        YouTube.Comments.Insert commentsInsertRequest = youtube.comments().insert(parameters.get("part").toString(), comment);

	        Comment response = commentsInsertRequest.execute();
	        System.out.println(response);
	      }catch (GoogleJsonResponseException e) {
            e.printStackTrace();
            System.err.println("There was a service error: " + e.getDetails().getCode() + " : " + e.getDetails().getMessage());
        } catch (Throwable t) {
            t.printStackTrace();
        }
	}

}
