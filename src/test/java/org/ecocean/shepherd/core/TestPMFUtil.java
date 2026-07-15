package org.ecocean.shepherd.core;

/**
 * Test-only bridge to package-private PMF lifecycle helpers. Testcontainers test classes (which
 * live in other packages) use this to evict the statically cached PMF whose database container
 * has stopped — see ShepherdPMF.closePMF for the full rationale.
 */
public final class TestPMFUtil {
    private TestPMFUtil() {}

    public static void closePMF(String context) {
        ShepherdPMF.closePMF(context);
    }
}
