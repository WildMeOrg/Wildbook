package org.ecocean.grid.tetra;

import org.ecocean.SuperSpot;
import org.ecocean.grid.EncounterLite;
import org.ecocean.grid.MatchObject;
import org.ecocean.grid.VertexPointMatch;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import static org.junit.jupiter.api.Assertions.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Benchmark comparing TETRA hash-based matching vs Modified Groth algorithm.
 *
 * Loads real whale shark spot data from tetra_benchmark_spots.csv
 * (exported from sharkbook.ai) and measures:
 * - Rank-1, Rank-5, Rank-10, Rank-20, Rank-50, Rank-100 accuracy
 * - Per-query timing
 * - Index build time (TETRA only)
 * - Hybrid TETRA+Groth performance
 *
 * Run with: mvn test -Dtest=org.ecocean.grid.tetra.TetraBenchmarkTest -Dtetra.benchmark.csv=tetra_benchmark_spots.csv
 *
 * NOTE: This test is designed to be run manually (not in CI) since it
 * requires the benchmark CSV file and takes several minutes to complete.
 * It is automatically skipped if the CSV file is not present.
 */
@EnabledIf("csvFileExists")
class TetraBenchmarkTest {

    private static final String CSV_PATH = System.getProperty(
        "tetra.benchmark.csv", "tetra_benchmark_spots.csv");

    static boolean csvFileExists() {
        return new File(CSV_PATH).exists();
    }

    // Groth optimized parameters (from Kingen & Holmberg 2019)
    private static final double GROTH_EPSILON = 0.008;
    private static final double GROTH_R = 49.8;
    private static final double GROTH_SIZELIM = 0.998;
    private static final double GROTH_MAX_ROTATION = 12.33;
    private static final double GROTH_C = 0.998;
    private static final boolean GROTH_SECOND_RUN = true;

    // Number of query encounters to test (random sample from dataset)
    private static final int NUM_QUERIES = 100;

    // Rank levels to evaluate
    private static final int[] RANK_LEVELS = {1, 5, 10, 20, 50, 100};

    // Data structures
    private static Map<String, ArrayList<SuperSpot>> encounterSpots;   // encId -> spots
    private static Map<String, String> encounterToIndividual;           // encId -> indId
    private static Map<String, List<String>> individualToEncounters;    // indId -> list of encIds
    private static List<String> allEncounterIds;

    @BeforeAll
    static void loadData() throws IOException {
        encounterSpots = new LinkedHashMap<>();
        encounterToIndividual = new HashMap<>();
        individualToEncounters = new HashMap<>();

        try (BufferedReader br = new BufferedReader(new FileReader(CSV_PATH))) {
            String header = br.readLine(); // skip header
            String line;
            while ((line = br.readLine()) != null) {
                String[] fields = parseCsvLine(line);
                if (fields.length < 8) continue;

                String encId = fields[0];
                String indId = fields[1];
                double spotX = Double.parseDouble(fields[5]);
                double spotY = Double.parseDouble(fields[6]);

                encounterSpots.computeIfAbsent(encId, k -> new ArrayList<>())
                    .add(new SuperSpot(spotX, spotY));

                encounterToIndividual.putIfAbsent(encId, indId);
                individualToEncounters.computeIfAbsent(indId, k -> new ArrayList<>());
                if (!individualToEncounters.get(indId).contains(encId)) {
                    individualToEncounters.get(indId).add(encId);
                }
            }
        }

        allEncounterIds = new ArrayList<>(encounterSpots.keySet());

        // Compute stats
        int[] spotCounts = encounterSpots.values().stream()
            .mapToInt(ArrayList::size).toArray();
        Arrays.sort(spotCounts);
        int median = spotCounts[spotCounts.length / 2];

        System.out.println("=== Benchmark Data Loaded ===");
        System.out.println("Encounters: " + allEncounterIds.size());
        System.out.println("Individuals: " + individualToEncounters.size());
        System.out.println("Total spots: " + Arrays.stream(spotCounts).sum());
        System.out.println("Spots per encounter: median=" + median +
            " min=" + spotCounts[0] + " max=" + spotCounts[spotCounts.length - 1]);
        System.out.println("Avg encounters per individual: " + String.format("%.1f",
            allEncounterIds.size() / (double) individualToEncounters.size()));
        System.out.println();
    }

    @Test
    void benchmarkTetra() {
        System.out.println("========================================");
        System.out.println("     TETRA HASH-BASED MATCHING");
        System.out.println("========================================\n");

        // Build TETRA index - best config from tuning
        TetraConfig config = new TetraConfig();
        config.setTopK(200);
        config.setMinVotes(1);
        config.setToleranceEnabled(false);
        config.setMaxRatioDistance(0.1);
        config.setNumBins(25);
        config.setMaxPatternsPerEncounter(500);
        config.setMaxQueryPatterns(1000);

        TetraHashIndex leftIndex = new TetraHashIndex(config.getNumBins());
        TetraHashIndex rightIndex = new TetraHashIndex(config.getNumBins());

        System.out.println("Config: numBins=" + config.getNumBins() +
            " maxPatterns=" + config.getMaxPatternsPerEncounter() +
            " maxQuery=" + config.getMaxQueryPatterns() +
            " tolerance=" + config.isToleranceEnabled() +
            " maxRatioDist=" + config.getMaxRatioDistance());
        System.out.println("Building TETRA index for " + allEncounterIds.size() + " encounters...");
        long buildStart = System.currentTimeMillis();

        for (String encId : allEncounterIds) {
            ArrayList<SuperSpot> spots = encounterSpots.get(encId);
            if (spots.size() < 4) continue;

            double[] sx = new double[spots.size()];
            double[] sy = new double[spots.size()];
            for (int i = 0; i < spots.size(); i++) {
                sx[i] = spots.get(i).getCentroidX();
                sy[i] = spots.get(i).getCentroidY();
            }
            normalize(sx, sy);
            leftIndex.indexEncounter(encId, sx, sy,
                config.getMaxPatternsPerEncounter());
        }

        long buildTime = System.currentTimeMillis() - buildStart;
        System.out.println("TETRA index built in " + buildTime + " ms");
        System.out.println("Indexed encounters: " + leftIndex.getNumIndexedEncounters());
        System.out.println("Hash table buckets used: " + leftIndex.getTableSize());
        System.out.println();

        // Run queries
        TetraQueryEngine engine = new TetraQueryEngine(leftIndex, rightIndex, config);
        List<String> queryEncIds = selectQueryEncounters();

        int[] rankHits = new int[RANK_LEVELS.length];
        long totalQueryTime = 0;
        int queriesRun = 0;
        int notFound = 0;

        for (String queryEncId : queryEncIds) {
            ArrayList<SuperSpot> querySpots = encounterSpots.get(queryEncId);
            String trueIndId = encounterToIndividual.get(queryEncId);

            long qStart = System.nanoTime();
            List<MatchObject> results = engine.match(querySpots, false, queryEncId);
            long qTime = System.nanoTime() - qStart;
            totalQueryTime += qTime;
            queriesRun++;

            int correctRank = findCorrectRank(results, queryEncId, trueIndId);

            if (correctRank == -1) {
                notFound++;
            }
            for (int r = 0; r < RANK_LEVELS.length; r++) {
                if (correctRank >= 1 && correctRank <= RANK_LEVELS[r]) {
                    rankHits[r]++;
                }
            }
        }

        printResults("TETRA", queriesRun, rankHits, totalQueryTime, buildTime, notFound);
    }

