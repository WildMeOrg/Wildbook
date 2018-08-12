/* 

https://github.com/pac4j/pac4j/wiki/Authenticate-with-Facebook,-Twitter-or-Google,-with-the-pac4j-library,-in-5-minutes

*/

package org.ecocean.security;

import java.util.Properties;

import org.pac4j.core.client.*;
import org.pac4j.oauth.client.*;
import org.ecocean.ShepherdProperties;

//for flickr
import org.scribe.builder.*;
import org.scribe.builder.api.*;
import org.scribe.model.*;
import org.scribe.oauth.*;

import java.util.*;


public class SocialAuth {

    public static final String FLICKR_URL = "https://api.flickr.com/services/rest/";

    //public SocialAuth() {}

    //TODO cache these for each context
    public static Properties authProps(String context) {
        Properties props = new Properties();
        props = ShepherdProperties.getProperties("socialAuth.properties", "", context);
        return props;
    }

    public static FacebookClient getFacebookClient(String context) throws Exception {
        Properties props = authProps(context);
        if ((props == null) || (props.getProperty("social.facebook.auth.appid") == null) || (props.getProperty("social.facebook.auth.secret") == null)) {
            //throw new Exception("facebookAppId or facebookSecret not set in socialAuth.properties");
            return null;
        }
        return new FacebookClient(props.getProperty("social.facebook.auth.appid"), props.getProperty("social.facebook.auth.secret"));
    }



    public static OAuthService getFlickrOauth(String context, String callbackUrl) throws Exception {
        Properties props = authProps(context);
        if ((props == null) || (props.getProperty("social.flickr.auth.key") == null) || (props.getProperty("social.flickr.auth.secret") == null)) {
            //throw new Exception("facebookAppId or facebookSecret not set in socialAuth.properties");
            return null;
        }
        return new ServiceBuilder().provider(FlickrApi.class).apiKey(props.getProperty("social.flickr.auth.key")).apiSecret(props.getProperty("social.flickr.auth.secret")).callback(callbackUrl).build();
    }


}

