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

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ecocean.*;
import org.ecocean.mmutil.FileUtilities;
import org.ecocean.mmutil.MMAResultsProcessor;
import org.ecocean.mmutil.MantaMatcherUtilities;
import org.ecocean.mmutil.MediaUtilities;
import org.ecocean.mmutil.RegexFilenameFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Servlet to process/display MantaMatcher algorithm results.
 *
 * @author Giles Winstanley
 */
public final class MantaMatcher extends DispatchServlet {
  private static final long serialVersionUID = 1L;
  /** SLF4J logger instance for writing log entries. */
  private static final Logger log = LoggerFactory.getLogger(MantaMatcher.class);
  /** Request key for referencing MMA results data. */
  public static final String REQUEST_KEY_RESULTS = "mma-results";
  /** Path for referencing JSP page for error display. */
  private static final String JSP_ERROR = "/error_generic.jsp";
  /** Path for referencing JSP page for MMA results display. */
  private static final String JSP_MMA_RESULTS = "/encounters/mmaResults.jsp";

  @Override
  public void init() throws ServletException {
    super.init();
    try {
      registerMethodGET("displayResults", "displayResultsRegional");
      registerMethodPOST("resetMmaCompatible", "deleteAllOrphanMatcherFiles");
    }
    catch (DelegateNotFoundException ex) {
      throw new ServletException(ex);
    }
  }

  @Override
  public String getServletInfo() {
    return "MantaMatcherResults, Copyright 2014 Giles Winstanley / Wild Book / wildme.org";
  }

  @Override
  protected void handleDelegateNotFound(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
    res.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unsupported delegate method specified.");
  }

  private void handleException(HttpServletRequest req, HttpServletResponse res, Throwable t) throws ServletException, IOException {
    log.warn(t.getMessage(), t);
    t = t.getCause();
    while (t != null) {
      log.warn("\tCaused by: " + t.getMessage(), t);
      t = t.getCause();
    }
    req.setAttribute("javax.servlet.jsp.jspException", t);
    getServletContext().getRequestDispatcher(JSP_ERROR).forward(req, res);
  }

  public void displayResults(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
    try {
      // Parse encounter for which to get MantaMatcher algorithm results.
      String num = req.getParameter("spv");
      if (num == null || "".equals(num.trim())) {
        throw new IllegalArgumentException("Invalid SinglePhotoVideo specified");
      }
      
      String context="context0";
      context=ServletUtilities.getContext(req);
      
      Shepherd shepherd = new Shepherd(context);
      SinglePhotoVideo spv = shepherd.getSinglePhotoVideo(num);
      if (spv == null) {
        throw new IllegalArgumentException("Invalid SinglePhotoVideo specified: " + num);
      }

      Map<String, File> mmMap = MantaMatcherUtilities.getMatcherFilesMap(spv);
      MMAResultsProcessor.MMAResult mmaResults = parseResults(req, mmMap.get("TXT"), spv);
      req.setAttribute(REQUEST_KEY_RESULTS, mmaResults);
      getServletContext().getRequestDispatcher(JSP_MMA_RESULTS).forward(req, res);

    } catch (Exception ex) {
      handleException(req, res, ex);
    }
  }

  public void displayResultsRegional(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
    try {
      // Parse encounter for which to get MantaMatcher algorithm results.
      String num = req.getParameter("spv");
      if (num == null || "".equals(num.trim())) {
        throw new IllegalArgumentException("Invalid SinglePhotoVideo specified");
      }
      String context="context0";
      context=ServletUtilities.getContext(req);
      Shepherd shepherd = new Shepherd(context);
      SinglePhotoVideo spv = shepherd.getSinglePhotoVideo(num);
      if (spv == null) {
        throw new IllegalArgumentException("Invalid SinglePhotoVideo specified: " + num);
      }

      Map<String, File> mmMap = MantaMatcherUtilities.getMatcherFilesMap(spv);
      MMAResultsProcessor.MMAResult mmaResults = parseResults(req, mmMap.get("TXT-REGIONAL"), spv);
      req.setAttribute(REQUEST_KEY_RESULTS, mmaResults);
      getServletContext().getRequestDispatcher(JSP_MMA_RESULTS).forward(req, res);

    } catch (Exception ex) {
      handleException(req, res, ex);
    }
  }

