package org.ecocean;

import javax.servlet.http.HttpServletRequest;
import java.util.Properties;
import org.ecocean.servlet.ServletUtilities;
import org.joda.time.LocalDateTime;
import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

import org.ecocean.media.TwitterAssetStore;
import org.ecocean.media.MediaAsset;
import org.ecocean.media.MediaAssetMetadata;
import org.ecocean.media.MediaAssetFactory;
import org.ecocean.identity.IBEISIA;

import com.google.gson.Gson;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

/*
import java.net.URL;
import java.io.File;

import java.io.IOException;
import java.net.MalformedURLException;
import java.security.NoSuchAlgorithmException;
import java.security.InvalidKeyException;
*/
import twitter4j.*;
import twitter4j.conf.ConfigurationBuilder;
import twitter4j.Status;

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

  public static String toJSONString(Object obj) {
    String returnVal = null;
    Gson gson = new Gson();
    returnVal = gson.toJson(obj);
    if(returnVal == null){
      System.out.println("returnVal in toJSONString is null");
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

  public static void sendPhotoSpecificCourtesyTweet(org.json.JSONArray emedia, String tweeterScreenName, Twitter twitterInst){
    int photoCount = 0;
    org.json.JSONObject jent = null;
    String mediaType = null;
    Long mediaEntityId = null;
    for(int j=0; j<emedia.length(); j++){
      try{
        jent = emedia.getJSONObject(j);
        mediaType = jent.getString("type");
        mediaEntityId = Long.parseLong(jent.getString("id"));
      } catch(Exception e){
        System.out.println("Error with JSONObject capture");
        e.printStackTrace();
      }

      try{
        if(mediaType.equals("photo")){
          //For now, just one courtesy tweet per tweet, even if the tweet contains multiple images
          if(photoCount<1){
            TwitterUtil.sendCourtesyTweet(tweeterScreenName, mediaType, twitterInst, mediaEntityId);
          }
          photoCount += 1;
        }
      } catch(Exception e){
        e.printStackTrace();
      }
    }
  }

  public static JSONObject makeParentTweetMediaAssetAndSave(Shepherd myShepherd, TwitterAssetStore tas, Status tweet, JSONObject tj){
    myShepherd.beginDBTransaction();
    try{
      MediaAsset ma = tas.create(Long.toString(tweet.getId()));  //parent (aka tweet)
      ma.addLabel("_original");
      MediaAssetMetadata md = ma.updateMetadata();
      MediaAssetFactory.save(ma, myShepherd);
      // JSONObject test = TwitterUtil.toJSONObject(ma);
      tj.put("maId", ma.getId());
      tj.put("metadata", ma.getMetadata().getData());
      System.out.println(tweet.getId() + ": created tweet asset " + ma);
      myShepherd.commitDBTransaction();
      return tj;
    } catch(Exception e){
      myShepherd.rollbackDBTransaction();
      e.printStackTrace();
      return tj;
    }
  }

  public static JSONObject saveEntitiesAsMediaAssetsToSheperdDatabaseAndSendEachToImageAnalysis(List<MediaAsset> mas, Long tweetID, Shepherd myShepherd, JSONObject tj, HttpServletRequest request){
    if ((mas == null) || (mas.size() < 1)) {
    } else {
      JSONArray jent = new JSONArray();
      for (MediaAsset ent : mas) {
        myShepherd.beginDBTransaction();
        try {
          JSONObject ej = new JSONObject();
          // MediaAssetMetadata entMd = ent.updateMetadata();
          MediaAssetFactory.save(ent, myShepherd);
          System.out.println("Ent's mediaAssetID is " + ent.toString());
          // MediaAssetFactory.save(ent, myShepherd);
          String taskId = IBEISIA.IAIntake(ent, myShepherd, request);
          ej.put("maId", ent.getId());
          ej.put("taskId", taskId);
          ej.put("creationDate", new LocalDateTime());
          String tweeterScreenName = tj.getJSONObject("tweet").getJSONObject("user").getString("screen_name");
          ej.put("tweeterScreenName", tweeterScreenName);
          jent.put(ej);
          // myShepherd.getPM().makePersistent(ej); //maybe?
          myShepherd.commitDBTransaction();
        } catch(Exception e){
          myShepherd.rollbackDBTransaction();
          e.printStackTrace();
        }
      }
      tj.put("entities", jent);
    }
    return tj;
  }


  public static void sendDetectionAndIdentificationTweet(String screenName, String imageId, Twitter twitterInst, String whaleId, boolean detected, boolean identified, String info){
    String tweet = null, tweet2 = null;
    if(detected && identified){
      tweet = "Hi, @" + screenName + "! We detected a whale in " + imageId + " and identified it as " + whaleId + "!";
      tweet2 = "@" + screenName + ", here's some info on " + whaleId + ": " + info; //TODO flesh out either by pulling info from db now that whaleId is available, or by passing some info as an additional argument in this method

    } else if(detected && !identified){
      tweet =  "Hi, @" + screenName + "! We detected a whale in " + imageId + " but we were not able to identify it.";
      tweet2 = "@" + screenName + ", if you'd like to make a manual submission for " + imageId + ", please go to http://www.flukebook.org/submit.jsp";
    } else {
      tweet =  "Hi, @" + screenName + "! We were not able to identify a whale in " + imageId + ".";
      tweet2 = "@" + screenName + ", if you'd like to make a manual submission for " + imageId + ", please go to http://www.flukebook.org/submit.jsp";
    }

    try {
      String status1 = createTweet(tweet, twitterInst);
      String status2 = createTweet(tweet2, twitterInst);
    } catch(TwitterException e){
      e.printStackTrace();
    }
  }

  public static void sendTimeoutTweet(String screenName, Twitter twitterInst, String id) {
    String reply = "Hello @" + screenName + "The image you sent for tweet " + id + " was unable to be processed";
    String reply2 = "@" + screenName + ", if you'd like to make a manual submission, please go to http://www.flukebook.org/submit.jsp";
    try {
      String status = createTweet(reply, twitterInst);
      String status2 = createTweet(reply2, twitterInst);
    } catch(TwitterException e) {
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

  public static JSONArray removePendingEntry(JSONArray pendingResults, int index){
    ArrayList<JSONObject> list = new ArrayList<>();
    for(int i = 0; i < pendingResults.length(); i++){
      if(i == index){
        continue;
      } else {
        list.add(pendingResults.getJSONObject(i));
      }
    }
    return new JSONArray(list);
  }
}
