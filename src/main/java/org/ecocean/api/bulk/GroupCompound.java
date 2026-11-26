package org.ecocean.api.bulk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.ecocean.api.ApiException;

/**
 * Compound encounter data for GroupCollapse (#1789, C4): projects, submitters, informOther,
 * photographers, individual names, measurements and sampling protocols, samples and genetics.
 * Every grouped row that supplies any of it supplies one identical set (exact original column
 * names, stored values); the merged row takes that set once, and the set must be complete and
 * unambiguous, as BulkImporter.processRow() would read it.
 */
final class GroupCompound {
    static final String INDIVIDUAL = "Encounter.individualID";
    static final String SAMPLE_ID = "TissueSample.sampleID";
    static final String ALLELE_NAMES = "MicrosatelliteMarkersAnalysis.alleleNames";
    static final String ALLELES = "MicrosatelliteMarkersAnalysis.alleles";
    static final String PROTOCOL = ".samplingProtocol";
    // the sample and genetics fields that are not numbered
    static final Set<String> SAMPLE_FIELDS = Set.of(SAMPLE_ID, "TissueSample.tissueType",
        "MicrosatelliteMarkersAnalysis.analysisID", ALLELE_NAMES,
        "SexAnalysis.processingLabTaskID", "SexAnalysis.sex", "MitochondrialDNAAnalysis.haplotype");
    // the sample ID family: handleSamples() takes the first of these it finds
    static final Set<String> SAMPLE_IDS = Set.of(SAMPLE_ID,
        "MicrosatelliteMarkersAnalysis.analysisID", "SexAnalysis.processingLabTaskID");

    // a conflict message names at most this many differing columns, then "and N more"
    static final int DIFFERING_LIMIT = 5;

    private GroupCompound() {}

    /**
     * A compound cell: a numbered field (with any index) that is not per-photo (project#,
     * submitter#, informOther#, photographer#, MarkedIndividual.name#,
     * MicrosatelliteMarkersAnalysis.alleles#), a measurement or sampling protocol, or a
     * sample/genetics field.
     */
    static boolean isCompound(String field) {
        if (SAMPLE_FIELDS.contains(field) || BulkValidator.isMeasurementFieldName(field))
            return true;
        // by its numbered family, whether or not its index is valid (project200 is a project)
        try {
            String prefix = BulkValidator.indexPrefixValue(field);
            return (prefix != null) && !GroupPhotos.isPhotoPrefix(prefix);
        } catch (BulkValidatorException ex) {
            return false;
        }
    }

    /**
     * True when the group gives no Encounter.individualID family value, so the importer has no
     * individual for individual-dependent values. False when that family failed: its failure is
     * already an Issue, and nothing is piled on the values that depend on it.
     */
    static boolean noIndividual(Map<String, Object> merged, Set<String> poisoned) {
        return !poisoned.contains(INDIVIDUAL) && !(merged.get(INDIVIDUAL) instanceof BulkValidator);
    }

    // the genotype allele columns handleSamples() reads by their literal names
    private static int alleleIndex(String field) {
        BulkImportUtil.Slot slot = BulkImportUtil.slotOf(field);
        return ((slot != null) && slot.prefix.equals(ALLELES)) ? slot.index : -1;
    }

    // the merged name of a compound cell: alleles00/alleles01 are bound to alleles0/alleles1
    static String mergedName(String field) {
        int i = alleleIndex(field);
        return ((i == 0) || (i == 1)) ? ALLELES + i : field;
    }

    // a failure on the agreed set: reported on every supplying row, under the same column
    private static final class Failure {
        final String message;
        final String type;

        Failure(String message, String type) {
            this.message = message;
            this.type = type;
        }
    }

    // the checks over one agreed set: first failure per column wins
    private static final class Check {
        final Map<String, GroupCollapse.Entry> set; // original column -> the first supplier's cell
        final Set<String> keys;
        final Map<String, Failure> failures = new LinkedHashMap<String, Failure>();

        Check(Map<String, GroupCollapse.Entry> set) {
            this.set = set;
            this.keys = new TreeSet<String>(set.keySet());
        }

        String value(String field) {
            return set.get(field).bv.getValueString();
        }

        void fail(String field, String message, String type) {
            failures.putIfAbsent(field, new Failure(message, type));
        }

