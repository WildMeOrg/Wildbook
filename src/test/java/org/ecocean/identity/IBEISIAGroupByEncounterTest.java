package org.ecocean.identity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.ecocean.Annotation;
import org.ecocean.Encounter;
import org.ecocean.shepherd.core.Shepherd;
import org.junit.jupiter.api.Test;

class IBEISIAGroupByEncounterTest {

    @Test void groupsAnnotationsByEncounter() {
        Shepherd shep = mock(Shepherd.class);
        Encounter encA = mock(Encounter.class);
        when(encA.getCatalogNumber()).thenReturn("encA");
        Encounter encB = mock(Encounter.class);
        when(encB.getCatalogNumber()).thenReturn("encB");
        Annotation a1 = ann(shep, encA);
        Annotation a2 = ann(shep, encA);
        Annotation b1 = ann(shep, encB);

        Map<String, List<Annotation>> map =
            IBEISIA.groupByEncounter(Arrays.asList(a1, a2, b1), shep);

        assertEquals(2, map.size());
        assertEquals(Arrays.asList(a1, a2), map.get("encA"));
        assertEquals(Collections.singletonList(b1), map.get("encB"));
    }

    @Test void skipsAnnotationsWithNoEncounter() {
        Shepherd shep = mock(Shepherd.class);
        Annotation orphan = mock(Annotation.class);
        when(orphan.findEncounter(shep)).thenReturn(null);

        Map<String, List<Annotation>> map =
            IBEISIA.groupByEncounter(Collections.singletonList(orphan), shep);
        assertTrue(map.isEmpty());
    }

    @Test void handlesNullList() {
        assertTrue(IBEISIA.groupByEncounter(null, mock(Shepherd.class)).isEmpty());
    }

    @Test void handlesEmptyList() {
        Map<String, List<Annotation>> map =
            IBEISIA.groupByEncounter(Collections.emptyList(), mock(Shepherd.class));
        assertTrue(map.isEmpty());
    }

    private static Annotation ann(Shepherd shep, Encounter enc) {
        Annotation a = mock(Annotation.class);
        when(a.findEncounter(shep)).thenReturn(enc);
        return a;
    }
}
