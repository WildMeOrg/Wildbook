<%@ page contentType="text/html; charset=utf-8" language="java"
	import="org.ecocean.servlet.*,java.util.ArrayList,java.util.GregorianCalendar,java.util.StringTokenizer,org.ecocean.*,java.text.DecimalFormat, javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Enumeration, java.net.URL, java.net.URLConnection, java.io.InputStream, java.io.FileInputStream, java.io.File, java.util.Iterator,java.util.Properties"%>

<%
Shepherd adoptShepherd=new Shepherd();
String num=request.getParameter("num");

try{


%>
    
   
				<h3 style="width: 250px">Adopters</h3>

				<%
						 	ArrayList adoptions = adoptShepherd.getAllAdoptionsForEncounter(num);
						 	 int numAdoptions = adoptions.size();

						 	 for(int ia=0;ia<numAdoptions;ia++){
						 	 	Adoption ad = (Adoption)adoptions.get(ia);
						 %>
				<table class="adopter" width="250px">
					<tr>
						<td class="image"><img
							src="../adoptions/<%=ad.getID()%>/thumb.jpg" width="250px"></td>
					</tr>

					<tr>
						<td class="name">
						<table>
							<tr>
								<td><strong><%=ad.getAdopterName()%></strong></td>
							</tr>
						</table>
						</td>
					</tr>
					<%
			if((ad.getAdopterQuote()!=null)&&(!ad.getAdopterQuote().equals(""))){
		%>

					<tr>
						<td>Why are research and conservation important for this
						species?</td>
					</tr>
					<tr>
						<td width="250px"><em>"<%=ad.getAdopterQuote()%>"</em></td>
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
				</table>
				<p>&nbsp;</p>
				<%
			 	}
			 	 
			 	 //add adoption
			 	 if(request.isUserInRole("admin")){
			 %>
				<p><a
					href="http://<%=CommonConfiguration.getURLLocation() %>/<%=CommonConfiguration.getAdoptionDirectory() %>/adoption.jsp?encounter=<%=num%>#create">[+]
				Add adoption</a></p>
				<%
			 	}
			 %>

		


				
<%
}
catch(Exception e){}
adoptShepherd.rollbackDBTransaction();
adoptShepherd.closeDBTransaction();
adoptShepherd=null;

%>