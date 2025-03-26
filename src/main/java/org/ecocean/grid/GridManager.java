package org.ecocean.grid;

// import org.apache.commons.math.stat.descriptive.SummaryStatistics;
// import org.ecocean.CommonConfiguration;
import org.ecocean.Shepherd;

// import org.ecocean.servlet.ServletUtilities;

import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.http.HttpServletRequest;

// import java.io.File;
import java.util.ArrayList;
import java.util.Enumeration;

// import org.apache.commons.math.stat.descriptive.SummaryStatistics;

public class GridManager {
    private ArrayList<GridNode> nodes = new ArrayList<GridNode>();

    // these are only generic nodes
    // targeted nodes are always allowed
    private int numAllowedNodes = 25;
    // private long nodeTimeout = 180000;
    // private String appletVersion = "1.2";
    public long checkoutTimeout = 240000;
    public int groupSize = 20;
    public int creationDeletionThreadQueueSize = 1;
    public int scanTaskLimit = 150;
    private long lastGridStatsQuery = 1;
    private long gridStatsRefreshPeriod = 300000;
    private int numScanTasks = 0;
    // private int numScanWorkItems = 0;
    private int numCollisions = 0;
    public int maxGroupSize = 240;
    public int numCompletedWorkItems = 0;

    // public ConcurrentHashMap<String,Integer> scanTaskSizes=new ConcurrentHashMap<String, Integer>();

    // Modified Groth algorithm parameters
    private String epsilon = "0.008";
    private String R = "49.8";
    private String Sizelim = "0.998";
    private String maxTriangleRotation = "12.33";
    private String C = "0.998";
    private String secondRun = "true";

    private static ConcurrentHashMap<String,
        EncounterLite> matchGraph = new ConcurrentHashMap<String, EncounterLite>();
    private static int numRightPatterns = 0;
    private static int numLeftPatterns = 0;

    private static boolean creationThread = false;

    // hold incompleted scanWorkItems
    private ArrayList<ScanWorkItem> toDo = new ArrayList<ScanWorkItem>();

    // hold completed scanWorkItems
    private ArrayList<ScanWorkItemResult> done = new ArrayList<ScanWorkItemResult>();

    // in-process scan work items that have been checked out and sent to a node
    private ArrayList<ScanWorkItem> underway = new ArrayList<ScanWorkItem>();

    public GridManager() {}

    public ArrayList<GridNode> getNodes() {
        return nodes;
    }

    public void initializeNodes(int initialEstimateNodes) {
        nodes = new ArrayList<GridNode>(initialEstimateNodes);
    }

    public void setMaxGroupSize(int mgs) {
        maxGroupSize = mgs;
    }

    public int getNumCollisions() {
        return numCollisions;
    }

    public synchronized void reportCollision() {
        numCollisions++;
    }

    public void setCreationThread(boolean status) {
        creationThread = status;
    }

    public boolean getCreationThread() {
        return creationThread;
    }

    public long getCheckoutTimeout() {
        return checkoutTimeout;
    }

    public void setCheckoutTimeout(long timeout) {
        this.checkoutTimeout = timeout;
    }

    public int getGroupSize() {
        return groupSize;
    }

    public void setGroupSize(int size) {
        this.groupSize = size;
    }

    public int getCreationDeletionThreadQueueSize() {
        return creationDeletionThreadQueueSize;
    }


    public int getNumNodes() {
        return nodes.size();
    }

    public int getNumAllowedNodes() {
        return numAllowedNodes;
    }

    public void setNumAllowedNodes(int num) {
        numAllowedNodes = num;
    }

    public boolean containsNode(String nodeID) {
        int numNodes = nodes.size();

        for (int i = 0; i < numNodes; i++) {
            if (nodes.get(i).getNodeIdentifier().equals(nodeID)) {
                return true;
            }
        }
        return false;
    }

