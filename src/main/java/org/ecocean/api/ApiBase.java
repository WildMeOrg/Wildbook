// base class for *all* api classes
package org.ecocean.api;

import java.io.IOException;
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
}
