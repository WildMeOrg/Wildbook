package org.ecocean;

import javax.servlet.http.HttpServletRequest;
import java.util.Properties;
import org.ecocean.servlet.ServletUtilities;
import org.json.JSONObject;
import org.json.JSONException;
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
import twitter4j.json.DataObjectFactory;

public class TwitterUtil {
    private static TwitterFactory tfactory = null;

    public static void init(HttpServletRequest request) {
        String context = ServletUtilities.getContext(request);
        tfactory = getTwitterFactory(context);
System.out.println("INFO: initialized TwitterUtil.tfactory");
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

    public static String toJSONString(Object obj) {
        if (obj == null) return null;
        //TODO catch exceptions etc and return null
        return DataObjectFactory.getRawJSON(obj);
    }
    public static JSONObject toJSONObject(Object obj) {
        String s = toJSONString(obj);
        if (s == null) return null;
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

}
