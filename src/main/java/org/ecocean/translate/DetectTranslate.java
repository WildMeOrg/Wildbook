package org.ecocean.translate;

import org.ecocean.CommonConfiguration;

import com.google.cloud.translate.Detection;
import com.google.cloud.translate.Translate;
import com.google.cloud.translate.TranslateOptions;
import com.google.cloud.translate.Translation;
import com.google.cloud.translate.Translate.TranslateOption;

public class DetectTranslate {
  
  public static String translate(String ytRemarks, String context){
    String apiKey= CommonConfiguration.getProperty("translate_key", context);
    
    TranslateOptions.newBuilder().setApiKey(apiKey);
    
    Translate translate = TranslateOptions.getDefaultInstance().getService();             
    Translation translation = translate.translate(ytRemarks,
    TranslateOption.targetLanguage("en"));
    System.out.println(translation.getTranslatedText());  
    ytRemarks=translation.getTranslatedText();  
      
    return ytRemarks;
    
  }
  public static String detect(String ytRemarks){
    TranslateOptions.newBuilder().setApiKey("AIzaSyAm5Rmvrvq58dcnF9JioQfzBFAjf1tKCLQ");
    
    Translate translate = TranslateOptions.getDefaultInstance().getService();  
    Detection detection = translate.detect(ytRemarks);
    String detectedLanguage = detection.getLanguage();
    return detectedLanguage; 
    
  }

}
