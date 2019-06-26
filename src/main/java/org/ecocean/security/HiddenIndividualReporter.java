package org.ecocean.security;

import org.ecocean.MarkedIndividual;
import javax.servlet.http.HttpServletRequest;
import java.util.Vector;

public class HiddenIndividualReporter extends HiddenDataReporter<MarkedIndividual> {

	protected static final String className = "Individual";

	public HiddenIndividualReporter(Vector tObjectsToFilter, HttpServletRequest request, boolean viewOnly) {
		super(className, tObjectsToFilter, request, viewOnly);
	}
	public HiddenIndividualReporter(Vector tObjectsToFilter, HttpServletRequest request) {
		super(className, tObjectsToFilter, request);
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
}