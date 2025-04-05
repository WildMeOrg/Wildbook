package org.ecocean.shepherd.utils;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.*;

import java.util.concurrent.ConcurrentHashMap;

class ShepherdStateTest {

    @AfterEach
    void tearDown() {
        // Clear state after each test to avoid pollution
        ShepherdState.getAllShepherdStates().clear();
    }

    @Test
    void testSetAndGetShepherdState() {
        ShepherdState.setShepherdState("shepherd1", "active");
        String state = ShepherdState.getShepherdState("shepherd1");
        assertEquals("active", state);
    }

    @Test
    void testRemoveShepherdState() {
        ShepherdState.setShepherdState("shepherd2", "inactive");
        ShepherdState.removeShepherdState("shepherd2");
        assertNull(ShepherdState.getShepherdState("shepherd2"));
    }

    @Test
    void testGetAllShepherdStates() {
        ShepherdState.setShepherdState("shepherd3", "busy");
        ConcurrentHashMap<String, String> allStates = ShepherdState.getAllShepherdStates();
        assertEquals(1, allStates.size());
        assertEquals("busy", allStates.get("shepherd3"));
    }

    @Test
    void testOverwriteShepherdState() {
        ShepherdState.setShepherdState("shepherd4", "idle");
        ShepherdState.setShepherdState("shepherd4", "working");
        String state = ShepherdState.getShepherdState("shepherd4");
        assertEquals("working", state);
    }

    @Test
    void testGetStateForUnknownShepherd() {
        assertNull(ShepherdState.getShepherdState("unknown"));
    }
}
