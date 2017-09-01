/*
* Wildbook - A Mark-Recapture Framework
* Copyright (C) 2011-2014 Jason Holmberg
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

package org.ecocean.servlet;

import com.sun.syndication.feed.synd.*;
import com.sun.syndication.io.SyndFeedInput;
import com.sun.syndication.io.SyndFeedOutput;
import com.sun.syndication.io.XmlReader;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.SAXReader;
import org.dom4j.io.XMLWriter;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.json.JSONObject;

import javax.jdo.Query;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletContext;
//import javax.servlet.http.HttpSession;
import org.json.JSONObject;


import java.io.*;
import java.net.URL;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.ThreadPoolExecutor;
import java.sql.*;
import java.util.Collection;
import java.util.Map;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.util.Calendar;

import org.ecocean.*;
import org.apache.shiro.crypto.hash.*;
import org.apache.shiro.util.*;
import org.apache.shiro.crypto.*;

import java.util.Properties;

import javax.servlet.http.Cookie;

import org.apache.commons.lang.StringEscapeUtils;
//parse Date with Stanford NLP
import java.util.*;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.*;
import edu.stanford.nlp.time.*;
import edu.stanford.nlp.time.SUTime.Temporal;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations.NamedEntityTagAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;

import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;

import twitter4j.Status;



//ATOM feed

public class ServletUtilities {

  public static String getHeader(HttpServletRequest request) {
    try {
      FileReader fileReader = new FileReader(findResourceOnFileSystem("servletResponseTemplate.htm"));
      BufferedReader buffread = new BufferedReader(fileReader);
      String templateFile = "", line;
      StringBuffer SBreader = new StringBuffer();
      while ((line = buffread.readLine()) != null) {
        SBreader.append(line).append("\n");
      }
      fileReader.close();
      buffread.close();
      templateFile = SBreader.toString();

      String context=getContext(request);

      //process the CSS string
      templateFile = templateFile.replaceAll("CSSURL", CommonConfiguration.getCSSURLLocation(request,context));

      //set the top header graphic
      templateFile = templateFile.replaceAll("TOPGRAPHIC", CommonConfiguration.getURLToMastheadGraphic(context));

      int end_header = templateFile.indexOf("INSERT_HERE");
      return (templateFile.substring(0, end_header));
    }
    catch (Exception e) {
      //out.println("I couldn't find the template file to read from.");
      e.printStackTrace();
      String error = "<html><body><p>An error occurred while attempting to read from the template file servletResponseTemplate.htm. This probably will not affect the success of the operation you were trying to perform.";
      return error;
    }

  }

  public static String getFooter(String context) {
    try {
      FileReader fileReader = new FileReader(findResourceOnFileSystem("servletResponseTemplate.htm"));
      BufferedReader buffread = new BufferedReader(fileReader);
      String templateFile = "", line;
      StringBuffer SBreader = new StringBuffer();
      while ((line = buffread.readLine()) != null) {
        SBreader.append(line).append("\n");
      }
      fileReader.close();
      buffread.close();
      templateFile = SBreader.toString();
      templateFile = templateFile.replaceAll("BOTTOMGRAPHIC", CommonConfiguration.getURLToFooterGraphic(context));

      int end_header = templateFile.indexOf("INSERT_HERE");
      return (templateFile.substring(end_header + 11));
    } catch (Exception e) {
      //out.println("I couldn't find the template file to read from.");
      e.printStackTrace();
      String error = "An error occurred while attempting to read from an HTML template file. This probably will not affect the success of the operation you were trying to perform.</p></body></html>";
      return error;
    }


  }

  /**
  * Inform (via email) researchers who've logged an interest in encounter.
  * @param request servlet request
  * @param encounterNumber ID of encounter to inform about
  * @param message message to include in email notification
  * @param context webapp context
  */
  public static void informInterestedParties(HttpServletRequest request, String encounterNumber, String message, String context) {
    Shepherd shep = new Shepherd(context);
    shep.setAction("ServletUtilities.class.informInterestedParties");
    shep.beginDBTransaction();
    if (shep.isEncounter(encounterNumber)) {
      Encounter enc = shep.getEncounter(encounterNumber);
      if(enc.getInterestedResearchers() != null){
        Collection<String> notifyMe = enc.getInterestedResearchers();
        if (!notifyMe.isEmpty()) {
          ThreadPoolExecutor es = MailThreadExecutorService.getExecutorService();
          for (String mailTo : notifyMe) {
            Map<String, String> tagMap = NotificationMailer.createBasicTagMap(request, enc);
            tagMap.put(NotificationMailer.EMAIL_NOTRACK, "number=" + encounterNumber);
            tagMap.put(NotificationMailer.EMAIL_HASH_TAG, Encounter.getHashOfEmailString(mailTo));
            tagMap.put(NotificationMailer.STANDARD_CONTENT_TAG, message == null ? "" : message);
            //            String langCode = ServletUtilities.getLanguageCode(request);
            NotificationMailer mailer = new NotificationMailer(context, null, mailTo, "encounterDataUpdate", tagMap);
            es.execute(mailer);
          }
          es.shutdown();
        }
      }
    }
    shep.rollbackDBTransaction();
    shep.closeDBTransaction();
  }

  /**
  * Inform (via email) researchers who've logged an interest in individual.
  * @param request servlet request
  * @param individualID ID of individual to inform about
  * @param message message to include in email notification
  * @param context webapp context
  */
  public static void informInterestedIndividualParties(HttpServletRequest request, String individualID, String message, String context) {
    Shepherd shep = new Shepherd(context);
    shep.setAction("ServletUtilities.informInterestedIndividualParties.class");
    shep.beginDBTransaction();
    if (shep.isMarkedIndividual(individualID)) {
      MarkedIndividual ind = shep.getMarkedIndividual(individualID);
      if (ind.getInterestedResearchers() != null) {
        Collection<String> notifyMe = ind.getInterestedResearchers();
        if (!notifyMe.isEmpty()) {
          ThreadPoolExecutor es = MailThreadExecutorService.getExecutorService();
          for (String mailTo : notifyMe) {
            Map<String, String> tagMap = NotificationMailer.createBasicTagMap(request, ind);
            tagMap.put(NotificationMailer.EMAIL_NOTRACK, "individual=" + individualID);
            tagMap.put(NotificationMailer.EMAIL_HASH_TAG, Encounter.getHashOfEmailString(mailTo));
            tagMap.put(NotificationMailer.STANDARD_CONTENT_TAG, message == null ? "" : message);
            //            String langCode = ServletUtilities.getLanguageCode(request);
            NotificationMailer mailer = new NotificationMailer(context, null, mailTo, "individualDataUpdate", tagMap);
            es.execute(mailer);
          }
          es.shutdown();
        }
      }
    }
    shep.rollbackDBTransaction();
    shep.closeDBTransaction();
  }


  //Loads a String of text from a specified file.
  //This is generally used to load an email template for automated emailing
  public static String getText(String shepherdDataDir, String fileName, String langCode) {
    String overrideText=loadOverrideText(shepherdDataDir, fileName, langCode);
    if(!overrideText.equals("")){
      return overrideText;
    }
    else{
      try {
        StringBuffer SBreader = new StringBuffer();
        String line;
        FileReader fileReader = new FileReader(findResourceOnFileSystem(fileName));

        BufferedReader buffread = new BufferedReader(fileReader);
        while ((line = buffread.readLine()) != null) {
          SBreader.append(line + "\n");
        }
        line = SBreader.toString();
        fileReader.close();
        buffread.close();
        return line;
      }
      catch (Exception e) {
        e.printStackTrace();
        return "";
      }
    }
  }

  //Logs a new ATOM entry
  public static synchronized void addATOMEntry(String title, String link, String description, File atomFile, String context) {
    try {

      if (atomFile.exists()) {

        //System.out.println("ATOM file found!");
        /** Namespace URI for content:encoded elements */
        String CONTENT_NS = "http://www.w3.org/2005/Atom";

        /** Parses RSS or Atom to instantiate a SyndFeed. */
        SyndFeedInput input = new SyndFeedInput();

        /** Transforms SyndFeed to RSS or Atom XML. */
        SyndFeedOutput output = new SyndFeedOutput();

        // Load the feed, regardless of RSS or Atom type
        SyndFeed feed = input.build(new XmlReader(atomFile));

        // Set the output format of the feed
        feed.setFeedType("atom_1.0");

        List<SyndEntry> items = feed.getEntries();
        int numItems = items.size();
        if (numItems > 9) {
          items.remove(0);
          feed.setEntries(items);
        }

        SyndEntry newItem = new SyndEntryImpl();
        newItem.setTitle(title);
        newItem.setLink(link);
        newItem.setUri(link);
        SyndContent desc = new SyndContentImpl();
        desc.setType("text/html");
        desc.setValue(description);
        newItem.setDescription(desc);
        desc.setType("text/html");
        newItem.setPublishedDate(new java.util.Date());

        List<SyndCategory> categories = new ArrayList<SyndCategory>();
        if(CommonConfiguration.getProperty("htmlTitle",context)!=null){
          SyndCategory category2 = new SyndCategoryImpl();
          category2.setName(CommonConfiguration.getProperty("htmlTitle",context));
          categories.add(category2);
        }
        newItem.setCategories(categories);
        if(CommonConfiguration.getProperty("htmlAuthor",context)!=null){
          newItem.setAuthor(CommonConfiguration.getProperty("htmlAuthor",context));
        }
        items.add(newItem);
        feed.setEntries(items);

        feed.setPublishedDate(new java.util.Date());


        FileWriter writer = new FileWriter(atomFile);
        output.output(feed, writer);
        writer.toString();

      }
    } catch (IOException ioe) {
      System.out.println("ERROR: Could not find the ATOM file.");
      ioe.printStackTrace();
    } catch (Exception e) {
      System.out.println("Unknown exception trying to add an entry to the ATOM file.");
      e.printStackTrace();
    }

  }

  //Logs a new entry in the library RSS file
  public static synchronized void addRSSEntry(String title, String link, String description, File rssFile) {
    //File rssFile=new File("nofile.xml");

    try {
      System.out.println("Looking for RSS file: "+rssFile.getCanonicalPath());
      if (rssFile.exists()) {

        SAXReader reader = new SAXReader();
        Document document = reader.read(rssFile);
        Element root = document.getRootElement();
        Element channel = root.element("channel");
        List items = channel.elements("item");
        int numItems = items.size();
        items = null;
        if (numItems > 9) {
          Element removeThisItem = channel.element("item");
          channel.remove(removeThisItem);
        }

        Element newItem = channel.addElement("item");
        Element newTitle = newItem.addElement("title");
        Element newLink = newItem.addElement("link");
        Element newDescription = newItem.addElement("description");
        newTitle.setText(title);
        newDescription.setText(description);
        newLink.setText(link);

        Element pubDate = channel.element("pubDate");
        pubDate.setText((new java.util.Date()).toString());

        //now save changes
        FileWriter mywriter = new FileWriter(rssFile);
        OutputFormat format = OutputFormat.createPrettyPrint();
        format.setLineSeparator(System.getProperty("line.separator"));
        XMLWriter writer = new XMLWriter(mywriter, format);
        writer.write(document);
        writer.close();

      }
    }
    catch (IOException ioe) {
      System.out.println("ERROR: Could not find the RSS file.");
      ioe.printStackTrace();
    }
    catch (DocumentException de) {
      System.out.println("ERROR: Could not read the RSS file.");
      de.printStackTrace();
    } catch (Exception e) {
      System.out.println("Unknown exception trying to add an entry to the RSS file.");
      e.printStackTrace();
    }
  }

  public static File findResourceOnFileSystem(String resourceName) {
    File resourceFile = null;

    URL resourceURL = ServletUtilities.class.getClassLoader().getResource(resourceName);
    if (resourceURL != null) {
      String resourcePath = resourceURL.getPath();
      if (resourcePath != null) {
        File tmp = new File(resourcePath);
        if (tmp.exists()) {
          resourceFile = tmp;
        }
      }
    }
    return resourceFile;
  }

  public static boolean isUserAuthorizedForEncounter(Encounter enc, HttpServletRequest request) {
    boolean isOwner = false;
    //if (request.isUserInRole("admin")) {
    if (request.getUserPrincipal()!=null) {
      if (request.isUserInRole("admin")) {
        isOwner = true;
      }
      else if (request.isUserInRole(enc.getLocationCode())) {
        isOwner = true;
      }
      else if ((((enc.getSubmitterID() != null) && (request.getRemoteUser() != null) && (enc.getSubmitterID().equals(request.getRemoteUser()))))) {
        isOwner = true;
      }

      //whaleshark.org custom
      if((request.getRemoteUser().equals("rgrampus"))&&(enc.getLocationCode().startsWith("2h"))){isOwner=false;}


    }
    return isOwner;
    //}
    // return isOwner;
  }

  public static boolean isUserAuthorizedForIndividual(MarkedIndividual sharky, HttpServletRequest request) {
    //if (request.isUserInRole("admin")) {
    if (request.getUserPrincipal()!=null) {
      //return true;

      if (request.isUserInRole("admin")) {
        return true;
      }

      Vector encounters = sharky.getEncounters();
      int numEncs = encounters.size();
      for (int y = 0; y < numEncs; y++) {
        Encounter enc = (Encounter) encounters.get(y);
        if (request.isUserInRole(enc.getLocationCode())) {
          return true;
        }
      }
    }
    //}
    return false;
  }

  //occurrence
  public static boolean isUserAuthorizedForOccurrence(Occurrence sharky, HttpServletRequest request) {

    if (request.getUserPrincipal()!=null) {

      if (request.isUserInRole("admin")) {  return true;  }
      ArrayList<Encounter> encounters = sharky.getEncounters();
      int numEncs = encounters.size();
      for (int y = 0; y < numEncs; y++) {
        Encounter enc = (Encounter) encounters.get(y);
        if (request.isUserInRole(enc.getLocationCode())) {
          return true;
        }
      }
    }


    return false;
  }
  //occurrence

  public static Query setRange(Query query, int iterTotal, int highCount, int lowCount) {

    if (iterTotal > 10) {

      //handle the normal situation first
      if ((lowCount > 0) && (lowCount <= highCount)) {
        if (highCount - lowCount > 50) {
          query.setRange((lowCount - 1), (lowCount + 50));
        } else {
          query.setRange(lowCount - 1, highCount);
        }
      } else {
        query.setRange(0, 10);
      }


    } else {
      query.setRange(0, iterTotal);
    }
    return query;

  }


  public static String cleanFileName(String myString){
    return myString.replaceAll("[^a-zA-Z0-9\\.\\-]", "_");
  }

  /*public static String cleanFileName(String aTagFragment) {
  final StringBuffer result = new StringBuffer();

  final StringCharacterIterator iterator = new StringCharacterIterator(aTagFragment);
  char character = iterator.current();
  while (character != CharacterIterator.DONE) {
  if (character == '<') {
  result.append("_");
} else if (character == '>') {
result.append("_");
} else if (character == '\"') {
result.append("_");
} else if (character == '\'') {
result.append("_");
} else if (character == '\\') {
result.append("_");
} else if (character == '&') {
result.append("_");
} else if (character == ' ') {
result.append("_");
} else if (character == '#') {
result.append("_");
} else {
//the char is not a special one
//add it to the result as is
result.append(character);
}
character = iterator.next();
}
return result.toString();
}
*/

