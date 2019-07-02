package org.ecocean.security;

import org.ecocean.Encounter;
import java.util.Vector;
import javax.servlet.http.HttpServletRequest;

public class HiddenEncReporter extends HiddenDataReporter<Encounter> {

	protected static final String className = "Encounter";

	public HiddenEncReporter(Vector tObjectsToFilter, HttpServletRequest request) {
		super(className, tObjectsToFilter, request);
	}

	// atomic methods that HiddenDataReporter methods call
	protected String getOwnerUsername(Encounter enc) {
		return enc.getAssignedUsername();
	}
	protected String getDatabaseId(Encounter enc) {
		return enc.getCatalogNumber();
	}
	protected boolean canUserAccess(Encounter enc) {
		return enc.canUserAccess(this.request);
	}
	public String getCollabUrl (String encId ) {
		return Encounter.getWebUrl(encId, this.request);
	}
}