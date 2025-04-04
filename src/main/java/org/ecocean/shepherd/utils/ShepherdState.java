package org.ecocean.shepherd.utils;

import java.util.concurrent.ConcurrentHashMap;

public class ShepherdState {

    private static ConcurrentHashMap<String, String> shepherds = new ConcurrentHashMap<String,
            String>();

    public static void setShepherdState(String shepherdID, String state) {
        if (shepherds == null) shepherds = new ConcurrentHashMap<String, String>();
        shepherds.put(shepherdID, state);
    }

    public static void removeShepherdState(String shepherdID) {
        if (shepherds == null) shepherds = new ConcurrentHashMap<String, String>();
        shepherds.remove(shepherdID);
    }

    public static String getShepherdState(String shepherdID) {
        if (shepherds == null) shepherds = new ConcurrentHashMap<String, String>();
        return shepherds.get(shepherdID);
    }

    public static ConcurrentHashMap<String, String> getAllShepherdStates() {
        if (shepherds == null) shepherds = new ConcurrentHashMap<String, String>();
        return shepherds;
    }
}
