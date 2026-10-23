package org.ecocean.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.ecocean.Organization;
import org.ecocean.User;
import org.ecocean.auth.ServiceKey;
import org.ecocean.auth.ServiceKeyCredential;
import org.ecocean.auth.ServiceKeyException;
import org.ecocean.auth.ServiceKeyStore;
import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

class ServiceKeysTest {
    private static final String ORG_ID = "org-1";
    private static final long DAY = 24L * 3600L * 1000L;

    /** Everything the fake world needs; tests tweak the fields before calling run(). */
    private static final class World {
        String method = "GET";
        String servletPath = "/api/v3/organizations";
        String pathInfo = "/" + ORG_ID + "/service-keys";
        String authorization = basic("admin", "right");
        String origin;
        String host = "wildbook.example";
        String body = "";
        String cookieUser;
        User user;
        Organization org;
        List<Organization> allOrgs = Collections.emptyList();
        List<ServiceKey> keys = Collections.emptyList();
        ServiceKeyCredential.Issued issued;
        RuntimeException createFailure;
        boolean revokeFound = true;
        int revokeAllCount;

        // results
        int status;
        String text;
        HttpServletResponse response;
        Shepherd shepherd;
        Integer createdDays;
        Set<String> createdScopes;
        String createdLabel;
        String createdBy;
        boolean committed;
        List<Object> revokeArgs;
    }

    private static String basic(String u, String p) {
        return "Basic " + Base64.getEncoder().encodeToString((u + ":" + p).getBytes());
    }

    private static User user(String password, boolean admin, boolean service) {
        User u = mock(User.class);
        when(u.checkPassword(password)).thenReturn(!service);
        when(u.isAdmin(any())).thenReturn(admin);
        when(u.isServiceAccount()).thenReturn(service);
        when(u.getUsername()).thenReturn("admin");
        when(u.getId()).thenReturn("admin-uuid");
        return u;
    }

    private static Organization org(String id, String name) {
        Organization o = mock(Organization.class);
        when(o.getId()).thenReturn(id);
        when(o.getName()).thenReturn(name);
        return o;
    }

    private static ServiceKey key(String keyId, long expiresAt) {
        ServiceKey k = new ServiceKey(keyId, ORG_ID, "svc-uuid", "label", "stored-hash-value",
            "data:read", System.currentTimeMillis(), "admin", expiresAt);
        return k;
    }

    private static World admin() {
        World w = new World();
        w.user = user("right", true, false);
        w.org = org(ORG_ID, "Org One");
        return w;
    }

