package org.ecocean.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

import java.io.IOException;
import org.apache.http.util.EntityUtils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.apache.http.HttpHost;
import org.ecocean.Annotation;
import org.ecocean.CommonConfiguration;
import org.ecocean.Encounter;
import org.ecocean.MarkedIndividual;
import org.ecocean.OpenSearch;
import org.ecocean.Role;
import org.ecocean.User;
import org.ecocean.security.PermissionsAudit.Config;
import org.ecocean.security.PermissionsAudit.Result;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.shepherd.core.TestPMFUtil;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opensearch.client.Request;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * The permissions audit against a real PostgreSQL and a real OpenSearch 3.1: documents are indexed
 * by the REAL serializers, then drifted by hand, and the audit must repair exactly that drift so the
 * token-path reader (OpenSearch.applyAclFilter) admits and denies the right users again. Also the
 * OpenSearch helpers the audit relies on (doc-values scroll across pages, conditional writes, the
 * deployed mapping), which nothing else exercises against a cluster.
 */
@Testcontainers class PermissionsAuditIntegrationTest {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
        .withDatabaseName("wildbook_test").withUsername("wildbook").withPassword("wildbook");

    @Container static GenericContainer<?> opensearch = new GenericContainer<>(DockerImageName.parse(
        "opensearchproject/opensearch:3.1.0"))
            .withExposedPorts(9200)
            .withEnv("discovery.type", "single-node")
            .withEnv("plugins.security.disabled", "true")
            .withEnv("OPENSEARCH_JAVA_OPTS", "-Xms512m -Xmx512m")
            .withEnv("DISABLE_INSTALL_DEMO_CONFIG", "true")
            .waitingFor(new HttpWaitStrategy().forPort(9200).forPath("/_cluster/health")
            .forStatusCode(200).withStartupTimeout(java.time.Duration.ofMinutes(3)));

    private static final Config CONFIG = new Config(2, 20000, 500, "1m"); // two hits per page
    private static Properties props;
    private static JSONObject previousTree;
    private static boolean hadPreviousConfig;
    private static Properties previousConfig;
    private static final Map<String, String> uuid = new HashMap<String, String>();
    private static final String[] ENCOUNTERS = { "e1", "e2", "e3", "e4" };
    private static final String[] ANNOTATIONS = { "a1", "a2", "a3", "a4" };
    private static final List<String> INDIVIDUALS = new ArrayList<String>();

    @SuppressWarnings("unchecked")
    private static Map<String, Properties> configCache() throws Exception {
        Field f = CommonConfiguration.class.getDeclaredField("contextToPropsCache");
        f.setAccessible(true);
        return (Map<String, Properties>)f.get(null);
    }

    @BeforeAll static void setUp() throws Exception {
        previousTree = LocationRoleTestTree.inject(); // Indonesia > Komodo; Atlantis is not a node
        hadPreviousConfig = configCache().containsKey("context0");
        previousConfig = configCache().get("context0");
        Properties cfg = new Properties();
        cfg.setProperty("collaborationSecurityEnabled", "true");
        cfg.setProperty("releaseDateFormat", "yyyy-MM-dd");
        cfg.setProperty("htmlTitle", "Unit Test");
        CommonConfiguration.initialize("context0", cfg);

        OpenSearch.initializeClient(new HttpHost(opensearch.getHost(),
            opensearch.getMappedPort(9200), "http"));
        OpenSearch.INDEX_EXISTS_CACHE.clear();
        OpenSearch.PIT_CACHE.clear();
        TestPMFUtil.closePMF("context0");

        props = new Properties();
        props.setProperty("datanucleus.ConnectionUserName", postgres.getUsername());
        props.setProperty("datanucleus.ConnectionPassword", postgres.getPassword());
        props.setProperty("datanucleus.ConnectionDriverName", postgres.getDriverClassName());
        props.setProperty("datanucleus.ConnectionURL", postgres.getJdbcUrl());
        props.setProperty("datanucleus.schema.autoCreateTables", "true");
        seed();

        OpenSearch os = new OpenSearch();
        os.ensureIndex("encounter", new Encounter().opensearchMapping());
        os.ensureIndex("annotation", new Annotation().opensearchMapping());
        os.ensureIndex("individual", new MarkedIndividual().opensearchMapping());
    }

