<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.*,org.ecocean.servlet.ServletUtilities,org.ecocean.Util,org.ecocean.Measurement, org.ecocean.Util.*, org.ecocean.genetics.*, org.ecocean.tag.*, java.awt.Dimension, javax.jdo.Extent, javax.jdo.Query, java.io.File, java.text.DecimalFormat, java.util.*" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>         


<%--
  ~ The Shepherd Project - A Mark-Recapture Framework
  ~ Copyright (C) 2011 Jason Holmberg
  ~
  ~ This program is free software; you can redistribute it and/or
  ~ modify it under the terms of the GNU General Public License
  ~ as published by the Free Software Foundation; either version 2
  ~ of the License, or (at your option) any later version.
  ~
  ~ This program is distributed in the hope that it will be useful,
  ~ but WITHOUT ANY WARRANTY; without even the implied warranty of
  ~ MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  ~ GNU General Public License for more details.
  ~
  ~ You should have received a copy of the GNU General Public License
  ~ along with this program; if not, write to the Free Software
  ~ Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
  --%>
<%!

  //shepherd must have an open trasnaction when passed in
  public String getNextIndividualNumber(Encounter enc, Shepherd myShepherd) {
    String returnString = "";
    try {
      String lcode = enc.getLocationCode();
      if ((lcode != null) && (!lcode.equals(""))) {

        //let's see if we can find a string in the mapping properties file
        Properties props = new Properties();
        //set up the file input stream
        props.load(getClass().getResourceAsStream("/bundles/newIndividualNumbers.properties"));


        //let's see if the property is defined
        if (props.getProperty(lcode) != null) {
          returnString = props.getProperty(lcode);


          int startNum = 1;
          boolean keepIterating = true;

          //let's iterate through the potential individuals
          while (keepIterating) {
            String startNumString = Integer.toString(startNum);
            if (startNumString.length() < 3) {
              while (startNumString.length() < 3) {
                startNumString = "0" + startNumString;
              }
            }
            String compositeString = returnString + startNumString;
            if (!myShepherd.isMarkedIndividual(compositeString)) {
              keepIterating = false;
              returnString = compositeString;
            } else {
              startNum++;
            }

          }
          return returnString;

        }


      }
      return returnString;
    } catch (Exception e) {
      e.printStackTrace();
      return returnString;
    }
  }

%>

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
<br> <%
				}
     %>
<c:if test="${param.edit eq 'releaseDate'}">
  <a name="releaseDate"></a>
  <table width="150" border="1" cellpadding="1" cellspacing="0" bordercolor="#000000" bgcolor="#CCCCCC">
    <tr>
      <td align="left" valign="top" class="para"><strong><font color="#990000">
                    <%=encprops.getProperty("releaseDate")%>:</font></strong><br />
      </td>
    </tr>
    <tr>
        <td>
            <form name="setReleaseDate" method="post" action="../EncounterSetReleaseDate">
                <input type="hidden" name="encounter" value="${num}"/>
            <table>
                <tr><td><%=encprops.getProperty("releaseDateFormat") %></td></tr>
                <c:set var="releaseDate">
                    <fmt:formatDate value="${enc.releaseDate}" pattern="dd/MM/yyyy"/>
                </c:set>
                <tr><td><input name="releaseDate" value="${releaseDate}"/></td></tr>
                <tr><td><input name="${set}" type="submit" value="${set}"/></td></tr>
            </table>
            </form>
        </td>
    </tr>
  </table>
</c:if>     
     <%
				//set location code
				if((request.getParameter("isOwner").equals("true"))&&(request.getParameter("edit")!=null)&&(request.getParameter("edit").equals("loccode"))){
			%> <a name="loccode"></a><br />
  <table width="150" border="1" cellpadding="1" cellspacing="0" bordercolor="#000000" bgcolor="#CCCCCC">
    <tr>
      <td align="left" valign="top" class="para"><strong><font
        color="#990000"><%=encprops.getProperty("setLocationID")%>:</font></strong></td>
    </tr>
    <tr>
      <td align="left" valign="top">
        <form name="addLocCode" action="../EncounterSetLocationID"
              method="post">
              
              <%
              if(CommonConfiguration.getProperty("locationID0")==null){
              %>
              <input name="code" type="text" size="10" maxlength="50"> 
              <%
              }
              else{
            	  //iterate and find the locationID options
            	  %>
            	  <select name="code" id="code">
						            	<option value=""></option>
						       
						       <%
						       boolean hasMoreLocs=true;
						       int taxNum=0;
						       while(hasMoreLocs){
						       	  String currentLoc = "locationID"+taxNum;
						       	  if(CommonConfiguration.getProperty(currentLoc)!=null){
						       	  	%>
						       	  	 
						       	  	  <option value="<%=CommonConfiguration.getProperty(currentLoc)%>"><%=CommonConfiguration.getProperty(currentLoc)%></option>
						       	  	<%
						       		taxNum++;
						          }
						          else{
						             hasMoreLocs=false;
						          }
						          
						       }
						       %>
						       
						       
						      </select>  
            	  
            	  
            <% 	  
              }
              %>
              
                                   <input name="number" type="hidden" value="<%=num%>"> 
                                   <input name="action" type="hidden" value="addLocCode">
          <input name="Set Location ID"
                 type="submit" id="Add" value="<%=encprops.getProperty("setLocationID")%>"></form>
      </td>
    </tr>
  </table>
<br /> <%
			}
				
		//set alternateid
		if((request.getParameter("isOwner").equals("true"))&&(request.getParameter("edit")!=null)&&(request.getParameter("edit").equals("alternateid"))){
		%> <a name="alternateid"></a><br />
  <table width="150" border="1" cellpadding="1" cellspacing="0" bordercolor="#000000" bgcolor="#CCCCCC">
    <tr>
      <td align="left" valign="top" class="para"><strong><font
        color="#990000"><%=encprops.getProperty("setAlternateID")%>:</font></strong></td>
    </tr>
    <tr>
      <td align="left" valign="top">
        <form name="setAltID" action="../EncounterSetAlternateID"
              method="post"><input name="alternateid" type="text"
                                   size="10" maxlength="50"> <input name="encounter"
                                                                    type="hidden" value=<%=num%>>
          <input name="Set"
                 type="submit" id="<%=encprops.getProperty("set")%>" value="Set"></form>
      </td>
    </tr>
  </table>
<br /> <%
		}
		
		//set verbatimEventDate
		if((request.getParameter("isOwner").equals("true"))&&(request.getParameter("edit")!=null)&&(request.getParameter("edit").equals("verbatimEventDate"))){
		%> 
		<a name="verbatimEventDate"></a><br />
		  <table width="150" border="1" cellpadding="1" cellspacing="0" bordercolor="#000000" bgcolor="#CCCCCC">
		    <tr>
		      <td align="left" valign="top" class="para"><strong><font
		        color="#990000"><%=encprops.getProperty("setVerbatimEventDate")%>:</font></strong>
		        <br />
			<font size="-1"><em><%=encprops.getProperty("useZeroIfUnknown")%>
          		</em></font>
		        </td>
		    </tr>
		    <tr>
		      <td align="left" valign="top">
		        <form name="setVerbatimEventDate" action="../EncounterSetVerbatimEventDate"
		              method="post"><input name="verbatimEventDate" type="text" size="10" maxlength="50"> 
		              <input name="encounter" type="hidden" value=<%=num%>>
		          <input name="Set" type="submit" id="<%=encprops.getProperty("set")%>" value="Set"></form>
		      </td>
		    </tr>
		  </table>
		<br /> <%
				}
		
		
		//encounter set dynamic property
		if(CommonConfiguration.isCatalogEditable()&&request.getParameter("isOwner").equals("true")&&(request.getParameter("edit")!=null)&&(request.getParameter("edit").equals("dynamicproperty"))){
		%> <a name="dynamicproperty"></a><br />
  <table width="150" border="1" cellpadding="1" cellspacing="0" bordercolor="#000000" bgcolor="#CCCCCC">
    <tr>
      <td align="left" valign="top" class="para">
        <table>
          <tr>
            <td><img align="absmiddle" src="../images/lightning_dynamic_props.gif"/></td>
            <td><strong><font
              color="#990000"><%=encprops.getProperty("initCapsSet")%> <%=request.getParameter("name")%>
            </font></strong></td>
          </tr>
        </table>
      </td>
    </tr>
    <tr>
      <td align="left" valign="top">
        <form name="setDynProp" action="../EncounterSetDynamicProperty" method="post">
          <%
            if (enc.getDynamicPropertyValue(request.getParameter("name")) != null) {
          %>
          <input name="value" type="text" size="10" maxlength="500"
                 value="<%=enc.getDynamicPropertyValue(request.getParameter("name"))%>">
          <%
          } else {
          %>
          <input name="value" type="text" size="10" maxlength="500">
          <%
            }
          %>
          <input name="number" type="hidden" value="<%=num%>">
          <input name="name" type="hidden" value="<%=request.getParameter("name")%>">
          <input name="Set" type="submit" id="<%=encprops.getProperty("set")%>"
                 value="<%=encprops.getProperty("initCapsSet")%>"></form>
      </td>
    </tr>
  </table>
<br /> <%
		}
		
		//encounter add dynamic property
		if(request.getParameter("isOwner").equals("true")&&CommonConfiguration.isCatalogEditable()){
		%> 
		<a name="add_dynamicproperty"></a>
		<br />
  <table width="150" border="1" cellpadding="1" cellspacing="0" bordercolor="#000000" bgcolor="#CCCCCC">
    <tr>
      <td align="left" valign="top" class="para">
        <table>
          <tr>
            <td><img align="absmiddle" src="../images/lightning_dynamic_props.gif"/></td>
            <td><strong><font color="#990000"><%=encprops.getProperty("addDynamicProperty")%>
            </font></strong></td>
          </tr>
        </table>
      </td>
    </tr>
    <tr>
      <td align="left" valign="top" class="para">
        <form name="addDynProp" action="../EncounterSetDynamicProperty" method="post">
          <%=encprops.getProperty("propertyName")%>:<br/><input name="name" type="text" size="10"
                                                                maxlength="50"><br/>
          <%=encprops.getProperty("propertyValue")%>:<br/><input name="value" type="text" size="10"
                                                                 maxlength="500">
          <input name="number" type="hidden" value="<%=num%>">
          <input name="Set" type="submit" id="<%=encprops.getProperty("set")%>"
                 value="<%=encprops.getProperty("initCapsSet")%>"></form>
      </td>
    </tr>
  </table>
<br /> <%
		}
		
				
				
				//set informothers
			if((request.getParameter("isOwner").equals("true"))&&(request.getParameter("edit")!=null)&&(request.getParameter("edit").equals("others"))){
		%> <a name="others"></a><br />
  <table width="150" border="1" cellpadding="1" cellspacing="0" bordercolor="#000000" bgcolor="#CCCCCC">
    <tr>
      <td align="left" valign="top" class="para">
        <strong><%=encprops.getProperty("setOthersToInform")%>
      </td>
    </tr>
    <tr>
      <td align="left" valign="top">
        <form name="setOthers" action="../EncounterSetInformOthers" method="post">
          <input name="encounter" type="hidden" value="<%=num%>">
          <input name="informothers" type="text" size="28" <%if(enc.getInformOthers()!=null){%>
                 value="<%=enc.getInformOthers().trim()%>" <%}%> maxlength="1000">
          <br> <input name="Set" type="submit" id="Set" value="<%=encprops.getProperty("set")%>">
        </form>
      </td>
    </tr>
  </table>
<br /> <%
			}
				
				//set matchedBy type
			if((request.getParameter("isOwner").equals("true"))&&(request.getParameter("edit")!=null)&&(request.getParameter("edit").equals("manageMatchedBy"))){
		%> <a name="matchedBy"></a>
  <table width="150" border="1" cellpadding="1" cellspacing="0" bordercolor="#000000" bgcolor="#CCCCCC">
    <tr>
      <td align="left" valign="top" class="para"><font
        color="#990000"><img align="absmiddle"
                             src="../images/Crystal_Clear_app_matchedBy.gif"/>
        <strong><%=encprops.getProperty("matchedBy")%>:</strong></font></td>
    </tr>
    <tr>
      <td align="left" valign="top">
        <form name="setMBT" action="../EncounterSetMatchedBy" method="post">
          <select name="matchedBy" id="matchedBy">
            <option
              value="Unmatched first encounter"><%=encprops.getProperty("unmatchedFirstEncounter")%>
            </option>
            <option value="Visual inspection"><%=encprops.getProperty("visualInspection")%>
            </option>
            <option value="Pattern match" selected><%=encprops.getProperty("patternMatch")%>
            </option>
          </select> <input name="number" type="hidden" value=<%=num%>>
          <input name="setMB" type="submit" id="setMB" value="<%=encprops.getProperty("set")%>">
        </form>
      </td>
    </tr>
  </table>
<br /> <%
			}
				
				
			  //add this encounter to a MarkedIndividual object
			  if ((request.getParameter("isOwner").equals("true"))&&(request.getParameter("edit")!=null)&&(request.getParameter("edit").equals("manageIdentity"))) {
		%> <a name="manageIdentity"></a>
  <table width="150" border="1" cellpadding="1" cellspacing="0" bordercolor="#000000" bgcolor="#CCCCCC">
    <tr>
      <td align="left" valign="top" class="para"><font
        color="#990000">
        <img align="absmiddle" src="../images/tag_small.gif"/><br></br>
        <strong><%=encprops.getProperty("add2MarkedIndividual")%>:</strong></font></td>
    </tr>
    <tr>
      <td align="left" valign="top">
        <form name="add2shark" action="../IndividualAddEncounter"
              method="post"><%=encprops.getProperty("individual")%>: <input name="individual"
                                                                            type="text" size="10"
                                                                            maxlength="50"><br> <%=encprops.getProperty("matchedBy")%>
          :<br />
          <select name="matchType" id="matchType">
            <option
              value="Unmatched first encounter"><%=encprops.getProperty("unmatchedFirstEncounter")%>
            </option>
            <option value="Visual inspection"><%=encprops.getProperty("visualInspection")%>
            </option>
            <option value="Pattern match" selected><%=encprops.getProperty("patternMatch")%>
            </option>
          </select> <br> <input name="noemail" type="checkbox" value="noemail">
          <%=encprops.getProperty("suppressEmail")%><br> <input name="number" type="hidden"
                                                                value=<%=num%>> <input name="action"
                                                                                       type="hidden"
                                                                                       value="add">
          <input name="Add" type="submit" id="Add"
                 value="<%=encprops.getProperty("add")%>"></form>
      </td>
    </tr>
  </table>
<br /> <%
		  	}
