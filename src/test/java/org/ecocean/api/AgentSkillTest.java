package org.ecocean.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

class AgentSkillTest {

    private String body() throws Exception {
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter out = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(out));
        new AgentSkill().doGetForTest(req, resp);
        verify(resp).setStatus(200);
        verify(resp).setContentType("text/markdown; charset=UTF-8");
        return out.toString();
    }

    @Test void serves_markdown_with_key_anchors() throws Exception {
        String md = body();
        assertFalse(md.isEmpty(), "skill body must be non-empty");
        assertTrue(md.contains("Authorization: Bearer"), "documents bearer auth");
        assertTrue(md.contains("/api/v3/media/resolve"), "documents media resolve");
        assertTrue(md.contains("/api/v3/search/"), "documents search");
        for (String idx : new String[] {"encounter", "individual", "annotation"})
            assertTrue(md.contains(idx), "mentions index " + idx);
        assertTrue(md.toLowerCase().contains("never ask for") || md.toLowerCase().contains("never give"),
            "contains the never-share-credentials guidance");
    }

    @Test void skill_index_claims_match_search_allowlist() throws Exception {
        String md = body();
        for (String idx : SearchApi.TOKEN_ALLOWED_INDICES)
            assertTrue(md.contains(idx), "skill must list allowed index " + idx);
        assertTrue(md.contains("occurrence") && md.contains("media_asset"),
            "skill must name the denied indices");
        assertTrue(md.contains("403"), "skill must state denied indices return 403");
        for (String denied : new String[] {"occurrence", "media_asset"})
            assertFalse(SearchApi.TOKEN_ALLOWED_INDICES.contains(denied),
                "denied index " + denied + " must not be in the allowlist");
    }

    @Test void skill_does_not_leak_internal_acl_field_names() throws Exception {
        String md = body();
        for (String acl : new String[] {
                "publiclyReadable", "submitterUserId", "submitterUserIds", "viewUsers", "editUsers"})
            assertFalse(md.contains(acl), "skill must not expose internal ACL field name " + acl);
    }
}
