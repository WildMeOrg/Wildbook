package org.ecocean.security;

import org.ecocean.MarkedIndividual;
import org.ecocean.Shepherd;
import org.ecocean.servlet.ServletUtilities;

import javax.servlet.http.HttpServletRequest;
import java.util.Vector;

public class HiddenIndividualReporter extends HiddenDataReporter<MarkedIndividual> {

	protected static final String className = "Individual";

	public HiddenIndividualReporter(Vector tObjectsToFilter, HttpServletRequest request, boolean viewOnly, Shepherd myShepherd) {
		super(className, tObjectsToFilter, request, viewOnly, myShepherd);
	}
	public HiddenIndividualReporter(Vector tObjectsToFilter, HttpServletRequest request, Shepherd myShepherd) {
		super(className, tObjectsToFilter, request, myShepherd);
	}

	// atomic methods that HiddenDataReporter methods call
	protected String getOwnerUsername(MarkedIndividual ind) {
		return ind.getAnEncounterOwner();
	}
	protected String getDatabaseId(MarkedIndividual ind) {
		return ind.getIndividualID();
	}
	protected boolean canUserAccess(MarkedIndividual ind) {
		return ind.canUserAccess(this.request);
	}
	public String getCollabUrl(String indId) {
		return MarkedIndividual.getWebUrl(indId, this.request);
	}
	
	 public Vector viewableResults(Vector tObjectsToFilter, boolean hiddenIdsToOwnersIsValid, Shepherd myShepherd) {
	    //if (!hiddenIdsToOwnersIsValid) loadAllViewable(tObjectsToFilter, myShepherd);
	    Vector cleanResults = new Vector();
	    for (Object untypedObj: tObjectsToFilter) {
	      MarkedIndividual typedObj = (MarkedIndividual) untypedObj;
	      // if hiddenData doesn't contain the object, add it to clean results
	      if (ServletUtilities.isUserAuthorizedForIndividual(typedObj, request)) cleanResults.add(untypedObj);
	    }
	    return cleanResults;
	  }
	
	
}