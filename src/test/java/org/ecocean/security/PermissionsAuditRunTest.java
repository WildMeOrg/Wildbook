package org.ecocean.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonGenerator;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.jdo.PersistenceManager;
import javax.jdo.Query;
import javax.jdo.Transaction;
import org.ecocean.Annotation;
import org.ecocean.MarkedIndividual;
import org.ecocean.OpenSearch;
import org.ecocean.Organization;
import org.ecocean.Role;
import org.ecocean.User;
import org.ecocean.security.PermissionsAudit.Config;
import org.ecocean.security.PermissionsAudit.Result;
import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

/**
 * PermissionsAudit.run(): one repeatable-read snapshot, then a doc-values scroll of each index,
 * conditional repairs of ACL drift, deny-first handling of structural drift followed by a verified
 * rebuild, and a completion status that only selects the next delay (nothing is acknowledged).
 * The OpenSearch side is mocked at the helper level (pages in, conditional writes out).
 */
class PermissionsAuditRunTest {
    private JSONObject previousTree;
    // database fixture
    private List<User> users;
    private Map<String, User> usersByName;
    private List<Role> roles;
    private List<Collaboration> collabs;
    private List<Organization> orgs;
    private List<String> orgAdminUsernames;
    private List<Object[]> encounterRows; // id, submitter, locationID
    private List<Object[]> annotationRows; // id (eligible)
    private List<Object[]> linkRows; // annotation id, encounter id
    private List<Object[]> individualRows; // id
    private List<Object[]> memberRows; // individual id, encounter id
    private Map<String, Annotation> annotations; // for rebuilds
    private Map<String, MarkedIndividual> individuals;
    private String isolationLevel;
    private boolean securityEnabled;
    // index fixture
    private Map<String, List<JSONArray> > pages; // index -> scroll pages (arrays of hits)
    private Map<String, Map<String, JSONObject> > mappings; // index -> field -> mapping
    private Set<String> scrollFails; // indices whose scroll throws
    private Set<String> scrollInterruptOn; // indices whose scroll reports an interruption (flag clear)
    private boolean rebuildShepherdFails; // the repair Shepherd cannot begin a transaction
    private Set<String> conflictOn; // ids whose conditional write returns a conflict
    private Set<String> conflictOnPut; // ids whose conditional REPLACE (rebuild) returns a conflict
    private Map<String, Runnable> onWrite; // id -> something that happens right after its write lands
    private Set<String> failOn; // ids whose conditional write throws
    private boolean skipAutoIndexing;
    // observations
    private List<JSONObject> writes; // {kind, index, id, doc|json, seqNo, primaryTerm}

    @BeforeEach void setUp() {
        previousTree = LocationRoleTestTree.inject();
        users = new ArrayList<User>();
        usersByName = new HashMap<String, User>();
        roles = new ArrayList<Role>();
        collabs = new ArrayList<Collaboration>();
        orgs = new ArrayList<Organization>();
        orgAdminUsernames = new ArrayList<String>();
        encounterRows = new ArrayList<Object[]>();
        annotationRows = new ArrayList<Object[]>();
        linkRows = new ArrayList<Object[]>();
        individualRows = new ArrayList<Object[]>();
        memberRows = new ArrayList<Object[]>();
        annotations = new HashMap<String, Annotation>();
        individuals = new HashMap<String, MarkedIndividual>();
        isolationLevel = "repeatable-read";
        securityEnabled = true;
        pages = new HashMap<String, List<JSONArray> >();
        mappings = new HashMap<String, Map<String, JSONObject> >();
        for (String index : new String[] { "encounter", "annotation", "individual" }) {
            pages.put(index, new ArrayList<JSONArray>());
            mappings.put(index, goodMapping(index));
        }
        scrollFails = new HashSet<String>();
        scrollInterruptOn = new HashSet<String>();
        rebuildShepherdFails = false;
        conflictOn = new HashSet<String>();
        conflictOnPut = new HashSet<String>();
        onWrite = new HashMap<String, Runnable>();
        failOn = new HashSet<String>();
        skipAutoIndexing = false;
        writes = new ArrayList<JSONObject>();
        // the usual cast: owner owns, bob holds Indonesia (covers Komodo), amy collaborates
        user("owner", "uuid-O");
        user("bob", "uuid-B");
        user("amy", "uuid-A");
        role("bob", "Indonesia");
        collab("owner", "amy", Collaboration.STATE_APPROVED);
    }

    @AfterEach void tearDown() {
        LocationRoleTestTree.restore(previousTree);
    }

    // ---- fixture helpers ----

    private static Map<String, JSONObject> goodMapping(String index) {
        Map<String, JSONObject> m = new HashMap<String, JSONObject>();
        m.put("publiclyReadable", new JSONObject().put("type", "boolean"));
        m.put("viewUsers", new JSONObject().put("type", "keyword"));
        if ("encounter".equals(index)) {
            m.put("submitterUserId", new JSONObject().put("type", "keyword"));
        } else {
            m.put("submitterUserIds", new JSONObject().put("type", "keyword"));
            m.put("annotation".equals(index) ? "encounterId" : "encounterIds",
                new JSONObject().put("type", "keyword"));
        }
        return m;
    }

    private void user(String username, String id) {
        User u = mock(User.class);
        when(u.getUsername()).thenReturn(username);
        when(u.getId()).thenReturn(id);
        users.add(u);
        usersByName.put(username, u);
    }

    private void role(String username, String rolename) {
        Role r = new Role(username, rolename);
        r.setContext("context0");
        roles.add(r);
    }

    private void collab(String u1, String u2, String state) {
        Collaboration c = new Collaboration(u1, u2);
        c.setState(state);
        collabs.add(c);
    }

    private void encounter(String id, String submitter, String locationID) {
        encounterRows.add(new Object[] { id, submitter, locationID });
    }

    private void annotation(String id, String... parents) {
        annotationRows.add(new Object[] { id });
        for (String p : parents) linkRows.add(new Object[] { id, p });
    }

    private void individual(String id, String... members) {
        individualRows.add(new Object[] { id });
        for (String m : members) memberRows.add(new Object[] { id, m });
    }

    private static Set<String> set(String... s) {
        return new HashSet<String>(Arrays.asList(s));
    }

    private static JSONArray arr(String... s) {
        return new JSONArray(Arrays.asList(s));
    }

    /** A doc-values hit. ownerField is submitterUserId (encounter) or submitterUserIds (child). */
    private static JSONObject hit(String id, long seqNo, long primaryTerm, Boolean pub,
        String ownerField, JSONArray owners, JSONArray viewers) {
        JSONObject fields = new JSONObject();
        if (pub != null) fields.put("publiclyReadable", new JSONArray().put(pub.booleanValue()));
        if (owners != null) fields.put(ownerField, owners);
        if (viewers != null) fields.put("viewUsers", viewers);
        return new JSONObject().put("_id", id).put("_seq_no", seqNo).put("_primary_term", primaryTerm)
                   .put("fields", fields);
    }

