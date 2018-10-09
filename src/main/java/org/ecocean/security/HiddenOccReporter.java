package org.ecocean.security;

import org.ecocean.Occurrence;
import javax.servlet.http.HttpServletRequest;
import java.util.Vector;

public class HiddenOccReporter extends HiddenDataReporter<Occurrence> {
	
	protected static final String className = "Occurrence";

	public HiddenOccReporter(Vector tObjectsToFilter, HttpServletRequest request, boolean viewOnly) {
		super(className, tObjectsToFilter, request, viewOnly);
	}
	public HiddenOccReporter(Vector tObjectsToFilter, HttpServletRequest request) {
		super(className, tObjectsToFilter, request);
	}


	// atomic methods that HiddenDataReporter methods call
	protected String getOwnerUsername(Occurrence occ) {
		return occ.getSubmitterID();
	}
	protected String getDatabaseId(Occurrence occ) {
		return occ.getOccurrenceID();
	}
	protected boolean canUserAccess(Occurrence occ) {
		return occ.canUserAccess(this.request);
	}
	public String getCollabUrl(String encId) {
		return Occurrence.getWebUrl(encId, this.request);
	}
}