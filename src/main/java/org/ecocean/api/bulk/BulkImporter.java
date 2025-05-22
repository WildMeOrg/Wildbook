// basic bulk import
package org.ecocean.api.bulk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;

import org.ecocean.api.bulk.*;
import org.ecocean.Annotation;
import org.ecocean.Base;
import org.ecocean.Encounter;
import org.ecocean.media.MediaAsset;
import org.ecocean.media.MediaAssetFactory;
import org.ecocean.MarkedIndividual;
import org.ecocean.Occurrence;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.shepherd.core.ShepherdPMF;
import org.ecocean.User;
import org.ecocean.Util;

public class BulkImporter {
    public List<Map<String, Object> > dataRows = null;
    public Map<String, MediaAsset> mediaAssetMap = null;
    public User user = null;
    public Shepherd myShepherd = null;

    // caching loaded and (more imporantly?) newly created objects, so they can be
    // used across all rows. StandardImport seemed to do some caching *based on user*
    // (of, for example, MarkedIndividuals). not sure why. maybe this will be revealed later.
    private Map<String, Encounter> encounterCache = new HashMap<String, Encounter>();
    private Map<String, Occurrence> occurrenceCache = new HashMap<String, Occurrence>();
    private Map<String, MarkedIndividual> individualCache = new HashMap<String, MarkedIndividual>();

    public BulkImporter(List<Map<String, Object> > rows, Map<String, MediaAsset> maMap, User user,
        Shepherd myShepherd) {
        this.dataRows = rows;
        this.mediaAssetMap = maMap;
        this.user = user;
        this.myShepherd = myShepherd;
    }

    public JSONObject createImport() {
        JSONObject rtn = new JSONObject();
        List<Base> needIndexing = new ArrayList<Base>();

        for (int rowNum = 0; rowNum < dataRows.size(); rowNum++) {
            List<BulkValidator> fields = new ArrayList<BulkValidator>();
            Map<String, Object> rowResult = dataRows.get(rowNum);
            for (String rowFieldName : rowResult.keySet()) {
                Object fieldObj = rowResult.get(rowFieldName);
                if (fieldObj instanceof BulkValidator) {
                    fields.add((BulkValidator)fieldObj);
                }
                // } else if (fieldObj instanceof BulkValidatorException) {
            }
            System.out.println("createImport() row " + rowNum);
            processRow(fields);
        }
        System.out.println(
            "------------ all rows processed; beginning persistence -------------\n");
        JSONArray arr = new JSONArray();
        for (MediaAsset ma : mediaAssetMap.values()) {
            ma.setSkipAutoIndexing(true);
            MediaAssetFactory.save(ma, myShepherd);
            System.out.println("MMMM " + ma);
            arr.put(ma.getIdInt());
            needIndexing.add(ma);
        }
        rtn.put("mediaAssets", arr);
        arr = new JSONArray();
        for (Encounter enc : encounterCache.values()) {
            // it is a certain kind of painful that if you do not pass id here it assigns a new random one
            enc.setSkipAutoIndexing(true);
            myShepherd.storeNewEncounter(enc, enc.getId());
            System.out.println("EEEE " + enc);
            arr.put(enc.getId());
            needIndexing.add(enc);
        }
        rtn.put("encounters", arr);
        arr = new JSONArray();
        for (Occurrence occ : occurrenceCache.values()) {
            occ.setSkipAutoIndexing(true);
            myShepherd.storeNewOccurrence(occ);
            System.out.println("OOOO " + occ);
            arr.put(occ.getId());
            needIndexing.add(occ);
        }
        rtn.put("sightings", arr);
        arr = new JSONArray();
        for (MarkedIndividual indiv : individualCache.values()) {
            indiv.setSkipAutoIndexing(true);
            myShepherd.storeNewMarkedIndividual(indiv);
            System.out.println("IIII " + indiv);
            arr.put(indiv.getId());
            needIndexing.add(indiv);
        }
        rtn.put("individuals", arr);
        // clears shepherd/pmf cache, which we seem to do when we create encounters (?)
        myShepherd.cacheEvictAll();
        BulkImportUtil.bulkOpensearchIndex(needIndexing);
        return rtn;
    }