    public synchronized boolean isGridSpaceAvailable(HttpServletRequest request, boolean targeted) {
        String nodeID = request.getParameter("nodeIdentifier");

        if (!containsNode(nodeID)) {
            GridNode node = new GridNode(request, groupSize);
            nodes.add(node);
        }
        // library users can always get permission to run targeted scans
        if (targeted) {
            return true;
        }
        // beyond here we know it's a generic node, which means it may be denied access to the queue
        else if (isInAllowedPosition(nodeID)) {
            return true;
        }
        // else if(canMakeSpace(request)){return true;}
        return false;
    }

    public synchronized boolean isInAllowedPosition(String nodeID) {
        int numNodes = nodes.size();

        if (numNodes < numAllowedNodes) {
            return true;
        }
        long currenTime = System.currentTimeMillis();
        for (int i = 0; i < numNodes; i++) {
            if (nodes.get(i).getNodeIdentifier().equals(nodeID)) {
                if (i <= (numAllowedNodes - 1)) return true;
            }
        }
        return false;
    }

    public synchronized void processHeartbeat(HttpServletRequest request) {
        String nodeID = request.getParameter("nodeIdentifier");

        if (containsNode(nodeID)) {
            GridNode nd = getGridNode(nodeID);
            nd.registerHeartbeat();
        } else {
            // create a new node
            GridNode node = new GridNode(request, groupSize);
            nodes.add(node);
        }
    }

    public int getNextGroupSize(GridNode nd) {
        return nd.getNextGroupSize(checkoutTimeout, maxGroupSize, groupSize);
    }

    public GridNode getGridNode(String nodeID) {
        int numNodes = nodes.size();

        for (int i = 0; i < numNodes; i++) {
            if (nodes.get(i).getNodeIdentifier().equals(nodeID)) {
                return nodes.get(i);
            }
        }
        return null;
    }

    public int getScanTaskLimit() {
        return scanTaskLimit;
    }

    public void setScanTaskLimit(int limit) {
        this.scanTaskLimit = limit;
    }

    public int getPerMinuteRate() {
        int rate = 0;
        // cleanupOldNodes();
        int numNodes = nodes.size();
        long totalComparisons = 0;
        long totalTime = 0;

        for (int i = 0; i < numNodes; i++) {
            GridNode nd = nodes.get(i);
            totalComparisons = totalComparisons + nd.numComparisons;
            totalTime = totalTime + nd.totalTimeSinceStart;
        }
        if (totalTime > 0) {
            rate = (int)(totalComparisons * 60 / (totalTime / 1000));
        }
        return rate;
    }

    // call this from outside any other transaction
    private void updateGridStats(String context) {
        long currenTime = System.currentTimeMillis();

        // refresh the grid stats if necessary
        if ((lastGridStatsQuery == 1) ||
            ((currenTime - lastGridStatsQuery) > gridStatsRefreshPeriod)) {
            Shepherd myShepherd = new Shepherd(context);
            myShepherd.setAction("GridManager.class");
            myShepherd.beginDBTransaction();
            numScanTasks = myShepherd.getNumScanTasks();
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
            myShepherd = null;
            lastGridStatsQuery = currenTime;
        }
    }

    public int getNumTasks(String context) {
        updateGridStats(context);
        return numScanTasks;
    }

    public int getNumCompletedWorkItems() {
        return numCompletedWorkItems;
    }

    public synchronized void incrementCompletedWorkItems(int numCompleted) {
        numCompletedWorkItems += numCompleted;
    }

    public double getCollisionRatePercentage() {
        if (numCompletedWorkItems == 0) {
            return 0;
        } else {
            return (100 * numCollisions / numCompletedWorkItems);
        }
    }

    public String getGrothEpsilon() {
        return epsilon;
    }

    public String getGrothR() {
        return R;
    }

    public String getGrothSizelim() {
        return Sizelim;
    }

    public String getGrothMaxTriangleRotation() {
        return maxTriangleRotation;
    }

    public String getGrothC() {
        return C;
    }

    public String getGrothSecondRun() {
        return secondRun;
    }

