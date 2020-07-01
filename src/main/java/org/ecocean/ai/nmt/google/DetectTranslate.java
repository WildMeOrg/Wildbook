package org.ecocean.ai.nmt.google;


import com.google.cloud.translate.Detection;
import com.google.cloud.translate.Translate;
import com.google.cloud.translate.TranslateOptions;
import com.google.cloud.translate.Translation;
import com.google.cloud.translate.Translate.TranslateOption;

public class DetectTranslate {

  
  //Depends on the system variable GOOGLE_APPLICATION_CREDENTIALS to get JSON key for service account authentication  
  public static String translateToEnglish(String text){
    return translateToLanguage(text, "en");
  }

  //Depends on the system variable GOOGLE_APPLICATION_CREDENTIALS to get JSON key for service account authentication
  public static String detectLanguage(String text){
    //String apiKey= ShepherdProperties.getProperties("googleKeys.properties","").getProperty("translate_key");
    //Translate translate = TranslateOptions.newBuilder().setApiKey(apiKey).build().getService();
    
    Translate translate = TranslateOptions.getDefaultInstance().getService();
    
    
    Detection detection = translate.detect(text);
    String detectedLanguage = detection.getLanguage();
    System.out.println("Detected language "+detectedLanguage+" from text: "+text);
    return detectedLanguage;
  }
  
  //Depends on the system variable GOOGLE_APPLICATION_CREDENTIALS to get JSON key for service account authentication
  //Language codes are two-letter, lowercase combinations supported by Google Translator
  public static String translateToLanguage(String text, String twoLetterLanguageCode){
    //String apiKey= ShepherdProperties.getProperties("googleKeys.properties","").getProperty("translate_key");
    //Translate translate = TranslateOptions.newBuilder().setApiKey(apiKey).build().getService();
    
    //Depends on the system variable GOOGLE_APPLICATION_CREDENTIALS to get JSON key for service account authentication
    Translate translate = TranslateOptions.getDefaultInstance().getService();   
    Translation translation = translate.translate(text, TranslateOption.targetLanguage(twoLetterLanguageCode));
    //System.out.println(translation.getTranslatedText());
    text=translation.getTranslatedText();
    return text;
  }
  
  public static String translateIfNotEnglish(String text){
    String shortForm=text;
    try{
    if(shortForm.length()>500){shortForm=shortForm.substring(0,499);}
      String langCode=DetectTranslate.detectLanguage(shortForm);
      if((!langCode.toLowerCase().equals("en"))&&(!langCode.toLowerCase().equals("und"))){
        System.out.println("Translating: "+text);
        text=DetectTranslate.translateToEnglish(text).replaceAll(",", " ").replaceAll("\n", " ").replaceAll("'", "").replaceAll("\"", "").replaceAll("′","").replaceAll("’","").toLowerCase();
        System.out.println("Translated to: "+text);
      }
    }
    catch(Exception e){}
    return text;
  }



}
