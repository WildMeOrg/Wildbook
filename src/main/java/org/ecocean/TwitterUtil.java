/*
    this class is for **generic** (usable across many applications) Twitter functions.
    for application-specific Twitter functionality, please create own class e.g. TwitterBot.java
*/

package org.ecocean;

import javax.servlet.http.HttpServletRequest;

import java.util.Properties;

import org.ecocean.servlet.ServletUtilities;
import org.joda.time.LocalDateTime;
import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

import com.google.gson.Gson;

import org.ecocean.media.MediaAsset;
import org.ecocean.media.TwitterAssetStore;

/*
import org.ecocean.media.MediaAssetMetadata;
import org.ecocean.media.MediaAssetFactory;
import org.ecocean.identity.IBEISIA;

import java.util.ArrayList;
import java.util.Arrays;
*/


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
    private static TwitterFactory tfactory = null;  //note should be based on context like below no????  TODO
    private static HashMap<String,Properties> Props = new HashMap<String,Properties>();  //keyed on context

    public static Twitter init(HttpServletRequest request) {
        return init(ServletUtilities.getContext(request));
    }

    public static Twitter init(String context) {
        twitter4j.User me = null;
        try {
            tfactory = getTwitterFactory(context);
            me = myUser();
        } catch (Exception ex) {
            System.out.println("INFO: TwitterUtil.init(" + context + ") failed [usually no twitter.properties] -- " + ex.toString());
            tfactory = null;
            return null;
        }
        System.out.println("INFO: initialized TwitterUtil.tfactory on " + context + " for account " + me.getScreenName() + " [" + me.getId() + "]");
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
    if (sinceId >= 0l) query.setSinceId(sinceId);
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

    // see:  http://twitter4j.org/javadoc/twitter4j/User.html
    //   e.g.  .getName(), .getId(), .getScreenName() etc
    public static twitter4j.User myUser() throws TwitterException {
        Twitter tw = tfactory.getInstance();
        return tw.verifyCredentials();
    }


    public static String toJSONString(Object obj) {
        if (obj == null) return null;
        return TwitterObjectFactory.getRawJSON(obj);
    }
    public static JSONObject toJSONObject(Object obj) {
        String s = toJSONString(obj);
        if (s == null) return null;
        return new JSONObject(s);
    }
    public static Status toStatus(JSONObject jobj) {
        if (jobj == null) return null;
        try {
            return TwitterObjectFactory.createStatus(jobj.toString());
        } catch (Exception ex) {
            System.out.println("ERROR: TwitterUtil.toStatus() failed with " + ex.toString());
        }
        return null;
    }
    //twitter-MediaAsset to propert twitter4j.Status
    public static Status toStatus(MediaAsset ma) {
        if ((ma == null) ||
            !(ma.getStore() instanceof TwitterAssetStore) ||
            !ma.hasLabel("_original") ||
            (ma.getMetadata() == null) ||
            (ma.getMetadata().getData().optJSONObject("twitterRawJson") == null)) {
            return null;
        }
        return toStatus(ma.getMetadata().getData().getJSONObject("twitterRawJson"));
    }

    public static List<String> getHashtags(Status tweet) {
        List<String> rtn = new ArrayList<String>();
        if (tweet == null) return rtn;
        HashtagEntity[] ht = tweet.getHashtagEntities();
        if ((ht == null) || (ht.length < 1)) return rtn;
        for (int i = 0 ; i < ht.length ; i++) {
            rtn.add(ht[i].getText());
        }
        return rtn;
    }

    public static String getProperty(String context, String key) {
        if (Props.get(context) == null) Props.put(context, ShepherdProperties.getProperties("twitter.properties", "", context));
        if (Props.get(context) == null) throw new RuntimeException("could not load twitter.properties for " + context);  //ouch
        return Props.get(context).getProperty(key);
    }

  //http://twitter4j.org/en/configuration.html
  public static TwitterFactory getTwitterFactory(String context) {
/*
    Properties props = ShepherdProperties.getProperties("twitter.properties", "", context);
    if (props == null) throw new RuntimeException("no twitter.properties");
    String debug = props.getProperty("debug");
    String consumerKey = props.getProperty("consumerKey");
*/
    String debug = getProperty(context, "debug");
    String consumerKey = getProperty(context, "consumerKey");
    if ((consumerKey == null) || consumerKey.equals("")) throw new RuntimeException("twitter.properties missing consumerKey");  //hopefully enough of a hint
    //String consumerSecret = props.getProperty("consumerSecret");
    //String accessToken = props.getProperty("accessToken");
    //String accessTokenSecret = props.getProperty("accessTokenSecret");
    String consumerSecret = getProperty(context, "consumerSecret");
    String accessToken = getProperty(context, "accessToken");
    String accessTokenSecret = getProperty(context, "accessTokenSecret");
    ConfigurationBuilder cb = new ConfigurationBuilder();
    cb.setDebugEnabled((debug != null) && debug.toLowerCase().equals("true"))
    .setOAuthRequestTokenURL("https://api.twitter.com/oauth2/request_token")
    .setOAuthAuthorizationURL("https://api.twitter.com/oauth2/authorize")
    .setOAuthAccessTokenURL("https://api.twitter.com/oauth2/access_token")
    .setOAuthConsumerKey(consumerKey)
    .setOAuthConsumerSecret(consumerSecret)
    .setOAuthAccessToken(accessToken)
    .setJSONStoreEnabled(true)
    .setOAuthAccessTokenSecret(accessTokenSecret)
    .setTweetModeExtended(true);
    return new TwitterFactory(cb.build());
  }


    //note: this directly sends tweet.  check out TwitterBot.sendTweet() for info on the prefered queued version.
    //  queueing will take into account rates etc.
    public static Status sendTweet(String tweetText) throws TwitterException {
        if (tfactory == null) throw new TwitterException("TwitterUtil has not been initialized");
        if (tweetText == null) throw new TwitterException("TwitterUtil.sendTweet() got null tweetText");
        return tfactory.getInstance().updateStatus(tweetText);
    }
    //send a tweet *in reply to* another  (if replyToId is null (or negative or zero!), it basically reverts to non-reply)
    public static Status sendTweet(String tweetText, Long replyToId) throws TwitterException {
        if (tfactory == null) throw new TwitterException("TwitterUtil has not been initialized");
        if (tweetText == null) throw new TwitterException("TwitterUtil.sendTweet() got null tweetText");
        StatusUpdate stat = new StatusUpdate(tweetText);
        if ((replyToId != null) && (replyToId > 0)) stat.setInReplyToStatusId(replyToId);
        return tfactory.getInstance().updateStatus(stat);
    }

    //given an "entity" (child) MediaAsset of a tweet, will return the parent tweet MediaAsset
    public static MediaAsset parentTweet(Shepherd myShepherd, MediaAsset ma) {
        if ((ma == null) || !ma.hasLabel("_entity")) return null;
        MediaAsset parentMA = ma.getParent(myShepherd);
        if (parentMA == null) return null;
        if (parentMA.getStore() instanceof TwitterAssetStore) return parentMA;
        return null;
    }
    
    public static String getText(Status tweet) {
      if (tweet == null) return null;
      String text = tweet.getText();
      return text;
  }
   
    public static Date getPostingDate(Status tweet) {
      if (tweet == null) return null;
      java.util.Date date = tweet.getCreatedAt();
      return date;
  }


}
