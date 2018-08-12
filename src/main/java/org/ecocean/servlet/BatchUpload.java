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
package org.ecocean.servlet;

import com.oreilly.servlet.multipart.FilePart;
import com.oreilly.servlet.multipart.MultipartParser;
import com.oreilly.servlet.multipart.Part;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.ecocean.*;
import org.ecocean.batch.BatchData;
import org.ecocean.batch.BatchMedia;
import org.ecocean.batch.BatchParser;
import org.ecocean.batch.BatchProcessor;
import org.ecocean.genetics.TissueSample;
import org.ecocean.mmutil.DataUtilities;
import org.ecocean.mmutil.MediaUtilities;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Servlet to manage batch data uploads for Wild Book.
 * There are three key steps to batch data upload:
 * <ol>
 * <li>CSV template download.</li>
 * <li>CSV data file upload.</li>
 * <li>Batch data processing.</li>
 * </ol>
 * <p><strong>Overview:</strong> A JSP view provides the user with file upload
 * capability for several CSV files, representing at least <em>individuals</em>,
 * and <em>encounters</em>, and optionally <em>media</em> and <em>samples</em>.
 * <h3>1. CSV template download</h3>
 * <p>The JSP page provides links to download templates for each CSV template
 * needed for data upload, which simply provide the data column headers,
 * and may be used for reference when writing code to export user data,
 * or simply filled in directly.</p>
 * <h3>2. CSV data file upload</h3>
 * <p>The JSP page also provides fields for specifying CSV data files
 * from your local file-system for upload to the server for processing.</p>
 * <h3>3. Batch data processing</h3>
 * <p>Once a request for data processing has been submitted, the batch
 * processor performs the following:</p>
 * <ul>
 * <li>Parse &amp; validate the CSV files.</li>
 * <li>Present the user with a summary page.</li>
 * <li>Request confirmation from user to proceed with data processing.</li>
 * <li>Write data to the database for permanent storage.</li>
 * <li>Process any media items submitted.</li>
 * </ul>
 * <p>The confirmation step allows users to have their CSV files tested by
 * the server for validity, and to check that all required data will be
 * processed as intended.</p>
 *
 * @see <a href="http://www.ietf.org/rfc/rfc1867.txt">RFC1867</a>
 *
 * @author Giles Winstanley
 */
public final class BatchUpload extends DispatchServlet {
  private static final long serialVersionUID = 1L;
  /** SLF4J logger instance for writing log entries. */
  public static Logger log = LoggerFactory.getLogger(BatchUpload.class);
  /** Name of folder in which to hold batch upload data. */
  private static final String BATCH_DATA_DIR = "batch-data";
  /** Session key for referencing batch upload data. */
  public static final String SESSION_KEY_DATA = "BatchData";
  /** Session key for referencing existing matching task. */
  public static final String SESSION_KEY_TASK = "BatchTask";
  /** Session key for referencing existing matching task's {@code Future}. */
  public static final String SESSION_KEY_TASKFUTURE = "BatchTaskFuture";
  /** Session key for referencing existing matching task. */
  public static final String SESSION_KEY_ERRORS = "BatchErrors";
  /** Session key for referencing existing matching task. */
  public static final String SESSION_KEY_WARNINGS = "BatchWarnings";
  /** Path for referencing JSP page for main batch upload page. */
  public static final String JSP_MAIN = "/appadmin/batchUpload.jsp";
  /** Path for referencing JSP page for batch task confirmation. */
  public static final String JSP_CONFIRM = "/appadmin/batchUploadConfirmation.jsp";
  /** Path for referencing JSP page for batch task progress. */
  public static final String JSP_PROGRESS = "/appadmin/batchUploadProgress.jsp";
  /** Path for referencing JSP page for error display. */
  public static final String JSP_ERROR = "/error_generic.jsp";
  /** Name of batch template files. */
  private static final String[] BTF = {
    "batchIndividuals.csv",
    "batchEncounters.csv",
    "batchMeasurements.csv",
    "batchMedia.csv",
    "batchSamples.csv"
  };
  /** DateFormat instance for generating unique filenames for batch upload data. */
  private static final DateFormat DF = new SimpleDateFormat("yyyyMMddHHmmssSSS");
  /** DateFormat instance for creating encounters' verbatim dates. */
  private static final DateFormat DFD = new SimpleDateFormat("yyyy-MM-dd");
  /** DateFormat instance for creating encounters' verbatim dates. */
  private static final DateFormat DFDT = new SimpleDateFormat("yyyy-MM-dd HH:mm");
  // TODO: Enhance regex for URL matching.
  /** Regex for matching image download URLs. */
  private static final String REGEX_URL = "^(https?|ftp|file)://.+$";
  /** Map of batch processors currently operating. */
  private static final Map<String, BatchProcessor> processMap = new HashMap<String, BatchProcessor>();
  /** Support for running asynchronous tasks. */
  private static final ExecutorService taskExecutor;

  static {
    // Create a single executor service to process batch uploads.
    // This should be sufficient, as batch uploads are likely infrequent.
    taskExecutor = Executors.newSingleThreadExecutor();
  }

  @Override
  public void init() throws ServletException {
    super.init();
    try {
      registerMethodGET("start");
      registerMethodGET("templateInd", "templateEnc", "templateMea", "templateMed", "templateSam");
      registerMethodPOST("uploadBatchData", "confirmBatchDataUpload");
      registerMethodGET("getBatchProgress");
    }
    catch (DelegateNotFoundException ex) {
      throw new ServletException(ex);
    }
  }

  @Override
  public void destroy() {
    super.destroy();
    if (taskExecutor != null)
      taskExecutor.shutdownNow();
  }

  private ResourceBundle getResources(Locale loc) {
    return ResourceBundle.getBundle("bundles/batchUpload", loc);
  }

  /**
   * Removes all batch upload session variables.
   */
  public static void flushSessionInfo(HttpServletRequest req) {
    HttpSession session = req.getSession(false);
    if (session != null) {
      for (Enumeration e = session.getAttributeNames(); e.hasMoreElements();) {
        String s = (String)e.nextElement();
        if (s.matches("^(?i)batch.*$"))
          session.removeAttribute(s);
      }
    }
  }

  @Override
  public String getServletInfo() {
    return "BatchUpload, Copyright 2013 Giles Winstanley / Wild Book / wildme.org";
  }

