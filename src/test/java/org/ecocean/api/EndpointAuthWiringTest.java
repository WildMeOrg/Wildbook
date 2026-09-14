package org.ecocean.api;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Asserts that the AuthToken and CanUserAccess endpoints are both registered in
 * web.xml AND gated by the appropriate Shiro rules.  No DB, no containers.
 *
 * <p>Maven runs tests with CWD = module root, so relative File paths resolve
 * against the project root.
 */
class EndpointAuthWiringTest {

    private static List<String> lines;

    @BeforeAll
    static void loadWebXml() throws Exception {
        File webXml = new File("src/main/webapp/WEB-INF/web.xml");
        assertTrue(webXml.exists(),
                "web.xml not found at expected path: " + webXml.getAbsolutePath());
        lines = Files.readAllLines(webXml.toPath());
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static String fullText() {
        return String.join("\n", lines);
    }

    /** Returns the 0-based index of the first line that contains {@code needle}, or -1. */
    private static int lineIndexOf(String needle) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains(needle)) {
                return i;
            }
        }
        return -1;
    }

    // -----------------------------------------------------------------------
    // Assertion 1 & 2 — servlet registrations present
    // -----------------------------------------------------------------------

    @Test
    void authToken_servletClassIsRegistered() {
        assertTrue(fullText().contains("<servlet-class>org.ecocean.api.AuthToken</servlet-class>"),
                "web.xml must contain a <servlet-class> declaration for org.ecocean.api.AuthToken");
    }

    @Test
    void authToken_urlPatternIsRegistered() {
        assertTrue(fullText().contains("<url-pattern>/api/v3/auth/token</url-pattern>"),
                "web.xml must contain a <url-pattern> mapping for /api/v3/auth/token");
    }

    @Test
    void canUserAccess_servletClassIsRegistered() {
        assertTrue(fullText().contains("<servlet-class>org.ecocean.api.CanUserAccess</servlet-class>"),
                "web.xml must contain a <servlet-class> declaration for org.ecocean.api.CanUserAccess");
    }

    @Test
    void canUserAccess_urlPatternIsRegistered() {
        assertTrue(fullText().contains("<url-pattern>/api/v3/can-user-access</url-pattern>"),
                "web.xml must contain a <url-pattern> mapping for /api/v3/can-user-access");
    }

    // -----------------------------------------------------------------------
    // Assertion 3 — /api/v3/auth/token is auth-gated (not anon)
    // -----------------------------------------------------------------------

    @Test
    void authToken_shiroRuleIsAuthGated() {
        // Find the Shiro [urls] rule line for the token endpoint (non-comment)
        String ruleLine = lines.stream()
                .filter(l -> {
                    String t = l.stripLeading();
                    return !t.startsWith("#") && t.contains("/api/v3/auth/token");
                })
                .findFirst()
                .orElse(null);

        assertNotNull(ruleLine,
                "Shiro [urls] must contain a rule line for /api/v3/auth/token");

        // The rule value must not be just "anon"
        String value = ruleLine.substring(ruleLine.indexOf("/api/v3/auth/token")
                + "/api/v3/auth/token".length()).trim();
        if (value.startsWith("=")) {
            value = value.substring(1).trim();
        }
        assertFalse("anon".equalsIgnoreCase(value.trim()),
                "Shiro rule for /api/v3/auth/token must not be 'anon' (was: '" + value + "')");

        assertTrue(ruleLine.contains("authc"),
                "Shiro rule for /api/v3/auth/token must contain 'authc' (was: '" + ruleLine.strip() + "')");
    }

    // -----------------------------------------------------------------------
    // Assertion 4 — /api/v3/can-user-access requires roles[admin]
    // -----------------------------------------------------------------------

    @Test
    void canUserAccess_shiroRuleRequiresAdminRole() {
        String ruleLine = lines.stream()
                .filter(l -> {
                    String t = l.stripLeading();
                    return !t.startsWith("#") && t.contains("/api/v3/can-user-access");
                })
                .findFirst()
                .orElse(null);

        assertNotNull(ruleLine,
                "Shiro [urls] must contain a rule line for /api/v3/can-user-access");

        assertTrue(ruleLine.contains("roles[admin]"),
                "Shiro rule for /api/v3/can-user-access must contain 'roles[admin]' (was: '"
                        + ruleLine.strip() + "')");
    }

    // -----------------------------------------------------------------------
    // Assertion 5 — ordering guard: both new rules appear before /** catch-all
    // -----------------------------------------------------------------------

    @Test
    void shiroRules_newEndpointsBeforeCatchAll() {
        // Find the catch-all line, if any (/** = ..., not commented out)
        int catchAllIdx = -1;
        for (int i = 0; i < lines.size(); i++) {
            String t = lines.get(i).stripLeading();
            if (!t.startsWith("#") && t.startsWith("/**")) {
                catchAllIdx = i;
                break;
            }
        }

        if (catchAllIdx == -1) {
            // No catch-all — nothing to check
            return;
        }

        int tokenRuleIdx = -1;
        int canAccessRuleIdx = -1;
        for (int i = 0; i < lines.size(); i++) {
            String t = lines.get(i).stripLeading();
            if (t.startsWith("#")) continue;
            if (t.contains("/api/v3/auth/token") && tokenRuleIdx == -1) {
                tokenRuleIdx = i;
            }
            if (t.contains("/api/v3/can-user-access") && canAccessRuleIdx == -1) {
                canAccessRuleIdx = i;
            }
        }

        assertNotEquals(-1, tokenRuleIdx,
                "Shiro [urls] must have a rule for /api/v3/auth/token before the /** catch-all");
        assertTrue(tokenRuleIdx < catchAllIdx,
                "Shiro rule for /api/v3/auth/token (line " + (tokenRuleIdx + 1)
                        + ") must appear before the /** catch-all (line " + (catchAllIdx + 1) + ")");

        assertNotEquals(-1, canAccessRuleIdx,
                "Shiro [urls] must have a rule for /api/v3/can-user-access before the /** catch-all");
        assertTrue(canAccessRuleIdx < catchAllIdx,
                "Shiro rule for /api/v3/can-user-access (line " + (canAccessRuleIdx + 1)
                        + ") must appear before the /** catch-all (line " + (catchAllIdx + 1) + ")");
    }

    // -----------------------------------------------------------------------
    // Assertion 6 — /api/v3/search/** wired to tokenAuthSearch ONLY
    // -----------------------------------------------------------------------

    @Test
    void searchPath_wiredToTokenFilterOnly() {
        // token filter declared in [main]
        assertTrue(fullText().contains(
            "tokenAuthSearch = org.ecocean.security.WildbookTokenAuthenticationFilter"),
            "web.xml [main] must declare tokenAuthSearch = WildbookTokenAuthenticationFilter");

        // search path mapped to the token filter (find the non-comment rule line)
        String ruleLine = lines.stream()
            .filter(l -> {
                String t = l.stripLeading();
                return !t.startsWith("#") && t.contains("/api/v3/search/**");
            })
            .findFirst()
            .orElse(null);
        assertNotNull(ruleLine,
            "Shiro [urls] must contain a rule for /api/v3/search/**");

        String value = ruleLine.substring(
            ruleLine.indexOf("/api/v3/search/**") + "/api/v3/search/**".length()).trim();
        if (value.startsWith("=")) value = value.substring(1).trim();
        assertEquals("tokenAuthSearch", value,
            "search path must map to tokenAuthSearch ONLY (no authc/roles chained); was: '"
                + value + "'");
    }

    // -----------------------------------------------------------------------
    // Assertion 7, 8, 9 — /api/v3/media/resolve wiring
    // -----------------------------------------------------------------------

    @Test
    void mediaResolve_servletClassIsRegistered() {
        assertTrue(fullText().contains("<servlet-class>org.ecocean.api.MediaResolveApi</servlet-class>"),
                "web.xml must register the MediaResolveApi servlet");
    }

    @Test
    void mediaResolve_urlPatternIsRegistered() {
        assertTrue(fullText().contains("<url-pattern>/api/v3/media/resolve</url-pattern>"),
                "web.xml must map /api/v3/media/resolve");
    }

    @Test
    void mediaResolve_shiroRuleIsTokenFilterOnly() {
        String ruleLine = lines.stream()
                .filter(l -> {
                    String t = l.stripLeading();
                    return !t.startsWith("#") && t.contains("/api/v3/media/resolve") && t.contains("tokenAuthSearch");
                })
                .findFirst().orElse(null);
        assertNotNull(ruleLine, "Shiro [urls] must contain a rule for /api/v3/media/resolve");
        String value = ruleLine.substring(
                ruleLine.indexOf("/api/v3/media/resolve") + "/api/v3/media/resolve".length()).trim();
        if (value.startsWith("=")) value = value.substring(1).trim();
        assertEquals("tokenAuthSearch", value,
                "media path must map to tokenAuthSearch ONLY (no authc/roles chained); was: '" + value + "'");
    }

    // -----------------------------------------------------------------------
    // Assertions — /api/v3/agent-skill wiring
    // -----------------------------------------------------------------------

    @Test
    void agentSkill_servletClassIsRegistered() {
        assertTrue(fullText().contains("<servlet-class>org.ecocean.api.AgentSkill</servlet-class>"),
                "web.xml must register the AgentSkill servlet");
    }

    @Test
    void agentSkill_urlPatternIsRegistered() {
        assertTrue(fullText().contains("<url-pattern>/api/v3/agent-skill</url-pattern>"),
                "web.xml must map /api/v3/agent-skill");
    }

    @Test
    void agentSkill_shiroRuleIsAnon() {
        String ruleLine = lines.stream()
                .filter(l -> { String t = l.stripLeading();
                    return !t.startsWith("#") && t.contains("/api/v3/agent-skill"); })
                .findFirst().orElse(null);
        assertNotNull(ruleLine, "Shiro [urls] must contain a rule for /api/v3/agent-skill");
        String value = ruleLine.substring(
                ruleLine.indexOf("/api/v3/agent-skill") + "/api/v3/agent-skill".length()).trim();
        if (value.startsWith("=")) value = value.substring(1).trim();
        assertEquals("anon", value,
                "the agent-skill doc must be anon (a how-to-auth doc can't require auth); was: '" + value + "'");
    }
}
