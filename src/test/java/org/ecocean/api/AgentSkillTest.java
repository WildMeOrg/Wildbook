package org.ecocean.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

class AgentSkillTest {

    // serve with a given pathInfo; return [status, contentType, body]
    private String[] serve(String pathInfo) throws Exception {
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        when(req.getPathInfo()).thenReturn(pathInfo);
        StringWriter out = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(out));
        new AgentSkill().doGetForTest(req, resp);
        org.mockito.ArgumentCaptor<Integer> st = org.mockito.ArgumentCaptor.forClass(Integer.class);
        verify(resp).setStatus(st.capture());
        org.mockito.ArgumentCaptor<String> ct = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(resp).setContentType(ct.capture());
        return new String[] { String.valueOf(st.getValue()), ct.getValue(), out.toString() };
    }

    @Test void bare_path_serves_index() throws Exception {
        String[] r = serve(null);
        assertEquals("200", r[0], "bare path returns 200");
        assertEquals("text/markdown; charset=UTF-8", r[1]);
        assertTrue(r[2].contains("find-missed-matches"), "bare path serves the index/toolbox");
    }

    @Test void trailing_slash_serves_index() throws Exception {
        assertEquals("200", serve("/")[0], "trailing slash returns the index");
    }

    @Test void each_skill_name_serves_markdown() throws Exception {
        for (String name : AgentSkill.SKILL_RESOURCES.keySet()) {
            String[] r = serve("/" + name);
            assertEquals("200", r[0], name + " returns 200");
            assertEquals("text/markdown; charset=UTF-8", r[1], name + " is markdown");
            assertFalse(r[2].isEmpty(), name + " is non-empty");
        }
    }

    @Test void api_reference_carries_allowlist_and_no_leak() throws Exception {
        String md = serve("/api-reference")[2];
        for (String idx : SearchApi.TOKEN_ALLOWED_INDICES)
            assertTrue(md.contains(idx), "api-reference must list allowed index " + idx);
        assertTrue(md.contains("occurrence") && md.contains("media_asset"),
            "api-reference must name the denied indices");
        assertTrue(md.contains("403"), "api-reference must state denied indices return 403");
        assertTrue(md.contains("Authorization: Bearer"), "api-reference documents bearer auth");
        for (String acl : new String[] {
                "publiclyReadable", "submitterUserId", "submitterUserIds", "viewUsers", "editUsers"})
            assertFalse(md.contains(acl), "must not expose internal ACL field name " + acl);
    }

    @Test void unknown_and_malformed_names_return_404() throws Exception {
        for (String bad : new String[] {
                "/nope", "/index", "/api-reference.md", "/API-REFERENCE",
                "/find-missed-matches/extra", "//double", "/..", "/../secret",
                "/a%2fb", "/%2e%2e", "/with space", "/trailing/" }) {
            assertEquals("404", serve(bad)[0], bad + " must 404");
        }
    }

    @Test void success_sets_nosniff_header() throws Exception {
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        when(req.getPathInfo()).thenReturn(null);
        when(resp.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
        new AgentSkill().doGetForTest(req, resp);
        verify(resp).setHeader("X-Content-Type-Options", "nosniff");
    }
}
