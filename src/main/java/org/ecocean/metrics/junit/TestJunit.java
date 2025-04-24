package org.ecocean.metrics.junit;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import org.ecocean.metrics.Prometheus;

public class TestJunit {
    String messsage = "Hello worl";

    // Shepherd myShepherd;
    File myFile;
    PrintWriter pw;
    Prometheus promObject;

    // Testing vars
    int usersInWildbook = -1;
    int wLogin = -1;
    int woLogin = -1;
    int encsInWildbook = -1;

    @Before public void setUp()
    throws FileNotFoundException {
        // initialize our global variables
        // this.myShepherd = new Shepherd("context0");
        this.promObject = new Prometheus(true);

        // for now hardcode the actual values
        this.usersInWildbook = 31;
        this.wLogin = 22;
        this.woLogin = 9;
        this.encsInWildbook = 32;
    }

    @Test public void testPrintMessage() {
        assertEquals(messsage, "Hello worl");
    }

    @Test public void testSetNumberOfUsers() {
    }

    @Test public void testSetNumberOfEncounters() {
    }

    @Test public void testSetNumberOfIndividuals() {
    }

    @Test public void testSetNumberofMediaAssets() {
    }
}