    private JSONObject encounterHit(String id, Boolean pub, String owner, String... viewers) {
        JSONObject h = hit(id, 5, 1, pub, "submitterUserId", (owner == null) ? null : arr(owner),
            arr(viewers));
        return h;
    }

    private JSONObject childHit(String id, Boolean pub, JSONArray owners, JSONArray viewers,
        String linkField, JSONArray linkValues) {
        JSONObject h = hit(id, 7, 2, pub, "submitterUserIds", owners, viewers);
        if (linkValues != null) h.getJSONObject("fields").put(linkField, linkValues);
        return h;
    }

    private void page(String index, JSONObject... hits) {
        pages.get(index).add(new JSONArray(Arrays.asList(hits)));
    }

    /** An annotation the audit can load for a rebuild; its serializer writes the given document. */
    private Annotation rebuildableAnnotation(String id, final JSONObject document) throws IOException {
        Annotation ann = spy(new Annotation());
        doReturn(id).when(ann).getId();
        doReturn(true).when(ann).shouldIndexInOpenSearch();
        doAnswer(inv -> {
            writeFields((JsonGenerator)inv.getArgument(0), document);
            return null;
        }).when(ann).opensearchDocumentSerializer(any(JsonGenerator.class), any(Shepherd.class));
        annotations.put(id, ann);
        return ann;
    }

    /** As rebuildableAnnotation, with something that happens while the serializer runs. */
    private Annotation rebuildableAnnotation(String id, final JSONObject document, final Runnable whileSerializing)
    throws IOException {
        Annotation ann = spy(new Annotation());
        doReturn(id).when(ann).getId();
        doReturn(true).when(ann).shouldIndexInOpenSearch();
        doAnswer(inv -> {
            whileSerializing.run();
            writeFields((JsonGenerator)inv.getArgument(0), document);
            return null;
        }).when(ann).opensearchDocumentSerializer(any(JsonGenerator.class), any(Shepherd.class));
        annotations.put(id, ann);
        return ann;
    }

    private MarkedIndividual rebuildableIndividual(String id, final JSONObject document) throws IOException {
        MarkedIndividual indiv = spy(new MarkedIndividual());
        doReturn(id).when(indiv).getId();
        doReturn(true).when(indiv).shouldIndexInOpenSearch();
        doAnswer(inv -> {
            writeFields((JsonGenerator)inv.getArgument(0), document);
            return null;
        }).when(indiv).opensearchDocumentSerializer(any(JsonGenerator.class), any(Shepherd.class));
        individuals.put(id, indiv);
        return indiv;
    }

    private static void writeFields(JsonGenerator jgen, JSONObject document) throws IOException {
        for (String key : document.keySet()) {
            Object v = document.get(key);
            if (v instanceof Boolean) jgen.writeBooleanField(key, (Boolean)v);
            else if (v instanceof JSONArray) {
                jgen.writeArrayFieldStart(key);
                JSONArray a = (JSONArray)v;
                for (int i = 0; i < a.length(); i++) jgen.writeString(a.getString(i));
                jgen.writeEndArray();
            } else jgen.writeStringField(key, v.toString());
        }
    }

    private static JSONObject childDoc(String id, boolean pub, JSONArray owners, JSONArray viewers,
        String linkField, Object linkValue) {
        return new JSONObject().put("id", id).put("publiclyReadable", pub).put("submitterUserIds", owners)
                   .put("viewUsers", viewers).put(linkField, linkValue);
    }

    // ---- running ----

    private Result run() throws Exception {
        return run(new Config(1000, 20000, 500, "5m"));
    }

    private Result run(Config config) throws Exception {
        return run(config, null);
    }

    /** gate: when non-null, the FIRST scroll of the encounter index blocks until it is counted down. */
    private Result run(Config config, final CountDownLatch gate) throws Exception {
        try (MockedStatic<Collaboration> mc = mockStatic(Collaboration.class, Answers.CALLS_REAL_METHODS);
            MockedStatic<OpenSearch> osStatic = mockStatic(OpenSearch.class);
            MockedConstruction<Shepherd> shepherds = mockConstruction(Shepherd.class, (mock, ctx) -> {
                when(mock.getContext()).thenReturn("context0");
                when(mock.getUsersWithUsername()).thenReturn(users);
                when(mock.getAllCollaborations()).thenReturn(collabs);
                when(mock.getAllOrganizationsStrict()).thenReturn(orgs);
                when(mock.getUsernamesWithAnyRole(any(), eq("context0"))).thenReturn(orgAdminUsernames);
                when(mock.getRolesInContext("context0")).thenReturn(roles);
                when(mock.getAnnotation(anyString())).thenAnswer(inv -> annotations.get((String)inv.getArgument(0)));
                when(mock.getMarkedIndividual(anyString())).thenAnswer(inv -> individuals.get((String)inv.getArgument(0)));
                final String[] action = { "" };
                doAnswer(inv -> {
                    action[0] = inv.getArgument(0);
                    return null;
                }).when(mock).setAction(anyString());
                doAnswer(inv -> {
                    if (rebuildShepherdFails && "PermissionsAudit.rebuild".equals(action[0]))
                        throw new RuntimeException("database down");
                    return null;
                }).when(mock).beginDBTransaction();
                PersistenceManager pm = mock(PersistenceManager.class);
                Transaction tx = mock(Transaction.class);
                when(tx.isActive()).thenReturn(true);
                when(tx.getIsolationLevel()).thenReturn(isolationLevel);
                when(pm.currentTransaction()).thenReturn(tx);
                when(pm.newQuery(eq("javax.jdo.query.SQL"), anyString())).thenAnswer(inv -> {
                    String sql = inv.getArgument(1);
                    Query q = mock(Query.class);
                    when(q.execute()).thenReturn(rowsFor(sql));
                    return q;
                });
                when(mock.getPM()).thenReturn(pm);
            });
            MockedConstruction<OpenSearch> searches = mockConstruction(OpenSearch.class, (mock, ctx) -> {
                when(mock.fieldMappings(anyString())).thenAnswer(inv -> mappings.get((String)inv.getArgument(0)));
                doAnswer(inv -> {
                    String index = inv.getArgument(0);
                    if (scrollFails.contains(index)) throw new IOException("scroll failed: " + index);
                    if (scrollInterruptOn.contains(index)) throw new InterruptedIOException("interrupted: " + index);
                    if ((gate != null) && "encounter".equals(index)) gate.await(10, TimeUnit.SECONDS);
                    OpenSearch.DocValuesPageConsumer consumer = inv.getArgument(4);
                    for (JSONArray p : pages.get(index)) consumer.accept(p);
                    return null;
                }).when(mock).scrollDocValues(anyString(), any(), anyInt(), anyString(), any());
                doAnswer(inv -> conditionalWrite("update", inv.getArgument(0), inv.getArgument(1),
                    inv.getArgument(2), inv.getArgument(3), inv.getArgument(4)))
                    .when(mock).updateIfUnchanged(anyString(), anyString(), any(JSONObject.class), anyLong(), anyLong());
                doAnswer(inv -> conditionalWrite("put", inv.getArgument(0), inv.getArgument(1),
                    inv.getArgument(2), inv.getArgument(3), inv.getArgument(4)))
                    .when(mock).putIfUnchanged(anyString(), anyString(), anyString(), anyLong(), anyLong());
            })) {
            mc.when(() -> Collaboration.securityEnabled(anyString())).thenReturn(securityEnabled);
            osStatic.when(OpenSearch::skipAutoIndexing).thenAnswer(inv -> skipAutoIndexing);
            return PermissionsAudit.run("context0", config);
        }
    }

