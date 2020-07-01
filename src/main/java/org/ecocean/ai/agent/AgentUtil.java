//right now this is kinda misc.  maybe it can focus or break up later.

package org.ecocean.ai.agent;

import org.ecocean.Util;
import java.util.List;
import java.util.ArrayList;
import java.util.Properties;
import org.ecocean.ShepherdProperties;

/*
import org.ecocean.Shepherd;
import org.ecocean.CommonConfiguration;
import org.ecocean.Annotation;
import org.ecocean.media.MediaAsset;
import org.ecocean.media.MediaAssetFactory;
import org.ecocean.identity.IBEISIA;
import org.ecocean.servlet.ServletUtilities;
import java.util.Arrays;
import org.json.JSONObject;
import org.json.JSONArray;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
*/

public class AgentUtil {
    private static List<String> YT_STOP_WORDS = null;

    //true means it should be filtered (ignored/blocked/skipped)
    public static boolean youtubeFilterOld(String context, String text) {
        if (text == null) return false;
        List<String> stopWords = youtubeFilterStopWords(context);  //these will all be lowercase
        if (Util.collectionIsEmptyOrNull(stopWords)) return false;
        text = text.toLowerCase();
        for (String w : stopWords) {
            //note: original code squished spaces in word we are testing.  is that still desirable?  FIXME
            if (text.indexOf(w) > -1) return true;
        }
        return false;  //passed!
    }

    public static List<String> youtubeFilterStopWords(String context) {
        if (YT_STOP_WORDS != null) return YT_STOP_WORDS;  //cache!
        YT_STOP_WORDS = new ArrayList<String>();  //this (empty but not null) is enough to count as cache next time
        Properties prop = null;
        try {
            prop = ShepherdProperties.getProperties("agent_youtube.properties", "", context);
        } catch (Exception ex) {
            System.out.println("INFO: agent_youtube.properties could not be read; youtubeFilterStopWords() cached as empty now - " + ex.toString());
            return YT_STOP_WORDS;
        }
        if (prop == null) return YT_STOP_WORDS;  //snh
        String allWords = prop.getProperty("stop_words");
        if (allWords == null) return YT_STOP_WORDS;
        String[] sw = allWords.split("\\s*,\\s*");
        for (int i = 0 ; i < sw.length ; i++) {
            if (!Util.stringExists(sw[i])) continue;
            YT_STOP_WORDS.add(sw[i].toLowerCase());
        }
        return YT_STOP_WORDS;
    }

}