  /**
   * Entrance point for the BatchUpload service, which is used whenever an
   * unrecognised delegate method is specified.
   * This method checks for an ongoing process for the current user, and if
   * one exists, redirects to the progress page. If not, it redirects to the
   * start page to create a new process.
   */
  protected void handleDelegateNotFound(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
    res.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unsupported delegate method specified.");
  }

  private void handleException(HttpServletRequest req, HttpServletResponse res, Throwable t) throws ServletException, IOException {
    log.warn(t.getMessage(), t);
    req.setAttribute("javax.servlet.jsp.jspException", t);
    getServletContext().getRequestDispatcher(JSP_ERROR).forward(req, res);
  }

  /**
   * Method to serve a CSV template file for batch upload of data.
   */
  private void templateGen(HttpServletRequest req, HttpServletResponse res, int index) throws ServletException, IOException {
    try {
      BatchParser bp = new BatchParser(req.getLocale());
      String template = bp.generateTemplates()[index];
      int len = template.getBytes("UTF-8").length;
      res.setCharacterEncoding("UTF-8");
      res.setContentType("text/csv; charset=UTF-8");
      res.setHeader("Content-Disposition", "attachment; filename=\"" + BTF[index] + "\"");
      res.setContentLength(len);
//      log.debug(String.format("Sending template '%s' to client (%d bytes)", BTF[index], len));
      PrintWriter pw = res.getWriter();
      pw.append(template);
      pw.flush();
    } catch (Throwable th) {
      handleException(req, res, th);
    }
  }

  /**
   * Method to serve a CSV template file for batch upload of individual data.
   */
  public void templateInd(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
    templateGen(req, res, 0);
  }

  /**
   * Method to serve a CSV template file for batch upload of encounter data.
   */
  public void templateEnc(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
    templateGen(req, res, 1);
  }

  /**
   * Method to serve a CSV template file for batch upload of media data.
   */
  public void templateMea(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
    templateGen(req, res, 2);
  }

  /**
   * Method to serve a CSV template file for batch upload of media data.
   */
  public void templateMed(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
    templateGen(req, res, 3);
  }

  /**
   * Method to serve a CSV template file for batch upload of sample data.
   */
  public void templateSam(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
    templateGen(req, res, 4);
  }

  /**
   * Entrance point for the BatchUpload service.
   * This method checks for an ongoing process for the current user, and if
   * one exists, redirects to the progress page. If not, it redirects to the
   * start page to create a new process.
   */
  public void start(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
    try {
      HttpSession session = req.getSession();

      List<String> errors = (List<String>)session.getAttribute(SESSION_KEY_ERRORS);
      List<String> warnings = (List<String>)session.getAttribute(SESSION_KEY_WARNINGS);
      boolean hasErrors = errors != null && !errors.isEmpty();
      boolean hasWarnings = warnings != null && !warnings.isEmpty();

      // Check for batch processor already assigned for this user.
      BatchProcessor proc = (BatchProcessor)session.getAttribute(SESSION_KEY_TASK);
      if (proc == null) {
        proc = processMap.get(req.getRemoteUser());
      }
      if (proc != null && !proc.isTerminated()) {
        getServletConfig().getServletContext().getRequestDispatcher(JSP_PROGRESS).forward(req, res);
        return;
      }

      // Clear any possible old data, then redirect to main page.
      flushSessionInfo(req);
      getServletConfig().getServletContext().getRequestDispatcher(JSP_MAIN).forward(req, res);

    } catch (Throwable th) {
      log.warn(th.getMessage(), th);
      handleException(req, res, th);
    }
  }

