package org.ecocean.shepherd.entity;

import org.ecocean.*;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.shepherd.core.ShepherdPMF;

import org.ecocean.scheduled.ScheduledIndividualMerge;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import javax.jdo.*;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class IndividualTest {
    private MockedStatic<ShepherdPMF> mockedShepherdPMF;
    private PersistenceManagerFactory mockPMF;
    private PersistenceManager mockPM;
    private Transaction mockTransaction;
    private Query mockQuery;

    @BeforeEach public void setUp() {
        // Create mock PersistenceManager and stub critical nested methods
        mockTransaction = mock(Transaction.class, RETURNS_DEEP_STUBS);
        mockQuery = mock(Query.class, RETURNS_DEEP_STUBS);
        mockPM = mock(PersistenceManager.class, RETURNS_DEEP_STUBS);
        when(mockPM.currentTransaction()).thenReturn(mockTransaction);
        when(mockPM.newQuery(anyString())).thenReturn(mockQuery);

        // Create mock PersistenceManagerFactory and PM creation
        mockPMF = mock(PersistenceManagerFactory.class);
        when(mockPMF.getPersistenceManager()).thenReturn(mockPM);

        // Open the static mock for ShepherdPMF
        mockedShepherdPMF = mockStatic(ShepherdPMF.class);
        // Configure the behavior for static getPMF()
        mockedShepherdPMF.when(() -> ShepherdPMF.getPMF(anyString(), any()))
            .thenReturn(mockPMF);
    }

    @AfterEach public void tearDown() {
        // Ensure that the static mock is closed after each test
        mockedShepherdPMF.close();
    }

    // creation
    @Test public void testStoreNewMarkedIndividual() {
        when(mockTransaction.isActive()).thenReturn(false);
        Shepherd testShepherd = spy(new Shepherd("testContext"));
        // should a null MarkedIndividual "work" here?
        MarkedIndividual markedIndividual = null;
        boolean returnValue = testShepherd.storeNewMarkedIndividual(markedIndividual);

        verify(testShepherd, times(1)).beginDBTransaction();
        verify(mockPM, times(1)).makePersistent(markedIndividual);
        verify(testShepherd, times(1)).commitDBTransaction();
        assertTrue(returnValue);
    }

    // behavior should be the same as inactive trans
    @Test public void testStoreNewMarkedIndividualWithActiveTransaction() {
        when(mockTransaction.isActive()).thenReturn(true);
        Shepherd testShepherd = spy(new Shepherd("testContext"));
        // should a null MarkedIndividual "work" here?
        MarkedIndividual markedIndividual = null;
        boolean returnValue = testShepherd.storeNewMarkedIndividual(markedIndividual);

        verify(testShepherd, times(1)).beginDBTransaction();
        verify(mockPM, times(1)).makePersistent(markedIndividual);
        verify(testShepherd, times(1)).commitDBTransaction();
        assertTrue(returnValue);
    }

    @Test public void testStoreNewMarkedIndividualWithException() {
        when(mockTransaction.isActive()).thenReturn(true);
        when(mockPM.makePersistent(any())).thenThrow(IllegalStateException.class);
        Shepherd testShepherd = spy(new Shepherd("testContext"));
        MarkedIndividual markedIndividual = null;
        boolean returnValue = testShepherd.storeNewMarkedIndividual(markedIndividual);

        assertThrows(IllegalStateException.class, () -> mockPM.makePersistent(any()));
        // test that exception leads to rollback
        verify(testShepherd, times(1)).rollbackDBTransaction();
        assertFalse(returnValue);
    }

    @Test public void testStoreNewScheduledIndividualMerge() {
        when(mockTransaction.isActive()).thenReturn(false);
        Shepherd testShepherd = spy(new Shepherd("testContext"));
        // should a null ScheduledIndividualMerge "work" here?
        ScheduledIndividualMerge scheduledIndividualMerge = null;
        boolean returnValue = testShepherd.storeNewScheduledIndividualMerge(
            scheduledIndividualMerge);

        verify(testShepherd, times(1)).beginDBTransaction();
        verify(mockPM, times(1)).makePersistent(scheduledIndividualMerge);
        verify(testShepherd, times(1)).commitDBTransaction();
        assertTrue(returnValue);
    }

    // deletion
    @Test public void testThrowAwayMarkedIndividual() {
        MarkedIndividual markedIndividual = spy(new MarkedIndividual());
        Shepherd testShepherd = new Shepherd("testContext");

        testShepherd.throwAwayMarkedIndividual(markedIndividual);
        verify(mockPM, times(1)).deletePersistent(markedIndividual);
        verify(markedIndividual, times(1)).opensearchUnindexQuiet();
    }

    // utilities
    @Test public void testIsMarkedIndividualWithString() {
        when(mockPM.getObjectById(any(), anyBoolean())).thenReturn(new MarkedIndividual());
        Shepherd testShepherd = spy(new Shepherd("testContext"));
        assertTrue(testShepherd.isMarkedIndividual("testIndividual"));
    }

    // perhaps the edge case of an empty string should be handled?
    @Test public void testIsMarkedIndividualWithEmptyString() {
        when(mockPM.getObjectById(any(), anyBoolean())).thenReturn(new MarkedIndividual());
        Shepherd testShepherd = spy(new Shepherd("testContext"));
        assertTrue(testShepherd.isMarkedIndividual(""));
    }

    @Test public void testIsMarkedIndividualWithStringWithException() {
        when(mockPM.getObjectById(any(), anyBoolean())).thenThrow(JDONullIdentityException.class);
        Shepherd testShepherd = spy(new Shepherd("testContext"));
        assertFalse(testShepherd.isMarkedIndividual(""));
    }

    @Test public void testIsMarkedIndividualWithMIisFalseWithNull() {
        Shepherd testShepherd = spy(new Shepherd("testContext"));
        MarkedIndividual markedIndividual = null;

        assertFalse(testShepherd.isMarkedIndividual(markedIndividual));
    }

    // getters
    @Test public void testGetMarkedIndividualQuiet() {
        when(mockPM.getObjectById(any(), anyBoolean())).thenReturn(new MarkedIndividual());
        Shepherd testShepherd = spy(new Shepherd("testContext"));
        assertInstanceOf(MarkedIndividual.class,
            testShepherd.getMarkedIndividualQuiet("some individual"));
    }

    @Test public void testGetMarkedIndividualByProject() {
        // null project returns empty list?
        Project testProject = null;
        List<MarkedIndividual> expected = new ArrayList<>();
        List<MarkedIndividual> returned = new ArrayList<>();

        when(mockQuery.execute(testProject)).thenReturn(returned);
        Shepherd testShepherd = spy(new Shepherd("testContext"));
        assertEquals(expected, testShepherd.getMarkedIndividualsFromProject(testProject));
    }

    @Test public void testGetMarkedIndividualByNullEncounter() {
        Shepherd testShepherd = new Shepherd("testContext");
        Encounter testEncounter = null;

        assertNull(testShepherd.getMarkedIndividual(testEncounter));
    }

    @Test public void testGetMarkedIndividualByEmptyEncounter() {
        Encounter mockEncounter;
        Shepherd testShepherd = new Shepherd("testContext");

        mockEncounter = mock(Encounter.class);
        when(mockEncounter.getIndividualID()).thenReturn("");
        assertNull(testShepherd.getMarkedIndividual(mockEncounter));
    }

    /*
       // todo:  verify these are removed or refactored after Shepherd method cleanup.
       @Test
       public void testGetMarkedIndividualThumbnails() {} // one usage ...

       @Test
       public void testGetMarkedIndividualHard() {}  // deprecated
     */
}
