package org.ecocean.metrics;

import io.prometheus.client.Collector.MetricFamilySamples;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Predicate;
import io.prometheus.client.exporter.MetricsServlet;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

/**
 *
 * This servlet allows Wildbook metrics to be accessible via endpoint.
 * @author jholmber, Gabe Marcial, Joanna Hoang, Sarah Schibel
 *
 */
public class WildbookMetrics extends MetricsServlet {
    /*Initialize variables*/
    // Shepherd myShepherd;
    // boolean pageVisited = false;
    Prometheus metricsExtractor;

    public WildbookMetrics() {
        // the stock servlet serves (GET, POST, HEAD, content negotiation, name[] and init-param
        // filters) whatever registry it is given; give it one that also carries queue health
        super(new ScrapeRegistry());
    }

    public void init(ServletConfig config)
    throws ServletException {
        super.init(config);
        this.metricsExtractor = new Prometheus();
        // this.myShepherd = new Shepherd("context0");
        // this.myShepherd.setAction("WildBookMetrics.class");
        // this.myShepherd.beginDBTransaction();
        try {
            // This if may not even be necessary as I believe init should only be called once.
            // if(!this.pageVisited)
            // {
            this.metricsExtractor.getValues();
            // this.pageVisited = true;
            // }
        } catch (Exception lEx) {
            lEx.printStackTrace();
        } finally {
            // this.myShepherd.rollbackDBTransaction();
            // this.myShepherd.closeDBTransaction();
        }
    }

    /**
     * The default registry plus the live queue-consumer health gauges, which
     * Prometheus.queueHealthRegistry() builds fresh on every scrape (they cannot live in the default
     * registry, which MetricsBot clears and reloads hourly).
     */
    static class ScrapeRegistry extends CollectorRegistry {
        @Override public Enumeration<MetricFamilySamples> metricFamilySamples() {
            return merged(CollectorRegistry::metricFamilySamples);
        }

        @Override public Enumeration<MetricFamilySamples> filteredMetricFamilySamples(
            Set<String> includedNames) {
            return merged(reg -> reg.filteredMetricFamilySamples(includedNames));
        }

        @Override public Enumeration<MetricFamilySamples> filteredMetricFamilySamples(
            Predicate<String> sampleNameFilter) {
            return merged(reg -> reg.filteredMetricFamilySamples(sampleNameFilter));
        }

        private static Enumeration<MetricFamilySamples> merged(
            Function<CollectorRegistry, Enumeration<MetricFamilySamples> > samples) {
            List<MetricFamilySamples> out = Collections.list(samples.apply(
                CollectorRegistry.defaultRegistry));
            try {
                out.addAll(Collections.list(samples.apply(Prometheus.queueHealthRegistry())));
            } catch (Exception ex) {
                System.out.println("WildbookMetrics: could not collect queue health: " + ex);
            }
            return Collections.enumeration(out);
        }
    }
}