    @Test
    void benchmarkHybrid() {
        System.out.println("========================================");
        System.out.println("     HYBRID: TETRA PRE-FILTER + GROTH");
        System.out.println("========================================\n");

        // Step 1: Build TETRA index (same as benchmarkTetra)
        TetraConfig config = new TetraConfig();
        config.setTopK(200);
        config.setMinVotes(1);
        config.setToleranceEnabled(false);
        config.setMaxRatioDistance(0.1);
        config.setNumBins(25);
        config.setMaxPatternsPerEncounter(500);
        config.setMaxQueryPatterns(1000);

        TetraHashIndex leftIndex = new TetraHashIndex(config.getNumBins());
        TetraHashIndex rightIndex = new TetraHashIndex(config.getNumBins());

        // Also build EncounterLite objects for Groth verification
        Map<String, EncounterLite> matchGraph = new LinkedHashMap<>();

        System.out.println("Building TETRA index + EncounterLite objects...");
        long buildStart = System.currentTimeMillis();

        for (String encId : allEncounterIds) {
            ArrayList<SuperSpot> spots = encounterSpots.get(encId);

            if (spots.size() >= 4) {
                double[] sx = new double[spots.size()];
                double[] sy = new double[spots.size()];
                for (int i = 0; i < spots.size(); i++) {
                    sx[i] = spots.get(i).getCentroidX();
                    sy[i] = spots.get(i).getCentroidY();
                }
                normalize(sx, sy);
                leftIndex.indexEncounter(encId, sx, sy,
                    config.getMaxPatternsPerEncounter());
            }

            if (spots.size() >= 3) {
                EncounterLite el = new EncounterLite();
                el.processLeftSpots(spots);
                matchGraph.put(encId, el);
            }
        }

        long buildTime = System.currentTimeMillis() - buildStart;
        System.out.println("Build complete in " + buildTime + " ms");

        // Step 2: For each query, use TETRA to get top-100, then Groth-verify
        TetraQueryEngine engine = new TetraQueryEngine(leftIndex, rightIndex, config);
        List<String> queryEncIds = selectQueryEncounters();

        int[] rankHits = new int[RANK_LEVELS.length];
        long totalQueryTime = 0;
        int queriesRun = 0;
        int notFound = 0;

        for (String queryEncId : queryEncIds) {
            ArrayList<SuperSpot> querySpots = encounterSpots.get(queryEncId);
            String trueIndId = encounterToIndividual.get(queryEncId);
            SuperSpot[] queryArray = querySpots.toArray(new SuperSpot[0]);

            long qStart = System.nanoTime();

            // Phase 1: TETRA pre-filter -> top 100 candidates
            List<MatchObject> tetraResults = engine.match(querySpots, false, queryEncId);
            Set<String> candidates = new LinkedHashSet<>();
            for (int i = 0; i < Math.min(100, tetraResults.size()); i++) {
                candidates.add(tetraResults.get(i).getEncounterNumber());
            }

            // Phase 2: Groth-verify only the candidates
            List<MatchObject> grothResults = new ArrayList<>();
            for (String candEncId : candidates) {
                EncounterLite el = matchGraph.get(candEncId);
                if (el == null) continue;

                try {
                    MatchObject mo = el.getPointsForBestMatch(
                        queryArray, GROTH_EPSILON, GROTH_R, GROTH_SIZELIM,
                        GROTH_MAX_ROTATION, GROTH_C, GROTH_SECOND_RUN, false);
                    if (mo != null && mo.getMatchValue() > 0) {
                        mo.encounterNumber = candEncId;
                        mo.individualName = encounterToIndividual.getOrDefault(
                            candEncId, "N/A");
                        grothResults.add(mo);
                    }
                } catch (Exception e) {
                    // Skip failed comparisons
                }
            }

            long qTime = System.nanoTime() - qStart;
            totalQueryTime += qTime;
            queriesRun++;

            // Sort Groth results
            grothResults.sort((a, b) -> Double.compare(
                b.getMatchValue() * b.getAdjustedMatchValue(),
                a.getMatchValue() * a.getAdjustedMatchValue()));

            int correctRank = findCorrectRank(grothResults, queryEncId, trueIndId);

            if (correctRank == -1) notFound++;
            for (int r = 0; r < RANK_LEVELS.length; r++) {
                if (correctRank >= 1 && correctRank <= RANK_LEVELS[r]) {
                    rankHits[r]++;
                }
            }

            if (queriesRun % 10 == 0) {
                System.out.println("  Hybrid progress: " + queriesRun + "/" +
                    queryEncIds.size() + " queries (" +
                    String.format("%.0f", totalQueryTime / 1_000_000.0 / queriesRun) + " ms avg)");
            }
        }

        printResults("HYBRID (TETRA top-100 -> Groth)",
            queriesRun, rankHits, totalQueryTime, buildTime, notFound);
    }