public static String preventCrossSiteScriptingAttacks(String description) {
  description = description.replaceAll("<", "&lt;").replaceAll(">", "&gt;");
  description = description.replaceAll("eval\\((.*)\\)", "");
  description = description.replaceAll("[\\\"\\\'][\\s]*((?i)javascript):(.*)[\\\"\\\']", "\"\"");
  description = description.replaceAll("((?i)script)", "");
  description = description.replaceAll("onerror", "");
  //description = description.replaceAll("alert", "");
  description = StringEscapeUtils.escapeHtml(description);
  return description;
}

public static String getDate() {
  DateTime dt = new DateTime();
  DateTimeFormatter fmt = ISODateTimeFormat.date();
  return (fmt.print(dt));
}

public static Connection getConnection() throws SQLException {

  Connection conn = null;
  Properties connectionProps = new Properties();
  connectionProps.put("user", CommonConfiguration.getProperty("datanucleus.ConnectionUserName","context0"));
  connectionProps.put("password", CommonConfiguration.getProperty("datanucleus.ConnectionPassword","context0"));


  conn = DriverManager.getConnection(
  CommonConfiguration.getProperty("datanucleus.ConnectionURL","context0"),
  connectionProps);

  System.out.println("Connected to database for authentication.");
  return conn;
}

