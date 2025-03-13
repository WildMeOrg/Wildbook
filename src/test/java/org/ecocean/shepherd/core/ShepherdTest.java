package org.ecocean.shepherd.core;

import org.ecocean.Shepherd;
import org.ecocean.ShepherdPMF;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

import javax.jdo.PersistenceManager;
import javax.jdo.PersistenceManagerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class ShepherdTest {

    @Test
    public void testShepherdInitialization() {
        // Mock the pmf
        PersistenceManagerFactory mockPMF = mock(PersistenceManagerFactory.class);

        // Mock the shepherdpmf static method
        try (MockedStatic<ShepherdPMF> mockedStatic = mockStatic(ShepherdPMF.class)) {
            // Define the behavior for the static call
            mockedStatic.when(() -> ShepherdPMF.getPMF(anyString()).getPersistenceManager())
                    .thenReturn(mockPMF);

            Shepherd testShepherd = new Shepherd("someContext");

            // Check Shepherd
            assertEquals(testShepherd.getContext(), "someContext");
        }
    }
}

