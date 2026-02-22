package org.ecocean.grid;

import org.ecocean.SuperSpot;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import static org.junit.jupiter.api.Assertions.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

/**
 * Benchmark test for optimized Groth algorithm.
 *
 * Loads benchmark spot data and runs Groth matching against a sample of
 * catalog encounters, measuring accuracy and per-query timing.
 *
 * Run with: mvn test -Dtest=org.ecocean.grid.GrothOptimizationTest \
 *           -Dtetra.benchmark.csv=tetra_benchmark_spots.csv \
 *           -DargLine="--add-opens java.base/java.lang=ALL-UNNAMED -Xmx4g"
 */
@EnabledIf("csvFileExists")
class GrothOptimizationTest {

    private static final String CSV_PATH = System.getProperty(
        "tetra.benchmark.csv", "tetra_benchmark_spots.csv");

    static boolean csvFileExists() {
        return new File(CSV_PATH).exists();
    }

    // Groth optimized parameters (from GrothParameterSweepTest coordinate descent)
    private static final double GROTH_EPSILON = 0.008;
    private static final double GROTH_R = 6.8;
    private static final double GROTH_SIZELIM = 0.671;
    private static final double GROTH_MAX_ROTATION = 22.5;
    private static final double GROTH_C = 1.146;

    // Data structures
    private static Map<String, ArrayList<SuperSpot>> encounterSpots;
    private static Map<String, String> encounterToIndividual;
    private static Map<String, List<String>> individualToEncounters;
    private static List<String> allEncounterIds;