    // this assumes all values have been validated, so just go for it! set data with values. good luck!
    private void processRow(List<BulkValidator> fields) {
        // some fields we do on a subsequent pass, as they require special care
        // handy for these subsequent passes
        Map<String, BulkValidator> fmap = new HashMap<String, BulkValidator>();

        for (BulkValidator field : fields) {
            System.out.println("   >> " + field);
            fmap.put(field.getFieldName(), field);
        }
        Set<String> allFieldNames = fmap.keySet();
        String indivId = null;
        if (fmap.containsKey("Encounter.individualID"))
            indivId = fmap.get("Encounter.individualID").getValueString();
        if ((indivId == null) && fmap.containsKey("MarkedIndividual.individualID"))
            indivId = fmap.get("MarkedIndividual.individualID").getValueString();
        MarkedIndividual indiv = getOrCreateMarkedIndividual(indivId, fmap);
        Occurrence occ = getOrCreateOccurrence(fmap);
        Encounter enc = getOrCreateEncounter(fmap, indiv, occ);
        if (enc != null) return; // FIXME temp disable

/*
        these are in order based on indexing numerical value such that list.get(i)
        will return the i-th field note that this means if the user data skipped
        values, there will be nulls, e.g. providing mediaAsset0 and mediaAsset2 only
        but should provide a way to match up, for example, mediaAssetX with keywordX
        based on this index value.
 */
        List<String> maFields = BulkImportUtil.findIndexedFieldNames(allFieldNames,
            "Encounter.mediaAsset");
        List<String> kwFields = BulkImportUtil.findIndexedFieldNames(allFieldNames,
            "Encounter.keyword");
        List<String> multiKwFields = BulkImportUtil.findIndexedFieldNames(allFieldNames,
            "Encounter.mediaAsset.keywords");
        List<String> nameLabelFields = BulkImportUtil.findIndexedFieldNames(allFieldNames,
            "MarkedIndividual.name.label");
        List<String> nameValueFields = BulkImportUtil.findIndexedFieldNames(allFieldNames,
            "MarkedIndividual.name.value");
        System.out.println(">>>>>>>>>>>> maFields: " + maFields);
        System.out.println(">>>>>>>>>>>> kwFields: " + kwFields);
        System.out.println(">>>>>>>>>>>> multiKwFields: " + multiKwFields);
        System.out.println(">>>>>>>>>>>> nameLabelFields: " + nameLabelFields);
        System.out.println(">>>>>>>>>>>> nameValueFields: " + nameValueFields);
        // Keyword kw = myShepherd.getOrCreateKeyword(kwString);
        if (indiv != null) {
            for (int i = 0; i < Math.min(nameLabelFields.size(), nameValueFields.size()); i++) {
                String label = nameLabelFields.get(i);
                String value = nameValueFields.get(i);
                if ((label == null) || (value == null)) continue;
                if (indiv.getName(label) != null)
                    indiv.getNames().removeValuesByKey(label, indiv.getName(label));
                indiv.addName(label, value);
            }
            indiv.refreshNamesCache();
        }
/*
   core functionality: creating data.....

   StandardImport seems to treat a lot of Sighting.fubar exactly as if it was Encounter.fubar, namely
   setting the value on the Encouner only. so we follow this as represented in that class, fbow.
 */
        for (BulkValidator bv : fields) {
            System.out.println("bv>>>> " + bv);
            String fieldName = bv.getFieldName();
            switch (fieldName) {
            case "Encounter.latitude":
            case "Encounter.decimalLatitude":
            case "Sighting.decimalLatitude":
                enc.setDecimalLatitude(bv.getValueDouble());
                break;
            case "Encounter.longitude":
            case "Encounter.decimalLongitude":
            case "Sighting.decimalLongitude":
                enc.setDecimalLongitude(bv.getValueDouble());
                break;

            case "Encounter.alternateID":
                enc.setAlternateID(bv.getValueString());
                break;

            case "Encounter.behavior":
                enc.setBehavior(bv.getValueString());
                break;

            case "Encounter.country":
                enc.setCountry(bv.getValueString());
                break;

            // this will supercede year/month/date but that
            // should be handled via validation step FIXME
            case "Sighting.dateInMilliseconds":
            case "Encounter.dateInMilliseconds":
                Long val = bv.getValueLong();
                if (val != null) enc.setDateInMilliseconds(val);
                break;
            case "Sighting.year":
            case "Encounter.year":
                enc.setYear(bv.getValueInteger());
                break;
            case "Sighting.month":
            case "Encounter.month":
                enc.setMonth(bv.getValueInteger());
                break;
            case "Sighting.day":
            case "Encounter.day":
                enc.setDay(bv.getValueInteger());
                break;
            case "Sighting.hour":
            case "Encounter.hour":
                enc.setHour(bv.getValueInteger());
                break;
            case "Sighting.minutes":
            case "Encounter.minutes":
                enc.setMinutes(bv.getValueString()); // why?? :(
                break;

            case "Encounter.depth":
                enc.setDepth(bv.getValueDouble());
                break;
            case "Encounter.elevation":
                enc.setMaximumElevationInMeters(bv.getValueDouble());
                break;

            case "Encounter.genus":
                enc.setGenus(bv.getValueString());
                break;
            case "Encounter.specificEpithet":
                enc.setSpecificEpithet(bv.getValueString());
                break;

            case "Encounter.lifeStage":
                enc.setLifeStage(bv.getValueString());
                break;

            case "Encounter.livingStatus":
                enc.setLivingStatus(bv.getValueString());
                break;

            case "Encounter.locationID":
                enc.setLocationID(bv.getValueString());
                break;

            case "Encounter.sex":
                enc.setSex(bv.getValueString());
                break;

            case "Encounter.state":
                enc.setState(bv.getValueString());
                break;

            case "Encounter.submitterID":
                enc.setSubmitterID(bv.getValueString());
                break;

            case "Encounter.submitterName":
                enc.setSubmitterName(bv.getValueString());
                break;

            case "Encounter.submitterOrganization":
                enc.setSubmitterOrganization(bv.getValueString());
                break;

            case "MarkedIndividual.nickName":
            case "MarkedIndividual.nickname":
                if (indiv != null) indiv.setNickName(bv.getValueString());
                break;

            case "Encounter.distinguishingScar":
            case "Encounter.groupRole":
            case "Encounter.identificationRemarks":
            case "Encounter.informOther":
            case "Encounter.measurement":
            case "Encounter.sightingID":
            case "Encounter.sightingRemarks":
            case "Encounter.otherCatalogNumbers":
            case "Encounter.patterningCode":
            case "Encounter.photographer":
            case "Encounter.project":
            case "Encounter.quality":
            case "Encounter.researcherComments":
            case "Encounter.verbatimLocality":

            case "Membership.role":
            case "MicrosatelliteMarkersAnalysis.alleleNames":
            case "MicrosatelliteMarkersAnalysis.analysisID":
            case "MitochondrialDNAAnalysis.haplotype":
            case "Sighting.bearing":
            case "Sighting.bestGroupSizeEstimate":
            case "Sighting.comments":
            case "Sighting.distance":
            case "Sighting.effortCode":
            case "Sighting.fieldStudySite":
            case "Sighting.fieldSurveyCode":
            case "Sighting.groupBehavior":
            case "Sighting.groupComposition":
            case "Sighting.humanActivityNearby":
            case "Sighting.individualCount":
            case "Sighting.initialCue":
            case "Sighting.maxGroupSizeEstimate":
            case "Sighting.millis":
            case "Sighting.minGroupSizeEstimate":
            case "Sighting.numAdults":
            case "Sighting.numCalves":
            case "Sighting.numJuveniles":
            case "Sighting.observer":
            case "Sighting.occurrenceID":
            case "Sighting.seaState":
            case "Sighting.seaSurfaceTemp":
            case "Sighting.seaSurfaceTemperature":
            case "Sighting.swellHeight":
            case "Sighting.transectBearing":
            case "Sighting.transectName":
            case "Sighting.visibilityIndex":
            case "SatelliteTag.serialNumber":
            case "SexAnalysis.processingLabTaskID":
            case "SexAnalysis.sex":
            case "SocialUnit.socialUnitName":
            case "Survey.comments":
            case "Survey.id":
            case "Survey.type":
            case "Survey.vessel":
            case "SurveyTrack.vesselID":
            case "Taxonomy.commonName":
            case "Taxonomy.scientificName":
            case "TissueSample.sampleID":
            case "TissueSample.tissueType":
                System.out.println("NOT YET IMPLEMENTED: " + fieldName);
                break;

            default:
                System.out.println("DEBUG: field ignored by main loop: " + fieldName);
            }
        }
        // fields done
        System.out.println("+ populated data on " + enc);
        // now attach annotations
        String tx = enc.getTaxonomyString();
        List<Annotation> annots = new ArrayList<Annotation>();
        for (String maKey : maFields) {
            if (maKey == null) continue; // data skipped an index
            BulkValidator bv = fmap.get(maKey);
            if (bv == null) throw new RuntimeException("could not find fmap for key=" + maKey);
            if (bv.valueIsNull()) continue;
            MediaAsset ma = this.mediaAssetMap.get(bv.getValueString());
            if (ma == null)
                throw new RuntimeException("could not find MediaAsset for maKey=" + maKey +
                        ", bv=" + bv.getValueString() + " in " + this.mediaAssetMap);
            Annotation ann = new Annotation(tx, ma);
            ann.setIsExemplar(true);
            ann.setSkipAutoIndexing(true);
            annots.add(ann);
        }
        if (annots.size() > 0) enc.addAnnotations(annots);
        System.out.println("+ populated " + annots.size() + " MediaAssets on " + enc);
    }

/*
    this will create an individual if none can be found
    StandardImport does all sorts of weird caching and the like here, so likely this will need to be refined and repaired
    we could apply the naming fields here too, but meh lets let that be done in the main loop -- seems easier for the indexed ones
 */
    private MarkedIndividual getOrCreateMarkedIndividual(String id,
        Map<String, BulkValidator> fmap) {
        if (id == null) return null;
        if (individualCache.containsKey(id)) return individualCache.get(id);
        MarkedIndividual indiv = myShepherd.getMarkedIndividual(id);
        // these "should always exists" as they are required; how much fate am i tempting here by not checking?
        String genus = fmap.get("Encounter.genus").getValueString();
        String specificEpithet = fmap.get("Encounter.specificEpithet").getValueString();
        if (indiv == null)
            indiv = MarkedIndividual.withName(myShepherd, id, genus, specificEpithet);
        if (indiv == null) {
            System.out.println(
                "BulkImporter.getMarkedIndividual() creating new; could not find existing indiv based on id="
                + id);
            indiv = new MarkedIndividual();
            indiv.setId(id);
            indiv.setGenus(genus);
            indiv.setSpecificEpithet(specificEpithet);
            // FIXME what else???
        }
        individualCache.put(id, indiv);
        return indiv;
    }

