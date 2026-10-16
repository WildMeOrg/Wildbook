package org.ecocean.servlet;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.Test;

/**
 * The parts of IndividualSetTaxonomy that live outside Java, and that nothing else here would
 * catch. A missing servlet mapping is a 404; a missing Shiro rule is worse than a 404, because the
 * [urls] block has no catch-all and an unmatched path is left unfiltered; and a bundle key added
 * only to en makes the page print the literal string "null" to every other locale, permanently.
 *
 * Static file reads only -- no DB, no container. Paths are relative to the module root, which is
 * the working directory Maven runs tests from.
 */
class IndividualSetTaxonomyWiringTest {
    private static final String PATH = "/IndividualSetTaxonomy";
    private static final String[] LOCALES = { "en", "de", "es", "fr", "it" };
    private static final String[] NEW_KEYS = {
        "taxonomyFromEncounters", "taxonomyUseFromEncounters"
    };

    private List<String> webXmlLines()
    throws Exception {
        File webXml = new File("src/main/webapp/WEB-INF/web.xml");

        assertTrue(webXml.exists(), "web.xml not found at " + webXml.getAbsolutePath());
        return Files.readAllLines(webXml.toPath());
    }

    private String webXml()
    throws Exception {
        return String.join("\n", webXmlLines());
    }

    @Test void servletClassIsRegistered()
    throws Exception {
        assertTrue(webXml().contains(
            "<servlet-class>org.ecocean.servlet.IndividualSetTaxonomy</servlet-class>"),
            "web.xml must declare the IndividualSetTaxonomy servlet class");
    }

    @Test void urlPatternIsMapped()
    throws Exception {
        assertTrue(webXml().contains("<url-pattern>" + PATH + "</url-pattern>"),
            "web.xml must map " + PATH);
    }

    // Without this rule the endpoint is not merely ungated but entirely unfiltered: the [urls]
    // block ends at /interconnect/mac/Interconnect.jar with no /** catch-all behind it.
    @Test void shiroRuleRequiresAnAuthenticatedResearcher()
    throws Exception {
        String rule = webXmlLines().stream()
                .filter(l -> {
            String t = l.stripLeading();
            return !t.startsWith("#") && t.startsWith(PATH + " ");
        })
                .findFirst().orElse(null);

        assertNotNull(rule, "Shiro [urls] must contain a rule line for " + PATH);
        assertTrue(rule.contains("authc"),
            "the " + PATH + " rule must require authentication (was: '" + rule.strip() + "')");
        assertTrue(rule.contains("roles[researcher]"),
            "the " + PATH + " rule must require the researcher role (was: '" + rule.strip() + "')");
    }

    // ShepherdProperties falls back to en only when a whole bundle file is missing, never for an
    // individual key, so a key present only in en renders as the string "null" everywhere else.
    @Test void everyLocaleDefinesTheNewLabels()
    throws Exception {
        for (String locale : LOCALES) {
            File bundle = new File("src/main/resources/bundles/" + locale +
                "/individuals.properties");
            assertTrue(bundle.exists(), "missing bundle: " + bundle.getPath());
            Properties props = new Properties();
            // .properties files here are ISO-8859-1 with \\uXXXX escapes, as Properties expects
            try (var in = Files.newInputStream(bundle.toPath())) {
                props.load(new java.io.InputStreamReader(in, StandardCharsets.ISO_8859_1));
            }
            for (String key : NEW_KEYS) {
                String value = props.getProperty(key);
                assertNotNull(value, locale + "/individuals.properties is missing '" + key + "'");
                assertFalse(value.trim().isEmpty(),
                    locale + "/individuals.properties has an empty '" + key + "'");
            }
        }
    }
}
