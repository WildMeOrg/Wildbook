// basic bulk import
package org.ecocean.api.bulk;

import java.util.ArrayList;
import java.util.Arrays;
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
import org.ecocean.genetics.*;
import org.ecocean.Keyword;
import org.ecocean.media.MediaAsset;
import org.ecocean.media.MediaAssetFactory;
import org.ecocean.MarkedIndividual;
import org.ecocean.Measurement;
import org.ecocean.Occurrence;
import org.ecocean.Project;
import org.ecocean.servlet.importer.ImportTask;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.shepherd.core.ShepherdPMF;
import org.ecocean.social.Membership;
import org.ecocean.social.SocialUnit;
import org.ecocean.tag.SatelliteTag;
import org.ecocean.User;
import org.ecocean.Util;

public class BulkImporter {
    private List<Map<String, Object> > dataRows = null;
    private Map<String, MediaAsset> mediaAssetMap = null;
    private User user = null;
    private String importTaskId = null;
    private Shepherd myShepherd = null;
    private long startTime = -1l;

    // caching loaded and (more imporantly?) newly created objects, so they can be
    // used across all rows. StandardImport seemed to do some caching *based on user*
    // (of, for example, MarkedIndividuals). not sure why. maybe this will be revealed later.
    private Map<String, Encounter> encounterCache = new HashMap<String, Encounter>();
    private Map<String, Occurrence> occurrenceCache = new HashMap<String, Occurrence>();
    private Map<String, MarkedIndividual> individualCache = new HashMap<String, MarkedIndividual>();
    private Map<String, User> userCache = new HashMap<String, User>();
    private Map<String, Keyword> keywordCache = new HashMap<String, Keyword>();
    private Map<String, Project> projectCache = new HashMap<String, Project>();

    public BulkImporter(String id, List<Map<String, Object> > rows, Map<String, MediaAsset> maMap,
        User user, Shepherd myShepherd) {
        this.dataRows = rows;
        if (maMap == null) {
            this.mediaAssetMap = new HashMap<String, MediaAsset>();
        } else {
            this.mediaAssetMap = maMap;
        }
        this.user = user;
        this.importTaskId = id;
        this.myShepherd = myShepherd;
        this.startTime = System.currentTimeMillis();
    }

