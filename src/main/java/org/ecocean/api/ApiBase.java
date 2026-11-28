// base class for *all* api classes
package org.ecocean.api;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;

public class ApiBase extends javax.servlet.http.HttpServlet implements javax.servlet.Servlet {
    @Override protected void service(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        if (request.getMethod().equalsIgnoreCase("PATCH")) {
            doPatch(request, response);
        } else {
            super.service(request, response);
        }
    }

    protected void doPatch(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        // override if needed
    }

    /** Parse an HTTP Basic Authorization header into [username, password], or null if absent/invalid. */
    protected static String[] parseBasic(String header) {
        if (header == null) return null;
        if (!header.regionMatches(true, 0, "Basic ", 0, 6)) return null;
        try {
            String decoded = new String(Base64.getDecoder().decode(header.substring(6).trim()),
                StandardCharsets.UTF_8);
            int i = decoded.indexOf(':');
            if (i < 0) return null;
            return new String[] { decoded.substring(0, i), decoded.substring(i + 1) };
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
