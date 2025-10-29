package org.ecocean.security;

import javax.jdo.PersistenceManager;
import javax.jdo.PersistenceManagerFactory;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;

import org.ecocean.api.BaseObject;
import org.ecocean.CommonConfiguration;
import org.ecocean.Encounter;
import org.ecocean.MarkedIndividual;
import org.ecocean.Occurrence;
import org.ecocean.security.Collaboration;
import org.ecocean.servlet.ReCAPTCHA;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.shepherd.core.ShepherdPMF;
import org.ecocean.User;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.Assert.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// this class should probably be about 300 tests long, but we have to start somewhere...
// so we will start with the occurrence-related changes made for 10.9

class PermissionsTest {
    PersistenceManagerFactory mockPMF;
    HttpServletRequest mockRequest;
    // HttpServletResponse mockResponse;

    @BeforeEach void setUp()
    throws IOException {
        mockRequest = mock(HttpServletRequest.class);
        mockPMF = mock(PersistenceManagerFactory.class);
    }

    @Test void occurrencePublicTest()
    throws ServletException, IOException {
        User user = new User("test-user", null, null);
        User other = new User("someone-else", null, null);

        other.setSharing(false);
        Occurrence occ = new Occurrence();
        occ.setSubmitterID(other.getUsername());
        Encounter enc = new Encounter();
        occ.addEncounter(enc);
        Shepherd myShepherd = mock(Shepherd.class);
        when(myShepherd.getUser(other.getUsername())).thenReturn(other);
        when(myShepherd.getContext()).thenReturn("context0");

        // mock static of only collaborationBetweenUsers() to be none; but canUserViewOccurrence() is still real method (to test)
        try (MockedStatic<Collaboration> mockCollab = mockStatic(Collaboration.class,
                org.mockito.Answers.CALLS_REAL_METHODS)) {
            mockCollab.when(() -> Collaboration.collaborationBetweenUsers(any(String.class),
                any(String.class), any(String.class))).thenReturn(null);
            mockCollab.when(() -> Collaboration.securityEnabled(any(String.class))).thenReturn(
                true);
            // public owned should be view=true
            enc.setSubmitterID("public");
            assertTrue(Collaboration.canUserViewOccurrence(occ, user, myShepherd));
            assertTrue(Collaboration.canUserAccessOccurrence(occ, user, myShepherd));
            // not public owned should be view=false
            enc.setSubmitterID("not-public");
            assertFalse(Collaboration.canUserViewOccurrence(occ, user, myShepherd));
            // but access/edit is false
            assertFalse(Collaboration.canUserAccessOccurrence(occ, user, myShepherd));
            // but now let them collaborate
            Collaboration collab = new Collaboration();
            mockCollab.when(() -> Collaboration.collaborationBetweenUsers(any(String.class),
                any(String.class), any(String.class))).thenReturn(collab);
            // first with a non-edit collab (fails)
            assertFalse(Collaboration.canUserAccessOccurrence(occ, user, myShepherd));
            // now make it edit-approved
            collab.setState(Collaboration.STATE_EDIT_PRIV);
            assertTrue(Collaboration.canUserAccessOccurrence(occ, user, myShepherd));
        }
    }

    @Test void occurrenceOwnedTest()
    throws ServletException, IOException {
        User user = new User("test-user", null, null);
        User other = new User("someone-else", null, null);

        other.setSharing(false);
        Occurrence occ = new Occurrence();
        occ.setSubmitterID(other.getUsername());
        Encounter enc = new Encounter();
        occ.addEncounter(enc);
        Shepherd myShepherd = mock(Shepherd.class);
        when(myShepherd.getUser(other.getUsername())).thenReturn(other);
        when(myShepherd.getContext()).thenReturn("context0");

        // mock static of only collaborationBetweenUsers() to be none; but canUserViewOccurrence() is still real method (to test)
        try (MockedStatic<Collaboration> mockCollab = mockStatic(Collaboration.class,
                org.mockito.Answers.CALLS_REAL_METHODS)) {
            mockCollab.when(() -> Collaboration.collaborationBetweenUsers(any(String.class),
                any(String.class), any(String.class))).thenReturn(null);
            mockCollab.when(() -> Collaboration.securityEnabled(any(String.class))).thenReturn(
                true);
            enc.setSubmitterID("random-user");
            // two diff users, both fail
            assertFalse(Collaboration.canUserViewOccurrence(occ, user, myShepherd));
            assertFalse(Collaboration.canUserAccessOccurrence(occ, user, myShepherd));
            // set owned by user
            enc.setSubmitterID(user.getUsername());
            assertTrue(Collaboration.canUserViewOccurrence(occ, user, myShepherd));
            assertTrue(Collaboration.canUserAccessOccurrence(occ, user, myShepherd));
            // two diff users but collab
            enc.setSubmitterID(other.getUsername());
            Collaboration collab = new Collaboration();
            collab.setState(Collaboration.STATE_APPROVED);
            mockCollab.when(() -> Collaboration.collaborationBetweenUsers(any(String.class),
                any(String.class), any(String.class))).thenReturn(collab);
            assertTrue(Collaboration.canUserViewOccurrence(occ, user, myShepherd));
            assertTrue(Collaboration.canUserAccessOccurrence(occ, user, myShepherd));
        }
    }
}
