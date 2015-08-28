package org.ecocean;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.mail.Address;
import javax.mail.Authenticator;
import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.PasswordAuthentication;
import javax.mail.SendFailedException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * <p>Convenience template mechanism for sending emails using JavaMail.
 * It provides support for both plain text and HTML emails, although HTML
 * emails must only reference remotely hosted images, not use embedded ones.</p>
 *
 * <p>A plain text file is mandatory, but the HTML version is optional.
 * If both are specified, the plain text should be specified first.
 * For HTML emails a MIME-multipart message will be sent using
 * the content of both files. If just the text version is present then a
 * simple plain text email will be sent.</p>
 *
 * @author Giles Winstanley
 * @see <a href="http://www.oracle.com/technetwork/java/javamail/">Oracle JavaMail page</a>
 * @see <a href="https://javamail.java.net/">JavaMail API project on Java.net</a>
 */
public final class EmailTemplate {
  /** SLF4J logger instance for writing log entries. */
  private static final Logger log = LoggerFactory.getLogger(EmailTemplate.class);
  /** Default character set encoding for email body texts. */
  private static final Charset DEFAULT_CHARSET = Charset.forName("ISO-8859-1");
  /** Template for message subject. */
  private TemplateFiller subj;
  /** Template for plain text message body. */
  private TemplateFiller plainBody;
  /** Template for HTML message body. */
  private TemplateFiller htmlBody;
  /** Character set encoding for plain text. */
  private Charset charsetPlain;
  /** Character set encoding for HTML text. */
  private Charset charsetHTML;
  /** Determines whether to show debug information. */
  private boolean debug = false;
  /** {@code Properties} instance to configure {@code Session}. */
  private final Properties props = new Properties();
  /** Mail {@code Session} for communicating with mail server. */
  private Session session;
  /** {@code Transport} instance for communicating with mail server. */
  private Transport transport;
  /** {@code Message} instance. */
  private MimeMessage message;
  /** Determines whether to use SMTP over SSL. */
  private boolean useSSL = false;
  /** Determines whether to use STARTTLS protocol. */
  private boolean useStartTLS = false;
  /** Port number to use on mail server. */
  private int port = 25;
  /** {@code Authenticator} instance to use if SMTP Authentication is required. */
  private SMTPAuthenticator auth;

  /**
   * Creates a new email template, using the subject and files provided
   * The files are used to create body text representations for both the
   * plain text and HTML style emails.
   *
   * @param subj subject header for the email
   * @param plain file containing body text for plain text email
   * @param html file containing body text for HTML email
   * @param csP {@code Charset} for body text (plain text version)
   * @param csH {@code Charset} for body text (HTML text version)
   * @param host host name of SMTP mail server
   * @param port port number of SMTP mail server
   * @param useSSL whether to use SMTP over SSL
   * @throws IOException if any of the input files cannot be loaded
   */
  public EmailTemplate(String subj, File plain, File html, Charset csP, Charset csH, String host, int port, boolean useSSL) throws IOException {
    this.subj = new TemplateFiller(subj);
    this.plainBody = new TemplateFiller(plain);
    if (html != null)
      this.htmlBody = new TemplateFiller(html);
    this.charsetPlain = csP;
    this.charsetHTML = csH;

    setHost(host, useSSL);

    if (port < 0)
      throw new IllegalArgumentException("Invalid port number specified");
    this.port = port;
  }

  /**
   * Creates a new email template, using the subject and files provided
   * The files are used to create body text representations for both the
   * plain text and HTML style emails.
   *
   * @param subj subject header for the email
   * @param plain file containing body text for plain text email
   * @param html file containing body text for HTML email
   * @throws IOException if any of the input files cannot be loaded
   */
  public EmailTemplate(String subj, File plain, File html) throws IOException {
    this.subj = new TemplateFiller(subj);
    this.plainBody = new TemplateFiller(plain);
    if (html != null)
      this.htmlBody = new TemplateFiller(html);
  }

  private EmailTemplate() {}

  private EmailTemplate(String host) {
    this(host, false);
  }

  private EmailTemplate(String host, boolean useSSL) {
    setHost(host, useSSL);
  }

