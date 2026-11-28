package org.ecocean.servlet;

import static org.junit.jupiter.api.Assertions.*;

import org.ecocean.User;
import org.junit.jupiter.api.Test;

class UserCreateServiceAccountTest {

    @Test void onlySiteAdminEditsServiceAccount() {
        User svc = new User("svc-org1", "hash", "salt");
        svc.setServiceOrganizationId("org1");
        User ordinary = new User("alice", "hash", "salt");
        assertFalse(UserCreate.canEditServiceAccount(false, svc), "non-admin blocked");
        assertTrue(UserCreate.canEditServiceAccount(true, svc), "site admin allowed");
        assertTrue(UserCreate.canEditServiceAccount(false, ordinary), "ordinary users unchanged");
    }
}
