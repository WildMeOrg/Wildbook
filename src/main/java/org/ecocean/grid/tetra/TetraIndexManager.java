package org.ecocean.grid.tetra;

import org.ecocean.grid.EncounterLite;
import org.ecocean.grid.GridManager;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Singleton managing TETRA hash indices for left and right spot patterns.
 * Mirrors the GridManagerFactory pattern.
 *
 * The index is built incrementally via hooks in GridManager.addMatchGraphEntry()
 * during startup, and updated when encounters are added/removed at runtime.
 * A separate buildFromMatchGraph() method is available for bulk rebuilds.
 */
public class TetraIndexManager {
    private static final Logger log = Logger.getLogger(TetraIndexManager.class.getName());
    private static TetraIndexManager instance;

    private TetraHashIndex leftIndex;
    private TetraHashIndex rightIndex;
    private TetraConfig config;
    private TetraQueryEngine queryEngine;

    private TetraIndexManager() {
        config = new TetraConfig();
        leftIndex = new TetraHashIndex(config.getNumBins());
        rightIndex = new TetraHashIndex(config.getNumBins());
        queryEngine = new TetraQueryEngine(leftIndex, rightIndex, config);
    }

    public static synchronized TetraIndexManager getInstance() {
        if (instance == null) {
            instance = new TetraIndexManager();
        }
        return instance;
    }

    /**
     * Build index from the existing GridManager matchGraph.
     * Called at startup by TetraIndexCreationThread.
     */
    public void buildFromMatchGraph() {
        ConcurrentHashMap<String, EncounterLite> matchGraph = GridManager.getMatchGraph();
        int total = matchGraph.size();
        int count = 0;
        long start = System.currentTimeMillis();

        for (ConcurrentHashMap.Entry<String, EncounterLite> entry : matchGraph.entrySet()) {
            indexEncounterLite(entry.getKey(), entry.getValue());
            count++;
            if (count % 500 == 0) {
                log.info("TETRA index build progress: " + count + "/" + total + " encounters");
            }
        }

        long elapsed = System.currentTimeMillis() - start;
        log.info("TETRA index build complete in " + elapsed + "ms. Left: " +
            leftIndex.getNumIndexedEncounters() + " encounters, Right: " +
            rightIndex.getNumIndexedEncounters() + " encounters");
    }

    /**
     * Index a single encounter's spot patterns.
     * Called both during bulk build and incrementally from GridManager hooks.
     */
    public void indexEncounterLite(String encId, EncounterLite el) {
        // Index left spots
        ArrayList leftSpots = el.getSpots();
        if (leftSpots != null && leftSpots.size() >= config.getMinSpots()) {
            double[] sx = new double[leftSpots.size()];
            double[] sy = new double[leftSpots.size()];
            for (int i = 0; i < leftSpots.size(); i++) {
                org.ecocean.SuperSpot ss = (org.ecocean.SuperSpot) leftSpots.get(i);
                sx[i] = ss.getCentroidX();
                sy[i] = ss.getCentroidY();
            }
            normalize(sx, sy);
            leftIndex.indexEncounter(encId, sx, sy,
                config.getMaxPatternsPerEncounter());
        }

        // Index right spots
        ArrayList rightSpots = el.getRightSpots();
        if (rightSpots != null && rightSpots.size() >= config.getMinSpots()) {
            double[] sx = new double[rightSpots.size()];
            double[] sy = new double[rightSpots.size()];
            for (int i = 0; i < rightSpots.size(); i++) {
                org.ecocean.SuperSpot ss = (org.ecocean.SuperSpot) rightSpots.get(i);
                sx[i] = ss.getCentroidX();
                sy[i] = ss.getCentroidY();
            }
            normalize(sx, sy);
            rightIndex.indexEncounter(encId, sx, sy,
                config.getMaxPatternsPerEncounter());
        }
    }

    /**
     * Remove an encounter from both indices.
     */
    public void removeEncounter(String encId) {
        leftIndex.removeEncounter(encId);
        rightIndex.removeEncounter(encId);
    }

    /**
     * Normalize coordinates by dividing by the max value.
     * Same normalization used in EncounterLite.getPointsForBestMatch().
     */
    private void normalize(double[] x, double[] y) {
        double max = 0;
        for (double v : x) max = Math.max(max, v);
        for (double v : y) max = Math.max(max, v);
        if (max > 0) {
            for (int i = 0; i < x.length; i++) x[i] /= max;
            for (int i = 0; i < y.length; i++) y[i] /= max;
        }
    }

    public TetraQueryEngine getQueryEngine() { return queryEngine; }

    /**
     * Returns true if any encounters have been indexed.
     * During startup, the index builds incrementally via GridManager hooks.
     */
    public boolean isReady() {
        return leftIndex.getNumIndexedEncounters() > 0 ||
               rightIndex.getNumIndexedEncounters() > 0;
    }

    public TetraHashIndex getLeftIndex() { return leftIndex; }
    public TetraHashIndex getRightIndex() { return rightIndex; }
    public TetraConfig getConfig() { return config; }
}
