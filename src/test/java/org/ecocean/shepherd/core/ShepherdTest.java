package org.ecocean.shepherd.core;

import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.shepherd.core.ShepherdPMF;

import javax.jdo.PersistenceManager;
import javax.jdo.PersistenceManagerFactory;
import javax.jdo.Transaction;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class ShepherdTest {
    private MockedStatic<ShepherdPMF> mockedShepherdPMF;
    private PersistenceManagerFactory mockPMF;
    private PersistenceManager mockPM;
    private Transaction mockTransaction;

    @BeforeEach public void setUp() {
        // Create mock PersistenceManager and stub critical nested methods
        mockTransaction = mock(Transaction.class, RETURNS_DEEP_STUBS);
        mockPM = mock(PersistenceManager.class, RETURNS_DEEP_STUBS);
        when(mockPM.currentTransaction()).thenReturn(mockTransaction);

        // Create mock PersistenceManagerFactory and PM creation
        mockPMF = mock(PersistenceManagerFactory.class);
        when(mockPMF.getPersistenceManager()).thenReturn(mockPM);

        // Open the static mock for ShepherdPMF
        mockedShepherdPMF = Mockito.mockStatic(ShepherdPMF.class);
        // Configure the behavior for static getPMF()
        mockedShepherdPMF.when(() -> ShepherdPMF.getPMF(anyString(), any()))
            .thenReturn(mockPMF);
    }

    @AfterEach public void tearDown() {
        // Ensure that the static mock is closed after each test
        mockedShepherdPMF.close();
    }

    @Test public void testBasicShepherdInitialization() {
        Shepherd testShepherd = new Shepherd("testContext");

        assertEquals("testContext", testShepherd.getContext());
        assertEquals(mockPM, testShepherd.getPM());
    }

    @Test public void testBeginTransactionWhenInactive() {
        when(mockTransaction.isActive()).thenReturn(false);
        Shepherd testShepherd = new Shepherd("testContext");
        testShepherd.beginDBTransaction();
        // Shepherd should add the WildbookLifecycleListener() once when beginning a transaction
        verify(mockTransaction, times(1)).begin();
        verify(mockPM, times(1)).addInstanceLifecycleListener(any(), isNull());
    }

    @Test public void testBeginTransactionWhenActive() {
        when(mockTransaction.isActive()).thenReturn(true);
        Shepherd testShepherd = new Shepherd("testContext");
        testShepherd.beginDBTransaction();
        // Shepherd should not begin a trans
        verify(mockTransaction, times(0)).begin();
        // addInstanceLifecycleListener is called even if there is an active trans ... is this intended?
        verify(mockPM, times(1)).addInstanceLifecycleListener(any(), isNull());
    }

    @Test public void testCommitTransactionWhenActive() {
        when(mockTransaction.isActive()).thenReturn(true);
        Shepherd testShepherd = new Shepherd("testContext");
        testShepherd.commitDBTransaction();
        verify(mockTransaction, times(1)).commit();
    }

    @Test public void testCommitTransactionWhenInactive() {
        when(mockTransaction.isActive()).thenReturn(false);
        Shepherd testShepherd = new Shepherd("testContext");
        testShepherd.commitDBTransaction();
        verify(mockTransaction, times(0)).commit();
    }

    @Test public void testRollbackTransactionWhenActive() {
        when(mockTransaction.isActive()).thenReturn(true);
        Shepherd testShepherd = new Shepherd("testContext");
        testShepherd.rollbackDBTransaction();
        verify(mockTransaction, times(1)).rollback();
    }

    @Test public void testCheckActiveTransaction() {
        when(mockTransaction.isActive()).thenReturn(true);
        Shepherd testShepherd = new Shepherd("testContext");
        assertTrue(testShepherd.isDBTransactionActive());
    }

    @Test public void testCheckInactiveTransaction() {
        when(mockTransaction.isActive()).thenReturn(false);
        Shepherd testShepherd = new Shepherd("testContext");
        assertFalse(testShepherd.isDBTransactionActive());
    }

    @Test public void testClosePMWhenOpen() {
        when(mockPM.isClosed()).thenReturn(false);
        Shepherd testShepherd = new Shepherd("testContext");
        testShepherd.closeDBTransaction(); // note:  closeDBTransaction actually closes the persistence manager, not the transaction
        verify(mockPM, times(1)).close();
    }

    @Test public void testSetAction() {
        Shepherd testShepherd = new Shepherd("testContext");
        String action = "testAction";

        testShepherd.setAction(action);
        assertEquals(action, testShepherd.getAction());
    }
}
