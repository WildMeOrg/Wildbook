package org.ecocean.servlet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Properties;
import javax.jdo.JDOHelper;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.ecocean.CommonConfiguration;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.shepherd.core.TestPMFUtil;
import org.ecocean.social.Relationship;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-end contract of RelationshipDelete against real DataNucleus + Postgres, pinning the
 * production regression where the persistenceID the UI posts
 * ("1011[OID]org.ecocean.social.Relationship") blew up in Long.parseLong at the bigint bind,
 * so every delete failed with a misleading "not enough information" message and HTTP 200 --
 * which the JSP's $.post success handler then treated as success.
 *
 * Contract: found -> delete + 200 Success; not found (including a second delete of the same
 * row) -> 404 Failure so the JSP's .fail() handler shows the error; blank/missing param -> 400.
 */
@Testcontainers
class RelationshipDeleteServletDbTest {
    @Container
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("wildbook_test")
            .withUsername("wildbook")
            .withPassword("wildbook");

    static String oidString;

    @BeforeAll
    static void setUp() throws Exception {
        CommonConfiguration.initialize("context0", new Properties());

        Properties props = new Properties();
        props.setProperty("datanucleus.ConnectionUserName", postgres.getUsername());
        props.setProperty("datanucleus.ConnectionPassword", postgres.getPassword());
        props.setProperty("datanucleus.ConnectionDriverName", postgres.getDriverClassName());
        props.setProperty("datanucleus.ConnectionURL", postgres.getJdbcUrl());
        props.setProperty("datanucleus.schema.autoCreateTables", "true");
        TestPMFUtil.closePMF("context0");

        Shepherd sh = new Shepherd("context0", props);
        try {
            sh.beginDBTransaction();
            Relationship rel = new Relationship();
            rel.setType("familial");
            rel.setMarkedIndividualName1("indiv-one");
            rel.setMarkedIndividualName2("indiv-two");
            sh.getPM().makePersistent(rel);
            oidString = JDOHelper.getObjectId(rel).toString();
            sh.commitDBTransaction();
        } catch (Exception e) {
            sh.rollbackDBTransaction();
            throw e;
        } finally {
            sh.closeDBTransaction();
        }
    }

    @AfterAll
    static void tearDown() {
        TestPMFUtil.closePMF("context0");
    }

    private static class Response {
        int status = HttpServletResponse.SC_OK; // servlet container default when never set
        String body;
    }

    private Response post(String persistenceID) throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);

        when(request.getParameter("context")).thenReturn("context0");
        when(request.getParameter("persistenceID")).thenReturn(persistenceID);
        when(request.getParameter("type")).thenReturn("familial");
        when(request.getParameter("markedIndividualName1")).thenReturn("indiv-one");
        when(request.getParameter("markedIndividualName2")).thenReturn("indiv-two");

        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter out = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(out));
        Response result = new Response();
        doAnswer(inv -> {
            result.status = inv.getArgument(0);
            return null;
        }).when(response).setStatus(anyInt());

        new RelationshipDelete().doPost(request, response);
        result.body = out.toString();
        return result;
    }

    private Relationship find(String persistenceID) {
        Shepherd sh = new Shepherd("context0");

        sh.setAction("RelationshipDeleteServletDbTest");
        sh.beginDBTransaction();
        try {
            return sh.getRelationship(persistenceID);
        } finally {
            sh.rollbackDBTransaction();
            sh.closeDBTransaction();
        }
    }

    @Test void deleteSucceedsThenSecondDeleteReports404() throws Exception {
        Response first = post(oidString);

        assertEquals(HttpServletResponse.SC_OK, first.status,
            "deleting an existing relationship succeeds");
        assertTrue(first.body.contains("Success"), "success body for the JSP handler: "
            + first.body);
        assertNull(find(oidString), "the row is actually gone");

        Response second = post(oidString);
        assertEquals(HttpServletResponse.SC_NOT_FOUND, second.status,
            "a second delete of the same relationship (e.g. double-click race) reports not-found");
        assertTrue(second.body.contains("Failure"), "failure body routes to the JSP .fail(): "
            + second.body);
    }

    @Test void unknownRelationshipReports404NotMisleadingSuccess() throws Exception {
        Response r = post("999999999[OID]org.ecocean.social.Relationship");

        assertEquals(HttpServletResponse.SC_NOT_FOUND, r.status);
        assertTrue(r.body.contains("Failure"), "body: " + r.body);
        assertTrue(r.body.contains("not") && r.body.toLowerCase().contains("found"),
            "the message says the relationship was not found, not 'missing information': "
            + r.body);
    }

    @Test void blankPersistenceIdReports400() throws Exception {
        Response r = post("");

        assertEquals(HttpServletResponse.SC_BAD_REQUEST, r.status);
        assertTrue(r.body.contains("Failure"), "body: " + r.body);
    }
}