  /**
   * Delegate method for mediating data upload for batch data processing.
   * The pipeline goes as follows:
   * <ol>
   * <li>Checks for existing batch data processor, and forwards to progress tracker if so.</li>
   * <li>Handle batch data from web page, saving files to temporary folder.</li>
   * <li>Parse batch data (CSV files) to validate data.</li>
   * <li>Save successfully parsed batch data (in session), then ask user for confirmation.</li>
   * </ol>
   * <p>As these steps are followed, any errors encountered are reported back
   * to the user, and progress is halted, giving the user a chance to perform
   * the operation again.</p>
   * <p><strong>Required input:</strong></p>
   * <ul>
   * <li>CSV batch data files (via URL-encoded HTML form input).</li>
   * </ul>
   * <p><strong>Output:</strong></p>
   * <ul>
   * <li>CSV batch data files (saved in session: batchInd/batchEnc/batchMed).</li>
   * <li>BatchProcessor instance (saved in session: BatchTask).</li>
   * <li>Future instance (saved in session: BatchTaskFuture).</li>
   * </ul>
   */
  public void uploadBatchData(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
    try {
      HttpSession session = req.getSession();
      
      String context="context0";
      context=ServletUtilities.getContext(req);

      Locale loc = req.getLocale();
      ResourceBundle bundle = getResources(loc);
      List<String> errors = new ArrayList<String>();
      session.setAttribute(SESSION_KEY_ERRORS, errors);
      List<String> warnings = new ArrayList<String>();
      session.setAttribute(SESSION_KEY_WARNINGS, warnings);

      // Setup folder/file paths.
      File batchDataDir = new File(getDataDir(context), BATCH_DATA_DIR);
      if (!batchDataDir.exists()) {
        if (!batchDataDir.mkdirs()) {
          errors.add(bundle.getString("batchUpload.error.MakeDir"));
        }
      }

      // Generate unique identifier for this upload.
      // (Millisecond precision should be good enough for non-automated code.)
      String uniq = DataUtilities.createUniqueId();
      // Process submitted files.
      MultipartParser mp = new MultipartParser(req, CommonConfiguration.getMaxMediaSizeInMegabytes(context) * 1048576);
      Part part = null;
      String type = "batchInd";
      while ((part = mp.readNextPart()) != null) {
        if (part.isFile()) {
          FilePart filePart = (FilePart)part;
          // Check type of file being uploaded.
          if (filePart.getName().equals("csvEnc")) {
            type = "batchEnc";
          } else if (filePart.getName().equals("csvMea")) {
            type = "batchMea";
          } else if (filePart.getName().equals("csvMed")) {
            type = "batchMed";
          } else if (filePart.getName().equals("csvSam")) {
            type = "batchSam";
          }
          String filename = String.format("%s-%s.csv", type, uniq);
          // If another upload for this type already exists, delete it.
          File prev = (File)session.getAttribute(type);
          if (prev != null && prev.exists()) {
            if (!prev.delete())
              log.warn(String.format("Failed to delete old batch data file: %s", prev.getAbsolutePath()));
          }
          // Upload/save file.
          File dataFile = new File(batchDataDir, filename);
          long fileSize = filePart.writeTo(dataFile);
//          log.trace(String.format("Written %d bytes to file %s", fileSize, dataFile.getName()));
//          log.trace(String.format("Upload batch data to file: %s", dataFile.getName()));
          // Assign file to session for later processing.
          if (dataFile.exists())
            session.setAttribute(type, dataFile);
        }
      }

      // VALIDATE INPUT.

      // Check uploaded data files.
      String[] batchTypes = { "Ind", "Enc", "Mea", "Med", "Sam" };
      File[] batchFiles = new File[batchTypes.length];
      for (int i = 0; i < batchTypes.length; i++) {
        batchFiles[i] = (File)session.getAttribute("batch" + batchTypes[i]);
        if (i < 2 && batchFiles[i] == null) {
          errors.add(bundle.getString("batchUpload.error.NoDataFile" + batchTypes[i]));
        } else if (batchFiles[i] != null && !batchFiles[i].exists()) {
          errors.add(bundle.getString("batchUpload.error.DataFileUploadErr" + batchTypes[i]));
        }
      }

      // PROCESS DATA.

      Map<SinglePhotoVideo, BatchMedia> mapMedia = null;
      List<MarkedIndividual> listInd = null;
      List<Encounter> listEnc = null;
      List<Measurement> listMea = null;
      List<BatchMedia> listMed = null;
      List<TissueSample> listSam = null;
      if (errors.isEmpty()) {
        // Parse data files & check for errors.
        BatchParser bp = new BatchParser(loc, batchFiles[0], batchFiles[1]);
        if (batchFiles[2] != null)
          bp.setFileMeasurements(batchFiles[2]);
        if (batchFiles[3] != null)
          bp.setFileMedia(batchFiles[3]);
        if (batchFiles[4] != null)
          bp.setFileSamples(batchFiles[4]);
        if (bp.parseBatchData()) {
          List<Map<String, Object>> dataInd = bp.getIndividualData();
          List<Map<String, Object>> dataEnc = bp.getEncounterData();
          List<Map<String, Object>> dataMea = bp.getMeasurementData();
          List<Map<String, Object>> dataMed = bp.getMediaData();
          List<Map<String, Object>> dataSam = bp.getSampleData();

          // Convert data into object instances, then process to assign relationships,
          // getting back a map of media relationships for later processing.
          listInd = parseInd(dataInd, errors, bundle);
          listEnc = parseEnc(dataEnc, errors, bundle);
          listMea = parseMea(dataMea, errors, bundle);
          listMed = parseMed(dataMed, errors, bundle);
          listSam = parseSam(dataSam, errors, bundle);
          mapMedia = assignDataRelationshipsAndValidate(req, res, listInd, listEnc, listMea, listMed, listSam, errors, bundle);

          // OUTPUT RESULT.

          BatchData batchData = new BatchData(listInd, listEnc);
          batchData.setListMea(listMea);
          batchData.setListMed(listMed, mapMedia);
          batchData.setListSam(listSam);
          log.debug("Parsed batch data: {}", batchData);
          if (errors.isEmpty()) {
            session.setAttribute(SESSION_KEY_DATA, batchData);
          }
        } else {
          errors.addAll(bp.getErrors());
        }
      }

      // Remove any previously attached batch data files.
      for (Enumeration e = session.getAttributeNames(); e.hasMoreElements();) {
        String s = (String)e.nextElement();
        if (s.matches("^batch(Ind|Enc|Mea|Med|Sam)$")) {
          File f = (File)session.getAttribute(s);
          if (f.exists())
            f.delete();
          session.removeAttribute(s);
        }
      }

      if (errors.isEmpty()) {
        // Return to the view to ask user if they really want to proceed.
        getServletConfig().getServletContext().getRequestDispatcher(JSP_CONFIRM).forward(req, res);
      } else {
        // Return to main page to report errors & start over.
        getServletConfig().getServletContext().getRequestDispatcher(JSP_MAIN).forward(req, res);
      }

    } catch (Throwable th) {
      log.warn(th.getMessage(), th);
      handleException(req, res, th);
    }
  }

  /**
   * Delegate method for performing batch data upload.
   * This method should be called when the user has confirmed they want to
   * proceed with the batch data upload, as it does the persistence work.
   * <p><strong>Required input:</strong></p>
   * <ul>
   * <li>CSV batch data files (saved in session: batchInd/batchEnc/batchMed).</li>
   * <li>BatchProcessor instance (saved in session: BatchTask).</li>
   * <li>Future instance (saved in session: BatchTaskFuture).</li>
   * <li></li>
   * </ul>
   * <p><strong>Output:</strong></p>
   * <ul>
   * <li>CSV batch data files (saved in session: batchInd/batchEnc/batchMed).</li>
   * <li>BatchProcessor instance (saved in session: BatchTask).</li>
   * <li>Future instance (saved in session: BatchTaskFuture).</li>
   * <li></li>
   * </ul>
   */
  public void confirmBatchDataUpload(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
    try {
      HttpSession session = req.getSession(false);
      
      String context="context0";
      context=ServletUtilities.getContext(req);

      Locale loc = req.getLocale();
      ResourceBundle bundle = getResources(loc);
      List<String> errors = (List<String>)session.getAttribute(SESSION_KEY_ERRORS);
      List<String> warnings = (List<String>)session.getAttribute(SESSION_KEY_WARNINGS);
      if (errors == null)
      {
        log.warn("Errors should already be assigned; check code for pathway anomalies");
        errors = new ArrayList<String>();
        session.setAttribute(SESSION_KEY_ERRORS, errors);
      }
      if (warnings == null)
      {
        log.warn("Warnings should already be assigned; check code for pathway anomalies");
        warnings = new ArrayList<String>();
        session.setAttribute(SESSION_KEY_WARNINGS, warnings);
      }

      // Find any currently running batch tasks assigned by this user.
      BatchProcessor proc = (BatchProcessor)session.getAttribute(SESSION_KEY_TASK);
      if (proc == null) {
        proc = processMap.get(req.getRemoteUser());
      }
      if (proc != null && !proc.isTerminated()) {
        getServletConfig().getServletContext().getRequestDispatcher(JSP_PROGRESS).forward(req, res);
        return;
      }

      BatchData data = (BatchData)session.getAttribute(SESSION_KEY_DATA);
      if (data == null) {
        errors.add(bundle.getString("batchUpload.error.DataTransferErr"));
        log.error("No batch data found in session");
      }

      if (errors.isEmpty()) {

        // NOTE: This might be updated to use Servlet 3.0 API asynchronous task mechanism in future.
        proc = new BatchProcessor(data.listInd, data.listEnc, errors, warnings, loc,context);
        proc.setMapMedia(data.mapMedia);
        proc.setListSam(data.listSam);
        proc.setServletContext(getServletContext(),context);
        proc.setUsername(req.getRemoteUser());
        proc.setURLLocation(CommonConfiguration.getURLLocation(req));
        log.info(String.format("Assigning batch processor for user %s: %s", req.getRemoteUser(), proc));
        processMap.put(req.getRemoteUser(), proc);

        session.setAttribute(SESSION_KEY_TASK, proc);
        session.setAttribute(SESSION_KEY_TASKFUTURE, taskExecutor.submit(proc));
        getServletConfig().getServletContext().getRequestDispatcher(JSP_PROGRESS).forward(req, res);

      } else {
        // Should never happen, but flag just in case.
        log.warn("Invalid BatchUpload condition has occurred");
        flushSessionInfo(req);
        getServletConfig().getServletContext().getRequestDispatcher(JSP_MAIN).forward(req, res);
      }
    } catch (Throwable th) {
      handleException(req, res, th);
    }
  }

