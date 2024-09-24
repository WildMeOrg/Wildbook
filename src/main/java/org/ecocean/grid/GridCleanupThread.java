package org.ecocean.grid;

import com.reijns.I3S.Pair;
import org.ecocean.Shepherd;

import java.util.List;
import javax.jdo.Extent;
import javax.jdo.Query;

public class GridCleanupThread implements Runnable {
    public Thread threadCleanupObject;
    private String context = "context0";

    /**
     * Constructor to create a new thread object
     */
    public GridCleanupThread(String context) {
        threadCleanupObject = new Thread(this, "gridCleanup");
        threadCleanupObject.start();
        this.context = context;
    }

    /**
     * main method of the shepherd thread
     */
    public void run() {
        cleanup();
    }

    public void cleanup() {
        Shepherd myShepherd = new Shepherd(context);

        myShepherd.setAction("GridCleanupThread.class");

        myShepherd.beginDBTransaction();

        // Iterator vpms=myShepherd.getAllPairsNoQuery();
        Extent encClass = myShepherd.getPM().getExtent(Pair.class, true);
        Query query = myShepherd.getPM().newQuery(encClass);
        int count = 0;
        int size = 1;
        while (size > 0) {
            try {
                List<Pair> pairs = myShepherd.getPairs(query, 50);
                size = pairs.size();
                for (int m = 0; m < size; m++) {
                    Pair mo = (Pair)pairs.get(m);
                    myShepherd.getPM().deletePersistent(mo);
                }
                myShepherd.commitDBTransaction();
                myShepherd.beginDBTransaction();
            } catch (Exception e) {
                System.out.println("I failed while constructing the workItems for a new scanTask.");
                e.printStackTrace();
                myShepherd.rollbackDBTransaction();
                myShepherd.beginDBTransaction();
            }
        }
        query.closeAll();
        myShepherd.commitDBTransaction();
        myShepherd.closeDBTransaction();
    }
}
