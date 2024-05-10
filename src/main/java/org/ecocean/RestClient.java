package org.ecocean;
/*  NOTE: due to the authentication header stuff, this is effectively IBEIS-specific.  break this out later i guess!  TODO

   2018-02-02 - i am starting to break this out into generic... but very much a work-in-progress.... see methods at the end of the file which will
      hopefully allow control over auth stuff better....   -jon
 */

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.json.JSONObject;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.client.utils.URIBuilder;

/*
   javax.ws.rs.core.UriBuilder https://stackoverflow.com/a/29053050/1525311
 */

public class RestClient {
    ///TODO this is IBEIS-specific -- need to generalize for RestClient to be universal
    private static final String HMAC_SHA1_ALGORITHM = "HmacSHA1";
    private static final int CONNECTION_TIMEOUT = 300000; // maybe this should be service-specific?

    public static JSONObject post(URL url, JSONObject data)
    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
        InvalidKeyException {
        return anyMethod("POST", url, data);
    }

    public static JSONObject put(URL url, JSONObject data)
    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
        InvalidKeyException {
        return anyMethod("PUT", url, data);
    }

    public static JSONObject get(URL url)
    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
        InvalidKeyException {
        return anyMethod("GET", url, null);
    }

    public static JSONObject get(URL url, int timeout)
    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
        InvalidKeyException {
        return anyMethod("GET", url, null, timeout);
    }

    public static JSONObject get(URL url, JSONObject data)
    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
        InvalidKeyException {
        return anyMethod("GET", url, data);
    }

    // IBEIS-specifically, data gets posted as name-value pairs where name comes from the keys
    private static JSONObject anyMethod(String method, URL url, JSONObject data)
    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
        InvalidKeyException {
        return anyMethod(method, url, data, CONNECTION_TIMEOUT);
    }

    private static JSONObject anyMethod(String method, URL url, JSONObject data, int ctimeout)
    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
        InvalidKeyException {
        System.out.println("TRYING anyMethod(" + method + ") url -> " + url);
        // System.setProperty("http.keepAlive", "false");
        HttpURLConnection conn = (HttpURLConnection)url.openConnection();
        conn.setReadTimeout(ctimeout);
        conn.setConnectTimeout(ctimeout);
        conn.setDoOutput((data != null));
        conn.setDoInput(true);
        conn.setRequestMethod(method);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setRequestProperty("Authorization", getAuthorizationHeader(url.toString()));
        if (data != null) {
            OutputStream os = conn.getOutputStream();
            os.write(getPostDataString(data).getBytes());
            os.flush();
            os.close();
        }
        conn.connect();

        boolean success = true;
        // TODO the 600 response here is IBEIS-specific, so we need to genericize this
        if ((conn.getResponseCode() != HttpURLConnection.HTTP_OK) &&
            (conn.getResponseCode() != 600)) {
            // conn.disconnnect();
            System.out.println("!!!!!!!!!!!!!!!!!!! [url = " + url.toString() +
                "] bad response code = " + conn.getResponseCode());
            success = false;
        }
        BufferedReader br = null;
/*
        if (success) {
            br = new BufferedReader(new InputStreamReader((conn.getInputStream())));
        } else {
            br = new BufferedReader(new InputStreamReader((conn.getErrorStream())));
        }
 */
        try {
            br = new BufferedReader(new InputStreamReader((conn.getInputStream())));
        } catch (IOException ioe) {
            br = new BufferedReader(new InputStreamReader((conn.getErrorStream())));
        }
        String output;
        String jtext = "";
        while ((output = br.readLine()) != null) {
            jtext += output;
        }
        br.close();
        // conn.disconnect();
        if (!success) {
            System.out.println("========= anyMethod failed with code=" + conn.getResponseCode() +
                "\n" + jtext + "\n============");
            throw new RuntimeException("Failed : HTTP error code : " + conn.getResponseCode());
        }
        if (jtext.equals("")) return null;
        return new JSONObject(jtext);

/*
      } catch (MalformedURLException e) {

        e.printStackTrace();

      } catch (IOException e) {

        e.printStackTrace();

     }
 */
    }

