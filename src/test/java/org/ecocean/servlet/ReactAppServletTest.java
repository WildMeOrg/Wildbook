package org.ecocean.servlet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import javax.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.Test;

/**
 * Pure-logic tests for {@link ReactAppServlet}'s path-classification
 * helpers. The full request dispatch path is exercised by manual
 * curl on the dev deployment. (C14: SPA fallback HTTP-status fix.)
 */
class ReactAppServletTest {

    private static HttpServletRequest reqWithContextPath(String ctx) {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getContextPath()).thenReturn(ctx);
        return req;
    }

    // --- stripContextPath ------------------------------------------------

    @Test void stripContextPath_rootContext_passesThrough() {
        HttpServletRequest req = reqWithContextPath("");
        assertEquals("/react/match-results",
            ReactAppServlet.stripContextPath(req, "/react/match-results"));
    }

    @Test void stripContextPath_nonRootContext_stripsPrefix() {
        HttpServletRequest req = reqWithContextPath("/wildbook");
        assertEquals("/react/match-results",
            ReactAppServlet.stripContextPath(req,
                "/wildbook/react/match-results"));
    }

    @Test void stripContextPath_uriWithoutContext_unchanged() {
        // Defense in depth: if for some reason the URI doesn't start
        // with the context, leave it alone rather than stripping the
        // wrong prefix.
        HttpServletRequest req = reqWithContextPath("/wildbook");
        assertEquals("/somewhere/else",
            ReactAppServlet.stripContextPath(req, "/somewhere/else"));
    }

    @Test void stripContextPath_nullContext_unchanged() {
        HttpServletRequest req = reqWithContextPath(null);
        assertEquals("/react/x",
            ReactAppServlet.stripContextPath(req, "/react/x"));
    }

    // --- looksLikeStaticAsset --------------------------------------------

    @Test void looksLikeStaticAsset_falseForCleanReactRoute() {
        assertFalse(ReactAppServlet.looksLikeStaticAsset("/react/match-results"));
    }

    @Test void looksLikeStaticAsset_falseForNestedRoute() {
        assertFalse(ReactAppServlet.looksLikeStaticAsset("/react/encounter/abc-123"));
    }

    @Test void looksLikeStaticAsset_falseForRootReact() {
        assertFalse(ReactAppServlet.looksLikeStaticAsset("/react"));
    }

    @Test void looksLikeStaticAsset_falseForTrailingSlash() {
        assertFalse(ReactAppServlet.looksLikeStaticAsset("/react/"));
    }

    @Test void looksLikeStaticAsset_trueForJsFile() {
        assertTrue(ReactAppServlet.looksLikeStaticAsset(
            "/react/static/js/main.4bf06ea2.js"));
    }

    @Test void looksLikeStaticAsset_trueForCssFile() {
        assertTrue(ReactAppServlet.looksLikeStaticAsset(
            "/react/static/css/main.css"));
    }

    @Test void looksLikeStaticAsset_trueForSourceMap() {
        assertTrue(ReactAppServlet.looksLikeStaticAsset(
            "/react/static/js/main.4bf06ea2.js.map"));
    }

    @Test void looksLikeStaticAsset_trueForManifestJson() {
        assertTrue(ReactAppServlet.looksLikeStaticAsset("/react/manifest.json"));
    }

    @Test void looksLikeStaticAsset_trueForServiceWorker() {
        // service-worker.js — the file that started this whole story.
        assertTrue(ReactAppServlet.looksLikeStaticAsset("/react/service-worker.js"));
    }

    @Test void looksLikeStaticAsset_trueForImages() {
        assertTrue(ReactAppServlet.looksLikeStaticAsset("/react/favicon.ico"));
        assertTrue(ReactAppServlet.looksLikeStaticAsset("/react/static/img/logo.png"));
        assertTrue(ReactAppServlet.looksLikeStaticAsset("/react/static/img/photo.JPG"));
    }

    @Test void looksLikeStaticAsset_trueForFonts() {
        assertTrue(ReactAppServlet.looksLikeStaticAsset(
            "/react/static/fonts/roboto.woff2"));
        assertTrue(ReactAppServlet.looksLikeStaticAsset(
            "/react/static/fonts/roboto.ttf"));
    }

    @Test void looksLikeStaticAsset_caseInsensitive() {
        // Mixed case extensions still classified correctly.
        assertTrue(ReactAppServlet.looksLikeStaticAsset("/react/photo.PNG"));
        assertTrue(ReactAppServlet.looksLikeStaticAsset("/react/bundle.JS"));
    }

    @Test void looksLikeStaticAsset_falseForUnknownExtension() {
        // Unknown extension — not in the asset list. React route with
        // a "." in the last segment (rare but possible) falls into
        // this bucket; we treat it as navigation.
        assertFalse(ReactAppServlet.looksLikeStaticAsset(
            "/react/encounter/some.weird.id"));
    }

    @Test void looksLikeStaticAsset_falseForTrailingDot() {
        // Pathological: segment ends with "." but no extension after.
        assertFalse(ReactAppServlet.looksLikeStaticAsset("/react/foo."));
    }

    @Test void looksLikeStaticAsset_falseForNull() {
        assertFalse(ReactAppServlet.looksLikeStaticAsset(null));
    }
}
