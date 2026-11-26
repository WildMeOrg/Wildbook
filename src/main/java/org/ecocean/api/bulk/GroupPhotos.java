package org.ecocean.api.bulk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.ecocean.api.ApiException;

/**
 * Photo packing for GroupCollapse (#1789, C2/C8): each grouped row's photos with the metadata the
 * importer would read for them, then packed into consecutive slots of the merged row.
 */
final class GroupPhotos {
    static final String PHOTO = "Encounter.mediaAsset";
    // per-photo metadata the importer reads by the photo's ordinal in positional mode
    static final Set<String> ORDINAL_PREFIXES = Set.of("Encounter.keyword", "Encounter.quality",
        "Encounter.mediaAsset.keywords");

    private GroupPhotos() {}

    // one source row's photos, by source index, each with the metadata the importer would read
    static final class RowPhotos {
        final int row;
        final TreeMap<Integer, String> photoFields = new TreeMap<Integer, String>(); // non-absent
        final Map<Integer, GroupCollapse.Entry> validPhotos =
            new HashMap<Integer, GroupCollapse.Entry>();
        final Map<Integer, List<GroupCollapse.Entry> > metadata =
            new HashMap<Integer, List<GroupCollapse.Entry> >();
        final Map<Integer, List<String> > failedMetadata = new HashMap<Integer, List<String> >();

        RowPhotos(int row) {
            this.row = row;
        }
    }

    /**
     * The index prefix of a per-photo field (Encounter.mediaAsset, Encounter.mediaAsset.bbox,
     * Encounter.keyword, Encounter.quality, Encounter.mediaAsset.keywords, ...), with
     * "Encounter.mediaAsset.<label>" for a labeled keyword; null for any other field.
     */
    static String photoPrefix(String field) {
        BulkImportUtil.Slot slot = BulkImportUtil.slotOf(field);
        if (slot == null) return null;
        return isPhotoPrefix(slot.prefix) ? slot.prefix : null;
    }

    // the index prefix of a per-photo field family (the photo, its box and keyword columns)
    static boolean isPhotoPrefix(String prefix) {
        return prefix.equals(PHOTO) || prefix.startsWith(PHOTO + ".") ||
                   ORDINAL_PREFIXES.contains(prefix);
    }

    // the source index of a per-photo field (a labeled keyword's offset)
    private static int photoIndex(String field) {
        return BulkImportUtil.slotOf(field).index;
    }

    // a per-photo field's name in packed slot k
    static String packedName(String prefix, int k) {
        if (prefix.startsWith(PHOTO + ".")) return PHOTO + k + prefix.substring(PHOTO.length());
        return prefix + k;
    }

    /**
     * 3-4. One source row's photos and the metadata processRow() would read for each: by the same
     * index in exact-slot mode (and always for box columns and labeled keywords), by the photo's
     * occupied-photo ordinal otherwise. Metadata whose photo is absent is an orphan (INVALID).
     */
    static RowPhotos resolvePhotos(GroupCollapse.State st, int r, Set<String> present,
        List<GroupCollapse.Entry> photoEntries, Set<String> failedPhotoFields) {
        RowPhotos rp = new RowPhotos(r);
        for (String k : present) {
            if (PHOTO.equals(photoPrefix(k))) rp.photoFields.put(photoIndex(k), k);
        }
        List<Integer> byOrdinal = new ArrayList<Integer>(rp.photoFields.keySet());
        Map<String, GroupCollapse.Entry> valid = new LinkedHashMap<String, GroupCollapse.Entry>();
        for (GroupCollapse.Entry e : photoEntries) {
            if (PHOTO.equals(photoPrefix(e.cell.field))) {
                rp.validPhotos.put(photoIndex(e.cell.field), e);
            } else {
                valid.put(e.cell.field, e);
            }
        }
        Set<String> metadata = new TreeSet<String>(valid.keySet());
        for (String k : failedPhotoFields) {
            if (!PHOTO.equals(photoPrefix(k))) metadata.add(k);
        }
        for (String k : metadata) {
            String prefix = photoPrefix(k);
            int index = photoIndex(k);
            boolean ordinal = !st.exactSlotMode && ORDINAL_PREFIXES.contains(prefix);
            Integer photo = null;
            if (ordinal) {
                if (index < byOrdinal.size()) photo = byOrdinal.get(index);
            } else if (rp.photoFields.containsKey(index)) {
                photo = index;
            }
            GroupCollapse.Entry e = valid.get(k);
            if (photo != null) {
                if (e != null) {
                    rp.metadata.computeIfAbsent(photo, x -> new ArrayList<GroupCollapse.Entry>()).add(e);
                } else {
                    rp.failedMetadata.computeIfAbsent(photo, x -> new ArrayList<String>()).add(k);
                }
            } else if (e != null) { // a failed orphan is already an Issue
                int n = byOrdinal.size();
                String msg = ordinal ? (k + " has no photo to go with: this row has " + n +
                    " photo" + ((n == 1) ? "" : "s")) : (k + " has no photo in " + PHOTO + index);
                st.issue(r, k, new BulkValidatorException(msg,
                    ApiException.ERROR_RETURN_CODE_INVALID,
                    BulkValidatorException.TYPE_INVALID_VALUE, k));
            }
        }
        return rp;
    }

