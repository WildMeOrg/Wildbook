package org.ecocean.api;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * GET /api/v3/agent-skill            -> index.md (the toolbox)
 * GET /api/v3/agent-skill/<name>     -> that skill's markdown (name in SKILL_RESOURCES)
 *
 * Anonymous: these are how-to docs with no secrets. Real data access still requires a bearer token.
 * The name is validated (^[a-z0-9-]+$) and used only as a key into a fixed map — never concatenated
 * into a resource path.
 */
public class AgentSkill extends ApiBase {
    private static final String DIR = "/agent-skills/";
    private static final String INDEX = "index.md";
    private static final Pattern NAME = Pattern.compile("^[a-z0-9-]+$");

    // single source of truth: fetchable skill name -> resource filename (index.md is NOT fetchable)
    static final Map<String, String> SKILL_RESOURCES;
    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("api-reference", "api-reference.md");
        m.put("find-missed-matches", "find-missed-matches.md");
        m.put("find-misfiled-sightings", "find-misfiled-sightings.md");
        m.put("how-good-is-our-matching", "how-good-is-our-matching.md");
        m.put("review-id-problems", "review-id-problems.md");
        m.put("inat-to-wildbook-import", "inat-to-wildbook-import.md");
        SKILL_RESOURCES = Collections.unmodifiableMap(m);
    }

    // returns the resource filename to serve, or null for a 404
    static String resolveResource(String pathInfo) {
        if (pathInfo == null || pathInfo.equals("/")) return INDEX;
        String name = pathInfo.startsWith("/") ? pathInfo.substring(1) : pathInfo;
        if (!NAME.matcher(name).matches()) return null;
        return SKILL_RESOURCES.get(name);
    }

    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response)
        throws IOException {
        handle(request, response);
    }

    void doGetForTest(HttpServletRequest request, HttpServletResponse response) throws IOException {
        handle(request, response);
    }

    private void handle(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String resource = resolveResource(request.getPathInfo());
        if (resource == null) {
            response.setStatus(404);
            response.setContentType("text/plain; charset=UTF-8");
            response.getWriter().write("not found");
            return;
        }
        String md = readResource(resource);
        if (md == null) {
            response.setStatus(500);
            response.setContentType("text/plain; charset=UTF-8");
            response.getWriter().write("agent skill unavailable");
            return;
        }
        response.setStatus(200);
        response.setContentType("text/markdown; charset=UTF-8");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Cache-Control", "public, max-age=300");
        response.getWriter().write(md);
    }

    private String readResource(String filename) throws IOException {
        try (InputStream in = AgentSkill.class.getResourceAsStream(DIR + filename)) {
            if (in == null) return null;
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