    private Object conditionalWrite(String kind, String index, String id, Object body, long seqNo,
        long primaryTerm) throws IOException {
        if (failOn.contains(id)) throw new IOException("write failed: " + id);
        JSONObject w = new JSONObject().put("kind", kind).put("index", index).put("id", id)
            .put("seqNo", seqNo).put("primaryTerm", primaryTerm);
        if (body instanceof JSONObject) w.put("doc", new JSONObject(body.toString()));
        else w.put("json", new JSONObject((String)body));
        writes.add(w);
        Runnable after = onWrite.get(id);
        if (after != null) after.run();
        if (conflictOn.contains(id)) return OpenSearch.ConditionalWrite.conflict();
        if ("put".equals(kind) && conflictOnPut.contains(id)) return OpenSearch.ConditionalWrite.conflict();
        return OpenSearch.ConditionalWrite.applied(seqNo + 1, primaryTerm);
    }

    private List<Object[]> rowsFor(String sql) {
        if (sql.contains("ENCOUNTER_ANNOTATIONS")) return linkRows;
        if (sql.contains("MARKEDINDIVIDUAL_ENCOUNTERS")) return memberRows;
        if (sql.contains("FROM \"MARKEDINDIVIDUAL\"")) return individualRows;
        if (sql.contains("FROM \"ANNOTATION\"")) return annotationRows;
        if (sql.contains("FROM \"ENCOUNTER\"")) return encounterRows;
        throw new IllegalArgumentException("unexpected SQL in test: " + sql);
    }

    private List<JSONObject> writesTo(String index, String id) {
        List<JSONObject> out = new ArrayList<JSONObject>();
        for (JSONObject w : writes) {
            if (w.getString("index").equals(index) && w.getString("id").equals(id)) out.add(w);
        }
        return out;
    }

    private static Set<String> strings(JSONArray a) {
        Set<String> s = new HashSet<String>();
        for (int i = 0; i < a.length(); i++) s.add(a.getString(i));
        return s;
    }

    // ================= converged system =================

    @Test void convergedSystemWritesNothingAndCompletes() throws Exception {
        encounter("e1", "owner", "Komodo");
        annotation("a1", "e1");
        individual("i1", "e1");
        page("encounter", encounterHit("e1", false, "uuid-O", "uuid-B", "uuid-A"));
        page("annotation", childHit("a1", false, arr("uuid-O"), arr("uuid-A", "uuid-B"), "encounterId", arr("e1")));
        page("individual", childHit("i1", false, arr("uuid-O"), arr("uuid-B", "uuid-A"), "encounterIds", arr("e1")));

        Result r = run();
        assertTrue(writes.isEmpty(), "nothing to repair: " + writes);
        assertTrue(r.completed, r.toString());
        assertEquals(1, r.index("encounter").scanned);
        assertEquals(1, r.index("annotation").scanned);
        assertEquals(1, r.index("individual").scanned);
    }

    // ================= encounter documents =================

    @Test void encounterViewerDriftIsRepairedWithTheHitsConcurrencyMetadata() throws Exception {
        encounter("e1", "owner", "Komodo");
        page("encounter", encounterHit("e1", false, "uuid-O")); // viewers missing

        Result r = run();
        List<JSONObject> w = writesTo("encounter", "e1");
        assertEquals(1, w.size());
        assertEquals("update", w.get(0).getString("kind"));
        assertEquals(5L, w.get(0).getLong("seqNo"));
        assertEquals(1L, w.get(0).getLong("primaryTerm"));
        JSONObject doc = w.get(0).getJSONObject("doc");
        assertEquals(false, doc.getBoolean("publiclyReadable"));
        assertEquals("uuid-O", doc.getString("submitterUserId"));
        assertEquals(set("uuid-B", "uuid-A"), strings(doc.getJSONArray("viewUsers")));
        assertTrue(r.completed, "a successful repair is a complete audit");
        assertEquals(1, r.index("encounter").repaired);
    }

    @Test void staleOwnerAndPublicFlagAreRepairedOnTheEncounterDocument() throws Exception {
        encounter("e1", "owner", "Komodo");
        page("encounter", encounterHit("e1", true, "uuid-OLD", "uuid-B", "uuid-A"));

        run();
        JSONObject doc = writesTo("encounter", "e1").get(0).getJSONObject("doc");
        assertEquals(false, doc.getBoolean("publiclyReadable"));
        assertEquals("uuid-O", doc.getString("submitterUserId"));
    }

    @Test void unresolvableOwnerClearsTheOwnerFieldExplicitly() throws Exception {
        encounter("e1", "ghost", "Komodo");
        page("encounter", encounterHit("e1", false, "uuid-OLD", "uuid-B"));

        run();
        JSONObject doc = writesTo("encounter", "e1").get(0).getJSONObject("doc");
        assertTrue(doc.has("submitterUserId") && doc.isNull("submitterUserId"), "explicit null, not omitted");
        assertEquals(set("uuid-B"), strings(doc.getJSONArray("viewUsers")));
    }

    @Test void publicEncounterDocumentKeepsItsResolvedOwner() throws Exception {
        user("public", "uuid-P");
        encounter("e1", "public", "Komodo");
        page("encounter", encounterHit("e1", true, "uuid-P"));

        run();
        assertTrue(writesTo("encounter", "e1").isEmpty(), "matches what the serializer writes");
    }

    @Test void documentWithoutAPublicFlagIsRepaired() throws Exception {
        encounter("e1", "owner", "Komodo");
        page("encounter", encounterHit("e1", null, "uuid-O", "uuid-B", "uuid-A"));

        run();
        assertEquals(1, writesTo("encounter", "e1").size(), "an absent flag is never assumed");
    }

