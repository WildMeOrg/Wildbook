package org.ecocean.security;

import org.ecocean.shepherd.core.Shepherd;

import javax.servlet.http.*;

import static org.ecocean.Util.addToMultimap;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import jxl.write.*;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;

/**
 * a HiddenDataReporter is a simple, non-persistent class. Its intended use is mainly in exporters or search results. It keeps track of data that was
 * hidden from a particular user so that that user can request collaborations with the data owners. Inheriting classes are defined by their choice of
 * the data type <T>
 * For example, class HiddenEncReporter extends AbstractHiddenDataReporter<Encounter>
 *
 * Useage:
 * > init with a vector of search results
 * > filter your export with the contains() method
 * > print an excel report if you like with writeHiddenDataReport
 */
abstract class HiddenDataReporter<T> {
    protected Map<String, String> hiddenIdsToOwners; // id: owner map listing all hidden data
    protected final HttpServletRequest request; // the whole thing only makes sense in the context of a request (which has attached user object)
    public final String className; // frustrating that I can't figure out how to get this generically from <T>

    // so we don't have to make a new Shepherd for each canUserView call
    // protected Shepherd myShepherd;

    // for when you're only viewing data in a table e.g. searchResults
    // (if viewOnly we care about canUserViewObject, if !viewOnly we care about canUserAccessObject)
    boolean viewOnly;
    public HiddenDataReporter(String className, Vector tObjectsToFilter, HttpServletRequest request,
        Shepherd myShepherd) {
        this(className, tObjectsToFilter, request, false, myShepherd);
    }

    public HiddenDataReporter(String className, Vector tObjectsToFilter, HttpServletRequest request,
        boolean viewOnly, Shepherd myShepherd) {
        this.className = className;
        this.request = request;
        this.hiddenIdsToOwners = new HashMap<String, String>();
        this.viewOnly = viewOnly;
        if (tObjectsToFilter != null) {
            if (viewOnly) this.loadAllViewable(tObjectsToFilter, myShepherd);
            else this.loadAllByPermission(tObjectsToFilter);
        }
    }

    // honor the superclass, but don't let it do anything
    public HiddenDataReporter(String className, HttpServletRequest request, boolean viewOnly,
        Shepherd myShepherd) {
        this.className = className;
        this.request = request;
        this.hiddenIdsToOwners = new HashMap<String, String>();
        this.viewOnly = viewOnly;
    }

    // if you just want to scrub the search results vector
    public Vector securityScrubbedResults(Vector tObjectsToFilter,
        boolean hiddenIdsToOwnersIsValid) {
        if (!hiddenIdsToOwnersIsValid) loadAllByPermission(tObjectsToFilter);
        Vector cleanResults = new Vector();
        for (Object untypedObj : tObjectsToFilter) {
            T typedObj = (T)untypedObj;
            // if hiddenData doesn't contain the object, add it to clean results
            if (!this.contains(typedObj)) cleanResults.add(untypedObj);
        }
        return cleanResults;
    }

    // assumes these are the same objects you submitted at initialization
    public Vector securityScrubbedResults(Vector tObjectsToFilter) {
        return securityScrubbedResults(tObjectsToFilter, false);
    }

    public Vector viewableResults(Vector tObjectsToFilter, Shepherd myShepherd) {
        return viewableResults(tObjectsToFilter, false, myShepherd);
    }

    public Vector viewableResults(Vector tObjectsToFilter, boolean hiddenIdsToOwnersIsValid,
        Shepherd myShepherd) {
        if (!hiddenIdsToOwnersIsValid) loadAllViewable(tObjectsToFilter, myShepherd);
        Vector cleanResults = new Vector();
        for (Object untypedObj : tObjectsToFilter) {
            T typedObj = (T)untypedObj;
            // if hiddenData doesn't contain the object, add it to clean results
            if (!this.contains(typedObj)) cleanResults.add(untypedObj);
        }
        return cleanResults;
    }

    // since different WB objects use diff conventions
    // the HiddenDataReporter logic is the same for all inheriting classes and built from
    // these basic pieces. You can make a HDR for anything with these methods.
    abstract protected String getOwnerUsername(T elem);
    abstract protected String getDatabaseId(T elem);
    abstract protected boolean canUserAccess(T elem);

    protected boolean canUserView(T elem, Shepherd myShepherd) {
        String ownerName = getOwnerUsername(elem);

        return Collaboration.canUserViewOwnedObject(ownerName, request, myShepherd);
    }

    // getCollabUrl directs the search user (who submitted req) to a page where they
    // can initialize a collaboration with the data owner (of databaseId). This way we don't have
    // to reveal contact info in exports, and leave collaboration-initialization
    // to the existing entrypoints in Wildbook
    abstract public String getCollabUrl(String databaseId);

    // add a data point to the hidden data multimap
    public void add(T elem) {
        // addToMultimap(this.hiddenDataByOwner, getOwnerUsername(T), getDatabaseId(T));
        this.hiddenIdsToOwners.put(getDatabaseId(elem), getOwnerUsername(elem));
    }

    public boolean contains(String dataId) {
        return (hiddenIdsToOwners.containsKey(dataId));
    }

    public boolean contains(T elem) {
        return contains(getDatabaseId(elem));
    }

