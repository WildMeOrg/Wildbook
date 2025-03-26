package org.ecocean;

/*
 * IndexingManagerFactory ensures that there is a Singleton IndexingManager that handles
 * and throttles indexing and unindexing OpenSearch activity as initiated by
 * WildbookLifeCycleListener, which follows the DataNucleus object lifecycle.
 */
public class IndexingManagerFactory {
    
	//The sole IndexingManager that should be used for the OpenSearch indexing lifecycles
	private static IndexingManager im;

	//Returns a threadsafe IndexingManager singleton
    public synchronized static IndexingManager getIndexingManager() {
        try {
            if (im == null) {
                im = new IndexingManager();
            }
            return im;
        } catch (Exception jdo) {
            jdo.printStackTrace();
            System.out.println("I couldn't instantiate an org.ecocean.IndexingManager.");
            return null;
        }
    }
}