    @Test void documentOfADeletedEncounterIsLeftToTheReconciler() throws Exception {
        page("encounter", encounterHit("gone", false, "uuid-O"));

        Result r = run();
        assertTrue(writes.isEmpty());
        assertTrue(r.completed);
        assertEquals(1, r.index("encounter").unknown);
    }

    // ================= completion status =================

    @Test void conflictIsCountedAndLeavesTheAuditIncomplete() throws Exception {
        encounter("e1", "owner", "Komodo");
        page("encounter", encounterHit("e1", false, "uuid-O"));
        conflictOn.add("e1");

        Result r = run();
        assertFalse(r.completed);
        assertEquals(1, r.index("encounter").conflicts);
        assertEquals(0, r.index("encounter").failed);
    }

    @Test void writeFailureLeavesTheAuditIncompleteButTheRestIsStillRepaired() throws Exception {
        encounter("e1", "owner", "Komodo");
        encounter("e2", "owner", "Komodo");
        page("encounter", encounterHit("e1", false, "uuid-O"), encounterHit("e2", false, "uuid-O"));
        failOn.add("e1");

        Result r = run();
        assertFalse(r.completed);
        assertEquals(1, r.index("encounter").failed);
        assertEquals(1, writesTo("encounter", "e2").size());
    }

    @Test void scrollFailureAbortsOnlyThatIndex() throws Exception {
        encounter("e1", "owner", "Komodo");
        individual("i1", "e1");
        page("encounter", encounterHit("e1", false, "uuid-O"));
        page("individual", childHit("i1", false, arr("uuid-O"), arr(), "encounterIds", arr("e1")));
        scrollFails.add("annotation");

        Result r = run();
        assertFalse(r.completed);
        assertEquals(1, writesTo("encounter", "e1").size(), "encounter audited");
        assertEquals(1, writesTo("individual", "i1").size(), "individual audited");
        assertTrue(r.reasons.toString().contains("annotation"), r.reasons.toString());
    }

    @Test void unusableHitAbortsThatIndexWithoutWriting() throws Exception {
        encounter("e1", "owner", "Komodo");
        JSONObject noSeq = encounterHit("e1", false, "uuid-O");
        noSeq.remove("_seq_no");
        page("encounter", noSeq);

        Result r = run();
        assertFalse(r.completed);
        assertTrue(writes.isEmpty());
    }

    @Test void repairCapStopsWritingAndLeavesTheAuditIncomplete() throws Exception {
        encounter("e1", "owner", "Komodo");
        encounter("e2", "owner", "Komodo");
        page("encounter", encounterHit("e1", false, "uuid-O"), encounterHit("e2", false, "uuid-O"));

        Result r = run(new Config(1000, 1, 500, "5m"));
        assertEquals(1, writes.size());
        assertFalse(r.completed);
        assertTrue(r.capHit);
    }

    @Test void mappingWithoutDocValuesDisablesThatIndexAudit() throws Exception {
        encounter("e1", "owner", "Komodo");
        annotation("a1", "e1");
        page("encounter", encounterHit("e1", false, "uuid-O"));
        page("annotation", childHit("a1", false, arr("uuid-O"), arr(), "encounterId", arr("e1")));
        mappings.get("annotation").put("viewUsers", new JSONObject().put("type", "keyword").put("doc_values", false));

        Result r = run();
        assertFalse(r.completed);
        assertEquals(1, writesTo("encounter", "e1").size());
        assertTrue(writesTo("annotation", "a1").isEmpty(), "not audited: doc values unavailable");
        assertEquals(0, r.index("annotation").scanned);
    }

    @Test void mappingOfTheWrongTypeDisablesThatIndexAudit() throws Exception {
        encounter("e1", "owner", "Komodo");
        page("encounter", encounterHit("e1", false, "uuid-O"));
        mappings.get("encounter").put("viewUsers", new JSONObject().put("type", "text"));

        Result r = run();
        assertFalse(r.completed);
        assertTrue(writes.isEmpty());
    }

    @Test void securityDisabledAuditsNothingAndCompletes() throws Exception {
        securityEnabled = false;
        encounter("e1", "owner", "Komodo");
        page("encounter", encounterHit("e1", false, "uuid-O"));

        Result r = run();
        assertTrue(r.completed);
        assertEquals(0, r.index("encounter").scanned);
    }

    @Test void snapshotWithoutRepeatableReadAbortsBeforeAnyIndexWork() throws Exception {
        isolationLevel = "read-committed";
        encounter("e1", "owner", "Komodo");
        page("encounter", encounterHit("e1", false, "uuid-O"));

        Result r = run();
        assertFalse(r.completed);
        assertEquals(0, r.index("encounter").scanned);
        assertTrue(writes.isEmpty());
    }

    @Test void interruptedThreadStopsBeforeTheNextPage() throws Exception {
        encounter("e1", "owner", "Komodo");
        encounter("e2", "owner", "Komodo");
        page("encounter", encounterHit("e1", false, "uuid-O"));
        page("encounter", encounterHit("e2", false, "uuid-O"));
        try {
            Result r = runWithInterruptOn("e1"); // interrupts the audit thread inside e1's repair
            assertFalse(r.completed);
            assertEquals(1, writes.size(), "the second page is not processed");
        } finally {
            Thread.interrupted(); // clear the flag for the test runner
        }
    }

    private Result runWithInterruptOn(final String id) throws Exception {
        final Config config = new Config(1000, 20000, 500, "5m");
        failOn.remove(id);
        // reuse run() but interrupt inside the conditional write
        final Set<String> original = failOn;
        failOn = new HashSet<String>(original) {
            @Override public boolean contains(Object o) {
                if (id.equals(o)) Thread.currentThread().interrupt();
                return super.contains(o);
            }
        };
        return run(config);
    }

    @Test void concurrentRunIsRefused() throws Exception {
        encounter("e1", "owner", "Komodo");
        page("encounter", encounterHit("e1", false, "uuid-O", "uuid-B", "uuid-A"));
        final CountDownLatch gate = new CountDownLatch(1);
        final AtomicReference<Result> first = new AtomicReference<Result>();
        final AtomicReference<Exception> firstError = new AtomicReference<Exception>();
        Thread t = new Thread(() -> {
            try {
                first.set(run(new Config(1000, 20000, 500, "5m"), gate));
            } catch (Exception ex) {
                firstError.set(ex);
            }
        });
        t.start();
        Thread.sleep(500); // let the first run reach the gated scroll
        Result second = run();
        gate.countDown();
        t.join(10000);
        assertFalse(second.completed, "a second runner must not audit while one is running");
        assertEquals(0, second.index("encounter").scanned);
        assertTrue(second.reasons.toString().contains("running"), second.reasons.toString());
        if (firstError.get() != null) throw firstError.get();
        assertNotNull(first.get());
        assertTrue(first.get().completed);
    }