  /**
   * Creates a new email template, using the subject and body provided,
   * which will send a plain text email.
   *
   * @param subject email subject
   * @param body email body text
   * @param cs {@code Charset} for body text
   */
  public EmailTemplate(String subject, String body, Charset cs) {
    this.subj = new TemplateFiller(subject);
    this.plainBody = new TemplateFiller(body);
    charsetPlain = cs;
  }

  /**
   * Creates a new email template, using the subj and body provided,
   * which will send a plain text email using the mail host specified.
   *
   * @param host email server host
   * @param subject email subject
   * @param body email body text
   * @param cs {@code Charset} for body text
   */
  public EmailTemplate(String host, String subject, String body, Charset cs) {
    this(subject, body, cs);
    props.setProperty("mail.smtp.host", host);
  }

  /**
   * Performs a string search/replace on the subject and body of the template.
   *
   * @param search term to find in both subject and body
   * @param replace term to substitute when a match is found
   * @param all whether to perform search throughout, or simply the first match
   */
  public void replace(String search, String replace, boolean all) {
    subj.replace(search, replace, all);
    plainBody.replace(search, replace, all);
    if (htmlBody != null)
      htmlBody.replace(search, replace, all);
  }

  /**
   * Performs a string search/replace on the subject and body of the template.
   * This method is a convenience to perform all replacements throughout.
   *
   * @param search term to find in both subject and body
   * @param replace term to substitute when a match is found
   */
  public void replace(String search, String replace) {
    replace(search, replace, true);
  }

  /**
   * Substitutes a specified string with the contents of a text file.
   *
   * @param search the string for which to search
   * @param replace the text file to use as replacement text
   * @throws IOException if the replacement text file cannot be loaded
   */
  public void replace(String search, File replace) throws IOException {
    replace(search, TemplateFiller.loadTextFromFile(replace));
  }

  /**
   * Performs a string search/replace on the subject and body of the template.
   *
   * @param search regex search term
   * @param replace regex replacement term
   * @param flags regex flags (defined in {@link Pattern})
   * @param all whether to perform search throughout, or simply the first match
   */
  public void replaceRegex(String search, String replace, int flags, boolean all) {
    subj.replaceRegex(search, replace, all);
    plainBody.replaceRegex(search, replace, all);
    if (htmlBody != null)
      htmlBody.replaceRegex(search, replace, all);
  }

  /**
   * Searches and replaces one or all occurrences of the specified regular
   * expression search term with the specified replacement.
   *
   * @param search regex search term
   * @param replace regex replacement term
   * @param all whether to replace all occurrences or just the first
   */
  public void replaceRegex(String search, String replace, boolean all) {
    replaceRegex(search, replace, 0, all);
  }

  /**
   * Searches and replaces one or all occurrences of the specified regular
   * expression search term with the specified replacement.
   * This method is a convenience to perform all replacements throughout.
   *
   * @param search regex search term
   * @param replace regex replacement term
   */
  public void replaceRegex(String search, String replace) {
    replaceRegex(search, replace, 0, true);
  }

  /**
   * Performs a string search/replace on the message subject template.
   *
   * @param search term to find
   * @param replace term to substitute
   * @param all whether to perform search throughout, or simply the first match
   */
  public void replaceInSubject(String search, String replace, boolean all) {
    subj.replace(search, replace, all);
  }

  /**
   * Performs a regex search/replace on the message subject template.
   *
   * @param search regex search term
   * @param replace regex replacement term
   * @param all whether to perform search throughout, or simply the first match
   */
  public void replaceRegexInSubject(String search, String replace, boolean all) {
    subj.replaceRegex(search, replace, all);
  }

  /**
   * Performs a string search/replace on the plain text message body template.
   *
   * @param search term to find
   * @param replace term to substitute
   * @param all whether to perform search throughout, or simply the first match
   */
  public void replaceInPlainText(String search, String replace, boolean all) {
    plainBody.replace(search, replace, all);
  }

  /**
   * Performs a regex search/replace on the plain text message body template.
   *
   * @param search regex search term
   * @param replace regex replacement term
   * @param all whether to perform search throughout, or simply the first match
   */
  public void replaceRegexInPlainText(String search, String replace, boolean all) {
    plainBody.replaceRegex(search, replace, all);
  }

