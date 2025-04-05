package org.ecocean.shepherd.utils;

import java.util.concurrent.ConcurrentHashMap;

public final class ShepherdState {

    private static final ConcurrentHashMap<String, String> shepherds = new ConcurrentHashMap<>();

    private ShepherdState() {
        // Prevent instantiation
    }

    public static void setShepherdState(String shepherdID, String state) {
        shepherds.put(shepherdID, state);
    }

    public static void removeShepherdState(String shepherdID) {
        shepherds.remove(shepherdID);
    }

    public static String getShepherdState(String shepherdID) {
        return shepherds.get(shepherdID);
    }

    public static ConcurrentHashMap<String, String> getAllShepherdStates() {
        return shepherds;
    }
}
