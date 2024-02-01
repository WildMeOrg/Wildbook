package org.ecocean.mmutil;

import java.io.File;
import java.io.FilenameFilter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code FilenameFilter} which filters based on a regular-expression match
 * to filenames.
 *
 * @author Giles Winstanley
 */
public class RegexFilenameFilter implements FilenameFilter
{
  private String s;

  public RegexFilenameFilter(String s)
  {
    this.s = s;
  }

  @Override
  public boolean accept(File dir, String name)
  {
    return name.matches(s);
  }

  public String getRegexString()
  {
    return s;
  }

  public Matcher getMatcher(CharSequence cs)
  {
    return Pattern.compile(s).matcher(cs);
  }
}
