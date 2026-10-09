package org.ecocean.security;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.jdo.PersistenceManager;
import javax.jdo.Query;
import javax.jdo.Transaction;
import org.ecocean.Annotation;
import org.ecocean.Base;
import org.ecocean.OpenSearch;
import org.ecocean.Organization;
import org.ecocean.Role;
import org.ecocean.User;
import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * The permissions audit: the OpenSearch encounter, annotation and individual documents each carry
 * an ACL (publiclyReadable, owner id(s), viewUsers) written by their serializers. This class holds
 * the rules that say what each document MUST carry, computed from one database snapshot, and how
 * an indexed document is read (doc values) and compared. The rules mirror the serializers exactly
 * (Encounter.opensearchDocumentSerializer, Encounter.opensearchAclFields, Annotation.writeAclFields,
 * MarkedIndividual.writeAclFields); any divergence makes the audit and a serializer alternate on the
 * affected documents, which ViewUsersParityDbTest guards on a real database.
 */
public final class PermissionsAudit {
    private PermissionsAudit() {}

    /** An ACL tuple as the index should carry it. Owners is a set so the same type serves the
     *  encounter document (single submitterUserId) and the child documents (submitterUserIds). */
    public static final class AclTuple {
        public static final AclTuple DENY = new AclTuple(false, Collections.<String>emptySet(),
            Collections.<String>emptySet());
        public final boolean publiclyReadable;
        public final Set<String> owners;
        public final Set<String> viewers;

        public AclTuple(boolean publiclyReadable, Set<String> owners, Set<String> viewers) {
            this.publiclyReadable = publiclyReadable;
            this.owners = Collections.unmodifiableSet(new HashSet<String>(owners));
            this.viewers = Collections.unmodifiableSet(new HashSet<String>(viewers));
        }

        /** Nobody but admins: the fail-closed tuple. */
        public boolean isDeny() {
            return !publiclyReadable && owners.isEmpty() && viewers.isEmpty();
        }

        @Override public boolean equals(Object o) {
            if (!(o instanceof AclTuple)) return false;
            AclTuple t = (AclTuple)o;
            return (publiclyReadable == t.publiclyReadable) && owners.equals(t.owners) &&
                       viewers.equals(t.viewers);
        }

        @Override public int hashCode() {
            return (publiclyReadable ? 1 : 0) + 31 * owners.hashCode() + 961 * viewers.hashCode();
        }

        @Override public String toString() {
            return "AclTuple[public=" + publiclyReadable + " owners=" + owners + " viewers=" +
                       viewers + "]";
        }
    }

    /** One hit of a doc-values scroll, as the audit reads it. */
    public static final class IndexedDoc {
        public String id;
        public long seqNo;
        public long primaryTerm;
        public Boolean publiclyReadable; // null = the field is absent (never assumed)
        public Set<String> owners = Collections.<String>emptySet();
        public Set<String> viewers = Collections.<String>emptySet();
        public List<String> encounterId = Collections.<String>emptyList(); // annotation linkage
        public Set<String> encounterIds = Collections.<String>emptySet(); // individual linkage
        public String error; // non-null = the hit is unusable; the audit of this index aborts
    }

    /**
     * Everything the audit reads from the database, materialized in one repeatable-read
     * transaction and immutable afterwards. Expected tuples are computed on demand, never stored.
     */
    public static final class Snapshot {
        // Phase data is released once the phase that needs it is over (releaseAfter), so the
        // three inventories never have to coexist with later phases' work.
        Map<String, String[]> encounters; // id -> { submitter, locationID }
        Map<String, String> usernameToId; // stored username -> user id
        Map<String, Set<String> > collabGrants; // raw submitter string -> counterpart ids
        Map<String, Set<String> > orgGrants; // owner id -> orgAdmin ids
        Map<String, Set<String> > roleNameToUserIds;
        final Map<String, Set<String> > lineageCache = new HashMap<String, Set<String> >();
        Set<String> eligibleAnnotations; // annotations that should have an index document
        Map<String, Set<String> > annotationLinks; // annotation id -> linked encounter ids
        Set<String> individuals;
        Map<String, Set<String> > individualMembers; // individual id -> member encounter ids

        public Snapshot(Map<String, String[]> encounters, Map<String, String> usernameToId,
            Map<String, Set<String> > collabGrants, Map<String, Set<String> > orgGrants,
            Map<String, Set<String> > roleNameToUserIds, Set<String> eligibleAnnotations,
            Map<String, Set<String> > annotationLinks, Set<String> individuals,
            Map<String, Set<String> > individualMembers) {
            this.encounters = Collections.unmodifiableMap(encounters);
            this.usernameToId = Collections.unmodifiableMap(usernameToId);
            this.collabGrants = Collections.unmodifiableMap(collabGrants);
            this.orgGrants = Collections.unmodifiableMap(orgGrants);
            this.roleNameToUserIds = Collections.unmodifiableMap(roleNameToUserIds);
            this.eligibleAnnotations = Collections.unmodifiableSet(eligibleAnnotations);
            this.annotationLinks = Collections.unmodifiableMap(annotationLinks);
            this.individuals = Collections.unmodifiableSet(individuals);
            this.individualMembers = Collections.unmodifiableMap(individualMembers);
        }