public static String hashAndSaltPassword(String clearTextPassword, String salt) {
  return new Sha512Hash(clearTextPassword, salt, 200000).toHex();
}

public static ByteSource getSalt() {
  return new SecureRandomNumberGenerator().nextBytes();
}

public static String getContext(HttpServletRequest request){
  String context="context0";
  if(ContextConfiguration.getDefaultContext()!=null){context=ContextConfiguration.getDefaultContext();}
  Properties contexts=ShepherdProperties.getContextsProperties();
  int numContexts=contexts.size();

  //check the URL for the context attribute
  //this can be used for debugging and takes precedence
  if(request.getParameter("context")!=null){
    //get the available contexts
    //System.out.println("Checking for a context: "+request.getParameter("context"));
    if(contexts.containsKey((request.getParameter("context")+"DataDir"))){
      //System.out.println("Found a request context: "+request.getParameter("context"));
      return request.getParameter("context");
    }
  }


  //the request cookie is the next thing we check. this should be the primary means of figuring context out
  Cookie[] cookies = request.getCookies();
  if(cookies!=null){
    for(Cookie cookie : cookies){
      if("wildbookContext".equals(cookie.getName())){
        return cookie.getValue();
      }
    }
  }

  //finally, we will check the URL vs values defined in context.properties to see if we can set the right context
  String currentURL=request.getServerName();
  for(int q=0;q<numContexts;q++){
    String thisContext="context"+q;
    List<String> domainNames=ContextConfiguration.getContextDomainNames(thisContext);
    int numDomainNames=domainNames.size();
    for(int p=0;p<numDomainNames;p++){

      if(currentURL.indexOf(domainNames.get(p))!=-1){return thisContext;}

    }


  }

  return context;
}