    @BeforeAll
    static void loadData() throws IOException {
        encounterSpots = new LinkedHashMap<>();
        encounterToIndividual = new HashMap<>();
        individualToEncounters = new HashMap<>();

        try (BufferedReader br = new BufferedReader(new FileReader(CSV_PATH))) {
            String header = br.readLine();
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

        System.out.println("=== Groth Optimization Benchmark Data ===");
        System.out.println("Encounters: " + allEncounterIds.size());
        System.out.println("Individuals: " + individualToEncounters.size());
        System.out.println();
    }

    /**
     * Benchmark: Run Groth on 20 queries against a subset of 200 catalog encounters.
     * Measures per-query timing and rank-1 accuracy.
     *
     * This simulates the hybrid TETRA-prefilter scenario where Groth only
     * needs to verify a small candidate set (100-500 encounters).
     */
    @Test
    void benchmarkGrothOnSubset() {
        System.out.println("========================================");
        System.out.println("  OPTIMIZED GROTH - SUBSET BENCHMARK");
        System.out.println("========================================\n");

        int NUM_QUERIES = 20;
        int CATALOG_SIZE = 200;

        // Build catalog of EncounterLite objects
        Map<String, EncounterLite> catalog = new LinkedHashMap<>();
        for (String encId : allEncounterIds) {
            ArrayList<SuperSpot> spots = encounterSpots.get(encId);
            if (spots.size() >= 3) {
                EncounterLite el = new EncounterLite();
                el.processLeftSpots(spots);
                catalog.put(encId, el);
            }
        }

        // Select query encounters from different individuals
        List<String> queryEncIds = selectQueryEncounters(NUM_QUERIES);

        int rank1Hits = 0;
        int rank5Hits = 0;
        long totalTimeNanos = 0;
        int queriesRun = 0;

        for (String queryEncId : queryEncIds) {
            ArrayList<SuperSpot> querySpots = encounterSpots.get(queryEncId);
            if (querySpots == null || querySpots.size() < 3) continue;

            SuperSpot[] queryArray = querySpots.toArray(new SuperSpot[0]);
            String trueIndId = encounterToIndividual.get(queryEncId);

            // Build a catalog subset: ensure at least one true match + random others
            Set<String> trueEncounters = new HashSet<>(
                individualToEncounters.getOrDefault(trueIndId, Collections.emptyList()));
            trueEncounters.remove(queryEncId);

            List<String> subsetIds = new ArrayList<>();
            // Add all true matches first
            for (String trueEncId : trueEncounters) {
                if (catalog.containsKey(trueEncId)) {
                    subsetIds.add(trueEncId);
                }
            }
            if (subsetIds.isEmpty()) continue; // skip if no true match in catalog

            // Fill remaining slots with random catalog entries
            Random rng = new Random(queryEncId.hashCode());
            List<String> catalogIds = new ArrayList<>(catalog.keySet());
            catalogIds.removeAll(subsetIds);
            catalogIds.remove(queryEncId);
            Collections.shuffle(catalogIds, rng);
            int remaining = Math.min(CATALOG_SIZE - subsetIds.size(), catalogIds.size());
            subsetIds.addAll(catalogIds.subList(0, remaining));

            // Run Groth matching
            List<MatchObject> results = new ArrayList<>();
            long startNanos = System.nanoTime();
            for (String catalogEncId : subsetIds) {
                EncounterLite el = catalog.get(catalogEncId);
                if (el == null) continue;
                MatchObject mo = el.getPointsForBestMatch(
                    queryArray, GROTH_EPSILON, GROTH_R, GROTH_SIZELIM,
                    GROTH_MAX_ROTATION, GROTH_C, true, false);
                mo.encounterNumber = catalogEncId;
                results.add(mo);
            }
            long elapsedNanos = System.nanoTime() - startNanos;
            totalTimeNanos += elapsedNanos;

            // Sort by matchValue descending
            results.sort((a, b) -> Double.compare(b.matchValue, a.matchValue));

            // Find rank
            int rank = findCorrectRank(results, queryEncId, trueIndId);
            if (rank == 1) rank1Hits++;
            if (rank >= 1 && rank <= 5) rank5Hits++;

            queriesRun++;
            System.out.printf("Query %d/%d: enc=%s rank=%d time=%.0fms (%d candidates)%n",
                queriesRun, NUM_QUERIES, queryEncId.substring(0, 8),
                rank, elapsedNanos / 1_000_000.0, subsetIds.size());
        }

        double avgMs = (totalTimeNanos / 1_000_000.0) / queriesRun;
        System.out.println("\n========================================");
        System.out.println("  RESULTS");
        System.out.println("========================================");
        System.out.printf("Queries run:     %d%n", queriesRun);
        System.out.printf("Avg query time:  %.1f ms (against %d encounters)%n", avgMs, CATALOG_SIZE);
        System.out.printf("Rank-1 accuracy: %d/%d (%.1f%%)%n",
            rank1Hits, queriesRun, 100.0 * rank1Hits / queriesRun);
        System.out.printf("Rank-5 accuracy: %d/%d (%.1f%%)%n",
            rank5Hits, queriesRun, 100.0 * rank5Hits / queriesRun);
        System.out.println("========================================\n");

        // Assert reasonable performance
        assertTrue(avgMs < 30000,
            "Average query time should be < 30s for 200 encounters, was " + avgMs + "ms");
    }

    /**
     * Benchmark: Run Groth on 5 queries against ALL catalog encounters.
     * This is the full-scan baseline for measuring total speedup.
     */
    @Test
    void benchmarkGrothFullScan() {
        System.out.println("========================================");
        System.out.println("  OPTIMIZED GROTH - FULL SCAN BENCHMARK");
        System.out.println("========================================\n");

        int NUM_QUERIES = 5;

        // Build catalog
        Map<String, EncounterLite> catalog = new LinkedHashMap<>();
        for (String encId : allEncounterIds) {
            ArrayList<SuperSpot> spots = encounterSpots.get(encId);
            if (spots.size() >= 3) {
                EncounterLite el = new EncounterLite();
                el.processLeftSpots(spots);
                catalog.put(encId, el);
            }
        }

        List<String> queryEncIds = selectQueryEncounters(NUM_QUERIES);

        int rank1Hits = 0;
        long totalTimeNanos = 0;
        int queriesRun = 0;

        for (String queryEncId : queryEncIds) {
            ArrayList<SuperSpot> querySpots = encounterSpots.get(queryEncId);
            if (querySpots == null || querySpots.size() < 3) continue;

            SuperSpot[] queryArray = querySpots.toArray(new SuperSpot[0]);
            String trueIndId = encounterToIndividual.get(queryEncId);

            List<MatchObject> results = new ArrayList<>();
            long startNanos = System.nanoTime();
            for (Map.Entry<String, EncounterLite> entry : catalog.entrySet()) {
                if (entry.getKey().equals(queryEncId)) continue;
                MatchObject mo = entry.getValue().getPointsForBestMatch(
                    queryArray, GROTH_EPSILON, GROTH_R, GROTH_SIZELIM,
                    GROTH_MAX_ROTATION, GROTH_C, true, false);
                mo.encounterNumber = entry.getKey();
                results.add(mo);
            }
            long elapsedNanos = System.nanoTime() - startNanos;
            totalTimeNanos += elapsedNanos;

            results.sort((a, b) -> Double.compare(b.matchValue, a.matchValue));
            int rank = findCorrectRank(results, queryEncId, trueIndId);
            if (rank == 1) rank1Hits++;

            queriesRun++;
            System.out.printf("Query %d/%d: enc=%s rank=%d time=%.1fs (%d encounters)%n",
                queriesRun, NUM_QUERIES, queryEncId.substring(0, 8),
                rank, elapsedNanos / 1_000_000_000.0, catalog.size());
        }

        double avgSec = (totalTimeNanos / 1_000_000_000.0) / queriesRun;
        System.out.println("\n========================================");
        System.out.println("  FULL SCAN RESULTS");
        System.out.println("========================================");
        System.out.printf("Queries run:     %d%n", queriesRun);
        System.out.printf("Catalog size:    %d encounters%n", catalog.size());
        System.out.printf("Avg query time:  %.1f seconds%n", avgSec);
        System.out.printf("Rank-1 accuracy: %d/%d (%.1f%%)%n",
            rank1Hits, queriesRun, 100.0 * rank1Hits / queriesRun);
        System.out.println("========================================\n");
    }

    private List<String> selectQueryEncounters(int numQueries) {
        Random rng = new Random(42);
        List<String> queries = new ArrayList<>();
        List<String> individuals = new ArrayList<>(individualToEncounters.keySet());
        Collections.sort(individuals);

        for (int i = 0; i < Math.min(numQueries, individuals.size()); i++) {
            List<String> encIds = individualToEncounters.get(individuals.get(i));
            queries.add(encIds.get(rng.nextInt(encIds.size())));
        }
        return queries;
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
