/*
    aka "Tweet-a-Whale" .. implements listening for and processing tweets
 */
package org.ecocean;

import io.prometheus.client.CollectorRegistry;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.lang.Runnable;
import java.net.URI;
import java.text.Normalizer;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.StringTokenizer;
import javax.jdo.Query;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.ecocean.ia.IA;
import org.ecocean.metrics.Prometheus;
import org.ecocean.queue.QueueUtil;
import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONObject;

public class MetricsBot {
    private static long collectorStartTime = 0l;

    public static String csvFile = "/data/metrics/metrics.csv";

    static String context = "context0";

    private static RateLimitation outgoingRL = new RateLimitation(48 * 60 * 60 * 1000); // only care about last 48 hrs

    public static String rateLimitationInfo() {
        return outgoingRL.toString();
    }

    // background workers
    public static boolean startServices(String context) {
        startCollector(context);
        return true;
    }

    // basically our "listener" daemon; but is more pull (poll?) than push so to speak.
    private static void startCollector(final String context) { // throws IOException {
        collectorStartTime = System.currentTimeMillis(); 
        long interval = 60; // number minutes between metrics refreshes of data in the CSV
        long initialDelay = 1; // number minutes before first execution occurs
        System.out.println("+ MetricsBot.startCollector(" + context + ") starting.");
        final ScheduledExecutorService schedExec = Executors.newScheduledThreadPool(1);
        final ScheduledFuture schedFuture = schedExec.scheduleWithFixedDelay(new Runnable() {
            int count = 0;

            // DO METRICS WORK HERE
            public void run() {
                ++count;
                if (new java.io.File("/tmp/WB_METRICSBOT_SHUTDOWN").exists()) {
                    System.out.println("INFO: MetricsBot.startCollection(" + context +
                    ") shutting down due to file signal");
                    schedExec.shutdown();
                    return;
                }
                refreshMetrics(context);
            }
        }, initialDelay, // initial delay
            interval, // period delay *after* execution finishes
            TimeUnit.MINUTES); // unit of delays above

        System.out.println("Let's get MetricsBot's time running.");
        try {
            schedExec.awaitTermination(5000, TimeUnit.MILLISECONDS);
        } catch (java.lang.InterruptedException ex) {
            System.out.println("WARNING: MetricsBot.startCollector(" + context + ") interrupted: " +
                ex.toString());
        }
        System.out.println("+ MetricsBot.startCollector(" + context + ") backgrounded");
    }

    // mostly for ContextDestroyed in StartupWildbook
    public static void cleanup() {
        System.out.println(
            "================ = = = = = = ===================== MetricsBot.cleanup() finished.");
    }

    public static String buildGauge(String filter, String name, String help, String context) {
        return buildGauge(filter, name, help, context, null);
    }

    public static String buildGauge(String filter, String name, String help, String context,
        String label) {
        // System.out.println("-- Collecting metrics for: "+ name);
        // System.out.println("        "+ filter);
        String line = null;
        Shepherd myShepherd = new Shepherd(context);

        myShepherd.setAction("MetricsBot_buildGauge_" + name);
        myShepherd.beginDBTransaction();
        Query q = null;
        try {
            Long myValue = null;
            q = myShepherd.getPM().newQuery(filter);
            myValue = (Long)q.execute();
            if (myValue != null) {
                line = name + "," + myValue.toString() + "," + "gauge" + "," + help;
            }
            if (label != null) line += "," + label;
        } catch (java.lang.IllegalArgumentException badArg) {
            System.out.println("MetricsBot.buildGauge called with bad arguments.");
            badArg.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (q != null) q.closeAll();
            myShepherd.rollbackAndClose();
        }
        // System.out.println("   -- Done: "+ line);
        return line;
    }

    // Helper method to safely parse label from buildGauge result
    private static String parseLabelFromGauge(String gaugeResult, String prefix) {
        if (gaugeResult == null) return null;
        StringTokenizer str = new StringTokenizer(gaugeResult, ",");
        if (str.countTokens() < 2) return null;
        return prefix + str.nextToken() + ":" + str.nextToken() + ",";
    }