    @AfterAll static void tearDown() throws Exception {
        TestPMFUtil.closePMF("context0");
        OpenSearch.INDEX_EXISTS_CACHE.clear();
        OpenSearch.PIT_CACHE.clear();
        if (hadPreviousConfig) configCache().put("context0", previousConfig);
        else configCache().remove("context0");
        LocationRoleTestTree.restore(previousTree);
    }

    // ---- fixture: owner owns e1/e4, bob holds Indonesia (covers Komodo) and owns e3, amy
    // collaborates with owner, xavier has nothing; e2 is anonymous ("public") ----

    private static void seed() throws Exception {
        Shepherd sh = new Shepherd("context0", props);
        sh.setAction("PermissionsAuditIntegrationTest.seed");
        try {
            sh.beginDBTransaction();
            for (String u : new String[] { "owner", "bob", "amy", "xavier" }) {
                User user = new User(u, "pw", "salt");
                sh.getPM().makePersistent(user);
                uuid.put(u, user.getId());
            }
            Role r = new Role("bob", "Indonesia");
            r.setContext("context0");
            sh.getPM().makePersistent(r);
            Collaboration c = new Collaboration("owner", "amy");
            c.setState(Collaboration.STATE_APPROVED);
            sh.getPM().makePersistent(c);
            Encounter e1 = encounter(sh, "e1", "owner", "Komodo");
            Encounter e2 = encounter(sh, "e2", "public", "Komodo");
            Encounter e3 = encounter(sh, "e3", "bob", "Atlantis");
            Encounter e4 = encounter(sh, "e4", "owner", "Atlantis");
            annotation(sh, "a1", e1);
            annotation(sh, "a2", e2);
            annotation(sh, "a3", e3);
            annotation(sh, "a4", null); // eligible orphan
            INDIVIDUALS.add(individual(sh, e1, e2));
            INDIVIDUALS.add(individual(sh, e3));
            INDIVIDUALS.add(individual(sh, e4));
            sh.commitDBTransaction();
        } catch (Exception ex) {
            sh.rollbackDBTransaction();
            throw ex;
        } finally {
            sh.closeDBTransaction();
        }
    }

    private static Encounter encounter(Shepherd sh, String id, String submitter, String locationID) {
        Encounter enc = new Encounter();
        enc.setCatalogNumber(id);
        enc.setSubmitterID(submitter);
        enc.setLocationID(locationID);
        enc.setSkipAutoIndexing(true); // no indexing queue during seeding
        sh.getPM().makePersistent(enc);
        return enc;
    }

    private static void annotation(Shepherd sh, String id, Encounter enc) {
        Annotation ann = new Annotation();
        ann.setId(id);
        ann.setMatchAgainst(true); // eligible for the annotation index
        ann.setSkipAutoIndexing(true);
        sh.getPM().makePersistent(ann);
        if (enc != null) enc.addAnnotation(ann);
    }

    private static String individual(Shepherd sh, Encounter... members) {
        MarkedIndividual indiv = new MarkedIndividual();
        indiv.setSkipAutoIndexing(true);
        for (Encounter enc : members) indiv.addEncounter(enc);
        sh.getPM().makePersistent(indiv);
        return indiv.getId();
    }

    // ---- index helpers ----

    private static OpenSearch os() {
        return new OpenSearch();
    }

    /** Index every fixture object through its REAL serializer (the state the audit must accept). */
    private static void reindexAll() throws Exception {
        OpenSearch os = os();
        Shepherd sh = new Shepherd("context0");
        sh.setAction("PermissionsAuditIntegrationTest.reindexAll");
        try {
            sh.beginDBTransaction();
            for (String id : ENCOUNTERS) os.index("encounter", sh.getEncounter(id));
            for (String id : ANNOTATIONS) os.index("annotation", sh.getAnnotation(id));
            for (String id : INDIVIDUALS) os.index("individual", sh.getMarkedIndividual(id));
        } finally {
            sh.rollbackAndClose();
        }
        refresh();
    }