%>		



<a name="manageOccurrence"></a>  
<!-- start Occurrence management section-->			  
	<%	
    //Remove from occurrence if assigned
	if((formShepherd.getOccurrenceForEncounter(enc.getCatalogNumber())!=null) && CommonConfiguration.isCatalogEditable() && request.getParameter("isOwner").equals("true") && (request.getParameter("edit")!=null) && (request.getParameter("edit").equals("manageOccurrence"))) {
	
	
	%>
	<table width="150" border="1" cellpadding="1" cellspacing="0" bordercolor="#000000" bgcolor="#CCCCCC">
  	<tr>
    	<td align="left" valign="top" class="para"><font color="#990000">
      <table>
        <tr>
          <td><font color="#990000"><img align="absmiddle"
                                         src="../images/cancel.gif"/></font></td>
          <td><strong><%=encprops.getProperty("removeFromOccurrence")%>
          </strong></td>
        </tr>
      </table>
    </font></td>
  </tr>
  <tr>
    <td align="left" valign="top">
      <form action="../OccurrenceRemoveEncounter" method="post" name="removeOccurrence">
      	<input name="number" type="hidden" value="<%=num%>" /> 
      	<input name="action" type="hidden" value="remove" /> 
      	<input type="submit" name="Submit" value="<%=encprops.getProperty("remove")%>" />
      </form>
    </td>
  </tr>
</table>
<br /> <%
      	}
      	  //create new Occurrence with name
      	  if(request.getParameter("isOwner").equals("true")&&(request.getParameter("edit")!=null)&&(request.getParameter("edit").equals("manageOccurrence"))){
      if((formShepherd.getOccurrenceForEncounter(enc.getCatalogNumber())==null)){
      
      %>
<table width="150" border="1" cellpadding="1" cellspacing="0" bordercolor="#000000" bgcolor="#CCCCCC">
  <tr>
    <td align="left" valign="top" class="para">
    	<font color="#990000">
      		<strong><%=encprops.getProperty("createOccurrence")%></strong></font></td>
  </tr>
  <tr>
    <td align="left" valign="top">
      <form name="createOccurrence" method="post" action="../OccurrenceCreate">
        <input name="number" type="hidden" value="<%=num%>" /> 
        <input name="action" type="hidden" value="create" /> 
        <%=encprops.getProperty("newOccurrenceID")%><br />
        <input name="occurrence" type="text" id="occurrence" size="10" maxlength="50" value="" />
        <br />
        <input name="Create" type="submit" id="Create" value="<%=encprops.getProperty("create")%>" />
      </form>
    </td>
  </tr>
</table></a>
<br/>	
	<%
      	  }
      	  }
		
	//add this encounter to an Occurrence object
	if ((formShepherd.getOccurrenceForEncounter(request.getParameter("number"))==null)&&(request.getParameter("isOwner").equals("true"))&&(request.getParameter("edit")!=null)&&(request.getParameter("edit").equals("manageOccurrence"))) {
		%> 
  <table width="150" border="1" cellpadding="1" cellspacing="0" bordercolor="#000000" bgcolor="#CCCCCC">
    <tr>
      <td align="left" valign="top" class="para"><font color="#990000">
      
        <strong><%=encprops.getProperty("add2Occurrence")%></strong></font></td>
    </tr>
    <tr>
      <td align="left" valign="top">
        <form name="add2occurrence" action="../OccurrenceAddEncounter" method="post">
        <%=encprops.getProperty("occurrenceID")%>: <input name="occurrence" type="text" size="10" maxlength="50" /><br /> 
                                                                            
            <input name="number" type="hidden" value="<%=num%>" /> 
            <input name="action" type="hidden" value="add" />
          <input name="Add" type="submit" id="Add" value="<%=encprops.getProperty("add")%>" />
          </form>
      </td>
    </tr>
  </table>
<br /> <%
		  	}
	//test comment
	
%>		
	
<!-- end Occurrence management section -->			  
			  
<%			  
		  	  //Remove from MarkedIndividual if not unassigned
		  	  if((!enc.isAssignedToMarkedIndividual().equals("Unassigned"))&&CommonConfiguration.isCatalogEditable()&&request.getParameter("isOwner").equals("true")&&(request.getParameter("edit")!=null)&&(request.getParameter("edit").equals("manageIdentity"))) {
		  %>
<table width="150" border="1" cellpadding="1" cellspacing="0" bordercolor="#000000" bgcolor="#CCCCCC">
  <tr>
    <td align="left" valign="top" class="para"><font
      color="#990000">
      <table>
        <tr>
          <td><font color="#990000"><img align="absmiddle"
                                         src="../images/cancel.gif"/></font></td>
          <td><strong><%=encprops.getProperty("removeFromMarkedIndividual")%>
          </strong></td>
        </tr>
      </table>
    </font></td>
  </tr>
  <tr>
    <td align="left" valign="top">
      <form action="../IndividualRemoveEncounter" method="post"
            name="removeShark"><input name="number" type="hidden"
                                      value=<%=num%>> <input name="action" type="hidden"
                                                             value="remove"> <input type="submit"
                                                                                    name="Submit"
                                                                                    value="<%=encprops.getProperty("remove")%>">
      </form>
    </td>
  </tr>
</table>
<br> <%
      	}
      	  //create new MarkedIndividual with name
      	  if(request.getParameter("isOwner").equals("true")&&(request.getParameter("edit")!=null)&&(request.getParameter("edit").equals("manageIdentity"))){
      %>
<table width="150" border="1" cellpadding="1" cellspacing="0" bordercolor="#000000" bgcolor="#CCCCCC">
  <tr>
    <td align="left" valign="top" class="para"><font
      color="#990000">
      <img align="absmiddle" src="../images/tag_small.gif"/>
      <strong><%=encprops.getProperty("createMarkedIndividual")%>:</strong></font></td>
  </tr>
  <tr>
    <td align="left" valign="top">
      <form name="createShark" method="post" action="../IndividualCreate">
        <input name="number" type="hidden" value="<%=num%>"> 
        <input name="action" type="hidden" value="create"> 
        <input name="individual" type="text" id="individual" size="10"
        maxlength="50"
        value="<%=getNextIndividualNumber(enc, formShepherd)%>"><br>
        <%
          if (request.getParameter("isOwner").equals("true")) {
        %> <input name="noemail" type="checkbox" value="noemail">
        <%=encprops.getProperty("suppressEmail")%><br> <%
        }
      %> <input name="Create" type="submit" id="Create" value="<%=encprops.getProperty("create")%>">
      </form>
    </td>
  </tr>
</table></a>
<br> <%
			}
      	if((request.getParameter("edit")!=null)&&(request.getParameter("edit").equals("gps"))){
      		
      		String longy="";
      		String laty="";
      		if(enc.getLatitudeAsDouble()!=null){laty=enc.getLatitudeAsDouble().toString();}
      		if(enc.getLongitudeAsDouble()!=null){longy=enc.getLongitudeAsDouble().toString();}
      		
    		%> <a name="gps"></a>
    		<table width="150" border="1" cellpadding="1" cellspacing="0" bordercolor="#000000" bgcolor="#CCCCCC">
    			<tr>
    				<td align="left" valign="top" class="para"><span class="style3"><font
    					color="#990000"><%=encprops.getProperty("resetGPS")%>:</font></span><br /> <font size="-1"><%=encprops.getProperty("leaveBlank")%></font>
    				</td>
    			</tr>
    			<tr>
    				<td>
    				<form name="resetGPSform" method="post" action="../EncounterSetGPS">
    				<input name="action" type="hidden" value="resetGPS" />
    				<p><strong><%=encprops.getProperty("latitude")%>:</strong><br /> 
    				<input type="text" size="7" maxlength="10" name="lat" id="lat" value="<%=laty %>"></input>
    				 
    				<br /> <strong><%=encprops.getProperty("longitude")%>:</strong><br> 
    				<input type="text" size="7" maxlength="10" name="longitude" id="longitude" value="<%=longy %>"></input> 
    				<input name="number" type="hidden" value=<%=num%> /> 
    				<input name="setGPSbutton" type="submit" id="setGPSbutton" value="<%=encprops.getProperty("setGPS")%>" />
    				</p>
    				</form>
    				</td>
    			</tr>
    		</table>
    		<br /> <%
    			}
				//set location for sighting
			if(request.getParameter("isOwner").equals("true")&&(request.getParameter("edit")!=null)&&(request.getParameter("edit").equals("location"))){
		%> <a name="location"></a>
  <table width="150" border="1" cellpadding="1" cellspacing="0" bordercolor="#000000" bgcolor="#CCCCCC">
    <tr>
      <td align="left" valign="top" class="para"><strong><font
        color="#990000"><%=encprops.getProperty("setLocation")%>:</font></strong></td>
    </tr>
    <tr>
      <td align="left" valign="top">
        <form name="setLocation" action="../EncounterSetLocation"
              method="post"><textarea name="location" size="15"><%=enc.getLocation()%>
        </textarea>
          <input name="number" type="hidden" value=<%=num%>> <input
            name="action" type="hidden" value="setLocation"> <input
            name="Add" type="submit" id="Add" value="<%=encprops.getProperty("setLocation")%>">
        </form>
      </td>
    </tr>
  </table>
<br /> <%
			}
				