    public JSONObject createImport()
    throws ServletException {
        JSONObject rtn = new JSONObject();
        List<Base> needIndexing = new ArrayList<Base>();

        logProgress("begin processRows");
        for (int rowNum = 0; rowNum < dataRows.size(); rowNum++) {
            if (rowNum % 100 == 0) logProgress("processRow=" + rowNum);
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
            // (previous) MediaAsset creation counts as 20%, and this counts as 50%,
            // with persisting making up the remaining 30%  #progressBarKludge
            markProgress(rowNum, dataRows.size(), 0.2d, 0.5d);
        }
        logProgress("end processRows");
        System.out.println(
            "------------ all rows processed; beginning persistence -------------\n");
        int persistenceTicksTotal = mediaAssetMap.values().size() + userCache.values().size() +
            encounterCache.values().size() + occurrenceCache.values().size() +
            individualCache.values().size() + projectCache.values().size();
        int persistenceTicks = 0;
        List<Integer> maIds = new ArrayList<Integer>(); // used later to build child MAs
        JSONArray arr = new JSONArray();
        for (MediaAsset ma : mediaAssetMap.values()) {
            ma.setSkipAutoIndexing(true);
            MediaAssetFactory.save(ma, myShepherd);
            System.out.println("MMMM " + ma);
            arr.put(ma.getIdInt());
            maIds.add(ma.getIdInt());
            // see note on MediaAsset.getSkipAutoIndexing()
            // needIndexing.add(ma);
            persistenceTicks++;
            markProgress(persistenceTicks, persistenceTicksTotal, 0.7d, 0.3d);
        }
        logProgress("end persist MediaAsset");
        rtn.put("mediaAssets", arr);
        for (User u : userCache.values()) {
            myShepherd.getPM().makePersistent(u);
            persistenceTicks++;
            markProgress(persistenceTicks, persistenceTicksTotal, 0.7d, 0.3d);
        }
        arr = new JSONArray();
        for (Encounter enc : encounterCache.values()) {
            // it is a certain kind of painful that if you do not pass id here it assigns a new random one
            myShepherd.storeNewEncounter(enc, enc.getId());
            System.out.println("EEEE " + enc);
            arr.put(enc.getId());
            needIndexing.add(enc);
            persistenceTicks++;
            markProgress(persistenceTicks, persistenceTicksTotal, 0.7d, 0.3d);
        }
        logProgress("end persist Encounter");
        rtn.put("encounters", arr);
        arr = new JSONArray();
        for (Occurrence occ : occurrenceCache.values()) {
            myShepherd.storeNewOccurrence(occ);
            System.out.println("OOOO " + occ);
            arr.put(occ.getId());
            needIndexing.add(occ);
            persistenceTicks++;
            markProgress(persistenceTicks, persistenceTicksTotal, 0.7d, 0.3d);
        }
        logProgress("end persist Occurrence");
        rtn.put("sightings", arr);
        arr = new JSONArray();
        for (MarkedIndividual indiv : individualCache.values()) {
            myShepherd.storeNewMarkedIndividual(indiv);
            indiv.refreshNamesCache();
            System.out.println("IIII " + indiv);
            arr.put(indiv.getId());
            needIndexing.add(indiv);
            persistenceTicks++;
            markProgress(persistenceTicks, persistenceTicksTotal, 0.7d, 0.3d);
        }
        logProgress("end persist MarkedIndividual");
        rtn.put("individuals", arr);
        for (Project proj : projectCache.values()) {
            myShepherd.storeNewProject(proj);
            System.out.println("PPPP " + proj);
            persistenceTicks++;
            markProgress(persistenceTicks, persistenceTicksTotal, 0.7d, 0.3d);
        }
        logProgress("persist COMPLETE");
        System.out.println(
            "------------ persistence complete; background indexing and MA children -------------\n");
        // clears shepherd/pmf cache, which we seem to do when we create encounters (?)
        myShepherd.cacheEvictAll();
        BulkImportUtil.bulkOpensearchIndex(needIndexing);
        MediaAsset.updateStandardChildrenBackground(myShepherd.getContext(), maIds);
        logProgress("end createImport()");
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
        // we will *always* get some Occurrence
        Occurrence occ = getOrCreateOccurrence(fmap);
        Encounter enc = getOrCreateEncounter(fmap, indiv, occ);
        if (indiv != null) {
            indiv.addEncounter(enc);
            enc.setIndividual(indiv);
            indiv.setTaxonomyFromEncounters(true);
        }
        occ.addEncounterAndUpdateIt(enc);
        occ.setLatLonFromEncs(false);
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
        List<String> maQuality = BulkImportUtil.findIndexedFieldNames(allFieldNames,
            "Encounter.quality");
        List<String> nameLabelFields = BulkImportUtil.findIndexedFieldNames(allFieldNames,
            "MarkedIndividual.name.label");
        List<String> nameValueFields = BulkImportUtil.findIndexedFieldNames(allFieldNames,
            "MarkedIndividual.name.value");
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
        // note: it is also required, so we should have *something* (but we check anyway)
        if (!fmap.containsKey("Encounter.submitterID") ||
            fmap.get("Encounter.submitterID").valueIsNull())
            throw new RuntimeException("no value for Encounter.submitterID");
        enc.setSubmitterID(fmap.get("Encounter.submitterID").getValueString());
        occ.setSubmitterIDFromEncs(false);
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
                saffil);
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
                ioaffil);
            enc.addInformOther(inf); // weeds out null and duplicates, yay!
        }
        // like above, but photographer
        List<String> photographerEmails = BulkImportUtil.findIndexedFieldNames(allFieldNames,
            "Encounter.photographer.emailAddress");
        List<String> photographerNames = BulkImportUtil.findIndexedFieldNames(allFieldNames,
            "Encounter.photographer.fullName");
        List<String> photographerAffiliations = BulkImportUtil.findIndexedFieldNames(allFieldNames,
            "Encounter.photographer.affiliation");
        for (int i = 0; i < photographerEmails.size(); i++) {
            if (photographerEmails.get(i) == null) continue;
            String pname = null;
            if ((i < photographerNames.size()) && (photographerNames.get(i) != null))
                pname = fmap.get(photographerNames.get(i)).getValueString();
            String paffil = null;
            if ((i < photographerAffiliations.size()) && (photographerAffiliations.get(i) != null))
                paffil = fmap.get(photographerAffiliations.get(i)).getValueString();
            User pho = getOrCreateUser(fmap.get(photographerEmails.get(i)).getValueString(), pname,
                paffil);
            enc.addPhotographer(pho); // weeds out null and duplicates, yay!
        }
        // projects
        List<String> projectPrefixes = BulkImportUtil.findIndexedFieldNames(allFieldNames,
            "Encounter.project.projectIdPrefix");
        List<String> projectNames = BulkImportUtil.findIndexedFieldNames(allFieldNames,
            "Encounter.project.researchProjectName");
        List<String> projectOwnerUsernames = BulkImportUtil.findIndexedFieldNames(allFieldNames,
            "Encounter.project.ownerUsername");
        for (int i = 0; i < projectPrefixes.size(); i++) {
            if (projectPrefixes.get(i) == null) continue;
            String projectPrefix = fmap.get(projectPrefixes.get(i)).getValueString();
            if (projectPrefix == null) continue;
            String projectName = null;
            if ((i < projectNames.size()) && (projectNames.get(i) != null))
                projectName = fmap.get(projectNames.get(i)).getValueString();
            // for whatever reason StandardImport bails on doing anything unless we have *both* prefix and name,
            // despite the fact, the lookup of existing projects uses prefix only. so replicating questionable behavior here.
            if (projectName == null) continue;
            String projectOwnerUsername = null;
            if ((i < projectOwnerUsernames.size()) && (projectOwnerUsernames.get(i) != null))
                projectOwnerUsername = fmap.get(projectOwnerUsernames.get(i)).getValueString();
            Project proj = getOrCreateProject(projectPrefix, projectName, projectOwnerUsername);
            if (proj != null) proj.addEncounter(enc);
        }
        // measurements kinda suck eggs. good luck with this.
        List<String> measFN = BulkImportUtil.findMeasurementFieldNames(allFieldNames);
        List<String> mspFN = BulkImportUtil.findMeasurementSamplingProtocolFieldNames(
            allFieldNames);
        // note that StandardImport does no validation against commonConfig for
        // samplingProtocol. so we ignore it just the same. :/
        List<String> munits = BulkImportUtil.getMeasurementUnits();
        List<String> mvals = BulkImportUtil.getMeasurementValues(); // really more like "names"
        for (int i = 0; i < measFN.size(); i++) {
            if (measFN.get(i) == null) continue;
            if (i >= mvals.size()) continue;
            Double mdbl = fmap.get(measFN.get(i)).getValueDouble();
            if (mdbl == null) continue;
            String sampProt = null;
            if ((i < mspFN.size()) && (mspFN.get(i) != null))
                sampProt = fmap.get(mspFN.get(i)).getValueString();
            String munit = null;
            if (i < munits.size()) munit = munits.get(i);
            Measurement meas = new Measurement(enc.getId(), mvals.get(i), mdbl, munit, sampProt);
            System.out.println("[INFO] field " + measFN.get(i) + " [i=" + i + "] created " + meas);
            enc.setMeasurement(meas);
        }
        handleSocialUnit(indiv, fmap.get("SocialUnit.socialUnitName"), fmap.get("Membership.role"));
        handleSamples(enc, fmap);