    private static void refresh() throws Exception {
        OpenSearch os = os();
        for (String index : new String[] { "encounter", "annotation", "individual" }) {
            os.getRestResponse(new Request("POST", "/" + index + "/_refresh"));
        }
    }

    /** Unconditional partial update: the drift a stale writer would leave behind. */
    private static void drift(String index, String id, JSONObject partial) throws Exception {
        Request req = new Request("POST", "/" + index + "/_update/" + id);
        req.setJsonEntity(new JSONObject().put("doc", partial).toString());
        os().getRestResponse(req);
        refresh();
    }

    private static JSONObject source(String index, String id) throws Exception {
        String rtn = os().getRestResponse(new Request("GET", "/" + index + "/_doc/" + id));
        return new JSONObject(rtn).getJSONObject("_source");
    }

    private static JSONObject meta(String index, String id) throws Exception {
        String rtn = os().getRestResponse(new Request("GET", "/" + index + "/_doc/" + id));
        return new JSONObject(rtn);
    }

    /** What the token-path reader lets this user see in an index. */
    private static Set<String> visible(String index, String userId) throws Exception {
        JSONObject query = new JSONObject().put("query", new JSONObject().put("match_all",
            new JSONObject()));
        query = OpenSearch.applyAclFilter(query, userId, index);
        query.put("size", 50);
        Request req = new Request("POST", "/" + index + "/_search");
        req.setJsonEntity(query.toString());
        JSONObject res = new JSONObject(os().getRestResponse(req));
        JSONArray hits = res.getJSONObject("hits").getJSONArray("hits");
        Set<String> ids = new HashSet<String>();
        for (int i = 0; i < hits.length(); i++) ids.add(hits.getJSONObject(i).getString("_id"));
        return ids;
    }

    private static Set<String> set(String... s) {
        return new HashSet<String>(Arrays.asList(s));
    }

    private static Set<String> strings(JSONArray a) {
        Set<String> s = new HashSet<String>();
        if (a != null) for (int i = 0; i < a.length(); i++) s.add(a.getString(i));
        return s;
    }

    private static Result audit() {
        Result r = PermissionsAudit.run("context0", CONFIG);
        return r;
    }

    private static int repaired(Result r) {
        int n = 0;
        for (PermissionsAudit.IndexCounters c : r.indices.values()) n += c.repaired;
        return n;
    }

    // ================= the audit end to end =================

    @Test void quietAuditAcrossSeveralPagesWritesNothing() throws Exception {
        reindexAll();
        Result r = audit();
        assertTrue(r.completed, r.toString());
        assertEquals(0, repaired(r), r.toString());
        assertEquals(4, r.index("encounter").scanned);
        assertEquals(4, r.index("annotation").scanned);
        assertEquals(3, r.index("individual").scanned);
        // the real serializers already agree with the audit's expectations
        assertEquals(set("a1", "a2", "a3"), visible("annotation", uuid.get("bob")),
            "bob: Komodo role covers e1; a2 public; bob owns e3");
        assertEquals(set("a1", "a2"), visible("annotation", uuid.get("amy")), "amy: collaborator of owner; a2 public");
        assertEquals(set("a2"), visible("annotation", uuid.get("xavier")), "xavier: only the public one");
    }

    @Test void viewerDriftOnAChildIsRepairedAndTheReaderAdmitsTheUserAgain() throws Exception {
        reindexAll();
        drift("annotation", "a1", new JSONObject().put("viewUsers", new JSONArray()));
        assertFalse(visible("annotation", uuid.get("bob")).contains("a1"), "drifted: bob lost a1");

        Result r = audit();
        refresh();
        assertTrue(r.completed, r.toString());
        assertEquals(1, r.index("annotation").repaired);
        assertTrue(visible("annotation", uuid.get("bob")).contains("a1"), "repaired");
        assertEquals(0, repaired(audit()), "a second audit is quiet");
    }

    @Test void revokedGrantOnAChildIsRemoved() throws Exception {
        reindexAll();
        drift("annotation", "a1", new JSONObject().put("viewUsers",
            new JSONArray(Arrays.asList(uuid.get("bob"), uuid.get("amy"), uuid.get("xavier")))));
        assertTrue(visible("annotation", uuid.get("xavier")).contains("a1"), "drifted: xavier sees a1");

        audit();
        refresh();
        assertFalse(visible("annotation", uuid.get("xavier")).contains("a1"), "revoked grant removed");
        assertTrue(visible("annotation", uuid.get("bob")).contains("a1"), "legitimate grant kept");
    }