    private Encounter getOrCreateEncounter(Map<String, BulkValidator> fmap, MarkedIndividual indiv,
        Occurrence occ) {
        String encId = null;
        Encounter enc = null;

        if (fmap.containsKey("Encounter.id")) encId = fmap.get("Encounter.id").getValueString();
        if ((encId == null) && fmap.containsKey("Encounter.catalogNumber"))
            encId = fmap.get("Encounter.catalogNumber").getValueString();
        if (encId != null) {
            if (encounterCache.containsKey(encId)) {
                return encounterCache.get(encId);
            } else {
                enc = myShepherd.getEncounter(encId);
            }
        } else if ((indiv != null) && (occ != null)) {
            // apparently this is a thing?
            enc = myShepherd.getEncounterByIndividualAndOccurrence(indiv.getId(), occ.getId());
            // this doesnt read from cache - not sure how much of a problem that will be, but likely some
        }
        if (enc == null) {
            enc = new Encounter();
            if (encId == null) encId = Util.generateUUID();
            enc.setId(encId);
            enc.setDWCDateAdded();
            enc.setDWCDateLastModified();
        }
        if (encId != null) encounterCache.put(encId, enc);
        return enc;
    }

    private Occurrence getOrCreateOccurrence(Map<String, BulkValidator> fmap) {
        // FIXME
        return null;
    }
}
