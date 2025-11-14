package org.ecocean.api;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.RestAssured;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumSet;
import javax.servlet.DispatcherType;

import org.apache.http.HttpHost;
import org.apache.shiro.web.servlet.IniShiroFilter;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletHolder;
import org.ecocean.CommonConfiguration;
import org.ecocean.media.AssetStore;
import org.ecocean.media.Feature;
import org.ecocean.media.LocalAssetStore;
import org.ecocean.media.MediaAsset;
import org.ecocean.OpenSearch;
import org.ecocean.servlet.ServletUtilities;
import org.json.JSONObject;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;

import java.net.InetSocketAddress;
import java.nio.file.FileSystems;
import java.util.*;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the EncounterExportImages API endpoint.
 * <p>
 * Uses Testcontainers for PostgreSQL and OpenSearch, WireMock for external image mocking,
 * and REST Assured for HTTP testing.
 */
@Testcontainers @TestMethodOrder(MethodOrderer.OrderAnnotation.class) public class
    EncounterExportImagesTest {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
        "postgres:15-alpine")
            .withDatabaseName("wildbook_test")
            .withUsername("wildbook")
            .withPassword("wildbook");

    @Container static GenericContainer<?> opensearch = new GenericContainer<>(DockerImageName.parse(
        "opensearchproject/opensearch:2.15.0"))
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

    private static String baseUrl;
    private static String authenticationCookie;

    @BeforeAll static void setUp() {
        System.out.println("=== Starting EncounterExportImagesTest Setup ===");

        // initialize commonConfiguration
        Properties commonConfiguration = new Properties();
        commonConfiguration.setProperty("collaborationSecurityEnabled", "true");
        commonConfiguration.setProperty("releaseDateFormat", "yyyy-MM-dd");
        CommonConfiguration.initialize("context0", commonConfiguration);

        System.out.println("PostgreSQL started at: " + postgres.getJdbcUrl());

        // Log OpenSearch connection details
        System.out.println("OpenSearch started at: " + getOpenSearchUrl());

        // start embedded jetty
        Server server = new Server(new InetSocketAddress("localhost", 0));
        ServletContextHandler ctx = new ServletContextHandler(ServletContextHandler.SESSIONS);

        // Configure Shiro using IniShiroFilter with shiro.ini from test resources
        IniShiroFilter shiroFilter = new IniShiroFilter();
        shiroFilter.setConfigPath("classpath:shiro.ini");

        ctx.addFilter(new FilterHolder(shiroFilter), "/*", EnumSet.of(DispatcherType.REQUEST));

        ctx.setContextPath("/");
        // TODO: see if we can load web.xml
        ctx.addServlet(new ServletHolder(new UserHome()), "/api/v3/home");
        ctx.addServlet(new ServletHolder(new Login()), "/api/v3/login");
        ctx.addServlet(new ServletHolder(new SearchApi()), "/api/v3/search/*");
        server.setHandler(ctx);
        try {
            server.start();
        } catch (Exception e) {
            throw new RuntimeException("Error starting embedded Jetty server", e);
        }
        // Configure REST Assured
        int port = ((ServerConnector)server.getConnectors()[0]).getLocalPort();
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port; // Assumes Wildbook running on 8080
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
        baseUrl = RestAssured.baseURI + ":" + RestAssured.port;

        System.out.println("REST Assured configured for: " + baseUrl);

        OpenSearch.initializeClient(new HttpHost(opensearch.getHost(),
            opensearch.getMappedPort(9200), "http"));

        // disable auto indexing for the duration of the test, or leave it disabled if it is currently
        try {
            File file = new File("/tmp/skipAutoIndexing");
            if (!file.exists()) {
                file.createNewFile();
                file.deleteOnExit();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        // Initialize test data via Shepherd
        // DataNucleus will auto-create tables on first access
        initializeTestData();

        // Manually trigger OpenSearch indexing (don't wait for background task which has 2 min delay)
        OpenSearch.updateEncounterIndexes("context0");

        authenticationCookie = authenticateTestUser();

        System.out.println("=== Setup Complete ===\n");
    }

    @AfterAll static void tearDown() {
        System.out.println("\n=== Tearing Down Test Environment ===");
        System.out.println("PostgreSQL container will be stopped automatically");
        System.out.println("OpenSearch container will be stopped automatically");
        System.out.println("=== Teardown Complete ===");
    }

    @BeforeEach void setupMocks() {
    }

    // =========================================================================
    // Test Cases
    // =========================================================================

    /**
     * Test the search endpoint with various queries
     */
    @Test @Order(2) void testPostSearch_Ok()
    throws Exception {
        System.out.println("\n--- Test: Search API with OpenSearch ---");

        // Wait for OpenSearch to be ready and index to be created
        Thread.sleep(2000);

        // Test 1: Search without path should return 404
        System.out.println("Test 1: Search without index name");
        given()
            .cookie("JSESSIONID", authenticationCookie)
            .contentType(ContentType.JSON)
            .body("{\"query\": {\"match_all\": {}}}")
            .when()
            .post("/api/v3/search/")
            .then()
            .statusCode(404)
            .body("error", equalTo("unsupported"))
            .log().ifValidationFails();

        // Test 2: Search with invalid index name should return 404
        System.out.println("Test 2: Search with invalid index name");
        given()
            .cookie("JSESSIONID", authenticationCookie)
            .contentType(ContentType.JSON)
            .body("{\"query\": {\"match_all\": {}}}")
            .when()
            .post("/api/v3/search/invalid_index")
            .then()
            .statusCode(404)
            .body("error", equalTo("unknown index"))
            .log().ifValidationFails();

        // Test 3: Unauthenticated request should return 401
        System.out.println("Test 3: Unauthenticated search request");
        given()
            .contentType(ContentType.JSON)
            .body("{\"query\": {\"match_all\": {}}}")
            .when()
            .post("/api/v3/search/encounter")
            .then()
            .statusCode(401)
            .body("error", equalTo(401))
            .log().ifValidationFails();

        // Test 4: Valid encounter search with match_all query
        System.out.println("Test 4: Valid encounter search with match_all");
        Response searchResponse = given()
                .cookie("JSESSIONID", authenticationCookie)
                .contentType(ContentType.JSON)
                .body("{\"query\": {\"match_all\": {}}}")
                .when()
                .post("/api/v3/search/encounter")
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("hits", notNullValue())
                .body("searchQueryId", notNullValue())
                .body("query", notNullValue())
                .header("X-Wildbook-Total-Hits", notNullValue())
                .header("X-Wildbook-Search-Query-Id", notNullValue())
                .log().ifValidationFails()
                .extract()
                .response();
        String searchQueryId = searchResponse.jsonPath().getString("searchQueryId");
        System.out.println("Search query ID: " + searchQueryId);

        // Test 5: Retrieve search results using the searchQueryId (GET request)
        System.out.println("Test 5: Retrieve search using searchQueryId");
        given()
            .cookie("JSESSIONID", authenticationCookie)
            .when()
            .get("/api/v3/search/" + searchQueryId)
            .then()
            .statusCode(200)
            .body("success", equalTo(true))
            .body("hits", notNullValue())
            .body("searchQueryId", equalTo(searchQueryId))
            .log().ifValidationFails();

        // Test 6: Search with pagination parameters
        System.out.println("Test 6: Search with pagination");
        given()
            .cookie("JSESSIONID", authenticationCookie)
            .contentType(ContentType.JSON)
            .body("{\"query\": {\"match_all\": {}}}")
            .queryParam("from", 0)
            .queryParam("size", 2)
            .when()
            .post("/api/v3/search/encounter")
            .then()
            .statusCode(200)
            .body("success", equalTo(true))
            .body("hits", notNullValue())
            .log().ifValidationFails();

        // Test 7: Search with sorting
        System.out.println("Test 7: Search with sorting");
        given()
            .cookie("JSESSIONID", authenticationCookie)
            .contentType(ContentType.JSON)
            .body("{\"query\": {\"match_all\": {}}}")
            .queryParam("sort", "dateSubmitted")
            .queryParam("sortOrder", "asc")
            .when()
            .post("/api/v3/search/encounter")
            .then()
            .statusCode(200)
            .body("success", equalTo(true))
            .body("hits", notNullValue())
            .log().ifValidationFails();

        // Test 8: Search with specific query (matching genus)
        System.out.println("Test 8: Search with match query for genus");
        given()
            .cookie("JSESSIONID", authenticationCookie)
            .contentType(ContentType.JSON)
            .body("{\"query\": {\"match\": {\"genus\": \"Panthera\"}}}")
            .when()
            .post("/api/v3/search/encounter")
            .then()
            .statusCode(200)
            .body("success", equalTo(true))
            .body("hits", notNullValue())
            .log().ifValidationFails();

        // Test 9: Search for individual index
        System.out.println("Test 9: Search individual index");
        given()
            .cookie("JSESSIONID", authenticationCookie)
            .contentType(ContentType.JSON)
            .body("{\"query\": {\"match_all\": {}}}")
            .when()
            .post("/api/v3/search/individual")
            .then()
            .statusCode(200)
            .body("success", equalTo(true))
            .body("hits", notNullValue())
            .log().ifValidationFails();

        System.out.println("Search API test passed");
    }

    @Test void testGetUser_Ok() {
        given()
            .cookie("JSESSIONID", authenticationCookie)
            .when()
            .get("/api/v3/home")
            .then()
            .statusCode(200)
            .log().ifValidationFails();
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    /**
     * Get the OpenSearch base URL for the running container.
     *
     * @return OpenSearch base URL (e.g., "http://localhost:12345")
     */
    private static String getOpenSearchUrl() {
        return "http://" + opensearch.getHost() + ":" + opensearch.getMappedPort(9200);
    }

    /**
     * Initialize test data in the database using Shepherd.
     * DataNucleus will auto-create schema based on entity annotations.
     */
    private static void initializeTestData() {
        System.out.println("Initializing test data via Shepherd...");

        // Configure database connection for tests via environment variables
        // ShepherdPMF will read these and connect to our Testcontainers PostgreSQL
        Properties properties = new Properties();
        properties.setProperty("datanucleus.ConnectionUserName", postgres.getUsername());
        properties.setProperty("datanucleus.ConnectionPassword", postgres.getPassword());
        properties.setProperty("datanucleus.ConnectionDriverName", postgres.getDriverClassName());
        properties.setProperty("datanucleus.ConnectionURL", postgres.getJdbcUrl());
        properties.setProperty("datanucleus.schema.autoCreateTables", "true");

        org.ecocean.shepherd.core.Shepherd myShepherd = new org.ecocean.shepherd.core.Shepherd(
            "context0", properties);

        try {
            myShepherd.beginDBTransaction();

            // Create test user with properly hashed password
            String username = "test_researcher";
            String plainPassword = "password123";
            String salt = ServletUtilities.getSalt().toHex();
            String hashedPassword = ServletUtilities.hashAndSaltPassword(plainPassword, salt);

            org.ecocean.User testUser = new org.ecocean.User(username, hashedPassword, salt);
            myShepherd.getPM().makePersistent(testUser);

            // Assign researcher role to the test user
            org.ecocean.Role researcherRole = new org.ecocean.Role(username, "researcher");
            myShepherd.getPM().makePersistent(researcherRole);

            Path assetsRoot = FileSystems.getDefault().getPath("src", "test",
                "bulk-images").toAbsolutePath();
            AssetStore localStore = new LocalAssetStore("local", assetsRoot, null, false);
            MediaAsset asset1 = ((LocalAssetStore)localStore).create(assetsRoot.resolve(
                "image-ok-0.jpg").toFile());
            MediaAsset asset2 = ((LocalAssetStore)localStore).create(assetsRoot.resolve(
                "image-ok-0.jpg").toFile());
            MediaAsset asset3 = ((LocalAssetStore)localStore).create(assetsRoot.resolve(
                "image-ok-0.jpg").toFile());

            // Create test encounters
            org.ecocean.Encounter enc1 = new org.ecocean.Encounter();
            enc1.setGenus("Panthera");
            enc1.setSpecificEpithet("leo");
            myShepherd.storeNewEncounter(enc1);

            enc1.opensearchIndexDeep();
            asset1.opensearchIndexDeep();

            org.ecocean.Encounter enc2 = new org.ecocean.Encounter();
            enc2.setGenus("Panthera");
            enc2.setSpecificEpithet("leo");
            myShepherd.storeNewEncounter(enc2);

            enc2.opensearchIndexDeep();
            asset2.opensearchIndexDeep();

            org.ecocean.Encounter enc3 = new org.ecocean.Encounter();
            enc3.setGenus("Panthera");
            enc3.setSpecificEpithet("leo");
            myShepherd.storeNewEncounter(enc3);

            enc3.opensearchIndexDeep();
            asset3.opensearchIndexDeep();

            // Create test individuals
            org.ecocean.MarkedIndividual ind1 = new org.ecocean.MarkedIndividual("Individual_1",
                enc1);
            enc1.setIndividual(ind1);
            enc2.setIndividual(ind1);
            myShepherd.storeNewMarkedIndividual(ind1);

            org.ecocean.MarkedIndividual ind2 = new org.ecocean.MarkedIndividual("Individual_2",
                enc3);
            enc3.setIndividual(ind2);
            myShepherd.storeNewMarkedIndividual(ind2);

            ind1.opensearchIndexDeep();
            ind2.opensearchIndexDeep();

            // Create annotations with bounding boxes
            org.ecocean.Annotation ann1 = new org.ecocean.Annotation("fluke", asset1);
            ann1.setBbox(0, 0, 500, 500);
            ann1.setViewpoint("left");
            myShepherd.storeNewAnnotation(ann1);
            ann1.opensearchIndexDeep();

            org.ecocean.Annotation ann2 = new org.ecocean.Annotation("fluke", asset2);
            ann2.setBbox(500, 0, 500, 500);
            ann2.setViewpoint("right");
            myShepherd.storeNewAnnotation(ann2);
            ann2.opensearchIndexDeep();

            org.ecocean.Annotation ann3 = new org.ecocean.Annotation("fluke", asset3);
            ann3.setBbox(0, 500, 500, 500);
            ann3.setViewpoint("front");
            myShepherd.storeNewAnnotation(ann3);
            ann3.opensearchIndexDeep();

            myShepherd.commitDBTransaction();
            System.out.println("Test data initialized successfully");
        } catch (Exception e) {
            System.err.println("Failed to initialize test data: " + e.getMessage());
            myShepherd.rollbackDBTransaction();
            throw new RuntimeException("Error executing database initialization", e);
        } finally {
            myShepherd.closeDBTransaction();
        }
    }

    /**
     * Authenticate as the test researcher user and return session cookie.
     *
     * @return session cookie string (JSESSIONID value)
     */
    private static String authenticateTestUser() {
        System.out.println("Authenticating test user...");

        try {
            Response response = given()
                    .contentType(ContentType.JSON)
                    .body("{\"username\": \"test_researcher\", \"password\": \"password123\"}")
                    .when()
                    .post("/api/v3/login")
                    .then()
                    .statusCode(200)
                    .extract()
                    .response();
            String cookie = response.getCookie("JSESSIONID");
            System.out.println("Authenticated successfully, session: " + cookie);
            return cookie;
        } catch (Exception e) {
            System.err.println("Authentication failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * Database connection info for debugging.
     */
    @Test @Order(0) void testDatabaseConnection() {
        System.out.println("\n--- Test: Database Connection ---");
        System.out.println("JDBC URL: " + postgres.getJdbcUrl());
        System.out.println("Username: " + postgres.getUsername());
        System.out.println("Database: " + postgres.getDatabaseName());

        assertTrue(postgres.isRunning(), "PostgreSQL container should be running");
        System.out.println("Database connection test passed");
    }

    /**
     * OpenSearch connection info for debugging.
     */
    @Test @Order(0) void testOpenSearchConnection() {
        System.out.println("\n--- Test: OpenSearch Connection ---");
        System.out.println("OpenSearch URL: " + getOpenSearchUrl());
        System.out.println("OpenSearch Port 9200: " + opensearch.getMappedPort(9200));
        System.out.println("OpenSearch Port 9300: " + opensearch.getMappedPort(9300));

        assertTrue(opensearch.isRunning(), "OpenSearch container should be running");

        // Verify we can connect to OpenSearch cluster health endpoint
        given()
            .when()
            .get(getOpenSearchUrl() + "/_cluster/health")
            .then()
            .statusCode(200)
            .log().ifValidationFails();

        System.out.println("OpenSearch connection test passed");
    }

    private static Feature createBbox(int x, int y, int width, int height) {
        JSONObject params = new JSONObject();

        params.put("width", width);
        params.put("height", height);
        params.put("x", x);
        params.put("y", y);
        return new Feature("org.ecocean.boundingBox", params);
    }
}
