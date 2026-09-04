package org.ecocean;

import javax.jdo.PersistenceManager;

import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Failure-path contract for OpenSearch.queryStore: null ⇔ durability not confirmed;
 * a confirmed commit always yields the id, even when post-commit recovery fails.
 */
class OpenSearchQueryStoreContractTest {

    private final JSONObject query = new JSONObject().put("query",
        new JSONObject().put("match_all", new JSONObject()));

    private User mockUser() {
        User u = mock(User.class);

        when(u.getUUID()).thenReturn("user-uuid");
        return u;
    }

    private Shepherd mockShepherd(boolean commitOk) {
        Shepherd sh = mock(Shepherd.class);
        PersistenceManager pm = mock(PersistenceManager.class);

        when(sh.getPM()).thenReturn(pm);
        when(sh.commitDBTransactionWithStatus()).thenReturn(commitOk);
        return sh;
    }

    @Test void commitFailure_returnsNull_andRecoversTransaction() {
        Shepherd sh = mockShepherd(false);
        String id = OpenSearch.queryStore(query, "encounter", mockUser(), sh);

        assertNull(id, "unconfirmed commit must not yield an id");
        verify(sh).rollbackDBTransaction();
        verify(sh).beginDBTransaction();
    }

    @Test void makePersistentThrows_returnsNull() {
        Shepherd sh = mock(Shepherd.class);
        PersistenceManager pm = mock(PersistenceManager.class);

        when(sh.getPM()).thenReturn(pm);
        when(sh.commitDBTransactionWithStatus()).thenReturn(true);
        doThrow(new RuntimeException("boom")).when(pm).makePersistent(any());
        assertNull(OpenSearch.queryStore(query, "encounter", mockUser(), sh),
            "persist failure must not yield an id");
    }

    @Test void rebeginThrows_afterConfirmedCommit_stillReturnsId() {
        Shepherd sh = mockShepherd(true);

        doThrow(new RuntimeException("re-begin failed")).when(sh).beginDBTransaction();
        assertNotNull(OpenSearch.queryStore(query, "encounter", mockUser(), sh),
            "a durably committed id must be returned even when re-begin fails");
    }

    @Test void nullArguments_returnNull() {
        Shepherd sh = mockShepherd(true);

        assertNull(OpenSearch.queryStore(null, "encounter", mockUser(), sh));
        assertNull(OpenSearch.queryStore(query, "encounter", null, sh));
        assertNull(OpenSearch.queryStore(query, "encounter", mockUser(), null));
    }
}
