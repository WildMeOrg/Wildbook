/*
    aka "Tweet-a-Whale" .. implements listening for and processing tweets
*/
package org.ecocean;

import javax.servlet.http.HttpServletRequest;

import java.util.Iterator;
import java.util.Properties;
import java.util.Map;
import java.util.HashMap;

import org.ecocean.servlet.ServletUtilities;
import org.joda.time.DateTime;
import org.joda.time.LocalDateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

import java.io.IOException;

import org.ecocean.TwitterUtil;
import org.ecocean.media.MediaAsset;
import org.ecocean.media.TwitterAssetStore;
import org.ecocean.media.MediaAsset;
import org.ecocean.media.MediaAssetMetadata;
import org.ecocean.media.MediaAssetFactory;
import org.ecocean.ai.nlp.SUTime;
import org.ecocean.ai.nmt.azure.DetectTranslate;
import org.ecocean.ai.utilities.ParseDateLocation;
import org.ecocean.identity.IBEISIA;
import org.ecocean.queue.*;
import org.ecocean.RateLimitation;
import org.ecocean.ia.Task;

import com.google.gson.Gson;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

import twitter4j.*;
import twitter4j.conf.ConfigurationBuilder;
import twitter4j.Status;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.lang.Runnable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledFuture;

public class TwitterBot {
    private static String SYSTEMVALUE_KEY_COLLECT_SINCEID = "TwitterBotCollectSinceId";
    private static String SYSTEMVALUE_KEY_COLLECT_PROCESSED_ID = "TwitterBotCollectProcessedId";
    private static Queue queueIn = null;
    private static Queue queueOut = null;
    private static long collectorStartTime = 0l;
    private static List<String> taskTweeted = new ArrayList<String>();  //so we dont tweet about the same task more than once

    //this probably *should* be "universal" for twitter!  (on TwitterUtil?) or at least per-account
    private static RateLimitation outgoingRL = new RateLimitation(48 * 60 * 60 * 1000);  //only care about last 48 hrs

    //kind of convenience method (to TwitterUtil) but also swallows exception
    //   TODO yeah this really should be based off context.  :(
    public static twitter4j.User myUser() {
        try {
            return TwitterUtil.myUser();
        } catch (TwitterException ex) {
            System.out.println("WARNING: TwitterBot.myUser() threw " + ex.toString());
        }
        return null;
    }

