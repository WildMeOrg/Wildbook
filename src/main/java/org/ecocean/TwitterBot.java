/*
    aka "Tweet-a-Whale" .. implements listening for and processing tweets
*/
package org.ecocean;

import javax.servlet.http.HttpServletRequest;
import java.util.Properties;
import org.ecocean.servlet.ServletUtilities;
import org.joda.time.LocalDateTime;
import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

import org.ecocean.TwitterUtil;
import org.ecocean.media.MediaAsset;
import org.ecocean.media.TwitterAssetStore;
import org.ecocean.media.MediaAsset;
import org.ecocean.media.MediaAssetMetadata;
import org.ecocean.media.MediaAssetFactory;
import org.ecocean.identity.IBEISIA;
import org.ecocean.queue.*;
import org.ecocean.RateLimitation;

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

public class TwitterBot {
    private static String SYSTEMVALUE_KEY_COLLECT_SINCEID = "TwitterBotCollectSinceId";
    private static String SYSTEMVALUE_KEY_COLLECT_PROCESSED_ID = "TwitterBotCollectProcessedId";
    private static Queue queueIn = null;
    private static Queue queueOut = null;

    //this probably *should* be "universal" for twitter!  (on TwitterUtil?) or at least per-account
    private static RateLimitation outgoingRL = new RateLimitation(48 * 60 * 60 * 1000);  //only care about last 48 hrs


  public static void sendCourtesyTweet(String screenName, String mediaType, Long twitterId) {
    String reply = null;
    if(mediaType.equals("photo")) {
      reply = "Thank you for the photo(s), including id " + Long.toString(twitterId) + ", @" + screenName + "! Result pending!";
    } else {
      reply = "Thanks for tweet " + Long.toString(twitterId) + ", @" + screenName + "! Could you send me a pic in a new tweet?";
    }
    sendTweet(reply);
  }