  /**
   * Performs a string search/replace on the HTML message body template.
   *
   * @param search term to find in both subject and body
   * @param replace term to substitute when a match is found
   * @param all whether to perform search throughout, or simply the first match
   */
  public void replaceInHtmlText(String search, String replace, boolean all) {
    if (htmlBody != null)
      htmlBody.replace(search, replace, all);
    else
      throw new IllegalStateException("No HTML message body exists");
  }

  /**
   * Performs a regex search/replace on the HTML message body template.
   *
   * @param search regex search term
   * @param replace regex replacement term
   * @param all whether to perform search throughout, or simply the first match
   */
  public void replaceRegexInHtmlText(String search, String replace, boolean all) {
    if (htmlBody != null)
      htmlBody.replaceRegex(search, replace, all);
    else
      throw new IllegalStateException("No HTML message body exists");
  }

  /**
   * Resets the template content to the base-state.
   * This allows the template to be re-used multiple times.
   */
  public void reset() {
    subj.reset();
    plainBody.reset();
    if (htmlBody != null)
      htmlBody.reset();
  }

  /**
   * Sets the base-state of the email template to the current content.
   * The base state is the state to which the template reverts when the
   * {@link #reset()} method is called.
   */
  public void setBaseState() {
    subj.setBaseState();
    plainBody.setBaseState();
    if (htmlBody != null)
      htmlBody.setBaseState();
  }

  /**
   * Opens the mail session ready to send multiple emails using the same
   * connection, before calling {@code closeSession()} at the end.
   *
   * @param from default email <strong>From:</strong> header field
   * @return true if session successfully opened, false otherwise
   */
  public boolean openSession(String from) {
    try {
      // Setup properties ready for obtaining session.
      String protocol = useSSL ? "smtps" : "smtp";
      props.setProperty("mail.from", from);
      if (useStartTLS)
        props.setProperty("mail." + protocol + ".starttls.enable", Boolean.toString(useStartTLS));
      // Obtain/configure session for sending messages.
      session = Session.getInstance(props, auth);
      session.setDebug(isDebug());
      if (useSSL)
        session.setProtocolForAddress("rfc822", "smtps");
      message = new MimeMessage(session);
      // Obtain/connect message transport.
      transport = session.getTransport(protocol);
			if (auth != null)
				transport.connect(auth.pa.getUserName(), auth.pa.getPassword());
			else
				transport.connect();
      return true;
    }
    catch (MessagingException me) {
      log.debug(me.getMessage(), me);
      return false;
    }
  }

  /**
   * Closes the mail session.
   *
   * @return true if session successfully opened, false otherwise
   */
  public boolean closeSession() {
    try {
      transport.close();
      return true;
    }
    catch (MessagingException me) {
      log.debug(me.getMessage(), me);
      return false;
    }
  }

  /**
   * Sends this email to the recipient specified.
   *
   * @param from email sender
   * @param to email recipients
   * @param cc email recipients (CC)
   * @param bcc email recipients (BCC)
   * @param html email HTML content
   * @return true if successful, otherwise false
   */
  public int send(String from, Collection<String> to, Collection<String> cc, Collection<String> bcc, boolean html) {
    String old = props.getProperty("mail.from");
    props.setProperty("mail.from", from);
    int ok = send(to, cc, bcc, html);
    props.setProperty("mail.from", old);
    return ok;
  }

  /**
   * Sends this email to the recipient specified.
   *
   * @param from email sender
   * @param to email recipients
   * @param cc email recipients (CC)
   * @param bcc email recipients (BCC)
   * @return true if successful, otherwise false
   */
  public int send(String from, Collection<String> to, Collection<String> cc, Collection<String> bcc) {
		return send(from, to, cc, bcc, hasHtmlText());
  }

  /**
   * Sends this email to the recipient specified.
   *
   * @param from email sender
   * @param to email recipients
   * @param html email HTML content
   * @return true if successful, otherwise false
   */
  public int send(String from, Collection<String> to, boolean html) {
		return send(from, to, null, null, html);
  }

  /**
   * Sends this email to the recipient specified.
   *
   * @param from email sender
   * @param to email recipients
   * @return true if successful, otherwise false
   */
  public int send(String from, Collection<String> to) {
		return send(from, to, null, null, hasHtmlText());
  }

