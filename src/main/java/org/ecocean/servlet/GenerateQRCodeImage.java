package org.ecocean.servlet;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

import net.glxn.qrgen.image.ImageType;
import net.glxn.qrgen.QRCode;

public class GenerateQRCodeImage extends HttpServlet {
    public void init(ServletConfig config)
    throws ServletException {
        super.init(config);
    }

    public void doPost(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        doGet(request, response);
    }

    public void doGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        String myURL = "http://www.whaleshark.org/individuals.jsp?number=" +
            request.getParameter("number");

        // reset code size to 238x238
        ByteArrayOutputStream out = QRCode.from(myURL).withSize(238,
            238).to(ImageType.PNG).stream();

        response.setContentType("image/png");
        response.setContentLength(out.size());

        OutputStream outStream = response.getOutputStream();

        outStream.write(out.toByteArray());

        outStream.flush();
        outStream.close();
        outStream = null;
    }
}
