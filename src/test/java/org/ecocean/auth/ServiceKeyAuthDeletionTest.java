package org.ecocean.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import javax.jdo.JDOObjectNotFoundException;
import javax.jdo.PersistenceManager;
import javax.jdo.Query;

import org.ecocean.User;
import org.ecocean.shepherd.core.Shepherd;
import org.junit.jupiter.api.Test;

/** A row deleted between the query and the refresh fails closed with a 401, never a 500. */
@SuppressWarnings({ "unchecked", "rawtypes" })
class ServiceKeyAuthDeletionTest {
    private static final long DAY = 24L * 3600L * 1000L;

    private static Query queryReturning(Object row) {
        Query query = mock(Query.class);
        when(query.execute(any())).thenReturn(Collections.singletonList(row));
        return query;
    }

    @Test void keyDeletedMidExchangeIsInvalid() {
        ServiceKeyCredential.Issued issued = ServiceKeyCredential.issue();
        ServiceKey key = new ServiceKey(issued.keyId, "org-1", "user-1", null, issued.secretHash, "data:read",
            0L, "admin-id", System.currentTimeMillis() + DAY);
        PersistenceManager pm = mock(PersistenceManager.class);
        Query keyQuery = queryReturning(key);
        when(pm.newQuery(eq(ServiceKey.class), anyString())).thenReturn(keyQuery);
        doThrow(new JDOObjectNotFoundException("gone")).when(pm).refresh(key);
        Shepherd sh = mock(Shepherd.class);
        when(sh.getPM()).thenReturn(pm);

        ServiceKeyException ex = assertThrows(ServiceKeyException.class,
            () -> ServiceKeyAuth.verify(sh, issued.plaintext, System.currentTimeMillis()), "must be rejected");
        assertEquals(401, ex.status, "status");
        assertEquals(ServiceKeyAuth.INVALID, ex.getMessage(), "message");
    }

    @Test void serviceUserDeletedMidExchangeIsUnavailable() {
        ServiceKeyCredential.Issued issued = ServiceKeyCredential.issue();
        ServiceKey key = new ServiceKey(issued.keyId, "org-1", "user-1", null, issued.secretHash, "data:read",
            0L, "admin-id", System.currentTimeMillis() + DAY);
        User user = new User(null, "user-1");
        PersistenceManager pm = mock(PersistenceManager.class);
        Query keyQuery = queryReturning(key);
        Query userQuery = queryReturning(user);
        when(pm.newQuery(eq(ServiceKey.class), anyString())).thenReturn(keyQuery);
        when(pm.newQuery(eq(User.class), anyString())).thenReturn(userQuery);
        Query orgQuery = queryReturning(new org.ecocean.Organization("org"));
        when(pm.newQuery(eq(org.ecocean.Organization.class), anyString())).thenReturn(orgQuery);
        doThrow(new JDOObjectNotFoundException("gone")).when(pm).refresh(user);
        Shepherd sh = mock(Shepherd.class);
        when(sh.getPM()).thenReturn(pm);

        ServiceKeyException ex = assertThrows(ServiceKeyException.class,
            () -> ServiceKeyAuth.verify(sh, issued.plaintext, System.currentTimeMillis()), "must be rejected");
        assertEquals(401, ex.status, "status");
        assertEquals(ServiceKeyAuth.UNAVAILABLE, ex.getMessage(), "message");
    }
}