    // calls 'add' on each of tObjects depending on canUserAccess
    public void loadAllByPermission(Vector tObjects) {
        for (Object tObject : tObjects) {
            T dataPoint = (T)tObject;
            if (!canUserAccess(dataPoint)) this.add(dataPoint);
        }
    }

    // calls 'add' on each of tObjects depending on canUserView
    public void loadAllViewable(Vector tObjects, Shepherd myShepherd) {
        for (Object tObject : tObjects) {
            T dataPoint = (T)tObject;
            if (!canUserView(dataPoint, myShepherd)) this.add(dataPoint);
        }
    }

    // total number of hidden objects
    public int size() {
        return hiddenIdsToOwners.size();
    }

    // useful in printing the actual report, kinda the inverse map of hiddenIdsToOwners
    public Map<String, Set<String> > getHiddenDataByOwner() {
        Map<String, Set<String> > hiddenDataByOwner = new HashMap<String, Set<String> >();

        for (String dataId : hiddenIdsToOwners.keySet()) {
            String owner = hiddenIdsToOwners.get(dataId);
            addToMultimap(hiddenDataByOwner, owner, dataId);
        }
        return hiddenDataByOwner;
    }

    public Set<String> getAllHiddenIds() {
        return hiddenIdsToOwners.keySet();
    }

    // for a given owner, returns an ID of an object owned by that owner
    // this is useful to provide a link to the hidden data, which prompts the user to start a collaboration
    public String getSampleID(String owner) {
        if (hiddenIdsToOwners == null || hiddenIdsToOwners.size() == 0) return null;
        Iterator<String> ids = hiddenIdsToOwners.keySet().iterator();
        while (ids.hasNext()) {
            String thisId = ids.next();
            if (owner.equals(hiddenIdsToOwners.get(thisId))) return thisId;
        }
        return ids.next();
    }

    public String getSampleCollab(String owner) {
        return getCollabUrl(getSampleID(owner));
    }

    // makes an excel report about this hidden data, exposing only the usernames, number encounters, and a prompt for collaboration
    public void writeHiddenDataReport(WritableWorkbook excelFile, int tabForReport)
    throws jxl.write.WriteException {
        WritableSheet hiddenDataSheet = excelFile.createSheet("Hidden Data Report", tabForReport);
        String[] missingDataColHeaders = new String[] {
            "username", "number of their " + className + "s hidden from your results",
                "example " + className + " page (click through to initialize a collaboration)"
        };

        for (int i = 0; i < missingDataColHeaders.length; i++) {
            hiddenDataSheet.addCell(new Label(i, 0, missingDataColHeaders[i]));
        }
        Map<String, Set<String> > hiddenEncsByOwner = getHiddenDataByOwner();
        int currentRow = 0;
        for (String owner : hiddenEncsByOwner.keySet()) {
            currentRow++;

            hiddenDataSheet.addCell(new Label(0, currentRow, owner));

            String numEncs = String.valueOf(hiddenEncsByOwner.get(owner).size());
            hiddenDataSheet.addCell(new Label(1, currentRow, numEncs));

            String sampleEncId = null;
            if (hiddenEncsByOwner.get(owner).size() > 0)
                sampleEncId = hiddenEncsByOwner.get(owner).iterator().next();
            String sampleLink = getCollabUrl(sampleEncId);
            hiddenDataSheet.addCell(new Label(2, currentRow, sampleLink));
        }
    }

    public void writeHiddenDataReport(Sheet hiddenSheet)
    throws IllegalArgumentException, IllegalAccessException {
        String[] missingDataColHeaders = new String[] {
            "username", "number of their " + className + "s hidden from your results",
                "example " + className + " page (click through to initialize a collaboration)"
        };
        Row row = hiddenSheet.getRow(0);

        if (row == null) {
            row = hiddenSheet.createRow(0);
        }
        for (int i = 0; i < missingDataColHeaders.length; i++) {
            Cell cell = row.createCell(i, Cell.CELL_TYPE_STRING);
            cell.setCellValue(missingDataColHeaders[i]);
        }
        Map<String, Set<String> > hiddenEncsByOwner = getHiddenDataByOwner();
        int currentRow = 0;
        for (String owner : hiddenEncsByOwner.keySet()) {
            currentRow++;

            Row row2 = hiddenSheet.getRow(currentRow);
            if (row2 == null) {
                row2 = hiddenSheet.createRow(currentRow);
            }
            Cell cell1 = row2.createCell(0, Cell.CELL_TYPE_STRING);
            cell1.setCellValue(owner);

            String numEncs = String.valueOf(hiddenEncsByOwner.get(owner).size());
            Cell cell2 = row2.createCell(1, Cell.CELL_TYPE_STRING);
            cell2.setCellValue(numEncs);

            String sampleEncId = null;
            if (hiddenEncsByOwner.get(owner).size() > 0)
                sampleEncId = hiddenEncsByOwner.get(owner).iterator().next();
            String sampleLink = getCollabUrl(sampleEncId);
            Cell cell3 = row2.createCell(2, Cell.CELL_TYPE_STRING);
            cell3.setCellValue(sampleLink);
        }
    }

    public void writeHiddenDataReport(WritableWorkbook excelOut)
    throws jxl.write.WriteException {
        writeHiddenDataReport(excelOut, 1);
    }
}