    public static JSONObject postStream(URL url, InputStream in)
    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
        InvalidKeyException {
        HttpURLConnection conn = (HttpURLConnection)url.openConnection();

        conn.setReadTimeout(CONNECTION_TIMEOUT);
        conn.setConnectTimeout(CONNECTION_TIMEOUT);
        conn.setDoOutput(true);
        conn.setDoInput(true);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        // conn.setRequestProperty("Authorization", getAuthorizationHeader(url.toString()));
        OutputStream os = conn.getOutputStream();
        byte[] buffer = new byte[10240];
        int len;
// System.out.println("OK, begin<");
        while ((len = in.read(buffer)) != -1) {
            os.write(buffer, 0, len);
// System.out.write(buffer, 0, len);
        }
        in.close();
        os.flush();
        os.close();
        conn.connect();

        boolean success = true;
        if ((conn.getResponseCode() != HttpURLConnection.HTTP_OK)) {
            // conn.disconnnect();
            System.out.println("!!!!!!!!!!!!!!!!!!! bad response code = " + conn.getResponseCode());
            success = false;
        }
        if (!success) {
            JSONObject rtn = new JSONObject();
            rtn.put("error", conn.getResponseCode());
            return rtn;
        }
/*
   InputStream is = request.getInputStream();
   byte buffer[] = new byte[10240];
   int i;
   System.out.println("before....");
   while ((i = is.read(buffer)) > 0) {
    System.out.write(buffer, 0, i);
   }
 */

        BufferedReader br = null;
/*
        if (success) {
            br = new BufferedReader(new InputStreamReader((conn.getInputStream())));
        } else {
            br = new BufferedReader(new InputStreamReader((conn.getErrorStream())));
        }
 */
        try {
            br = new BufferedReader(new InputStreamReader((conn.getInputStream())));
        } catch (IOException ioe) {
            success = false;
            br = new BufferedReader(new InputStreamReader((conn.getErrorStream())));
        }
        String output;
        String jtext = "";
        while ((output = br.readLine()) != null) {
            jtext += output;
        }
        br.close();
        // conn.disconnect();
/*
        if (!success) {
            System.out.println("========= anyMethod failed with code=" + conn.getResponseCode() + "\n" + jtext + "\n============");
            throw new RuntimeException("Failed : HTTP error code : " + conn.getResponseCode());
        }
 */
        if (jtext.equals("")) return null;
        System.out.println("======================== postStream -> " + jtext);
        return new JSONObject(jtext);
    }

    ///TODO this chunk below is IBEIS-specific -- need to generalize for RestClient to be universal

    private static String getSignature(String key, byte[] messageToSendBytes)
    throws NoSuchAlgorithmException, InvalidKeyException {
        SecretKeySpec keyHmac = new SecretKeySpec(key.getBytes(), HMAC_SHA1_ALGORITHM);
        Mac mac = Mac.getInstance(HMAC_SHA1_ALGORITHM);

        mac.init(keyHmac);
        return new String(Base64.encodeBase64(mac.doFinal(messageToSendBytes)));
    }

    public static String getAuthorizationHeader(String url)
    throws NoSuchAlgorithmException, InvalidKeyException {
        String appName = "IBEIS";
        String appSecret = "CB73808F-A6F6-094B-5FCD-385EBAFF8FC0";

        return appName + ":" + getSignature(appSecret, url.getBytes());
    }

    ///// end TODO

    private static String getPostDataString(JSONObject obj) {
        StringBuilder result = new StringBuilder();
        boolean first = true;
        Iterator<?> keys = obj.keys();

        while (keys.hasNext()) {
            String key = (String)keys.next();
            if (first) {
                first = false;
            } else {
                result.append("&");
            }
            try {
                result.append(URLEncoder.encode(key, "UTF-8"));
                result.append("=");
                result.append(URLEncoder.encode(obj.get(key).toString(), "UTF-8"));
            } catch (UnsupportedEncodingException uee) {
                System.out.println("caught exception on key " + key + ": " + uee.toString());
            }
        }
//////System.out.println("------- getPostDataString=(\n" + result.toString() + "\n)--------\n");
        return result.toString();
    }

    public static void writeToFile(URL url, File file)
    throws IOException {
        if (url == null) throw new IOException("RestClient.writeToFile() got null url");
        InputStream is = url.openStream();
        OutputStream os = new FileOutputStream(file);
        byte[] b = new byte[2048];
        int length;
        while ((length = is.read(b)) != -1) {
            os.write(b, 0, length);
        }
        is.close();
        os.close();
    }

    /***********************************************************************************************
       some attempts to *truly* genericize these....

       rather than authUsername/authPassword, we might want to have our own credential class that allows us to set headers etc accordingly!   TODO

       note also that data passed in (for post) is a string, so encode it accordingly first one annoying thing we attempt to handle now is that there
          may be plain-text returned (not always json)
     ***********************************************************************************************/
    public static JSONObject getJSONObject(URL url, String authUsername, String authPassword)
    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
        InvalidKeyException {
        String res = get(url, authUsername, authPassword);

        if (res == null) return null;
        return new JSONObject(res);
    }

