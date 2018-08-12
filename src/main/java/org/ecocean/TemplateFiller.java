package org.ecocean;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implementation of a simple text template.
 * Loads a text template from a file, which can subsequently be used to
 * substitute placeholder strings with other strings in order to fill the template.
 * Templates also retain a copy of their original state so they can be re-used.
 *
 * @author Giles Winstanley
 */
public class TemplateFiller {
  /** Original text template (used for recycling template). */
  private String original;
  /** Text template. */
  private String template;
  /** Regular-expression pattern for text matching. */
  private Pattern pattern;
  /** Regular-expression {@code Matcher} instance for text matching. */
  private Matcher matcher;

  /**
   * Creates a new template using a string template.
   *
   * @param text text to use as template
   */
  public TemplateFiller(String text) {
    if (text == null) {
      throw new NullPointerException();
    }
    original = template = text;
  }

  /**
   * Creates a new template from the text contained in the specified file.
   *
   * @param file filename of the text file to use as template
   * @throws IOException if the template file cannot be loaded
   */
  public TemplateFiller(File file) throws IOException {
    this(loadTextFromFile(file));
  }

  /**
   * @return String contents of a text file.
   * @param filename name of {@code File} from which to load text
   * @throws IOException if the template file cannot be loaded
   */
  public static String loadTextFromFile(String filename) throws IOException {
    return loadTextFromFile(new File(filename));
  }

  /**
   * @return String contents of a text file.
   * @param file {@code File} instance from which to load text
   * @throws IOException if the template file cannot be loaded
   */
  public static String loadTextFromFile(File file) throws IOException {
    if (!file.exists()) {
      throw new FileNotFoundException("Unable to find file: " + file.getAbsolutePath());
    }
    return new String(Files.readAllBytes(file.toPath()));
  }

  /**
   * @return Whether the template contains the specified regular expression token.
   * This is useful for conditional replacement (e.g. optimisation).
   *
   * @param search regex search term
   * @param flags regex flags
   * @see java.util.regex.Pattern
   */
  public boolean containsRegex(String search, int flags) {
    pattern = Pattern.compile(search, flags);
    matcher = pattern.matcher(template);
    return matcher.find();
  }

  /**
   * @return Whether the template contains the specified regular expression token.
   * This is useful for conditional replacement (e.g. optimisation).
   * @param search regex search term
   */
  public boolean containsRegex(String search) {
    return containsRegex(search, 0);
  }

  /**
   * Searches and replaces one or all occurrences of the specified regular
   * expression search term with the specified replacement.
   *
   * @param search regex search term
   * @param replace regex replacement term
   * @param flags regex flags (defined in {@link Pattern})
   * @param all whether to replace all occurrences or just the first
   */
  public void replaceRegex(String search, String replace, int flags, boolean all) {
    String x = null;
    pattern = Pattern.compile(search, flags);
    matcher = pattern.matcher(template);
    if (all) {
      x = matcher.replaceAll(replace == null ? "" : replace);
    } else {
      x = matcher.replaceFirst(replace == null ? "" : replace);
    }
    template = x;
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
   * Substitutes a specified string with another within the template page.
   *
   * @param search the string for which to search
   * @param replace the string with which to replace the occurrences found
   * @param all whether to replace all occurrences or just the first
   */
  public void replace(String search, String replace, boolean all) {
    if (template == null || template.length() == 0) {
      return;
    }
    StringBuilder sb = new StringBuilder(template.length());
    int len = search.length();
    int max = template.length() - len + 1;
    int lo = 0;

    for (int pos = 0; pos < max; pos++) {
      if (template.regionMatches(pos, search, 0, len)) {
        sb.append(template.substring(lo, pos));
        sb.append(replace == null ? "" : replace);
        lo = pos + len;
        pos = lo - 1;
        if (!all) {
          break;
        }
      }
    }
    if (lo < template.length()) {
      sb.append(template.substring(lo));
    }
    template = sb.toString();
  }

  /**
   * Substitutes a specified string with the contents of a text file.
   *
   * @param search the string for which to search
   * @param replace the text file to use as replacement text
   * @throws IOException if the replacement text file cannot be loaded
   */
  public void replace(String search, File replace) throws IOException {
    if (template == null || template.length() == 0) {
      return;
    }
    String s = loadTextFromFile(replace);
    replace(search, s, false);
  }

  /**
   * Sets the template back to its base state.
   */
  public void reset() {
    template = original;
  }

  /**
   * Sets the current state as the template's base state.
   */
  public void setBaseState() {
    original = template;
  }

  /**
   * Sets the template to be the specified string.
   *
   * @param s string to use as template
   */
  public void setText(String s) {
    template = s;
  }

  /**
   * @return Current text content of the template, or {@code null} if not
   * loaded. This is the method to be used to retrieve the template contents
   * after the required textual replacements have been performed.
   */
  public String getText() {
    return template;
  }
}