//update submitted comments for sighting
if(request.getParameter("isOwner").equals("true")&&(request.getParameter("edit")!=null)&&(request.getParameter("edit").equals("comments"))){
%> 
<a name="comments"></a>
  <table width="150" border="1" cellpadding="1" cellspacing="0" bordercolor="#000000" bgcolor="#CCCCCC">
    <tr>
      <td align="left" valign="top" class="para"><strong><font
        color="#990000"><%=encprops.getProperty("editSubmittedComments")%>:</font></strong></td>
    </tr>
    <tr>
      <td align="left" valign="top">
        <form name="setComments" action="../EncounterSetOccurrenceRemarks"
              method="post"><textarea name="fixComment" size="15"><%=enc.getComments()%>
        </textarea>
          <input name="number" type="hidden" value=<%=num%>> <input
            name="action" type="hidden" value="editComments"> <input
            name="EditComm" type="submit" id="EditComm"
            value="<%=encprops.getProperty("submitEdit")%>"></form>
      </td>
    </tr>
  </table>
<br /> 
<%
}

//update submitted comments for sighting
if(request.getParameter("isOwner").equals("true")&&(request.getParameter("edit")!=null)&&(request.getParameter("edit").equals("behavior"))){
%> 
<a name="behavior"></a>
  <table width="150" border="1" cellpadding="1" cellspacing="0" bordercolor="#000000" bgcolor="#CCCCCC">
    <tr>
      <td align="left" valign="top" class="para">
      	<strong><font color="#990000"><%=encprops.getProperty("editBehaviorComments")%>:</font></strong>
      	<br /><font size="-1"><%=encprops.getProperty("leaveBlank")%></font>
      </td>
    </tr>
    <tr>
      <td align="left" valign="top">
        <form name="setBehaviorComments" action="../EncounterSetBehavior"
              method="post"><textarea name="behaviorComment" size="15">
         <%
         if((enc.getBehavior()!=null)&&(!enc.getBehavior().trim().equals(""))){
         %>
              <%=enc.getBehavior()%>
        <%
        }
        %>
        </textarea>
          <input name="number" type="hidden" value=<%=num%>> <input
            name="action" type="hidden" value="editBehavior"> <input
            name="EditBeh" type="submit" id="EditBeh"
            value="<%=encprops.getProperty("submitEdit")%>"></form>
      </td>
    </tr>
  </table>
<br /> 
<%
}
//reset contact info
if(request.getParameter("isOwner").equals("true")&&(request.getParameter("edit")!=null)&&(request.getParameter("edit").equals("contact"))){
		%> 

<a name="contact"></a>
  <table width="150" border="1" cellpadding="1" cellspacing="0" bordercolor="#000000" bgcolor="#CCCCCC">
    <tr>
      <td align="left" valign="top" class="para"><strong><font
        color="#990000"><%=encprops.getProperty("editContactInfo")%>:</font></strong></td>
    </tr>
    <tr>
      <td></td>
    </tr>
    <tr>
      <td align="left" valign="top">
        <form name="setPersonalDetails"
              action="../EncounterSetSubmitterPhotographerContactInfo"
              method="post"><label> <input type="radio"
                                           name="contact"
                                           value="submitter"><%=encprops.getProperty("submitter")%>
        </label> <br /><label>
          <input type="radio" name="contact"
                 value="photographer"><%=encprops.getProperty("photographer")%>
        </label>
          <br /> 
          
          <%=encprops.getProperty("name")%><br />
          <input name="name" type="text" size="20" maxlength="100" /> 
          
          <%=encprops.getProperty("email")%><br />
          <input name="email" type="text" size="20" /> 
          
          <%=encprops.getProperty("phone")%><br />
          <input name="phone" type="text" size="20" maxlength="100" /> 
          
          <%=encprops.getProperty("address")%><br />
          <input name="address" type="text" size="20" maxlength="100" /> 
          
           <%=encprops.getProperty("submitterOrganization")%><br />
          <input name="submitterOrganization" type="text" size="20" maxlength="100" /> 
          
          <%=encprops.getProperty("submitterProject")%><br />
	  <input name="submitterProject" type="text" size="20" maxlength="100" /> 
	            
          
            
            
            
            <input name="number" type="hidden" value="<%=num%>" /> 
            <input name="action" type="hidden" value="editcontact" /> 
            <input name="EditContact" type="submit" id="EditContact" value="Update" />
        </form>
      </td>
    </tr>
  </table>

<br /> 
<%
}


//reset or create a tissue sample
if(request.getParameter("isOwner").equals("true")&&(request.getParameter("edit")!=null)&&(request.getParameter("edit").equals("tissueSample"))){
		%> 

<a name="tissueSample"></a>
<table width="150" border="1" cellpadding="1" cellspacing="0" bordercolor="#000000" bgcolor="#CCCCCC">
    <tr>
      <td align="left" valign="top" class="para"><strong>
      <font color="#990000"><%=encprops.getProperty("setTissueSample")%>:</font></strong></td>
    </tr>
    <tr>
      <td></td>
    </tr>
    <tr>
      <td align="left" valign="top">
        <form name="setTissueSample" action="../EncounterSetTissueSample" method="post">

          <%=encprops.getProperty("sampleID")%> (<%=encprops.getProperty("required")%>)<br />
          <%
          TissueSample thisSample=new TissueSample();
          String sampleIDString="";
          if((request.getParameter("sampleID")!=null)&&(formShepherd.isTissueSample(request.getParameter("sampleID"), request.getParameter("number")))){
        	  sampleIDString=request.getParameter("sampleID");
        	  thisSample=formShepherd.getTissueSample(sampleIDString, enc.getCatalogNumber());
              
          }
          %>
          <input name="sampleID" type="text" size="20" maxlength="100" value="<%=sampleIDString %>" /><br />
          
          <%
          String alternateSampleID="";
          if(thisSample.getAlternateSampleID()!=null){alternateSampleID=thisSample.getAlternateSampleID();}
          %>
          <%=encprops.getProperty("alternateSampleID")%><br />
          <input name="alternateSampleID" type="text" size="20" maxlength="100" value="<%=alternateSampleID %>" /> 
          
          <%
          String tissueType="";
          if(thisSample.getTissueType()!=null){tissueType=thisSample.getTissueType();}
          %>
          <%=encprops.getProperty("tissueType")%><br />
          
          
          
                        <%
              if(CommonConfiguration.getProperty("tissueType0")==null){
              %>
              <input name="tissueType" type="text" size="20" maxlength="50"> 
              <%
              }
              else{
            	  //iterate and find the locationID options
            	  %>
            	  <select name="tissueType" id="tissueType">
						            	<option value=""></option>
						       
						       <%
						       boolean hasMoreLocs=true;
						       int taxNum=0;
						       while(hasMoreLocs){
						       	  String currentLoc = "tissueType"+taxNum;
						       	  if(CommonConfiguration.getProperty(currentLoc)!=null){
						       		  
						       		  String selected="";
						       		  if(tissueType.equals(CommonConfiguration.getProperty(currentLoc))){selected="selected=\"selected\"";}
						       	  	%>
						       	  	 
						       	  	  <option value="<%=CommonConfiguration.getProperty(currentLoc)%>" <%=selected %>><%=CommonConfiguration.getProperty(currentLoc)%></option>
						       	  	<%
						       		taxNum++;
						          }
						          else{
						             hasMoreLocs=false;
						          }
						          
						       }
						       %>
						       
						       
						      </select>  
            	  
            	  
            <% 	  
              }
              %>
          
          
          
          <%
          String preservationMethod="";
          if(thisSample.getPreservationMethod()!=null){preservationMethod=thisSample.getPreservationMethod();}
          %>
          <%=encprops.getProperty("preservationMethod")%><br />
          <input name="preservationMethod" type="text" size="20" maxlength="100" value="<%=preservationMethod %>"/> 
          
          <%
          String storageLabID="";
          if(thisSample.getStorageLabID()!=null){storageLabID=thisSample.getStorageLabID();}
          %>
          <%=encprops.getProperty("storageLabID")%><br />
          <input name="storageLabID" type="text" size="20" maxlength="100" value="<%=storageLabID %>"/> 
          
          <%
          String samplingProtocol="";
          if(thisSample.getSamplingProtocol()!=null){samplingProtocol=thisSample.getSamplingProtocol();}
          %>
          <%=encprops.getProperty("samplingProtocol")%><br />
          <input name="samplingProtocol" type="text" size="20" maxlength="100" value="<%=samplingProtocol %>" /> 
          
          <%
          String samplingEffort="";
          if(thisSample.getSamplingEffort()!=null){samplingEffort=thisSample.getSamplingEffort();}
          %>
          <%=encprops.getProperty("samplingEffort")%><br />
          <input name="samplingEffort" type="text" size="20" maxlength="100" value="<%=samplingEffort%>"/> 
     
          <%
          String fieldNumber="";
          if(thisSample.getFieldNumber()!=null){fieldNumber=thisSample.getFieldNumber();}
          %>
		  <%=encprops.getProperty("fieldNumber")%><br />
          <input name="fieldNumber" type="text" size="20" maxlength="100" value="<%=fieldNumber %>" /> 
          
          <%
          String fieldNotes="";
          if(thisSample.getFieldNotes()!=null){fieldNotes=thisSample.getFieldNotes();}
          %>
           <%=encprops.getProperty("fieldNotes")%><br />
          <input name="fieldNNotes" type="text" size="20" maxlength="100" value="<%=fieldNotes %>" /> 
          
          
          <%
          String eventRemarks="";
          if(thisSample.getEventRemarks()!=null){eventRemarks=thisSample.getEventRemarks();}
          %>
          <%=encprops.getProperty("eventRemarks")%><br />
          <input name="eventRemarks" type="text" size="20" value="<%=eventRemarks %>" /> 
          
          <%
          String institutionID="";
          if(thisSample.getInstitutionID()!=null){institutionID=thisSample.getInstitutionID();}
          %>
          <%=encprops.getProperty("institutionID")%><br />
          <input name="institutionID" type="text" size="20" maxlength="100" value="<%=institutionID %>" /> 
          
          <%
          String collectionID="";
          if(thisSample.getCollectionID()!=null){collectionID=thisSample.getCollectionID();}
          %>
          <%=encprops.getProperty("collectionID")%><br />
          <input name="collectionID" type="text" size="20" maxlength="100" value="<%=collectionID %>" /> 
          
          <%
          String collectionCode="";
          if(thisSample.getCollectionCode()!=null){collectionCode=thisSample.getCollectionCode();}
          %>
          <%=encprops.getProperty("collectionCode")%><br />
          <input name="collectionCode" type="text" size="20" maxlength="100" value="<%=collectionCode %>" /> 
          
          <%
          String datasetID="";
          if(thisSample.getDatasetID()!=null){datasetID=thisSample.getDatasetID();}
          %>
			<%=encprops.getProperty("datasetID")%><br />
          <input name="datasetID" type="text" size="20" maxlength="100" value="<%=datasetID %>" /> 
          
          <%
          String datasetName="";
          if(thisSample.getDatasetName()!=null){datasetName=thisSample.getDatasetName();}
          %>
          <%=encprops.getProperty("datasetName")%><br />
          <input name="datasetName" type="text" size="20" maxlength="100" value="<%=datasetName %>" /> 

            
            <input name="encounter" type="hidden" value="<%=num%>" /> 
            <input name="action" type="hidden" value="setTissueSample" /> 
            <input name="EditTissueSample" type="submit" id="EditTissueSample" value="Set" />
        </form>
      </td>
    </tr>
  </table>

<br /> 
<%
}



