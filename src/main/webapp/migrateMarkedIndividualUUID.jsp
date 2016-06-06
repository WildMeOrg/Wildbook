<%@ page contentType="text/plain; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>

BEGIN;
ALTER TABLE ONLY "MARKEDINDIVIDUAL_ENCOUNTERS" DROP CONSTRAINT "MARKEDINDIVIDUAL_ENCOUNTERS_FK1";

<%

String context="context0";
context=ServletUtilities.getContext(request);

Shepherd myShepherd=new Shepherd(context);



Iterator all = myShepherd.getAllMarkedIndividuals();
int count = 0;
while (all.hasNext()) {
	MarkedIndividual ind = (MarkedIndividual)all.next();
	String id = ind.getIndividualID();
	if (Util.isUUID(id)) {
		String msg = "-- migrateMarkedIndividualUUID: " + id + " is already a UUID; skipping.";
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

	String sql = "UPDATE \"MARKEDINDIVIDUAL_ENCOUNTERS\" SET \"INDIVIDUALID_OID\"='" + newId + "' WHERE \"INDIVIDUALID_OID\" = '" + id + "';";
	sql += "\nUPDATE \"MARKEDINDIVIDUAL\" SET \"INDIVIDUALID\"='" + newId + "', \"ALTERNATEID\"='" + altId + "' WHERE \"INDIVIDUALID\" = '" + id + "';";
	sql += "\nUPDATE \"ENCOUNTER\" SET \"INDIVIDUALID\"='" + newId + "' WHERE \"INDIVIDUALID\" = '" + id + "';";
	out.println(sql);


	String msg = "-- migrateMarkedIndividualUUID: (" + count + ") " + id + " -> " + newId + " [" + altId + "]";
	System.out.println(msg);
	out.println(msg);

	count++;
	if (count > 10) break;
}

myShepherd.closeDBTransaction();

%>

ALTER TABLE ONLY "MARKEDINDIVIDUAL_ENCOUNTERS"
ADD CONSTRAINT "MARKEDINDIVIDUAL_ENCOUNTERS_FK1" FOREIGN KEY ("INDIVIDUALID_OID") REFERENCES
"MARKEDINDIVIDUAL"("INDIVIDUALID") ON UPDATE RESTRICT ON DELETE RESTRICT;

COMMIT;