    @Test void encounterOwnerAndPublicFlagDriftIsRepaired() throws Exception {
        reindexAll();
        drift("encounter", "e1", new JSONObject().put("publiclyReadable", true)
            .put("submitterUserId", uuid.get("xavier")));
        assertTrue(visible("encounter", uuid.get("xavier")).contains("e1"), "drifted: public and owned by xavier");

        Result r = audit();
        refresh();
        assertEquals(1, r.index("encounter").repaired);
        JSONObject src = source("encounter", "e1");
        assertEquals(false, src.getBoolean("publiclyReadable"));
        assertEquals(uuid.get("owner"), src.getString("submitterUserId"));
        assertFalse(visible("encounter", uuid.get("xavier")).contains("e1"));
        assertTrue(visible("encounter", uuid.get("owner")).contains("e1"));
    }

    @Test void annotationNamingTheWrongParentIsDeniedThenRebuiltFromTheSerializer() throws Exception {
        reindexAll();
        // e3's ACL (bob owns it, nobody else) on a document that is really e1's
        drift("annotation", "a1", new JSONObject().put("encounterId", "e3")
            .put("submitterUserIds", new JSONArray().put(uuid.get("bob"))).put("viewUsers", new JSONArray()));

        Result first = audit();
        refresh();
        assertEquals(1, first.index("annotation").structural);
        assertFalse(first.completed, "a structural repair is verified by the next audit");
        JSONObject src = source("annotation", "a1");
        assertEquals("e1", src.getString("encounterId"), "rebuilt from the serializer");
        assertEquals(set(uuid.get("owner")), strings(src.getJSONArray("submitterUserIds")));
        assertEquals(set(uuid.get("bob"), uuid.get("amy")), strings(src.getJSONArray("viewUsers")));

        Result second = audit();
        assertTrue(second.completed, second.toString());
        assertEquals(0, second.index("annotation").structural);
    }

    @Test void eligibleOrphanAnnotationIsDenied() throws Exception {
        reindexAll();
        drift("annotation", "a4", new JSONObject().put("publiclyReadable", true));
        assertTrue(visible("annotation", uuid.get("xavier")).contains("a4"), "drifted: public");

        Result r = audit();
        refresh();
        assertEquals(1, r.index("annotation").repaired);
        assertFalse(visible("annotation", uuid.get("xavier")).contains("a4"));
        JSONObject src = source("annotation", "a4");
        assertEquals(false, src.getBoolean("publiclyReadable"));
        assertEquals(0, src.getJSONArray("submitterUserIds").length());
    }

    @Test void individualMembershipDriftIsDeniedThenRebuilt() throws Exception {
        reindexAll();
        String i1 = INDIVIDUALS.get(0); // members e1 (owner, private) + e2 (public)
        drift("individual", i1, new JSONObject().put("encounterIds", new JSONArray().put("e1"))
            .put("publiclyReadable", false).put("viewUsers", new JSONArray()));

        Result first = audit();
        refresh();
        assertEquals(1, first.index("individual").structural);
        JSONObject src = source("individual", i1);
        assertEquals(set("e1", "e2"), strings(src.getJSONArray("encounterIds")));
        assertEquals(true, src.getBoolean("publiclyReadable"), "e2 is public");
        assertEquals(set(uuid.get("owner")), strings(src.getJSONArray("submitterUserIds")));
        assertEquals(set(uuid.get("bob"), uuid.get("amy")), strings(src.getJSONArray("viewUsers")));
        assertTrue(audit().completed);
    }

    @Test void driftReintroducedAfterARepairIsRepairedAgainByTheNextAudit() throws Exception {
        // the documented eventual-consistency contract: a stale writer landing after a repair is
        // simply re-detected; nothing is acknowledged
        reindexAll();
        drift("annotation", "a1", new JSONObject().put("viewUsers", new JSONArray()));
        audit();
        drift("annotation", "a1", new JSONObject().put("viewUsers", new JSONArray()));
        Result again = audit();
        refresh();
        assertEquals(1, again.index("annotation").repaired);
        assertTrue(visible("annotation", uuid.get("bob")).contains("a1"));
    }