/*
   core functionality: altering main objects (Encounters, etc.)

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
                if (!bv.valueIsNull()) occ.setGroupBehavior(bv.getValueString());
                break;

            case "Encounter.country":
                enc.setCountry(bv.getValueString());
                break;

            // validation step should only be allowing millis OR year/month/day/etc; not both
            case "Sighting.dateInMilliseconds":
            case "Sighting.millis":
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

            case "Encounter.sightingRemarks":
                enc.setOccurrenceRemarks(bv.getValueString());
                break;

            case "Encounter.otherCatalogNumbers":
                enc.setOtherCatalogNumbers(bv.getValueString());
                break;

            case "Encounter.patterningCode":
                enc.setPatterningCode(bv.getValueString());
                break;

            case "Encounter.researcherComments":
                if (!bv.valueIsNull()) enc.addComments(bv.getValueString());
                break;

            case "Encounter.verbatimLocality":
                enc.setVerbatimLocality(bv.getValueString());
                break;

            case "Sighting.bearing":
                occ.setBearing(bv.getValueDouble());
                break;

            case "Sighting.bestGroupSizeEstimate":
                occ.setBestGroupSizeEstimate(bv.getValueDouble());
                break;

            case "Sighting.comments":
                occ.setComments(bv.getValueString());
                break;

            case "Sighting.distance":
                occ.setDistance(bv.getValueDouble());
                break;

            case "Sighting.effortCode":
                occ.setEffortCode(bv.getValueDouble());
                break;

            case "Sighting.fieldStudySite":
                occ.setFieldStudySite(bv.getValueString());
                break;

            case "Sighting.fieldSurveyCode":
            case "Survey.id":
                occ.setFieldSurveyCode(bv.getValueString());
                break;

            case "Sighting.groupBehavior":
                occ.setGroupBehavior(bv.getValueString());
                break;

            case "Sighting.groupComposition":
                occ.setGroupComposition(bv.getValueString());
                break;

            case "Sighting.humanActivityNearby":
                occ.setHumanActivityNearby(bv.getValueString());
                break;

            case "Sighting.individualCount":
                occ.setIndividualCount(bv.getValueInteger());
                break;

            case "Sighting.initialCue":
                occ.setInitialCue(bv.getValueString());
                break;

            case "Sighting.maxGroupSizeEstimate":
                occ.setMaxGroupSizeEstimate(bv.getValueInteger());
                break;

            case "Sighting.minGroupSizeEstimate":
                occ.setMinGroupSizeEstimate(bv.getValueInteger());
                break;

            case "Sighting.numAdults":
                occ.setIndividualCount(bv.getValueInteger());
                break;

            case "Sighting.numCalves":
                occ.setNumCalves(bv.getValueInteger());
                break;

            case "Sighting.numJuveniles":
                occ.setNumJuveniles(bv.getValueInteger());
                break;

            case "Sighting.observer":
                occ.setObserver(bv.getValueString());
                break;

            case "Sighting.seaState":
                occ.setObserver(bv.getValueString());
                break;

            case "Sighting.seaSurfaceTemp":
            case "Sighting.seaSurfaceTemperature":
                occ.setSeaSurfaceTemp(bv.getValueDouble());
                break;

            case "Sighting.swellHeight":
                occ.setSwellHeight(bv.getValueDouble());
                break;

            case "Sighting.transectBearing":
                occ.setTransectBearing(bv.getValueDouble());
                break;

            case "Sighting.transectName":
                occ.setTransectName(bv.getValueString());
                break;

            case "Sighting.visibilityIndex":
                occ.setVisibilityIndex(bv.getValueDouble());
                break;

            case "SatelliteTag.serialNumber":
                if (!bv.valueIsNull()) {
                    SatelliteTag stag = new SatelliteTag("", bv.getValueString(), "");
                    enc.setSatelliteTag(stag);
                }
                break;

            case "Survey.comments":
                if (!bv.valueIsNull() &&
                    ((occ.getComments() == null) ||
                    !occ.getComments().contains(bv.getValueString())))
                    occ.addComments(bv.getValueString());
                break;

            case "Survey.vessel":
            case "SurveyTrack.vesselID":
                occ.setSightingPlatform(bv.getValueString());
                break;

            // these add to Occurence.taxonomies, which i am not sure are supported any more
/*
            it was decided we disallow setting of the Occurrence.taxonomies values via bulk import
            case "Sighting.taxonomy0":
            case "Taxonomy.commonName":
            case "Taxonomy.scientificName":
                System.out.println("[INFO] " + fieldName + " currently not implemented");
                break;
 */