  /**
   * Sends this email to the recipient specified.
   *
   * @param from email sender
   * @param to email recipient
   * @param html email HTML content
   * @return true if successful, otherwise false
   */
  public boolean send(String from, String to, boolean html) {
    String old = props.getProperty("mail.from");
    props.setProperty("mail.from", from);
    int ok = send(Arrays.asList(to), null, null, html);
    props.setProperty("mail.from", old);
    return ok > 0;
  }

  /**
   * Sends this email to the recipient specified.
   *
   * @param from email sender
   * @param to email recipient
   * @return true if successful, otherwise false
   */
  public boolean send(String from, String to) {
    return send(from, to, hasHtmlText());
  }

  /**
   * Sends this email to the recipients specified.
   * If an HTML format has been included in the template, then a call
   * with the {@code html} parameter as {@code true} will attempt
   * to send the email as a multipart/alternative MIME type.
   *
   * @param to email recipients
   * @param cc email recipients (CC)
   * @param bcc email recipients (BCC)
   * @param html whether to try to send in HTML format
   * @return number of successful messages sent
   */
  private int send(Collection<String> to, Collection<String> cc, Collection<String> bcc, boolean html) {
    if (to == null || to.isEmpty())
      throw new IllegalArgumentException();
    try {
			Address[] addr = convertAddresses(to);
      message.setRecipients(Message.RecipientType.TO, addr);
			if (cc != null && !cc.isEmpty())
				message.setRecipients(Message.RecipientType.CC, convertAddresses(cc));
			if (bcc != null && !bcc.isEmpty())
				message.setRecipients(Message.RecipientType.BCC, convertAddresses(bcc));

			message.setSubject(subj.getText(), getPlainTextCharset().name());
      if (html && hasHtmlText())
        message.setContent(createMultipartEmail());
      else
        message.setText(plainBody.getText(), getHtmlTextCharset().name());
      message.setSentDate(new Date());
      message.saveChanges();
      transport.sendMessage(message, message.getAllRecipients());
      return message.getAllRecipients().length;
    }
    catch (SendFailedException sfe) {
      Address[] vsa = sfe.getValidSentAddresses();
      return (vsa != null) ? vsa.length : 0;
    }
    catch (MessagingException me) {
      log.debug(me.getMessage(), me);
      return 0;
    }
  }

	/**
	 * Converts a list of email addresses from strings to an {@code Address} array.
	 * @param x list of email addresses
	 * @return array of {@code Address} objects
	 * @throws AddressException if thrown when creating {@code Address} instances
 	 */
	private Address[] convertAddresses(Collection<String> x) throws AddressException {
    List<Address> list = new ArrayList<>(x.size());
		for (String s : x) {
      try {
        if (s != null && !"".equals(s.trim()))
          list.add(new InternetAddress(s.trim()));
        else
          log.warn("Invalid email address; ignoring: " + s);
      } catch (AddressException ex) {
        log.warn("Failed to convert email address: " + s);
      }
    }
		return list.toArray(new Address[list.size()]);
	}

  /**
   * Sends this email to the recipients specified.
   * If an HTML format has been included in the template, then a call
   * with the {@code html} parameter as {@code true} will attempt
   * to send the email as a multipart/alternative MIME type.
   *
   * @param from email sender
   * @param to email recipients
   * @param cc email recipients (CC)
   * @param bcc email recipients (BCC)
   * @param html whether to try to send in HTML format (will default to plain text if not possible)
   * @return number of successful messages sent
   */
  public boolean sendSingle(String from, Collection<String> to, Collection<String> cc, Collection<String> bcc, boolean html) {
    if (from == null || "".equals(from.trim()))
      throw new IllegalArgumentException();
    if (to == null || to.isEmpty())
      throw new IllegalArgumentException();
    ByteArrayOutputStream debugOS = null;
    PrintStream debugPS = null;
    try {
      Session sess = Session.getDefaultInstance(props, auth);
      if (isDebug()) {
        sess.setDebug(true);
        debugOS = new ByteArrayOutputStream();
        debugPS = new PrintStream(debugOS);
        sess.setDebugOut(debugPS);
      }
      MimeMessage m = new MimeMessage(sess);
      m.setFrom(new InternetAddress(from));

			m.setRecipients(Message.RecipientType.TO, convertAddresses(to));
			if (cc != null && !cc.isEmpty())
				m.setRecipients(Message.RecipientType.CC, convertAddresses(cc));
			if (bcc != null && !bcc.isEmpty())
				m.setRecipients(Message.RecipientType.BCC, convertAddresses(bcc));

      m.setSubject(subj.getText(), getPlainTextCharset().name());
      if (html && hasHtmlText())
        m.setContent(createMultipartEmail());
      else
        m.setText(plainBody.getText(), getPlainTextCharset().name());
      m.saveChanges();

      boolean ok = openSession(from);
      if (ok)
        transport.sendMessage(m, m.getAllRecipients());
      if (debugOS != null)
        log.debug(debugOS.toString());
      return true;
    }
    catch (MessagingException me) {
      log.debug("Error sending email to: " + to);
      log.debug(me.getMessage(), me);
      return false;
    }
    finally {
      closeSession();
      if (debugPS != null)
        debugPS.close();
    }
  }

