package org.ecocean.servlet;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

/**
 * Serves the React single-page-app shell ({@code /react/index.html})
 * for both direct hits on {@code /react} and as the SPA fallback for
 * the {@code <error-page>404</error-page>} directive in web.xml.
 *
 * <p>Routing decision tree for error-page dispatches (the
 * {@code javax.servlet.error.request_uri} attribute is non-null in
 * this case):</p>
 * <ul>
 *   <li>Non-GET request method (POST, PUT, DELETE, etc.): preserve
 *       404. API clients hitting a non-existent {@code POST /api/...}
 *       must see 404, not the React shell and not 405.</li>
 *   <li>Path outside {@code /react}: preserve 404. API and static-
 *       outside-react 404s pass through with their original status.</li>
 *   <li>Path under {@code /react} but ending in a known static-asset
 *       extension (.js, .css, .png, .json, etc.): preserve 404. We
 *       must NOT serve HTML as a JS/CSS response — the service
 *       worker's NetworkFirst cache could store HTML keyed at the
 *       JS URL, breaking subsequent loads.</li>
 *   <li>Otherwise (navigation-like GET to {@code /react/<route>}):
 *       rewrite status to 200 and serve the React shell so PWA
 *       service workers using NetworkFirst caching accept the body.
 *       Without the rewrite the SW rejects 404s as "no-response" and
 *       deep-linked React routes render blank.</li>
 * </ul>
 *
 * <p>Direct hits on {@code /react} (no error dispatch):
 * {@code errorUri} is null; only GETs are honored, anything else
 * gets 405 (the standard HttpServlet behavior we'd inherit anyway).</p>
 */
public class ReactAppServlet extends HttpServlet {
    /** Standard error-dispatch attribute name (servlet 3.x+). */
    private static final String ERROR_REQUEST_URI = "javax.servlet.error.request_uri";

    /**
     * Extensions that identify a request as a static asset rather
     * than a navigation route. Missing assets must preserve 404 so
     * the service worker doesn't cache the HTML shell at a JS/CSS
     * URL. List covers common React-build outputs plus typical
     * media types.
     */
    private static final Set<String> ASSET_EXTENSIONS;
    static {
        Set<String> s = new HashSet<String>(Arrays.asList(
            "js", "mjs", "css", "map",
            "json", "txt", "xml", "wasm",
            "html", "htm",
            "ico", "png", "jpg", "jpeg", "gif", "svg", "webp", "bmp",
            "woff", "woff2", "ttf", "eot", "otf",
            "mp3", "mp4", "webm", "ogg",
            "pdf", "zip"));
        ASSET_EXTENSIONS = Collections.unmodifiableSet(s);
    }

    public void init(ServletConfig config)
    throws ServletException {
        super.init(config);
    }

    /**
     * Override {@code service} (not just {@code doGet}) so non-GET
     * methods reaching this servlet via the error-page dispatch are
     * handled uniformly. The default {@code HttpServlet.doPost} /
     * {@code doPut} / etc. return 405 Method Not Allowed; for SPA
     * error-page dispatches we want 404 instead — API clients hitting
     * a non-existent {@code POST /api/...} should see 404, not 405.
     */
    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response)
    throws IOException, ServletException {
        String errorUri = (String) request.getAttribute(ERROR_REQUEST_URI);
        if (errorUri == null) {
            // Direct hit on /react: only GET is meaningful (serves the
            // React shell). Other methods get the standard 405.
            if ("GET".equalsIgnoreCase(request.getMethod())) {
                forwardToShell(request, response);
            } else {
                super.service(request, response);
            }
            return;
        }
        // Error-page dispatch. Decide based on path and method.
        String pathInWebapp = stripContextPath(request, errorUri);
        boolean isSpaNavigation =
            "GET".equalsIgnoreCase(request.getMethod())
                && (pathInWebapp.equals("/react") ||
                    pathInWebapp.startsWith("/react/"))
                && !looksLikeStaticAsset(pathInWebapp);
        if (isSpaNavigation) {
            // Rewrite to 200 so service workers accept the React shell.
            response.setStatus(HttpServletResponse.SC_OK);
            forwardToShell(request, response);
        } else {
            // Preserve 404 for everything else: non-SPA paths, missing
            // static assets, and non-GET methods. Write a minimal body
            // rather than calling sendError() so we don't recurse
            // through the same error-page handler.
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.setContentType("text/plain;charset=UTF-8");
            response.getWriter().write("404 Not Found");
        }
    }

    /**
     * Strip the webapp's context path from {@code uri}. Required when
     * the webapp is deployed at a non-root context (e.g.
     * {@code /wildbook}) so the prefix check matches the in-webapp
     * path, not the public URL.
     */
    static String stripContextPath(HttpServletRequest request, String uri) {
        String ctx = request.getContextPath();
        return (ctx != null && !ctx.isEmpty() && uri.startsWith(ctx))
            ? uri.substring(ctx.length())
            : uri;
    }

    /**
     * Heuristic: a path identifies a static asset if its last
     * URL segment ends in a known asset file extension. Paths
     * without an extension in the last segment ({@code /react/match-results},
     * {@code /react/encounter/abc-123}) are navigation routes
     * and get the SPA shell; paths with an asset extension
     * ({@code /react/static/js/main.js}, {@code /react/manifest.json})
     * remain 404 so the service worker doesn't cache the HTML
     * shell at the asset's URL.
     */
    static boolean looksLikeStaticAsset(String pathInWebapp) {
        if (pathInWebapp == null) return false;
        int lastSlash = pathInWebapp.lastIndexOf('/');
        String lastSegment = (lastSlash >= 0)
            ? pathInWebapp.substring(lastSlash + 1)
            : pathInWebapp;
        int lastDot = lastSegment.lastIndexOf('.');
        if (lastDot < 0 || lastDot == lastSegment.length() - 1) return false;
        String ext = lastSegment.substring(lastDot + 1).toLowerCase();
        return ASSET_EXTENSIONS.contains(ext);
    }

    private void forwardToShell(HttpServletRequest request,
        HttpServletResponse response)
    throws IOException, ServletException {
        try {
            request.getRequestDispatcher("/react/index.html").forward(request, response);
        } catch (ServletException e) {
            e.printStackTrace();
            throw e;
        }
    }
}
