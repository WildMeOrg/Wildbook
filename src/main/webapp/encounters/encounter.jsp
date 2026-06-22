<%@ page contentType="text/html; charset=utf-8" language="java" %>

<%
    // get encounter number and redirect to new Encounter detail page
    String num = request.getParameter("number").replaceAll("\\+", "").trim();
    response.sendRedirect("/react/encounter?number=" + num);

    out.println("<meta http-equiv=\"refresh\" content=\"0; url=/react/encounter?number=" + num + "\" />");
%>
