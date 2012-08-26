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
import org.ecocean.*;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import javax.jdo.Query;
import javax.servlet.http.HttpServletRequest;
import java.io.*;
import java.net.URL;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.ThreadPoolExecutor;

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

      //process the CSS string
      templateFile = templateFile.replaceAll("CSSURL", CommonConfiguration.getCSSURLLocation(request));

      //set the top header graphic
      templateFile = templateFile.replaceAll("TOPGRAPHIC", CommonConfiguration.getURLToMastheadGraphic());

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

  public static String getFooter() {
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
      templateFile = templateFile.replaceAll("BOTTOMGRAPHIC", CommonConfiguration.getURLToFooterGraphic());

      int end_header = templateFile.indexOf("INSERT_HERE");
      return (templateFile.substring(end_header + 11));
    } catch (Exception e) {
      //out.println("I couldn't find the template file to read from.");
      e.printStackTrace();
      String error = "An error occurred while attempting to read from an HTML template file. This probably will not affect the success of the operation you were trying to perform.</p></body></html>";
      return error;
    }


  }

  public static void informInterestedParties(HttpServletRequest request, String number,
                                             String message) {
    Shepherd myShepherd = new Shepherd();
    myShepherd.beginDBTransaction();
    Encounter enc = myShepherd.getEncounter(number);
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
      es.execute(new NotificationMailer(CommonConfiguration.getMailHost(), CommonConfiguration.getAutoEmailAddress(), mailMe, ("Encounter data update: " + number), (email + mailMe), e_images));


      //NotificationMailer mailer=new NotificationMailer(CommonConfiguration.getMailHost(), CommonConfiguration.getAutoEmailAddress(), mailMe, ("Encounter data update: "+number), (email+mailMe), e_images);
      for (int j = 1; j < size; j++) {
        mailMe = interested[j];

        es.execute(new NotificationMailer(CommonConfiguration.getMailHost(), CommonConfiguration.getAutoEmailAddress(), mailMe, ("Encounter data update: " + number), (email + mailMe), e_images));
      }
    }
  }

  //inform researchers that have logged an interest with the encounter or marked individual
  public static void informInterestedIndividualParties(HttpServletRequest request, String shark, String message) {
    Shepherd myShepherd = new Shepherd();
    myShepherd.beginDBTransaction();
    MarkedIndividual sharkie = myShepherd.getMarkedIndividual(shark);
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

      es.execute(new NotificationMailer(CommonConfiguration.getMailHost(), CommonConfiguration.getAutoEmailAddress(), mailMe, ("Marked individual data update: " + shark), (email + mailMe), e_images));
      for (int j = 1; j < size; j++) {
        mailMe = interested[j];
        es.execute(new NotificationMailer(CommonConfiguration.getMailHost(), CommonConfiguration.getAutoEmailAddress(), mailMe, ("Individual data update: " + shark), (email + mailMe), e_images));
      }
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
  public static synchronized void addATOMEntry(String title, String link, String description, File atomFile) {
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
        if(CommonConfiguration.getProperty("htmlTitle")!=null){
        	SyndCategory category2 = new SyndCategoryImpl();
        	category2.setName(CommonConfiguration.getProperty("htmlTitle"));
        	categories.add(category2);
		}
        newItem.setCategories(categories);
        if(CommonConfiguration.getProperty("htmlAuthor")!=null){
        	newItem.setAuthor(CommonConfiguration.getProperty("htmlAuthor"));
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
    if (request.isUserInRole("admin")) {
      isOwner = true;
    } 
    else if (request.isUserInRole(enc.getLocationCode())) {
      isOwner = true;
    } 
    else if ((((enc.getSubmitterID() != null) && (request.getRemoteUser() != null) && (enc.getSubmitterID().equals(request.getRemoteUser()))))) {
      isOwner = true;
    }
    return isOwner;
  }

  public static boolean isUserAuthorizedForIndividual(MarkedIndividual sharky, HttpServletRequest request) {
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
    return false;
  }
  
  //occurrence
  public static boolean isUserAuthorizedForOccurrence(Occurrence sharky, HttpServletRequest request) {
    if (request.isUserInRole("admin")) {
      return true;
    }

    ArrayList<Encounter> encounters = sharky.getEncounters();
    int numEncs = encounters.size();
    for (int y = 0; y < numEncs; y++) {
      Encounter enc = (Encounter) encounters.get(y);
      if (request.isUserInRole(enc.getLocationCode())) {
        return true;
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

}
