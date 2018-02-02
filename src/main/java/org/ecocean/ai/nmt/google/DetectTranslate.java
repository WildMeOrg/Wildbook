package org.ecocean.ai.nmt.google;

//import org.ecocean.CommonConfiguration;
import org.ecocean.ShepherdProperties;

import com.google.cloud.translate.Detection;
import com.google.cloud.translate.Translate;
import com.google.cloud.translate.TranslateOptions;
import com.google.cloud.translate.Translation;
import com.google.cloud.translate.Translate.TranslateOption;

public class DetectTranslate {

  public static String translateToEnglish(String text, String context){
    //String apiKey= CommonConfiguration.getProperty("translate_key", context);
    String apiKey= ShepherdProperties.getProperties("googleKeys.properties","").getProperty("translate_key");
    Translate translate = TranslateOptions.newBuilder().setApiKey(apiKey).build().getService();
    Translation translation = translate.translate(text,
    TranslateOption.targetLanguage("en"));
    //System.out.println(translation.getTranslatedText());
    text=translation.getTranslatedText();
    return text;
  }

  public static String detectLanguage(String text, String context){
    String apiKey= ShepherdProperties.getProperties("googleKeys.properties","").getProperty("translate_key");
    Translate translate = TranslateOptions.newBuilder().setApiKey(apiKey).build().getService();
    Detection detection = translate.detect(text);
    String detectedLanguage = detection.getLanguage();
    System.out.println("Detected language "+detectedLanguage+" from text: "+text);
    return detectedLanguage;
  }
  
  public static String translateToLanguage(String text, String language, String context){
    String apiKey= ShepherdProperties.getProperties("googleKeys.properties","").getProperty("translate_key");
    Translate translate = TranslateOptions.newBuilder().setApiKey(apiKey).build().getService();
    Translation translation = translate.translate(text, TranslateOption.targetLanguage(language));
    //System.out.println(translation.getTranslatedText());
    text=translation.getTranslatedText();
    return text;
  }

  
  
  //Legacy Methods from Stella
  /*
  public static String translate(String ytRemarks, String context){
    return translateToEnglish(ytRemarks, context);
  }

  public static String detect(String ytRemarks, String context){
    return detectLanguage(ytRemarks, context);
  }
  */

}
