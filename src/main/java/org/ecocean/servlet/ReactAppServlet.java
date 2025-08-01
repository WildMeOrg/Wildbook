package org.ecocean.servlet;

import java.io.IOException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

public class ReactAppServlet extends HttpServlet {
    public void init(ServletConfig config)
    throws ServletException {
        super.init(config);
    }

    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response)
    throws IOException, ServletException {
        try {
            request.getRequestDispatcher("/react/index.html").forward(request, response);
        } catch (ServletException e) {
            e.printStackTrace();
            throw e;
        }
    }
}