    @Test
    void benchmarkGroth() {
        System.out.println("========================================");
        System.out.println("     MODIFIED GROTH ALGORITHM");
        System.out.println("========================================\n");

        // Build EncounterLite objects (Groth's "index" is the matchGraph)
        Map<String, EncounterLite> matchGraph = new LinkedHashMap<>();

        System.out.println("Building EncounterLite objects for " +
            allEncounterIds.size() + " encounters...");
        long buildStart = System.currentTimeMillis();

        for (String encId : allEncounterIds) {
            ArrayList<SuperSpot> spots = encounterSpots.get(encId);
            if (spots.size() < 3) continue;

            EncounterLite el = new EncounterLite();
            el.processLeftSpots(spots);
            matchGraph.put(encId, el);
        }

        long buildTime = System.currentTimeMillis() - buildStart;
        System.out.println("EncounterLites built in " + buildTime + " ms");
        System.out.println("Encounters in graph: " + matchGraph.size());
        System.out.println();

        // Run queries (same encounters as TETRA test)
        List<String> queryEncIds = selectQueryEncounters();

        int[] rankHits = new int[RANK_LEVELS.length];
        long totalQueryTime = 0;
        int queriesRun = 0;
        int notFound = 0;

        for (String queryEncId : queryEncIds) {
            ArrayList<SuperSpot> querySpots = encounterSpots.get(queryEncId);
            String trueIndId = encounterToIndividual.get(queryEncId);

            SuperSpot[] queryArray = querySpots.toArray(new SuperSpot[0]);

            // Run Groth against ALL catalog encounters
            List<MatchObject> results = new ArrayList<>();

            long qStart = System.nanoTime();
            for (Map.Entry<String, EncounterLite> entry : matchGraph.entrySet()) {
                if (entry.getKey().equals(queryEncId)) continue;

                try {
                    MatchObject mo = entry.getValue().getPointsForBestMatch(
                        queryArray, GROTH_EPSILON, GROTH_R, GROTH_SIZELIM,
                        GROTH_MAX_ROTATION, GROTH_C, GROTH_SECOND_RUN, false);
                    if (mo != null && mo.getMatchValue() > 0) {
                        mo.encounterNumber = entry.getKey();
                        mo.individualName = encounterToIndividual.getOrDefault(
                            entry.getKey(), "N/A");
                        results.add(mo);
                    }
                } catch (Exception e) {
                    // Skip failed comparisons
                }
            }
            long qTime = System.nanoTime() - qStart;
            totalQueryTime += qTime;
            queriesRun++;

            // Sort by matchValue * adjustedMatchValue (same as MatchComparator)
            results.sort((a, b) -> Double.compare(
                b.getMatchValue() * b.getAdjustedMatchValue(),
                a.getMatchValue() * a.getAdjustedMatchValue()));

            int correctRank = findCorrectRank(results, queryEncId, trueIndId);

            if (correctRank == -1) notFound++;
            for (int r = 0; r < RANK_LEVELS.length; r++) {
                if (correctRank >= 1 && correctRank <= RANK_LEVELS[r]) {
                    rankHits[r]++;
                }
            }

            if (queriesRun % 10 == 0) {
                System.out.println("  Groth progress: " + queriesRun + "/" +
                    queryEncIds.size() + " queries (" +
                    (totalQueryTime / 1_000_000 / queriesRun) + " ms avg)");
            }
        }

        printResults("GROTH", queriesRun, rankHits, totalQueryTime, buildTime, notFound);
    }

    /**
     * Select query encounters: pick one encounter from each of NUM_QUERIES
     * different individuals. This ensures diverse test coverage.
     */
    private List<String> selectQueryEncounters() {
        Random rng = new Random(42); // fixed seed for reproducibility
        List<String> queries = new ArrayList<>();
        List<String> individuals = new ArrayList<>(individualToEncounters.keySet());
        Collections.sort(individuals); // deterministic order

        for (int i = 0; i < Math.min(NUM_QUERIES, individuals.size()); i++) {
            List<String> encIds = individualToEncounters.get(individuals.get(i));
            // Pick a random encounter for this individual
            queries.add(encIds.get(rng.nextInt(encIds.size())));
        }
        return queries;
    }

    /**
     * Find the rank at which the correct individual first appears.
     * Returns -1 if not found in results.
     */
    private int findCorrectRank(List<MatchObject> results, String queryEncId,
                                 String trueIndId) {
        // Get all encounter IDs belonging to the true individual (excluding query)
        Set<String> trueEncounters = new HashSet<>(
            individualToEncounters.getOrDefault(trueIndId, Collections.emptyList()));
        trueEncounters.remove(queryEncId);

        for (int i = 0; i < results.size(); i++) {
            String resultEncId = results.get(i).getEncounterNumber();
            if (trueEncounters.contains(resultEncId)) {
                return i + 1; // 1-indexed rank
            }
        }
        return -1; // not found
    }

    private void printResults(String algorithmName, int queries,
                               int[] rankHits,
                               long totalQueryTimeNanos, long buildTimeMs,
                               int notFound) {
        double avgQueryMs = (totalQueryTimeNanos / 1_000_000.0) / queries;

        System.out.println("\n========================================");
        System.out.println("     " + algorithmName + " RESULTS");
        System.out.println("========================================");
        System.out.println("Queries run:     " + queries);
        System.out.println("Build time:      " + buildTimeMs + " ms");
        System.out.println("Avg query time:  " + String.format("%.1f", avgQueryMs) + " ms");
        System.out.println("Total query time:" + (totalQueryTimeNanos / 1_000_000) + " ms");
        System.out.println("Not found:       " + notFound + "/" + queries);
        System.out.println();
        for (int r = 0; r < RANK_LEVELS.length; r++) {
            System.out.println(String.format("Rank-%-3d recall:  %d/%d (%.1f%%)",
                RANK_LEVELS[r], rankHits[r], queries,
                100.0 * rankHits[r] / queries));
        }
        System.out.println("========================================\n");
    }

    private static void normalize(double[] x, double[] y) {
        double max = 0;
        for (double v : x) max = Math.max(max, v);
        for (double v : y) max = Math.max(max, v);
        if (max > 0) {
            for (int i = 0; i < x.length; i++) x[i] /= max;
            for (int i = 0; i < y.length; i++) y[i] /= max;
        }
    }

    // =========================================================================
    //  HYBRID SWEEP: Systematic parameter exploration
    // =========================================================================