    // ================= the OpenSearch helpers =================

    @Test void conditionalWritesApplyOnTheCurrentVersionAndConflictOnAStaleOne() throws Exception {
        reindexAll();
        JSONObject m = meta("encounter", "e2");
        long seqNo = m.getLong("_seq_no");
        long primaryTerm = m.getLong("_primary_term");
        OpenSearch os = os();

        OpenSearch.ConditionalWrite stale = os.updateIfUnchanged("encounter", "e2",
            new JSONObject().put("viewUsers", new JSONArray().put("x")), seqNo + 1000, primaryTerm);
        assertFalse(stale.applied, "a version that is not the current one conflicts");
        assertTrue(strings(source("encounter", "e2").optJSONArray("viewUsers")).isEmpty(), "nothing written");

        OpenSearch.ConditionalWrite ok = os.updateIfUnchanged("encounter", "e2",
            new JSONObject().put("viewUsers", new JSONArray().put("x")), seqNo, primaryTerm);
        assertTrue(ok.applied);
        assertTrue(ok.seqNo > seqNo, "the new sequence number is returned");
        assertEquals(set("x"), strings(source("encounter", "e2").getJSONArray("viewUsers")));

        OpenSearch.ConditionalWrite replaced = os.putIfUnchanged("encounter", "e2",
            source("encounter", "e2").put("viewUsers", new JSONArray()).toString(), ok.seqNo, ok.primaryTerm);
        assertTrue(replaced.applied);
        OpenSearch.ConditionalWrite late = os.putIfUnchanged("encounter", "e2",
            source("encounter", "e2").put("viewUsers", new JSONArray().put("y")).toString(), ok.seqNo, ok.primaryTerm);
        assertFalse(late.applied, "the earlier version is gone");
        assertTrue(strings(source("encounter", "e2").optJSONArray("viewUsers")).isEmpty());
    }

    @Test void docValuesScrollReadsEveryDocumentOnceAcrossPagesWithConcurrencyMetadata() throws Exception {
        reindexAll();
        final List<JSONObject> hits = new ArrayList<JSONObject>();
        final int[] pages = { 0 };
        os().scrollDocValues("annotation", Arrays.asList("publiclyReadable", "submitterUserIds",
            "viewUsers", "encounterId"), 2, "1m", new OpenSearch.DocValuesPageConsumer() {
            public void accept(JSONArray page) {
                pages[0]++;
                for (int i = 0; i < page.length(); i++) hits.add(page.getJSONObject(i));
            }
        });
        assertEquals(4, hits.size());
        assertTrue(pages[0] >= 2, "two hits per page");
        Set<String> ids = new HashSet<String>();
        for (JSONObject h : hits) {
            ids.add(h.getString("_id"));
            PermissionsAudit.IndexedDoc d = PermissionsAudit.parseHit(h, "submitterUserIds");
            assertEquals(null, d.error, h.toString());
            assertNotNull(d.publiclyReadable, h.toString());
        }
        assertEquals(set("a1", "a2", "a3", "a4"), ids);
    }

    @Test void deployedMappingsReportTheTypesTheAuditNeeds() throws Exception {
        Map<String, JSONObject> m = os().fieldMappings("annotation");
        assertEquals("keyword", m.get("viewUsers").getString("type"));
        assertEquals("keyword", m.get("submitterUserIds").getString("type"));
        assertEquals("keyword", m.get("encounterId").getString("type"));
        assertEquals("boolean", m.get("publiclyReadable").getString("type"));
        assertEquals(null, PermissionsAudit.checkMapping(m, Arrays.asList("publiclyReadable",
            "submitterUserIds", "viewUsers", "encounterId")));
    }

    // ================= round-17 =================