        public int encounterCount() {
            return encounters.size();
        }

        public int annotationCount() {
            return eligibleAnnotations.size();
        }

        public int individualCount() {
            return individuals.size();
        }

        /** Drop what no later phase needs: after the annotation phase its inventory and links;
         *  after the individual phase everything. */
        void releaseAfter(String index) {
            if ("annotation".equals(index)) {
                eligibleAnnotations = Collections.<String>emptySet();
                annotationLinks = Collections.<String, Set<String> >emptyMap();
            } else if ("individual".equals(index)) {
                individuals = Collections.<String>emptySet();
                individualMembers = Collections.<String, Set<String> >emptyMap();
                encounters = Collections.<String, String[]>emptyMap();
                collabGrants = Collections.<String, Set<String> >emptyMap();
                orgGrants = Collections.<String, Set<String> >emptyMap();
                roleNameToUserIds = Collections.<String, Set<String> >emptyMap();
                usernameToId = Collections.<String, String>emptyMap();
                lineageCache.clear();
            }
        }

        /**
         * The encounter document's tuple. The serializer resolves and writes the owner even for an
         * anonymous-by-name owner (a user literally named "public" has an id), so the owner is
         * resolved regardless of the public flag; viewers are computed only for a private owner.
         */
        public AclTuple expectedEncounter(String id) {
            String[] row = encounters.get(id);
            if (row == null) return null;
            String submitter = row[0];
            String locationID = row[1];
            String owner = userIdForUsername(usernameToId, submitter);
            Set<String> owners = (owner == null) ? Collections.<String>emptySet() :
                Collections.singleton(owner);
            if (User.isUsernameAnonymous(submitter)) {
                return new AclTuple(true, owners, Collections.<String>emptySet());
            }
            Set<String> viewers = new LinkedHashSet<String>();
            viewers.addAll(LocationRoleAccess.viewUserIdsForLocation(locationID, roleNameToUserIds,
                lineageCache));
            if (owner != null) { // owner-dependent grants fail closed for an unresolvable owner
                Set<String> collab = collabGrants.get(submitter);
                if (collab != null) viewers.addAll(collab);
                Set<String> org = orgGrants.get(owner);
                if (org != null) viewers.addAll(org);
                viewers.remove(owner); // the owner is granted as owner, never listed as a viewer
            }
            return new AclTuple(false, owners, viewers);
        }

        /** What a child document copies from one parent: Encounter.opensearchAclFields omits the
         *  owner and the viewers of a public parent. */
        private static AclTuple childProjection(AclTuple parent) {
            if (parent.publiclyReadable) {
                return new AclTuple(true, Collections.<String>emptySet(),
                    Collections.<String>emptySet());
            }
            return parent;
        }

        /** Linked encounters that exist (Encounter.findAllByAnnotation ignores dangling rows). */
        public Set<String> parentsOf(String annotationId) {
            Set<String> linked = annotationLinks.get(annotationId);
            if (linked == null) return Collections.<String>emptySet();
            Set<String> parents = new LinkedHashSet<String>();
            for (String encId : linked) {
                if (encounters.containsKey(encId)) parents.add(encId);
            }
            return parents;
        }

        /** Annotation.writeAclFields: exactly one parent copies it; none or several fail closed.
         *  Null for an annotation that should not have a document at all. */
        public AclTuple expectedAnnotation(String id) {
            if (!eligibleAnnotations.contains(id)) return null;
            Set<String> parents = parentsOf(id);
            if (parents.size() != 1) return AclTuple.DENY;
            return childProjection(expectedEncounter(parents.iterator().next()));
        }

        /** Member encounters that exist. */
        public Set<String> membersOf(String individualId) {
            Set<String> linked = individualMembers.get(individualId);
            if (linked == null) return Collections.<String>emptySet();
            Set<String> members = new LinkedHashSet<String>();
            for (String encId : linked) {
                if (encounters.containsKey(encId)) members.add(encId);
            }
            return members;
        }

        /** MarkedIndividual.writeAclFields: public if any member is (or there are none); owners and
         *  viewers are the union over the private members' projections. Null for no such individual. */
        public AclTuple expectedIndividual(String id) {
            if (!individuals.contains(id)) return null;
            Set<String> members = membersOf(id);
            if (members.isEmpty()) {
                return new AclTuple(true, Collections.<String>emptySet(),
                    Collections.<String>emptySet());
            }
            boolean pub = false;
            Set<String> owners = new LinkedHashSet<String>();
            Set<String> viewers = new LinkedHashSet<String>();
            for (String encId : members) {
                AclTuple t = childProjection(expectedEncounter(encId));
                if (t.publiclyReadable) {
                    pub = true;
                    continue;
                }
                owners.addAll(t.owners);
                viewers.addAll(t.viewers);
            }
            return new AclTuple(pub, owners, viewers);
        }
    }

