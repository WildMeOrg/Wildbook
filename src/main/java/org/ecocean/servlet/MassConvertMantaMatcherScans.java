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

import org.ecocean.CommonConfiguration;
import org.ecocean.Encounter;
import org.ecocean.Shepherd;
import org.ecocean.SinglePhotoVideo;
import org.ecocean.mmutil.FileUtilities;
import org.ecocean.mmutil.MantaMatcherScan;
import org.ecocean.mmutil.MantaMatcherUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.jdo.Extent;
import javax.jdo.Query;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.regex.Pattern;


/**
 * Admin servlet to convert old-style MantaMatcher scans to the new format (supporting arbitrary LoctionID selections).
 *
 * @author Giles Winstanley
 */
public class MassConvertMantaMatcherScans extends HttpServlet {
  /** SLF4J logger instance for writing log entries. */
  private static final Logger log = LoggerFactory.getLogger(MassConvertMantaMatcherScans.class);
  /** Path for referencing JSP page for error display. */
  public static final String JSP_ERROR = "/error_generic.jsp";

  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }

  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    throw new ServletException("Not implemented");
  }

  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

    try {
      String context = "context0";
      context = ServletUtilities.getContext(request);
      Shepherd shep = new Shepherd(context);
      File dataDir = CommonConfiguration.getDataDirectory(getServletContext(), context);
      File encsDir = new File(dataDir, "encounters");
      List<String> allLocationIDs = CommonConfiguration.getIndexedValues("locationID", context);

      // Find all relevant TXT/CSV files, then filter for encounters.
      Set<File> found = new HashSet<>();
      Pattern p = Pattern.compile("^.+_mmaOutput(Regional)?\\.txt$");
      findFilesByPattern(found, encsDir, p, -1);
      // Convert files found to encounter IDs.
      Set<String> encIds = new HashSet<>();
      for (File f : found) {
        encIds.add(f.getParentFile().getName());
      }
      log.info(String.format("Discovered %d encounters for which MMA scans can be converted", encIds.size()));

      // Setup collection for failed conversions.
      Set<SinglePhotoVideo> failed = new HashSet<>();

      // Process each encounter.
      for (String encId : encIds) {
        Encounter enc = shep.getEncounter(encId);
        int count = 0;
        // Process each SPV in the encounter.
        for (SinglePhotoVideo spv : enc.getSinglePhotoVideo()) {
          // Define files relating to old-style MMA scan output.
          File gt = new File(spv.getFile().getParentFile(), spv.getDataCollectionEventID() + "_mmaOutput.txt");
          File gc = new File(spv.getFile().getParentFile(), spv.getDataCollectionEventID() + "_mmaOutput.csv");
          File rt = new File(spv.getFile().getParentFile(), spv.getDataCollectionEventID() + "_mmaOutputRegional.txt");
          File rc = new File(spv.getFile().getParentFile(), spv.getDataCollectionEventID() + "_mmaOutputRegional.csv");
          // Check for existence, and convert old scans.
          try {
            Set<MantaMatcherScan> scans = MantaMatcherUtilities.loadMantaMatcherScans(context, spv);
            if (gt.isFile() && gc.isFile()) {
              MantaMatcherScan scan = new MantaMatcherScan(spv, allLocationIDs, new Date(gt.lastModified()));
              scans.add(scan);
              gt.renameTo(scan.getScanOutputTXT());
              gc.renameTo(scan.getScanOutputCSV());
              log.trace(String.format("Converted: %s", gt.getAbsolutePath()));
            }
            if (rt.isFile() && rc.isFile()) {
              MantaMatcherScan scan = new MantaMatcherScan(spv, Arrays.asList(enc.getLocationID()), new Date(rt.lastModified()));
              scans.add(scan);
              rt.renameTo(scan.getScanOutputTXT());
              rc.renameTo(scan.getScanOutputCSV());
              log.trace(String.format("Converted: %s", rt.getAbsolutePath()));
            }
            // Save new scans if necessary.
            if (!scans.isEmpty()) {
              MantaMatcherUtilities.saveMantaMatcherScans(context, spv, scans);
              count += scans.size();
            }
            log.info(String.format("Converted %d scans for encounter: %s", count, enc.getCatalogNumber()));
          }
          catch (Exception ex) {
            log.warn(String.format("Failed to convert SPV: %s (encounter: %s)", spv.getDataCollectionEventID(), enc.getCatalogNumber()), ex);
            failed.add(spv);
          }
        }
      }

      // Show output.
      response.setContentType("text/html");
      PrintWriter out = response.getWriter();
      out.println(ServletUtilities.getHeader(request));

      if (failed.isEmpty()) {
        out.println(String.format("<strong>Success!</strong> Successfully updated %d encounters.\n", encIds.size()));
        out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/appadmin/admin.jsp\">Return to the Administration page.</a></p>\n");
      }
      else {
        out.println(String.format("<strong>Failure!</strong> Successfully updated %d encounters, but failed for %d\n", encIds.size() - failed.size(), failed.size()));
        out.println(String.format("<ul>\n"));
        for (SinglePhotoVideo spv : failed) {
          out.println(String.format("<li>Encounter: %s, SPV: %s</li>\n", spv.getCorrespondingEncounterNumber(), spv.getDataCollectionEventID()));
        }
        out.println(String.format("</ul>\n"));
        out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/appadmin/admin.jsp\">Return to the Administration page.</a></p>\n");
      }
      out.println(ServletUtilities.getFooter(context));
    }
    catch (Throwable t) {
      log.warn(t.getMessage(), t);
      request.setAttribute("javax.servlet.jsp.jspException", t);
      getServletContext().getRequestDispatcher(JSP_ERROR).forward(request, response);
    }
  }

  /**
   * Recursively scans the specified folder for the named item,
   * and returns all matching files/folders encountered.
   * @param found collection to which to add matching files
   * @param dir folder in which to scan
   * @param regex regex pattern of filename for which to search
   * @param depth maximum depth for recursive search (-1 for exhaustive search, 0 for only specified folder, &gt;1 for specified depth)
   */
  private static void findFilesByPattern(final Collection<File> found, final File dir, final Pattern regex, int depth)
  {
    Objects.requireNonNull(found);
    Objects.requireNonNull(dir);
    Objects.requireNonNull(regex);
    File[] files = dir.listFiles();
    for (File f : files)
    {
      if (f.isFile() && regex.matcher(f.getName()).matches())
        found.add(f);
    }
    for (File f : files)
    {
      if (f.isDirectory() && (depth <= -1 || depth >= 1))
        findFilesByPattern(found, f, regex, depth - 1);
    }
  }
}
