package org.ecocean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.ecocean.security.ShepherdRealm;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.shepherd.core.Shepherd;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

class UserServiceAccountTest {

    @Test void serviceAccountPasswordNeverChecksOut() {
        String salt = ServletUtilities.getSalt().toHex();
        String hashed = ServletUtilities.hashAndSaltPassword("known-password", salt);
        User ordinary = new User("alice", hashed, salt);
        assertTrue(ordinary.checkPassword("known-password"), "ordinary user still logs in");
        User svc = new User("svc-org1", hashed, salt);
        svc.setServiceOrganizationId("org1");
        assertTrue(svc.isServiceAccount(), "marker set");
        assertFalse(svc.checkPassword("known-password"), "service account never checks out");
    }

    @Test void realmRejectsServiceAccount() throws Exception {
        User svc = new User("svc-org1", "hash", "salt");
        svc.setServiceOrganizationId("org1");
        try (MockedConstruction<Shepherd> sh = mockConstruction(Shepherd.class, (m, c) -> {
            doNothing().when(m).beginDBTransaction();
            doNothing().when(m).setAction(anyString());
            when(m.getUser("svc-org1")).thenReturn(svc);
        })) {
            ShepherdRealm realm = new ShepherdRealm();
            java.lang.reflect.Method meth = ShepherdRealm.class.getDeclaredMethod(
                "doGetAuthenticationInfo", org.apache.shiro.authc.AuthenticationToken.class);
            meth.setAccessible(true);
            java.lang.reflect.InvocationTargetException e = assertThrows(
                java.lang.reflect.InvocationTargetException.class,
                () -> meth.invoke(realm, new UsernamePasswordToken("svc-org1", "x")),
                "realm must reject service account");
            assertTrue(e.getCause() instanceof AuthenticationException, "is an AuthenticationException");
        }
    }
}