    public ArrayList<ScanWorkItem> getIncompleteWork() {
        return toDo;
    }

    public ArrayList<ScanWorkItemResult> getCompletedWork() {
        return done;
    }

    public void removeAllCompletedWorkItems() {
        done = new ArrayList<ScanWorkItemResult>();
    }

    public void removeAllWorkItems() {
        toDo = new ArrayList<ScanWorkItem>();
        underway = new ArrayList<ScanWorkItem>();
        // numScanWorkItems=0;
    }

    public synchronized void addWorkItem(ScanWorkItem swi) {
        toDo.add(swi);
        // numScanWorkItems++;
    }

    public synchronized ArrayList<ScanWorkItem> getWorkItems(int num) {
        ArrayList<ScanWorkItem> returnItems = new ArrayList<ScanWorkItem>();
        int iterNum = num;

        if (iterNum > toDo.size()) iterNum = toDo.size();
        // int toDoSize= toDo.size();
        // boolean cont = true;
        long time = System.currentTimeMillis();
        for (int i = 0; i < iterNum; i++) {
            // if (cont) {
            ScanWorkItem item = toDo.get(i);
            // if ((!item.isCheckedOut(checkoutTimeout)) && (!item.isDone())) {
            item.setStartTime(time);
            returnItems.add(item);
            // if (returnItems.size() >= num) {
            // cont = false;
            // }
            // }
            // }
        }
        if (returnItems.size() > 0) {
            toDo.removeAll(returnItems);
            numScanTasks = numScanTasks - returnItems.size();
            underway.addAll(returnItems);
            return returnItems;
        }
        // if toDO doesn't have any work, start popping stuff off underway to help finish up
        else {
            iterNum = underway.size();
            if (iterNum > maxGroupSize) iterNum = maxGroupSize;
            for (int i = 0; i < iterNum; i++) {
                // if (cont) {
                ScanWorkItem item = underway.get(i);
                if (!item.isDone()) {
                    // item.setStartTime(System.currentTimeMillis());
                    returnItems.add(item);
                    // if (returnItems.size() >= num) {
                    // cont = false;
                    // }
                }
                // }
            }
        }
        return returnItems;
    }

    public void removeWorkItem(String uniqueNumberWorkItem) {
        int iter = underway.size();

        for (int i = 0; i < iter; i++) {
            if (underway.get(i).getUniqueNumber().equals(uniqueNumberWorkItem)) {
                underway.remove(i);
                // numScanWorkItems--;
                i--;
                iter--;
            }
        }
    }

    public synchronized void removeWorkItemsForTask(String taskID) {
        for (int i = 0; i < underway.size(); i++) {
            if (underway.get(i).getTaskIdentifier().equals(taskID)) {
                underway.remove(i);
                // numScanWorkItems--;
                i--;
            }
        }
        for (int i = 0; i < toDo.size(); i++) {
            if (toDo.get(i).getTaskIdentifier().equals(taskID)) {
                toDo.remove(i);
                // numScanWorkItems--;
                i--;
            }
        }
    }