    /** The user id for a stored username, resolved the way Shepherd.getUser resolves it (the lookup
     *  key is trimmed, stored usernames are matched exactly). */
    public static String userIdForUsername(Map<String, String> usernameToId, String username) {
        if ((usernameToId == null) || (username == null)) return null;
        return usernameToId.get(username.trim());
    }

    // ---- reading the index ----

    /**
     * Parse one scroll hit requested with _source:false, docvalue_fields and seq_no_primary_term.
     * Doc values come back as arrays, scalars as single-element arrays; an absent field is absent.
     * Anything unexpected sets {@code error} rather than being guessed at.
     */
    public static IndexedDoc parseHit(JSONObject hit, String ownerField) {
        IndexedDoc doc = new IndexedDoc();
        if (hit == null) {
            doc.error = "null hit";
            return doc;
        }
        doc.id = hit.optString("_id", null);
        if (doc.id == null) {
            doc.error = "hit without _id";
            return doc;
        }
        if (!hit.has("_seq_no") || hit.isNull("_seq_no") || !hit.has("_primary_term") ||
            hit.isNull("_primary_term")) {
            doc.error = "hit " + doc.id + " without _seq_no/_primary_term";
            return doc;
        }
        try {
            doc.seqNo = hit.getLong("_seq_no");
            doc.primaryTerm = hit.getLong("_primary_term");
        } catch (Exception ex) {
            doc.error = "hit " + doc.id + " with non-numeric _seq_no/_primary_term";
            return doc;
        }
        Object rawFields = hit.opt("fields");
        if ((rawFields != null) && !(rawFields instanceof JSONObject)) {
            doc.error = "hit " + doc.id + " with fields that are not an object";
            return doc;
        }
        JSONObject fields = (rawFields == null) ? new JSONObject() : (JSONObject)rawFields;
        Object rawFlag = fields.opt("publiclyReadable");
        if (rawFlag != null) {
            if (!(rawFlag instanceof JSONArray) || (((JSONArray)rawFlag).length() != 1) ||
                !(((JSONArray)rawFlag).opt(0) instanceof Boolean)) {
                doc.error = "hit " + doc.id + " with a malformed publiclyReadable: " + rawFlag;
                return doc;
            }
            doc.publiclyReadable = ((JSONArray)rawFlag).getBoolean(0);
        }
        try {
            doc.owners = new LinkedHashSet<String>(strings(fields, ownerField));
            doc.viewers = new LinkedHashSet<String>(strings(fields, "viewUsers"));
            doc.encounterId = strings(fields, "encounterId");
            doc.encounterIds = new LinkedHashSet<String>(strings(fields, "encounterIds"));
        } catch (IllegalArgumentException ex) {
            doc.error = "hit " + doc.id + ": " + ex.getMessage();
        }
        return doc;
    }

    /** The strings of an array field; absent is empty, anything that is not an array of strings
     *  is malformed (never read as absence). */
    static List<String> strings(JSONObject fields, String name) {
        List<String> out = new ArrayList<String>();
        Object raw = fields.opt(name);
        if ((raw == null) || JSONObject.NULL.equals(raw)) return out;
        if (!(raw instanceof JSONArray))
            throw new IllegalArgumentException(name + " is not an array: " + raw);
        JSONArray arr = (JSONArray)raw;
        for (int i = 0; i < arr.length(); i++) {
            Object v = arr.opt(i);
            if (!(v instanceof String))
                throw new IllegalArgumentException("non-string value in " + name + ": " + arr);
            out.add((String)v);
        }
        return out;
    }

    /** Set comparison on all three fields; an absent public flag never matches. */
    public static boolean matches(IndexedDoc doc, AclTuple expected) {
        if ((doc == null) || (expected == null) || (doc.publiclyReadable == null)) return false;
        return (doc.publiclyReadable.booleanValue() == expected.publiclyReadable) &&
                   doc.owners.equals(expected.owners) && doc.viewers.equals(expected.viewers);
    }

    /** Null when a scroll page is usable; otherwise the reason the audit of this index aborts. */
    public static String validatePage(JSONObject page) {
        if (page == null) return "no page";
        if (page.optBoolean("timed_out", false)) return "timed_out";
        JSONObject shards = page.optJSONObject("_shards");
        if (shards == null) return "no _shards";
        if (shards.optInt("failed", 0) > 0) return "shard failures: " + shards.optInt("failed");
        JSONObject hits = page.optJSONObject("hits");
        if ((hits == null) || (hits.optJSONArray("hits") == null)) return "no hits envelope";
        Object scrollId = page.opt("_scroll_id");
        if (!(scrollId instanceof String) || ((String)scrollId).isEmpty()) return "no usable _scroll_id";
        return null;
    }

    // ---- running the audit (skeleton: specified by PermissionsAuditRunTest) ----

    /** Tunables of one audit run. */
    public static final class Config {
        public final int pageSize;
        public final int repairCap;
        public final int rebuildCap;
        public final String keepAlive;

        public Config(int pageSize, int repairCap, int rebuildCap, String keepAlive) {
            this.pageSize = pageSize;
            this.repairCap = repairCap;
            this.rebuildCap = rebuildCap;
            this.keepAlive = keepAlive;
        }
    }

