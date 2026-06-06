package org.ecocean.servlet.importer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import javax.jdo.Query;

import org.junit.jupiter.api.Test;

/**
 * Tests the batch count parsing logic in ImportTask.
 * The public methods (getAllEncounterCounts, etc.) compose a SQL query and
 * pass it to parseSqlCountResults, which is thoroughly tested here.
 */
class ImportTaskCountsTest {

    @Test
    void parseSqlCountResults_parsesRowsAndHandlesTypes() {
        Query query = mock(Query.class);
        when(query.executeList()).thenReturn(Arrays.asList(
            new Object[]{"task-long", 5L},
            new Object[]{"task-int", Integer.valueOf(3)},
            new Object[]{"task-zero", 0L}
        ));

        Map<String, Integer> result = ImportTask.parseSqlCountResults(query);

        assertEquals(3, result.size());
        assertEquals(5, result.get("task-long"));
        assertEquals(3, result.get("task-int"),
            "Should handle Integer via Number.intValue()");
        assertEquals(0, result.get("task-zero"));
        // Missing keys return null from the map; callers use getOrDefault
        assertNull(result.get("task-missing"));
        assertEquals(0, result.getOrDefault("task-missing", 0));
        verify(query).closeAll();
    }

    @Test
    void parseSqlCountResults_emptyResults() {
        Query query = mock(Query.class);
        when(query.executeList()).thenReturn(Collections.emptyList());

        Map<String, Integer> result = ImportTask.parseSqlCountResults(query);

        assertTrue(result.isEmpty());
        verify(query).closeAll();
    }

    @Test
    void parseSqlCountResults_queryThrows_returnsEmptyMapAndClosesQuery() {
        Query query = mock(Query.class);
        when(query.executeList()).thenThrow(new RuntimeException("DB error"));

        Map<String, Integer> result = ImportTask.parseSqlCountResults(query);

        assertTrue(result.isEmpty(), "Should return empty map on exception");
        verify(query).closeAll();
    }
}
