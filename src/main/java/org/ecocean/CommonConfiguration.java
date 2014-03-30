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
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;

public class CommonConfiguration {
  
  private static final String COMMON_CONFIGURATION_PROPERTIES = "commonConfiguration.properties";
  
  //class setup
  private static Properties props = new Properties();
  
  private static volatile int propsSize = 0;


  private static void initialize() {
    //set up the file input stream
    if (propsSize == 0) {
      loadProps();
    }
  }

  public static synchronized boolean refresh() {
      props.clear();
      propsSize = 0;
      return loadProps();
  }
  
  
  public static synchronized boolean reloadProps() {
      InputStream resourceAsStream = null;
      try {
        resourceAsStream = CommonConfiguration.class.getResourceAsStream("/bundles/" + COMMON_CONFIGURATION_PROPERTIES);
        props.load(resourceAsStream);
      } catch (IOException ioe) {
        ioe.printStackTrace();
        return false;
      }
      finally {
        if (resourceAsStream != null) {
          try {
            resourceAsStream.close();
          } catch (Exception e) {
            e.printStackTrace();
          }
        }
      }
      String shepherdDataDir="shepherd_data_dir";
      if((props.getProperty("dataDirectoryName")!=null)&&(!props.getProperty("dataDirectoryName").trim().equals(""))){shepherdDataDir=props.getProperty("dataDirectoryName");}
      loadOverrideProps(shepherdDataDir);
      propsSize = props.size();

    return true;
  }
  
  private static synchronized boolean loadProps() {
    if (propsSize == 0) {
      return reloadProps();
    }
    return true;
  }
  
  private static void loadOverrideProps(String shepherdDataDir) {
    File configDir = new File("webapps/"+shepherdDataDir+"/WEB-INF/classes/bundles");
    
    //sometimes this ends up being the "bin" directory of the J2EE container
    //we need to fix that
    if((configDir.getAbsolutePath().contains("/bin/"))||(configDir.getAbsolutePath().contains("\\bin\\"))){
      String fixedPath=configDir.getAbsolutePath().replaceAll("/bin", "").replaceAll("\\\\bin", "");
      configDir=new File(fixedPath);
      System.out.println("Fixing the bin issue in CommonConfiguration.");
      System.out.println("The fix absolute path is: "+configDir.getAbsolutePath());
    }
    
    if(!configDir.exists()){configDir.mkdirs();}
    File configFile = new File(configDir, COMMON_CONFIGURATION_PROPERTIES);
    if (configFile.exists()) {
      System.out.println("Overriding default properties with " + configFile.getAbsolutePath());
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
      System.out.println("No properties override file found at " + configFile.getAbsolutePath());
    }
  }

