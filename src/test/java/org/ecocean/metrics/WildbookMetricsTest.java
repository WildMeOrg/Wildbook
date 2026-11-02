package org.ecocean.metrics;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Gauge;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** The /metrics servlet must serve the scrape-time queue health gauges, not just the default registry. */
public class WildbookMetricsTest {
    private Gauge defaultGauge;

    @BeforeEach void registerDefaultGauge() {
        defaultGauge = Gauge.build().name("wildbook_test_default_gauge").help("test")
            .register(CollectorRegistry.defaultRegistry);
    }

    @AfterEach void unregisterDefaultGauge() {
        CollectorRegistry.defaultRegistry.unregister(defaultGauge);
    }

    private static String scrape(WildbookMetrics servlet, String method, String[] names)
    throws Exception {
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter body = new StringWriter();
        when(req.getMethod()).thenReturn(method);
        when(req.getParameterValues("name[]")).thenReturn(names);
        when(resp.getWriter()).thenReturn(new PrintWriter(body));

        servlet.service(req, resp);

        verify(resp).setStatus(HttpServletResponse.SC_OK);
        InOrder order = inOrder(resp);
        order.verify(resp).setContentType(anyString());
        order.verify(resp).getWriter();
        return body.toString();
    }

    @Test void getIncludesQueueHealthAndDefaultRegistry() throws Exception {
        String out = scrape(new WildbookMetrics(), "GET", null);

        assertTrue(out.contains("wildbook_queue_consumers_tracked"),
            "queue health gauges missing from /metrics output:\n" + out);
        assertTrue(out.contains("wildbook_test_default_gauge"),
            "default registry missing from /metrics output:\n" + out);
    }

    @Test void postIncludesQueueHealth() throws Exception {
        String out = scrape(new WildbookMetrics(), "POST", null);

        assertTrue(out.contains("wildbook_queue_consumers_tracked"),
            "queue health gauges missing from POST /metrics output:\n" + out);
    }

    @Test void nameFilterAppliesToBothRegistries() throws Exception {
        String out = scrape(new WildbookMetrics(), "GET",
            new String[] { "wildbook_queue_consumers_tracked" });

        assertTrue(out.contains("wildbook_queue_consumers_tracked"));
        assertFalse(out.contains("wildbook_queue_consumers_alive"),
            "name[] filter should exclude other queue families:\n" + out);
        assertFalse(out.contains("wildbook_test_default_gauge"),
            "name[] filter should exclude default-registry families:\n" + out);
    }

    @Test void initParamFilterAppliesToQueueHealth() throws Exception {
        ServletConfig config = mock(ServletConfig.class);
        when(config.getServletContext()).thenReturn(mock(ServletContext.class));
        when(config.getInitParameterNames()).thenReturn(Collections.enumeration(
            Collections.singletonList("name-must-not-start-with")));
        when(config.getInitParameter("name-must-not-start-with")).thenReturn("wildbook_queue_");
        WildbookMetrics servlet = new WildbookMetrics();
        servlet.init(config);

        String out = scrape(servlet, "GET", null);

        assertFalse(out.contains("wildbook_queue_"),
            "init-param name filter should exclude queue health:\n" + out);
        assertTrue(out.contains("wildbook_test_default_gauge"));
    }
}
