<%@ page contentType="text/html; charset=utf-8" language="java"
	import="org.ecocean.servlet.*,java.util.ArrayList,java.util.GregorianCalendar,java.util.StringTokenizer,org.ecocean.*,java.text.DecimalFormat, javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Enumeration, java.net.URL, java.net.URLConnection, java.io.InputStream, java.io.FileInputStream, java.io.File, java.util.Iterator,java.util.Properties"%>

<%
Shepherd adoptShepherd=new Shepherd();
String name=request.getParameter("name");

try{
%>

				
		
<h3>Adopters</h3>

<%
			 ArrayList adoptions = adoptShepherd.getAllAdoptionsForMarkedIndividual(name);
			 int numAdoptions = adoptions.size();

			 for(int ia=0;ia<numAdoptions;ia++){
			 	Adoption ad = (Adoption)adoptions.get(ia);
				%>
<table class="adopter">

	<tr>
		<td class="image" style="padding-top: 1px;">
		<center><img width="186px" height="186px"
			src="adoptions/<%=ad.getID()%>/thumb.jpg" /></center>
		</td>
	</tr>


	<tr>
		<td class="name">
		<table>
			<tr>
				<td><img src="images/adoption.gif" align="absmiddle" />
				<td><strong><%=ad.getAdopterName()%></strong></td>
			</tr>
		</table>
		</td>
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

			 if(request.isUserInRole("adoption")){
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
</table>
<p>&nbsp;</p>
<%
			 }
			 
			 //add adoption
			 if(request.isUserInRole("adoption")){
			 %>
<p><a
	href="http://<%=CommonConfiguration.getURLLocation()%>/<%=CommonConfiguration.getAdoptionDirectory() %>/adoption.jsp?individual=<%=request.getParameter("individual")%>#create">[+]
Add adoption</a></p>
<%
			 }
			 %>

<table class="adopter" cellpadding="2">
	<tr>
		<td><img src="images/adoption.gif" align="left" /> <em><a
			href="adoptashark.jsp?individual=<%=request.getParameter("number")%>">This
		animal is available for adoption! <br><br>Click here to
		learn how to support our research through adoption!
		</a></em></td>
	</tr>
</table>
				
				
				
<%
}
catch(Exception e){}
adoptShepherd.rollbackDBTransaction();
adoptShepherd.closeDBTransaction();
adoptShepherd=null;

%>