    @Test void denyIsVisibleBeforeTheRebuildWhenTheRebuildBudgetIsExhausted() throws Exception {
        reindexAll();
        drift("annotation", "a1", new JSONObject().put("encounterId", "e3")
            .put("submitterUserIds", new JSONArray().put(uuid.get("bob"))).put("viewUsers", new JSONArray()));
        assertTrue(visible("annotation", uuid.get("bob")).contains("a1"), "drifted: bob as owner");

        Result first = PermissionsAudit.run("context0", new Config(2, 20000, 0, "1m")); // no rebuild budget
        refresh();
        assertEquals(1, first.index("annotation").structural);
        assertEquals(1, first.index("annotation").deferred);
        assertFalse(visible("annotation", uuid.get("bob")).contains("a1"), "closed until rebuilt");
        assertFalse(visible("annotation", uuid.get("xavier")).contains("a1"));

        Result second = audit();
        refresh();
        assertEquals(1, second.index("annotation").structural);
        assertEquals("e1", source("annotation", "a1").getString("encounterId"));
        assertTrue(visible("annotation", uuid.get("bob")).contains("a1"), "rebuilt: bob through the Komodo role");
    }

    @Test void staleFullReplacementAfterARepairIsRepairedAgain() throws Exception {
        reindexAll();
        JSONObject stale = source("annotation", "a1").put("viewUsers", new JSONArray());
        drift("annotation", "a1", new JSONObject().put("viewUsers", new JSONArray()));
        audit();
        Request put = new Request("PUT", "/annotation/_doc/a1"); // a stale writer replaces the whole document
        put.setJsonEntity(stale.toString());
        os().getRestResponse(put);
        refresh();
        assertFalse(visible("annotation", uuid.get("bob")).contains("a1"), "stale again");

        Result again = audit();
        refresh();
        assertEquals(1, again.index("annotation").repaired);
        assertTrue(visible("annotation", uuid.get("bob")).contains("a1"));
    }

    @Test void serializerThenAuditThenSerializerThenAuditConverges() throws Exception {
        reindexAll();
        Result a = audit();
        assertEquals(0, repaired(a), a.toString());
        assertTrue(a.completed);
        reindexAll();
        Result b = audit();
        assertEquals(0, repaired(b), b.toString());
        assertTrue(b.completed);
    }

    @Test void mappingWithANullValueIsReportedAndRejected() throws Exception {
        OpenSearch os = os();
        String index = "individual"; // re-created with a null_value on the owner field, then restored
        os.getRestResponse(new Request("DELETE", "/" + index));
        OpenSearch.INDEX_EXISTS_CACHE.remove(index);
        Request create = new Request("PUT", "/" + index);
        create.setJsonEntity(new JSONObject().put("mappings", new JSONObject().put("properties", new JSONObject()
            .put("publiclyReadable", new JSONObject().put("type", "boolean"))
            .put("viewUsers", new JSONObject().put("type", "keyword"))
            .put("encounterIds", new JSONObject().put("type", "keyword"))
            .put("submitterUserIds", new JSONObject().put("type", "keyword").put("null_value", "someone")))).toString());
        os.getRestResponse(create);
        try {
            Map<String, JSONObject> m = os.fieldMappings(index);
            assertEquals("someone", m.get("submitterUserIds").getString("null_value"));
            assertNotNull(PermissionsAudit.checkMapping(m, Arrays.asList("publiclyReadable",
                "submitterUserIds", "viewUsers", "encounterIds")));
            Result r = audit();
            assertEquals(0, r.index(index).scanned, "not audited with that mapping");
            assertFalse(r.completed);
        } finally {
            os.getRestResponse(new Request("DELETE", "/" + index));
            OpenSearch.INDEX_EXISTS_CACHE.remove(index);
            os.ensureIndex(index, new MarkedIndividual().opensearchMapping());
            reindexAll();
        }
    }

