package org.ecocean;
import java.util.HashMap;
import java.io.File;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import org.ecocean.configuration.*;
import org.json.JSONObject;
import org.json.JSONArray;
import java.util.UUID;
import org.apache.shiro.crypto.hash.Sha256Hash;


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

    // mirrors utils.get_stored_filename() in houston
    public static String getStoredFilename(String inputFilename) {
        if (inputFilename == null) return null;
        return new Sha256Hash(inputFilename).toHex();
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

    public static JSONArray makeChoices(Set<String> set) {
        JSONArray arr = new JSONArray();
        for (String s : setSort(set)) {
            JSONObject c = new JSONObject();
            c.put("label", s);
            c.put("value", s);
            arr.put(c);
        }
        return arr;
    }


    public static String getOrMakeCustomFieldCategory(Shepherd myShepherd, String type, String label) throws DataDefinitionException, ConfigurationException {
        if ((type == null) || (label == null)) throw new IllegalArgumentException("type and label must not be null");
        String confKey = "site.custom.customFieldCategories";
        Configuration conf = ConfigurationUtil.getConfiguration(myShepherd, confKey);
        JSONArray cats = new JSONArray();
        if (conf != null) cats = conf.getValueAsJSONArray();
        for (int i = 0 ; i < cats.length() ; i++) {
            JSONObject c = cats.optJSONObject(i);
            if (c == null) continue;
            String cid = c.optString("id", null);
            String clabel = c.optString("label", null);
            String ctype = c.optString("type", null);
            if ((cid == null) || (clabel == null) || (ctype == null)) {
                System.out.println("WARNING: CustomFieldCategory got wonky entry: " + c);
                continue;
            }
            if (ctype.equals(type) && clabel.equals(label)) return cid;
        }
        JSONObject newCat = new JSONObject();
        newCat.put("id", Util.generateUUID());
        newCat.put("type", type);
        newCat.put("label", label);
        newCat.put("timeCreated", System.currentTimeMillis());
        cats.put(newCat);
        conf = ConfigurationUtil.setConfigurationValue(myShepherd, confKey, cats);
        ConfigurationUtil.resetValueCache("site");
        return newCat.getString("id");
    }

}
