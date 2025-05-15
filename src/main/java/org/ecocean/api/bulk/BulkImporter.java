// basic bulk import
package org.ecocean.api.bulk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONObject;

import org.ecocean.api.bulk.*;
import org.ecocean.Encounter;
import org.ecocean.media.MediaAsset;
import org.ecocean.MarkedIndividual;
import org.ecocean.Occurrence;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.User;
import org.ecocean.Util;

public class BulkImporter {
    public static JSONObject createImport(List<Map<String, Object> > rows,
        Map<String, MediaAsset> maMap, User user, Shepherd myShepherd) {
        JSONObject rtn = new JSONObject();

        for (int rowNum = 0; rowNum < rows.size(); rowNum++) {
            List<BulkValidator> fields = new ArrayList<BulkValidator>();
            Map<String, Object> rowResult = rows.get(rowNum);
            for (String rowFieldName : rowResult.keySet()) {
                Object fieldObj = rowResult.get(rowFieldName);
                if (fieldObj instanceof BulkValidator) {
                    fields.add((BulkValidator)fieldObj);
                }
                // } else if (fieldObj instanceof BulkValidatorException) {
            }
            System.out.println("createImport() row " + rowNum);
            processRow(fields, maMap, user, myShepherd);
        }
        return rtn;
    }

    // this assumes all values have been validated, so just go for it! set data with values. good luck!
    private static void processRow(List<BulkValidator> fields, Map<String, MediaAsset> maMap,
        User user, Shepherd myShepherd) {
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
        MarkedIndividual indiv = getOrCreateMarkedIndividual(indivId, fmap, user, myShepherd);
        Occurrence occ = getOrCreateOccurrence(fmap, myShepherd);
        Encounter enc = getOrCreateEncounter(fmap, indiv, occ, myShepherd);
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

   StandardImport seems to treat a lot of Occurrence.fubar exactly as if it was Encounter.fubar, namely
   setting the value on the Encouner only. so we follow this as represented in that class, fbow.
 */
        for (BulkValidator bv : fields) {
            System.out.println("bv>>>> " + bv);
            String fieldName = bv.getFieldName();
            switch (fieldName) {
            case "Encounter.latitude":
            case "Encounter.decimalLatitude":
            case "Occurrence.decimalLatitude":
                enc.setDecimalLatitude(bv.getValueDouble());
                break;
            case "Encounter.longitude":
            case "Encounter.decimalLongitude":
            case "Occurrence.decimalLongitude":
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
            case "Occurrence.dateInMilliseconds":
            case "Encounter.dateInMilliseconds":
                Long val = bv.getValueLong();
                if (val != null) enc.setDateInMilliseconds(val);
                break;
            case "Occurrence.year":
            case "Encounter.year":
                enc.setYear(bv.getValueInteger());
                break;
            case "Occurrence.month":
            case "Encounter.month":
                enc.setMonth(bv.getValueInteger());
                break;
            case "Occurrence.day":
            case "Encounter.day":
                enc.setDay(bv.getValueInteger());
                break;
            case "Occurrence.hour":
            case "Encounter.hour":
                enc.setHour(bv.getValueInteger());
                break;
            case "Occurrence.minutes":
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
            case "Encounter.occurrenceID":
            case "Encounter.occurrenceRemarks":
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
            case "Occurrence.bearing":
            case "Occurrence.bestGroupSizeEstimate":
            case "Occurrence.comments":
            case "Occurrence.distance":
            case "Occurrence.effortCode":
            case "Occurrence.fieldStudySite":
            case "Occurrence.fieldSurveyCode":
            case "Occurrence.groupBehavior":
            case "Occurrence.groupComposition":
            case "Occurrence.humanActivityNearby":
            case "Occurrence.individualCount":
            case "Occurrence.initialCue":
            case "Occurrence.maxGroupSizeEstimate":
            case "Occurrence.millis":
            case "Occurrence.minGroupSizeEstimate":
            case "Occurrence.numAdults":
            case "Occurrence.numCalves":
            case "Occurrence.numJuveniles":
            case "Occurrence.observer":
            case "Occurrence.occurrenceID":
            case "Occurrence.seaState":
            case "Occurrence.seaSurfaceTemp":
            case "Occurrence.seaSurfaceTemperature":
            case "Occurrence.swellHeight":
            case "Occurrence.transectBearing":
            case "Occurrence.transectName":
            case "Occurrence.visibilityIndex":
            case "SatelliteTag.serialNumber":
            case "SexAnalysis.processingLabTaskID":
            case "SexAnalysis.sex":
            case "SocialUnit.socialUnitName":
            case "Survey.comments":
            case "Survey.id":
            case "Survey.vessel":
            case "SurveyTrack.vesselID":
            case "Taxonomy.commonName":
            case "Taxonomy.scientificName":
            case "TissueSample.sampleID":
            case "TissueSample.tissueType":
                System.out.println("NOT YET IMPLEMENTED: " + fieldName);
                break;

            default:
                System.out.println("field ignored by main loop: " + fieldName);
            }
        }
        // fields done
        System.out.println("+ populated data on " + enc);
    }

/*
    this will create an individual if none can be found
    StandardImport does all sorts of weird caching and the like here, so likely this will need to be refined and repaired
    we could apply the naming fields here too, but meh lets let that be done in the main loop -- seems easier for the indexed ones
 */
    private static MarkedIndividual getOrCreateMarkedIndividual(String id,
        Map<String, BulkValidator> fmap, User user, Shepherd myShepherd) {
        if (id == null) return null;
        MarkedIndividual indiv = myShepherd.getMarkedIndividual(id);
        // these "should always exists" as they are required; how much fate am i tempting here by not checking?
        String genus = fmap.get("Encounter.genus").getValueString();
        String specificEpithet = fmap.get("Encounter.specificEpithet").getValueString();
        if (indiv == null)
            indiv = MarkedIndividual.withName(myShepherd, id, genus, specificEpithet);
        // FIXME create if does not exist
        if (indiv == null) {
            System.out.println(
                "BulkImporter.getMarkedIndividual() could not find existing indiv based on id=" +
                id);
        }
        return indiv;
    }

    private static Encounter getOrCreateEncounter(Map<String, BulkValidator> fmap,
        MarkedIndividual indiv, Occurrence occ, Shepherd myShepherd) {
        // apparently this is a thing?
        Encounter enc = null;

        if ((indiv != null) && (occ != null))
            enc = myShepherd.getEncounterByIndividualAndOccurrence(indiv.getId(), occ.getId());
        if (enc == null) {
            enc = new Encounter();
            enc.setId(Util.generateUUID());
            enc.setDWCDateAdded();
            enc.setDWCDateLastModified();
        }
        return enc;
    }

    private static Occurrence getOrCreateOccurrence(Map<String, BulkValidator> fmap,
        Shepherd myShepherd) {
        // FIXME
        return null;
    }
}
