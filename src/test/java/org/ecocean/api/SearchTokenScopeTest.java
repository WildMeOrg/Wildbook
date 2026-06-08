package org.ecocean.api;

import io.restassured.RestAssured;
import io.restassured.response.Response;

import java.net.InetSocketAddress;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.EnumSet;
import java.util.HashMap;
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
 * End-to-end enforcement test for the token-scoped encounter search path.
 *
 * Boots an embedded Jetty + Shiro (with {@code WildbookTokenAuthenticationFilter} wired onto
 * {@code /api/v3/search/**} via the shared test {@code shiro.ini}), a real OpenSearch container,
 * and a real PostgreSQL container. Seeds three encounter docs directly into the OpenSearch
 * {@code encounter} index (deterministic: only the exact fields the ACL filter + sanitizeDoc read)
 * plus real {@link User}/{@link Role} rows in Postgres, then asserts:
 *
 * <ul>
 *   <li>a non-admin token user sees ONLY their permitted encounters AND scoped totals,</li>
 *   <li>an admin token is unscoped,</li>
 *   <li>a token to a non-encounter index is 403,</li>
 *   <li>a context-drift (cookie) token is 401,</li>
 *   <li>a token request mints no session (no JSESSIONID).</li>
 * </ul>
 *
 * The JWT keypair is generated in {@code @BeforeAll} and published to CommonConfiguration so the
 * production filter (which loads keys via {@code JwtService.fromConfig("context0")}) and the test
 * signer share the same keys.
 */
@Testcontainers public class SearchTokenScopeTest {

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
        System.out.println("=== Starting SearchTokenScopeTest Setup ===");

        // --- JWT keypair (mirror JwtServiceTest): shared by filter (via config) + test signer ---
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

        // seed users in Postgres + encounter docs in OpenSearch
        seedUsers();
        seedEncounterDocs();

        System.out.println("=== Setup Complete ===\n");
    }

    @AfterAll static void tearDown() {
        System.out.println("=== Tearing Down SearchTokenScopeTest ===");
    }

    // =========================================================================
    // Seeding
    // =========================================================================

    /**
     * Create real User rows (alice: normal; adminUser: admin role; bob: normal submitter).
     * isAdmin is computed from DB roles by SearchApi, and the token filter resolves the user
     * by UUID via Shepherd.getUserByUUID(sub) -> must return a User with a username.
     */
    private static void seedUsers() {
        Properties properties = new Properties();
        properties.setProperty("datanucleus.ConnectionUserName", postgres.getUsername());
        properties.setProperty("datanucleus.ConnectionPassword", postgres.getPassword());
        properties.setProperty("datanucleus.ConnectionDriverName", postgres.getDriverClassName());
        properties.setProperty("datanucleus.ConnectionURL", postgres.getJdbcUrl());
        properties.setProperty("datanucleus.schema.autoCreateTables", "true");

        Shepherd myShepherd = new Shepherd("context0", properties);
        myShepherd.setAction("SearchTokenScopeTest.seedUsers");
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
     * Index three minimal encounter docs carrying exactly the fields the ACL filter
     * (publiclyReadable / submitterUserId / viewUsers) and sanitizeDoc (id) read. The index is
     * created first with the real Encounter mapping so submitterUserId/viewUsers are keyword
     * fields and the ACL term-queries match. Refreshed before any search runs.
     */
    private static void seedEncounterDocs() throws Exception {
        OpenSearch os = new OpenSearch();
        // create the encounter index with the real mapping (keyword submitterUserId/viewUsers)
        os.ensureIndex("encounter", new org.ecocean.Encounter().opensearchMapping());

        // alice sees encA (she is the submitter)
        putDoc(os, "encA", docJson("encA", aliceUuid, false, new String[] {}));
        // alice sees encB (granted via viewUsers)
        putDoc(os, "encB", docJson("encB", bobUuid, false, new String[] { aliceUuid }));
        // alice must NOT see encC (bob's private encounter, no grant)
        putDoc(os, "encC", docJson("encC", bobUuid, false, new String[] {}));

        // refresh so docs are immediately searchable
        os.getRestResponse(new org.opensearch.client.Request("POST", "/encounter/_refresh"));
        System.out.println("Seeded 3 encounter docs (encA, encB, encC) and refreshed index");
    }

    private static JSONObject docJson(String id, String submitterUserId, boolean publiclyReadable,
        String[] viewUsers) {
        JSONObject doc = new JSONObject();
        doc.put("id", id);
        doc.put("publiclyReadable", publiclyReadable);
        doc.put("submitterUserId", submitterUserId);
        JSONArray vu = new JSONArray();
        for (String u : viewUsers) vu.put(u);
        doc.put("viewUsers", vu);
        return doc;
    }

    private static void putDoc(OpenSearch os, String id, JSONObject doc) throws Exception {
        org.opensearch.client.Request req = new org.opensearch.client.Request("PUT",
            "/encounter/_doc/" + id);
        req.setJsonEntity(doc.toString());
        os.getRestResponse(req);
    }

    private static String getOpenSearchUrl() {
        return "http://" + opensearch.getHost() + ":" + opensearch.getMappedPort(9200);
    }

    // =========================================================================
    // Tests
    // =========================================================================

    @Test void tokenSearch_scopesToPermittedEncountersAndTotals() throws Exception {
        String token = testJwtService.sign(aliceUuid, "context0", 60_000L);
        Response r = given()
            .header("Authorization", "Bearer " + token)
            .contentType(io.restassured.http.ContentType.JSON)
            .body("{\"query\":{\"match_all\":{}}}")
            .when().post("/api/v3/search/encounter")
            .then().log().ifValidationFails().statusCode(200).extract().response();
        JSONObject body = new JSONObject(r.asString());
        JSONArray hits = body.getJSONArray("hits");
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < hits.length(); i++) ids.add(hits.getJSONObject(i).optString("id"));
        assertTrue(ids.contains("encA"), "alice sees her own encounter");
        assertTrue(ids.contains("encB"), "alice sees the viewUsers encounter");
        assertFalse(ids.contains("encC"), "alice must NOT see bob's private encounter");
        assertEquals("2", r.header("X-Wildbook-Total-Hits"), "total reflects scoped set");
    }

    @Test void adminTokenSearch_seesAll() throws Exception {
        String token = testJwtService.sign(adminUuid, "context0", 60_000L);
        Response r = given()
            .header("Authorization", "Bearer " + token)
            .contentType(io.restassured.http.ContentType.JSON)
            .body("{\"query\":{\"match_all\":{}}}")
            .when().post("/api/v3/search/encounter")
            .then().log().ifValidationFails().statusCode(200).extract().response();
        assertEquals("3", r.header("X-Wildbook-Total-Hits"), "admin token is unscoped");
    }

    @Test void tokenSearch_nonAllowlistedIndex_403() throws Exception {
        // The token index allowlist is encounter + annotation + individual (see SearchApi). Any other
        // index must be rejected with 403. Use occurrence as the representative non-allowlisted index.
        // (Originally probed /individual, but the token allowlist was widened to include the child
        //  indices in a later commit; see SearchTokenScopeChildIndexTest for their scoped coverage.)
        String token = testJwtService.sign(aliceUuid, "context0", 60_000L);
        given().header("Authorization", "Bearer " + token)
            .contentType(io.restassured.http.ContentType.JSON)
            .body("{\"query\":{\"match_all\":{}}}")
            .when().post("/api/v3/search/occurrence")
            .then().log().ifValidationFails().statusCode(403);
    }

    @Test void tokenSearch_contextDriftViaCookie_401() throws Exception {
        String token = testJwtService.sign(aliceUuid, "context0", 60_000L);
        given().header("Authorization", "Bearer " + token)
            .cookie("wildbookContext", "context1")
            .contentType(io.restassured.http.ContentType.JSON)
            .body("{\"query\":{\"match_all\":{}}}")
            .when().post("/api/v3/search/encounter")
            .then().log().ifValidationFails().statusCode(401);
    }

    @Test void tokenSearch_mintsNoSession() throws Exception {
        String token = testJwtService.sign(aliceUuid, "context0", 60_000L);
        Response r = given()
            .header("Authorization", "Bearer " + token)
            .contentType(io.restassured.http.ContentType.JSON)
            .body("{\"query\":{\"match_all\":{}}}")
            .when().post("/api/v3/search/encounter")
            .then().log().ifValidationFails().statusCode(200).extract().response();
        String setCookie = r.header("Set-Cookie");
        assertFalse((setCookie != null) && setCookie.contains("JSESSIONID"),
            "token search must not mint a JSESSIONID");
        assertTrue(r.getCookie("JSESSIONID") == null, "no JSESSIONID cookie returned");
        // replaying whatever cookies came back (none auth-bearing) must NOT grant access
        given()
            .cookies(r.getCookies() == null ? new HashMap<String, String>() : r.getCookies())
            .contentType(io.restassured.http.ContentType.JSON)
            .body("{\"query\":{\"match_all\":{}}}")
            .when().post("/api/v3/search/encounter")
            .then().log().ifValidationFails().statusCode(401);
    }
}
