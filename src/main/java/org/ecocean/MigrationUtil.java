package org.ecocean;
import java.util.HashMap;
import java.io.File;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import org.json.JSONObject;
import org.json.JSONArray;
import java.util.UUID;


public class MigrationUtil {
    private static File migDir = new File("/tmp/migration");

    public static File setDir(String dir) {
        if (dir == null) return null;
        migDir = new File(dir);
        return migDir;
    }
    public static File getDir() {
        return migDir;
    }
    public static String checkDir() {
        if (!migDir.exists()) return migDir.toString() + " (does not exist)";
        return migDir.toString();
    }

    public static File writeFile(String fname, String contents) throws java.io.IOException {
        File file = new File(getDir(), fname);
        Util.writeToFile(contents, file.getAbsolutePath());
        return file;
    }
    public static File appendFile(String fname, String contents) throws java.io.IOException {
        File file = new File(getDir(), fname);
        Util.appendToFile(contents, file.getAbsolutePath());
        return file;
    }

    public static String sqlSub(String inSql, String rep) {
        if (rep == null) return inSql.replaceFirst("\\?", "NULL");
        rep = rep.replaceAll("'", "''");  //FIXME this needs to be better string-prepping
        return inSql.replaceFirst("\\?", "'" + rep + "'");
    }
    public static String sqlSub(String inSql, Integer rep) {
        String rs = "NULL";
        if (rep != null) rs = rep.toString();
        return inSql.replaceFirst("\\?", rs);
    }
    public static String sqlSub(String inSql, Long rep) {
        String rs = "NULL";
        if (rep != null) rs = rep.toString();
        return inSql.replaceFirst("\\?", rs);
    }
    public static String sqlSub(String inSql, Boolean rep) {
        String rs = "NULL";
        if (rep != null) rs = rep.toString();
        return inSql.replaceFirst("\\?", rs);
    }


    public static String toUUID(String s) {  // h/t https://stackoverflow.com/a/19399768
        return UUID.fromString(
            s.replaceFirst( 
                "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)", "$1-$2-$3-$4-$5" 
            )
        ).toString();
    }

    public static String jsonQuote(JSONObject j) {
        if (j == null) return "\"{}\"";
        String s = j.toString();
        return "\"" + s.replaceAll("\"", "\\\\\\\\\"") + "\"";  // gimme a effin break java
    }

    public static String cleanup(String in) {
        if (in == null) return null;
        in = in.replaceAll("\\s+", " ").trim();
        if (in.equals("")) return null;
        return in;
    }

    public static User getPublicUser(Shepherd myShepherd) {
        String pubAddr = "public@wildme.org";  // this is the only one i know of for now, but can expand to try others if needed
        return myShepherd.getUserByEmailAddress(pubAddr);
    }
    public static String getPublicUserId(Shepherd myShepherd) {
        User pub = getPublicUser(myShepherd);
        if (pub != null) return pub.getUUID();
        System.out.println("WARNING: MigrationUtil.getPublicUserId() could NOT find public User, using zero-guid!");
        return "00000000-0000-0000-0000-000000000000";  // we need something.  :(
    }

    public static List<String> setSort(Set<String> in) {
        List<String> sort = new ArrayList<String>(in);
        Collections.sort(sort, String.CASE_INSENSITIVE_ORDER);
        return sort;
    }

}
