package org.ecocean.api;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.ServletException;

import org.apache.shiro.subject.Subject;
import org.apache.shiro.SecurityUtils;

public class Logout extends ApiBase {
    public Logout() {
        super();
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        doPost(request, response);
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        Subject subject = SecurityUtils.getSubject();

        if (subject != null) subject.logout();
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        response.setStatus(200);
        response.setHeader("Content-Type", "application/json");
        response.getWriter().write("{\"success\": true}");
    }
}
