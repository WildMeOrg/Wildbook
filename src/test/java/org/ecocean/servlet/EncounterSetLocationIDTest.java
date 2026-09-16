package org.ecocean.servlet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collection;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.ecocean.Encounter;
import org.ecocean.LocationID;
import org.ecocean.User;
import org.ecocean.security.Collaboration;
import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

/**
 * /EncounterSetLocationID must authorize against the encounter as persisted, before anything is
 * changed. In particular a caller must not be able to move an encounter into a location their
 * own location-based role covers in order to gain access to it.
 */
class EncounterSetLocationIDTest {
    private JSONObject previousTree;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private StringWriter body;
    private Encounter enc;
    private User bob;

    // Pakistan -> PAKISTAN - North ; Indonesia -> Flores Sea -> Komodo
    private static JSONObject tree() {
        return new JSONObject("{\"locationID\":["
            + "{\"name\":\"Pakistan\",\"id\":\"Pakistan\",\"locationID\":["
            + "  {\"name\":\"PAKISTAN - North\",\"id\":\"PAKISTAN - North\",\"locationID\":[]}]},"
            + "{\"name\":\"Indonesia\",\"id\":\"Indonesia\",\"locationID\":["
            + "  {\"name\":\"Flores Sea\",\"id\":\"Flores Sea\",\"locationID\":["
            + "    {\"name\":\"Komodo\",\"id\":\"Komodo\",\"locationID\":[]}]}]}]}");
    }

    @BeforeEach void setUp() throws Exception {
        previousTree = LocationID.getJSONMaps().get("default");
        LocationID.getJSONMaps().put("default", tree());
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body));
        when(request.getServletContext()).thenReturn(null);
        when(request.getContextPath()).thenReturn("");
        when(request.getRemoteUser()).thenReturn("bob");
        when(request.getParameter("number")).thenReturn("enc-1");
        enc = new Encounter();
        enc.setCatalogNumber("enc-1");
        enc.setSubmitterID("owner");
        enc.setLocationID("Pakistan");
        bob = new User("bob", null, null);
    }

    @AfterEach void tearDown() {
        if (previousTree == null) LocationID.getJSONMaps().remove("default");
        else LocationID.getJSONMaps().put("default", previousTree);
    }

    /** bob's only location role is heldRole; no collaborations exist. */
    @SuppressWarnings("unchecked")
    private MockedConstruction<Shepherd> shepherdWhereBobHolds(final String heldRole) {
        return mockConstruction(Shepherd.class, (mock, ctx) -> {
            when(mock.getContext()).thenReturn("context0");
            when(mock.getEncounter("enc-1")).thenReturn(enc);
            when(mock.getUser(any(HttpServletRequest.class))).thenReturn(bob);
            when(mock.doesUserHaveAnyRole(eq("bob"), any(Collection.class), eq("context0")))
                .thenAnswer(inv -> ((Collection<String>)inv.getArgument(1)).contains(heldRole));
        });
    }

    private static MockedStatic<Collaboration> noCollaborations() {
        MockedStatic<Collaboration> mc = mockStatic(Collaboration.class, Answers.CALLS_REAL_METHODS);
        mc.when(() -> Collaboration.collaborationBetweenUsers(anyString(), anyString(),
            anyString())).thenReturn(null);
        return mc;
    }

    @Test void callerCannotMoveAnEncounterIntoTheirOwnLocationRole() throws Exception {
        when(request.getParameter("code")).thenReturn("Indonesia"); // covered by bob's role
        try (MockedStatic<Collaboration> mc = noCollaborations();
            MockedConstruction<Shepherd> shepherds = shepherdWhereBobHolds("Indonesia")) {
            new EncounterSetLocationID().doPost(request, response);
            verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
            verify(shepherds.constructed().get(0), never()).commitDBTransaction();
        }
        assertEquals("Pakistan", enc.getLocationID(), "the persisted location must be untouched");
    }

    @Test void nonOwnerWithoutRoleOrCollaborationIsForbidden() throws Exception {
        when(request.getParameter("code")).thenReturn("Komodo");
        try (MockedStatic<Collaboration> mc = noCollaborations();
            MockedConstruction<Shepherd> shepherds = shepherdWhereBobHolds("nothing")) {
            new EncounterSetLocationID().doPost(request, response);
            verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
            verify(shepherds.constructed().get(0), never()).commitDBTransaction();
        }
        assertEquals("Pakistan", enc.getLocationID());
    }

    @Test void roleCoveringTheCurrentLocationMayChangeIt() throws Exception {
        when(request.getParameter("code")).thenReturn("Komodo");
        try (MockedStatic<Collaboration> mc = noCollaborations();
            MockedConstruction<Shepherd> shepherds = shepherdWhereBobHolds("Pakistan")) {
            new EncounterSetLocationID().doPost(request, response);
            verify(response).setStatus(HttpServletResponse.SC_OK);
            verify(shepherds.constructed().get(0)).commitDBTransaction();
        }
        assertEquals("Komodo", enc.getLocationID());
    }

    @Test void ownerMayChangeIt() throws Exception {
        enc.setSubmitterID("bob");
        when(request.getParameter("code")).thenReturn("Komodo");
        try (MockedStatic<Collaboration> mc = noCollaborations();
            MockedConstruction<Shepherd> shepherds = shepherdWhereBobHolds("nothing")) {
            new EncounterSetLocationID().doPost(request, response);
            verify(response).setStatus(HttpServletResponse.SC_OK);
        }
        assertEquals("Komodo", enc.getLocationID());
    }

    @Test @SuppressWarnings("unchecked")
    void aThrowingAuthorizationCheckStillReleasesTheShepherd() throws Exception {
        when(request.getParameter("code")).thenReturn("Komodo");
        try (MockedStatic<Collaboration> mc = noCollaborations();
            MockedConstruction<Shepherd> shepherds = mockConstruction(Shepherd.class,
                (mock, ctx) -> {
                    when(mock.getContext()).thenReturn("context0");
                    when(mock.getEncounter("enc-1")).thenReturn(enc);
                    when(mock.getUser(any(HttpServletRequest.class))).thenReturn(bob);
                    when(mock.doesUserHaveAnyRole(anyString(), any(Collection.class), anyString()))
                        .thenThrow(new javax.jdo.JDOException("datastore down"));
                })) {
            new EncounterSetLocationID().doPost(request, response);
            Shepherd sh = shepherds.constructed().get(0);
            verify(sh).closeDBTransaction();
            verify(sh, never()).commitDBTransaction();
            verify(response).setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
        assertEquals("Pakistan", enc.getLocationID());
    }

    @Test void unknownEncounterIsNotFound() throws Exception {
        when(request.getParameter("number")).thenReturn("missing");
        when(request.getParameter("code")).thenReturn("Komodo");
        try (MockedStatic<Collaboration> mc = noCollaborations();
            MockedConstruction<Shepherd> shepherds = shepherdWhereBobHolds("nothing")) {
            new EncounterSetLocationID().doPost(request, response);
            verify(response).setStatus(HttpServletResponse.SC_NOT_FOUND);
            verify(shepherds.constructed().get(0), never()).commitDBTransaction();
        }
    }
}