  //start getter methods
  public static String getURLLocation(HttpServletRequest request) {
    return request.getServerName() + ":" + request.getServerPort() + request.getContextPath();
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

  public static String getMailHost() {
    initialize();
    return props.getProperty("mailHost").trim();
  }

/**
  public static String getImageDirectory() {
    initialize();
    return props.getProperty("imageLocation").trim();
  }
*/
  /**
  public static String getMarkedIndividualDirectory() {
    initialize();
    return props.getProperty("markedIndividualDirectoryLocation").trim();
  }
  */
/*
  public static String getAdoptionDirectory() {
    initialize();
    return props.getProperty("adoptionLocation").trim();
  }
*/
  public static String getWikiLocation() {
    initialize();
    if(props.getProperty("wikiLocation")!=null){return props.getProperty("wikiLocation").trim();}
    return null;
  }

  public static String getDBLocation() {
    initialize();
    return props.getProperty("dbLocation").trim();
  }

  public static String getAutoEmailAddress() {
    initialize();
    return props.getProperty("autoEmailAddress").trim();
  }

  public static String getNewSubmissionEmail() {
    initialize();
    return props.getProperty("newSubmissionEmail").trim();
  }

  public static String getR() {
    initialize();
    return props.getProperty("R").trim();
  }

  public static String getEpsilon() {
    initialize();
    return props.getProperty("epsilon").trim();
  }

  public static String getSizelim() {
    initialize();
    return props.getProperty("sizelim").trim();
  }

  public static String getMaxTriangleRotation() {
    initialize();
    return props.getProperty("maxTriangleRotation").trim();
  }

  public static String getC() {
    initialize();
    return props.getProperty("C").trim();
  }

  public static String getHTMLDescription() {
    initialize();
    return props.getProperty("htmlDescription").trim();
  }
  
  public static int getMaxMediaSizeInMegabytes(){
    int maxSize=10;
    
    try{
      String sMaxSize=getProperty("maxMediaSize");
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

  public static String getHTMLKeywords() {
    initialize();
    return props.getProperty("htmlKeywords").trim();
  }

  public static String getHTMLTitle() {
    initialize();
    return props.getProperty("htmlTitle").trim();
  }

  public static String getVersion() {
      initialize();
      return props.getProperty("application.version").trim();
  }

  public static String getCSSURLLocation(HttpServletRequest request) {
    initialize();

    return (request.getScheme() + "://" +
      getURLLocation(request) + "/" +
      props.getProperty("cssURLLocation"))
      .trim();
  }

  public static String getHTMLAuthor() {
    initialize();
    return props.getProperty("htmlAuthor").trim();
  }

  public static String getHTMLShortcutIcon() {
    initialize();
    return props.getProperty("htmlShortcutIcon").trim();
  }

  public static String getGlobalUniqueIdentifierPrefix() {
    initialize();
    return props.getProperty("GlobalUniqueIdentifierPrefix");
  }

  public static String getURLToMastheadGraphic() {
    initialize();
    return props.getProperty("urlToMastheadGraphic");
  }

  public static String getTapirLinkURL() {
    initialize();
    return props.getProperty("tapirLinkURL");
  }

  public static String getIPTURL() {
    initialize();
    return props.getProperty("iptURL");
  }

  public static String getURLToFooterGraphic() {
    initialize();
    return props.getProperty("urlToFooterGraphic");
  }

  public static String getGoogleMapsKey() {
    initialize();
    return props.getProperty("googleMapsKey");
  }

  public static String getGoogleSearchKey() {
    initialize();
    return props.getProperty("googleSearchKey");
  }

  public static String getProperty(String name) {
    initialize();
    return props.getProperty(name);
  }
  
  public static Enumeration<?> getPropertyNames() {
    initialize();
    return props.propertyNames();
  }

  public static ArrayList<String> getSequentialPropertyValues(String propertyPrefix){
    initialize();
    ArrayList<String> returnThese=new ArrayList<String>();
    int iter=0;
    while(props.getProperty(propertyPrefix+iter)!=null){
      returnThese.add(props.getProperty(propertyPrefix+iter));
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
  public static boolean showProperty(String thisString) {
    if((getProperty(thisString)!=null)&&(getProperty(thisString).equals("false"))){return false;}
    return true;
  }

  /**
   * This configuration option defines whether adoptions of MarkedIndividual or encounter objects are allowed. Generally adoptions are used as a fundraising or public awareness tool.
   *
   * @return true if adoption functionality should be displayed. False if adoptions are not supported in this catalog.
   */
  public static boolean allowAdoptions() {
    initialize();
    boolean canAdopt = true;
    if ((props.getProperty("allowAdoptions") != null) && (props.getProperty("allowAdoptions").equals("false"))) {
      canAdopt = false;
    }
    return canAdopt;
  }

  /**
   * This configuration option defines whether batch upload of {@link MarkedIndividual} or {@link Encounter} objects are allowed.
   *
   * @return true if batch upload functionality should be displayed. False if batch upload are not supported in this catalog.
   */
  public static boolean allowBatchUpload() {
    initialize();
    return parseBoolean(props.getProperty("allowBatchUpload"), false);
  }

  /**
   * Helper method to parse boolean from string.
   * @param s string to parse
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
  public static String getBatchUploadPlugin() {
    initialize();
    if (props.getProperty("batchUploadPlugin") != null) {
      return props.getProperty("batchUploadPlugin").trim();
    }
    return null;
  }

  /**
   * This configuration option defines whether batch upload of {@link MarkedIndividual} or {@link Encounter} objects are allowed.
   *
   * @return true if batch upload functionality should be displayed. False if batch upload are not supported in this catalog.
   */
  public static int getBatchUploadProgressRefresh() {
    initialize();
    int def = 10;
    String prop = props.getProperty("batchUploadProgressRefresh");
    if (prop == null || "".equals(prop.trim())) {
      return def;
    }
    try {
      return Integer.parseInt(prop.trim());
    } catch (NumberFormatException ex) {
      return def;
    }
  }

  public static boolean sendEmailNotifications() {
    initialize();
    boolean sendNotifications = true;
    if ((props.getProperty("sendEmailNotifications") != null) && (props.getProperty("sendEmailNotifications").equals("false"))) {
      sendNotifications = false;
    }
    return sendNotifications;
  }

  /**
   * This configuration option defines whether nicknames are allowed for MarkedIndividual entries.
   *
   * @return true if nicknames are displayed for MarkedIndividual entries. False otherwise.
   */
  public static boolean allowNicknames() {
    initialize();
    boolean canNickname = true;
    if ((props.getProperty("allowNicknames") != null) && (props.getProperty("allowNicknames").equals("false"))) {
      canNickname = false;
    }
    return canNickname;
  }

  /**
   * This configuration option defines whether the spot pattern recognition software embedded in the framework is used for the species under study.
   *
   * @return true if this catalog is for a species for which the spot pattern recognition software component can be used. False otherwise.
   */
  public static boolean useSpotPatternRecognition() {
    initialize();
    boolean useSpotPatternRecognition = true;
    if ((props.getProperty("useSpotPatternRecognition") != null) && (props.getProperty("useSpotPatternRecognition").equals("false"))) {
      useSpotPatternRecognition = false;
    }
    return useSpotPatternRecognition;
  }

  /**
   * This configuration option defines whether users can edit this catalog. Some studies may wish to use the framework only for data display.
   *
   * @return true if edits are allows. False otherwise.
   */
  public static boolean isCatalogEditable() {
    initialize();
    boolean isCatalogEditable = true;
    if ((props.getProperty("isCatalogEditable") != null) && (props.getProperty("isCatalogEditable").equals("false"))) {
      isCatalogEditable = false;
    }
    return isCatalogEditable;
  }

  /**
   * This configuration option defines whether users can edit this catalog. Some studies may wish to use the framework only for data display.
   *
   * @return true if EXIF data should be shown. False otherwise.
   */
  public static boolean showEXIFData() {
    initialize();
    boolean showEXIF = true;
    if ((props.getProperty("showEXIF") != null) && (props.getProperty("showEXIF").equals("false"))) {
      showEXIF = false;
    }
    return showEXIF;
  }

  /**
   * This configuration option defines whether a pre-installed TapirLink provider will be used in conjunction with this database to expose mark-recapture data to biodiversity frameworks, such as the GBIF.
   *
   * @return true if a TapirLink provider is used with the framework. False otherwise.
   */
  public static boolean useTapirLinkURL() {
    initialize();
    boolean useTapirLink = true;
    if ((props.getProperty("tapirLinkURL") != null) && (props.getProperty("tapirLinkURL").equals("false"))) {
      useTapirLink = false;
    }
    return useTapirLink;
  }
  
  public static boolean showMeasurements() {
    return showCategory("showMeasurements");
  }
  
  public static boolean showMetalTags() {
    return showCategory("showMetalTags");
  }
  
  public static boolean showAcousticTag() {
    return showCategory("showAcousticTag");
  }
  
  public static boolean showSatelliteTag() {
    return showCategory("showSatelliteTag");
  }
  
  public static boolean showReleaseDate() {
    return showCategory("showReleaseDate");
  }

  public static String appendEmailRemoveHashString(HttpServletRequest request, String
                                                   originalString, String emailAddress) {
    initialize();
    if (props.getProperty("removeEmailString") != null) {
      originalString += "\n\n" + props.getProperty("removeEmailString") + "\nhttp://" + getURLLocation(request) + "/removeEmailAddress.jsp?hashedEmail=" + Encounter.getHashOfEmailString(emailAddress);
    }
    return originalString;
  }
  
  public static List<String> getIndexedValues(String baseKey) {
    List<String> list = new ArrayList<String>();
    boolean hasMore = true;
    int index = 0;
    while (hasMore) {
      String key = baseKey + index++;
      String value = CommonConfiguration.getProperty(key);
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
  
  public static Integer getIndexNumberForValue(String baseKey, String checkValue){
    System.out.println("getIndexNumberForValue started for baseKey "+baseKey+" and checkValue "+checkValue);
    boolean hasMore = true;
    int index = 0;
    while (hasMore) {
      String key = baseKey + index;
      String value = CommonConfiguration.getProperty(key);
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
  
  
  private static boolean showCategory(final String category) {
    String showMeasurements = getProperty(category);
    return !Boolean.FALSE.toString().equals(showMeasurements);
  }
  
  public static String getDataDirectoryName() {
    initialize();
    String dataDirectoryName="shepherd_data_dir";
    if(props.getProperty("dataDirectoryName")!=null){return props.getProperty("dataDirectoryName").trim();}
    return dataDirectoryName;
  }

  /**
   * Gets the directory for holding website data ('shepherd_data_dir').
   * @param sc ServletContext as reference for finding directory
   * @return The data directory used for web application storage.
   * @throws FileNotFoundException if folder not found (or unable to create)
   */
  public static File getDataDirectory(ServletContext sc) throws FileNotFoundException {
    String webappRoot = sc.getRealPath("/");
    File dataDir = new File(webappRoot).getParentFile();
    File f = new File(dataDir, getDataDirectoryName());
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
  public static File getUsersDataDirectory(ServletContext sc) throws FileNotFoundException {
    File f = new File(getDataDirectory(sc), "users");
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
  public static File getDataDirectoryForUser(ServletContext sc, String username) throws FileNotFoundException {
    if (username == null)
      throw new NullPointerException();
    if ("".equals(username.trim()))
      throw new IllegalArgumentException();
    File f = new File(getUsersDataDirectory(sc), username);
    if (!f.exists() && !f.mkdir())
      throw new FileNotFoundException("Unable to find/create folder: " + f.getAbsolutePath());
    return f;
  }

  /**
   * This configuration option defines whether information about User objects associated with Encounters and MarkedIndividuals will be displayed to web site viewers.
   *
   * @return true if edits are allows. False otherwise.
   */
  public static boolean showUsersToPublic() {
    initialize();
    boolean showUsersToPublic = true;
    if ((props.getProperty("showUsersToPublic") != null) && (props.getProperty("showUsersToPublic").equals("false"))) {
      showUsersToPublic = false;
    }
    return showUsersToPublic;
  }
  
  
  public static boolean isIntegratedWithWildMe(){
    
    initialize();
    boolean integrated = true;
    if ((props.getProperty("isIntegratedWithWildMe") != null) && (props.getProperty("isIntegratedWithWildMe").equals("false"))) {
      integrated = false;
    }
    return integrated;
  }
  
  
}