//reset or create a haplotype
if(request.getParameter("isOwner").equals("true")&&(request.getParameter("edit")!=null)&&(request.getParameter("edit").equals("haplotype"))){
		%> 

<a name="haplotype"></a>
<table width="150" border="1" cellpadding="1" cellspacing="0" bordercolor="#000000" bgcolor="#CCCCCC">
  <tr>
    <td align="left" valign="top" class="para"><strong>
    <font color="#990000"><%=encprops.getProperty("setHaplotype")%>:</font></strong></td>
  </tr>
  <tr>
    <td></td>
  </tr>
  <tr>
    <td align="left" valign="top">
      <form name="setHaplotype" action="../TissueSampleSetHaplotype" method="post">

        <%=encprops.getProperty("analysisID")%> (<%=encprops.getProperty("required")%>)<br />
        <%
        MitochondrialDNAAnalysis mtDNA=new MitochondrialDNAAnalysis();
        String analysisIDString="";
        if((request.getParameter("analysisID")!=null)&&(formShepherd.isGeneticAnalysis(request.getParameter("sampleID"),request.getParameter("number"),request.getParameter("analysisID"),"MitochondrialDNA"))){
      	    analysisIDString=request.getParameter("analysisID");
      		mtDNA=formShepherd.getMitochondrialDNAAnalysis(request.getParameter("sampleID"), enc.getCatalogNumber(),analysisIDString);
        }
        %>
        <input name="analysisID" type="text" size="20" maxlength="100" value="<%=analysisIDString %>" /><br />
        
        <%
        String haplotypeString="";
        try{
        	if(mtDNA.getHaplotype()!=null){haplotypeString=mtDNA.getHaplotype();}
        }
        catch(NullPointerException npe34){}
        %>
        <%=encprops.getProperty("haplotype")%> (<%=encprops.getProperty("required")%>)<br />
        <input name="haplotype" type="text" size="20" maxlength="100" value="<%=haplotypeString %>" /> 
 		
 		 <%
        String processingLabTaskID="";
        if(mtDNA.getProcessingLabTaskID()!=null){processingLabTaskID=mtDNA.getProcessingLabTaskID();}
        %>
        <%=encprops.getProperty("processingLabTaskID")%><br />
        <input name="processingLabTaskID" type="text" size="20" maxlength="100" value="<%=processingLabTaskID %>" /> 
 
  		 <%
        String processingLabName="";
        if(mtDNA.getProcessingLabName()!=null){processingLabName=mtDNA.getProcessingLabName();}
        %>
        <%=encprops.getProperty("processingLabName")%><br />
        <input name="processingLabName type="text" size="20" maxlength="100" value="<%=processingLabName %>" /> 
 
   		 <%
        String processingLabContactName="";
        if(mtDNA.getProcessingLabContactName()!=null){processingLabContactName=mtDNA.getProcessingLabContactName();}
        %>
        <%=encprops.getProperty("processingLabContactName")%><br />
        <input name="processingLabContactName type="text" size="20" maxlength="100" value="<%=processingLabContactName %>" /> 
 
   		 <%
        String processingLabContactDetails="";
        if(mtDNA.getProcessingLabContactDetails()!=null){processingLabContactDetails=mtDNA.getProcessingLabContactDetails();}
        %>
        <%=encprops.getProperty("processingLabContactDetails")%><br />
        <input name="processingLabContactDetails type="text" size="20" maxlength="100" value="<%=processingLabContactDetails %>" /> 
 
 		  <input name="sampleID" type="hidden" value="<%=request.getParameter("sampleID")%>" /> 
          <input name="number" type="hidden" value="<%=num%>" /> 
          <input name="action" type="hidden" value="setHaplotype" /> 
          <input name="EditTissueSample" type="submit" id="EditTissueSample" value="Set" />
      </form>
    </td>
  </tr>
</table>
<br /> 
<%
}


//reset or create a biological measurement
if(request.getParameter("isOwner").equals("true")&&(request.getParameter("edit")!=null)&&(request.getParameter("edit").equals("addBiologicalMeasurement"))){
		%> 

<a name="addBiologicalMeasurement"></a>
<table width="150" border="1" cellpadding="1" cellspacing="0" bordercolor="#000000" bgcolor="#CCCCCC">
<tr>
<td align="left" valign="top" class="para"><strong>
<font color="#990000"><%=encprops.getProperty("setBiologicalMeasurement")%></font></strong></td>
</tr>
<tr>
<td></td>
</tr>
<tr>
<td align="left" valign="top">
  <form name="setBiologicalMeasurement" action="../TissueSampleSetMeasurement" method="post">

    <%=encprops.getProperty("analysisID")%> (<%=encprops.getProperty("required")%>)<br />
    <%
    BiologicalMeasurement mtDNA=new BiologicalMeasurement();
    String analysisIDString="";
    if((request.getParameter("analysisID")!=null)&&(formShepherd.isGeneticAnalysis(request.getParameter("sampleID"),request.getParameter("number"),request.getParameter("analysisID"),"BiologicalMeasurement"))){
  	    analysisIDString=request.getParameter("analysisID");
  		mtDNA=formShepherd.getBiologicalMeasurement(request.getParameter("sampleID"), enc.getCatalogNumber(),analysisIDString);
    }
    %>
    <input name="analysisID" type="text" size="20" maxlength="100" value="<%=analysisIDString %>" /><br />
    

    
    <%
    String type="";
    if(mtDNA.getMeasurementType()!=null){type=mtDNA.getMeasurementType();}
    %>
    <%=encprops.getProperty("type")%> (<%=encprops.getProperty("required")%>)<br />
    


     		<%
     		ArrayList<String> values=CommonConfiguration.getSequentialPropertyValues("biologicalMeasurementType");
 			int numProps=values.size();
 			ArrayList<String> measurementUnits=CommonConfiguration.getSequentialPropertyValues("biologicalMeasurementUnits");
 			int numUnitsProps=measurementUnits.size();
     		
     		if(numProps>0){

     			%>
     			<p><select size="<%=(numProps+1) %>" name="measurementType" id="measurementType">
     			<%
     		
     			for(int y=0;y<numProps;y++){
     				String units="";
     				if(numUnitsProps>y){units="&nbsp;("+measurementUnits.get(y)+")";}
     				String selected="";
     				if((mtDNA.getMeasurementType()!=null)&&(mtDNA.getMeasurementType().equals(values.get(y)))){
     					selected="selected=\"selected\"";
     				}
     			%>
     				<option value="<%=values.get(y) %>" <%=selected %>><%=values.get(y) %><%=units %></option>
     			<%
     			}
     			%>
     			</select>
				</p>
			<%
     		}
     		else{
			%>
    			<input name="measurementType" type="text" size="20" maxlength="100" value="<%=type %>" /> 
    		<%
     		}
    		
     		
     		
    String thisValue="";
    if(mtDNA.getValue()!=null){thisValue=mtDNA.getValue().toString();}
    %>
    <%=encprops.getProperty("value")%> (<%=encprops.getProperty("required")%>)<br />
    <input name="value" type="text" size="20" maxlength="100" value="<%=thisValue %>"></input>
    
	<%
    String thisSamplingProtocol="";
    if(mtDNA.getSamplingProtocol()!=null){thisSamplingProtocol=mtDNA.getSamplingProtocol();}
    %>
    <%=encprops.getProperty("samplingProtocol")%><br />
    
    
     		<%
     		ArrayList<String> protovalues=CommonConfiguration.getSequentialPropertyValues("biologicalMeasurementSamplingProtocols");
 			int protonumProps=protovalues.size();
     		
     		if(protonumProps>0){

     			%>
     			<p><select size="<%=(protonumProps+1) %>" name="samplingProtocol" id="samplingProtocol">
     			<%
     		
     			for(int y=0;y<protonumProps;y++){
     				String selected="";
     				if((mtDNA.getSamplingProtocol()!=null)&&(mtDNA.getSamplingProtocol().equals(protovalues.get(y)))){
     					selected="selected=\"selected\"";
     				}
     			%>
     				<option value="<%=protovalues.get(y) %>" <%=selected %>><%=protovalues.get(y) %></option>
     			<%
     			}
     			%>
     			</select>
				</p>
			<%
     		}
     		else{
			%>
    			<input name="samplingProtocol" type="text" size="20" maxlength="100" value="<%=type %>" /> 
    		<%
     		}

    String processingLabTaskID="";
    if(mtDNA.getProcessingLabTaskID()!=null){processingLabTaskID=mtDNA.getProcessingLabTaskID();}
    %>
    <%=encprops.getProperty("processingLabTaskID")%><br />
    <input name="processingLabTaskID" type="text" size="20" maxlength="100" value="<%=processingLabTaskID %>" /> 

		 <%
    String processingLabName="";
    if(mtDNA.getProcessingLabName()!=null){processingLabName=mtDNA.getProcessingLabName();}
    %>
    <%=encprops.getProperty("processingLabName")%><br />
    <input name="processingLabName" type="text" size="20" maxlength="100" value="<%=processingLabName %>" /> 

		 <%
    String processingLabContactName="";
    if(mtDNA.getProcessingLabContactName()!=null){processingLabContactName=mtDNA.getProcessingLabContactName();}
    %>
    <%=encprops.getProperty("processingLabContactName")%><br />
    <input name="processingLabContactName" type="text" size="20" maxlength="100" value="<%=processingLabContactName %>" /> 

		 <%
    String processingLabContactDetails="";
    if(mtDNA.getProcessingLabContactDetails()!=null){processingLabContactDetails=mtDNA.getProcessingLabContactDetails();}
    %>
    <%=encprops.getProperty("processingLabContactDetails")%><br />
    <input name="processingLabContactDetails" type="text" size="20" maxlength="100" value="<%=processingLabContactDetails %>" /> 

		  <input name="sampleID" type="hidden" value="<%=request.getParameter("sampleID")%>" /> 
      <input name="encounter" type="hidden" value="<%=num%>" /> 
      <input name="action" type="hidden" value="setBiologicalMeasurement" /> 
      <input name="EditTissueSampleBiomeasurementAnalysis" type="submit" id="EditTissueSampleBioMeasurementAnalysis" value="Set" />
  </form>
</td>
</tr>
</table>
<br /> 
<%
}