    public static void refreshMetrics(String context) {
        // NOTE: We no longer clear the registry at the start.
        // Instead, we build all metrics into the CSV first, then atomically
        // swap by clearing and reloading only after successful CSV write.
        // This prevents blank /metrics responses during refresh.

        // first, make sure metrics file exists
        // if not, create it
        File metricsFile = new File(csvFile);
        File metricsDir = metricsFile.getParentFile();
        File tempFile = new File(csvFile + ".tmp");
        try {
            if (!metricsDir.exists()) {
                boolean created = metricsDir.mkdirs();
                if (!created)
                    throw new Exception("Could not create directory: " +
                            metricsDir.getAbsolutePath());
            }
            // store our CSV lines for writing
            ArrayList<String> csvLines = new ArrayList<String>();

            // execute queries to get metrics

            addLineIfNotNull(csvLines, buildGauge("SELECT count(this) FROM org.ecocean.media.MediaAsset",
                "wildbook_mediaassets_total", "Number of media assets", context));
            addLineIfNotNull(csvLines, buildGauge("SELECT count(this) FROM org.ecocean.Occurrence",
                "wildbook_sightings_total", "Number of sightings", context));
            addLineIfNotNull(csvLines, buildGauge("SELECT count(this) FROM org.ecocean.Annotation",
                "wildbook_annotations_total", "Number of annotations", context));

            // User-related
            ZonedDateTime now = ZonedDateTime.now();
            ZonedDateTime oldDay = now.minusDays(1);
            long oneDayAgo = oldDay.toInstant().toEpochMilli();
            ZonedDateTime oldWeek = now.minusDays(7);
            long oneWeekAgo = oldWeek.toInstant().toEpochMilli();
            ZonedDateTime oldMonth = now.minusMonths(1);
            long oneMonthAgo = oldMonth.toInstant().toEpochMilli();
            ZonedDateTime oldYear = now.minusYears(1);
            long oneYearAgo = oldYear.toInstant().toEpochMilli();

            // Taxonomy has to be treated differently because of past data pollution from Spotter app
            List<String> taxa = CommonConfiguration.getIndexedPropertyValues("genusSpecies",
                context);
            if (taxa != null) {
                System.out.println("-- Collecting metrics for: wildbook_taxonomies_total");
                csvLines.add("wildbook_taxonomies_total" + "," + taxa.size() + "," + "gauge" + "," +
                    "Number of species");
                System.out.println("   -- Done");
            }
            String encLabels = "";
            String indyLabels = "";
            if (taxa != null) {
                for (String tax : taxa) {
                    StringTokenizer str = new StringTokenizer(tax, " ");
                    if (str.countTokens() > 1) {
                        String genus = str.nextToken();
                        String specificEpithet = str.nextToken();
                        if (str.hasMoreTokens()) specificEpithet += " " + str.nextToken();
                        String indyLabelTemp = buildGauge(
                            "SELECT count(this) FROM org.ecocean.MarkedIndividual where encounters.contains(enc) && enc.specificEpithet == '"
                            + specificEpithet.replaceAll("_", " ") + "'",
                            (genus + "_" + specificEpithet.replaceAll(" ", "_")),
                            "Number of marked individuals (" + genus + " " + specificEpithet + ")",
                            context);
                        String indyLabel = parseLabelFromGauge(indyLabelTemp, "species_");
                        if (indyLabel != null) indyLabels += indyLabel;

                        String encLabelTemp = buildGauge(
                            "SELECT count(this) FROM org.ecocean.Encounter where specificEpithet == '" +
                            specificEpithet.replaceAll("_", " ") + "'",
                            (genus + "_" + specificEpithet.replaceAll(" ", "_")),
                            "Number of encounters (" + genus + " " + specificEpithet + ")", context);
                        String encLabel = parseLabelFromGauge(encLabelTemp, "species_");
                        if (encLabel != null) encLabels += encLabel;
                    }
                }
            }
            String indyLabelTemp = buildGauge(
                "SELECT count(this) FROM org.ecocean.MarkedIndividual", "*",
                "Number of marked individuals", context);
            String indyLabelParsed = parseLabelFromGauge(indyLabelTemp, "species_");
            if (indyLabelParsed != null) indyLabels += indyLabelParsed;

            String encLabelTemp = buildGauge("SELECT count(this) FROM org.ecocean.Encounter", "*",
                "Number of encounters", context);
            String encLabelParsed = parseLabelFromGauge(encLabelTemp, "species_");
            if (encLabelParsed != null) encLabels += encLabelParsed;
            if (encLabels.equals("")) encLabels = null;
            else if (encLabels.endsWith(",")) {
                encLabels = "\"" + encLabels.substring(0, (encLabels.length() - 1)) + "\"";
            }
            if (indyLabels.equals("")) indyLabels = null;
            else if (indyLabels.endsWith(",")) {
                indyLabels = "\"" + indyLabels.substring(0, (indyLabels.length() - 1)) + "\"";
            }
            addLineIfNotNull(csvLines, buildGauge("SELECT count(this) FROM org.ecocean.Encounter",
                "wildbook_encounters_total", "Number of encounters", context, encLabels));
            addLineIfNotNull(csvLines, buildGauge("SELECT count(this) FROM org.ecocean.MarkedIndividual",
                "wildbook_individuals_total", "Number of marked individuals", context, indyLabels));

            // Database vs OpenSearch sync drift metrics
            try {
                // Encounter sync drift
                long dbEncounterCount = getDatabaseCount("org.ecocean.Encounter", context);
                long osEncounterCount = getOpenSearchIndexCount("encounter");
                if (dbEncounterCount >= 0 && osEncounterCount >= 0) {
                    long encounterDrift = dbEncounterCount - osEncounterCount;
                    csvLines.add("wildbook_encounters_db_count," + dbEncounterCount + ",gauge,Database encounter count");
                    csvLines.add("wildbook_encounters_opensearch_count," + osEncounterCount + ",gauge,OpenSearch encounter count");
                    csvLines.add("wildbook_encounters_sync_drift," + encounterDrift + ",gauge,Encounter count difference (DB minus OpenSearch)");
                    System.out.println("MetricsBot: Encounter sync - DB: " + dbEncounterCount + " OS: " + osEncounterCount + " Drift: " + encounterDrift);
                }

                // Individual sync drift
                long dbIndividualCount = getDatabaseCount("org.ecocean.MarkedIndividual", context);
                long osIndividualCount = getOpenSearchIndexCount("individual");
                if (dbIndividualCount >= 0 && osIndividualCount >= 0) {
                    long individualDrift = dbIndividualCount - osIndividualCount;
                    csvLines.add("wildbook_individuals_db_count," + dbIndividualCount + ",gauge,Database individual count");
                    csvLines.add("wildbook_individuals_opensearch_count," + osIndividualCount + ",gauge,OpenSearch individual count");
                    csvLines.add("wildbook_individuals_sync_drift," + individualDrift + ",gauge,Individual count difference (DB minus OpenSearch)");
                    System.out.println("MetricsBot: Individual sync - DB: " + dbIndividualCount + " OS: " + osIndividualCount + " Drift: " + individualDrift);
                }
            } catch (Exception syncEx) {
                System.out.println("MetricsBot: Error calculating sync drift metrics: " + syncEx.getMessage());
            }

            // User analysis
            String userLabels = "";
            String dayLabel = buildGauge(
                "SELECT count(this) FROM org.ecocean.User where username != null && lastLogin > " +
                oneDayAgo, "lastDayLogin", "Number of users logging in (24 hours)", context);
            String dayLabelParsed = parseLabelFromGauge(dayLabel, "login_");
            if (dayLabelParsed != null) userLabels += dayLabelParsed;

            String weekLabel = buildGauge(
                "SELECT count(this) FROM org.ecocean.User where username != null && lastLogin > " +
                oneWeekAgo, "lastWeekLogin", "Number of users logging in (Last 7 days)", context);
            String weekLabelParsed = parseLabelFromGauge(weekLabel, "login_");
            if (weekLabelParsed != null) userLabels += weekLabelParsed;

            String monthLabel = buildGauge(
                "SELECT count(this) FROM org.ecocean.User where username != null && lastLogin > " +
                oneMonthAgo, "lastMonthLogin", "Number of users logging in (Last 30 days)",
                context);
            String monthLabelParsed = parseLabelFromGauge(monthLabel, "login_");
            if (monthLabelParsed != null) userLabels += monthLabelParsed;

            String yearLabel = buildGauge(
                "SELECT count(this) FROM org.ecocean.User where username != null && lastLogin > " +
                oneYearAgo, "lastYearLogin", "Number of users logging in (Last 365 days)", context);
            String yearLabelParsed = parseLabelFromGauge(yearLabel, "login_");
            if (yearLabelParsed != null) userLabels += yearLabelParsed;

            String userLabelTemp = buildGauge(
                "SELECT count(this) FROM org.ecocean.User WHERE username != null && lastLogin > 0",
                "*", "Number of users", context);
            String userLabelParsed = parseLabelFromGauge(userLabelTemp, "login_");
            if (userLabelParsed != null) userLabels += userLabelParsed;
            if (userLabels.equals("")) userLabels = null;
            else if (userLabels.endsWith(",")) {
                userLabels = "\"" + userLabels.substring(0, (userLabels.length() - 1)) + "\"";
            }
            addLineIfNotNull(csvLines, buildGauge(
                "SELECT count(this) FROM org.ecocean.User WHERE username != null && lastLogin > 0",
                "wildbook_users_total", "Number of users", context, userLabels));

            // data contributors
            String contributorsLabels = "";
            String allContribLabel = buildGauge("SELECT count(this) FROM org.ecocean.User", "*",
                "Number of data contributors", context);
            String allContribParsed = parseLabelFromGauge(allContribLabel, "contributor_");
            if (allContribParsed != null) contributorsLabels += allContribParsed;

            String publicContribLabel = buildGauge(
                "SELECT count(this) FROM org.ecocean.User WHERE username == null", "public",
                "Number of public data contributors", context);
            String publicContribParsed = parseLabelFromGauge(publicContribLabel, "contributor_");
            if (publicContribParsed != null) contributorsLabels += publicContribParsed;
            if (contributorsLabels.equals("")) contributorsLabels = null;
            else if (contributorsLabels.endsWith(",")) {
                contributorsLabels = "\"" + contributorsLabels.substring(0,
                    (contributorsLabels.length() - 1)) + "\"";
            }
            addLineIfNotNull(csvLines, buildGauge(
                "SELECT count(this) FROM org.ecocean.User WHERE username == null",
                "wildbook_datacontributors_total", "Number of public data contributors", context,
                contributorsLabels));

            //Issue 532 - find number Encounters owned by User 'public'
            addLineIfNotNull(csvLines, buildGauge("SELECT count(this) FROM org.ecocean.Encounter where submitterID == 'public'",
                    "wildbook_encounters_public_owned_total", "Number of public owned encounters", context, encLabels));

            //Issue 532 - number of encounters submitted by researcher: encounters submitted by accounts that have researcher role
            addLineIfNotNull(csvLines, buildGauge("SELECT count(this) FROM org.ecocean.Encounter where submitterID == role.username && role.rolename=='researcher' VARIABLES org.ecocean.Role role",
                    "wildbook_encounters_researcher_owned_total", "Number of researcher owned encounters", context, encLabels));

            //Issue 532 - number of encounters submitted by citizen scientist: encounters submitted by accounts that do not have a role
            addLineIfNotNull(csvLines, buildGauge("SELECT count(this) FROM org.ecocean.Encounter where submitterID == null || submitterID == 'public' || !(select distinct username from org.ecocean.Role where rolename=='researcher').contains(submitterID)",
                    "wildbook_encounters_citsci_contributed_total", "Number of citizen science contributed encounters", context, encLabels));


            // Machine learning tasks
            addTasksToCsv(csvLines, context);

            // WBIA Metrics pull
            String metricsURL = IA.getProperty(context, "IBEISIARestUrlAddImages");
            if (metricsURL != null) {
                metricsURL = metricsURL.replaceAll("/api/image/json/", "") + "/metrics";
                String wbiaMetrics = httpGetRemoteText(metricsURL);

                // WBIA turnaround time all task types
                String regexTT = "wbia_turnaround_seconds\\{endpoint=\"\\*\".*\\} \\d*\\.\\d*";
                String promValueTT = getWBIAPrometheusClientValue(wbiaMetrics, regexTT);
                if (promValueTT != null) {
                    csvLines.add("wildbook_wbia_turnaroundtime" + "," + promValueTT + "," +
                        "gauge" + "," + "WBIA job queue turnaround time");
                }
                // WBIA turnaround time detection (lightnet) tasks
                String regexTTdetect =
                    "wbia_turnaround_seconds\\{endpoint=\"/api/engine/detect/cnn/lightnet/\".*\\} \\d*\\.\\d*";
                String promValueDetect = getWBIAPrometheusClientValue(wbiaMetrics, regexTTdetect);
                if (promValueDetect != null) {
                    csvLines.add("wildbook_wbia_turnaroundtime_detection" + "," + promValueDetect +
                        "," + "gauge" + "," + "WBIA job queue turnaround time for detection tasks");
                }
                // WBIA turnaround time ID (graph) tasks
                String regexTTgraph =
                    "wbia_turnaround_seconds\\{endpoint=\"/api/engine/query/graph/\".*\\} \\d*\\.\\d*";
                String promValueID = getWBIAPrometheusClientValue(wbiaMetrics, regexTTgraph);
                if (promValueID != null) {
                    csvLines.add("wildbook_wbia_turnaroundtime_id" + "," + promValueID + "," +
                        "gauge" + "," + "WBIA job queue turnaround time for ID tasks");
                }
            }
            // write the file atomically: write to temp file first, then rename
            // This prevents partial reads if /metrics is requested during write
            FileOutputStream fos = new FileOutputStream(tempFile);
            OutputStreamWriter outp = new OutputStreamWriter(fos);
            for (String line : csvLines) {
                if (line != null) {
                    outp.write(line + "\n");
                }
            }
            outp.close();
            fos.close();
            outp = null;

            // Atomic rename: move temp file to final location
            // Delete old file first if it exists
            if (metricsFile.exists()) {
                metricsFile.delete();
            }
            boolean renamed = tempFile.renameTo(metricsFile);
            if (!renamed) {
                System.out.println("WARNING: MetricsBot could not atomically rename temp file to " + csvFile);
                // Fallback: copy content if rename fails (can happen across filesystems)
                java.nio.file.Files.copy(tempFile.toPath(), metricsFile.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                tempFile.delete();
            }

            // Now that CSV is safely written, clear and reload the Prometheus registry
            // This is the only place where we clear the registry, ensuring /metrics
            // always has data (either old or new, never empty)
            synchronized (CollectorRegistry.defaultRegistry) {
                CollectorRegistry.defaultRegistry.clear();
                Prometheus metricsExtractor = new Prometheus();
                metricsExtractor.getValues();
            }
            System.out.println("MetricsBot.refreshMetrics completed successfully");
        } catch (Exception f) {
            // On any exception, we do NOT clear the registry, so old metrics remain available
            System.out.println("Exception in MetricsBot.refreshMetrics - old metrics preserved!");
            f.printStackTrace();
        } finally {
            // Clean up temp file if it still exists (e.g., if exception occurred)
            if (tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    // Helper method to add a line to csvLines only if it's not null
    private static void addLineIfNotNull(ArrayList<String> csvLines, String line) {
        if (line != null) {
            csvLines.add(line);
        }
    }

    // Helper method to get total document count from an OpenSearch index
    private static long getOpenSearchIndexCount(String indexName) {
        try {
            OpenSearch os = new OpenSearch();
            // Query with size=0 to just get count, not documents
            JSONObject query = new JSONObject();
            query.put("query", new JSONObject().put("match_all", new JSONObject()));
            JSONObject result = os.queryPit(indexName, query, 0, 0, null, null);

            // Extract total count from response: hits.total.value
            JSONObject hits = result.optJSONObject("hits");
            if (hits != null) {
                JSONObject total = hits.optJSONObject("total");
                if (total != null) {
                    return total.optLong("value", -1);
                }
                // Some versions return total as a number directly
                return hits.optLong("total", -1);
            }
        } catch (Exception e) {
            System.out.println("MetricsBot.getOpenSearchIndexCount(" + indexName + ") failed: " + e.getMessage());
        }
        return -1;
    }

    // Helper method to get database count for a class
    private static long getDatabaseCount(String className, String context) {
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("MetricsBot_dbCount_" + className);
        myShepherd.beginDBTransaction();
        try {
            Query q = myShepherd.getPM().newQuery("SELECT count(this) FROM " + className);
            Long count = (Long) q.execute();
            q.closeAll();
            return count != null ? count : -1;
        } catch (Exception e) {
            System.out.println("MetricsBot.getDatabaseCount(" + className + ") failed: " + e.getMessage());
            return -1;
        } finally {
            myShepherd.rollbackAndClose();
        }
    }

    /*
     * addTasksToCsv
     *
     * Helper method for adding machine learning tasks related metrics
     * Written by 2022 Captstone team: Gabe Marcial, Joanna Hoang, Sarah Schibel
     */
    private static void addTasksToCsv(ArrayList<String> csvLines, String context)
    throws FileNotFoundException {
        // Total tasks
        // Haven't found practical value for this
        // csvLines.add(buildGauge("SELECT count(this) FROM org.ecocean.ia.Task", "wildbook_tasks_total", "Number of machine learning tasks",
        // context));

        // Detection tasks
        // Haven't found practical value for this
        // csvLines.add(buildGauge("SELECT count(this) FROM org.ecocean.ia.Task where parameters.indexOf('ibeis.detection') > -1  && (children == null
        // || (children.contains(child) && child.parameters.indexOf('ibeis.detection') == -1)) VARIABLES org.ecocean.ia.Task
        // child","wildbook_detection_tasks","Number of detection tasks", context));

        // Identification tasks
        // Haven't found practical value for this
        // csvLines.add(buildGauge("SELECT count(this) FROM org.ecocean.ia.Task where (parameters.indexOf('ibeis.identification') > -1 ||
        // parameters.indexOf('pipeline_root') > -1 || parameters.indexOf('graph') > -1)" , "wildbook_identification_tasks","Number of identification
        // tasks", context));

        // Task loading
        long TwoFourHours = 1000 * 60 * 60 * 24;

        addLineIfNotNull(csvLines, buildGauge(
            (
            "SELECT count(this) FROM org.ecocean.ia.Task where (parameters.indexOf('ibeis.identification') > -1 || parameters.indexOf('pipeline_root') > -1 || parameters.indexOf('graph') > -1) && (children==null || children.size()==0) && created > "
            + (System.currentTimeMillis() - TwoFourHours)),
            "wildbook_identification_tasks_added_last24",
            "Number of child identification tasks added last 24 hours", context));
        addLineIfNotNull(csvLines, buildGauge(
            (
            "SELECT count(this) FROM org.ecocean.ia.Task where parameters.indexOf('ibeis.detection') > -1  && (children == null || (children.contains(child) && child.parameters.indexOf('ibeis.detection') == -1)) && created > "
            + (System.currentTimeMillis() - TwoFourHours)) + " VARIABLES org.ecocean.ia.Task child",
            "wildbook_detection_tasks_added_last24",
            "Number of detection tasks added last 24 hours", context));
        addLineIfNotNull(csvLines, buildGauge(
            (
            "SELECT count(this) FROM org.ecocean.ia.Task where (parameters.indexOf('ibeis.identification') > -1 || parameters.indexOf('pipeline_root') > -1 || parameters.indexOf('graph') > -1) && (children==null || children.size()==0) && parameters.indexOf('fastlane') > -1 && created > "
            + (System.currentTimeMillis() - TwoFourHours)),
            "wildbook_fastlane_identification_tasks_added_last24",
            "Number of fastlane child identification tasks added last 24 hours", context));
        addLineIfNotNull(csvLines, buildGauge(
            (
            "SELECT count(this) FROM org.ecocean.ia.Task where (parameters.indexOf('ibeis.identification') > -1 || parameters.indexOf('pipeline_root') > -1 || parameters.indexOf('graph') > -1) && (children==null || children.size()==0) && parameters.indexOf('fastlane') > -1 && completionDateInMilliseconds > "
            + (System.currentTimeMillis() - TwoFourHours)),
            "wildbook_fastlane_identification_tasks_completed_last24",
            "Number of fastlane child identification tasks completed last 24 hours", context));

        // Hotspotter, PieTwo, MiewID
        addLineIfNotNull(csvLines, buildGauge(
            "SELECT count(this) FROM org.ecocean.ia.Task where children == null && parameters.indexOf('\"sv_on\"')>-1",
            "wildbook_tasks_hotspotter", "Number of tasks using Hotspotter algorithm", context));
        addLineIfNotNull(csvLines, buildGauge(
            "SELECT count(this) FROM org.ecocean.ia.Task where  children == null && parameters.indexOf('PieTwo')>-1",
            "wildbook_tasks_pieTwo", "Number of tasks using PieTwo algorithm", context));
		addLineIfNotNull(csvLines, buildGauge(
            "SELECT count(this) FROM org.ecocean.ia.Task where  children == null && parameters.indexOf('MiewId')>-1",
            "wildbook_tasks_pieTwo", "Number of tasks using MiewId algorithm", context));



        // add queue information
        try {
            System.out.println("Trying to get queue sizes!");
            org.ecocean.queue.Queue iaQueue = QueueUtil.getBest(context, "IA");
            org.ecocean.queue.Queue detectionQueue = QueueUtil.getBest(context, "detection");
            long iaQueueSize = iaQueue.getQueueSize();
            long detectionQueueSize = detectionQueue.getQueueSize();
            // csvLines.add("wildbook_taxonomies_total"+","+taxa.size()+","+"gauge"+","+"Number of species");
            csvLines.add("wildbook_queue_detect, " + detectionQueueSize + "," + "gauge" + "," +
                "Number detection jobs in Wildbook queue now");
            csvLines.add("wildbook_queue_ia, " + iaQueueSize + "," + "gauge" + "," +
                "Number ID jobs in Wildbook queue now");
            csvLines.add("wildbook_queue_total, " + (iaQueueSize + detectionQueueSize) + "," +
                "gauge" + "," + "Number total jobs in Wildbook queue");
        } catch (Exception e) { e.printStackTrace(); }
        // Species tasks
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("MetricsBot_ML_Tasks");
        myShepherd.beginDBTransaction();
        try {
            // Task completion tasks
            int numDetectionCompletedLast24 = 0;
            int numIDCompletedLast24 = 0;
            String detectionsCompleteFilter =
                "SELECT count(this) FROM org.ecocean.ia.Task where completionDateInMilliseconds > "
                + (System.currentTimeMillis() - TwoFourHours) +
                " && parameters.indexOf('ibeis.detection') > -1  && (children == null || children.size() == 0)";
            String idCompleteFilter =
                "SELECT count(this) FROM org.ecocean.ia.Task where completionDateInMilliseconds > "
                + (System.currentTimeMillis() - TwoFourHours);
            Query qD = null;
            Query qID = null;
            try {
                Long detectValue = null;
                Long idValue = null;
                qD = myShepherd.getPM().newQuery(detectionsCompleteFilter);
                detectValue = (Long)qD.execute();
                if (detectValue != null) numDetectionCompletedLast24 = detectValue.intValue();
                qID = myShepherd.getPM().newQuery(idCompleteFilter);
                idValue = (Long)qID.execute();
                if (idValue != null)
                    numIDCompletedLast24 = idValue.intValue() - detectValue.intValue();
                csvLines.add("wildbook_identification_tasks_completed_last24, " +
                    numIDCompletedLast24 + "," + "gauge" + "," +
                    "Number of child identification tasks completed last 24 hours");
                csvLines.add("wildbook_detection_tasks_completed_last24, " +
                    numDetectionCompletedLast24 + "," + "gauge" + "," +
                    "Number of detection tasks completed last 24 hours");
            } catch (java.lang.IllegalArgumentException badArg) {
                System.out.println("MetricsBot.buildGauge called with bad arguments.");
                badArg.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (qD != null) qD.closeAll();
                if (qID != null) qID.closeAll();
            }
            IAJsonProperties iaConfig = new IAJsonProperties();
            List<Taxonomy> taxes = iaConfig.getAllTaxonomies(myShepherd);
            String filter3 =
                "SELECT count(this) FROM org.ecocean.ia.Task where (parameters.indexOf('ibeis.identification') > -1 || parameters.indexOf('pipeline_root') > -1 || parameters.indexOf('graph') > -1) ";
            String scientificName = "";
            // Iterate through our species
            for (Taxonomy tax : taxes) {
                // Logic for extracting IA classes comes from taskQueryTests.jsp
                List<String> iaClasses = iaConfig.getValidIAClassesIgnoreRedirects(tax);
                if (iaClasses != null && iaClasses.size() > 0) {
                    String allowedIAClasses = "&& ( ";
                    for (String str : iaClasses) {
                        if (allowedIAClasses.indexOf("iaClass") == -1) {
                            allowedIAClasses += " annot.iaClass == '" + str + "' ";
                        } else {
                            allowedIAClasses += " || annot.iaClass == '" + str + "' ";
                        }
                    }
                    try {
                        scientificName = tax.getScientificName();
                    } catch (NullPointerException e) {
                        System.out.println("Null Pointer Exception in Species Tasks");
                    }
                    // Replace space w/ underscore for prometheus syntax
                    scientificName = scientificName.replaceAll("\\s+", "_");
                    scientificName = scientificName.replaceAll("\\+", "_");

                    allowedIAClasses += " )";
                    String filter = filter3 + " && objectAnnotations.contains(annot) " +
                        allowedIAClasses + " VARIABLES org.ecocean.Annotation annot";
                    addLineIfNotNull(csvLines, buildGauge(filter, "wildbook_tasks_idSpecies_" + scientificName,
                        "Number of ID tasks by species " + scientificName, context));
                }
            }
            // Tasks by users
            // WB-1968: filter to only users who have logged in
            // List<User> users = myShepherd.getAllUsers();
            String filterTasksUsers = "SELECT FROM org.ecocean.User where lastLogin > 0";
            Query filterTasksUsersQuery = null;
            try {
                filterTasksUsersQuery = myShepherd.getPM().newQuery(filterTasksUsers);
                Collection c = (Collection)filterTasksUsersQuery.execute();
                List<User> users = new ArrayList<User>(c);
            } finally {
                if (filterTasksUsersQuery != null) filterTasksUsersQuery.closeAll();
            }
            // end WB-1968

        } catch (Exception exy) { exy.printStackTrace(); } finally {
            myShepherd.rollbackAndClose();
        }
    }

    // Helper method for normalizing characters
    public static String stripAccents(String input) {
        return input == null ? null : Normalizer.normalize(input, Normalizer.Form.NFD)
                   .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }

    public static String httpGetRemoteText(String url) {
        String responseString = "";

        try {
            CloseableHttpClient httpClient = HttpClientBuilder.create().build();
            URIBuilder uriBuilder = new URIBuilder(url);
            URI uri = uriBuilder.build();
            HttpGet request = new HttpGet(uri);

            // Make the API call and get the response entity.
            HttpResponse response = httpClient.execute(request);
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                responseString = EntityUtils.toString(entity);
            }
        } catch (Exception e) {
            System.out.println("Failed to get a response posting MediaAsset with URL: " + url);
            e.printStackTrace();
        }
        return responseString;
    }

    public static String getWBIAPrometheusClientValue(String parseMe, String regex) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(parseMe);

        if (matcher.find()) {
            String tt = matcher.group(0);
            StringTokenizer str = new StringTokenizer(tt, " ");
            if (str.countTokens() > 1) {
                String definition = str.nextToken();
                String value = str.nextToken();
                return value;
            }
        }
        return null;
    }
}
