<%@ page contentType="text/html; charset=utf-8" language="java"
     import="org.ecocean.servlet.ReCAPTCHA
	"
%>
<%

//this is just how we also *process* the form -- so this would be typically in the form action servlet
if (request.getParameter("formSubmitted") != null) {
	boolean ok = ReCAPTCHA.captchaIsValid(request);
	out.println("<p>Results from captchaIsValid(): <b>" + ok + "</b>.</p>");
	return;  //bail
}

/*
    note: there will be a js function recaptchaCompleted() [boolean] which can be used client-side to make sure the
    user has even done something in the captcha, to save a form submit before that happens.
*/
%>


<form action="captchaExample.jsp" method="post">

<%= ReCAPTCHA.captchaWidget(request) %>

<input name="formSubmitted" type="submit" value="submit" />
</form>

