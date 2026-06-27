package org.ecocean.cache;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

class QueryCacheFactorySafeInvalidateTest {
    private static final String CTX = "context0";
    private static final String NAME = "iaImageIds";

    @Test void safeInvalidate_invokesInvalidateByName_whenCacheNotNull()
    throws Exception {
        QueryCache cache = mock(QueryCache.class);
        doNothing().when(cache).invalidateByName(NAME);
        try (MockedStatic<QueryCacheFactory> mocked = mockStatic(QueryCacheFactory.class)) {
            mocked.when(() -> QueryCacheFactory.getQueryCache(CTX)).thenReturn(cache);
            mocked.when(() -> QueryCacheFactory.safeInvalidate(CTX, NAME)).thenCallRealMethod();
            QueryCacheFactory.safeInvalidate(CTX, NAME);
        }
        verify(cache).invalidateByName(NAME);
    }

    @Test void safeInvalidate_swallowsNull_whenGetQueryCacheReturnsNull() {
        try (MockedStatic<QueryCacheFactory> mocked = mockStatic(QueryCacheFactory.class)) {
            mocked.when(() -> QueryCacheFactory.getQueryCache(CTX)).thenReturn(null);
            mocked.when(() -> QueryCacheFactory.safeInvalidate(CTX, NAME)).thenCallRealMethod();
            // Must not throw.
            QueryCacheFactory.safeInvalidate(CTX, NAME);
        }
    }

    @Test void safeInvalidate_swallowsIoException_fromInvalidateByName()
    throws Exception {
        QueryCache cache = mock(QueryCache.class);
        doThrow(new IOException("simulated cache failure"))
            .when(cache).invalidateByName(NAME);
        try (MockedStatic<QueryCacheFactory> mocked = mockStatic(QueryCacheFactory.class)) {
            mocked.when(() -> QueryCacheFactory.getQueryCache(CTX)).thenReturn(cache);
            mocked.when(() -> QueryCacheFactory.safeInvalidate(CTX, NAME)).thenCallRealMethod();
            // Must not throw; exception is logged and swallowed.
            QueryCacheFactory.safeInvalidate(CTX, NAME);
        }
        verify(cache).invalidateByName(NAME);
    }

    @Test void safeInvalidate_swallowsRuntimeException_fromGetQueryCache() {
        try (MockedStatic<QueryCacheFactory> mocked = mockStatic(QueryCacheFactory.class)) {
            mocked.when(() -> QueryCacheFactory.getQueryCache(CTX))
                .thenThrow(new RuntimeException("simulated factory failure"));
            mocked.when(() -> QueryCacheFactory.safeInvalidate(CTX, NAME)).thenCallRealMethod();
            // Must not propagate.
            QueryCacheFactory.safeInvalidate(CTX, NAME);
        }
    }

    @Test void safeInvalidate_swallowsRuntimeException_fromInvalidateByName()
    throws Exception {
        // The shape of a poisoned/half-initialized singleton: getQueryCache
        // returns a cache instance, but invalidateByName throws a runtime
        // failure (e.g., NPE from a partially-loaded internal map).
        QueryCache cache = mock(QueryCache.class);
        doThrow(new RuntimeException("simulated runtime failure"))
            .when(cache).invalidateByName(NAME);
        try (MockedStatic<QueryCacheFactory> mocked = mockStatic(QueryCacheFactory.class)) {
            mocked.when(() -> QueryCacheFactory.getQueryCache(CTX)).thenReturn(cache);
            mocked.when(() -> QueryCacheFactory.safeInvalidate(CTX, NAME)).thenCallRealMethod();
            QueryCacheFactory.safeInvalidate(CTX, NAME);
        }
        verify(cache).invalidateByName(NAME);
    }
}
