package org.ecocean.ai.ocr.azure;

import java.util.ArrayList;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.json.*;

import org.ecocean.media.MediaAsset;
import org.ecocean.ShepherdProperties;

import java.net.*;
import java.util.*;

public class AzureOcr {

    private static final String host = "https://westus2.api.cognitive.microsoft.com/vision/v2.0/ocr";

    public static String detectText(ArrayList<MediaAsset> mas) {
        return detectText(mas, "ukn");
    }
        
    public static String detectText(ArrayList<MediaAsset> mas, String language) {
        String results = "";
        
        for (MediaAsset ma : mas) {
            if (language.length()<2) {
                language = "ukn";
            }
            String responseString = postSingleAsset(ma.webURL().toString(), language);
            results += responseString;
            System.out.println("Response String: "+responseString);
        }
        return results;
    }

    public static String postSingleAsset(String url, String language) {
        String responseString = "";

        try {
            Properties azureProps = ShepherdProperties.getProperties("azure.properties","");
            String subscriptionKey = azureProps.getProperty("subscriptionKeyOCR");

            CloseableHttpClient httpClient = HttpClientBuilder.create().build();
    
            URIBuilder uriBuilder = new URIBuilder(host);
    
            uriBuilder.setParameter("language", language);
            uriBuilder.setParameter("detectOrientation", "true");

            URI uri = uriBuilder.build();
            HttpPost request = new HttpPost(uri);
    
            request.setHeader("Content-Type", "application/json");
            request.setHeader("Ocp-Apim-Subscription-Key", subscriptionKey);

            StringEntity requestEntity = new StringEntity("{\"url\":\"" + url + "\"}");
            request.setEntity(requestEntity);
    
            // Make the REST API call and get the response entity.
            HttpResponse response = httpClient.execute(request);
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                String jsonString = EntityUtils.toString(entity);
                JSONObject json = new JSONObject(jsonString);

                // We just want a flat string of space delimited words to pull date stuff in IBEISIA.java
                responseString = parseJSONResponseToWordString(json);

                //System.out.println("REST Response:\n");
                //System.out.println(json.toString(2));
                //System.out.println("PARSED JSON WORD STRING: "+responseString);
            }

        } catch (Exception e) {
            System.out.println("Failed to get a response posting MediaAsset with URL: "+url);
            e.printStackTrace();
        }   
        return responseString;
    }

    private static String formatWords(String wordString) {
        String formatted = wordString.replaceAll("\"", "").toLowerCase().trim();
        return formatted;
    }

    private static String parseJSONResponseToWordString(JSONObject json) {
        JsonParser parser = new JsonParser();
        String content = "";
        try {
            if (json.get("regions")!=null) {
                JsonArray regionArr = parser.parse(json.get("regions").toString()).getAsJsonArray();
                for (JsonElement region : regionArr) {
                    if (region.getAsJsonObject().get("lines")!=null) {
                        JsonArray lines = region.getAsJsonObject().getAsJsonArray("lines");
                        for (JsonElement line : lines) {
                            if (line.getAsJsonObject().get("words")!=null) {
                                JsonArray words = line.getAsJsonObject().getAsJsonArray("words");
                                for (JsonElement word : words) {
                                    String wordString = word.getAsJsonObject().get("text").getAsString();
                                    if (!content.contains(wordString)) {
                                        content += " "+word.getAsJsonObject().get("text");
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return formatWords(content);
    }

}
