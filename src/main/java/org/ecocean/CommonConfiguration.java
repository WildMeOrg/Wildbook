/*
 * The Shepherd Project - A Mark-Recapture Framework
 * Copyright (C) 2011 Jason Holmberg
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package org.ecocean;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.util.*;
import java.net.URI;
import java.net.URISyntaxException;

import javax.servlet.ServletContext;



import javax.servlet.http.HttpServletRequest;

public class CommonConfiguration {
  
  private static final String COMMON_CONFIGURATION_PROPERTIES = "commonConfiguration.properties";
  
  //class setup
  //private static Properties props = new Properties();
  
  //private static volatile int propsSize = 0;
  
  //private static String currentContext;


  private static Properties initialize(String context) {
    //set up the file input stream
    //if ((currentContext==null)||(!currentContext.equals(context))||(propsSize == 0)) {
      return loadProps(context);
    //}
  }


  
  public static synchronized Properties loadProps(String context) {
      InputStream resourceAsStream = null;
      Properties props=new Properties();
      try {
        //resourceAsStream = CommonConfiguration.class.getResourceAsStream("/bundles/" + COMMON_CONFIGURATION_PROPERTIES);
        //props.load(resourceAsStream);
        props=ShepherdProperties.getProperties(COMMON_CONFIGURATION_PROPERTIES, "",context);

      } catch (Exception ioe) {
        ioe.printStackTrace();
        //return null;
      }

    return props;
  }
  
  

  private static Properties loadOverrideProps(String shepherdDataDir) {
    File configDir = new File("webapps/"+shepherdDataDir+"/WEB-INF/classes/bundles");
    Properties props=new Properties();
    //sometimes this ends up being the "bin" directory of the J2EE container
    //we need to fix that
    if((configDir.getAbsolutePath().contains("/bin/"))||(configDir.getAbsolutePath().contains("\\bin\\"))){
      String fixedPath=configDir.getAbsolutePath().replaceAll("/bin", "").replaceAll("\\\\bin", "");
      configDir=new File(fixedPath);
      //System.out.println("Fixing the bin issue in CommonConfiguration.");
      //System.out.println("The fix absolute path is: "+configDir.getAbsolutePath());
    }
    
    if(!configDir.exists()){configDir.mkdirs();}
    File configFile = new File(configDir, COMMON_CONFIGURATION_PROPERTIES);
    if (configFile.exists()) {
      //System.out.println("Overriding default properties with " + configFile.getAbsolutePath());
      FileInputStream fileInputStream = null;
      try {
        fileInputStream = new FileInputStream(configFile);
        props.load(fileInputStream);
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
    else {
      //System.out.println("No properties override file found at " + configFile.getAbsolutePath());
    }
    return props;
  }

  //start getter methods
  public static String getURLLocation(HttpServletRequest request) {
    int port = request.getServerPort();
    return request.getServerName() + ((port == 80) ? "" : ":" + port) + request.getContextPath();
  }


  /**
   * Utility method to return a {@code URI} instance for the specified
   * context path of the server relating to the servlet request.
   * This method ensures all appropriate encoding is performed for the respective
   * parts of the URI.
   * @param req HttpServletRequest for which to return server root URI
   * @param contextPath context path for the URI (must start with '/')
   * @return URI for the specified context path
   * @throws URISyntaxException if thrown when creating URI
   */
  public static URI getServerURI(HttpServletRequest req, String contextPath) throws URISyntaxException {
    return new URI(req.getScheme(), null, req.getServerName(), req.getServerPort(), contextPath, null, null).normalize();
  }

  /**
   * Utility method to return a URL string for the specified
   * context path of the server relating to the servlet request.
   * This method ensures all appropriate encoding is performed for the respective
   * parts of the URI.
   * @param req HttpServletRequest for which to return server root URL
   * @param contextPath context path for the URI (must start with '/')
   * @return URI string for the server's root (without context path)
   * @throws URISyntaxException if thrown when creating URI
   */
  public static String getServerURL(HttpServletRequest req, String contextPath) throws URISyntaxException {
    return getServerURI(req, contextPath).toASCIIString();
  }


  public static String getMailHost(String context) {
    String s = getProperty("mailHost", context);
    return s != null ? s.trim() : s;
  }

  public static boolean getMailHostSslOption(String context) {
    return parseBoolean(getProperty("mailHostSSL",context), false);
  }

  public static String getMailAuth(String context) {
    String s = getProperty("mailAuth", context);
    return s != null ? s.trim() : s;
  }

  public static String getWikiLocation(String context) {
    Properties props=initialize(context);
    if(props.getProperty("wikiLocation")!=null){return props.getProperty("wikiLocation").trim();}
    return null;
  }

  public static String getDBLocation(String context) {
    return getProperty("dbLocation",context).trim();
  }

  public static String getAutoEmailAddress(String context) {
    return getProperty("autoEmailAddress", context).trim();
  }

  public static String getNewSubmissionEmail(String context) {
    return getProperty("newSubmissionEmail",context).trim();
  }

  public static String getR(String context) {
    return getProperty("R", context).trim();
  }

  public static String getEpsilon(String context) {
    return getProperty("epsilon",context).trim();
  }

  public static String getSizelim(String context) {
    return getProperty("sizelim",context).trim();
  }

    public static String getUploadTmpDir(String context) {
        String dir = getProperty("uploadTmpDir", context);
        if (dir == null) {
            dir = System.getProperty("java.io.tmpdir");
            System.out.println("WARNING: no uploadTmpDir property specified in CommonConfiguration; using " + dir + " as default; this may introduce insecurities.");
        }
        return dir.trim();
    }

  public static String getMaxTriangleRotation(String context) {
    return getProperty("maxTriangleRotation",context).trim();
  }

  public static String getC(String context) {
    return getProperty("C",context).trim();
  }

  public static String getHTMLDescription(String context) {
    return getProperty("htmlDescription",context).trim();
  }
  
  public static int getMaxMediaSizeInMegabytes(String context){
    int maxSize=10;
    
    try{
      String sMaxSize=getProperty("maxMediaSize", context);
      if(sMaxSize!=null){
        Integer value=new Integer(sMaxSize);
        maxSize=value.intValue();
      }
    }
    catch(Exception e){
      System.out.println("\n\nHit an exception trying to read maxMediaSize property from commonConfiguration.properties.");
      e.printStackTrace();
    }
    return maxSize;
  }

  public static String getHTMLKeywords(String context) {
    return getProperty("htmlKeywords",context).trim();
  }

  public static String getHTMLTitle(String context) {
    return getProperty("htmlTitle",context).trim();
  }


  public static String getCSSURLLocation(HttpServletRequest request, String context) {
    return (request.getScheme() + "://" +
      getURLLocation(request) + "/" +
      getProperty("cssURLLocation",context)).trim();
  }

  public static String getHTMLAuthor(String context) {
    return getProperty("htmlAuthor",context).trim();
  }

  public static String getHTMLShortcutIcon(String context) {
    return getProperty("htmlShortcutIcon",context).trim();
  }

  public static String getGlobalUniqueIdentifierPrefix(String context) {
    return getProperty("GlobalUniqueIdentifierPrefix",context);
  }

  public static String getURLToMastheadGraphic(String context) {
    return getProperty("urlToMastheadGraphic",context);
  }

  public static String getTapirLinkURL(String context) {
    return getProperty("tapirLinkURL",context);
  }

  public static String getIPTURL(String context) {
    return getProperty("iptURL",context);
  }

  public static String getURLToFooterGraphic(String context) {
    return getProperty("urlToFooterGraphic",context);
  }

  public static String getGoogleMapsKey(String context) {
    return getProperty("googleMapsKey",context);
  }

  public static String getGoogleSearchKey(String context) {
    return getProperty("googleSearchKey",context);
  }

  public static String getProperty(String name, String context) {
    return initialize(context).getProperty(name);
  }
  
  public static Enumeration<?> getPropertyNames(String context) {
    return initialize(context).propertyNames();
  }

  public static ArrayList<String> getSequentialPropertyValues(String propertyPrefix, String context){
    Properties myProps=initialize(context);
    //System.out.println(myProps.toString());
    ArrayList<String> returnThese=new ArrayList<String>();

    //System.out.println("Looking for: "+propertyPrefix);

    int iter=0;
    while(myProps.getProperty(propertyPrefix+iter)!=null){
      //System.out.println("Found: "+propertyPrefix+iter);
      returnThese.add(myProps.getProperty((propertyPrefix+iter)));
      iter++;
    }

    return returnThese;
  }

  /*
   * This method is used to determined the show/hide condition of an element of the UI.
   * It simply looks to see if a property is defined AND if the property is false.
   * For any other value or if the value is absent, the method returns true. Thsi means that conditional elements
   * are shown by default.
   *
   * @param thisString The name of the property to show/hide.
   * @return true if the property is not defined or has any other value than "false". Otherwise, returns false.
   */
  public static boolean showProperty(String thisString, String context) {
    if((getProperty(thisString, context)!=null)&&(getProperty(thisString, context).equals("false"))){return false;}
    return true;
  }

  /**
   * This configuration option defines whether adoptions of MarkedIndividual or encounter objects are allowed. Generally adoptions are used as a fundraising or public awareness tool.
   *
   * @return true if adoption functionality should be displayed. False if adoptions are not supported in this catalog.
   */
  public static boolean allowAdoptions(String context) {
    initialize(context);
    boolean canAdopt = true;
    if ((getProperty("allowAdoptions",context) != null) && (getProperty("allowAdoptions", context).equals("false"))) {
      canAdopt = false;
    }
    return canAdopt;
  }


  /**
   * This configuration option defines whether batch upload of {@link MarkedIndividual} or {@link Encounter} objects are allowed.
   *
   * @return true if batch upload functionality should be displayed. False if batch upload are not supported in this catalog.
   */
  public static boolean allowBatchUpload(String context) {
    return parseBoolean(getProperty("allowBatchUpload",context), false);
  }



  /**
   * Helper method to parse boolean from string.
   * @param s string to parse
   * @param def default value
   * @return true if s is one of { true, yes, ok, 1 }
   */
  private static boolean parseBoolean(String s, boolean def) {
    if (s == null)
      return def;
    String prop = s.trim().toLowerCase(Locale.US);
    if ("true".equals(prop) || "yes".equals(prop) || "ok".equals(prop) || "1".equals(prop)) {
      return true;
    }
    return false;
  }

  /**
   * This configuration option defines the class name of the batch data plugin
   * to use (must implement {@link org.ecocean.batch.BatchProcessorPlugin}).
   *
   * @return Fully-qualified class name of the plugin to use, or null.
   */
  public static String getBatchUploadPlugin(String context) {
    //initialize(context);
    if (getProperty("batchUploadPlugin", context) != null) {
      return getProperty("batchUploadPlugin", context).trim();
    }
    return null;
  }

  /**
   * This configuration option defines whether batch upload of {@link MarkedIndividual} or {@link Encounter} objects are allowed.
   *
   * @return true if batch upload functionality should be displayed. False if batch upload are not supported in this catalog.
   */
  public static int getBatchUploadProgressRefresh(String context) {
    initialize(context);
    int def = 10;
    String prop = getProperty("batchUploadProgressRefresh", context);
    if (prop == null || "".equals(prop.trim())) {
      return def;
    }
    try {
      return Integer.parseInt(prop.trim());
    } catch (NumberFormatException ex) {
      return def;
    }
  }




  public static boolean sendEmailNotifications(String context) {
    initialize(context);
    boolean sendNotifications = true;
    if ((getProperty("sendEmailNotifications",context) != null) && (getProperty("sendEmailNotifications", context).equals("false"))) {
      sendNotifications = false;
    }
    return sendNotifications;
  }

  /**
   * This configuration option defines whether nicknames are allowed for MarkedIndividual entries.
   *
   * @return true if nicknames are displayed for MarkedIndividual entries. False otherwise.
   */
  public static boolean allowNicknames(String context) {
    initialize(context);
    boolean canNickname = true;
    if ((getProperty("allowNicknames",context) != null) && (getProperty("allowNicknames",context).equals("false"))) {
      canNickname = false;
    }
    return canNickname;
  }

  /**
   * This configuration option defines whether the spot pattern recognition software embedded in the framework is used for the species under study.
   *
   * @return true if this catalog is for a species for which the spot pattern recognition software component can be used. False otherwise.
   */
  public static boolean useSpotPatternRecognition(String context) {
    initialize(context);
    boolean useSpotPatternRecognition = true;
    if ((getProperty("useSpotPatternRecognition",context) != null) && (getProperty("useSpotPatternRecognition",context).equals("false"))) {
      useSpotPatternRecognition = false;
    }
    return useSpotPatternRecognition;
  }

  /**
   * This configuration option defines whether users can edit this catalog. Some studies may wish to use the framework only for data display.
   *
   * @return true if edits are allows. False otherwise.
   */
  public static boolean isCatalogEditable(String context) {
    initialize(context);
    boolean isCatalogEditable = true;
    if ((getProperty("isCatalogEditable", context) != null) && (getProperty("isCatalogEditable", context).equals("false"))) {
      isCatalogEditable = false;
    }
    return isCatalogEditable;
  }

  /**
   * This configuration option defines whether users can edit this catalog. Some studies may wish to use the framework only for data display.
   *
   * @return true if EXIF data should be shown. False otherwise.
   */
  public static boolean showEXIFData(String context) {
    initialize(context);
    boolean showEXIF = true;
    if ((getProperty("showEXIF",context) != null) && (getProperty("showEXIF", context).equals("false"))) {
      showEXIF = false;
    }
    return showEXIF;
  }

  /**
   * This configuration option defines whether a pre-installed TapirLink provider will be used in conjunction with this database to expose mark-recapture data to biodiversity frameworks, such as the GBIF.
   *
   * @return true if a TapirLink provider is used with the framework. False otherwise.
   */
  public static boolean useTapirLinkURL(String context) {
    initialize(context);
    boolean useTapirLink = true;
    if ((getProperty("tapirLinkURL",context) != null) && (getProperty("tapirLinkURL",context).equals("false"))) {
      useTapirLink = false;
    }
    return useTapirLink;
  }
  
  public static boolean showMeasurements(String context) {
    return showCategory("showMeasurements",context);
  }
  
  public static boolean showMetalTags(String context) {
    return showCategory("showMetalTags",context);
  }
  
  public static boolean showAcousticTag(String context) {
    return showCategory("showAcousticTag",context);
  }
  
  public static boolean showSatelliteTag(String context) {
    return showCategory("showSatelliteTag",context);
  }
  
  public static boolean showReleaseDate(String context) {
    return showCategory("showReleaseDate",context);
  }

  public static String appendEmailRemoveHashString(HttpServletRequest request, String
                                                   originalString, String emailAddress, String context) {
    initialize(context);
    if (getProperty("removeEmailString",context) != null) {
      originalString=originalString.replaceAll("REMOVEME",("\n\n" + getProperty("removeEmailString",context) + "\nhttp://" + getURLLocation(request) + "/removeEmailAddress.jsp?hashedEmail=" + Encounter.getHashOfEmailString(emailAddress)));
    }
    return originalString;
  }

  public static Map<String, String> getIndexedValuesMap(String baseKey, String context) {
    Map<String, String> map = new TreeMap<>();
    boolean hasMore = true;
    int index = 0;
    while (hasMore) {
      String key = baseKey + index++;
      String value = CommonConfiguration.getProperty(key, context);
      if (value != null) {
        value = value.trim();
        if (value.length() > 0) {
          map.put(key, value.trim());
        }
      }
      else {
        hasMore = false;
      }
    }
    return map;
  }

  public static List<String> getIndexedPropertyValues(String baseKey, String context) {
    List<String> list = new ArrayList<String>();
    boolean hasMore = true;
    int index = 0;
    while (hasMore) {
      String key = baseKey + index++;
      String value = CommonConfiguration.getProperty(key, context);
      if (value != null) {
        value = value.trim();
        if (value.length() > 0) {
          list.add(value.trim());
        }
      }
      else {
        hasMore = false;
      }
    }
    return list;
  }
  
  public static Integer getIndexNumberForValue(String baseKey, String checkValue, String context){
    System.out.println("getIndexNumberForValue started for baseKey "+baseKey+" and checkValue "+checkValue);
    boolean hasMore = true;
    int index = 0;
    while (hasMore) {
      String key = baseKey + index;
      String value = CommonConfiguration.getProperty(key, context);
      System.out.println("     key "+key+" and value "+value);
      if (value != null) {
        value = value.trim();
        System.out.println("CommonConfiguration: "+value);
        if(value.equals(checkValue)){return (new Integer(index));}
      }
      else {
        hasMore = false;
      }
      index++;
    }
    return null;
  }
  
  
  private static boolean showCategory(final String category, String context) {
    String showMeasurements = getProperty(category,context);
    return !Boolean.FALSE.toString().equals(showMeasurements);
  }

  
  
  public static String getDataDirectoryName(String context) {
    initialize(context);
    String dataDirectoryName="shepherd_data_dir";
    
    //new context code here
    
    //if(props.getProperty("dataDirectoryName")!=null){return props.getProperty("dataDirectoryName").trim();}
    
    if((ContextConfiguration.getDataDirForContext(context)!=null)&&(!ContextConfiguration.getDataDirForContext(context).trim().equals(""))){dataDirectoryName=ContextConfiguration.getDataDirForContext(context);}
    
    return dataDirectoryName;
  }
  
  /**
   * This configuration option defines whether information about User objects associated with Encounters and MarkedIndividuals will be displayed to web site viewers.
   *
   * @return true if edits are allows. False otherwise.
   */
  public static boolean showUsersToPublic(String context) {
    initialize(context);
    boolean showUsersToPublic = true;
    if ((getProperty("showUsersToPublic",context) != null) && (getProperty("showUsersToPublic",context).equals("false"))) {
      showUsersToPublic = false;
    }
    return showUsersToPublic;
  }

  /**
   * Gets the directory for holding website data ('shepherd_data_dir').
   * @param sc ServletContext as reference for finding directory
   * @return The data directory used for web application storage.
   * @throws FileNotFoundException if folder not found (or unable to create)
   */
  public static File getDataDirectory(ServletContext sc, String context) throws FileNotFoundException {
    String webappRoot = sc.getRealPath("/");
    File dataDir = new File(webappRoot).getParentFile();
    File f = new File(dataDir, getDataDirectoryName(context));
    if (!f.exists() && !f.mkdir())
      throw new FileNotFoundException("Unable to find/create folder: " + f.getAbsolutePath());
    return f;
  }

  /**
   * Gets the directory for holding user-specific data folders (i.e. parent
   * folder of each user-specific folder).
   * @param sc ServletContext as reference for finding directory
   * @return The user-specific data directory used for web application storage.
   * @throws FileNotFoundException if folder not found (or unable to create)
   */
  public static File getUsersDataDirectory(ServletContext sc, String context) throws FileNotFoundException {
    File f = new File(getDataDirectory(sc, context), "users");
    if (!f.exists() && !f.mkdir())
      throw new FileNotFoundException("Unable to find/create folder: " + f.getAbsolutePath());
    return f;
  }

  /**
   * Gets the directory for holding user-specific data (e.g. profile photo).
   * @param sc ServletContext as reference for finding directory
   * @param username username for which to locate directory
   * @return The user-specific data directory used for web application storage.
   * @throws FileNotFoundException if folder not found (or unable to create)
   */
  public static File getDataDirectoryForUser(ServletContext sc, String username, String context) throws FileNotFoundException {
    if (username == null)
      throw new NullPointerException();
    if ("".equals(username.trim()))
      throw new IllegalArgumentException();
    File f = new File(getUsersDataDirectory(sc, context), username);
    if (!f.exists() && !f.mkdir())
      throw new FileNotFoundException("Unable to find/create folder: " + f.getAbsolutePath());
    return f;
  }


  public static boolean isIntegratedWithWildMe(String context){
    
    initialize(context);
    boolean integrated = true;
    if ((getProperty("isIntegratedWithWildMe",context) != null) && (getProperty("isIntegratedWithWildMe",context).equals("false"))) {
      integrated = false;
    }
    return integrated;
  }


  // This can/should be ever-expanded with different conditions;
  // This function is called to determine if StartupWildbook.initializeWildbook() should be called
  public static boolean isWildbookInitialized(Shepherd myShepherd) {
    List<User> users = myShepherd.getAllUsers();
    if (users.size() == 0) return false;

    return true;
  }


}
