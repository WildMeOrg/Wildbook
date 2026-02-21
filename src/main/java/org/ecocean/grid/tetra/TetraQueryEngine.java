package org.ecocean.grid.tetra;

import org.ecocean.SuperSpot;
import org.ecocean.grid.EncounterLite;
import org.ecocean.grid.GridManager;
import org.ecocean.grid.MatchObject;
import org.ecocean.grid.VertexPointMatch;

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

        // Extract and normalize spot coordinates
        double[] spotX = new double[n];
        double[] spotY = new double[n];
        double maxCoord = 0;
        for (int i = 0; i < n; i++) {
            spotX[i] = querySpots.get(i).getCentroidX();
            spotY[i] = querySpots.get(i).getCentroidY();
            maxCoord = Math.max(maxCoord, Math.max(spotX[i], spotY[i]));
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
                acc, candidateEncId, querySpots, spotX, spotY,
                totalQueryPatterns, rightScan);
            results.add(mo);
        }

        // Sort by matchValue * adjustedMatchValue (same as MatchComparator)
        results.sort((a, b) -> Double.compare(
            b.getMatchValue() * b.getAdjustedMatchValue(),
            a.getMatchValue() * a.getAdjustedMatchValue()));

        return results;
    }

    /**
     * Builds a MatchObject compatible with existing display code.
     */
    private MatchObject buildMatchObject(VoteAccumulator acc,
                                          String candidateEncId,
                                          ArrayList<SuperSpot> querySpots,
                                          double[] normSpotX,
                                          double[] normSpotY,
                                          int totalQueryPatterns,
                                          boolean rightScan) {
        // Build spot-pair scores from vote accumulator
        Map<String, Integer> spotPairVotes = acc.getSpotPairVotes();
        ArrayList<VertexPointMatch> scores = new ArrayList<>();
        double totalPoints = 0;

        // Look up candidate encounter from matchGraph for catalog spot coords
        EncounterLite el = GridManager.getMatchGraphEncounterLiteEntry(candidateEncId);
        ArrayList catalogSpots = null;
        if (el != null) {
            catalogSpots = rightScan ? el.getRightSpots() : el.getSpots();
        }

        for (Map.Entry<String, Integer> pair : spotPairVotes.entrySet()) {
            String[] parts = pair.getKey().split(":");
            int qIdx = Integer.parseInt(parts[0]);
            int cIdx = Integer.parseInt(parts[1]);

            double newX = querySpots.get(qIdx).getCentroidX();
            double newY = querySpots.get(qIdx).getCentroidY();

            double oldX = 0, oldY = 0;
            if (catalogSpots != null && cIdx < catalogSpots.size()) {
                SuperSpot cs = (SuperSpot) catalogSpots.get(cIdx);
                oldX = cs.getCentroidX();
                oldY = cs.getCentroidY();
            }

            int points = pair.getValue();
            scores.add(new VertexPointMatch(newX, newY, oldX, oldY, points));
            totalPoints += points;
        }

        // Sort scores descending by points
        scores.sort((a, b) -> Integer.compare(b.getPoints(), a.getPoints()));

        // Build point breakdown string
        StringBuilder pb = new StringBuilder();
        for (VertexPointMatch vpm : scores) {
            if (pb.length() > 0) pb.append("|");
            pb.append(vpm.getPoints());
        }

        double matchValue = totalPoints;
        double adjustedMatchValue = (totalQueryPatterns > 0) ?
            totalPoints / totalQueryPatterns : 0;

        String indName = (el != null) ? el.getBelongsToMarkedIndividual() : "N/A";
        String date = (el != null) ? el.getDate() : "";

        return new MatchObject(
            indName,
            matchValue,
            adjustedMatchValue,
            acc.getVoteCount(),
            scores,
            candidateEncId,
            pb.toString(),
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
