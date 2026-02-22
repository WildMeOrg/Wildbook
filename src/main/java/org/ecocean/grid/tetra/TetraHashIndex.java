package org.ecocean.grid.tetra;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

/**
 * In-memory hash index for TETRA pattern matching.
 *
 * Uses ConcurrentHashMap for thread safety (mirrors GridManager.matchGraph).
 * Separate indices are maintained for left and right spot patterns.
 *
 * Key space: numBins^5 (default 25^5 = 9,765,625 possible keys, sparse).
 */
public class TetraHashIndex implements java.io.Serializable {
    static final long serialVersionUID = 4918273650182739405L;
    private static final Logger log = Logger.getLogger(TetraHashIndex.class.getName());

    public static final int DEFAULT_NUM_BINS = 25;

    private final int numBins;
    private final ConcurrentHashMap<Long, List<TetraHashEntry>> table;
    private final ConcurrentHashMap<String, Integer> indexedEncounters;

    public TetraHashIndex() {
        this(DEFAULT_NUM_BINS);
    }

    public TetraHashIndex(int numBins) {
        this.numBins = numBins;
        this.table = new ConcurrentHashMap<>(1_000_000);
        this.indexedEncounters = new ConcurrentHashMap<>();
    }

    /**
     * Index all C(n,4) patterns for a single encounter's spots (no limit).
     * Thread-safe; re-indexes if encounter was already indexed.
     */
    public synchronized void indexEncounter(String encounterId,
                                             double[] spotX, double[] spotY) {
        indexEncounter(encounterId, spotX, spotY, Integer.MAX_VALUE);
    }

    /**
     * Index patterns for a single encounter's spots, with a cap on total patterns.
     * If C(n,4) exceeds maxPatterns, uses reservoir sampling for uniform selection.
     * Thread-safe; re-indexes if encounter was already indexed.
     */
    public synchronized void indexEncounter(String encounterId,
                                             double[] spotX, double[] spotY,
                                             int maxPatterns) {
        if (indexedEncounters.containsKey(encounterId)) {
            removeEncounter(encounterId);
        }

        int n = spotX.length;
        if (n < 4) return;

        // Calculate total number of C(n,4) patterns
        long totalPatterns = binomial(n, 4);

        if (totalPatterns <= maxPatterns) {
            // Index all patterns
            int patternCount = 0;
            for (int i = 0; i < n - 3; i++) {
                for (int j = i + 1; j < n - 2; j++) {
                    for (int k = j + 1; k < n - 1; k++) {
                        for (int l = k + 1; l < n; l++) {
                            TetraPattern pattern = new TetraPattern(
                                spotX, spotY, i, j, k, l, numBins);
                            TetraHashEntry entry = new TetraHashEntry(encounterId, pattern);
                            table.computeIfAbsent(pattern.getHashKey(),
                                x -> new CopyOnWriteArrayList<>()).add(entry);
                            patternCount++;
                        }
                    }
                }
            }
            indexedEncounters.put(encounterId, patternCount);
        } else {
            // Reservoir sampling: uniformly select maxPatterns from all C(n,4)
            Random rng = new Random(encounterId.hashCode());
            TetraPattern[] reservoir = new TetraPattern[maxPatterns];
            int seen = 0;

            for (int i = 0; i < n - 3; i++) {
                for (int j = i + 1; j < n - 2; j++) {
                    for (int k = j + 1; k < n - 1; k++) {
                        for (int l = k + 1; l < n; l++) {
                            if (seen < maxPatterns) {
                                reservoir[seen] = new TetraPattern(
                                    spotX, spotY, i, j, k, l, numBins);
                            } else {
                                int slot = rng.nextInt(seen + 1);
                                if (slot < maxPatterns) {
                                    reservoir[slot] = new TetraPattern(
                                        spotX, spotY, i, j, k, l, numBins);
                                }
                            }
                            seen++;
                        }
                    }
                }
            }

            int patternCount = Math.min(seen, maxPatterns);
            for (int idx = 0; idx < patternCount; idx++) {
                TetraPattern pattern = reservoir[idx];
                TetraHashEntry entry = new TetraHashEntry(encounterId, pattern);
                table.computeIfAbsent(pattern.getHashKey(),
                    x -> new CopyOnWriteArrayList<>()).add(entry);
            }
            indexedEncounters.put(encounterId, patternCount);
        }
    }

    private static long binomial(int n, int k) {
        if (k > n) return 0;
        if (k == 0 || k == n) return 1;
        long result = 1;
        for (int i = 0; i < k; i++) {
            result = result * (n - i) / (i + 1);
        }
        return result;
    }

    /**
     * Remove all entries for an encounter from the index.
     */
    public synchronized void removeEncounter(String encounterId) {
        if (!indexedEncounters.containsKey(encounterId)) return;
        for (List<TetraHashEntry> bucket : table.values()) {
            bucket.removeIf(e -> e.getEncounterId().equals(encounterId));
        }
        indexedEncounters.remove(encounterId);
    }

    /**
     * Look up entries for an exact hash key.
     */
    public List<TetraHashEntry> lookup(long hashKey) {
        List<TetraHashEntry> result = table.get(hashKey);
        // CopyOnWriteArrayList iterators are snapshot-safe, but return a copy
        // to prevent callers from holding a reference to the internal bucket
        return (result != null) ? new ArrayList<>(result) : new ArrayList<>();
    }

    /**
     * Look up with tolerance: checks the exact bin plus all neighboring bins
     * (up to 3^5 = 243 combinations where each of the 5 ratio dimensions
     * is checked at bin-1, bin, and bin+1).
     */
    public List<TetraHashEntry> lookupWithTolerance(long hashKey, double[] exactRatios) {
        List<TetraHashEntry> candidates = new ArrayList<>();
        long[] neighborKeys = computeNeighborKeys(exactRatios);
        for (long nk : neighborKeys) {
            List<TetraHashEntry> bucket = table.get(nk);
            if (bucket != null) {
                candidates.addAll(bucket);
            }
        }
        return candidates;
    }

    /**
     * Generates all neighboring hash keys by varying each ratio dimension by +/-1 bin.
     */
    private long[] computeNeighborKeys(double[] ratios) {
        int[] bins = new int[5];
        for (int i = 0; i < 5; i++) {
            bins[i] = (int) (ratios[i] * numBins);
            if (bins[i] >= numBins) bins[i] = numBins - 1;
            if (bins[i] < 0) bins[i] = 0;
        }
        List<Long> keys = new ArrayList<>();
        generateNeighborKeys(bins, 0, new int[5], keys);
        return keys.stream().mapToLong(Long::longValue).toArray();
    }

    private void generateNeighborKeys(int[] center, int dim,
                                       int[] current, List<Long> keys) {
        if (dim == 5) {
            long key = 0;
            for (int i = 0; i < 5; i++) {
                key = key * numBins + current[i];
            }
            keys.add(key);
            return;
        }
        for (int delta = -1; delta <= 1; delta++) {
            int bin = center[dim] + delta;
            if (bin >= 0 && bin < numBins) {
                current[dim] = bin;
                generateNeighborKeys(center, dim + 1, current, keys);
            }
        }
    }

    public int getNumBins() { return numBins; }

    public int getNumIndexedEncounters() { return indexedEncounters.size(); }

    public boolean isEncounterIndexed(String id) {
        return indexedEncounters.containsKey(id);
    }

    public Set<String> getIndexedEncounterIds() {
        return indexedEncounters.keySet();
    }

    public int getTableSize() { return table.size(); }
}