public static String getLanguageCode(HttpServletRequest request){
  String context=ServletUtilities.getContext(request);

  //worst case scenario default to English
  String langCode="en";

  //try to detect a default if defined
  if(CommonConfiguration.getProperty("defaultLanguage", context)!=null){
    langCode=CommonConfiguration.getProperty("defaultLanguage", context);
  }


  List<String> supportedLanguages=new ArrayList<String>();
  if(CommonConfiguration.getIndexedPropertyValues("language", context)!=null){
    supportedLanguages=CommonConfiguration.getIndexedPropertyValues("language", context);
  }

  //if specified directly, always accept the override
  if(request.getParameter("langCode")!=null){
    if(supportedLanguages.contains(request.getParameter("langCode"))){return request.getParameter("langCode");}
  }


  //the request cookie is the next thing we check. this should be the primary means of figuring langCode out
  Cookie[] cookies = request.getCookies();
  if(cookies!=null){
    for(Cookie cookie : cookies){
      if("wildbookLangCode".equals(cookie.getName())){
        if(supportedLanguages.contains(cookie.getValue())){return cookie.getValue();}
      }
    }
  }

  //finally, we will check the URL vs values defined in context.properties to see if we can set the right context
  //TBD - future - detect browser supported language codes and locale from the HTTPServletRequest object

  return langCode;
}


public static String dataDir(String context, String rootWebappPath) {
  File webappsDir = new File(rootWebappPath).getParentFile();
  File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));
  if(!shepherdDataDir.exists()){shepherdDataDir.mkdirs();}
  return shepherdDataDir.getAbsolutePath();
}

//like above, but can pass a subdir to append
public static String dataDir(String context, String rootWebappPath, String subdir) {
  return dataDir(context, rootWebappPath) + File.separator + subdir;
}

/*
//like above, but only need request passed
public static String dataDir(HttpServletRequest request) {
String context = "context0";
context = ServletUtilities.getContext(request);
//String rootWebappPath = request.getServletContext().getRealPath("/");  // only in 3.0??
//String rootWebappPath = request.getSession(true).getServlet().getServletContext().getRealPath("/");
ServletContext s = request.getServletContext();
String rootWebappPath = "xxxxxx";
return dataDir(context, rootWebappPath);
}
*/


private static String loadOverrideText(String shepherdDataDir, String fileName, String langCode) {
  //System.out.println("Starting loadOverrideProps");
  StringBuffer myText=new StringBuffer("");
  //Properties myProps=new Properties();
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


      BufferedReader reader = new BufferedReader(new InputStreamReader(fileInputStream));
      StringBuilder out = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) {
        myText.append(line);
      }




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
  return myText.toString();
}

public static String handleNullString(Object obj){
  if(obj==null){return "";}
  return obj.toString();
}


public static JSONObject jsonFromHttpServletRequest(HttpServletRequest request) throws IOException {
  StringBuilder sb = new StringBuilder();
  BufferedReader reader = request.getReader();
  try {
    String line;
    while ((line = reader.readLine()) != null) {
      sb.append(line).append('\n');
    }
  } finally {
    reader.close();
  }
  //ParseException
  return new JSONObject(sb.toString());
}


public static String getParameterOrAttribute(String name, HttpServletRequest request) {
  String result = request.getParameter(name);
  if (name != null) {
    result = (String) request.getAttribute(name);
  }
  return result;
}

