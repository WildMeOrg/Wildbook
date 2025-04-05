package org.ecocean.shepherd.utils;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.concurrent.ConcurrentHashMap;

import org.ecocean.extensions.StaticFieldClearExtension;

class ShepherdStateTest {

    @RegisterExtension
    static StaticFieldClearExtension clearExtension =
            new StaticFieldClearExtension(ShepherdState.class, "shepherds");

    @Test
    void testSetAndGetShepherdState() {
        ShepherdState.setShepherdState("shepherd1", "active");
        assertEquals("active", ShepherdState.getShepherdState("shepherd1"));
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
        assertEquals("working", ShepherdState.getShepherdState("shepherd4"));
    }

    @Test
    void testGetStateForUnknownShepherd() {
        assertNull(ShepherdState.getShepherdState("unknown"));
    }
}
