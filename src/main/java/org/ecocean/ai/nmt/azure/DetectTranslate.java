package org.ecocean.ai.nmt.azure;

import java.io.*;
import java.net.*;
import java.util.*;
import javax.net.ssl.HttpsURLConnection;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.ecocean.ShepherdProperties;

public class DetectTranslate {

    static String host = "https://api.cognitive.microsofttranslator.com";
    static String detectPath = "/detect?api-version=3.0";
    static String translatePath = "/translate?api-version=3.0";
    
    // Whatever
    private static class RequestBody {
        String Text;
        public RequestBody(String text) {
            this.Text = text;
            //System.out.println("Azure DetectTranslate Request body text: "+text);
        }
    }
    
    public static String translateToEnglish(String text){
        return translateToLanguage(text, "en");
    }
    
    //AZURE - generic post...
    private static String postToAzure(URL url, String content) {
        Properties azureProps = ShepherdProperties.getProperties("azureNMT.properties","");
        String subscriptionKey = azureProps.getProperty("subscriptionKey");
        StringBuilder response = new StringBuilder ();
        try {
            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Content-Length", content.length() + "");
            connection.setRequestProperty("Ocp-Apim-Subscription-Key", subscriptionKey);
            connection.setRequestProperty("X-ClientTraceId", java.util.UUID.randomUUID().toString());
            connection.setDoOutput(true);

            DataOutputStream wr = new DataOutputStream(connection.getOutputStream());
            byte[] encoded_content = content.getBytes("UTF-8");
            wr.write(encoded_content, 0, encoded_content.length);
            wr.flush();
            wr.close();

            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
            String line;
            while ((line = in.readLine()) != null) {
                response.append(line);
            }
            in.close();
        } catch (Exception e) {
            System.out.println(" ===================>>>>>>>>>>>>>>>>>>>>>> THIS IS THE CONTENT BEING REJECTED: "+content);
            e.printStackTrace();
        }
        return response.toString();
    }

    private static JsonObject getAsJsonObject(String jsonString) {
        JsonParser parser = new JsonParser();
        //System.out.println("JSONSTRING in getAsJsonObject --------> "+jsonString);
        JsonObject json = parser.parse(jsonString).getAsJsonArray().get(0).getAsJsonObject();
        return json;
    }

    public static String detectLanguage(String input)  throws MalformedURLException{
        String langCode = "";
        String text = cleanStringForPosting(input);
        if (text!=null&&!"".equals(text)) {
            URL url = new URL(host+detectPath);
            List<RequestBody> objList = new ArrayList<RequestBody>();
            objList.add(new RequestBody(text));
            try {
                String content = new Gson().toJson(objList);
                System.out.println("Sending this to Azure detectLanguage: "+text);
                String jsonString = postToAzure(url,content);
                System.out.println("Response from Azure detectLanguage: "+jsonString);
                JsonObject jsonObjResponse = getAsJsonObject(jsonString);
                langCode = jsonObjResponse.get("language").getAsString();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return langCode;
    }

    public static String translateToLanguage(String input, String twoLetterLanguageCode) {
        String params = "&to="+twoLetterLanguageCode;
        String result = "";
        URL url = null;
        String text = cleanStringForPosting(input);
        if (text!=null&&!"".equals(text)) {
            try {
                url = new URL(host+translatePath+params);
            } catch (MalformedURLException mue) {
                mue.printStackTrace();
            }
            
            List<RequestBody> objList = new ArrayList<RequestBody>();
            objList.add(new RequestBody(text));
            
            try {
                String content = new Gson().toJson(objList);
                String jsonString = postToAzure(url,content);
                //System.out.println("---------> translateToLanguage jsonString response: "+jsonString);
                JsonObject jsonObjResponse = getAsJsonObject(jsonString);
                JsonArray arr = jsonObjResponse.get("translations").getAsJsonArray();
                result = arr.get(0).getAsJsonObject().get("text").toString();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return result;
    }

    private static String cleanStringForPosting(String text) {
        String cleanText = text.replaceAll(",", " ").replaceAll("\n", " ").replaceAll("'", "").replaceAll("\"", "").replaceAll("′","").replaceAll("’","").replace("[","").replace("]","").trim().toLowerCase();  
        return cleanText;
    }

    public static String translateIfNotEnglish(String text){
        String shortForm=text;
        try{
            if(shortForm.length()>500){shortForm=shortForm.substring(0,499);}
            String langCode=DetectTranslate.detectLanguage(shortForm);
            System.out.println("---------> Detected language code: "+langCode+" with text ===> "+text);
            if(!langCode.toLowerCase().equals("en")&&langCode.length()>1&&langCode!="") {
                text=DetectTranslate.translateToEnglish(text);
                System.out.println("---------> Translated to: "+text);
            } 
        } catch (Exception e){
            e.printStackTrace();
        }
        return text;
    }

}