//handy "let anyone do anything (?) cors stuff
public static void doOptions(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
  response.setHeader("Access-Control-Allow-Origin", "*");
  response.setHeader("Access-Control-Allow-Methods", "GET, POST");
  if (request.getHeader("Access-Control-Request-Headers") != null) response.setHeader("Access-Control-Allow-Headers", request.getHeader("Access-Control-Request-Headers"));
}


/* see webapps/captchaExample.jsp for implementation */

//note: this only handles single-widget (per page) ... if we need multiple, will have to extend things here
public static String captchaWidget(HttpServletRequest request) {
  return captchaWidget(request, null);
}
public static String captchaWidget(HttpServletRequest request, String params) {
  String context = getContext(request);
  Properties recaptchaProps = ShepherdProperties.getProperties("recaptcha.properties", "", context);
  if (recaptchaProps == null) return "<div class=\"error captcha-error captcha-missing-properties\">Unable to get captcha settings.</div>";
  String siteKey = recaptchaProps.getProperty("siteKey");
  String secretKey = recaptchaProps.getProperty("secretKey");  //really dont need this here
  if ((siteKey == null) || (secretKey == null)) return "<div class=\"error captcha-error captcha-missing-key\">Unable to get captcha key settings.</div>";
  return "<script>function recaptchaCompleted() { return (grecaptcha && grecaptcha.getResponse(0)); }</script>\n" +
  "<script src='https://www.google.com/recaptcha/api.js" + ((params == null) ? "" : "?" + params) + "' async defer></script>\n" +
  "<div class=\"g-recaptcha\" data-sitekey=\"" + siteKey + "\"></div>";
}

//  https://developers.google.com/recaptcha/docs/verify
public static boolean captchaIsValid(HttpServletRequest request) {
  return captchaIsValid(getContext(request), request.getParameter("g-recaptcha-response"), request.getRemoteAddr());
}

public static boolean captchaIsValid(String context, String uresp, String remoteIP) {
  if (context == null) context = "context0";
  Properties recaptchaProps = ShepherdProperties.getProperties("recaptcha.properties", "", context);
  if (recaptchaProps == null) {
    System.out.println("WARNING: no recaptcha.properties for captchaIsValid(); failing");
    return false;
  }
  String siteKey = recaptchaProps.getProperty("siteKey");  //really dont need this here
  String secretKey = recaptchaProps.getProperty("secretKey");
  if ((siteKey == null) || (secretKey == null)) {
    System.out.println("WARNING: could not determine keys for captchaIsValid(); failing");
    return false;
  }
  if (uresp == null) {
    System.out.println("WARNING: g-recaptcha-response is null in captchaIsValid(); failing");
    return false;
  }
  JSONObject cdata = new JSONObject();
  cdata.put("secret", secretKey);
  cdata.put("remoteip", remoteIP);  //i guess this is technically optional (so we dont care if null?)
  cdata.put("response", uresp);
  JSONObject gresp = null;
  try {
    gresp = RestClient.post(new URL("https://www.google.com/recaptcha/api/siteverify"), cdata);
  } catch (Exception ex) {
    System.out.println("WARNING: exception calling captcha api in captchaIsValid(); failing: " + ex.toString());
    return false;
  }
  if (gresp == null) {  //would this ever happen?
    System.out.println("WARNING: null return from captcha api in captchaIsValid(); failing");
    return false;
  }
  System.out.println("INFO: captchaIsValid() api call returned: " + gresp.toString());
  return gresp.optBoolean("success", false);
}

public static ArrayList<String> nlpLocationParse(String text) throws RuntimeException {
  ArrayList<String> locations = new ArrayList<>();
  Properties props = new Properties();
  props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner"); //TODO adding truecase before ner doesn't seem to be making a difference here. If this doesn't change with some tweaking, you may want to remove the stanford-corenlp class:model-english dependency. Update: the stanford-corenlp class:model-english dependency seems essential even when truecase is excluded. Very weird.
  StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
  edu.stanford.nlp.pipeline.Annotation document = new edu.stanford.nlp.pipeline.Annotation(text);
  pipeline.annotate(document);
  List<CoreMap> sentences = document.get(SentencesAnnotation.class);
  for(CoreMap sentence: sentences) {
    for (CoreLabel token: sentence.get(TokensAnnotation.class)) {
      String ne = token.get(NamedEntityTagAnnotation.class);
      if(ne.equals("LOCATION")){
        String word = token.get(TextAnnotation.class);
        System.out.println("Location captured: " + word);
        locations.add(word);
      }
    }
  }

  if (locations.size() > 0){
    return locations;
  } else{
    throw new RuntimeException("no locations found");
  }

}


