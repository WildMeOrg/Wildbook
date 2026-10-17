package org.ecocean;

/** Test-only bridge to the package-private IA.json override (pattern of shepherd.core.TestPMFUtil). */
public final class TestIAJsonPropertiesUtil {
    private TestIAJsonPropertiesUtil() {}

    public static void override(IAJsonProperties config) {
        IAJsonProperties.overrideForTests(config);
    }
}