//reset or create a genetic sex analysis
if(request.getParameter("isOwner").equals("true")&&(request.getParameter("edit")!=null)&&(request.getParameter("edit").equals("sexAnalysis"))){
		%> 

<a name="sexAnalysis"></a>
<table width="150" border="1" cellpadding="1" cellspacing="0" bordercolor="#000000" bgcolor="#CCCCCC">
<tr>
  <td align="left" valign="top" class="para"><strong>
  <font color="#990000"><%=encprops.getProperty("setSexAnalysis")%>:</font></strong></td>
</tr>
<tr>
  <td></td>
</tr>
<tr>
  <td align="left" valign="top">
    <form name="setSexAnalysis" action="../TissueSampleSetSexAnalysis" method="post">

      <%=encprops.getProperty("analysisID")%> (<%=encprops.getProperty("required")%>)<br />
      <%
      SexAnalysis mtDNA=new SexAnalysis();
      String analysisIDString="";
      if((request.getParameter("analysisID")!=null)&&(formShepherd.isGeneticAnalysis(request.getParameter("sampleID"),request.getParameter("number"),request.getParameter("analysisID"),"SexAnalysis"))){
    	    analysisIDString=request.getParameter("analysisID");
    		mtDNA=formShepherd.getSexAnalysis(request.getParameter("sampleID"), enc.getCatalogNumber(),analysisIDString);
      }
      %>
      <input name="analysisID" type="text" size="20" maxlength="100" value="<%=analysisIDString %>" /><br />
      
      <%
      String haplotypeString="";
      try{
      	if(mtDNA.getSex()!=null){haplotypeString=mtDNA.getSex();}
      }
      catch(NullPointerException npe34){}
      %>
      <%=encprops.getProperty("geneticSex")%> (<%=encprops.getProperty("required")%>)<br />
      <input name="sex" type="text" size="20" maxlength="100" value="<%=haplotypeString %>" /> 
		
		 <%
      String processingLabTaskID="";
      if(mtDNA.getProcessingLabTaskID()!=null){processingLabTaskID=mtDNA.getProcessingLabTaskID();}
      %>
      <%=encprops.getProperty("processingLabTaskID")%><br />
      <input name="processingLabTaskID" type="text" size="20" maxlength="100" value="<%=processingLabTaskID %>" /> 

		 <%
      String processingLabName="";
      if(mtDNA.getProcessingLabName()!=null){processingLabName=mtDNA.getProcessingLabName();}
      %>
      <%=encprops.getProperty("processingLabName")%><br />
      <input name="processingLabName type="text" size="20" maxlength="100" value="<%=processingLabName %>" /> 

 		 <%
      String processingLabContactName="";
      if(mtDNA.getProcessingLabContactName()!=null){processingLabContactName=mtDNA.getProcessingLabContactName();}
      %>
      <%=encprops.getProperty("processingLabContactName")%><br />
      <input name="processingLabContactName type="text" size="20" maxlength="100" value="<%=processingLabContactName %>" /> 

 		 <%
      String processingLabContactDetails="";
      if(mtDNA.getProcessingLabContactDetails()!=null){processingLabContactDetails=mtDNA.getProcessingLabContactDetails();}
      %>
      <%=encprops.getProperty("processingLabContactDetails")%><br />
      <input name="processingLabContactDetails type="text" size="20" maxlength="100" value="<%=processingLabContactDetails %>" /> 

		  <input name="sampleID" type="hidden" value="<%=request.getParameter("sampleID")%>" /> 
        <input name="number" type="hidden" value="<%=num%>" /> 
        <input name="action" type="hidden" value="setSexAnalysis" /> 
        <input name="EditTissueSampleSexAnalysis" type="submit" id="EditTissueSampleSexAnalysis" value="Set" />
    </form>
  </td>
</tr>
</table>
<br /> 
<%
}



//reset or create ms markers
if(request.getParameter("isOwner").equals("true")&&(request.getParameter("edit")!=null)&&(request.getParameter("edit").equals("msMarkers"))){
		%> 

<a name="msMarkers"></a>
<table width="150" border="1" cellpadding="1" cellspacing="0" bordercolor="#000000" bgcolor="#CCCCCC">
  <tr>
    <td align="left" valign="top" class="para"><strong>
    <font color="#990000"><%=encprops.getProperty("setMsMarkers")%>:</font></strong></td>
  </tr>
  <tr>
    <td></td>
  </tr>
  <tr>
    <td align="left" valign="top">
      <form name="setMsMarkers" action="../TissueSampleSetMicrosatelliteMarkers" method="post">

        <%=encprops.getProperty("analysisID")%> (<%=encprops.getProperty("required")%>)<br />
        <%
        MicrosatelliteMarkersAnalysis msDNA=new MicrosatelliteMarkersAnalysis();
        String analysisIDString="";
        if((request.getParameter("analysisID")!=null)&&(formShepherd.isGeneticAnalysis(request.getParameter("sampleID"),request.getParameter("number"),request.getParameter("analysisID"),"MicrosatelliteMarkers"))){
      	    analysisIDString=request.getParameter("analysisID");
      		msDNA=formShepherd.getMicrosatelliteMarkersAnalysis(request.getParameter("sampleID"), enc.getCatalogNumber(),analysisIDString);
        }
        %>
        <input name="analysisID" type="text" size="20" maxlength="100" value="<%=analysisIDString %>" /><br />
        

 		 <%
        String processingLabTaskID="";
        if(msDNA.getProcessingLabTaskID()!=null){processingLabTaskID=msDNA.getProcessingLabTaskID();}
        %>
        <%=encprops.getProperty("processingLabTaskID")%><br />
        <input name="processingLabTaskID" type="text" size="20" maxlength="100" value="<%=processingLabTaskID %>" /> 
 
  		 <%
        String processingLabName="";
        if(msDNA.getProcessingLabName()!=null){processingLabName=msDNA.getProcessingLabName();}
        %>
        <%=encprops.getProperty("processingLabName")%><br />
        <input name="processingLabName" type="text" size="20" maxlength="100" value="<%=processingLabName %>" /> 
 
   		 <%
        String processingLabContactName="";
        if(msDNA.getProcessingLabContactName()!=null){processingLabContactName=msDNA.getProcessingLabContactName();}
        %>
        <%=encprops.getProperty("processingLabContactName")%><br />
        <input name="processingLabContactName" type="text" size="20" maxlength="100" value="<%=processingLabContactName %>" /> 
 		<br />
   		 <%
        String processingLabContactDetails="";
        if(msDNA.getProcessingLabContactDetails()!=null){processingLabContactDetails=msDNA.getProcessingLabContactDetails();}
        %>
        <%=encprops.getProperty("processingLabContactDetails")%><br />
        <input name="processingLabContactDetails" type="text" size="20" maxlength="100" value="<%=processingLabContactDetails %>" /> 
 		<br />
 		<%
 		//begin setting up the loci and alleles
 	      int numPloids=2; //most covered species will be diploids
 	      try{
 	        numPloids=(new Integer(CommonConfiguration.getProperty("numPloids"))).intValue();
 	      }
 	      catch(Exception e){System.out.println("numPloids configuration value did not resolve to an integer.");e.printStackTrace();}
 	      
 	      int numLoci=10;
 	      try{
 	 	  	numLoci=(new Integer(CommonConfiguration.getProperty("numLoci"))).intValue();
 	 	  }
 	 	  catch(Exception e){System.out.println("numLoci configuration value did not resolve to an integer.");e.printStackTrace();}
 	 	   
 		  for(int locus=0;locus<numLoci;locus++){
 			 String locusNameValue="";
 			 if((msDNA.getLoci()!=null)&&(locus<msDNA.getLoci().size())){locusNameValue=msDNA.getLoci().get(locus).getName();}
 		  %>
			<br /><%=encprops.getProperty("locus") %>: <input name="locusName<%=locus %>" type="text" size="10" value="<%=locusNameValue %>" /><br />
 				<%
 				for(int ploid=0;ploid<numPloids;ploid++){
 					Integer ploidValue=0;
 					if((msDNA.getLoci()!=null)&&(locus<msDNA.getLoci().size())&&(msDNA.getLoci().get(locus).getAllele(ploid)!=null)){ploidValue=msDNA.getLoci().get(locus).getAllele(ploid);}
 			 		  
 				%>
 				<%=encprops.getProperty("allele") %>: <input name="allele<%=locus %><%=ploid %>" type="text" size="10" value="<%=ploidValue %>" /><br />
 			
 			
 				<%
 				}
 				%>
 			
		  <%
 		  }  //end for loci loop
		  %> 
 		  <input name="sampleID" type="hidden" value="<%=request.getParameter("sampleID")%>" /> 
          <input name="number" type="hidden" value="<%=num%>" /> 
          <input name="action" type="hidden" value="setHaplotype" /> 
          <input name="EditTissueSample" type="submit" id="EditTissueSample" value="Set" />
      </form>
    </td>
  </tr>
</table>
<br /> 
<%
}


