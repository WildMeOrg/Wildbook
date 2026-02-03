package org.ecocean.servlet.export;

import org.ecocean.CommonConfiguration;
import org.ecocean.Encounter;
import org.ecocean.EncounterQueryProcessor;
import org.ecocean.EncounterQueryResult;
import org.ecocean.export.EncounterCOCOExportFile;
import org.ecocean.security.HiddenEncReporter;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.shepherd.core.Shepherd;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

public class EncounterSearchExportCOCO extends HttpServlet {

    public void init(ServletConfig config) throws ServletException {
        super.init(config);
    }

    public void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        doPost(request, response);
    }

    public void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("EncounterSearchExportCOCO.class");

        myShepherd.beginDBTransaction();

        try {
            // Process query
            EncounterQueryResult queryResult = EncounterQueryProcessor.processQuery(
                myShepherd, request, "year descending, month descending, day descending");
            Vector<?> rEncounters = queryResult.getResult();

            // Filter hidden encounters
            HiddenEncReporter hiddenData = new HiddenEncReporter(rEncounters, request, myShepherd);

            // Convert to list, excluding hidden
            List<Encounter> encounters = new ArrayList<>();
            for (Object obj : rEncounters) {
                Encounter enc = (Encounter) obj;
                if (!hiddenData.contains(enc)) {
                    encounters.add(enc);
                }
            }

            // Set response headers
            response.setContentType("application/zip");
            response.setHeader("Content-Disposition", "attachment; filename=\"wildbook-coco-export.zip\"");

            // Write export
            OutputStream out = response.getOutputStream();
            EncounterCOCOExportFile exportFile = new EncounterCOCOExportFile(encounters, myShepherd);
            exportFile.writeTo(out);
            out.flush();

        } catch (Exception e) {
            e.printStackTrace();
            response.setContentType("text/html");
            PrintWriter out = response.getWriter();
            out.println(ServletUtilities.getHeader(request));
            out.println("<html><body><p><strong>Error encountered</strong></p>");
            out.println("<p>Error: " + e.getMessage() + "</p>");
            out.println("<p>Please let the webmaster know you encountered an error at: EncounterSearchExportCOCO servlet</p></body></html>");
            out.println(ServletUtilities.getFooter(context));
            out.close();
        } finally {
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
        }
    }
}