    /**
     * Comprehensive parameter sweep to find optimal TETRA configuration for
     * hybrid TETRA+Groth matching.
     *
     * Part A: TETRA-only sweep measuring Rank-100 recall (the bottleneck metric).
     *   - Primary grid: maxRatioDistance x numBins (query-time vs hash granularity)
     *   - Secondary grid: maxPatternsPerEncounter x maxQueryPatterns (sampling density)
     *   - Index is reused across maxRatioDistance values (only rebuilt when numBins
     *     or maxPatternsPerEncounter changes).
     *
     * Part B: Hybrid sweep with best TETRA config, varying candidate pool size
     *   passed to Groth verification: [50, 100, 200, 500].
     *
     * Run with:
     *   mvn test -Dtest="org.ecocean.grid.tetra.TetraBenchmarkTest#benchmarkHybridSweep" \
     *       -Dtetra.benchmark.csv=tetra_benchmark_spots.csv
     */
    @Test
    void benchmarkHybridSweep() {
        System.out.println("================================================================");
        System.out.println("     HYBRID PARAMETER SWEEP");
        System.out.println("     Goal: TETRA Rank-100 recall > 90%, hybrid < 5s/query");
        System.out.println("================================================================\n");

        List<String> queryEncIds = selectQueryEncounters();

        // =====================================================================
        // PART A: TETRA parameter sweep (TETRA-only, Rank-100 recall focus)
        // =====================================================================
        System.out.println("--- PART A: TETRA Parameter Sweep (maxRatioDistance x numBins) ---\n");

        double[] maxRatioDistances = {0.05, 0.1, 0.15, 0.2};
        int[] numBinsValues = {15, 20, 25, 30};
        int defaultMaxPatterns = 500;
        int defaultMaxQuery = 1000;
        int sweepTopK = 500; // return plenty of candidates for recall measurement
        int sweepMinVotes = 1;

        // Collect results for the table
        List<SweepResult> partAResults = new ArrayList<>();

        // Outer loop: numBins (requires index rebuild)
        for (int numBins : numBinsValues) {
            // Build index once per numBins value
            System.out.println("Building index: numBins=" + numBins +
                " maxPatterns=" + defaultMaxPatterns + " ...");
            long buildStart = System.currentTimeMillis();

            TetraHashIndex leftIndex = new TetraHashIndex(numBins);
            TetraHashIndex rightIndex = new TetraHashIndex(numBins);

            for (String encId : allEncounterIds) {
                ArrayList<SuperSpot> spots = encounterSpots.get(encId);
                if (spots.size() < 4) continue;
                double[] sx = new double[spots.size()];
                double[] sy = new double[spots.size()];
                for (int i = 0; i < spots.size(); i++) {
                    sx[i] = spots.get(i).getCentroidX();
                    sy[i] = spots.get(i).getCentroidY();
                }
                normalize(sx, sy);
                leftIndex.indexEncounter(encId, sx, sy, defaultMaxPatterns);
            }

            long buildTime = System.currentTimeMillis() - buildStart;
            System.out.println("  Index built in " + buildTime + " ms, " +
                leftIndex.getNumIndexedEncounters() + " encounters, " +
                leftIndex.getTableSize() + " buckets");

            // Inner loop: maxRatioDistance (query-time only, reuses index)
            for (double maxRatioDist : maxRatioDistances) {
                TetraConfig config = new TetraConfig();
                config.setNumBins(numBins);
                config.setMaxPatternsPerEncounter(defaultMaxPatterns);
                config.setMaxQueryPatterns(defaultMaxQuery);
                config.setMaxRatioDistance(maxRatioDist);
                config.setTopK(sweepTopK);
                config.setMinVotes(sweepMinVotes);
                config.setToleranceEnabled(false);

                TetraQueryEngine engine = new TetraQueryEngine(
                    leftIndex, rightIndex, config);

                SweepResult result = runTetraSweepQueries(
                    engine, queryEncIds, config, buildTime);
                partAResults.add(result);
            }
        }

        // Print Part A results table
        printSweepTable("PART A: maxRatioDistance x numBins (TETRA-only)",
            partAResults,
            "numBins", "maxRatioDist", "maxPat/enc", "maxQPat",
            true);

        // Find the best numBins and maxRatioDistance by Rank-100 recall,
        // breaking ties by query speed
        SweepResult bestA = findBestByRank100(partAResults);
        System.out.println(">> Best from Part A: numBins=" + bestA.numBins +
            " maxRatioDist=" + bestA.maxRatioDistance +
            " -> Rank-100=" + String.format("%.1f%%", bestA.rank100Pct) +
            " (" + String.format("%.0f", bestA.avgQueryMs) + " ms/query)\n");

        // =====================================================================
        // PART A2: Pattern density sweep with best numBins + maxRatioDistance
        // =====================================================================
        System.out.println("--- PART A2: Pattern Density Sweep " +
            "(maxPatternsPerEncounter x maxQueryPatterns) ---");
        System.out.println("     Using numBins=" + bestA.numBins +
            " maxRatioDistance=" + bestA.maxRatioDistance + "\n");

        int[] maxPatternsValues = {500, 1000, 2000};
        int[] maxQueryValues = {500, 1000, 2000};

        List<SweepResult> partA2Results = new ArrayList<>();

        // Outer loop: maxPatternsPerEncounter (requires index rebuild)
        for (int maxPat : maxPatternsValues) {
            System.out.println("Building index: numBins=" + bestA.numBins +
                " maxPatterns=" + maxPat + " ...");
            long buildStart = System.currentTimeMillis();

            TetraHashIndex leftIndex = new TetraHashIndex(bestA.numBins);
            TetraHashIndex rightIndex = new TetraHashIndex(bestA.numBins);

            for (String encId : allEncounterIds) {
                ArrayList<SuperSpot> spots = encounterSpots.get(encId);
                if (spots.size() < 4) continue;
                double[] sx = new double[spots.size()];
                double[] sy = new double[spots.size()];
                for (int i = 0; i < spots.size(); i++) {
                    sx[i] = spots.get(i).getCentroidX();
                    sy[i] = spots.get(i).getCentroidY();
                }
                normalize(sx, sy);
                leftIndex.indexEncounter(encId, sx, sy, maxPat);
            }

            long buildTime = System.currentTimeMillis() - buildStart;
            System.out.println("  Index built in " + buildTime + " ms, " +
                leftIndex.getNumIndexedEncounters() + " encounters, " +
                leftIndex.getTableSize() + " buckets");

            // Inner loop: maxQueryPatterns (query-time only)
            for (int maxQ : maxQueryValues) {
                TetraConfig config = new TetraConfig();
                config.setNumBins(bestA.numBins);
                config.setMaxPatternsPerEncounter(maxPat);
                config.setMaxQueryPatterns(maxQ);
                config.setMaxRatioDistance(bestA.maxRatioDistance);
                config.setTopK(sweepTopK);
                config.setMinVotes(sweepMinVotes);
                config.setToleranceEnabled(false);

                TetraQueryEngine engine = new TetraQueryEngine(
                    leftIndex, rightIndex, config);

                SweepResult result = runTetraSweepQueries(
                    engine, queryEncIds, config, buildTime);
                partA2Results.add(result);
            }
        }

        printSweepTable("PART A2: maxPatternsPerEncounter x maxQueryPatterns",
            partA2Results,
            "numBins", "maxRatioDist", "maxPat/enc", "maxQPat",
            true);

        // Find overall best TETRA config
        List<SweepResult> allTetraResults = new ArrayList<>(partAResults);
        allTetraResults.addAll(partA2Results);
        SweepResult bestTetra = findBestByRank100(allTetraResults);
        System.out.println(">> Overall best TETRA config:");
        System.out.println("   numBins=" + bestTetra.numBins +
            " maxRatioDistance=" + bestTetra.maxRatioDistance +
            " maxPatternsPerEncounter=" + bestTetra.maxPatternsPerEncounter +
            " maxQueryPatterns=" + bestTetra.maxQueryPatterns);
        System.out.println("   Rank-1=" + String.format("%.1f%%", bestTetra.rank1Pct) +
            " Rank-100=" + String.format("%.1f%%", bestTetra.rank100Pct) +
            " Avg=" + String.format("%.0f", bestTetra.avgQueryMs) + " ms" +
            " NotFound=" + bestTetra.notFound + "\n");

        // =====================================================================
        // PART B: Hybrid sweep with best TETRA config
        // =====================================================================
        System.out.println("--- PART B: Hybrid Sweep (TETRA candidates -> Groth) ---");
        System.out.println("     TETRA config: numBins=" + bestTetra.numBins +
            " maxRatioDist=" + bestTetra.maxRatioDistance +
            " maxPat=" + bestTetra.maxPatternsPerEncounter +
            " maxQ=" + bestTetra.maxQueryPatterns + "\n");

        int[] candidatePoolSizes = {50, 100, 200, 500};

        // Build the TETRA index with best config
        System.out.println("Building TETRA index + EncounterLite objects for hybrid...");
        long hybridBuildStart = System.currentTimeMillis();

        TetraHashIndex bestLeftIndex = new TetraHashIndex(bestTetra.numBins);
        TetraHashIndex bestRightIndex = new TetraHashIndex(bestTetra.numBins);
        Map<String, EncounterLite> matchGraph = new LinkedHashMap<>();

        for (String encId : allEncounterIds) {
            ArrayList<SuperSpot> spots = encounterSpots.get(encId);

            if (spots.size() >= 4) {
                double[] sx = new double[spots.size()];
                double[] sy = new double[spots.size()];
                for (int i = 0; i < spots.size(); i++) {
                    sx[i] = spots.get(i).getCentroidX();
                    sy[i] = spots.get(i).getCentroidY();
                }
                normalize(sx, sy);
                bestLeftIndex.indexEncounter(encId, sx, sy,
                    bestTetra.maxPatternsPerEncounter);
            }

            if (spots.size() >= 3) {
                EncounterLite el = new EncounterLite();
                el.processLeftSpots(spots);
                matchGraph.put(encId, el);
            }
        }

        long hybridBuildTime = System.currentTimeMillis() - hybridBuildStart;
        System.out.println("Build complete in " + hybridBuildTime + " ms\n");

        TetraConfig bestConfig = new TetraConfig();
        bestConfig.setNumBins(bestTetra.numBins);
        bestConfig.setMaxPatternsPerEncounter(bestTetra.maxPatternsPerEncounter);
        bestConfig.setMaxQueryPatterns(bestTetra.maxQueryPatterns);
        bestConfig.setMaxRatioDistance(bestTetra.maxRatioDistance);
        bestConfig.setTopK(sweepTopK);
        bestConfig.setMinVotes(sweepMinVotes);
        bestConfig.setToleranceEnabled(false);

        TetraQueryEngine bestEngine = new TetraQueryEngine(
            bestLeftIndex, bestRightIndex, bestConfig);

        List<SweepResult> partBResults = new ArrayList<>();

        for (int poolSize : candidatePoolSizes) {
            System.out.println("Running hybrid with top-" + poolSize +
                " TETRA candidates -> Groth...");

            int[] rankHits = new int[RANK_LEVELS.length];
            long totalQueryTime = 0;
            int queriesRun = 0;
            int notFound = 0;

            for (String queryEncId : queryEncIds) {
                ArrayList<SuperSpot> querySpots = encounterSpots.get(queryEncId);
                String trueIndId = encounterToIndividual.get(queryEncId);
                SuperSpot[] queryArray = querySpots.toArray(new SuperSpot[0]);

                long qStart = System.nanoTime();

                // Phase 1: TETRA pre-filter
                List<MatchObject> tetraResults = bestEngine.match(
                    querySpots, false, queryEncId);
                Set<String> candidates = new LinkedHashSet<>();
                for (int i = 0; i < Math.min(poolSize, tetraResults.size()); i++) {
                    candidates.add(tetraResults.get(i).getEncounterNumber());
                }

                // Phase 2: Groth verification on candidates only
                List<MatchObject> grothResults = new ArrayList<>();
                for (String candEncId : candidates) {
                    EncounterLite el = matchGraph.get(candEncId);
                    if (el == null) continue;

                    try {
                        MatchObject mo = el.getPointsForBestMatch(
                            queryArray, GROTH_EPSILON, GROTH_R, GROTH_SIZELIM,
                            GROTH_MAX_ROTATION, GROTH_C, GROTH_SECOND_RUN, false);
                        if (mo != null && mo.getMatchValue() > 0) {
                            mo.encounterNumber = candEncId;
                            mo.individualName = encounterToIndividual.getOrDefault(
                                candEncId, "N/A");
                            grothResults.add(mo);
                        }
                    } catch (Exception e) {
                        // Skip failed comparisons
                    }
                }

                long qTime = System.nanoTime() - qStart;
                totalQueryTime += qTime;
                queriesRun++;

                // Sort Groth results
                grothResults.sort((a, b) -> Double.compare(
                    b.getMatchValue() * b.getAdjustedMatchValue(),
                    a.getMatchValue() * a.getAdjustedMatchValue()));

                int correctRank = findCorrectRank(grothResults, queryEncId, trueIndId);

                if (correctRank == -1) notFound++;
                for (int r = 0; r < RANK_LEVELS.length; r++) {
                    if (correctRank >= 1 && correctRank <= RANK_LEVELS[r]) {
                        rankHits[r]++;
                    }
                }
            }

            double avgMs = (totalQueryTime / 1_000_000.0) / queriesRun;
            SweepResult result = new SweepResult();
            result.numBins = bestTetra.numBins;
            result.maxRatioDistance = bestTetra.maxRatioDistance;
            result.maxPatternsPerEncounter = bestTetra.maxPatternsPerEncounter;
            result.maxQueryPatterns = bestTetra.maxQueryPatterns;
            result.hybridPoolSize = poolSize;
            result.rank1Pct = 100.0 * rankHits[0] / queriesRun;
            result.rank5Pct = 100.0 * rankHits[1] / queriesRun;
            result.rank10Pct = 100.0 * rankHits[2] / queriesRun;
            result.rank20Pct = 100.0 * rankHits[3] / queriesRun;
            result.rank50Pct = 100.0 * rankHits[4] / queriesRun;
            result.rank100Pct = 100.0 * rankHits[5] / queriesRun;
            result.avgQueryMs = avgMs;
            result.buildTimeMs = hybridBuildTime;
            result.notFound = notFound;
            result.queries = queriesRun;
            partBResults.add(result);

            System.out.println("  Top-" + poolSize + ": Rank-1=" +
                String.format("%.1f%%", result.rank1Pct) +
                " Rank-5=" + String.format("%.1f%%", result.rank5Pct) +
                " Avg=" + String.format("%.0f", avgMs) + " ms" +
                " NotFound=" + notFound);
        }

        // Print Part B results table
        System.out.println();
        printHybridTable("PART B: Hybrid (best TETRA -> Groth verify)",
            partBResults);

        // =====================================================================
        // FINAL SUMMARY
        // =====================================================================
        System.out.println("================================================================");
        System.out.println("     FINAL SUMMARY");
        System.out.println("================================================================\n");

        // Find best hybrid config meeting the target
        SweepResult bestHybrid = null;
        for (SweepResult r : partBResults) {
            if (r.avgQueryMs <= 5000) {
                if (bestHybrid == null || r.rank1Pct > bestHybrid.rank1Pct) {
                    bestHybrid = r;
                }
            }
        }

        System.out.println("Best TETRA-only config (by Rank-100 recall):");
        System.out.println("  numBins=" + bestTetra.numBins +
            " maxRatioDistance=" + bestTetra.maxRatioDistance +
            " maxPatternsPerEncounter=" + bestTetra.maxPatternsPerEncounter +
            " maxQueryPatterns=" + bestTetra.maxQueryPatterns);
        System.out.println("  Rank-1=" + String.format("%.1f%%", bestTetra.rank1Pct) +
            " Rank-100=" + String.format("%.1f%%", bestTetra.rank100Pct) +
            " Avg=" + String.format("%.0f ms", bestTetra.avgQueryMs) +
            " NotFound=" + bestTetra.notFound);
        System.out.println();

        if (bestHybrid != null) {
            System.out.println("Best hybrid config (Rank-1 accuracy, under 5s/query):");
            System.out.println("  TETRA: numBins=" + bestHybrid.numBins +
                " maxRatioDist=" + bestHybrid.maxRatioDistance +
                " maxPat=" + bestHybrid.maxPatternsPerEncounter +
                " maxQ=" + bestHybrid.maxQueryPatterns);
            System.out.println("  Hybrid: top-" + bestHybrid.hybridPoolSize +
                " candidates -> Groth verify");
            System.out.println("  Rank-1=" + String.format("%.1f%%", bestHybrid.rank1Pct) +
                " Rank-5=" + String.format("%.1f%%", bestHybrid.rank5Pct) +
                " Avg=" + String.format("%.0f ms", bestHybrid.avgQueryMs) +
                " NotFound=" + bestHybrid.notFound);

            boolean meetsTarget = bestHybrid.avgQueryMs <= 5000;
            System.out.println("  Speed target (<5s):    " + (meetsTarget ? "MET" : "NOT MET"));
        } else {
            System.out.println("No hybrid config met the <5s/query target.");
            System.out.println("Best hybrid by Rank-1 (any speed):");
            SweepResult fastest = partBResults.stream()
                .max(Comparator.comparingDouble(r -> r.rank1Pct))
                .orElse(null);
            if (fastest != null) {
                System.out.println("  top-" + fastest.hybridPoolSize +
                    " -> Rank-1=" + String.format("%.1f%%", fastest.rank1Pct) +
                    " Avg=" + String.format("%.0f ms", fastest.avgQueryMs));
            }
        }

        // Show the Rank-100 recall of the best TETRA config to assess the
        // ceiling for hybrid accuracy
        System.out.println("\nTETRA Rank-100 recall ceiling: " +
            String.format("%.1f%%", bestTetra.rank100Pct) +
            " (hybrid Rank-1 can never exceed this)");
        System.out.println("================================================================\n");
    }