    @Test void scrollContextIsClearedEvenWhenTheFirstPageIsRejected() throws Exception {
        final List<Request> seen = new ArrayList<Request>();
        OpenSearch os = spy(os());
        doAnswer(inv -> {
            Request req = inv.getArgument(0);
            seen.add(req);
            if ("DELETE".equals(req.getMethod())) return "{\"succeeded\":true}";
            return new JSONObject().put("timed_out", true).put("_scroll_id", "abc")
                       .put("_shards", new JSONObject().put("failed", 0))
                       .put("hits", new JSONObject().put("hits", new JSONArray())).toString();
        }).when(os).getRestResponse(any(Request.class));

        assertThrows(IOException.class, () -> os.scrollDocValues("annotation", Arrays.asList("viewUsers"),
            2, "1m", hits -> {}));
        boolean cleared = false;
        for (Request r : seen) {
            if ("DELETE".equals(r.getMethod()) && r.getEndpoint().contains("_search/scroll") &&
                EntityUtils.toString(r.getEntity()).contains("abc")) cleared = true;
        }
        assertTrue(cleared, "the rejected page's scroll id must still be cleared: " + seen);
    }

    // ================= round-18 =================

    @Test void writeBetweenTheAuditsReadAndItsRepairIsAConflictRepairedByTheNextAudit() throws Exception {
        reindexAll();
        drift("annotation", "a1", new JSONObject().put("viewUsers", new JSONArray()));
        OpenSearch os = spy(os());
        // a concurrent writer touches a1 after the audit read it and before it repairs it
        doAnswer(inv -> {
            drift("annotation", "a1", new JSONObject().put("viewUsers", new JSONArray().put("someone")));
            return inv.callRealMethod();
        }).when(os).updateIfUnchanged(eq("annotation"), eq("a1"), any(JSONObject.class), anyLong(), anyLong());

        Result r = PermissionsAudit.run("context0", CONFIG, os);
        assertEquals(1, r.index("annotation").conflicts, r.toString());
        assertEquals(0, r.index("annotation").failed);
        assertFalse(r.completed, "a conflict leaves the audit incomplete");
        refresh();
        assertFalse(visible("annotation", uuid.get("bob")).contains("a1"), "the stale write stood");

        Result next = audit();
        refresh();
        assertEquals(1, next.index("annotation").repaired, "the next audit repairs it");
        assertTrue(visible("annotation", uuid.get("bob")).contains("a1"));
    }

    @Test void scrollClearsTheLatestIdWhenALaterPageIsRejected() throws Exception {
        final List<Request> seen = new ArrayList<Request>();
        OpenSearch os = spy(os());
        doAnswer(inv -> {
            Request req = inv.getArgument(0);
            seen.add(req);
            if ("DELETE".equals(req.getMethod())) return "{\"succeeded\":true}";
            if (req.getEndpoint().contains("/_search/scroll")) { // the continuation: rejected
                return new JSONObject().put("timed_out", true).put("_scroll_id", "p2")
                           .put("_shards", new JSONObject().put("failed", 0))
                           .put("hits", new JSONObject().put("hits", new JSONArray())).toString();
            }
            JSONObject hit = new JSONObject().put("_id", "a1").put("_seq_no", 1).put("_primary_term", 1)
                .put("fields", new JSONObject().put("viewUsers", new JSONArray()));
            return new JSONObject().put("timed_out", false).put("_scroll_id", "p1")
                       .put("_shards", new JSONObject().put("failed", 0))
                       .put("hits", new JSONObject().put("hits", new JSONArray().put(hit))).toString();
        }).when(os).getRestResponse(any(Request.class));

        final int[] pagesSeen = { 0 };
        assertThrows(IOException.class, () -> os.scrollDocValues("annotation", Arrays.asList("viewUsers"),
            2, "1m", hits -> pagesSeen[0]++));
        assertEquals(1, pagesSeen[0], "the good first page was delivered");
        boolean clearedLatest = false;
        for (Request r : seen) {
            if ("DELETE".equals(r.getMethod()) && EntityUtils.toString(r.getEntity()).contains("p2")) clearedLatest = true;
        }
        assertTrue(clearedLatest, "the LATEST id (the rejected page's) is cleared: " + seen);
        boolean partialRefused = false;
        for (Request r : seen) {
            if ("POST".equals(r.getMethod()) && r.getEndpoint().endsWith("/_search") &&
                "false".equals(r.getParameters().get("allow_partial_search_results"))) partialRefused = true;
        }
        assertTrue(partialRefused, "partial results are refused on the request");
    }

