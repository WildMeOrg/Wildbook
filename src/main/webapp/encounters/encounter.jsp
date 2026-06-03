<%@ page contentType="text/html; charset=utf-8" language="java" %>
<%! String reactBase = org.ecocean.servlet.ReactRouter.getBasePath(); %>

<%
    // get encounter number and redirect to new Encounter detail page
    String num = request.getParameter("number").replaceAll("\\+", "").trim();
    response.sendRedirect(request.getContextPath() + reactBase + "/encounter?number=" + num);

    out.println("<meta http-equiv=\"refresh\" content=\"0; url=" + request.getContextPath() + reactBase + "/encounter?number=" + num + "\" />");
%>
