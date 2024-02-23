package org.ecocean.servlet;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class ReactAppServlet extends HttpServlet {

    public void init(ServletConfig config) throws ServletException {
        super.init(config);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        System.out.println("=========> In ReactAppServlet Servlet ");
        // response.setStatus(HttpServletResponse.SC_OK);
        // response.getWriter().write(request.getRequestURL().toString());
        // response.getWriter().flush();
        try {
            request.getRequestDispatcher("/react/index.html").forward(request, response);
        } catch (ServletException e) {
            System.out.println("=========> ServletException, message: "+e.getMessage());
            e.printStackTrace();
            throw e;
        }

        // request.getRequestDispatcher("/wildbook/react").forward(request, response);
        // response.setContentType("text/html");

        // String filePath = getServletContext().getRealPath("/react/");

        // String content = readFile(filePath);

        // response.getWriter().println(content);
    }

    // private String readFile(String filePath) throws IOException {
    //     StringBuilder contentBuilder = new StringBuilder();
    //     try (BufferedReader reader = new BufferedReader(new FileReader(new File(filePath)))) {
    //         String line;
    //         while ((line = reader.readLine()) != null) {
    //             contentBuilder.append(line);
    //         }
    //     }
    //     return contentBuilder.toString();
    // }
}



// package org.ecocean.servlet;

// import javax.servlet.http.HttpServlet;
// import javax.servlet.http.HttpServletRequest;
// import javax.servlet.http.HttpServletResponse;
// import java.io.IOException;

// public class ReactAppServlet extends HttpServlet {

//     public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
//         request.getRequestDispatcher("/wildbook/react").forward(request, response);
//   }

// }