  /**
   * Returns the progress of the current user's current {@code BatchProcessor}
   * instance as a JSON object, for parsing/display by AJAX in JSP page.
   */
  public void getBatchProgress(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
    try {
      HttpSession session = req.getSession(false);

      // Find BatchProcessor in session.
      BatchProcessor proc = (BatchProcessor)session.getAttribute(SESSION_KEY_TASK);
      if (proc == null) {
        proc = processMap.get(req.getRemoteUser());
      }

      // Return progress (or error) as JSON object.
      Map<String, Object> map = new HashMap<>();
      if (proc == null)
        map.put("error", "true");
      else {
        map.put("status", proc.getStatus().toString());
        map.put("phase", proc.getPhase().toString());
        map.put("progress", (int)(100 * proc.getProgress()));
      }
      JSONObject jo = new JSONObject(map);

      res.setContentType("application/json; charset=UTF-8");
      res.setCharacterEncoding("UTF-8");
      res.getOutputStream().write(jo.toString().getBytes("UTF-8"));
      res.getOutputStream().flush();
    } catch (Throwable th) {
      handleException(req, res, th);
    }
  }

  /**
   * Processes raw object lists to assign relationships between them,
   * validate the parsed data, and flags errors as appropriate.
   * NOTE: This method is currently ludicrously long as it requires a lot of
   * cross-referencing; ideally it should be broken up a bit, but that would
   * require a lot of reference passing between methods.
   * @param listInd list of individuals
   * @param listEnc list of encounters
   * @param listMea list of measurements
   * @param listMed list of media items
   * @param listSam list of samples
   * @param errors list of error messages
   * @param bundle ResourceBundle for creating error messages
   * @return A map of {@code SinglePhotoVideo} instances to previously parsed media items ready for further processing
   */
  private Map<SinglePhotoVideo, BatchMedia> assignDataRelationshipsAndValidate(
          HttpServletRequest req,
          HttpServletResponse res,
          List<MarkedIndividual> listInd,
          List<Encounter> listEnc,
          List<Measurement> listMea,
          List<BatchMedia> listMed,
          List<TissueSample> listSam,
          List<String> errors, ResourceBundle bundle) throws IOException {

    // Get reference to persistent store.
    String context="context0";
    context=ServletUtilities.getContext(req);
    Shepherd shepherd = new Shepherd(context);
    shepherd.setAction("BatchUpload.class");

    // Validate individuals.
    List<String> indIDs = new ArrayList<String>();
    for (MarkedIndividual x : listInd) {
      // Check for repetition.
      if (indIDs.contains(x.getIndividualID())) {
        String msg = bundle.getString("batchUpload.verifyError.repeatIndividualID");
        errors.add(MessageFormat.format(msg, x.getIndividualID()));
      } else {
        indIDs.add(x.getIndividualID().trim());
      }
      if (shepherd.isMarkedIndividual(x.getIndividualID())) {
        String msg = bundle.getString("batchUpload.verifyError.existsIndividualID");
        errors.add(MessageFormat.format(msg, x.getIndividualID()));
      }
    }

    // Get valid values of certain data.
    List<String> listSex = CommonConfiguration.getIndexedPropertyValues("sex",context);
    List<String> listTax = CommonConfiguration.getIndexedPropertyValues("genusSpecies",context);
    List<String> listLS = CommonConfiguration.getIndexedPropertyValues("lifeStage",context);
    List<String> listPC = CommonConfiguration.getIndexedPropertyValues("patterningCode",context);
    List<String> listLoc = CommonConfiguration.getIndexedPropertyValues("locationID",context);
    List<String> listSP = CommonConfiguration.getIndexedPropertyValues("samplingProtocol",context);

    // Validate encounters.
    Map<String, Encounter> mapEnc = new HashMap<String, Encounter>();
    Set<String> locIDs = new HashSet<String>();
    for (Encounter x : listEnc) {
      // Check for repetition.
      if (mapEnc.containsKey(x.getEncounterNumber())) {
        String msg = bundle.getString("batchUpload.verifyError.repeatEncounter");
        errors.add(MessageFormat.format(msg, x.getEncounterNumber()));
      } else {
        mapEnc.put(x.getEncounterNumber(), x);
      }
      // Check/assign individual.
      // Individuals created on import have their Encounters assigned to them.
      // Encounters for existing Individuals are left unassigned, but with the
      // IndividualID specified, so they can be processed by the BatchProcessor.
      String indID = x.getIndividualID();
      if (indID != null)
        indID = indID.trim();
      if (indID == null || "".equals(indID)) {
        // NOTE: Currently requires setting explicit "Unassigned" instead of null,
        // but this would break the logic here; should be done in BatchProcessor.
        x.setIndividualID(null);
      } else if (!indIDs.contains(x.getIndividualID())) {
        // Check for existing individual in database.
        if (!shepherd.isMarkedIndividual(x.getIndividualID())) {
          String msg = bundle.getString("batchUpload.verifyError.encounterUnknownIndividual");
          errors.add(MessageFormat.format(msg, x.getEncounterNumber(), x.getIndividualID()));
        }
      } else {
        MarkedIndividual ind = listInd.get(indIDs.indexOf(indID));
        ind.addEncounter(x, context);
      }

      // Check sex.
      String sex = x.getSex();
      if (!listSex.isEmpty() && !listSex.contains(sex)) {
        String msg = bundle.getString("batchUpload.verifyError.encounterInvalidSex");
        errors.add(MessageFormat.format(msg, x.getEncounterNumber(), sex));
      }

      // Check genus/species.
      if (x.getGenus() != null && x.getSpecificEpithet() != null) {
        String tax = x.getGenus() + " " + x.getSpecificEpithet();
        if (!listTax.isEmpty() && !listTax.contains(tax)) {
          String msg = bundle.getString("batchUpload.verifyError.encounterInvalidTaxonomy");
          errors.add(MessageFormat.format(msg, x.getEncounterNumber(), tax));
        }
      }

      // Check life stage.
      String lifeStage = x.getLifeStage();
      if (lifeStage != null && !listLS.isEmpty() && !listLS.contains(lifeStage)) {
        String msg = bundle.getString("batchUpload.verifyError.encounterInvalidLifeStage");
        errors.add(MessageFormat.format(msg, x.getEncounterNumber(), lifeStage));
      }

      // Check patterning code.
      String patterningCode = x.getPatterningCode();
      if (patterningCode != null && !listPC.isEmpty() && !listPC.contains(patterningCode)) {
        String msg = bundle.getString("batchUpload.verifyError.encounterInvalidPatterningCode");
        errors.add(MessageFormat.format(msg, x.getEncounterNumber(), patterningCode));
      }

      // Check location.
      String locID = x.getLocationID();
      if (locID == null && listLoc.size() == 1) {
        locID = listLoc.get(0);
        x.setLocationID(locID);
      }
      if (locID == null) {
        String msg = bundle.getString("batchUpload.verifyError.encounterNoLocation");
        errors.add(MessageFormat.format(msg, x.getEncounterNumber()));
      }
      else {
        if (!listLoc.contains(locID)) {
          String msg = bundle.getString("batchUpload.verifyError.encounterInvalidLocation");
          errors.add(MessageFormat.format(msg, x.getEncounterNumber(), locID));
        }
        else
          locIDs.add(locID);
      }

      // Check measurement sampling protocol. TODO: Check against measurements
      String protocol = x.getMeasureUnits();
      if (protocol == null && listSP.size() == 1) {
        x.setMeasureUnits(listSP.get(0));
      }
      if (protocol != null && !listSP.isEmpty() && !listSP.contains(protocol)) {
        String msg = bundle.getString("batchUpload.verifyError.encounterInvalidSamplingProtocol");
        errors.add(MessageFormat.format(msg, protocol));
      }
    }
    // Check user has permission to import for specified regions.
    List<Role> roles = shepherd.getAllRolesForUser(req.getRemoteUser());
    for (String locID : locIDs) {
      boolean hasLocationPermission = false;
      for (Role r : roles) {
        if (r.getRolename().equals(locID)) {
          hasLocationPermission = true;
          break;
        }
      }
      if (!hasLocationPermission) {
        String msg = bundle.getString("batchUpload.verifyError.encounterInvalidLocationPermission");
        errors.add(MessageFormat.format(msg, locID));
      }
    }

    // Check that each individual has encounters.
    for (MarkedIndividual x : listInd) {
      if (x.getEncounters().isEmpty()) {
        String msg = bundle.getString("batchUpload.verifyError.individualNoEncounter");
        errors.add(MessageFormat.format(msg, x.getIndividualID()));
      }
    }


    // Get valid values of certain data.
    List<String> listMT = CommonConfiguration.getIndexedPropertyValues("measurement",context);
    List<String> listMU = CommonConfiguration.getIndexedPropertyValues("measurementUnits",context);

    // Check/assign measurements.
    Set<Measurement> badMeaNoEnc = new LinkedHashSet<Measurement>();
    Set<String> badMeaInvalidEnc = new LinkedHashSet<String>();
    Set<String> badMeaInvalidType = new LinkedHashSet<String>();
    Set<String> badMeaInvalidUnits = new LinkedHashSet<String>();
    Set<String> badMeaInvalidProtocol = new LinkedHashSet<String>();
    if (listMea != null) {
      for (Measurement x : listMea) {
        String encID = x.getCorrespondingEncounterNumber();
        if (encID == null || "".equals(encID)) {
          badMeaNoEnc.add(x);
        } else {
          // Ensure encounter matches one already parsed.
          try {
            Encounter enc = mapEnc.get(x.getCorrespondingEncounterNumber());
            enc.setMeasurement(x,shepherd);
            // TODO: Fill shared data from encounter?
          } catch (Exception ex) {
            badMeaInvalidEnc.add(x.getCorrespondingEncounterNumber());
          }
        }

        // Check measurement type.
        String type = x.getType();
        if (!listMT.isEmpty() && !listMT.contains(type))
          badMeaInvalidType.add(type);

        // Check measurement units.
        String units = x.getUnits();
        if (!listMU.isEmpty() && !listMU.contains(units))
          badMeaInvalidUnits.add(units);

        // Check measurement protocol.
        String protocol = x.getSamplingProtocol();
        if (!listSP.isEmpty() && !listSP.contains(protocol))
          badMeaInvalidProtocol.add(protocol);
      }
      if (!badMeaNoEnc.isEmpty())
        errors.add(bundle.getString("batchUpload.verifyError.measurementNoEncounter"));
      for (String s : badMeaInvalidEnc) {
        String msg = bundle.getString("batchUpload.verifyError.measurementInvalidEncounter");
        errors.add(MessageFormat.format(msg, s));
      }
      for (String s : badMeaInvalidType) {
        String msg = bundle.getString("batchUpload.verifyError.measurementInvalidType");
        errors.add(MessageFormat.format(msg, s));
      }
      for (String s : badMeaInvalidUnits) {
        String msg = bundle.getString("batchUpload.verifyError.measurementInvalidUnits");
        errors.add(MessageFormat.format(msg, s));
      }
      for (String s : badMeaInvalidProtocol) {
        String msg = bundle.getString("batchUpload.verifyError.measurementInvalidProtocol");
        errors.add(MessageFormat.format(msg, s));
      }
    }

    // Validate/assign media, and assign target filename on local filesystem.
    // This does NOT download the media items, just prepares data relating to them.
    File usersDir = CommonConfiguration.getUsersDataDirectory(getServletContext(), context);
    File dataDir = new File(usersDir, req.getRemoteUser());
    Map<SinglePhotoVideo, BatchMedia> map = new HashMap<SinglePhotoVideo, BatchMedia>();
    Set<String> spvNames = new HashSet<String>();
    Set<BatchMedia> badMedNoEnc = new LinkedHashSet<BatchMedia>();
    Set<String> badMedInvalidEnc = new LinkedHashSet<String>();
    Set<String> badMedDupFile = new LinkedHashSet<String>();
    Set<String> badMedInvalidType = new LinkedHashSet<String>();
    Set<BatchMedia> badMedNoUrl = new LinkedHashSet<BatchMedia>();
    Set<String> badMedInvalidUrl = new LinkedHashSet<String>();
    Set<String> badMedInvalidKeyword = new LinkedHashSet<String>();
    if (listMed != null) {
      for (BatchMedia bm : listMed) {
        if (bm.getEncounterNumber() == null)
          badMedNoEnc.add(bm);
        // Check media URL.
        if (bm.getMediaURL() == null) {
          badMedNoUrl.add(bm);
          continue;
        } else {
          if (bm.getMediaURL().toLowerCase(Locale.US).startsWith("www.")) {
            bm.setMediaURL("http://" + bm.getMediaURL());
          } else if (bm.getMediaURL().toLowerCase(Locale.US).startsWith("ftp.")) {
            bm.setMediaURL("ftp://" + bm.getMediaURL());
          }
          // Validate URL format. TODO: create better regex for URL matching.
          if (!bm.getMediaURL().matches(REGEX_URL))
            badMedInvalidUrl.add(bm.getMediaURL());
        }
        // Check media keywords.
        // NOTE: Shepherd returns an iterator instead of a collection, so we
        // have to copy the results to use it without querying the database
        // repeatedly. Keywords are checked for validity against database
        // (case-insensitive) and non-compliant ones are flagged as errors.
        List<Keyword> listKWA = new ArrayList<Keyword>();
        for (Iterator<Keyword> iter = shepherd.getAllKeywords(); iter.hasNext();) {
          listKWA.add(iter.next());
        }
        if (bm.getKeywords() != null) {
          List<String> listKWALC = new ArrayList<String>();
          for (Keyword kw : listKWA)
            listKWALC.add(kw.getReadableName().toLowerCase(Locale.US));
          List<String> keywords = new ArrayList<String>();
          for (String s : bm.getKeywords())
            keywords.add(s);
          for (int i = 0; i < keywords.size(); i++) {
            String s = keywords.get(i);
            int idx = listKWALC.indexOf(s.toLowerCase(Locale.US));
            if (idx >= 0) {
              keywords.set(i, listKWA.get(idx).getReadableName());
            } else {
              badMedInvalidKeyword.add(s);
            }
          }
          keywords.removeAll(badMedInvalidKeyword);
          bm.setKeywords(keywords.isEmpty() ? null : keywords.toArray(new String[keywords.size()]));
        }
        // Create/check filename for media item.
        String base = bm.getMediaURL().substring(bm.getMediaURL().lastIndexOf("/") + 1);
        // TODO: Ensure uniqueness of image filename instead of complaining at duplicate?
        String filename = String.format("%s", base);
        try {
          filename = URLDecoder.decode(filename, "UTF-8");
        }
        catch (UnsupportedEncodingException ex) {
          log.warn(ex.getMessage(), ex);
        }
        filename = ServletUtilities.cleanFileName(filename);
        // Check for duplicate file.
        if (spvNames.contains(filename))
          badMedDupFile.add(filename);
        spvNames.add(filename);
        // Check for invalid media type.
        if (!MediaUtilities.isAcceptableMediaFile(filename))
          badMedInvalidType.add(filename.substring(filename.lastIndexOf(".") + 1));
        if (bm.getEncounterNumber() != null) {
          // Get encounter instance, and assign data.
          File f = new File(dataDir, filename);
          Encounter enc = mapEnc.get(bm.getEncounterNumber());
          if (enc == null) {
            badMedInvalidEnc.add(bm.getEncounterNumber());
          } else {
            SinglePhotoVideo spv = new SinglePhotoVideo(enc.getEncounterNumber(), f);
            enc.addSinglePhotoVideo(spv);
            if (bm.getCopyrightOwner() != null && !"".equals(bm.getCopyrightOwner())) {
              spv.setCopyrightOwner(bm.getCopyrightOwner());
            }
            if (bm.getCopyrightStatement() != null && !"".equals(bm.getCopyrightStatement())) {
              spv.setCopyrightStatement(bm.getCopyrightStatement());
            }
            // Now map the two for further processing.
            map.put(spv, bm);
          }
        }
      }
      if (!badMedNoEnc.isEmpty())
        errors.add(bundle.getString("batchUpload.verifyError.mediaNoEncounter"));
      for (String s : badMedInvalidEnc) {
        String msg = bundle.getString("batchUpload.verifyError.mediaInvalidEncounter");
        errors.add(MessageFormat.format(msg, s));
      }
      for (String s : badMedDupFile) {
        String msg = bundle.getString("batchUpload.verifyError.mediaDuplicateFile");
        errors.add(MessageFormat.format(msg, s));
      }
      for (String s : badMedInvalidType) {
        String msg = bundle.getString("batchUpload.verifyError.mediaInvalidType");
        errors.add(MessageFormat.format(msg, s));
      }
      if (!badMedNoUrl.isEmpty())
        errors.add(bundle.getString("batchUpload.verifyError.mediaNoURL"));
      for (String s : badMedInvalidUrl) {
        String msg = bundle.getString("batchUpload.verifyError.mediaInvalidURL");
        errors.add(MessageFormat.format(msg, s));
      }
      // NOTE: Comment this section to ignore bad keywords.
      for (String s : badMedInvalidKeyword) {
        String msg = bundle.getString("batchUpload.verifyError.mediaInvalidKeyword");
        errors.add(MessageFormat.format(msg, s));
      }
    }

    // Get valid values of certain data.
    List<String> listTT = CommonConfiguration.getIndexedPropertyValues("tissueType",context);

    // Check/assign samples.
    Set<TissueSample> badSamNoEnc = new LinkedHashSet<TissueSample>();
    Set<String> badSamInvalidEnc = new LinkedHashSet<String>();
    Set<String> badSamInvalidType = new LinkedHashSet<String>();
    if (listSam != null) {
      for (TissueSample x : listSam) {
        String encID = x.getCorrespondingEncounterNumber();
        if (encID == null || "".equals(encID)) {
          badSamNoEnc.add(x);
        } else {
          // Ensure encounter matches one already parsed.
          try {
            Encounter enc = mapEnc.get(x.getCorrespondingEncounterNumber());
            enc.addTissueSample(x);
            // TODO: Fill shared data from encounter?
          } catch (Exception ex) {
            badSamInvalidEnc.add(x.getCorrespondingEncounterNumber());
          }
        }

        // Check tissue type.
        String tissueType = x.getTissueType();
        if (!listTT.isEmpty() && !listTT.contains(tissueType))
          badSamInvalidType.add(tissueType);
      }
      if (!badSamNoEnc.isEmpty())
        errors.add(bundle.getString("batchUpload.verifyError.sampleNoEncounter"));
      for (String s : badSamInvalidEnc) {
        String msg = bundle.getString("batchUpload.verifyError.sampleInvalidEncounter");
        errors.add(MessageFormat.format(msg, s));
      }
      for (String s : badSamInvalidType) {
        String msg = bundle.getString("batchUpload.verifyError.sampleInvalidTissueType");
        errors.add(MessageFormat.format(msg, s));
      }
    }

    // Close Shepherd reference.
    shepherd.closeDBTransaction();

    return map;
  }

