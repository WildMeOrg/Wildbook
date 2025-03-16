package org.ecocean;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


// import org.apache.commons.math.stat.descriptive.SummaryStatistics;

public class IndexingManager {

    private static List<String> indexingQueue = Collections.synchronizedList(new ArrayList<>());
  
    public static List<String> getIndexingQueue() { return indexingQueue; }
    public static void addIndexingQueueEntry(String objectID) {
    	if(!indexingQueue.contains(objectID))indexingQueue.add(objectID);
    }

    public static void removeIndexingQueueEntry(String objectID) {
        if (indexingQueue.contains(objectID)) {
        	indexingQueue.remove(objectID);
        }
    }

    
    public void resetIndexingQueuehWithInitialCapacity(int initialCapacity) {
    	indexingQueue = null;
    	indexingQueue = Collections.synchronizedList(new ArrayList<>());
    }

}
