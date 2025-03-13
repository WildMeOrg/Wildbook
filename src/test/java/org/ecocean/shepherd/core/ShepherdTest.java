package org.ecocean.shepherd.core;

import org.ecocean.Shepherd;
import org.ecocean.ShepherdPMF;

import javax.jdo.PersistenceManagerFactory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

public class ShepherdTest {

    private MockedStatic<ShepherdPMF> mockedStatic;
    private PersistenceManagerFactory mockPMF;

    @BeforeEach
    public void setUp() {
        // Create your mock instance
        mockPMF = mock(PersistenceManagerFactory.class);
        // Open the static mock for PersistenceManagerFactory
        mockedStatic = Mockito.mockStatic(ShepherdPMF.class);
        // Configure the behavior for the static method
        mockedStatic.when(() -> ShepherdPMF.getPMF(anyString()).getPersistenceManager())
                .thenReturn(mockPMF);
    }

    @AfterEach
    public void tearDown() {
        // Ensure that the static mock is closed after each test
        mockedStatic.close();
    }

    @Test
    public void testShepherdInitialization() {
        Shepherd testShepherd = new Shepherd("testContext");

        assertEquals(testShepherd.getContext(), "testContext");
    }
}