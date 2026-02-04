package org.ecocean.api;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class ApiDocsServlet extends HttpServlet {

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String uri = request.getRequestURI();

        // Serve the raw YAML file for programmatic access
        if (uri.endsWith("openapi.yaml")) {
            serveYaml(response);
        } else {
            // Serve the Swagger UI HTML for human-readable docs
            serveSwaggerUI(request, response);
        }
    }

    private void serveYaml(HttpServletResponse response) throws IOException {
        String resourcePath = "/openapi.yaml";

        try (InputStream inputStream = getClass().getResourceAsStream(resourcePath)) {

            if (inputStream == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "OpenAPI spec not found.");
                return;
            }

            response.setContentType("application/x-yaml");
            response.setCharacterEncoding("UTF-8");

            OutputStream outputStream = response.getOutputStream();
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        } catch (Exception e) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error reading API spec.");
            e.printStackTrace();
        }
    }

    private void serveSwaggerUI(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        response.setContentType("text/html");
        response.setCharacterEncoding("UTF-8");

        // Build the URL to the OpenAPI spec (relative to current path)
        String contextPath = request.getContextPath();
        String specUrl = contextPath + "/api/v3/docs/openapi.yaml";

        PrintWriter out = response.getWriter();
        out.println("<!DOCTYPE html>");
        out.println("<html lang=\"en\">");
        out.println("<head>");
        out.println("    <meta charset=\"UTF-8\">");
        out.println("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
        out.println("    <title>Wildbook v3 API Documentation</title>");
        out.println("    <link rel=\"stylesheet\" href=\"https://unpkg.com/swagger-ui-dist@5.11.0/swagger-ui.css\" />");
        out.println("    <style>");
        out.println("        body { margin: 0; padding: 0; }");
        out.println("        #swagger-ui { max-width: 1460px; margin: 0 auto; }");
        out.println("    </style>");
        out.println("</head>");
        out.println("<body>");
        out.println("    <div id=\"swagger-ui\"></div>");
        out.println("    <script src=\"https://unpkg.com/swagger-ui-dist@5.11.0/swagger-ui-bundle.js\"></script>");
        out.println("    <script src=\"https://unpkg.com/swagger-ui-dist@5.11.0/swagger-ui-standalone-preset.js\"></script>");
        out.println("    <script>");
        out.println("        window.onload = function() {");
        out.println("            window.ui = SwaggerUIBundle({");
        out.println("                url: '" + specUrl + "',");
        out.println("                dom_id: '#swagger-ui',");
        out.println("                deepLinking: true,");
        out.println("                presets: [");
        out.println("                    SwaggerUIBundle.presets.apis,");
        out.println("                    SwaggerUIStandalonePreset");
        out.println("                ],");
        out.println("                plugins: [");
        out.println("                    SwaggerUIBundle.plugins.DownloadUrl");
        out.println("                ],");
        out.println("                layout: 'StandaloneLayout',");
        out.println("                tryItOutEnabled: true,");
        out.println("                persistAuthorization: true");
        out.println("            });");
        out.println("        };");
        out.println("    </script>");
        out.println("</body>");
        out.println("</html>");
    }
}
