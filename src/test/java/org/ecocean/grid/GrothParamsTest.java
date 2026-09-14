package org.ecocean.grid;

import org.ecocean.CommonConfiguration;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class GrothParamsTest {

    private static java.util.function.Function<String,String> lookup(Map<String,String> m) {
        return m::get; // returns null for absent keys
    }

    @Test void speciesKeyNormalizes() {
        assertEquals("carcharias_taurus", CommonConfiguration.speciesKey("Carcharias", "taurus"));
        assertEquals("carcharias_taurus", CommonConfiguration.speciesKey("  Carcharias ", " taurus "));
    }

    @Test void speciesKeyNullWhenEitherBlank() {
        assertNull(CommonConfiguration.speciesKey("Carcharias", ""));
        assertNull(CommonConfiguration.speciesKey(null, "taurus"));
        assertNull(CommonConfiguration.speciesKey("  ", "taurus"));
    }

    @Test void speciesOverrideWins() {
        Map<String,String> p = new HashMap<>();
        p.put("R", "8.8"); p.put("R.carcharias_taurus", "60");
        GrothParams gp = CommonConfiguration.resolveGrothParams("Carcharias", "taurus", lookup(p));
        assertEquals(60.0, gp.getR(), 1e-9);
    }

    @Test void fallsBackToGlobalWhenNoSpeciesKey() {
        Map<String,String> p = new HashMap<>();
        p.put("R", "8.8");
        GrothParams gp = CommonConfiguration.resolveGrothParams("Rhincodon", "typus", lookup(p));
        assertEquals(8.8, gp.getR(), 1e-9);
    }

    @Test void fallsBackToConstantWhenMissing() {
        GrothParams gp = CommonConfiguration.resolveGrothParams("Foo", "bar", lookup(new HashMap<>()));
        assertEquals(8.8, gp.getR(), 1e-9);          // default R
        assertEquals(0.015, gp.getEpsilon(), 1e-9);  // default epsilon
        assertEquals(1.146, gp.getC(), 1e-9);        // default C
    }

    @Test void nullSpeciesUsesGlobal() {
        Map<String,String> p = new HashMap<>();
        p.put("R", "8.8"); p.put("R.carcharias_taurus", "60");
        GrothParams gp = CommonConfiguration.resolveGrothParams(null, null, lookup(p));
        assertEquals(8.8, gp.getR(), 1e-9);
    }

    @Test void invalidValuesFallThrough() {
        Map<String,String> p = new HashMap<>();
        p.put("R.carcharias_taurus", "   ");   // blank
        p.put("R", "NaN");                       // non-finite
        GrothParams gp = CommonConfiguration.resolveGrothParams("Carcharias", "taurus", lookup(p));
        assertEquals(8.8, gp.getR(), 1e-9);     // both invalid -> default
        Map<String,String> p2 = new HashMap<>();
        p2.put("sizelim", "-1");                 // <= 0
        assertEquals(0.94, CommonConfiguration.resolveGrothParams("X","y", lookup(p2)).getSizelim(), 1e-9);
    }
}
