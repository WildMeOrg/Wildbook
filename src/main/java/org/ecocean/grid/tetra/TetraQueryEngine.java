package org.ecocean.grid.tetra;

import org.ecocean.SuperSpot;
import org.ecocean.grid.EncounterLite;
import org.ecocean.grid.GridManager;
import org.ecocean.grid.MatchObject;

import java.util.*;
import java.util.logging.Logger;

/**
 * Performs TETRA hash-based pattern matching against the hash index.
 * Replaces the GridManager queue + ScanWorkItem pipeline with a single
 * synchronous function call.
 *
 * Pipeline:
 * 1. Generate C(n,4) patterns from query spots -> hash lookup (O(1) per pattern)
 * 2. Fine ratio filtering (reject hash collisions)
 * 3. Vote accumulation (count matches per candidate encounter)
 * 4. Build MatchObject results from top-K candidates
 */
public class TetraQueryEngine {
    private static final Logger log = Logger.getLogger(TetraQueryEngine.class.getName());

    private final TetraHashIndex leftIndex;
    private final TetraHashIndex rightIndex;
    private final TetraConfig config;

    public TetraQueryEngine(TetraHashIndex leftIndex,
                            TetraHashIndex rightIndex,
                            TetraConfig config) {
        this.leftIndex = leftIndex;
        this.rightIndex = rightIndex;
        this.config = config;
    }

    /**
     * Match a query encounter's spots against the catalog.
     *
     * @param querySpots  spots from the query encounter
     * @param rightScan   true for right-side patterns, false for left
     * @param queryEncId  encounter ID of the query (excluded from results)
     * @return sorted list of MatchObject results, compatible with existing display code
     */
    public List<MatchObject> match(ArrayList<SuperSpot> querySpots,
                                    boolean rightScan,
                                    String queryEncId) {
        TetraHashIndex index = rightScan ? rightIndex : leftIndex;
        int n = querySpots.size();
        if (n < config.getMinSpots()) return Collections.emptyList();

        // Extract and normalize spot coordinates (using absolute values
        // to handle negative coordinates correctly)
        double[] spotX = new double[n];
        double[] spotY = new double[n];
        double maxCoord = 0;
        for (int i = 0; i < n; i++) {
            spotX[i] = querySpots.get(i).getCentroidX();
            spotY[i] = querySpots.get(i).getCentroidY();
            maxCoord = Math.max(maxCoord,
                Math.max(Math.abs(spotX[i]), Math.abs(spotY[i])));
        }
        if (maxCoord > 0) {
            for (int i = 0; i < n; i++) {
                spotX[i] /= maxCoord;
                spotY[i] /= maxCoord;
            }
        }

        // Generate query patterns (sampled if C(n,4) > maxQueryPatterns)
        int numBins = index.getNumBins();
        int maxQP = config.getMaxQueryPatterns();
        List<TetraPattern> queryPatterns = generateQueryPatterns(
            spotX, spotY, numBins, maxQP);

        // Hash lookup and vote accumulation
        Map<String, VoteAccumulator> votes = new HashMap<>();

        for (TetraPattern qp : queryPatterns) {
            List<TetraHashEntry> hits;
            if (config.isToleranceEnabled()) {
                hits = index.lookupWithTolerance(
                    qp.getHashKey(), qp.getRatios());
            } else {
                hits = index.lookup(qp.getHashKey());
            }

            for (TetraHashEntry hit : hits) {
                if (hit.getEncounterId().equals(queryEncId)) continue;

                double dist = ratioDistance(qp.getRatios(), hit.getRatios());
                if (dist < config.getMaxRatioDistance()) {
                    votes.computeIfAbsent(hit.getEncounterId(),
                        x -> new VoteAccumulator()).addVote(qp, hit);
                }
            }
        }

        // Rank candidates by vote count, filter by minVotes
        List<Map.Entry<String, VoteAccumulator>> ranked = new ArrayList<>(votes.entrySet());
        ranked.removeIf(e -> e.getValue().getVoteCount() < config.getMinVotes());
        ranked.sort((a, b) -> Integer.compare(
            b.getValue().getVoteCount(), a.getValue().getVoteCount()));

        // Build MatchObject results for top-K
        int topK = Math.min(config.getTopK(), ranked.size());
        int totalQueryPatterns = binomial(n, 4);
        List<MatchObject> results = new ArrayList<>();

        for (int r = 0; r < topK; r++) {
            String candidateEncId = ranked.get(r).getKey();
            VoteAccumulator acc = ranked.get(r).getValue();

            MatchObject mo = buildMatchObject(
                acc, candidateEncId, totalQueryPatterns);
            results.add(mo);
        }

        // Sort by matchValue * adjustedMatchValue (same as MatchComparator)
        results.sort((a, b) -> Double.compare(
            b.getMatchValue() * b.getAdjustedMatchValue(),
            a.getMatchValue() * a.getAdjustedMatchValue()));

        return results;
    }

    /**
     * Builds a MatchObject from TETRA vote counts.
     * TETRA's edge-sorted hashing does not preserve spot correspondence,
     * so we only populate vote-count-based scores — no spot-pair matching.
     * Accurate spot correspondence is provided by the Groth refinement
     * step in HybridMatchServlet.
     */
    private MatchObject buildMatchObject(VoteAccumulator acc,
                                          String candidateEncId,
                                          int totalQueryPatterns) {
        EncounterLite el = GridManager.getMatchGraphEncounterLiteEntry(candidateEncId);
        String indName = (el != null) ? el.getBelongsToMarkedIndividual() : "N/A";
        String date = (el != null) ? el.getDate() : "";

        double matchValue = acc.getVoteCount();
        double adjustedMatchValue = (totalQueryPatterns > 0) ?
            matchValue / totalQueryPatterns : 0;

        return new MatchObject(
            indName,
            matchValue,
            adjustedMatchValue,
            acc.getVoteCount(),
            new ArrayList<>(),
            candidateEncId,
            String.valueOf(acc.getVoteCount()),
            new double[0],
            "Unknown",
            date,
            0.0
        );
    }

    /**
     * Generate query patterns, sampling via reservoir if C(n,4) > maxPatterns.
     */
    private List<TetraPattern> generateQueryPatterns(
            double[] spotX, double[] spotY, int numBins, int maxPatterns) {
        int n = spotX.length;
        long total = binomial(n, 4);

        if (total <= maxPatterns) {
            List<TetraPattern> patterns = new ArrayList<>((int) total);
            for (int i = 0; i < n - 3; i++)
                for (int j = i + 1; j < n - 2; j++)
                    for (int k = j + 1; k < n - 1; k++)
                        for (int l = k + 1; l < n; l++)
                            patterns.add(new TetraPattern(
                                spotX, spotY, i, j, k, l, numBins));
            return patterns;
        }

        // Reservoir sampling
        Random rng = new Random(Arrays.hashCode(spotX));
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

        return Arrays.asList(reservoir);
    }

    /**
     * Euclidean distance between query ratios (double[5]) and catalog ratios (float[5]).
     */
    private static double ratioDistance(double[] a, float[] b) {
        double sum = 0;
        for (int i = 0; i < 5; i++) {
            double d = a[i] - b[i];
            sum += d * d;
        }
        return Math.sqrt(sum);
    }

    /**
     * Computes C(n, k) = n! / (k! * (n-k)!)
     */
    static int binomial(int n, int k) {
        if (k > n || k < 0) return 0;
        if (k == 0 || k == n) return 1;
        int result = 1;
        for (int i = 0; i < k; i++) {
            result = result * (n - i) / (i + 1);
        }
        return result;
    }
}