    // ================= annotation documents =================

    @Test void childCopyIsRepairedEvenWhenTheParentDocumentIsAlreadyCorrect() throws Exception {
        // ordering 1: the parent was rewritten by a writer that skipped the children
        encounter("e1", "owner", "Komodo");
        annotation("a1", "e1");
        page("encounter", encounterHit("e1", false, "uuid-O", "uuid-B", "uuid-A"));
        page("annotation", childHit("a1", false, arr("uuid-O"), arr("uuid-A"), "encounterId", arr("e1")));

        Result r = run();
        assertTrue(writesTo("encounter", "e1").isEmpty());
        List<JSONObject> w = writesTo("annotation", "a1");
        assertEquals(1, w.size());
        JSONObject doc = w.get(0).getJSONObject("doc");
        assertEquals(set("uuid-O"), strings(doc.getJSONArray("submitterUserIds")));
        assertEquals(set("uuid-B", "uuid-A"), strings(doc.getJSONArray("viewUsers")));
        assertEquals(7L, w.get(0).getLong("seqNo"));
        assertTrue(r.completed);
    }

    @Test void childCopyKeepingARevokedGrantIsRepairedThoughTheParentNeverChanged() throws Exception {
        // ordering 2: grant, child reindexed, revoke: the encounter document never held the grant
        encounter("e1", "owner", "Atlantis"); // no location grants
        annotation("a1", "e1");
        page("encounter", encounterHit("e1", false, "uuid-O", "uuid-A"));
        page("annotation", childHit("a1", false, arr("uuid-O"), arr("uuid-A", "uuid-REVOKED"), "encounterId", arr("e1")));

        run();
        assertTrue(writesTo("encounter", "e1").isEmpty());
        JSONObject doc = writesTo("annotation", "a1").get(0).getJSONObject("doc");
        assertEquals(set("uuid-A"), strings(doc.getJSONArray("viewUsers")));
    }

    @Test void annotationWithTheWrongParentIsDeniedFirstThenRebuiltFromTheSerializer() throws Exception {
        encounter("e1", "owner", "Komodo");
        annotation("a1", "e1");
        // the document still names an old parent while carrying e1's correct ACL
        page("annotation", childHit("a1", false, arr("uuid-O"), arr("uuid-B", "uuid-A"), "encounterId", arr("eOld")));
        rebuildableAnnotation("a1", childDoc("a1", false, arr("uuid-O"), arr("uuid-B", "uuid-A"), "encounterId", "e1"));

        Result r = run();
        List<JSONObject> w = writesTo("annotation", "a1");
        assertEquals(2, w.size(), "deny, then rebuild: " + w);
        assertEquals("update", w.get(0).getString("kind"));
        JSONObject deny = w.get(0).getJSONObject("doc");
        assertEquals(false, deny.getBoolean("publiclyReadable"));
        assertEquals(0, deny.getJSONArray("submitterUserIds").length());
        assertEquals(0, deny.getJSONArray("viewUsers").length());
        assertEquals(7L, w.get(0).getLong("seqNo"));
        assertEquals("put", w.get(1).getString("kind"));
        assertEquals(8L, w.get(1).getLong("seqNo"), "conditioned on the deny write's result");
        assertEquals("e1", w.get(1).getJSONObject("json").getString("encounterId"));
        assertEquals(1, r.index("annotation").structural);
        assertFalse(r.completed, "a structural repair is verified by the next audit");
    }

    @Test void annotationAlreadyDeniedWithTheWrongParentIsRebuiltWithoutADenyWrite() throws Exception {
        encounter("e1", "owner", "Komodo");
        annotation("a1", "e1");
        page("annotation", childHit("a1", false, arr(), arr(), "encounterId", arr("eOld")));
        rebuildableAnnotation("a1", childDoc("a1", false, arr("uuid-O"), arr("uuid-B", "uuid-A"), "encounterId", "e1"));

        run();
        List<JSONObject> w = writesTo("annotation", "a1");
        assertEquals(1, w.size());
        assertEquals("put", w.get(0).getString("kind"));
        assertEquals(7L, w.get(0).getLong("seqNo"), "conditioned on the hit");
    }

    @Test void rebuildWhoseSerializerDisagreesWithTheSnapshotIsNotSent() throws Exception {
        encounter("e1", "owner", "Komodo");
        annotation("a1", "e1");
        page("annotation", childHit("a1", false, arr("uuid-O"), arr("uuid-B", "uuid-A"), "encounterId", arr("eOld")));
        // the database moved on after the snapshot: the serializer now sees another parent
        rebuildableAnnotation("a1", childDoc("a1", false, arr("uuid-B"), arr(), "encounterId", "e2"));

        Result r = run();
        List<JSONObject> w = writesTo("annotation", "a1");
        assertEquals(1, w.size(), "only the deny write");
        assertEquals("update", w.get(0).getString("kind"));
        assertFalse(r.completed);
        assertEquals(1, r.index("annotation").failed);
    }

    @Test void rebuildIsNotAttemptedAfterAConflictedDenyWrite() throws Exception {
        encounter("e1", "owner", "Komodo");
        annotation("a1", "e1");
        page("annotation", childHit("a1", false, arr("uuid-O"), arr("uuid-B", "uuid-A"), "encounterId", arr("eOld")));
        rebuildableAnnotation("a1", childDoc("a1", false, arr("uuid-O"), arr("uuid-B", "uuid-A"), "encounterId", "e1"));
        conflictOn.add("a1");

        Result r = run();
        assertEquals(1, writesTo("annotation", "a1").size(), "the conflicted deny only");
        assertFalse(r.completed);
        assertEquals(1, r.index("annotation").conflicts);
    }

    @Test void rebuildIsDeferredDuringASkipAutoIndexingWindow() throws Exception {
        skipAutoIndexing = true;
        encounter("e1", "owner", "Komodo");
        annotation("a1", "e1");
        page("annotation", childHit("a1", false, arr("uuid-O"), arr("uuid-B", "uuid-A"), "encounterId", arr("eOld")));
        rebuildableAnnotation("a1", childDoc("a1", false, arr("uuid-O"), arr("uuid-B", "uuid-A"), "encounterId", "e1"));

        Result r = run();
        List<JSONObject> w = writesTo("annotation", "a1");
        assertEquals(1, w.size(), "the deny write still happens");
        assertEquals("update", w.get(0).getString("kind"));
        assertEquals(1, r.index("annotation").deferred);
        assertFalse(r.completed);
    }