    private static String firstPage(String scrollId) {
        JSONObject hit = new JSONObject().put("_id", "a1").put("_seq_no", 1).put("_primary_term", 1)
            .put("fields", new JSONObject().put("viewUsers", new JSONArray()));
        return new JSONObject().put("timed_out", false).put("_scroll_id", scrollId)
                   .put("_shards", new JSONObject().put("failed", 0))
                   .put("hits", new JSONObject().put("hits", new JSONArray().put(hit))).toString();
    }

    private static boolean cleared(List<Request> seen, String scrollId) throws Exception {
        for (Request r : seen) {
            if ("DELETE".equals(r.getMethod()) && EntityUtils.toString(r.getEntity()).contains(scrollId)) return true;
        }
        return false;
    }

    @Test void scrollContextIsClearedWhenTheContinuationRequestFails() throws Exception {
        final List<Request> seen = new ArrayList<Request>();
        OpenSearch os = spy(os());
        doAnswer(inv -> {
            Request req = inv.getArgument(0);
            seen.add(req);
            if ("DELETE".equals(req.getMethod())) return "{\"succeeded\":true}";
            if (req.getEndpoint().contains("/_search/scroll")) throw new IOException("connection reset");
            return firstPage("h1");
        }).when(os).getRestResponse(any(Request.class));

        assertThrows(IOException.class, () -> os.scrollDocValues("annotation", Arrays.asList("viewUsers"),
            2, "1m", hits -> {}));
        assertTrue(cleared(seen, "h1"), "an HTTP failure on a continuation still clears the context: " + seen);
    }

    @Test void scrollContextIsClearedWhenAPageIsMalformed() throws Exception {
        final List<Request> seen = new ArrayList<Request>();
        OpenSearch os = spy(os());
        doAnswer(inv -> {
            Request req = inv.getArgument(0);
            seen.add(req);
            if ("DELETE".equals(req.getMethod())) return "{\"succeeded\":true}";
            return new JSONObject().put("timed_out", false).put("_scroll_id", "m1")
                       .put("_shards", new JSONObject().put("failed", 0)).toString(); // no hits envelope
        }).when(os).getRestResponse(any(Request.class));

        assertThrows(IOException.class, () -> os.scrollDocValues("annotation", Arrays.asList("viewUsers"),
            2, "1m", hits -> {}));
        assertTrue(cleared(seen, "m1"), "a malformed page still clears its context: " + seen);
    }

    @Test void repairCapOnARealIndexLeavesTheRestForTheNextAudit() throws Exception {
        reindexAll();
        drift("annotation", "a1", new JSONObject().put("viewUsers", new JSONArray()));
        drift("annotation", "a3", new JSONObject().put("viewUsers", new JSONArray().put("someone")));

        Result capped = PermissionsAudit.run("context0", new Config(2, 1, 500, "1m"));
        refresh();
        assertEquals(1, capped.index("annotation").repaired);
        assertTrue(capped.capHit);
        assertFalse(capped.completed);

        Result rest = audit();
        refresh();
        assertEquals(1, rest.index("annotation").repaired);
        assertTrue(rest.completed, rest.toString());
        assertTrue(visible("annotation", uuid.get("bob")).contains("a1"));
        assertFalse(visible("annotation", "someone").contains("a3"));
    }

    @Test void realSkipWindowDefersTheRebuildAndTheNextAuditRebuilds() throws Exception {
        java.io.File skip = new java.io.File("/tmp/skipAutoIndexing");
        boolean preexisting = skip.exists();
        reindexAll();
        drift("annotation", "a1", new JSONObject().put("encounterId", "e3")
            .put("submitterUserIds", new JSONArray().put(uuid.get("bob"))).put("viewUsers", new JSONArray()));
        try {
            assertTrue(preexisting || skip.createNewFile());
            Result first = audit();
            refresh();
            assertEquals(1, first.index("annotation").structural);
            assertEquals(1, first.index("annotation").deferred);
            assertFalse(visible("annotation", uuid.get("bob")).contains("a1"), "closed, not rebuilt");
        } finally {
            if (!preexisting) skip.delete();
        }
        Result second = audit();
        refresh();
        assertEquals("e1", source("annotation", "a1").getString("encounterId"));
        assertTrue(visible("annotation", uuid.get("bob")).contains("a1"));
    }
}