        void require(String missing, String present) {
            fail(missing, missing + " is required with " + present,
                BulkValidatorException.TYPE_REQUIRED_VALUE);
        }

        void invalid(String field, String message) {
            fail(field, message, BulkValidatorException.TYPE_INVALID_VALUE);
        }
    }

    // the partner column of a numbered field, spelled with the same index ("project00.x" -> "project00.y")
    private static String partner(String field, String suffix) {
        return field.substring(0, field.lastIndexOf('.') + 1) + suffix;
    }

    private static String at(List<String> list, int i) {
        return (i < list.size()) ? list.get(i) : null;
    }

    /**
     * C4 for one group. byRow holds each grouped row's validated compound cells, in field-name
     * order. A row with a compound cell that already failed (its own validation, a header check
     * or a duplicate offset) is left out of the comparison: its failures are already Issues. Two
     * or more distinct sets among the other supplying rows are a GROUP_CONFLICT on every compound
     * cell of those rows. When no row failed and there is one set, it is merged once (from the
     * first supplier) and checked for completeness.
     */
    static void merge(GroupCollapse.State st, List<Integer> rows,
        Map<Integer, List<GroupCollapse.Entry> > byRow, Map<String, Object> merged,
        Map<String, List<GroupedRows.Cell> > contrib, Map<String, Object> raw, Set<String> poisoned) {
        for (int r : rows) checkOffsets(st, byRow.get(r));
        boolean anyFailed = false;
        List<Integer> suppliers = new ArrayList<Integer>();
        List<Integer> compared = new ArrayList<Integer>();
        List<Map<String, String> > sets = new ArrayList<Map<String, String> >();
        for (int r : rows) {
            boolean failed = st.compoundIssueRows.contains(r);
            anyFailed |= failed;
            Map<String, String> set = new HashMap<String, String>();
            for (GroupCollapse.Entry e : byRow.get(r)) {
                set.put(e.cell.field, e.bv.getValueString());
                GroupCollapse.addContributor(contrib, raw, mergedName(e.cell.field), e);
            }
            if (set.isEmpty()) continue;
            suppliers.add(r);
            if (failed) continue;
            compared.add(r);
            sets.add(set);
        }
        if (suppliers.isEmpty()) return;
        boolean conflict = new HashSet<Map<String, String> >(sets).size() > 1;
        if (conflict) {
            String msg = conflictMessage(compared, sets);
            for (int r : compared) {
                for (GroupCollapse.Entry e : byRow.get(r))
                    st.issue(r, e.cell.field, new BulkValidatorException(msg,
                        ApiException.ERROR_RETURN_CODE_INVALID,
                        BulkValidatorException.TYPE_GROUP_CONFLICT, e.cell.field));
            }
        }
        // a conflict, or a row whose compound data failed (so it cannot be compared): no merge
        if (anyFailed || conflict) {
            for (int r : suppliers) poisonAll(byRow.get(r), poisoned);
            return;
        }
        Map<String, GroupCollapse.Entry> set = new LinkedHashMap<String, GroupCollapse.Entry>();
        for (GroupCollapse.Entry e : byRow.get(suppliers.get(0))) {
            set.put(e.cell.field, e);
            String name = mergedName(e.cell.field);
            merged.put(name, BulkValidator.rebound(e.bv, name));
        }
        Check c = new Check(set);
        checkNames(c, noIndividual(merged, poisoned));
        checkSubmitters(c);
        checkProjects(st, c);
        checkMeasurements(c);
        checkSamples(c);
        checkGenotype(st, c, merged);
        for (int r : suppliers) {
            for (Map.Entry<String, Failure> f : c.failures.entrySet()) {
                boolean required = f.getValue().type.equals(BulkValidatorException.TYPE_REQUIRED_VALUE);
                st.issue(r, f.getKey(), new BulkValidatorException(f.getValue().message,
                    required ? ApiException.ERROR_RETURN_CODE_REQUIRED :
                    ApiException.ERROR_RETURN_CODE_INVALID, f.getValue().type, f.getKey()));
            }
        }
        for (String f : c.failures.keySet()) poisoned.add(mergedName(f));
    }

    private static void poisonAll(List<GroupCollapse.Entry> entries, Set<String> poisoned) {
        for (GroupCollapse.Entry e : entries) poisoned.add(mergedName(e.cell.field));
    }

