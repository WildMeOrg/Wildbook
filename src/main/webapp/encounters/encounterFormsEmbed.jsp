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