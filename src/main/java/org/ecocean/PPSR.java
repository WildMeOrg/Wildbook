package org.ecocean;

import org.ecocean.Shepherd;
import org.ecocean.CommonConfiguration;
import org.ecocean.ContextConfiguration;
import org.ecocean.ShepherdProperties;
import org.json.JSONObject;
import java.util.Properties;
import java.io.IOException;
import java.net.URL;
import java.net.MalformedURLException;

public class PPSR {
    private static final String PROP_FILE = "ppsr.properties";


/*
    making a decision here to set *both* scistarter and ppsr values when possible
    for example, there is "name" (scistarter) and "ProjectName" (ppsr) -- these both will be set
    no matter what.  they will be identical *unless* the property files specifically set them differently!

    note: (intentionally) exculdes api key.... add that after when needed
*/
    public static JSONObject generateJSONObject(String context) throws IOException {
        Shepherd myShepherd = new Shepherd(context);
        JSONObject jobj = new JSONObject();

        //first we make sure we have the required fields
        myShepherd.beginDBTransaction();
        String guid = CommonConfiguration.getGUID(myShepherd);
        String sysUrl = CommonConfiguration.getServerURL(myShepherd);
        myShepherd.commitDBTransaction();
        if (guid == null) throw new IOException("required GUID not found");
        jobj.put("ProjectGUID", "guid.wildbook.org/" + guid);
        String wbname = ContextConfiguration.getNameForContext(context);
        boolean req = _setJSON(context, jobj, "name", "ProjectName", wbname);
        if (!req) throw new IOException("one of properties name or ProjectName is required");
        req = _setJSON(context, jobj, "url", "ProjectUrl", sysUrl);
        if (!req) throw new IOException("one of properties url or ProjectURL is required");
        req = _setJSON(context, jobj, "description", "ProjectDescription", CommonConfiguration.getHTMLDescription(context));
        if (!req) throw new IOException("one of properties description or ProjectDescription is required");
        req = _setJSON(context, jobj, "origin", "ProjectDataProvider", "PPSR:wildbook.org:" + guid);
        if (!req) throw new IOException("one of properties origin or ProjectDataProvider is required");
        req = _setJSON(context, jobj, "contact_name", "ProjectContactName", wbname + " Admin");
        if (!req) throw new IOException("one of properties contact_name or ProjectContactName is required");
        req = _setJSON(context, jobj, "contact_email", "ProjectContactEmail", CommonConfiguration.getAutoEmailAddress(context));
        if (!req) throw new IOException("one of properties contact_email or ProjectContactEmail is required");
        req = _setJSON(context, jobj, "status", "ProjectStatus", "active");
        if (!req) throw new IOException("one of properties status or ProjectStatus is required");

        //the rest of these are optional
        _setJSON(context, jobj, "twitter_name", "ProjectTwitter", null);
        _setJSON(context, jobj, "facebook_page", "ProjectFacebook", null);
        ///////  etc!   add these later.... TODO
        /// note: "image" 

        //self-promotion a little bit
        String keywords = getProperty(context, "ppsr.ProjectKeywords", CommonConfiguration.getHTMLKeywords(context));
        if (keywords == null) keywords = "";
        if (!keywords.toLowerCase().matches("\\bwildbook\\b")) keywords += ", Wildbook";
        if (!keywords.toLowerCase().matches("\\bwild me\\b")) keywords += ", Wild Me";
        if (keywords.startsWith(", ")) keywords = keywords.substring(2);
        jobj.put("ProjectKeywords", keywords);

        return jobj;
    }

    //internal utility to set based on scistarter/ppsr property mix; returns true if there was a value set, false if not
    //  for convenience, we drop the "sciStarter." and "ppsr." prefixes on the properties passed in!!!
    //  note: if only one value was set, it will set both to this one (rather than default!)
    static boolean _setJSON(String context, JSONObject jobj, String ssProp, String ppsrProp, String def) {
        if (jobj == null) return false;
        String ssVal = getProperty(context, "sciStarter." + ssProp, def);
        String ppsrVal = getProperty(context, "ppsr." + ppsrProp, def);
        if ((ssVal == null) && (ppsrVal == null)) return false;

        //this sets up so that if one of these got set, but the other is default, other gets the value
        if (def != null) {
            if (!def.equals(ssVal) && def.equals(ppsrVal)) ppsrVal = ssVal;
            if (!def.equals(ppsrVal) && def.equals(ssVal)) ssVal = ppsrVal;
        }

        if (ssVal != null) jobj.put(ssProp, ssVal);
        if (ppsrVal != null) jobj.put(ppsrProp, ppsrVal);
        //the cases where *one* was null (so gets the other value)
        if (ssVal == null) jobj.put(ssProp, ppsrVal);
        if (ppsrVal == null) jobj.put(ppsrProp, ssVal);
        return true;
    }

    //this does the magic or registering
    public static JSONObject register(String context) throws IOException {
        if (!enabled(context)) throw new IOException("PPSR not enabled in ppsr.properties");
        URL apiUrl = null;
        try {
            apiUrl = new URL(apiUrl(context));
        } catch (MalformedURLException ex) {}
        if (apiUrl == null) throw new IOException("unable to obtain sciStarterApiEndpoint URL");
        String apiKey = getProperty(context, "sciStarterApiKey", null);
        if (apiKey == null) throw new IOException("you must set sciStarterApiKey in ppsr.properties");
        JSONObject jobj = generateJSONObject(context);
        jobj.put("key", apiKey);
        JSONObject rtn = RestClient.postJSON(apiUrl, jobj, null);
        return rtn;
    }

    public static String apiUrl(String context) {
        return getProperty(context, "sciStarterApiEndpoint", "https://scistarter.com/api/project/add/");
    }

    public static boolean enabled(String context) {
        String val = getProperty(context, "enabled", "false");
        if (val == null) return false;
        return val.toLowerCase().equals("true");
    }

    public static String getProperty(String context, String label, String def) {
        Properties p = getProperties(context);
        if (p == null) {
            System.out.println("PPSR.getProperty(" + label + ") has no properties; ppsr.properties unavailable?");
            return null;
        }
        return p.getProperty(label, def);
    }
    private static Properties getProperties(String context) {
        try {
            return ShepherdProperties.getProperties(PROP_FILE, "", context);
        } catch (Exception ex) {
            return null;
        }
    }


}

/*

*exhaustive* list of properties scistarter accepts.
https://scistarter.com/api#add for details

it appears that scistarter *also* supports all of these (untested)
https://www.citsci.org/CWIS438/Websites/CitSci/PPSR_CORE_Documentation.php

in properties file, scistarter ones are prepended with "sciStarter." and PPSR with "ppsr."

name
scistarter_id
description
url
origin
contact_name
contact_affiliation
contact_email
contact_phone
contact_address
presenting_org
rsvp
address
city
state
zip
country
video_url
blog_url
twitter_name
facebook_page
status
preregistration
goal
task
image
image_credit
how_to_join
special_skills
gear
outdoors
indoors
time_commitment
project_type
audience
regions
region_label
UN_regions

*/