  /**
   * Sends this email to the recipients specified.
   * If an HTML format has been included in the template, then a call
   * with the {@code html} parameter as {@code true} will attempt
   * to send the email as a multipart/alternative MIME type.
   *
   * @param from email sender
   * @param to email recipients
   * @param cc email recipients (CC)
   * @param bcc email recipients (BCC)
   * @return number of successful messages sent
   */
  public boolean sendSingle(String from, Collection<String> to, Collection<String> cc, Collection<String> bcc) {
		return sendSingle(from, to, cc, bcc, hasHtmlText());
	}

  /**
   * Sends this email to the recipient specified.
   *
   * @param from email sender
   * @param to email recipient
   * @return number of successful messages sent
   */
  public boolean sendSingle(String from, Collection<String> to) {
    return sendSingle(from, to, null, null, hasHtmlText());
  }

  /**
   * Sends this email to the recipient specified.
   *
   * @param from email sender
   * @param to email recipient
   * @param html whether to try to send in HTML format
   * @return number of successful messages sent
   */
  public boolean sendSingle(String from, String to, boolean html) {
    return sendSingle(from, Arrays.asList(to), null, null, html);
  }

  /**
   * Sends this email to the recipient specified.
   *
   * @param from email sender
   * @param to email recipient
   * @return number of successful messages sent
   */
  public boolean sendSingle(String from, String to) {
    return sendSingle(from, to, hasHtmlText());
  }

  /**
   * Creates an email in multipart/alternative format to cater for both
   * HTML and text-only email clients.
   *
   * @return Multipart instance
   * @throws MessagingException if a problem occurs while creating the message
   */
  private Multipart createMultipartEmail() throws MessagingException {
    // Create multipart.
    Multipart mp = new MimeMultipart("alternative");

    // Add plain text content.
    BodyPart part = new MimeBodyPart();
    part.setContent(plainBody.getText(), String.format("text/plain; charset=\"%s\"", getPlainTextCharset().name()));
    mp.addBodyPart(part);

    // Add HTML content.
    part = new MimeBodyPart();
    part.setContent(htmlBody.getText(), String.format("text/html; charset=\"%s\"", getHtmlTextCharset().name()));
    mp.addBodyPart(part);

    return mp;
  }

  /**
   * Resolves mail template files using the specified filename base.
   * Template files are assumed to have the extensions &quot;.txt&quot;
   * and &quot;.html&quot; (or &quot;.htm&quot;) for plain text
   * and HTML messages respectively.
   * This method doesn't attempt to load the files, just resolves them to
   * {@code File} instances based on name and existence.
   *
   * @param path folder path to use to locate files
   * @param baseName base name of email template files to load
   * @return pair (as 2-element array) of files (plain text, HTML text)
   * @throws IOException if a problem occurs in locating the template files
   */
  public static File[] resolveTemplatesFromRoot(File path, String baseName) throws IOException {
    Objects.requireNonNull(path);
    Objects.requireNonNull(baseName);
    File fP = getTemplateFile(path, baseName, new String[]{".txt",".TXT",""});
    File fH = getTemplateFile(path, baseName, new String[]{".html",".HTML",".htm",".HTM"});
    return new File[]{fP, fH};
  }

