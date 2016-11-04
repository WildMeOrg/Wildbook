package org.ecocean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class to check for spam content.
 *
 * @author Giles Winstanley
 */
public final class SpamChecker {
  /** SLF4J logger instance for writing log entries. */
  private static final Logger log = LoggerFactory.getLogger(SpamChecker.class);
  /** Enumeration of possible spam detection outcomes. */
  public enum Result { SPAM, POSSIBLE_SPAM, NOT_SPAM };
  /** Name of text file resource containing spam filter definitions (definite spam). */
  private static final String RESOURCE_DEFINITE_SPAM = "/SpamChecker_spam.txt";
  /** Name of text file resource containing spam filter definitions (possible spam). */
  private static final String RESOURCE_POSSIBLE_SPAM = "/SpamChecker_possibleSpam.txt";
  /** List of regex patterns which if matched denote definite spam. */
  private List<Pattern> spamPatterns;
  /** List of regex patterns which if matched denote possible spam. */
  private List<Pattern> possibleSpamPatterns;

  public SpamChecker() {
    try {
      this.spamPatterns = loadPatterns(RESOURCE_DEFINITE_SPAM);
    }
    catch (IOException ex) {
      log.warn("Failed to find SpamChecker configuration file: " + RESOURCE_DEFINITE_SPAM);
    }
    try {
      this.possibleSpamPatterns = loadPatterns(RESOURCE_POSSIBLE_SPAM);
    }
    catch (IOException ex) {
      log.warn("Failed to find SpamChecker configuration file: " + RESOURCE_POSSIBLE_SPAM);
    }
  }

  /**
   * Loads words-matching patterns from a text file resource.
   * The specified resource should contain each entry on a separate line, with case-insensitive substring matches
   * entered as plain text, and regular-expression matches (for entire text) delimited by forward slashes
   * (e.g. &quot;/.&#42;([Ff]oo|[Bb]ar)&#42;/&quot;).
   * @param rName resource name to be found on classpath
   * @return list of {@code Pattern} instances for word matching
   * @throws IOException
   */
  private static List<Pattern> loadPatterns(String rName) throws IOException {
    List<Pattern> list = new ArrayList<>();
    try(InputStream in = SpamChecker.class.getResourceAsStream(rName);
        BufferedReader br = new BufferedReader(new InputStreamReader(in))) {
      String s = null;
      while ((s = br.readLine()) != null) {
        if (s.trim().startsWith("#") || "".equals(s.trim()))
          continue;
        list.add(Pattern.compile(s));
        // Check for regex-match if text is slash-delimited (e.g. "/regex/")
        Matcher m = Pattern.compile("^/(.+)/$").matcher(s);
        if (m.matches()) {
          list.add(Pattern.compile(m.group(1)));
        }
        // Otherwise add regex for plain substring match (mapped to regex: ".*text.*").
        else {
          list.add(Pattern.compile(".*" + s + ".*", Pattern.CASE_INSENSITIVE));
        }
      }
    }
    catch (NullPointerException ex) {
      throw new IOException(ex);
    }
    return list;
  }

  /**
   * Checks if the specified {@code Encounter} is a spam submission, based on the text content submitted.
   * Note: the specified encounter will generally not have any stored media files when passed to this method,
   * so spam determination should only be via text fields.
   * @param enc encounter to check
   * @return true if spam detected, false otherwise
   */
  public Result isSpam(Encounter enc) {
    // Checks for definite spam.
    if (containsDefiniteSpam(enc.getSubmitterName()) || containsDefiniteSpam(enc.getSubmitterPhone()))
      return Result.SPAM;
    if (containsDefiniteSpam(enc.getPhotographerName()) || containsDefiniteSpam(enc.getPhotographerPhone()))
      return Result.SPAM;
    if (containsDefiniteSpam(enc.getLocation()) || containsDefiniteSpam(enc.getComments()) || containsDefiniteSpam(enc.getBehavior()))
      return Result.SPAM;

    // Checks for possible spam.
    if (containsPossibleSpam(enc.getSubmitterName()) || containsPossibleSpam(enc.getSubmitterPhone()))
      return Result.POSSIBLE_SPAM;
    if (containsPossibleSpam(enc.getPhotographerName()) || containsPossibleSpam(enc.getPhotographerPhone()))
      return Result.POSSIBLE_SPAM;
    if (containsPossibleSpam(enc.getLocation()) || containsPossibleSpam(enc.getComments()) || containsPossibleSpam(enc.getBehavior()))
      return Result.POSSIBLE_SPAM;

    return Result.NOT_SPAM;
  }

  /**
   * Checks if the specified text contains anything considered spam.
   * These detections are absolute, and should only be specified for definite spam text.
   * @param text text to check
   * @return true if spam words detected, false otherwise
   */
  public boolean containsDefiniteSpam(String text) {
    if (text == null || spamPatterns == null)
      return false;
    for (Pattern p : spamPatterns) {
      if (p.matcher(text).matches()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Checks if the specified text contains anything considered possible spam.
   * @param text text to check
   * @return true if possible spam words detected, false otherwise
   */
  public boolean containsPossibleSpam(String text) {
    if (text == null || possibleSpamPatterns == null)
      return false;
    for (Pattern p : possibleSpamPatterns) {
      if (p.matcher(text).matches()) {
        return true;
      }
    }
    return false;
  }
}