    @Test void rebuildCapLeavesTheRestForTheNextAudit() throws Exception {
        encounter("e1", "owner", "Komodo");
        annotation("a1", "e1");
        annotation("a2", "e1");
        page("annotation", childHit("a1", false, arr(), arr(), "encounterId", arr("eOld")),
            childHit("a2", false, arr(), arr(), "encounterId", arr("eOld")));
        JSONObject good = childDoc("x", false, arr("uuid-O"), arr("uuid-B", "uuid-A"), "encounterId", "e1");
        rebuildableAnnotation("a1", new JSONObject(good.toString()).put("id", "a1"));
        rebuildableAnnotation("a2", new JSONObject(good.toString()).put("id", "a2"));

        Result r = run(new Config(1000, 20000, 1, "5m"));
        int puts = 0;
        for (JSONObject w : writes) if ("put".equals(w.getString("kind"))) puts++;
        assertEquals(1, puts);
        assertFalse(r.completed);
        assertTrue(r.capHit);
    }

    @Test void annotationWithTwoParentsGetsTheDenyTupleAsAnOrdinaryRepair() throws Exception {
        encounter("e1", "owner", "Komodo");
        encounter("e2", "bob", "Komodo");
        annotation("a1", "e1", "e2");
        page("annotation", childHit("a1", false, arr("uuid-O"), arr("uuid-B", "uuid-A"), "encounterId", arr("e1")));

        Result r = run();
        List<JSONObject> w = writesTo("annotation", "a1");
        assertEquals(1, w.size());
        assertEquals(0, w.get(0).getJSONObject("doc").getJSONArray("viewUsers").length());
        assertEquals(0, r.index("annotation").structural, "no single parent to compare the linkage with");
        assertTrue(r.completed);
    }

    @Test void eligibleOrphanAnnotationIsDenied() throws Exception {
        annotation("a1"); // eligible, no links
        page("annotation", childHit("a1", false, arr("uuid-O"), arr("uuid-B"), "encounterId", arr("eGone")));

        Result r = run();
        List<JSONObject> w = writesTo("annotation", "a1");
        assertEquals(1, w.size());
        assertEquals(0, w.get(0).getJSONObject("doc").getJSONArray("submitterUserIds").length());
        assertTrue(r.completed);
    }

    @Test void ineligibleAnnotationDocumentIsLeftToTheReconciler() throws Exception {
        encounter("e1", "owner", "Komodo");
        page("annotation", childHit("a9", false, arr("uuid-O"), arr("uuid-B"), "encounterId", arr("e1")));

        Result r = run();
        assertTrue(writes.isEmpty());
        assertEquals(1, r.index("annotation").unknown);
        assertTrue(r.completed);
    }

    @Test void annotationLinkageWithTwoIndexedValuesIsStructural() throws Exception {
        encounter("e1", "owner", "Komodo");
        annotation("a1", "e1");
        page("annotation", childHit("a1", false, arr("uuid-O"), arr("uuid-B", "uuid-A"), "encounterId", arr("e1", "e1")));
        rebuildableAnnotation("a1", childDoc("a1", false, arr("uuid-O"), arr("uuid-B", "uuid-A"), "encounterId", "e1"));

        Result r = run();
        assertEquals(1, r.index("annotation").structural);
    }

    // ================= individual documents =================

    @Test void individualAclDriftWithCorrectMembershipIsRepairedInPlace() throws Exception {
        encounter("e1", "owner", "Komodo");
        encounter("e2", "public", "Komodo");
        individual("i1", "e1", "e2");
        page("individual", childHit("i1", false, arr("uuid-O"), arr("uuid-B"), "encounterIds", arr("e1", "e2")));

        Result r = run();
        JSONObject doc = writesTo("individual", "i1").get(0).getJSONObject("doc");
        assertEquals(true, doc.getBoolean("publiclyReadable"), "e2 is public");
        assertEquals(set("uuid-O"), strings(doc.getJSONArray("submitterUserIds")));
        assertEquals(set("uuid-B", "uuid-A"), strings(doc.getJSONArray("viewUsers")));
        assertTrue(r.completed);
    }

    @Test void individualMembershipDriftIsDeniedThenRebuilt() throws Exception {
        encounter("e1", "owner", "Komodo");
        individual("i1", "e1");
        page("individual", childHit("i1", false, arr("uuid-O"), arr("uuid-B", "uuid-A"), "encounterIds", arr("e1", "eOld")));
        rebuildableIndividual("i1", childDoc("i1", false, arr("uuid-O"), arr("uuid-B", "uuid-A"), "encounterIds", arr("e1")));

        Result r = run();
        List<JSONObject> w = writesTo("individual", "i1");
        assertEquals(2, w.size(), w.toString());
        assertEquals("update", w.get(0).getString("kind"));
        assertEquals("put", w.get(1).getString("kind"));
        assertEquals(1, r.index("individual").structural);
    }

    @Test void deletedIndividualDocumentIsLeftToTheReconciler() throws Exception {
        page("individual", childHit("gone", false, arr("uuid-O"), arr(), "encounterIds", arr("e1")));

        Result r = run();
        assertTrue(writes.isEmpty());
        assertEquals(1, r.index("individual").unknown);
    }

    @Test void encounterlessIndividualIsPublicWithNoGrants() throws Exception {
        individual("i1");
        page("individual", childHit("i1", false, arr("uuid-O"), arr("uuid-B"), "encounterIds", arr()));

        run();
        JSONObject doc = writesTo("individual", "i1").get(0).getJSONObject("doc");
        assertEquals(true, doc.getBoolean("publiclyReadable"));
        assertEquals(0, doc.getJSONArray("submitterUserIds").length());
    }

    @Test void rebuildOfAnObjectTheDatabaseNoLongerHasIsSkipped() throws Exception {
        encounter("e1", "owner", "Komodo");
        individual("i1", "e1");
        page("individual", childHit("i1", false, arr(), arr(), "encounterIds", arr("e1", "eOld")));
        // no rebuildable individual registered: getMarkedIndividual returns null

        Result r = run();
        assertTrue(writesTo("individual", "i1").isEmpty(), "already denied; nothing to send");
        assertFalse(r.completed);
    }

    // ================= round-17 findings =================

    private int puts() {
        int n = 0;
        for (JSONObject w : writes) if ("put".equals(w.getString("kind"))) n++;
        return n;
    }

    private static JSONObject correctChild(String id, String linkField, Object link) {
        return childDoc(id, false, arr("uuid-O"), arr("uuid-B", "uuid-A"), linkField, link);
    }