    // which rows supply the set, and which columns differ between them (each name clipped: a
    // zero-padded index makes a legal column name arbitrarily long)
    private static String conflictMessage(List<Integer> suppliers, List<Map<String, String> > sets) {
        // one pass over the supplied cells: each column's first value, how many sets give it, and
        // whether any gives another value
        Map<String, String> first = new HashMap<String, String>();
        Map<String, Integer> count = new HashMap<String, Integer>();
        Set<String> differing = new TreeSet<String>();
        for (Map<String, String> s : sets) {
            for (Map.Entry<String, String> cell : s.entrySet()) {
                String earlier = first.putIfAbsent(cell.getKey(), cell.getValue());
                count.merge(cell.getKey(), 1, Integer::sum);
                if ((earlier != null) && !earlier.equals(cell.getValue())) differing.add(cell.getKey());
            }
        }
        for (Map.Entry<String, Integer> c : count.entrySet()) {
            if (c.getValue() < sets.size()) differing.add(c.getKey());
        }
        List<String> shown = new ArrayList<String>();
        for (String col : differing) shown.add(GroupCollapse.clip(col));
        // bounded whatever the group's size: every compound cell of every supplier carries it
        List<String> rowList = new ArrayList<String>();
        for (int r : suppliers) rowList.add(Integer.toString(r));
        return "rows " + GroupCollapse.firstFew(rowList, GroupCollapse.LIST_LIMIT) +
                   " of this group give different project, submitter, name, " +
                   "measurement or sample data (differing: " +
                   GroupCollapse.firstFew(shown, DIFFERING_LIMIT) +
                   "); every row that gives any of it must give the same columns with the same values.";
    }

    // within one source row only: two columns for one measurement (or protocol) offset
    private static void checkOffsets(GroupCollapse.State st, List<GroupCollapse.Entry> entries) {
        Map<Integer, String> measurements = new HashMap<Integer, String>();
        Map<Integer, String> protocols = new HashMap<Integer, String>();
        for (GroupCollapse.Entry e : entries) {
            String f = e.cell.field;
            int offset = BulkImportUtil.findMeasurementOffset(f);
            Map<Integer, String> seen = measurements;
            if (offset < 0) {
                offset = BulkImportUtil.findMeasurementSamplingProtocolOffset(f);
                seen = protocols;
            }
            if (offset < 0) continue;
            String earlier = seen.putIfAbsent(offset, f);
            if (earlier != null)
                st.issue(e.cell.row, f, new BulkValidatorException(earlier + " and " + f +
                    " are the same measurement", ApiException.ERROR_RETURN_CODE_INVALID,
                    BulkValidatorException.TYPE_INVALID_DUPLICATE, f));
        }
    }

    // MarkedIndividual.name#: label and value in pairs, one value per label, and an individual
    private static void checkNames(Check c, boolean noIndividual) {
        List<String> labels = BulkImportUtil.findIndexedFieldNames(c.keys,
            "MarkedIndividual.name.label");
        List<String> values = BulkImportUtil.findIndexedFieldNames(c.keys,
            "MarkedIndividual.name.value");
        Map<String, String> valueByLabel = new HashMap<String, String>();
        for (int i = 0; i < Math.max(labels.size(), values.size()); i++) {
            String l = at(labels, i);
            String v = at(values, i);
            if ((l == null) && (v == null)) continue;
            if (noIndividual) {
                for (String f : new String[] { l, v }) {
                    if (f != null)
                        c.fail(f, "individual names need an Encounter.individualID in the group",
                            BulkValidatorException.TYPE_REQUIRED_VALUE);
                }
            }
            if (v == null) {
                c.require(partner(l, "value"), l);
            } else if (l == null) {
                c.require(partner(v, "label"), v);
            } else {
                String earlier = valueByLabel.putIfAbsent(c.value(l), c.value(v));
                if ((earlier != null) && !earlier.equals(c.value(v)))
                    c.invalid(v, "name label " + c.value(l) + " already has the value " + earlier +
                        " in this group");
            }
        }
    }

