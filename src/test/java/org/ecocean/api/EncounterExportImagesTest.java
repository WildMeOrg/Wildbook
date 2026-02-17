package org.ecocean.api;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.RestAssured;

import java.awt.image.BufferedImage;
import java.io.*;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.*;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.imageio.ImageIO;
import javax.servlet.*;
import javax.servlet.http.*;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.http.HttpHost;
import org.apache.shiro.web.servlet.IniShiroFilter;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletHolder;
import org.ecocean.CommonConfiguration;
import org.ecocean.export.EncounterAnnotationExportFile;
import org.ecocean.export.EncounterImageExportFile;
import org.ecocean.media.*;
import org.ecocean.Occurrence;
import org.ecocean.OpenSearch;
import org.ecocean.servlet.export.EncounterSearchExportMetadataExcel;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.shepherd.core.Shepherd;
import org.jetbrains.annotations.NotNull;
import org.joda.time.Instant;
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
import java.util.*;

import static io.restassured.RestAssured.*;
import static org.apache.commons.lang3.StringUtils.isNumeric;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration tests for the EncounterExportImages API endpoint.
 * <p>
 * Uses Testcontainers for PostgreSQL and OpenSearch, WireMock for external image mocking,
 * and REST Assured for HTTP testing.
 */
@Testcontainers @TestMethodOrder(MethodOrderer.OrderAnnotation.class) public class
    EncounterExportImagesTest {
    public static final int TEST_IMAGE_WIDTH = 500;
    public static final int TEST_IMAGE_HEIGHT = 500;

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
        commonConfiguration.setProperty("htmlTitle", "Unit Test");
        CommonConfiguration.initialize("context0", commonConfiguration);

        System.out.println("PostgreSQL started at: " + postgres.getJdbcUrl());

        // Log OpenSearch connection details
        System.out.println("OpenSearch started at: " + getOpenSearchUrl());

        // start embedded jetty
        Server server = new Server(new InetSocketAddress("localhost", 0));
        ServletContextHandler ctx = new ServletContextHandler(ServletContextHandler.SESSIONS);

        // Set resource base so getRealPath() doesn't return null
        ctx.setResourceBase("/tmp");

        // Configure Shiro using IniShiroFilter with shiro.ini from test resources
        IniShiroFilter shiroFilter = new IniShiroFilter();
        shiroFilter.setConfigPath("classpath:shiro.ini");

        ctx.addFilter(new FilterHolder(shiroFilter), "/*", EnumSet.of(DispatcherType.REQUEST));

        ctx.setContextPath("/");
        // TODO: see if we can load web.xml
        ctx.addServlet(new ServletHolder(new UserHome()), "/api/v3/home");
        ctx.addServlet(new ServletHolder(new Login()), "/api/v3/login");
        ctx.addServlet(new ServletHolder(new SearchApi()), "/api/v3/search/*");
        ctx.addServlet(new ServletHolder(new EncounterExport()), "/api/v3/encounters/export");
        ctx.addServlet(new ServletHolder(new EncounterSearchExportMetadataExcel()),
            "/EncounterSearchExportMetadataExcel");
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

    private static @NotNull Feature createBbox(MediaAsset asset, int x, int y, int width,
        int height) {
        JSONObject featureParameters = new JSONObject();

        featureParameters.put("x", x);
        featureParameters.put("y", y);
        featureParameters.put("width", width);
        featureParameters.put("height", height);
        Feature f = new Feature("org.ecocean.boundingBox", featureParameters);
        asset.addFeature(f);
        return f;
    }

    @BeforeEach void setupMocks() {
    }

    // =========================================================================
    // Test Cases
    // =========================================================================

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
        searchResponse.getBody().prettyPrint();

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
            .body("{\"query\": {\"match\": {\"genus\": \"lynx\"}}}")
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

    @Test void testEncounterExportImageMetadata_HappyPath() {
        Path tempPath = FileSystems.getDefault().getPath("/tmp");
        Shepherd myShepherd = new Shepherd("context0");
        HttpServletRequest mockRequest = mock(HttpServletRequest.class);
        HttpSession mockSession = mock(HttpSession.class);
        ServletContext mockServletContext = mock(ServletContext.class);

        // Mock basic request properties
        when(mockRequest.getContextPath()).thenReturn("/wildbook");
        when(mockRequest.getScheme()).thenReturn("http");
        when(mockRequest.getServerName()).thenReturn("localhost");
        when(mockRequest.getServerPort()).thenReturn(8080);
        when(mockRequest.getSession()).thenReturn(mockSession);
        when(mockRequest.getSession(anyBoolean())).thenReturn(mockSession);

        when(mockSession.getAttribute("context")).thenReturn("context0");
        when(mockSession.getServletContext()).thenReturn(mockServletContext);
        when(mockServletContext.getRealPath("/")).thenReturn("/tmp");

        // Mock query parameters for encounter search (match all lynx)
        when(mockRequest.getParameter("genus")).thenReturn("lynx");
        when(mockRequest.getParameterNames()).thenReturn(Collections.enumeration(Arrays.asList(
            "genus")));

        when(mockRequest.getRequestURL()).thenReturn(new StringBuffer());

        // Mock remote user
        when(mockRequest.getRemoteUser()).thenReturn("test_researcher");

        EncounterAnnotationExportFile file = new EncounterAnnotationExportFile(mockRequest,
            myShepherd);
        Path outputFilePath = tempPath.resolve(file.getName());
        try {
            System.out.println("Wrote: " + outputFilePath);
            try (OutputStream os = Files.newOutputStream(outputFilePath)) {
                file.writeToStream(os);
            }

            // validate file contents against expected CSV
            File csvFile = outputFilePath.toFile();
            assertTrue(csvFile.exists(), "CSV file should exist");
            assertTrue(csvFile.length() > 0, "CSV file should not be empty");

            // Load expected CSV data
            File expectedCsvFile = new File("src/test/resources/expected_encounter_export.csv");
            List<String[]> expectedRows = loadCsv(expectedCsvFile);

            // Load actual CSV data
            List<String[]> actualRows = loadCsv(csvFile);

            // Compare CSV files
            assertCsvFilesEqual(actualRows, expectedRows);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            myShepherd.closeDBTransaction();
        }
    }

    private static @NotNull List<String[]> loadCsv(File expectedCsvFile)
    throws IOException {
        List<String[]> expectedRows = new ArrayList<>();

        try (Reader reader = new FileReader(expectedCsvFile);
        CSVParser parser = CSVFormat.EXCEL.parse(reader)) {
            boolean firstRow = true;
            for (CSVRecord record : parser) {
                String[] row = new String[record.size()];
                for (int i = 0; i < record.size(); i++) {
                    String value = record.get(i);
                    // Strip BOM from first cell of first row
                    if (firstRow && i == 0 && value.startsWith("\uFEFF")) {
                        value = value.substring(1);
                    }
                    row[i] = value;
                }
                expectedRows.add(row);
                firstRow = false;
            }
        }

        System.out.println("  Loaded " + expectedRows.size() + " rows from CSV: " +
            expectedCsvFile.toString());
        return expectedRows;
    }

    private void assertCsvFilesEqual(List<String[]> actualRows, List<String[]> expectedRows) {
        // Compare each row and cell
        for (int rowIndex = 0; rowIndex < expectedRows.size(); rowIndex++) {
            String[] actualRow = actualRows.get(rowIndex);
            String[] expectedRow = expectedRows.get(rowIndex);

            assertNotNull(actualRow, "Row " + rowIndex + " should not be null");
            // Compare each cell
            for (int cellIndex = 0; cellIndex < Math.min(expectedRow.length, actualRow.length);
                cellIndex++) {
                String expectedValue = expectedRow[cellIndex];
                String actualValue = actualRow[cellIndex];
                // Skip comparison for dynamic fields like Occurrence.occurrenceID and Encounter.sourceUrl
                if (rowIndex == 0) {
                    // Header row - exact match required
                    assertEquals(expectedValue, actualValue,
                        "Header cell [" + rowIndex + "," + cellIndex + "] mismatch");
                } else {
                    // Data rows - skip UUID columns (column 0 and 1)
                    if (cellIndex == 0 || cellIndex == 1) {
                        assertNotNull(actualValue,
                            "Cell [" + rowIndex + "," + cellIndex + "] should not be null");
                    } else {
                        // Compare other cells with tolerance for numeric precision
                        if (expectedValue.isEmpty() &&
                            (actualValue == null || actualValue.isEmpty())) {
                            // Both empty - OK
                            continue;
                        } else if (isNumeric(expectedValue) && isNumeric(actualValue)) {
                            // Compare numbers with tolerance
                            double expected = Double.parseDouble(expectedValue);
                            double actual = Double.parseDouble(actualValue);
                            assertEquals(expected, actual, 0.0001,
                                "Cell [" + rowIndex + "," + cellIndex + "] numeric value mismatch");
                        } else {
                            // String comparison
                            assertEquals(expectedValue, actualValue,
                                "Cell [" + rowIndex + "," + cellIndex + "] mismatch");
                        }
                    }
                }
            }
            // Compare cell count
            assertEquals(expectedRow.length, actualRow.length,
                "Row " + rowIndex + " should have " + expectedRow.length + " cells");
        }
        // Compare row count
        assertEquals(expectedRows.size(), actualRows.size(),
            "CSV should have same number of rows as expected");

        System.out.println("CSV metadata validation passed - all cells match expected CSV");
    }

    /**
     * Test the happy path for encounter export with images.
     * Verifies that a valid export request returns a properly structured ZIP file
     * containing cropped images organized by Individual ID and metadata.
     */
    @Test @Order(3) void testEncounterExportImages_HappyPath()
    throws Exception {
        System.out.println("\n--- Test: Encounter Export Images - Happy Path ---");

        String authenticationCookie = authenticateTestUser();

        // Prepare request body with search criteria and export options
        String requestBody =
            "{  \"query\": {\"match\": {\"genus\": \"lynx\"}}},\"exportOptions\": {  \"unidentifiedEncounters\": false,  \"numAnnotationsPerId\": \"all\",  \"includeMetadata\": true}";

        System.out.println("Sending export request...");

        // Make the export request
        Response response = given()
                .cookie("JSESSIONID", authenticationCookie)
                .contentType(ContentType.JSON)
                .body(requestBody)
                .queryParam("includeMetadata", "true")
                .when()
                .post("/api/v3/encounters/export")
                .then()
                .statusCode(200)
                .contentType("application/zip")
                .header("Content-Disposition", containsString("attachment"))
                .header("Content-Disposition", containsString("encounter_export_"))
                .header("Content-Disposition", containsString(".zip"))
                .log().ifValidationFails()
                .extract()
                .response();

        System.out.println("Export request successful, verifying ZIP structure...");

        // Extract the ZIP file bytes
        byte[] zipBytes = response.asByteArray();
        assertTrue(zipBytes.length > 0, "ZIP file should not be empty");

        // Parse the ZIP file and verify its structure
        Set<String> zipEntries = new HashSet<>();
        byte[] metadataCsvBytes = null;
        byte[] hiddenDataCsvBytes = null;
        int croppedImageCount = 0;

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                zipEntries.add(entry.getName());
                System.out.println("  Found ZIP entry: " + entry.getName());
                // Extract metadata.csv for validation
                if (entry.getName().equals("metadata.csv")) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        baos.write(buffer, 0, len);
                    }
                    metadataCsvBytes = baos.toByteArray();
                }
                // Extract hidden_data.csv for validation
                if (entry.getName().equals("hidden_data.csv")) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        baos.write(buffer, 0, len);
                    }
                    hiddenDataCsvBytes = baos.toByteArray();
                }
                // Validate cropped image dimensions
                if (entry.getName().startsWith("images/") && entry.getName().endsWith(".jpg")) {
                    // Read image and verify dimensions
                    BufferedImage image = ImageIO.read(zis);
                    assertNotNull(image, "Image should be readable: " + entry.getName());

                    // All test annotations have 500x500 bounding boxes
                    assertEquals(TEST_IMAGE_WIDTH, image.getWidth(),
                        "Cropped image width should be 500px: " + entry.getName());
                    assertEquals(TEST_IMAGE_HEIGHT, image.getHeight(),
                        "Cropped image height should be 500px: " + entry.getName());

                    croppedImageCount++;
                    System.out.println("  Validated cropped image dimensions (500x500): " +
                        entry.getName());
                }
                zis.closeEntry();
            }
        }

        // Verify expected structure
        System.out.println("Verifying ZIP structure...");

        // Should contain metadata file (if includeMetadata: true)
        assertTrue(zipEntries.stream().anyMatch(e -> e.equals("metadata.csv")),
            "ZIP should contain metadata.csv");

        assertTrue(zipEntries.stream().anyMatch(e -> e.equals("hidden_data.csv")),
            "ZIP should contain hidden_data.csv");

        // Should contain images directory
        assertTrue(zipEntries.stream().anyMatch(e -> e.startsWith("images/")),
            "ZIP should contain images/ directory");

        // Should contain Individual_1 directory (from test data)
        assertTrue(zipEntries.stream().anyMatch(e -> e.contains("Individual_1/")),
            "ZIP should contain Individual_1/ subdirectory");

        // Should contain Individual_2 directory (from test data)
        assertTrue(zipEntries.stream().anyMatch(e -> e.contains("Individual_2/")),
            "ZIP should contain Individual_2/ subdirectory");

        // Should NOT contain Unidentified_annotations (since unidentifiedEncounters: false)
        assertFalse(zipEntries.stream().anyMatch(e -> e.contains(
            EncounterImageExportFile.UNIDENTIFIED_INDIVIDUAL)),
            "ZIP should NOT contain unidentified annotations (unidentifiedEncounters: false)");

        // Should contain actual image files with proper naming convention
        assertTrue(zipEntries.stream().anyMatch(e -> e.matches(
            "images/Individual_[12]/.+\\.(jpg|jpeg)")),
            "ZIP should contain cropped image files with proper naming");

        // Count image files for Individual_1 (should have 2 annotations based on test data)
        long individual1Images = zipEntries.stream()
                .filter(e -> e.startsWith("images/Individual_1/") && e.endsWith(".jpg"))
                .count();
        assertTrue(individual1Images >= 1, "Individual_1 should have at least 1 cropped image");

        // Count image files for Individual_2 (should have 1 annotation based on test data)
        long individual2Images = zipEntries.stream()
                .filter(e -> e.startsWith("images/Individual_2/") && e.endsWith(".jpg"))
                .count();
        assertTrue(individual2Images >= 1, "Individual_2 should have at least 1 cropped image");

        // Verify that we validated at least some cropped images
        assertTrue(croppedImageCount >= 3,
            "Should have validated at least 3 cropped images (one per annotation)");

        System.out.println("ZIP structure verification passed");
        System.out.println("  Total ZIP entries: " + zipEntries.size());
        System.out.println("  Individual_1 images: " + individual1Images);
        System.out.println("  Individual_2 images: " + individual2Images);
        System.out.println("  Validated cropped images: " + croppedImageCount);

        // Validate CSV metadata file content
        System.out.println("\nVerifying CSV metadata file content...");
        assertNotNull(metadataCsvBytes, "metadata.csv should have been extracted from ZIP");
        assertTrue(metadataCsvBytes.length > 0, "metadata.csv should not be empty");

        // Load expected CSV data
        File expectedCsvFile = new File("src/test/resources/expected_encounter_export.csv");
        List<String[]> expectedRows = loadCsv(expectedCsvFile);

        // Parse actual CSV from bytes
        List<String[]> actualRows;
        try (Reader reader = new InputStreamReader(new ByteArrayInputStream(metadataCsvBytes));
        CSVParser parser = CSVFormat.EXCEL.parse(reader)) {
            actualRows = new ArrayList<>();
            boolean firstRow = true;
            for (CSVRecord record : parser) {
                String[] row = new String[record.size()];
                for (int i = 0; i < record.size(); i++) {
                    String value = record.get(i);
                    // Strip BOM from first cell of first row
                    if (firstRow && i == 0 && value.startsWith("\uFEFF")) {
                        value = value.substring(1);
                    }
                    row[i] = value;
                }
                actualRows.add(row);
                firstRow = false;
            }
        }

        assertCsvFilesEqual(actualRows, expectedRows);

        System.out.println("\nHappy path test completed successfully");
    }

    /**
     * Test the EncounterSearchExportMetadataExcel servlet.
     * Verifies that the CSV export has proper row separators (not one long row).
     */
    @Test @Order(4) void testEncounterSearchExportMetadataExcel_CsvFormat()
    throws Exception {
        System.out.println("\n--- Test: EncounterSearchExportMetadataExcel CSV Format ---");

        // Make the export request with genus filter
        Response response = given()
                .cookie("JSESSIONID", authenticationCookie)
                .queryParam("genus", "lynx")
                .when()
                .get("/EncounterSearchExportMetadataExcel")
                .then()
                .statusCode(200)
                .contentType("text/csv")
                .log().ifValidationFails()
                .extract()
                .response();

        System.out.println("Export request successful, verifying CSV format...");

        // Get the CSV content as string
        String csvContent = response.getBody().asString();
        assertNotNull(csvContent, "CSV content should not be null");
        assertFalse(csvContent.isEmpty(), "CSV content should not be empty");

        try (FileOutputStream output = new FileOutputStream("/tmp/actual_search_export.csv");
        OutputStreamWriter streamWriter = new OutputStreamWriter(output)) {
            streamWriter.write(csvContent);
            System.out.println("Wrote to: /tmp/actual_search_export.csv");
        }

        File expectedCsvFile = new File("src/test/resources/expected_search_export.csv");
        List<String[]> expectedData = loadCsv(expectedCsvFile);
        List<String[]> actualData = loadCsv(new File("/tmp/actual_search_export.csv"));

        assertCsvFilesEqual(actualData, expectedData);

        System.out.println("CSV format test passed:");
        System.out.println("  ✓ Multiple rows (not one long row)");
        System.out.println("  ✓ Header row with expected columns");
        System.out.println("  ✓ Consistent column count across all rows");
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
            FeatureType.initAll(myShepherd);

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
            Path tmpRoot = FileSystems.getDefault().getPath("/tmp");
            File testImage = Files.copy(assetsRoot.resolve("image-ok-0.jpg"),
                tmpRoot.resolve("test_image.jpg"), StandardCopyOption.REPLACE_EXISTING).toFile();
            AssetStore localStore = new LocalAssetStore("local", tmpRoot, "file://" + tmpRoot,
                false);
            MediaAsset asset1 = ((LocalAssetStore)localStore).create(testImage);
            MediaAsset asset2 = ((LocalAssetStore)localStore).create(testImage);
            MediaAsset asset3 = ((LocalAssetStore)localStore).create(testImage);

            asset1.setId(1);
            asset2.setId(2);
            asset3.setId(3);

            // Create keywords and add them to media assets
            org.ecocean.Keyword refKeyword = new org.ecocean.Keyword("Reference");
            org.ecocean.Keyword laboratoryKeyword = new org.ecocean.Keyword("Laboratory Study");
            org.ecocean.Keyword fieldKeyword = new org.ecocean.Keyword("Field Study");
            myShepherd.getPM().makePersistent(refKeyword);
            myShepherd.getPM().makePersistent(laboratoryKeyword);
            myShepherd.getPM().makePersistent(fieldKeyword);

            // asset1 has Reference and Scientific keywords
            asset1.addKeyword(refKeyword);
            asset1.addKeyword(laboratoryKeyword);

            // asset2 has only Field Study keyword
            asset2.addKeyword(fieldKeyword);

            // asset3 has no keywords (to test that case)

            // Create test encounters
            org.ecocean.Encounter enc1 = new org.ecocean.Encounter();
            enc1.setGenus("lynx");
            enc1.setSpecificEpithet("pardinus");
            enc1.setLifeStage("cub");
            enc1.setVerbatimLocality("iberia");
            enc1.setDecimalLatitude(37.15414445923345);
            enc1.setDecimalLongitude(-6.730740044168456);
            enc1.setDateInMilliseconds(Instant.parse("2024-06-01T00:00:00Z").getMillis());
            myShepherd.storeNewEncounter(enc1);

            enc1.opensearchIndexDeep();
            enc1.addMediaAsset(asset1);
            asset1.opensearchIndexDeep();

            org.ecocean.Encounter enc2 = new org.ecocean.Encounter();
            enc2.setGenus("lynx");
            enc2.setSpecificEpithet("pardinus");
            enc2.setLifeStage("adult");
            enc2.setVerbatimLocality("iberia");
            enc2.setDecimalLatitude(37.15414445923345);
            enc2.setDecimalLongitude(-6.730740044168456);
            enc2.setDateInMilliseconds(Instant.parse("2025-05-02T00:00:00Z").getMillis());
            myShepherd.storeNewEncounter(enc2);

            enc2.opensearchIndexDeep();
            enc2.addMediaAsset(asset2);
            asset2.opensearchIndexDeep();

            org.ecocean.Encounter enc3 = new org.ecocean.Encounter();
            enc3.setGenus("lynx");
            enc3.setSpecificEpithet("pardinus");
            enc3.setLifeStage("senior");
            enc3.setVerbatimLocality("iberia");
            enc3.setDecimalLatitude(37.15414445923345);
            enc3.setDecimalLongitude(-6.730740044168456);
            enc3.setDateInMilliseconds(Instant.parse("2025-05-03T00:00:00Z").getMillis());
            myShepherd.storeNewEncounter(enc3);

            enc3.opensearchIndexDeep();
            enc3.addMediaAsset(asset3);
            asset3.opensearchIndexDeep();

            // Create encounter without individual assignment (to test column alignment bug)
            MediaAsset asset4 = ((LocalAssetStore)localStore).create(testImage);
            asset4.setId(4);
            asset4.addKeyword(fieldKeyword);

            org.ecocean.Encounter enc4 = new org.ecocean.Encounter();
            enc4.setGenus("lynx");
            enc4.setSpecificEpithet("pardinus");
            enc4.setLifeStage("juvenile");
            enc4.setVerbatimLocality("iberia");
            enc4.setDecimalLatitude(37.15414445923345);
            enc4.setDecimalLongitude(-6.730740044168456);
            enc4.setDateInMilliseconds(Instant.parse("2025-05-04T00:00:00Z").getMillis());
            myShepherd.storeNewEncounter(enc4);
            // NOTE: enc4 is intentionally NOT assigned to any individual

            enc4.opensearchIndexDeep();
            enc4.addMediaAsset(asset4);
            asset4.opensearchIndexDeep();

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
            org.ecocean.Annotation ann1 = new org.ecocean.Annotation("l. pardinus",
                createBbox(asset1, 0, 0, TEST_IMAGE_WIDTH, TEST_IMAGE_HEIGHT));
            ann1.setId("0caa1e9b-be5e-4c9f-9e07-27ba98984997");
            ann1.setViewpoint("left");
            ann1.setMatchAgainst(true);
            enc1.addAnnotation(ann1);
            myShepherd.storeNewAnnotation(ann1);
            ann1.opensearchIndexDeep();

            org.ecocean.Annotation ann2 = new org.ecocean.Annotation("l. pardinus",
                createBbox(asset2, 500, 0, TEST_IMAGE_WIDTH, TEST_IMAGE_HEIGHT));
            ann2.setId("6c1e1088-49b2-4535-b90c-a6b793f9da49");
            ann2.setViewpoint("right");
            ann2.setMatchAgainst(true);
            enc2.addAnnotation(ann2);
            myShepherd.storeNewAnnotation(ann2);
            ann2.opensearchIndexDeep();

            org.ecocean.Annotation ann3 = new org.ecocean.Annotation("l. pardinus",
                createBbox(asset3, 0, 500, TEST_IMAGE_WIDTH, TEST_IMAGE_HEIGHT));
            ann3.setId("e0d19b0f-6d44-4969-a409-a00f2afe7b85");
            ann3.setViewpoint("front");
            ann3.setMatchAgainst(true);
            enc3.addAnnotation(ann3);
            myShepherd.storeNewAnnotation(ann3);
            ann3.opensearchIndexDeep();

            // Annotation for encounter without individual
            org.ecocean.Annotation ann4 = new org.ecocean.Annotation("l. pardinus",
                createBbox(asset4, 250, 250, TEST_IMAGE_WIDTH, TEST_IMAGE_HEIGHT));
            ann4.setId("b7d3c8f1-5e2a-4b9c-8d6e-1a2b3c4d5e6f");
            ann4.setViewpoint("back");
            ann4.setMatchAgainst(true);
            enc4.addAnnotation(ann4);
            myShepherd.storeNewAnnotation(ann4);
            ann4.opensearchIndexDeep();

            org.ecocean.Occurrence occ1 = new Occurrence("9cf5a4e7-4c81-466e-a788-8d976f869086");
            occ1.addEncounter(enc1);
            occ1.addAsset(asset1);
            asset1.setOccurrence(occ1);
            myShepherd.storeNewOccurrence(occ1);
            occ1.opensearchIndexDeep();

            org.ecocean.Occurrence occ2 = new Occurrence("c2dbf187-ac3b-450f-9886-aa4e49073844");
            occ2.addEncounter(enc2);
            occ2.addAsset(asset2);
            asset2.setOccurrence(occ2);
            myShepherd.storeNewOccurrence(occ2);
            occ2.opensearchIndexDeep();

            org.ecocean.Occurrence occ3 = new Occurrence("f981b20f-330a-4e52-bb0f-dd8d13f1e76a");
            occ3.addEncounter(enc3);
            occ3.addAsset(asset3);
            asset3.setOccurrence(occ3);
            myShepherd.storeNewOccurrence(occ3);
            occ3.opensearchIndexDeep();

            // Occurrence for encounter without individual
            org.ecocean.Occurrence occ4 = new Occurrence("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
            occ4.addEncounter(enc4);
            occ4.addAsset(asset4);
            asset4.setOccurrence(occ4);
            myShepherd.storeNewOccurrence(occ4);
            occ4.opensearchIndexDeep();

            myShepherd.getPM().makePersistent(asset1);
            myShepherd.getPM().makePersistent(asset2);
            myShepherd.getPM().makePersistent(asset3);
            myShepherd.getPM().makePersistent(asset4);

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
}
