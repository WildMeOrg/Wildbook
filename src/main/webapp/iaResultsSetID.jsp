<%@ page contentType="application/json; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.*,org.ecocean.ia.*,java.util.*,
java.io.*,java.util.*, java.io.FileInputStream, 

org.datanucleus.api.rest.RESTUtils,
org.datanucleus.ExecutionContext,
java.lang.reflect.Method,
org.datanucleus.api.jdo.JDOPersistenceManager,
org.ecocean.media.*,
org.ecocean.cache.*,
java.util.zip.GZIPOutputStream,org.ecocean.servlet.importer.*,org.json.JSONObject,
java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>
<%!
//try to see if encounter was part of ImportTask so we can mark complete
//  note: this sets *all annots* on that encounter!  clever or stupid?  tbd!
private static void setImportTaskComplete(Shepherd myShepherd, Encounter enc) {
    if ((enc == null) || (enc.numAnnotations() < 1)) return;
    String jdoql = "SELECT FROM org.ecocean.servlet.importer.ImportTask WHERE encounters.contains(enc) && enc.catalogNumber =='" + enc.getCatalogNumber() + "'";
    Query query = myShepherd.getPM().newQuery(jdoql);
    query.setOrdering("created desc");
    List results = (List)query.execute();
    ImportTask itask = null;
    if (!Util.collectionIsEmptyOrNull(results)) itask = (ImportTask)results.get(0);
    query.closeAll();
	System.out.println("setImportTaskComplete(" + enc + ") => " + itask);
    if (itask == null) return;
    String svKey = "rapid_completed_" + itask.getId();
    myShepherd.beginDBTransaction();
    JSONObject m = SystemValue.getJSONObject(myShepherd, svKey);
    if (m == null) m = new JSONObject();
    for (Annotation ann : enc.getAnnotations()) {
        m.put(ann.getId(), true);
		System.out.println("setImportTaskComplete() setting true for annot " + ann.getId());
    }
    SystemValue.set(myShepherd, svKey, m);
    myShepherd.commitDBTransaction();
}


%>
<%

String context = ServletUtilities.getContext(request);
String langCode = ServletUtilities.getLanguageCode(request);

//confirm no match and set next automatic name
JSONObject res = new JSONObject("{\"success\": false}");
boolean useLocation = Util.requestParameterSet(request.getParameter("useLocation"));


