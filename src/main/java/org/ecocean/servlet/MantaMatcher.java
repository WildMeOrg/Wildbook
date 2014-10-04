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

import freemarker.template.Configuration;
import freemarker.template.TemplateException;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ecocean.*;
import org.ecocean.mmutil.MantaMatcherUtilities;
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
  /** Path for referencing JSP page for batch task confirmation. */
  private static final String PATH_TEMPLATES = "/templates";
  /** Path for referencing JSP page for error display. */
  private static final String JSP_ERROR = "/error_generic.jsp";

  @Override
  public void init() throws ServletException {
    super.init();
    try {
      registerMethodGET("displayResults", "displayResultsRegional");
      registerMethodPOST("resetMmaCompatible");
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
    req.getSession().setAttribute("thrown", t);
//    res.sendRedirect(req.getContextPath() + JSP_ERROR);
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
      String html = createResultsPage(req, mmMap.get("TXT"), spv);

      // Write results page to output (with support HTTP/1.1).
      int len = html.getBytes("UTF-8").length;
      res.setCharacterEncoding("UTF-8");
      res.setContentType("text/html; charset=UTF-8");
      res.setContentLength(len);
      PrintWriter pw = res.getWriter();
      pw.append(html);
      pw.flush();
      pw.close();

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
      String html = createResultsPage(req, mmMap.get("TXT-REGIONAL"), spv);

      // Write results page to output (with support HTTP/1.1).
      int len = html.getBytes("UTF-8").length;
      res.setCharacterEncoding("UTF-8");
      res.setContentType("text/html; charset=UTF-8");
      res.setContentLength(len);
      PrintWriter pw = res.getWriter();
      pw.append(html);
      pw.flush();
      pw.close();

    } catch (Exception ex) {
      handleException(req, res, ex);
    }
  }

  private String createResultsPage(HttpServletRequest req, File mmaResults, SinglePhotoVideo spv)
          throws IOException, URISyntaxException, ParseException, TemplateException {
    assert spv != null;
    assert mmaResults != null;
    
    String context="context0";
    context=ServletUtilities.getContext(req);

    // Find FreeMarker config in ServletContext, or create if doesn't exist.
    Configuration conf = (Configuration)getServletContext().getAttribute("templateConfig");
    if (conf == null) {
      File dir = new File(getServletContext().getRealPath(PATH_TEMPLATES));
      conf = MantaMatcherUtilities.configureTemplateEngine(dir);
      getServletContext().setAttribute("templateConfig", conf);
    }

    // Derive data folder info.
    String rootDir = getServletContext().getRealPath("/");
    File dataDir = new File(ServletUtilities.dataDir(context, rootDir));

    // URL prefix of the encounters folder (for image links).
    String dir = "/" + CommonConfiguration.getDataDirectoryName(context) + "/encounters";
    String dataDirUrlPrefix = CommonConfiguration.getServerURL(req, dir);
    // Format string for encounter page URL (with placeholder).
    String pageUrlFormatEnc = "//" + CommonConfiguration.getURLLocation(req) + "/encounters/encounter.jsp?number=%s";
    String pageUrlFormatInd = "//" + CommonConfiguration.getURLLocation(req) + "/individuals.jsp?number=%s";
    // Parse MantaMatcher results files ready for display.
    Shepherd shepherd = new Shepherd(context);
    try {
      return MantaMatcherUtilities.getResultsHtml(shepherd, conf, mmaResults, spv, dataDir, dataDirUrlPrefix, pageUrlFormatEnc, pageUrlFormatInd);
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
      for (Iterator iter = shepherd.getAllEncounters(); iter.hasNext();) {
        Encounter enc = (Encounter)iter.next();
        boolean hasCR = hasCR(enc, dataDir);
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

  /**
   * Returns whether an encounter has any MMA-compatible &quot;candidate region&quot;
   * images (which may be used to determine MMA-compatibility status).
   * @param enc encounter to test
   * @param dataDir data folder
   * @return true if any CR images are found, false otherwise
   */
  private boolean hasCR(Encounter enc, File dataDir) {
    // This implementation checks any SPVs stored for this encounter for the
    // standard "_CR" filename suffix. If found, it double-checks that the
    // base image (i.e. without "_CR" suffix) also exists, and returns true
    // if both are found.
    List<SinglePhotoVideo> spvs = enc.getSinglePhotoVideo();
    for (SinglePhotoVideo spv : spvs) {
      String s = spv.getFile().getName();
      String bn = s.substring(0, s.lastIndexOf("."));
      if (bn.endsWith("_CR")) {
        for (SinglePhotoVideo spv2 : spvs) {
          String s2 = spv.getFile().getName();
          String bn2 = s.substring(0, s.lastIndexOf("."));
          if (!spv2.equals(spv) && bn2.equals(bn + "_CR")) {
            // Found SPV with both CR and base image; encounter is MMA-compatible!
            return true;
          }
        }
      }
    }
    return false;
  }
}
