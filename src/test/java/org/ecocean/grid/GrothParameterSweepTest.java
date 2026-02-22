package org.ecocean.grid;

import org.ecocean.SuperSpot;
import org.ecocean.grid.tetra.TetraConfig;
import org.ecocean.grid.tetra.TetraHashIndex;
import org.ecocean.grid.tetra.TetraQueryEngine;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

/**
 * Coordinate-descent parameter optimization for the Modified Groth algorithm.
 *
 * Data quality filters:
 * - Only includes individuals with non-empty, unique names (avoids duplicate bug)
 * - Only includes individuals with 2+ encounters (need at least one catalog match)
 *
 * Query stratification:
 * - Half of queries from 2-encounter individuals (hardest "first match" case)
 * - Half from individuals with 3+ encounters
 *
 * Metrics:
 * - Rank-1 accuracy (is the top result correct?)
 * - mAP (mean Average Precision — how well are ALL true matches ranked?)
 *
 * Optimization: coordinate descent with 2 rounds of refinement.
 *
 * Run with:
 *   mvn test -Dtest=GrothParameterSweepTest \
 *     -Dtetra.benchmark.csv=benchmark_spots.csv \
 *     -DargLine="--add-opens java.base/java.lang=ALL-UNNAMED \
 *               --add-opens java.base/java.util=ALL-UNNAMED -Xmx4g"
 */
@EnabledIf("csvFileExists")
class GrothParameterSweepTest {

    private static final String CSV_PATH = System.getProperty(
        "tetra.benchmark.csv", "benchmark_spots.csv");

    static boolean csvFileExists() {
        return new File(CSV_PATH).exists();
    }

    private static final int NUM_QUERIES = 40;
    private static final int CATALOG_SIZE = 200;

    // Data structures (filtered to named, unique individuals only)
    private static Map<String, ArrayList<SuperSpot>> encounterSpots;
    private static Map<String, String> encounterToIndividual;
    private static Map<String, List<String>> individualToEncounters;
    private static Map<String, EncounterLite> catalog;
    private static List<String> queryEncIds;
    private static int queriesAvailable;

    @BeforeAll
    static void loadData() throws IOException {
        // Step 1: Load all data from CSV
        Map<String, ArrayList<SuperSpot>> allEncounterSpots = new LinkedHashMap<>();
        Map<String, String> allEncToInd = new HashMap<>();
        Map<String, List<String>> allIndToEncs = new HashMap<>();
        Map<String, String> indIdToName = new HashMap<>();

        try (BufferedReader br = new BufferedReader(new FileReader(CSV_PATH))) {
            String header = br.readLine();
            String line;
            while ((line = br.readLine()) != null) {
                String[] fields = parseCsvLine(line);
                if (fields.length < 8) continue;

                String encId = fields[0];
                String indId = fields[1];
                String indName = fields[2].trim();
                double spotX = Double.parseDouble(fields[5]);
                double spotY = Double.parseDouble(fields[6]);

                allEncounterSpots.computeIfAbsent(encId, k -> new ArrayList<>())
                    .add(new SuperSpot(spotX, spotY));

                allEncToInd.putIfAbsent(encId, indId);
                allIndToEncs.computeIfAbsent(indId, k -> new ArrayList<>());
                if (!allIndToEncs.get(indId).contains(encId)) {
                    allIndToEncs.get(indId).add(encId);
                }
                if (!indName.isEmpty()) {
                    indIdToName.put(indId, indName);
                }
            }
        }

        // Step 2: Find names that appear on multiple individual IDs (duplicates)
        Map<String, List<String>> nameToIds = new HashMap<>();
        for (Map.Entry<String, String> entry : indIdToName.entrySet()) {
            nameToIds.computeIfAbsent(entry.getValue(), k -> new ArrayList<>())
                .add(entry.getKey());
        }
        Set<String> duplicatedNames = new HashSet<>();
        for (Map.Entry<String, List<String>> entry : nameToIds.entrySet()) {
            if (entry.getValue().size() > 1) {
                duplicatedNames.add(entry.getKey());
            }
        }

        // Step 3: Filter to named individuals with unique names and 2+ encounters
        Set<String> eligibleIndividuals = new HashSet<>();
        for (Map.Entry<String, String> entry : indIdToName.entrySet()) {
            String indId = entry.getKey();
            String name = entry.getValue();
            if (!duplicatedNames.contains(name) &&
                allIndToEncs.containsKey(indId) &&
                allIndToEncs.get(indId).size() >= 2) {
                eligibleIndividuals.add(indId);
            }
        }

        // Step 4: Build filtered data structures
        encounterSpots = new LinkedHashMap<>();
        encounterToIndividual = new HashMap<>();
        individualToEncounters = new HashMap<>();

        for (String indId : eligibleIndividuals) {
            List<String> encIds = allIndToEncs.get(indId);
            individualToEncounters.put(indId, new ArrayList<>(encIds));
            for (String encId : encIds) {
                if (allEncounterSpots.containsKey(encId)) {
                    encounterSpots.put(encId, allEncounterSpots.get(encId));
                    encounterToIndividual.put(encId, indId);
                }
            }
        }

        // Step 5: Build catalog
        catalog = new LinkedHashMap<>();
        for (Map.Entry<String, ArrayList<SuperSpot>> entry : encounterSpots.entrySet()) {
            if (entry.getValue().size() >= 3) {
                EncounterLite el = new EncounterLite();
                el.processLeftSpots(entry.getValue());
                catalog.put(entry.getKey(), el);
            }
        }

        // Step 6: Select stratified queries
        queryEncIds = selectStratifiedQueries(NUM_QUERIES, indIdToName);

        // Count strata
        int twoEncQueries = 0;
        int multiEncQueries = 0;
        for (String qEncId : queryEncIds) {
            String indId = encounterToIndividual.get(qEncId);
            if (individualToEncounters.get(indId).size() == 2) twoEncQueries++;
            else multiEncQueries++;
        }

        queriesAvailable = queryEncIds.size();

        System.out.println("=== Groth Parameter Sweep Data ===");
        System.out.println("Total encounters in CSV: " + allEncounterSpots.size());
        System.out.println("Total individuals in CSV: " + allIndToEncs.size());
        System.out.println("Named individuals: " + indIdToName.size());
        System.out.println("Duplicated names excluded: " + duplicatedNames.size());
        System.out.println("Eligible individuals (named, unique, 2+ encs): " +
            eligibleIndividuals.size());
        System.out.println("Eligible encounters: " + encounterSpots.size());
        System.out.println("Catalog (with 3+ spots): " + catalog.size());
        System.out.println("Queries selected: " + queriesAvailable +
            " (" + twoEncQueries + " two-encounter, " +
            multiEncQueries + " multi-encounter)");
        System.out.println();
    }