//--------------------------
//edit sex reported for sighting	
if((request.getParameter("edit")!=null)&&(request.getParameter("edit").equals("sex"))){
						%> 
<a name="sex"></a>
<table width="150" border="1" cellpadding="1" cellspacing="0" bordercolor="#000000" bgcolor="#CCCCCC">
  <tr>
    <td align="left" valign="top" class="para"><strong><font
      color="#990000"><%=encprops.getProperty("resetSex")%>:</font></strong></td>
  </tr>
  <tr>
    <td align="left" valign="top">
      <form name="setxencshark" action="../EncounterSetSex" method="post">
        <select name="selectSex" size="1" id="selectSex">
          <option value="unknown" selected><%=encprops.getProperty("unknown")%>
          </option>
          <option value="male"><%=encprops.getProperty("male")%>
          </option>
          <option value="female"><%=encprops.getProperty("female")%>
          </option>
        </select> <input name="number" type="hidden" value="<%=num%>" id="number">
        <input name="action" type="hidden" value="setEncounterSex">
        <input name="Add" type="submit" id="Add" value="<%=encprops.getProperty("resetSex")%>">
      </form>
    </td>
  </tr>
</table>
<br> <%
			}
			
		if((request.getParameter("edit")!=null)&&(request.getParameter("edit").equals("livingStatus"))){
						%> <a name="livingStatus"></a>
<table width="150" border="1" cellpadding="1" cellspacing="0" bordercolor="#000000" bgcolor="#CCCCCC">
  <tr>
    <td align="left" valign="top" class="para"><strong><font
      color="#990000"><img align="absmiddle"
                           src="../images/life_icon.gif"> <%=encprops.getProperty("resetStatus")%>:</font></strong>
    </td>
  </tr>
  <tr>
    <td align="left" valign="top">
      <form name="livingStatusForm" action="../EncounterSetLivingStatus"
            method="post"><select name="livingStatus" id="livingStatus">
        <option value="alive" selected><%=encprops.getProperty("alive")%>
        </option>
        <option value="dead"><%=encprops.getProperty("dead")%>
        </option>
      </select> <input name="encounter" type="hidden" value="<%=num%>" id="number">
        <input name="Add" type="submit" id="Add" value="<%=encprops.getProperty("resetStatus")%>">
      </form>
    </td>
  </tr>
</table>
<br /> <%
			}
			
if((request.getParameter("edit")!=null)&&(request.getParameter("edit").equals("genusSpecies"))){
									%> <a name="genusSpecies"></a>
			<table width="150" border="1" cellpadding="1" cellspacing="0" bordercolor="#000000" bgcolor="#CCCCCC">
			  <tr>
			    <td align="left" valign="top" class="para"><strong><font
			      color="#990000"><img align="absmiddle"
			                           src="../images/taxontree.gif"> <%=encprops.getProperty("resetTaxonomy")%>:</font></strong>
			    </td>
			  </tr>
			  <tr>
			    <td align="left" valign="top">
			      <form name="taxonomyForm" action="../EncounterSetGenusSpecies" method="post">
			            <select name="genusSpecies" id="genusSpecies">
			            	<option value="unknown"><%=encprops.getProperty("notAvailable")%></option>
			       
			       <%
			       boolean hasMoreTax=true;
			       int taxNum=0;
			       while(hasMoreTax){
			       	  String currentGenuSpecies = "genusSpecies"+taxNum;
			       	  if(CommonConfiguration.getProperty(currentGenuSpecies)!=null){
			       	  	%>
			       	  	 
			       	  	  <option value="<%=CommonConfiguration.getProperty(currentGenuSpecies)%>"><%=CommonConfiguration.getProperty(currentGenuSpecies)%></option>
			       	  	<%
			       		taxNum++;
			          }
			          else{
			             hasMoreTax=false;
			          }
			          
			       }
			       %>
			       
			       
			      </select> <input name="encounter" type="hidden" value="<%=num%>" id="number">
			        <input name="<%=encprops.getProperty("set")%>" type="submit" id="<%=encprops.getProperty("set")%>" value="<%=encprops.getProperty("set")%>">
			      </form>
			    </td>
			  </tr>
			</table>
			<br /> <%
	}
	
	//
	if((request.getParameter("edit")!=null)&&(request.getParameter("edit").equals("lifeStage"))){
		%> 
		<a name="lifeStage"></a>
		<table width="150" border="1" cellpadding="1" cellspacing="0" bordercolor="#000000" bgcolor="#CCCCCC">
			<tr>
				<td align="left" valign="top" class="para"><strong><font color="#990000">
					<%=encprops.getProperty("resetLifeStage")%>:</font></strong><br /> <font size="-1"><%=encprops.getProperty("leaveBlank")%></font>
						    </td>
						  </tr>
						  <tr>
						    <td align="left" valign="top">
						      <form name="lifeStageForm" action="../EncounterSetLifeStage" method="post">
						            <select name="lifeStage" id="lifeStage">
						            	<option value=""></option>
						       
						       <%
						       boolean hasMoreStages=true;
						       int taxNum=0;
						       while(hasMoreStages){
						       	  String currentLifeStage = "lifeStage"+taxNum;
						       	  if(CommonConfiguration.getProperty(currentLifeStage)!=null){
						       	  	%>
						       	  	 
						       	  	  <option value="<%=CommonConfiguration.getProperty(currentLifeStage)%>"><%=CommonConfiguration.getProperty(currentLifeStage)%></option>
						       	  	<%
						       		taxNum++;
						          }
						          else{
						             hasMoreStages=false;
						          }
						          
						       }
						       %>
						       
						       
						      </select> <input name="encounter" type="hidden" value="<%=num%>" id="number">
						        <input name="<%=encprops.getProperty("set")%>" type="submit" id="<%=encprops.getProperty("set")%>" value="<%=encprops.getProperty("set")%>">
						      </form>
						    </td>
						  </tr>
						</table>
						<br /> <%
	}
%>			

<c:if test="${param.edit eq 'measurements'}">
 <% 
   pageContext.setAttribute("items", Util.findMeasurementDescs(langCode)); 
 %>
        <a name="measurements"></a>
        <table width="150" border="1" cellpadding="1" cellspacing="0" bordercolor="#000000" bgcolor="#CCCCCC">
        <form name="setMeasurements" method="post"
                action="../EncounterSetMeasurements">
        <input type="hidden" name="encounter" value="${num}"/>
        <c:set var="index" value="0"/>
        <%
          List<Measurement> list = (List<Measurement>) enc.getMeasurements();
        
        %>
        <c:forEach items="${items}" var="item">
        <%
          MeasurementDesc measurementDesc = (MeasurementDesc) pageContext.getAttribute("item");
          Measurement measurement = enc.findMeasurementOfType(measurementDesc.getType());
          if (measurement == null) {
              measurement = new Measurement(enc.getEventID(), measurementDesc.getType(), null, measurementDesc.getUnits(), null);
          }
          pageContext.setAttribute("measurementEvent", measurement);
          pageContext.setAttribute("optionDescs", Util.findSamplingProtocols(langCode));
        %>
            <tr>
              <td class="form_label"><c:out value="${item.label}"/><input type="hidden" name="measurement${index}(id)" value="${measurementEvent.dataCollectionEventID}"/></td>
              <td><input name="measurement${index}(value)" value="${measurementEvent.value}"/>
                  <input type="hidden" name="measurement${index}(type)" value="${item.type}"/><input type="hidden" name="measurement${index}(units)" value="${item.unitsLabel}"/><c:out value="(${item.unitsLabel})"/>
                  <select name="measurement${index}(samplingProtocol)">
                  <c:forEach items="${optionDescs}" var="optionDesc">
                    <c:choose>
                    <c:when test="${measurementEvent.samplingProtocol eq optionDesc.name}">
                      <option value="${optionDesc.name}" selected="selected"><c:out value="${optionDesc.display}"/></option>
                    </c:when>
                    <c:otherwise>
                      <option value="${optionDesc.name}"><c:out value="${optionDesc.display}"/></option>
                    </c:otherwise>
                    </c:choose>
                  </c:forEach>
                  </select>
              </td>
            </tr>
            <c:set var="index" value="${index + 1}"/>
        </c:forEach>
        <tr>
        <td><input name="${set}" type="submit" value="${set}"/></td>
        </tr>
        </form>
        </table>
        <br/>
 </c:if>
 
<c:if test="${param.edit eq 'metalTags'}">
 <% pageContext.setAttribute("metalTagDescs", Util.findMetalTagDescs(langCode)); %>
 <a name="metalTags"></a>
 <form name="setMetalTags" method="post" action="../EncounterSetTags">
 <input type="hidden" name="tagType" value="metalTags"/>
 <input type="hidden" name="encounter" value="${num}"/>
 <table width="150" border="1" cellpadding="1" cellspacing="0" bordercolor="#000000" bgcolor="#CCCCCC">
     <tr>
      <td align="left" valign="top" class="para"><strong><font color="#990000">
                    <%=encprops.getProperty("resetMetalTags")%></font></strong><br />
      </td>
    </tr>
 <c:forEach items="${metalTagDescs}" var="metalTagDesc">
    <%
      MetalTagDesc metalTagDesc = (MetalTagDesc) pageContext.getAttribute("metalTagDesc");
      MetalTag metalTag = Util.findMetalTag(metalTagDesc, enc);
      if (metalTag == null) {
          metalTag = new MetalTag();
      }
      pageContext.setAttribute("metalTag", metalTag);
    %>
    <tr><td class="formLabel"><c:out value="${metalTagDesc.locationLabel}"/></td></tr>
    <tr><td><input name="metalTag(${metalTagDesc.location})" value="${metalTag.tagNumber}"/></td></tr>
 </c:forEach>
 <tr><td><input name="${set}" type="submit" value="${set}"/></td></tr>
 </table>
 </form>
 <br />
