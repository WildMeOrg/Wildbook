package org.ecocean;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Properties; 

public class ShepherdProperties {

  public static Properties getProperties(String fileName){
    return getProperties(fileName, "en");
  }
  
  public static Properties getProperties(String fileName, String langCode){
    return getProperties(fileName, langCode, "context0");
  }

  public static Properties getProperties(String fileName, String langCode, String context){
    LinkedProperties props=new LinkedProperties();
    String shepherdDataDir="wildbook_data_dir";
    if(!langCode.equals("")){
      langCode=langCode+"/";
    }
    Properties contextsProps=getContextsProperties();
    if(contextsProps.getProperty(context+"DataDir")!=null){
      shepherdDataDir=contextsProps.getProperty(context+"DataDir"); 
    }
    LinkedProperties overrideProps=loadOverrideProps(shepherdDataDir, fileName, langCode);
    if(overrideProps.size()>0){
      props=overrideProps;
    } else {
      try {
        InputStream inputStream=ShepherdProperties.class.getResourceAsStream("/bundles/"+langCode+fileName);
        props.load(new InputStreamReader(inputStream, Charset.forName("UTF-8")));
        inputStream.close();
      } catch (IOException ioe) {
        if (!langCode.equals("en")) {
          props=(LinkedProperties)getProperties(fileName, "en", context);
        } else {
          ioe.printStackTrace();
        }   
      }
    }
    return props;
  }
  
  public static Properties getContextsProperties(){
    Properties props=new Properties();
      try {
        InputStream inputStream = ShepherdProperties.class.getResourceAsStream("/bundles/contexts.properties");
        props.load(inputStream);
        inputStream.close();
      } catch (IOException ioe) {
        ioe.printStackTrace();
      }
    return props;
  }

  private static LinkedProperties loadOverrideProps(String shepherdDataDir, String fileName, String langCode) {
    LinkedProperties myProps=new LinkedProperties();
    File configDir = new File("webapps/"+shepherdDataDir+"/WEB-INF/classes/bundles/"+langCode);
    if((configDir.getAbsolutePath().contains("/bin/")) || (configDir.getAbsolutePath().contains("\\bin\\"))){
      String fixedPath=configDir.getAbsolutePath().replaceAll("/bin", "").replaceAll("\\\\bin", "");
      configDir=new File(fixedPath);
    }
    if((configDir.getAbsolutePath().contains("/logs/")) || (configDir.getAbsolutePath().contains("\\logs\\"))){
      String fixedPath=configDir.getAbsolutePath().replaceAll("/logs", "").replaceAll("\\\\logs", "");
      configDir=new File(fixedPath);
    }
    if(!configDir.exists()){configDir.mkdirs();}
    File configFile = new File(configDir, fileName);
    if (configFile.exists()) {
      FileInputStream inputStream = null;
      try {
        inputStream = new FileInputStream(configFile);
        myProps.load(new InputStreamReader(inputStream, Charset.forName("UTF-8")));
      } catch (Exception e) {
        e.printStackTrace();
      } finally {
        if (inputStream != null) {
          try {
            inputStream.close();
          } catch (Exception e2) {
            e2.printStackTrace();
          }
        }
      }
    } 
    return myProps;
  }

}