  /**
   * Parses data from parsed CSV files into individuals.
   * @param dataInd map of parsed CSV data
   * @return a list of {@code MarkedIndividual}
   */
  private static List<MarkedIndividual> parseInd(List<Map<String, Object>> dataInd, List<String> errors, ResourceBundle bundle) {
    List<MarkedIndividual> list = new ArrayList<MarkedIndividual>();
    String pre = "individual.";

    for (Map<String, Object> map : dataInd) {
      MarkedIndividual x = new MarkedIndividual();
      x.setDateTimeCreated(ServletUtilities.getDate());
      x.setIndividualID((String)map.get(pre + "individualID"));
      x.setAlternateID((String)map.get(pre + "alternateID"));
      x.setSex((String)map.get(pre + "sex"));
      x.setNickName((String)map.get(pre + "nickName"));
      x.setNickNamer((String)map.get(pre + "nickNamer"));
      x.setSeriesCode((String)map.get(pre + "seriesCode"));
      x.setPatterningCode((String)map.get(pre + "patterningCode"));
      list.add(x);

      Object listDP = map.get(pre + "dynamicProperties");
      if (listDP != null) {
        for (Object o : (List)listDP) {
          String s = (String)o;
          if (s != null) {
            int pos = s.indexOf("=");
            if (pos > 0) {
              x.setDynamicProperty(s.substring(0, pos).trim(), s.substring(pos + 1).trim());
            } else {
              log.trace(String.format("Invalid dynamic property specification: %s", s));
              String msg = bundle.getString("batchUpload.parseError.invalidDynPropSpec");
              errors.add(MessageFormat.format(msg, bundle.getString("individual").toUpperCase(bundle.getLocale()), x.getIndividualID()));
            }
          }
        }
      }

      Object listIR = map.get(pre + "interestedResearchers");
      if (listIR != null) {
        for (Object o : (List)listIR) {
          x.addInterestedResearcher((String)o);
        }
      }

      Object listDF = map.get(pre + "dataFiles");
      if (listDF != null) {
        for (Object o : (List)listDF) {
          x.addDataFile((String)o);
        }
      }
    }

    return list;
  }