</c:if>
 
<c:if test="${param.edit eq 'acousticTag'}">
 <c:set var="acousticTag" value="${enc.acousticTag}"/>
 <c:if test="${empty acousticTag}">
 <%
   pageContext.setAttribute("acousticTag", new AcousticTag());
 %>
 </c:if>
 <a name="acousticTag"></a>
 <table width="150" border="1" cellpadding="1" cellspacing="0" bordercolor="#000000" bgcolor="#CCCCCC">
    <tr>
      <td align="left" valign="top" class="para"><strong><font color="#990000">
                    <%=encprops.getProperty("resetAcousticTag")%></font></strong><br />
      </td>
    </tr>
    <tr>
      <td>
        <form name="setAcousticTag" method="post" action="../EncounterSetTags">
        <input type="hidden" name="encounter" value="${num}"/>
        <input type="hidden" name="tagType" value="acousticTag"/>
        <input type="hidden" name="id" value="${acousticTag.id}"/>
        <table>
          <tr><td class="formLabel">Serial number:</td></tr>
          <tr><td><input name="acousticTagSerial" value="${acousticTag.serialNumber}"/></td></tr>
          <tr><td class="formLabel">ID:</td></tr>
          <tr><td><input name="acousticTagId" value="${acousticTag.idNumber}"/></td></tr>
          <tr><td><input name="${set}" type="submit" value="${set}"/></td></tr>
        </table>
        </form>
      </td>
    </tr>
 </table>
 <br />
</c:if>
 
<c:if test="${param.edit eq 'satelliteTag'}">
 <a name="satelliteTag"></a>
 <c:set var="satelliteTag" value="${enc.satelliteTag}"/>
 <c:if test="${empty satelliteTag}">
 <%
   pageContext.setAttribute("satelliteTag", new SatelliteTag());
 %>
 </c:if>
 <%
    pageContext.setAttribute("satelliteTagNames", Util.findSatelliteTagNames());
 %>
 <form name="setSatelliteTag" method="post" action="../EncounterSetTags">
 <input type="hidden" name="tagType" value="satelliteTag"/>
 <input type="hidden" name="encounter" value="${num}"/>
 <input type="hidden" name="id" value="${satelliteTag.id}"/>
 <table width="150" border="1" cellpadding="1" cellspacing="0" bordercolor="#000000" bgcolor="#CCCCCC">
      <tr>
      <td align="left" valign="top" class="para"><strong><font color="#990000">
                    <%=encprops.getProperty("resetSatelliteTag")%></font></strong><br />
      </td>
    </tr>
    <tr><td class="formLabel">Name:</td></tr>
    <tr><td>
      <select name="satelliteTagName">
      <c:forEach items="${satelliteTagNames}" var="satelliteTagName">
        <c:choose>
            <c:when test="${satelliteTagName eq satelliteTag.name}">
                <option value="${satelliteTagName}" selected="selected">${satelliteTagName}</option>
            </c:when>
            <c:otherwise>
                <option value="${satelliteTagName}">${satelliteTagName}</option>
            </c:otherwise>
        </c:choose>
      </c:forEach>
      </select>
    </td></tr>
    <tr><td class="formLabel">Serial number:</td></tr>
    <tr><td><input name="satelliteTagSerial" value="${satelliteTag.serialNumber}"/></td></tr>
    <tr><td class="formLabel">Argos PTT Number:</td></tr>
    <tr><td><input name="satelliteTagArgosPttNumber" value="${satelliteTag.argosPttNumber}"/></td></tr>
    <tr><td><input name="${set}" type="submit" value="${set}"/></td></tr>
 </table>
 </form>
 <br />