    // -- Sweep helper: run TETRA-only queries and collect SweepResult -----------

    private SweepResult runTetraSweepQueries(
            TetraQueryEngine engine, List<String> queryEncIds,
            TetraConfig config, long buildTimeMs) {

        int[] rankHits = new int[RANK_LEVELS.length];
        long totalQueryTime = 0;
        int queriesRun = 0;
        int notFound = 0;

        for (String queryEncId : queryEncIds) {
            ArrayList<SuperSpot> querySpots = encounterSpots.get(queryEncId);
            String trueIndId = encounterToIndividual.get(queryEncId);

            long qStart = System.nanoTime();
            List<MatchObject> results = engine.match(querySpots, false, queryEncId);
            long qTime = System.nanoTime() - qStart;
            totalQueryTime += qTime;
            queriesRun++;

            int correctRank = findCorrectRank(results, queryEncId, trueIndId);

            if (correctRank == -1) notFound++;
            for (int r = 0; r < RANK_LEVELS.length; r++) {
                if (correctRank >= 1 && correctRank <= RANK_LEVELS[r]) {
                    rankHits[r]++;
                }
            }
        }

        double avgMs = (totalQueryTime / 1_000_000.0) / queriesRun;
        SweepResult result = new SweepResult();
        result.numBins = config.getNumBins();
        result.maxRatioDistance = config.getMaxRatioDistance();
        result.maxPatternsPerEncounter = config.getMaxPatternsPerEncounter();
        result.maxQueryPatterns = config.getMaxQueryPatterns();
        result.hybridPoolSize = 0; // not hybrid
        result.rank1Pct = 100.0 * rankHits[0] / queriesRun;
        result.rank5Pct = 100.0 * rankHits[1] / queriesRun;
        result.rank10Pct = 100.0 * rankHits[2] / queriesRun;
        result.rank20Pct = 100.0 * rankHits[3] / queriesRun;
        result.rank50Pct = 100.0 * rankHits[4] / queriesRun;
        result.rank100Pct = 100.0 * rankHits[5] / queriesRun;
        result.avgQueryMs = avgMs;
        result.buildTimeMs = buildTimeMs;
        result.notFound = notFound;
        result.queries = queriesRun;
        return result;
    }

