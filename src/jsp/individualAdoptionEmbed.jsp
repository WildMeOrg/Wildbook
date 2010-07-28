<%@ page contentType="text/html; charset=utf-8" language="java"
	import="org.ecocean.servlet.*,java.util.ArrayList,java.util.GregorianCalendar,java.util.StringTokenizer,org.ecocean.*,java.text.DecimalFormat, javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Enumeration, java.net.URL, java.net.URLConnection, java.io.InputStream, java.io.FileInputStream, java.io.File, java.util.Iterator,java.util.Properties"%>

<%
Shepherd adoptShepherd=new Shepherd();
String name=request.getParameter("name");

try{
%>

<style type="text/css">
<!--
.style1 {font-weight: bold}


table.adopter {
	border-width: 0px 0px 0px 0px;
	border-spacing: 0px;
	border-style: solid solid solid solid;
	border-color: black black black black;
	border-collapse: separate;
	
}

table.adopter td {
	border-width: 1px 1px 1px 1px;
	padding: 3px 3px 3px 3px;
	border-style: none none none none;
	border-color: gray gray gray gray;
	background-color: #D7E0ED;
	-moz-border-radius: 0px 0px 0px 0px;
	font-size: 12px;
	color: #330099;
}

table.adopter td.name {
	font-size: 12px;
	text-align:center;
	background-color: #D7E0ED;
	
}

table.adopter td.image {
	padding: 0px 0px 0px 0px;
	
}
.style2 {
	font-size: x-small;
	color: #000000;
}

-->
</style>

<table class="adopter" bgcolor="#D7E0ED" style="background-color:#D7E0Ed " width="190px">


<%
			 ArrayList adoptions = adoptShepherd.getAllAdoptionsForMarkedIndividual(name);
			 int numAdoptions = adoptions.size();
			int ia=0;
			 for(ia=0;ia<numAdoptions;ia++){
			 	Adoption ad = (Adoption)adoptions.get(ia);
				%>
<tr><td class="image"><img border="0" src="images/meet-adopter-frame.gif" /></td></tr>
			
	<%
	if((ad.getAdopterImage()!=null)&&(!ad.getAdopterImage().equals(""))){
	%>	
	<tr>
		<td class="image" style="padding-top: 0px;">
		<center><img width="188px" height="188px"
			src="adoptions/<%=ad.getID()%>/thumb.jpg" /></center>
		</td>
	</tr>
	<%
	}
	%>


<tr><td class="name">
			 	<center><strong><font color="#282460" size="+1"><%=ad.getAdopterName()%></font></strong></center>
</td></tr>
	<tr>
		<td>&nbsp;</td>
	</tr>
	<%
			if((ad.getAdopterQuote()!=null)&&(!ad.getAdopterQuote().equals(""))){
		%>

	<tr>
		<td>Why are research and conservation for this species important?</td>
	</tr>
	<tr>
		<td><em>"<%=ad.getAdopterQuote()%>"</em></td>
	</tr>

	<%
			 }

			 if(request.isUserInRole("admin")){
			 %>
	<tr>
		<td>&nbsp;</td>
	</tr>
	<tr>
		<td><em>Adoption type:</em><br><%=ad.getAdoptionType()%>
		</td>
	</tr>
	<tr>
		<td><em>Adoption start:</em><br><%=ad.getAdoptionStartDate()%>
		</td>
	</tr>
	<tr>
		<td><em>Adoption end:</em><br><%=ad.getAdoptionEndDate()%>
		</td>
	</tr>
	<tr>
		<td>&nbsp;</td>
	</tr>
	<tr>
		<td align="left"><a
			href="http://<%=CommonConfiguration.getURLLocation()%>/<%=CommonConfiguration.getAdoptionDirectory() %>/adoption.jsp?number=<%=ad.getID()%>#create">[edit
		this adoption]</a></td>
	</tr>
	<tr>
		<td>&nbsp;</td>
	</tr>
	<%
			 }
			 %>
			  <tr><td>&nbsp;</td></tr>
			
<%
			 }
			 
			 if(ia>0){
%>

	  
<tr><td class="image"><img border="0" src="images/adopter-frame-bottom.gif" /></td></tr>
			 
<%
			} 
%>
</table>
<p>&nbsp;</p>

			
				
				
<%
}
catch(Exception e){}
adoptShepherd.rollbackDBTransaction();
adoptShepherd.closeDBTransaction();
adoptShepherd=null;

%>