<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>

<%

String context="context0";
context=ServletUtilities.getContext(request);

Shepherd myShepherd=new Shepherd(context);


%>

<html>
<head>
<title>Check SDRP for Merge Duplicates</title>

</head>


<body>
<ol>
<%

myShepherd.beginDBTransaction();

String filter="SELECT FROM org.ecocean.MarkedIndividual WHERE encounters.contains(enc) && ( enc.submitterID == \"SDRP\" )  VARIABLES org.ecocean.Encounter enc";
Query q=myShepherd.getPM().newQuery(filter);
Collection c = (Collection) (q.execute());
ArrayList<MarkedIndividual> indies=new ArrayList<MarkedIndividual>(c);
q.closeAll();

HashMap<String, MarkedIndividual> newMap=new HashMap<String, MarkedIndividual>();


try {

	int numIndies=indies.size();

	
	for(int i=0;i<numIndies;i++){
		MarkedIndividual indy=indies.get(i);
		String speciesString="";
		boolean hasOtherSpecies=false;
		List<Encounter> encs=indy.getEncounterList();
		for(Encounter enc:encs){
			if(enc.getGenus()!=null && !enc.getGenus().equals("Tursiops")){
				hasOtherSpecies=true;
				speciesString+=enc.getGenus();
			}
		}
		if(hasOtherSpecies){

			//OKAY, we know we have an innappropriately merged, multi-species individual, and we need to fix it.
			for(Encounter enc:encs){
				if(enc.getSubmitterID()!=null && enc.getSubmitterID().equals("SDRP")){
					
					//so this is a candidate Encounter to split off
					indy.removeEncounter(enc);
					enc.setIndividual(null);
					myShepherd.updateDBTransaction();
					
					//OK, if we haven't created the tursiops copy, do it now
					if(!newMap.containsKey(indy.getDefaultName())){
						
						MarkedIndividual newIndy=new MarkedIndividual(enc);
						if(indy.getDefaultName()!=null)newIndy.addName(indy.getDefaultName());
						if(indy.getNickName()!=null)newIndy.addNameByKey("Nickname", indy.getNickName());
						myShepherd.getPM().makePersistent(newIndy);
						myShepherd.updateDBTransaction();
						enc.setIndividual(newIndy);
						myShepherd.updateDBTransaction();
						newMap.put(indy.getDefaultName(), newIndy);
						
					}
					//otherwise, grab it from the map and add the encounter
					else{
						MarkedIndividual newIndy=newMap.get(indy.getDefaultName());
						newIndy.addEncounter(enc);
						enc.setIndividual(newIndy);
						myShepherd.updateDBTransaction();
					}
					
					
				}
			}
			
			
			
		}
	}
}
catch(Exception e){
	%>
	Error!
	<%
	e.printStackTrace();
}
finally{
	myShepherd.rollbackDBTransaction();
	myShepherd.closeDBTransaction();

}





%>

</ol>

</body>
</html>
