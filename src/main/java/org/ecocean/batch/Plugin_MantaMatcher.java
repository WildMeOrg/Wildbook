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

package org.ecocean.batch;

import java.io.*;
import java.text.MessageFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.ecocean.Encounter;
import org.ecocean.MarkedIndividual;
import org.ecocean.Shepherd;
import org.ecocean.SinglePhotoVideo;
import org.ecocean.mmutil.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Batch data processing plugin for the MantaMatcher website.
 * This class intercepts images with the <code>_CR</code> suffix for use as
 * &quot;candidate region&quot; images for the MantaMatcher algorithm.
 * Each matching image is copied to a new file, then processed using the
 * algorithm's <code>mmprocess</code> executable to generate the other files
 * necessary for performing image matching.
 * This class does not run <code>mmatch</code> for performance reasons.
 * Including it in the batch process causes an unacceptable delay,
 * and it's reasonable to assume data uploaded via this mechanism have been
 * previously checked visually.
 *
 * @author Giles Winstanley
 */
public final class Plugin_MantaMatcher extends BatchProcessorPlugin {
  /** SLF4J logger instance for writing log entries. */
  private static final Logger log = LoggerFactory.getLogger(Plugin_MantaMatcher.class);
  /** Regex pattern string for matching CR image filenames. */
  private static final Pattern REGEX_CR = Pattern.compile("^(.+)_CR\\." + MediaUtilities.REGEX_SUFFIX_FOR_WEB_IMAGES + "$");
  /** Resources for internationalization. */
  private final ResourceBundle bundle;
  /** Collection of media files to process with mmprocess. */
  private final List<SinglePhotoVideo> list = new ArrayList<SinglePhotoVideo>();

  public Plugin_MantaMatcher(Shepherd shepherd, List<MarkedIndividual> listInd, List<Encounter> listEnc, List<String> errors, List<String> warnings, Locale loc) {
    super(shepherd, listInd, listEnc, errors, warnings, loc);
    bundle = ResourceBundle.getBundle("bundles/" + getClass().getSimpleName(), loc);
  }

  @Override
  protected String getStatusMessage() {
    return bundle.getString("plugin.status");
  }

  @Override
  void preProcess() {
    // Process all images to find MantaMatcher CR images.
    List<File> done = new ArrayList<File>();
    for (SinglePhotoVideo spv : getMapPhoto().keySet()) {
      File f = spv.getFile();
      Matcher m = REGEX_CR.matcher(f.getName());
      if (m.matches()) {
        getMapPhoto().get(spv).setPersist(false);
        if (done.contains(f)) {
          String msg = MessageFormat.format(bundle.getString("plugin.warning.duplicateFile"), f.getName());
          addWarning(msg);
          log.warn(String.format("Duplicate CR image file found: %s", f.getAbsolutePath()));
        } else {
          done.add(f);
          list.add(spv);
          log.trace(String.format("Found MantaMatcher CR image: %s", spv.getFilename()));
        }
      }
    }
    setMaxCount(list.size());
  }

  /**
   * Process images for MantaMantcher algorithm.
   * The <code>mmprocess</code> executable requires input of a &quot;candidate region&quot;
   * image, which must have the filename suffix <code>_CR</code>
   * (e.g.&nbsp;foo_CR.jpg).
   * For each CR image file found:
   * <ol>
   * <li>Locate reference image to use (ID image, else fall back to CR image).</li>
   * <li>Rename CR image relative to reference image.</li>
   * <li>Call <code>mmprocess</code> to produce MM algorithm artefacts.</li>
   * </ol>
   * Output comprises three files with the suffixes <em>{ _EH, _FT, _FEAT }</em>.
   * This method ensures the initial file conditions, then checks for output.
   */
  @Override
  void process() throws IOException, InterruptedException {
    for (SinglePhotoVideo spv : list) {
      // Find reference image.
      SinglePhotoVideo ref = findReferenceSPV(spv);
      if (ref == null || !ref.getFile().exists()) {
        String msg = MessageFormat.format(bundle.getString("plugin.warning.noReference"), spv.getFile().getName());
        addWarning(msg);
        log.warn(String.format("Unable to find associated reference image for: %s", spv.getFile().getName()));
        incrementCounter();
        continue;
      }
      // Perform MM process.
      mmprocess(ref.getFile());
      // Check that mmprocess did something.
      Map<String, File> mmFiles = MantaMatcherUtilities.getMatcherFilesMap(ref);
      File fEH = mmFiles.get("EH");
      File fFT = mmFiles.get("FT");
      File fFEAT = mmFiles.get("FEAT");
      // Notify user & delete residual files if mmprocess failed.
      if (!fEH.exists() || !fFT.exists() || !fFEAT.exists()) {
        String msg = MessageFormat.format(bundle.getString("plugin.warning.mmprocess.failed"), spv.getFile().getName());
        addWarning(msg);
        log.warn(msg);
        fFEAT.delete();
        fFT.delete();
        fEH.delete();
        spv.getFile().delete();
      }
      // Increment progress counter.
      incrementCounter();
      // Take a breath to avoid hogging resources through external calls.
      Thread.yield();
    }

    // Process encounters to determine which are now MMA-compatible.
    for (Encounter enc : getListEnc()) {
      boolean hasCR = false;
      for (SinglePhotoVideo mySPV : enc.getSinglePhotoVideo()) {
        if (list.contains(mySPV))
          continue;
        if (MediaUtilities.isAcceptableImageFile(mySPV.getFile())) {
          Map<String, File> mmaFiles = MantaMatcherUtilities.getMatcherFilesMap(mySPV);
          hasCR = hasCR | mmaFiles.get("CR").exists();
          if (hasCR)
            break;
        }
      }
      // If encounter is MMA-compatible, set flag.
      if (hasCR)
        enc.setMmaCompatible(true);
    }
  }