    public static void processIncomingTweet(Status tweet, String context) {
        if (tweet == null) return;
        if ((tweet.getUser() != null) && tweet.getUser().equals(myUser())) {
            System.out.println("WARNING: TwitterBot.processIncomingTweet() -- tweet " + tweet.getId() + " ignored as it was from TwitterBot account itself!");
            return;
        }
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("TwitterBot.processIncomingTweet");
        myShepherd.beginDBTransaction();
        TwitterAssetStore tas = TwitterAssetStore.find(myShepherd);
        if (tas == null) {
            System.out.println("WARNING: TwitterBot.processIncomingTweet() -- no TwitterAssetStore found! Probably should fix this if you are using Twitter. :)");
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
            return;
        }
        Task task = new Task();
        myShepherd.getPM().makePersistent(task);
        JSONObject p = new JSONObject();
        p.put("id", tweet.getId());
        MediaAsset tweetMA = tas.find(p, myShepherd);
        List<MediaAsset> entities = null;
        if (tweetMA == null) {
            tweetMA = tas.create(p);
            tweetMA.addLabel("_original");
            try {
                tweetMA.updateMetadata();
            } catch (IOException ex) {
                System.out.println("WARNING: TwitterBot.processIncomingTweet() tweetMA.updateMetadata() threw " + ex.toString());
            }
            MediaAssetFactory.save(tweetMA, myShepherd);
            entities = tas.entitiesAsMediaAssets(tweetMA);
            if ((entities != null) && (entities.size() > 0)) {
                for (MediaAsset ema : entities) {
                    MediaAssetFactory.save(ema, myShepherd);
                }
            }
        } else {
            ///TODO ... do we even *want* to process a tweet that is already stored??????  going to say NO for now!
            System.out.println("WARNING: TwitterBot.processIncomingTweet() -- tweet " + tweet.getId() + " already stored, so skipping");
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
            return;
            //entities = (load the children from retrieved tweetMA)
        }
System.out.println("\n---------\nprocessIncomingTweet:\n" + tweet + "\n" + tweetMA + "\n-------\n");
        sendCourtesyTweet(context, tweet, ((entities == null) || (entities.size() < 1)) ? null : entities.get(0));
        myShepherd.commitDBTransaction();
        myShepherd.closeDBTransaction();
        if ((entities == null) || (entities.size() < 1)) return;  //no IA for you!

        String baseUrl = CommonConfiguration.getServerURL(context);
        if (baseUrl == null) {
            System.out.println("DANGER! could not obtain baseUrl in TwitterBot.processIncomingTweet() for tweet " + tweet.getId() + "; failing miserably!");
            return;
        }

        //need to add to queue *after* commit above, so that queue can get it from the db immediately (if applicable)
        JSONObject qj = detectionQueueJob(entities, context, baseUrl, task.getId());
        qj.put("tweetAssetId", tweetMA.getId());
        try {
            org.ecocean.servlet.IAGateway.addToQueue(context, qj.toString());
            System.out.println("INFO: TwitterBot.processIncomingTweet() added detection taskId=" + qj.optString("taskId") + " to IAQueue");
        } catch (IOException ioe) {
            System.out.println("ERROR: TwitterBot.processIncomingTweet() during addToQueue threw " + ioe.toString());
        }
    }

    //TODO this should probably live somewhere more useful.  and be resolved to be less confusing re: IAIntake?
    private static JSONObject detectionQueueJob(List<MediaAsset> mas, String context, String baseUrl, String taskId) {
        JSONObject qj = new JSONObject();
        qj.put("taskId", taskId);
        qj.put("__context", context);
        qj.put("__baseUrl", baseUrl);
        JSONArray idArr = new JSONArray();
        JSONObject maj = new JSONObject();
        for (MediaAsset ma : mas) {
            idArr.put(ma.getId());
        }
        maj.put("mediaAssetIds", idArr);
        qj.put("detect", maj);
        return qj;
    }

    public static void sendCourtesyTweet(String context, Status originTweet, MediaAsset ma) {
        Map<String,String> vars = new HashMap<String,String>();  //%SOURCE_TWEET_ID, %SOURCE_IMAGE_ID, %SOURCE_SCREENNAME, %INDIV_ID, %URL_INDIV, %URL_SUBMIT
        vars.put("SOURCE_SCREENNAME", originTweet.getUser().getScreenName());
        vars.put("SOURCE_TWEET_ID", Long.toString(originTweet.getId()));
        if ((ma == null) || !ma.isMimeTypeMajor("image")) {
            sendTweet(tweetText(context, "tweetTextCourtesy", vars), originTweet.getId());
        } else {
            sendTweet(tweetText(context, "tweetTextCourtesyPhoto", vars), originTweet.getId());
        }
  }


    public static String rateLimitationInfo() {
        return outgoingRL.toString();
    }

    //this is *queued* sending, which is what we want (usually!) so that rate limits etc can be taken care of
    public static void sendTweet(String tweetText, Long replyToId) {
        JSONObject jout = new JSONObject();
        jout.put("tweetText", tweetText);
        jout.put("replyToId", replyToId);
        queuePush(queueOut, jout.toString());
System.out.println("SEND-TWEET:\n[" + jout.toString() + "]");
    }

    private static void messageInHandler(String msg) {
System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>\nmessageInHandler msg=" + msg);
        JSONObject qjob = Util.stringToJSONObject(msg);
        if (qjob == null) return;
        String context = qjob.optString("context");
        if (context == null) {
            System.out.println("WARNING: TwitterBot.messageInHandler() got no context in " + qjob);
            return;
        }
        Status tw = TwitterUtil.toStatus(qjob.optJSONObject("tweet"));
        if (tw == null) {
            System.out.println("WARNING: TwitterBot.messageInHandler() failed to construct tweet Status from " + qjob);
            return;
        }
        processIncomingTweet(tw, context);
    }

