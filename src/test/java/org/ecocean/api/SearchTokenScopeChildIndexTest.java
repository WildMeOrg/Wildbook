package org.ecocean.api;

import io.restassured.RestAssured;
import io.restassured.response.Response;

import java.net.InetSocketAddress;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import javax.servlet.DispatcherType;

import org.apache.http.HttpHost;
import org.apache.shiro.web.servlet.IniShiroFilter;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.ecocean.CommonConfiguration;
import org.ecocean.OpenSearch;
import org.ecocean.Role;
import org.ecocean.User;
import org.ecocean.api.auth.JwtService;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end enforcement test for the token-scoped ANNOTATION + INDIVIDUAL child-index search paths.
 *
 * Companion to {@link SearchTokenScopeTest} (which covers the encounter index). Boots the same
 * harness shape: embedded Jetty + Shiro ({@code WildbookTokenAuthenticationFilter} wired onto
 * {@code /api/v3/search/**} via the shared test {@code shiro.ini}), a real OpenSearch container,
 * and a real PostgreSQL container. Seeds encounter, annotation and individual docs directly into
 * OpenSearch using the REAL production mappings, plus real {@link User}/{@link Role} rows, then
 * asserts:
 *
 * <ul>
 *   <li>a non-admin token sees only annotations whose denormalized parent ACL it can satisfy
 *       (publiclyReadable / submitterUserIds / viewUsers), with the ACL fields scrubbed,</li>
 *   <li>a non-admin token sees only individuals with at least one visible encounter (via the
 *       denormalized ACL union), and the returned doc is an identity-only allowlist (aggregates
 *       such as numberEncounters / users / encounterIds are dropped),</li>
 *   <li>an admin token is unscoped (sees both individuals) but the ACL fields are still scrubbed,</li>
 *   <li>a token to the occurrence index is 403 (not in the token allowlist).</li>
 * </ul>
 *
 * The JWT keypair is generated in {@code @BeforeAll} and published to CommonConfiguration so the
 * production filter (which loads keys via {@code JwtService.fromConfig("context0")}) and the test
 * signer share the same keys.
 */
@Testcontainers public class SearchTokenScopeChildIndexTest {

    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
        "postgres:15-alpine")
            .withDatabaseName("wildbook_test")
            .withUsername("wildbook")
            .withPassword("wildbook");

    @Container static GenericContainer<?> opensearch = new GenericContainer<>(DockerImageName.parse(
        "opensearchproject/opensearch:3.1.0"))
            .withExposedPorts(9200, 9300)
            .withEnv("discovery.type", "single-node")
            .withEnv("plugins.security.disabled", "true")
            .withEnv("OPENSEARCH_JAVA_OPTS", "-Xms512m -Xmx512m")
            .withEnv("DISABLE_INSTALL_DEMO_CONFIG", "true")
            .waitingFor(new HttpWaitStrategy()
            .forPort(9200)
            .forPath("/_cluster/health")
            .forStatusCode(200)
            .withStartupTimeout(java.time.Duration.ofMinutes(2)));

    private static JwtService testJwtService;
    private static String aliceUuid;
    private static String adminUuid;
    private static String bobUuid;

    @BeforeAll static void setUp() throws Exception {
        System.out.println("=== Starting SearchTokenScopeChildIndexTest Setup ===");

        // --- JWT keypair: shared by filter (via config) + test signer ---
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();
        String privB64 = Base64.getEncoder().encodeToString(kp.getPrivate().getEncoded()); // PKCS8
        String pubB64 = Base64.getEncoder().encodeToString(kp.getPublic().getEncoded());   // X.509

        Properties commonConfiguration = new Properties();
        commonConfiguration.setProperty("collaborationSecurityEnabled", "true");
        commonConfiguration.setProperty("releaseDateFormat", "yyyy-MM-dd");
        commonConfiguration.setProperty("htmlTitle", "Unit Test");
        commonConfiguration.setProperty("jwtPrivateKeyBase64", privB64);
        commonConfiguration.setProperty("jwtPublicKeyBase64", pubB64);
        commonConfiguration.setProperty("jwtIssuer", "wildbook");
        commonConfiguration.setProperty("jwtAudience", "wildbook-scoped-api");
        commonConfiguration.setProperty("jwtContext", "context0");
        CommonConfiguration.initialize("context0", commonConfiguration);

        testJwtService = JwtService.fromBase64Keys(privB64, pubB64, "wildbook",
            "wildbook-scoped-api");

        System.out.println("PostgreSQL started at: " + postgres.getJdbcUrl());
        System.out.println("OpenSearch started at: " + getOpenSearchUrl());

        // --- embedded Jetty + Shiro (shiro.ini wires tokenAuthSearch onto /api/v3/search/**) ---
        Server server = new Server(new InetSocketAddress("localhost", 0));
        ServletContextHandler ctx = new ServletContextHandler(ServletContextHandler.SESSIONS);
        ctx.setResourceBase("/tmp");

        IniShiroFilter shiroFilter = new IniShiroFilter();
        shiroFilter.setConfigPath("classpath:shiro.ini");
        ctx.addFilter(new FilterHolder(shiroFilter), "/*", EnumSet.of(DispatcherType.REQUEST));

        ctx.setContextPath("/");
        ctx.addServlet(new ServletHolder(new SearchApi()), "/api/v3/search/*");
        server.setHandler(ctx);
        server.start();

        int port = ((ServerConnector) server.getConnectors()[0]).getLocalPort();
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();

        OpenSearch.initializeClient(new HttpHost(opensearch.getHost(),
            opensearch.getMappedPort(9200), "http"));

        // seed users in Postgres + encounter/annotation/individual docs in OpenSearch
        seedUsers();
        seedDocs();

        System.out.println("=== Setup Complete ===\n");
    }

    @AfterAll static void tearDown() {
        System.out.println("=== Tearing Down SearchTokenScopeChildIndexTest ===");
    }

    // =========================================================================
    // Seeding
    // =========================================================================

    private static void seedUsers() {
        Properties properties = new Properties();
        properties.setProperty("datanucleus.ConnectionUserName", postgres.getUsername());
        properties.setProperty("datanucleus.ConnectionPassword", postgres.getPassword());
        properties.setProperty("datanucleus.ConnectionDriverName", postgres.getDriverClassName());
        properties.setProperty("datanucleus.ConnectionURL", postgres.getJdbcUrl());
        properties.setProperty("datanucleus.schema.autoCreateTables", "true");

        Shepherd myShepherd = new Shepherd("context0", properties);
        myShepherd.setAction("SearchTokenScopeChildIndexTest.seedUsers");
        try {
            myShepherd.beginDBTransaction();

            User alice = newUser("alice");
            myShepherd.getPM().makePersistent(alice);

            User adminUser = newUser("adminUser");
            myShepherd.getPM().makePersistent(adminUser);
            Role adminRole = new Role("adminUser", "admin");
            adminRole.setContext("context0");
            myShepherd.getPM().makePersistent(adminRole);

            User bob = newUser("bob");
            myShepherd.getPM().makePersistent(bob);

            myShepherd.commitDBTransaction();

            aliceUuid = alice.getId();
            adminUuid = adminUser.getId();
            bobUuid = bob.getId();
            System.out.println("Seeded users: alice=" + aliceUuid + " admin=" + adminUuid
                + " bob=" + bobUuid);
        } catch (Exception e) {
            myShepherd.rollbackDBTransaction();
            throw new RuntimeException("Failed to seed users", e);
        } finally {
            myShepherd.closeDBTransaction();
        }
    }

    private static User newUser(String username) {
        String salt = ServletUtilities.getSalt().toHex();
        String hashed = ServletUtilities.hashAndSaltPassword("password123", salt);
        return new User(username, hashed, salt);
    }

    /**
     * Create the encounter, annotation and individual indices with their REAL production mappings
     * (so submitterUserId/submitterUserIds/viewUsers are keyword fields and the ACL term-queries
     * match), then index docs carrying exactly the denormalized ACL fields the filter reads plus a
     * few identity/aggregate fields. Refreshed before any search runs.
     */
    private static void seedDocs() throws Exception {
        OpenSearch os = new OpenSearch();
        os.ensureIndex("encounter", new org.ecocean.Encounter().opensearchMapping());
        os.ensureIndex("annotation", new org.ecocean.Annotation().opensearchMapping());
        os.ensureIndex("individual", new org.ecocean.MarkedIndividual().opensearchMapping());

        // --- encounters (single submitterUserId) ---
        // alice sees encA (submitter) + encB (viewUsers grant); NOT encC.
        putDoc(os, "encounter", "encA", encDoc("encA", aliceUuid, false, new String[] {}));
        putDoc(os, "encounter", "encB", encDoc("encB", bobUuid, false, new String[] { aliceUuid }));
        putDoc(os, "encounter", "encC", encDoc("encC", bobUuid, false, new String[] {}));

        // --- annotations (denormalized parent ACL: union set submitterUserIds) ---
        // alice sees annA (her parent encA) + annB (parent encB grants her via viewUsers); NOT annC.
        JSONObject annA = childAclDoc("annA", false, new String[] { aliceUuid }, new String[] {});
        annA.put("encounterId", "encA");
        annA.put("viewpoint", "left");
        putDoc(os, "annotation", "annA", annA);

        JSONObject annB = childAclDoc("annB", false, new String[] { bobUuid },
            new String[] { aliceUuid });
        annB.put("encounterId", "encB");
        putDoc(os, "annotation", "annB", annB);

        JSONObject annC = childAclDoc("annC", false, new String[] { bobUuid }, new String[] {});
        annC.put("encounterId", "encC");
        putDoc(os, "annotation", "annC", annC);

        // --- individuals (denormalized ACL union over member encounters) ---
        // indShared: alice satisfies via submitterUserIds + viewUsers. Carries aggregates that the
        // token allowlist must strip.
        JSONObject indShared = childAclDoc("indShared", false,
            new String[] { aliceUuid, bobUuid }, new String[] { aliceUuid });
        indShared.put("displayName", "Fluke");
        indShared.put("sex", "female");
        indShared.put("taxonomy", "Orcinus orca");
        indShared.put("numberEncounters", 3);
        indShared.put("users", new JSONArray().put(aliceUuid).put(bobUuid));
        indShared.put("encounterIds",
            new JSONArray().put("encA").put("encB").put("encC"));
        putDoc(os, "individual", "indShared", indShared);

        // indBobOnly: alice satisfies none of the ACL shoulds -> must not see it.
        JSONObject indBob = childAclDoc("indBobOnly", false, new String[] { bobUuid },
            new String[] {});
        indBob.put("displayName", "BobWhale");
        indBob.put("sex", "male");
        putDoc(os, "individual", "indBobOnly", indBob);

        os.getRestResponse(new org.opensearch.client.Request("POST", "/encounter/_refresh"));
        os.getRestResponse(new org.opensearch.client.Request("POST", "/annotation/_refresh"));
        os.getRestResponse(new org.opensearch.client.Request("POST", "/individual/_refresh"));
        System.out.println("Seeded encounter/annotation/individual docs and refreshed indices");
    }

    /** Encounter ACL doc: single submitterUserId. */
    private static JSONObject encDoc(String id, String submitterUserId, boolean publiclyReadable,
        String[] viewUsers) {
        JSONObject doc = new JSONObject();
        doc.put("id", id);
        doc.put("publiclyReadable", publiclyReadable);
        doc.put("submitterUserId", submitterUserId);
        doc.put("viewUsers", strArray(viewUsers));
        return doc;
    }

    /** Child-index ACL doc: denormalized union set submitterUserIds + viewUsers. */
    private static JSONObject childAclDoc(String id, boolean publiclyReadable,
        String[] submitterUserIds, String[] viewUsers) {
        JSONObject doc = new JSONObject();
        doc.put("id", id);
        doc.put("publiclyReadable", publiclyReadable);
        doc.put("submitterUserIds", strArray(submitterUserIds));
        doc.put("viewUsers", strArray(viewUsers));
        return doc;
    }

    private static JSONArray strArray(String[] values) {
        JSONArray arr = new JSONArray();
        for (String v : values) arr.put(v);
        return arr;
    }

    private static void putDoc(OpenSearch os, String index, String id, JSONObject doc)
    throws Exception {
        org.opensearch.client.Request req = new org.opensearch.client.Request("PUT",
            "/" + index + "/_doc/" + id);
        req.setJsonEntity(doc.toString());
        os.getRestResponse(req);
    }

    private static String getOpenSearchUrl() {
        return "http://" + opensearch.getHost() + ":" + opensearch.getMappedPort(9200);
    }

    private static Set<String> hitIds(Response r) {
        JSONArray hits = new JSONObject(r.asString()).getJSONArray("hits");
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < hits.length(); i++) ids.add(hits.getJSONObject(i).optString("id"));
        return ids;
    }

    private static Response search(String userUuid, String index) {
        String token = testJwtService.sign(userUuid, "context0", 60_000L);
        return given()
            .header("Authorization", "Bearer " + token)
            .contentType(io.restassured.http.ContentType.JSON)
            .body("{\"query\":{\"match_all\":{}}}")
            .when().post("/api/v3/search/" + index)
            .then().log().ifValidationFails().extract().response();
    }

    // =========================================================================
    // Tests
    // =========================================================================

    @Test void tokenAnnotationSearch_scopedToVisibleEncounters() throws Exception {
        Response r = search(aliceUuid, "annotation");
        assertEquals(200, r.statusCode(), "alice token annotation search OK");
        Set<String> ids = hitIds(r);
        assertTrue(ids.contains("annA"), "alice sees annotation on her own encounter");
        assertTrue(ids.contains("annB"), "alice sees annotation on the viewUsers-granted encounter");
        assertFalse(ids.contains("annC"), "alice must NOT see annotation on bob's private encounter");
        assertEquals("2", r.header("X-Wildbook-Total-Hits"), "annotation total is query-time scoped");

        // ACL fields must never leak in any hit
        JSONArray hits = new JSONObject(r.asString()).getJSONArray("hits");
        for (int i = 0; i < hits.length(); i++) {
            JSONObject h = hits.getJSONObject(i);
            assertFalse(h.has("publiclyReadable"), "publiclyReadable scrubbed from annotation hit");
            assertFalse(h.has("submitterUserIds"), "submitterUserIds scrubbed from annotation hit");
            assertFalse(h.has("viewUsers"), "viewUsers scrubbed from annotation hit");
        }
    }

    @Test void tokenIndividualSearch_gatedAndAllowlisted() throws Exception {
        Response r = search(aliceUuid, "individual");
        assertEquals(200, r.statusCode(), "alice token individual search OK");
        Set<String> ids = hitIds(r);
        assertTrue(ids.contains("indShared"), "alice sees the shared individual (visible encounter)");
        assertFalse(ids.contains("indBobOnly"), "alice must NOT see bob-only individual");
        assertEquals("1", r.header("X-Wildbook-Total-Hits"), "individual total is query-time scoped");

        JSONArray hits = new JSONObject(r.asString()).getJSONArray("hits");
        JSONObject shared = hits.getJSONObject(0);
        // allowlisted identity fields present
        assertEquals("indShared", shared.optString("id"), "id kept");
        assertEquals("Fluke", shared.optString("displayName"), "displayName kept");
        assertEquals("female", shared.optString("sex"), "sex kept");
        assertEquals("Orcinus orca", shared.optString("taxonomy"), "taxonomy kept");
        // aggregates + ACL fields must be ABSENT
        assertFalse(shared.has("numberEncounters"), "numberEncounters aggregate dropped");
        assertFalse(shared.has("users"), "users aggregate dropped");
        assertFalse(shared.has("encounterIds"), "encounterIds aggregate dropped");
        assertFalse(shared.has("viewUsers"), "viewUsers ACL field dropped");
        assertFalse(shared.has("submitterUserIds"), "submitterUserIds ACL field dropped");
        assertFalse(shared.has("publiclyReadable"), "publiclyReadable ACL field dropped");
    }

    @Test void adminTokenIndividualSearch_unscoped() throws Exception {
        Response r = search(adminUuid, "individual");
        assertEquals(200, r.statusCode(), "admin token individual search OK");
        Set<String> ids = hitIds(r);
        assertTrue(ids.contains("indShared"), "admin sees shared individual");
        assertTrue(ids.contains("indBobOnly"), "admin sees bob-only individual (unscoped)");
        assertEquals("2", r.header("X-Wildbook-Total-Hits"), "admin token is unscoped");

        // ACL fields still scrubbed from every hit (universal scrub, even for admin)
        JSONArray hits = new JSONObject(r.asString()).getJSONArray("hits");
        for (int i = 0; i < hits.length(); i++) {
            JSONObject h = hits.getJSONObject(i);
            assertFalse(h.has("publiclyReadable"), "publiclyReadable scrubbed for admin too");
            assertFalse(h.has("submitterUserIds"), "submitterUserIds scrubbed for admin too");
            assertFalse(h.has("viewUsers"), "viewUsers scrubbed for admin too");
            assertFalse(h.has("editUsers"), "editUsers scrubbed for admin too");
        }
    }

    @Test void tokenOccurrenceSearch_403() throws Exception {
        Response r = search(aliceUuid, "occurrence");
        assertEquals(403, r.statusCode(), "occurrence is not in the token index allowlist");
    }
}
