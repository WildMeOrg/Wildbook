<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.*,org.ecocean.servlet.ServletUtilities,org.ecocean.Util,org.ecocean.Measurement, org.ecocean.Util.*, org.ecocean.genetics.*, org.ecocean.tag.*, java.awt.Dimension, javax.jdo.Extent, javax.jdo.Query, java.io.File, java.text.DecimalFormat, java.util.*" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>         



<td width="170" align="left" valign="top" bgcolor="#99CCFF">
<%

try {

	Shepherd formShepherd = new Shepherd();
		

	//handle translation
	String langCode = "en";

	//check what language is requested
	if (session.getAttribute("langCode") != null) {
  		langCode = (String) session.getAttribute("langCode");
	}

	//let's load encounters.properties
	Properties encprops = new Properties();
	encprops.load(getClass().getResourceAsStream("/bundles/" + langCode + "/encounter.properties"));

	String num = request.getParameter("encounterNumber");
	pageContext.setAttribute("num", num);
	pageContext.setAttribute("set", encprops.getProperty("set"));

	Encounter enc=formShepherd.getEncounter(num);
	pageContext.setAttribute("enc", enc);
	int numImages=formShepherd.getAllSinglePhotoVideosForEncounter(enc.getCatalogNumber()).size();
    

	  GregorianCalendar cal = new GregorianCalendar();
	  int nowYear = cal.get(1);
%>


 <%
 	//start deciding menu bar contents

 //if not logged in
if(!request.getParameter("loggedIn").equals("true")){
	 
 %>
<p class="para"><a
  href="../welcome.jsp?reflect=<%=request.getRequestURI()%>?number=<%=num%>"><%=encprops.getProperty("login")%>
</a></p>

  <%
			} 
		//if logged in, limit commands displayed			
		else {
		%>
<p align="center" class="para"><font color="#000000" size="+1"><strong>
  <%=encprops.getProperty("action") %> <font color="#000000" size="+1"><strong><img
  src="../images/Crystal_Clear_app_advancedsettings.gif" width="29" height="29" align="absmiddle"/></strong></font> <%=encprops.getProperty("uppercaseEdit") %>
</strong></font><br> <br> <em><font
  size="-1"><%=encprops.getProperty("editarea")%>
</font></em>
</p>
  <%
			//manager-level commands
				if(request.getParameter("isOwner").equals("true")) {
				
			
			//approve new encounter
			if ((enc.getState()!=null) && (enc.getState().equals("unapproved")) && (request.getParameter("isOwner").equals("true"))) {
		%>
<table width="175" border="1" cellpadding="1" cellspacing="0"
       bordercolor="#000000" bgcolor="#CECFCE">
  <tr>
    <td height="30" class="para">
      <p><font color="#990000"><font color="#990000">&nbsp;<img
        align="absmiddle" src="../images/check_green.png"/></font>
        <strong><%=encprops.getProperty("approve_encounter")%>
        </strong></font></p>

      <p><font color="#990000"><font color="#990000"><a
        href="<%=CommonConfiguration.getWikiLocation()%>approving_an_encounter"
        target="_blank">&nbsp;<img src="../images/information_icon_svg.gif" alt="Help" border="0"
                                   align="absmiddle"/></a></font> </font><span
        class="style2"><%=encprops.getProperty("approval_checklist")%></span></p>
    </td>
  </tr>
  <tr>
    <td>
      <form name="approve_form" method="post" action="../EncounterApprove">
        <input name="action" type="hidden" id="action" value="approve">
        <input name="number" type="hidden"
               value=<%=request.getParameter("number")%>> <input
        name="approve" type="submit" id="approve" value="<%=encprops.getProperty("approve")%> ">
      </form>
    </td>
  </tr>
</table>
<br /> <%
				}



	  //reject encounter
	  if (request.getParameter("isOwner").equals("true")&&CommonConfiguration.isCatalogEditable()) {
	  %>
<table width="150" border="1" cellpadding="1" cellspacing="0" bordercolor="#000000"
       bgcolor="#CECFCE">
  <tr>
    <td>
      <p class="para"><font color="#990000"><img
        align="absmiddle" src="../images/cancel.gif"/>
        <strong><%=encprops.getProperty("rejectEncounter")%>
        </strong></font></p>

      <p class="para"><font color="#990000"><strong><font color="#990000"><font color="#990000"><a
        href="<%=CommonConfiguration.getWikiLocation()%>approving_an_encounter"
        target="_blank"><img src="../images/information_icon_svg.gif"
                             alt="Help" border="0" align="absmiddle"/></a>
      </font></font></strong></font><span
        class="style4"><%=encprops.getProperty("moreInfo")%> </span></p>
    </td>
  </tr>
  <tr>
    <td>
      <form name="reject_form" method="post" action="reject.jsp">
        <input name="action" type="hidden" id="action" value="reject">
        <input name="number" type="hidden" value=<%=num%>> <input
        name="reject" type="submit" id="reject"
        value="<%=encprops.getProperty("rejectEncounter")%>"></form>
    </td>
  </tr>
</table>
<br/> <%
	  	}
	  	  if ((enc.getState()!=null) && (enc.getState().equals("unidentifiable")) && request.getParameter("isOwner").equals("true")) {
	  %>
<table width="150" border="1" cellpadding="1" cellspacing="0"
       bordercolor="#000000" bgcolor="#CECFCE">
  <tr>
    <td class="para">
      <p><strong><font color="#990000"><%=encprops.getProperty("setIdentifiable")%>
      </font></strong></p>

      <p><font color="#990000"><font color="#990000"><a
        href="<%=CommonConfiguration.getWikiLocation()%>approving_an_encounter"
        target="_blank"><img src="../images/information_icon_svg.gif"
                             alt="Help" border="0" align="absmiddle"/></a></font> </font><span
        class="style4"><%=encprops.getProperty("moreInfo")%> </span></p>
    </td>
  </tr>
  <tr>
    <td>
      <form name="reacceptEncounter" method="post"
            action="../EncounterSetIdentifiable"><input name="action"
                                                        type="hidden" id="action" value="reaccept">
        <input
          name="number" type="hidden" value=<%=num%>> <input
          name="reject" type="submit" id="reject" value="<%=encprops.getProperty("reaccept")%>">
      </form>
    </td>
  </tr>
</table>
<br/> <%
	  	}
	  	  //remove spot data
	  	  if(request.getParameter("isOwner").equals("true")) {
	  	  

	  	  
			if(CommonConfiguration.isCatalogEditable()){
	  	  %>

  <%
		}
	  	//end isOwner permissions
	  	  }
	  	  
	  	  	  //end else if-edit not null
	  	  }
	  	  
	  	  //add e-mail for tracking
	  	 
	  %> 

<p>&nbsp;</p>
  <%
				}
			%>


<%
}
catch(Exception e){
	e.printStackTrace();
}
%>
</td>