    // -- Sweep result data class -----------------------------------------------

    private static class SweepResult {
        int numBins;
        double maxRatioDistance;
        int maxPatternsPerEncounter;
        int maxQueryPatterns;
        int hybridPoolSize;  // 0 = TETRA-only
        double rank1Pct, rank5Pct, rank10Pct, rank20Pct, rank50Pct, rank100Pct;
        double avgQueryMs;
        long buildTimeMs;
        int notFound;
        int queries;
    }

    // -- Table printers --------------------------------------------------------

    private void printSweepTable(String title, List<SweepResult> results,
                                  String col1, String col2, String col3, String col4,
                                  boolean showAllRanks) {
        System.out.println("\n" + title);
        String sep = "+--------+------------+----------+--------+--------+" +
            "--------+--------+--------+--------+--------+-----------+----------+";
        String hdr = String.format(
            "| %-6s | %-10s | %-8s | %-6s | %-6s | %-6s | %-6s | %-6s | %-6s | %-6s | %-9s | %-8s |",
            col1, col2, col3, col4,
            "Rk-1", "Rk-5", "Rk-10", "Rk-20", "Rk-50", "Rk-100",
            "Avg ms", "NotFnd");
        System.out.println(sep);
        System.out.println(hdr);
        System.out.println(sep);

        for (SweepResult r : results) {
            System.out.println(String.format(
                "| %-6d | %-10.2f | %-8d | %-6d | %5.1f%% | %5.1f%% | %5.1f%% | %5.1f%% | %5.1f%% | %5.1f%% | %9.0f | %5d/%-2d |",
                r.numBins, r.maxRatioDistance,
                r.maxPatternsPerEncounter, r.maxQueryPatterns,
                r.rank1Pct, r.rank5Pct, r.rank10Pct,
                r.rank20Pct, r.rank50Pct, r.rank100Pct,
                r.avgQueryMs, r.notFound, r.queries));
        }
        System.out.println(sep);
        System.out.println();
    }