//overloaded version to deal with tweets
public static String nlpDateParse(String text, Status tweet) throws Exception{
  System.out.println("Entering nlpDateParse twitter version with text " + text);
  //create my pipeline with the help of the annotators I added.
  Properties props = new Properties();
  AnnotationPipeline pipeline = new AnnotationPipeline();
  pipeline.addAnnotator(new TokenizerAnnotator(false));
  pipeline.addAnnotator(new WordsToSentencesAnnotator(false));
  pipeline.addAnnotator(new POSTaggerAnnotator(false));
  pipeline.addAnnotator(new TimeAnnotator("sutime", props));

  text = text.replaceAll("[,.!?;:]", "$0 ");
  System.out.println("text: " + text);
  String[] text1 = text.replaceAll("[^A-Za-z0-9 ]", " ").toLowerCase()
    .split("\\s+"); //TODO I think this does a better version of what the above (text = text.replaceAll("[,.!?;:]", "$0 ");) does?? -Mark Fisher
  String text2 = String.join(" ", text1);

  System.out.println("text2: " + text2);
  edu.stanford.nlp.pipeline.Annotation annotation = new edu.stanford.nlp.pipeline.Annotation(text2);

  //get current date (no time) and formatted with Joda time.
  LocalDate date = LocalDate.now();
  DateTimeFormatter dateFormat = DateTimeFormat.forPattern("yyyy-MM-dd");
  String referenceDate = dateFormat.print(date);

  //pass current date and record date (or dates) given by text in TIMEX3(an ISO 8601 extention)format.
  annotation.set(CoreAnnotations.DocDateAnnotation.class, referenceDate);
  pipeline.annotate(annotation);
  List<CoreMap> timexAnnsAll = annotation
  .get(TimeAnnotations.TimexAnnotations.class);

  ArrayList<String> arrayListDates = new ArrayList<String>();
  for (CoreMap cm : timexAnnsAll) {
    Temporal myDate = cm.get(TimeExpression.Annotation.class).getTemporal();
    //        TimeExpression.Annotation:The CoreMap key for storing a TimeExpression annotation.
    String dateStr = myDate.toString();
    System.out.println(".....found date: " + dateStr);
    arrayListDates.add(dateStr.replaceAll("-XX", ""));
  }
  System.out.println("NLP dates found+:" + arrayListDates);

  if (!arrayListDates.isEmpty()) {
    //turn arrayList into an array to be able to use the old For loop and compare dates.
    String[] arrayDates = new String[arrayListDates.size()];
    arrayDates = arrayListDates.toArray(arrayDates);
    String selectedDate = "";

    try{
      System.out.println("About to enter selectBestDateFromCandidates");
      selectedDate = selectBestDateFromCandidates(arrayDates);
    } catch(Exception e){
      e.printStackTrace();
    }
    if(selectedDate == null | selectedDate.equals("")){
      try{
        java.util.Date tweetDate = tweet.getCreatedAt();
        DateFormat dfTweetStamp = new SimpleDateFormat("yyyy-MM-dd");
        selectedDate = dfTweetStamp.format(tweetDate);
        System.out.println("Date of tweet when all other candidates were eliminated is: " + selectedDate);
        return selectedDate;
      } catch(Exception e){
        e.printStackTrace();
        throw new Exception("Couldn't fetch a timeStamp for the tweet to use as a last-resort date after all other candidates were eliminated");
      }
    } else{
      return selectedDate;
    }
  } else{
    try{
      java.util.Date tweetDate = tweet.getCreatedAt();
      DateFormat dfTweetStamp = new SimpleDateFormat("yyyy-MM-dd");
      String selectedDate = dfTweetStamp.format(tweetDate);
      System.out.println("Date of tweet when no other candidates existed is: " + selectedDate);
      return selectedDate;
    } catch(Exception e){
      e.printStackTrace();
      throw new Exception("Couldn't fetch a timeStamp for the tweet to use as a last-resort date when no other candidates existed");
    }
  }
}

public static String nlpDateParse(String text) throws Exception{
  System.out.println("Entering nlpDateParse with text " + text);
  //create my pipeline with the help of the annotators I added.
  Properties props = new Properties();
  AnnotationPipeline pipeline = new AnnotationPipeline();
  pipeline.addAnnotator(new TokenizerAnnotator(false));
  pipeline.addAnnotator(new WordsToSentencesAnnotator(false));
  pipeline.addAnnotator(new POSTaggerAnnotator(false));
  pipeline.addAnnotator(new TimeAnnotator("sutime", props));

  text = text.replaceAll("[,.!?;:]", "$0 ");
  System.out.println("text: " + text);
  String[] text1 = text.replaceAll("[^A-Za-z0-9 ]", " ").toLowerCase()
    .split("\\s+"); //TODO I think this does a better version of what the above (text = text.replaceAll("[,.!?;:]", "$0 ");) does?? -Mark Fisher
  String text2 = String.join(" ", text1);

  System.out.println("text2: " + text2);
  edu.stanford.nlp.pipeline.Annotation annotation = new edu.stanford.nlp.pipeline.Annotation(text2);

  //get current date (no time) and formatted with Joda time.
  LocalDate date = LocalDate.now();
  DateTimeFormatter dateFormat = DateTimeFormat.forPattern("yyyy-MM-dd");
  String referenceDate = dateFormat.print(date);

  //pass current date and record date (or dates) given by text in TIMEX3(an ISO 8601 extention)format.
  annotation.set(CoreAnnotations.DocDateAnnotation.class, referenceDate);
  pipeline.annotate(annotation);
  List<CoreMap> timexAnnsAll = annotation
  .get(TimeAnnotations.TimexAnnotations.class);

  ArrayList<String> arrayListDates = new ArrayList<String>();
  for (CoreMap cm : timexAnnsAll) {
    Temporal myDate = cm.get(TimeExpression.Annotation.class).getTemporal();
    //        TimeExpression.Annotation:The CoreMap key for storing a TimeExpression annotation.
    String dateStr = myDate.toString();
    System.out.println(".....found date: " + dateStr);
    arrayListDates.add(dateStr.replaceAll("-XX", ""));
  }
  System.out.println("NLP dates found+:" + arrayListDates);

  if (!arrayListDates.isEmpty()) {
    //turn arrayList into an array to be able to use the old For loop and compare dates.
    String[] arrayDates = new String[arrayListDates.size()];
    arrayDates = arrayListDates.toArray(arrayDates);
    String selectedDate = "";

    try{
      selectedDate = selectBestDateFromCandidates(arrayDates);
    } catch(Exception e){
      e.printStackTrace();
    }
    if(selectedDate == null | selectedDate.equals("")){
      throw new Exception("selectedDate was empty or null in the nlpDateParse method");
    } else{
      return selectedDate;
    }
  } else{
    throw new Exception("No candidate dates found in nlpDateParse method");
  }
}

