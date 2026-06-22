package org.ecocean.metrics;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;

import org.ecocean.MetricsBot;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.common.TextFormat;
import io.prometheus.client.Gauge;

public class Prometheus {
    private String context = "context0";

    static String cvsSplitBy = ",";

    // Default constructor
    public Prometheus() {}

    public Prometheus(String context) {
        this.context = context;
    }

    // Unit test constructor
    public Prometheus(boolean isTesting) {}

    /** Implementation borrowed from MetricsServlet class Parses the default collector registery into the kind of
     * output that prometheus likes Visit
     * https://github.com/prometheus/client_java/blob/master/simpleclient_servlet/src/main/java/io/prometheus/client/exporter/MetricsServlet.java
     */
    public void metrics(HttpServletRequest request, HttpServletResponse response)
    throws IOException {
        Writer writer = new BufferedWriter(response.getWriter());

        response.setStatus(HttpServletResponse.SC_OK);
        String contentType = TextFormat.chooseContentType(request.getHeader("Accept"));
        response.setContentType(contentType);
        try {
            TextFormat.writeFormat(contentType, writer,
                CollectorRegistry.defaultRegistry.filteredMetricFamilySamples(parse(request)));
            writer.flush();
        } finally {
            writer.close();
        }
    }

    // Helper method for metrics() also borrowed from MetricsServlet.java
    private Set<String> parse(HttpServletRequest req) {
        String[] includedParam = req.getParameterValues("name[]");

        if (includedParam == null) {
            return Collections.emptySet();
        } else {
            return new HashSet<String>(Arrays.asList(includedParam));
        }
    }

    public static String getValue(String key) {
        if (key == null) return null;
        String value = null;
        BufferedReader br = null;
        String line = "";

        try {
            br = new BufferedReader(new FileReader(MetricsBot.csvFile));
            while ((line = br.readLine()) != null) {
                // use comma as separator
                String[] vals = line.split(cvsSplitBy);
                if (vals[0] != null && vals[1] != null) {
                    String m_key = vals[0];
                    String m_value = vals[1];
                    if (m_key.trim().equals(key)) { value = m_value; }
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            System.out.println("Metrics failed to load value " + key +
                " because I could not find file: " + MetricsBot.csvFile);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return value;
    }

    public void getValues() {
        ArrayList<String> metrics = new ArrayList<String>();
        BufferedReader br = null;
        String line = "";

        try {
            br = new BufferedReader(new FileReader(MetricsBot.csvFile));
            while ((line = br.readLine()) != null) {
                metrics.add(line);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            System.out.println("Metrics failed to load because I could not find file: " +
                MetricsBot.csvFile);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        for (String metric : metrics) {
            String[] vals = metric.split(",(?=([^\"]*\"[^\"]*\")*[^\"]*$)");
            if (vals.length > 3) {
                String m_key = null;
                try {
                    m_key = vals[0].trim();
                    String m_value = vals[1].trim();
                    String m_type = vals[2].trim();
                    String m_help = vals[3].trim();
                    String m_labels = null;
                    if (vals.length > 4) m_labels = vals[4].trim();
                    if (m_type.equals("gauge")) {
                        // System.out.println("Loading gauge: "+m_key);
                        if (m_labels != null) {
                            // preprocess labels
                            m_labels = m_labels.replaceAll("\"", "");
                            ArrayList<String> labelNames = new ArrayList<String>();
                            ArrayList<String> labelValues = new ArrayList<String>();
                            StringTokenizer str = new StringTokenizer(m_labels, ",");
                            while (str.hasMoreTokens()) {
                                String token = str.nextToken();
                                StringTokenizer str2 = new StringTokenizer(token, ":");
                                if (str2.countTokens() < 2) continue; // skip malformed labels
                                String name = str2.nextToken();
                                String value = str2.nextToken();
                                labelNames.add(name);
                                labelValues.add(value);
                            }
                            if (labelNames.isEmpty()) continue; // skip if no valid labels

                            // add label names and build gauge
                            StringTokenizer str4 = new StringTokenizer(labelNames.get(0), "_");
                            String prefix4 = str4.nextToken();

                            // Use createOrGet pattern to avoid IllegalArgumentException on re-registration
                            Gauge g = getOrCreateGauge(m_key, prefix4, m_help);
                            if (g == null) continue; // skip if gauge creation failed

                            // add label values
                            for (int i = 0; i < labelNames.size(); i++) {
                                String name = labelNames.get(i);
                                String[] str3 = name.split("_", 2);
                                if (str3.length < 2) continue; // skip malformed label names
                                String suffix = str3[1];
                                String value = labelValues.get(i);
                                if (name != null && value != null) {
                                    try {
                                        g.labels(suffix).inc(Double.parseDouble(value));
                                    } catch (NumberFormatException nfe) {
                                        System.out.println("Prometheus: Invalid number value for " + m_key + ": " + value);
                                    }
                                }
                            }
                        }
                        // no labels, easy case!
                        else {
                            Gauge g = getOrCreateGauge(m_key, null, m_help);
                            if (g != null) {
                                try {
                                    g.inc(Double.parseDouble(m_value));
                                } catch (NumberFormatException nfe) {
                                    System.out.println("Prometheus: Invalid number value for " + m_key + ": " + m_value);
                                }
                            }
                        }
                    }
                } catch (IllegalArgumentException iae) {
                    // This can happen if gauge already exists with different configuration
                    System.out.println("Prometheus: Could not register gauge " + m_key + ": " + iae.getMessage());
                } catch (Exception e) {
                    System.out.println("Prometheus: Error processing metric " + m_key);
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Get an existing Gauge or create a new one if it doesn't exist.
     * This prevents IllegalArgumentException when trying to register a gauge that already exists.
     */
    private Gauge getOrCreateGauge(String name, String labelName, String help) {
        try {
            if (labelName != null) {
                return Gauge.build().name(name).labelNames(labelName).help(help).register();
            } else {
                return Gauge.build().name(name).help(help).register();
            }
        } catch (IllegalArgumentException iae) {
            // Gauge already registered - this shouldn't happen now that we clear the registry first,
            // but handle it gracefully just in case
            System.out.println("Prometheus: Gauge " + name + " already registered, skipping");
            return null;
        }
    }
}
