package org.ecocean;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;



public class IndexingManager {

    private List<String> indexingQueue = Collections.synchronizedList(new ArrayList<String>());
  
    public List<String> getIndexingQueue() { return indexingQueue; }
    
    public void addIndexingQueueEntry(String objectID) {
    	if(!indexingQueue.contains(objectID)) {
    		indexingQueue.add(objectID);
    	}
    	
    	
    	
    }

    public void removeIndexingQueueEntry(String objectID) {
        if (indexingQueue.contains(objectID)) {
        	indexingQueue.remove(objectID);
        }
    }

    
    public void resetIndexingQueuehWithInitialCapacity(int initialCapacity) {
    	indexingQueue = null;
    	indexingQueue = Collections.synchronizedList(new ArrayList<String>());
    }

}