public static String selectBestDateFromCandidates(String[] candidates) throws Exception{
  String selectedDate = "";

  if(candidates.length <1){
    throw new Exception("list of candidate dates was empty");
  } else if (candidates.length >= 1) {

    //filter by options that are valid dates
    ArrayList<String> validDates = new ArrayList<String>();
    try{
      validDates = removeInvalidDates(candidates);
    } catch(Exception e){
      // e.printStackTrace();
    }
    try{
      System.out.println(validDates);
    } catch(Exception e){
      System.out.println("couldn't print validDates");
      // e.printStackTrace();
    }

    //filter by options that are not in the future
    ArrayList<String> validDatesWithFutureDatesRemoved = new ArrayList<String>();
    try{
      validDatesWithFutureDatesRemoved = removeFutureDates(validDates);
      System.out.println("Before future removal:");
      System.out.println(validDates);
      System.out.println("After future removal:");
      System.out.println(validDatesWithFutureDatesRemoved);
    } catch(Exception e){
      System.out.println("couldn't run removeFutureDates");
      // e.printStackTrace();
    }
    
    //remove excessive past dates
    ArrayList<String> validDatesWithExcessiveDatesRemoved = new ArrayList<String>();
    try{
      validDatesWithExcessiveDatesRemoved = removeExcessivePastDates(validDatesWithFutureDatesRemoved);
      System.out.println("Before excessive past removal:");
      System.out.println(validDatesWithFutureDatesRemoved);
      System.out.println("After excessive past removal:");
      System.out.println(validDatesWithExcessiveDatesRemoved );
    } catch(Exception e){
      System.out.println("couldn't run removeExcessivePastDates");
      // e.printStackTrace();
    }


    //if non-yesterday dates exist as well as yesterday ones, prefer the non-yesterdays. Otherwise, just get the yesterday.
    ArrayList<String> validDatesFilteredByYesterday = new ArrayList<String>();
    try{
      validDatesFilteredByYesterday = removeYesterdayDatesIfTheyAreNotTheOnlyDates(validDatesWithExcessiveDatesRemoved );
      System.out.println(validDatesFilteredByYesterday);
    } catch(Exception e){
      System.out.println("couldn't print validDatesFilteredByYesterday");
      // e.printStackTrace();
    }


    //Now select the longest one?
    if(validDatesFilteredByYesterday == null | validDatesFilteredByYesterday.size()<1){
      throw new Exception("validDatesFilteredByYesterday is null or empty before selecting the longest string");
    }
    if(validDatesFilteredByYesterday.size()>1){
      for (int j = 0; j < validDatesFilteredByYesterday.size(); j++) {
        for (int k = j + 1; k < validDatesFilteredByYesterday.size(); k++) {
          if (validDatesFilteredByYesterday.get(j).length() > validDatesFilteredByYesterday.get(k).length()) {
            selectedDate = validDatesFilteredByYesterday.get(j);
          } else if (validDatesFilteredByYesterday.get(j).length() < validDatesFilteredByYesterday.get(k).length()) {
            selectedDate = validDatesFilteredByYesterday.get(k);
          } else {
            selectedDate = validDatesFilteredByYesterday.get(0);
          }
        }
      }
    } else if(validDatesFilteredByYesterday.size()==1){
      selectedDate = validDatesFilteredByYesterday.get(0);
    }
  }

  if(selectedDate == null | selectedDate.equals("")){
    throw new Exception("selectedDate either null or empty after selecting for longest one");
  } else {
    return selectedDate;
  }
}

public static ArrayList<String> removeInvalidDates(String[] candidates) throws Exception{
  ArrayList<String> validDates = new ArrayList<String>();
  DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
  DateFormat df2 = new SimpleDateFormat("yyyy-MM");
  DateFormat df3 = new SimpleDateFormat("yyyy");
  String newDateString = null;
  java.util.Date candiDate;
  for(int i =0; i<candidates.length; i++){
    String candidateString = candidates[i];
    try {
      candiDate = df.parse(candidateString);
      newDateString = df.format(candiDate);
      validDates.add(newDateString);
    } catch (ParseException e) {
      try{
        candiDate = df2.parse(candidateString);
        newDateString = df2.format(candiDate);
        validDates.add(newDateString);
      } catch(Exception f){
        try{
          candiDate = df3.parse(candidateString);
          newDateString = df3.format(candiDate);
          validDates.add(newDateString);
        } catch(Exception g){
          g.printStackTrace();
          continue;
        }
      }

    }
  }
  if(validDates == null | validDates.size()<1){
    throw new Exception("validDates arrayList is empty or null in removeInvalidDates method");
  } else{
    return validDates;
  }
}

public static ArrayList<String> removeFutureDates(ArrayList<String> candidates) throws Exception{
  //TODO add handling for tweets coming from future in datelines
  ArrayList<String> returnCandidates = new ArrayList<String>();
  java.util.Date today =  getToday();
  for(int i = 0; i<candidates.size(); i++){
    String currentDateString = candidates.get(i);
    try{
      java.util.Date currentDateObj = convertStringToDate(currentDateString);
      if(currentDateObj == null){
        System.out.println("currentDateObj in removeFutureDates is null");
      }
      if(!currentDateObj.after(today)){
        returnCandidates.add(currentDateString);
      }
    } catch(Exception e){
      e.printStackTrace();
      continue;
    }
  }
  if(returnCandidates == null | returnCandidates.size()<1){
    throw new Exception("return list is null or empty after removeFutureDates runs");
  } else{
    return returnCandidates;
  }
}