  /**
   * Resolves mail template files using the specified filename base.
   * Template files are assumed to have the extensions &quot;.txt&quot;
   * and &quot;.html&quot; (or &quot;.htm&quot;) for plain text
   * and HTML messages respectively.
   * This method doesn't attempt to load the files, just resolves them to
   * {@code File} instances based on name and existence.
   *
   * @param path folder path to use to locate files
   * @param baseName base name of email template files to load
   * @return pair of files (plain text, HTML text)
   * @throws IOException if a problem occurs in locating the template files
   */
  public static File[] resolveTemplatesFromRoot(String path, String baseName) throws IOException {
    return resolveTemplatesFromRoot(new File(path), baseName);
  }

  /**
   * Loads a mail template using the specified pair of files, specified as
   * plain text and HTML text in order.
   *
   * @param fP plain text email template file
   * @param fH HTML text email template file
   * @param csP {@code Charset} for plain text file
   * @param csH {@code Charset} for HTML text file
   * @return EmailTemplate instance ready for use
   * @throws IOException if a problem occurs in loading the template
   */
  public static EmailTemplate load(File fP, File fH, Charset csP, Charset csH) throws IOException {
    Objects.requireNonNull(fP);
    if (!fP.exists())
      throw new IllegalArgumentException("Invalid file specified: " + fP.getCanonicalPath());

    // Process plain text file for subject line.
    String pt = join("\n", Files.readAllLines(fP.toPath(), csP));
    String[] subjAndBody = extractSubjectLine(pt);
    EmailTemplate x = new EmailTemplate(subjAndBody[0], subjAndBody[1], csP);

    if (fH != null) {
      if (!fH.exists())
        throw new IllegalArgumentException("Invalid file specified: " + fH.getCanonicalPath());
      List<String> ht = Files.readAllLines(fH.toPath(), csH);

      // Perform quick check for matching HTML page charset definition.
      Pattern pat = Pattern.compile("[<\\s]meta\\s.*\\scharset\\s*=\\s*\"?([^\"]+)\"?", Pattern.CASE_INSENSITIVE);
      for (String s : ht) {
        if (s.toLowerCase(Locale.US).contains("charset")) {
          Matcher m = pat.matcher(s);
          if (m.find()) {
            String cs = m.group(1).trim();
            if (!cs.equalsIgnoreCase(csH.name())) {
              log.warn(String.format("Found HTML charset mismatch; %s (page specifies: %s): %s", csH.name(), cs, fH.getCanonicalPath()));
            }
          }
        }
        break;
      }
			x.setHtmlText(new TemplateFiller(join("\n", ht)), csH);
		}

		return x;
  }

