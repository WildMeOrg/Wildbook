package org.ecocean.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import javax.jdo.Query;
import org.ecocean.Annotation;
import org.ecocean.CommonConfiguration;
import org.ecocean.Encounter;
import org.ecocean.MarkedIndividual;
import org.ecocean.OpenSearch;
import org.ecocean.Organization;
import org.ecocean.Role;
import org.ecocean.User;
import org.ecocean.security.PermissionsAudit.AclTuple;
import org.ecocean.security.PermissionsAudit.Result;
import org.ecocean.security.PermissionsAudit.Snapshot;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.shepherd.core.TestPMFUtil;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Parity on a real database: the ACL tuples the permissions audit expects (PermissionsAudit.
 * Snapshot, built by the audit's own snapshot loader) must equal what the serializers write for
 * every encounter, annotation and individual document. Any divergence would make the audit and a
 * serializer alternate on the affected documents. The fixture collects every username and grant
 * shape that has bitten before: padded names, quotes and backslashes, invalid and anonymous owners,
 * orgAdmins, system role names that are also locations, one and two parents, orphans, and
 * individuals with no, one and several members of mixed visibility.
 */
@Testcontainers class ViewUsersParityDbTest {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
        .withDatabaseName("wildbook_test").withUsername("wildbook").withPassword("wildbook");

    private static JSONObject previousTree;
    private static boolean hadPreviousConfig;
    private static Properties previousConfig;
    private static final Map<String, String> uuid = new HashMap<String, String>();
    private static final Map<String, String> individualIds = new HashMap<String, String>();

    @SuppressWarnings("unchecked")
    private static Map<String, Properties> configCache() throws Exception {
        Field f = CommonConfiguration.class.getDeclaredField("contextToPropsCache");
        f.setAccessible(true);
        return (Map<String, Properties>)f.get(null);
    }

    @BeforeAll static void setUp() throws Exception {
        previousTree = LocationRoleTestTree.inject();
        hadPreviousConfig = configCache().containsKey("context0");
        previousConfig = configCache().get("context0");
        Properties cfg = new Properties();
        cfg.setProperty("collaborationSecurityEnabled", "true");
        CommonConfiguration.initialize("context0", cfg);
        TestPMFUtil.closePMF("context0");

        Properties props = new Properties();
        props.setProperty("datanucleus.ConnectionUserName", postgres.getUsername());
        props.setProperty("datanucleus.ConnectionPassword", postgres.getPassword());
        props.setProperty("datanucleus.ConnectionDriverName", postgres.getDriverClassName());
        props.setProperty("datanucleus.ConnectionURL", postgres.getJdbcUrl());
        props.setProperty("datanucleus.schema.autoCreateTables", "true");
        Shepherd sh = new Shepherd("context0", props);
        // no OpenSearch exists here; indexing side effects are kept out of the seeding
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

    @AfterAll static void tearDown() throws Exception {
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

    private static Encounter encounter(Shepherd sh, String id, String submitter, String locationID) {
        Encounter enc = new Encounter();
        enc.setCatalogNumber(id);
        enc.setSubmitterID(submitter);
        enc.setLocationID(locationID);
        enc.setSkipAutoIndexing(true); // no indexing queue during seeding
        sh.getPM().makePersistent(enc);
        return enc;
    }

    private static void annotation(Shepherd sh, String id, boolean eligible, Encounter... parents) {
        Annotation ann = new Annotation();
        ann.setId(id);
        ann.setMatchAgainst(eligible);
        ann.setSkipAutoIndexing(true);
        sh.getPM().makePersistent(ann);
        for (Encounter enc : parents) enc.addAnnotation(ann);
    }

    private static void individual(Shepherd sh, String name, Encounter... members) {
        MarkedIndividual indiv = new MarkedIndividual();
        indiv.setSkipAutoIndexing(true);
        for (Encounter enc : members) indiv.addEncounter(enc);
        sh.getPM().makePersistent(indiv);
        individualIds.put(name, indiv.getId());
    }

    private static void seed(Shepherd sh) {
        for (String u : new String[] { "owner", "ann", "ed", "pen", "rej", "orgadmin", "memberA",
            "memberB", "lake", "far", "pad", "sys", "cpad", "padmem", "o'neal", "qu\"ote",
            "back\\slash", "public" }) user(sh, u); // yes, a user literally named "public"
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
        Encounter e1 = encounter(sh, "e1", "owner", "Komodo");
        Encounter e2 = encounter(sh, "e2", "memberA", "Komodo");
        encounter(sh, "e3", "orgadmin", "Flores Sea");
        encounter(sh, "e4", "lake", "Komodo");        // the owner holds a covering role
        Encounter e5 = encounter(sh, "e5", "ghost", "Komodo");       // owner has no user row
        encounter(sh, "e6", " ", "Komodo");           // blank owner: anonymous after trim
        Encounter e7 = encounter(sh, "e7", "public", "Komodo");      // anonymous by name, yet a user named public exists
        encounter(sh, "e8", "owner", "Atlantis");     // location not in the tree
        encounter(sh, "e9", "memberB", "PAKISTAN - North");
        encounter(sh, "e10", "pad", "Komodo");        // padded-role owner
        encounter(sh, "e11", "owner", "Lab");         // lineage {researcher, Lab}: researcher excluded
        encounter(sh, "e12", " padmem ", "Komodo");   // padded submitter: getUser trims, orgs apply
        encounter(sh, "e13", "o'neal", "Komodo");     // the collaboration query must not break
        annotation(sh, "a1", true, e1);               // one private parent
        annotation(sh, "a7", true, e7);               // one public parent
        annotation(sh, "a5", true, e5);               // parent with an unresolvable owner
        annotation(sh, "aOrphan", true);              // eligible, no parent
        annotation(sh, "aTwo", true, e1, e2);         // two parents
        annotation(sh, "aTrivial", false, e1);        // not eligible: no document at all
        individual(sh, "iMixed", e1, e7);             // private + public member
        individual(sh, "iPrivate", e2);               // one private member
        individual(sh, "iGhost", e5);                 // member with an unresolvable owner
        individual(sh, "iNone");                      // no members
    }

    private static Set<String> ids(String... usernames) {
        Set<String> s = new TreeSet<String>();
        for (String u : usernames) s.add(uuid.get(u));
        return s;
    }

    /** What every writer must produce for the viewers, stated independently of any implementation. */
    private static Map<String, Set<String> > expectedViewers() {
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

    // ---- the writers ----

    private static Shepherd open() {
        Shepherd sh = new Shepherd("context0");
        sh.setAction("ViewUsersParityDbTest");
        sh.beginDBTransaction();
        return sh;
    }

    private static Set<String> computed(Shepherd sh, String id) {
        return new TreeSet<String>(sh.getEncounter(id).computeViewUsers(sh));
    }

    private static JSONObject encounterDocument(Shepherd sh, String id) throws Exception {
        StringWriter sw = new StringWriter();
        JsonGenerator jg = new JsonFactory().createGenerator(sw);
        jg.writeStartObject();
        sh.getEncounter(id).opensearchDocumentSerializer(jg, sh);
        jg.writeEndObject();
        jg.close();
        return new JSONObject(sw.toString());
    }

    private static JSONObject annotationAcl(Shepherd sh, String id) throws Exception {
        StringWriter sw = new StringWriter();
        JsonGenerator jg = new JsonFactory().createGenerator(sw);
        jg.writeStartObject();
        sh.getAnnotation(id).writeAclFields(jg, sh);
        jg.writeEndObject();
        jg.close();
        return new JSONObject(sw.toString());
    }

    private static JSONObject individualAcl(Shepherd sh, String id) throws Exception {
        StringWriter sw = new StringWriter();
        JsonGenerator jg = new JsonFactory().createGenerator(sw);
        jg.writeStartObject();
        sh.getMarkedIndividual(id).writeAclFields(jg, sh);
        jg.writeEndObject();
        jg.close();
        return new JSONObject(sw.toString());
    }

    private static Set<String> strings(JSONObject doc, String field) {
        Set<String> s = new TreeSet<String>();
        JSONArray a = doc.optJSONArray(field);
        if (a != null) for (int i = 0; i < a.length(); i++) s.add(a.getString(i));
        return s;
    }

    /** The encounter document's tuple as the serializer wrote it. */
    private static AclTuple serializedEncounter(JSONObject doc) {
        Set<String> owners = new TreeSet<String>();
        if (doc.has("submitterUserId") && !doc.isNull("submitterUserId"))
            owners.add(doc.getString("submitterUserId"));
        return new AclTuple(doc.getBoolean("publiclyReadable"), owners, strings(doc, "viewUsers"));
    }

    /** A child document's tuple as writeAclFields wrote it. */
    private static AclTuple serializedChild(JSONObject acl) {
        return new AclTuple(acl.getBoolean("publiclyReadable"), strings(acl, "submitterUserIds"),
            strings(acl, "viewUsers"));
    }

    private static Snapshot snapshot() {
        Result r = new Result();
        Snapshot s = PermissionsAudit.loadSnapshot("context0", r);
        assertNotNull(s, "snapshot: " + r.reasons);
        return s;
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

    // ---- parity ----

    private static void assertEncounterParity(Snapshot s, Shepherd sh, Map<String, Set<String> > viewers)
    throws Exception {
        for (String id : new String[] { "e1", "e2", "e3", "e4", "e5", "e6", "e7", "e8", "e9", "e10",
            "e11", "e12", "e13" }) {
            AclTuple expected = s.expectedEncounter(id);
            assertNotNull(expected, id);
            assertEquals(serializedEncounter(encounterDocument(sh, id)), expected,
                "encounter document tuple for " + id);
            assertEquals(new TreeSet<String>(expected.viewers), computed(sh, id), "computeViewUsers for " + id);
            if (viewers.containsKey(id)) assertEquals(viewers.get(id), new TreeSet<String>(expected.viewers), id);
        }
    }

    private static void assertChildParity(Snapshot s, Shepherd sh) throws Exception {
        for (String id : new String[] { "a1", "a7", "a5", "aOrphan", "aTwo" }) {
            assertEquals(serializedChild(annotationAcl(sh, id)), s.expectedAnnotation(id), "annotation " + id);
        }
        assertNull(s.expectedAnnotation("aTrivial"), "not eligible: no expectation, no document");
        for (String name : new String[] { "iMixed", "iPrivate", "iGhost", "iNone" }) {
            String id = individualIds.get(name);
            assertEquals(serializedChild(individualAcl(sh, id)), s.expectedIndividual(id), "individual " + name);
        }
    }

    @Test void auditExpectationsEqualTheSerializersOnEveryDocumentKind() throws Exception {
        Map<String, Set<String> > viewers = expectedViewers();
        Snapshot s = snapshot();
        Shepherd sh = open();
        try {
            assertEncounterParity(s, sh, viewers);
            // anonymous owners: public, no viewers; e7's owner resolves to the user named "public"
            assertTrue(s.expectedEncounter("e6").publiclyReadable);
            assertTrue(s.expectedEncounter("e6").owners.isEmpty());
            assertTrue(s.expectedEncounter("e7").publiclyReadable);
            assertEquals(ids("public"), new TreeSet<String>(s.expectedEncounter("e7").owners));
            assertTrue(s.expectedEncounter("e5").owners.isEmpty(), "unresolvable owner: no owner id");
            assertChildParity(s, sh);
            // the shapes the children must take
            assertEquals(AclTuple.DENY, s.expectedAnnotation("aOrphan"));
            assertEquals(AclTuple.DENY, s.expectedAnnotation("aTwo"));
            assertTrue(s.expectedAnnotation("a7").publiclyReadable);
            assertTrue(s.expectedAnnotation("a7").owners.isEmpty(), "a public parent projects no owner");
            assertTrue(s.expectedIndividual(individualIds.get("iMixed")).publiclyReadable);
            assertEquals(ids("owner"), new TreeSet<String>(s.expectedIndividual(individualIds.get("iMixed")).owners));
            assertTrue(s.expectedIndividual(individualIds.get("iNone")).publiclyReadable);
        } finally {
            sh.rollbackAndClose();
        }

        // a revocation changes both writers the same way, nowhere else
        revoke("lake", "Indonesia");
        Map<String, Set<String> > after = new HashMap<String, Set<String> >();
        for (Map.Entry<String, Set<String> > e : viewers.entrySet()) {
            Set<String> v = new TreeSet<String>(e.getValue());
            v.remove(uuid.get("lake"));
            after.put(e.getKey(), v);
        }
        Snapshot s2 = snapshot();
        sh = open();
        try {
            assertEncounterParity(s2, sh, after);
            assertChildParity(s2, sh);
        } finally {
            sh.rollbackAndClose();
        }
    }

    /** The audit must see a failed organization read as a failure (no snapshot, nothing audited),
     *  never as "no organizations": the lenient read hides it as an empty catalog. */
    @Test void strictOrganizationReadPropagatesADatastoreFailure() {
        Shepherd sh = open();
        sh.rollbackAndClose(); // closed PersistenceManager: every read from here on fails
        assertTrue(sh.getAllOrganizations().isEmpty(), "the lenient read swallows the failure");
        org.junit.jupiter.api.Assertions.assertThrows(javax.jdo.JDOException.class,
            () -> sh.getAllOrganizationsStrict(), "the strict read propagates it");
    }

    /** The snapshot runs under repeatable read on a real database (the level is verified, not assumed). */
    @Test void snapshotTransactionIsRepeatableRead() throws Exception {
        Shepherd sh = new Shepherd("context0");
        sh.setAction("ViewUsersParityDbTest.isolation");
        try {
            PermissionsAudit.beginRepeatableRead(sh);
            assertEquals("repeatable-read", sh.getPM().currentTransaction().getIsolationLevel());
            assertTrue(sh.getPM().currentTransaction().isActive());
        } finally {
            sh.rollbackAndClose();
        }
    }
}
