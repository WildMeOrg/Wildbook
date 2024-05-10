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
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;

import java.io.*;
import java.net.URL;
import java.sql.*;
import java.util.ArrayList;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import org.apache.shiro.crypto.*;
import org.apache.shiro.crypto.hash.*;
import org.apache.shiro.util.*;
import org.ecocean.*;
import org.ecocean.security.Collaboration;
import org.ecocean.servlet.importer.ImportTask;

import java.util.Properties;

import javax.servlet.http.Cookie;

import org.apache.commons.lang3.StringEscapeUtils;

public class ServletUtilities {
    public static String getHeader(HttpServletRequest request) {
        try {
            FileReader fileReader = new FileReader(findResourceOnFileSystem(
                "servletResponseTemplate.htm"));
            BufferedReader buffread = new BufferedReader(fileReader);
            String templateFile = "", line;
            StringBuffer SBreader = new StringBuffer();
            while ((line = buffread.readLine()) != null) {
                SBreader.append(line).append("\n");
            }
            fileReader.close();
            buffread.close();
            templateFile = SBreader.toString();

            String context = getContext(request);

            // process the CSS string
            templateFile = templateFile.replaceAll("CSSURL",
                CommonConfiguration.getCSSURLLocation(request, context));

            // set the top header graphic
            templateFile = templateFile.replaceAll("TOPGRAPHIC",
                CommonConfiguration.getURLToMastheadGraphic(context));

            int end_header = templateFile.indexOf("INSERT_HERE");
            return (templateFile.substring(0, end_header));
        } catch (Exception e) {
            // out.println("I couldn't find the template file to read from.");
            e.printStackTrace();
            String error =
                "<html><body><p>An error occurred while attempting to read from the template file servletResponseTemplate.htm. This probably will not affect the success of the operation you were trying to perform.";
            return error;
        }
    }