  /**
   * Parses data from parsed CSV files into encounters.
   * @param dataEnc map of parsed CSV data
   * @return a list of {@code Encounter}
   */
  private static List<Encounter> parseEnc(List<Map<String, Object>> dataEnc, List<String> errors, ResourceBundle bundle) {
    List<Encounter> list = new ArrayList<Encounter>();
    String pre = "encounter.";

    for (Map<String, Object> map : dataEnc) {
      Encounter x = new Encounter();

      x.setCatalogNumber(map.get(pre + "catalogNumber").toString());
      Object tempEID = map.get(pre + "eventID");
      if (tempEID != null)
        x.setEventID(map.get(pre + "eventID").toString());
      x.setIndividualID((String)map.get(pre + "individualID"));
      x.setAlternateID((String)map.get(pre + "alternateID"));

      // Get date fields.
      Date date = (Date)map.get(pre + "date");
      if (date != null) {
        int yy = date.getYear() + 1900;
        int mm = date.getMonth() + 1;
        int dd = date.getDate();
        x.setYear(yy);
        x.setMonth(mm);
        x.setDay(dd);
        // Get time fields.
        Date time = (Date)map.get(pre + "time");
        if (time != null) {
          int h = time.getHours();
          int m = time.getMinutes();
          x.setHour(h);
          // NOTE: Why minutes as string in Encounter?!
          x.setMinutes(String.format("%02d", m));
          synchronized(DFDT) {
            x.setVerbatimEventDate(DFDT.format(new Date(yy - 1900, mm - 1, dd, h, m)));
          }
        } else {
          // NOTE: Must set hour=-1 due to odd design of Encounter implementation.
          x.setHour(-1);
          synchronized(DFD) {
            x.setVerbatimEventDate(DFD.format(date));
          }
        }
      }

      x.setSex((String)map.get(pre + "sex"));
      x.setGenus((String)map.get(pre + "genus"));
      x.setSpecificEpithet((String)map.get(pre + "specificEpithet"));
      x.setLocationID((String)map.get(pre + "locationID"));
      x.setCountry((String)map.get(pre + "country"));
      x.setVerbatimLocality((String)map.get(pre + "verbatimLocality"));
      if (x.getVerbatimLocality() == null && x.getLocationID() != null)
        x.setVerbatimLocality(x.getLocationID());
      x.setDecimalLatitude((Double)map.get(pre + "decimalLatitude"));
      x.setDecimalLongitude((Double)map.get(pre + "decimalLongitude"));
      x.setMaximumDepthInMeters((Double)map.get(pre + "maximumDepthInMeters"));
      x.setMaximumElevationInMeters((Double)map.get(pre + "maximumElevationInMeters"));
      x.setLivingStatus((String)map.get(pre + "livingStatus"));
      x.setLifeStage((String)map.get(pre + "lifeStage"));
      Date tempDate = (Date)map.get(pre + "releaseDate");
      if (tempDate != null)
        x.setReleaseDate(tempDate.getTime());
      x.setSize((Double)map.get(pre + "size"));
      x.setSizeGuess((String)map.get(pre + "sizeGuess"));
      x.setPatterningCode((String)map.get(pre + "patterningCode"));
      x.setDistinguishingScar((String)map.get(pre + "distinguishingScar"));
      x.setOtherCatalogNumbers((String)map.get(pre + "otherCatalogNumbers"));
      x.setDWCGlobalUniqueIdentifier((String)map.get(pre + "occurrenceID"));
      x.setOccurrenceRemarks((String)map.get(pre + "occurrenceRemarks"));
      x.setRecordedBy((String)map.get(pre + "recordedBy"));
      x.setBehavior((String)map.get(pre + "behavior"));
      list.add(x);

      Object listDP = map.get(pre + "dynamicProperties");
      if (listDP != null) {
        for (Object o : (List)listDP) {
          String s = (String)o;
          if (s != null) {
            int pos = s.indexOf("=");
            if (pos > 0) {
              x.setDynamicProperty(s.substring(0, pos).trim(), s.substring(pos + 1).trim());
            } else {
              log.trace(String.format("Invalid dynamic property specification: %s", s));
              String msg = bundle.getString("batchUpload.parseError.invalidDynPropSpec");
              errors.add(MessageFormat.format(msg, bundle.getString("encounter").toUpperCase(bundle.getLocale()), x.getIndividualID()));
            }
          }
        }
      }

      x.setMatchedBy((String)map.get(pre + "identificationRemarks"));
      x.addComments((String)map.get(pre + "researcherComments"));

      String temp = (String)map.get(pre + "informOthers");
      if (temp != null)
        x.setInformOthers(temp);

      x.setSubmitterOrganization((String)map.get(pre + "submitterOrganization"));
      x.setSubmitterProject((String)map.get(pre + "submitterProject"));
      x.setSubmitterName((String)map.get(pre + "submitterName"));
      x.setSubmitterEmail((String)map.get(pre + "submitterEmail"));
      x.setSubmitterAddress((String)map.get(pre + "submitterAddress"));
      x.setSubmitterPhone((String)map.get(pre + "submitterPhone"));

      temp = (String)map.get(pre + "photographerName");
      if (temp != null)
        x.setPhotographerName(temp);
      temp = (String)map.get(pre + "photographerEmail");
      if (temp != null)
        x.setPhotographerEmail(temp);
      temp = (String)map.get(pre + "photographerPhone");
      if (temp != null)
        x.setPhotographerPhone(temp);
      temp = (String)map.get(pre + "photographerAddress");
      if (temp != null)
        x.setPhotographerAddress(temp);

      Object listIR = map.get(pre + "interestedResearchers");
      if (listIR != null) {
        for (Object o : (List)listIR) {
          x.addInterestedResearcher((String)o);
        }
      }

      x.setOKExposeViaTapirLink((Boolean)map.get(pre + "okExposeViaTapirLink"));
    }

    return list;
  }