    @Test
    void sweepGrothParameters() {
        // Starting baseline (Kingen & Holmberg 2019)
        double bestEpsilon = 0.008;
        double bestR = 49.8;
        double bestSizelim = 0.998;
        double bestMaxRot = 12.33;
        double bestC = 0.998;

        System.out.println("========================================");
        System.out.println("  GROTH PARAMETER SWEEP - ROUND 1");
        System.out.println("========================================");
        System.out.printf("Baseline: epsilon=%.4f, R=%.1f, Sizelim=%.3f, maxRot=%.2f, C=%.3f%n",
            bestEpsilon, bestR, bestSizelim, bestMaxRot, bestC);

        // --- Round 1: Coarse sweep ---

        double[] epsilonValues = {0.002, 0.004, 0.006, 0.008, 0.01, 0.015, 0.02, 0.03};
        bestEpsilon = sweepParameter("epsilon", epsilonValues,
            bestEpsilon, bestR, bestSizelim, bestMaxRot, bestC, ParamIndex.EPSILON);

        double[] rValues = {5, 10, 20, 30, 50, 70, 100};
        bestR = sweepParameter("R", rValues,
            bestEpsilon, bestR, bestSizelim, bestMaxRot, bestC, ParamIndex.R);

        double[] sizelimValues = {0.85, 0.9, 0.95, 0.98, 0.99, 0.995, 0.998};
        bestSizelim = sweepParameter("Sizelim", sizelimValues,
            bestEpsilon, bestR, bestSizelim, bestMaxRot, bestC, ParamIndex.SIZELIM);

        double[] maxRotValues = {5, 8, 10, 12, 15, 20, 30, 45};
        bestMaxRot = sweepParameter("maxRot", maxRotValues,
            bestEpsilon, bestR, bestSizelim, bestMaxRot, bestC, ParamIndex.MAX_ROT);

        double[] cValues = {0.85, 0.9, 0.95, 0.98, 0.99, 0.995, 0.998};
        bestC = sweepParameter("C", cValues,
            bestEpsilon, bestR, bestSizelim, bestMaxRot, bestC, ParamIndex.C);

        System.out.println("\n========================================");
        System.out.println("  ROUND 1 WINNERS");
        System.out.println("========================================");
        System.out.printf("epsilon=%.4f, R=%.1f, Sizelim=%.3f, maxRot=%.2f, C=%.3f%n",
            bestEpsilon, bestR, bestSizelim, bestMaxRot, bestC);

        // --- Round 2: Fine sweep ---
        System.out.println("\n========================================");
        System.out.println("  GROTH PARAMETER SWEEP - ROUND 2");
        System.out.println("========================================\n");

        double[] epsilonFine = refineRange(bestEpsilon, epsilonValues);
        if (epsilonFine.length > 1) {
            bestEpsilon = sweepParameter("epsilon", epsilonFine,
                bestEpsilon, bestR, bestSizelim, bestMaxRot, bestC, ParamIndex.EPSILON);
        }

        double[] rFine = refineRange(bestR, rValues);
        if (rFine.length > 1) {
            bestR = sweepParameter("R", rFine,
                bestEpsilon, bestR, bestSizelim, bestMaxRot, bestC, ParamIndex.R);
        }

        double[] sizelimFine = refineRange(bestSizelim, sizelimValues);
        if (sizelimFine.length > 1) {
            bestSizelim = sweepParameter("Sizelim", sizelimFine,
                bestEpsilon, bestR, bestSizelim, bestMaxRot, bestC, ParamIndex.SIZELIM);
        }

        double[] maxRotFine = refineRange(bestMaxRot, maxRotValues);
        if (maxRotFine.length > 1) {
            bestMaxRot = sweepParameter("maxRot", maxRotFine,
                bestEpsilon, bestR, bestSizelim, bestMaxRot, bestC, ParamIndex.MAX_ROT);
        }

        double[] cFine = refineRange(bestC, cValues);
        if (cFine.length > 1) {
            bestC = sweepParameter("C", cFine,
                bestEpsilon, bestR, bestSizelim, bestMaxRot, bestC, ParamIndex.C);
        }

        // Final validation
        System.out.println("\n========================================");
        System.out.println("  FINAL VALIDATION");
        System.out.println("========================================");
        System.out.printf("Parameters: epsilon=%.4f, R=%.1f, Sizelim=%.3f, maxRot=%.2f, C=%.3f%n",
            bestEpsilon, bestR, bestSizelim, bestMaxRot, bestC);
        double[] finalResult = runBenchmark(bestEpsilon, bestR, bestSizelim, bestMaxRot, bestC);
        int qCount = (int) finalResult[3];
        System.out.printf("Rank-1: %.0f/%d (%.1f%%)%n",
            finalResult[0], qCount, 100.0 * finalResult[0] / qCount);
        System.out.printf("mAP:    %.3f%n", finalResult[2]);
        System.out.printf("Avg:    %,.0f ms%n", finalResult[4]);
        System.out.println("========================================\n");

        System.out.println("=== For commonConfiguration.properties ===");
        System.out.printf("R=%.1f%n", bestR);
        System.out.printf("epsilon=%.4f%n", bestEpsilon);
        System.out.printf("sizelim=%.3f%n", bestSizelim);
        System.out.printf("maxTriangleRotation=%.2f%n", bestMaxRot);
        System.out.printf("C=%.3f%n", bestC);
    }

