package org.ecocean;
import java.util.HashMap;
import java.io.File;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import org.json.JSONObject;
import org.json.JSONArray;
import java.util.UUID;


public class MigrationUtil {
    private static File migDir = new File("/tmp/migration");
    private static User PUBLIC_USER = null;
    private static String PUBLIC_USER_ID = null;

    public static File setDir(String dir) {
        if (dir == null) return null;
        migDir = new File(dir);
        return migDir;
    }
    public static File getDir() {
        return migDir;
    }
    public static String checkDir() {
        if (!migDir.exists()) throw new RuntimeException("YOU MUST CREATE " + migDir + " AS A WRITEABLE DIRECTORY FIRST");
        try {
            writeFile(".test", "test");
        } catch (java.io.IOException ex) {
            throw new RuntimeException("YOU MUST CREATE " + migDir + " AS A WRITEABLE DIRECTORY FIRST; could not create test file: " + ex.toString());
        }
        File test = new File(getDir(), ".test");
        if (!test.exists()) throw new RuntimeException("YOU MUST CREATE " + migDir + " AS A WRITEABLE DIRECTORY FIRST; could not create test file");
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

/*
    public static String sqlSub(String inSql, String rep) {
        if (rep == null) return inSql.replaceFirst("\\?", "NULL");
        rep = rep.replaceAll("'", "''");  //FIXME this needs to be better string-prepping
        return inSql.replaceFirst("\\?", "'" + java.util.regex.Matcher.quoteReplacement(rep) + "'");
    }
*/
    public static String sqlSub(String inSql, String rep) {
        if (rep == null) return sqlSub(inSql, "NULL", false);
        return sqlSub(inSql, rep, true);  //default behavior is quote (assumed string)
    }
    public static String sqlSub(String inSql, String rep, boolean quoteIt) {
        Pattern p = Pattern.compile("^(.*?[ \\(])\\?([,\\)].*)$", Pattern.DOTALL);  //need to match multiline
        Matcher m = p.matcher(inSql);
        if (!m.matches()) {
            System.out.println("WARNING sqlSub() could not find pattern in: " + inSql);
            return inSql;
        }
        rep = rep.replaceAll("'", "''");  //FIXME this needs to be better string-prepping
        if (quoteIt) rep = "'" + rep + "'";
        return m.group(1) + rep + m.group(2);
    }
    public static String sqlSub(String inSql, Integer rep) {
        String rs = "NULL";
        if (rep != null) rs = rep.toString();
        return sqlSub(inSql, rs, false);
    }
    public static String sqlSub(String inSql, Long rep) {
        String rs = "NULL";
        if (rep != null) rs = rep.toString();
        return sqlSub(inSql, rs, false);
    }
    public static String sqlSub(String inSql, Boolean rep) {
        String rs = "NULL";
        if (rep != null) rs = rep.toString();
        return sqlSub(inSql, rs, false);
    }

    // gives a matchable guid but different!
    public static String partnerGuid(String guid) {
        if (guid == null) return null;
        char[] c = guid.toCharArray();
        char tmp = c[35];
        c[35] = c[34];
        c[34] = tmp;
        return new String(c);
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
        return "\"" + s.replaceAll("\"", "\\\\\"") + "\"";  // gimme a effin break java
    }

    public static String cleanup(String in) {
        if (in == null) return null;
        in = in.replaceAll("\\s+", " ").trim();
        if (in.equals("")) return null;
        return in;
    }

    public static User getPublicUser(Shepherd myShepherd) {
        if (PUBLIC_USER != null) return PUBLIC_USER;
        String pubAddr = "public@wildme.org";  // this is the only one i know of for now, but can expand to try others if needed
        PUBLIC_USER = myShepherd.getUserByEmailAddress(pubAddr);
        return PUBLIC_USER;
    }
    public static String getPublicUserId(Shepherd myShepherd) {
        if (PUBLIC_USER_ID != null) return PUBLIC_USER_ID;
        User pub = getPublicUser(myShepherd);
        if (pub != null) {
            PUBLIC_USER_ID = pub.getUUID();
        } else {
            System.out.println("WARNING: MigrationUtil.getPublicUserId() could NOT find public User, using zero-guid!");
            PUBLIC_USER_ID = "00000000-0000-0000-0000-000000000123";  // we need something.  :(
        }
        return PUBLIC_USER_ID;
    }

    public static List<String> setSort(Set<String> in) {
        List<String> sort = new ArrayList<String>(in);
        Collections.sort(sort, String.CASE_INSENSITIVE_ORDER);
        return sort;
    }

}
