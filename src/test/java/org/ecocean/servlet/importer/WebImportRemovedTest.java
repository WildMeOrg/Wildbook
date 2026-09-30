package org.ecocean.servlet.importer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * WebImport was a legacy spreadsheet importer that nothing in the UI linked to. It was reachable at
 * /WebImport with no authentication at all (no Shiro rule covered that alias), joined request and
 * spreadsheet paths onto the upload directory without containment, copied the referenced files into
 * the asset store even on a dry run, created keywords and taxonomies on a dry run, kept every
 * request's state in shared servlet fields, never closed its Shepherd, and echoed request and cell
 * values into HTML unescaped. It was removed rather than repaired; these tests keep it from coming
 * back by accident.
 */
class WebImportRemovedTest {
    private static List<String> webXml()
    throws IOException {
        return Files.readAllLines(new File("src/main/webapp/WEB-INF/web.xml").toPath(),
            StandardCharsets.UTF_8);
    }

    @Test void theServletClassIsGone() {
        assertThrows(ClassNotFoundException.class,
            () -> Class.forName("org.ecocean.servlet.importer.WebImport"));
    }

    @Test void neitherUrlIsMappedAnyMore()
    throws IOException {
        String xml = String.join("\n", webXml());

        assertFalse(xml.contains("org.ecocean.servlet.importer.WebImport"),
            "web.xml must not register the removed servlet");
        assertFalse(xml.contains("<url-pattern>/WebImport</url-pattern>"),
            "/WebImport must not be mapped");
        assertFalse(xml.contains("<url-pattern>/import/upload</url-pattern>"),
            "/import/upload must not be mapped");
    }

    @Test void theImportDirectoryStaysGated()
    throws IOException {
        // uploadHeader.jsp / uploadFooter.jsp and the other /import pages are still used by
        // StandardImport, so removing WebImport must not loosen the rule that covers them
        assertTrue(webXml().stream().map(String::trim)
            .anyMatch(l -> l.equals("/import/** = authc, roles[researcher]")),
            "/import/** must remain authc + researcher");
    }
}