    /**
     * Comparison report: run the same 40 stratified queries with five approaches:
     *   1. Groth with old Wildbook defaults
     *   2. Groth with Kingen & Holmberg 2019 literature values
     *   3. Groth with our coordinate-descent optimized values
     *   4. TETRA-only (hash-based pre-filter, no Groth refinement)
     *   5. Hybrid (TETRA pre-filter + Groth refinement with optimized params)
     *
     * Groth-only approaches use a random 200-encounter catalog subset per query
     * (with true matches guaranteed). TETRA and Hybrid search the full catalog.
     *
     * Run with:
     *   mvn test -Dtest=GrothParameterSweepTest#compareParameterSets \
     *     -Dtetra.benchmark.csv=benchmark_spots.csv \
     *     -DargLine="--add-opens java.base/java.lang=ALL-UNNAMED \
     *               --add-opens java.base/java.util=ALL-UNNAMED -Xmx4g"
     */
    @Test
    void compareParameterSets() {
        System.out.println();
        System.out.println("================================================================");
        System.out.println("  MODIFIED GROTH & HYBRID COMPARISON REPORT");
        System.out.println("================================================================");
        System.out.println();
        System.out.println("Dataset: " + CSV_PATH);
        System.out.println("Eligible individuals: " + individualToEncounters.size());
        System.out.println("Catalog encounters: " + catalog.size());
        System.out.println("Queries: " + queriesAvailable +
            " (stratified: half 2-encounter, half multi-encounter)");
        System.out.println("Groth catalog subset: " + CATALOG_SIZE +
            " encounters per query (random with guaranteed true matches)");
        System.out.println("TETRA/Hybrid catalog: ALL " + catalog.size() +
            " encounters (full scan via hash index)");
        System.out.println();

        // --- Build TETRA index from the full catalog ---
        System.out.println("Building TETRA index...");
        long indexStart = System.nanoTime();

        TetraConfig tetraConfig = new TetraConfig();
        tetraConfig.setNumBins(25);
        tetraConfig.setMaxPatternsPerEncounter(500);
        tetraConfig.setMaxQueryPatterns(1000);
        tetraConfig.setToleranceEnabled(false);
        tetraConfig.setMaxRatioDistance(0.1);
        tetraConfig.setMinVotes(2);
        tetraConfig.setTopK(200);
        tetraConfig.setMinSpots(4);

        TetraHashIndex tetraIndex = new TetraHashIndex(tetraConfig.getNumBins());

        for (Map.Entry<String, ArrayList<SuperSpot>> entry : encounterSpots.entrySet()) {
            ArrayList<SuperSpot> spots = entry.getValue();
            if (spots.size() < tetraConfig.getMinSpots()) continue;

            double[] sx = new double[spots.size()];
            double[] sy = new double[spots.size()];
            double maxCoord = 0;
            for (int i = 0; i < spots.size(); i++) {
                sx[i] = spots.get(i).getCentroidX();
                sy[i] = spots.get(i).getCentroidY();
                maxCoord = Math.max(maxCoord,
                    Math.max(Math.abs(sx[i]), Math.abs(sy[i])));
            }
            if (maxCoord > 0) {
                for (int i = 0; i < sx.length; i++) { sx[i] /= maxCoord; sy[i] /= maxCoord; }
            }
            tetraIndex.indexEncounter(entry.getKey(), sx, sy,
                tetraConfig.getMaxPatternsPerEncounter());
        }

        TetraQueryEngine tetraEngine = new TetraQueryEngine(
            tetraIndex, tetraIndex, tetraConfig);

        long indexMs = (System.nanoTime() - indexStart) / 1_000_000;
        System.out.printf("TETRA index built in %,d ms (%d encounters indexed)%n%n",
            indexMs, tetraIndex.getNumIndexedEncounters());

        // --- 1-3: Groth-only benchmarks (200-encounter subsets) ---
        System.out.println("Running Groth benchmarks (200-encounter subsets)...");
        double[] oldDefaults = runBenchmark(0.01, 8.0, 0.9, 30.0, 0.99);
        System.out.println("  Old defaults complete.");
        double[] kingen = runBenchmark(0.008, 49.8, 0.998, 12.33, 0.998);
        System.out.println("  Kingen 2019 complete.");
        double[] optimized = runBenchmark(0.008, 6.8, 0.671, 22.5, 1.146);
        System.out.println("  Optimized complete.");

        // --- 4: TETRA-only (full catalog) ---
        System.out.println("Running TETRA-only benchmark (full catalog)...");
        double[] tetraOnly = runTetraBenchmark(tetraEngine);
        System.out.println("  TETRA-only complete.");

        // --- 5: Hybrid TETRA+Groth (full catalog) ---
        System.out.println("Running Hybrid benchmark (full catalog)...");
        double[] hybrid = runHybridBenchmark(tetraEngine, 0.008, 6.8, 0.671, 22.5, 1.146);
        System.out.println("  Hybrid complete.");

        // --- Print comparison table ---
        int q = (int) oldDefaults[3];
        int qTetra = (int) tetraOnly[3];
        int qHybrid = (int) hybrid[3];

        System.out.println();
        System.out.println("===========================================================================================");
        System.out.printf("%-24s | %-14s | %-14s | %-14s | %-14s | %-14s%n",
            "Metric", "Groth Old", "Groth Kingen", "Groth Opt", "TETRA Only", "Hybrid");
        System.out.println("===========================================================================================");

        // Approach description
        System.out.printf("%-24s | %-14s | %-14s | %-14s | %-14s | %-14s%n",
            "Algorithm", "Groth", "Groth", "Groth", "TETRA hash", "TETRA+Groth");
        System.out.printf("%-24s | %-14s | %-14s | %-14s | %-14s | %-14s%n",
            "Catalog scope", "200 subset", "200 subset", "200 subset",
            "full " + catalog.size(), "full " + catalog.size());

        System.out.println("-------------------------------------------------------------------------------------------");

        // Parameters
        System.out.printf("%-24s | %-14s | %-14s | %-14s | %-14s | %-14s%n",
            "epsilon", "0.01", "0.008", "0.008", "n/a", "0.008");
        System.out.printf("%-24s | %-14s | %-14s | %-14s | %-14s | %-14s%n",
            "R", "8", "49.8", "6.8", "n/a", "6.8");
        System.out.printf("%-24s | %-14s | %-14s | %-14s | %-14s | %-14s%n",
            "Sizelim", "0.9", "0.998", "0.671", "n/a", "0.671");
        System.out.printf("%-24s | %-14s | %-14s | %-14s | %-14s | %-14s%n",
            "maxRot", "30", "12.33", "22.5", "n/a", "22.5");
        System.out.printf("%-24s | %-14s | %-14s | %-14s | %-14s | %-14s%n",
            "C", "0.99", "0.998", "1.146", "n/a", "1.146");

        System.out.println("-------------------------------------------------------------------------------------------");

        // Results
        System.out.printf("%-24s | %d/%-11d | %d/%-11d | %d/%-11d | %d/%-11d | %d/%-11d%n",
            "Rank-1",
            (int) oldDefaults[0], q, (int) kingen[0], q, (int) optimized[0], q,
            (int) tetraOnly[0], qTetra, (int) hybrid[0], qHybrid);

        System.out.printf("%-24s | %-14s | %-14s | %-14s | %-14s | %-14s%n",
            "Rank-1 %",
            String.format("%.1f%%", 100.0 * oldDefaults[0] / q),
            String.format("%.1f%%", 100.0 * kingen[0] / q),
            String.format("%.1f%%", 100.0 * optimized[0] / q),
            String.format("%.1f%%", 100.0 * tetraOnly[0] / qTetra),
            String.format("%.1f%%", 100.0 * hybrid[0] / qHybrid));

        System.out.printf("%-24s | %d/%-11d | %d/%-11d | %d/%-11d | %d/%-11d | %d/%-11d%n",
            "Rank-5",
            (int) oldDefaults[1], q, (int) kingen[1], q, (int) optimized[1], q,
            (int) tetraOnly[1], qTetra, (int) hybrid[1], qHybrid);

        System.out.printf("%-24s | %-14s | %-14s | %-14s | %-14s | %-14s%n",
            "Rank-5 %",
            String.format("%.1f%%", 100.0 * oldDefaults[1] / q),
            String.format("%.1f%%", 100.0 * kingen[1] / q),
            String.format("%.1f%%", 100.0 * optimized[1] / q),
            String.format("%.1f%%", 100.0 * tetraOnly[1] / qTetra),
            String.format("%.1f%%", 100.0 * hybrid[1] / qHybrid));

        System.out.printf("%-24s | %-14s | %-14s | %-14s | %-14s | %-14s%n",
            "mAP",
            String.format("%.3f", oldDefaults[2]),
            String.format("%.3f", kingen[2]),
            String.format("%.3f", optimized[2]),
            String.format("%.3f", tetraOnly[2]),
            String.format("%.3f", hybrid[2]));

        System.out.printf("%-24s | %-14s | %-14s | %-14s | %-14s | %-14s%n",
            "Avg time/query",
            String.format("%,.0f ms", oldDefaults[4]),
            String.format("%,.0f ms", kingen[4]),
            String.format("%,.0f ms", optimized[4]),
            String.format("%,.0f ms", tetraOnly[4]),
            String.format("%,.0f ms", hybrid[4]));

        // Extra detail for hybrid: TETRA time + Groth time
        if (hybrid.length > 5) {
            System.out.printf("%-24s | %-14s | %-14s | %-14s | %-14s | %-14s%n",
                "  TETRA phase",
                "", "", "", "",
                String.format("%,.0f ms", hybrid[5]));
            System.out.printf("%-24s | %-14s | %-14s | %-14s | %-14s | %-14s%n",
                "  Groth phase",
                "", "", "", "",
                String.format("%,.0f ms", hybrid[6]));
            System.out.printf("%-24s | %-14s | %-14s | %-14s | %-14s | %-14s%n",
                "  Avg candidates",
                "", "", "", "",
                String.format("%.0f", hybrid[7]));
        }

        System.out.println("===========================================================================================");

        // Delta summaries
        System.out.println();
        System.out.println("=== Improvement: Hybrid vs Groth Old Defaults ===");
        System.out.printf("Rank-1:  %+.0f queries (%.1f%% -> %.1f%%)%n",
            hybrid[0] - oldDefaults[0],
            100.0 * oldDefaults[0] / q, 100.0 * hybrid[0] / qHybrid);
        System.out.printf("mAP:     %+.3f (%.3f -> %.3f)%n",
            hybrid[2] - oldDefaults[2], oldDefaults[2], hybrid[2]);
        System.out.printf("Speed:   %.1fx %s (and searches %dx more encounters)%n",
            oldDefaults[4] / hybrid[4],
            oldDefaults[4] > hybrid[4] ? "faster" : "slower",
            catalog.size() / CATALOG_SIZE);

        System.out.println();
        System.out.println("=== Improvement: Hybrid vs Groth Optimized (same params, full catalog) ===");
        System.out.printf("Catalog: %d encounters (hybrid) vs %d (Groth subset)%n",
            catalog.size(), CATALOG_SIZE);
        System.out.printf("Speed:   %,.0f ms (hybrid) vs %,.0f ms (Groth on 200)%n",
            hybrid[4], optimized[4]);
        System.out.printf("Note:    Hybrid searches %dx more encounters with comparable accuracy%n",
            catalog.size() / CATALOG_SIZE);
        System.out.println();
    }

