package org.ecocean.servlet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import javax.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.Test;

class ReactRouterTest {

    private static HttpServletRequest reqWithContextPath(String ctx) {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getContextPath()).thenReturn(ctx);
        return req;
    }

    @Test void getBasePath_returnsReact() {
        assertEquals("/react", ReactRouter.getBasePath());
    }

    @Test void getBasePath_withRequest_rootContext() {
        HttpServletRequest req = reqWithContextPath("");
        assertEquals("/react", ReactRouter.getBasePath(req));
    }

    @Test void getBasePath_withRequest_nonRootContext() {
        HttpServletRequest req = reqWithContextPath("/wildbook");
        assertEquals("/wildbook/react", ReactRouter.getBasePath(req));
    }

    @Test void getBasePath_withRequest_nullContext() {
        HttpServletRequest req = reqWithContextPath(null);
        assertEquals("null/react", ReactRouter.getBasePath(req));
    }

    @Test void path_nullRoute_returnsTrailingSlash() {
        assertEquals("/react/", ReactRouter.path(null));
    }

    @Test void path_emptyRoute_returnsTrailingSlash() {
        assertEquals("/react/", ReactRouter.path(""));
    }

    @Test void path_rootOnly_returnsTrailingSlash() {
        assertEquals("/react/", ReactRouter.path("/"));
    }

    @Test void path_leadingSlashRoute() {
        assertEquals("/react/login", ReactRouter.path("/login"));
    }

    @Test void path_noLeadingSlashRoute() {
        assertEquals("/react/login", ReactRouter.path("login"));
    }

    @Test void path_nestedRoute_withLeadingSlash() {
        assertEquals("/react/encounter-search?state=approved",
            ReactRouter.path("/encounter-search?state=approved"));
    }

    @Test void path_withRequest_rootContext() {
        HttpServletRequest req = reqWithContextPath("");
        assertEquals("/react/login", ReactRouter.path(req, "/login"));
    }

    @Test void path_withRequest_nonRootContext() {
        HttpServletRequest req = reqWithContextPath("/wildbook");
        assertEquals("/wildbook/react/login", ReactRouter.path(req, "/login"));
    }

    @Test void path_withRequest_nullContext() {
        HttpServletRequest req = reqWithContextPath(null);
        assertEquals("null/react/login", ReactRouter.path(req, "/login"));
    }
}
