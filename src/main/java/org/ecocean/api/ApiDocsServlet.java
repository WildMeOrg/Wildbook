package org.ecocean.api;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class ApiDocsServlet extends HttpServlet {

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        // Load the OpenAPI file from src/main/resources/openapi.yaml
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
}