    private static void messageOutHandler(String msg) {
        int lastHour = outgoingRL.numSinceHoursAgo(1);
        while (lastHour > 10) {   //TODO maybe this is configurable? must adhere to twitter policy etc
            System.out.println("INFO: TwitterBot.messageOutHandler() got message.  Last hour rate = " + lastHour + ", so stalling...");
            try {
                Thread.sleep(5000);
            } catch (java.lang.InterruptedException ex) {}
            lastHour = outgoingRL.numSinceHoursAgo(1);
        }
        try {
            JSONObject jin = Util.stringToJSONObject(msg);
            if (jin == null) throw new TwitterException("non-JSON queue content: " + msg);
            Status tweet = TwitterUtil.sendTweet(jin.optString("tweetText", null), jin.optLong("replyToId", -1L));
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
        } catch (IOException ex) {
            System.out.println("+ ERROR: TwitterIn queue startup exception: " + ex.toString());
        }
        queueOut = null;
        try {
            queueOut = QueueUtil.getBest(context, "TwitterOut");
        } catch (IOException ex) {
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
        } catch (IOException iox) {
            System.out.println("+ TwitterBot.startServices() queueIn.consume() FAILED on " + queueIn.toString() + ": " + iox.toString());
        }
        TwitterOutgoingMessageHandler qh2 = new TwitterOutgoingMessageHandler();
        try {
            queueOut.consume(qh2);
            System.out.println("+ TwitterBot.startServices() queueOut.consume() started on " + queueOut.toString());
        } catch (IOException iox) {
            System.out.println("+ TwitterBot.startServices() queueOut.consume() FAILED on " + queueOut.toString() + ": " + iox.toString());
        }

        startCollector(context);
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
        } catch (IOException iox) {
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
            qjob.put("context", myShepherd.getContext());
            qjob.put("numOffset", offset);
            qjob.put("numTotal", tweets.size());
            qjob.put("timestamp", System.currentTimeMillis());
            qjob.put("source", "TwitterBot.collectTweets()");
            qjob.put("tweetId", tweet.getId());
            qjob.put("tweet", TwitterUtil.toJSONObject(tweet));
            queuePush(queueIn, qjob.toString());
            queued++;
        }

