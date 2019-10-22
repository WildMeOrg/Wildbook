<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.servlet.ServletUtilities,
org.ecocean.*
" %>
<% request.setAttribute("pageTitle", "Account Creation"); %>
<jsp:include page="header.jsp" flush="true"/>
<div class="container maincontent">
<%

String username = request.getParameter("username");

if (username != null) {
    String context = ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);
    myShepherd.setAction("spotterUser.jsp");
    myShepherd.beginDBTransaction();

    User user = myShepherd.getUser(username);
    if (user != null) {
        myShepherd.rollbackAndClose();
%>
<h2>The user <b><%=username%></b> already exists.</h2>
<p>You can <a href="login.jsp?username=<%=username%>">login here</a>.</p>
<%
    } else {
        //SpotterConserveIO.init("context0");
        //SpotterConserveIO.testLogin("wildbook-test", "password-fail")
        myShepherd.commitDBTransaction();

%>
hello???

<%
    }
} else {  //main form
%>

<h1>Whale Alert user creation</h1>
<p>
If you are a <a target="_new" href="http://www.whalealert.org/">Whale Alert</a> app user, you can create an account
on Flukebook with your username and password.  (more info etc)
</p>

<form method="post">
    <p><input name="username" /> <b>Whale Alert username</b></p>
    <p><input type="password" name="password" /> <b>Whale Alert password</b></p>
    <input type="submit" value="Create Account" />
    (agree to terms etc....)
</form>


<%
}
%>
</div>