    private void printHybridTable(String title, List<SweepResult> results) {
        System.out.println(title);
        String sep = "+-----------+--------+--------+--------+--------+--------+--------+-----------+----------+";
        String hdr = String.format(
            "| %-9s | %-6s | %-6s | %-6s | %-6s | %-6s | %-6s | %-9s | %-8s |",
            "Pool Size", "Rk-1", "Rk-5", "Rk-10", "Rk-20", "Rk-50", "Rk-100",
            "Avg ms", "NotFnd");
        System.out.println(sep);
        System.out.println(hdr);
        System.out.println(sep);

        for (SweepResult r : results) {
            String speedMark = r.avgQueryMs <= 5000 ? " " : "*";
            System.out.println(String.format(
                "| top-%-5d | %5.1f%% | %5.1f%% | %5.1f%% | %5.1f%% | %5.1f%% | %5.1f%% | %8.0f%s | %5d/%-2d |",
                r.hybridPoolSize,
                r.rank1Pct, r.rank5Pct, r.rank10Pct,
                r.rank20Pct, r.rank50Pct, r.rank100Pct,
                r.avgQueryMs, speedMark, r.notFound, r.queries));
        }
        System.out.println(sep);
        System.out.println("(* = exceeds 5s/query target)\n");
    }

    /**
     * Find the SweepResult with the highest Rank-100 recall.
     * Ties broken by: fewer notFound, then lower avg query time.
     */
    private SweepResult findBestByRank100(List<SweepResult> results) {
        return results.stream()
            .max(Comparator.comparingDouble((SweepResult r) -> r.rank100Pct)
                .thenComparingInt(r -> -r.notFound)
                .thenComparingDouble(r -> -r.avgQueryMs))
            .orElseThrow(() -> new RuntimeException("No sweep results"));
    }