        SystemValue.set(myShepherd, SYSTEMVALUE_KEY_COLLECT_PROCESSED_ID, previousId);
        SystemValue.set(myShepherd, SYSTEMVALUE_KEY_COLLECT_SINCEID, sinceId);
        return queued;
    }



    //gets the template string and substitutes (using 'vars' Map)
    //  substitution keys (so far):  %SOURCE_TWEET_ID, %SOURCE_IMAGE_ID, %SOURCE_SCREENNAME, %INDIV_ID, %URL_INDIV, %URL_SUBMIT
    //TODO might want to live in TwitterUtil
    public static String tweetText(String context, String key, Map<String,String> vars) {
        String text = TwitterUtil.getProperty(context, key);
        if (text == null) return null;
        String ref = Util.generateUUID().substring(0,6);
        System.out.println("tweetText REF=" + ref + " key=" + key);
        vars.put("REF", ref);
        //is there a "standard" java way to do this? sh/could be in Util? (template substitution)  etc.
        for (String k : vars.keySet()) {
            text = text.replaceAll("%" + k, vars.get(k));
        }
        return text;
    }


    //basically our "listener" daemon; but is more pull (poll?) than push so to speak.
    // just checks for tweets at regular intervals
    private static void startCollector(final String context) { //throws IOException {
        collectorStartTime = System.currentTimeMillis();  //TODO should really be keyed off context!
        //note: up to user discretion not to violate twitter rate limits  TODO maybe handle this in code?  (value in seconds)
        long interval = 600;
        String ci = TwitterUtil.getProperty(context, "collectorInterval");
        if (ci != null) {
            try {
                interval = Long.parseLong(ci);
            } catch (java.lang.NumberFormatException ex) {
                System.out.println("WARNING: TwitterBot.startCollector() could not parse collectorInterval; using default -- " + ex.toString());
            }
        }
        System.out.println("+ TwitterBot.startCollector(" + context + ") starting with interval = " + interval + " sec.");
        final ScheduledExecutorService schedExec = Executors.newScheduledThreadPool(5);
        final ScheduledFuture schedFuture = schedExec.scheduleWithFixedDelay(new Runnable() {
            int count = 0;
            public void run() {
                ++count;
                if (new java.io.File("/tmp/WB_TWITTERBOT_SHUTDOWN").exists()) {
                    System.out.println("INFO: TwitterBot.startCollection(" + context + ") shutting down due to file signal");
                    schedExec.shutdown();
                    return;
                }
                Shepherd myShepherd = new Shepherd(context);
                myShepherd.setAction("TwitterBot.startCollection()");
                myShepherd.beginDBTransaction();
                int t = collectTweets(myShepherd);
                myShepherd.commitDBTransaction();
                myShepherd.closeDBTransaction();
                if ((t != 0) || (count % 1 == 0)) System.out.println("INFO: TwitterBot.startCollection(" + context + ") collectTweets() -> " + t + "  [" + new LocalDateTime() + " count=" + count + " uptime=" + ((System.currentTimeMillis() - collectorStartTime) / (60*1000)) + " min]");
            }
        },
        20,  //initial delay
        interval,  //period delay *after* execution finishes
        TimeUnit.SECONDS);  //unit of delays above

        try {
            schedExec.awaitTermination(5000, TimeUnit.MILLISECONDS);
        } catch (java.lang.InterruptedException ex) {
            System.out.println("WARNING: TwitterBot.startCollector(" + context + ") interrupted: " + ex.toString());
        }
        System.out.println("+ TwitterBot.startCollector(" + context + ") backgrounded");
    }

    //the only thing we need to do here is handle the case there were not annotations made
    //  since otherwise those annotations are going on for identification (and we have nothing to send)
    // NOTE: non-twitter results get passed here too!! in that case we should do nothing and return null
    public static String processDetectionResults(Shepherd myShepherd, final ArrayList<MediaAsset> mas, String rootDir) {
System.out.println("processDetectionResults() -> " + mas);
        if ((mas == null) || (mas.size() < 1)) return null;
        int successful = 0;
        MediaAsset tweetMA = null;
        for (MediaAsset ma : mas) {
            if (tweetMA == null) {
                MediaAsset t = TwitterUtil.parentTweet(myShepherd, ma);
                if (t == null) continue;
                tweetMA = t;  //all these "should be" children of the same tweet, so any parentTweet will do
            }
            //aha, we have a tweet-spawned media asset.  but did it pass detection and get annotations?
            ArrayList<Annotation> anns = ma.getAnnotations();
            if ((anns != null) && (anns.size() > 0)) {
                updateEncounter(tweetMA, anns, myShepherd, rootDir);
                successful++;
            }
        }
        if (tweetMA == null) return null;  //no tweet stuff
        if (successful > 0) return successful + " Annotation(s) in process; not detection-fail tweet sent.";

        String context = myShepherd.getContext();
        Map<String,String> vars = new HashMap<String,String>();
        Status originTweet = TwitterUtil.toStatus(tweetMA);
        if (originTweet == null) return "No Annotations found; but also error: unable to generate originTweet for " + tweetMA;

        vars.put("SOURCE_SCREENNAME", originTweet.getUser().getScreenName());
        //vars.put("SOURCE_TWEET_ID", Long.toString(originTweet.getId()));
        sendTweet(tweetText(context, "tweetTextIANone", vars), originTweet.getId());
        return "Failed to find any Annotations; sent tweet";
    }

    //this input format is cuz this is how IBEISIA has this originally (the key in the map is the acmId)
    public static String processIdentificationResults(Shepherd myShepherd, final HashMap<String,Annotation> annMap, final JSONObject annotPairDict, String taskId) {
        if (annMap == null) return null;
        return processIdentificationResults(myShepherd, new ArrayList<Annotation>(annMap.values()), annotPairDict, taskId);
    }
    //annotPairDict is from the IA results.  in the future we could let this be null and if so fetch it
    public static String processIdentificationResults(Shepherd myShepherd, final List<Annotation> anns, final JSONObject annotPairDict, String taskId) {
        if ((anns == null) || (anns.size() < 1)) return null;

        Task task = Task.load(taskId, myShepherd);
        Task rootTask = null;
        if (task != null) rootTask = task.getRootTask();
System.out.println("processIdentificationResults() [taskId=" + taskId + " > rootTaskId=" + ((rootTask == null) ? "(NULL!?!)" : rootTask.getId()) + "] -> " + anns + " ==> " + annotPairDict);
        if (rootTask != null) taskId = rootTask.getId();  //this way we just use taskId below

        if (taskTweeted.contains(taskId)) {
            System.out.println("NOTE: already tweeted about task " + taskId + "; muting this one");
            return "(muted duplicate tweet about taskId=" + taskId + ")";
        }

        //now we have to find the source tweet (which should be the only one even if more than one annot)
        int successful = 0;
        MediaAsset tweetMA = null;
        for (Annotation ann : anns) {
            MediaAsset ma = ann.getMediaAsset();
            tweetMA = TwitterUtil.parentTweet(myShepherd, ma);
            if (tweetMA != null) break;
        }
        if (tweetMA == null) return null;  //no tweet stuff

        Status originTweet = TwitterUtil.toStatus(tweetMA);
        if (originTweet == null) return "Error finding originTweet for " + tweetMA + " (anns=" + anns + ")";

        String context = myShepherd.getContext();
        Map<String,String> vars = new HashMap<String,String>();
        vars.put("SOURCE_SCREENNAME", originTweet.getUser().getScreenName());
        vars.put("MATCH_URL", CommonConfiguration.getServerURL(context) + "/iaResults.jsp?taskId=" + taskId);
        taskTweeted.add(taskId);

        if ((annotPairDict == null) || (annotPairDict.optJSONArray("review_pair_list") == null) ||
            (annotPairDict.getJSONArray("review_pair_list").length() < 1)) {
            sendTweet(tweetText(context, "tweetTextIADetect", vars), originTweet.getId());
            return "Got " + anns.size() + " annots, but empty ident results[" + taskId + "]; tweet sent.";
        }

        sendTweet(tweetText(context, "tweetTextIABoth", vars), originTweet.getId());
        return "Got " + anns.size() + " annots, and some possible matches!! [" + taskId + "] tweet sent.";
    }

    private static void updateEncounter(MediaAsset tweetMA, ArrayList<Annotation> anns, Shepherd myShepherd, String rootDir) {
        Status originTweet = TwitterUtil.toStatus(tweetMA);
        if ((originTweet == null) || (anns == null)) return;

        String tx = taxonomyStringFromTweet(originTweet, myShepherd.getContext());
        
        //use NLP to get Date/Location if available in Tweet
        String newDetectedDate=ParseDateLocation.parseDate(rootDir, myShepherd.getContext(), originTweet);
        
        
        for (Annotation ann : anns) {
          
            Encounter enc = ann.findEncounter(myShepherd);
            if (enc == null) continue;
            System.out.println("INFO: TwitterBot.updateEncounter() using tx=" + tx + " for " + enc);
            enc.setTaxonomyFromString(tx);
            enc.setState("unapproved");        
            
           
            if(newDetectedDate!=null){
              DateTimeFormatter parser3 = ISODateTimeFormat.dateParser();
              DateTime dt=parser3.parseDateTime(newDetectedDate);
              if(newDetectedDate.length()==10){
                enc.setYear(dt.getYear());
                enc.setMonth(dt.getMonthOfYear());
                enc.setDay(dt.getDayOfMonth());
                enc.setHour(-1);
              }
              else if(newDetectedDate.length()==7){
                enc.setYear(dt.getYear());
                enc.setMonth(dt.getMonthOfYear());
                enc.setDay(-1);
                
              }
              else if(newDetectedDate.length()==4){
                enc.setYear(dt.getYear());
                enc.setMonth(-1);
                
              }
            }
            
            //location?
            setLocationIDFromTweet(enc, originTweet, myShepherd.getContext());
            
            //get Tweet comments for faster review on Encounter page
            enc.setOccurrenceRemarks(originTweet.getText());
            
            

        }
        
        

        
        
    }

    // mostly for ContextDestroyed in StartupWildbook..... i think?
    public static void cleanup() {
/*
        for (ScheduledExecutorService ses : runningSES) {
            ses.shutdown();
            try {
                if (ses.awaitTermintation(20, TimeUnit.SECONDS)) {
                    ses.shutdownNow();
                    if (ses.awaitTermintation(20, TimeUnit.SECONDS)) {
                        System.out.println("!!! QueueUtil.cleanup() -- ExecutorService did not terminate");
                    }
                }
            } catch (InterruptedException ie) {
                ses.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        for (ScheduledFuture sf : runningSF) {
            sf.cancel(true);
        }
*/
        System.out.println("================ = = = = = = ===================== TwitterBot.cleanup() finished.");
    }

    public static String taxonomyStringFromTweet(Status tweet, String context) {
        List<String> tags = TwitterUtil.getHashtags(tweet);
        for (String tag : tags) {
            if (tag == null) continue;
            String tx = TwitterUtil.getProperty(context, "taxonomyHash_" + tag.toLowerCase());
            if (tx != null) return tx;
        }
        return TwitterUtil.getProperty(context, "taxonomyDefault");
    }
    
    public static void setLocationIDFromTweet(Encounter enc, Status tweet, String context) {
      
      /*
       * Step 1. Support explicit hashtagging Encounter.locationID
       * 
       */
      String locationID="";
      String location="";
      List<String> tags = TwitterUtil.getHashtags(tweet);
      Shepherd myShepherd=new Shepherd(context);
      myShepherd.setAction("TwitterBot.setLocationIDFromTweet");
      myShepherd.beginDBTransaction();
      List<String> locIDs=myShepherd.getAllLocationIDs();
      myShepherd.closeDBTransaction();
      myShepherd.closeDBTransaction();
      for (String tag : tags) {
        try{
          for(String l : locIDs){
            if(tag.toLowerCase().equals(l.toLowerCase().replaceAll("-", "").replaceAll(" ", ""))){
              enc.setLocationID(l);
              enc.setLocation(l);
              return;
            }
          }

        }
        catch(Exception e){
          e.printStackTrace();
        }
      }
      
      /*
       * Step 2. If not explicitly set from a hashtag, let's try to get Encounter.locationID from the text
       * 
       */
      try {

        LinkedProperties props=(LinkedProperties)ShepherdProperties.getProperties("submitActionClass.properties", "",context);
          String lowercaseRemarks=DetectTranslate.translateIfNotEnglish(tweet.getText()).toLowerCase();
          Iterator m_enum = props.orderedKeys().iterator();
          while (m_enum.hasNext()) {
            String aLocationSnippet = ((String) m_enum.next()).replaceFirst("\\s++$", "");
            //System.out.println("     Looking for: "+aLocationSnippet);
            if (lowercaseRemarks.indexOf(aLocationSnippet) != -1) {
              locationID = props.getProperty(aLocationSnippet);
              location+=" "+ aLocationSnippet;
              //System.out.println(".....Building an idea of location: "+location);
            }
          }
          if(!locationID.equals("")){
            enc.setLocationID(locationID);
            enc.setLocation(location);
          }
        }
        catch(Exception e){
          e.printStackTrace();
        }
    }


}