  /**
   * Parses data from parsed CSV files into measurements.
   * @param dataMea map of parsed CSV data
   * @return a list of {@code Measurement}
   */
  private static List<Measurement> parseMea(List<Map<String, Object>> dataMea, List<String> errors, ResourceBundle bundle) {
    List<Measurement> list = new ArrayList<Measurement>();
    if (dataMea == null)
      return list;
    String pre = "measurement.";

    for (Map<String, Object> map : dataMea) {
      String encNum = map.get(pre + "encounterNumber").toString();
      String type = (String)map.get(pre + "type");
      String units = (String)map.get(pre + "units");
      Double val = (Double)map.get(pre + "value");
      String protocol = (String)map.get(pre + "protocol");
      Measurement x = new Measurement(encNum, type, val, units, protocol);
      list.add(x);
    }

    return list;
  }

  /**
   * Parses data from parsed CSV files into media items.
   * @param dataMed map of parsed CSV data
   * @return a list of {@code BatchMedia}
   */
  private static List<BatchMedia> parseMed(List<Map<String, Object>> dataMed, List<String> errors, ResourceBundle bundle) {
    List<BatchMedia> list = new ArrayList<BatchMedia>();
    if (dataMed == null)
      return list;
    String pre = "media.";

    for (Map<String, Object> map : dataMed) {
      BatchMedia x = new BatchMedia();
      x.setEncounterNumber(map.get(pre + "encounterNumber").toString());
      x.setMediaURL((String)map.get(pre + "mediaURL"));
      x.setCopyrightOwner((String)map.get(pre + "copyrightOwner"));
      x.setCopyrightStatement((String)map.get(pre + "copyrightStatement"));
      list.add(x);

      List listKW = (List)map.get(pre + "keywords");
      if (listKW != null) {
        String[] keywords = new String[listKW.size()];
        int idx = 0;
        for (Object o : listKW) {
          keywords[idx++] = (String)o;
        }
        x.setKeywords(keywords);
      }
    }

    return list;
  }

