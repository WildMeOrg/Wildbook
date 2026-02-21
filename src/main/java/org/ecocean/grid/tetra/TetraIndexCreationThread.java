package org.ecocean.grid.tetra;

import java.util.logging.Logger;

/**
 * Builds the TETRA hash index at application startup.
 * Runs AFTER MatchGraphCreationThread has populated the matchGraph.
 * Launched from StartupWildbook alongside createMatchGraph().
 */
public class TetraIndexCreationThread implements Runnable {
    private static final Logger log = Logger.getLogger(TetraIndexCreationThread.class.getName());
    private volatile boolean finished = false;

    public TetraIndexCreationThread() {}

    @Override
    public void run() {
        log.info("Starting TetraIndexCreationThread");
        try {
            TetraIndexManager manager = TetraIndexManager.getInstance();
            manager.buildFromMatchGraph();
            finished = true;
            log.info("TetraIndexCreationThread completed successfully");
        } catch (Exception e) {
            log.severe("TetraIndexCreationThread failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public boolean isFinished() { return finished; }
}
