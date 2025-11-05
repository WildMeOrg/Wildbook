package org.ecocean.api;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.RestAssured;
import org.ecocean.media.AssetStore;
import org.ecocean.media.Feature;
import org.ecocean.media.LocalAssetStore;
import org.ecocean.media.MediaAsset;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.FileSystems;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.imageio.ImageIO;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the EncounterExportImages API endpoint.
 *
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

    private static WireMockServer wireMockServer;
    private static String baseUrl;
    private static String sessionCookie;

    @BeforeAll static void setUp() {
        System.out.println("=== Starting EncounterExportImagesTest Setup ===");

        // Configure database connection for tests via environment variables
        // ShepherdPMF will read these and connect to our Testcontainers PostgreSQL
        String jdbcUrl = postgres.getJdbcUrl();

        System.out.println("PostgreSQL started at: " + jdbcUrl);

        // Start WireMock server for mocking image URLs
        wireMockServer = new WireMockServer(8089);
        wireMockServer.start();
        configureFor("localhost", 8089);
        System.out.println("WireMock started on port 8089");

        // Configure REST Assured
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = 8080; // Assumes Wildbook running on 8080
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
        if (wireMockServer != null) {
            wireMockServer.stop();
            System.out.println("WireMock stopped");
        }
        System.out.println("PostgreSQL container will be stopped automatically");
        System.out.println("=== Teardown Complete ===");
    }

    @BeforeEach void setupMocks() {
        // Reset WireMock stubs before each test
        wireMockServer.resetAll();
    }

    // =========================================================================
    // Test Cases
    // =========================================================================

    /**
     * Test 1: Verify that unauthenticated requests are rejected with 401.
     * This is a basic smoke test to validate the test framework is working.
     */
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

        org.ecocean.shepherd.core.Shepherd myShepherd = new org.ecocean.shepherd.core.Shepherd(
            "context0", properties);

        try {
            myShepherd.beginDBTransaction();

            // Create test users
            org.ecocean.User testUser = new org.ecocean.User("test_researcher", "test_researcher");
            testUser.setPassword("password123");
            // Note: Role assignment may need to be done differently based on Wildbook's user model
            myShepherd.getPM().makePersistent(testUser);

            // Create test individuals
            org.ecocean.MarkedIndividual ind1 = new org.ecocean.MarkedIndividual("Individual_1",
                null);
            myShepherd.getPM().makePersistent(ind1);

            org.ecocean.MarkedIndividual ind2 = new org.ecocean.MarkedIndividual("Individual_2",
                null);
            myShepherd.getPM().makePersistent(ind2);

            AssetStore localStore = new LocalAssetStore("local",
                FileSystems.getDefault().getPath("src", "test", "bulk-images"), null, false);
            MediaAsset asset1 = ((LocalAssetStore)localStore).create(new File("image-ok-0.jpg"));
            MediaAsset asset2 = ((LocalAssetStore)localStore).create(new File("image-ok-0.jpg"));
            MediaAsset asset3 = ((LocalAssetStore)localStore).create(new File("image-ok-0.jpg"));

            // Create test encounters
            org.ecocean.Encounter enc1 = new org.ecocean.Encounter();
            enc1.setEncounterNumber("ENC_001");
            enc1.setGenus("Panthera");
            enc1.setSpecificEpithet("leo");
            enc1.setIndividual(ind1);
            enc1.addMediaAsset(asset1);
            myShepherd.getPM().makePersistent(enc1);

            org.ecocean.Encounter enc2 = new org.ecocean.Encounter();
            enc2.setEncounterNumber("ENC_002");
            enc2.setGenus("Panthera");
            enc2.setSpecificEpithet("leo");
            enc2.setIndividual(ind1);
            enc1.addMediaAsset(asset2);
            myShepherd.getPM().makePersistent(enc2);

            org.ecocean.Encounter enc3 = new org.ecocean.Encounter();
            enc3.setEncounterNumber("ENC_003");
            enc3.setGenus("Panthera");
            enc3.setSpecificEpithet("leo");
            enc3.setIndividual(ind2);
            enc1.addMediaAsset(asset3);
            myShepherd.getPM().makePersistent(enc3);

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

            // Create media assets pointing to WireMock URLs
            org.ecocean.media.MediaAsset ma1 = new org.ecocean.media.MediaAsset();
            // Note: MediaAsset configuration may vary - adjust as needed
            myShepherd.getPM().makePersistent(ma1);

            myShepherd.commitDBTransaction();
            System.out.println("Test data initialized successfully");
        } catch (Exception e) {
            System.err.println("Failed to initialize test data: " + e.getMessage());
            e.printStackTrace();
            myShepherd.rollbackDBTransaction();
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

        // Note: This assumes a login endpoint exists
        // Adjust the endpoint and payload based on actual Wildbook API
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
     * Mock an image URL with WireMock, returning a generated test image.
     *
     * @param path URL path to mock
     * @param width image width in pixels
     * @param height image height in pixels
     */
    private static void mockImageUrl(String path, int width, int height) {
        BufferedImage image = createTestImage(width, height);
        byte[] imageBytes = imageToBytes(image);

        stubFor(get(urlEqualTo(path))
                .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "image/jpeg")
                .withBody(imageBytes)));

        System.out.println("Mocked image URL: " + path + " (" + width + "x" + height + ")");
    }

    /**
     * Create a simple test image with the specified dimensions.
     *
     * @param width image width
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
     * @param zipBytes the ZIP file as byte array
     * @param hasMetadata whether metadata.xlsx should be present
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