</c:if>
 
  

		
<%				//reset encounter date
				if((request.getParameter("edit")!=null)&&(request.getParameter("edit").equals("date"))){
		%> <a name="date"></a>
  <table width="150" border="1" cellpadding="1" cellspacing="0" bordercolor="#000000" bgcolor="#CCCCCC">
    <tr>
      <td align="left" valign="top" class="para"><strong><font
        color="#990000"><%=encprops.getProperty("resetEncounterDate")%>:</font></strong></td>
    </tr>
    <tr>
      <td align="left" valign="top">
        <form name="setxencshark" action="../EncounterResetDate" method="post">
          <em><%=encprops.getProperty("day")%>
          </em> <select name="day" id="day">
          <option value="0">?</option>
          <%
            for (int pday = 1; pday < 32; pday++) {
          %>
          <option value="<%=pday%>"><%=pday%>
          </option>
          <%
            }
          %>
        </select><br> <em>&nbsp;<%=encprops.getProperty("month")%>
        </em> <select name="month" id="month">
          <option value="-1">?</option>
          <%
            for (int pmonth = 1; pmonth < 13; pmonth++) {
          %>
          <option value="<%=pmonth%>"><%=pmonth%>
          </option>
          <%
            }
          %>
        </select><br> <em>&nbsp;<%=encprops.getProperty("year")%>
        </em> <select name="year" id="year">
          <option value="-1">?</option>

          <%
            for (int pyear = nowYear; pyear > (nowYear - 50); pyear--) {
          %>
          <option value="<%=pyear%>"><%=pyear%>
          </option>
          <%
            }
          %>
        </select><br> <em>&nbsp;<%=encprops.getProperty("hour")%>
        </em> <select name="hour" id="hour">
          <option value="-1" selected>?</option>
          <option value="6">6 am</option>
          <option value="7">7 am</option>
          <option value="8">8 am</option>
          <option value="9">9 am</option>
          <option value="10">10 am</option>
          <option value="11">11 am</option>
          <option value="12">12 pm</option>
          <option value="13">1 pm</option>
          <option value="14">2 pm</option>
          <option value="15">3 pm</option>
          <option value="16">4 pm</option>
          <option value="17">5 pm</option>
          <option value="18">6 pm</option>
          <option value="19">7 pm</option>
          <option value="20">8 pm</option>
        </select><br> <em>&nbsp;<%=encprops.getProperty("minutes")%>
        </em> <select name="minutes" id="minutes">
          <option value="00" selected>:00</option>
          <option value="15">:15</option>
          <option value="30">:30</option>
          <option value="45">:45</option>
        </select><br> <input name="number" type="hidden" value="<%=num%>"
                             id="number"> <input name="action" type="hidden"
                                                 value="changeEncounterDate"> <input name="AddDate"
                                                                                     type="submit"
                                                                                     id="AddDate"
                                                                                     value="<%=encprops.getProperty("setDate")%>">
        </form>
      </td>
    </tr>
  </table>
<br /> <%
			}
				
				
				//reset water depth
				if((request.getParameter("edit")!=null)&&(request.getParameter("edit").equals("depth"))){
		%> <a name="depth"></a>
  <table width="150" border="1" cellpadding="1" cellspacing="0"
         bordercolor="#000000" bgcolor="#CCCCCC">
    <tr>
      <td align="left" valign="top" class="para"><strong><font
        color="#990000"><%=encprops.getProperty("setDepth")%>:</font></strong></td>
    </tr>
    <tr>
      <td align="left" valign="top">
        <form name="setencdepth" action="../EncounterSetMaximumDepth" method="post">
          <input name="depth" type="text" id="depth" size="10"> <%=encprops.getProperty("meters")%>
          <input name="lengthUnits" type="hidden"
                 id="lengthUnits" value="Meters"> <input name="number"
                                                         type="hidden" value="<%=num%>" id="number">
          <input
            name="action" type="hidden" value="setEncounterDepth"> <input
          name="AddDepth" type="submit" id="AddDepth" value="<%=encprops.getProperty("setDepth")%>">
        </form>
      </td>
    </tr>
  </table>
<br /> <%
			}
			
			
				//reset elevation
		if((request.getParameter("edit")!=null)&&(request.getParameter("edit").equals("elevation"))){
		%> <a name="elevation"></a>
  <table width="150" border="1" cellpadding="1" cellspacing="0"
         bordercolor="#000000" bgcolor="#CCCCCC">
    <tr>
      <td align="left" valign="top" class="para"><strong><font
        color="#990000"><%=encprops.getProperty("setElevation")%>:</font></strong></td>
    </tr>
    <tr>
      <td align="left" valign="top">
        <form name="setencelev" action="../EncounterSetMaximumElevation" method="post">
          <input name="elevation" type="text" id="elevation" size="10"> Meters <input
          name="lengthUnits" type="hidden" id="lengthUnits" value="Meters">
          <input name="number" type="hidden" value="<%=num%>" id="number">
          <input name="action" type="hidden" value="setEncounterElevation">
          <input name="AddElev" type="submit" id="AddElev"
                 value="<%=encprops.getProperty("setElevation")%>">
        </form>
      </td>
    </tr>
  </table>
<br/> <%
			}

		if((request.getParameter("edit")!=null)&&(request.getParameter("edit").equals("user"))){
		%> <a name="user"></a>
  <table width="150" border="1" cellpadding="1" cellspacing="0" bordercolor="#000000"
         bgcolor="#CCCCCC">
    <tr>
      <td align="left" valign="top" class="para"><font
        color="#990000"><img align="absmiddle"
                             src="../images/Crystal_Clear_app_Login_Manager.gif"/>
        <strong><%=encprops.getProperty("assignUser")%>:</strong></font></td>
    </tr>
    <tr>
      <td align="left" valign="top">
        <form name="asetSubmID" action="../EncounterSetSubmitterID"
              method="post"><input name="submitter" type="text" size="10"
                                   maxlength="50"> <input name="number" type="hidden"
                                                          value=<%=num%>> <input name="Assign"
                                                                                 type="submit"
                                                                                 id="Assign"
                                                                                 value="<%=encprops.getProperty("assign")%>">
        </form>
      </td>
    </tr>
  </table>
<br/> <%
		}

	//reset scarring
	if((request.getParameter("edit")!=null)&&(request.getParameter("edit").equals("scar"))){
	%> <a name="scar"></a>
  <table width="150" border="1" cellpadding="1" cellspacing="0"
         bordercolor="#000000" bgcolor="#CCCCCC">
    <tr>
      <td align="left" valign="top" class="para"><strong><font
        color="#990000"><%=encprops.getProperty("editScarring")%>:</font></strong></td>
    </tr>
    <tr>
      <td align="left" valign="top">
        <form name="setencsize" action="../EncounterSetScarring" method="post">
          <textarea name="scars" size="15"><%=enc.getDistinguishingScar()%>
          </textarea>
          <input name="number" type="hidden" value="<%=num%>" id="number">
          <input name="action" type="hidden" value="setScarring"> <input
          name="Add" type="submit" id="scar" value="<%=encprops.getProperty("resetScarring")%>">
        </form>
      </td>
    </tr>
  </table>
<br/> <%
			}

		//kick off a scan
				if (((enc.getNumSpots()>0)||(enc.getNumRightSpots()>0))&&request.getParameter("isOwner").equals("true")) {
		%> <br/>
<table width="150" border="1" cellpadding="1" cellspacing="0"
       bgcolor="#CCCCCC">
  <tr>
    <td align="left" valign="top">
      <p class="para"><font color="#990000"><strong><img
        align="absmiddle" src="../images/Crystal_Clear_action_find.gif"/>
        Find Pattern Match <a
          href="<%=CommonConfiguration.getWikiLocation()%>sharkgrid"
          target="_blank"><img src="../images/information_icon_svg.gif"
                               alt="Help" border="0" align="absmiddle"></a><br/>
      </strong> Scan entire database on the <a href="http://www.sharkgrid.org">sharkGrid</a>
        using the <a
          href="http://www.blackwell-synergy.com/doi/pdf/10.1111/j.1365-2664.2005.01117.x">Modified
          Groth</a> and <a
          href="http://www.blackwell-synergy.com/doi/abs/10.1111/j.1365-2664.2006.01273.x?journalCode=jpe">I3S</a>
        algorithms</font>

      <div id="formDiv">
        <form name="formSharkGrid" id="formSharkGrid" method="post"
              action="../ScanTaskHandler"><input name="action" type="hidden"
                                                 id="action" value="addTask"> <input
          name="encounterNumber"
          type="hidden" value=<%=num%>>

          <table width="200">
            <tr>
              <%
                if ((enc.getSpots() != null) && (enc.getSpots().size() > 0)) {
              %>
              <td class="para"><label> <input name="rightSide"
                                              type="radio" value="false" checked> left-side</label>
              </td>
              <%
                }
              %>
              <%
                if ((enc.getRightSpots() != null) && (enc.getRightSpots().size() > 0) && (enc.getSpots() != null) && (enc.getSpots().size() == 0)) {
              %>
              <td class="para"><label> <input type="radio"
                                              name="rightSide" value="true" checked>
                right-side</label></td>
              <%
              } else if ((enc.getRightSpots() != null) && (enc.getRightSpots().size() > 0)) {
              %>
              <td class="para"><label> <input type="radio"
                                              name="rightSide" value="true"> right-side</label></td>
              <%
                }
              %>
            </tr>
          </table>

          <input name="writeThis" type="hidden" id="writeThis" value="true">
          <br/> <input name="scan" type="submit" id="scan"
                       value="Start Scan"
                       onclick="submitForm(document.getElementById('formSharkGrid'))">
          <input name="cutoff" type="hidden" value="0.02"></form>
      </p>
</div>
</td>
</tr>
</table>
<br/> <!--
			<%}
			
			if (((enc.getNumSpots()>0)||(enc.getNumRightSpots()>0))&&request.getParameter("isOwner").equals("true")) {%>
			<table width="150" border="1" cellpadding="1" cellspacing="0" bgcolor="#CCCCCC">
        		<tr>
          			<td align="left" valign="top">
		  				<form name="formSingleScan" method="get" action="appletScan.jsp">
              				<p class="para"><font color="#990000"><strong>Groth:</strong> Scan against one other encounter</font>
						    <table width="200">
							  <tr>
							    <%if(enc.getNumSpots()>0) {%>
								<td width="93" class="para"><label>
							      <input name="rightSide" type="radio" value="false" checked>
							      left-side</label></td>
						    <%}%>
							<%if(enc.getNumRightSpots()>0) {%>
							    <td width="95" class="para"><label>
							      <input type="radio" name="rightSide" value="true">
							      right-side</label></td>
								  <%}%>
						    </tr>
							
						  </table>
							
   						      <input name="singleComparison" type="text" size="15" maxlength="50">
						      <input name="scan" type="submit" id="scan" value="Scan">
  						      <input name="number" type="hidden" value=<%=num%>>
  						      <input name="R" type="hidden" value="8">
  						      <input name="Sizelim" type="hidden" value="0.85">
								<input name="cutoff" type="hidden" value="0.02">
  						      <input name="epsilon" type="hidden" value="0.01">
						      <input name="C" type="hidden" value="0.99">
						      <input name="maxTriangleRotation" type="hidden" value="10">
							  
				      </form>
		  			</td>
				</tr>
			</table><br />
			--> <%
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
	  	  
	  	  if((request.getParameter("edit")!=null)&&(request.getParameter("edit").equals("rmSpots"))){
	  %> <a name="rmSpots"></a>
  <table border="1" cellpadding="1" cellspacing="0"
         bordercolor="#000000" bgcolor="#CCCCCC">
    <tr>
      <td align="left" valign="top" class="para"><strong><font
        color="#990000">Remove spot data:</font></strong></td>
    </tr>
    <tr>
      <td align="left" valign="top">
        <center>
          <form name="removeSpots" method="post"
                action="../EncounterRemoveSpots">
            <table width="200">
              <tr>
                <%
                  if (enc.getSpots().size() > 0) {
                %>
                <td><label> <input name="rightSide" type="radio"
                                   value="false"> left-side</label></td>
                <%
                  }
                  if (enc.getRightSpots().size() > 0) {
                %>
                <td><label> <input type="radio" name="rightSide"
                                   value="true"> right-side</label></td>
                <%
                  }
                %>
              </tr>
            </table>
            <input name="number" type="hidden" value=<%=num%>> <input
            name="action" type="hidden" value="removeSpots"> <input
            name="Remove3" type="submit" id="Remove3" value="Remove"></form>
        </center>
      </td>
    </tr>
  </table>
<br/> <%
	  	}
	  	  if((request.getParameter("edit")!=null)&&(request.getParameter("edit").equals("spotImage"))){
	  %> <a name="spotImage"></a>
  <table border="1" cellpadding="1" cellspacing="0"
         bordercolor="#000000" bgcolor="#CCCCCC">
    <tr>
      <td class="para">
        <form action="../EncounterAddSpotFile" method="post"
              enctype="multipart/form-data" name="addSpotsFile"><input
          name="action" type="hidden" value="fileadder" id="action">
          <input name="number" type="hidden" value="<%=num%>" id="shark">
          <font color="#990000"><strong><img align="absmiddle"
                                             src="../images/upload_small.gif"/></strong> <strong>Set
            spot
            image file:</strong></font><br/> <label><input name="rightSide"
                                                           type="radio" value="false">
            left</label><br/> <label><input
            name="rightSide" type="radio" value="true"> right</label><br/>
          <br/> <input name="file2add" type="file" size="15"><br/>
          <input name="addtlFile" type="submit" id="addtlFile"
                 value="Upload spot image"></form>
      </td>
    </tr>
  </table>
<br/> <%
	  	  	}
			if(CommonConfiguration.isCatalogEditable()){
	  	  %>
<table border="1" cellpadding="2" cellspacing="0" bordercolor="#000000" bgcolor="#CCCCCC">
  <tr>
    <td align="left" valign="top" class="para">
      <font color="#990000"><img
        align="absmiddle" src="../images/thumbnail_image.gif"/></font>
      <strong><%=encprops.getProperty("resetThumbnail")%>
      </strong>&nbsp;</font></td>
  </tr>
  <tr>
    <td align="left">
      <form action="../resetThumbnail.jsp" method="get" enctype="multipart/form-data"
            name="resetThumbnail">
        <input name="number" type="hidden" value="<%=num%>" id="numreset"><br/>
        <%=encprops.getProperty("useImage")%>: <select name="imageNum">
        <%
          for (int rmi2 = 1; rmi2 <= numImages; rmi2++) {
        %>
        <option value="<%=rmi2%>"><%=rmi2%>
        </option>
        <%
          }
        %>
      </select><br/>
        <input name="resetSubmit" type="submit" id="resetSubmit"
               value="<%=encprops.getProperty("resetThumbnail")%>"></form>
    </td>
  </tr>
</table>
<br/> <a name="tapirlink"></a>
  <table width="175" border="1" cellpadding="1" cellspacing="0"
         bordercolor="#000000" bgcolor="#CECFCE">
    <tr>
      <td height="30" class="para"><font color="#990000">&nbsp;<img
        align="absmiddle" src="../images/interop.gif"/> <strong>TapirLink?</strong>
        <a href="<%=CommonConfiguration.getWikiLocation()%>tapirlink"
           target="_blank"><img src="../images/information_icon_svg.gif"
                                alt="Help" border="0" align="absmiddle"/></a></font></td>
    </tr>
    <tr>
      <td height="30" class="para">&nbsp; <%=encprops.getProperty("currentValue")%>
        : <%=enc.getOKExposeViaTapirLink()%>
      </td>
    </tr>
    <tr>
      <td>
        <form name="approve_form" method="post"
              action="../EncounterSetTapirLinkExposure"><input name="action"
                                                               type="hidden" id="action"
                                                               value="tapirLinkExpose"> <input
          name="number" type="hidden" value=<%=num%>> <input
          name="approve" type="submit" id="approve" value="<%=encprops.getProperty("change")%>">
        </form>
      </td>
    </tr>
  </table>
<br/> <%
		}
	  	//end isOwner permissions
	  	  }
	  	  
	  	  	  //end else if-edit not null
	  	  }
	  	  
	  	  //add e-mail for tracking
	  	 
	  %> <!--<br /><table width="150" border="1" cellpadding="1" cellspacing="0" bordercolor="#000000" bgcolor="#CCCCCC">
      	<tr>
        	<td align="left" valign="top" class="para"><font color="#990000">Track data changes to this encounter via email address:</font></td>
        </tr>
        <tr>
        	<td align="left" valign="top"> 
           		<form name="trackShark" method="post" action="../TrackIt">
		  			<input name="number" type="hidden" value=<%=num%>>
              		<input name="email" type="text" id="email" size="20" maxlength="50">
              		<input name="Track" type="submit" id="Track" value="Track">
            	</form>
			</td>
        </tr>
      </table><br />
	        <table width="150" border="1" cellpadding="1" cellspacing="0" bordercolor="#000000" bgcolor="#CCCCCC">
      	<tr>
        	<td align="left" valign="top" class="para"><font color="#990000">Remove email address from tracking:</font></td>
        </tr>
        <tr>
        	<td align="left" valign="top"> 
           		<form name="trackShark" method="post" action="../DontTrack">
		  			<input name="number" type="hidden" value=<%=num%>>
              		<input name="email" type="text" id="email" size="20" maxlength="50">
              		<input name="Remove" type="submit" id="RemoveTrack" value="Remove">
            	</form>
			</td>
        </tr>
      </table><br />-->

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