if ((request.getParameter("taskId") != null) && (request.getParameter("number") != null) && ((request.getParameter("individualID") != null) || useLocation)) {
	
	String taskId = request.getParameter("taskId");
	res.put("encounterId", request.getParameter("number"));
	res.put("encounterId2", request.getParameter("enc2"));
	res.put("individualId", request.getParameter("individualID"));
        res.put("useLocation", useLocation);
	res.put("taskId", taskId);
	String projectId = null;
	if (Util.stringExists(request.getParameter("projectId"))) {
		res.put("projectId", request.getParameter("projectId"));
		projectId = request.getParameter("projectId");
	}

	Shepherd myShepherd = new Shepherd(request);
	myShepherd.setAction("iaResultsSetID.jsp");
	myShepherd.beginDBTransaction();

	Encounter enc = myShepherd.getEncounter(request.getParameter("number"));
	if (enc == null) {
		res.put("error", "no such encounter: " + request.getParameter("number"));
		out.println(res.toString());
		myShepherd.rollbackDBTransaction();
		myShepherd.closeDBTransaction();
		return;
	}
	else if(!ServletUtilities.isUserAuthorizedForEncounter(enc, request)){
		res.put("error", "User unauthorized for encounter: " + request.getParameter("number"));
		out.println(res.toString());
		myShepherd.rollbackDBTransaction();
		myShepherd.closeDBTransaction();
		return;
	} else if (useLocation && !Util.stringExists(enc.getLocationID())) {
		res.put("error", "Empty locationID with useLocation=true for encounter: " + request.getParameter("number"));
		out.println(res.toString());
		myShepherd.rollbackDBTransaction();
		myShepherd.closeDBTransaction();
		return;
	}

	Encounter enc2 = null;
	if (request.getParameter("enc2") != null) {
		enc2 = myShepherd.getEncounter(request.getParameter("enc2"));
		myShepherd.getPM().refresh(enc2);

		if (enc2 == null) {
			res.put("error", "no such encounter: " + request.getParameter("enc2"));
			out.println(res.toString());
			myShepherd.rollbackDBTransaction();
			myShepherd.closeDBTransaction();
			return;
		}
		else if(!ServletUtilities.isUserAuthorizedForEncounter(enc2, request)){
			res.put("error", "User unauthorized for encounter: " + request.getParameter("number"));
			out.println(res.toString());
			myShepherd.rollbackDBTransaction();
			myShepherd.closeDBTransaction();
			return;
		}
	}
	/* now, making an assumption here (and the UI does as well):
	   basically, we only allow a NEW INDIVIDUAL when both encounters are unnamed;
	   otherwise, we are assuming we are naming one based on the other.  thus, we MUST
	   use an *existing* indiv in those cases (but allow a new one in the other)

            addendum via WB-1216: the NEW INDIVIDUAL case can now allow for useLocation=true option.
	*/

	// once you assign an id to one, it will still ask for input on another.

	String indyUUID = null;
	MarkedIndividual indiv = null;
	MarkedIndividual indiv2 = null;
	String individualID = null;
	try {

		individualID = request.getParameter("individualID");
		if (individualID!=null) individualID = individualID.trim();
		// from query enc
		indiv = myShepherd.getMarkedIndividual(enc);
		// from target enc
		indiv2 = myShepherd.getMarkedIndividual(enc2);
		indyUUID = request.getParameter("newIndividualUUID");
		//should take care of getting an indy stashed in the URL params
		if (indiv==null) {
			indiv = myShepherd.getMarkedIndividualQuiet(indyUUID);
		}

		System.out.println("got ID: "+indyUUID+" from header set previous match");

		System.out.println("did you get indiv? "+indiv+" did you get indiv2? "+indiv2);

		// if both have an id, throw an error. any deecision to override would be arbitrary
		// should get to MERGE option instead of getting here anyway
		if (indiv!=null&&indiv2!=null) {


			// need nuance here.. if both individuals are present but there is not a project ID allow set

			res.put("error", "Both encounters already have an ID. You must remove one or reassign from the Encounter page.");
			out.println(res.toString());
			myShepherd.rollbackDBTransaction();
			myShepherd.closeDBTransaction();
		}
	} catch (Exception e) {
		e.printStackTrace();
	}

	// allow flow either way if one or the other has an ID
	if ((indiv == null || indiv2 == null) && (enc != null) && (enc2 != null)) {

		System.out.println("indiv OR indiv2 is null, and two viable enc have been selected!");

		try {


			//enc.setState("approved");
			//enc2.setState("approved");

			// neither have an individual
			if (indiv==null&&indiv2==null) {
                                //note that useLocation flavor has slight race condition possible here...
                                // might be better to have way to *create* indiv with new loc-based name
                                if (useLocation) {
                                    String locPrefix = LocationID.getPrefixForLocationID(enc.getLocationID(), null);
									int prefixPadding = LocationID.getPrefixDigitPaddingForLocationID(enc.getLocationID(), null);
                                    individualID = MarkedIndividual.nextNameByPrefix(locPrefix, prefixPadding);
                                }
				if (Util.stringExists(individualID)) {
					System.out.println("CASE 1: both indy null");
					if (Util.isUUID(individualID)) {
						indiv = myShepherd.getMarkedIndividual(individualID);
					}
					if (indiv==null) {
						indiv = new MarkedIndividual(individualID, enc);
					}



					myShepherd.getPM().makePersistent(indiv);
					//check for project to add new name with prefix
					if (projectId!=null) {
						Project project = myShepherd.getProjectByProjectIdPrefix(projectId);
						if (project!=null&&project.getNextIncrementalIndividualId().equals(individualID)) {
							project.getNextIncrementalIndividualIdAndAdvance();
							myShepherd.updateDBTransaction();
						}

						indiv.addNameByKey(projectId, individualID);
						res.put("newIncrementalId", indiv.getDisplayName(projectId));
					}
					myShepherd.updateDBTransaction();
					//res.put("newIndividualUUID", indiv.getId());
					res.put("individualName", indiv.getDisplayName(request, myShepherd));
					res.put("individualId", indiv.getId());
					enc.setIndividual(indiv);
					enc2.setIndividual(indiv);
					indiv.addEncounter(enc2);

					myShepherd.updateDBTransaction();
                                        setImportTaskComplete(myShepherd, enc);
                                        setImportTaskComplete(myShepherd, enc2);
                    indiv.refreshNamesCache();

                    IndividualAddEncounter.executeEmails(myShepherd, request,indiv,true, enc2, context, langCode);
                    IndividualAddEncounter.executeEmails(myShepherd, request,indiv,true, enc, context, langCode);


				} else {
					res.put("error", "Please enter a new Individual ID for both encounters.");
				}
			}

			// query enc has indy, or already stashed in URL params
			if (indiv!=null&&indiv2==null) {
				System.out.println("CASE 2: query enc indy is null");
				enc2.setIndividual(indiv);
				indiv.addEncounter(enc2);
				res.put("individualName", indiv.getDisplayName(request, myShepherd));
				myShepherd.updateDBTransaction();
				IndividualAddEncounter.executeEmails(myShepherd, request,indiv,false, enc2, context, langCode);
                                setImportTaskComplete(myShepherd, enc2);
			}

			// target enc has indy
			if (indiv==null&&indiv2!=null) {
				System.out.println("CASE 3: target enc indy is null");
				enc.setIndividual(indiv2);
				indiv2.addEncounter(enc);
				res.put("individualName", indiv2.getDisplayName(request, myShepherd));
				myShepherd.updateDBTransaction();

				IndividualAddEncounter.executeEmails(myShepherd, request,indiv2,false, enc, context, langCode);
                                setImportTaskComplete(myShepherd, enc);
			}


			String matchMsg = enc.getMatchedBy();
			if ((matchMsg == null) || matchMsg.equals("Unknown")) matchMsg = "";
			matchMsg += "<p>match approved via <i>iaResults</i> (by <i>" + AccessControl.simpleUserString(request) + "</i>) " + ((taskId == null) ? "<i>unknown Task ID</i>" : "Task <b>" + taskId + "</b>") + "</p>";
			enc.setMatchedBy(matchMsg);
			enc2.setMatchedBy(matchMsg);

			if (res.optString("error", null) == null) res.put("success", true);

		} catch (Exception e) {
			//enc.setState("unapproved");
			//enc2.setState("unapproved");
			e.printStackTrace();
			res.put("error", "Please enter a different Individual ID.");
		}

		//indiv.addComment(????)
		out.println(res.toString());
		myShepherd.rollbackDBTransaction();
		myShepherd.closeDBTransaction();
		return;
	}

	if (indiv == null && indiv2 == null) {
		res.put("error", "No valid record could be found or created for name: " + individualID);
		out.println(res.toString());
		myShepherd.rollbackDBTransaction();
		myShepherd.closeDBTransaction();
		return;
	}

	if (myShepherd.getPM().currentTransaction().isActive()) {
		myShepherd.commitDBTransaction();
		myShepherd.closeDBTransaction();
	}

	res.put("error", "Unknown error setting individual " + individualID);
	out.println(res.toString());
	return;
}




%>