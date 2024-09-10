package org.ecocean.security;

import org.ecocean.MarkedIndividual;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.Shepherd;

import java.util.Vector;
import javax.servlet.http.HttpServletRequest;

public class HiddenIndividualReporter extends HiddenDataReporter<MarkedIndividual> {
    protected static final String className = "Individual";

    public HiddenIndividualReporter(Vector tObjectsToFilter, HttpServletRequest request,
        boolean viewOnly, Shepherd myShepherd) {
        super(className, request, viewOnly, myShepherd);
    }
    public HiddenIndividualReporter(Vector tObjectsToFilter, HttpServletRequest request,
        Shepherd myShepherd) {
        super(className, request, true, myShepherd);
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

    public Vector viewableResults(Vector tObjectsToFilter, boolean hiddenIdsToOwnersIsValid,
        Shepherd myShepherd) {
        if (!hiddenIdsToOwnersIsValid) loadAllViewable(tObjectsToFilter, myShepherd);
        Vector cleanResults = new Vector();
        for (Object untypedObj : tObjectsToFilter) {
            MarkedIndividual typedObj = (MarkedIndividual)untypedObj;
            // if hiddenData doesn't contain the object, add it to clean results
            if (!this.contains(typedObj)) cleanResults.add(untypedObj);
        }
        return cleanResults;
    }

    // calls 'add' on each of tObjects depending on canUserView
    // we only add individuals the user should NOT see
    public void loadAllViewable(Vector tObjects, Shepherd myShepherd) {
        for (Object tObject : tObjects) {
            MarkedIndividual indy = (MarkedIndividual)tObject;
            if (!ServletUtilities.isUserAuthorizedForIndividual(indy, request)) this.add(indy);
        }
    }
}
