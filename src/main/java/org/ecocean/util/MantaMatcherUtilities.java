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

package org.ecocean.util;

import freemarker.template.Configuration;
import freemarker.template.DefaultObjectWrapper;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import freemarker.template.Version;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;
import org.ecocean.SinglePhotoVideo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Giles Winstanley
 */
public final class MantaMatcherUtilities {
  /** SLF4J logger instance for writing log entries. */
  private static final Logger log = LoggerFactory.getLogger(MantaMatcherUtilities.class);

  private MantaMatcherUtilities() {}

  /**
   * Converts the specified file to a map of files, keyed by string for easy
   * referencing, comprising the following elements:
   * <ul>
   * <li>Original file (key: O).</li>
   * <li>File representing MantaMatcher &quot;candidate region&quot; photo (key: CR).</li>
   * <li>File representing MantaMatcher enhanced photo (key: EH).</li>
   * <li>File representing MantaMatcher feature photo (key: FT).</li>
   * <li>File representing MantaMatcher feature file (key: FEAT).</li>
   * <li>File representing MantaMatcher output TXT file (key: TXT).</li>
   * <li>File representing MantaMatcher output CSV file (key: CSV).</li>
   * <li>File representing MantaMatcher output XHTML file (key: XHTML).</li>
   * </ul>
   * All files are assumed to be in the same folder, and no checking is
   * performed to see if they exist.
   * @param spv {@code SinglePhotoVideo} instance denoting base reference image
   * @return Map of string to file for each MantaMatcher algorithm feature.
   */
  public static Map<String, File> getMatcherFilesMap(SinglePhotoVideo spv) {
    if (spv == null)
      throw new NullPointerException("Invalid file specified: null");
    Map<String, File> map = getMatcherFilesMap(spv.getFile());
    map.put("TXT", new File(spv.getFile().getParentFile(), spv.getDataCollectionEventID() + "_matchOutput.txt"));
    map.put("CSV", new File(spv.getFile().getParentFile(), spv.getDataCollectionEventID() + "_matchOutput.csv"));
    map.put("XHTML", new File(spv.getFile().getParentFile(), spv.getDataCollectionEventID() + "_matchOutput.xhtml"));
    return map;
  }

  /**
   * Converts the specified file to a map of files, keyed by string for easy
   * referencing, comprising the following elements:
   * <ul>
   * <li>Original file (key: O).</li>
   * <li>File representing MantaMatcher &quot;candidate region&quot; photo (key: CR).</li>
   * <li>File representing MantaMatcher enhanced photo (key: EH).</li>
   * <li>File representing MantaMatcher feature photo (key: FT).</li>
   * <li>File representing MantaMatcher feature file (key: FEAT).</li>
   * </ul>
   * All files are assumed to be in the same folder, and no checking is
   * performed to see if they exist.
   * The functionality is centralized here to reduce naming errors/conflicts.
   * @param f base image file from which to reference other algorithm files
   * @return Map of string to file for each MantaMatcher algorithm feature.
   */
  public static Map<String, File> getMatcherFilesMap(File f) {
    if (f == null)
      throw new NullPointerException("Invalid file specified: null");
    String name = f.getName();
    String regFormat = MediaUtilities.REGEX_SUFFIX_FOR_WEB_IMAGES;
    if (!name.matches("^.+\\." + regFormat))
      throw new IllegalArgumentException("Invalid file type specified");
    String regex = "\\." + regFormat;
    File pf = f.getParentFile();
    File cr = new File(pf, name.replaceFirst(regex, "_CR.$1"));
    File eh = new File(pf, name.replaceFirst(regex, "_EH.$1"));
    File ft = new File(pf, name.replaceFirst(regex, "_FT.$1"));
    File feat = new File(pf, name.replaceFirst(regex, ".FEAT"));
    
    Map<String, File> map = new HashMap<String, File>(7);
    map.put("O", f);
    map.put("CR", cr);
    map.put("EH", eh);
    map.put("FT", ft);
    map.put("FEAT", feat);
    return map;
  }

  /**
   * Checks whether the MantaMatcher algorithm files exist for the specified
   * base file (only checks the files required for running {@code mmatch}).
   * @param f base image file from which to reference other algorithm files
   * @return true if all MantaMatcher files exist (O/CR/EH/FT/FEAT), false otherwise
   */
  public static boolean checkMatcherFilesExist(File f) {
    Map<String, File> mmFiles = getMatcherFilesMap(f);
    return mmFiles.get("O").exists() &&
            mmFiles.get("CR").exists() &&
            mmFiles.get("EH").exists() &&
            mmFiles.get("FT").exists() &&
            mmFiles.get("FEAT").exists();
  }

  /**
   * Checks whether the MantaMatcher algorithm files exist for the specified
   * base file.
   * @param spv {@code SinglePhotoVideo} instance denoting base reference image
   * @return true if MantaMatcher results files exist (TXT/CSV), false otherwise
   */
  public static boolean checkMatcherResultsFilesExist(SinglePhotoVideo spv) {
    Map<String, File> mmFiles = getMatcherFilesMap(spv);
    return mmFiles.get("TXT").exists() && mmFiles.get("CSV").exists();
  }

	/**
   * Creates a FreeMarker template configuration instance.
   * @param dir folder in which templates are located
   * @return Configuration instance for loading FreeMarker templates
   * @throws IOException
   */
  public static Configuration configureTemplateEngine(File dir) throws IOException
	{
		Configuration conf = new Configuration();
		conf.setDirectoryForTemplateLoading(dir);
		conf.setObjectWrapper(new DefaultObjectWrapper());
		conf.setDefaultEncoding("UTF-8");
    conf.setURLEscapingCharset("ISO-8859-1");
		conf.setTemplateExceptionHandler(TemplateExceptionHandler.DEBUG_HANDLER);
		conf.setIncompatibleImprovements(new Version(2, 3, 20));
    return conf;
	}

  /**
   * Parses the MantaMatcher results text file for the specified SinglePhotoVideo.
   * @param conf FreeMarker configuration
   * @param spv {@code SinglePhotoVideo} instance for base reference image
   * @param urlPrefixImage URL prefix for encounter folder (for image links)
   * @param pageUrlFormat Format string for encounter page URL (with <em>%s</em> placeholder)
   * @return A map containing parsed results ready for use with a FreeMarker template
   * @throws IOException
   * @throws ParseException
   * @throws TemplateException
   */
  @SuppressWarnings("unchecked")
  public static String getResultsHtml(Configuration conf, SinglePhotoVideo spv, String urlPrefixImage, String pageUrlFormat) throws IOException, ParseException, TemplateException {
    // Load test from file.
    Map<String, File> mmFiles = getMatcherFilesMap(spv);
    File txt = mmFiles.get("TXT");
    if (!txt.isFile())
      throw new FileNotFoundException("Unable to locate results file: " + txt.getAbsolutePath());

    // Load results file.
    String text = new String(FileUtilities.loadFile(txt));
    // Convert to HTML results page.
    return MMAResultsProcessor.convertResultsToHtml(conf, text, spv, urlPrefixImage, pageUrlFormat);
  }
}
