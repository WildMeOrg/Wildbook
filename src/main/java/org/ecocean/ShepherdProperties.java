package org.ecocean;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class ShepherdProperties {
  
  public static Properties getProperties(String fileName){
    return getProperties(fileName, "en");
  }
  
  public static Properties getProperties(String fileName, String langCode){
    Properties props=new Properties();
    
    String shepherdDataDir="shepherd_data_dir";
    if((CommonConfiguration.getProperty("dataDirectoryName")!=null)&&(!CommonConfiguration.getProperty("dataDirectoryName").trim().equals(""))){shepherdDataDir=CommonConfiguration.getProperty("dataDirectoryName");}
    Properties overrideProps=loadOverrideProps(shepherdDataDir, fileName, langCode);
    //System.out.println(overrideProps);
    
    if(overrideProps.size()>0){props=overrideProps;}
    else {
      //otherwise load the embedded commonConfig
      
      try {
        props.load(ShepherdProperties.class.getResourceAsStream("/bundles/"+langCode+"/"+fileName));
      } 
      catch (IOException ioe) {
        ioe.printStackTrace();
      }
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
    return myProps;
  }

}