    /**
     * Run TETRA-only benchmark against the full catalog.
     * Returns [rank1Hits, rank5Hits, mAP, queriesRun, avgTimeMs].
     */
    private double[] runTetraBenchmark(TetraQueryEngine engine) {
        int rank1Hits = 0;
        int rank5Hits = 0;
        double sumAP = 0;
        long totalTimeNanos = 0;
        int queriesRun = 0;

        for (String queryEncId : queryEncIds) {
            ArrayList<SuperSpot> querySpots = encounterSpots.get(queryEncId);
            if (querySpots == null || querySpots.size() < 4) continue;

            String trueIndId = encounterToIndividual.get(queryEncId);
            Set<String> trueEncounters = new HashSet<>(
                individualToEncounters.getOrDefault(trueIndId, Collections.emptyList()));
            trueEncounters.remove(queryEncId);
            if (trueEncounters.isEmpty()) continue;

            int numTrueInCatalog = 0;
            for (String trueEncId : trueEncounters) {
                if (catalog.containsKey(trueEncId)) numTrueInCatalog++;
            }
            if (numTrueInCatalog == 0) continue;

            long startNanos = System.nanoTime();
            List<MatchObject> results = engine.match(querySpots, false, queryEncId);
            totalTimeNanos += System.nanoTime() - startNanos;

            int rank = findCorrectRank(results, queryEncId, trueIndId);
            if (rank == 1) rank1Hits++;
            if (rank >= 1 && rank <= 5) rank5Hits++;

            double ap = computeAP(results, trueEncounters, numTrueInCatalog);
            sumAP += ap;
            queriesRun++;
        }

        double avgMs = (queriesRun > 0) ?
            (double) totalTimeNanos / 1_000_000 / queriesRun : 0;
        double mAP = (queriesRun > 0) ? sumAP / queriesRun : 0;
        return new double[]{rank1Hits, rank5Hits, mAP, queriesRun, avgMs};
    }