  /**
   * Attempts to extract an email subject line from the specified text, which
   * should be prefixed with &quot;SUBJECT:&quot;. If found, the subject line
   * of the email is assigned, and the text returned has the subject line
   * removed.
   * @param text text to examine for subject line
   * @return two-element array of subject line and remaining text (subject may be null of not found)
   */
  static String[] extractSubjectLine(String text) {
    log.trace(text);
    final Pattern p = Pattern.compile("^SUBJECT:(.*)$", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    Matcher m = p.matcher(text);
    if (m.find())
      return new String[]{ m.group(1), text.substring(1 + m.end(1)) };
    return new String[]{ null, text };
  }

  static String join(CharSequence sep, Iterable<? extends CharSequence> elem) {
    StringBuilder sb = new StringBuilder();
    Iterator<? extends CharSequence> it = elem.iterator();
    if (it.hasNext()) {
      sb.append(it.next());
      while (it.hasNext())
        sb.append(sep).append(it.next());
    }
    return sb.toString();
  }

  /**
   * Loads a mail template using the specified name as a base for the files.
   * Template files are assumed to have the extensions &quot;.txt&quot;
   * and &quot;.html&quot; (or &quot;.htm&quot;) for plain text
   * and HTML messages respectively. A plain text message must be present
   * to successfully create an {@code EmailTemplate} instance, and an HTML
   * message file can be optionally present.
	 *
   * @param fP plain text email template file
   * @param fH HTML text email template file
	 * @param cs {@code Charset} for text files (must be the same)
   * @return EmailTemplate instance ready for use
   * @throws IOException if a problem occurs in loading the template
   */
  public static EmailTemplate load(File fP, File fH, Charset cs) throws IOException {
    return load(fP, fH, cs, cs);
  }

  /**
   * Loads a mail template using the specified name as a base for the files.
   * Template files are assumed to have the extensions &quot;.txt&quot;
   * and &quot;.html&quot; (or &quot;.htm&quot;) for plain text
   * and HTML messages respectively. A plain text message must be present
   * to successfully create an {@code EmailTemplate} instance, and an HTML
   * message file can be optionally present.
	 *
   * @param path folder path to use to locate files
   * @param baseName base name of email template files to load
   * @param csP {@code Charset} for plain text file
   * @param csH {@code Charset} for HTML text file
   * @return EmailTemplate instance ready for use
   * @throws IOException if a problem occurs in loading the template
   */
  public static EmailTemplate load(File path, String baseName, Charset csP, Charset csH) throws IOException {
    File[] f = resolveTemplatesFromRoot(path, baseName);
    return load(f[0], f[1], csP, csH);
  }

  /**
   * Loads a mail template using the specified name as a base for the files.
   * Template files are assumed to have the extensions &quot;.txt&quot;
   * and &quot;.html&quot; (or &quot;.htm&quot;) for plain text
   * and HTML messages respectively. A plain text message must be present
   * to successfully create an {@code EmailTemplate} instance, and an HTML
   * message file can be optionally present.
	 *
   * @param path folder path to use to locate files
   * @param baseName base name of email template files to load
	 * @param cs {@code Charset} for text files (must be the same)
   * @return EmailTemplate instance ready for use
   * @throws IOException if a problem occurs in loading the template
   */
  public static EmailTemplate load(File path, String baseName, Charset cs) throws IOException {
    File[] f = resolveTemplatesFromRoot(path, baseName);
    return load(f[0], f[1], cs, cs);
  }

  /**
   * Loads a mail template using the specified name as a base for the files.
	 *
   * @param path folder path to use to locate files
   * @param baseName base name of email template files to load
	 * @param cs {@code Charset} for text files (must be the same)
   * @return EmailTemplate instance ready for use
   * @throws IOException if a problem occurs in loading the template
   */
  public static EmailTemplate load(String path, String baseName, Charset cs) throws IOException {
    return load(new File(path), baseName, cs, cs);
  }

  /**
   * Finds the a file with an option of suffixes.
	 *
   * @param path file path for template file
   * @param name root name of the template
   * @param suffix file suffixes to use to find template
   * @return File instance representing existing template file, or null
   */
  private static File getTemplateFile(File path, String name, String[] suffix) {
    for (String s : suffix) {
      File f = new File(path, name + s);
      if (f.exists())
        return f;
    }
    return null;
  }

  public void setSubject(String x) {
		subj.setText(x);
		subj.setBaseState();
	}

	public String getSubject() {
		return subj.getText();
	}

  public void setPlainText(TemplateFiller x, Charset cs) {
		plainBody = x;
		charsetPlain = (cs == null) ? DEFAULT_CHARSET : cs;
	}

	public void setPlainText(String x, Charset cs) {
		plainBody.setText(x);
		plainBody.setBaseState();
		charsetPlain = (cs == null) ? DEFAULT_CHARSET : cs;
	}

	public String getPlainText() {
		return plainBody.getText();
	}

	public Charset getPlainTextCharset() {
		return (charsetPlain == null) ? DEFAULT_CHARSET : charsetPlain;
	}

  public void setHtmlText(TemplateFiller x, Charset cs) {
		htmlBody = x;
		charsetHTML = (cs == null) ? DEFAULT_CHARSET : cs;
	}

	public void setHtmlText(String x, Charset cs) {
		if (htmlBody == null)
			htmlBody = new TemplateFiller(x);
		else {
			htmlBody.setText(x);
			htmlBody.setBaseState();
		}
		charsetHTML = (cs == null) ? DEFAULT_CHARSET : cs;
	}

	public String getHtmlText() {
		return htmlBody.getText();
	}

	public Charset getHtmlTextCharset() {
		if (!hasHtmlText())
			throw new IllegalStateException("No HTML text has been specified");
		return (charsetHTML == null) ? DEFAULT_CHARSET : charsetHTML;
	}

	public boolean hasHtmlText() {
		return htmlBody != null;
	}

  public void removeHtmlText() {
    this.htmlBody = null;
  }

  /**
   * Tells the mail transport whether to use SMTP Authentication.
   *
   * @param useAuth whether to use SMTP Authentication.
   * @param username username for authentication (can be {@code null} if {@code useAuth=false})
   * @param password password for authentication (can be {@code null} if {@code useAuth=false})
   *
   * @see <a href="http://www.ietf.org/rfc/rfc2554.txt">RFC2554</a>
   */
  public void setUseAuth(boolean useAuth, String username, String password) {
    if (useAuth) {
      props.setProperty("mail.smtp.auth", "true");
      auth = new SMTPAuthenticator(username, password);
      if (isDebug())
        log.debug(String.format("Setup SMTP Authentication for username: %s", username));
    }
    else {
      props.remove("mail.smtp.auth");
      auth = null;
    }
  }

  /**
   * Tells the mail transport whether to use STARTTLS security for the connection.
   *
   * @param useStartTLS whether to use STARTTLS.
   */
  public void setUseStartTLS(boolean useStartTLS) {
    this.useStartTLS = useStartTLS;
  }

  /**
   * Sets the mail server host name to use, and whether to use SMTP over SSL.
   *
   * @param host host name of mail server
   * @param port port number of mail server
   * @param useSSL whether to use SMTP over SSL
   */
  public void setHost(String host, int port, boolean useSSL) {
    if (host == null || host.equals(""))
      throw new IllegalArgumentException("Invalid host specified");
    if (port < 0)
      throw new IllegalArgumentException("Invalid port specified: " + port);

    this.useSSL = useSSL;
    if (useSSL) {
      props.setProperty("mail.transport.protocol", "smtps");
      props.setProperty("mail.smtps.host", host);
      props.setProperty("mail.smtps.port", Integer.toString(port));
      props.remove("mail.smtp.host");
      props.remove("mail.smtp.port");
    }
    else {
      props.setProperty("mail.transport.protocol", "smtp");
      props.setProperty("mail.smtp.host", host);
      props.setProperty("mail.smtp.port", Integer.toString(port));
      props.remove("mail.smtps.host");
      props.remove("mail.smtps.port");
    }
  }

  /**
   * Sets the host name of the mail server, and whether to use SMTP over SSL.
   *
   * @param host email server host
   * @param useSSL whether to use SSL
   */
  public final void setHost(String host, boolean useSSL) {
    if (host == null || host.equals(""))
      throw new IllegalArgumentException("Invalid host specified");

    // Check for port number suffix on host name.
    port = useSSL ? 465 : 25;  // Default mail server port.
    Matcher m = Pattern.compile("^(.+):(\\d+)$").matcher(host);
    if (m.matches()) {
      try {
        if (isDebug())
          log.debug(String.format("Setting host: %s:%d", host, port));
        host = m.group(1);
        port = Integer.parseInt(m.group(2));
      }
      catch (NumberFormatException nfx) {
        throw new IllegalArgumentException("Invalid host/port specified", nfx);
      }
    }
    setHost(host, port, useSSL);
  }

  /**
   * Sets the mail server host name to use (defaults to port 25).
   *
   * @param host host name of mail server
   */
  public void setHost(String host) {
    setHost(host, false);
  }

  /**
   * @return Host name of mail server
   */
  public String getHost() {
    return props.getProperty(useSSL ? "mail.smtps.host" : "mail.smtp.host");
  }

  /**
   * @return Host port of mail server
   */
  public String getPort() {
    return props.getProperty(useSSL ? "mail.smtps.port" : "mail.smtp.port");
  }

  public void setDebug(boolean b) {
    this.debug = b;
    props.setProperty("mail.debug", Boolean.toString(b));
  }

  public boolean isDebug() {
    return this.debug;
  }

  /**
   * {@code Authenticator} instance to allow SMTP Authentication.
   */
  private static final class SMTPAuthenticator extends Authenticator {
    private final PasswordAuthentication pa;

    private SMTPAuthenticator(String u, String p) {
      pa = new PasswordAuthentication(u, p);
    }

    @Override
    protected PasswordAuthentication getPasswordAuthentication() {
      return pa;
    }
  }
}