    /** Per-index counters of one audit run. */
    public static final class IndexCounters {
        public int scanned;
        public int unknown;
        public int repaired;
        public int conflicts;
        public int structural;
        public int deferred;
        public int failed;

        @Override public String toString() {
            return "scanned=" + scanned + " unknown=" + unknown + " repaired=" + repaired +
                       " conflicts=" + conflicts + " structural=" + structural + " deferred=" +
                       deferred + " failed=" + failed;
        }
    }

    /** Outcome of one audit run. Completion only selects the next delay; nothing is acknowledged. */
    public static final class Result {
        public boolean completed;
        public boolean capHit;
        public boolean interrupted;
        public long peakHeapMB;
        public long millis;
        public final List<String> reasons = new ArrayList<String>();
        public final Map<String, IndexCounters> indices = new HashMap<String, IndexCounters>();

        public IndexCounters index(String indexName) {
            IndexCounters c = indices.get(indexName);
            if (c == null) {
                c = new IndexCounters();
                indices.put(indexName, c);
            }
            return c;
        }

        @Override public String toString() {
            return "Result[completed=" + completed + " capHit=" + capHit + " interrupted=" +
                       interrupted + " indices=" + indices + " reasons=" + reasons + " millis=" +
                       millis + " peakHeapMB=" + peakHeapMB + "]";
        }
    }

