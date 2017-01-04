package org.ecocean.mmutil;

/**
 * {@code FilenameFilter} which matches JPEG/PNG images.
 * 
 * @author Giles Winstanley
 */
public class WebImageFilenameFilter extends RegexFilenameFilter {
  private static final RegexFilenameFilter INSTANCE = new WebImageFilenameFilter();
  
  private WebImageFilenameFilter() {
    super("(.+)\\." + MediaUtilities.REGEX_SUFFIX_FOR_WEB_IMAGES);
  }

  public static RegexFilenameFilter instance() {
    return INSTANCE;
  }
}
