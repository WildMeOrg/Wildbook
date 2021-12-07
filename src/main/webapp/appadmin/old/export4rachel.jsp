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

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java"
         import="com.drew.imaging.jpeg.JpegMetadataReader,com.drew.metadata.Directory, com.drew.metadata.Metadata,com.drew.metadata.Tag,org.ecocean.*,org.ecocean.servlet.ServletUtilities,java.io.File, java.util.*" %>



<html>
<head />

 

<body>

<%

Shepherd myShepherd=new Shepherd();
myShepherd.beginDBTransaction();
int numSharks=0;

Iterator allSharks = myShepherd.getAllMarkedIndividuals();
while(allSharks.hasNext()){

	MarkedIndividual shark=(MarkedIndividual)allSharks.next();
	if((shark.wasSightedInLocationCode("2f")||shark.wasSightedInLocationCode("2")||shark.wasSightedInLocationCode("2a")||shark.wasSightedInLocationCode("2b")||shark.wasSightedInLocationCode("2c")||shark.wasSightedInLocationCode("2d")||shark.wasSightedInLocationCode("2e"))&&(shark.participatesInTheseLocationIDs().size()>1)){
		numSharks++;
		
		Encounter[] encounters=shark.getDateSortedEncounters(true);
		for(int num=0;num<encounters.length;num++){
			//String firstCol=" ";
			//if(num==0){firstCol=shark.getName();}
			
			
			%>
			<%=shark.getName()%> <%=encounters[num].getYear()%> <%=encounters[num].getLocationID()%><br />
			
			<%
		
		}
	
	
	}

}

%>

<br />
<br />
Number sharks sighted in multiple locations: <%=numSharks%>
<%

myShepherd.rollbackDBTransaction();
myShepherd.closeDBTransaction();


%>


</body>
</html>

