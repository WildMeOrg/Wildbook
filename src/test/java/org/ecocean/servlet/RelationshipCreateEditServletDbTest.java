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
import org.ecocean.Encounter;
import org.ecocean.MarkedIndividual;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.shepherd.core.TestPMFUtil;
import org.ecocean.social.Relationship;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * RelationshipCreate's EDIT path (persistenceID present) had the same datastore-identity bug as
 * RelationshipDelete: the "<n>[OID]org.ecocean.social.Relationship" id the UI posts hit
 * Long.parseLong and threw, so every edit failed. Worse, rel was pre-initialized to
 * new Relationship(), so a null lookup would silently "edit" an unpersisted throwaway object.
 *
 * Contract: valid persistenceID -> the persisted row is updated, 200; stale/unknown
 * persistenceID -> 404 Failure, nothing changed, transaction rolled back.
 */
@Testcontainers
@ResourceLock("wildbook.context0.pmf") // all classes share the static context0 PMF cache
class RelationshipCreateEditServletDbTest {
    @Container
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("wildbook_test")
            .withUsername("wildbook")
            .withPassword("wildbook");

    static String oidString;
    static String ind1Id; // real (UUID) individual PKs -- the constructor's string arg is a NAME
    static String ind2Id;

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
            ind1Id = storeIndividual(sh, "rel-ind-1");
            ind2Id = storeIndividual(sh, "rel-ind-2");
            Relationship rel = new Relationship();
            rel.setType("familial");
            rel.setMarkedIndividualName1(ind1Id);
            rel.setMarkedIndividualName2(ind2Id);
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

    private static String storeIndividual(Shepherd sh, String name) {
        Encounter enc = new Encounter();

        // keep postStore from enqueueing IndexingManager work: its worker PMs hold open
        // transactions that make tearDown's closePMF fail and leak this class's PMF into
        // whatever Testcontainers class runs next
        enc.setSkipAutoIndexing(true);
        sh.storeNewEncounter(enc);
        MarkedIndividual indiv = new MarkedIndividual(name, enc);
        indiv.setSkipAutoIndexing(true);
        sh.storeNewMarkedIndividual(indiv);
        return indiv.getId();
    }

    private static class Response {
        int status = HttpServletResponse.SC_OK;
        String body;
    }

    private Response postEdit(String persistenceID, String newType) throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);

        when(request.getParameter("context")).thenReturn("context0");
        when(request.getParameter("persistenceID")).thenReturn(persistenceID);
        when(request.getParameter("type")).thenReturn(newType);
        when(request.getParameter("markedIndividualName1")).thenReturn(ind1Id);
        when(request.getParameter("markedIndividualName2")).thenReturn(ind2Id);

        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter out = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(out));
        Response result = new Response();
        doAnswer(inv -> {
            result.status = inv.getArgument(0);
            return null;
        }).when(response).setStatus(anyInt());

        new RelationshipCreate().doPost(request, response);
        result.body = out.toString();
        return result;
    }

    private String persistedType() {
        Shepherd sh = new Shepherd("context0");

        sh.setAction("RelationshipCreateEditServletDbTest");
        sh.beginDBTransaction();
        try {
            Relationship rel = sh.getRelationship(oidString);
            assertNotNull(rel, "the seeded relationship exists");
            return rel.getType();
        } finally {
            sh.rollbackDBTransaction();
            sh.closeDBTransaction();
        }
    }

    @Test void editWithValidPersistenceIdUpdatesThePersistedRow() throws Exception {
        Response r = postEdit(oidString, "matriline");

        assertEquals(HttpServletResponse.SC_OK, r.status, "body: " + r.body);
        assertEquals("matriline", persistedType(),
            "the edit reaches the persisted row, not a detached throwaway object");
    }

    @Test void editWithStalePersistenceIdReports404AndChangesNothing() throws Exception {
        String before = persistedType();
        Response r = postEdit("999999999[OID]org.ecocean.social.Relationship", "poisoned");

        assertEquals(HttpServletResponse.SC_NOT_FOUND, r.status, "body: " + r.body);
        assertTrue(r.body.contains("Failure"), "failure body routes to the JSP .fail(): "
            + r.body);
        assertEquals(before, persistedType(), "a failed edit must not alter the row");
    }

    @Test void editWithMalformedPersistenceIdReports400AndChangesNothing() throws Exception {
        String before = persistedType();
        Response r = postEdit("abc", "poisoned");

        assertEquals(HttpServletResponse.SC_BAD_REQUEST, r.status,
            "malformed input is a bad request, not a stale record; body: " + r.body);
        assertEquals(before, persistedType(), "a failed edit must not alter the row");
    }
}
