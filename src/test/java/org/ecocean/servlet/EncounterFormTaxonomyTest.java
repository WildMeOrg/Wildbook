package org.ecocean.servlet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.Util;
import org.junit.jupiter.api.Test;

class EncounterFormTaxonomyTest {
    private Shepherd shepherdWith(String... names) {
        Shepherd sh = mock(Shepherd.class);
        when(sh.getAllTaxonomyNames()).thenReturn(Arrays.asList(names));
        return sh;
    }

    private boolean accepts(Shepherd sh, String submitted) {
        return EncounterForm.isConfiguredTaxonomy(Util.parseGenusSpecies(submitted), sh);
    }

    @Test void acceptsConfiguredNames() {
        Shepherd sh = shepherdWith("Rhincodon typus", "Delphinus capensis tropicalis",
            "Tursiops truncatus_gephyreus");
        assertTrue(accepts(sh, "Rhincodon typus"));
        assertTrue(accepts(sh, "  Rhincodon   typus "));
        assertTrue(accepts(sh, "rhincodon TYPUS"));
        assertTrue(accepts(sh, "Delphinus capensis tropicalis"));
        assertTrue(accepts(sh, "Tursiops truncatus_gephyreus"));
        assertTrue(accepts(sh, "Tursiops truncatus gephyreus"));
    }

    @Test void rejectsUnconfiguredNames() {
        Shepherd sh = shepherdWith("Rhincodon typus");
        assertFalse(accepts(sh, "Rhincodon other"));
        assertFalse(accepts(sh, "Rhincodon typus extra"));
        assertFalse(accepts(sh, "Foo <b>bar</b>"));
        assertFalse(EncounterForm.isConfiguredTaxonomy(new String[] { "Rhincodon", "typus" },
            shepherdWith()));
    }
}
