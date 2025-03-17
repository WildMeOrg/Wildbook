package org.ecocean.shepherd.entity;

import org.junit.jupiter.api.Test;

public class IndividualTest {

    // creation
    @Test
    public void testStoreNewMarkedIndividual() {}

    @Test
    public void testStoreNewMarkedIndividualException() {}

    // deletion
    @Test
    public void testThrowAwayMarkedIndividual() {} // test call to opensearch unindex ...

    // getters ... replace all with OpenSearch?
    @Test
    public void testGetMarkedIndividualById() {}  // used in getAllUsersForMarkedIndividual ... which is not used

    @Test
    public void testGetMarkedIndividualByProject() {}

    @Test
    public void testGetMarkedIndividualHard() {}

    @Test
    public void testGetMarkedIndividualQuiet() {}

    @Test
    public void testGetMarkedIndividualByEncounter() {}

    @Test
    public void testGetOrCreateMarkedIndividual() {}

    @Test
    public void testGetMarkedIndividualThumbnails() {}

    @Test
    public void testGetMarkedIndividualsByAlternateID() {}

    @Test
    public void testGetMarkedIndividualCaseInsensitive() {}

    @Test
    public void testGetMarkedIndividualsByNickname() {} // no usage?
}
