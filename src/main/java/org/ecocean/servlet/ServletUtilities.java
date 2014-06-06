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

import javax.jdo.Query;
//import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
//import javax.servlet.http.HttpSession;

import java.io.*;
import java.net.URL;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.ThreadPoolExecutor;
import java.sql.*;

import org.ecocean.*;
import org.apache.shiro.crypto.hash.*;
import org.apache.shiro.util.*; 
import org.apache.shiro.crypto.*;

import java.util.Properties;

import javax.servlet.http.Cookie;

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

  public static void informInterestedParties(HttpServletRequest request, String number, String message, String context) {
    //String context="context0";
    //context=ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);
    myShepherd.beginDBTransaction();
    
    if(myShepherd.isEncounter(number)){
      
      Encounter enc = myShepherd.getEncounter(number);
      if(enc.getInterestedResearchers()!=null){
        Vector notifyMe = enc.getInterestedResearchers();
        int size = notifyMe.size();
        String[] interested = new String[size];
        for (int i = 0; i < size; i++) {
          interested[i] = (String) notifyMe.get(i);
        }
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
        if (size > 0) {
          Vector e_images = new Vector();
          String mailMe = interested[0];
          String email = getText("dataUpdate.txt").replaceAll("INSERTTEXT", ("Encounter " + number + ": " + message + "\n\nLink to encounter: http://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" + number));
          email += ("\n\nWant to stop tracking this set of encounter data? Use this link.\nhttp://" + CommonConfiguration.getURLLocation(request) + "/dontTrack?number=" + number + "&email=");
          ThreadPoolExecutor es = MailThreadExecutorService.getExecutorService();
          es.execute(new NotificationMailer(CommonConfiguration.getMailHost(context), CommonConfiguration.getAutoEmailAddress(context), mailMe, ("Encounter data update: " + number), (email + mailMe), e_images, context));


          //NotificationMailer mailer=new NotificationMailer(CommonConfiguration.getMailHost(), CommonConfiguration.getAutoEmailAddress(), mailMe, ("Encounter data update: "+number), (email+mailMe), e_images);
          for (int j = 1; j < size; j++) {
            mailMe = interested[j];
            es.execute(new NotificationMailer(CommonConfiguration.getMailHost(context), CommonConfiguration.getAutoEmailAddress(context), mailMe, ("Encounter data update: " + number), (email + mailMe), e_images, context));
          }
        }
      }
      else{
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
      }
      
    
    }
    else{
      myShepherd.rollbackDBTransaction();
      myShepherd.closeDBTransaction();
    }
    
  }

  //inform researchers that have logged an interest with the encounter or marked individual
  public static void informInterestedIndividualParties(HttpServletRequest request, String shark, String message, String context) {
    Shepherd myShepherd = new Shepherd(context);
    myShepherd.beginDBTransaction();

      if(myShepherd.isMarkedIndividual(shark)){
        MarkedIndividual sharkie = myShepherd.getMarkedIndividual(shark);
        if(sharkie.getInterestedResearchers()!=null){
          Vector notifyMe = sharkie.getInterestedResearchers();
          int size = notifyMe.size();
          String[] interested = new String[size];
          for (int i = 0; i < size; i++) {
            interested[i] = (String) notifyMe.get(i);
          }
          myShepherd.rollbackDBTransaction();
          myShepherd.closeDBTransaction();
          if (size > 0) {

            ThreadPoolExecutor es = MailThreadExecutorService.getExecutorService();

            Vector e_images = new Vector();
            String mailMe = interested[0];
            String email = getText("dataUpdate.txt").replaceAll("INSERTTEXT", ("Tag " + shark + ": " + message + "\n\nLink to individual: http://" + CommonConfiguration.getURLLocation(request) + "/individuals.jsp?number=" + shark));
            email += ("\n\nWant to stop tracking this set of this individual's data? Use this link.\n\nhttp://" + CommonConfiguration.getURLLocation(request) + "/dontTrack?shark=" + shark + "&email=");

            es.execute(new NotificationMailer(CommonConfiguration.getMailHost(context), CommonConfiguration.getAutoEmailAddress(context), mailMe, ("Marked individual data update: " + shark), (email + mailMe), e_images,context));
            for (int j = 1; j < size; j++) {
              mailMe = interested[j];
              es.execute(new NotificationMailer(CommonConfiguration.getMailHost(context), CommonConfiguration.getAutoEmailAddress(context), mailMe, ("Individual data update: " + shark), (email + mailMe), e_images,context));
            }
          }
        }
        else{
          myShepherd.rollbackDBTransaction();
          myShepherd.closeDBTransaction();
        }
      }
      else{
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
      }

    
    
    
  }


  //Loads a String of text from a specified file.
  //This is generally used to load an email template for automated emailing
  public static String getText(String fileName) {
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
    } catch (Exception e) {
      e.printStackTrace();
      return "";
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
    if (request.getUserPrincipal()!=null) {
      isOwner = true;
    } 
    return isOwner;
  }

  public static boolean isUserAuthorizedForIndividual(MarkedIndividual sharky, HttpServletRequest request) {
    if (request.getUserPrincipal()!=null) {
      return true;
    } 
    return false;
  }
  
  //occurrence
  public static boolean isUserAuthorizedForOccurrence(Occurrence sharky, HttpServletRequest request) {
    if (request.getUserPrincipal()!=null) {
      return true;
    } 
    return false;
  }


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

  public static String cleanFileName(String aTagFragment) {
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

  public static String preventCrossSiteScriptingAttacks(String description) {
    description = description.replaceAll("<", "&lt;").replaceAll(">", "&gt;");
    description = description.replaceAll("eval\\((.*)\\)", "");
    description = description.replaceAll("[\\\"\\\'][\\s]*((?i)javascript):(.*)[\\\"\\\']", "\"\"");
    description = description.replaceAll("((?i)script)", "");
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
    ArrayList<String> domainNames=ContextConfiguration.getContextDomainNames(thisContext);
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

  
  ArrayList<String> supportedLanguages=new ArrayList<String>();
  if(CommonConfiguration.getSequentialPropertyValues("language", context)!=null){
    supportedLanguages=CommonConfiguration.getSequentialPropertyValues("language", context);
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
    if(!shepherdDataDir.exists()){shepherdDataDir.mkdir();}
		return shepherdDataDir.getAbsolutePath();
	}

	//like above, but can pass a subdir to append
	public static String dataDir(String context, String rootWebappPath, String subdir) {
		return dataDir(context, rootWebappPath) + File.separator + subdir;
	}


}
