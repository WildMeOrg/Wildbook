package org.ecocean.shepherd.core;

import org.ecocean.Shepherd;
import org.ecocean.ShepherdPMF;

import javax.jdo.PersistenceManager;
import javax.jdo.PersistenceManagerFactory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class ShepherdTest {

    private MockedStatic<ShepherdPMF> mockedStatic;
    private PersistenceManagerFactory mockPMF;
    private PersistenceManager mockPM;

    @BeforeEach
    public void setUp() {
        // Create mock PersistenceManager and stub critical nested methods
        mockPM = mock(PersistenceManager.class, RETURNS_DEEP_STUBS);

        // Create mock PersistenceManagerFactory and PM creation
        mockPMF = mock(PersistenceManagerFactory.class);
        when(mockPMF.getPersistenceManager()).thenReturn(mockPM);

        // Open the static mock for ShepherdPMF
        mockedStatic = Mockito.mockStatic(ShepherdPMF.class);
        // Configure the behavior for static getPMF()
        mockedStatic.when(() -> ShepherdPMF.getPMF(anyString()))
                .thenReturn(mockPMF);
    }

    @AfterEach
    public void tearDown() {
        // Ensure that the static mock is closed after each test
        mockedStatic.close();
    }

    @Test
    public void testBasicShepherdInitialization() {
        Shepherd testShepherd = new Shepherd("testContext");
        assertEquals(testShepherd.getContext(), "testContext");
        assertEquals(testShepherd.getPM(), mockPM);
    }

    @Test
    public void testBeginTransaction() {
        when(mockPM.currentTransaction().isActive()).thenReturn(false);
        Shepherd testShepherd = new Shepherd("testContext");
        testShepherd.beginDBTransaction();
        // Shepherd should add the WildbookLifecycleListener() once when beginning a transaction
        verify(mockPM, times(1)).addInstanceLifecycleListener(any(), isNull());
    }
}