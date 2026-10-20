package org.ecocean.security;

import java.nio.file.Path;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.shiro.config.Ini;
import org.apache.shiro.util.AntPathMatcher;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import static org.junit.jupiter.api.Assertions.*;

class AuthorizationRoutesTest {
    private Ini.Section routes() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        NodeList filters = factory.newDocumentBuilder()
            .parse(Path.of("src/main/webapp/WEB-INF/web.xml").toFile()).getElementsByTagName("filter");
        for (int i = 0; i < filters.getLength(); i++) {
            Element filter = (Element)filters.item(i);
            if ("ShiroFilter".equals(filter.getElementsByTagName("filter-name").item(0).getTextContent())) {
                Ini ini = new Ini();
                ini.load(filter.getElementsByTagName("param-value").item(0).getTextContent());
                return ini.getSection("urls");
            }
        }
        throw new AssertionError("Shiro configuration missing");
    }

    private String firstRule(Ini.Section routes, String path) {
        AntPathMatcher matcher = new AntPathMatcher();
        for (Map.Entry<String, String> rule : routes.entrySet()) {
            if (matcher.matches(rule.getKey(), path)) return rule.getValue();
        }
        return null;
    }

    @Test void sensitiveWriteRoutesRequireAuthenticationBeforeHandlerChecks() throws Exception {
        Ini.Section routes = routes();
        for (String path : new String[] {"/EncounterSetBehavior", "/OccurrenceAddComment", "/EncounterAddComment", "/MediaAssetModify"}) {
            assertEquals("authc, roles[researcher]", firstRule(routes, path), path);
        }
        assertEquals("authc", firstRule(routes, "/ProjectDelete"));
        assertEquals("authc, roles[admin]", firstRule(routes, "/UserDelete"));
    }
}
