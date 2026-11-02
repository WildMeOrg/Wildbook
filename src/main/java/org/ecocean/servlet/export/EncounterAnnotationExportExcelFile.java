package org.ecocean.servlet.export;
import javax.servlet.*;
import javax.servlet.http.*;

import java.io.*;

import org.ecocean.export.EncounterAnnotationExportFile;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.shepherd.core.Shepherd;

/**
 * Serves the encounter annotation export.
 *
 * Defaults to .xlsx because this export feeds the WildEx image-export app, whose file picker only
 * accepts Excel files. Pass ?format=csv for the CSV form; both contain exactly the same rows.
 */
public class EncounterAnnotationExportExcelFile extends HttpServlet {
    /**
     * What this endpoint serves when the caller does not ask for a format.
     *
     * XLSX, because this download feeds the WildEx image-export app, whose file picker only accepts
     * .xls/.xlsx. Serving CSV forces users to convert in Excel, and that conversion retypes the
     * "true" in Annotation&lt;n&gt;.MatchAgainst as a boolean, after which WildEx discards every row
     * and produces an empty folder with no error. CSV is still available via ?format=csv.
     *
     * This is the endpoint's own default and deliberately differs from
     * {@link EncounterAnnotationExportFile#DEFAULT_FORMAT}, which stays CSV for callers that embed
     * the export under a fixed filename.
     */
    public static final ExportFileFormat DEFAULT_FORMAT = ExportFileFormat.XLSX;

    public void init(ServletConfig config)
    throws ServletException {
        super.init(config);
    }

    public void doGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        doPost(request, response);
    }

    public void doPost(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        // Resolve the format before anything is written, so a bad value fails cleanly with a
        // status code instead of part-way through a download.
        ExportFileFormat format = ExportFileFormat.fromRequestParam(request.getParameter("format"),
            DEFAULT_FORMAT);

        if (format == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                "Unsupported format. Use format=xlsx or format=csv.");
            return;
        }
        String context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);

        myShepherd.beginDBTransaction();

        try {
            EncounterAnnotationExportFile exportFile = new EncounterAnnotationExportFile(request,
                myShepherd, format);

            // now write out the file
            response.setContentType(exportFile.getContentType());
            // The filename is a fixed prefix plus the current date plus an extension taken from the
            // format enum, so no request input reaches this header.
            response.setHeader("Content-Disposition",
                "attachment; filename=\"" + exportFile.getName() + "\"");
            // This is per-user data, filtered by what the requester is allowed to see.
            response.setHeader("Cache-Control", "private, no-store");

            OutputStream os = response.getOutputStream();
            exportFile.writeToStream(os);

            os.flush();
        } catch (Exception e) {
            e.printStackTrace();
            // Once any export bytes have gone out we cannot switch to an HTML error page - doing so
            // would append markup to a partial spreadsheet and hand the user a corrupt download.
            if (response.isCommitted()) {
                System.out.println(
                    "EncounterAnnotationExportExcelFile: response already committed; truncating download.");
            } else {
                response.reset();
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.setContentType("text/html");
                PrintWriter out = response.getWriter();
                out.println(ServletUtilities.getHeader(request));
                out.println("<html><body><p><strong>Error encountered</strong></p>");
                out.println(
                    "<p>Please let the webmaster know you encountered an error at: EncounterAnnotationExportExcelFile servlet</p></body></html>");
                out.println(ServletUtilities.getFooter(context));
                out.close();
            }
        } finally {
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
        }
    }
}