    // ---- running the audit ----

    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);
    // ids whose rebuild failed in the previous run; ordered last so they cannot starve the rest
    private static final Set<String> LAST_FAILED_REBUILDS = Collections.synchronizedSet(
        new HashSet<String>());
    private static final String ISOLATION = "repeatable-read";
    private static final String[] INDICES = { "encounter", "annotation", "individual" };

    /** Thrown inside the scroll consumer when the repair cap is reached: stop reading, not an abort. */
    private static final class CapReached extends IOException {
        CapReached() {
            super("repair cap reached");
        }
    }

    /**
     * One audit: one repeatable-read snapshot of the database, then for each index a doc-values
     * scroll, a comparison of every document against its expected tuple, conditional repairs, and
     * for a child document whose linkage disagrees with the database a deny write followed by a
     * verified rebuild. Nothing is acknowledged: completion only selects the next delay.
     */
    public static Result run(String context, Config config) {
        Result r = new Result();
        long startT = System.currentTimeMillis();

        if (!RUNNING.compareAndSet(false, true)) {
            r.reasons.add("already running in this JVM");
            r.millis = System.currentTimeMillis() - startT;
            return r;
        }
        try {
            if (!Collaboration.securityEnabled(context)) {
                // everything is publicly readable: there is nothing to verify (unchanged policy)
                r.completed = true;
                return r;
            }
            if (interrupted(r, "before the snapshot")) return r;
            Snapshot snapshot = loadSnapshot(context, r);
            if (snapshot == null) return r;
            sampleHeap(r, "snapshot loaded");
            Runner runner = new Runner(context, config, snapshot, r);
            boolean allAudited = true;
            for (String index : INDICES) {
                if (interrupted(r, "before auditing " + index)) break;
                allAudited &= runner.auditIndex(index);
                sampleHeap(r, index + " audited");
                snapshot.releaseAfter(index); // the next phases do not need this one's structures
            }
            snapshot.releaseAfter("individual");
            r.completed = allAudited && !r.capHit && !r.interrupted && countersClean(r);
            return r;
        } catch (Exception ex) {
            r.reasons.add("audit failed: " + ex);
            ex.printStackTrace();
            return r;
        } finally {
            RUNNING.set(false);
            r.millis = System.currentTimeMillis() - startT;
            System.out.println("PermissionsAudit: " + r);
        }
    }

    private static boolean countersClean(Result r) {
        for (IndexCounters c : r.indices.values()) {
            if ((c.failed > 0) || (c.conflicts > 0) || (c.structural > 0) || (c.deferred > 0))
                return false;
        }
        return true;
    }

    /** True (and recorded) when the audit thread has been interrupted: the run stops here. */
    private static boolean interrupted(Result r, String where) {
        if (!Thread.currentThread().isInterrupted()) return false;
        r.interrupted = true;
        r.reasons.add("interrupted " + where);
        return true;
    }

    private static void sampleHeap(Result r, String when) {
        Runtime rt = Runtime.getRuntime();
        long usedMB = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        if (usedMB > r.peakHeapMB) r.peakHeapMB = usedMB;
        System.out.println("PermissionsAudit: " + when + "; heap used=" + usedMB + "MB");
    }

    // ---- snapshot ----

    /**
     * Shepherd creates its PersistenceManager when a transaction begins, so: begin, roll that
     * empty transaction back, set the isolation level, begin again, then require that it took.
     * Any failure propagates: the audit does not run on a snapshot it cannot vouch for.
     */
    static void beginRepeatableRead(Shepherd sh)
    throws IOException {
        sh.beginDBTransaction();
        PersistenceManager pm = sh.getPM();
        if (pm == null) throw new IOException("no PersistenceManager");
        Transaction tx = pm.currentTransaction();
        if (tx.isActive()) tx.rollback();
        tx.setIsolationLevel(ISOLATION);
        tx.begin();
        if (!tx.isActive() || !ISOLATION.equals(tx.getIsolationLevel())) {
            throw new IOException("snapshot isolation not established: active=" + tx.isActive() +
                      " level=" + tx.getIsolationLevel());
        }
    }

    /** Rows of a SQL query as Object[]: DataNucleus returns a single selected column as the bare
     *  value, not as a one-element array. */
    private static List<Object[]> sqlRows(Shepherd sh, String sql) {
        Query q = sh.getPM().newQuery("javax.jdo.query.SQL", sql);
        try {
            List<Object[]> rows = new ArrayList<Object[]>();
            for (Object o : (List<?>)q.execute()) {
                rows.add((o instanceof Object[]) ? (Object[])o : new Object[] { o });
            }
            return rows;
        } finally {
            q.closeAll();
        }
    }

    private static void addTo(Map<String, Set<String> > map, String key, String value) {
        if ((key == null) || (value == null)) return;
        Set<String> s = map.get(key);
        if (s == null) {
            s = new HashSet<String>();
            map.put(key, s);
        }
        s.add(value);
    }

    /** Everything the audit reads from the database, in one repeatable-read transaction. Null (with
     *  a reason) when any read fails: the audit then does nothing this run. */
    static Snapshot loadSnapshot(String context, Result r) {
        Shepherd sh = new Shepherd(context);
        sh.setAction("PermissionsAudit.snapshot");
        try {
            beginRepeatableRead(sh);
            // 1. users: stored username -> id (Shepherd.getUser trims the lookup key, see userIdForUsername)
            Map<String, String> usernameToId = new HashMap<String, String>();
            List<User> users = sh.getUsersWithUsername();
            if (users == null) throw new IOException("could not read users");
            for (User u : users) {
                if ((u != null) && (u.getUsername() != null) && (u.getId() != null))
                    usernameToId.put(u.getUsername(), u.getId());
            }
            // 2. approved/edit collaborations: raw username -> counterpart ids (computeViewUsers
            //    selects the rows naming the stored submitter string on either side, raw)
            Map<String, Set<String> > collabGrants = new HashMap<String, Set<String> >();
            List<Collaboration> collabs = (List<Collaboration>)sh.getAllCollaborations();
            if (collabs == null) throw new IOException("could not read collaborations");
            for (Collaboration col : collabs) {
                if ((col == null) || (!col.isApproved() && !col.isEditApproved())) continue;
                String u1 = col.getUsername1();
                String u2 = col.getUsername2();
                if ((u1 == null) || (u2 == null) || u1.equals(u2)) continue;
                addTo(collabGrants, u1, userIdForUsername(usernameToId, u2));
                addTo(collabGrants, u2, userIdForUsername(usernameToId, u1));
            }
            // 3. orgAdmins of each member's organizations: member id -> admin ids (one-way)
            Set<String> orgAdminNames = new HashSet<String>();
            List<String> adminList = sh.getUsernamesWithAnyRole(
                Collections.singletonList(Organization.ROLE_MANAGER), context);
            if (adminList != null) orgAdminNames.addAll(adminList);
            Map<String, Set<String> > orgGrants = new HashMap<String, Set<String> >();
            List<Organization> orgs = sh.getAllOrganizationsStrict(); // a failed read propagates
            if (orgs == null) throw new IOException("could not read organizations");
            for (Organization org : orgs) {
                List<User> members = (org == null) ? null : org.getMembers();
                if (members == null) continue;
                List<String> adminIds = new ArrayList<String>();
                for (User m : members) {
                    if ((m == null) || (m.getUsername() == null) || (m.getId() == null)) continue;
                    if (orgAdminNames.contains(m.getUsername())) adminIds.add(m.getId());
                }
                if (adminIds.isEmpty()) continue;
                for (User m : members) {
                    if ((m == null) || (m.getId() == null)) continue;
                    for (String adminId : adminIds) {
                        if (!adminId.equals(m.getId())) addTo(orgGrants, m.getId(), adminId);
                    }
                }
            }
            // 4. location roles: role name -> holder ids (system role names never count)
            Map<String, Set<String> > roleNameToUserIds = new HashMap<String, Set<String> >();
            List<Role> roles = sh.getRolesInContext(context);
            if (roles == null) throw new IOException("could not read roles");
            for (Role role : roles) {
                if ((role == null) || (role.getRolename() == null) || (role.getUsername() == null))
                    continue;
                if (Role.SYSTEM_ROLE_NAMES.contains(role.getRolename())) continue;
                addTo(roleNameToUserIds, role.getRolename(),
                    userIdForUsername(usernameToId, role.getUsername()));
            }
            // 5. inventories and relationships
            Map<String, String[]> encounters = new HashMap<String, String[]>();
            for (Object[] row : sqlRows(sh,
                "SELECT \"CATALOGNUMBER\", \"SUBMITTERID\", \"LOCATIONID\" FROM \"ENCOUNTER\"")) {
                if ((row == null) || (row[0] == null)) continue;
                encounters.put((String)row[0], new String[] { (String)row[1],
                    (row.length > 2) ? (String)row[2] : null });
            }
            // eligible annotations: the reconciler's own desired-state SQL, so eligibility can
            // never diverge from Annotation.shouldIndexInOpenSearch()
            Set<String> eligible = new HashSet<String>();
            for (Object[] row : sqlRows(sh, new Annotation().getAllVersionsSql())) {
                if ((row != null) && (row[0] != null)) eligible.add((String)row[0]);
            }
            // relationship rows name encounters by id; share the encounter map's own key instance
            // so the link and membership sets do not hold a second copy of every id
            Map<String, String> canonical = new HashMap<String, String>();
            for (String encId : encounters.keySet()) canonical.put(encId, encId);
            Map<String, Set<String> > links = new HashMap<String, Set<String> >();
            for (Object[] row : sqlRows(sh,
                "SELECT \"ID_EID\", \"CATALOGNUMBER_OID\" FROM \"ENCOUNTER_ANNOTATIONS\"")) {
                if (row == null) continue;
                String encId = (String)row[1];
                String shared = canonical.get(encId);
                addTo(links, (String)row[0], (shared != null) ? shared : encId);
            }
            Set<String> individuals = new HashSet<String>();
            for (Object[] row : sqlRows(sh, "SELECT \"INDIVIDUALID\" FROM \"MARKEDINDIVIDUAL\"")) {
                if ((row != null) && (row[0] != null)) individuals.add((String)row[0]);
            }
            Map<String, Set<String> > members = new HashMap<String, Set<String> >();
            for (Object[] row : sqlRows(sh,
                "SELECT \"INDIVIDUALID_OID\", \"CATALOGNUMBER_EID\" FROM \"MARKEDINDIVIDUAL_ENCOUNTERS\"")) {
                if (row == null) continue;
                String encId = (String)row[1];
                String shared = canonical.get(encId);
                addTo(members, (String)row[0], (shared != null) ? shared : encId);
            }
            Snapshot snapshot = new Snapshot(encounters, usernameToId, collabGrants, orgGrants,
                roleNameToUserIds, eligible, links, individuals, members);
            System.out.println("PermissionsAudit: snapshot of " + encounters.size() +
                " encounters, " + eligible.size() + " eligible annotations, " + individuals.size() +
                " individuals; " + usernameToId.size() + " users, " + collabGrants.size() +
                " usernames with collaboration grants, " + orgGrants.size() +
                " users with orgAdmin grants, " + roleNameToUserIds.size() + " location role names");
            return snapshot;
        } catch (Exception ex) {
            r.reasons.add("snapshot failed: " + ex);
            ex.printStackTrace();
            return null;
        } finally {
            sh.rollbackAndClose();
        }
    }

    // ---- per-index audit ----

    /** Field mappings the audit needs, with doc values; anything else disables the index's audit. */
    static String checkMapping(Map<String, JSONObject> mappings, List<String> fields) {
        if (mappings == null) return "no mapping";
        for (String field : fields) {
            JSONObject fm = mappings.get(field);
            if (fm == null) return "field " + field + " is not mapped";
            String type = fm.optString("type", "");
            if (!"keyword".equals(type) && !"boolean".equals(type))
                return "field " + field + " has type " + type + " (doc values need keyword/boolean)";
            if (fm.has("doc_values") && !fm.optBoolean("doc_values", true))
                return "field " + field + " has doc_values=false";
            if (fm.has("null_value")) // a JSON null written to clear the field would index this value
                return "field " + field + " has a null_value";
        }
        return null;
    }

    /** The partial document that installs a tuple on an index document. */
    static JSONObject tupleDocument(String index, AclTuple tuple) {
        JSONObject doc = new JSONObject();
        doc.put("publiclyReadable", tuple.publiclyReadable);
        if ("encounter".equals(index)) {
            // the serializer omits the owner for an unresolvable one; a partial update that merely
            // omitted it would leave a stale id in place, so clear it explicitly
            doc.put("submitterUserId", tuple.owners.isEmpty() ? JSONObject.NULL :
                tuple.owners.iterator().next());
        } else {
            doc.put("submitterUserIds", new JSONArray(tuple.owners));
        }
        doc.put("viewUsers", new JSONArray(tuple.viewers));
        return doc;
    }

    private static final class Runner {
        final String context;
        final Config config;
        final Snapshot snapshot;
        final Result result;
        final OpenSearch os;
        int writes = 0; // toward the repair cap
        int rebuilds = 0; // toward the rebuild cap, shared by both child indices

        Runner(String context, Config config, Snapshot snapshot, Result result) {
            this.context = context;
            this.config = config;
            this.snapshot = snapshot;
            this.result = result;
            this.os = new OpenSearch();
        }

        /** True when the index was fully read (repairs may still have failed, which the counters show). */
        boolean auditIndex(final String index) {
            final IndexCounters c = result.index(index);
            final String ownerField = "encounter".equals(index) ? "submitterUserId" : "submitterUserIds";
            final List<String> fields = new ArrayList<String>(Arrays.asList("publiclyReadable",
                ownerField, "viewUsers"));
            if ("annotation".equals(index)) fields.add("encounterId");
            if ("individual".equals(index)) fields.add("encounterIds");
            try {
                String bad = checkMapping(os.fieldMappings(index), fields);
                if (bad != null) {
                    result.reasons.add(index + ": not audited: " + bad);
                    return false;
                }
            } catch (Exception ex) {
                result.reasons.add(index + ": mapping check failed: " + ex);
                return false;
            }
            final List<String> rebuilds = new ArrayList<String>();
            final Map<String, long[]> rebuildVersions = new HashMap<String, long[]>();
            boolean read = true;
            try {
                os.scrollDocValues(index, fields, config.pageSize, config.keepAlive,
                    new OpenSearch.DocValuesPageConsumer() {
                    public void accept(JSONArray hits) throws IOException {
                        for (int i = 0; i < hits.length(); i++) {
                            if (Thread.currentThread().isInterrupted())
                                throw new InterruptedIOException("interrupted while auditing " + index);
                            IndexedDoc doc = parseHit(hits.optJSONObject(i), ownerField);
                            if (doc.error != null) throw new IOException(doc.error);
                            processHit(index, doc, c, rebuilds, rebuildVersions);
                        }
                    }
                });
            } catch (CapReached cap) {
                result.capHit = true; // stop reading; the next audit continues
            } catch (InterruptedIOException iex) {
                result.interrupted = true;
                result.reasons.add(index + ": " + iex.getMessage());
                read = false;
            } catch (Exception ex) {
                result.reasons.add(index + ": " + ex);
                read = false;
            }
            if (read && !result.interrupted) rebuild(index, rebuilds, rebuildVersions, c);
            System.out.println("PermissionsAudit: " + index + ": " + c);
            return read;
        }

        private void processHit(String index, IndexedDoc doc, IndexCounters c, List<String> rebuilds,
            Map<String, long[]> rebuildVersions)
        throws IOException {
            c.scanned++;
            AclTuple expected;
            boolean structural = false;
            if ("encounter".equals(index)) {
                expected = snapshot.expectedEncounter(doc.id);
            } else if ("annotation".equals(index)) {
                expected = snapshot.expectedAnnotation(doc.id);
                Set<String> parents = snapshot.parentsOf(doc.id);
                if ((expected != null) && (parents.size() == 1)) {
                    String parent = parents.iterator().next();
                    structural = (doc.encounterId.size() != 1) || !parent.equals(doc.encounterId.get(0));
                }
            } else {
                expected = snapshot.expectedIndividual(doc.id);
                if (expected != null) structural = !doc.encounterIds.equals(snapshot.membersOf(doc.id));
            }
            if (expected == null) {
                c.unknown++; // not in the database (or not eligible): the content reconciler's job
                return;
            }
            if (structural) {
                // the document's linkage disagrees with the database: its metadata may belong to
                // another parent, so close it first, then rebuild it from the serializer
                c.structural++;
                long seqNo = doc.seqNo;
                long primaryTerm = doc.primaryTerm;
                if (!matches(doc, AclTuple.DENY)) {
                    OpenSearch.ConditionalWrite w = write(index, doc.id, tupleDocument(index,
                        AclTuple.DENY), seqNo, primaryTerm, c, false);
                    if (w == null) return; // conflict, failure: no rebuild this run
                    seqNo = w.seqNo;
                    primaryTerm = w.primaryTerm;
                }
                rebuilds.add(doc.id);
                rebuildVersions.put(doc.id, new long[] { seqNo, primaryTerm });
                return;
            }
            if (!matches(doc, expected)) {
                write(index, doc.id, tupleDocument(index, expected), doc.seqNo, doc.primaryTerm, c,
                    true);
            }
        }

        /** A conditional partial update under the repair cap. Null when nothing was applied. */
        private OpenSearch.ConditionalWrite write(String index, String id, JSONObject doc, long seqNo,
            long primaryTerm, IndexCounters c, boolean countAsRepair)
        throws CapReached {
            if (writes >= config.repairCap) throw new CapReached();
            writes++;
            try {
                OpenSearch.ConditionalWrite w = os.updateIfUnchanged(index, id, doc, seqNo, primaryTerm);
                if (!w.applied) {
                    c.conflicts++;
                    return null;
                }
                if (countAsRepair) c.repaired++;
                return w;
            } catch (Exception ex) {
                c.failed++;
                System.out.println("PermissionsAudit: " + index + "/" + id + " write failed: " + ex);
                return null;
            }
        }

        /** Rebuild the collected child documents from their serializers, after the scroll is over. */
        private void rebuild(String index, List<String> ids, Map<String, long[]> versions,
            IndexCounters c) {
            if (ids.isEmpty()) return;
            if (OpenSearch.skipAutoIndexing()) { // checked immediately before dispatch
                c.deferred += ids.size();
                return;
            }
            List<String> ordered = new ArrayList<String>();
            List<String> failedBefore = new ArrayList<String>();
            for (String id : ids) {
                if (LAST_FAILED_REBUILDS.contains(id)) failedBefore.add(id);
                else ordered.add(id);
            }
            ordered.addAll(failedBefore);
            Shepherd sh = new Shepherd(context);
            sh.setAction("PermissionsAudit.rebuild");
            try {
                sh.beginDBTransaction();
                Iterator<String> it = ordered.iterator();
                while (it.hasNext()) {
                    String id = it.next();
                    if (Thread.currentThread().isInterrupted()) {
                        result.interrupted = true;
                        result.reasons.add(index + ": interrupted before a rebuild");
                        c.deferred += 1 + remaining(it);
                        return;
                    }
                    if (rebuilds >= config.rebuildCap) {
                        result.capHit = true;
                        c.deferred += 1 + remaining(it);
                        return;
                    }
                    rebuilds++;
                    long[] v = versions.get(id);
                    try {
                        rebuildOne(index, id, v[0], v[1], sh, c);
                    } catch (InterruptedIOException iex) {
                        result.interrupted = true;
                        result.reasons.add(index + ": " + iex.getMessage());
                        c.deferred += 1 + remaining(it);
                        return;
                    } catch (Exception ex) {
                        c.failed++;
                        LAST_FAILED_REBUILDS.add(id);
                        System.out.println("PermissionsAudit: " + index + "/" + id + " rebuild failed: " + ex);
                    }
                }
            } catch (Exception ex) {
                result.reasons.add(index + ": rebuilds aborted: " + ex);
                c.failed++;
            } finally {
                sh.rollbackAndClose();
            }
        }

        private static int remaining(Iterator<String> it) {
            int n = 0;
            while (it.hasNext()) {
                it.next();
                n++;
            }
            return n;
        }

        private void rebuildOne(String index, String id, long seqNo, long primaryTerm, Shepherd sh,
            IndexCounters c)
        throws IOException {
            Base obj = "annotation".equals(index) ? sh.getAnnotation(id) : sh.getMarkedIndividual(id);
            if (obj == null) {
                System.out.println("PermissionsAudit: " + index + "/" + id +
                    " no longer in the database; left denied for the reconciler");
                return;
            }
            if (!obj.shouldIndexInOpenSearch()) return; // the reconciler removes it
            // the THROWING serializer overload on our own Shepherd: a failure propagates instead
            // of leaving a partial document (Base.opensearchDocumentSerializer(JsonGenerator))
            StringWriter sw = new StringWriter();
            JsonGenerator jgen = new JsonFactory().createGenerator(sw);
            jgen.writeStartObject();
            obj.opensearchDocumentSerializer(jgen, sh);
            jgen.writeEndObject();
            jgen.close();
            String json = sw.toString();
            String problem = verifyRebuilt(index, id, new JSONObject(json));
            if (problem != null) {
                c.failed++;
                LAST_FAILED_REBUILDS.add(id);
                System.out.println("PermissionsAudit: " + index + "/" + id +
                    " rebuilt document disagrees with the snapshot (" + problem + "); left denied");
                return;
            }
            if (Thread.currentThread().isInterrupted())
                throw new InterruptedIOException("interrupted after serializing " + id);
            OpenSearch.ConditionalWrite w = os.putIfUnchanged(index, id, json, seqNo, primaryTerm);
            if (!w.applied) {
                c.conflicts++;
                return;
            }
            LAST_FAILED_REBUILDS.remove(id);
        }

        /** The rebuilt document must agree with the snapshot on linkage and ACL, with every field
         *  of the expected shape, or it is not sent. */
        private String verifyRebuilt(String index, String id, JSONObject built) {
            AclTuple expected;
            try {
                if ("annotation".equals(index)) {
                    expected = snapshot.expectedAnnotation(id);
                    Set<String> parents = snapshot.parentsOf(id);
                    String parent = (parents.size() == 1) ? parents.iterator().next() : null;
                    Object rawLinked = built.opt("encounterId");
                    if ((rawLinked != null) && !(rawLinked instanceof String))
                        return "encounterId is not a string: " + rawLinked;
                    String linked = (String)rawLinked;
                    if ((parent == null) ? (linked != null) : !parent.equals(linked))
                        return "encounterId " + linked + " vs parent " + parent;
                } else {
                    expected = snapshot.expectedIndividual(id);
                    Set<String> linked = new HashSet<String>(strings(built, "encounterIds"));
                    if (!linked.equals(snapshot.membersOf(id)))
                        return "encounterIds " + linked + " vs members " + snapshot.membersOf(id);
                }
                if (expected == null) return "no expectation";
                Object flag = built.opt("publiclyReadable");
                if (!(flag instanceof Boolean)) return "publiclyReadable missing or not a boolean";
                AclTuple builtTuple = new AclTuple((Boolean)flag,
                    new HashSet<String>(strings(built, "submitterUserIds")),
                    new HashSet<String>(strings(built, "viewUsers")));
                if (!builtTuple.equals(expected)) return "acl " + builtTuple + " vs " + expected;
                return null;
            } catch (IllegalArgumentException malformed) {
                return "malformed: " + malformed.getMessage();
            }
        }
    }
}
