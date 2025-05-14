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
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.Util;

public class BulkImporter {
    public static JSONObject createImport(List<Map<String, Object> > rows,
        Map<String, MediaAsset> maMap, Shepherd myShepherd) {
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
            processRow(fields, maMap, myShepherd);
        }
        return rtn;
    }

    // this assumes all values have been validated, so just go for it! set data with values. good luck!
    private static void processRow(List<BulkValidator> fields, Map<String, MediaAsset> maMap,
        Shepherd myShepherd) {
        Encounter enc = new Encounter();

        if (enc != null) return; // FIXME temp disable
        enc.setId(Util.generateUUID());
        enc.setDWCDateAdded();
        enc.setDWCDateLastModified();

        // some fields we do on a subsequent pass, as they require special care
        // handy for these subsequent passes
        Map<String, BulkValidator> fmap = new HashMap<String, BulkValidator>();
        for (BulkValidator field : fields) {
            System.out.println("   >> " + field);
            fmap.put(field.getFieldName(), field);
        }
        Set<String> allFieldNames = fmap.keySet();
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
        System.out.println(">>>>>>>>>>>> maFields: " + maFields);
        System.out.println(">>>>>>>>>>>> kwFields: " + kwFields);
        System.out.println(">>>>>>>>>>>> multiKwFields: " + multiKwFields);
        // Keyword kw = myShepherd.getOrCreateKeyword(kwString);
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

            case "Encounter.distinguishingScar":
            case "Encounter.groupRole":
            case "Encounter.identificationRemarks":
            case "Encounter.individualID":
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
            case "Encounter.submitterID":
            case "Encounter.submitterName":
            case "Encounter.submitterOrganization":
            case "Encounter.verbatimLocality":
            case "MarkedIndividual.individualID":
            case "MarkedIndividual.name":
            case "MarkedIndividual.nickName":
            case "MarkedIndividual.nickname":
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
                System.out.println("UNSUPPORTED FIELDNAME: " + fieldName);
            }
        }
        // fields done
        System.out.println("+ populated data on " + enc);
    }
}
