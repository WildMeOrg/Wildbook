package org.ecocean.servlet;

import java.nio.file.Path;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import static org.junit.jupiter.api.Assertions.*;

/**
 * IAGateway's shutdown safety net lives in its servlet {@code destroy()}: it stops the static
 * requeue executor and flushes payloads whose delayed publish was cancelled to the persistent
 * spool. Tomcat only calls {@code destroy()} on a servlet it has instantiated, and it instantiates
 * a servlet lazily on its first request unless the descriptor asks for eager init. The ml-service
 * processors requeue through the STATIC {@code requeueJobResult} API with no /ia traffic at all,
 * so without eager init a redeploy on a site where nothing has hit /ia since boot would cancel the
 * pending retries and silently lose jobs already reported as queued.
 *
 * Static file read only -- the path is relative to the module root, Maven's test working dir.
 */
class IAGatewayDeploymentDescriptorTest {
    private static Element servletNamed(String name)
    throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        NodeList servlets = factory.newDocumentBuilder()
            .parse(Path.of("src/main/webapp/WEB-INF/web.xml").toFile())
            .getElementsByTagName("servlet");
        for (int i = 0; i < servlets.getLength(); i++) {
            Element servlet = (Element)servlets.item(i);
            String declared = servlet.getElementsByTagName("servlet-name").item(0)
                .getTextContent().trim();
            if (name.equals(declared)) return servlet;
        }
        throw new AssertionError("web.xml declares no servlet named " + name);
    }

    @Test void iaGatewayIsLoadedOnStartupSoDestroyAlwaysRuns()
    throws Exception {
        NodeList loadOnStartup = servletNamed("IAGateway").getElementsByTagName("load-on-startup");
        assertEquals(1, loadOnStartup.getLength(),
            "IAGateway must declare <load-on-startup>: destroy() is the only hook that flushes " +
            "pending requeues, and Tomcat skips destroy() on a servlet it never instantiated");
        assertTrue(Integer.parseInt(loadOnStartup.item(0).getTextContent().trim()) >= 0,
            "a negative load-on-startup value still means lazy init");
    }
}
