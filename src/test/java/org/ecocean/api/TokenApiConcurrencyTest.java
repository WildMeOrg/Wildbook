package org.ecocean.api;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The per-instance concurrency guard: a bounded number of token requests run at once (excess gets a
 * null permit -> caller 429s), permits are restored on close, the two pools are independent, and
 * double-close is safe (no over-release).
 */
class TokenApiConcurrencyTest {

    private List<TokenApiConcurrency.Permit> drain(TokenApiConcurrency.Kind k) {
        List<TokenApiConcurrency.Permit> held = new ArrayList<>();
        TokenApiConcurrency.Permit p;
        while ((p = TokenApiConcurrency.tryAcquire(k)) != null) held.add(p);
        return held;
    }

    @Test void exhaustsThenRecovers() {
        TokenApiConcurrency.Kind K = TokenApiConcurrency.Kind.SIMILARITY;
        List<TokenApiConcurrency.Permit> held = drain(K);
        try {
            assertTrue(held.size() >= 1, "pool has at least one permit");
            assertEquals(0, TokenApiConcurrency.availablePermits(K), "pool is fully drained");
            assertNull(TokenApiConcurrency.tryAcquire(K), "no permit when the pool is at capacity");

            held.get(0).close(); // release one
            TokenApiConcurrency.Permit again = TokenApiConcurrency.tryAcquire(K);
            assertNotNull(again, "a permit is available again after a release");
            again.close();
        } finally {
            for (TokenApiConcurrency.Permit p : held) p.close();
        }
        // all returned
        int max = TokenApiConcurrency.availablePermits(K);
        assertTrue(max >= 1, "permits fully restored after closing");
    }

    @Test void poolsAreIndependent() {
        List<TokenApiConcurrency.Permit> sim = drain(TokenApiConcurrency.Kind.SIMILARITY);
        try {
            assertEquals(0, TokenApiConcurrency.availablePermits(TokenApiConcurrency.Kind.SIMILARITY));
            TokenApiConcurrency.Permit g =
                TokenApiConcurrency.tryAcquire(TokenApiConcurrency.Kind.GENERAL);
            assertNotNull(g, "exhausting SIMILARITY must not block GENERAL");
            g.close();
        } finally {
            for (TokenApiConcurrency.Permit p : sim) p.close();
        }
    }

    @Test void doubleCloseIsSafe() {
        TokenApiConcurrency.Kind K = TokenApiConcurrency.Kind.GENERAL;
        int before = TokenApiConcurrency.availablePermits(K);
        TokenApiConcurrency.Permit p = TokenApiConcurrency.tryAcquire(K);
        assertNotNull(p);
        assertEquals(before - 1, TokenApiConcurrency.availablePermits(K));
        p.close();
        p.close(); // must NOT over-release
        assertEquals(before, TokenApiConcurrency.availablePermits(K),
            "double close releases exactly one permit");
    }
}