    /**
     * 5-7. Every grouped row needs a photo; a filename may appear once per group (a later
     * occurrence of a packed photo is an Issue and is not packed, with its metadata); photos
     * beyond the cap are a GROUP_CONFLICT and are not packed. The rest are packed in sheet order, then index order,
     * into slots 0..n-1, each with its own metadata; a failed metadata cell poisons its packed name.
     */
    static void packPhotos(GroupCollapse.State st, List<RowPhotos> photos, Map<String, Object> merged,
        Map<String, List<GroupedRows.Cell> > contrib, Map<String, Object> raw,
        Set<String> poisoned) {
        Map<String, GroupedRows.Cell> seen = new HashMap<String, GroupedRows.Cell>();
        int slot = 0;
        for (RowPhotos rp : photos) {
            if (rp.photoFields.isEmpty())
                st.issue(rp.row, PHOTO + "0", new BulkValidatorException(
                    "each row of a group needs at least one photo",
                    ApiException.ERROR_RETURN_CODE_REQUIRED,
                    BulkValidatorException.TYPE_REQUIRED_VALUE, PHOTO + "0"));
            for (int index : rp.photoFields.keySet()) {
                GroupCollapse.Entry photo = rp.validPhotos.get(index);
                if (photo == null) continue; // its own failure is already an Issue
                String filename = photo.bv.getValueString();
                GroupedRows.Cell first = seen.get(filename);
                if (first != null) {
                    st.issue(rp.row, photo.cell.field, new BulkValidatorException("photo " +
                        filename + " is already in this group at row " + first.row + ", " +
                        first.field + "; a photo may appear only once in a group",
                        ApiException.ERROR_RETURN_CODE_INVALID,
                        BulkValidatorException.TYPE_INVALID_DUPLICATE, photo.cell.field));
                    continue;
                }
                // the cap counts packed slots: repeats and failed photos take none
                if (slot >= st.maxPhotos) {
                    st.issue(rp.row, photo.cell.field, new BulkValidatorException(
                        "a group can have at most " + st.maxPhotos + " photos",
                        ApiException.ERROR_RETURN_CODE_INVALID,
                        BulkValidatorException.TYPE_GROUP_CONFLICT, photo.cell.field));
                    continue;
                }
                seen.put(filename, photo.cell); // only packed photos: a repeat names one
                pack(photo, PHOTO, slot, merged, contrib, raw);
                for (GroupCollapse.Entry e : rp.metadata.getOrDefault(index, List.of()))
                    pack(e, photoPrefix(e.cell.field), slot, merged, contrib, raw);
                for (String k : rp.failedMetadata.getOrDefault(index, List.of()))
                    poisoned.add(packedName(photoPrefix(k), slot));
                slot++;
            }
        }
    }

    private static void pack(GroupCollapse.Entry e, String prefix, int slot, Map<String, Object> merged,
        Map<String, List<GroupedRows.Cell> > contrib, Map<String, Object> raw) {
        String name = packedName(prefix, slot);
        GroupCollapse.addContributor(contrib, raw, name, e);
        merged.put(name, BulkValidator.rebound(e.bv, name));
    }
}