  public static void sendCourtesyTweet(String screenName, String mediaType, String id) {
    String reply = null;
    if(mediaType.equals("photo")) {
      reply = "Thank you for the photo(s), including id " + id + ", @" + screenName + "! Result pending!";
    } else {
      reply = "Thanks for tweet " + id + ", @" + screenName + "! Could you send me a pic in a new tweet?";
    }
    sendTweet(reply);
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
            sendCourtesyTweet(tweeterScreenName, mediaType, mediaEntityId);
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


  public static void sendDetectionAndIdentificationTweet(String screenName, String imageId, String whaleId, boolean detected, boolean identified, String info){
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
    sendTweet(tweet);
    sendTweet(tweet2);
  }

  public static void sendTimeoutTweet(String screenName, String id) {
    String reply = "Hello @" + screenName + "The image you sent for tweet " + id + " was unable to be processed";
    String reply2 = "@" + screenName + ", if you'd like to make a manual submission, please go to http://www.flukebook.org/submit.jsp";
    sendTweet(reply);
    sendTweet(reply2);
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


    public static String rateLimitationInfo() {
        return outgoingRL.toString();
    }

    //this is *queued* sending, which is what we want (usually!) so that rate limits etc can be taken care of
    public static void sendTweet(String tweetText) {
        //for now, outgoing queue just takes tweet text!  maybe this will change?  see: messageOutHandler()
        queuePush(queueOut, tweetText);
    }


    private static void messageInHandler(String msg) {
System.out.println("<<< hey i should be doing something with: " + msg);
    }

    private static void messageOutHandler(String msg) {
        int lastHour = outgoingRL.numSinceHoursAgo(1);
        while (lastHour > 10) {
            System.out.println("INFO: TwitterBot.messageOutHandler() got message.  Last hour rate = " + lastHour + ", so stalling...");
            try {
                Thread.sleep(5000);
            } catch (java.lang.InterruptedException ex) {}
            lastHour = outgoingRL.numSinceHoursAgo(1);
        }
        try {
            Status tweet = TwitterUtil.sendTweet(msg);
            System.out.println("INFO: TweetBot.messageOutHandler() sent tweet id=" + tweet.getId());
        } catch (TwitterException tex) {
            System.out.println("WARNING: TweetBot.messageOutHandler(" + msg + ") threw " + tex.toString());
        }
        outgoingRL.addEvent();  //adding event *even if we fail* ... just cuz???
    }


    //background workers related to twitter
    public static boolean startServices(String context) {
        class TwitterIncomingMessageHandler extends QueueMessageHandler {
            public boolean handler(String msg) {
                messageInHandler(msg);
                return true;
            }
        }
        class TwitterOutgoingMessageHandler extends QueueMessageHandler {
            public boolean handler(String msg) {
                messageOutHandler(msg);
                return true;
            }
        }

        TwitterUtil.init(context);
        if (!TwitterUtil.isActive()) {
            System.out.println("+ INFO: Twitter not enabled; services not started");
            return false;
        }

        queueIn = null;
        try {
            queueIn = QueueUtil.getBest(context, "TwitterIn");
        } catch (java.io.IOException ex) {
            System.out.println("+ ERROR: TwitterIn queue startup exception: " + ex.toString());
        }
        queueOut = null;
        try {
            queueOut = QueueUtil.getBest(context, "TwitterOut");
        } catch (java.io.IOException ex) {
            System.out.println("+ ERROR: TwitterOut queue startup exception: " + ex.toString());
        }
        if ((queueIn == null) || (queueOut == null)) {
            System.out.println("+ WARNING: Twitter queue service(s) NOT started");
            return false;
        }

        TwitterIncomingMessageHandler qh = new TwitterIncomingMessageHandler();
        try {
            queueIn.consume(qh);
            System.out.println("+ TwitterBot.startServices() queueIn.consume() started on " + queueIn.toString());
        } catch (java.io.IOException iox) {
            System.out.println("+ TwitterBot.startServices() queueIn.consume() FAILED on " + queueIn.toString() + ": " + iox.toString());
        }
        TwitterOutgoingMessageHandler qh2 = new TwitterOutgoingMessageHandler();
        try {
            queueOut.consume(qh2);
            System.out.println("+ TwitterBot.startServices() queueOut.consume() started on " + queueOut.toString());
        } catch (java.io.IOException iox) {
            System.out.println("+ TwitterBot.startServices() queueOut.consume() FAILED on " + queueOut.toString() + ": " + iox.toString());
        }

        //TODO start "listener"
        return true;
    }

    //TODO could potentially read listenHashtags from twitter.properties, perhaps???
    public static String searchString() {
        String handle = null;
        try {
            handle = TwitterUtil.myUser().getScreenName();
        } catch (TwitterException tex) {
            System.out.println("TwitterBot.searchString(): " + tex.toString());
            return null;
        }
        return "@" + handle;
    }

    public static boolean queuePush(Queue q, String msg) {
        if (q == null) {
            System.out.println("ERROR: TwitterBot.queuePush() has null queue.  msg=" + msg);
            return false;
        }
        try {
            q.publish(msg);
        } catch (java.io.IOException iox) {
            System.out.println("ERROR: TwitterBot.queuePush(" + q.toString() + ", " + msg + ") publish() got exception: " + iox.toString());
            return false;
        }
        return true;
    }

    //finds tweets and dumps into incoming queue... easy-peasy!
    public static int collectTweets(Shepherd myShepherd) {
        String searchString = searchString();
        if (searchString == null) {
            System.out.println("WARNING: TwitterBot.collectTweets() could not establish searchString.  :(");
            return -99;
        }

        Long sinceId = SystemValue.getLong(myShepherd, SYSTEMVALUE_KEY_COLLECT_SINCEID);
        if (sinceId == null) sinceId = 986640529160048640l; //dont go back forever! kinda arbitrary start...
        Long previousId = SystemValue.getLong(myShepherd, SYSTEMVALUE_KEY_COLLECT_PROCESSED_ID);
        if (previousId == null) previousId = 0l;  //we dont process any id < this, since we should only move forward in time!

        QueryResult qr = null;
        try {
            qr = TwitterUtil.findTweets(searchString, sinceId);
        } catch (TwitterException tex) {
            System.out.println("WARNING: TwitterBot.collectTweets() => " + tex.toString());
        }
        if (qr == null) return -1;

        //note, it appears we really should be *paging thru* multiple sets of these... FIXME
        //   see:   http://twitter4j.org/javadoc/twitter4j/QueryResult.html
        List<Status> tweets = qr.getTweets();
        int offset = -1;
        int queued = 0;
        for (Status tweet : tweets) {
            offset++;
            if (tweet.getId() <= previousId) {
                System.out.println("INFO: TwitterBot.collectTweets() skipping " + tweet.getId() + ", less than previousId");
                continue;
            }
            previousId = tweet.getId();
            if (tweet.getId() > sinceId) sinceId = tweet.getId();
            JSONObject qjob = new JSONObject();
            qjob.put("numOffset", offset);
            qjob.put("numTotal", tweets.size());
            qjob.put("timestamp", System.currentTimeMillis());
            qjob.put("source", "TwitterBot.collectTweets()");
            qjob.put("tweetId", tweet.getId());
            qjob.put("tweet", TwitterUtil.toJSONObject(tweet));
//System.out.println("\n\n>>>>>>>>>>>>>>>>>>>>>>>>\n" + tweet);
            queuePush(queueIn, qjob.toString());
            queued++;
        }

        SystemValue.set(myShepherd, SYSTEMVALUE_KEY_COLLECT_PROCESSED_ID, previousId);
        SystemValue.set(myShepherd, SYSTEMVALUE_KEY_COLLECT_SINCEID, sinceId);
        return queued;
    }

}
