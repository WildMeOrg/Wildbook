package org.ecocean.servlet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import javax.jdo.PersistenceManager;
import javax.jdo.Query;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.ecocean.Organization;
import org.ecocean.User;
import org.ecocean.shepherd.core.Shepherd;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;

/**
 * Account edits are accepted only by POST, and only from the same origin when an Origin is sent.
 * A non-admin's edit of a user outside their organizations, or addition of an organization they do
 * not belong to, is refused with 403 before anything is persisted.
 */
class UserCreateMethodTest {
    @TempDir Path webappRoot;

    private final User requester = new User("org-admin", "x", "salt");
    private final User outsider = new User("outsider", "x", "salt");
    private final Organization ownOrg = new Organization("own");
    private final Organization foreignOrg = new Organization("foreign");
    private final PersistenceManager pm = mock(PersistenceManager.class);

    private UserCreate servlet() throws Exception {
        ServletContext ctx = mock(ServletContext.class);
        when(ctx.getRealPath("/")).thenReturn(webappRoot.resolve("wildbook").toString());
        ServletConfig config = mock(ServletConfig.class);
        when(config.getServletContext()).thenReturn(ctx);
        UserCreate servlet = new UserCreate();
        servlet.init(config);
        return servlet;
    }

    // a same-origin POST from a signed-in non-admin
    private HttpServletRequest sameOriginPost(String uuid, String... organizationIds) {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getMethod()).thenReturn("POST");
        when(req.getHeader("Origin")).thenReturn("https://wildbook.example");
        when(req.getHeader("Host")).thenReturn("wildbook.example");
        when(req.getParameter("uuid")).thenReturn(uuid);
        when(req.getParameter("isEdit")).thenReturn("true");
        when(req.getParameterValues("organization")).thenReturn(organizationIds);
        when(req.isUserInRole(anyString())).thenReturn(false);
        return req;
    }

    // the requester belongs to ownOrg only; the outsider to foreignOrg only; nobody holds a role
    private MockedConstruction<Shepherd> shepherds() {
        Query<?> query = mock(Query.class);
        when(query.execute(any())).thenReturn(Collections.emptyList());
        when(query.execute(any(), any())).thenReturn(0L);
        doReturn(query).when(pm).newQuery(any(Class.class), anyString());
        return mockConstruction(Shepherd.class, (sh, c) -> {
            when(sh.getPM()).thenReturn(pm);
            when(sh.getUser(any(HttpServletRequest.class))).thenReturn(requester);
            when(sh.getUserByUUID(outsider.getId())).thenReturn(outsider);
            when(sh.getOrganization(ownOrg.getId())).thenReturn(ownOrg);
            when(sh.getOrganization(foreignOrg.getId())).thenReturn(foreignOrg);
            when(sh.getAllOrganizationsForUser(requester)).thenReturn(List.of(ownOrg));
            when(sh.getAllOrganizationsForUser(outsider)).thenReturn(List.of(foreignOrg));
        });
    }

    private HttpServletResponse response() throws Exception {
        HttpServletResponse resp = mock(HttpServletResponse.class);
        when(resp.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
        return resp;
    }

    private void assertRefusedBeforePersisting(MockedConstruction<Shepherd> shepherds,
        HttpServletResponse resp) throws Exception {
        verify(resp).sendError(HttpServletResponse.SC_FORBIDDEN);
        assertEquals(1, shepherds.constructed().size());
        Shepherd sh = shepherds.constructed().get(0);
        verify(sh).rollbackDBTransaction();
        verify(sh, never()).commitDBTransaction();
        verify(sh, never()).updateDBTransaction();
        verify(pm, never()).makePersistent(any());
        verify(pm, never()).deletePersistentAll(any(java.util.Collection.class));
    }

    @Test void doPostRefusesOutsideOrgTarget() throws Exception {
        HttpServletRequest req = sameOriginPost(outsider.getId());
        HttpServletResponse resp = response();
        try (MockedConstruction<Shepherd> sh = shepherds()) {
            servlet().doPost(req, resp);
            assertRefusedBeforePersisting(sh, resp);
        }
        assertEquals("outsider", outsider.getUsername(), "target left unchanged");
    }

    @Test void doPostRefusesForeignOrgAddition() throws Exception {
        String newUserId = java.util.UUID.randomUUID().toString();
        HttpServletRequest req = sameOriginPost(newUserId, foreignOrg.getId());
        when(req.getParameter("username")).thenReturn("new-member");
        when(req.getParameter("password")).thenReturn("pw");
        when(req.getParameter("password2")).thenReturn("pw");
        HttpServletResponse resp = response();
        try (MockedConstruction<Shepherd> sh = shepherds()) {
            servlet().doPost(req, resp);
            assertRefusedBeforePersisting(sh, resp);
        }
        assertTrue(foreignOrg.getMembers() == null || foreignOrg.getMembers().isEmpty(),
            "no member added to the foreign organization");
    }

    @Test void getIs405() throws Exception {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getParameter(anyString())).thenReturn("x");
        HttpServletResponse resp = mock(HttpServletResponse.class);
        try (MockedConstruction<Shepherd> sh = mockConstruction(Shepherd.class)) {
            new UserCreate().doGet(req, resp);
            assertTrue(sh.constructed().isEmpty(), "no Shepherd for GET");
        }
        verify(resp).setHeader("Allow", "POST");
        verify(resp).sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        verify(resp, never()).getWriter();
    }

    @Test void foreignOriginPostIs403BeforeAnyDbWork() throws Exception {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("Origin")).thenReturn("https://evil.example");
        when(req.getHeader("Host")).thenReturn("wildbook.example");
        when(req.getParameter(anyString())).thenReturn("x");
        HttpServletResponse resp = mock(HttpServletResponse.class);
        try (MockedConstruction<Shepherd> sh = mockConstruction(Shepherd.class)) {
            new UserCreate().doPost(req, resp);
            assertTrue(sh.constructed().isEmpty(), "no Shepherd before the origin check");
        }
        verify(resp).sendError(HttpServletResponse.SC_FORBIDDEN);
        verify(resp, never()).getWriter();
    }
}
