package org.ecocean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.jdo.PersistenceManager;
import javax.jdo.Query;
import org.ecocean.shepherd.core.Shepherd;
import org.junit.jupiter.api.Test;

class EncounterFindByAnnotationTest {
    private Shepherd shepherdReturning(List<String> catalogNumbers, Query query) {
        Shepherd sh = mock(Shepherd.class);
        PersistenceManager pm = mock(PersistenceManager.class);

        when(sh.getPM()).thenReturn(pm);
        when(pm.newQuery(eq("javax.jdo.query.SQL"), anyString())).thenReturn(query);
        when(query.execute(anyString())).thenReturn(catalogNumbers);
        return sh;
    }

    private Annotation annotationWithId(String id) {
        Annotation ann = mock(Annotation.class);

        when(ann.getId()).thenReturn(id);
        return ann;
    }

    @Test void findByAnnotation_nullAnnotation_returnsNull() {
        assertNull(Encounter.findByAnnotation(null, mock(Shepherd.class)), "null annot -> null");
    }

    @Test void findByAnnotation_nullAnnotationId_returnsNullWithoutQuerying() {
        Shepherd sh = mock(Shepherd.class);

        assertNull(Encounter.findByAnnotation(annotationWithId(null), sh), "null id -> null");
        verify(sh, never()).getPM();
    }

    @Test void findByAnnotation_noJoinRows_returnsNull() {
        Query query = mock(Query.class);
        Shepherd sh = shepherdReturning(Collections.emptyList(), query);

        assertNull(Encounter.findByAnnotation(annotationWithId("ann-1"), sh), "orphan -> null");
        verify(query).closeAll();
    }

    @Test void findByAnnotation_singleParent_loadsByCatalogNumber() {
        Query query = mock(Query.class);
        Shepherd sh = shepherdReturning(Arrays.asList("ENC-1"), query);
        Encounter enc = mock(Encounter.class);

        when(sh.getEncounter("ENC-1")).thenReturn(enc);
        assertSame(enc, Encounter.findByAnnotation(annotationWithId("ann-1"), sh),
            "single parent returned");
        verify(query).closeAll();
    }

    @Test void findByAnnotation_multipleParents_returnsFirst() {
        Query query = mock(Query.class);
        Shepherd sh = shepherdReturning(Arrays.asList("ENC-1", "ENC-2"), query);
        Encounter enc = mock(Encounter.class);

        when(sh.getEncounter("ENC-1")).thenReturn(enc);
        assertSame(enc, Encounter.findByAnnotation(annotationWithId("ann-1"), sh),
            "first parent returned when anomalous multiple");
    }

    @Test void findAllByAnnotation_nullAnnotation_returnsEmpty() {
        assertTrue(Encounter.findAllByAnnotation(null, mock(Shepherd.class)).isEmpty(),
            "null annot -> empty list");
    }

    @Test void findAllByAnnotation_skipsUnresolvableCatalogNumbers() {
        Query query = mock(Query.class);
        Shepherd sh = shepherdReturning(Arrays.asList("ENC-GONE", "ENC-2"), query);
        Encounter enc2 = mock(Encounter.class);

        when(sh.getEncounter("ENC-GONE")).thenReturn(null);
        when(sh.getEncounter("ENC-2")).thenReturn(enc2);
        List<Encounter> out = Encounter.findAllByAnnotation(annotationWithId("ann-1"), sh);
        assertEquals(1, out.size(), "dangling join row skipped");
        assertSame(enc2, out.get(0));
    }
}
