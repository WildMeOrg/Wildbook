package org.ecocean.api.bulk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
     * A compound cell: a numbered field that is not per-photo (project#, submitter#, informOther#,
     * photographer#, MarkedIndividual.name#, MicrosatelliteMarkersAnalysis.alleles#), a measurement
     * or sampling protocol, or a sample/genetics field.
     */
    static boolean isCompound(String field) {
        if (SAMPLE_FIELDS.contains(field) || BulkValidator.isMeasurementFieldName(field))
            return true;
        if (GroupPhotos.photoPrefix(field) != null) return false;
        try {
            return BulkValidator.indexIntValue(field) >= 0;
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
     * order. A group with a compound cell that already failed (its own validation, a header check
     * or a duplicate offset) is not compared further: its failures are already Issues. Otherwise
     * two or more distinct sets are a GROUP_CONFLICT on every compound cell of every supplying row;
     * one set is merged once (from the first supplier) and checked for completeness.
     */
    static void merge(GroupCollapse.State st, List<Integer> rows,
        Map<Integer, List<GroupCollapse.Entry> > byRow, Map<String, Object> merged,
        Map<String, List<GroupedRows.Cell> > contrib, Map<String, Object> raw, Set<String> poisoned) {
        for (int r : rows) checkOffsets(st, byRow.get(r));
        List<Integer> suppliers = new ArrayList<Integer>();
        List<Map<String, String> > sets = new ArrayList<Map<String, String> >();
        for (int r : rows) {
            Map<String, String> set = new HashMap<String, String>();
            for (GroupCollapse.Entry e : byRow.get(r)) {
                set.put(e.cell.field, e.bv.getValueString());
                GroupCollapse.addContributor(contrib, raw, mergedName(e.cell.field), e);
            }
            if (set.isEmpty()) continue;
            suppliers.add(r);
            sets.add(set);
        }
        if (suppliers.isEmpty()) return;
        if (hasCompoundIssue(st, rows)) {
            for (int r : suppliers) poisonAll(byRow.get(r), poisoned);
            return;
        }
        if (new LinkedHashSet<Map<String, String> >(sets).size() > 1) {
            String msg = conflictMessage(suppliers, sets);
            for (int r : suppliers) {
                for (GroupCollapse.Entry e : byRow.get(r))
                    st.issue(r, e.cell.field, new BulkValidatorException(msg,
                        ApiException.ERROR_RETURN_CODE_INVALID,
                        BulkValidatorException.TYPE_GROUP_CONFLICT, e.cell.field));
                poisonAll(byRow.get(r), poisoned);
            }
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

    // an Issue already on a compound cell of this group
    private static boolean hasCompoundIssue(GroupCollapse.State st, List<Integer> rows) {
        Set<Integer> members = new LinkedHashSet<Integer>(rows);
        for (GroupedRows.Issue is : st.issues) {
            if (members.contains(is.row) && isCompound(is.field)) return true;
        }
        return false;
    }

    // which rows supply the set, and which columns differ between them
    private static String conflictMessage(List<Integer> suppliers, List<Map<String, String> > sets) {
        Set<String> columns = new TreeSet<String>();
        for (Map<String, String> s : sets) columns.addAll(s.keySet());
        List<String> differing = new ArrayList<String>();
        for (String col : columns) {
            Set<String> values = new LinkedHashSet<String>();
            boolean missing = false;
            for (Map<String, String> s : sets) {
                if (s.containsKey(col)) {
                    values.add(s.get(col));
                } else {
                    missing = true;
                }
            }
            if (missing || (values.size() > 1)) differing.add(col);
        }
        // bounded whatever the group's size: every compound cell of every supplier carries it
        List<String> rowList = new ArrayList<String>();
        for (int r : suppliers) rowList.add(Integer.toString(r));
        return "rows " + GroupCollapse.firstFew(rowList, GroupCollapse.LIST_LIMIT) +
                   " of this group give different project, submitter, name, " +
                   "measurement or sample data (differing: " +
                   GroupCollapse.firstFew(differing, DIFFERING_LIMIT) +
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

    // Encounter.submitter#: the importer creates a submitter only from an email address
    private static void checkSubmitters(Check c) {
        List<String> emails = BulkImportUtil.findIndexedFieldNames(c.keys,
            "Encounter.submitter.emailAddress");
        List<String> names = BulkImportUtil.findIndexedFieldNames(c.keys,
            "Encounter.submitter.fullName");
        List<String> affiliations = BulkImportUtil.findIndexedFieldNames(c.keys,
            "Encounter.submitter.affiliation");
        for (int i = 0; i < Math.max(names.size(), affiliations.size()); i++) {
            if (at(emails, i) != null) continue;
            String present = (at(names, i) != null) ? at(names, i) : at(affiliations, i);
            if (present != null) c.require(partner(present, "emailAddress"), present);
        }
    }

    // Encounter.project#: prefix and name in pairs, an owner for a new project, one definition per prefix
    private static void checkProjects(GroupCollapse.State st, Check c) {
        List<String> prefixes = BulkImportUtil.findIndexedFieldNames(c.keys,
            "Encounter.project.projectIdPrefix");
        List<String> names = BulkImportUtil.findIndexedFieldNames(c.keys,
            "Encounter.project.researchProjectName");
        List<String> owners = BulkImportUtil.findIndexedFieldNames(c.keys,
            "Encounter.project.ownerUsername");
        int n = Math.max(prefixes.size(), Math.max(names.size(), owners.size()));
        Map<String, Integer> firstByPrefix = new HashMap<String, Integer>();
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
            Integer earlier = firstByPrefix.putIfAbsent(prefix, i);
            if (earlier != null) {
                // the importer caches the first definition and silently reuses it
                if (!c.value(nm).equals(c.value(names.get(earlier))))
                    c.invalid(nm, "project " + prefix + " is already named " +
                        c.value(names.get(earlier)) + " in this group");
                String earlierOwner = at(owners, earlier);
                if ((o != null) && (earlierOwner != null) &&
                    !c.value(o).equals(c.value(earlierOwner)))
                    c.invalid(o, "project " + prefix + " already has the owner " +
                        c.value(earlierOwner) + " in this group");
                continue;
            }
            if ((o == null) && !st.projectExists.computeIfAbsent(prefix,
                k -> BulkImportUtil.findExistingProject(k, st.sh) != null))
                c.fail(partner(p, "ownerUsername"), "project " + prefix +
                    " does not exist yet; an owner username is required to create it",
                    BulkValidatorException.TYPE_REQUIRED_VALUE);
        }
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
        List<String> canonical = new ArrayList<String>(List.of("", "", ""));
        if ((tokens[0].length != tokens[1].length) || (tokens[1].length != tokens[2].length)) {
            problem = "they list different numbers of loci";
        } else {
            for (int locus = 0; (locus < tokens[0].length) && (problem == null); locus++) {
                String sep = (locus == 0) ? "" : ",";
                String name = tokens[0][locus].trim();
                if (name.isEmpty()) problem = "locus " + (locus + 1) + " has no name";
                canonical.set(0, canonical.get(0) + sep + name);
                for (int a = 1; (a < 3) && (problem == null); a++) {
                    Integer v = parseAllele(tokens[a][locus]);
                    if (v == null) {
                        problem = "allele \"" + tokens[a][locus] + "\" is not an integer";
                    } else {
                        canonical.set(a, canonical.get(a) + sep + Integer.toString(v));
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
                merged.put(literal[i], new BulkValidator(literal[i], canonical.get(i), st.sh));
            } catch (BulkValidatorException ex) {
                throw new IllegalStateException("canonical genotype rejected: " + canonical, ex);
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