public static java.util.Date convertStringToDate(String dateString) throws Exception{
  DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
  DateFormat df2 = new SimpleDateFormat("yyyy-MM");
  DateFormat df3 = new SimpleDateFormat("yyyy");
  java.util.Date returnDate = null;
  try{
    returnDate = df.parse(dateString);
  } catch(Exception e){
    try{
      returnDate = df2.parse(dateString);
    } catch(Exception f){
      try{
        returnDate = df3.parse(dateString);
      } catch(Exception g){
        g.printStackTrace();
      }
    }
  }

  if(returnDate == null){
    throw new Exception("date in convertStringToDate is null");
  } else{
    return returnDate;
  }
}

public static ArrayList<String> removeYesterdayDatesIfTheyAreNotTheOnlyDates(ArrayList<String> candidates) throws Exception{
    String yesterday = getYesterdayDateString();
    ArrayList<String> returnCandidates = new ArrayList<String>();
    int yesterdayCounter = 0;
    for(int i = 0; i<candidates.size(); i++){
      if (candidates.get(i).equals(yesterday)){
        yesterdayCounter ++;
      } else {
        returnCandidates.add(candidates.get(i));
      }
    }
    if(yesterdayCounter == candidates.size() | yesterdayCounter == 0){
      //yesterday is the only date or yesterday doesn't occur at all
      returnCandidates = candidates;
    } else if (yesterdayCounter != 0 && yesterdayCounter < candidates.size()){
      //keep returnCandidates as it is from the for loop above
    }
    if(returnCandidates == null | returnCandidates.size()<1){
      throw new Exception("removeYesterdayDatesIfTheyAreNotTheOnlyDates method returned null or empty arrayList");
    } else{
      return returnCandidates;
    }
}

public static String getTodayDateString() {
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        return dateFormat.format(getToday());
}

public static java.util.Date getToday() {
    final Calendar cal = Calendar.getInstance();
    return cal.getTime();
}

public static String getYesterdayDateString() {
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        return dateFormat.format(getYesterday());
}

public static java.util.Date getYesterday() {
    final Calendar cal = Calendar.getInstance();
    cal.add(Calendar.DATE, -1);
    return cal.getTime();
}


/* Same as nlpDateParse, but will return the entire arraylist instead of
** just the best date
*/
public static ArrayList<String> nlpDateParseToArrayList(String text){
  System.out.println("Entering nlpParseDateArray");

  // create pipeline with annotators like above
  Properties props = new Properties();
  AnnotationPipeline pipeline = new AnnotationPipeline();
  pipeline.addAnnotator(new TokenizerAnnotator(false));
  pipeline.addAnnotator(new WordsToSentencesAnnotator(false));
  pipeline.addAnnotator(new POSTaggerAnnotator(false));
  pipeline.addAnnotator(new TimeAnnotator("sutime", props));

  // replace special characters using regex
  text = text.replaceAll("[,.!?;:]", "$0 ");
  System.out.println("text: " + text);
  String[] text1 = text.replaceAll("[^A-Za-z0-9 ]", "").toLowerCase().split("\\s+");
  String text2 = String.join(" ", text1);

  System.out.println("text2: " + text2);
  // Create annotation using new string
  edu.stanford.nlp.pipeline.Annotation annotation = new edu.stanford.nlp.pipeline.Annotation(text2);

  // Get current date (not time) and formatted with Joda time as above
  LocalDate date = LocalDate.now();
  DateTimeFormatter dateTimeFormatter = DateTimeFormat.forPattern("yyyy-MM-dd");
  String referenceDate = dateTimeFormatter.print(date);

  // Pass current date and record date(s) given by text in TIMEX3(ISO 8601 standard) format.
  annotation.set(CoreAnnotations.DocDateAnnotation.class, referenceDate);
  pipeline.annotate(annotation);
  List<CoreMap> timexAnnsAll = annotation.get(TimeAnnotations.TimexAnnotations.class);

  ArrayList<String> arrayListDates = new ArrayList<String>();
  for(CoreMap cm : timexAnnsAll){
    Temporal myDate = cm.get(TimeExpression.Annotation.class).getTemporal();
    String dateStr = myDate.toString();
    System.out.println(".....found date: " + dateStr);
    arrayListDates.add(dateStr.replaceAll("-XX", ""));
  }
  System.out.println("NLP dates found+: " + arrayListDates);

  return arrayListDates;
}

//use this function to remove false positive from NLP that are clearly before the project started, such as "1492"
public static ArrayList<String> removeExcessivePastDates(ArrayList<String> candidates) throws Exception{
  //TODO add handling for tweets coming from future in datelines
  ArrayList<String> returnCandidates = new ArrayList<String>();
  for(int i = 0; i<candidates.size(); i++){
    String currentDateString = candidates.get(i);
    try{
      java.util.Date currentDateObj = convertStringToDate(currentDateString);
      if(currentDateObj == null){
        System.out.println("currentDateObj in removeFutureDates is null");
      }
      if(!currentDateObj.before(convertStringToDate("1930-01-01"))){
        returnCandidates.add(currentDateString);
      }
    } catch(Exception e){
      e.printStackTrace();
      continue;
    }
  }
  if(returnCandidates == null | returnCandidates.size()<1){
    throw new Exception("return list is null or empty after removeExcessivePastDates runs");
  } else{
    return returnCandidates;
  }
}


}
