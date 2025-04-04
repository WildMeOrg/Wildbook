package org.ecocean;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.shepherd.core.ShepherdProperties;



public class IndexingManager {
	
	//The ExecutorService executes indexing jobs
	private final ExecutorService executor;
		
	//The indexingQueue is a List of Strings that represent the UUIDs of Base class-implementing objects 
	//(Encounter, MarkedIndividual, Annotation, etc.) that need to be indexed or unindexed.
	//The queue ensures that overzealous calls from the WildbookLifecycleListener do not cause
	//unnecessary, duplicate indexing jobs. The UUIDs of the objects being ndexed are removed 
	//from the queue once completed.
    private List<String> indexingQueue = Collections.synchronizedList(new ArrayList<String>());
  
    public IndexingManager() {
    	
    	int numAllowedThreads = 4;
    	Properties props = ShepherdProperties.getProperties("OpenSearch.properties", "", "context0");
    	if(props!=null) {
	    	String indexingNumAllowedThreads = props.getProperty("indexingNumAllowedThreads");
	    	if(indexingNumAllowedThreads!=null) {
	    		Integer allowThreads = Integer.getInteger(indexingNumAllowedThreads);
	    		if(allowThreads!=null)numAllowedThreads = allowThreads.intValue();
	    	}
    	}
    	executor = Executors.newFixedThreadPool(numAllowedThreads);
    	
    }
    
    //Returns the indexing queue List of Strings
    public List<String> getIndexingQueue() { return indexingQueue; }
    
    /*
     * Adds a Base object to the queue for indexing or unindexing
     * @Base base The Base-class implementing object to be indexed or unindexed
     * @boolean unindex Whether the object is to be indexed or unindexed.
     */
    public void addIndexingQueueEntry(Base base, boolean unindex) {
    	String objectID = base.getId();
    	Class myClass = base.getClass();
    	if(!indexingQueue.contains(objectID)) {
    		indexingQueue.add(objectID);
    		
    		//IMPORTANT - no persistent objects, such as the passed in Base can be referenced inside this method
            Runnable rn = new Runnable() {
                public void run() {
                    Shepherd bgShepherd = new Shepherd("context0");
                    bgShepherd.setAction("IndexingManager_" + objectID);
                    bgShepherd.beginDBTransaction();
                    try {
                    	Base base = (Base)bgShepherd.getPM().getObjectById(myClass, objectID);
                    	if(unindex) {base.opensearchUnindexDeep();}
                    	else{base.opensearchIndexDeep();}
                    	
                    } 
                    catch (Exception e) {
                        e.printStackTrace();
                    } 
                    finally {
                        bgShepherd.rollbackAndClose();
                    }
                    
                    //remove from indexing queue
                    if(indexingQueue.contains(objectID))indexingQueue.remove(objectID);
                }
            };

            executor.execute(rn);
    		
    	}

    }

    //Removes an oject's UUID from the queue
    public void removeIndexingQueueEntry(String objectID) {
        if (indexingQueue.contains(objectID)) {
        	indexingQueue.remove(objectID);
        }
    }

    //Resets the indexing queue
    public void resetIndexingQueuehWithInitialCapacity(int initialCapacity) {
    	indexingQueue = null;
    	indexingQueue = Collections.synchronizedList(new ArrayList<String>());
    }
    
    public void shutdown() {
    	if(executor!=null)executor.shutdown();
    }

}
