package org.ecocean;

import javax.servlet.http.HttpServletRequest;
import java.util.Properties;
import org.ecocean.servlet.ServletUtilities;
import org.json.JSONObject;
import org.json.JSONException;

import com.google.gson.Gson;
/*
import java.net.URL;
import java.io.File;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

import java.io.IOException;
import java.net.MalformedURLException;
import java.security.NoSuchAlgorithmException;
import java.security.InvalidKeyException;
*/
import twitter4j.*;
import twitter4j.conf.ConfigurationBuilder;

public class TwitterUtil {
    private static TwitterFactory tfactory = null;

    public static Twitter init(HttpServletRequest request) {
        String context = ServletUtilities.getContext(request);
        tfactory = getTwitterFactory(context);
System.out.println("INFO: initialized TwitterUtil.tfactory");
        return tfactory.getInstance();
    }

    public static boolean isActive() {
        return (tfactory != null);
    }


    //https://dev.twitter.com/rest/public/search   e.g. "whaleshark filter:media"
    public static QueryResult findTweets(String search) throws TwitterException {
        return findTweets(search, -1l);
    }
    public static QueryResult findTweets(String search, long sinceId) throws TwitterException {
        Twitter tw = tfactory.getInstance();
        Query query = new Query(search);
        if (sinceId >= 0l){
          System.out.println("sinceId is " + Long.toString(sinceId) + " and is >= 0l");
          query.setSinceId(sinceId);
        }
        return tw.search(query);
    }

    public static Status getTweet(long tweetId) {
        Twitter tw = tfactory.getInstance();
        try {
            return tw.showStatus(tweetId);
        } catch (TwitterException tex) {
            System.out.println("ERROR: TwitterUtil.getTweet(" + tweetId + ") threw " + tex.toString());
            return null;
        } catch (Exception ex) {
            System.out.println("ERROR: TwitterUtil.getTweet(" + tweetId + ") threw " + ex.toString());
        }
        return null;
    }

    public static String toJSONString(Object obj) throws RuntimeException {
        String returnVal = null;
        Gson gson = new Gson();
        returnVal = gson.toJson(obj);
        if(returnVal == null){
          throw new RuntimeException("JSON string ended up null!");
        }
        System.out.println(returnVal);
        return returnVal;
    }
    public static JSONObject toJSONObject(Object obj) {
        String s = toJSONString(obj);
        if (s == null){
          System.out.println("toJSONString is null");
          return null;
        }
        try {
            JSONObject j = new JSONObject(s);
            return j;
        } catch (JSONException ex) {
            System.out.println("ERROR: TwitterUtil.toJSONObject() could not parse '" + s + "' as JSON: " + ex.toString());
            return null;
        }
    }

    //http://twitter4j.org/en/configuration.html
    public static TwitterFactory getTwitterFactory(String context) {
        Properties props = ShepherdProperties.getProperties("twitter.properties", "", context);
        if (props == null) throw new RuntimeException("no twitter.properties");
        String debug = props.getProperty("debug");
        String consumerKey = props.getProperty("consumerKey");
        if ((consumerKey == null) || consumerKey.equals("")) throw new RuntimeException("twitter.properties missing consumerKey");  //hopefully enough of a hint
        String consumerSecret = props.getProperty("consumerSecret");
        String accessToken = props.getProperty("accessToken");
        String accessTokenSecret = props.getProperty("accessTokenSecret");
        ConfigurationBuilder cb = new ConfigurationBuilder();
        cb.setDebugEnabled((debug != null) && debug.toLowerCase().equals("true"))
            .setOAuthRequestTokenURL("https://api.twitter.com/oauth2/request_token")
            .setOAuthAuthorizationURL("https://api.twitter.com/oauth2/authorize")
            .setOAuthAccessTokenURL("https://api.twitter.com/oauth2/access_token")
            .setOAuthConsumerKey(consumerKey)
            .setOAuthConsumerSecret(consumerSecret)
            .setOAuthAccessToken(accessToken)
            .setJSONStoreEnabled(true)
            .setOAuthAccessTokenSecret(accessTokenSecret);
        return new TwitterFactory(cb.build());
    }

    public static void sendCourtesyTweet(String screenName, String mediaType,  Twitter twitterInst, Long twitterId) {
      String reply = null;
      if(mediaType.equals("photo")) {
        reply = "Thank you for the photo(s), including id " + Long.toString(twitterId) + ", @" + screenName + "! Result pending!";
      } else {
        reply = "Thanks for tweet " + Long.toString(twitterId) + ", @" + screenName + "! Could you send me a pic in a new tweet?";
      }
      try {
        String status = createTweet(reply, twitterInst);
      } catch(TwitterException e) {
        e.printStackTrace();
      }
    }

    public static void sendCourtesyTweet(String screenName, String mediaType,  Twitter twitterInst, String id) {
      String reply = null;
      if(mediaType.equals("photo")) {
        reply = "Thank you for the photo(s), including id " + id + ", @" + screenName + "! Result pending!";
      } else {
        reply = "Thanks for tweet " + id + ", @" + screenName + "! Could you send me a pic in a new tweet?";
      }
      try {
        String status = createTweet(reply, twitterInst);
      } catch(TwitterException e) {
        e.printStackTrace();
      }
    }

    public static void sendDetectionAndIdentificationTweet(String screenName, String imageId, Twitter twitterInst, String whaleId, boolean detected, boolean identified){
      String tweet = null, tweet2 = null;
      if(detected && identified){
        tweet = "Hi, @" + screenName + "! We detected a whale in " + imageId + " and identified it as " + whaleId + "!";
        tweet2 = "Here's some info on " + whaleId + ": ";
      } else if(detected && !identified){
        tweet = "We detected a whale in " + imageId + " but we were not able to identify it.";
        tweet2 = "If you'd like to make a manual submission for " + imageId + ", please go to http://www.flukebook.org/submit.jsp";
      } else {
        tweet = "We were not able to identify a whale in " + imageId + ".";
        tweet2 = "If you'd like to make a manual submission for " + imageId + ", please go to http://www.flukebook.org/submit.jsp";
      }

      try {
        String status1 = createTweet(tweet, twitterInst);
        String status2 = createTweet(tweet2, twitterInst);
      } catch(TwitterException e){
        e.printStackTrace();
      }
    }

    public static String createTweet(String tweet, Twitter twitterInst) throws TwitterException {
      String returnVal = null;
      try {
        Status status = twitterInst.updateStatus(tweet);
        returnVal = status.getText();
      } catch(TwitterException e) {
        e.printStackTrace();
      }
      return returnVal;
    }
}