    // Encounter.submitter#: the importer creates a submitter only from an email address, once
    // per address (the first slot's full name and affiliation; a later slot's are dropped)
    private static void checkSubmitters(Check c) {
        List<String> emails = BulkImportUtil.findIndexedFieldNames(c.keys,
            "Encounter.submitter.emailAddress");
        List<String> names = BulkImportUtil.findIndexedFieldNames(c.keys,
            "Encounter.submitter.fullName");
        List<String> affiliations = BulkImportUtil.findIndexedFieldNames(c.keys,
            "Encounter.submitter.affiliation");
        Map<String, String> nameByEmail = new HashMap<String, String>();
        Map<String, String> affiliationByEmail = new HashMap<String, String>();
        for (int i = 0; i < Math.max(emails.size(), Math.max(names.size(), affiliations.size()));
            i++) {
            String email = at(emails, i);
            if (email == null) {
                String present = (at(names, i) != null) ? at(names, i) : at(affiliations, i);
                if (present != null) c.require(partner(present, "emailAddress"), present);
                continue;
            }
            String address = c.value(email);
            sameAsFirst(c, nameByEmail, address, at(names, i), "submitter " +
                GroupCollapse.clip(address) + " already has the full name ");
            sameAsFirst(c, affiliationByEmail, address, at(affiliations, i), "submitter " +
                GroupCollapse.clip(address) + " already has the affiliation ");
        }
    }

    /**
     * One stored object's property, given in several slots of the set: the first slot that gives
     * it (key -> its value in first) defines it; a later slot giving another value is INVALID on
     * that slot's column. field may be null (the slot does not give the property).
     */
    private static void sameAsFirst(Check c, Map<String, String> first, String key, String field,
        String message) {
        if (field == null) return;
        String earlier = first.putIfAbsent(key, c.value(field));
        if ((earlier != null) && !earlier.equals(c.value(field)))
            c.invalid(field, message + GroupCollapse.clip(earlier) + " in this group");
    }

    // Encounter.project#: prefix and name in pairs, an owner for a new project, one definition per
    // prefix (the importer caches the first definition and silently reuses it)
    private static void checkProjects(GroupCollapse.State st, Check c) {
        List<String> prefixes = BulkImportUtil.findIndexedFieldNames(c.keys,
            "Encounter.project.projectIdPrefix");
        List<String> names = BulkImportUtil.findIndexedFieldNames(c.keys,
            "Encounter.project.researchProjectName");
        List<String> owners = BulkImportUtil.findIndexedFieldNames(c.keys,
            "Encounter.project.ownerUsername");
        int n = Math.max(prefixes.size(), Math.max(names.size(), owners.size()));
        Map<String, String> nameByPrefix = new HashMap<String, String>();
        Map<String, String> ownerByPrefix = new HashMap<String, String>();
        for (int i = 0; i < n; i++) {
            String p = at(prefixes, i);
            String nm = at(names, i);
            String o = at(owners, i);
            if ((p == null) && (nm == null)) {
                if (o != null) {
                    c.require(partner(o, "projectIdPrefix"), o);
                    c.require(partner(o, "researchProjectName"), o);
                }
                continue;
            }
            if (nm == null) {
                c.require(partner(p, "researchProjectName"), p);
                continue;
            }
            if (p == null) {
                c.require(partner(nm, "projectIdPrefix"), nm);
                continue;
            }
            String prefix = c.value(p);
            boolean repeat = nameByPrefix.containsKey(prefix);
            String shown = "project " + GroupCollapse.clip(prefix);
            sameAsFirst(c, nameByPrefix, prefix, nm, shown + " is already named ");
            sameAsFirst(c, ownerByPrefix, prefix, o, shown + " already has the owner ");
            if (repeat || (o != null)) continue;
            Boolean exists = projectExists(st, prefix);
            if (exists == null) {
                c.invalid(p, "Could not check whether this project exists");
            } else if (!exists) {
                c.fail(partner(p, "ownerUsername"), shown +
                    " does not exist yet; an owner username is required to create it",
                    BulkValidatorException.TYPE_REQUIRED_VALUE);
            }
        }
    }

    // whether an existing project answers the prefix, looked up once per upload outside the
    // request's transaction; null when the lookup failed (the caller reports that on the prefix)
    private static Boolean projectExists(GroupCollapse.State st, String prefix) {
        if (st.projectExists.containsKey(prefix)) return st.projectExists.get(prefix);
        Boolean exists;
        try {
            exists = BulkImportUtil.projectExists(prefix, st.sh);
        } catch (RuntimeException ex) {
            exists = null;
        }
        st.projectExists.put(prefix, exists);
        return exists;
    }

