package org.ecocean;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ShepherdProperties {

  public static Properties getProperties(String fileName){
    return getProperties(fileName, "en");
  }
  
  
  public static Properties getProperties(String fileName, String langCode){
    
    return getProperties(fileName, langCode, "context0");
    
  }

  public static Properties getProperties(String fileName, String langCode, String context){
    Properties props=new Properties();

    String shepherdDataDir="wildbook_data_dir";
    if(!langCode.equals("")){
      langCode=langCode+"/";
    }
    
    //if((CommonConfiguration.getProperty("dataDirectoryName",context)!=null)&&(!CommonConfiguration.getProperty("dataDirectoryName",context).trim().equals(""))){
    //  shepherdDataDir=CommonConfiguration.getProperty("dataDirectoryName",context);
    //}
    
    Properties contextsProps=getContextsProperties();
    if(contextsProps.getProperty(context+"DataDir")!=null){
      shepherdDataDir=contextsProps.getProperty(context+"DataDir");
      
    }
    
    //context change here!
    
    
    Properties overrideProps=loadOverrideProps(shepherdDataDir, fileName, langCode);
    //System.out.println(overrideProps);

    if(overrideProps.size()>0){props=overrideProps;}
    else {
      //otherwise load the embedded commonConfig

      try {
        InputStream inputStream=ShepherdProperties.class.getResourceAsStream("/bundles/"+langCode+fileName);
        props.load(inputStream);
        inputStream.close();
      }
      catch (IOException ioe) {
        
        //OK, we couldn't find the overridden file, and we couldn't find the local file in the webapp
        //default to the English version
        if(!langCode.equals("en")) {
          props=getProperties(fileName, "en", context);
        }
        else {
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
      }
      catch (IOException ioe) {
        ioe.printStackTrace();
      }
    

    return props;
  }

  private static Properties loadOverrideProps(String shepherdDataDir, String fileName, String langCode) {
    //System.out.println("Starting loadOverrideProps");

    Properties myProps=new Properties();
    File configDir = new File("webapps/"+shepherdDataDir+"/WEB-INF/classes/bundles/"+langCode);
    //System.out.println(configDir.getAbsolutePath());
    //sometimes this ends up being the "bin" directory of the J2EE container
    //we need to fix that
    if((configDir.getAbsolutePath().contains("/bin/")) || (configDir.getAbsolutePath().contains("\\bin\\"))){
      String fixedPath=configDir.getAbsolutePath().replaceAll("/bin", "").replaceAll("\\\\bin", "");
      configDir=new File(fixedPath);
      //System.out.println("Fixing the bin issue in Shepherd PMF. ");
      //System.out.println("The fix abs path is: "+configDir.getAbsolutePath());
    }
    if((configDir.getAbsolutePath().contains("/logs/")) || (configDir.getAbsolutePath().contains("\\logs\\"))){
      String fixedPath=configDir.getAbsolutePath().replaceAll("/logs", "").replaceAll("\\\\logs", "");
      configDir=new File(fixedPath);
      //System.out.println("Fixing the logs directory issue in Shepherd PMF. ");
      //System.out.println("The fix abs path is: "+configDir.getAbsolutePath());
    }
    //System.out.println("ShepherdProps: "+configDir.getAbsolutePath());
    if(!configDir.exists()){configDir.mkdirs();}
    File configFile = new File(configDir, fileName);
    if (configFile.exists()) {
      //System.out.println("ShepherdProps: "+"Overriding default properties with " + configFile.getAbsolutePath());
      FileInputStream fileInputStream = null;
      try {
        fileInputStream = new FileInputStream(configFile);
        myProps.load(fileInputStream);
      } catch (Exception e) {
        e.printStackTrace();
      }
      finally {
        if (fileInputStream != null) {
          try {
            fileInputStream.close();
          } catch (Exception e2) {
            e2.printStackTrace();
          }
        }
      }
    }
    else{
      //System.out.println("I could not find the override files that I was expecting at: "+configFile.getAbsolutePath());
    }
    return myProps;
  }

}