  /**
   * Parses data from parsed CSV files into samples.
   * @param dataSam map of parsed CSV data
   * @return a list of {@code TissueSample}
   */
  private static List<TissueSample> parseSam(List<Map<String, Object>> dataSam, List<String> errors, ResourceBundle bundle) {
    List<TissueSample> list = new ArrayList<TissueSample>();
    if (dataSam == null)
      return list;
    String pre = "sample.";

    for (Map<String, Object> map : dataSam) {
      String encNum = map.get(pre + "encounterNumber").toString();
      String sampleID = (String)map.get(pre + "sampleID");
      TissueSample x = new TissueSample(encNum, sampleID);
      x.setTissueType((String)map.get(pre + "tissueType"));
      x.setAlternateSampleID((String)map.get(pre + "alternateID"));
      x.setStorageLabID((String)map.get(pre + "storageLab"));
      x.setSamplingProtocol((String)map.get(pre + "samplingProtocol"));
      x.setSamplingEffort((String)map.get(pre + "samplingEffort"));
      x.setFieldNumber((String)map.get(pre + "fieldNumber"));
      x.setFieldNotes((String)map.get(pre + "fieldNotes"));
      x.setEventRemarks((String)map.get(pre + "remarks"));
      x.setInstitutionID((String)map.get(pre + "institutionID"));
      x.setCollectionID((String)map.get(pre + "collectionID"));
      x.setDatasetID((String)map.get(pre + "datasetID"));
      x.setInstitutionCode((String)map.get(pre + "institutionCode"));
      x.setCollectionCode((String)map.get(pre + "collectionCode"));
      x.setDatasetName((String)map.get(pre + "datasetName"));
      list.add(x);
    }

    return list;
  }
}