    private static World run(World w) throws Exception {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getMethod()).thenReturn(w.method);
        when(req.getServletPath()).thenReturn(w.servletPath);
        when(req.getPathInfo()).thenReturn(w.pathInfo);
        when(req.getHeader("Authorization")).thenReturn(w.authorization);
        when(req.getHeader("Origin")).thenReturn(w.origin);
        when(req.getHeader("Host")).thenReturn(w.host);
        // what Tomcat sees behind a TLS-terminating proxy; the Origin check must not depend on it
        when(req.getScheme()).thenReturn("http");
        when(req.getServerName()).thenReturn("tomcat-upstream");
        when(req.getServerPort()).thenReturn(8080);
        when(req.getReader()).thenReturn(new BufferedReader(new StringReader(w.body)));
        if (w.cookieUser != null) {
            java.security.Principal p = () -> w.cookieUser;
            when(req.getUserPrincipal()).thenReturn(p);
            when(req.getRemoteUser()).thenReturn(w.cookieUser);
        }
        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter out = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(out));
        w.response = resp;
        try (MockedConstruction<Shepherd> sh = mockConstruction(Shepherd.class, (m, c) -> {
                 when(m.getUser("admin")).thenReturn(w.user);
                 when(m.getUser(any(HttpServletRequest.class))).thenReturn(w.user);
                 when(m.getAllOrganizations()).thenReturn(w.allOrgs);
                 when(m.getAllOrganizationsStrict()).thenReturn(w.allOrgs);
                 when(m.commitDBTransactionWithStatus()).thenReturn(true);
                 when(m.getUserByUUID("admin-uuid")).thenReturn(w.user);
             });
             MockedStatic<ServiceKeyStore> store = mockStatic(ServiceKeyStore.class, i ->
                 // real rendering (toJson and the private helpers a static mock would otherwise
                 // intercept); everything else defaults. Not CALLS_REAL_METHODS: stubbing would then
                 // run the real revokeAll over the stubbed list.
                 i.getMethod().getName().equals("toJson")
                     || java.lang.reflect.Modifier.isPrivate(i.getMethod().getModifiers())
                     ? i.callRealMethod() : RETURNS_DEFAULTS.answer(i))) {
            store.when(() -> ServiceKeyStore.organization(any(), anyString()))
                .then(i -> ORG_ID.equals(i.getArgument(1)) ? w.org : null);
            store.when(() -> ServiceKeyStore.list(any(), anyString())).thenReturn(w.keys);
            store.when(() -> ServiceKeyStore.revoke(any(), anyString(), anyString(), anyString())).then(i -> {
                w.revokeArgs = Arrays.asList(i.getArgument(1), i.getArgument(2), i.getArgument(3));
                return w.revokeFound;
            });
            store.when(() -> ServiceKeyStore.revokeAll(any(), anyString(), anyString())).then(i -> {
                w.revokeArgs = Arrays.asList(i.getArgument(1), i.getArgument(2));
                return w.revokeAllCount;
            });
            store.when(() -> ServiceKeyStore.create(any(), any(), any(), any(), anyInt(), anyString())).then(i -> {
                if (w.createFailure != null) throw w.createFailure;
                w.createdLabel = i.getArgument(2);
                @SuppressWarnings("unchecked") Set<String> scopes = i.getArgument(3);
                w.createdScopes = scopes;
                w.createdDays = i.getArgument(4);
                w.createdBy = i.getArgument(5);
                w.issued = ServiceKeyCredential.issue();
                return w.issued;
            });
            store.when(() -> ServiceKeyStore.get(any(), anyString(), anyString())).then(i -> {
                ServiceKey k = new ServiceKey(w.issued.keyId, ORG_ID, "svc-uuid", w.createdLabel,
                    w.issued.secretHash, String.join(",", new java.util.TreeSet<>(w.createdScopes)),
                    System.currentTimeMillis(), w.createdBy,
                    System.currentTimeMillis() + w.createdDays * DAY);
                return k;
            });
            ServiceKeys servlet = new ServiceKeys();
            if ("GET".equals(w.method)) servlet.doGet(req, resp);
            else if ("POST".equals(w.method)) servlet.doPost(req, resp);
            else if ("DELETE".equals(w.method)) servlet.doDelete(req, resp);
            ArgumentCaptor<Integer> status = ArgumentCaptor.forClass(Integer.class);
            verify(resp, atLeastOnce()).setStatus(status.capture());
            w.status = status.getValue();
            verify(resp).setHeader("Cache-Control", "no-store");
            w.text = out.toString();
            if (!sh.constructed().isEmpty()) {
                w.shepherd = sh.constructed().get(0);
                verify(w.shepherd).rollbackAndClose();
                w.committed = mockingDetails(w.shepherd).getInvocations().stream()
                    .anyMatch(i -> i.getMethod().getName().equals("commitDBTransactionWithStatus"));
            }
            if (w.status >= 400) {
                JSONObject err = new JSONObject(w.text);
                assertFalse(err.getBoolean("success"), "error shape: " + w.text);
                assertTrue(err.has("error"), "error message: " + w.text);
                assertFalse(w.committed, "a refused request never commits");
            }
        }
        return w;
    }

    @Test void noBasicIs401() throws Exception {
        World w = admin();
        w.authorization = null;
        assertEquals(401, run(w).status, "no credential");
        w = admin();
        w.authorization = "Bearer something";
        assertEquals(401, run(w).status, "not Basic");
    }

    @Test void cookieSessionAloneIs401() throws Exception {
        for (String method : new String[] { "GET", "POST", "DELETE" }) {
            World w = admin();
            w.method = method;
            w.authorization = null;
            w.cookieUser = "admin";
            w.body = "{\"scopes\":[\"data:read\"]}";
            assertEquals(401, run(w).status, method + " with a session but no Basic");
            assertNull(w.issued, "nothing created");
        }
    }

    @Test void wrongPasswordIs401() throws Exception {
        World w = admin();
        w.authorization = basic("admin", "WRONG");
        assertEquals(401, run(w).status, "wrong password");
        w = admin();
        w.user = null;
        assertEquals(401, run(w).status, "unknown user");
    }

    @Test void nonAdminIs403() throws Exception {
        for (String method : new String[] { "GET", "POST", "DELETE" }) {
            World w = admin();
            w.method = method;
            w.user = user("right", false, false);
            w.body = "{\"scopes\":[\"data:read\"]}";
            assertEquals(403, run(w).status, method + " by a non-admin");
            assertNull(w.issued, "nothing created");
        }
        World w = admin();
        w.method = "DELETE";
        w.pathInfo = "/" + ORG_ID + "/service-keys/some-key";
        w.user = user("right", false, false);
        assertEquals(403, run(w).status, "revoke one by a non-admin");
    }

    @Test void serviceUserCannotManage() throws Exception {
        World w = admin();
        w.user = user("right", true, true);
        assertEquals(401, run(w).status, "service account Basic is refused");
        w = admin();
        w.user = user("right", true, false);
        when(w.user.isServiceAccount()).thenReturn(true);
        assertEquals(401, run(w).status, "service account refused even if checkPassword said yes");
    }

    private static int mutate(String method, String origin, String host) throws Exception {
        World w = admin();
        w.method = method;
        w.origin = origin;
        w.host = host;
        w.body = "{\"scopes\":[\"data:read\"]}";
        run(w);
        if (w.status == 403) assertNull(w.issued, "nothing created");
        return w.status;
    }

    @Test void foreignOriginMutationIs403() throws Exception {
        assertEquals(403, mutate("POST", "https://evil.example", "wildbook.example"), "foreign origin POST");
        assertEquals(403, mutate("DELETE", "https://evil.example", "wildbook.example"), "foreign origin DELETE");
        assertEquals(403, mutate("DELETE", "https://wildbook.example.evil.example", "wildbook.example"),
            "suffix trick");
        assertEquals(403, mutate("DELETE", "https://wildbook.example:8443", "wildbook.example"), "other port");
        assertEquals(403, mutate("POST", "https://wildbook.example", null), "Origin without a Host header");
        assertEquals(403, mutate("POST", "not a uri at all", "wildbook.example"), "unparseable Origin");
        assertEquals(403, mutate("POST", "https://wildbook.example/path", "wildbook.example"), "Origin with a path");

        assertEquals(201, mutate("POST", "https://wildbook.example", "wildbook.example"),
            "same origin allowed although Tomcat sees http://tomcat-upstream:8080");
        assertEquals(201, mutate("POST", "https://WildBook.Example", "wildbook.EXAMPLE"), "case-insensitive");
        assertEquals(200, mutate("DELETE", "https://wildbook.example:443", "wildbook.example"),
            "default port spelled out in Origin");
        assertEquals(200, mutate("DELETE", "https://wildbook.example", "wildbook.example:443"),
            "default port spelled out in Host");
        assertEquals(201, mutate("POST", null, "wildbook.example"), "no Origin (curl) allowed");
        assertEquals(201, mutate("POST", null, null), "no Origin and no Host allowed");

        World get = admin();
        get.origin = "https://evil.example";
        assertEquals(200, run(get).status, "reads are not origin-checked");
    }

    @Test void sameHostDifferentSchemeAllowed() throws Exception {
        assertEquals(201, mutate("POST", "http://wb.example", "wb.example"), "scheme ignored");
        assertEquals(200, mutate("DELETE", "https://wb.example", "wb.example"), "scheme ignored");
    }

    @Test void nullOriginIs403() throws Exception {
        assertEquals(403, mutate("POST", "null", "wb.example"), "opaque origin");
        assertEquals(403, mutate("DELETE", "null", "wb.example"), "opaque origin");
    }

    @Test void hostWithPortMatches() throws Exception {
        assertEquals(201, mutate("POST", "https://wb.example:8443", "wb.example:8443"), "same non-default port");
        assertEquals(403, mutate("POST", "https://wb.example:8443", "wb.example:9443"), "different port");
        assertEquals(403, mutate("POST", "https://wb.example", "wb.example:8443"), "port only on Host");
    }

    @Test void createReturnsPlaintextOnce() throws Exception {
        World w = admin();
        w.method = "POST";
        w.body = "{\"label\":\"ingest bot\",\"scopes\":[\"data:read\",\"submissions:write\"],\"days\":30}";
        run(w);
        assertEquals(201, w.status, "created: " + w.text);
        assertTrue(w.committed, "committed");
        JSONObject body = new JSONObject(w.text);
        assertEquals(w.issued.plaintext, body.getString("key"), "plaintext returned");
        assertEquals(w.issued.keyId, body.getString("keyId"), "key id");
        assertTrue(body.getLong("expiresAt") > System.currentTimeMillis() + 29 * DAY, "expiresAt");
        assertFalse(w.text.contains(w.issued.secretHash), "no hash in the create response");
        assertEquals("ingest bot", w.createdLabel, "label passed");
        assertEquals(Set.of("data:read", "submissions:write"), w.createdScopes, "scopes passed");
        assertEquals(Integer.valueOf(30), w.createdDays, "days passed");
        assertEquals("admin-uuid", w.createdBy, "createdBy = the admin's user id");
        assertEquals("admin-uuid", body.getString("createdBy"), "create response createdBy");
        assertEquals("admin", body.getString("createdByName"), "create response createdByName");

        World list = admin();
        ServiceKey stored = new ServiceKey(w.issued.keyId, ORG_ID, "svc-uuid", "ingest bot", w.issued.secretHash,
            "data:read,submissions:write", System.currentTimeMillis(), "admin-uuid", System.currentTimeMillis() + 30 * DAY);
        list.keys = Arrays.asList(stored);
        run(list);
        assertEquals(200, list.status, "listed");
        assertFalse(list.text.contains(w.issued.plaintext), "list never shows the plaintext");
        assertFalse(list.text.contains(w.issued.secretHash), "list never shows the hash");
        JSONArray keys = new JSONObject(list.text).getJSONArray("keys");
        assertEquals(1, keys.length(), "one key");
        JSONObject k = keys.getJSONObject(0);
        assertEquals(w.issued.keyId, k.getString("keyId"), "keyId");
        assertEquals("ingest bot", k.getString("label"), "label");
        assertEquals(new JSONArray(Arrays.asList("data:read", "submissions:write")).toString(),
            k.getJSONArray("scopes").toString(), "scopes");
        assertTrue(k.isNull("revokedAt"), "not revoked");
        assertTrue(k.isNull("lastUsedAt"), "never used");
        assertEquals("admin-uuid", k.getString("createdBy"), "createdBy is the user id");
        assertEquals("admin", k.getString("createdByName"), "createdByName resolved at read time");
        assertTrue(k.isNull("revokedBy"), "no revoker");
        assertTrue(k.isNull("revokedByName"), "no revoker name");
        assertFalse(k.has("key"), "no plaintext field");
    }

    @Test void createValidationErrorsSurface() throws Exception {
        World badJson = admin();
        badJson.method = "POST";
        badJson.body = "{not json";
        assertEquals(400, run(badJson).status, "malformed body");

        World noScopes = admin();
        noScopes.method = "POST";
        noScopes.body = "{\"label\":\"x\"}";
        assertEquals(400, run(noScopes).status, "scopes required");

        World badDays = admin();
        badDays.method = "POST";
        badDays.body = "{\"scopes\":[\"data:read\"],\"days\":\"ninety\"}";
        assertEquals(400, run(badDays).status, "days must be a number");

        World fractionalDays = admin();
        fractionalDays.method = "POST";
        fractionalDays.body = "{\"scopes\":[\"data:read\"],\"days\":1.5}";
        assertEquals(400, run(fractionalDays).status, "days must be whole");

        World badScopeType = admin();
        badScopeType.method = "POST";
        badScopeType.body = "{\"scopes\":[7]}";
        assertEquals(400, run(badScopeType).status, "scopes are strings");

        World cap = admin();
        cap.method = "POST";
        cap.body = "{\"scopes\":[\"data:read\"]}";
        cap.createFailure = new ServiceKeyException(409, "organization already has 2 active keys");
        run(cap);
        assertEquals(409, cap.status, "cap");
        assertEquals("organization already has 2 active keys", new JSONObject(cap.text).getString("error"), "message");
    }

    @Test void unknownOrgIs404() throws Exception {
        for (String method : new String[] { "GET", "POST", "DELETE" }) {
            World w = admin();
            w.method = method;
            w.pathInfo = "/no-such-org/service-keys";
            w.body = "{\"scopes\":[\"data:read\"]}";
            assertEquals(404, run(w).status, method + " on an unknown org");
        }
    }

    @Test void unknownPathsAre404() throws Exception {
        String[][] paths = {
            { "/api/v3/organizations", "/" + ORG_ID },
            { "/api/v3/organizations", null },
            { "/api/v3/organizations", "/" + ORG_ID + "/members" },
            { "/api/v3/organizations", "/" + ORG_ID + "/service-keys/k/extra" },
            { "/api/v3/service-keys", "/elsewhere" },
            { "/api/v3/service-keys", null },
        };
        for (String[] p : paths) {
            World w = admin();
            w.servletPath = p[0];
            w.pathInfo = p[1];
            assertEquals(404, run(w).status, p[0] + p[1]);
        }
        World w = admin();
        w.method = "POST";
        w.servletPath = "/api/v3/service-keys";
        w.pathInfo = "/organizations";
        assertEquals(405, run(w).status, "discovery is read-only");
        w = admin();
        w.method = "POST";
        w.pathInfo = "/" + ORG_ID + "/service-keys/some-key";
        assertEquals(405, run(w).status, "no POST on a single key");
    }

    @Test void omittedDaysDefaultsTo90() throws Exception {
        World w = admin();
        w.method = "POST";
        w.body = "{\"scopes\":[\"data:read\"]}";
        run(w);
        assertEquals(201, w.status, "created");
        assertEquals(Integer.valueOf(90), w.createdDays, "servlet applies the default");
        long expiresAt = new JSONObject(w.text).getLong("expiresAt");
        long expected = System.currentTimeMillis() + 90 * DAY;
        assertTrue(Math.abs(expiresAt - expected) < 60_000L, "~90 days out");
        assertNull(w.createdLabel, "label optional");
    }

    @Test void revokeOneAndAll() throws Exception {
        World one = admin();
        one.method = "DELETE";
        one.pathInfo = "/" + ORG_ID + "/service-keys/key-9";
        run(one);
        assertEquals(200, one.status, "revoked");
        assertTrue(one.committed, "committed");
        assertTrue(new JSONObject(one.text).getBoolean("revoked"), "found");

        World missing = admin();
        missing.method = "DELETE";
        missing.pathInfo = "/" + ORG_ID + "/service-keys/key-9";
        missing.revokeFound = false;
        assertEquals(404, run(missing).status, "unknown key under this org");

        World all = admin();
        all.method = "DELETE";
        all.revokeAllCount = 2;
        run(all);
        assertEquals(200, all.status, "revoke all");
        assertTrue(all.committed, "committed");
        assertEquals(2, new JSONObject(all.text).getInt("revoked"), "count");
    }

    @Test void revokePassesOrgAndAdmin() throws Exception {
        World one = admin();
        one.method = "DELETE";
        one.pathInfo = "/" + ORG_ID + "/service-keys/key-9";
        run(one);
        assertEquals(200, one.status, "revoked");
        assertEquals(Arrays.asList(ORG_ID, "key-9", "admin-uuid"), one.revokeArgs,
            "org-scoped, revokedBy = the admin's user id");

        World all = admin();
        all.method = "DELETE";
        run(all);
        assertEquals(Arrays.asList(ORG_ID, "admin-uuid"), all.revokeArgs, "revoke all is org-scoped");
    }

    @Test void discoverySiteAdminOnly() throws Exception {
        World w = admin();
        w.servletPath = "/api/v3/service-keys";
        w.pathInfo = "/organizations";
        w.allOrgs = Arrays.asList(org("o1", "Alpha"), org("o2", "Beta"));
        run(w);
        assertEquals(200, w.status, "discovery");
        JSONArray orgs = new JSONObject(w.text).getJSONArray("organizations");
        assertEquals(2, orgs.length(), "every organization for a site admin");
        assertEquals("o1", orgs.getJSONObject(0).getString("id"), "id");
        assertEquals("Alpha", orgs.getJSONObject(0).getString("name"), "name");

        World orgAdmin = admin();
        orgAdmin.servletPath = "/api/v3/service-keys";
        orgAdmin.pathInfo = "/organizations";
        orgAdmin.user = user("right", false, false);
        when(orgAdmin.user.hasRoleByName(eq(Organization.ROLE_MANAGER), any())).thenReturn(true);
        Organization mine = org("o1", "Alpha");
        when(mine.hasMember(orgAdmin.user)).thenReturn(true);
        orgAdmin.allOrgs = Arrays.asList(mine);
        run(orgAdmin);
        assertEquals(200, orgAdmin.status, "discovery");
        assertEquals(0, new JSONObject(orgAdmin.text).getJSONArray("organizations").length(),
            "an orgAdmin gets [] in PR-B");

        World anon = admin();
        anon.servletPath = "/api/v3/service-keys";
        anon.pathInfo = "/organizations";
        anon.authorization = null;
        assertEquals(401, run(anon).status, "discovery needs Basic");
    }
}
