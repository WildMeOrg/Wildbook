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

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import javax.servlet.ServletContext;
import org.ecocean.Encounter;
import org.ecocean.MarkedIndividual;
import org.ecocean.Measurement;
import org.ecocean.Shepherd;
import org.ecocean.SinglePhotoVideo;
import org.ecocean.genetics.TissueSample;

/**
 * Base implementation for a site-specific batch data processor.
 * Implementors should assume the class will be instantiated via the single
 * available constructor, with pre-parsed data passed in as arguments.
 * Control is passed from the main {@link BatchProcessor} class at two points
 * of execution, firstly at the very start of processing via the
 * {@link #preProcess()} method, and secondly after all other processing has
 * completed via the {@link #process()} method. The {@code preProcess()}
 * method should execute swiftly, and also ensure to set an appropriate value
 * for the progress monitor using the {@link #setMaxCount(int)} method.
 * The progress should then be increased sequentially using the
 * {@link #incrementCounter()} method within the {@code process()} method.
 *
 * <p><strong>WARNING:</strong> Implementors should be aware that because this
 * class can intercept and modify all data being uploaded, it has potential to
 * be a data security risk when used inappropriately. Care should be taken with
 * all field and method access modifiers to ensure no inadvertent data access
 * may occur from unwanted sources.</p>
 *
 * @author Giles Winstanley
 */
public abstract class BatchProcessorPlugin {
  /** Shepherd instance for persisting data to database. */
  private final Shepherd shepherd;
  /** List of individuals. */
  private final List<MarkedIndividual> listInd;
  /** List of encounters. */
  private final List<Encounter> listEnc;
  /** List of measurements. */
  private List<Measurement> listMea;
  /** Map of media-items to batch-photos used during batch processing. */
  private Map<SinglePhotoVideo, BatchMedia> mapPhoto;
  /** List of samples. */
  private List<TissueSample> listSam;
  /** List of errors produced by the batch processor (fatal). */
  private final List<String> errors;
  /** List of warnings produced by the batch processor (non-fatal). */
  private final List<String> warnings;
  /** Locale for internationalization. */
  private final Locale locale;
  /** ServletContext for web application, to allow access to resources. */
  private ServletContext servletContext;
  /** Data folder for web application. */
  private File dataDir;
  /** Maximum &quot;item&quot; count (used for progress display). */
  private int maxCount;
  /** Current &quot;item&quot; count (used for progress display). */
  private int counter;

  public BatchProcessorPlugin(Shepherd shepherd, List<MarkedIndividual> listInd, List<Encounter> listEnc, List<String> errors, List<String> warnings, Locale loc) {
    Objects.requireNonNull(shepherd, "Shepherd cannot be null");
    Objects.requireNonNull(listInd, "Individuals list cannot be null");
    Objects.requireNonNull(listEnc, "Encounters list cannot be null");
    Objects.requireNonNull(errors, "Errors cannot be null");
    Objects.requireNonNull(warnings, "Warnings cannot be null");
    Objects.requireNonNull(loc, "Locale cannot be null");
    this.shepherd = shepherd;
    this.listInd = listInd;
    this.listEnc = listEnc;
    this.errors = errors;
    this.warnings = warnings;
    this.locale = loc;
  }

  protected final Shepherd getShepherd() {
    return shepherd;
  }

  public final List<MarkedIndividual> getListInd() {
    return listInd;
  }

  public final List<Encounter> getListEnc() {
    return listEnc;
  }

  protected final List<Measurement> getListMea() {
    return listMea;
  }

  final void setListMea(List<Measurement> listMea) {
    this.listMea = listMea;
  }

  final protected Map<SinglePhotoVideo, BatchMedia> getMapPhoto() {
    return mapPhoto;
  }

  final void setMapPhoto(Map<SinglePhotoVideo, BatchMedia> mapPhoto) {
    this.mapPhoto = mapPhoto;
  }

  final protected List<TissueSample> getListSam() {
    return listSam;
  }

  final void setListSam(List<TissueSample> listSam) {
    this.listSam = listSam;
  }

  protected final List<String> getErrors() {
    return errors;
  }

  protected final void addError(String msg) {
    errors.add(msg);
  }

  protected final List<String> getWarnings() {
    return warnings;
  }

  protected final void addWarning(String msg) {
    warnings.add(msg);
  }

  protected final Locale getLocale() {
    return locale;
  }

  protected final ServletContext getServletContext() {
    return servletContext;
  }

  final void setServletContext(ServletContext servletContext) {
    this.servletContext = servletContext;
  }

  protected final File getDataDir() {
    return dataDir;
  }

  final void setDataDir(File dataDir) {
    this.dataDir = dataDir;
  }

  protected final void setMaxCount(int maxCount) {
    this.maxCount = maxCount;
  }

  final int getMaxCount() {
    return maxCount;
  }

  protected final void incrementCounter() {
    this.counter++;
  }

  final int getCounter() {
    return counter;
  }

  /**
   * @return The string to be used as status message during processing by the plugin (or null for generic message).
   */
  protected String getStatusMessage() {
    return null;
  }

  /**
   * Method to perform plugin work before the {@code BatchProcessor} performs
   * the core of its work.
   * This method is called before any of the media files have been downloaded,
   * so if reliant on downloaded media files, they should not be removed from
   * the relevant collection (see {@link #getMapPhoto()}.
   * Implementations of this method should ensure they return quickly,
   * and before returning should have assigned values for the maxCount and
   * counter fields, ready to track progress.
   */
  abstract void preProcess() throws Exception;

  /**
   * Method to perform core plugin work, which takes place after the
   * {@code BatchProcessor} has performed the core of its work.
   * This task may take a while to execute, and it should report its progress
   * through the use of the counter field. The {@code counter} is expected to
   * start at zero, and when the the task is complete {@code counter = maxCount}.
   */
  abstract void process() throws Exception;
}
