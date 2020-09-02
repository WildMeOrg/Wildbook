/*
    HttpServlet does not support PATCH.   :(
    so we roll our own here.

    h/t  https://technology.amis.nl/2017/12/15/handle-http-patch-request-with-java-servlet/
*/

package org.ecocean.api;

import java.io.IOException;
 
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
 
public abstract class ApiHttpServlet extends HttpServlet {
    public void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (request.getMethod().equalsIgnoreCase("PATCH")){
           doPatch(request, response);
        } else {
            super.service(request, response);
        }
    }
    public abstract void doPatch(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException;
}
