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

package org.ecocean.mmutil;

import java.io.File;
import java.util.*;
import javax.jdo.Extent;
import javax.jdo.Query;
import org.ecocean.Encounter;
import org.ecocean.Shepherd;
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
   * <li>File representing MantaMatcher algorithm input file (key: MMA-INPUT).</li>
   * <li>File representing MantaMatcher algorithm regional input file (key: MMA-INPUT-REGIONAL).</li>
   * <li>File representing MantaMatcher output TXT file (key: TXT).</li>
   * <li>File representing MantaMatcher output CSV file (key: CSV).</li>
   * <li>File representing MantaMatcher regional output TXT file (key: TXT-REGIONAL).</li>
   * <li>File representing MantaMatcher regional output CSV file (key: CSV-REGIONAL).</li>
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
    // MMA input files.
    map.put("MMA-INPUT", new File(spv.getFile().getParentFile(), spv.getDataCollectionEventID() + "_mmaInput.txt"));
    map.put("MMA-INPUT-REGIONAL", new File(spv.getFile().getParentFile(), spv.getDataCollectionEventID() + "_mmaInputRegional.txt"));
    // MMA results files: global.
    map.put("TXT", new File(spv.getFile().getParentFile(), spv.getDataCollectionEventID() + "_mmaOutput.txt"));
    map.put("CSV", new File(spv.getFile().getParentFile(), spv.getDataCollectionEventID() + "_mmaOutput.csv"));
    // MMA results files: regional.
    map.put("TXT-REGIONAL", new File(spv.getFile().getParentFile(), spv.getDataCollectionEventID() + "_mmaOutputRegional.txt"));
    map.put("CSV-REGIONAL", new File(spv.getFile().getParentFile(), spv.getDataCollectionEventID() + "_mmaOutputRegional.csv"));
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
   * All files are assumed to be in the same folder, and if existing
   * web-compatible image files exist (based on file extension) they will be
   * referenced, otherwise a generic name is used (with the same extension as
   * the original file).
   * <p>
   * This allows simple determination of, for example, existence of a CR file
   * for a specified original:
   * <pre>
   * Map<String, File> mmFiles = MantaMatcherUtilities.getMatcherFilesMap(spv);
   * if (mmFiles.get("CR").exists()) {
   *     // ...do something ...
   * }
   * </pre>
   * 
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
      throw new IllegalArgumentException("Invalid file type specified: " + f.getName());
    String regex = "\\." + regFormat;
    File pf = f.getParentFile();

    // Locate web-compatible image files for each type (if possible).
    // (This provides basic support if in future mmprocess allows original/CR
    // files to have different file extensions.)
    String baseName = name.replaceFirst(regex, "");
    List<File> crOpts = MediaUtilities.listWebImageFiles(pf, baseName + "_CR");
    List<File> ehOpts = MediaUtilities.listWebImageFiles(pf, baseName + "_EH");
    List<File> ftOpts = MediaUtilities.listWebImageFiles(pf, baseName + "_FT");

    // Use existing image files if possible, otherwise use generic name with same file extension.
    File cr = crOpts.isEmpty() ? new File(pf, name.replaceFirst(regex, "_CR.$1")) : crOpts.get(0);
    String crExt = cr.getName().substring(cr.getName().lastIndexOf(".") + 1);
    File eh = ehOpts.isEmpty() ? new File(pf, name.replaceFirst(regex, "_EH." + crExt)) : ehOpts.get(0);
    File ft = ftOpts.isEmpty() ? new File(pf, name.replaceFirst(regex, "_FT." + crExt)) : ftOpts.get(0);
    File feat = new File(pf, name.replaceFirst(regex, ".FEAT"));
    
    Map<String, File> map = new HashMap<String, File>(11);
    map.put("O", f);
    map.put("CR", cr);
    map.put("EH", eh);
    map.put("FT", ft);
    map.put("FEAT", feat);
    return map;
  }

  /**
   * Checks whether the pre-processed MantaMatcher algorithm files exist for
   * the specified base image file (only checks the files required for running
   * {@code mmatch}).
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
   * Checks whether the pre-processed MantaMatcher algorithm files exist for
   * an encounter (which may be used to determine MMA-compatibility status).
   * @param enc encounter to test
   * @param dataDir data folder
   * @return true if any CR images are found, false otherwise
   */
  public static boolean checkEncounterHasMatcherFiles(Encounter enc, File dataDir) {
    for (SinglePhotoVideo spv : enc.getSinglePhotoVideo()) {
      if (MediaUtilities.isAcceptableImageFile(spv.getFile()) && checkMatcherFilesExist(spv.getFile())) {
        return true;
      }
    }
    return false;
  }

  /**
   * Removes all MantaMatcher algorithm files relating to the specified
   * base image file.
   * @param spv {@code SinglePhotoVideo} instance denoting base reference image
   */
  public static void removeMatcherFiles(SinglePhotoVideo spv) {
    Map<String, File> mmFiles = MantaMatcherUtilities.getMatcherFilesMap(spv);
    mmFiles.get("CR").delete();
    mmFiles.get("EH").delete();
    mmFiles.get("FT").delete();
    mmFiles.get("FEAT").delete();
    mmFiles.get("TXT").delete();
    mmFiles.get("CSV").delete();
    mmFiles.get("TXT-REGIONAL").delete();
    mmFiles.get("CSV-REGIONAL").delete();
    mmFiles.get("MMA-INPUT").delete();
    mmFiles.get("MMA-INPUT-REGIONAL").delete();
  }

  /**
   * Collates text input for the MantaMatcher algorithm.
   * The output of this method is suitable for placing in a temporary file
   * to be access by the {@code mmatch} process.
   * @param shep {@code Shepherd} instance
   * @param encDir &quot;encounters&quot; directory
   * @param enc {@code Encounter} instance
   * @param spv {@code SinglePhotoVideo} instance
   * @return text suitable for MantaMatcher algorithm input file
   */
  @SuppressWarnings("unchecked")
  public static String collateAlgorithmInput(Shepherd shep, File encDir, Encounter enc, SinglePhotoVideo spv) {
    // Validate input.
    if (enc.getLocationID() == null)
      throw new IllegalArgumentException("Invalid location ID specified");
    if (encDir == null || !encDir.isDirectory())
      throw new IllegalArgumentException("Invalid encounter directory specified");
    if (enc == null || spv == null)
      throw new IllegalArgumentException("Invalid encounter/SPV specified");

    // Build query filter based on encounter.
    StringBuilder sbf = new StringBuilder();
    if (enc.getSpecificEpithet()!= null) {
      sbf.append("(this.specificEpithet == null");
      sbf.append(" || this.specificEpithet == '").append(enc.getSpecificEpithet()).append("'");
      sbf.append(")");
    }
    if (enc.getPatterningCode() != null) {
      if (sbf.length() > 0)
        sbf.append(" && ");
      sbf.append("(this.patterningCode == null");
      // Normal & White mantas should always be compared.
      if (enc.getPatterningCode().matches("^(normal|white).*$"))
        sbf.append(" || this.patterningCode.startsWith('normal') || this.patterningCode.startsWith('white')");
      else
        sbf.append(" || this.patterningCode == '").append(enc.getPatterningCode()).append("'");
      sbf.append(")");
    }
    if (enc.getSex() != null && !"unknown".equals(enc.getSex())) {
      if (sbf.length() > 0)
        sbf.append(" && ");
      sbf.append("(this.sex == null");
      sbf.append(" || this.sex == 'unknown'");
      sbf.append(" || this.sex == '").append(enc.getSex()).append("'");
      sbf.append(")");
    }
//    log.trace(String.format("Filter: %s", sbf.toString()));

    // Issue query.
    Extent ext = shep.getPM().getExtent(Encounter.class, true);
		Query query = shep.getPM().newQuery(ext);
    query.setFilter(sbf.toString());
    List<Encounter> list = (List<Encounter>)query.execute();

    // Collate results.
    StringBuilder sb = new StringBuilder();
//    sb.append(spv.getFile().getParent()).append("\n\n");
    sb.append(spv.getFile().getAbsolutePath()).append("\n\n");
    for (Encounter x : list) {
      if (!enc.getEncounterNumber().equals(x.getEncounterNumber()))
        sb.append(encDir.getAbsolutePath()).append(File.separatorChar).append(x.subdir()).append("\n");
    }

    // Clean resources.
    query.closeAll();
    ext.closeAll();

    return sb.toString();
  }

  /**
   * Collates text input for the MantaMatcher algorithm.
   * The output of this method is suitable for placing in a temporary file
   * to be access by the {@code mmatch} process.
   * @param shep {@code Shepherd} instance
   * @param encDir &quot;encounters&quot; directory
   * @param enc {@code Encounter} instance
   * @param spv {@code SinglePhotoVideo} instance
   * @return text suitable for MantaMatcher algorithm input file
   */
  @SuppressWarnings("unchecked")
  public static String collateAlgorithmInputRegional(Shepherd shep, File encDir, Encounter enc, SinglePhotoVideo spv) {
    // Validate input.
    if (enc.getLocationID() == null)
      throw new IllegalArgumentException("Invalid location ID specified");
    if (encDir == null || !encDir.isDirectory())
      throw new IllegalArgumentException("Invalid encounter directory specified");
    if (enc == null || spv == null)
      throw new IllegalArgumentException("Invalid encounter/SPV specified");

    // Build query filter based on encounter.
    StringBuilder sbf = new StringBuilder();
    sbf.append("this.locationID == '").append(enc.getLocationID()).append("'");
    if (enc.getSpecificEpithet()!= null) {
      if (sbf.length() > 0)
        sbf.append(" && ");
      sbf.append("(this.specificEpithet == null");
      sbf.append(" || this.specificEpithet == '").append(enc.getSpecificEpithet()).append("'");
      sbf.append(")");
    }
    if (enc.getPatterningCode() != null) {
      if (sbf.length() > 0)
        sbf.append(" && ");
      sbf.append("(this.patterningCode == null");
      // Normal & White mantas should always be compared.
      if (enc.getPatterningCode().matches("^(normal|white).*$"))
        sbf.append(" || this.patterningCode.startsWith('normal') || this.patterningCode.startsWith('white')");
      else
        sbf.append(" || this.patterningCode == '").append(enc.getPatterningCode()).append("'");
      sbf.append(")");
    }
    if (enc.getSex() != null && !"unknown".equals(enc.getSex())) {
      if (sbf.length() > 0)
        sbf.append(" && ");
      sbf.append("(this.sex == null");
      sbf.append(" || this.sex == 'unknown'");
      sbf.append(" || this.sex == '").append(enc.getSex()).append("'");
      sbf.append(")");
    }
//    log.trace(String.format("Filter: %s", sbf.toString()));

    // Issue query.
    Extent ext = shep.getPM().getExtent(Encounter.class, true);
		Query query = shep.getPM().newQuery(ext);
    query.setFilter(sbf.toString());
    List<Encounter> list = (List<Encounter>)query.execute();

    // Collate results.
    StringBuilder sb = new StringBuilder();
//    sb.append(spv.getFile().getParent()).append("\n\n");
    sb.append(spv.getFile().getAbsolutePath()).append("\n\n");
    for (Encounter x : list) {
      if (!enc.getEncounterNumber().equals(x.getEncounterNumber()))
        sb.append(encDir.getAbsolutePath()).append(File.separatorChar).append(x.subdir()).append("\n");
    }

    // Clean resources.
    query.closeAll();
    ext.closeAll();

    return sb.toString();
  }

  /**
   * Removes any previously generated algorithm match results for the
   * specified encounter.
   * It's recommended this method be called whenever any match critical data
   * fields are changed (e.g. species/patterningCode/locationID).
   * @param enc encounter for which to remove algorithm match results
   */
  public static void removeAlgorithmMatchResults(Encounter enc) {
    for (SinglePhotoVideo spv : enc.getSinglePhotoVideo()) {
      Map<String, File> mmFiles = MantaMatcherUtilities.getMatcherFilesMap(spv);
      mmFiles.get("TXT").delete();
      mmFiles.get("CSV").delete();
      mmFiles.get("TXT-REGIONAL").delete();
      mmFiles.get("CSV-REGIONAL").delete();
    }
  }
}
