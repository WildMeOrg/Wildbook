package org.ecocean;


public class IndexingManagerFactory {
    private static IndexingManager im;

    public synchronized static IndexingManager getIndexingManager() {
        try {
            if (im == null) {
                im = new IndexingManager();
            }
            return im;
        } catch (Exception jdo) {
            jdo.printStackTrace();
            System.out.println("I couldn't instantiate an IndexingManager.");
            return null;
        }
    }
}
