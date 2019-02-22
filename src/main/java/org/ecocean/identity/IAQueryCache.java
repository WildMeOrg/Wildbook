package org.ecocean.identity;

import java.util.ArrayList;
import org.ecocean.Annotation;
import org.ecocean.Encounter;
import org.ecocean.Shepherd;
import org.ecocean.cache.QueryCache;
import org.ecocean.cache.CachedQuery;
import org.ecocean.cache.QueryCacheFactory;
import org.json.JSONObject;
import org.json.JSONArray;


public class IAQueryCache {
    private static String NAME_PREFIX = "IBEISIA.";
    private static String NAME_TARGETANNOTATIONS = "targetAnnotations";

    //this is used at the end IBEISIA.sendIdentify() to "store" (if applicable?) the JSONObject which will be cached
    //  for future use.  possibly more complex logic could decide if/when to store it etc.
    public static void buildTargetAnnotationsCache(String context, ArrayList<Annotation> qanns, JSONObject jdata) {
        //qanns can "in theory" contain more than one annot, but we arent ready for that universe yet anyway...
        if ((jdata == null) || (qanns == null) || (qanns.size() < 1)) return;  //  :(
System.out.println("!!!! IAQueryCache.buildTargetAnnotationsCache() caching ~" + jdata.toString().length() + " byte of json data");
        setTargetAnnotationsCache(context, qanns.get(0), jdata);
    }

    public static CachedQuery setTargetAnnotationsCache(String context, Annotation ann, JSONObject jobj) {
        String qname = generateQueryName(context, ann, NAME_TARGETANNOTATIONS);
        if (qname == null) {
            System.out.println("WARNING: IAQueryCache.setTargetAnnotationsCache() could not find query name for " + ann);
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
            System.out.println("INFO: IAQueryCache.setTargetAnnotationsCache(" + qname + ") created new CachedQuery id=" + q.getUUID());
        } else {
            q.setJSONSerializedQueryResult(jobj, true);
            System.out.println("INFO: IAQueryCache.setTargetAnnotationsCache(" + qname + ") updated CachedQuery id=" + q.getUUID());
        }
        return q;
    }

    public static JSONObject getTargetAnnotationsCache(String context, Annotation ann) {
        String qname = generateQueryName(context, ann, NAME_TARGETANNOTATIONS);
        if (qname == null) {
            System.out.println("WARNING: IAQueryCache.getTargetAnnotationsCache() could not find query name for " + ann);
            return null;
        }
        QueryCache qc = QueryCacheFactory.getQueryCache(context);
        CachedQuery q = qc.getQueryByName(qname);
        if (q == null) {
            System.out.println("INFO: IAQueryCache.getTargetAnnotationsCache(" + qname + ") has no such CachedQuery, sorry!");
            return null;
        }
        return q.getJSONSerializedQueryResult();
    }

    //NOTE: this assumes ann as been "approved" for sending
    public static JSONObject addTargetAnnotation(String context, Annotation ann) {
        JSONObject current = getTargetAnnotationsCache(context, ann);
        if (current == null) {
            System.out.println("WARNING: IAQueryCache.addTargetAnnotation() has no current cache whence to add " + ann);
            return null;
        }
        JSONArray tIdArr = current.optJSONArray("database_annot_uuid_list");
        JSONArray tNameArr = current.optJSONArray("database_annot_name_list");
        if ((tIdArr == null) && (tNameArr == null)) {
            System.out.println("WARNING: IAQueryCache.addTargetAnnotation() found empty current arrays!  weird.");
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
        current.put("database_annot_uuid_list", tIdArr);
        current.put("database_annot_name_list", tNameArr);
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
}