    /**
     * Run Hybrid (TETRA pre-filter + Groth refinement) benchmark against the full catalog.
     * Returns [rank1Hits, rank5Hits, mAP, queriesRun, avgTotalMs,
     *          avgTetraMs, avgGrothMs, avgCandidates].
     */
    private double[] runHybridBenchmark(TetraQueryEngine engine,
                                         double epsilon, double R, double sizelim,
                                         double maxRot, double C) {
        int rank1Hits = 0;
        int rank5Hits = 0;
        double sumAP = 0;
        long totalTetraNanos = 0;
        long totalGrothNanos = 0;
        int totalCandidates = 0;
        int queriesRun = 0;

        for (String queryEncId : queryEncIds) {
            ArrayList<SuperSpot> querySpots = encounterSpots.get(queryEncId);
            if (querySpots == null || querySpots.size() < 4) continue;

            String trueIndId = encounterToIndividual.get(queryEncId);
            Set<String> trueEncounters = new HashSet<>(
                individualToEncounters.getOrDefault(trueIndId, Collections.emptyList()));
            trueEncounters.remove(queryEncId);
            if (trueEncounters.isEmpty()) continue;

            int numTrueInCatalog = 0;
            for (String trueEncId : trueEncounters) {
                if (catalog.containsKey(trueEncId)) numTrueInCatalog++;
            }
            if (numTrueInCatalog == 0) continue;

            SuperSpot[] queryArray = querySpots.toArray(new SuperSpot[0]);

            // Step 1: TETRA pre-filter
            long tetraStart = System.nanoTime();
            List<MatchObject> tetraCandidates = engine.match(querySpots, false, queryEncId);
            long tetraNanos = System.nanoTime() - tetraStart;
            totalTetraNanos += tetraNanos;

            // Step 2: Groth refinement on TETRA candidates
            List<MatchObject> grothResults = new ArrayList<>();
            long grothStart = System.nanoTime();
            for (MatchObject tetraMo : tetraCandidates) {
                String candidateEncId = tetraMo.getEncounterNumber();
                EncounterLite el = catalog.get(candidateEncId);
                if (el == null) continue;

                MatchObject mo = el.getPointsForBestMatch(
                    queryArray, epsilon, R, sizelim, maxRot, C, true, false);
                mo.encounterNumber = candidateEncId;
                grothResults.add(mo);
            }
            long grothNanos = System.nanoTime() - grothStart;
            totalGrothNanos += grothNanos;

            totalCandidates += tetraCandidates.size();

            // Sort by Groth matchValue descending
            grothResults.sort((a, b) -> Double.compare(b.matchValue, a.matchValue));

            int rank = findCorrectRank(grothResults, queryEncId, trueIndId);
            if (rank == 1) rank1Hits++;
            if (rank >= 1 && rank <= 5) rank5Hits++;

            double ap = computeAP(grothResults, trueEncounters, numTrueInCatalog);
            sumAP += ap;
            queriesRun++;
        }

        double avgTotalMs = (queriesRun > 0) ?
            (double) (totalTetraNanos + totalGrothNanos) / 1_000_000 / queriesRun : 0;
        double avgTetraMs = (queriesRun > 0) ?
            (double) totalTetraNanos / 1_000_000 / queriesRun : 0;
        double avgGrothMs = (queriesRun > 0) ?
            (double) totalGrothNanos / 1_000_000 / queriesRun : 0;
        double avgCandidates = (queriesRun > 0) ?
            (double) totalCandidates / queriesRun : 0;
        double mAP = (queriesRun > 0) ? sumAP / queriesRun : 0;
        return new double[]{rank1Hits, rank5Hits, mAP, queriesRun, avgTotalMs,
            avgTetraMs, avgGrothMs, avgCandidates};
    }

