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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.servlet.http.HttpServletRequest;
import org.ecocean.servlet.ServletUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Sends out an email notification.
 * This class in designed to run on an independent thread, so can be scheduled
 * for background operation.
 * <p>When an instance is created, a delegate {@code EmailTemplate} instance
 * is created which handles loading of relevant text/HTML content templates.
 * The template mechanism can be thought of as having three levels:</p>
 * <ol>
 * <li>Base template (text/HTML versions; used for all emails).</li>
 * <li>Type template (text/HTML versions; used for specific type emails).</li>
 * <li>Tag assignments (user-specified text replacements; apply to both text/HTML versions).</li>
 * </ol>
 *
 * <p>For example, if client code calls the following:</p>
 * <pre>
 * String context = "context0";
 * String langCode = "en";
 * String from = "from&#64;bar.org";
 * String to = "to&#64;wibble.org";
 * String type = "individualAddEncounter";
 * Map&lt;String, String&gt; tagMap = new HashMap&lt;&gt;();
 * NotificationMailer mailer = new NotificationMailer(context, langCode, from, to, type, tagMap);
 * </pre>
 * <p>then the following will occur:</p>
 * <ol>
 * <li>Base templates loaded (<code>email-template.txt</code>, <code>email-template.html</code>).</li>
 * <li>Type templates loaded (<code>individualAddEncounter.txt</code>, <code>individualAddEncounter.html</code>).</li>
 * <li>Type templates inserted into respective base templates (via <em>&#64;EMAIL_CONTENT&#64;</em> tag).</li>
 * <li>Email subject line extracted from first line of plain text template (if possible).</li>
 * <li>Replacements performed for tags specified in map.</li>
 * </ol>
 * <p>Initialization is now complete, and no email will be sent until the
 * {@link #run()} method is invoked, usually via a wrapping
 * {@link java.lang.Thread} or {@link java.util.concurrent.Executor}.</p>
 * <p>If the <em>type</em> parameter is null, then no type-template is loaded,
 * and the base tag (<em>&#64;EMAIL_CONTENT&#64;</em>) is simply replaced by the
 * standard content tag (<em>&#64;TEXT_CONTENT&#64;</em>) before proceeding as usual.</p>
 *
 * <p>If instead the other constructor is invoked:</p>
 * <pre>
 * String context = "context0";
 * String langCode = "en";
 * String from = "from&#64;bar.org";
 * String to = "to&#64;wibble.org";
 * String type = "individualAddEncounter";
 * String text = "Thank you for submitting a new individual to the database!";
 * NotificationMailer mailer = new NotificationMailer(context, langCode, from, to, type, text);
 * </pre>
 * <p>then the standard content tag (<em>&#64;TEXT_CONTENT&#64;</em>) will be replaced
 * with the specified text, and no other tag replacements will occur during
 * initialization.</p>
 * <p>If instead you want different text inserted for each of the plain/HTML
 * templates, you must do the replacements after initialization:</p>
 * <pre>
 * String context = "context0";
 * String langCode = "en";
 * String from = "from&#64;bar.org";
 * String to = "to&#64;wibble.org";
 * String type = "individualAddEncounter";
 * NotificationMailer mailer = new NotificationMailer(context, langCode, from, to, type);
 * String text = "Thank you for submitting a new individual to the database!";
 * mailer.replaceInPlainText("&#64;TEXT_CONTENT&#64;", text);
 * mailer.replaceInHtmlText("&#64;TEXT_CONTENT&#64;", "&lt;div id=\&quot;thanks&quot;\&gt;" + text + "&lt;/div&gt;");
 * </pre>
 *
 * <p>An optional REMOVEME section is also supported, which is designed to
 * support inclusion of a section for removing a user from an email
 * subscription. This optional section can be delimited by
 * <em>&#64;REMOVEME_START&#64;</em> &amp; <em>&#64;REMOVEME_END&#64;</em>
 * tags in the plain text template, or by commented versions
 * (<em>&lt;!--&#64;REMOVEME_START&#64;--&gt;</em> &amp;
 * <em>&lt;!--&#64;REMOVEME_END&#64;--&gt;</em>) in the HTML template.
 * If a user-specific email-hash tag is specified in the tag map, these
 * delimiters are removed, and the section remains in place, otherwise the
 * entire section is removed during initialization.</p>
 * <p>For example, this in the plain text version:</p>
 * <pre>
 * &#64;REMOVEME_START&#64;
 * To be unsubscribed from these emails, follow this link:
 *     &#64;REMOVE_LINK&#64;
 * &#64;REMOVEME_END&#64;
 * </pre>
 * <p>or the following in the HTML version:</p>
 * <pre>
 * &lt;!--&#64;REMOVEME_START&#64;--&gt;
 * &lt;p&gt;To be unsubscribed from these emails, &lt;a href="&#64;REMOVE_LINK&#64;"&gt;click here&lt;/a&gt;.&lt;/p&gt;
 * &lt;!--&#64;REMOVEME_END&#64;--&gt;
 * </pre>
 *
 * <p>After initialization, and before invoking {@link #run()}, the template
 * can still be used to make arbitrary tag replacements using the
 * {@link #replace(String, String)} or {@link #replaceRegex(String, String)}
 * methods.</p>
 *
 * @author Giles Winstanley
 */
public final class NotificationMailer implements Runnable {
  /** SLF4J logger instance for writing log entries. */
  private static final Logger log = LoggerFactory.getLogger(NotificationMailer.class);
  /** Charset for plain text email. */
  private static final Charset CHARSET_PLAIN = Charset.forName("UTF-8");
  /** Charset for HTML text email. */
  private static final Charset CHARSET_HTML = Charset.forName("UTF-8");
  /** Search path for email templates (relative to root resources). */
  private static final String SEARCH_PATH = "emails";
  /** Base email template from which all emails are derived. */
  private static final String BASE_TEMPLATE_ROOT = "email-template";
  /** Tag in base template to replace with email-specific content. */
  private static final String BASE_CONTENT_TAG = "@EMAIL_CONTENT@";
  /** Generic tag in to replace with text content. */
  public static final String STANDARD_CONTENT_TAG = "@TEXT_CONTENT@";
  /** Tag to replace with email &quot;dontTrack&quot; link when specifying for REMOVEME section. */
  public static final String EMAIL_NOTRACK = "@EMAIL_NOTRACK@";
  /** Tag to replace with email hash when specifying for REMOVEME section. */
  public static final String EMAIL_HASH_TAG = "@EMAIL_HASH@";
  /** Web application context. */
  private String context;
  /** SMTP host. */
  private String host;
  /** Email address of sender. */
  private String sender;
  /** Email addresses of recipients. */
  private Collection<String> recipients;
  /** Map of tags/replacements for email content. */
  private Map<String, String> map;
  /** Email template processor. */
  private EmailTemplate mailer;
  /** Flag indicating whether setup failed. */
  private boolean failedSetup;
  private String urlScheme="http";

  /**
   * Creates a new NotificationMailer instance.
   *
   * @param context webapp context
   * @param langCode language code for template loading (defaults to &quot;en&quot;)
   * @param to email recipients
   * @param types list of email types to try ((e.g. [<em>individualAddEncounter-auto</em>, <em>individualAddEncounter</em>])
   * @param map map of search/replace strings for email template (if order is important, supply {@code LinkedHashMap}
   */
  public NotificationMailer(String context, String langCode, Collection<String> to, List<String> types, Map<String, String> map) {
    Objects.requireNonNull(context);
    Objects.requireNonNull(to);
    for (String s : to) {
      if (s == null || "".equals(s.trim()))
        throw new IllegalArgumentException("Invalid email TO address specified");
    }
    System.out.println("NoteMailerHere2");
    this.context = context;
    this.sender = CommonConfiguration.getAutoEmailAddress(context);
    System.out.println("Send this email to: "+to);
    System.out.println("This email is from: "+sender);
    this.recipients = to;
    this.host = CommonConfiguration.getMailHost(context);
    boolean useSSL = CommonConfiguration.getMailHostSslOption(context);
    String mailAuth = CommonConfiguration.getMailAuth(context);
    String[] mAuth = null;
    if (mailAuth != null && mailAuth.contains(":"))
      mAuth = mailAuth.split(":", 2);
    try {
      mailer = loadEmailTemplate(langCode, types);
      System.out.println("About to set email host:" + host);
      mailer.setHost(host, useSSL);
      if (mAuth != null)
        mailer.setUseAuth(true, mAuth[0], mAuth[1]);
      // Can also set port/SSL/etc. here if needed.
      // Perform tag replacements.
      System.out.println("About to perform string replacements");

      if (map != null) {
        for (Map.Entry<String, String> me : map.entrySet()) {
          try {
            mailer.replace(me.getKey(), me.getValue() == null ? "" : me.getValue());
          }
          catch (IllegalStateException ex) {
            // Additional safe-guard for when key's value is missing in some map implementations.
            ex.printStackTrace();
          }
        }
        // Remove REMOVEME section when not applicable (i.e. no hashed email info).
        if (map.containsKey("@URL_LOCATION@") && map.containsKey(EMAIL_HASH_TAG) && map.containsKey(EMAIL_NOTRACK)) {
          mailer.replaceInPlainText("@REMOVEME_START@", null, false);
          mailer.replaceInPlainText("@REMOVEME_END@", null, false);
          if (mailer.hasHtmlText()) {
            mailer.replaceInHtmlText("<!--@REMOVEME_START@-->", null, false);
            mailer.replaceInHtmlText("<!--@REMOVEME_END@-->", null, false);
          }
          // Extra layer to help prevent chance of URL spoof attacks.
          String noTrack = map.get(EMAIL_NOTRACK);
          if (noTrack.matches("([a-z]+)=(.+)")) {
            String link = String.format(urlScheme+"://%s/DontTrack?%s&email=%s", map.get("@URL_LOCATION@"), noTrack, map.get(EMAIL_HASH_TAG));
            mailer.replace("@REMOVEME_LINK@", link, true);
          }
        } else {
          mailer.replaceRegexInPlainText("(?s)@REMOVEME_START@.*@REMOVEME_END@", null, false);
          if (mailer.hasHtmlText())
            mailer.replaceRegexInHtmlText("(?s)<!--@REMOVEME_START@.*@REMOVEME_END@-->", null, false);
        }
      }
      System.out.println("String replacement done!");

    } catch (IOException ex) {
      // Logged/flagged as error to avoid interrupting client code processing.
      ex.printStackTrace();
      log.error(ex.getMessage(), ex);
      failedSetup = true;
    }
  }

  /**
   * Creates a new NotificationMailer instance.
   *
   * @param context webapp context
   * @param langCode language code for template loading
   * @param to email recipients
   * @param type email type ((e.g. <em>individualAddEncounter</em>)
   * @param map map of search/replace strings for email template (if order is important, supply {@code LinkedHashMap}
   */
  public NotificationMailer(String context, String langCode, Collection<String> to, String type, Map<String, String> map) {
    this(context, langCode, to, Arrays.asList(type), map);
  }

  /**
   * Creates a new NotificationMailer instance.
   *
   * @param context webapp context
   * @param langCode language code for template loading
   * @param to email recipient
   * @param types list of email types to try ((e.g. [<em>individualAddEncounter-auto</em>, <em>individualAddEncounter</em>])
   * @param map map of search/replace strings for email template (if order is important, supply {@code LinkedHashMap}
   */
  public NotificationMailer(String context, String langCode, String to, List<String> types, Map<String, String> map) {
    this(context, langCode, Arrays.asList(to), types, map);
  }

  /**
   * Creates a new NotificationMailer instance.
   *
   * @param context webapp context
   * @param langCode language code for template loading
   * @param to email recipient
   * @param type email type ((e.g. <em>individualAddEncounter</em>)
   * @param map map of search/replace strings for email template (if order is important, supply {@code LinkedHashMap}
   */
  public NotificationMailer(String context, String langCode, String to, String type, Map<String, String> map) {
    this(context, langCode, Arrays.asList(to), type, map);
  }

  /**
   * Creates a new NotificationMailer instance.
   * If the <em>type</em> parameter is null, the specified <em>text</em> is
   * placed directly into the standard email template, instead of also loading
   * an type-template.
   * @param context webapp context
   * @param langCode language code for template loading
   * @param to email recipients
   * @param type email type ((e.g. <em>individualAddEncounter</em>)
   * @param text text with which to replace standard content tag
   */
  public NotificationMailer(String context, String langCode, Collection<String> to, String type, final String text) {
    this(context, langCode, to, type, new HashMap<String, String>(){{ put(STANDARD_CONTENT_TAG, text);
      System.out.println("NoteMailerHere1");
    }});
  }

  /**
   * Creates a new NotificationMailer instance.
   * If the <em>type</em> parameter is null, the specified <em>text</em> is
   * placed directly into the standard email template, instead of also loading
   * an type-template.
   *
   * @param context webapp context
   * @param langCode language code for template loading
   * @param to email recipient
   * @param type email type ((e.g. <em>individualAddEncounter</em>)
   * @param text text with which to replace standard content tag
   */
  public NotificationMailer(String context, String langCode, String to, String type, final String text) {
    this(context, langCode, Arrays.asList(to), type, text);
  }

  /**
   * Removes any initialized HTML text assigned to this mailer.
   * This can be called to ensure a plain text only email is sent.
   */
  public void removeHtmlText() {
    mailer.removeHtmlText();
  }

  /**
   * Checks if an email template for the specified email type exists.
   *
   * @param langCode language code for template loading
   * @param type string specifying type of email (e.g. <em>individualAddEncounter</em>)
   * @return true if the template type exists, false otherwise
   */
  public boolean existsEmailTemplate(String langCode, String type) {
    try {
      return resolveTemplatesFromRoot(langCode, type)[0] != null;
    } catch (IOException ex) {
      return false;
    }
  }

  /**
   * Loads an email template from the specified email types.
   * This method traverses the list of types until one is found that provides
   * a valid email template.
   *
   * @param langCode language code for template loading
   * @param types collection of types of email (e.g. <em>individualAddEncounter</em>)
   * @return {@code EmailTemplate} instance
   */
  private EmailTemplate loadEmailTemplate(String langCode, List<String> types) throws IOException {
    System.out.println("NoteMailerHere4 and types are: "+types.toString());

    if (langCode != null && !"".equals(langCode.trim())) {
      for (String type : types) {
        if (existsEmailTemplate(langCode, type))
          return loadEmailTemplate(langCode, type);
      }
    }
    // Default to "en" if none found yet.
    for (String type : types) {
      if (existsEmailTemplate("en", type))
        return loadEmailTemplate("en", type);
    }
    throw new FileNotFoundException("Failed to find valid email template in specified types");
  }

  /**
   * Loads an email template for the specified email type.
   * An email template references two files, one for each of plain/HTML text.
   *
   * @param langCode language code for template loading
   * @param type string specifying type of email (e.g. <em>individualAddEncounter</em>)
   * @return {@code EmailTemplate} instance
   */
  private EmailTemplate loadEmailTemplate(String langCode, String type) throws IOException {
    System.out.println("NoteMailerHere3 and type is: "+type);
    // Load generic email template for context.
    File[] fBase = resolveTemplatesFromRoot(langCode, BASE_TEMPLATE_ROOT);
    if (fBase[0] == null || !fBase[0].isFile())
      throw new FileNotFoundException(String.format("Failed to find core plain text email template: %s.txt", BASE_TEMPLATE_ROOT));
    if (fBase[1] == null || !fBase[1].isFile()) {
      log.trace(String.format("Failed to find core HTML text email template: %s.html", BASE_TEMPLATE_ROOT));
      fBase[1] = null;
    }

    EmailTemplate template = EmailTemplate.load(fBase[0], fBase[1], CHARSET_PLAIN, CHARSET_HTML);

    // Load content relating to specified email type.
    if (type != null) {
      File[] fCont = resolveTemplatesFromRoot(langCode, type);
      if (fCont[0] == null || !fCont[0].isFile())
        throw new FileNotFoundException(String.format("Failed to find plain text email template: %s.txt", type));
      if (fCont[1] == null || !fCont[1].isFile()) {
        log.trace(String.format("Failed to find HTML text email template: %s.html", type));
        fCont[1] = null;
        template.removeHtmlText();
      }
      // Place content in base template.
      String pt = TemplateFiller.loadTextFromFile(fCont[0]);
      String[] subjAndBody = EmailTemplate.extractSubjectLine(pt);
      if (subjAndBody[0] != null) {
        template.setSubject(subjAndBody[0]);
        template.setPlainText(subjAndBody[1], template.getPlainTextCharset());
      }
      String ht = (fCont[1] == null) ? null : TemplateFiller.loadTextFromFile(fCont[1]);
      template.replaceInPlainText(BASE_CONTENT_TAG, pt, false);
      if (template.hasHtmlText())
        template.replaceInHtmlText(BASE_CONTENT_TAG, ht, false);
    } else {
      // Place content in base template.
      template.replaceInPlainText(BASE_CONTENT_TAG, STANDARD_CONTENT_TAG, false);
      if (template.hasHtmlText())
        template.replaceInHtmlText(BASE_CONTENT_TAG, STANDARD_CONTENT_TAG, false);
    }

    // Return template.
    return template;
  }

  /**
   * Resolves mail template files using the specified filename base.
   * Template files are assumed to have the extensions &quot;.txt&quot;
   * and &quot;.html&quot; (or &quot;.htm&quot;) for plain text
   * and HTML messages respectively.
   * This method doesn't attempt to load the files, just resolves them to
   * {@code File} instances based on name and existence.
   *
   * @param langCode language code for template loading
   * @param baseName base name of email template files to load
   * @return pair (as 2-element array) of files (plain text, HTML text)
   * @throws IOException if a problem occurs in locating the template files
   */
  private File[] resolveTemplatesFromRoot(String langCode, String baseName) throws IOException {
    Objects.requireNonNull(langCode);
    Objects.requireNonNull(baseName);
    String s = baseName + ".txt";
    File f = ServletUtilities.findResourceOnFileSystem(String.format("%s/%s/%s", SEARCH_PATH, langCode, s));
    if (f == null) {
      s = baseName + ".TXT";
      f = ServletUtilities.findResourceOnFileSystem(String.format("%s/%s/%s", SEARCH_PATH, langCode, s));
    }
    if (f == null)
      throw new FileNotFoundException(String.format("Failed to find plain text email template: %s.txt", baseName));
    return EmailTemplate.resolveTemplatesFromRoot(f.getParentFile(), baseName);
  }

  /**
   * Performs a string search/replace on the subject and body of the template.
   * This method is a convenience to perform all replacements throughout.
   *
   * @param search term to find in both subject and body
   * @param replace term to substitute when a match is found
   */
  public void replace(String search, String replace) {
    mailer.replace(search, replace, true);
  }

  /**
   * Searches and replaces one or all occurrences of the specified regular
   * expression search term with the specified replacement.
   * This method is a convenience to perform all replacements throughout.
   * @param search regex search term
   * @param replace regex replacement term
   */
  public void replaceRegex(String search, String replace) {
    mailer.replaceRegex(search, replace, 0, true);
  }

  /**
   * Performs a string search/replace on the plain text message body template.
   *
   * @param search term to find
   * @param replace term to substitute
   */
  public void replaceInPlainText(String search, String replace) {
    mailer.replaceInPlainText(search, replace, true);
  }

  /**
   * Performs a regex search/replace on the plain text message body template.
   *
   * @param search regex search term
   * @param replace regex replacement term
   */
  public void replaceRegexInPlainText(String search, String replace) {
    mailer.replaceRegexInPlainText(search, replace, true);
  }

  /**
   * Performs a string search/replace on the HTML message body template.
   *
   * @param search term to find in both subject and body
   * @param replace term to substitute when a match is found
   */
  public void replaceInHtmlText(String search, String replace) {
    mailer.replaceInHtmlText(search, replace, true);
  }

  /**
   * Performs a regex search/replace on the HTML message body template.
   *
   * @param search regex search term
   * @param replace regex replacement term
   */
  public void replaceRegexInHtmlText(String search, String replace) {
    mailer.replaceRegexInHtmlText(search, replace, true);
  }

  @Override
  public void run() {
    if (failedSetup) {
      log.info("*** Not processing email as setup failed; see previous error log. ***");
      return;
    }
    if (CommonConfiguration.sendEmailNotifications(context)) {
      if (!"".equals(host.trim()) && !"none".equalsIgnoreCase(host)) {
        try {
          mailer.sendSingle(sender, recipients);
        }
        catch (Exception ex) {
          ex.printStackTrace();
          log.error("Error sending notification email", ex);
          log.error("     from: " + sender);
          log.error("     to  : " + EmailTemplate.join(",", recipients));
        }
      }
    }
  }

  /**
   * Creates a basic tag map for the specified adoption.
   * This map can subsequently be enhanced with extra tags.
   * Adoption tags included:
   * <ul>
   * <li>&#64;INDIVIDUAL_LINK&#64;</li>
   * <li>&#64;INDIVIDUAL_ID&#64;</li>
   * <li>&#64;INDIVIDUAL_ALT_ID&#64;</li>
   * <li>&#64;INDIVIDUAL_SEX&#64;</li>
   * <li>&#64;INDIVIDUAL_NAME&#64;</li>
   * <li>&#64;INDIVIDUAL_NICKNAME&#64;</li>
   * <li>&#64;INDIVIDUAL_NICKNAMER&#64;</li>
   * <li>&#64;INDIVIDUAL_COMMENTS&#64;</li>
   * </ul>
   *
   * @param req servlet request for data reference
   * @param ind MarkedIndividual for which to add tag data
   * @return map instance for tag replacement in email template
   */
  public static Map<String, String> createBasicTagMap(HttpServletRequest req, Adoption adp, String scheme) {
    Map<String, String> map = new HashMap<>();
    addTags(map, req, adp, scheme);
    return map;
  }

  /**
   * Creates a basic tag map for the specified encounter.
   * This map can subsequently be enhanced with extra tags.
   * Individual tags included:
   * <ul>
   * <li>&#64;INDIVIDUAL_LINK&#64;</li>
   * <li>&#64;INDIVIDUAL_ID&#64;</li>
   * <li>&#64;INDIVIDUAL_ALT_ID&#64;</li>
   * <li>&#64;INDIVIDUAL_SEX&#64;</li>
   * <li>&#64;INDIVIDUAL_NAME&#64;</li>
   * <li>&#64;INDIVIDUAL_NICKNAME&#64;</li>
   * <li>&#64;INDIVIDUAL_NICKNAMER&#64;</li>
   * <li>&#64;INDIVIDUAL_COMMENTS&#64;</li>
   * </ul>
   *
   * @param req servlet request for data reference
   * @param ind MarkedIndividual for which to add tag data
   * @return map instance for tag replacement in email template
   */
  public static Map<String, String> createBasicTagMap(HttpServletRequest req, MarkedIndividual ind) {
    Map<String, String> map = new HashMap<>();
    addTags(map, req, ind, req.getScheme());
    return map;
  }

  /**
   * Creates a basic tag map for the specified encounter.
   * This map can subsequently be enhanced with extra tags.
   * Encounter tags included:
   * <ul>
   * <li>&#64;ENCOUNTER_LINK&#64;</li>
   * <li>&#64;ENCOUNTER_ID&#64;</li>
   * <li>&#64;ENCOUNTER_ALT_ID&#64;</li>
   * <li>&#64;ENCOUNTER_INDIVIDUALID&#64;</li>
   * <li>&#64;ENCOUNTER_DATE&#64;</li>
   * <li>&#64;ENCOUNTER_LOCATION&#64;</li>
   * <li>&#64;ENCOUNTER_LOCATIONID&#64;</li>
   * <li>&#64;ENCOUNTER_SEX&#64;</li>
   * <li>&#64;ENCOUNTER_LIFE_STAGE&#64;</li>
   * <li>&#64;ENCOUNTER_COUNTRY&#64;</li>
   * <li>&#64;ENCOUNTER_SUBMITTER_NAME&#64;</li>
   * <li>&#64;ENCOUNTER_SUBMITTER_ID&#64;</li>
   * <li>&#64;ENCOUNTER_SUBMITTER_EMAIL&#64;</li>
   * <li>&#64;ENCOUNTER_SUBMITTER_ORGANIZATION&#64;</li>
   * <li>&#64;ENCOUNTER_SUBMITTER_PROJECT&#64;</li>
   * <li>&#64;ENCOUNTER_PHOTOGRAPHER_NAME&#64;</li>
   * <li>&#64;ENCOUNTER_PHOTOGRAPHER_EMAIL&#64;</li>
   * <li>&#64;ENCOUNTER_COMMENTS&#64;</li>
   * <li>&#64;ENCOUNTER_USER&#64;</li>
   * </ul>
   *
   * @param req servlet request for data reference
   * @param enc Encounter for which to add tag data
   * @return map instance for tag replacement in email template
   */
  public static Map<String, String> createBasicTagMap(HttpServletRequest req, Encounter enc) {
    Map<String, String> map = new HashMap<>();
    addTags(map, req, enc,req.getScheme());
    return map;
  }

  /**
   * Creates a basic tag map for the specified encounter.
   * This map can subsequently be enhanced with extra tags.
   * Tags included are the union of those added by
   * {@link #addTags(Map, HttpServletRequest, MarkedIndividual)}
   * and {@link #addTags(Map, HttpServletRequest, Encounter)}.
   *
   * @param req servlet request for data reference
   * @param ind MarkedIndividual for which to add tag data
   * @param enc Encounter for which to add tag data
   * @return map instance for tag replacement in email template
   */
  public static Map<String, String> createBasicTagMap(HttpServletRequest req, MarkedIndividual ind, Encounter enc, String scheme) {
    Map<String, String> map = new HashMap<>();
    addTags(map, req, ind, scheme);
    addTags(map, req, enc, scheme);
    return map;
  }
  
  public static Map<String, String> createBasicTagMap(HttpServletRequest req, MarkedIndividual ind, Encounter enc) {
    return createBasicTagMap(req, ind, enc,req.getScheme());
  }

  public static Map<String, String> createBasicTagMap(HttpServletRequest req, MarkedIndividual ind, Adoption adp, String scheme) {
    Map<String, String> map = new HashMap<>();
    addTags(map, req, ind,scheme);
    addTags(map, req, adp,scheme);
    return map;
  }
  
  public static Map<String, String> createBasicTagMap(HttpServletRequest req, MarkedIndividual ind, Adoption adp) {
    return createBasicTagMap(req, ind, adp, req.getScheme());
  }

  public static Map<String, String> createBasicTagMap(HttpServletRequest req, Encounter enc, Adoption adp, String scheme) {
    Map<String, String> map = new HashMap<>();
    addTags(map, req, enc,scheme);
    addTags(map, req, adp,scheme);
    return map;
  }

  public static Map<String, String> createBasicTagMap(HttpServletRequest req, Encounter enc, Adoption adp, MarkedIndividual ind, String scheme) {
    Map<String, String> map = new HashMap<>();
    addTags(map, req, enc, scheme);
    addTags(map, req, adp, scheme);
    addTags(map, req, ind,scheme);
    return map;
  }

  /**
   * Adds info tags for the specified encounter.
   *
   * @param req servlet request for data reference
   * @param ind MarkedIndividual for which to add tag data
   * @param map map to which to add tag data
   */
  private static void addTags(Map<String, String> map, HttpServletRequest req, MarkedIndividual ind, String scheme) {
    Objects.requireNonNull(map);
    if (!map.containsKey("@URL_LOCATION@"))
      map.put("@URL_LOCATION@", String.format(scheme+"://%s", CommonConfiguration.getURLLocation(req)));
    if (ind != null) {
      map.put("@INDIVIDUAL_LINK@", String.format("%s/individuals.jsp?number=%s", map.get("@URL_LOCATION@"), ind.getIndividualID()));
      map.put("@INDIVIDUAL_ID@", ind.getIndividualID());
      map.put("@INDIVIDUAL_ALT_ID@", ind.getAlternateID());
      map.put("@INDIVIDUAL_SEX@", ind.getSex());
      map.put("@INDIVIDUAL_NAME@", ind.getName());
      map.put("@INDIVIDUAL_NICKNAME@", ind.getNickName());
      map.put("@INDIVIDUAL_NICKNAMER@", ind.getNickNamer());
      map.put("@INDIVIDUAL_COMMENTS@", ind.getComments());
    }
  }

  /**
   * Adds info tags for the specified adoption.
   *
   * @param req servlet request for data reference
   * @param ind Adoption for which to add tag data
   * @param map map to which to add tag data
   */
  private static void addTags(Map<String, String> map, HttpServletRequest req, Adoption adp, String scheme) {
    Objects.requireNonNull(map);
    if (!map.containsKey("@URL_LOCATION@"))
      map.put("@URL_LOCATION@", String.format(scheme+"://%s", CommonConfiguration.getURLLocation(req)));
    if (adp != null) {
      map.put("@ADOPTION_CANCELLATION_LINK@", String.format("%s/adoptions/emailCancelAdoption.jsp?number=%s&stripeID=%s&adoption=%s", map.get("@URL_LOCATION@"), adp.getMarkedIndividual(), adp.getStripeCustomerId(), adp.getID()));
      map.put("@ADOPTION_ALTERATION_LINK@", String.format("%s/adoptions/emailAlterAdoption.jsp?number=%s&stripeID=%s&adoption=%s", map.get("@URL_LOCATION@"), adp.getMarkedIndividual(), adp.getStripeCustomerId(), adp.getID()));
      map.put("@ADOPTION_ID@", adp.getID());
      map.put("@ADOPTION_STRIPE_CUSTOMER_ID@", adp.getStripeCustomerId());
      map.put("@ADOPTER_NAME@", adp.getAdopterName());
      map.put("@ADOPTER_EMAIL@", adp.getAdopterEmail());
      map.put("@ADOPTER_ADDRESS@", adp.getAdopterAddress());
      map.put("@ADOPTER_QUOTE@", adp.getAdopterQuote());
      map.put("@ADOPTION_MANAGER@", adp.getAdoptionManager());
      map.put("@ADOPTION_INDIVIDUAL@", adp.getMarkedIndividual());
      map.put("@ADOPTION_ENCOUNTER@", adp.getEncounter());
      map.put("@ADOPTION_NOTES@", adp.getNotes());
      map.put("@ADOPTION_TYPE@", adp.getAdoptionType());
      map.put("@ADOPTION_START@", adp.getAdoptionStartDate());
    }
  }

  /**
   * Creates a basic tag map for the specified encounter.
   * This map can subsequently be enhanced with extra tags.
   *
   * @param req servlet request for data reference
   * @param enc Encounter for which to add tag data
   * @return map instance for tag replacement in email template
   */
  private static void addTags(Map<String, String> map, HttpServletRequest req, Encounter enc, String scheme) {
    Objects.requireNonNull(map);
    if (!map.containsKey("@URL_LOCATION@"))
      map.put("@URL_LOCATION@", String.format(scheme+"://%s", CommonConfiguration.getURLLocation(req)));
    if (enc != null) {
      // Add useful encounter fields.
      map.put("@ENCOUNTER_LINK@", String.format("%s/encounters/encounter.jsp?number=%s", map.get("@URL_LOCATION@"), enc.getCatalogNumber()));
      map.put("@ENCOUNTER_ID@", enc.getCatalogNumber());
      map.put("@ENCOUNTER_ALT_ID@", enc.getAlternateID());
      map.put("@ENCOUNTER_INDIVIDUALID@", ServletUtilities.handleNullString(enc.getIndividualID()));
      map.put("@ENCOUNTER_DATE@", enc.getDate());
      map.put("@ENCOUNTER_LOCATION@", enc.getLocation());
      map.put("@ENCOUNTER_LOCATIONID@", enc.getLocationID());
      map.put("@ENCOUNTER_SEX@", enc.getSex());
      map.put("@ENCOUNTER_LIFE_STAGE@", enc.getLifeStage());
      map.put("@ENCOUNTER_COUNTRY@", enc.getCountry());
      map.put("@ENCOUNTER_SUBMITTER_NAME@", enc.getSubmitterName());
      map.put("@ENCOUNTER_SUBMITTER_ID@", enc.getSubmitterID());
      map.put("@ENCOUNTER_SUBMITTER_EMAIL@", enc.getSubmitterEmail());
      map.put("@ENCOUNTER_SUBMITTER_ORGANIZATION@", enc.getSubmitterOrganization());
      map.put("@ENCOUNTER_SUBMITTER_PROJECT@", enc.getSubmitterProject());
      map.put("@ENCOUNTER_PHOTOGRAPHER_NAME@", enc.getPhotographerName());
      map.put("@ENCOUNTER_PHOTOGRAPHER_EMAIL@", enc.getPhotographerEmail());
      map.put("@ENCOUNTER_COMMENTS@", enc.getComments());
      map.put("@ENCOUNTER_USER@", enc.getAssignedUsername());
    }
  }

  /**
   * Splits a comma-separated string of email addresses.
   * @param cs comma-separated string of email addresses
   * @return list of strings
   */
  public static List<String> splitEmails(String cs) {
    if (cs == null)
      return Collections.EMPTY_LIST;
    // Conservative checking to avoid potential blank email entries.
    String[] sep = cs.split("\\s*,\\s*");
    List<String> list = new ArrayList<>();
    for (String s : sep) {
      String t = s.trim();
      if (!"".equals(t))
        list.add(t);
    }
    return list;
  }

  /**
   * Joins email addresses into a single string (delimited by &quot;, &quot;.
   * @param c collection of email addresses to join
   * @return comma-separated string
   */
  public static String joinEmails(Collection<String> c) {
    return EmailTemplate.join(", ", c);
  }

  /**
   * Removes all null and whitespace-only entries from the specified collection.
   *
   * @param <T> collection type
   * @param c collection to process
   * @return original collection, with blanks removed
   */
  public static <T extends Collection<String>> T filterBlanks(T c) {
    if (c == null || c.isEmpty())
      return c;
    Collection<String> x = new HashSet<>();
    x.add(null);
    for (String s : c) {
      if (s != null && "".equals(s.trim()))
        x.add(s);
    }
    c.removeAll(x);
    return c;
  }

  /**
   * Appends the specified text to the email subject line.
   * @param text text to append
   */
  public void appendToSubject(String text) {
    Objects.requireNonNull(text);
    String subj = mailer.getSubject();
    mailer.setSubject(subj == null ? text : subj + text);
  }
  
  public void setUrlScheme(String scheme){this.urlScheme=scheme;}
  
}
