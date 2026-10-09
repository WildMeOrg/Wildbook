package org.ecocean.security;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.ecocean.User;
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
        final Map<String, String[]> encounters; // id -> { submitter, locationID }
        final Map<String, String> usernameToId; // stored username -> user id
        final Map<String, Set<String> > collabGrants; // raw submitter string -> counterpart ids
        final Map<String, Set<String> > orgGrants; // owner id -> orgAdmin ids
        final Map<String, Set<String> > roleNameToUserIds;
        final Map<String, Set<String> > lineageCache = new HashMap<String, Set<String> >();
        final Set<String> eligibleAnnotations; // annotations that should have an index document
        final Map<String, Set<String> > annotationLinks; // annotation id -> linked encounter ids
        final Set<String> individuals;
        final Map<String, Set<String> > individualMembers; // individual id -> member encounter ids

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
        JSONObject fields = hit.optJSONObject("fields");
        if (fields == null) fields = new JSONObject();
        JSONArray flag = fields.optJSONArray("publiclyReadable");
        if (flag != null) {
            if ((flag.length() != 1) || !(flag.opt(0) instanceof Boolean)) {
                doc.error = "hit " + doc.id + " with a malformed publiclyReadable: " + flag;
                return doc;
            }
            doc.publiclyReadable = flag.getBoolean(0);
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

    private static List<String> strings(JSONObject fields, String name) {
        JSONArray arr = fields.optJSONArray(name);
        List<String> out = new ArrayList<String>();
        if (arr == null) return out;
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
        if (!page.has("_scroll_id") || page.isNull("_scroll_id")) return "no _scroll_id";
        return null;
    }
}
