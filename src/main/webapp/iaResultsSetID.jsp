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
java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%><%@ page import="org.ecocean.shepherd.core.Shepherd"%>
<%!
//try to see if encounter was part of ImportTask so we can mark complete
//  note: this sets *all annots* on that encounter!  clever or stupid?  tbd!
private static void setImportTaskComplete(Shepherd myShepherd, Encounter enc) {
	try{
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
	    myShepherd.updateDBTransaction();
	}
	catch(Exception e){e.printStackTrace();}
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
        String[] otherEncIds = request.getParameterValues("encOther");
	res.put("encounterId", request.getParameter("number"));
	res.put("encounterOther", otherEncIds);
	res.put("individualId", request.getParameter("individualID"));
        res.put("useLocation", useLocation);
	res.put("taskId", taskId);
	String projectId = null;
	if (Util.stringExists(request.getParameter("projectId"))) {
		res.put("projectId", request.getParameter("projectId"));
		projectId = request.getParameter("projectId");
	}

        System.out.println("iaResultsSetID: INITIALIZED res=" + res);
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
	else if(!ServletUtilities.isUserAuthorizedForEncounter(enc, request,myShepherd)){
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

	//res.put("encounterOther", otherEncIds);
	List<Encounter> otherEncs = new ArrayList<Encounter>();
        List<MarkedIndividual> otherIndivs = new ArrayList<MarkedIndividual>();
        if (otherEncIds != null) for (String oeId : otherEncIds) {
		Encounter oenc = myShepherd.getEncounter(oeId);
		myShepherd.getPM().refresh(oenc);

		if (oenc == null) {
			res.put("error", "no such encounter: " + oeId);
			out.println(res.toString());
			myShepherd.rollbackDBTransaction();
			myShepherd.closeDBTransaction();
			return;
		}
		else if(!ServletUtilities.isUserAuthorizedForEncounter(oenc, request,myShepherd)){
			res.put("error", "User unauthorized for encounter: " + oeId);
			out.println(res.toString());
			myShepherd.rollbackDBTransaction();
			myShepherd.closeDBTransaction();
			return;
		}
                System.out.println("iaResultsSetID: adding " + oenc + " to otherEncs");
                otherEncs.add(oenc);
                MarkedIndividual oindiv = oenc.getIndividual();
                if (oindiv != null) otherIndivs.add(oindiv);
	}
	/* now, making an assumption here (and the UI does as well):
	   basically, we only allow a NEW INDIVIDUAL when all encounters are unnamed;
	   otherwise, we are assuming we are naming one based on the other.  thus, we MUST
	   use an *existing* indiv in those cases (but allow a new one in the other)

            addendum via WB-1216: the NEW INDIVIDUAL case can now allow for useLocation=true option.
	*/

	// once you assign an id to one, it will still ask for input on another.

	String indyUUID = null;
	MarkedIndividual indiv = null;
	//MarkedIndividual indiv2 = null;
	String individualID = null;
        Set<MarkedIndividual> allEncIndivs = new HashSet<MarkedIndividual>();
	try {

		individualID = request.getParameter("individualID");
		if (individualID!=null) individualID = individualID.trim();
		// from query enc
		indiv = myShepherd.getMarkedIndividual(enc);
                if (indiv != null) allEncIndivs.add(indiv);
		// from target enc
		//indiv2 = myShepherd.getMarkedIndividual(enc2);
                for (Encounter oenc : otherEncs) {
		    MarkedIndividual oindiv = myShepherd.getMarkedIndividual(oenc);
                    if (oindiv != null) allEncIndivs.add(oindiv);
                }
		
		//if target enc2 is null and therefore indiv2 is null, use individualID
		//if(indiv2==null)indiv2=myShepherd.getMarkedIndividual(individualID);
		
		indyUUID = request.getParameter("newIndividualUUID");
		//should take care of getting an indy stashed in the URL params
		if (indiv==null) {
			indiv = myShepherd.getMarkedIndividualQuiet(indyUUID);
		}

		System.out.println("got ID: "+indyUUID+" from header set previous match");

		//System.out.println("did you get indiv? "+indiv+" did you get indiv2? "+indiv2);

		// if both have an id, throw an error. any deecision to override would be arbitrary
		// should get to MERGE option instead of getting here anyway
		if (allEncIndivs.size() > 1) {
			// need nuance here.. if both individuals are present but there is not a project ID allow set
			res.put("error", "All/Some encounters already have an ID. You must remove one or reassign from the Encounter page.");
			out.println(res.toString());
			myShepherd.rollbackDBTransaction();
			myShepherd.closeDBTransaction();
		}
	} catch (Exception e) {
		e.printStackTrace();
	}

        boolean isNewIndiv = false;

	// allow flow either way if one or the other has an ID
	//if ((indiv == null || indiv2 == null) && (enc != null)) {
        if ((indiv == null || (otherIndivs.size() == 0)) && (enc != null)) {

		System.out.println("indiv is null OR otherIndivs is empty, and two viable enc have been selected!");

		try {
			//enc.setState("approved");
			//enc2.setState("approved");

			// neither have an individual
			if (allEncIndivs.size() == 0) {
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
                                                isNewIndiv = true;
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
                    for (Encounter oenc : otherEncs) {
					    oenc.setIndividual(indiv);
					    indiv.addEncounter(oenc);
										
						//add same logging style as IndividualAddEncounter
						oenc.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>" + "Added to " + indiv.getDisplayName(request) + ".</p>");
						indiv.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>" + "Added encounter " + oenc.getCatalogNumber() + ".</p>");
						oenc.setMatchedBy("Pattern match");
                    }

					myShepherd.updateDBTransaction();
                    setImportTaskComplete(myShepherd, enc);
                    for (Encounter oenc : otherEncs) {
                        setImportTaskComplete(myShepherd, oenc);
                    }
                    indiv.refreshNamesCache();

                    if ((indiv != null) && (enc != null)) IndividualAddEncounter.executeEmails(myShepherd, request, indiv, isNewIndiv, enc, context, langCode);


				} else {
					res.put("error", "Please enter a new Individual ID for both encounters.");
				}
			}

			// query enc has indy, or already stashed in URL params
			//if (indiv!=null&&indiv2==null) {
			if ((indiv != null) && (otherIndivs.size() < 1)) {
				System.out.println("CASE 2: query enc indy is null");
                                for (Encounter oenc : otherEncs) {
                                    oenc.setIndividual(indiv);
                                    indiv.addEncounter(oenc);
									//add same logging style as IndividualAddEncounter
									oenc.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>" + "Added to " + indiv.getDisplayName(request) + ".</p>");
									indiv.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>" + "Added encounter " + oenc.getCatalogNumber() + ".</p>");
									oenc.setMatchedBy("Pattern match");
                                }
				res.put("individualName", indiv.getDisplayName(request, myShepherd));
				myShepherd.updateDBTransaction();
				// if(enc2!=null){
                                for (Encounter oenc : otherEncs) {
                                    IndividualAddEncounter.executeEmails(myShepherd, request, indiv, false, oenc, context, langCode);
                                    setImportTaskComplete(myShepherd, oenc);
                                }
			}

			// target enc has indy
			//if (indiv==null&&indiv2!=null) {
			if ((indiv == null) && (allEncIndivs.size() == 1)) {
				System.out.println("CASE 3: target enc indy is null");
                                Iterator<MarkedIndividual> it = allEncIndivs.iterator();
                                MarkedIndividual oindiv = it.next();
				enc.setIndividual(oindiv);
				oindiv.addEncounter(enc);
				
				//add same logging style as IndividualAddEncounter
				enc.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>" + "Added to " + oindiv.getDisplayName(request) + ".</p>");
				oindiv.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>" + "Added encounter " + enc.getCatalogNumber() + ".</p>");
				enc.setMatchedBy("Pattern match");
				
				res.put("individualName", oindiv.getDisplayName(request, myShepherd));
				myShepherd.updateDBTransaction();

				IndividualAddEncounter.executeEmails(myShepherd, request, oindiv, false, enc, context, langCode);
                                setImportTaskComplete(myShepherd, enc);
			}


			//String matchMsg = enc.getMatchedBy();
			//if ((matchMsg == null) || matchMsg.equals("Unknown")) matchMsg = "";
			//matchMsg += "<p>match approved via <i>iaResults</i> (by <i>" + AccessControl.simpleUserString(request) + "</i>) " + ((taskId == null) ? "<i>unknown Task ID</i>" : "Task <b>" + taskId + "</b>") + "</p>";
			//enc.setMatchedBy(matchMsg);
            //            for (Encounter oenc : otherEncs) {
			//    oenc.setMatchedBy(matchMsg);
            //            }

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

	if ((indiv == null) && (otherIndivs.size() < 1)) {
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