  /**
   * Attempts to find the ID image file relating to the specified CR image file.
   * @param spvCR CR image file for which to find ID image file
   * @return {@code SinglePhotoVideo} instance of the ID image, or null if not found.
   */
  private SinglePhotoVideo findReferenceSPV(SinglePhotoVideo spvCR) {
    File fCR = spvCR.getFile();
    File found = null;
    Matcher m = REGEX_CR.matcher(fCR.getName());
    if (!m.matches())
      throw new IllegalArgumentException("Invalid CR image filename");

    // Check for existence of image without _CR suffix & same extension.
    File f = new File(fCR.getParentFile(), String.format("%s.%s", m.group(1), m.group(2)));
    if (f.exists()) {
      found = f;
      log.trace(String.format("Found reference file for %s (%s)", fCR.getName(), found.getName()));
    }
    // Check for existence of image without _CR suffix & different extension.
    if (found == null)
    {
      List<File> poss = MediaUtilities.listWebImageFiles(fCR.getParentFile(), m.group(1));
      if (!poss.isEmpty()) {
        found = poss.get(0);
        log.trace(String.format("Found reference file for %s (%s)", fCR.getName(), found.getName()));
        if (poss.size() > 1)
        {
          for (File x : poss)
            log.debug("Found multiple matching ID ref: " + x.getName());
        }
      }
    }
    // Failed to find any match.
    if (found == null)
      return null;

    // Find SPV relating to reference file.
    for (SinglePhotoVideo x : findEncounterForSPV(spvCR).getSinglePhotoVideo()) {
      if (x.getFile().equals(found))
        return x;
    }
    return null;
  }

  private Encounter findEncounterForSPV(SinglePhotoVideo spv) {
    assert spv != null;
    for (Encounter enc : getListEnc()) {
      if (enc.getCatalogNumber().equals(spv.getCorrespondingEncounterNumber()))
        return enc;
    }
    return null;
  }

  /**
   * Runs the <code>mmprocess</code> utility on the specified image file.
   * This method assumes that the related _CR image file is also in place.
   * @param imageFile image file for which to run utility
   * @throws IOException if there is a problem redirecting the process output to file
   * @throws InterruptedException if the process is interrupted
   */
  private void mmprocess(File imageFile) throws IOException, InterruptedException {
    assert MantaMatcherUtilities.getMatcherFilesMap(imageFile).get("CR").exists();
    File fOut = new File(imageFile.getParentFile(), "mmprocess.log");
    if (fOut.exists())
      fOut.delete();

    List<String> args = ListHelper.create("/usr/bin/mmprocess")
            .add(imageFile.getAbsolutePath())
            .add("4").add("1").add("2").asList();
    ProcessBuilder proc = new ProcessBuilder(args);
    proc.redirectErrorStream(true);
    log.trace("Running mmprocess for: " + imageFile.getName());
    Process p = proc.directory(imageFile.getParentFile()).start();

    InputStream in = null;
    OutputStream out = null;
    try {
      in = p.getInputStream();
      out = new BufferedOutputStream(new FileOutputStream(fOut));
      int len = 0;
      byte[] b = new byte[4096];
      while ((len = in.read(b)) != -1)
        out.write(b, 0, len);
    } finally {
      if (in != null) {
        in.close();
      }
      if (out != null) {
        out.flush();
        out.close();
      }
    }
    // Wait for process to finish, to avoid overload of processes.
    if (p.waitFor() == 0)
      fOut.delete();
  }
}