/*
            unsure where these came from; possibly specific wildbooks?
            //case "Survey.type":
            //case "Sighting.numAdultMales":
            //case "Sighting.numSubFemales":
 */
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
        int offset = 0;
        for (String maKey : maFields) {
            if (maKey == null) continue; // data skipped an index
            BulkValidator bv = fmap.get(maKey);
            if (bv == null) throw new RuntimeException("could not find fmap for key=" + maKey);
            if (bv.valueIsNull()) continue;
            MediaAsset ma = this.mediaAssetMap.get(bv.getValueString());
            if (ma == null)
                throw new RuntimeException("could not find MediaAsset for maKey=" + maKey +
                        ", bv=" + bv.getValueString() + " in " + this.mediaAssetMap);
            Set<String> kws = new HashSet<String>();
            if ((offset < kwFields.size()) && (kwFields.get(offset) != null))
                kws.add(fmap.get(kwFields.get(offset)).getValueString());
            // StandardImport claims multivalue keywordS is delimited by underscore :/ is this for real?
            if ((offset < multiKwFields.size()) && (multiKwFields.get(offset) != null)) {
                String multi = fmap.get(multiKwFields.get(offset)).getValueString();
                if (multi != null) kws.addAll(Arrays.asList(multi.split("_")));
            }
            handleKeywords(ma, kws);
            Annotation ann = new Annotation(tx, ma);
            ann.setIsExemplar(true);
            ann.setSkipAutoIndexing(true);
            if ((offset < maQuality.size()) && (maQuality.get(offset) != null))
                ann.setQuality(fmap.get(maQuality.get(offset)).getValueDouble());
            annots.add(ann);
            offset++;
        }
        if (annots.size() > 0) enc.addAnnotations(annots);
        System.out.println("+ populated " + annots.size() + " MediaAssets on " + enc);
    }

    public void markProgress(int ticks, int total, double base, double weight) {
        if (this.importTaskId == null) return;
        // we want our own shepherd here so we can persist this task independent of our main shepherd
        Shepherd taskShepherd = new Shepherd(this.myShepherd.getContext());
        taskShepherd.setAction("BulkImporter.markProgress");
        taskShepherd.beginDBTransaction();
        try {
            ImportTask itask = taskShepherd.getImportTask(this.importTaskId);
            if (itask == null) return;
            Double progress = base + (weight * new Double(ticks) / new Double(total));
            itask.setProcessingProgress(progress);
            taskShepherd.storeNewImportTask(itask);
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            taskShepherd.commitDBTransaction();
            taskShepherd.closeDBTransaction();
        }
    }

    public List<Encounter> getEncounters() {
        return new ArrayList<Encounter>(encounterCache.values());
    }

    private void handleKeywords(MediaAsset ma, Set<String> keywordValues) {
        for (String kval : keywordValues) {
            if (kval == null) continue;
            Keyword key = keywordCache.get(kval);
            if (key == null) key = myShepherd.getKeyword(kval);
            if (key == null) key = new Keyword(kval);
            keywordCache.put(kval, key); // always do this so we get new *and* db-loaded ones into cache
            ma.addKeyword(key);
        }
    }

    private void handleSocialUnit(MarkedIndividual indiv, BulkValidator suNameBV,
        BulkValidator memRoleBV) {
        if ((indiv == null) || (suNameBV == null)) return;
        String suName = suNameBV.getValueString();
        if (suName == null) return;
        String memRole = null;
        if (memRoleBV != null) memRole = memRoleBV.getValueString();
        try {
            SocialUnit su = myShepherd.getSocialUnit(suName);
            if (su == null) su = new SocialUnit(suName);
            if (su.hasMarkedIndividualAsMember(indiv)) return; // StandardImport bails in this case
            Membership mem = new Membership(indiv);
            if (memRole != null) mem.setRole(memRole);
            su.addMember(mem);
            myShepherd.getPM().makePersistent(su);
            myShepherd.getPM().makePersistent(mem);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void handleSamples(Encounter enc, Map<String, BulkValidator> fmap) {
        String tsId = null;

        if (fmap.containsKey("TissueSample.sampleID"))
            tsId = fmap.get("TissueSample.sampleID").getValueStringTrimmedNonEmpty();
        if ((tsId == null) && fmap.containsKey("MicrosatelliteMarkersAnalysis.analysisID"))
            tsId = fmap.get(
                "MicrosatelliteMarkersAnalysis.analysisID").getValueStringTrimmedNonEmpty();
        if ((tsId == null) && fmap.containsKey("SexAnalysis.processingLabTaskID"))
            tsId = fmap.get("SexAnalysis.processingLabTaskID").getValueStringTrimmedNonEmpty();
        if (tsId == null) return; // can neither find nor create a sample, so bye
        // if this is a newly created encounter, i am fairly certain this will always return null
        // TODO but maybe these samples will be on encounters *created in this import*, in which case,
        // we may need to do some kind of caching like other objects
        TissueSample sample = myShepherd.getTissueSample(tsId, enc.getId());
        if (sample == null) sample = new TissueSample(enc.getId(), tsId);
        if (fmap.containsKey("TissueSample.tissueType"))
            sample.setTissueType(fmap.get(
                "TissueSample.tissueType").getValueStringTrimmedNonEmpty());
        // genotype
        String alleleNames = null;
        String alleleZeros = null;
        String alleleOnes = null;
        if (fmap.containsKey("MicrosatelliteMarkersAnalysis.alleleNames"))
            alleleNames = fmap.get(
                "MicrosatelliteMarkersAnalysis.alleleNames").getValueStringTrimmedNonEmpty();
        if (fmap.containsKey("MicrosatelliteMarkersAnalysis.alleles0"))
            alleleZeros = fmap.get(
                "MicrosatelliteMarkersAnalysis.alleles0").getValueStringTrimmedNonEmpty();
        if (fmap.containsKey("MicrosatelliteMarkersAnalysis.alleles1"))
            alleleOnes = fmap.get(
                "MicrosatelliteMarkersAnalysis.alleles1").getValueStringTrimmedNonEmpty();
        if ((alleleNames != null) && (alleleZeros != null) && (alleleOnes != null)) {
            ArrayList<Locus> loci = new ArrayList<Locus>();
            String[] names = alleleNames.split(",");
            String[] zeros = alleleZeros.split(",");
            String[] ones = alleleOnes.split(",");
            if ((names.length == zeros.length) && (zeros.length == ones.length)) {
                for (int i = 0; i < names.length; i++) {
                    Integer all0 = null;
                    Integer all1 = null;
                    try {
                        all0 = Integer.parseInt(zeros[i]);
                        all1 = Integer.parseInt(ones[i]);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                    if ((all0 == null) || (all1 == null)) {
                        System.out.println(
                            "BulkImporter.handleSamples(): failed to get allele ints for " +
                            zeros[i] + "; " + ones[i]);
                    } else if (names[i].equals("")) {
                        System.out.println("BulkImporter.handleSamples(): empty name for i=" + i +
                            " in " + alleleNames);
                    } else {
                        Locus locus = new Locus(names[i], all0, all1);
                        loci.add(locus);
                    }
                }
            } else {
                System.out.println("BulkImporter.handleSamples(): length mismatch for (" +
                    alleleNames + "|" + alleleZeros + "|" + alleleOnes + ")");
            }
            if (loci.size() > 0) {
                MicrosatelliteMarkersAnalysis markers = new MicrosatelliteMarkersAnalysis(
                    Util.generateUUID(), tsId, enc.getId(), loci);
                myShepherd.getPM().makePersistent(markers);
                sample.addGeneticAnalysis(markers);
                System.out.println("BulkImporter.handleSamples(): adding " + markers + " to " +
                    sample);
            }
        }
        // sex analysis
        String sas = null;
        if (fmap.containsKey("SexAnalysis.sex"))
            sas = fmap.get("SexAnalysis.sex").getValueStringTrimmedNonEmpty();
        if (sas != null) {
            SexAnalysis sexAn = new SexAnalysis(Util.generateUUID(), sas, enc.getId(), tsId);
            myShepherd.getPM().makePersistent(sexAn);
            sample.addGeneticAnalysis(sexAn);
            System.out.println("BulkImporter.handleSamples(): adding " + sexAn + " to " + sample);
        }
        // haplotype
        String hap = null;
        if (fmap.containsKey("MitochondrialDNAAnalysis.haplotype"))
            hap = fmap.get("MitochondrialDNAAnalysis.haplotype").getValueStringTrimmedNonEmpty();
        if (hap != null) {
            MitochondrialDNAAnalysis mda = new MitochondrialDNAAnalysis(Util.generateUUID(), hap,
                enc.getId(), tsId);
            myShepherd.getPM().makePersistent(mda);
            sample.addGeneticAnalysis(mda);
            System.out.println("BulkImporter.handleSamples(): adding " + mda + " to " + sample);
        }
        // wrap it up, we are done!
        enc.addTissueSample(sample);
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
        if (!(fmap.containsKey("Encounter.genus") &&
            fmap.containsKey("Encounter.specificEpithet"))) {
            System.out.println("[WARNING] BulkImporter.getOrCreateMarkedIndividual(" + id +
                ") is missing genus and/or specificEpithet values");
            return null;
        }
        String genus = fmap.get("Encounter.genus").getValueString();
        String specificEpithet = fmap.get("Encounter.specificEpithet").getValueString();
        if (indiv == null)
            indiv = MarkedIndividual.withName(myShepherd, id, genus, specificEpithet);
        if (indiv == null) {
            indiv = new MarkedIndividual();
            if (Util.isUUID(id)) {
                indiv.setId(id);
            } else {
                indiv.addName(id);
            }
            indiv.setGenus(genus);
            indiv.setSpecificEpithet(specificEpithet);
            indiv.setVersion();
            // TODO what else???
            System.out.println(
                "[INFO] BulkImporter.getOrCreateMarkedIndividual() creating new; could not find existing indiv based on id="
                + id + " => " + indiv);
        }
        indiv.setSkipAutoIndexing(true);
        // note: this is using "id" which may be a name, but we are banking on that *this import*
        // will re-use the same thing (name or uuid) for us to key off of in cache
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
/*
   i have removed this block because it is giving bizarre behavior, especially with test data where we
   often have occ id and indiv id set to recycled values, causing this to be activated
        } else if ((indiv != null) && (occ != null)) {
            // apparently this is a thing?
            enc = myShepherd.getEncounterByIndividualAndOccurrence(indiv.getId(), occ.getId());
            // this doesnt read from cache - not sure how much of a problem that will be, but likely some
 */
        }
        if (enc == null) {
            enc = new Encounter();
            if (encId == null) encId = Util.generateUUID();
            enc.setId(encId);
            enc.setDWCDateAdded();
            enc.setDWCDateLastModified();
        }
        enc.setSkipAutoIndexing(true);
        if (encId != null) encounterCache.put(encId, enc);
        return enc;
    }

    private User getOrCreateUser(String email, String fullname, String affiliation) {
        if (email == null) return null;
        if (userCache.containsKey(email)) return userCache.get(email);
        User user = myShepherd.getUserByEmailAddress(email);
        if (user == null) {
            user = new User(email, Util.generateUUID());
            user.setFullName(fullname);
            user.setAffiliation(affiliation);
            System.out.println("[INFO] BulkImporter.getOrCreateUser() creating new " + user);
        }
        userCache.put(email, user);
        return user;
    }

    private Project getOrCreateProject(String projectPrefix, String projectName,
        String projectOwnerUsername) {
        if (Util.stringIsEmptyOrNull(projectPrefix)) return null;
        Project proj = projectCache.get(projectPrefix);
        if (proj == null) proj = myShepherd.getProjectByProjectIdPrefix(projectPrefix);
/*
        if exact match fails, we get a little more lax and let user 'foo-' match things
        like 'FOO-###'. note: this could be way too lax if the users passes no dash in there,
        as 'foo' would match 'foobar-###' so we might want to make further restrictions on when
        to do this fuzzy matching, depending on user behavior and how forgiving we are.
 */
        if (proj == null) {
            proj = myShepherd.getProjectByProjectIdPrefixPrefix(projectPrefix);
            if (proj != null)
                System.out.println(
                    "[INFO] BulkImporter.getOrCreateProject() fuzzy-matched projectPrefix '" +
                    projectPrefix + "' to " + proj);
        }
        if (proj == null) {
            // in StandardImport, both of these are required to create a new Project
            if ((projectName == null) || (projectOwnerUsername == null)) return null;
/*
            sadly: (1) we cannot use getOrCreateUser, as we dont have an email address(!)
            (2) we also create a user based purely on username (!) if we dont find one
            we dont want to end up with duplicated usernames, so we still use userCache to find this
            to find this user again (for example, if *another* project is made with same username),
            by keying off of username. sigh
 */
            User owner = userCache.get(projectOwnerUsername);
            if (owner == null) owner = myShepherd.getUser(projectOwnerUsername);
            if (owner == null) {
                owner = new User(Util.generateUUID());
                owner.setUsername(projectOwnerUsername);
                userCache.put(projectOwnerUsername, owner);
            }
            proj = new Project(projectPrefix);
            proj.setResearchProjectName(projectName);
            proj.setOwner(owner);
        }
        projectCache.put(projectPrefix, proj);
        return proj;
    }

    private Occurrence getOrCreateOccurrence(Map<String, BulkValidator> fmap) {
        // accessed as Occurrence.occurrenceID or Encounter.occurrenceID in StandardImport
        // but we change to .sightingID
        String id = null;

        if (fmap.containsKey("Sighting.sightingID"))
            id = fmap.get("Sighting.sightingID").getValueString();
        if ((id == null) && fmap.containsKey("Encounter.sightingID"))
            id = fmap.get("Encounter.sightingID").getValueString();
        if ((id != null) && occurrenceCache.containsKey(id)) return occurrenceCache.get(id);
        // this will create a new one *even if null id* (assigns random)
        Occurrence occ = myShepherd.getOrCreateOccurrence(id);
        occ.setSkipAutoIndexing(true);
        occurrenceCache.put(occ.getId(), occ); // we use getId() in case of id==null
        return occ;
    }

    public static void logProgress(String id, String msg, Long startTime) {
        Util.mark("BulkImporter.logProgress[" + id + "]: " + msg, startTime);
    }

    public void logProgress(String msg) {
        logProgress(this.importTaskId, msg, this.startTime);
    }

    public String toString() {
        return super.toString() + " [id:" + importTaskId + "] " + (dataRows ==
                   null ? 0 : dataRows.size()) + " dataRows; " + this.encounterCache.size() +
                   " encs";
    }
}