    @Test void rebuildCapIsSharedAcrossTheChildIndexes() throws Exception {
        encounter("e1", "owner", "Komodo");
        annotation("a1", "e1");
        individual("i1", "e1");
        page("annotation", childHit("a1", false, arr(), arr(), "encounterId", arr("eOld")));
        page("individual", childHit("i1", false, arr(), arr(), "encounterIds", arr("e1", "eOld")));
        rebuildableAnnotation("a1", correctChild("a1", "encounterId", "e1"));
        rebuildableIndividual("i1", correctChild("i1", "encounterIds", arr("e1")));

        Result r = run(new Config(1000, 20000, 1, "5m"));
        assertEquals(1, puts(), "one rebuild budget for the whole audit, not one per index");
        assertTrue(r.capHit);
    }

    @Test void mappingWithANullValueDisablesThatIndexAudit() throws Exception {
        encounter("e1", "ghost", "Komodo");
        page("encounter", encounterHit("e1", false, "uuid-OLD", "uuid-B"));
        mappings.get("encounter").put("submitterUserId",
            new JSONObject().put("type", "keyword").put("null_value", "uuid-X"));

        Result r = run();
        assertTrue(writes.isEmpty(), "writing a JSON null would index the mapping's null_value");
        assertFalse(r.completed);
    }

    @Test void scalarAclFieldInAHitAbortsThatIndex() throws Exception {
        encounter("e1", "owner", "Komodo");
        JSONObject h = encounterHit("e1", false, "uuid-O", "uuid-B", "uuid-A");
        h.getJSONObject("fields").put("viewUsers", "uuid-B"); // a scalar where an array is expected

        page("encounter", h);
        Result r = run();
        assertTrue(writes.isEmpty(), "malformed is not absent");
        assertFalse(r.completed);
    }

    @Test void rebuiltDocumentWithMalformedAclFieldsIsNotSent() throws Exception {
        encounter("e1", "ghost", "Komodo"); // expected owners {}, viewers {uuid-B}
        annotation("a1", "e1");
        page("annotation", childHit("a1", false, arr(), arr(), "encounterId", arr("eOld")));
        // a scalar owner would read as "no owners" to a lenient check and pass the empty expectation
        rebuildableAnnotation("a1", new JSONObject().put("id", "a1").put("publiclyReadable", false)
            .put("submitterUserIds", "unexpected-user").put("viewUsers", arr("uuid-B")).put("encounterId", "e1"));

        Result r = run();
        assertEquals(0, puts());
        assertEquals(1, r.index("annotation").failed);
    }

    @Test void failedDenyWriteSuppressesTheRebuild() throws Exception {
        encounter("e1", "owner", "Komodo");
        annotation("a1", "e1");
        page("annotation", childHit("a1", false, arr("uuid-O"), arr("uuid-B", "uuid-A"), "encounterId", arr("eOld")));
        rebuildableAnnotation("a1", correctChild("a1", "encounterId", "e1"));
        failOn.add("a1");

        Result r = run();
        assertEquals(0, puts(), "no rebuild after a deny that did not land");
        assertEquals(1, r.index("annotation").failed);
        assertFalse(r.completed);
    }

    @Test void throwingSerializerKeepsTheDocumentDenied() throws Exception {
        encounter("e1", "owner", "Komodo");
        annotation("a1", "e1");
        page("annotation", childHit("a1", false, arr(), arr(), "encounterId", arr("eOld")));
        Annotation ann = spy(new Annotation());
        doReturn("a1").when(ann).getId();
        doReturn(true).when(ann).shouldIndexInOpenSearch();
        doThrow(new IOException("serializer failed")).when(ann)
            .opensearchDocumentSerializer(any(JsonGenerator.class), any(Shepherd.class));
        annotations.put("a1", ann);

        Result r = run();
        assertEquals(0, puts());
        assertEquals(1, r.index("annotation").failed);
        assertFalse(r.completed);
    }

    @Test void conflictOnTheRebuildIsCountedNotFailed() throws Exception {
        encounter("e1", "owner", "Komodo");
        annotation("a1", "e1");
        page("annotation", childHit("a1", false, arr(), arr(), "encounterId", arr("eOld")));
        rebuildableAnnotation("a1", correctChild("a1", "encounterId", "e1"));
        conflictOnPut.add("a1");

        Result r = run();
        assertEquals(1, r.index("annotation").conflicts);
        assertEquals(0, r.index("annotation").failed);
        assertFalse(r.completed);
    }

    @Test void previousRebuildFailureGoesLastUnderASmallCap() throws Exception {
        encounter("e1", "owner", "Komodo");
        annotation("a1", "e1");
        annotation("a2", "e1");
        // run 1: a1's serializer disagrees with the snapshot -> remembered as failed
        page("annotation", childHit("a1", false, arr(), arr(), "encounterId", arr("eOld")));
        rebuildableAnnotation("a1", correctChild("a1", "encounterId", "e2"));
        assertEquals(1, run().index("annotation").failed);
        // run 2: both need a rebuild, a1 now serializes correctly, one rebuild allowed
        writes.clear();
        pages.get("annotation").clear();
        page("annotation", childHit("a1", false, arr(), arr(), "encounterId", arr("eOld")),
            childHit("a2", false, arr(), arr(), "encounterId", arr("eOld")));
        rebuildableAnnotation("a1", correctChild("a1", "encounterId", "e1"));
        rebuildableAnnotation("a2", correctChild("a2", "encounterId", "e1"));

        Result r = run(new Config(1000, 20000, 1, "5m"));
        assertEquals(1, puts());
        for (JSONObject w : writes) if ("put".equals(w.getString("kind"))) assertEquals("a2", w.getString("id"));
        assertEquals(1, r.index("annotation").deferred);
    }

    @Test void freshRebuildsOfEitherChildIndexComeBeforePreviouslyFailedOnes() throws Exception {
        encounter("e1", "owner", "Komodo");
        annotation("a1", "e1");
        individual("i1", "e1");
        // run 1: a1's serializer disagrees with the snapshot -> remembered as failed
        page("annotation", childHit("a1", false, arr(), arr(), "encounterId", arr("eOld")));
        rebuildableAnnotation("a1", correctChild("a1", "encounterId", "e2"));
        assertEquals(1, run().index("annotation").failed);
        // run 2: a1 (failed before, annotation index, scanned first) and i1 (fresh, individual
        // index, scanned last) both need a rebuild; one rebuild allowed -> the fresh one goes first
        writes.clear();
        pages.get("annotation").clear();
        page("annotation", childHit("a1", false, arr(), arr(), "encounterId", arr("eOld")));
        page("individual", childHit("i1", false, arr(), arr(), "encounterIds", arr("e1", "eOld")));
        rebuildableAnnotation("a1", correctChild("a1", "encounterId", "e1"));
        rebuildableIndividual("i1", correctChild("i1", "encounterIds", arr("e1")));

        Result r = run(new Config(1000, 20000, 1, "5m"));
        assertEquals(1, puts());
        for (JSONObject w : writes) if ("put".equals(w.getString("kind"))) assertEquals("i1", w.getString("id"));
        assertEquals(1, r.index("annotation").deferred, "the previously failed annotation waits");
        assertEquals(0, r.index("individual").deferred);
    }

