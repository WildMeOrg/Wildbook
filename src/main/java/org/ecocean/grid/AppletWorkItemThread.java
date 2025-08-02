package org.ecocean.grid;

import java.util.Vector;

/**
 * Test comment
 *
 * @author jholmber
 */
public class AppletWorkItemThread implements Runnable {
    public Thread threadObject;
    public ScanWorkItem swi;
    public Vector results;

    /**
     * Constructor to create a new thread object
     */
    public AppletWorkItemThread(ScanWorkItem swi, Vector results) {
        this.swi = swi;
        threadObject = new Thread(this, ("sharkGrid_" + swi.getUniqueNumber()));
        threadObject.setPriority(Thread.MIN_PRIORITY);
        this.results = results;
    }

    public void run() {
        // executeComparison();

        System.out.println("...in the run method of AppletWorkItemThread...");
        try {
            org.ecocean.grid.MatchObject thisResult;
            thisResult = swi.execute();
            System.out.println("...thisResult returned!!!");
            results.add(new ScanWorkItemResult(swi.getTaskIdentifier(), swi.getUniqueNumber(),
                thisResult));
            System.out.println("results size: " + results.size());
        } catch (OutOfMemoryError oome) {
            oome.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void nullThread() {
        swi = null;
        threadObject = null;
    }

    public void executeComparison() {
    }
}
