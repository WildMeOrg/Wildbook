package org.ecocean.servlet;

import javax.servlet.http.HttpServletRequest;

/**
 * Central configuration for the React frontend base path.
 * Changing REACT_BASE_PATH updates every legacy link and redirect.
 */
public final class ReactRouter {
    private static final String REACT_BASE_PATH = "/react";

    private ReactRouter() {}

    public static String getBasePath() {
        return REACT_BASE_PATH;
    }

    public static String getBasePath(HttpServletRequest request) {
        return request.getContextPath() + REACT_BASE_PATH;
    }

    public static String path(String route) {
        if (route == null || route.isEmpty() || "/".equals(route)) {
            return REACT_BASE_PATH + "/";
        }
        if (route.startsWith("/")) {
            return REACT_BASE_PATH + route;
        }
        return REACT_BASE_PATH + "/" + route;
    }

    public static String path(HttpServletRequest request, String route) {
        return request.getContextPath() + path(route);
    }
}