    @Test void interruptionDuringAPageStopsFurtherRepairsInThatPage() throws Exception {
        encounter("e1", "owner", "Komodo");
        encounter("e2", "owner", "Komodo");
        page("encounter", encounterHit("e1", false, "uuid-O"), encounterHit("e2", false, "uuid-O"));
        onWrite.put("e1", () -> Thread.currentThread().interrupt());
        try {
            Result r = run();
            assertEquals(1, writes.size(), "no repair after the interruption, even within the same page");
            assertTrue(r.interrupted);
            assertFalse(r.completed);
        } finally {
            Thread.interrupted();
        }
    }

    @Test void interruptionRaisedByTheLastWriteStillMakesTheAuditIncomplete() throws Exception {
        encounter("e1", "owner", "Komodo");
        page("encounter", encounterHit("e1", false, "uuid-O")); // the only hit of the only page
        onWrite.put("e1", () -> Thread.currentThread().interrupt());
        try {
            Result r = run();
            assertFalse(r.completed, "nothing was left to read, but the run was interrupted");
            assertTrue(r.interrupted);
        } finally {
            Thread.interrupted();
        }
    }

    @Test void skipWindowOpeningAfterTheDenyWriteDefersTheRebuild() throws Exception {
        encounter("e1", "owner", "Komodo");
        annotation("a1", "e1");
        page("annotation", childHit("a1", false, arr("uuid-O"), arr("uuid-B", "uuid-A"), "encounterId", arr("eOld")));
        rebuildableAnnotation("a1", correctChild("a1", "encounterId", "e1"));
        onWrite.put("a1", () -> skipAutoIndexing = true); // the window opens between the deny and the rebuild

        Result r = run();
        assertEquals(0, puts(), "the switch is read immediately before dispatch");
        assertEquals(1, r.index("annotation").deferred);
    }

    @Test void unrelatedIndexFailureDoesNotBlockQueuedRebuilds() throws Exception {
        encounter("e1", "owner", "Komodo");
        annotation("a1", "e1");
        mappings.get("encounter").put("viewUsers", new JSONObject().put("type", "text")); // encounter skipped
        page("annotation", childHit("a1", false, arr(), arr(), "encounterId", arr("eOld")));
        rebuildableAnnotation("a1", correctChild("a1", "encounterId", "e1"));

        Result r = run();
        assertEquals(1, puts(), "the closed annotation is rebuilt although another index was not audited");
        assertFalse(r.completed);
    }

    @Test void laterIndexFailureAfterCandidatesWereQueuedStillRebuildsThem() throws Exception {
        encounter("e1", "owner", "Komodo");
        annotation("a1", "e1");
        page("annotation", childHit("a1", false, arr(), arr(), "encounterId", arr("eOld")));
        rebuildableAnnotation("a1", correctChild("a1", "encounterId", "e1"));
        scrollFails.add("individual");

        Result r = run();
        assertEquals(1, puts());
        assertFalse(r.completed);
    }

    @Test void skipWindowOpeningBetweenCandidatesDefersTheRest() throws Exception {
        encounter("e1", "owner", "Komodo");
        annotation("a1", "e1");
        annotation("a2", "e1");
        page("annotation", childHit("a1", false, arr(), arr(), "encounterId", arr("eOld")),
            childHit("a2", false, arr(), arr(), "encounterId", arr("eOld")));
        rebuildableAnnotation("a1", correctChild("a1", "encounterId", "e1"));
        rebuildableAnnotation("a2", correctChild("a2", "encounterId", "e1"));
        onWrite.put("a1", () -> skipAutoIndexing = true); // the window opens after a1's rebuild lands

        Result r = run();
        assertEquals(1, puts());
        assertEquals(1, r.index("annotation").deferred, "a2 waits for the window to close");
    }

    @Test void skipWindowOpeningDuringSerializationDefersThatCandidate() throws Exception {
        encounter("e1", "owner", "Komodo");
        annotation("a1", "e1");
        page("annotation", childHit("a1", false, arr(), arr(), "encounterId", arr("eOld")));
        rebuildableAnnotation("a1", correctChild("a1", "encounterId", "e1"), () -> skipAutoIndexing = true);

        Result r = run();
        assertEquals(0, puts(), "the switch is read again immediately before the replace");
        assertEquals(1, r.index("annotation").deferred);
        assertFalse(r.completed);
    }

    @Test void interruptionReportedByTheScrollStopsLaterIndexesEvenWithTheFlagClear() throws Exception {
        encounter("e1", "owner", "Komodo");
        annotation("a1", "e1");
        page("encounter", encounterHit("e1", false, "uuid-O", "uuid-B", "uuid-A"));
        page("annotation", childHit("a1", false, arr("uuid-O"), arr(), "encounterId", arr("e1"))); // would be repaired
        scrollInterruptOn.add("encounter");

        Result r = run();
        assertTrue(r.interrupted);
        assertEquals(0, r.index("annotation").scanned, "no later index is audited after an interruption");
        assertTrue(writes.isEmpty());
        assertFalse(r.completed);
    }

    @Test void rebuildAbortCountsTheUnattemptedCandidatesAsDeferred() throws Exception {
        encounter("e1", "owner", "Komodo");
        annotation("a1", "e1");
        annotation("a2", "e1");
        page("annotation", childHit("a1", false, arr(), arr(), "encounterId", arr("eOld")),
            childHit("a2", false, arr(), arr(), "encounterId", arr("eOld")));
        rebuildableAnnotation("a1", correctChild("a1", "encounterId", "e1"));
        rebuildableAnnotation("a2", correctChild("a2", "encounterId", "e1"));
        rebuildShepherdFails = true;

        Result r = run();
        assertEquals(0, puts());
        assertEquals(2, r.index("annotation").deferred, "both wait for the next audit");
        assertFalse(r.completed);
    }

    @Test void interruptedThreadWithNothingToReadStillLeavesTheAuditIncomplete() throws Exception {
        encounter("e1", "owner", "Komodo"); // every index is empty: no consumer call ever happens
        try {
            Thread.currentThread().interrupt();
            Result r = run();
            assertFalse(r.completed, "an interrupted run is never a completed audit");
            assertTrue(r.reasons.toString().toLowerCase().contains("interrupt"), r.reasons.toString());
        } finally {
            Thread.interrupted();
        }
    }
}
