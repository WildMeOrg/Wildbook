package org.ecocean;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Properties; 
import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;
import javax.servlet.http.HttpServletRequest;


public class ShepherdProperties {

  public static Properties getProperties(String fileName){
    return getProperties(fileName, "en");
  }
  
  public static Properties getProperties(String fileName, String langCode){
    return getProperties(fileName, langCode, "context0");
  }

  public static Properties getProperties(String fileName, String langCode, String context){
    return getProperties(fileName, langCode, context, null);
  }

  // derives the overridePrefix from the user (derived from the request)
  public static Properties getOrgProperties(String fileName, String langCode, String context, HttpServletRequest request) {
    System.out.println("getOrgProperties called");
    if (request == null || request.getUserPrincipal() == null) return getProperties(fileName, langCode, context, null);
    Shepherd myShepherd = new Shepherd(request);
    User user = myShepherd.getUser(request);
    System.out.println("getOrgProperties has user "+user);
    if (user == null) return getProperties(fileName, langCode, context, null);
    System.out.println("getOrgProperties going to use override string "+getOverrideStringForUser(user));
    return getProperties(fileName, langCode, context, getOverrideStringForUser(user));
  }

  public static String getOverrideStringForUser(User user) {
    if (user.getOrganizations()==null) return null;
    for (Organization org: user.getOrganizations()) {
      String name = org.getName();
      if (name==null) return null;
      name = name.toLowerCase();
      if (overrideOrgs.contains(name)) return name;
    }
    return null;
  }

  public static final String[] overrideOrgsArr = {"indocet"};
  public static final Set<String> overrideOrgs = new HashSet<>(Arrays.asList(overrideOrgsArr));

  public static Properties getProperties(String fileName, String langCode, String context, String overridePrefix){
    // initialize props as empty (no override provided) or the default values (if override provided)
    // initializing
    boolean verbose = (Util.stringExists(overridePrefix));

    String shepherdDataDir="wildbook_data_dir";
    Properties contextsProps=getContextsProperties();
    if(contextsProps.getProperty(context+"DataDir")!=null) {
      shepherdDataDir=contextsProps.getProperty(context+"DataDir"); 
    }
    Properties props = (overridePrefix==null) 
      ? new Properties(loadOverrideProps(shepherdDataDir, fileName, langCode)) // load override properties as defaults
      : new Properties(getProperties(fileName, langCode, context, null)); // Java's Properties-with-another-Properties-as-default constructor, using this method to make the default.
    if(!langCode.equals("")){
      langCode=langCode+"/";
    }
    String overrideStr = (overridePrefix==null) ? "" : overridePrefix+"/";
    String pathStr = "webapps/wildbook_data_dir/WEB-INF/classes/bundles/"+langCode+overrideStr+fileName;

    try {

      File propertiesFile = new File(pathStr);
      InputStream inputStream = new FileInputStream(propertiesFile);
      if (inputStream!=null) props.load(new InputStreamReader(inputStream, Charset.forName("UTF-8")));
      else System.out.println("getProperties inputStream is null for path "+pathStr);
      //if (verbose) System.out.printf("\tDone loading the input stream. Props size is now "+props.size()+"\n");
      //System.out.printf("\tDone loading the input stream. Props size is now "+props.size()+"\n");
      if (inputStream!=null) inputStream.close();
    } catch (IOException ioe) {
      if (Util.stringExists(langCode) && !langCode.equals("en") && !langCode.equals("en/")) {
        System.out.printf("\t Weird Shepherd.properties non-english case reached with langCode %s\n",langCode);
        props=(LinkedProperties)getProperties(fileName, "en", context);
      } else {
        ioe.printStackTrace();
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