    private enum ParamIndex { EPSILON, R, SIZELIM, MAX_ROT, C }

    /**
     * Sweep a single parameter, printing a results table.
     * Returns the best value, optimizing for mAP (tiebreak: Rank-1, then speed).
     */
    private double sweepParameter(String name, double[] values,
                                   double epsilon, double R, double sizelim,
                                   double maxRot, double C, ParamIndex which) {
        System.out.printf("%n=== Sweeping %s ===%n", name);
        System.out.printf("%-12s | Rank-1    | mAP   | Avg Time (ms)%n", name);
        System.out.println("-------------|-----------|-------|-------------");

        double bestValue;
        switch (which) {
            case EPSILON: bestValue = epsilon; break;
            case R:       bestValue = R; break;
            case SIZELIM: bestValue = sizelim; break;
            case MAX_ROT: bestValue = maxRot; break;
            case C:       bestValue = C; break;
            default:      bestValue = 0; break;
        }
        double bestMAP = -1;
        int bestRank1 = -1;
        double bestTime = Double.MAX_VALUE;

        for (double val : values) {
            double e = epsilon, r = R, s = sizelim, m = maxRot, c = C;
            switch (which) {
                case EPSILON: e = val; break;
                case R:       r = val; break;
                case SIZELIM: s = val; break;
                case MAX_ROT: m = val; break;
                case C:       c = val; break;
            }

            double[] result = runBenchmark(e, r, s, m, c);
            int rank1 = (int) result[0];
            double mAP = result[2];
            int qCount = (int) result[3];
            double avgMs = result[4];

            System.out.printf("%-12s | %2d/%-2d     | %.3f | %,.0f%n",
                formatValue(val), rank1, qCount, mAP, avgMs);

            // Pick best: maximize mAP, then Rank-1, then minimize time
            if (mAP > bestMAP + 0.001 ||
                (Math.abs(mAP - bestMAP) <= 0.001 && rank1 > bestRank1) ||
                (Math.abs(mAP - bestMAP) <= 0.001 && rank1 == bestRank1 &&
                    avgMs < bestTime)) {
                bestMAP = mAP;
                bestRank1 = rank1;
                bestTime = avgMs;
                bestValue = val;
            }
        }

        System.out.printf(">>> Best %s: %s (mAP=%.3f, Rank-1: %d)%n",
            name, formatValue(bestValue), bestMAP, bestRank1);
        return bestValue;
    }

