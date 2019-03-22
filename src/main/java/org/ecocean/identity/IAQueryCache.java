package org.ecocean.identity;

import java.util.ArrayList;
import java.util.List;
import org.ecocean.Annotation;
import org.ecocean.Encounter;
import org.ecocean.Shepherd;
import org.ecocean.cache.QueryCache;
import org.ecocean.cache.CachedQuery;
import org.ecocean.cache.QueryCacheFactory;
import org.json.JSONObject;
import org.json.JSONArray;


public class IAQueryCache {
    private static final String NAME_PREFIX = "IBEISIA.";
    private static final String NAME_TARGETANNOTATIONS = "targetAnnotations";
    private static final String TARGET_ANNOT_UUID_KEY = "database_annot_uuid_list";
    private static final String TARGET_ANNOT_NAME_KEY = "database_annot_name_list";

    //builds a cache ("final" usable json object to send to IA via rest) for the species
    //  that uses *all* (appropriate!) Annotations.  we weed out some later with tryTargetAnnotationsCache() below
    //  returns number of annots in cache (-1 means we didnt do it)
    public static int buildTargetAnnotationsCache(String context, ArrayList<Annotation> qanns) {
        //qanns can "in theory" contain more than one annot, but we arent ready for that universe yet anyway...
        if ((qanns == null) || (qanns.size() < 1)) return -1;  //  :(
        //we want *non* excluding version of this:
        Shepherd myShepherd = new Shepherd(context);
        ArrayList<Annotation> anns = qanns.get(0).getMatchingSetForTaxonomy(myShepherd, null);
        if (anns == null) return -2;
        JSONObject jdata = new JSONObject();
        JSONArray idArr = new JSONArray();
        JSONArray nameArr = new JSONArray();
        for (Annotation ann : anns) {
//TODO do we also filter on name conflicts etc here????????????????????????????
            String name = ann.findIndividualId(myShepherd);
            if (name == null) name = IBEISIA.IA_UNKNOWN_NAME;
            nameArr.put(name);
            idArr.put(ann.getAcmId());
        }
        jdata.put("created", System.currentTimeMillis());
        jdata.put("triggerAnnotation", qanns.get(0).getId());
        jdata.put("context", context);
        jdata.put(TARGET_ANNOT_UUID_KEY, idArr);
        jdata.put(TARGET_ANNOT_NAME_KEY, nameArr);
log("buildTargetAnnotationsCache() caching " + idArr.length() + " Annotations");
        setTargetAnnotationsCache(context, qanns.get(0), jdata);
        return idArr.length();
    }

    public static CachedQuery setTargetAnnotationsCache(String context, Annotation ann, JSONObject jobj) {
        String qname = generateQueryName(context, ann, NAME_TARGETANNOTATIONS);
        if (qname == null) {
            log("WARNING: setTargetAnnotationsCache() could not find query name for " + ann);
            return null;
        }
        QueryCache qc = QueryCacheFactory.getQueryCache(context);
        CachedQuery q = qc.getQueryByName(qname);
        if (q == null) {
            Shepherd myShepherd = new Shepherd(context);
            myShepherd.beginDBTransaction();
            q = new CachedQuery(qname, jobj, true, myShepherd);
            qc.addCachedQuery(q);
            myShepherd.commitDBTransaction();
            log("INFO: setTargetAnnotationsCache(" + qname + ") created new CachedQuery id=" + q.getUUID());
        } else {
            q.setJSONSerializedQueryResult(jobj, true);
            log("INFO: setTargetAnnotationsCache(" + qname + ") updated CachedQuery id=" + q.getUUID());
        }
        return q;
    }

    public static JSONObject getTargetAnnotationsCache(String context, Annotation ann) {
        String qname = generateQueryName(context, ann, NAME_TARGETANNOTATIONS);
        if (qname == null) {
            log("WARNING: getTargetAnnotationsCache() could not find query name for " + ann);
            return null;
        }
        QueryCache qc = QueryCacheFactory.getQueryCache(context);
        CachedQuery q = qc.getQueryByName(qname);
        if (q == null) {
            log("INFO: getTargetAnnotationsCache(" + qname + ") has no such CachedQuery, sorry!");
            return null;
        }
        q.loadCachedJSONIfNeeded();
        return q.getJSONSerializedQueryResult();
    }


