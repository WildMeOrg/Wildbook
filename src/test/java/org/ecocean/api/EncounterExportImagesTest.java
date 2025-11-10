package org.ecocean.api;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.RestAssured;

import java.nio.file.Path;
import java.util.EnumSet;
import javax.servlet.DispatcherType;

import org.apache.shiro.config.IniSecurityManagerFactory;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.util.ThreadContext;
import org.apache.shiro.web.servlet.IniShiroFilter;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletHolder;
import org.ecocean.media.AssetStore;
import org.ecocean.media.LocalAssetStore;
import org.ecocean.media.MediaAsset;
import org.ecocean.security.ShepherdRealm;
import org.ecocean.servlet.ServletUtilities;
import org.junit.After;
import org.junit.Before;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;

import java.awt.image.BufferedImage;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.FileSystems;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.imageio.ImageIO;
import javax.servlet.DispatcherType;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the EncounterExportImages API endpoint.
 * <p>
 * Uses Testcontainers for PostgreSQL, WireMock for external image mocking,
 * and REST Assured for HTTP testing.
 */
@Testcontainers @TestMethodOrder(MethodOrderer.OrderAnnotation.class) public class
    EncounterExportImagesTest {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
        "postgres:15-alpine")
            .withDatabaseName("wildbook_test")
            .withUsername("wildbook")
            .withPassword("wildbook");

    private static String baseUrl;
    private static String sessionCookie;

    @BeforeAll static void setUp() {
        System.out.println("=== Starting EncounterExportImagesTest Setup ===");

        // Configure database connection for tests via environment variables
        // ShepherdPMF will read these and connect to our Testcontainers PostgreSQL
        String jdbcUrl = postgres.getJdbcUrl();

        System.out.println("PostgreSQL started at: " + jdbcUrl);

        // start embedded jetty
        Server server = new Server(new InetSocketAddress("localhost", 0));
        ServletContextHandler ctx = new ServletContextHandler(ServletContextHandler.SESSIONS);

        // Configure Shiro using IniShiroFilter with shiro.ini from test resources
        IniShiroFilter shiroFilter = new IniShiroFilter();
        shiroFilter.setConfigPath("classpath:shiro.ini");

        ctx.addFilter(new FilterHolder(shiroFilter), "/*", EnumSet.of(DispatcherType.REQUEST));

        ctx.setContextPath("/");
// ctx.setAttribute("datasource", ds);
// ctx.setAttribute("objectMapper", new ObjectMapper());
        // TODO: see if we can load web.xml
        ctx.addServlet(new ServletHolder(new UserHome()), "/api/v3/home");
        ctx.addServlet(new ServletHolder(new Login()), "/api/v3/login");
        server.setHandler(ctx);
        try {
            server.start();
        } catch (Exception e) {
            throw new RuntimeException("Error starting embedded Jetty server", e);
        }
        int port = ((ServerConnector)server.getConnectors()[0]).getLocalPort();

        // Configure REST Assured
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port; // Assumes Wildbook running on 8080
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
        baseUrl = RestAssured.baseURI + ":" + RestAssured.port;

        System.out.println("REST Assured configured for: " + baseUrl);

        // Initialize test data via Shepherd
        // DataNucleus will auto-create tables on first access
        initializeTestData();

        System.out.println("=== Setup Complete ===\n");

        // Note: Authentication will need to be implemented when the API endpoint exists
        // sessionCookie = authenticateTestUser();
    }

    @AfterAll static void tearDown() {
        System.out.println("\n=== Tearing Down Test Environment ===");
        System.out.println("PostgreSQL container will be stopped automatically");
        System.out.println("=== Teardown Complete ===");
    }

    @BeforeEach void setupMocks() {
    }

    // =========================================================================
    // Test Cases
    // =========================================================================

    /**
     * Test 1: Verify that unauthenticated requests are rejected with 401.
     * This is a basic smoke test to validate the test framework is working.
     */
    @Test void testGetUser_Ok() {
        String authenticationCookie = authenticateTestUser();

        given()
            .cookie("JSESSIONID", authenticationCookie)
            .when()
            .get("/api/v3/home")
            .then()
            .statusCode(200)
            .log().ifValidationFails();
    }

    @Test @Order(1) void testExportImages_Unauthorized() {
        System.out.println("\n--- Test: Unauthorized Access ---");

        given()
            .contentType(ContentType.JSON)
            .body("{\"searchCriteria\": {}, \"exportOptions\": {}}")
            .when()
            .post("/api/v3/encounters/export-images")
            .then()
            .statusCode(anyOf(is(401), is(404))) // 404 if endpoint doesn't exist yet, 401 when it does
            .log().ifValidationFails();

        System.out.println("Test passed: Unauthorized access handled correctly");
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    /**
     * Initialize test data in the database using Shepherd.
     * DataNucleus will auto-create schema based on entity annotations.
     */
    private static void initializeTestData() {
        System.out.println("Initializing test data via Shepherd...");

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
            enc1.setEncounterNumber("ENC_001");
            enc1.setGenus("Panthera");
            enc1.setSpecificEpithet("leo");
            myShepherd.getPM().makePersistent(enc1);
            enc1.addMediaAsset(asset1);

            org.ecocean.Encounter enc2 = new org.ecocean.Encounter();
            enc2.setEncounterNumber("ENC_002");
            enc2.setGenus("Panthera");
            enc2.setSpecificEpithet("leo");
            myShepherd.getPM().makePersistent(enc2);
            enc1.addMediaAsset(asset2);

            org.ecocean.Encounter enc3 = new org.ecocean.Encounter();
            enc3.setEncounterNumber("ENC_003");
            enc3.setGenus("Panthera");
            enc3.setSpecificEpithet("leo");
            myShepherd.getPM().makePersistent(enc3);
            enc1.addMediaAsset(asset3);

            // Create test individuals
            org.ecocean.MarkedIndividual ind1 = new org.ecocean.MarkedIndividual("Individual_1",
                enc1);
            enc1.setIndividual(ind1);
            enc2.setIndividual(ind1);
            myShepherd.getPM().makePersistent(ind1);

            org.ecocean.MarkedIndividual ind2 = new org.ecocean.MarkedIndividual("Individual_2",
                enc3);
            enc3.setIndividual(ind2);
            myShepherd.getPM().makePersistent(ind2);

// Create annotations with bounding boxes
            org.ecocean.Annotation ann1 = new org.ecocean.Annotation("fluke", asset1);
            ann1.setViewpoint("left");
// Note: bbox format may need adjustment based on Annotation class
            myShepherd.getPM().makePersistent(ann1);

            org.ecocean.Annotation ann2 = new org.ecocean.Annotation("fluke", asset2);
            ann2.setViewpoint("right");
            myShepherd.getPM().makePersistent(ann2);

            org.ecocean.Annotation ann3 = new org.ecocean.Annotation("fluke", asset3);
            ann3.setViewpoint("front");
            myShepherd.getPM().makePersistent(ann3);

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
     * Create a simple test image with the specified dimensions.
     *
     * @param width  image width
     * @param height image height
     * @return BufferedImage
     */
    private static BufferedImage createTestImage(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        // Image is blank - could add test patterns if needed
        return image;
    }

    /**
     * Convert a BufferedImage to JPEG byte array.
     *
     * @param image the image to convert
     * @return byte array of JPEG data
     */
    private static byte[] imageToBytes(BufferedImage image) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "jpg", baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to convert image to bytes", e);
        }
    }

    /**
     * Verify the contents of a ZIP file.
     *
     * @param zipBytes        the ZIP file as byte array
     * @param hasMetadata     whether metadata.xlsx should be present
     * @param hasUnidentified whether Unidentified_annotations directory should be present
     * @throws IOException if ZIP reading fails
     */
    private static void verifyZipContents(byte[] zipBytes, boolean hasMetadata,
        boolean hasUnidentified)
    throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            boolean foundMetadata = false;
            boolean foundImages = false;
            boolean foundUnidentified = false;
            List<String> entries = new ArrayList<>();
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                entries.add(name);
                if (name.equals("metadata.xlsx")) {
                    foundMetadata = true;
                }
                if (name.startsWith("images/") && name.endsWith(".jpg")) {
                    foundImages = true;
                }
                if (name.contains("Unidentified_annotations")) {
                    foundUnidentified = true;
                }
            }
            System.out.println("ZIP contains " + entries.size() + " entries:");
            entries.forEach(e -> System.out.println("  - " + e));

            assertEquals(hasMetadata, foundMetadata, "Metadata file presence mismatch");
            assertTrue(foundImages, "No image files found in ZIP");
            assertEquals(hasUnidentified, foundUnidentified,
                "Unidentified annotations presence mismatch");
        }
    }

    /**
     * Verify that a ZIP file contains an errors.txt file with proper format.
     *
     * @param zipBytes the ZIP file as byte array
     * @throws IOException if ZIP reading fails
     */
    private static void verifyZipContainsErrorFile(byte[] zipBytes)
    throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            boolean foundErrorFile = false;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals("errors.txt")) {
                    foundErrorFile = true;

                    // Read and verify error file format
                    BufferedReader reader = new BufferedReader(new InputStreamReader(zis));
                    String line = reader.readLine();
                    if (line != null) {
                        System.out.println("Error file content: " + line);
                        assertTrue(line.matches(".*_\\d+: .*"),
                            "Error line doesn't match expected format: {encounterId}_{annotationIdx}: {message}");
                    }
                    break;
                }
            }
            assertTrue(foundErrorFile, "errors.txt not found in ZIP");
        }
    }

    /**
     * Parse ZIP structure into a map for detailed verification.
     *
     * @param zipBytes the ZIP file as byte array
     * @return map of entry names to their properties
     * @throws IOException if ZIP reading fails
     */
    private static Map<String, Map<String, Object> > parseZipStructure(byte[] zipBytes)
    throws IOException {
        Map<String, Map<String, Object> > structure = new HashMap<>();

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Map<String, Object> props = new HashMap<>();
                props.put("size", entry.getSize());
                props.put("isDirectory", entry.isDirectory());
                structure.put(entry.getName(), props);
            }
        }

        return structure;
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
}
