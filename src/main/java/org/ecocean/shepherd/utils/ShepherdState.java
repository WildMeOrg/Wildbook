package org.ecocean.shepherd.utils;

import java.util.concurrent.ConcurrentHashMap;

/**
 * ShepherdState provides static utility methods to track the state and use of shepherd instances (database access).
 *
 * See, e.g., Shepherd transaction handlers, Shepherd.setAction(...), RestServlet.doGet(...), etc.
 * A state dump can be accessed via dbconnections.jsp
 *
 * <p><strong>Note:</strong> This class is not intended to be instantiated.</p>
 */
public final class ShepherdState {

    // Internal thread-safe static map to store the state.
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
