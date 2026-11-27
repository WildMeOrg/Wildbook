package org.ecocean.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.jdo.PersistenceManager;
import javax.jdo.PersistenceManagerFactory;

import org.ecocean.Annotation;
import org.ecocean.Encounter;
import org.ecocean.IAJsonProperties;
import org.ecocean.LabeledKeyword;
import org.ecocean.MarkedIndividual;
import org.ecocean.Occurrence;
import org.ecocean.TestIAJsonPropertiesUtil;
import org.ecocean.User;
import org.ecocean.api.bulk.BulkImportUtil;
import org.ecocean.api.bulk.BulkImporter;
import org.ecocean.api.bulk.GroupCollapse;
import org.ecocean.api.bulk.GroupedRows;
import org.ecocean.genetics.GeneticAnalysis;
import org.ecocean.genetics.Locus;
import org.ecocean.genetics.MicrosatelliteMarkersAnalysis;
import org.ecocean.genetics.TissueSample;
import org.ecocean.media.AssetStore;
import org.ecocean.media.FeatureType;
import org.ecocean.media.MediaAsset;
import org.ecocean.media.MediaAssetMetadata;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.shepherd.core.ShepherdPMF;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

/**
 * Bulk import groupKey (#1789), Task 8: BulkImporter turns a collapsed group into one encounter
 * with one sighting, and a grouped row's individual takes sex and taxonomy from that encounter.
 * Harness as BulkImporterBoxedRowsTest; grouped input is built by GroupCollapse.collapse.
 */
class BulkImporterGroupedRowTest {
    static final String KEY = "Encounter.groupKey";
    PersistenceManager mockPM = mock(PersistenceManager.class);
    PersistenceManagerFactory mockPMF = mock(PersistenceManagerFactory.class);
    User user = mock(User.class);
    Occurrence occ = mock(Occurrence.class);
    MarkedIndividual existingByName = null;   // what the importer's name lookup returns
    final Map<String, MediaAsset> maMap = new HashMap<String, MediaAsset>();

    @BeforeEach void fixture() {
        BulkImportUtil.labeledKeywordMap = new LinkedHashMap<String, Set<String> >();
        BulkImportUtil.labeledKeywordMap.put("colour", new LinkedHashSet<String>(List.of("red", "blue")));
        JSONObject json = new JSONObject().put("Genus", new JSONObject().put("species", new JSONObject()
            .put("body", new JSONObject().put("_id_conf", new JSONArray().put(new JSONObject().put("default", true))))));
        IAJsonProperties iac = IAJsonProperties.iaConfig();
        assertNotNull(iac);
        iac.setJson(json);
        TestIAJsonPropertiesUtil.override(iac);
        maMap.clear();
        for (String n : new String[] { "p.jpg", "q.jpg", "r.jpg" }) maMap.put(n, asset());
    }

    @AfterEach void clear() {
        BulkImportUtil.labeledKeywordMap = null;
        TestIAJsonPropertiesUtil.override(null);
    }

    static MediaAsset asset() {
        MediaAsset ma = new MediaAsset(0, mock(AssetStore.class), null);
        MediaAssetMetadata md = new MediaAssetMetadata();
        md.getData().put("attributes", new JSONObject().put("width", 4608).put("height", 3456).put("contentType", "image/jpeg"));
        ma.setMetadata(md);
        return ma;
    }

    static JSONObject src(String photo, Object... kv) {
        JSONObject r = new JSONObject().put(KEY, "G").put("Encounter.mediaAsset0", photo);
        for (int i = 0; i < kv.length; i += 2) r.put((String)kv[i], kv[i + 1]);
        return r;
    }

    static final Object[] BASE = { "Encounter.submitterID", "fakeSubmitterId", "Encounter.year", 2020,
        "Encounter.genus", "Genus", "Encounter.specificEpithet", "species" };

    static Object[] cat(Object[] a, Object... b) {
        Object[] o = new Object[a.length + b.length];
        System.arraycopy(a, 0, o, 0, a.length);
        System.arraycopy(b, 0, o, a.length, b.length);
        return o;
    }

