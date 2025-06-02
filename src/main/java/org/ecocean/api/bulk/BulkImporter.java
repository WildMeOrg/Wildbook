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

import javax.servlet.ServletException;

import org.ecocean.api.bulk.*;
import org.ecocean.Annotation;
import org.ecocean.Base;
import org.ecocean.Encounter;
import org.ecocean.media.MediaAsset;
import org.ecocean.media.MediaAssetFactory;
import org.ecocean.MarkedIndividual;
import org.ecocean.Measurement;
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
    private Map<String, User> userCache = new HashMap<String, User>();

    public BulkImporter(List<Map<String, Object> > rows, Map<String, MediaAsset> maMap, User user,
        Shepherd myShepherd) {
        this.dataRows = rows;
        this.mediaAssetMap = maMap;
        this.user = user;
        this.myShepherd = myShepherd;
    }

    public JSONObject createImport()
    throws ServletException {
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
            System.out.println("createImport() row=" + rowNum);
            try {
                processRow(fields);
            } catch (Exception ex) {
                // TODO we could allow this some leeway with a tolerance setting
                System.out.println("createImport() row=" + rowNum + " failed with " + ex);
                ex.printStackTrace();
                throw new ServletException("unexpected exception on processRow for row=" + rowNum +
                        ": " + ex);
            }
        }
        System.out.println(
            "------------ all rows processed; beginning persistence -------------\n");
        List<Integer> maIds = new ArrayList<Integer>(); // used later to build child MAs
        JSONArray arr = new JSONArray();
        for (MediaAsset ma : mediaAssetMap.values()) {
            ma.setSkipAutoIndexing(true);
            MediaAssetFactory.save(ma, myShepherd);
            System.out.println("MMMM " + ma);
            arr.put(ma.getIdInt());
            maIds.add(ma.getIdInt());
            needIndexing.add(ma);
        }
        rtn.put("mediaAssets", arr);
        for (User u : userCache.values()) {
            myShepherd.getPM().makePersistent(u);
        }
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
        System.out.println(
            "------------ persistence complete; background indexing and MA children -------------\n");
        // clears shepherd/pmf cache, which we seem to do when we create encounters (?)
        myShepherd.cacheEvictAll();
        BulkImportUtil.bulkOpensearchIndex(needIndexing);
        MediaAsset.updateStandardChildrenBackground(myShepherd.getContext(), maIds);
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
        if (indiv != null) {
            indiv.addEncounter(enc);
            enc.setIndividual(indiv);
            indiv.setTaxonomyFromEncounters(true);
        }
        if (occ != null) {
            occ.addEncounter(enc);
            occ.setLatLonFromEncs(false);
        }
        // this line can be uncommented to disable persisting for development purposes
        // TODO remove this when no longer useful
        // if (enc != null) return;

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
        // Keyword kw = myShepherd.getOrCreateKeyword(kwString); // no we need to make our own as this commits :(
        if (indiv != null) {
            for (int i = 0; i < Math.min(nameLabelFields.size(), nameValueFields.size()); i++) {
                String labelFN = nameLabelFields.get(i);
                String valueFN = nameValueFields.get(i);
                if ((labelFN == null) || (valueFN == null)) continue;
                String label = fmap.get(labelFN).getValueString();
                String value = fmap.get(valueFN).getValueString();
                if ((label == null) || (value == null)) continue;
                if (indiv.getName(label) != null)
                    indiv.getNames().removeValuesByKey(label, indiv.getName(label));
                indiv.addName(label, value);
            }
            indiv.refreshNamesCache();
        }
        // submitterID sets "owner", but it has already been validated, so we now it
        // is either a (valid) username [which might be this.user] or "public" which
        // is, sadly, what public encounters seem to be assigned
        // not it is also required, so we should have *something*
        enc.setSubmitterID(fmap.get("Encounter.submitterID").getValueString());
        if (occ != null) occ.setSubmitterIDFromEncs(false);
        // but we also have enc.submitters, whatever this is about (!?)
        // StandardImport actually *creates Users* based on these, so here we go....
        // NOTE: these are FIELDNAMES not actual values
        List<String> submitterEmails = BulkImportUtil.findIndexedFieldNames(allFieldNames,
            "Encounter.submitter.emailAddress");
        List<String> submitterNames = BulkImportUtil.findIndexedFieldNames(allFieldNames,
            "Encounter.submitter.fullName");
        List<String> submitterAffiliations = BulkImportUtil.findIndexedFieldNames(allFieldNames,
            "Encounter.submitter.affiliation");
        for (int i = 0; i < submitterEmails.size(); i++) {
            if (submitterEmails.get(i) == null) continue; // handles case where an offset was skipped
            String sname = null;
            if ((i < submitterNames.size()) && (submitterNames.get(i) != null))
                sname = fmap.get(submitterNames.get(i)).getValueString();
            String saffil = null;
            if ((i < submitterAffiliations.size()) && (submitterAffiliations.get(i) != null))
                saffil = fmap.get(submitterAffiliations.get(i)).getValueString();
            User sub = getOrCreateUser(fmap.get(submitterEmails.get(i)).getValueString(), sname,
                saffil, myShepherd);
            enc.addSubmitter(sub); // weeds out null and duplicates, yay!
        }
        // like above, but informOther
        List<String> informOtherEmails = BulkImportUtil.findIndexedFieldNames(allFieldNames,
            "Encounter.informOther.emailAddress");
        List<String> informOtherNames = BulkImportUtil.findIndexedFieldNames(allFieldNames,
            "Encounter.informOther.fullName");
        List<String> informOtherAffiliations = BulkImportUtil.findIndexedFieldNames(allFieldNames,
            "Encounter.informOther.affiliation");
        for (int i = 0; i < informOtherEmails.size(); i++) {
            if (informOtherEmails.get(i) == null) continue;
            String ioname = null;
            if ((i < informOtherNames.size()) && (informOtherNames.get(i) != null))
                ioname = fmap.get(informOtherNames.get(i)).getValueString();
            String ioaffil = null;
            if ((i < informOtherAffiliations.size()) && (informOtherAffiliations.get(i) != null))
                ioaffil = fmap.get(informOtherAffiliations.get(i)).getValueString();
            User inf = getOrCreateUser(fmap.get(informOtherEmails.get(i)).getValueString(), ioname,
                ioaffil, myShepherd);
            enc.addInformOther(inf); // weeds out null and duplicates, yay!
        }
        // measurements kinda suck eggs. good luck with this.
        List<String> measFN = BulkImportUtil.findMeasurementFieldNames(allFieldNames);
        List<String> mspFN = BulkImportUtil.findMeasurementSamplingProtocolFieldNames(
            allFieldNames);
        List<String> munits = BulkImportUtil.getMeasurementUnits();
        List<String> mvals = BulkImportUtil.getMeasurementValues();
        for (int i = 0; i < measFN.size(); i++) {
            if (measFN.get(i) == null) continue;
            if (i >= mvals.size()) continue;
            Double mdbl = fmap.get(measFN.get(i)).getValueDouble();
            System.out.println("MEAS???? mval=" + mvals.get(i) + " > " + mdbl);
            if (mdbl == null) continue;
            String sampProt = null;
            if ((i < mspFN.size()) && (mspFN.get(i) != null))
                sampProt = fmap.get(mspFN.get(i)).getValueString();
            String munit = null;
            if (i < munits.size()) munit = munits.get(i);
            Measurement meas = new Measurement(enc.getId(), mvals.get(i), mdbl, munit, sampProt);
            System.out.println("MEASUREMENT??? " + meas);
            // enc.setMeasurement() // this commits, so lets figure out the right way (but still updates-in-place)
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
                if (!bv.valueIsNull()) enc.setMonth(bv.getValueInteger());
                break;
            case "Sighting.day":
            case "Encounter.day":
                if (!bv.valueIsNull()) enc.setDay(bv.getValueInteger());
                break;
            case "Sighting.hour":
            case "Encounter.hour":
                if (!bv.valueIsNull()) enc.setHour(bv.getValueInteger());
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
                enc.setDistinguishingScar(bv.getValueString());
                break;

            case "Encounter.groupRole":
                enc.setGroupRole(bv.getValueString());
                break;

            case "Encounter.identificationRemarks":
                enc.setIdentificationRemarks(bv.getValueString());
                break;

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
            case "Sighting.numAdultMales":
            case "Sighting.numSubFemales":
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
                System.out.println("[INFO] processRow() ignored a field [" + fieldName +
                    "] that was flagged valid");
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

    public List<Encounter> getEncounters() {
        return new ArrayList<Encounter>(encounterCache.values());
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

    private User getOrCreateUser(String email, String fullname, String affiliation,
        Shepherd myShepherd) {
        if (email == null) return null;
        if (userCache.containsKey(email)) return userCache.get(email);
        User user = myShepherd.getUserByEmailAddress(email);
        if (user == null) {
            user = new User(email, Util.generateUUID());
            user.setFullName(fullname);
            user.setAffiliation(affiliation);
            System.out.println("[INFO] BulkImporter.getOrCreateUser() created new " + user);
        }
        userCache.put(email, user);
        return user;
    }

    private Occurrence getOrCreateOccurrence(Map<String, BulkValidator> fmap) {
        // FIXME
        return null;
    }
}
