package org.ecocean.resumableupload;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * WebImport is mapped twice: /import/upload, which "/import/** = authc, roles[researcher]" covers,
 * and /WebImport, which nothing covered. There is no catch-all in the Shiro [urls] block and the
 * servlet performs no authorization of its own, so that alias was an unauthenticated route into an
 * importer that writes Encounters when called with commit=true.
 */
class WebImportAuthWiringTest {
    private static List<String> webXml()
    throws IOException {
        return Files.readAllLines(new File("src/main/webapp/WEB-INF/web.xml").toPath(),
            StandardCharsets.UTF_8);
    }

    private static Optional<String> ruleFor(List<String> lines, String path)
    throws IOException {
        return lines.stream().map(String::trim)
                   .filter(l -> !l.startsWith("#") && !l.startsWith("<"))
                   .filter(l -> l.startsWith(path + " ") || l.startsWith(path + "="))
                   .findFirst();
    }

    @Test void webImportAliasIsAuthenticated()
    throws IOException {
        Optional<String> rule = ruleFor(webXml(), "/WebImport");

        assertTrue(rule.isPresent(), "/WebImport needs its own Shiro rule: there is no catch-all "
            + "in [urls], so an unlisted path reaches the servlet unauthenticated");
        String value = rule.get().substring(rule.get().indexOf('=') + 1).trim();
        assertFalse("anon".equals(value), "/WebImport must not be anon");
        assertTrue(value.contains("authc"), "/WebImport must require authentication; was: " + value);
    }

    @Test void webImportAliasCarriesTheSameRoleAsItsOtherMapping()
    throws IOException {
        List<String> lines = webXml();
        String alias = ruleFor(lines, "/WebImport").orElseThrow();

        assertTrue(alias.contains("roles[researcher]"),
            "the same servlet is reachable at /import/upload under roles[researcher]; the alias "
            + "must not be weaker");
    }
}