    /**
     * High-pattern TETRA benchmark for 16GB RAM environments.
     * Tests 3000, 4000, 5000 patterns/enc with best bins/maxRatioDist from sweep.
     * Also diagnoses which queries fail (by spot count).
     *
     * Run with:
     *   mvn test -Dtest="org.ecocean.grid.tetra.TetraBenchmarkTest#benchmarkHighPatterns" \
     *       -Dtetra.benchmark.csv=tetra_benchmark_spots.csv \
     *       -DargLine="--add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED -Xmx8g"
     */
    @Test
    void benchmarkHighPatterns() {
        System.out.println("================================================================");
        System.out.println("     HIGH-PATTERN TETRA (16GB RAM target)");
        System.out.println("     Best config from sweep: numBins=20, maxRatioDist=0.05");
        System.out.println("================================================================\n");

        List<String> queryEncIds = selectQueryEncounters();
        int bestNumBins = 20;
        double bestMaxRatioDist = 0.05;
        int maxQuery = 2000;  // also increase query patterns
        int sweepTopK = 500;

        int[] patternCounts = {2000, 3000, 4000, 5000};
        List<SweepResult> results = new ArrayList<>();

        for (int maxPat : patternCounts) {
            System.out.println("Building index: numBins=" + bestNumBins +
                " maxPatterns=" + maxPat + " maxQuery=" + maxQuery + " ...");
            long buildStart = System.currentTimeMillis();

            TetraHashIndex leftIndex = new TetraHashIndex(bestNumBins);
            TetraHashIndex rightIndex = new TetraHashIndex(bestNumBins);

            for (String encId : allEncounterIds) {
                ArrayList<SuperSpot> spots = encounterSpots.get(encId);
                if (spots.size() < 4) continue;
                double[] sx = new double[spots.size()];
                double[] sy = new double[spots.size()];
                for (int i = 0; i < spots.size(); i++) {
                    sx[i] = spots.get(i).getCentroidX();
                    sy[i] = spots.get(i).getCentroidY();
                }
                normalize(sx, sy);
                leftIndex.indexEncounter(encId, sx, sy, maxPat);
            }

            long buildTime = System.currentTimeMillis() - buildStart;
            long approxMemMB = (long) leftIndex.getNumIndexedEncounters() * maxPat * 84 / 1_000_000;
            System.out.println("  Built in " + buildTime + " ms, " +
                leftIndex.getNumIndexedEncounters() + " encounters, " +
                leftIndex.getTableSize() + " buckets, ~" + approxMemMB + " MB entries");

            TetraConfig config = new TetraConfig();
            config.setNumBins(bestNumBins);
            config.setMaxPatternsPerEncounter(maxPat);
            config.setMaxQueryPatterns(maxQuery);
            config.setMaxRatioDistance(bestMaxRatioDist);
            config.setTopK(sweepTopK);
            config.setMinVotes(1);
            config.setToleranceEnabled(false);

            TetraQueryEngine engine = new TetraQueryEngine(
                leftIndex, rightIndex, config);

            // Run TETRA-only queries
            int[] rankHits = new int[RANK_LEVELS.length];
            long totalQueryTime = 0;
            int queriesRun = 0;
            int notFound = 0;
            List<String> notFoundIds = new ArrayList<>();
            List<Integer> notFoundSpotCounts = new ArrayList<>();

            for (String queryEncId : queryEncIds) {
                ArrayList<SuperSpot> querySpots = encounterSpots.get(queryEncId);
                String trueIndId = encounterToIndividual.get(queryEncId);

                long qStart = System.nanoTime();
                List<MatchObject> matchResults = engine.match(querySpots, false, queryEncId);
                long qTime = System.nanoTime() - qStart;
                totalQueryTime += qTime;
                queriesRun++;

                int correctRank = findCorrectRank(matchResults, queryEncId, trueIndId);

                if (correctRank == -1) {
                    notFound++;
                    notFoundIds.add(queryEncId);
                    notFoundSpotCounts.add(querySpots.size());
                }
                for (int r = 0; r < RANK_LEVELS.length; r++) {
                    if (correctRank >= 1 && correctRank <= RANK_LEVELS[r]) {
                        rankHits[r]++;
                    }
                }
            }

            double avgMs = (totalQueryTime / 1_000_000.0) / queriesRun;

            SweepResult result = new SweepResult();
            result.numBins = bestNumBins;
            result.maxRatioDistance = bestMaxRatioDist;
            result.maxPatternsPerEncounter = maxPat;
            result.maxQueryPatterns = maxQuery;
            result.rank1Pct = 100.0 * rankHits[0] / queriesRun;
            result.rank5Pct = 100.0 * rankHits[1] / queriesRun;
            result.rank10Pct = 100.0 * rankHits[2] / queriesRun;
            result.rank20Pct = 100.0 * rankHits[3] / queriesRun;
            result.rank50Pct = 100.0 * rankHits[4] / queriesRun;
            result.rank100Pct = 100.0 * rankHits[5] / queriesRun;
            result.avgQueryMs = avgMs;
            result.buildTimeMs = buildTime;
            result.notFound = notFound;
            result.queries = queriesRun;
            results.add(result);

            System.out.println("  maxPat=" + maxPat +
                " Rank-1=" + String.format("%.1f%%", result.rank1Pct) +
                " Rank-5=" + String.format("%.1f%%", result.rank5Pct) +
                " Rank-100=" + String.format("%.1f%%", result.rank100Pct) +
                " Avg=" + String.format("%.0f ms", avgMs) +
                " NotFound=" + notFound);

            if (!notFoundIds.isEmpty()) {
                System.out.println("  Not-found encounters (spot counts): " +
                    notFoundSpotCounts.stream()
                        .sorted()
                        .map(String::valueOf)
                        .collect(Collectors.joining(", ")));
            }
            System.out.println();
        }

        // Print summary table
        printSweepTable("HIGH-PATTERN TETRA (numBins=20, maxRatioDist=0.05)",
            results,
            "numBins", "maxRatioDist", "maxPat/enc", "maxQPat",
            true);

        // Now run hybrid with the best high-pattern config
        SweepResult bestConfig = findBestByRank100(results);
        System.out.println("Best high-pattern config: maxPat=" +
            bestConfig.maxPatternsPerEncounter +
            " Rank-100=" + String.format("%.1f%%", bestConfig.rank100Pct) + "\n");

        System.out.println("--- Hybrid with best high-pattern TETRA ---\n");

        // Build index and matchGraph
        System.out.println("Building final index + EncounterLite objects...");
        long hybridBuildStart = System.currentTimeMillis();

        TetraHashIndex bestLeft = new TetraHashIndex(bestNumBins);
        TetraHashIndex bestRight = new TetraHashIndex(bestNumBins);
        Map<String, EncounterLite> matchGraph = new LinkedHashMap<>();

        for (String encId : allEncounterIds) {
            ArrayList<SuperSpot> spots = encounterSpots.get(encId);

            if (spots.size() >= 4) {
                double[] sx = new double[spots.size()];
                double[] sy = new double[spots.size()];
                for (int i = 0; i < spots.size(); i++) {
                    sx[i] = spots.get(i).getCentroidX();
                    sy[i] = spots.get(i).getCentroidY();
                }
                normalize(sx, sy);
                bestLeft.indexEncounter(encId, sx, sy,
                    bestConfig.maxPatternsPerEncounter);
            }

            if (spots.size() >= 3) {
                EncounterLite el = new EncounterLite();
                el.processLeftSpots(spots);
                matchGraph.put(encId, el);
            }
        }

        long hybridBuildTime = System.currentTimeMillis() - hybridBuildStart;
        System.out.println("Build complete in " + hybridBuildTime + " ms\n");

        TetraConfig hybridConfig = new TetraConfig();
        hybridConfig.setNumBins(bestNumBins);
        hybridConfig.setMaxPatternsPerEncounter(bestConfig.maxPatternsPerEncounter);
        hybridConfig.setMaxQueryPatterns(maxQuery);
        hybridConfig.setMaxRatioDistance(bestMaxRatioDist);
        hybridConfig.setTopK(sweepTopK);
        hybridConfig.setMinVotes(1);
        hybridConfig.setToleranceEnabled(false);

        TetraQueryEngine hybridEngine = new TetraQueryEngine(
            bestLeft, bestRight, hybridConfig);

        int[] hybridPoolSizes = {100, 200, 300, 500};
        List<SweepResult> hybridResults = new ArrayList<>();

        for (int poolSize : hybridPoolSizes) {
            System.out.println("Running hybrid top-" + poolSize + "...");

            int[] rankHits = new int[RANK_LEVELS.length];
            long totalQueryTime = 0;
            int queriesRun = 0;
            int notFound = 0;

            for (String queryEncId : queryEncIds) {
                ArrayList<SuperSpot> querySpots = encounterSpots.get(queryEncId);
                String trueIndId = encounterToIndividual.get(queryEncId);
                SuperSpot[] queryArray = querySpots.toArray(new SuperSpot[0]);

                long qStart = System.nanoTime();

                List<MatchObject> tetraResults = hybridEngine.match(
                    querySpots, false, queryEncId);
                Set<String> candidates = new LinkedHashSet<>();
                for (int i = 0; i < Math.min(poolSize, tetraResults.size()); i++) {
                    candidates.add(tetraResults.get(i).getEncounterNumber());
                }

                List<MatchObject> grothResults = new ArrayList<>();
                for (String candEncId : candidates) {
                    EncounterLite el = matchGraph.get(candEncId);
                    if (el == null) continue;

                    try {
                        MatchObject mo = el.getPointsForBestMatch(
                            queryArray, GROTH_EPSILON, GROTH_R, GROTH_SIZELIM,
                            GROTH_MAX_ROTATION, GROTH_C, GROTH_SECOND_RUN, false);
                        if (mo != null && mo.getMatchValue() > 0) {
                            mo.encounterNumber = candEncId;
                            mo.individualName = encounterToIndividual.getOrDefault(
                                candEncId, "N/A");
                            grothResults.add(mo);
                        }
                    } catch (Exception e) {
                        // Skip
                    }
                }

                long qTime = System.nanoTime() - qStart;
                totalQueryTime += qTime;
                queriesRun++;

                grothResults.sort((a, b) -> Double.compare(
                    b.getMatchValue() * b.getAdjustedMatchValue(),
                    a.getMatchValue() * a.getAdjustedMatchValue()));

                int correctRank = findCorrectRank(grothResults, queryEncId, trueIndId);

                if (correctRank == -1) notFound++;
                for (int r = 0; r < RANK_LEVELS.length; r++) {
                    if (correctRank >= 1 && correctRank <= RANK_LEVELS[r]) {
                        rankHits[r]++;
                    }
                }
            }

            double avgMs = (totalQueryTime / 1_000_000.0) / queriesRun;
            SweepResult hr = new SweepResult();
            hr.numBins = bestNumBins;
            hr.maxRatioDistance = bestMaxRatioDist;
            hr.maxPatternsPerEncounter = bestConfig.maxPatternsPerEncounter;
            hr.maxQueryPatterns = maxQuery;
            hr.hybridPoolSize = poolSize;
            hr.rank1Pct = 100.0 * rankHits[0] / queriesRun;
            hr.rank5Pct = 100.0 * rankHits[1] / queriesRun;
            hr.rank10Pct = 100.0 * rankHits[2] / queriesRun;
            hr.rank20Pct = 100.0 * rankHits[3] / queriesRun;
            hr.rank50Pct = 100.0 * rankHits[4] / queriesRun;
            hr.rank100Pct = 100.0 * rankHits[5] / queriesRun;
            hr.avgQueryMs = avgMs;
            hr.buildTimeMs = hybridBuildTime;
            hr.notFound = notFound;
            hr.queries = queriesRun;
            hybridResults.add(hr);

            System.out.println("  Top-" + poolSize + ": Rank-1=" +
                String.format("%.1f%%", hr.rank1Pct) +
                " Rank-5=" + String.format("%.1f%%", hr.rank5Pct) +
                " Avg=" + String.format("%.0f ms", avgMs) +
                " NotFound=" + notFound);
        }

        printHybridTable("Hybrid (high-pattern TETRA -> Groth)", hybridResults);
    }

    /**
     * Parse a CSV line handling quoted fields (for individual_name which may be empty "").
     */
    static String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                fields.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields.toArray(new String[0]);
    }
}