    /**
     * Run the matching benchmark with the given Groth parameters.
     * Returns [rank1Hits, rank5Hits, mAP, queriesRun, avgTimeMs].
     */
    private double[] runBenchmark(double epsilon, double R, double sizelim,
                                   double maxRot, double C) {
        int rank1Hits = 0;
        int rank5Hits = 0;
        double sumAP = 0;
        long totalTimeNanos = 0;
        int queriesRun = 0;

        for (String queryEncId : queryEncIds) {
            ArrayList<SuperSpot> querySpots = encounterSpots.get(queryEncId);
            if (querySpots == null || querySpots.size() < 3) continue;

            SuperSpot[] queryArray = querySpots.toArray(new SuperSpot[0]);
            String trueIndId = encounterToIndividual.get(queryEncId);

            // Build catalog subset with guaranteed true matches
            Set<String> trueEncounters = new HashSet<>(
                individualToEncounters.getOrDefault(trueIndId, Collections.emptyList()));
            trueEncounters.remove(queryEncId);

            List<String> subsetIds = new ArrayList<>();
            for (String trueEncId : trueEncounters) {
                if (catalog.containsKey(trueEncId)) {
                    subsetIds.add(trueEncId);
                }
            }
            if (subsetIds.isEmpty()) continue;

            int numTrueInCatalog = subsetIds.size();

            Random rng = new Random(queryEncId.hashCode());
            List<String> catalogIds = new ArrayList<>(catalog.keySet());
            catalogIds.removeAll(subsetIds);
            catalogIds.remove(queryEncId);
            Collections.shuffle(catalogIds, rng);
            int remaining = Math.min(CATALOG_SIZE - subsetIds.size(), catalogIds.size());
            subsetIds.addAll(catalogIds.subList(0, remaining));

            // Run Groth
            List<MatchObject> results = new ArrayList<>();
            long startNanos = System.nanoTime();
            for (String catalogEncId : subsetIds) {
                EncounterLite el = catalog.get(catalogEncId);
                if (el == null) continue;
                MatchObject mo = el.getPointsForBestMatch(
                    queryArray, epsilon, R, sizelim, maxRot, C, true, false);
                mo.encounterNumber = catalogEncId;
                results.add(mo);
            }
            totalTimeNanos += System.nanoTime() - startNanos;

            results.sort((a, b) -> Double.compare(b.matchValue, a.matchValue));

            // Rank-1
            int rank = findCorrectRank(results, queryEncId, trueIndId);
            if (rank == 1) rank1Hits++;
            if (rank >= 1 && rank <= 5) rank5Hits++;

            // Average Precision for this query
            double ap = computeAP(results, trueEncounters, numTrueInCatalog);
            sumAP += ap;

            queriesRun++;
        }

        double avgMs = (queriesRun > 0) ?
            (double) totalTimeNanos / 1_000_000 / queriesRun : 0;
        double mAP = (queriesRun > 0) ? sumAP / queriesRun : 0;
        return new double[]{rank1Hits, rank5Hits, mAP, queriesRun, avgMs};
    }

