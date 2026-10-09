package org.ecocean.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import javax.jdo.Query;
import org.ecocean.Base;
import org.ecocean.CommonConfiguration;
import org.ecocean.Encounter;
import org.ecocean.IndexingManager;
import org.ecocean.IndexingManagerFactory;
import org.ecocean.OpenSearch;
import org.ecocean.Organization;
import org.ecocean.Role;
import org.ecocean.User;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.shepherd.core.TestPMFUtil;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The encounter index has two viewUsers writers: the full-index serializer (computeViewUsers) and
 * the background permissions pass (SQL rows + precomputed maps). The pass reindexes an encounter's
 * children whenever the indexed set differs from the one it computes, so if the two writers ever
 * disagree for some encounter, every pass rewrites it and every rewrite re-creates the difference
 * (issue #1779). This test runs both against the same real Postgres fixture, through real
 * DataNucleus queries, and then walks the convergence cycle: pass -> full reindex -> pass must be
 * quiet; a revocation must be written and must refresh only the encounters it changed.
 */
@Testcontainers
class ViewUsersParityDbTest {
    @Container
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("wildbook_test")
            .withUsername("wildbook")
            .withPassword("wildbook");

    private static JSONObject previousTree;
    private static boolean hadPreviousConfig;
    private static Properties previousConfig;
    private static final Map<String, String> uuid = new HashMap<String, String>();

    @SuppressWarnings("unchecked")
    private static Map<String, Properties> configCache() throws Exception {
        Field f = CommonConfiguration.class.getDeclaredField("contextToPropsCache");
        f.setAccessible(true);
        return (Map<String, Properties>)f.get(null);
    }

    @BeforeAll
    static void setUp() throws Exception {
        previousTree = LocationRoleTestTree.inject();
        hadPreviousConfig = configCache().containsKey("context0");
        previousConfig = configCache().get("context0");
        Properties cfg = new Properties();
        cfg.setProperty("collaborationSecurityEnabled", "true"); // otherwise everything is public
        CommonConfiguration.initialize("context0", cfg);

        Properties props = new Properties();
        props.setProperty("datanucleus.ConnectionUserName", postgres.getUsername());
        props.setProperty("datanucleus.ConnectionPassword", postgres.getPassword());
        props.setProperty("datanucleus.ConnectionDriverName", postgres.getDriverClassName());
        props.setProperty("datanucleus.ConnectionURL", postgres.getJdbcUrl());
        props.setProperty("datanucleus.schema.autoCreateTables", "true");
        TestPMFUtil.closePMF("context0");

        Shepherd sh = new Shepherd("context0", props);
        // the lifecycle listener flags permissionsNeeded on Role/Collaboration/Organization
        // stores; keep that out of the fixture database and out of a non-existent OpenSearch
        try (MockedStatic<OpenSearch> os = mockStatic(OpenSearch.class)) {
            sh.beginDBTransaction();
            seed(sh);
            sh.commitDBTransaction();
        } catch (Exception e) {
            sh.rollbackDBTransaction();
            throw e;
        } finally {
            sh.closeDBTransaction();
        }
    }

    @AfterAll
    static void tearDown() throws Exception {
        TestPMFUtil.closePMF("context0");
        if (hadPreviousConfig) configCache().put("context0", previousConfig);
        else configCache().remove("context0");
        LocationRoleTestTree.restore(previousTree);
    }

    // ---- fixture ----

    private static void user(Shepherd sh, String username) {
        User u = new User(username, "pw", "salt");
        sh.getPM().makePersistent(u);
        uuid.put(username, u.getId());
    }

    private static void role(Shepherd sh, String username, String rolename) {
        Role r = new Role(username, rolename);
        r.setContext("context0");
        sh.getPM().makePersistent(r);
    }

    private static void collab(Shepherd sh, String u1, String u2, String state) {
        Collaboration c = new Collaboration(u1, u2);
        c.setState(state);
        sh.getPM().makePersistent(c);
    }

    // Organization.members is the inverse side (mapped-by User.organizations), so the join rows
    // are written from each user's organizations list, with IDX = the organization's position in
    // THAT user's list; the auto-created table's primary key is (ORGANIZATION_ID, IDX). Two users
    // whose first organization is the same would therefore collide, so the fixture gives every
    // membership of one organization a distinct position (see seed()).
    private static Organization org(Shepherd sh, String name) {
        Organization o = new Organization(name);
        sh.getPM().makePersistent(o);
        return o;
    }

    private static void memberships(Shepherd sh, String username, Organization... orgs) {
        User u = sh.getUser(username);
        for (Organization o : orgs) u.addOrganization(o);
    }

    private static void encounter(Shepherd sh, String id, String submitter, String locationID) {
        Encounter enc = new Encounter();
        enc.setCatalogNumber(id);
        enc.setSubmitterID(submitter);
        enc.setLocationID(locationID);
        enc.setSkipAutoIndexing(true); // no indexing queue / OpenSearch during seeding
        sh.getPM().makePersistent(enc);
    }

    private static void seed(Shepherd sh) {
        for (String u : new String[] { "owner", "ann", "ed", "pen", "rej", "orgadmin", "memberA",
            "memberB", "lake", "far", "pad", "sys", "cpad", "padmem", "o'neal", "qu\"ote",
            "back\\slash" }) user(sh, u);
        role(sh, "orgadmin", Organization.ROLE_MANAGER);
        role(sh, "lake", "Indonesia");       // ancestor of Flores Sea and Komodo
        role(sh, "far", "Pakistan");         // ancestor of PAKISTAN - North
        role(sh, " pad ", "Flores Sea");     // padded username: getUser(" pad ") resolves pad
        role(sh, "sys", "researcher");       // a system role name that is also a tree node
        role(sh, "sys", "admin");
        role(sh, "ghostrole", "Indonesia");  // no such user: no id to grant
        collab(sh, "owner", "ann", Collaboration.STATE_APPROVED);
        collab(sh, "owner", "ed", Collaboration.STATE_EDIT_PRIV);
        collab(sh, "owner", "pen", Collaboration.STATE_INITIALIZED);
        collab(sh, "rej", "owner", Collaboration.STATE_REJECTED);
        collab(sh, "owner", " cpad ", Collaboration.STATE_APPROVED); // padded counterpart
        collab(sh, "o'neal", "ann", Collaboration.STATE_APPROVED); // a quote in the owner's name
        collab(sh, "owner", "qu\"ote", Collaboration.STATE_APPROVED); // getUser must bind these
        collab(sh, "owner", "back\\slash", Collaboration.STATE_APPROVED);
        Organization orgA = org(sh, "orgA");
        Organization orgB = org(sh, "orgB");
        Organization filler = org(sh, "filler"); // no orgAdmin: grants nothing; pads positions
        Organization filler2 = org(sh, "filler2");
        Organization filler3 = org(sh, "filler3");
        memberships(sh, "orgadmin", orgA, orgB);   // orgadmin manages two organizations: (A,0) (B,1)
        memberships(sh, "memberA", filler, orgA);  // (filler,0) (A,1)
        memberships(sh, "memberB", orgB);          // (B,0)
        memberships(sh, "padmem", filler2, filler3, orgA); // (A,2); submits as " padmem "
        encounter(sh, "e1", "owner", "Komodo");
        encounter(sh, "e2", "memberA", "Komodo");
        encounter(sh, "e3", "orgadmin", "Flores Sea");
        encounter(sh, "e4", "lake", "Komodo");        // the owner holds a covering role
        encounter(sh, "e5", "ghost", "Komodo");       // owner has no user row
        encounter(sh, "e6", " ", "Komodo");           // blank owner: anonymous after trim
        encounter(sh, "e7", "public", "Komodo");      // anonymous
        encounter(sh, "e8", "owner", "Atlantis");     // location not in the tree
        encounter(sh, "e9", "memberB", "PAKISTAN - North");
        encounter(sh, "e10", "pad", "Komodo");        // padded-role owner
        encounter(sh, "e11", "owner", "Lab");         // lineage {researcher, Lab}: researcher excluded
        encounter(sh, "e12", " padmem ", "Komodo");   // padded submitter: getUser trims, orgs apply
        encounter(sh, "e13", "o'neal", "Komodo");     // the collaboration query must not break
    }

    private static Set<String> ids(String... usernames) {
        Set<String> s = new TreeSet<String>();
        for (String u : usernames) s.add(uuid.get(u));
        return s;
    }

    /** What both writers must produce, stated independently of either implementation. */
    private static Map<String, Set<String> > expected() {
        Map<String, Set<String> > e = new HashMap<String, Set<String> >();
        e.put("e1", ids("ann", "ed", "lake", "pad", "cpad", "qu\"ote", "back\\slash"));
        e.put("e2", ids("orgadmin", "lake", "pad"));
        e.put("e3", ids("lake", "pad"));          // members never see the orgAdmin's own
        e.put("e4", ids("pad"));                  // lake owns it: never listed
        e.put("e5", ids("lake", "pad"));          // invalid owner: location grants only
        e.put("e8", ids("ann", "ed", "cpad", "qu\"ote", "back\\slash"));
        e.put("e9", ids("orgadmin", "far"));
        e.put("e10", ids("lake"));                // pad owns it: never listed
        e.put("e11", ids("ann", "ed", "cpad", "qu\"ote", "back\\slash"));
        e.put("e12", ids("orgadmin", "lake", "pad"));
        e.put("e13", ids("ann", "lake", "pad"));
        return e;
    }

    // ---- running the real pass against the fixture database ----

    private static class PassRun {
        final Map<String, Set<String> > written = new HashMap<String, Set<String> >();
        final Set<String> enqueued = new TreeSet<String>();
        boolean completed;
    }

    private static PassRun runPass(final Map<String, JSONArray> indexed) {
        final PassRun run = new PassRun();
        IndexingManager im = mock(IndexingManager.class);
        doAnswer(inv -> {
            run.enqueued.add(((Base)inv.getArgument(0)).getId());
            return null;
        }).when(im).addIndexingQueueEntry(any(), anyBoolean());
        try (MockedConstruction<OpenSearch> searches = mockConstruction(OpenSearch.class,
                (mock, ctx) -> {
                    when(mock.getIndexedViewUsers(anyString(), anyString())).thenAnswer(inv ->
                        indexed.get((String)inv.getArgument(1)));
                    // the pass's partial update changes the indexed viewUsers (stateful)
                    doAnswer(inv -> {
                        JSONObject doc = inv.getArgument(2);
                        JSONArray vu = doc.optJSONArray("viewUsers");
                        Set<String> set = new TreeSet<String>();
                        if (vu != null) for (int j = 0; j < vu.length(); j++) set.add(vu.getString(j));
                        String id = inv.getArgument(1);
                        run.written.put(id, set);
                        if (indexed.containsKey(id)) indexed.put(id, new JSONArray(set));
                        return null;
                    }).when(mock).indexUpdate(eq("encounter"), anyString(), any(JSONObject.class));
                });
            MockedStatic<IndexingManagerFactory> factory = mockStatic(IndexingManagerFactory.class);
            MockedStatic<OpenSearch> osStatic = mockStatic(OpenSearch.class)) {
            factory.when(IndexingManagerFactory::getIndexingManager).thenReturn(im);
            osStatic.when(OpenSearch::skipAutoIndexing).thenReturn(false);
            run.completed = Encounter.opensearchIndexPermissions();
        }
        return run;
    }

    /** A full reindex of the given encounters: what the REAL serializer emits replaces the
     *  indexed viewUsers, exactly as OpenSearch.index() replaces the whole document. */
    private static void fullReindex(Map<String, JSONArray> indexed, Collection<String> ids)
    throws Exception {
        Shepherd sh = open();
        try {
            for (String id : ids) indexed.put(id, new JSONArray(serialized(sh, id)));
        } finally {
            sh.rollbackAndClose();
        }
    }

    private static Shepherd open() {
        Shepherd sh = new Shepherd("context0");
        sh.setAction("ViewUsersParityDbTest");
        sh.beginDBTransaction();
        return sh;
    }

    private static Set<String> computed(Shepherd sh, String id) {
        return new TreeSet<String>(sh.getEncounter(id).computeViewUsers(sh));
    }

    private static Set<String> serialized(Shepherd sh, String id) throws Exception {
        StringWriter sw = new StringWriter();
        JsonGenerator jg = new JsonFactory().createGenerator(sw);
        jg.writeStartObject();
        sh.getEncounter(id).opensearchDocumentSerializer(jg, sh);
        jg.writeEndObject();
        jg.close();
        JSONArray vu = new JSONObject(sw.toString()).getJSONArray("viewUsers");
        Set<String> set = new TreeSet<String>();
        for (int i = 0; i < vu.length(); i++) set.add(vu.getString(i));
        return set;
    }

    private static void revoke(String username, String rolename) {
        Shepherd sh = open();
        try (MockedStatic<OpenSearch> os = mockStatic(OpenSearch.class)) {
            Query q = sh.getPM().newQuery(Role.class,
                "username == :u && rolename == :r && context == 'context0'");
            Collection c = (Collection)q.execute(username, rolename);
            assertEquals(1, c.size(), "fixture role to revoke");
            sh.getPM().deletePersistentAll(new ArrayList(c));
            q.closeAll();
            sh.commitDBTransaction();
        } finally {
            sh.rollbackAndClose();
        }
    }

    /** The pass must see a failed organization read as a failure (abort, retry next tick),
     *  never as "no organizations": the lenient read hides it as an empty catalog. */
    @Test void strictOrganizationReadPropagatesADatastoreFailure() {
        Shepherd sh = open();
        sh.rollbackAndClose(); // closed PersistenceManager: every read from here on fails
        assertTrue(sh.getAllOrganizations().isEmpty(), "the lenient read swallows the failure");
        org.junit.jupiter.api.Assertions.assertThrows(javax.jdo.JDOException.class,
            () -> sh.getAllOrganizationsStrict(), "the strict read propagates it");
    }

    @Test void passAndSerializerAgreeThenConvergeAndRevoke() throws Exception {
        Map<String, Set<String> > expected = expected();

        // ---- pass 1: nothing indexed yet (snapshot unknown) ----
        PassRun first = runPass(new HashMap<String, JSONArray>());
        assertTrue(first.completed);
        assertEquals(expected.keySet(), first.written.keySet(),
            "written exactly for the private, resolvable-owner encounters");
        for (String id : expected.keySet()) {
            assertEquals(expected.get(id), first.written.get(id), "pass viewUsers for " + id);
        }
        assertTrue(first.enqueued.isEmpty(),
            "unknown snapshots never storm; an invalid owner is written inline, not reindexed");

        // ---- the other writer: computeViewUsers and the real serializer on the same rows ----
        Shepherd sh = open();
        try {
            for (String id : expected.keySet()) {
                assertEquals(expected.get(id), computed(sh, id), "computeViewUsers for " + id);
                assertEquals(expected.get(id), serialized(sh, id), "serializer viewUsers for " + id);
            }
            for (String id : new String[] { "e6", "e7" }) {
                assertTrue(computed(sh, id).isEmpty(), id + " is anonymous-owned: no viewers");
                assertTrue(serialized(sh, id).isEmpty(), id + " serializes an empty viewUsers");
            }
        } finally {
            sh.rollbackAndClose();
        }

        // ---- full reindex of everything (the real serializer), then another pass: quiet ----
        Map<String, JSONArray> indexed = new HashMap<String, JSONArray>();
        fullReindex(indexed, expected.keySet());
        PassRun second = runPass(indexed);
        assertEquals(first.written, second.written, "steady state rewrites the same sets");
        assertTrue(second.enqueued.isEmpty(),
            "an unchanged indexed set must not be reindexed (the #1779 loop)");

        // ---- revocation: lake loses Indonesia; only the encounters whose set changes refresh ----
        revoke("lake", "Indonesia");
        PassRun fourth = runPass(indexed);
        Set<String> lakeGone = new TreeSet<String>();
        for (Map.Entry<String, Set<String> > e : expected.entrySet()) {
            Set<String> after = new TreeSet<String>(e.getValue());
            if (after.remove(uuid.get("lake"))) lakeGone.add(e.getKey());
            assertEquals(after, fourth.written.get(e.getKey()), "after revocation: " + e.getKey());
        }
        assertEquals(lakeGone, fourth.enqueued, "only the encounters whose set changed");
        assertFalse(fourth.written.get("e10").contains(uuid.get("lake")));
        // the partial update already made the indexed state agree: a further pass is quiet
        PassRun fifth = runPass(indexed);
        assertTrue(fifth.enqueued.isEmpty(),
            "once written, the revocation does not keep re-refreshing");
        sh = open();
        try {
            assertEquals(fourth.written.get("e1"), computed(sh, "e1"), "both writers see the revocation");
        } finally {
            sh.rollbackAndClose();
        }
    }
}
