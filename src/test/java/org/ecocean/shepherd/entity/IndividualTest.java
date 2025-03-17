package org.ecocean.shepherd.entity;

import org.ecocean.MarkedIndividual;
import org.ecocean.Shepherd;
import org.ecocean.ShepherdPMF;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import javax.jdo.PersistenceManager;
import javax.jdo.PersistenceManagerFactory;
import javax.jdo.Transaction;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class IndividualTest {
    private MockedStatic<ShepherdPMF> mockedShepherdPMF;
    private PersistenceManagerFactory mockPMF;
    private PersistenceManager mockPM;
    private Transaction mockTransaction;

    @BeforeEach
    public void setUp() {
        // Create mock PersistenceManager and stub critical nested methods
        mockTransaction = mock(Transaction.class, RETURNS_DEEP_STUBS);
        mockPM = mock(PersistenceManager.class, RETURNS_DEEP_STUBS);
        when(mockPM.currentTransaction()).thenReturn(mockTransaction);

        // Create mock PersistenceManagerFactory and PM creation
        mockPMF = mock(PersistenceManagerFactory.class);
        when(mockPMF.getPersistenceManager()).thenReturn(mockPM);

        // Open the static mock for ShepherdPMF
        mockedShepherdPMF = mockStatic(ShepherdPMF.class);
        // Configure the behavior for static getPMF()
        mockedShepherdPMF.when(() -> ShepherdPMF.getPMF(anyString()))
                .thenReturn(mockPMF);
    }

    @AfterEach
    public void tearDown() {
        // Ensure that the static mock is closed after each test
        mockedShepherdPMF.close();
    }
    // creation
    @Test
    public void testStoreNewMarkedIndividual() {
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
    @Test
    public void testStoreNewMarkedIndividualWithActiveTransaction() {
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

    @Test
    public void testStoreNewMarkedIndividualWithException() {
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

    // deletion
    @Test
    public void testThrowAwayMarkedIndividual() {} // test call to opensearch unindex ...

    // getters ... replace all with OpenSearch?
    @Test
    public void testGetMarkedIndividualById() {}  // used in getAllUsersForMarkedIndividual ... which is not used

    @Test
    public void testGetMarkedIndividualByProject() {}

    @Test
    public void testGetMarkedIndividualHard() {}

    @Test
    public void testGetMarkedIndividualQuiet() {}

    @Test
    public void testGetMarkedIndividualByEncounter() {}

    @Test
    public void testGetOrCreateMarkedIndividual() {}

    @Test
    public void testGetMarkedIndividualThumbnails() {}

    @Test
    public void testGetMarkedIndividualsByAlternateID() {}

    @Test
    public void testGetMarkedIndividualCaseInsensitive() {}

    @Test
    public void testGetMarkedIndividualsByNickname() {} // no usage?
}
