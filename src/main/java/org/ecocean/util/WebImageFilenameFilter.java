package org.ecocean.util;

/**
 * {@code FilenameFilter} which matches JPEG/PNG images.
 * 
 * @author Giles Winstanley
 */
public class WebImageFilenameFilter extends RegexFilenameFilter {
  private static final RegexFilenameFilter INSTANCE = new WebImageFilenameFilter();
  
  private WebImageFilenameFilter() {
    super("(?i:(.+)\\.(jpe?g?|png))");
  }

  public static RegexFilenameFilter instance() {
    return INSTANCE;
  }
}
