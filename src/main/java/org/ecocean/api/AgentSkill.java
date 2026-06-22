package org.ecocean.api;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * GET /api/v3/agent-skill
 * Serves the curated, version-controlled agent-skill markdown (classpath resource). Anonymous: a
 * how-to-authenticate doc cannot itself require auth, and it contains no secrets — only API docs.
 */
public class AgentSkill extends ApiBase {
    private static final String RESOURCE = "/agent-skill.md";

    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response)
        throws IOException {
        handle(request, response);
    }

    void doGetForTest(HttpServletRequest request, HttpServletResponse response) throws IOException {
        handle(request, response);
    }

    private void handle(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String md = readResource();
        if (md == null) {
            response.setStatus(500);
            response.setContentType("text/plain; charset=UTF-8");
            response.getWriter().write("agent skill unavailable");
            return;
        }
        response.setStatus(200);
        response.setContentType("text/markdown; charset=UTF-8");
        response.getWriter().write(md);
    }

    private String readResource() throws IOException {
        try (InputStream in = AgentSkill.class.getResourceAsStream(RESOURCE)) {
            if (in == null) return null;
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