    public void removeCompletedWorkItemsForTask(String taskID) {
        // int iter=done.size();
        try {
            for (int i = 0; i < done.size(); i++) {
                if ((done.get(i) != null) && (done.get(i).getUniqueNumberTask().equals(taskID))) {
                    done.remove(i);
                    i--;
                    // iter--;
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    public synchronized void checkinResult(ScanWorkItemResult swir) {
        try {
            // System.out.println("GM checking in a scan result!");
            if (!doneContains(swir)) {
                done.add(swir);
                numCompletedWorkItems++;
            } else {
                numCollisions++;
            }
            removeWorkItem(swir.getUniqueNumberWorkItem());
        } catch (Exception e) { e.printStackTrace(); }
    }

    public boolean doneContains(ScanWorkItemResult swir) {
        boolean hasit = false;

        try {
            int iter = done.size();
            for (int i = 0; i < iter; i++) {
                if ((done.get(i) != null) &&
                    (done.get(i).getUniqueNumberWorkItem().equals(
                    swir.getUniqueNumberWorkItem()))) {
                    hasit = true;
                }
            }
        } catch (Exception e) {}
        return hasit;
    }

    public boolean toDoContains(ScanWorkItem swi) {
        boolean hasit = false;
        int iter = toDo.size();

        for (int i = 0; i < iter; i++) {
            if (toDo.get(i).getUniqueNumber().equals(swi.getUniqueNumber())) {
                hasit = true;
            }
        }
        return hasit;
    }

    public int getNumWorkItemsCompleteForTask(String taskID) {
        int num = 0;

        try {
            if (done == null) { done = new ArrayList<ScanWorkItemResult>(); }
            int iter = done.size();
            for (int i = 0; i < iter; i++) {
                if ((done.get(i) != null) && (done.get(i).getUniqueNumberTask().equals(taskID))) {
                    num++;
                }
            }
        } catch (Exception e) {}
        return num;
    }

    public int getNumWorkItemsIncompleteForTask(String taskID) {
        int num = 0;

        try {
            if (toDo == null) { toDo = new ArrayList<ScanWorkItem>(); }
            // int iter = numScanWorkItems;
            for (int i = 0; i < toDo.size(); i++) {
                if ((toDo.get(i) != null) && (toDo.get(i).getTaskIdentifier().equals(taskID))) {
                    num++;
                }
            }
            if (underway == null) { underway = new ArrayList<ScanWorkItem>(); }
            int iter = underway.size();
            for (int i = 0; i < iter; i++) {
                if ((underway.get(i) != null) &&
                    (underway.get(i).getTaskIdentifier().equals(taskID))) {
                    num++;
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        return num;
    }

    public ArrayList<ScanWorkItem> getRemainingWorkItemsForTask(String taskID) {
        ArrayList<ScanWorkItem> list = new ArrayList<ScanWorkItem>();

        if (toDo == null) { toDo = new ArrayList<ScanWorkItem>(); }
        for (int i = 0; i < toDo.size(); i++) {
            if (toDo.get(i).getTaskIdentifier().equals(taskID)) {
                list.add(toDo.get(i));
            }
        }
        if (underway == null) { underway = new ArrayList<ScanWorkItem>(); }
        for (int i = 0; i < underway.size(); i++) {
            if (underway.get(i).getTaskIdentifier().equals(taskID)) {
                list.add(underway.get(i));
            }
        }
        return list;
    }

    public ArrayList<MatchObject> getMatchObjectsForTask(String taskID) {
        ArrayList<MatchObject> list = new ArrayList<MatchObject>();
        int iter = done.size();

        for (int i = 0; i < iter; i++) {
            try {
                if ((done.get(i) != null) && (done.get(i).getUniqueNumberTask().equals(taskID))) {
                    list.add(done.get(i).getResult());
                }
            } catch (Exception e) {
                // do nothing for now
            }
        }
        return list;
    }

    public ArrayList<ScanWorkItemResult> getResultsForTask(String taskID) {
        ArrayList<ScanWorkItemResult> list = new ArrayList<ScanWorkItemResult>();
        int iter = done.size();

        for (int i = 0; i < iter; i++) {
            if (done.get(i).getUniqueNumberTask().equals(taskID)) {
                list.add(done.get(i));
            }
        }
        return list;
    }

    public int getNumWorkItemsAndResults() {
        int returnMe = 0;

        if (toDo != null) returnMe += toDo.size();
        if (done != null) returnMe += done.size();
        if (underway != null) returnMe += underway.size();
        return returnMe;
    }

    public int getToDoSize() {
        return toDo.size();
    }

    public int getDoneSize() {
        return done.size();
    }

    public int getUnderwaySize() {
        return underway.size();
    }

    public ScanWorkItem getWorkItem(String uniqueNum) {
        int iter = toDo.size();
        ScanWorkItem swi = new ScanWorkItem();

        for (int i = 0; i < iter; i++) {
            if (toDo.get(i).getUniqueNumber().equals(uniqueNum)) {
                return toDo.get(i);
            }
        }
        for (int i = 0; i < underway.size(); i++) {
            if (underway.get(i).getUniqueNumber().equals(uniqueNum)) {
                return underway.get(i);
            }
        }
        return swi;
    }

    public int getNumProcessors() {
        int numProcessors = 0;
        ArrayList<GridNode> nodes = getNodes();
        int numNodes = nodes.size();

        for (int i = 0; i < numNodes; i++) {
            GridNode node = nodes.get(i);
            numProcessors += node.numProcessors;
        }
        return numProcessors;
    }

    public static ConcurrentHashMap<String, EncounterLite> getMatchGraph() { return matchGraph; }
    public static void addMatchGraphEntry(String elID, EncounterLite el) {
        matchGraph.put(elID, el);
        resetPatternCounts();
    }

    public static void removeMatchGraphEntry(String elID) {
        if (matchGraph.containsKey(elID)) {
            matchGraph.remove(elID);
        }
        resetPatternCounts();
    }

    public static EncounterLite getMatchGraphEncounterLiteEntry(String elID) {
        return matchGraph.get(elID);
    }

    public static synchronized int getNumRightPatterns() { return numRightPatterns; }
    public static synchronized int getNumLeftPatterns() { return numLeftPatterns; }

    private static ConcurrentHashMap<String,
        Long> speciesCountsMapLeft = new ConcurrentHashMap<String, Long>();
    private static ConcurrentHashMap<String,
        Long> speciesCountsMapRight = new ConcurrentHashMap<String, Long>();

    public ConcurrentHashMap<String, Long> getSpeciesCountsMapLeft() {
        return speciesCountsMapLeft;
    }

    public ConcurrentHashMap<String, Long> getSpeciesCountsMapRight() {
        return speciesCountsMapRight;
    }

    /*
     * Convenience method to speed ScanWorkItemCreationThread by always maintaining and recalculating accurate counts of potential patterns to compare
     * against.
     */
    private static synchronized void resetPatternCounts() {
        numLeftPatterns = 0;
        numRightPatterns = 0;
        speciesCountsMapLeft = new ConcurrentHashMap<String, Long>();
        speciesCountsMapRight = new ConcurrentHashMap<String, Long>();
        Enumeration<String> keys = getMatchGraph().keys();
        while (keys.hasMoreElements()) {
            String key = keys.nextElement();
            EncounterLite el = getMatchGraphEncounterLiteEntry(key);
            String species = "null";
            if (el.getGenus() != null && el.getSpecificEpithet() != null)
                species = (el.getGenus() + " " + el.getSpecificEpithet());
            if ((el.getSpots() != null) && (el.getSpots().size() > 0)) {
                numLeftPatterns++;
                // WB-1791 - do by-species counts as well
                if (speciesCountsMapLeft.containsKey(species)) {
                    speciesCountsMapLeft.put(species,
                        Long.sum(speciesCountsMapLeft.get(species), 1));
                } else { speciesCountsMapLeft.put(species, new Long(1)); }
            }
            if ((el.getRightSpots() != null) && (el.getRightSpots().size() > 0)) {
                numRightPatterns++;
                // WB-1791 - do by-species counts as well
                if (speciesCountsMapRight.containsKey(species)) {
                    speciesCountsMapRight.put(species,
                        Long.sum(speciesCountsMapRight.get(species), 1));
                } else { speciesCountsMapRight.put(species, new Long(1)); }
            }
        }
    }

    public void clearDoneItems() { done = new ArrayList<ScanWorkItemResult>(); }

    public int getNumUnderway() {
        if (underway != null) return underway.size();
        return 0;
    }

    public void resetMatchGraphWithInitialCapacity(int initialCapacity) {
        matchGraph = null;
        matchGraph = new ConcurrentHashMap<String, EncounterLite>(initialCapacity);
    }
}