    BulkImporter run(List<JSONObject> sourceRows) throws Exception {
        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class, (mockSh, context) -> {
                 when(mockSh.getContext()).thenReturn("context0");
                 when(mockSh.getPM()).thenReturn(mockPM);
                 when(mockSh.getUser(any(String.class))).thenReturn(user);
                 when(mockSh.getOrCreateOccurrence(any(String.class))).thenReturn(occ);
                 when(mockSh.getOrCreateOccurrence(null)).thenReturn(occ);
                 when(mockSh.isValidTaxonomyName(any(String.class))).thenReturn(true);
                 when(mockSh.getOrCreateLabeledKeyword(anyString(), anyString(), anyBoolean()))
                     .thenAnswer(inv -> new LabeledKeyword(inv.getArgument(0), inv.getArgument(1)));
             });
             MockedStatic<ShepherdPMF> pmf = mockStatic(ShepherdPMF.class);
             MockedStatic<FeatureType> ft = mockStatic(FeatureType.class);
             MockedStatic<MediaAsset> media = mockStatic(MediaAsset.class, CALLS_REAL_METHODS);
             MockedStatic<MarkedIndividual> mi = mockStatic(MarkedIndividual.class, CALLS_REAL_METHODS);
             MockedStatic<BulkImportUtil> util = mockStatic(BulkImportUtil.class, CALLS_REAL_METHODS)) {
            pmf.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
            ft.when(() -> FeatureType.load(eq("org.ecocean.boundingBox"), any(Shepherd.class))).thenReturn(new FeatureType("org.ecocean.boundingBox"));
            media.when(() -> MediaAsset.updateStandardChildrenBackground(anyString(), any(List.class), any(Runnable.class)))
                .thenAnswer(inv -> { ((Runnable)inv.getArgument(2)).run(); return null; });
            mi.when(() -> MarkedIndividual.withName(any(Shepherd.class), anyString(), any(), any()))
                .thenAnswer(inv -> existingByName);
            util.when(() -> BulkImportUtil.bulkOpensearchIndex(any(List.class))).thenAnswer(inv -> null);
            util.when(() -> BulkImportUtil.encounterIdsInUse(anyCollection(), any())).thenReturn(new HashSet<String>());
            Shepherd sh = new Shepherd("context0");
            GroupedRows g = GroupCollapse.collapse(sourceRows, sh, false, org.ecocean.api.bulk.BulkValidator.MAX_INDEX + 1);
            assertTrue(g.issues().isEmpty(), "collapse issues: " + g.issues());
            BulkImporter imp = new BulkImporter("import-grouped", g.mergedRows(), maMap, null, sh,
                g.exactSlotMode(), g.groupedMergedIndexes());
            imp.createImport();
            return imp;
        }
    }

    static List<JSONObject> rows(JSONObject... r) { return new ArrayList<JSONObject>(List.of(r)); }

    @Test void groupBecomesOneEncounterOneSighting() throws Exception {
        BulkImporter imp = run(rows(src("p.jpg", BASE), src("q.jpg"), src("r.jpg")));
        assertEquals(1, imp.getEncounters().size());
        assertEquals(3, imp.getEncounters().get(0).getAnnotations().size());
        verify(occ, times(1)).addEncounterAndUpdateIt(any(Encounter.class));
    }

    @Test void boxOnSecondRowUsesGroupSpecies() throws Exception {
        BulkImporter imp = run(rows(src("p.jpg", BASE),
            src("q.jpg", "Encounter.mediaAsset0.bbox", "10,20,300,400", "Encounter.mediaAsset0.iaClass", "body")));
        Encounter enc = imp.getEncounters().get(0);
        Annotation boxed = null;
        for (Annotation a : enc.getAnnotations()) if (a.isImportedBox()) boxed = a;
        assertNotNull(boxed, "the second row's box is imported");
        assertEquals("body", boxed.getIAClass());
        assertEquals("Genus species", enc.getTaxonomyString());
    }

    @Test void individualOnLastRowGetsEncounterSex() throws Exception {
        BulkImporter imp = run(rows(src("p.jpg", cat(BASE, "Encounter.sex", "female")), src("q.jpg"),
            src("r.jpg", "Encounter.individualID", "indA")));
        Encounter enc = imp.getEncounters().get(0);
        MarkedIndividual indiv = enc.getIndividual();
        assertNotNull(indiv);
        assertEquals("female", indiv.getSex());
        assertEquals("Genus species", indiv.getTaxonomyString());
    }

    @Test void ungroupedIndividualDerivationUnchanged() throws Exception {
        JSONObject r = new JSONObject().put("Encounter.mediaAsset0", "p.jpg");
        Object[] kv = cat(BASE, "Encounter.sex", "female", "Encounter.individualID", "indA");
        for (int i = 0; i < kv.length; i += 2) r.put((String)kv[i], kv[i + 1]);
        BulkImporter imp = run(rows(r));
        MarkedIndividual indiv = imp.getEncounters().get(0).getIndividual();
        assertNotNull(indiv);
        // control: an ungrouped new individual is created before its encounter has a sex
        assertEquals("unknown", indiv.getSex());
    }

    @Test void ownerOnlyOnLastRowIsUsed() throws Exception {
        Object[] noOwner = { "Encounter.year", 2020, "Encounter.genus", "Genus", "Encounter.specificEpithet", "species" };
        BulkImporter imp = run(rows(src("p.jpg", noOwner), src("q.jpg"),
            src("r.jpg", "Encounter.submitterID", "u2")));
        assertEquals("u2", imp.getEncounters().get(0).getSubmitterID());
    }

    @Test void groupKeyNeverStored() throws Exception {
        BulkImporter imp = run(rows(src("p.jpg", BASE), src("q.jpg")));
        Encounter enc = imp.getEncounters().get(0);
        assertFalse(enc.getId().equalsIgnoreCase("G"));
        // the key is a grouping label only: no encounter text field carries it
        assertFalse("G".equals(enc.getOccurrenceID()) || "G".equals(enc.getSubmitterID()) ||
            "G".equals(enc.getOccurrenceRemarks()) || "G".equals(enc.getAlternateID()) ||
            "G".equals(enc.getCatalogNumber()));
    }

    static MicrosatelliteMarkersAnalysis markers(Encounter enc) {
        for (TissueSample ts : enc.getTissueSamples())
            for (GeneticAnalysis ga : ts.getGeneticAnalyses())
                if (ga instanceof MicrosatelliteMarkersAnalysis) return (MicrosatelliteMarkersAnalysis)ga;
        return null;
    }

    static void assertTwoLoci(MicrosatelliteMarkersAnalysis m) {
        assertNotNull(m);
        List<Locus> loci = m.getLoci();
        assertEquals(2, loci.size());
        assertEquals("A", loci.get(0).getName());
        assertEquals(1, loci.get(0).getAllele(0));
        assertEquals(3, loci.get(0).getAllele(1));
        assertEquals("B", loci.get(1).getName());
        assertEquals(2, loci.get(1).getAllele(0));
        assertEquals(4, loci.get(1).getAllele(1));
    }

    @Test void genotypePersistsAllLoci() throws Exception {
        BulkImporter imp = run(rows(
            src("p.jpg", cat(BASE, "TissueSample.sampleID", "ts1", "MicrosatelliteMarkersAnalysis.alleleNames", "A, B",
                "MicrosatelliteMarkersAnalysis.alleles0", "1, 2", "MicrosatelliteMarkersAnalysis.alleles1", "3, 4")),
            src("q.jpg")));
        assertTwoLoci(markers(imp.getEncounters().get(0)));
    }

    @Test void paddedAlleleColumnsPersistAllLoci() throws Exception {
        BulkImporter imp = run(rows(
            src("p.jpg", cat(BASE, "TissueSample.sampleID", "ts1", "MicrosatelliteMarkersAnalysis.alleleNames", "A,B",
                "MicrosatelliteMarkersAnalysis.alleles00", "1,2", "MicrosatelliteMarkersAnalysis.alleles01", "3,4")),
            src("q.jpg")));
        assertTwoLoci(markers(imp.getEncounters().get(0)));
    }

    @Test void namesPersistOnGroupIndividual() throws Exception {
        BulkImporter imp = run(rows(src("p.jpg", BASE), src("q.jpg",
            "Encounter.individualID", "indA", "MarkedIndividual.name0.label", "alias",
            "MarkedIndividual.name0.value", "X")));
        MarkedIndividual indiv = imp.getEncounters().get(0).getIndividual();
        assertNotNull(indiv);
        assertEquals("X", indiv.getName("alias"));
    }

    @Test void existingIndividualSexPreserved() throws Exception {
        MarkedIndividual existing = new MarkedIndividual();
        existing.setSex("male");
        existingByName = existing;
        BulkImporter imp = run(rows(src("p.jpg", cat(BASE, "Encounter.sex", "female")), src("q.jpg"),
            src("r.jpg", "Encounter.individualID", "indA")));
        assertSame(existing, imp.getEncounters().get(0).getIndividual());
        assertEquals("male", existing.getSex());
    }
}
