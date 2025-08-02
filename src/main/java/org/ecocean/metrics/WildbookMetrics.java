package org.ecocean.metrics;

import io.prometheus.client.exporter.MetricsServlet;
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
}