    public static String getFooter(String context) {
        try {
            FileReader fileReader = new FileReader(findResourceOnFileSystem(
                "servletResponseTemplate.htm"));
            BufferedReader buffread = new BufferedReader(fileReader);
            String templateFile = "", line;
            StringBuffer SBreader = new StringBuffer();
            while ((line = buffread.readLine()) != null) {
                SBreader.append(line).append("\n");
            }
            fileReader.close();
            buffread.close();
            templateFile = SBreader.toString();
            templateFile = templateFile.replaceAll("BOTTOMGRAPHIC",
                CommonConfiguration.getURLToFooterGraphic(context));

            int end_header = templateFile.indexOf("INSERT_HERE");
            return (templateFile.substring(end_header + 11));
        } catch (Exception e) {
            // out.println("I couldn't find the template file to read from.");
            e.printStackTrace();
            String error =
                "An error occurred while attempting to read from an HTML template file. This probably will not affect the success of the operation you were trying to perform.</p></body></html>";
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
    public static void informInterestedParties(HttpServletRequest request, String encounterNumber,
        String message, String context) {
        Shepherd shep = new Shepherd(context);

        shep.setAction("ServletUtilities.class.informInterestedParties");
        shep.beginDBTransaction();
        if (shep.isEncounter(encounterNumber)) {
            Encounter enc = shep.getEncounter(encounterNumber);
            if (enc.getInterestedResearchers() != null) {
                Collection<String> notifyMe = enc.getInterestedResearchers();
                if (!notifyMe.isEmpty()) {
                    ThreadPoolExecutor es = MailThreadExecutorService.getExecutorService();
                    for (String mailTo : notifyMe) {
                        Map<String, String> tagMap = NotificationMailer.createBasicTagMap(request,
                            enc);
                        tagMap.put(NotificationMailer.EMAIL_NOTRACK, "number=" + encounterNumber);
                        tagMap.put(NotificationMailer.EMAIL_HASH_TAG,
                            Encounter.getHashOfEmailString(mailTo));
                        tagMap.put(NotificationMailer.STANDARD_CONTENT_TAG,
                            message == null ? "" : message);
                        // String langCode = ServletUtilities.getLanguageCode(request);
                        NotificationMailer mailer = new NotificationMailer(context, null, mailTo,
                            "encounterDataUpdate", tagMap);
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
    public static void informInterestedIndividualParties(HttpServletRequest request,
        String individualID, String message, String context) {
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
                        Map<String, String> tagMap = NotificationMailer.createBasicTagMap(request,
                            ind);
                        tagMap.put(NotificationMailer.EMAIL_NOTRACK, "individual=" + individualID);
                        tagMap.put(NotificationMailer.EMAIL_HASH_TAG,
                            Encounter.getHashOfEmailString(mailTo));
                        tagMap.put(NotificationMailer.STANDARD_CONTENT_TAG,
                            message == null ? "" : message);
                        // String langCode = ServletUtilities.getLanguageCode(request);
                        NotificationMailer mailer = new NotificationMailer(context, null, mailTo,
                            "individualDataUpdate", tagMap);
                        es.execute(mailer);
                    }
                    es.shutdown();
                }
            }
        }
        shep.rollbackDBTransaction();
        shep.closeDBTransaction();
    }

    // Loads a String of text from a specified file.
    // This is generally used to load an email template for automated emailing
    public static String getText(String shepherdDataDir, String fileName, String langCode) {
        String overrideText = loadOverrideText(shepherdDataDir, fileName, langCode);

        if (!overrideText.equals("")) {
            return overrideText;
        } else {
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
    }

    // Logs a new ATOM entry
    public static synchronized void addATOMEntry(String title, String link, String description,
        File atomFile, String context) {
        try {
            if (atomFile.exists()) {
                // System.out.println("ATOM file found!");
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
                if (CommonConfiguration.getProperty("htmlTitle", context) != null) {
                    SyndCategory category2 = new SyndCategoryImpl();
                    category2.setName(CommonConfiguration.getProperty("htmlTitle", context));
                    categories.add(category2);
                }
                newItem.setCategories(categories);
                if (CommonConfiguration.getProperty("htmlAuthor", context) != null) {
                    newItem.setAuthor(CommonConfiguration.getProperty("htmlAuthor", context));
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

    // Logs a new entry in the library RSS file
    public static synchronized void addRSSEntry(String title, String link, String description,
        File rssFile) {
        // File rssFile=new File("nofile.xml");

        try {
            System.out.println("Looking for RSS file: " + rssFile.getCanonicalPath());
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

                // now save changes
                FileWriter mywriter = new FileWriter(rssFile);
                OutputFormat format = OutputFormat.createPrettyPrint();
                format.setLineSeparator(System.getProperty("line.separator"));
                XMLWriter writer = new XMLWriter(mywriter, format);
                writer.write(document);
                writer.close();
            }
        } catch (IOException ioe) {
            System.out.println("ERROR: Could not find the RSS file.");
            ioe.printStackTrace();
        } catch (DocumentException de) {
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

    // there must be a less bad name for this
    public static boolean isCurrentUserOrgAdminOfTargetUser(User user, HttpServletRequest request,
        Shepherd myShepherd) {
        return request.isUserInRole("orgAdmin") &&
                   !myShepherd.getAllCommonOrganizationsForTwoUsers(user,
                myShepherd.getUser(myShepherd.getUsername(request))).isEmpty();
    }

    public static boolean isUserAuthorizedForEncounter(Encounter enc, HttpServletRequest request) {
        boolean isOwner = false;
        Shepherd myShepherd = new Shepherd(request);

        myShepherd.setAction("SevrletUtilities.isUserAuthorizedForEncounterShortForm");
        myShepherd.beginDBTransaction();
        try {
            isOwner = isUserAuthorizedForEncounter(enc, request, myShepherd);
        } catch (Exception e) { e.printStackTrace(); } finally {
            myShepherd.rollbackAndClose();
        }
        return isOwner;
    }

    public static boolean isUserAuthorizedForEncounter(Encounter enc, HttpServletRequest request,
        Shepherd myShepherd) {
        boolean isOwner = false;

        try {
            if (request.getUserPrincipal() != null) {
                // if the current user is an admin, they always have access
                if (request.isUserInRole("admin")) {
                    isOwner = true;
                }
                // user-specific checks
                else if ((enc.getSubmitterID() != null) && (request.getRemoteUser() != null)) {
                    // allow access to public encounters
                    if (enc.getSubmitterID().equals("public")) return true;
                    // if the current user owns the Encounter, they obviously have permission
                    if (enc.getSubmitterID().equals(request.getRemoteUser())) { isOwner = true; }
                    // if the current user is the orgAdmin for the other user, they can ave permission
                    else if (request.isUserInRole("orgAdmin") &&
                        myShepherd.getUser(enc.getSubmitterID()) != null) {
                        User encounterOwner = myShepherd.getUser(enc.getSubmitterID());
                        User requester = myShepherd.getUser(request);
                        List<Organization> encounterOwnerOrgs = encounterOwner.getOrganizations();
                        List<Organization> requesterOrgs = requester.getOrganizations();
                        if (encounterOwnerOrgs != null && encounterOwnerOrgs.size() > 0 &&
                            requesterOrgs != null && requesterOrgs.size() > 0) {
                            for (Organization org : encounterOwnerOrgs) {
                                if (requesterOrgs.contains(org)) { isOwner = true; }
                            }
                        }
                    }
                }
                // check other cases
                // ----------------
                // if the current user has a location ID-specific role matching the location ID of this Encounter, they are authorized
                if (!isOwner && enc.getLocationCode() != null &&
                    request.isUserInRole(enc.getLocationCode())) {
                    isOwner = true;
                }
                // if the current user is in a collaboration with the Encounter owner
                if (!isOwner && Collaboration.canEditEncounter(enc, request)) { return true; }
                // allow WDP edit stenella frontalis cit sci encounters
                // TODO make a mmore resonable way for researchers to ID and edit cit sci submissions
                if (!isOwner && "wdp".equals(request.getUserPrincipal().getName())) {
                    List<User> users = enc.getSubmitters();
                    boolean researcherSubmitted = false;
                    for (User user : users) {
                        if (user.getUsername() != null && !"".equals(user.getUsername())) {
                            researcherSubmitted = true;
                        }
                    }
                    String genSpec = enc.getTaxonomyString();
                    if (!researcherSubmitted && "Stenella frontalis".equals(genSpec)) {
                        isOwner = true;
                    }
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        return isOwner;
    }

    public static boolean isEncounterOwnedByPublic(Encounter enc) {
        boolean isPublic = false;

        if (enc.getSubmitterID() != null &&
            enc.getSubmitterID().toLowerCase().trim().equals("public")) {
            isPublic = true;
        }
        return isPublic;
    }

    public static boolean isUserAuthorizedForIndividual(MarkedIndividual indy,
        HttpServletRequest request) {
        boolean isOwner = false;
        Shepherd myShepherd = new Shepherd(request);

        myShepherd.setAction("SevrletUtilities.isUserAuthorizedForIndividualShortForm");
        myShepherd.beginDBTransaction();
        try {
            isOwner = isUserAuthorizedForIndividual(indy, request, myShepherd);
        } catch (Exception e) { e.printStackTrace(); } finally {
            myShepherd.rollbackAndClose();
        }
        return isOwner;
    }

    public static boolean isUserAuthorizedForIndividual(MarkedIndividual indy,
        HttpServletRequest request, Shepherd myShepherd) {
        if (request.getUserPrincipal() != null) {
            if (request.isUserInRole("admin")) {
                return true;
            }
            Vector encounters = indy.getEncounters();
            int numEncs = encounters.size();
            for (int y = 0; y < numEncs; y++) {
                Encounter enc = (Encounter)encounters.get(y);
                if (isUserAuthorizedForEncounter(enc, request, myShepherd)) {
                    return true;
                }
            }
        }
        return false;
    }

    // occurrence

    public static boolean isUserAuthorizedForOccurrence(Occurrence occur,
        HttpServletRequest request) {
        boolean isOwner = false;
        Shepherd myShepherd = new Shepherd(request);

        myShepherd.setAction("SevrletUtilities.isUserAuthorizedForOccurrenceShortForm");
        myShepherd.beginDBTransaction();
        try {
            isOwner = isUserAuthorizedForOccurrence(occur, request, myShepherd);
        } catch (Exception e) { e.printStackTrace(); } finally {
            myShepherd.rollbackAndClose();
        }
        return isOwner;
    }

    public static boolean isUserAuthorizedForImportTask(ImportTask occ, HttpServletRequest request,
        Shepherd myShepherd) {
        if (request.getUserPrincipal() != null) {
            if (request.isUserInRole("admin")) {
                return true;
            }
            // first check if the User on the ImportTask matches the current user
            if (occ.getCreator() != null && request.getUserPrincipal() != null &&
                occ.getCreator().getUsername().equals(request.getUserPrincipal().getName())) {
                return true;
            }
            // quick collaboration check between current user and bulk import owner
            // if(occ.getCreator() !=null && Collaboration.canCollaborate(request.getUserPrincipal().getName(), occ.getCreator().getUsername(),
            // myShepherd.getContext()))return true;
            if (request.getUserPrincipal() != null &&
                request.getUserPrincipal().getName() != null && occ.getCreator() != null &&
                occ.getCreator().getUsername() != null) {
                Collaboration collab = Collaboration.collaborationBetweenUsers(myShepherd,
                    request.getUserPrincipal().getName(), occ.getCreator().getUsername());
                if (collab != null && collab.getState() != null &&
                    (collab.getState().equals(Collaboration.STATE_EDIT_PRIV) ||
                    collab.getState().equals(Collaboration.STATE_APPROVED))) return true;
            }
            // quick orgAdminCheck
            // if this user is the orgAdmin for the bulk import's uploading user, they can see it
            if (ServletUtilities.isCurrentUserOrgAdminOfTargetUser(occ.getCreator(), request,
                myShepherd)) { return true; }
        }
        return false;
    }

    public static boolean isUserAuthorizedForOccurrence(Occurrence occur,
        HttpServletRequest request, Shepherd myShepherd) {
        if (request.getUserPrincipal() != null) {
            if (request.isUserInRole("admin")) { return true; }
            ArrayList<Encounter> encounters = occur.getEncounters();
            int numEncs = encounters.size();
            for (int y = 0; y < numEncs; y++) {
                Encounter enc = (Encounter)encounters.get(y);
                if (isUserAuthorizedForEncounter(enc, request, myShepherd)) {
                    return true;
                }
            }
        }
        return false;
    }

    // occurrence

    public static Query setRange(Query query, int iterTotal, int highCount, int lowCount) {
        if (iterTotal > 10) {
            // handle the normal situation first
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

    public static String cleanFileName(String myString) {
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
       //add it to the result as is result.append(character);
       }
       character = iterator.next();
       }
       return result.toString();
       }
     */
    public static String getEncounterUrl(String encID, HttpServletRequest request) {
        return (CommonConfiguration.getServerURL(request) + "/encounters/encounter.jsp?number=" +
                   encID);
    }

    public static String getIndividualUrl(String indID, HttpServletRequest request) {
        return (CommonConfiguration.getServerURL(request) + "/individuals.jsp?number=" + indID);
    }

    public static String getOccurrenceUrl(String occID, HttpServletRequest request) {
        return (CommonConfiguration.getServerURL(request) + "/occurrence.jsp?number=" + occID);
    }

    public static String preventCrossSiteScriptingAttacks(String description) {
        description = description.replaceAll("<", "&lt;").replaceAll(">", "&gt;");
        description = description.replaceAll("eval\\((.*)\\)", "");
        description = description.replaceAll("[\\\"\\\'][\\s]*((?i)javascript):(.*)[\\\"\\\']",
            "\"\"");
        description = description.replaceAll("((?i)script)", "");
        description = description.replaceAll("onerror", "");
        // description = description.replaceAll("alert", "");
        description = StringEscapeUtils.escapeHtml4(description);
        return description;
    }

    public static String getDate() {
        DateTime dt = new DateTime();
        DateTimeFormatter fmt = ISODateTimeFormat.date();

        return (fmt.print(dt));
    }

    public static Connection getConnection()
    throws SQLException {
        Connection conn = null;
        Properties connectionProps = new Properties();

        connectionProps.put("user",
            CommonConfiguration.getProperty("datanucleus.ConnectionUserName", "context0"));
        connectionProps.put("password",
            CommonConfiguration.getProperty("datanucleus.ConnectionPassword", "context0"));

        conn = DriverManager.getConnection(CommonConfiguration.getProperty(
            "datanucleus.ConnectionURL", "context0"), connectionProps);

        System.out.println("Connected to database for authentication.");
        return conn;
    }

    public static String hashAndSaltPassword(String clearTextPassword, String salt) {
        return new Sha512Hash(clearTextPassword, salt, 200000).toHex();
    }

    public static String hashString(String hashMe) {
        return new Sha512Hash(hashMe).toHex();
    }

    public static ByteSource getSalt() {
        return new SecureRandomNumberGenerator().nextBytes();
    }

    public static String getContext(HttpServletRequest request) {
        String context = "context0";

        if (ContextConfiguration.getDefaultContext() != null) {
            context = ContextConfiguration.getDefaultContext();
        }
        Properties contexts = ShepherdProperties.getContextsProperties();
        int numContexts = contexts.size();
        // check the URL for the context attribute
        // this can be used for debugging and takes precedence
        if (request.getParameter("context") != null) {
            // get the available contexts
            // System.out.println("Checking for a context: "+request.getParameter("context"));
            if (contexts.containsKey((request.getParameter("context") + "DataDir"))) {
                // System.out.println("Found a request context: "+request.getParameter("context"));
                return request.getParameter("context");
            }
        }
        // the request cookie is the next thing we check. this should be the primary means of figuring context out
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("wildbookContext".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        // finally, we will check the URL vs values defined in context.properties to see if we can set the right context
        String currentURL = request.getServerName();
        for (int q = 0; q < numContexts; q++) {
            String thisContext = "context" + q;
            List<String> domainNames = ContextConfiguration.getContextDomainNames(thisContext);
            int numDomainNames = domainNames.size();
            for (int p = 0; p < numDomainNames; p++) {
                if (currentURL.indexOf(domainNames.get(p)) != -1) { return thisContext; }
            }
        }
        return context;
    }

    public static String getLanguageCode(HttpServletRequest request) {
        String context = ServletUtilities.getContext(request);

        // worst case scenario default to English
        String langCode = "en";

        // try to detect a default if defined
        if (CommonConfiguration.getProperty("defaultLanguage", context) != null) {
            langCode = CommonConfiguration.getProperty("defaultLanguage", context);
        }
        List<String> supportedLanguages = new ArrayList<String>();
        if (CommonConfiguration.getIndexedPropertyValues("language", context) != null) {
            supportedLanguages = CommonConfiguration.getIndexedPropertyValues("language", context);
        }
        // if specified directly, always accept the override
        if (request.getParameter("langCode") != null) {
            if (supportedLanguages.contains(request.getParameter("langCode"))) {
                return request.getParameter("langCode");
            }
        }
        // the request cookie is the next thing we check. this should be the primary means of figuring langCode out
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("wildbookLangCode".equals(cookie.getName())) {
                    if (supportedLanguages.contains(cookie.getValue())) {
                        return cookie.getValue();
                    }
                }
            }
        }
        // finally, we will check the URL vs values defined in context.properties to see if we can set the right context
        // TBD - future - detect browser supported language codes and locale from the HTTPServletRequest object

        return langCode;
    }

    public static String dataDir(String context, String rootWebappPath) {
        File webappsDir = new File(rootWebappPath).getParentFile();
        File shepherdDataDir = new File(webappsDir,
            CommonConfiguration.getDataDirectoryName(context));

        if (!shepherdDataDir.exists()) { shepherdDataDir.mkdirs(); }
        return shepherdDataDir.getAbsolutePath();
    }

// like above, but can pass a subdir to append
    public static String dataDir(String context, String rootWebappPath, String subdir) {
        return dataDir(context, rootWebappPath) + File.separator + subdir;
    }

/*
   //like above, but only need request passed public static String dataDir(HttpServletRequest request) {
   String context = "context0";
   context = ServletUtilities.getContext(request);
   //String rootWebappPath = request.getServletContext().getRealPath("/");  // only in 3.0??
   //String rootWebappPath = request.getSession(true).getServlet().getServletContext().getRealPath("/");
   ServletContext s = request.getServletContext();
   String rootWebappPath = "xxxxxx";
   return dataDir(context, rootWebappPath);
   }
 */
    private static String loadOverrideText(String shepherdDataDir, String fileName,
        String langCode) {
        // System.out.println("Starting loadOverrideProps");
        StringBuffer myText = new StringBuffer("");
        // Properties myProps=new Properties();
        File configDir = new File("webapps/" + shepherdDataDir + "/WEB-INF/classes/bundles/" +
            langCode);

        // System.out.println(configDir.getAbsolutePath());
        // sometimes this ends up being the "bin" directory of the J2EE container
        // we need to fix that
        if ((configDir.getAbsolutePath().contains("/bin/")) ||
            (configDir.getAbsolutePath().contains("\\bin\\"))) {
            String fixedPath = configDir.getAbsolutePath().replaceAll("/bin",
                "").replaceAll("\\\\bin", "");
            configDir = new File(fixedPath);
            // System.out.println("Fixing the bin issue in Shepherd PMF. ");
            // System.out.println("The fix abs path is: "+configDir.getAbsolutePath());
        }
        // System.out.println("ShepherdProps: "+configDir.getAbsolutePath());
        if (!configDir.exists()) { configDir.mkdirs(); }
        File configFile = new File(configDir, fileName);
        if (configFile.exists()) {
            // System.out.println("ShepherdProps: "+"Overriding default properties with " + configFile.getAbsolutePath());
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
            } finally {
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

    public static String handleNullString(Object obj) {
        if (obj == null) { return ""; }
        return obj.toString();
    }

    public static JSONObject jsonFromHttpServletRequest(HttpServletRequest request)
    throws IOException {
        StringBuilder sb = new StringBuilder();
        BufferedReader reader = request.getReader();

        try {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        // ParseException
        catch (Exception e) {
            e.printStackTrace();
            System.out.println("Errored state of sb is: " + sb.toString());
        } finally {
            reader.close();
        }
        return new JSONObject(sb.toString());
    }

    public static void printParams(HttpServletRequest request) {
        Enumeration<String> names = request.getParameterNames();

        while (names.hasMoreElements()) {
            String name = names.nextElement();
            System.out.println("  " + name + ": " + request.getParameter(name));
        }
    }

    public static List<String> getIndexedParameters(String key, HttpServletRequest request) {
        List<String> vals = new ArrayList<String>();

        for (int i = 0; i < 100000; i++) { // hundred thousand seems like a reasonable upper limit right?
            String val = request.getParameter(key + i);
            if (Util.stringExists(val)) vals.add(val);
            else return vals;
        }
        return vals;
    }

    public static String getParameterOrAttribute(String name, HttpServletRequest request) {
        if (name == null) return null;
        String result = request.getParameter(name);
        if (result == null) {
            Object attr = request.getAttribute(name);
            if (attr != null) result = attr.toString();
        }
        return result;
    }

    public static String getParameterOrAttributeOrSessionAttribute(String name,
        HttpServletRequest request) {
        String result = request.getParameter(name);

        if (result == null) {
            Object attr = request.getAttribute(name);
            if (attr != null) result = attr.toString();
        }
        if (result == null) result = getSessionAttribute(name, request);
        return result;
    }

    public static String getSessionAttribute(String name, HttpServletRequest request) {
        String stringAns = null;
        Object attr = request.getSession().getAttribute(name);

        if (attr != null) stringAns = attr.toString();
        return stringAns;
    }

// handy "let anyone do anything (?) cors stuff
    public static void doOptions(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST");
        if (request.getHeader("Access-Control-Request-Headers") != null)
            response.setHeader("Access-Control-Allow-Headers",
                request.getHeader("Access-Control-Request-Headers"));
    }

/* see webapps/captchaExample.jsp for implementation */

// note: this only handles single-widget (per page) ... if we need multiple, will have to extend things here
    public static String captchaWidget(HttpServletRequest request) {
        return captchaWidget(request, null);
    }

    public static String captchaWidget(HttpServletRequest request, String params) {
        String context = getContext(request);
        Properties recaptchaProps = ShepherdProperties.getProperties("recaptcha.properties", "",
            context);

        if (recaptchaProps == null)
            return
                    "<div class=\"error captcha-error captcha-missing-properties\">Unable to get captcha settings.</div>";
        String siteKey = recaptchaProps.getProperty("siteKey");
        String secretKey = recaptchaProps.getProperty("secretKey"); // really dont need this here
        if ((siteKey == null) || (secretKey == null))
            return
                    "<div class=\"error captcha-error captcha-missing-key\">Unable to get captcha key settings.</div>";
        return
                "<script>function recaptchaCompleted() { return (grecaptcha && grecaptcha.getResponse(0)); }</script>\n"
                + "<script src='https://www.google.com/recaptcha/api.js" + ((params ==
                null) ? "" : "?" + params) + "' async defer></script>\n" +
                "<div class=\"g-recaptcha\" data-sitekey=\"" + siteKey + "\"></div>";
    }

// https://developers.google.com/recaptcha/docs/verify
    public static boolean captchaIsValid(HttpServletRequest request) {
        return captchaIsValid(getContext(request), request.getParameter("g-recaptcha-response"),
                request.getRemoteAddr());
    }

    public static boolean captchaIsValid(String context, String uresp, String remoteIP) {
        if (context == null) context = "context0";
        Properties recaptchaProps = ShepherdProperties.getProperties("recaptcha.properties", "",
            context);
        if (recaptchaProps == null) {
            System.out.println("WARNING: no recaptcha.properties for captchaIsValid(); failing");
            return false;
        }
        String siteKey = recaptchaProps.getProperty("siteKey"); // really dont need this here
        String secretKey = recaptchaProps.getProperty("secretKey");
        if ((siteKey == null) || (secretKey == null)) {
            System.out.println("WARNING: could not determine keys for captchaIsValid(); failing");
            return false;
        }
        if (uresp == null) {
            System.out.println(
                "WARNING: g-recaptcha-response is null in captchaIsValid(); failing");
            return false;
        }
        JSONObject cdata = new JSONObject();
        cdata.put("secret", secretKey);
        cdata.put("remoteip", remoteIP); // i guess this is technically optional (so we dont care if null?)
        cdata.put("response", uresp);
        JSONObject gresp = null;
        try {
            gresp = RestClient.post(new URL("https://www.google.com/recaptcha/api/siteverify"),
                cdata);
        } catch (Exception ex) {
            System.out.println(
                "WARNING: exception calling captcha api in captchaIsValid(); failing: " +
                ex.toString());
            return false;
        }
        if (gresp == null) { // would this ever happen?
            System.out.println(
                "WARNING: null return from captcha api in captchaIsValid(); failing");
            return false;
        }
        System.out.println("INFO: captchaIsValid() api call returned: " + gresp.toString());
        return gresp.optBoolean("success", false);
    }

// this takes into account that we might be going thru nginx (etc?) as well
    public static String getRemoteHost(HttpServletRequest request) {
        if (request == null) return null;
        // these all seem to be *possible* headers from nginx (or other proxies?) but we standardize on "x-real-ip"
        // x-real-ip, x-forwarded-for, x-forwarded-host
        if (request.getHeader("x-real-ip") != null) return request.getHeader("x-real-ip");
        return request.getRemoteHost();
    }

    public static void importJsp(String filename, HttpServletRequest request,
        HttpServletResponse response)
    throws javax.servlet.ServletException, java.io.IOException {
        PrintWriter out = response.getWriter();

        request.getRequestDispatcher(filename).include(request, response);
    }

// used to determine if we want to apply a custom UI style, e.g. for IndoCet or the New England Aquarium to a web page
    public static boolean useCustomStyle(HttpServletRequest request, String orgName) {
        // check url for "organization=____" arg
        String organization = request.getParameter("organization");
        String cookieOrg = getOrganizationCookie(request);

        if (organization != null && organization.toLowerCase().equals(orgName.toLowerCase())) {
            return true;
        }
        if (cookieOrg != null && orgName.toLowerCase().equals(cookieOrg.toLowerCase())) {
            return true;
        }
        // The checks further below will also return true _right after logging out_ so we need this step
        if (Util.requestHasVal(request, "logout")) return false;
        // Shepherd handling w 'finally' to ensure we close the dbconnection after return.
        Shepherd myShepherd = Shepherd.newActiveShepherd(request,
            "ServletUtilities.useCustomStyle");
        try {
            // check user affiliation
            User user = myShepherd.getUser(request);
            if (user == null) return false;
            if (user.hasAffiliation(orgName)) return true;
            // check organization object
            Organization org = myShepherd.getOrganizationByName(orgName);
            if (org == null) return false;
            return org.hasMember(user);
        } finally {
            myShepherd.rollbackAndClose();
        }
    }

    public static String getOrganizationCookie(HttpServletRequest request) {
        // Similar to langCode above, check for cookie to apply custom styles
        String context = ServletUtilities.getContext(request);
        Cookie[] cookies = request.getCookies();

        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("wildbookOrganization".equals(cookie.getName())) {
                    // needed because of unicode in COOKIESPACE
                    String value = cookie.getValue().replaceAll("%20", " ");
                    return value;
                }
            }
        }
        return "";
    }
}