    public static String get(URL url, String authUsername, String authPassword)
    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
        InvalidKeyException {
        return anyMethodGeneric("GET", url, authUsername, authPassword, null);
    }

    // you might want to use getPostDataString() to get input data string here
    private static String anyMethodGeneric(String method, URL url, String authUsername,
        String authPassword, String data)
    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
        InvalidKeyException {
        return anyMethodGeneric(method, url, authUsername, authPassword, data, CONNECTION_TIMEOUT);
    }

    private static String anyMethodGeneric(String method, URL url, String authUsername,
        String authPassword, String data, int ctimeout)
    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
        InvalidKeyException {
        System.out.println("TRYING anyMethodGeneric(" + method + ") url -> " + url);
        // System.setProperty("http.keepAlive", "false");
        HttpURLConnection conn = (HttpURLConnection)url.openConnection();
        conn.setReadTimeout(ctimeout);
        conn.setConnectTimeout(ctimeout);
        conn.setDoOutput((data != null));
        conn.setDoInput(true);
        conn.setRequestMethod(method);
        ////conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");  TODO should this be here for GET ???
        if ((authUsername != null) && (authPassword != null)) {
            byte[] authBytes = (authUsername + ":" + authPassword).getBytes("UTF-8");
            String authEncoded = javax.xml.bind.DatatypeConverter.printBase64Binary(authBytes);
            conn.setRequestProperty("Authorization", "Basic " + authEncoded);
        }
        if (data != null) {
            OutputStream os = conn.getOutputStream();
            os.write(data.getBytes());
            os.flush();
            os.close();
        }
        conn.connect();

        boolean success = true;
        if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
            // conn.disconnnect();
            success = false;
        }
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader((conn.getInputStream())));
        } catch (IOException ioe) {
            br = new BufferedReader(new InputStreamReader((conn.getErrorStream())));
        }
        String output;
        String jtext = "";
        while ((output = br.readLine()) != null) {
            jtext += output;
        }
        br.close();
        // conn.disconnect();
        if (!success) {
            System.out.println("========= anyMethod failed with code=" + conn.getResponseCode() +
                "\n" + jtext + "\n============");
            throw new RuntimeException("Failed : HTTP error code : " + conn.getResponseCode());
        }
        if (jtext.equals("")) return null;
        return jtext;
    }

    // much more generic form...
    public static String postRaw(URL url, String data, Map<String, String> headers)
    throws IOException, java.net.ProtocolException {
        HttpURLConnection conn = (HttpURLConnection)url.openConnection();

        conn.setReadTimeout(CONNECTION_TIMEOUT);
        conn.setConnectTimeout(CONNECTION_TIMEOUT);
        conn.setDoOutput((data != null));
        conn.setDoInput(true);
        conn.setRequestMethod("POST");
        if (headers != null) {
            for (String hkey : headers.keySet()) {
                conn.setRequestProperty(hkey, headers.get(hkey));
            }
        }
        if (data != null) {
            OutputStream os = conn.getOutputStream();
            os.write(data.getBytes());
            os.flush();
            os.close();
        }
        conn.connect();

        boolean success = true;
        if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) success = false;
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader((conn.getInputStream())));
        } catch (IOException ioe) {
            br = new BufferedReader(new InputStreamReader((conn.getErrorStream())));
        }
        String output;
        String jtext = "";
        while ((output = br.readLine()) != null) {
            jtext += output;
        }
        br.close();
        // conn.disconnect();
        if (!success) {
            System.out.println("WARNING: postRaw() on " + url + " failed with code=" +
                conn.getResponseCode() + "\n" + jtext + "\n============");
            throw new IOException("HTTP error code = " + conn.getResponseCode());
        }
        if (jtext.equals("")) return null;
        return jtext;
    }

    // JSON-friendly generic  (can pass null for headers and it will get set)
    public static JSONObject postJSON(URL url, JSONObject data, Map<String, String> headers)
    throws IOException, java.net.ProtocolException {
        if (headers == null) headers = new HashMap<String, String>();
        if (headers.get("Content-type") == null) headers.put("Content-type", "application/json");
        String rtn = postRaw(url, (data == null) ? (String)null : data.toString(), headers);
        JSONObject jrtn = Util.stringToJSONObject(rtn);
        if (jrtn == null)
            throw new IOException("could not convert postRaw() to JSONObject: " + rtn);
        return jrtn;
    }
}
