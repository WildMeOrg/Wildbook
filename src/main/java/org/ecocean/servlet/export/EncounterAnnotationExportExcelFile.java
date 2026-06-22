package org.ecocean.servlet.export;
import javax.servlet.*;
import javax.servlet.http.*;

import java.io.*;
import java.lang.reflect.InvocationTargetException;

import org.ecocean.export.EncounterAnnotationExportFile;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.shepherd.core.Shepherd;

public class EncounterAnnotationExportExcelFile extends HttpServlet {
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
        String context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);

        myShepherd.beginDBTransaction();

        try {
            EncounterAnnotationExportFile excelFile = new EncounterAnnotationExportFile(request,
                myShepherd);

            // now write out the file
            response.setContentType("application/msexcel");
            response.setHeader("Content-Disposition", "attachment;filename=" + excelFile.getName());

            OutputStream os = response.getOutputStream();
            excelFile.writeToStream(os);

            os.flush();
            os.close();
        } catch (Exception e) {
            e.printStackTrace();
            response.setContentType("text/html");
            PrintWriter out = response.getWriter();
            out.println(ServletUtilities.getHeader(request));
            out.println("<html><body><p><strong>Error encountered</strong></p>");
            out.println(
                "<p>Please let the webmaster know you encountered an error at: EncounterSearchExportExcelFile servlet</p></body></html>");
            out.println(ServletUtilities.getFooter(context));
            out.close();
        } finally {
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
        }
    }
}