    // a sampling protocol is imported only with its measurement
    private static void checkMeasurements(Check c) {
        List<String> measurements = BulkImportUtil.findMeasurementFieldNames(c.keys);
        List<String> protocols = BulkImportUtil.findMeasurementSamplingProtocolFieldNames(c.keys);
        for (int i = 0; i < protocols.size(); i++) {
            String p = protocols.get(i);
            if ((p == null) || (at(measurements, i) != null)) continue;
            c.require(p.substring(0, p.length() - PROTOCOL.length()), p);
        }
    }

    // genetics need a sample ID; the importer reads only alleles0 and alleles1
    private static void checkSamples(Check c) {
        boolean hasId = false;
        String genetic = null;
        for (String k : c.keys) {
            if (SAMPLE_IDS.contains(k)) {
                hasId = true;
            } else if ((genetic == null) && (SAMPLE_FIELDS.contains(k) || (alleleIndex(k) >= 0))) {
                genetic = k;
            }
            if (alleleIndex(k) >= 2)
                c.invalid(k, "only " + ALLELES + "0 and " + ALLELES + "1 are imported");
        }
        if ((genetic != null) && !hasId) c.require(SAMPLE_ID, genetic);
    }

    /**
     * The genotype: alleleNames, alleles0 and alleles1 together, each split on every comma
     * (trailing empty tokens kept) and trimmed, of one length, with no empty name and an integer
     * for every allele. Bound to the literal names handleSamples() reads, in canonical form.
     */
    private static void checkGenotype(GroupCollapse.State st, Check c, Map<String, Object> merged) {
        String names = c.keys.contains(ALLELE_NAMES) ? ALLELE_NAMES : null;
        String[] alleles = new String[2];
        for (String k : c.keys) {
            int i = alleleIndex(k);
            if ((i == 0) || (i == 1)) alleles[i] = k;
        }
        String[] trio = { names, alleles[0], alleles[1] };
        String[] literal = { ALLELE_NAMES, ALLELES + "0", ALLELES + "1" };
        String present = null;
        for (String f : trio) {
            if ((present == null) && (f != null)) present = f;
        }
        if (present == null) return;
        boolean complete = true;
        for (int i = 0; i < 3; i++) {
            if (trio[i] == null) {
                c.require(literal[i], present);
                complete = false;
            }
        }
        if (!complete) return;
        String[][] tokens = new String[3][];
        for (int i = 0; i < 3; i++) tokens[i] = c.value(trio[i]).split(",", -1);
        String problem = null;
        if ((tokens[0].length != tokens[1].length) || (tokens[1].length != tokens[2].length)) {
            problem = "they list different numbers of loci";
        } else {
            // each token is normalised in place (trimmed name, integer alleles), joined once below
            for (int locus = 0; (locus < tokens[0].length) && (problem == null); locus++) {
                tokens[0][locus] = tokens[0][locus].trim();
                if (tokens[0][locus].isEmpty()) problem = "locus " + (locus + 1) + " has no name";
                for (int a = 1; (a < 3) && (problem == null); a++) {
                    Integer v = parseAllele(tokens[a][locus]);
                    if (v == null) {
                        problem = "allele \"" + tokens[a][locus] + "\" is not an integer";
                    } else {
                        tokens[a][locus] = Integer.toString(v);
                    }
                }
            }
        }
        if (problem != null) {
            for (String f : trio)
                c.invalid(f, ALLELE_NAMES + ", alleles0 and alleles1 must list the same loci, " +
                    "each with a name and two integer alleles: " + problem);
            return;
        }
        for (int i = 0; i < 3; i++) {
            try {
                merged.put(literal[i], new BulkValidator(literal[i], String.join(",", tokens[i]), st.sh));
            } catch (BulkValidatorException ex) {
                throw new IllegalStateException("canonical genotype rejected: " + literal[i], ex);
            }
        }
    }

    // an allele as handleSamples() reads it, after trimming; null when it is not an int
    static Integer parseAllele(String token) {
        try {
            return Integer.parseInt(token.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