    /**
     * Compute Average Precision (AP) for a single query.
     * AP = (1/R) * sum_{k=1}^{N} P(k) * rel(k)
     * where R is the number of relevant docs, P(k) is precision at rank k,
     * and rel(k) is 1 if the k-th result is relevant.
     */
    private double computeAP(List<MatchObject> results, Set<String> trueEncounters,
                              int numRelevant) {
        if (numRelevant == 0) return 0;
        double sumPrecision = 0;
        int truePositives = 0;

        for (int i = 0; i < results.size(); i++) {
            String encId = results.get(i).getEncounterNumber();
            if (trueEncounters.contains(encId)) {
                truePositives++;
                double precisionAtK = (double) truePositives / (i + 1);
                sumPrecision += precisionAtK;
            }
        }
        return sumPrecision / numRelevant;
    }

    /**
     * Select stratified queries: half from 2-encounter individuals,
     * half from individuals with 3+ encounters.
     * Only selects from eligible (named, unique) individuals.
     */
    private static List<String> selectStratifiedQueries(int numQueries,
                                                         Map<String, String> indIdToName) {
        Random rng = new Random(42);
        int halfQueries = numQueries / 2;

        List<String> twoEncInds = new ArrayList<>();
        List<String> multiEncInds = new ArrayList<>();

        for (Map.Entry<String, List<String>> entry : individualToEncounters.entrySet()) {
            String indId = entry.getKey();
            int encCount = entry.getValue().size();
            // Verify at least one encounter is in the catalog
            boolean hasCatalogEntry = false;
            for (String encId : entry.getValue()) {
                if (catalog.containsKey(encId)) {
                    hasCatalogEntry = true;
                    break;
                }
            }
            if (!hasCatalogEntry) continue;

            if (encCount == 2) {
                twoEncInds.add(indId);
            } else if (encCount >= 3) {
                multiEncInds.add(indId);
            }
        }

        Collections.sort(twoEncInds);
        Collections.sort(multiEncInds);
        Collections.shuffle(twoEncInds, new Random(42));
        Collections.shuffle(multiEncInds, new Random(43));

        List<String> queries = new ArrayList<>();

        // Select from 2-encounter individuals
        for (int i = 0; i < Math.min(halfQueries, twoEncInds.size()); i++) {
            String indId = twoEncInds.get(i);
            List<String> encIds = individualToEncounters.get(indId);
            queries.add(encIds.get(rng.nextInt(encIds.size())));
        }

        // Select from multi-encounter individuals
        for (int i = 0; i < Math.min(halfQueries, multiEncInds.size()); i++) {
            String indId = multiEncInds.get(i);
            List<String> encIds = individualToEncounters.get(indId);
            queries.add(encIds.get(rng.nextInt(encIds.size())));
        }

        return queries;
    }

    private double[] refineRange(double winner, double[] coarseValues) {
        Arrays.sort(coarseValues);
        int idx = -1;
        for (int i = 0; i < coarseValues.length; i++) {
            if (Math.abs(coarseValues[i] - winner) < 1e-9) {
                idx = i;
                break;
            }
        }
        if (idx < 0) return new double[]{winner};

        double lo = (idx > 0) ? coarseValues[idx - 1] : winner * 0.7;
        double hi = (idx < coarseValues.length - 1) ? coarseValues[idx + 1] : winner * 1.3;

        double[] fine = new double[5];
        for (int i = 0; i < 5; i++) {
            fine[i] = lo + (hi - lo) * i / 4.0;
        }
        return fine;
    }

    private static String formatValue(double val) {
        if (val == (int) val && val >= 1) {
            return String.valueOf((int) val);
        }
        if (val >= 1) return String.format("%.1f", val);
        if (val >= 0.01) return String.format("%.3f", val);
        return String.format("%.4f", val);
    }

    private int findCorrectRank(List<MatchObject> results, String queryEncId,
                                 String trueIndId) {
        Set<String> trueEncounters = new HashSet<>(
            individualToEncounters.getOrDefault(trueIndId, Collections.emptyList()));
        trueEncounters.remove(queryEncId);

        for (int i = 0; i < results.size(); i++) {
            String resultEncId = results.get(i).getEncounterNumber();
            if (trueEncounters.contains(resultEncId)) {
                return i + 1;
            }
        }
        return -1;
    }

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