    //if this returns null, then expensive grind happens to find and send ident task
    //  (on the plus side, it should *set* the cache for next time!)
    //NOTE:  this *must* return the JSONObject structure expected to be returned from IBEISIA._sendIdentificationTask()
    public static JSONObject tryTargetAnnotationsCache(String context, Annotation ann, final JSONObject baseRtn) {
        JSONObject current = getTargetAnnotationsCache(context, ann);
        if (current == null) {
            log("INFO: sorry, tryTargetAnnotationsCache() has no current cache for " + ann);
            return null;
        }
        JSONObject rtn = new JSONObject(baseRtn, JSONObject.getNames(baseRtn));  //basically clones the incoming baseRtn
        JSONArray idArr = current.optJSONArray(TARGET_ANNOT_UUID_KEY);
        JSONArray nameArr = current.optJSONArray(TARGET_ANNOT_NAME_KEY);
        if ((idArr == null) || (nameArr == null) || (idArr.length() != nameArr.length())) {
            log("WARNING: tryTargetAnnotationsCache() could not unpack idArr or nameArr from cache; WEIRD!");
            return null;
        }
log("!!!! tryTargetAnnotationsCache() using " + idArr.length() + " Annotations cache");

        //weed out sibling (including ourself!) annots
        List<String> famAnnotIds = new ArrayList<String>();
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.beginDBTransaction();
        Encounter enc = ann.findEncounter(myShepherd);
        if ((enc != null) && (enc.getAnnotations() != null)) {  //second part is impossible (flw)
            for (Annotation ea : enc.getAnnotations()) {
                famAnnotIds.add(ea.getAcmId());
            }
        }
        myShepherd.rollbackDBTransaction();
        // TODO some other checks here, obviously????? (or will it just expire by itself)  (e.g. validAnnotation???)
        JSONObject postData = new JSONObject();
        JSONArray outIdArr = new JSONArray();
        JSONArray outNameArr = new JSONArray();
        for (int i = 0 ; i < idArr.length() ; i++) {
            //we have the potential of putting nulls in here, which "should never happen"
            // if you are reading this, future-me, have a good laugh
            String aid = idArr.optString(i, null);
            String an = nameArr.optString(i, null);
            if (famAnnotIds.contains(aid)) continue;
            outIdArr.put(aid);
            outNameArr.put(an);
        }
/*
            JSONObject sent = IBEISIA.beginIdentifyAnnotations(qanns, matchingSet, queryConfigDict, userConfidence,
                                                               myShepherd, annTaskId, baseUrl);
            ann.setIdentificationStatus(IBEISIA.STATUS_PROCESSING);
            taskRes.put("beginIdentify", sent);
            String jobId = null;
            if ((sent.optJSONObject("status") != null) && sent.getJSONObject("status").optBoolean("success", false))
                jobId = sent.optString("response", null);
            taskRes.put("jobId", jobId);
            //validIds.toArray(new String[validIds.size()])
            IBEISIA.log(annTaskId, ann.getId(), jobId, new JSONObject("{\"_action\": \"initIdentify\"}"), context);
*/
        rtn.put("success", false);  //tmp during development
        return rtn;
    }

    //NOTE: this assumes ann as been "approved" for sending
    public static JSONObject addTargetAnnotation(String context, Annotation ann) {
        JSONObject current = getTargetAnnotationsCache(context, ann);
        if (current == null) {
            log("WARNING: addTargetAnnotation() has no current cache whence to add " + ann);
            return null;
        }
        JSONArray tIdArr = current.optJSONArray(TARGET_ANNOT_UUID_KEY);
        JSONArray tNameArr = current.optJSONArray(TARGET_ANNOT_NAME_KEY);
        if ((tIdArr == null) && (tNameArr == null)) {
            log("WARNING: addTargetAnnotation() found empty current arrays!  weird.");
            return null;
        }
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.beginDBTransaction();
        String indivId = ann.findIndividualId(myShepherd);
        myShepherd.rollbackDBTransaction();
        //cheap fix to handle name-conflict potential: we dont add an annot which is already on list in cache!
        // TODO ... do this!  :)  :(
        if (indivId == null) {
            tNameArr.put(IBEISIA.IA_UNKNOWN_NAME);
        } else {
            tNameArr.put(indivId);
        }
        tIdArr.put(IBEISIA.toFancyUUID(ann.getAcmId()));
        current.put(TARGET_ANNOT_UUID_KEY, tIdArr);
        current.put(TARGET_ANNOT_NAME_KEY, tNameArr);
        setTargetAnnotationsCache(context, ann, current);
        return current;
    }

    private static String generateQueryName(String context, Annotation ann, String type) {
        if (ann == null) return null;
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.beginDBTransaction();
        Encounter enc = ann.findEncounter(myShepherd);
        myShepherd.rollbackDBTransaction();
        if (enc == null) return null;
        String val = enc.getTaxonomyString();
        if (val == null) return null;
        return NAME_PREFIX + val.replaceAll(" ", "_");
    }

    private static void log(String msg) {
        System.out.println("IAQueryCache " + msg);
    }

}

