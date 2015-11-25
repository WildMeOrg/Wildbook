package org.ecocean;
///NOTE: due to the authentication header stuff, this is effectively IBEIS-specific.  break this out later i guess!  TODO

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Iterator;
import org.json.JSONObject;

import javax.crypto.spec.SecretKeySpec;
import javax.crypto.Mac;
import java.security.NoSuchAlgorithmException;
import java.security.InvalidKeyException;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.client.utils.URIBuilder;

/*
javax.ws.rs.core.UriBuilder
https://stackoverflow.com/a/29053050/1525311
*/

public class RestClient {
    private static final String HMAC_SHA1_ALGORITHM = "HmacSHA1";

    //here, "data" should(?) be JSONObject or JSONArray -- or something else which .toString() returns valid json!
    public static JSONObject post(URL url, JSONObject data) throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException, InvalidKeyException {
        return anyMethod("POST", url, data);
    }

    public static JSONObject get(URL url) throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException, InvalidKeyException {
        return anyMethod("GET", url, null);
    }

    private static JSONObject anyMethod(String method, URL url, JSONObject data) throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException, InvalidKeyException {
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setReadTimeout(10000);
        conn.setConnectTimeout(15000);
        conn.setDoOutput(true);
        conn.setDoInput(true);
        conn.setRequestMethod(method);
        ///conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", getAuthorizationHeader(url.toString()));
        if (data != null) {
            OutputStream os = conn.getOutputStream();
            os.write(getPostDataString(data).getBytes());
            os.flush();
            os.close();
        }
        conn.connect();

        if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
            throw new RuntimeException("Failed : HTTP error code : " + conn.getResponseCode());
        }

        BufferedReader br = new BufferedReader(new InputStreamReader((conn.getInputStream())));
        String output;
        String jtext = "";
        while ((output = br.readLine()) != null) {
            jtext += output;
        }
        conn.disconnect();
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


    private static String getSignature(String key, byte[] messageToSendBytes) throws NoSuchAlgorithmException, InvalidKeyException {
        SecretKeySpec keyHmac = new SecretKeySpec(key.getBytes(), HMAC_SHA1_ALGORITHM);
        Mac mac = Mac.getInstance(HMAC_SHA1_ALGORITHM);
        mac.init(keyHmac);
        return new String(Base64.encodeBase64(mac.doFinal(messageToSendBytes)));
    }

    public static String getAuthorizationHeader(String url) throws NoSuchAlgorithmException, InvalidKeyException {
        String appName = "IBEIS";
        String appSecret = "CB73808F-A6F6-094B-5FCD-385EBAFF8FC0";
        return appName + ":" + getSignature(appSecret, url.getBytes());
    }

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
System.out.println("------- getPostDataString=" + result.toString());
        return result.toString();
    }
}
