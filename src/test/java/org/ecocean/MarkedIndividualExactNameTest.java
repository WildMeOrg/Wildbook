package org.ecocean;

import javax.jdo.PersistenceManager;
import javax.jdo.Query;

import org.ecocean.shepherd.core.Shepherd;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class MarkedIndividualExactNameTest {
    Shepherd mockShepherd;
    PersistenceManager mockPM;
    Query mockQuery;
    HashMap<Integer, String> savedCache;

    @SuppressWarnings("unchecked")
    private HashMap<Integer, String> namesCache()
    throws Exception {
        Field f = MarkedIndividual.class.getDeclaredField("NAMES_CACHE");

        f.setAccessible(true);
        return (HashMap<Integer, String>)f.get(null);
    }

    @BeforeEach void setUp()
    throws Exception {
        mockShepherd = mock(Shepherd.class);
        mockPM = mock(PersistenceManager.class);
        mockQuery = mock(Query.class);
        when(mockShepherd.getPM()).thenReturn(mockPM);
        when(mockPM.newQuery(anyString())).thenReturn(mockQuery);
        savedCache = new HashMap<Integer, String>(namesCache());
        namesCache().clear();
    }

    @AfterEach void tearDown()
    throws Exception {
        namesCache().clear();
        namesCache().putAll(savedCache);
    }

    @Test void exactNameMatchesAndScopesByTaxonomy()
    throws Exception {
        // cache format: "<individualId>;<name1>;<name2>" with names lowercased
        namesCache().put(7, "11111111-aaaa-bbbb-cccc-dddddddddddd;gns-2620");
        MarkedIndividual indiv = mock(MarkedIndividual.class);
        when(mockQuery.execute()).thenReturn(Arrays.asList(indiv));

        List<MarkedIndividual> found = MarkedIndividual.findByExactName(mockShepherd, "GNS-2620",
            "Carcharodon", "carcharias");
        assertEquals(1, found.size());
        assertEquals(indiv, found.get(0));

        ArgumentCaptor<String> jdoql = ArgumentCaptor.forClass(String.class);
        verify(mockPM).newQuery(jdoql.capture());
        assertTrue(jdoql.getValue().contains("names.id == 7"));
        assertTrue(jdoql.getValue().contains("Carcharodon"));
        assertTrue(jdoql.getValue().contains("carcharias"));
    }

    @Test void substringOrOtherNamesDoNotMatch()
    throws Exception {
        namesCache().put(3, "22222222-aaaa-bbbb-cccc-dddddddddddd;gns-26201;other-name");

        List<MarkedIndividual> found = MarkedIndividual.findByExactName(mockShepherd, "GNS-2620",
            null, null);
        assertEquals(0, found.size());
        verify(mockPM, never()).newQuery(anyString());
    }

    @Test void caseInsensitiveExactMatchWithoutTaxonomy()
    throws Exception {
        namesCache().put(5, "33333333-aaaa-bbbb-cccc-dddddddddddd;r1714");
        MarkedIndividual indiv = mock(MarkedIndividual.class);
        when(mockQuery.execute()).thenReturn(Arrays.asList(indiv));

        List<MarkedIndividual> found = MarkedIndividual.findByExactName(mockShepherd, "r1714",
            null, null);
        assertEquals(1, found.size());
        ArgumentCaptor<String> jdoql = ArgumentCaptor.forClass(String.class);
        verify(mockPM).newQuery(jdoql.capture());
        // no taxonomy scope when genus/specificEpithet not both given
        assertTrue(!jdoql.getValue().contains("genus"));
    }

    @Test void firstCacheSegmentIsIndividualIdNotAName()
    throws Exception {
        namesCache().put(11, "55555555-aaaa-bbbb-cccc-dddddddddddd;gns-1");

        // the leading segment is the individual id and must not match as a name
        List<MarkedIndividual> found = MarkedIndividual.findByExactName(mockShepherd,
            "55555555-aaaa-bbbb-cccc-dddddddddddd", null, null);
        assertEquals(0, found.size());
        verify(mockPM, never()).newQuery(anyString());
    }

    @Test void blankTaxonomyMeansNoScope()
    throws Exception {
        namesCache().put(13, "66666666-aaaa-bbbb-cccc-dddddddddddd;gns-5");
        MarkedIndividual indiv = mock(MarkedIndividual.class);
        when(mockQuery.execute()).thenReturn(Arrays.asList(indiv));

        List<MarkedIndividual> found = MarkedIndividual.findByExactName(mockShepherd, "GNS-5",
            "", "  ");
        assertEquals(1, found.size());
        ArgumentCaptor<String> jdoql = ArgumentCaptor.forClass(String.class);
        verify(mockPM).newQuery(jdoql.capture());
        // blank genus/specificEpithet must not become an ''=='' taxonomy filter
        assertTrue(!jdoql.getValue().contains("enc.genus"));
    }

    @Test void inputIsTrimmed()
    throws Exception {
        namesCache().put(15, "77777777-aaaa-bbbb-cccc-dddddddddddd;gns-7");
        MarkedIndividual indiv = mock(MarkedIndividual.class);
        when(mockQuery.execute()).thenReturn(Arrays.asList(indiv));

        assertEquals(1,
            MarkedIndividual.findByExactName(mockShepherd, "  GNS-7  ", null, null).size());
    }

    @Test void regexMetacharactersInNameAreSafe()
    throws Exception {
        namesCache().put(9, "44444444-aaaa-bbbb-cccc-dddddddddddd;r(19+a]");
        MarkedIndividual indiv = mock(MarkedIndividual.class);
        when(mockQuery.execute()).thenReturn(Arrays.asList(indiv));

        // must neither throw nor mis-match
        List<MarkedIndividual> found = MarkedIndividual.findByExactName(mockShepherd, "R(19+a]",
            null, null);
        assertEquals(1, found.size());
        assertEquals(0,
            MarkedIndividual.findByExactName(mockShepherd, "R.19.a.", null, null).size());
    }
}
