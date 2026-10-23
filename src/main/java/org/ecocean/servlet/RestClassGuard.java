package org.ecocean.servlet;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Service key records are not served by the generic REST endpoints (RestServlet, LightRestServlet).
 * A request that names them is refused before any query runs, and result objects of those classes
 * are dropped as a backstop.
 */
final class RestClassGuard {
    static final String MESSAGE = "service key records are not served by the generic REST endpoints";

    private static final String RESTRICTED_PACKAGE = "org.ecocean.auth.";
    private static final String[] RESTRICTED_NAMES = { RESTRICTED_PACKAGE, "servicekey" };
    private static final int MAX_DECODE_ROUNDS = 4;

    private RestClassGuard() {}

    /** True if the path, the query string, or any parameter names a restricted class (case-insensitive). */
    static boolean namesRestricted(HttpServletRequest req) {
        if (mentions(req.getRequestURI()) || mentions(req.getPathInfo()) || mentions(req.getQueryString()))
            return true;
        Enumeration<String> names = req.getParameterNames();
        while (names != null && names.hasMoreElements()) {
            String name = names.nextElement();
            if (mentions(name)) return true;
            String[] values = req.getParameterValues(name);
            if (values == null) continue;
            for (String value : values) if (mentions(value)) return true;
        }
        return false;
    }

    /** Writes the 403 refusal; returns true when the request was refused. */
    static boolean refuse(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (!namesRestricted(req)) return false;
        resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
        resp.setHeader("Content-Type", "application/json");
        resp.getWriter().write("{\"exception\":\"" + MESSAGE + "\"}");
        return true;
    }

    static boolean isRestricted(Object obj) {
        return obj != null && obj.getClass().getName().startsWith(RESTRICTED_PACKAGE);
    }

    /** {@code result} without restricted objects: a collection is copied without them. */
    static Object dropRestricted(Object result) {
        if (!(result instanceof Collection)) return result;
        Collection<?> rows = (Collection<?>)result;
        boolean any = false;
        for (Object row : rows) if (isRestricted(row)) { any = true; break; }
        if (!any) return result;
        List<Object> kept = new ArrayList<>();
        for (Object row : rows) if (!isRestricted(row)) kept.add(row);
        return kept;
    }

    /** Checks the text as sent and after each round of URL decoding (the servlets decode it again). */
    static boolean mentions(String text) {
        for (int round = 0; text != null && round <= MAX_DECODE_ROUNDS; round++) {
            String lower = text.toLowerCase(Locale.ROOT);
            for (String name : RESTRICTED_NAMES) if (lower.contains(name)) return true;
            String decoded;
            try {
                decoded = URLDecoder.decode(text, "UTF-8");
            } catch (IllegalArgumentException | UnsupportedEncodingException e) {
                return true; // undecodable: fail closed (the servlets could not run it either)
            }
            if (decoded.equals(text)) return false;
            text = decoded;
        }
        return false;
    }
}