  private MMAResultsProcessor.MMAResult parseResults(HttpServletRequest req, File mmaResults, SinglePhotoVideo spv)
          throws IOException, ParseException {
    assert spv != null;
    assert mmaResults != null;
    
    String context="context0";
    context=ServletUtilities.getContext(req);

    // Derive data folder info.
    String rootDir = getServletContext().getRealPath("/");
    File dataDir = new File(ServletUtilities.dataDir(context, rootDir));
    // Parse MantaMatcher results files ready for display.
    Shepherd shepherd = new Shepherd(context);
    try {
      // Load results file.
      String text = new String(FileUtilities.loadFile(mmaResults));
      // Parse results.
      return MMAResultsProcessor.parseMatchResults(shepherd, text, spv, dataDir);
    } finally {
      shepherd.closeDBTransaction();
    }
  }

  public void resetMmaCompatible(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
    String context="context0";
    context = ServletUtilities.getContext(req);
    Shepherd shepherd = new Shepherd(context);
    File dataDir = new File(ServletUtilities.dataDir(context, getServletContext().getRealPath("/")));

    try {

      // Perform MMA-compatible flag updates.
      int ok = 0, changed = 0, failed = 0;
      shepherd.beginDBTransaction();
      for (Iterator<Encounter> iter = shepherd.getAllEncounters(); iter.hasNext();) {
        Encounter enc = iter.next();
        boolean hasCR = MantaMatcherUtilities.checkEncounterHasMatcherFiles(enc, dataDir);
        boolean encCR = enc.getMmaCompatible();
        if ((hasCR && encCR) || (!hasCR && !encCR)) {
          ok++;
        }
        else {
          try {
            enc.setMmaCompatible(hasCR);
            changed++;
          }
          catch (Exception ex) {
            failed++;
            log.warn("Failed to set MMA-compatible flag for encounter: " + enc.getCatalogNumber(), ex);
          }
        }
      }
      shepherd.commitDBTransaction();
      shepherd.closeDBTransaction();

      // Write output to response.
      res.setCharacterEncoding("UTF-8");
      res.setContentType("text/html; charset=UTF-8");
      PrintWriter out = res.getWriter();
      out.println(ServletUtilities.getHeader(req));

      if (failed > 0) {
        out.println(String.format("<strong>Failure!</strong> I failed to reset all the MMA-compatibility flags; %d failed to update, %d were updated successfully, and %d were already correct.", failed, changed, ok));
      } else {
        out.println(String.format("<strong>Success!</strong> I have successfully reset the MMA-compatibility flag for %d encounters (%d were already correct).", changed, ok));
      }

      out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(req) + "/appadmin/admin.jsp\">Return to the Administration page.</a></p>\n");
      out.println(ServletUtilities.getFooter(context));

      out.flush();
      out.close();

    } catch (Exception ex) {
      shepherd.rollbackDBTransaction();
      shepherd.closeDBTransaction();
      handleException(req, res, ex);
    }
  }

  // Admin utility method to scan encounters & their data folders for
  // orphaned files relating to the MantaMatcher algorithm, and delete them.
  public void deleteAllOrphanMatcherFiles(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
    String context="context0";
    context = ServletUtilities.getContext(req);
    Shepherd shepherd = new Shepherd(context);
    File dataDir = new File(ServletUtilities.dataDir(context, getServletContext().getRealPath("/")));
    // Format string for encounter page URL (with placeholder).
    String pageUrlFormatEnc = "//" + CommonConfiguration.getURLLocation(req) + "/encounters/encounter.jsp?number=%s";

    Map<File, String> files = new TreeMap<>();
    Map<File, String> failed = new TreeMap<>();

    try {
      // Perform MMA-compatible flag updates.
      shepherd.beginDBTransaction();
      for (Iterator<Encounter> iter = shepherd.getAllEncounters(); iter.hasNext();) {
        Encounter enc = iter.next();
        File dir = new File(enc.dir(dataDir.getAbsolutePath()));
        if (dir == null || !dir.exists())
          continue;

        // Collate files to be checked.
        RegexFilenameFilter ff1 = new RegexFilenameFilter("^.+_(CR|EH|FT)\\." + MediaUtilities.REGEX_SUFFIX_FOR_WEB_IMAGES);
        RegexFilenameFilter ff2 = new RegexFilenameFilter("^.+\\.FEAT$");
        RegexFilenameFilter ff3 = new RegexFilenameFilter("^.+_mma(In|Out)put(Regional)?\\.(txt|csv)$");
        File[] fileList = dir.listFiles(ff1);
        if (fileList != null) {
          for (File f : Arrays.asList(fileList))
            files.put(f, enc.getCatalogNumber());
        }
        fileList = dir.listFiles(ff2);
        if (fileList != null) {
          for (File f : Arrays.asList(fileList))
            files.put(f, enc.getCatalogNumber());
        }
        fileList = dir.listFiles(ff3);
        if (fileList != null) {
          for (File f : Arrays.asList(fileList))
            files.put(f, enc.getCatalogNumber());
        }

        // Remove matcher files relating to existing SPVs.
        for (SinglePhotoVideo spv : enc.getSinglePhotoVideo()) {
          if (!MediaUtilities.isAcceptableImageFile(spv.getFile())) {
            continue;
          }
          Map<String, File> mmFiles = MantaMatcherUtilities.getMatcherFilesMap(spv);
          File cr = mmFiles.get("CR");
          if (cr.exists()) {
            for (File f : mmFiles.values())
              files.remove(f);
          }
        }

        // Delete orphan files.
        for (Map.Entry<File, String> me : files.entrySet()) {
          if (me.getKey().exists() && !me.getKey().delete()) {
            failed.put(me.getKey(), me.getValue());
          }
        }
      }

      // Write output to response.
      res.setCharacterEncoding("UTF-8");
      res.setContentType("text/html; charset=UTF-8");
      try (PrintWriter out = res.getWriter()) {
        out.println(ServletUtilities.getHeader(req));

        if (!failed.isEmpty()) {
          out.println(String.format("<strong>Failure!</strong> I failed to delete all orphan MantaMatcher algorithm files (%2$d of %1$d couldn't be deleted).", files.size(), failed.size()));
        } else {
          out.println(String.format("<strong>Success!</strong> I have successfully deleted all orphan MantaMatcher algorithm files (%d were found).", files.size()));
        }

        out.println("<ul class=\"adminToolDetailList\">");
        for (Map.Entry<File, String> me : files.entrySet()) {
          File f = me.getKey();
          String encNum = me.getValue();
          String url = String.format(pageUrlFormatEnc, encNum);
          if (failed.containsKey(me.getKey())) {
            out.println(String.format("<li>Failed to delete file: %s (<a href=\"%s\">%s</a>)</li>", f.getName(), url, encNum));
          } else {
            out.println(String.format("<li>Successfully deleted file: %s (<a href=\"%s\">%s</a>)</li>", f.getName(), url, encNum));
          }
        }
        out.println("</ul>");

        out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(req) + "/appadmin/admin.jsp\">Return to the Administration page.</a></p>\n");
        out.println(ServletUtilities.getFooter(context));
      }

    } catch (Exception ex) {
      shepherd.rollbackDBTransaction();
      shepherd.closeDBTransaction();
      handleException(req, res, ex);
    }
  }
}
