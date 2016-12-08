package org.ecocean;

import javax.servlet.http.HttpServletRequest;
import java.util.Properties;
import org.ecocean.servlet.ServletUtilities;
/*
import java.net.URL;
import org.json.JSONObject;
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

    public static void init(HttpServletRequest request) {
        String context = ServletUtilities.getContext(request);
        tfactory = getTwitterFactory(context);
    }

    public static boolean isActive() {
        return (tfactory != null);
    }


    //https://dev.twitter.com/rest/public/search   e.g. "whaleshark filter:media"
    public static QueryResult findTweets(String search) throws TwitterException {
        Twitter tw = tfactory.getInstance();
        Query query = new Query(search);
        return tw.search(query);
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
            .setOAuthAccessTokenSecret(accessTokenSecret);
        return new TwitterFactory(cb.build());
    }

}
