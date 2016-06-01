<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/plain; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>

<%

String context="context0";
context=ServletUtilities.getContext(request);

Shepherd myShepherd=new Shepherd(context);

/*
       Table "public.MARKEDINDIVIDUAL_ENCOUNTERS"
      Column       |          Type          | Modifiers 
-------------------+------------------------+-----------
 INDIVIDUALID_OID  | character varying(100) | not null
 CATALOGNUMBER_EID | character varying(100) | 
 IDX               | integer                | not null
*/

myShepherd.beginDBTransaction();

Iterator all = myShepherd.getAllMarkedIndividuals();
int count = 0;
while (all.hasNext()) {
	MarkedIndividual ind = (MarkedIndividual)all.next();
	String id = ind.getIndividualID();
	if (Util.isUUID(id)) {
		String msg = "migrateMarkedIndividualUUID: " + id + " is already a UUID; skipping.";
		System.out.println(msg);
		out.println(msg);
		continue;
	}

	String altId = ind.getAlternateID();
	if (altId.equals("None")) altId = null;
	if (altId == null) {
		altId = id;
	} else {
		altId = id + ", " + altId;
	}

	String newId = Util.generateUUID();

	if (ind.getEncounters() != null) {
		for (Object obj: ind.getEncounters()) {
			Encounter enc = (Encounter)obj;
			enc.setIndividualID(newId);
		}
	}
	ind.setIndividualID(newId);
	myShepherd.getPM().makePersistent(ind);

	String msg = "migrateMarkedIndividualUUID: (" + count + ") " + id + " -> " + newId + " [" + altId + "]";
	System.out.println(msg);
	out.println(msg);
	count++;
	if (count > 10) break;
}

myShepherd.commitDBTransaction();

%>

