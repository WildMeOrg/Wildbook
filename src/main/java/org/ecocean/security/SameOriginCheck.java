package org.ecocean.security;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import javax.servlet.http.HttpServletRequest;

/** Same-origin rule for state-changing requests made by browsers. */
public final class SameOriginCheck {
    private SameOriginCheck() {}

    /**
     * No Origin (curl, server-to-server) is allowed. Otherwise the Origin's host[:port] must equal the
     * request's Host header, both lowercased with a default port (80/443) dropped. The scheme is
     * ignored, so a TLS-terminating proxy in front of Tomcat does not turn same-site requests away.
     * "null", an unparseable Origin, an Origin with userinfo, path, query or fragment, or a missing
     * Host header is refused.
     */
    public static boolean allows(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin == null) return true;
        String host = request.getHeader("Host");
        if (host == null || host.trim().isEmpty()) return false;
        try {
            URI uri = new URI(origin.trim());
            if (uri.getScheme() == null || uri.getHost() == null || uri.getRawUserInfo() != null) return false;
            if (uri.getRawPath() != null && !uri.getRawPath().isEmpty()) return false;
            if (uri.getRawQuery() != null || uri.getRawFragment() != null) return false;
            String originHost = uri.getHost().toLowerCase(Locale.ROOT);
            if (uri.getPort() > 0) originHost += ":" + uri.getPort();
            return withoutDefaultPort(originHost).equals(withoutDefaultPort(host.trim().toLowerCase(Locale.ROOT)));
        } catch (URISyntaxException ex) {
            return false;
        }
    }

    private static String withoutDefaultPort(String hostPort) {
        if (hostPort.endsWith(":80")) return hostPort.substring(0, hostPort.length() - 3);
        if (hostPort.endsWith(":443")) return hostPort.substring(0, hostPort.length() - 4);
        return hostPort;
    }
}
