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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.ecocean.Encounter;
import org.ecocean.MarkedIndividual;
import org.ecocean.SinglePhotoVideo;
import org.ecocean.util.FileUtilities;
import org.ecocean.util.RegexFilenameFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Batch data processing plugin for the MantaMatcher website.
 * This class intercepts images with the <code>_CR</code> suffix for use as
 * &quot;candidate region&quot; images for the MantaMatcher algorithm.
 * Each matching image is copied to a new file, then processed using the
 * algorithm's <em>mmprocess</em> executable to generate the other files
 * necessary for performing image matching.
 * <p><strong>NOTE:</strong> Only one <code>_CR</code> image per encounter
 * should be specified in the batch data, as only a single image per encounter
 * is currently supported by the website design.
 *
 * @author Giles Winstanley
 */
public final class Plugin_MantaMatcher extends BatchProcessorPlugin {
  /** SLF4J logger instance for writing log entries. */
  private static final Logger log = LoggerFactory.getLogger(Plugin_MantaMatcher.class);
  /** Resources for internationalization. */
  private ResourceBundle bundle;
  /** Collection of media files to process. */
  private List<SinglePhotoVideo> list = new ArrayList<SinglePhotoVideo>();

  public Plugin_MantaMatcher(List<MarkedIndividual> listInd, List<Encounter> listEnc, List<String> errors, List<String> warnings, Locale loc) {
    super(listInd, listEnc, errors, warnings, loc);
    bundle = ResourceBundle.getBundle("bundles/" + getClass().getSimpleName(), loc);
  }

  @Override
  protected String getStatusMessage() {
    return bundle.getString("plugin.status");
  }

  @Override
  void preProcess() {
    // Process all images to find MantaMatcher "_CR" images.
    // Keep track of MM images for post-processing.
    Pattern p = Pattern.compile("^(.+)_CR\\.(?i:(jpe?g?|png))$");
    List<File> done = new ArrayList<File>();
    for (SinglePhotoVideo spv : getMapPhoto().keySet()) {
      File f = spv.getFile();
      Matcher m = p.matcher(f.getName());
      if (m.matches()) {
        if (done.contains(f)) {
          String msg = MessageFormat.format(bundle.getString("plugin.warning.duplicate"), f.getName());
          addWarning(msg);
          log.warn(String.format("Duplicate _CR image found: %s", f.getAbsolutePath()));
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
   * The mmprocess executable requires input of a &quot;candidate region&quot;
   * image, which must have the filename suffix <em>_CR</em>
   * (e.g.&nbsp;foo_CR.jpg).
   * Output comprises three files with the suffixes <em>{ _EH, _FT, _FEAT }</em>.
   * This method ensures the initial file conditions, then checks for output.
   */
  @Override
  void process() throws IOException {
    RegexFilenameFilter ff = new RegexFilenameFilter("(?i:(.+)_CR\\.(jpe?g?|png))");
    for (SinglePhotoVideo spv : list) {
      // Copy file to isolate MM process.
      Matcher m = ff.getMatcher(spv.getFile().getName());
      if (!m.matches())
        throw new IOException("Non-matching filename: " + spv.getFilename());
      File f = new File(spv.getFile().getParentFile(), String.format("mantaProcessedImage.%s", m.group(2)));
      File fCR = new File(spv.getFile().getParentFile(), String.format("mantaProcessedImage_CR.%s", m.group(2)));

      if (f.exists() || fCR.exists()) {
        String msg = MessageFormat.format(bundle.getString("plugin.warning.duplicate"), f.getName());
        addWarning(msg);
        log.warn(String.format("Duplicate _CR image found: %s", f.getAbsolutePath()));
      } else if (!spv.getFile().exists()) {
        String msg = MessageFormat.format(bundle.getString("plugin.warning.fileNotFound"), spv.getFile().getAbsolutePath());
        addWarning(msg);
        log.warn(String.format("Original image not found: %s", spv.getFile().getAbsolutePath()));
      } else {
        FileUtilities.copyFile(spv.getFile(), fCR);
        FileUtilities.copyFile(spv.getFile(), f);
        // Perform MM process.
        mmprocess(f);
        // Check that mmprocess did something.
        File fEH = new File(fCR.getParentFile(), fCR.getName().replace("_CR", "_EH"));
        File fFT = new File(fCR.getParentFile(), fCR.getName().replace("_CR", "_FT"));
        File fFEAT = new File(fCR.getParentFile(), fCR.getName().replaceFirst("_CR.+$", ".FEAT"));
        if (!fEH.exists() || !fFT.exists() || !fFEAT.exists()) {
          String param = String.format("%1$s_EH.%2$s, %1$s_FT.%2$s, %1$s.FEAT,", m.group(1), m.group(2));
          String msg = MessageFormat.format(bundle.getString("plugin.warning.mmprocess.failed"), param);
          addWarning(msg);
          log.warn(msg);
        }
      }
      // Increment progress counter.
      incrementCounter();
    }
  }

  private void mmprocess(File imageFile) throws IOException {
    File fOut = new File(imageFile.getParentFile(), "mmprocess.log");
    if (fOut.exists())
      fOut.delete();

    List<String> args = new ArrayList<String>();
    args.add("/usr/bin/mmprocess");
    args.add(imageFile.getAbsolutePath());
    args.add("4");
    args.add("1");
    args.add("2");
    ProcessBuilder proc = new ProcessBuilder(args);
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
  }
}
