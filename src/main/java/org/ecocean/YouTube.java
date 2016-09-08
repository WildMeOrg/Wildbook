package org.ecocean;

import javax.servlet.http.HttpServletRequest;
import java.net.URL;
import org.json.JSONObject;
import org.ecocean.servlet.ServletUtilities;
import java.io.File;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

import java.io.IOException;
import java.net.MalformedURLException;
import java.security.NoSuchAlgorithmException;
import java.security.InvalidKeyException;

public class YouTube {
    private static String apiKey = null;
  //private String storyMediaURL;
  //public void setStoryTellerEmail(String email){this.storyTellerEmail=email;}

    public static void init(HttpServletRequest request) {
        String context = ServletUtilities.getContext(request);
        apiKey = CommonConfiguration.getProperty("youtube_api_key", context);
    }

    public static boolean isActive() {
        return (apiKey != null);
    }

    public static boolean validId(String id) {
        if (id == null) return false;
        return id.matches("^[a-zA-Z0-9_-]{11}$");
    }

    public static JSONObject simpleInfo(String id) throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException, InvalidKeyException {
        if (!validId(id)) return null;
        URL url = new URL("https://www.youtube.com/oembed?url=http://www.youtube.com/watch?v=" + id + "&format=json");
        return RestClient.get(url);
        //TODO handle 404 for bad ids, etc....
    }

    /* /usr/local/bin/youtube_get.sh ID tempdir
will generate:
-rw-rw-r-- 1 jon jon   253156 Sep  8 00:10 V_ozofiGPJo.jpg
-rw------- 1 jon jon    30282 Sep  8 00:10 V_ozofiGPJo.info.json
-rw-rw-r-- 1 jon jon 36398939 Aug 23 08:13 V_ozofiGPJo.mp4
use .json to populate ma.metadata.detailed  -- note: set .detailed = { processing: true } before background grab
*/

    //this fills a directory with grabbed goodies from youtube (synchronously) and returns a list of Files that are its contents
    public static List<File> grab(String id, File targetDir) {
        if ((id == null) || (targetDir == null)) return null;
        if (!targetDir.isDirectory()) throw new RuntimeException(targetDir + " is not a directory");
        String[] cmd = new String[]{"/usr/local/bin/youtube_get.sh", id, targetDir.toString()};
        ProcessBuilder pb = new ProcessBuilder();
        pb.command(cmd);
System.out.println("before!");

        try {
            Process proc = pb.start();
            proc.waitFor();
System.out.println("DONE?????");
        } catch (Exception ex) {
            throw new RuntimeException("YouTube.grab(" + id + ", " + targetDir + ") failed: " + ex.toString());
        }

        File[] flist = targetDir.listFiles();
        if ((flist == null) || (flist.length < 1)) throw new RuntimeException(targetDir + " is empty; grab failed");
        return Arrays.asList(flist);
    }

}
